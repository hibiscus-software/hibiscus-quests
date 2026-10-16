/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscuslib.api.team.event.PlayerJoinedTeamEvent;
import software.hibiscus.hibiscuslib.api.team.event.TeamDeletedEvent;
import software.hibiscus.hibiscuslib.util.Profiles;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.event.ChapterCompletedEvent;
import software.hibiscus.hibiscusquests.api.event.QuestCompletedEvent;
import software.hibiscus.hibiscusquests.api.event.RewardClaimedEvent;
import software.hibiscus.hibiscusquests.api.event.TaskProgressEvent;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.book.AutoClaim;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.ProgressMode;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestReward;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.book.TeamChange;
import software.hibiscus.hibiscusquests.config.QuestsConfig;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.reward.ChoiceReward;
import software.hibiscus.hibiscusquests.reward.Rewards;

// Every team's progress on the server, and every change to it. Task progress, completion, claims, resets and team
// changes all go through here, so the rules (QuestLogic), the API events, saving and syncing happen the same way
// whether the change came from a task, the book, a command or another mod. Server thread only.
//
// Completion is checked whenever progress changes: every unlocked quest whose tasks are done completes, which can
// unlock and complete the quests after it in the same pass. Then auto-claim rewards are handed out and repeatable
// quests whose rewards are all claimed start over.
public final class ProgressManager {
    private static final int SAVE_DELAY_TICKS = 100;

    private static final Map<UUID, TeamProgress> TEAMS = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    // Deleted teams: archived, and forgotten at the end of the tick (after the members' team-change events).
    private static final Set<UUID> DELETED = new HashSet<>();
    // When each team's next repeat cooldown runs out, so its tasks can start again.
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static @Nullable ProgressStorage storage;
    private static @Nullable MinecraftServer server;
    private static int saveTimer;

    private ProgressManager() {}

    public static void start(MinecraftServer starting) {
        server = starting;
        storage = new ProgressStorage(starting);
        TEAMS.clear();
        DIRTY.clear();
        DELETED.clear();
        COOLDOWNS.clear();
    }

    public static void stop() {
        saveAll();
        ActiveTasks.clearAll();
        TEAMS.clear();
        DIRTY.clear();
        COOLDOWNS.clear();
        server = null;
        storage = null;
    }

    public static @Nullable ProgressStorage storage() {
        return storage;
    }

    public static void tick(MinecraftServer running) {
        if (!DELETED.isEmpty()) {
            DELETED.forEach(TEAMS::remove);
            DELETED.clear();
        }
        if (!COOLDOWNS.isEmpty() && running.getTickCount() % 20 == 0) {
            long now = QuestClock.now();
            for (UUID teamId : List.copyOf(COOLDOWNS.keySet())) {
                if (COOLDOWNS.get(teamId) <= now) {
                    COOLDOWNS.remove(teamId);
                    scheduleCooldowns(progress(teamId));
                    evaluate(teamId, null);
                    ActiveTasks.refreshTeam(running, teamId);
                }
            }
        }
        if (!DIRTY.isEmpty() && ++saveTimer >= SAVE_DELAY_TICKS) {
            saveAll();
        }
    }

    public static void saveAll() {
        saveTimer = 0;
        if (storage == null) {
            return;
        }
        for (UUID teamId : List.copyOf(DIRTY)) {
            TeamProgress progress = TEAMS.get(teamId);
            if (progress != null) {
                try {
                    storage.write(progress);
                } catch (IOException e) {
                    HibiscusQuests.LOGGER.error("Couldn't save quest progress for {}", teamId, e);
                    continue;
                }
            }
            DIRTY.remove(teamId);
        }
    }

    // A team's progress, loaded from its file the first time it's needed.
    public static TeamProgress progress(UUID teamId) {
        TeamProgress progress = TEAMS.get(teamId);
        if (progress == null) {
            progress = storage == null ? new TeamProgress(teamId) : storage.load(teamId).orElseGet(() -> new TeamProgress(teamId));
            TEAMS.put(teamId, progress);
            scheduleCooldowns(progress);
        }
        return progress;
    }

    // What /hibiscusquests cleanup found (or did): teams looked at, teams with progress for quests no longer in the
    // book, and how many quests' progress that is.
    public record CleanupReport(int teams, int teamsChanged, int quests) {}

    // Progress is kept for quests that leave the book (so putting a quest back keeps it); cleanup drops it, for every
    // team with a progress file, online or not. With apply false it only counts.
    public static CleanupReport cleanup(boolean apply) {
        QuestBook book = BookManager.book();
        Set<String> questKeys = new HashSet<>();
        book.quests().forEach(quest -> questKeys.add(quest.ref().toString()));
        Set<String> chapterIds = new HashSet<>();
        book.chapters().forEach(chapter -> chapterIds.add(chapter.id()));
        Set<UUID> teamIds = new LinkedHashSet<>(TEAMS.keySet());
        if (storage != null && Files.isDirectory(storage.directory())) {
            try (Stream<Path> files = Files.list(storage.directory())) {
                files.map(path -> path.getFileName().toString()).filter(name -> name.matches("[0-9a-f-]{36}\\.snbt"))
                        .forEach(name -> teamIds.add(UUID.fromString(name.substring(0, 36))));
            } catch (IOException e) {
                HibiscusQuests.LOGGER.warn("Couldn't list the progress files", e);
            }
        }
        int changed = 0;
        int quests = 0;
        for (UUID teamId : teamIds) {
            TeamProgress progress = progress(teamId);
            if (storage != null && storage.isBroken(teamId)) {
                continue;
            }
            int missing = progress.countMissing(questKeys);
            if (missing == 0) {
                continue;
            }
            changed++;
            quests += missing;
            if (apply) {
                progress.removeMissing(questKeys, chapterIds);
                DIRTY.add(teamId);
                QuestSync.queueFull(teamId);
            }
        }
        if (apply) {
            saveAll();
        }
        return new CleanupReport(teamIds.size(), changed, quests);
    }

    public static TeamProgress progressOf(ServerPlayer player) {
        return progress(QuestTeams.teamIdOf(player));
    }

    private static void changed(UUID teamId, QuestRef quest) {
        DIRTY.add(teamId);
        QuestSync.queueQuest(teamId, quest.toString());
    }

    private static void scheduleCooldowns(TeamProgress progress) {
        long now = QuestClock.now();
        long next = Long.MAX_VALUE;
        for (QuestState state : progress.quests.values()) {
            if (state.cooldownUntil > now) {
                next = Math.min(next, state.cooldownUntil);
            }
        }
        if (next != Long.MAX_VALUE) {
            COOLDOWNS.merge(progress.teamId(), next, Math::min);
        }
    }

    // ---- task progress ----

    // Sets a task's progress for a team (clamped to 0..target), then checks for completion. Returns whether it changed.
    public static boolean setTaskProgress(UUID teamId, Quest quest, QuestTask task, long value, @Nullable ServerPlayer cause) {
        long clamped = Math.max(0, Math.min(task.target(), value));
        TeamProgress progress = progress(teamId);
        QuestState state = progress.stateOrCreate(quest.ref());
        if (state.isCompleted()) {
            return false;
        }
        long old = state.task(task.id());
        if (old == clamped) {
            return false;
        }
        state.tasks.put(task.id(), clamped);
        if (state.started == 0) {
            state.started = QuestClock.now();
        }
        changed(teamId, quest.ref());
        NeoForge.EVENT_BUS.post(new TaskProgressEvent(teamId, cause, quest.ref(), task.id(), old, clamped, task.target()));
        boolean completedAny = evaluate(teamId, cause);
        if (clamped >= task.target() || completedAny) {
            refresh(teamId);
        }
        return true;
    }

    // Completes every quest that's unlocked with its tasks done, repeatedly, so one completion can carry the next ones.
    // Returns whether any completed.
    public static boolean evaluate(UUID teamId, @Nullable ServerPlayer cause) {
        QuestBook book = BookManager.book();
        TeamProgress progress = progress(teamId);
        long now = QuestClock.now();
        List<Quest> completed = new ArrayList<>();
        boolean again = true;
        while (again) {
            again = false;
            for (Quest quest : book.quests()) {
                if (!progress.isCompleted(quest.ref()) && QuestLogic.tasksDone(progress, quest)
                        && QuestLogic.isUnlocked(book, progress, quest, now)) {
                    markCompleted(teamId, progress, quest, cause, now);
                    completed.add(quest);
                    again = true;
                }
            }
        }
        for (Quest quest : completed) {
            autoClaim(teamId, quest, cause);
            maybeRepeat(teamId, quest, cause == null ? null : cause.getUUID());
        }
        return !completed.isEmpty();
    }

    private static void markCompleted(UUID teamId, TeamProgress progress, Quest quest, @Nullable ServerPlayer cause, long now) {
        QuestState state = progress.stateOrCreate(quest.ref());
        state.completed = now;
        if (state.started == 0) {
            state.started = now;
        }
        state.completedBy = cause == null ? null : cause.getUUID();
        changed(teamId, quest.ref());
        QuestSync.queueCompleted(teamId, quest.ref());
        NeoForge.EVENT_BUS.post(new QuestCompletedEvent(teamId, cause, quest.ref()));
        String chapterId = quest.chapter();
        ChapterStatus status = QuestLogic.chapterStatus(BookManager.book(), progress, chapterId);
        if (status.total() > 0 && status.isCompleted() && progress.completedChapters.add(chapterId)) {
            DIRTY.add(teamId);
            NeoForge.EVENT_BUS.post(new ChapterCompletedEvent(teamId, cause, chapterId));
            announceChapter(teamId, chapterId);
        }
    }

    private static void announceChapter(UUID teamId, String chapterId) {
        MinecraftServer running = server;
        if (running == null || !QuestsConfig.get(QuestsConfig.ANNOUNCE_CHAPTERS)) {
            return;
        }
        Component title = BookManager.book().chapter(chapterId).map(chapter -> BookText.titleOr(chapter.title(), chapterId))
                .orElse(Component.literal(chapterId));
        Component who = QuestTeams.mode() == ProgressMode.TEAM
                ? QuestTeams.teams().team(teamId).map(Team::displayName).orElseGet(() -> Profiles.displayName(running, teamId))
                : Profiles.displayName(running, teamId);
        running.getPlayerList().broadcastSystemMessage(Component.translatable("hibiscusquests.message.chapter_announce", who, title), false);
    }

    // ---- commands and the API ----

    // Completes a quest's tasks for a team (it completes once it's unlocked).
    public static void completeTasks(UUID teamId, Quest quest, @Nullable ServerPlayer cause) {
        TeamProgress progress = progress(teamId);
        QuestState state = progress.stateOrCreate(quest.ref());
        if (state.isCompleted()) {
            return;
        }
        for (QuestTask task : quest.tasks()) {
            state.tasks.put(task.id(), task.target());
        }
        if (state.started == 0) {
            state.started = QuestClock.now();
        }
        changed(teamId, quest.ref());
        evaluate(teamId, cause);
        refresh(teamId);
    }

    // Marks a quest complete for a team whether or not it's unlocked (change_progress complete), then carries on.
    public static void forceComplete(UUID teamId, Quest quest, @Nullable ServerPlayer cause) {
        TeamProgress progress = progress(teamId);
        QuestState state = progress.stateOrCreate(quest.ref());
        if (state.isCompleted()) {
            return;
        }
        for (QuestTask task : quest.tasks()) {
            state.tasks.put(task.id(), task.target());
        }
        markCompleted(teamId, progress, quest, cause, QuestClock.now());
        autoClaim(teamId, quest, cause);
        evaluate(teamId, cause);
        refresh(teamId);
    }

    // Forgets everything about a quest for a team, including claims and repeats (change_progress reset).
    public static void reset(UUID teamId, Quest quest) {
        TeamProgress progress = progress(teamId);
        progress.quests.remove(quest.ref().toString());
        progress.completedChapters.remove(quest.chapter());
        changed(teamId, quest.ref());
        refresh(teamId);
    }

    private static void refresh(UUID teamId) {
        if (server != null) {
            ActiveTasks.refreshTeam(server, teamId);
        }
    }

    // ---- rewards ----

    public static ClaimResult claim(ServerPlayer player, QuestRef ref, String rewardId) {
        return claim(player, ref, rewardId, -1);
    }

    // A claim, with the option picked for a choice reward (-1 for none).
    public static ClaimResult claim(ServerPlayer player, QuestRef ref, String rewardId, int choice) {
        Quest quest = BookManager.book().quest(ref).orElse(null);
        QuestReward reward = quest == null ? null : quest.reward(rewardId).orElse(null);
        if (quest == null || reward == null) {
            return ClaimResult.UNKNOWN;
        }
        UUID teamId = QuestTeams.teamIdOf(player);
        ClaimResult result = claim(player, teamId, quest, reward, choice);
        if (result.claimed()) {
            maybeRepeat(teamId, quest, player.getUUID());
        }
        return result;
    }

    private static ClaimResult claim(ServerPlayer player, UUID teamId, Quest quest, QuestReward reward) {
        return claim(player, teamId, quest, reward, -1);
    }

    private static ClaimResult claim(ServerPlayer player, UUID teamId, Quest quest, QuestReward reward, int choice) {
        TeamProgress progress = progress(teamId);
        QuestState state = progress.state(quest.ref());
        if (state == null || !state.isCompleted()) {
            return ClaimResult.NOT_COMPLETE;
        }
        if (state.isClaimed(reward.id(), reward.teamReward(), player.getUUID())) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        RewardContext context = new RewardContext(player, teamId, quest.ref(), reward.id(), reward.teamReward(),
                server == null ? List.of(player) : QuestTeams.onlineMembers(server, teamId), choice);
        if (NeoForge.EVENT_BUS.post(new RewardClaimedEvent.Pre(context, reward.reward())).isCanceled()) {
            return ClaimResult.CANCELLED;
        }
        if (!Rewards.give(context, reward.reward())) {
            return ClaimResult.FAILED;
        }
        state.claim(reward.id(), reward.teamReward(), player.getUUID());
        changed(teamId, quest.ref());
        NeoForge.EVENT_BUS.post(new RewardClaimedEvent.Post(context, reward.reward()));
        return ClaimResult.CLAIMED;
    }

    // Claims every reward the player can claim now, except those left out of Claim all and choices (someone has to
    // pick). Returns how many.
    public static int claimAll(ServerPlayer player) {
        UUID teamId = QuestTeams.teamIdOf(player);
        TeamProgress progress = progress(teamId);
        int count = 0;
        for (Quest quest : BookManager.book().quests()) {
            QuestState state = progress.state(quest.ref());
            if (state == null || !state.isCompleted()) {
                continue;
            }
            boolean any = false;
            for (QuestReward reward : quest.rewards()) {
                if (!reward.excludeFromClaimAll() && !(reward.reward() instanceof ChoiceReward)
                        && !state.isClaimed(reward.id(), reward.teamReward(), player.getUUID())
                        && claim(player, teamId, quest, reward).claimed()) {
                    count++;
                    any = true;
                }
            }
            if (any) {
                maybeRepeat(teamId, quest, player.getUUID());
            }
        }
        return count;
    }

    // Hands out a newly completed quest's auto-claim rewards: a team reward to the player who completed it (else the
    // first member online), a per-player reward to every member online. Offline members get theirs when they join.
    private static void autoClaim(UUID teamId, Quest quest, @Nullable ServerPlayer cause) {
        if (server == null) {
            return;
        }
        List<ServerPlayer> online = QuestTeams.onlineMembers(server, teamId);
        for (QuestReward reward : quest.rewards()) {
            if (reward.autoClaim() == AutoClaim.OFF || reward.reward() instanceof ChoiceReward) {
                continue;
            }
            if (reward.teamReward()) {
                ServerPlayer claimer = cause != null && online.contains(cause) ? cause : online.isEmpty() ? null : online.getFirst();
                if (claimer != null) {
                    claim(claimer, teamId, quest, reward);
                }
            } else {
                online.forEach(member -> claim(member, teamId, quest, reward));
            }
        }
    }

    // The auto-claim rewards a player missed while offline.
    private static void autoClaimMissed(ServerPlayer player, UUID teamId) {
        TeamProgress progress = progress(teamId);
        for (Quest quest : BookManager.book().quests()) {
            QuestState state = progress.state(quest.ref());
            if (state == null || !state.isCompleted()) {
                continue;
            }
            for (QuestReward reward : quest.rewards()) {
                if (reward.autoClaim() != AutoClaim.OFF && !(reward.reward() instanceof ChoiceReward)
                        && !state.isClaimed(reward.id(), reward.teamReward(), player.getUUID())) {
                    claim(player, teamId, quest, reward);
                }
            }
        }
    }

    // A repeatable quest starts over once everything has been claimed from it: team rewards by anyone, per-player
    // rewards by the player who just claimed (or, with no player, a quest with no rewards). With
    // can_repeat_without_claiming it starts over as soon as it's complete; the completer's rewards (all but choices)
    // are handed out first, since the next round clears them. Its repeat_cooldown (seconds) then has to pass before its
    // tasks count again.
    private static void maybeRepeat(UUID teamId, Quest quest, @Nullable UUID player) {
        if (!quest.rules().repeatable() || quest.tasks().isEmpty()) {
            return;
        }
        TeamProgress progress = progress(teamId);
        QuestState state = progress.state(quest.ref());
        if (state == null || !state.isCompleted()) {
            return;
        }
        if (quest.rules().canRepeatWithoutClaiming()) {
            ServerPlayer claimer = player == null || server == null ? null : server.getPlayerList().getPlayer(player);
            if (claimer != null) {
                for (QuestReward reward : quest.rewards()) {
                    if (!(reward.reward() instanceof ChoiceReward) && !state.isClaimed(reward.id(), reward.teamReward(), player)) {
                        claim(claimer, teamId, quest, reward);
                    }
                }
            }
            restart(teamId, progress, quest, state);
            return;
        }
        for (QuestReward reward : quest.rewards()) {
            boolean claimed = reward.teamReward() ? state.teamClaims.contains(reward.id())
                    : player != null && state.isClaimed(reward.id(), false, player);
            if (!claimed) {
                return;
            }
        }
        restart(teamId, progress, quest, state);
    }

    private static void restart(UUID teamId, TeamProgress progress, Quest quest, QuestState state) {
        state.clear();
        state.repeats++;
        if (quest.rules().repeatCooldown() > 0) {
            state.cooldownUntil = QuestClock.now() + quest.rules().repeatCooldown() * 1000;
            COOLDOWNS.merge(teamId, state.cooldownUntil, Math::min);
        }
        progress.completedChapters.remove(quest.chapter());
        changed(teamId, quest.ref());
        refresh(teamId);
    }

    // ---- pins ----

    // Pins or unpins a quest on the player's HUD (up to the server's maxPinnedQuests). Returns whether it's done.
    public static boolean setPinned(ServerPlayer player, QuestRef quest, boolean pin) {
        if (pin && BookManager.book().quest(quest).isEmpty()) {
            return false;
        }
        UUID teamId = QuestTeams.teamIdOf(player);
        TeamProgress progress = progress(teamId);
        boolean done = progress.setPinned(player.getUUID(), quest.toString(), pin, QuestsConfig.get(QuestsConfig.MAX_PINNED));
        if (done) {
            changed(teamId, quest);
        }
        return done;
    }

    // ---- lifecycle ----

    public static void onLogin(ServerPlayer player) {
        UUID teamId = QuestTeams.teamIdOf(player);
        evaluate(teamId, player);
        autoClaimMissed(player, teamId);
        QuestSync.sendProgress(player);
        ActiveTasks.refresh(player);
    }

    public static void onLogout(ServerPlayer player) {
        ActiveTasks.clear(player);
    }

    // The book changed: progress catches up (a quest may now be complete or unlocked) and every task is re-tracked.
    public static void onBookChanged(MinecraftServer running) {
        Set<UUID> online = new HashSet<>();
        running.getPlayerList().getPlayers().forEach(player -> online.add(QuestTeams.teamIdOf(player)));
        for (UUID teamId : online) {
            evaluate(teamId, null);
        }
        ActiveTasks.refreshAll(running);
    }

    // A player's team changed (Teams API): apply team_change, then resend their progress and re-track their tasks.
    public static void onTeamChanged(PlayerJoinedTeamEvent event) {
        if (server != null && QuestTeams.mode() == ProgressMode.TEAM) {
            applyTeamChange(server, event.playerId(), event.from().id(), event.to().id(), QuestTeams.teamChange());
        }
    }

    public static void applyTeamChange(MinecraftServer running, UUID playerId, UUID fromId, UUID toId, TeamChange mode) {
        if (fromId.equals(toId)) {
            return;
        }
        TeamProgress from = progress(fromId);
        TeamProgress to = progress(toId);
        boolean changedTo = switch (mode) {
            case KEEP_WITH_TEAM -> false;
            case COPY_TO_NEW_TEAM -> {
                boolean copy = to.isEmpty() && !from.isEmpty();
                if (copy) {
                    to.mergeFrom(from);
                }
                yield copy;
            }
            case MERGE -> {
                to.mergeFrom(from);
                yield !from.isEmpty();
            }
        };
        if (changedTo) {
            DIRTY.add(toId);
            scheduleCooldowns(to);
            evaluate(toId, null);
            QuestSync.queueFull(toId);
        }
        ServerPlayer player = running.getPlayerList().getPlayer(playerId);
        if (player != null) {
            QuestSync.sendProgress(player);
        }
        ActiveTasks.refreshTeam(running, toId);
        ActiveTasks.refreshTeam(running, fromId);
    }

    // A party is about to be deleted: archive its progress (members' team-change events follow, and may still read it).
    public static void onTeamDeleted(TeamDeletedEvent event) {
        UUID teamId = event.team().id();
        if (storage == null || QuestTeams.mode() != ProgressMode.TEAM) {
            return;
        }
        TeamProgress progress = progress(teamId);
        try {
            if (!progress.isEmpty()) {
                storage.archive(progress).ifPresent(path -> HibiscusQuests.LOGGER.info("Archived quest progress of deleted team {} to {}", teamId, path));
            }
        } catch (IOException e) {
            HibiscusQuests.LOGGER.error("Couldn't archive quest progress of deleted team {}", teamId, e);
        }
        DIRTY.remove(teamId);
        DELETED.add(teamId);
    }

    // For tests: forget a team's in-memory progress without saving it.
    public static void forget(UUID teamId) {
        TEAMS.remove(teamId);
        DIRTY.remove(teamId);
        COOLDOWNS.remove(teamId);
    }
}

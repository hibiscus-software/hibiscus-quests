/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;

// The tasks each online player is tracking (see TaskType): the unfinished tasks of their team's quests that QuestLogic
// says are active. refresh works out the set again and tells task types what started and stopped (onActivated /
// onDeactivated); tick polls the types that poll. A task's activation can change progress straight away (an
// advancement the player already has), which asks for another refresh; that's run after the current one, not inside it.
public final class ActiveTasks {
    private record Key(QuestRef quest, String taskId) {}

    // One active task for one player, handed to task types.
    static final class Handle<T extends Task> implements ActiveTask<T> {
        private final ServerPlayer player;
        private final UUID teamId;
        private final Quest quest;
        private final QuestTask entry;
        private final T task;
        private boolean active = true;

        Handle(ServerPlayer player, UUID teamId, Quest quest, QuestTask entry, T task) {
            this.player = player;
            this.teamId = teamId;
            this.quest = quest;
            this.entry = entry;
            this.task = task;
        }

        @Override
        public ServerPlayer player() {
            return player;
        }

        @Override
        public UUID teamId() {
            return teamId;
        }

        @Override
        public QuestRef quest() {
            return quest.ref();
        }

        @Override
        public String taskId() {
            return entry.id();
        }

        @Override
        public T task() {
            return task;
        }

        QuestTask entry() {
            return entry;
        }

        @Override
        public long progress() {
            return ProgressManager.progress(teamId).task(quest.ref(), entry.id());
        }

        @Override
        public void setProgress(long progress) {
            if (active) {
                ProgressManager.setTaskProgress(teamId, quest, entry, progress, player);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @SuppressWarnings("unchecked")
        TaskType<T> type() {
            return (TaskType<T>) task.type();
        }
    }

    private static final class PlayerTasks {
        @Nullable UUID teamId;
        @Nullable QuestBook book;
        final Map<Key, Handle<?>> active = new LinkedHashMap<>();
        boolean refreshing;
        boolean again;
    }

    private static final Map<UUID, PlayerTasks> PLAYERS = new HashMap<>();

    private ActiveTasks() {}

    public static void refresh(ServerPlayer player) {
        PlayerTasks tasks = PLAYERS.computeIfAbsent(player.getUUID(), key -> new PlayerTasks());
        if (tasks.refreshing) {
            tasks.again = true;
            return;
        }
        tasks.refreshing = true;
        try {
            int rounds = 0;
            do {
                tasks.again = false;
                update(player, tasks);
            } while (tasks.again && ++rounds < 64);
        } finally {
            tasks.refreshing = false;
        }
    }

    private static void update(ServerPlayer player, PlayerTasks tasks) {
        QuestBook book = BookManager.book();
        UUID teamId = QuestTeams.teamIdOf(player);
        if (tasks.book != book || !teamId.equals(tasks.teamId)) {
            deactivateAll(tasks);
            tasks.book = book;
            tasks.teamId = teamId;
        }
        TeamProgress progress = ProgressManager.progress(teamId);
        long now = QuestClock.now();
        Map<Key, Handle<?>> wanted = new LinkedHashMap<>();
        for (Quest quest : book.quests()) {
            for (QuestTask entry : QuestLogic.activeTasks(book, progress, quest, now)) {
                Key key = new Key(quest.ref(), entry.id());
                Handle<?> existing = tasks.active.get(key);
                wanted.put(key, existing != null ? existing : new Handle<>(player, teamId, quest, entry, entry.task()));
            }
        }
        List<Handle<?>> stopped = new ArrayList<>();
        tasks.active.entrySet().removeIf(entry -> {
            if (!wanted.containsKey(entry.getKey())) {
                stopped.add(entry.getValue());
                return true;
            }
            return false;
        });
        stopped.forEach(ActiveTasks::deactivate);
        List<Handle<?>> started = new ArrayList<>();
        wanted.forEach((key, handle) -> {
            if (!tasks.active.containsKey(key)) {
                tasks.active.put(key, handle);
                started.add(handle);
            }
        });
        for (Handle<?> handle : started) {
            if (handle.isActive()) {
                activate(handle);
            }
        }
    }

    private static <T extends Task> void activate(Handle<T> handle) {
        try {
            handle.type().onActivated(handle);
        } catch (RuntimeException e) {
            HibiscusQuests.LOGGER.error("Task {} of {} failed to start", handle.taskId(), handle.quest(), e);
        }
    }

    private static <T extends Task> void deactivate(Handle<T> handle) {
        if (!handle.active) {
            return;
        }
        handle.active = false;
        try {
            handle.type().onDeactivated(handle);
        } catch (RuntimeException e) {
            HibiscusQuests.LOGGER.error("Task {} of {} failed to stop", handle.taskId(), handle.quest(), e);
        }
    }

    private static void deactivateAll(PlayerTasks tasks) {
        List<Handle<?>> all = new ArrayList<>(tasks.active.values());
        tasks.active.clear();
        all.forEach(ActiveTasks::deactivate);
    }

    public static void refreshTeam(MinecraftServer server, UUID teamId) {
        for (ServerPlayer member : QuestTeams.onlineMembers(server, teamId)) {
            refresh(member);
        }
        // Players who just left the team still have its tasks; refresh them too.
        for (Map.Entry<UUID, PlayerTasks> entry : List.copyOf(PLAYERS.entrySet())) {
            if (teamId.equals(entry.getValue().teamId)) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    refresh(player);
                }
            }
        }
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    public static void clear(ServerPlayer player) {
        PlayerTasks tasks = PLAYERS.remove(player.getUUID());
        if (tasks != null) {
            deactivateAll(tasks);
        }
    }

    public static void clearAll() {
        for (PlayerTasks tasks : List.copyOf(PLAYERS.values())) {
            deactivateAll(tasks);
        }
        PLAYERS.clear();
    }

    // Polls the active tasks of the types that poll, each on its own interval, spread over ticks by player.
    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTasks tasks = PLAYERS.get(player.getUUID());
            if (tasks == null || tasks.active.isEmpty()) {
                continue;
            }
            int offset = player.getUUID().hashCode() & 0xFFFF;
            for (Handle<?> handle : List.copyOf(tasks.active.values())) {
                poll(handle, tick + offset);
            }
        }
    }

    private static <T extends Task> void poll(Handle<T> handle, int tick) {
        TaskType<T> type = handle.type();
        int interval = type.pollInterval();
        if (interval > 0 && handle.isActive() && tick % interval == 0) {
            try {
                type.poll(handle);
            } catch (RuntimeException e) {
                HibiscusQuests.LOGGER.error("Task {} of {} failed to update", handle.taskId(), handle.quest(), e);
            }
        }
    }

    // The player's active tasks of a type.
    @SuppressWarnings("unchecked")
    public static <T extends Task> List<ActiveTask<T>> of(ServerPlayer player, TaskType<T> type) {
        PlayerTasks tasks = PLAYERS.get(player.getUUID());
        if (tasks == null) {
            return List.of();
        }
        List<ActiveTask<T>> list = new ArrayList<>();
        for (Handle<?> handle : tasks.active.values()) {
            if (handle.task().type() == type && handle.isActive()) {
                list.add((ActiveTask<T>) handle);
            }
        }
        return list;
    }

    // One active task of the player's, if it's active (for Submit).
    public static Optional<ActiveTask<?>> find(ServerPlayer player, QuestRef quest, String taskId) {
        PlayerTasks tasks = PLAYERS.get(player.getUUID());
        Handle<?> handle = tasks == null ? null : tasks.active.get(new Key(quest, taskId));
        return handle != null && handle.isActive() ? Optional.of(handle) : Optional.empty();
    }

    // Submits an active task for the player, if its type takes submissions. Returns whether it did.
    public static boolean submit(ServerPlayer player, QuestRef quest, String taskId) {
        PlayerTasks tasks = PLAYERS.get(player.getUUID());
        Handle<?> handle = tasks == null ? null : tasks.active.get(new Key(quest, taskId));
        return handle != null && handle.isActive() && submit(handle);
    }

    private static <T extends Task> boolean submit(Handle<T> handle) {
        TaskType<T> type = handle.type();
        if (!type.canSubmit(handle.task())) {
            return false;
        }
        type.submit(handle);
        return true;
    }
}

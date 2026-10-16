/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscuslib.util.PermissionLevels;
import software.hibiscus.hibiscuslib.util.Profiles;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ProgressMode;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestLogic;
import software.hibiscus.hibiscusquests.progress.QuestTeams;
import software.hibiscus.hibiscusquests.progress.TeamProgress;
import software.hibiscus.hibiscusquests.task.CriterionTask;

// /hibiscusquests (alias /hquests). Players: claim_all. Ops (hibiscusquests.admin.* nodes): reload, change_progress
// and debug. Player and team arguments take online or offline players by name (through Hibiscus Lib's Profiles), a
// team's short name, or a team id.
public final class QuestsCommand {
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (context, builder) -> {
        List<String> options = new ArrayList<>();
        for (Quest quest : BookManager.book().quests()) {
            options.add(quest.ref().toString());
        }
        return SharedSuggestionProvider.suggest(options, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> QUESTS_AND_CHAPTERS = (context, builder) -> {
        List<String> options = new ArrayList<>();
        options.add("*");
        for (Chapter chapter : BookManager.book().chapters()) {
            options.add(chapter.id());
        }
        for (Quest quest : BookManager.book().quests()) {
            options.add(quest.ref().toString());
        }
        return SharedSuggestionProvider.suggest(options, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> TARGETS = (context, builder) -> {
        List<String> options = new ArrayList<>(List.of(context.getSource().getServer().getPlayerNames()));
        if (QuestTeams.mode() == ProgressMode.TEAM) {
            QuestTeams.teams().allTeams().forEach(team -> options.add(team.shortName()));
        }
        return SharedSuggestionProvider.suggest(options.stream().distinct().toList(), builder);
    };

    private QuestsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("hibiscusquests"));
        dispatcher.register(build("hquests"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String root) {
        return Commands.literal(root)
                .then(Commands.literal("claim_all")
                        .executes(QuestsCommand::claimAll))
                .then(Commands.literal("reload")
                        .requires(PermissionLevels.requires(QuestPermissions.RELOAD, PermissionLevel.GAMEMASTERS))
                        .executes(QuestsCommand::reload))
                .then(Commands.literal("change_progress")
                        .requires(PermissionLevels.requires(QuestPermissions.CHANGE_PROGRESS, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("target", StringArgumentType.word()).suggests(TARGETS)
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("quests", StringArgumentType.greedyString()).suggests(QUESTS_AND_CHAPTERS)
                                                .executes(context -> changeProgress(context, false))))
                                .then(Commands.literal("complete")
                                        .then(Commands.argument("quests", StringArgumentType.greedyString()).suggests(QUESTS_AND_CHAPTERS)
                                                .executes(context -> changeProgress(context, true))))))
                .then(Commands.literal("debug")
                        .requires(PermissionLevels.requires(QuestPermissions.DEBUG, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("quest", StringArgumentType.greedyString()).suggests(QUESTS)
                                .executes(QuestsCommand::debug)));
    }

    private static int claimAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int claimed = ProgressManager.claimAll(player);
        if (claimed == 0) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.claim_all.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.claim_all.done", claimed), false);
        return claimed;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        List<String> warnings = BookManager.reload(context.getSource().getServer());
        QuestBook book = BookManager.book();
        context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.reload.done", book.chapters().size(),
                book.questCount()), true);
        for (String warning : warnings.subList(0, Math.min(10, warnings.size()))) {
            context.getSource().sendSystemMessage(Component.literal(warning).withStyle(ChatFormatting.YELLOW));
        }
        if (warnings.size() > 10) {
            context.getSource().sendSystemMessage(Component.translatable("hibiscusquests.command.reload.more_warnings", warnings.size() - 10)
                    .withStyle(ChatFormatting.YELLOW));
        }
        return Math.max(1, book.questCount());
    }

    // The team a target argument means: a player's (online or not), a team's short name, or a team id.
    private record Target(UUID teamId, Component name, Optional<ServerPlayer> player) {}

    private static Optional<Target> target(MinecraftServer server, String text) {
        // A UUID is a team id (a player's solo team id is their UUID). Checked first: an offline-mode server would
        // otherwise make up a player UUID for it as if it were a name.
        Optional<UUID> asId = parseUuid(text);
        if (asId.isPresent()) {
            ServerPlayer player = server.getPlayerList().getPlayer(asId.get());
            UUID teamId = player != null ? QuestTeams.teamIdOf(player) : asId.get();
            return Optional.of(new Target(teamId, Component.literal(text), Optional.ofNullable(player)));
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(text);
        if (online != null) {
            return Optional.of(new Target(QuestTeams.teamIdOf(online), online.getDisplayName(), Optional.of(online)));
        }
        Optional<UUID> player = Profiles.idOf(server, text);
        if (player.isPresent()) {
            return Optional.of(new Target(QuestTeams.teamIdOf(player.get()), Profiles.displayName(server, player.get()), Optional.empty()));
        }
        for (Team team : QuestTeams.teams().allTeams()) {
            if (team.shortName().equalsIgnoreCase(text)) {
                return Optional.of(new Target(team.id(), team.displayName(), Optional.empty()));
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> parseUuid(String text) {
        try {
            return text.length() == 36 ? Optional.of(UUID.fromString(text)) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // The quests a quests argument means: *, a chapter id, or chapter/quest.
    private static List<Quest> quests(QuestBook book, String text) {
        String spec = text.strip();
        List<Quest> list = new ArrayList<>();
        if (spec.equals("*")) {
            book.quests().forEach(list::add);
        } else if (spec.indexOf('/') >= 0) {
            QuestRef.parse(spec).flatMap(book::quest).ifPresent(list::add);
        } else {
            book.chapter(spec).ifPresent(chapter -> list.addAll(chapter.quests()));
        }
        return list;
    }

    private static int changeProgress(CommandContext<CommandSourceStack> context, boolean complete) {
        CommandSourceStack source = context.getSource();
        String targetText = StringArgumentType.getString(context, "target");
        String questsText = StringArgumentType.getString(context, "quests");
        Optional<Target> target = target(source.getServer(), targetText);
        if (target.isEmpty()) {
            source.sendFailure(Component.translatable("hibiscusquests.command.unknown_target", targetText));
            return 0;
        }
        List<Quest> quests = quests(BookManager.book(), questsText);
        if (quests.isEmpty()) {
            source.sendFailure(Component.translatable("hibiscusquests.command.unknown_quests", questsText));
            return 0;
        }
        UUID teamId = target.get().teamId();
        ServerPlayer cause = target.get().player().orElse(null);
        for (Quest quest : quests) {
            if (complete) {
                ProgressManager.forceComplete(teamId, quest, cause);
            } else {
                ProgressManager.reset(teamId, quest);
            }
        }
        int count = quests.size();
        Component name = target.get().name();
        source.sendSuccess(() -> Component.translatable(complete ? "hibiscusquests.command.change_progress.completed"
                : "hibiscusquests.command.change_progress.reset", count, name), true);
        return count;
    }

    private static int debug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String text = StringArgumentType.getString(context, "quest").strip();
        QuestBook book = BookManager.book();
        Optional<Quest> found = QuestRef.parse(text).flatMap(book::quest);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("hibiscusquests.command.unknown_quests", text));
            return 0;
        }
        Quest quest = found.get();
        UUID teamId = QuestTeams.teamIdOf(player);
        TeamProgress progress = ProgressManager.progress(teamId);
        long now = QuestClock.now();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hibiscusquests.command.debug.header", BookText.titleOr(quest.title(), quest.id()),
                quest.ref().toString(), teamId.toString()).withStyle(ChatFormatting.GOLD));
        if (progress.isCompleted(quest.ref())) {
            lines.add(Component.translatable("hibiscusquests.command.debug.complete").withStyle(ChatFormatting.GREEN));
        } else {
            List<Component> reasons = QuestLogic.lockReasons(book, progress, quest, now, true);
            if (reasons.isEmpty()) {
                lines.add(Component.translatable("hibiscusquests.command.debug.unlocked").withStyle(ChatFormatting.GREEN));
            } else {
                lines.add(Component.translatable("hibiscusquests.command.debug.locked").withStyle(ChatFormatting.RED));
                reasons.forEach(reason -> lines.add(Component.literal("  - ").append(reason)));
            }
        }
        List<QuestTask> active = QuestLogic.activeTasks(book, progress, quest, now);
        for (QuestTask task : quest.tasks()) {
            long value = progress.task(quest.ref(), task.id());
            MutableComponent line = Component.translatable("hibiscusquests.command.debug.task", task.id(),
                    task.title().map(BookText::component).orElseGet(() -> task.task().title()), value, task.target());
            if (task.optional()) {
                line.append(Component.translatable("hibiscusquests.command.debug.optional"));
            }
            if (active.contains(task)) {
                line.append(Component.translatable("hibiscusquests.command.debug.tracked"));
            }
            if (task.task() instanceof CriterionTask criterion) {
                line.append(Component.literal(" [" + criterion.trigger() + "]").withStyle(ChatFormatting.GRAY));
            }
            lines.add(line.withStyle(value >= task.target() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        lines.forEach(source::sendSystemMessage);
        return 1;
    }
}

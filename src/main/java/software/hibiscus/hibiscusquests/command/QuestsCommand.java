/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.command;

import java.io.IOException;
import java.nio.file.Path;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscuslib.util.PermissionLevels;
import software.hibiscus.hibiscuslib.util.Profiles;
import software.hibiscus.hibiscusquests.QuestItems;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterExport;
import software.hibiscus.hibiscusquests.book.PackArchive;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.ProgressMode;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.config.QuestsConfig;
import software.hibiscus.hibiscusquests.edit.BookEditor;
import software.hibiscus.hibiscusquests.edit.ChapterGenerator;
import software.hibiscus.hibiscusquests.edit.EditingMode;
import software.hibiscus.hibiscusquests.edit.Ids;
import software.hibiscus.hibiscusquests.edit.LangExport;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestLogic;
import software.hibiscus.hibiscusquests.progress.QuestTeams;
import software.hibiscus.hibiscusquests.progress.TeamProgress;
import software.hibiscus.hibiscusquests.task.CriterionTask;
import org.jspecify.annotations.Nullable;

// /hibiscusquests (alias /hquests). Players: open [quest], pin <quest>, claim_all, emergency and locale [locale]. Ops
// (hibiscusquests.admin.* nodes): reload, change_progress, debug, export, export_chapter, import and cleanup; editors
// (hibiscusquests.editor): editing_mode, generate_chapter and export_lang. Player and team arguments take online or offline players by name (through Hibiscus
// Lib's Profiles), a team's short name, or a team id.
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
    private static final SuggestionProvider<CommandSourceStack> CHAPTERS = (context, builder) -> SharedSuggestionProvider.suggest(
            BookManager.book().chapters().stream().map(Chapter::id).toList(), builder);
    private static final SuggestionProvider<CommandSourceStack> ZIPS = (context, builder) -> SharedSuggestionProvider.suggest(
            PackArchive.zips(context.getSource().getServer()), builder);
    private static final SuggestionProvider<CommandSourceStack> LOCALES = (context, builder) -> {
        List<String> options = new ArrayList<>(BookManager.book().lang().keySet());
        options.addAll(List.of("en_us", "de_de", "es_es", "fr_fr", "ja_jp", "pt_br", "ru_ru", "zh_cn"));
        return SharedSuggestionProvider.suggest(options.stream().distinct().toList(), builder);
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
                .then(Commands.literal("open")
                        .executes(context -> open(context, ""))
                        .then(Commands.argument("quest", StringArgumentType.greedyString()).suggests(QUESTS)
                                .executes(context -> open(context, StringArgumentType.getString(context, "quest").strip()))))
                .then(Commands.literal("pin")
                        .then(Commands.argument("quest", StringArgumentType.greedyString()).suggests(QUESTS)
                                .executes(QuestsCommand::pin)))
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
                                .executes(QuestsCommand::debug)))
                .then(Commands.literal("editing_mode")
                        .requires(PermissionLevels.requires(QuestPermissions.EDITOR, PermissionLevel.GAMEMASTERS))
                        .executes(context -> editingMode(context, null, null))
                        .then(Commands.literal("on")
                                .executes(context -> editingMode(context, true, null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> editingMode(context, true, EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("off")
                                .executes(context -> editingMode(context, false, null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> editingMode(context, false, EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("generate_chapter")
                        .requires(PermissionLevels.requires(QuestPermissions.EDITOR, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(QuestsCommand::generateChapter)))
                .then(Commands.literal("export_lang")
                        .requires(PermissionLevels.requires(QuestPermissions.EDITOR, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("locale", StringArgumentType.word()).suggests(LOCALES)
                                .executes(QuestsCommand::exportLang)))
                .then(Commands.literal("export")
                        .requires(PermissionLevels.requires(QuestPermissions.EXPORT, PermissionLevel.GAMEMASTERS))
                        .executes(QuestsCommand::export))
                .then(Commands.literal("export_chapter")
                        .requires(PermissionLevels.requires(QuestPermissions.EXPORT, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("chapter", StringArgumentType.word()).suggests(CHAPTERS)
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(QuestsCommand::exportChapter))))
                .then(Commands.literal("import")
                        .requires(PermissionLevels.requires(QuestPermissions.IMPORT, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("file", StringArgumentType.greedyString()).suggests(ZIPS)
                                .executes(QuestsCommand::importZip)))
                .then(Commands.literal("cleanup")
                        .requires(PermissionLevels.requires(QuestPermissions.CLEANUP, PermissionLevel.GAMEMASTERS))
                        .executes(context -> cleanup(context, false))
                        .then(Commands.literal("confirm")
                                .executes(context -> cleanup(context, true))))
                .then(Commands.literal("emergency")
                        .executes(QuestsCommand::emergency))
                .then(Commands.literal("locale")
                        .executes(context -> locale(context, ""))
                        .then(Commands.argument("locale", StringArgumentType.word()).suggests(LOCALES)
                                .executes(context -> locale(context, StringArgumentType.getString(context, "locale")))));
    }

    // Turns edit mode on or off (or over, with no argument) for the player running it or another one. Putting someone
    // else in edit mode lets them edit for the rest of their session, even without the editor permission.
    private static int editingMode(CommandContext<CommandSourceStack> context, @Nullable Boolean on, @Nullable ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : context.getSource().getPlayerOrException();
        boolean wanted = on != null ? on : !EditingMode.isEditing(player);
        boolean editing = EditingMode.set(player, wanted, target != null && target != context.getSource().getPlayer());
        context.getSource().sendSuccess(() -> Component.translatable(editing ? "hibiscusquests.command.editing.on" : "hibiscusquests.command.editing.off",
                player.getDisplayName()), true);
        return 1;
    }

    // A new chapter with an item task for each kind of item in the container the player is looking at, laid out on a
    // grid: a quick start for a chapter of "collect these" quests.
    private static int generateChapter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String title = StringArgumentType.getString(context, "title").strip();
        HitResult hit = player.pick(player.blockInteractionRange(), 0, false);
        Container container = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(block.getBlockPos()) instanceof Container found ? found : null;
        if (container == null) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.generate.no_container"));
            return 0;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<ItemStack> same = stacks.stream().filter(other -> ItemStack.isSameItemSameComponents(other, stack)).findFirst();
            if (same.isPresent()) {
                same.get().grow(stack.getCount());
            } else {
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.generate.empty"));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        String chapterId = Ids.fromTitle(title, "chapter", BookManager.book().chapters().stream().map(Chapter::id).toList());
        BookEditor.Result result = BookEditor.apply(server, ChapterGenerator.edits(server, chapterId, title, stacks));
        if (!result.ok()) {
            result.messages().forEach(message -> context.getSource().sendFailure(message));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.generate.done", chapterId, stacks.size()), true);
        return 1;
    }

    // Moves the pack's literal text to lang/<locale>.snbt (and the default locale's), keys in its place.
    private static int exportLang(CommandContext<CommandSourceStack> context) {
        String locale = StringArgumentType.getString(context, "locale");
        if (!PackFiles.isLocale(locale)) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.lang.bad_locale", locale));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        LangExport.Result export = LangExport.edits(server, locale);
        if (export.edits().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.lang.nothing", locale), false);
            return 1;
        }
        BookEditor.Result result = BookEditor.apply(server, export.edits());
        if (!result.ok()) {
            result.messages().forEach(message -> context.getSource().sendFailure(message));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.lang.done", export.texts(),
                PackFiles.LANG + "/" + locale + ".snbt"), true);
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        try {
            Path zip = PackArchive.export(server, "book");
            context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.export.done",
                    PackArchive.EXPORTS + "/" + zip.getFileName()), true);
            return 1;
        } catch (IOException e) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.export.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int exportChapter(CommandContext<CommandSourceStack> context) {
        String chapter = StringArgumentType.getString(context, "chapter");
        String namespace = StringArgumentType.getString(context, "namespace");
        if (!namespace.matches("[a-z0-9_.-]+")) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.export_chapter.bad_namespace", namespace));
            return 0;
        }
        if (BookManager.book().sourceChapter(chapter).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.edit.error.no_chapter", chapter));
            return 0;
        }
        try {
            ChapterExport.Result result = ChapterExport.export(context.getSource().getServer(), chapter, namespace);
            context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.export_chapter.done", chapter,
                    PackArchive.EXPORTS, result.files().size()), true);
            result.files().forEach(file -> context.getSource().sendSuccess(() -> Component.literal("  " + file).withStyle(ChatFormatting.GRAY), false));
            if (!chapter.startsWith(namespace + "_") && !chapter.equals(namespace)) {
                context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.export_chapter.prefix", namespace)
                        .withStyle(ChatFormatting.GOLD), false);
            }
            return 1;
        } catch (IOException e) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.export.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int importZip(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "file").strip();
        try {
            PackArchive.ImportResult result = PackArchive.importZip(context.getSource().getServer(), name);
            if (!result.problems().isEmpty()) {
                context.getSource().sendFailure(Component.translatable("hibiscusquests.command.import.refused", name));
                result.problems().stream().limit(10).forEach(problem -> context.getSource().sendFailure(Component.literal("  " + problem)));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.import.done", result.files(), name,
                    PackArchive.EXPORTS + "/" + result.backup().getFileName()), true);
            return 1;
        } catch (IOException e) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.import.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    // Counts (or, with confirm, drops) progress for quests no longer in the book.
    private static int cleanup(CommandContext<CommandSourceStack> context, boolean apply) {
        ProgressManager.CleanupReport report = ProgressManager.cleanup(apply);
        if (report.quests() == 0) {
            context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.cleanup.nothing", report.teams()), false);
            return 1;
        }
        String key = apply ? "hibiscusquests.command.cleanup.done" : "hibiscusquests.command.cleanup.dry_run";
        context.getSource().sendSuccess(() -> Component.translatable(key, report.quests(), report.teamsChanged(), report.teams()), apply);
        return 1;
    }

    // The book's emergency items (a fresh start for someone who lost everything), once per cooldown.
    private static int emergency(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BookSettings settings = BookManager.book().settings();
        if (settings.emergencyItems().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.emergency.none"));
            return 0;
        }
        long cooldown = settings.emergencyCooldown().orElseGet(() -> QuestsConfig.get(QuestsConfig.EMERGENCY_COOLDOWN)) * 1000L;
        long now = System.currentTimeMillis();
        long last = player.getData(QuestItems.EMERGENCY_USED);
        if (last > 0 && now - last < cooldown) {
            long seconds = (cooldown - (now - last) + 999) / 1000;
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.emergency.wait", seconds));
            return 0;
        }
        player.setData(QuestItems.EMERGENCY_USED, now);
        for (ItemStack stack : settings.emergencyItems()) {
            player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
        }
        context.getSource().sendSuccess(() -> Component.translatable("hibiscusquests.command.emergency.given"), false);
        return 1;
    }

    // Shows the player's book in another locale ("" goes back to their own language).
    private static int locale(CommandContext<CommandSourceStack> context, String locale) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!locale.isEmpty() && !PackFiles.isLocale(locale)) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.lang.bad_locale", locale));
            return 0;
        }
        QuestSync.previewLocale(player, locale);
        context.getSource().sendSuccess(() -> locale.isEmpty() ? Component.translatable("hibiscusquests.command.locale.reset")
                : Component.translatable("hibiscusquests.command.locale.preview", locale), false);
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> context, String quest) throws CommandSyntaxException {
        QuestSync.openBook(context.getSource().getPlayerOrException(), quest);
        return 1;
    }

    // Pins the quest to the player's HUD, or unpins it if it's pinned.
    private static int pin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String text = StringArgumentType.getString(context, "quest").strip();
        Optional<QuestRef> quest = QuestRef.parse(text).filter(ref -> BookManager.book().quest(ref).isPresent());
        if (quest.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.unknown_quests", text));
            return 0;
        }
        boolean pinned = ProgressManager.progressOf(player).pinned(player.getUUID()).contains(quest.get().toString());
        if (!ProgressManager.setPinned(player, quest.get(), !pinned)) {
            context.getSource().sendFailure(Component.translatable("hibiscusquests.command.pin.full", QuestsConfig.get(QuestsConfig.MAX_PINNED)));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(pinned ? "hibiscusquests.command.pin.removed" : "hibiscusquests.command.pin.added",
                quest.get().toString()), false);
        return 1;
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

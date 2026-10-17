/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookSources.PackChange;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.edit.BookEdit;
import software.hibiscus.hibiscusquests.edit.BookEditor;
import software.hibiscus.hibiscusquests.edit.ChapterGenerator;
import software.hibiscus.hibiscusquests.edit.EditingMode;
import software.hibiscus.hibiscusquests.edit.GroupEdits;
import software.hibiscus.hibiscusquests.edit.Ids;
import software.hibiscus.hibiscusquests.network.BookPatch;
import software.hibiscus.hibiscusquests.task.ItemTask;

// The editor's server side: edits checked and applied to the pack's files, mod chapters read-only until overridden,
// deletions to the trash, patches that rebuild the same book on clients, edit mode and its permission, and
// generate_chapter. They run in a batch of their own (they rebuild the book), on a pack in a temporary directory.
final class EditorGameTests {
    private static final String CHAPTER = "edit_test";
    private static final String MOD_CHAPTER = "demo_tour";

    private EditorGameTests() {}

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseCompoundFully(text);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    private static BookEditor.Result apply(MinecraftServer server, BookEdit... edits) {
        return BookEditor.apply(server, List.of(edits));
    }

    // Runs a test on a pack of its own, in a temporary directory, and puts the real pack back after.
    private static void withTempPack(GameTestHelper helper, Body body) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path pack = Files.createTempDirectory("hibiscusquests-pack");
        BookManager.usePackDirectory(server, pack);
        try {
            body.run(server, pack);
        } finally {
            BookManager.usePackDirectory(server, null);
            try (Stream<Path> files = Files.walk(pack)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException ignored) {
                // a temporary directory; the OS cleans it up eventually
            }
        }
    }

    @FunctionalInterface
    private interface Body {
        void run(MinecraftServer server, Path pack) throws Exception;
    }

    // Making a chapter and its quests, editing one, a refused edit, and deleting the chapter to the trash.
    static void edits(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            QuestBook before = BookManager.book();
            BookEditor.Result made = apply(server,
                    new BookEdit.ChapterInfo(CHAPTER, Optional.of(snbt("{title: \"Edit Test\"}"))),
                    new BookEdit.Quest(CHAPTER, "first", Optional.of(snbt("{title: \"First\", x: 1.0d, tasks: [{id: \"tick\", type: \"checkmark\"}]}"))),
                    new BookEdit.Quest(CHAPTER, "second", Optional.of(snbt("{x: 3.0d, dependencies: [\"first\"]}"))));
            check(helper, made.ok(), "a new chapter with two quests is saved: " + made.messages());
            Chapter chapter = BookManager.book().chapter(CHAPTER).orElse(null);
            check(helper, chapter != null && chapter.quests().size() == 2 && chapter.source().isEmpty(), "the book has it");
            check(helper, BookManager.book().dependents(new QuestRef(CHAPTER, "first")).contains(new QuestRef(CHAPTER, "second")),
                    "with its dependency");
            Path file = pack.resolve(PackFiles.CHAPTERS).resolve(CHAPTER + ".snbt");
            check(helper, Files.isRegularFile(file) && Files.readString(file).contains("First"), "in the pack's files");

            // What clients get instead of the whole book: the same book.
            PackChange change = new PackChange(Map.of(CHAPTER, Optional.of(new CompoundTag())), Optional.empty(), Optional.empty(), Map.of());
            BookPatch patch = BookPatch.of(BookManager.book(), change);
            Tag encoded = BookPatch.CODEC.encodeStart(BookManager.ops(server), patch).getOrThrow();
            QuestBook patched = BookPatch.CODEC.parse(BookManager.ops(server), encoded).getOrThrow().apply(before);
            check(helper, patched.chapter(CHAPTER).map(c -> c.quests().size()).orElse(0) == 2
                    && patched.questCount() == BookManager.book().questCount()
                    && patched.dependents(new QuestRef(CHAPTER, "first")).contains(new QuestRef(CHAPTER, "second")), "a patch rebuilds the same book");

            BookEditor.Result edited = apply(server, new BookEdit.Quest(CHAPTER, "first",
                    Optional.of(snbt("{title: \"Renamed\", tasks: [{id: \"tick\", type: \"checkmark\"}]}"))));
            check(helper, edited.ok() && BookManager.book().quest(new QuestRef(CHAPTER, "first")).map(q -> q.title().equals("Renamed")).orElse(false),
                    "editing a quest replaces it");

            int quests = BookManager.book().questCount();
            BookEditor.Result bad = apply(server, new BookEdit.Quest(CHAPTER, "broken", Optional.of(snbt("{tasks: [{type: \"no_such_type\"}]}"))));
            check(helper, !bad.ok() && BookManager.book().questCount() == quests, "an edit that doesn't decode is refused and changes nothing");
            BookEditor.Result badId = apply(server, new BookEdit.Quest(CHAPTER, "Not Valid", Optional.of(new CompoundTag())));
            check(helper, !badId.ok(), "so is an invalid id");
            check(helper, !Files.readString(file).contains("broken"), "and the file is as it was");

            BookEditor.Result deleted = apply(server, new BookEdit.ChapterInfo(CHAPTER, Optional.empty()));
            check(helper, deleted.ok() && BookManager.book().chapter(CHAPTER).isEmpty(), "deleting the chapter takes it out of the book");
            check(helper, !Files.exists(file), "its file is gone from chapters/");
            try (Stream<Path> trash = Files.list(pack.resolve(PackFiles.TRASH).resolve(PackFiles.CHAPTERS))) {
                check(helper, trash.anyMatch(path -> path.getFileName().toString().endsWith(CHAPTER + ".snbt")), "into the trash");
            }
        });
        helper.succeed();
    }

    // A mod's chapter can't be edited until it's overridden (copied into the pack); deleting the copy brings the
    // mod's back.
    static void override(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            Chapter modChapter = BookManager.book().chapter(MOD_CHAPTER).orElse(null);
            check(helper, modChapter != null && modChapter.source().isPresent(), "the demo chapter comes from a data pack");
            String questId = modChapter.quests().getFirst().id();
            BookEditor.Result refused = apply(server, new BookEdit.Quest(MOD_CHAPTER, questId, Optional.of(snbt("{title: \"Mine\"}"))));
            check(helper, !refused.ok(), "editing it is refused");
            BookEditor.Result copied = apply(server, new BookEdit.Override(MOD_CHAPTER));
            check(helper, copied.ok() && BookManager.book().chapter(MOD_CHAPTER).map(c -> c.source().isEmpty()).orElse(false),
                    "Override makes it the pack's own");
            check(helper, Files.isRegularFile(pack.resolve(PackFiles.CHAPTERS).resolve(MOD_CHAPTER + ".snbt")), "as a file in the pack");
            check(helper, BookManager.book().chapter(MOD_CHAPTER).map(c -> c.quests().size()).orElse(0) == modChapter.quests().size(),
                    "with all its quests");
            BookEditor.Result edited = apply(server, new BookEdit.Quest(MOD_CHAPTER, questId, Optional.of(snbt("{title: \"Mine\"}"))));
            check(helper, edited.ok(), "and then it can be edited");
            BookEditor.Result removed = apply(server, new BookEdit.ChapterInfo(MOD_CHAPTER, Optional.empty()));
            check(helper, removed.ok() && BookManager.book().chapter(MOD_CHAPTER).map(c -> c.source().isPresent()).orElse(false),
                    "deleting the pack's copy brings the mod's chapter back");
        });
        helper.succeed();
    }

    // Reward tables, groups and settings.
    static void tablesGroupsSettings(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            BookEditor.Result table = apply(server, new BookEdit.Table("edit_table",
                    Optional.of(snbt("{title: \"T\", rolls: 2, entries: [{weight: 3, type: \"xp\", amount: 5}]}"))));
            check(helper, table.ok() && BookManager.book().table("edit_table").map(t -> t.rolls() == 2).orElse(false), "a new reward table");
            BookEditor.Result rolling = apply(server, new BookEdit.Table("edit_table",
                    Optional.of(snbt("{title: \"T\", entries: [{type: \"random\", table: \"edit_table\"}]}"))));
            check(helper, !rolling.ok(), "a table can't roll a table");
            BookEditor.Result tableGone = apply(server, new BookEdit.Table("edit_table", Optional.empty()));
            check(helper, tableGone.ok() && BookManager.book().table("edit_table").isEmpty(), "and deleted");

            ListTag groups = new ListTag();
            groups.add(snbt("{id: \"edit_group\", title: \"Edited\"}"));
            check(helper, apply(server, new BookEdit.Groups(groups)).ok()
                    && BookManager.book().groups().stream().anyMatch(group -> group.id().equals("edit_group")), "groups");
            groups.add(snbt("{id: \"edit_group\", title: \"Twice\"}"));
            check(helper, !apply(server, new BookEdit.Groups(groups)).ok(), "two groups can't share an id");
            check(helper, apply(server, new BookEdit.Settings(snbt("{title: \"Edited Book\"}"))).ok()
                    && BookManager.book().settings().title().equals("Edited Book"), "settings");
        });
        helper.succeed();
    }

    private static ListTag encodeGroups(MinecraftServer server, List<ChapterGroup> groups) {
        return (ListTag) ChapterGroup.CODEC.listOf().encodeStart(BookManager.ops(server), groups).getOrThrow();
    }

    private static String parentOf(String group) {
        return BookManager.book().groupTree().parent(group);
    }

    // Nesting a group in another and taking it back out, as the editor does (GroupEdits), each undone and redone the
    // way the editor does it (the old or new list of groups); and the parents the server refuses.
    static void groupNesting(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            ListTag start = new ListTag();
            start.add(snbt("{id: \"nest_parent\", title: \"Parent\"}"));
            start.add(snbt("{id: \"nest_child\", title: \"Child\"}"));
            check(helper, apply(server, new BookEdit.Groups(start)).ok(), "two groups");

            List<ChapterGroup> flat = BookManager.book().groups();
            List<ChapterGroup> nested = GroupEdits.nest(flat, "nest_child", "nest_parent").orElseThrow();
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, nested))).ok() && parentOf("nest_child").equals("nest_parent"),
                    "nesting puts the child under the parent");
            check(helper, Files.readString(pack.resolve(PackFiles.GROUPS)).contains("parent: \"nest_parent\""), "in the pack's file");
            check(helper, GroupEdits.nest(BookManager.book().groups(), "nest_parent", "nest_child").isEmpty(),
                    "a group can't go into one under it");
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, flat))).ok() && parentOf("nest_child").isEmpty(), "undo un-nests it");
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, nested))).ok() && parentOf("nest_child").equals("nest_parent"),
                    "redo nests it again");

            List<ChapterGroup> out = GroupEdits.moveOut(BookManager.book().groups(), "nest_child").orElseThrow();
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, out))).ok() && parentOf("nest_child").isEmpty(),
                    "moving it out puts it back at the top level");
            List<String> top = BookManager.book().groupTree().children("").stream().map(ChapterGroup::id).toList();
            check(helper, top.indexOf("nest_child") == top.indexOf("nest_parent") + 1, "just after its old parent: " + top);
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, nested))).ok() && parentOf("nest_child").equals("nest_parent"),
                    "and undo puts it back in");

            check(helper, GroupEdits.placeBefore(BookManager.book().groups(), "nest_parent", "nest_child").isEmpty(),
                    "a group can't go beside one under it");
            List<ChapterGroup> reordered = GroupEdits.placeBefore(flat, "nest_child", "nest_parent").orElseThrow();
            check(helper, apply(server, new BookEdit.Groups(encodeGroups(server, reordered))).ok(), "dropping a heading on another still reorders");
            List<String> reorderedTop = BookManager.book().groupTree().children("").stream().map(ChapterGroup::id).toList();
            check(helper, reorderedTop.indexOf("nest_child") + 1 == reorderedTop.indexOf("nest_parent"), "putting it just before: " + reorderedTop);

            ListTag unknown = new ListTag();
            unknown.add(snbt("{id: \"nest_parent\"}"));
            unknown.add(snbt("{id: \"nest_child\", parent: \"no_such_group\"}"));
            check(helper, !apply(server, new BookEdit.Groups(unknown)).ok(), "a parent that isn't a group is refused");
            ListTag cycle = new ListTag();
            cycle.add(snbt("{id: \"nest_parent\", parent: \"nest_child\"}"));
            cycle.add(snbt("{id: \"nest_child\", parent: \"nest_parent\"}"));
            check(helper, !apply(server, new BookEdit.Groups(cycle)).ok(), "so is a group inside itself");
        });
        helper.succeed();
    }

    // Only players in edit mode can edit; a player needs the editor permission to turn it on, unless an editor puts
    // them in it with the command.
    static void permission(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            ServerPlayer player = join(helper);
            check(helper, !EditingMode.hasPermission(player), "a mock player isn't an op");
            List<BookEdit> edits = List.of(new BookEdit.ChapterInfo("from_player", Optional.of(snbt("{title: \"Mine\"}"))));
            check(helper, !BookEditor.applyFrom(player, edits).ok(), "edits from a player not in edit mode are refused");
            check(helper, !EditingMode.set(player, true, false), "a non-op can't turn edit mode on");
            check(helper, !BookEditor.applyFrom(player, edits).ok() && BookManager.book().chapter("from_player").isEmpty(), "so their edits stay refused");
            check(helper, EditingMode.set(player, true, true), "an editor can put them in edit mode");
            check(helper, BookEditor.applyFrom(player, edits).ok() && BookManager.book().chapter("from_player").isPresent(), "and then they can edit");
            EditingMode.set(player, false, false);
            check(helper, !BookEditor.applyFrom(player, edits).ok(), "until edit mode is off again");
            EditingMode.onLogout(player.getUUID());
            leave(helper, player);
        });
        helper.succeed();
    }

    // generate_chapter's chapter: a quest per item, each with an item task for as many as there were; and the ids it
    // makes from titles.
    static void generate(GameTestHelper helper) throws Exception {
        check(helper, Ids.fromTitle("Steel Ingot", "x", List.of()).equals("steel_ingot"), "ids come from titles");
        check(helper, Ids.fromTitle("Steel Ingot", "x", List.of("steel_ingot")).equals("steel_ingot_2"), "and don't clash");
        check(helper, Ids.fromTitle("&c!!!", "fallback", List.of()).equals("fallback"), "with a fallback for titles with no letters");
        withTempPack(helper, (server, pack) -> {
            List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STONE, 64));
            BookEditor.Result result = BookEditor.apply(server, ChapterGenerator.edits(server, "generated", "Generated", stacks));
            check(helper, result.ok(), "the generated chapter is saved: " + result.messages());
            Chapter chapter = BookManager.book().chapter("generated").orElse(null);
            check(helper, chapter != null && chapter.quests().size() == 2, "with a quest per item");
            check(helper, chapter.quests().getFirst().tasks().getFirst().task() instanceof ItemTask task && task.target() == 3,
                    "each an item task for the count there was");
        });
        helper.succeed();
    }
}

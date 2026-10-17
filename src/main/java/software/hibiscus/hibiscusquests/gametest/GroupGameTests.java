/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookMerger;
import software.hibiscus.hibiscusquests.book.BookSources;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.GroupTree;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.QuestBook;

// Nested chapter groups: parents and the tree's order (the book's order), parents that can't be used, the chapter
// list's rows, and parents a pack removes or disables.
final class GroupGameTests {
    private GroupGameTests() {}

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseCompoundFully(text);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    // A chapter_groups.snbt, from a mod (a namespace) or the pack (null).
    private static RawFile groups(String source, String text) {
        return new RawFile(PackFiles.GROUPS, Optional.ofNullable(source), snbt(text), (source == null ? "pack" : source) + ":" + PackFiles.GROUPS);
    }

    private static RawFile chapter(String id, String group) {
        return new RawFile(id, Optional.empty(), snbt("{title: \"" + id + "\", group: \"" + group + "\"}"), "pack:" + id);
    }

    private static QuestBook book(GameTestHelper helper, BookSources sources, List<String> warnings) {
        return BookMerger.decode(BookMerger.merge(sources, warnings::add), BookManager.ops(helper.getLevel().getServer()), warnings::add);
    }

    private static List<String> ids(List<ChapterGroup> groups) {
        return groups.stream().map(ChapterGroup::id).toList();
    }

    // The rows as "h:<group>:<depth>" and "c:<chapter>:<depth>", to compare.
    private static List<String> rows(QuestBook book, boolean showEmpty, Predicate<ChapterGroup> folded) {
        List<String> rows = new ArrayList<>();
        for (GroupTree.Row row : book.groupTree().rows(book.chapters(), showEmpty, folded)) {
            rows.add(switch (row) {
                case GroupTree.Row.Heading heading -> "h:" + heading.group().id() + ":" + heading.depth();
                case GroupTree.Row.Entry entry -> "c:" + entry.chapter().id() + ":" + entry.depth();
            });
        }
        return rows;
    }

    // A parent holds its groups, ordered among themselves; deeper levels work too, and the book's chapters follow
    // the tree: ungrouped first, then each group's own chapters before the groups under it.
    static void nesting(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        RawFile mod = groups("somemod", """
                {groups: [{id: "mod", title: "Mod"}, {id: "mod_b", parent: "mod", order: 1}, {id: "mod_a", parent: "mod", order: 0},
                {id: "mod_a_deep", parent: "mod_a"}, {id: "other", order: 5}]}""");
        List<RawFile> chapters = List.of(chapter("in_other", "other"), chapter("in_b", "mod_b"), chapter("in_deep", "mod_a_deep"),
                chapter("in_a", "mod_a"), chapter("in_mod", "mod"), chapter("loose", ""));
        QuestBook book = book(helper, new BookSources(Optional.empty(), List.of(mod), chapters, List.of()), warnings);
        GroupTree tree = book.groupTree();
        check(helper, warnings.isEmpty(), "no warnings: " + warnings);
        check(helper, tree.parent("mod_a").equals("mod") && tree.parent("mod_a_deep").equals("mod_a") && tree.parent("mod").isEmpty(),
                "each group is under its parent");
        check(helper, tree.depth("mod_a_deep") == 2 && ids(tree.ancestors("mod_a_deep")).equals(List.of("mod_a", "mod")), "two levels down");
        check(helper, ids(book.groups()).equals(List.of("mod", "mod_a", "mod_a_deep", "mod_b", "other")),
                "groups in the tree's order, siblings by order: " + ids(book.groups()));
        List<String> order = book.chapters().stream().map(Chapter::id).toList();
        check(helper, order.equals(List.of("loose", "in_mod", "in_a", "in_deep", "in_b", "in_other")), "chapters in the tree's order: " + order);
        helper.succeed();
    }

    // A parent that names no group, and groups inside themselves, are reported and shown at the top level; the
    // groups keep their parent as written (the editor writes it back), and a group under one of them stays there.
    static void badParents(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        RawFile pack = groups(null, """
                {groups: [{id: "orphan", parent: "nowhere"}, {id: "loop_a", parent: "loop_b"}, {id: "loop_b", parent: "loop_a"},
                {id: "self", parent: "self"}, {id: "under_loop", parent: "loop_a"}]}""");
        QuestBook book = book(helper, new BookSources(Optional.empty(), List.of(pack), List.of(), List.of()), warnings);
        GroupTree tree = book.groupTree();
        check(helper, warnings.stream().anyMatch(w -> w.contains("orphan") && w.contains("nowhere")), "the unknown parent is reported: " + warnings);
        for (String id : List.of("loop_a", "loop_b", "self")) {
            check(helper, warnings.stream().anyMatch(w -> w.contains("Group " + id + " is inside itself")), id + "'s cycle is reported");
        }
        check(helper, warnings.size() == 4, "and nothing else: " + warnings);
        for (String id : List.of("orphan", "loop_a", "loop_b", "self")) {
            check(helper, tree.parent(id).isEmpty() && tree.depth(id) == 0, id + " is at the top level");
        }
        check(helper, tree.parent("under_loop").equals("loop_a") && tree.depth("under_loop") == 1, "a group under one of them stays under it");
        check(helper, tree.group("orphan").map(group -> group.parent().equals("nowhere")).orElse(false), "the parent as written is kept");
        helper.succeed();
    }

    // The chapter list: a group with only groups under it shows when one of them has a chapter, a branch with no
    // chapters doesn't (except in the editor), and folding a group hides everything under it.
    static void rows(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        RawFile pack = groups(null, """
                {groups: [{id: "parent_only", order: 0}, {id: "child", parent: "parent_only"}, {id: "grandchild", parent: "child"},
                {id: "empty", order: 1}, {id: "empty_child", parent: "empty"}]}""");
        List<RawFile> chapters = List.of(chapter("in_grandchild", "grandchild"), chapter("in_child", "child"), chapter("loose", ""));
        QuestBook book = book(helper, new BookSources(Optional.empty(), List.of(pack), chapters, List.of()), warnings);
        List<String> shown = rows(book, false, group -> false);
        check(helper, shown.equals(List.of("c:loose:0", "h:parent_only:0", "h:child:1", "c:in_child:1", "h:grandchild:2", "c:in_grandchild:2")),
                "a parent with only groups shows, the empty branch doesn't: " + shown);
        List<String> editing = rows(book, true, group -> false);
        check(helper, editing.containsAll(List.of("h:empty:0", "h:empty_child:1")) && editing.size() == shown.size() + 2,
                "the editor shows empty groups too: " + editing);
        List<String> foldedParent = rows(book, false, group -> group.id().equals("parent_only"));
        check(helper, foldedParent.equals(List.of("c:loose:0", "h:parent_only:0")), "a folded group hides everything under it: " + foldedParent);
        List<String> foldedChild = rows(book, false, group -> group.id().equals("child"));
        check(helper, foldedChild.equals(List.of("c:loose:0", "h:parent_only:0", "h:child:1")), "each group folds on its own: " + foldedChild);
        helper.succeed();
    }

    // A parent the pack removes (remove: true), or one from a disabled source, leaves its groups at the top level,
    // with a warning.
    static void removedParent(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        RawFile mod = groups("somemod", "{groups: [{id: \"mod_root\"}, {id: \"mod_leaf\", parent: \"mod_root\"}]}");
        RawFile other = groups("othermod", "{groups: [{id: \"other_root\"}]}");
        RawFile addon = groups("addon", "{groups: [{id: \"addon_leaf\", parent: \"other_root\"}]}");
        RawFile pack = groups(null, "{groups: [{id: \"mod_root\", remove: true}]}");
        BookSources sources = new BookSources(Optional.of(snbt("{disabled_sources: [\"othermod\"]}")), List.of(mod, other, addon, pack),
                List.of(), List.of());
        QuestBook book = book(helper, sources, warnings);
        GroupTree tree = book.groupTree();
        check(helper, !tree.contains("mod_root") && !tree.contains("other_root"), "the removed and disabled parents are gone");
        check(helper, tree.contains("mod_leaf") && tree.parent("mod_leaf").isEmpty(), "a removed parent's group is at the top level");
        check(helper, tree.contains("addon_leaf") && tree.parent("addon_leaf").isEmpty(), "so is a disabled one's");
        check(helper, warnings.stream().anyMatch(w -> w.contains("mod_leaf")) && warnings.stream().anyMatch(w -> w.contains("addon_leaf")),
                "each with a warning: " + warnings);
        helper.succeed();
    }
}

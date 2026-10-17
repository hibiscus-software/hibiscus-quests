/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.GroupTree;

// The editor's changes to the chapter groups' tree, as the whole new list of groups (what a BookEdit.Groups carries):
// a group put before another, into another or back out, stepped among its siblings, or deleted. Each is empty when
// it can't be done (a group that isn't there, or a move that would put a group inside itself). Siblings that are
// reordered get orders 10, 20, 30...; groups elsewhere in the tree keep theirs.
public final class GroupEdits {
    private GroupEdits() {}

    private static GroupTree tree(List<ChapterGroup> groups) {
        return GroupTree.of(groups, warning -> {});
    }

    // A group just before another, among that one's siblings (so it takes the other's parent).
    public static Optional<List<ChapterGroup>> placeBefore(List<ChapterGroup> groups, String id, String before) {
        GroupTree tree = tree(groups);
        if (!tree.contains(id) || !tree.contains(before) || tree.isWithin(before, id)) {
            return Optional.empty();
        }
        String parent = tree.parent(before);
        List<String> siblings = siblingIds(tree, parent, id);
        siblings.add(siblings.indexOf(before), id);
        return Optional.of(arrange(groups, parent, siblings, id));
    }

    // A group last under another ("" for the top level).
    public static Optional<List<ChapterGroup>> nest(List<ChapterGroup> groups, String id, String parent) {
        GroupTree tree = tree(groups);
        if (!tree.contains(id) || !parent.isEmpty() && (!tree.contains(parent) || tree.isWithin(parent, id))) {
            return Optional.empty();
        }
        List<String> siblings = siblingIds(tree, parent, id);
        siblings.add(id);
        return Optional.of(arrange(groups, parent, siblings, id));
    }

    // A group out of its parent: just after it, among the parent's siblings.
    public static Optional<List<ChapterGroup>> moveOut(List<ChapterGroup> groups, String id) {
        GroupTree tree = tree(groups);
        String parent = tree.parent(id);
        if (!tree.contains(id) || parent.isEmpty()) {
            return Optional.empty();
        }
        String grandparent = tree.parent(parent);
        List<String> siblings = siblingIds(tree, grandparent, id);
        siblings.add(siblings.indexOf(parent) + 1, id);
        return Optional.of(arrange(groups, grandparent, siblings, id));
    }

    // A group up (by < 0) or down among its siblings.
    public static Optional<List<ChapterGroup>> step(List<ChapterGroup> groups, String id, int by) {
        GroupTree tree = tree(groups);
        if (!tree.contains(id)) {
            return Optional.empty();
        }
        String parent = tree.parent(id);
        List<String> siblings = siblingIds(tree, parent, "");
        int index = siblings.indexOf(id);
        int target = index + by;
        if (target < 0 || target >= siblings.size()) {
            return Optional.empty();
        }
        siblings.add(target, siblings.remove(index));
        return Optional.of(arrange(groups, parent, siblings, id));
    }

    // A group gone; the groups under it move up to its parent.
    public static Optional<List<ChapterGroup>> delete(List<ChapterGroup> groups, String id) {
        GroupTree tree = tree(groups);
        if (!tree.contains(id)) {
            return Optional.empty();
        }
        String parent = tree.parent(id);
        List<ChapterGroup> result = new ArrayList<>();
        for (ChapterGroup group : groups) {
            if (group.id().equals(id)) {
                continue;
            }
            result.add(tree.parent(group.id()).equals(id) ? group.withParent(parent) : group);
        }
        return Optional.of(result);
    }

    // The ids of a parent's groups in order, leaving one out.
    private static List<String> siblingIds(GroupTree tree, String parent, String without) {
        List<String> ids = new ArrayList<>();
        tree.children(parent).forEach(group -> {
            if (!group.id().equals(without)) {
                ids.add(group.id());
            }
        });
        return ids;
    }

    // The list with a parent's groups in this order (renumbered), and the moved group under that parent.
    private static List<ChapterGroup> arrange(List<ChapterGroup> groups, String parent, List<String> siblings, String moved) {
        Map<String, Integer> orders = new HashMap<>();
        for (int n = 0; n < siblings.size(); n++) {
            orders.put(siblings.get(n), (n + 1) * 10);
        }
        List<ChapterGroup> result = new ArrayList<>();
        for (ChapterGroup group : groups) {
            ChapterGroup changed = group;
            if (group.id().equals(moved) && !group.parent().equals(parent)) {
                changed = changed.withParent(parent);
            }
            Integer order = orders.get(group.id());
            if (order != null && order != group.order()) {
                changed = changed.withOrder(order);
            }
            result.add(changed);
        }
        return result;
    }
}

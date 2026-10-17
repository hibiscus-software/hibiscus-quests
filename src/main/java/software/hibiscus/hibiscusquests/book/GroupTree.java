/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

// The chapter groups as a tree. A group's parent is the group it names, unless that's no group in the book or the
// group would end up inside itself: then it's at the top level (and a warning says why). Siblings go by order, then
// id. The tree's order (each group, then the groups under it) is the book's order, and rows() lays out the chapter
// list from it. Immutable; the same on the server and the client.
public final class GroupTree {
    // Why a group's parent was set aside.
    public enum Problem {
        UNKNOWN_PARENT,
        CYCLE
    }

    // A row of the chapter list: a group's heading, or a chapter (in a group, or ungrouped at depth 0).
    public sealed interface Row {
        int depth();

        record Heading(ChapterGroup group, int depth, boolean folded) implements Row {}

        record Entry(Chapter chapter, int depth) implements Row {}
    }

    private static final Comparator<ChapterGroup> SIBLING_ORDER = Comparator.comparingInt(ChapterGroup::order).thenComparing(ChapterGroup::id);

    public static final GroupTree EMPTY = of(List.of(), warning -> {});

    private final Map<String, ChapterGroup> byId;
    private final Map<String, String> parents;
    private final Map<String, List<ChapterGroup>> children;
    private final Map<String, Integer> depths;
    private final List<ChapterGroup> ordered;

    private GroupTree(Map<String, ChapterGroup> byId, Map<String, String> parents) {
        this.parents = Collections.unmodifiableMap(parents);
        Map<String, List<ChapterGroup>> kids = new HashMap<>();
        for (ChapterGroup group : byId.values()) {
            kids.computeIfAbsent(parents.get(group.id()), key -> new ArrayList<>()).add(group);
        }
        kids.values().forEach(list -> list.sort(SIBLING_ORDER));
        kids.replaceAll((key, list) -> List.copyOf(list));
        this.children = Map.copyOf(kids);
        List<ChapterGroup> order = new ArrayList<>();
        Map<String, Integer> depth = new HashMap<>();
        walk("", 0, order, depth);
        this.ordered = List.copyOf(order);
        this.depths = Map.copyOf(depth);
        Map<String, ChapterGroup> inOrder = new LinkedHashMap<>();
        order.forEach(group -> inOrder.put(group.id(), group));
        this.byId = Collections.unmodifiableMap(inOrder);
    }

    private void walk(String parent, int depth, List<ChapterGroup> order, Map<String, Integer> depthOf) {
        for (ChapterGroup group : children.getOrDefault(parent, List.of())) {
            order.add(group);
            depthOf.put(group.id(), depth);
            walk(group.id(), depth + 1, order, depthOf);
        }
    }

    // The tree of these groups (the first of two with the same id wins), each parent that can't be used reported.
    public static GroupTree of(List<ChapterGroup> groups, Consumer<String> warnings) {
        Map<String, ChapterGroup> byId = byId(groups);
        Map<String, Problem> problems = problems(byId);
        Map<String, String> parents = new HashMap<>();
        for (ChapterGroup group : byId.values()) {
            Problem problem = problems.get(group.id());
            parents.put(group.id(), problem == null ? group.parent() : "");
            if (problem == Problem.UNKNOWN_PARENT) {
                warnings.accept("Group " + group.id() + "'s parent \"" + group.parent() + "\" isn't in the book; the group is shown at the top level");
            } else if (problem == Problem.CYCLE) {
                warnings.accept("Group " + group.id() + " is inside itself (its parents lead back to it); the group is shown at the top level");
            }
        }
        return new GroupTree(byId, parents);
    }

    // The groups whose parent can't be used, and why: it names no group, or following parents comes back round.
    public static Map<String, Problem> problems(List<ChapterGroup> groups) {
        return problems(byId(groups));
    }

    private static Map<String, ChapterGroup> byId(List<ChapterGroup> groups) {
        Map<String, ChapterGroup> byId = new LinkedHashMap<>();
        groups.forEach(group -> byId.putIfAbsent(group.id(), group));
        return byId;
    }

    private static Map<String, Problem> problems(Map<String, ChapterGroup> byId) {
        Map<String, Problem> problems = new LinkedHashMap<>();
        for (ChapterGroup group : byId.values()) {
            if (!group.parent().isEmpty() && !byId.containsKey(group.parent())) {
                problems.put(group.id(), Problem.UNKNOWN_PARENT);
            }
        }
        // A group is in a cycle when its parents lead back to it. Walking stops at the top, at a parent that's set
        // aside, or after as many steps as there are groups (a cycle further up that this group isn't part of).
        for (ChapterGroup group : byId.values()) {
            if (problems.containsKey(group.id())) {
                continue;
            }
            String current = group.parent();
            for (int steps = 0; !current.isEmpty() && steps <= byId.size(); steps++) {
                if (current.equals(group.id())) {
                    problems.put(group.id(), Problem.CYCLE);
                    break;
                }
                ChapterGroup up = byId.get(current);
                if (up == null || problems.get(up.id()) == Problem.UNKNOWN_PARENT) {
                    break;
                }
                current = up.parent();
            }
        }
        return problems;
    }

    // Every group, in the tree's order.
    public List<ChapterGroup> groups() {
        return ordered;
    }

    public Optional<ChapterGroup> group(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    // The group a group is under ("" at the top level, or for no group).
    public String parent(String id) {
        return parents.getOrDefault(id, "");
    }

    // The groups right under a parent ("" for the top level), in order.
    public List<ChapterGroup> children(String parent) {
        return children.getOrDefault(parent, List.of());
    }

    // How many groups a group is under (0 at the top level).
    public int depth(String id) {
        return depths.getOrDefault(id, 0);
    }

    // The groups above a group, nearest first.
    public List<ChapterGroup> ancestors(String id) {
        List<ChapterGroup> ancestors = new ArrayList<>();
        for (String current = parent(id); !current.isEmpty(); current = parent(current)) {
            ancestors.add(byId.get(current));
        }
        return ancestors;
    }

    // Whether a group is the given one or anywhere under it.
    public boolean isWithin(String id, String ancestor) {
        for (String current = id; !current.isEmpty(); current = parent(current)) {
            if (current.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    // The chapter list: ungrouped chapters (those naming no group in the book) first, then each top-level group's
    // heading, its own chapters and the groups under it, the same way down. Chapters keep the order given. A group
    // shows if it or a group under it has a chapter, or always with showEmpty (the editor); a folded group's heading
    // shows but nothing under it.
    public List<Row> rows(List<Chapter> chapters, boolean showEmpty, Predicate<ChapterGroup> folded) {
        Map<String, List<Chapter>> byGroup = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (contains(chapter.info().group())) {
                byGroup.computeIfAbsent(chapter.info().group(), key -> new ArrayList<>()).add(chapter);
            } else {
                rows.add(new Row.Entry(chapter, 0));
            }
        }
        Map<String, Boolean> filled = new HashMap<>();
        addRows("", byGroup, showEmpty, folded, filled, rows);
        return rows;
    }

    private void addRows(String parent, Map<String, List<Chapter>> byGroup, boolean showEmpty, Predicate<ChapterGroup> folded,
            Map<String, Boolean> filled, List<Row> rows) {
        for (ChapterGroup group : children(parent)) {
            if (!showEmpty && !hasChapters(group.id(), byGroup, filled)) {
                continue;
            }
            int depth = depth(group.id());
            boolean isFolded = folded.test(group);
            rows.add(new Row.Heading(group, depth, isFolded));
            if (isFolded) {
                continue;
            }
            byGroup.getOrDefault(group.id(), List.of()).forEach(chapter -> rows.add(new Row.Entry(chapter, depth)));
            addRows(group.id(), byGroup, showEmpty, folded, filled, rows);
        }
    }

    private boolean hasChapters(String id, Map<String, List<Chapter>> byGroup, Map<String, Boolean> filled) {
        Boolean known = filled.get(id);
        if (known != null) {
            return known;
        }
        boolean has = !byGroup.getOrDefault(id, List.of()).isEmpty();
        for (ChapterGroup child : children(id)) {
            has |= hasChapters(child.id(), byGroup, filled);
        }
        filled.put(id, has);
        return has;
    }
}

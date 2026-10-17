/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A heading in the book's chapter list that chapters sit under (a chapter names its group's id). The groups are listed
// in chapter_groups.snbt: {groups: [{id: "basics", title: "The Basics", icon: "minecraft:crafting_table"}]}. A group
// may sit under another (parent: a group id), so the list is a tree; order places it among the groups with the same
// parent (GroupTree).
public record ChapterGroup(String id, String title, Optional<BookIcon> icon, int order, boolean collapsed, String parent) {
    public static final Codec<ChapterGroup> CODEC = RecordCodecBuilder.create(i -> i.group(
            BookCodecs.ID.fieldOf("id").forGetter(ChapterGroup::id),
            BookCodecs.text("title").forGetter(ChapterGroup::title),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(ChapterGroup::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ChapterGroup::order),
            Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(ChapterGroup::collapsed),
            BookCodecs.text("parent").forGetter(ChapterGroup::parent))
            .apply(i, ChapterGroup::new));

    public ChapterGroup withOrder(int newOrder) {
        return new ChapterGroup(id, title, icon, newOrder, collapsed, parent);
    }

    // Under another group, or at the top level for "".
    public ChapterGroup withParent(String newParent) {
        return new ChapterGroup(id, title, icon, order, collapsed, newParent);
    }

    // The whole chapter_groups.snbt file.
    public static final Codec<List<ChapterGroup>> FILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            BookCodecs.list("groups", CODEC).forGetter(groups -> groups))
            .apply(i, groups -> groups));
}

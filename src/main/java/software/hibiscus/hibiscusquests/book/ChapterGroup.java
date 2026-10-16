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
// in chapter_groups.snbt: {groups: [{id: "basics", title: "The Basics", icon: "minecraft:crafting_table"}]}.
public record ChapterGroup(String id, String title, Optional<BookIcon> icon, int order, boolean collapsed) {
    public static final Codec<ChapterGroup> CODEC = RecordCodecBuilder.create(i -> i.group(
            BookCodecs.ID.fieldOf("id").forGetter(ChapterGroup::id),
            BookCodecs.text("title").forGetter(ChapterGroup::title),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(ChapterGroup::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ChapterGroup::order),
            Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(ChapterGroup::collapsed))
            .apply(i, ChapterGroup::new));

    // The whole chapter_groups.snbt file.
    public static final Codec<List<ChapterGroup>> FILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            BookCodecs.list("groups", CODEC).forGetter(groups -> groups))
            .apply(i, groups -> groups));
}

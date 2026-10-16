/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A chapter's own settings: where it sits in the book, how it looks, the defaults for its quests' nodes, and what
// has to be complete before it appears (requires: chapter ids, or "chapter/quest" for single quests). requires_mods
// lists mods the chapter is for: without one of them the chapter is left out of the book. Read from the chapter's
// compound alongside its quests.
public record ChapterInfo(String group, int order, String title, String subtitle, Optional<BookIcon> icon,
        Optional<String> defaultShape, Optional<Double> defaultSize, boolean defaultHideDependencyLines,
        boolean alwaysInvisible, boolean requireSequential, List<String> requires, List<String> requiresMods) {
    public static final MapCodec<ChapterInfo> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BookCodecs.text("group").forGetter(ChapterInfo::group),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ChapterInfo::order),
            BookCodecs.text("title").forGetter(ChapterInfo::title),
            BookCodecs.text("subtitle").forGetter(ChapterInfo::subtitle),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(ChapterInfo::icon),
            Codec.STRING.optionalFieldOf("default_shape").forGetter(ChapterInfo::defaultShape),
            Codec.doubleRange(0.5, 3).optionalFieldOf("default_size").forGetter(ChapterInfo::defaultSize),
            Codec.BOOL.optionalFieldOf("default_hide_dependency_lines", false).forGetter(ChapterInfo::defaultHideDependencyLines),
            Codec.BOOL.optionalFieldOf("always_invisible", false).forGetter(ChapterInfo::alwaysInvisible),
            Codec.BOOL.optionalFieldOf("require_sequential", false).forGetter(ChapterInfo::requireSequential),
            BookCodecs.list("requires", Codec.STRING).forGetter(ChapterInfo::requires),
            BookCodecs.list("requires_mods", Codec.STRING).forGetter(ChapterInfo::requiresMods))
            .apply(i, ChapterInfo::new));

    public ChapterInfo {
        requires = List.copyOf(requires);
        requiresMods = List.copyOf(requiresMods);
    }
}

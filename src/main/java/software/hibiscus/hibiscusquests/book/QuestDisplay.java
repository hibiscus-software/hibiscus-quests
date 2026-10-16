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

// How a quest looks: its text, icon, place on the chapter's canvas, node shape and size, and when it's hidden. Read
// from the same compound as the rest of the quest (the fields are split only to keep each codec small).
public record QuestDisplay(String title, String subtitle, Optional<BookIcon> icon, List<String> description, double x,
        double y, Optional<Double> size, Optional<String> shape, HideMode hide, boolean secret,
        Optional<Boolean> hideDependencyLines, int invisibleUntilTasks, int minWidth, boolean disableToast) {
    public static final QuestDisplay DEFAULT = new QuestDisplay("", "", Optional.empty(), List.of(), 0, 0, Optional.empty(),
            Optional.empty(), HideMode.NEVER, false, Optional.empty(), 0, 0, false);

    public static final MapCodec<QuestDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BookCodecs.text("title").forGetter(QuestDisplay::title),
            BookCodecs.text("subtitle").forGetter(QuestDisplay::subtitle),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(QuestDisplay::icon),
            BookCodecs.LINES.optionalFieldOf("description", List.of()).forGetter(QuestDisplay::description),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(QuestDisplay::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(QuestDisplay::y),
            Codec.doubleRange(0.5, 3).optionalFieldOf("size").forGetter(QuestDisplay::size),
            Codec.STRING.optionalFieldOf("shape").forGetter(QuestDisplay::shape),
            HideMode.CODEC.optionalFieldOf("hide", HideMode.NEVER).forGetter(QuestDisplay::hide),
            Codec.BOOL.optionalFieldOf("secret", false).forGetter(QuestDisplay::secret),
            Codec.BOOL.optionalFieldOf("hide_dependency_lines").forGetter(QuestDisplay::hideDependencyLines),
            Codec.intRange(0, 1000).optionalFieldOf("invisible_until_tasks", 0).forGetter(QuestDisplay::invisibleUntilTasks),
            Codec.intRange(0, 4096).optionalFieldOf("min_width", 0).forGetter(QuestDisplay::minWidth),
            Codec.BOOL.optionalFieldOf("disable_toast", false).forGetter(QuestDisplay::disableToast))
            .apply(i, QuestDisplay::new));

    public QuestDisplay {
        description = List.copyOf(description);
    }

    // The description split into pages at "---" lines, with translated lines (BookText.lines) in place.
    public List<List<String>> pages() {
        return BookCodecs.pages(BookText.lines(description));
    }
}

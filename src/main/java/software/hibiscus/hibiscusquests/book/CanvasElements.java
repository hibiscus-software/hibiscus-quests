/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// The things on a chapter's canvas besides its own quests: links to quests in other chapters, images and text labels.
public final class CanvasElements {
    private CanvasElements() {}

    // A node that shows a quest from another chapter (written "chapter/quest"); completing it there completes it here.
    public record QuestLink(String id, String quest, double x, double y, Optional<Double> size, Optional<String> shape) {
        public static final Codec<QuestLink> CODEC = RecordCodecBuilder.create(i -> i.group(
                BookCodecs.ID.fieldOf("id").forGetter(QuestLink::id),
                Codec.STRING.fieldOf("quest").forGetter(QuestLink::quest),
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(QuestLink::x),
                Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(QuestLink::y),
                Codec.doubleRange(0.5, 3).optionalFieldOf("size").forGetter(QuestLink::size),
                Codec.STRING.optionalFieldOf("shape").forGetter(QuestLink::shape))
                .apply(i, QuestLink::new));
    }

    // A picture on the canvas: a texture or an item, sized in grid units, behind or in front of the nodes. Clicking it
    // can open a URL (asking first) or a quest ("chapter/quest").
    public record CanvasImage(BookIcon image, double x, double y, double width, double height, double rotation, double alpha,
            ImageLayer layer, Optional<String> click) {
        public static final Codec<CanvasImage> CODEC = RecordCodecBuilder.create(i -> i.group(
                BookIcon.CODEC.fieldOf("image").forGetter(CanvasImage::image),
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(CanvasImage::x),
                Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(CanvasImage::y),
                Codec.doubleRange(0.05, 64).optionalFieldOf("width", 1.0).forGetter(CanvasImage::width),
                Codec.doubleRange(0.05, 64).optionalFieldOf("height", 1.0).forGetter(CanvasImage::height),
                Codec.DOUBLE.optionalFieldOf("rotation", 0.0).forGetter(CanvasImage::rotation),
                Codec.doubleRange(0, 1).optionalFieldOf("alpha", 1.0).forGetter(CanvasImage::alpha),
                ImageLayer.CODEC.optionalFieldOf("layer", ImageLayer.BEHIND).forGetter(CanvasImage::layer),
                Codec.STRING.optionalFieldOf("click").forGetter(CanvasImage::click))
                .apply(i, CanvasImage::new));
    }

    // Rich text placed on the canvas, for headings over groups of nodes.
    public record CanvasLabel(String text, double x, double y, double scale, Optional<Integer> color) {
        public static final Codec<CanvasLabel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(CanvasLabel::text),
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(CanvasLabel::x),
                Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(CanvasLabel::y),
                Codec.doubleRange(0.25, 8).optionalFieldOf("scale", 1.0).forGetter(CanvasLabel::scale),
                BookCodecs.COLOR.optionalFieldOf("color").forGetter(CanvasLabel::color))
                .apply(i, CanvasLabel::new));
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// Whether a canvas image is drawn behind the quest nodes or in front of them.
public enum ImageLayer implements StringRepresentable {
    BEHIND("behind"),
    FRONT("front");

    public static final Codec<ImageLayer> CODEC = StringRepresentable.fromEnum(ImageLayer::values);

    private final String name;

    ImageLayer(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

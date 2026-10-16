/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// Whether a quest's tasks are done in order (linear: only the first unfinished task counts) or in any order (flexible).
public enum ProgressionMode implements StringRepresentable {
    FLEXIBLE("flexible"),
    LINEAR("linear");

    public static final Codec<ProgressionMode> CODEC = StringRepresentable.fromEnum(ProgressionMode::values);

    private final String name;

    ProgressionMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

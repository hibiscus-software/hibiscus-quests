/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// How a quest's dependencies unlock it: all of them (default), any one, one_of (any, and completing it locks the
// other one_of quests sharing a dependency: a branch), or at least min_required.
public enum DependencyMode implements StringRepresentable {
    ALL("all"),
    ANY("any"),
    ONE_OF("one_of"),
    MIN("min");

    public static final Codec<DependencyMode> CODEC = StringRepresentable.fromEnum(DependencyMode::values);

    private final String name;

    DependencyMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

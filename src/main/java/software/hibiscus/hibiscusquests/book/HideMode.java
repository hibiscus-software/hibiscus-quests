/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// When a quest's node is hidden from players: never, until it unlocks, until one of its dependencies is visible, or
// until it's complete.
public enum HideMode implements StringRepresentable {
    NEVER("never"),
    UNTIL_UNLOCKED("until_unlocked"),
    UNTIL_DEPENDENCIES_VISIBLE("until_dependencies_visible"),
    ALWAYS_UNTIL_COMPLETE("always_until_complete");

    public static final Codec<HideMode> CODEC = StringRepresentable.fromEnum(HideMode::values);

    private final String name;

    HideMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

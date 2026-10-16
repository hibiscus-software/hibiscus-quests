/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// Whether a reward is claimed by itself when its quest completes, and whether the player hears about it.
public enum AutoClaim implements StringRepresentable {
    OFF("off"),
    ENABLED("enabled"),
    NO_TOAST("no_toast"),
    INVISIBLE("invisible");

    public static final Codec<AutoClaim> CODEC = StringRepresentable.fromEnum(AutoClaim::values);

    private final String name;

    AutoClaim(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

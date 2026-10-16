/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// Who progress belongs to: the player's team through the Hibiscus Lib Teams API (default), or each player alone.
public enum ProgressMode implements StringRepresentable {
    TEAM("team"),
    PLAYER("player");

    public static final Codec<ProgressMode> CODEC = StringRepresentable.fromEnum(ProgressMode::values);

    private final String name;

    ProgressMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

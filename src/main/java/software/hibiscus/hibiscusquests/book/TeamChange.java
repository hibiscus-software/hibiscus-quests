/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// What happens to quest progress when a player changes team: it stays with the team they left, is copied into a new
// team that has none, or both teams' progress is merged into the new one.
public enum TeamChange implements StringRepresentable {
    KEEP_WITH_TEAM("keep_with_team"),
    COPY_TO_NEW_TEAM("copy_to_new_team"),
    MERGE("merge");

    public static final Codec<TeamChange> CODEC = StringRepresentable.fromEnum(TeamChange::values);

    private final String name;

    TeamChange(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

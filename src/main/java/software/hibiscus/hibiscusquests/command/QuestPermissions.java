/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.command;

import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import software.hibiscus.hibiscuslib.util.PermissionLevels;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// The NeoForge permission nodes for admin commands (hibiscusquests.admin.*), granted by default to ops (level 2) so a
// permissions mod can hand them out more finely. Player commands need no node. Loaded from the mod constructor so the
// nodes are declared before the server starts.
public final class QuestPermissions {
    // reload: read the pack's files again.
    public static final PermissionNode<Boolean> RELOAD = node("admin.reload");
    // change_progress: reset or complete quests for a player or team.
    public static final PermissionNode<Boolean> CHANGE_PROGRESS = node("admin.change_progress");
    // debug: why a quest is locked or incomplete.
    public static final PermissionNode<Boolean> DEBUG = node("admin.debug");

    private QuestPermissions() {}

    private static PermissionNode<Boolean> node(String name) {
        return PermissionLevels.node(HibiscusQuests.MODID, name, PermissionLevel.GAMEMASTERS);
    }

    // Forces the class to load (and so the nodes to be declared).
    public static void init() {}
}

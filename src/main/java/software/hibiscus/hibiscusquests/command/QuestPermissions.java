/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.command;

import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import software.hibiscus.hibiscuslib.util.PermissionLevels;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.config.QuestsConfig;

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
    // editor: edit mode in the book, editing_mode and generate_chapter. Its default level is the server config's
    // editor.permissionLevel (2, ops, unless changed), so it's declared here rather than through PermissionLevels.
    public static final PermissionNode<Boolean> EDITOR = new PermissionNode<>(HibiscusQuests.MODID, "editor", PermissionTypes.BOOLEAN,
            (player, playerId, context) -> player != null && PermissionLevels.hasLevel(player,
                    PermissionLevel.byId(QuestsConfig.get(QuestsConfig.EDITOR_LEVEL))));

    private QuestPermissions() {}

    private static PermissionNode<Boolean> node(String name) {
        return PermissionLevels.node(HibiscusQuests.MODID, name, PermissionLevel.GAMEMASTERS);
    }

    // Forces the class to load (and so the nodes to be declared), and declares the editor node.
    public static void init() {
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(EDITOR));
    }
}

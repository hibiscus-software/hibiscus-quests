/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscuslib.util.PermissionLevels;
import software.hibiscus.hibiscusquests.command.QuestPermissions;
import software.hibiscus.hibiscusquests.network.QuestSync;

// Who's editing the book. A player with the hibiscusquests.editor permission turns edit mode on and off from the
// book's top bar; an editor can also put someone else in edit mode with /hibiscusquests editing_mode, which lets them
// edit for the rest of their session even without the permission. Only players in edit mode can send edits. Not
// saved: everyone starts out of edit mode.
public final class EditingMode {
    private static final Set<UUID> EDITING = new HashSet<>();
    // Players an editor let edit with the command (they keep it until they log out).
    private static final Set<UUID> GRANTED = new HashSet<>();

    private EditingMode() {}

    public static boolean hasPermission(ServerPlayer player) {
        return PermissionLevels.check(player, QuestPermissions.EDITOR);
    }

    // Whether the player may turn edit mode on.
    public static boolean canEdit(ServerPlayer player) {
        return GRANTED.contains(player.getUUID()) || hasPermission(player);
    }

    public static boolean isEditing(ServerPlayer player) {
        return EDITING.contains(player.getUUID()) && canEdit(player);
    }

    // Turns edit mode on or off; grant lets a player without the permission edit (the command). Returns whether the
    // player is editing afterwards.
    public static boolean set(ServerPlayer player, boolean editing, boolean grant) {
        UUID id = player.getUUID();
        if (grant && editing) {
            GRANTED.add(id);
        }
        if (editing && canEdit(player)) {
            EDITING.add(id);
        } else {
            EDITING.remove(id);
        }
        sync(player);
        return isEditing(player);
    }

    public static void sync(ServerPlayer player) {
        QuestSync.sendEditorState(player, canEdit(player), isEditing(player));
    }

    public static void onLogin(ServerPlayer player) {
        sync(player);
    }

    public static void onLogout(UUID player) {
        EDITING.remove(player);
        GRANTED.remove(player);
    }

    public static void onServerStopped() {
        EDITING.clear();
        GRANTED.clear();
    }
}

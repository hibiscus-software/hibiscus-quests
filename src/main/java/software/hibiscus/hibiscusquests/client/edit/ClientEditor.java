/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.DynamicOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import software.hibiscus.hibiscuslib.client.HibiscusClientNetwork;
import software.hibiscus.hibiscuslib.client.gui.HibiscusToasts;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.edit.BookEdit;
import software.hibiscus.hibiscusquests.network.QuestPayloads;

// The client's side of the editor: whether this player may edit and is editing (the server says), the edits they've
// made this session for undo and redo, and the registries the pickers list (asked of the server once each). Every
// change goes to the server as an Edit; the book changes when the server's patch comes back, for every player.
public final class ClientEditor {
    private static final int HISTORY = 200;

    // One undoable change: the edits that made it, and the edits that put things back.
    private record Change(Component name, List<BookEdit> forward, List<BookEdit> back) {}

    // What a batch that's on its way was: a new change, an undo or a redo (to fix the stacks if it's refused).
    private enum Sent { DO, UNDO, REDO }

    // A registry's ids and tags, for the pickers.
    public record RegistryData(List<String> ids, List<String> tags) {}

    private static boolean canEdit;
    private static boolean editing;
    private static final Deque<Change> UNDO = new ArrayDeque<>();
    private static final Deque<Change> REDO = new ArrayDeque<>();
    private static final Deque<Sent> PENDING = new ArrayDeque<>();
    private static final Map<String, RegistryData> REGISTRIES = new HashMap<>();
    private static final Map<String, List<Consumer<RegistryData>>> WAITING = new HashMap<>();

    private ClientEditor() {}

    public static boolean canEdit() {
        return canEdit;
    }

    public static boolean isEditing() {
        return editing && canEdit;
    }

    public static void onState(boolean newCanEdit, boolean newEditing) {
        boolean was = isEditing();
        canEdit = newCanEdit;
        editing = newEditing;
        if (was && !isEditing()) {
            UNDO.clear();
            REDO.clear();
        }
        ClientQuestState.refresh();
    }

    // Asks the server to turn edit mode on or off.
    public static void setEditing(boolean on) {
        HibiscusClientNetwork.sendToServer(new QuestPayloads.SetEditing(on));
    }

    // Sends a change and remembers it for undo. back must undo forward on the book as it is now.
    public static void submit(Component name, List<BookEdit> forward, List<BookEdit> back) {
        if (forward.isEmpty() || !isEditing()) {
            return;
        }
        send(forward, Sent.DO);
        UNDO.push(new Change(name, List.copyOf(forward), List.copyOf(back)));
        while (UNDO.size() > HISTORY) {
            UNDO.removeLast();
        }
        REDO.clear();
    }

    public static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    public static boolean canRedo() {
        return !REDO.isEmpty();
    }

    public static void undo() {
        Change change = UNDO.poll();
        if (change != null && isEditing()) {
            send(change.back(), Sent.UNDO);
            REDO.push(change);
            HibiscusToasts.show(Component.translatable("hibiscusquests.edit.undone", change.name()), null, Icon.NONE, 1500, ClientEditor.class);
        }
    }

    public static void redo() {
        Change change = REDO.poll();
        if (change != null && isEditing()) {
            send(change.forward(), Sent.REDO);
            UNDO.push(change);
            HibiscusToasts.show(Component.translatable("hibiscusquests.edit.redone", change.name()), null, Icon.NONE, 1500, ClientEditor.class);
        }
    }

    private static void send(List<BookEdit> edits, Sent kind) {
        for (int from = 0; from < edits.size(); from += QuestPayloads.Edit.MAX_EDITS) {
            List<BookEdit> part = edits.subList(from, Math.min(edits.size(), from + QuestPayloads.Edit.MAX_EDITS));
            HibiscusClientNetwork.sendToServer(new QuestPayloads.Edit(new ArrayList<>(part)));
            PENDING.add(from == 0 ? kind : Sent.DO);
        }
    }

    // The server's answer to a batch. A refused change didn't happen, so it comes off the stack it went on.
    public static void onResult(boolean ok, List<Component> messages) {
        Sent sent = PENDING.poll();
        if (!ok) {
            if (sent == Sent.DO && !UNDO.isEmpty()) {
                UNDO.pop();
            } else if (sent == Sent.UNDO && !REDO.isEmpty()) {
                UNDO.push(REDO.pop());
            } else if (sent == Sent.REDO && !UNDO.isEmpty()) {
                REDO.push(UNDO.pop());
            }
        }
        if (!ok) {
            // Screens drew the change ahead of the server (a moved node): show the book as it is.
            ClientQuestState.refresh();
        }
        if (!messages.isEmpty() || !ok) {
            Component title = Component.translatable(ok ? "hibiscusquests.edit.warning" : "hibiscusquests.edit.refused");
            Component first = messages.isEmpty() ? null : messages.getFirst();
            HibiscusToasts.show(title, first, Icon.NONE, 6000, null);
        }
    }

    // ---- registries for the pickers ----

    // Calls back with a registry's ids and tags, from the cache or once the server has sent them.
    public static void registry(String registry, Consumer<RegistryData> callback) {
        RegistryData known = REGISTRIES.get(registry);
        if (known != null) {
            callback.accept(known);
            return;
        }
        List<Consumer<RegistryData>> waiting = WAITING.computeIfAbsent(registry, key -> new ArrayList<>());
        waiting.add(callback);
        if (waiting.size() == 1) {
            HibiscusClientNetwork.sendToServer(new QuestPayloads.RegistryRequest(registry));
        }
    }

    public static void onRegistryList(String registry, List<String> ids, List<String> tags) {
        RegistryData data = new RegistryData(List.copyOf(ids), List.copyOf(tags));
        REGISTRIES.put(registry, data);
        List<Consumer<RegistryData>> waiting = WAITING.remove(registry);
        if (waiting != null) {
            waiting.forEach(callback -> callback.accept(data));
        }
    }

    // Leaving a server.
    public static void clear() {
        canEdit = false;
        editing = false;
        UNDO.clear();
        REDO.clear();
        PENDING.clear();
        REGISTRIES.clear();
        WAITING.clear();
    }

    // The client's registries, for encoding and checking edits as the files hold them.
    public static DynamicOps<Tag> ops() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        RegistryAccess access = connection != null ? connection.registryAccess() : RegistryAccess.EMPTY;
        return RegistryOps.create(NbtOps.INSTANCE, access);
    }
}

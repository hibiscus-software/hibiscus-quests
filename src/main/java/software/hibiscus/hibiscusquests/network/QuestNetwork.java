/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import software.hibiscus.hibiscuslib.network.HibiscusNetwork;
import software.hibiscus.hibiscuslib.util.RateLimiter;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.edit.BookEditor;
import software.hibiscus.hibiscusquests.edit.EditingMode;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ProgressManager;

// Registers the payloads and handles the client's requests. Each request is checked here, never trusted: the sender's
// rate, that the quest and task or reward exist, and (inside ProgressManager and ActiveTasks) that the quest is
// complete or the task active for the sender's own team.
public final class QuestNetwork {
    // Bumped whenever a payload changes; clients and servers must match.
    public static final String VERSION = "4";

    private static final RateLimiter BOOK_REQUESTS = new RateLimiter(3, 0.2);
    private static final RateLimiter CLAIMS = new RateLimiter(20, 10);
    private static final RateLimiter SUBMITS = new RateLimiter(10, 5);
    private static final RateLimiter EDITS = new RateLimiter(40, 10);
    private static final RateLimiter REGISTRIES = new RateLimiter(20, 2);

    private QuestNetwork() {}

    public static void register(IEventBus modEventBus) {
        HibiscusNetwork.channel(modEventBus, VERSION, channel -> channel
                .toClient(QuestPayloads.BookHash.TYPE, QuestPayloads.BookHash.STREAM_CODEC, (payload, context) -> ClientBridge.bookHash(payload))
                .toClient(QuestPayloads.BookChunk.TYPE, QuestPayloads.BookChunk.STREAM_CODEC, (payload, context) -> ClientBridge.bookChunk(payload))
                .toClient(QuestPayloads.Progress.TYPE, QuestPayloads.Progress.STREAM_CODEC, (payload, context) -> ClientBridge.progress(payload))
                .toClient(QuestPayloads.OpenBook.TYPE, QuestPayloads.OpenBook.STREAM_CODEC, (payload, context) -> ClientBridge.openBook(payload))
                .toClient(QuestPayloads.Toast.TYPE, QuestPayloads.Toast.STREAM_CODEC, (payload, context) -> ClientBridge.toast(payload))
                .toClient(QuestPayloads.BookPatched.TYPE, QuestPayloads.BookPatched.STREAM_CODEC, (payload, context) -> ClientBridge.bookPatched(payload))
                .toClient(QuestPayloads.EditorState.TYPE, QuestPayloads.EditorState.STREAM_CODEC, (payload, context) -> ClientBridge.editorState(payload))
                .toClient(QuestPayloads.EditResult.TYPE, QuestPayloads.EditResult.STREAM_CODEC, (payload, context) -> ClientBridge.editResult(payload))
                .toClient(QuestPayloads.RegistryList.TYPE, QuestPayloads.RegistryList.STREAM_CODEC, (payload, context) -> ClientBridge.registryList(payload))
                .toServer(QuestPayloads.Edit.TYPE, QuestPayloads.Edit.STREAM_CODEC, QuestNetwork::edit)
                .toServer(QuestPayloads.SetEditing.TYPE, QuestPayloads.SetEditing.STREAM_CODEC, QuestNetwork::setEditing)
                .toServer(QuestPayloads.RegistryRequest.TYPE, QuestPayloads.RegistryRequest.STREAM_CODEC, QuestNetwork::registryRequest)
                .toServer(QuestPayloads.Pin.TYPE, QuestPayloads.Pin.STREAM_CODEC, QuestNetwork::pin)
                .toServer(QuestPayloads.BookRequest.TYPE, QuestPayloads.BookRequest.STREAM_CODEC, QuestNetwork::bookRequest)
                .toServer(QuestPayloads.Claim.TYPE, QuestPayloads.Claim.STREAM_CODEC, QuestNetwork::claim)
                .toServer(QuestPayloads.ClaimAll.TYPE, QuestPayloads.ClaimAll.STREAM_CODEC, QuestNetwork::claimAll)
                .toServer(QuestPayloads.Submit.TYPE, QuestPayloads.Submit.STREAM_CODEC, QuestNetwork::submit));
    }

    public static void forget(UUID player) {
        BOOK_REQUESTS.forget(player);
        CLAIMS.forget(player);
        SUBMITS.forget(player);
        EDITS.forget(player);
        REGISTRIES.forget(player);
    }

    private static Optional<ServerPlayer> sender(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? Optional.of(player) : Optional.empty();
    }

    private static void bookRequest(QuestPayloads.BookRequest payload, IPayloadContext context) {
        sender(context).filter(player -> BOOK_REQUESTS.tryAcquire(player.getUUID()))
                .ifPresent(player -> QuestSync.sendBook(player, payload.hash()));
    }

    private static void claim(QuestPayloads.Claim payload, IPayloadContext context) {
        Optional<QuestRef> quest = QuestRef.parse(payload.quest());
        sender(context).filter(player -> quest.isPresent() && CLAIMS.tryAcquire(player.getUUID()))
                .ifPresent(player -> ProgressManager.claim(player, quest.get(), payload.reward(), payload.choice()));
    }

    private static void pin(QuestPayloads.Pin payload, IPayloadContext context) {
        Optional<QuestRef> quest = QuestRef.parse(payload.quest());
        sender(context).filter(player -> quest.isPresent() && SUBMITS.tryAcquire(player.getUUID()))
                .ifPresent(player -> ProgressManager.setPinned(player, quest.get(), payload.pinned()));
    }

    private static void claimAll(QuestPayloads.ClaimAll payload, IPayloadContext context) {
        sender(context).filter(player -> CLAIMS.tryAcquire(player.getUUID())).ifPresent(ProgressManager::claimAll);
    }

    // An edit from the book's editor (BookEditor checks the sender is editing, and the edit itself).
    private static void edit(QuestPayloads.Edit payload, IPayloadContext context) {
        sender(context).ifPresent(player -> {
            if (!EDITS.tryAcquire(player.getUUID())) {
                QuestSync.sendEditResult(player, BookEditor.Result.failed(Component.translatable("hibiscusquests.edit.error.too_fast")));
                return;
            }
            QuestSync.sendEditResult(player, BookEditor.applyFrom(player, payload.edits()));
        });
    }

    private static void setEditing(QuestPayloads.SetEditing payload, IPayloadContext context) {
        sender(context).filter(player -> SUBMITS.tryAcquire(player.getUUID()))
                .ifPresent(player -> EditingMode.set(player, payload.editing(), false));
    }

    private static void registryRequest(QuestPayloads.RegistryRequest payload, IPayloadContext context) {
        sender(context).filter(player -> EditingMode.isEditing(player) && REGISTRIES.tryAcquire(player.getUUID()))
                .ifPresent(player -> QuestSync.sendRegistry(player, payload.registry()));
    }

    private static void submit(QuestPayloads.Submit payload, IPayloadContext context) {
        Optional<QuestRef> quest = QuestRef.parse(payload.quest());
        sender(context).filter(player -> quest.isPresent() && SUBMITS.tryAcquire(player.getUUID()))
                .ifPresent(player -> ActiveTasks.submit(player, quest.get(), payload.task()));
    }
}

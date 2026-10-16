/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import software.hibiscus.hibiscuslib.network.HibiscusNetwork;
import software.hibiscus.hibiscuslib.util.RateLimiter;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ProgressManager;

// Registers the payloads and handles the client's requests. Each request is checked here, never trusted: the sender's
// rate, that the quest and task or reward exist, and (inside ProgressManager and ActiveTasks) that the quest is
// complete or the task active for the sender's own team.
public final class QuestNetwork {
    // Bumped whenever a payload changes; clients and servers must match.
    public static final String VERSION = "3";

    private static final RateLimiter BOOK_REQUESTS = new RateLimiter(3, 0.2);
    private static final RateLimiter CLAIMS = new RateLimiter(20, 10);
    private static final RateLimiter SUBMITS = new RateLimiter(10, 5);

    private QuestNetwork() {}

    public static void register(IEventBus modEventBus) {
        HibiscusNetwork.channel(modEventBus, VERSION, channel -> channel
                .toClient(QuestPayloads.BookHash.TYPE, QuestPayloads.BookHash.STREAM_CODEC, (payload, context) -> ClientBridge.bookHash(payload))
                .toClient(QuestPayloads.BookChunk.TYPE, QuestPayloads.BookChunk.STREAM_CODEC, (payload, context) -> ClientBridge.bookChunk(payload))
                .toClient(QuestPayloads.Progress.TYPE, QuestPayloads.Progress.STREAM_CODEC, (payload, context) -> ClientBridge.progress(payload))
                .toClient(QuestPayloads.OpenBook.TYPE, QuestPayloads.OpenBook.STREAM_CODEC, (payload, context) -> ClientBridge.openBook(payload))
                .toClient(QuestPayloads.Toast.TYPE, QuestPayloads.Toast.STREAM_CODEC, (payload, context) -> ClientBridge.toast(payload))
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

    private static void submit(QuestPayloads.Submit payload, IPayloadContext context) {
        Optional<QuestRef> quest = QuestRef.parse(payload.quest());
        sender(context).filter(player -> quest.isPresent() && SUBMITS.tryAcquire(player.getUUID()))
                .ifPresent(player -> ActiveTasks.submit(player, quest.get(), payload.task()));
    }
}

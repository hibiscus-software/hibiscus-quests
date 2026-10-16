/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscuslib.network.HibiscusNetwork;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestTeams;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// Keeps clients' copies up to date. The book goes as compressed NBT, split into chunks under the payload size limit,
// after its content hash: a client that already has that book (it's unchanged since its last visit, or after a
// /reload that changed nothing) skips the download. Progress goes in full on login and team changes, then as deltas of
// the quests that changed, batched per team at the end of each tick and sent to every online member.
public final class QuestSync {
    public static final int CHUNK_SIZE = 256 * 1024;

    private static byte[] bookBytes = new byte[0];
    private static String bookHash = "";
    private static final Map<UUID, Set<String>> PENDING = new HashMap<>();
    private static final Map<UUID, List<String>> COMPLETED = new HashMap<>();
    private static final Set<UUID> FULL = new HashSet<>();

    private QuestSync() {}

    public static String bookHash() {
        return bookHash;
    }

    public static void onBookChanged(MinecraftServer server) {
        QuestBook book = BookManager.book();
        try {
            bookBytes = encode(book, BookManager.ops(server));
        } catch (IOException | RuntimeException e) {
            HibiscusQuests.LOGGER.error("Couldn't encode the quest book for clients", e);
            bookBytes = new byte[0];
        }
        bookHash = hash(bookBytes);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendBookHash(player);
        }
    }

    public static void onServerStopped() {
        bookBytes = new byte[0];
        bookHash = "";
        PENDING.clear();
        COMPLETED.clear();
        FULL.clear();
    }

    public static void onLogin(ServerPlayer player) {
        sendBookHash(player);
    }

    private static void sendBookHash(ServerPlayer player) {
        send(player, new QuestPayloads.BookHash(bookHash));
    }

    // The book, in chunks, if the player asked for the current one.
    static void sendBook(ServerPlayer player, String hash) {
        if (!hash.equals(bookHash) || bookBytes.length == 0) {
            return;
        }
        int count = Math.max(1, (bookBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        for (int index = 0; index < count; index++) {
            byte[] data = Arrays.copyOfRange(bookBytes, index * CHUNK_SIZE, Math.min(bookBytes.length, (index + 1) * CHUNK_SIZE));
            send(player, new QuestPayloads.BookChunk(bookHash, index, count, data));
        }
    }

    // Everything about the player's team's progress.
    public static void sendProgress(ServerPlayer player) {
        UUID teamId = QuestTeams.teamIdOf(player);
        TeamProgress progress = ProgressManager.progress(teamId);
        send(player, new QuestPayloads.Progress(teamId, true, progress.save(), List.of()));
    }

    public static void queueQuest(UUID teamId, String questKey) {
        PENDING.computeIfAbsent(teamId, key -> new LinkedHashSet<>()).add(questKey);
    }

    public static void queueCompleted(UUID teamId, QuestRef quest) {
        COMPLETED.computeIfAbsent(teamId, key -> new ArrayList<>()).add(quest.toString());
    }

    public static void queueFull(UUID teamId) {
        FULL.add(teamId);
    }

    // Sends what changed this tick to each team's online members.
    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty() && FULL.isEmpty() && COMPLETED.isEmpty()) {
            return;
        }
        Set<UUID> teams = new LinkedHashSet<>(FULL);
        teams.addAll(PENDING.keySet());
        teams.addAll(COMPLETED.keySet());
        for (UUID teamId : teams) {
            List<ServerPlayer> members = QuestTeams.onlineMembers(server, teamId);
            if (members.isEmpty()) {
                continue;
            }
            TeamProgress progress = ProgressManager.progress(teamId);
            List<String> completed = COMPLETED.getOrDefault(teamId, List.of());
            boolean full = FULL.contains(teamId);
            CompoundTag data = full ? progress.save() : progress.delta(PENDING.getOrDefault(teamId, Set.of()));
            QuestPayloads.Progress payload = new QuestPayloads.Progress(teamId, full, data, List.copyOf(completed));
            for (ServerPlayer member : members) {
                send(member, payload);
            }
        }
        PENDING.clear();
        COMPLETED.clear();
        FULL.clear();
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            HibiscusNetwork.sendToPlayer(player, payload);
        }
    }

    // ---- the book's network form ----

    public static byte[] encode(QuestBook book, DynamicOps<Tag> ops) throws IOException {
        Tag tag = QuestBook.NETWORK_CODEC.encodeStart(ops, book).getOrThrow(message -> new IOException("Can't encode the book: " + message));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed((CompoundTag) tag, out);
        return out.toByteArray();
    }

    public static QuestBook decode(byte[] bytes, DynamicOps<Tag> ops) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        return QuestBook.NETWORK_CODEC.parse(ops, tag).getOrThrow(message -> new IOException("Can't read the book: " + message));
    }

    // The hash covers the network version too, so a client's cached book from an older format is never reused.
    public static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(QuestNetwork.VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}

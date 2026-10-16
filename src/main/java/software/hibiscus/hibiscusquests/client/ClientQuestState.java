/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import software.hibiscus.hibiscuslib.client.HibiscusClientNetwork;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.network.QuestPayloads;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// The client's copy of the book and of its team's progress, as fresh as the last sync. Read-only: every change goes to
// the server as a request. Books are cached by hash in memory and on disk (<game dir>/hibiscusquests/book-cache/), so
// joining a server whose book hasn't changed downloads nothing.
public final class ClientQuestState {
    private static final int DISK_CACHE_SIZE = 5;
    private static final int MEMORY_CACHE_SIZE = 3;
    private static final UUID NOBODY = new UUID(0, 0);

    private static QuestBook book = QuestBook.EMPTY;
    private static String hash = "";
    private static TeamProgress progress = new TeamProgress(NOBODY);
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<String, QuestBook> MEMORY = new LinkedHashMap<>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, QuestBook> eldest) {
            return size() > MEMORY_CACHE_SIZE;
        }
    };

    // The book being downloaded.
    private static @Nullable String incoming;
    private static byte[] @Nullable [] chunks;
    private static int received;

    private ClientQuestState() {}

    public static QuestBook book() {
        return book;
    }

    public static TeamProgress progress() {
        return progress;
    }

    public static boolean hasBook() {
        return !hash.isEmpty();
    }

    // Called (on the client thread) whenever the book or progress changes, so screens can rebuild.
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static void changed() {
        LISTENERS.forEach(Runnable::run);
    }

    public static void onBookHash(String newHash) {
        if (newHash.isEmpty() || newHash.equals(hash)) {
            return;
        }
        QuestBook cached = MEMORY.get(newHash);
        if (cached == null) {
            cached = readDiskCache(newHash);
        }
        if (cached != null) {
            install(newHash, cached);
            return;
        }
        incoming = newHash;
        chunks = null;
        received = 0;
        HibiscusClientNetwork.sendToServer(new QuestPayloads.BookRequest(newHash));
    }

    public static void onBookChunk(QuestPayloads.BookChunk chunk) {
        if (!chunk.hash().equals(incoming) || chunk.count() <= 0 || chunk.index() < 0 || chunk.index() >= chunk.count()) {
            return;
        }
        if (chunks == null || chunks.length != chunk.count()) {
            chunks = new byte[chunk.count()][];
            received = 0;
        }
        if (chunks[chunk.index()] == null) {
            chunks[chunk.index()] = chunk.data();
            received++;
        }
        if (received < chunks.length) {
            return;
        }
        int size = 0;
        for (byte[] part : chunks) {
            size += part.length;
        }
        byte[] bytes = new byte[size];
        int offset = 0;
        for (byte[] part : chunks) {
            System.arraycopy(part, 0, bytes, offset, part.length);
            offset += part.length;
        }
        String done = chunk.hash();
        incoming = null;
        chunks = null;
        if (!QuestSync.hash(bytes).equals(done)) {
            HibiscusQuests.LOGGER.warn("The quest book from the server didn't match its hash; ignoring it");
            return;
        }
        try {
            QuestBook decoded = QuestSync.decode(bytes, ops());
            writeDiskCache(done, bytes);
            install(done, decoded);
        } catch (IOException | RuntimeException e) {
            HibiscusQuests.LOGGER.error("Couldn't read the quest book from the server", e);
        }
    }

    private static void install(String newHash, QuestBook newBook) {
        hash = newHash;
        book = newBook;
        MEMORY.put(newHash, newBook);
        changed();
    }

    public static void onProgress(QuestPayloads.Progress payload) {
        if (payload.full()) {
            try {
                progress = TeamProgress.load(payload.data());
            } catch (IllegalArgumentException e) {
                HibiscusQuests.LOGGER.warn("Ignoring unreadable quest progress from the server: {}", e.getMessage());
                return;
            }
        } else if (payload.teamId().equals(progress.teamId())) {
            progress.applyDelta(payload.data());
        } else {
            return; // a delta for a team we've just left; the full sync for the new one follows
        }
        changed();
    }

    // Leaving a server: forget its progress (books stay cached by hash).
    public static void clear() {
        hash = "";
        book = QuestBook.EMPTY;
        progress = new TeamProgress(NOBODY);
        incoming = null;
        chunks = null;
        changed();
    }

    private static DynamicOps<Tag> ops() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("not connected");
        }
        return RegistryOps.create(NbtOps.INSTANCE, connection.registryAccess());
    }

    // ---- disk cache ----

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(HibiscusQuests.MODID).resolve("book-cache");
    }

    private static @Nullable QuestBook readDiskCache(String key) {
        if (!key.matches("[0-9a-f]{64}")) {
            return null;
        }
        Path file = cacheDir().resolve(key + ".nbt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (!QuestSync.hash(bytes).equals(key)) {
                Files.deleteIfExists(file);
                return null;
            }
            return QuestSync.decode(bytes, ops());
        } catch (IOException | RuntimeException e) {
            HibiscusQuests.LOGGER.debug("Ignoring cached quest book {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static void writeDiskCache(String key, byte[] bytes) {
        Path dir = cacheDir();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key + ".nbt"), bytes);
            List<Path> files = new ArrayList<>();
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(path -> path.getFileName().toString().endsWith(".nbt")).forEach(files::add);
            }
            files.sort(Comparator.comparingLong(ClientQuestState::modified).reversed());
            for (Path old : files.subList(Math.min(DISK_CACHE_SIZE, files.size()), files.size())) {
                Files.deleteIfExists(old);
            }
        } catch (IOException e) {
            HibiscusQuests.LOGGER.debug("Couldn't cache the quest book: {}", e.getMessage());
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.event.BookReloadedEvent;
import software.hibiscus.hibiscusquests.book.BookSources.PackChange;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.task.CriterionTasks;

// The server's book. It's rebuilt from its sources (the pack's files and the chapters data packs ship) when the server
// starts, after /reload (the data-pack chapters change), after /hibiscusquests reload (the pack's files are read again)
// and after edits; each rebuild posts BookReloadedEvent, lets progress catch up and sends the book to every player.
public final class BookManager {
    private static volatile QuestBook book = QuestBook.EMPTY;
    private static BookSources packSources = BookSources.EMPTY;
    private static List<RawFile> modChapters = List.of();
    private static List<RawFile> modGroups = List.of();
    private static List<RawFile> modTables = List.of();
    private static List<String> warnings = List.of();
    // The pack files' problems from the last read, kept so every rebuild reports them.
    private static List<String> readWarnings = List.of();
    private static @Nullable MinecraftServer server;
    // Tests point the pack somewhere of their own.
    private static @Nullable Path packDirectoryOverride;

    private BookManager() {}

    public static QuestBook book() {
        return book;
    }

    // What went wrong in the last rebuild (also logged), for /hibiscusquests reload.
    public static List<String> warnings() {
        return warnings;
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    // Files read and written with the server's registries (items, enchantments and the like by id).
    public static DynamicOps<Tag> ops(MinecraftServer server) {
        return RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
    }

    public static Path packDirectory(MinecraftServer server) {
        return packDirectoryOverride != null ? packDirectoryOverride : PackFiles.directory(server);
    }

    // For tests: reads the pack from another directory (null: the real one again) and rebuilds.
    public static void usePackDirectory(MinecraftServer running, @Nullable Path directory) {
        packDirectoryOverride = directory;
        reload(running);
    }

    // The pack's files as last read or edited (the editor checks edits against them).
    public static BookSources packSources() {
        return packSources;
    }

    public static void onServerStarting(MinecraftServer starting) {
        server = starting;
        readPack(starting);
        rebuild(starting);
    }

    public static void onServerStopped() {
        server = null;
        book = QuestBook.EMPTY;
        packSources = BookSources.EMPTY;
    }

    // The data-pack chapters were (re)loaded. On server start this runs before the server exists, so the book is built
    // in onServerStarting; after /reload, OnDatapackSyncEvent rebuilds it once the new data is in place.
    static void onDataLoaded(List<RawFile> chapters, List<RawFile> groups, List<RawFile> tables) {
        modChapters = List.copyOf(chapters);
        modGroups = List.copyOf(groups);
        modTables = List.copyOf(tables);
    }

    // After /reload (player is null then), with the new data packs live.
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null && server != null) {
            rebuild(server);
        }
    }

    // /hibiscusquests reload: reads the pack's files again and rebuilds. Returns the warnings.
    public static List<String> reload(MinecraftServer running) {
        readPack(running);
        rebuild(running);
        return warnings;
    }

    private static void readPack(MinecraftServer running) {
        List<String> found = new ArrayList<>();
        packSources = PackFiles.read(packDirectory(running), found::add);
        found.forEach(warning -> HibiscusQuests.LOGGER.warn("Quest book: {}", warning));
        readWarnings = found;
    }

    // Merges, decodes and installs the book, then brings everything that depends on it up to date.
    public static void rebuild(MinecraftServer running) {
        install(running, build(running), warnings, null);
    }

    // The editor changed the pack's files (they're written already): the same rebuild from the pack's files in memory,
    // and clients are sent just the parts that changed. Returns the new warnings.
    public static List<String> onPackEdited(MinecraftServer running, PackChange change) {
        packSources = packSources.withPackChange(change);
        List<String> before = warnings;
        QuestBook built = build(running);
        install(running, built, warnings, change);
        return warnings.stream().filter(warning -> !before.contains(warning)).toList();
    }

    private static QuestBook build(MinecraftServer running) {
        List<String> found = new ArrayList<>(readWarnings);
        BookSources sources = packSources.withModFiles(modChapters, modGroups, modTables);
        DynamicOps<Tag> ops = ops(running);
        QuestBook built = BookMerger.decode(BookMerger.merge(sources, found::add), ops, found::add);
        CriterionTasks.rebuild(built, ops, found::add);
        found.subList(readWarnings.size(), found.size()).forEach(warning -> HibiscusQuests.LOGGER.warn("Quest book: {}", warning));
        warnings = List.copyOf(found);
        return built;
    }

    // Puts a book in place (also used by tests).
    public static void install(MinecraftServer running, QuestBook built, List<String> found) {
        install(running, built, found, null);
    }

    private static void install(MinecraftServer running, QuestBook built, List<String> found, @Nullable PackChange change) {
        book = built;
        warnings = List.copyOf(found);
        HibiscusQuests.LOGGER.info("Quest book: {} chapters, {} quests{}", built.chapters().size(), built.questCount(),
                found.isEmpty() ? "" : ", " + found.size() + " warnings");
        NeoForge.EVENT_BUS.post(new BookReloadedEvent(built));
        ProgressManager.onBookChanged(running);
        if (change == null) {
            QuestSync.onBookChanged(running);
        } else {
            QuestSync.onBookPatched(running, change);
        }
    }
}

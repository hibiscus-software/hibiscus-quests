/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;

// Reads the chapters mods and data packs ship, data/<namespace>/hibiscusquests/chapters/*.snbt,
// data/<namespace>/hibiscusquests/chapter_groups.snbt and data/<namespace>/hibiscusquests/reward_tables/*.snbt,
// whenever data packs load (server start, /reload). A data pack overrides a mod's file by shipping the same path,
// which is how a pack can replace, or remove (remove: true), one.
// The files are handed to BookManager, which merges them with the pack's own and rebuilds the book.
public final class DataChapters extends SimplePreparableReloadListener<DataChapters.Loaded> {
    public static final Identifier ID = HibiscusQuests.id("chapters");
    private static final String ROOT = HibiscusQuests.MODID;

    public record Loaded(List<RawFile> chapters, List<RawFile> groups, List<RawFile> tables, List<String> warnings) {}

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<RawFile> chapters = new ArrayList<>();
        List<RawFile> groups = new ArrayList<>();
        List<RawFile> tables = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<Identifier, Resource> files = manager.listResources(ROOT, id -> id.getPath().endsWith(".snbt"));
        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath().substring(ROOT.length() + 1);
            String origin = file.getNamespace() + ":" + file.getPath() + " (" + entry.getValue().sourcePackId() + ")";
            boolean chapter = path.startsWith(PackFiles.CHAPTERS + "/") && path.indexOf('/', PackFiles.CHAPTERS.length() + 1) < 0;
            boolean group = path.equals(PackFiles.GROUPS);
            boolean table = path.startsWith(PackFiles.TABLES + "/") && path.indexOf('/', PackFiles.TABLES.length() + 1) < 0;
            if (!chapter && !group && !table) {
                continue;
            }
            CompoundTag tag;
            try (Reader reader = entry.getValue().openAsReader()) {
                tag = TagParser.parseCompoundFully(readAll(reader));
            } catch (IOException | CommandSyntaxException e) {
                warnings.add(origin + ": " + e.getMessage());
                continue;
            }
            if (chapter) {
                String name = path.substring(PackFiles.CHAPTERS.length() + 1);
                chapters.add(new RawFile(PackFiles.chapterId(tag, name), Optional.of(file.getNamespace()), tag, origin));
            } else if (table) {
                String name = path.substring(PackFiles.TABLES.length() + 1);
                tables.add(new RawFile(PackFiles.chapterId(tag, name), Optional.of(file.getNamespace()), tag, origin));
            } else {
                groups.add(new RawFile(PackFiles.GROUPS, Optional.of(file.getNamespace()), tag, origin));
            }
        }
        return new Loaded(chapters, groups, tables, warnings);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            text.append(buffer, 0, read);
        }
        return text.toString();
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        loaded.warnings().forEach(warning -> HibiscusQuests.LOGGER.warn("Quest book: {}", warning));
        BookManager.onDataLoaded(loaded.chapters(), loaded.groups(), loaded.tables());
    }
}

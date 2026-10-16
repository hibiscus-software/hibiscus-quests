/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;

// The book's files before merging, as raw tags: the pack's own (config/hibiscusquests/, editable) and the ones mods and
// data packs ship under data/<namespace>/hibiscusquests/ (read-only defaults).
public record BookSources(Optional<CompoundTag> settings, List<RawFile> groupFiles, List<RawFile> packChapters,
        List<RawFile> modChapters, List<RawFile> packTables, List<RawFile> modTables) {
    public static final BookSources EMPTY = new BookSources(Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of());

    public BookSources {
        groupFiles = List.copyOf(groupFiles);
        packChapters = List.copyOf(packChapters);
        modChapters = List.copyOf(modChapters);
        packTables = List.copyOf(packTables);
        modTables = List.copyOf(modTables);
    }

    // Chapters only (tests and simple callers).
    public BookSources(Optional<CompoundTag> settings, List<RawFile> groupFiles, List<RawFile> packChapters, List<RawFile> modChapters) {
        this(settings, groupFiles, packChapters, modChapters, List.of(), List.of());
    }

    // One file: id is the chapter id (the file's id field, else its name), source the namespace that shipped it (empty
    // for the pack), origin where it came from, for messages.
    public record RawFile(String id, Optional<String> source, CompoundTag tag, String origin) {}

    // The pack's files after an edit: chapters and tables replaced by id (or removed when empty), and the settings and
    // groups files replaced when given.
    public BookSources withPackChange(PackChange change) {
        List<RawFile> chapters = replaceById(packChapters, change.chapters(), PackFiles.CHAPTERS);
        List<RawFile> tables = replaceById(packTables, change.tables(), PackFiles.TABLES);
        List<RawFile> groups = new ArrayList<>(groupFiles);
        if (change.groups().isPresent()) {
            groups.removeIf(file -> file.source().isEmpty());
            groups.add(new RawFile(PackFiles.GROUPS, Optional.empty(), change.groups().get(), PackFiles.GROUPS));
        }
        Optional<CompoundTag> newSettings = change.settings().isPresent() ? change.settings() : settings;
        return new BookSources(newSettings, groups, chapters, modChapters, tables, modTables);
    }

    private static List<RawFile> replaceById(List<RawFile> files, Map<String, Optional<CompoundTag>> changes, String directory) {
        List<RawFile> result = new ArrayList<>();
        for (RawFile file : files) {
            if (!changes.containsKey(file.id())) {
                result.add(file);
            }
        }
        changes.forEach((id, tag) -> tag.ifPresent(value -> result.add(new RawFile(id, Optional.empty(), value, directory + "/" + id + ".snbt"))));
        return result;
    }

    // What an edit changed in the pack's files, as the files' tags: by chapter and table id (empty: removed), and the
    // groups and settings files when they changed.
    public record PackChange(Map<String, Optional<CompoundTag>> chapters, Optional<CompoundTag> groups,
            Optional<CompoundTag> settings, Map<String, Optional<CompoundTag>> tables) {
        public PackChange {
            chapters = Collections.unmodifiableMap(new LinkedHashMap<>(chapters));
            tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        }
    }

    public BookSources withModFiles(List<RawFile> chapters, List<RawFile> modGroups, List<RawFile> tables) {
        List<RawFile> groups = new ArrayList<>(groupFiles.stream().filter(file -> file.source().isEmpty()).toList());
        groups.addAll(modGroups);
        return new BookSources(settings, groups, packChapters, chapters, packTables, tables);
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;

// The book's files before merging, as raw tags: the pack's own (config/hibiscusquests/, editable) and the ones mods and
// data packs ship under data/<namespace>/hibiscusquests/ (read-only defaults).
public record BookSources(Optional<CompoundTag> settings, List<RawFile> groupFiles, List<RawFile> packChapters,
        List<RawFile> modChapters) {
    public static final BookSources EMPTY = new BookSources(Optional.empty(), List.of(), List.of(), List.of());

    public BookSources {
        groupFiles = List.copyOf(groupFiles);
        packChapters = List.copyOf(packChapters);
        modChapters = List.copyOf(modChapters);
    }

    // One file: id is the chapter id (the file's id field, else its name), source the namespace that shipped it (empty
    // for the pack), origin where it came from, for messages.
    public record RawFile(String id, Optional<String> source, CompoundTag tag, String origin) {}

    public BookSources withModChapters(List<RawFile> chapters, List<RawFile> modGroups) {
        List<RawFile> groups = new ArrayList<>(groupFiles.stream().filter(file -> file.source().isEmpty()).toList());
        groups.addAll(modGroups);
        return new BookSources(settings, groups, packChapters, chapters);
    }
}

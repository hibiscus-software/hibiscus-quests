/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.ModList;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;

// Merges the book's sources and decodes the result into a QuestBook.
//
// Merge rules: mod-provided chapters go in first (a chapter id shipped by two namespaces keeps the alphabetically later
// one, with a warning), minus anything data.snbt's disabled_sources names (a namespace or a chapter id). A pack chapter
// with the same id then replaces it, or, with merge: true, is laid over it: its fields replace the mod's, and its
// quests and quest links replace the mod's ones with the same id (remove: true on a quest drops it) or are added. A
// chapter file with remove: true drops the chapter, and one whose requires_mods names a mod that isn't installed is
// left out quietly. Groups and reward tables merge by id the same way, the pack's winning.
public final class BookMerger {
    private static final List<String> MERGED_LISTS = List.of("quests", "quest_links");

    // Whether a mod is installed, for requires_mods.
    private static final Predicate<String> MOD_LOADED = modId -> ModList.get() == null || ModList.get().isLoaded(modId);

    private BookMerger() {}

    private static boolean modsPresent(CompoundTag chapter) {
        for (Tag entry : chapter.getListOrEmpty("requires_mods")) {
            if (!MOD_LOADED.test(entry.asString().orElse(""))) {
                return false;
            }
        }
        return true;
    }

    // The sources merged but not yet decoded.
    public record Merged(CompoundTag settings, List<RawFile> groups, List<RawFile> chapters, List<RawFile> tables,
            Map<String, Map<String, String>> lang) {
        public Merged(CompoundTag settings, List<RawFile> groups, List<RawFile> chapters, List<RawFile> tables) {
            this(settings, groups, chapters, tables, Map.of());
        }
    }

    public static Merged merge(BookSources sources, Consumer<String> warnings) {
        CompoundTag settings = sources.settings().map(CompoundTag::copy).orElseGet(CompoundTag::new);
        List<String> disabled = settings.getList("disabled_sources").map(list -> list.stream()
                .map(tag -> tag.asString().orElse("")).filter(s -> !s.isEmpty()).toList()).orElse(List.of());

        // Mod chapters, by id.
        Map<String, RawFile> chapters = new LinkedHashMap<>();
        List<RawFile> mod = new ArrayList<>(sources.modChapters());
        mod.sort(Comparator.comparing((RawFile file) -> file.source().orElse("")).thenComparing(RawFile::origin));
        for (RawFile file : mod) {
            if (disabled.contains(file.source().orElse("")) || disabled.contains(file.id()) || !modsPresent(file.tag())) {
                continue;
            }
            if (file.tag().getBooleanOr("remove", false)) {
                chapters.remove(file.id());
                continue;
            }
            RawFile previous = chapters.put(file.id(), file);
            if (previous != null && !previous.source().equals(file.source())) {
                warnings.accept("Chapter " + file.id() + " is shipped by both " + previous.source().orElse("?") + " and "
                        + file.source().orElse("?") + "; using " + file.origin());
            }
        }

        // The pack's chapters replace or merge over them.
        for (RawFile file : sources.packChapters()) {
            if (!modsPresent(file.tag())) {
                continue;
            }
            if (file.tag().getBooleanOr("remove", false)) {
                chapters.remove(file.id());
                continue;
            }
            RawFile base = chapters.get(file.id());
            if (base != null && file.tag().getBooleanOr("merge", false)) {
                chapters.put(file.id(), new RawFile(file.id(), Optional.empty(), overlay(base.tag(), file.tag()), file.origin()));
            } else {
                CompoundTag tag = file.tag().copy();
                tag.remove("merge");
                chapters.put(file.id(), new RawFile(file.id(), Optional.empty(), tag, file.origin()));
            }
        }

        // Groups: mod files first, then the pack's.
        Map<String, RawFile> groups = new LinkedHashMap<>();
        List<RawFile> groupFiles = new ArrayList<>(sources.groupFiles());
        groupFiles.sort(Comparator.comparing((RawFile file) -> file.source().isEmpty()).thenComparing(file -> file.source().orElse("")));
        for (RawFile file : groupFiles) {
            if (file.source().isPresent() && disabled.contains(file.source().get())) {
                continue;
            }
            for (Tag entry : file.tag().getListOrEmpty("groups")) {
                if (entry instanceof CompoundTag group) {
                    String id = group.getStringOr("id", "");
                    if (group.getBooleanOr("remove", false)) {
                        groups.remove(id);
                    } else {
                        groups.put(id, new RawFile(id, file.source(), group, file.origin()));
                    }
                }
            }
        }
        // Reward tables: the mods', minus disabled ones, replaced (or removed) by the pack's.
        Map<String, RawFile> tables = new LinkedHashMap<>();
        List<RawFile> modTables = new ArrayList<>(sources.modTables());
        modTables.sort(Comparator.comparing((RawFile file) -> file.source().orElse("")).thenComparing(RawFile::origin));
        for (RawFile file : modTables) {
            if (disabled.contains(file.source().orElse("")) || disabled.contains(file.id())) {
                continue;
            }
            if (file.tag().getBooleanOr("remove", false)) {
                tables.remove(file.id());
            } else {
                tables.put(file.id(), file);
            }
        }
        for (RawFile file : sources.packTables()) {
            if (file.tag().getBooleanOr("remove", false)) {
                tables.remove(file.id());
            } else {
                tables.put(file.id(), new RawFile(file.id(), Optional.empty(), file.tag(), file.origin()));
            }
        }
        // The pack's translations: strings only.
        Map<String, Map<String, String>> lang = new LinkedHashMap<>();
        sources.packLang().forEach((locale, tag) -> {
            Map<String, String> entries = new LinkedHashMap<>();
            for (String key : tag.keySet()) {
                Optional<String> text = tag.get(key).asString();
                if (text.isPresent()) {
                    entries.put(key, text.get());
                } else {
                    warnings.accept("lang/" + locale + ".snbt: " + key + " isn't text");
                }
            }
            lang.put(locale, entries);
        });
        return new Merged(settings, List.copyOf(groups.values()), List.copyOf(chapters.values()), List.copyOf(tables.values()), lang);
    }

    // A pack chapter laid over a mod chapter (merge: true).
    static CompoundTag overlay(CompoundTag base, CompoundTag over) {
        CompoundTag result = base.copy();
        for (String key : over.keySet()) {
            if (key.equals("merge")) {
                continue;
            }
            Tag value = over.get(key);
            if (MERGED_LISTS.contains(key) && value instanceof ListTag overList) {
                result.put(key, mergeById(base.getListOrEmpty(key), overList));
            } else if (value != null) {
                result.put(key, value.copy());
            }
        }
        return result;
    }

    private static ListTag mergeById(ListTag base, ListTag over) {
        Map<String, Tag> byId = new LinkedHashMap<>();
        int unnamed = 0;
        for (Tag entry : base) {
            String id = entry instanceof CompoundTag c ? c.getStringOr("id", "") : "";
            byId.put(id.isEmpty() ? "\0" + unnamed++ : id, entry.copy());
        }
        for (Tag entry : over) {
            if (entry instanceof CompoundTag c && QuestRef.isValidId(c.getStringOr("id", ""))) {
                String id = c.getStringOr("id", "");
                if (c.getBooleanOr("remove", false)) {
                    byId.remove(id);
                } else {
                    byId.put(id, c.copy());
                }
            }
        }
        ListTag result = new ListTag();
        result.addAll(byId.values());
        return result;
    }

    // Decodes the merged files. A chapter or group that won't decode is reported and left out, so one bad file doesn't
    // take the whole book down.
    public static QuestBook decode(Merged merged, DynamicOps<Tag> ops, Consumer<String> warnings) {
        BookSettings settings = BookSettings.CODEC.parse(ops, merged.settings())
                .resultOrPartial(error -> warnings.accept("data.snbt: " + error)).orElse(BookSettings.DEFAULT);
        List<ChapterGroup> groups = new ArrayList<>();
        for (RawFile file : merged.groups()) {
            ChapterGroup.CODEC.parse(ops, file.tag()).resultOrPartial(error -> warnings.accept(file.origin() + ", group "
                    + file.id() + ": " + error)).ifPresent(groups::add);
        }
        List<Chapter> chapters = new ArrayList<>();
        for (RawFile file : merged.chapters()) {
            if (!QuestRef.isValidId(file.id())) {
                warnings.accept(file.origin() + ": \"" + file.id() + "\" isn't a valid chapter id (lowercase letters, digits and _)");
                continue;
            }
            DataResult<Chapter> result = Chapter.CODEC.parse(ops, file.tag());
            Optional<Chapter> chapter = result.result();
            if (chapter.isEmpty()) {
                warnings.accept(file.origin() + ": " + result.error().map(DataResult.Error::message).orElse("unreadable"));
                continue;
            }
            chapters.add(chapter.get().built(file.id(), file.source()));
        }
        List<RewardTable> tables = new ArrayList<>();
        for (RawFile file : merged.tables()) {
            if (!QuestRef.isValidId(file.id())) {
                warnings.accept(file.origin() + ": \"" + file.id() + "\" isn't a valid reward table id (lowercase letters, digits and _)");
                continue;
            }
            DataResult<RewardTable> result = RewardTable.CODEC.parse(ops, file.tag());
            if (result.result().isEmpty()) {
                warnings.accept(file.origin() + ": " + result.error().map(DataResult.Error::message).orElse("unreadable"));
                continue;
            }
            tables.add(result.result().get().withId(file.id(), file.source()));
        }
        return QuestBook.build(settings, groups, chapters, tables, merged.lang(), warnings);
    }
}

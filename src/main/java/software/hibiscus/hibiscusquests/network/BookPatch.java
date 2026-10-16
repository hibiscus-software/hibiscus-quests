/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.book.BookCodecs;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookSources.PackChange;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;

// The parts of the book an edit changed, as they are in the new merged book: chapters and reward tables by id (empty
// when they're gone), and the settings and groups when they changed. Sent to clients instead of the whole book after
// an edit; a client applies it to the book it has, if that's the book the patch was made from.
public record BookPatch(Optional<BookSettings> settings, Optional<List<ChapterGroup>> groups, Map<String, Optional<Chapter>> chapters,
        Map<String, Optional<RewardTable>> tables) {
    private record ChapterEntry(String id, Optional<Chapter> chapter) {
        static final Codec<ChapterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(ChapterEntry::id),
                Chapter.NETWORK_CODEC.optionalFieldOf("chapter").forGetter(ChapterEntry::chapter))
                .apply(i, ChapterEntry::new));
    }

    private record TableEntry(String id, Optional<RewardTable> table) {
        static final Codec<TableEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(TableEntry::id),
                RewardTable.NETWORK_CODEC.optionalFieldOf("table").forGetter(TableEntry::table))
                .apply(i, TableEntry::new));
    }

    public static final Codec<BookPatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            BookSettings.CODEC.optionalFieldOf("settings").forGetter(BookPatch::settings),
            ChapterGroup.CODEC.listOf().optionalFieldOf("groups").forGetter(BookPatch::groups),
            BookCodecs.list("chapters", ChapterEntry.CODEC).forGetter(patch -> patch.chapters.entrySet().stream()
                    .map(entry -> new ChapterEntry(entry.getKey(), entry.getValue())).toList()),
            BookCodecs.list("tables", TableEntry.CODEC).forGetter(patch -> patch.tables.entrySet().stream()
                    .map(entry -> new TableEntry(entry.getKey(), entry.getValue())).toList()))
            .apply(i, (settings, groups, chapters, tables) -> {
                Map<String, Optional<Chapter>> chapterMap = new LinkedHashMap<>();
                chapters.forEach(entry -> chapterMap.put(entry.id(), entry.chapter()));
                Map<String, Optional<RewardTable>> tableMap = new LinkedHashMap<>();
                tables.forEach(entry -> tableMap.put(entry.id(), entry.table()));
                return new BookPatch(settings, groups, chapterMap, tableMap);
            }));

    // What changed in a book after an edit: the touched parts, as they are in the new (merged) book. A chapter the
    // pack removed may still be there if a mod ships one with the same id; it's sent as it is now.
    public static BookPatch of(QuestBook book, PackChange change) {
        Map<String, Optional<Chapter>> chapters = new LinkedHashMap<>();
        change.chapters().keySet().forEach(id -> chapters.put(id, book.sourceChapter(id)));
        Map<String, Optional<RewardTable>> tables = new LinkedHashMap<>();
        change.tables().keySet().forEach(id -> tables.put(id, book.table(id)));
        return new BookPatch(change.settings().map(tag -> book.settings()), change.groups().map(tag -> book.groups()), chapters, tables);
    }

    // The book with the patch applied.
    public QuestBook apply(QuestBook book) {
        BookSettings newSettings = settings.orElse(book.settings());
        List<ChapterGroup> newGroups = groups.orElse(book.groups());
        List<Chapter> newChapters = new ArrayList<>();
        for (Chapter chapter : book.sourceChapters()) {
            if (!chapters.containsKey(chapter.id())) {
                newChapters.add(chapter);
            }
        }
        chapters.values().forEach(chapter -> chapter.ifPresent(newChapters::add));
        List<RewardTable> newTables = new ArrayList<>();
        for (RewardTable table : book.tables()) {
            if (!tables.containsKey(table.id())) {
                newTables.add(table);
            }
        }
        tables.values().forEach(table -> table.ifPresent(newTables::add));
        return QuestBook.build(newSettings, newGroups, newChapters, newTables, warning -> {});
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookSources.PackChange;
import software.hibiscus.hibiscusquests.book.BookSources;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasImage;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.CanvasElements.QuestLink;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.ChapterInfo;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;

// Applies the editor's edits to the pack's book files. A batch is checked whole before anything is written: every
// value must decode as its file would (so the files stay readable), ids must be valid, and only the pack's own chapters
// and tables can change (a mod's chapter has to be overridden first, which copies it into the pack). Then the touched
// files are written (a deleted chapter or table goes to .trash/), and the book is rebuilt and patched on every client.
public final class BookEditor {
    private BookEditor() {}

    // How a batch went: refused with the reason, or saved (with any new warnings about the book).
    public record Result(boolean ok, List<Component> messages) {
        public static Result failed(Component message) {
            return new Result(false, List.of(message));
        }
    }

    // An edit that can't be applied; the batch stops there and nothing is written.
    private static final class Refused extends Exception {
        private final transient Component reason;

        Refused(Component reason) {
            super(reason.getString(), null, false, false);
            this.reason = reason;
        }
    }

    // The book as the batch changes it: chapters, tables, groups and settings that were touched.
    private static final class Working {
        private final QuestBook book;
        private final Map<String, Optional<Chapter>> chapters = new LinkedHashMap<>();
        private final Map<String, Optional<RewardTable>> tables = new LinkedHashMap<>();
        private Optional<List<ChapterGroup>> groups = Optional.empty();
        private Optional<BookSettings> settings = Optional.empty();
        // The pack's translation files the batch changed, by locale (copies of the files' tags).
        private final Map<String, CompoundTag> lang = new LinkedHashMap<>();

        Working(QuestBook book) {
            this.book = book;
        }

        Optional<Chapter> chapter(String id) {
            return chapters.containsKey(id) ? chapters.get(id) : book.sourceChapter(id);
        }

        // A chapter the batch may change: it exists and it's the pack's own.
        Chapter editable(String id) throws Refused {
            Chapter chapter = chapter(id).orElseThrow(() -> new Refused(Component.translatable("hibiscusquests.edit.error.no_chapter", id)));
            if (chapter.source().isPresent()) {
                throw new Refused(Component.translatable("hibiscusquests.edit.error.read_only", id, chapter.source().get()));
            }
            return chapter;
        }

        Optional<RewardTable> table(String id) {
            return tables.containsKey(id) ? tables.get(id) : book.table(id);
        }

        CompoundTag lang(String locale, BookSources sources) {
            return lang.computeIfAbsent(locale, key -> sources.packLang().getOrDefault(key, new CompoundTag()).copy());
        }
    }

    // Edits from a player: only from one who's in edit mode (EditingMode), and logged when they're saved.
    public static Result applyFrom(ServerPlayer player, List<BookEdit> edits) {
        if (!EditingMode.isEditing(player)) {
            return Result.failed(Component.translatable("hibiscusquests.edit.error.not_editing"));
        }
        Result result = apply(player.level().getServer(), edits);
        if (result.ok()) {
            HibiscusQuests.LOGGER.info("{} edited the quest book: {}", player.getPlainTextName(), describe(edits));
        }
        return result;
    }

    public static Result apply(MinecraftServer server, List<BookEdit> edits) {
        if (edits.isEmpty()) {
            return new Result(true, List.of());
        }
        DynamicOps<Tag> ops = BookManager.ops(server);
        Working working = new Working(BookManager.book());
        try {
            for (BookEdit edit : edits) {
                apply(working, edit, ops);
            }
        } catch (Refused refused) {
            return Result.failed(refused.reason);
        }

        // The files' new tags.
        Map<String, Optional<CompoundTag>> chapterTags = new LinkedHashMap<>();
        Map<String, Optional<CompoundTag>> tableTags = new LinkedHashMap<>();
        Optional<CompoundTag> groupsTag = Optional.empty();
        Optional<CompoundTag> settingsTag = Optional.empty();
        Map<String, Optional<CompoundTag>> langTags = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Optional<Chapter>> entry : working.chapters.entrySet()) {
                chapterTags.put(entry.getKey(), entry.getValue().map(chapter -> PackFiles.encode(chapter, ops)));
            }
            for (Map.Entry<String, Optional<RewardTable>> entry : working.tables.entrySet()) {
                tableTags.put(entry.getKey(), entry.getValue().map(table -> PackFiles.encode(table, ops)));
            }
            if (working.groups.isPresent()) {
                groupsTag = Optional.of((CompoundTag) ChapterGroup.FILE_CODEC.encodeStart(ops, working.groups.get()).getOrThrow());
            }
            if (working.settings.isPresent()) {
                settingsTag = Optional.of((CompoundTag) BookSettings.CODEC.encodeStart(ops, working.settings.get()).getOrThrow());
            }
            working.lang.forEach((locale, tag) -> langTags.put(locale, tag.isEmpty() ? Optional.empty() : Optional.of(tag)));
        } catch (RuntimeException e) {
            HibiscusQuests.LOGGER.error("Couldn't encode an edit to the quest book", e);
            return Result.failed(Component.translatable("hibiscusquests.edit.error.encode", String.valueOf(e.getMessage())));
        }

        // Write them.
        Path directory = BookManager.packDirectory(server);
        try {
            for (Map.Entry<String, Optional<CompoundTag>> entry : chapterTags.entrySet()) {
                if (entry.getValue().isPresent()) {
                    PackFiles.writeChapterTag(directory, entry.getKey(), entry.getValue().get());
                } else {
                    PackFiles.trashChapter(directory, entry.getKey());
                }
            }
            for (Map.Entry<String, Optional<CompoundTag>> entry : tableTags.entrySet()) {
                if (entry.getValue().isPresent()) {
                    PackFiles.writeTableTag(directory, entry.getKey(), entry.getValue().get());
                } else {
                    PackFiles.trashTable(directory, entry.getKey());
                }
            }
            if (groupsTag.isPresent()) {
                PackFiles.writeGroupsTag(directory, groupsTag.get());
            }
            if (settingsTag.isPresent()) {
                PackFiles.writeSettingsTag(directory, settingsTag.get());
            }
            for (Map.Entry<String, Optional<CompoundTag>> entry : langTags.entrySet()) {
                if (entry.getValue().isPresent()) {
                    PackFiles.writeLangTag(directory, entry.getKey(), entry.getValue().get());
                } else {
                    PackFiles.trashLang(directory, entry.getKey());
                }
            }
        } catch (IOException e) {
            HibiscusQuests.LOGGER.error("Couldn't write the quest book's files", e);
            return Result.failed(Component.translatable("hibiscusquests.edit.error.write", String.valueOf(e.getMessage())));
        }

        List<String> warnings = BookManager.onPackEdited(server, new PackChange(chapterTags, groupsTag, settingsTag, tableTags, langTags));
        return new Result(true, warnings.stream().map(warning -> (Component) Component.literal(warning)).toList());
    }

    private static void apply(Working working, BookEdit edit, DynamicOps<Tag> ops) throws Refused {
        switch (edit) {
            case BookEdit.Quest e -> {
                Chapter chapter = working.editable(e.chapter());
                checkId(e.id());
                List<Quest> quests = new ArrayList<>(chapter.quests());
                int index = indexOf(quests.stream().map(Quest::id).toList(), e.id());
                if (e.value().isPresent()) {
                    CompoundTag tag = e.value().get().copy();
                    tag.putString("id", e.id());
                    Quest quest = decode(Quest.CODEC, tag, ops, "quest " + e.chapter() + "/" + e.id());
                    if (index >= 0) {
                        quests.set(index, quest);
                    } else {
                        quests.add(quest);
                    }
                } else if (index >= 0) {
                    quests.remove(index);
                }
                working.chapters.put(chapter.id(), Optional.of(chapter.withQuests(quests)));
            }
            case BookEdit.Link e -> {
                Chapter chapter = working.editable(e.chapter());
                checkId(e.id());
                List<QuestLink> links = new ArrayList<>(chapter.links());
                int index = indexOf(links.stream().map(QuestLink::id).toList(), e.id());
                if (e.value().isPresent()) {
                    CompoundTag tag = e.value().get().copy();
                    tag.putString("id", e.id());
                    QuestLink link = decode(QuestLink.CODEC, tag, ops, "quest link " + e.chapter() + "/" + e.id());
                    if (QuestRef.parseRelative(link.quest(), chapter.id()).isEmpty()) {
                        throw new Refused(Component.translatable("hibiscusquests.edit.error.bad_link", link.quest()));
                    }
                    if (index >= 0) {
                        links.set(index, link);
                    } else {
                        links.add(link);
                    }
                } else if (index >= 0) {
                    links.remove(index);
                }
                working.chapters.put(chapter.id(), Optional.of(new Chapter(chapter.id(), chapter.info(), chapter.quests(), links,
                        chapter.images(), chapter.labels(), chapter.source())));
            }
            case BookEdit.Images e -> {
                Chapter chapter = working.editable(e.chapter());
                List<CanvasImage> images = decode(CanvasImage.CODEC.listOf(), e.value(), ops, "images of " + e.chapter());
                working.chapters.put(chapter.id(), Optional.of(new Chapter(chapter.id(), chapter.info(), chapter.quests(), chapter.links(),
                        images, chapter.labels(), chapter.source())));
            }
            case BookEdit.Labels e -> {
                Chapter chapter = working.editable(e.chapter());
                List<CanvasLabel> labels = decode(CanvasLabel.CODEC.listOf(), e.value(), ops, "labels of " + e.chapter());
                working.chapters.put(chapter.id(), Optional.of(new Chapter(chapter.id(), chapter.info(), chapter.quests(), chapter.links(),
                        chapter.images(), labels, chapter.source())));
            }
            case BookEdit.ChapterInfo e -> {
                if (e.value().isEmpty()) {
                    working.editable(e.chapter());
                    working.chapters.put(e.chapter(), Optional.empty());
                    return;
                }
                ChapterInfo info = decode(ChapterInfo.MAP_CODEC.codec(), e.value().get(), ops, "chapter " + e.chapter());
                Optional<Chapter> existing = working.chapter(e.chapter());
                if (existing.isPresent()) {
                    Chapter chapter = working.editable(e.chapter());
                    working.chapters.put(chapter.id(), Optional.of(new Chapter(chapter.id(), info, chapter.quests(), chapter.links(),
                            chapter.images(), chapter.labels(), chapter.source())));
                } else {
                    checkId(e.chapter());
                    working.chapters.put(e.chapter(), Optional.of(new Chapter(e.chapter(), info, List.of(), List.of(), List.of(), List.of(),
                            Optional.empty())));
                }
            }
            case BookEdit.Groups e -> {
                List<ChapterGroup> groups = decode(ChapterGroup.CODEC.listOf(), e.value(), ops, "chapter groups");
                Set<String> ids = new HashSet<>();
                for (ChapterGroup group : groups) {
                    if (!ids.add(group.id())) {
                        throw new Refused(Component.translatable("hibiscusquests.edit.error.duplicate", group.id()));
                    }
                }
                working.groups = Optional.of(groups);
            }
            case BookEdit.Settings e -> working.settings = Optional.of(decode(BookSettings.CODEC, e.value(), ops, "book settings"));
            case BookEdit.Table e -> {
                checkId(e.id());
                if (e.value().isPresent()) {
                    Optional<RewardTable> existing = working.table(e.id());
                    if (existing.isPresent() && existing.get().source().isPresent()) {
                        throw new Refused(Component.translatable("hibiscusquests.edit.error.read_only_table", e.id(), existing.get().source().get()));
                    }
                    RewardTable table = decode(RewardTable.CODEC, e.value().get(), ops, "reward table " + e.id());
                    working.tables.put(e.id(), Optional.of(table.withId(e.id(), Optional.empty())));
                } else {
                    Optional<RewardTable> existing = working.table(e.id());
                    if (existing.isEmpty() || existing.get().source().isPresent()) {
                        throw new Refused(Component.translatable("hibiscusquests.edit.error.no_table", e.id()));
                    }
                    working.tables.put(e.id(), Optional.empty());
                }
            }
            case BookEdit.Lang e -> {
                if (!PackFiles.isLocale(e.locale())) {
                    throw new Refused(Component.translatable("hibiscusquests.edit.error.bad_locale", e.locale()));
                }
                if (!e.key().matches("[a-z0-9_.-]+")) {
                    throw new Refused(Component.translatable("hibiscusquests.edit.error.bad_key", e.key()));
                }
                CompoundTag lang = working.lang(e.locale(), BookManager.packSources());
                if (e.text().isPresent()) {
                    lang.putString(e.key(), e.text().get());
                } else {
                    lang.remove(e.key());
                }
            }
            case BookEdit.Override e -> {
                Chapter chapter = working.chapter(e.chapter())
                        .orElseThrow(() -> new Refused(Component.translatable("hibiscusquests.edit.error.no_chapter", e.chapter())));
                if (chapter.source().isEmpty()) {
                    throw new Refused(Component.translatable("hibiscusquests.edit.error.already_pack", e.chapter()));
                }
                working.chapters.put(chapter.id(), Optional.of(chapter.withSource(Optional.empty())));
            }
        }
    }

    private static void checkId(String id) throws Refused {
        if (!QuestRef.isValidId(id)) {
            throw new Refused(Component.translatable("hibiscusquests.edit.error.bad_id", id));
        }
    }

    private static int indexOf(List<String> ids, String id) {
        return ids.indexOf(id);
    }

    private static <T> T decode(Codec<T> codec, Tag tag, DynamicOps<Tag> ops, String what) throws Refused {
        DataResult<T> result = codec.parse(ops, tag);
        if (result.result().isEmpty()) {
            String message = result.error().map(DataResult.Error::message).orElse("unreadable");
            throw new Refused(Component.translatable("hibiscusquests.edit.error.invalid", what, message));
        }
        return result.result().get();
    }

    // For the log: what a batch touched.
    public static String describe(List<BookEdit> edits) {
        return edits.stream().map(BookEdit::target).distinct().collect(Collectors.joining(", "));
    }
}

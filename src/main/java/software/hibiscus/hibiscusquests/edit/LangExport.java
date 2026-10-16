/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.ChapterInfo;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;

// /hibiscusquests export_lang <locale>: makes the pack translatable. Every literal text in the pack's own files
// (chapter, quest, task and group titles, subtitles, descriptions, labels, command and toast rewards' text, the book's
// title and lock message, reward tables' titles and crate names) moves to the pack's lang/ files under a readable key
// (quests.<chapter>.<quest>.title...), and the file gets {translate:<key>} in its place. The text goes into the
// default locale and the one asked for, so translators start from it; keys already in the pack get an entry in the new
// locale too. Mods' chapters are left alone (they're translated in their own assets/<namespace>/lang/).
public final class LangExport {
    private static final String PREFIX = "quests.";

    // The edits, and how many texts there are in the locale's file afterwards.
    public record Result(List<BookEdit> edits, int texts) {}

    private final QuestBook book;
    private final String locale;
    private final DynamicOps<Tag> ops;
    private final List<BookEdit> edits = new ArrayList<>();
    private final Map<String, String> added = new LinkedHashMap<>();

    private LangExport(QuestBook book, String locale, DynamicOps<Tag> ops) {
        this.book = book;
        this.locale = locale;
        this.ops = ops;
    }

    public static Result edits(MinecraftServer server, String locale) {
        LangExport export = new LangExport(BookManager.book(), locale, BookManager.ops(server));
        export.run();
        return new Result(export.edits, export.added.size());
    }

    private void run() {
        for (Chapter chapter : book.sourceChapters()) {
            if (chapter.source().isPresent()) {
                continue;
            }
            String base = PREFIX + chapter.id();
            CompoundTag info = encode(ChapterInfo.MAP_CODEC.codec(), chapter.info());
            if (text(info, "title", base + ".title") | text(info, "subtitle", base + ".subtitle")) {
                edits.add(new BookEdit.ChapterInfo(chapter.id(), Optional.of(info)));
            }
            for (Quest quest : chapter.quests()) {
                CompoundTag tag = encode(Quest.CODEC, quest);
                String questBase = base + "." + quest.id();
                boolean changed = text(tag, "title", questBase + ".title") | text(tag, "subtitle", questBase + ".subtitle")
                        | description(tag, questBase + ".description");
                changed |= listTexts(tag.getListOrEmpty("tasks"), questBase + ".task", "title");
                for (Tag entry : tag.getListOrEmpty("rewards")) {
                    if (entry instanceof CompoundTag reward) {
                        String rewardBase = questBase + ".reward." + reward.getStringOr("id", "reward");
                        changed |= text(reward, "title", rewardBase + ".title") | text(reward, "message", rewardBase + ".message");
                    }
                }
                if (changed) {
                    edits.add(new BookEdit.Quest(chapter.id(), quest.id(), Optional.of(tag)));
                }
            }
            ListTag labels = (ListTag) encodeTag(CanvasLabel.CODEC.listOf(), chapter.labels());
            boolean labelsChanged = false;
            for (int n = 0; n < labels.size(); n++) {
                if (labels.get(n) instanceof CompoundTag label) {
                    labelsChanged |= text(label, "text", base + ".label." + n);
                }
            }
            if (labelsChanged) {
                edits.add(new BookEdit.Labels(chapter.id(), labels));
            }
        }
        ListTag groups = (ListTag) encodeTag(ChapterGroup.CODEC.listOf(), book.groups());
        boolean groupsChanged = false;
        for (Tag entry : groups) {
            if (entry instanceof CompoundTag group) {
                groupsChanged |= text(group, "title", PREFIX + "group." + group.getStringOr("id", "group") + ".title");
            }
        }
        if (groupsChanged) {
            edits.add(new BookEdit.Groups(groups));
        }
        CompoundTag settings = encode(BookSettings.CODEC, book.settings());
        if (text(settings, "title", PREFIX + "book.title") | text(settings, "lock_message", PREFIX + "book.lock_message")) {
            edits.add(new BookEdit.Settings(settings));
        }
        for (RewardTable table : book.tables()) {
            if (table.source().isPresent()) {
                continue;
            }
            CompoundTag tag = encode(RewardTable.CODEC, table);
            tag.remove("id");
            boolean changed = text(tag, "title", PREFIX + "table." + table.id() + ".title");
            Optional<CompoundTag> crate = tag.getCompound("loot_crate");
            if (crate.isPresent()) {
                changed |= text(crate.get(), "name", PREFIX + "table." + table.id() + ".crate");
            }
            if (changed) {
                edits.add(new BookEdit.Table(table.id(), Optional.of(tag)));
            }
        }
        added.forEach((key, value) -> {
            edits.add(new BookEdit.Lang(locale, key, Optional.of(value)));
            if (!locale.equals(BookText.DEFAULT_LOCALE) && book.translation(BookText.DEFAULT_LOCALE, key).isEmpty()) {
                edits.add(new BookEdit.Lang(BookText.DEFAULT_LOCALE, key, Optional.of(value)));
            }
        });
    }

    private <T> Tag encodeTag(Codec<T> codec, T value) {
        return codec.encodeStart(ops, value).getOrThrow();
    }

    private <T> CompoundTag encode(Codec<T> codec, T value) {
        return (CompoundTag) encodeTag(codec, value);
    }

    // One text field: a literal moves to the lang file under key and the field becomes {translate:key}; a key the pack
    // already translates gets an entry in this locale if it hasn't one. Returns whether the field changed.
    private boolean text(CompoundTag tag, String field, String key) {
        Optional<String> value = tag.getString(field);
        if (value.isEmpty() || value.get().isBlank()) {
            return false;
        }
        return move(value.get(), key, replacement -> tag.putString(field, replacement));
    }

    private boolean move(String value, String key, Consumer<String> replace) {
        Optional<String> existing = BookText.keyOf(value);
        if (existing.isPresent()) {
            if (isPackKey(existing.get()) && book.translation(locale, existing.get()).isEmpty()) {
                added.put(existing.get(), anyTranslation(existing.get()));
            }
            return false;
        }
        added.put(key, value);
        replace.accept("{translate:" + key + "}");
        return true;
    }

    // A description: its lines, as one text (pages and all), unless it's already a single key.
    private boolean description(CompoundTag tag, String key) {
        ListTag lines = tag.getListOrEmpty("description");
        if (lines.isEmpty()) {
            Optional<String> single = tag.getString("description");
            if (single.isEmpty()) {
                return false;
            }
            lines = new ListTag();
            lines.add(StringTag.valueOf(single.get()));
        }
        List<String> text = new ArrayList<>();
        lines.forEach(line -> text.add(line.asString().orElse("")));
        if (text.stream().allMatch(String::isBlank)) {
            return false;
        }
        String joined = String.join("\n", text);
        return move(text.size() == 1 ? text.getFirst() : joined, key, replacement -> {
            ListTag replaced = new ListTag();
            replaced.add(StringTag.valueOf(replacement));
            tag.put("description", replaced);
        });
    }

    // A text field in each compound of a list (task titles), keyed by the entry's id.
    private boolean listTexts(ListTag list, String base, String field) {
        boolean changed = false;
        for (Tag entry : list) {
            if (entry instanceof CompoundTag compound) {
                changed |= text(compound, field, base + "." + compound.getStringOr("id", "task") + "." + field);
            }
        }
        return changed;
    }

    private boolean isPackKey(String key) {
        return book.lang().values().stream().anyMatch(entries -> entries.containsKey(key));
    }

    private String anyTranslation(String key) {
        return book.translation(BookText.DEFAULT_LOCALE, key)
                .orElseGet(() -> book.lang().values().stream().map(entries -> entries.get(key)).filter(v -> v != null).findFirst().orElse(""));
    }
}

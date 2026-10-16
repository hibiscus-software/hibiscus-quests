/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import software.hibiscus.hibiscuslib.util.SnbtFiles;

// /hibiscusquests export_chapter <chapter> <namespace>: a chapter as a mod ships it, in
// <server>/hibiscusquests_exports/, ready to copy into the mod's src/main/resources/:
//   data/<namespace>/hibiscusquests/chapters/<chapter>.snbt
//   data/<namespace>/hibiscusquests/chapter_groups.snbt      its group, if it has one
//   data/<namespace>/hibiscusquests/reward_tables/<id>.snbt  the tables its rewards roll
//   assets/<namespace>/lang/<locale>.json                    the pack translations it uses, as the mod's lang files
public final class ChapterExport {
    private ChapterExport() {}

    // What was written: the files, relative to the exports folder.
    public record Result(List<String> files) {}

    public static Result export(MinecraftServer server, String chapterId, String namespace) throws IOException {
        QuestBook book = BookManager.book();
        Chapter chapter = book.sourceChapter(chapterId).orElseThrow(() -> new IOException("There's no chapter " + chapterId));
        DynamicOps<Tag> ops = BookManager.ops(server);
        Path exports = PackArchive.exportsDirectory(server);
        Path data = exports.resolve("data").resolve(namespace).resolve("hibiscusquests");
        List<String> written = new ArrayList<>();

        CompoundTag chapterTag = PackFiles.encode(chapter.withSource(Optional.empty()), ops);
        write(exports, data.resolve(PackFiles.CHAPTERS).resolve(chapterId + ".snbt"), chapterTag, written);

        Optional<ChapterGroup> group = book.groups().stream().filter(g -> g.id().equals(chapter.info().group())).findFirst();
        if (group.isPresent()) {
            CompoundTag groups = (CompoundTag) ChapterGroup.FILE_CODEC.encodeStart(ops, List.of(group.get())).getOrThrow();
            write(exports, data.resolve(PackFiles.GROUPS), groups, written);
        }

        TreeSet<String> tables = new TreeSet<>();
        for (Quest quest : chapter.quests()) {
            for (QuestReward reward : quest.rewards()) {
                if (reward.reward() instanceof UsesRewardTable user) {
                    tables.add(user.table());
                }
            }
        }
        List<CompoundTag> tableTags = new ArrayList<>();
        for (String id : tables) {
            Optional<RewardTable> table = book.table(id);
            if (table.isPresent()) {
                CompoundTag tag = PackFiles.encode(table.get(), ops);
                tableTags.add(tag);
                write(exports, data.resolve(PackFiles.TABLES).resolve(id + ".snbt"), tag, written);
            }
        }

        // The pack translations the chapter (and its group and tables) use, per locale.
        TreeSet<String> keys = new TreeSet<>();
        collectKeys(chapterTag, book, keys);
        group.ifPresent(g -> collectKeys((CompoundTag) ChapterGroup.CODEC.encodeStart(ops, g).getOrThrow(), book, keys));
        tableTags.forEach(tag -> collectKeys(tag, book, keys));
        for (Map.Entry<String, Map<String, String>> locale : book.lang().entrySet()) {
            Map<String, String> entries = new TreeMap<>();
            for (String key : keys) {
                String text = locale.getValue().get(key);
                if (text != null) {
                    entries.put(key, text);
                }
            }
            if (entries.isEmpty()) {
                continue;
            }
            JsonObject json = new JsonObject();
            entries.forEach(json::addProperty);
            Path file = exports.resolve("assets").resolve(namespace).resolve("lang").resolve(locale.getKey() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
            written.add(exports.relativize(file).toString().replace('\\', '/'));
        }
        return new Result(written);
    }

    private static void write(Path exports, Path file, CompoundTag tag, List<String> written) throws IOException {
        SnbtFiles.write(file, tag);
        written.add(exports.relativize(file).toString().replace('\\', '/'));
    }

    // Every string in a tag that's a key the pack translates.
    private static void collectKeys(Tag tag, QuestBook book, TreeSet<String> keys) {
        if (tag instanceof CompoundTag compound) {
            compound.keySet().forEach(key -> collectKeys(compound.get(key), book, keys));
        } else if (tag instanceof ListTag list) {
            list.forEach(entry -> collectKeys(entry, book, keys));
        } else {
            tag.asString().flatMap(BookText::keyOf).filter(key -> book.lang().values().stream().anyMatch(entries -> entries.containsKey(key)))
                    .ifPresent(keys::add);
        }
    }
}

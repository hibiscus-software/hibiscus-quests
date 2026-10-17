/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.team;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.tick;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestStatus;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookMerger;
import software.hibiscus.hibiscusquests.book.BookSources;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.ProgressStorage;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// The book's files and their merging, saving and reloading the pack's files and progress, the book's network form, and
// the public API's queries and progress pushes.
final class BookGameTests {
    private BookGameTests() {}

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseCompoundFully(text);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static DynamicOps<Tag> ops(GameTestHelper helper) {
        return BookManager.ops(helper.getLevel().getServer());
    }

    // Chapters shipped as data (the demo pack, data/hibiscusquests/hibiscusquests/) are in the book, with their source.
    static void dataChaptersLoad(GameTestHelper helper) {
        QuestBook book = BookManager.book();
        Optional<Chapter> items = book.chapter("demo_items");
        check(helper, items.isPresent(), "the demo pack's demo_items chapter is in the book");
        check(helper, items.get().source().equals(Optional.of(HibiscusQuests.MODID)), "with the namespace that shipped it");
        check(helper, items.get().quests().size() == 3, "and its 3 quests");
        check(helper, book.groups().stream().anyMatch(group -> group.id().equals("demo")), "the demo pack's groups are loaded");
        check(helper, book.groupTree().parent("demo_outdoors").equals("demo"), "with the nested one under its parent");
        check(helper, items.get().quest("iron").get().tasks().getFirst().id().equals("iron"), "task ids from the file are kept");
        check(helper, book.chapter("test_logic").get().quest("a").get().tasks().getFirst().id().equals("task_1"),
                "tasks without an id get task_1, task_2...");
        helper.succeed();
    }

    // A pack file replaces a mod chapter; with merge: true it's laid over it quest by quest; disabled_sources hides a
    // namespace; remove: true drops a chapter.
    static void packOverridesModChapter(GameTestHelper helper) {
        RawFile mod = new RawFile("ores", Optional.of("somemod"), snbt("""
                {title: "Ores", quests: [{id: "copper", title: "Copper", tasks: [{type: "checkmark"}]},
                {id: "tin", title: "Tin", tasks: [{type: "checkmark"}]}]}"""), "somemod:ores");
        RawFile other = new RawFile("magic", Optional.of("othermod"), snbt("{title: \"Magic\"}"), "othermod:magic");
        RawFile gone = new RawFile("old", Optional.of("somemod"), snbt("{title: \"Old\"}"), "somemod:old");
        RawFile mergeOver = new RawFile("ores", Optional.empty(), snbt("""
                {merge: true, subtitle: "Restyled", quests: [{id: "tin", title: "Tin, renamed"}, {id: "zinc", title: "Zinc"},
                {id: "copper", remove: true}]}"""), "pack:ores");
        RawFile removeOld = new RawFile("old", Optional.empty(), snbt("{remove: true}"), "pack:old");
        List<String> warnings = new ArrayList<>();

        BookSources merged = new BookSources(Optional.of(snbt("{disabled_sources: [\"othermod\"]}")), List.of(),
                List.of(mergeOver, removeOld), List.of(mod, other, gone));
        QuestBook book = BookMerger.decode(BookMerger.merge(merged, warnings::add), ops(helper), warnings::add);
        Chapter ores = book.chapter("ores").orElse(null);
        check(helper, ores != null && ores.info().title().equals("Ores") && ores.info().subtitle().equals("Restyled"),
                "merge keeps the mod's fields and takes the pack's");
        check(helper, ores.quest("copper").isEmpty(), "remove: true drops a quest");
        check(helper, ores.quest("tin").map(q -> q.title().equals("Tin, renamed")).orElse(false), "a pack quest replaces the mod's");
        check(helper, ores.quest("zinc").isPresent(), "a new pack quest is added");
        check(helper, ores.source().isEmpty(), "the merged chapter is the pack's now");
        check(helper, book.chapter("magic").isEmpty(), "disabled_sources hides othermod");
        check(helper, book.chapter("old").isEmpty(), "remove: true drops a chapter");

        RawFile replace = new RawFile("ores", Optional.empty(), snbt("{title: \"Pack ores\", quests: [{id: \"iron\"}]}"), "pack:ores");
        QuestBook replaced = BookMerger.decode(BookMerger.merge(new BookSources(Optional.empty(), List.of(), List.of(replace),
                List.of(mod)), warnings::add), ops(helper), warnings::add);
        Chapter packOres = replaced.chapter("ores").get();
        check(helper, packOres.info().title().equals("Pack ores") && packOres.quests().size() == 1 && packOres.quest("iron").isPresent(),
                "without merge, the pack chapter replaces the mod's entirely");
        helper.succeed();
    }

    // The pack's files come back exactly as they were written, and one that won't parse is reported, not fatal.
    static void packFilesRoundTrip(GameTestHelper helper) throws IOException {
        Path dir = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("hibiscusquests_test").resolve(UUID.randomUUID().toString());
        DynamicOps<Tag> ops = ops(helper);
        QuestBook book = BookManager.book();
        for (String id : List.of("demo_items", "demo_other", "test_rewards")) {
            PackFiles.writeChapter(dir, book.chapter(id).get().withSource(Optional.empty()), ops);
        }
        PackFiles.writeSettings(dir, book.settings(), ops);
        PackFiles.writeGroups(dir, book.groups(), ops);
        Files.writeString(dir.resolve(PackFiles.CHAPTERS).resolve("broken.snbt"), "{title: \"unclosed", StandardCharsets.UTF_8);

        List<String> warnings = new ArrayList<>();
        BookSources read = PackFiles.read(dir, warnings::add);
        QuestBook reloaded = BookMerger.decode(BookMerger.merge(read, warnings::add), ops, warnings::add);
        check(helper, warnings.stream().anyMatch(w -> w.contains("broken.snbt")), "the broken file is reported");
        for (String id : List.of("demo_items", "demo_other", "test_rewards")) {
            CompoundTag before = PackFiles.encode(book.chapter(id).get(), ops);
            CompoundTag after = PackFiles.encode(reloaded.chapter(id).orElseThrow(), ops);
            check(helper, before.equals(after), "chapter " + id + " should survive a write and read unchanged");
        }
        check(helper, reloaded.groups().size() == book.groups().size(), "the groups survive too");
        helper.succeed();
    }

    // Progress survives a save and load; a progress file that won't load is left alone, never overwritten.
    static void progressRoundTrip(GameTestHelper helper) throws IOException {
        ServerPlayer player = join(helper);
        tick(player, "test_logic", "a");
        tick(player, "test_rewards", "shared");
        ProgressManager.claim(player, ref("test_rewards", "shared"), "apple");
        TeamProgress progress = ProgressManager.progressOf(player);
        Path dir = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("hibiscusquests_test").resolve(UUID.randomUUID().toString());
        ProgressStorage storage = new ProgressStorage(dir);
        check(helper, storage.write(progress), "the progress is written");
        TeamProgress loaded = new ProgressStorage(dir).load(progress.teamId()).orElseThrow();
        check(helper, loaded.save().equals(progress.save()), "and reads back unchanged");
        check(helper, loaded.state(ref("test_rewards", "shared")).isClaimed("apple", false, player.getUUID()), "claims included");

        UUID brokenId = UUID.randomUUID();
        ProgressStorage broken = new ProgressStorage(dir);
        Files.writeString(broken.file(brokenId), "{team: \"" + brokenId + "\", quests: {", StandardCharsets.UTF_8);
        check(helper, broken.load(brokenId).isEmpty() && broken.isBroken(brokenId), "a broken file loads as empty and is marked broken");
        check(helper, !broken.write(new TeamProgress(brokenId)), "and isn't written over");
        check(helper, Files.readString(broken.file(brokenId)).endsWith("quests: {"), "so its contents are still there to fix");
        leave(helper, player);
        helper.succeed();
    }

    // The book's network form decodes to the same book, and the same book always has the same hash.
    static void syncRoundTrip(GameTestHelper helper) throws IOException {
        DynamicOps<Tag> ops = ops(helper);
        QuestBook book = BookManager.book();
        byte[] bytes = QuestSync.encode(book, ops);
        QuestBook decoded = QuestSync.decode(bytes, ops);
        check(helper, decoded.chapters().size() == book.chapters().size() && decoded.questCount() == book.questCount(), "same chapters and quests");
        check(helper, decoded.chapter("demo_items").get().source().equals(book.chapter("demo_items").get().source()), "sources survive");
        check(helper, decoded.table("treasure").flatMap(RewardTable::source).equals(book.table("treasure").flatMap(RewardTable::source)),
                "tables' too");
        check(helper, QuestSync.hash(QuestSync.encode(decoded, ops)).equals(QuestSync.hash(bytes)), "the hash is stable");
        check(helper, QuestSync.bookHash().equals(QuestSync.hash(bytes)), "and matches what the server announces");
        helper.succeed();
    }

    // QuestsApi: queries, and progress pushed by another mod.
    static void api(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        UUID teamId = QuestsApi.get().teamIdOf(player);
        check(helper, teamId.equals(team(player)), "teamIdOf agrees with the progress owner");
        check(helper, !QuestsApi.get().addProgress(player, ref("demo_items", "gold"), "gold", 1), "progress on a locked consume task is ignored");
        check(helper, QuestsApi.get().addProgress(player, ref("demo_items", "iron"), "iron", 2), "progress on a tracked task is added");
        QuestStatus status = QuestsApi.get().progress(teamId, ref("demo_items", "iron"));
        check(helper, status.task("iron") == 2 && Math.abs(status.fraction() - 0.5) < 1e-9, "progress reports 2 of 4");
        check(helper, QuestsApi.get().complete(player, ref("demo_items", "iron")), "complete() finds the quest");
        check(helper, QuestsApi.get().isCompleted(teamId, ref("demo_items", "iron")), "and completes it");
        ChapterStatus chapter = QuestsApi.get().chapterProgress(teamId, "demo_items");
        check(helper, chapter.completed() == 1 && chapter.total() == 3, "the chapter is 1 of 3");
        check(helper, QuestsApi.get().book().quest(ref("demo_items", "iron")).isPresent(), "the book view finds quests");
        leave(helper, player);
        helper.succeed();
    }
}

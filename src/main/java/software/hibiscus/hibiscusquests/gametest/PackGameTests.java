/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.team;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookSources.PackChange;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.ChapterExport;
import software.hibiscus.hibiscusquests.book.PackArchive;
import software.hibiscus.hibiscusquests.book.PackFiles;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.edit.BookEdit;
import software.hibiscus.hibiscusquests.edit.BookEditor;
import software.hibiscus.hibiscusquests.edit.LangExport;
import software.hibiscus.hibiscusquests.network.BookPatch;
import software.hibiscus.hibiscusquests.progress.ProgressManager;

// The pack as a whole: translations (export_lang, the pack's lang files, translated text on the server and in
// patches), export and import, export_chapter, cleanup and emergency. In the editor's batch, on a pack in a temporary
// directory.
final class PackGameTests {
    private static final String CHAPTER = "pack_test";

    private PackGameTests() {}

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseCompoundFully(text);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    private static BookEditor.Result apply(MinecraftServer server, BookEdit... edits) {
        return BookEditor.apply(server, List.of(edits));
    }

    private static void withTempPack(GameTestHelper helper, Body body) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path pack = Files.createTempDirectory("hibiscusquests-pack");
        BookManager.usePackDirectory(server, pack);
        try {
            body.run(server, pack);
        } finally {
            BookManager.usePackDirectory(server, null);
            delete(pack);
        }
    }

    @FunctionalInterface
    private interface Body {
        void run(MinecraftServer server, Path pack) throws Exception;
    }

    private static void delete(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // a temporary file; the OS cleans it up eventually
        }
    }

    private static void makeChapter(MinecraftServer server) {
        BookEditor.Result made = apply(server,
                new BookEdit.ChapterInfo(CHAPTER, Optional.of(snbt("{title: \"Pack Test\"}"))),
                new BookEdit.Quest(CHAPTER, "first", Optional.of(snbt("{title: \"Hello\", description: [\"Line one\", \"---\", \"Page two\"], "
                        + "tasks: [{id: \"tick\", type: \"checkmark\", title: \"Do it\"}], rewards: [{id: \"roll\", type: \"random\", table: \"pack_table\"}]}"))),
                new BookEdit.Table("pack_table", Optional.of(snbt("{title: \"Pack Table\", entries: [{type: \"xp\", amount: 1}]}"))),
                new BookEdit.Labels(CHAPTER, snbt("{l: [{text: \"Heading\"}]}").getListOrEmpty("l")));
        if (!made.ok()) {
            throw new IllegalStateException("couldn't make the test chapter: " + made.messages());
        }
    }

    // export_lang: literal text moves to lang files under keys; the book shows the translations (on the server, in the
    // default locale), descriptions keep their pages, and edits and patches carry translations.
    static void lang(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            makeChapter(server);
            QuestBook before = BookManager.book();
            LangExport.Result export = LangExport.edits(server, "de_de");
            BookEditor.Result result = BookEditor.apply(server, export.edits());
            check(helper, result.ok() && export.texts() >= 5, "export_lang moves the texts: " + result.messages());
            String titleKey = "quests." + CHAPTER + ".first.title";
            Quest quest = BookManager.book().quest(new QuestRef(CHAPTER, "first")).orElseThrow();
            check(helper, quest.title().equals("{translate:" + titleKey + "}"), "the quest's title is a key now: " + quest.title());
            check(helper, BookManager.book().translation("de_de", titleKey).equals(Optional.of("Hello"))
                    && BookManager.book().translation(BookText.DEFAULT_LOCALE, titleKey).equals(Optional.of("Hello")),
                    "its text is in the locale asked for and the default one");
            check(helper, BookText.component(quest.title()).getString().equals("Hello"), "and the book shows it");
            check(helper, quest.display().pages().size() == 2 && quest.display().pages().getFirst().equals(List.of("Line one")),
                    "a translated description keeps its pages");
            check(helper, quest.tasks().getFirst().title().map(BookText::component).map(c -> c.getString()).orElse("").equals("Do it"),
                    "task titles too");
            check(helper, Files.isRegularFile(pack.resolve(PackFiles.LANG).resolve("de_de.snbt")), "in lang/de_de.snbt");
            check(helper, LangExport.edits(server, "de_de").edits().isEmpty(), "a second export finds nothing new");

            check(helper, apply(server, new BookEdit.Lang("de_de", titleKey, Optional.of("Hallo"))).ok()
                    && BookManager.book().translation("de_de", titleKey).equals(Optional.of("Hallo")), "a translation can be edited");
            check(helper, BookText.component(quest.title()).getString().equals("Hello"), "the server still speaks the default locale");
            check(helper, !apply(server, new BookEdit.Lang("Not A Locale", titleKey, Optional.of("x"))).ok(), "locales are checked");

            LangExport.Result french = LangExport.edits(server, "fr_fr");
            check(helper, BookEditor.apply(server, french.edits()).ok()
                    && BookManager.book().translation("fr_fr", titleKey).equals(Optional.of("Hello")), "a new locale starts from the default text");

            PackChange change = new PackChange(Map.of(CHAPTER, Optional.of(new CompoundTag())), Optional.empty(), Optional.empty(), Map.of(),
                    Map.of("de_de", Optional.of(new CompoundTag())));
            BookPatch patch = BookPatch.CODEC.parse(BookManager.ops(server),
                    BookPatch.CODEC.encodeStart(BookManager.ops(server), BookPatch.of(BookManager.book(), change)).getOrThrow()).getOrThrow();
            QuestBook patched = patch.apply(before);
            check(helper, patched.translation("de_de", titleKey).equals(Optional.of("Hallo")), "patches carry the translations");
        });
        helper.succeed();
    }

    // export zips the book; import puts a zip back (keeping the book it replaced), and refuses one with a bad file.
    static void exportImport(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path exports = PackArchive.exportsDirectory(server);
        withTempPack(helper, (srv, pack) -> {
            makeChapter(server);
            Path zip = PackArchive.export(server, "gametest");
            check(helper, Files.isRegularFile(zip), "export writes a zip");
            check(helper, apply(server, new BookEdit.ChapterInfo(CHAPTER, Optional.empty())).ok()
                    && BookManager.book().chapter(CHAPTER).isEmpty(), "the chapter is deleted");
            PackArchive.ImportResult imported = PackArchive.importZip(server, zip.getFileName().toString());
            check(helper, imported.problems().isEmpty() && imported.files() >= 2, "import takes the zip: " + imported.problems());
            check(helper, BookManager.book().chapter(CHAPTER).isPresent() && BookManager.book().table("pack_table").isPresent(),
                    "and the book has the chapter back");
            check(helper, Files.isRegularFile(imported.backup()), "after zipping the book it replaced");

            Path bad = exports.resolve("gametest-bad.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(bad))) {
                out.putNextEntry(new ZipEntry("../escape.snbt"));
                out.write("{}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("chapters/broken.snbt"));
                out.write("{this isn't snbt".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            int chapters = BookManager.book().chapters().size();
            PackArchive.ImportResult refused = PackArchive.importZip(server, bad.getFileName().toString());
            check(helper, !refused.problems().isEmpty() && BookManager.book().chapters().size() == chapters, "a zip with a broken file changes nothing");
            check(helper, !Files.exists(pack.getParent().resolve("escape.snbt")) && !Files.exists(pack.resolve("../escape.snbt").normalize()),
                    "and nothing escapes the pack");
            check(helper, !PackArchive.importZip(server, "../outside.zip").problems().isEmpty(), "only zips in the exports folder");
        });
        cleanExports(exports);
        helper.succeed();
    }

    private static void cleanExports(Path exports) {
        if (!Files.isDirectory(exports)) {
            return;
        }
        try (Stream<Path> files = Files.list(exports)) {
            files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("gametest") || name.startsWith("before-import") || name.equals("data") || name.equals("assets");
            }).forEach(PackGameTests::delete);
        } catch (IOException ignored) {
            // leftovers in a test server's folder
        }
    }

    // export_chapter: the chapter, its tables and its translations as a mod ships them.
    static void exportChapter(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path exports = PackArchive.exportsDirectory(server);
        withTempPack(helper, (srv, pack) -> {
            makeChapter(server);
            BookEditor.apply(server, LangExport.edits(server, BookText.DEFAULT_LOCALE).edits());
            ChapterExport.Result result = ChapterExport.export(server, CHAPTER, "mymod");
            Path data = exports.resolve("data/mymod/hibiscusquests");
            check(helper, Files.isRegularFile(data.resolve("chapters/" + CHAPTER + ".snbt")), "the chapter's file");
            check(helper, Files.isRegularFile(data.resolve("reward_tables/pack_table.snbt")), "the table its reward rolls");
            Path lang = exports.resolve("assets/mymod/lang/en_us.json");
            check(helper, Files.isRegularFile(lang) && Files.readString(lang).contains("quests." + CHAPTER + ".first.title"),
                    "and its translations as the mod's lang file");
            check(helper, result.files().size() >= 3, "listed for the command: " + result.files());
        });
        cleanExports(exports);
        helper.succeed();
    }

    // cleanup: progress for quests no longer in the book is counted, then dropped with confirm.
    static void cleanup(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            makeChapter(server);
            ServerPlayer player = join(helper);
            Quest quest = BookManager.book().quest(new QuestRef(CHAPTER, "first")).orElseThrow();
            ProgressManager.forceComplete(team(player), quest, player);
            check(helper, apply(server, new BookEdit.ChapterInfo(CHAPTER, Optional.empty())).ok(), "the quest leaves the book");
            check(helper, ProgressManager.progressOf(player).state(quest.ref()) != null, "its progress is kept");
            ProgressManager.CleanupReport dry = ProgressManager.cleanup(false);
            check(helper, dry.quests() >= 1 && ProgressManager.progressOf(player).state(quest.ref()) != null, "cleanup counts it first");
            ProgressManager.CleanupReport done = ProgressManager.cleanup(true);
            check(helper, done.quests() >= 1 && ProgressManager.progressOf(player).state(quest.ref()) == null, "and drops it with confirm");
            leave(helper, player);
        });
        helper.succeed();
    }

    // emergency: the book's emergency items, then a wait.
    static void emergency(GameTestHelper helper) throws Exception {
        withTempPack(helper, (server, pack) -> {
            check(helper, apply(server, new BookEdit.Settings(snbt("{emergency_items: [{id: \"minecraft:bread\", count: 3}], emergency_cooldown: 600}"))).ok(),
                    "the book has emergency items");
            ServerPlayer player = join(helper);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "hibiscusquests emergency");
            check(helper, player.getInventory().countItem(Items.BREAD) == 3, "emergency gives them");
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "hibiscusquests emergency");
            check(helper, player.getInventory().countItem(Items.BREAD) == 3, "but not again before the cooldown");
            leave(helper, player);
        });
        helper.succeed();
    }
}

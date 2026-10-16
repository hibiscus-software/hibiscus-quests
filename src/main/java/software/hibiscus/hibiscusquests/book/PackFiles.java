/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import software.hibiscus.hibiscuslib.util.SnbtFiles;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.book.BookSources.RawFile;
import org.jspecify.annotations.Nullable;

// The pack's own book files, the ones the editor reads and writes:
//   config/hibiscusquests/            (or <world>/serverconfig/hibiscusquests/ when that exists)
//     data.snbt                       book settings
//     chapter_groups.snbt             {groups: [...]}
//     chapters/<chapter_id>.snbt      one chapter each
//     reward_tables/<table_id>.snbt   one reward table each
// Files are SNBT, written by Hibiscus Lib's SnbtFiles (sorted keys, atomic, with a .bak).
public final class PackFiles {
    public static final String SETTINGS = "data.snbt";
    public static final String GROUPS = "chapter_groups.snbt";
    public static final String CHAPTERS = "chapters";
    public static final String TABLES = "reward_tables";
    // Where deleted chapters and tables go (the editor never deletes a file outright).
    public static final String TRASH = ".trash";
    private static final String SUFFIX = ".snbt";

    private PackFiles() {}

    // The pack directory for a server: a world's serverconfig/hibiscusquests/ wins over config/hibiscusquests/.
    public static Path directory(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(HibiscusQuests.MODID);
        return Files.isDirectory(world) ? world : FMLPaths.CONFIGDIR.get().resolve(HibiscusQuests.MODID);
    }

    // Reads every file in a pack directory as raw tags. A file that won't parse is reported and left out.
    public static BookSources read(Path directory, Consumer<String> warnings) {
        Optional<CompoundTag> settings = readFile(directory.resolve(SETTINGS), warnings);
        List<RawFile> groups = new ArrayList<>();
        readFile(directory.resolve(GROUPS), warnings)
                .ifPresent(tag -> groups.add(new RawFile(GROUPS, Optional.empty(), tag, display(directory, GROUPS))));
        List<RawFile> chapters = readDirectory(directory, CHAPTERS, warnings);
        List<RawFile> tables = readDirectory(directory, TABLES, warnings);
        return new BookSources(settings, groups, chapters, List.of(), tables, List.of());
    }

    // Every .snbt file in a subdirectory, by name; ids come from the files' id fields, else their names.
    private static List<RawFile> readDirectory(Path directory, String subdirectory, Consumer<String> warnings) {
        List<RawFile> found = new ArrayList<>();
        Path dir = directory.resolve(subdirectory);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).sorted().toList()) {
                    String name = file.getFileName().toString();
                    readFile(file, warnings).ifPresent(tag -> found.add(new RawFile(chapterId(tag, name), Optional.empty(), tag,
                            display(directory, subdirectory + "/" + name))));
                }
            } catch (IOException e) {
                warnings.accept("Couldn't list " + dir + ": " + e.getMessage());
            }
        }
        return found;
    }

    // A chapter's id: its id field, else its file name without .snbt.
    static String chapterId(CompoundTag tag, String fileName) {
        String id = tag.getStringOr("id", "");
        return id.isEmpty() ? fileName.substring(0, fileName.length() - SUFFIX.length()) : id;
    }

    private static Optional<CompoundTag> readFile(Path file, Consumer<String> warnings) {
        try {
            return SnbtFiles.read(file);
        } catch (IOException e) {
            warnings.accept(e.getMessage());
            return Optional.empty();
        }
    }

    private static String display(Path directory, String file) {
        return directory.getFileName() + "/" + file;
    }

    // ---- writing ----

    // A chapter as its file's tag, with its id first in mind for people reading it (keys are sorted on write anyway).
    public static CompoundTag encode(Chapter chapter, DynamicOps<Tag> ops) {
        Tag tag = Chapter.CODEC.encodeStart(ops, chapter).getOrThrow(message -> new IllegalStateException("Can't write chapter " + chapter.id() + ": " + message));
        CompoundTag compound = (CompoundTag) tag;
        compound.putString("id", chapter.id());
        return compound;
    }

    public static void writeChapter(Path directory, Chapter chapter, DynamicOps<Tag> ops) throws IOException {
        SnbtFiles.write(directory.resolve(CHAPTERS).resolve(chapter.id() + SUFFIX), encode(chapter, ops));
    }

    public static CompoundTag encode(RewardTable table, DynamicOps<Tag> ops) {
        Tag tag = RewardTable.CODEC.encodeStart(ops, table).getOrThrow(message -> new IllegalStateException("Can't write reward table " + table.id() + ": " + message));
        CompoundTag compound = (CompoundTag) tag;
        compound.putString("id", table.id());
        return compound;
    }

    public static void writeTable(Path directory, RewardTable table, DynamicOps<Tag> ops) throws IOException {
        SnbtFiles.write(directory.resolve(TABLES).resolve(table.id() + SUFFIX), encode(table, ops));
    }

    public static void writeSettings(Path directory, BookSettings settings, DynamicOps<Tag> ops) throws IOException {
        Tag tag = BookSettings.CODEC.encodeStart(ops, settings).getOrThrow(message -> new IllegalStateException("Can't write book settings: " + message));
        SnbtFiles.write(directory.resolve(SETTINGS), (CompoundTag) tag);
    }

    // ---- the editor's writes: file tags as they are, by id ----

    // Writes a chapter's file, chapters/<id>.snbt, and moves any other file that declares the same id to the trash
    // (a hand-written file named differently), so the id isn't in the pack twice.
    public static void writeChapterTag(Path directory, String id, CompoundTag tag) throws IOException {
        writeById(directory, CHAPTERS, id, tag);
    }

    public static void trashChapter(Path directory, String id) throws IOException {
        trashById(directory, CHAPTERS, id, null);
    }

    public static void writeTableTag(Path directory, String id, CompoundTag tag) throws IOException {
        writeById(directory, TABLES, id, tag);
    }

    public static void trashTable(Path directory, String id) throws IOException {
        trashById(directory, TABLES, id, null);
    }

    public static void writeSettingsTag(Path directory, CompoundTag tag) throws IOException {
        SnbtFiles.write(directory.resolve(SETTINGS), tag);
    }

    public static void writeGroupsTag(Path directory, CompoundTag tag) throws IOException {
        SnbtFiles.write(directory.resolve(GROUPS), tag);
    }

    private static void writeById(Path directory, String subdirectory, String id, CompoundTag tag) throws IOException {
        Path file = directory.resolve(subdirectory).resolve(id + SUFFIX);
        trashById(directory, subdirectory, id, file);
        CompoundTag written = tag.copy();
        written.putString("id", id);
        SnbtFiles.write(file, written);
    }

    // Moves every file in the subdirectory whose id is this one to .trash/<subdirectory>/, except keep.
    private static void trashById(Path directory, String subdirectory, String id, @Nullable Path keep) throws IOException {
        Path dir = directory.resolve(subdirectory);
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).toList()) {
                if (file.equals(keep)) {
                    continue;
                }
                String name = file.getFileName().toString();
                Optional<CompoundTag> tag;
                try {
                    tag = SnbtFiles.read(file);
                } catch (IOException e) {
                    tag = Optional.empty();
                }
                String fileId = tag.map(t -> chapterId(t, name)).orElse(name.substring(0, name.length() - SUFFIX.length()));
                if (fileId.equals(id)) {
                    matches.add(file);
                }
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        Path trash = directory.resolve(TRASH).resolve(subdirectory);
        Files.createDirectories(trash);
        String stamp = TRASH_STAMP.format(LocalDateTime.now());
        for (Path file : matches) {
            Files.move(file, trash.resolve(stamp + "-" + file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final DateTimeFormatter TRASH_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    public static void writeGroups(Path directory, List<ChapterGroup> groups, DynamicOps<Tag> ops) throws IOException {
        Tag tag = ChapterGroup.FILE_CODEC.encodeStart(ops, groups).getOrThrow(message -> new IllegalStateException("Can't write chapter groups: " + message));
        SnbtFiles.write(directory.resolve(GROUPS), (CompoundTag) tag);
    }
}

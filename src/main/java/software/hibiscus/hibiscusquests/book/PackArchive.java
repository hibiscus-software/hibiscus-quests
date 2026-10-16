/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.minecraft.server.MinecraftServer;

// /hibiscusquests export and import: the pack's book files as a zip in <server>/hibiscusquests_exports/, to move a
// book between worlds or servers or keep a copy. Import only takes the book's own files (data.snbt,
// chapter_groups.snbt, chapters/, reward_tables/, lang/), checks every one reads before anything changes, keeps a zip
// of the book it replaces, and moves the old files to .trash/ rather than deleting them.
public final class PackArchive {
    public static final String EXPORTS = "hibiscusquests_exports";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final List<String> DIRECTORIES = List.of(PackFiles.CHAPTERS, PackFiles.TABLES, PackFiles.LANG);
    private static final List<String> FILES = List.of(PackFiles.SETTINGS, PackFiles.GROUPS);
    private static final long MAX_ENTRY = 16L * 1024 * 1024;

    private PackArchive() {}

    public static Path exportsDirectory(MinecraftServer server) {
        return server.getServerDirectory().resolve(EXPORTS);
    }

    // The zips import can read: the .zip files in the exports folder.
    public static List<String> zips(MinecraftServer server) {
        Path dir = exportsDirectory(server);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".zip")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // Zips the pack's book files; returns the zip.
    public static Path export(MinecraftServer server, String prefix) throws IOException {
        Path pack = BookManager.packDirectory(server);
        Path dir = exportsDirectory(server);
        Files.createDirectories(dir);
        Path zip = dir.resolve(prefix + "-" + STAMP.format(LocalDateTime.now()) + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Path file : bookFiles(pack)) {
                out.putNextEntry(new ZipEntry(pack.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return zip;
    }

    // The book's own files in a pack directory (no backups, no trash).
    private static List<Path> bookFiles(Path pack) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String name : FILES) {
            if (Files.isRegularFile(pack.resolve(name))) {
                files.add(pack.resolve(name));
            }
        }
        for (String name : DIRECTORIES) {
            Path dir = pack.resolve(name);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> list = Files.list(dir)) {
                    list.filter(path -> path.getFileName().toString().endsWith(".snbt")).sorted().forEach(files::add);
                }
            }
        }
        return files;
    }

    // Whether a zip entry is one of the book's files, and a safe name (no climbing out of the pack).
    static boolean isBookEntry(String name) {
        if (name.contains("..") || name.startsWith("/") || name.contains("\\") || name.contains(":")) {
            return false;
        }
        if (FILES.contains(name)) {
            return true;
        }
        int slash = name.indexOf('/');
        return slash > 0 && DIRECTORIES.contains(name.substring(0, slash)) && name.indexOf('/', slash + 1) < 0
                && name.endsWith(".snbt") && name.length() > slash + 1 + ".snbt".length();
    }

    // What an import found: the files it took, and the problems that stopped it (empty when it went ahead).
    public record ImportResult(int files, List<String> problems, Path backup) {}

    // Replaces the pack's book files with a zip's, if every one of them reads. The current book is zipped first.
    public static ImportResult importZip(MinecraftServer server, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_.+-]+\\.zip")) {
            return new ImportResult(0, List.of("Not a zip in " + EXPORTS + "/: " + name), Path.of(""));
        }
        Path zip = exportsDirectory(server).resolve(name);
        if (!Files.isRegularFile(zip)) {
            return new ImportResult(0, List.of("There's no " + EXPORTS + "/" + name), Path.of(""));
        }
        Path staging = Files.createTempDirectory("hibiscusquests-import");
        try {
            int count = 0;
            List<String> skipped = new ArrayList<>();
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (!isBookEntry(entry.getName())) {
                        skipped.add(entry.getName());
                        continue;
                    }
                    Path target = staging.resolve(entry.getName());
                    Files.createDirectories(target.getParent());
                    copyLimited(in, target);
                    count++;
                }
            }
            if (count == 0) {
                return new ImportResult(0, List.of(name + " has none of the book's files"), Path.of(""));
            }
            List<String> problems = new ArrayList<>();
            PackFiles.read(staging, problems::add);
            if (!problems.isEmpty()) {
                return new ImportResult(0, problems, Path.of(""));
            }
            Path backup = export(server, "before-import");
            Path pack = BookManager.packDirectory(server);
            Path trash = pack.resolve(PackFiles.TRASH).resolve("import-" + STAMP.format(LocalDateTime.now()));
            for (Path file : bookFiles(pack)) {
                Path moved = trash.resolve(pack.relativize(file));
                Files.createDirectories(moved.getParent());
                Files.move(file, moved, StandardCopyOption.REPLACE_EXISTING);
            }
            for (Path file : bookFiles(staging)) {
                Path target = pack.resolve(staging.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
            BookManager.reload(server);
            return new ImportResult(count, List.of(), backup);
        } finally {
            try (Stream<Path> files = Files.walk(staging)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    // Copies an entry, refusing one too big to be a book file.
    private static void copyLimited(InputStream in, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                total += read;
                if (total > MAX_ENTRY) {
                    throw new IOException(target.getFileName() + " is too big to be a book file");
                }
                out.write(buffer, 0, read);
            }
        }
    }
}

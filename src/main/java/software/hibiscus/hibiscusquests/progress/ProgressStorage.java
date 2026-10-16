/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import software.hibiscus.hibiscuslib.util.SnbtFiles;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// Progress on disk: <world>/hibiscusquests/progress/<team id>.snbt, one file per team, written atomically with a
// .bak by SnbtFiles. A file that won't load is logged and remembered as broken, and is never written over (the team
// plays on with empty progress until it's fixed and the server restarts). A deleted team's file is archived, not
// deleted.
public final class ProgressStorage {
    private static final String SUFFIX = ".snbt";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path directory;
    private final Set<UUID> broken = new HashSet<>();

    public ProgressStorage(MinecraftServer server) {
        this(server.getWorldPath(LevelResource.ROOT).resolve(HibiscusQuests.MODID).resolve("progress"));
    }

    public ProgressStorage(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public Path file(UUID teamId) {
        return directory.resolve(teamId + SUFFIX);
    }

    public boolean isBroken(UUID teamId) {
        return broken.contains(teamId);
    }

    // A team's saved progress, if it has any. A file that won't read is logged, marked broken and treated as empty.
    public Optional<TeamProgress> load(UUID teamId) {
        Path file = file(teamId);
        try {
            Optional<CompoundTag> tag = SnbtFiles.read(file);
            if (tag.isEmpty()) {
                return Optional.empty();
            }
            TeamProgress progress = TeamProgress.load(tag.get());
            if (!progress.teamId().equals(teamId)) {
                throw new IOException("its team id " + progress.teamId() + " doesn't match the file name");
            }
            return Optional.of(progress);
        } catch (IOException | RuntimeException e) {
            broken.add(teamId);
            HibiscusQuests.LOGGER.error("Couldn't load quest progress {}; it will be left untouched until it's fixed: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    // Writes a team's progress, unless its file is broken; returns whether it was written.
    public boolean write(TeamProgress progress) throws IOException {
        if (broken.contains(progress.teamId())) {
            return false;
        }
        SnbtFiles.write(file(progress.teamId()), progress.save());
        return true;
    }

    // A deleted team: its progress goes to <team id>.archived-<time>.snbt and the live file (and backup) is removed.
    public Optional<Path> archive(TeamProgress progress) throws IOException {
        if (broken.contains(progress.teamId())) {
            return Optional.empty();
        }
        Path archived = directory.resolve(progress.teamId() + ".archived-" + LocalDateTime.now().format(STAMP) + SUFFIX);
        SnbtFiles.write(archived, progress.save());
        Path file = file(progress.teamId());
        Files.deleteIfExists(file);
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
        return Optional.of(archived);
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import software.hibiscus.hibiscuslib.util.SnbtFiles;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// What the book remembers between openings, for this player on this computer: the chapter last viewed, whether the
// chapter list is collapsed, which groups are folded, and each chapter's zoom and pan. Saved to
// <game dir>/hibiscusquests/book_ui.snbt when the book closes.
public final class BookUiState {
    // A chapter's view: zoom, and the map point (in grid pixels at zoom 1) at the canvas's centre.
    public record View(double zoom, double panX, double panY) {}

    private static boolean loaded;
    private static @Nullable String lastChapter;
    private static @Nullable Boolean panelCollapsed;
    private static boolean hudVisible = true;
    private static final Set<String> FOLDED_GROUPS = new HashSet<>();
    private static final Map<String, View> VIEWS = new HashMap<>();

    private BookUiState() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(HibiscusQuests.MODID).resolve("book_ui.snbt");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            CompoundTag tag = SnbtFiles.read(file()).orElse(null);
            if (tag == null) {
                return;
            }
            lastChapter = tag.getString("last_chapter").orElse(null);
            panelCollapsed = tag.getBoolean("panel_collapsed").orElse(null);
            hudVisible = tag.getBooleanOr("hud_visible", true);
            for (Tag group : tag.getListOrEmpty("folded_groups")) {
                group.asString().ifPresent(FOLDED_GROUPS::add);
            }
            CompoundTag views = tag.getCompoundOrEmpty("views");
            for (String chapter : views.keySet()) {
                CompoundTag view = views.getCompoundOrEmpty(chapter);
                VIEWS.put(chapter, new View(view.getDoubleOr("zoom", 1), view.getDoubleOr("x", 0), view.getDoubleOr("y", 0)));
            }
        } catch (IOException | RuntimeException e) {
            HibiscusQuests.LOGGER.debug("Ignoring the book's saved view: {}", e.getMessage());
        }
    }

    public static void save() {
        if (!loaded) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        if (lastChapter != null) {
            tag.putString("last_chapter", lastChapter);
        }
        if (panelCollapsed != null) {
            tag.putBoolean("panel_collapsed", panelCollapsed);
        }
        tag.putBoolean("hud_visible", hudVisible);
        ListTag groups = new ListTag();
        FOLDED_GROUPS.stream().sorted().forEach(group -> groups.add(StringTag.valueOf(group)));
        tag.put("folded_groups", groups);
        CompoundTag views = new CompoundTag();
        VIEWS.forEach((chapter, view) -> {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("zoom", view.zoom());
            entry.putDouble("x", view.panX());
            entry.putDouble("y", view.panY());
            views.put(chapter, entry);
        });
        tag.put("views", views);
        try {
            SnbtFiles.write(file(), tag);
        } catch (IOException e) {
            HibiscusQuests.LOGGER.debug("Couldn't save the book's view: {}", e.getMessage());
        }
    }

    public static Optional<String> lastChapter() {
        load();
        return Optional.ofNullable(lastChapter);
    }

    public static void setLastChapter(String chapter) {
        load();
        lastChapter = chapter;
    }

    public static boolean panelCollapsed() {
        load();
        return panelCollapsed != null ? panelCollapsed : QuestsClientConfig.get(QuestsClientConfig.PANEL_COLLAPSED);
    }

    public static void setPanelCollapsed(boolean collapsed) {
        load();
        panelCollapsed = collapsed;
    }

    // Whether the pinned quests show on screen (the book's top-bar toggle).
    public static boolean hudVisible() {
        load();
        return hudVisible;
    }

    public static void setHudVisible(boolean visible) {
        load();
        hudVisible = visible;
    }

    public static boolean isFolded(String group, boolean byDefault) {
        load();
        return FOLDED_GROUPS.contains(group) != byDefault;
    }

    // Folding is stored as "different from the group's default", so a pack can change the default.
    public static void setFolded(String group, boolean folded, boolean byDefault) {
        load();
        if (folded != byDefault) {
            FOLDED_GROUPS.add(group);
        } else {
            FOLDED_GROUPS.remove(group);
        }
    }

    public static Optional<View> view(String chapter) {
        load();
        return Optional.ofNullable(VIEWS.get(chapter));
    }

    public static void setView(String chapter, View view) {
        load();
        VIEWS.put(chapter, view);
    }
}

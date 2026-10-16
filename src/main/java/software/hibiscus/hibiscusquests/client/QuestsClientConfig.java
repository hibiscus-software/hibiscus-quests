/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

// Each player's own settings, config/hibiscusquests-client.toml.
public final class QuestsClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Where the pinned quests sit on screen.
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    public static final ModConfigSpec.BooleanValue TOASTS;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.DoubleValue ZOOM_SENSITIVITY;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue PANEL_COLLAPSED;
    public static final ModConfigSpec.EnumValue<Corner> HUD_CORNER;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;

    static {
        BUILDER.push("notifications");
        TOASTS = BUILDER
                .comment("Show toasts when a quest or chapter is completed and when a reward arrives by itself.")
                .define("toasts", true);
        SOUNDS = BUILDER
                .comment("Play sounds when a quest or chapter is completed and when a reward is claimed.")
                .define("sounds", true);
        BUILDER.pop();

        BUILDER.push("book");
        ZOOM_SENSITIVITY = BUILDER
                .comment("How far one step of the mouse wheel zooms the chapter map (1 = the normal step).")
                .defineInRange("zoomSensitivity", 1.0, 0.25, 4.0);
        REDUCED_MOTION = BUILDER
                .comment("Stop the book's animations: pulsing claimable quests and moving dashes on dependency lines.")
                .define("reducedMotion", false);
        PANEL_COLLAPSED = BUILDER
                .comment("Start with the chapter list collapsed to icons (the book remembers it once you change it).")
                .define("chapterListCollapsed", false);
        BUILDER.pop();

        BUILDER.push("hud");
        HUD_CORNER = BUILDER
                .comment("Where your pinned quests show on screen: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT.")
                .defineEnum("corner", Corner.TOP_LEFT);
        HUD_SCALE = BUILDER
                .comment("How big the pinned quests are drawn (1 = the GUI's size).")
                .defineInRange("scale", 1.0, 0.5, 2.0);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuestsClientConfig() {}

    // A value, or its default before the config has loaded.
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return SPEC.isLoaded() ? value.get() : value.getDefault();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}

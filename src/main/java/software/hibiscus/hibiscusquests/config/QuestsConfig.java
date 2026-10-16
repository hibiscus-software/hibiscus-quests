/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.config;

import java.util.function.Supplier;

import net.neoforged.neoforge.common.ModConfigSpec;
import software.hibiscus.hibiscusquests.book.ProgressMode;
import software.hibiscus.hibiscusquests.book.TeamChange;

// The server's settings, config/hibiscusquests-server.toml (a world's syncedconfig/hibiscusquests-server.toml overrides
// it). Registered as ModConfig.Type.SYNCED, 26.3's server config, so clients get a copy when they connect. The book's
// data.snbt can override the progress defaults for its own book.
public final class QuestsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<ProgressMode> PROGRESS_MODE;
    public static final ModConfigSpec.EnumValue<TeamChange> TEAM_CHANGE;
    public static final ModConfigSpec.IntValue ITEM_SCAN_INTERVAL;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_CHAPTERS;

    static {
        BUILDER.push("progress");
        PROGRESS_MODE = BUILDER
                .comment("Who quest progress belongs to: TEAM (the player's team: their party with Hibiscus Teams, else just them)",
                        "or PLAYER (every player on their own, even in a party). A book's progress_mode in data.snbt wins.")
                .defineEnum("mode", ProgressMode.TEAM);
        TEAM_CHANGE = BUILDER
                .comment("What happens to progress when a player changes team (TEAM mode): KEEP_WITH_TEAM (it stays with the team",
                        "they left), COPY_TO_NEW_TEAM (a team with no progress yet gets a copy of theirs) or MERGE (the team they",
                        "join gets both teams' progress). A book's team_change in data.snbt wins.")
                .defineEnum("teamChange", TeamChange.KEEP_WITH_TEAM);
        BUILDER.pop();

        BUILDER.push("tasks");
        ITEM_SCAN_INTERVAL = BUILDER
                .comment("Ticks between checks of a player's inventory for item tasks that detect items (20 = once a second). A",
                        "check only looks at the inventory when it has changed since the last one.")
                .defineInRange("itemScanInterval", 10, 1, 1200);
        BUILDER.pop();

        BUILDER.push("announcements");
        ANNOUNCE_CHAPTERS = BUILDER
                .comment("Tell the whole server in chat when a team completes a chapter.")
                .define("chapterCompletion", false);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuestsConfig() {}

    // A value, or its default before the config has loaded (gametests, very early calls).
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return safe(value::get, value.getDefault());
    }

    private static <T> T safe(Supplier<T> supplier, T fallback) {
        try {
            return SPEC.isLoaded() ? supplier.get() : fallback;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}

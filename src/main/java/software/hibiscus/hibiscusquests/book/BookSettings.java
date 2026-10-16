/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

// data.snbt: the book's own settings. progress_mode and team_change override the server config's defaults when set.
// background is a texture id or a "#rrggbb" colour; disabled_sources hides mod-provided content by namespace or chapter
// id. version is the file format, for migrations.
public record BookSettings(int version, String title, Optional<BookIcon> icon, String defaultShape, double defaultSize,
        Optional<String> background, Optional<Integer> accent, Optional<ProgressMode> progressMode,
        Optional<TeamChange> teamChange, boolean dropLootCrates, List<ItemStack> emergencyItems,
        Optional<Integer> emergencyCooldown, boolean pauseGame, String lockMessage, List<String> disabledSources) {
    public static final int CURRENT_VERSION = 1;

    public static final BookSettings DEFAULT = new BookSettings(CURRENT_VERSION, "", Optional.empty(), "circle", 1.0,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, List.of(), Optional.empty(), false,
            "", List.of());

    public static final Codec<BookSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(BookSettings::version),
            BookCodecs.text("title").forGetter(BookSettings::title),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(BookSettings::icon),
            Codec.STRING.optionalFieldOf("default_shape", "circle").forGetter(BookSettings::defaultShape),
            Codec.doubleRange(0.5, 3).optionalFieldOf("default_size", 1.0).forGetter(BookSettings::defaultSize),
            Codec.STRING.optionalFieldOf("background").forGetter(BookSettings::background),
            BookCodecs.COLOR.optionalFieldOf("accent").forGetter(BookSettings::accent),
            ProgressMode.CODEC.optionalFieldOf("progress_mode").forGetter(BookSettings::progressMode),
            TeamChange.CODEC.optionalFieldOf("team_change").forGetter(BookSettings::teamChange),
            Codec.BOOL.optionalFieldOf("drop_loot_crates", true).forGetter(BookSettings::dropLootCrates),
            BookCodecs.list("emergency_items", ItemStack.CODEC).forGetter(BookSettings::emergencyItems),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("emergency_cooldown").forGetter(BookSettings::emergencyCooldown),
            Codec.BOOL.optionalFieldOf("pause_game", false).forGetter(BookSettings::pauseGame),
            BookCodecs.text("lock_message").forGetter(BookSettings::lockMessage),
            BookCodecs.list("disabled_sources", Codec.STRING).forGetter(BookSettings::disabledSources))
            .apply(i, BookSettings::new));

    public BookSettings {
        emergencyItems = emergencyItems.stream().map(ItemStack::copy).toList();
        disabledSources = List.copyOf(disabledSources);
    }
}

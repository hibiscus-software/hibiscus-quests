/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

// The built-in demo data pack (src/main/resources/datapacks/demo): one chapter per task type, plus the chapters the
// gametests use. It ships in the jar but is off unless switched on with /datapack enable, except in the gametest and
// screenshot runs (-Dhibiscusquests.gametest=true), where it's always on. It's also the worked example of shipping
// chapters as data (data/<namespace>/hibiscusquests/).
final class QuestPacks {
    static final String GAMETEST_PROPERTY = "hibiscusquests.gametest";

    private QuestPacks() {}

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(QuestPacks::addPackFinders);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addPackFinders(HibiscusQuests.id("datapacks/demo"), PackType.SERVER_DATA,
                    Component.translatable("hibiscusquests.pack.demo"), PackSource.FEATURE, Boolean.getBoolean(GAMETEST_PROPERTY),
                    Pack.Position.TOP);
        }
    }
}

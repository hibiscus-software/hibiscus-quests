/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscuslib.button.ButtonBadge;
import software.hibiscus.hibiscuslib.button.ButtonSpec;
import software.hibiscus.hibiscuslib.button.HibiscusButtons;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.client.PinnedHud;
import software.hibiscus.hibiscusquests.client.QuestsClientConfig;
import software.hibiscus.hibiscusquests.client.screen.QuestBookScreen;
import software.hibiscus.hibiscusquests.item.QuestBookItem;
import software.hibiscus.hibiscusquests.reward.TableRewards;

// Hibiscus Quests' client entrypoint, the only way into client/ code (besides payload handlers, through ClientBridge).
// Registers the client config, the inventory button that opens the book (with its badge and an unbound "Open Quest Book"
// key mapping, which has to be registered during mod construction), and keeps the client's copy of the book and
// progress tidy across connections.
@Mod(value = HibiscusQuests.MODID, dist = Dist.CLIENT)
public class HibiscusQuestsClient {
    public HibiscusQuestsClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, QuestsClientConfig.SPEC, HibiscusQuests.MODID + "-client.toml");
        // Mods screen > Hibiscus Quests > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        HibiscusButtons.register(HibiscusQuests.id("book"), ButtonSpec.builder()
                .sprite(HibiscusQuests.id("button/quests"))
                .tooltip(Component.translatable("hibiscusquests.button.tooltip"))
                .onClick(QuestBookScreen::open)
                // Only on servers running Hibiscus Quests (they send the book on login).
                .visibleWhen(ClientQuestState::hasBook)
                // A gold count of the rewards waiting to be claimed.
                .badge(() -> ButtonBadge.count(ClientBook.unclaimedCount(), HibiscusTheme.current().color(HibiscusTheme.GOLD)))
                .order(110)
                .withKeyMapping()
                .build());

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientQuestState.clear());
        QuestBookItem.opener = QuestBookScreen::open;
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(HibiscusQuests.id("pinned_quests"), PinnedHud::render));
        TableRewards.ClientBookAccess.set(ClientQuestState::book);
    }
}

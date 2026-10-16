/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscusquests.client.ClientQuestState;

// Hibiscus Quests' client entrypoint, the only way into client/ code. For now it keeps the client's copy of the book
// and progress (ClientQuestState) tidy across connections; the book's screens hook in here.
@Mod(value = HibiscusQuests.MODID, dist = Dist.CLIENT)
public class HibiscusQuestsClient {
    public HibiscusQuestsClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientQuestState.clear());
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import software.hibiscus.hibiscuslib.api.team.event.PlayerJoinedTeamEvent;
import software.hibiscus.hibiscuslib.api.team.event.TeamDeletedEvent;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.DataChapters;
import software.hibiscus.hibiscusquests.command.QuestPermissions;
import software.hibiscus.hibiscusquests.command.QuestsCommand;
import software.hibiscus.hibiscusquests.config.QuestsConfig;
import software.hibiscus.hibiscusquests.edit.EditingMode;
import software.hibiscus.hibiscusquests.gametest.HibiscusQuestsGameTests;
import software.hibiscus.hibiscusquests.network.QuestNetwork;
import software.hibiscus.hibiscusquests.network.QuestSync;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestServiceImpl;

// Hibiscus Quests' common entrypoint (both sides). Must match the modId in META-INF/neoforge.mods.toml. Installs the
// API, registers the task and reward types, config, payloads and permission nodes, and hooks the server lifecycle,
// data-pack reloads, logins, ticks, commands and the Teams API's team events.
@Mod(HibiscusQuests.MODID)
public class HibiscusQuests {
    public static final String MODID = "hibiscusquests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HibiscusQuests(IEventBus modEventBus, ModContainer modContainer) {
        QuestsApi.install(QuestServiceImpl.INSTANCE);
        // SYNCED is 26.3's server config: loaded as the server starts (a world's syncedconfig/ copy wins)
        // and sent to clients.
        modContainer.registerConfig(ModConfig.Type.SYNCED, QuestsConfig.SPEC, MODID + "-server.toml");
        QuestPermissions.init();
        BuiltinTypes.register(modEventBus);
        QuestSounds.register(modEventBus);
        QuestItems.register(modEventBus);
        QuestNetwork.register(modEventBus);
        QuestPacks.register(modEventBus);
        HibiscusQuestsGameTests.register(modEventBus);

        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> event.addListener(DataChapters.ID, new DataChapters()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> BookManager.onDatapackSync(event));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            ProgressManager.start(event.getServer());
            BookManager.onServerStarting(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ProgressManager.stop());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            BookManager.onServerStopped();
            QuestSync.onServerStopped();
            EditingMode.onServerStopped();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            ProgressManager.tick(event.getServer());
            ActiveTasks.tick(event.getServer());
            QuestSync.flush(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(HibiscusQuests::onLogin);
        NeoForge.EVENT_BUS.addListener(HibiscusQuests::onLogout);
        NeoForge.EVENT_BUS.addListener(HibiscusQuests::onRespawn);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> QuestsCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerJoinedTeamEvent event) -> ProgressManager.onTeamChanged(event));
        NeoForge.EVENT_BUS.addListener((TeamDeletedEvent event) -> ProgressManager.onTeamDeleted(event));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestSync.onLogin(player);
            ProgressManager.onLogin(player);
            EditingMode.onLogin(player);
            giveBookOnFirstJoin(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QuestNetwork.forget(event.getEntity().getUUID());
        EditingMode.onLogout(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressManager.onLogout(player);
        }
    }

    // The Quest Book, once per player, when the server hands it out (giveBookOnFirstJoin).
    private static void giveBookOnFirstJoin(ServerPlayer player) {
        if (QuestsConfig.get(QuestsConfig.GIVE_BOOK) && !player.getData(QuestItems.BOOK_GIVEN)) {
            player.setData(QuestItems.BOOK_GIVEN, true);
            player.getInventory().placeItemBackInInventory(new ItemStack(QuestItems.QUEST_BOOK.get()), Prediction.SERVER_ONLY);
        }
    }

    // Respawning makes a new player entity in 26.3, so the tracked tasks move to it.
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ActiveTasks.clear(player);
            ActiveTasks.refresh(player);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.screenshots;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.QuestItems;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.client.screen.QuestBookScreen;
import software.hibiscus.hibiscusquests.item.LootCrateItem;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestTeams;

// Development only (src/screenshots, never in the mod jar): ./gradlew runScreenshotClient opens a flat creative world
// with the demo book (the built-in data pack, switched on for this run), sets up progress so every kind of node shows,
// puts each book screen state on screen at each GUI scale and window size the README promises, saves a screenshot of
// each to run/screenshotClient/screenshots/, and quits. The same idea as Hibiscus Lib's and Hibiscus Teams' harnesses;
// its pictures are what the layout is checked against and what the README shows.
@EventBusSubscriber(modid = HibiscusQuests.MODID, value = Dist.CLIENT)
public final class ScreenshotHarness {
    private static final boolean ENABLED = Boolean.getBoolean("hibiscusquests.screenshots");
    private static final String WORLD = "hibiscusquests_screenshots";
    private static final String TOUR = "demo_tour";
    private static final String WORLD_CHAPTER = "demo_world";

    private record Step(Runnable action, int waitTicks, @Nullable String shot) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean worldRequested;
    private static boolean planned;
    private static int wait;
    private static int inWorldTicks;
    private static int startupTicks;

    private ScreenshotHarness() {}

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            if (!worldRequested && minecraft.gui.screen() instanceof TitleScreen) {
                worldRequested = true;
                openWorld(minecraft);
            } else if (!worldRequested && minecraft.gui.screen() != null && ++startupTicks > 40) {
                // A first launch shows the accessibility onboarding (or another notice) before the title screen.
                minecraft.options.onboardAccessibility = false;
                minecraft.options.save();
                minecraft.gui.setScreen(new TitleScreen());
            }
            return;
        }
        if (++inWorldTicks < 100) {
            return;
        }
        if (!planned) {
            planned = true;
            // The harness's window may not have focus: don't open the pause menu over the shots.
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            plan(minecraft);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            HibiscusQuests.LOGGER.info("Screenshot harness finished");
            minecraft.stop();
            return;
        }
        if (step.shot() != null) {
            File dir = minecraft.gameDirectory;
            String name = step.shot() + ".png";
            Screenshot.grab(dir, name, minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> HibiscusQuests.LOGGER.info("Screenshot {}: {}", name, message.getString()));
        }
        step.action().run();
        wait = step.waitTicks();
    }

    private static void openWorld(Minecraft minecraft) {
        // A fresh world each run: opening an old one can stop on a notice (a mod's version changed, say) the harness
        // can't answer, and the pictures should be the same every time.
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(WORLD)) {
                access.deleteLevel();
            } catch (IOException e) {
                HibiscusQuests.LOGGER.warn("Screenshot harness: can't delete the old world", e);
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(26_3L, false, false),
                WorldPresets::createTestWorldDimensions, new TitleScreen());
    }

    private static void then(Runnable action, int waitTicks) {
        STEPS.add(new Step(action, waitTicks, null));
    }

    private static void shot(String name) {
        STEPS.add(new Step(() -> {}, 2, name));
    }

    // Runs something on the integrated server's thread (progress lives there), then waits for the sync.
    private static void onServer(Minecraft minecraft, Consumer<MinecraftServer> action) {
        then(() -> {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> action.accept(server));
            }
        }, 20);
    }

    private static @Nullable QuestBookScreen book(Minecraft minecraft) {
        return minecraft.gui.screen() instanceof QuestBookScreen screen ? screen : null;
    }

    // ---- demo progress (server thread) ----

    private static @Nullable Quest quest(String chapter, String id) {
        return BookManager.book().quest(new QuestRef(chapter, id)).orElse(null);
    }

    // Every state on the tour: Welcome done and claimed, Timber done with rewards waiting, Sticks and Stones half done,
    // Fire and the branches open, the rest locked; and the Iron quest the link shows, done with rewards waiting.
    private static void scene(MinecraftServer server, UUID self) {
        ServerPlayer player = server.getPlayerList().getPlayer(self);
        if (player == null) {
            return;
        }
        UUID team = QuestTeams.teamIdOf(player);
        for (Chapter chapter : BookManager.book().chapters()) {
            chapter.quests().forEach(quest -> ProgressManager.reset(team, quest));
        }
        complete(team, player, TOUR, "start");
        ProgressManager.claim(player, new QuestRef(TOUR, "start"), "bread");
        complete(team, player, TOUR, "logs");
        Quest tools = quest(TOUR, "tools");
        if (tools != null) {
            ProgressManager.setTaskProgress(team, tools, tools.task("sticks").orElseThrow(), 16, player);
            ProgressManager.setTaskProgress(team, tools, tools.task("cobble").orElseThrow(), 12, player);
        }
        complete(team, player, "demo_items", "iron");
        Quest gold = quest("demo_items", "gold");
        if (gold != null) {
            ProgressManager.setTaskProgress(team, gold, gold.task("gold").orElseThrow(), 1, player);
        }
        complete(team, player, "demo_other", "read_me");
        // Out in the World: Night Watch done (its choice waiting), a few quests under way and pinned to the screen.
        complete(team, player, WORLD_CHAPTER, "zombies");
        Quest undead = quest(WORLD_CHAPTER, "undead");
        if (undead != null) {
            ProgressManager.setTaskProgress(team, undead, undead.task("undead").orElseThrow(), 7, player);
        }
        Quest jumps = quest(WORLD_CHAPTER, "jumps");
        if (jumps != null) {
            ProgressManager.setTaskProgress(team, jumps, jumps.task("jumps").orElseThrow(), 62, player);
        }
        for (String pin : new String[] {TOUR + "/tools", WORLD_CHAPTER + "/undead", WORLD_CHAPTER + "/jumps"}) {
            ProgressManager.setPinned(player, QuestRef.parse(pin).orElseThrow(), true);
        }
        // The Quest Book and a few loot crates on the hotbar.
        player.getInventory().setItem(0, new ItemStack(QuestItems.QUEST_BOOK.get()));
        BookManager.book().table("treasure").ifPresent(table -> player.getInventory().setItem(1, LootCrateItem.crate(table, 3)));
    }

    private static void complete(UUID team, ServerPlayer player, String chapter, String id) {
        Quest quest = quest(chapter, id);
        if (quest != null) {
            ProgressManager.forceComplete(team, quest, player);
        }
    }

    // ---- the plan ----

    private static void plan(Minecraft minecraft) {
        UUID self = minecraft.player.getUUID();
        int[][] sizes = {{1280, 720, 1}, {1280, 720, 2}, {1280, 720, 3}, {1440, 960, 4}};
        for (int[] size : sizes) {
            String tag = size[0] + "x" + size[1] + "_scale" + size[2];
            then(() -> {
                minecraft.getWindow().setWindowed(size[0], size[1]);
                minecraft.options.guiScale().set(size[2]);
                minecraft.resizeGui();
            }, 20);
            then(minecraft::resizeGui, 5);
            onServer(minecraft, server -> scene(server, self));
            then(() -> minecraft.gui.toastManager().clear(), 2);
            // The inventory with the book's button and its badge.
            onServer(minecraft, server -> gameMode(server, self, GameType.SURVIVAL));
            then(() -> minecraft.gui.setScreen(new InventoryScreen(minecraft.player)), 10);
            shot("inventory_" + tag);
            then(() -> minecraft.gui.setScreen(null), 5);
            onServer(minecraft, server -> gameMode(server, self, GameType.CREATIVE));
            // The tour chapter, fitted to the window.
            then(() -> {
                QuestBookScreen.open();
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.setListCollapsed(false);
                    screen.showChapter(TOUR);
                    screen.fitMap();
                }
            }, 10);
            shot("book_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.setListCollapsed(true);
                }
            }, 5);
            shot("book_collapsed_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.setListCollapsed(false);
                    screen.showQuest(new QuestRef(TOUR, "start"));
                }
            }, 5);
            shot("quest_" + tag);
            then(() -> showQuest(minecraft, new QuestRef(TOUR, "logs")), 5);
            shot("quest_rewards_" + tag);
            then(() -> showQuest(minecraft, new QuestRef(TOUR, "tools")), 5);
            shot("quest_tasks_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.topPopup().ifPresent(popup -> popup.cancel());
                    screen.typeSearch("i");
                }
            }, 5);
            shot("search_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.typeSearch("");
                    screen.showChapter("demo_items");
                }
            }, 5);
            shot("book_items_" + tag);
            // A quest completing while the book is open: its toast, and the map updating.
            onServer(minecraft, server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(self);
                if (player != null) {
                    complete(QuestTeams.teamIdOf(player), player, "demo_items", "gold");
                }
            });
            shot("toast_" + tag);
            // Out in the World: the newer task types, a choice reward waiting and its dialog.
            then(() -> {
                minecraft.gui.toastManager().clear();
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.showChapter(WORLD_CHAPTER);
                    screen.fitMap();
                }
            }, 5);
            shot("book_world_" + tag);
            then(() -> showQuest(minecraft, new QuestRef(WORLD_CHAPTER, "zombies")), 5);
            shot("quest_choice_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    moveMouse(minecraft, screen.width - 4, screen.height - 4);
                    screen.showChoice(new QuestRef(WORLD_CHAPTER, "zombies"), "pick", List.of(new ItemStack(Items.IRON_SWORD),
                            new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32)), 1);
                }
            }, 5);
            shot("choice_" + tag);
            then(() -> showQuest(minecraft, new QuestRef(WORLD_CHAPTER, "spawn")), 5);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    screen.rewardIconCentre("roll").ifPresent(centre -> moveMouse(minecraft, centre[0], centre[1]));
                }
            }, 5);
            shot("quest_random_" + tag);
            then(() -> {
                QuestBookScreen screen = book(minecraft);
                if (screen != null) {
                    moveMouse(minecraft, screen.width / 2, screen.height / 2);
                }
            }, 2);
            // Back in the world: the pinned quests in their corner, and the book and crates on the hotbar.
            then(() -> minecraft.gui.setScreen(null), 10);
            shot("hud_" + tag);
        }
    }

    private static void gameMode(MinecraftServer server, UUID self, GameType mode) {
        ServerPlayer player = server.getPlayerList().getPlayer(self);
        if (player != null) {
            player.setGameMode(mode);
        }
    }

    // Puts the mouse at a point given in GUI coordinates, for a hover tooltip (or to keep the mouse off things).
    private static void moveMouse(Minecraft minecraft, int guiX, int guiY) {
        double scale = minecraft.getWindow().getGuiScale();
        try {
            Field x = MouseHandler.class.getDeclaredField("xpos");
            Field y = MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, guiX * scale);
            y.setDouble(minecraft.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException e) {
            HibiscusQuests.LOGGER.warn("Screenshot harness: can't move the mouse", e);
        }
    }

    private static void showQuest(Minecraft minecraft, QuestRef quest) {
        QuestBookScreen screen = book(minecraft);
        if (screen != null) {
            screen.showQuest(quest);
        }
    }
}

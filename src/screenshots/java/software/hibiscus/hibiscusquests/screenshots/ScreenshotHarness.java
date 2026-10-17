/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.screenshots;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
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
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.client.edit.ClientEditor;
import software.hibiscus.hibiscusquests.client.edit.EditActions;
import software.hibiscus.hibiscusquests.client.screen.QuestBookScreen;
import software.hibiscus.hibiscusquests.edit.BookEdit;
import software.hibiscus.hibiscusquests.edit.BookEditor;
import software.hibiscus.hibiscusquests.edit.EditingMode;
import software.hibiscus.hibiscusquests.edit.LangExport;
import software.hibiscus.hibiscusquests.item.LootCrateItem;
import software.hibiscus.hibiscusquests.network.QuestSync;
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
    // The demo's nested group (Outdoors, under Demo), and its parent.
    private static final String SUB_GROUP = "demo_outdoors";
    private static final String PARENT_GROUP = "demo";

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
        // Edits go to a pack in a temporary directory, so the run leaves config/ alone.
        onServer(minecraft, server -> {
            try {
                BookManager.usePackDirectory(server, Files.createTempDirectory("hibiscusquests-screenshots"));
            } catch (IOException e) {
                HibiscusQuests.LOGGER.warn("Screenshot harness: no temporary pack", e);
            }
        });
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
            // The list's tree folded: the nested group, then its parent (which hides it too); then unfolded again.
            then(() -> withBook(minecraft, screen -> screen.setGroupFolded(SUB_GROUP, true)), 5);
            shot("list_folded_sub_" + tag);
            then(() -> withBook(minecraft, screen -> screen.setGroupFolded(PARENT_GROUP, true)), 5);
            shot("list_folded_parent_" + tag);
            then(() -> withBook(minecraft, screen -> {
                screen.setGroupFolded(PARENT_GROUP, false);
                screen.setGroupFolded(SUB_GROUP, false);
            }), 5);
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
            editorShots(minecraft, self, tag);
        }
    }

    // Edit mode on the tour chapter (overridden into the temporary pack so it can be edited): a selection, a quest's
    // menu, its form, its task list, the item picker, the description editor and the reward tables.
    private static void editorShots(Minecraft minecraft, UUID self, String tag) {
        QuestRef tools = new QuestRef(TOUR, "tools");
        onServer(minecraft, server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(self);
            if (player != null) {
                EditingMode.set(player, true, false);
                if (BookManager.book().chapter(TOUR).flatMap(Chapter::source).isPresent()) {
                    BookEditor.apply(server, List.of(new BookEdit.Override(TOUR)));
                }
            }
        });
        then(() -> {
            QuestBookScreen.open();
            QuestBookScreen screen = book(minecraft);
            if (screen != null) {
                screen.showChapter(TOUR);
                screen.fitMap();
                screen.selectQuests(List.of("logs", "tools"));
                moveMouse(minecraft, screen.width - 4, screen.height - 4);
            }
        }, 10);
        shot("edit_book_" + tag);
        // Real edits through the client: move two quests down a step and undo it, add a quest and undo that. Each
        // comes back as a patch, which must leave the client with the server's book.
        double[] before = new double[1];
        then(() -> {
            before[0] = questY("logs");
            EditActions.move(TOUR, List.of(EditActions.Element.quest("logs"), EditActions.Element.quest("tools")), 0, 1);
        }, 20);
        shot("edit_moved_" + tag);
        then(() -> {
            report("a move reaches the book", questY("logs") == before[0] + 1);
            report("as a patch, to the server's book", ClientQuestState.hash().equals(QuestSync.bookHash()));
            ClientEditor.undo();
        }, 20);
        then(() -> {
            report("undo puts it back", questY("logs") == before[0]);
            CompoundTag task = new CompoundTag();
            task.putString("type", "checkmark");
            EditActions.createQuest(TOUR, 8, 3, "Harness Quest", task);
        }, 20);
        then(() -> {
            report("a new quest arrives", ClientBook.book().quest(new QuestRef(TOUR, "harness_quest")).isPresent());
            ClientEditor.undo();
        }, 20);
        then(() -> {
            report("and undo removes it", ClientBook.book().quest(new QuestRef(TOUR, "harness_quest")).isEmpty());
            report("the client still has the server's book", ClientQuestState.hash().equals(QuestSync.bookHash()));
            withBook(minecraft, screen -> screen.selectQuests(List.of("logs", "tools")));
        }, 5);
        then(() -> withBook(minecraft, screen -> screen.showEditMenu(tools)), 5);
        shot("edit_menu_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            screen.showQuestForm(tools);
        }), 5);
        shot("edit_form_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            screen.showTaskList(tools);
        }), 5);
        shot("edit_tasks_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            screen.showItemPicker();
        }), 5);
        shot("edit_picker_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            screen.showRichText(new QuestRef(TOUR, "start"));
        }), 5);
        shot("edit_text_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            screen.showTables();
        }), 5);
        shot("edit_tables_" + tag);
        then(() -> withBook(minecraft, QuestBookScreen::closePopups), 2);
        // A group's menu, and taking the nested group out of its parent and back (undo), through the client.
        then(() -> withBook(minecraft, screen -> screen.showGroupMenu(SUB_GROUP)), 5);
        shot("edit_group_menu_" + tag);
        then(() -> withBook(minecraft, screen -> {
            screen.closePopups();
            EditActions.moveGroupOut(SUB_GROUP);
        }), 20);
        shot("edit_group_out_" + tag);
        then(() -> {
            report("moving a group out reaches the book", ClientBook.book().groupTree().parent(SUB_GROUP).isEmpty());
            report("as a patch, to the server's book", ClientQuestState.hash().equals(QuestSync.bookHash()));
            ClientEditor.undo();
        }, 20);
        then(() -> {
            report("undo puts it back in its parent", ClientBook.book().groupTree().parent(SUB_GROUP).equals(PARENT_GROUP));
            report("the client still has the server's book", ClientQuestState.hash().equals(QuestSync.bookHash()));
        }, 2);
        // Translations: export_lang moves the tour's text into the pack's lang file; the client shows the text (not
        // the key), and the editor saving a translated title changes the translation, not the chapter.
        onServer(minecraft, server -> BookEditor.apply(server, LangExport.edits(server, BookText.DEFAULT_LOCALE).edits()));
        then(() -> {
            Quest start = ClientBook.book().quest(new QuestRef(TOUR, "start")).orElse(null);
            report("translated titles show their text", start != null && start.title().startsWith("{translate:")
                    && !BookText.component(start.title()).getString().startsWith("quests."));
            String key = start == null ? "" : BookText.keyOf(start.title()).orElse("");
            EditActions.withLang(Map.of(key, "Welcome, edited"), () -> EditActions.putQuest(TOUR, "start", EditActions.encodeQuest(start), "edit_quest"));
        }, 20);
        then(() -> {
            Quest start = ClientBook.book().quest(new QuestRef(TOUR, "start")).orElse(null);
            report("editing translated text changes the translation", start != null
                    && BookText.component(start.title()).getString().equals("Welcome, edited"));
            report("the client still has the server's book", ClientQuestState.hash().equals(QuestSync.bookHash()));
        }, 2);
        onServer(minecraft, server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(self);
            if (player != null) {
                EditingMode.set(player, false, false);
            }
        });
        then(() -> {
            moveMouse(minecraft, minecraft.getWindow().getGuiScaledWidth() / 2, minecraft.getWindow().getGuiScaledHeight() / 2);
            minecraft.gui.setScreen(null);
        }, 10);
    }

    private static double questY(String id) {
        return ClientBook.book().quest(new QuestRef(TOUR, id)).map(quest -> quest.display().y()).orElse(Double.NaN);
    }

    // Logs a check on the editor's round trip (the harness has no assertions of its own; grep the log for FAILED).
    private static void report(String what, boolean ok) {
        HibiscusQuests.LOGGER.info("Screenshot harness check: {}: {}", what, ok ? "ok" : "FAILED");
    }

    private static void withBook(Minecraft minecraft, Consumer<QuestBookScreen> action) {
        QuestBookScreen screen = book(minecraft);
        if (screen != null) {
            action.accept(screen);
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

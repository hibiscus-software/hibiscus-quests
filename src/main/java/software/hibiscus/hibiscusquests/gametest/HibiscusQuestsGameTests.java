/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// Registers every Hibiscus Quests gametest, the same way Hibiscus Lib and Hibiscus Teams register theirs. They run in
// one batch on an empty structure (the editor's in a second one); ./gradlew runGameTestServer runs them (and Lib's) and exits, failing the build if
// any fails. The book they use is the built-in demo data pack, which the gametest run switches on.
public final class HibiscusQuestsGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, HibiscusQuests.MODID);

    // Every test, by name; the editor's run in a batch of their own afterwards (they rebuild the book).
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    private static final Map<String, Consumer<GameTestHelper>> EDITOR_TESTS = new LinkedHashMap<>();

    // A test that may throw (file access); a throw fails it.
    @FunctionalInterface
    private interface Test {
        void run(GameTestHelper helper) throws Exception;
    }

    private static void add(String name, Test test) {
        add(TESTS, name, test);
    }

    private static void add(Map<String, Consumer<GameTestHelper>> tests, String name, Test test) {
        tests.put(name, helper -> {
            try {
                test.run(helper);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(name + " failed: " + e.getMessage(), e);
            }
        });
    }

    static {
        add("data_chapters_load", BookGameTests::dataChaptersLoad);
        add("pack_overrides_mod_chapter", BookGameTests::packOverridesModChapter);
        add("pack_files_round_trip", BookGameTests::packFilesRoundTrip);
        add("progress_round_trip", BookGameTests::progressRoundTrip);
        add("sync_round_trip", BookGameTests::syncRoundTrip);
        add("api", BookGameTests::api);
        add("task_item_detect", TaskGameTests::itemDetect);
        add("task_item_consume", TaskGameTests::itemConsume);
        add("task_checkmark", TaskGameTests::checkmark);
        add("task_advancement", TaskGameTests::advancement);
        add("task_criterion", TaskGameTests::criterion);
        add("dependency_modes", LogicGameTests::dependencyModes);
        add("one_of_branch", LogicGameTests::oneOfBranch);
        add("require_sequential", LogicGameTests::sequential);
        add("chapter_gate", LogicGameTests::chapterGate);
        add("locked_progress_counts", LogicGameTests::lockedProgressCounts);
        add("linear_tasks", LogicGameTests::linear);
        add("missing_dependency_dropped", LogicGameTests::missingDependency);
        add("rewards_per_player_and_team", RewardGameTests::perPlayerAndTeam);
        add("rewards_refused_claims", RewardGameTests::refusedClaims);
        add("rewards_auto_claim", RewardGameTests::autoClaim);
        add("repeatable_cooldown", RewardGameTests::repeatable);
        add("team_solo_is_player", TeamGameTests::soloTeamIsPlayer);
        add("team_change_keep", TeamGameTests::keepWithTeam);
        add("team_change_copy", TeamGameTests::copyToNewTeam);
        add("team_change_merge", TeamGameTests::merge);
        add("task_kill", WorldTaskGameTests::kill);
        add("task_location", WorldTaskGameTests::location);
        add("task_dimension", WorldTaskGameTests::dimension);
        add("task_stat", WorldTaskGameTests::stat);
        add("task_xp", WorldTaskGameTests::xp);
        add("task_fluid", WorldTaskGameTests::fluid);
        add("task_energy", WorldTaskGameTests::energy);
        add("reward_choice", RewardTypeGameTests::choice);
        add("reward_random_and_loot", RewardTypeGameTests::randomAndLoot);
        add("reward_toast", RewardTypeGameTests::toast);
        add("repeat_without_claiming", RewardTypeGameTests::repeatWithoutClaiming);
        add("reward_tables_load", RewardTypeGameTests::rewardTablesLoad);
        add("pins", RewardTypeGameTests::pins);
        // Only with Arcforge in the run (drop its jar in run/gameTestServer/mods/): its five custom triggers.
        if (ModList.get().isLoaded("arcforge")) {
            add("arcforge_triggers", IntegrationGameTests::arcforgeTriggers);
        }
        add(EDITOR_TESTS, "editor_edits", EditorGameTests::edits);
        add(EDITOR_TESTS, "editor_override", EditorGameTests::override);
        add(EDITOR_TESTS, "editor_tables_groups_settings", EditorGameTests::tablesGroupsSettings);
        add(EDITOR_TESTS, "editor_permission", EditorGameTests::permission);
        add(EDITOR_TESTS, "editor_generate_chapter", EditorGameTests::generate);
        add(EDITOR_TESTS, "pack_lang", PackGameTests::lang);
        add(EDITOR_TESTS, "pack_export_import", PackGameTests::exportImport);
        add(EDITOR_TESTS, "pack_export_chapter", PackGameTests::exportChapter);
        add(EDITOR_TESTS, "progress_cleanup", PackGameTests::cleanup);
        add(EDITOR_TESTS, "emergency_items", PackGameTests::emergency);
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        EDITOR_TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private HibiscusQuestsGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(HibiscusQuestsGameTests::registerTests);
        // Vanilla has no item that holds energy, so in the gametest run a redstone block holds 100000 FE (always full:
        // each look at it is a new store), for task_energy.
        if (Boolean.getBoolean("hibiscusquests.gametest")) {
            modEventBus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(Capabilities.Energy.ITEM,
                    (stack, access) -> new SimpleEnergyHandler(100_000, 0, 100_000, 100_000), Items.REDSTONE_BLOCK));
        }
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                200, 0, true, Rotation.NONE, false, 1, 1, false, 8);
        TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(function(name), data)));
        // The editor's tests rebuild the book, so they get an environment (a batch) of their own, one at a time.
        Holder<TestEnvironmentDefinition<?>> editor = event.registerEnvironment(id("editor"));
        TestData<Holder<TestEnvironmentDefinition<?>>> editorData = new TestData<>(editor, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                200, 0, true, Rotation.NONE, false, 1, 1, false, 8);
        EDITOR_TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(function(name), editorData)));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HibiscusQuests.MODID, path);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }
}

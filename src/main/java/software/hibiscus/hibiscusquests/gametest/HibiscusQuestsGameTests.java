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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// Registers every Hibiscus Quests gametest, the same way Hibiscus Lib and Hibiscus Teams register theirs. All run in
// one batch on an empty structure; ./gradlew runGameTestServer runs them (and Lib's) and exits, failing the build if
// any fails. The book they use is the built-in demo data pack, which the gametest run switches on.
public final class HibiscusQuestsGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, HibiscusQuests.MODID);

    // Every test, by name.
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    // A test that may throw (file access); a throw fails it.
    @FunctionalInterface
    private interface Test {
        void run(GameTestHelper helper) throws Exception;
    }

    private static void add(String name, Test test) {
        TESTS.put(name, helper -> {
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
        // Only with Arcforge in the run (drop its jar in run/gameTestServer/mods/): its five custom triggers.
        if (ModList.get().isLoaded("arcforge")) {
            add("arcforge_triggers", IntegrationGameTests::arcforgeTriggers);
        }
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private HibiscusQuestsGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(HibiscusQuestsGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                200, 0, true, Rotation.NONE, false, 1, 1, false, 8);
        TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(function(name), data)));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HibiscusQuests.MODID, path);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }
}

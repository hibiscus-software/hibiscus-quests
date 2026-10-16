/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.completed;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.progress;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.quest;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.submit;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.tick;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestLogic;

// The unlock and completion rules: dependency modes, one_of branches, require_sequential, chapter gates, progress
// made while locked, linear quests, and dependencies that point nowhere.
final class LogicGameTests {
    private static final String LOGIC = "test_logic";

    private LogicGameTests() {}

    // all needs every dependency, any one of them, min at least min_required; quests with no tasks complete as soon as
    // they unlock.
    static void dependencyModes(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !completed(player, LOGIC, "needs_any"), "nothing is unlocked yet");
        check(helper, tick(player, LOGIC, "a"), "A takes a tick");
        check(helper, completed(player, LOGIC, "needs_any"), "any: one dependency is enough");
        check(helper, !completed(player, LOGIC, "needs_all"), "all: one of two isn't enough");
        check(helper, !completed(player, LOGIC, "needs_two"), "min 2: one isn't enough");
        check(helper, tick(player, LOGIC, "b"), "B takes a tick");
        check(helper, completed(player, LOGIC, "needs_all"), "all: both done unlocks and completes it");
        check(helper, completed(player, LOGIC, "needs_two"), "min 2: A and B are two");
        leave(helper, player);
        helper.succeed();
    }

    // Completing one one_of quest locks the others that share its dependency.
    static void oneOfBranch(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !tick(player, LOGIC, "left"), "left is locked until A");
        check(helper, tick(player, LOGIC, "a"), "A takes a tick");
        check(helper, tick(player, LOGIC, "left"), "left is open after A");
        check(helper, completed(player, LOGIC, "left"), "left completes");
        check(helper, !tick(player, LOGIC, "right"), "right is locked once left is taken");
        check(helper, !QuestLogic.lockReasons(BookManager.book(), progress(player), quest(helper, LOGIC, "right"), QuestClock.now(), true).isEmpty(),
                "right says why it's locked");
        leave(helper, player);
        helper.succeed();
    }

    // In a require_sequential chapter each quest waits for the one before it.
    static void sequential(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !tick(player, "test_sequential", "s2"), "s2 waits for s1");
        check(helper, tick(player, "test_sequential", "s1"), "s1 is open");
        check(helper, tick(player, "test_sequential", "s2"), "s2 opens after s1");
        check(helper, !tick(player, "test_sequential", "s3") || completed(player, "test_sequential", "s3"), "s3 opens after s2");
        leave(helper, player);
        helper.succeed();
    }

    // A chapter's requires (a chapter and a quest) gate all of its quests.
    static void chapterGate(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !tick(player, "test_gated", "g1"), "g1 is behind the gate");
        tick(player, "test_sequential", "s1");
        tick(player, "test_sequential", "s2");
        tick(player, "test_sequential", "s3");
        check(helper, !tick(player, "test_gated", "g1"), "the gate also needs test_logic/a");
        tick(player, LOGIC, "a");
        check(helper, tick(player, "test_gated", "g1"), "with test_sequential and test_logic/a done, g1 opens");
        leave(helper, player);
        helper.succeed();
    }

    // Detected items count while the quest is locked; it completes the moment it unlocks.
    static void lockedProgressCounts(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.getInventory().add(new ItemStack(Items.STICK));
        helper.runAfterDelay(25, () -> {
            check(helper, progress(player).task(ref(LOGIC, "held"), "stick") == 1, "the stick counts while the quest is locked");
            check(helper, !completed(player, LOGIC, "held"), "but the quest doesn't complete while locked");
            check(helper, tick(player, LOGIC, "gate"), "the gate quest takes a tick");
            check(helper, completed(player, LOGIC, "held"), "unlocking completes it straight away");
            leave(helper, player);
            helper.succeed();
        });
    }

    // A linear quest's tasks go in order.
    static void linear(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !submit(player, LOGIC, "linear", "second"), "the second task waits for the first");
        check(helper, submit(player, LOGIC, "linear", "first"), "the first task is open");
        check(helper, submit(player, LOGIC, "linear", "second"), "then the second");
        check(helper, completed(player, LOGIC, "linear"), "both done completes it");
        leave(helper, player);
        helper.succeed();
    }

    // A dependency on a quest that isn't in the book is dropped with a warning instead of locking the quest forever.
    static void missingDependency(GameTestHelper helper) {
        check(helper, quest(helper, LOGIC, "ghost").dependencies().isEmpty(), "the unknown dependency is dropped");
        check(helper, BookManager.warnings().stream().anyMatch(w -> w.contains("no_such_quest")), "and reported");
        helper.succeed();
    }
}

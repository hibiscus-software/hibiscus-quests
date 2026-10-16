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
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.team;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.tick;

import java.util.Optional;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ClaimResult;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestState;

// Claiming: per-player rewards (each member claims their own) against team rewards (claimed once), claims the server
// must refuse, auto-claim, Claim all's exclusions, command rewards, and a repeatable quest with a cooldown.
final class RewardGameTests {
    private static final String CHAPTER = "test_rewards";

    private RewardGameTests() {}

    // Two players in one (stub) team: both claim their own apples, but only one gets the team's stick.
    static void perPlayerAndTeam(GameTestHelper helper) {
        ServerPlayer first = join(helper);
        ServerPlayer second = join(helper);
        StubTeams teams = StubTeams.get();
        StubTeams.Party party = teams.party("rewards");
        teams.join(party, first.getUUID());
        teams.join(party, second.getUUID());
        ActiveTasks.refresh(first);
        ActiveTasks.refresh(second);
        try {
            check(helper, team(first).equals(party.id()) && team(second).equals(party.id()), "both players' progress is the party's");
            check(helper, tick(first, CHAPTER, "shared"), "the first player ticks the quest");
            check(helper, completed(second, CHAPTER, "shared"), "and it's complete for the second too");
            check(helper, ProgressManager.claim(first, ref(CHAPTER, "shared"), "apple") == ClaimResult.CLAIMED, "the first claims their apples");
            check(helper, ProgressManager.claim(second, ref(CHAPTER, "shared"), "apple") == ClaimResult.CLAIMED, "the second claims their own");
            check(helper, first.getInventory().countItem(Items.APPLE) == 2 && second.getInventory().countItem(Items.APPLE) == 2,
                    "each has 2 apples");
            check(helper, ProgressManager.claim(first, ref(CHAPTER, "shared"), "stick") == ClaimResult.CLAIMED, "the first claims the team's stick");
            check(helper, ProgressManager.claim(second, ref(CHAPTER, "shared"), "stick") == ClaimResult.ALREADY_CLAIMED,
                    "the team reward can't be claimed twice");
            check(helper, second.getInventory().countItem(Items.STICK) == 0, "so the second gets no stick");
            check(helper, progress(second).state(ref(CHAPTER, "shared")).teamClaimer("stick").equals(Optional.of(first.getUUID())),
                    "the book can say who on the team claimed it");
            int levelsBefore = second.totalExperience;
            check(helper, ProgressManager.claimAll(second) == 1, "Claim all takes the XP but leaves the excluded command reward");
            check(helper, second.totalExperience == levelsBefore + 7, "7 XP points arrive");
            check(helper, ProgressManager.claim(first, ref(CHAPTER, "shared"), "tag") == ClaimResult.CLAIMED, "the command reward claims");
            check(helper, first.entityTags().contains("hq_reward_ran"), "and its command ran with {uuid} replaced");
            helper.succeed();
        } finally {
            teams.leave(first.getUUID());
            teams.leave(second.getUUID());
            leave(helper, first, second);
        }
    }

    // Claims the server must refuse: an incomplete quest, an unknown reward, and a second claim.
    static void refusedClaims(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "shared"), "apple") == ClaimResult.NOT_COMPLETE,
                "an incomplete quest's reward can't be claimed");
        tick(player, CHAPTER, "shared");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "shared"), "no_such_reward") == ClaimResult.UNKNOWN, "an unknown reward");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "nope"), "apple") == ClaimResult.UNKNOWN, "an unknown quest");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "shared"), "apple") == ClaimResult.CLAIMED, "a real claim works");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "shared"), "apple") == ClaimResult.ALREADY_CLAIMED, "but only once");
        check(helper, player.getInventory().countItem(Items.APPLE) == 2, "and pays once");
        leave(helper, player);
        helper.succeed();
    }

    static void autoClaim(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        int before = player.experienceLevel;
        tick(player, CHAPTER, "auto");
        check(helper, player.experienceLevel == before + 2, "the auto-claim levels arrive on completion");
        check(helper, progress(player).state(ref(CHAPTER, "auto")).isClaimed("levels", false, player.getUUID()), "and are recorded");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "auto"), "levels") == ClaimResult.ALREADY_CLAIMED, "so can't be claimed again");
        leave(helper, player);
        helper.succeed();
    }

    // A repeatable quest resets once its rewards are claimed and waits out its cooldown (1 second) before
    // counting again.
    static void repeatable(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, tick(player, CHAPTER, "again"), "the first round ticks");
        check(helper, completed(player, CHAPTER, "again"), "and completes");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "again"), "points") == ClaimResult.CLAIMED, "claiming the reward");
        QuestState state = progress(player).state(ref(CHAPTER, "again"));
        check(helper, state != null && !state.isCompleted() && state.repeats() == 1, "starts the quest over, one repeat counted");
        check(helper, !tick(player, CHAPTER, "again"), "during the cooldown the task doesn't count");
        // Cooldowns run in real time; the gametest server runs ticks faster than that, so move the clock on instead.
        QuestClock.advance(1500);
        helper.succeedWhen(() -> {
            check(helper, tick(player, CHAPTER, "again"), "after the cooldown it counts again");
            check(helper, completed(player, CHAPTER, "again"), "and the second round completes");
            leave(helper, player);
        });
    }
}

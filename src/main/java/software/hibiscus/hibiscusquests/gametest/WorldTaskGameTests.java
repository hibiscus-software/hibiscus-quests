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
import static software.hibiscus.hibiscusquests.gametest.TestSupport.submit;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

// The task types that watch the world: kill (by type and by tag), location (radius and box), dimension, stat, xp
// (handed in and just held), fluid (handed in and just held) and energy (from an item; the gametest run gives redstone
// blocks an energy store for this).
final class WorldTaskGameTests {
    private static final String CHAPTER = "test_world";

    private WorldTaskGameTests() {}

    private static long value(ServerPlayer player, String quest) {
        return progress(player).task(ref(CHAPTER, quest), "t");
    }

    // A zombie killed by the player counts for both the zombie task and the #undead one.
    static void kill(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ServerLevel level = helper.getLevel();
        for (int n = 0; n < 2; n++) {
            Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
            zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000);
            check(helper, zombie.isDeadOrDying(), "the zombie dies");
            if (n == 0) {
                check(helper, value(player, "kill") == 1 && !completed(player, CHAPTER, "kill"), "one zombie of two");
                check(helper, completed(player, CHAPTER, "kill_tag"), "a zombie is #minecraft:undead");
            }
        }
        check(helper, completed(player, CHAPTER, "kill"), "two zombies complete the kill task");
        leave(helper, player);
        helper.succeed();
    }

    // Being within the radius, or inside the box, completes the task on the next check (once a second).
    static void location(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.teleportTo(5.5, -59, 5.5);
        helper.succeedWhen(() -> {
            check(helper, completed(player, CHAPTER, "location"), "standing within 3 blocks of 5, -59, 5");
            if (!completed(player, CHAPTER, "box")) {
                check(helper, player.getX() < 100, "the box isn't reached from outside it");
                player.teleportTo(105.5, 0, 105.5);
                helper.fail("not in the box yet");
            }
            leave(helper, player);
        });
    }

    static void dimension(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        check(helper, nether != null, "the gametest server has a Nether");
        check(helper, !completed(player, CHAPTER, "dimension"), "not in the Nether yet");
        player.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0, 0, false);
        helper.succeedWhen(() -> {
            check(helper, completed(player, CHAPTER, "dimension"), "entering the Nether completes it");
            leave(helper, player);
        });
    }

    static void stat(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.awardStat(Stats.JUMP, 2);
        helper.runAfterDelay(25, () -> {
            check(helper, value(player, "stat") == 2, "two jumps count as 2 of 3");
            player.awardStat(Stats.JUMP);
            helper.succeedWhen(() -> {
                check(helper, completed(player, CHAPTER, "stat"), "the third jump completes it");
                leave(helper, player);
            });
        });
    }

    // Handing in levels takes only what's needed; with consume: false, having the levels is enough.
    static void xp(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.giveExperienceLevels(7);
        check(helper, submit(player, CHAPTER, "xp", "t"), "the xp task takes submissions");
        check(helper, completed(player, CHAPTER, "xp"), "5 levels complete it");
        check(helper, player.experienceLevel == 2, "and only 5 of the 7 levels are taken");
        player.giveExperienceLevels(1);
        helper.succeedWhen(() -> {
            check(helper, completed(player, CHAPTER, "xp_detect"), "having 3 levels completes the detect task");
            check(helper, player.experienceLevel == 3, "without taking any");
            leave(helper, player);
        });
    }

    // Water from buckets: handed in (the buckets come back empty), or just carried.
    static void fluid(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        helper.succeedWhen(() -> {
            check(helper, completed(player, CHAPTER, "fluid_detect"), "carrying a water bucket is 1000 mB");
            check(helper, submit(player, CHAPTER, "fluid", "t"), "the fluid task takes submissions");
            check(helper, value(player, "fluid") == 1000, "one bucket hands in 1000 mB");
            check(helper, player.getInventory().countItem(Items.BUCKET) == 1, "and comes back empty");
            player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
            submit(player, CHAPTER, "fluid", "t");
            check(helper, completed(player, CHAPTER, "fluid"), "a second bucket completes 2000 mB");
            leave(helper, player);
        });
    }

    // At most max_rate (20000 FE) per press.
    static void energy(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        player.getInventory().add(new ItemStack(Items.REDSTONE_BLOCK));
        check(helper, submit(player, CHAPTER, "energy", "t"), "the energy task takes submissions");
        check(helper, value(player, "energy") == 20000, "one press takes max_rate");
        submit(player, CHAPTER, "energy", "t");
        check(helper, completed(player, CHAPTER, "energy"), "a second press completes 30000 FE");
        leave(helper, player);
        helper.succeed();
    }
}

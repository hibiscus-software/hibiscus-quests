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
import static software.hibiscus.hibiscusquests.gametest.TestSupport.team;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.progress.ProgressManager;

// The built-in task types tracking progress on a real (mock) player: item detect and consume, checkmark, advancement,
// and criterion with a vanilla trigger, the last without touching the player's advancements.
final class TaskGameTests {
    // Players whose advancement progress was granted on one of Hibiscus Quests' stand-in advancements (must stay
    // empty).
    private static final Set<UUID> STAND_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    static {
        NeoForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementProgressEvent event) -> {
            if (event.getAdvancement().id().getNamespace().equals(HibiscusQuests.MODID)) {
                STAND_IN_PROGRESS.add(event.getEntity().getUUID());
            }
        });
    }

    private TaskGameTests() {}

    // Carrying the items is enough; the inventory check runs every itemScanInterval ticks.
    static void itemDetect(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        quest(helper, "demo_items", "iron");
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
        helper.runAfterDelay(25, () -> {
            check(helper, progress(player).task(ref("demo_items", "iron"), "iron") == 3, "3 of 4 iron should be counted");
            check(helper, !completed(player, "demo_items", "iron"), "3 of 4 iron isn't enough");
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
            helper.succeedWhen(() -> {
                check(helper, completed(player, "demo_items", "iron"), "holding 5 iron should complete the quest");
                check(helper, progress(player).task(ref("demo_items", "iron"), "iron") == 4, "progress is capped at the target");
                leave(helper, player);
            });
        });
    }

    // Submit takes as many items as are still needed, no more.
    static void itemConsume(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ProgressManager.forceComplete(team(player), quest(helper, "demo_items", "iron"), player);
        check(helper, !submit(player, "demo_items", "gold", "gold") || progress(player).task(ref("demo_items", "gold"), "gold") == 0,
                "submitting with no gold changes nothing");
        player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 3));
        check(helper, submit(player, "demo_items", "gold", "gold"), "the gold task should take submissions");
        check(helper, completed(player, "demo_items", "gold"), "submitting 2 gold should complete the quest");
        check(helper, player.getInventory().countItem(Items.GOLD_INGOT) == 1, "only the 2 needed should be taken");
        leave(helper, player);
        helper.succeed();
    }

    // A checkmark is ticked from the book; its auto-claim reward arrives by itself.
    static void checkmark(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, submit(player, "demo_other", "read_me", "ack"), "the checkmark should take a tick");
        check(helper, completed(player, "demo_other", "read_me"), "ticking it completes the quest");
        check(helper, player.getInventory().countItem(Items.BREAD) == 4, "the auto-claim bread should be in the inventory");
        check(helper, progress(player).state(ref("demo_other", "read_me")).isClaimed("bread", false, player.getUUID()),
                "the auto-claimed reward is recorded as claimed");
        leave(helper, player);
        helper.succeed();
    }

    // An advancement task completes when the player earns the advancement.
    static void advancement(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ProgressManager.forceComplete(team(player), quest(helper, "demo_other", "read_me"), player);
        check(helper, !completed(player, "demo_other", "stone"), "the advancement isn't earned yet");
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Identifier.withDefaultNamespace("story/mine_stone"));
        check(helper, holder != null, "vanilla's story/mine_stone should exist");
        player.getAdvancements().award(holder, "get_stone");
        check(helper, completed(player, "demo_other", "stone"), "earning story/mine_stone should complete the quest");
        leave(helper, player);
        helper.succeed();
    }

    // A criterion task counts a vanilla trigger with matching conditions, even before its quest unlocks, and writes
    // nothing to the player's advancements.
    static void criterion(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, !completed(player, "demo_other", "stone"), "the apples quest starts locked");
        CriteriaTriggers.CONSUME_ITEM.trigger(player, new ItemStack(Items.APPLE));
        check(helper, progress(player).task(ref("demo_other", "apples"), "eat") == 1, "an apple eaten while locked counts");
        CriteriaTriggers.CONSUME_ITEM.trigger(player, new ItemStack(Items.BREAD));
        check(helper, progress(player).task(ref("demo_other", "apples"), "eat") == 1, "bread doesn't match the conditions");
        ProgressManager.forceComplete(team(player), quest(helper, "demo_other", "read_me"), player);
        ProgressManager.forceComplete(team(player), quest(helper, "demo_other", "stone"), player);
        check(helper, !completed(player, "demo_other", "apples"), "one apple of two isn't enough");
        CriteriaTriggers.CONSUME_ITEM.trigger(player, new ItemStack(Items.APPLE));
        check(helper, completed(player, "demo_other", "apples"), "the second apple completes it");
        CriteriaTriggers.CONSUME_ITEM.trigger(player, new ItemStack(Items.APPLE));
        check(helper, progress(player).task(ref("demo_other", "apples"), "eat") == 2, "a finished task stops counting");
        check(helper, !STAND_IN_PROGRESS.contains(player.getUUID()), "no advancement progress may be granted for a criterion task");
        leave(helper, player);
        helper.succeed();
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.progress;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.tick;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import software.hibiscus.hibiscusquests.QuestItems;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.config.QuestsConfig;
import software.hibiscus.hibiscusquests.item.LootCrateItem;
import software.hibiscus.hibiscusquests.progress.ClaimResult;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestState;

// The reward types beyond items and XP: choice (claimed with a pick, never by Claim all), random and loot (rolling a
// reward table, and opening its crate), toast, repeating without claiming, reward tables loading, and pins.
final class RewardTypeGameTests {
    private static final String CHAPTER = "test_world";

    private RewardTypeGameTests() {}

    static void choice(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        tick(player, CHAPTER, "choice");
        check(helper, ProgressManager.claimAll(player) == 1, "Claim all takes the XP but not the choice");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "choice"), "pick") == ClaimResult.FAILED, "a choice needs a pick");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "choice"), "pick", 7) == ClaimResult.FAILED, "a pick in range");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "choice"), "pick", 1) == ClaimResult.CLAIMED, "picking the bow claims it");
        check(helper, player.getInventory().countItem(Items.BOW) == 1 && player.getInventory().countItem(Items.IRON_SWORD) == 0,
                "the bow, and only the bow, arrives");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "choice"), "pick", 0) == ClaimResult.ALREADY_CLAIMED, "once");
        leave(helper, player);
        helper.succeed();
    }

    // A one-entry table always gives its entry.
    static void randomAndLoot(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        tick(player, CHAPTER, "random");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "random"), "roll") == ClaimResult.CLAIMED, "the random reward claims");
        check(helper, player.getInventory().countItem(Items.EMERALD) == 3, "and rolls the table's 3 emeralds");
        tick(player, CHAPTER, "loot");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "loot"), "crate") == ClaimResult.CLAIMED, "the loot reward claims");
        ItemStack crates = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(QuestItems.lootCrate())) {
                crates = player.getInventory().getItem(slot);
            }
        }
        check(helper, crates.getCount() == 2, "2 crates arrive");
        check(helper, LootCrateItem.table(crates).orElse("").equals("test_single"), "tied to the table");
        // Mock players are in creative, where using an item doesn't use it up.
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, crates.copy());
        crates.setCount(0);
        int emeralds = player.getInventory().countItem(Items.EMERALD);
        player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        check(helper, player.getMainHandItem().getCount() == 1, "opening one uses it up");
        check(helper, player.getInventory().countItem(Items.EMERALD) == emeralds + 3, "and gives the table's roll");
        leave(helper, player);
        helper.succeed();
    }

    static void toast(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        tick(player, CHAPTER, "toast");
        check(helper, ProgressManager.claim(player, ref(CHAPTER, "toast"), "toast") == ClaimResult.CLAIMED, "a toast reward claims");
        leave(helper, player);
        helper.succeed();
    }

    // can_repeat_without_claiming: it starts over as soon as it's done, handing out the rewards first.
    static void repeatWithoutClaiming(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        int before = player.totalExperience;
        tick(player, CHAPTER, "again");
        QuestState state = progress(player).state(ref(CHAPTER, "again"));
        check(helper, state != null && !state.isCompleted() && state.repeats() == 1, "it completed and started over at once");
        check(helper, player.totalExperience == before + 2, "with its reward given on the way");
        check(helper, tick(player, CHAPTER, "again") && progress(player).state(ref(CHAPTER, "again")).repeats() == 2, "and again");
        leave(helper, player);
        helper.succeed();
    }

    static void rewardTablesLoad(GameTestHelper helper) {
        RewardTable treasure = BookManager.book().table("treasure").orElse(null);
        check(helper, treasure != null && treasure.rolls() == 2 && treasure.entries().size() == 5, "the demo pack's treasure table loads");
        check(helper, treasure.lootCrate().isPresent() && treasure.lootCrate().get().drops().boss() == 1.0, "with its crate");
        check(helper, Math.abs(treasure.chance(treasure.entries().getFirst()) - 0.4) < 1e-9, "40 of 100 weight is 40%");
        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        check(helper, RewardTable.CODEC.parse(ops, parse("{title: \"A\", entries: [{type: \"xp\", amount: 1}]}")).isSuccess(),
                "a table of xp rewards parses");
        check(helper, RewardTable.CODEC.parse(ops, parse("{title: \"A\", entries: [{type: \"random\", table: \"a\"}]}")).isError(),
                "a table can't roll a table");
        helper.succeed();
    }

    private static Tag parse(String snbt) {
        try {
            return TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Pins: up to maxPinnedQuests, and unpinning.
    static void pins(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        int max = QuestsConfig.get(QuestsConfig.MAX_PINNED);
        String[] quests = {"kill", "kill_tag", "location", "box", "dimension", "stat", "xp", "fluid", "energy"};
        for (int n = 0; n < max; n++) {
            check(helper, ProgressManager.setPinned(player, ref(CHAPTER, quests[n]), true), "pin " + (n + 1) + " of " + max);
        }
        check(helper, !ProgressManager.setPinned(player, ref(CHAPTER, quests[max]), true), "no more than maxPinnedQuests");
        check(helper, ProgressManager.setPinned(player, ref(CHAPTER, quests[0]), false), "unpinning works");
        check(helper, ProgressManager.setPinned(player, ref(CHAPTER, quests[max]), true), "and frees a place");
        check(helper, progress(player).pinned(player.getUUID()).size() == max, "the pins are the player's");
        leave(helper, player);
        helper.succeed();
    }
}

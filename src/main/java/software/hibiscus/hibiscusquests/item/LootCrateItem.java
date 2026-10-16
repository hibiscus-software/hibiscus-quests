/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import software.hibiscus.hibiscusquests.QuestItems;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.reward.Rewards;

// A loot crate: tied to a reward table with a loot_crate section, named and coloured from it (the colour tints the
// crate's band through the dye component), and opened with a right click, which rolls the table and gives what comes up.
public class LootCrateItem extends Item {
    public LootCrateItem(Properties properties) {
        super(properties);
    }

    // A stack of a table's crates.
    public static ItemStack crate(RewardTable table, int count) {
        ItemStack stack = new ItemStack(QuestItems.lootCrate(), Math.max(1, count));
        stack.set(QuestItems.LOOT_TABLE.get(), table.id());
        RewardTable.LootCrate crate = table.lootCrate().orElse(null);
        String name = crate != null && !crate.name().isBlank() ? crate.name() : table.title();
        if (!name.isBlank()) {
            stack.set(DataComponents.ITEM_NAME, BookText.component(name));
        }
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(crate != null ? crate.color() : 0xE8B04B));
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DYED_COLOR, true));
        return stack;
    }

    public static Optional<String> table(ItemStack stack) {
        return Optional.ofNullable(stack.get(QuestItems.LOOT_TABLE.get()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Optional<RewardTable> table = table(stack).flatMap(id -> BookManager.book().table(id));
        if (table.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("hibiscusquests.crate.unknown"));
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        RewardContext context = Rewards.crateContext(serverPlayer, table.get().id());
        List<Reward> rolled = table.get().roll(serverPlayer.getRandom());
        for (Reward reward : rolled) {
            Rewards.give(context, reward);
        }
        if (rolled.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("hibiscusquests.crate.empty"));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS_SERVER;
    }
}

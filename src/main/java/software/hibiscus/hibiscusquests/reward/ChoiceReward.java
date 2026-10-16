/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import java.util.List;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;

// choice: the player picks one of the items in a dialog when claiming. Left out of Claim all and auto-claim, since
// someone has to choose.
//   {type: "choice", items: ["minecraft:iron_sword", "minecraft:bow", {id: "minecraft:arrow", count: 32}]}
public record ChoiceReward(List<ItemStack> items) implements Reward {
    // An item id, or a stack with a count and components.
    static final Codec<ItemStack> STACK = Codec.either(Identifier.CODEC, ItemStack.CODEC).comapFlatMap(
            either -> either.map(
                    id -> BuiltInRegistries.ITEM.getOptional(id).map(item -> DataResult.success(new ItemStack(item)))
                            .orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
                    DataResult::success),
            stack -> stack.getCount() == 1 && stack.getComponentsPatch().isEmpty()
                    ? Either.left(BuiltInRegistries.ITEM.getKey(stack.getItem())) : Either.right(stack));

    public static final MapCodec<ChoiceReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            STACK.listOf().validate(list -> list.size() >= 2 && list.size() <= 12 ? DataResult.success(list)
                    : DataResult.error(() -> "A choice reward needs 2 to 12 items")).fieldOf("items").forGetter(ChoiceReward::items))
            .apply(i, ChoiceReward::new));

    public static final RewardType<ChoiceReward> TYPE = new RewardType<>() {
        @Override
        public MapCodec<ChoiceReward> codec() {
            return CODEC;
        }

        // Nothing chosen (or out of range): stays unclaimed.
        @Override
        public boolean claim(RewardContext context, ChoiceReward reward) {
            int choice = context.choice();
            if (choice < 0 || choice >= reward.items.size()) {
                return false;
            }
            ItemStack stack = reward.items.get(choice).copy();
            while (!stack.isEmpty()) {
                ItemStack part = stack.split(stack.getMaxStackSize());
                context.player().getInventory().placeItemBackInInventory(part, Prediction.SERVER_ONLY);
            }
            return true;
        }
    };

    public ChoiceReward {
        items = items.stream().map(ItemStack::copy).toList();
    }

    @Override
    public RewardType<?> type() {
        return TYPE;
    }

    @Override
    public ItemStack icon() {
        return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().copy();
    }

    @Override
    public Component title() {
        return Component.translatable("hibiscusquests.reward.choice", items.size());
    }
}

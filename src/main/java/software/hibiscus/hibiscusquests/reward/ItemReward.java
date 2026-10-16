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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;

// item: an item stack, any count (split into stacks; what doesn't fit is dropped at the player's feet). A team reward
// goes to whoever claims it, or with to_all_online to every member online.
//   {type: "item", item: "minecraft:diamond", count: 4}
//   {type: "item", item: {id: "minecraft:diamond_sword", components: {...}}, team_reward: true, to_all_online: true}
public record ItemReward(ItemStack item, int count, boolean toAllOnline) implements Reward {
    private static final Codec<ItemStack> ITEM = Codec.either(Identifier.CODEC, ItemStack.CODEC).comapFlatMap(
            either -> either.map(
                    id -> BuiltInRegistries.ITEM.getOptional(id).map(item -> DataResult.success(new ItemStack(item)))
                            .orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
                    stack -> DataResult.success(stack.copyWithCount(1))),
            stack -> stack.getComponentsPatch().isEmpty() ? Either.left(BuiltInRegistries.ITEM.getKey(stack.getItem())) : Either.right(stack));

    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ITEM.fieldOf("item").forGetter(ItemReward::item),
            Codec.intRange(1, 1_000_000).optionalFieldOf("count", 1).forGetter(ItemReward::count),
            Codec.BOOL.optionalFieldOf("to_all_online", false).forGetter(ItemReward::toAllOnline))
            .apply(i, ItemReward::new));

    public static final RewardType<ItemReward> TYPE = new RewardType<>() {
        @Override
        public MapCodec<ItemReward> codec() {
            return CODEC;
        }

        @Override
        public boolean claim(RewardContext context, ItemReward reward) {
            List<ServerPlayer> receivers = context.teamReward() && reward.toAllOnline ? context.onlineMembers() : List.of(context.player());
            for (ServerPlayer receiver : receivers) {
                reward.giveTo(receiver);
            }
            return true;
        }
    };

    public ItemReward {
        item = item.copyWithCount(1);
    }

    void giveTo(ServerPlayer player) {
        int left = count;
        while (left > 0) {
            int part = Math.min(left, item.getMaxStackSize());
            player.getInventory().placeItemBackInInventory(item.copyWithCount(part), Prediction.SERVER_ONLY);
            left -= part;
        }
    }

    @Override
    public RewardType<?> type() {
        return TYPE;
    }

    @Override
    public ItemStack icon() {
        return item.copyWithCount(Math.min(count, item.getMaxStackSize()));
    }

    @Override
    public Component title() {
        return Component.translatable("hibiscusquests.reward.item", count, item.getHoverName());
    }
}

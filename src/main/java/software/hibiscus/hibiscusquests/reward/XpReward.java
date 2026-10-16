/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;

// xp: experience points, {type: "xp", amount: 100}. xp_levels: whole levels, {type: "xp_levels", levels: 5}.
public record XpReward(int amount, boolean levels) implements Reward {
    public static final MapCodec<XpReward> POINTS_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(XpReward::amount))
            .apply(i, amount -> new XpReward(amount, false)));
    public static final MapCodec<XpReward> LEVELS_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 10_000).fieldOf("levels").forGetter(XpReward::amount))
            .apply(i, amount -> new XpReward(amount, true)));

    public static final RewardType<XpReward> POINTS = type(POINTS_CODEC);
    public static final RewardType<XpReward> LEVELS = type(LEVELS_CODEC);

    private static RewardType<XpReward> type(MapCodec<XpReward> codec) {
        return new RewardType<>() {
            @Override
            public MapCodec<XpReward> codec() {
                return codec;
            }

            @Override
            public boolean claim(RewardContext context, XpReward reward) {
                if (reward.levels) {
                    context.player().giveExperienceLevels(reward.amount);
                } else {
                    context.player().giveExperiencePoints(reward.amount);
                }
                return true;
            }
        };
    }

    @Override
    public RewardType<?> type() {
        return levels ? LEVELS : POINTS;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public Component title() {
        return Component.translatable(levels ? "hibiscusquests.reward.xp_levels" : "hibiscusquests.reward.xp", amount);
    }
}

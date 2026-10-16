/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;

// advancement: grants an advancement (every criterion it still lacks). {type: "advancement", advancement: "mymod:quests/done"}
public record AdvancementReward(Identifier advancement) implements Reward {
    public static final MapCodec<AdvancementReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("advancement").forGetter(AdvancementReward::advancement))
            .apply(i, AdvancementReward::new));

    public static final RewardType<AdvancementReward> TYPE = new RewardType<>() {
        @Override
        public MapCodec<AdvancementReward> codec() {
            return CODEC;
        }

        @Override
        public boolean claim(RewardContext context, AdvancementReward reward) {
            ServerPlayer player = context.player();
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(reward.advancement);
            if (holder == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
            return true;
        }
    };

    @Override
    public RewardType<?> type() {
        return TYPE;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    @Override
    public Component title() {
        return Component.translatable("hibiscusquests.reward.advancement", advancement.toString());
    }
}

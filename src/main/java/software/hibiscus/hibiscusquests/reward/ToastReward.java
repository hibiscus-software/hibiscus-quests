/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.network.QuestSync;

// toast: just a message, as a toast, to the claimer (or, for a team reward, everyone on the team who's online).
//   {type: "toast", title: "Well done!", message: "The forge remembers.", icon: "minecraft:anvil"}
public record ToastReward(String toastTitle, String message, Optional<ItemStack> toastIcon) implements Reward {
    public static final MapCodec<ToastReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(ToastReward::toastTitle),
            Codec.STRING.optionalFieldOf("message", "").forGetter(ToastReward::message),
            ChoiceReward.STACK.optionalFieldOf("icon").forGetter(ToastReward::toastIcon))
            .apply(i, ToastReward::new));

    public static final RewardType<ToastReward> TYPE = new RewardType<>() {
        @Override
        public MapCodec<ToastReward> codec() {
            return CODEC;
        }

        @Override
        public boolean claim(RewardContext context, ToastReward reward) {
            List<ServerPlayer> to = context.teamReward() ? context.onlineMembers() : List.of(context.player());
            for (ServerPlayer player : to) {
                QuestSync.sendToast(player, BookText.component(reward.toastTitle),
                        reward.message.isBlank() ? Component.empty() : BookText.component(reward.message), reward.icon());
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
        return toastIcon.map(ItemStack::copy).orElseGet(() -> new ItemStack(Items.PAPER));
    }

    @Override
    public Component title() {
        return BookText.component(toastTitle);
    }
}

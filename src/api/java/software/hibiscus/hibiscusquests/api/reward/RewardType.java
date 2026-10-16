/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.reward;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A kind of reward: how it's written in chapter files and how it's handed out. Register one with
 * {@link RewardTypes#register} (or a {@code DeferredRegister} on {@link RewardTypes#REGISTRY_KEY}).
 *
 * @param <R> the reward values this type reads
 */
public interface RewardType<R extends Reward> {
    /**
     * Reads and writes the reward's own fields in a chapter file (its {@code type} field picks the type).
     *
     * @return the codec
     */
    MapCodec<R> codec();

    /**
     * Writes the reward for the network. The default goes through {@link #codec()}.
     *
     * @return the stream codec
     */
    default StreamCodec<RegistryFriendlyByteBuf, R> streamCodec() {
        return ByteBufCodecs.fromCodecWithRegistries(codec().codec());
    }

    /**
     * Hands the reward out. The claim has already been checked (the quest is complete, the reward unclaimed, the
     * {@link software.hibiscus.hibiscusquests.api.event.RewardClaimedEvent.Pre} not cancelled); it's recorded as
     * claimed when this returns true.
     *
     * @param context who's claiming and for which team
     * @param reward  the reward
     * @return true if it was given; false leaves it unclaimed (for example, a choice reward with nothing chosen)
     */
    boolean claim(RewardContext context, R reward);
}

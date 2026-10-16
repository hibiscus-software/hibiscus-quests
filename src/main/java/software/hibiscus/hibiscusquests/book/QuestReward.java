/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.api.book.RewardView;
import software.hibiscus.hibiscusquests.api.reward.Reward;

// A reward in a quest: the fields every reward has (id, once per team or per member, auto-claim, left out of Claim
// all) around the type's own value: {id: "steel", type: "item", item: "arcforge:steel_ingot", count: 16,
// team_reward: true}.
public record QuestReward(String id, boolean teamReward, AutoClaim autoClaim, boolean excludeFromClaimAll, Reward reward)
        implements RewardView {
    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.<QuestReward>mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(QuestReward::id),
            Codec.BOOL.optionalFieldOf("team_reward", false).forGetter(QuestReward::teamReward),
            AutoClaim.CODEC.optionalFieldOf("auto_claim", AutoClaim.OFF).forGetter(QuestReward::autoClaim),
            Codec.BOOL.optionalFieldOf("exclude_from_claim_all", false).forGetter(QuestReward::excludeFromClaimAll),
            BookCodecs.REWARD.forGetter(QuestReward::reward))
            .apply(i, QuestReward::new)).codec();

    public QuestReward withId(String newId) {
        return new QuestReward(newId, teamReward, autoClaim, excludeFromClaimAll, reward);
    }
}

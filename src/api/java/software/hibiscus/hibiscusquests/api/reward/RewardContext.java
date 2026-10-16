/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.reward;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.QuestRef;

/**
 * Who's claiming a reward, for {@link RewardType#claim}.
 *
 * @param player        the player claiming it
 * @param teamId        the id their progress is stored under
 * @param quest         the quest the reward belongs to
 * @param rewardId      the reward's id within the quest
 * @param teamReward    whether it's claimed once for the whole team (rather than by each member)
 * @param onlineMembers the team's online members, including {@code player}
 */
public record RewardContext(ServerPlayer player, UUID teamId, QuestRef quest, String rewardId, boolean teamReward,
        List<ServerPlayer> onlineMembers) {
    /**
     * Creates a context.
     *
     * @param player        the player claiming it
     * @param teamId        the id their progress is stored under
     * @param quest         the quest the reward belongs to
     * @param rewardId      the reward's id within the quest
     * @param teamReward    whether it's claimed once for the whole team
     * @param onlineMembers the team's online members
     */
    public RewardContext {
        onlineMembers = List.copyOf(onlineMembers);
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.progress.QuestTeams;

// Handing a reward out through its type, for quests, reward tables (each rolled entry) and loot crates.
public final class Rewards {
    // The chapter part of the quest reference rewards from a loot crate see: loot_crate/<table id>.
    public static final String LOOT_CRATE_CHAPTER = "loot_crate";

    private Rewards() {}

    // Gives a reward; false if its type declined (nothing chosen) or it failed (logged).
    @SuppressWarnings("unchecked")
    public static <R extends Reward> boolean give(RewardContext context, R reward) {
        try {
            return ((RewardType<R>) reward.type()).claim(context, reward);
        } catch (RuntimeException e) {
            HibiscusQuests.LOGGER.error("Reward {} of {} failed", context.rewardId(), context.quest(), e);
            return false;
        }
    }

    // The context for rewards a player gets from opening a loot crate.
    public static RewardContext crateContext(ServerPlayer player, String table) {
        List<ServerPlayer> online = player.level().getServer() == null ? List.of(player)
                : QuestTeams.onlineMembers(player.level().getServer(), QuestTeams.teamIdOf(player));
        return new RewardContext(player, QuestTeams.teamIdOf(player), new QuestRef(LOOT_CRATE_CHAPTER, table), "crate", false,
                online, -1);
    }
}

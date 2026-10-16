/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One reward of a quest, as written in a chapter file: an immutable value decoded by its {@link RewardType}'s codec.
 * The fields every reward has (its id, team or per-player, auto-claim, claim-all exclusion) are handled by Hibiscus
 * Quests around it.
 */
public interface Reward {
    /**
     * The reward's type, whose codec reads and writes it and which hands it out.
     *
     * @return the type
     */
    RewardType<?> type();

    /**
     * The icon shown in the book's reward slot.
     *
     * @return an item stack, or empty for the type's default
     */
    default ItemStack icon() {
        return ItemStack.EMPTY;
    }

    /**
     * The label shown for the reward, for example "16 × Steel Ingot" or "5 levels".
     *
     * @return the label
     */
    Component title();
}

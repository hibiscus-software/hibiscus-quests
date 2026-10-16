/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.book;

import software.hibiscus.hibiscusquests.api.reward.Reward;

/** A reward in a quest, with the fields every reward has around its type's value. */
public interface RewardView {
    /**
     * The reward's id within its quest.
     *
     * @return the id
     */
    String id();

    /**
     * The reward's type-specific value.
     *
     * @return the reward
     */
    Reward reward();

    /**
     * Whether it's claimed once for the whole team; otherwise each member claims their own.
     *
     * @return true for a team reward
     */
    boolean teamReward();
}

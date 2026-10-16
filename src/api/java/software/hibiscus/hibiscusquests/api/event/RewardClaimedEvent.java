/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import net.neoforged.bus.api.ICancellableEvent;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;

/**
 * A player claims a reward: {@link Pre} before it's handed out (cancel it to stop the claim), {@link Post} after.
 */
public abstract class RewardClaimedEvent extends QuestEvent {
    private final RewardContext context;
    private final Reward reward;

    /**
     * Creates the event.
     *
     * @param context who's claiming, for which team and quest
     * @param reward  the reward
     */
    protected RewardClaimedEvent(RewardContext context, Reward reward) {
        super(context.teamId(), context.player());
        this.context = context;
        this.reward = reward;
    }

    /**
     * Who's claiming, for which team and quest.
     *
     * @return the context
     */
    public RewardContext context() {
        return context;
    }

    /**
     * The reward's quest.
     *
     * @return the quest
     */
    public QuestRef quest() {
        return context.quest();
    }

    /**
     * The reward.
     *
     * @return the reward
     */
    public Reward reward() {
        return reward;
    }

    /** Before the reward is handed out. Cancelling it leaves the reward unclaimed. */
    public static class Pre extends RewardClaimedEvent implements ICancellableEvent {
        /**
         * Creates the event.
         *
         * @param context who's claiming
         * @param reward  the reward
         */
        public Pre(RewardContext context, Reward reward) {
            super(context, reward);
        }
    }

    /** After the reward was handed out and recorded as claimed. */
    public static class Post extends RewardClaimedEvent {
        /**
         * Creates the event.
         *
         * @param context who claimed it
         * @param reward  the reward
         */
        public Post(RewardContext context, Reward reward) {
            super(context, reward);
        }
    }
}

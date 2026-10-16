/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.book;

import java.util.List;

import software.hibiscus.hibiscusquests.api.QuestRef;

/** A quest: its tasks, rewards and dependencies. */
public interface QuestView {
    /**
     * Where the quest is.
     *
     * @return its reference
     */
    QuestRef ref();

    /**
     * The quest's title as written in its file: plain text, a lang key or markup.
     *
     * @return the raw title
     */
    String title();

    /**
     * The quest's tasks, in order.
     *
     * @return an unmodifiable list
     */
    List<? extends TaskView> tasks();

    /**
     * The quest's rewards, in order.
     *
     * @return an unmodifiable list
     */
    List<? extends RewardView> rewards();

    /**
     * The quests this one depends on.
     *
     * @return an unmodifiable list
     */
    List<QuestRef> dependencies();

    /**
     * Whether the quest is optional: it doesn't count toward its chapter's completion.
     *
     * @return true if optional
     */
    boolean optional();
}

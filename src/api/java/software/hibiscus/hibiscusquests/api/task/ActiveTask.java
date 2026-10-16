/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.task;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.QuestRef;

/**
 * A task that's active for an online player (see {@link TaskType}), with the progress of the player's team. Changes go
 * straight to the team's progress on the server: the quest completes, events fire and every online member's book
 * updates. Use it on the server thread, and don't keep it past {@link TaskType#onDeactivated}.
 *
 * @param <T> the task's value type
 */
public interface ActiveTask<T extends Task> {
    /**
     * The player the task is active for.
     *
     * @return the player
     */
    ServerPlayer player();

    /**
     * The id progress is stored under: the player's team, or the player in {@code progress_mode = player}.
     *
     * @return the team id
     */
    UUID teamId();

    /**
     * The quest the task belongs to.
     *
     * @return the quest
     */
    QuestRef quest();

    /**
     * The task's id within its quest.
     *
     * @return the id
     */
    String taskId();

    /**
     * The task's value from the chapter file.
     *
     * @return the task
     */
    T task();

    /**
     * The team's progress, from 0 to {@link Task#target()}.
     *
     * @return the progress
     */
    long progress();

    /**
     * Sets the team's progress, clamped to 0 .. {@link Task#target()}. Does nothing once the handle is inactive.
     *
     * @param progress the new value
     */
    void setProgress(long progress);

    /**
     * Adds to the team's progress, clamped to {@link Task#target()}.
     *
     * @param amount how much to add
     */
    default void addProgress(long amount) {
        if (amount > 0) {
            long current = progress();
            setProgress(amount > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + amount);
        }
    }

    /** Marks the task done: progress becomes the target. */
    default void complete() {
        setProgress(task().target());
    }

    /**
     * Whether the handle still changes progress (false after {@link TaskType#onDeactivated}).
     *
     * @return true while the task is active
     */
    boolean isActive();
}

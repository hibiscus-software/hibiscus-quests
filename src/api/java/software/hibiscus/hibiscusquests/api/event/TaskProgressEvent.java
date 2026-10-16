/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.QuestRef;

/** A task's progress changed for a team. Posted after the change. */
public class TaskProgressEvent extends QuestEvent {
    private final QuestRef quest;
    private final String taskId;
    private final long oldProgress;
    private final long newProgress;
    private final long target;

    /**
     * Creates the event.
     *
     * @param teamId      the team id
     * @param player      the member who made the progress, or null
     * @param quest       the quest
     * @param taskId      the task's id
     * @param oldProgress the progress before
     * @param newProgress the progress now
     * @param target      the task's target
     */
    public TaskProgressEvent(UUID teamId, @Nullable ServerPlayer player, QuestRef quest, String taskId, long oldProgress,
            long newProgress, long target) {
        super(teamId, player);
        this.quest = quest;
        this.taskId = taskId;
        this.oldProgress = oldProgress;
        this.newProgress = newProgress;
        this.target = target;
    }

    /**
     * The task's quest.
     *
     * @return the quest
     */
    public QuestRef quest() {
        return quest;
    }

    /**
     * The task's id within its quest.
     *
     * @return the id
     */
    public String taskId() {
        return taskId;
    }

    /**
     * The progress before the change.
     *
     * @return the old value
     */
    public long oldProgress() {
        return oldProgress;
    }

    /**
     * The progress after the change.
     *
     * @return the new value
     */
    public long newProgress() {
        return newProgress;
    }

    /**
     * The task's target.
     *
     * @return the target
     */
    public long target() {
        return target;
    }

    /**
     * Whether this change finished the task.
     *
     * @return true if the task reached its target now
     */
    public boolean finished() {
        return oldProgress < target && newProgress >= target;
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A snapshot of a team's progress on one quest.
 *
 * @param tasks       each task's progress, by task id
 * @param fraction    how far through the quest's non-optional tasks the team is, from 0 to 1
 * @param started     when the first progress was made (milliseconds since the epoch), if any
 * @param completed   when the quest was completed (milliseconds since the epoch), if it is
 * @param completedBy the member whose progress completed it, if known
 * @param repeats     how many times a repeatable quest has been completed and reset
 */
public record QuestStatus(Map<String, Long> tasks, double fraction, Optional<Long> started, Optional<Long> completed,
        Optional<UUID> completedBy, int repeats) {
    /** A quest with no progress. */
    public static final QuestStatus NONE = new QuestStatus(Map.of(), 0, Optional.empty(), Optional.empty(), Optional.empty(), 0);

    /**
     * Creates a snapshot.
     *
     * @param tasks       each task's progress, by task id
     * @param fraction    how far through the quest the team is
     * @param started     when the first progress was made
     * @param completed   when the quest was completed
     * @param completedBy the member who completed it
     * @param repeats     times completed and reset
     */
    public QuestStatus {
        tasks = Map.copyOf(tasks);
    }

    /**
     * Whether the quest is complete.
     *
     * @return true if complete
     */
    public boolean isCompleted() {
        return completed.isPresent();
    }

    /**
     * A task's progress.
     *
     * @param taskId the task's id
     * @return its progress, 0 if none
     */
    public long task(String taskId) {
        return tasks.getOrDefault(taskId, 0L);
    }
}

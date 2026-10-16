/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

/**
 * How far a team is through a chapter.
 *
 * @param completed how many of its non-optional quests are complete
 * @param total     how many non-optional quests it has
 */
public record ChapterStatus(int completed, int total) {
    /**
     * Whether every non-optional quest is complete (a chapter with none counts as complete).
     *
     * @return true if complete
     */
    public boolean isCompleted() {
        return completed >= total;
    }

    /**
     * The completed share, from 0 to 1 (1 for a chapter with no non-optional quests).
     *
     * @return the fraction
     */
    public double fraction() {
        return total == 0 ? 1 : (double) completed / total;
    }
}

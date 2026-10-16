/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.book;

import software.hibiscus.hibiscusquests.api.task.Task;

/** A task in a quest, with the fields every task has around its type's value. */
public interface TaskView {
    /**
     * The task's id within its quest (what {@link software.hibiscus.hibiscusquests.api.QuestService#addProgress}
     * takes).
     *
     * @return the id
     */
    String id();

    /**
     * The task's type-specific value.
     *
     * @return the task
     */
    Task task();

    /**
     * Whether the task is optional: the quest completes without it.
     *
     * @return true if optional
     */
    boolean optional();
}

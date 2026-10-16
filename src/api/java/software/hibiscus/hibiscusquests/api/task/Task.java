/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.task;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One task of a quest, as written in a chapter file: an immutable value decoded by its {@link TaskType}'s codec (an
 * item task's item and count, a kill task's entity). Progress is a {@code long} counter per team that runs from 0 to
 * {@link #target()}; the task is done when it reaches the target.
 *
 * <p>The fields every task has (its id, a title or icon override, optional, hidden until started) are handled by
 * Hibiscus Quests around the task and aren't part of this value.
 */
public interface Task {
    /**
     * The task's type, whose codec reads and writes it.
     *
     * @return the type
     */
    TaskType<?> type();

    /**
     * The value progress counts up to: an item count, a number of kills, 1 for a yes/no task.
     *
     * @return the target, at least 1
     */
    default long target() {
        return 1;
    }

    /**
     * Whether progress made while the quest is still locked counts toward it, so the quest completes as soon as it
     * unlocks. A chapter file can override this per task ({@code counts_while_locked}).
     *
     * @return true to track the task while its quest is locked
     */
    default boolean countsWhileLocked() {
        return false;
    }

    /**
     * The icon shown when the task has no icon of its own.
     *
     * @return an item stack, or empty for the type's default
     */
    default ItemStack icon() {
        return ItemStack.EMPTY;
    }

    /**
     * The label shown when the task has no title of its own, for example "16 × Steel Ingot".
     *
     * @return the label
     */
    Component title();
}

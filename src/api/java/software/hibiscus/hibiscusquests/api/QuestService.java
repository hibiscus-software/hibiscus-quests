/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.book.BookView;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

/**
 * Hibiscus Quests on the server: the book, each team's progress, and progress pushes. Get it from
 * {@link QuestsApi#get()}. Every method is for the server thread while a server is running.
 *
 * <p>Progress belongs to a <em>team id</em>: the player's team from the Hibiscus Lib Teams API (their party with
 * Hibiscus Teams, else their own solo team, whose id is their UUID), or the player's UUID when the book's
 * {@code progress_mode} is {@code player}. {@link #teamIdOf(ServerPlayer)} gives the right one.
 */
public interface QuestService {
    /**
     * The book as it is now.
     *
     * @return the book
     */
    BookView book();

    /**
     * The id a player's progress is stored under.
     *
     * @param player the player
     * @return their team id
     */
    UUID teamIdOf(ServerPlayer player);

    /**
     * Whether a team has completed a quest.
     *
     * @param teamId the team id
     * @param quest  the quest
     * @return true if complete (false for an unknown quest)
     */
    boolean isCompleted(UUID teamId, QuestRef quest);

    /**
     * A team's progress on a quest.
     *
     * @param teamId the team id
     * @param quest  the quest
     * @return the progress (all zero for an unknown quest)
     */
    QuestStatus progress(UUID teamId, QuestRef quest);

    /**
     * How many of a chapter's non-optional quests a team has completed.
     *
     * @param teamId    the team id
     * @param chapterId the chapter's id
     * @return completed and total quests
     */
    ChapterStatus chapterProgress(UUID teamId, String chapterId);

    /**
     * Adds progress to a task for the player's team, for mods that track something themselves rather than defining a
     * task type. Ignored when the task isn't being tracked (its quest is locked and the task doesn't count while
     * locked, or the quest is already complete).
     *
     * @param player the player who made the progress
     * @param quest  the quest
     * @param taskId the task's id within the quest
     * @param amount how much to add
     * @return true if progress was added
     */
    boolean addProgress(ServerPlayer player, QuestRef quest, String taskId, long amount);

    /**
     * Completes every task of a quest for the player's team, as if they had done them (it still only completes once
     * it's unlocked).
     *
     * @param player the player
     * @param quest  the quest
     * @return true if the quest exists
     */
    boolean complete(ServerPlayer player, QuestRef quest);

    /**
     * The tasks of a type that are active for an online player now (see {@link TaskType}), for a type that tracks
     * progress from events.
     *
     * @param player the player
     * @param type   the task type
     * @param <T>    the task values the type reads
     * @return the active tasks, possibly empty
     */
    <T extends Task> List<ActiveTask<T>> activeTasks(ServerPlayer player, TaskType<T> type);
}

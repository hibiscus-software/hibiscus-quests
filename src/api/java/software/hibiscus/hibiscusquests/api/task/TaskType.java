/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.task;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.Map;
import software.hibiscus.hibiscusquests.api.editor.FieldHint;

/**
 * A kind of task: how it's written in chapter files, and how the server tracks it. Register one with
 * {@link TaskTypes#register} (or a {@code DeferredRegister} on {@link TaskTypes#REGISTRY_KEY}).
 *
 * <p>The server tracks a task only while it's <em>active</em> for a player: the player is online, the task isn't
 * done, its quest isn't complete, and the quest is unlocked (or the task {@linkplain Task#countsWhileLocked() counts
 * while locked}). A type tracks progress in any of three ways, mixed as it likes:
 * <ul>
 *   <li><b>events</b>: subscribe to a NeoForge event yourself and update the player's active tasks of your type
 *       through {@link software.hibiscus.hibiscusquests.api.QuestService#activeTasks};</li>
 *   <li><b>polling</b>: return a {@link #pollInterval()} and check the player in {@link #poll};</li>
 *   <li><b>submitting</b>: return true from {@link #canSubmit} and act when the player presses Submit in the book.</li>
 * </ul>
 * {@link #onActivated} and {@link #onDeactivated} bracket the time a task is active for a player, for types that
 * attach listeners (criterion tasks do).
 *
 * @param <T> the task values this type reads
 */
public interface TaskType<T extends Task> {
    /**
     * Reads and writes the task's own fields in a chapter file (the task's {@code type} field picks the type, and the
     * fields every task has are read around it).
     *
     * @return the codec
     */
    MapCodec<T> codec();

    /**
     * Writes the task for the network. The default goes through {@link #codec()}.
     *
     * @return the stream codec
     */
    default StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
        return ByteBufCodecs.fromCodecWithRegistries(codec().codec());
    }

    /**
     * How often, in ticks, {@link #poll} runs for each player's active tasks of this type. 0 never polls.
     *
     * @return the interval in ticks, or 0
     */
    default int pollInterval() {
        return 0;
    }

    /**
     * Checks one active task, every {@link #pollInterval()} ticks.
     *
     * @param task the active task
     */
    default void poll(ActiveTask<T> task) {}

    /**
     * Whether the book shows a Submit button (or checkbox) for this task.
     *
     * @param task the task
     * @return true if players submit it by hand
     */
    default boolean canSubmit(T task) {
        return false;
    }

    /**
     * The player pressed Submit for an active task. Only called when {@link #canSubmit} is true; the request has
     * already been checked (the player's team, the task being active, the rate limit).
     *
     * @param task the active task
     */
    default void submit(ActiveTask<T> task) {}

    /**
     * A task became active for an online player: they joined, the quest unlocked, the book was reloaded or they
     * changed team. Also a good moment to check state the player already has (an advancement they've earned).
     *
     * @param task the active task
     */
    default void onActivated(ActiveTask<T> task) {}

    /**
     * A task stopped being active for a player: it's done, the quest completed or was reset, the player left or
     * changed team, or the book was reloaded. The handle can't change progress any more.
     *
     * @param task the task that was active
     */
    default void onDeactivated(ActiveTask<T> task) {}

    /**
     * How the book's editor edits this type's fields, by field name; fields without a hint get a control picked from
     * their current value (or a text box for their SNBT). See {@link FieldHint}.
     *
     * @return hints by field name; empty by default
     */
    default Map<String, FieldHint> editorHints() {
        return Map.of();
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.editor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * Custom editors for task and reward types, on the client. A type without one is edited with a form the book builds
 * from its codec and {@linkplain software.hibiscus.hibiscusquests.api.task.TaskType#editorHints() hints}, which is
 * usually enough. Register from client code (your {@code @Mod(dist = Dist.CLIENT)} class), and only when Hibiscus
 * Quests is installed.
 */
public final class CustomEditors {
    private static final Map<Identifier, CustomEditor> TASKS = new ConcurrentHashMap<>();
    private static final Map<Identifier, CustomEditor> REWARDS = new ConcurrentHashMap<>();

    private CustomEditors() {}

    /**
     * Uses an editor of your own for a task type.
     *
     * @param type   the task type's registry id
     * @param editor the editor
     */
    public static void registerTask(Identifier type, CustomEditor editor) {
        TASKS.put(type, editor);
    }

    /**
     * Uses an editor of your own for a reward type.
     *
     * @param type   the reward type's registry id
     * @param editor the editor
     */
    public static void registerReward(Identifier type, CustomEditor editor) {
        REWARDS.put(type, editor);
    }

    /**
     * The custom editor for a task type, if one was registered.
     *
     * @param type the task type's registry id
     * @return the editor
     */
    public static Optional<CustomEditor> task(Identifier type) {
        return Optional.ofNullable(TASKS.get(type));
    }

    /**
     * The custom editor for a reward type, if one was registered.
     *
     * @param type the reward type's registry id
     * @return the editor
     */
    public static Optional<CustomEditor> reward(Identifier type) {
        return Optional.ofNullable(REWARDS.get(type));
    }
}

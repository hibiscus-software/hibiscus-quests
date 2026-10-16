/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// checkmark: done when the player ticks it in the book, for read-and-acknowledge steps. {type: "checkmark"}
public record CheckmarkTask() implements Task {
    public static final CheckmarkTask INSTANCE = new CheckmarkTask();
    public static final MapCodec<CheckmarkTask> CODEC = MapCodec.unit(INSTANCE);

    public static final TaskType<CheckmarkTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<CheckmarkTask> codec() {
            return CODEC;
        }

        @Override
        public boolean canSubmit(CheckmarkTask task) {
            return true;
        }

        @Override
        public void submit(ActiveTask<CheckmarkTask> task) {
            task.complete();
        }
    };

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public Component title() {
        return Component.translatable("hibiscusquests.task.checkmark");
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.api.book.TaskView;
import software.hibiscus.hibiscusquests.api.task.Task;

// A task in a quest: the fields every task has (id, title and icon overrides, optional, hidden until the quest has
// started, whether locked progress counts) around the type's own value, all in one compound in the file:
// {id: "steel", type: "item", item: "arcforge:steel_ingot", count: 16L, optional_task: true}.
public record QuestTask(String id, Optional<String> title, Optional<BookIcon> icon, boolean optional,
        boolean hideUntilStarted, Optional<Boolean> countsWhileLockedOverride, Task task) implements TaskView {
    public static final Codec<QuestTask> CODEC = RecordCodecBuilder.<QuestTask>mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(QuestTask::id),
            Codec.STRING.optionalFieldOf("title").forGetter(QuestTask::title),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(QuestTask::icon),
            Codec.BOOL.optionalFieldOf("optional_task", false).forGetter(QuestTask::optional),
            Codec.BOOL.optionalFieldOf("hide_until_started", false).forGetter(QuestTask::hideUntilStarted),
            Codec.BOOL.optionalFieldOf("counts_while_locked").forGetter(QuestTask::countsWhileLockedOverride),
            BookCodecs.TASK.forGetter(QuestTask::task))
            .apply(i, QuestTask::new)).codec();

    // Whether progress made while the quest is locked counts: the file's override, else the type's default.
    public boolean countsWhileLocked() {
        return countsWhileLockedOverride.orElseGet(task::countsWhileLocked);
    }

    public long target() {
        return Math.max(1, task.target());
    }

    public QuestTask withId(String newId) {
        return new QuestTask(newId, title, icon, optional, hideUntilStarted, countsWhileLockedOverride, task);
    }
}

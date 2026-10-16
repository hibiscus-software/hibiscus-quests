/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.QuestService;
import software.hibiscus.hibiscusquests.api.QuestStatus;
import software.hibiscus.hibiscusquests.api.book.BookView;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;

// The public API (QuestsApi.get()) over BookManager and ProgressManager.
public final class QuestServiceImpl implements QuestService {
    public static final QuestServiceImpl INSTANCE = new QuestServiceImpl();

    private QuestServiceImpl() {}

    @Override
    public BookView book() {
        return BookManager.book();
    }

    @Override
    public UUID teamIdOf(ServerPlayer player) {
        return QuestTeams.teamIdOf(player);
    }

    @Override
    public boolean isCompleted(UUID teamId, QuestRef quest) {
        return ProgressManager.progress(teamId).isCompleted(quest);
    }

    @Override
    public QuestStatus progress(UUID teamId, QuestRef ref) {
        Quest quest = BookManager.book().quest(ref).orElse(null);
        TeamProgress progress = ProgressManager.progress(teamId);
        QuestState state = progress.state(ref);
        if (quest == null || state == null) {
            return QuestStatus.NONE;
        }
        Map<String, Long> tasks = new HashMap<>(state.tasks());
        return new QuestStatus(tasks, QuestLogic.fraction(progress, quest), positive(state.started()), positive(state.completed()),
                state.completedBy(), state.repeats());
    }

    private static Optional<Long> positive(long value) {
        return value > 0 ? Optional.of(value) : Optional.empty();
    }

    @Override
    public ChapterStatus chapterProgress(UUID teamId, String chapterId) {
        return QuestLogic.chapterStatus(BookManager.book(), ProgressManager.progress(teamId), chapterId);
    }

    @Override
    public boolean addProgress(ServerPlayer player, QuestRef ref, String taskId, long amount) {
        QuestBook book = BookManager.book();
        Quest quest = book.quest(ref).orElse(null);
        QuestTask task = quest == null ? null : quest.task(taskId).orElse(null);
        if (task == null || amount <= 0) {
            return false;
        }
        UUID teamId = QuestTeams.teamIdOf(player);
        TeamProgress progress = ProgressManager.progress(teamId);
        boolean tracked = QuestLogic.activeTasks(book, progress, quest, QuestClock.now()).contains(task);
        if (!tracked) {
            return false;
        }
        long current = progress.task(ref, taskId);
        return ProgressManager.setTaskProgress(teamId, quest, task, amount > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + amount, player);
    }

    @Override
    public boolean complete(ServerPlayer player, QuestRef ref) {
        Optional<Quest> quest = BookManager.book().quest(ref);
        quest.ifPresent(q -> ProgressManager.completeTasks(QuestTeams.teamIdOf(player), q, player));
        return quest.isPresent();
    }

    @Override
    public <T extends Task> List<ActiveTask<T>> activeTasks(ServerPlayer player, TaskType<T> type) {
        return ActiveTasks.of(player, type);
    }
}

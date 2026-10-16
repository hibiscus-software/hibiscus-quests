/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ProgressionMode;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;

// The rules, as pure functions of the book and a team's progress, so the server and the client's node states agree:
// when a quest is unlocked, done, and which of its tasks are being tracked. now is QuestClock.now().
//
// A quest is unlocked when its chapter's gate (requires) is met, the quest before it is complete in a
// require_sequential chapter, its dependencies satisfy its dependency_mode, no one_of sibling has been completed,
// and a repeatable quest isn't cooling down. It completes once it's unlocked and every non-optional task has reached
// its target.
public final class QuestLogic {
    private QuestLogic() {}

    public static boolean isUnlocked(QuestBook book, TeamProgress progress, Quest quest, long now) {
        return lockReasons(book, progress, quest, now, false).isEmpty();
    }

    // Why a quest is locked (empty if it isn't). With all=false it stops at the first reason.
    public static List<Component> lockReasons(QuestBook book, TeamProgress progress, Quest quest, long now, boolean all) {
        List<Component> reasons = new ArrayList<>();
        for (QuestBook.Requirement requirement : book.requirements(quest.chapter())) {
            if (requirement.chapter().isPresent() && !chapterStatus(book, progress, requirement.chapter().get()).isCompleted()) {
                reasons.add(Component.translatable("hibiscusquests.lock.chapter_requires_chapter", requirement.chapter().get()));
            } else if (requirement.quest().isPresent() && !progress.isCompleted(requirement.quest().get())) {
                reasons.add(Component.translatable("hibiscusquests.lock.chapter_requires_quest", requirement.quest().get().toString()));
            }
            if (!all && !reasons.isEmpty()) {
                return reasons;
            }
        }
        Chapter chapter = book.chapter(quest.chapter()).orElse(null);
        if (chapter != null && chapter.info().requireSequential()) {
            book.previousInChapter(quest).filter(previous -> !progress.isCompleted(previous.ref()))
                    .ifPresent(previous -> reasons.add(Component.translatable("hibiscusquests.lock.sequential", previous.ref().toString())));
            if (!all && !reasons.isEmpty()) {
                return reasons;
            }
        }
        List<QuestRef> dependencies = quest.dependencies();
        if (!dependencies.isEmpty()) {
            int done = (int) dependencies.stream().filter(progress::isCompleted).count();
            int needed = switch (quest.rules().dependencyMode()) {
                case ALL -> dependencies.size();
                case ANY, ONE_OF -> 1;
                case MIN -> Math.min(Math.max(1, quest.rules().minRequired()), dependencies.size());
            };
            if (done < needed) {
                List<String> missing = dependencies.stream().filter(ref -> !progress.isCompleted(ref)).map(QuestRef::toString).toList();
                reasons.add(Component.translatable("hibiscusquests.lock.dependencies", done, needed, String.join(", ", missing)));
                if (!all) {
                    return reasons;
                }
            }
        }
        for (QuestRef sibling : book.oneOfSiblings(quest.ref())) {
            if (progress.isCompleted(sibling)) {
                reasons.add(Component.translatable("hibiscusquests.lock.branch_taken", sibling.toString()));
                if (!all) {
                    return reasons;
                }
                break;
            }
        }
        QuestState state = progress.state(quest.ref());
        if (state != null && !state.isCompleted() && state.cooldownUntil() > now) {
            reasons.add(Component.translatable("hibiscusquests.lock.cooldown", (state.cooldownUntil() - now + 999) / 1000));
        }
        return reasons;
    }

    // Every non-optional task at its target (a quest with no tasks is done as soon as it unlocks).
    public static boolean tasksDone(TeamProgress progress, Quest quest) {
        for (QuestTask task : quest.tasks()) {
            if (!task.optional() && progress.task(quest.ref(), task.id()) < task.target()) {
                return false;
            }
        }
        return true;
    }

    // The tasks being tracked for the team now: none once the quest is complete or cooling down; while it's
    // locked, only tasks whose progress counts while locked; in a linear quest, only the first unfinished task.
    public static List<QuestTask> activeTasks(QuestBook book, TeamProgress progress, Quest quest, long now) {
        QuestState state = progress.state(quest.ref());
        if (state != null && (state.isCompleted() || state.cooldownUntil() > now)) {
            return List.of();
        }
        boolean unlocked = isUnlocked(book, progress, quest, now);
        List<QuestTask> active = new ArrayList<>();
        for (QuestTask task : quest.tasks()) {
            if (progress.task(quest.ref(), task.id()) >= task.target()) {
                continue;
            }
            if (unlocked || task.countsWhileLocked()) {
                active.add(task);
            }
            if (quest.rules().progressionMode() == ProgressionMode.LINEAR) {
                break;
            }
        }
        return active;
    }

    // How far through its non-optional tasks a quest is, from 0 to 1 (1 if it's complete).
    public static double fraction(TeamProgress progress, Quest quest) {
        if (progress.isCompleted(quest.ref())) {
            return 1;
        }
        double sum = 0;
        int count = 0;
        for (QuestTask task : quest.tasks()) {
            if (!task.optional()) {
                sum += Math.min(1.0, (double) progress.task(quest.ref(), task.id()) / task.target());
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    public static ChapterStatus chapterStatus(QuestBook book, TeamProgress progress, String chapterId) {
        Chapter chapter = book.chapter(chapterId).orElse(null);
        if (chapter == null) {
            return new ChapterStatus(0, 0);
        }
        int total = 0;
        int done = 0;
        for (Quest quest : chapter.quests()) {
            if (!quest.optional()) {
                total++;
                if (progress.isCompleted(quest.ref())) {
                    done++;
                }
            }
        }
        return new ChapterStatus(done, total);
    }
}

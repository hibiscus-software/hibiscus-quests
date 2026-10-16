/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.book.QuestView;

// A quest: one node on a chapter's canvas, with its tasks and rewards. The chapter id isn't in the quest's compound
// (it's the file the quest is in); the chapter fills it in when it's built, and resolves the dependencies against it.
public record Quest(String chapter, String id, QuestDisplay display, QuestRules rules, List<QuestTask> tasks,
        List<QuestReward> rewards) implements QuestView {
    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
            BookCodecs.ID.fieldOf("id").forGetter(Quest::id),
            QuestDisplay.MAP_CODEC.forGetter(Quest::display),
            QuestRules.MAP_CODEC.forGetter(Quest::rules),
            BookCodecs.list("tasks", QuestTask.CODEC).forGetter(Quest::tasks),
            BookCodecs.list("rewards", QuestReward.CODEC).forGetter(Quest::rewards))
            .apply(i, (id, display, rules, tasks, rewards) -> new Quest("", id, display, rules, tasks, rewards)));

    public Quest {
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }

    // The same quest in a chapter, with task and reward ids filled in where the file left them out (task_1, reward_1).
    Quest inChapter(String chapterId) {
        List<QuestTask> namedTasks = new ArrayList<>();
        for (int n = 0; n < tasks.size(); n++) {
            QuestTask task = tasks.get(n);
            namedTasks.add(task.id().isEmpty() ? task.withId(freeId("task_", n + 1, tasks.stream().map(QuestTask::id).toList())) : task);
        }
        List<QuestReward> namedRewards = new ArrayList<>();
        for (int n = 0; n < rewards.size(); n++) {
            QuestReward reward = rewards.get(n);
            namedRewards.add(reward.id().isEmpty() ? reward.withId(freeId("reward_", n + 1, rewards.stream().map(QuestReward::id).toList())) : reward);
        }
        return new Quest(chapterId, id, display, rules, namedTasks, namedRewards);
    }

    private static String freeId(String prefix, int number, List<String> taken) {
        String id = prefix + number;
        int suffix = 2;
        while (taken.contains(id)) {
            id = prefix + number + "_" + suffix++;
        }
        return id;
    }

    @Override
    public QuestRef ref() {
        return new QuestRef(chapter, id);
    }

    @Override
    public String title() {
        return display.title();
    }

    // Dependencies that parse; the book drops (and logs) the ones that point nowhere.
    @Override
    public List<QuestRef> dependencies() {
        List<QuestRef> refs = new ArrayList<>();
        for (String text : rules.dependencies()) {
            QuestRef.parseRelative(text, chapter).ifPresent(refs::add);
        }
        return refs;
    }

    @Override
    public boolean optional() {
        return rules.optional();
    }

    public Optional<QuestTask> task(String taskId) {
        return tasks.stream().filter(task -> task.id().equals(taskId)).findFirst();
    }

    public Optional<QuestReward> reward(String rewardId) {
        return rewards.stream().filter(reward -> reward.id().equals(rewardId)).findFirst();
    }

    Quest withDependencies(List<String> dependencies) {
        QuestRules r = rules;
        return new Quest(chapter, id, display, new QuestRules(dependencies, r.dependencyMode(), r.minRequired(), r.optional(),
                r.repeatable(), r.repeatCooldown(), r.canRepeatWithoutClaiming(), r.ignoreRewardBlocking(), r.progressionMode()),
                tasks, rewards);
    }
}

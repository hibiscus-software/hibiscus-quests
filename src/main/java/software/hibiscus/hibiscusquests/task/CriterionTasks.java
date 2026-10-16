/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;

// Criterion tasks without advancements. In 26.3 a trigger looks up, on the player's PlayerAdvancements, the trigger
// instances listening to it (a map from (advancement, criterion) keys to instances), tests each, and calls
// award(advancement, criterion) for the ones that match. So for each active criterion task an online player has, this
// puts the task's trigger instance in that map under a key whose advancement is a stand-in made here (id
// hibiscusquests:criterion/<chapter>/<quest>/<task>) that isn't in the server's advancement tree.
// PlayerAdvancementsMixin catches award() for those stand-ins before vanilla does anything and counts the task
// instead: nothing is written to the player's advancement progress, sent to the advancements screen or announced.
// The key leaves the map when the task stops being active (done, quest complete, logout, book reload).
public final class CriterionTasks {
    public static final String CRITERION_NAME = "task";
    private static final String PATH = "criterion/";

    // A criterion task's parsed trigger and the stand-in advancement its key uses.
    private record Binding(QuestRef quest, String taskId, AdvancementHolder holder, Criterion<?> criterion) {}

    private static volatile Map<Identifier, Binding> bindings = Map.of();
    // What each active task put in its player's map, so exactly that comes out again even if the book changed since.
    private static final Map<ActiveTask<CriterionTask>, Binding> ATTACHED = new IdentityHashMap<>();

    private CriterionTasks() {}

    // Parses every criterion task in the book with its trigger's codec. One that won't parse is reported and
    // never fires.
    public static void rebuild(QuestBook book, DynamicOps<Tag> ops, Consumer<String> warnings) {
        Map<Identifier, Binding> built = new HashMap<>();
        for (Quest quest : book.quests()) {
            for (QuestTask entry : quest.tasks()) {
                if (!(entry.task() instanceof CriterionTask task)) {
                    continue;
                }
                DataResult<Criterion<?>> parsed = Criterion.CODEC.parse(ops, task.criterion());
                Optional<Criterion<?>> criterion = parsed.result();
                if (criterion.isEmpty()) {
                    warnings.accept("Quest " + quest.ref() + ", task " + entry.id() + ": the criterion won't load ("
                            + parsed.error().map(DataResult.Error::message).orElse("?") + "); it can't be completed");
                    continue;
                }
                Identifier id = holderId(quest.ref(), entry.id());
                Advancement standIn = new Advancement(Optional.empty(), Optional.empty(), AdvancementRewards.EMPTY,
                        Map.of(CRITERION_NAME, criterion.get()), AdvancementRequirements.allOf(List.of(CRITERION_NAME)), false);
                built.put(id, new Binding(quest.ref(), entry.id(), new AdvancementHolder(id, standIn), criterion.get()));
            }
        }
        bindings = Map.copyOf(built);
    }

    // Whether a criterion task's criterion parsed (for tests and /hibiscusquests debug).
    public static boolean isBound(QuestRef quest, String taskId) {
        return bindings.containsKey(holderId(quest, taskId));
    }

    private static Identifier holderId(QuestRef quest, String taskId) {
        return HibiscusQuests.id(PATH + quest.chapter() + "/" + quest.quest() + "/" + taskId);
    }

    static void listen(ActiveTask<CriterionTask> task) {
        Binding binding = bindings.get(holderId(task.quest(), task.taskId()));
        if (binding != null) {
            access(task.player()).hibiscusquests$addCriterionListener(binding.criterion(), key(binding));
            ATTACHED.put(task, binding);
        }
    }

    static void stopListening(ActiveTask<CriterionTask> task) {
        Binding binding = ATTACHED.remove(task);
        if (binding != null) {
            access(task.player()).hibiscusquests$removeCriterionListener(binding.criterion().trigger(), key(binding));
        }
    }

    private static PlayerAdvancements.TriggerInstanceKey key(Binding binding) {
        return new PlayerAdvancements.TriggerInstanceKey(binding.holder(), CRITERION_NAME);
    }

    private static PlayerAdvancementsAccess access(ServerPlayer player) {
        return (PlayerAdvancementsAccess) player.getAdvancements();
    }

    // Called from PlayerAdvancementsMixin at the start of award(). Returns true if the advancement is one of the
    // stand-ins, in which case the trigger counts toward the player's task and vanilla's award() is skipped.
    public static boolean onAward(ServerPlayer player, AdvancementHolder holder) {
        Identifier id = holder.id();
        if (!id.getNamespace().equals(HibiscusQuests.MODID) || !id.getPath().startsWith(PATH)) {
            return false;
        }
        Binding binding = bindings.get(id);
        if (binding == null) {
            return true; // a stand-in from a book that's since been replaced: swallow it
        }
        for (ActiveTask<CriterionTask> task : QuestsApi.get().activeTasks(player, CriterionTask.TYPE)) {
            if (task.quest().equals(binding.quest()) && task.taskId().equals(binding.taskId())) {
                task.addProgress(1);
                break;
            }
        }
        return true;
    }
}

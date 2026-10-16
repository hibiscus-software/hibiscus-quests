/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// criterion: any advancement trigger, vanilla or modded, fires with matching conditions, written the way an advancement
// writes a criterion, counted count times:
//   {type: "criterion", criterion: {trigger: "minecraft:placed_block", conditions: {location: [...]}}}
//   {type: "criterion", count: 3L,
//    criterion: {trigger: "arcforge:multiblock_formed", conditions: {multiblock: "arcforge:arcforge_furnace"}}}
// It's the hook that covers every mod's own triggers with no code in that mod. Nothing is written to the player's
// advancements: CriterionTasks listens to the trigger directly (see PlayerAdvancementsMixin). The criterion is kept as
// written and parsed with the trigger's own codec on the server, so the client never needs the trigger's types.
public record CriterionTask(CompoundTag criterion, long count) implements Task {
    public static final MapCodec<CriterionTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CompoundTag.CODEC.validate(tag -> tag.getString("trigger").isPresent() ? DataResult.success(tag)
                    : DataResult.error(() -> "A criterion needs a trigger")).fieldOf("criterion").forGetter(CriterionTask::criterion),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "count must be at least 1"))
                    .optionalFieldOf("count", 1L).forGetter(CriterionTask::count))
            .apply(i, CriterionTask::new));

    public static final TaskType<CriterionTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<CriterionTask> codec() {
            return CODEC;
        }

        @Override
        public void onActivated(ActiveTask<CriterionTask> task) {
            CriterionTasks.listen(task);
        }

        @Override
        public void onDeactivated(ActiveTask<CriterionTask> task) {
            CriterionTasks.stopListening(task);
        }
    };

    public CriterionTask {
        criterion = criterion.copy();
    }

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return count;
    }

    // Triggers usually fire once per deed, and a quest might list the deed before it unlocks, so they count
    // while locked.
    @Override
    public boolean countsWhileLocked() {
        return true;
    }

    public String trigger() {
        return criterion.getStringOr("trigger", "?");
    }

    @Override
    public Component title() {
        return count > 1 ? Component.translatable("hibiscusquests.task.criterion.count", trigger(), count)
                : Component.translatable("hibiscusquests.task.criterion", trigger());
    }
}

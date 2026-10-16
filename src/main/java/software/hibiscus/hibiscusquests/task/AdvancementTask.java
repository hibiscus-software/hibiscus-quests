/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// advancement: a team member has an advancement, or one criterion of it.
//   {type: "advancement", advancement: "minecraft:story/mine_diamond"}
//   {type: "advancement", advancement: "minecraft:adventure/adventuring_time", criterion: "minecraft:jungle"}
// Checked when the task starts (so one earned earlier counts) and whenever the player makes advancement progress.
public record AdvancementTask(Identifier advancement, Optional<String> criterion) implements Task {
    public static final MapCodec<AdvancementTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("advancement").forGetter(AdvancementTask::advancement),
            Codec.STRING.optionalFieldOf("criterion").forGetter(AdvancementTask::criterion))
            .apply(i, AdvancementTask::new));

    public static final Type TYPE = new Type();

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public Component title() {
        return criterion.<Component>map(c -> Component.translatable("hibiscusquests.task.advancement.criterion", c, advancement.toString()))
                .orElseGet(() -> Component.translatable("hibiscusquests.task.advancement", advancement.toString()));
    }

    // Whether the player has it now.
    public boolean isDone(ServerPlayer player) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(advancement);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (criterion.isEmpty()) {
            return progress.isDone();
        }
        CriterionProgress part = progress.getCriterion(criterion.get());
        return part != null && part.isDone();
    }

    public static final class Type implements TaskType<AdvancementTask> {
        private Type() {}

        @Override
        public MapCodec<AdvancementTask> codec() {
            return CODEC;
        }

        @Override
        public void onActivated(ActiveTask<AdvancementTask> task) {
            if (task.task().isDone(task.player())) {
                task.complete();
            }
        }

        // NeoForge's AdvancementEarnEvent only fires for advancements with a display, so progress events are watched.
        public void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
            if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT
                    || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            Identifier id = event.getAdvancement().id();
            for (ActiveTask<AdvancementTask> task : QuestsApi.get().activeTasks(player, this)) {
                if (task.task().advancement.equals(id) && task.task().isDone(player)) {
                    task.complete();
                }
            }
        }
    }
}

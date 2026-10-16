/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// stat: one of a member's vanilla statistics reaches a value (the stat as counted since they first joined).
// stat_type is the kind of statistic, stat what it counts: blocks mined, items crafted, custom stats like jumps or
// distance walked (in cm).
//   {type: "stat", stat_type: "minecraft:custom", stat: "minecraft:jump", count: 100L}
//   {type: "stat", stat_type: "minecraft:mined", stat: "minecraft:stone", count: 500L}
public record StatTask(Identifier statType, Identifier stat, long count) implements Task {
    public static final MapCodec<StatTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("stat_type", Identifier.withDefaultNamespace("custom")).forGetter(StatTask::statType),
            Identifier.CODEC.fieldOf("stat").forGetter(StatTask::stat),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "count must be at least 1"))
                    .optionalFieldOf("count", 1L).forGetter(StatTask::count))
            .apply(i, StatTask::new));

    public static final TaskType<StatTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<StatTask> codec() {
            return CODEC;
        }

        @Override
        public int pollInterval() {
            return 20;
        }

        @Override
        public void poll(ActiveTask<StatTask> task) {
            task.task().resolve().ifPresent(stat -> {
                long value = Math.min(task.task().count, task.player().getStats().getValue(stat));
                if (value > task.progress()) {
                    task.setProgress(value);
                }
            });
        }

        @Override
        public void onActivated(ActiveTask<StatTask> task) {
            poll(task);
        }
    };

    // The vanilla Stat, if the type and value exist.
    public Optional<Stat<?>> resolve() {
        return BuiltInRegistries.STAT_TYPE.getOptional(statType).flatMap(this::resolve);
    }

    private <T> Optional<Stat<?>> resolve(StatType<T> type) {
        return type.getRegistry().getOptional(stat).map(type::get);
    }

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return count;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public Component title() {
        Component name = Component.translatableWithFallback("stat." + stat.getNamespace() + "." + stat.getPath(), stat.toString());
        return Component.translatable("hibiscusquests.task.stat", name, count);
    }
}

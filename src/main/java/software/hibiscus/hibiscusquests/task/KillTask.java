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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// kill: a team member kills count entities of a type or tag (directly, or with a projectile they shot).
//   {type: "kill", entity: "minecraft:zombie", count: 10L}
//   {type: "kill", entity: "#minecraft:undead", count: 25L}
public record KillTask(IdOrTag<EntityType<?>> entity, long count) implements Task {
    public static final MapCodec<KillTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IdOrTag.codec(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(KillTask::entity),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "count must be at least 1"))
                    .optionalFieldOf("count", 1L).forGetter(KillTask::count))
            .apply(i, KillTask::new));

    public static final Type TYPE = new Type();

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return count;
    }

    public boolean matches(Entity victim) {
        return entity.matches(victim.getType().builtInRegistryHolder());
    }

    // The type's spawn egg, if it has one.
    @Override
    public ItemStack icon() {
        if (!entity.tag()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).orElse(null);
            if (type != null) {
                var egg = SpawnEggItem.byId(type);
                if (egg.isPresent()) {
                    return new ItemStack(egg.get());
                }
            }
        }
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public Component title() {
        Component name = entity.tag() ? entity.name("entity_type")
                : BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).map(EntityType::getDescription).orElse(Component.literal(entity.toString()));
        return Component.translatable("hibiscusquests.task.kill", count, name);
    }

    public static final class Type implements TaskType<KillTask> {
        private Type() {}

        @Override
        public MapCodec<KillTask> codec() {
            return CODEC;
        }

        public void onDeath(LivingDeathEvent event) {
            Entity killer = event.getSource().getEntity();
            if (killer instanceof ServerPlayer player) {
                for (ActiveTask<KillTask> task : QuestsApi.get().activeTasks(player, this)) {
                    if (task.task().matches(event.getEntity())) {
                        task.addProgress(1);
                    }
                }
            }
        }
    }
}

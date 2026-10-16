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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// observation: a member looks at a block or entity (by id or #tag) for ticks ticks in all, from up to 8 blocks away.
//   {type: "observation", block: "minecraft:end_portal_frame", ticks: 100L}
//   {type: "observation", entity: "minecraft:warden", ticks: 40L}
public record ObservationTask(Optional<IdOrTag<Block>> block, Optional<IdOrTag<EntityType<?>>> entity, long ticks) implements Task {
    private static final int CHECK_INTERVAL = 5;
    private static final double REACH = 8;

    public static final MapCodec<ObservationTask> CODEC = RecordCodecBuilder.<ObservationTask>mapCodec(i -> i.group(
            IdOrTag.codec(Registries.BLOCK).optionalFieldOf("block").forGetter(ObservationTask::block),
            IdOrTag.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity").forGetter(ObservationTask::entity),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "ticks must be at least 1"))
                    .optionalFieldOf("ticks", 20L).forGetter(ObservationTask::ticks))
            .apply(i, ObservationTask::new))
            .validate(task -> task.block.isPresent() || task.entity.isPresent()
                    ? DataResult.success(task) : DataResult.error(() -> "An observation task needs a block or an entity"));

    public static final TaskType<ObservationTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<ObservationTask> codec() {
            return CODEC;
        }

        @Override
        public int pollInterval() {
            return CHECK_INTERVAL;
        }

        @Override
        public void poll(ActiveTask<ObservationTask> task) {
            if (task.task().isLooking(task.player())) {
                task.addProgress(CHECK_INTERVAL);
            }
        }
    };

    public boolean isLooking(ServerPlayer player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, target -> !target.isSpectator() && target.isPickable(), REACH);
        if (hit instanceof EntityHitResult entityHit) {
            return entity.isPresent() && entity.get().matches(entityHit.getEntity().getType().builtInRegistryHolder());
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return block.isPresent() && block.get().matches(player.level().getBlockState(blockHit.getBlockPos()).typeHolder());
        }
        return false;
    }

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return ticks;
    }

    @Override
    public ItemStack icon() {
        if (block.isPresent() && !block.get().tag()) {
            Block found = BuiltInRegistries.BLOCK.getOptional(block.get().id()).orElse(null);
            if (found != null && found.asItem() != Items.AIR) {
                return new ItemStack(found);
            }
        }
        return new ItemStack(Items.SPYGLASS);
    }

    @Override
    public Component title() {
        Component name = block.map(b -> b.tag() ? b.name("block")
                : BuiltInRegistries.BLOCK.getOptional(b.id()).map(Block::getName).orElse(Component.literal(b.toString())))
                .orElseGet(() -> entity.map(e -> e.tag() ? e.name("entity_type")
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(e.id()).map(EntityType::getDescription).orElse(Component.literal(e.toString())))
                        .orElse(Component.empty()));
        return Component.translatable("hibiscusquests.task.observation", name, Math.max(1, (ticks + 19) / 20));
    }
}

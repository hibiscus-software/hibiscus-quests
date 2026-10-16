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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// fluid: the team hands in amount mB of a fluid (by id or #tag) from buckets, tanks and other fluid-holding items in
// the inventory (NeoForge's fluid item capability), with the Submit button. In detect mode carrying it is enough.
//   {type: "fluid", fluid: "minecraft:lava", amount: 4000L}
//   {type: "fluid", fluid: "#c:oil", amount: 8000L, mode: "detect"}
public record FluidTask(IdOrTag<Fluid> fluid, long amount, ItemTask.Mode mode) implements Task {
    public static final MapCodec<FluidTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IdOrTag.codec(Registries.FLUID).fieldOf("fluid").forGetter(FluidTask::fluid),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "amount must be at least 1"))
                    .optionalFieldOf("amount", (long) FluidType.BUCKET_VOLUME).forGetter(FluidTask::amount),
            ItemTask.Mode.CODEC.optionalFieldOf("mode", ItemTask.Mode.CONSUME).forGetter(FluidTask::mode))
            .apply(i, FluidTask::new));

    public static final TaskType<FluidTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<FluidTask> codec() {
            return CODEC;
        }

        @Override
        public int pollInterval() {
            return 20;
        }

        @Override
        public void poll(ActiveTask<FluidTask> task) {
            if (task.task().mode == ItemTask.Mode.DETECT) {
                long held = Math.min(task.task().amount, task.task().transfer(task.player(), Long.MAX_VALUE, false));
                if (held > task.progress()) {
                    task.setProgress(held);
                }
            }
        }

        @Override
        public void onActivated(ActiveTask<FluidTask> task) {
            poll(task);
        }

        @Override
        public boolean canSubmit(FluidTask task) {
            return task.mode == ItemTask.Mode.CONSUME;
        }

        @Override
        public void submit(ActiveTask<FluidTask> task) {
            long taken = task.task().transfer(task.player(), task.task().amount - task.progress(), true);
            if (taken > 0) {
                task.addProgress(taken);
            }
        }
    };

    // How much matching fluid the player's items hold (take false), or takes up to max of it (take true).
    long transfer(ServerPlayer player, long max, boolean take) {
        Inventory inventory = player.getInventory();
        long total = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && total < max; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                continue;
            }
            ItemAccess access = ItemAccess.forPlayerSlot(player, slot);
            ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
            if (handler == null) {
                continue;
            }
            for (int index = 0; index < handler.size() && total < max; index++) {
                FluidResource resource = handler.getResource(index);
                if (resource.isEmpty() || !fluid.matches(resource.getFluid().builtInRegistryHolder())) {
                    continue;
                }
                int wanted = (int) Math.min(Integer.MAX_VALUE, max - total);
                try (Transaction transaction = Transaction.openRoot()) {
                    int moved = handler.extract(index, resource, wanted, transaction);
                    if (take) {
                        transaction.commit();
                    }
                    total += moved;
                }
            }
        }
        return total;
    }

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return amount;
    }

    @Override
    public ItemStack icon() {
        if (!fluid.tag()) {
            Fluid found = BuiltInRegistries.FLUID.getOptional(fluid.id()).orElse(Fluids.EMPTY);
            ItemStack bucket = new ItemStack(found.getBucket());
            if (!bucket.isEmpty() && found != Fluids.EMPTY) {
                return bucket;
            }
        }
        return new ItemStack(Items.BUCKET);
    }

    @Override
    public Component title() {
        Component name = fluid.tag() ? fluid.name("fluid")
                : BuiltInRegistries.FLUID.getOptional(fluid.id()).map(f -> f.getFluidType().getDescription()).orElse(Component.literal(fluid.toString()));
        return Component.translatable(mode == ItemTask.Mode.CONSUME ? "hibiscusquests.task.fluid.submit" : "hibiscusquests.task.fluid", amount, name);
    }
}

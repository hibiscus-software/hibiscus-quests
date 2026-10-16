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

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// energy: the team hands in amount FE from batteries and other energy-holding items in the inventory (NeoForge's energy
// item capability), with the Submit button. max_rate caps what one press takes (0: no cap), for "keep feeding it"
// quests.
//   {type: "energy", amount: 1000000L}
//   {type: "energy", amount: 50000L, max_rate: 5000L}
public record EnergyTask(long amount, long maxRate) implements Task {
    public static final MapCodec<EnergyTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "amount must be at least 1"))
                    .fieldOf("amount").forGetter(EnergyTask::amount),
            Codec.LONG.validate(n -> n >= 0 ? DataResult.success(n) : DataResult.error(() -> "max_rate can't be negative"))
                    .optionalFieldOf("max_rate", 0L).forGetter(EnergyTask::maxRate))
            .apply(i, EnergyTask::new));

    public static final TaskType<EnergyTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<EnergyTask> codec() {
            return CODEC;
        }

        @Override
        public boolean canSubmit(EnergyTask task) {
            return true;
        }

        @Override
        public void submit(ActiveTask<EnergyTask> task) {
            EnergyTask energy = task.task();
            long wanted = energy.amount - task.progress();
            if (energy.maxRate > 0) {
                wanted = Math.min(wanted, energy.maxRate);
            }
            long taken = take(task.player(), wanted);
            if (taken > 0) {
                task.addProgress(taken);
            }
        }
    };

    // Takes up to max FE from the player's energy-holding items.
    static long take(ServerPlayer player, long max) {
        Inventory inventory = player.getInventory();
        long total = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && total < max; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                continue;
            }
            EnergyHandler handler = ItemAccess.forPlayerSlot(player, slot).getCapability(Capabilities.Energy.ITEM);
            if (handler == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int moved = handler.extract((int) Math.min(Integer.MAX_VALUE, max - total), transaction);
                transaction.commit();
                total += moved;
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
        return new ItemStack(Items.REDSTONE);
    }

    @Override
    public Component title() {
        return Component.translatable("hibiscusquests.task.energy", amount);
    }
}

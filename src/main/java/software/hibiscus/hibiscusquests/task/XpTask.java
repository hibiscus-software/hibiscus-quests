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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// xp: the team hands in amount experience levels (or points, with points: true) with the Submit button, which takes
// what's still needed. With consume: false, reaching that many levels (or points) is enough and nothing is taken.
//   {type: "xp", amount: 30L}
//   {type: "xp", amount: 500L, points: true, consume: false}
public record XpTask(long amount, boolean points, boolean consume) implements Task {
    public static final MapCodec<XpTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "amount must be at least 1"))
                    .fieldOf("amount").forGetter(XpTask::amount),
            Codec.BOOL.optionalFieldOf("points", false).forGetter(XpTask::points),
            Codec.BOOL.optionalFieldOf("consume", true).forGetter(XpTask::consume))
            .apply(i, XpTask::new));

    public static final TaskType<XpTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<XpTask> codec() {
            return CODEC;
        }

        @Override
        public int pollInterval() {
            return 20;
        }

        // Without consume: what the player has counts.
        @Override
        public void poll(ActiveTask<XpTask> task) {
            if (!task.task().consume) {
                long held = Math.min(task.task().amount, task.task().held(task.player()));
                if (held > task.progress()) {
                    task.setProgress(held);
                }
            }
        }

        @Override
        public void onActivated(ActiveTask<XpTask> task) {
            poll(task);
        }

        @Override
        public boolean canSubmit(XpTask task) {
            return task.consume;
        }

        @Override
        public void submit(ActiveTask<XpTask> task) {
            XpTask xp = task.task();
            ServerPlayer player = task.player();
            int take = (int) Math.min(Integer.MAX_VALUE, Math.min(xp.amount - task.progress(), xp.held(player)));
            if (take <= 0) {
                return;
            }
            if (xp.points) {
                player.giveExperiencePoints(-take);
            } else {
                player.giveExperienceLevels(-take);
            }
            task.addProgress(take);
        }
    };

    // The levels (or points) the player has.
    long held(ServerPlayer player) {
        return points ? player.totalExperience : player.experienceLevel;
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
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public Component title() {
        String key = "hibiscusquests.task.xp." + (points ? "points" : "levels") + (consume ? ".submit" : "");
        return Component.translatable(key, amount);
    }
}

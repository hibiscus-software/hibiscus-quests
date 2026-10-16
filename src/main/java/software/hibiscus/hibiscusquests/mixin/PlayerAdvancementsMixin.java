/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.task.CriterionTasks;
import software.hibiscus.hibiscusquests.task.PlayerAdvancementsAccess;

// Lets criterion tasks listen to advancement triggers without being advancements (see CriterionTasks): exposes the
// private add/remove of trigger listeners, and diverts award() for Hibiscus Quests' stand-in advancements to the task
// before vanilla records, rewards, announces or syncs anything. Every other advancement goes through untouched.
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin implements PlayerAdvancementsAccess {
    @Shadow
    private ServerPlayer player;

    @Shadow
    private <T extends CriterionTriggerInstance> void addListener(Criterion<T> typeAndInstance, PlayerAdvancements.TriggerInstanceKey criterion) {
        throw new AssertionError("shadowed");
    }

    @Shadow
    private <T extends CriterionTriggerInstance> void removeListener(CriterionTrigger<T> type, PlayerAdvancements.TriggerInstanceKey criterion) {
        throw new AssertionError("shadowed");
    }

    @Override
    public <T extends CriterionTriggerInstance> void hibiscusquests$addCriterionListener(Criterion<T> criterion, PlayerAdvancements.TriggerInstanceKey key) {
        addListener(criterion, key);
    }

    @Override
    public <T extends CriterionTriggerInstance> void hibiscusquests$removeCriterionListener(CriterionTrigger<T> trigger, PlayerAdvancements.TriggerInstanceKey key) {
        removeListener(trigger, key);
    }

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void hibiscusquests$award(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (CriterionTasks.onAward(player, holder)) {
            cir.setReturnValue(false);
        }
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.server.PlayerAdvancements;

// Added to PlayerAdvancements by PlayerAdvancementsMixin: puts a trigger instance in (or takes it out of) the player's
// map of listening trigger instances, which is private in vanilla. Used by CriterionTasks only.
public interface PlayerAdvancementsAccess {
    <T extends CriterionTriggerInstance> void hibiscusquests$addCriterionListener(Criterion<T> criterion, PlayerAdvancements.TriggerInstanceKey key);

    <T extends CriterionTriggerInstance> void hibiscusquests$removeCriterionListener(CriterionTrigger<T> trigger, PlayerAdvancements.TriggerInstanceKey key);
}

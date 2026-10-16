/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import software.hibiscus.hibiscusquests.api.reward.RewardTypes;
import software.hibiscus.hibiscusquests.api.task.TaskTypes;
import software.hibiscus.hibiscusquests.reward.CommandReward;
import software.hibiscus.hibiscusquests.reward.ItemReward;
import software.hibiscus.hibiscusquests.reward.XpReward;
import software.hibiscus.hibiscusquests.task.AdvancementTask;
import software.hibiscus.hibiscusquests.task.CheckmarkTask;
import software.hibiscus.hibiscusquests.task.CriterionTask;
import software.hibiscus.hibiscusquests.task.ItemTask;

// The task and reward type registries, and the built-in types (hibiscusquests:<name>), with the events the built-in
// types track progress from.
final class BuiltinTypes {
    private BuiltinTypes() {}

    static void register(IEventBus modEventBus) {
        TaskTypes.register(HibiscusQuests.id("item"), ItemTask.TYPE);
        TaskTypes.register(HibiscusQuests.id("checkmark"), CheckmarkTask.TYPE);
        TaskTypes.register(HibiscusQuests.id("advancement"), AdvancementTask.TYPE);
        TaskTypes.register(HibiscusQuests.id("criterion"), CriterionTask.TYPE);

        RewardTypes.register(HibiscusQuests.id("item"), ItemReward.TYPE);
        RewardTypes.register(HibiscusQuests.id("xp"), XpReward.POINTS);
        RewardTypes.register(HibiscusQuests.id("xp_levels"), XpReward.LEVELS);
        RewardTypes.register(HibiscusQuests.id("command"), CommandReward.TYPE);

        modEventBus.addListener((NewRegistryEvent event) -> {
            event.register(TaskTypes.REGISTRY);
            event.register(RewardTypes.REGISTRY);
        });
        modEventBus.addListener((RegisterEvent event) -> {
            TaskTypes.registerQueued(event);
            RewardTypes.registerQueued(event);
        });

        NeoForge.EVENT_BUS.addListener(ItemTask.TYPE::onCrafted);
        NeoForge.EVENT_BUS.addListener(AdvancementTask.TYPE::onProgress);
    }
}

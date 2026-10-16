/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// The book's sounds. assets/hibiscusquests/sounds.json points them at vanilla sounds; a resource pack can point them
// anywhere else.
public final class QuestSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HibiscusQuests.MODID);

    public static final Supplier<SoundEvent> QUEST_COMPLETE = register("quest_complete");
    public static final Supplier<SoundEvent> REWARD_CLAIM = register("reward_claim");
    public static final Supplier<SoundEvent> CHAPTER_COMPLETE = register("chapter_complete");

    private QuestSounds() {}

    private static Supplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HibiscusQuests.id(name)));
    }

    static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}

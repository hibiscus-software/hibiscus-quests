/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.reward;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import software.hibiscus.hibiscusquests.api.HibiscusQuestsApi;

/**
 * The registry of reward types ({@code hibiscusquests:reward_type}), used like
 * {@link software.hibiscus.hibiscusquests.api.task.TaskTypes}: register from your mod constructor with
 * {@link #register} or a {@code DeferredRegister} on {@link #REGISTRY_KEY}. Chapter files may leave the
 * {@code hibiscusquests} namespace out of built-in types ({@code type: "xp_levels"}).
 */
public final class RewardTypes {
    /** The registry's key. */
    public static final ResourceKey<Registry<RewardType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(HibiscusQuestsApi.MOD_ID, "reward_type"));

    /** The registry. Hibiscus Quests adds it to NeoForge's registries; it isn't synced (both sides register the same types). */
    public static final Registry<RewardType<?>> REGISTRY = new RegistryBuilder<>(REGISTRY_KEY).create();

    private static final Map<Identifier, RewardType<?>> PENDING = new LinkedHashMap<>();
    private static boolean registered;

    private RewardTypes() {}

    /**
     * Queues a reward type for registration. Call it from your mod constructor.
     *
     * @param id   the type's id, in your mod's namespace
     * @param type the type
     * @param <R>  the reward values the type reads
     * @return {@code type}
     * @throws IllegalStateException if registration is already over, or the id is taken
     */
    public static synchronized <R extends Reward> RewardType<R> register(Identifier id, RewardType<R> type) {
        if (registered) {
            throw new IllegalStateException("Reward type " + id + " was registered too late: register reward types from your mod constructor");
        }
        if (PENDING.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Reward type " + id + " is already registered");
        }
        return type;
    }

    /**
     * A reward type by id.
     *
     * @param id the id
     * @return the type, or empty if there's none
     */
    public static Optional<RewardType<?>> get(Identifier id) {
        return REGISTRY.getOptional(id);
    }

    /**
     * A reward type's id.
     *
     * @param type the type
     * @return its id, or empty if it isn't registered
     */
    public static Optional<Identifier> idOf(RewardType<?> type) {
        return Optional.ofNullable(REGISTRY.getKey(type));
    }

    /**
     * Registers the queued types. Hibiscus Quests calls this from its {@code RegisterEvent} listener; other mods
     * don't.
     *
     * @param event the registration event
     */
    public static synchronized void registerQueued(RegisterEvent event) {
        event.register(REGISTRY_KEY, helper -> {
            PENDING.forEach(helper::register);
            registered = true;
        });
    }
}

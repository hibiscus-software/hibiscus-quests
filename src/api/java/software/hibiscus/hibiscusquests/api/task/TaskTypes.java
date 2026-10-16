/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.task;

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
 * The registry of task types ({@code hibiscusquests:task_type}). Built-in types live in the {@code hibiscusquests}
 * namespace ({@code hibiscusquests:item}); chapter files may leave that namespace out ({@code type: "item"}).
 *
 * <p>Register a type from your mod constructor, either with {@link #register}:
 * <pre>{@code
 * TaskTypes.register(Identifier.fromNamespaceAndPath(MODID, "heat"), HeatTask.TYPE);
 * }</pre>
 * or with a {@code DeferredRegister}:
 * <pre>{@code
 * DeferredRegister<TaskType<?>> TASKS = DeferredRegister.create(TaskTypes.REGISTRY_KEY, MODID);
 * TASKS.register("heat", () -> HeatTask.TYPE);
 * }</pre>
 * Both sides need the same types, since the book is decoded on the client too.
 */
public final class TaskTypes {
    /** The registry's key. */
    public static final ResourceKey<Registry<TaskType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(HibiscusQuestsApi.MOD_ID, "task_type"));

    /** The registry. Hibiscus Quests adds it to NeoForge's registries; it isn't synced (both sides register the same types). */
    public static final Registry<TaskType<?>> REGISTRY = new RegistryBuilder<>(REGISTRY_KEY).create();

    private static final Map<Identifier, TaskType<?>> PENDING = new LinkedHashMap<>();
    private static boolean registered;

    private TaskTypes() {}

    /**
     * Queues a task type for registration. Call it from your mod constructor; it's registered with the other task
     * types when NeoForge fires its {@code RegisterEvent}.
     *
     * @param id   the type's id, in your mod's namespace
     * @param type the type
     * @param <T>  the task values the type reads
     * @return {@code type}
     * @throws IllegalStateException if registration is already over, or the id is taken
     */
    public static synchronized <T extends Task> TaskType<T> register(Identifier id, TaskType<T> type) {
        if (registered) {
            throw new IllegalStateException("Task type " + id + " was registered too late: register task types from your mod constructor");
        }
        if (PENDING.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Task type " + id + " is already registered");
        }
        return type;
    }

    /**
     * A task type by id.
     *
     * @param id the id
     * @return the type, or empty if there's none
     */
    public static Optional<TaskType<?>> get(Identifier id) {
        return REGISTRY.getOptional(id);
    }

    /**
     * A task type's id.
     *
     * @param type the type
     * @return its id, or empty if it isn't registered
     */
    public static Optional<Identifier> idOf(TaskType<?> type) {
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

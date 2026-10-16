/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Base for the events about a team's progress, posted on {@code NeoForge.EVENT_BUS} on the server thread.
 */
public abstract class QuestEvent extends Event {
    private final UUID teamId;
    private final @Nullable ServerPlayer player;

    /**
     * Creates the event.
     *
     * @param teamId the id the team's progress is stored under
     * @param player the player who caused it, or null (a command on an offline team)
     */
    protected QuestEvent(UUID teamId, @Nullable ServerPlayer player) {
        this.teamId = teamId;
        this.player = player;
    }

    /**
     * The team whose progress this is about.
     *
     * @return the team id
     */
    public UUID teamId() {
        return teamId;
    }

    /**
     * The player who caused it, when one did.
     *
     * @return the player, or empty
     */
    public Optional<ServerPlayer> player() {
        return Optional.ofNullable(player);
    }
}

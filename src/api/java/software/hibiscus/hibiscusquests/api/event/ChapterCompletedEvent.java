/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

/** A team completed every non-optional quest of a chapter, for the first time. */
public class ChapterCompletedEvent extends QuestEvent {
    private final String chapter;

    /**
     * Creates the event.
     *
     * @param teamId  the team id
     * @param player  the member whose progress completed it, or null
     * @param chapter the chapter's id
     */
    public ChapterCompletedEvent(UUID teamId, @Nullable ServerPlayer player, String chapter) {
        super(teamId, player);
        this.chapter = chapter;
    }

    /**
     * The chapter that was completed.
     *
     * @return the chapter's id
     */
    public String chapter() {
        return chapter;
    }
}

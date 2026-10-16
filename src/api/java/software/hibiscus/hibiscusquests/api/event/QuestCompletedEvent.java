/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.QuestRef;

/** A team completed a quest. Posted after the completion is recorded, before rewards are auto-claimed. */
public class QuestCompletedEvent extends QuestEvent {
    private final QuestRef quest;

    /**
     * Creates the event.
     *
     * @param teamId the team id
     * @param player the member whose progress completed it, or null
     * @param quest  the quest
     */
    public QuestCompletedEvent(UUID teamId, @Nullable ServerPlayer player, QuestRef quest) {
        super(teamId, player);
        this.quest = quest;
    }

    /**
     * The quest that was completed.
     *
     * @return the quest
     */
    public QuestRef quest() {
        return quest;
    }
}

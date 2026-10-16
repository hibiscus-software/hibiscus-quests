/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import java.util.Optional;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import software.hibiscus.hibiscusquests.api.QuestRef;

/**
 * The quest book is about to open, on the client (posted on {@code NeoForge.EVENT_BUS}, on the client thread). Cancel it
 * to keep the book shut, for example to open a screen of your own instead.
 */
public class QuestBookOpenEvent extends Event implements ICancellableEvent {
    private final Optional<QuestRef> quest;

    /**
     * Creates the event.
     *
     * @param quest the quest the book will open on, if any
     */
    public QuestBookOpenEvent(Optional<QuestRef> quest) {
        this.quest = quest;
    }

    /**
     * The quest the book will open on: given when it's opened from a link or a command, else empty (the chapter the
     * player last viewed).
     *
     * @return the quest, or empty
     */
    public Optional<QuestRef> quest() {
        return quest;
    }
}

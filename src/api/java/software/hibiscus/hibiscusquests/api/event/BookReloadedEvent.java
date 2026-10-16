/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.event;

import net.neoforged.bus.api.Event;
import software.hibiscus.hibiscusquests.api.book.BookView;

/**
 * The server's book was (re)built: at server start, after {@code /reload} or {@code /hibiscusquests reload}, and
 * after edits. Posted on {@code NeoForge.EVENT_BUS} on the server thread, before the book is sent to players.
 */
public class BookReloadedEvent extends Event {
    private final BookView book;

    /**
     * Creates the event.
     *
     * @param book the new book
     */
    public BookReloadedEvent(BookView book) {
        this.book = book;
    }

    /**
     * The new book.
     *
     * @return the book
     */
    public BookView book() {
        return book;
    }
}

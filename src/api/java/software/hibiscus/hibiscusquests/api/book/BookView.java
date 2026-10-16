/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.book;

import java.util.List;
import java.util.Optional;

import software.hibiscus.hibiscusquests.api.QuestRef;

/**
 * The quest book as the server has it now: mod-provided chapters merged with the pack's files. Read-only, and
 * replaced as a whole when the book is reloaded or edited ({@link software.hibiscus.hibiscusquests.api.event.BookReloadedEvent}),
 * so look it up again rather than keeping it.
 */
public interface BookView {
    /**
     * The chapters, in book order (by group, then order).
     *
     * @return an unmodifiable list
     */
    List<? extends ChapterView> chapters();

    /**
     * A chapter by id.
     *
     * @param id the chapter's id
     * @return the chapter, or empty
     */
    Optional<? extends ChapterView> chapter(String id);

    /**
     * A quest by reference.
     *
     * @param quest the quest
     * @return the quest, or empty
     */
    Optional<? extends QuestView> quest(QuestRef quest);
}

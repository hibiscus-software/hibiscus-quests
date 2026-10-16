/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.book;

import java.util.List;
import java.util.Optional;

/** A chapter of the book: a map of quests. */
public interface ChapterView {
    /**
     * The chapter's id, unique in the book.
     *
     * @return the id
     */
    String id();

    /**
     * The chapter's title as written in its file: plain text, a lang key or markup.
     *
     * @return the raw title
     */
    String title();

    /**
     * The namespace of the mod or data pack that ships the chapter, or empty for one from the pack's own files.
     *
     * @return the source namespace, or empty
     */
    Optional<String> source();

    /**
     * The chapter's quests, in file order.
     *
     * @return an unmodifiable list
     */
    List<? extends QuestView> quests();
}

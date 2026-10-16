/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

import java.util.Optional;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Points at one quest: its chapter's id and its own id, written {@code chapter/quest} (for example
 * {@code steelmaking/first_steel}). Quest ids are unique within their chapter, chapter ids within the book.
 *
 * @param chapter the chapter's id
 * @param quest   the quest's id within that chapter
 */
public record QuestRef(String chapter, String quest) {
    /** What chapter, quest, task and reward ids may contain: lowercase letters, digits and underscores. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    /** Reads and writes the {@code chapter/quest} form. */
    public static final Codec<QuestRef> CODEC = Codec.STRING.comapFlatMap(
            text -> parse(text).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a quest reference (chapter/quest): " + text)),
            QuestRef::toString);

    /**
     * Creates a reference, checking both ids.
     *
     * @param chapter the chapter's id
     * @param quest   the quest's id
     * @throws IllegalArgumentException if either id isn't {@code [a-z0-9_]+}
     */
    public QuestRef {
        if (!isValidId(chapter) || !isValidId(quest)) {
            throw new IllegalArgumentException("Invalid quest reference " + chapter + "/" + quest + ": ids are [a-z0-9_]+");
        }
    }

    /**
     * Parses {@code chapter/quest}.
     *
     * @param text the text
     * @return the reference, or empty if the text isn't one
     */
    public static Optional<QuestRef> parse(String text) {
        int slash = text.indexOf('/');
        if (slash <= 0 || slash != text.lastIndexOf('/')) {
            return Optional.empty();
        }
        String chapter = text.substring(0, slash);
        String quest = text.substring(slash + 1);
        return isValidId(chapter) && isValidId(quest) ? Optional.of(new QuestRef(chapter, quest)) : Optional.empty();
    }

    /**
     * Parses a reference as written inside a chapter: {@code quest} for a quest in the same chapter, or
     * {@code chapter/quest} for one in another.
     *
     * @param text           the text
     * @param currentChapter the chapter the reference is written in
     * @return the reference, or empty if the text isn't one
     */
    public static Optional<QuestRef> parseRelative(String text, String currentChapter) {
        if (text.indexOf('/') < 0) {
            return isValidId(text) && isValidId(currentChapter) ? Optional.of(new QuestRef(currentChapter, text)) : Optional.empty();
        }
        return parse(text);
    }

    /**
     * Whether a string is a valid chapter, quest, task or reward id.
     *
     * @param id the string
     * @return true if it's non-empty and only {@code [a-z0-9_]}
     */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * The reference as written from inside {@code currentChapter}: just the quest id if it's in that chapter.
     *
     * @param currentChapter the chapter the reference is written in
     * @return {@code quest} or {@code chapter/quest}
     */
    public String toRelativeString(String currentChapter) {
        return chapter.equals(currentChapter) ? quest : toString();
    }

    /**
     * The {@code chapter/quest} form, which is also how progress files key quests.
     *
     * @return {@code chapter/quest}
     */
    @Override
    public String toString() {
        return chapter + "/" + quest;
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.editor;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * How the book's editor should edit one field of a task or reward type (see
 * {@link software.hibiscus.hibiscusquests.api.task.TaskType#editorHints()}).
 *
 * <p>The editor builds a form for every type from its codec's field names, so a type works in the editor without
 * hints: a field it can't place gets a plain text box for its SNBT value. Hints give a field the right control (a
 * toggle, a number box, an item or registry picker) and let the form show fields the codec leaves out of the file
 * while they have their default value.
 *
 * @param kind     the kind of control
 * @param registry for {@link Kind#ID}, {@link Kind#ID_OR_TAG} and {@link Kind#TAG}, the registry the picker lists
 * @param choices  for {@link Kind#CHOICE}, the values to cycle through, as written in the file
 */
public record FieldHint(Kind kind, Optional<ResourceKey<? extends Registry<?>>> registry, List<String> choices) {
    /** The kinds of control the editor has. */
    public enum Kind {
        /** One line of text (titles may be lang keys or use the book's markup). */
        TEXT,
        /** Several lines, edited with a preview of the book's markup (written as a list of lines). */
        LONG_TEXT,
        /** A toggle. */
        BOOLEAN,
        /** Yes, no, or the type's default (a field that's left out unless set). */
        OPTIONAL_BOOLEAN,
        /** A whole number. */
        INTEGER,
        /** A number with decimals. */
        DECIMAL,
        /** One of {@link FieldHint#choices()}. */
        CHOICE,
        /** An id from {@link FieldHint#registry()}, with a searchable picker. */
        ID,
        /** An id or {@code #tag} from {@link FieldHint#registry()}, with a searchable picker. */
        ID_OR_TAG,
        /** A tag of {@link FieldHint#registry()}, written without the {@code #}, with a searchable picker. */
        TAG,
        /** An item: its id, or a whole stack with components (the item in the editor's hand). */
        ITEM_STACK,
        /** A list of items, edited as a row of slots. */
        ITEM_LIST,
        /** An icon: an item, a stack, or {@code {texture: "..."}}. */
        ICON,
        /** An RGB colour, written {@code "#rrggbb"}. */
        COLOR,
        /** A list of strings, edited as one comma-separated line. */
        STRING_LIST,
        /** The id of one of the book's reward tables. */
        REWARD_TABLE,
        /** A quest, written {@code "quest"} in the same chapter or {@code "chapter/quest"}. */
        QUEST,
        /** Not shown in the form (it's edited some other way, or not at all). */
        HIDDEN
    }

    /**
     * Makes a hint, checking the arguments.
     *
     * @param kind     the kind of control
     * @param registry the registry, for {@link Kind#ID}, {@link Kind#ID_OR_TAG} and {@link Kind#TAG}
     * @param choices  the values, for {@link Kind#CHOICE}
     */
    public FieldHint {
        choices = List.copyOf(choices);
        if ((kind == Kind.ID || kind == Kind.ID_OR_TAG || kind == Kind.TAG) && registry.isEmpty()) {
            throw new IllegalArgumentException(kind + " needs a registry");
        }
        if (kind == Kind.CHOICE && choices.isEmpty()) {
            throw new IllegalArgumentException("CHOICE needs choices");
        }
    }

    /**
     * A hint with no registry or choices.
     *
     * @param kind the kind of control
     * @return the hint
     */
    public static FieldHint of(Kind kind) {
        return new FieldHint(kind, Optional.empty(), List.of());
    }

    /**
     * One line of text.
     *
     * @return the hint
     */
    public static FieldHint text() {
        return of(Kind.TEXT);
    }

    /**
     * A toggle.
     *
     * @return the hint
     */
    public static FieldHint bool() {
        return of(Kind.BOOLEAN);
    }

    /**
     * A whole number.
     *
     * @return the hint
     */
    public static FieldHint integer() {
        return of(Kind.INTEGER);
    }

    /**
     * A number with decimals.
     *
     * @return the hint
     */
    public static FieldHint decimal() {
        return of(Kind.DECIMAL);
    }

    /**
     * One of a few values.
     *
     * @param choices the values, as written in the file
     * @return the hint
     */
    public static FieldHint choice(String... choices) {
        return new FieldHint(Kind.CHOICE, Optional.empty(), List.of(choices));
    }

    /**
     * An id from a registry.
     *
     * @param registry the registry, for example {@code Registries.ITEM}
     * @return the hint
     */
    public static FieldHint id(ResourceKey<? extends Registry<?>> registry) {
        return new FieldHint(Kind.ID, Optional.of(registry), List.of());
    }

    /**
     * An id or {@code #tag} from a registry.
     *
     * @param registry the registry, for example {@code Registries.ENTITY_TYPE}
     * @return the hint
     */
    public static FieldHint idOrTag(ResourceKey<? extends Registry<?>> registry) {
        return new FieldHint(Kind.ID_OR_TAG, Optional.of(registry), List.of());
    }

    /**
     * A tag of a registry, written without the {@code #}.
     *
     * @param registry the registry, for example {@code Registries.ITEM}
     * @return the hint
     */
    public static FieldHint tag(ResourceKey<? extends Registry<?>> registry) {
        return new FieldHint(Kind.TAG, Optional.of(registry), List.of());
    }

    /**
     * An item id or stack.
     *
     * @return the hint
     */
    public static FieldHint itemStack() {
        return of(Kind.ITEM_STACK);
    }
}

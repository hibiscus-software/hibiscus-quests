/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api.editor;

import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;

/**
 * An editor of your own for one task or reward type, used instead of the book's generated form. Register it on the
 * client with {@link CustomEditors}.
 */
@FunctionalInterface
public interface CustomEditor {
    /**
     * Opens the editor. The quest book is the current screen when this is called: open your own screen, and go back
     * to the one that was showing when you're done.
     *
     * @param value the task or reward as written in its chapter file (including {@code type} and the fields every
     *              task or reward has, such as {@code id} and {@code title})
     * @param save  call with the edited compound to save it; it's checked like any edit, and an error is shown in the
     *              book. Don't call it to cancel.
     */
    void open(CompoundTag value, Consumer<CompoundTag> save);
}

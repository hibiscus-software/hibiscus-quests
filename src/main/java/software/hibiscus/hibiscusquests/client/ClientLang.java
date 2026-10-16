/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.QuestBook;

// The pack's translations on the client: quest text in the player's language (or the one /hibiscusquests locale
// previews), falling back to the pack's default locale. The editor edits the text of this locale.
public final class ClientLang {
    private static String preview = "";

    private ClientLang() {}

    // The locale quest text is shown (and edited) in.
    public static String locale() {
        if (!preview.isEmpty()) {
            return preview;
        }
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        return selected == null || selected.isEmpty() ? BookText.DEFAULT_LOCALE : selected;
    }

    public static boolean isPreviewing() {
        return !preview.isEmpty();
    }

    // Shows quest text in another locale until it's reset ("") or the player leaves.
    public static void setPreview(String locale) {
        preview = locale;
        ClientQuestState.refresh();
    }

    public static Optional<String> lookup(String key) {
        QuestBook book = ClientQuestState.book();
        return book.translation(locale(), key).or(() -> book.translation(BookText.DEFAULT_LOCALE, key));
    }

    // Leaving a server.
    static void clear() {
        preview = "";
    }
}

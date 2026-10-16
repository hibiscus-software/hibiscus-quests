/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

// Quest text (titles, subtitles, descriptions) as the book shows it. Text may be a literal (with & or section-sign
// formatting codes), {translate:some.key}, or a bare lang key (lowercase, dotted, no spaces). A key is looked up in
// the pack's own translations first (lang/<locale>.snbt, sent with the book), then in the game's languages (a mod's
// assets/<namespace>/lang/), and shown as itself when neither has it. The book's markup (**bold**, {item:...}) is
// drawn by the client's text layout; here it's left as written.
//
// The pack's translations are looked up per side: on the server thread in the server's book (in DEFAULT_LOCALE, for
// chat and command output), elsewhere through the client's lookup (the player's language, or the one they preview).
public final class BookText {
    // The locale the pack's text falls back to, and the one the server uses.
    public static final String DEFAULT_LOCALE = "en_us";

    private static final Pattern TRANSLATE = Pattern.compile("\\{translate:([^}]+)}");
    private static final Pattern BARE_KEY = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)+");
    // & followed by a formatting code character, as in &6gold or &lbold.
    private static final Pattern FORMAT_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static volatile Function<String, Optional<String>> clientLookup = key -> Optional.empty();

    private BookText() {}

    // How the client finds the pack's translations (set by the client mod).
    public static void setClientLookup(Function<String, Optional<String>> lookup) {
        clientLookup = lookup;
    }

    // The pack's translation of a key, for the side asking.
    public static Optional<String> packTranslation(String key) {
        MinecraftServer server = BookManager.server();
        if (server != null && server.isSameThread()) {
            return BookManager.book().translation(DEFAULT_LOCALE, key);
        }
        return clientLookup.apply(key);
    }

    // The key a text stands for: {translate:key} as the whole text, or a bare key.
    public static Optional<String> keyOf(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        Matcher whole = TRANSLATE.matcher(text.strip());
        if (whole.matches()) {
            return Optional.of(whole.group(1));
        }
        return BARE_KEY.matcher(text).matches() ? Optional.of(text) : Optional.empty();
    }

    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Optional<String> key = keyOf(text);
        if (key.isPresent()) {
            Optional<String> translated = packTranslation(key.get());
            if (translated.isPresent()) {
                return literal(translated.get());
            }
            return Component.translatableWithFallback(key.get(), key.get());
        }
        return literal(text);
    }

    private static Component literal(String text) {
        return Component.literal(FORMAT_CODE.matcher(text).replaceAll("§$1"));
    }

    // A title, or the fallback when it's blank (a quest's id, say).
    public static Component titleOr(String text, String fallback) {
        return text == null || text.isBlank() ? Component.literal(fallback) : component(text);
    }

    // A description's lines, with any line that's a key (a translated description, as export_lang writes it) replaced
    // by its translation's lines.
    public static List<String> lines(List<String> lines) {
        List<String> resolved = new ArrayList<>();
        for (String line : lines) {
            Optional<String> key = keyOf(line);
            Optional<String> translated = key.flatMap(BookText::packTranslation)
                    .or(() -> key.filter(k -> Language.getInstance().has(k)).map(k -> Language.getInstance().getOrDefault(k)));
            if (translated.isPresent()) {
                resolved.addAll(List.of(translated.get().split("\n", -1)));
            } else {
                resolved.add(line);
            }
        }
        return resolved;
    }
}

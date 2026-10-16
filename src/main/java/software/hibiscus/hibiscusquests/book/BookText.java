/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;

// Quest text (titles, subtitles) as a plain Component, for chat, toasts and command output. Text may be a literal
// (with & or section-sign formatting codes), {translate:some.key}, or a bare lang key (lowercase, dotted, no spaces),
// which is shown as itself when there's no translation. The book's markup (**bold**, {item:...}) is drawn by the
// client's text layout; here it's left as written.
public final class BookText {
    private static final Pattern TRANSLATE = Pattern.compile("\\{translate:([^}]+)}");
    private static final Pattern BARE_KEY = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)+");
    // & followed by a formatting code character, as in &6gold or &lbold.
    private static final Pattern FORMAT_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private BookText() {}

    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Matcher whole = TRANSLATE.matcher(text);
        if (whole.matches()) {
            return Component.translatableWithFallback(whole.group(1), whole.group(1));
        }
        if (BARE_KEY.matcher(text).matches()) {
            return Component.translatableWithFallback(text, text);
        }
        return Component.literal(FORMAT_CODE.matcher(text).replaceAll("§$1"));
    }

    // A title, or the fallback when it's blank (a quest's id, say).
    public static Component titleOr(String text, String fallback) {
        return text == null || text.isBlank() ? Component.literal(fallback) : component(text);
    }
}

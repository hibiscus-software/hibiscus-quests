/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

// Readable ids made from titles, as the editor gives new chapters, quests and tables: "Steel Ingot" becomes
// steel_ingot, or steel_ingot_2 when that's taken. Never random hex: people write these files by hand.
public final class Ids {
    private static final int MAX_LENGTH = 48;

    private Ids() {}

    // A free id from a title (or the fallback when the title has no letters or digits).
    public static String fromTitle(String title, String fallback, Collection<String> taken) {
        String base = slug(title);
        if (base.isEmpty()) {
            base = fallback;
        }
        String id = base;
        int n = 2;
        while (taken.contains(id)) {
            id = base + "_" + n++;
        }
        return id;
    }

    // Lowercase letters, digits and single underscores; formatting codes and accents dropped.
    public static String slug(String text) {
        String plain = text.replaceAll("[&§][0-9a-fk-or]", "");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("_+$", "");
        }
        return slug;
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

import java.util.Objects;

/**
 * The entry point to the Hibiscus Quests API: {@code QuestsApi.get().isCompleted(teamId, quest)}.
 *
 * <p>Only usable when Hibiscus Quests is installed. A mod that depends on it optionally should check
 * {@code ModList.get().isLoaded("hibiscusquests")} before touching this class, or ship its quests as data (which needs
 * no code at all, see docs/API.md).
 */
public final class QuestsApi {
    private static volatile QuestService service;

    private QuestsApi() {}

    /**
     * The quest service.
     *
     * @return the service
     * @throws IllegalStateException if Hibiscus Quests hasn't set it up yet (before its mod construction)
     */
    public static QuestService get() {
        QuestService current = service;
        if (current == null) {
            throw new IllegalStateException("Hibiscus Quests hasn't set up its API yet");
        }
        return current;
    }

    /**
     * Sets the service. Hibiscus Quests calls this from its mod constructor; other mods don't.
     *
     * @param implementation the service
     * @throws IllegalStateException if it's already set
     */
    public static synchronized void install(QuestService implementation) {
        Objects.requireNonNull(implementation, "implementation");
        if (service != null) {
            throw new IllegalStateException("The Hibiscus Quests API is already installed");
        }
        service = implementation;
    }
}

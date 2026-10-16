/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

// Wall-clock time for progress: start and completion times, and repeat cooldowns (which run in real time, not ticks,
// so they carry on while the server is off). Tests move it forward instead of waiting.
public final class QuestClock {
    private static volatile long offset;

    private QuestClock() {}

    // Milliseconds since the epoch.
    public static long now() {
        return System.currentTimeMillis() + offset;
    }

    // For tests: pretend this much more time has passed.
    public static void advance(long millis) {
        offset += millis;
    }
}

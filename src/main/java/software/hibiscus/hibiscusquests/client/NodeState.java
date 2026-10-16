/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

// How a quest stands for the player, which decides how its node is drawn.
public enum NodeState {
    // Not unlocked yet: a dimmed icon and a dark edge.
    LOCKED("locked"),
    // A secret quest that isn't unlocked: a "?" instead of its icon.
    SECRET("secret"),
    // Unlocked, nothing done yet.
    AVAILABLE("available"),
    // Unlocked with some progress: a ring shows how far.
    IN_PROGRESS("in_progress"),
    // Complete, with rewards the player hasn't claimed: a pulsing gold edge.
    CLAIMABLE("claimable"),
    // Complete and claimed (or nothing to claim): the accent edge and a check.
    DONE("done");

    private final String key;

    NodeState(String key) {
        this.key = key;
    }

    // The lang key of the state's name.
    public String translationKey() {
        return "hibiscusquests.state." + key;
    }

    public boolean isComplete() {
        return this == CLAIMABLE || this == DONE;
    }

    public boolean isUnlocked() {
        return this != LOCKED && this != SECRET;
    }
}

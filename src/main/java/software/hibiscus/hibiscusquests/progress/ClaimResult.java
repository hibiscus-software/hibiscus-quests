/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

// How a claim went, so commands and the book can say why nothing happened.
public enum ClaimResult {
    CLAIMED,
    UNKNOWN,
    NOT_COMPLETE,
    ALREADY_CLAIMED,
    CANCELLED,
    FAILED;

    public boolean claimed() {
        return this == CLAIMED;
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

// A reward that rolls a reward table (random rewards, loot crates), so the book can check the table exists and show a
// preview of what it may give.
public interface UsesRewardTable {
    String table();
}

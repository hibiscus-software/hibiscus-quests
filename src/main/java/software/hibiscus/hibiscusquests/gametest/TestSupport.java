/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import java.util.UUID;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestTeams;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// Shared pieces for the gametests. Every test uses its own mock players, so their (solo) teams' progress never meets
// another test's, even though tests run side by side.
final class TestSupport {
    private TestSupport() {}

    // A mock player that has logged in (so Hibiscus Quests has loaded their progress and is tracking their tasks).
    @SuppressWarnings("removal")
    static ServerPlayer join(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    static void leave(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    static QuestRef ref(String chapter, String quest) {
        return new QuestRef(chapter, quest);
    }

    static Quest quest(GameTestHelper helper, String chapter, String quest) {
        Quest found = BookManager.book().quest(ref(chapter, quest)).orElse(null);
        if (found == null) {
            helper.fail("The test book has no quest " + chapter + "/" + quest + " (is the demo data pack on?)");
        }
        return found;
    }

    static UUID team(ServerPlayer player) {
        return QuestTeams.teamIdOf(player);
    }

    static TeamProgress progress(ServerPlayer player) {
        return ProgressManager.progressOf(player);
    }

    static boolean completed(ServerPlayer player, String chapter, String quest) {
        return progress(player).isCompleted(ref(chapter, quest));
    }

    // What the book's Submit button / checkbox does: true if the server took it.
    static boolean submit(ServerPlayer player, String chapter, String quest, String task) {
        return ActiveTasks.submit(player, ref(chapter, quest), task);
    }

    // Ticks the first checkmark of a quest (the test book's checkmarks are named task_1 unless given an id).
    static boolean tick(ServerPlayer player, String chapter, String quest) {
        return submit(player, chapter, quest, "task_1");
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

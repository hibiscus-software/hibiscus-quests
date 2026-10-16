/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscuslib.api.team.TeamManager;
import software.hibiscus.hibiscuslib.api.team.TeamsApi;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.ProgressMode;
import software.hibiscus.hibiscusquests.book.TeamChange;
import software.hibiscus.hibiscusquests.config.QuestsConfig;

// Whose progress a player's actions count toward. In TEAM mode that's their team from the Hibiscus Lib Teams API (their
// party with Hibiscus Teams, else their solo team, whose id is their own UUID), in PLAYER mode their UUID. Only the
// Teams API is used, never a teams mod's classes.
public final class QuestTeams {
    // Gametests put a stub here to stand in for a teams mod (TeamsApi.install takes only one installer per run).
    private static volatile @Nullable TeamManager override;

    private QuestTeams() {}

    public static TeamManager teams() {
        TeamManager stub = override;
        return stub != null ? stub : TeamsApi.get();
    }

    public static void setOverride(@Nullable TeamManager manager) {
        override = manager;
    }

    public static @Nullable TeamManager override() {
        return override;
    }

    public static ProgressMode mode() {
        return BookManager.book().settings().progressMode().orElseGet(() -> QuestsConfig.get(QuestsConfig.PROGRESS_MODE));
    }

    public static TeamChange teamChange() {
        return BookManager.book().settings().teamChange().orElseGet(() -> QuestsConfig.get(QuestsConfig.TEAM_CHANGE));
    }

    public static UUID teamIdOf(ServerPlayer player) {
        return teamIdOf(player.getUUID());
    }

    public static UUID teamIdOf(UUID player) {
        return mode() == ProgressMode.PLAYER ? player : teams().teamOf(player).id();
    }

    // The online players whose progress is the team's.
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        return server.getPlayerList().getPlayers().stream().filter(player -> teamIdOf(player).equals(teamId)).toList();
    }
}

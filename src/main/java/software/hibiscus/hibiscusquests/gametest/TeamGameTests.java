/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.completed;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.team;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.tick;

import java.util.UUID;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscuslib.api.team.TeamsApi;
import software.hibiscus.hibiscuslib.api.team.event.PlayerJoinedTeamEvent;
import software.hibiscus.hibiscusquests.book.TeamChange;
import software.hibiscus.hibiscusquests.progress.ActiveTasks;
import software.hibiscus.hibiscusquests.progress.ProgressManager;
import software.hibiscus.hibiscusquests.progress.QuestTeams;

// Progress per team through the Teams API: a solo team's id is the player's UUID with the Lib fallback and with a teams
// mod, and each team_change mode does what it says when a player joins a (stub) party.
final class TeamGameTests {
    private TeamGameTests() {}

    // With no party, progress is stored under the player's own UUID whichever teams implementation is installed.
    static void soloTeamIsPlayer(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        check(helper, team(player).equals(player.getUUID()), "a player on their own keeps progress under their UUID");
        if (TeamsApi.isFallback()) {
            check(helper, TeamsApi.get().teamOf(player).id().equals(player.getUUID()), "the Lib fallback's team is the player");
        }
        tick(player, "test_logic", "a");
        check(helper, ProgressManager.progress(player.getUUID()).isCompleted(ref("test_logic", "a")), "and the progress is there");
        leave(helper, player);
        helper.succeed();
    }

    // keep_with_team, through the real event: the party starts with nothing; the player's solo progress stays put.
    static void keepWithTeam(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        tick(player, "test_logic", "a");
        StubTeams teams = StubTeams.get();
        StubTeams.Party party = teams.party("keep");
        try {
            UUID solo = team(player);
            teams.join(party, player.getUUID());
            if (QuestTeams.teamChange() == TeamChange.KEEP_WITH_TEAM) {
                NeoForge.EVENT_BUS.post(new PlayerJoinedTeamEvent(player.getUUID(), player, QuestTeams.teams().playerTeam(player.getUUID()), party));
            } else {
                ProgressManager.applyTeamChange(server(helper), player.getUUID(), solo, party.id(), TeamChange.KEEP_WITH_TEAM);
            }
            check(helper, team(player).equals(party.id()), "the player's progress is now the party's");
            check(helper, !completed(player, "test_logic", "a"), "keep_with_team: the party doesn't get the player's progress");
            check(helper, ProgressManager.progress(solo).isCompleted(ref("test_logic", "a")), "which stays with the solo team");
            helper.succeed();
        } finally {
            teams.leave(player.getUUID());
            leave(helper, player);
        }
    }

    // copy_to_new_team copies into a party with no progress, and leaves a party that has some alone.
    static void copyToNewTeam(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ServerPlayer other = join(helper);
        tick(player, "test_logic", "a");
        StubTeams teams = StubTeams.get();
        StubTeams.Party fresh = teams.party("fresh");
        StubTeams.Party busy = teams.party("busy");
        try {
            UUID solo = team(player);
            teams.join(busy, other.getUUID());
            ActiveTasks.refresh(other);
            tick(other, "test_logic", "b");
            teams.join(fresh, player.getUUID());
            ProgressManager.applyTeamChange(server(helper), player.getUUID(), solo, fresh.id(), TeamChange.COPY_TO_NEW_TEAM);
            check(helper, completed(player, "test_logic", "a"), "copy: a new party gets the player's progress");
            check(helper, ProgressManager.progress(solo).isCompleted(ref("test_logic", "a")), "and the solo team keeps it too");
            teams.join(busy, player.getUUID());
            ProgressManager.applyTeamChange(server(helper), player.getUUID(), fresh.id(), busy.id(), TeamChange.COPY_TO_NEW_TEAM);
            check(helper, !completed(player, "test_logic", "a") && completed(player, "test_logic", "b"),
                    "copy: a party that has progress keeps its own");
            helper.succeed();
        } finally {
            teams.leave(player.getUUID());
            teams.leave(other.getUUID());
            leave(helper, player, other);
        }
    }

    // merge: the party gets the union of both teams' progress, and completions follow from it.
    static void merge(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        ServerPlayer other = join(helper);
        tick(player, "test_logic", "a");
        StubTeams teams = StubTeams.get();
        StubTeams.Party party = teams.party("merge");
        try {
            UUID solo = team(player);
            teams.join(party, other.getUUID());
            ActiveTasks.refresh(other);
            tick(other, "test_logic", "b");
            check(helper, !completed(other, "test_logic", "needs_all"), "the party has only B");
            teams.join(party, player.getUUID());
            ProgressManager.applyTeamChange(server(helper), player.getUUID(), solo, party.id(), TeamChange.MERGE);
            check(helper, completed(other, "test_logic", "a") && completed(other, "test_logic", "b"), "merge: the party has A and B");
            check(helper, completed(other, "test_logic", "needs_all"), "and needs_all completes from the merged progress");
            helper.succeed();
        } finally {
            teams.leave(player.getUUID());
            teams.leave(other.getUUID());
            leave(helper, player, other);
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }
}

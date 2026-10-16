/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscuslib.api.team.TeamManager;
import software.hibiscus.hibiscuslib.api.team.TeamRank;
import software.hibiscus.hibiscuslib.api.team.TeamType;
import software.hibiscus.hibiscusquests.progress.QuestTeams;

// A stand-in teams mod for the gametests: parties made by a test, for the players it puts in them; everyone else is
// answered by whatever TeamsApi.get() is (Hibiscus Lib's fallback, or Hibiscus Teams in a run that has it). It's put in
// QuestTeams rather than installed through TeamsApi.install, which takes only one installer per run, so tests pass both
// with and without a teams mod.
final class StubTeams implements TeamManager {
    private static final StubTeams INSTANCE = new StubTeams();

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyOf = new ConcurrentHashMap<>();

    private StubTeams() {}

    // The stub, put in place the first time a test asks for it.
    static synchronized StubTeams get() {
        if (QuestTeams.override() != INSTANCE) {
            QuestTeams.setOverride(INSTANCE);
        }
        return INSTANCE;
    }

    private static TeamManager real() {
        return software.hibiscus.hibiscuslib.api.team.TeamsApi.get();
    }

    // A new empty party.
    Party party(String name) {
        Party party = new Party(UUID.randomUUID(), name);
        parties.put(party.id, party);
        return party;
    }

    void join(Party party, UUID player) {
        partyOf.put(player, party.id);
        party.members.put(player, party.members.isEmpty() ? TeamRank.OWNER : TeamRank.MEMBER);
    }

    void leave(UUID player) {
        UUID party = partyOf.remove(player);
        if (party != null && parties.containsKey(party)) {
            parties.get(party).members.remove(player);
        }
    }

    @Override
    public Team teamOf(UUID player) {
        UUID party = partyOf.get(player);
        return party != null && parties.containsKey(party) ? parties.get(party) : real().teamOf(player);
    }

    @Override
    public Optional<Team> team(UUID teamId) {
        Party party = parties.get(teamId);
        return party != null ? Optional.of(party) : real().team(teamId).map(team -> (Team) team);
    }

    @Override
    public Collection<? extends Team> allTeams() {
        List<Team> all = new ArrayList<>(real().allTeams());
        all.addAll(parties.values());
        return all;
    }

    @Override
    public Team playerTeam(UUID player) {
        return real().playerTeam(player);
    }

    static final class Party implements Team {
        private final UUID id;
        private final String name;
        private final Map<UUID, TeamRank> members = new LinkedHashMap<>();
        private final Map<Identifier, CompoundTag> extra = new HashMap<>();

        private Party(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public TeamType type() {
            return TeamType.PARTY;
        }

        @Override
        public Component displayName() {
            return Component.literal(name);
        }

        @Override
        public String shortName() {
            return name;
        }

        @Override
        public int color() {
            return 0xFFFFFF;
        }

        @Override
        public Optional<UUID> owner() {
            return members.entrySet().stream().filter(e -> e.getValue() == TeamRank.OWNER).map(Map.Entry::getKey).findFirst();
        }

        @Override
        public Map<UUID, TeamRank> members() {
            return Map.copyOf(members);
        }

        @Override
        public Set<UUID> allies() {
            return Set.of();
        }

        @Override
        public TeamRank rankOf(UUID player) {
            return members.getOrDefault(player, TeamRank.NONE);
        }

        @Override
        public List<ServerPlayer> onlineMembers(MinecraftServer server) {
            return members.keySet().stream().map(uuid -> server.getPlayerList().getPlayer(uuid)).filter(p -> p != null).toList();
        }

        @Override
        public CompoundTag extraData(Identifier owningMod) {
            return extra.computeIfAbsent(owningMod, key -> new CompoundTag());
        }

        @Override
        public void markDirty() {}
    }
}

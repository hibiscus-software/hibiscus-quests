/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

// A team's progress on one quest: task counters, when it was started and completed and by whom, which rewards were
// claimed (once for the team, or by each member), and how often a repeatable quest has come round. Times are
// milliseconds since the epoch, 0 for "not yet". Mutable; owned by its TeamProgress.
public final class QuestState {
    final Map<String, Long> tasks = new TreeMap<>();
    long started;
    long completed;
    @Nullable UUID completedBy;
    int repeats;
    long cooldownUntil;
    final Set<String> teamClaims = new TreeSet<>();
    // Who claimed each team reward, for "claimed by" in the book.
    final Map<String, UUID> teamClaimers = new TreeMap<>();
    final Map<UUID, Set<String>> playerClaims = new TreeMap<>();

    public long task(String taskId) {
        return tasks.getOrDefault(taskId, 0L);
    }

    public Map<String, Long> tasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public boolean isCompleted() {
        return completed > 0;
    }

    public long started() {
        return started;
    }

    public long completed() {
        return completed;
    }

    public Optional<UUID> completedBy() {
        return Optional.ofNullable(completedBy);
    }

    public int repeats() {
        return repeats;
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    // Whether a reward has been claimed: for a team reward by anyone, for a per-player reward by this player.
    public boolean isClaimed(String rewardId, boolean teamReward, UUID player) {
        return teamReward ? teamClaims.contains(rewardId) : playerClaims.getOrDefault(player, Set.of()).contains(rewardId);
    }

    public Set<String> teamClaims() {
        return Collections.unmodifiableSet(teamClaims);
    }

    public Optional<UUID> teamClaimer(String rewardId) {
        return Optional.ofNullable(teamClaimers.get(rewardId));
    }

    public Set<String> claimsOf(UUID player) {
        return Collections.unmodifiableSet(playerClaims.getOrDefault(player, Set.of()));
    }

    void claim(String rewardId, boolean teamReward, UUID player) {
        if (teamReward) {
            teamClaims.add(rewardId);
            teamClaimers.put(rewardId, player);
        } else {
            playerClaims.computeIfAbsent(player, key -> new TreeSet<>()).add(rewardId);
        }
    }

    // Starts the quest over (a repeatable quest coming round, or a reset): everything but the repeat count goes.
    void clear() {
        tasks.clear();
        started = 0;
        completed = 0;
        completedBy = null;
        cooldownUntil = 0;
        teamClaims.clear();
        teamClaimers.clear();
        playerClaims.clear();
    }

    public boolean isEmpty() {
        return tasks.isEmpty() && started == 0 && completed == 0 && repeats == 0 && cooldownUntil == 0
                && teamClaims.isEmpty() && playerClaims.isEmpty();
    }

    // Adds another team's progress to this one: the higher counter, the earlier start and completion, every claim.
    void mergeFrom(QuestState other) {
        other.tasks.forEach((id, value) -> tasks.merge(id, value, Math::max));
        started = earliest(started, other.started);
        if (other.completed > 0 && (completed == 0 || other.completed < completed)) {
            completed = other.completed;
            completedBy = other.completedBy;
        }
        repeats = Math.max(repeats, other.repeats);
        cooldownUntil = Math.max(cooldownUntil, other.cooldownUntil);
        teamClaims.addAll(other.teamClaims);
        other.teamClaimers.forEach(teamClaimers::putIfAbsent);
        other.playerClaims.forEach((player, claims) -> playerClaims.computeIfAbsent(player, key -> new TreeSet<>()).addAll(claims));
    }

    private static long earliest(long a, long b) {
        return a == 0 ? b : b == 0 ? a : Math.min(a, b);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!tasks.isEmpty()) {
            CompoundTag counters = new CompoundTag();
            tasks.forEach(counters::putLong);
            tag.put("tasks", counters);
        }
        putIfSet(tag, "started", started);
        putIfSet(tag, "completed", completed);
        if (completedBy != null) {
            tag.putString("completed_by", completedBy.toString());
        }
        if (repeats > 0) {
            tag.putInt("repeats", repeats);
        }
        putIfSet(tag, "cooldown_until", cooldownUntil);
        if (!teamClaims.isEmpty()) {
            tag.put("claimed_team", strings(teamClaims));
        }
        if (!teamClaimers.isEmpty()) {
            CompoundTag by = new CompoundTag();
            teamClaimers.forEach((reward, player) -> by.putString(reward, player.toString()));
            tag.put("claimed_team_by", by);
        }
        CompoundTag claims = new CompoundTag();
        playerClaims.forEach((player, ids) -> {
            if (!ids.isEmpty()) {
                claims.put(player.toString(), strings(ids));
            }
        });
        if (!claims.isEmpty()) {
            tag.put("claimed", claims);
        }
        return tag;
    }

    public static QuestState load(CompoundTag tag) {
        QuestState state = new QuestState();
        CompoundTag counters = tag.getCompoundOrEmpty("tasks");
        for (String id : counters.keySet()) {
            counters.getLong(id).ifPresent(value -> state.tasks.put(id, value));
        }
        state.started = tag.getLongOr("started", 0);
        state.completed = tag.getLongOr("completed", 0);
        state.completedBy = tag.getString("completed_by").flatMap(QuestState::uuid).orElse(null);
        state.repeats = tag.getIntOr("repeats", 0);
        state.cooldownUntil = tag.getLongOr("cooldown_until", 0);
        readStrings(tag.getListOrEmpty("claimed_team"), state.teamClaims);
        CompoundTag by = tag.getCompoundOrEmpty("claimed_team_by");
        for (String reward : by.keySet()) {
            by.getString(reward).flatMap(QuestState::uuid).ifPresent(player -> state.teamClaimers.put(reward, player));
        }
        CompoundTag claims = tag.getCompoundOrEmpty("claimed");
        for (String key : claims.keySet()) {
            uuid(key).ifPresent(player -> readStrings(claims.getListOrEmpty(key),
                    state.playerClaims.computeIfAbsent(player, k -> new TreeSet<>())));
        }
        return state;
    }

    private static void putIfSet(CompoundTag tag, String key, long value) {
        if (value != 0) {
            tag.putLong(key, value);
        }
    }

    static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    static void readStrings(ListTag list, Set<String> into) {
        for (Tag entry : list) {
            entry.asString().ifPresent(into::add);
        }
    }

    static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import software.hibiscus.hibiscusquests.api.QuestRef;

// One team's progress through the book: a QuestState per quest (keyed "chapter/quest"), the chapters it has completed
// (so each completion is announced once) and each member's pinned quests. Quests that aren't in the book any more are
// kept, so taking a quest out and putting it back loses nothing, until an op runs cleanup. Saved as
// <world>/hibiscusquests/progress/<team id>.snbt; the client gets the same tag for its own team.
public final class TeamProgress {
    public static final int VERSION = 1;

    private final UUID teamId;
    final Map<String, QuestState> quests = new TreeMap<>();
    final Set<String> completedChapters = new TreeSet<>();
    final Map<UUID, List<String>> pinned = new TreeMap<>();

    public TeamProgress(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    public @Nullable QuestState state(QuestRef quest) {
        return quests.get(quest.toString());
    }

    QuestState stateOrCreate(QuestRef quest) {
        return quests.computeIfAbsent(quest.toString(), key -> new QuestState());
    }

    public boolean isCompleted(QuestRef quest) {
        QuestState state = quests.get(quest.toString());
        return state != null && state.isCompleted();
    }

    public long task(QuestRef quest, String taskId) {
        QuestState state = quests.get(quest.toString());
        return state == null ? 0 : state.task(taskId);
    }

    public Map<String, QuestState> quests() {
        return Collections.unmodifiableMap(quests);
    }

    public Set<String> completedChapters() {
        return Collections.unmodifiableSet(completedChapters);
    }

    public List<String> pinned(UUID player) {
        return List.copyOf(pinned.getOrDefault(player, List.of()));
    }

    // Pins or unpins a quest for a player; false if pinning would go past max.
    boolean setPinned(UUID player, String quest, boolean pin, int max) {
        List<String> list = pinned.computeIfAbsent(player, key -> new ArrayList<>());
        if (!pin) {
            return list.remove(quest);
        }
        if (list.contains(quest)) {
            return true;
        }
        if (list.size() >= max) {
            return false;
        }
        list.add(quest);
        return true;
    }

    public boolean isEmpty() {
        return quests.values().stream().allMatch(QuestState::isEmpty) && completedChapters.isEmpty();
    }

    // Adds another team's progress to this one (merge, and copy into an empty team): see QuestState.mergeFrom.
    void mergeFrom(TeamProgress other) {
        other.quests.forEach((key, state) -> quests.computeIfAbsent(key, k -> new QuestState()).mergeFrom(state));
        completedChapters.addAll(other.completedChapters);
        other.pinned.forEach((player, list) -> pinned.putIfAbsent(player, new ArrayList<>(list)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("team", teamId.toString());
        CompoundTag questTags = new CompoundTag();
        quests.forEach((key, state) -> {
            if (!state.isEmpty()) {
                questTags.put(key, state.save());
            }
        });
        tag.put("quests", questTags);
        if (!completedChapters.isEmpty()) {
            tag.put("chapters_completed", QuestState.strings(completedChapters));
        }
        CompoundTag pins = new CompoundTag();
        pinned.forEach((player, list) -> {
            if (!list.isEmpty()) {
                pins.put(player.toString(), QuestState.strings(list));
            }
        });
        if (!pins.isEmpty()) {
            tag.put("pinned", pins);
        }
        return tag;
    }

    // Reads a saved tag. Throws IllegalArgumentException if it isn't a progress file this version can read.
    public static TeamProgress load(CompoundTag tag) {
        UUID id = tag.getString("team").flatMap(QuestState::uuid)
                .orElseThrow(() -> new IllegalArgumentException("it has no valid team id"));
        int version = tag.getIntOr("version", VERSION);
        if (version > VERSION) {
            throw new IllegalArgumentException("it was written by a newer Hibiscus Quests (format " + version + ")");
        }
        TeamProgress progress = new TeamProgress(id);
        CompoundTag questTags = tag.getCompoundOrEmpty("quests");
        for (String key : questTags.keySet()) {
            questTags.getCompound(key).ifPresent(state -> progress.quests.put(key, QuestState.load(state)));
        }
        QuestState.readStrings(tag.getListOrEmpty("chapters_completed"), progress.completedChapters);
        CompoundTag pins = tag.getCompoundOrEmpty("pinned");
        for (String key : pins.keySet()) {
            QuestState.uuid(key).ifPresent(player -> {
                List<String> list = new ArrayList<>();
                for (Tag entry : pins.getListOrEmpty(key)) {
                    entry.asString().ifPresent(list::add);
                }
                progress.pinned.put(player, list);
            });
        }
        return progress;
    }

    // The changes to some quests, for the client: {quests: {key: state}, removed: [key], chapters_completed: [...]}.
    public CompoundTag delta(Iterable<String> keys) {
        CompoundTag delta = new CompoundTag();
        CompoundTag changed = new CompoundTag();
        ListTag removed = new ListTag();
        for (String key : keys) {
            QuestState state = quests.get(key);
            if (state == null || state.isEmpty()) {
                removed.add(StringTag.valueOf(key));
            } else {
                changed.put(key, state.save());
            }
        }
        delta.put("quests", changed);
        delta.put("removed", removed);
        delta.put("chapters_completed", QuestState.strings(completedChapters));
        CompoundTag pins = new CompoundTag();
        pinned.forEach((player, list) -> pins.put(player.toString(), QuestState.strings(list)));
        delta.put("pinned", pins);
        return delta;
    }

    // Applies a delta from the server (client side): changed quests replace the old state, removed ones go.
    public void applyDelta(CompoundTag delta) {
        CompoundTag changed = delta.getCompoundOrEmpty("quests");
        for (String key : changed.keySet()) {
            changed.getCompound(key).ifPresent(state -> quests.put(key, QuestState.load(state)));
        }
        for (Tag entry : delta.getListOrEmpty("removed")) {
            entry.asString().ifPresent(quests::remove);
        }
        completedChapters.clear();
        QuestState.readStrings(delta.getListOrEmpty("chapters_completed"), completedChapters);
        delta.getCompound("pinned").ifPresent(pins -> {
            pinned.clear();
            for (String key : pins.keySet()) {
                QuestState.uuid(key).ifPresent(player -> {
                    List<String> list = new ArrayList<>();
                    for (Tag entry : pins.getListOrEmpty(key)) {
                        entry.asString().ifPresent(list::add);
                    }
                    pinned.put(player, list);
                });
            }
        });
    }
}

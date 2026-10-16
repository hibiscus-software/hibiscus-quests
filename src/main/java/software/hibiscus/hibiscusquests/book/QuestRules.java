/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// How a quest unlocks and completes: its dependencies (written "quest" in the same chapter or "chapter/quest"), the
// dependency mode, whether it's optional or repeatable, and whether its tasks go in order. Read from the same compound
// as the rest of the quest.
public record QuestRules(List<String> dependencies, DependencyMode dependencyMode, int minRequired, boolean optional,
        boolean repeatable, long repeatCooldown, boolean canRepeatWithoutClaiming, boolean ignoreRewardBlocking,
        ProgressionMode progressionMode) {
    public static final QuestRules DEFAULT = new QuestRules(List.of(), DependencyMode.ALL, 0, false, false, 0, false, false,
            ProgressionMode.FLEXIBLE);

    public static final MapCodec<QuestRules> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BookCodecs.list("dependencies", Codec.STRING).forGetter(QuestRules::dependencies),
            DependencyMode.CODEC.optionalFieldOf("dependency_mode", DependencyMode.ALL).forGetter(QuestRules::dependencyMode),
            Codec.intRange(0, 10000).optionalFieldOf("min_required", 0).forGetter(QuestRules::minRequired),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestRules::optional),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestRules::repeatable),
            // Seconds after a repeatable quest resets before it can progress again.
            Codec.LONG.optionalFieldOf("repeat_cooldown", 0L).forGetter(QuestRules::repeatCooldown),
            Codec.BOOL.optionalFieldOf("can_repeat_without_claiming", false).forGetter(QuestRules::canRepeatWithoutClaiming),
            Codec.BOOL.optionalFieldOf("ignore_reward_blocking", false).forGetter(QuestRules::ignoreRewardBlocking),
            ProgressionMode.CODEC.optionalFieldOf("progression_mode", ProgressionMode.FLEXIBLE).forGetter(QuestRules::progressionMode))
            .apply(i, QuestRules::new));

    public QuestRules {
        dependencies = List.copyOf(dependencies);
    }
}

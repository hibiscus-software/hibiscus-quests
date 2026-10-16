/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.api.reward.RewardTypes;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.api.task.TaskTypes;

// Codecs shared by the book's files: ids, colours, multi-line text, and the task and reward type dispatch (where the
// hibiscusquests: namespace may be left out, so files say type: "item").
public final class BookCodecs {
    // A chapter, quest, task or reward id: [a-z0-9_]+.
    public static final Codec<String> ID = Codec.STRING.validate(id -> QuestRef.isValidId(id)
            ? DataResult.success(id) : DataResult.error(() -> "Invalid id \"" + id + "\": ids are lowercase letters, digits and _"));

    // An RGB colour, written "#rrggbb" (an integer is read too).
    public static final Codec<Integer> COLOR = Codec.either(Codec.STRING, Codec.INT).comapFlatMap(
            either -> either.map(BookCodecs::parseColor, value -> DataResult.success(value & 0xFFFFFF)),
            color -> Either.left(String.format(Locale.ROOT, "#%06x", color & 0xFFFFFF)));

    // Text of several lines: a list of strings, or one string with line breaks.
    public static final Codec<List<String>> LINES = Codec.either(Codec.STRING.listOf(), Codec.STRING).xmap(
            either -> either.map(List::copyOf, text -> List.of(text.split("\n", -1))),
            Either::left);

    public static final Codec<TaskType<?>> TASK_TYPE = typeCodec(TaskTypes.REGISTRY, "task");
    public static final Codec<RewardType<?>> REWARD_TYPE = typeCodec(RewardTypes.REGISTRY, "reward");

    // A task's own fields, picked by its type field.
    public static final MapCodec<Task> TASK = TASK_TYPE.dispatchMap("type", Task::type, TaskType::codec);
    public static final MapCodec<Reward> REWARD = REWARD_TYPE.dispatchMap("type", Reward::type, RewardType::codec);

    private BookCodecs() {}

    private static DataResult<Integer> parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            if (hex.length() == 6) {
                return DataResult.success(Integer.parseInt(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error
        }
        return DataResult.error(() -> "Not a colour (#rrggbb): " + text);
    }

    // Reads "item" as hibiscusquests:item and "othermod:heat" as itself; writes built-in types without the namespace.
    private static <T> Codec<T> typeCodec(Registry<T> registry, String what) {
        return Codec.STRING.comapFlatMap(name -> {
            Identifier id = name.indexOf(':') < 0 ? Identifier.tryBuild(HibiscusQuests.MODID, name) : Identifier.tryParse(name);
            if (id == null) {
                return DataResult.error(() -> "Invalid " + what + " type \"" + name + "\"");
            }
            return registry.getOptional(id).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown " + what + " type \"" + name + "\" (is the mod that adds it installed?)"));
        }, type -> {
            Identifier id = registry.getKey(type);
            if (id == null) {
                throw new IllegalStateException("Unregistered " + what + " type " + type);
            }
            return id.getNamespace().equals(HibiscusQuests.MODID) ? id.getPath() : id.toString();
        });
    }

    // Splits description lines into pages at lines that are exactly "---".
    public static List<List<String>> pages(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        for (String line : lines) {
            if (line.strip().equals("---")) {
                pages.add(List.copyOf(page));
                page.clear();
            } else {
                page.add(line);
            }
        }
        pages.add(List.copyOf(page));
        return pages;
    }

    // A list field that's left out of the file when empty.
    public static <T> MapCodec<List<T>> list(String name, Codec<T> element) {
        return element.listOf().optionalFieldOf(name, List.of());
    }

    // An optional string field that's left out of the file when empty.
    public static MapCodec<String> text(String name) {
        return Codec.STRING.optionalFieldOf(name, "");
    }

    // Splits "a, b" style lists that people sometimes write as one string.
    public static List<String> splitList(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    // Optional.empty() for a blank string.
    public static Optional<String> nonBlank(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }
}

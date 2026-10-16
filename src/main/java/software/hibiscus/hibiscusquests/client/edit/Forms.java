/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.editor.FieldHint;
import software.hibiscus.hibiscusquests.api.editor.FieldHint.Kind;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.api.reward.RewardTypes;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.api.task.TaskTypes;

// The editor's forms: which fields each kind of object shows, in what order, with what controls. A field is a path in
// the object's file compound ("title", or "loot_crate.drop.boss" inside nested compounds) and a hint (FieldHint). Task
// and reward types get their fields from their codecs, with the built-in types' hints kept here and any type's own
// editorHints() on top.
public final class Forms {
    public static final String[] SHAPES = {"circle", "square", "rsquare", "diamond", "pentagon", "hexagon", "octagon", "heart", "gear"};

    // One field of a form. A heading has an empty path.
    public record Field(String path, Component label, FieldHint hint) {
        public boolean isHeading() {
            return path.isEmpty();
        }
    }

    private Forms() {}

    // The label for a field: the lang key hibiscusquests.field.<name>, else the name itself.
    public static Component label(String path) {
        String key = "hibiscusquests.field." + path;
        return Component.translatableWithFallback(key, path);
    }

    private static Field field(String path, FieldHint hint) {
        return new Field(path, label(path), hint);
    }

    private static Field heading(String key) {
        return new Field("", Component.translatable("hibiscusquests.form." + key), FieldHint.of(Kind.HIDDEN));
    }

    private static FieldHint of(Kind kind) {
        return FieldHint.of(kind);
    }

    private static FieldHint shapes() {
        return FieldHint.choice(SHAPES);
    }

    // ---- the book's own objects ----

    public static List<Field> quest() {
        return List.of(
                heading("look"),
                field("title", FieldHint.text()),
                field("subtitle", FieldHint.text()),
                field("icon", of(Kind.ICON)),
                field("description", of(Kind.LONG_TEXT)),
                field("shape", shapes()),
                field("size", FieldHint.decimal()),
                field("x", FieldHint.decimal()),
                field("y", FieldHint.decimal()),
                heading("visibility"),
                field("hide", FieldHint.choice("never", "until_unlocked", "until_dependencies_visible", "always_until_complete")),
                field("secret", FieldHint.bool()),
                field("invisible_until_tasks", FieldHint.integer()),
                field("hide_dependency_lines", of(Kind.OPTIONAL_BOOLEAN)),
                field("disable_toast", FieldHint.bool()),
                field("min_width", FieldHint.integer()),
                heading("rules"),
                field("dependencies", of(Kind.STRING_LIST)),
                field("dependency_mode", FieldHint.choice("all", "any", "one_of", "min")),
                field("min_required", FieldHint.integer()),
                field("optional", FieldHint.bool()),
                field("progression_mode", FieldHint.choice("flexible", "linear")),
                field("repeatable", FieldHint.bool()),
                field("repeat_cooldown", FieldHint.integer()),
                field("can_repeat_without_claiming", FieldHint.bool()),
                field("ignore_reward_blocking", FieldHint.bool()));
    }

    public static List<Field> questShape() {
        return List.of(field("shape", shapes()), field("size", FieldHint.decimal()));
    }

    public static List<Field> chapter(List<String> groups) {
        List<String> choices = new ArrayList<>(groups);
        List<Field> fields = new ArrayList<>();
        fields.add(field("title", FieldHint.text()));
        fields.add(field("subtitle", FieldHint.text()));
        fields.add(field("icon", of(Kind.ICON)));
        fields.add(choices.isEmpty() ? field("group", FieldHint.text()) : field("group", FieldHint.choice(choices.toArray(String[]::new))));
        fields.add(field("order", FieldHint.integer()));
        fields.add(heading("nodes"));
        fields.add(field("default_shape", shapes()));
        fields.add(field("default_size", FieldHint.decimal()));
        fields.add(field("default_hide_dependency_lines", FieldHint.bool()));
        fields.add(heading("rules"));
        fields.add(field("always_invisible", FieldHint.bool()));
        fields.add(field("require_sequential", FieldHint.bool()));
        fields.add(field("requires", of(Kind.STRING_LIST)));
        fields.add(field("requires_mods", of(Kind.STRING_LIST)));
        return fields;
    }

    public static List<Field> group() {
        return List.of(field("title", FieldHint.text()), field("icon", of(Kind.ICON)), field("collapsed", FieldHint.bool()));
    }

    public static List<Field> settings() {
        return List.of(
                field("title", FieldHint.text()),
                field("icon", of(Kind.ICON)),
                field("default_shape", shapes()),
                field("default_size", FieldHint.decimal()),
                field("background", FieldHint.text()),
                field("accent", of(Kind.COLOR)),
                field("lock_message", FieldHint.text()),
                field("pause_game", FieldHint.bool()),
                heading("progress"),
                field("progress_mode", FieldHint.choice("team", "player")),
                field("team_change", FieldHint.choice("keep_with_team", "copy_to_new_team", "merge")),
                field("drop_loot_crates", FieldHint.bool()),
                field("emergency_items", of(Kind.ITEM_LIST)),
                field("emergency_cooldown", FieldHint.integer()),
                field("disabled_sources", of(Kind.STRING_LIST)));
    }

    public static List<Field> image() {
        return List.of(
                field("image", of(Kind.ICON)),
                field("x", FieldHint.decimal()),
                field("y", FieldHint.decimal()),
                field("width", FieldHint.decimal()),
                field("height", FieldHint.decimal()),
                field("rotation", FieldHint.decimal()),
                field("alpha", FieldHint.decimal()),
                field("layer", FieldHint.choice("behind", "front")),
                field("click", FieldHint.text()));
    }

    public static List<Field> label() {
        return List.of(
                field("text", FieldHint.text()),
                field("x", FieldHint.decimal()),
                field("y", FieldHint.decimal()),
                field("scale", FieldHint.decimal()),
                field("color", of(Kind.COLOR)));
    }

    public static List<Field> link() {
        return List.of(
                field("quest", of(Kind.QUEST)),
                field("x", FieldHint.decimal()),
                field("y", FieldHint.decimal()),
                field("size", FieldHint.decimal()),
                field("shape", shapes()));
    }

    public static List<Field> table() {
        return List.of(
                field("title", FieldHint.text()),
                field("icon", of(Kind.ICON)),
                field("rolls", FieldHint.integer()),
                field("empty_weight", FieldHint.integer()),
                heading("loot_crate"),
                field("loot_crate.name", FieldHint.text()),
                field("loot_crate.color", of(Kind.COLOR)),
                field("loot_crate.drop.passive", FieldHint.decimal()),
                field("loot_crate.drop.hostile", FieldHint.decimal()),
                field("loot_crate.drop.boss", FieldHint.decimal()));
    }

    // ---- tasks and rewards ----

    // A task: the fields every task has, then the type's own.
    public static List<Field> task(Identifier typeId, TaskType<?> type) {
        List<Field> fields = new ArrayList<>();
        fields.add(field("title", FieldHint.text()));
        fields.add(field("icon", of(Kind.ICON)));
        fields.add(field("optional_task", FieldHint.bool()));
        fields.add(field("hide_until_started", FieldHint.bool()));
        fields.add(field("counts_while_locked", of(Kind.OPTIONAL_BOOLEAN)));
        fields.add(new Field("", Component.translatable("hibiscusquests.form.type", typeName(typeId, true)), FieldHint.of(Kind.HIDDEN)));
        Map<String, FieldHint> hints = new HashMap<>(BUILTIN_TASKS.getOrDefault(typeId, Map.of()));
        hints.putAll(type.editorHints());
        fields.addAll(typeFields(type.codec(), hints));
        return fields;
    }

    // A reward: the fields every reward has (not for a reward table's entries, which have a weight instead), then the
    // type's own.
    public static List<Field> reward(Identifier typeId, RewardType<?> type, boolean tableEntry) {
        List<Field> fields = new ArrayList<>();
        if (tableEntry) {
            fields.add(field("weight", FieldHint.integer()));
        } else {
            fields.add(field("team_reward", FieldHint.bool()));
            fields.add(field("auto_claim", FieldHint.choice("off", "enabled", "no_toast", "invisible")));
            fields.add(field("exclude_from_claim_all", FieldHint.bool()));
        }
        fields.add(new Field("", Component.translatable("hibiscusquests.form.type", typeName(typeId, false)), FieldHint.of(Kind.HIDDEN)));
        Map<String, FieldHint> hints = new HashMap<>(BUILTIN_REWARDS.getOrDefault(typeId, Map.of()));
        hints.putAll(type.editorHints());
        fields.addAll(typeFields(type.codec(), hints));
        return fields;
    }

    // A type's own fields, from its codec's keys, in the codec's order.
    private static List<Field> typeFields(MapCodec<?> codec, Map<String, FieldHint> hints) {
        Set<String> names = new LinkedHashSet<>();
        codec.keys(ClientEditor.ops()).forEach(key -> key.asString().ifPresent(names::add));
        names.addAll(hints.keySet());
        List<Field> fields = new ArrayList<>();
        for (String name : names) {
            FieldHint hint = hints.get(name);
            if (hint != null && hint.kind() == Kind.HIDDEN) {
                continue;
            }
            fields.add(new Field(name, label(name), hint));
        }
        return fields;
    }

    // A type's name: the lang key hibiscusquests.task_type.<namespace>.<path> (or reward_type), else its id.
    public static Component typeName(Identifier typeId, boolean task) {
        String key = "hibiscusquests." + (task ? "task_type." : "reward_type.") + typeId.getNamespace() + "." + typeId.getPath();
        return Component.translatableWithFallback(key, typeId.getNamespace().equals(HibiscusQuests.MODID) ? typeId.getPath() : typeId.toString());
    }

    public static List<Identifier> taskTypes() {
        return TaskTypes.REGISTRY.keySet().stream().sorted().toList();
    }

    public static List<Identifier> rewardTypes() {
        return RewardTypes.REGISTRY.keySet().stream().sorted().toList();
    }

    // ---- the built-in types' hints ----

    private static final Map<Identifier, Map<String, FieldHint>> BUILTIN_TASKS = Map.ofEntries(
            Map.entry(HibiscusQuests.id("item"), Map.of(
                    "item", FieldHint.itemStack(),
                    "tag", FieldHint.tag(Registries.ITEM),
                    "count", FieldHint.integer(),
                    "mode", FieldHint.choice("detect", "consume"),
                    "only_from_crafting", FieldHint.bool())),
            Map.entry(HibiscusQuests.id("advancement"), Map.of(
                    "advancement", FieldHint.id(Registries.ADVANCEMENT),
                    "criterion", FieldHint.text())),
            Map.entry(HibiscusQuests.id("criterion"), Map.of("count", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("kill"), Map.of(
                    "entity", FieldHint.idOrTag(Registries.ENTITY_TYPE),
                    "count", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("location"), Map.of(
                    "dimension", FieldHint.id(Registries.DIMENSION),
                    "x", FieldHint.integer(),
                    "y", FieldHint.integer(),
                    "z", FieldHint.integer(),
                    "radius", FieldHint.decimal())),
            Map.entry(HibiscusQuests.id("dimension"), Map.of("dimension", FieldHint.id(Registries.DIMENSION))),
            Map.entry(HibiscusQuests.id("biome"), Map.of("biome", FieldHint.idOrTag(Registries.BIOME))),
            Map.entry(HibiscusQuests.id("structure"), Map.of("structure", FieldHint.idOrTag(Registries.STRUCTURE))),
            Map.entry(HibiscusQuests.id("stat"), Map.of(
                    "stat_type", FieldHint.id(Registries.STAT_TYPE),
                    "stat", FieldHint.text(),
                    "count", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("xp"), Map.of(
                    "amount", FieldHint.integer(),
                    "points", FieldHint.bool(),
                    "consume", FieldHint.bool())),
            Map.entry(HibiscusQuests.id("observation"), Map.of(
                    "block", FieldHint.idOrTag(Registries.BLOCK),
                    "entity", FieldHint.idOrTag(Registries.ENTITY_TYPE),
                    "ticks", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("fluid"), Map.of(
                    "fluid", FieldHint.idOrTag(Registries.FLUID),
                    "amount", FieldHint.integer(),
                    "mode", FieldHint.choice("consume", "detect"))),
            Map.entry(HibiscusQuests.id("energy"), Map.of(
                    "amount", FieldHint.integer(),
                    "max_rate", FieldHint.integer())));

    private static final Map<Identifier, Map<String, FieldHint>> BUILTIN_REWARDS = Map.ofEntries(
            Map.entry(HibiscusQuests.id("item"), Map.of(
                    "item", FieldHint.itemStack(),
                    "count", FieldHint.integer(),
                    "to_all_online", FieldHint.bool())),
            Map.entry(HibiscusQuests.id("xp"), Map.of("amount", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("xp_levels"), Map.of("levels", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("command"), Map.of(
                    "command", FieldHint.text(),
                    "title", FieldHint.text(),
                    "silent", FieldHint.bool())),
            Map.entry(HibiscusQuests.id("choice"), Map.of("items", FieldHint.of(Kind.ITEM_LIST))),
            Map.entry(HibiscusQuests.id("random"), Map.of("table", FieldHint.of(Kind.REWARD_TABLE))),
            Map.entry(HibiscusQuests.id("loot"), Map.of(
                    "table", FieldHint.of(Kind.REWARD_TABLE),
                    "count", FieldHint.integer())),
            Map.entry(HibiscusQuests.id("advancement"), Map.of("advancement", FieldHint.id(Registries.ADVANCEMENT))),
            Map.entry(HibiscusQuests.id("toast"), Map.of(
                    "title", FieldHint.text(),
                    "message", FieldHint.text(),
                    "icon", FieldHint.of(Kind.ICON))));
}

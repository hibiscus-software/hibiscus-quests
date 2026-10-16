# Hibiscus Quests for mod developers

There are two ways for a mod to work with Hibiscus Quests:

- **Ship quests as data** (most mods): chapter files in your jar, no code and no compile dependency. Without Hibiscus
  Quests installed the files are simply ignored.
- **Use the API** (`hibiscusquests-api`): add task or reward types, push progress from your own code, ask whether a
  team has done something, or react to completions. How to add the dependency is in [DEPENDING.md](DEPENDING.md).

## Shipping quests as data

Put your chapters in your mod's resources:

```
src/main/resources/data/<your mod id>/hibiscusquests/
  chapter_groups.snbt          optional: groups for the chapter list
  chapters/<chapter id>.snbt   one chapter per file
  reward_tables/<id>.snbt      optional: reward tables for random rewards and loot crates
```

and declare Hibiscus Quests as an optional dependency in `neoforge.mods.toml` (so it loads first when it's there):

```toml
[[dependencies.${mod_id}]]
    modId="hibiscusquests"
    type="optional"
    versionRange="[1.0.0,)"
    ordering="NONE"
    side="BOTH"
```

These files load through the data-pack system: `/reload` re-reads them, and a data pack can replace one by shipping the
same path. A modpack can also restyle or replace your chapters in its own files, or hide them with
`disabled_sources: ["<your mod id>"]` in its `data.snbt` (see "The book" below). Your chapters show a lock in the
book's editor; an op's **Override** copies one into the pack's files to edit it.

Chapter ids are shared by every mod in the book, so prefix yours with your mod id (`arcforge_steelmaking`, not
`steelmaking`). Quest, task and reward ids only need to be unique within their chapter and quest.

### The easiest way to write them

Build the chapter in the in-game editor in a dev world, then export it as mod-ready files with
`/hibiscusquests export_chapter <chapter> <your mod id>`; it writes them to
`hibiscusquests_exports/data/<your mod id>/hibiscusquests/`, ready to copy into `src/main/resources/`. (The editor and
`export_chapter` arrive with Hibiscus Quests 1.0.0; the files are the same either way, and can always be written by
hand.)

### The chapter file

SNBT (Minecraft's text format for NBT: `key: value`, `"strings"`, `1b`/`true` for booleans, `16L` for longs, lists in
`[...]`). Every field but `id` on a quest can be left out; files written by the editor leave out every field that has
its default value. A full example (the `//` comments are only for this page: SNBT has no comments, so leave them out
of real files; the demo book in `src/main/resources/datapacks/demo/` has working files to copy):

```snbt
{
    id: "mymod_basics",                  // the chapter's id (the file name is used if it's left out)
    group: "mymod",                      // a group from chapter_groups.snbt
    order: 0,                            // place within the group
    title: "The Basics",                 // literal text, a lang key, or {translate:some.key}
    subtitle: "Where every engineer starts",
    icon: "mymod:wrench",                // an item id, a stack {id: ..., components: {...}}, or {texture: "mymod:textures/..."}
    requires: ["mymod_intro"],           // chapters (or "chapter/quest") that must be complete before this one opens
    require_sequential: false,           // quests unlock one after another, in the order listed
    requires_mods: ["mymod"],            // leave the chapter out unless these mods are installed
    quests: [
        {
            id: "wrench",
            title: "Turn of the Wrench",
            subtitle: "Every machine starts here",
            description: [
                "# The Wrench",
                "Right-click a machine to **rotate** it, sneak-click to pick it up.",
                "---",
                "A second page. Shows {item:mymod:wrench} inline, links to {quest:mymod_basics/furnace}."
            ],
            x: 0.0d, y: 0.0d,            // position on the chapter's grid
            shape: "gear", size: 1.5d,   // circle, square, rsquare, diamond, pentagon, hexagon, octagon, heart, gear
            tasks: [
                {id: "wrench", type: "item", item: "mymod:wrench", count: 1L}
            ],
            rewards: [
                {id: "iron", type: "item", item: "minecraft:iron_ingot", count: 8},
                {id: "levels", type: "xp_levels", levels: 2}
            ]
        },
        {
            id: "furnace",
            title: "Fire It Up",
            x: 2.0d, y: 0.0d,
            dependencies: ["wrench"],    // same chapter: "quest"; another chapter: "chapter/quest"
            tasks: [
                {
                    id: "formed",
                    type: "criterion",
                    title: "Form a Furnace",
                    icon: "mymod:furnace_bricks",
                    criterion: {trigger: "mymod:multiblock_formed", conditions: {multiblock: "mymod:furnace"}}
                }
            ],
            rewards: [
                {id: "coal", type: "item", item: "minecraft:coal", count: 16, team_reward: true}
            ]
        }
    ]
}
```

`chapter_groups.snbt`:

```snbt
{groups: [{id: "mymod", title: "My Mod", icon: "mymod:wrench", order: 10}]}
```

#### Quest fields

| Field | Default | |
|---|---|---|
| `id` | (required) | `[a-z0-9_]+`, unique in the chapter |
| `title`, `subtitle` | | text (see Text below) |
| `icon` | the first task's | item id, stack or `{texture: ...}` |
| `description` | | a list of lines (or one string); a line `---` starts a new page |
| `x`, `y` | 0 | grid position |
| `shape`, `size` | the chapter's, then the book's | `size` from 0.5 to 3 |
| `dependencies` | none | quests that must be complete first |
| `dependency_mode` | `all` | `all`, `any`, `one_of` (any; completing one `one_of` quest locks the others sharing a dependency: a branch), `min` (with `min_required`) |
| `hide` | `never` | `until_unlocked`, `until_dependencies_visible`, `always_until_complete` |
| `secret` | false | shown as "?" until unlocked |
| `optional` | false | doesn't count toward the chapter's completion |
| `repeatable`, `repeat_cooldown` | false, 0 | starts over once its rewards are claimed; the cooldown is in seconds, real time |
| `progression_mode` | `flexible` | `linear`: tasks count one at a time, in order |
| `hide_dependency_lines`, `invisible_until_tasks`, `min_width`, `disable_toast` | | display options |

#### Task fields

Every task has an `id` (`task_1`, `task_2`... if left out; set it if anything refers to the task), a `type`, and
optionally `title`, `icon`, `optional_task: true` (the quest completes without it), `hide_until_started: true` and
`counts_while_locked` (override whether progress made while the quest is locked counts).

| Type | Fields | Completes when |
|---|---|---|
| `item` | `item` (id or stack), `tag`, `predicate` (an item predicate, as in advancements), `count` (default 1), `mode` (`detect` default, or `consume`), `only_from_crafting` | the player holds the items (`detect`), hands them in with Submit (`consume`), or crafts them |
| `checkmark` | | the player ticks it in the book |
| `advancement` | `advancement`, optional `criterion` | the player has the advancement (or that criterion of it) |
| `criterion` | `criterion: {trigger, conditions}`, `count` (default 1) | the trigger fires with matching conditions, `count` times |
| `kill` | `entity` (id or `#tag`), `count` (default 1) | the player kills that many (directly or with a projectile they shot) |
| `location` | `x`, `y`, `z` and `radius`, or `box: [x1, y1, z1, x2, y2, z2]`; optional `dimension` | the player is within `radius` blocks of the point, or inside the box |
| `dimension` | `dimension` | the player is in the dimension |
| `biome` | `biome` (id or `#tag`) | the player is in the biome |
| `structure` | `structure` (id or `#tag`) | the player is inside one of the structure's pieces |
| `stat` | `stat_type` (default `minecraft:custom`), `stat`, `count` | the player's statistic reaches `count` (`minecraft:mined` + `minecraft:stone`, `minecraft:custom` + `minecraft:jump`...) |
| `xp` | `amount`, `points` (default false: levels), `consume` (default true) | the player hands in that many levels (or points) with Submit; with `consume: false`, reaching them is enough |
| `observation` | `block` or `entity` (id or `#tag`), `ticks` (default 20) | the player has looked at one, from up to 8 blocks away, for `ticks` ticks in all |
| `fluid` | `fluid` (id or `#tag`), `amount` (mB), `mode` (`consume` default, or `detect`) | the player hands in that much from buckets, tanks and other fluid-holding items, or carries it |
| `energy` | `amount` (FE), `max_rate` (default 0, no cap) | the player hands in that much from energy-holding items; `max_rate` caps one press of Submit |

The place tasks (`location`, `dimension`, `biome`, `structure`) are checked once a second, and as soon as the task
starts, so being there already counts. `fluid` and `energy` use NeoForge's item capabilities, so any mod's tanks and
batteries work.

#### Reward fields

Every reward has an `id` (`reward_1`... if left out), a `type`, and optionally `team_reward: true` (claimed once for
the whole team; otherwise each member claims their own), `auto_claim` (`off`, `enabled`, `no_toast`, `invisible`) and
`exclude_from_claim_all: true`.

| Type | Fields | |
|---|---|---|
| `item` | `item` (id or stack), `count`, `to_all_online` | for a team reward, `to_all_online` gives one to every member online |
| `xp` | `amount` | experience points |
| `xp_levels` | `levels` | |
| `command` | `command`, `title`, `silent` | runs as the server; `{p}` the claimer's name, `{uuid}` their UUID, `{team}` their team's short name, `{quest}` the quest |
| `choice` | `items` (2 to 12, ids or stacks) | the player picks one in a dialog; left out of Claim all and auto-claim |
| `random` | `table` | rolls the reward table now and hands out what comes up |
| `loot` | `table`, `count` (default 1) | that many of the table's loot crates, opened later with a right click |
| `advancement` | `advancement` | grants every criterion of it the player still lacks |
| `toast` | `title`, `message`, `icon` | just a toast (for a team reward, to every member online) |

A quest with `repeatable: true` and `can_repeat_without_claiming: true` starts over the moment it completes: its
rewards (other than `choice`) go to the player who completed it, without waiting for a claim.

#### Reward tables

`reward_tables/<id>.snbt`, one table per file, merged like chapters (a pack file with the same id replaces a mod's,
`remove: true` drops it, `disabled_sources` hides a namespace's):

```snbt
{
    title: "Treasure",
    icon: "minecraft:chest",
    rolls: 2,
    empty_weight: 10,
    entries: [
        {weight: 10, type: "item", item: "minecraft:diamond"},
        {weight: 30, type: "xp", amount: 50}
    ],
    loot_crate: {name: "Treasure Crate", color: "#e8b04b", drop: {passive: 0.0d, hostile: 0.01d, boss: 0.5d}}
}
```

Each of the `rolls` picks one entry by weight, or nothing with `empty_weight`. Entries are rewards of any type but
`random`, `loot` and `choice`, with a `weight` (default 1). The book shows a table's entries and their chances when you
hover over a reward that uses it. With a `loot_crate`, the table has its own crate item, named and coloured as given;
mobs a player kills drop one with the chance for their kind (bosses are `#c:bosses`), unless `drop_loot_crates: false`
in `data.snbt` or the server's `rewards.lootCrateDrops` is off. A crate's rewards reach the API as a claim of the quest
`loot_crate/<table>`.

#### Text

Titles, subtitles and descriptions may be plain text (with `&` formatting codes), a lang key (`mymod.quest.wrench`,
shown as written when there's no translation), or `{translate:mymod.quest.wrench}`. Ship the translations in your
mod's `assets/<your mod id>/lang/` files to have your quests translated. Descriptions also take `# headings`,
`- bullets`, `**bold**`, `*italic*`, `[links](https://...)`, `{item:id}`, `{quest:chapter/quest}` and
`{image:<texture> <width> <height>}` on a line of its own (a texture path such as
`mymod:textures/gui/map.png`).

### Criterion tasks: your mod's own triggers

A `criterion` task listens to any registered advancement trigger, so every custom trigger your mod already has (for
its own advancements) works as a quest task with no code in your mod:

```snbt
{type: "criterion", count: 3L,
 criterion: {trigger: "arcforge:multiblock_formed", conditions: {multiblock: "arcforge:arcforge_furnace"}}}
```

The criterion is written exactly as it would be in an advancement's `criteria`, and parsed with your trigger's own
codec. Nothing is added to the player's advancements, so the advancements screen doesn't change and no advancement is
announced. Triggers that extend vanilla's `SimpleCriterionTrigger` (as nearly all do) work as they are; progress made
while the quest is locked counts, so a quest whose deed was done early completes as soon as it unlocks.

## The API

Everything starts at `QuestsApi.get()`, on the server thread:

```java
QuestService quests = QuestsApi.get();
UUID team = quests.teamIdOf(player);                     // the player's team (or the player, in progress_mode player)
QuestRef furnace = new QuestRef("mymod_basics", "furnace");
if (quests.isCompleted(team, furnace)) { ... }
QuestStatus status = quests.progress(team, furnace);     // task counters, fraction, when it was started and completed
ChapterStatus chapter = quests.chapterProgress(team, "mymod_basics");
quests.book().chapter("mymod_basics");                   // read-only view of the book
```

Only touch these classes when Hibiscus Quests is installed (`ModList.get().isLoaded("hibiscusquests")`): keep the
calls in a class of their own that you only load then.

### Pushing progress

For something your mod tracks itself, without a task type of its own, point a task at it in the chapter (any type will
do; a `checkmark` with a custom `title` reads well) and push progress to it:

```java
QuestsApi.get().addProgress(player, new QuestRef("mymod_basics", "heat"), "heat", 1);
QuestsApi.get().complete(player, new QuestRef("mymod_basics", "furnace"));   // every task of the quest
```

`addProgress` is ignored (and returns false) while the task isn't being tracked: its quest is locked and the task
doesn't count while locked, or the quest is complete.

### Custom task types

A task type is a codec for the task's fields plus how the server tracks it. Register it from your mod constructor:

```java
public record HeatTask(int celsius) implements Task {
    public static final MapCodec<HeatTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("celsius").forGetter(HeatTask::celsius)).apply(i, HeatTask::new));

    public static final TaskType<HeatTask> TYPE = new TaskType<>() {
        @Override
        public MapCodec<HeatTask> codec() {
            return CODEC;
        }

        // Polled every second while the task is active for a player.
        @Override
        public int pollInterval() {
            return 20;
        }

        @Override
        public void poll(ActiveTask<HeatTask> task) {
            if (MyHeat.bodyTemperature(task.player()) >= task.task().celsius()) {
                task.complete();
            }
        }
    };

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public Component title() {
        return Component.translatable("mymod.task.heat", celsius);
    }
}

// in your mod constructor (only when Hibiscus Quests is installed):
TaskTypes.register(Identifier.fromNamespaceAndPath(MODID, "heat"), HeatTask.TYPE);
```

Chapter files then use `{type: "mymod:heat", celsius: 40}`. Register the type on both sides: the client decodes the
book too. A type tracks progress through any mix of:

- **events**: subscribe to a NeoForge event yourself and update the player's active tasks of your type,
  `QuestsApi.get().activeTasks(player, TYPE)`;
- **polling**: `pollInterval()` and `poll(task)`;
- **submitting**: `canSubmit(task)` and `submit(task)`, for a Submit button in the book;
- **activation**: `onActivated` / `onDeactivated` bracket the time a task is tracked for a player (it's unlocked and
  unfinished, the player is online), for types that attach listeners.

`Task.countsWhileLocked()` says whether progress counts before the quest unlocks, and `icon()` and `title()` what the
book shows when the chapter doesn't override them. Reward types work the same way: implement `Reward` and
`RewardType` (`codec()` and `claim(context, reward)`) and register with `RewardTypes.register`. The `RewardContext`
carries the player, their team, the quest and reward, whether it's a team reward, the members online, and `choice()`
(the index picked in a choice dialog, or -1).

### Events

Posted on `NeoForge.EVENT_BUS` on the server thread:

| Event | When |
|---|---|
| `TaskProgressEvent` | a task's progress changed (`oldProgress`, `newProgress`, `finished()`) |
| `QuestCompletedEvent` | a team completed a quest |
| `ChapterCompletedEvent` | a team completed every non-optional quest of a chapter, the first time |
| `RewardClaimedEvent.Pre` | a reward is about to be handed out; cancel it to stop the claim |
| `RewardClaimedEvent.Post` | a reward was handed out and recorded |
| `BookReloadedEvent` | the book was (re)built: server start, `/reload`, `/hibiscusquests reload`, edits |

And on the client: `QuestBookOpenEvent`, as the book is about to open (cancel it to keep it shut), with the quest it
opens on, if any.

## The book (for packs)

The pack's own files, which the editor reads and writes, are in `config/hibiscusquests/` (or a world's
`serverconfig/hibiscusquests/`, which wins when it exists): `data.snbt` (settings), `chapter_groups.snbt` and
`chapters/<id>.snbt`, in the same format as above. They're merged with the chapters mods ship:

- a pack chapter with the same id as a mod's replaces it;
- with `merge: true`, the pack chapter is laid over the mod's instead: the fields it has replace the mod's, its quests
  and quest links replace the mod's ones with the same id (or are added), and a quest with `remove: true` is dropped;
- `remove: true` on a chapter drops it;
- `disabled_sources` in `data.snbt` lists namespaces or chapter ids to leave out.

## Versioning

`HibiscusQuestsApi.API_VERSION` follows semantic versioning on its own, separately from the mod: a patch for docs and
fixes, a minor for additions (new types, new **default** methods on interfaces, new enum constants), a major for any
change or removal. It's `0.x` until Hibiscus Quests 1.0.0 and may still change between minor versions until then; from
1.0.0 the API only grows.

# Changelog

All notable changes to Hibiscus Quests are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.0.0+26.3`), set by `mod_version` and `minecraft_version` in
`gradle.properties`, and each is released as the tag `vMAJOR.MINOR.PATCH+MINECRAFT`. Each Minecraft version has its own
branch and its own changelog; this one is Minecraft 26.3's (`main`). The same version number on two Minecraft versions
has the same changes.

- **PATCH** (1.0.0 → 1.0.1): bug fixes and small tweaks. Books, progress and configs carry over untouched.
- **MINOR** (1.0.0 → 1.1.0): new features, task or reward types, commands or settings. Books and progress carry over.
- **MAJOR** (1.x → 2.0.0): anything that breaks book files, saved progress, configs or mods using the API.

The API jar (`hibiscusquests-api`) has its own version, `HibiscusQuestsApi.API_VERSION`; see
[docs/API.md](docs/API.md).

Changes land under **Unreleased** as they're made; when a version is released, that section gets its number and
date. How to cut a release is in [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

Suggested version: 1.0.0 (the first release). Needs Hibiscus Lib 1.4.0 (its new text markup and `TextArea`).

### Added

- **The quest book's core.** One book per server, merged from the pack's own files (`config/hibiscusquests/`, or a
  world's `serverconfig/hibiscusquests/`) and chapters that mods and data packs ship as data
  (`data/<namespace>/hibiscusquests/chapters/*.snbt` and `chapter_groups.snbt`, reloaded with `/reload`). A pack file
  with a mod chapter's id replaces it, or with `merge: true` changes single fields and quests; `disabled_sources` in
  `data.snbt` hides a mod's chapters; `remove: true` drops a chapter or quest; `requires_mods` leaves a chapter out
  when a mod isn't installed. Files are readable SNBT with sorted keys and readable ids; one that won't parse is
  reported (in the log and by `/hibiscusquests reload`) and skipped.
- **Chapters, groups and quests** with the full data model: dependencies (`all`, `any`, `one_of` branches that lock
  each other, `min`), `require_sequential` chapters, chapter gates (`requires`), optional and repeatable quests (with a
  real-time `repeat_cooldown`), linear task order, and the display fields the book will use (shapes, sizes, hiding,
  secret quests, descriptions with pages, quest links, images and labels).
- **Tasks:** `item` (detect held items, or submit them; by item, tag or item predicate; or count crafted items),
  `checkmark`, `advancement` (or one criterion of it) and `criterion`, which listens to any vanilla or modded
  advancement trigger written like an advancement criterion, without adding anything to the player's advancements.
  Detected items and triggers count while a quest is locked (`counts_while_locked` per task), so it completes the
  moment it unlocks.
- **Rewards:** `item`, `xp`, `xp_levels` and `command` (`{p}`, `{uuid}`, `{team}` and `{quest}` placeholders), each
  per player or once per team, with auto-claim and Claim all.
- **Progress per team** through the Hibiscus Lib Teams API: a player's party with Hibiscus Teams, else just them
  (`progress_mode = player` keeps everyone separate). When a player changes team, their progress stays with the old
  team, is copied into a new empty team, or is merged (`team_change`). A deleted party's progress is archived. Saved as
  one SNBT file per team in `<world>/hibiscusquests/progress/`, shortly after each change and when the server stops; a
  file that won't load is left untouched. Quests that leave the book keep their progress.
- **Sync:** the book is sent compressed, in chunks, behind a content hash, so an unchanged book isn't downloaded again
  (clients cache books on disk); progress goes as per-team deltas to every online member.
- **Commands:** `/hibiscusquests` (alias `/hquests`) `claim_all`, and for ops `reload`, `change_progress <player|team>
  <reset|complete> <quest|chapter|*>` and `debug <quest>` (why it's locked, each task's progress), behind the
  permission nodes `hibiscusquests.admin.reload`, `.change_progress` and `.debug`.
- **Configuration:** `hibiscusquests-server.toml` (progress mode, team change, the inventory check interval, chapter
  completion announcements).
- **The API** (`hibiscusquests-api`, version 0.1.0): task and reward type registries, queries (`isCompleted`,
  `progress`, `chapterProgress`, `book`), progress pushes (`addProgress`, `complete`), and the events
  `QuestCompletedEvent`, `ChapterCompletedEvent`, `TaskProgressEvent`, `RewardClaimedEvent.Pre`/`Post` and
  `BookReloadedEvent`.
- **A demo book** as a built-in data pack, off unless switched on with `/datapack enable`: a tour of every shape and
  state, and a chapter per task type.
- **The quest book screen**, from a button by the inventory (with a gold count of rewards waiting), an unbound "Open
  Quest Book" key, or `/hibiscusquests open [quest]`. It fills the window: the chapter list on the left (groups that
  fold, a progress bar per chapter, a gold dot where rewards wait, a lock on chapters still gated; collapsible to
  icons), a top bar with the chapter's title and progress, search, Claim all and your team (opening Hibiscus Teams'
  screen when it's installed), and the chapter's map: quests as nodes in nine shapes, joined by dependency lines,
  with the chapter's images, labels and links to other chapters' quests. Drag to pan, scroll to zoom (0.5x to 3x);
  each chapter remembers its view. Nodes show whether a quest is locked, open, in progress (a ring), complete with
  rewards waiting (pulsing gold) or done (a check); secret quests show "?". Lines into open quests have moving dashes.
- **The quest view**, over the map: description pages (with headings, bold and italic, inline item icons, pictures and
  links to other quests), tasks with progress bars, Submit buttons and checkboxes, rewards with a Claim button each
  (and who on the team claimed a team reward), and the quests still needed. Item icons open JEI's recipes and uses.
- **Toasts and sounds** for completed quests and chapters and for rewards that arrive by themselves, and a sound for
  claims. Keyboard: Esc closes the view then the book, Tab and the arrow keys change chapter, Ctrl+F searches.
- **Client settings**, `hibiscusquests-client.toml`: toasts, sounds, the zoom step, reduced motion (no pulsing or
  moving dashes) and whether the chapter list starts collapsed.
- **API 0.2.0**: `QuestBookOpenEvent` (client, cancellable).
- **More tasks:** `kill` (entity or tag), `location` (a point and radius, or a box), `dimension`, `biome`,
  `structure`, `stat` (any vanilla statistic), `xp` (hand in levels or points, or just reach them), `observation`
  (look at a block or entity), `fluid` and `energy` (hand in from any mod's tanks and batteries through NeoForge's
  item capabilities; `max_rate` caps one hand-in).
- **More rewards:** `choice` (pick one item in a dialog), `random` (roll a reward table), `loot` (a table's loot
  crates), `advancement` and `toast`. Repeatable quests with `can_repeat_without_claiming` start over as soon as they
  complete, handing their rewards straight to the player who completed them.
- **Reward tables**, `reward_tables/<id>.snbt` in the pack's files or a mod's data, merged like chapters: weighted
  entries, several rolls, a chance of nothing, and the book shows each entry's chance. A table can have a **loot
  crate**, named and coloured, opened with a right click and dropped by mobs players kill (a chance each for passive
  mobs, hostile ones and bosses; `rewards.lootCrateDrops` and `drop_loot_crates` turn it off).
- **Pinned quests:** pin up to `book.maxPinnedQuests` (5) quests from the quest view or `/hibiscusquests pin`, and
  their tasks show in a corner of the screen (`hud.corner`, `hud.scale` in the client config; the book's top bar
  hides them). Pins are per player and saved.
- **The Quest Book item**, crafted from a book and any dye, opens the book; `book.giveBookOnFirstJoin` hands one out.
- **API 0.3.0**: `RewardContext.choice()`, the index picked in a choice dialog.
- **The in-game editor**, for players with the `hibiscusquests.editor` permission (ops by default,
  `editor.permissionLevel`), from the pencil in the book's top bar or `/hibiscusquests editing_mode [on|off] [player]`.
  In edit mode the book shows every chapter and quest; right-click menus on the map add quests (by task type, then the
  item or the one in your hand), quest links, images and labels; quests are selected by click, Shift-click or a box,
  dragged (snapping to half the grid, Alt to place freely), nudged with the arrow keys, copied, cut, pasted (as text,
  into any world's book) and duplicated; Ctrl-click toggles a dependency. A quest's menu edits its properties, tasks
  and rewards, shape and size, moves it to another chapter, and resets or completes it for your team.
- **Forms for everything**, built from the files' format: quests, tasks and rewards of every type (custom ones too),
  chapters, groups, images, labels, quest links, reward tables and the book's settings, with pickers for items,
  registries and tags, colours, and a description editor with a live preview.
- **The chapter list in edit mode:** add, edit, reorder (drag or menu), regroup and delete chapters and groups; deleted
  chapters' files go to `.trash/`. Mods' chapters are read-only until **Override** copies one into the pack.
- **Reward tables** editor: tables, their rolls and loot crates, and entries (by item, or any reward type).
- **Undo and redo** (Ctrl+Z, Ctrl+Y) for the editing session.
- Every edit is checked on the server (permission, rate, and that it decodes as its file would), saved to the pack's
  files at once, and sent to every player as a patch of just what changed.
- `/hibiscusquests generate_chapter <title>`: a chapter with a quest for each item in the container you're looking at.
- **API 0.4.0**: `TaskType.editorHints()` and `RewardType.editorHints()` (`FieldHint`), and `CustomEditors` for types
  with an editor of their own.

[Unreleased]: https://github.com/hibiscus-software/hibiscus-quests/commits/HEAD

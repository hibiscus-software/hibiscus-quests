# Hibiscus Quests

A quest book for NeoForge, Minecraft 26.3, Java 25, that any mod or modpack fills with content: chapters drawn as maps
of quests joined by dependencies, tasks that track themselves or take submitted items, rewards claimed from the book,
progress shared per team, and an in-game editor for ops. Built on Hibiscus Lib (`hibiscuslib`, required); with
Hibiscus Teams (`hibiscusteams`, optional) progress is per party, without it every player is their own team.

- Clean room: FTB Quests (https://github.com/FTBTeam/FTB-Quests) is a behavioural reference only. Never copy its code,
  textures or lang text.
- Package `software.hibiscus.hibiscusquests`. The public API lives in its own source set, `src/api/java`
  (`software.hibiscus.hibiscusquests.api`), which can't see the mod's internals; it's also published alone as
  `hibiscusquests-api`, versioned by `HibiscusQuestsApi.API_VERSION`. Since 1.0.0 it only grows (new default methods
  and types, never changed signatures). Every API type and method has Javadoc; a missing or
  broken doc comment fails `./gradlew build` (`apiJavadoc`).
- Built on Hibiscus Lib (`hibiscuslib_version` in `gradle.properties`); read Lib's `CLAUDE.md`, `docs/API.md` and
  `docs/DEPENDING.md` and use what it has (`BaseScreen` and the widgets, `HibiscusTheme`, `HibiscusToasts`,
  `HibiscusButtons`, `HibiscusNetwork`, `SnbtFiles`, `PermissionLevels`, `Profiles`, `RateLimiter`) rather than writing
  your own. If Lib is missing something general, add it in Lib's repo (with docs, gallery entry, screenshots and
  CHANGELOG) and say so: Lib is released first, then `hibiscuslib_version` goes up here. Never put Quests-specific code
  in Lib, and never change the Teams API except by adding default methods.
- Teams: only through Lib's Teams API (`TeamsApi`, `Team`, `PlayerJoinedTeamEvent`, `TeamDeletedEvent`), never a
  Hibiscus Teams class. `progress/QuestTeams` decides whose progress a player's is (their team, or themselves in
  `progress_mode = player`); a solo team's id is the player's UUID with or without Teams.
- Building against Lib: from GitHub Packages (needs `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`), or a
  local checkout through the `includeBuild` in `settings.gradle` (`../hibiscus-lib` by default; elsewhere pass
  `-Phibiscuslib_dir=...`, e.g. `-Phibiscuslib_dir=../hibiscuslib-template-26.3`).
- NeoForge range: the jar declares every 26.3 build from 26.3.0.0-beta (`neo_version_range`) while compiling against
  `neo_version`. To check an older build, run the already compiled classes on it:
  `./gradlew runGameTestServer -x compileJava -x compileApiJava -Pneo_version=26.3.0.0-beta --no-configuration-cache`
  after deleting `build/neoForm` (and `runScreenshotClient -x compileScreenshotsJava` for client changes); without
  that the Minecraft patched for `neo_version` is reused and the run fails with a `ClassNotFoundException`. Something that differs between builds is looked
  up by name, as `HibiscusQuests.syncedConfigType` does for `ModConfig.Type.SYNCED` (`SERVER` before 26.3.0.37-beta).
- Layout: `book/` the book (the file model and its codecs, `BookMerger` merging the pack's files with chapters shipped
  as data, `PackFiles` the pack directory, `DataChapters` the data-pack loader, `BookManager` the server's current book,
  `BookText` quest text and the pack's translations, `PackArchive` export/import, `ChapterExport` export_chapter),
  `progress/` progress (`TeamProgress` / `QuestState` the data, `QuestLogic` the unlock and completion rules as pure
  functions, `ProgressManager` every change with its events, saving and sync, `ActiveTasks` which tasks each player is
  tracking, `ProgressStorage` the files), `task/` and `reward/` the built-in types (`CriterionTasks` plus
  `mixin/PlayerAdvancementsMixin` for criterion tasks), `network/` payloads and sync (`QuestSync`: the book in chunks
  behind a content hash, progress as per-team deltas, `BookPatch` an edit's changes), `edit/` the editor's server side
  (`BookEdit` the edits, `BookEditor` checking and applying them to the pack's files, `EditingMode` who's editing,
  `LangExport` export_lang),
  `command/` `/hibiscusquests` (alias `/hquests`), `config/`, `client/` the client's copy and screens (`client/edit/`
  the editor: `ClientEditor` state and undo, `EditActions` every action as edits, `Forms` and `FormPopup` the forms),
  `gametest/` the tests.
- Server-authoritative: the server owns the book and all progress. Every client action (claim, submit, edit) is a
  payload checked on the server (permission, state, `RateLimiter`); clients never decide what a player may do.
- Book files: SNBT through `SnbtFiles` (sorted keys, atomic, `.bak`). The pack's live in `config/hibiscusquests/`
  (a world's `serverconfig/hibiscusquests/` wins); mods ship chapters as data in `data/<ns>/hibiscusquests/`. Ids are
  readable `[a-z0-9_]` strings, never random hex: people and scripts write these files by hand. Codecs leave default
  values out of the files. A file that won't parse is reported and skipped, never written over.
- Progress files: `<world>/hibiscusquests/progress/<team id>.snbt`, saved debounced and on stop. A file that won't load
  is logged and left untouched; a deleted team's is archived. Quests no longer in the book keep their progress.
- Dedicated servers: no client classes on the server. Client code lives under `client/` and is only reached from
  `HibiscusQuestsClient` (`@Mod(dist = Dist.CLIENT)`), or from payload handlers through `network/ClientBridge`.
- Every UI string is a lang key in `assets/hibiscusquests/lang/en_us.json`. Quest text may be literal or a lang key.
- GUI: build on Hibiscus Lib's widgets and theme (flat fills, 1 px bevels). Screens rebuild on every sync, so keep their
  state (chapter, scroll, zoom, selection, unsaved edits) in fields, not widgets. Check every change with
  `./gradlew runScreenshotClient` (source set `src/screenshots`, never in a jar) at GUI scales 1-4, and look at the
  shots; the README's images are crops of them in `docs/images/`. The inventory button's sprite comes from
  `tools/gui_sprite_gen.py`.
- Comments: a short comment above each class and non-obvious method saying what it's for, as in the code already.
- License header on every source file (the MIT block comment at the top of the existing sources).
- 26.3 names: `Identifier` (not ResourceLocation), `GuiGraphicsExtractor` with `extractRenderState` /
  `extractBackground`, `MouseButtonEvent` / `KeyEvent`, `minecraft.gui.setScreen(...)` / `minecraft.gui.screen()`,
  `InputConstants.MOUSE_BUTTON_LEFT` (= 1) and `MOUSE_BUTTON_RIGHT` (= 3). Config types are `LOCAL`, `CLIENT`,
  `SYNCED` (the server config) and `STARTUP`. When unsure, grep the decompiled sources (unzip
  `build/neoForm/neoFormJoined26.3-1/steps/patchUserDev/outputs.jar`), Hibiscus Lib, Hibiscus Teams or Arcforge.
- Item stacks can't be made during mod construction or client setup in 26.3 (data components aren't bound yet): use
  suppliers.
- Tests: `./gradlew build runGameTestServer`. Gametests live in `gametest/` and are registered in
  `HibiscusQuestsGameTests`; Hibiscus Lib's run alongside them (`neoforge.enabledGameTestNamespaces`) and must keep
  passing. The test book is the built-in demo data pack (`src/main/resources/datapacks/demo`), switched on in the
  gametest run. Tests must pass with and without a teams mod: two-player teams use `gametest/StubTeams` through
  `QuestTeams`, never `TeamsApi.install` (it takes one installer per run). Cooldowns use `QuestClock`, which tests move
  forward (the gametest server runs faster than real time). `arcforge_triggers` only runs with Arcforge's jar in
  `run/gameTestServer/mods/`.
- Changes go under `## [Unreleased]` in CHANGELOG.md as they're made.
- Releasing: follow docs/RELEASING.md. A pushed `v<mod_version>+<minecraft_version>` tag (e.g. `v1.0.0+26.3`) runs
  `.github/workflows/release.yml`: a GitHub Release with the mod and API jars, both to GitHub Packages, and the mod jar
  to CurseForge once `CURSEFORGE_PROJECT_ID` is set.
- Commits and PRs: the maintainer commits, pushes and opens PRs. Don't run `git commit`, `git push` or `gh pr create`,
  and don't add yourself as a co-author anywhere. Where a commit would go, make sure `./gradlew build runGameTestServer`
  passes, then hand over a one-line commit message (and the PR text when a milestone is done).
- Minecraft versions: one branch each; `main` is the newest, `mc/<major.minor>` the others. The same version number
  has the same changes on every branch: make a change on one branch and cherry-pick it to the others.

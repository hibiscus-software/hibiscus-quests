# Depending on Hibiscus Quests

**A mod that only ships quests needs no dependency at all**: put the chapter files in
`src/main/resources/data/<your mod id>/hibiscusquests/` and declare Hibiscus Quests optional in `neoforge.mods.toml`
(see [API.md](API.md#shipping-quests-as-data)). Read on if your code uses the API: custom task or reward types,
progress pushes, queries or events.

## From GitHub Packages

Releases are published to `https://maven.pkg.github.com/hibiscus-software/hibiscus-quests`, each in two artifacts:

- `software.hibiscus.hibiscusquests:hibiscusquests-api:<API_VERSION>+<minecraft_version>`: the API on its own, with
  sources and Javadoc. Compile against this one.
- `software.hibiscus.hibiscusquests:hibiscusquests:<mod_version>+<minecraft_version>`: the whole mod, for running it in
  your dev environment.

GitHub Packages asks for a token even to read public packages.

`build.gradle`:

```groovy
repositories {
    maven {
        name = 'HibiscusQuests'
        url = 'https://maven.pkg.github.com/hibiscus-software/hibiscus-quests'
        credentials {
            // Locally: gpr.user / gpr.key in ~/.gradle/gradle.properties (a token with read:packages).
            // In GitHub Actions: the workflow's GITHUB_ACTOR and GITHUB_TOKEN.
            username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GITHUB_ACTOR')).getOrNull()
            password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GITHUB_TOKEN')).getOrNull()
        }
        content { includeGroup 'software.hibiscus.hibiscusquests' }
    }
    // Hibiscus Quests runs on Hibiscus Lib, for the dev run below.
    maven {
        name = 'HibiscusLib'
        url = 'https://maven.pkg.github.com/hibiscus-software/hibiscus-lib'
        credentials {
            username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GITHUB_ACTOR')).getOrNull()
            password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GITHUB_TOKEN')).getOrNull()
        }
        content { includeGroup 'software.hibiscus.hibiscuslib' }
    }
}

dependencies {
    // The API: compiled against, never shipped in your jar.
    compileOnly "software.hibiscus.hibiscusquests:hibiscusquests-api:1.0.0+26.3"
    // Optional: Hibiscus Quests (and Hibiscus Lib) in your dev runs, to try your quests.
    localRuntime "software.hibiscus.hibiscusquests:hibiscusquests:1.0.0+26.3"
    localRuntime "software.hibiscus.hibiscuslib:hibiscuslib:1.4.0+26.3"
}
```

(`localRuntime` is the template's configuration that `runtimeClasspath` extends, for dependencies that run in dev but
aren't published.)

`~/.gradle/gradle.properties` (yours, never committed):

```properties
gpr.user=<your GitHub user name>
gpr.key=<a personal access token (classic) with read:packages>
```

Then declare the dependency in `META-INF/neoforge.mods.toml`, as optional unless your mod can't work without quests:

```toml
[[dependencies.${mod_id}]]
    modId="hibiscusquests"
    type="optional"
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="BOTH"
```

With an optional dependency, only touch API classes when Hibiscus Quests is installed: keep them in a class of your
own that you only load after `ModList.get().isLoaded("hibiscusquests")`. Register task and reward types from your mod
constructor, on both sides.

## Versions

The API jar has its own version (`HibiscusQuestsApi.API_VERSION`), separate from the mod's; a mod release that doesn't
change the API ships the same API version as the one before. From 1.0.0 the API only grows, so a mod built against
API 1.2 runs with any later 1.x.
Each Minecraft version has its own builds (`+26.3`), from the matching branch.

## Working on Hibiscus Quests itself

Hibiscus Quests builds against Hibiscus Lib, from GitHub Packages or a local checkout (`includeBuild` in
`settings.gradle`, `../hibiscus-lib` by default, elsewhere `-Phibiscuslib_dir=...`), as Hibiscus Lib's own
`docs/DEPENDING.md` describes.

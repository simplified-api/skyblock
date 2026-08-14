# SkyBlock Data Layer

The Hypixel SkyBlock game-data layer: 34 JPA entities loaded into an embedded H2 database from the versioned JSON corpus this repository carries under `data/v1/`, plus the generator that indexes that corpus and the SkyBlock calendar the whole thing is dated against.

> [!IMPORTANT]
> **The corpus is in the repository and not in the jar.** `data/v1/**` is tracked here, beside the entities that bind it, so a model and its table are one commit. Nothing under `src/main/resources` carries game data - `connect` reads `data/v1/index.json` and the files it names off `master` over the GitHub Contents API, so a data correction reaches a consumer without a release. Both halves are true at once: the data ships in the tree and travels over the wire.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Usage](#usage)
- [Models](#models)
  - [Relations](#relations)
- [The Data Corpus](#the-data-corpus)
  - [Layout](#layout)
  - [The Index](#the-index)
  - [Extras](#extras)
  - [Versioning](#versioning)
  - [Determinism](#determinism)
- [Data Loading](#data-loading)
  - [Authentication](#authentication)
- [The SkyBlock Calendar](#the-skyblock-calendar)
- [Data Repository Contracts](#data-repository-contracts)
- [The Index Generator](#the-index-generator)
  - [Continuous Integration](#continuous-integration)
- [Gradle Tasks](#gradle-tasks)
  - [Build and Test](#build-and-test)
  - [Schema Export](#schema-export)
- [Repository Structure](#repository-structure)
  - [Runtime Directories](#runtime-directories)
- [Contributing](#contributing)
- [License](#license)

## Features

- **One connect, then plain lookups** - `SkyBlockData.connect(...)` stands up an H2 in-memory session with every repository registered; everything after that is `getRepository(Item.class).findFirst(...)`
- **A generated manifest** - `data/v1/index.json` pairs every JSON file with the entity that binds it and carries a SHA-256 of the file bytes, so a consumer can tell what moved before fetching anything
- **A model and its table are one commit** - the generator reads each entity's `@Table(name = ...)` and refuses a data file with no entity and an entity with no data file, so the two cannot drift apart
- **Relations resolve** - `@ManyToOne` foreign keys and `@ForeignIds` id-lists resolve to entity references, and nullable relations come back as `Optional`
- **Reads answer from held rows** - `getRepository` hands back a view over rows already in memory, so resolving many ids against one table costs one round trip rather than one per lookup
- **Second-level caching** - Hibernate query and entity caches are on by default under EhCache, read-write, with a 30-second query-results TTL
- **The SkyBlock calendar** - `SkyBlockDate` converts both directions between real epoch milliseconds and the accelerated 372-day in-game year
- **Auto-registered Gson** - the date adapters and the JPA exclusion strategy attach to `GsonSettings.defaults()` by SPI, with no bootstrap code at the call site

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | 8.x | Wrapper is bundled (`./gradlew`) |
| [Git](https://git-scm.com/) | 2.x+ | For cloning the repository |
| [Python](https://www.python.org/) | **3.8+** | Runs the index generator - standard library only, no virtualenv and no lockfile |
| GitHub PAT | - | For anything that connects to GitHub. Unauthenticated, GitHub allows 60 requests per hour per IP and one connect spends 36 |

### Installation

Add the JitPack repository and the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.simplified-api:skyblock:master-SNAPSHOT")
}
```

`persistence`, `gson-extras`, `collections`, `utils`, `reflection`, `minecraft-library/text` and `simplified-api/github` all come in transitively as `api` dependencies.

Or clone and build locally:

```bash
git clone https://github.com/simplified-api/skyblock.git
cd skyblock
./gradlew build
```

### Usage

```java
// Once, at startup. Connects H2, registers every repository, and loads the corpus.
SkyBlockData.connect(GsonSettings.defaults());

// Anywhere thereafter.
Repository<Item> items = SkyBlockData.getRepository(Item.class);

Item hyperion = items.findFirst(Item::getId, "HYPERION").orElseThrow();
hyperion.getRarity();               // Rarity.LEGENDARY
hyperion.getCategory().getId();     // the resolved ItemCategory, not just its id

Repository<Pet> pets = SkyBlockData.getRepository(Pet.class);
pets.findFirst(Pet::getId, "AMMONITE").orElseThrow().getSkill().getId();   // "FISHING"
```

> [!NOTE]
> `GsonSettings.defaults()` is what makes this work. It discovers this module's `GsonContributor` through `ServiceLoader` and registers the `SkyBlockDate.RealTime` / `SkyBlockDate.SkyBlockTime` adapters and the `JpaExclusionStrategy` Hibernate needs to round-trip its Gson-stored columns. A hand-built `GsonSettings` without those adapters connects and then fails on the first date column.

> [!WARNING]
> `connect` performs network I/O. It fetches the manifest and one file per model from GitHub, so an unreachable `api.github.com` at startup fails the connect rather than degrading. Anything that reaches a repository before `connect` throws `JpaException`.

## Models

34 entity classes live in `api.simplified.skyblock.model`. `RepositoryFactory.resolveModels(Item.class)` scans that package, so **the package is the registration** - putting the class there is what registers it, and nothing anywhere names a model by hand.

A model's `@Table(name = ...)` is the stem of its corpus file: `@Table(name = "items")` pairs with `items.json`, wherever under `data/v1/` that file sits. That annotation is the only thing pairing the two, and it is what the index generator reads.

| Category | Models |
|----------|--------|
| Items | `Item`, `ItemCategory`, `Accessory`, `BitsItem` |
| Mobs | `MobType`, `BestiaryCategory`, `BestiarySubcategory`, `BestiaryFamily` |
| Modifiers | `Stat`, `StatCategory`, `Buff`, `Enchantment`, `Reforge`, `Gemstone`, `Mixin`, `Power`, `HotmPerk`, `ShopPerk`, `Potion`, `PotionGroup`, `Brew` |
| Player | `Skill`, `Collection`, `Slayer`, `Minion`, `Pet`, `Essence` |
| World | `Region`, `Zone`, `FairySoul`, `Event`, `Mayor`, `MelodySong`, `Keyword` |

The categories are the corpus's own, so a row here names the directory its JSON sits in.

Shared enums and value types sit alongside the entities: `Rarity` and `GameStage` in `common/`, and `SkinTexture` at the package root as a base64 texture blob stored in a JSON column.

### Relations

```java
Stat health = SkyBlockData.getRepository(Stat.class)
    .findFirst(Stat::getId, "HEALTH")
    .orElseThrow();

health.getCategoryId();          // "COMBAT" - the raw column
health.getCategory().getId();    // the resolved StatCategory entity

Enchantment absorb = SkyBlockData.getRepository(Enchantment.class)
    .findFirst(Enchantment::getId, "ABSORB")
    .orElseThrow();

absorb.getCategoryIds();         // ["AXE", ...] - the raw id list
absorb.getCategories();          // the resolved ItemCategory entities

// A nullable relation is an Optional, never a null.
SkyBlockData.getRepository(Reforge.class)
    .findFirst(Reforge::getId, "FAIR")
    .orElseThrow()
    .getStone();                 // Optional.empty()
```

## The Data Corpus

34 tables of Hypixel SkyBlock reference data, one JSON file each, tracked under `data/v1/`.

### Layout

```
data/
└── v1/
    ├── index.json               # generated manifest - the entry point
    ├── items/                   # 4 tables - item registry, categories, accessories
    ├── mobs/                    # 4 tables - monster taxonomy and bestiary
    ├── modifiers/               # 13 tables - stats, buffs, enchantments, reforges, potions
    ├── player/                  # 6 tables - progression systems
    └── world/                   # 7 tables - regions, zones, events
```

Each primary file is a JSON array of entities keyed by natural id, and the file stem is the table name. `hotm_perks.json` and `fairy_souls.json` are currently empty arrays: a table with no file aborts the generator, so `[]` is how a table declares itself known and unpopulated.

Category directories are **presentational only**. A consumer reads `index.json` and never walks the tree, so moving a file between categories changes its `path` and nothing else about how it is found. The directories sit in the same checkout as the models, which makes it easy to assume the path is the contract. It is not - the manifest is.

### The Index

`data/v1/index.json` is generated, never hand-edited. Every entry names a file and the model class that binds it.

```json
{
  "version": 1,
  "generated_at": "2026-08-14T07:46:50Z",
  "commit_sha": "c6fffbeb9cceece003e5e712461a34327d610b61",
  "count": 34,
  "files": [
    {
      "path": "data/v1/items/items.json",
      "category": "items",
      "table_name": "items",
      "model_class": "api.simplified.skyblock.model.Item",
      "content_sha256": "97b48d3f...",
      "bytes": 7082076,
      "has_extra": true,
      "extra_path": "data/v1/items/items_extra.json",
      "extra_sha256": "b006e73d...",
      "extra_bytes": 465
    }
  ]
}
```

| Field | Meaning |
|-------|---------|
| `count` | Number of **tables** (34), not files - an extra does not add to it |
| `path` | Repo-root-relative, forward slashes, exactly what a consumer should request |
| `category` | The directory the file happens to sit in; presentational |
| `table_name` | The file stem, matched byte for byte against a `@Table(name = ...)` |
| `model_class` | The `@Table`-annotated entity in this repository that binds the file |
| `content_sha256` | Lowercase hex digest of the file bytes **as stored on disk** |
| `bytes` | File size on disk |
| `has_extra` | Whether an `_extra` companion exists; the three `extra_*` fields appear only when it does |

`generated_at` and `commit_sha` are informational. Both are excluded from the `--check` comparison, so a regeneration that finds no content change is a no-op rather than a diff.

> [!WARNING]
> `data/v1/items/items.json` is about 7 MB. The GitHub Contents API returns a base64 envelope capped at 1 MB unless the request carries `Accept: application/vnd.github.raw+json`, so a consumer that omits that media type fails on this one file and succeeds on the other 34.

### Extras

`<table>_extra.json` is an optional companion merged into its primary at load time, for entries maintained by hand beside a bulk-generated file - `items_extra.json` carries the anniversary balloon hats that no upstream dump contains.

An extra has no model class and no index entry of its own; it appears as three fields on its primary's entry, and an extra with no matching primary aborts the generator as an orphan.

### Versioning

`v1/` is a **schema-version boundary**. A breaking schema change ships as `v2/` alongside it, never as a mutation of `v1/` in place - existing consumers keep reading `v1/index.json` until they move deliberately.

### Determinism

`content_sha256` has to match bit for bit between a Windows contributor and Linux CI, or every entry fails its check at once. Three rules hold that:

- `.gitattributes` forces `* text=auto eol=lf`, so a checkout carries LF whatever `core.autocrlf` says
- the generator writes the index with `write_bytes` rather than `write_text`, which on Windows would translate `\n` to `\r\n`
- output is `indent=2, sort_keys=True` plus a trailing newline, so key order is alphabetical and stable

## Data Loading

The jar carries no JSON. `SkyBlockFactory` gives every model its own `RemoteJsonSource`, and all of them share one manifest source and one file fetcher pointed at this repository's `master`.

```
SkyBlockData.connect(...)
  -> SkyBlockFactory                          # one RemoteJsonSource per model
    -> manifest source                        # GET data/v1/index.json, held after the first call
    -> file fetcher                           # GET the path the manifest names for this model
      -> SkyBlockDataContract                 # Accept: application/vnd.github.raw+json
        -> H2 in-memory schema "skyblock"     # Hibernate, EhCache L2, read-write
```

The manifest is fetched once and held, not once per model - `RemoteJsonSource` asks for it on every load and there is one source per model, so without that hold the same file would be fetched 34 times per connect. `SkyBlockFactory.refreshManifest()` discards it.

Any GitHub failure is re-thrown as `JpaException` carrying the HTTP status, the dataset's source id and the path, so a 404 on one model names the file rather than surfacing as a decode error.

`SkyBlockData.getRepository` answers from rows already in memory: it takes one `findAll()` per model and scans those, so resolving many ids against one table costs a single round trip. A held row can be up to that repository's cache duration stale, because the refresh cycle moves a repository's stamp before it deletes the rows its source has withdrawn. A caller that needs the uncached answer asks `SkyBlockData.getSessionManager()` for the repository instead.

### Authentication

`SkyBlockFactory`'s no-argument constructor builds its own GitHub clients and reads a personal access token from `SKYBLOCK_DATA_GITHUB_TOKEN`.

A cold connect is **36 requests**: the manifest, 34 primary files, and the one extra.

| Mode | Budget | Connects per hour |
|------|--------|-------------------|
| Unauthenticated | 60 requests / hour / IP | roughly one |
| Authenticated (PAT) | 5000 requests / hour | well over a hundred |

A blank or missing token degrades to unauthenticated rather than failing - enough for a single session, not for a process that connects repeatedly.

Callers that already own configured GitHub proxies - a Spring context, for instance - should pass a `SkyBlockDataContract` to the `SkyBlockFactory(SkyBlockDataContract)` constructor instead of letting it build its own.

## The SkyBlock Calendar

SkyBlock runs an accelerated calendar: one real second is about 72 in-game seconds, an in-game minute is `50000 / 60` real milliseconds, and a year is 12 seasons of 31 days - 372 days. The epoch is the SkyBlock launch, 11 June 2019.

```java
SkyBlockDate date = new SkyBlockDate(278, Season.LATE_SUMMER, 27, 0);

date.getRealTime();        // real-world epoch millis
date.getSkyBlockTime();    // elapsed SkyBlock millis since launch
date.getYear();            // 278
date.getSeason();          // LATE_SUMMER
date.getDay();             // 27
```

`SkyBlockDate.RealTime` and `SkyBlockDate.SkyBlockTime` are the two bindable subtypes - a wire field carrying a real epoch binds to the first, one carrying elapsed SkyBlock milliseconds to the second, and each has its own Gson adapter registered by the SPI contributor.

`SkyBlockDate.Launch` holds the one instant the calendar is measured from, and `SkyBlockDate.Length` every millisecond constant the conversions are built from. What recurs on that calendar lives on `Event`, whose rows carry an anchor and a list of repeating phases rather than a list of dates, so an occurrence is walked forward rather than looked up.

## Data Repository Contracts

Three façades pre-bind `simplified-api` / `skyblock` as the owner and repo of every GitHub call, so a call site passes a path and nothing else. `SkyBlockDataContract.MANIFEST_PATH` names the manifest.

| Façade | Shape | Can drive one Feign client |
|--------|-------|:--------------------------:|
| `SkyBlockDataContract` | interface extending both Contents contracts | ❌ |
| `SkyBlockDataService` | final class, constructor-injected with both proxies | ❌ |
| `SkyBlockGitDataContract` | interface extending the Git Data contract | ✅ |

The first two are **type façades only**. The read surface needs `Accept: application/vnd.github.raw+json` and the write surface needs `application/vnd.github+json`, and a Feign client carries one static header set - so the two proxies are built separately against the parent contracts and aggregated through `SkyBlockDataContract.from(read, write)`. The Git Data surface uses a single media type, so it can be handed straight to a client builder.

`SkyBlockDataContract` and `SkyBlockDataService` expose the same surface deliberately; pick the ergonomics that suit the call site.

## The Index Generator

`scripts/generate_index.py` builds `data/v1/index.json` from two inputs: the files on disk under `data/v1/`, and a scan of `src/main/java/api/simplified/skyblock/model/` for each entity's `@Table(name = ...)`. It reads the Java as text, so it needs no JDK, no Gradle and no build output.

```bash
python scripts/generate_index.py                     # regenerate data/v1/index.json
python scripts/generate_index.py --check             # verify it is in sync; exit 1 if stale
python scripts/generate_index.py --repo-root <dir>   # run against a tree elsewhere
python scripts/generate_index.py --model-src <dir>   # scan a model root elsewhere
```

Write mode compares content first and prints `already in sync, not rewriting` rather than churning the timestamp when nothing moved. `--check` ignores `generated_at` and `commit_sha`, so only a real content change fails it.

The generator aborts rather than emitting a partial index, naming the offending file, on any of:

| Condition | Message |
|-----------|---------|
| A data file whose table name no entity declares | `has no model` |
| An entity whose `@Table` name no data file matches | `but no data file matches` |
| An `@Entity` with no `@Table(name = ...)`, or a `@Table` with no `@Entity` | `carries @Entity with no @Table(name = ...)` / `carries @Table with no @Entity` |
| A type whose name disagrees with its file name | `disagrees with its file name` |
| Two entities claiming one table name | `two entities claim table` |
| An `_extra` with no primary | `orphan extra` |
| Two primaries or two extras for one table | `duplicate primary` / `duplicate extra` |

The refusals are what keep a model and its table one commit: neither half can land alone.

### Continuous Integration

`.github/workflows/regenerate-index.yml` runs on changes to `data/v1/**`, the model sources, the generator, or the workflow itself. Both jobs need Python alone.

| Event | Mode | Outcome |
|-------|------|---------|
| Pull request | `--check` | Fails if the committed index is stale; the contributor regenerates and pushes, and the diff stays visible in review |
| Push to `master` | write | Regenerates and auto-commits as `github-actions[bot]` if anything changed |

The push job exists to catch a squash-merge that dropped the regenerated index, not to excuse skipping it in a pull request.

## Gradle Tasks

### Build and Test

```bash
./gradlew build       # compile, test, assemble jar
./gradlew test        # JUnit 5 suite
```

The suite reads the corpus off disk, so no request leaves the machine and no token is needed. The `test` task hands the forked JVM `-Dskyblock.corpus.root=<project dir>`, because the corpus is a tracked directory of this project and a forked JVM inherits no notion of where the project is.

| Suite | Reads the corpus |
|-------|:----------------:|
| `JpaModelTest` - every model binds, and its relations resolve | ✅ |
| `BuffCorpusValidationTest` - every shipped buff row is well formed and every reference resolves | ✅ |
| `SubstituteTokenTest` - a description and its substitutes name the same things | ✅ |
| `EventTest` - the schedule walk, against inline fixtures | ❌ |
| `LadderBindingTest` - skill and slayer experience ladders, against inline fixtures | ❌ |
| `SkyBlockDateTest` - calendar arithmetic | ❌ |

The corpus has a gate of its own in `python scripts/generate_index.py --check`, and neither substitutes for the other: the Java suite proves the corpus binds, and `--check` proves the manifest describes it.

### Schema Export

`SchemaExporter` is a `main()` in the test sources, not a test. It connects the session and writes H2 DDL plus a persistent file database under `.schema/`, which lets IntelliJ resolve JPA column names in the entity classes.

> [!CAUTION]
> `SchemaExporter` connects through `SkyBlockData`, which means real GitHub traffic - 36 requests per run. Unauthenticated, that is most of an hour's budget in one go, so put a token in `SKYBLOCK_DATA_GITHUB_TOKEN` before running it.

Point IntelliJ at the exported database with:

```
jdbc:h2:file:$PROJECT_DIR$/.schema/skyblock;ACCESS_MODE_DATA=r
```

user `sa`, empty password. The directory is gitignored and excluded from the IDE module.

## Repository Structure

```
skyblock/
├── src/
│   ├── main/java/api/simplified/skyblock/
│   │   ├── SkyBlockData.java                  # static locator: connect() + getRepository()
│   │   ├── SkyBlockFactory.java               # one RemoteJsonSource per model, over GitHub
│   │   ├── SkyBlockDataGsonContributor.java   # SPI hook: date adapters + JpaExclusionStrategy
│   │   ├── ReferenceIndex.java                # the held-row view getRepository hands back
│   │   ├── SkinTexture.java                   # base64 texture blob stored as a JSON column
│   │   ├── common/                            # Rarity, GameStage
│   │   ├── contract/                          # the three façades over the github module
│   │   ├── date/                              # SkyBlockDate, Season
│   │   └── model/                             # the 34 JPA entities
│   ├── main/resources/META-INF/services/      # GsonContributor SPI registration
│   └── test/java/                             # the six suites, LocalSkyBlockData, SchemaExporter
├── data/
│   └── v1/
│       ├── index.json                         # generated manifest
│       └── items/  mobs/  modifiers/  player/  world/
├── scripts/
│   └── generate_index.py                      # writes and verifies the manifest
├── .github/workflows/regenerate-index.yml
├── .gitattributes                             # forces LF; load-bearing for the digests
├── build.gradle.kts  settings.gradle.kts  gradle/libs.versions.toml
└── LICENSE.md  CONTRIBUTING.md  CLAUDE.md
```

### Runtime Directories

Created during execution and excluded from version control:

| Path | Contents |
|------|----------|
| `.schema/` | H2 file database written by `SchemaExporter` for IDE JPA resolution |
| `build/` | Gradle outputs |
| `.gradle/` | Gradle project cache and daemon state |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, how to add a model and its table, how to edit data and regenerate the index, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see [LICENSE](LICENSE.md) for the full text.

Data content is derived from public Hypixel API responses and community knowledge. Individual entry-level copyrights, where they exist, belong to their respective owners; the corpus as a compilation is licensed under Apache 2.0.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This library is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

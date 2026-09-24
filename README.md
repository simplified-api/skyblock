# SkyBlock Data Layer

The Hypixel SkyBlock game-data layer: 34 JPA models held in memory by a persistence session, read from the versioned JSON corpus this repository carries under `data/v1/`, plus the generator that catalogues that corpus and the SkyBlock calendar the whole thing is dated against.

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
- [Repository Structure](#repository-structure)
  - [Runtime Directories](#runtime-directories)
- [Contributing](#contributing)
- [License](#license)

## Features

- **One connect, then plain lookups** - `SkyBlockData.connect()` reads every model out of the published corpus and registers one session holding a repository per model; everything after that is `getRepository(Item.class).findFirst(...)`
- **Keeps up every ten minutes** - every model declares a ten-minute `@Hydration` cadence; a tick asks whether the corpus branch moved and re-reads only the documents whose catalogue fingerprint changed, so a running session sees a data correction without reconnecting
- **A generated catalogue** - `data/v1/index.json` names the ordered layers each document is made of and carries a SHA-256 of every layer's bytes, so a consumer can tell what moved before fetching anything
- **A model and its table are one commit** - a model's `@Table(name = ...)` is the name of the document it reads, so a model whose document the catalogue does not carry fails every connect, the test suite's included
- **Relations resolve** - a `@Linked` field resolves the id or id list beside it to rows of the target model before a generation is published, and a relation that may be absent comes back as `Optional`
- **Reads answer from held rows** - `getRepository` hands back a repository holding one generation of rows in memory, so every finder is a scan or an index probe and none performs I/O
- **No database, no second-level cache** - nothing is opened and no second-level cache sits in front of any repository; each repository publishes a whole linked generation at once, and a reader holding one never sees it change underneath them
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
| GitHub PAT | - | Only for writing the corpus back. `connect()` reads unauthenticated, which GitHub caps at 60 requests per hour per IP; one connect makes 37 requests and its session one more every ten minutes |

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
// Once, at startup. Reads the corpus, links every model's rows, and registers the session.
SkyBlockData.connect();

// Anywhere thereafter.
Repository<Item> items = SkyBlockData.getRepository(Item.class);

Item hyperion = items.findFirst(Item::getId, "HYPERION").orElseThrow();
hyperion.getRarity();               // Rarity.LEGENDARY
hyperion.getCategory().getId();     // the resolved ItemCategory, not just its id

Repository<Pet> pets = SkyBlockData.getRepository(Pet.class);
pets.findFirst(Pet::getId, "AMMONITE").orElseThrow().getSkill().getId();   // "FISHING"
```

> [!NOTE]
> `connect()` parses the corpus with `SkyBlockData.corpusSettings()`, which is `GsonSettings.defaults()` with the string type set to `DEFAULT`. `defaults()` is what makes this work: it discovers every `GsonContributor` through `ServiceLoader`, so this module's registers the `SkyBlockDate.RealTime` / `SkyBlockDate.SkyBlockTime` adapters and persistence's registers the `JpaExclusionStrategy` that keeps every `@Linked` field out of a document. Code that parses corpus JSON itself starts from `corpusSettings()` for the same reason; a hand-built `GsonSettings` without those adapters fails on the first date column.

> [!WARNING]
> `connect` performs network I/O. It fetches the catalogue and every layer it names from GitHub, so an unreachable `api.github.com` at startup fails the connect rather than degrading - the session is shut down and never registered. Anything that reaches a repository before a connect succeeds throws `JpaException`.

## Models

34 entity classes live in `api.simplified.skyblock.model`. `JpaModel.resolveModels(Item.class)` scans that package, so **the package is the registration** - putting the class there is what registers it, and nothing anywhere names a model by hand.

A model's `@Table(name = ...)` is the name of its corpus document, and the catalogue names each document by the stem of its primary file: `@Table(name = "items")` reads the document `items`, which is `items.json` wherever under `data/v1/` that file sits. That annotation is the only thing pairing the two. Its `@Id` property is the key the document's layers merge on.

| Category | Models |
|----------|--------|
| Items | `Item`, `ItemCategory`, `Accessory`, `BitsItem` |
| Mobs | `MobType`, `BestiaryCategory`, `BestiarySubcategory`, `BestiaryFamily` |
| Modifiers | `Stat`, `StatCategory`, `Buff`, `Enchantment`, `Reforge`, `Gemstone`, `Mixin`, `Power`, `HotmPerk`, `ShopPerk`, `Potion`, `PotionGroup`, `Brew` |
| Player | `Skill`, `Collection`, `Slayer`, `Minion`, `Pet`, `Essence` |
| World | `Region`, `Zone`, `FairySoul`, `Event`, `Mayor`, `MelodySong`, `Keyword` |

The categories are the corpus's own, so a row here names the directory its JSON sits in.

Shared enums and value types sit alongside the entities: `Rarity` and `GameStage` in `common/`, and `SkinTexture` at the package root as a base64 texture blob read from a nested object on an `Item` row.

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

34 tables of Hypixel SkyBlock reference data, one primary JSON file each plus one `_extra` companion, tracked under `data/v1/`.

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

Each primary file is a JSON array of entities keyed by natural id, and the file stem is the table name. `hotm_perks.json` and `fairy_souls.json` are currently empty arrays: a model whose document the catalogue does not carry fails the connect, so `[]` is how a table declares itself known and unpopulated.

Category directories are **presentational only**. A consumer reads `index.json` and never walks the tree, so moving a file between categories changes its `path` and nothing else about how it is found. The directories sit in the same checkout as the models, which makes it easy to assume the path is the contract. It is not - the manifest is.

### The Index

`data/v1/index.json` is generated, never hand-edited. It maps every logical document to the ordered layers it is made of, and names no Java class.

```json
{
  "documents": {
    "items": [
      {
        "path": "data/v1/items/items.json",
        "sha256": "97b48d3f..."
      },
      {
        "path": "data/v1/items/items_extra.json",
        "sha256": "b006e73d..."
      }
    ]
  },
  "revision": "320cea87f2d538b6afcd9b15936fda8d668bb880"
}
```

| Field | Meaning |
|-------|---------|
| `documents` | One entry per logical document (34), keyed by the stem of its primary file - the name a model's `@Table(name = ...)` reads |
| `path` | Repo-root-relative, forward slashes, exactly what a consumer should request; the category directory appears here and nowhere else |
| `sha256` | Lowercase hex digest of the layer's bytes **as stored on disk** |
| `revision` | The commit `HEAD` pointed at when the catalogue was written |

A document's layers are listed in merge order: the primary first, then its `_extra` companion where one exists. `revision` is excluded from the `--check` comparison, so a regeneration that finds no content change is a no-op rather than a diff.

> [!WARNING]
> `data/v1/items/items.json` is about 7 MB. The GitHub Contents API returns a base64 envelope capped at 1 MB unless the request carries `Accept: application/vnd.github.raw+json`, so a consumer that omits that media type fails on this one file and succeeds on the other 34.

### Extras

`<table>_extra.json` is an optional companion merged into its primary at load time, for entries kept beside a bulk-generated file - `items_extra.json` carries the anniversary balloon hats that no upstream dump contains, and every item a write adds.

An extra has no document of its own; it is the second layer of its primary's. The layers merge by `@Id`, so a row the extra repeats replaces the primary's row of the same id in place and a new id is appended. An extra with no matching primary aborts the generator as an orphan.

A write through `SkyBlockData.writing(...)` lands in the layer that owns each row it names, so a balloon hat is written into the extra and any other existing item into `items.json`. A new item is added to the extra, which a regeneration of `items.json` leaves alone, and a delete removes the id from every layer carrying it. Only a file the write changes is rewritten, one commit each. An id in the extra keeps overriding the primary's row even once an upstream dump carries it, since the generator's `duplicate extra` check counts files, not ids.

### Versioning

`v1/` is a **schema-version boundary**. A breaking schema change ships as `v2/` alongside it, never as a mutation of `v1/` in place - existing consumers keep reading `v1/index.json` until they move deliberately.

### Determinism

Each layer's `sha256` has to match bit for bit between a Windows contributor and Linux CI, or every entry fails its check at once. Three rules hold that:

- `.gitattributes` forces `* text=auto eol=lf`, so a checkout carries LF whatever `core.autocrlf` says
- the generator writes the index with `write_bytes` rather than `write_text`, which on Windows would translate `\n` to `\r\n`
- output is `indent=2, sort_keys=True` plus a trailing newline, so key order is alphabetical and stable

## Data Loading

The jar carries no JSON. `SkyBlockData.connect()` hands the session one source for every model: a `DocumentSource` over a `CorpusOrigin`, which reads this repository's `master` over the GitHub Contents API.

```
SkyBlockData.connect()
  -> JpaConfig(JpaModel.resolveModels(Item.class), DocumentSource)
    -> SessionManager.connect                  # hydrates every model, then registers the session
      -> CorpusOrigin.fingerprints()           # GET the branch tip, then data/v1/index.json at that tip
      -> DocumentSource.read(model)            # once per model, all in one pass
        -> CorpusOrigin.layersOf(@Table name)  # the held catalogue
        -> CorpusOrigin.read(path)             # GET each layer at the catalogue's tip, Accept: application/vnd.github.raw+json
      -> link every model, then publish every generation
  -> every ten minutes
    -> CorpusOrigin.fingerprints()             # GET the branch tip; the catalogue only when it moved
    -> re-read each model whose fingerprint moved, with every model linking into it
```

The catalogue is held by the `GitHubCorpus` the connect builds rather than fetched once per model - every model asks the origin for its layers, so without that hold the same file would be fetched 34 times per connect. It is held until a tick finds the branch tip moved, which fetches the catalogue at the new tip in its place. Each connect builds its own corpus, so the next connect fetches the catalogue again.

A failure on the connect's first two requests - the branch tip and the catalogue at that tip - crosses `CorpusOrigin` as a `JpaException` naming the corpus check, `Failed to ask whether the corpus moved`, rather than any model. A failed request after them crosses it as a `JpaException` naming what was being read - a layer's path, or the document whose catalogue entry was asked for - and the session wraps it in one naming the model that failed to hydrate, so a 404 on one model names the file rather than surfacing as a decode error. An error status GitHub answers adds the HTTP status and the reason; a request that never reaches GitHub, or a body that is no catalogue, is carried as the cause. A failed connect shuts its session down and registers nothing.

`SkyBlockData.getRepository` answers from rows already in memory: each repository holds one generation, read in the same pass as every other model and published only after every link in that pass has been resolved. Every SkyBlock model declares `@Hydration(every = 10, unit = TimeUnit.MINUTES)`, so the session ticks every ten minutes and asks for the branch tip. A tip that has not moved costs that one request and reads nothing; a moved tip fetches the catalogue at the new commit, and each model whose document's fingerprint moved is re-read at that commit together with every model linking into it. A model whose fingerprint did not move keeps its generation, and `Repository.getHydratedAt()` keeps saying when that generation was published. A data correction on `master` reaches a running consumer at the first tick after the catalogue is regenerated.

### Authentication

`connect()` reads unauthenticated: the corpus it builds carries no token, and nothing in this module reads one from the environment.

Every connect makes **37 requests** - the branch tip, the catalogue at that tip, 34 primaries and the extra - and a connected session one more per ten-minute tick, plus the catalogue and the moved documents at a tick that finds the tip moved. Each connect builds its own corpus, so none of them starts warm.

| Mode | Budget | Connects per hour |
|------|--------|-------------------|
| Unauthenticated | 60 requests / hour / IP | roughly one |
| Authenticated (PAT) | 5000 requests / hour | well over a hundred |

Unauthenticated is enough for a single session, not for a process that connects repeatedly.

A token belongs to a caller that writes the corpus back. It names the corpus through `SkyBlockData.corpus()`, adds `.token(GitHubToken.of("<VARIABLE>"))` and builds, then connects `new JpaConfig(JpaModel.resolveModels(Item.class), SkyBlockData.writing(corpus))` on a `SessionManager` of its own and writes through the `JpaSession.write` that connect returns. That source reads authenticated and is the only one a `WriteRequest` can be applied through - the session `connect()` registers holds no write instruction, so `SkyBlockData.write` against a SkyBlock model fails. `GitHubToken.of` fails at startup on an unset or blank variable rather than degrading.

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

`SkyBlockData.corpus()` names the data repository once. It returns a `GitHubCorpus.Builder` with `simplified-api` / `skyblock` as owner and repo, `data/v1/index.json` as the catalogue and `corpusSettings()` as the parser already bound, leaving the token and the branch (`master` unless named) to the caller.

`GitHubCorpus`, in the `github` module, builds the two Contents contract proxies itself. The read surface needs `Accept: application/vnd.github.raw+json` and the write surface needs `application/vnd.github+json`, and a Feign client carries one static header set - so the two proxies are built separately and no caller assembles either.

`CorpusOrigin` is the one place that speaks both languages. It answers the two questions a `DocumentSource` asks - which layers a document is made of, and what text sits at a path - out of the corpus, and restates a `GitHubApiException` as a `JpaException`. Its `Writing` subtype, which only `SkyBlockData.writing(...)` builds, adds the write: one commit per file, messaged `Update <path>`. The file's text and its blob sha come out of one read at the branch, the change applies to that text and the commit carries that sha, so a file that moved in between - or a body the client's response cache replayed from before the branch moved - is refused rather than overwritten.

## The Index Generator

`scripts/generate_index.py` builds `data/v1/index.json` from the files on disk under `data/v1/`, plus `git rev-parse HEAD` for `revision`. It walks them and hashes them and reads no Java, so it needs no JDK, no Gradle and no build output, and a model rename cannot stale it.

```bash
python scripts/generate_index.py                     # regenerate data/v1/index.json
python scripts/generate_index.py --check             # verify it is in sync; exit 1 if stale
python scripts/generate_index.py --repo-root <dir>   # run against a tree elsewhere
```

Write mode compares content first and prints `already in sync (34 documents), not rewriting` rather than churning the revision when nothing moved. `--check` ignores `revision`, so only a real content change fails it.

The generator aborts rather than emitting a partial index, naming the offending path or document, on any of:

| Condition | Message |
|-----------|---------|
| No `data/v1/` directory under the repo root | `data root not found` |
| An `_extra` with no primary | `orphan extra` |
| Two primaries or two extras for one table in one category | `duplicate primary` / `duplicate extra` |
| Two categories publishing the same file stem | `two categories both publish` |

The generator knows nothing of models, so it cannot hold a model and its table together. The connect does: a model whose `@Table` name the catalogue does not carry fails with `The origin names no document`, and `./gradlew test` connects every model against the checkout.

### Continuous Integration

`.github/workflows/regenerate-index.yml` runs on changes to `data/v1/**`, the generator, or the workflow itself. Both jobs need Python alone.

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
| `StatGrantsTest` - a reward line grants the stat it names and no other | ✅ |
| `EventTest` - the schedule walk, against inline fixtures | ❌ |
| `LadderBindingTest` - skill and slayer experience ladders, against inline fixtures | ❌ |
| `SkyBlockDateTest` - calendar arithmetic | ❌ |

The corpus has a gate of its own in `python scripts/generate_index.py --check`, and neither substitutes for the other: the Java suite proves the corpus binds, and `--check` proves the manifest describes it.

## Repository Structure

```
skyblock/
├── src/
│   ├── main/java/api/simplified/skyblock/
│   │   ├── SkyBlockData.java                  # static locator: connect(), getRepository(), write(), corpus(), writing()
│   │   ├── CorpusOrigin.java                  # the corpus as document layers; Writing adds the write
│   │   ├── SkyBlockDataGsonContributor.java   # SPI hook: SkyBlockDate adapters
│   │   ├── SkinTexture.java                   # base64 texture blob, a nested object on Item
│   │   ├── common/                            # Rarity, GameStage
│   │   ├── date/                              # SkyBlockDate, Season
│   │   └── model/                             # the 34 JPA entities
│   ├── main/resources/META-INF/services/      # GsonContributor SPI registration
│   └── test/java/                             # the seven suites, LocalSkyBlockData
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
| `build/` | Gradle outputs |
| `.gradle/` | Gradle project cache and daemon state |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, how to add a model and its table, how to edit data and regenerate the index, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see [LICENSE](LICENSE.md) for the full text.

Data content is derived from public Hypixel API responses and community knowledge. Individual entry-level copyrights, where they exist, belong to their respective owners; the corpus as a compilation is licensed under Apache 2.0.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This library is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

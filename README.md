# SkyBlock Data Layer

The Hypixel SkyBlock game-data layer: 41 JPA entities loaded into an embedded H2 database from the [skyblock-data](https://github.com/simplified-api/skyblock-data) repository, plus the SkyBlock calendar and the mayor election cycle it drives.

> [!IMPORTANT]
> **The jar carries no game data.** Every model is fetched at `connect` time from the `master` branch of [skyblock-data](https://github.com/simplified-api/skyblock-data), so the version you pin here does not pin what you load - a data correction reaches you without a release, and a model the manifest does not name is inert rather than an error. An entity change and its corpus change have to land together.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Usage](#usage)
- [Models](#models)
  - [Relations](#relations)
- [Data Loading](#data-loading)
  - [Authentication](#authentication)
- [The SkyBlock Calendar](#the-skyblock-calendar)
  - [Elections](#elections)
- [Data Repository Contracts](#data-repository-contracts)
- [Gradle Tasks](#gradle-tasks)
  - [Build and Test](#build-and-test)
  - [Schema Export](#schema-export)
- [Package Structure](#package-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **One connect, then plain lookups** - `SkyBlockData.connect(...)` stands up an H2 in-memory session with every repository registered; everything after that is `getRepository(Item.class).findFirst(...)`
- **Data lives outside the jar** - every model is fetched from `skyblock-data` over the GitHub Contents API at connect time, so a data correction ships without a release here
- **Relations resolve** - `@ManyToOne` foreign keys and `@ForeignIds` id-lists resolve to entity references, and nullable relations come back as `Optional`
- **Second-level caching** - Hibernate query and entity caches are on by default under EhCache, read-write, with a 30-second query-results TTL
- **The SkyBlock calendar** - `SkyBlockDate` converts both directions between real epoch milliseconds and the accelerated 372-day in-game year
- **Election forecasting** - mayor and special-mayor cycles are derived from the year rather than stored, so a future election is computable rather than fetched
- **Auto-registered Gson** - the date adapters and the JPA exclusion strategy attach to `GsonSettings.defaults()` by SPI, with no bootstrap code at the call site

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | 8.x | Wrapper is bundled (`./gradlew`) |
| [Git](https://git-scm.com/) | 2.x+ | For cloning the repository |
| GitHub PAT | - | Strongly recommended. Without one GitHub allows 60 requests per hour per IP, and one connect spends about 42 |

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

41 entity classes live in `api.simplified.skyblock.model`, alongside `BuffEffectsModel` - an interface several of them implement rather than an entity of its own. `RepositoryFactory.resolveModels(Item.class)` scans that package, so adding a model is a matter of putting the class there and giving `skyblock-data` a matching manifest entry - nothing registers it by name.

| Group | Models |
|-------|--------|
| Items | `Item`, `ItemCategory`, `Accessory`, `Gemstone`, `Reforge`, `Enchantment`, `Mixin`, `PetItem`, `BitsItem` |
| Progression | `Skill`, `Collection`, `Slayer`, `Minion`, `HotmPerk`, `Power`, `MelodySong` |
| Combat | `MobType`, `BestiaryCategory`, `BestiarySubcategory`, `BestiaryFamily` |
| Stats | `Stat`, `StatCategory`, `HotPotatoStat` |
| Bonuses | `BonusArmorSet`, `BonusItemStat`, `BonusItemRarity`, `BonusReforgeStat`, `BonusEnchantmentStat`, `BonusPetAbilityStat` |
| World | `Region`, `Zone`, `FairySoul`, `ShopPerk`, `Mayor`, `ZodiacEvent` |
| Consumables | `Potion`, `PotionGroup`, `Brew`, `Essence` |
| Other | `Pet`, `Keyword` |

Shared enums and value types sit alongside them: `Rarity`, `GameMode`, `GameStage` and `Profile` in `common/`, `CraftingRecipe` / `CraftingTable` in `crafting/`, and `SkinTexture` at the root.

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

## Data Loading

No JSON ships in the jar. `SkyBlockFactory` gives every model its own `RemoteJsonSource`, and all of them share one index provider and one file fetcher pointed at the data repository.

```
SkyBlockData.connect(...)
  -> SkyBlockFactory                          # one RemoteJsonSource per model
    -> GitHubIndexProvider                    # GET data/v1/index.json, held after the first call
    -> GitHubFileFetcher                      # GET the file the manifest names for this model
      -> GitHubContentsContract               # Accept: application/vnd.github.raw+json
        -> H2 in-memory schema "skyblock"     # Hibernate, EhCache L2, read-write
```

The manifest is fetched once and held, not once per model - `RemoteJsonSource` asks for it on every load and there is one source per model, so without that hold the same file would be fetched 41 times per connect. `GitHubIndexProvider.refresh()` discards it.

Any GitHub failure is re-thrown as `JpaException` carrying the HTTP status, the source id (`skyblock-data`) and the path, so a 404 on one model names the file rather than surfacing as a decode error.

### Authentication

`SkyBlockFactory`'s no-argument constructor builds its own GitHub clients and reads a personal access token from `SKYBLOCK_DATA_GITHUB_TOKEN`.

| Mode | Budget | Connects per hour |
|------|--------|-------------------|
| Unauthenticated | 60 requests / hour / IP | roughly one |
| Authenticated (PAT) | 5000 requests / hour | roughly one hundred |

A blank or missing token degrades to unauthenticated rather than failing - enough for a single session, not for a suite that connects repeatedly.

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

`Launch` and `Length` hold the anchors: the launch epoch, the Traveling Zoo and Jacob's Contest starts, the first mayor and special-mayor elections, and every millisecond constant the conversions are built from. `ZOO_CYCLE` and `SPECIAL_MAYOR_CYCLE` are the two repeating rotations.

### Elections

An election is identified by its year; both of its windows are derived rather than stored.

```java
Election election = new Election(278);

election.getVoting().getStart();   // late summer 27, year 278
election.getVoting().getEnd();     // late spring 27, year 279
election.getTerm().getStart();     // the same instant voting closes
election.getTerm().getEnd();       // late spring 27, year 280
```

A term begins the moment voting closes, so `term.start` and `voting.end` are the same millisecond. `SpecialElection` adds the special mayor's name and folds it into identity - two special elections of one year with different mayors are not equal.

`SkyBlockDate.getNextMayor()` and `getNextSpecialMayor()` forecast forward from the cycle anchors.

## Data Repository Contracts

Three façades pre-bind `simplified-api/skyblock-data` as the owner and repo of every GitHub call, so a call site passes a path and nothing else.

| Façade | Shape | Can drive one Feign client |
|--------|-------|:--------------------------:|
| `SkyBlockDataContract` | interface extending both Contents contracts | ❌ |
| `SkyBlockDataService` | final class, constructor-injected with both proxies | ❌ |
| `SkyBlockGitDataContract` | interface extending the Git Data contract | ✅ |

The first two are **type façades only**. The read surface needs `Accept: application/vnd.github.raw+json` and the write surface needs `application/vnd.github+json`, and a Feign client carries one static header set - so the two proxies are built separately against the parent contracts and aggregated through `SkyBlockDataContract.from(read, write)`. The Git Data surface uses a single media type, so it can be handed straight to a client builder.

`SkyBlockDataContract` and `SkyBlockDataService` expose the same surface deliberately; pick the ergonomics that suit the call site.

## Gradle Tasks

### Build and Test

```bash
./gradlew build       # compile, test, assemble jar
./gradlew test        # JUnit 5 suite
```

> [!CAUTION]
> `JpaModelTest` connects a real session, which means real GitHub traffic - about 42 requests per run. Unauthenticated, that is most of an hour's budget in one go. Put a token in a `.env` file at the project root:
>
> ```
> SKYBLOCK_DATA_GITHUB_TOKEN=ghp_...
> ```
>
> The `test` task reads that file and injects each entry into the test JVM's environment, because `SkyBlockFactory` reads the variable off the JVM and Gradle has no notion of `.env`. The file is gitignored.

`ElectionTest` is pure arithmetic and needs neither network nor token.

### Schema Export

`SchemaExporter` is a `main()` in the test sources, not a test. It connects the session and writes H2 DDL plus a persistent file database under `.schema/`, which lets IntelliJ resolve JPA column names in the entity classes.

Point IntelliJ at it with:

```
jdbc:h2:file:$PROJECT_DIR$/.schema/skyblock;ACCESS_MODE_DATA=r
```

user `sa`, empty password. The directory is gitignored and excluded from the IDE module.

## Package Structure

```
skyblock/
├── src/
│   ├── main/java/api/simplified/skyblock/
│   │   ├── SkyBlockData.java                  # static locator: connect() + getRepository()
│   │   ├── SkyBlockFactory.java               # one RemoteJsonSource per model, over GitHub
│   │   ├── SkyBlockDataGsonContributor.java   # SPI hook: date adapters + JpaExclusionStrategy
│   │   ├── SkinTexture.java                   # base64 texture blob stored as a JSON column
│   │   ├── common/                            # Rarity, GameMode, GameStage, Profile
│   │   ├── contract/                          # the three skyblock-data façades
│   │   ├── crafting/                          # CraftingRecipe, CraftingTable
│   │   ├── date/                              # SkyBlockDate, Season, Election, SpecialElection
│   │   ├── model/                             # 41 JPA entities + the BuffEffectsModel interface
│   │   └── source/                            # GitHubIndexProvider, GitHubFileFetcher
│   ├── main/resources/META-INF/services/      # GsonContributor SPI registration
│   └── test/java/                             # JpaModelTest, ElectionTest, SchemaExporter
├── build.gradle.kts  settings.gradle.kts  gradle/libs.versions.toml
└── LICENSE.md  CONTRIBUTING.md  CLAUDE.md
```

### Runtime Directories

Created during execution and excluded from version control:

| Path | Contents |
|------|----------|
| `.env` | `SKYBLOCK_DATA_GITHUB_TOKEN`, read by the `test` task |
| `.schema/` | H2 file database written by `SchemaExporter` for IDE JPA resolution |
| `build/` | Gradle outputs |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see [LICENSE](LICENSE.md) for the full text.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This library is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

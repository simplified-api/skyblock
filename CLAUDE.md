# skyblock

The SkyBlock game-data layer: 41 JPA entities in an embedded H2 session, the SkyBlock calendar, and
three façades over the `github` module that fetch the corpus. Root **`api.simplified.skyblock.**`**.

## Build

- Gradle `group` is `dev.sbs`, the package root is `api.simplified.skyblock`, and the JitPack
  coordinate is `com.github.simplified-api:skyblock`. Three org spellings for one module, none of
  them derived from another - only the trailing `skyblock` is shared.
- Every dependency is `api(...)` with an inline `strictly()` pin, including the `github` sibling - a
  data-layer module depending on a vendor-API module, because the corpus is served over the GitHub
  Contents API.
- The `test` task hand-parses `.env` and calls `environment(k, v)` per line. Gradle has no notion of
  `.env` and `SkyBlockFactory` reads the token off the JVM's own environment, so without that
  `doFirst` block the suite runs unauthenticated.

## Gates

`./gradlew test` is `JpaModelTest` plus `ElectionTest`.

**`JpaModelTest` is a network test.** `@BeforeAll` calls `SkyBlockData.connect`, which fetches the
manifest and one file per model - about 42 GitHub requests. Unauthenticated that is 60 per hour per
IP, so the budget is roughly one run. Put `SKYBLOCK_DATA_GITHUB_TOKEN` in `.env` and the ceiling
becomes 5000.

The suite therefore fails for two unrelated reasons that look alike: a genuine model defect, and a
throttled IP. Read the `JpaException` message before diagnosing - it carries the HTTP status, the
source id and the path.

`ElectionTest` is pure arithmetic, no network, no token. Its expected values are epoch milliseconds
captured off the identical class in the `hypixel` module before its post-init hook was removed, so
they are pre-change measurements rather than restatements of the expressions that now produce them.
Do not relax one into a range.

`SchemaExporter` is a `main()` under test sources, not a test. It writes the `.schema/` H2 file
database IntelliJ reads for JPA column resolution; it also connects, so it also spends the budget.

## The corpus is not in the jar

Nothing under `src/main/resources` holds game data - only the SPI service file. Every model is loaded
at connect time from `simplified-api/skyblock-data` over the Contents API.

```
connect -> SkyBlockFactory            # one RemoteJsonSource per resolved model
  -> GitHubIndexProvider              # data/v1/index.json
  -> GitHubFileFetcher                # the file the manifest names
```

- `RemoteJsonSource` asks its `IndexProvider` for the manifest on **every** load, and there is one
  source per model. `GitHubIndexProvider` holds the parsed manifest behind double-checked locking for
  exactly that reason; removing the hold turns one fetch into 41. `refresh()` is the only way to drop
  it.
- `connect` performs network I/O and fails rather than degrading. An unreachable `api.github.com` at
  startup is a failed connect, not a slow one.
- `RepositoryFactory.resolveModels(Item.class)` scans the package `Item` lives in and keeps the 41
  `JpaModel` implementers - `BuffEffectsModel` is an interface those models share, not a 42nd entity.
  **The package is the registration**: a class dropped into `model/` is live and one moved out is
  gone, with no list to update and no compile error either way.
- A model without a manifest entry in the data repository is inert. `master` here must connect
  against `master` there, so a model change lands with or after its corpus change and never before.

## connect() overrides the string type

`SkyBlockData.connect(gsonSettings)` derives a copy with `StringType.DEFAULT` before handing it to
Hibernate, whatever you passed. `GsonSettings.defaults()` ships `StringType.NULL`, which turns an
empty string into a null; the corpus carries empty strings on columns declared `nullable = false`, so
without the override the load is a constraint violation Hibernate reports against a column rather
than against the setting.

Every entity column also needs a **non-null field default**. A `nullable = false` column whose Gson
field is absent from one corpus entry fails the entire connect, and the default is what absorbs an
entry that predates the column.

## The contributor runs last on purpose

`SkyBlockDataGsonContributor.priority()` is `100`, so `GsonSettings.defaults()` applies it after
every default-priority contributor. `JpaExclusionStrategy` decides what Hibernate's Gson-stored
columns serialize, and it has to see the fully registered adapter set to decide correctly - at
default priority it decides against an incomplete one.

The same contributor registers `SkyBlockDate.RealTime.Adapter` and
`SkyBlockDate.SkyBlockTime.Adapter`. A hand-built `GsonSettings` that skips the SPI connects fine and
then fails on the first date column, which reads as a corpus problem.

## Two Election classes, one arithmetic

`api.simplified.skyblock.date.Election` and `api.simplified.hypixel.response.skyblock.election.Election`
are distinct types with identical cycle arithmetic. This one drives `SkyBlockDate`'s mayor
forecasting; that one is a DTO bound off the Hypixel wire. Neither converts to the other, and both
suites pin the same millisecond values so a divergence surfaces on one side.

- Identity is the **year alone**. `Cycle` declares no `equals`, so folding the derived cycles into
  identity silently made two elections of one year unequal - that is what the stored-cycle hook did
  before it was removed.
- `SpecialElection` extends it and adds the mayor name to identity. `equals` uses
  `getClass() != o.getClass()`, so an `Election` and a `SpecialElection` of one year are never equal
  in either direction.
- A term begins the instant voting closes: `term.start == voting.end`, exactly.

## Calendar constants are load-bearing

`Length.MINUTE_MS` is `50000.0 / 60` - a `double`, and the only non-integral constant in the chain.
Everything above it (`HOUR_MS`, `DAY_MS`, `MONTH_MS`, `YEAR_MS`) is a `long` cast off that product,
so the rounding happens once and reordering the multiplications changes results.

- A year is 12 seasons of 31 days - 372 days, not 365.
- `Launch.SKYBLOCK` is `1560275700000L`. `MAYOR_ELECTIONS_START` and `SPECIAL_ELECTIONS_START` are
  themselves computed by constructing a `SkyBlockDate`, so a change to the conversion moves the
  anchors it is measured against.
- Other modules pin exact epoch millisecond values derived from these. A change here is evaluated
  against those pinned numbers, never against the expression that produces them.

## The façades cannot all be proxied

Three façades in `contract/` pre-bind `simplified-api` / `skyblock-data` as owner and repo.

| Façade | Handed to one Feign client |
|---|---|
| `SkyBlockDataContract` | no - extends both Contents contracts |
| `SkyBlockDataService` | no - not an interface |
| `SkyBlockGitDataContract` | yes |

`SkyBlockDataContract` is a **type** façade. It extends `GitHubContentsContract` and
`GitHubContentsWriteContract`, which require different `Accept` media types, and a `ClientConfig`
carries one static header set - so the two proxies are built separately and aggregated through
`SkyBlockDataContract.from(read, write)`. Handing the interface itself to a client builder produces a
proxy whose read or write half is silently wrong.

`SkyBlockDataService` is the same surface as a final class, kept alongside deliberately so call sites
can be judged against both ergonomics. `SkyBlockGitDataContract` is proxyable because the Git Data
API uses one media type throughout.

## Relations

- A single FK is `@ManyToOne` + `@JoinColumn` beside the raw `*_id` column, and both are readable.
  The raw column binds whether or not the relation resolves, so a test asserting only the id passes
  on a broken relation.
- A list of ids is `@ForeignIds`, resolving to entity references beside the raw list.
- A nullable relation is `Optional`, never null - `Reforge.getStone()` is the canonical empty case,
  `BestiaryFamily.getSubcategory()` the canonical present-and-absent pair.
- `Rarity` carries `@SerializedName(alternate = ...)` for two historical spellings: `SUPREME` binds to
  `DIVINE`, `UNOBTAINABLE` to `ADMIN`. The corpus still sends both.
- `FairySoul`'s corpus file is currently empty, which is why its test asserts non-null rather than
  non-empty. That is the fixture, not the model.

## Skip these

- `build/`, `.gradle/` - Gradle output and daemon state.
- `.schema/` - the H2 file database `SchemaExporter` writes for IntelliJ; excluded from the IDE module
  by `build.gradle.kts`.
- `.env` - the GitHub token the `test` task injects. Gitignored, and it stays that way.

## Decisions that stay closed

- Do not bundle corpus JSON under `src/main/resources`. The corpus moved out so a data correction
  ships without a release here; a bundled copy shadows nothing and misleads everyone.
- Do not drop the manifest hold in `GitHubIndexProvider`. It is the difference between one request and
  41 per connect.
- Do not store a derived election cycle on the entity. It makes identity depend on a `Cycle` that
  declares no `equals`.
- Do not hand `SkyBlockDataContract` to a Feign client builder. It is a type façade over two
  media types.
- Do not register a model by name. The package is the registration; a second mechanism would let the
  two disagree.
- Do not relax a pinned epoch millisecond into a range. Those values are measurements of behaviour
  before a change, and a range is what would have let the change through.

# skyblock

34 JPA entities in an embedded H2 session, the versioned JSON corpus they load from, the generator
that indexes it, and the SkyBlock calendar - one tree, so a model rename and its index entry are one
commit. Root **`api.simplified.skyblock.**`**.

The corpus is `data/v1/**`, tracked here. It is a test fixture on disk and a served artifact in
production, and never a classpath resource.

## Build

- Gradle `group` is `dev.sbs`, the package root is `api.simplified.skyblock`, and the JitPack
  coordinate is `com.github.simplified-api:skyblock`. Three org spellings for one module, none of
  them derived from another - only the trailing `skyblock` is shared.
- Every dependency is `api(...)` with an inline `strictly()` pin, including the `github` sibling - a
  data-layer module depending on a vendor-API module, because the corpus is served over the GitHub
  Contents API.
- The `test` task sets `skyblock.corpus.root` to the project directory. A forked test JVM inherits no
  notion of where the project is, and every suite that connects resolves the corpus through that
  property, so the build is what makes the offline connect find its files.
- `SkyBlockFactory` reads `SKYBLOCK_DATA_GITHUB_TOKEN` off the JVM's own environment. Nothing in the
  build injects it: the only things that read it are a production connect and a `SchemaExporter` run
  from the IDE.

## Gates

Two gates, and neither substitutes for the other.

**`./gradlew test` is six classes and none of them touch the network.** `JpaModelTest`,
`BuffCorpusValidationTest` and `SubstituteTokenTest` connect; `EventTest`, `LadderBindingTest` and
`SkyBlockDateTest` bind fixture strings in-process and connect to nothing.

The three that connect go through `LocalSkyBlockData`, which reads `data/v1/index.json` and the files
it names off disk under `skyblock.corpus.root`. The production factory is bound to GitHub and cannot
be repointed, so the test session builds its own `JpaConfig` and registers it with the same
process-wide `SessionManager` that `SkyBlockData.getRepository` resolves against. That manager is
static: a session opened by one class is visible to every other class in the same JVM, so whoever
connects must `disconnect` in `@AfterAll` or the next suite reads the previous one's rows.

`BuffCorpusValidationTest` and `SubstituteTokenTest` open on
`assumeTrue(LocalSkyBlockData.uncoveredModels(root).isEmpty())`. A model this build declares that the
committed manifest carries no file for **skips** those suites rather than failing them, so a green run
that skipped two classes means the models and the index are of different vintages - regenerate, do
not shrug. `JpaModelTest` has no such guard and fails outright, because every source is read during
the connect.

`JpaModelTest` orders its cases: `@Order(1)` is the leaf models, `@Order(2)` is everything with a
foreign key, so a broken relation reports after the table it points at is known good.

`SchemaExporter` is a `main()` under test sources, not a test. It writes the `.schema/` H2 file
database IntelliJ reads for JPA column resolution, and it connects through `SkyBlockData.connect` -
the GitHub path - so it is the one thing here that spends request budget.

**`python scripts/generate_index.py --check` is the second gate**, and CI runs the same command. It
hashes bytes and **does not parse the data**, so malformed JSON passes here and fails at the
consumer; `python -m json.tool <file> > /dev/null` is the missing half.

Pinned numbers are measurements, not restatements. `EventTest` asserts the mayor voting window as
`1684145700000L` and `1684480500000L` and asserts the same two instants against
`new SkyBlockDate(278, LATE_SUMMER, 27, 0)` and `new SkyBlockDate(279, LATE_SPRING, 27, 0)`, so
either the literals or the calendar arithmetic drifting fails there. They are updated deliberately
and never loosened into a range.

## The corpus is in the repo and not in the jar

Both halves are true at once and a reader will assume they cannot be. `data/v1/**` is 36 tracked
files sitting beside the models. Nothing under `src/main/resources` holds game data - only the SPI
service file - and a production connect reads every table off `master` of `simplified-api/skyblock`
over the Contents API, so a data correction reaches a consumer without a release.

```
connect -> SkyBlockFactory            # one RemoteJsonSource per resolved model
  -> ManifestSource                   # data/v1/index.json
  -> fileFetcher                      # the file the manifest names
```

- `RemoteJsonSource` asks its `IndexProvider` for the manifest on **every** load, and there is one
  source per model. `ManifestSource` holds the parsed manifest behind double-checked locking for
  exactly that reason; removing the hold turns one fetch into 34. `refreshManifest()` is the only way
  to drop it.
- A cold connect is **36 requests**: the manifest, 34 primaries and `items_extra.json`.
  Unauthenticated GitHub allows 60 an hour per IP, so a tokenless consumer gets roughly one connect
  per hour. That budget is why the suite reads disk.
- `connect` performs network I/O and fails rather than degrading. An unreachable `api.github.com` at
  startup is a failed connect, not a slow one.
- `RepositoryFactory.resolveModels(Item.class)` scans the package `Item` lives in and keeps the 34
  `JpaModel` implementers. **The package is the registration**: a class dropped into `model/` is live
  and one moved out is gone, with no list to update and no compile error either way.
- Owner and repo are `simplified-api` / `skyblock`; the source id is `skyblock-data` and the test
  session's is `skyblock-data-local`. `SkyBlockFactory.SOURCE_ID` keys exception messages and
  `ExternalAssetState` rows, so it identifies the dataset rather than the repository serving it - the
  two spellings are not a mismatch to tidy.

## A table is a file and a class

Two things move together, and the generator **recovers** the pairing rather than storing it. A JSON
file's stem is matched byte for byte against the `@Table(name = ...)` of an `@Entity` under
`src/main/java/api/simplified/skyblock/model/`, and the `model_class` in the index is that source's
package declaration plus its type name. There is no registry to keep in step, so a table rename that
misses the other half is not a discipline failure that ships - it is a refusal.

`scripts/generate_index.py` reads `.java` as **text**. It masks comments and string literals first,
so no `@` can hide behind a comment or a quote, then reads balanced parentheses so
`@Table(name = "x", indexes = @Index(name = "a"))` keys off the outer `name` rather than the
`@Index`. No JDK, no Gradle, no `build/` - which is why `--check` runs on a bare Python container and
the CI job carries no Java step. `--model-src PATH` points the scan at another model root.

| Refusal | Trigger |
|---|---|
| `has no model` | a primary file whose stem no `@Table(name = ...)` claims |
| `but no data file matches` | an entity whose table name has no primary file |
| `carries @Entity with no @Table(name = ...)` | JPA defaults the name to the entity name, which no file answers to |
| `carries @Table with no @Entity` | `@Entity` is what makes the class a model |
| `annotates a nested type` | either annotation below the top level; the index has no shape for it |
| `declares type 'X', which disagrees with its file name` | the index would name a class no consumer can load |
| `two entities claim table 'X'` | one data file binds into one class |
| `spells @Table with a positional value` | `jakarta.persistence.Table` has no positional element |
| `orphan extra` | an `_extra` with no primary |
| `duplicate primary` / `duplicate extra` | two files for one `(category, table)` |

Every one aborts. The generator never emits a partial index, so a stale index is always a whole
index of an older tree rather than a half-written one.

## The digest is over working-tree bytes

`content_sha256` is taken over the file exactly as stored on disk, which makes three otherwise
cosmetic things load-bearing:

- `.gitattributes` forces `* text=auto eol=lf`. **CRLF changes the digest**, so a Windows checkout
  with `core.autocrlf=true` and no such rule hashes differently from what Linux CI computes and
  **every** entry fails its check rather than one. Anyone deleting that line as cosmetic breaks the
  corpus gate on every Windows clone.
- The generator writes with `write_bytes`, never `write_text` - the latter translates `\n` to `\r\n`
  on Windows and produces an index that disagrees with CI's.
- Output is `json.dumps(..., indent=2, sort_keys=True)` plus a trailing newline. Key order is
  alphabetical, which is why `commit_sha` sits near the top of the document and `version` sits last.

`git ls-files --eol data/v1` must print `w/lf` for all 36 files. Anything else is a `.gitattributes`
regression, and it is the one check that names the cause directly rather than reporting 34 digest
mismatches.

A diff where every line of a file changed is a line-ending or a reformat, not a data change. Check
`git diff --stat` before believing one.

## What the manifest means

- **`count` is tables, not files.** 34 tables, 35 data files - `items_extra.json` folds into its
  primary's entry as three fields rather than getting one of its own.
- **`category` is presentational.** Consumers read `index.json` and never walk directories, so moving
  a file between categories changes its `path` and nothing else about how it is found. This matters
  more now that the corpus sits in the same checkout as the models: the directory is in the `path`
  and nowhere else, and it is not the contract.
- **`model_class` is the entity that binds the file**, recovered from the source carrying the
  matching `@Table`.
- **`generated_at` and `commit_sha` are excluded from the `--check` comparison**, and write mode
  compares content before writing. A regeneration with no content change prints `already in sync, not
  rewriting` rather than churning a timestamp into every commit.
- **`v1/` is a schema-version boundary.** A breaking shape change ships as `v2/` alongside; `v1/` is
  never mutated in place. `DATA_VERSION` in the generator pins which tree it walks.

## An empty table ships as `[]`

`modifiers/hotm_perks.json` and `world/fairy_souls.json` are empty arrays, not absent files. An
entity whose table has no primary file aborts the generator, so the empty array is how a table stays
declared and unpopulated. Deleting one to "clean up" breaks the index; `JpaModelTest` asserts
`FairySoul` non-null rather than non-empty for the same reason, and that is the corpus rather than
the model.

## Extras

`<table>_extra.json` is merged into its primary at load time. It exists where a primary is
bulk-generated from an upstream dump that has never carried certain entries - `items_extra.json`
holds the two anniversary balloon hats, which a regeneration of `items.json` drops every time.

An extra has no model class and no index entry of its own; it appears as `has_extra`, `extra_path`,
`extra_sha256` and `extra_bytes` on its primary. `RemoteJsonSource` fetches it when `has_extra` is
true, which is the 36th request. Adding one without its primary is the `orphan extra` abort.

## items.json is over the envelope cap

`data/v1/items/items.json` is 7,082,076 bytes over 147,227 lines. The GitHub Contents API returns a
base64 envelope **capped at 1 MB** unless the request carries `Accept: application/vnd.github.raw+json`.
That one file is why the read contract pins the raw media type and why the read and write surfaces
cannot share a client: the write surface needs the JSON envelope carrying the blob sha. A consumer
that omits the raw accept fails on this file and succeeds on the other thirty-four, which reads as a
corrupt file rather than a header problem.

## connect() overrides the string type

`SkyBlockData.connect(gsonSettings)` derives a copy with `StringType.DEFAULT` before handing it to
Hibernate, whatever you passed. `GsonSettings.defaults()` ships `StringType.NULL`, which turns an
empty string into a null; the corpus carries empty strings on columns declared `nullable = false`, so
without the override the load is a constraint violation Hibernate reports against a column rather
than against the setting. `LocalSkyBlockData` and `EventTest` both make the same mutation by hand,
which is what lets a fixture bind the way the corpus does.

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

## A read comes off a held generation

`SkyBlockData.getRepository` does not hand back the session's repository - it wraps it in
`ReferenceIndex`, which holds one `findAll()` per model class and answers by scanning those rows.
Every finder `Sortable` offers is written over `stream()`, so holding that one method reaches all of
them and a caller resolving many ids against one table pays a single round trip for all of them.

- **A held row can be one cache duration stale.** The refresh cycle moves a repository's stamp before
  it deletes the rows its source withdrew, so a hold taken between those two steps carries a
  withdrawn row until the stamp moves again. A caller that needs the uncached answer asks
  `getSessionManager()` for the repository instead.
- The holds are static and keyed by model class. Both `connect` paths and `LocalSkyBlockData.disconnect`
  clear them, which is what keeps one suite's rows out of the next suite's session; a new connect that
  forgets to clear reads the dead session's rows and every assertion still passes.

## Calendar constants are load-bearing

`Length.MINUTE_MS` is `50000.0 / 60` - a `double`, and the only non-integral constant in the chain.
Everything above it (`HOUR_MS`, `DAY_MS`, `MONTH_MS`, `YEAR_MS`) is a `long` cast off that product,
so the rounding happens once and reordering the multiplications changes results.

- A year is 12 seasons of 31 days - 372 days, not 365.
- `Launch.SKYBLOCK` is `1560275700000L`, and it is the only constant `Launch` holds. Every other
  anchor is a `Moment` on an `Event.Schedule` in `world/events.json`, turned into an instant by
  constructing a `SkyBlockDate`, so a change to the conversion moves every anchor measured against
  it. An anchor is a representative of a run rather than the first one, which is why a query behind
  it folds forward onto a negative index instead of running out of schedule.
- Other modules pin exact epoch millisecond values derived from these. A change here is evaluated
  against those pinned numbers, never against the expression that produces them.

## The façades cannot all be proxied

Three façades in `contract/` pre-bind `simplified-api` / `skyblock` as owner and repo - the
repository they read is this one.

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
- A wire key that differs from its field is named explicitly and nothing else catches it: the
  experience ladders spell `totalExpRequired` against a field named `totalRequiredXP`, no field
  naming policy is configured, and without the explicit name every threshold binds zero. That is what
  `LadderBindingTest` exists for.
- A description is a template and `%{VALUE:X}` is filled by the substitute whose id is `X`, so the id
  is the token's key rather than a note. Nothing but `SubstituteTokenTest` compares the two halves,
  and a mismatch grants zero in silence.

## CI

`.github/workflows/regenerate-index.yml`, triggered by `data/v1/**`,
`src/main/java/api/simplified/skyblock/model/**`, the generator and the workflow itself. The model
path is in the filter because a `@Table` rename stales the index exactly as a data edit does.

- **Pull request** - `--check`. A stale index fails, the contributor regenerates locally, and the
  generated diff stays visible in review.
- **Push to master** - write mode, auto-committing a changed index as `github-actions[bot]`.

Both jobs are Python alone. The scan reads source text, so no JDK step and no Gradle step belongs
here, and adding one is how the cheap gate stops being cheap.

The push job exists to catch a squash-merge that lost the regenerated index. It is a backstop, not a
reason to skip regenerating in the PR.

## Skip these

- `build/`, `.gradle/` - Gradle output and daemon state.
- `.schema/` - the H2 file database `SchemaExporter` writes for IntelliJ; excluded from the IDE module
  by `build.gradle.kts`.
- `.env` - the GitHub token a production connect or an IDE-run `SchemaExporter` reads. Gitignored, and
  it stays that way.
- `notes/` - gitignored working notes. Nothing tracked reads one, so do not cite a `notes/` path from
  a tracked file; the directory resolves for nobody who clones this.
- `data/v1/items/items.json` - 7 MB over 147,227 lines. Read a slice, never open it whole.
- `data/v1/index.json` - generated. Read it with a `python -c` slice and regenerate it rather than
  editing it.

## Decisions that stay closed

- Do not put corpus JSON under `src/main/resources`. The corpus is served over the Contents API off
  `master`, so a data correction ships without a release; a second copy on the classpath shadows
  nothing and misleads everyone. `data/v1/` is a fixture the tests read off disk and an artifact
  GitHub serves - never a packaged resource.
- Do not hardcode the model-to-table association again. The generator reads it off `@Table`, which is
  what makes a rename impossible to desynchronise; a registry beside the models is a second place for
  the truth to live and it drifts silently.
- Do not give a model a field whose type is derived rather than bound. Every column is a corpus key, so
  a decode needs no repository and opens no session. `Buff.Validator` is the shape that works - a pure
  function over loaded rows, called by the gate rather than by the load.
- Do not drop the manifest hold in `ManifestSource`. It is the difference between one request and 34
  per connect.
- Do not repoint the production factory at disk. `LocalSkyBlockData` builds its own config for the
  suite; a switch on the shipped factory is a production path nothing runs.
- Do not hand `SkyBlockDataContract` to a Feign client builder. It is a type façade over two media
  types.
- Do not register a model by name. The package is the registration; a second mechanism would let the
  two disagree.
- Do not relax a pinned epoch millisecond into a range. Those values are measurements of behaviour
  before a change, and a range is what would have let the change through.
- Do not hand-edit `data/v1/index.json`. A hand-written digest that happens to be wrong is worse than
  a stale one, because the check compares content rather than intent.
- Do not delete an empty table's file. `[]` is the declaration.
- Do not reformat a data file to make an edit. The digest is over the bytes and the diff is the
  review.
- Do not change a field's name, shape or type inside `v1/`. Every consumer binds it; that is what
  `v2/` is for.
- Do not relax `.gitattributes` or the `write_bytes` call. Both exist because the digest must match
  across platforms, and neither failure is local to the file that caused it.
- Do not mark `data/v1/index.json` `linguist-generated`. GitHub collapses the diff in review, and the
  regenerated diff being visible is what makes the index reviewable at all.

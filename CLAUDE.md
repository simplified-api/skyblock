# skyblock

34 JPA models held in memory by a persistence session, the versioned JSON corpus they are read from,
the generator that catalogues it, and the SkyBlock calendar - one tree, so a `@Table` rename and its
data file are one commit. Root **`api.simplified.skyblock.**`**.

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
- Nothing in this module reads a token or the environment. `SkyBlockData.connect()` reads the corpus
  unauthenticated; a caller that writes it back names its own variable through `GitHubToken.of`, adds
  it to `SkyBlockData.corpus()` and hands the built corpus to `SkyBlockData.writing`.

## Gates

Two gates, and neither substitutes for the other.

**`./gradlew test` is nine classes and none of them touch the network.** `JpaModelTest`,
`BuffCorpusValidationTest`, `SubstituteTokenTest`, `StatGrantsTest` and `SkyBlockDataTest` connect;
`CorpusOriginTest` answers the Contents API from memory; `EventTest`, `LadderBindingTest` and
`SkyBlockDateTest` bind fixture strings in-process and connect to nothing.

The five that connect hand `SkyBlockData.connect(source)` a `LocalSkyBlockData.checkout(root)`, a
read-only source builder reading `data/v1/index.json` and the layers it names off disk under
`skyblock.corpus.root`, so the
session reads the same resolved models with the same `corpusSettings()` as the published connect.
The corpus connects once per JVM and the first connect wins: whichever suite runs first reads the
checkout, every later connect returns that session, and nothing disconnects it. Every suite connects
the same checkout, so that is the session each of them wants. A test whose assertions depend on
performing a connect itself - counting the reads one makes, say - connects a `JpaConfig` over a
`Checkout` on a `SessionManager` of its own, so it does not depend on the order the suites run in.

`BuffCorpusValidationTest` and `SubstituteTokenTest` open on
`assumeTrue(LocalSkyBlockData.uncoveredModels(root).isEmpty())`. A model this build declares that the
committed manifest carries no document for **skips** those suites rather than failing them, so a green
run that skipped two classes means the models and the index are of different vintages - regenerate, do
not shrug. `JpaModelTest`, `StatGrantsTest` and `SkyBlockDataTest` have no such guard and fail
outright, because every model is read during the connect.

`JpaModelTest` orders its cases leaves first and deeper chains last, so a broken relation usually
reports after the table it points at is known good.

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
connect -> JpaConfig(resolveModels(Item.class), DocumentSource)
  -> CorpusOrigin.fingerprints        # the branch tip, then data/v1/index.json at that tip
  -> CorpusOrigin.layersOf            # the held catalogue, the layers of one document
  -> CorpusOrigin.read                # each layer the catalogue names, at the catalogue's tip
tick, every ten minutes
  -> CorpusOrigin.fingerprints        # the branch tip; the catalogue only when the tip moved
  -> the moved documents, each with every document linking into it
```

- `CorpusOrigin` asks `GitHubCorpus.manifest()` for the catalogue on **every** model's read, and
  there are 34 models. The corpus holds the parsed catalogue behind double-checked locking for exactly
  that reason; removing the hold turns one fetch into 34. The hold lives on the `GitHubCorpus`
  instance, and only the connect that reads builds one.
- The connect that reads makes **37 requests**: the branch tip, the manifest at that tip, 34
  primaries and `items_extra.json`. Each ten-minute tick then makes one - the branch tip - and a tick
  that finds it moved adds the manifest and the layers of every moved document and every document
  linking into one. `connect()` carries no token, and unauthenticated GitHub allows 60 an hour per
  IP, so a consumer gets one connect and its six ticks per hour, with room for what a moved tip
  re-reads. That budget is why the suite reads disk.
- `CorpusOrigin.read` reads every layer at the commit the held manifest was read at, never at the
  branch. A body always comes out of the same tree as the fingerprint the session recorded for it,
  and the client's one-minute response cache cannot replay a body from before a move. It does not
  read at the manifest's `revision`: the generator records the commit its checkout stood at, which
  is never the commit carrying the manifest, and a manifest regenerated locally over uncommitted
  files names a commit that does not hold the documents it fingerprints.
- `CorpusOrigin.writing` answers layers through `refreshedLayersOf`, which polls first, so a write
  resolves its layers from the manifest as the branch holds it now rather than as the writer booted
  with it.
- `connect` performs network I/O and fails rather than degrading. An unreachable `api.github.com` at
  startup is a failed connect, not a slow one: the session is shut down and never registered.
- `JpaModel.resolveModels(Item.class)` scans the package `Item` lives in and keeps the 34 `JpaModel`
  implementers. **The package is the registration**: a class dropped into `model/` is live and one
  moved out is gone, with no list to update and no compile error either way.
- Owner, repo and catalogue path are `simplified-api` / `skyblock` / `data/v1/index.json`, bound once
  in `SkyBlockData.corpus()`. The branch is `master` unless a caller names another, and the token is
  always the caller's.

## A table is a file and a class

Two things move together, and **nothing stores the pairing**. A model's `@Table(name = ...)` is its
document name (`JpaModel.documentOf`), the catalogue keys documents by the stem of their primary file,
and `DocumentSource` asks the origin for the layers of that name. A model whose name the catalogue
lacks fails its read with `The origin names no document '<name>' for '<class>'`, which fails the whole
connect; a file no model names is a document nothing reads. There is no registry to keep in step, so a
table rename that misses the other half is not a discipline failure that ships - it is a failed
connect in `./gradlew test`.

`scripts/generate_index.py` reads **no Java**. It walks `data/v1/<category>/*.json`, pairs each
`_extra` with its primary, and hashes every layer. No JDK, no Gradle, no `build/` - which is why
`--check` runs on a bare Python container and the CI job carries no Java step.

| Refusal | Trigger |
|---|---|
| `data root not found` | no `data/v1/` under the repo root |
| `orphan extra` | an `_extra` with no primary |
| `duplicate primary` / `duplicate extra` | two files for one `(category, table)` |
| `two categories both publish 'X'` | one stem under two categories, which would be one document with a layer from each |

Every one aborts. The generator never emits a partial index, so a stale index is always a whole
index of an older tree rather than a half-written one.

## The digest is over working-tree bytes

Each layer's `sha256` is taken over the file exactly as stored on disk, which makes three otherwise
cosmetic things load-bearing:

- `.gitattributes` forces `* text=auto eol=lf`. **CRLF changes the digest**, so a Windows checkout
  with `core.autocrlf=true` and no such rule hashes differently from what Linux CI computes and
  **every** entry fails its check rather than one. Anyone deleting that line as cosmetic breaks the
  corpus gate on every Windows clone.
- The generator writes with `write_bytes`, never `write_text` - the latter translates `\n` to `\r\n`
  on Windows and produces an index that disagrees with CI's.
- Output is `json.dumps(..., indent=2, sort_keys=True)` plus a trailing newline. Key order is
  alphabetical, which is why `documents` opens the document and `revision` sits last.

`git ls-files --eol data/v1` must print `w/lf` for all 36 files. Anything else is a `.gitattributes`
regression, and it is the one check that names the cause directly rather than reporting 35 digest
mismatches.

A diff where every line of a file changed is a line-ending or a reformat, not a data change. Check
`git diff --stat` before believing one.

## What the manifest means

- **`documents` is keyed by logical name, and each value is layers in merge order.** 34 documents,
  35 layers - `items_extra.json` is the second layer of `items` rather than a document of its own.
- **The category is presentational.** Consumers read `index.json` and never walk directories, so moving
  a file between categories changes its `path` and nothing else about how it is found. The corpus
  sits in the same checkout as the models, the directory is in the `path` and nowhere else, and it is
  not the contract.
- **The catalogue names no Java class.** A model finds its document through the `@Table` name it
  already declares, so a model rename cannot stale the index.
- **`revision` is excluded from the `--check` comparison**, and write mode compares content before
  writing. A regeneration with no content change prints `already in sync (34 documents), not rewriting` rather than
  churning a revision into every commit.
- **`v1/` is a schema-version boundary.** A breaking shape change ships as `v2/` alongside; `v1/` is
  never mutated in place. `DATA_VERSION` in the generator pins which tree it walks.

## An empty table ships as `[]`

`modifiers/hotm_perks.json` and `world/fairy_souls.json` are empty arrays, not absent files. A model
whose table has no primary file has no document in the catalogue and fails the connect, so the empty
array is how a table stays declared and unpopulated. Deleting one to "clean up" drops its document
from the catalogue and fails every connect; `JpaModelTest` asserts `FairySoul` non-null rather than
non-empty for the same reason, and that is the corpus rather than the model.

## Extras

`<table>_extra.json` is merged into its primary at load time. It exists where a primary is
bulk-generated from an upstream dump that has never carried certain entries - `items_extra.json`
holds the two anniversary balloon hats, which a regeneration of `items.json` drops every time.

An extra has no document of its own; it is the second layer of its primary's. `DocumentSource` merges
the layers by `@Id`, so a row the extra repeats replaces the primary's in place and a new id is
appended. `CorpusOrigin` reads it as the second layer of `items`, which is what makes a connect 37
requests rather than 36. Adding
one without its primary is the `orphan extra` abort.

A write through `SkyBlockData.writing(...)` lands in the layer that owns each row it names - the last
layer carrying its id - so a balloon hat is written into the extra and any other existing item into
`items.json`. A new item is added to the extra, which a regeneration of `items.json` leaves alone,
and a delete removes the id from every layer carrying it. Only a file the write changes is
rewritten, one commit each, so a write touching both layers is two commits.

An id in the extra overrides the primary's row of that id for good, including one a later upstream
dump starts carrying. The generator does not refuse the pair: `duplicate extra` counts files per
table, not ids. An update the dump makes to such an item stays hidden until the row is removed from
`items_extra.json` by hand, since a delete through the writer removes the id from both files.

## items.json is over the envelope cap

`data/v1/items/items.json` is 7,082,076 bytes over 147,227 lines. The GitHub Contents API returns a
base64 envelope **capped at 1 MB** unless the request carries `Accept: application/vnd.github.raw+json`.
That one file is why the read contract pins the raw media type and why the read and write surfaces
cannot share a client: the write surface's `PUT` takes the JSON media type. A write reads the file it
edits through the raw surface as well, and computes the blob sha from those bytes, so it never needs
the envelope. A consumer that omits the raw accept fails on this file and succeeds on the other
thirty-four, which reads as a corrupt file rather than a header problem.

## connect() overrides the string type

`SkyBlockData.connect()` and `connect(origin)` parse with `SkyBlockData.corpusSettings()`, which is
`GsonSettings.defaults()` with `StringType.DEFAULT` set on top; `SkyBlockData.writing(...)` and
`corpus()` use the same settings. `GsonSettings.defaults()` ships `StringType.NULL`, which turns an
empty string into a null; the corpus carries empty strings on columns declared `nullable = false`, so
without the override an empty string reads as null over the field's default, binds null behind a
`@NotNull` accessor, and a write carries it back as an omitted key rather than `""`.
A suite's checkout is read through `connect(origin)` and so parsed the same way, and `EventTest` makes
the same mutation by hand, which is what lets a fixture bind the way the corpus does.

Every entity column also needs a **non-null field default**. Nothing checks `nullable = false` on a
read: a key absent from one corpus entry leaves the field at its initializer, so a column with no
default binds null behind a `@NotNull` accessor and fails at whichever caller reads it. The default is
what absorbs an entry that predates the column.

## The contributor runs last on purpose

The contributor that runs last is persistence's `JpaGsonContributor`: its `priority()` is `100`, so
`GsonSettings.defaults()` applies it after every default-priority contributor. It registers
`JpaExclusionStrategy`, which keeps every `@Linked` field out of a document in both directions, and it
has to see the fully registered adapter set to decide correctly - at default priority it decides
against an incomplete one.

`SkyBlockDataGsonContributor` runs at the default priority and registers
`SkyBlockDate.RealTime.Adapter` and `SkyBlockDate.SkyBlockTime.Adapter`, and nothing else. A
hand-built `GsonSettings` that skips the SPI binds fine and then fails on the first date column, which
reads as a corpus problem.

## A read comes off a held generation

`SkyBlockData.getRepository` hands back the session's own repository, which holds one generation of
rows in memory. Every finder `Sortable` offers is written over `stream()` and every equality finder
reaches `indexes()` first, so none of them performs I/O. An equality finder over an `@Indexed`
property probes a hash; no SkyBlock model declares one, so every finder scans the held rows, and a
caller resolving many ids against one table pays one scan per id. A generation is read, linked and
only then published, by one reference write, so a reader never sees a row whose links are still
empty.

- **A generation is re-read only when its document moves.** Every SkyBlock model declares
  `@Hydration(every = 10, unit = TimeUnit.MINUTES)`, so a session ticks every ten minutes. A model
  whose manifest fingerprint has not moved keeps its generation and stays `CURRENT`; one whose
  fingerprint moved is re-read with every model linking into it. `getHydratedAt()` says when the
  held generation was published, not when it was last checked. A write through a session built on
  `SkyBlockData.writing(...)` rebuilds the written model plus every model linking into it at once,
  and again at the next tick, because the manifest is regenerated only after the commit lands. A
  model added to `model/` without the annotation holds the rows its connect read until a write
  covers it.
- **The corpus session is held for the JVM's life.** `SkyBlockData` holds the session the first
  successful connect registered, on a `SessionManager` that holds nothing else, and every later
  connect returns it whichever origin it names. A second connect can neither re-read the corpus nor
  register a session behind the first. Nothing disconnects it; the manager's JVM shutdown hook shuts
  it down at exit. A connect that fails holds nothing, so the next one tries again.

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

## Reading and writing are two sources

`SkyBlockData.corpus()` names the corpus once - owner, repo, catalogue path and parser bound, token
and branch left to the caller - and every GitHub call this module makes goes through the
`GitHubCorpus` it builds. `GitHubCorpus` assembles its own two Contents proxies, because
`GitHubContentsContract` and `GitHubContentsWriteContract` require different `Accept` media types and
a `ClientConfig` carries one static header set; nothing here builds a Feign client.

| Built by | Source | Can write |
|---|---|---|
| `connect()` | `DocumentSource.ReadOnly` that `CorpusOrigin.reading` fills, unauthenticated | no |
| `connect(source)` | `DocumentSource.ReadOnly` from the builder handed in | no |
| `writing(corpus)` | `DocumentSource.ReadWrite` that `CorpusOrigin.writing` fills | yes |

The write instruction is a property of the source's type, not a setting on it: only a
`DocumentSource.ReadWrite` builder takes one, and only `CorpusOrigin.writing`, reached through
`writing(...)`, gives it one here. The session a
connect holds has no write half to reach for, and `SkyBlockData` offers no write: a caller that
writes the corpus connects `writing(corpus)` on a `SessionManager` of its own and writes through that
session. A write is one edit per file, committed as `Update <path>`: the file's text and blob sha
come out of one read at the branch, the change applies to that text, and the commit carries that
sha. A file that moved, or a body the response cache replayed from before the branch moved, is
refused with a `409` rather than overwritten.

## Relations

- A relation is a `transient` field marked `@Linked("<idProperty>")` beside the raw id column it
  resolves, and both are readable. A field of the target type resolves one row, an `Optional` of it
  one row that may be absent, and a `ConcurrentList` of it many. The raw column binds whether or not
  the relation resolves, so a test asserting only the id passes on a broken list or `Optional`.
- Links resolve once per generation, after every model has been read and before any is published. An
  id naming no row drops out of a list silently - `JpaModelTest` compares resolved counts against id
  counts for that reason - and leaves an `Optional` link empty. A plain single-valued link whose id is
  absent or names no row fails the link pass, and with it the whole connect, every model included. A
  link whose target this package does not register fails the connect too.
- A relation that may be absent is `Optional`, never null: the id is `Optional<String>`, the linked
  field is `transient @NotNull Optional<X> x = Optional.empty()`, and the class-level `@Getter`
  generates its `Optional` getter - `Reforge.stone` is the canonical empty case,
  `BestiaryFamily.subcategory` the canonical present-and-absent pair. A plain field over an id the
  data can leave out fails every connect the first time it does.
- `JpaSession.write`, on a session over `writing(...)`, links an upsert's rows before they are
  written and refuses one whose plain link would miss. A write straight through the `writing(...)`
  source reaches no session and is not checked, so a row it commits whose plain link misses fails
  the connect of every process that connects before another commit repairs the data.
- `@Linked` fields never reach a document: `JpaExclusionStrategy` skips them on read and on write, so
  a row carries only the id.
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

`.github/workflows/regenerate-index.yml`, triggered by `data/v1/**`, the generator and the workflow
itself. No model path is in the filter: the catalogue names no Java, so a model change cannot stale
it.

- **Pull request** - `--check`. A stale index fails, the contributor regenerates locally, and the
  generated diff stays visible in review.
- **Push to master** - write mode, auto-committing a changed index as `github-actions[bot]`.

Both jobs are Python alone. The generator walks files and hashes them, so no JDK step and no Gradle
step belongs here, and adding one is how the cheap gate stops being cheap.

The push job exists to catch a squash-merge that lost the regenerated index. It is a backstop, not a
reason to skip regenerating in the PR.

## Skip these

- `build/`, `.gradle/` - Gradle output and daemon state.
- `.env` - local credentials; nothing in this module reads it. Gitignored, and it stays that way.
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
- Do not keep a model-to-document registry, in the catalogue or beside the models. A model's `@Table`
  name is its document name and the catalogue keys by file stem, so each side states the pairing once;
  a registry is a second place for the truth to live and it drifts silently.
- Do not give a model a field whose type is derived rather than bound. Every column is a corpus key, so
  a decode needs no repository and opens no session. `Buff.Validator` is the shape that works - a pure
  function over loaded rows, called by the gate rather than by the load.
- Do not drop the catalogue hold in `GitHubCorpus.manifest()`. It is the difference between one
  request and 34 per connect.
- Do not give `connect()` a switch to read disk. A suite hands `connect(source)` a
  `LocalSkyBlockData.checkout(root)`; a switch on the shipped connect is a production path nothing runs.
- Do not build a GitHub client here. `GitHubCorpus` assembles both Contents proxies with their two
  media types, and a second hand-built pair drifts the moment one of them is copied without the other.
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

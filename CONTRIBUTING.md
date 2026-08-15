# Contributing to the SkyBlock Data Layer

Thank you for your interest in contributing! This document explains how to get started, what to expect during the review process, and the conventions this project follows.

Contributions come in two shapes: a change to the Java models and contracts under `src/`, and a change to the JSON corpus under `data/v1/`. They live in one repository and share one review, one branch and one pull request - a change that is both is one commit, and a model rename that leaves its table behind is what the index generator exists to refuse.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
  - [IntelliJ IDEA](#intellij-idea)
  - [Editing JSON](#editing-json)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Data Conventions](#data-conventions)
  - [Editing Data](#editing-data)
  - [Adding a Model and its Table](#adding-a-model-and-its-table)
  - [Commit Messages](#commit-messages)
  - [Validating Output](#validating-output)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Project Architecture](#project-architecture)
- [Legal](#legal)

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | **21+** | Required for the Java half |
| Gradle | 8.x | Wrapper is bundled (`./gradlew`) |
| Python | **3.8+** | Standard library only - no virtualenv, no `pip install` |
| Git | 2.x+ | For cloning and contributing |
| GitHub PAT | - | Optional. Only a live GitHub-backed connect spends requests, and the test suite is not one |
| IDE | Any | IntelliJ IDEA is the recommended editor; anything that can be told not to reformat JSON will do for the corpus |

> [!NOTE]
> The index generator needs Python and nothing else - no JDK, no Gradle and no build output. It reads the model sources as text to find each entity's `@Table(name = ...)`, so `python scripts/generate_index.py` works on a bare checkout and CI runs it with no Java step at all.

> [!TIP]
> `./gradlew test` reads the corpus out of `data/v1/` in this checkout, so the suite issues no network request and needs no token. A personal access token matters when you run something that connects over the GitHub Contents API - `SchemaExporter`, or a consumer application. Export it as `SKYBLOCK_DATA_GITHUB_TOKEN`; without one GitHub allows 60 requests an hour per IP and one connect spends 36 of them.

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/simplified-api/skyblock/fork), then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/skyblock.git
   cd skyblock
   ```

2. **Confirm the index is in sync before you change anything**

   ```bash
   python --version                          # 3.8 or newer
   python scripts/generate_index.py --check
   ```

   A clean checkout prints `ok: data/v1/index.json is in sync (34 entries)`. If it does not, your git configuration is rewriting line endings - see [Line endings](#line-endings) before going further.

3. **Verify the JDK toolchain**

   Gradle's Java toolchain feature downloads JDK 21 automatically if needed. Confirm with:

   ```bash
   ./gradlew --version
   ```

4. **Run the build**

   ```bash
   ./gradlew build
   ```

5. **Build against local siblings (optional)**

   Every upstream dependency is `strictly()`-pinned to a JitPack SHA in `build.gradle.kts`. To test against unpublished sibling changes - most often `persistence` or `github` - build from the `Simplified-Api` parent, whose `settings.gradle.kts` substitutes those coordinates for local sources.

### IntelliJ IDEA

1. Open the project root (the directory containing `settings.gradle.kts`). IntelliJ auto-imports the Gradle build.
2. Ensure the **Project SDK** under **File > Project Structure** is set to a JDK 21 installation.
3. Enable **annotation processing** - the annotation processor generates every accessor on every entity, and the IDE reports phantom errors until the processor runs.
4. For JPA column resolution, run `SchemaExporter` once and attach the H2 file database it writes:

   ```
   jdbc:h2:file:$PROJECT_DIR$/.schema/skyblock;ACCESS_MODE_DATA=r
   ```

   user `sa`, empty password. `.schema/` is gitignored and excluded from the IDE module by the build script. `SchemaExporter` is a `main()` rather than a test and it connects over the network, so give its run configuration a `SKYBLOCK_DATA_GITHUB_TOKEN` and expect it to spend 36 requests.

### Editing JSON

The digests in `data/v1/index.json` are taken over each file's bytes exactly as stored, so anything your editor does on save is a content change.

- **Turn off format-on-save for `.json` in this repository.** A reformat rewrites every line of a 7 MB file, produces an unreviewable diff, and changes the digest.
- **Do not sort keys or re-indent existing files.** Match the surrounding file's style when you add an entry.
- **Ensure LF line endings.** `.gitattributes` declares `* text=auto eol=lf`; an editor forcing CRLF fights it.
- Leave the trailing newline on every file.

## Making Changes

### Branching Strategy

- Create a feature branch from `master` for your work.
- Use a descriptive branch name naming the change rather than its half of the tree: `fix/reforge-stone-nullable`, `feat/attribute-shard-model`, `data/add-attribute-shards`, `docs/index-schema`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

The repository uses Simplified Annotations for boilerplate reduction and enforces a consistent Javadoc, exception, and control-flow style.

#### Javadoc

- **Punctuation** - Single hyphens ` - ` only as separators. Never em dashes, `&mdash;`, or `--`.
- **Voice** - Class/interface = noun phrase. Method = third-person singular verb ("Returns the..."). Field = sentence fragment, no tags.
- **Tags** - Always include `@param`, `@return`, `@throws` where applicable. Lowercase sentence fragments, no trailing period. Single space after the parameter name - never column-align.
- **Cross-references** - Use `{@link}` / `{@linkplain}` / `@see`. Use `{@code}` for inline code. Import link targets so they render with short names.
- **Overrides** - Use `/** {@inheritDoc} */` for methods that override library/framework types. Do not rewrite the parent doc.
- **Field getters** - Field-like interface methods (no params, non-void return) use a noun-phrase fragment without `@return` and without "Gets"/"Returns". A `@Getter` field carries its doc on the field, not a separate method Javadoc block.
- **Structure** - `<p>` on its own line between paragraphs; `<ul>` / `<li>` for lists; `<b>` for emphasis inside list items.
- **Forbidden tags** - Never use `@author` or `@since`.
- **Entity fields** - a column whose name matches its meaning needs no doc. A column that carries a game rule - a magic-power value, a cycle anchor, a tier subtractor - needs one, and it should state the rule.

#### Control flow

Omit braces on single-line bodies; use braces when the body wraps across multiple lines. Applies to all single-statement forms (`if`, `for`, `while`, `do`, lambda bodies).

```java
for (Class<JpaModel> model : this.getModels())
    sources.put(model, new RemoteJsonSource<>(SOURCE_ID, this.manifestSource, fileFetcher, model));

if (session != null) {
    SkyBlockData.getSessionManager().shutdown(session);
    ReferenceIndex.clear();
}
```

#### Collections

Use `getFirst()` / `getLast()` for sequenced access - never `get(0)` or `get(size() - 1)`. This excludes non-`SequencedCollection` types such as Gson's `JsonArray`.

#### Exception classes

Project exceptions follow a **five-constructor pattern** in this order:

1. `(Throwable cause)`
2. `(String message)`
3. `(Throwable cause, String message)`
4. `(@PrintFormat String message, Object... args)`
5. `(Throwable cause, @PrintFormat String message, Object... args)`

Root exceptions (extending `RuntimeException`) reverse the `super()` parameter order:

```java
super(message, cause);
super(String.format(message, args), cause);
```

Child exceptions pass through to the parent, which handles the reversal:

```java
super(cause, message);
super(cause, message, args);
```

Message conventions:

- No trailing punctuation.
- Start with an uppercase letter.
- Use `'%s'` for interpolated values in format strings.

Annotations:

- `@NotNull` on `Throwable cause` and `String message` parameters.
- `@PrintFormat` on format string parameters (from `org.intellij.lang.annotations`).
- `@Nullable` on `Object... args` parameters.

Javadoc:

- **Class-level** - "Thrown when [condition]." Never use the words "unchecked" or "exception" in the description.
- **Constructor** - "Constructs a new {@code ClassName} with [description]."
- **`@param` tags** - lowercase, no trailing period.

> [!NOTE]
> This module declares no exception classes of its own. Source failures are wrapped in `JpaException` from the persistence library, with the HTTP status, the source id and the path interpolated into the message and the original exception kept as the cause. Keep that shape - a wrapper that drops the path forces the next reader to guess which model failed.

### Data Conventions

- Every file is a JSON **array** of objects, each carrying a natural `id`.
- An id is uppercase snake case, matching what Hypixel sends. Some ids carry a colon (`INK_SACK:3`); leave them alone.
- A table with nothing in it ships as `[]` rather than being deleted - an entity with no file aborts the generator, so the empty array is how a table stays declared and unpopulated. `hotm_perks.json` and `fairy_souls.json` are both in that state today.
- Keep entries in the order the file already uses. Re-sorting a whole file to add one entry buries the change.
- The category directories under `data/v1/` are presentational. A consumer reads `index.json` and follows the paths it names, so moving a file between categories is a real change to every path in the manifest and never a tidy-up.

### Editing Data

1. Edit or add files under `data/v1/<category>/`.
2. Regenerate the manifest:

   ```bash
   python scripts/generate_index.py
   ```

3. Stage **both** the data edits and the refreshed `data/v1/index.json`, in one commit.

The generator recomputes each file's `content_sha256`, its byte length and the model class that binds it, and it prints `already in sync, not rewriting` when nothing moved - so running it when you did not need to costs nothing and leaves no diff.

> [!IMPORTANT]
> Never hand-edit `data/v1/index.json`. It is generated output; a hand-written digest that happens to be wrong is worse than a stale one, because the check compares content and not intent.

### Adding a Model and its Table

Two things move together, and the generator refuses to emit an index if either is missing. Nothing registers a model by name - `RepositoryFactory.resolveModels(Item.class)` scans the package `Item` lives in, so the class's location is its registration, and its `@Table(name = ...)` is what names the file.

1. Put the entity in `api.simplified.skyblock.model`, implementing `JpaModel`, annotated `@Entity` and `@Table(name = "<table>")`. The type must be top level and its name must match its file name.
2. Give every column a **non-null default**. Hibernate's `nullable = false` plus a Gson-absent field is a constraint violation at load, and the default is what absorbs a corpus entry that predates the column.
3. Model the relations:
   - a single FK is `@ManyToOne` + `@JoinColumn` beside the raw `*_id` column;
   - a list of ids is `@ForeignIds`, resolving to entity references;
   - a nullable relation returns `Optional`, never null.
4. Add the data file at `data/v1/<category>/<table>.json`, whose stem is the `@Table` name byte for byte. A table with no rows yet ships as `[]`.
5. Regenerate the manifest with `python scripts/generate_index.py`.
6. Add a case to `JpaModelTest`, ordered by dependency depth - `@Order(1)` for leaves, `@Order(2)` for one FK hop, `@Order(3)` for deeper chains - and assert the *resolved* relation, not just that the list is non-empty.

Removing a model is the same in reverse, in one commit: delete the entity, delete its file, regenerate. Deleting either alone aborts the generator, which is the intended failure.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Add the 2025 anniversary balloon hat to the items extra

The bulk item dump is generated from an upstream source that has never
carried the anniversary hats, so they are maintained by hand in the
extra rather than being reintroduced and lost on the next regeneration.
```

- Use the imperative mood ("Add", "Fix", "Update", not "Added", "Fixes").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.
- Say where a value came from. A stat correction with no source is unreviewable.
- Dependency bumps use the `build(deps):` prefix and name the artifact and the new SHA.

### Validating Output

Cheapest first. Run the index check whatever your change touched, then the rest as they apply.

- **The index check** - the corpus gate, and the same one CI runs:

  ```bash
  python scripts/generate_index.py --check
  ```

  It covers a model change as well as a data change: the index names the entity that binds each file, so renaming a `@Table` stales it exactly as editing a JSON file does.

- **JSON validity** - the generator hashes bytes and does not parse your data, so malformed JSON passes the check above and fails at the consumer:

  ```bash
  python -m json.tool data/v1/<category>/<table>.json > /dev/null
  ```

- **Diff size sanity** - `git diff --stat` before you push. A one-entry change touching thousands of lines means your editor reformatted the file; revert and redo it without the reformat.

- **Test suite**

  ```bash
  ./gradlew test
  ```

  It binds the corpus in this checkout, so it is the fastest way to find out whether an entry you edited still decodes into its entity. The corpus validation suites skip themselves rather than fail when the manifest carries no file for a model this build declares, which means the two are of different vintages - regenerating the index is what clears it.

- **Relation coverage** - required when your change adds or moves a relation. Assert the resolved reference (`getCategory().getId()`), not just the raw id - the raw column binds whether or not the FK resolves, so an id-only assertion passes on a broken relation.

- **Round-trip the columns** - required when your change touches a `@GsonType` field or a date column. Those are stored as Gson-serialized text; a missing adapter surfaces at load rather than at compile.

- **Schema check** - required when your change adds or renames a column. `SchemaExporter` is a `main()` that connects over the network; run it from the IDE with a token in its environment, then re-attach `.schema/` so column resolution matches the entity.

- **Calendar changes** - `EventTest` pins exact epoch millisecond values captured from the behaviour before a change, and `SkyBlockDateTest` pins the season arithmetic those values rest on. Every constant in `Length` and `Launch` is load-bearing for both. If your change moves a pinned value, say which one moved and why the new one is right. Do not relax the assertion to a range.

#### Line endings

If `--check` fails on a clean checkout, or your diff shows every line changed, git is rewriting line endings:

```bash
git config core.autocrlf false
git rm --cached -r .
git reset --hard
```

`.gitattributes` forces `eol=lf` for exactly this reason: the digest is over the working-tree bytes, so a CRLF checkout hashes differently from what Linux CI computes and every check fails at once.

## Submitting a Pull Request

1. **Push your branch** to your fork.

   ```bash
   git push origin feat/my-feature
   ```

2. **Open a Pull Request** against the `master` branch of [simplified-api/skyblock](https://github.com/simplified-api/skyblock).

3. **In the PR description**, include:
   - A summary of the changes and the motivation behind them.
   - Where a data value came from - a wiki page, an API response, an in-game observation.
   - Confirmation that `python scripts/generate_index.py` ran and its output is in the commit.
   - Any pinned calendar value you changed, with the old value and the new one.
   - Whether the suite ran clean.

4. **Respond to review feedback.** PRs may go through one or more rounds of review before being merged.

### What gets reviewed

- **The index is in the commit.** The PR check enforces it, but a PR that needs a second push to add it is a PR that regenerated after review started. A model change and its table change are one commit; the generator's refusals are what enforce that, and a PR arriving without the refreshed index has bypassed them locally.
- **Defaults on every column.** A `nullable = false` column with no default fails the whole connect the first time the corpus omits it, and the failure names Hibernate rather than the field.
- **Relation direction and nullability.** `Optional` for a relation that can be absent, a plain reference for one that cannot. Getting this backwards produces a null far from its cause.
- **The diff is the change.** Reformatting noise around a one-line correction blocks a merge - not because the data is wrong, but because nobody can see whether it is.
- **Sourcing.** A number changed without a stated source is not reviewable. Say where it came from.
- **Schema stability.** A field added, renamed or retyped inside `v1/` is a breaking change for every consumer binding it. If the shape has to change, it goes in `v2/`.
- **Calendar arithmetic.** Every constant in `Length` and `Launch` is load-bearing for values other modules pin. A change there is reviewed against the pinned millisecond values, not against the expression.
- **Javadoc and exception style** as documented above. Inconsistent style will be flagged.

## Reporting Issues

Use [GitHub Issues](https://github.com/simplified-api/skyblock/issues) to report bugs, incorrect data, or request a new table.

When reporting a library bug, include:

- **JDK version** (`java -version`)
- **Operating system**
- **The model and field** involved
- **Whether it failed at connect or at lookup** - a connect failure is a corpus or column problem, a lookup failure is a relation or cache problem
- **The `JpaException` message in full** - it names the source id and the path
- **Full stack trace** (if applicable)

When reporting bad data, include:

- **The file and the entry id**
- **The current value and the correct one**
- **Where the correct value comes from** - a source is what makes the report actionable
- **The game version or date** you observed it, if the value changes over time

For a calendar issue, include the real epoch millisecond and the SkyBlock coordinates you expected it to convert to, in both directions.

When reporting a consumer-side failure, include the `content_sha256` your consumer holds for the file alongside the one currently in `data/v1/index.json` - a mismatch means a stale cache rather than bad data.

> [!CAUTION]
> Never paste a personal access token into an issue, a `.env` committed by accident, or a commit.

## Project Architecture

The Java package tree is one half of the checkout; `data/v1/`, `scripts/` and `.github/` are the other, and the README lays out both together.

```
api.simplified.skyblock/
├── ReferenceIndex.java               # repository wrapper holding rows and scanning them
├── SkinTexture.java                  # @GsonType blob, stored as a JSON column
├── SkyBlockData.java                 # static locator: connect() + getRepository()
├── SkyBlockDataGsonContributor.java  # SPI hook, priority 100
├── SkyBlockFactory.java              # RepositoryFactory; one RemoteJsonSource per model
├── common/                           # GameStage, Rarity
├── contract/                         # the three façades over the github module
├── date/                             # SkyBlockDate, Season
└── model/                            # 34 JPA entities - the package IS the registration
```

### Connect flow

```
SkyBlockData.connect(gsonSettings)
  -> gsonSettings.mutate().withStringType(DEFAULT)   # so "" round-trips on nullable=false columns
  -> JpaConfig: H2MemoryDriver, schema "skyblock", EhCache L2, read-write, 30s query TTL
    -> SkyBlockFactory.getSources()                  # one RemoteJsonSource per resolved model
      -> ManifestSource.loadIndex()                  # data/v1/index.json, held after first call
      -> the file fetcher                            # one raw GET per model
        -> Gson -> entity -> Hibernate -> Repository
```

`connect` mutates the supplied `GsonSettings` to `StringType.DEFAULT` internally. That is deliberate: the corpus carries empty strings for columns declared `nullable = false`, and the default string type would turn them into nulls.

The suite takes the same route with the fetches pointed at the checkout - `LocalSkyBlockData` builds its own factory over `data/v1/` and registers it with the same session manager, which is why `./gradlew test` needs no token.

### The Gson contributor runs last

`SkyBlockDataGsonContributor.priority()` returns `100`, so it applies after default-priority contributors. `JpaExclusionStrategy` has to see the fully registered type-adapter set when Gson wires the Hibernate JSON columns; registering it earlier means it decides against an incomplete picture.

### How a data change reaches a consumer

```
edit data/v1/<cat>/<table>.json  (and the entity, when the change is both)
  -> python scripts/generate_index.py     # recomputes content_sha256, bytes, model_class
    -> commit both, open PR
      -> CI --check                       # fails if the index is stale
        -> merge to master
          -> consumer re-fetches index.json, sees a moved digest, re-fetches that file
```

The corpus has no release and no version bump - `master` is what a consumer reads over the Contents API, so a data correction reaches one without waiting on a published artifact. The Java library is a separate question: it is consumed as a JitPack coordinate and a code change reaches a consumer only when they move their pin.

### Why the generator refuses so much

The manifest is the only thing standing between a data edit and a consumer's binder, and every failure it prevents is silent downstream:

| Refusal | What it would otherwise be |
|---------|----------------------------|
| A data file whose table name matches no `@Table` | A file nobody loads, because it is in no index |
| An entity whose `@Table` matches no data file | An index entry naming a file that 404s |
| An `@Entity` with no `@Table(name = ...)` | An entry keyed by the class name, which no data file answers to |
| A nested or misnamed entity type | An index naming a class no consumer can load |
| Two entities claiming one table | One of two classes binding the file arbitrarily |
| An extra with no primary | Hand-maintained entries silently dropped at merge time |
| A duplicate primary or extra | One of two files winning arbitrarily |

## Legal

By submitting a pull request, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE.md), the same license that covers this project.

**Do not commit credentials.** `.env` is gitignored and that entry stays. A token committed to a public repository is revoked by GitHub's secret scanner, but only after it has been readable.

**Do not contribute data obtained by scraping private endpoints, from another project's proprietary dataset, or from any source whose terms forbid redistribution.** Data content here is derived from public Hypixel API responses and community knowledge. Individual entry-level copyrights, where they exist, belong to their respective owners; the corpus as a compilation is licensed under Apache 2.0.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This project is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

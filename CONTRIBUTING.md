# Contributing to the SkyBlock Data Layer

Thank you for your interest in contributing! This document explains how to get started, what to expect during the review process, and the conventions this project follows.

Contributions come in two shapes: a change to the Java models under `src/`, and a change to the JSON corpus under `data/v1/`. They live in one repository and share one review, one branch and one pull request - a change that is both is one commit, and a `@Table` rename that leaves its file behind fails every connect, the test suite's included.

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
> The index generator needs Python and nothing else - no JDK, no Gradle and no build output. It walks `data/v1/` and hashes the files it finds, reading no Java, so `python scripts/generate_index.py` works on a bare checkout and CI runs it with no Java step at all.

> [!TIP]
> `./gradlew test` reads the corpus out of `data/v1/` in this checkout, so the suite issues no network request and needs no token. `SkyBlockData.connect()` reads the published corpus unauthenticated, which GitHub caps at 60 requests an hour per IP; one connect makes 37 requests against it, and its session one more every ten minutes. A personal access token matters only to a caller that writes the corpus back: it builds `SkyBlockData.corpus().token(GitHubToken.of("<VARIABLE>")).build()`, naming its own environment variable, and connects `new JpaConfig(JpaModel.resolveModels(Item.class), SkyBlockData.writing(corpus))` on a session manager of its own.

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

   A clean checkout prints `ok: data/v1/index.json is in sync (34 documents)`. If it does not, your git configuration is rewriting line endings - see [Line endings](#line-endings) before going further.

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
if (session != null)
    SkyBlockData.getSessionManager().shutdown(session);

if (this.attributes == null) {
    this.attributes = new Attributes(
        this.npcSellPrice > 0,
        this.can_place,
        // ...
        this.soulbound
    );
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
> This module declares no exception classes of its own. `CorpusOrigin` restates an error status GitHub answers as `JpaException` from the persistence library, with what was attempted - reading a layer's path, or the catalogue entry for a document - the HTTP status and the reason interpolated into the message and the original exception kept as the cause; the session wraps that in one naming the model that failed to hydrate. Keep that shape - a wrapper that drops the path forces the next reader to guess which file failed.

### Data Conventions

- Every file is a JSON **array** of objects, each carrying a natural `id`.
- An id is uppercase snake case, matching what Hypixel sends. Some ids carry a colon (`INK_SACK:3`); leave them alone.
- A table with nothing in it ships as `[]` rather than being deleted - a model whose document the catalogue does not carry fails the connect, so the empty array is how a table stays declared and unpopulated. `hotm_perks.json` and `fairy_souls.json` are both in that state today.
- Keep entries in the order the file already uses. Re-sorting a whole file to add one entry buries the change.
- The category directories under `data/v1/` are presentational. A consumer reads `index.json` and follows the paths it names, so moving a file between categories is a real change to every path in the manifest and never a tidy-up.

### Editing Data

1. Edit or add files under `data/v1/<category>/`.
2. Regenerate the manifest:

   ```bash
   python scripts/generate_index.py
   ```

3. Stage **both** the data edits and the refreshed `data/v1/index.json`, in one commit.

The generator recomputes each layer's `sha256` and the document it belongs to, and it prints `already in sync (34 documents), not rewriting` when nothing moved - so running it when you did not need to costs nothing and leaves no diff.

> [!IMPORTANT]
> Never hand-edit `data/v1/index.json`. It is generated output; a hand-written digest that happens to be wrong is worse than a stale one, because the check compares content and not intent.

### Adding a Model and its Table

Two things move together, and the connect fails when the model's half has no document. Nothing registers a model by name - `JpaModel.resolveModels(Item.class)` scans the package `Item` lives in, so the class's location is its registration, and its `@Table(name = ...)` is what names the document.

1. Put the entity in `api.simplified.skyblock.model`, implementing `JpaModel`, annotated `@Entity` and `@Table(name = "<table>")`, with `@Id` on its key. The table name is the document the model reads and the id is what its layers merge on; a model missing either fails the read.
2. Give every column a **non-null default**. A key the corpus omits leaves the field at its initializer, so a column with no default binds null behind a `@NotNull` accessor and fails at whichever caller reads it. The default is what absorbs a corpus entry that predates the column.
3. Model the relations with `@Linked`, which names the property carrying the id or ids:
   - a single id is a raw id column beside a `transient` field of the target model, marked `@Linked("<idProperty>")`;
   - a list of ids is a raw id list beside a `transient` `ConcurrentList` of the target model, marked the same way;
   - a relation that may be absent declares its id as `Optional<String>`, keeps the linked field `@Nullable` behind `@Getter(AccessLevel.NONE)`, and exposes a getter returning `Optional`, never null.

   The target has to be a model this package registers - a link to anything else fails the connect. An id naming no row is dropped from a list and leaves a single link null without an error, which is why step 6 asserts the resolved side.
4. Add the data file at `data/v1/<category>/<table>.json`, whose stem is the `@Table` name byte for byte. A table with no rows yet ships as `[]`.
5. Regenerate the manifest with `python scripts/generate_index.py`.
6. Add a case to `JpaModelTest`, ordered leaves first - `@Order(1)` for leaves, `@Order(2)` and `@Order(3)` for models that link further in - and assert the *resolved* relation, not just that the list is non-empty.

Removing a model is the same in reverse, in one commit: delete the entity, delete its file, regenerate. Deleting the file alone fails every connect; deleting the entity alone leaves a document nothing reads.

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

  It covers a data change and nothing else: the catalogue names documents by file stem and reads no Java, so a `@Table` rename passes it and fails the connect instead. The test suite is what catches that.

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

- **Relation coverage** - required when your change adds or moves a relation. Assert the resolved reference (`getCategory().getId()`), not just the raw id - the raw column binds whether or not the link resolves, so an id-only assertion passes on a broken relation.

- **Round-trip the columns** - required when your change touches a `@GsonType` field or a date column. Those bind through Gson adapters; a missing adapter surfaces at load rather than at compile.

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

- **The index is in the commit.** The PR check enforces it, but a PR that needs a second push to add it is a PR that regenerated after review started. A model change and its table change are one commit; a model whose document is missing fails every connect, so a PR whose suite did not run clean has not shown the two agree.
- **Defaults on every column.** A column with no default binds null the first time the corpus omits it, and the failure surfaces at whichever caller reads the field rather than at the load that bound it.
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
- **Whether it failed at connect or at lookup** - a connect failure is a corpus, column or link-target problem, a lookup failure is a relation or finder problem
- **The `JpaException` message and its cause in full** - the message names the model, the cause names the path and the HTTP status
- **Full stack trace** (if applicable)

When reporting bad data, include:

- **The file and the entry id**
- **The current value and the correct one**
- **Where the correct value comes from** - a source is what makes the report actionable
- **The game version or date** you observed it, if the value changes over time

For a calendar issue, include the real epoch millisecond and the SkyBlock coordinates you expected it to convert to, in both directions.

When reporting a consumer-side failure, include what `Repository.getHydratedAt()` and `Repository.getState()` answer for the model alongside the commit that last changed the entry - a session re-reads a document at the first ten-minute tick after the catalogue is regenerated, so a generation published before the fix, or one reporting `STALE`, is a session that has not caught up rather than bad data.

> [!CAUTION]
> Never paste a personal access token into an issue, a `.env` committed by accident, or a commit.

## Project Architecture

The Java package tree is one half of the checkout; `data/v1/`, `scripts/` and `.github/` are the other, and the README lays out both together.

```
api.simplified.skyblock/
├── CorpusOrigin.java                 # the GitHub corpus as document layers; Writing adds the write
├── SkinTexture.java                  # base64 texture blob, a nested object on Item
├── SkyBlockData.java                 # static locator: connect(), getRepository(), write(), corpus(), writing()
├── SkyBlockDataGsonContributor.java  # SPI hook: SkyBlockDate adapters, default priority
├── common/                           # GameStage, Rarity
├── date/                             # SkyBlockDate, Season
└── model/                            # 34 JPA entities - the package IS the registration
```

### Connect flow

```
SkyBlockData.connect()
  -> corpusSettings()                                # defaults() with StringType.DEFAULT, so "" round-trips
  -> JpaConfig(resolveModels(Item.class), DocumentSource(CorpusOrigin))
    -> SessionManager.connect                        # registers the session only once it has hydrated
      -> read every model                            # layersOf(@Table name), then one raw GET per layer
      -> link every model                            # @Linked ids resolved against this pass's rows
      -> publish every generation                    # Repository
```

`connect` parses with `SkyBlockData.corpusSettings()`, which sets `StringType.DEFAULT` on top of `GsonSettings.defaults()`. That is deliberate: the corpus carries empty strings for columns declared `nullable = false`, and the default string type would turn them into nulls.

The suite takes the same route with the reads pointed at the checkout - `LocalSkyBlockData` builds a `JpaConfig` over the same resolved models and `corpusSettings()`, with a `DocumentSource` whose origin reads `data/v1/` off disk, and registers it with the same session manager, which is why `./gradlew test` needs no token.

### The Gson contributor runs last

The one that runs last is persistence's `JpaGsonContributor`, whose `priority()` returns `100`, so it applies after default-priority contributors. It registers `JpaExclusionStrategy`, which keeps every `@Linked` field out of a document in both directions and has to see the fully registered type-adapter set; registering it earlier means it decides against an incomplete picture. `SkyBlockDataGsonContributor` runs at the default priority and registers the two `SkyBlockDate` adapters.

### How a data change reaches a consumer

```
edit data/v1/<cat>/<table>.json  (and the entity, when the change is both)
  -> python scripts/generate_index.py     # recomputes each layer's sha256
    -> commit both, open PR
      -> CI --check                       # fails if the index is stale
        -> merge to master
          -> consumer's next connect      # reads index.json and every layer off master
```

The corpus has no release and no version bump - `master` is what a consumer reads over the Contents API at connect, so a data correction reaches one without waiting on a published artifact. A session already running picks a correction up at its first ten-minute tick after the catalogue is regenerated. The Java library is a separate question: it is consumed as a JitPack coordinate and a code change reaches a consumer only when they move their pin.

### Why the generator refuses so much

The manifest is the only thing standing between a data edit and a consumer's reader, and all but the first failure it prevents are silent downstream:

| Refusal | What it would otherwise be |
|---------|----------------------------|
| No `data/v1/` tree under the repo root | An empty catalogue every connect fails against |
| An extra with no primary | Hand-maintained entries in no document, silently never read |
| A duplicate primary or extra | One of two files winning arbitrarily |
| Two categories publishing one file stem | One document with a layer from each, merged as if one overrode the other |

It refuses nothing about models, because it reads none. A model whose document is missing fails the connect with `The origin names no document`; a file no model names is a document nothing reads.

## Legal

By submitting a pull request, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE.md), the same license that covers this project.

**Do not commit credentials.** `.env` is gitignored and that entry stays. A token committed to a public repository is revoked by GitHub's secret scanner, but only after it has been readable.

**Do not contribute data obtained by scraping private endpoints, from another project's proprietary dataset, or from any source whose terms forbid redistribution.** Data content here is derived from public Hypixel API responses and community knowledge. Individual entry-level copyrights, where they exist, belong to their respective owners; the corpus as a compilation is licensed under Apache 2.0.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This project is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

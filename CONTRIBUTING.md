# Contributing to the SkyBlock Data Layer

Thank you for your interest in contributing! This document explains how to get started, what to expect during the review process, and the conventions this project follows.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
  - [IntelliJ IDEA](#intellij-idea)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Adding a Model](#adding-a-model)
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
| JDK | **21+** | Required |
| Gradle | 8.x | Wrapper is bundled (`./gradlew`) |
| Git | 2.x+ | For cloning and contributing |
| GitHub PAT | - | **Effectively required.** One test run spends about 42 requests; unauthenticated is 60 per hour |
| IDE | Any | IntelliJ IDEA is the recommended editor |

> [!CAUTION]
> `./gradlew test` connects a live session, which fetches the manifest and one file per model from `simplified-api/skyblock-data`. Without a token you get roughly one test run per hour before GitHub throttles your IP.

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/simplified-api/skyblock/fork), then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/skyblock.git
   cd skyblock
   ```

2. **Create a `.env`**

   A [fine-grained token](https://github.com/settings/tokens) with read access to public repositories is enough:

   ```
   SKYBLOCK_DATA_GITHUB_TOKEN=ghp_...
   ```

   The `test` task reads that file and injects each entry into the test JVM's environment - `SkyBlockFactory` reads the variable off the JVM, and Gradle has no notion of `.env`. The file is gitignored; keep it that way.

3. **Verify the JDK toolchain**

   Gradle's Java toolchain feature will download JDK 21 automatically if needed. Confirm with:

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
3. Enable **annotation processing** - Lombok generates every accessor on every entity, and the IDE reports phantom errors until the processor runs.
4. For JPA column resolution, run `SchemaExporter` once and attach the H2 file database it writes:

   ```
   jdbc:h2:file:$PROJECT_DIR$/.schema/skyblock;ACCESS_MODE_DATA=r
   ```

   user `sa`, empty password. `.schema/` is gitignored and excluded from the IDE module by the build script.

## Making Changes

### Branching Strategy

- Create a feature branch from `master` for your work.
- Use a descriptive branch name: `fix/reforge-stone-nullable`, `feat/attribute-shard-model`, `docs/election-cycles`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

The repository uses Lombok for boilerplate reduction and enforces a consistent Javadoc, exception, and control-flow style.

#### Javadoc

- **Punctuation** - Single hyphens ` - ` only as separators. Never em dashes, `&mdash;`, or `--`.
- **Voice** - Class/interface = noun phrase. Method = third-person singular verb ("Returns the..."). Field = sentence fragment, no tags.
- **Tags** - Always include `@param`, `@return`, `@throws` where applicable. Lowercase sentence fragments, no trailing period. Single space after the parameter name - never column-align.
- **Cross-references** - Use `{@link}` / `{@linkplain}` / `@see`. Use `{@code}` for inline code. Import link targets so they render with short names.
- **Overrides** - Use `/** {@inheritDoc} */` for methods that override library/framework types. Do not rewrite the parent doc.
- **Field getters** - Field-like interface methods (no params, non-void return) use a noun-phrase fragment without `@return` and without "Gets"/"Returns". Lombok `@Getter` implementations carry their doc on the field, not a separate method Javadoc block.
- **Structure** - `<p>` on its own line between paragraphs; `<ul>` / `<li>` for lists; `<b>` for emphasis inside list items.
- **Forbidden tags** - Never use `@author` or `@since`.
- **Entity fields** - a column whose name matches its meaning needs no doc. A column that carries a game rule - a magic-power value, a cycle anchor, a tier subtractor - needs one, and it should state the rule.

#### Control flow

Omit braces on single-line bodies; use braces when the body wraps across multiple lines. Applies to all single-statement forms (`if`, `for`, `while`, `do`, lambda bodies).

```java
if (held != null) return held;

for (Class<JpaModel> model : this.getModels()) {
    if (sources.containsKey(model))
        continue;
    sources.put(model, new RemoteJsonSource<>(SOURCE_ID, indexProvider, fileFetcher, model));
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

### Adding a Model

Nothing registers a model by name. `RepositoryFactory.resolveModels(Item.class)` scans the package `Item` lives in, so the class's location is its registration.

1. Put the entity in `dev.simplified.skyblock.model`, implementing `JpaModel`.
2. Give every column a **non-null default**. Hibernate's `nullable = false` plus a Gson-absent field is a constraint violation at load, and the default is what absorbs a corpus entry that predates the column.
3. Model the relations:
   - a single FK is `@ManyToOne` + `@JoinColumn` beside the raw `*_id` column;
   - a list of ids is `@ForeignIds`, resolving to entity references;
   - a nullable relation returns `Optional`, never null.
4. Add the matching entry to the [skyblock-data](https://github.com/simplified-api/skyblock-data) manifest and its JSON file. The model is inert without one.
5. Add a case to `JpaModelTest`, ordered by dependency depth - `@Order(1)` for leaves, `@Order(2)` for one FK hop, `@Order(3)` for deeper chains - and assert the *resolved* relation, not just that the list is non-empty.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Derive both election cycles on demand

The cycles were stored on the entity by a post-init hook, which made
identity depend on them - Cycle declares no equals, so two elections
of the same year compared unequal. Compute both from the year instead
and leave identity as the year alone.
```

- Use the imperative mood ("Add", "Fix", "Update", not "Added", "Fixes").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.
- Dependency bumps use the `build(deps):` prefix and name the artifact and the new SHA.

### Validating Output

- **Test suite**

  ```bash
  ./gradlew test
  ```

  Budget your runs. Each one spends about 42 GitHub requests.

- **Relation coverage** - required when your change adds or moves a relation. Assert the resolved reference (`getCategory().getId()`), not just the raw id - the raw column binds whether or not the FK resolves, so an id-only assertion passes on a broken relation.

- **Round-trip the columns** - required when your change touches a `@GsonType` field or a date column. Those are stored as Gson-serialized text; a missing adapter surfaces at load rather than at compile.

- **Schema check** - required when your change adds or renames a column:

  ```bash
  ./gradlew test --tests '*SchemaExporter*' || java -cp <test-classpath> dev.simplified.skyblock.schema.SchemaExporter
  ```

  Re-attach `.schema/` in the IDE afterwards so column resolution matches the entity.

- **Calendar changes** - `ElectionTest` pins exact epoch millisecond values captured from the behaviour before a change. If your change moves one, say which value moved and why the new one is right. Do not relax the assertion to a range.

## Submitting a Pull Request

1. **Push your branch** to your fork.

   ```bash
   git push origin feat/my-feature
   ```

2. **Open a Pull Request** against the `master` branch of [simplified-api/skyblock](https://github.com/simplified-api/skyblock).

3. **In the PR description**, include:
   - A summary of the changes and the motivation behind them.
   - A link to the companion `skyblock-data` PR, if the corpus has to move with this one.
   - Any pinned calendar value you changed, with the old value and the new one.
   - Whether you ran the suite authenticated, and whether it passed clean.

4. **Respond to review feedback.** PRs may go through one or more rounds of review before being merged.

### What gets reviewed

- **Defaults on every column.** A `nullable = false` column with no default fails the whole connect the first time the corpus omits it, and the failure names Hibernate rather than the field.
- **Relation direction and nullability.** `Optional` for a relation that can be absent, a plain reference for one that cannot. Getting this backwards produces a null far from its cause.
- **Corpus coupling.** A model change that needs a corpus change lands with, or after, its `skyblock-data` counterpart - never before. `master` here must connect against `master` there.
- **Calendar arithmetic.** Every constant in `Length` and `Launch` is load-bearing for values other modules pin. A change there is reviewed against the pinned millisecond values, not against the expression.
- **Javadoc and exception style** as documented above. Inconsistent style will be flagged.

## Reporting Issues

Use [GitHub Issues](https://github.com/simplified-api/skyblock/issues) to report bugs or request features.

When reporting a bug, include:

- **JDK version** (`java -version`)
- **Operating system**
- **The model and field** involved
- **Whether it failed at connect or at lookup** - a connect failure is a corpus or column problem, a lookup failure is a relation or cache problem
- **The `JpaException` message in full** - it names the source id and the path
- **The corresponding entry** from the `skyblock-data` JSON file
- **Full stack trace** (if applicable)

For a calendar issue, include the real epoch millisecond and the SkyBlock coordinates you expected it to convert to, in both directions.

> [!CAUTION]
> Never paste a personal access token into an issue, a `.env` committed by accident, or a commit.

## Project Architecture

A brief overview to help you find your way around the codebase:

```
dev.simplified.skyblock/
├── SkyBlockData.java                 # static locator: connect() + getRepository()
├── SkyBlockFactory.java              # RepositoryFactory; one RemoteJsonSource per model
├── SkyBlockDataGsonContributor.java  # SPI hook, priority 100
├── SkinTexture.java                  # @GsonType blob, stored as a JSON column
├── common/                           # Rarity, GameMode, GameStage, Profile
├── contract/                         # the three skyblock-data façades over the github module
├── crafting/                         # CraftingRecipe, CraftingTable
├── date/                             # SkyBlockDate, Season, Election, SpecialElection
├── model/                            # 41 JPA entities - the package IS the registration
└── source/                           # GitHubIndexProvider, GitHubFileFetcher
```

### Connect flow

```
SkyBlockData.connect(gsonSettings)
  -> gsonSettings.mutate().withStringType(DEFAULT)   # so "" round-trips on nullable=false columns
  -> JpaConfig: H2MemoryDriver, schema "skyblock", EhCache L2, read-write, 30s query TTL
    -> SkyBlockFactory.getSources()                  # one RemoteJsonSource per resolved model
      -> GitHubIndexProvider.loadIndex()             # data/v1/index.json, held after first call
      -> GitHubFileFetcher.fetchFile(path)           # one raw GET per model
        -> Gson -> entity -> Hibernate -> Repository
```

`connect` mutates the supplied `GsonSettings` to `StringType.DEFAULT` internally. That is deliberate: the corpus carries empty strings for columns declared `nullable = false`, and the default string type would turn them into nulls.

### The Gson contributor runs last

`SkyBlockDataGsonContributor.priority()` returns `100`, so it applies after default-priority contributors. `JpaExclusionStrategy` has to see the fully registered type-adapter set when Gson wires the Hibernate JSON columns; registering it earlier means it decides against an incomplete picture.

### Two Election classes

`dev.simplified.skyblock.date.Election` and `api.simplified.hypixel.response.skyblock.election.Election` are separate types with the same arithmetic. This one drives `SkyBlockDate`'s mayor forecasting; that one is a DTO bound off the Hypixel wire. They are not interchangeable and neither converts to the other - `ElectionTest` here and the equivalent case in the hypixel suite pin the same millisecond values on purpose.

## Legal

By submitting a pull request, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE.md), the same license that covers this project.

**Do not commit credentials.** `.env` is gitignored and that entry stays. A token committed to a public repository is revoked by GitHub's secret scanner, but only after it has been readable.

**Do not commit game data.** The corpus lives in [skyblock-data](https://github.com/simplified-api/skyblock-data) precisely so it can be corrected without a release here; a JSON file checked in under `src/main/resources` would shadow nothing and mislead everyone.

Hypixel and SkyBlock are properties of Hypixel Inc.; Minecraft is a trademark of Mojang AB. This library is an independent data layer and is not affiliated with, endorsed by, or sponsored by either.

#!/usr/bin/env python3
"""Generate or verify data/v1/index.json.

The index is a machine-readable manifest that consumers use to discover files
without hard-coding paths. Every entry pairs a JSON file in data/v1/ with the
JPA entity that binds it, so the index is built from two inputs: the on-disk
contents of data/v1/, and a scan of the model sources under
src/main/java/api/simplified/skyblock/model/ for their @Table(name = ...).

The scan reads Java text, so the script needs no JDK, no Gradle and no build
output and --check runs on a bare Python container. A data file with no entity
and an entity with no data file are both refusals naming the offender; the
generator aborts rather than emitting a partial index.

Usage:
    python scripts/generate_index.py                    # write data/v1/index.json
    python scripts/generate_index.py --check            # verify, exit 1 if stale
    python scripts/generate_index.py --model-src PATH   # scan a model root elsewhere
    python scripts/generate_index.py --help

Requirements: Python 3.8 or newer, standard library only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# The table name is the stem of the primary JSON file (e.g., "items" for
# "items.json"), and it is matched byte for byte against the @Table(name = ...)
# of an @Entity under MODEL_SUBPATH. The model class named in the index is that
# entity's package declaration plus its simple name.
#
# There is no registry to keep in step: a new JSON file under data/v1/ is
# registered by the annotation on the entity that binds it. A file with no
# entity, and an entity with no file, are both refusals.
# ---------------------------------------------------------------------------

MODEL_SUBPATH = "src/main/java/api/simplified/skyblock/model"

ENTITY_RE = re.compile(r"@\s*(?:jakarta\.persistence\.)?Entity\b")
TABLE_RE = re.compile(r"@\s*(?:jakarta\.persistence\.)?Table\b")
TYPE_RE = re.compile(r"\b(?:class|enum|record|interface)\s+(\w+)")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
NAME_RE = re.compile(r"\bname\s*=\s*\x00(\d+)\x00")
LONE_RE = re.compile(r"^\s*\x00(\d+)\x00\s*$")

DATA_VERSION = 1
INDEX_VERSION = 1
EXTRA_SUFFIX = "_extra"
INDEX_FILENAME = "index.json"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def sha256_hex(path: Path) -> str:
    """Return the lowercase hex SHA-256 digest of a file's bytes."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_commit_sha(repo_root: Path) -> Optional[str]:
    """Return the current HEAD commit SHA, or None if git is unavailable."""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            check=False,
        )
    except FileNotFoundError:
        return None
    if result.returncode != 0:
        return None
    sha = result.stdout.strip()
    return sha if sha else None


def relative_forward(path: Path, repo_root: Path) -> str:
    """Return a repo-root-relative path with forward slashes, or the whole path when it lies outside."""
    try:
        return path.relative_to(repo_root).as_posix()
    except ValueError:
        return path.as_posix()


def classify_file(stem: str) -> Tuple[str, bool]:
    """Return (table_name, is_extra) for a JSON filename stem."""
    if stem.endswith(EXTRA_SUFFIX):
        return stem[: -len(EXTRA_SUFFIX)], True
    return stem, False


# ---------------------------------------------------------------------------
# Model source scan
# ---------------------------------------------------------------------------


def mask_source(text: str) -> Tuple[str, List[str]]:
    """Return the source with comments deleted and literals replaced by index markers.

    Java hides annotations in three places a regex cannot see past - a line comment, a block or
    javadoc comment, and a string literal. Removing all three first means the match only ever sees
    code, while the marker keeps the one literal the scan actually wants reachable. Every newline a
    removed run spanned is preserved, so an offset in the masked text carries the line number of the
    original with no side table, and no brace, parenthesis or @ can be hiding behind a comment or a
    quote once the pass is done.
    """
    out: List[str] = []
    literals: List[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
        elif ch == "/" and text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append("\n" * text.count("\n", i, end))
            i = end
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end
            out.append("\x00%d\x00" % len(literals))
            literals.append(text[i + 3:end])
            out.append("\n" * text.count("\n", i, end))
            i = min(n, end + 3)
        elif ch in "\"'":
            # The newline guard keeps a malformed file from swallowing the rest of the source into
            # one literal, and the escape branch keeps a \" from terminating it early.
            j, buf = i + 1, []
            while j < n and text[j] != ch and text[j] != "\n":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1])
                    j += 2
                    continue
                buf.append(text[j])
                j += 1
            out.append("\x00%d\x00" % len(literals))
            literals.append("".join(buf))
            i = j + 1
        else:
            out.append(ch)
            i += 1
    return "".join(out), literals


def depth_at(masked: str, pos: int) -> int:
    """Return the brace nesting depth of an offset in masked source."""
    return masked.count("{", 0, pos) - masked.count("}", 0, pos)


def line_of(masked: str, pos: int) -> int:
    """Return the 1-based line number of an offset in masked source."""
    return masked.count("\n", 0, pos) + 1


def read_args(masked: str, at: int) -> Optional[str]:
    """Return the text between the parentheses that follow position at, or None when there are none.

    The read is balanced rather than a run up to the first ')', because
    @Table(name = "x", indexes = @Index(name = "a")) is legal and its first ')' closes the @Index.
    """
    i = at
    while i < len(masked) and masked[i].isspace():
        i += 1
    if i >= len(masked) or masked[i] != "(":
        return None
    depth, start = 0, i + 1
    while i < len(masked):
        if masked[i] == "(":
            depth += 1
        elif masked[i] == ")":
            depth -= 1
            if depth == 0:
                return masked[start:i]
        i += 1
    return None


def outer_only(args: str) -> str:
    """Return an annotation's argument text with everything nested inside parentheses blanked out.

    A sibling annotation carries a name element of its own - @Index(name = "idx") is the one JPA
    puts here - and only the element belonging to @Table itself may key the index. Blanking keeps
    the text the same length, so a marker at the outer level still reads back as itself.
    """
    out: List[str] = []
    depth = 0
    for ch in args:
        if ch == "(":
            depth += 1
            out.append(" ")
        elif ch == ")":
            depth -= 1
            out.append(" ")
        else:
            out.append(" " if depth > 0 else ch)
    return "".join(out)


def scan_source(source_path: Path, repo_root: Path) -> Optional[Tuple[str, str]]:
    """Return (table_name, model FQN) for one model source, or None when it declares no entity.

    @Entity is the recogniser and @Table is then mandatory: JPA defaults an absent table name to the
    entity name, which no data file answers to, so an entity missing its table is a refusal rather
    than a guess. A file carrying neither annotation is the only silent outcome.
    """
    display = relative_forward(source_path, repo_root)
    masked, literals = mask_source(source_path.read_text(encoding="utf-8"))

    entities = list(ENTITY_RE.finditer(masked))
    annotations = list(TABLE_RE.finditer(masked))
    if not entities and not annotations:
        return None

    for match in sorted(entities + annotations, key=lambda m: m.start()):
        if depth_at(masked, match.start()) > 0:
            raise SystemExit(
                f"error: {display}:{line_of(masked, match.start())} annotates a nested type with "
                f"{match.group(0).strip()}\n"
                "       the index has no shape for a nested entity; give it its own file"
            )

    if not entities:
        raise SystemExit(
            f"error: {display}:{line_of(masked, annotations[0].start())} carries @Table with no @Entity\n"
            "       @Entity is what makes the class a model; add it or remove the @Table"
        )
    if not annotations:
        raise SystemExit(
            f"error: {display} carries @Entity with no @Table(name = ...)\n"
            "       every model in the index is keyed by its table name; add one"
        )

    table = annotations[0]
    table_line = line_of(masked, table.start())
    raw_args = read_args(masked, table.end())
    args = None if raw_args is None else outer_only(raw_args)
    name_match = NAME_RE.search(args) if args is not None else None
    if name_match is None:
        if args is not None and LONE_RE.match(args):
            raise SystemExit(
                f"error: {display}:{table_line} spells @Table with a positional value\n"
                '       jakarta.persistence.Table has no positional element; write @Table(name = "...")'
            )
        raise SystemExit(
            f"error: {display}:{table_line} has @Table with no name element\n"
            "       the index is keyed by @Table(name = ...); an omitted name defaults to the class "
            "name, which no data file answers to"
        )

    table_name = literals[int(name_match.group(1))]
    if not table_name:
        raise SystemExit(
            f"error: {display}:{table_line} declares an empty @Table name\n"
            "       the index is keyed by the table name, and an empty one keys nothing"
        )

    package_match = PACKAGE_RE.search(masked)
    if package_match is None:
        raise SystemExit(
            f"error: {display} carries @Entity with no package declaration\n"
            "       the model class in the index is the package plus the type name"
        )

    simple_name = None
    for match in TYPE_RE.finditer(masked, max(entities[0].end(), table.end())):
        if depth_at(masked, match.start()) == 0:
            simple_name = match.group(1)
            break
    if simple_name is None:
        raise SystemExit(
            f"error: {display} carries @Entity with no type declaration after it\n"
            "       the model class in the index is the package plus the type name"
        )
    if simple_name != source_path.stem:
        raise SystemExit(
            f"error: {display} declares type '{simple_name}', which disagrees with its file name\n"
            "       the index would name a class no consumer can load; rename one to match the other"
        )

    return table_name, package_match.group(1) + "." + simple_name


def scan_model_classes(model_root: Path, repo_root: Path) -> Dict[str, Tuple[str, Path]]:
    """Return table name -> (model FQN, source path) for every @Entity under model_root.

    The source path travels with the class name so that a model with no data file can be refused
    against the file that declares it, which a bare class name cannot name.
    """
    if not model_root.is_dir():
        raise SystemExit(
            f"error: model source root not found: {model_root}\n"
            "       every entry in the index names the class that binds it; the scan cannot run "
            "without the model sources"
        )

    model_classes: Dict[str, Tuple[str, Path]] = {}
    for source_path in sorted(model_root.rglob("*.java")):
        found = scan_source(source_path, repo_root)
        if found is None:
            continue
        table_name, fqn = found
        if table_name in model_classes:
            raise SystemExit(
                f"error: two entities claim table '{table_name}'\n"
                f"       {relative_forward(model_classes[table_name][1], repo_root)}\n"
                f"       {relative_forward(source_path, repo_root)}\n"
                "       one data file binds into one class; rename one of the tables"
            )
        model_classes[table_name] = (fqn, source_path)

    if not model_classes:
        raise SystemExit(
            f"error: no @Entity found under {relative_forward(model_root, repo_root)}\n"
            "       every entry in the index names a model class, so an empty scan cannot produce "
            "an index"
        )
    return model_classes


# ---------------------------------------------------------------------------
# Main generation logic
# ---------------------------------------------------------------------------


def build_index(repo_root: Path, model_root: Path) -> dict:
    """Walk data/v1/, scan the model sources and build the index dict."""
    data_root = repo_root / "data" / f"v{DATA_VERSION}"
    if not data_root.is_dir():
        raise SystemExit(f"error: data root not found: {data_root}")

    model_classes = scan_model_classes(model_root, repo_root)
    model_display = relative_forward(model_root, repo_root)

    # Group files by (category, table_name). Each group has one primary and
    # optionally one _extra companion.
    primaries: Dict[Tuple[str, str], Path] = {}
    extras: Dict[Tuple[str, str], Path] = {}

    for category_dir in sorted(data_root.iterdir()):
        if not category_dir.is_dir():
            continue
        category = category_dir.name
        for json_file in sorted(category_dir.glob("*.json")):
            stem = json_file.stem
            table_name, is_extra = classify_file(stem)
            key = (category, table_name)
            if is_extra:
                if key in extras:
                    raise SystemExit(
                        f"error: duplicate extra for {category}/{table_name}: "
                        f"{extras[key].name} and {json_file.name}"
                    )
                extras[key] = json_file
            else:
                if key in primaries:
                    raise SystemExit(
                        f"error: duplicate primary for {category}/{table_name}: "
                        f"{primaries[key].name} and {json_file.name}"
                    )
                primaries[key] = json_file

    # Validate: every extra has a matching primary.
    for (category, table_name), extra_path in extras.items():
        if (category, table_name) not in primaries:
            raise SystemExit(
                f"error: orphan extra {relative_forward(extra_path, repo_root)} "
                f"has no matching primary file"
            )

    # Validate: every primary is bound by an entity.
    unmodelled = sorted(
        (relative_forward(path, repo_root), table_name)
        for (_, table_name), path in primaries.items()
        if table_name not in model_classes
    )
    if unmodelled:
        lines: List[str] = []
        for display, table_name in unmodelled:
            lines.append(f"error: {display} has no model")
            lines.append(f'       no @Table(name = "{table_name}") under {model_display}')
            lines.append("       add the entity, or remove the file")
        raise SystemExit("\n".join(lines))

    # Validate: every entity has a matching primary file.
    found_tables = {table_name for (_, table_name) in primaries.keys()}
    stale_tables = sorted(set(model_classes) - found_tables)
    if stale_tables:
        lines = []
        for table_name in stale_tables:
            source_display = relative_forward(model_classes[table_name][1], repo_root)
            lines.append(
                f'error: {source_display} declares @Table(name = "{table_name}") '
                "but no data file matches"
            )
            lines.append(f"       expected data/v{DATA_VERSION}/<category>/{table_name}.json")
            lines.append("       add the file (an empty table ships as []), or remove the entity")
        raise SystemExit("\n".join(lines))

    # Build file entries, sorted by path for deterministic output.
    files: List[dict] = []
    for (category, table_name), primary_path in sorted(primaries.items()):
        entry = {
            "path": relative_forward(primary_path, repo_root),
            "category": category,
            "table_name": table_name,
            "model_class": model_classes[table_name][0],
            "content_sha256": sha256_hex(primary_path),
            "bytes": primary_path.stat().st_size,
            "has_extra": (category, table_name) in extras,
        }
        if entry["has_extra"]:
            extra_path = extras[(category, table_name)]
            entry["extra_path"] = relative_forward(extra_path, repo_root)
            entry["extra_sha256"] = sha256_hex(extra_path)
            entry["extra_bytes"] = extra_path.stat().st_size
        files.append(entry)

    return {
        "version": INDEX_VERSION,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "commit_sha": git_commit_sha(repo_root),
        "count": len(files),
        "files": files,
    }


def serialize(index: dict) -> str:
    """Serialize the index to a deterministic JSON string with trailing newline."""
    return json.dumps(index, indent=2, sort_keys=True) + "\n"


def content_equals(a: dict, b: dict) -> bool:
    """Compare two index dicts ignoring generated_at and commit_sha."""
    ignored = {"generated_at", "commit_sha"}
    a_stable = {k: v for k, v in a.items() if k not in ignored}
    b_stable = {k: v for k, v in b.items() if k not in ignored}
    return a_stable == b_stable


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the committed index matches the tree; exit 1 if stale",
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=None,
        help="override the repo root (default: the parent of the scripts/ directory)",
    )
    parser.add_argument(
        "--model-src",
        type=Path,
        default=None,
        help="override the model source root (default: <repo-root>/" + MODEL_SUBPATH + ")",
    )
    args = parser.parse_args()

    repo_root = args.repo_root or Path(__file__).resolve().parent.parent
    model_root = args.model_src or repo_root / MODEL_SUBPATH
    index_path = repo_root / "data" / f"v{DATA_VERSION}" / INDEX_FILENAME

    new_index = build_index(repo_root, model_root)
    new_text = serialize(new_index)

    if args.check:
        if not index_path.is_file():
            print(
                f"error: {relative_forward(index_path, repo_root)} is missing",
                file=sys.stderr,
            )
            return 1
        try:
            old_index = json.loads(index_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(
                f"error: {relative_forward(index_path, repo_root)} is not valid JSON: {e}",
                file=sys.stderr,
            )
            return 1
        if not content_equals(old_index, new_index):
            print(
                f"error: {relative_forward(index_path, repo_root)} is out of sync with the tree.",
                file=sys.stderr,
            )
            print(
                "run `python scripts/generate_index.py` locally and commit the result.",
                file=sys.stderr,
            )
            return 1
        print(f"ok: {relative_forward(index_path, repo_root)} is in sync ({new_index['count']} entries)")
        return 0

    index_path.parent.mkdir(parents=True, exist_ok=True)
    existing_text = index_path.read_text(encoding="utf-8") if index_path.is_file() else None
    if existing_text is not None:
        try:
            existing_index = json.loads(existing_text)
        except json.JSONDecodeError:
            existing_index = None
        if existing_index is not None and content_equals(existing_index, new_index):
            print(
                f"ok: {relative_forward(index_path, repo_root)} already in sync "
                f"({new_index['count']} entries), not rewriting"
            )
            return 0

    # write_bytes forces LF line endings on all platforms (write_text on
    # Windows would translate \n to \r\n, producing a file that differs from
    # what Linux CI writes and triggering spurious git CRLF warnings).
    index_path.write_bytes(new_text.encode("utf-8"))
    print(
        f"wrote {relative_forward(index_path, repo_root)} "
        f"({new_index['count']} entries, {len(new_text)} bytes)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

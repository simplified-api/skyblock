#!/usr/bin/env python3
"""Generate or verify data/v1/index.json.

The catalogue is what a consumer discovers files through instead of hard-coding
paths. It maps a logical document name to the ordered layers it merges from - a
primary file and, where one exists, its _extra companion - and records the corpus
revision the walk was taken at.

The logical name is the file stem, and a consumer resolves a type to it through
the table that type already declares. Nothing here reads Java, so a model rename
cannot stale the catalogue and the generator needs no JDK, no Gradle and no build
output.

Usage:
    python scripts/generate_index.py                    # write data/v1/index.json
    python scripts/generate_index.py --check            # verify, exit 1 if stale
    python scripts/generate_index.py --help

Requirements: Python 3.8 or newer, standard library only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# A logical document is named by the stem of its primary JSON file ("items" for
# "items.json"), and the companion "items_extra.json" is its second layer. There
# is no registry to keep in step and nothing to match against: a new file under
# data/v1/ is a new document, and a consumer finds it by the table its model
# declares.
# ---------------------------------------------------------------------------

DATA_VERSION = 1
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
def build_index(repo_root: Path) -> dict:
    """Walk data/v1/ and build the catalogue dict."""
    data_root = repo_root / "data" / f"v{DATA_VERSION}"
    if not data_root.is_dir():
        raise SystemExit(f"error: data root not found: {data_root}")

    # Group files by (category, name). Each group has one primary and optionally one
    # _extra companion, which is the second layer of the same logical document.
    primaries: Dict[Tuple[str, str], Path] = {}
    extras: Dict[Tuple[str, str], Path] = {}

    for category_dir in sorted(data_root.iterdir()):
        if not category_dir.is_dir():
            continue
        category = category_dir.name
        for json_file in sorted(category_dir.glob("*.json")):
            name, is_extra = classify_file(json_file.stem)
            key = (category, name)
            target = extras if is_extra else primaries
            kind = "extra" if is_extra else "primary"
            if key in target:
                raise SystemExit(
                    f"error: duplicate {kind} for {category}/{name}: "
                    f"{target[key].name} and {json_file.name}"
                )
            target[key] = json_file

    # Validate: every extra layers over a primary.
    for (category, name), extra_path in extras.items():
        if (category, name) not in primaries:
            raise SystemExit(
                f"error: orphan extra {relative_forward(extra_path, repo_root)} "
                f"has no matching primary file"
            )

    # A logical name maps to its layers in merge order. Two categories publishing the
    # same name would be one document with a layer from each, which is a corpus mistake
    # rather than an override.
    documents: Dict[str, List[dict]] = {}
    for (category, name), primary_path in sorted(primaries.items()):
        if name in documents:
            raise SystemExit(f"error: two categories both publish '{name}'")
        layers = [{"path": relative_forward(primary_path, repo_root), "sha256": sha256_hex(primary_path)}]
        extra_path = extras.get((category, name))
        if extra_path is not None:
            layers.append({"path": relative_forward(extra_path, repo_root), "sha256": sha256_hex(extra_path)})
        documents[name] = layers

    return {
        "revision": git_commit_sha(repo_root) or "",
        "documents": documents,
    }


def serialize(index: dict) -> str:
    """Serialize the index to a deterministic JSON string with trailing newline."""
    return json.dumps(index, indent=2, sort_keys=True) + "\n"


def content_equals(a: dict, b: dict) -> bool:
    """Compare two catalogues ignoring the revision, which moves with every commit."""
    ignored = {"revision"}
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
    args = parser.parse_args()

    repo_root = args.repo_root or Path(__file__).resolve().parent.parent
    index_path = repo_root / "data" / f"v{DATA_VERSION}" / INDEX_FILENAME

    new_index = build_index(repo_root)
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
        print(f"ok: {relative_forward(index_path, repo_root)} is in sync ({len(new_index['documents'])} documents)")
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
                f"({len(new_index['documents'])} documents), not rewriting"
            )
            return 0

    # write_bytes forces LF line endings on all platforms (write_text on
    # Windows would translate \n to \r\n, producing a file that differs from
    # what Linux CI writes and triggering spurious git CRLF warnings).
    index_path.write_bytes(new_text.encode("utf-8"))
    print(
        f"wrote {relative_forward(index_path, repo_root)} "
        f"({len(new_index['documents'])} documents, {len(new_text)} bytes)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

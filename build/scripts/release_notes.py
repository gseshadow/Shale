#!/usr/bin/env python3
"""Validate authored release notes and merge them into Shale's public manifest."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path

VERSION = re.compile(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\Z")
GROUPS = ("New", "Improvements", "Fixes")


class NotesError(ValueError):
    pass


def _text(value: object, field: str, limit: int) -> str:
    if not isinstance(value, str) or not value.strip():
        raise NotesError(f"{field} must be non-blank text")
    result = value.strip()
    if len(result) > limit:
        raise NotesError(f"{field} exceeds {limit} characters")
    if "<" in result or ">" in result:
        raise NotesError(f"{field} must be plain text, not HTML")
    return result


def load_notes(path: Path, expected_version: str) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise NotesError(f"could not read valid JSON from {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise NotesError("release notes root must be an object")
    allowed = {"version", "title", "releaseDate", "summary", "groups"}
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise NotesError("unknown release-notes fields: " + ", ".join(unknown))
    version = _text(value.get("version"), "version", 32)
    if not VERSION.fullmatch(version):
        raise NotesError("version must be canonical major.minor.build")
    if version != expected_version:
        raise NotesError(f"notes version {version} does not match release version {expected_version}")
    title = _text(value.get("title"), "title", 200)
    summary = _text(value.get("summary"), "summary", 510)
    release_date = value.get("releaseDate")
    if release_date is not None:
        release_date = _text(release_date, "releaseDate", 10)
        try:
            dt.date.fromisoformat(release_date)
        except ValueError as exc:
            raise NotesError("releaseDate must be YYYY-MM-DD") from exc
    groups = value.get("groups")
    if not isinstance(groups, dict):
        raise NotesError("groups must be an object")
    unknown_groups = sorted(set(groups) - set(GROUPS))
    if unknown_groups:
        raise NotesError("unknown release-note groups: " + ", ".join(unknown_groups))
    normalized: dict[str, list[str]] = {}
    total = 0
    for group in GROUPS:
        items = groups.get(group, [])
        if not isinstance(items, list):
            raise NotesError(f"groups.{group} must be an array")
        normalized[group] = [_text(item, f"groups.{group} item", 2000) for item in items]
        total += len(normalized[group])
    if total == 0:
        raise NotesError("at least one release-note item is required")
    result = {"version": version, "title": title, "summary": summary, "groups": normalized}
    if release_date is not None:
        result["releaseDate"] = release_date
    return result


def plain_text(notes: dict) -> str:
    lines = [notes["title"], notes["summary"]]
    for group in GROUPS:
        items = notes["groups"][group]
        if items:
            lines.extend(("", group))
            lines.extend(f"• {item}" for item in items)
    return "\n".join(lines)


def merge(notes_dir: Path, version: str, manifest_path: Path) -> bool:
    if not VERSION.fullmatch(version):
        raise NotesError("release version must be canonical major.minor.build")
    notes_path = notes_dir / f"{version}.json"
    if not notes_path.exists():
        print(f"No authored release notes at {notes_path}; using the generic manifest fallback.")
        return False
    notes = load_notes(notes_path, version)
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise NotesError(f"could not read manifest {manifest_path}: {exc}") from exc
    if not isinstance(manifest, dict) or manifest.get("version") != version:
        raise NotesError("manifest version must match release notes before they are merged")
    manifest["notes"] = plain_text(notes)
    manifest["releaseNotes"] = notes
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Validated and merged release notes from {notes_path}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--notes-dir", type=Path, default=Path("release-notes"))
    args = parser.parse_args()
    try:
        merge(args.notes_dir, args.version, args.manifest)
        return 0
    except NotesError as exc:
        print(f"Release notes validation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

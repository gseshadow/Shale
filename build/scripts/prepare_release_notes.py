#!/usr/bin/env python3
"""Create a deterministic, review-required release-notes draft when one is absent."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path

from release_notes import GROUPS, VERSION, load_notes


PREPARED = 10
RELEASE_SUBJECT = re.compile(r"Release Shale ((?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))\Z")
EXCLUDED = re.compile(
    r"^(?:chore(?:\([^)]*\))?:\s*)?(?:release(?: shale)?|version bump|bump version)\b",
    re.IGNORECASE,
)
FIX = re.compile(r"\b(?:fix(?:es|ed)?|bug|regression|correct(?:s|ed|ion)?|resolve[sd]?)\b", re.IGNORECASE)
NEW = re.compile(r"\b(?:add(?:s|ed)?|introduc(?:e[sd]?|ing)|new|implement(?:s|ed)?)\b", re.IGNORECASE)


class PreparationError(RuntimeError):
    pass


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise PreparationError(f"git {' '.join(args)} failed: {detail}")
    return result.stdout


def published_version(root: Path) -> str:
    manifest_path = root / "build" / "assets" / "shale-stable.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PreparationError(f"could not read published release metadata {manifest_path}: {exc}") from exc
    version = manifest.get("version") if isinstance(manifest, dict) else None
    if not isinstance(version, str) or not VERSION.fullmatch(version):
        raise PreparationError(f"published release metadata has no canonical version: {manifest_path}")
    return version


def release_boundary(root: Path, version: str) -> str | None:
    subjects = git(root, "log", "--format=%H%x00%s")
    records = subjects.splitlines()
    expected = f"Release Shale {version}"
    for record in records:
        commit, separator, subject = record.partition("\0")
        if separator and subject == expected:
            return commit
    return None


def commit_subjects(root: Path, boundary: str | None) -> list[str]:
    revision = f"{boundary}..HEAD" if boundary else "HEAD"
    output = git(root, "log", "--reverse", "--format=%s", revision)
    return [subject.strip() for subject in output.splitlines() if subject.strip()]


def categorize(subjects: list[str]) -> dict[str, list[str]]:
    groups = {group: [] for group in GROUPS}
    seen: set[str] = set()
    for subject in subjects:
        if subject in seen or subject.startswith("Merge ") or EXCLUDED.search(subject):
            continue
        seen.add(subject)
        if FIX.search(subject):
            group = "Fixes"
        elif NEW.search(subject):
            group = "New"
        else:
            group = "Improvements"
        groups[group].append(subject)
    return groups


def draft(root: Path, version: str, today: dt.date | None = None) -> dict:
    previous = published_version(root)
    boundary = release_boundary(root, previous)
    groups = categorize(commit_subjects(root, boundary))
    source = f"since Shale {previous}" if boundary else f"from available Git history (published version {previous})"
    if not any(groups.values()):
        groups["Improvements"].append(
            "Review this draft and replace this placeholder with release highlights supported by the completed work."
        )
    return {
        "version": version,
        "title": f"What's New in Shale {version}",
        "releaseDate": (today or dt.date.today()).isoformat(),
        "summary": f"Draft generated from commit messages {source}. Review and edit before publishing.",
        "groups": groups,
    }


def prepare(root: Path, version: str, today: dt.date | None = None) -> tuple[Path, bool]:
    if not VERSION.fullmatch(version):
        raise PreparationError("release version must be canonical major.minor.build")
    path = root / "release-notes" / f"{version}.json"
    if path.exists():
        return path, False
    value = draft(root, version, today)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open("x", encoding="utf-8", newline="\n") as handle:
            json.dump(value, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    except FileExistsError:
        return path, False
    load_notes(path, version)
    return path, True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    try:
        path, created = prepare(args.root.resolve(), args.version)
    except (PreparationError, OSError) as exc:
        print(f"Release-notes preparation failed: {exc}", file=sys.stderr)
        return 1
    if not created:
        print(f"Using existing developer-authored release notes: {path}")
        return 0
    print(f"Prepared release-notes draft: {path}")
    return PREPARED


if __name__ == "__main__":
    raise SystemExit(main())

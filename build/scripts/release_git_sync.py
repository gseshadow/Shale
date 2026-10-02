#!/usr/bin/env python3
"""Fail-closed Git synchronization for the release-and-publish workflow."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


RELEASE_FILES = (
    "pom.xml",
    "shale-core/pom.xml",
    "shale-data/pom.xml",
    "shale-ui/pom.xml",
    "shale-desktop/pom.xml",
    "shale-updater/pom.xml",
    "shale-server/pom.xml",
    "build/assets/shale-stable.json",
)


class GitFailure(RuntimeError):
    pass


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise GitFailure(f"git {' '.join(args)} failed: {detail}")
    return result


def branch_and_upstream(root: Path) -> tuple[str, str, str]:
    branch = git(root, "symbolic-ref", "--quiet", "--short", "HEAD", check=False)
    if branch.returncode:
        raise GitFailure("Release requires an attached branch; check out the release branch first.")
    upstream = git(root, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}", check=False)
    if upstream.returncode:
        raise GitFailure(
            f"Branch {branch.stdout.strip()} has no upstream. Configure one first, for example: "
            f"git push --set-upstream origin {branch.stdout.strip()}"
        )
    remote = git(root, "config", "--get", f"branch.{branch.stdout.strip()}.remote", check=False)
    if remote.returncode or not remote.stdout.strip():
        raise GitFailure(f"Could not resolve the configured upstream remote for {branch.stdout.strip()}.")
    return branch.stdout.strip(), upstream.stdout.strip(), remote.stdout.strip()


def dirty_paths(root: Path) -> list[str]:
    output = git(root, "status", "--porcelain=v1", "-z", "--untracked-files=all").stdout
    paths: list[str] = []
    records = output.split("\0")
    index = 0
    while index < len(records):
        record = records[index]
        index += 1
        if not record:
            continue
        path = record[3:]
        if record[:2] in {"R ", " R", "C ", " C"} and index < len(records):
            path = f"{path} -> {records[index]}"
            index += 1
        paths.append(path)
    return paths


def fetch_and_counts(root: Path, remote: str) -> tuple[int, int]:
    result = git(root, "fetch", "--prune", remote, check=False)
    if result.returncode:
        raise GitFailure(
            f"Could not fetch upstream remote {remote}; no release files were changed. "
            f"Check connectivity/authentication and retry. {result.stderr.strip()}"
        )
    counts = git(root, "rev-list", "--left-right", "--count", "HEAD...@{upstream}").stdout.split()
    return int(counts[0]), int(counts[1])


def preflight(root: Path) -> None:
    branch, upstream, remote = branch_and_upstream(root)
    changed = dirty_paths(root)
    if changed:
        listing = "\n".join(f"  {path}" for path in changed)
        raise GitFailure(
            "Release requires an unambiguous clean index and working tree. Commit or otherwise "
            f"resolve these files before retrying (Shale will not stash or discard them):\n{listing}"
        )
    ahead, behind = fetch_and_counts(root, remote)
    if behind:
        state = "diverged" if ahead else "behind"
        raise GitFailure(
            f"Branch {branch} is {state} relative to {upstream} (ahead {ahead}, behind {behind}). "
            "Reconcile it manually (for example, merge or rebase according to project policy), "
            "then rerun the release. Shale will not reset, rebase, or force-push."
        )
    print(f"Git preflight passed: {branch} tracks {upstream} (local commits ahead: {ahead}).")


def synchronize(root: Path, version: str) -> None:
    branch, upstream, remote = branch_and_upstream(root)
    ahead, behind = fetch_and_counts(root, remote)
    if behind:
        raise GitFailure(
            f"Upstream {upstream} advanced during the build (ahead {ahead}, behind {behind}). "
            "Generated release files are preserved. Reconcile the branch manually before publishing."
        )

    for relative in RELEASE_FILES:
        tracked = git(root, "ls-files", "--error-unmatch", "--", relative, check=False)
        if tracked.returncode:
            raise GitFailure(f"Required release metadata is not tracked: {relative}")
    notes_file = f"release-notes/{version}.json"
    release_files = (*RELEASE_FILES, notes_file) if (root / notes_file).is_file() else RELEASE_FILES
    git(root, "add", "--", *release_files)

    staged = git(root, "diff", "--cached", "--name-only").stdout.splitlines()
    unexpected = sorted(set(staged) - set(release_files))
    if unexpected:
        raise GitFailure("Refusing to commit unexpected staged paths: " + ", ".join(unexpected))

    if staged:
        result = git(root, "commit", "-m", f"Release Shale {version}", check=False)
        if result.returncode:
            raise GitFailure(
                "Release commit failed; generated files and the index are preserved for recovery. "
                + (result.stderr.strip() or result.stdout.strip())
            )
        print(f"Created release commit: Release Shale {version}")
    else:
        print("Release metadata has no staged changes; no duplicate or empty commit was created.")

    result = git(root, "push", check=False)
    if result.returncode:
        raise GitFailure(
            f"Release push to {upstream} failed; generated files and any release commit are preserved. "
            f"Fix authentication/connectivity or reconcile the remote, then run git push. "
            + (result.stderr.strip() or result.stdout.strip())
        )
    print(f"Git synchronization confirmed: {branch} pushed to {upstream}.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("preflight", "sync"))
    parser.add_argument("root", type=Path)
    parser.add_argument("version", nargs="?")
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            preflight(args.root.resolve())
        else:
            if not args.version:
                parser.error("sync requires a version")
            synchronize(args.root.resolve(), args.version)
        return 0
    except GitFailure as exc:
        print(f"Git release synchronization failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Safely prepare and clean the dedicated macOS release checkout."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


GENERATED_POMS = (
    "pom.xml",
    "shale-core/pom.xml",
    "shale-data/pom.xml",
    "shale-desktop/pom.xml",
    "shale-server/pom.xml",
    "shale-ui/pom.xml",
    "shale-updater/pom.xml",
)

GENERATED_OUTPUTS = ("build/tmp", "dist-macos")


class WorkspaceError(RuntimeError):
    pass


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if check and result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise WorkspaceError(f"git {' '.join(args)} failed: {detail}")
    return result


def workspace_changes(root: Path) -> list[tuple[str, str]]:
    output = git(root, "status", "--porcelain=v1", "-z", "--untracked-files=all").stdout
    records = output.split("\0")
    changes: list[tuple[str, str]] = []
    index = 0
    while index < len(records) and records[index]:
        record = records[index]
        if len(record) < 4:
            raise WorkspaceError(f"Unable to parse Git workspace status: {record!r}")
        status, path = record[:2], record[3:]
        changes.append((status, path))
        if "R" in status or "C" in status:
            index += 1
            if index >= len(records) or not records[index]:
                raise WorkspaceError("Unable to parse renamed/copied Git workspace path")
            changes.append((status, records[index]))
        index += 1
    return changes


def reject_unrelated_changes(root: Path) -> list[str]:
    changes = workspace_changes(root)
    allowed = set(GENERATED_POMS)
    unrelated = [(status, path) for status, path in changes if path not in allowed or status == "??"]
    if unrelated:
        details = "\n".join(f"  {status} {path}" for status, path in unrelated)
        raise WorkspaceError(
            "Mac release workspace contains changes outside the known generated POM files:\n"
            f"{details}\nResolve these changes before retrying; no unrelated changes were discarded."
        )
    return [path for _, path in changes]


def restore_generated_poms(root: Path) -> None:
    changed = reject_unrelated_changes(root)
    if changed:
        git(root, "restore", "--source=HEAD", "--staged", "--worktree", "--", *GENERATED_POMS)
    remaining = workspace_changes(root)
    if remaining:
        details = "\n".join(f"  {status} {path}" for status, path in remaining)
        raise WorkspaceError(f"Mac release workspace was not clean after POM restoration:\n{details}")


def cleanup_generated_outputs(root: Path) -> None:
    """Remove only disposable outputs; never follow parent symlinks or delete tracked paths."""
    root = root.resolve()
    if Path(git(root, "rev-parse", "--show-toplevel").stdout.strip()).resolve() != root:
        raise WorkspaceError("Mac release root must be the Git workspace root")
    # Check both HEAD and the index so staged deletions cannot hide tracked files.
    tracked = (
        git(root, "ls-files", "--cached", "-z", "--", *GENERATED_OUTPUTS).stdout
        + git(root, "ls-tree", "-r", "--name-only", "-z", "HEAD", "--", *GENERATED_OUTPUTS).stdout
    )
    if tracked:
        raise WorkspaceError("Refusing to clean generated output containing tracked paths: "
                             + ", ".join(sorted(set(tracked.rstrip("\0").split("\0")))))
    # Validate every destination before deleting anything. Do not resolve output
    # symlinks: unlink them, and let rmtree leave nested symlink targets alone.
    for relative in GENERATED_OUTPUTS:
        path = root / relative
        if any(parent.is_symlink() for parent in path.parents if parent != root and root in parent.parents):
            raise WorkspaceError(f"Refusing to clean generated output through a symlinked parent: {relative}")
    for relative in GENERATED_OUTPUTS:
        path = root / relative
        try:
            if path.is_symlink() or path.is_file():
                path.unlink()
            elif path.exists():
                shutil.rmtree(path)
        except OSError as error:
            raise WorkspaceError(f"Unable to clean generated output {relative}: {error}") from error


def cleanup_generated_poms(root: Path) -> None:
    """Restore only tracked generated POMs, without touching any other late changes."""
    changed_poms = [path for status, path in workspace_changes(root) if path in GENERATED_POMS and status != "??"]
    if changed_poms:
        git(root, "restore", "--source=HEAD", "--staged", "--worktree", "--", *GENERATED_POMS)


def prepare(root: Path, remote: str, revision: str) -> None:
    cleanup_generated_outputs(root)
    restore_generated_poms(root)
    git(root, "fetch", remote)
    if git(root, "cat-file", "-e", f"{revision}^{{commit}}", check=False).returncode:
        raise WorkspaceError(f"Requested source revision is unavailable after fetching {remote}: {revision}")
    git(root, "checkout", "--detach", revision)
    head = git(root, "rev-parse", "HEAD").stdout.strip()
    requested = git(root, "rev-parse", f"{revision}^{{commit}}").stdout.strip()
    if head != requested:
        raise WorkspaceError(f"Mac release checkout mismatch: requested {requested}, HEAD is {head}")
    print(f"Mac release workspace HEAD verified: {head}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    subparsers = parser.add_subparsers(dest="command", required=True)
    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument("--remote", required=True)
    prepare_parser.add_argument("--revision", required=True)
    subparsers.add_parser("cleanup")
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            prepare(args.root.resolve(), args.remote, args.revision)
        else:
            cleanup_generated_poms(args.root.resolve())
        return 0
    except WorkspaceError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

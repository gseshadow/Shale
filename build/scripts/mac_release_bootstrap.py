#!/usr/bin/env python3
"""Run the Mac release entrypoint directly from an exact fetched commit."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys


RELEASE_SCRIPT = "build/scripts/prepare-shale-mac-release.sh"
REVISION_PATTERN = re.compile(r"[0-9a-fA-F]{40}")

REMOTE_BOOTSTRAP = r'''set -eu
repo=$1
remote=$2
branch=$3
version=$4
revision=$5
release_script=$6

cd "$repo"
git fetch --prune "$remote"
git cat-file -e "$revision^{commit}"
expected_blob=$(git rev-parse "$revision:$release_script")
bootstrap_script=$(mktemp "${TMPDIR:-/tmp}/shale-mac-release.XXXXXX")
trap 'rm -f "$bootstrap_script"' EXIT HUP INT TERM
git show "$revision:$release_script" > "$bootstrap_script"
actual_blob=$(git hash-object "$bootstrap_script")
if [ "$actual_blob" != "$expected_blob" ]; then
  echo "Fetched release script content did not match requested revision $revision" >&2
  exit 1
fi
chmod 700 "$bootstrap_script"
echo "Verified Mac release entrypoint from requested revision: $revision ($expected_blob)"
SHALE_RELEASE_ROOT="$repo" "$bootstrap_script" "$branch" "$version" "$revision"
'''


def run_remote_bootstrap(
    ssh_target: str,
    repo: str,
    remote: str,
    branch: str,
    version: str,
    revision: str,
) -> None:
    if REVISION_PATTERN.fullmatch(revision) is None:
        raise ValueError("source revision must be a full 40-character Git SHA")
    subprocess.run(
        [
            "ssh", ssh_target, "bash", "-s", "--",
            repo, remote, branch, version, revision, RELEASE_SCRIPT,
        ],
        input=REMOTE_BOOTSTRAP,
        text=True,
        check=True,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ssh_target")
    parser.add_argument("repo")
    parser.add_argument("remote")
    parser.add_argument("branch")
    parser.add_argument("version")
    parser.add_argument("revision")
    args = parser.parse_args(argv)
    try:
        run_remote_bootstrap(
            args.ssh_target, args.repo, args.remote, args.branch,
            args.version, args.revision,
        )
    except (ValueError, subprocess.CalledProcessError) as exc:
        print(f"Mac release bootstrap failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

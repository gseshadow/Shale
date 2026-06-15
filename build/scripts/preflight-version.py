#!/usr/bin/env python3
"""Validate Shale release POM versions before building artifacts."""
from __future__ import annotations

import re
import sys
from pathlib import Path

PARENT_RE = re.compile(
    r"<parent>.*?<groupId>\s*com\.shale\s*</groupId>.*?"
    r"<artifactId>\s*shale-parent\s*</artifactId>.*?"
    r"<version>\s*([^<\s]+)\s*</version>.*?</parent>",
    re.DOTALL,
)
ROOT_VERSION_RE = re.compile(
    r"<artifactId>\s*shale-parent\s*</artifactId>\s*<version>\s*([^<\s]+)\s*</version>",
    re.DOTALL,
)


def line_number(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def rel(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def main() -> int:
    if len(sys.argv) not in (2, 3, 4):
        print("Usage: preflight-version.py <repo-root> [previous-version] [--print-root-version]", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    print_root_version = "--print-root-version" in sys.argv[2:]
    previous_version = next((arg for arg in sys.argv[2:] if arg and arg != "--print-root-version"), None)
    root_pom = root / "pom.xml"
    poms = [root_pom, *sorted(root.glob("shale-*/pom.xml"))]
    failures: list[str] = []

    root_text = root_pom.read_text(encoding="utf-8")
    root_match = ROOT_VERSION_RE.search(root_text)
    if not root_match:
        failures.append(f"{rel(root_pom, root)}: unable to find root shale-parent version")
        root_version = None
    else:
        root_version = root_match.group(1)

    if print_root_version:
        if root_version is None:
            print("Unable to find root shale-parent version", file=sys.stderr)
            return 1
        print(root_version)
        return 0

    for pom in poms:
        text = pom.read_text(encoding="utf-8")
        if previous_version:
            for match in re.finditer(re.escape(previous_version), text):
                failures.append(
                    f"{rel(pom, root)}:{line_number(text, match.start())}: contains previous version {previous_version}"
                )

        if pom == root_pom or root_version is None:
            continue

        parent_match = PARENT_RE.search(text)
        if not parent_match:
            failures.append(f"{rel(pom, root)}: unable to find shale-parent parent version")
            continue

        parent_version = parent_match.group(1)
        if parent_version != root_version:
            failures.append(
                f"{rel(pom, root)}:{line_number(text, parent_match.start(1))}: "
                f"parent version {parent_version} does not match root pom version {root_version}"
            )

    if failures:
        print("Release version preflight failed:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1

    print(f"Release version preflight passed for root version {root_version}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

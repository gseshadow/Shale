#!/usr/bin/env python3
"""Select a jpackage resource from an extracted JDK module image."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path, PurePosixPath
from typing import Iterable, TextIO


WINDOWS_MSI_MAIN_RESOURCE = PurePosixPath(
    "jdk.jpackage/jdk/jpackage/internal/resources/main.wxs"
)


def normalized_resource_path(path: str | Path) -> PurePosixPath:
    return PurePosixPath(str(path).replace("\\", "/").lstrip("./"))


def select_windows_msi_main(
    candidates: Iterable[str | Path], diagnostic: TextIO = sys.stderr
) -> PurePosixPath:
    discovered = [normalized_resource_path(candidate) for candidate in candidates]
    print("jpackage main.wxs candidates:", file=diagnostic)
    if discovered:
        for candidate in discovered:
            print(f"candidate={candidate.as_posix()}", file=diagnostic)
    else:
        print("candidate=<none>", file=diagnostic)

    matches = [candidate for candidate in discovered if candidate == WINDOWS_MSI_MAIN_RESOURCE]
    if len(matches) != 1:
        print(
            "classification=windows_msi_main_template_cardinality "
            f"expected_resource={WINDOWS_MSI_MAIN_RESOURCE.as_posix()} "
            f"expected=1 found={len(matches)} discovered={len(discovered)}",
            file=diagnostic,
        )
        raise ValueError("authoritative Windows MSI main.wxs resource cardinality failure")
    return matches[0]


def discover_main_templates(extracted_modules: Path) -> list[PurePosixPath]:
    return sorted(
        (PurePosixPath(path.relative_to(extracted_modules).as_posix())
         for path in extracted_modules.rglob("main.wxs") if path.is_file()),
        key=lambda path: path.as_posix(),
    )


def extract_selected_template(extracted_modules: Path, destination: Path) -> None:
    candidates = discover_main_templates(extracted_modules)
    selected = select_windows_msi_main(candidates)
    source = extracted_modules.joinpath(*selected.parts)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    print(f"selected={selected.as_posix()}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("extracted_modules", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        extract_selected_template(args.extracted_modules, args.destination)
    except (OSError, ValueError) as exc:
        print(f"jpackage resource selection failed: {exc}", file=sys.stderr)
        return 46
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

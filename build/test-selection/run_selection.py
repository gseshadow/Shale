#!/usr/bin/env python3
"""Validate and execute structured Maven arguments from a selector plan."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from typing import Iterable, Sequence


WINDOWS_SAFE_COMMAND_LENGTH = 7000
MAVEN_VALUE_OPTIONS = {"-pl", "--projects", "-f", "--file", "-s", "--settings",
                       "-gs", "--global-settings", "-t", "--toolchains", "-T", "--threads"}
CASE_TIMELINE_TESTS = {
    "com.shale.data.dao.CaseLifecycleAuditContractTest",
    "com.shale.data.dao.CaseTimelineCoverageContractTest",
    "com.shale.data.dao.CaseTimelineWriterTest",
    "com.shale.ui.controller.CaseDetailsTimelineCoverageTest",
    "com.shale.ui.controller.CaseTimelineDescriptionTest",
}


def resolve_maven() -> str:
    candidates = ("mvn.cmd", "mvn") if os.name == "nt" else ("mvn", "mvn.cmd")
    for candidate in candidates:
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    raise FileNotFoundError("Unable to find Maven launcher (mvn.cmd or mvn) on PATH.")


def display_command(argv: Iterable[str]) -> str:
    """Diagnostic only; this string is never executed."""
    return subprocess.list2cmdline(list(argv))


def validate_maven_args(arguments: list[str]) -> None:
    if not arguments or arguments[-1] != "test":
        raise ValueError("Affected Maven arguments must end with the test goal.")
    for index, argument in enumerate(arguments):
        if not isinstance(argument, str) or not argument:
            raise ValueError(f"Maven argument {index} must be a nonempty string.")
        if argument in MAVEN_VALUE_OPTIONS:
            if index + 1 >= len(arguments) or not arguments[index + 1] or arguments[index + 1].startswith("-"):
                raise ValueError(f"Maven option {argument} requires a nonempty following value.")
    if len(display_command(["mvn.cmd", *arguments])) > WINDOWS_SAFE_COMMAND_LENGTH:
        raise ValueError("Affected Maven command exceeds the Windows-safe command length limit.")


def affected_batches(plan: dict) -> list[list[str]]:
    batches = plan.get("selected_maven_batches")
    if not isinstance(batches, list):
        raise ValueError("Selection plan must contain selected_maven_batches.")
    for batch in batches:
        if not isinstance(batch, list):
            raise ValueError("Each selected Maven batch must be an argument array.")
        validate_maven_args(batch)

    if plan.get("focused_change_set") == "case-timeline":
        selected = set(plan.get("test_patterns", []))
        batch_tests = {
            test_class
            for batch in batches
            for argument in batch
            if argument.startswith("-Dtest=")
            for test_class in argument.removeprefix("-Dtest=").split(",")
        }
        if (selected != CASE_TIMELINE_TESTS or batch_tests != CASE_TIMELINE_TESTS
                or len(plan.get("test_patterns", [])) != len(CASE_TIMELINE_TESTS)):
            raise ValueError("Case Timeline affected selection must contain exactly its five justified tests.")
    return batches


def execute_affected(plan: dict, launcher: str | Sequence[str] | None = None) -> None:
    launcher_prefix = ([resolve_maven()] if launcher is None
                       else [launcher] if isinstance(launcher, str)
                       else list(launcher))
    if not launcher_prefix or any(not argument for argument in launcher_prefix):
        raise ValueError("Launcher command prefix must contain nonempty arguments.")
    for arguments in affected_batches(plan):
        argv = [*launcher_prefix, *arguments]
        print(f"Display only: {display_command(argv)}", flush=True)
        for index, argument in enumerate(argv):
            print(f"argv[{index}] = {argument}", flush=True)
        subprocess.run(argv, shell=False, check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plan", type=Path)
    parser.add_argument("--command", choices=("affected",), required=True)
    args = parser.parse_args(argv)
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    try:
        execute_affected(plan)
    except (FileNotFoundError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Selection runner failed: {error}", file=sys.stderr)
        return error.returncode if isinstance(error, subprocess.CalledProcessError) else 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

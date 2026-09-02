#!/usr/bin/env python3
"""Select Shale tests from changed paths or an explicit feature area."""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = Path(__file__).with_name("test-areas.json")


def load_config() -> dict:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def matches(path: str, patterns: Iterable[str]) -> bool:
    path = path.replace("\\", "/").lstrip("./")
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def changed_paths(base: str, head: str) -> list[str]:
    command = ["git", "diff", "--name-only", "--diff-filter=ACMR", base, head, "--"]
    completed = subprocess.run(command, cwd=ROOT, check=True, text=True, encoding="utf-8", capture_output=True)
    return sorted({line.strip().replace("\\", "/") for line in completed.stdout.splitlines() if line.strip()})


def test_class_for_path(path: str) -> str | None:
    marker = "/src/test/java/"
    normalized = "/" + path.replace("\\", "/").lstrip("/")
    if marker not in normalized or not normalized.endswith(".java"):
        return None
    relative = normalized.split(marker, 1)[1][:-5]
    return relative.replace("/", ".")


def module_for_path(path: str, modules: Iterable[str]) -> str | None:
    first = path.replace("\\", "/").split("/", 1)[0]
    return first if first in modules else None


def select(paths: list[str], explicit_areas: list[str] | None = None) -> dict:
    config = load_config()
    area_config = config["areas"]
    unknown_areas = sorted(set(explicit_areas or []) - set(area_config))
    if unknown_areas:
        raise ValueError(f"Unknown test area(s): {', '.join(unknown_areas)}")

    selected: set[str] = set(explicit_areas or [])
    reasons: dict[str, list[str]] = {area: ["Explicit feature selection."] for area in selected}
    modified_tests: set[str] = set()
    modified_test_modules: set[str] = set()
    affected_modules: set[str] = set()
    unknown_production: list[str] = []
    escalation_reasons: list[str] = []

    for path in paths:
        module = module_for_path(path, config["modules"])
        if module and "/src/main/" in "/" + path:
            affected_modules.add(module)

        test_class = test_class_for_path(path)
        if test_class:
            modified_tests.add(test_class)
            if module:
                affected_modules.add(module)
                modified_test_modules.add(module)

        if matches(path, config["full_suite_patterns"]):
            escalation_reasons.append(f"{path}: parent build, module graph, selector, workflow, or Codex test infrastructure")
            continue

        path_areas: set[str] = set()
        presentation_resource = path.startswith("shale-ui/src/main/resources/") and path.endswith((".css", ".fxml"))
        for area, definition in area_config.items():
            if presentation_resource and area not in {"ui-presentation", "ui-fxml-structure"}:
                continue
            if matches(path, definition.get("paths", [])):
                path_areas.add(area)
                reasons.setdefault(area, []).append(f"{path}: {definition['reason']}")
        for shared in config.get("shared_paths", []):
            if matches(path, shared["patterns"]):
                for area in shared["areas"]:
                    path_areas.add(area)
                    reasons.setdefault(area, []).append(f"{path}: {shared['reason']}")
        selected.update(path_areas)

        production = "/src/main/" in "/" + path or path.startswith("build/") or path.startswith("shale-web/")
        if production and not path_areas and not matches(path, config["full_suite_patterns"]):
            unknown_production.append(path)

    if unknown_production:
        escalation_reasons.extend(f"{path}: unknown production path" for path in unknown_production)

    full_suite = bool(escalation_reasons)
    selected_definitions = [area_config[name] for name in sorted(selected)]
    modules = sorted(affected_modules | {module for item in selected_definitions for module in item.get("modules", [])})
    patterns = sorted({pattern for item in selected_definitions for pattern in item.get("test_patterns", [])} | modified_tests)
    python_tests = sorted({command for item in selected_definitions for command in item.get("python_tests", [])})

    focused_command = ""
    if modified_tests:
        focused_command = " ".join([
            "mvn", "-pl", ",".join(sorted(modified_test_modules)), "-am",
            f"-Dtest={','.join(sorted(modified_tests))}",
            "-Dsurefire.failIfNoSpecifiedTests=false", "test"
        ])

    if "ui-visual-advisory" in selected:
        selected_command = "mvn -Pui-visual test"
    elif patterns:
        module_args = ["-pl", ",".join(modules), "-am"] if modules else []
        selected_command = " ".join([
            "mvn", *module_args, f"-Dtest={','.join(patterns)}",
            "-Dsurefire.failIfNoSpecifiedTests=false", "test"
        ])
    else:
        selected_command = ""
    informational_command = "mvn -Pall-tests test" if full_suite else ""

    return {
        "changed_paths": sorted(paths),
        "selected_areas": sorted(selected),
        "selected_modules": modules,
        "test_patterns": patterns,
        "modified_test_classes": sorted(modified_tests),
        "python_commands": python_tests,
        "critical_command": "mvn test",
        "focused_command": focused_command,
        "selected_command": selected_command,
        "informational_command": informational_command,
        "commands": [command for command in [*python_tests, focused_command, selected_command, "mvn test"] if command],
        "full_suite": full_suite,
        "escalation_reasons": escalation_reasons,
        "reasons": {area: reasons.get(area, [area_config[area]["reason"]]) for area in sorted(selected)},
        "skipped_areas": {
            area: "No changed path mapped to this area."
            for area in sorted(area_config) if area not in selected
        },
    }


def markdown(result: dict) -> str:
    lines = ["## Shale relevant test selection", "", "### Changed paths"]
    lines.extend(f"- `{path}`" for path in result["changed_paths"] or ["(none)"])
    lines.extend(["", "### Selected areas and reasons"])
    if result["selected_areas"]:
        for area in result["selected_areas"]:
            lines.append(f"- **{area}**")
            lines.extend(f"  - {reason}" for reason in result["reasons"][area])
    else:
        lines.append("- None; the change is documentation-only or has no Maven test contract.")
    lines.extend([
        "", f"**Full-suite escalation:** {'yes' if result['full_suite'] else 'no'}",
        f"**Selected modules:** {', '.join(result['selected_modules']) or 'none'}",
        f"**Modified test classes:** {', '.join(result['modified_test_classes']) or 'none'}",
        "", "### Commands",
    ])
    lines.extend(f"- `{command}`" for command in result["commands"] or ["(no Maven tests)"])
    if result["escalation_reasons"]:
        lines.extend(["", "### Informational full-suite escalation", "", f"- `{result['informational_command']}`",
                      "- This historical-suite result is non-blocking and does not replace the focused gate.",
                      "", "### Escalation reasons"])
        lines.extend(f"- {reason}" for reason in result["escalation_reasons"])
    lines.extend(["", "### Skipped areas"])
    lines.extend(f"- **{area}:** {reason}" for area, reason in result["skipped_areas"].items())
    return "\n".join(lines) + "\n"


def run_commands(result: dict) -> int:
    for command in result["commands"]:
        print(f"+ {command}", flush=True)
        completed = subprocess.run(command, cwd=ROOT, shell=True)
        if completed.returncode:
            return completed.returncode
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Base Git revision for change selection")
    parser.add_argument("--head", default="HEAD", help="Head Git revision (default: HEAD)")
    parser.add_argument("--path", action="append", default=[], help="Synthetic changed path; repeatable")
    parser.add_argument("--area", action="append", default=[], help="Explicit feature area; repeatable")
    parser.add_argument("--format", choices=("json", "markdown"), default="json")
    parser.add_argument("--output", type=Path, help="Also write the selected format to this file")
    parser.add_argument("--github-output", type=Path, help="Write CI step outputs")
    parser.add_argument("--run", action="store_true", help="Execute the emitted commands in order")
    args = parser.parse_args(argv)

    if args.path and args.base:
        parser.error("Use either --path or --base/--head, not both")
    if not args.path and not args.base and not args.area:
        parser.error("Provide --base, one or more --path values, or one or more --area values")
    try:
        paths = sorted(set(args.path)) if args.path else (changed_paths(args.base, args.head) if args.base else [])
        result = select(paths, args.area)
    except (subprocess.CalledProcessError, ValueError) as error:
        parser.error(str(error))

    rendered = json.dumps(result, indent=2) + "\n" if args.format == "json" else markdown(result)
    print(rendered, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"full_suite={str(result['full_suite']).lower()}\n")
            output.write(f"selected_command={result['selected_command']}\n")
            output.write(f"informational_command={result['informational_command']}\n")
            output.write(f"python_commands={json.dumps(result['python_commands'])}\n")
    return run_commands(result) if args.run else 0


if __name__ == "__main__":
    raise SystemExit(main())

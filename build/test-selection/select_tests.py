#!/usr/bin/env python3
"""Select Shale tests from changed paths or an explicit feature area."""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = Path(__file__).with_name("test-areas.json")
CRITICAL_PATH = Path(__file__).with_name("critical-tests.txt")
WINDOWS_SAFE_COMMAND_LENGTH = 7000
OWNERSHIP_ADVISORY_THRESHOLD = 50
POM_TEST_SELECTION_MARKERS = (
    "shale.test.includesFile", "shale.test.excludesFile", "maven-surefire-plugin",
    "surefire.version", "all-tests.txt", "ui-visual-advisory-tests.txt",
)


def display_command(arguments: Iterable[str]) -> str:
    """Return diagnostics only; execution always receives the original argument list."""
    return " ".join(arguments)


def configured_command_arguments(command: str) -> list[str]:
    """Parse repository-owned simple commands without invoking a command interpreter."""
    return shlex.split(command, posix=os.name != "nt")


def test_catalog() -> dict[str, str]:
    catalog: dict[str, str] = {}
    for path in ROOT.glob("shale-*/src/test/java/**/*Test.java"):
        qualified = path.as_posix().split("/src/test/java/", 1)[1][:-5].replace("/", ".")
        catalog[qualified] = path.relative_to(ROOT).parts[0]
    return catalog


def critical_classes() -> set[str]:
    return {line.strip() for line in CRITICAL_PATH.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")}


def expand_test_patterns(patterns: Iterable[str], catalog: dict[str, str]) -> set[str]:
    expanded: set[str] = set()
    for pattern in patterns:
        matches_for_pattern = {
            qualified for qualified in catalog
            if fnmatch.fnmatchcase(qualified, pattern)
            or fnmatch.fnmatchcase(qualified.rsplit(".", 1)[-1], pattern)
        }
        if matches_for_pattern:
            expanded.update(matches_for_pattern)
        elif not any(character in pattern for character in "*?["):
            expanded.add(pattern)
    return expanded


def maven_batches(classes: list[str], modules: list[str], catalog: dict[str, str]) -> list[list[str]]:
    if not classes:
        return []
    combined = ["-pl", ",".join(modules), "-am", f"-Dtest={','.join(classes)}",
                "-Dsurefire.failIfNoSpecifiedTests=false", "test"] if modules else [
                f"-Dtest={','.join(classes)}", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    if len(display_command(["mvn", *combined])) <= WINDOWS_SAFE_COMMAND_LENGTH:
        return [combined]

    batches: list[list[str]] = []
    by_module: dict[str, list[str]] = {}
    for qualified in classes:
        by_module.setdefault(catalog.get(qualified, modules[0] if modules else ""), []).append(qualified)
    for module in sorted(by_module):
        current: list[str] = []
        for qualified in by_module[module]:
            candidate = [*current, qualified]
            args = ["-pl", module, "-am", f"-Dtest={','.join(candidate)}",
                    "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
            if current and len(display_command(["mvn", *args])) > WINDOWS_SAFE_COMMAND_LENGTH:
                batches.append(["-pl", module, "-am", f"-Dtest={','.join(current)}",
                                "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
                current = [qualified]
            else:
                current = candidate
        if current:
            batches.append(["-pl", module, "-am", f"-Dtest={','.join(current)}",
                            "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
    if any(len(display_command(["mvn", *args])) > WINDOWS_SAFE_COMMAND_LENGTH for args in batches):
        raise ValueError("A single selected test class exceeds the Windows-safe Maven command limit.")
    return batches


def load_config() -> dict:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def matches(path: str, patterns: Iterable[str]) -> bool:
    path = path.replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def changed_paths(base: str, head: str) -> list[str]:
    command = ["git", "diff", "--name-only", "--diff-filter=ACMR", base, head, "--"]
    completed = subprocess.run(command, cwd=ROOT, check=True, text=True, encoding="utf-8", capture_output=True)
    return sorted({line.strip().replace("\\", "/") for line in completed.stdout.splitlines() if line.strip()})


def selector_tests_required(paths: list[str], base: str | None = None, head: str = "HEAD") -> bool:
    if any(path.startswith("build/test-selection/") for path in paths):
        return True
    pom_paths = [path for path in paths if path == "pom.xml" or path.endswith("/pom.xml")]
    if not pom_paths or not base:
        return False
    completed = subprocess.run(
        ["git", "diff", "--unified=0", base, head, "--", *pom_paths],
        cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8",
    )
    changed_lines = "\n".join(line for line in completed.stdout.splitlines()
                              if line.startswith(("+", "-")) and not line.startswith(("+++", "---")))
    return any(marker in changed_lines for marker in POM_TEST_SELECTION_MARKERS)


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
    catalog = test_catalog()
    area_config = config["areas"]
    unknown_areas = sorted(set(explicit_areas or []) - set(area_config))
    if unknown_areas:
        raise ValueError(f"Unknown test area(s): {', '.join(unknown_areas)}")

    selected: set[str] = set(explicit_areas or [])
    reasons: dict[str, list[str]] = {area: ["Explicit feature selection."] for area in selected}
    modified_tests: set[str] = set()
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
    configured_modules = ({module for item in selected_definitions for module in item.get("modules", [])}
                          if explicit_areas else set())
    area_pattern_key = "ownership_patterns" if explicit_areas else "blocking_smoke_patterns"
    configured_patterns = {pattern for item in selected_definitions
                           for pattern in item.get(area_pattern_key, [])} | modified_tests
    narrow_mappings: dict[str, list[str]] = {}
    if not explicit_areas:
        for mapping in config.get("file_test_mappings", []):
            matching = [path for path in paths if matches(path, mapping.get("paths", []))]
            if matching:
                for pattern in mapping.get("test_classes", []):
                    configured_patterns.add(pattern)
                    narrow_mappings.setdefault(pattern, []).extend(matching)
    patterns = sorted(expand_test_patterns(configured_patterns, catalog))
    modules = sorted(affected_modules | configured_modules
                     | {catalog[qualified] for qualified in patterns if qualified in catalog})
    critical = critical_classes()
    test_reasons: dict[str, dict] = {}
    for qualified in patterns:
        mapping_rules: list[str] = []
        selecting_paths: set[str] = set()
        classifications: set[str] = set()
        if qualified in modified_tests:
            mapping_rules.append("directly-modified-test")
            selecting_paths.update(path for path in paths if test_class_for_path(path) == qualified)
            classifications.add("directly_modified")
        if qualified in narrow_mappings:
            mapping_rules.append("explicit-file-mapping")
            selecting_paths.update(narrow_mappings[qualified])
            classifications.add("affected_behavior")
        for area in sorted(selected):
            area_patterns = area_config[area].get(area_pattern_key, [])
            if any(fnmatch.fnmatchcase(qualified, pattern)
                   or fnmatch.fnmatchcase(qualified.rsplit(".", 1)[-1], pattern) for pattern in area_patterns):
                mapping_rules.append(f"area:{area}")
                selecting_paths.update(path for path in paths if matches(path, area_config[area].get("paths", [])))
                if explicit_areas:
                    selecting_paths.add("(explicit area selection)")
                    classifications.add("manual_inventory")
                else:
                    classifications.add("blocking_smoke")
        if qualified in critical:
            classifications.add("critical")
        test_reasons[qualified] = {
            "changed_paths": sorted(selecting_paths),
            "mapping_rules": mapping_rules,
            "classifications": sorted(classifications),
        }
    unjustified = [qualified for qualified, reason in test_reasons.items()
                   if not reason["changed_paths"] or not reason["mapping_rules"] or not reason["classifications"]]
    if unjustified:
        raise ValueError("Selected tests lack traceable reasons: " + ", ".join(unjustified))
    python_tests = sorted({command for item in selected_definitions for command in item.get("python_tests", [])})
    python_command_args = [configured_command_arguments(command) for command in python_tests]

    selected_maven_args: list[str] = []
    selected_maven_batches: list[list[str]] = []
    selection_error = ""
    if "ui-visual-advisory" in selected:
        selected_command = "mvn -Pui-visual test"
        selected_maven_args = ["-Pui-visual", "test"]
        selected_maven_batches = [selected_maven_args]
    elif patterns and not selection_error:
        selected_maven_batches = maven_batches(patterns, modules, catalog)
        selected_maven_args = selected_maven_batches[0] if len(selected_maven_batches) == 1 else []
        selected_command = " ; ".join(display_command(["mvn", *args]) for args in selected_maven_batches)
    else:
        selected_command = ""
    informational_maven_args = ["-Pall-tests", "test"] if full_suite else []
    informational_command = display_command(["mvn", *informational_maven_args]) if informational_maven_args else ""

    return {
        "changed_paths": sorted(paths),
        "selected_areas": sorted(selected),
        "selected_modules": modules,
        "test_patterns": patterns,
        "ownership_class_count": len(expand_test_patterns({pattern for item in selected_definitions for pattern in item.get("ownership_patterns", [])}, catalog)),
        "ownership_advisory_command": " ".join(["python", "build/test-selection/select_tests.py", *[part for area in sorted(selected) for part in ("--area", area)], "--run"]) if selected and not explicit_areas else "",
        "test_reasons": test_reasons,
        "modified_test_classes": sorted(modified_tests),
        "python_commands": python_tests,
        "python_command_args": python_command_args,
        "critical_command": "mvn test",
        "critical_maven_args": ["test"],
        "selected_command": selected_command,
        "selected_maven_args": selected_maven_args,
        "selected_maven_batches": selected_maven_batches,
        "maximum_command_length": max((len(display_command(["mvn", *args])) for args in selected_maven_batches), default=0),
        "windows_safe_command_length": WINDOWS_SAFE_COMMAND_LENGTH,
        "ownership_advisory_threshold": OWNERSHIP_ADVISORY_THRESHOLD,
        "selection_error": selection_error,
        "informational_command": informational_command,
        "informational_maven_args": informational_maven_args,
        "commands": [command for command in [*python_tests, selected_command, "mvn test"] if command],
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
        f"**Maximum generated command length:** {result['maximum_command_length']} / {result['windows_safe_command_length']}",
        f"**Complete owned classes (manual/advisory):** {result['ownership_class_count']}",
        "", "### Selected test reasons",
    ])
    if result["test_reasons"]:
        for qualified, reason in result["test_reasons"].items():
            lines.append(f"- `{qualified}`")
            lines.append(f"  - Classification: {', '.join(reason['classifications'])}")
            lines.append(f"  - Mapping: {', '.join(reason['mapping_rules'])}")
            lines.extend(f"  - Changed path: `{path}`" for path in reason["changed_paths"])
    else:
        lines.append("- None")
    lines.extend(["", "### Commands"])
    lines.extend(f"- `{command}`" for command in result["commands"] or ["(no Maven tests)"])
    if result["selection_error"]:
        lines.extend(["", "### Selector policy error", "", f"- {result['selection_error']}"])
    if result["ownership_advisory_command"]:
        lines.extend(["", "### Broader ownership coverage", "",
                      f"- `{result['ownership_advisory_command']}`",
                      "- Complete area ownership is optional local/advisory coverage."])
    if result["escalation_reasons"]:
        lines.extend(["", "### Informational full-suite escalation", "", f"- `{result['informational_command']}`",
                      "- This historical-suite result is optional and does not replace focused local checks.",
                      "", "### Escalation reasons"])
        lines.extend(f"- {reason}" for reason in result["escalation_reasons"])
    lines.extend(["", "### Skipped areas"])
    lines.extend(f"- **{area}:** {reason}" for area, reason in result["skipped_areas"].items())
    return "\n".join(lines) + "\n"


def run_commands(result: dict) -> int:
    command_specs = [
        *[(display_command(arguments), arguments) for arguments in result["python_command_args"]],
        *[(display_command(["mvn", *args]), ["mvn", *args]) for args in result["selected_maven_batches"]],
        (result["critical_command"], ["mvn", *result["critical_maven_args"]]),
    ]
    for display, arguments in command_specs:
        print(f"+ {display}", flush=True)
        completed = subprocess.run(arguments, cwd=ROOT, shell=False)
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
    parser.add_argument("--plan-output", type=Path, help="Write the authoritative JSON execution plan")
    parser.add_argument("--run", action="store_true", help="Execute the emitted commands in order")
    args = parser.parse_args(argv)

    if args.path and args.base:
        parser.error("Use either --path or --base/--head, not both")
    if not args.path and not args.base and not args.area:
        parser.error("Provide --base, one or more --path values, or one or more --area values")
    try:
        paths = sorted(set(args.path)) if args.path else (changed_paths(args.base, args.head) if args.base else [])
        result = select(paths, args.area)
        result["selector_tests_required"] = selector_tests_required(paths, args.base, args.head)
    except (subprocess.CalledProcessError, ValueError) as error:
        parser.error(str(error))

    rendered = json.dumps(result, indent=2) + "\n" if args.format == "json" else markdown(result)
    print(rendered, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    if args.plan_output:
        args.plan_output.parent.mkdir(parents=True, exist_ok=True)
        args.plan_output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if result["selection_error"]:
        print(f"Selector policy error: {result['selection_error']}", file=sys.stderr)
        return 2
    return run_commands(result) if args.run else 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Replay jpackage's authoritative WiX definitions for main.wxs recompilation."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


DEFINITION = re.compile(r"^-d(Jp[A-Za-z0-9_]+)=(.*)$", re.DOTALL)
CORE_DEFINITIONS = (
    "JpAppVersion",
    "JpProductCode",
    "JpProductUpgradeCode",
    "JpAppName",
    "JpConfigDir",
)


def _unquote(argument: str) -> str:
    argument = argument.strip()
    if len(argument) >= 2 and argument[0] == argument[-1] == '"':
        return argument[1:-1]
    return argument


def _jpackage_command_arguments(log: str) -> list[list[str]]:
    """Return ProcessBuilder-style argument lists printed by verbose jpackage."""
    commands: list[list[str]] = []
    for line in log.splitlines():
        if "candle.exe" not in line.lower() or "main.wxs" not in line.lower():
            continue
        end = line.rfind("]")
        start = line.rfind("[", 0, end)
        if start < 0 or end <= start:
            continue
        # jpackage logs ProcessBuilder's List<String>: each comma-space boundary
        # is an argument boundary, while spaces inside an argument are retained.
        commands.append([_unquote(argument) for argument in line[start + 1:end].split(", ")])
    return commands


def _is_named_file(argument: str, filename: str) -> bool:
    normalized = _unquote(argument).replace("\\", "/").rstrip("/")
    return normalized.rsplit("/", 1)[-1].lower() == filename.lower()


def definitions(log: str) -> dict[str, str]:
    """Recover only definitions on jpackage's generated-main Candle command."""
    candidates = []
    for arguments in _jpackage_command_arguments(log):
        if (any(_is_named_file(argument, "candle.exe") for argument in arguments)
                and any(_is_named_file(argument, "main.wxs") for argument in arguments)):
            candidates.append(arguments)

    if not candidates:
        raise ValueError(
            "original jpackage candle.exe invocation for generated main.wxs was not found or parseable"
        )
    if len(candidates) != 1:
        raise ValueError(
            f"expected one original jpackage candle.exe invocation for generated main.wxs; found {len(candidates)}"
        )

    recovered: dict[str, str] = {}
    for argument in candidates[0]:
        match = DEFINITION.fullmatch(argument)
        if not match:
            continue
        name, value = match.groups()
        if name in recovered:
            raise ValueError(f"duplicate jpackage WiX definition in main.wxs invocation: {name}")
        recovered[name] = value

    missing = [name for name in CORE_DEFINITIONS if name not in recovered]
    if missing:
        raise ValueError(
            "original jpackage main.wxs candle invocation is missing core definition(s): "
            + ", ".join(missing)
        )
    return recovered


def quote_response_argument(argument: str) -> str:
    """Quote one Candle response-file argument, including paths with spaces."""
    rendered = subprocess.list2cmdline([argument])
    return rendered if rendered.startswith('"') else f'"{rendered}"'


def prepare(log_path: Path, response_path: Path) -> None:
    values = definitions(log_path.read_text(encoding="utf-8", errors="replace"))
    response_path.write_text(
        "\n".join(quote_response_argument(f"-d{name}={value}") for name, value in values.items()) + "\n",
        encoding="utf-8",
    )
    print(f"Recovered {len(values)} jpackage main.wxs WiX definition(s): {', '.join(values)}")


def main(argv: list[str]) -> int:
    if len(argv) != 3 or argv[0] != "prepare":
        print("Usage: windows_jpackage_wix_definitions.py prepare <jpackage.log> <output.rsp>",
              file=sys.stderr)
        return 2
    try:
        prepare(Path(argv[1]), Path(argv[2]))
    except (OSError, ValueError) as exc:
        print(f"jpackage WiX definition recovery failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

"""Replay jpackage's authoritative WiX definitions for main.wxs recompilation."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


DEFINITION = re.compile(r"^-d(Jp[A-Za-z0-9_]+)=(.*)$", re.DOTALL)
DEFINITION_START = re.compile(r"(?<!\S)-d(Jp[A-Za-z0-9_]+)=")
COMMAND_HEADER = re.compile(
    r"^\s*(?:\[[^\]]+\]\s+)?Command \[PID: \d+\]:\s*$",
    re.IGNORECASE,
)
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


def _jpackage_commands(log: str) -> list[tuple[str, list[str] | None]]:
    """Return logged Candle commands as raw lines or legacy argument lists."""
    commands: list[tuple[str, list[str] | None]] = []
    lines = log.splitlines()
    for index, line in enumerate(lines):
        if COMMAND_HEADER.fullmatch(line):
            if index + 1 < len(lines) and lines[index + 1][:1].isspace():
                command = lines[index + 1].strip()
                if "candle.exe" in command.lower():
                    commands.append((command, None))
            continue

        if "candle.exe" not in line.lower():
            continue
        end = line.rfind("]")
        start = line.rfind("[", 0, end)
        if start < 0 or end <= start:
            continue
        # Older jpackage output can render ProcessBuilder's List<String>.
        arguments = [_unquote(argument) for argument in line[start + 1:end].split(", ")]
        commands.append((line, arguments))
    return commands


def _is_named_file(argument: str, filename: str) -> bool:
    normalized = _unquote(argument).replace("\\", "/").rstrip("/")
    return normalized.rsplit("/", 1)[-1].lower() == filename.lower()


def _raw_command_has_named_file(command: str, filename: str) -> bool:
    """Match a filename without tokenizing paths or definition values on spaces."""
    return re.search(
        rf"(?:^|[\\/\s\"]){re.escape(filename)}(?=[\s\"]|$)",
        command,
        re.IGNORECASE,
    ) is not None


def _raw_definitions(command: str) -> list[str]:
    """Extract complete -dJp arguments using the next definition as boundary."""
    starts = list(DEFINITION_START.finditer(command))
    arguments = []
    for index, start in enumerate(starts):
        end = starts[index + 1].start() if index + 1 < len(starts) else len(command)
        arguments.append(command[start.start():end].rstrip())
    return arguments


def definitions(log: str) -> dict[str, str]:
    """Recover only definitions on jpackage's generated-main Candle command."""
    candidates: list[list[str]] = []
    for command, arguments in _jpackage_commands(log):
        if arguments is None:
            first_definition = DEFINITION_START.search(command)
            command_prefix = command[:first_definition.start()] if first_definition else command
            if (_raw_command_has_named_file(command_prefix, "candle.exe")
                    and _raw_command_has_named_file(command_prefix, "main.wxs")):
                candidates.append(_raw_definitions(command))
        elif (any(_is_named_file(argument, "candle.exe") for argument in arguments)
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

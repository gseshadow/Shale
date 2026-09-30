"""Recover jpackage's authoritative WiX preprocessor definitions for recompilation."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


REFERENCE = re.compile(r"\$\(var\.(Jp[A-Za-z0-9_]+)\)")


def required_variables(source: str) -> list[str]:
    return sorted(set(REFERENCE.findall(source)))


def _definition_value(log: str, name: str) -> str | None:
    marker = f"-d{name}="
    values: set[str] = set()
    for line in log.splitlines():
        start = line.find(marker)
        while start >= 0:
            value_start = start + len(marker)
            # JDK jpackage verbose output renders ProcessBuilder arguments as a
            # comma-separated list.  Accept whitespace-separated command output
            # as well, while retaining spaces inside a definition's value.
            endings = [position for token in (", -", ", main.wxs", ", bundle.wxf", "]")
                       if (position := line.find(token, value_start)) >= 0]
            next_definition = line.find(" -dJp", value_start)
            if next_definition >= 0:
                endings.append(next_definition)
            for token in (" -ext ", " -arch ", " -out "):
                position = line.find(token, value_start)
                if position >= 0:
                    endings.append(position)
            end = min(endings) if endings else len(line)
            value = line[value_start:end].strip().strip('"')
            if value:
                values.add(value)
            start = line.find(marker, value_start)
    if len(values) > 1:
        raise ValueError(f"conflicting jpackage values for {name}: {sorted(values)!r}")
    return next(iter(values), None)


def definitions(source: str, log: str) -> dict[str, str]:
    required = required_variables(source)
    found = {name: _definition_value(log, name) for name in required}
    missing = [name for name, value in found.items() if value is None]
    if missing:
        raise ValueError("missing jpackage WiX definition(s): " + ", ".join(missing))
    return {name: value for name, value in found.items() if value is not None}


def quote_response_argument(argument: str) -> str:
    """Quote one Candle response-file argument, including paths with spaces."""
    rendered = subprocess.list2cmdline([argument])
    return rendered if rendered.startswith('"') else f'"{rendered}"'


def prepare(source_path: Path, log_path: Path, response_path: Path) -> None:
    values = definitions(
        source_path.read_text(encoding="utf-8"),
        log_path.read_text(encoding="utf-8", errors="replace"),
    )
    response_path.write_text(
        "\n".join(quote_response_argument(f"-d{name}={value}") for name, value in values.items()) + "\n",
        encoding="utf-8",
    )
    print(f"Recovered {len(values)} required jpackage WiX definition(s): {', '.join(values)}")


def main(argv: list[str]) -> int:
    if len(argv) != 4 or argv[0] != "prepare":
        print("Usage: windows_jpackage_wix_definitions.py prepare <main.wxs> <jpackage.log> <output.rsp>",
              file=sys.stderr)
        return 2
    try:
        prepare(Path(argv[1]), Path(argv[2]), Path(argv[3]))
    except (OSError, ValueError) as exc:
        print(f"jpackage WiX definition recovery failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Reject native toast bridge dependencies that are not Windows system DLLs."""
from pathlib import Path
import re
import sys

SYSTEM_DLLS = {
    "advapi32.dll", "bcrypt.dll", "combase.dll", "crypt32.dll", "gdi32.dll",
    "kernel32.dll", "ntdll.dll", "ole32.dll", "oleaut32.dll", "rpcrt4.dll",
    "sechost.dll", "shell32.dll", "shlwapi.dll", "user32.dll", "userenv.dll",
    "version.dll", "winhttp.dll", "winmm.dll", "ws2_32.dll",
}
SYSTEM_PREFIXES = ("api-ms-win-", "ext-ms-win-")
NON_SYSTEM_RUNTIME_PREFIXES = ("vcruntime", "msvcp", "concrt", "msvcr", "ucrtbased")

def dependencies(text: str) -> set[str]:
    return {match.group(1).lower() for line in text.splitlines()
            if (match := re.fullmatch(r"\s*([A-Za-z0-9_.-]+\.dll)\s*", line))}

def validate(names: set[str]) -> None:
    if not names:
        raise ValueError("dumpbin reported no DLL dependencies")
    rejected = sorted(name for name in names if name.startswith(NON_SYSTEM_RUNTIME_PREFIXES)
                      or not (name in SYSTEM_DLLS or name.startswith(SYSTEM_PREFIXES)))
    if rejected:
        raise ValueError("non-system DLL dependency: " + ", ".join(rejected))

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: validate_windows_dll_dependencies.py <dumpbin-output>", file=sys.stderr)
        return 2
    try:
        names = dependencies(Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace"))
        validate(names)
    except (OSError, ValueError) as error:
        print(f"Windows toast DLL dependency validation failed: {error}", file=sys.stderr)
        return 1
    print("Windows toast DLL dependencies validated as Windows system components")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

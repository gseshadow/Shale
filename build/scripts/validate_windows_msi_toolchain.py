#!/usr/bin/env python3
"""Resolve and safely probe the WiX v3 executables required by the MSI build."""
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import sys

TOOLS = ("candle.exe", "light.exe", "dark.exe")
VERSION = re.compile(r"(?:toolset|compiler|linker|decompiler).*?version\s+3\.", re.IGNORECASE | re.DOTALL)

def validate_tool(name, resolver=shutil.which, runner=subprocess.run):
    path = resolver(name)
    if not path:
        raise ValueError(f"tool={name} classification=missing resolved=<not-found> check=PATH-resolution")
    resolved = str(Path(path).resolve())
    print(f'WiX tool check tool={name} resolved="{resolved}" check=help-version-probe')
    try:
        result = runner([resolved, "-?"], capture_output=True, text=True, timeout=30, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        code = getattr(error, "errno", None)
        raise ValueError(f'tool={name} classification=invocation_failure resolved="{resolved}" check=help-version-probe exit={code if code is not None else "unavailable"}') from None
    output = (result.stdout or "") + "\n" + (result.stderr or "")
    if not VERSION.search(output):
        raise ValueError(f'tool={name} classification=incompatible_version resolved="{resolved}" check=WiX-v3-signature exit={result.returncode}')
    print(f'WiX tool valid tool={name} resolved="{resolved}" version=3 exit={result.returncode}')

def validate():
    for name in TOOLS:
        validate_tool(name)

def main():
    argparse.ArgumentParser().parse_args()
    try:
        validate()
    except ValueError as error:
        print(f"Windows MSI toolchain validation failed: {error}", file=sys.stderr)
        return 1
    print("Windows MSI toolchain validation passed.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

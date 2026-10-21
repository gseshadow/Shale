#!/usr/bin/env python3
"""Stage only the desktop runtime inputs, never an entire Maven target directory."""
import argparse
from pathlib import Path
import re
import shutil

FORBIDDEN = frozenset({"generated-sources", "generated-test-sources", "test-classes",
                       "surefire-reports", "failsafe-reports", "maven-status", "maven-archiver",
                       "classes", "test-results", ".git"})

def validate_paths(paths):
    for path in paths:
        parts = tuple(alias.casefold() for part in path.parts for alias in part.split("|"))
        if any(part in FORBIDDEN for part in parts) or parts[-1].endswith(("-tests.jar", "-sources.jar", "-javadoc.jar")):
            raise ValueError(f"development-only artifact in runtime payload: {path}")

def validate_tree(root):
    paths = list(root.rglob("*"))
    if any(path.is_symlink() for path in paths):
        raise ValueError("runtime input must not contain symbolic links")
    validate_paths(path.relative_to(root) for path in paths)

def stage(target, destination, version):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("expected numeric major.minor.build version")
    if target.resolve() == destination.resolve() or target.resolve() in destination.resolve().parents or destination.resolve() in target.resolve().parents:
        raise ValueError("runtime stage and Maven target must be separate trees")
    jar = target / f"shale-desktop-{version}.jar"
    metadata = target / "shale-installed-version.properties"
    lib, updater = target / "lib", target / "updater"
    if not jar.is_file() or not metadata.is_file() or not lib.is_dir() or not (updater / "ShaleUpdater.exe").is_file():
        raise ValueError("missing desktop JAR, version metadata, dependencies, or packaged updater")
    dependencies = sorted(lib.iterdir())
    if not dependencies or any(not path.is_file() or path.suffix.casefold() != ".jar" for path in dependencies):
        raise ValueError("runtime dependency directory must contain only JARs")
    validate_tree(lib)
    validate_tree(updater)
    validate_paths(Path(path.name) for path in dependencies)
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    shutil.copy2(jar, destination / jar.name)
    shutil.copy2(metadata, destination / metadata.name)
    (destination / "lib").mkdir()
    for path in dependencies:
        shutil.copy2(path, destination / "lib" / path.name)
    shutil.copytree(updater, destination / "updater")
    # Runtime notices may be added explicitly at target root by future dependency tooling.
    for name in ("LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt", "THIRD-PARTY-NOTICES.txt"):
        source = target / name
        if source.is_file():
            shutil.copy2(source, destination / name)
    validate_tree(destination)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("target", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    try:
        stage(args.target, args.destination, args.version)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Windows runtime staging failed: {error}\n")
    print("Windows runtime inputs staged and validated")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate installed files in WiX 3 XML decompiled by dark.exe."""
from pathlib import Path
import argparse
import sys
import xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
REQUIRED_MARKER = {
    "format": "shale-windows-toast-v1",
    "appUserModelId": "com.shale.desktop.Shale",
    "architecture": "x64",
    "bridgeVersion": "1",
}
APPROVED_MAIN_CLASS = "com.shale.desktop.ShaleLauncher"

def tag(name):
    return f"{{{NS}}}{name}"

def parent_map(root):
    return {child: parent for parent in root.iter() for child in parent}

def directory_graph(root):
    parents = {}
    names = {}
    for owner in root.iter():
        if owner.tag not in (tag("Directory"), tag("DirectoryRef")):
            continue
        owner_id = owner.get("Id")
        for child in owner.findall(tag("Directory")):
            child_id = child.get("Id")
            if child_id and owner_id:
                parents.setdefault(child_id, set()).add(owner_id)
            if child_id:
                names.setdefault(child_id, set()).add(child.get("Name"))
        if owner.tag == tag("Directory") and owner_id:
            names.setdefault(owner_id, set()).add(owner.get("Name"))
    return parents, names

def file_description(file_node, component, directory_id):
    return (f"File Id={file_node.get('Id')!r} Name={file_node.get('Name')!r} "
            f"Source={file_node.get('Source')!r} Component={component.get('Id')!r} "
            f"Directory={directory_id!r}")

def canonical_directory_paths(directory_id, parents, names, trail=()):
    if directory_id == "INSTALLDIR":
        return {()}
    if directory_id in trail:
        raise ValueError(f"cyclic installed directory graph: {' -> '.join(trail + (directory_id,))}")
    parent_ids = parents.get(directory_id, set())
    if not parent_ids:
        return set()
    declared_names = names.get(directory_id, {None})
    paths = set()
    for parent_id in parent_ids:
        for parent_path in canonical_directory_paths(parent_id, parents, names, trail + (directory_id,)):
            for name in declared_names:
                paths.add(parent_path + (() if name in (None, ".") else (name,)))
    return paths

def installed_directory(root, file_node):
    parents = parent_map(root)
    component = parents.get(file_node)
    if component is None or component.tag != tag("Component"):
        raise ValueError(f"payload File {file_node.get('Name')!r} is not owned by a Component")
    directory_id = component.get("Directory")
    if not directory_id:
        owner = parents.get(component)
        while owner is not None and owner.tag not in (tag("Directory"), tag("DirectoryRef")):
            owner = parents.get(owner)
        directory_id = None if owner is None else owner.get("Id")
    description = file_description(file_node, component, directory_id)
    if not file_node.get("Id"):
        raise ValueError(f"payload File is missing required Id: {description}")
    if not directory_id:
        raise ValueError(f"payload File has no installed directory: {description}")

    graph, names = directory_graph(root)
    try:
        paths = canonical_directory_paths(directory_id, graph, names)
    except ValueError as error:
        raise ValueError(f"{error}; {description}") from None
    if not paths:
        raise ValueError(f"payload directory is not beneath INSTALLDIR; {description}")
    if len(paths) != 1:
        candidates = ", ".join("INSTALLDIR" + "".join(f"/{item}" for item in path) for path in sorted(paths))
        raise ValueError(f"payload directory has conflicting canonical paths [{candidates}]; {description}")
    return next(iter(paths))

def required_file(root, filename, expected_directory, label):
    matches = [node for node in root.iter(tag("File")) if node.get("Name", "").casefold() == filename.casefold()]
    if not matches:
        raise ValueError(f"missing {label} File entry: {filename}")
    if len(matches) != 1:
        raise ValueError(f"duplicate {label} File entries: {filename} ({len(matches)})")
    node = matches[0]
    actual_directory = installed_directory(root, node)
    if tuple(item.casefold() for item in actual_directory) != tuple(item.casefold() for item in expected_directory):
        actual = "INSTALLDIR" + "".join(f"/{item}" for item in actual_directory)
        expected = "INSTALLDIR" + "".join(f"/{item}" for item in expected_directory)
        raise ValueError(f"{label} is in the wrong installed directory: expected {expected}, found {actual}")
    source = node.get("Source")
    if not source or not Path(source).is_file():
        raise ValueError(f"missing extracted {label} payload: {source or '<missing Source>'}")
    return Path(source)

def required_source_file(root, filename, expected_directory, label):
    matches = []
    for node in root.iter(tag("File")):
        source = node.get("Source", "")
        basename = source.replace("/", "\\").rsplit("\\", 1)[-1]
        if basename.casefold() == filename.casefold():
            matches.append(node)
    if not matches:
        raise ValueError(f"missing generated {label} File entry: {filename}")
    if len(matches) != 1:
        raise ValueError(f"duplicate generated {label} File entries: {filename} ({len(matches)})")
    node = matches[0]
    if installed_directory(root, node) != expected_directory:
        raise ValueError(f"generated {label} is in the wrong installed directory")
    source = Path(node.get("Source"))
    if not source.is_file():
        raise ValueError(f"missing generated {label} payload: {source}")
    return source

def validate_launcher_config(path):
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except OSError as error:
        raise ValueError(f"launcher configuration is unreadable: {error.__class__.__name__}") from None
    section = ""
    values = []
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith(("#", ";")):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if section == "Application" and line.startswith("app.mainclass="):
            values.append(line.partition("=")[2].strip())
    if len(values) != 1:
        raise ValueError(f"launcher configuration must contain exactly one app.mainclass; found {len(values)}")
    if values[0] != APPROVED_MAIN_CLASS:
        raise ValueError(f"launcher configuration app.mainclass is not approved: {values[0] or '<empty>'}")

def marker_properties(path):
    required = {key: [] for key in REQUIRED_MARKER}
    try:
        lines = path.read_text(encoding="iso-8859-1").splitlines()
    except OSError as error:
        raise ValueError(f"invalid installed marker contents: unreadable payload ({error.__class__.__name__})") from None
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        separator = next((index for index, char in enumerate(line) if char in "=:"), -1)
        if separator < 0:
            parts = line.split(None, 1)
            key, value = (parts[0], parts[1] if len(parts) == 2 else "")
        else:
            key, value = line[:separator].strip(), line[separator + 1:].strip()
        if key in required:
            required[key].append(value)
    for key, expected in REQUIRED_MARKER.items():
        values = required[key]
        if not values:
            raise ValueError(f"invalid installed marker contents: missing required property {key}")
        if len(values) != 1:
            raise ValueError(f"invalid installed marker contents: duplicate required property {key}")
        if values[0] != expected:
            raise ValueError(f"invalid installed marker contents: conflicting value for {key}")

def validate_compiled(wxs):
    root = ET.parse(wxs).getroot()
    marker = required_file(root, "shale-windows-toast.properties", ("app",), "installed marker")
    required_file(root, "shale_windows_toast.dll", ("app", "native"), "native DLL")
    required_file(root, "Shale.exe", (), "launcher")
    launcher_config = required_file(root, "Shale.cfg", ("app",), "launcher configuration")
    marker_properties(marker)
    validate_launcher_config(launcher_config)

def validate_source(wxs):
    root = ET.parse(wxs).getroot()
    launcher_config = required_source_file(root, "Shale.cfg", ("app",), "launcher configuration")
    validate_launcher_config(launcher_config)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["config", "source", "compiled"])
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    try:
        {"config": validate_launcher_config, "source": validate_source, "compiled": validate_compiled}[args.mode](args.path)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Windows MSI payload validation failed: {error}", file=sys.stderr)
        return 1
    print(f"Windows launcher/payload {args.mode} validation passed: {args.path.name}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

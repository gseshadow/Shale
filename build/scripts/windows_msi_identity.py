#!/usr/bin/env python3
"""Fail-closed identity mutation/validation for JDK 21 jpackage WiX 3 output."""
from pathlib import Path
import argparse
import re
import sys
import xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
APP_ID = "com.shale.desktop.Shale"
ET.register_namespace("", NS)

def tag(name):
    return f"{{{NS}}}{name}"

def program_menu_directory_ids(root):
    ids = set()
    roots = [node for node in root.iter(tag("Directory")) if node.get("Id") == "ProgramMenuFolder"]
    if not roots:
        raise ValueError("ProgramMenuFolder directory declaration is missing")
    def collect(node):
        directory_id = node.get("Id")
        if directory_id:
            ids.add(directory_id)
        for child in node.findall(tag("Directory")):
            collect(child)
    for node in roots:
        collect(node)
    return ids

def component_condition(component):
    condition = component.get("Condition")
    if condition:
        return condition.strip()
    child = component.find(tag("Condition"))
    return "" if child is None or child.text is None else child.text.strip()

def resolve_target_file(root, shortcut):
    match = re.fullmatch(r"\[#([^\]]+)\]", shortcut.get("Target", ""))
    if not match:
        raise ValueError("Start Menu shortcut target is not a WiX file reference")
    file_id = match.group(1)
    files = [node for node in root.iter(tag("File")) if node.get("Id") == file_id]
    if len(files) != 1:
        raise ValueError(f"Start Menu shortcut target file {file_id!r} resolved {len(files)} times")
    source = files[0].get("Source", "")
    basename = source.replace("/", "\\").rsplit("\\", 1)[-1]
    if basename.casefold() != "shale.exe":
        raise ValueError(f"Start Menu shortcut target is not Shale.exe: {basename or '<missing>'}")
    return files[0]

def shortcut(root):
    parents = {child: parent for parent in root.iter() for child in parent}
    menu_ids = program_menu_directory_ids(root)
    found = []
    for node in root.iter(tag("Shortcut")):
        component = parents.get(node)
        if component is None or component.tag != tag("Component"):
            continue
        owner = parents.get(component)
        while owner is not None and owner.tag not in (tag("DirectoryRef"), tag("Directory")):
            owner = parents.get(owner)
        if owner is None or owner.get("Id") not in menu_ids:
            continue
        if component_condition(component) != "JP_INSTALL_STARTMENU_SHORTCUT":
            continue
        if node.get("Name") != "Shale" or node.get("WorkingDirectory") != "INSTALLDIR" or node.get("Advertise") != "no":
            continue
        resolve_target_file(root, node)
        found.append(node)
    if len(found) != 1:
        raise ValueError(f"expected one Shale Start Menu shortcut; found {len(found)}")
    return found[0]

def identity_property(node):
    props = [item for item in node.findall(tag("ShortcutProperty")) if item.get("Key") == "System.AppUserModel.ID"]
    if props and any(item.get("Value") != APP_ID for item in props):
        raise ValueError("conflicting AppUserModelID")
    if len(props) > 1:
        raise ValueError("duplicate AppUserModelID")
    return props

def inspect(path):
    tree = ET.parse(path)
    identity_property(shortcut(tree.getroot()))

def mutate(path):
    tree = ET.parse(path)
    node = shortcut(tree.getroot())
    if not identity_property(node):
        ET.SubElement(node, tag("ShortcutProperty"), {"Key": "System.AppUserModel.ID", "Value": APP_ID})
    tree.write(path, encoding="utf-8", xml_declaration=True)

def validate(path):
    tree = ET.parse(path)
    props = identity_property(shortcut(tree.getroot()))
    if len(props) != 1:
        raise ValueError("compiled shortcut identity missing or ambiguous")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["inspect", "mutate", "validate"])
    parser.add_argument("file", type=Path)
    args = parser.parse_args()
    try:
        {"inspect": inspect, "mutate": mutate, "validate": validate}[args.mode](args.file)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Windows MSI identity validation failed: {error}", file=sys.stderr)
        return 1
    print(f"Windows MSI identity {args.mode} passed: {args.file.name}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

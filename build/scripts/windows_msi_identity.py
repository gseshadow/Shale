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

def directory_subtree_ids(root, root_id):
    ids = set()
    roots = [node for node in root.iter() if node.tag in (tag("Directory"), tag("DirectoryRef")) and node.get("Id") == root_id]
    if not roots:
        raise ValueError(f"directory declaration is missing: {root_id}")
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

def resolve_target_file(root, shortcut, representation):
    match = re.fullmatch(r"\[#([^\]]+)\]", shortcut.get("Target", ""))
    if not match:
        raise ValueError(f"{representation}: Start Menu shortcut target is not a WiX file reference")
    file_id = match.group(1)
    files = [node for node in root.iter(tag("File")) if node.get("Id") == file_id]
    if len(files) != 1:
        raise ValueError(f"{representation}: Start Menu shortcut target file {file_id!r} resolved {len(files)} times")
    name = files[0].get("Name")
    source = files[0].get("Source", "")
    basename = source.replace("/", "\\").rsplit("\\", 1)[-1]
    if representation == "generated-source":
        if basename.casefold() != "shale.exe":
            raise ValueError(f"generated-source: Start Menu shortcut Source is not Shale.exe: {basename or '<missing>'}")
        if name is not None and name.casefold() != "shale.exe":
            raise ValueError(f"generated-source: Start Menu shortcut File Name is inconsistent: {name}")
    else:
        if name is None or name.casefold() != "shale.exe":
            raise ValueError(f"compiled/decompiled: Start Menu shortcut File Name is not Shale.exe: {name or '<missing>'}")
    return files[0]

def owned_shortcuts(root, directory_ids, condition):
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in root.iter(tag("Shortcut")):
        component = parents.get(node)
        if component is None or component.tag != tag("Component"):
            continue
        owner = parents.get(component)
        while owner is not None and owner.tag not in (tag("DirectoryRef"), tag("Directory")):
            owner = parents.get(owner)
        if owner is None or owner.get("Id") not in directory_ids:
            continue
        if component_condition(component) != condition:
            continue
        yield node

def ensure_desktop_identity_absent(root, representation):
    desktop_ids = directory_subtree_ids(root, "DesktopFolder")
    for node in owned_shortcuts(root, desktop_ids, "JP_INSTALL_DESKTOP_SHORTCUT"):
        if any(item.get("Key") == "System.AppUserModel.ID" for item in node.findall(tag("ShortcutProperty"))):
            raise ValueError(f"{representation}: Desktop shortcut must not contain System.AppUserModel.ID")

def shortcut(root, representation):
    menu_ids = directory_subtree_ids(root, "ProgramMenuFolder")
    found = []
    for node in owned_shortcuts(root, menu_ids, "JP_INSTALL_STARTMENU_SHORTCUT"):
        if node.get("Name") != "Shale" or node.get("WorkingDirectory") != "INSTALLDIR":
            continue
        advertise = node.get("Advertise")
        if representation == "generated-source" and advertise != "no":
            raise ValueError(f"generated-source: invalid Start Menu shortcut Advertise value: {advertise or '<missing>'}")
        if representation == "compiled/decompiled" and advertise not in (None, "no"):
            raise ValueError(f"compiled/decompiled: invalid Start Menu shortcut Advertise value: {advertise}")
        resolve_target_file(root, node, representation)
        found.append(node)
    if len(found) != 1:
        raise ValueError(f"{representation}: expected one Shale Start Menu shortcut; found {len(found)}")
    ensure_desktop_identity_absent(root, representation)
    return found[0]

def identity_property(node, representation):
    props = [item for item in node.findall(tag("ShortcutProperty")) if item.get("Key") == "System.AppUserModel.ID"]
    if props and any(item.get("Value") != APP_ID for item in props):
        raise ValueError(f"{representation}: conflicting AppUserModelID")
    if len(props) > 1:
        raise ValueError(f"{representation}: duplicate AppUserModelID")
    return props

def inspect(path):
    tree = ET.parse(path)
    identity_property(shortcut(tree.getroot(), "generated-source"), "generated-source")

def mutate(path):
    tree = ET.parse(path)
    node = shortcut(tree.getroot(), "generated-source")
    if not identity_property(node, "generated-source"):
        ET.SubElement(node, tag("ShortcutProperty"), {"Key": "System.AppUserModel.ID", "Value": APP_ID})
    tree.write(path, encoding="utf-8", xml_declaration=True)

def validate(path):
    tree = ET.parse(path)
    props = identity_property(shortcut(tree.getroot(), "compiled/decompiled"), "compiled/decompiled")
    if len(props) != 1:
        raise ValueError("compiled/decompiled: shortcut identity missing or ambiguous")

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

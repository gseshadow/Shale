#!/usr/bin/env python3
"""Fail-closed mutation/validation for JDK 21 jpackage WiX 3 output."""
from pathlib import Path
import argparse, sys, xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
APP_ID = "com.shale.desktop.Shale"
ET.register_namespace("", NS)

def shortcut(root):
    parents = {c:p for p in root.iter() for c in p}
    found=[]
    for node in root.iter(f"{{{NS}}}Shortcut"):
        if node.get("Name") != "Shale" or node.get("Advertise") != "no" or node.get("WorkingDirectory") != "INSTALLDIR": continue
        if not (node.get("Target", "").startswith("[#") and node.get("Target", "").endswith("]")): continue
        lineage=[]; cur=node
        while cur in parents: cur=parents[cur]; lineage.append(cur)
        if any(x.get("Id") == "ProgramMenuFolder" for x in lineage): found.append(node)
    if len(found) != 1: raise ValueError(f"expected one Shale Start Menu shortcut; found {len(found)}")
    return found[0]

def identity_property(node):
    props=[x for x in node.findall(f"{{{NS}}}ShortcutProperty") if x.get("Key")=="System.AppUserModel.ID"]
    if props and any(x.get("Value") != APP_ID for x in props): raise ValueError("conflicting AppUserModelID")
    if len(props)>1: raise ValueError("duplicate AppUserModelID")
    return props

def inspect(path):
    tree=ET.parse(path); identity_property(shortcut(tree.getroot()))

def mutate(path):
    tree=ET.parse(path); node=shortcut(tree.getroot())
    props=identity_property(node)
    if not props: ET.SubElement(node, f"{{{NS}}}ShortcutProperty", {"Key":"System.AppUserModel.ID", "Value":APP_ID})
    tree.write(path, encoding="utf-8", xml_declaration=True)

def validate(path):
    tree=ET.parse(path); node=shortcut(tree.getroot())
    props=[x for x in node.findall(f"{{{NS}}}ShortcutProperty") if x.get("Key")=="System.AppUserModel.ID" and x.get("Value")==APP_ID]
    if len(props)!=1: raise ValueError("compiled shortcut identity missing or ambiguous")

def main():
    p=argparse.ArgumentParser(); p.add_argument("mode", choices=["inspect","mutate","validate"]); p.add_argument("file", type=Path); a=p.parse_args()
    try: {"inspect":inspect,"mutate":mutate,"validate":validate}[a.mode](a.file)
    except (OSError, ET.ParseError, ValueError) as e: print(f"Windows MSI identity validation failed: {e}", file=sys.stderr); return 1
    print(f"Windows MSI identity {a.mode} passed: {a.file.name}"); return 0
if __name__ == "__main__": raise SystemExit(main())

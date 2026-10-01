#!/usr/bin/env python3
"""Inject and validate rollback-aware elevated registration actions in jpackage WiX 3 source."""
import argparse, base64, sys
from pathlib import Path
import xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
ET.register_namespace("", NS)
def tag(name): return f"{{{NS}}}{name}"
IDS = ("ShaleRegistrationInstall", "ShaleRegistrationUninstall", "ShaleRegistrationRollbackInstall", "ShaleRegistrationRollbackUninstall", "ShaleRegistrationCommit")
ROWS = (("SetShaleRegistrationRollbackInstall","InstallFiles","NOT (REMOVE~=\"ALL\")"),(IDS[2],"SetShaleRegistrationRollbackInstall","NOT (REMOVE~=\"ALL\")"),("SetShaleRegistrationInstall",IDS[2],"NOT (REMOVE~=\"ALL\")"),(IDS[0],"SetShaleRegistrationInstall","NOT (REMOVE~=\"ALL\")"),("SetShaleRegistrationRollbackUninstall",IDS[0],"REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),(IDS[3],"SetShaleRegistrationRollbackUninstall","REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),("SetShaleRegistrationUninstall",IDS[3],"REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),(IDS[1],"SetShaleRegistrationUninstall","REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),("SetShaleRegistrationCommit",IDS[1],"1"),(IDS[4],"SetShaleRegistrationCommit","1"))

def encoded_script(script):
    return base64.b64encode(script.read_text(encoding="utf-8-sig").encode("utf-16le")).decode("ascii")

def command(payload, mode):
    return f'"[SystemFolder]cmd.exe" /D /S /C "set ""SHALE_REG_MODE={mode}"" & set ""SHALE_REG_OWNER=[UserSID]"" & set ""SHALE_REG_INSTALL=[INSTALLDIR]"" & set ""SHALE_REG_SUPPORT=[LocalAppDataFolder]Shale"" & ""[SystemFolder]WindowsPowerShell\\v1.0\\powershell.exe"" -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand {payload}"'

def mutate(path, script):
    # jpackage's resource is a WiX preprocessor template.  Retain its processing
    # instructions so jpackage, rather than this helper, remains responsible for
    # evaluating every Jp* default and conditional.
    parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True, insert_pis=True))
    tree=ET.parse(path, parser=parser); root=tree.getroot(); products=list(root.iter(tag("Product")))
    if len(products) != 1: raise ValueError(f"expected one Product; found {len(products)}")
    product=products[0]
    packages=product.findall(tag("Package"))
    if len(packages) != 1: raise ValueError(f"expected one Package; found {len(packages)}")
    if packages[0].get("InstallScope") != "perUser": raise ValueError("registration requires jpackage per-user scope")
    # InstallScope is the sole WiX package privilege/scope declaration.  WiX 3
    # rejects perUser combined with InstallPrivileges=elevated (CNDL0387).
    # Privilege is instead obtained by launching msiexec elevated; the deferred
    # NoImpersonate actions then execute in the elevated installer service.
    packages[0].attrib.pop("InstallPrivileges", None)
    if any(n.get("Id") in IDS for n in product.findall(tag("CustomAction"))): raise ValueError("registration actions already exist")
    payload=encoded_script(script)
    actions=((IDS[0],"Install",None),(IDS[1],"Uninstall",None),(IDS[2],"RollbackInstall","rollback"),(IDS[3],"RollbackUninstall","rollback"),(IDS[4],"Commit","commit"))
    for action,mode,execute in actions:
        setter=f"Set{action}"
        ET.SubElement(product,tag("CustomAction"),{"Id":setter,"Property":action,"Value":command(payload,mode)})
        attrs={"Id":action,"BinaryKey":"WixCA","DllEntry":"WixQuietExec64","Execute":execute or "deferred","Return":"check","Impersonate":"no"}
        ET.SubElement(product,tag("CustomAction"),attrs)
    sequence=product.find(tag("InstallExecuteSequence"))
    if sequence is None: sequence=ET.SubElement(product,tag("InstallExecuteSequence"))
    for action,after,condition in ROWS:
        node=ET.SubElement(sequence,tag("Custom"),{"Action":action,"After":after}); node.text=condition
    tree.write(path,encoding="utf-8",xml_declaration=True)

def validate(path):
    root=ET.parse(path).getroot()
    actions={n.get("Id"):n for n in root.iter(tag("CustomAction"))}
    packages=list(root.iter(tag("Package")))
    if len(packages) != 1 or packages[0].get("InstallScope") != "perUser": raise ValueError("jpackage per-user scope missing")
    if packages[0].get("InstallPrivileges") is not None: raise ValueError("InstallPrivileges must be absent when InstallScope is perUser")
    for action in IDS:
        if action not in actions: raise ValueError(f"missing action: {action}")
        if actions[action].get("Impersonate") != "no" or actions[action].get("Return") != "check": raise ValueError(f"action is not fail-closed/elevated: {action}")
    sequence=root.find(f".//{tag('InstallExecuteSequence')}")
    actual=set() if sequence is None else {(n.get("Action"),n.get("After"),(n.text or "").strip()) for n in sequence.findall(tag("Custom"))}
    missing=set(ROWS)-actual
    if missing: raise ValueError(f"registration sequence contract missing: {sorted(missing)}")
    text=Path(path).read_text(encoding="utf-8")
    for forbidden in ("schtasks", "Register-ScheduledTask", "New-ScheduledTask", "New-Service", "CreateService"):
        if forbidden.casefold() in text.casefold(): raise ValueError(f"forbidden scheduler/service primitive: {forbidden}")

def main():
    p=argparse.ArgumentParser(); p.add_argument("mode",choices=("mutate","validate")); p.add_argument("file",type=Path); p.add_argument("--script",type=Path); a=p.parse_args()
    try:
        if a.mode == "mutate":
            if not a.script: raise ValueError("--script is required")
            mutate(a.file,a.script)
        validate(a.file)
    except (OSError,ET.ParseError,ValueError) as error:
        print(f"Windows MSI registration validation failed: {error}",file=sys.stderr); return 1
    print(f"Windows MSI registration {a.mode} passed: {a.file.name}"); return 0
if __name__ == "__main__": raise SystemExit(main())

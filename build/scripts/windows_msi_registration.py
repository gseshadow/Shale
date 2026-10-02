#!/usr/bin/env python3
"""Inject and validate rollback-aware elevated registration actions in jpackage WiX 3 source."""
import argparse, base64, sys
from pathlib import Path
import xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
ET.register_namespace("", NS)
def tag(name): return f"{{{NS}}}{name}"
IDS = ("ShaleRegistrationInstall", "ShaleRegistrationUninstall", "ShaleRegistrationRollbackInstall", "ShaleRegistrationRollbackUninstall", "ShaleRegistrationCommit")
JPACKAGE_SCOPE = "$(var.JpInstallScope)"
TARGET_MAX = 255
PAYLOAD_PROPERTY = "Srp"
MODE_CODES = {"Install":"I", "Uninstall":"U", "RollbackInstall":"R", "RollbackUninstall":"B", "Commit":"C"}
ROWS = (("SetShaleRegistrationRollbackInstall","InstallFiles","NOT (REMOVE~=\"ALL\")"),(IDS[2],"SetShaleRegistrationRollbackInstall","NOT (REMOVE~=\"ALL\")"),("SetShaleRegistrationInstall",IDS[2],"NOT (REMOVE~=\"ALL\")"),(IDS[0],"SetShaleRegistrationInstall","NOT (REMOVE~=\"ALL\")"),("SetShaleRegistrationRollbackUninstall",IDS[0],"REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),(IDS[3],"SetShaleRegistrationRollbackUninstall","REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),("SetShaleRegistrationUninstall",IDS[3],"REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),(IDS[1],"SetShaleRegistrationUninstall","REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE"),("SetShaleRegistrationCommit",IDS[1],"1"),(IDS[4],"SetShaleRegistrationCommit","1"))

def encoded_script(script):
    return base64.b64encode(script.read_text(encoding="utf-8-sig").encode("utf-16le")).decode("ascii")

def command(mode):
    """Return a short MSI-authored CustomAction.Target.

    Do not route through cmd.exe: cmd has an ~8K command-line ceiling, while the
    encoded registration payload expands to about 17K.  WixQuietExec can launch
    powershell.exe directly, which uses the normal Win32 process-command limit.
    The compact PowerShell bootstrap sets the four environment inputs expected by
    the embedded registration program, decodes the MSI-authored payload, and runs
    it in the same deferred non-impersonating process.
    """
    code=MODE_CODES[mode]
    return (
        f'"[System64Folder]WindowsPowerShell\\v1.0\\powershell.exe" '
        f'-NoP -EP Bypass -C "'
        f'$env:M=\'{code}\';$env:O=\'[UserSID]\';$env:I=\'[INSTALLDIR]\';'
        f'$env:S=\'[LocalAppDataFolder]Shale\';'
        f'iex([\\[]Text.Encoding[\\]]::Unicode.GetString([\\[]Convert[\\]]::FromBase64String(\'[{PAYLOAD_PROPERTY}]\')))"'
    )

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
    if packages[0].get("InstallScope") != JPACKAGE_SCOPE: raise ValueError("template contract violation: Package InstallScope must remain jpackage-controlled")
    # InstallScope is the sole WiX package privilege/scope declaration.  WiX 3
    # rejects perUser combined with InstallPrivileges=elevated (CNDL0387).
    # Privilege is instead obtained by launching msiexec elevated; the deferred
    # NoImpersonate actions then execute in the elevated installer service.
    packages[0].attrib.pop("InstallPrivileges", None)
    if any(n.get("Id") in IDS for n in product.findall(tag("CustomAction"))): raise ValueError("registration actions already exist")
    payload=encoded_script(script)
    ET.SubElement(product,tag("Property"),{"Id":PAYLOAD_PROPERTY,"Value":payload})
    actions=((IDS[0],"Install",None),(IDS[1],"Uninstall",None),(IDS[2],"RollbackInstall","rollback"),(IDS[3],"RollbackUninstall","rollback"),(IDS[4],"Commit","commit"))
    for action,mode,execute in actions:
        setter=f"Set{action}"
        ET.SubElement(product,tag("CustomAction"),{"Id":setter,"Property":action,"Value":command(mode)})
        attrs={"Id":action,"BinaryKey":"WixCA","DllEntry":"WixQuietExec64","Execute":execute or "deferred","Return":"check","Impersonate":"no"}
        ET.SubElement(product,tag("CustomAction"),attrs)
    sequence=product.find(tag("InstallExecuteSequence"))
    if sequence is None: sequence=ET.SubElement(product,tag("InstallExecuteSequence"))
    for action,after,condition in ROWS:
        node=ET.SubElement(sequence,tag("Custom"),{"Action":action,"After":after}); node.text=condition
    tree.write(path,encoding="utf-8",xml_declaration=True)

def validate_registration(root, path, contract, compiled=False):
    actions={n.get("Id"):n for n in root.iter(tag("CustomAction"))}
    properties={n.get("Id"):n for n in root.iter(tag("Property"))}
    packages=list(root.iter(tag("Package")))
    if len(packages) != 1: raise ValueError(f"{contract} contract violation: expected one Package; found {len(packages)}")
    for action in IDS:
        if action not in actions: raise ValueError(f"{contract} contract violation: missing action: {action}")
        return_mode=actions[action].get("Return")
        return_ok=return_mode == "check" or (compiled and return_mode is None)
        if actions[action].get("Impersonate") != "no" or not return_ok: raise ValueError(f"{contract} contract violation: action is not fail-closed/elevated: {action}")
        setter=actions.get(f"Set{action}")
        if setter is None: raise ValueError(f"{contract} contract violation: missing action-data setter: Set{action}")
        target=setter.get("Value") or ""
        if len(target) > TARGET_MAX: raise ValueError(f"{contract} contract violation: CustomAction Target overflow: Set{action} length={len(target)} limit={TARGET_MAX}")
        for required in (f"[{PAYLOAD_PROPERTY}]", "[UserSID]", "[INSTALLDIR]", "[LocalAppDataFolder]Shale"):
            if required not in target: raise ValueError(f"{contract} contract violation: incomplete action data for Set{action}: missing {required}")
    payload=properties.get(PAYLOAD_PROPERTY)
    if payload is None or not (payload.get("Value") or "").strip():
        raise ValueError(f"{contract} contract violation: missing private encoded registration payload")
    sequence=root.find(f".//{tag('InstallExecuteSequence')}")
    if compiled:
        expected_actions=[row[0] for row in ROWS]
        expected_conditions={row[0]:row[2] for row in ROWS}
        nodes=[] if sequence is None else [n for n in sequence.findall(tag("Custom")) if n.get("Action") in expected_actions]
        if len(nodes) != len(expected_actions):
            present=[n.get("Action") for n in nodes]
            missing=[action for action in expected_actions if action not in present]
            raise ValueError(f"{contract} contract violation: registration sequence missing: {missing}")
        try:
            ordered=sorted(nodes,key=lambda n:int(n.get("Sequence") or ""))
        except ValueError:
            raise ValueError(f"{contract} contract violation: compiled registration sequence must use numeric Sequence values")
        actual_actions=[n.get("Action") for n in ordered]
        if actual_actions != expected_actions:
            raise ValueError(f"{contract} contract violation: compiled registration sequence order mismatch: {actual_actions}")
        for node in ordered:
            action=node.get("Action"); condition=(node.text or "").strip()
            if condition != expected_conditions[action]:
                raise ValueError(f"{contract} contract violation: registration sequence condition mismatch: {action}")
    else:
        actual=set() if sequence is None else {(n.get("Action"),n.get("After"),(n.text or "").strip()) for n in sequence.findall(tag("Custom"))}
        missing=set(ROWS)-actual
        if missing: raise ValueError(f"{contract} contract violation: registration sequence missing: {sorted(missing)}")
    text=Path(path).read_text(encoding="utf-8")
    for forbidden in ("schtasks", "Register-ScheduledTask", "New-ScheduledTask", "New-Service", "CreateService"):
        if forbidden.casefold() in text.casefold(): raise ValueError(f"{contract} contract violation: forbidden scheduler/service primitive: {forbidden}")

def validate_template(path):
    root=ET.parse(path).getroot(); packages=list(root.iter(tag("Package")))
    if len(packages) != 1 or packages[0].get("InstallScope") != JPACKAGE_SCOPE:
        raise ValueError("template contract violation: Package InstallScope must be $(var.JpInstallScope)")
    if packages[0].get("InstallPrivileges") is not None:
        raise ValueError("template contract violation: Package InstallPrivileges must be absent")
    validate_registration(root, path, "template")

def validate_final(path):
    root=ET.parse(path).getroot(); packages=list(root.iter(tag("Package")))
    if len(packages) != 1:
        raise ValueError(f"final MSI contract violation: expected one Package; found {len(packages)}")
    package=packages[0]
    # InstallScope is WiX authoring syntax and is not reconstructed by WiX 3.14
    # Dark for this jpackage MSI.  Validate the compiled MSI representation that
    # Dark does emit instead, while rejecting source-like machine scope if a
    # future toolchain ever supplies it.
    scope=package.get("InstallScope")
    if scope is not None and scope != "perUser":
        raise ValueError(f"final MSI contract violation: explicit Package InstallScope must be perUser; found {scope}")
    privileges=package.get("InstallPrivileges")
    if privileges != "limited":
        raise ValueError(f"final MSI contract violation: resolved Package InstallPrivileges must be limited; found {privileges or '<absent>'}")

    properties={n.get("Id"):(n.get("Value") or "").strip() for n in root.iter(tag("Property"))}
    all_users=properties.get("ALLUSERS")
    install_per_user=properties.get("MSIINSTALLPERUSER")
    if all_users is not None and all_users not in ("", "2"):
        raise ValueError(f"final MSI contract violation: ALLUSERS indicates or may indicate per-machine installation: {all_users or '<empty>'}")
    if install_per_user is not None and (install_per_user != "1" or all_users != "2"):
        raise ValueError("final MSI contract violation: MSIINSTALLPERUSER is contradictory without ALLUSERS=2 and MSIINSTALLPERUSER=1")
    if all_users == "2" and install_per_user != "1":
        raise ValueError("final MSI contract violation: ALLUSERS=2 is ambiguous without MSIINSTALLPERUSER=1")
    validate_registration(root, path, "final MSI", compiled=True)

def main():
    p=argparse.ArgumentParser(); p.add_argument("mode",choices=("mutate","template","final")); p.add_argument("file",type=Path); p.add_argument("--script",type=Path); a=p.parse_args()
    try:
        if a.mode == "mutate":
            if not a.script: raise ValueError("--script is required")
            mutate(a.file,a.script)
            validate_template(a.file)
        elif a.mode == "template": validate_template(a.file)
        else: validate_final(a.file)
    except (OSError,ET.ParseError,ValueError) as error:
        print(f"Windows MSI registration validation failed: {error}",file=sys.stderr); return 1
    result = "template mutation/contract" if a.mode == "mutate" else f"{a.mode} contract"
    print(f"Windows MSI registration {result} passed: {a.file.name}"); return 0
if __name__ == "__main__": raise SystemExit(main())

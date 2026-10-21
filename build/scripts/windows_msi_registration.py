#!/usr/bin/env python3
"""Author and validate native per-user WiX registration; never execute installer scripts."""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

NS = "http://schemas.microsoft.com/wix/2006/wi"
ET.register_namespace("", NS)
JPACKAGE_SCOPE = "$(var.JpInstallScope)"
COMPONENT = "ShalePerUserRegistration"
COMPONENT_GUID = "{5CEFA7E0-8327-4C80-B748-D79A886E83DB}"
REGISTRY_KEY = r"SOFTWARE\Shale\Desktop"
LEGACY_PROPERTY = "SHALE_LEGACY_MSI"
LEGACY_CONDITION = "NOT SHALE_LEGACY_MSI"
LEGACY_MESSAGE = ("A legacy Shale MSI is installed. Close Shale and have the same Windows account "
                  "uninstall that version with administrator consent before installing this per-user MSI. "
                  "Preserve user settings and update state. See docs/windows-update-packaging.md.")
VALUES = {"scope": ("string", "perUser"), "schemaVersion": ("integer", "2"),
          "ownerSid": ("string", "[UserSID]"), "installRoot": ("string", "[INSTALLDIR]"),
          "supportRoot": ("string", "[LocalAppDataFolder]Shale"),
          "upgradeCode": ("string", "[UpgradeCode]")}

def tag(name):
    return f"{{{NS}}}{name}"

def one(root, name):
    nodes = list(root.iter(tag(name)))
    if len(nodes) != 1:
        raise ValueError(f"expected one {name}; found {len(nodes)}")
    return nodes[0]

def mutate(path):
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True, insert_pis=True))
    tree = ET.parse(path, parser=parser)
    root = tree.getroot()
    product, package = one(root, "Product"), one(root, "Package")
    if package.get("InstallScope") != JPACKAGE_SCOPE or package.get("InstallPrivileges") is not None:
        raise ValueError("template contract violation: unexpected package scope/privileges")
    features = product.findall(tag("Feature"))
    if len(features) != 1 or not product.get("UpgradeCode"):
        raise ValueError("expected one jpackage Feature and an UpgradeCode")
    if any(n.get("Id") == COMPONENT for n in root.iter(tag("Component"))):
        raise ValueError("per-user registration already exists")
    directory = ET.SubElement(product, tag("DirectoryRef"), {"Id": "INSTALLDIR"})
    component = ET.SubElement(directory, tag("Component"),
                              {"Id": COMPONENT, "Guid": COMPONENT_GUID, "Win64": "yes"})
    for index, (name, (kind, value)) in enumerate(VALUES.items()):
        attrs = {"Id": f"ShaleUserRegistration{index}", "Root": "HKCU", "Key": REGISTRY_KEY,
                 "Name": name, "Type": kind, "Value": value}
        if name == "installRoot":
            attrs["KeyPath"] = "yes"
        ET.SubElement(component, tag("RegistryValue"), attrs)
    ET.SubElement(features[0], tag("ComponentRef"), {"Id": COMPONENT})
    # Removing an older product executes the custom actions embedded in THAT MSI.
    # Refuse that transaction before RemoveExistingProducts, even if elevated.
    upgrade = ET.SubElement(product, tag("Upgrade"), {"Id": product.get("UpgradeCode")})
    ET.SubElement(upgrade, tag("UpgradeVersion"), {
        "Minimum": "1.0.128", "IncludeMinimum": "yes", "Maximum": "1.0.137",
        "IncludeMaximum": "yes", "OnlyDetect": "yes", "Property": LEGACY_PROPERTY})
    ET.SubElement(product, tag("Condition"), {"Message": LEGACY_MESSAGE}).text = LEGACY_CONDITION
    tree.write(path, encoding="utf-8", xml_declaration=True)

def validate_registration(root):
    components = [n for n in root.iter(tag("Component")) if n.get("Id") == COMPONENT]
    if len(components) != 1:
        raise ValueError("missing or duplicate per-user registration component")
    component = components[0]
    if component.get("Guid", "").upper() != COMPONENT_GUID or component.get("Win64") != "yes":
        raise ValueError("registration component identity/view changed")
    if component.get("Permanent") == "yes" or component.get("NeverOverwrite") == "yes":
        raise ValueError("registration must participate in repair/uninstall")
    parents = {child: owner for owner in root.iter() for child in owner}
    owner = parents[component]
    if component.get("Directory", owner.get("Id")) != "INSTALLDIR":
        raise ValueError("registration component must belong to INSTALLDIR")
    actual = {}
    for node in component.findall(tag("RegistryValue")):
        if node.get("Root") != "HKCU" or node.get("Key") != REGISTRY_KEY:
            raise ValueError("registration must write only the current user's key")
        name = node.get("Name")
        if name in actual:
            raise ValueError("duplicate registration value")
        actual[name] = (node.get("Type"), node.get("Value"))
        if (node.get("KeyPath") == "yes") != (name == "installRoot"):
            raise ValueError("installRoot must be the registration key path")
    if actual != VALUES:
        raise ValueError("incomplete or changed registration values")
    refs = [n for n in root.iter(tag("ComponentRef")) if n.get("Id") == COMPONENT]
    if len(refs) != 1 or parents[refs[0]].tag != tag("Feature"):
        raise ValueError("registration component must be selected by one Feature")
    # No installer command strings, HKLM writes, or legacy encoded payload may return.
    for node in root.iter():
        if node.tag in (tag("RegistryValue"), tag("RegistryKey"), tag("RemoveRegistryKey"), tag("RemoveRegistryValue")) and node.get("Root") in ("HKLM", "HKMU"):
            raise ValueError("machine registry writes are forbidden in the per-user package")
        if node.tag in (tag("Permission"), tag("PermissionEx")) or node.tag.rsplit("}", 1)[-1] == "PermissionEx":
            raise ValueError("installer ACL overrides are forbidden")
        if node.tag == tag("CustomAction"):
            text = " ".join(node.attrib.values()).casefold()
            if any(word in text for word in ("shaleregistration", "powershell", "frombase64string", "invoke-expression", "iex(")):
                raise ValueError("script registration custom actions are forbidden")
        if node.tag == tag("Property") and node.get("Id") == "Srp":
            raise ValueError("encoded registration payload is forbidden")
    product = one(root, "Product")
    legacy = [n for n in root.iter(tag("UpgradeVersion")) if n.get("Property") == LEGACY_PROPERTY]
    expected = {"Minimum": "1.0.128", "IncludeMinimum": "yes", "Maximum": "1.0.137",
                "IncludeMaximum": "yes", "OnlyDetect": "yes", "Property": LEGACY_PROPERTY}
    if len(legacy) != 1 or legacy[0].attrib != expected or parents[legacy[0]].get("Id") != product.get("UpgradeCode"):
        raise ValueError("legacy MSI migration detection must remain explicit")
    if not any(n.text == LEGACY_CONDITION and n.get("Message") == LEGACY_MESSAGE for n in product.findall(tag("Condition"))):
        raise ValueError("legacy MSI migration must stop before removal")

def validate_template(path):
    root = ET.parse(path).getroot()
    package = one(root, "Package")
    if package.get("InstallScope") != JPACKAGE_SCOPE or package.get("InstallPrivileges") is not None:
        raise ValueError("template contract violation: preserve jpackage scope without explicit privileges")
    validate_registration(root)

def validate_final(path):
    root = ET.parse(path).getroot()
    package = one(root, "Package")
    if package.get("InstallScope") not in (None, "perUser") or package.get("InstallPrivileges") != "limited":
        raise ValueError("final MSI contract violation: package must be perUser with limited privileges")
    properties = {n.get("Id"): n.get("Value", "") for n in root.iter(tag("Property"))}
    all_users, per_user = properties.get("ALLUSERS"), properties.get("MSIINSTALLPERUSER")
    if all_users not in (None, "") or per_user not in (None, ""):
        raise ValueError("final MSI contract violation: dual/machine scope is unsupported")
    validate_registration(root)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("mutate", "template", "final"))
    parser.add_argument("file", type=Path)
    args = parser.parse_args()
    try:
        if args.mode == "mutate":
            mutate(args.file)
            validate_template(args.file)
        else:
            {"template": validate_template, "final": validate_final}[args.mode](args.file)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Windows MSI registration validation failed: {error}", file=sys.stderr)
        return 1
    print(f"Windows MSI registration {args.mode} contract passed: {args.file.name}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("registration", Path(__file__).with_name("windows_msi_registration.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def fixture():
    return f'''<Wix xmlns="{m.NS}"><?include overrides.wxi?><Product Id="*" UpgradeCode="$(var.JpProductUpgradeCode)">
    <Package InstallScope="{m.JPACKAGE_SCOPE}"/><Feature Id="DefaultFeature"/><InstallExecuteSequence/></Product></Wix>'''


class RegistrationMutationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.wxs = Path(self.temporary.name) / "main.wxs"
        self.wxs.write_text(fixture())

    def mutate(self, final=False):
        m.mutate(self.wxs)
        if final:
            tree = ET.parse(self.wxs)
            package = next(tree.getroot().iter(m.tag("Package")))
            package.attrib.clear()
            package.set("InstallPrivileges", "limited")
            tree.write(self.wxs)
        return ET.parse(self.wxs)

    def test_native_registration_uses_current_user_and_msi_lifecycle(self):
        tree = self.mutate()
        m.validate_template(self.wxs)
        root = tree.getroot()
        component = next(root.iter(m.tag("Component")))
        self.assertEqual(m.COMPONENT_GUID, component.get("Guid"))
        self.assertEqual(m.VALUES, {n.get("Name"): (n.get("Type"), n.get("Value")) for n in component})
        self.assertTrue(all(n.get("Root") == "HKCU" for n in component))
        self.assertIsNone(component.get("Permanent"))
        self.assertIsNone(component.get("NeverOverwrite"))
        self.assertEqual(["installRoot"], [n.get("Name") for n in component if n.get("KeyPath") == "yes"])
        self.assertFalse(list(root.iter(m.tag("CustomAction"))))
        # Constant per-user component identity survives new product/version generation;
        # separate HKCU hives isolate two Windows owners without a shared write.
        self.assertEqual("[UserSID]", m.VALUES["ownerSid"][1])
        self.assertEqual("[UpgradeCode]", m.VALUES["upgradeCode"][1])

    def test_preserves_jpackage_instructions_and_scope(self):
        self.mutate()
        self.assertIn("<?include overrides.wxi?>", self.wxs.read_text())
        with self.assertRaisesRegex(ValueError, "already exists"):
            m.mutate(self.wxs)

    def test_legacy_upgrade_is_blocked_before_old_msi_can_execute(self):
        tree = self.mutate()
        upgrade = next(tree.getroot().iter(m.tag("UpgradeVersion")))
        self.assertEqual("yes", upgrade.get("OnlyDetect"))
        self.assertEqual("1.0.136", upgrade.get("Maximum"))
        condition = next(tree.getroot().iter(m.tag("Condition")))
        self.assertEqual(m.LEGACY_CONDITION, condition.text)
        self.assertIn("administrator consent", condition.get("Message"))
        tree.getroot().find(m.tag("Product")).remove(condition)
        tree.write(self.wxs)
        with self.assertRaisesRegex(ValueError, "stop before removal"):
            m.validate_template(self.wxs)

    def test_rejects_machine_writes_encoded_actions_and_payload(self):
        for tag, attrs in (("RegistryValue", {"Root": "HKLM"}),
                           ("RegistryValue", {"Root": "HKMU"}),
                           ("CustomAction", {"Id": "ShaleRegistrationInstall"}),
                           ("CustomAction", {"Id": "Other", "ExeCommand": "powershell.exe"}),
                           ("Property", {"Id": "Srp", "Value": "encoded"})):
            self.wxs.write_text(fixture())
            tree = self.mutate()
            ET.SubElement(tree.getroot().find(m.tag("Product")), m.tag(tag), attrs)
            tree.write(self.wxs)
            with self.assertRaises(ValueError, msg=f"must reject {tag} {attrs}"):
                m.validate_template(self.wxs)

    def test_registration_value_and_lifecycle_drift_fail_closed(self):
        mutations = (("RegistryValue", "Root", "HKLM"), ("RegistryValue", "Value", "changed"),
                     ("Component", "Permanent", "yes"), ("Component", "NeverOverwrite", "yes"),
                     ("Component", "Guid", "changed"), ("UpgradeVersion", "OnlyDetect", "no"),
                     ("ComponentRef", "Id", "missing"))
        for name, attribute, value in mutations:
            self.wxs.write_text(fixture())
            tree = self.mutate()
            next(tree.getroot().iter(m.tag(name))).set(attribute, value)
            tree.write(self.wxs)
            with self.assertRaises(ValueError, msg=f"must reject {name}.{attribute}"):
                m.validate_template(self.wxs)

    def test_template_rejects_literal_scope_or_explicit_privileges(self):
        for name, value in (("InstallScope", "perMachine"), ("InstallScope", "perUser"),
                            ("InstallPrivileges", "elevated"), ("InstallPrivileges", "limited")):
            self.wxs.write_text(fixture())
            tree = self.mutate()
            next(tree.getroot().iter(m.tag("Package"))).set(name, value)
            tree.write(self.wxs)
            with self.assertRaisesRegex(ValueError, "template contract"):
                m.validate_template(self.wxs)

    def test_dark_limited_representation_passes(self):
        self.mutate(final=True)
        m.validate_final(self.wxs)

    def test_final_rejects_machine_elevation_and_dual_scope(self):
        for name, value in (("InstallPrivileges", "elevated"), ("InstallScope", "perMachine")):
            self.wxs.write_text(fixture())
            tree = self.mutate(final=True)
            next(tree.getroot().iter(m.tag("Package"))).set(name, value)
            tree.write(self.wxs)
            with self.assertRaisesRegex(ValueError, "final MSI contract"):
                m.validate_final(self.wxs)
        for name, value in (("ALLUSERS", "1"), ("ALLUSERS", "2"), ("MSIINSTALLPERUSER", "1")):
            self.wxs.write_text(fixture())
            tree = self.mutate(final=True)
            ET.SubElement(tree.getroot().find(m.tag("Product")), m.tag("Property"), {"Id": name, "Value": value})
            tree.write(self.wxs)
            with self.assertRaisesRegex(ValueError, "dual/machine scope"):
                m.validate_final(self.wxs)

    def test_active_installer_has_no_powershell_execution(self):
        scripts = Path(__file__).parent
        self.assertFalse((scripts / "windows-installation-registration.ps1").exists())
        self.assertFalse((scripts / "DELETE windows_msi_registration.py").exists())
        batch = (scripts / "build-shale-windows-msi.bat").read_text()
        self.assertNotIn("--script", batch)
        self.assertNotIn("ExecutionPolicy Bypass", batch)

if __name__ == "__main__":
    unittest.main()

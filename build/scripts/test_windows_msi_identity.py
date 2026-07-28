import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("identity", Path(__file__).with_name("windows_msi_identity.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

def fixture(menu_id="dir-menu-generated-a", file_id="file-main-generated-a", source=r"C:\staged\Shale.exe",
            menu_shortcuts=1, include_file=True, property_xml="", include_menu=True):
    menu_directory = f'<Directory Id="{menu_id}" Name="Unknown"/>' if include_menu else ""
    menu_components = "".join(f'''
      <Component Id="component-menu-{index}">
        <Condition>JP_INSTALL_STARTMENU_SHORTCUT</Condition>
        <Shortcut Id="shortcut-menu-{index}" Name="Shale" WorkingDirectory="INSTALLDIR" Advertise="no" Target="[#{file_id}]">{property_xml}</Shortcut>
      </Component>''' for index in range(menu_shortcuts))
    file_xml = f'<Component Id="component-file"><File Id="{file_id}" Source="{source}"/></Component>' if include_file else ""
    return f'''<?xml version="1.0"?>
<Wix xmlns="{m.NS}">
  <Fragment>
    <DirectoryRef Id="TARGETDIR"><Directory Id="ProgramMenuFolder">{menu_directory}</Directory></DirectoryRef>
    <DirectoryRef Id="{menu_id}">{menu_components}</DirectoryRef>
    <DirectoryRef Id="DesktopFolder">
      <Component Id="component-desktop"><Condition>JP_INSTALL_DESKTOP_SHORTCUT</Condition>
        <Shortcut Id="shortcut-desktop" Name="Shale" WorkingDirectory="INSTALLDIR" Advertise="no" Target="[#{file_id}]"/>
      </Component>
    </DirectoryRef>
    <DirectoryRef Id="INSTALLDIR">{file_xml}</DirectoryRef>
  </Fragment>
</Wix>'''

def compiled_fixture(menu_id="dir-menu-compiled-a", file_id="file-main-compiled-a", menu_shortcuts=1,
                     include_file=True, file_name="Shale.exe", advertise=None,
                     property_xml='<ShortcutProperty Key="System.AppUserModel.ID" Value="com.shale.desktop.Shale"/>',
                     desktop_property_xml="", duplicate_file=False):
    advertise_xml = "" if advertise is None else f' Advertise="{advertise}"'
    menu_components = "".join(f'''
      <Component Id="component-menu-{index}">
        <Condition>JP_INSTALL_STARTMENU_SHORTCUT</Condition>
        <Shortcut Id="shortcut-menu-{index}" Directory="{menu_id}" Name="Shale" WorkingDirectory="INSTALLDIR"{advertise_xml} Target="[#{file_id}]">{property_xml}</Shortcut>
      </Component>''' for index in range(menu_shortcuts))
    name_xml = "" if file_name is None else f' Name="{file_name}"'
    files = ""
    if include_file:
        files = f'<Component Id="component-file"><File Id="{file_id}"{name_xml} Source="C:\\dark\\payload\\File\\opaque-id"/></Component>'
        if duplicate_file:
            files += f'<Component Id="component-file-duplicate"><File Id="{file_id}"{name_xml} Source="C:\\dark\\payload\\File\\other-id"/></Component>'
    return f'''<?xml version="1.0"?>
<Wix xmlns="{m.NS}">
  <Fragment>
    <Directory Id="TARGETDIR">
      <Directory Id="ProgramMenuFolder"><Directory Id="{menu_id}" Name="Unknown">{menu_components}</Directory></Directory>
      <Directory Id="DesktopFolder">
        <Component Id="component-desktop"><Condition>JP_INSTALL_DESKTOP_SHORTCUT</Condition>
          <Shortcut Id="shortcut-desktop" Directory="DesktopFolder" Name="Shale" WorkingDirectory="INSTALLDIR" Target="[#{file_id}]">{desktop_property_xml}</Shortcut>
        </Component>
      </Directory>
      <Directory Id="INSTALLDIR">{files}</Directory>
    </Directory>
  </Fragment>
</Wix>'''

class IdentityMutationTest(unittest.TestCase):
    def file(self, text):
        path = Path(tempfile.mkdtemp()) / "bundle.wxf"
        path.write_text(text, encoding="utf-8")
        return path

    def test_generated_program_menu_directory_and_target_resolve(self):
        path = self.file(fixture(menu_id="dir-random-one", file_id="file-random-one"))
        m.inspect(path)

    def test_desktop_shortcut_with_same_name_and_target_is_not_selected_or_mutated(self):
        path = self.file(fixture())
        m.mutate(path)
        root = ET.parse(path).getroot()
        menu = next(node for node in root.iter(m.tag("Shortcut")) if node.get("Id").startswith("shortcut-menu"))
        desktop = next(node for node in root.iter(m.tag("Shortcut")) if node.get("Id") == "shortcut-desktop")
        self.assertEqual(1, len(menu.findall(m.tag("ShortcutProperty"))))
        self.assertEqual(0, len(desktop.findall(m.tag("ShortcutProperty"))))

    def test_zero_and_multiple_start_menu_shortcuts_fail(self):
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaises(ValueError):
                m.inspect(self.file(fixture(menu_shortcuts=count)))

    def test_missing_target_file_fails(self):
        with self.assertRaisesRegex(ValueError, "resolved 0 times"):
            m.inspect(self.file(fixture(include_file=False)))

    def test_non_shale_target_fails(self):
        with self.assertRaisesRegex(ValueError, "not Shale.exe"):
            m.inspect(self.file(fixture(source=r"C:\staged\Other.exe")))

    def test_conflicting_and_duplicate_identity_fail(self):
        conflicting = '<ShortcutProperty Key="System.AppUserModel.ID" Value="wrong"/>'
        duplicate = ''.join('<ShortcutProperty Key="System.AppUserModel.ID" Value="com.shale.desktop.Shale"/>' for _ in range(2))
        for value in (conflicting, duplicate):
            with self.subTest(value=value), self.assertRaises(ValueError):
                m.inspect(self.file(fixture(property_xml=value)))

    def test_expected_existing_identity_is_idempotent(self):
        expected = '<ShortcutProperty Key="System.AppUserModel.ID" Value="com.shale.desktop.Shale"/>'
        path = self.file(fixture(property_xml=expected))
        m.inspect(path); m.mutate(path); m.inspect(path)
        self.assertEqual(1, path.read_text(encoding="utf-8").count("System.AppUserModel.ID"))

    def test_generated_ids_can_change(self):
        for suffix in ("alpha", "completely-different-42"):
            with self.subTest(suffix=suffix):
                m.inspect(self.file(fixture(menu_id=f"dir-{suffix}", file_id=f"file-{suffix}")))

    def test_compiled_dark_representation_passes_and_excludes_desktop(self):
        path = self.file(compiled_fixture())
        m.validate(path)
        root = ET.parse(path).getroot()
        desktop = next(node for node in root.iter(m.tag("Shortcut")) if node.get("Id") == "shortcut-desktop")
        self.assertEqual([], desktop.findall(m.tag("ShortcutProperty")))

    def test_compiled_advertise_rules(self):
        m.validate(self.file(compiled_fixture(advertise=None)))
        with self.assertRaisesRegex(ValueError, "compiled/decompiled: invalid.*Advertise"):
            m.validate(self.file(compiled_fixture(advertise="yes")))

    def test_compiled_uses_file_name_not_dark_payload_source(self):
        m.validate(self.file(compiled_fixture(file_name="sHaLe.ExE")))
        for name in (None, "Other.exe"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "compiled/decompiled:.*File Name"):
                m.validate(self.file(compiled_fixture(file_name=name)))

    def test_compiled_shortcut_cardinality_is_exact(self):
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, "compiled/decompiled: expected one"):
                m.validate(self.file(compiled_fixture(menu_shortcuts=count)))

    def test_compiled_target_file_cardinality_is_exact(self):
        with self.assertRaisesRegex(ValueError, "resolved 0 times"):
            m.validate(self.file(compiled_fixture(include_file=False)))
        with self.assertRaisesRegex(ValueError, "resolved 2 times"):
            m.validate(self.file(compiled_fixture(duplicate_file=True)))

    def test_compiled_identity_is_required_unique_and_exact(self):
        expected = '<ShortcutProperty Key="System.AppUserModel.ID" Value="com.shale.desktop.Shale"/>'
        cases = (
            ("", "missing or ambiguous"),
            ('<ShortcutProperty Key="System.AppUserModel.ID" Value="wrong"/>', "conflicting"),
            (expected + expected, "duplicate"),
        )
        for identity, diagnostic in cases:
            with self.subTest(diagnostic=diagnostic), self.assertRaisesRegex(ValueError, diagnostic):
                m.validate(self.file(compiled_fixture(property_xml=identity)))

    def test_compiled_desktop_identity_is_rejected(self):
        identity = '<ShortcutProperty Key="System.AppUserModel.ID" Value="com.shale.desktop.Shale"/>'
        with self.assertRaisesRegex(ValueError, "Desktop shortcut"):
            m.validate(self.file(compiled_fixture(desktop_property_xml=identity)))

    def test_compiled_generated_ids_can_change(self):
        for suffix in ("first", "unrelated-987"):
            with self.subTest(suffix=suffix):
                m.validate(self.file(compiled_fixture(menu_id=f"dir-{suffix}", file_id=f"file-{suffix}")))

    def test_generated_source_still_requires_advertise_and_source_basename(self):
        without_advertise = fixture().replace(' Advertise="no"', "", 1)
        with self.assertRaisesRegex(ValueError, "generated-source: invalid.*Advertise"):
            m.inspect(self.file(without_advertise))
        with self.assertRaisesRegex(ValueError, "generated-source:.*Source is not Shale.exe"):
            m.inspect(self.file(fixture(source=r"C:\dark\payload\opaque-id")))

if __name__ == "__main__":
    unittest.main()

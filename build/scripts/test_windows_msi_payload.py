import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

spec = importlib.util.spec_from_file_location("payload", Path(__file__).with_name("windows_msi_payload.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class CompiledPayloadTest(unittest.TestCase):
    def fixture(self, *, marker_count=1, dll_count=1, launcher_count=1, marker_dir="app",
                dll_dir="native", marker_contents=None, missing_payload=None,
                main_class=m.APPROVED_MAIN_CLASS, diagnostic_launcher_count=1,
                diagnostic_main_class=m.DIAGNOSTIC_MAIN_CLASS, diagnostic_class_count=1,
                legacy_diagnostic_count=0, diagnostic_owner="shale-desktop-1.0.129.jar"):
        temporary = Path(tempfile.mkdtemp())
        payload = temporary / "opaque"
        payload.mkdir()
        expected = marker_contents or "\n".join(f"{key}={value}" for key, value in m.REQUIRED_MARKER.items()) + "\n"
        entries = []
        def files(name, count, directory, contents=b"payload"):
            result = []
            for index in range(count):
                source = payload / f"opaque-{name}-{index}"
                if missing_payload != name:
                    source.write_bytes(contents if isinstance(contents, bytes) else contents.encode("iso-8859-1"))
                result.append(f'<Component Id="c-{name}-{index}"><File Id="f-{name}-{index}" Name="{name}" Source="{source}"/></Component>')
            return "".join(result)
        marker_files = files("shale-windows-toast.properties", marker_count, marker_dir, expected)
        dll_files = files("shale_windows_toast.dll", dll_count, dll_dir)
        launcher_files = files("Shale.exe", launcher_count, "INSTALLDIR")
        diagnostic_launcher_files = files(m.DIAGNOSTIC_LAUNCHER, diagnostic_launcher_count, "INSTALLDIR")
        config = files("Shale.cfg", 1, "app", f"[Application]\napp.mainclass={main_class}\n")
        jar_names = [diagnostic_owner, "shale-core-1.0.129.jar", "shale-updater-1.0.129.jar"]
        jar_names.extend(f"duplicate-{index}.jar" for index in range(1, diagnostic_class_count))
        classpath = "\n".join(f"app.classpath=$APPDIR/{'lib/' if name != diagnostic_owner else ''}{name}" for name in jar_names)
        diagnostic_config = files(m.DIAGNOSTIC_CONFIG, 1, "app", f"[Application]\napp.mainclass={diagnostic_main_class}\n{classpath}\n")
        root_jars, lib_jars = [], []
        for index, name in enumerate(jar_names):
            source = payload / f"jar-{index}.jar"
            if missing_payload != "diagnostic.jar":
                with zipfile.ZipFile(source, "w") as jar:
                    if (index == 0 and diagnostic_class_count > 0) or index >= 3:
                        jar.writestr(m.DIAGNOSTIC_CLASS_ENTRY, b"class")
                    if name.startswith("shale-core-"):
                        jar.writestr(m.PRODUCTION_READER_CLASS_ENTRY, b"reader")
                    if index > 0 and index <= legacy_diagnostic_count:
                        jar.writestr(m.LEGACY_DIAGNOSTIC_CLASS_ENTRY, b"legacy")
            entry = f'<Component Id="c-jar-{index}"><File Id="f-jar-{index}" Name="{name}" Source="{source}"/></Component>'
            (root_jars if name == diagnostic_owner else lib_jars).append(entry)
        if marker_dir == "app":
            app_marker, wrong_marker = marker_files, ""
        else:
            app_marker, wrong_marker = "", marker_files
        if dll_dir == "native":
            native_dll, wrong_dll = dll_files, ""
        else:
            native_dll, wrong_dll = "", dll_files
        xml = f'''<Wix xmlns="{m.NS}"><Fragment><Directory Id="TARGETDIR"><Directory Id="INSTALLDIR">
          {launcher_files}{diagnostic_launcher_files}<Directory Id="app-dir" Name="app">{app_marker}{config}{diagnostic_config}{"".join(root_jars)}<Directory Id="lib-dir" Name="lib">{"".join(lib_jars)}</Directory><Directory Id="native-dir" Name="native">{native_dll}</Directory></Directory>
          <Directory Id="wrong-dir" Name="wrong">{wrong_marker}{wrong_dll}</Directory>
        </Directory></Directory></Fragment></Wix>'''
        wxs = temporary / "final.wxs"
        wxs.write_text(xml, encoding="utf-8")
        return wxs

    def test_opaque_dark_sources_and_installed_names_pass(self):
        m.validate_compiled(self.fixture())

    def test_marker_cardinality(self):
        for count, message in ((0, "missing installed marker"), (2, "duplicate installed marker")):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, message):
                m.validate_compiled(self.fixture(marker_count=count))

    def test_marker_directory_and_extracted_payload(self):
        with self.assertRaisesRegex(ValueError, "installed marker is in the wrong installed directory"):
            m.validate_compiled(self.fixture(marker_dir="wrong"))
        with self.assertRaisesRegex(ValueError, "missing extracted installed marker payload"):
            m.validate_compiled(self.fixture(missing_payload="shale-windows-toast.properties"))

    def test_marker_required_properties(self):
        valid = dict(m.REQUIRED_MARKER)
        cases = []
        missing = valid.copy(); missing.pop("architecture")
        cases.append((missing, "missing required property architecture"))
        conflicting = valid.copy(); conflicting["appUserModelId"] = "wrong"
        cases.append((conflicting, "conflicting value for appUserModelId"))
        for values, message in cases:
            contents = "\r\n".join(f"{key}={value}" for key, value in values.items()) + "\r\n"
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                m.validate_compiled(self.fixture(marker_contents=contents))
        duplicate = "\n".join(f"{key}={value}" for key, value in valid.items()) + "\narchitecture=x64\n"
        with self.assertRaisesRegex(ValueError, "duplicate required property architecture"):
            m.validate_compiled(self.fixture(marker_contents=duplicate))

    def test_native_dll_cardinality_directory_and_payload(self):
        for count, message in ((0, "missing native DLL"), (2, "duplicate native DLL")):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, message):
                m.validate_compiled(self.fixture(dll_count=count))
        with self.assertRaisesRegex(ValueError, "native DLL is in the wrong installed directory"):
            m.validate_compiled(self.fixture(dll_dir="wrong"))
        with self.assertRaisesRegex(ValueError, "missing extracted native DLL payload"):
            m.validate_compiled(self.fixture(missing_payload="shale_windows_toast.dll"))

    def test_launcher_is_mandatory_and_extracted(self):
        with self.assertRaisesRegex(ValueError, "missing launcher"):
            m.validate_compiled(self.fixture(launcher_count=0))
        with self.assertRaisesRegex(ValueError, "missing extracted launcher payload"):
            m.validate_compiled(self.fixture(missing_payload="Shale.exe"))

    def test_diagnostic_launcher_configuration_and_class_are_mandatory(self):
        with self.assertRaisesRegex(ValueError, "missing registration diagnostic launcher"):
            m.validate_compiled(self.fixture(diagnostic_launcher_count=0))
        with self.assertRaisesRegex(ValueError, "app.mainclass is not approved"):
            m.validate_compiled(self.fixture(diagnostic_main_class="com.shale.desktop.ShaleLauncher"))
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, "desktop diagnostic class must occur.*exactly one"):
                m.validate_compiled(self.fixture(diagnostic_class_count=count))
        with self.assertRaisesRegex(ValueError, "must be owned by shale-desktop"):
            m.validate_compiled(self.fixture(diagnostic_owner="diagnostic-host.jar"))
        with self.assertRaisesRegex(ValueError, "legacy core diagnostic class remains"):
            m.validate_compiled(self.fixture(legacy_diagnostic_count=1))

    def test_diagnostic_launcher_properties_are_narrow_and_console_enabled(self):
        temporary = Path(tempfile.mkdtemp())
        properties = temporary / "diagnostic.properties"
        valid = (f"main-class={m.DIAGNOSTIC_MAIN_CLASS}\nwin-console=true\n"
                 "win-menu=false\nwin-shortcut=false\n")
        properties.write_text(valid, encoding="utf-8")
        m.validate_add_launcher_properties(properties)
        for invalid in (
                valid.replace("win-console=true", "win-console=false"),
                valid + "arguments=--anything\n"):
            properties.write_text(invalid, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "must contain only"):
                m.validate_add_launcher_properties(properties)

    def test_application_image_requires_packaged_diagnostic_launcher_config_and_class(self):
        temporary = Path(tempfile.mkdtemp())
        app = temporary / "app"
        lib = app / "lib"
        lib.mkdir(parents=True)
        (temporary / "Shale.exe").write_bytes(b"main")
        (temporary / m.DIAGNOSTIC_LAUNCHER).write_bytes(b"diagnostic")
        (app / "Shale.cfg").write_text(f"[Application]\napp.mainclass={m.APPROVED_MAIN_CLASS}\n", encoding="utf-8")
        (app / m.DIAGNOSTIC_CONFIG).write_text(f"[Application]\napp.mainclass={m.DIAGNOSTIC_MAIN_CLASS}\napp.classpath=$APPDIR\\shale-desktop-1.0.129.jar\napp.classpath=$APPDIR\\lib\\shale-core-1.0.129.jar\n", encoding="utf-8")
        with zipfile.ZipFile(app / "shale-desktop-1.0.129.jar", "w") as jar:
            jar.writestr(m.DIAGNOSTIC_CLASS_ENTRY, b"class")
        with zipfile.ZipFile(lib / "shale-core-1.0.129.jar", "w") as jar:
            jar.writestr(m.PRODUCTION_READER_CLASS_ENTRY, b"reader")
        m.validate_image(temporary)
        (temporary / m.DIAGNOSTIC_LAUNCHER).unlink()
        with self.assertRaisesRegex(ValueError, "application image is missing ShaleRegistrationDiagnostic.exe"):
            m.validate_image(temporary)

    def test_compiled_launcher_configuration_requires_bootstrap(self):
        m.validate_compiled(self.fixture())
        with self.assertRaisesRegex(ValueError, "app.mainclass is not approved"):
            m.validate_compiled(self.fixture(main_class="com.shale.desktop.MainApp"))

    def test_direct_launcher_configuration_validation(self):
        temporary = Path(tempfile.mkdtemp())
        config = temporary / "Shale.cfg"
        config.write_text(f"[Application]\r\napp.mainclass={m.APPROVED_MAIN_CLASS}\r\n", encoding="utf-8")
        m.validate_launcher_config(config)
        config.write_text("[Application]\napp.mainclass=com.shale.desktop.MainApp\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "app.mainclass is not approved"):
            m.validate_launcher_config(config)

    def test_generated_source_launcher_configuration_validation(self):
        temporary = Path(tempfile.mkdtemp())
        app = temporary / "app"
        app.mkdir()
        lib = app / "lib"
        lib.mkdir()
        config = app / "Shale.cfg"
        config.write_text(f"[Application]\napp.mainclass={m.APPROVED_MAIN_CLASS}\n", encoding="utf-8")
        diagnostic_config = app / m.DIAGNOSTIC_CONFIG
        diagnostic_config.write_text(f"[Application]\napp.mainclass={m.DIAGNOSTIC_MAIN_CLASS}\napp.classpath=$APPDIR\\shale-desktop-1.0.129.jar\napp.classpath=$APPDIR\\lib\\shale-core-1.0.129.jar\n", encoding="utf-8")
        diagnostic_launcher = temporary / m.DIAGNOSTIC_LAUNCHER
        diagnostic_launcher.write_bytes(b"launcher")
        diagnostic_jar = app / "shale-desktop-1.0.129.jar"
        with zipfile.ZipFile(diagnostic_jar, "w") as jar:
            jar.writestr(m.DIAGNOSTIC_CLASS_ENTRY, b"class")
        core_jar = lib / "shale-core-1.0.129.jar"
        with zipfile.ZipFile(core_jar, "w") as jar:
            jar.writestr(m.PRODUCTION_READER_CLASS_ENTRY, b"reader")
        wxs = temporary / "bundle.wxf"
        wxs.write_text(f'''<Wix xmlns="{m.NS}"><Fragment>
          <Directory Id="TARGETDIR"><Directory Id="INSTALLDIR"><Directory Id="app-dir" Name="app"><Directory Id="lib-dir" Name="lib"/></Directory></Directory></Directory>
          <DirectoryRef Id="INSTALLDIR"><Component Id="diagnostic-launcher"><File Id="diagnostic-launcher-file" Source="{diagnostic_launcher}"/></Component></DirectoryRef>
          <DirectoryRef Id="app-dir"><Component Id="config"><File Id="config-file" Source="{config}"/></Component><Component Id="diagnostic-config"><File Id="diagnostic-config-file" Source="{diagnostic_config}"/></Component><Component Id="diagnostic-jar"><File Id="diagnostic-jar-file" Source="{diagnostic_jar}"/></Component></DirectoryRef>
          <DirectoryRef Id="lib-dir"><Component Id="core-jar"><File Id="core-jar-file" Source="{core_jar}"/></Component></DirectoryRef>
        </Fragment></Wix>''', encoding="utf-8")
        m.validate_source(wxs)
        config.write_text("[Application]\napp.mainclass=com.shale.desktop.MainApp\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "app.mainclass is not approved"):
            m.validate_source(wxs)

    def test_repeated_directory_references_with_one_canonical_path_are_accepted(self):
        xml = f'''<Wix xmlns="{m.NS}"><Fragment>
          <DirectoryRef Id="INSTALLDIR"><Directory Id="alias-a"><Directory Id="shared-app" Name="app"/></Directory></DirectoryRef>
          <DirectoryRef Id="INSTALLDIR"><Directory Id="alias-b"><Directory Id="shared-app" Name="app"/></Directory></DirectoryRef>
          <DirectoryRef Id="shared-app"><Component Id="config-component"><File Id="config-file" Source="opaque-Shale.cfg"/></Component></DirectoryRef>
        </Fragment></Wix>'''
        root = ET.fromstring(xml)
        file_node = next(root.iter(m.tag("File")))
        self.assertEqual(("app",), m.installed_directory(root, file_node))

    def test_genuinely_conflicting_canonical_paths_report_element_context(self):
        xml = f'''<Wix xmlns="{m.NS}"><Fragment>
          <DirectoryRef Id="INSTALLDIR"><Directory Id="app-parent" Name="app"><Directory Id="shared" Name="native"/></Directory></DirectoryRef>
          <DirectoryRef Id="INSTALLDIR"><Directory Id="wrong-parent" Name="wrong"><Directory Id="shared" Name="native"/></Directory></DirectoryRef>
          <Component Id="native-component" Directory="shared"><File Id="native-file" Name="shale_windows_toast.dll" Source="opaque-payload"/></Component>
        </Fragment></Wix>'''
        root = ET.fromstring(xml)
        file_node = next(root.iter(m.tag("File")))
        with self.assertRaisesRegex(ValueError, r"conflicting canonical paths.*INSTALLDIR/app/native.*INSTALLDIR/wrong/native.*Id='native-file'.*Component='native-component'.*Directory='shared'"):
            m.installed_directory(root, file_node)

    def test_missing_file_id_fails_with_component_and_source_context(self):
        xml = f'''<Wix xmlns="{m.NS}"><Fragment><DirectoryRef Id="INSTALLDIR">
          <Component Id="broken-component"><File Name="Shale.cfg" Source="opaque-source"/></Component>
        </DirectoryRef></Fragment></Wix>'''
        root = ET.fromstring(xml)
        with self.assertRaisesRegex(ValueError, r"missing required Id.*Name='Shale.cfg'.*Source='opaque-source'.*Component='broken-component'"):
            m.installed_directory(root, next(root.iter(m.tag("File"))))

if __name__ == "__main__":
    unittest.main()

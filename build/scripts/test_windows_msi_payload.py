import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("payload", Path(__file__).with_name("windows_msi_payload.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class CompiledPayloadTest(unittest.TestCase):
    def fixture(self, *, marker_count=1, dll_count=1, launcher_count=1, marker_dir="app",
                dll_dir="native", marker_contents=None, missing_payload=None):
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
        if marker_dir == "app":
            app_marker, wrong_marker = marker_files, ""
        else:
            app_marker, wrong_marker = "", marker_files
        if dll_dir == "native":
            native_dll, wrong_dll = dll_files, ""
        else:
            native_dll, wrong_dll = "", dll_files
        xml = f'''<Wix xmlns="{m.NS}"><Fragment><Directory Id="TARGETDIR"><Directory Id="INSTALLDIR">
          {launcher_files}<Directory Id="app-dir" Name="app">{app_marker}<Directory Id="native-dir" Name="native">{native_dll}</Directory></Directory>
          <Directory Id="wrong-dir" Name="wrong">{wrong_marker}{wrong_dll}</Directory>
        </Directory></Directory></Fragment></Wix>'''
        wxs = temporary / "final.wxs"
        wxs.write_text(xml, encoding="utf-8")
        return wxs

    def test_opaque_dark_sources_and_installed_names_pass(self):
        m.validate(self.fixture())

    def test_marker_cardinality(self):
        for count, message in ((0, "missing installed marker"), (2, "duplicate installed marker")):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, message):
                m.validate(self.fixture(marker_count=count))

    def test_marker_directory_and_extracted_payload(self):
        with self.assertRaisesRegex(ValueError, "installed marker is in the wrong installed directory"):
            m.validate(self.fixture(marker_dir="wrong"))
        with self.assertRaisesRegex(ValueError, "missing extracted installed marker payload"):
            m.validate(self.fixture(missing_payload="shale-windows-toast.properties"))

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
                m.validate(self.fixture(marker_contents=contents))
        duplicate = "\n".join(f"{key}={value}" for key, value in valid.items()) + "\narchitecture=x64\n"
        with self.assertRaisesRegex(ValueError, "duplicate required property architecture"):
            m.validate(self.fixture(marker_contents=duplicate))

    def test_native_dll_cardinality_directory_and_payload(self):
        for count, message in ((0, "missing native DLL"), (2, "duplicate native DLL")):
            with self.subTest(count=count), self.assertRaisesRegex(ValueError, message):
                m.validate(self.fixture(dll_count=count))
        with self.assertRaisesRegex(ValueError, "native DLL is in the wrong installed directory"):
            m.validate(self.fixture(dll_dir="wrong"))
        with self.assertRaisesRegex(ValueError, "missing extracted native DLL payload"):
            m.validate(self.fixture(missing_payload="shale_windows_toast.dll"))

    def test_launcher_is_mandatory_and_extracted(self):
        with self.assertRaisesRegex(ValueError, "missing launcher"):
            m.validate(self.fixture(launcher_count=0))
        with self.assertRaisesRegex(ValueError, "missing extracted launcher payload"):
            m.validate(self.fixture(missing_payload="Shale.exe"))

if __name__ == "__main__":
    unittest.main()

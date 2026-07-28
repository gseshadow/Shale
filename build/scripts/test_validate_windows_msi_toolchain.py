import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("toolchain", Path(__file__).with_name("validate_windows_msi_toolchain.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class WindowsMsiToolchainValidationTest(unittest.TestCase):
    def tool_path(self, name="candle.exe"):
        directory = Path(tempfile.mkdtemp()) / "Program Files (x86)" / "WiX Toolset v3.14" / "bin"
        directory.mkdir(parents=True)
        path = directory / name
        path.write_text("fixture", encoding="utf-8")
        return path

    def test_valid_wix_v3_tool_under_spaces_and_parentheses_passes(self):
        descriptions = {
            "candle.exe": "Compiler",
            "light.exe": "Linker",
            "dark.exe": "Decompiler",
        }
        for name, description in descriptions.items():
            with self.subTest(name=name):
                path = self.tool_path(name)
                result = subprocess.CompletedProcess([str(path), "-?"], 0,
                        f"Windows Installer XML Toolset {description} version 3.14.1", "")
                m.validate_tool(name, resolver=lambda _: str(path), runner=lambda *args, **kwargs: result)

    def test_legitimate_nonzero_help_exit_passes_when_v3_signature_is_present(self):
        path = self.tool_path("light.exe")
        result = subprocess.CompletedProcess([str(path), "-?"], 1, "Windows Installer XML Toolset Linker version 3.14.1", "")
        m.validate_tool("light.exe", resolver=lambda _: str(path), runner=lambda *args, **kwargs: result)

    def test_missing_tool_names_resolution_context(self):
        with self.assertRaisesRegex(ValueError, "tool=dark.exe classification=missing resolved=<not-found> check=PATH-resolution"):
            m.validate_tool("dark.exe", resolver=lambda _: None)

    def test_incompatible_and_non_runnable_tools_include_path_and_exit(self):
        path = self.tool_path("dark.exe")
        incompatible = subprocess.CompletedProcess([str(path), "-?"], 9, "unrelated program", "")
        with self.assertRaisesRegex(ValueError, r"classification=incompatible_version.*WiX Toolset v3\.14.*exit=9"):
            m.validate_tool("dark.exe", resolver=lambda _: str(path), runner=lambda *args, **kwargs: incompatible)
        def cannot_run(*args, **kwargs):
            raise OSError(193, "not executable")
        with self.assertRaisesRegex(ValueError, r"classification=invocation_failure.*WiX Toolset v3\.14.*exit=193"):
            m.validate_tool("dark.exe", resolver=lambda _: str(path), runner=cannot_run)

if __name__ == "__main__":
    unittest.main()

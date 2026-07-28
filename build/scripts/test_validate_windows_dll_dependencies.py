import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("validator", Path(__file__).with_name("validate_windows_dll_dependencies.py"))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)

class DllDependencyValidationTest(unittest.TestCase):
    def test_accepts_windows_system_dependencies(self):
        names = validator.dependencies("  KERNEL32.dll\n  combase.dll\n  api-ms-win-core-winrt-l1-1-0.dll\n")
        validator.validate(names)

    def test_rejects_dynamic_visual_cpp_runtime(self):
        for dependency in ("VCRUNTIME140.dll", "MSVCP140.dll", "CONCRT140.dll"):
            with self.subTest(dependency=dependency), self.assertRaises(ValueError):
                validator.validate({"kernel32.dll", dependency.lower()})

    def test_rejects_unknown_bundled_or_third_party_dependency(self):
        with self.assertRaises(ValueError):
            validator.validate({"kernel32.dll", "unexpected.dll"})

    def test_rejects_empty_dumpbin_result(self):
        with self.assertRaises(ValueError):
            validator.validate(set())

if __name__ == "__main__":
    unittest.main()

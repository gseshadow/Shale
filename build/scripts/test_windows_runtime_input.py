from pathlib import Path
import tempfile
import unittest
try:
    from windows_runtime_input import FORBIDDEN, stage, validate_paths
except ModuleNotFoundError:
    from build.scripts.windows_runtime_input import FORBIDDEN, stage, validate_paths

class RuntimeInputTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.target, self.output = self.root / "target", self.root / "input"
        for name in ("shale-desktop-1.0.137.jar", "lib/dependency.jar", "updater/ShaleUpdater.exe",
                     "updater/runtime/legal/java.base/LICENSE", "shale-installed-version.properties", "NOTICE.txt"):
            path = self.target / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("runtime")

    def test_runtime_allowlist_excludes_build_outputs_and_stale_stage(self):
        for name in FORBIDDEN:
            path = self.target / name / "unwanted.txt"
            path.parent.mkdir(parents=True)
            path.write_text("build")
        (self.target / "original-shale-desktop-1.0.137.jar").write_text("build")
        self.output.mkdir()
        (self.output / "stale.jar").write_text("stale")
        stage(self.target, self.output, "1.0.137")
        files = {path.relative_to(self.output).as_posix() for path in self.output.rglob("*") if path.is_file()}
        self.assertEqual({"shale-desktop-1.0.137.jar", "lib/dependency.jar", "updater/ShaleUpdater.exe",
                          "updater/runtime/legal/java.base/LICENSE", "shale-installed-version.properties", "NOTICE.txt"}, files)

    def test_rejects_missing_dependencies_or_updater(self):
        (self.target / "updater/ShaleUpdater.exe").unlink()
        with self.assertRaisesRegex(ValueError, "missing"):
            stage(self.target, self.output, "1.0.137")

    def test_rejects_nested_development_payload_and_test_jars(self):
        for name in ("app/test-classes/Test.class", "runtime/MAVEN-STATUS/state", "lib/library-tests.jar"):
            with self.assertRaisesRegex(ValueError, "development-only"):
                validate_paths([Path(name)])
        (self.target / "lib/dependency-tests.jar").write_text("test")
        with self.assertRaisesRegex(ValueError, "development-only"):
            stage(self.target, self.output, "1.0.137")

    def test_staging_cannot_delete_source_or_contain_source(self):
        for destination in (self.target, self.target / "stage", self.root):
            with self.assertRaisesRegex(ValueError, "separate trees"):
                stage(self.target, destination, "1.0.137")
        self.assertTrue((self.target / "shale-desktop-1.0.137.jar").is_file())

if __name__ == "__main__":
    unittest.main()

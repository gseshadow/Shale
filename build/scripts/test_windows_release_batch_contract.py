from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class WindowsReleaseBatchContractTest(unittest.TestCase):
    def test_msi_toolchain_check_avoids_nested_cmd_metacharacter_parsing(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        self.assertNotIn("2^>^&1", source)
        self.assertNotIn("|| (", source)
        self.assertIn('java -version >"%JDK_VERSION_LOG%" 2>&1', source)
        self.assertIn('findstr /r /c:"^openjdk version .*21[.]" /c:"^java version .*21[.]" "%JDK_VERSION_LOG%" >nul', source)
        self.assertNotIn('findstr /r /c:"version \\"21\\."', source)
        self.assertNotIn("findstr /r ^>nul", source)
        findstr_line = next(line for line in source.splitlines() if line.startswith("findstr "))
        self.assertTrue(findstr_line.endswith('"%JDK_VERSION_LOG%" >nul'))
        self.assertNotIn('" ^>nul', findstr_line)
        self.assertIn("if errorlevel 1 goto :missing_jdk21", source)
        self.assertIn("call :require_tool candle.exe 12", source)
        self.assertIn("call :require_tool light.exe 13", source)
        self.assertIn("call :require_tool dark.exe 14", source)
        self.assertIn('call "%ROOT%\\build\\native\\windows-toast\\build-native.bat"', source)
        self.assertIn("candle.exe -nologo", source)
        self.assertIn("light.exe -nologo", source)
        self.assertIn("dark.exe -x", source)
        self.assertIn("windows_msi_identity.py\" validate", source)
        self.assertIn('move /y "%ROOT%\\dist\\Shale-%VERSION%.msi.new"', source)

    def test_release_reports_the_first_post_updater_stage(self):
        source = (ROOT / "build/scripts/build-shale-release.bat").read_text(encoding="utf-8")
        updater = source.index("build-updater.bat")
        desktop_stage = source.index("echo Building desktop application image...")
        jpackage = source.index("jpackage", desktop_stage)
        self.assertLess(updater, desktop_stage)
        self.assertLess(desktop_stage, jpackage)

    def test_updater_packages_only_the_staged_runtime_jar(self):
        source = (ROOT / "build/scripts/build-updater.bat").read_text(encoding="utf-8")
        self.assertIn("set UPDATER_INPUT=%ROOT%\\build\\staging\\updater-input", source)
        self.assertIn('copy /y "%UPDATER_TARGET%\\shale-updater-%VERSION%.jar"', source)
        self.assertIn('--input "%UPDATER_INPUT%"', source)
        self.assertNotIn('--input "%UPDATER_TARGET%"', source)

    def test_native_dependency_report_parent_exists_before_redirection(self):
        source = (ROOT / "build/native/windows-toast/build-native.bat").read_text(encoding="utf-8")
        mkdir = source.index('if not exist "%DEPENDENCY_DIR%" mkdir "%DEPENDENCY_DIR%"')
        redirect = source.index('dumpbin.exe /nologo /dependents "%DLL_PATH%" >"%DEPENDENCIES%"')
        self.assertLess(mkdir, redirect)
        self.assertIn("set DEPENDENCY_DIR=%ROOT%\\build\\staging\\windows-toast-dependencies", source)
        self.assertNotIn("set DEPENDENCIES=%TEMP%", source)
        self.assertIn('if not exist "%DLL_PATH%" goto :missing_dll', source)
        self.assertIn('echo Compiled native DLL was not found: "%DLL_PATH%"', source)

    def test_native_outputs_and_required_stages_have_explicit_order(self):
        native = (ROOT / "build/native/windows-toast/build-native.bat").read_text(encoding="utf-8")
        stages = [
            "Native DLL compilation completed.",
            "Native DLL location verified:",
            "Starting dumpbin dependency inspection...",
            "Dependency validation passed.",
        ]
        positions = [native.index(stage) for stage in stages]
        self.assertEqual(positions, sorted(positions))
        self.assertIn('/Fo"%BUILD_DIR%\\shale_windows_toast.obj"', native)
        self.assertIn('/IMPLIB:"%BUILD_DIR%\\shale_windows_toast.lib"', native)
        self.assertIn('/PDB:"%BUILD_DIR%\\shale_windows_toast.pdb"', native)
        msi = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        self.assertLess(msi.index("build-native.bat"), msi.index("Starting jpackage and WiX MSI construction..."))

if __name__ == "__main__":
    unittest.main()

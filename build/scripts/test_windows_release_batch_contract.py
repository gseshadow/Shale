from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class WindowsReleaseBatchContractTest(unittest.TestCase):
    def test_full_release_requires_and_forwards_boolean_mandatory_flag(self):
        source = (ROOT / "build/scripts/release-all.bat").read_text(encoding="utf-8")
        self.assertIn('if "%~2"=="" goto :usage', source)
        self.assertIn('if not "%~3"=="" goto :usage', source)
        self.assertIn('if /I not "%MANDATORY_UPDATE%"=="true" if /I not "%MANDATORY_UPDATE%"=="false" goto :invalid_mandatory', source)
        self.assertIn('set "MANDATORY_UPDATE=%~2"', source)
        self.assertNotIn("set MANDATORY_UPDATE=false", source)
        self.assertIn('call "%DOWNSTREAM_SCRIPT%" "%VERSION%" "%MANDATORY_UPDATE%"', source)
        for downstream in ("release-and-publish.bat", "release.bat"):
            text = (ROOT / "build/scripts" / downstream).read_text(encoding="utf-8")
            self.assertIn('set "MANDATORY_UPDATE=%~2"', text)
            self.assertIn('if /I not "%MANDATORY_UPDATE%"=="true" if /I not "%MANDATORY_UPDATE%"=="false"', text)

    def test_full_release_windows_stage_is_rooted_diagnostic_and_fail_closed(self):
        source = (ROOT / "build/scripts/release-all.bat").read_text(encoding="utf-8")
        self.assertIn('for %%I in ("%~dp0..\\..") do set "ROOT=%%~fI"', source)
        self.assertIn('set "DOWNSTREAM_SCRIPT=%SCRIPT_DIR%\\release-and-publish.bat"', source)
        self.assertIn('cd /d "%ROOT%" || goto :root_unavailable', source)
        for diagnostic in (
                "Windows stage repository root:", "Windows stage downstream script:",
                "Windows stage working directory:", "Windows stage version:",
                "Windows stage mandatory flag:", "Windows stage Mac ZIP:",
                "Windows stage Mac metadata:"):
            self.assertIn(diagnostic, source)
        self.assertIn('if not exist "%DOWNSTREAM_SCRIPT%" goto :missing_downstream', source)
        self.assertIn('echo Windows downstream release script was not found: "%DOWNSTREAM_SCRIPT%"', source)
        call = source.index('call "%DOWNSTREAM_SCRIPT%"')
        failure = source.index("if errorlevel 1 goto :windows_stage_failed", call)
        complete = source.index("Full release complete", failure)
        self.assertLess(call, failure)
        self.assertLess(failure, complete)

    def test_release_script_paths_are_caller_independent_and_space_safe(self):
        for filename in ("release-all.bat", "release-and-publish.bat", "release.bat", "build-shale-release.bat"):
            source = (ROOT / "build/scripts" / filename).read_text(encoding="utf-8")
            self.assertIn("%~dp0", source)
            self.assertIn('set "ROOT=', source)
        for filename in ("release-and-publish.bat", "release.bat", "build-shale-release.bat"):
            source = (ROOT / "build/scripts" / filename).read_text(encoding="utf-8")
            self.assertIn('set "SCRIPT_DIR=%~dp0"', source)

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
        self.assertIn('set "VALIDATOR=%~dp0validate_windows_msi_toolchain.py"', source)
        self.assertIn('python "%VALIDATOR%"', source)
        self.assertIn('if errorlevel 1 goto :wix_toolchain_failed', source)
        self.assertIn('if not defined JAVA_HOME for %%J in ("!JAVA_PATH!") do set "JAVA_BIN=%%~dpJ"', source)
        self.assertIn('if not defined JAVA_HOME for %%H in ("!JAVA_BIN!..") do set "JAVA_HOME=%%~fH"', source)
        self.assertIn('if not exist "!JAVA_HOME!\\include\\jni.h" goto :missing_jni_headers', source)
        self.assertIn('Toolchain check tool=JNI resolved="!JAVA_HOME!\\include\\jni.h" check=header-exists', source)
        self.assertIn('echo Toolchain validator: "%VALIDATOR%"', source)
        self.assertIn('classification=WiX-toolchain exit=%WIX_VALIDATION_EXIT%', source)
        self.assertNotIn("call :require_tool", source)
        self.assertIn('set "NATIVE_BUILD_SCRIPT=%ROOT%\\build\\native\\windows-toast\\build-native.bat"', source)
        self.assertIn('call "%NATIVE_BUILD_SCRIPT%"', source)
        self.assertIn('--main-class com.shale.desktop.ShaleLauncher', source)
        self.assertNotIn('--main-class com.shale.desktop.MainApp', source)
        self.assertIn("candle.exe -nologo", source)
        self.assertIn("light.exe -nologo", source)
        self.assertIn("dark.exe -x", source)
        self.assertIn("windows_msi_identity.py\" validate", source)
        self.assertIn('move /y "%ROOT%\\dist\\Shale-%VERSION%.msi.new"', source)

    def test_wix_validator_diagnostics_precede_msi_construction_and_publication(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        validation = source.index('python "%VALIDATOR%"')
        failure = source.index('if errorlevel 1 goto :wix_toolchain_failed', validation)
        construction = source.index("Starting jpackage and WiX MSI construction...", failure)
        publication = source.index("Final MSI publication completed:", construction)
        self.assertEqual([validation, failure, construction, publication], sorted([validation, failure, construction, publication]))
        validator = (ROOT / "build/scripts/validate_windows_msi_toolchain.py").read_text(encoding="utf-8")
        for tool in ("candle.exe", "light.exe", "dark.exe"):
            self.assertIn(tool, validator)
        for diagnostic in ("classification=missing", "classification=invocation_failure", "classification=incompatible_version", "exit="):
            self.assertIn(diagnostic, validator)

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
        redirect = source.index('"!DUMPBIN_PATH!" /nologo /dependents "%DLL_PATH%" >"%DEPENDENCIES%"')
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

    def test_generated_wix_source_and_objects_use_authoritative_directories(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        self.assertIn("set JPACKAGE_TEMP=%STAGE%\\jpackage-temp", source)
        self.assertIn("set GENERATED_CONFIG_DIR=%JPACKAGE_TEMP%\\config", source)
        self.assertIn("set BUNDLE_SOURCE=%GENERATED_CONFIG_DIR%\\bundle.wxf", source)
        self.assertIn("set WIXOBJ_DIR=%JPACKAGE_TEMP%\\wixobj", source)
        self.assertIn("set BUNDLE_WIXOBJ=%WIXOBJ_DIR%\\bundle.wixobj", source)
        self.assertNotIn("%WIXOBJ_DIR%\\bundle.wxf", source)
        self.assertNotIn('for /r "%JPACKAGE_TEMP%" %%F in (bundle.wxf)', source)

        exists = source.index('if not exist "%BUNDLE_SOURCE%" goto :missing_bundle')
        inspect = source.index('windows_msi_identity.py" inspect "%BUNDLE_SOURCE%"')
        mutate = source.index('windows_msi_identity.py" mutate "%BUNDLE_SOURCE%"')
        candle = source.index('candle.exe -nologo "%BUNDLE_SOURCE%"')
        light = source.index("Final light.exe reconstruction started.")
        dark = source.index("dark.exe -x")
        compiled_validation = source.index('windows_msi_identity.py" validate')
        publish = source.index('move /y "%ROOT%\\dist\\Shale-%VERSION%.msi.new"')
        self.assertEqual([exists, inspect, mutate, candle, light, dark, compiled_validation, publish],
                         sorted([exists, inspect, mutate, candle, light, dark, compiled_validation, publish]))

    def test_msi_stages_are_distinct_and_fail_closed(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        for message in (
            "Preliminary jpackage MSI completed.",
            "Generated bundle.wxf location verified:",
            "Generated WiX identity validation passed.",
            "bundle.wxf identity and shortcut mutation completed.",
            "Modified bundle.wxf recompilation started.",
            "Recompiled bundle.wixobj verified:",
            "Final WiX link inputs verified:",
            "Final light.exe reconstruction started.",
            "Final light.exe reconstruction completed.",
            "Compiled final MSI validation started.",
            "dark.exe inspection completed.",
            "Final identity and payload validation passed.",
            "Final MSI publication completed:",
        ):
            self.assertIn(message, source)
        self.assertIn('echo Generated bundle.wxf was not found at expected path: "%BUNDLE_SOURCE%"', source)

    def test_final_light_reuses_complete_jpackage_link_contract(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        light = next(line for line in source.splitlines() if line.startswith("light.exe "))
        self.assertIn("-ext WixUtilExtension", light)
        self.assertIn("-ext WixUIExtension", light)
        self.assertIn('-b "%GENERATED_CONFIG_DIR%"', light)
        self.assertNotIn("-sice:ICE61", source)
        self.assertNotIn("-sice:ICE69", source)
        for variable, filename in (
            ("MAIN_WIXOBJ", "main.wixobj"),
            ("UI_WIXOBJ", "ui.wixobj"),
            ("INSTALLDIR_DIALOG_WIXOBJ", "InstallDirNotEmptyDlg.wixobj"),
            ("BUNDLE_WIXOBJ", "bundle.wixobj"),
        ):
            self.assertIn(f"set {variable}=%WIXOBJ_DIR%\\{filename}", source)
            self.assertIn(f'if not exist "%{variable}%"', source)
        self.assertIn('for %%F in ("%WIXOBJ_DIR%\\*.wixobj")', source)
        self.assertIn("if not !BUNDLE_OBJECT_COUNT! EQU 1 goto :duplicate_bundle_object", source)
        self.assertIn('candle.exe -nologo "%BUNDLE_SOURCE%"', source)
        self.assertIn("if errorlevel 1 goto :candle_failed", source)
        self.assertIn("if errorlevel 1 goto :light_failed", source)

    def test_final_light_requires_localization_binding_and_post_link_validation(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        for variable, filename in (
            ("LOC_DE", "MsiInstallerStrings_de.wxl"),
            ("LOC_EN", "MsiInstallerStrings_en.wxl"),
            ("LOC_JA", "MsiInstallerStrings_ja.wxl"),
            ("LOC_ZH_CN", "MsiInstallerStrings_zh_CN.wxl"),
        ):
            self.assertIn(f"set {variable}=%GENERATED_CONFIG_DIR%\\{filename}", source)
            self.assertIn(f'if not exist "%{variable}%"', source)
        light = source.index("Final light.exe reconstruction started.")
        dark = source.index("dark.exe -x", light)
        identity = source.index('windows_msi_identity.py" validate', dark)
        payload = source.index('windows_msi_payload.py" compiled "%STAGE%\\dark\\final.wxs"', identity)
        publish = source.index('move /y "%ROOT%\\dist\\Shale-%VERSION%.msi.new"', payload)
        self.assertEqual([light, dark, identity, payload, publish], sorted([light, dark, identity, payload, publish]))
        self.assertIn('windows_msi_payload.py" compiled "%STAGE%\\dark\\final.wxs"', source)
        self.assertIn('if errorlevel 1 goto :compiled_payload_failed', source)
        self.assertNotIn('dir /s /b "%STAGE%\\dark\\payload', source)
        self.assertNotIn('copy /y "%PRELIMINARY_MSI%"', source)

    def test_packaged_entrypoints_use_plain_bootstrap_and_configs_are_validated(self):
        release = (ROOT / "build/scripts/build-shale-release.bat").read_text(encoding="utf-8")
        msi = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        macos = (ROOT / "build/scripts/build-shale-macos.sh").read_text(encoding="utf-8")
        launcher = (ROOT / "shale-desktop/src/main/java/com/shale/desktop/ShaleLauncher.java").read_text(encoding="utf-8")
        for source in (release, msi, macos):
            self.assertIn("--main-class", source)
            self.assertIn("com.shale.desktop.ShaleLauncher", source)
            self.assertNotIn("--main-class com.shale.desktop.MainApp", source)
            self.assertNotIn('--main-class "com.shale.desktop.MainApp"', source)
        self.assertIn("public final class ShaleLauncher", launcher)
        self.assertNotIn("extends Application", launcher)
        self.assertIn("public static void main(String[] args)", launcher)
        self.assertIn("MainApp.main(args);", launcher)
        self.assertIn('windows_msi_payload.py" config "%DIST_APP%\\Shale\\app\\Shale.cfg"', release)
        source_validation = msi.index('windows_msi_payload.py" source "%BUNDLE_SOURCE%"')
        final_validation = msi.index('windows_msi_payload.py" compiled "%STAGE%\\dark\\final.wxs"')
        publish = msi.index('move /y "%ROOT%\\dist\\Shale-%VERSION%.msi.new"')
        self.assertLess(source_validation, final_validation)
        self.assertLess(final_validation, publish)

    def test_successful_toolchain_reaches_named_native_stage_without_stale_errorlevel(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        success = source.index("Windows MSI stage completed: toolchain-validation")
        staging = source.index("Windows MSI stage started: staging", success)
        native = source.index("Windows MSI stage started: native-DLL", staging)
        call = source.index('call "%NATIVE_BUILD_SCRIPT%"', native)
        capture = source.index('set "NATIVE_BUILD_EXIT=!ERRORLEVEL!"', call)
        check = source.index('if not "!NATIVE_BUILD_EXIT!"=="0" goto :native_build_failed', capture)
        self.assertEqual([success, staging, native, call, capture, check], sorted([success, staging, native, call, capture, check]))
        self.assertNotIn('build-native.bat" "%APPINPUT%\\native" || exit /b', source)

    def test_every_post_toolchain_operation_has_fail_closed_stage_diagnostics(self):
        source = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text(encoding="utf-8")
        stages = (
            "staging", "native-DLL", "marker-staging", "preliminary-jpackage",
            "generated-payload-validation", "generated-identity-validation",
            "generated-identity-mutation", "candle-recompile", "light-reconstruction",
            "dark-extraction", "compiled-identity-validation", "compiled-payload-validation",
            "artifact-finalization",
        )
        toolchain = source.index("Windows MSI stage completed: toolchain-validation")
        publish = source.index("Final MSI publication completed:", toolchain)
        for stage in stages:
            self.assertIn(f"stage={stage}", source)
            self.assertLess(toolchain, source.index(f"stage={stage}"))
        self.assertIn('echo Windows MSI stage failed:', source)
        self.assertIn('exit=!NATIVE_BUILD_EXIT!', source)
        self.assertGreater(publish, source.index("Windows MSI stage started: compiled-payload-validation"))

if __name__ == "__main__":
    unittest.main()

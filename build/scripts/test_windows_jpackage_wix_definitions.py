from pathlib import Path
import tempfile
import unittest

import windows_jpackage_wix_definitions as subject


CORE_ARGUMENTS = (
    "-dJpAppVersion=1.0.128, "
    "-dJpProductCode={generated-product}, "
    "-dJpProductUpgradeCode={generated-upgrade}, "
    "-dJpAppName=Shale, "
    r"-dJpConfigDir=C:\Program Files\Shale\config"
)

PRODUCT_CODE = "600556c4-4d9c-332a-8728-fe3138d5161a"
UPGRADE_CODE = "e66c3164-cac0-3da4-bbf7-ad0299059575"
WORKSPACE = r"C:\Eclipse\Workspace\Shale Release"


def real_command(source: str = "main.wxs") -> str:
    return (
        "[17:11:24.618] Command [PID: 30588]:\n"
        "    candle.exe -nologo "
        rf"{WORKSPACE}\build\staging\windows-msi\jpackage-temp\config\{source} "
        "-ext WixUtilExtension -arch x64 "
        rf"-out {WORKSPACE}\build\staging\windows-msi\jpackage-temp\wixobj\main.wixobj "
        "-dJpAppDescription=Shale Desktop "
        "-dJpAppVersion=1.0.128 "
        f"-dJpProductCode={PRODUCT_CODE} "
        "-dJpAppName=Shale "
        "-dJpAllowUpgrades=yes "
        f"-dJpProductUpgradeCode={UPGRADE_CODE} "
        "-dJpAllowDowngrades=yes "
        rf"-dJpIcon={WORKSPACE}\build\staging\windows-msi\app-image\Shale.exe "
        "-dJpAppVendor=Get Downing "
        "-dJpAppSizeKb=245560 "
        rf"-dJpConfigDir={WORKSPACE}\build\staging\windows-msi\jpackage-temp\config"
    )


def command(*extra: str) -> str:
    definitions = ", ".join(extra)
    if definitions:
        definitions += ", "
    return (
        r"[10:02:03.000] Running [C:\Program Files\WiX Toolset v3.14\bin\candle.exe, -nologo, "
        + definitions + CORE_ARGUMENTS
        + r", C:\build\jpackage-temp\config\main.wxs, -ext, WixUtilExtension, -arch, x64]"
    )


class WindowsJpackageWixDefinitionsTest(unittest.TestCase):
    def test_parses_real_jdk21_command_record_and_preserves_spaced_values(self):
        recovered = subject.definitions(real_command())
        self.assertEqual("Shale Desktop", recovered["JpAppDescription"])
        self.assertEqual("Get Downing", recovered["JpAppVendor"])
        self.assertEqual(
            WORKSPACE + r"\build\staging\windows-msi\app-image\Shale.exe",
            recovered["JpIcon"],
        )
        self.assertEqual(
            WORKSPACE + r"\build\staging\windows-msi\jpackage-temp\config",
            recovered["JpConfigDir"],
        )
        self.assertEqual(PRODUCT_CODE, recovered["JpProductCode"])
        self.assertEqual(UPGRADE_CODE, recovered["JpProductUpgradeCode"])

    def test_real_log_selects_only_generated_main_source(self):
        log = "\n".join((
            real_command("bundle.wxf"),
            real_command("ui.wxf"),
            real_command(),
            real_command("InstallDirNotEmptyDlg.wxs"),
        ))
        self.assertEqual(PRODUCT_CODE, subject.definitions(log)["JpProductCode"])

    def test_locates_main_command_and_recovers_only_definitions_actually_supplied(self):
        log = "\n".join((
            r"Running [C:\wix\candle.exe, -dJpIgnored=bundle, C:\temp\bundle.wxf]",
            command("-dJpAppDescription=Shale Desktop", "-dJpAppVendor=Get Downing"),
        ))
        self.assertEqual(
            {
                "JpAppDescription": "Shale Desktop",
                "JpAppVendor": "Get Downing",
                "JpAppVersion": "1.0.128",
                "JpProductCode": "{generated-product}",
                "JpProductUpgradeCode": "{generated-upgrade}",
                "JpAppName": "Shale",
                "JpConfigDir": r"C:\Program Files\Shale\config",
            },
            subject.definitions(log),
        )

    def test_optional_source_references_are_not_required_when_command_omits_them(self):
        recovered = subject.definitions(command())
        for optional in ("JpAboutURL", "JpHelpURL", "JpUpdateURL"):
            self.assertNotIn(optional, recovered)

    def test_generated_product_identities_come_from_original_command(self):
        recovered = subject.definitions(command())
        self.assertEqual("{generated-product}", recovered["JpProductCode"])
        self.assertEqual("{generated-upgrade}", recovered["JpProductUpgradeCode"])

    def test_response_arguments_preserve_paths_with_spaces(self):
        argument = r"-dJpIcon=C:\Program Files\Eclipse Adoptium\Shale.exe"
        self.assertEqual(r'"-dJpIcon=C:\Program Files\Eclipse Adoptium\Shale.exe"',
                         subject.quote_response_argument(argument))

    def test_missing_main_invocation_fails_with_clear_diagnostic(self):
        with self.assertRaisesRegex(ValueError, "invocation for generated main.wxs was not found or parseable"):
            subject.definitions(r"Running [C:\wix\candle.exe, -dJpAppName=Shale, bundle.wxf]")

    def test_plain_command_without_jdk_command_header_fails_with_clear_diagnostic(self):
        with self.assertRaisesRegex(ValueError, "invocation for generated main.wxs was not found or parseable"):
            subject.definitions(r"Command: candle.exe -dJpAppName=Shale main.wxs")

    def test_multiple_real_main_invocations_fail_with_clear_diagnostic(self):
        with self.assertRaisesRegex(ValueError, "expected one .*; found 2"):
            subject.definitions(real_command() + "\n" + real_command())

    def test_missing_core_definition_fails_with_its_name(self):
        incomplete = command().replace("-dJpProductCode={generated-product}, ", "")
        with self.assertRaisesRegex(ValueError, r"missing core definition\(s\): JpProductCode"):
            subject.definitions(incomplete)

    def test_prepare_writes_recovered_command_arguments_in_original_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            log = root / "jpackage.log"
            response = root / "definitions.rsp"
            log.write_text(command("-dJpAppDescription=Shale Desktop"), encoding="utf-8")
            subject.prepare(log, response)
            arguments = response.read_text(encoding="utf-8").splitlines()
            self.assertEqual('"-dJpAppDescription=Shale Desktop"', arguments[0])
            self.assertIn('"-dJpProductCode={generated-product}"', arguments)
            self.assertIn('"-dJpProductUpgradeCode={generated-upgrade}"', arguments)
            self.assertIn(r'"-dJpConfigDir=C:\Program Files\Shale\config"', arguments)

    def test_prepare_real_log_creates_response_file_with_semantic_spaces_intact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            log = root / "jpackage-verbose.log"
            response = root / "jpackage-main-definitions-test.rsp"
            log.write_text(real_command(), encoding="utf-8")

            subject.prepare(log, response)

            arguments = response.read_text(encoding="utf-8").splitlines()
            self.assertIn('"-dJpAppDescription=Shale Desktop"', arguments)
            self.assertIn('"-dJpAppVendor=Get Downing"', arguments)
            self.assertIn(f'"-dJpProductCode={PRODUCT_CODE}"', arguments)
            self.assertIn(f'"-dJpProductUpgradeCode={UPGRADE_CODE}"', arguments)
            self.assertIn(
                f'"-dJpConfigDir={WORKSPACE}\\build\\staging\\windows-msi\\jpackage-temp\\config"',
                arguments,
            )


if __name__ == "__main__":
    unittest.main()

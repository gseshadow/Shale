from pathlib import Path
import tempfile
import unittest

import windows_jpackage_wix_definitions as subject


class WindowsJpackageWixDefinitionsTest(unittest.TestCase):
    def test_discovers_all_references_and_uses_authoritative_logged_values(self):
        source = '<Product Id="$(var.JpProductCode)" UpgradeCode="$(var.JpProductUpgradeCode)" Name="$(var.JpAppName)" />'
        log = ('Command [42]: [candle.exe, -dJpAppName=Shale Desktop, '
               '-dJpProductCode=generated-product, -dJpProductUpgradeCode=generated-upgrade, main.wxs]')
        self.assertEqual(
            {
                "JpAppName": "Shale Desktop",
                "JpProductCode": "generated-product",
                "JpProductUpgradeCode": "generated-upgrade",
            },
            subject.definitions(source, log),
        )

    def test_response_arguments_preserve_paths_with_spaces(self):
        argument = r'-dJpIcon=C:\Program Files\Eclipse Adoptium\Shale.exe'
        self.assertEqual(r'"-dJpIcon=C:\Program Files\Eclipse Adoptium\Shale.exe"',
                         subject.quote_response_argument(argument))

    def test_unknown_missing_reference_fails_closed_with_its_name(self):
        with self.assertRaisesRegex(ValueError, r"missing jpackage WiX definition\(s\): JpFutureValue"):
            subject.definitions('<Property Value="$(var.JpFutureValue)" />',
                                'Command: candle.exe -dJpAppName=Shale main.wxs')

    def test_prepare_writes_every_definition_as_a_quoted_candle_argument(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "main.wxs"
            log = root / "jpackage.log"
            response = root / "definitions.rsp"
            source.write_text('$(var.JpProductCode) $(var.JpConfigDir)', encoding="utf-8")
            log.write_text(
                r'Command: candle.exe -dJpProductCode=generated-code -dJpConfigDir=C:\Program Files\Shale -ext WixUtilExtension',
                encoding="utf-8",
            )
            subject.prepare(source, log, response)
            self.assertEqual(
                ['"-dJpConfigDir=C:\\Program Files\\Shale"', '"-dJpProductCode=generated-code"'],
                response.read_text(encoding="utf-8").splitlines(),
            )


if __name__ == "__main__":
    unittest.main()

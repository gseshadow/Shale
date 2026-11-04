"""Offline launcher safety contracts. No Azure, credentials, SQL engine or network access."""
from pathlib import Path
import hashlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CatalogComponentLauncherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (ROOT / 'docs/testing/native-azure-readonly-operator-runbook.md').read_text(encoding='utf-8')

    def block(self, number):
        section = re.search(rf'(?ms)^## {number}\. .*?(?=^## |\Z)', self.text)
        self.assertIsNotNone(section, 'Required operator section is missing')
        return re.search(r'(?s)```powershell\n(.*?)```', section.group()).group(1)

    def launcher(self):
        section = self.text.split('## Reviewed live launcher', 1)[1]
        return re.search(r'(?s)```powershell\n(.*?)```', section).group(1)

    def test_fresh_resource_and_exact_cleanup_blocks_are_unchanged(self):
        for number, expected in [(2, '7e435b864701c75167ba00f0e28e0383ac2513fc1169eaeb4160efe90b3c2b9e'),
                                 (5, '6bffe7d3773450cb4ef1e8e08110175ecc00abc79529c2165ac00b8970b672e2')]:
            self.assertEqual(expected, hashlib.sha256(self.block(number).encode()).hexdigest(),
                             'Resource creation/TLS/firewall/cleanup safeguards must remain exact')

    def test_runtime_credential_generation_remains_exact(self):
        block = re.search(r'(?s)# BEGIN OFFLINE-TESTED CONTAINED CREDENTIALS\n(.*?)# END OFFLINE-TESTED CONTAINED CREDENTIALS', self.text).group(1)
        self.assertEqual('44a35302619c3617e045a1c5d359931409591c51dba463b79c2bc91e91aa2b74', hashlib.sha256(block.encode()).hexdigest())

    def test_live_digest_covers_the_executed_blocks(self):
        launcher = self.launcher()
        self.assertIn('foreach ($Number in @(2,3,5))', launcher)
        expected = hashlib.sha256('\n'.join(self.block(n) for n in (2, 3, 5)).encode()).hexdigest()
        self.assertIn(f"if ($LiveDigest -cne '{expected}')", launcher)
        self.assertNotIn('. $LiveBlocks[4]', launcher, 'This attempt may only compare components after setup')
        self.assertRegex(launcher, r'(?s)try \{\s*\. \$LiveBlocks\[2\].*?\. \$LiveBlocks\[3\].*?finally \{.*?\. \$LiveBlocks\[5\]')
        self.assertIn('AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT', launcher)
        self.assertLess(launcher.index('AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT'),
                        launcher.index('Remove-Item -LiteralPath $ReaderValidationPath -ErrorAction Stop'))

    def test_validation_is_same_source_same_host_and_consumed_before_creation(self):
        launcher = self.launcher()
        validation = self.block(1)
        baseline = '5b88402940fc9950228eb5320bed9b37fc64e359'
        self.assertIn(f"$ExecutableHead = '{baseline}'", validation)
        self.assertIn(f"$ValidatedHead = '{baseline}'", launcher)
        self.assertIn('$ValidatedSourceHead -cne $ReviewedPacketHead', validation)
        self.assertIn('sourceHead=$ValidatedSourceHead', validation)
        self.assertIn('codeHead=$ExecutableHead', validation)
        self.assertNotRegex(validation, r'(?m)^\s*(mvn|.*select_tests\.py|.*New-Az)\b')
        for block in (validation, launcher):
            for guard in (
                'work/azure-native/catalog-reader-windows-validation.json',
                '$Reader.codeHead -cne $Baseline', '$Reader.sourceHead -cne $Baseline',
                '$Reader.hostName -cne [Environment]::MachineName', "$Reader.result -cne 'PASS'",
                "$Reader.scope -cne 'CATALOG_READER_DELTA_ONLY'", '$Reader.tests -ne 12',
                '$Reader.failures -ne 0', '$Reader.errors -ne 0', '$Reader.skipped -ne 0',
                '$Reader.launcherGuards -ne 5', '$Reader.azureAuthorized -isnot [bool]',
                '$Reader.azureAuthorized -ne $false', 'readerValidationSha256',
            ):
                self.assertIn(guard, block)
        self.assertIn('if (Test-Path -LiteralPath $ValidationPath) { throw', validation)
        self.assertIn('Assert-ReaderValidation $ExecutableHead', validation)
        self.assertIn('Assert-ReaderValidation $ValidatedHead', launcher)
        self.assertIn("scope='CATALOG_COMPONENT_PACKET_ONLY'", validation)
        self.assertIn('azureAuthorized=$false', validation)
        self.assertIn('$Validation.sourceHead -cne $CurrentHead', launcher)
        self.assertIn('$Validation.codeHead -cne $ValidatedHead', launcher)
        self.assertIn('$Validation.hostName -cne [Environment]::MachineName', launcher)
        self.assertLess(launcher.index('Remove-Item -LiteralPath $ValidationPath -ErrorAction Stop'), launcher.index('. $LiveBlocks[2]'))
        self.assertLess(launcher.index('Remove-Item -LiteralPath $ReaderValidationPath -ErrorAction Stop'), launcher.index('. $LiveBlocks[2]'))
        self.assertIn('Test-Path -LiteralPath work/azure-native/resource-scope.json', launcher)

    def test_comparison_runs_after_setup_before_owner_secret_cleanup(self):
        block = self.block(3)
        setup = block.index("Assert-NativeReport 'AiCaseDataAzureSqlDisposableSetupTest'")
        clear_setup = block.index('Remove-Item Env:SHALE_AZURE_SETUP_OPT_IN', setup)
        comparison = block.index("'-Dtest=AiCaseDataAzureSqlCatalogComponentTest'")
        cleanup = block.index('Remove-Item Env:SHALE_AZURE_OWNER_USERNAME')
        self.assertLess(setup, clear_setup)
        self.assertLess(clear_setup, comparison)
        self.assertLess(comparison, cleanup)
        self.assertIn("Assert-NativeReport 'AiCaseDataAzureSqlCatalogComponentTest'", block)
        self.assertIn('if ($ComponentExit -ne 0)', block)
        self.assertIn('finally { Remove-Item Env:SHALE_AZURE_CATALOG_COMPONENT_OPT_IN', block)
        self.assertIn('Remove-Item work/azure-native/catalog-components.tsv', block)
        self.assertIn('Remove-Item shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlCatalogComponentTest.xml', block)
        command = next(line for line in block.splitlines()
                       if 'mvn ' in line and "'-Dtest=AiCaseDataAzureSqlCatalogComponentTest'" in line)
        self.assertTrue(command.endswith(' *> $null'), 'Never forward native diagnostic streams')
        self.assertNotIn('{ Get-Content work/azure-native/catalog-components.tsv }', block)
        self.assertIn('$Lines.Count -ne 20', block)
        self.assertIn('$Fields.Count -ne 7', block)
        self.assertIn('$Fields[1] -cne $Names[$Index]', block)
        self.assertIn("$Fields[6] -cne 'true'", block)
        self.assertIn('$Fields[2] -cne $Fields[3]', block)
        self.assertIn('$Fields[4] -cne $Fields[5]', block)
        self.assertLess(block.index('$Fields.Count -ne 7'), block.index('$Lines | Write-Output'))
        for category in ('OWNER_TARGET_PREFLIGHT', 'VERIFIER_TARGET_PREFLIGHT',
                         'OWNER_COMPONENT_READ', 'VERIFIER_COMPONENT_READ', 'REPORT_WRITE'):
            self.assertIn(category, block)
        self.assertIn('if ($Messages -ccontains $SafeMessage)', block)
        self.assertIn("throw \"Catalog component diagnostic failed; stage=$SafeCategory; discard fixture; no retry/adoption\"", block)


if __name__ == '__main__':
    unittest.main()


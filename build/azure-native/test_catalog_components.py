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

    def test_resource_mutations_and_cleanup_remain_exact(self):
        creation = self.block(2)
        mutations = '\n'.join(line for line in creation.splitlines() if line.startswith('New-Az'))
        self.assertEqual('f6c64ea35acc0e82997be1c05318f9eed1efcc5d3b4343361ff957affb697fbd', hashlib.sha256(mutations.encode()).hexdigest(),
                         'Preserve all resource/TLS/one-IP firewall commands exactly')
        self.assertEqual('6bffe7d3773450cb4ef1e8e08110175ecc00abc79529c2165ac00b8970b672e2',
                         hashlib.sha256(self.block(5).encode()).hexdigest())
        self.assertIn('-AsSecureString', creation)
        self.assertIn('[System.Management.Automation.PSCredential]::new($UserName,$SecurePassword)', creation)
        self.assertNotIn('GetNetworkCredential', creation)
        self.assertNotIn('SecureStringTo', creation)

    def test_runtime_credential_generation_remains_exact(self):
        block = re.search(r'(?s)# BEGIN OFFLINE-TESTED CONTAINED CREDENTIALS\n(.*?)# END OFFLINE-TESTED CONTAINED CREDENTIALS', self.text).group(1)
        self.assertEqual('44a35302619c3617e045a1c5d359931409591c51dba463b79c2bc91e91aa2b74', hashlib.sha256(block.encode()).hexdigest())

    def test_live_digest_covers_the_executed_blocks(self):
        launcher = self.launcher()
        self.assertIn('foreach ($Number in @(2,3,5))', launcher)
        expected = hashlib.sha256('\n'.join(self.block(n) for n in (2, 3, 5)).encode()).hexdigest()
        self.assertEqual('3b3a5eef6faff948959ca81714e93c5e35716cb39588f94061b3b07d89f5e03a', expected,
                         'Only reviewed section 3 diagnostic parsing may change the live digest')
        self.assertIn(f"if ($LiveDigest -cne '{expected}')", launcher)
        self.assertNotIn('. $LiveBlocks[4]', launcher, 'This attempt may only compare components after setup')
        self.assertRegex(launcher, r'(?s)try \{\s*\. \$LiveBlocks\[2\].*?\. \$LiveBlocks\[3\].*?finally \{.*?\. \$LiveBlocks\[5\]')
        self.assertIn('AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT', launcher)
        self.assertLess(launcher.index('AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT'),
                        launcher.index('. $LiveBlocks[2]'))

    def test_validation_is_same_source_same_host_and_consumed_before_creation(self):
        launcher = self.launcher()
        validation = self.block(1)
        baseline = '8d6bfa11b31a0334e16cc99c81cac3f35bfada48'
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
                "$Reader.scope -cne 'CATALOG_READER_DELTA_ONLY'", '$Reader.tests -ne 15',
                "$Reader.validationMarker -cne 'COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS'",
                "$Reader.reportSha256 -cnotmatch '^[a-f0-9]{64}$'",
                '$Reader.failures -ne 0', '$Reader.errors -ne 0', '$Reader.skipped -ne 0',
                '$Reader.launcherGuards -ne 5', '$Reader.azureAuthorized -isnot [bool]',
                '$Reader.azureAuthorized -ne $false', 'readerValidationSha256',
            ):
                self.assertIn(guard, block)
        self.assertIn('if (Test-Path -LiteralPath $ValidationPath) { throw', validation)
        self.assertIn('if (Test-Path -LiteralPath $ReaderValidationPath) { throw', validation)
        for evidence in ("$ReaderReport.testsuite.tests -cne '15'", "$ReaderReport.testsuite.failures -cne '0'",
                         "$ReaderReport.testsuite.errors -cne '0'", "$ReaderReport.testsuite.skipped -cne '0'",
                         '@($ReaderReport.testsuite.testcase).Count -ne 15', 'reportSha256=$ReportDigest',
                         'codeHead=$ExecutableHead; sourceHead=$ExecutableHead',
                         "validationMarker='COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS'"):
            self.assertIn(evidence, validation)
        self.assertLess(validation.index("$env:OS -cne 'Windows_NT'"), validation.index('Set-Content -Encoding utf8 -LiteralPath $ReaderValidationPath'))
        self.assertNotIn('Set-Content -Encoding utf8 -LiteralPath $ReaderValidationPath', launcher)
        self.assertNotIn('5b88402940fc9950228eb5320bed9b37fc64e359', validation + launcher)
        self.assertIn('Assert-ReaderValidation $ExecutableHead', validation)
        self.assertIn('Assert-ReaderValidation $ValidatedHead', launcher)
        self.assertIn("scope='CATALOG_COMPONENT_PACKET_ONLY'", validation)
        self.assertIn('azureAuthorized=$false', validation)
        self.assertIn('$Validation.sourceHead -cne $CurrentHead', launcher)
        self.assertIn('$Validation.codeHead -cne $ValidatedHead', launcher)
        self.assertIn('$Validation.hostName -cne [Environment]::MachineName', launcher)
        creation = self.block(2)
        reader = creation.index('Remove-Item -LiteralPath $ReaderValidationPath -ErrorAction Stop')
        live = creation.index('Remove-Item -LiteralPath $ValidationPath -ErrorAction Stop')
        for preflight in ('Connect-AzAccount', 'Set-AzContext', 'Get-AzLocation', 'Get-Credential -UserName',
                          '[System.Net.IPAddress]::TryParse', 'Get-AzResourceGroup'):
            self.assertLess(creation.index(preflight), reader)
        self.assertLess(reader, live)
        self.assertLess(live, creation.index('Set-Content -Encoding utf8 work/azure-native/resource-scope.json'))
        self.assertLess(creation.index('$ScopeWritten = $true'), creation.index('New-AzResourceGroup'))
        boundary = creation[live:creation.index('New-AzResourceGroup')]
        self.assertNotRegex(boundary, r'Read-Host|Get-Az|Get-Credential')
        for marker in ('$ReaderValidationPath', '$ValidationPath'):
            removal = f'Remove-Item -LiteralPath {marker} -ErrorAction Stop'
            self.assertEqual(1, creation.count(removal))
            self.assertNotIn(removal, launcher)
        for block in (validation, launcher):
            self.assertIn("'build/azure-native/test_runbook_launcher.ps1'", block)
        self.assertIn('-File build/azure-native/test_runbook_launcher.ps1', validation)
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
        self.assertIn('if ($Messages -ccontains $SafeMessage) { return $SafeMessage }', block)
        self.assertIn('if ($Messages.Count -ne 1) { return $Fallback }', block)
        self.assertIn('$SafeFailure = Get-SafeCatalogFailure -Messages $Messages', block)
        self.assertIn('throw (Get-SafeCatalogFailure -Messages @($_.Exception.Message))', block)
        parser = re.search(r'(?s)# BEGIN OFFLINE-TESTED CATALOG FAILURE PARSER\n(.*?)# END OFFLINE-TESTED CATALOG FAILURE PARSER', block).group(1)
        java = (ROOT / 'shale-data/src/test/java/com/shale/data/security/ai/AzureCatalogComponents.java').read_text(encoding='utf-8')
        names = re.search(r'(?s)NAMES=List.of\((.*?)\);', java).group(1)
        self.assertEqual(re.findall(r'"([a-z_]+)"', names),
                         re.findall(r"'([a-z_]+)'", re.search(r'(?s)\$ComponentNames = @\((.*?)\)', parser).group(1)))
        phases = re.search(r'ReadPhase \{([^}]+)\}', java).group(1)
        self.assertEqual(re.findall(r'[A-Z_]+', phases),
                         re.findall(r"'([A-Z_]+)'", re.search(r'\$Phases = @\((.*?)\)', parser).group(1)))
        self.assertNotIn('return $Messages', parser)
        self.assertNotIn('throw $_', block)



if __name__ == '__main__':
    unittest.main()


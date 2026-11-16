"""Offline instrumentation/transport invariants, not live SQL engine verification."""
import json
from pathlib import Path
import re
import unittest
from test_packet import TOOL
import test_supervised as supervised
from test_supervised import quote


class TransactionProofDiagnosticTest(unittest.TestCase):
    def setUp(self):
        self.case = supervised.SupervisedTest()
        self.case.setUp()

    def tearDown(self):
        self.case.tearDown()

    def parse(self, lines):
        command = '. ' + quote(TOOL / 'FastTrack.ps1') + '; '
        command += 'Read-ProofModuleFailures @(' + ','.join(quote(line) for line in lines) + ') | ConvertTo-Json -Depth 5'
        result = self.case.ps(command)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        return json.loads(result.stdout)

    def test_masks_distinguish_every_failed_property_without_private_values(self):
        expected = ['OBJECT_MISSING', 'DEFINITION_UNAVAILABLE', 'OWNERSHIP', 'ANSI_NULLS',
                    'QUOTED_IDENTIFIER', 'EXECUTION_CONTEXT', 'DEFINITION_HASH']
        for i, name in enumerate(expected):
            mask = ''.join('1' if n == i else '0' for n in range(7))
            report = self.parse(['PROOF_MODULE_FAIL:AiCimdDataLocateAccessOwnerV1:' + mask,
                                 'PROOF_ROLLBACK_CONFIRMED', 'PRIVATE_NEVER_PRINT'])
            self.assertTrue(report['Valid'])
            self.assertTrue(report['RollbackConfirmed'])
            self.assertEqual([name], report['Modules'][0]['FailedProperties'])

    def test_malformed_unknown_duplicate_and_empty_records_are_not_evidence(self):
        good = 'PROOF_MODULE_FAIL:AiCimdDataLocateAccessOwnerV1:0000001'
        for lines in ([], [good, good], [good + ':PRIVATE_NEVER_PRINT'],
                      ['PROOF_MODULE_FAIL:PRIVATE_NEVER_PRINT:0000001'],
                      [good.replace('0000001', '0000000')], [good, 'PROOF_ROLLBACK_CONFIRMED', 'PROOF_ROLLBACK_CONFIRMED']):
            report = self.parse(lines)
            self.assertFalse(report['Valid'])
            self.assertEqual([], report['Modules'])

    def test_installer_instrumentation_precedes_unchanged_rollback_and_throw(self):
        sql = (self.case.rendered / '21-proof.sql').read_text()
        original = (TOOL.parents[1] / 'docs/sql/ai-integration/universal-pr4a-proof.sql').read_text()
        source = sql[:sql.index("ACCEPTANCE_SOURCE_OK:21-proof:1")]
        catch = source.index('END TRY BEGIN CATCH /* ACCEPTANCE_PROOF_DIAGNOSTIC_BEGIN */')
        diagnostic = source.index('PROOF_MODULE_FAIL:', catch)
        rollback = source.index('IF XACT_STATE()<>0 ROLLBACK TRANSACTION;', diagnostic)
        confirmed = source.index('PROOF_ROLLBACK_CONFIRMED', rollback)
        rethrow = source.index('THROW; END CATCH;', confirmed)
        self.assertLess(catch, diagnostic)
        self.assertLess(diagnostic, rollback)
        self.assertLess(rollback, confirmed)
        self.assertLess(confirmed, rethrow)
        self.assertIn('BEGIN TRY\n  SELECT', source[catch:rollback])
        self.assertIn('DECLARE @AcceptanceDiagnosticUnavailable bit=1;', source[catch:rollback])
        cleaned = re.sub(r'/\* ACCEPTANCE_PROOF_DIAGNOSTIC_BEGIN \*/\n.*?/\* ACCEPTANCE_PROOF_DIAGNOSTIC_END \*/\n', '', sql, flags=re.S)
        self.assertIn(original, cleaned, 'Removing instrumentation must recover exact original checks and transaction logic')
        original_hex = re.findall(r'DECLARE @C4aModule\d+ nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)', original)
        self.assertEqual(original_hex, re.findall(r'DECLARE @C4aModule\d+ nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)', sql))
        projection = (TOOL / 'ProofModuleFailure.sql').read_text().split('FROM @C4aModules')[0]
        self.assertNotIn('CONVERT(varchar(64)', projection)
        self.assertNotIn('ERROR_MESSAGE() AS', projection)

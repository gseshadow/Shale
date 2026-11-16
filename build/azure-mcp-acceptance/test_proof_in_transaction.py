"""Pre-THROW evidence, phase attribution and unchanged rollback contracts. No SQL."""
import json
import re
import unittest
from test_packet import TOOL
import test_supervised as supervised
from test_supervised import quote
from proof_instrumentation import SEAL, THROW, strip_v2


class TransactionProofDiagnosticTest(unittest.TestCase):
    def setUp(self):
        self.case = supervised.SupervisedTest()
        self.case.setUp()

    def tearDown(self):
        self.case.tearDown()

    def parse(self, lines):
        command = '. ' + quote(TOOL / 'FastTrack.ps1') + '; '
        command += 'Read-ProofModuleFailures @(' + ','.join(quote(line) for line in lines) + ') | ConvertTo-Json -Depth 6'
        result = self.case.ps(command)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        return json.loads(result.stdout)

    def test_every_property_is_attributed_before_throw_with_transaction_state(self):
        expected = ['OBJECT_MISSING', 'DEFINITION_UNAVAILABLE', 'OWNERSHIP', 'ANSI_NULLS',
                    'QUOTED_IDENTIFIER', 'EXECUTION_CONTEXT', 'DEFINITION_HASH']
        for i, name in enumerate(expected):
            mask = ''.join('1' if n == i else '0' for n in range(7))
            report = self.parse(['PROOF_CHECK:SEAL:1:1:1',
                                 'PROOF_MODULE_FAIL:SEAL:AiCimdDataLocateAccessOwnerV1:' + mask,
                                 'PROOF_CATCH:SEAL:-1:1:1', 'PROOF_ROLLBACK_CONFIRMED'])
            self.assertTrue(report['Valid'])
            self.assertTrue(report['RollbackConfirmed'])
            self.assertEqual('SEAL', report['Modules'][0]['Phase'])
            self.assertEqual([name], report['Modules'][0]['FailedProperties'])
            self.assertEqual(1, report['Checks'][0]['XactState'])
            self.assertEqual(-1, report['CatchStates'][0]['XactState'])

    def test_legacy_missing_objects_do_not_reconstruct_the_historical_failure(self):
        names = [line.split('=')[0] for line in (TOOL.parent / 'sql-confinement/cimd-v1-modules.sha256').read_text().splitlines()]
        report = self.parse(['PROOF_MODULE_FAIL:' + name + ':1111111' for name in names])
        self.assertFalse(report['Valid'])
        self.assertEqual('UNSCOPED_LEGACY_OUTPUT', report['Observation'])
        self.assertEqual([], report['Modules'])
        self.assertFalse(report['RollbackConfirmed'])
        report = self.parse(['PROOF_CHECK:STANDALONE:0:0:1', 'PROOF_METADATA_ABSENT:STANDALONE'])
        self.assertFalse(report['Valid'])
        self.assertEqual('NO_MODULE_METADATA_AT_ASSERTION', report['Observation'])
        self.assertEqual([], report['Modules'])
        self.assertEqual(0, report['Checks'][0]['TranCount'])

    def test_invalid_duplicate_unknown_and_unscoped_records_fail_closed(self):
        good = 'PROOF_MODULE_FAIL:INSTALL:AiCimdDataLocateAccessOwnerV1:0000001'
        for lines in ([], [good], ['PROOF_CHECK:INSTALL:0:1:1', good],
                      ['PROOF_CHECK:INSTALL:1:1:1', good, good], [good + ':PRIVATE_NEVER_PRINT'],
                      ['PROOF_MODULE_FAIL:INSTALL:PRIVATE_NEVER_PRINT:0000001'],
                      ['PROOF_CHECK:INSTALL:1:1:1', good.replace('0000001', '0000000')],
                      ['PROOF_CHECK:INSTALL:1:1:1', good, 'PROOF_CATCH:SEAL:-1:1:1'],
                      ['PROOF_CHECK:INSTALL:1:1:1', good, 'PROOF_METADATA_ABSENT:INSTALL'],
                      ['PROOF_CHECK:INSTALL:1:1:1', good, 'PROOF_DIAGNOSTIC_UNAVAILABLE:INSTALL']):
            report = self.parse(lines)
            self.assertFalse(report['Valid'])
            self.assertEqual([], report['Modules'])

    def test_assertion_sites_capture_before_throw_and_catch_never_reads_catalog(self):
        sql = (self.case.rendered / '21-proof.sql').read_text()
        original = (TOOL.parents[1] / 'docs/sql/ai-integration/universal-pr4a-proof.sql').read_text()
        self.assertIn(original, strip_v2(sql))
        for phase, body in [('INSTALL', sql), ('STANDALONE', sql),
                            ('SEAL', bytes.fromhex(SEAL.search(sql)[1]).decode('utf-16le'))]:
            origin = body.index('PROOF_CHECK:' + phase)
            rows = body.index('PROOF_MODULE_FAIL:' + phase, origin)
            throw = body.index(THROW, rows)
            self.assertLess(origin, rows)
            self.assertLess(rows, throw)
            self.assertIn('IF EXISTS(SELECT 1 FROM @C4aModules', body[:origin])
        installer = sql[:sql.index('ACCEPTANCE_SOURCE_OK:21-proof:1')]
        catch = installer.index('END TRY BEGIN CATCH /* ACCEPTANCE_PROOF_V2_BEGIN */')
        rollback = installer.index('IF XACT_STATE()<>0 ROLLBACK TRANSACTION;', catch)
        rethrow = installer.index('THROW; END CATCH;', rollback)
        self.assertIn('@AcceptanceErrorNumber int=ERROR_NUMBER()', installer[catch:rollback])
        self.assertNotIn('FROM sys.', installer[catch:rethrow])
        self.assertIn('PROOF_ROLLBACK_CONFIRMED', installer[rollback:rethrow])
        self.assertNotIn('ERROR_MESSAGE()', installer[rollback:rethrow])
        payload = r'DECLARE @C4aModule\d+ nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)'
        self.assertEqual(re.findall(payload, original), re.findall(payload, sql))

    def test_source_partition_uses_the_exact_approved_bytes(self):
        import hashlib
        path = self.case.packet / 'owned-snapshot.sql'
        path.write_text('synthetic approved input')
        digest = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        path.write_text('PRIVATE_NEVER_PRINT')
        command = '. ' + quote(TOOL / 'Supervision.ps1') + '; . ' + quote(TOOL / 'FastTrack.ps1') + '; '
        command += '$text=Read-ProofStageSnapshot ' + quote(path) + ' ' + quote(digest)
        result = self.case.ps(command, success=False)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        self.assertIn('input changed', result.stderr)

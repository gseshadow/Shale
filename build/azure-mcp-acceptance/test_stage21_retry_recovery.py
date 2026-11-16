"""Latest-attempt recovery tests; owned synthetic history/captures and mocked SQLCMD only."""
import csv
import hashlib
import json
from pathlib import Path
import re
import shutil
import unittest

from test_packet import TOOL
from test_supervised import quote
import test_stage21_recovery as old_recovery


class LatestStage21RecoveryTest(unittest.TestCase):
    def setUp(self):
        from proof_instrumentation import strip_v2
        self.old = old_recovery.Stage21RecoveryTest()
        self.old.setUp()
        self.case = self.old.case
        self.packet = self.case.packet
        legacy_directory = self.case.rendered
        instrumented = (legacy_directory / '21-proof.sql').read_text()
        legacy = strip_v2(instrumented)
        self.assertEqual('F3D4E8EB41054B0E5A5EF4A608497B6936DB0D889E77A1C99D9BE72A6FD5827E', hashlib.sha256(legacy.encode()).hexdigest().upper())
        self.replace_stage(legacy_directory, legacy)
        self.old.prepare()
        self.old.finalize()
        original_fork = next(self.packet.glob('fast-track-recovered-*.jsonl'))
        self.case.recovery_journal = original_fork
        self.case.run_fast('module-drift', success=False)
        initial_report = max(self.packet.glob('fast-track-*/report.json'), key=lambda p: p.stat().st_mtime_ns)
        self.previous = self.packet / 'rendered-v1'
        shutil.copytree(legacy_directory, self.previous)
        args = [('Previous', legacy_directory), ('V1', self.previous), ('Failure', initial_report),
                ('Packet', self.packet), ('OldCapture', self.old.captures[0]), ('OldTicket', self.old.plan['Ticket'])]
        command = '& ' + quote(TOOL / 'test_stage21_v1_fixture.ps1')
        command += ''.join(' -' + k + ' ' + quote(v) for k, v in args)
        self.case.fixture_case.ps(command)
        self.parent = self.packet / 'synthetic-v1-failed.jsonl'
        self.case.recovery_journal = self.parent
        self.case.rendered = self.previous
        self.case.run_fast('module-drift', success=False)
        self.failure = max(self.packet.glob('fast-track-*/report.json'), key=lambda p: p.stat().st_mtime_ns)
        report = json.loads(self.failure.read_text())
        names = [line.split('=')[0] for line in (TOOL.parent / 'sql-confinement/cimd-v1-modules.sha256').read_text().splitlines()]
        flags = ['OBJECT_MISSING', 'DEFINITION_UNAVAILABLE', 'OWNERSHIP', 'ANSI_NULLS', 'QUOTED_IDENTIFIER', 'EXECUTION_CONTEXT', 'DEFINITION_HASH']
        report.update(ProofModules=[dict(Module=n, FailedProperties=flags) for n in names], RollbackConfirmed=False, ProofDiagnosticsValid=True)
        self.failure.write_text(json.dumps(report))  # reconstruct the observed OLD report in owned fixtures only
        self.new = self.packet / 'rendered-instrumented'
        shutil.copytree(self.previous, self.new)
        self.replace_stage(self.new, instrumented)
        self.case.rendered = self.new
        self.review = self.packet / 'retry-review'
        digest = hashlib.sha256(self.parent.read_bytes()).hexdigest().upper()
        self.successor = self.packet / ('fast-track-stage21-recovery-' + digest + '.json')
        self.source_bytes = {p: p.read_bytes() for p in (self.parent, original_fork, self.old.journal,
                             self.packet / 'fast-track-recovery.json', self.packet / 'fast-track-stage21-recovery.json')}

    @staticmethod
    def replace_stage(directory, text):
        path = directory / '21-proof.sql'
        path.write_text(text)
        manifest = directory / 'sha256.csv'
        with manifest.open() as stream:
            rows = list(csv.DictReader(stream))
        for row in rows:
            if row['Stage'] == '21-proof':
                row['SHA256'] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        with manifest.open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['Stage', 'SHA256'])
            writer.writeheader()
            writer.writerows(rows)

    def tearDown(self):
        self.old.tearDown()

    def ps(self, args, success=True):
        return self.case.fixture_case.ps('& ' + quote(TOOL / 'Stage21RetryRecovery.ps1')
                                        + ' -RenderedDirectory ' + quote(self.new) + ' ' + args, success=success)

    def prepare(self, success=True):
        result = self.ps('-PreviousRenderedDirectory ' + quote(self.previous)
                         + ' -FailureReport ' + quote(self.failure) + ' -ReviewDirectory ' + quote(self.review), success)
        if success:
            self.plan_file = self.review / 'plan.json'
            self.plan = json.loads(self.plan_file.read_text())
            old_text = self.old.captures[0].read_text()
            self.capture = self.review / 'current.txt'
            self.capture.write_text(old_text.replace(self.old.plan['Ticket'], self.plan['Ticket']))
        return result

    def finalize(self, success=True):
        return self.ps('-PlanPath ' + quote(self.plan_file) + ' -Capture ' + quote(self.capture)
                       + ' -ReviewReference synthetic-reviewed-parent-report-and-observation'
                       + ' -VerifiedNativeExitZero -NoOtherSessionsOrWriters', success)

    def assert_sources_preserved(self):
        for path, original in self.source_bytes.items():
            self.assertEqual(original, path.read_bytes(), str(path))

    def test_single_evidenced_fork_resumes_at21_and_never_replays_prefix(self):
        self.prepare()
        self.finalize()
        selected = json.loads((self.successor).read_text())
        fork = Path(selected['Journal'])
        self.finalize(success=False)  # one immutable selection; no parallel execution path
        self.case.recovery_journal = fork
        before = len(self.case.dispatched_stages())
        self.case.run_fast()
        dispatched = self.case.dispatched_stages()[before:]
        self.assertEqual(['21-proof'], dispatched[0])
        self.assertEqual(28, len(dispatched))
        self.assertEqual(51, self.case.report()['CompletedCount'])
        self.assert_sources_preserved()
        calls = self.case.calls.read_bytes()
        self.case.run_fast()
        self.assertEqual(calls, self.case.calls.read_bytes())
        self.case.recovery_journal = self.parent
        self.case.run_fast(success=False)  # old selection cannot bypass the new selection
        self.assertEqual(calls, self.case.calls.read_bytes())

    def test_insufficient_nonzero_drifted_or_wrong_plan_capture_cannot_recover(self):
        self.prepare()
        good = self.capture.read_text()
        for bad in ('', good.replace('\n0\n', '\n1\n', 1),
                    good.replace('A' * 64, 'B' * 64),
                    good.replace(self.plan['Ticket'], '0' * 32),
                    good.replace('BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED', ''),
                    good + 'Msg 59812, Level 16, State 1\nPRIVATE_NEVER_PRINT\n'):
            self.capture.write_text(bad)
            result = self.finalize(success=False)
            self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
            self.assertFalse(self.successor.exists())
            self.assert_sources_preserved()
        self.capture.write_text(good)
        self.ps('-PlanPath ' + quote(self.plan_file) + ' -Capture ' + quote(self.capture)
                + ' -ReviewReference synthetic-review -VerifiedNativeExitZero:$false -NoOtherSessionsOrWriters',
                success=False)
        self.assertFalse(self.successor.exists())
        self.assert_sources_preserved()

    def test_changed_sql_even_with_updated_plan_hash_is_refused(self):
        self.prepare()
        path = Path(self.plan['SqlPath'])
        path.write_text('SELECT 1; -- synthetic substituted probe')
        self.plan['SqlHash'] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        self.plan_file.write_text(json.dumps(self.plan))
        self.finalize(success=False)
        self.assert_sources_preserved()

    def test_any_change_beyond_diagnostics_or_to_completed_stage_is_refused(self):
        path = self.new / '20-pr3.sql'
        path.write_text(path.read_text() + '\n-- synthetic completed-stage change')
        manifest = self.new / 'sha256.csv'
        text = manifest.read_text()
        old_hash = re.search(r'20-pr3,([A-F0-9]{64})', text)[1]
        manifest.write_text(text.replace(old_hash, hashlib.sha256(path.read_bytes()).hexdigest().upper()))
        self.prepare(success=False)
        self.assert_sources_preserved()

    def test_wrong_failure_report_or_changed_evidence_blocks_resume(self):
        self.prepare()
        self.finalize()
        selected = json.loads((self.successor).read_text())
        self.case.recovery_journal = Path(selected['Journal'])
        self.capture.write_text('changed synthetic evidence')
        calls = self.case.calls.read_bytes()
        self.case.run_fast(success=False)
        self.assertEqual(calls, self.case.calls.read_bytes())
        self.assert_sources_preserved()

    def test_failure_report_without_exact_assertion_refuses_preparation(self):
        report = json.loads(self.failure.read_text())
        report['Assertions'] = ['UNCLASSIFIED_SQL_ASSERTION']
        self.failure.write_text(json.dumps(report))
        self.prepare(success=False)
        self.review = self.packet / 'malformed-report-review'
        self.failure.write_text('{PRIVATE_NEVER_PRINT')
        result = self.prepare(success=False)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        self.assertFalse(self.successor.exists())
        self.assert_sources_preserved()

    def test_parent_integrity_and_new_journal_chain_are_checked_before_approval(self):
        self.prepare()
        self.finalize()
        selected = json.loads((self.successor).read_text())
        fork = Path(selected['Journal'])
        self.case.recovery_journal = fork
        calls = self.case.calls.read_bytes()
        approvals = self.case.approvals.read_bytes()
        parent_bytes = self.parent.read_bytes()
        self.parent.write_bytes(parent_bytes.splitlines(keepends=True)[0])  # valid-prefix truncation of owned synthetic evidence
        self.case.run_fast(success=False)
        self.assertEqual(calls, self.case.calls.read_bytes())
        self.assertEqual(approvals, self.case.approvals.read_bytes())
        self.parent.write_bytes(parent_bytes)
        record = json.loads(fork.read_text().splitlines()[0])
        record['Hash'] = '0' * 64
        fork.write_text(json.dumps(record) + '\n')
        self.case.run_fast(success=False)
        self.assertEqual(calls, self.case.calls.read_bytes())
        self.assertEqual(approvals, self.case.approvals.read_bytes())
        self.assert_sources_preserved()

    def test_unapproved_stage21_change_is_refused(self):
        good = (self.new / '21-proof.sql').read_text()
        self.replace_stage(self.new, good + '\n-- unapproved synthetic change')
        self.prepare(success=False)
        self.assertFalse(self.successor.exists())
        self.assert_sources_preserved()

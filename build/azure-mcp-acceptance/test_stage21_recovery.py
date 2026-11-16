"""Offline reconciliation/fork contracts. All captures and journals are synthetic;
no SQL client is invoked, including during the tested resume."""
import hashlib
import json
from pathlib import Path
import unittest

import test_fast_track as fast
from test_packet import TOOL
from test_supervised import quote


class Stage21RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.case = fast.FastTrackTest()
        self.case.setUp()
        self.case.run_fast('proof-fail', success=False)
        self.packet = self.case.packet
        self.journal = self.packet / 'fast-track.jsonl'
        records = self.case.journal()
        # Recreate ONLY an owned synthetic old grouped journal with its real
        # baseline and stage hashes. Production journals are never altered.
        seed = next(r for r in records if r.get('Stage') == '15c-seed' and r['Kind'] == 'complete')
        completed = [r for r in records if r['Kind'] == 'complete' and r['Stage'] != '15c-seed']
        records = [records[0], dict(Kind='pending', Start=18, End=19), seed,
                   dict(Kind='verified'), dict(Kind='pending', Start=19, End=30),
                   *completed, dict(Kind='failed', Stage='21-proof')]
        chain = '0' * 64
        lines = []
        for i, row in enumerate(records):
            row['Index'] = i
            payload = json.dumps(row, separators=(',', ':'))
            chain = hashlib.sha256((chain + payload).encode()).hexdigest().upper()
            lines.append(json.dumps(dict(Payload=payload, Hash=chain), separators=(',', ':')))
        self.journal.write_text('\n'.join(lines) + '\n')
        self.original = self.journal.read_bytes()
        self.review = self.packet / 'checkpoint-review'
        self.plan_path = self.review / 'plan.json'

    def tearDown(self):
        self.case.tearDown()

    def ps(self, args, success=True):
        return self.case.fixture_case.ps('& ' + quote(TOOL / 'Stage21Recovery.ps1')
                                        + ' -RenderedDirectory ' + quote(self.case.rendered) + ' ' + args,
                                        success=success)

    def prepare(self):
        self.ps('-ReviewDirectory ' + quote(self.review))
        self.plan = json.loads(self.plan_path.read_text())
        self.captures = [self.review / 'a.txt', self.review / 'b.txt']
        text = '\n'.join(name + '\n------\n0' for name in (
            'ApplicationCatalogDriftFindingCount', 'ConfinementCatalogDriftFindingCount',
            'RegistryConfinementFindingCount'))
        text += '\nBINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED\nACCEPTANCE_SOURCE_OK:18-binding-postcheck:1'
        text += '\nACCEPTANCE_SNAPSHOT:' + 'A' * 64 + ':2:2:2\n'
        text += 'RECOVERY24_OK:' + self.plan['Ticket'] + '\n'
        for path in self.captures:
            path.write_text(text)

    def finalize(self, success=True):
        return self.ps('-PlanPath ' + quote(self.plan_path)
                       + ' -CaptureA ' + quote(self.captures[0]) + ' -CaptureB ' + quote(self.captures[1])
                       + ' -ReviewReference ' + quote('synthetic-reviewed-native-exits-and-no-writers')
                       + ' -VerifiedNativeExitZero -NoOtherSessionsOrWriters', success=success)

    def test_recovery_preserves_source_and_resumes_at_proof_without_replay(self):
        self.prepare()
        probe = Path(self.plan['SqlPath']).read_text()
        self.assertIn('PR 3 catalog drift; never refresh receipts.', probe)
        self.assertIn('Later-stage artifacts present or partial', probe)
        self.assertNotIn('CREATE TABLE', probe)
        legacy = (TOOL.parents[1] / 'docs/sql/ai-integration/phase3b8-confinement-verify.sql').read_text()
        self.assertNotIn(legacy, probe, 'Use PR3-aware verification after PR3 role changes')
        self.finalize()
        self.assertEqual(self.original, self.journal.read_bytes())
        fork = next(self.packet.glob('fast-track-recovered-*.jsonl'))
        self.assertTrue(fork.read_bytes().startswith(self.original))
        self.finalize(success=False)  # a second fork could otherwise replay the same checkpoint
        self.case.recovery_journal = fork
        before = len(self.case.dispatched_stages())
        self.case.run_fast()
        self.assertEqual([['21-proof']], self.case.dispatched_stages()[before:before+1])
        self.assertEqual(51, self.case.report()['CompletedCount'])
        self.assertEqual(self.original, self.journal.read_bytes())

    def test_missing_nonzero_wrong_ticket_and_error_captures_cannot_unlock(self):
        self.prepare()
        good = self.captures[0].read_text()
        for bad in ('', good.replace('\n0\n', '\n1\n', 1),
                    good.replace(self.plan['Ticket'], '0' * 32),
                    good + 'Msg 59400, Level 16, State 1\nPRIVATE_NEVER_PRINT\n'):
            self.captures[0].write_text(bad)
            result = self.finalize(success=False)
            self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
            self.assertFalse(list(self.packet.glob('fast-track-recovered-*.jsonl')))
            self.assertEqual(self.original, self.journal.read_bytes())

    def test_changed_snapshot_or_probe_refuses_recovery(self):
        self.prepare()
        self.captures[1].write_text(self.captures[1].read_text().replace('A' * 64, 'B' * 64))
        self.finalize(success=False)
        Path(self.plan['SqlPath']).write_text('changed synthetic probe')
        self.finalize(success=False)
        self.assertEqual(self.original, self.journal.read_bytes())

    def test_changed_evidence_after_fork_blocks_before_approval_and_sql(self):
        self.prepare()
        self.finalize()
        self.case.recovery_journal = next(self.packet.glob('fast-track-recovered-*.jsonl'))
        self.captures[0].write_text('changed evidence')
        before = self.case.calls.read_bytes()
        self.case.run_fast(success=False)
        self.assertEqual(before, self.case.calls.read_bytes())
        self.assertEqual(self.original, self.journal.read_bytes())

    def test_existing_review_directory_and_truncated_source_are_refused(self):
        self.prepare()
        self.ps('-ReviewDirectory ' + quote(self.review), success=False)
        self.journal.write_bytes(self.original[:-1])
        self.finalize(success=False)
        self.assertFalse(list(self.packet.glob('fast-track-recovered-*.jsonl')))

    def test_changed_probe_and_rehashed_plan_cannot_substitute_arbitrary_sql(self):
        self.prepare()
        path = Path(self.plan['SqlPath'])
        path.write_text('SELECT 1; -- synthetic substituted probe')
        self.plan['SqlHash'] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        self.plan_path.write_text(json.dumps(self.plan))
        self.finalize(success=False)
        self.assertFalse(list(self.packet.glob('fast-track-recovered-*.jsonl')))

    def test_rollback_or_partial_later_state_needs_separate_review(self):
        self.prepare()
        self.captures[0].write_text(self.captures[0].read_text().replace(
            'RECOVERY24_OK:' + self.plan['Ticket'], 'Msg 59812, Level 16, State 1'))
        self.finalize(success=False)
        self.assertFalse(list(self.packet.glob('fast-track-recovered-*.jsonl')))

    def test_all_public_stage21_assertions_have_safe_codes(self):
        import re
        messages = set()
        for name in ('universal-pr4a-proof.sql', 'universal-pr4a-proof-verify.sql'):
            source = (TOOL.parents[1] / 'docs/sql/ai-integration' / name).read_text()
            messages.update(re.findall(r"THROW 59400,'([^']*)'", source))
        command = '. ' + quote(TOOL / 'FastTrack.ps1') + '; '
        command += 'Get-SafeFastTrackAssertions @(' + ','.join(quote(m) for m in sorted(messages)) + ')'
        result = self.case.fixture_case.ps(command)
        self.assertNotIn('UNCLASSIFIED_SQL_ASSERTION', result.stdout)
        self.assertEqual(len(messages), len(result.stdout.splitlines()))

    def test_safe_diagnostics_never_return_arbitrary_messages_or_session_values(self):
        command = '. ' + quote(TOOL / 'FastTrack.ps1') + "; "
        command += "Get-SafeFastTrackAssertions @('PRIVATE_NEVER_PRINT','Fresh unscoped dbo lease required. PRIVATE_NEVER_PRINT'); "
        command += "Get-SafeFastTrackSessionFailures @('FAST_SESSION_STATE:21-proof:0:1:1:1:1','FAST_SESSION_STATE:21-proof:secret') '21-proof'"
        result = self.case.fixture_case.ps(command)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        for token in ('UNCLASSIFIED_SQL_ASSERTION', 'USER_NOT_DBO', 'OPEN_TRANSACTION',
                      'TENANT_CONTEXT_SET', 'PRINCIPAL_CONTEXT_SET', 'IMPLICIT_TRANSACTIONS_ON'):
            self.assertIn(token, result.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)

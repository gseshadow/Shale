"""Protect explicit risk acceptance and shared exact recovery; no production/SQL execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / 'docs/sql/auth/permission-cutover'


def read(name):
    return (SQL / (name + '.sql')).read_text(encoding='utf-8')


def executable(text):
    return re.sub(r'/\*.*?\*/|--[^\n]*', '', text, flags=re.S)


class ControlledRetirementContractTest(unittest.TestCase):
    def test_routes_select_distinct_approvals_before_identical_mutation_bodies(self):
        for original, controlled, body in [
            ('retire-legacy-login', 'controlled-retire-legacy-login', 'legacy-retirement-apply-body'),
            ('rollback-legacy-login', 'controlled-rollback-legacy-login', 'legacy-retirement-rollback-body'),
        ]:
            for entry, approval in [(original, 'legacy-retirement-approval'),
                                    (controlled, 'controlled-legacy-approval')]:
                text = read(entry)
                includes = re.findall(r'^:r\s+(.+?)\s*$', text, re.M)
                self.assertEqual([f'docs/sql/auth/permission-cutover/{n}.sql' for n in
                                  ['legacy-retirement-context', approval, body]], includes)
                self.assertTrue(text.startswith(':ON ERROR EXIT'))
                self.assertNotRegex(executable(text), r'(?i)\b(GRANT|REVOKE|DENY|COMMIT)\b')

    def test_alternative_cannot_relabel_original_inventory_or_zero_invocation_proof(self):
        original, alternative = read('legacy-retirement-approval'), read('controlled-legacy-approval')
        for name in ['CallerInventoryApproved', 'LegacyCallersDrained',
                     'CallerInventoryEvidence', 'CallerDrainEvidence']:
            self.assertIn('$(' + name + ')', original)
            self.assertNotIn('$(' + name + ')', alternative)
        for name, literal in {
            'LegacyAcceptanceRoute': 'CONTROLLED_CHANGE',
            'UnknownClientRisk': 'ACCEPTED_NOT_ZERO_PROOF',
            'CompletedPackageReview': 'APPROVED',
            'ControlledWindowAuthorization': 'AUTHORIZED',
            'IrrecoverableEffectsRisk': 'ACCEPTED', 'ReopenedCapabilityRisk': 'ACCEPTED',
            'RepresentativeChecks': 'PASSED', 'RollbackRehearsal': 'PASSED',
        }.items():
            self.assertIn(f"N'$({name})'<>N'{literal}'", alternative)
        for name in ['SecurityOwner', 'ServiceOwner', 'RiskApprovalEvidence', 'PackageReviewEvidence',
                     'WindowAuthorizationEvidence', 'KnownArtifactEvidence', 'CompatibilityEvidence',
                     'OperationalReadinessEvidence', 'RollbackRehearsalEvidence']:
            self.assertIn(f"NULLIF(LTRIM(RTRIM(N'$({name})')),N'') IS NULL", alternative)
        self.assertNotRegex(executable(alternative), r'(?i)\b(GRANT|REVOKE|DENY|EXEC|SETVAR)\b')
        self.assertIn('THROW 58360', alternative)

    def test_apply_has_finite_window_but_recovery_never_expires(self):
        apply = read('controlled-retire-legacy-login')
        for check in ['@Start IS NULL', '@End IS NULL', '@RollbackBy IS NULL',
                      '@Start>=@RollbackBy', 'DATEADD(MINUTE,10,@RollbackBy)>@End',
                      '@End>DATEADD(MINUTE,30,@Start)', 'SYSUTCDATETIME()<@Start',
                      'SYSUTCDATETIME()>=@RollbackBy', 'THROW 58361']:
            self.assertIn(check, apply)
        self.assertLess(apply.index('THROW 58361'), apply.index('legacy-retirement-apply-body.sql'))
        recovery = read('controlled-rollback-legacy-login') + read('controlled-legacy-approval')
        self.assertNotRegex(executable(recovery), r'SYSUTCDATETIME|WindowEndUtc|RollbackStartByUtc')

    def test_shared_bodies_retain_exact_nine_row_delta_atomicity_and_rollback_hash(self):
        names = ['sp_login_get_hash', 'sp_login_mark_failure', 'sp_login_mark_success']
        apply, rollback = read('legacy-retirement-apply-body'), read('legacy-retirement-rollback-body')
        expected_apply = {f'REVOKE EXECUTE ON OBJECT::dbo.{n} FROM shale_app AS dbo;' for n in names}
        expected_apply |= {f'DENY EXECUTE ON OBJECT::dbo.{n} TO {p} AS dbo;'
                           for n in names for p in ['shale_runtime', 'shale_runtime_role']}
        expected_rollback = {f'REVOKE EXECUTE ON OBJECT::dbo.{n} FROM {p} AS dbo;'
                             for n in names for p in ['shale_runtime', 'shale_runtime_role']}
        expected_rollback |= {f'GRANT EXECUTE ON OBJECT::dbo.{n} TO shale_app AS dbo;' for n in names}
        for text, expected in [(apply, expected_apply), (rollback, expected_rollback)]:
            statements = re.findall(r'\b(?:GRANT|REVOKE|DENY)\s+EXECUTE\s+ON\s+OBJECT::[^;]+;', executable(text))
            self.assertEqual(expected, {re.sub(r'\s+', ' ', s) for s in statements})
            self.assertEqual(9, len(statements))
            self.assertLess(text.index('BEGIN TRANSACTION'), text.index(statements[0]))
            self.assertGreater(text.index('COMMIT TRANSACTION'), text.index(statements[-1]))
            self.assertIn('THROW 58344', text)
            self.assertIn('THROW 58345', text)
            self.assertIn('THROW 58346', text)
            self.assertNotRegex(executable(text), r'\bCASCADE\b')
        self.assertIn('#LegacyExpectedAcl EXCEPT', apply)
        self.assertIn('OtherHash', apply)
        self.assertIn("N'$(LegacyBeforeHash)'", rollback)
        self.assertIn('LegacyRollbackApproval', rollback)


if __name__ == '__main__':
    unittest.main()

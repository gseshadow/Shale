"""Protect the read-only fallback's privacy/scope, not Azure runtime completeness."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / 'docs/sql/auth/permission-cutover/sample-legacy-login-usage.sql'
RUNBOOK = ROOT / 'docs/testing/auth-module-permission-cutover.md'


class LegacyUsageObservationContractTest(unittest.TestCase):
    def setUp(self):
        raw = SQL.read_text(encoding='utf-8')
        self.sql = re.sub(r'/\*.*?\*/|--[^\n]*', '', raw, flags=re.S)
        self.book = RUNBOOK.read_text(encoding='utf-8').split('## Captured legacy login behavior')[0]

    def test_exact_object_and_database_filter_excludes_decoys(self):
        names = re.findall(r"OBJECT_ID\(N'([^']+)',N'P'\)", self.sql)
        self.assertEqual(['dbo.sp_login_get_hash', 'dbo.sp_login_mark_failure',
                          'dbo.sp_login_mark_success'], names)
        self.assertRegex(self.sql, r's\.database_id\s*=\s*DB_ID\(\)\s+AND\s+s\.object_id\s*=\s*t\.ObjectId')
        self.assertNotRegex(self.sql, r'(?i)\bLIKE\b')
        self.assertIn('COUNT(DISTINCT ObjectId)', self.sql)

    def test_no_application_data_text_handles_or_persistent_instrumentation(self):
        # Allow only this fixed DMV as a FROM/JOIN sys source. OBJECT_ID only resolves identity.
        self.assertEqual(['dm_exec_procedure_stats'], re.findall(r'\bsys\.(\w+)', self.sql))
        self.assertNotRegex(self.sql, r'(?i)\b(GRANT|REVOKE|DENY|ALTER|CREATE|DROP|EXEC|EXECUTE|DBCC)\b')
        self.assertNotRegex(self.sql, r'(?i)\b(sql_handle|plan_handle|sql_text|statement|password_hash|email|token)\b')
        self.assertNotRegex(self.sql, r'(?i)SELECT\s+\*')
        # These are the only runtime source fields the query can read from the DMV.
        self.assertEqual({'object_id', 'database_id', 'type', 'execution_count',
                          'cached_time', 'last_execution_time'}, set(re.findall(r'\bs\.(\w+)', self.sql)))

    def test_missing_cache_is_null_and_never_certifies_zero_usage(self):
        self.assertRegex(self.sql, r'LEFT\s+JOIN\s+sys\.dm_exec_procedure_stats')
        self.assertIn('SUM(s.execution_count)', self.sql)
        self.assertNotRegex(self.sql, r'(?i)\b(COALESCE|ISNULL)\b')
        self.assertIn("N'INCOMPLETE_CACHE_OBSERVATION' AS Coverage", self.sql)
        for guard in ["DB_NAME()<>", "USER_NAME()<>", '@@TRANCOUNT<>0',
                      'DATEPART(TZOFFSET,SYSDATETIMEOFFSET())<>0', 'THROW 58351']:
            self.assertIn(guard, self.sql)

    def test_fallback_preserves_blocker_and_event_payload_review_boundary(self):
        for requirement in ['entire raw event', 'intrinsic data field', 'not just displayed/exported columns',
                            'UNKNOWN / insufficient', 'cannot close #1874', 'No SQL text, arguments, email',
                            'no Azure XE activation/drop commands', 'missed rounds remain recorded gaps']:
            self.assertIn(requirement, self.book)

    def test_cleanup_has_exact_ownership_stop_retention_and_no_recursive_delete(self):
        blocks = re.findall(r'```powershell\n(.*?)```', self.book, re.S)
        cleanup = next(b for b in blocks if '# CLEANUP:' in b)
        executable = re.sub(r'#.*', '', cleanup)
        for guard in ['APPROVED_EVIDENCE_RETAINED', 'observationStopped', 'stopPath',
                      'ReparsePoint', 'Owner mismatch', 'children.Count', '-cnotcontains']:
            self.assertIn(guard, executable)
        removals = re.findall(r'Remove-Item[^\n}]*', executable)
        self.assertEqual(2, len(removals))
        for removal in removals:
            self.assertIn('-LiteralPath', removal)
            self.assertNotRegex(removal, r'(?i)-Recurse|-Force|\*')


if __name__ == '__main__':
    unittest.main()

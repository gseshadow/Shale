"""Offline evidence contracts; never connects or executes SQL."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

TOOL = Path(__file__).resolve().parent
ROOT = TOOL.parents[1]
spec = importlib.util.spec_from_file_location('proof_diagnostic', TOOL / 'proof_module_diagnostic.py')
diag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diag)


class ProofModuleDiagnosticTest(unittest.TestCase):
    def test_exact_encoded_bytes_match_all_expected_hashes_and_context_clauses(self):
        report = diag.audit(diag.INSTALLER)
        self.assertEqual(16, report['ModuleCount'])
        self.assertTrue(report['AllEncodedDefinitionsMatch'])
        self.assertTrue(report['ExpectedRegistriesMatch'])
        self.assertFalse(report['EngineMetadataObserved'])
        self.assertEqual(['AiCimdDataLocateAccessOwnerV1'], [m['ModuleName'] for m in report['Modules']
                                                         if m['ExpectedExecutionContext'] == 'OWNER'])

    def test_readonly_query_has_exact_registry_and_distinguishes_every_assertion_property(self):
        query = (TOOL / 'Stage21ModuleDiagnostic.sql').read_text()
        self.assertEqual(diag.registry(diag.INSTALLER.read_text())[0], diag.ENTRY.findall(query))
        code = re.sub(r"/\*.*?\*/|--[^\n]*|'(?:''|[^'])*'", ' ', query, flags=re.S)
        self.assertNotRegex(code, r'(?i)\b(?:CREATE|ALTER|DROP|INSERT|UPDATE|DELETE|MERGE|EXEC(?:UTE)?|COMMIT|ROLLBACK|BEGIN\s+TRAN)\b')
        for field in ('ObjectPresent', 'DefinitionVisible', 'OwnerMatches', 'AnsiNullsMatches',
                      'QuotedIdentifierMatches', 'ExecuteAsMatches', 'DefinitionHashMatches',
                      'CurrentAssertionFindingCount', 'ABSENT_NOW_NO_HISTORICAL_METADATA'):
            self.assertIn(field, query)
        projection = query[query.rindex('\nSELECT ModuleName'):]
        self.assertNotRegex(projection, r'(?i)\bdefinition\b|principal_id|password|credential')
        for fence in ("N'ShaleMcpAcceptance'", "N'shale.database.windows.net'", "'VIEW DEFINITION'", "USER_NAME()<>N'dbo'"):
            self.assertIn(fence, query)

    def run_input(self, text):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work', prefix='proof-diagnostic-') as tmp:
            path = Path(tmp) / 'owned-synthetic.sql'
            path.write_text(text, encoding='utf-8')
            return subprocess.run([sys.executable, str(TOOL / 'proof_module_diagnostic.py'), '--input', str(path)],
                                  text=True, capture_output=True, check=False)

    def test_changed_payload_is_identified_without_printing_sql(self):
        text = diag.INSTALLER.read_text()
        payload = diag.PAYLOAD.search(text)
        original = payload[2]
        # Change a character inside a public error string, preserving UTF-16/source identity.
        changed = original.replace('49006e0074006500670072006100740069006f006e00',
                                   '58006e0074006500670072006100740069006f006e00', 1)
        self.assertNotEqual(original, changed)
        result = self.run_input(text[:payload.start(2)] + changed + text[payload.end(2):])
        self.assertEqual(1, result.returncode)
        report = json.loads(result.stdout)
        failed = [m['ModuleName'] for m in report['Modules'] if not m['DefinitionHashMatches']]
        self.assertEqual(['AiCimdDataLocateAccessOwnerV1'], failed)
        self.assertNotIn('CREATE PROCEDURE', result.stdout)
        self.assertNotIn('Integration authentication failed', result.stdout)

    def test_changed_expected_registry_is_distinct_from_changed_payload(self):
        text = diag.INSTALLER.read_text()
        first = diag.registry(text)[0][0][1]
        result = self.run_input(text.replace('0x' + first, '0x' + '0' * 64, 1))
        self.assertEqual(1, result.returncode)
        report = json.loads(result.stdout)
        self.assertTrue(report['AllEncodedDefinitionsMatch'])
        self.assertFalse(report['ExpectedRegistriesMatch'])

    def test_private_or_malformed_input_is_never_echoed(self):
        for text in ('PRIVATE_NEVER_PRINT', diag.INSTALLER.read_text() + '\nPRIVATE_NEVER_PRINT'):
            result = self.run_input(text)
            self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        self.assertEqual(2, self.run_input('PRIVATE_NEVER_PRINT').returncode)

    def test_crlf_does_not_change_embedded_utf16_module_hashes(self):
        result = self.run_input(diag.INSTALLER.read_text().replace('\n', '\r\n'))
        self.assertEqual(0, result.returncode)
        self.assertTrue(json.loads(result.stdout)['AllEncodedDefinitionsMatch'])


if __name__ == '__main__':
    unittest.main(verbosity=2)

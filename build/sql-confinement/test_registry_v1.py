"""Public-source reproducibility and historical-verifier preservation contracts."""
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / 'docs/sql/ai-integration'

class RegistryV1GenerationTest(unittest.TestCase):
    def test_checked_in_artifacts_are_current_without_rewriting(self):
        subprocess.run([sys.executable, str(ROOT / 'build/sql-confinement/generate-registry-v1.py'), '--check'], check=True)

    def test_private_helper_is_exactly_pinned_and_runs_as_caller(self):
        prepare = (SQL / 'universal-pr3-confinement-prepare.sql').read_text()
        encoded = re.search(r'DECLARE @RegistryDefinition nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)', prepare).group(1)
        raw = bytes.fromhex(encoded)
        source = hashlib.sha256(raw).hexdigest()
        body = raw.decode('utf-16le')
        self.assertTrue(body.startswith('CREATE PROCEDURE dbo.AiRegistryConfinementV1'))
        self.assertNotIn('EXECUTE AS', body.upper())
        self.assertIn('DENY EXECUTE ON dbo.AiRegistryConfinementV1 TO public;', prepare)
        for path in [SQL/'universal-pr3.sql', SQL/'universal-pr3-confinement-verify.sql', ROOT/'shale-data/src/main/resources/ai/registry-confinement-v1-catalog.sql']:
            self.assertIn('0x'+source, path.read_text(), str(path))
        self.assertNotIn('sp_updateextendedproperty', prepare.lower())

    def test_both_historical_verifiers_are_embedded_without_changes(self):
        prepare = (SQL / 'universal-pr3-confinement-prepare.sql').read_text()
        for name in ['phase3b7-confinement-verify.sql', 'phase3b8-confinement-verify.sql']:
            historical = (SQL/name).read_text()
            self.assertIn("EXEC(N'" + historical.replace("'", "''") + "');", prepare)

if __name__ == '__main__':
    unittest.main()

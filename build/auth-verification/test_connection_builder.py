"""Protect pre-Open construction; source checks never count as native PS 5.1 verification."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
HARNESS = ROOT / 'docs/sql/auth/invocation-validation/harness.ps1'
NATIVE = Path(__file__).with_suffix('.ps1')


class ConnectionBuilderTest(unittest.TestCase):
    def test_all_builder_writes_use_canonical_dictionary_keys(self):
        source = HARNESS.read_text(encoding='utf-8')
        self.assertNotRegex(source, r'\$builder\s*\.\s*\w+\s*=',
                            'Builder property writes break dictionary adaptation in PS 5.1; use canonical keys')
        assignments = re.findall(r"\$builder\s*\[\s*'([^']+)'\s*\]\s*=\s*([^;\r\n]+)", source)
        self.assertEqual({key: value.strip() for key, value in assignments}, {
            'Data Source': '$f.Endpoint', 'Initial Catalog': '$f.Database',
            'Encrypt': '$true', 'TrustServerCertificate': '$false', 'Connect Timeout': '30',
            'Pooling': '$false', 'Asynchronous Processing': '$true',
            'Application Name': "'SyntheticInvocationValidation'",
        }, 'Preserve endpoint, database and every transport/async setting')
        self.assertEqual(8, len(assignments), 'Do not override a guarded setting later')
        self.assertNotRegex(source, r'(?i)\$builder\s*\.\s*ConnectionString\b',
                            'Use the explicit CLR getter for the related connection-string read')
        self.assertRegex(source, r'\$builder\s*\.\s*get_ConnectionString\s*\(\s*\)')

    @unittest.skipUnless(os.name == 'nt', 'Native Windows PowerShell 5.1 unavailable; source checks only')
    def test_native_windows_powershell_51_offline_construction(self):
        powershell = shutil.which('powershell.exe')
        self.assertIsNotNone(powershell, 'Windows requires powershell.exe; pwsh is not native PS 5.1')
        result = subprocess.run([powershell, '-NoLogo', '-NoProfile', '-NonInteractive',
                                 '-File', str(NATIVE)], cwd=ROOT, text=True, capture_output=True, timeout=60)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('PASS native Windows PowerShell 5.1.', result.stdout)


if __name__ == '__main__':
    unittest.main()

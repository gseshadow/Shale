"""Exercise real Git checkout conversion; never normalize SQL before receipt verification."""
from pathlib import Path
import hashlib
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = ('docs/sql/ai-integration/*.sql', 'shale-data/src/main/resources/ai/*.sql',
            'shale-data/src/main/resources/ai/confinement*-modules.properties',
            'build/sql-confinement/*.sql', 'build/sql-confinement/*.sha256',
            'build/sql-confinement/reviewed-case-module-variants-v2.json')


class PinnedCheckout(unittest.TestCase):
    def test_windows_autocrlf_checkout_preserves_every_reviewed_blob(self):
        paths = sorted({p.relative_to(ROOT).as_posix() for pattern in PATTERNS for p in ROOT.glob(pattern)})
        self.assertIn('docs/sql/ai-integration/phase3b8-confinement.sql', paths)
        self.assertIn('docs/sql/ai-integration/phase3b7-confinement.sql', paths)
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            def git(*args):
                return subprocess.run(['git', *args], cwd=repo, check=True, capture_output=True).stdout
            git('init', '-q')
            git('config', 'core.autocrlf', 'false')
            git('config', 'core.attributesFile', str(repo / 'no-global-attributes'))
            (repo / '.gitattributes').write_bytes((ROOT / '.gitattributes').read_bytes())
            expected = {}
            for path in paths:
                # Start with committed blobs, not a text reader that might hide CRLF conversion.
                raw = subprocess.check_output(['git', 'show', 'HEAD:' + path], cwd=ROOT)
                self.assertNotIn(b'\r\n', raw, 'Review is required before changing a historical blob')
                expected[path] = raw
                target = repo / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(raw)
            (repo / 'unrelated.txt').write_bytes(b'ordinary checkout\n')
            git('add', '.')
            git('-c', 'core.autocrlf=true', 'checkout-index', '--all', '--force', '--prefix=checkout/')
            for path, raw in expected.items():
                self.assertEqual(raw, (repo / 'checkout' / path).read_bytes(), path)
            self.assertEqual(b'ordinary checkout\r\n', (repo / 'checkout/unrelated.txt').read_bytes(),
                             'The test must actually exercise Windows-style Git conversion')
            for name, receipt in [('phase3b7', 'b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3'),
                                  ('phase3b8', '60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891')]:
                raw = (repo / 'checkout/docs/sql/ai-integration' / (name + '-confinement.sql')).read_bytes()
                match = re.search(rb'@sourceHash varbinary\(32\)=0x([0-9a-f]{64})', raw)
                self.assertIsNotNone(match)
                zeroed = raw[:match.start(1)] + b'0' * 64 + raw[match.end(1):]
                self.assertEqual(receipt, match[1].decode())
                self.assertEqual(receipt, hashlib.sha256(zeroed).hexdigest())


if __name__ == '__main__':
    unittest.main()

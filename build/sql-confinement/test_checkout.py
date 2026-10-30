"""Exercise real Git checkout conversion; never normalize SQL before receipt verification."""
from pathlib import Path
from contextlib import contextmanager
import hashlib
import os
import sys
import checkout_bytes
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = checkout_bytes.PATTERNS


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
                self.assertFalse(b'\r\n' in raw, 'Review is required before changing a historical blob')
                expected[path] = raw
                target = repo / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(raw)
            (repo / 'unrelated.txt').write_bytes(b'ordinary checkout\n')
            git('add', '.')
            git('-c', 'core.autocrlf=true', 'checkout-index', '--all', '--force', '--prefix=checkout/')
            for path, raw in expected.items():
                self.assertTrue(raw == (repo / 'checkout' / path).read_bytes(), path + ' bytes differ')
            self.assertEqual(b'ordinary checkout\r\n', (repo / 'checkout/unrelated.txt').read_bytes(),
                             'The test must actually exercise Windows-style Git conversion')
            checkout_bytes.verify_receipts({path: (repo / 'checkout' / path).read_bytes() for path in paths})

    @contextmanager
    def existing_checkout(self, switch=True):
        paths = sorted({p.relative_to(ROOT).as_posix() for pattern in PATTERNS for p in ROOT.glob(pattern)})
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            def git(*args):
                env = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1')
                return subprocess.run(['git', *args], cwd=repo, env=env, check=True, capture_output=True).stdout
            git('init', '-q')
            git('config', 'user.name', 'Checkout Test')
            git('config', 'user.email', 'checkout@example.invalid')
            git('config', 'commit.gpgsign', 'false')
            git('config', 'core.autocrlf', 'false')
            git('config', 'core.attributesFile', str(repo / 'no-global-attributes'))
            expected = {}
            for path in paths:
                raw = subprocess.check_output(['git', 'show', 'HEAD:' + path], cwd=ROOT)
                expected[path] = raw
                target = repo / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(raw)
            git('add', '.')
            git('commit', '-qm', 'Pre-attributes checkout')
            git('branch', 'before')
            git('config', 'core.autocrlf', 'true')
            for path in paths:
                (repo / path).unlink()
            # --index records the real existing checkout's stat data, unlike a fresh prefix.
            git('checkout-index', '--force', '--index', '--', *paths)
            (repo / '.gitattributes').write_bytes((ROOT / '.gitattributes').read_bytes())
            git('add', '.gitattributes')
            git('commit', '-qm', 'Introduce LF attributes without changing SQL blobs')
            git('branch', 'after')
            if switch:
                git('switch', '-q', 'before')
                git('switch', '-q', 'after')
            git('diff', '--quiet')
            git('status', '--porcelain')
            self.assertFalse(git('status', '--porcelain'), 'Model the operator clean index/worktree')
            target = repo / 'docs/sql/ai-integration/phase3b8-confinement.sql'
            self.assertEqual(4471, target.read_bytes().count(b'\r\n'))
            self.assertEqual([b'i/lf', b'w/crlf'], git('ls-files', '--eol', '--', target.relative_to(repo).as_posix()).split()[:2])
            self.assertEqual('9c93d15bb1584f64bb9be673c30f558b20a93753c9759335808a256cc8b9d861', hashlib.sha256(target.read_bytes()).hexdigest())
            self.assertEqual('7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8', hashlib.sha256(expected[target.relative_to(repo).as_posix()]).hexdigest())
            self.assertIn(b'eol: lf', git('check-attr', 'eol', '--', target.relative_to(repo).as_posix()))
            yield repo, expected, git

    def assert_refreshed(self, repo, expected):
        count, receipts = checkout_bytes.refresh(repo)
        for path, raw in expected.items():
            self.assertTrue(raw == (repo / path).read_bytes(), path + ' must be byte-exact after refresh')
        self.assertEqual(len(expected), count)
        self.assertEqual({v: r for v, (_, r) in checkout_bytes.RECEIPTS.items()}, receipts)
        self.assertEqual(len(expected), checkout_bytes.verify(repo)[0])

    def test_existing_cached_crlf_worktree_refresh_rewrites_actual_bytes(self):
        with self.existing_checkout() as (repo, expected, git):
            target = repo / 'docs/sql/ai-integration/phase3b8-confinement.sql'
            before = target.stat()
            git('checkout-index', '--force', '--', *expected)
            self.assertEqual(4471, target.read_bytes().count(b'\r\n'), 'Old refresh reproduces the real defect')
            self.assertEqual(before.st_mtime_ns, target.stat().st_mtime_ns, 'Old refresh leaves cached file untouched')
            with self.assertRaises(ValueError):
                checkout_bytes.verify(repo)
            self.assert_refreshed(repo, expected)

    def test_attributes_introduced_into_existing_crlf_copy_without_switch(self):
        with self.existing_checkout(switch=False) as (repo, expected, git):
            self.assert_refreshed(repo, expected)

    def test_clearing_only_cached_index_stat_makes_old_command_rewrite_lf(self):
        with self.existing_checkout() as (repo, expected, git):
            path = 'docs/sql/ai-integration/phase3b8-confinement.sql'
            oid = git('rev-parse', ':' + path).decode().strip()
            # Diagnostic in this isolated fixture only, not the operator repair procedure.
            git('update-index', '--cacheinfo', '100644,' + oid + ',' + path)
            git('checkout-index', '--force', '--', path)
            self.assertEqual(oid, git('rev-parse', ':' + path).decode().strip(), 'No index blob normalization occurred')
            self.assertTrue(expected[path] == (repo / path).read_bytes(), 'Clearing cached stat alone changes the old checkout outcome')

    def test_fresh_clone_with_autocrlf_true_has_exact_bytes(self):
        with self.existing_checkout() as (repo, expected, git), tempfile.TemporaryDirectory() as directory:
            clone = Path(directory) / 'clone'
            git('clone', '-q', '-c', 'core.autocrlf=true', '-c', 'core.attributesFile=' + str(Path(directory) / 'no-attributes'), str(repo), str(clone))
            for path, raw in expected.items():
                self.assertTrue(raw == (clone / path).read_bytes(), path + ' fresh clone bytes differ')
            self.assertEqual(len(expected), checkout_bytes.verify(clone)[0])

    def test_refresh_refuses_real_worktree_edits_without_overwriting_other_files(self):
        with self.existing_checkout() as (repo, expected, git):
            path = repo / 'docs/sql/ai-integration/phase3b8-confinement.sql'
            edited = path.read_bytes() + b'-- local edit\r\n'
            path.write_bytes(edited)
            before = {p: (repo / p).read_bytes() for p in expected}
            with self.assertRaises(ValueError):
                checkout_bytes.refresh(repo)
            self.assertTrue(all((repo / p).read_bytes() == raw for p, raw in before.items()))

    def test_refresh_refuses_staged_changes_and_raw_receipts_refuse_crlf(self):
        with self.existing_checkout() as (repo, expected, git):
            path = 'docs/sql/ai-integration/phase3b8-confinement.sql'
            with self.assertRaises(ValueError):
                checkout_bytes.source_receipt((repo / path).read_bytes(), checkout_bytes.RECEIPTS['V2'][1])
            (repo / path).write_bytes(expected[path] + b'-- staged edit\n')
            git('add', '--', path)
            with self.assertRaises(ValueError):
                checkout_bytes.refresh(repo)

    def test_refresh_preserves_unrelated_crlf_files_and_is_idempotent(self):
        with self.existing_checkout() as (repo, expected, git):
            unrelated = repo / 'unrelated.txt'
            unrelated.write_bytes(b'leave this alone\r\n')
            self.assert_refreshed(repo, expected)
            self.assert_refreshed(repo, expected)
            self.assertEqual(b'leave this alone\r\n', unrelated.read_bytes())

    def test_operator_cli_rejects_actual_crlf_then_refreshes_and_verifies_receipts(self):
        with self.existing_checkout() as (repo, expected, git):
            script = repo / 'build/sql-confinement/checkout_bytes.py'
            script.write_bytes(Path(checkout_bytes.__file__).read_bytes())
            def run(*args):
                return subprocess.run([sys.executable, str(script), *args], cwd=repo, capture_output=True, text=True)
            result = run()
            self.assertEqual(1, result.returncode)
            self.assertIn('PINNED_CHECKOUT FAIL', result.stdout)
            result = run('--refresh')
            self.assertEqual(0, result.returncode, result.stdout)
            self.assertIn('PINNED_CHECKOUT PASS files=25', result.stdout)
            for version, (_, receipt) in checkout_bytes.RECEIPTS.items():
                self.assertIn(f'PINNED_RECEIPT {version} PASS {receipt}', result.stdout)
            self.assertEqual(0, run().returncode)



if __name__ == '__main__':
    unittest.main()

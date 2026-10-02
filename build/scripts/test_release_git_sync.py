import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("release_git_sync.py")
SPEC = importlib.util.spec_from_file_location("release_git_sync", SCRIPT)
SYNC = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(SYNC)


def run(cwd, *args, check=True):
    return subprocess.run(args, cwd=cwd, check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


class ReleaseGitSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        base = Path(self.temp.name)
        self.remote = base / "remote.git"
        self.repo = base / "work"
        run(base, "git", "init", "--bare", str(self.remote))
        run(base, "git", "clone", str(self.remote), str(self.repo))
        run(self.repo, "git", "config", "user.name", "Release Test")
        run(self.repo, "git", "config", "user.email", "release@example.invalid")
        for relative in SYNC.RELEASE_FILES:
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("old\n", encoding="utf-8")
        (self.repo / "unrelated.txt").write_text("original\n", encoding="utf-8")
        run(self.repo, "git", "add", ".")
        run(self.repo, "git", "commit", "-m", "initial")
        run(self.repo, "git", "push", "--set-upstream", "origin", "HEAD")

    def tearDown(self):
        self.temp.cleanup()

    def test_preflight_allows_committed_local_ahead_and_rejects_dirty_paths(self):
        (self.repo / "unrelated.txt").write_text("committed ahead\n", encoding="utf-8")
        run(self.repo, "git", "commit", "-am", "local work")
        SYNC.preflight(self.repo)

        (self.repo / "unrelated.txt").write_text("ambiguous\n", encoding="utf-8")
        with self.assertRaisesRegex(SYNC.GitFailure, "unrelated.txt"):
            SYNC.preflight(self.repo)

    def test_preflight_rejects_upstream_ahead_and_diverged(self):
        peer = Path(self.temp.name) / "peer"
        run(peer.parent, "git", "clone", str(self.remote), str(peer))
        run(peer, "git", "config", "user.name", "Peer")
        run(peer, "git", "config", "user.email", "peer@example.invalid")
        (peer / "remote.txt").write_text("remote\n", encoding="utf-8")
        run(peer, "git", "add", "remote.txt")
        run(peer, "git", "commit", "-m", "remote")
        run(peer, "git", "push")
        with self.assertRaisesRegex(SYNC.GitFailure, "behind"):
            SYNC.preflight(self.repo)

        (self.repo / "local.txt").write_text("local\n", encoding="utf-8")
        run(self.repo, "git", "add", "local.txt")
        run(self.repo, "git", "commit", "-m", "local")
        with self.assertRaisesRegex(SYNC.GitFailure, "diverged"):
            SYNC.preflight(self.repo)

    def test_sync_stages_only_release_files_and_pushes_ahead_source_commits(self):
        (self.repo / "unrelated.txt").write_text("committed source work\n", encoding="utf-8")
        run(self.repo, "git", "commit", "-am", "source work")
        (self.repo / "pom.xml").write_text("release\n", encoding="utf-8")
        ignored = self.repo / "dist" / "secret.bin"
        ignored.parent.mkdir()
        ignored.write_text("do not stage\n", encoding="utf-8")
        run(self.repo, "git", "status", "--short")

        SYNC.synchronize(self.repo, "1.2.3")

        self.assertEqual("Release Shale 1.2.3", run(self.repo, "git", "log", "-1", "--pretty=%s").stdout.strip())
        self.assertEqual("?? dist/", run(self.repo, "git", "status", "--short").stdout.strip())
        remote_log = run(self.repo, "git", "log", "@{upstream}", "-2", "--pretty=%s").stdout
        self.assertIn("source work", remote_log)

    def test_push_failure_preserves_commit_and_clean_retry_does_not_duplicate_it(self):
        (self.repo / "pom.xml").write_text("release\n", encoding="utf-8")
        run(self.repo, "git", "remote", "set-url", "--push", "origin", str(self.remote) + "-missing")
        with self.assertRaisesRegex(SYNC.GitFailure, "push"):
            SYNC.synchronize(self.repo, "2.0.0")
        self.assertEqual("Release Shale 2.0.0", run(self.repo, "git", "log", "-1", "--pretty=%s").stdout.strip())
        before = run(self.repo, "git", "rev-list", "--count", "HEAD").stdout

        run(self.repo, "git", "remote", "set-url", "--push", "origin", str(self.remote))
        SYNC.synchronize(self.repo, "2.0.0")
        self.assertEqual(before, run(self.repo, "git", "rev-list", "--count", "HEAD").stdout)

    def test_orchestrator_syncs_after_release_and_before_publication(self):
        source = Path(__file__).with_name("release-and-publish.bat").read_text(encoding="utf-8")
        release = source.index('release.bat"')
        sync = source.index('release_git_sync.py" sync')
        publish = source.index('publish-update.bat"')
        self.assertLess(release, sync)
        self.assertLess(sync, publish)
        self.assertNotIn("git add .", source.lower())
        self.assertIn("publish-update.bat", source[source.index(":fail"):])


if __name__ == "__main__":
    unittest.main()

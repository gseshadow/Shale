import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "build/scripts/mac_release_bootstrap.py"
WORKSPACE_PATH = ROOT / "build/scripts/mac_release_workspace.py"
SPEC = importlib.util.spec_from_file_location("mac_release_bootstrap", SCRIPT_PATH)
BOOTSTRAP = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(BOOTSTRAP)


def run(root: Path, *args: str, check: bool = True, input_text: str | None = None):
    return subprocess.run(
        args, cwd=root, text=True, input=input_text, capture_output=True, check=check
    )


class MacReleaseBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        base = Path(self.temp.name)
        self.remote = base / "remote.git"
        self.repo = base / "checkout"
        run(base, "git", "init", "--bare", str(self.remote))
        run(base, "git", "init", "-b", "main", str(self.repo))
        run(self.repo, "git", "config", "user.email", "test@example.com")
        run(self.repo, "git", "config", "user.name", "Test")
        run(self.repo, "git", "remote", "add", "origin", str(self.remote))
        for pom in ("pom.xml", "shale-core/pom.xml", "shale-data/pom.xml",
                    "shale-ui/pom.xml", "shale-desktop/pom.xml",
                    "shale-updater/pom.xml", "shale-server/pom.xml"):
            path = self.repo / pom
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("base\n", encoding="utf-8")
        scripts = self.repo / "build/scripts"
        scripts.mkdir(parents=True)
        (scripts / "mac_release_workspace.py").write_text(
            WORKSPACE_PATH.read_text(encoding="utf-8"), encoding="utf-8"
        )
        self.prepare = scripts / "prepare-shale-mac-release.sh"
        self.prepare.write_text("#!/usr/bin/env bash\necho stale > \"$SHALE_RELEASE_ROOT/../marker\"\n", encoding="utf-8")
        (self.repo / "tracked.txt").write_text("safe\n", encoding="utf-8")
        run(self.repo, "git", "add", ".")
        run(self.repo, "git", "commit", "-m", "stale checkout")
        run(self.repo, "git", "push", "-u", "origin", "main")
        self.stale_revision = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()

        self.prepare.write_text(
            "#!/usr/bin/env bash\nset -eu\n"
            "python3 \"$SHALE_RELEASE_ROOT/build/scripts/mac_release_workspace.py\" "
            "--root \"$SHALE_RELEASE_ROOT\" prepare --remote origin --revision \"$3\"\n"
            "echo requested > \"$SHALE_RELEASE_ROOT/../marker\"\n",
            encoding="utf-8",
        )
        run(self.repo, "git", "add", str(self.prepare))
        run(self.repo, "git", "commit", "-m", "safe requested entrypoint")
        run(self.repo, "git", "push", "origin", "main")
        self.requested = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        run(self.repo, "git", "checkout", "--detach", self.stale_revision)

    def tearDown(self):
        self.temp.cleanup()

    def bootstrap(self, revision: str | None = None):
        return run(
            self.repo, "bash", "-s", "--", str(self.repo), "origin", "main",
            "1.2.3", revision or self.requested, BOOTSTRAP.RELEASE_SCRIPT,
            check=False, input_text=BOOTSTRAP.REMOTE_BOOTSTRAP,
        )

    def test_requested_script_overrides_stale_checkout_and_repairs_generated_poms(self):
        for pom in ("pom.xml", "shale-core/pom.xml", "shale-data/pom.xml",
                    "shale-ui/pom.xml", "shale-desktop/pom.xml",
                    "shale-updater/pom.xml", "shale-server/pom.xml"):
            (self.repo / pom).write_text("release-generated\n", encoding="utf-8")

        result = self.bootstrap()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("requested\n", (self.repo.parent / "marker").read_text(encoding="utf-8"))
        self.assertIn(f"requested revision: {self.requested}", result.stdout)
        self.assertEqual(self.requested, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_unrelated_dirty_file_fails_closed(self):
        (self.repo / "tracked.txt").write_text("preserve me\n", encoding="utf-8")
        result = self.bootstrap()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("tracked.txt", result.stderr)
        self.assertEqual("preserve me\n", (self.repo / "tracked.txt").read_text(encoding="utf-8"))
        self.assertFalse((self.repo.parent / "marker").exists())

    def test_unavailable_revision_fails_before_entrypoint_execution(self):
        result = self.bootstrap("f" * 40)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.repo.parent / "marker").exists())
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())

    def test_revision_without_release_script_fails_before_entrypoint_execution(self):
        run(self.repo, "git", "checkout", "main")
        run(self.repo, "git", "rm", BOOTSTRAP.RELEASE_SCRIPT)
        run(self.repo, "git", "commit", "-m", "missing release entrypoint")
        missing_script_revision = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        run(self.repo, "git", "push", "origin", "main")
        run(self.repo, "git", "checkout", "--detach", self.stale_revision)

        result = self.bootstrap(missing_script_revision)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.repo.parent / "marker").exists())
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())

    def test_ssh_invocation_transmits_bootstrap_instead_of_invoking_checkout_script(self):
        with mock.patch.object(BOOTSTRAP.subprocess, "run") as invoked:
            BOOTSTRAP.run_remote_bootstrap("mac", "/repo", "origin", "main", "1.2.3", "a" * 40)
        command = invoked.call_args.args[0]
        self.assertEqual(["ssh", "mac", "bash", "-s", "--"], command[:5])
        self.assertNotIn("./build/scripts/prepare-shale-mac-release.sh", command)
        self.assertEqual(BOOTSTRAP.REMOTE_BOOTSTRAP, invoked.call_args.kwargs["input"])

    def test_bootstrap_contains_no_broad_destructive_git_commands(self):
        sources = SCRIPT_PATH.read_text(encoding="utf-8") + (ROOT / "build/scripts/release-all.bat").read_text(encoding="utf-8")
        for forbidden in ("git reset --hard", "git clean -fd", "git stash"):
            self.assertNotIn(forbidden, sources)


if __name__ == "__main__":
    unittest.main()

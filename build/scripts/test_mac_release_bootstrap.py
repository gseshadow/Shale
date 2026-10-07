import importlib.util
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "build/scripts/mac_release_bootstrap.py"
WORKSPACE_PATH = ROOT / "build/scripts/mac_release_workspace.py"
PREPARE_PATH = ROOT / "build/scripts/prepare-shale-mac-release.sh"
SPEC = importlib.util.spec_from_file_location("mac_release_bootstrap", SCRIPT_PATH)
BOOTSTRAP = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(BOOTSTRAP)


def run(root: Path, *args: str, check: bool = True, input_text: str | None = None,
        env: dict[str, str] | None = None):
    return subprocess.run(
        args, cwd=root, text=True, input=input_text, capture_output=True, check=check, env=env
    )


def bootstrap_tools(base: Path) -> tuple[str, dict[str, str]]:
    env = {**os.environ, "TMPDIR": base.as_posix()}
    if os.name != "nt":
        return shutil.which("bash") or "bash", env

    # Windows' bash.exe can be a WSL launcher even when Git Bash is installed.
    git_path = Path(shutil.which("git") or "git").resolve()
    roots = [*git_path.parents,
             Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git",
             Path(os.environ.get("LOCALAPPDATA", "")) / "Programs/Git"]
    for root in roots:
        bash = root / "bin/bash.exe"
        utilities = root / "usr/bin"
        if bash.is_file() and (utilities / "mktemp.exe").is_file():
            break
    else:
        raise RuntimeError("Bootstrap tests require Git Bash on Windows; install Git for Windows.")

    # Use the interpreter running unittest, avoiding Windows' python3 Store alias.
    tools = base / "tools"
    tools.mkdir()
    (tools / "python3").write_text(
        "#!/usr/bin/env bash\nexec " + shlex.quote(Path(sys.executable).as_posix()) + ' "$@"\n',
        encoding="utf-8", newline="\n",
    )
    env["PATH"] = os.pathsep.join([str(tools), str(utilities), str(bash.parent), env["PATH"]])
    return str(bash), env


class MacReleaseBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name)
        self.bash, self.bootstrap_env = bootstrap_tools(base)
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
        self.prepare = scripts / "prepare-shale-mac-release.sh"
        self.prepare.write_text("#!/usr/bin/env bash\necho stale > \"$SHALE_RELEASE_ROOT/../marker\"\n", encoding="utf-8")
        (self.repo / "tracked.txt").write_text("safe\n", encoding="utf-8")
        run(self.repo, "git", "add", ".")
        run(self.repo, "git", "commit", "-m", "stale checkout")
        run(self.repo, "git", "push", "-u", "origin", "main")
        self.stale_revision = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()

        (scripts / "mac_release_workspace.py").write_text(
            WORKSPACE_PATH.read_text(encoding="utf-8"), encoding="utf-8"
        )
        # Exercise the production pre-sync code, stopping before packaging.
        # Only the platform/tool checks are stubbed; Git and Python are real.
        prepare_source = PREPARE_PATH.read_text(encoding="utf-8")
        self.prepare.write_text(
            "#!/usr/bin/env bash\nuname() { echo Darwin; }\nmvn() { :; }\n"
            + prepare_source.split("PREVIOUS_VERSION=", 1)[0]
            + "echo requested > \"$SHALE_RELEASE_ROOT/../marker\"\n",
            encoding="utf-8",
        )
        run(self.repo, "git", "add", "build/scripts")
        run(self.repo, "git", "commit", "-m", "safe requested entrypoint")
        run(self.repo, "git", "push", "origin", "main")
        self.requested = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        run(self.repo, "git", "checkout", "--detach", self.stale_revision)

    def tearDown(self):
        self.temp.cleanup()

    def bootstrap(self, revision: str | None = None):
        result = run(
            self.repo, self.bash, "-s", "--", str(self.repo), "origin", "main",
            "1.2.3", revision or self.requested, BOOTSTRAP.RELEASE_SCRIPT,
            check=False, input_text=BOOTSTRAP.REMOTE_BOOTSTRAP,
            env=self.bootstrap_env,
        )
        self.assertEqual([], list(Path(self.temp.name).glob("shale-mac-*")),
                         "Bootstrap must remove temporary scripts on success and failure")
        return result

    def publish_entrypoint(self, source: str) -> None:
        run(self.repo, "git", "checkout", "main")
        self.prepare.write_text(source, encoding="utf-8")
        run(self.repo, "git", "add", "build/scripts")
        run(self.repo, "git", "commit", "-m", "requested entrypoint variant")
        run(self.repo, "git", "push", "origin", "main")
        self.requested = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        run(self.repo, "git", "checkout", "--detach", self.stale_revision)

    def test_requested_script_overrides_stale_checkout_and_repairs_generated_poms(self):
        self.assertFalse((self.repo / "build/scripts/mac_release_workspace.py").exists())
        for pom in ("pom.xml", "shale-core/pom.xml", "shale-data/pom.xml",
                    "shale-ui/pom.xml", "shale-desktop/pom.xml",
                    "shale-updater/pom.xml", "shale-server/pom.xml"):
            (self.repo / pom).write_text("release-generated\n", encoding="utf-8")

        for relative in ("build/tmp/macos-runtime-image/bin/java",
                         "build/tmp/macos-runtime-smoke/smoke.txt",
                         "dist-macos/Shale-1.0.135.dmg", "dist-macos/shale-mac-release.json"):
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("previous release\n", encoding="utf-8")

        result = self.bootstrap()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.repo / "build/tmp").exists())
        self.assertFalse((self.repo / "dist-macos").exists())
        self.assertEqual("requested\n", (self.repo.parent / "marker").read_text(encoding="utf-8"))
        self.assertIn(f"requested revision: {self.requested}", result.stdout)
        self.assertIn(f"workspace helper from requested revision: {self.requested}", result.stdout)
        self.assertEqual(self.requested, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_stale_workspace_helper_is_not_executed(self):
        helper = self.repo / "build/scripts/mac_release_workspace.py"
        helper.write_text("raise SystemExit('stale helper must never run')\n", encoding="utf-8")
        run(self.repo, "git", "add", "build/scripts/mac_release_workspace.py")
        run(self.repo, "git", "commit", "-m", "stale helper")

        result = self.bootstrap()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.requested, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_tampered_workspace_helper_fails_before_workspace_mutation(self):
        source = run(self.repo, "git", "show", f"{self.requested}:{BOOTSTRAP.RELEASE_SCRIPT}").stdout
        tamper = (
            'git() { command git "$@"; '
            'if [[ "$1" == show && "$2" == *:build/scripts/mac_release_workspace.py ]]; '
            'then echo "# tampered"; fi; }\n'
        )
        self.publish_entrypoint(source.replace("set -euo pipefail\n", "set -euo pipefail\n" + tamper, 1))
        (self.repo / "pom.xml").write_text("release-generated\n", encoding="utf-8")

        result = self.bootstrap()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("workspace helper content did not match requested revision", result.stderr)
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("release-generated\n", (self.repo / "pom.xml").read_text(encoding="utf-8"))
        self.assertFalse((self.repo.parent / "marker").exists())

    def test_failure_cleanup_uses_verified_helper_and_preserves_late_changes(self):
        source = run(self.repo, "git", "show", f"{self.requested}:{BOOTSTRAP.RELEASE_SCRIPT}").stdout
        self.publish_entrypoint(
            source
            + 'echo release-generated > "$ROOT/pom.xml"\n'
            + 'echo "raise SystemExit(99)" > "$ROOT/build/scripts/mac_release_workspace.py"\n'
            + 'echo late-change > "$ROOT/tracked.txt"\nexit 23\n'
        )

        result = self.bootstrap()

        self.assertEqual(23, result.returncode, result.stderr)
        self.assertEqual("base\n", (self.repo / "pom.xml").read_text(encoding="utf-8"))
        self.assertEqual("late-change\n", (self.repo / "tracked.txt").read_text(encoding="utf-8"))
        self.assertEqual("raise SystemExit(99)\n", (self.repo / "build/scripts/mac_release_workspace.py").read_text(encoding="utf-8"))

    def test_revision_without_workspace_helper_fails_before_workspace_mutation(self):
        run(self.repo, "git", "checkout", "main")
        run(self.repo, "git", "rm", "build/scripts/mac_release_workspace.py")
        run(self.repo, "git", "commit", "-m", "missing workspace helper")
        missing_helper_revision = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        run(self.repo, "git", "push", "origin", "main")
        run(self.repo, "git", "checkout", "--detach", self.stale_revision)
        (self.repo / "pom.xml").write_text("release-generated\n", encoding="utf-8")

        result = self.bootstrap(missing_helper_revision)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.repo.parent / "marker").exists())
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("release-generated\n", (self.repo / "pom.xml").read_text(encoding="utf-8"))

    def test_unrelated_dirty_file_fails_closed(self):
        (self.repo / "tracked.txt").write_text("preserve me\n", encoding="utf-8")
        result = self.bootstrap()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("tracked.txt", result.stderr)
        self.assertEqual("preserve me\n", (self.repo / "tracked.txt").read_text(encoding="utf-8"))
        self.assertFalse((self.repo.parent / "marker").exists())
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())

    def test_unrelated_untracked_file_fails_closed_with_missing_stale_helper(self):
        (self.repo / "notes.txt").write_text("preserve me\n", encoding="utf-8")
        (self.repo / "pom.xml").write_text("release-generated\n", encoding="utf-8")
        result = self.bootstrap()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("notes.txt", result.stderr)
        self.assertEqual("preserve me\n", (self.repo / "notes.txt").read_text(encoding="utf-8"))
        self.assertEqual("release-generated\n", (self.repo / "pom.xml").read_text(encoding="utf-8"))
        self.assertFalse((self.repo.parent / "marker").exists())
        self.assertEqual(self.stale_revision, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())

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
        self.assertEqual(
            ["ssh", "mac", "bash", "-s", "--", "/repo", "origin", "main",
             "1.2.3", "a" * 40, BOOTSTRAP.RELEASE_SCRIPT],
            command,
        )
        self.assertNotIn("./build/scripts/prepare-shale-mac-release.sh", command)
        transmitted = invoked.call_args.kwargs["input"]
        self.assertIsInstance(transmitted, bytes)
        self.assertNotIn(b"\r", transmitted)
        self.assertEqual(BOOTSTRAP.REMOTE_BOOTSTRAP.encode("utf-8"), transmitted)
        self.assertNotIn("text", invoked.call_args.kwargs)
        self.assertNotIn("stdout", invoked.call_args.kwargs)
        self.assertNotIn("stderr", invoked.call_args.kwargs)
        self.assertNotIn("capture_output", invoked.call_args.kwargs)
        self.assertTrue(invoked.call_args.kwargs["check"])

    def test_ssh_invocation_normalizes_windows_and_legacy_mac_line_endings_to_lf(self):
        windows_source = "set -eu\r\necho first\recho second\r\n"
        with (mock.patch.object(BOOTSTRAP, "REMOTE_BOOTSTRAP", windows_source),
              mock.patch.object(BOOTSTRAP.subprocess, "run") as invoked):
            BOOTSTRAP.run_remote_bootstrap("mac", "/repo", "origin", "main", "1.2.3", "a" * 40)

        transmitted = invoked.call_args.kwargs["input"]
        self.assertEqual(b"set -eu\necho first\necho second\n", transmitted)
        self.assertNotIn(b"\r", transmitted)
        self.assertEqual(transmitted.count(b"\n"), len(transmitted.splitlines()))

    def test_invalid_or_non_full_revision_is_rejected_before_ssh(self):
        for revision in ("a" * 39, "a" * 41, "g" * 40, ""):
            with self.subTest(revision=revision), mock.patch.object(BOOTSTRAP.subprocess, "run") as invoked:
                with self.assertRaisesRegex(
                    ValueError, "source revision must be a full 40-character Git SHA"
                ):
                    BOOTSTRAP.run_remote_bootstrap(
                        "mac", "/repo", "origin", "main", "1.2.3", revision
                    )
                invoked.assert_not_called()

    def test_bootstrap_contains_no_broad_destructive_git_commands(self):
        sources = SCRIPT_PATH.read_text(encoding="utf-8") + (ROOT / "build/scripts/release-all.bat").read_text(encoding="utf-8")
        for forbidden in ("git reset --hard", "git clean -fd", "git stash"):
            self.assertNotIn(forbidden, sources)


if __name__ == "__main__":
    unittest.main()

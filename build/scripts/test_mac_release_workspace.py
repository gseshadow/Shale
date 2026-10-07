import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "build/scripts/mac_release_workspace.py"
PREPARE_PATH = ROOT / "build/scripts/prepare-shale-mac-release.sh"
SPEC = importlib.util.spec_from_file_location("mac_release_workspace", SCRIPT_PATH)
WORKSPACE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(WORKSPACE)


def run(root: Path, *args: str, check: bool = True):
    return subprocess.run(args, cwd=root, text=True, capture_output=True, check=check)


class MacReleaseWorkspaceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        base = Path(self.temp.name)
        self.remote = base / "remote.git"
        self.repo = base / "checkout"
        run(base, "git", "init", "--bare", str(self.remote))
        run(base, "git", "init", "-b", "main", str(self.repo))
        run(self.repo, "git", "config", "user.email", "test@example.com")
        run(self.repo, "git", "config", "user.name", "Test")
        for pom in WORKSPACE.GENERATED_POMS:
            path = self.repo / pom
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("1.0.0\n", encoding="utf-8")
        (self.repo / "tracked.txt").write_text("safe\n", encoding="utf-8")
        run(self.repo, "git", "add", ".")
        run(self.repo, "git", "commit", "-m", "base")
        run(self.repo, "git", "remote", "add", "origin", str(self.remote))
        run(self.repo, "git", "push", "-u", "origin", "main")
        self.requested = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()

    def tearDown(self):
        self.temp.cleanup()

    def test_known_pom_only_changes_are_cleaned_and_requested_sha_becomes_head(self):
        for pom in WORKSPACE.GENERATED_POMS:
            (self.repo / pom).write_text("2.0.0\n", encoding="utf-8")
        WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertEqual(self.requested, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def previous_outputs(self):
        for relative in (
            "build/tmp/macos-runtime-image/bin/java",
            "build/tmp/macos-runtime-smoke/smoke.txt",
            "dist-macos/Shale-1.0.135.dmg",
            "dist-macos/shale-mac-release.json",
        ):
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("previous release\n", encoding="utf-8")

    def assert_outputs_removed(self):
        for relative in WORKSPACE.GENERATED_OUTPUTS:
            self.assertFalse((self.repo / relative).exists(), relative)

    def test_previous_release_outputs_are_removed_before_check_and_cleanup_is_repeatable(self):
        # Exercise an old checkout without ignore rules and one with ignored output.
        for ignored in (False, True):
            with self.subTest(ignored=ignored):
                if ignored:
                    (self.repo / ".git/info/exclude").write_text(
                        "/build/tmp/\n/dist-macos/\n", encoding="utf-8"
                    )
                self.previous_outputs()
                (self.repo / "pom.xml").write_text("release version\n", encoding="utf-8")
                WORKSPACE.prepare(self.repo, "origin", self.requested)
                self.assert_outputs_removed()
                WORKSPACE.prepare(self.repo, "origin", self.requested)
                self.assertEqual(self.requested, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())
                self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_tracked_generated_path_is_refused_even_after_staged_deletion(self):
        path = self.repo / "dist-macos/versioned.txt"
        path.parent.mkdir()
        path.write_text("preserve\n", encoding="utf-8")
        run(self.repo, "git", "add", "dist-macos")
        run(self.repo, "git", "commit", "-m", "tracked output")
        for staged_deletion in (False, True):
            with self.subTest(staged_deletion=staged_deletion):
                if staged_deletion:
                    run(self.repo, "git", "rm", "--cached", "dist-macos/versioned.txt")
                with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "tracked paths"):
                    WORKSPACE.prepare(self.repo, "origin", self.requested)
                self.assertEqual("preserve\n", path.read_text(encoding="utf-8"))

    def test_staged_generated_path_is_refused(self):
        self.previous_outputs()
        run(self.repo, "git", "add", "dist-macos")
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "tracked paths"):
            WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertTrue((self.repo / "build/tmp/macos-runtime-image/bin/java").exists())

    def test_symlink_targets_are_preserved(self):
        outside = self.repo.parent / "developer-files"
        outside.mkdir()
        marker = outside / "notes.txt"
        marker.write_text("preserve\n", encoding="utf-8")
        (self.repo / "dist-macos").symlink_to(outside, target_is_directory=True)
        nested = self.repo / "build/tmp/runtime-link"
        nested.parent.mkdir(parents=True)
        nested.symlink_to(outside, target_is_directory=True)
        WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assert_outputs_removed()
        self.assertFalse((self.repo / "dist-macos").is_symlink())
        self.assertEqual("preserve\n", marker.read_text(encoding="utf-8"))

    def test_symlinked_output_parent_is_refused_without_deleting_external_files(self):
        outside = self.repo.parent / "developer-build"
        (outside / "tmp").mkdir(parents=True)
        marker = outside / "tmp/notes.txt"
        marker.write_text("preserve\n", encoding="utf-8")
        (self.repo / "build").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "symlinked parent"):
            WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertEqual("preserve\n", marker.read_text(encoding="utf-8"))

    def test_subdirectory_root_is_refused(self):
        self.previous_outputs()
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "Git workspace root"):
            WORKSPACE.prepare(self.repo / "build", "origin", self.requested)
        self.assertTrue((self.repo / "dist-macos/Shale-1.0.135.dmg").exists())

    def test_unrelated_tracked_change_fails_closed(self):
        self.previous_outputs()
        (self.repo / "tracked.txt").write_text("do not discard\n", encoding="utf-8")
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "tracked.txt"):
            WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertEqual("do not discard\n", (self.repo / "tracked.txt").read_text(encoding="utf-8"))

    def test_unrelated_untracked_change_fails_closed(self):
        self.previous_outputs()
        (self.repo / "notes.txt").write_text("do not discard\n", encoding="utf-8")
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "notes.txt"):
            WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertTrue((self.repo / "notes.txt").exists())

    def test_unrelated_tracked_deletion_fails_closed(self):
        self.previous_outputs()
        (self.repo / "tracked.txt").unlink()
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "tracked.txt"):
            WORKSPACE.prepare(self.repo, "origin", self.requested)
        self.assertIn(" D tracked.txt", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_unknown_revision_is_rejected_without_moving_head(self):
        original = run(self.repo, "git", "rev-parse", "HEAD").stdout.strip()
        with self.assertRaisesRegex(WORKSPACE.WorkspaceError, "unavailable"):
            WORKSPACE.prepare(self.repo, "origin", "f" * 40)
        self.assertEqual(original, run(self.repo, "git", "rev-parse", "HEAD").stdout.strip())

    def test_cleanup_restores_generated_poms_after_success_or_failure(self):
        for simulated_result in ("success", "failure"):
            with self.subTest(simulated_result=simulated_result):
                (self.repo / "pom.xml").write_text(f"{simulated_result}\n", encoding="utf-8")
                WORKSPACE.cleanup_generated_poms(self.repo)
                self.assertEqual("", run(self.repo, "git", "status", "--porcelain").stdout)

    def test_failure_cleanup_restores_poms_without_discarding_unrelated_late_change(self):
        (self.repo / "pom.xml").write_text("release version\n", encoding="utf-8")
        (self.repo / "tracked.txt").write_text("build failure diagnostic\n", encoding="utf-8")
        WORKSPACE.cleanup_generated_poms(self.repo)
        status = run(self.repo, "git", "status", "--porcelain").stdout
        self.assertNotIn("pom.xml", status)
        self.assertIn("tracked.txt", status)

    def test_prepare_script_traps_cleanup_and_avoids_broad_destructive_git(self):
        source = PREPARE_PATH.read_text(encoding="utf-8")
        helper = SCRIPT_PATH.read_text(encoding="utf-8")
        self.assertIn("trap cleanup_release_poms EXIT", source)
        self.assertIn('prepare --remote "$REMOTE" --revision "$REQUESTED_REVISION"', source)
        for forbidden in ("git reset --hard", "git clean -fd", "git stash"):
            self.assertNotIn(forbidden, source)
            self.assertNotIn(forbidden, helper)


if __name__ == "__main__":
    unittest.main()

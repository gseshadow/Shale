import datetime as dt
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("prepare_release_notes.py")
sys.path.insert(0, str(SCRIPT.parent))
SPEC = importlib.util.spec_from_file_location("prepare_release_notes", SCRIPT)
PREPARE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(PREPARE)


def run(cwd, *args):
    return subprocess.run(args, cwd=cwd, check=True, text=True, stdout=subprocess.PIPE)


class PrepareReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "build/assets").mkdir(parents=True)
        (self.root / "build/assets/shale-stable.json").write_text(
            json.dumps({"version": "1.2.3"}), encoding="utf-8"
        )
        run(self.root, "git", "init")
        run(self.root, "git", "config", "user.name", "Release Test")
        run(self.root, "git", "config", "user.email", "release@example.invalid")
        run(self.root, "git", "add", ".")
        run(self.root, "git", "commit", "-m", "initial metadata")
        run(self.root, "git", "commit", "--allow-empty", "-m", "Release Shale 1.2.3")

    def tearDown(self):
        self.temp.cleanup()

    def commit(self, subject):
        run(self.root, "git", "commit", "--allow-empty", "-m", subject)

    def test_missing_notes_generates_versioned_valid_draft_from_deterministic_history(self):
        self.commit("Add export workflow")
        self.commit("Improve search ordering")
        self.commit("Fix blank results")
        self.commit("chore: bump version")
        path, created = PREPARE.prepare(self.root, "1.2.4", dt.date(2026, 10, 2))
        self.assertTrue(created)
        self.assertEqual(self.root / "release-notes/1.2.4.json", path)
        value = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual("1.2.4", value["version"])
        self.assertEqual("What's New in Shale 1.2.4", value["title"])
        self.assertEqual("2026-10-02", value["releaseDate"])
        self.assertEqual(["Add export workflow"], value["groups"]["New"])
        self.assertEqual(["Improve search ordering"], value["groups"]["Improvements"])
        self.assertEqual(["Fix blank results"], value["groups"]["Fixes"])
        self.assertNotIn("bump", json.dumps(value).lower())

    def test_existing_notes_are_never_overwritten(self):
        path = self.root / "release-notes/1.2.4.json"
        path.parent.mkdir()
        path.write_text("developer authored\n", encoding="utf-8")
        returned, created = PREPARE.prepare(self.root, "1.2.4", dt.date(2026, 10, 2))
        self.assertFalse(created)
        self.assertEqual(path, returned)
        self.assertEqual("developer authored\n", path.read_text(encoding="utf-8"))

    def test_empty_history_still_produces_a_valid_reviewable_draft(self):
        path, created = PREPARE.prepare(self.root, "1.2.4", dt.date(2026, 10, 2))
        self.assertTrue(created)
        value = PREPARE.load_notes(path, "1.2.4")
        self.assertIn("Review and edit", value["summary"])
        self.assertIn("replace this placeholder", value["groups"]["Improvements"][0])

    def test_manifest_version_selects_the_matching_release_commit_boundary(self):
        self.commit("Add current capability")
        self.commit("Release Shale 1.2.4")
        self.commit("Fix after current release")
        (self.root / "build/assets/shale-stable.json").write_text(
            json.dumps({"version": "1.2.4"}), encoding="utf-8"
        )
        value = PREPARE.draft(self.root, "1.2.5", dt.date(2026, 10, 2))
        self.assertEqual([], value["groups"]["New"])
        self.assertEqual(["Fix after current release"], value["groups"]["Fixes"])


if __name__ == "__main__":
    unittest.main()

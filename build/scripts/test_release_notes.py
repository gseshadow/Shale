import json
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("release_notes.py")
SPEC = importlib.util.spec_from_file_location("release_notes", SCRIPT)
release_notes = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(release_notes)


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.notes = self.root / "release-notes"
        self.notes.mkdir()
        self.manifest = self.root / "manifest.json"
        self.manifest.write_text(json.dumps({"version": "1.2.3", "notes": "Release 1.2.3"}), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def authored(self, **overrides):
        value = {"version": "1.2.3", "title": "What's New", "releaseDate": "2026-10-02",
                 "summary": "A concise summary.",
                 "groups": {"New": ["New workflow"], "Improvements": [], "Fixes": ["Fixed issue"]}}
        value.update(overrides)
        (self.notes / "1.2.3.json").write_text(json.dumps(value), encoding="utf-8")

    def test_parses_valid_grouped_plain_text_and_merges_manifest(self):
        self.authored()
        self.assertTrue(release_notes.merge(self.notes, "1.2.3", self.manifest))
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        self.assertEqual("1.2.3", manifest["releaseNotes"]["version"])
        self.assertIn("New\n• New workflow", manifest["notes"])
        self.assertIn("Fixes\n• Fixed issue", manifest["notes"])

    def test_rejects_version_mismatch_html_unknown_fields_and_empty_content(self):
        invalid = ({"version": "1.2.4"}, {"summary": "<b>unsafe</b>"}, {"extra": True},
                   {"groups": {"New": [], "Improvements": [], "Fixes": []}})
        for override in invalid:
            with self.subTest(override=override):
                self.authored(**override)
                with self.assertRaises(release_notes.NotesError):
                    release_notes.load_notes(self.notes / "1.2.3.json", "1.2.3")

    def test_missing_notes_preserves_generic_manifest(self):
        before = self.manifest.read_text(encoding="utf-8")
        self.assertFalse(release_notes.merge(self.notes, "1.2.3", self.manifest))
        self.assertEqual(before, self.manifest.read_text(encoding="utf-8"))

    def test_manifest_version_mismatch_fails_before_publication_data_changes(self):
        self.authored()
        self.manifest.write_text(json.dumps({"version": "9.9.9"}), encoding="utf-8")
        with self.assertRaisesRegex(release_notes.NotesError, "manifest version"):
            release_notes.merge(self.notes, "1.2.3", self.manifest)


if __name__ == "__main__":
    unittest.main()

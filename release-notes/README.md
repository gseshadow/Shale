# Shale release notes

The first `release-all.bat` invocation creates a UTF-8 JSON draft named `<major>.<minor>.<build>.json` when it is
missing, then stops before release work so a developer can review and edit it. Existing files are never overwritten.
Files are retained permanently so past notes remain reviewable. `release.bat` strictly validates the reviewed file
and merges it into the existing `shale-stable.json` publication manifest.

```json
{
  "version": "1.0.132",
  "title": "What's New in Shale 1.0.132",
  "releaseDate": "2026-10-15",
  "summary": "A short plain-text overview of this release.",
  "groups": {
    "New": ["A new capability."],
    "Improvements": ["A workflow is easier to use."],
    "Fixes": ["A specific problem was corrected."]
  }
}
```

`releaseDate` is optional. The other top-level fields are required, and at least one grouped item is required.
Content is plain text: do not add HTML. Run this validation before release with:

```text
python build/scripts/release_notes.py 1.0.132 build/assets/shale-stable.json
```

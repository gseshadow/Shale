# Shale release notes

Add one UTF-8 JSON file named `<major>.<minor>.<build>.json` for each release. Files are retained permanently so
past notes remain reviewable. `release.bat` validates a matching file when present and merges it into the existing
`shale-stable.json` publication manifest. Missing notes are allowed and retain the generic manifest fallback.

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

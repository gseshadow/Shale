# Release and publication runbook

## Normal release

From an attached Windows Git branch with a configured upstream, a clean index/worktree, Azure CLI access, and the
documented Windows packaging toolchain, run:

```bat
build\scripts\release-and-publish.bat <version> <true|false>
```

For the cross-platform handoff, run `build\scripts\release-all.bat <version> <true|false>` instead. It performs the
same Git preflight before contacting the Mac host and passes the exact preflighted Windows `HEAD` revision to the Mac
builder. This avoids the former mismatch where the Mac host always built `origin/codex/latest` while Windows could
legitimately contain committed local changes ahead of that remote branch.

The enforced order is:

1. require an attached branch and configured upstream, require a clean index/worktree, fetch the upstream, and reject
   upstream-ahead or diverged history;
2. run `release.bat`, which bumps Maven versions, builds artifacts, updates the source manifest, and validates
   `release-notes/<version>.json` when present before embedding its plain-text and structured content in that manifest;
3. stage only `pom.xml`, the six module POMs, and `build/assets/shale-stable.json`;
4. create `Release Shale <version>` when those files have staged changes, then perform a normal `git push` to the
   configured upstream (already-committed local-ahead work is included);
5. begin `publish-update.bat` only after the push succeeds.

`release.bat` remains build-only. It neither commits nor pushes. The synchronizer never uses `git add .`/`-A`, never
force-pushes, resets, stashes, rebases, or discards work, and never stages `dist`, credentials, Mac handoff output, or
arbitrary source changes. A clean retry creates no empty duplicate commit.

## Failure and recovery

* A preflight failure happens before release mutation/build. Commit or otherwise resolve every path listed by the
  diagnostic, or manually reconcile upstream-ahead/diverged history, then rerun the normal release command.
* A commit failure preserves generated files and the index. Correct the reported Git problem and inspect
  `git status`; do not rerun publication until the release commit is valid.
* A push failure preserves generated files and any `Release Shale <version>` commit. Fix credentials/connectivity or
  reconcile a newly advanced remote without force-pushing, then run `git push`. After that succeeds, publish with:

  ```bat
  build\scripts\publish-update.bat
  ```

* If publication fails after synchronization was confirmed, GitHub already contains the release metadata. Do not
  rebuild or create another release commit; correct the Azure/publication problem and use the same publish-only
  command above.

The release scripts do not upload or push during automated tests. The Git synchronization tests use temporary local
repositories and bare remotes.

## Authoring What's New content

Before the release preflight, copy the example in `release-notes/README.md` to
`release-notes/<major>.<minor>.<build>.json`, write the short title and summary, and add plain-text entries under
`New`, `Improvements`, and `Fixes`. Commit that historical source with the release work. The filename and embedded
version must exactly match the version passed to `release-all.bat`.

The build validates canonical versioning, the optional ISO release date, supported fields/groups, length limits,
nonempty content, and rejection of HTML. A present but invalid or mismatched file fails before the manifest is
copied to `dist`; an absent file is explicitly non-fatal and leaves the established `Release <version>` fallback.
The ordinary manifest carries both the backward-compatible `notes` text and a structured `releaseNotes` object
without coupling content to `mandatory`. During `publish-update.bat`, after immutable installers/ZIPs upload but
before the discoverable manifest uploads, `import_release_catalog.py` posts that object to the dedicated
`/api/control-plane/application-releases/<version>/import` endpoint. Set
`SHALE_RELEASE_CONTROL_PLANE_URL` and a minimum-32-character `SHALE_RELEASE_CONTROL_PLANE_TOKEN` in the operator
environment; configure the server with the same token and optional `SHALE_RELEASE_CONTROL_PLANE_OPERATOR` audit
identity. These values are never committed or printed.

The authenticated desktop What's New experience remains post-update and release-catalog driven. It displays
published catalog summaries/items through the existing per-user acknowledgement flow; release notes are not added
to the pre-update policy dialog because update policy and release content remain separate. Missing `releaseNotes`
skips import and remains non-fatal. Present notes make import mandatory: authorization, validation, conflict, audit,
or database failure stops publication before manifest upload. Identical imports return `UNCHANGED` without duplicate
rows or audit noise. Different historical content returns HTTP 409; an exceptional correction must explicitly send
`allowUpdate=true` with the current base64 `expectedRowVersion`. That RowVer-guarded replacement and its sanitized
audit append commit together. The normal release script never enables correction mode, and operators must not use
ad-hoc SQL instead.

Developer procedure: author and commit `release-notes/<major>.<minor>.<build>.json`; run its Python contract tests;
then run `build\scripts\release-all.bat <major>.<minor>.<build> <true|false>` from the clean attached release branch.
The version argument remains the only release-version source of truth and must match both filename and JSON value.

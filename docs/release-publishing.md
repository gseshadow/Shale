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
2. run `release.bat`, which bumps Maven versions, builds artifacts, and updates the source manifest;
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

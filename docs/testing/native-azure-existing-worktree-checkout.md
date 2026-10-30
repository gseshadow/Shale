# Existing Windows checkout diagnosis — 2026-10-30

**BLOCKED.** The existing-worktree failure is reproduced with the operator's exact byte hashes,
and the guarded refresh/actual-byte preflight fixes that reproduction. Mandatory Maven suites
and packaging cannot execute in this environment because Spring Boot 4.1.1 dependency resolution
is restricted. Native Windows execution of the new refresh and the required owner Maven results
are still pending. This is not `OPERATOR RETRY READY`. Keep #1929 draft and unmerged.

## New operator evidence

At head `97e2e234c39a1f5acdb3c508fbdcfb899cfc819b`, the owner confirmed the failed disposable
resource group was deleted before non-Azure testing. All 25 inputs reported `eol: lf`; the fresh
prefix checkout regression passed. Nevertheless, the actual clean Windows worktree retained
4471 CRLF endings in `phase3b8-confinement.sql` after the runbook's forced checkout command.
Attestation 9/9, safety 3/3, datasource 4/4 and V1 migration 1/1 passed; V2 migration failed with
the previously identified CRLF-derived source digest. These are owner-reported historical results,
not passing evidence for the repaired checkout.

| Evidence | Operator and reproduced value |
| --- | --- |
| CRLF / LF-only count | 4471 / 0 |
| Working-tree SHA-256 | `9c93d15bb1584f64bb9be673c30f558b20a93753c9759335808a256cc8b9d861` |
| HEAD blob SHA-256 | `7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8` |
| Expected V2 source receipt | `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891` |
| CRLF-derived source receipt | `561d548e8c49c17688c4f8d64c6f5f606cd70a477db2fb7cf67b104305faabda` |

No Azure reconnection/resource creation or production access was performed in this follow-up.
Both native Azure read-only and mutation/fault gates remain **OPEN**.

## Reproduction and cause

The old regression tested only a new `--prefix` destination. It could prove how Git writes a new
file, but could not prove that a cached existing file was rewritten. That coverage was incomplete.

The new regression builds a real local Git history from the unchanged committed inputs:

1. Commit all 25 LF blobs before `.gitattributes` exists.
2. Enable `core.autocrlf=true`, materialize CRLF copies and record their index stat information
   using `checkout-index --index`. This models a completed existing checkout, not just new files.
3. Commit the new attributes without changing any SQL/resource blob. Switch from the earlier
   commit to the attributes commit, or separately introduce attributes without switching.
4. Refresh status/diff metadata. Assert the worktree is clean, attributes say LF, and
   `ls-files --eol` says **index LF / working tree CRLF**. Assert both full-file hashes above.
5. Run the old `checkout-index --force -- <paths>`. The V2 file's CRLF count, hash and mtime remain
   unchanged. The default actual-byte check refuses this state.

The deciding condition is Git's cached unchanged-file fast path. `--force` allows overwriting;
it does not force an unchanged/stat-matching entry to be materialized again. Attributes govern
conversion when a file is written; adding them does not invalidate every unchanged path's cache.
Git's clean conversion maps the CRLF contents back to the correct LF index blob, so clean status
and correct attributes are compatible with the wrong literal worktree bytes.

The index is **already normalized**; its object ID is not a CRLF blob. A diagnostic regression in
an isolated fixture clears only the cached stat data by reinstalling the same mode/object ID,
then runs the same forced checkout. It writes LF while the index object ID remains unchanged.
That distinguishes cached worktree handling from a bad index blob or incorrect attributes.
Do not perform that diagnostic index operation on the operator repository as the repair method.

Experiments with `git restore --worktree --source=HEAD`, `git checkout HEAD -- <path>` and
`git checkout --force HEAD -- <path>` also preserve the cached CRLF file; changing to one of
those commands would not fix the modeled failure. A new prefix does write LF, explaining why
the earlier synthetic test passed.

These executions use Git 2.52.0 on Linux with the real `core.autocrlf=true` conversion and cached
existing-file transition, reproducing the exact Windows operator hashes. They do not claim a
new native Windows workstation run. The regression is portable and must also run there.

## Repair and operator preflight

The [runbook](native-azure-readonly-operator-runbook.md) replaces the old refresh with:

```powershell
python build/sql-confinement/checkout_bytes.py --refresh
if ($LASTEXITCODE -ne 0) { throw 'STOP before Maven or Azure' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'STOP before Maven or Azure' }
```

`checkout_bytes.py` obtains the protected scope from the Git index/HEAD, checks the LF attributes
and refuses staged changes. It permits replacement only if each existing file is exactly the
HEAD byte sequence or exactly its CRLF checkout form. A genuine edit, deletion or unsafe path
stops the entire preflight before replacement begins. It never treats clean status as byte proof.

It then materializes a fresh temporary Git prefix, compares every output against the reviewed
HEAD bytes, and validates both source receipts. Only after that verification does it atomically
replace the conversion-only destination files. Adjacent temporary files support Windows setups
where the repository and TEMP are on different drives. The final verification reads the actual
destination files. Repeated refreshes are idempotent, and unrelated CRLF files are untouched.
No user Git configuration, checkout hook, index normalization or manual `git show > file` is used.

The receipt calculation is extracted from the existing raw-byte checkout regression and shared
by the operator command and tests: verify the reviewed embedded digest, zero only that field,
then SHA-256 the exact bytes. Java security-contract behavior, module representations, expected
hashes and every historical SQL/resource blob remain unchanged. V1 is checked against its reviewed
`b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3` source receipt and V2 against
`60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`. No newline normalization is
performed by receipt verification. PASS requires all 25 actual files plus both receipt lines.

A fresh clone already writes LF through the attributes. For an existing branch switch/update,
Git may retain CRLF; the explicit guarded refresh is required before validation. The same
supported preflight works in all three requested flows, without claiming attributes alone can
force an unchanged-file rewrite. Failure stops the runbook before Maven and resource creation.
Native acceptance opt-ins are cleared during this non-Azure validation phase.

## Test-impact review and actual verification

Reviewed repository routing rules, V2 representation/receipt architecture, both Java migration
contracts, the original checkout regression, current attributes and operator runbook before editing.
The shared raw-byte receipt implementation and fresh-prefix test are updated together. Added
coverage models the existing cached worktree, branch transition, attributes added in place, fresh
clone, cache-only diagnostic, CLI rejection/repair and preservation of staged/genuine/unrelated edits.
No production Java or SQL changes. Audit compatibility: local checkout/receipt metadata is data-free
build tooling; no sensitive application read, business mutation, audit event/schema or activation.

| Required validation | Actual result |
| --- | --- |
| Existing-worktree regression against old refresh command | FAIL as intended before repair; clean cached CRLF file remains unchanged |
| Existing-worktree transition/in-place refresh, fresh clone/prefix and safety/CLI tests | 9/9 PASS locally; native Windows owner execution still required |
| Actual working-tree V1 and V2 source receipts | PASS from both refresh and read-only preflight; all 25 inputs byte-identical to HEAD |
| Existing V2 private projection contracts | 4/4 PASS with the previously supplied private candidate/catalog; no artifacts committed |
| Phase1R runner and selector self-tests | 3/3 and 27/27 PASS locally; offline checks, not Phase1R SQL acceptance |
| Focused Maven, Phase1R Maven, affected Maven, critical reactor | Blocked before execution by restricted Spring Boot 4.1.1 BOM resolution; owner-required |
| Local data/core package | Blocked by same dependency issue; owner-required; no server deployment artifact built |

`python3 -m unittest discover -s build/sql-confinement -p 'test_*.py'` executed 13 checks with no
failures/skips, including the nine checkout cases and four private-input V2 cases. The private
paths were supplied via the existing environment contract and remain outside Git.

`python3 build/test-selection/select_tests.py --base 97e2e234c39a1f5acdb3c508fbdcfb899cfc819b
--head HEAD` passed and selected security-data across core/data/server. Its emitted affected batch,
the focused five-class selector, `Phase1RLocalSqlServerTest`, critical `mvn -B -ntp test`, and local
`mvn -B -ntp -pl shale-data -am -DskipTests package` all stopped during dependency resolution with
`Operation not permitted`; none is credited as passing. `git diff <previous-head> --check` passed,
and exact SQL/resource/production-Java diffs are empty. No new Azure or production action occurred.

Do not change classification until the real Windows byte/receipt checks and every mandatory
non-Azure Maven/package group pass. The source checkout fix does not close either Azure gate or
resolve the earlier native Stage 0 socket timeout. No Azure execution is authorized by this record.

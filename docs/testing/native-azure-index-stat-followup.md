# Verified LF repair and clean Git stat metadata — 2026-10-30

**BLOCKED pending operator validation.** The new stat-cache regression fails before the fix and
passes locally afterward. The owner has already proved byte/receipt repair and the mandatory
focused Maven suite on Windows. The corrected metadata completion step still needs Windows
operator validation; the remaining mandatory non-Azure acceptance results remain separate.
Keep #1929 draft and unmerged. Both native Azure gates remain OPEN. No Azure reconnection,
resource creation, production access or deployment occurred in this follow-up.

## Operator evidence retained

At the previous PR head, the operator reports both helper commands passing 25/25, both reviewed
source receipts passing, and actual phase3b8 bytes containing 0 CRLF / 4471 LF-only endings.
The mandatory focused Maven suite returned BUILD SUCCESS, including the previously failing V2
migration contract. This is real owner-reported Windows evidence, not a local Maven claim.

However, Git still reported repaired paths as `.M`. For the V2 installer, index and HEAD both
referenced `a210a6c44250f57e00b9e17ffbe02d1b88e43850`; textual diff/numstat were empty, while raw
`diff-files` showed `M` with a zero worktree object ID. Both `update-index --refresh` and
`--really-refresh` reported `needs update`. No content/receipt change was required.

## Exact reproduction and diagnosis

The existing pre-attributes/CRLF fixture reproduces the complete state after the previous
helper's atomic replacement, using Git 2.52.0 with `core.autocrlf=true`:

- all 25 destination byte sequences equal HEAD, and V1/V2 raw source receipts pass;
- textual diff is empty;
- `diff-files --raw` and porcelain v2 report modified inputs;
- both ordinary/real refresh commands fail with `needs update`;
- index mode/OID/stage entries still equal the original entries.

For phase3b8, the cached stat entry still records the old CRLF size 525828, while the verified LF
file is 521357 bytes and has a different inode/mtime from atomic replacement. A content-equal
textual diff is not proof that Git accepted this new filesystem identity into its stat cache.
The former regression proved bytes but omitted the required post-repair Git cleanliness check.

`git checkout-index -h` documents `-u` / `--index` as **update stat information in the index file**.
After verified LF replacement, `git checkout-index --force --index -- <pinned paths>` has a changed
filesystem entry to materialize. It reads Git's existing index blobs, writes the same reviewed LF
bytes and records the resulting worktree stat information. The actual experiment yields empty
textual diff, raw diff-files and porcelain status, and a subsequent ordinary refresh succeeds.
Every index mode/OID/stage record remains identical before/after. This differs from the earlier
failure where `checkout-index --force` skipped a still-stat-matching CRLF file before replacement.

No `git add`, cacheinfo/index-info replacement, reset, assume-unchanged, skip-worktree, receipt
change or SQL normalization is used by the repair. A native checkout stat update completes the
byte repair; it does not manufacture a clean status by hiding changed content.

## Implementation and preserved guarantees

The helper preserves its existing staged/genuine-edit refusal, exact LF-or-CRLF-form admission,
fresh-prefix/HEAD comparison, V1/V2 receipt verification and actual destination-byte verification.
It snapshots **all** index mode/OID/stage records and tracking flags before the repair.

After atomic replacement it verifies the actual destination bytes/receipts before invoking the
scoped native stat update. It rejects any index record/flag change, verifies actual bytes again,
and requires empty `diff`, `diff-files --raw` and `status --porcelain` results for pinned paths.
The default verification command also refuses byte-correct but stat-dirty inputs. Both commands
print `PINNED_INDEX PASS clean=true` only after these checks. Unrelated staged/working-tree edits
are retained and remain visible; cleanliness is explicitly scoped to the pinned inputs.

The stat step runs even when no new byte replacement is needed. Therefore an operator whose
previous helper already repaired LF bytes but left `.M` can run the corrected `--refresh`
directly. There is no need to reintroduce CRLF, stage anything or manipulate index blobs.

## Regressions and acceptance boundary

Added a regression requiring successful refresh, exact HEAD bytes, V1/V2 receipts, identical
full index entries/flags, empty textual diff, empty raw diff-files and clean porcelain v1/v2.
It includes an unrelated staged edit to prove its index entry is not altered. Before the fix,
the real regression failed at `diff-files`; the other byte/receipt assertions already passed.

A second regression simulates the prior atomic replacement, proves that both refresh commands
still fail in that state, and proves default verification refuses it. Rerunning the corrected
helper must recover clean Git state while retaining the same index entries. Existing tests
continue to cover staged/genuine edit refusal, fresh clone/prefix, existing branch transition,
attributes introduced in place, CLI checks, idempotence and unrelated CRLF preservation.

The [operator packet](native-azure-readonly-operator-runbook.md) uses the same commands:

```powershell
python build/sql-confinement/checkout_bytes.py --refresh
if ($LASTEXITCODE -ne 0) { throw 'STOP: byte/receipt/stat repair failed' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'STOP: pinned inputs are not byte-exact and clean' }
```

Require all 25 byte checks, `PINNED_INDEX PASS clean=true`, both receipt PASS lines and actual
empty Git diff/diff-files/porcelain for the repaired inputs on Windows before changing status.
The current source receipts remain V1 `b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3`
and V2 `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`.

## Review scope and verification

Reviewed repository rules, V2 architecture, prior checkout investigation, helper, all checkout
regressions and the operator workflow before editing. Test impact is restricted to checkout
completion and operator validation. No Java security code, historical SQL/module/resource bytes,
receipt, runtime timeout, audit schema/event or capability default changes. Local filesystem/stat
metadata work introduces no application data access or business mutation requiring an audit row.

Local `python3 -m unittest discover -s build/sql-confinement -p 'test_*.py'` passed 15/15 with
no skips: 11 checkout/preflight cases plus four existing private-input V2 contracts. The dedicated
post-repair Git-clean regression failed before the implementation change and passed afterward.
Selector self-tests passed 27/27. Both actual helper commands passed all 25 inputs, the new clean
index check and both reviewed receipts in the task checkout. Private inputs/logs remain outside Git.

The local five-class focused Maven invocation stopped before tests because Spring Boot 4.1.1
dependency resolution reports `Operation not permitted`; that does not invalidate or replace the
owner's successful focused Maven evidence. The selector command
`python3 build/test-selection/select_tests.py --base 9bbf590a2abcfc7b7e8b886218b2c55a155bc904
--head HEAD` passed and selected security-data across core/data/server. Its emitted affected batch,
`mvn -B -ntp test` and `mvn -B -ntp -pl shale-data -am -DskipTests package` also stopped before
execution at the same dependency blocker. These attempts are unverified, never passing tests.
Diff checks passed; protected SQL/resource/production-Java diffs are empty. No local test is
credited as native Windows operator validation or native Azure acceptance.

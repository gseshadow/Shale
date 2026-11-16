# Stage 21 investigation and controlled checkpoint recovery

Scope: `shale.database.windows.net / ShaleMcpAcceptance` only. This change performs
no SQL during preparation or recovery. Keep the application stopped and all controls
OFF. No reset, production access, automatic retry, journal deletion or merge is part
of this procedure. SQL collection and the later resume are future operator actions
requiring their own authorization; the code repair does not authorize execution.

## What the evidence does and does not establish

The reported checkpoint is **24/51**, through `20-pr3`, with an unresolved group
covering manifest indices `[19,30)` (Stages 16–26). The original fast-track launcher
grouped consecutive owner stages because their credentials matched. Stage 21 starts
with error **59400 / “Fresh unscoped dbo lease required.”** if any of these are true:

- `USER_NAME()` is not dbo.
- `@@TRANCOUNT` is nonzero.
- `ShaleClientId` or `PrincipalUserId` session context is non-null.

That is only one use of 59400. The same file uses it for the schema lock/capability,
pre-existing objects, module, ownership, expression, RLS and permission assertions.
The embedded predecessor verification can also throw before object creation. Its
transaction rolls back later failures, so zero `AiCimd*` objects/principals in another
SSMS session proves neither the first assertion nor its individual clause failed.
SSMS has different session state from the terminated SQLCMD connection.

The packet renderer already repeats `target.sql` and `owner.sql` before sources.
`target.sql` rejects open/implicit transactions and populated tenant/principal/email
context; `owner.sql` requires dbo/CONTROL and OFF controls. Fast-track additionally
repeats them around each stage. A contaminated session reaching these fences would
normally fail **59800/59801**, before the Stage 21 source's 59400 assertion. Inspection
of the dispatched Stage 16–20 sources, including decoded hex predecessor payloads,
found no executed context setter or impersonation that explains the reported failure;
references in catalog allowlists and stored module definitions are not executed setters.
PR3 installation commits or rolls back its own transaction. Shared identity therefore
does not establish session compatibility, but **the historical failed assertion cannot
be identified from error number 59400 and object absence alone**. Do not call a
session-contamination hypothesis the verified root cause. The old runner discarded
raw messages; retain any original public assertion/line diagnostic if available.
Do not rerun an installer to recover a diagnostic.

Stages 22 (OAuth), 23 (reviewer), 24 (onboarding), 25 (C1), and 26 (C2) also explicitly
require neutral dbo sessions; Stage 22 rejects implicit transactions too. Seed,
provisioning and verification may evolve to leave session state behind. Fast-track
now gives **every stage a separate native connection**, including 16–20 and 28–30.
Sources within a stage remain ordered on that stage's connection; GO is only a batch
boundary. No context clearing, REVERT, rollback or SQL assertion weakening is used
as a repair. A full run from the retained 18-stage baseline has **33 native logins**,
with one run approval. From checkpoint24, 27 stage connections remain.

New reports include exact allowlisted assertion codes and boolean session failures
(`USER_NOT_DBO`, `OPEN_TRANSACTION`, `TENANT_CONTEXT_SET`, `PRINCIPAL_CONTEXT_SET`,
`IMPLICIT_TRANSACTIONS_ON`). They never include actual context values, credentials,
server/procedure suffixes, SQL lines or arbitrary error messages. An unrecognized
assertion stays `UNCLASSIFIED_SQL_ASSERTION`; it is never guessed from the number.
Session booleans are observed before each stage's fence, not reconstructed afterward.

## Prepare read-only checkpoint evidence offline

Preserve the original packet, rendering, manifest, journal, report, client hash and
independent 00–20 evidence. Use the exact reviewed rendering; never edit completed
stage bytes or replace their hashes. All paths below must be under this checkout's
private `work/` directory. Use PowerShell 7 from the checkout root:

```powershell
$Rendered = Read-Host 'Exact retained rendered directory'
$Review = Join-Path (Split-Path $Rendered) ('checkpoint24-review-' + [guid]::NewGuid().ToString('N'))
./build/azure-mcp-acceptance/Stage21Recovery.ps1 -RenderedDirectory $Rendered -ReviewDirectory $Review
```

This reads and validates the original journal chain and completed-stage hashes. It
supports only the intact failed 16–26 group with 24 completions and `21-proof` failure.
It creates `plan.json` and `checkpoint24.sql`, without connecting. Independently review
both, the utility commit, all hashes and the original evidence. The generated SQL has:

1. Fixed acceptance destination, full owner visibility, neutral session and OFF guards.
2. The unchanged rendered Stage 18 **read-only postcheck**, including its exact reviewed
   binding inputs, proving the committed binding still matches them.
3. Unchanged PR3 receipt/source/catalog and registry-confinement verification, proving
   the Stage 19–20 installed predecessor state rather than merely counting tables.
4. Absence checks across schemas for the proof/OAuth/reviewer/onboarding/C1/DCR object
   and role namespaces, plus later contained users. Any later or partial state stops.
5. The existing metadata/receipt/permission/RLS/binding fingerprint and seed-table
   counts, followed by a unique plan marker. Module definitions are hashed inside SQL
   and never emitted. Snapshot equality alone is insufficient to approve recovery.

No installer is included. There are no schema, permission, receipt or application-row
writes. Temporary SQL variables and session SETs support read-only catalog verification.
Full visibility is mandatory so missing privileges cannot masquerade as absent objects.

## Future separately authorized operator collection

First establish independently that the failed SQLCMD process is terminated, no installer
sessions/open transactions survive, no other writer/DDL operation can run during review
and resume, the app is stopped and controls remain OFF. Do not kill or change sessions
as an automatic repair. If this cannot be established, STOP. A matching pair of snapshots
is point-in-time evidence, not a concurrency lock or proof of the original assertion.

Use the already reviewed absolute **ODBC 17/18 SQLCMD** binary and existing acceptance
owner credential. Keep ambient SQLCMD/application credentials absent. Passwords remain
native hidden console input; never use `-P`, environment credentials, `-e`, `-C` or a
production connection. Independently approve the generated probe hash before collection.
For each of **two separate connections**, select a new capture/error path and run the
following only when authorized (substitute A then B):

```powershell
$Plan = Get-Content -LiteralPath (Join-Path $Review 'plan.json') -Raw | ConvertFrom-Json
$SqlcmdExe = Read-Host 'Reviewed absolute ODBC SQLCMD executable'
$Owner = Read-Host 'Existing acceptance owner username'
$Capture = Join-Path $Review 'capture-a.txt'  # second connection: capture-b.txt
$Errors = $Capture + '.errors.txt'
if ((Test-Path $Capture) -or (Test-Path $Errors)) { throw 'STOP: preserve evidence; use new paths' }
& $SqlcmdExe -S 'tcp:shale.database.windows.net,1433' -d 'ShaleMcpAcceptance' `
    -U $Owner -N -b -V 11 -r 1 -l 30 -t 20 -x -X1 -f 65001 `
    -s '|' -W -w 65535 -i $Plan.SqlPath -o $Capture 2> $Errors
if ($LASTEXITCODE -ne 0) { throw 'STOP: preserve failed capture; no recovery' }
```

Retain column headers (do **not** use `-h -1`), the exit result, stderr and invocation
provenance privately. Independently review both captures, the binding's expected values,
three named zero finding results, identical snapshot/count records, final unique marker,
source journal/report and 00–20 checkpoint evidence. Any error, drift, missing/partial
result, active session, ambiguous commit or later object means STOP. Do not refresh
receipts, reset, grant permissions, replay a completed stage or mark the whole group
verified. Other checkpoints, including a committed Stage 21, need separate recovery work.

## Finalize a new journal fork offline, then resume later

After independent review records both native exits as zero and verifies the no-writer
window, create a recovery fork (no SQL is executed by this command):

```powershell
./build/azure-mcp-acceptance/Stage21Recovery.ps1 -RenderedDirectory $Rendered `
    -PlanPath (Join-Path $Review 'plan.json') `
    -CaptureA (Join-Path $Review 'capture-a.txt') -CaptureB (Join-Path $Review 'capture-b.txt') `
    -ReviewReference 'your-private-independent-review-reference' `
    -VerifiedNativeExitZero -NoOtherSessionsOrWriters
```

The switches are operator attestations, not inferred proof of database execution.
Capture text can be fabricated; the tool cannot authenticate a SQL Server observation.
Native exit and provenance review is essential. Finalization compares the exact generated
SQL, source journal, plan, stage hashes, finding results, snapshots and unique marker;
it rejects substituted probes even if their plan hash is updated.

The original **failed/pending journal remains byte-for-byte unchanged**. A new
`fast-track-recovered-<id>.jsonl` contains its entire history plus one `recovered24`
record referencing hash-bound evidence. A create-new `fast-track-recovery.json` selects
one fork so another finalization cannot create a parallel replay path. Preserve the
original journal, fork, selection, plan, SQL, receipt and both captures together.

For the next separately approved run, use the exact fork path printed by finalization:

```powershell
./build/azure-mcp-acceptance/Start-FastTrackAcceptance.ps1 `
    -RenderedDirectory $Rendered -AuthoritativeFixture $UnchangedFixture `
    -SqlcmdExecutable $SqlcmdExe -OperatorUser $Owner -JournalPath $ExactRecoveryFork
```

The launcher validates the selected fork, original source and evidence hashes before
requesting run approval or SQL authentication. It resumes at **21-proof**, never at
Stage 16 or seed, and isolates every later stage. Default launch against the original
journal still blocks. Keep the verified database state unchanged between collection and
resume; refresh authorization/reconcile separately if the maintenance window is lost.
A new failure stays pending on the fork; this narrowly scoped recovery does not override
it. Preserve the evidence and investigate independently.

## Test and audit scope

`test_fast_track.py` covers isolated dispatch, fresh-session diagnostic allowlisting,
completed-prefix preservation, prerequisite pauses and failure/relaunch refusal.
`test_stage21_recovery.py` uses owned synthetic journals/captures to test exact checkpoint
recovery, no replay, immutable original history, single-fork selection, capture/plan/probe
changes, absent/error/partial evidence, mismatched snapshots and sanitized diagnostics.
All SQLCMD execution in these tests is mocked; passing tests do not establish live SQL
acceptance or the historical failed assertion. Windows native authentication remains
subject to the existing native console verification procedure.

Audit compatibility: the change manages private operator evidence and connection
boundaries. It introduces no sensitive application read surface or domain/database
administrative mutation. Existing migration transactions and audits are unchanged; no
application audit row, audit schema or competing audit mechanism is introduced.

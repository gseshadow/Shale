# Stage 21: transaction diagnostics and one evidence-bound resume

The historical failing module/property **cannot be reconstructed**: the retained
report contains an aggregate assertion and the failed transaction's metadata was
not retained. The exact legacy packet hash
`F3D4E8EB41054B0E5A5EF4A608497B6936DB0D889E77A1C99D9BE72A6FD5827E`
matches the reviewed public rendering. All 16 encoded bodies and expected hashes
agree offline. Session failures were absent. No specific module-body, hash, SET,
ownership or execution-context defect has been demonstrated. Do not invent one.

The verified repair is to the **diagnostic gap and blocked recovery workflow**.
This change does not modify the public installer/verifier, module bodies, expected
hashes, receipts, security assertions or database objects. It instruments only new
acceptance packet files and provides an offline recovery tool. Nothing here executes
SQL automatically. This task has executed no SQL or installer retry.

## Diagnostic behavior

`Prepare-Packet.ps1` now inserts four marked blocks into Stage 21 only:

- In the original installer's CATCH, while `XACT_STATE()<>0`, read module metadata
  before the original explicit rollback. This covers both the initial module check
  and the embedded seal verifier. If SQL Server already ended the transaction,
  skip this read rather than label absent objects as historical failures.
- Preserve the exact original `IF XACT_STATE()<>0 ROLLBACK TRANSACTION;` and bare
  `THROW;`. A nested TRY/CATCH suppresses diagnostic failures, so they cannot skip
  rollback or replace it with a commit. After rollback, a literal marker reports
  transaction count zero for this exact module assertion. That marker alone never
  authorizes recovery or proves the original failing property.
- Wrap the second, standalone verifier in TRY/CATCH to report current module flags
  and rethrow. This verifier runs after the installer's commit; it emits no rollback
  confirmation. Existing proof objects after a failure therefore block reinstall.

The SELECT emits at most 16 fixed public names and a seven-bit failed-property mask:
`OBJECT_MISSING`, `DEFINITION_UNAVAILABLE`, `OWNERSHIP`, `ANSI_NULLS`,
`QUOTED_IDENTIFIER`, `EXECUTION_CONTEXT`, `DEFINITION_HASH`. No definitions, digests,
principal names, exception text, credentials or application values are emitted by
this instrumentation. With missing objects/definitions, other failure bits mean an
unsatisfied comparison, not an observed historical value.

The native wrapper keeps output private. Fast-track accepts only exact records,
known names and seven Boolean bits, rejecting duplicate, malformed, unknown-name
and all-zero records as diagnostic evidence. `report.json` retains `ProofModules`
with named `FailedProperties`, `ProofDiagnosticsValid` and `RollbackConfirmed`.
Missing/unavailable diagnostics remain unknown. A 59400 error still stops execution,
leaves Stage 21 pending, and prevents later stages or automatic retry. A valid module
report identifies a discrepancy; it does not authorize weakening the assertion.

Removing the four **exact approved** blocks recovers the original packet bytes.
The offline auditor checks those blocks, the original sources, both SET preambles,
all payload digests and expected registries. The newly rendered Stage 21 file has a
**new hash**; keep the old rendering and manifest unchanged. Stages 00–20 and all
other rendered stages retain their hashes.

## Recovery scope and evidence

`Stage21RetryRecovery.ps1` supports exactly the reported latest attempt:

- The existing `fast-track-recovery.json` selects the original recovered24 fork.
  Its chain, original journal, receipts and prior captures remain valid.
- That selected fork has 24 completions, then an unresolved single-stage `[24,25)`
  pending/failed `21-proof` attempt. The failed packet has the exact legacy hash above.
- Its retained report names only Stage 21, exit 16, error 59400, exact
  `PROOF_MODULE_DRIFT`, no session failures, completed count 24 and `20-pr3` prefix.
  The independent reviewer must verify that this report belongs to that journal
  attempt; old records do not contain a cryptographic run/report association.
- The new rendering differs **only** by the exact diagnostic transform of Stage 21.
  All other first-30 stage hashes, including every completion, must match.
- **One new read-only capture** must pass the existing binding/PR3 receipt and
  confinement checks, OFF guards and later-object absence checks. Its complete
  metadata/count snapshot must equal the snapshot already independently verified
  in the original checkpoint24 receipt. A new plan-specific marker prevents stale
  captures from being accidentally reused. Counts or a rollback marker alone fail.
- Native exit zero, capture/invocation provenance, no surviving failed client/open
  transaction, stopped application and no competing writers/DDL during collection
  through resume must be independently reviewed. No process/session is killed or
  database state changed by recovery. If this window cannot be established, stop.

Preparation/finalization never connect. Finalization creates a **new** journal with
an evidence-bound checkpoint24 baseline, referencing the complete original failed
parent chain, failure report, old selection, plan, probe and capture by hash. It does
not rewrite old events or claim Stage 21 completed. A create-new
`fast-track-stage21-recovery.json` selects this journal. The launcher accepts only
that selected new journal when it exists; the old selection/history is preserved.

Every launch revalidates the parent chain and evidence before approval/authentication.
Changed/truncated journals, reports, receipts, captures, probes, completed stages,
unapproved Stage 21 edits, drift, missing evidence or another checkpoint are refused.
A second recovery fork/retry-of-retry is refused. There is no force/skip/reset mode.
Later Stage 28 prerequisite continuation can use another rendering only if the
bound first-30 SQL hashes and all subsequently completed hashes remain identical.

## Exact operator sequence

Use the reviewed PR utility checkout, PowerShell 7, Python and JDK 21. Retain the
existing fixture, packet inputs, both renderings, both old journals, old selection,
reports and evidence. All private paths must remain under this checkout's `work/`.
Do not change approval bits, secrets, SET expectations, module hashes or old files
to get through a refusal. These steps are for a **future separately authorized**
collection and run; the repair task does not execute them.

### 1. Prepare the instrumented rendering offline

```powershell
$Previous = Read-Host 'Exact rendered directory used for the failed F3D4E8EB... attempt'
$Packet = Split-Path $Previous
$Fixture = Read-Host 'Unchanged authoritative fixture path'
$FailureReport = Read-Host 'Exact retained report for the latest failed single-stage attempt'
./build/azure-mcp-acceptance/Prepare-Packet.ps1 -PacketDirectory $Packet `
    -AuthoritativeFixture $Fixture -Continuation
$Rendered = Read-Host 'Exact NEW rendered directory printed above'
python build/azure-mcp-acceptance/proof_module_diagnostic.py `
    --input (Join-Path $Rendered '21-proof.sql')
if ($LASTEXITCODE -ne 0) { throw 'STOP: offline proof audit failed' }
```

Review all comparison booleans, including `AcceptanceDiagnosticsPresent` and
`AcceptanceDiagnosticBlocksMatch`, as true; `EngineMetadataObserved` remains false.
Review the new Stage 21 hash and the catch-path diff. Recovery below independently
checks all first-30 hashes and the exact transform; do not manually edit a manifest.

### 2. Prepare the single read-only checkpoint probe offline

```powershell
$Review = Join-Path $Packet ('stage21-retry-review-' + [guid]::NewGuid().ToString('N'))
./build/azure-mcp-acceptance/Stage21RetryRecovery.ps1 `
    -PreviousRenderedDirectory $Previous -RenderedDirectory $Rendered `
    -FailureReport $FailureReport -ReviewDirectory $Review
```

Review `plan.json` and `checkpoint24.sql`. The probe reuses the previously reviewed
read-only binding/PR3/OFF/absence/snapshot checks; it never invokes an installer or
modifies a receipt, permission, application row or object. Keep all existing controls
OFF. Stop if proof/later objects remain or the observed checkpoint differs.

### 3. Collect once, only under separate read-only authorization

Use the same reviewed absolute ODBC 17/18 executable and existing acceptance owner
credential in a clean foreground ConsoleHost. No password argv/environment, credential
store, production connection or TLS bypass. The existing native transport shows
`PASSWORD INPUT READY` and leaves hidden input with SQLCMD. Do not print `$native`.

```powershell
. ./build/azure-mcp-acceptance/Supervision.ps1
. ./build/azure-mcp-acceptance/FastTrack.ps1
$PlanPath = Join-Path $Review 'plan.json'
$Plan = Read-Stage21JsonFile $PlanPath
$SqlcmdExe = Read-Host 'Reviewed absolute ODBC SQLCMD executable path'
$Owner = Read-Host 'Existing acceptance owner username'
$Capture = Join-Path $Review 'current-checkpoint.txt'
if (Test-Path $Capture) { throw 'STOP: preserve capture; use a new review' }
if ((Get-FileHash -LiteralPath $Plan.SqlPath -Algorithm SHA256).Hash -cne $Plan.SqlHash) {
    throw 'STOP: reviewed probe changed'
}
$nativeArgs = @('-S','tcp:shale.database.windows.net,1433','-d','ShaleMcpAcceptance',
    '-U',$Owner,'-N','-b','-V','11','-r','1','-l','30','-t','20','-x','-X1',
    '-f','65001','-i',$Plan.SqlPath,'-s','|','-W','-w','65535')
$native = & ./build/azure-mcp-acceptance/Invoke-NativeSqlcmd.ps1 `
    -Executable $SqlcmdExe -Arguments $nativeArgs -RequireConsole
Write-FastTrackFile $Capture ((@($native.Lines) -join "`n") + "`n")
if ($native.FailureCode -or $native.ExitCode -ne 0 -or
    -not $native.PasswordPromptObserved -or -not $native.PasswordInputReadyShown) {
    $native = $null
    throw 'STOP: preserve failed capture; no recovery'
}
$native = $null
```

Keep capture, native exit/prompt evidence and invocation/client hashes private.
Review the three zero finding results, exact binding status, unchanged prior snapshot
and final plan marker. The tool cannot authenticate fabricated capture text; native
execution provenance and the no-writer window remain operator responsibilities.

### 4. Finalize offline into a new selected journal

Only after that review, native exit zero and the no-writer/session evidence:

```powershell
./build/azure-mcp-acceptance/Stage21RetryRecovery.ps1 -RenderedDirectory $Rendered `
    -PlanPath $PlanPath -Capture $Capture -ReviewReference 'private-independent-review-reference' `
    -VerifiedNativeExitZero -NoOtherSessionsOrWriters
```

Keep the newly printed journal path, receipt and create-new selection together with
all old evidence. Existing journals/selections remain byte-for-byte unchanged. These
switches are explicit provenance attestations, not substitutes for passing evidence.

### 5. Resume later under a new run approval

```powershell
$NewJournal = Read-Host 'Exact NEW journal path printed by finalization'
./build/azure-mcp-acceptance/Start-FastTrackAcceptance.ps1 `
    -RenderedDirectory $Rendered -AuthoritativeFixture $Fixture `
    -SqlcmdExecutable $SqlcmdExe -OperatorUser $Owner -JournalPath $NewJournal
```

The launcher starts at **21-proof**, never Stages 00–20, with a fresh connection.
The existing approval covers remaining transitions; later stages execute only if
Stage 21 completes and their prerequisites pass. If it fails again, retain the
module/property report and new pending journal, and stop. No automatic second retry
or diagnosis-based repair is performed. Correct the identified discrepancy only
with supporting evidence in separate review; keep application/MCP/AI OFF.

## Verification and audit scope

Offline tests cover exact source restoration, unchanged encoded bodies/expected
hashes, pre-rollback diagnostic placement, diagnostic-error containment, rollback/
rethrow preservation, masks and sanitization, report retention and fail-fast/no replay.
Synthetic journal/capture tests cover checkpoint24 adoption, no completed-stage replay,
single-fork selection, parent/child chain integrity, evidence and snapshot changes,
missing/partial results, and unapproved packet edits. SQLCMD is mocked throughout.
No live SQL transaction test was executed: engine behavior and Windows native console
operation remain subject to the existing separately authorized acceptance procedure.

Audit compatibility: operator evidence and bounded administrative metadata only.
No application read surface, domain/database administrative mutation, audit schema
change or competing audit trail is introduced.

# Stage 21: capture the failed assertion before THROW

## What the latest evidence establishes

The supplied report has `RollbackConfirmed=false`, `ProofDiagnosticsValid=true`,
and all seven bits set for all 16 names. These are **v1, unscoped CATCH records**.
The report path is in the operator workspace and was not available in this checkout;
this review uses the supplied fields. No SQL or retry was executed.

The exact historical diagnostic path **cannot be uniquely identified** from those
fields. Both old paths emit the same `PROOF_MODULE_FAIL:<name>:<bits>` format:

| Possible path | What the old code did | Meaning of missing rollback marker |
| --- | --- | --- |
| Installer CATCH, after its initial assertion or nested seal verifier throws | If `XACT_STATE()<>0`, reread catalogs after error processing; then run the original conditional rollback | The marker's error/count guard was not observed as satisfied, or its output was not captured. This is not evidence that rollback failed. |
| Standalone verifier CATCH, in the second source | Reread catalogs even with no transaction and after prior work may have been undone | This path never emitted a rollback marker. All-absent rows are fully compatible with this path, but do not prove it ran. |

`ProofDiagnosticsValid=true` in v1 validated record syntax, name allowlisting and
bits. It did **not** validate the point in time or origin of the metadata. Absence
makes ownership, SET, context and digest comparisons unavailable, not seven verified
historical defects in every module. We cannot infer that standalone executed after
an installer error: SQLCMD's existing fail-fast switches should stop that, and the
old report does not retain the source boundary or origin needed to prove otherwise.

`XACT_ABORT ON` governs error handling of `THROW`; it does not make a CATCH catalog
read a snapshot of the predicate before the throw. Error unwinding or nested error
handling can precede that read. `XACT_STATE()` describes the current transaction:
0 means none, -1 uncommittable, 1 committable. Nonzero does not prove earlier DDL is
still visible. The old marker used `ERROR_NUMBER()`/`ERROR_MESSAGE()` after diagnostic
work, rather than caching the original error on entry. The exact reason its guard
was not observed cannot be reconstructed from the report. Console absence alone was
also inconclusive because the old runner did not print the raw marker.

In the public installer, the initial module check and embedded seal both precede
COMMIT. The standalone source follows the installer. Decoding the predecessor
verification batches found no BEGIN TRANSACTION, COMMIT or ROLLBACK statements;
there is no evidence of an intentional early rollback in those public batches.
This does not establish what error unwinding did in the actual failed session.

The verified defect is **late, unattributed diagnostic collection and misleading
validity semantics**. The original SQL module/property defect remains unknown.
The legacy F3D4E8EB... packet and all 16 expected module hashes remain internally
consistent; none is changed to force acceptance.

## Targeted correction

New rendered packets preserve the original assertion predicate and explicit
`THROW 59400,...,1`, and collect metadata **inside its failing branch before THROW**:

- `INSTALL`: initial installer module assertion, before its exception is raised.
- `SEAL`: the same assertion in the decoded embedded seal verifier, also before
  its exception. Only this anonymous verification batch is instrumented; none of
  the 16 installed module bodies or their expected hashes changes.
- `STANDALONE`: the second source's assertion, before its exception.

Each failing branch emits a bounded origin/transaction record: phase, `XACT_STATE`,
`@@TRANCOUNT`, and effective `XACT_ABORT`. It then emits only fixed public module
names and failed-property bits. No definitions, observed digests, credentials,
principal names, exception text or application rows are exposed.

If every module is already absent **at the assertion**, emit only
`PROOF_METADATA_ABSENT:<phase>` and the state record. Do not fabricate 16 all-bad
module diagnoses. The new report uses `NO_MODULE_METADATA_AT_ASSERTION` and keeps
`ProofDiagnosticsValid=false`. Unscoped legacy rows are classified as
`UNSCOPED_LEGACY_OUTPUT`, never accepted as evidence of the original bad property.

The installer CATCH performs **no catalog reads**. It first caches the original
error, reports bounded CATCH state for this exact assertion, runs the original
conditional rollback, and uses the cached error plus zero transaction count for
rollback confirmation. The original bare rethrow remains. Diagnostic failures are
contained; they do not skip rollback or weaken the explicit assertion throw. The
confirmation means no transaction remains after handling, not an independently
verified database checkpoint. If CATCH state is already 0, the transaction had
ended before the explicit rollback; the report does not claim the explicit rollback
statement ran. The snapshot probe below still establishes recovery eligibility.

Fast-track now dispatches Stage 21's two sources in **separate SQLCMD processes**.
Standalone is dispatched only after installer exit 0, no SQL/capture error, confirmed
native prompt and the exact installer source-success marker. Even a zero-exit SQL
error prevents standalone. This enforces the boundary in the controller without
assuming the historical client violated its fail-fast settings. Existing `-b -V 11`,
`:on error exit`, TLS, guards and source order remain. Standalone explicitly inherits
XACT_ABORT ON on its fresh connection. A successful Stage 21 adds one owner login
(34 connections from checkpoint18; 28 from checkpoint24); a failed install uses one.

`report.json` and concise console output now identify `ProofAttemptedSources`,
`ProofChecks` (pre-THROW), `ProofCatchStates`, `ProofObservation`, phase-tagged
`ProofModules`, and `RollbackConfirmed`. A pre-THROW state of 1 followed by CATCH -1
is different from CATCH 0; no historical state is guessed from missing output.

## Evidence-bound recovery of this latest failed v1 attempt

The old journal and both existing selection files stay unchanged and mandatory.
The new utility accepts only the latest unresolved single Stage 21 failure on the
previous **v1** recovered journal, with exactly the supplied old all-absent report
pattern. It validates that prior receipt, complete ancestor chain, and frozen v1
rendering. Frozen v1 transforms are retained only to authenticate existing evidence;
the current renderer always generates pre-THROW v2 instrumentation.

One separately authorized read-only capture must pass binding, PR3 receipt/confinement,
OFF and absence checks, and match the previously verified checkpoint24 snapshot.
`RollbackConfirmed=false` is neither a recovery veto nor proof of safety. Current
state evidence, native-exit/provenance review and a no-writer/session window decide.
Any surviving/partial proof objects or drift refuses reinstall. No process is killed,
state repaired or receipt refreshed.

Finalization creates a new evidence-bound journal and an immutable successor file
`fast-track-stage21-recovery-<parent-journal-sha256>.json`. The launcher follows and
validates that successor; it never edits the old selection or discards the failed
parent. Parent hash changes, including truncation to a valid earlier prefix, are
refused. A parallel successor, cycle, unsupported ancestry, changed report/capture,
unapproved SQL change or missing evidence blocks execution. This recovery supports
one v1-to-v2 correction; another v2 failure requires review, not another automatic retry.

Only the exact diagnostic transform may change Stage 21. Every other first-30 SQL
hash, including all 24 completions, must match. Completion remains at 24 until the
new attempt actually completes; Stages 00–20 are never replayed.

## Exact future operator commands

These are future operator actions, not executions performed by this task. Use the
reviewed utility checkout, original fixture/private inputs, existing ODBC 17/18
binary and owner credential. Keep acceptance application/MCP/AI OFF and preserve
all old files. Do not normalize old evidence or regenerate hashes to bless changes.

1. **Offline re-render and prepare** using the exact directory for the failed **v1
   instrumented** attempt and its retained report:

```powershell
$Previous = Read-Host 'Exact rendered directory used for the all-absent v1 failure'
$Packet = Split-Path $Previous
$Fixture = Read-Host 'Unchanged authoritative fixture path'
$FailureReport = Join-Path $Packet 'fast-track-97eeaa908753486bba5cee09546280c2/report.json'
./build/azure-mcp-acceptance/Prepare-Packet.ps1 -PacketDirectory $Packet `
    -AuthoritativeFixture $Fixture -Continuation
$Rendered = Read-Host 'Exact NEW rendered directory printed above'
python build/azure-mcp-acceptance/proof_module_diagnostic.py --input (Join-Path $Rendered '21-proof.sql')
if ($LASTEXITCODE -ne 0) { throw 'STOP: offline packet audit failed' }
$Review = Join-Path $Packet ('stage21-prethrow-review-' + [guid]::NewGuid().ToString('N'))
./build/azure-mcp-acceptance/Stage21RetryRecovery.ps1 -RenderedDirectory $Rendered `
    -PreviousRenderedDirectory $Previous -FailureReport $FailureReport -ReviewDirectory $Review
```

Review `AcceptanceDiagnosticVersion=2`, all source/hash/preamble/block checks true,
`EngineMetadataObserved=false`, the new packet hash and `plan.json`/`checkpoint24.sql`.
The old packet hash stays unchanged; the v2 file necessarily has a new hash.

2. **Collect once only under separate read-only authorization.** Establish the failed
   client ended, no surviving open transaction/competing writer/DDL, and maintain that
   window through resume. Use clean foreground ConsoleHost; native hidden password
   input only. Never print `$native` or pass credentials through argv/environment.

```powershell
. ./build/azure-mcp-acceptance/Supervision.ps1
. ./build/azure-mcp-acceptance/FastTrack.ps1
$PlanPath = Join-Path $Review 'plan.json'
$Plan = Read-Stage21JsonFile $PlanPath
$SqlcmdExe = Read-Host 'Reviewed absolute ODBC SQLCMD executable'
$Owner = Read-Host 'Existing acceptance owner username'
$Capture = Join-Path $Review 'current-checkpoint.txt'
if (Test-Path $Capture) { throw 'STOP: preserve capture; use a new review' }
if ((Get-FileHash $Plan.SqlPath -Algorithm SHA256).Hash -cne $Plan.SqlHash) { throw 'STOP: probe changed' }
$nativeArgs = @('-S','tcp:shale.database.windows.net,1433','-d','ShaleMcpAcceptance',
    '-U',$Owner,'-N','-b','-V','11','-r','1','-l','30','-t','20','-x','-X1',
    '-f','65001','-i',$Plan.SqlPath,'-s','|','-W','-w','65535')
$native = & ./build/azure-mcp-acceptance/Invoke-NativeSqlcmd.ps1 `
    -Executable $SqlcmdExe -Arguments $nativeArgs -RequireConsole
Write-FastTrackFile $Capture ((@($native.Lines) -join "`n") + "`n")
if ($native.FailureCode -or $native.ExitCode -ne 0 -or
    -not $native.PasswordPromptObserved -or -not $native.PasswordInputReadyShown) {
    $native = $null; throw 'STOP: preserve failed capture; no recovery'
}
$native = $null
```

Review zero findings, binding status, exact prior snapshot and final plan marker,
native exit and invocation/client provenance privately. The utility cannot authenticate
fabricated capture text or establish external no-writer state. If evidence fails, stop.

3. **Finalize offline**, only after that independent review:

```powershell
./build/azure-mcp-acceptance/Stage21RetryRecovery.ps1 -RenderedDirectory $Rendered `
    -PlanPath $PlanPath -Capture $Capture -ReviewReference 'private-independent-review-reference' `
    -VerifiedNativeExitZero -NoOtherSessionsOrWriters
```

4. **Resume later under a new run approval**, using the newly printed journal:

```powershell
$NewJournal = Read-Host 'Exact NEW journal path printed by finalization'
./build/azure-mcp-acceptance/Start-FastTrackAcceptance.ps1 `
    -RenderedDirectory $Rendered -AuthoritativeFixture $Fixture `
    -SqlcmdExecutable $SqlcmdExe -OperatorUser $Owner -JournalPath $NewJournal
```

The run starts at Stage 21. A failed installer stops before standalone authentication.
If it passes, standalone gets a separate connection. Another failure stays pending;
review its actual origin/state/property evidence before proposing a SQL correction.
No database change or retry is performed by preparation/finalization. No reset, journal
edit, receipt refresh, production access, activation or automatic merge is included.

## Tests and audit scope

Offline tests cover all three pre-THROW assertion sites (including decoded seal),
unchanged module bytes/security predicates/rollback/rethrow, original-error caching,
state/phase parsing, legacy-output rejection, absent-metadata suppression, first-source
error/zero-exit gating, standalone attribution, nested v1/v2 evidence validation,
immutable ancestors, valid-prefix parent truncation, single-successor selection,
insufficient evidence and no completed-stage replay. SQLCMD is mocked. Live SQL
transaction behavior is deliberately unexecuted under the task restriction.

Audit compatibility: bounded operator/catalog evidence only. No application read
surface, domain/database administrative mutation or audit schema change is introduced.

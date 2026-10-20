# Existing sandbox authority correction and one continuation

Manual recovery **NOT MEASURED**. No Windows execution is claimed here. Brian's
completed password, remembered close/reopen, logout and reboot checks stay accepted
at their reported scope; do not repeat them. This supersedes the preparation/reboot
sequence in single-pc-recovery.md for this existing state only.

At `ed2a137ff916420cc1ba507184ab1f06defdbe71`, Apply failed with SQL 58307.
Preserve `C:\ShaleVerification\ManualRecovery-20261019-155907-523` unchanged.
The subsequent reported read-only comparison confirmed full original catalog
identity `0xC584A399EA216FF10DD0E3C28AEDC8CC306DDB42606C93009F4C75123DC502AF`.
There was no committed retirement/application trial and no manual rollback was needed.
Do not run incident rollback for this attempt or replace its baseline/timing/evidence.

## Review the entire compatibility gate once

The representative synthetic SQL test reproduced the **complete** post-mutation
58307 findings: auth SELECT on `[sandbox].[Ownership]` and its `[Nonce]` column.
Before retirement there are also the three expected legacy EXECUTE findings; the
retirement itself removes those. The new inspection retains EVERY auth check,
including NULL, plus EVERY runtime escalation check, without the diagnostic display's
200-row limit. Additional or UNKNOWN findings block correction. Brian's actual
private full diagnostics are unavailable in this cloud task: inspect them below;
the reported two lines alone do not establish the complete Windows result.

The existing local blockers and requirements must be resolved together:

* Auth's ownership SELECT conflicts with the unchanged guard. The exact correction
  removes that **one dbo-granted ACL row**. Runtime's ownership SELECT, auth's metadata
  DENY, Users read, old resolver, runtime grants, roles and all production guards stay.
* The old parent launcher still reads the nonce as auth and would fail after that
  removal. The additive Sandbox overlay instead checks the **original one-row nonce
  and database extended property as the dedicated local dbo provisioner**, then checks
  real auth/runtime login mappings, catalog and non-bypass authority. Runtime also
  checks its original nonce row. Both API and Desktop now need an additional secure
  local-provisioner prompt. That credential is removed before creating the application
  child; it is never an application identity or stored secret. Missing credentials,
  incorrect mapping/nonce or failed launch validation stop this continuation.
* Original state, immutable manifest/export/pinned classes/dependencies and both exact
  overlay receipts must validate. No New, session Stage, UpdateStartup, Phase Verify,
  manifest regeneration, SQL credential/timestamp repair or replacement expected hash.
  Application pin is `59d8374858764788c621f6f8d986773b58ab79c4`; startup overlay is
  `62a12940aeb5d6994016432cd8985ec66cd98bf7`; session overlay is
  `2a74cd122e87b15fb995722451e026faa212abb8`. Use the same Windows owner, isolated
  profile/identities and API signing secret. All old files/receipts/evidence remain.
* Dedicated SQL at `127.0.0.1:14333`, JDK 21, native PowerShell 5.1 and explicit ODBC
  SQLCMD 17/18 must be available. Four consoles and uninterrupted SQL/API availability,
  existing audit viewer, known-work pause/drain/reconcile/support controls and private
  evidence storage are required for the later exercise. Preserve prior reboot evidence;
  no further reboot/sleep is part of this continuation.
* Native execution of this correction/launcher is still unverified until Brian runs
  it. Any extra catalog/authority finding, digest/path refusal, missing secret, failing
  service or unresolved workflow/audit/live prerequisite blocks Apply. Record all such
  blockers together in one private gate record and stop; no trial-and-error grant edits.

No other permission conflict was found in the representative synthetic sandbox.
It uses the actual local permission recipe and metadata DENY on a representative
schema, not the unavailable private 96-table export. Windows inspection therefore
remains required. Production build/configuration acceptance, identity/caller/work
review, package/risk/window authorization and final evidence review remain separate
open decisions; this local correction authorizes none of them.

## One finite native Windows sequence

Run each PowerShell block as one line in the operator console. Prompts never ask for
production credentials. Start in the existing Git checkout, preserve it, and obtain a
new detached review worktree at this PR's full reviewed SHA:

```powershell
$ErrorActionPreference='Stop'; $state='C:\ShaleVerification\RetirementOperator-1881\SandboxState-1887-B'; $jdk='C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; $failed='C:\ShaleVerification\ManualRecovery-20261019-155907-523'; $revision=Read-Host 'Full reviewed authority-correction commit SHA'; if($revision -notmatch '^[0-9a-f]{40}$'){throw 'Full reviewed SHA required'}; git fetch origin codex/fix-existing-recovery-authority; if($LASTEXITCODE){throw 'Fetch failed'}; $review=Join-Path ([IO.Path]::GetDirectoryName($state)) ('AuthorityReview-'+[guid]::NewGuid().ToString('N')); git worktree add --detach $review $revision; if($LASTEXITCODE){throw 'Worktree failed'}; Set-Location -LiteralPath $review
```

1. Stop only the sandbox Desktop and API normally in their owned consoles, preserving
   all profile/DPAPI/session data; leave SQL and Relay available. Stage the additive
   **authority** overlay once. This compiles two local-tool classes against the existing
   pinned snapshots; it does not build/change application classes or connect to SQL.

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase StageAuthority -StatePath $state -JavaHome $jdk -ReviewedRevision $revision; if(-not $?){throw 'Authority staging failed; preserve state; stop'}
```

2. Inspect once, read-only. Enter the existing dedicated LOCAL provisioner username
   and secure password. The original hash must match the failed attempt's original
   records and the current unchanged catalog. This also hashes the entire failed
   evidence tree without writing into it.

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase InspectAuthority -StatePath $state -JavaHome $jdk -ReviewedRevision $revision -FailedEvidence $failed; if(-not $?){throw 'Inspection blocked; review all recorded findings; stop'}; $overlay=Join-Path $state ('authority-updates/'+$revision); notepad.exe (Join-Path $overlay 'inspection.json'); notepad.exe (Join-Path $failed 'apply.sqlcmd.txt'); Get-FileHash -LiteralPath (Join-Path $overlay 'inspection.json') -Algorithm SHA256
```

Review **all** Checks and RuntimeChecks and the retained complete failed Apply output.
Expected findings: exactly five auth findings before retirement (three legacy EXECUTEs,
Ownership object/Nonce SELECT); zero runtime escalation/UNKNOWN findings. Record the
inspection digest and original baseline comparison evidence in private gate notes.
A diagnostic count/truncation discrepancy or any additional finding stops correction.
Do not just acknowledge the two reported SELECT lines.

3. Correct once, only after reviewing that inspection. The prompt requests the digest
   just independently reviewed. It is not an approval of retirement. Enter existing
   auth/runtime passwords and local provisioner credentials at the secure prompts.

```powershell
$inspection=Read-Host 'SHA256 of the complete inspection you reviewed'; & .\build\session-utc-update\session-update.ps1 -Phase CorrectAuthority -StatePath $state -JavaHome $jdk -ReviewedRevision $revision -FailedEvidence $failed -InspectionSha256 $inspection; if(-not $?){throw 'Correction failed/uncertain; preserve attempted/result/evidence; no retry'}; notepad.exe (Join-Path $overlay 'correction-result.json'); Get-FileHash -LiteralPath (Join-Path $overlay 'correction-result.json') -Algorithm SHA256
```

The transaction checks the original full baseline, exact two ownership grants, full
expected ACL minus auth's SELECT, unchanged OtherHash and original nonces. It commits
only that one ACL removal. It does not execute retirement. The result preserves
BeforeHash and the **new observed CorrectedBaseline**, plus the SQL commit marker;
these must agree. Keep that result/digest independently with the gate record.

This is a legitimate local baseline transition: ACL fingerprint changes because one
local SELECT grant was removed; OtherHash, original nonce row/property, modules,
principals, role membership, RLS and application data do not change. The former
`0xC584...` remains the failed attempt's original identity forever. No expected module
hash or old evidence is replaced. The later retirement rollback must return exactly
to **CorrectedBaseline**, preserving the correction, not reinstall auth's broken grant.

4. Run read-only ownership/login validation once (existing local provisioner and
   auth/runtime secure prompts), then launch API and Desktop from this review checkout
   in their service consoles with **both** selectors equal to `$revision`. Reinitialize
   `$state`, `$jdk`, `$revision` there from the values above. Relay continues on its
   retained startup selector; no new Relay behavior or repeated baseline suite.

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase CheckAuthority -StatePath $state -JavaHome $jdk -ReviewedRevision $revision; if(-not $?){throw 'Corrected launch/ownership validation failed; stop'}
```

API console (also prompts for the same API signing secret):

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase Api -StatePath $state -JavaHome $jdk -ReviewedRevision $revision -AuthorityUpdate $revision; if(-not $?){throw 'API failed; preserve evidence; stop'}
```

Desktop console:

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase Desktop -StatePath $state -JavaHome $jdk -ReviewedRevision $revision -AuthorityUpdate $revision; if(-not $?){throw 'Desktop failed; preserve evidence; stop'}
```

These launches validate original/startup/session/authority artifact precedence and
ownership using the existing nonce, identity, local-network/profile/process guards.
Observe usable services/expected identity and record launch evidence. Do not turn
this into another password/remembered/logout/reboot acceptance cycle.

5. Only when the whole gate is resolved, prepare ONE new manual exercise using the
   existing §3 payload and unchanged guarded SQL. The following creates a new private
   directory and payload, recording the authority overlay scope and adding a comparison to the **recorded corrected baseline**
   immediately after the payload reads its BeforeHash. It never alters the original
   staged exercise or failed evidence. Do not paste a replacement hash.

```powershell
. .\docs\sql\auth\permission-cutover\retirement-operator.ps1; $overlay=Join-Path $state ('authority-updates/'+$revision); $c=Read-Json (Join-Path $overlay 'correction-result.json'); Assert-Hex $c.CorrectedBaseline; if($c.BeforeHash -cne '0xC584A399EA216FF10DD0E3C28AEDC8CC306DDB42606C93009F4C75123DC502AF' -or -not @($c.SqlResults | Where-Object {$_.PSObject.Properties['CheckName'] -and $_.CheckName -ceq 'LocalOwnershipCorrected' -and $_.StateHash -ceq $c.CorrectedBaseline}).Count){throw 'Correction identity missing'}; $evidence=Join-Path ([IO.Path]::GetDirectoryName($state)) ('AuthorityContinuation-'+[guid]::NewGuid().ToString('N')); $null=New-Item -ItemType Directory -Path $evidence; $text=Get-Content -LiteralPath .\docs\sql\auth\permission-cutover\remaining-acceptance.md -Raw; $section=$text.Substring($text.IndexOf('## 3. One timed operator recovery exercise')); $block=[regex]::Match($section,'(?s)```powershell\r?\n(.*?)\r?\n```'); if(-not $block.Success){throw 'Payload missing'}; $payload=$block.Groups[1].Value; $anchor=[regex]::Matches($payload,'(?m)^    \$before = Get-SavedHashes \$baseline ''Baseline''\r?$'); if($anchor.Count -ne 1){throw 'Baseline anchor changed; stop'}; $payload=$payload.Replace($anchor[0].Value,$anchor[0].Value+"`r`n    if (`$before -ine '$($c.CorrectedBaseline)') { throw 'Recorded corrected baseline drift; stop' }"); $payload=$payload.Replace('PINNED_SOURCE_PLUS_STARTUP_AND_SESSION_OVERLAYS_LOCAL_ONLY','PINNED_SOURCE_PLUS_STARTUP_SESSION_AUTHORITY_OVERLAYS_LOCAL_ONLY'); $exercise=Join-Path $evidence 'corrected-manual-exercise.ps1'; Write-NewText $exercise $payload; Get-FileHash -LiteralPath $exercise -Algorithm SHA256; notepad.exe (Join-Path $evidence 'gate.txt')
```

Record correction/inspection/result digests, all launch and prerequisite outcomes,
prior completed Windows evidence references and actual workflow/audit/support controls
in gate.txt. Review the generated payload and owning
[§3 checks](../../docs/sql/auth/permission-cutover/remaining-acceptance.md#3-one-timed-operator-recovery-exercise-including-the-application).
No repeated Phase Verify, test suites, Stage, New or rehearsal. Validate the existing
ODBC executable with help only, then run this new local exercise once:

```powershell
$odbc=Assert-SupportedSqlcmd 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'; powershell.exe -NoLogo -NoProfile -File $exercise; if($LASTEXITCODE){throw 'Exercise failed/incomplete; preserve evidence and exact hashes; no Apply retry'}
```

Use port 14333, database `ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c`,
existing local provisioner credentials and the new actual gate-record reference.
This is a new separately reviewed attempt after a catalog correction, not a retry of
old Apply evidence. The payload captures new BeforeHash/AfterHash, makes one guarded
Apply, performs the retired-state checks, then times incident acknowledgement through
exact rollback/full corrected-baseline equality and application/reconciliation checks
in the same uninterrupted operator console. No Windows reboot occurs. Stop afterward;
no automatic retry, rollback, readiness update or production authorization.

If a later Apply is ambiguous or commits before failure, preserve that **new** attempt
and use the separately reviewed incident path in single-pc-recovery.md with its own
saved context/hashes and this review checkout. Rollback never expires. Missing commit
identity requires exact-state review, not an invented AfterHash. Do not apply that
incident path to the already-recovered `155907-523` attempt. Manual recovery stays
NOT MEASURED until the complete new interval and application evidence actually exist.

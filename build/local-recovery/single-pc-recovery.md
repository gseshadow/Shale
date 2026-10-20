> Superseded for the reported `ed2a137f` SQL 58307 failure by
> [authority-recovery.md](authority-recovery.md). Preserve that failed attempt and
> its original baseline. Brian's completed reboot/password/session checks are not
> repeated; follow the new existing-state correction and finite continuation.

# Brian's remaining single-PC retirement recovery

Full manual recovery: **NOT MEASURED**. This is the finite continuation for the
existing Windows sandbox, not a request to provision or repeat completed checks.
The [owning payload and recovery rules](../../docs/sql/auth/permission-cutover/remaining-acceptance.md#3-one-timed-operator-recovery-exercise-including-the-application)
retain the exact controlled SQL guards. No production action or approval is implied.

## Accepted report and artifact scope

Brian reports these results at update `2a74cd122e87b15fb995722451e026faa212abb8`:
Stage and native PowerShell launcher checks PASS; API started; synthetic password
login with Stay logged in PASS; ordinary close/reopen restored automatically PASS;
explicit logout then close/reopen stayed at login without a restore attempt PASS;
Phase Verify application/API paths and local live routing PASS. These are reported
Windows observations, not executions by this documentation change. Do not rerun them.
Reboot persistence and the timed retirement/recovery interval remain unperformed.

| Component | Exact selection / evidence |
| --- | --- |
| Existing private state | `C:\ShaleVerification\RetirementOperator-1881\SandboxState-1887-B` |
| Existing SQL | `127.0.0.1:14333`, `ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c` |
| Application source | `59d8374858764788c621f6f8d986773b58ab79c4`, original snapshotted classes/dependencies |
| Startup correction | `62a12940aeb5d6994016432cd8985ec66cd98bf7`, `startup-updates/<SHA>/receipt.json` |
| Session correction | `2a74cd122e87b15fb995722451e026faa212abb8`, `session-updates/<SHA>/receipt.json` |
| API and desktop launch | Existing clean tooling checkout at the session correction SHA; session classes first, startup classes next, originals last |
| Relay launch | Same checkout's `sandbox.ps1`, explicitly selecting the retained startup correction |

The composite is a **source-built legacy compatibility sandbox with overlays**.
The session compatibility store uses the old resolver with UTC conversion; current
application V2 source is a different artifact. Neither the pinned source nor the
update SHA is an installed 1.0.136 version. Local SQL TLS, disabled updater and
loopback Relay substitutions retain the limits in [README](README.md). Reported
local Windows remembered restore does not attest the installed production profile,
Azure live transport, production certificate trust or serving API configuration.

## 1. Resolve the whole gate before any operator drill

Review these together and write actual evidence references/outcomes in one private
record. Do not begin Apply if any local prerequisite is unavailable; list all missing
items in that record and stop once, rather than uncovering prerequisites step by step.
No new inventory or infrastructure is required.

* Access to the existing state, original manifest, completed provisioning marker,
  export, pinned source/dependencies, both overlay receipts/classes and clean session
  tooling checkout. Preserve all original checkouts, logs, packets, nonces, certificate
  and archived pre-fix profile/DPAPI ciphertext. Do not regenerate manifests, Stage,
  UpdateStartup, New or replace files. A receipt/path/digest refusal is a blocker.
* Same Windows owner and isolated profile/installation/machine identities; known
  synthetic passwords; existing local auth/runtime passwords, local SQL provisioner
  username/password and **same API signing secret**. No secret printing, transcription,
  tracing, command-line secrets or production credentials. Missing secrets/archives
  block the sequence; do not repair credentials/timestamps with SQL.
* Existing dedicated SQL container/instance available after reboot; Adoptium JDK 21
  at the reported path; Windows PowerShell 5.1; Microsoft **ODBC SQLCMD 17/18** at the
  explicit path below. Existing sandbox launchers validate original/overlay integrity,
  local identity/nonce and non-bypass authority; their failures stop acceptance.
  Review retained baseline pre-Stage-A/body/ACL evidence; the drill's read-only baseline
  will check it again. No V1/V2 installation, grant edit or SQL rehearsal.
* Four local consoles, same PC: Relay, API, Desktop, operator. SQL/API remain up for
  the whole timed interval; no Windows restart, sleep, console closure, automatic
  update or unrelated operator activity during it. Reserve the five-minute retired-state
  trial plus 20 uninterrupted recovery minutes. Know normal shutdown/relaunch commands.
* Private access-restricted evidence location outside all Git checkouts and original
  state. Know where rollback SQL, BeforeHash/AfterHash and saved output will be located;
  know the incident path if Apply is ambiguous, an application check fails, the console
  exits or the bound expires. Rollback never expires; missing AfterHash needs exact-state
  review. Brian owns pause/drain/reconcile/resume/support and the lack of a second verifier.
* Required observable workflow: synthetic1/tenant 11 and synthetic3/tenant 22 login /
  displayed identity, desktop/API session operations, Team directory/detail and self
  First Name edit/audit, local live reconnect, actual error output and reconciliation.
  Existing Phase Verify covers baseline capability, so do not rerun it. Have the existing
  local-admin audit viewer/query ready: select the expected USER/UPDATED audit with the
  actual user/tenant/time, and the persisted profile value; no new runtime audit grant.
  Record known due jobs as none for this sandbox unless an already-known selected job
  exists; then record its actual controls/result. Any identified failure blocks resume.
* Reboot evidence from step 3 must PASS before Apply. Review all other local items now,
  then add that result to the same gate record. No result is inferred from typing a prompt.

Separately list **production-readiness blockers** together: actual representative
installed 1.0.136 / serving API build/configuration binding and acceptance; existing
identity/authority review; already-known caller/work disposition; operational stop,
follow-up and reopened-risk dates; Brian's final evidence review and separate window
and risk approvals. Reuse existing attributed records where sufficient. Unknown
callers remain named exclusions, not a fleet survey. These do not forbid a local
synthetic drill, but a local pass cannot close them. Full local manual evidence is
also missing until step 4 actually finishes. No automatic readiness.json update.

## 2. Bind the retained launchers and a new evidence checkout

Run each code block as **one command line** in native Windows PowerShell 5.1.
No provisioning/build command is part of this sequence. First, in the existing clean
session tooling checkout, initialize selectors. Repeat this line in each service
console after reboot, then change to that same checkout using the next line.

```powershell
$ErrorActionPreference='Stop'; $state='C:\ShaleVerification\RetirementOperator-1881\SandboxState-1887-B'; $jdk='C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; $session='2a74cd122e87b15fb995722451e026faa212abb8'; $startup='62a12940aeb5d6994016432cd8985ec66cd98bf7'
```

Prompt: enter the **existing** clean session tooling checkout path, not state/source,
not the installed app, and not a new copy at the continuation PR revision.

```powershell
$appCheckout=Read-Host 'Existing session tooling checkout path at 2a74cd122e87b15fb995722451e026faa212abb8'; Set-Location -LiteralPath $appCheckout; if((git rev-parse HEAD).Trim() -cne $session -or $LASTEXITCODE){throw 'Wrong retained session checkout'}
```

In the operator console only, obtain this documentation/payload in a new detached
worktree. Prompt: enter this PR's **full reviewed commit SHA**, not the session SHA.
Preserve the old tooling checkout and staged manual-exercise file. The fetch/worktree
steps do not touch the database. Do not fetch/replay other test/provisioning branches.

```powershell
$continuation=Read-Host 'Full reviewed single-PC continuation commit SHA'; if($continuation -notmatch '^[0-9a-f]{40}$'){throw 'Full reviewed SHA required'}; git fetch origin codex/single-pc-retirement-recovery; if($LASTEXITCODE){throw 'Fetch failed'}; $reviewCheckout=Join-Path ([IO.Path]::GetDirectoryName($state)) ('SinglePcReview-'+[guid]::NewGuid().ToString('N')); git worktree add --detach $reviewCheckout $continuation; if($LASTEXITCODE){throw 'Worktree failed'}; Set-Location -LiteralPath $reviewCheckout
```

Create a new private evidence directory under the existing protected verification
root. Retain its path on paper/private operator notes for after reboot. In `gate.txt`,
record each prerequisite/result/reference, build/manifest/receipt digests, console
paths, expected workflow/audit, error baseline and known-work/support/recovery controls.
Record PENDING/FAILED honestly. Never store plaintext passwords/tokens in notes.

```powershell
$evidence=Join-Path ([IO.Path]::GetDirectoryName($state)) ('SinglePcEvidence-'+[guid]::NewGuid().ToString('N')); $null=New-Item -ItemType Directory -Path $evidence; notepad.exe (Join-Path $evidence 'gate.txt')
```

Validate the explicit ODBC executable with **help only**, without SQL credentials or
connections; this is tool availability, not a repeated SQLCMD rehearsal.

```powershell
. .\docs\sql\auth\permission-cutover\retirement-operator.ps1; $odbc=Assert-SupportedSqlcmd 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'
```

## 3. Untimed reboot persistence, before retirement

Because the last reported logout correctly left no credential, use the Desktop
launcher below to establish **one new checked synthetic login for reboot acceptance**.
This is not a repeat of the completed password/ordinary reopen/logout acceptance.
Leave an already-running correct Relay/API alone. Where a service is down, use its
line in its assigned console from the retained session checkout after the selectors
above. Each command stays attached; prompts use existing secrets only.

Relay console (no credential prompt):

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase Relay -StatePath $state -JavaHome $jdk -ToolingUpdate $startup; if(-not $?){throw 'Relay stopped; preserve evidence'}
```

API console (existing app-login password, runtime-login password, API signing secret):

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase Api -StatePath $state -JavaHome $jdk -ReviewedRevision $session; if(-not $?){throw 'API stopped; preserve evidence'}
```

Desktop console (existing app/runtime passwords, then synthetic credentials in UI):

```powershell
& .\build\session-utc-update\session-update.ps1 -Phase Desktop -StatePath $state -JavaHome $jdk -ReviewedRevision $session; if(-not $?){throw 'Desktop stopped; preserve evidence'}
```

Observe protected-save success and selected identity. X-close Desktop normally,
**without Logout**. Save notes/logs; stop API/Relay normally in their owned consoles;
restart Windows through its normal UI. No retirement SQL or recovery timer is running.
After reboot, open Docker Desktop if needed and resume only the existing dedicated
SQL container through its existing control. If that exact container is stopped:

```powershell
docker start shale-case-read-sql; if($LASTEXITCODE){throw 'Existing SQL container unavailable; stop, do not create a replacement'}
```

Do not create/reconfigure/pull containers, change ports, kill/adopt listeners or mount
production settings. Repeat step 2's selector and existing-checkout lines in the three
service consoles; start Relay, then API, then Desktop with the same three lines above.
Use the same Windows owner, isolated profile and API signing secret. Observe automatic
remembered restore **without entering the synthetic password**. Record actual time,
identity, HTTP/desktop outcomes and private log/screenshot references in gate.txt.
If it prompts for password, rejects, returns 500/uncertain status or cannot save the
rotated protected record: record FAILED/INCOMPLETE and stop before Apply. No repeated
Retry, secret file edit, credential deletion, timestamp repair or automatic approval.
This reboot check measures persistence once services return, not their availability.

## 4. One uninterrupted local retirement/recovery interval

Return the operator console to `$reviewCheckout` (or prompt for its retained path after
reboot). Prompt for the saved `$evidence` directory and update gate.txt with reboot PASS
and every prerequisite resolved. Keep services on the retained session/startup selectors.
Do not rerun Stage, launcher tests, Phase Verify, New, Prepare, Rehearse or prior checks.

```powershell
$ErrorActionPreference='Stop'; $reviewCheckout=Read-Host 'Retained single-PC review checkout path'; Set-Location -LiteralPath $reviewCheckout; $evidence=Read-Host 'Retained SinglePcEvidence directory path'; if(-not(Test-Path -LiteralPath (Join-Path $evidence 'gate.txt') -PathType Leaf)){throw 'Actual gate evidence required'}; notepad.exe (Join-Path $evidence 'gate.txt')
```

Extract the revised §3 payload into **a new file** in that evidence directory. Never
run or overwrite `$state\manual-exercise.ps1`. The extracted payload uses the current
review checkout's unchanged SQL and transport helpers, not files edited in state.

```powershell
$text=Get-Content -LiteralPath .\docs\sql\auth\permission-cutover\remaining-acceptance.md -Raw; $section=$text.Substring($text.IndexOf('## 3. One timed operator recovery exercise')); $block=[regex]::Match($section,'(?s)```powershell\r?\n(.*?)\r?\n```'); if(-not $block.Success){throw 'Revised payload not found'}; . .\docs\sql\auth\permission-cutover\retirement-operator.ps1; $exercise=Join-Path $evidence 'single-pc-exercise.ps1'; Write-NewText $exercise $block.Groups[1].Value; Get-FileHash -LiteralPath $exercise -Algorithm SHA256
```

Read the extracted script and owning §3 checks once. Retain its digest and the
review checkout SHA with gate.txt. Execute once from this review checkout:

```powershell
powershell.exe -NoLogo -NoProfile -File $exercise; if($LASTEXITCODE){throw 'Exercise failed/incomplete; stop admission, preserve evidence, follow guarded incident recovery; never replay Apply'}
```

Prompts, in order: local port **14333**; exact database above; dedicated local SQL
admin username; actual gate/reboot evidence record ID; explicit authorization for
this one synthetic exercise; secure local admin password. The script creates a new
`C:\ShaleVerification\ManualRecovery-<timestamp>` directory and retains SQL input,
SQLCMD output/results, original BeforeHash/AfterHash and an Apply-attempt marker.
After Apply, perform the **new retired-state checks** in §3 and enter their actual
PASSED/FAILED/INCOMPLETE outcome. Failure still requires guarded rollback. Preserve
BeforeHash/AfterHash independently before proceeding; never derive replacements.

The timer starts **before incident acknowledgement**. Acknowledge pause/drain and
finding the saved recovery records; securely re-enter the local admin password.
After guarded rollback/full baseline equality, perform §3's application/session,
workflow/audit, live reconnect and reconciliation/resume checks while the **same
operator console and timer continue**. Enter the exact final phrase only if every
check actually passed, then the actual private application evidence record ID.
Keep notes ready in the Desktop/other console; the operator process is waiting.

Retain StartedUtc, SqlVerifiedUtc, all outputs, actual per-check notes and either
manual-result.json or FAILED.txt. A result file is Brian's assertion backed by those
records, not automatic approval. Only the full completed interval may populate the
**local** MeasuredRecoverySeconds. Until that execution is reported, manual recovery
remains **NOT MEASURED**. Reboot duration, SQL-only `0.743829` and suite `44.5663216`
are excluded from that field. No successful rerun to improve time. Stop after this
exercise: the review copy and production window in §4 remain separate decisions.

## Incident path, reviewed before Apply

If the payload stops after Apply, stop local admission and retain FAILED/output.
There is no automatic rollback/retry. If unambiguous saved Apply output and the
independently retained hashes exist, use this **separate explicit incident command**
from the same review checkout. This is recovery after failure, not another drill.
Prompt: the exact failed ManualRecovery directory, then the existing LOCAL recovery
admin password. The retained context has no password. The command verifies both
saved markers/expected hashes, invokes the unchanged guarded rollback without an
exercise deadline, and reverifies full baseline equality into new unique log names.
If any marker/hash is missing, mismatched or ambiguous, stop for exact-state review;
never substitute a current catalog hash. Do not rerun a successful rollback.

```powershell
. .\docs\sql\auth\permission-cutover\retirement-operator.ps1; $failed=Read-Host 'Exact failed ManualRecovery evidence directory'; $c=Read-Json (Join-Path $failed 'recovery-context.json'); if($c.Database -cne 'ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c' -or $c.Server -cne 'tcp:127.0.0.1,14333' -or $c.Repo -cne (Get-Location).Path){throw 'Wrong recovery context'}; $before=(Get-Content -LiteralPath (Join-Path $failed 'BeforeHash.txt') -Raw); Assert-Hex $before; if($before -ine $c.BeforeHash -or (Get-SavedHashes (Join-Path $failed 'before.sqlcmd.txt') 'Baseline') -ine $before){throw 'Original baseline mismatch'}; $after=Get-SavedHashes (Join-Path $failed 'apply.sqlcmd.txt') 'Apply' $before; if($after -ine (Get-Content -LiteralPath (Join-Path $failed 'AfterHash.txt') -Raw)){throw 'Saved AfterHash mismatch; exact-state review required'}; $odbc=Assert-SupportedSqlcmd 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'; $password=Read-Host 'Existing dedicated LOCAL recovery SQL password' -AsSecureString; try{$prefix=Join-Path $failed ('incident-'+[guid]::NewGuid().ToString('N')); $vars=@($c.Common)+@('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$before","LegacyAfterHash=$after"); $rolled=Invoke-PacketSql $c.Repo $c.Server $c.Database $c.User $password 'docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql' $vars $prefix $odbc -LocalOnly; if((Get-SavedHashes $rolled 'Rollback') -ine $before){throw 'Rollback equality failed'}; $verified=Invoke-PacketSql $c.Repo $c.Server $c.Database $c.User $password 'docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$($c.Database)") ($prefix+'-verify') $odbc -LocalOnly; if((Get-SavedHashes $verified 'Baseline') -ine $before){throw 'Full catalog equality failed'}}finally{$password.Dispose()}
```

A SQL recovery success leaves manual acceptance FAILED/INCOMPLETE until the actual
application/support/reconciliation outcome is reviewed. Retain all incident evidence
and original failed timing; no automatic resume, new timer or production approval.

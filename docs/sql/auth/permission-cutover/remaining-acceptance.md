# Finite controlled-retirement acceptance

This continues the [owning runbook](../../../testing/auth-module-permission-cutover.md#bounded-evidence-and-acceptance).
It does not authorize a production connection/change. Brian is the security owner,
operator, verifier, incident lead and support contact; independent verification is
absent by his choice. He reports everyone uses desktop 1.0.136. Accept the bounded
attestation without a fleet survey. Unknown/dormant/dynamic/external callers remain
a named risk, not a new collection project. Known incompatible callers still block.

## 1. Review evidence once; do not restart preparation

At `041fa1ed0b21d64cce3599a01eaa3344ecf57331`, Brian reports native Windows PS5.1
offline runner, real ODBC complete-variable/timestamp, and full local SQLCMD rehearsal
PASSED, including refusals/apply/rollback/catalog equality/cleanup. Current originals:

* `C:\ShaleVerification\RetirementOperator-1881\ValuesFix-20261016-161952-926\Packet`
* `C:\ShaleVerification\RetirementOperator-1881\ValuesFix-20261016-161952-926\LocalRehearsal`

These files were not available here. Brian reviews their actual records; no invented
evidence IDs or automatic readiness changes. Do not rerun these passing checks,
Prepare, the 44.57-second suite, Stage A/B, or unrelated broad suites.

The map below distinguishes source coverage from a recorded execution. Existing
Java methods are under `shale-server/src/test/java/com/shale/server/` unless noted.
An old passing report can satisfy only assertions actually present at that revision.
New tests cannot be backdated into that report.

| Readiness requirement | Exact existing evidence/coverage | Limit and genuine remaining work |
| --- | --- | --- |
| IdentityAuthority | Reported actual shale_app/shale_runtime login/user/role observations; `runtime/PreTenantAuthenticationLocalSqlServerTest.caller` rejects dbo/owner/CONTROL/impersonation, and fixture authority diagnostics exercise nested/public/ownership findings. | Synthetic roles do not inspect serving login/server/fixed/nested tokens or configuration precedence. Brian finishes one review of existing private identity/authority records. No new Azure query is authorized here. |
| KnownArtifacts / known callers | Supplied desktop/data/server digests and attributed artifact findings in evidence-scope; Brian's all-1.0.136 attestation. | Bind to the actual representative installed desktop and serving API/config references; disposition only already-known job/integration/Function work. Unknown callers are exclusions; an identified unresolved caller blocks. No fleet inventory. |
| DesktopPassword | PC-000 correct/incorrect password and ordinary workflow report. `controller/DesktopSessionControllerTest.verifiedCredentialsCreateExactlyOneDesktopSessionBoundToOwnedInstance` and `invalidCredentialsAndForeignInstanceFailWithoutSessionAndClaimsCannotBeSupplied` cover second exchange and foreign-instance rejection using fakes. `PreTenantAuthenticationLocalSqlServerTest.credentials` covers actual bcrypt/eligibility under non-bypass SQL roles. | The latter uses LookupAuthenticationCredentialV1 after Stage A/B, not the deployed direct Users path. Safe representative installed password/second exchange remains before/during-trial acceptance. New local compatibility test below checks real nonempty direct Users/bcrypt across retirement. |
| DesktopRemembered | PC-000 checked restore after Windows restart, earlier checked/unchecked/ordinary close reports, only as stated. `shale-desktop/.../DesktopSessionEnrollmentLifecycleTest.ordinaryShutdownPreservesRememberedCredentialButExplicitLogoutDeletesAndRevokes`, `authoritativeRevocationDeletesRememberedCredential`, `newGenerationAndShutdownClearPreviousBearer`, and checked-login failure tests cover lifecycle seams with fakes. | No existing behavioral restore/Retry or protected-read/write failure test. New DesktopRememberedRecoveryTest closes these synthetic gaps. Windows DPAPI and real reboot remain installed-artifact observations; test doubles do not certify DPAPI. |
| ApiPassword | `controller/AuthControllerTest`: login success, bad password/unknown user, /me valid/invalid/missing token and logout/revoked-token rejection. `runtime/DurableSessionAuthTest.boundTokenRequiresSharedDurableStateAndRotationInvalidatesOldJti`, `durableValidationRejectsMissingRevokedExpiredWrongOwnerAndMalformedClaims`, `legacyEligibilityIsBoundedAndRefreshUpgradesToDurableSession`; `ServerAuthSessionLogoutTest.boundLogoutRevokesTheExactEnrolledSessionWithUserLogoutReason`. | MockMvc/service/memory-store evidence, not a deployed HTTP/SQL run. Safe serving API login, /me, refresh/logout and user switch remain representative checks. Do not force replay/revocation on real staff accounts. |
| ApiRemembered | `RememberedDesktopSessionTest.checkedEnrollmentAndRestoreRotateOpaqueCredentialWithoutExtendingDeadline` covers opaque rotation, deadline and replay using a fake. Its `wrongInstallationRevocationExpiryAndDisabledOwnerAreRejected` actually asserts wrong installation and a single inactive-store rejection: its name alone does NOT prove distinct expiry/disabled/revocation outcomes. `PreTenantAuthenticationLocalSqlServerTest.remembered` explicitly covers wrong installation, rotation/JTI/deadline, replay, revoked session, deleted/removed/NULL-tenant and expired session with real synthetic SQL. | This SQL store uses V2 after Stage A/B. Its destructive-case evidence is valid for that shared transaction contract, not proof the old resolver or serving artifact was exercised. New local test covers the actual old resolver's identity filters across retirement. Safe serving old-resolver restore remains necessary. |
| Tenant/actor isolation (API rows) | `PreTenantAuthenticationLocalSqlServerTest.runtimeIsolation`: same physical pooled SPID across 11/1, 11/6, 22/2, 11/1; context stamping, cross-tenant filtering, no-context denial, module/hash denial and rolled-back password write. `DurableSessionAuthTest` rejects wrong tenant/user; request-provider tests verify derived principal. | The SQL fixture explicitly adds simulated strict Users RLS AFTER cutover tests. It does not establish deployed Users RLS. Use disposable destructive isolation evidence at its stated layer; safe representative account switch confirms actual displayed identity/context. No production cross-tenant probing. |
| NeighboringWork | PC-000 User directory/detail and one ordinary workflow report. Existing cutover fixture permits TOP(0) User projections, old-resolver empty invocation and audit table access; runtimeIsolation rolls back password writes. | Empty probes are not completed workflows, jobs, reconnects or audited writes. Brian checks one designated safe ordinary workflow/audit and existing live-update reconnect; each already-known due job/integration gets an actual result/date or truthful untested residual-risk entry as the runbook permits. Identified failure blocks resume. No fake job created merely to claim integration coverage. |
| ExactSqlcmdRehearsal | Attributed successful Windows local rehearsal at reviewed 041fa1ed, including connection-close atomic refusal, exact nine-row delta, wrong-hash refusal, full catalog restoration and cleanup. `AuthPermissionCutoverFixture.actualLegacyRetirement` also covers missing approvals/route/window, unsupported ACL/dependency/drift, post-mutation atomicity, route equality and expiry-independent rollback using JDBC. | JDBC manually rolls back exceptions and expands includes; it cannot replace native SQLCMD connection-close behavior. The latest Windows report supplies that distinct evidence. No remaining SQLCMD rehearsal gap is inferred. |
| OperationalReadiness / FollowUp | Existing log-delivery report proves delayed delivery only; desktop direct-SQL failures may be absent. | Brian records existing same-path baseline, one-minute thresholds/stop symptoms, support route, known-work pause/drain/retry/reconciliation controls, daily review through >=7 calendar days, actual known next-due dates (or explicit none), and reopened-risk review date. No new telemetry or invented queue lifetime. |
| ManualRecovery / MeasuredRecoverySeconds | **NOT MEASURED.** `0.743829` seconds is SQL-only rollback/catalog reverification. Earlier `44.5663216` seconds is the entire automated suite. | Neither includes Brian's incident decision, locating records, credentials, application checks, reconciliation or resume. Perform exactly the exercise in section 3; do not enter either automated time as manual recovery. |

## 2. Missing automation: dedicated synthetic checks only

`DesktopRememberedRecoveryTest` uses the real desktop HTTP client and lifecycle,
one random loopback HTTP server and in-memory synthetic protected state. It checks
outage before rotation, lost response after rotation, bounded journal Retry,
authoritative HTTP rejection, protected-read/write failures before HTTP, unchecked
startup and ordinary-close persistence. It never accesses a real DPAPI file or staff
credential and does not stand in for the installed application.

`ControlledRetirementCompatibilityLocalSqlServerTest` creates a new random database
on a separately opted-in **local** SQL instance. It uses the unchanged controlled
SQL entry points, captured CRLF legacy bodies, real old resolver DDL and synthetic
data. Before/during/after retirement it asserts nonempty direct Users/bcrypt success
and rejection, old-resolver tenant/user results, wrong installation, changed-hash
replay, deleted/removed/expired identity rejection, and tenant-qualified User reads.
Full original catalog digest and ACL/grantors must match after rollback. No Stage
A/B or unrelated diagnostic sweep runs. Database-user impersonation is not actual
login-token evidence; changing a hash in the fixture is not the serving API's
rotation transaction. In particular the old resolver is an identity lookup, not a
session-revocation validator. The existing transaction tests above supply that
separate destructive-case layer. No deployed audit behavior is inferred.

Run only these additions and their directly shared desktop fixture in the normal
reviewed checkout (JDK 21/Maven already installed). Windows PS5.1:

```powershell
$ErrorActionPreference = 'Stop'
mvn -pl shale-desktop -am '-Dtest=DesktopRememberedRecoveryTest,DesktopSessionEnrollmentLifecycleTest' -Dsurefire.failIfNoSpecifiedTests=false test
if ($LASTEXITCODE -ne 0) { throw 'Desktop recovery regression failed; stop' }
# Dedicated LOCAL admin only, never an application/production credential.
# Existing application connection variables/JVM injection options must be absent.
$env:SHALE_AUTH_VERIFY_OPT_IN = 'LOCAL_DISPOSABLE_AUTH'
$env:SHALE_AUTH_VERIFY_PORT = '14333'
$env:SHALE_AUTH_VERIFY_ADMIN_USER = Read-Host 'Dedicated LOCAL synthetic SQL admin'
$secret = Read-Host 'Dedicated LOCAL synthetic SQL password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:SHALE_AUTH_VERIFY_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    mvn -pl shale-server -am -Dtest=ControlledRetirementCompatibilityLocalSqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
    if ($LASTEXITCODE -ne 0) { throw 'Focused SQL compatibility failed; preserve exact printed local resource; stop' }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secret.Dispose()
    foreach ($name in @('SHALE_AUTH_VERIFY_OPT_IN','SHALE_AUTH_VERIFY_PORT','SHALE_AUTH_VERIFY_ADMIN_USER','SHALE_AUTH_VERIFY_ADMIN_PASSWORD')) {
        Remove-Item -LiteralPath ('Env:'+$name) -ErrorAction SilentlyContinue
    }
}
```

Do not rerun them if an accepted execution record already covers this final revision.
Success requires test counts >0, no skips, the SQL PASS marker and exact-resource
cleanup. Failure retains the printed random database for exact review; never use a
wildcard DROP, forced disconnect, replacement expected hash or automatic retry.

Implementation verification on 2026-10-16, based on `568e8d403509e868aa3fc2f939ebb0592250e8e4`:

* `mvn -pl shale-desktop -am -Dtest=DesktopRememberedRecoveryTest,DesktopSessionEnrollmentLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **15 tests passed, zero failures/errors/skips** (six new recovery checks, nine existing lifecycle checks).
* `mvn -pl shale-server -am -Dtest=ControlledRetirementCompatibilityLocalSqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **one test passed, zero failures/errors/skips**, against a dedicated loopback SQL
  Server 2022 container. The PASS marker confirms full-digest recovery and exact
  nonce-owned database cleanup. No application/production credential was used.
* New PowerShell command blocks parsed with PowerShell Core 7.4.6, without executing
  them. This is syntax validation only, not native Windows PS5.1, SQLCMD or manual
  application acceptance.

The local Maven invocations used workspace-local Maven 3.9.9/JDK 21 and session
proxy/trust settings, outside the repository. Pre-edit impact review inspected the
runner/ODBC tests, controlled SQL contracts, existing cutover fixture, pre-tenant
SQL test, desktop client/lifecycle/remembered/UI contracts, API controller and durable
session/store tests named in the map. Only tests and instructions changed: the
shared SQL fixture gains a separately called method; its existing broad diagnostic
and Stage A/B path is unchanged. The new SQL test calls that method, and the new
desktop test reuses the existing synthetic store. No production symbol, SQL guard,
runner serialization or captured body changed. Broad/critical suites are deliberately
not rerun under the task's explicit scope. Existing tests listed in the map are
source coverage plus their attributed earlier reports, not new execution claims.
`python build/test-selection/select_tests.py --base origin/codex/latest --head HEAD`
completed successfully: the documentation paths select the broad security-data
area in addition to directly modified tests. That advisory selection is recorded,
not executed; Case/Contact/audit/RLS implementations are untouched and the user
explicitly excluded unrelated broad reruns. `git diff origin/codex/latest HEAD --check`
also passed.

## 3. One timed operator recovery exercise, including the application

Runnable local provisioning and isolated source-build launchers are in
[build/local-recovery](../../../../build/local-recovery/README.md). They consume the
hash-verified private schema export and support the directory/profile-audit/session
paths below. Their pinned source builds and local live/TLS/updater substitutes do not
certify installed 1.0.136, serving production configuration, Windows DPAPI or reboot.
They do not run this exercise, change readiness or authorize a window. Manual recovery
remains NOT MEASURED. The unchanged timed block below is staged privately by provisioning.

**Still unperformed.** Brian performs this once in an approved disposable application
sandbox with **synthetic data only**, representative installed desktop 1.0.136 and
the reviewed serving API build/configuration. Use an isolated Windows test profile,
designated synthetic accounts/tenants, working directory/detail and one ordinary
audited workflow, existing live-update support and any already-known job selected
for the exercise. The application sandbox must actually support these paths. The
minimal SQL unit-test database and its HTTP fake cannot do that. If no representative
sandbox is available, this is the specific manual-evidence blocker; do not secretly
substitute production or mark recovery measured.

Before the timer, verify the target is the dedicated loopback synthetic database,
the desktop/API point ONLY there, the original captured legacy bodies/three grants
and pre-Stage-A state match, and baseline safe application checks succeed. Privately
record actual identities, build/config references and expected workflow/audit results.
Use existing approved sandbox provisioning, not a production data copy. No staff
account changes, production replay/revocation, telemetry or employee interruption.
Review existing exact known-work pause/resume/retry controls. No guessed commands
are supplied for unreported jobs.

Use the unchanged runner's transport helpers for this local drill; this avoids
hand-written ODBC quoting and never uses/edits the Windows production packet.
From the reviewed repository root in Windows PS5.1, save this block as a new private
exercise command file, supply only the stated local selectors, then run it once.
The selected database must already be the functioning synthetic app sandbox; this
block does not create an incomplete replacement application database.

```powershell
$ErrorActionPreference = 'Stop'
. .\docs\sql\auth\permission-cutover\retirement-operator.ps1
$repo = (Get-Location).Path
$odbc = Assert-SupportedSqlcmd 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'
$port = [int](Read-Host 'Dedicated LOCAL sandbox port, 1024..65535')
if ($port -lt 1024 -or $port -gt 65535) { throw 'Local port refused' }
$server = 'tcp:127.0.0.1,'+$port
$db = Read-Host 'Dedicated synthetic app database name (not Shale)'
if ($db -notmatch '^ShaleRetirementExercise_[A-Za-z0-9]+$') { throw 'Dedicated exercise database required' }
$user = Read-Host 'Dedicated LOCAL sandbox SQL admin'
$out = 'C:\ShaleVerification\ManualRecovery-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')
if (Test-Path -LiteralPath $out) { throw 'New evidence directory required' }
$null = New-Item -ItemType Directory -Path $out
$password = Read-Host 'Dedicated LOCAL sandbox password' -AsSecureString
$common = @("ExpectedDatabase=$db",'LegacyAcceptanceRoute=CONTROLLED_CHANGE',
 'ReviewedIdentityMapping=CONFIRMED','LegacyRetirementApproval=APPROVED',
 'RuntimeLegacyDisposition=DENY_THREE_MODULES','ExclusiveChangeWindow=CONFIRMED',
 'CompletedPackageReview=APPROVED','ControlledWindowAuthorization=AUTHORIZED',
 'UnknownClientRisk=ACCEPTED_NOT_ZERO_PROOF','IrrecoverableEffectsRisk=ACCEPTED',
 'ReopenedCapabilityRisk=ACCEPTED','RepresentativeChecks=PASSED','RollbackRehearsal=PASSED',
 'SecurityOwner=SYNTHETIC','ServiceOwner=SYNTHETIC')
foreach ($key in @('RiskApprovalEvidence','PackageReviewEvidence','WindowAuthorizationEvidence',
 'KnownArtifactEvidence','CompatibilityEvidence','OperationalReadinessEvidence','RollbackRehearsalEvidence')) {
    $common += "$key=SYNTHETIC_LOCAL_EXERCISE_ONLY"
}
function Local-Step($name,$sql,$vars,$deadline) {
    $inputFile = Join-Path $out ($name+'.sql')
    Write-NewText $inputFile $sql
    $null = Invoke-PacketSql $repo $server $db $user $password $inputFile $vars (Join-Path $out $name) $odbc $deadline -LocalOnly
    return (Join-Path $out ($name+'.sqlcmd.txt'))
}
try {
    $baseline = Local-Step 'before' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$db") ([datetime]::UtcNow.AddMinutes(1))
    $before = Get-SavedHashes $baseline 'Baseline'
    Write-NewText (Join-Path $out 'BeforeHash.txt') $before
    $now = [datetime]::UtcNow
    $applyVars = $common + @("LegacyBeforeHash=$before","WindowStartUtc=$($now.AddMinutes(-1).ToString('s'))",
        "WindowEndUtc=$($now.AddMinutes(28).ToString('s'))","RollbackStartByUtc=$($now.AddMinutes(17).ToString('s'))")
    # ONE local apply. Nonzero/ambiguous outcome stops; never replay or replace hashes.
    $applied = Local-Step 'apply' ':r docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql' $applyVars ($now.AddMinutes(17))
    $after = Get-SavedHashes $applied 'Apply' $before
    Write-NewText (Join-Path $out 'AfterHash.txt') $after
    # Start BEFORE incident acknowledgement, work pause, locating recovery records or password re-entry.
    $t0 = [datetime]::UtcNow
    Write-NewText (Join-Path $out 'StartedUtc.txt') $t0.ToString('o')
    $limit = $t0.AddMinutes(20)
    Write-Host "SIMULATED INCIDENT. Stop local test admission now. Hard exercise end $($limit.ToString('o'))."
    Confirm-Exact 'Acknowledge incident/support route, pause/drain known synthetic work with existing controls; locate saved rollback/hash records.' 'LOCAL WORK PAUSED AND RECOVERY RECORDS LOCATED'
    $password.Dispose()
    $password = Read-Host 'Re-enter dedicated LOCAL recovery SQL password' -AsSecureString
    $rollbackVars = $common + @('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$before","LegacyAfterHash=$after")
    $rolled = Local-Step 'rollback' ':r docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql' $rollbackVars $limit
    if ((Get-SavedHashes $rolled 'Rollback') -ine $before) { throw 'Exact rollback equality failed' }
    $verified = Local-Step 'reverify' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$db") $limit
    if ((Get-SavedHashes $verified 'Baseline') -ine $before) { throw 'Full catalog reverification failed' }
    Write-NewText (Join-Path $out 'SqlVerifiedUtc.txt') ([datetime]::UtcNow.ToString('o'))
    # Keep this timer running on a separate operator console while restarting the test machine.
    # An interrupted operator console is an incomplete exercise; preserve all evidence, no SQL replay.
    Confirm-Exact 'Perform ALL application/reconciliation checks listed immediately below and retain actual outcomes privately.' 'ALL LOCAL APPLICATION AND RECONCILIATION CHECKS PASSED'
    $appEvidence = Read-Host 'Actual private application/reconciliation evidence record ID'
    Assert-SafeValue $appEvidence
    $end = [datetime]::UtcNow
    if ($end -ge $limit) { throw '20-minute exercise bound exceeded; retain elapsed evidence as FAILED, no window approval' }
    Write-NewJson (Join-Path $out 'manual-result.json') @{Status='PASSED_LOCAL_EXERCISE_ONLY';StartedUtc=$t0.ToString('o');FinishedUtc=$end.ToString('o');MeasuredRecoverySeconds=($end-$t0).TotalSeconds;ApplicationEvidence=$appEvidence}
} catch {
    Write-NewText (Join-Path $out 'FAILED.txt') ('STOPPED '+[datetime]::UtcNow.ToString('o')+'; preserve outputs and local resource; no automatic retry')
    throw
} finally { $password.Dispose() }
```

During the final prompt, Brian performs and records these actual checks in order:

1. Desktop correct password and second API exchange succeed; incorrect password
   rejects. API login, /me, refresh and logout give the expected current identity.
2. Checked remembered login, ordinary close/reopen and Windows test-machine restart
   restore correctly. Unchecked login leaves no startup restore. Confirm a safe
   switch between designated synthetic users changes displayed tenant/actor and
   leaves no prior user's session. Use the separately mapped disposable evidence
   for destructive replay/revocation/disabled cases; do not damage real accounts.
3. User directory/detail and one approved ordinary synthetic workflow produce the
   expected result and existing audit entries. Reconnect the existing live-update
   client and verify its expected result. Check any already-known selected job's
   result through its existing control; otherwise record explicitly no known due
   job for this drill. Do not treat an unrun job as PASSED.
4. Check the existing error/support channel and expected baseline. Identify the
   simulated affected work, compare actual completion/audit/accounting records,
   decide retry/idempotency from those records, and resume only reconciled local
   test work. No blind replay, fabricated history or guessed accounting correction.
   Record that grants reopen the original vulnerable capabilities and cannot repair
   lost failure counters, locks, last_login, audits or external effects.

Target schedule: minutes 0–2 incident/pause/records, 2–4 password/guarded rollback/full
catalog verification, 4–12 desktop/API/restart, 12–17 ordinary work/audit/reconnect,
17–20 reconciliation/resume/record. These are budgets, not results. Missing/failed
checks stop acceptance; preserve timings/outputs and the exact sandbox for review.
Do not rerun a successful drill to improve its time. A failed/overrun drill remains
failed until its specific problem is resolved, without extending this timer.

Only the observed full interval, including manual delays, supports
MeasuredRecoverySeconds. The result file is an operator assertion requiring the
real application evidence record; typing the phrase alone proves nothing. Use
`max(600, ceil(MeasuredRecoverySeconds))` as reserve. If recovery plus drain/trial
cannot fit a 30-minute window, reject that window; no automatic extension.

## 4. One final review copy, then one separately authorized window

Leave both original Windows directories untouched. After the real drill and
bounded reviews, Brian may make ONE new private review copy of the successful
packet (not Prepare/rebuild). Confirm every copied file hash matches its original
before changing only the copy's readiness.json. Retain the original reviewed
041fa1ed source, packet/seal/captures/SQL/commands byte-for-byte. This PR's supplement
and new-test/manual evidence are separate references; no source revision relabeling.

```powershell
$ErrorActionPreference = 'Stop'
$original = 'C:\ShaleVerification\RetirementOperator-1881\ValuesFix-20261016-161952-926\Packet'
$packet = 'C:\ShaleVerification\RetirementAcceptance-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')
if (Test-Path -LiteralPath $packet) { throw 'New review-copy directory required' }
Copy-Item -LiteralPath $original -Destination $packet -Recurse
foreach ($file in Get-ChildItem -LiteralPath $original -Recurse -File) {
    $relative = $file.FullName.Substring($original.Length).TrimStart('\')
    if ((Get-FileHash -LiteralPath $file.FullName).Hash -cne (Get-FileHash -LiteralPath (Join-Path $packet $relative)).Hash) { throw 'Copy hash mismatch; stop' }
}
$runner = Join-Path $packet 'source\docs\sql\auth\permission-cutover\retirement-operator.ps1'
# Now review actual records and edit ONLY this copy's readiness.json; no prefilled PASSED rows.
# Preserve row evidence ID, scope/limits, separate SQL-only and full manual timings.
```

Each readiness row needs the evidence in section 1 and an actual private record ID.
No evidence means PENDING. In particular review serving identity/authority, serving
build/configuration, safe representative desktop/API results, neighboring work,
existing detection/stop/retry controls and the full measured manual exercise.
Set KnownCallerDisposition only from the bounded known-work review. Fix real
follow-up UTC dates through at least seven calendar days after go and each known
next due run (or explicit none), plus the reopened-capability review date. All
roles are Brian; the deliberate absence of a second reviewer stays explicit.

Only after Brian reviews this concrete copy and its readiness evidence, separately
authorize the exact UTC window, risk/package decisions and guarded rollback.
There is no authorization in this document. Use the copied runner with explicit
PacketPath, not the old absolute paths in the copied commands.ps1.txt:

```powershell
# $startUtc/$endUtc/$rollbackByUtc come from the actual separate decision,
# each yyyy-MM-ddTHH:mm:ssZ; <=30 minutes, reserve >=600 seconds or full manual time.
powershell.exe -NoLogo -NoProfile -File $runner -Phase Apply -PacketPath $packet -WindowStartUtc $startUtc -WindowEndUtc $endUtc -RollbackStartByUtc $rollbackByUtc
if ($LASTEXITCODE -ne 0) { throw 'STOP: uncertain apply; preserve marker/output/original hashes; never replay' }
powershell.exe -NoLogo -NoProfile -File $runner -Phase Verify -PacketPath $packet
if ($LASTEXITCODE -ne 0) { throw 'STOP: incident; use separately preapproved guarded recovery below' }
# Perform/record safe representative section-1 application checks during the trial.
# Any failure, lost visibility, complaint or incomplete acceptance at rollback cutoff
# requires recovery. This is a separate incident path, NOT an automatic happy-path step:
```

```powershell
powershell.exe -NoLogo -NoProfile -File $runner -Phase Rollback -PacketPath $packet
if ($LASTEXITCODE -ne 0) { throw 'Guarded recovery refused/failed; retain output, exact-state incident review; no bypass/retry' }
powershell.exe -NoLogo -NoProfile -File $runner -Phase Verify -PacketPath $packet
if ($LASTEXITCODE -ne 0) { throw 'Recovery catalog verification failed; incident remains open' }
# Repeat safe password/remembered/ordinary-work checks, reconcile and record actual outcomes.
```

Do not let a shell throw replace the incident response: stop the trial, invoke the
separately preapproved guarded recovery promptly, then inspect refusal/uncertainty.
Preserve the uniquely emitted original AfterHash independently before proceeding.
Missing/ambiguous AfterHash requires exact-state incident review, never a current
hash substituted as expected. No SQL guard, exact rollback, original-route approval,
Stage A/B requirement or tenant/audit constraint is relaxed. Check each minute in
trial/first staffed hour, daily for seven days and at known next due runs. Unknown
late callers remain an explicit support risk after finite closure.

Audit compatibility: this change adds synthetic tests and acceptance instructions.
No application mutation, sensitive-read audit, production event/schema or permission
code changes. Synthetic catalog/data operations are disposable test setup, not
production audited business events; actual representative workflow audit evidence
continues through the established application audit seams.

# Brian's controlled retirement packet

## Process-output correction

Before using a newly reviewed runner, read [transport-output.md](transport-output.md).
It covers the single-log-path return fix and one real-child offline Windows regression.
It does not authorize editing or replaying an existing attempted packet, transferring
its decisions, or rerunning completed application/rehearsal checks.

## Remaining acceptance — use this continuation first

For the missing synthetic application sandbox, use the
[local recovery provisioner and launchers](../../../../build/local-recovery/README.md).
Their source-build scope and Windows/manual limits remain separate from installed
acceptance; no passing preparation/rehearsal is repeated.

The operator reports all three Windows checks at reviewed revision
`041fa1ed0b21d64cce3599a01eaa3344ecf57331` PASSED, including the complete local
SQLCMD rehearsal. **Do not repeat Prepare, serialization regressions or Rehearse
below.** They are retained as the earlier construction instructions, not today's
next steps. Existing private packets remain untouched.

Use [remaining-acceptance.md](remaining-acceptance.md) for the evidence-to-test map,
the focused synthetic additions, one timed manual SQL **and application** recovery
exercise, and the single final approval-copy/window sequence. Brian holds all roles
and reports the estate uses desktop 1.0.136. No fleet inventory, employee interruption,
independent reviewer or telemetry project is required. Unknown callers remain an
explicit bounded risk. The reported `0.743829` seconds is SQL-only recovery;
manual application recovery remains **NOT MEASURED** until the exercise is performed.

Preparation only. Production risk acceptance, completed-package approval and window
execution authorization remain **PENDING**. Use the controlled entry points from merged
[#1880](https://github.com/gseshadow/Shale/pull/1880); original-route requirements keep
all their original meanings. No Azure/production action has been performed by this PR.
The [owning runbook](../../../testing/auth-module-permission-cutover.md) remains authoritative;
[evidence-scope.md](evidence-scope.md) records the supplied facts and remaining gaps.

Use Windows PowerShell 5.1, existing Git and Microsoft ODBC SQLCMD (`sqlcmd.exe`, supports
`-u -y 0 -Y 0 -w 65535 -b -V 16`), existing approved network/SQL identity, and a private
folder with exclusive ACLs outside every checkout. No transcript, shell tracing or query
logging. Nothing here installs tools, changes credentials, Stage A/B or authentication.
Brian serves every role. **Independent human verification is absent**, explicitly accepted
as a process choice; this package does not require another person.

The runner now checks `-?` help in a child process (10-second bound, SQLCMD environment
scrubbed) before prompts in Prepare/Rehearse and before credentials in connected phases.
Only Microsoft ODBC 17/18 is accepted; Go SQLCMD, missing executables and unrecognized help
are refused with an explicit `-SqlcmdPath` example. This is client identification, not a
connection or SQL compatibility test. Switches are serialized bare, spaced file arguments
are quoted, and EVERY `-v` value is quoted, including ISO timestamps and hashes:
`WindowStartUtc="2026-10-16T21:58:55" SecurityOwner="Brian Downing"`.
Whitespace-only quoting is insufficient for Windows ODBC's raw parser. Executable paths stay in
ProcessStartInfo.FileName; no shell or password argument is introduced. Prepared commands
retain the resolved ODBC executable automatically.

The rehearsal setup now terminates its initial final `GO` once and places every canonical
`:r` directly at the next batch start. It adds no leading blank line to failure/success
definitions. Expected hashes, canonical resources and all SQL guards remain unchanged.
The offline native regression captures the real generated setup and compares all three
CREATE batches exactly, including CRLF whitespace; help parsing alone is not rehearsal evidence.

Before preparing a corrected packet, use a **new isolated checkout** of the full reviewed
fix commit. From the existing repository root in Windows PowerShell 5.1:

```powershell
# $reviewedPackageCommit must be the full corrected commit from the fix PR, after review.
git fetch origin codex/fix-retirement-odbc-values
if ($LASTEXITCODE -ne 0) { throw 'Fetch failed; stop' }
$newRoot = 'C:\ShaleVerification\RetirementOperator-ODBC-Values-Fix-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
if (Test-Path -LiteralPath $newRoot) { throw 'New directory required' }
$checkout = Join-Path $newRoot 'Checkout'
git worktree add --detach $checkout $reviewedPackageCommit
if ($LASTEXITCODE -ne 0) { throw 'Isolated checkout failed; stop' }
Set-Location -LiteralPath $checkout
$odbc = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'
powershell.exe -NoLogo -NoProfile -NonInteractive -File build/auth-verification/test_retirement_operator.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline Windows regression failed; stop' }
powershell.exe -NoLogo -NoProfile -NonInteractive -File build/auth-verification/test_retirement_odbc.ps1 -SqlcmdPath $odbc
if ($LASTEXITCODE -ne 0) { throw 'Real Windows ODBC complete-variable regression failed; stop before rehearsal' }
& .\docs\sql\auth\permission-cutover\retirement-operator.ps1 -Phase Prepare -ReviewedRevision $reviewedPackageCommit -SqlcmdPath $odbc -EvidencePath 'C:\ShaleVerification\RetirementOperator-1881\Packet' -PacketPath (Join-Path $newRoot 'Packet')
if ($LASTEXITCODE -ne 0) { throw 'Preparation failed; preserve output and stop' }
# ONE separately opted-in loopback rehearsal, <=15 minutes, with dedicated LOCAL credentials:
& .\docs\sql\auth\permission-cutover\retirement-operator.ps1 -Phase Rehearse -ReviewedRevision $reviewedPackageCommit -SqlcmdPath $odbc -LocalPort 14333 -PacketPath (Join-Path $newRoot 'LocalRehearsal-ODBC-Values-Fix')
if ($LASTEXITCODE -ne 0) { throw 'Rehearsal failed; preserve evidence/resource; no automatic retry or cleanup' }
```

The existing `RetirementOperator-1881\Packet`, `LocalRehearsal`, `LocalRehearsal-ODBC`
and every other earlier failed/successful packet, output and nonce-owned database
are preserved unchanged. Prepare reads only the original three capture files from that Packet;
it regenerates all staged scripts/hashes in the NEW packet automatically. Do not copy/patch old
scripts, manifests or seals. If those original capture files are unavailable, stop for review.
Earlier Go and ODBC runs may have left nonce-owned databases: this sequence never adopts,
retries or cleans them. No wildcard cleanup, forced disconnect or production operation is authorized.

Expected output: `PASS retirement operator offline behavior on Desktop 5.1...`, then
`PASS real Windows ODBC quoted-switch reproduction and complete-variable timestamp reproduction/corrected help parsing on Desktop 5.1...`.
The real ODBC test parses complete rehearsal/apply/rollback/verify lists, all three
timestamps, both hashes, owner names/evidence references with spaces, and trailing
path backslashes. It retains raw argument/result evidence in the reported fresh
help-only test directory and requires the old unquoted-timestamp parser failure on
Windows. Help parsing does not certify executed SQL or value roundtrips.
Prepare prints `SQLCMD PREFLIGHT PASSED` and `PREPARED - NO CONNECTION - NOT AUTHORIZED`.
Only a successful full rehearsal prints `LOCAL SQLCMD REHEARSAL COMPLETED` and retains
`rehearsal-result.json` with `ExactSqlcmdChecks=PASSED_LOCAL_ONLY`; manual recovery and
production decisions remain PENDING. Unsupported clients print `SQLCMD PREFLIGHT REFUSED`
before credential prompts/resources. Child failure reports `ExitCode`, `DeadlineExceeded`
and its output prefix; retain `.sqlcmd.txt`, `.stdout.bin`, `.stderr.bin`, `.result.json`
and `local-owner.json`. Stop on any failure; never replay Apply or replace expected hashes.

Prepare asks for the existing approved Shale SQL endpoint and Brian's SQL operator
username. **It makes no connection and asks for no password.** It requires HEAD to equal
the reviewed commit, PR1880 ancestry and clean relevant paths; unrelated changes stay.
It copies the three original captures, all cutover scripts/instructions and captured-body
test resources; records SHA256, source revision, original BeforeHash and PENDING decisions;
and stages apply/verification/rollback together in commands.ps1.txt. Use a new directory
for a new packet. Never rebuild a packet to get new hashes after an apply attempt.

After the help-only preflight, successful output includes `PREPARED - NO CONNECTION - NOT AUTHORIZED`, then prints private
packet path, exact source revision, packet SHA256, original BeforeHash and
`Risk/package/window: PENDING`. Privately retain a second hash-verified copy, including
packet.json, packet-seal.txt, BeforeHash.txt, commands, source and captures. Hashes detect
accidental changes; they are not a signature or defense against a concurrent writer.

Complete only the missing bounded facts in **readiness.json**. Every row begins PENDING;
set PASSED only for evidence actually reviewed, with a real safe private record ID and
ScopeAndLimits. The runbook's required before-window matrix covers desktop password/
second exchange, checked/unchecked remembered startup/ordinary close/restart, remembered
rotation/replay/revoked/disabled/outage Retry, API password and remembered session lifecycle
and tenant/actor isolation, plus neighboring approved work. Existing disposable evidence
can cover destructive cases; do not repeat them on real accounts. The PC-000 report
covers only its stated subset. EvidenceScope lists missing cases honestly; no aggregate
PASSED based solely on those reports. Different rows may use one real compatibility record
if it explicitly covers them. No invented record IDs or negative-use/fleet claims.

IdentityAuthority includes actual auth/runtime and original-login/server/fixed/public/nested
review; KnownArtifacts binds supplied artifact findings to serving build/configuration and
existing known-work records. Set KnownCallerDisposition to
NO_IDENTIFIED_UNRESOLVED_CALLER only after bounded known-work review. Unknown schedules and
unavailable/external/dynamic clients remain explicit exclusions for risk acceptance; no
fleet collection. Identified active legacy callers or incompatible known work block go.

OperationalReadiness records existing baseline/detection channels, one-minute checks,
buffering/direct-SQL gaps, support route, known-work admission pause/drain/resume and
retry/reconciliation procedures. Brian must be present for SQL, verification, incident
lead and support. No fleet pause, session killing, guessed queue lifetimes or new telemetry.
FollowUp records Brian's daily review through at least seven calendar days after go,
each already-known next due run with owner/date (or an explicit truthful no-known-due-run
statement), and a dated reopened-risk remediation review. Support owns late-client incidents
afterward; unknown laptop-return dates do not block closure or become tested findings.

Run only the single Rehearse command above, once, on the existing dedicated local disposable
instance. This adds the missing bounded evidence; it is not a full suite/inventory cycle.

It accepts only tcp:127.0.0.1 and a dedicated unprivileged local port, asks explicit local
opt-in and synthetic-only admin credentials, creates ONE new random database, checks a
saved nonce on every database connection, and runs the unmodified controlled SQLCMD entry
points within a hard 15-minute local bound. Tests cover missing decision, deadline, drift, post-mutation failure/connection-close
atomicity, wrong rollback hash, apply/catalog checks, exact rollback and full baseline equality.
It restores captured CRLF bodies, retains full Unicode/console output and timings, then
removes only that exact nonce-owned database without forcing disconnects. On failure retain
resources and local-owner.json for exact manual review; no automatic retry or pattern cleanup.
No production data/copy/credential is accepted. It runs no Maven, Azure, Stage A/B or app tests.

Record **SQL-only** recovery/reverification time from rehearsal-result.json separately from
**manual** recovery. ManualRecovery must record the measured end-to-end recovery exercise
including password/remembered and ordinary-work checks, existing recovery/reconciliation
steps and a realistic operator sequence. Point at existing valid evidence if available;
otherwise this remains a factual gap. MeasuredRecoverySeconds must include that manual
sequence, not the reported 44.5663216-second full automated suite. Reserve at least 600
seconds, enlarged to measured recovery if larger. If it cannot fit within 30 minutes,
reject this window; no automatic extension or weakening of SQL guards.

**Final approval is interactive Apply, after this concrete packet and readiness are complete.**
Read commands.ps1.txt and use the staged runner from now on:

```powershell
$packet = 'C:\ShaleVerification\LegacyRetirement-Final'
$runner = Join-Path $packet 'source\docs\sql\auth\permission-cutover\retirement-operator.ps1'
# Set all three strings from the exact separately authorized UTC window, each yyyy-MM-ddTHH:mm:ssZ.
& $runner -Phase Apply -PacketPath $packet -WindowStartUtc $startUtc -WindowEndUtc $endUtc -RollbackStartByUtc $rollbackByUtc
# Exit 0/commit output does not establish application acceptance.
& $runner -Phase Verify -PacketPath $packet
# On incident, incomplete acceptance, lost visibility or deadline; remains available after expiry:
& $runner -Phase Rollback -PacketPath $packet
& $runner -Phase Verify -PacketPath $packet
```

Apply prints UTC/hard end/rollback cutoff; maximum window 30 minutes, reserve >=10 minutes
or measured recovery. Start includes the first admission pause, if any, or apply, whichever
is earlier. It requires Brian to explicitly accept unknown clients/invocation gaps,
irrecoverable effects and reopened capabilities; approve the exact packet and readiness SHA256 values; and separately authorize exact start/end/cutoff and preapprove guarded rollback.
Each decision needs its actual private record ID. No earlier approval is imported. The
SQL password is prompted interactively with Read-Host -AsSecureString; only SQLCMD's child
environment receives it, never command arguments, a global environment edit or a file.
Production uses encrypted certificate-verified transport; no trust-certificate bypass.

A durable attempt marker is written **before** connection. Apply is never replayed, even
after nonzero exit/partial output. SQLCMD full output, separate raw stdout/stderr and exit
record are retained. SQLCMD has stop-on-error, query/login timeouts, untruncated Unicode
output and visible deadline updates; reaching apply's cutoff terminates its process and
marks an uncertain outcome. This is not proof that a commit did/didn't occur. No background
watcher extends the window; Brian watches the printed clock after each phase returns.
Save the original emitted AfterHash independently with BeforeHash and complete output
before continuing. The runner persists AfterHash.txt only from the unique original commit
record, including when a subsequent native error makes exit nonzero. Missing/ambiguous
AfterHash stops guarded recovery: exact-state incident review is required, never a current
hash substituted as expected. Keep the original packet and outputs available offline.

Verify distinguishes (1) full saved digest/exact ACL/effective permission checks with no
legacy procedure execution, (2) safe designated PC-000/API password/remembered/ordinary-work
checks using EXISTING settings, and (3) unknown-client and buffered/direct-SQL monitoring
gaps. Record each actual outcome and scope, no forced replay/revocation/isolation on production.
Database-user impersonation checks do not replace reviewed actual connection identity.
Keep pre/post-check evidence privately; missing observations remain incomplete. Any unexpected
login/remembered failure, SQL 229, missed/delayed work, accounting/lock issue, tenant/audit
anomaly, unknown-client complaint or lost visibility triggers immediate guarded rollback.
Start by RollbackStartByUtc if acceptance is incomplete. Review each minute during trial
and the first staffed hour after resume, then daily for seven days and known next due runs.

Rollback always uses the saved original approval packet and BeforeHash/AfterHash with
controlled-rollback-legacy-login.sql. It preserves all original guard/transaction/definition/
dependency/ACL/grantor/SID checks and requires original full digest equality. Drift/refusal
is an incident for Brian; preserve outputs and review exact state. Never hand-replay grants,
replace expected hashes or retry apply. If an apply failed without a commit marker, Brian
may compare a separately authorized read-only legacy-retirement-baseline output to saved
BeforeHash to establish unchanged original catalogs; unequal state or missing AfterHash
requires incident review, not automatic recovery. Permission restoration cannot repair
lost failure counts, locks/last_login, lost/duplicate work, missing audits or external
effects, and **reopens the original vulnerable capabilities**. Reconcile from existing
approved business records, check idempotency before retries and separately approve data
corrections through established audit seams. No fabricated accounting/history.

Audit compatibility: security-catalog evidence stays in private change control. No application
sensitive-read/domain-mutation/audit schema/event behavior changes. Stage A/B, full original
route, RLS/Case/R4, broader audit permissions and live-update authorization remain separate gates.

Expected-refusal checks require the intended SQL Server `Msg <number>, Level 16`
header, a caught nonzero exit other than client exit 1, and no deadline expiry.
Exit 1 never establishes the intended SQL refusal. A parser/client failure before
SQL output exists reports the original transport failure, client stderr, and exact
retained `.stderr.bin`, `.result.json`, `.stdout.bin` and optional `.sqlcmd.txt`
paths. Preserve those records and the exact nonce-owned database; stop without retry.

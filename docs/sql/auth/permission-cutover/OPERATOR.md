# Brian's controlled retirement packet

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

From the clean reviewed package checkout root, with `$reviewedPackageCommit` set to the
full commit Brian actually reviewed:

```powershell
& .\docs\sql\auth\permission-cutover\retirement-operator.ps1 -Phase Prepare -ReviewedRevision $reviewedPackageCommit -EvidencePath 'C:\ShaleVerification\RetirementPacket-20261016-135011' -PacketPath 'C:\ShaleVerification\LegacyRetirement-Final'
```

Prepare asks for the existing approved Shale SQL endpoint and Brian's SQL operator
username. **It makes no connection and asks for no password.** It requires HEAD to equal
the reviewed commit, PR1880 ancestry and clean relevant paths; unrelated changes stay.
It copies the three original captures, all cutover scripts/instructions and captured-body
test resources; records SHA256, source revision, original BeforeHash and PENDING decisions;
and stages apply/verification/rollback together in commands.ps1.txt. Use a new directory
for a new packet. Never rebuild a packet to get new hashes after an apply attempt.

Successful output begins `PREPARED - NO CONNECTION - NOT AUTHORIZED`, then prints private
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

If exact SQLCMD recovery rehearsal is still missing, run this **once** in the reviewed
checkout against the existing dedicated local disposable SQL instance. This is a bounded
addition to the evidence already collected, not a new full suite/inventory cycle:

```powershell
& .\docs\sql\auth\permission-cutover\retirement-operator.ps1 -Phase Rehearse -ReviewedRevision $reviewedPackageCommit -LocalPort 14333 -PacketPath 'C:\ShaleVerification\LocalSqlcmdRecovery-Final'
```

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

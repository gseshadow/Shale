# October 16 operator evidence — attributed, not production acceptance

## Latest report and remaining acceptance

This update supersedes the older Windows/rehearsal-pending statements below for
their stated scope only. At reviewed revision
`041fa1ed0b21d64cce3599a01eaa3344ecf57331`, Brian reports native Windows PowerShell
5.1 offline runner regression, real Windows ODBC complete-variable/timestamp
regression, and full local SQLCMD rehearsal **PASSED**, including guarded refusals,
apply, rollback, catalog equality and cleanup. SQL-only recovery/reverification was
`0.743829` seconds. Manual application recovery is **NOT MEASURED**.

Current private packet:
`C:\ShaleVerification\RetirementOperator-1881\ValuesFix-20261016-161952-926\Packet`.
Local evidence:
`C:\ShaleVerification\RetirementOperator-1881\ValuesFix-20261016-161952-926\LocalRehearsal`.
These Windows files were unavailable to this implementation task and were not
inspected, edited or used to create readiness entries. The results above are
operator-attributed, not independently reproduced Windows observations.

Brian reports everyone uses desktop 1.0.136 and holds every operational/security
role. Accept this as his bounded estate/build attestation; no device inventory,
staff interruption, independent reviewer or new telemetry is required. It does not
prove dynamic/external invocation completeness. Identified incompatible callers
still block; unknown callers stay an explicit risk.

[Remaining acceptance](remaining-acceptance.md) maps exact test methods and limits
to readiness rows and supplies the finite recovery/final-window sequence. Source
coverage, old passing reports and new local test results are kept distinct. No
unperformed application check or decision has been promoted to PASSED.

Brian Downing is security owner, service/operations owner, SQL operator, verifier,
incident lead and support contact. He explicitly selected a single-person process.
**Independent human verification is absent.** No second person is required by this packet.

Risk acceptance, completed-package approval and exact UTC execution authorization
are **PENDING**. Earlier approvals/tests and merging a PR do not satisfy them.

The operator reports rehearsal checkout `0987b3f8e4afac5aa5a8b17a27a75743138512d1`;
native Windows PowerShell 5.1 connection-builder tests: three PASS, exit 0; disposable
SQL launcher: three scenarios PASS, exit 0. Full timed suite: `00:00:44.5663216`.
This includes fixture setup/refusals/roundtrips and application scenarios; it is
**not** isolated SQLCMD rollback/reverification or manual/production recovery time.
The Java fixture expands includes and executes JDBC batches, rolls back exceptions
and cleans temporary tables; it does not test native SQLCMD connection-close behavior.
It covers controlled missing decisions/route/window, drift/unsupported ACL/dependency,
postcheck atomicity, equal deltas, exact rollback and recovery after expiry.
`Rehearse` supplies the remaining bounded local SQLCMD path. It does not replace
manual recovery timing or representative serving API/desktop acceptance.

PC-000 reports: correct password succeeds, incorrect rejects; checked remembered
restore after Windows restart; earlier checked/unchecked and ordinary-close checks;
User directory/detail and one ordinary workflow passed. These establish only those
observations. API login/session lifecycle, replay/revoked/disabled/outage Retry,
isolation and live-update/job checks are not inferred. Existing disposable evidence
may cover destructive/replay cases as the owning runbook permits; retain exact scope.
Readiness stays PENDING until Brian reviews real evidence for each required row.

Reported actual Shale credential sessions resolve shale_app to login/user shale_app,
public; shale_runtime to login/user shale_runtime, public plus shale_runtime_role;
neither reported dbo/db_owner. Packaged/API usernames match. This does not enumerate
all server/fixed/nested authority or every override; Brian must finish that bounded
review from existing records rather than claiming packaged names prove authority.

Private originals on Brian's PC:
`C:\ShaleVerification\RetirementPacket-20261016-135011\` contains
inspect-legacy-login.txt, capture.txt, legacy-retirement-baseline.txt. Reported original
and repeated baseline:
`0x579CCE23B55F60F121F04B4D44E148D06247598A2EECC2EBF198555CE8AAC561`.
They were unavailable to this implementation task and were **not inspected here**.
Prepare copies and hashes them privately, validates the reported baseline record and
retains originals; copying/hashing does not itself review their contents.

Reported reviewed findings: three dbo-owned CALLER modules; exactly three original
shale_app EXECUTE G rows, grantor dbo; no recorded inbound SQL dependencies, signatures
or related triggers. Runtime database EXECUTE and role dbo-schema EXECUTE require the
six targeted DENYs. Direct Users SELECT and old remembered resolver remain. Capture
reports 284 permissions, 19 principals, three memberships, 77 security predicates.
These are attributed counts, not hard-coded production assertions or proof of nonuse.

Known SHA256 (operator supplied; not recomputed in this task):

| Artifact | Digest |
| --- | --- |
| Desktop 1.0.136 | 210ed09880529c230826e9796622cd49f03bef1d3718f4ef0af1107e8776c42a |
| Desktop data 1.0.136 | 93fbe2fa77230771b06e669f1224e60885c3ee4f39def1428ae92508007a997b |
| Deployed server | 12209f90f2462df09d50d8df825451018341236d4a5fc3e281274a0eb9468d78 |

Brian accepts or rejects attributed findings and ties them to actual representative
serving-build/configuration references. No fleet inventory or employee interruption:
estimated 20–30 devices, about four inaccessible laptops; older/different/dormant,
external/dynamic consumers and invocation completeness remain UNKNOWN. An identified
active incompatible caller or positive legacy use blocks until separately resolved.

API App Service Runtime Log stream is accessible. Reported nonexistent-page GET:
HTTP 500 at 20:00:05 UTC; later capture contains corresponding-time
NoResourceFoundException and 20:05:42 UTC heartbeat. Complete captured errors showed
sqlExceptions=[]. Delivery is established with observed buffering/delay. This is not
comprehensive authentication monitoring; desktop direct-SQL failures may never appear.
No new verbose/sensitive logging, instrumentation or telemetry project is authorized.

Known gaps before go: complete bounded identity/authority and serving-build review;
remaining runbook representative compatibility evidence (do not mark unperformed cases
passed); real detection baseline/thresholds and known-work stop/retry/reconciliation
procedures; exact SQLCMD rehearsal and measured manual recovery/reverification;
seven-day and known-next-due-run follow-up dates; explicit risk/package/window decisions.
Unknown-client completeness remains a risk for final acceptance, not a collection task.

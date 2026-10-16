# Authentication module permission cutover — 2026-10-15

**Latest preparation: [controlled-change contract](#controlled-change-contract--prepared-not-authorized--2026-10-16).** Production execution remains BLOCKED pending completed-package review, named risk approval and explicit window authorization. The original inventory/zero-invocation route remains available. No suitable entry-observation replacement is established.


## Controlled-change contract — PREPARED, NOT AUTHORIZED — 2026-10-16

This section supersedes #1879's proposed collection checklist **for the explicitly selected controlled
route only**. Stop fleet-wide collection: no staff interruption, device visits, reconciled device list,
per-device hashes, recall of the approximately four unavailable laptops, or new monitoring harness is
required. The estimated estate is 20–30 devices; its exact membership and older/different clients remain
UNKNOWN. Their unresolved status is permitted by this contract **only with named security-owner risk
approval and service/operations concurrence**. No complete inventory, complete drain or zero invocations
is asserted. A quiet trial cannot establish them. Preparation is authorized; risk acceptance and
production execution are not. Merging this package grants neither.

Fetched live `codex/latest` at `bd189ee66e854b036e9a15c510b2d09a00d2cea7`; GitHub metadata confirms
#1879 merged and local ancestry confirms that merge in this base. Branch
`codex/controlled-legacy-login-retirement` uses a separate worktree preserving unrelated work.
Reviewed AGENTS.md, architecture/codex-prompt-rules.md, this complete owning runbook, invocation-validation
README, architecture/tenancy-and-rls.md and the database-schema auth/Users/tenancy sections. Historical
assessments below retain their original evidence/authorization scope.

### Bounded evidence and acceptance

Keep a private packet keyed to this reviewed revision. Store only safe references in SQLCMD variables;
never commit private captures, binaries or credentials. A named reviewer records source, date, scope,
limits and acceptance for each attributed finding. Independent reinspection is optional if that reviewer
explicitly accepts the attribution; do not describe an absent attachment as independently verified.

| Evidence supplied in this conversation | Accepted scope / limit |
| --- | --- |
| PC-000 launcher and installed-version file select 1.0.136 | One representative installation; does not establish every device's installed version. |
| Prior assistant inspected uploaded desktop and data JARs: direct Users lookup plus Java bcrypt for password; remembered restore calls API; no literal sp_login_* references found | Attributed static findings for those artifacts; no complete dynamic-call or fleet compatibility proof. |
| Prior assistant inspected uploaded Azure server JAR and bundled libraries: reviewed password paths use direct Users/bcrypt; remembered lookup calls dbo.ResolveDesktopRememberCredential; no literal sp_login_* references found | Attributed artifact review; deployment parity and actual serving build still require an existing release/manifest reference or owner attestation. |
| API usernames reported shale_app/shale_runtime; packaged desktop usernames match, no overrides in checked PowerShell Process/User/Machine scopes | Configuration evidence only. Does not establish live connection identities, all override sources or server/login authority. |
| Desktop SHA-256 | `210ed09880529c230826e9796622cd49f03bef1d3718f4ef0af1107e8776c42a` |
| Desktop data SHA-256 | `93fbe2fa77230771b06e669f1224e60885c3ee4f39def1428ae92508007a997b` |
| Server SHA-256 | `12209f90f2462df09d50d8df825451018341236d4a5fc3e281274a0eb9468d78` |

These are operator-supplied digests, not recomputed here; no attachments were supplied to this task.
No additional device hashes are required. `KnownArtifactEvidence` identifies this ledger, the reviewer's
acceptance and the PC-000/API build/configuration references, plus bounded review of **existing** job,
integration and Function records already held by owners. Record unavailable records, unknown schedules,
dormant/external/dynamic consumers and unavailable laptops as explicit exclusions for risk acceptance;
do not require those gaps to be closed. Do not invent another inventory exercise. An identified active
legacy procedure caller, known incompatible artifact or positive legacy-use evidence **does** block this
route until separately reviewed migration/withdrawal; unknown clients must not be mislabeled compatible.
The prior Function DLL finding remains attributed, and its separate negotiate authorization finding stays OPEN.

For this route, legacy requirements 2 and 3 are replaced by bounded artifact review and representative
acceptance with a controlled trial, detection and exact rollback. Requirements 1 and 4 retain actual
identity/authority review and compatibility **for the reviewed representative desktop/API paths**;
unresolved older/different clients are excluded explicitly, subject to the same named risk decision.
Requirement 5's exclusive metadata control is unchanged. Review actual auth/runtime connections for
these representative mappings using the existing SELECT-only identity/token handoff, under separately
authorized read access: intended database, original/effective login, shale_app/shale_runtime separation,
server/fixed/public/nested authority and configuration precedence. Unexpected privilege or a different
mapping blocks the fixed-identity scripts. Packaged usernames alone cannot satisfy ReviewedIdentityMapping.
No Azure connection is authorized by this preparation.

`CompatibilityEvidence` records before/after results for one operator-controlled representative installed
1.0.136 desktop and the reviewed serving API, using designated test accounts and existing permitted error
summaries. No employee must stop work, supply credentials or run a test. Cover this compact matrix:

| Path | Required observable result before and during trial |
| --- | --- |
| Desktop password | Correct password signs in through desktop and second API credential exchange; wrong password rejects safely. Reopen after ordinary close and restart the representative test machine. |
| Desktop remembered | Checked startup restores through API; unchecked startup does not auto-restore. Rotation succeeds, replay/revoked/disabled credentials reject, and outage Retry recovers without fallback to legacy procedures. |
| API password | Login success and rejection; /me, refresh/logout, user switching and tenant/actor isolation remain correct. |
| API remembered | Existing remembered lookup/restore/rotation succeeds through old resolver, revoked/disabled/replayed credentials reject, and restored principal/session context is correct. |
| Neighboring work | Existing approved User directory/detail and a representative job/integration/live-update reconnect complete with expected result and existing audit behavior. Record untested work as residual risk; an identified failure blocks resume. |

Use existing disposable compatibility evidence for destructive/replay/revocation cases and repeat safe
representative checks in the authorized window. Do not alter real staff credentials/accounts or call
sp_login_* against Azure to manufacture evidence. Direct Users SELECT and the old resolver remain
available for 1.0.136; this route does not authorize Stage B. The reviewed direct/bcrypt paths do not
implement legacy failure/lock/last_login accounting. Accepting that bounded fact is not approval to
silently discard an identified caller's accounting policy; any such caller needs separate disposition.

### Explicit executable selection and preserved guards

The original `retire-legacy-login.sql` and `rollback-legacy-login.sql` still use the unchanged
`legacy-retirement-approval.sql`: CallerInventoryApproved, LegacyCallersDrained and their original evidence
retain their complete-inventory/zero-invocation meanings. Existing invocations need no new variables.
Never set LegacyCallersDrained=CONFIRMED to represent this alternative.

The alternative entry points are `controlled-retire-legacy-login.sql` and
`controlled-rollback-legacy-login.sql`. They require `LegacyAcceptanceRoute=CONTROLLED_CHANGE` and the
separate `controlled-legacy-approval.sql` contract. There is no automatic fallback, default route or
blanket approval from #1879. Missing SQLCMD substitutions fail before mutation; wrong/blank decisions
throw 58360. The common apply/rollback bodies are extracted verbatim from the original scripts, so both
routes execute the identical permission/hash/transaction logic. Body files are includes, not operator
entry points. Original captures and all context/fingerprint/effective checks remain untouched.

In the private packet, record real people for SecurityOwner and ServiceOwner (both capacities if one
person serves both), SQL operator, independent verifier, incident lead and support/on-call contact.
RiskApprovalEvidence must contain the security owner's explicit approval and service owner's concurrence
for unknown/older/offline-client failures, incomplete invocation/drain knowledge, irrecoverable accounting
and operations, the disruption bound and reopened rollback exposure. PackageReviewEvidence identifies
completed implementation review; WindowAuthorizationEvidence identifies a separately signed exact window
and pre-approved rollback. OperationalReadinessEvidence names detection channels, baseline, thresholds,
known in-flight/retry handling, graceful stop/resume for affected known work, support escalation and
reconciliation owners. It acknowledges that unknown sessions/work may be in flight; no fleet/session
purge or proof of global quiescence is required. Identified required legacy work must be resolved first.
RollbackRehearsalEvidence records a separately authorized disposable rehearsal of these exact entry points,
missing acknowledgements/deadline refusal, drift/ACL refusal, postcheck-failure atomicity, exact equality
and measured rollback/reverification time. Static checks and historical passes are not that rehearsal.

All exact permission deltas remain: three auth EXECUTE REVOKEs AS dbo; six runtime/principal-role object
EXECUTE DENYs AS dbo. Baseline/drift/SID/owner/module/SET/parameter/dependency/synonym/trigger checks,
G AS dbo state/grantor restrictions, full expected delta, transaction atomicity and original BeforeHash /
AfterHash rollback equality are unchanged. No CASCADE, grantor substitution or broad grant replay.
Security preflight/58307, Stage A installation, Stage B full inventory/old-read drain, installed compatibility,
Case/R4 and all RLS prerequisites remain separate unchanged gates. No new module authority or audit permission
acceptance is granted. Actual username mappings and unrelated security findings remain open.

### Short operator sequence — future authorized window only

1. **Review the bounded packet.** Accept or reject the attributed artifacts; bind the representative
   desktop/API build and actual identity evidence, pre-window matrix and existing known-work disposition.
   Obtain named risk approval, completed-package review and separate window authorization. Do not call
   employees or wait for offline laptops. First next action is packet review, not fleet collection.
2. **Rehearse exact recovery.** Retain the unchanged original captures. In a separately authorized
   disposable environment rehearse apply/rollback and refusals above. Require full catalog equality and
   measure recovery including representative checks. Keep Stage A/B and unrelated DDL/security edits
   excluded throughout the trial and its recovery commitment. No rehearsal or SQL mutation occurred here.
3. **Set the bound and staff operators.** Maximum planned disruption is **30 minutes** from the first
   admission pause, if any, or permission apply, whichever is earlier. Record exact UTC start/end and
   RollbackStartByUtc, reserving the measured rollback/reverification duration, **at least 10 minutes**.
   If rehearsal cannot fit, reject this window. This is an owner-approved budget, not a guarantee for
   unknown callers or failed recovery. Use only operator/test accounts; no fleet-wide pause or session
   termination. Known affected work is gracefully held/drained using existing controls where needed.
4. **Baseline and go/no-go.** Under separately authorized access, serialize catalog control, run existing
   `legacy-retirement-baseline.sql` with -b -V 16 -y 0 -Y 0 -u; independently retain BeforeHash and its
   complete reviewed catalog evidence. Confirm actual identities, all approvals, healthy detection and
   rollback artifacts. Stop for drift, known dependency, missing evidence or insufficient time reserve.
5. **Explicit trial.** Invoke the alternative with the exact reviewed packet; retain complete private
   output and independently save AfterHash. Check actual effective legacy EXECUTE=0 for auth/runtime
   without invoking those modules; verify the exact nine-row delta and retained Users/old-resolver access.
   Run the safe desktop/API password/remembered checks above. Nonzero SQLCMD means stop/close and verify
   transaction rollback/catalog equality. Do not run installation or Stage A/B as part of this window.
6. **Detect and decide.** Operator/verifier review existing approved login success/rejection, permission
   error and availability summaries plus representative outcomes **each minute** during trial and the
   first staffed hour after resume. Any unexpected login/remembered failure, SQL 229, missing/delayed
   expected work, incorrect lock/accounting, tenant/audit anomaly, unknown-client complaint or lost
   visibility triggers rollback immediately; otherwise start rollback by RollbackStartByUtc if acceptance
   is incomplete. Incident lead holds affected admission/work with existing controls and directs the SQL
   operator/verifier; no extra grants, retry loops, guard bypass or silent window extension.
7. **Exact rollback or bounded follow-up.** Use controlled-rollback with original packet and separate
   LegacyRollbackApproval/compatibility acknowledgement, saved BeforeHash/AfterHash. Remove only six DENYs,
   restore only three original G AS dbo grants and require full original digest before commit. Repeat
   representative checks. Expired window does not disable recovery. If drift prevents rollback, stop for
   exact-state incident review and escalate to security/service owners; never replace expected hashes.
   An overrun is an incident. If accepted, assign named daily review for **seven calendar days** plus
   each already-known next due run (owner/date fixed before go); support/on-call remains the late-client
   route afterward. Unknown offline-return dates do not block closure and are not declared tested.

Future invocation shape (trusted fixed literals, safe IDs/names and ISO UTC times from the signed packet;
no approved values or connection details are supplied here). Populate `$controlledApproval` with **all**:

```text
ExpectedDatabase, ReviewedIdentityMapping, LegacyRetirementApproval, RuntimeLegacyDisposition,
ExclusiveChangeWindow, LegacyAcceptanceRoute, CompletedPackageReview, ControlledWindowAuthorization,
UnknownClientRisk, IrrecoverableEffectsRisk, ReopenedCapabilityRisk, RepresentativeChecks,
RollbackRehearsal, SecurityOwner, ServiceOwner, RiskApprovalEvidence, PackageReviewEvidence,
WindowAuthorizationEvidence, KnownArtifactEvidence, CompatibilityEvidence,
OperationalReadinessEvidence, RollbackRehearsalEvidence
```

The required literals are defined in controlled-legacy-approval.sql; set them only from real decisions.
Use approved local paths and the existing approved SQLCMD connection array. UTC timestamps use
`yyyy-MM-ddTHH:mm:ss` without suffix. The apply guard rejects invalid times, >30 minutes, <10-minute
reserve or a start outside trial time (58361); operators must also enforce the measured larger reserve
and the deadline throughout the operation. It is a pre-apply guard, not a timer interrupting SQL.

```powershell
$controlledApply = $controlledApproval + @("LegacyBeforeHash=$legacyBeforeHash", "WindowStartUtc=$windowStartUtc", "WindowEndUtc=$windowEndUtc", "RollbackStartByUtc=$rollbackStartByUtc")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql -v @controlledApply -o $privateControlledApplyOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: verify transaction rollback and exact catalogs' }
# Independently save AfterHash; no Stage A/B or unrelated catalog edits.
$controlledRollback = $controlledApproval + @('LegacyRollbackApproval=APPROVED', 'RollbackCompatibilityReviewed=CONFIRMED', "LegacyBeforeHash=$legacyBeforeHash", "LegacyAfterHash=$legacyAfterHash")
# Only on the separately approved incident/rollback decision:
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql -v @controlledRollback -o $privateControlledRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: exact recovery refused; escalate, never replay grants manually' }
```

A returning older/unknown client failure is an incident even after the seven-day review. Support captures
only approved timestamp/build reference, symptom and permitted error code, holds affected work and pages
the named incident lead; no broad staff survey or per-device hash collection. Compare to the representative
path, preserve existing business evidence and evaluate exact guarded rollback while still pre-Stage-A.
If later Stage A/B or unrelated metadata exists, rollback may refuse: use the documented separately
reviewed Stage B then Stage A rollback/drain order first; the 30-minute recovery budget does not carry
forward automatically. Approve a new recovery commitment before any later stage. Never force an unknown
client to use an unreviewed hash fallback or waive tenant isolation to recover service.

Permissions are reversible within the exact supported state. **Missed failure increments, stale locks,
last_login gaps, lost/duplicate work, partial external effects and missing audit events are not repaired
by rollback.** The incident owner and consumer/accounting owner reconcile from existing approved business
evidence, assess idempotency before retry and obtain separate approval for corrections using established
audit seams; do not fabricate history. Restored access also reopens hash disclosure/offline attack,
unauthenticated lock/success manipulation and inherited runtime capability. These risks need explicit
approval, a dated remediation review and incident ownership even after service is restored.

Audit compatibility: no application sensitive read/domain mutation or audit schema/event changes.
The existing PHI/entity-action/session-security transactions remain authoritative. This security-catalog
package and approvals use private operator change control; no raw captures or ad-hoc app audit events.

### Exact remaining facts and one approval decision

Still required: named security/service owners and operator/verifier/incident/support contacts; reviewer
acceptance of the attributed artifact findings and representative deployed-build/configuration references;
actual auth/runtime identity and authority evidence for representative desktop/API mappings; before/after
matrix results; disposition of any identified active legacy caller; original complete captures and
independently retained BeforeHash/AfterHash at the later window; measured exact rollback/refusal rehearsal;
existing detection channels/baselines, known-work stop/resume/reconciliation owners; seven-day and known-due-run
follow-up owners/dates; completed-package review and exact signed UTC window with reserve/rollback decision.
No fleet reconciliation, unavailable laptop inspection or zero-invocation evidence is a remaining fact
under this alternative. Unknown clients remain recorded risks, not accepted compatibility findings.

**One concrete approval decision:** the named security owner, with named service/operations concurrence,
approves or rejects this CONTROLLED_CHANGE contract for the reviewed artifacts and representative paths,
explicitly accepting unresolved older/unknown/offline clients, incomplete invocation knowledge,
irrecoverable effects, the 30-minute disruption budget and rollback's reopened capabilities. Approval
must reference this completed package and private evidence; it is currently **PENDING**. Execution stays
BLOCKED until package review and separate specific window authorization are also recorded. Rejecting or
not granting it leaves the original gate in force. No production execution is requested by this PR.

## Proposed maintenance-window acceptance policy — NOT APPROVED — 2026-10-16

Historical #1879 proposal. Its fleet-reconciliation checklist is superseded for the explicitly selected
controlled route by the contract above; it is not an additional collection requirement for that route.

**Proposal only. The existing retirement requirements remain in force.** This planning PR neither
approves nor activates this alternative. Retirement, installation and production monitoring remain
BLOCKED. No executable permission, observation, preflight, rollback or harness file changes here.

Fetched live `codex/latest` at `9e23c1b611e40dac5ad6bf22ea80b9af9192178c`; GitHub confirms #1878
merged and the fetched commit is its merge commit. Separate task branch:
`codex/legacy-login-maintenance-acceptance`, in a separate worktree preserving the original checkout.
Read AGENTS.md, architecture/codex-prompt-rules.md, this full runbook and invocation-validation README;
reviewed architecture/database-schema.md auth/Users/tenancy sections and architecture/tenancy-and-rls.md.

**New operator-reported verification:** the #1878 Windows offline regression passed all three tests,
exit 0, including native PowerShell 5.1 default and explicit HarnessPath forms. This supersedes the
pending native launcher retest in the dated #1878 record below for that reported run only. It was not
executed independently here; no exact checkout/artifact digest or additional tool version was supplied.
It establishes neither installed-desktop compatibility nor Azure workload/privacy/coverage validation.
Earlier failure captures and historical results remain intact; no new Discover/Setup/Workload is needed.

### Risk and the exact policy decision

Retaining these capabilities permits hash disclosure/offline password attack and unauthenticated
failure/lock/success/last-login manipulation. The captured email-only match is not tenant-qualified,
permits NULL is_deleted and ignores removed status; duplicate emails can affect several tenants.
get_hash hides ambiguity with unordered TOP (1); mark_failure can lock matching accounts and
mark_success can clear locks without credential proof. Dbo ownership chaining defeats direct table
DML/read fences; inherited runtime EXECUTE exposes the same capabilities. Retention also keeps the
unchanged Stage A security preflight blocked. Retirement reduces these particular capabilities, while
broader shared credentials, runtime CRUD/EXECUTE and audit permissions remain separate security debt.

Retiring an undiscovered dependent caller can break password login or background work, miss failure
increments, leave stale locks or last_login, and lose operations even if the caller swallows the SQL
error. An offline desktop, infrequent job or external/dynamic caller can return after a quiet window.
Known-consumer artifact review and successful checks cannot prove that no such caller exists.

The proposed replacement is specifically for items **2 and 3** under
[Exact permission delta and approval boundaries](#exact-permission-delta-and-approval-boundaries):

- Item 2 currently requires a **complete caller inventory**, with required callers migrated first.
  Replace its universal completeness claim with a reconciled, bounded inventory of the named estate
  and its declared exclusions/unknowns, signed by its accountable owners. Every identified required
  caller must still be compatibly migrated or explicitly withdrawn before retirement. A known missing
  artifact, owner or configuration is a blocker, not an unknown-caller exception.
- Item 3 currently requires an observation/drain record with **zero remaining invocations** and says a
  missing observation source blocks retirement pending a telemetry plan. Replace that negative-proof
  component with verified known-consumer artifacts/configuration, documented operational drain and
  a controlled retirement trial with representative acceptance, symptom detection and exact rollback.
  Keep start/end times, actual schedule/lifetime bounds, owner signoffs and connection/session drain
  for known consumers. Absence in cache/Query Store remains UNKNOWN; no silence becomes proof.

Items 1, 4 and 5 remain required: separate security-owner retirement/reopened-risk approval, actual
identities and artifact compatibility/accounting disposition, and exclusive serialized metadata control.
The earlier five-requirement mapping remains an assessment under the existing policy, not evidence that
this proposed replacement is approved. No supported privacy-safe complete invocation source is
established; this alternative does not depend on finding one or require proof about every conceivable caller.

**Required explicit approval:** the designated Shale security owner must accept the policy replacement,
residual unknown-caller/security risks and exact rollback exposure in a recorded decision. The accountable
Shale service/operations owner must co-approve the disruption limit, staffing and recovery/reconciliation
plan; desktop, API and job/integration owners sign their inventory, compatibility and drain evidence.
If one person holds several roles, record each capacity. Named people and decision IDs are currently
missing and must be supplied in checklist item 5. Repository review or merging this proposal alone must
not be interpreted as those approvals. A later separately reviewed implementation and a specific
maintenance-window go/no-go authorization are also required before any permission operation.

### Finite operator checklist and missing-fact collection

All entries are OPEN unless explicitly attributed above. Keep a private packet with source revision,
collection time, owner, digest/reference, finding and gap/disposition for each row. Collection described
here is future work under existing or separately obtained read/operation authorization, not an Azure
instruction for this task. Inspect existing records first; do not collect passwords, tokens, application
hashes, emails, arguments, raw SQL or new event payloads. Stop at the specified estate boundary and
record residual exclusions rather than expanding an endless search.

| Order / missing fact | Exact bounded collection action | Completion criterion |
| --- | --- | --- |
| 1 — installed desktop versions and artifacts | Desktop owner reconciles the current managed-device/installer distribution list with the support roster, including known offline devices, 1.0.136 and older supported installations. For each distinct installed build obtain its installed executable/JAR/MSI digest and packaged auth implementation; privately resolve configuration precedence and target/principal references. Record the dated estate list and exceptions. | Every listed installation maps to a reviewed build/configuration or an owner-attested withdrawal preventing reconnect; counts reconcile across those two lists. Missing known/offline installations block. Unknown devices outside the declared estate remain an explicit residual risk. Release source alone is insufficient. |
| 2 — deployed API identity/build | API owner obtains the active deployment manifest and artifact digest for each serving slot/instance from existing release records; compares packaged auth code and effective configuration precedence to that artifact. Retains the existing actual auth/runtime identity/token handoff, or separately authorizes that bounded SELECT-only handoff from the Case runbook for each distinct mapping. | All serving instances map to exact artifacts and intended database, distinct shale_app/shale_runtime identities, original-login/server and nested/public/fixed-token review; no unexplained instance/override or guessed identity. Record web-to-API routing/build. |
| 3 — remaining named callers and inventory boundary | Service owners reconcile existing scheduler/job definitions (enabled, disabled, monthly/quarterly), integration/admin-script register and DB dependency/synonym evidence for these three exact modules. Include both live-update functions, their deployed artifact/configuration provenance, and external/dynamic/wrapper paths. Review only those registered artifacts and their transitive call/configuration paths; retain the prior Function DLL report as attributed evidence until provenance is verified. | A per-consumer/per-procedure register covers this named estate with owner, artifact, effective identity/target reference, call mode, last/next activity, timezone/interval and retire/migrate/no-call disposition. Every discovered required caller has accepted migration/withdrawal evidence; unresolved known paths block. Owners sign the boundary/exclusions, not universal nonuse. |
| 4 — compatibility and accounting behavior | Owners compare each distinct artifact/configuration path in rows 1–3 to the captured procedure semantics and run the representative matrix below on an approved disposable environment, then the separately authorized installed-artifact acceptance. Retain pass/fail and artifact references only. | All expected outcomes pass under actual configured principals; any active mark_failure/mark_success consumer has a separately reviewed compatible migration or withdrawal and explicit security-owner accounting disposition. V1 lookup alone is not an accounting replacement. No direct runtime hash-read fallback. |
| 5 — accountable approvals, operational bounds and detection | Change owner names security/service/consumer owners, SQL operator, independent verifier, incident lead and support contact. Obtain actual task/retry/connection/pool/in-flight maxima and due-run dates from row 3 configurations. Review existing permitted status/error counters, support path and acceptance outcomes; complete the window worksheet below. | Signed packet supplies names, evidence/approval IDs, UTC start/end, per-consumer drain deadlines, exact detection channel/cadence, measured rehearsal times, rollback deadline and follow-up due runs. No unbounded lifetime or unstaffed interval. If existing safe symptoms cannot detect the representative failures, stop; no new instrumentation is assumed. |
| 6 — exact rollback feasibility and baseline | SQL operator/verifier review retained original schema/prior-state/legacy captures and existing baseline/apply/rollback scripts. In an already approved disposable environment rehearse the exact nine-row delta and rollback, retain timing and full equality results. Before a later live attempt separately authorize the existing bounded baseline and actual-identity checks, serialized against catalog editing. | Original captures remain untouched; independently saved BeforeHash and subsequently emitted AfterHash are retained with complete ACL/grantor/state/SID/owner/module evidence. Captured G AS dbo profile and exact-state guards pass; Stage A/B are absent and no unrelated drift prevents rollback. Rehearsal fits the approved bound. No inline current hash used as expected state. |
| 7 — drain and final go/no-go | In the separately authorized window consumer owners pause admission/schedules using their approved procedures, complete queued/retry/in-flight work and close old connections/pools gracefully. Verify their stopped/empty status using existing controls and actual configured maxima; no session killing is assumed. Review hashes, approvals, staffing and checklist again. | Each known old path is stopped/withdrawn or compatibly replaced, with timestamped owner evidence and no pending work. Unknown/unbounded drain, failed due run or positive legacy-use evidence stops. Only the incident lead and security owner can record go; never set existing acknowledgement variables from this proposal. |
| 8 — controlled trial, follow-up and closure | Only after policy/implementation/window approval, apply the unchanged exact delta with SQLCMD stop-on-error, independently save AfterHash, verify catalog/effective results without invoking legacy procedures, and execute the matrix. Owners review each registered next due run and planned offline return; record each outcome and investigate complaints. | Every check passes before resume, all due-run/return dates have finite owners/deadlines and all incidents/reconciliation are closed before final retirement acceptance. A failed or missed run stays open or triggers rollback; late unknown callers remain incident risks after closure. No zero-invocation claim. |

Representative matrix: each distinct supported installed desktop build/configuration must exercise
password/bcrypt success and rejection, second API credential exchange, checked/unchecked remembered
startup, ordinary close/Windows restart, rotation/replay/outage Retry, disabled/revoked rejection and
DPAPI-unavailable behavior. API checks cover login, enrollment, /me, issue/refresh/logout, user switching
and pooled tenant/actor isolation. Verify User directory/Team/detail/assignment/history and approved
create/reset/revocation writes and audit behavior. Check both live-update negotiation/reconnect and
representative job/integration due work. For migrated accounting callers verify captured threshold,
expiry/default/NULL, concurrent failure/success and last-login outcomes in disposable tests; no legacy
procedure invocation against Azure to manufacture evidence. Retain 1.0.136 direct auth Users reads and
old remembered resolver until Stage B's independently satisfied broader drain. Source tests and the
new offline Windows pass do not replace these installed-artifact checks.

### Window limits, symptoms and rollback decision

Proposed ceiling for explicit owner acceptance: **30 minutes of planned login/dependent-work
unavailability**, starting when admission is paused, including graceful drain, trial, rollback and
verification. This is a proposed budget, not a measured recovery guarantee. The packet must specify
UTC start, hard end, and a rollback reserve measured in item 6 (at least 10 minutes). Do not apply if
drain/checks leave less than that reserve. Begin rollback no later than hard end minus the reserve;
never extend the window silently. If rehearsal cannot fit, reject this window proposal and return for
separate review of a concrete larger budget. An unplanned recovery overrun is an incident, not a claim
that disruption stayed within the ceiling. Missed accounting/data effects can outlast service recovery.

Keep the SQL operator, independent catalog verifier and incident lead/service owner present throughout;
desktop/API/job owners and support must be reachable immediately. Security owner must be available for
the pre-agreed rollback decision, including acceptance of reopened capabilities. No staffed coverage,
no go. Keep this window pre-Stage-A and prohibit Stage A/B or unrelated DDL/security changes until the
trial decision and rollback exposure review finish, so the saved legacy AfterHash remains usable.

During the trial check existing privacy-approved login/error/availability summaries and queue/task
completion at least once per minute, with a support route for swallowed errors or user symptoms.
Compare to a same-path pre-window baseline and the matrix's expected outcomes; record timestamps,
pass/fail and permitted error codes only. Do not enable SQL text, principal/argument tracing or any
new monitoring harness. Before resume require every matrix path to pass. Staff the first hour after
resume with the same one-minute checks; then assign a named daily review through the latest registered
due-run/offline-return date. Dates and responsible owners must be fixed before go, with escalation
coverage in between; unknown future callers can still fail after that finite review ends.

Stop before apply for any missing fact/signoff above, known unreviewed dependency/accounting path,
positive use without disposition, drift/unsupported grantor/state, incomplete captures, failed identity
mapping, unexpected privilege, absent rollback artifacts, failed acceptance, unbounded drain, unavailable
staff/detection or exhausted time budget. A nonzero SQLCMD result stops immediately: close its connection,
retain output and verify transaction rollback/catalog equality before considering any next step.

After apply, **any** unexpected authentication/remembered failure, permission error (including 229),
missing/delayed job result, incorrect accounting/lock state, tenant/audit anomaly, unexpected ACL delta,
unknown-caller report, lost symptom visibility or insufficient remaining rollback time triggers an
incident and pauses affected admission/work. The incident lead invokes the pre-approved guarded rollback
with the SQL operator/verifier; no repeated trial or extra grants to mask the symptom. If drift prevents
exact rollback, stop for exact-state incident review, maintain the outage notice and escalate to security
and service owners. Never overwrite expected hashes or bypass a guard to meet the clock.

Rollback is exactly the existing `rollback-legacy-login.sql`: validate the independently saved
BeforeHash/AfterHash and complete expected state, remove only the six new runtime/role DENYs AS dbo,
restore only the three original auth EXECUTE G rows AS dbo without GRANT OPTION, and require the entire
original catalog digest before commit. W/D/R, other grantors, changed SIDs/owners/definitions/dependencies
or extra ACLs remain rejected. No CASCADE, broad grant script, grantor substitution or guessed replay.
If Stage A/B have subsequently been authorized and installed, legacy rollback still refuses: drain their
consumers and use their guarded rollbacks in the documented order first; do not promise the same 30-minute
recovery in that later state. Review that risk before permitting any later stage.

Restoring grants reopens hash disclosure, unauthenticated lock/accounting manipulation and inherited
runtime access. The security owner must explicitly accept this temporary exposure and assign a dated
remediation review. It does **not** replay lost operations, restore missed failure counts/last_login,
repair lock decisions, reverse partial external effects or reconstruct missing audit events. Consumer
owners must reconcile each affected operation from existing approved business evidence, assess retries
for duplication/idempotency, and separately approve any data correction. Never fabricate history or
blindly replay failed work; uncertain accounting/security outcomes remain incidents after grants return.

### Preserved safeguards and later implementation

The exact three auth REVOKEs and six runtime/role DENYs, captured definitions/SET flags/parameters,
original captures, independent baseline hashes, grantors/states/SIDs, catalog/effective drift rejection,
transaction atomicity and all rollback guards are unchanged. Security preflight/58307 is unchanged;
retirement does not approve installation. Stage A and Stage B keep separate approvals, module/fence
identities, installed compatibility and broader fleet/job/read-drain gates. No Users/auditLog/Statuses
FILTER, shared TenantFilter function, Case harness, original schema export or R4 acceptance changes.
RLS prerequisites, actual-login authority review and separate audit/live-update security findings stay open.

If the policy is approved, a later separately reviewable change must define an explicit alternative
acceptance/evidence contract in operator instructions and acknowledgement validation, so
LegacyCallersDrained=CONFIRMED never silently means unproved zero invocations. Update affected tests and
rehearse missing approval/evidence, unknown known-consumer facts, deadline failure, drift refusal and exact
rollback without weakening permission/preflight guards. Supply a reviewed window worksheet, artifact
matrix, graceful-drain/resume procedures, reconciliation ownership and rollback timing from real evidence.
No new monitoring harness is needed or proposed. Existing scripts must not be used under this alternative
until that contract is reviewed; this planning PR leaves all executable scripts byte-for-byte unchanged.

Audit compatibility: this documentation changes no application sensitive read, mutation or schema/event.
Existing PHI/entity-action/session-security transaction seams remain authoritative. Private operator
change control holds approvals and evidence; later business reconciliation must use the established
audit framework, with any unrepresentable gap explicitly reviewed rather than ad-hoc audit records.

Planning-PR validation (Linux): pre-edit searches reviewed the existing observation runbook assertions,
invocation privacy/coverage tests, approval validation and exact apply/rollback scripts; no production
symbol or existing test contract changes. `python3 -m unittest discover -s build/auth-verification
-p 'test_*.py' -v` passed 30 tests with two native-Windows skips. The committed base-to-head selector
(`python3 build/test-selection/select_tests.py --base 9e23c1b611e40dac5ad6bf22ea80b9af9192178c
--head HEAD --format markdown --plan-output work/selection.json`) selected no affected Maven area for
this documentation-only change and no full-suite escalation. Its critical command was run with the
existing configured wrapper, `/workspace/.tools/bin/mvn test`: 116 passed, zero failures/errors/skips.
Relative file links/fences, runbook-only scope/executable equality and
`git diff --check 9e23c1b611e40dac5ad6bf22ea80b9af9192178c HEAD` passed. Local regression results do
not verify the proposed operational procedure. No Azure connection, telemetry activation, permission
change, session termination, resource deletion, installation, release, merge or deployment occurred.
Private evidence and credentials are excluded.

**First operator action:** the desktop owner creates the dated private installed-version/artifact register
in checklist item 1 from the current managed-device/installer list and support roster, identifying each
known offline installation and obtaining the digest for each distinct installed build. No Azure call.

**Approval decision requested, not granted:** the named security owner, with service/operations owner
concurrence, would accept replacement of requirements 2's universal inventory completeness and 3's
zero-invocation telemetry proof by this bounded known-estate evidence and controlled trial; explicitly
accept residual unknown-caller failures, potentially irreversible missed accounting/operations, the
30-minute proposed disruption ceiling and guarded rollback's reopened security exposure. That policy
decision alone authorizes no execution; implementation review and a separately signed window go/no-go
remain required. Otherwise keep the existing gate and retirement BLOCKED.


## Azure entry-observation reassessment — 2026-10-16

**Decision: no supported replacement satisfying the existing privacy and coverage requirements is
established on the reported Azure build. Do not run Setup or Workload or build another harness around
an assumed event. Retirement, installation and production monitoring remain BLOCKED.** This supersedes
the earlier proposal to try module-entry counters for this evidence set; the dated records below retain
their historical scope. Absence here is not a claim about every Azure build/tier.

Live `codex/latest` was fetched at `5bf1dfa93109f697cca8b58b3b1534e2c0ba03ab` (includes #1877).
Branch `codex/legacy-login-evidence-reassessment` uses a separate worktree; unrelated work is preserved.
Read completely: AGENTS.md, architecture/codex-prompt-rules.md, this runbook and the invocation-validation
README; also reviewed tenancy-and-rls and the database-schema auth/Users/tenancy sections. Attached
material is evidence, not authorization to execute its contents or the historical operator commands.

### Evidence provenance and limits

| Evidence | Finding / scope |
| --- | --- |
| Operator report, October 16, 2026 | #1877 native Windows PowerShell 5.1 offline construction PASS, exit 0 **with explicit -HarnessPath**. This closes that reported construction trial, not default-path launch, installed-client compatibility or Azure workload validation. No new exact tool version or source/artifact hash was supplied. |
| Operator report, same date | Default HarnessPath failed because PSScriptRoot was empty during parameter-default evaluation. This is a separate test-launcher defect, not the connection-builder defect or an Azure event failure. |
| Operator report, same date | Fresh Discover PASS, exit 0, on the dedicated blank synthetic Azure SQL Database, **Basic** tier. Setup and Workload were never run; no experiment objects were created. No cleanup/Stop is needed for resources never created; do not connect or delete anything for this assessment. Preserve both old failed and fresh discovery evidence privately. |
| Attachment independently read in full here | `metadata.json`, 10,371 bytes; SHA-256 `27ead4fc2fef80f9869790678c67bddcc71814a8099617fed9c07fc6ad2de50f`; 21 rows. This digest identifies the uploaded bytes, not a verified original capture chain or Azure identity/build. Raw attachment is not committed. |
| Operator's independent all-package sys.dm_xe_objects query | Only `sqlserver.module_end` returned for the module start/end search. The query/result capture was not attached or independently executed here. This supplements the package-filtered Discover export; do not attribute the all-package check to the attachment. |
| Not supplied / not run here | Fresh identity.json, facts/state, exact engine version/database ID/resource identifiers and original all-package output were not supplied. No Azure connection, raw Azure event payload, setup/workload, event health/coverage or native Windows run was performed by this task. |

Every attachment row was reviewed: package0.event_counter has one target row without columns;
package0.ring_buffer has three customizable uint32 options (max_memory=0, occurrence_number=0,
max_events_limit=1000). sqlserver.database_id appears once as action and once as predicate source.
The remaining 15 rows describe **module_end only**: four readonly UUID/VERSION/CHANNEL/KEYWORD fields;
customizable boolean collect_statement=false (description says statement is omitted by default);
and ten data fields source_database_id, object_id, duration, row_count, line_number, offset,
offset_end, object_type, object_name and statement. The last is intrinsic Unicode SQL text metadata.
This is metadata, not an emitted-payload privacy pass. No module_start row or alternate entry event is
in the attachment. Basic is a reported resource fact; it does not establish that Basic caused the absence
or that a tier change would supply the event. No tier/resource change is proposed.

### Supported-mechanism review before any new experiment

Official Microsoft documentation read October 16, 2026; conclusions about suitability below are the
assessment of these documented semantics against the actual evidence, not Microsoft's certification
of this deployment:

| Mechanism and official source | Limitation / disposition |
| --- | --- |
| [Azure SQL XE availability and scope](https://learn.microsoft.com/en-us/azure/azure-sql/database/xevent-db-diff-from-svr), [XE objects](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-objects-transact-sql), [column metadata](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-object-columns-transact-sql) | Azure sessions are database-scoped; query actual supported objects/columns. The exact proposed source was sqlserver.module_start filtered by source_database_id and each exact object_id, with statement collection disabled. It is absent in supplied discovery and operator-reported all-package results. Generic XE support or local SQL Server availability does not supply it. Even an available entry source would miss failures before entry and still need complete privacy, health, continuity and retention validation. |
| Available sqlserver.module_end | Described by the actual metadata as end of execution. No entry acknowledgement; cancelled/aborted/in-flight coverage cannot be inferred. collect_statement=false cannot change event timing or repair missing entry coverage. Rejected; no end-only substitution. |
| [RPC:Starting](https://learn.microsoft.com/en-us/sql/relational-databases/event-classes/rpc-starting-event-class), [SP:StmtStarting](https://learn.microsoft.com/en-us/sql/relational-databases/event-classes/sp-stmtstarting-event-class) and [Trace-to-XE conversion](https://learn.microsoft.com/en-us/sql/relational-databases/extended-events/convert-an-existing-sql-trace-script-to-an-extended-events-session) | RPC start describes a client request, not every module reached by nested wrappers/dynamic SQL. A statement start describes execution inside a procedure, not one entry per invocation; an entry followed by cancellation before a statement is not established by it. The conversion example names sp_statement_starting, not a replacement module-entry contract. Trace event-class pages do not prove Azure XE availability or a safe intrinsic payload. No RPC/batch/statement tracing, text parsing, line/offset heuristic or broad capture is proposed. |
| Exact-object EXECUTE Audit / SCHEMA_OBJECT_ACCESS_GROUP: [Azure Audit configuration](https://learn.microsoft.com/en-us/powershell/module/az.sql/set-azsqldatabaseaudit), [action semantics](https://learn.microsoft.com/en-us/sql/relational-databases/security/auditing/sql-server-audit-action-groups-and-actions), [Azure log format](https://learn.microsoft.com/en-us/azure/azure-sql/database/audit-log-format), [privacy limitations](https://learn.microsoft.com/en-us/azure/azure-sql/database/auditing-overview) | Object-specific EXECUTE audit is supported, but permission-use/issued-EXECUTE records are not documented as a complete module-entry ledger through same-owner chains. Azure records include statement text and principal information; the reviewed configuration does not establish source-level text/argument suppression. Filtering objects or projecting/redacting stored output cannot satisfy the source privacy requirement. No audit activation or broader action group is proposed. |
| [Procedure cache statistics](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-exec-procedure-stats-transact-sql), Query Store AUTO | Finished cached executions/retained selected queries remain positive evidence only; absence, eviction and NULLs do not prove zero invocations. Earlier conclusions remain unchanged. |
| event_counter, histogram or another target | A target cannot manufacture a missing source event, change exit to entry, or sanitize sensitive intrinsic data before capture. Counts alone also do not establish loss-free durable coverage. No alternate target harness is justified. |

No further metadata is essential to the present **blocked** decision: the independent all-package
start/end check has already been reported. Do not repeat discovery or request unrestricted event dumps
just to search for a plausible name. Before reopening mechanism work, a separate review must identify
an exact documented Azure-supported source with equivalent entry semantics, exact module/database
filtering and source-level privacy controls, then obtain only its bounded metadata if needed. A new
harness is contingent on that review; a familiar event name, tier upgrade or local test is insufficient.
No suitable supported alternative is established by the evidence reviewed here.

### Concrete caller inventory, migration and drain proposal — separate review only

This produces migration evidence; **inventory alone does not prove zero invocations**. No instrumentation,
release, resource or permission operation is authorized or implemented by this proposal.

1. **Create a private per-consumer/per-procedure register.** For each of get_hash, mark_failure and
   mark_success, record owner, consumer/build/artifact digest, effective connection target and actual
   auth/runtime principal mapping reference, call mode (direct/nested/dynamic/synonym), last/next expected
   activity, timezone/frequency, enabled/disabled status, offline/dormant instances, evidence reference,
   retire-or-migrate decision and unresolved gaps. No emails, arguments, secrets or raw SQL in this register.
   Cover installed desktop 1.0.136 and older/offline builds, Shale API/web versions, both live-update
   functions and deployment/configuration provenance, jobs (including disabled/monthly/quarterly),
   integrations, administrative scripts, stored wrappers, synonyms and dynamic/external callers.
   Owners must reconcile installer/fleet, deployment and scheduler records; repository/dependency absence
   and the current “no other consumers known” statement do not close rows. Keep unknown consumers explicit.
2. **Review required behavior before migration.** For each active row, obtain separate compatible design
   review. get_hash consumers need the reviewed bounded pre-tenant lookup/bcrypt path and ambiguity,
   deleted/disabled and actual-identity acceptance; no direct runtime hash-read fallback. mark_failure
   and mark_success require explicit owner disposition of the captured lockout/accounting policy,
   concurrent updates and authenticated authority; the current V1 lookup is not a replacement accounting
   implementation. Do not silently omit those effects or invent policy. Preserve remembered rotation,
   password/reset/session revocation, User projections and audit compatibility. Reproduce approved
   behavior in disposable tests; tie acceptance to installed artifacts/configuration, not source alone.
3. **Plan a separately authorized deployment and consumer drain.** For each migrated/retired row, retain
   old/new artifact and configuration identities, compatibility tests, rollback artifacts and an owner
   signoff. Schedule the window only from real activity records. Use the longest inventoried job interval
   plus an actual due run; 14 days is insufficient for an unexercised monthly/quarterly job. Require
   representative password/remembered startups, restarts and reconnects. Offline clients remain unresolved
   until reconciled/upgraded/withdrawn by their owners; do not infer drain from silence. Later authorized
   operations must prove old workers/schedulers stopped, queued/retry work exhausted, and old connections,
   sessions, pools and in-flight calls drained. Specify maximum task/retry/connection lifetimes from actual
   configuration; unknown or unbounded lifetimes block signoff. Do not kill sessions or change credentials
   as part of this task. Any fallback/rollback that reintroduces an old consumer reopens its drain.
4. **Assemble the decision packet without inventing negative evidence.** Retain per-procedure coverage,
   exact UTC start/end, owners, due runs, shutdown/drain attestations, artifact/configuration mapping,
   gaps, original captures and independently approved operational evidence. Positive cache evidence
   blocks retirement; quiet cache/Query Store or owner attestations alone do not prove zero remaining
   invocations. If no already-approved adequate observation record exists, leave the zero-invocation
   requirement unresolved. Trusted-seam application instrumentation would be a separate design review
   requiring privacy, every deployed caller/path including failures, continuity and durable health proof;
   it is not supplied here and cannot cover unknown external callers by assertion.

Mapping to the **five existing pre-retirement requirements** in “Exact permission delta and approval
boundaries” (possible outcomes after independently reviewed execution, none claimed satisfied now):

| Existing requirement | What this alternative can supply | What remains blocked / separate |
| --- | --- | --- |
| 1 — separate security-owner retirement/reopened-risk approval and actual identities/artifacts | Owner register, artifact hashes and actual configuration/token evidence references for review. | Approval itself, original-login/server authority and acceptance of reopened capabilities must still be supplied. Inventory does not grant them. |
| 2 — complete caller inventory; migrate required callers first | Reconciled fleet/job/wrapper/external register and separately tested migrations can satisfy this requirement **only if coverage is demonstrated complete**. | Unknown/dormant/external consumers, unreviewed accounting behavior or unaccepted installed artifacts keep it open. |
| 3 — bounded observation/drain, longest interval, connection/session drain, owners, zero remaining invocations | Real dates/due-run records and shutdown/queue/retry/pool/session evidence can satisfy the documented scheduling and operational-drain components for covered consumers. | Complete zero-invocation evidence remains BLOCKED without adequate approved coverage; register completion and attestations alone do not satisfy it. Do not set LegacyCallersDrained=CONFIRMED from this plan. |
| 4 — installed password/bcrypt/remembered compatibility and actual principals; accounting disposition | Separate installed-artifact tests and approved behavior disposition can satisfy compatibility for the demonstrated consumers while retaining 1.0.136 reads where required. | Offline/unverified artifacts or active accounting callers without compatible migration remain blockers. No Stage B old-client read drain is inferred. |
| 5 — exclusive serialized security/module change window | Named operator/window, source/baseline/rollback references and handoff can be prepared. | Actual exclusive control, independently retained BeforeHash/AfterHash and unchanged catalog/effective checks remain mandatory at any later authorized change. |

No acknowledgement, allowlist, preflight, retirement or rollback SQL changes. All three auth revocations
and six runtime DENYs in the separate candidate, exact grantors/states/identity hashes, failure atomicity
and rollback order remain intact. Stage A still requires successful unchanged preflight and separate
approval; Stage B still requires its broader compatible fleet/job/old-read drain. Original captures,
installed 1.0.136 compatibility, Case/RLS prerequisites and the separate live-update authorization and
audit-permission findings remain open and unchanged.

### Separate offline launcher correction and verification

The launcher now leaves HarnessPath optional in param() and resolves its omitted default from PSScriptRoot
in the script body. Explicit paths remain supported. It still evaluates only closed connection
construction and never opens/authenticates. The Windows regression covers **both** launch forms from an
unrelated working directory; the source regression prevents reintroducing parameter-default evaluation.
Pre-edit test-impact review searched HarnessPath, builder/launcher references, module_start consumers,
invocation assertions, observation tests and selector ownership. Existing payload, coverage and lifecycle
contracts remain unchanged. Only the offline launcher and its Python tests change executable behavior;
the Azure harness/session/setup/retirement/rollback/preflight/Stage A/B/RLS files are unchanged.

Audit compatibility: documentation and offline closed construction introduce no application sensitive
read/domain mutation or audit schema/event. Existing PHI/entity-action/session-security seams remain
intact. Proposed inventory/approval/metadata retention stays in private change control.


Local verification for this reassessment (Linux; no native Windows/Azure execution):

| Check | Result / boundary |
| --- | --- |
| Focused builder Python | 2 source checks PASS; 1 native-Windows skip. New default-path regression also rejects the live-base launcher. |
| Entire auth Python suite | 30 PASS, 2 native-Windows skips (32 total); all six invocation privacy/coverage/ownership assertions still pass. |
| Focused existing auth/module/lifecycle/support Maven | 15 PASS; no failures/errors/skips. |
| Committed base-to-head selector affected suite / critical reactor | 54 PASS / 116 PASS; no failures/errors/skips. Security-data selects auth, tenant/session and neighboring audit/transaction contracts for this security-tooling path; no full-suite escalation. |
| Changed-file/test review, relative documentation links/fences, Python compilation and Git whitespace | PASS. Exactly four changed files: offline launcher, its tests, package README and this runbook. No Azure lifecycle or safeguard SQL changes; raw attachment/private captures are excluded. |
| Corrected default-path launcher / two-form native regression | NOT RUN: no native Windows PowerShell 5.1 on this host. The default-path fix remains runtime-UNVERIFIED; the operator's earlier explicit-path PASS is separately attributed above. |

The initial network-enabled execution request timed out in automatic approval review before starting.
An offline focused attempt then lacked cached libphonenumber 9.0.40; the default-network attempt could
not reach the sandbox proxy. The permitted configured-proxy retry passed without dependency/build
changes. Failed/unstarted attempts are not passes. No installation was performed.

Exact repository-root verification commands (existing configured Maven/JDK/proxy/CA wrapper; scratch
logs and selection plan are outside Git):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_connection_builder.py' -v
python3 -m unittest discover -s build/auth-verification -p 'test_*.py' -v
python3 -m py_compile build/auth-verification/test_connection_builder.py
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base 5bf1dfa93109f697cca8b58b3b1534e2c0ba03ab --head HEAD --format markdown --plan-output /workspace/work/login-selection.json --run
# --run executes the selected affected Maven suite followed by mvn test.
git diff --check 5bf1dfa93109f697cca8b58b3b1534e2c0ba03ab HEAD
```

Required native retest from the reviewed repository root (offline only):

```powershell
python -m unittest discover -s build/auth-verification -p 'test_connection_builder.py' -v
if ($LASTEXITCODE -ne 0) { throw 'Corrected default/explicit launcher remains unverified' }
```

This runs the real launcher both without and with an explicit HarnessPath from an unrelated working
directory. No Azure connection, instrumentation, permission/resource change, deletion, installation,
release, merge or deployment was performed. Retirement/installation and production monitoring remain
BLOCKED; the native launcher fix remains unverified until its corrected forms pass on Windows 5.1.

## Windows PowerShell construction correction — 2026-10-16

Live `codex/latest` fetched at `f2c7d47cc00a4804b3747d73bc635525ddd44652`; separate task branch
`codex/fix-invocation-powershell51` and checkout preserve unrelated work. Initial sandboxed Git fetch
could not reach the configured proxy. Connector reads imported and hash-verified the exact live commit,
tree and blobs; the subsequent permitted shell fetch confirmed that base and supplied full history.

Operator reports Windows PowerShell **5.1.26100.9549** Discover failed with `System.ArgumentException`
at former harness line 46, before `SqlConnection.Open`. Offline `DataSource` and `InitialCatalog`
assignments each returned `Keyword not supported`. State was saved as `Discovering`; evidence directory
was empty; no harness instrumentation or synthetic procedures were created. This is attributed evidence,
not an independently executed native run or Azure observation. It establishes no target/metadata result.

PowerShell's `IDictionary` adaptation makes dot-property writes use connection-string keyword lookup.
All eight builder settings now use canonical indexer keys; the related read explicitly calls the CLR
`get_ConnectionString()` accessor. Encryption, certificate verification, timeout, disabled pooling,
async support and application name are preserved. Authentication, endpoint/database/database ID/dbo,
engine/transaction/version guards and every phase's SQL/ownership behavior remain unchanged.
The generic failure message now routes to phase-specific README recovery.

For this exact pre-Open failure, retain original facts/state/source/error/version evidence and the empty
directory privately. Do not edit/reuse the failed state or run Stop/Cleanup for resources never created.
The [package recovery instructions](../sql/auth/invocation-validation/README.md#recovery-from-the-pre-connection-windows-powershell-51-discover-failure)
require a fresh nonexistent state path and new Discover run after the fix, with current independently
approved source/facts. Retain original facts even if a separate reviewed facts file is needed.
Uncertain failures or partial Setup still require exact manual ownership review; this is no approval
of Azure retry, new credentials, synthetic DDL, permissions, retirement, merge or deployment.

Pre-edit inventory searched the repository for `SqlConnectionStringBuilder`, builder property writes
and reads, `Open-Connection` and invocation-validation references. Reviewed AGENTS/prompt rules and the
package README completely, owning runbook, relevant database-schema sections and tenancy-and-rls.
Existing six invocation payload/privacy/loss/ownership tests and neighboring observation/launcher tests
have no obsolete builder expectations. Changed-file review maps the harness to the new offline builder
test pair, README/recovery to operator instructions and this evidence record. The selector's security-data
auth/session/audit/transaction smoke contracts preserve the surrounding security tooling boundary.

The new standalone native test parses the real harness in PS 5.1 and evaluates only its construction
assignments through a closed SqlConnection. It checks all eight settings after connection-string
roundtrip, escaping, no embedded identity and async API availability with `.invalid` inputs; it never
invokes Open, authentication, Query or the harness lifecycle. Python runs it on Windows and skips it
elsewhere, with a separate source check for canonical keys/accessor. The source check rejects the live-base
implementation. This does **not** constitute native runtime verification; no native powershell.exe,
powershell or pwsh is available on this Linux host. The precise native command is:

```powershell
powershell.exe -NoLogo -NoProfile -NonInteractive -File build/auth-verification/test_connection_builder.ps1
```

Local verification (Linux; Python 3.12.14, Maven 3.9.11 / JDK 21):

| Check | Result / boundary |
| --- | --- |
| Focused builder Python | 1 source check PASS, 1 explicit native-Windows skip; source check rejects the original base. |
| Full auth Python | 29 PASS, 2 explicit native-Windows skips (31 tests total). Existing six invocation assertions pass. |
| Focused auth/module/lifecycle/support Maven | 15 PASS, no failures/errors/skips. Initial sandboxed dependency fetch was blocked; configured-proxy retry passed without build changes. |
| Committed base-to-head selector affected security-data suite | 54 PASS, no failures/errors/skips; no full-suite escalation. |
| Critical reactor `mvn test` through selector | 116 PASS, no failures/errors/skips. |
| Source scope, relative documentation file links/fences, Python compilation, Git whitespace | PASS. Authentication/guards/lifecycle after construction byte-for-byte unchanged apart from the recovery message. No private evidence in the commit. |
| Native Windows PowerShell 5.1 builder execution / Azure lifecycle | NOT RUN; not inferred from source checks or Maven. |

Exact repository-root commands (existing Maven proxy/CA/JDK wrapper; logs/plan kept outside Git):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_connection_builder.py' -v
python3 -m unittest discover -s build/auth-verification -p 'test_*.py' -v
python3 -m py_compile build/auth-verification/test_connection_builder.py
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base f2c7d47cc00a4804b3747d73bc635525ddd44652 --head HEAD --format markdown --plan-output /workspace/work/ps51-selection.json --run
# --run executes the selected affected command followed by mvn test.
git diff --check f2c7d47cc00a4804b3747d73bc635525ddd44652 HEAD
```

Native Windows 5.1 construction remains
**UNVERIFIED** until that command passes there. Source checks, Maven and any other runtime cannot certify it.
No Azure connection, credentials, instrumentation, permission changes or private evidence are part of
this patch. Audit compatibility needs no application event or migration: no domain/sensitive application
read/mutation changes; existing PHI/entity-action/session-security seams remain intact and operator
failure evidence/approval/retention belong in private change control. All prior retirement/rollback,
preflight, Stage A/B, installed-client and RLS gates below remain authoritative.

## Synthetic Azure invocation validation follow-up — 2026-10-15

**Production monitoring, retirement and installation remain BLOCKED.** The separately isolated
[executable operator package](../sql/auth/invocation-validation/README.md) tests raw module-entry/exit
payload privacy and bounded coverage in a separately approved, empty synthetic Azure SQL Database.
It supplies discovery, guarded setup, synthetic workload, full raw collection, offline assertions,
stop and exact-owner cleanup. It provisions nothing and has not connected to Azure. No live candidate
is supplied; synthetic diagnostic start/end sessions must never be activated against Shale or its copies.

Operator evidence at **2026-10-15 22:29:04 UTC**: the #1875 sampler ran against Shale, exit 0;
each of `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure`, `dbo.sp_login_mark_success` had
`CachedPlanCount=0`, NULL execution counts/times and `INCOMPLETE_CACHE_OBSERVATION`.
Record these as **unavailable cache evidence, not zero usage**. Query Store AUTO also retained no
statements for them. These attributed reports do not establish nonuse; no original output was supplied
or regenerated in this task. Preserve the original captures and #1874 retirement/rollback, unchanged
preflight, Stage A/B, desktop compatibility and RLS prerequisites. The live-update tenant/user
negotiate authorization concern remains separate and OPEN.

Live base fetched: `be55d7187cddc2bc7bf2fbfbbfd00a19c4b614e4`; GitHub reports #1875 merged at
2026-10-15 22:21:20 UTC, and local ancestry confirms its merge in the base. Task branch:
`codex/azure-synthetic-invocation-validation-20261015`; a separate worktree preserves the prior checkout.
Reviewed completely: AGENTS.md, architecture/codex-prompt-rules.md and this runbook; database-schema
and tenancy-and-rls routing and the existing auth/module/lifecycle/permission fixture and launcher tests
were inspected before edits. No application symbol, auth behavior, permission, preflight, historical
capture, retirement/rollback, Stage A/B or RLS contract changes. Audit integration needs no application
schema/event: synthetic DDL/security metadata and retention use private operator change control.

Microsoft documentation and local supported metadata were inspected before choosing this bounded
experiment. Local module_start/module_end expose intrinsic statement plus configurable collect_statement;
setting it to zero emitted an empty statement field locally. That is **not Azure payload verification**.
The package requires actual Azure metadata review before setup, rejects full-payload privacy violations,
checks exact module/database predicates, counts and health, and deliberately demonstrates/rejects bounded
target loss. Cross-database context, restart/failover and durable operational coverage remain explicit gaps.
Even successful synthetic assertions always refuse production activation and cannot close #1874's drain gate.
The package lists minimum actual resource facts, provenance, identity, experiment/review and retention
approvals; none is invented or granted here. If privacy/coverage cannot be established, use the documented
practical alternatives or keep retirement blocked without weakening any gate.

### Verification of the synthetic validation package

Pre-edit inventory searched legacy procedure names and existing observation, launcher, auth/module,
lifecycle, permission fixture and selector contracts. Existing tests/production expectations remain
unchanged; six new offline contracts cover forbidden intrinsic values and sentinels, unexpected raw
actions/fields/causality/nested XML, exact identities, target overwrite despite zero session drops,
complete evidence/stale/restarted/stopped capture and hidden final-snapshot leaks, concurrency overlap
and evidence-preserving ownership cleanup. Changed-file review maps only this new package and tests
to these contracts and the selector's security-data auth/session/audit/transaction smoke suite.

| Executed check | Result / boundary |
| --- | --- |
| New offline assertions / full auth Python suite | 6 PASS / 28 PASS and one native-Windows skip (29 tests run). Includes adversarial synthetic XML and complete synthetic evidence fixtures; not Azure acceptance. |
| Existing focused auth/module/lifecycle/support Maven | 15 PASS; no failures/errors/skips. |
| Committed-base selector affected security-data suite / critical reactor | 54 PASS / 116 PASS; no failures/errors/skips, no full-suite escalation. |
| Disposable SQL Server 2022 supported metadata and raw source payloads | PASS: inspected all module_start/module_end/target columns before selection; collect_statement=0 emits empty intrinsic statement; full raw payload allowlist/sentinel checks pass. |
| Local synthetic SQL workload and predicates | PASS: per probe seven starts/seven ends, 42 total events; direct, nested, dynamic, overlapping concurrent, thrown failure, pre-entry conversion and entry-acknowledged SQLCMD timeout/cancellation trials. Same-schema unrelated/near-name and other-schema same-name calls excluded; wrong source-database-ID session receives zero events. |
| Local target bounds/loss and ownership | PASS: eight-event target retains 8 of 2000 processed events, droppedCount=0 and truncated=0; loss is detectable by total/retained mismatch. Exact session config fingerprint stable across start/stop, module-catalog drift detected and explicit SQL object cleanup succeeds. |
| Source scope, relative documentation links/fences, Python compilation and Git whitespace | PASS; no private/operator evidence, binaries, original captures or application changes committed. |

Local SQL uses the previously recorded Microsoft image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, a fresh task-labelled,
network-isolated container with no published ports, random synthetic credentials/databases/schemas and
only task-owned resources. Exact container ID and unique label are checked before container removal.
Local XE uses **ON SERVER**, server catalogs and SQL Server health support; the Azure database-session
catalog guard is not executable locally and was omitted only in scratch local orchestration. The shipped
Azure guards remain intact. Azure's failed_buffer_count is documented but absent locally: no Azure
health result or synthetic full Azure lifecycle pass is claimed. The Windows .NET runner was statically
reviewed; it was not executed or parser-certified on native Windows. It remains runtime-unverified there.

An initial 64 KB local session buffer was rejected with a minimum-size error; the bounded stress
configuration now uses 1024 KB and passed. Scratch orchestration also required batch separation and
handling SQLCMD's timeout output: local SQLCMD printed `Timeout expired` with exit 0, so that local
cancellation was validated by entry acknowledgement, explicit timeout output and raw events, **not by
exit status**. The shipped .NET workload uses explicit Cancel and requires a thrown SqlException. Failed
scratch attempts were corrected/rerun and are not counted as passes. The first sandboxed Maven dependency
fetch failed; its configured-proxy retry passed without dependency/build changes.

Exact repository-root commands (existing configured Maven wrapper; work/ contains uncommitted synthetic
scratch helpers/public documentation copies, not operator captures):

```bash
python3 -m unittest discover -s build/auth-verification -p test_invocation_validation.py -v
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m py_compile docs/sql/auth/invocation-validation/assertions.py
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base be55d7187cddc2bc7bf2fbfbbfd00a19c4b614e4 --head HEAD --format markdown --plan-output work/invocation/selection.json --run
# --run executes the selected affected Maven command, followed by mvn test.
python3 work/invocation/validate-local.py
python3 work/invocation/local.py stop
# Final committed-file selection is checked again without repeating unchanged Maven suites.
git diff --check be55d7187cddc2bc7bf2fbfbbfd00a19c4b614e4 HEAD
```

**Unrun:** Azure connections/provisioning/activation, Azure ON DATABASE lifecycle/authority/payloads,
Azure target failure counters, native Windows .NET/PowerShell execution, cross-database context/colliding
object-ID/replica trials, restart/failover, sustained-rate/durable multi-day coverage and installed desktop
acceptance. None is inferred from local results. No permission change, production procedure call,
installation, release, merge or deployment occurred. Production activation and retirement remain BLOCKED.

## Post-#1874 evidence and bounded usage observation — 2026-10-15

**Retirement and installation remain BLOCKED. Observation is not permission-change or installation
approval.** This task fetched live `codex/latest` at `572b96756c1e2a6573be5b1887ccbb70cdcfe8a1`;
GitHub reports #1874 merged, and local Git ancestry independently confirms its merge is in the base.
Work branch: `codex/legacy-login-observation-20261015`. Original captures, unchanged preflight,
#1874 exact retirement/rollback safeguards, installed-client compatibility, Stage A/B gates and every
RLS prerequisite remain authoritative. No live retirement, Stage A installation, Stage B restriction,
RLS change or deployment has occurred according to the operator; none was performed in this task.
The historical records below remain attributed to their original revisions.

### Evidence ledger and limits

| Provenance | October 15 evidence | What it establishes / does not establish |
| --- | --- | --- |
| Operator report after #1874 | Native Windows disposable SQL harness: credential_identity, remembered_rotation, runtime_isolation PASS; exit 0 | Supersedes the pending native Windows retest for the operator's reported post-#1874 run. Exact checkout/tool/artifact hashes were not supplied. Codex did not execute that Windows run; installed desktop acceptance and actual-login mapping remain open. |
| Operator consumer inventory | Desktop 1.0.136; app.myshale.com through Shale API; Azure live updates; no other consumers known | Working inventory only. Does not establish exhaustive fleet, job, integration, dormant/offline-client or dynamic/wrapper coverage. |
| Operator Azure Function inventory | Function App `negotiate` has HTTP functions `negotiate` and `live-case-updated`; settings list has Web PubSub, Storage and monitoring, with no observed SQL settings/connection strings | Supports a messaging-only hypothesis for the observed component. Settings-list absence is not proof that every code/configuration/identity path lacks SQL access. |
| Operator artifact provenance | Both source files and DLL supplied previously; DLL downloaded with Azure **Download app content**, app settings excluded | Attachments are absent from this session. They were not inspected or rehashed here. Preserve them privately; no binaries or private captures in Git. |
| Prior assistant static inspection, relayed in this task | DLL startup/function methods consistent with supplied source, Web PubSub operations, no SQL calls or legacy-login procedure references found | Prior static evidence only; neither independent inspection in this task nor runtime/exhaustive nonuse proof. |
| Operator artifact/deployment identifiers | DLL SHA-256 `19acf16bd78f754d147ebbb7ad5050eb057fa5692a72815238e7209b713361e1`; active deployment displayed `845a735`, February 25, 2026, VS Code push / Run-From-Zip | Digest identifies the reported artifact, not a digest independently computed here. Deployment ID is **not established as a source Git commit**; no assumed source/deployed parity. |
| Operator Query Store report | READ_WRITE, AUTO, retention setting 30 days; each exact legacy procedure has zero captured statements, zero retained executions, NULL first/last execution | No retained usage found by that query. AUTO can filter queries; retention configuration is not a complete 30-day observation record. Cannot certify nonuse. |
| Existing automated tests | #1874 records local synthetic SQL, focused, selected and critical passes | Regression evidence for the captured bodies and safeguards, not deployed usage or installed-fleet acceptance. New checks for this task are recorded separately below. |
| Independently verified here | Live GitHub merge metadata, fetched Git base/ancestry, tracked source/runbook and current official Microsoft documentation | No Azure connection, attachment inspection, deployed runtime verification or new operator approval is implied. |

**Separate live-update authorization finding — OPEN:** the prior source review reports that `negotiate`
derives tenant/user from request parameters and grants join/send group roles without Shale-session
validation in the reviewed method. That is a tenant authorization concern, separate from SQL legacy
procedure use. Platform authentication/other upstream controls and full deployed behavior are not
established here. Track independent owner review of authenticated tenant/user derivation, group-role
scope and negative cross-tenant/session tests. Do not modify or deploy that component in this task;
absence of observed SQL references does not resolve its authorization concern.

### Smallest supported method and concrete limitation

Prefer an **already approved, adequate** observation record if an owner can supply one with exact
module coverage, UTC boundaries, loss/gap/health evidence and retention covering the window. None is
established by the supplied Query Store AUTO result. Microsoft’s [Query Store best practices](https://learn.microsoft.com/en-us/sql/relational-databases/performance/best-practice-with-the-query-store) describe AUTO filtering and automatic cleanup/state transitions.
Do not change its capture mode, query raw SQL text,
or enable broad SQL Audit/RPC/batch/statement tracing to compensate.

The smallest supported additional read is the existing
[`sys.dm_exec_procedure_stats`](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-exec-procedure-stats-transact-sql)
cache counters, filtered by **current database ID and exactly the three object IDs**, never name patterns:
`dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure`, `dbo.sp_login_mark_success`.
The separate [sample candidate](../sql/auth/permission-cutover/sample-legacy-login-usage.sql) reads
only module identity, UTC sample/cache/last-execution times and counts; it creates no persistent object,
event, target, audit or database setting. It uses the existing approved dbo metadata connection,
not auth/runtime impersonation or new grants. Microsoft documents service-tier-specific DMV authority:
Basic/S0/S1 and elastic pools require server/Entra admin or `##MS_ServerStateReader##`; other Azure SQL
Database service objectives permit VIEW DATABASE STATE or that server role. If the existing identity
cannot read it, stop; this plan does not authorize granting additional authority. Dbo database context
alone does not establish that service-tier authority. Wrong database, missing/hidden modules, non-UTC
engine, ambient transaction or a DMV error must stop the sample.

**This is a positive-evidence fallback, not a sufficient retirement observation system.** Microsoft
states counters cover completed executions of cached plans; rows disappear on eviction. Aborted,
in-flight, recompiled/evicted-between-samples and pre-entry failures can be missed. A retained last
execution in the approved UTC window is evidence to investigate use and block retirement. Earlier
retained executions are historical context. Counts are cumulative per cached plan: do not sum successive
snapshots or claim their difference is an exact call count. Multiple plans are aggregated; changes in
plan count/earliest cache time/counters can reveal some resets, never establish continuity. A missing
row yields NULL retained count/times, not zero calls. Every row says `INCOMPLETE_CACHE_OBSERVATION`,
even when collection succeeds. A quiet series **cannot close #1874's drain/nonuse gate**.

UTC is supported by Microsoft's [GETDATE documentation](https://learn.microsoft.com/en-us/sql/t-sql/functions/getdate-transact-sql):
Azure SQL Database follows UTC; Managed Instance is an exception. The sampler refuses a nonzero engine
offset rather than silently labeling local SQL Server timestamps UTC. Its sampling boundary follows
materialization; the view is not an atomic invocation ledger. Catalog changes, object-ID reuse or
recreated modules invalidate mapping: serialize module metadata during sampling and review the original
captured-definition baseline separately before/after, without replacing original captures.

### Why no activatable event-session candidate is supplied

Microsoft's [Azure SQL Extended Events documentation](https://learn.microsoft.com/en-us/azure/azure-sql/database/xevent-db-diff-from-svr)
confirms database-scoped `ON DATABASE` sessions, common event metadata DMVs, in-memory targets, and
`event_file` persistence to Azure Storage with separate credential/RBAC requirements. That is platform
support, **not verification of a particular Azure build's complete event payload**. This session has
neither a version-specific Azure event schema nor raw synthetic event payloads establishing that a
module-entry/exit event excludes every forbidden field. No event configuration can be labeled privacy
verified here. The shipped sampler emits no event payload at all; it does not create a new event pipeline. This is an evidence limitation, not a claim that Azure SQL cannot support safe events.
The permitted bounded fallback above therefore stays insufficient for negative proof.

A separately approved follow-up can first inspect available `sqlserver.module_start`/`module_end`
metadata through [`sys.dm_xe_object_columns`](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-object-columns-transact-sql)
(readonly/data/customizable fields, defaults and configurable controls), without creating a session.
Then, on a disposable Azure SQL database with synthetic modules/arguments, review **entire raw event
payloads before activation in Shale**: every intrinsic data field, action, causal/activity metadata and
target serialization, not just displayed/exported columns. Verify SQL text/statement fields are absent
or disabled in the emitted payload itself, not masked afterward. No SQL text, arguments, email,
credentials, hashes, tokens or application data may enter the event pipeline even if the chosen target
stores only counts. Reject unexpected fields and unsupported options. Do not infer that no ACTION
clause means no intrinsic statement field. Raw validation uses synthetic sentinels only, never real
legacy calls against Azure. Review version/build drift before later activation.

If that succeeds, review a distinct exact-object-predicate module-entry counter candidate for each
procedure, with no actions and only necessary identity/timing/count fields. Three filtered
`event_counter` sessions could avoid histogram collisions; per-session ownership, monitoring overhead,
UTC collection boundaries and restart continuity still need validation. A single histogram is smaller
but Microsoft's [target documentation](https://learn.microsoft.com/en-us/sql/relational-databases/extended-events/targets-for-extended-events-in-sql-server)
warns collisions can misattribute procedure counts and zero buckets. Aggregating targets **do not**
make an unsafe source payload private. A ring buffer stores raw events, loses old events and may
truncate its XML at 4 MB. It is not a durable multi-day record. Event files add Storage access,
retention/rollover, credentials and cleanup scope; none is approved or created here. Do not use
NO_EVENT_LOSS as a blanket completeness guarantee or block application work to make observation perfect.

Any later session candidate must supply tested exact Windows create/start/status/collect/stop/drop
commands, unique task names and saved identity/configuration guards; refuse preexisting sessions and
unexpected configuration. It must not adopt/drop existing telemetry, shared credentials or storage.
**There are deliberately no Azure XE activation/drop commands in this revision:** safe event payloads
and a complete observation design are not verified. Approval of this PR authorizes no such activation.

### Coverage, health, window and decision

| Risk | Required treatment |
| --- | --- |
| Missed/aborted/pre-entry calls | Cache counters only report finished executions. Module-entry events would still miss permission/compile failures before entry; exit-only events can miss aborted calls. Neither proves attempted-call absence. |
| Nested/dynamic invocation | Filter the target module identity, not caller RPC text. Wrappers/dynamic SQL that reach it may contribute counters; calls that fail before entry do not. Validate nested, same-owner wrapper and dynamic paths in any later event candidate; inventory remains mandatory. |
| Event loss/aggregation | Future event candidates must record session/target state, loss counters and collection watermarks before/after each interval. Counter aggregation and histogram collisions need explicit testing. Unknown loss is incomplete; no dropped-event counter alone proves health. |
| Restart/failover/cache eviction | Cache state can vanish; event targets/session counters can reset and stopped sessions stop capturing. Preserve prior snapshots, mark a gap and start a new interval. STARTUP_STATE alone cannot recover downtime or in-memory data. |
| Retention/capture gaps | Query Store 30-day setting is not 30 days of AUTO coverage. Local snapshots exist only after successful collection. Future files need bounded rollover/lifecycle and retrieval verification. Missing files, inaccessible storage, partial exports or stopped capture invalidate the affected interval. |
| Monitoring health | Record expected/actual UTC collections, operator availability, target/database identity and known maintenance privately. Nonzero SQLCMD exit, timeout, missing/invalid three-row output, stale status, clock reversal, changed object IDs or cache discontinuities are gaps/UNKNOWN. Successful polling is collection health only, never complete call coverage. |

**Window proposal, not an approved schedule:** first obtain actual activity dates/frequency for desktop
1.0.136 (password login and checked/unchecked remembered startup/restart), Shale API/web authentication,
and live-update negotiation/reconnect. Review already approved records/configuration; do not generate
legacy Azure calls or collect user identities/payloads for this inventory. Obtain owners' scheduled-job
inventory, including disabled/monthly/quarterly jobs, integrations, maintenance scripts, stored wrappers,
older/offline desktops and effective connection targets. Intervals, timezones, next due runs, idle-client
coverage and fleet completeness are currently **UNKNOWN**; no job schedule or complete fleet is asserted.

After inventory, propose **14 consecutive days** as an initial operational review window to include two
weekly activity cycles *if owners confirm those cycles actually occur*. Start only when the known
consumers will be active; include their normal workdays, representative restarts and the longest
inventoried job interval plus a completed due run. If a monthly/quarterly job exceeds that bound, extend
to its actual due cycle or keep that consumer unresolved for its own approved disposition; do not mark
it drained. Dormant/offline clients require separate compatibility/drain evidence. Missing activity or
health gaps extend/restart the affected interval under owner review. Fourteen days and five-minute
sampling below are proposals, not evidence of actual schedules or a guarantee that calls are captured.

At the endpoint, submit per-procedure use/unknown findings, exact UTC intervals, gaps, telemetry health,
consumer activity and inventory coverage to the security owner. Positive use blocks retirement pending
caller migration/disposition; no observed use with this fallback remains **UNKNOWN / insufficient**.
Do not substitute acceptance of cache sampling for #1874's independently approved inventory, caller drain,
installed 1.0.136 compatibility, actual principal mapping, exclusive change window, baseline/rollback
identities, unchanged Stage A preflight, Stage B fleet/job gates or RLS prerequisites.

### Exact Windows fallback lifecycle — only after separate read-only collection approval

No commands below were run against Azure. Approve the fixed sampler revision, existing connection
identity/target, 14-day maximum initial collection, proposed five-minute cadence, operator staffing,
private destination and retention first. The connection array is the existing approved mechanism;
no secrets/URLs are supplied here. Do not add SQL/query echo or command-line passwords. This is a
manual per-sample procedure: no unattended scheduler or background Azure job is installed. A staffed
collector may invoke the collect block at the approved cadence; missed rounds remain recorded gaps.
Use a fresh PowerShell session and keep its exact path/token/file list for guarded local cleanup.

```powershell
# START: local bookkeeping only. $privateObservationParent must already be an approved private folder.
$ErrorActionPreference = 'Stop'
$observationToken = [guid]::NewGuid().ToString('N')
$privateObservationParent = (Resolve-Path -LiteralPath $privateObservationParent).Path
$observationDir = Join-Path $privateObservationParent "shale-legacy-observation-$observationToken"
if (Test-Path -LiteralPath $observationDir) { throw 'Refuse existing observation directory' }
New-Item -ItemType Directory -Path $observationDir | Out-Null
$markerPath = Join-Path $observationDir 'owner.txt'
$startPath = Join-Path $observationDir 'start-utc.txt'
$stopPath = Join-Path $observationDir 'stop-utc.txt'
Set-Content -LiteralPath $markerPath -Value $observationToken
Set-Content -LiteralPath $startPath -Value ([DateTime]::UtcNow.ToString('o'))
$ownedObservationFiles = @($markerPath, $startPath)
$observationStopped = $false
$observationStartUtc = [DateTime]::Parse((Get-Content -LiteralPath $startPath)).ToUniversalTime()

# STATUS: local status only, NOT Azure capture health or zero-use evidence.
Get-Content -LiteralPath $startPath
Get-Item -LiteralPath $ownedObservationFiles | Select-Object Name, Length, LastWriteTimeUtc
if (Test-Path -LiteralPath $stopPath) { Get-Content -LiteralPath $stopPath }

# COLLECT: one separately approved read-only DB connection; repeat only within approved bounds.
if ([DateTime]::UtcNow -gt $observationStartUtc.AddDays(14) -or
    $ownedObservationFiles.Count -ge 4035) { throw 'Initial collection bound reached; stop for review' }
if ($observationStopped -or (Test-Path -LiteralPath $stopPath)) { throw 'Collection stopped' }
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
$samplePath = Join-Path $observationDir ("sample-" + [guid]::NewGuid().ToString('N') + '.txt')
$ownedObservationFiles += $samplePath # retain even failed output; never overwrite prior samples
sqlcmd @approvedSqlcmdOptions -b -V 16 -l 30 -t 30 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/sample-legacy-login-usage.sql -v ExpectedDatabase=Shale -o $samplePath
if ($LASTEXITCODE -ne 0) { throw 'Collection gap: retain failed output privately; no zero-use inference' }
Get-Content -LiteralPath $samplePath
# Operator validates exactly three named rows, fixed Coverage value, UTC values and counts.
# Record missing/late rounds and resets in private change control. Never rewrite original samples.

# STOP: after last collection/review, local marker only; no server telemetry exists to stop.
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
if (Test-Path -LiteralPath $stopPath) { throw 'Already stopped; preserve original boundary' }
Set-Content -LiteralPath $stopPath -Value ([DateTime]::UtcNow.ToString('o'))
$ownedObservationFiles += $stopPath
$observationStopped = $true
```

SQLCMD closes each connection, removing its table variables. Nothing to stop/drop in Azure; **do not
clear the plan cache**, remove Query Store data, drop any event session or alter audit/Storage settings.
Preserve snapshots privately through security-owner review. Local deletion requires a separate recorded
retention decision and evidence retained in its approved destination; never delete the original captures.
The cleanup below validates the complete directory before deleting an exact in-memory file list; it
refuses unexpected files/subdirectories/reparse points, changed token, missing stop marker or lost state.
If the terminal/file list is lost, stop for manual ownership review, never reconstruct ownership by glob.

```powershell
# CLEANUP: only after review/retention approval; set this from that actual decision, never by default.
if ($observationCleanupApproval -cne 'APPROVED_EVIDENCE_RETAINED') { throw 'Retention decision required' }
if (-not $observationStopped -or -not (Test-Path -LiteralPath $stopPath)) { throw 'Stop first' }
$dir = Get-Item -LiteralPath $observationDir
if ($dir.Name -cne "shale-legacy-observation-$observationToken" -or
    ($dir.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe directory identity' }
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
$children = @(Get-ChildItem -LiteralPath $observationDir -Force)
if ($children.Count -ne $ownedObservationFiles.Count) { throw 'Unexpected directory contents' }
foreach ($child in $children) {
    if ($child.PSIsContainer -or ($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        $ownedObservationFiles -cnotcontains $child.FullName) { throw 'Not task-owned; stop' }
}
foreach ($file in $ownedObservationFiles) { Remove-Item -LiteralPath $file }
Remove-Item -LiteralPath $observationDir # empty directory only; no recursive/wildcard deletion
```

Keep this private directory exclusive to the operator during validation/deletion (including trusted
ancestors); the guard is not protection against a hostile concurrent filesystem writer. Partial deletion
failure stops and requires review. This does not authorize deletion of any server telemetry or shared file.

Audit compatibility: the fallback reads bounded performance/security metadata, not application rows or
sensitive business views, and mutates no domain/security catalog. No application audit event/schema is
added. Existing PHI/entity-action/session-security audit transactions stay unchanged; collection approvals,
health/gaps and retention belong in private operator change control without account data or SQL payloads.

### Verification of this observation revision

Pre-edit test-impact review searched all three procedure names, permission-cutover scripts, the complete
`AuthPermissionCutoverFixture`, existing auth/module/lifecycle and launcher tests, and selector ownership.
No application symbol, DTO, auth behavior or existing test expectation changes. Changed-file review maps
the new read-only SQL to five privacy/scope/NULL/approval/cleanup contract checks; the owning runbook adds
operator instructions only. Existing SQL/Java fixtures and every preflight/retirement/Stage A/B/RLS file
are unchanged. The selector chooses security-data's auth, session and neighboring audit/transaction smoke
contracts for security tooling; no full-suite escalation. Audit integration requires no schema change.

| Check executed in this task | Result and boundary |
| --- | --- |
| New observation Python contracts | 5 PASS: exact object/database filtering, minimum source fields/no mutation or text/handles, uncached NULL/fixed incomplete coverage, event-payload approval boundary, exact nonrecursive cleanup guards. Static checks, not Windows/Azure runtime verification. |
| Entire auth launcher Python suite | 22 PASS, one native-Windows skip (23 tests run). |
| Focused existing auth/module/lifecycle/support Maven tests | 15 PASS; no failures/errors/skips. |
| Committed-base selector affected suite / critical reactor | 54 PASS / 116 PASS; no failures/errors/skips. |
| Disposable SQL Server 2022 sampler through real SQLCMD | PASS: missing modules, wrong database and ambient transaction refuse; uncached counters/times remain NULL; exact three rows exclude same-schema near-name and other-schema decoys; direct/nested/dynamic synthetic calls produce retained counts; full returned output excludes synthetic argument sentinel; ALTER of one synthetic module evicts its cached row and returns unknown, not zero-use. |
| Local cleanup | Exact generated container ID plus unique task label verified before removal; no ports published, no existing resource touched. Windows cleanup received static guard review only. |
| Documentation links/fences, source scope, whitespace | PASS; only this runbook, the new sampler and its Python contracts change. No private captures/binaries committed. |

Local SQL used the repository-recorded image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, fresh random
container/database and synthetic no-op procedures/argument sentinels. These reproduce neither deployed
Azure usage nor installed desktop acceptance. Local validation used a UTC SQL Server engine; the shipped sampler has no event-session scope at all. No Azure `ON DATABASE` session,
Azure service-tier permission, Azure event payload or failover behavior was tested.

Exact repository-root commands (`work/` contains uncommitted scratch orchestration and public documentation
copies, never operator captures; `/workspace/.tools/bin/mvn` is the existing configured wrapper):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_observation.py' -v
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base 572b96756c1e2a6573be5b1887ccbb70cdcfe8a1 --head HEAD --format markdown --plan-output work/legacy-observation/selection.json --run
# --run executes its selected Maven command followed by mvn test.
python3 work/legacy-observation/local_validate.py
git diff --check 572b96756c1e2a6573be5b1887ccbb70cdcfe8a1 HEAD
```

The initial offline focused Maven run lacked ICU/libphonenumber dependencies; the configured-proxy retry
passed without build changes. Sandboxed Git/proxy and Docker-socket access initially failed; permitted
shell execution resolved those access constraints. They were not counted as test passes. Official
Microsoft pages linked above were fetched and read on October 15, 2026; they establish the documented
capabilities/limitations, not this deployment's runtime configuration.

**Unverified boundaries:** PowerShell/SQLCMD commands were not executed on native Windows in this task;
Windows cleanup is statically reviewed, not runtime certified. Azure-specific event availability/payloads,
DMV service-tier authority, actual target mapping, failover/restart coverage and installed 1.0.136 acceptance
remain NOT RUN. This bounded alternative cannot certify Azure nonuse; retirement/installation stay BLOCKED.


## Captured legacy login behavior and separate retirement candidate — 2026-10-15

**Current decision: retirement candidate ready for review; installation remains BLOCKED.**
The complete private `legacy-login.txt` capture collected **2026-10-15 20:30:25.8430398 UTC**
was reviewed, including definitions, SET flags, parameter metadata, ownership, dependencies,
principal/role context and exact ACL states/grantors. It supplements the original prior-state
capture; neither raw capture nor unrelated principal/permission inventory is committed.
Live `codex/latest` was fetched at `9a812953028b8f58876172ecc39d3d9b502cdd85`; GitHub metadata
and Git ancestry verify merged #1873. Separate branch: `codex/legacy-login-retirement-20261015`.
Earlier unknown-definition statements below describe the evidence available to those earlier revisions.
This section supersedes them for these three captured procedures only.

| Captured fact | Consequence |
| --- | --- |
| All three accept only `@email nvarchar(320)`, compare `LOWER(email)` without tenant qualification, and accept `is_deleted IS NULL` | Duplicate emails can affect multiple tenants; removed status is not checked. |
| get_hash checks for any future lock first, then unordered `TOP (1) password_hash` | One matching account can suppress all results; ambiguity is hidden. No password verification occurs. |
| mark_failure updates every eligible match, increments the existing value, and uses AuthConfig id 1 with NULL/missing defaults 5/15 | Expired locks do not reset the counter; threshold failures assign a new lock deadline. NULL counters stay NULL. No lockout policy change is proposed. |
| mark_success updates every eligible match to zero failures, NULL lock and current last_login | A caller can clear locks and stamp successful-login accounting without presenting credentials or authenticated identity. |
| CALLER context, NULL explicit object owner, effective dbo ownership, no captured signatures/triggers/inbound dependencies; outbound Users and AuthConfig only | Same-owner ownership chaining skips table permission checks. CALLER and direct Users DML DENYs do not confine these capabilities. Static metadata is not an external/dynamic caller inventory. |
| Auth has exactly three object EXECUTE G/GRANT rows, grantor dbo | A narrow exact-state REVOKE is possible only after independently approved caller retirement. W/D/R or other grantors are rejected rather than rewritten. |
| Runtime has database EXECUTE plus role dbo-schema EXECUTE | REVOKE on auth does not retire runtime access; table read fences do not block the hash procedure or accounting writes. |

The test resources contain **only the three reviewed procedure bodies**, normalized to LF in Git and
restored to captured CRLF before SQL creation. Their SQL Server SHA-256 hashes must equal:

```text
get_hash     0x1B0D6F1117829EA94326E04EF955243EC7BF8110692C550876F1ADBA1C9D7453
mark_failure 0x08AF95D876F0DBA465DE97AB5A7371544604B64B916B464722DA5D040F374112
mark_success 0xA76F60DAEA06E046D46A7CAB99770C819449474AA8A214DD27C13F5EB24C0C6E
```

### Exact permission delta and approval boundaries

`retire-legacy-login.sql` is a **separate pre-Stage-A operator candidate**, never included by an
installer or preflight. Its one transaction removes the three captured auth object EXECUTE grants
**AS dbo** and adds exactly six object EXECUTE DENYs **AS dbo**, one for each procedure on each of
`shale_runtime` and `shale_runtime_role`. This explicit runtime scope is required to defeat the reported
inherited EXECUTE and future role members; broad database/schema grants remain unchanged. No module,
user data, AuthConfig value, session context, remembered credential, RLS predicate, role or membership
is edited. This does not establish least privilege: runtime's direct Users access, broader schema
CRUD/EXECUTE and audit permissions remain independent findings.

Before mutation, an operator must retain and reference all of the following privately:

1. Security-owner approval specifically for retiring **all three auth and runtime capabilities** and
   the reopened-risk rollback, distinct from Stage A/B approval. Establish actual installed desktop,
   server, job and connection identities, original-login/server-token authority and exact artifact hashes.
2. A complete caller inventory covering installed 1.0.136/older clients, server versions, jobs,
   integrations, stored wrappers, dynamic SQL, synonyms and external consumers. Repository source absence
   and empty dependency metadata do not establish absence of use. Any required caller must first be
   migrated through separately reviewed compatible work; this candidate does not create a substitute.
3. A bounded observation/drain record for the inventoried consumers with a documented start/end,
   longest scheduled-job interval and connection/session drain, owners and zero remaining invocations.
   Do not invoke these procedures against Azure to create evidence. Use already-approved private
   operational/configuration/telemetry records; a missing observation source remains a blocker requiring
   a separately approved telemetry collection plan, not guessed zero usage.
4. Compatibility evidence tying unchanged password/bcrypt and remembered flows to the installed
   artifacts and actual configured principals, including 1.0.136's direct Users read and old remembered
   resolver. This can justify retiring unused grants while retaining old-client reads; it cannot justify
   disabling a deployed/external consumer. If accounting/lockout callers are active, stop for their own
   migration/disposition rather than silently bypassing or redesigning their policy.
5. Exclusive serialized operator control of security/module metadata during baseline collection,
   apply and rollback. No concurrent DDL/permission editing; the scripts do not impose a global DDL lock.

Acknowledgements and nonempty evidence references record these decisions; they do not prove them.
**Those records have not been supplied. No live retirement or installation is approved by this PR.**

`legacy-retirement-baseline.sql` is a bounded read-only catalog/hash action on the existing approved dbo
metadata connection. It pins the captured bodies/SET flags/parameters/owners, rejects signatures,
Users/AuthConfig triggers, inbound static callers and matching synonyms, and verifies the four outbound
dependencies. It outputs a single database-specific catalog digest. Retain it independently only after
reviewing the associated current private catalog evidence; never calculate an expected hash inside the
mutation invocation or replace an original failed baseline with current state merely to pass a guard.
It does not replace the original prior-state capture. Its digest covers exact permission/grantor/state,
principal SID/owner, membership, schema/object ownership, module hashes/SET/execution flags, parameters/column/type metadata,
signatures, dependencies, synonyms, triggers and security policies/predicates. Application rows and
server/login authority are intentionally outside this metadata digest. Changed/unsupported evidence stops.

Apply refuses drift before editing and compares the complete expected ACL delta plus unchanged other
metadata before commit. Auth authority diagnostics remain unchanged and must pass after revocation;
runtime effective checks must show no escalation and no legacy EXECUTE. A same-owner wrapper can bypass
nested procedure DENY, so known inbound callers are rejected and external/dynamic paths remain an inventory
gate. Later/new wrappers or authority changes require renewed review; the DENYs are not a sandbox for
arbitrary dbo modules. Run full unchanged Stage A preflight separately after retirement, with its original
identity/security-owner gates. A successful retirement does not authorize installation.

### Windows operator commands and the remaining bounded action

**Next action now:** have the security owner complete the five-item evidence review above and record a
per-procedure, per-auth/runtime-consumer retire-or-migrate decision. If the records cannot establish no
remaining dependent callers, stop here; provide the missing consumer/telemetry plan for separate approval.
No Azure call, permission change or installation is part of this Codex task.

Once separately authorized, from the reviewed repository root use the existing approved connection
array and private output paths. SQLCMD variables accept trusted fixed literals, safe change-control IDs
and emitted hex digests only; never interpolate quotes, SQL, credentials, URLs or private contents.
Do not put invented evidence references in these variables. Use one SQLCMD connection per script and
stop on every error; SQLCMD exits/closes the connection so an uncommitted transaction rolls back.

```powershell
# Read-only; retain the output privately. No approval of mutation is implied.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql -v ExpectedDatabase=Shale -o $privateLegacyBaselineOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: captured legacy baseline is incompatible or incomplete' }
# Set $legacyBeforeHash from the independently reviewed output, never an inline current-state query.
# Fill these safe IDs only from approved private change-control records.
$legacyApproval = @('ExpectedDatabase=Shale', 'ReviewedIdentityMapping=CONFIRMED', 'LegacyRetirementApproval=APPROVED', 'CallerInventoryApproved=APPROVED', 'LegacyCallersDrained=CONFIRMED', 'RuntimeLegacyDisposition=DENY_THREE_MODULES', 'ExclusiveChangeWindow=CONFIRMED', "CallerInventoryEvidence=$inventoryRecordId", "CallerDrainEvidence=$drainRecordId", "CompatibilityEvidence=$compatibilityRecordId")
$legacyApply = $legacyApproval + @("LegacyBeforeHash=$legacyBeforeHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/retire-legacy-login.sql -v @legacyApply -o $privateLegacyRetirementOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: retirement failed; verify unchanged catalogs before any next action' }
# Independently save AfterHash as $legacyAfterHash from that successful output.
# Recheck the actual configured auth/runtime login tokens and effective permissions, without invoking legacy modules.
# Existing Stage A approval remains separate; @review is the previously evidenced original acknowledgement set.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightRetestOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation remains blocked' }
```

Afterward, actual auth must have zero effective EXECUTE on each legacy procedure and retain direct Users
SELECT/old resolver EXECUTE. Actual runtime must have zero EXECUTE on all three and retain approved
noncredential projections/password-management writes. Review original-login/server-token and dynamic
wrapper authority separately; database-user impersonation tests do not establish those facts.
No legacy procedure execution against Azure is requested, including as a postcheck.

### Stage B disposition and exact rollback

The three retired auth grants **stay absent** and all six runtime DENYs **stay present through Stage A,
Stage B and rollback of either stage**. The existing scripts already preserve unrelated permissions; no
Stage B allowlist or guard change is needed. Stage B's full compatible fleet/job drain, caller inventory,
module identities and RLS prerequisites remain independently mandatory. The legacy retirement's smaller
caller drain is not a substitute for the Stage B old-client read drain. Neither a Users SELECT DENY nor
runtime hash-column DENY would prevent these procedures' internal access; this is why the explicit
EXECUTE retirement is required before considering Stage B complete for this profile.

`rollback-legacy-login.sql` supports only the captured original **G/GRANT AS dbo** profile. It requires
separate rollback approval, compatibility/reopened-capability review, the original independently saved
BeforeHash and AfterHash, and the same inventory/drain/exclusive-window evidence. It rejects extra target
ACLs, changed grantors/states/SIDs/metadata and missing hashes. It removes exactly six new DENYs AS dbo,
replays exactly the three original grants AS dbo without GRANT OPTION, then requires the **entire original
catalog digest** before commit. No CASCADE, grantor substitution, broad grant replay or module edits.
Unsupported original W/D/R/other-grantor states fail closed rather than using this rollback.

Rollback order: while Stage A/B exist the legacy rollback intentionally refuses; drain/roll back their
consumers and use their own guarded rollbacks first. If any later unrelated catalog change remains,
AfterHash will still differ: stop for separately reviewed reconciliation, never overwrite saved hashes.
Review the reopened hash disclosure, unauthenticated accounting and inherited runtime risks before
restoring access. Failed rollback closes the connection and rolls back its complete transaction.

```powershell
$legacyRollback = $legacyApproval + @('LegacyRollbackApproval=APPROVED', 'RollbackCompatibilityReviewed=CONFIRMED', "LegacyBeforeHash=$legacyBeforeHash", "LegacyAfterHash=$legacyAfterHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-legacy-login.sql -v @legacyRollback -o $privateLegacyRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: exact legacy rollback refused; do not replay grants manually' }
```

Audit compatibility: this is a security-catalog change, not an application business-data mutation or
sensitive application read. Existing PHI/entity-action/session-security transactions and schemas remain
unchanged; use private operator change control for approval, source hashes, evidence and catalog digests.
Do not place raw metadata, bodies, SQL, credentials or account data in application audit rows.

### Verification of this candidate

Pre-edit test-impact inventory: existing auth lookup/module/lifecycle, remembered store/migration,
User creation/removal/projection and `AuthVerificationSupportTest`, the full permission fixture, local SQL
harness, launcher and security-data selector ownership were inspected. No runtime auth symbols or DTOs
change. The expanded fixture runs captured behavior first, then the actual retirement/rollback scripts,
then the existing unchanged Stage A/B and password/remembered/isolation scenarios. Retired modules remain
present through that roundtrip, preventing accidental test-only removal of the capability blocker.

All accounts, AuthConfig values, table shapes and calls in the fixture are **synthetic**. Only the three
hash-verified module bodies reproduce the capture. Coverage includes duplicate emails across tenants,
NULL/deleted/removed eligibility, threshold/expiry/default/NULL accounting, unauthenticated success,
ownership-chain read/update despite direct DML DENY, runtime inherited EXECUTE after object REVOKE,
future role-member denial, caller/dependency/approval/hash/ACL drift refusal, rollback of all nine ACL
changes on postcheck failure, and exact original G/W/D/R/other-grantor preservation outside the target delta.
Native Windows execution and installed 1.0.136 acceptance of this revision remain NOT RUN.



Linux results for this revision (Maven 3.9.11 / JDK 21; SQL Server 2022 pinned to the digest
already recorded below, task-owned container bound only to 127.0.0.1:14333):

| Check | Result |
| --- | --- |
| Focused auth/module/lifecycle/store/User tests | 38 PASS; no failures/errors/skips. |
| Committed-base repository selector / affected security-data suite | 54 PASS; no full-suite escalation. Auth, tenant/session and neighboring audit/transaction contracts are relevant to permission tooling. |
| Critical reactor `mvn test` | 116 PASS; no failures/errors/skips. |
| Final disposable SQL fixture / public quiet launcher | All three PASS lines; exit 0. Captured-body behavior, retirement/rollback and existing Stage A/B roundtrip execute before the current password/remembered/isolation scenarios. |
| Real SQLCMD `-b -V 16` | PASS: missing drain 58343/exit 16; post-mutation authority failure 58347/exit 16 with complete rollback; drift 58344/exit 16; wrong final rollback identity 58346/exit 16 with no reopened grants; exact original digest restored. |
| Python auth / selector / unchanged Case launcher | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL execution. |
| Captured-body UTF-16 hashes, SQL includes/aliases, runbook links/fences, scope and Git checks | PASS. Original preflight, Stage A/B, RLS, runtime authentication and remembered-credential code unchanged. |

Commands executed from repository root (`mvn` uses the existing configured JDK/proxy/CA wrapper;
`work/` helpers provide private synthetic credentials and exact nonce-owned local resources only):

```bash
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 work/legacy-login/local_sql.py test
python3 work/legacy-login/sqlcmd_roundtrip.py
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base 9a812953028b8f58876172ecc39d3d9b502cdd85 --head HEAD --format markdown --plan-output work/legacy-login/selection.json --run
# The selector runs its printed affected Maven command, followed by mvn test.
python3 work/legacy-login/local_sql.py launcher
git diff --check 9a812953028b8f58876172ecc39d3d9b502cdd85 HEAD
```

The first new SQL attempts exposed empty-catalog XML NULL serialization and synthetic grant-option
cleanup requirements. Empty catalog serializations now have explicit canonical empty roots, and an
independently enforced non-NULL digest check; effective-permission UNKNOWN handling is unchanged.
A definition-drift injection was made explicit rather than relying on CREATE/ALTER spelling. Private
SQLCMD setup also needed captured SET flags and exact UTF-16 definition transport. Final runs above
passed after those corrections; failed attempts are not acceptance. Private diagnostic helpers were
removed from test sources. The final documented Windows launcher procedure remains available below;
**native Windows execution, installed 1.0.136 acceptance and actual-login checks remain NOT RUN**.
No Azure connection, live permission change, installation, release, RLS change, merge or deployment
occurred. Only task-owned disposable SQL resources were removed. Caller/drain/compatibility evidence,
independent owner approval and the existing Stage A/B/RLS/security gates remain open.

**Historical #1873 blocker:** the operator identified three `sp_login_*` EXECUTE grants after #1872.
The [current assessment and one bounded dbo query](#identified-1872-blocker-three-legacy-login-executes--2026-10-15)
keep installation blocked pending deployed definitions/authority and accounting/caller evidence.

**Review candidate; deployment BLOCKED.** Base `7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0`
was fetched from live `codex/latest`; merged #1868, #1869 and #1870 were verified with GitHub metadata
and Git ancestry. Separate branch: `codex/auth-module-permission-cutover`.
The [Case security runbook](case-read-local-security-verification.md) retains the original schema evidence,
Case verification procedure and historical execution records. This document owns the permission candidate.
No Azure connection, live grant/migration, client release, configuration change, merge or deployment is
part of this task. No three-table FILTER or other predicate is added/removed. Original schema export,
Case harness and accepted R4 reports remain unchanged; no substitute export is generated.

## Attributed operator evidence

Reported by the user in the October 15 task, not executed by Codex against Windows/Azure:

```text
PASS credential_identity
PASS remembered_rotation
PASS runtime_isolation
Exit code 0
```

This closes the requested **native Windows synthetic auth SQL retest** after #1870, including its
reported successful exit. Tool versions, JVM timezone and exact Windows source/artifact hashes were not
supplied; none is invented. It does not establish this new permission candidate's Windows execution,
installed application compatibility, complete production-schema parity or security acceptance.

User-run Azure Shale metadata/effective-permission evidence:

| Observation | Consequence |
| --- | --- |
| Database collation `SQL_Latin1_General_CP1_CI_AS` | Case-insensitive identifiers resolve AuditLog/auditLog; exact email comparisons retain this collation. No collation/index/data rewrite. |
| `shale_app`: no db_owner/ShaleAdmins; Users SELECT, old resolver EXECUTE, database CONNECT | Current capability supports older password/remembered consumers. Catalog names do not establish actual server/desktop configuration mapping. |
| `shale_runtime`: no db_owner/ShaleAdmins; member `shale_runtime_role` | Both principal and role need an explicit module fence. |
| Runtime and role: dbo schema CRUD; runtime database EXECUTE, role dbo schema EXECUTE | Installing dbo owner-executed procedures alone exposes them to runtime. An object REVOKE does not remove inherited EXECUTE. |
| Runtime effective Users.password_hash SELECT/UPDATE and auditLog SELECT/INSERT/UPDATE/DELETE | Hash disclosure and mutable audit history are real permission findings. Read FILTERs alone would not repair either. |
| V1/V2 absent; old resolver caller-executed, dbo-owned | New-name installation can preserve all old object definitions/ACLs and their grantors/states. |
| Users/auditLog/Statuses have no RLS predicates | Confirmed gap persists; this task must not attach the three FILTERs. |

**Actual deployed server and desktop auth/runtime credential configuration is still UNCONFIRMED.**
No independently approved security-owner acceptance of dbo module authority/internal hash access is implied.

## 58307 investigation after #1871 — 2026-10-15

**Operator-reported evidence, not a Codex Azure/Windows execution:** the expanded native Windows
fixture produced all three PASS lines and exit 0; prior-state capture succeeded with exit 0 and is
retained privately. Azure preflight stopped at 58307. On the actual `shale_app` connection, database
CONTROL, ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, dbo schema ALTER/EXECUTE, dbo user IMPERSONATE and
database EXECUTE all returned 0. Auth has Users SELECT, old resolver EXECUTE, CONNECT and an explicit
database VIEW DEFINITION DENY, without db_owner/ShaleAdmins. **Stage A has not been installed; employees
still use desktop 1.0.136.** Preserve that captured state and the original schema; neither is regenerated
or committed here. The expanded Windows report supersedes the pending #1871 fixture retest for that
reported revision only; this diagnostic revision has not been run natively on Windows by Codex. These
observations do not enumerate effective column permissions or every token path.

**Disposable result:** the reported auth grants **including VIEW DEFINITION DENY** pass preflight and
the existing install/postcheck/restrict/rollback fixture on SQL Server 2022. DENY alone does not reproduce
58307. No Azure root cause or extra live permission is established; no live permission change is proposed.
The new output names the decision inputs so the operator can distinguish an actual extra capability from
UNKNOWN evidence. This is a diagnostic/tooling change, not security acceptance or cutover authorization.

Every auth condition formerly combined in the 58307 OR list now contributes to one shared diagnostic
relation and shared fail-closed decision. It checks:

| ConditionName | Authority tested / reported securable |
| --- | --- |
| role / fixed-role | db_owner, ShaleAdmins, every fixed database role in the effective token except public |
| database / database-permission | ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, CONTROL, EXECUTE; every fn_my_permissions database capability outside CONNECT and the two existing column-encryption metadata exceptions |
| dbo-schema / dbo-user / impersonate-user | dbo ALTER/EXECUTE, dbo IMPERSONATE and other impersonatable database users; roles are not USER securables |
| ownership | schema and explicit object owners matched against the complete effective token |
| grant-option | every W-state permission matched to the token, including class/major/minor IDs, entity/column where applicable, grantee and original grantor |
| column | every dbo-inventoried column UPDATE, and every non-Users column SELECT |
| object | ALTER/CONTROL/INSERT/UPDATE/DELETE, non-Users SELECT and EXECUTE outside the three existing resolver/V1/V2 exceptions; includes callable elevated/signed/encrypted modules without printing bodies |

Token output includes public and nested roles. Ownership, grant-option, principal, object and column
inventories are captured under dbo before impersonation; filtered caller catalogs are not proof of absence.
Database VIEW DEFINITION's raw effective value and matching token ACLs are context, not a requirement to
grant metadata visibility. Effective 1 is GRANTED, 0 is NOT_GRANTED, SQL NULL is UNKNOWN. Any GRANTED guard
input or UNKNOWN stops with 58307. No unknown is coerced to a safe zero or mislabeled a grant. The database
user test still does not replace actual-login/server-token review. Serialize metadata changes during probes.

Output is metadata only: no application query, hash, SID, secret or module body. It contains at most 200
finding details and 200 token rows, grouped finding counts, total counts and explicit truncation flags.
Truncation leaves the investigation incomplete; retain private output and narrow the affected condition in
a separately reviewed read-only follow-up before drawing conclusions. No broad permissions should be
removed to make the count disappear. Dbo-known absence of the optional ShaleAdmins role is distinguished
from a NULL membership result for an existing role.

### Next bounded operator query and exact retest commands

From repository root, on the **existing approved dbo metadata connection**, run this read-only diagnostic
before proposing permission changes. It does not require new security-approval acknowledgements and does
not include capture, installation or restriction scripts:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/diagnose-auth.sql -v ExpectedDatabase=Shale -o $privateAuthDiagnosticOutput
$authDiagnosticExit = $LASTEXITCODE
# A nonzero exit with 58307 is expected when GRANTED/UNKNOWN findings exist. Stop and review them.
if ($authDiagnosticExit -ne 0) { throw 'Stop: review bounded auth diagnostics; do not change permissions or install' }
```

`diagnose-auth.sql` is the bounded read-only query artifact: only SET, temporary catalog snapshots,
EXECUTE AS/REVERT and metadata/effective-permission queries. It works before Stage A. It reports the same
auth inputs as preflight and restores dbo before reporting/throwing. Preserve the prior-state capture as-is;
do not rerun capture merely to replace the original failure evidence. Share only approved condition,
securable, permission, outcome and truncation metadata, never retained private captures/module definitions.
If all inputs are zero, compare the exact script revision, selected database and connection identity with
the failed run; do not assume that permission state or original-login authority is unchanged.

After reviewing the diagnostic, retest only the read-only preflight with the **previously evidenced**
review acknowledgements (do not assert unapproved decisions):

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightRetestOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight still blocked' }
```

No Stage A/B, release or permission-change command is authorized by this investigation. The native Windows
synthetic retest uses the launcher commands in the Windows section below; use a dedicated local disposable SQL
instance, never Azure. Installer, restriction and rollback paths include the same preflight. Existing
module postchecks already reject NULL EXECUTE with fail-closed COALESCE; read postchecks reject NULL forbidden
column access and NULL required writes. The expanded fixture exercises these paths under the metadata DENY.

## Caller review and bounded decision

### Identified #1872 blocker: three legacy login EXECUTEs — 2026-10-15

**Operator-reported Azure result:** `shale_app` has database VIEW DEFINITION DENY; the effective token
contains `public` and `shale_app`. The diagnostic reports 3299 NOT_GRANTED, 3 GRANTED and zero UNKNOWN.
The three findings are object EXECUTE on `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure` and
`dbo.sp_login_mark_success`; SQLCMD exits 16 / error 58307. This identifies the guard inputs, not their
definitions, grantors, execution authority or safety. It supersedes the earlier unresolved-condition
statement above. **Stage A remains NOT installed, desktop 1.0.136 remains in use, installation BLOCKED.**
Codex has made no Azure connection or live change.

Fetched live `codex/latest` at `cfc62956b209265d7c56829b5e8e1af3773c99c1` (merged #1872), with complete
available Git history; separate task branch `codex/1872-legacy-auth-evidence`. The initial sandboxed
Git fetch could not reach its proxy. A GitHub API fetch verified the complete base tree and signed commit;
the subsequent approved shell fetch/unshallow confirmed that same live base and supplied history.

| Evidence reviewed | Finding and limit |
| --- | --- |
| Current tracked repository and available history, exact searches for all three module names | No definitions or callers found before this investigation. The schema reference names `failed_attempts`, `lock_until` and `last_login`, but supplies no accounting algorithm or module authority. No original schema export is available in this task workspace; none was regenerated or inferred. |
| Release commit `c2c77830` (`Release Shale 1.0.136`), `shale-data/.../auth/AuthServiceImpl.java` | Direct parameterized `SELECT TOP 1` from Users by email with `is_deleted=0`; `password_hash AS pw_hash`; bcrypt verification in Java. No lockout check, failure increment, success reset or last-login update in this path. Its retry counter is connection retry only. |
| Released `DesktopUiAuthService.login`, shared auth adapter and server auth controller | Desktop delegates to the shared auth service, then stages the separate server credential exchange. No `sp_login_*` call is present. This is source evidence; installed binary hashes, actual configured identities and external clients/jobs remain unverified. |
| Current `AuthServiceImpl` and `LookupAuthenticationCredentialV1` | New bounded credential lookup plus Java bcrypt; no new lockout/accounting algorithm. Neither current nor released source proves that deployed modules/triggers/external consumers are unused. |
| Ownership/execution/permission authority of the three Azure procedures | **Unknown.** dbo schema/name does not establish explicit object owner, CALLER versus OWNER/named execution, signatures, chaining, parameter semantics or grantor/state. Even CALLER modules can reach same-owner data through ownership chaining. |

**Decision:** these are unreviewed executable capabilities. Evidence does not yet support classifying
them as necessary narrowly scoped legacy capabilities, obsolete grants, or unsafe implementations.
Do not whitelist by name, revoke them, edit their bodies, or relax 58307. No production permission or
authentication correction is justified yet. Preserve deployed lockout/failure/success behavior by making
no change to it. Repository absence is not permission-removal evidence.

#### One bounded read-only deployed-evidence query

On the existing approved **dbo metadata connection**, from repository root, the operator can run:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/inspect-legacy-login.sql -v ExpectedDatabase=Shale -o $privateLegacyLoginEvidence
if ($LASTEXITCODE -ne 0) { throw 'Stop: legacy module evidence incomplete; installation remains blocked' }
```

This single script performs only catalog reads, session SET and temporary metadata tables; it does not
impersonate, invoke modules, query application rows, alter permissions, capture a replacement prior state,
or install anything. Full Unicode definitions and SHA-256 hashes are private review evidence. Parameter
types/directions/lengths are returned, never parameter default values. Module owners (explicit, schema,
effective), CALLER/OWNER/named execution, SET flags, signer identities/thumbprints without keys/signature
bytes, direct inbound/outbound dependency metadata, related owners/triggers, complete database-principal
and nested-role context, and exact applicable permission class/major/minor IDs, G/W/D/R states,
grantors and grantees are included. ACLs cover database, related schemas/objects/columns, USER/type
authority and all explicit grants to auth/runtime, module owners/execution identities/signers and their
nested roles, including authority beyond direct dependencies. Fixed-role and dbo authority is not
limited to explicit ACL rows. Server/login authority still needs the existing actual-connection review.

Bounds are explicit: three bodies of at most 1 MiB each, 200 dependency records/related objects/signatures/
triggers, 6303 parameter rows, 1000 principals/membership rows each and 5000 relevant permission rows.
Over-limit evidence stops with 58330 rather than truncating. Missing/encrypted/non-T-SQL modules report
their status then stop with 58331. SQLCMD must retain complete `-y 0 -Y 0 -u` output. Serialize catalog
changes during collection. An exit 0 means collection succeeded, **not preflight approval**. Definitions
may contain sensitive embedded literals; review/redact privately before sharing. No application rows,
credential values from application storage, SIDs, key material or parameter defaults are returned.

#### Evidence/action required before any correction

1. Review those deployed bodies/parameters/SET flags, explicit/effective owners, execution identities,
   signatures and exact ACL/grantors. Trace every dependency, synonym, dynamic SQL, trigger and external
   caller; catalog dependencies are not a complete dynamic/external call graph. Obtain separately bounded
   private evidence for unresolved/transitive bodies or trigger behavior rather than assuming absence.
2. Establish installed 1.0.136 artifact/configuration mapping and jobs/older consumers. Verify how credentials
   are selected and how failed attempts, threshold, duration, expiry, deleted/removed users, successful login,
   last-login updates and concurrent attempts are accounted for. No live authentication execution is requested
   by this metadata step. Obtain approved behavior evidence; do not invent a lockout policy from names.
3. Classify each module. If necessary and safe, a subsequent correction must pin independently reviewed
   deployed definitions/SET semantics, parameters, owners, execution/signature authority, exact ACL states/
   grantors and permitted caller/role closure; reject drift, broad or grant-option authority and unexpected
   dependencies. Reproduce the **actual reviewed behavior** in disposable SQL, including lockout boundaries,
   concurrent failure/success accounting and released-client compatibility, before permitting a capability.
   If obsolete or unsafe, obtain a separately reviewed consumer migration/drain and exact-state permission/
   module correction with rollback. No blanket REVOKE or guessed GRANT replay is acceptable.
4. Keep the original private prior-state capture and original schema export unchanged. Supplement them with
   this timestamped evidence; never overwrite them. Any later authorized change requires its own exact
   delta, independent baseline hashes/signature and grantor/state constraints, drift refusal, failure atomicity
   and exact rollback. Recheck deployed metadata and actual auth/runtime connections before Stage A.

Stage B still requires complete caller inventory and compatible installed-fleet/job drain, including these
three modules if used. A Stage B table SELECT fence alone must not be claimed to block a same-owner procedure's
internal read or update. Any admitted legacy path requires an explicit Stage B disposition and regression tests.
The current candidate remains blocked with these grants; none of its staged scripts, prior-state capture,
schema export or RLS gates has been changed. The existing rollback commands do not authorize modifying these
three deployed procedures. **This revision's rollback is reverting its query/test/documentation commit;
there is no database delta to undo.**

#### Test impact and verification boundary

Pre-edit review covered `AuthCredentialLookupTest`, `AuthUserLifecycleSecurityTest`,
`PreTenantAuthenticationModuleContractTest`, `AuthPermissionCutoverFixture`,
`PreTenantAuthenticationLocalSqlServerTest`, `SqlRememberCredentialStoreTest`, remembered migration,
User creation/removal/projection tests, the auth launcher tests and the selector's security-data inventory.
Only the fixture, new metadata script and this runbook change. Shared login, bcrypt, account lifecycle,
password-management SQL, historical installers, capture and RLS definitions are unchanged.

The disposable fixture creates all three exact names with explicitly **synthetic** parameterized bodies,
grants each EXECUTE as dbo, and calls them under the metadata-denied auth principal. It requires diagnostic,
preflight and installer to reject all three at 58307 before module installation; harmless-looking names/bodies
are never adopted. It exercises the new dbo collector, full definitions/hash/parameters/owners/ACL grantors,
missing-module rejection, execution/definition drift, and certificate signer authority beyond dependencies.
It then removes only its synthetic probes and compares exact original permissions/memberships/predicates
before the existing staged roundtrip and credential/remembered/isolation checks. These probes reproduce the
grant blocker and collector behavior; **Azure module behavior/lockout reproduction remains NOT RUN until
the deployed definitions and behavioral evidence arrive.** Synthetic counts vary with fixture schema and
are not asserted equal to Azure's 3299 negative checks. Native Windows execution of this revision remains
NOT RUN. No Case SQL or Azure execution is part of this revision.

Audit compatibility: private security metadata collection adds no business mutation, application sensitive
view or audit schema. Existing application/security audit transactions remain authoritative. Retain approved
metadata privately in operator change control; do not log bodies, credentials or SQL into application audits.

Linux verification for this revision uses the same SQL Server 2022 digest recorded below, a new task-owned
container bound only to `127.0.0.1:14333`, generated synthetic credentials, and random nonce/SID-guarded
databases/logins. No operator-retained resource or original export was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/User tests | 38 PASS. |
| Selector affected security-data/server suite | 54 PASS; no full-suite escalation. These preserve auth/lifecycle and neighboring transaction/audit/session contracts selected for SQL security tooling. |
| Critical `mvn test` | 116 PASS. |
| Expanded disposable SQL fixture and final public quiet launcher | All three scenario PASS lines, exit 0. Exact three GRANTED / zero UNKNOWN; all three entry points reject 58307. Missing/encrypted bodies reject 58331; oversized evidence rejects 58330 before output. Original ACLs/memberships/predicates and staged rollback checks pass. |
| Real SQLCMD `-b -V 16` | Three exact EXECUTEs report GRANTED and exit 16 / 58307; collector exits 0; a missing module exits 16 / 58331. Fresh independently nonce-guarded synthetic database removed. |
| Python auth / selector / unchanged Case launcher tests | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| Changed-file/test review, SQL metadata/include/scope/alias, runbook links/fences, Git whitespace | PASS; only the collector, fixture and owning runbook changed. |

Commands executed from repository root (`mvn` uses the existing `/workspace/.tools/bin/mvn` proxy/CA/JDK
wrapper; `work/` helpers are private local orchestration, not application configuration):

```bash
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 work/local_sql.py test
python3 work/sqlcmd_probe.py
python3 work/local_sql.py launcher
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base cfc62956b209265d7c56829b5e8e1af3773c99c1 --head HEAD --format markdown --plan-output work/committed-selection.json --run
git diff --check cfc62956b209265d7c56829b5e8e1af3773c99c1 HEAD
```

The public Windows launcher procedure below reproduces the committed Java fixture; the local helpers
only supply private synthetic credentials and test real SQLCMD. An initial offline Maven dependency miss
(`libphonenumber`) was resolved through the configured proxy; no build change was needed. Passing local
checks validates this evidence tooling and continued blocking, **not the deployed procedures or their
lockout behavior**. Installation remains BLOCKED; security acceptance OPEN.

Repository-wide credential-field/SQL/call-site search at this base found:

- `AuthServiceImpl.login` uses V1 through `DataSources.auth()` for shared desktop/server password checks;
  BCrypt remains in Java. No inline SELECT fallback. Server desktop exchange repeats this verification.
- `SqlRememberCredentialStore.lookup` uses V2 on auth, then principal-initialized runtime performs the
  serializable owner/session/JTI revalidation and rotation. Creation/deletion also use runtime.
- `UserDao` directory/detail/management projections list noncredential fields explicitly. User creation
  inserts `password_hash`/`password_alg`; administrative reset updates both and revokes sessions on the
  same connection. These writes must remain available. No blanket runtime Users SELECT/UPDATE denial.
- Minimized/shared Case projections, User directory/Team/detail, selectors and session/profile readers
  consume explicit User identity/display/eligibility fields. Their original projections are preserved.
- Older installed desktops can still execute the former direct auth SELECT. External jobs, custom
  modules, installed source versions and actual configuration cannot be inventoried from this checkout.

Stage A adds object EXECUTE to `shale_app` and object DENY EXECUTE to **both** `shale_runtime` and
`shale_runtime_role` for V1/V2. One outer transaction encloses the unchanged #1868 installer, its nested
transaction, permissions and effective postchecks. Newly privileged modules are never committed with
an exposure window. Do not run the historical module installer alone in this permission profile.
No existing broad grant is revoked or rewritten. Explicit module ACL shape and exact expected permission/member deltas are checked before commit; original module definitions/owners are preserved too. No role gains dbo/ShaleAdmins/ALTER/CONTROL/impersonation.

Stage B is separately gated on compatible fleet/job drain and approved caller inventory. It creates
new deny-only roles, requiring their names to be absent:

| New role / members | Exact restriction |
| --- | --- |
| `ShaleAuthTableFenceV1` / shale_app | DENY Users SELECT, remembered-table SELECT and old resolver EXECUTE. New V1/V2 remain executable through their confined owner scope. |
| `ShaleRuntimeHashFenceV1` / shale_runtime and shale_runtime_role | DENY SELECT on Users.password_hash only. Other User projections, INSERT and password_hash/password_alg UPDATE remain. |

SQL Server column GRANT can override table DENY. Stage B snapshots all User/remembered column names as
dbo, checks every column as auth and aborts/rolls back if any remains readable. It never silently revokes
that column grant. Runtime's column DENY is tested against a direct column GRANT as well as inherited
schema CRUD. This is not a restriction on every legacy credential-related column or a repository-wide
least-privilege rewrite. Remaining credential fields and alternate elevated modules require inventory.
Stage A intentionally retains old auth SELECT/resolver compatibility; Stage B intentionally ends it.
Do not run Stage B merely because the synthetic auth test passed.

### Verification of this diagnostic revision

Fetched live base `bfca3e3346aa1b4abeb357788484d655b9087a7e` (merged #1871); isolated branch
`codex/diagnose-1871-auth-preflight`. Codex executed Linux/local disposable checks only, using the same
SQL Server 2022 image digest recorded below, a newly created task-labelled container bound to
127.0.0.1:14333, random nonce/SID-guarded fixture databases/logins and private synthetic credentials.
No retained operator resource, prior capture, original schema or Azure connection was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/migration/User tests | 38 PASS, zero failures/errors/skips. |
| Committed-base selector affected security-data/server suite | 54 PASS, one explicit opt-in SQL skip; modified fixture compiled. No full-suite escalation. |
| Critical `mvn test` | 116 PASS, zero failures/errors/skips. |
| Expanded disposable Java SQL and final quiet launcher | All three PASS lines, exit 0; reported CONNECT/Users SELECT/old resolver EXECUTE/VIEW DEFINITION DENY retained through staged roundtrip and exact ACL/member/predicate comparisons. |
| Negative diagnostic probes | Database/schema EXECUTE, fixed/ShaleAdmins membership, column UPDATE/non-Users SELECT, public/nested inheritance, W state, schema ownership, user impersonation and elevated module EXECUTE each reject 58307 and name a GRANTED condition. Unsupported permission probe returns real SQL NULL, reports UNKNOWN and fails the shared decision. |
| Real SQLCMD `-b -V 16` standalone diagnostic | Baseline exit 0 with metadata DENY shown; injected Users.email UPDATE reports GRANTED and exits 16 on 58307. Disposable injection removed. |
| Python auth / selector / unchanged Case launcher | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| SQL include, privacy/scope, alias, changed-file and Git checks | PASS. No capture/historical SQL/schema/RLS/Case-tooling changes; new diagnostics have no permission mutations, module definitions, hashes or application SELECTs. |

The first offline Maven attempts lacked cached dependencies (libphonenumber and later jna-platform);
configured-proxy retries passed without build-file changes. Early fixture development exposed a W-state
test cleanup requirement, a result-decoder collision with grouped condition counts and the fact that
missing securables under denied metadata can return 0 rather than NULL. The final UNKNOWN test uses an
unsupported permission to obtain a real NULL; no missing object is asserted to be denied by that test.
Dbo inventories, explicit prerequisites and serialized metadata changes remain essential. These failed
attempts are not counted as acceptance. Existing SQL postchecks passed under the DENY without guard removal.

Exact local commands (the private `work/` helpers supply only task-local credentials and expand the
committed SQL; reproduce the Java coverage using the documented public quiet launcher):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base bfca3e3346aa1b4abeb357788484d655b9087a7e --head HEAD --format markdown --plan-output work/auth-58307/selection.json
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn test
python3 work/auth-58307/run_fixture.py
python3 work/auth-58307/check_sqlcmd.py
python3 work/auth-58307/run_launcher.py
git diff --check bfca3e3346aa1b4abeb357788484d655b9087a7e HEAD
```

Audit compatibility: diagnostics read only security metadata and add no application/business mutation or
sensitive application view, so no application audit event or schema migration is introduced. Retain
approved diagnostic evidence in private operator change control. **The Azure 58307 cause remains
unresolved until the bounded diagnostic is run there by the operator. Deployment BLOCKED; security
acceptance OPEN; desktop 1.0.136 compatibility and the original prior state remain preserved.**

## Exact operator preflight and staged next steps

These are **future separately authorized operator actions**, not instructions to connect during this task.
Use a reviewed checkout and an existing approved dbo-capable migration connection. Never place passwords,
JDBC URLs, tokens or hashes of application credentials in command arguments/output. `sqlcmd` authentication
uses the operator's existing approved mechanism; do not invent a server/login or reuse local credentials.
Run from repository root, one connection per script, SQLCMD mode, stop on first error (`-b -V 16`).
An SSMS runner that continues GO batches after failure is unsuitable. Retain complete private output with
no display truncation; definitions/SIDs/ACL metadata are change-control evidence, not public chat output.

1. **Resolve configuration first.** Privately inspect effective server and each desktop configuration's
   URL/user precedence (including aliases/system properties/packaged sources), without sharing values.
   On each already-approved actual auth/runtime connection run the SELECT-only identity/token/effective
   catalog handoff in the Case runbook. Establish same intended database and distinct actual principals
   matching `shale_app`/`shale_runtime`; verify original/effective login and nested/public/fixed/server
   authority. Repeat for jobs and supported older builds. Different mappings block these fixed-identity
   scripts; do not substitute guessed names. Record exact compatible server/MSI/source hashes and fleet.
2. **Security owner reviews** the bounded dbo modules, internal exact-email hash capability and offline
   hash-attack risk, duplicate-email rejection (including inactive/NULL-owner duplicates), module owners,
   signing, dependencies and plans/scanned work. Review complete existing module definitions, runtime
   escalation/impersonation/server authority and any elevated wrapper/dynamic SQL paths. Preflight rejects
   executable elevated, signed or encrypted user modules outside V1/V2; static/deferred caller wrappers
   are rejected by module postchecks because same-owner chaining can skip a nested procedure DENY; unexpected authority needs separate disposition.
3. **Metadata/effective preflight.** Only after steps 1–2 are evidenced, run:

   ```powershell
   sqlcmd <approved connection options> -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v ExpectedDatabase="Shale" ReviewedIdentityMapping="CONFIRMED" SecurityOwnerApproval="APPROVED" -o <private-preflight-output>
   if ($LASTEXITCODE -ne 0) { throw 'Stop: auth cutover preflight failed' }
   ```

   The angle-bracket fields are operator-selected local paths/approved connection options, not literal
   arguments. Preflight changes only session execution context/SET options and temporary metadata tables; no permanent setting or grant changes. It requires dbo metadata visibility, no ambient
   transaction, expected database/collation/owners/principals/old resolver and zero three-table predicates.
   It tests actual database-user effective permissions; server/login-token review in step 1 is still
   required, particularly for sysadmin/CONTROL SERVER not represented by database-user impersonation.
4. **Retain prior evidence before authorization.** Export the full SELECT-only catalog handoff and
   exact old resolver definition/hash/owner, principal SIDs/role closure and every applicable permission
   row including class/major/minor IDs, grantor, grantee and G/W/D/R state. Preserve original grantor
   principals. Run `capture.sql` on the approved metadata connection and save its complete output, then
   close it (its #temp tables are session-local). Capture new-name absence separately. Store source,
   script hashes and exact client rollback artifacts privately. Missing/truncated metadata blocks work.
5. **Stage A, later authorization:** run `install.sql` with the same `-v` values and stop flags, saving
   complete output. It captures all prior database permissions/memberships/predicates/known module
   definitions before mutation, then asserts every original permission/grantor/state and membership
   remains and predicates are identical. Save the two emitted `DefinitionHash` values independently
   for Stage B/rollback. A preexisting module name stops; there is no overwrite/no-op/adoption path.
   On error the runner must close its connection so the open transaction rolls back; never continue.
6. **Actual-connection postchecks, before clients:** as actual runtime, verify both new module EXECUTE
   effective permissions are 0 and invalid-input calls receive permission error 229; as actual auth,
   verify both are 1 and run approved authentication smoke checks without collecting hashes/results.
   Verify caller identity/context restoration on success/failure. Reinspect owners/definition hashes,
   exact ACLs/grantors, original old resolver, memberships and all predicates. Database-user impersonation
   is useful but does not replace original-login checks on real connections. Stage A retains direct auth
   Users SELECT and old resolver EXECUTE; record this transitional exposure explicitly.
7. **Compatible releases, later authorization:** modules/permissions precede server and desktop artifacts.
   Test password login (A/B users), second credential exchange, enrollment, /me, issuance/refresh/logout,
   checked/unchecked remembered login, Windows restart/ordinary close, rotation/replay/outage Retry,
   revocation/disabled-account rejection, DPAPI unavailable behavior, user switching and pooled context.
   Verify User directory/Team/detail/assignments/history and creation/reset/revocation. Retain the exact
   deployed artifact hashes and minimum compatible fleet/drain evidence. No release is made by this PR.
8. **Stage B, only after full inventory/drain and separate authorization:** run `restrict-reads.sql` with
   the same values plus `CompatibleFleetDrained="CONFIRMED" CallerInventoryApproved="APPROVED"`
   `LookupDefinitionHash="<saved Stage A hex>" RememberDefinitionHash="<saved Stage A hex>"`.
   Save complete prior evidence and emitted `FenceSid` values. Failures roll back both roles/memberships.
   Rerun actual-connection checks: auth direct User/remembered reads and old resolver denied; V1/V2 work;
   runtime both modules and hash SELECT denied; original noncredential projections and credential writes
   work; runtime audit INSERT remains. Confirm no prior permission/grantor/state/member/predicate changed.
9. **Separate next decisions:** keep targeted three-FILTER/candidate-schema work deferred until installed
   compatibility, actual permission checks and owner approval pass. Later prepare original/derived/candidate
   hashes/manifests from the unchanged export and run the unchanged Case harness. No synthetic auth pass
   substitutes for Case SQL or deployed RLS/grant/performance/security acceptance.

Use `module-postcheck.sql` and, after Stage B, `fence-postcheck.sql` plus `read-postcheck.sql` as repeatable metadata/effective checks
on the approved dbo connection. Their checks are supplemented by actual calls above; empty filtered rows
are not permission-denial evidence. A repeat mutation script fails closed; inspect/reconcile rather than
replaying it. Serialize operator changes during capture/install/rollback; no concurrent permission editing.

### Operator command sequence (after each preceding approval/evidence gate)

Use the existing approved connection arguments as a PowerShell array `$approvedSqlcmdOptions`; no
connection or secret values are provided by this PR. Define private output paths and use the saved
hashes/SIDs from the prior successful stage. The explicit `-y 0 -Y 0 -u` flags preserve complete Unicode
catalog/definition output. Do not add `-W` or `-h` with `-y 0`; SQLCMD rejects those combinations.
The acknowledgement values are recorded decisions, not ways to bypass missing evidence.

```powershell
$review = @('ExpectedDatabase=Shale', 'ReviewedIdentityMapping=CONFIRMED', 'SecurityOwnerApproval=APPROVED')
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight failed' }
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/capture.sql -o $privatePriorCatalogOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: prior capture failed' }
# Stage A requires its own authorization after reviewing the capture above.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/install.sql -v @review -o $privateInstallOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation failed' }
# Save $lookupDefinitionHash / $rememberDefinitionHash from that successful output.
# Release acceptance and complete old-client/job drain occur BEFORE Stage B authorization.
$readCutover = @review + @('CompatibleFleetDrained=CONFIRMED', 'CallerInventoryApproved=APPROVED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/restrict-reads.sql -v @readCutover -o $privateReadCutoverOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read restriction failed' }
# Save $authFenceSid / $runtimeFenceSid from that successful output.
```

Rollback commands, only after the compatibility/risk decisions below:

```powershell
$readRollback = @review + @('RollbackCompatibilityReviewed=CONFIRMED', "AuthFenceSid=$authFenceSid", "RuntimeFenceSid=$runtimeFenceSid")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-reads.sql -v @readRollback -o $privateReadRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read rollback failed' }
$installRollback = @review + @('NewModuleConsumersDrained=CONFIRMED', 'RollbackCompatibilityReviewed=CONFIRMED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-install.sql -v @installRollback -o $privateInstallRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation rollback failed' }
```

Use only independently saved values, never query current state to invent a missing expected identity.
The scripts fail closed on missing/empty values. SQLCMD variables are trusted operator inputs: retain the
literal database/principal profile and use emitted hex hash/SID values, never arbitrary text/SQL.

For the candidate's **new Windows synthetic tests**, use the existing dedicated local SQL instance and
fresh guarded terminal described in the Case runbook, then:

```powershell
python -m unittest discover -s build/auth-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Auth launcher tests failed' }
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333 --maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'
if ($LASTEXITCODE -ne 0) { throw 'Candidate SQL remains unverified; retain fixed failure/detail pairs' }
```

All three PASS lines plus exit 0 now include the permission fixture under setup, followed by the existing
credential, remembered-rotation and pooled isolation checks. Earlier user PASS results certify their
original run only. Leave retained earlier resources/container intact; new test runs create fresh random
resources and use the existing exact nonce/SID cleanup guards. Do not weaken environment guards.

## Exact rollback

Keep additive modules while any consumer depends on them. Rollback never disables TenantFilter, edits shared
functions, restores a broad historical grant script, rewrites data, deletes audit history or rotates secrets.
Durable-session signing/cutoff/max-TTL constraints in the App Service guide remain independent requirements.

- **Failed batch:** stop/close the single SQLCMD connection. Its outstanding transaction rolls back. Confirm
  new-name absence or unchanged prior stage and compare the saved complete catalog snapshots.
- **Stage B rollback:** review which installed artifacts will depend on restored direct access and document
  the reopened hash-read risk. Run `rollback-reads.sql` with preflight variables, separately saved
  `AuthFenceSid` and `RuntimeFenceSid`, and `RollbackCompatibilityReviewed="CONFIRMED"`. It validates exact
  SIDs, dbo role owners, three exact membership rows, four DENY rows and original dbo grantor, no role
  nesting/extra grants or grantor dependencies, then removes only those newly created memberships/roles.
  Existing grants (including other grantors/W/column R exceptions) were never rewritten and remain exact.
  Compare all prior permission/member/predicate rows to the saved Stage A baseline before client rollback.
- **Stage A rollback:** after Stage B rollback and every V1/V2 consumer drained/rolled back to known compatible
  artifacts, run `rollback-install.sql` with preflight values, both saved Stage A definition hashes,
  `NewModuleConsumersDrained="CONFIRMED"` and `RollbackCompatibilityReviewed="CONFIRMED"`.
  It rejects changed definitions/owners/ACL/grantors, signatures and static dependencies. It removes only
  the two originally absent procedures and their six newly created ACL rows. Old resolver and every old
  permission/grantor/state/member remain. Dynamic/external dependency drain is an operator requirement.
  Verify restored older password/remembered flows and exact baseline catalogs. Unknown drift means stop
  for exact-state review; no CASCADE, broad REVOKE, grantor substitution or guessed GRANT replay.

If another separately authorized change has attached a Users FILTER, these scripts stop at preflight.
Coordinate that change's own compatibility/rollback disposition; never disable the entire policy.

## Audit compatibility and explicitly retained backlog

This permission change adds no business-data view or application mutation and no new audit schema/event.
Existing PHI/entity-action/session-security transactions remain authoritative; migration catalog evidence
belongs in operator change control, without credentials, emails, hashes, SQL payloads or raw logs in app audit.

**Audit permission remediation remains OPEN:** reported runtime auditLog SELECT/INSERT/UPDATE/DELETE means
history is **not database-enforced append-only**. This candidate preserves those permissions, including
INSERT needed by required Overview audit. A separate reviewed scope must inventory PHI/legacy writers and
administrative tools, preserve historical viewer behavior, establish active-admin authority below the UI,
reduce audit UPDATE/DELETE and ordinary SELECT compatibly, decide strict write BLOCKs and global Status
immutability, and verify failure/rollback. Audit-list SQL/bound-value stderr logging remains an independent
privacy finding. Shared desktop credentials/mutable SESSION_CONTEXT and broader schema CRUD/EXECUTE remain
trust-boundary debt; do not call this a full least-privilege/security acceptance.

## Verification record

Executed on Linux, Python 3.12.14, Maven 3.9.11, Temurin JDK 21.0.12.1 and the existing Microsoft
JDBC 12.6.1.jre11. `mvn` below denotes `/workspace/.tools/bin/mvn` with its existing proxy/CA settings;
no repository build/dependency configuration changed. SQL Server 2022 uses task-owned image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, published only to
127.0.0.1:14333. A fresh random database/logins and original nonce/login SIDs guard the Java fixture;
separate SQLCMD databases use an independently generated exact nonce. No Azure destination/credentials,
original schema input, Case SQL execution or operator-owned retained resource is used.

Pre-edit inventory covered existing auth/module/lifecycle, remembered migration/store/UTC diagnostics,
User create/reset/removal/projections, runtime, server exchange and desktop lifecycle tests. No existing
production caller or DTO expectation changes. The new helper executes the actual candidate scripts within
the existing opt-in harness; the unchanged quiet launcher still exposes only three PASS scenario lines.

| Check | Final result / boundary |
| --- | --- |
| Focused auth/module/store/User projection/password tests | 38 PASS, one explicit opt-in SQL skip. |
| Additional existing server/desktop/UI auth/session compatibility | 47 PASS. Synthetic regression evidence, not installed-client acceptance. |
| Committed base-to-head selector / affected suite | 54 PASS, one explicit opt-in SQL skip. Security-data plus compiled modified helper/harness; no full-suite escalation. |
| Critical reactor `mvn -B test` | 116 PASS, zero failures/errors/skips. |
| Auth launcher Python | 17 PASS, one explicit native-Windows skip. |
| Unchanged Case launcher / selector Python | 4 PASS / 24 PASS; no Case SQL. |
| Final disposable Java SQL / real quiet launcher | All three scenarios PASS; exact cleanup, exit 0. New broad-grant/cutover/rollback checks execute under setup before application scenarios. |
| Real SQLCMD `-b -V 16` | PASS: error-stop atomic installation failure, install/restrict/rollback roundtrip, exact catalog equality and nonce cleanup. |
| Documentation/scope/Git checks | PASS: relative links/fences, SQL includes, no bare RowCount aliases, exact unchanged Case tooling/original auth SQL/R4 ledger and whitespace. |

The fixture reproduces runtime direct database EXECUTE and both principal/role dbo CRUD plus role schema
EXECUTE. It proves installer-only and REVOKE-only exposure, both final module denials (229), public and
future-role-member inheritance, and same-owner wrapper bypass followed by explicit dependency rejection.
It tests missing configuration/fleet acknowledgements, installation postcheck failure with zero committed
modules, Stage B write-capability failure with neither role retained, column-GRANT behavior, exact original
G/W/D/R/non-dbo-grantor preservation, wrong SID/hash and changed-ACL rollback refusal, and complete staged
roundtrip. Auth direct User/credential reads and old resolver fail after Stage B; new modules/password and
remembered rotation work. Runtime noncredential projection and actual parameter-bound password updates
remain usable; broad audit SELECT/UPDATE/DELETE are deliberately still available. Every rejected stage
asserts dbo caller restoration and no open transaction. Synthetic Users RLS is added only after these
checks, preserving the existing Case/source-schema boundary.

Initial offline runs missed existing dependencies, resolved through the configured proxy without POM
changes. An overlapping Maven attempt produced a transient compile failure and was discarded; counted
final runs are sequential. The first critical attempt was blocked when an existing desktop test opened
a loopback HTTP socket; the unchanged suite passed with approved local socket access. Early candidate
SQL runs exposed an unsupported permission-name check, catalog/state collation comparison and THROW/
REVERT transaction behavior; these were corrected and rerun rather than weakening guards. Initial private
SQLCMD setup had file-access/header-flag errors; final real wrapper execution passed with complete output.
No failing attempt is counted as acceptance. Existing historical all-tests advisories remain unresolved;
no new all-tests run was required or claimed.

Exact commands executed (private helpers are task scratch tools, not application configuration; dedicated
local SQL credentials were supplied only through private prompts/child environment):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest,PreTenantAuthenticationLocalSqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dtest=AuthServiceAdapterTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SessionControllerTest,ServerAuthSessionLogoutTest,DesktopSessionEnrollmentLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopRuntimeSessionLifecycleTest,LoginRememberedSignInContractTest,AuthenticatedSessionLifecycleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 --head HEAD --format markdown --plan-output work/auth-cutover/selection.json
mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B test
python3 work/auth-cutover/run_sql.py
python3 work/auth-cutover/run_sqlcmd.py
python3 work/auth-cutover/run_launcher.py
python3 work/auth-cutover/check_docs.py
git diff --check 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 HEAD
```

The public Windows launcher procedure above reproduces the committed opt-in Java fixture; private helpers
only supply task-local credentials/capture fixed output or exercise real SQLCMD with fresh synthetic setup.
Passing disposable SQL does not close actual configured identity mapping, independent security-owner
approval, installed fleet/drain, deployed parity/permissions, Case SQL, broader audit/admin remediation
or other R4/security/host/device gates. **Deployment remains BLOCKED; security acceptance OPEN.**

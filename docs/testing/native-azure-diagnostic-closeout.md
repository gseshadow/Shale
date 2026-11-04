# Native Azure catalog investigation closeout and deployment decision

Operator decision recorded 2026-11-04. This is a documentation/status update to draft
PR [#1929](https://github.com/gseshadow/Shale/pull/1929), not deployment or SQL execution.
It supersedes earlier requests to refresh diagnostic packets or run more Azure fixtures.
**The Azure-specific investigation is stopped by operator choice. Native Azure catalog/read-only
acceptance is NOT fully passed.** Local acceptance, setup success and Windows validation retain
only their distinct scopes below.

## Final evidence

The operator reports that the full disposable Azure setup repeatedly passed every step:

- owner connect/preflight
- Stage 0
- schema import
- prerequisite migrations
- Phase1R/V2 prerequisite
- V2 install
- contained users
- deployment binding
- cumulative verify
- Stage 0 after
- OFF check

These are disposable Azure setup/V2 installation successes, not production installation or
restricted-verifier catalog/read-only acceptance. The remaining catalog diagnostic failure was
localized to exactly:

```text
VERIFIER_COMPONENT_READ; component=0:before_first_component; phase=BATCH_EXECUTE
```

The unchanged 19-component batch passed locally under a restricted database principal with
exactly the installer's six verifier grants. Application-table SELECT remained denied in that
restricted-principal local test. **No verifier permission defect was established. No permission
change is justified.** The [statement/authority review](native-azure-catalog-batch-review.md)
records the detailed local proof and its Azure limits.

The leading hypothesis for the Azure-only failure is that the verifier's **5000-ms socket timeout
may preempt its 20-second query timeout / first response**. This is an inference only, not a
proven Azure root cause. Local timeout experiments do not retrospectively classify that Azure
failure. Production SQL, catalog expressions, six verifier authorities and all timeout bounds stay
unchanged by this closeout.

The launcher bug that masked the real Surefire failure as REPORT_WRITE was fixed and
Windows-validated at `fceac929e1f9cfcf6626f26d602c1f5bd15b1ce5`: five Python catalog guards,
`RUNBOOK_DIAGNOSTICS PASS cases=208 azureExecuted=false`,
`RUNBOOK_CREDENTIALS PASS rounds=2 identities=5 partialFailureCleanup=true`,
`RUNBOOK_LAUNCHER PASS scenarios=18 azureExecuted=false`, and
`CATALOG_INVOCATION_WINDOWS_VALIDATION PASS` (operator-reported native Windows evidence).

The tiny Azure probe was implemented at
[`871919c263b650bd7dc805fef16cee7ddac35769`](https://github.com/gseshadow/Shale/commit/871919c263b650bd7dc805fef16cee7ddac35769)
and Windows-validated: Python tests passed, 21 launcher scenarios passed, and
`TINY_CATALOG_WINDOWS_VALIDATION PASS`. This supersedes that commit's preparation-time
Windows-pending status. **The tiny probe was NOT executed in Azure.** Its implementation is a
separate archived diagnostic branch; it is not a production migration or a prerequisite to a dormant
JAR release. No probe code is added to #1929 by this closeout.

A suitable dedicated nonproduction Azure SQL server did not exist. The only discovered server was
production `shale`. The operator explicitly chose not to create a dedicated nonproduction server
and not to run the tiny Azure probe. Do not substitute production, add diagnostics or resume the
fixture/probe loop. Historical executable packets are retained as evidence, not current next steps.

Consequently, from this investigation/closeout:

- no production SQL changed
- no production permissions changed
- no production deployment occurred
- no production AI/MCP/OAuth switches were enabled
- the Azure-specific BATCH_EXECUTE cause remains unresolved by operator choice
- the native Azure catalog/read-only gate must NOT be represented as fully passed

PR #1929 remains draft, open and unmerged. The disposable mutation/fault acceptance gate also
remains OPEN; successful setup does not close it. Closing the investigation is not closing either
acceptance gate. Production identity/catalog/deployed parity, live OAuth/MCP, SDK prerelease,
legal retention, operational/security/tenant-owner and pilot approvals retain their separate status.

## Minimum deployment gate and decision

**Conditional GO for a dormant server JAR; NO-GO for AI readiness/activation or a one-client MCP
pilot. No unconditional GO for production Phase1R/V2 SQL follows from disposable setup success.**

A fully passing native Azure catalog verifier is required before claiming native acceptance or
activating the confined AI serving/readiness path. It is **not a technical prerequisite to uploading
and starting a JAR with every AI/MCP/OAuth control, including SQL readiness composition, OFF**.
This separates an ordinary dormant release from the earlier umbrella SQL-bound deployment gate;
it does not waive an attestation, authority or pilot check or alter the architecture.

The existing code supports that separation:

- `McpCaseCompositionPolicy.boundaryRequired()` is false when readiness and serving controls are OFF.
- `McpTransportConfiguration.mcpAiCaseData` is conditional on readiness/serving activation.
- `McpCaseReadiness.state()` returns `disabled` without constructing/attesting the boundary.
- Existing `McpProductionOffBootTest` covers azure/prod Boot with no AI configuration/pool/handlers,
  ordinary health/authentication boundaries, disabled readiness and unavailable MCP/OAuth routes.
  This is existing local evidence, not a new live production smoke result.

The minimum remaining gate for that dormant release is the **ordinary release compatibility and OFF
configuration gate**: select the exact reviewed source and successfully built JDK 21 executable JAR,
retain its SHA-256/previous executable, reconcile its ordinary auth/session SQL dependencies against
existing production change records, and verify every effective AI control false (including external
App Service overrides). Use the existing local acceptance for unchanged code; no new diagnostic
campaign is requested. If the exact release differs in behavior, run its relevant release checks.
After an authorized upload/restart, complete ordinary health/login/session/remembered-restore and
OFF-route smoke; a failed smoke requires containment/rollback, not AI activation.

Ordinary dependency reconciliation is real, not an assertion that the seven AI migrations cover the
entire JAR. Current auth callers require the versioned authentication modules and their approved
Stage A fences; session/remembered dependencies remain those in the
[App Service compatibility table](../azure-app-service-deployment.md#release-dependencies-and-order--2026-10-21).
The October 20 legacy-procedure retirement report does not prove Stage A/B installation or exact
current JAR/fleet parity. Those facts are UNKNOWN where no existing record establishes them.
First use existing records; do not repeat the completed retirement or invent a new survey/rehearsal.
If a required ordinary dependency is actually missing, stop and review only that existing missing
change. There is no defensible unconditional production SQL list for unknown ordinary dependencies.
No new migration is proposed by this closeout.

For the smallest release, **defer Phase1R/V2 SQL as well as activation**, build/upload the compatible
JAR with all controls OFF, and defer the one-client MCP pilot until native Azure acceptance and the
other retained activation gates close. The proposed “required SQL -> JAR -> OFF -> defer pilot”
sequence is valid only when SQL preparation is separately chosen and its destination/compatibility
gates are satisfied. It is not the minimum necessary dormant release: Phase1R changes ordinary RLS
regardless of AI flags and should not be installed merely to upload inactive code.

## Exact SQL disposition

The recorded production baseline already has these seven scripts under `docs/sql/ai-integration/`,
in this historical order. **Do not rerun any of these installers**, including for receipt repair:

1. `phase3b1.sql`
2. `phase3b2b.sql`
3. `phase3b2c1.sql`
4. `phase3b2c2.sql`
5. `revocation.sql`
6. `phase3b2d.sql`
7. `phase3b3.sql`

No diagnostic or Windows result changes that installed baseline. Preserve completed legacy-login
retirement; do not rerun it. Do not run historical `phase3b7-confinement.sql` as a repair: its production
representation refusal was correct. Do not import a fixture/schema into production or rewrite tables,
module definitions, hashes or receipts to fit the old fingerprint.

**Before the dormant OFF JAR alone: no new AI/MCP SQL script is required.** Phase1R and confinement
remain uninstalled in the recorded production state; full disposable setup does not install them there.

If production SQL preparation is separately selected, Phase3B.8 is the only new **AI confinement
installer** candidate from this work, but it is **not a one-script production change**: the existing,
uninstalled Phase1R three-FILTER candidate must precede it. The actual V2 prerequisite hashes the
Case/RLS catalog including those predicates. `AiCaseDataV2LocalSqlServerTest.beforeConfinement`
explicitly proves that the pre-Phase1R production representation fails V2, then applies Phase1R
PREFLIGHT/APPLY/POSTCHECK before V2. OFF flags do not bypass that prerequisite.

After reconciling existing destination receipts, independently reviewed Phase1R database/collation/
function/unrelated-predicate inputs, ordinary auth/desktop/job compatibility and the migration window,
the exact existing SQL sequence is:

| Order | Existing script | Effect |
| --- | --- | --- |
| 1 | `docs/sql/phase1r/preflight.sql` | Read-only guarded destination review; stop on mismatch |
| 2 | `docs/sql/phase1r/apply.sql` | Add only the three reviewed FILTERs; own Phase1R receipt |
| 3 | `docs/sql/phase1r/postcheck.sql` | Verify exact FILTER/receipt state and unchanged unrelated predicates |
| 4 | `docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql` | Read-only authoritative Case/RLS prerequisite; stop on any finding |
| 5 | `docs/sql/ai-integration/phase3b8-confinement.sql` | Install V2 using the existing grants, catalog contract and receipts |
| 6 | `docs/sql/ai-integration/verify.sql` | Cumulative verification; installed V2 selects V2 exclusively |

`docs/sql/phase1r/engine.sql` is included by the Phase1R entry points, not a separate deployment step.
`phase3b8-confinement-verify.sql` is the standalone verifier; cumulative `verify.sql` already embeds
its V2 verification, so no extra installer/migration is inferred. Use existing fail-fast SQLCMD/TLS/
fresh-session rules; stop on every failure. No executable production packet/values are generated here.

Phase1R requires compatible installed ordinary callers before FILTER attachment. Stage A/B status
must not be guessed from legacy retirement. If an existing auth module/fence stage is missing and
chosen, its own guarded ordering/approvals and compatible-caller drain precede Phase1R; do not blindly
rerun `docs/sql/auth/permission-cutover/install.sql`, `restrict-reads.sql` or the bare historical module
installer. Stage B is an independent permission restriction, not an automatic extra 3B.8 migration
or a JAR-upload prerequisite. Its preflight deliberately refuses already-attached FILTERs.
If a compatible server/desktop rollout is still needed, do that dormant rollout before Phase1R;
there is then no defensible universal “SQL first” order.

A separately approved SQL-first rollout, **only after those gates**, is: the six entries above ->
verify SQL global/tenant capability still disabled -> build/package and upload the exact compatible
JAR with every control OFF -> ordinary/no-disclosure smoke -> stop. It does not require creating
AI contained identities, provisioning AI secrets, writing a deployment binding, or turning readiness
ON merely to release inactive code. Those remain part of later separately governed AI acceptance.
Passing owner/cumulative SQL verification does not close the unresolved restricted native gate.

## Deployment decision table

“Can proceed” is a technical sequencing decision, not execution authorization in this task.

| Item | Current state | Production prerequisite | Can proceed now? | Reason |
| --- | --- | --- | --- | --- |
| Local restricted-principal catalog validation | PASS; exact six grants; application SELECT denied | None | Complete; retain evidence | No permission defect or justified grant change |
| Disposable Azure setup/V2 installation | Repeated PASS through OFF check | None; never adopt as production | Complete at setup scope | Does not prove verifier/read-only acceptance |
| Further Azure diagnostics/tiny probe | Stopped; Windows validation PASS; no tiny Azure execution | Dedicated nonproduction server would be needed; operator declined | No | Explicit stop decision; production is not a substitute |
| Seven installed AI migrations | Installed in recorded production baseline | Review existing receipts; no replay | Do not rerun | Already completed predecessors |
| Dormant server JAR build/upload | Source/local OFF behavior accepted; exact release/deployed ordinary parity still to reconcile | Reviewed artifact/build, ordinary SQL compatibility, effective all-OFF settings, release authorization and smoke | Conditional GO | No AI pool/attestation needed while readiness and serving stay OFF |
| Phase1R production change | Not installed in recorded baseline | Reviewed destination inputs, compatible ordinary callers and rollback/window approval | Not an unconditional GO | Changes ordinary Users/auditLog/Statuses RLS even with AI OFF |
| Phase3B.8 V2 production installation | Disposable setup PASS; production uninstalled | Phase1R postcheck, exact V2 prerequisite, existing seven receipts and governed SQL change | Conditional, after prerequisites | Only new AI confinement installer; not needed for dormant JAR |
| AI contained identities/secrets/binding and readiness | Production/live parity OPEN | SQL preparation, independent consumers and complete native/live attestation | Defer | OFF deployment is not a readiness claim; never relax attestation |
| OAuth/MCP/handlers/global/tenant/integration activation; one-client pilot | NOT AUTHORIZED | Native read-only and mutation/fault acceptance, production identity/catalog/binding parity, live ingress/OAuth and retained legal/SDK/security/operator/tenant approvals | No | Setup/local/Windows passes do not establish live acceptance |
| Ordinary authenticated REST/web/desktop release work | Independent of AI activation | Its own SQL/API/caller/audit/release acceptance | Yes, within those gates | Does not require completing MCP or running another Azure diagnostic |

## Controls and remaining blocked work

For a dormant release, explicitly keep these effective properties false:
`shale.ai.enabled`, `shale.ai.mcp.enabled`, `shale.ai.mcp.case-data-composition-enabled`,
`shale.ai.mcp.search-cases-enabled`, `shale.ai.mcp.get-case-overview-enabled`,
`shale.ai.oauth.composition-enabled`, `shale.ai.oauth.authorization-enabled`, and
`shale.ai.oauth.exchange-enabled`. SQL `AiProductCapability.Enabled` stays 0;
tenant policies stay missing/disabled; no real integration/grant/session/credential authority is enabled.
Do not supply a test/dev profile as an activation workaround. Preserve ordinary datasource/auth/session
configuration, secrets, CORS, TLS and audit behavior. Readiness should be `disabled`, not claimed `ready`.

Native Azure catalog/read-only acceptance and its BATCH_EXECUTE cause remain unresolved by operator
choice. AI readiness sign-off, real OAuth issuance/consent, MCP discovery/serving, either Case handler,
real tenant/integration enablement and the one-client pilot remain blocked. Native acceptance closure
is necessary, not sufficient: the independent production/live/operational/legal/SDK approvals above
also remain. No root-cause diagnosis is required for the dormant JAR, and no new diagnostic plan is
created here. Any later reopening is a separate operator decision, not this closeout's next step.

Audit compatibility: this update changes documentation/status only, creates no application read or
mutation, and changes no audit event, schema or transaction seam. No new test contract, grant,
permission, timeout, probe, migration or architecture is introduced. Existing executable diagnostic
fences/SQL/source/receipt pins are preserved.

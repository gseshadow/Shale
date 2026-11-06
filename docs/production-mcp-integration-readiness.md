# Production MCP integration readiness — 2026-11-06

Assessment baseline: `600723b92fe34731e1d6d25e598fc9e3cbfc2770`.
Production updates below are the operator's November 6 confirmations, not agent-executed checks.
This assessment supersedes older pending Stage B/Phase 1R status and rollout instructions in the
linked records. Their dated investigations and receipts retain their original scope.

**Stage B and Phase 1R production deployment: COMPLETE at the confirmed scope.**
**Production MCP readiness/serving and pilot: NO-GO pending the remaining gates below.**
There is no new evidence of V2 production installation or native Azure acceptance. No SQL, deployment,
configuration change, diagnostic rerun or MCP/OAuth activation is authorized by this assessment.

## Accepted evidence and completed work

| Item | Current disposition |
| --- | --- |
| Stage B authentication permission cutover | Operator-confirmed production installation successful; fence and effective-read postchecks PASS. Do not rerun `restrict-reads.sql` or its installation preflight. |
| Phase 1R | Operator-confirmed three-FILTER migration installed; POSTCHECK PASS. Users/auditLog use strict tenant filtering; Statuses uses tenant-or-global filtering under the existing policy. Do not rerun PREFLIGHT/APPLY as an outstanding migration. |
| Production ordinary caller compatibility | Operator-confirmed desktop/API smoke PASS for login, remembered login, Cases, user directory, statuses and authorized updates after deployment. Those flows are accepted; no repeat smoke campaign is requested. |
| Phase 1R supporting evidence | Existing function provenance, inventory hash, pool-reuse tests and authoritative local Case/RLS acceptance remain accepted by the operator. No recapture, rehash, function rewrite or new local campaign is required. |
| Earlier SQL and retirement | Seven AI predecessor installers and the October 20 legacy-login retirement remain installed/completed at their recorded scope. No replay. |
| Phase 3B.8 V2 local acceptance | COMPLETE under the final repeatability criterion: ten clean readiness runs; complete V1/V2, authoritative-fixture, client, fault and regression groups accepted. The original readiness timeout is an unreproduced historical transient; no root-cause fix is claimed. |
| Production Azure platform | Existing operator-reported Stage 0 PASS retained. This is platform evidence, not production identity/catalog attestation. |

The Phase 1R FILTER result does not establish general write-blocking or complete least privilege.
The ordinary shared runtime and the independent proof-bound AI boundary retain distinct trust models.
Functional smoke also does not identify an executable's SHA-256 or prove every unreported ACL.
These limits do not reopen accepted cutover/provenance/pool investigations or justify installing SQL.

## V2 and native Azure reconciliation

[V2 final local acceptance](testing/phase3b8-confinement-v2.md#final-local-acceptance-after-repeatability)
supersedes its earlier local BLOCKED statements. V2 accepts the reviewed production column-ID/module
representations without weakening the Case/RLS, ownership, dependency, authority or receipt checks.
Historical V1 correctly refused that representation; do not retry it or reshape production to fit it.
Production must explicitly select `shale.ai.data.contract-version=V2`; failure cannot fall back to V1.

The [native Azure closeout](testing/native-azure-diagnostic-closeout.md#final-evidence) reports repeated
**disposable** setup/V2-installation success through contained users, binding, cumulative verification
and OFF checks. It does not establish V2 installation in production. Native restricted catalog/read-only
acceptance remains **OPEN**, localized to
`VERIFIER_COMPONENT_READ; component=0:before_first_component; phase=BATCH_EXECUTE`.
Disposable Azure mutation/fault acceptance is separately **OPEN**.

No permission defect was established. The socket/query-timeout explanation remains a hypothesis;
no grant, timeout or SQL change is justified by it. Windows launcher/tiny-probe validation passed,
but the tiny probe never ran in Azure. The operator stopped that investigation and declined a new
nonproduction server/probe. Do not resume the archived packets, substitute production, or repeat
local/Windows campaigns. Stage B/Phase 1R success closes neither native gate. Reopening acceptance
work needs a separate operator decision; a stopped investigation is not an acceptance waiver.

## SQL installed versus still needed

| SQL item | Production state and remaining action |
| --- | --- |
| Stage B `docs/sql/auth/permission-cutover/restrict-reads.sql` | INSTALLED; postchecks PASS. No replay or rollback. |
| Phase 1R `docs/sql/phase1r/apply.sql` | INSTALLED; POSTCHECK PASS. Retain the accepted `Shale.Phase1R.v1` receipt and review inputs; no apply/preflight replay or receipt refresh. |
| `phase3b1.sql`, `phase3b2b.sql`, `phase3b2c1.sql`, `phase3b2c2.sql`, `revocation.sql`, `phase3b2d.sql`, `phase3b3.sql` under `docs/sql/ai-integration/` | Previously installed predecessors. MUST NOT rerun, including for receipt repair. |
| Legacy-login retirement and earlier ordinary auth/session/remembered foundations | Preserve existing accepted installations/change records. No new ordinary installer is identified by this assessment. Do not treat an unreported detail as a missing migration. |
| Historical `phase3b7-confinement.sql` | Not the production successor. Do not run as a repair or fallback. |
| `docs/sql/ai-integration/phase3b8-confinement.sql` | Still required for the production confined MCP path; no production installation confirmation exists. This is the sole remaining identified versioned migration installer in the known chain. |
| `phase3b8-confinement-prerequisite.sql` followed, after separately authorized V2 installation, by `verify.sql` | Existing read-only prerequisite and cumulative verification, not migrations. Stop on any finding; never refresh hashes/receipts or retry V1. They were not executed here. |
| Restricted identities, independent secrets and immutable `AiCaseDataDeployment` binding | Still governed provisioning/configuration steps; not additional identified migration files. No real authority is provisioned here. |

The future V2-only SQL path starts from the **already installed** Phase 1R and seven predecessors:
accepted production change records -> read-only V2 prerequisite -> separately authorized V2 installer
-> cumulative `verify.sql` -> confirm SQL capabilities remain OFF -> STOP.
Do not prepend Stage A/B or Phase 1R installers, rerun retirement, or import a fixture. A destination
finding stops review; it does not authorize repairing production. Preserve existing SQLCMD fail-fast,
validated TLS, fresh owner-session and secure-secret rules from the [preflight](production-mcp-deployment-preflight.md).

## Genuinely outstanding prerequisites

| Gate | What remains before its corresponding production step |
| --- | --- |
| Production V2 SQL preparation | Destination-specific V2 prerequisite and predecessor/Phase 1R record consistency, approved change/rollback window, then separately authorized V2 installation and cumulative verification. Local/disposable hashes cannot replace a production receipt. |
| Restricted identity and binding readiness | Governed contained AI/verifier identities and separate bootstrap identity, independent secret consumers, exact live SID/token/permission separation from ordinary identities, immutable issuer/resource/database/time-zone binding and complete live V2 attestation. No broad role/grant workaround. |
| Native Azure acceptance | Restricted catalog/read-only and separate disposable mutation/fault gates remain open. No production fault injection. The stopped diagnostic investigation is not the recommended next step. |
| Exact release and dormant deployment evidence | Reviewed source, successfully built executable JAR SHA-256, matching uploaded artifact, retained compatible rollback JAR and effective settings. The November 6 smoke is accepted for its listed flows; it does not supply a new MCP artifact hash, OFF-route check or settings attestation. Use existing records first. |
| Production ingress/OAuth/client acceptance | Actual canonical HTTPS origin/resource/issuer, trusted proxy/Host/Origin and payload-log policy, human consent/issuance/revocation and authorized client/tool scope behavior on the real deployment. Local two-client/TLS acceptance remains accepted but cannot establish these live facts. |
| Operations and activation approval | Monitoring, capacity/SLA, restart/failover/fleet controls and containment; SDK prerelease production approval; tenant/legal retention, security/operator/tenant-owner sign-offs and explicit monitored-pilot authorization. No approval is inferred. |

An ordinary compatible dormant JAR remains a separate conditional release: **no new AI SQL or native
acceptance is required merely to start it with all eight AI/MCP/OAuth/readiness switches OFF**.
V2, restricted identities and binding are prerequisites to the confined AI path, not that dormant
upload. The accepted ordinary smoke does not itself authorize a new release. Future readiness-only
composition also needs separate authorization and complete attestation; it is not granted by SQL install.
Keep global/tenant/integration authority disabled. No live OAuth issuance or MCP tool serving is approved.

## Single next bounded step

**Prepare and review one offline, V2-only production SQL change packet from the pinned baseline.**
It should reference the accepted Stage B/Phase 1R/predecessor records, pin the unchanged V2 scripts
and source receipt, specify the read-only V2 prerequisite -> V2 installer -> cumulative verification
sequence, preserve all capabilities OFF, and define stop/containment and change approval boundaries.
Use existing accepted destination inputs; mark missing deployment-specific fields for operator review
without collecting new production state. No Stage B, Phase 1R, predecessor or retirement replay belongs
in that packet. Its completion is a reviewable proposal, not execution or readiness/pilot approval.

This advances the remaining known SQL prerequisite while respecting the stopped Azure investigation.
It does not close the native acceptance blocker or authorize identities, secrets, binding writes,
deployment, readiness, OAuth or MCP activation. No further implementation defect is established by
the accepted evidence; a speculative permission/timeout fix is not the next step.

## Evidence and validation boundary

Repository sources were read at the exact baseline through GitHub because the supplied local checkout
was older. Evidence owners: [production preflight](production-mcp-deployment-preflight.md),
[V2 contract](architecture/phase3b8-confinement-v2.md),
[final V2 acceptance](testing/phase3b8-confinement-v2.md#final-local-acceptance-after-repeatability),
[native acceptance](testing/native-azure-readonly-acceptance.md),
[native closeout](testing/native-azure-diagnostic-closeout.md),
[ordinary release compatibility](dormant-server-release-compatibility.md), and the operator's
November 6 confirmations supplied with this task.

Documentation/status only. Existing production composition and OFF Boot tests were inspected as
source; accepted runtime tests/investigations were not rerun and no new runtime result is claimed.
No application read or mutation, audit event, schema, grant or transaction seam changes; no audit
migration is needed. Historical receipts, private provenance and exact SQL/source bytes remain intact.

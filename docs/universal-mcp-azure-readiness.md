# Universal MCP — Azure rollout readiness

Reviewed November 12, 2026. Source: `codex/latest` at
`426c7cfe500574e02a3b1007fa0de084f5ba2cff`, merged [PR #1959](https://github.com/gseshadow/Shale/pull/1959).
**Planning only. Production activation: NO-GO. Dormant JAR: conditional release candidate.**
No production SQL connection (including read-only), Azure inspection/change, deployment,
restart, activation, production-data test or merge was performed for this document.
Stages below are future checklists, not execution authorization. Keep production controls OFF.

## 1. Current verified state and evidence limits

| Evidence | Established state | Still unverified |
| --- | --- | --- |
| Repository and #1959 integration report | Accepted C2 implementation is merged; disposable SQL, stock Hermes DCR in two fresh profiles, CIMD and manual continuity have recorded passes. | Native Azure compatibility, deployed artifact/configuration and production acceptance. Those passes are reused, not rerun here. |
| [Azure deployment guide](azure-app-service-deployment.md) | Existing Linux App Service/Java 21; executable Spring Boot JAR uploaded through Kudu to `/home/site/wwwroot/shale-server.jar`; startup `java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure`; `PORT` defaults to 8080. | Current executable SHA-256 and effective startup configuration. No JAR CI deployment is established; desktop release scripts do not deploy the API. |
| [Operator discovery record](production-mcp-binding-operator-discovery.md), Nov 9 | Operator supplied App Service `shale-api`, resource group `Shale`, SQL `shale.database.windows.net` / `Shale`; reported Linux/OpenJDK 21.0.13, one replica, OS UTC, empty `AiCaseDataDeployment`, three dedicated contained identities verified PASS. | Actual running JVM zone, approved OAuth origin and Java/SQL time-zone binding; full live attestation. Do not repeat completed identity provisioning. |
| [Production preflight](production-mcp-deployment-preflight.md), Nov 6 | Seven AI migrations listed below installed; auth permission-cutover Stage B fence/effective-read postchecks PASS; Phase 1R installed/POSTCHECK PASS; ordinary login/remembered restore/Case/directory/status/update smoke PASS, all operator-reported. | Production confinement V2 installation was not confirmed in that baseline. Nov 9's binding-table observation does not establish a complete V2 receipt/catalog. Later Universal migrations have no production-installation evidence. |
| HTTPS/browser deployment docs | Recorded API origin `https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net`; React frontend uses Azure Static Web Apps, built with `VITE_SHALE_API_BASE_URL` (origin only). | Canonical MCP ingress. `https://api.myshale.com/mcp` is the product target, not proof of installed DNS/TLS/routes. No live MCP/OAuth exposure is established. |

Production state above means **attributed existing operator evidence**, not fresh live verification.
Unknown is neither absent nor permission to install. Historical pending statements in older runbooks
are superseded only for the specifically dated completed gates.

Ordinary SQL uses separate existing app/auth and runtime credentials/pools. Preferred names are
`SHALE_APP_DB_URL`, `SHALE_APP_DB_USER`, `SHALE_APP_DB_PASSWORD`, and corresponding
`SHALE_RT_DB_URL`, `SHALE_RT_DB_USER`, `SHALE_RT_DB_PASSWORD`; aliases are
`SHALE_APP_JDBC_URL`, `SHALE_APP_USER`, `SHALE_APP_PASS`, `SHALE_RT_JDBC_URL`,
`SHALE_RT_USER`, `SHALE_RT_PASS`. Preserve the existing mappings, validated SQL TLS,
`SHALE_AUTH_TOKEN_SECRET`, token TTL and `SHALE_AUTH_SESSION_BINDING_CUTOVER_AT`.
This is SQL-authenticated configuration; no managed-identity conversion is required or established.
Azure/prod trusts validated bearer identity, never development identity headers. Runtime leases
stamp tenant/user context; ordinary credentials must never become fallback Case capabilities.

### Hard activation boundary

The reviewed #1960 baseline rejected `azure`/`prod` and lacked production capability wiring.
The [runtime integration successor](architecture/universal-mcp-azure-runtime.md) replaces that
source-level ban with explicit V2/DCR_V3 configuration, separate contained capability providers
and existing live SQL verification. All controls remain default-OFF. This is not deployment or
native Azure acceptance; the SQL, identity/binding, egress, ingress/NAT, browser, capacity and
approval gates below remain open. Never mix dev+azure profiles or deploy fixture providers.

## 2. Exact prerequisites and SQL inventory

Before SQL preparation: recover existing change records and exact receipt/source versions; approve
an isolated Azure target and synthetic data; obtain the unchanged private authoritative fixture
for its acceptance tests (SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`).
Never reconstruct or commit that fixture, or copy production data into it.
Before production SQL: separate authorization, compatible caller inventory/drain, full destination
schema/permission visibility, reviewed packet hashes, correct contained identities and an approved
immutable origin/time-zone binding. No production SQL commands are to be run under this task.

Existing dependencies: dbo-owned `ShaleClients`, `Users` and tenant-qualified Users key;
`UserSessions`/`ApplicationInstances`; `DesktopRememberCredentials` for ordinary callers;
`EntityActionAuditLog`, `GlobalControlPlaneAuditLog`; enabled schema-bound `sec.TenantFilter`
and reviewed strict predicate; authoritative `Cases`, `CaseUsers`, `CaseStatuses`, `Statuses`,
`PracticeAreas`, `auditLog` definitions and RLS. Preserve soft deletion and Statuses overlay semantics.
Resolve ordinary dependencies from existing records using
[dormant release compatibility](dormant-server-release-compatibility.md), not installer replay.
Ordinary session foundations are `2026-09-29_user_sessions_foundation_phase7a.sql` and
`2026-09-29_session_security_audit_phase8a.sql`; remembered storage is
`2026-10-05_desktop_remember_credentials.sql` plus its Oct 6 permissions history.
Current bounded auth callers require `auth/permission-cutover/install.sql`'s Oct 14 V1/V2 modules
and six ACL entries; the bare Oct 14 installer is not a substitute for that packet.

All names in the following table are relative to `docs/sql/ai-integration/` unless prefixed.
This is the dependency order for a **reviewed destination packet**, not a batch to replay.

| Order | Installer / prerequisite | Purpose and postcheck | Existing production evidence |
| --- | --- | --- | --- |
| 0 | `../phase1r/preflight.sql` → `apply.sql` → `postcheck.sql` | Three-table tenant FILTER prerequisite; restrictive to old callers even with MCP OFF. | Installed/POSTCHECK PASS Nov 6. **Do not rerun.** Retain auth fences/Oct 20 retirement too. |
| 1 | `phase3b1.sql` | Foundation, policies, integrations, consent, sessions, quotas, roles, receipts/audit allowlists. | Installed, operator report. Do not rerun. |
| 2 | `phase3b2b.sql` | Durable OAuth authorization/browser intent/code. | Installed; do not rerun. |
| 3 | `phase3b2c1.sql` | Exact code exchange/S256 binding. | Installed; do not rerun. |
| 4 | `phase3b2c2.sql` | Access/refresh issuance and rotation. | Installed; do not rerun. |
| 5 | `revocation.sql` | Durable OAuth revocation. | Installed; do not rerun. |
| 6 | `phase3b2d.sql` | Account/security lifecycle revocation. | Installed; do not rerun. |
| 7 | `phase3b3.sql` | Case read/quota/access-audit foundation. | Installed; do not rerun. |
| 8 | `phase3b8-confinement-prerequisite.sql` → `phase3b8-confinement.sql` | Reviewed Azure-compatible Case confinement V2; then `verify.sql` / `phase3b8-confinement-verify.sql`. | Unconfirmed; inventory before proposing installation. Historical `phase3b7-confinement.sql` V1 refused the production representation; never retry it as repair. |
| 9 | `deployment-binding/discover.sql`, `preflight.sql` → separately approved `install.sql` → fresh `postcheck.sql` | Exactly one immutable Id=1 `case-read-v1` tuple, exact issuer/resource and verified JVM/SQL time mapping. Follow [binding packet](production-mcp-deployment-binding-readiness.md) for inputs/include paths. | Empty table reported Nov 9; insert unexecuted in record. Do not infer UTC from OS/SQL clocks. |
| 10 | `universal-pr3-authority-inventory.sql`; `universal-pr3-confinement-prepare.sql` | Read-only authority inventory, then additive registry/confinement supplement; requires installed confinement, existing binding, capability OFF and pinned predecessor verification. | Not established. Preparation is a mutation, not a read-only probe. |
| 11 | `universal-pr3.sql` | External application/revision/tenant approval registry; check `universal-pr3-verify.sql` and `universal-pr3-confinement-verify.sql` at this version. | Not established. |
| 12 | `universal-pr4a-proof.sql` | Separate CIMD proof/credential/invocation namespace; `universal-pr4a-proof-verify.sql`. | Not established. |
| 13 | `universal-pr4b-oauth.sql` | CIMD metadata/browser/issuer/resolver lifecycle; `universal-pr4b-oauth-verify.sql`. | Not established. |
| 14 | `universal-pr-a-reviewer.sql` | Fixed read-only receipt accessor and exact metadata-review role. | Not established. |
| 15 | `universal-pr-b-onboarding.sql` | Original-WEB-session-bound own-tenant onboarding projection. | Not established. |
| 16 | `universal-pr-c1-external-v2.sql` | Source-aware EXTERNAL_V2, immutable stored DCR metadata, new roles/marker; preserves old receipts through explicit successor comparisons. | Not established. |
| 17 | `universal-pr-c2-dcr-v3.sql` | DCR_V3 admission/registration/status, durable limits/cleanup, two reviewed trigger deltas; explicit V3 verification below. | Not established. Development acceptance only. |

The Universal installers are guarded additive candidates, several explicitly disposable/development-only.
They are not a production-approved change packet. A present/partial successor must be investigated;
never rerun an installer, overwrite a receipt, change column IDs or refresh hashes to bless drift.

### Versioned verification and permissions

Run each predecessor check **before advancing**. The final deployed composition must select the actual
successor, not indiscriminately run every frozen historical verifier against a newer catalog:

| Layer | Required contract |
| --- | --- |
| Case | Explicit `shale.ai.data.contract-version=V2`; `Shale.AiCaseDataV2` and `.Source`, complete live identity/catalog/binding attestation. `verify.sql` selects Case V2, not the complete Universal/DCR chain. |
| Registry/CIMD V1 | Registry supplement plus PR3 receipt; additive proof/OAuth receipts and `cimd-authorization-v1-catalog.sql`. Historical verifiers remain frozen. |
| C1 | Explicit `AiExternalContract.EXTERNAL_V2`; packaged `registry-external-v2-catalog.sql` and `external-authorization-v2-catalog.sql`. V1 rejects a V2 installation. |
| C2 final | Explicit `AiExternalContract.DCR_V3` consistently in predecessor, Case and lifecycle capabilities; `registry-dcr-v3-catalog.sql` and `authorization-dcr-v3-catalog.sql` under `shale-data/src/main/resources/ai/`. Retains C1 EXTERNAL_V2 procedure/Case namespace. No fallback on verification failure. |

There is no standalone C2 operator `*-verify.sql`; use the existing version-selected Java attestation
and packaged batches through reviewed capability providers. Final read-only verification packaging
for the destination is a remaining packet requirement. Raw resource execution alone does not prove
actual identity tokens, provider separation, deadlines or complete runtime composition.
Do not turn on readiness with default V1 selection over installed V3 SQL just to test it.

Installers create empty roles, not users, memberships, credentials or pools. Separately review/provision
only existing capability contracts; keep exact EXECUTE allowlists and direct-table denials:

- Foundation: `AiIntegrationRuntime` (bounded tenant/OAuth operations), `AiIntegrationBootstrap`
  (provisional resolvers), `AiCapabilityOperator` (global inspect/change); registry
  `AiApplicationPublisher` (publish only), `AiApplicationTenant` (request/decide only).
- Case: `AiCaseDataRuntime` has six `AiData*V1` EXECUTEs and direct/legacy denials;
  separate `AiCaseDataCatalogVerifier` has VIEW DEFINITION, dependency-view SELECT and four
  reference-table reads. Azure EngineEdition 5 requires contained SQL users, explicit database,
  no server login/master grants; own user/public/exact role only, no ownership or broad roles.
- CIMD V1: `AiCimdCaseRuntimeV1` (six Case roots), `AiCimdLifecycleV1`,
  `AiCimdMetadataPublisherV1`, `AiCimdOAuthBrowserV1`, `AiCimdOAuthIssuerV1`,
  `AiCimdOAuthResolverV1`, `AiCimdOAuthNeutralV1`, separate `AiCimdOnboardingRuntimeV1`.
- Reviewer: `AiCimdAuthorizationCatalogV1` has exactly VIEW DEFINITION, SELECT on
  `sys.sql_expression_dependencies`, EXECUTE on `AiCimdReadAuthorizationReceiptsV1`.
  No direct binding/receipt/Case SELECT. Do not combine with the Case identity.
- C1 equivalents: `AiExternalCaseRuntimeV2`, `AiExternalLifecycleV2`,
  `AiExternalMetadataPublisherV2`, `AiExternalOAuthBrowserV2`, `AiExternalOAuthIssuerV2`,
  `AiExternalOAuthResolverV2`, `AiExternalOAuthNeutralV2`, `AiExternalOnboardingRuntimeV2`;
  `AiDcrMetadataReaderV2` prepares stored metadata; `AiDcrAdministratorV2` changes state/revision.
  `AiDcrRegistrarV2` is the old C1 registrar, **not** the HTTP endpoint capability.
- C2 endpoint: `AiDcrEndpointV3` has only `AiDcrAdmissionV3`, `AiDcrRegisterApplicationV3`,
  `AiDcrStatusV3` EXECUTEs. No approval, tenant read or issuance authority.

Exact module grants are in the respective installers and pinned catalogs; do not copy fixture grants
or give new grants to historical role members. Onboarding needs its own capability: combining it
with a historical tenant-approval member failed exact-permission verification. Preserve independently
governed secrets/SIDs versus ordinary auth/runtime/bootstrap, inherited/public/column authority checks,
RLS and fail-closed audit. This calls for explicit providers using existing patterns, not a new pool
architecture, signing authority or database security framework.

For any later authorized installation: fresh neutral dbo/autocommit connection, required context keys
NULL, exclusive schema lock, compatible exact source/catalog and every installed SQL control OFF.
Use the existing SQLCMD policy: validated TLS, `-N -b -V 16`, interactive password, no `-C`/`-P`,
immediate exit-code checks and STOP after any failure. Check exact roles/grants, enabled/trusted
constraints/RLS, receipts, preserved predecessor/binding state, zero unintended enablement and
transactional audit compatibility after each step. Fault/mutation tests belong only in the synthetic
fixture. An uncertain commit requires separately authorized discovery, not an automatic retry.

## 3. Required Azure configuration and acceptance

Keep the existing App Service, Azure SQL and Static Web Apps deployment facilities. No new proxy,
database framework, pool topology or hosting service is proposed. A pre-existing isolated slot may
be used only if its SQL, secrets, traffic and identity are demonstrably isolated; otherwise obtain an
explicitly authorized nonproduction app/database. Never swap synthetic secrets/configuration into production.

For Stage B explicitly set the following effective Spring properties to `false` (do not rely on
absence). Source defaults are false for all ten controls:

```properties
shale.ai.enabled=false
shale.ai.mcp.enabled=false
shale.ai.mcp.case-data-composition-enabled=false
shale.ai.mcp.search-cases-enabled=false
shale.ai.mcp.get-case-overview-enabled=false
shale.ai.oauth.composition-enabled=false
shale.ai.oauth.authorization-enabled=false
shale.ai.oauth.exchange-enabled=false
shale.ai.cimd.enabled=false
shale.ai.dcr.enabled=false
```

Where installed, keep `AiProductCapability`, `AiCimdProofControlV1`, `AiCimdOAuthControlV1`,
`AiExternalControlV2`, `AiDcrControlV3` Enabled=0; tenant policy missing/disabled, no new pilot authority.
`shale.ai.cimd.metadata-advertisement-enabled` is a separate optional advertisement toggle with a
**true** fallback when CIMD is enabled; explicitly false for the dormant review. It grants no authority.
The existing startup diagnostic covers only the eight historical app controls; it does not prove
CIMD/DCR or SQL state. Obtain allowlisted effective-property/release evidence including those additions,
without secret dumps. Use exact properties or verified Spring bindings, not guessed environment aliases.

For later approved composition, retain `SPRING_PROFILES_ACTIVE=azure`, PRODUCTION identity, canonical
`shale.ai.oauth.public-origin`, approved `shale.ai.oauth.browser-origin`, exact
`shale.ai.mcp.allowed-origins` (at most eight), ordinary browser CORS and no wildcard. AI data properties
are `shale.ai.data.{jdbc-url,username,password,database,contract-version}` and
`shale.ai.data.verifier.{jdbc-url,username,password}`; bootstrap properties are
`shale.ai.oauth.bootstrap.{jdbc-url,username,password}`. AI data aliases are
`SHALE_AI_DATA_JDBC_URL`, `SHALE_AI_DATA_USERNAME`, `SHALE_AI_DATA_PASSWORD`.
Keep encrypted certificate-validated SQL; protected JDBC URL options cannot override TLS/auth/MARS/timeouts.
Universal provider keys/roles are specified in the [runtime contract](architecture/universal-mcp-azure-runtime.md#required-configuration-contract-future-authorized-configuration-only); no credentials or Azure settings are supplied here.

Minimum Stage C acceptance, with sanitized evidence for the exact artifact/slot/configuration:

| Check | Pass requirement / STOP |
| --- | --- |
| Native Azure SQL | EngineEdition 5 contained identities and exact tokens/permissions; complete V2 + DCR_V3 startup/admission verification, receipt/binding preservation; restricted two-tenant Case reads, audit-fault rollback, revocation/final-handoff denial. Past disposable Azure setup/install passes did not close restricted catalog/read-only acceptance; recorded `VERIFIER_COMPONENT_READ` failure remains open. Stop on missing visibility or deadline failure; no broader grant/timeout repair. |
| CIMD outbound | On the intended Azure worker, standard `CimdMetadataService` resolves all A/AAAA answers, rejects any private answer, connects directly to a pinned numeric public address on TCP 443, validates original-host SNI/certificate and retrieves live metadata/JWKS within combined 5 s. It ignores ambient HTTP proxies. Use the existing standalone JShell procedure in [stabilization record](testing/universal-mcp-pr-b-stabilization.md#precise-azure-egress-acceptance-check-not-executed-here); no SQL or feature enablement needed for this probe. Proxy-only egress is a blocker; Cloud harness DNS/CONNECT seams are not Azure evidence or production fallbacks. |
| HTTPS ingress/discovery | Existing HTTPS-only/certificate/route evidence must cover `/mcp`, `/.well-known/oauth-protected-resource/mcp`, `/.well-known/oauth-authorization-server/oauth/mcp`, `/oauth/mcp/authorize`, `/consent`, `/token`, `/revoke`, `/register`, `/onboarding`, `/approval` under `/oauth/mcp`. No prefix/host rewrite to a different issuer. Anonymous MCP returns 401 discovery challenge when enabled; advertise only complete verified capabilities. Verify REQUEST/ASYNC security-chain registration and configured external consent URL through actual ingress. Never derive authority from Host/Forwarded. |
| Original Shale browser login | Deploy matching React/server artifacts via existing facilities; approve frontend HTTPS origin, API-origin build value, SPA deep-link `/oauth/mcp/authorize`, CORS/preflight/security headers. Real normal Shale password login produces a current durable WEB session/JTI; no separate OAuth password page, dev headers or client-supplied WEB bearer. Login return path, consent refresh/expiry/denial, logout, session replacement and revocation must work. DESKTOP/unbound/stale/foreign sessions fail. |
| Callbacks and stock clients | Fresh unmodified Hermes profiles receive only the nonproduction MCP URL; separately pass automatic CIMD and DCR, manual continuity, discovery/registration, login, tenant approval, explicit consent, S256, code/token, both tools, refresh/replay/revoke. Exact callback/state/resource/issuer match; DCR allows its reviewed exact localhost callback, manual/CIMD retain numeric-loopback policy. No pasted ID or patched protocol/client. |
| DCR shared-client/NAT | Test multiple users/tenants sharing a CIMD application and DCR metadata fingerprint; registration grants no tenant authority. Limits are SQL UTC-minute 100 global attempts, 10/socket-peer digest, 10/canonical metadata fingerprint, bounded 1,024 accounting rows; invalid attempts consume budget. Socket peer ignores forwarded headers: Azure ingress may collapse all users to one peer. Measure through actual ingress and approve source/NAT policy before activation; spoofed forwarded headers must not bypass it. |
| Capacity/retention | Cumulative 5,000 DCR identities, not just pending count. Never-approved payload/cache expires after 24 h; identity/audit history remains and capacity is not automatically reclaimed. Approved metadata is retained. Stop on unfair 429s, capacity exhaustion or need for changed limits; review a narrow policy change separately. |
| Approval/consent/revocation/audit | Same-tenant current admin approval, each user's independent explicit consent, two-tenant denial, revised/disabled application and reduced/revoked approval invalidate old authority; reenabling never resurrects it. Verify SQL-identity-attributed global registration audit, tenant EntityActionAuditLog, Case PHI/access/resource/RETURNED/quota records; audit failure withholds success. No tokens, callback/code URLs, assertions, request bodies or PHI in telemetry. |
| Deadlines/concurrency | Ten-second whole HTTP/admission/tool budget, SQL statements capped at five seconds, metadata five seconds; no renewed budget after verification. Measure cold and warm requests at agreed pilot concurrency including refresh/revoke, using stable schema. Prior local four concurrent Overviews passed at 7.587–8.557 s, limited headroom, not Azure/SLA evidence. Monitor timeout/withdrawal, SQL pressure and ordinary API latency; stop on failed verification/withdrawal, missing audit/handoff or unacceptable latency. Do not extend deadlines or skip checks to obtain a pass. |

## 4. Staged checklist

### Stage A — Preparation (this task prepares the review only)

- [ ] Pin source/tree and dependency versions; retain #1959 acceptance evidence. Review existing
  installation records against the inventory without connecting to production SQL.
- [ ] Record exact candidate/rollback executable hashes, ordinary dependency compatibility, effective
  all-OFF configuration evidence, owner/change window and rollback access. No new AI SQL is required
  merely to deploy an otherwise compatible dormant JAR.
- [ ] Approve the isolated Stage C target/data, origin mapping, ingress ownership, capacity objective
  and minimal production-composition follow-up. Resolve binding/verification packet inputs.
- **STOP:** unknown ordinary dependency, missing rollback artifact, ambiguous origin/zone, partial SQL
  state, effective control unknown/ON, or missing authorization for the next stage. Missing AI acceptance
  blocks activation, not independently justified ordinary all-OFF code preparation.

### Stage B — Dormant deployment (future, separate release authorization)

- [ ] Build/review exact source under JDK 21; run relevant local checks, package executable JAR, hash it
  and retain prior executable/configuration. Keep AI readiness, OAuth, CIMD, DCR, transport and tools OFF.
- [ ] Use the existing Kudu upload/startup/restart process from the Azure guide. No new SQL, AI secret
  provisioning, frontend publication or capability enablement is required by this dormant stage.
- [ ] Verify all ten effective controls false; no AI pool/attestation, readiness disabled, registration
  closed/no advertisement, no OAuth issuance or Case disclosure. Check ordinary health and authorized
  synthetic login/session/remembered-restore flows only; do not use production data for this rollout test.
- **STOP/rollback:** changed control, startup/ordinary auth regression, AI pool unexpectedly allocated,
  missing artifact parity or any new disclosure. Remain dormant after success.

### Stage C — Isolated Azure acceptance (future, explicit environment/test authorization)

Use the [isolated acceptance operator runbook](testing/universal-mcp-azure-acceptance-runbook.md)
for the minimal target, ordered fixture packet, native test matrix and remaining input gaps.

- [ ] Confirm no production DB/secret/data/traffic relationship; use synthetic tenants and hash-verified
  authoritative fixture. Rehearse the ordered SQL packet, contained role provisioning and binding here.
- [ ] Run standalone actual-worker CIMD egress check first. Use the accepted disposable composition
  only within its supported separate test identity/profile; it proves Azure infrastructure behavior,
  not production Boot compatibility. Existing `.test`/`.localhost` identity restrictions mean an
  ordinary Azure hostname cannot simply replace the fixture origin.
- [ ] Before production eligibility, review the supported [runtime composition](architecture/universal-mcp-azure-runtime.md)
  and isolated nonproduction identity binding; run actual App Service Boot/ingress/SQL/browser/stock
  acceptance with that candidate and all checks in section 3. Do not bypass guards to reach this step.
- [ ] Record exact client/SDK, artifact, SQL contracts, safe audit/count evidence, timings and cleanup;
  shut down test authority after acceptance. Production remains OFF throughout.
- **STOP:** any production reference, unsupported profile/identity, incomplete capability provider,
  failed native verifier/egress, NAT unfairness, wrong browser session, cross-tenant access, audit failure
  returning success or deadline/withdrawal regression. No production pilot based solely on fixture passes.

### Stage D — Controlled activation (future, separate production SQL and pilot authorizations)

- [ ] Require Stage C PASS for the production-capable candidate; approved operator/security/legal
  retention/SDK/tenant-owner decisions, pilot tenant/users/scopes, concurrency/monitoring thresholds,
  containment owner and change window. Approve the final production SQL and immutable binding packet.
- [ ] Authorized operator reconciles destination records/state, drains security-sensitive work, installs
  only missing approved SQL in order with all controls OFF, verifies complete V2/DCR_V3 security and
  binding under actual identities; stop at that boundary pending activation authorization.
- [ ] Deploy the approved candidate dormant through the same facility, verify ordinary compatibility,
  then separately authorize verified readiness/composition and independent disclosure/OAuth/SQL controls.
  Enable tenant authority only for the approved pilot, with admin approval and each user's consent.
  Global DCR registration is tenant-neutral and public when enabled: pilot tenant isolation does not
  restrict registration traffic. Approve that exposure explicitly or defer DCR advertisement/activation.
- [ ] Monitor denials/429s, audits, quota release, timeouts/withdrawal, capacity and ordinary regressions;
  expand only by another approved decision. No automatic merge/deploy/activation or expansion.
- **STOP/contain:** any unapproved tenant authority, identity/catalog/binding drift, data/audit discrepancy,
  security failure, unusable shared-NAT limits or exceeded approved latency/error thresholds.

Repository-determined commands for a **later authorized local build**, from the repository root:

```bash
mvn -B -pl shale-server -am clean package
# Select the one executable versioned JAR; never deploy *.jar.original.
sha256sum shale-server/target/shale-server-*.jar
# After inspecting the single executable match:
cp shale-server/target/shale-server-*.jar shale-server.jar
sha256sum shale-server.jar
```

No deployment or SQL execution command is supplied as an action for this task. Resource credentials,
nonproduction resource names and unverified binding inputs must come from approved records.
Do not use desktop release/publish scripts to deploy the JAR or trigger the `main`-targeted Static Web
Apps workflow while preparing this `codex/latest` documentation PR.

## 5. Rollback and containment

1. For a dormant-release failure, restore the retained exact previous executable via the existing
   Kudu path/startup procedure and governed restart; restore reviewed ordinary configuration while
   preserving every AI/MCP/OAuth/CIMD/DCR control OFF. Verify ordinary behavior and no new disclosure.
2. For pilot containment, disable tools/transport and global/tenant/application capabilities, stop
   new authorization/registration/issuance, revoke affected integration/grant/family/session/credentials
   through governed existing operations, drain serving leases/pools and if necessary disable dedicated
   connection/EXECUTE authority. Preserve revocation handling or provide the governed alternate before
   disabling its endpoint. Verify old credentials no longer disclose across replicas.
3. JAR rollback does not uninstall SQL or undo a binding. Retain receipts, approvals, revisions,
   invocation/quota and audit history. Never restore shared-runtime fallback, broad grants or old
   active authority. Incorrect committed binding requires a separately reviewed additive migration.
4. If reverting to pre-Phase-7B session code, follow the Azure guide: drain/wait one maximum token TTL;
   emergency rollback after durable revocation requires token-secret rotation and reauthentication.
   Do not casually replace the existing session cutover timestamp. Prefer a compatible recent rollback JAR.
5. On unknown SQL commit outcome or verification failure, keep controls OFF and stop for authorized
   discovery/review; no retry/reseal/uninstall or permission/timeout workaround.

## 6. Decisions still requiring owner approval

- Authorize any Stage B upload/restart separately after exact artifact/OFF/ordinary compatibility review.
- Choose and authorize Stage C Azure target, synthetic fixture and test window; approve the minimal
  production capability wiring/identity/guard successor and its verification packet as separate work.
- Approve canonical production/browser origins, full HTTPS route ownership, actual JVM/SQL mapping
  and immutable binding. Recorded App Service hostname and OS UTC are insufficient.
- Resolve native Azure restricted verification and actual-worker direct CIMD egress, without reopening
  the stopped diagnostic campaign or expanding permissions/timeouts without separate scope approval.
- Approve ingress source attribution/NAT fairness, DCR public-registration exposure, lifetime capacity/
  retention, pilot concurrency/error/latency thresholds and monitoring/containment ownership.
- Approve missing production SQL/role/secret provisioning only after inventory, then separately approve
  pilot tenant/scopes/consent and activation. No approval is inferred from this draft PR.

Primary supporting records: [deployment preflight](production-mcp-deployment-preflight.md),
[binding readiness](production-mcp-deployment-binding-readiness.md),
[PR A contract](architecture/universal-mcp-pr-a.md), [C1](architecture/universal-mcp-pr-c1.md),
[C2](architecture/universal-mcp-pr-c2.md), [integration verification](testing/universal-mcp-dcr-integration.md),
[C2 acceptance](testing/universal-mcp-pr-c2-verification.md),
[Azure egress/concurrency limits](testing/universal-mcp-pr-b-stabilization.md).

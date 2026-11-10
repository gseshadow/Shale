# PR #1947 confinement compatibility review and proposed supplement

Review baseline: `c4cd97889cf8990e7656a26723e076096aa3d2ae` on draft #1947.
Authoritative candidate: SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`, verified before SQL; its bytes remain private and unchanged.

## Findings before implementation

**Recommendation for #1947 as reviewed: REQUEST CHANGES; do not merge as-is.** Its installation guard
correctly blocks the production path, but historical scoped verification cannot authorize coexistence.
Review and incorporate the separate versioned supplement before merging the combined foundation.
No local result below authorizes production SQL or closes native Azure/binding deployment gates.

The authoritative V1 and V2 baseline both pass their unchanged verifiers and actual AI-login attestation.
The original registry guard refuses installation after either confinement version, with all existing receipts
and the immutable binding unchanged. In fresh disposable databases, installing the unchanged registry first
and confinement second is permitted by both original installers. Their historical receipt scopes and the
registry receipt both verify in that order. This is review evidence, not a production sequencing workaround.

Two actual-login counterexamples justify additional closure:

- Adding `db_datareader` to the publisher user leaves the new registry receipt and existing Case attestation
  green, but the publisher can SELECT tenant Cases. The registry receipt checks its roles' parents, not its
  member users' complete tokens and effective grants. Existing Case attestation protects its own AI/catalog
  identities, not a newly provisioned publisher identity.
- An extra trigger on the terminal `GlobalControlPlaneAuditLog` is outside both receipt scopes. It can extend
  the publisher's static ownership chain to a Case UPDATE while restoring its temporary RLS context. Direct
  Case UPDATE by the same login is denied. Both old scoped verifiers still accept that catalog. The trigger
  was created only as a deliberately malicious disposable fixture; neither new role can install a trigger.

An initial extra-trigger probe on the new approval table was correctly rejected by its parent-object
catalog receipt. The gap is the unclosed terminal audit dependency, not arbitrary triggers on new tables.
No change to a historical Case verifier's meaning or source receipt is justified by these findings.
A separately versioned **registry/confinement supplement V1** is required to cover the new authority graph.

## Proposed compatibility successor, reviewed before production edits

Keep V1/V2 Case installers, RPCs, manifests, standalone/packaged catalogs and all historical receipt values
unchanged. Add a private caller-executed prerequisite/check module with its own source, before-state and
catalog receipts. It grants no new application EXECUTE, SELECT, bypass or impersonation authority.

The registry guard may admit an existing confinement deployment only when this exact source-pinned module
exists. Recheck under the existing schema lock; require current historical verification, one existing
immutable binding, global product capability OFF, no partial registry and a closed pre-install graph. Validate
and seal the complete extension before the registry transaction commits. Never turn the guard into a
caller-set context flag, refresh an old receipt, copy a disposable receipt to another database, or mutate
an existing binding. Missing/drifted/partial prerequisites must still reject installation.

The supplement must close actual role/member/direct/inherited/public/column/schema ownership and module
signature edges, exact new entry points, helper/trigger execution context, every DML-trigger edge through
registrations/grants/sessions/credentials/consent and both terminal audit tables, strict policy identity,
FK/index/constraint state, cross-database module references and unchanged historical receipt/binding facts.
Table CHECK/default/computed expressions may not introduce an unreviewed user-module bridge; the seal
also fingerprints reachable table columns/constraints and policy identities. Individual SQL endpoint identity and cross-database login access require actual-session verification as
well as full independent catalog visibility; metadata absence is not acceptance.

Existing Case attestation must execute the new read-only supplement whenever any registry/supplement object
or role is present. An incomplete extension must remove readiness rather than fall back to old scoped
acceptance. The unchanged old Case checks still run first; new code only adds restrictions. Neither a
registry UUID nor the compatibility receipt establishes tenant access or consent.

## Audit and deployment scope

Catalog inspection and deployment metadata receipts have no PHI/domain read and need no application
entity-action event. Existing application/global mutation audits remain on the business transaction;
closing terminal audit triggers protects that established seam. No ad-hoc application audit table is added.
Native Azure contained-session/catalog/mutation-fault acceptance remains a separate OPEN gate. Existing
operator-reported production identities, Stage B/Phase1R and deployment-binding confirmations retain their
recorded scopes; none is rerun or rewritten. No SQL deployment, production permission/credential/settings
change, MCP/OAuth activation or automatic merge is authorized.

## Complete relevant dependency and authority inventory

`universal-pr3-authority-inventory.sql` provides a read-only full-database object, dependency, permission,
column, role-membership, owner, signature, trigger/order, FK and policy inventory. It requires complete
VIEW DEFINITION and returns metadata/hashes, never module bodies, table contents or credentials. The
inventory ran against both authoritative disposable variants. Keep destination principal metadata private.
The compatibility verifier closes the relevant graph as follows:

| Root / edge | Reachable authority and control |
| --- | --- |
| Publisher user → public + `AiApplicationPublisher` | Exactly one EXECUTE on `AiPublishApplicationV1`; no parent/additional roles, ownership, direct grants except CONNECT, grant options, impersonation, application public privileges or server administration. |
| Publish → registry + revision + `GlobalControlPlaneAuditLog` | Static dbo ownership chain; caller execution and exact source/settings/signature checks. Unscoped publisher membership, optimistic RowVer and application lock; audit is terminal with no DML trigger. |
| Tenant user → public + `AiApplicationTenant` | Exactly two EXECUTEs on request/decide; same member/effective-permission restrictions as publisher. No legacy, Case, bootstrap or private compatibility RPC. |
| Request → `AiLockV1` + `AiActorV1` → Users / tenant context | Existing serializing lock and active actor validation. Actor and tenant keys come from the trusted authenticated provider; mutable SQL session context is not independent SQL authentication or tenant identity proof. |
| Decide → same helpers + admin Actor + `AiEnabledV1` | Existing active-admin authorization; product/tenant policy and revision/expiry/RowVer checks remain. New approval creates only SUSPENDED registration. No consent, credential, delegated session or grant issuance. |
| Decide → registration / grant / session / credential / consent lifecycle | Explicit tenant-qualified writes and joins. All nine existing immutable/cascade/lifecycle triggers are source pinned and enabled; their transitive DML destinations are included. Manual registrations are reached only through an approval-owned integration ID. |
| Approval DML → strict tenant policy + three new immutable triggers | Exact existing `sec.TenantFilter`, dbo owner, enabled and schemabound, FILTER/AFTER INSERT/AFTER UPDATE. New constraints/index/FKs and row-version metadata remain covered by the independent registry catalog. History deletion/reopening cannot bypass the approved lifecycle. |
| All business mutations → both audit tables | Closed EntityType allowlist extension preserves the accepted predecessor vocabulary; no additional permission on either audit table. Existing source-pinned transaction inserts fail closed. Neither terminal audit table may add an unreviewed DML trigger. |
| All 18 reachable modules → owner/settings/dependencies | Exact separately reviewed LF/CRLF source variants, dbo ownership, caller execution, ANSI/QI ON, no signature augmentation, unresolved/caller-dependent/ambiguous/cross-database dependency. Only documented sys helpers and trigger inserted/deleted pseudo-tables are exempt from missing object IDs. |
| New members / roles → schema/object/type/certificate/key/assembly/role ownership | All forbidden. Independent metadata supplements actual endpoint token and effective object/column probes, so a column GRANT or bridge view cannot hide behind table DENY. |
| Private `AiRegistryConfinementV1` → old V1/V2 verifier + new graph | DENY EXECUTE to public; no application grant or EXECUTE AS OWNER. Only the separately authorized dbo install transaction uses BEFORE/SEAL. Readiness uses the read-only packaged batch under the existing catalog identity. |

The new SQL roles each retain six DENYs on each new table: SELECT, INSERT, UPDATE, DELETE, ALTER, CONTROL.
Their exact explicit permission counts are 19 and 20. Existing application roles and Case RPC grants are
unchanged. The independent catalog role receives no new permission: its existing metadata visibility
reads the supplement; it cannot execute the private helper. On SQL Server/MI the independent reviewer
also needs its existing server-definition/database inventory visibility. Azure never issues the boxed
server-catalog branch. Unknown engines refuse rather than selecting a fallback. A legitimate destination-only control or
representation needs a separately reviewed source accommodation; never remove an established trigger,
policy or permission fence to match a disposable fixture.

`AiRegistryIdentityAttestation` is a dormant read-only verifier, with no Spring bean, pool configuration,
endpoint or credentials. It verifies distinct actual publisher/tenant/catalog sessions, mapped SQL versus
contained Azure authentication, expected tokens and root EXECUTEs, complete object/column effective
permissions, and actual SQL Server HAS_DBACCESS against the independent full user-database inventory.
Both this verifier and ordinary Case attestation must pass before any future PR 4 pool activation or
rotation. Catalog-only verification cannot prove cross-database access of an actual login.

## Guarded migration and compatibility semantics

The generated preparation candidate uses the existing exclusive transaction schema lock and demands a
fresh unscoped dbo session, global `AiProductCapability` OFF, exactly one pre-existing immutable binding,
one recognized exact historical V1/V2 source, passing unchanged historical verification, no registry
objects/roles, and a closed predecessor graph. It does not create users, bind a deployment, activate
features or replay migrations. Its own helper definition is installed from UTF-16LE bytes so script line
endings cannot silently redefine its receipt. `generate-registry-v1.py --check` verifies public-source
reproducibility without rewriting files or consuming the authoritative fixture.

The registry guard checks the helper's source, owner, caller context, private permissions and namespaced
requirement marker. It repeats these checks plus BEFORE under the original lock. Before commit, SEAL
verifies the full new graph and appends its own catalog receipt exactly once. A failure rolls back all
registry/audit-constraint DDL and the seal; preparation remains fail-closed pending a valid installation.
A matching preparation rerun only verifies; it never refreshes any receipt. A one-shot registry rerun
still refuses. Unprepared reverse-order catalogs cannot obtain Case readiness or be silently adopted.

Historical source/receipts/binding retain their existing meaning. The supplement adds only
`Shale.RegistryConfinementV1.Source`, `.Before`, `.Catalog` on its helper and a new `.Required` marker on
`AiCaseDataInvocation`. No existing extended-property value is changed. It snapshots the old values and
binding exactly and compares them after installation and at readiness; approved future changes require a
new governed successor, not receipt refresh. New startup checks run after historical catalog validation
and before normal source/deployment readiness. Removing a component, helper or seal cannot cause fallback.

## Exact future deployment prerequisites and verification

No command below authorizes production access. Keep MCP/OAuth/CIMD/DCR and global product capability OFF.

1. Review and incorporate this separate successor explicitly before admitting #1947 alongside confinement.
   Review backup/restore, schema/audit locks, retention/capacity and the deliberate readiness outage between
   preparation and sealing. Build/package the new read-only resource with the attestation change; do not
   install SQL while running an older attestor that omits the supplement.
2. Respect the accepted Stage B, Phase 1R, predecessor migration, provenance and three dedicated contained
   identity records. Do not replay completed work. Read-only current-target verification must select the
   recognized installed version exactly; destination receipts are never copied from disposable SQL.
3. Close the independently governed immutable binding prerequisite, including actual running JVM time-zone
   evidence and approved issuer/resource. This candidate deliberately refuses the currently recorded
   empty production binding table. It provides no binding insert, replacement tuple or waiver.
4. Close native Azure gates below in their proper environments. Review actual target trigger/policy,
   ownership/signature, audit allowlist and full effective-permission inventory with independent visibility.
   Provision any future publisher/tenant identities only through a separately approved credential/permission
   change. Existing production identities are not re-provisioned by this review.
5. In the separately authorized maintenance window, use SQLCMD stop-on-error in a fresh unscoped dbo lease:
   run `universal-pr3-confinement-prepare.sql`, then `universal-pr3.sql`. Keep all controls OFF. Any error
   stops the sequence; never drop a guard, enable trust/chaining, grant dbo, repair a receipt, or adopt a
   partial/reverse-order catalog to proceed.
6. Run unchanged `verify.sql`, exact installed `phase3b7-confinement-verify.sql` or
   `phase3b8-confinement-verify.sql`, `universal-pr3-verify.sql`, and
   `universal-pr3-confinement-verify.sql` with full independent metadata visibility. Require zero findings.
   Compare old receipt and binding values with the approved private before-record; preserve their raw
   values. Confirm no new application approval, grant, token, consent or active client has been seeded.
7. Verify ordinary/manual-client regression and actual dedicated publisher/tenant sessions using
   `AiRegistryIdentityAttestation` plus full existing Case readiness; require only the approved root
   EXECUTEs and no extra user/database/server authority. TLS certificates and independent pool composition
   are separate prerequisites. This PR wires no new pools and performs no OAuth access or registration.

Containment keeps controls OFF, drains future pools and withdraws only separately approved new memberships/
EXECUTEs while retaining all history/audits/receipts. There is no destructive down migration or runtime
fallback. Once sealed, changing the graph requires another reviewed migration/version.

## Native Azure acceptance still OPEN

Disposable SQL Server proves neither contained Azure login behavior nor native catalog visibility and
policy/dependency representations. Existing [native reconciliation](../production-mcp-integration-readiness.md)
and [binding prerequisites](../production-mcp-deployment-binding-readiness.md) retain authority.
The following are separate remaining gates, not instructions to resume an archived investigation:

- Authorized read-only native contained-session/catalog/component-0 acceptance on the governed target,
  including Azure EngineEdition=5, original contained authentication, distinct identities, full metadata
  visibility, no boxed-server query, old receipts and current function/policy/dependency representations.
- Separately authorized **disposable Azure** prepare/seal/rollback and permission, trigger, RLS, audit-fault,
  expiry/replay/race and two-tenant acceptance using this successor. Never run fault injections or synthetic
  fixture writes on production. SQL Server's canonical authoritative V2 candidate does not substitute for
  the actual accepted Phase 1R/native Azure representation.
- Existing V2 destination/deployment, JVM/binding, artifact/ingress/configuration parity, operator/legal/
  tenant approval and explicit future consent gates. No automatic client registration is advertised;
  CIMD, DCR and MCP/OAuth activation remain out of scope.

## Test impact and regression scope

Pre-edit searches covered constructor/resource loading, receipt/source dispatch, SQL role/permission and
binding checks, registry procedure/trigger bodies, and all neighboring migration/attestation tests.

| Changed production contract | Relevant verification |
| --- | --- |
| New graph template, generator, prepare/verify and registry guard/seal | Six authoritative scenarios per V1/V2: refusal without prerequisite, sealed install, drift/restore, cross-database actual identity, prepare/seal rollback, and business audit rollback. Reverse-order unsealed readiness rejection on each version. |
| Existing Case attestor's additive presence/read-only stage | V1/V2 legacy-only actual readiness, incomplete preparation rejection, combined positive readiness, actual existing manual proof-bound overview, recovery after drift; existing `AiCaseDataAttestationTest` Azure routing/inventory and both migration contracts. |
| Dormant registry identity attestor | Distinct real logins, exact EXECUTEs, all-object/column permissions, fixed/custom-role and direct/column/public authority, other-database mapping, terminal triggers, table-expression modules and policy identity. |
| Generated source/binding boundary | Three public generator contracts, unchanged old standalone verifier embedding/source pins; five existing immutable-binding packet checks; raw-byte comparison of 31 historical artifacts. |
| Unchanged application business/lifecycle and JDBC boundary | Existing nine real SQL registry scenarios rerun: two tenants, administrator authorization, races, RowVer/replay, stale/expired revisions, ceiling/revocation, manual client preservation and failed audit rollback. Existing security/OAuth completion/credential/exchange/revoke contracts. |

The selector chooses Case and security-data core/data/server smoke and dependency tests. This covers the
protected Case boundary and tenant/JDBC/OAuth/audit composition affected by readiness. Its full-suite
recommendation is informational because the new resource path is not mapped by the old selector; that
resource is directly exercised by authoritative SQL, migration/source checks and affected tests. No UI,
visual, dependency, build, workflow or general Case business behavior changed; the full historical profile
is not run. No SQL-opt-in skip counts as SQL acceptance.

## Local verification results and merge recommendation

The isolated SQL instance uses SQL Server 2022 Linux image
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`,
loopback port 14334, certificate-validated forced TLS, random nonce-owned databases and six independent
random SQL logins per test class. The unchanged authoritative fixture is hash-gated before parsing and
SQL execution. Its only data is newly seeded synthetic tenants 7/8 and test users/Cases/manual clients.
No production SQL connection, settings/credential change, deployment or enablement occurred.

Use the disposable setup and private environment described in
[the original verification record](../testing/universal-mcp-pr3-verification.md), plus
`SHALE_CASE_VERIFY_SCHEMA=<private authoritative-candidate.sql path>` and
`SHALE_CASE_VERIFY_SCHEMA_SHA256=4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
The extra classes reuse the unchanged existing Case fixture; select only the new compatibility methods
below rather than unnecessarily rerunning every inherited historical Case test.

```sh
python3 build/sql-confinement/generate-registry-v1.py --check
python3 -m unittest discover -s build/sql-confinement -p test_registry_v1.py
compat_methods='originalGuardRejectsCombinedCatalogWithoutTouchingReceipts+preparedCompatibilityInstallsWithoutRefreshingAnyHistoricalReceipt+newGraphRejectsMemberPublicColumnAndTerminalAuditBridges+actualPublisherSessionRejectsOtherDatabaseMapping+unsafePreparationAndFailedSealLeaveNoPartialInstallation+authoritativeAuditFailureRollsBackBusinessMutations'
mvn -B -pl shale-data -am \
  "-Dtest=AiApplicationConfinementLocalSqlServerTest#${compat_methods},AiApplicationConfinementV2LocalSqlServerTest#${compat_methods},AiApplicationCombinedConfinementLocalSqlServerTest#unpreparedReverseOrderCannotObtainCaseReadiness,AiApplicationCombinedConfinementV2LocalSqlServerTest#unpreparedReverseOrderCannotObtainCaseReadiness" \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Unset SQL opt-in for ordinary unit/affected/critical runs. Microsoft JDK 21.0.13.0.2 and Maven 3.9.11,
local dependency repository and session-only proxy/trust options match the original verification record.
No build, workflow, dependency or test timeout was changed. The generated helper Source SHA-256 is
`3636deda3744ada73314b233532834bd13d02a102132f16ea4cf00ab91cde5c1`.

```sh
mvn -B -pl shale-data -am \
  -Dtest=AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,AiApplicationContractTest,AiSecurityContractTest,AiOAuthCredentialContractTest,AiOAuthExchangeContractTest,AiOAuthRevocationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base c4cd97889cf8990e7656a26723e076096aa3d2ae --head HEAD \
  --format markdown --plan-output work/pr1947-review/plan.json --output work/pr1947-review/selection.md
python3 build/test-selection/run_selection.py work/pr1947-review/plan.json --command affected
mvn -B test
python3 -m unittest build/test-selection/test_select_tests.py
```

Counts from overlapping runs must not be added as distinct tests. The original #1947 verification record retains its historical results;
this review does not rerun all 182 unchanged legacy SQL scenarios or reopen accepted local campaigns.

Verified final-source authoritative scenarios: **14/14** across V1 and V2. The final expanded 14-scenario
batch passed 12 and exposed two test-fixture DDL errors: a CHECK and function had the same SQL object
name. Only the constraint was renamed; the two affected scenarios then passed in the clean focused run.
No production SQL or assertion was relaxed for that correction.

| Check | Result |
| --- | --- |
| Final authoritative scenarios | 14 distinct scenarios verified: two versions each of guard refusal, guarded install/old manual Case read/two-tenant approval, drift/recovery, actual other-DB mapping, failed preparation/late seal rollback, business audit rollback; plus unprepared reverse-order rejection on each version. |
| Corrected focused run | 39 PASS, zero failures/errors/skips: 28 migration/attestation/application/security/OAuth contracts, nine existing real SQL registry scenarios, two authoritative graph/actual-identity scenarios. |
| Selector affected suite | 153 PASS, zero failures/errors/skips reported; core/data/server successful. SQL opt-in is absent, so zero-test SQL classes are not acceptance evidence. |
| Critical `mvn -B test` | 245 PASS, zero failures/errors/skips; all seven reactor modules successful. |
| Generator/source contracts | 3 PASS; generated artifacts current. |
| Existing immutable-binding packet tests | 5 PASS. |
| Selector tests | 32 PASS. |

The authoritative negative tests cover fixed/custom role inheritance, direct table and column grants,
a public bridge view, a terminal global-audit trigger, a CHECK invoking an unreviewed function and a
replacement RLS policy. Unchanged standalone Case/registry SQL verifiers remain green for the seven role-member, terminal-audit,
table-expression and policy-identity mutations while the supplement, Case readiness and actual registry
identity verification refuse; removing each fault restores acceptance. The public bridge also exercises
the existing public-authority guard. The terminal-trigger test also proves the controlled synthetic Case write through publish,
while the publisher's direct UPDATE is denied. No application identity can create that trigger.
Old source-pinned root procedure/trigger bodies remain unchanged from #1947.

The corrected focused run used this command with the private disposable SQL environment still present:

```sh
mvn -B -pl shale-data -am \
  -Dtest=AiApplicationConfinementLocalSqlServerTest#newGraphRejectsMemberPublicColumnAndTerminalAuditBridges,AiApplicationConfinementV2LocalSqlServerTest#newGraphRejectsMemberPublicColumnAndTerminalAuditBridges,AiApplicationLocalSqlServerTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,AiApplicationContractTest,AiSecurityContractTest,AiOAuthCredentialContractTest,AiOAuthExchangeContractTest,AiOAuthRevocationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/deployment-binding -p test_packet.py
```

Final preservation review compared all 31 historical SQL/resource/manifest/binding artifacts byte-for-byte
to `c4cd97889cf8990e7656a26723e076096aa3d2ae`. A broader comparison also confirmed 44 predecessor
artifacts/records unchanged, including old generators, binding packet code and the original #1947 test
record. Registry procedure/trigger bodies are unchanged. Actual positive SQL tests compare every historical
extended-property value and every immutable binding field before/after installation. The attached fixture
retains its approved SHA-256. Certificate-validated owner metadata verification found **zero user databases
and zero nonce logins** after the SQL runs; the dedicated container and generated SA secret/key/truststore
were then removed. No fixture, private body, credential, live receipt or destination metadata is committed.
`git diff --check` passed.

**Merge recommendation: REQUEST CHANGES on #1947 as-is.** A versioned supplement is justified and this
successor is isolated for explicit review against #1947's branch. Incorporate the reviewed successor before
merging the combined offline foundation into `codex/latest`. Production installation remains **NO-GO**
until the independently governed native Azure, existing binding/JVM, destination, artifact/configuration
and operator approval prerequisites pass. No SQL deployment, production permission change, feature
activation or automatic merge occurred.

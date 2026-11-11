# PR 4C: dedicated runtime candidate — BLOCKED / UNVERIFIED

Stacked on draft #1951 (`codex/cimd-oauth-pr4b`, `4043996008de85cd7c9f7b137598e3d597698ea4`),
retaining #1949/#1950. CIMD remains OFF, unadvertised and unavailable to HTTP/browser composition.
This is a reviewable runtime candidate with explicit blockers, **not completed PR 4C acceptance**.
No production credentials, SQL, permission changes, settings, deployment or merge occurred.

## Blocking findings

1. Restricted catalog execution is still unresolved on SQL Server/MI. Direct SELECT grants on
   PR 4A/4B receipt tables would change their sealed permission catalogs. The additive parameterless
   owner-context catalog gate avoids those grants, but `EXECUTE AS OWNER` loses server visibility.
   A real restricted catalog login has VIEW ANY DEFINITION; inside an owner-context procedure on a
   TRUSTWORTHY-OFF disposable SQL Server database, `HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION')`
   is **0** and the nonce login inventory is **empty**. Empty visibility cannot attest absence.
   Consequently the unchanged boxed-server branches cannot be accepted through this gate. A separately
   reviewed least-privilege catalog execution contract is required; this candidate does not add
   TRUSTWORTHY, impersonation, server administrative roles, signing authority, or altered verifiers.

The authoritative fixture was subsequently restored and hash-verified as recorded below.
The boxed catalog finding still blocks SQL Server/MI full runtime readiness. The native Azure
path is also **unverified**; a local boxed refusal is not evidence that Azure succeeds. Keep all live
composition disabled and do not install the new SQL candidate as a production workaround.

## Restored authoritative fixture

The original path `/workspace/attachments/86798399-e48c-4839-a4ca-096931b813b1/authoritative-candidate.sql`
is missing. The user supplied the attachment again; its downloaded copy is at
`/workspace/attachments/815fd541-5132-4fca-be78-16a1af192a37/authoritative-candidate.sql`.
Its SHA-256 matches the required
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f` exactly.
Hash verification precedes schema import and is repeated after SQL execution. The file remains
unmodified and uncommitted. The existing restrictive, hash-gated DDL importer treats it as schema
evidence, not operator instructions. No substitute schema or production SQL is used. This clears
the earlier missing-fixture blocker; it does not clear restricted catalog execution or native gates.

## Pre-edit review and test impact

Reviewed the repository bootstrap and complete `architecture/codex-prompt-rules.md`, database/schema,
tenancy/RLS and system-overview guidance; universal MCP onboarding, PR 4 dependency, PR 4A/4B handoffs,
#1948 compatibility, current production MCP readiness, web API and Azure deployment/binding guidance.
Current accepted Stage B/Phase 1R, existing identity and deployment records retain their scopes.

Inspected `AiCaseDataAttestationTest`, `AiJdbcUrlTest`, `DataSourcesAiCaseDataTest`,
`McpCaseCompositionTest`, existing OAuth/browser/assertion contracts, authoritative Case/registry/
CIMD proof/OAuth SQL fixtures and V1 variants, generators and selector inventories before editing.

| Changed contract | Relevant verification |
| --- | --- |
| Independent config, fixed TLS policy, pool ownership | AiCimdRuntimeTest; existing AiJdbcUrlTest and DataSourcesAiCaseDataTest |
| Actual identity, role/public inheritance, complete effective authority | AiCimdRuntimeSqlServerTest using real restricted SQL logins |
| Fixed catalog gate, source pinning, closed execution graph | test_cimd_runtime_v1.py; full authoritative installation/graph acceptance remains blocked |
| Explicit Case namespace | Existing AiCaseDataAccessTest plus CIMD routing assertions; existing authoritative proof fixture now uses production routing instead of a SQL-string-rewriting proxy |
| Startup activation refusal | CimdRuntimeConfigurationTest; affected server/OAuth and critical suites |

## Exact permission matrix

All business identities receive database CONNECT and membership of **one** existing role. They have
no direct application-table/column SELECT, INSERT, UPDATE, DELETE, REFERENCES, schema/DDL, impersonation,
ownership, grant option or private-helper EXECUTE. SQL Server's ordinary public CONNECT SQL/VIEW ANY
DATABASE and column-encryption metadata defaults remain bounded allowances, not application grants.
Each identity is distinct from all other new identities and from ordinary auth, runtime/legacy OAuth,
OAuth bootstrap and legacy Case identities, proven through actual sessions and catalog SIDs.

| Dedicated pool key / SQL role | Exact dbo EXECUTE roots | Tenant context |
| --- | --- | --- |
| `case` / AiCimdCaseRuntimeV1 | AiCimdDataAuthenticateV1, AiCimdDataSearchCasesV1, AiCimdDataGetCaseOverviewV1, AiCimdDataRecordCaseFailureV1, AiCimdDataFinishCaseQuotaV1, AiCimdDataAuthorizeCaseHandoffV1 | Initially neutral; credential proof owns context |
| `browser` / AiCimdOAuthBrowserV1 | AiCimdLoadClientV1, AiCimdBeginOAuthV1, AiCimdFindOAuthIntentV1, AiCimdApproveOAuthV1, AiCimdCompleteOAuthV1, AiCimdDenyOAuthV1 | Original authenticated WEB tenant/actor |
| `issuer` / AiCimdOAuthIssuerV1 | AiCimdLoadClientProofV1, AiCimdRedeemV1, AiCimdRefreshV1, AiCimdRevokeCredentialV1 | Restricted resolver-derived digest owner |
| `resolver` / AiCimdOAuthResolverV1 | AiCimdResolveCodeV1, AiCimdResolveRefreshV1, AiCimdResolveRevocationV1 | Neutral |
| `metadata` / AiCimdMetadataPublisherV1 | AiCimdResolvePublicationV1, AiCimdPublishMetadataV1 | Neutral; cannot advance registry publication |
| `neutral` / AiCimdOAuthNeutralV1 | AiCimdRevokeUnknownV1 | Neutral |
| `registry-publisher` / AiApplicationPublisher | AiPublishApplicationV1 | Neutral governed publisher |
| `registry-tenant` / AiApplicationTenant | AiRequestApplicationV1, AiDecideApplicationV1 | Authenticated tenant/actor; SQL verifies administrator decision |
| `catalog` / **new** AiCimdCatalogV1 | **new** AiCimdAttestCatalogV1 | Neutral, read-only verification |

The new authoritative tests reuse the established nonce-owned OAuth fixture, add a distinct restricted
catalog login and exercise only their explicitly selected methods; inherited historical campaigns are
not run. The catalog role additionally has database VIEW DEFINITION; SQL Server/MI requires a separately
configured login with VIEW ANY DEFINITION and complete database visibility. SQL Server 2022 reports
VIEW SECURITY/PERFORMANCE DEFINITION as implications of VIEW DEFINITION, and corresponding ANY
implications at server scope. Only the original explicit VIEW DEFINITION grants are allowed; no
additional explicit grant is introduced for those implications. Catalog effective permissions must
contain no business data access. The gate runs fixed reviewed SQL, returns predecessor finding/count/
receipt reports plus the immutable deployment binding; Java privately drains all results. It accepts
no arguments or caller-selected SQL and exposes no module/data/credential output to runtime callers.

The existing `AiCimdLifecycleV1` family-administration role retains its exact one `AiCimdRevokeFamilyV1`
EXECUTE and existing DENYs. No new pool or service consumer is created for it: PR 4B token revocation
already runs through the dedicated issuer. Its source/role/members remain checked by PR 4A, and its
root remains included in the supplemental closed graph. No legacy role receives a new permission.

## Runtime and pool design

`AiCimdRuntime.Configuration.read` resolves only `shale.ai.cimd.*` keys. Every pool independently
requires `.jdbc-url`, `.username`, `.password` beneath its matrix key. Database, issuer, resource and
explicit `contract-version=V1|V2` are also mandatory. Missing settings, duplicate configured usernames,
unsupported versions and URL authentication/TLS overrides fail before pool allocation. Configuration
and endpoint string representations redact all values. Actual identities must also match configured
usernames, the configured database, one server and one platform; name checks cannot replace SID checks.

Each of nine owned Hikari pools uses minimum idle 0, maximum 2, 3-second borrow/login timeout,
1-second validation timeout, 15-minute maximum lifetime, and certificate/hostname-validated TLS:
`encrypt=true`, `trustServerCertificate=false`. Destination URLs use the existing strict `AiJdbcUrl`
allowlist; credentials and TLS behavior cannot enter through URL aliases. SQL statements have five-second
query limits, with a 30-second socket bound. Full verification has no positive cache and currently scans
all application objects/columns; no HTTP admission latency claim is made. Establish total admission/
cleanup deadlines before live composition. TLS trust must be independently configured and accepted for
every deployment; local test certificates never enter system or production trust.

`open` constructs all pools and attests before returning. Startup failure closes every allocated pool.
Every service lease reattests; attestation failure closes the runtime, requiring complete reconstruction
and reattestation for rotation/recovery. No per-pool unchecked reset or fallback exists. The four forbidden
sources are evidence-only, never candidates for a business connection. Existing ordinary auth/runtime
sources may share an identity; every new SID must differ from all of them.

Tenant services reuse `RuntimeSessionService` with immutable per-call owner values. Both context keys
are cleared before a lease returns; failed restoration/unfinished transaction evicts the connection.
Neutral services start unscoped. Mutable SESSION_CONTEXT remains an application assertion, not SQL
identity authentication. Case operations reuse the existing audited reader, quotas, proof lifetime,
result drainage and final disclosure checks, selecting `Namespace.CIMD_V1` explicitly for every phase.
Legacy constructors still select their original namespace. No caught SQL error selects another root.

The Spring startup fence rejects true **or malformed** `shale.ai.cimd.enabled` and
`shale.ai.cimd.composition-enabled` before singleton initialization. Absent or literal false values
need no CIMD credentials. No Spring runtime bean, live provider routing, endpoint, browser filter,
metadata advertisement or activation flag is introduced. `CimdOAuthLifecycle` remains unwired.

## Attestation design and evidence limits

The candidate examines actual authenticated sessions and the independent complete catalog:

- Exact SQL-user/public/one-role tokens, no nested/inherited/server roles; expected mapped SQL versus
  native-contained authentication, live session origin, distinct SID/server/database/platform and
  no fallback to ordinary/bootstrap/legacy identities. Duplicate or extra role members fail closed.
- Complete effective database/server/object permissions, every application object from the reviewer's
  inventory, and every column's SELECT/UPDATE/REFERENCES. Direct, role and public grants, scoped
  impersonation, grant options, and schema/object/type/role/certificate/key/assembly ownership are
  examined separately. A real column GRANT can override a table DENY and is rejected explicitly.
- SQL Server/MI cross-database HAS_DBACCESS for every other database from the independent complete
  inventory, including the catalog identity. Missing/NULL probe results refuse. Native Azure checks
  contained own-session authentication and rejects synonyms/external/cross-database references;
  it must not execute boxed server-catalog probes. Full system-database public/guest/mapped-user
  authority is not positively accepted by the boxed prototype: its cross-database loop covers other
  user databases, matching the predecessor. That additional scope must be closed in the reviewed
  boxed catalog successor; current full boxed readiness is already blocked. Do not label the
  isolated identity-helper tests complete cross-database acceptance.
- Before entering the catalog gate: exact packaged SHA-256 over its SQL Server UTF-16LE definition,
  owner/execution context, ANSI/QI settings, exact grants, and absence of signatures. Unknown source,
  schema, role, property, receipt, binding or result contract cannot establish readiness.
- The fixed gate embeds unchanged PR 4B verification, transitively retaining V1/V2 dispatch, #1948,
  PR 4A/4B source/catalog/before receipts. Explicit configured V1 refuses V2 markers. The immutable
  data/audit contract remains `case-read-v1` even with confinement V2. Issuer/resource/JVM time zone
  must match; established SQL/Java wall-time sample checks remain.
- The additive graph closes procedure/function dependencies, triggers, table expressions, policy
  functions and cascading-FK destinations from 26 roots. User modules require reviewed hashes;
  unresolved, ambiguous, caller-dependent, foreign-database/server, synonym, external and CLR edges
  fail. Ownership/settings/signatures and enabled trigger semantics remain checked. The predecessor
  verifiers retain their exact generations and catalogs; the supplemental source allowlist cannot
  authorize a different historical generation. Unknown legitimate edges are blockers, not migrations.

The new one-shot candidate installer creates only the catalog role and fixed gate under the existing
schema lock and OFF controls. Its final independent verification runs before COMMIT. It never creates
logins, secrets, memberships, historical properties or a replacement binding/receipt. It must **not** be
used to repair an accepted installation. Authoritative installation and a focused terminal-audit
fault test are recorded below; no complete runtime or exhaustive graph-fault acceptance is claimed.

## Audit compatibility

Business mutation and Case disclosure continue through unchanged PR 4A/4B and historical SQL modules,
using established transactional EntityActionAuditLog, PHI and integration-access audits. The explicit
Case namespace changes routing only. No new audit schema, entity vocabulary or metadata is introduced.
Missing/failed required audit remains a SQL transaction failure. Attestation reads catalog/receipts,
binding and existing non-PHI informational counts; it performs no business mutation or PHI disclosure
and intentionally creates no domain/PHI audit event. There are no new credential/URL/assertion/digest,
SQL, module-body or RowVer log fields. New identity provisioning and governed deployment/change records
remain operator responsibilities. Those are not fabricated tenant timeline events.

## Executed validation

All SQL executed on a new
loopback-only disposable SQL Server 2022 container with actual nonce-owned SQL logins. Its image was
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`.
Generated localhost certificate/private truststore enforced `trustServerCertificate=false`; neither
system trust nor production changed. The original eight synthetic identity/refusal scenarios intentionally
have no authoritative receipts and establish only that boundary. The subsequent authoritative scenarios
use the restored hash-verified fixture and unchanged V1/V2/#1948/4A/4B sources, with synthetic rows only.

Eight SQL scenarios cover exact per-role root access; denial of tables/wrong issuer roots; column exceptions,
inherited db_datareader and scoped user impersonation; schema ownership/public chained views/other-DB
mappings; scoped server impersonation and catalog column grants; incomplete schema/duplicate identity/
failed-runtime pool cleanup; fixed-gate compilation/missing-receipt refusal; certificate hostname rejection; and the independently asserted owner-context server-visibility blocker.
Scenario assertions overlap; this is eight tests, not a V1/V2 lifecycle campaign. Development fixture
identifier/column-grant setup errors were corrected; SQL Server 2022 metadata implications were admitted only after actual
permission enumeration, keeping explicit grants exact. No predecessor assertion was relaxed.


| Final local check | Result |
| --- | --- |
| Focused configuration/startup/Case/old attestation/TLS URL contracts | 27 PASS; zero failures/errors/skips |
| Actual restricted-login SQL scenarios | 8 PASS; identity/refusal evidence only; no authoritative lifecycle acceptance |
| Authoritative V1/V2 lifecycle compatibility | 4 PASS: two-tenant issue/rotate/legacy exclusion and private-key/global replay/audit rollback, each on V1 and V2 |
| Authoritative V1/V2 PR 4C catalog/identity/rollback | 4 PASS: installation plus restricted refusal, and late terminal-audit fault rollback, each on V1 and V2 |
| Selector affected core/data/server batch | 221 PASS; zero failures/errors/skips |
| Critical seven-module reactor | 254 PASS; all modules successful; zero failures/errors/skips |
| CIMD generators/contracts (4B, 4A, new runtime) | 11 PASS (4 + 3 + 4) |
| Unchanged registry generation/contracts | 3 PASS |
| Immutable binding packet / selector unit tests | 5 / 32 PASS |
| Diff whitespace, historical artifacts and private-fixture non-substitution | PASS; only additive new SQL/resources |
| Successful full boxed runtime readiness | **BLOCKED**: unchanged #1948 rejects owner-context catalog execution with SQL 59310 on V1 and V2 |
| Native Azure catalog and mutation/fault acceptance | **NOT RUN**, independent gates remain open |

The eight authoritative executions comprise four distinct scenarios repeated on V1 and V2. The
catalog scenario installs the exact PR 4C candidate with controls OFF, confirms the supplemental graph
accepts the authoritative schema, verifies all nine actual identities/effective permissions through
the dedicated catalog login, checks source/selected-contract checks reach the catalog phase, and
asserts both direct SQL 59310 with `Registry server catalog visibility required.` and full-runtime
startup refusal/complete pool cleanup. It preserves every historical receipt and immutable binding
and reruns the unchanged cumulative PR 4B verifier. The late-fault scenario injects an unreviewed
terminal-audit trigger after creating the new role; failure rolls back that trigger, role and catalog
gate, preserving old receipts/binding and manual-client continuity.

The first authoritative batch passed its four unchanged lifecycle checks. The four new runtime tests
then failed their final manual-client check because the fixture still had the ordinary global
capability OFF. Restoring that ordinary flag inside the disposable test before manual continuity
corrected setup; all four runtime tests passed in the focused rerun. Production code, SQL generation,
permissions, receipts and confinement assertions were unchanged. The previously passing eight
synthetic identity/TLS tests remain separate evidence; they are not relabeled authoritative tests.

Counts overlap. SQL-opt-in classes execute zero tests in affected/critical batches and are not SQL
acceptance. The selector's informational full-suite suggestion comes from the selector inventory edit
and new resource paths; no unrelated historical full suite was run, honoring the requested scope.

Exact commands from the repository root (workspace-local JDK 21/Maven 3.9.11, Cloud proxy/CA settings):

```sh
mvn -B -pl shale-server -am \
  '-Dtest=AiCimdRuntimeTest,AiCaseDataAccessTest,AiCaseDataAttestationTest,AiJdbcUrlTest,DataSourcesAiCaseDataTest,CimdRuntimeConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Only inside the dedicated disposable TLS setup; never supply production credentials.
# SHALE_CIMD_SQL_OPT_IN=LOCAL_DISPOSABLE_CIMD, generated password, port 14333,
# and a private truststore containing the test certificate are required.
mvn -B -pl shale-data -am -Dtest=AiCimdRuntimeSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Restored fixture: SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI, hash-gated schema,
# generated disposable credentials, and SHALE_AI_SQL_TRUSTSTORE (synthetic-trust).
mvn -B -pl shale-data -am \
  '-Dtest=AiCimdOAuthLocalSqlServerTest#lifecycleTwoTenantIssuanceRotationAndLegacyExclusion+lifecyclePrivateKeyBindingAndAssertionReplayAreAtomic,AiCimdOAuthV1LocalSqlServerTest#lifecycleTwoTenantIssuanceRotationAndLegacyExclusion+lifecyclePrivateKeyBindingAndAssertionReplayAreAtomic' \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-data -am \
  '-Dtest=AiCimdRuntimeAuthoritativeSqlServerTest#runtime*,AiCimdRuntimeAuthoritativeV1SqlServerTest#runtime*' \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/sql-confinement/generate-cimd-runtime-v1.py --check
python3 -m unittest discover -s build/sql-confinement -p 'test_cimd*.py'
python3 -m unittest discover -s build/sql-confinement -p test_registry_v1.py
python3 -m unittest discover -s build/deployment-binding -p test_packet.py
python3 -m unittest build/test-selection/test_select_tests.py
python3 build/test-selection/select_tests.py --base 4043996008de85cd7c9f7b137598e3d597698ea4 \
  --head HEAD --format markdown --plan-output work/pr4c/selection.json \
  --output work/pr4c/selection.md
# Outside ALL SQL opt-ins, run each selected_maven_batches entry, then:
mvn -B test
git diff --check 4043996008de85cd7c9f7b137598e3d597698ea4 HEAD
```

A separate certificate-validated JDBC connection confirmed **zero disposable user databases and zero
nonce logins** after the final SQL run. The dedicated container, generated passwords/key/certificate
and private truststores were removed. The restored authoritative attachment was imported only into
disposable databases; it was not reconstructed, modified or committed. No live application
endpoint/browser/stock client was contacted.

## Independent native Azure acceptance and HTTP dependencies

Native Azure acceptance requires a separately authorized disposable Azure SQL Database, distinct **SQL
contained** users with expected SIDs/authentication and no privileged mappings/groups, independent TLS
pools, exact V2/#1948/4A/4B receipts and unchanged binding. Prove complete restricted catalog execution
through the new gate without server catalogs, direct table grants, impersonation or receipt refresh.
The existing stopped native catalog investigation and separate mutation/fault gate remain open; this PR
does not restart them or substitute production. Retain accepted Stage 0 and existing identity evidence
at their documented scopes.

After the catalog contract (including boxed system-database authority) is resolved, complete the remaining
authoritative V1/V2 identity/graph/permission faults and actual accepted runtime leases across tenants,
poisoned pool reuse, rotation/restart, source/schema/receipt/binding mutation, Case proof/final disclosure,
issuer replay/revocation/audit rollback and manual continuity. Repeat the native contained-user catalog
and separate disposable mutation/fault cases independently; Linux SQL Server is not native acceptance.

Before HTTP/browser integration: accepted complete catalog contract; positive runtime/TLS/identity tests;
bounded total request/cleanup budgets; maintained manual-versus-CIMD provider routing with no identifier
translation; actual original-WEB/browser/HTTPS/SQL lifecycle; metadata freshness and remote-key/auth-method
acceptance; governed source/artifact/configuration/binding/ingress/logging/legal/operator/tenant gates.
Stock-client acceptance, DCR and Settings stay in later scopes. No production provisioning, SQL,
activation, advertisement, deployment or automatic merge is authorized by this draft.

# Azure SQL Database attestation acceptance

Current native follow-up (2026-10-29): [pending record](native-azure-readonly-acceptance.md) and
[exact disposable operator packet](native-azure-readonly-operator-runbook.md). The native JUnit gate
now requires the disposable V2 fixture and refuses production; the older command/settings below
are historical context, not a complete current execution packet. No native Azure pass is claimed.

Date: 2026-10-28. Base: `a8b216f6cc89897121988c675b0ea58280a80cca` (merged #1925).
Branch: `codex/authoritative-acceptance-20261028`. Tested implementation: `61ec3bf59b2ae7ed67e5adec95fd7d7644e887e7`.
Subsequent record-only edits leave the tested runtime and test source unchanged.

**READY FOR PRODUCTION IDENTITY/SQL DEPLOYMENT PREPARATION**

| Independent gate | Current result |
| --- | --- |
| Production Azure SQL Stage 0 platform | PASS — operator-reported live production evidence |
| Authoritative local/adversarial acceptance | PASS — fresh owned local SQL, both clients and real TLS |
| Production identity/catalog attestation | OPEN |
| Production deployment | NOT AUTHORIZED |
| Pilot | NOT AUTHORIZED |

This permits preparation of a reviewable governed deployment proposal, not provisioning, migration,
deployment, configuration changes or activation. No production connection or change was made by this task.
The [preflight](../production-mcp-deployment-preflight.md) retains separate staged authorizations.

## Production Stage 0 platform evidence

**PRODUCTION AZURE SQL PLATFORM STAGE 0: PASS**

The existing authorized operator reported manually running this command against actual production `Shale`:

```text
sqlcmd -S tcp:shale.database.windows.net,1433 -d Shale -U brian -N -b -l 5 -t 15 -i docs/sql/ai-integration/stage0-platform-verify.sql
```

Exact reported result (execution timestamp was not supplied):

```text
EngineEdition = 5
Platform = AZURE_SQL_DATABASE
ExternalDataSourceFindingCount = 0
ExternalTableFindingCount = 0
ScopedCredentialFindingCount = 0
SynonymFindingCount = 0
CrossDatabaseDependencyFindingCount = 0
OwnershipRouteFindingCount = 0
PublicAuthorityFindingCount = 0
PlatformStatus = PLATFORM_COMPATIBLE_PENDING_IDENTITY_AND_CATALOG_ATTESTATION
```

This is operator-reported live production Stage 0 evidence, not an agent rerun, identity/catalog
attestation or deployment authorization. The operator reported no SQL migration, principal provisioning,
Azure configuration change, JAR deployment or AI capability activation.

## Authoritative fixture and execution environment

The supplied private export was treated as data, hashed before decoding and left unchanged/uncommitted.
`python3 build/sql-confinement/prepare-authoritative-fixture.py <original-source> <private-output>`
reproduced the reviewed fixture before any SQL execution:

| Artifact | Bytes / SHA-256 |
| --- | --- |
| Original `shale-schema-source.sql` | 728862; `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda` |
| Derived schema | `40a665caefb3203a6c364d0caac3fadcc1a58d24a66a994f48e058b73ae6f243` |
| Reviewed candidate | `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f` |

All 96 tables, five functions, three schemas, 208 indexes and 1142 ALTER TABLE batches retain their
reviewed shapes. No generated substitute or receipt reissue was used.

Task-owned SQL Server 2022 Developer `16.0.4305.5` used the pinned base image
`mcr.microsoft.com/mssql/server@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
The external-table fixture added matching PolyBase `16.0.4305.5-1`, package SHA-256
`476f61739090c07ac1aeec1ca7c3f898f63422ab54d2a075032f4b6f6e0c34fa`, to execute a real external-table
fault. Only nonce-owned databases/logins and synthetic rows were used: ordinary confinement and
production serving on loopback port 14333, and the isolated external-table case on port 14334.
Both owned containers were removed after exact-label checks. Private SQL, credentials, keys and logs
remain uncommitted. Maven 3.9.11/JDK 21 use an isolated cache and a test truststore containing the owned
localhost SQL certificate and system roots. Production-composition JDBC validates encryption,
certificate and hostname. The rejected external route's nested SQLPAL ODBC connector uses a local-only
certificate trust override; that setup is not counted as TLS acceptance.

## Verification-discovered defect and regression coverage

Fresh authoritative execution of #1925 found **29 tests: 25 pass, 1 failure, 3 errors, 0 skips**.
Valid identities failed at `OWNERSHIP`: the AI ownership query had one parameter but bound four,
and its subqueries incorrectly used the verifier session's token. The corrected query uses four
parameters to inspect schema/object/principal ownership and grant option for each actual AI token,
through the independent verifier. Separate verifier-token checks remain intact. No check was removed.

Pre-edit review covered the repository rules, database/tenancy contracts, confinement and Azure
architecture, prior composition evidence and SQL runbook. Searches inspected all attestation callers,
ownership/token/binding queries, live SQL tests, readiness and serving helpers before edits.
`AiCaseDataLocalSqlServerTest`, `McpProductionReadinessLocalSqlServerTest`, and
`Phase3b7AcceptanceLocalSqlServerTest` own the driver-level and full Boot/HTTP regressions.

The shared **42-fault** matrix independently injects direct/schema/database/column/view/function/
public grants, public EXECUTE and grant option, plus schema/role ownership, impersonation, DDL and
GRANT/CONTROL authority for AI and verifier. Full Boot returns fresh `not-ready` for each fault and
fresh `ready` after removal. Actual HTTP serving denies all 42 discovery and 42 protected Overview
requests with 401 and no structured/protected output, then reattests after restoration. The inherited
public Case SELECT regression positively proves excess verifier visibility before asserting refusal.

Actual-login tests also cover direct Case/join/credential/control read denials, mutable/read-only
SESSION_CONTEXT tenant/user poisoning, direct foreign PHI INSERT denial, **all 42 legacy EXECUTE
denials plus private helpers**, effective grants, module/catalog drift and separate SID/token proofs.
The Azure isolation helper is executed against real shared local catalogs containing an external
source, external table, synonym and explicit cross-database dependency. The external table points to
an owned local marker, never production. These tests prove local structural refusal, not native Azure
contained authentication or catalog completeness. Unit routing/ambiguous-result tests remain supplemental.

## Fresh mandatory results

Exact commands and per-class counts are in the [verification JSON](azure-sql-case-attestation-verification.json).
Counts overlap and must not be added as unique tests. All final groups have zero failures/errors.

| Group | Passed | Failures/errors | Skipped |
| --- | ---: | --- | ---: |
| authoritative-final | 50 | 0/0 | 0 |
| focused-final | 45 | 0/0 | 0 |
| affected | 137 | 0/0 | 9 |
| critical | 226 | 0/0 | 0 |
| package-final | PASS (build) | 0/0 | 0 |
| Selector Python | 27 | 0/0 | 0 |
| TypeScript client vectors | 66/66 | 0/0 | 0 |
| Python client vectors | 66/66 | 0/0 | 0 |
| Owned validated TLS/proxy checks | 20/20 | 0/0 | 0 |

The zero-skip authoritative partitions contain **31 data confinement/parity/security tests** and
**19 production-profile cases**: full Boot readiness/ordinary smoke (1), durable SQL OAuth lifecycle
(12), independent-client live lock/expiry drills (2), both full 66-vector clients (2), validated Caddy
TLS/proxy (1), and authority-fault serving (1). No required vector or mandatory local case was skipped.
The normal affected invocation supplies no SQL opt-in; its reported skips are not passing SQL evidence.
The required cases execute separately in the zero-skip authoritative run. No native Azure test was
substituted with a mock. Historical opt-in suites outside this fresh scope are not represented as run.

Both expiry drills positively observe the exact final-gate blocker, cross the stored DB-UTC expiry
before releasing its lock, prove a subsequent SQL UTC sample using XEvents, and deny protected output.
Proof lifetime, SQL/Java authorization deadlines and lock drill are unchanged. Both clients also execute
live authority reduction/revocation, bearer expiry/revocation, cancellation, commit/ACK/close/audit faults
and privacy checks. The 12 SQL-backed OAuth tests exercise transaction/consent/code consume, PKCE,
assertion replay, opaque access issuance, refresh rotation/reuse family revocation and explicit revocation;
the clients supply fresh bearer/expiry/live-authority evidence through the confined production path.

Caddy 2.11.7 performs real TLS and proxy serving, including rejected wrong-host/untrusted handshakes,
canonical metadata despite hostile forwarding headers, exact Origin handling, protected Overview and
credential-bearing HTTP redirects. Its 20 checks use actual sockets; zero-valued receipt entries mean
refused TLS handshakes. Local proxy evidence does not certify deployed Azure ingress.

Focused regressions cover attestation/data, production composition, both MCP tools, filter isolation,
OAuth configuration and production OFF Boot. Affected cases/security-data selection, the critical
reactor, selector Python and Boot packaging are fresh. The initial focused setup error from overlapping
Maven reactors is retained as a resolved attempt; final reactors use isolated target directories and
run sequentially within each worktree. Static diff checks pass. The optional historical full-suite
profile is not a replacement for these required gates.

A combined PolyBase run also encountered four socket timeouts during migration import, before the
affected security assertions. No timeout or authorization deadline was extended. The final authoritative
suite is partitioned into 30 ordinary data cases on the original pinned fixture, one real external-table
case on the PolyBase fixture, and 19 production serving cases on the original fixture. Every partition
must pass with zero skips; the failed setup attempt is retained in the JSON and is not passing evidence.

## Separate remaining Azure and production gates

No already-authorized disposable Azure SQL Database fixture or Azure acceptance credentials are
configured. No Azure fixture or production identity was provisioned. The **Azure disposable mutation/fault
suite remains a deployment gate**, including actual contained-principal and external-catalog semantics.
The local structural tests and operator Stage 0 result do not close it.

The remaining distinct gates are governed contained AI identity; governed contained verifier identity;
bootstrap identity; independent secrets; exact database token/SID proof; complete live attestation;
secret-consumer separation; immutable deployment binding; and the authorized disposable Azure fault
fixture. After separately governed provisioning, run `AiCaseDataAzureSqlDatabaseReadOnlyTest` with the
explicit `AZURE_SQL_DATABASE_READ_ONLY` opt-in and independent target settings described in the
[Azure design contract](../architecture/azure-sql-case-attestation.md). Supply through approved secret
handling `SHALE_AZURE_ATTESTATION_OPT_IN=AZURE_SQL_DATABASE_READ_ONLY`, `SHALE_AZURE_JDBC_URL`
(SQL authentication, explicit `databaseName`, no TLS overrides), `SHALE_AZURE_DATABASE`,
`SHALE_AZURE_ISSUER`, `SHALE_AZURE_RESOURCE`, and independent
`SHALE_AZURE_{AI,VERIFIER,AUTH,RUNTIME,BOOTSTRAP}_{USERNAME,PASSWORD}`. Use the actual JVM/SQL
time-zone mapping and immutable binding. The later read-only acceptance command is:

```bash
mvn -B -pl shale-data -am -Dtest=AiCaseDataAzureSqlDatabaseReadOnlyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Require one executed test,
zero skips/failures, actual edition 5 and complete live attestation. Measure it within the unchanged
ten-second protected request budget; do not widen authority or extend deadlines to pass.

**No new SQL migration is required by this PR.** Future production preparation must inventory and
review any unapplied existing migration chain, dedicated principal provisioning and immutable binding;
the platform result establishes none of that catalog state. Existing legal, SDK, security/operator/
tenant-owner and pilot approvals remain separate. Audit compatibility uses existing PHI/integration
transactions unchanged; data-free attestation adds no mutation/PHI audit schema or event.

No production SQL, identities, Azure settings, JARs or AI/MCP/OAuth controls were changed.
Production identity/catalog attestation remains **OPEN**. Deployment and pilot remain **NOT AUTHORIZED**.
The [#1925 record](https://github.com/gseshadow/Shale/blob/a8b216f6cc89897121988c675b0ea58280a80cca/docs/testing/azure-sql-case-attestation.md)
retains the historical blocked evidence; it is not presented as a fresh pass.

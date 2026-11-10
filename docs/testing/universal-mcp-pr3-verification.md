# Universal MCP PR 3 verification

Baseline `85f8b9d9e43d0f7c991bd6bf608252a8c7bfeddc` (merged #1946).
[Security design, migration review and exact deployment prerequisites](../sql/ai-integration/universal-pr3-review.md).

## Pre-edit impact review

Reviewed repository bootstrap/rules, database schema, tenancy/RLS and system overview; universal onboarding
PR 3, AI integration SQL foundation/OAuth lifecycle, confinement V1/V2 verification/attestation, current
production readiness/binding receipts and local test-selection guidance. Inspected existing implementations
before edits and searched their tests, SQL field/binding lists and receipt/permission dependencies.

| Changed contract | Tests inspected/maintained and regression protected |
| --- | --- |
| New registry/approval SQL and service adapters | `AiApplicationLocalSqlServerTest`: actual restricted logins, exact identity, separate tenants, lifecycle, audit rollback, RowVer, expiry, retained history, manual preservation and independent receipts. `AiApplicationContractTest`: invalid input before SQL and disconnected/default dormant composition. |
| Shared transactional boundary | Existing `AiSql` and `AiSecurityContractTest`: withhold results on failed completion/close. No changes to this helper. |
| Additive audit entity/state | Existing `EntityActionAuditEventTest`, `EntityActionAuditMigrationContractTest`, `EntityActionAuditEntityTypeConstraintMigrationTest`; updated closed-state event/reader tests and `ContactPhase2BAuditMigrationContractTest` cumulative vocabulary. Historical allowlists stay historical; the new successor supplies only its new discriminator. |
| Legacy tenant/client behavior | Inspected and ran existing `AiSecurityLocalSqlServerTest`; existing exchange/credential/revocation contracts, manual OAuth client mapping, browser, metadata and consent/exchange policy tests remain authoritative. |
| Confinement | Inspected existing migration/attestation tests and source/module/dependency/permission receipt checks. No authoritative Case SQL fixture or native Azure acceptance is claimed; candidate installation explicitly refuses coexistence pending compatibility review. |

New tests are owned by the security/RLS area. The pure new contract test joins the local critical list;
the SQL fixture still requires explicit opt-in. No workflow, dependency, project version, production setting,
credential or immutable binding change. The generated full-suite-only inventory is already stale on the
baseline; regenerating it would add hundreds of unrelated changes, so it is left unchanged.

## Disposable SQL setup and commands

Actual fixture: SQL Server 2022 Linux image
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`,
loopback host port 14333, isolated disposable databases and random login names/passwords. No authoritative
Case/production schema or tenant data is installed. New SQL fixture connections use `encrypt=true`,
`trustServerCertificate=false`, a private test truststore and explicit certificate hostname `localhost`.
The certificate is generated for this fixture; no system/production trust is changed. The existing legacy
fixture retains its original explicit self-signed test connection behavior.

Reproduce with a dedicated disposable loopback SQL instance only. Generate a test RSA certificate with
CN/SAN localhost, configure SQL TLS to that certificate/key, import that certificate into a PKCS12 truststore
(password `synthetic-trust`), and force encrypted SQL. Supply the following environment privately, never
put an SA password in command arguments, test output or committed files:

- `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`
- `SHALE_AI_SQL_PORT=<dedicated loopback port, 1024–65535>`
- `SHALE_AI_SQL_PASSWORD=<disposable fixture SA password>`
- `SHALE_AI_SQL_TRUSTSTORE=<absolute path to test PKCS12 truststore>`

Use Java 21 JDK and Maven 3.9.11. Session commands used a local Maven repository, proxy settings and JVM
truststore to access dependencies; these are environment-only options, not project configuration.
The equivalent exact test commands (with normal `mvn` on PATH) are:

```sh
mvn -B -pl shale-data -am \
  -Dtest=AiApplicationLocalSqlServerTest,AiApplicationContractTest,EntityActionAuditEventTest,AiSecurityContractTest,AiOAuthCredentialContractTest,AiOAuthExchangeContractTest,AiOAuthRevocationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-data -am -Dtest=AiSecurityLocalSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The tests create/drop only nonce-owned databases and logins on that loopback instance. The application
migration does not create a login, seed an application/tenant/grant or enable either policy. Synthetic
test setup alone enables the two fixture policies and seeds retained family rows as explicit storage
fixtures; approval never issues those rows. These are storage/lifecycle tests, not a live CIMD OAuth
connection, stock-client URL-only test or assertion replay test (those remain PR 4/7).

## Results

Focused run: **36 passed, zero failures/errors/skips**, including **9 real SQL scenarios**:

1. Exact shared CIMD identity, one application, separate A/B approvals and suspended integration IDs;
   foreign/non-admin decisions rejected; no grant/session/credential/redirect/key created by approval;
   existing runtime consent cannot use a suspended integration.
2. Competing approve/deny has exactly one winner; stale decision replay and terminal approval fail;
   denial history remains and replacement has a new ID.
3. Publication revisions/disablement and stale/expired requests fail closed, including expiry with a
   current RowVer. No stale revision is silently upgraded.
4. Injected global/request/approval/revoke audit failures roll back registry, request, suspended integration,
   approval and retained credential mutation together.
5. Actual restricted publisher/tenant/bootstrap logins deny direct tables, DML and impersonation;
   an independent test probe observes FILTER and INSERT BLOCK error 33504, owner immutability,
   history-delete rejection and no visibility with missing/foreign context.
6. Two tenants' explicit synthetic grant/family rows remain independent: A ceiling reduction/revocation
   permanently revokes A lineage while B grant/session/credential stay unrevoked; increases/reopening fail.
7. Full identity is compared after digest lookup; deliberate forged-hash fixture collision fails;
   case-distinct CIMD paths retain distinct identities; initial publication replay fails.
8. Independent registry verification passes; old foundation receipt still verifies unchanged;
   rerun is rejected and altered catalog fails verification without receipt replacement.
9. Active request replay and administrator removal before decision are rejected.

Every scenario also checks the pre-existing manual client RowVer is unchanged by migration.

Remaining regression commands and results are recorded below after local execution.

## Evidence limits and deployment dependency

The compatibility guard, production/native Azure gates and PR 4 live binding/consent dependency remain
open exactly as listed in the linked review. No production connection, SQL, settings, credential change,
deployment, feature activation, migration replay or immutable binding update occurred. Local receipts and
synthetic SQL are not production permission/catalog acceptance. RowVer transition replay tests do not
claim OAuth assertion/code/refresh replay integration. No new public registration capability is advertised.

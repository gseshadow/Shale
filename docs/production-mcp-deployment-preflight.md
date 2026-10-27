# Production MCP deployment preflight

This is a future governed sequence, not execution authorization. The production composition's
[prior SQL Server local acceptance passed](testing/production-mcp-case-composition.md), including the
inherited-verifier-authority fix. The [Azure SQL Database successor](testing/azure-sql-case-attestation.md)
is currently **BLOCKED pending actual SQL/Azure acceptance**. Do not apply the prior platform-limited
READY classification to this unverified successor. No deployment, production SQL, principal
provisioning or activation is authorized by either local result.

## Configuration and safe deployment state

All controls default false. Existing JDBC configuration and certificate validation remain required.

| Setting/control | Category | Safe readiness-only state |
| --- | --- | --- |
| `shale.ai.mcp.case-data-composition-enabled` | SQL composition/readiness | Explicitly true only after separate readiness authorization |
| `shale.ai.oauth.identity` | Identity | PRODUCTION, profile azure/prod, no test/dev/local profile |
| `shale.ai.oauth.public-origin` | Identity | Canonical HTTPS origin; issuer appends /oauth/mcp, resource appends /mcp |
| `shale.ai.data.jdbc-url`, `.username`, `.password` | Readiness configuration | Restricted AI SQL identity; password is a secret |
| `shale.ai.data.database` | Readiness configuration | Exact expected database |
| `shale.ai.data.verifier.jdbc-url`, `.username`, `.password` | Readiness configuration | Separate catalog verifier; password is a secret |
| `shale.ai.oauth.bootstrap.jdbc-url`, `.username`, `.password` | Readiness/activation configuration | Separate bootstrap identity; password is a secret |
| `shale.ai.oauth.composition-enabled` | OAuth composition | false |
| `shale.ai.oauth.authorization-enabled` | OAuth authorization | false |
| `shale.ai.oauth.exchange-enabled` | OAuth issuance/revocation | false |
| `shale.ai.enabled` | Application capability | false |
| `shale.ai.mcp.enabled` | Transport activation | false |
| `shale.ai.mcp.search-cases-enabled` | Search activation | false |
| `shale.ai.mcp.get-case-overview-enabled` | Overview activation | false |
| `shale.ai.mcp.allowed-origins` | Transport Origin policy | Explicit exact reviewed HTTPS list before activation; no wildcard |
| `AiProductCapability.Enabled` | Database capability | 0 |
| `TenantAiIntegrationPolicy.Enabled` | Tenant capability | Missing/disabled |
| Registration/grant/session/credentials | Integration authority | No real authority provisioned by this sequence |

AI pool aliases are `SHALE_AI_DATA_JDBC_URL`, `SHALE_AI_DATA_USERNAME`, `SHALE_AI_DATA_PASSWORD`.
Use exact Spring properties or verified bindings for other settings. Store passwords only through
approved secret mechanisms; no values in command history, logs or evidence. Preserve existing ordinary
auth/runtime secrets, token signing secret, session cutover boundary and browser CORS consumers.
Readiness uses read-only SQL catalog/reference/identity queries, not Case data or OAuth issuance.

## Stage 0 Read only inventory

The [Azure attestation contract](architecture/azure-sql-case-attestation.md) replaces the invalid
SQL Server/MI-only platform assumption. Its current acceptance is **BLOCKED pending live evidence**;
Azure SQL Database is not intrinsically a blocker. Unknown/unsupported engines always STOP.

Run the bounded read-only platform dispatcher using an already authorized operator connection:

```bash
sqlcmd -S "$SQL_SERVER" -d "$SQL_DATABASE" -U "$SQL_INVENTORY_USER" -N -b -l 5 -t 15 -i docs/sql/ai-integration/stage0-platform-verify.sql
```

Supply the password through the approved secure prompt/secret mechanism, never a command argument.
Certificate validation stays enabled (no `-C`). This command is an operator recheck instruction;
it was not run on production by this change.

| Positive engine detection | Required verifier |
| --- | --- |
| EngineEdition 5 | `azure-sql-stage0-verify.sql`, dispatched automatically; no server permission/principal/endpoint/login-token query |
| EngineEdition 8 | Managed Instance server catalog verifier branch |
| EngineEdition 2, 3, 4 | Supported SQL Server server catalog verifier branch |
| NULL / any other edition | STOP |

The Azure script requires existing CONTROL DATABASE operator visibility **only for this read-only
inventory**, including scoped credentials. Never grant it to the application/verifier. It returns
one bounded count/status row; zero external sources/tables/scoped credentials/synonyms/cross-database
dependencies/unsafe ownership routes/unexpected public grants is required. Missing visibility stops.
Names, credential identities/locations, PHI and secrets are never output. A platform-compatible
result is not migration, identity, catalog, readiness or disclosure acceptance. The existing general
inventory below and all later independently authorized stages remain required.


**READ ONLY — SAFE PREFLIGHT.** Establish actual engine/edition, database/login/user, NULL application
context, migration/source/catalog receipts, roles and effective permissions; inventory Cases, Users,
auditLog and Statuses predicates, owners/triggers/constraints and enabled capability/tenant counts.
No credential/hash/PHI rows may be exported. Missing visibility means UNKNOWN, never an empty pass. Include inherited
`public` grants when reviewing the catalog verifier: exact role membership and its own direct grants
do not exclude public Case/application authority. Production attestation rejects unexpected public
application grants and grant options. Inventory existing grants read-only; do not inject the local
adversarial fault or automatically revoke permissions in production.
Verify existing Stage A/B, remembered/session security and legacy retirement before proposing changes.
Preserve the October 20 operator-reported retirement; do not replay it. Stage A's historical preflight
predates the three FILTERs and refuses their presence; reconcile uncertain state instead of weakening it.

## Stage 1 SQL migration

**DO NOT RUN — FUTURE SQL CHANGE.** After explicit SQL authorization and reviewed prerequisites, install
only unapplied migrations: phase3b1.sql -> phase3b2b.sql -> phase3b2c1.sql -> phase3b2c2.sql ->
revocation.sql -> phase3b2d.sql -> phase3b3.sql -> phase3b7-confinement.sql. No phase3b2a.sql exists.
Use fresh dbo/autocommit/NULL-context sessions and stop on errors. phase3b2b is not rerunnable.
Never rerun historical installers over a newer catalog or replace receipts. Follow the SQL README.
Run cumulative verify.sql after all migrations. Its confinement branch equals the standalone verifier;
completion without errors and ConfinementCatalogDriftFindingCount=0 establishes only that catalog check.
Its early return means separate state and actual-login permission checks remain necessary.

## Stage 2 Restricted identities and immutable binding

**DO NOT RUN — FUTURE SQL CHANGE.** After SQL verification, separately provision SQL-authenticated AI
and catalog identities. On EngineEdition 5 use dedicated database-contained users (type S,
authentication_type 2), independent secrets and explicit target database; no server login/master
user or server role. Login-mapped and external/group AI/verifier identities fail closed. AI membership is own SQL user/public/AiCaseDataRuntime only, with the six exact
role EXECUTEs and no direct data/PHI/legacy EXECUTE/impersonation/DDL/grant authority. Catalog membership
is own SQL user/public/AiCaseDataCatalogVerifier, with the existing definition/dependency/four reference
table permissions. SQL Server/MI additionally need separately governed server VIEW ANY
DEFINITION/VIEW ANY DATABASE. Azure SQL Database uses the reviewed contained-principal and
database-scoped contract; no server metadata grant or broader role compensates for missing catalogs.
Confirm independent secret consumers and the Stage 0 scoped-credential inventory. After provisioning,
run the explicit live read-only Azure attestation gate documented in the acceptance record. A local
SQL Server run cannot close it.
Provision exactly one AiCaseDataDeployment row with Id 1, case-read-v1, canonical issuer/resource and
the actual existing JVM/SQL time-zone mapping. It is immutable; do not guess UTC or update a binding.
Verify the real AI login's tokens, column permissions, six EXECUTEs, 42 denials, no ownership, cross-DB
or escalation route, and distinct AI/verifier/bootstrap versus ordinary auth/runtime SIDs.

## Stage 3 Configuration

**DO NOT RUN — FUTURE AZURE CHANGE.** Provision dedicated configuration/secrets while every switch,
including readiness, remains false. Do not change DNS/proxy/certificates or ordinary configuration.
AI URLs cannot override TLS/authentication/MARS/timeouts. Do not use test profile or TEST identity.

## Stage 4 Build and deploy

**DO NOT RUN — FUTURE DEPLOYMENT.** After local acceptance, build the reviewed successor source using
JDK 21 and `mvn -B -pl shale-server -am clean package`, capture SHA-256, retain the previous executable,
and deploy only the executable JAR (never .jar.original) through the approved App Service process.
The inspected parent/server version is 1.0.138; record the exact candidate source and artifact identity.
Keep every disclosure/OAuth/tenant control OFF. Repository version/health alone does not prove production parity.

## Stage 5 Readiness only

**DO NOT RUN — FUTURE AZURE CHANGE.** Separately authorize only
`shale.ai.mcp.case-data-composition-enabled=true`, with the production identity and dedicated settings.
Restart/drain through the approved lifecycle and observe the bounded `AI_CASE_SQL_READINESS ready`
startup marker. Missing configuration or failed initial attestation blocks construction/startup.
**READ ONLY — SAFE PREFLIGHT.** Verify ordinary health/auth/session compatibility, effective OFF settings,
SQL receipts/permissions, deployment binding and real public TLS/proxy/Origin/logging policy. The marker
is a startup observation; internal `McpCaseReadiness.state()` reattests, and protected requests always
reattest. No new public readiness endpoint or background monitor is introduced.

## Stage 6 No disclosure smoke

**READ ONLY — SAFE PREFLIGHT.** With readiness ready and OAuth/MCP/handlers/capabilities OFF, verify
no token issuance, no usable MCP route and no handlers. Confirm ordinary health behavior. Do not issue
a credential, enable transport or register a real integration merely to manufacture a smoke test.

## Stage 7 Pilot

**DO NOT RUN — FUTURE AZURE CHANGE.** Obtain the separately recorded legal retention, SDK prerelease,
security/operator/tenant-owner approvals and explicit pilot authorization. Verify monitored capacity,
failover/restart, installed caller/native parity and actual ingress. This candidate supplies no live
activation commands. OAuth composition/authorization/exchange, application capability, transport, each
handler, SQL global capability, tenant and integration/grant authority remain independent controls.

## Monitoring and containment

Before pilot establish safe OAuth/MCP authentication/authority denial rates, quota/throttling, audit
failure, SQL attestation and ai-case-data-pool readiness, final-handoff denial, unexpected activation,
5xx/timeouts and ordinary regressions. Do not log proofs, bearer/assertion/code parameters, PHI, SQL
binds or request/response bodies. Monitoring/dashboard availability is not established by source alone.

Containment is capability/transport/handlers OFF -> governed integration/grant/session/credential
revocation -> drain AI pools/disable dedicated connection if necessary -> verify no new disclosures.
Retain all audits, invocation/quota history, revocations and receipts. Never restore broad-runtime
fallback, erase history or automatically uninstall SQL.

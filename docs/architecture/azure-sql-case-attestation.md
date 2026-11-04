# Azure SQL Database Case attestation contract

Current disposable native-gate packet (2026-10-29):
[operator commands](../testing/native-azure-readonly-operator-runbook.md) and
[unexecuted acceptance record](../testing/native-azure-readonly-acceptance.md).
The native JUnit gate now requires a nonce-named disposable V2 database and reserved test binding;
it must never target production. Deployed production attestation remains a separate future
`AiCaseDataAttestation`/production-readiness operation. Production authority/audits are unchanged.

Date: 2026-10-27. Base: `efd8be8a350523344bf69b72bbc715f9e888589d` (#1924).
This supplements the [confinement contract](phase3b7-sql-confinement-contract.md).
Current evidence (2026-10-28): **production Stage 0 platform PASS** (operator-reported),
**authoritative local/adversarial acceptance PASS**, **production identity/catalog attestation OPEN**.
See the [current acceptance](../testing/azure-sql-case-attestation.md) for the preparation-only classification;
production deployment and pilot remain **NOT AUTHORIZED**.
Azure SQL Database itself is supported by the proposed contract, rather than classified as an
inherent deployment blocker. This is neither deployment nor activation approval.

## Platform and principal decision

`SERVERPROPERTY('EngineEdition')` is positively identified on every connection. Editions 2/3/4
(SQL Server) and 8 (Managed Instance) retain the existing server contract. Edition 5 selects
`AZURE_SQL_DATABASE`. NULL, unknown editions, unavailable catalogs and mixed platforms fail closed.
There is no missing-object exception fallback. Azure pools must also agree on the live logical
server and target database, rather than comparing unrelated contained SIDs across databases.

Use **SQL-authenticated database-contained users** for AI and verifier on Azure SQL Database:
`sys.database_principals.type='S' AND authentication_type=2`. Require non-dbo/non-system principal
IDs, nonempty database SIDs, original authenticated name equal to the current database user, and
agreement between the session's own principal row and the independent verifier's row (SID, type,
authentication type, ID and name). Also require this connection's own
`sys.dm_exec_sessions.authenticating_database_id=DB_ID()` and original security SID equal to the
current database user's SID. A matching contained catalog row alone must not disguise a session
authenticated through a login or impersonation. [Microsoft's session DMV](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-exec-sessions-transact-sql?view=sql-server-ver17)
distinguishes login authentication (0) from contained database authentication. Only the own-session
row is read; no database/server state privilege or other-session inventory is granted. Missing
own-session evidence fails closed and must be investigated in live Azure acceptance. Database evidence is queried from SQL; caller-set session context
and configuration names cannot supply it. Login-mapped SQL users (`authentication_type=1`), external
users/groups, users without login, certificates and keys are not alternate acceptable models.

[Microsoft's contained-user model](https://learn.microsoft.com/en-us/sql/relational-databases/security/contained-database-users-making-your-database-portable?view=sql-server-ver17)
authenticates inside the selected database independently of `master` logins.
[Database principal authentication types](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-principals-transact-sql?view=sql-server-ver17)
distinguish DATABASE from INSTANCE/EXTERNAL; a SQL-looking name is insufficient.
[Azure's access model](https://learn.microsoft.com/en-us/azure/azure-sql/database/logins-create-manage)
supports both login-mapped and contained users. This contract deliberately accepts only the latter
for these two credentials. It does not convert existing auth/runtime/bootstrap identities.

Separately authorized provisioning, **not application startup**, creates each user with its own
random secret using `CREATE USER [reviewed-user] WITH PASSWORD=<separately supplied secret>` in the
target database. Add AI only to `AiCaseDataRuntime` and verifier only to
`AiCaseDataCatalogVerifier`; retain their existing exact grants. Do not create a corresponding
server login, master user, server role membership or broad metadata role. No passwords belong in
scripts, repository, command arguments, evidence or receipts. The existing dedicated AI/verifier
JDBC settings require explicit database selection, independent secrets, validated TLS and independent
pools; no ordinary pool may use either credential. Inventory deployed secret consumers and prohibit
secret reuse elsewhere. Source cannot prove secret-consumer exclusivity: that is an operator gate.

Production still compares live AI/verifier/bootstrap SIDs against ordinary auth/runtime and each
other as #1924 requires. Ordinary auth/runtime may share one SID. Bootstrap must differ from both;
AI and verifier each differ from every other identity. All comparisons must resolve in the same
Azure target database/server; ambiguity refuses readiness.

## Complete current-check inventory and replacement mapping

All rows preserve security-essential **properties**; engine-specific catalog availability is evidence,
not a permission to omit a property. D=database-scoped; S=server-scoped; A=Azure difference.
Test names below identify required evidence, not assertions that an unexecuted test passed.

| Current check / scope | Why it exists | Azure equivalent | Security property preserved | Residual risk / test evidence |
| --- | --- | --- | --- | --- |
| `SUSER_SID`, `ORIGINAL_LOGIN`, `IS_SRVROLEMEMBER('sysadmin')` / S,A | Exclude shared/bypass login and impersonated sessions | Own database SID, original name, non-dbo ID, S/DATABASE type confirmed independently and own-session authenticating database/SID; database/server equality | Dedicated authenticated non-bypass identity | Actual Azure principal semantics require live read-only gate; focused routing/identity rejection tests supplement it |
| AI and verifier `sys.login_token` / S,A | Exclude inherited groups/server roles | No login authority under contained SQL authentication; exact database user token and contained type | No inherited server/group privilege path | Login-based and Entra models refused; no assertion about Azure login-token rows. Routing test proves no query is issued |
| `fn_my_permissions(NULL,'SERVER')` / S,A | No server control, tracing, DDL or impersonation authority | Contained model plus exact database authority allowlist and metadata permission closure | Principal cannot elevate through server grants | Does not attest privileged operators/host diagnostics; those remain external governance |
| Verifier `VIEW ANY DEFINITION` and `VIEW ANY DATABASE` / S,A | Avoid incomplete server inventory/cross-DB enumeration | Database `VIEW DEFINITION` prerequisite, exact verifier token/grants and retained catalog receipt | Complete reviewed target-database metadata without data authority | Scoped credentials have a different visibility rule; separate gate below |
| AI/public `sys.server_permissions` joined to `sys.server_principals` / S,A | Detect specific-login impersonation and grants hidden from server-wide effective permission list | S/DATABASE principal model, exact database token, all direct permission edges, exact role receipt and public edge scan | No login-grant route, no database impersonation/control/grant option | Cannot reproduce server-wide inventory safely from target DB; different provisioning model replaces its need |
| Verifier `sys.server_permissions` / S,A | No verifier data/escalation authority inherited from server | Same contained restriction, verifier effective database/object allowlists, exact role receipt and direct/public grant checks | Catalog-only verifier cannot inherit server authority | Never compensate with server roles or CONTROL; live Azure gate required |
| Public CONNECT on four non-admin TSQL endpoints via `sys.endpoints` / S,A | Allow only harmless transport baseline | Azure-managed TDS boundary, contained authentication and database CONNECT only | No admin endpoint authority acquired by principal | Logical-server transport/RBAC/firewall configuration is operator inventory; no user-managed SQL Server endpoint proof is claimed |
| `sys.databases` enumeration + actual AI `HAS_DBACCESS(other)` / S,A | Reject cross-database access | Database-contained authentication plus reject all external sources/tables/synonyms/cross-DB expression dependencies; deny bulk/external/DDL/credential authority via permission closure | No ambient cross-DB login route and no configured in-database bridge | Cannot enumerate every other database or detect reused passwords safely from target DB; operator identity/secret gate and Stage 0 credential inventory |
| `sys.user_token` exact own/public/AI or verifier role / D | Exclude fixed/custom/nested roles/groups | Retained; verifier enforced for Azure even outside production composition | No inherited application/bypass authority | Actual SQL attacks and read-only Azure gate; extra role/token refuses |
| Direct database permissions, grant option, public edges / D | DENY does not neutralize every grant/column/ownership path | Retained and strengthened: any direct permission except plain CONNECT refused; public metadata DENY that could hide catalog also refused | No impersonation, schema/database DDL, grants/control/take-ownership or public application authority | Fresh authoritative local actual-login/public/readiness/serving matrices pass; native Azure mutations remain OPEN |
| Schema/object/principal ownership / D | Ownership can bypass permission DENYs | Retained for all AI tokens; expanded to all verifier tokens | Neither owns application schemas, modules or roles | Empty-schema and role ownership attacks required |
| Every application object/column + exact six EXECUTEs / D | Reject views/functions/synonyms, column exceptions, broad application execution | Retained; Azure verifier also checks effective object rights: VIEW DEFINITION, plus SELECT only on four reviewed reference tables | No direct Case/PHI/control/credential read/write, exactly six AI public procedures, zero verifier application EXECUTEs | Module receipts retain 42 denials and private helper protection; no naming-based allowlist expansion |
| Catalog hash, source receipt, module body/owner/execute-as/call graph / D | Prevent a narrow grant from invoking altered/broad code | Unchanged packaged SQL/manifest/source bytes and runtime checks | Reviewed SQL/RLS/security-policy definitions and dependencies | Full authoritative candidate still required; no repaired/reissued receipt |
| Database trustworthy/chaining bits in receipt / D,A | Prevent ownership/cross-DB bridges | Retained receipt plus Azure explicit zero-state check | No accepted ownership-chaining escape | Azure platform behavior needs live validation; unexpected state refuses |
| Deployment database, issuer/resource, Java/SQL wall-time mapping / D | Bind exact deployment and PHI timestamp semantics | Retained, plus Azure live server equality | No foreign deployment/binding/time-zone substitution | Existing 2025–2028 wall-time probes remain; no UTC assumption |
| Per-request attestation, proof-bound read, final handoff / application + D | Drift/expiry/revocation must withhold disclosure | Unchanged request path; either platform must finish every check | No cached ready result authorizes protected serving | Existing final-gate SQL lock/expiry, both clients and audit/close/cancel suites remain mandatory |

## Azure semantics and confinement argument

[Database tokens](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-user-token-transact-sql?view=sql-server-ver17)
include effective database roles, including public. Membership alone does not establish absence of
public grants. [Database permissions](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-permissions-transact-sql?view=sql-server-ver17)
include object/column/state edges; fixed-role authority also requires token checks. Both are retained.
Public table/view/function/procedure/schema/database/column grants and grant options are refused,
even when a role DENY still makes an AI direct read fail. This also protects the verifier.

[HAS_PERMS_BY_NAME](https://learn.microsoft.com/en-us/sql/t-sql/functions/has-perms-by-name-transact-sql?view=sql-server-ver17)
is used for supported database/object evidence, with the separately complete catalog determining the
objects. NULL never proves the required VIEW DEFINITION prerequisite. No Azure SERVER probe is used.
The prior SQL Server invisible-object error 15151 handling is retained only for empty object allowlists;
it is not an unknown-platform or missing-catalog fallback.

[EXECUTE AS USER](https://learn.microsoft.com/en-us/sql/t-sql/statements/execute-as-transact-sql?view=sql-server-ver17)
is database scoped; EXECUTE AS LOGIN is unavailable on Azure SQL Database. The application is not
granted impersonation. The existing tiny owner-executed locator remains pinned and inaccessible
directly. Its static caller/owner chain is unchanged; it is not permission to run Case queries as dbo.
[sys.login_token](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-login-token-transact-sql?view=sql-server-ver17)
is documented for SQL Server; its availability/rows are not assumed to prove Azure containment.

[Azure fixed logical-server roles](https://learn.microsoft.com/en-us/azure/azure-sql/database/security-server-roles)
can propagate authority for login-based users (including database connector and state/definition
readers). Master dbmanager/loginmanager roles also exist. They are not grants to a contained SQL
user in this target database. Accepting login-mapped or Entra principals would require a different
review, rather than trusting target-database server metadata to show the entire logical server.

[Elastic query](https://learn.microsoft.com/en-us/azure/azure-sql/database/elastic-query-overview)
and [sp_execute_remote](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-execute-remote-azure-sql-database)
mean cross-database access is **not impossible merely because ordinary three-part queries are
unsupported**. The runtime rejects every external data source/table and synonym, including local or
unresolved synonyms, plus every dependency with an explicit server/database name. This conservative
whole-database restriction may block an unrelated existing bridge; review it, never silently exempt it.
No probe executes an external query or contacts a remote resource.

[External-source metadata](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-external-data-sources-transact-sql?view=sql-server-ver17)
is subject to securable visibility. Database VIEW DEFINITION is required before inventory. Verifier
direct permissions are restricted (including DENY that could obscure metadata), public metadata
DENY is refused, and the verifier role's complete permission edges remain receipt-bound.

[sys.database_scoped_credentials](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-scoped-credentials-transact-sql?view=sql-server-ver17)
requires CONTROL DATABASE for inventory. **A complete credential inventory cannot safely be
reproduced with the runtime verifier's authority.** Stage 0 therefore uses an already-authorized
operator connection, returns only the count, and requires zero credentials. No CONTROL, ALTER ANY
CREDENTIAL, new signed/owner module or wider role is added to the verifier. Repeat this operator
gate after governed credential/catalog changes and before readiness rollout. An empty or denied
low-privilege catalog is never a passing credential inventory.

The runtime does not claim a fresh credential-absence proof on every request. It proves absence
of credential *authority*: no direct or public credential grant, exact role grants, no database
bulk/external-endpoint/DDL permissions, no external sources/tables/synonyms and only the pinned six
application EXECUTEs. Thus a dormant credential alone supplies no permitted path to remote data.
Credential ownership/inventory and other-database secret reuse remain explicit external/operator
facts; fresh runtime authority closure, rather than an unsigned historical receipt, protects serving.
If live platform testing cannot establish complete external-source visibility or this closure, the
contract remains BLOCKED. Do not activate or add CONTROL to make it pass.

## Preserved behavior, audit and migration

**No SQL migration required.** Principal provisioning changes only the separately governed identity
model. Historical migration, verifier SQL, module manifest and receipts are untouched. New Stage 0
files are read-only tools, not migrations. The two tools, schemas, OAuth grant types/lifetimes,
ProofDigest, quotas, audits, flags and packaged OFF defaults are unchanged. Every protected request
still calls `ready()` before proof authentication; failed attestation yields no protected result.
Final-handoff SQL, locks, post-lock UTC sample and Java lifetime subtraction are byte-for-byte unchanged.

Audit compatibility: attestation and Stage 0 inspect identity/catalog/noncredential reference state
only. They create no PHI/entity mutation or new audit event. Existing transactional PHI/integration
reads, failure accounting and final-handoff semantics remain authoritative. Test fault injection
uses only a separately owned synthetic fixture and never production permissions.

See [acceptance and exact commands](../testing/azure-sql-case-attestation.md) and the updated
[deployment preflight](../production-mcp-deployment-preflight.md). Local SQL Server does not prove
Azure catalog semantics, even if EngineEdition is mocked in a routing unit test.

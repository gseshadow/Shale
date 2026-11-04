# Dormant server ordinary release compatibility — 2026-11-04

## Decision and evidence boundary

**Production dormant JAR deployment: BLOCKED on ordinary compatibility evidence and artifact/configuration validation.**
The source supports an all-OFF release without Phase1R, Phase3B.8, AI identities or new AI secrets.
No evidence reviewed establishes that any SQL must actually run before that release. Unknown installation
is not absence and is not a recommendation to execute an installer.

Reviewed source: `codex/latest`, `1f12091aef3275721c1e35d6ba00a2ecb80c5173`,
the confirmed merge of [PR #1929](https://github.com/gseshadow/Shale/pull/1929).
Its merge changes PR status, not native Azure acceptance or production installation.
No SQL (including read-only SQL), Azure operation, build, upload, deployment or activation ran for this review.
No Phase1R/V2 execution packet is prepared. Only documentation changes are proposed.

The [October 21 reconciliation](architecture/ai-integration-and-mcp.md#25-authentication-permissions-and-delivery-reconciliation--2026-10-21)
explicitly leaves Stage A/B and exact active API/desktop/SQL parity unconfirmed.
The [R4 record](../shale-web/docs/r4-desktop-acceptance.md) reports API redeployment after #1858;
it does not identify the active executable's SHA-256 or exact source.
The October 2/6/20 operator observations below are retained as attributed production/Windows evidence,
not newly executed checks or complete current catalog/ACL attestation.

## Dependency decision table

The last three columns use only the specified status vocabulary.
INSTALLED/ESTABLISHED on historical behavior does not close a separately listed current catalog/identity gap.
UNKNOWN in the action column means resolve the bounded evidence gap described below; it never means run SQL.

| Dependency | Required by dormant JAR? | Production state established? | Action before deploy |
| ---------- | ------------------------ | ----------------------------- | -------------------- |
| Stage A V1/V2 modules, dbo owner execution and six module EXECUTE grants/denials | REQUIRED | UNKNOWN | UNKNOWN |
| Stage B table/old-resolver/hash read restrictions | NOT REQUIRED | UNKNOWN | DEFERRED |
| UserSessions Phase 7A foundation and durable lifecycle already exercised | REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| Session security/audit Phase 8A behavior already exercised | REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| Current session catalog, tenant predicates, constraints and actual runtime permission parity | REQUIRED | UNKNOWN | UNKNOWN |
| October 5 remembered-credential storage/lifecycle already exercised | REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| October 6 legacy remembered resolver support already exercised | NOT REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| Current remembered runtime object permissions and V2 lookup parity | REQUIRED | UNKNOWN | UNKNOWN |
| October 20 legacy auth retirement replay | NOT REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| Existing ordinary API/Case/release-control schema and grants for retained routes | REQUIRED | UNKNOWN | UNKNOWN |
| Additional ordinary migration beyond the identified V1/V2 auth dependency since #1858 | NOT REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| Phase1R three-FILTER change | NOT REQUIRED | DEFERRED | DEFERRED |
| Phase3B.8 confinement V2 | NOT REQUIRED | DEFERRED | DEFERRED |
| Replay of the seven operator-reported installed AI migrations | NOT REQUIRED | INSTALLED/ESTABLISHED | NOT REQUIRED |
| New AI runtime/verifier/bootstrap identities, secrets or deployment binding | NOT REQUIRED | DEFERRED | DEFERRED |
| Effective ordinary configuration and all-OFF configuration verification | REQUIRED | UNKNOWN | REQUIRED |
| Exact executable build/hash, retained rollback artifact and release smoke | REQUIRED | UNKNOWN | REQUIRED |

## Stage A: the ordinary compatibility gate

[AuthServiceImpl](../shale-data/src/main/java/com/shale/data/auth/AuthServiceImpl.java) calls
`dbo.LookupAuthenticationCredentialV1(@Email nvarchar(max))`; BCrypt verification remains in Java.
There is no direct Users SELECT or legacy login-procedure fallback.
[SqlRememberCredentialStore](../shale-server/src/main/java/com/shale/server/runtime/SqlRememberCredentialStore.java)
calls `dbo.ResolveDesktopRememberCredentialV2(@CredentialHash varbinary(max),
@InstallationId uniqueidentifier, @Now datetime2(7))` before opening a tenant-initialized runtime
connection for authoritative serializable credential/JTI rotation.

The approved Stage A entry point is [install.sql](sql/auth/permission-cutover/install.sql),
which wraps [the October 14 module installer](sql/2026-10-14_pretenant_authentication_modules.sql)
and its six object permission changes in one outer transaction.
Both procedures require visible reviewed definitions, `WITH EXECUTE AS OWNER`, and effective dbo
ownership, including dbo schema ownership. The historical bare module installer creates no grants
or denials and is not a substitute for Stage A.

For **each** of the two procedures, the exact Stage A ACL is:

- object EXECUTE GRANT to `shale_app`, grantor dbo;
- object EXECUTE DENY to `shale_runtime`, grantor dbo;
- object EXECUTE DENY to `shale_runtime_role`, grantor dbo.

That is two grants and four denials, no role-membership change.
[module-postcheck.sql](sql/auth/permission-cutover/module-postcheck.sql) requires exactly those six
object ACL entries, correct dbo owner execution, effective auth EXECUTE, runtime non-execution,
and no static/unresolved wrapper dependencies that bypass the fence. Preserve the existing
non-bypass identity/ownership/escalation checks; runtime is not dbo, ShaleAdmins or a fixed broad role.

The modules depend on existing Users, ShaleClients, UserSessions and DesktopRememberCredentials.
V1 returns at most one eligible unambiguous user/hash projection; V2 returns only a candidate
tenant/user/session/email after credential, account, session and expiry checks.
Direct table grants to auth are not needed by these bounded current callers.

**Production Stage A is UNKNOWN.** Neither successful login nor the October 20 legacy retirement
identifies the serving source, the V1/V2 definitions, module ownership or all six fences.
Smallest resolution: locate the existing Stage A change/postcheck record with both definition
hashes, ownership/execution metadata, six ACLs and actual deployed auth/runtime identity mapping.
If unavailable, a separately authorized read-only catalog/effective-permission check limited to
those facts resolves it. Do not execute install.sql or its bare include to discover state:
they refuse existing modules. Historical preflight also refuses Users/auditLog/Statuses predicates;
never weaken that guard or replay the installer against unknown state.

## Stage B is independent and remains deferred

[restrict-reads.sql](sql/auth/permission-cutover/restrict-reads.sql) adds:

- `ShaleAuthTableFenceV1`: DENY SELECT on Users and DesktopRememberCredentials, DENY EXECUTE on
  old `ResolveDesktopRememberCredential`; `shale_app` is a member.
- `ShaleRuntimeHashFenceV1`: DENY SELECT on Users.password_hash; `shale_runtime` and
  `shale_runtime_role` are members.

It preserves password-management INSERT/UPDATE and audit CRUD; it is distinct from three-table
FILTER attachment and the three legacy-procedure retirement. Current bounded callers do not
need these denials to function, so Stage B is **NOT REQUIRED** merely to deploy/run this JAR.
Installation is **UNKNOWN**, never inferred from successful login. Leave further restriction work
**DEFERRED**; do not undo it if already present. The smallest existing record is its separate
read-postcheck/fence-role record; otherwise inspect only those roles, memberships, explicit denials
and effective permissions read-only. Caller inventory/drain remains required for any later restrictive change.

## Sessions and remembered credentials

[Phase 7A](sql/2026-09-29_user_sessions_foundation_phase7a.sql) supplies UserSessions, unique public
SessionId/current JTI, UTC lifecycle fields, tenant-qualified user/optional instance keys,
expiry/refresh/revocation constraints and enabled strict tenant FILTER plus AFTER INSERT/UPDATE
blocks through sec.fn_FilterByTenant. Users, ShaleClients, ApplicationInstances and their required
tenant-qualified keys are prerequisites. Current UserSessionDao/SqlDurableSessionStore require
runtime SELECT/INSERT/UPDATE on UserSessions and the used Users/ApplicationInstances projections.
Every connection stamps ShaleClientId and PrincipalUserId before tenant work.
Bound login, me/validation, refresh and logout depend on this storage, independently of AI.

[Phase 8A](sql/2026-09-29_session_security_audit_phase8a.sql) extends revocation vocabulary with
USER_REVOKED and supplies SessionSecurityAuditLog, its closed events/reasons, UTC default,
tenant/user keys, index, strict FILTER and AFTER INSERT/UPDATE blocks.
SessionManagementService requires runtime INSERT into that audit table for administrative list
and explicit self/admin revocation in the same transaction; audit failure withholds success.
Session listing also requires eligible Users/session reads and revocation requires UserSessions UPDATE.

The October 2 production report records enrollment, My Sessions, administrative list/revocation and
locking after remote revocation. October 6 records self/admin revocation invalidating remembered restore.
These establish the **deployed functional foundation/security-audit support at that scope**.
They do not establish every current catalog definition, predicate, trusted constraint, effective grant
or instance identity. No new session migration was added by the later UTC corrections.
Smallest remaining resolution: the existing Phase 7A/8A schema/RLS/grant verification record matched
to the active DB/runtime identity; otherwise a catalog/permission-only inspection of these two tables,
their keys/checks/defaults/predicates and used runtime permissions. Existing scripts are reference
inventories, not authorization to run their mutation/RLS-test companions.

[October 5](sql/2026-10-05_desktop_remember_credentials.sql) supplies DesktopRememberCredentials:
hash, installation/session binding, absolute expiry/rotation/consumption UTC fields, unique hash and
session/installation constraints, session/user FKs and expiry index.
Current remembered create/rotate/delete requires runtime SELECT/INSERT/UPDATE/DELETE on that table,
Users projections and UserSessions SELECT/UPDATE (session issuance separately requires INSERT).
Remembered storage is an ordinary feature dependency; Stage A also references the table even for
a release whose users choose password login only.

[October 6 correction](sql/2026-10-06_desktop_remember_credentials_permissions.sql) supplied the old
dbo-owned caller-executed ResolveDesktopRememberCredential, auth object EXECUTE and runtime object
SELECT/INSERT/UPDATE/DELETE on remembered storage. The [October 6 closeout](architecture/application-release-session-management.md#stay-logged-in-acceptance-closeout--2026-10-06)
accepts installed-Windows remembered issuance/rotation/restart/X-close/logout and self/admin revocation;
the earlier record also attributes application of the remembered migration to the operator.
That establishes historical working storage/resolver support, not the exact present ACL.

**The current JAR calls V2, not the October 6 resolver.** Preserve the old resolver for compatibility;
do not rerun the October 6 correction to restore its auth grant if Stage B already denies it.
The October 5 verifier's historical auth Users SELECT/old-resolver EXECUTE expectations are not the
current Stage A/B contract. Do not treat them as reasons to broaden auth grants.
For current compatibility use the Stage A V2 facts plus existing runtime remembered-object permission
record; absent that, inspect only effective used permissions and catalog shape read-only.
Do not export credential hashes, session/JTI values, identities, production rows or private packets.

## Completed legacy retirement

The [October 20 operator report](testing/auth-module-permission-cutover.md#current-operator-report--2026-10-21-reconciliation)
removes shale_app EXECUTE and denies shale_runtime/shale_runtime_role EXECUTE on
`sp_login_get_hash`, `sp_login_mark_failure`, `sp_login_mark_success`.
API login/me/refresh/logout and installed-desktop automatic sign-in/user-detail checks followed.
This is **INSTALLED/ESTABLISHED at its attributed report scope**. Do not rerun retirement,
rehearsals or approval packets. It does not establish Stage A, Stage B or full least privilege.
Current ordinary authentication does not require these retired legacy EXECUTEs.

## Other ordinary SQL since the recorded deployed baseline

The last recorded API redeployment baseline is #1858,
`d0d43655765b74e0c1e83ecefe59451307e7b749`; the exact active binary remains UNKNOWN.
Full recursive repository-tree comparison to the reviewed head identifies October 14 authentication
modules as the only added dated ordinary installer. Other new SQL is auth operator/verification
tooling, Phase1R or AI integration; none adds another ordinary dormant-release migration.

The changed ordinary persistence paths were also reviewed: V1 password/V2 remembered lookup;
UserSessionDao and session-list UTC conversions; bounded runtime setup overload (ordinary caller
unchanged); shared minimized Case read/transaction overloads; Case Updates search/edit/delete using
existing lifecycle/RowVersion and PHI AuditLog; and AI-only entity audit vocabulary.
The [Case Updates record](testing/case-updates-workspace.md) explicitly requires existing schema/audit
support and introduces no migration or API deployment requirement. UTC corrections require rebuilt
artifacts, not timestamp rewrites, constraint changes or timezone forcing.

Existing ordinary retained endpoints still need their established schema/grants: Users/clients;
Case search/assigned/Overview tables and required PHI audit; application release catalog/policy,
release state, ApplicationInstances/heartbeat and administrative read audit where those APIs are used.
Their migration records include the September 28 release catalog/policy, user release state and
ApplicationInstances foundations, September 29 heartbeat and administrative-read audit, and
October 2 release-catalog import scripts. Those dependencies predate #1858 and are not new SQL for this release. R4 establishes working Case
reads/Overview audit only at its reported scope. Smallest resolution for current route parity is the
existing active-JAR deployment manifest/hash and matching ordinary migration/grant inventory.
Without an exact deployed source there is no defensible exact production-to-candidate binary diff.
Do not convert that limit into a blanket migration list or repeat all historical installers.

## Exact effective dormant configuration

Ordinary required configuration:

| Setting | Required effective value / preservation rule |
| --- | --- |
| SPRING_PROFILES_ACTIVE | azure, with no dev/local/test profile |
| SHALE_APP_DB_URL / SHALE_APP_JDBC_URL | Existing reviewed production auth DB destination; first nonblank primary wins |
| SHALE_APP_DB_USER / SHALE_APP_USER | Existing reviewed auth principal mapped to Stage A |
| SHALE_APP_DB_PASSWORD / SHALE_APP_PASS | Existing auth secret; preserve securely |
| SHALE_RT_DB_URL / SHALE_RT_JDBC_URL | Existing reviewed production runtime DB destination |
| SHALE_RT_DB_USER / SHALE_RT_USER | Existing reviewed tenant runtime principal/pool mapping; no dbo/ShaleAdmins bypass |
| SHALE_RT_DB_PASSWORD / SHALE_RT_PASS | Existing runtime secret; preserve securely |
| SHALE_AUTH_TOKEN_SECRET | Existing signing secret, at least 32 characters; do not rotate for this release |
| SHALE_AUTH_SESSION_BINDING_CUTOVER_AT | Preserve original valid ISO-8601 boundary; do not reset it to this deployment |
| SHALE_AUTH_TOKEN_TTL_SECONDS | Preserve existing positive TTL; default 28800 only if absent |
| SHALE_ALLOWED_CORS_ORIGINS | Preserve exact approved browser origins and existing alias precedence; no wildcard |
| PORT | Existing App Service port; application default 8080 |
| DB_MAX_POOL_SIZE / DB_CONNECTION_TIMEOUT_MS | Preserve existing values/defaults; no timeout or pool tuning |
| LIVE_PUBLISH_ENDPOINT_URL / FUNCTION_KEY | Preserve existing post-commit invalidation configuration if used; not a new login prerequisite |

Config reads ordinary keys from environment first, then JVM system properties per key, with primary
_DB_ names before aliases. Spring configuration is used for AI properties. Inspect effective sources,
including App Settings, startup arguments, JVM properties and profiles; packaged defaults alone do
not prove deployed OFF state. Preserve JDBC TLS/certificate validation and existing secret consumers.

All **eight** Boolean controls must be effectively false:

```properties
shale.ai.enabled=false
shale.ai.mcp.enabled=false
shale.ai.mcp.case-data-composition-enabled=false
shale.ai.mcp.search-cases-enabled=false
shale.ai.mcp.get-case-overview-enabled=false
shale.ai.oauth.composition-enabled=false
shale.ai.oauth.authorization-enabled=false
shale.ai.oauth.exchange-enabled=false
```

Keep identity at its PRODUCTION default, never TEST; no new public origin, AI data/verifier/bootstrap
JDBC configuration, independent secret, principal or immutable binding is needed for OFF Boot.
Do not delete existing configuration or provision authority. Keep
`logging.level.io.modelcontextprotocol.modern.JsonRpc=OFF`; do not enable payload logging.
The reported SQL baseline keeps AiProductCapability.Enabled=0, tenant policies missing/disabled,
and no new real integration/grant/session/credential authority. Effective production values are UNKNOWN
until an existing sanitized OFF/settings record resolves them; any later read-only check is separate.
If an enabled value is discovered, stop rather than silently changing it under this review.

[McpCaseReadiness](../shale-server/src/main/java/com/shale/server/mcp/McpCaseReadiness.java)
returns disabled without attestation when boundaryRequired is false.
The conditional production boundary is absent, the AI pool is unallocated, and OAuth exchange bootstrap
is absent. [McpProductionOffBootTest](../shale-server/src/test/java/com/shale/server/mcp/McpProductionOffBootTest.java)
covers these facts under azure/prod with ordinary beans and no AI configuration.
This supports deployment without Phase1R/V2; it does not prove production SQL/configuration parity.

## Minimum build and deployment validation after compatibility is established

1. Pin source **1f12091aef3275721c1e35d6ba00a2ecb80c5173** in a clean complete checkout,
   or explicitly record a reviewed successor consisting only of these documentation corrections.
   Record JDK 21 and Maven versions. Do not build the older initial workspace checkout.
2. Preserve existing local acceptance for unchanged behavior. For a fresh candidate release,
   run ordinary auth/session and OFF Boot regressions locally (no production settings/credentials,
   no opt-in SQL harness); then the normal server reactor package:
   ```bash
   mvn -B -pl shale-server -am -Dtest=AuthCredentialLookupTest,PreTenantAuthenticationModuleContractTest,AuthControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,ServerAuthSessionLogoutTest,SessionManagementContractTest,DesktopSessionControllerTest,SessionControllerTest,AdminSessionControllerTest,McpProductionOffBootTest -Dsurefire.failIfNoSpecifiedTests=false test
   mvn -B -pl shale-server -am clean package
   sha256sum shale-server/target/shale-server-1.0.138.jar
   ```
   The package runs the configured critical inventory; no full-suite or diagnostic campaign is added.
   Any failure blocks artifact acceptance. These commands are requirements, not results of this review.
3. Artifact: **shale-server/target/shale-server-1.0.138.jar**, executable Boot JAR, never .jar.original.
   Record its actual SHA-256 before upload and match the uploaded executable byte-for-byte.
   **Candidate artifact SHA-256: UNKNOWN (not built here).** A source commit/hash or prior test receipt
   cannot supply this value.
4. Retain the actual currently deployed executable as a separately named rollback JAR, with its
   SHA-256/source if known and current settings/start command. **Rollback artifact path/hash: UNKNOWN.**
   Prefer a compatible durable-session rollback. Pre-7B rollback needs the documented maximum-TTL
   drain or emergency signing-secret rotation after revocation; never silently restore a JAR that
   ignores SQL revocation.
5. Verify the effective ordinary configuration and all eight false controls above, readiness disabled,
   existing global/tenant/integration OFF posture, no new AI identity/secret requirement, existing
   target/start command and reviewed release authorization. No SQL/configuration write is part of
   closing UNKNOWN by evidence. Upload/restart remains separately authorized; this review performs neither.
6. After a separately authorized upload, require ordinary /api/health and /api/health/db 200;
   valid login -> me -> refresh -> old-JTI rejection -> logout -> old-bearer rejection;
   desktop enrollment with/without remember -> ordinary-close/restart restore -> credential rotation
   and old-credential rejection -> explicit logout invalidating restore; self/admin sessions and
   approved test-session revocation, ordinary-user admin denial, and representative existing protected
   API/Case read with required audit/no-store. Use approved accounts and retain only sanitized outcomes.
   Smoke necessarily exercises ordinary session writes; none is authorized/executed by this read-only review.
7. OFF smoke: startup marker **AI_CASE_SQL_READINESS disabled**; no AI pool/attestation;
   POST /mcp, /oauth/mcp/authorize and /oauth/mcp/token unavailable (404), no token issuance,
   no usable MCP discovery/tools/Case disclosure; verify OAuth metadata/resource discovery remains
   unavailable under OFF controls: GET /.well-known/oauth-authorization-server/oauth/mcp and
   GET /.well-known/oauth-protected-resource/mcp return 404. Also retain unavailable
   /oauth/mcp/revoke, /oauth/mcp/introspect and /oauth/mcp/jwks routes. Ordinary protected routes
   still reject absent/invalid bearers.
   No new public readiness endpoint, real integration registration or activation is needed for smoke.
   Any regression or unexpected readiness/activation stops the release and invokes retained rollback.

The smallest next step is retrieve the existing Stage A install/postcheck and active-JAR/settings/
ordinary schema-permission records, not execute SQL. If those records resolve compatibility unchanged,
**zero production SQL changes are required** for the dormant JAR. Until then deployment stays BLOCKED,
even though an isolated build itself does not require production SQL access.

## Still blocked after a successful dormant deployment

Stage B changes and Phase1R/Phase3B.8 remain deferred. AI readiness sign-off, native restricted
catalog/read-only and disposable mutation/fault acceptance, production identity/catalog/binding parity,
real OAuth issuance/consent, MCP discovery/serving/handlers, tenant/integration activation and the
one-client pilot remain blocked by their retained live ingress/OAuth, operational, SDK prerelease,
legal and security/operator/tenant-owner gates. Dormant deployment closes none of these and does
not resume the stopped Azure diagnostic investigation.

## Documentation validation for this review

Only Markdown documentation changed. The current merged selector, run with each changed path via
`--path`, selects no affected Maven area and no full-suite escalation. Existing executable fenced
blocks in corrected documents are byte-for-byte unchanged. Relative link targets, status vocabulary,
all eight OFF properties and diff whitespace are checked. No runtime suite or release build ran:
this task records the release gate and does not claim a newly built or production-accepted artifact.

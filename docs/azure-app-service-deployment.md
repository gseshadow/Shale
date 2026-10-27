# Shale Server Azure App Service Deployment Guide

## Current authentication/security reconciliation — 2026-10-21

The [canonical API/MCP roadmap §2.5](architecture/ai-integration-and-mcp.md#25-authentication-permissions-and-delivery-reconciliation--2026-10-21)
records the October 20 **operator-reported production retirement** of EXECUTE on the three legacy login
procedures for `shale_app`, runtime principal/role denials, and successful API login/identity/refresh/logout
plus installed-desktop automatic sign-in/user-detail checks. Do not repeat completed operator rehearsals
or treat the older retirement-BLOCKED statements below as current status for that exact delta.

The separate V1/V2 auth-module Stage A installation/fences, Stage B table/hash read restrictions,
subsequent desktop/API/SQL deployment details and monitoring closeout are not established by that report.
Current source is merged through release 1.0.137; merge/release metadata does not establish deployed parity.
Users/auditLog/Statuses read coverage, runtime hash/audit capabilities, write/admin restrictions and
Case SQL acceptance remain open as qualified in that ledger. Supplied offline schema confirmed the gap;
older missing-input statements are historical. No complete least-privilege, MCP or native-mobile acceptance.

Follow the [SQL/API/client release order](azure-app-service-deployment.md#release-dependencies-and-order--2026-10-21).
The one next implementation phase is the roadmap's [Phase 1R prompt](architecture/ai-integration-and-mcp.md#91-ready-to-run-codex-prompt--phase-1r),
a local three-FILTER candidate, without production changes. Earlier dated tests, local Windows/source
recovery limits and all accepted R4/session observations retain their scopes. This update executes no SQL.


This guide is the repeatable deployment checklist for deploying `shale-server` to Azure App Service. It assumes the Step 4A-4D API migration work is present: Azure/local profiles, bearer auth, health endpoints, Swagger/OpenAPI, standardized errors, and paged read endpoints.

**Hosted-web preparation update (2026-10-12):** use the existing API and the
[hosted operator handoff](shale-web-deployment.md). Static browser assets in `shale-web/dist` are separate
from this executable Java JAR. [R4](../shale-web/docs/r4-desktop-acceptance.md) records user-reported
API redeployment after #1858 and successful Overview; exact current JAR/source/hash/settings remain
UNKNOWN. Historical deployment notes below do not identify today's binary. No resource creation,
configuration change, deployment or production fault testing is authorized by this handoff.
Phase 2 acceptance OPEN; Phase 3 IN PROGRESS; Phase 4 INCOMPLETE. Appearance remains provisional;
web delivery is independent of AI/MCP. Hosted routing/cache/privacy, two-tenant/non-dbo isolation,
isolated audit failure/commit uncertainty, remaining live sessions and device/AT gates remain open.

## Release dependencies and order — 2026-10-21

This is a dependency checklist for a future separately authorized change, not instructions to replay
completed SQL or retirement. Inspect existing non-secret deployment/change records first and skip
already established steps. Health, a merged PR, a release manifest or successful catalog import alone
does not prove the remaining SQL/API/desktop dependencies. Exact subsequent production deployment
state is unconfirmed here; the October 20 operator report has the bounded scope stated above.

| Consumer / change | Required SQL and configuration before consumer rollout | Compatibility / evidence required |
| --- | --- | --- |
| Bound API sessions and self/admin session management | Phase 7A `2026-09-29_user_sessions_foundation_phase7a.sql`; Phase 8A `2026-09-29_session_security_audit_phase8a.sql`, their verification scripts, existing tenant Users and instance dependencies, actual runtime grants/RLS. Configure `SHALE_AUTH_SESSION_BINDING_CUTOVER_AT` and signing secret before the bound-session API. | Existing Phase 7B/7C/8A/8B server routes and durable validation must be deployed before dependent desktops. Audit failure must not be bypassed to make session lists work. Preserve legacy cutover/maximum-TTL and emergency secret-rotation rollback rules below. |
| Remembered desktop enrollment/restore | `2026-10-05_desktop_remember_credentials.sql` plus approved resolver/object permissions from the October 6 correction; UserSessions/Users/RLS and runtime SELECT/INSERT/UPDATE/DELETE on remembered credentials, required session grants. Current callers additionally require V1/V2 below. | API desktop-session/restore support precedes dependent desktop packaging. Preserve DPAPI credential/installation state, fixed remembered expiry and existing SQL UTC defaults/constraints. #1889's remaining UTC session fixes require rebuilt affected API/desktop artifacts; pinned recovery overlays are not production deployment artifacts. |
| Current password and remembered identity callers (#1868 and later) | Approved `docs/sql/auth/permission-cutover/install.sql` Stage A wraps `2026-10-14_pretenant_authentication_modules.sql` atomically with object EXECUTE for auth and object DENY for both runtime principal/role. Check exact definitions/owners/effective permissions and no bypass. Do not execute the historical bare installer or blindly rerun grants. | SQL modules/fences **before** rebuilt API **and** direct-JDBC desktop callers. `AuthServiceImpl` has no direct Users SELECT fallback. Functional login alone does not prove which artifact/module/fence is serving. |
| Stage B restrictions or future Users FILTER | `restrict-reads.sql` has separate approved-inventory/drained-fleet guards and module hash requirements. It adds auth table/old-resolver SELECT/EXECUTE fences and runtime password-hash SELECT denial; it does not remove password-management INSERT/UPDATE or audit CRUD. | First accept compatible deployed API/desktop/job callers and drain incompatible pre-context/direct-SELECT paths. Restriction and FILTER are distinct changes; neither is implied by the three legacy-procedure retirement. Future three-FILTER work follows Phase 1R, not the broad June proposal. |
| Desktop release 1.0.137 / Case Updates | Existing CaseUpdates lifecycle/RowVersion and PHI AuditLog dependencies in the [feature acceptance record](testing/case-updates-workspace.md); current shared authentication dependencies still apply to the complete artifact. | #1893–#1895 themselves add no new SQL migration/API deployment. That does not make an upgrade from an older base independent of previously introduced SQL/API requirements. Repository release metadata does not prove installation or server rollout. |
| Web V2 and future phone apps | Selected API routes, tenant/actor checks, required audit schema/grants and live security acceptance; browser origin CORS and compiled `VITE_SHALE_API_BASE_URL`; native credentials/origins/deep-links reviewed separately when implemented. | API/SQL first, then compatible static assets or packaged client. Static web deployment is separate from the API JAR/MSI. iPhone browser acceptance is not a native release; web publication cannot update embedded native assets. |

Phase 1R now supplies a [local three-FILTER candidate and exact rollback](sql/phase1r/README.md).
Its [synthetic acceptance](testing/case-read-local-security-verification.md#phase-1r-candidate--2026-10-21)
does not establish authoritative Case SQL or deployed caller compatibility. Stage A/B preflight deliberately
rejects these three predicates: finish any missing separately approved module/fence stages before RLS,
then compatible API/desktop rollout and old-caller drain. Do not rerun Stage A/B after FILTER attachment
or remove those guards. Users FILTER activation and all production actions remain blocked here.

Required sequence: review exact source-to-SQL/API/client compatibility and existing state -> install only
missing approved additive SQL/modules with fences -> deploy/verify compatible API -> package/roll out
compatible desktop/web clients -> confirm older caller drain -> separately authorize restrictive grants
or RLS after candidate acceptance -> record bounded operational checks and monitoring closeout. Do not
re-run October 20 retirement as a prerequisite to this sequence. Its three-procedure ACL delta is separate.

For the next RLS candidate, prepare rollback before any future authorization: remove only added
predicates/permission deltas against recorded hashes/SIDs, preserve pre-existing policies, auth modules
needed by current callers and all audit history. Old direct-SELECT auth clients cannot be restored under
strict Users filtering without a coordinated compatibility decision. No whole-policy disablement,
runtime dbo/ShaleAdmins bypass or automatic downgrade to pre-durable revocation behavior is acceptable.

Record only safe deployment facts: source/artifact identifiers, applied stage/migration labels,
definition hashes, sanitized principal/fence dispositions and scenario outcomes. Leave private packets,
credentials, tokens, connection strings, request/response bodies and production rows private. Missing
artifact/ACL/monitoring facts remain UNKNOWN; no production check or completed rehearsal is repeated here.

## Deployment readiness review

- `shale-server` listens on `server.port=${PORT:8080}`, allowing Azure App Service to provide the runtime port.
- Use `SPRING_PROFILES_ACTIVE=azure` in Azure App Service.
- The `azure` profile uses bearer-token authentication only.
- The `dev` and `local` profiles are the only profiles that support development headers such as `X-Shale-UserId` and `X-Shale-TenantId`.
- Database URLs, usernames, passwords, token secrets, token TTLs, and CORS settings come from Azure App Settings.
- Do not store secrets in source control.
- Tenant/user identity comes exclusively from validated bearer tokens.

---

## Build the deployable jar

From the repository root:

```bash
mvn -pl shale-server -am clean package -DskipTests
```

Locate the executable Spring Boot jar:

```text
shale-server/target/shale-server-<version>.jar
```

Rename or copy it as:

```text
shale-server.jar
```

Example:

```bash
cp shale-server/target/shale-server-*.jar shale-server.jar
```

Important:

Do not deploy:

```text
shale-server-<version>.jar.original
```

The `.original` file is not executable.

---

## Existing Azure App Service identity

Only the current API/database exists. Confirm the existing subscription/resource group/App Service,
runtime/profile/HTTPS settings, active executable JAR hash and source commit through authorized
operator evidence before a separately authorized deployment. Resource identifiers and current effective
settings are UNKNOWN here; the documented API URL is not proof of those settings. The historical
resource-creation examples have been removed to avoid deploying guessed resources. No second Azure
environment is required merely to prepare isolated fault tests; R4 recommends disposable local SQL/API
feasibility work with synthetic fixtures and no production connections or credentials.

---

## Required App Settings

Configure under:

```text
Azure Portal
→ Web App
→ Settings
→ Environment Variables
```

Required:

| Setting | Notes |
|----------|----------|
| SPRING_PROFILES_ACTIVE | azure |
| SHALE_APP_JDBC_URL | App/Auth database JDBC URL |
| SHALE_APP_USER | App/Auth database user |
| SHALE_APP_PASS | App/Auth database password |
| SHALE_RT_JDBC_URL | Runtime database JDBC URL |
| SHALE_RT_USER | Runtime database user |
| SHALE_RT_PASS | Runtime database password |
| SHALE_AUTH_TOKEN_SECRET | 32+ character random secret |
| SHALE_AUTH_SESSION_BINDING_CUTOVER_AT | **Required for Phase 7B.** ISO-8601 UTC deployment boundary; startup fails when missing/malformed. |

Optional:

| Setting | Notes |
|----------|----------|
| SHALE_AUTH_TOKEN_TTL_SECONDS | Defaults to 28800 (8 hours) |
| SHALE_ALLOWED_CORS_ORIGINS | Browser clients require the exact origin in the existing effective list. Preserve all authorized consumers; append localhost/approved HTTPS frontend origins as needed, never replace the list or use a wildcard. Restart after a separately authorized change. See the hosted handoff for primary/alias precedence and platform CORS evidence. |
| DB_MAX_POOL_SIZE | Pool tuning |
| DB_CONNECTION_TIMEOUT_MS | Pool tuning |

---

## Startup Command

Configure under:

```text
Azure Portal
→ Web App
→ Settings
→ Configuration
→ Stack Settings
→ Startup Command
```

Use:

```bash
java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure
```

Do NOT use:

```bash
java $JAVA_OPTS -jar /home/site/wwwroot/shale-server.jar
```

During deployment testing Azure attempted to interpret `$JAVA_OPTS` as a Java class name and startup failed.

---

## Deploy the jar

### Recommended Method (Validated)

Open:

```text
Azure Portal
→ Web App
→ Advanced Tools
→ Go
```

This opens Kudu.

Navigate:

```text
Debug Console
→ CMD
→ site
→ wwwroot
```

Upload:

```text
shale-server.jar
```

directly into:

```text
/home/site/wwwroot
```

Verify the file exists before restarting.

This deployment path was validated successfully.

---

## Restart

After changing startup commands, environment variables, or deployed jars:

```text
Azure Portal
→ Web App
→ Restart
```

---

## Log Streaming

Open:

```text
Azure Portal
→ Monitoring
→ Log Stream
```

Successful startup should show:

```text
Starting ShaleServerApplication
The following 1 profile is active: "azure"
auth-pool - Start completed.
runtime-pool - Start completed.
Tomcat started on port 80
Started ShaleServerApplication
```

---

## Health Check Configuration

Configure:

```text
/api/health
```

Health endpoints:

| Endpoint | Purpose |
|----------|----------|
| /api/health | Liveness |
| /api/health/db | Database readiness |
| /v3/api-docs | OpenAPI JSON |
| /swagger-ui/index.html | Swagger UI |

---

## Swagger Authentication

Open:

```text
https://<site>.azurewebsites.net/swagger-ui/index.html
```

Authenticate:

1. Execute `POST /api/auth/login`
2. Copy the returned access token
3. Click Authorize

Important:

Paste only:

```text
<access token>
```

NOT:

```text
Bearer <access token>
```

Swagger already prepends the Bearer scheme automatically.

Using `Bearer <token>` manually may result in:

```json
{
  "status": 401,
  "message": "Invalid or expired authentication token."
}
```

---

## Current Step 5 Browser Login Endpoint

The current deployed Azure API origin for the Step 5 `shale-web` browser-login milestone is:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

Local browser login from Vite requires the effective CORS list to include the following origin;
preserve every existing authorized origin. This historical single-origin example is not an instruction
to overwrite the current list. A separately authorized setting change requires an App Service restart:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173
```

---

## Post Deployment Smoke Tests

### Health

```text
GET /api/health
```

Expected:

```json
{
  "status": "ok"
}
```

### Database

```text
GET /api/health/db
```

Expected:

```json
{
  "status": "ok"
}
```

### OpenAPI

```text
GET /v3/api-docs
```

Expected:

```text
200 OK
```

### Swagger

```text
GET /swagger-ui/index.html
```

Expected:

```text
Swagger UI loads
```

### Login

```text
POST /api/auth/login
```

Expected:

```json
{
  "accessToken": "...",
  ...
}
```

### Authenticated User

```text
GET /api/auth/me
```

Expected:

```text
200 OK
```

Returns authenticated user information.

### Protected Data Endpoint

For the reviewed Case workspace use `POST /api/v2/cases/search-page` with an approved synthetic
case-name query in its JSON body, `GET /api/v2/cases/assigned-page`, and a deliberately authorized
`GET /api/v2/cases/{caseId}/overview`. Expected eligible success is 200 JSON with `Cache-Control: no-store`;
Overview requires committed server-owned audit. Use first-read §11 and the hosted handoff for bounds,
safe failures and sanitized evidence. Never put real search text in diagnostic URLs/logs or export
bearers, request bodies, sensitive responses or raw HAR. The legacy GET search remains available and
needs query-log/referrer privacy review when exposed; the body-only minimized path does not prove
upstream request/error/APM capture is disabled.

An authorized successful request provides bounded evidence for:

- JWT generation
- JWT validation
- Azure profile
- Database connectivity
- Runtime session creation
- Tenant context propagation
- End-to-end API functionality

---

## First Successful Deployment Notes (2026-06-18)

The first successful Azure deployment validated:

- Linux App Service
- Java 21
- Azure profile
- Auth database connectivity
- Runtime database connectivity
- Swagger/OpenAPI
- JWT authentication
- `/api/health`
- `/api/health/db`
- `/api/auth/login`
- `/api/auth/me`

Known deployment lessons:

1. Use Kudu direct upload if Deployment Center placement is unclear.
2. Verify the jar exists in `/home/site/wwwroot`.
3. Use `java -jar ...` without `$JAVA_OPTS`.
4. Paste only the raw token into Swagger authorization.
5. Expect first startup to take approximately 45–60 seconds while Spring Boot initializes and Azure instrumentation attaches.
## Phase 7B durable API sessions

Set `SHALE_AUTH_SESSION_BINDING_CUTOVER_AT` to the UTC deployment boundary in strict ISO-8601 form (for example, `2026-09-29T18:00:00Z`) before deploying the Phase 7B server. Startup fails if it is absent or malformed. Existing unbound JWTs issued before that instant remain eligible only until the earlier of their own expiry or the boundary plus `SHALE_AUTH_TOKEN_TTL_SECONDS`; refresh upgrades them to a durable bound session. New logins require the already-deployed Phase 7A `UserSessions` table.

Safe rollout order is Phase 7A verification, cutoff configuration, Phase 7B deployment, legacy drain/refresh upgrade, and confirmation that the maximum token TTL has elapsed. A routine rollback to pre-7B must wait one maximum token TTL with Phase 7B traffic drained because old code does not enforce SQL revocation. If emergency rollback follows any durable revocation, rotate `SHALE_AUTH_TOKEN_SECRET` and require reauthentication; otherwise a still-unexpired bound JWT revoked only in SQL could be accepted by old code.

## Phase 7C desktop enrollment rollout

Deploy the additive `POST /api/auth/desktop-session` server endpoint before Phase 7C desktops and configure each
desktop's `SHALE_SERVER_API_BASE_URL` to the HTTPS API origin (without `/api`). The endpoint repeats credential
verification once after successful JDBC login because no trusted post-JDBC assertion facility exists yet; it never
accepts tenant/user claims as proof. Existing web auth contracts and older desktops are unchanged. A 404/501 is the
explicit staged-rollout signal for JDBC-only compatibility. Do not interpret 401/403 or an instance mismatch as an
old-server condition. Rollback may restore the previous desktop; it ignores historical session rows and there is no
persisted desktop bearer credential to remove.

### Desktop API-origin packaging and overrides

Production desktop packages contain the verified deployment origin
`https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net` as
`SHALE_PACKAGED_SERVER_API_BASE_URL`. Installed workstations therefore do not need a per-machine environment
variable. This repository value is deployment evidence only; release acceptance must still prove that the target
deployment contains `POST /api/auth/desktop-session` and the Phase 7A/8A database migrations.

`DesktopConfig` resolves one origin for enrollment and both session-management clients. Precedence is a nonblank
`SHALE_SERVER_API_BASE_URL` Java system property, then the same environment variable, then packaged configuration
for a production/installed launch. Blank override values are absent. A nonblank invalid override fails startup and
never falls through to the packaged destination. Development launches ignore the packaged production origin and
remain unconfigured unless an explicit override is supplied. An explicit local example is
`-DSHALE_SERVER_API_BASE_URL=http://localhost:8080`; HTTP is accepted only for loopback development. All production
origins must be HTTPS. Origins must not contain user-info, a path (including `/api`), a query, or a fragment, and
trailing slashes are normalized.

### Phase 7C runtime acceptance and remaining checks

The 2026-10-02 user-reported Windows/Eclipse production run confirms the Azure API starts, desktop enrollment
returns HTTP 200, Administration > Sessions loads tenant-wide data, explicit logout returns HTTP 200 and revokes the
session, remote administrative revocation is detected by polling in approximately one minute, and confirmed
revocation locks the application before the popup is dismissed (OK returns to sign-in). My Sessions works for both
ordinary users and administrators, is self-only, marks the current session, and current-session self-revocation
locks and returns to sign-in. Earlier local Maven runs passed as reported by the user; these runtime observations do
not create a new Maven result.

The following checklist remains the reproducible deployment procedure. A clean installed production launch without
`SHALE_SERVER_API_BASE_URL` or another API override is **NOT RUN** unless separately evidenced. Intermittent
enrollment `REQUEST_TIMEOUT` also remains open. Items concerning a second user, an ordinary user's administrator
403, and crafted cross-tenant requests are not claimed as manually accepted by the evidence above.

From an authorized Windows test workstation, without recording credentials or response bodies:

1. Confirm `GET <origin>/api/health` returns 200.
2. Confirm an intentionally invalid credential `POST <origin>/api/auth/desktop-session` returns 401 rather than
   404/405/501. A GET or generic bearer 401 is not endpoint proof.
3. Sign in with an authorized ordinary user and confirm Settings > Sessions loads authoritative rows; sign out and
   confirm the prior bearer can no longer be used.
4. Sign in as a different ordinary user and confirm no prior-user sessions or authority are inherited; confirm the
   administrator endpoint returns 403.
5. Sign in as a tenant administrator and confirm the bounded Administration > Sessions page loads and can perform
   an authorized test revocation.
6. Inspect sanitized desktop logs for `Desktop durable session enrollment succeeded.` Never capture passwords,
   bearer tokens, request bodies, or sensitive response bodies.
7. For an enrollment failure, record the single sanitized enrollment diagnostic: HTTP status and `elapsedMs` when a
   response arrived, or transport `kind`, `exceptionClass`, and `elapsedMs`. Do not collect surrounding credential,
   bearer, request/response-body, email, or unrestricted exception-message output. `REQUEST_TIMEOUT` at approximately
   8000 ms identifies the desktop request boundary; `CONNECTION_FAILURE` and `TLS_FAILURE` distinguish connection and
   handshake paths without increasing either timeout.
8. After successful enrollment, open Administration > Sessions and record only the sanitized administrative-list
   diagnostic: response `status` and `elapsedMs`; transport `kind`, `exceptionClass`, and `elapsedMs`; or response
   parsing `exceptionClass`. Do not capture the URL/query, authorization header, bearer, body, filters, or any
   user/session fields.
9. If the list reports status 500, verify that `dbo.SessionSecurityAuditLog` exists, has the Phase 8A tenant FILTER
   and AFTER INSERT/UPDATE block predicates, and permits the configured runtime database principal to insert. A
   successful desktop enrollment proves `dbo.UserSessions` issuance but does not prove this fail-closed read-audit
   write. Apply the existing `2026-09-29_session_security_audit_phase8a.sql` migration if it is absent; do not bypass
   or disable the audit to make the page load.

### Desktop durable-session revocation enforcement rollout

The 2026-10-02 enforcement correction requires a rebuilt/redeployed desktop, not a new SQL migration. The server must
already include the Phase 8A session APIs/audit migration and Phase 8B post-commit publisher configuration described
above; no server rebuild is required when that version is already deployed. Roll out the server/migrations first,
then the corrected desktop. Older desktops can durably enroll but do not reliably remove direct-JDBC authority after
remote revocation.

Acceptance requires two corrected desktop processes: enroll Joreen's exact current session, revoke that public
session ID as a same-tenant administrator, and verify that Joreen receives the session-ended warning and cannot start
new JDBC work. Repeat with LiveBus disconnected. Push should accelerate detection; without push, authoritative
validation begins within 60 seconds and has a six-second request timeout, for a maximum documented detection window
of 66 seconds when the server is reachable. A timeout or transport outage is uncertainty and must not be reported as
revocation; validation retries at the next interval.


### 2026-10-02 revocation acceptance closeout

The user-reported production acceptance satisfies the observed enforcement behavior: administrative revocation was
detected in approximately one minute, the application was already locked while the session-ended popup remained
open, and OK moved to sign-in. This does not replace automated tenant/authorization coverage or claim manual
cross-tenant/crafted-request acceptance. Normal X-button closure continues to clear local state without server
revocation; only explicit Logout revokes the durable session. Logged-out automatic updates remain `UNSUPPORTED`.

## Future AI OAuth deployment sequence — through Phase 3B.2D

This stable linked sequence includes the Phase 3B.3 internal audit/disclosure candidate.
Phase 3B.4 transport/discovery now exists in source, explicitly default OFF; its source/protocol/client acceptance
is recorded in the [canonical roadmap §10.25](architecture/ai-integration-and-mcp.md#1025-phase-3b4-default-off-transport-and-discovery--2026-10-23).
SDK 2.2.0-M1 remains a prerelease implementation candidate, not production approval. 3B.4 adds no migration.
Phase 3B.5 adds only synthetic/test-profile search_cases through the existing quota/audit/final-disclosure
seam; its [source/client receipt](architecture/ai-integration-and-mcp.md#1026-phase-3b5-synthetic-only-search_cases-handler--2026-10-23)
is separate from live acceptance. `shale.ai.mcp.search-cases-enabled=false` remains explicit, and production/
Azure profiles cannot activate that test handler. No 3B.5 migration or production pilot enablement is added.

Phase 3B.6 adds only the second synthetic/test handler, get_case_overview, using the same transaction-aware
Overview/PHI/integration-audit/quota/final-handoff seam. Its [source/client receipt](architecture/ai-integration-and-mcp.md#1027-phase-3b6-synthetic-only-get_case_overview-handler--2026-10-24)
retains synthetic-only scope. `shale.ai.mcp.get-case-overview-enabled=false` is explicit; production/Azure
profiles refuse forced activation. No migration or real tenant/integration enablement is added. Phase 3B.7
and authoritative Case SQL/non-bypass deployed permission/RLS, SDK prerelease, tenant/legal and live gates
remain open. Aggregate Phase 3B remains IN PROGRESS.

Phase 3B.7 has now been attempted and is **BLOCKED — authoritative Case artifact unavailable**;
its [acceptance ledger](testing/phase3b7-adversarial-acceptance.md) also records remaining full-stack,
TLS/proxy, client, conformance and performance limits. Independent synthetic checks do not authorize
this future sequence or real enablement. No migration/configuration/deployment change is part of 3B.7.

This is a cumulative future operator sequence, **not authorization to deploy, run production/Azure SQL,
enable a tenant, publish an installer/version or perform live acceptance in this task**. Keep every AI
startup/global/tenant switch OFF until independently authorized acceptance. Existing WEB/DESKTOP
session authority and previously completed deployment/security records above remain applicable.

1. Build/upload the new server JAR from the reviewed source; record source/artifact identity and retain
   the previous JAR. Upload with AI composition/authorization/exchange and `shale.ai.mcp.enabled` / `shale.ai.mcp.search-cases-enabled` / `shale.ai.mcp.get-case-overview-enabled` disabled.
2. Execute accumulated, **unapplied** AI SQL migrations in order:
   [phase3b1.sql](sql/ai-integration/phase3b1.sql) → [phase3b2b.sql](sql/ai-integration/phase3b2b.sql) →
   [phase3b2c1.sql](sql/ai-integration/phase3b2c1.sql) → [phase3b2c2.sql](sql/ai-integration/phase3b2c2.sql) →
   [revocation.sql](sql/ai-integration/revocation.sql) → [phase3b2d.sql](sql/ai-integration/phase3b2d.sql) →
   [phase3b3.sql](sql/ai-integration/phase3b3.sql),
   using approved migration authority, fail-fast sessions and the [SQL operator instructions](sql/ai-integration/README.md).
   Review existing receipts to skip applied migrations; older receipts deliberately detect later catalog changes.
3. Execute the cumulative [AI SQL verifier](sql/ai-integration/verify.sql), review effective non-bypass runtime
   and separate bootstrap permissions, RLS FILTER/INSERT BLOCK/UPDATE BLOCK, trusted tenant FKs and receipt
   findings. Any failure blocks acceptance. Supply the separate restricted bootstrap configuration;
   never substitute the ordinary auth identity or expand its access.
4. **Only then** perform separately authorized live OAuth acceptance with synthetic approved identities.
   Prove code+S256 issuance, exact private_key_jwt/public profiles, rotation, response-loss retry/family revoke,
   authority clipping, resets/revokes, audit failure, restart/replicas and ordinary WEB/DESKTOP regressions.
   RFC 7009 acceptance must cover empty 200 for unknown/repeated/foreign credentials, both credential kinds,
   pinned RS256 endpoint-audience proofs and shared JTI replay, family races and atomic security audit.
   The revocation extension must be installed before enabling exchange in the closeout server version;
   metadata advertises that implemented endpoint with exchange, with no runtime migration installer.
   3B.2D provides internal ACCESS validation; 3B.3 provides the internal audited buffered seam.
   3B.4 adds a default-OFF protected transport/catalog; 3B.5 and 3B.6 add only explicitly test-activated
   search and minimized Overview handlers. Both production Case handlers remain disabled.
   Live OAuth acceptance starts only after SQL migrations, the cumulative verifier and server deployment all succeed.
5. Only after server deployment, all accumulated SQL migrations/verifier/permission checks and live OAuth
   acceptance succeed, perform separately authorized MCP transport acceptance with synthetic identities.
   First complete the source/local 3B.4 SDK, protocol and two-independent-client gates recorded in §10.25.
   This source increment does not authorize live acceptance or enabling the default-OFF transport. Keep Case data handlers disabled.
6. Do not enable Case MCP handlers or a real tenant/integration until 3B.7 acceptance and all prerequisite gates are
   explicitly complete and operational enablement is separately authorized.

Rollback disables OAuth/AI and revokes credential families while retaining all additive storage, receipts,
consumption/replay/audit/revocation history and the configured token namespace. Re-enable cannot restore
revoked descendants. Do not drop tables or restore pre-revocation database history to make old tokens work.

## Production Case composition candidate

The earlier synthetic-only/current-production-refusal passages describe the pre-composition baseline.
A separate production/readiness candidate now exists, **BLOCKED pending compilation and actual
production-profile authoritative/client/TLS acceptance**. It does not authorize deployment.
Use [the current staged preflight](production-mcp-deployment-preflight.md) and
[verification limits](testing/production-mcp-case-composition.md). All packaged defaults remain OFF.

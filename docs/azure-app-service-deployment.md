# Shale Server Azure App Service Deployment Guide

This guide is the repeatable deployment checklist for deploying `shale-server` to Azure App Service. It assumes the Step 4A-4D API migration work is present: Azure/local profiles, bearer auth, health endpoints, Swagger/OpenAPI, standardized errors, and paged read endpoints.

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

## Create Azure App Service

Recommended settings:

1. Linux App Service Plan
2. Java 21 Runtime
3. HTTPS Only enabled
4. Region as close to Azure SQL as practical
5. Deploy a Spring Boot executable jar

Example:

```bash
az group create --name rg-shale-web --location eastus

az appservice plan create \
  --resource-group rg-shale-web \
  --name plan-shale-web \
  --is-linux \
  --sku B1

az webapp create \
  --resource-group rg-shale-web \
  --plan plan-shale-web \
  --name shale-api \
  --runtime "JAVA:21-java21"
```

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
| SHALE_ALLOWED_CORS_ORIGINS | Required for browser clients. For local `shale-web` login, set `SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173` and restart the App Service. |
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

Local browser login from Vite requires this App Service setting followed by an App Service restart:

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

Example:

```text
GET /api/cases/search?query=smith
```

Expected:

```text
200 OK
```

This validates:

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

### Remaining Phase 7C runtime acceptance

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

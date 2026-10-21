# Shale Server Azure App Service Readiness

## Stable selected Case REST contract — Phase 2 (2026-10-21)

This section owns the supported wire contract and compatibility policy for **only the three minimized
Case reads**. It supersedes historical Step 3/4 examples below for this slice. The current implementation
was inspected on fetched live `origin/codex/latest` `18f2dbf09955e0ee8d25e50c4beb5f0aa685587c`
(merged Phase 1S #1899, following Phase 1R #1898). Generated `GET /v3/api-docs` and
`GET /swagger-ui/index.html` describe these same endpoints. No separate mobile/integration route is needed.
Broader REST review is pending; this is contract/source acceptance, not deployed SQL security acceptance.

### Route inventory and supported scope

| HTTP method and exact path | Inputs and bounds | Success DTO / shared operation | Disposition |
| --- | --- | --- | --- |
| `POST /api/v2/cases/search-page` | Required JSON object; optional/null `query`, `page`, `size`. Defaults empty query, 0, 25. Page 0–100; size 1–25. Strip surrounding Unicode whitespace, then query ≤100 UTF-16 code units. | `MinimizedCasePage` / `searchMinimizedCases` | Stable; retained unchanged. Body-only literal Case-name substring search, not number/client/full text. Blank checks actor authority and returns empty, never browse. |
| `GET /api/v2/cases/assigned-page` | Optional query `page=0`, `size=25`; same bounds. | `MinimizedCasePage` / `listMinimizedAssignedCases` | Stable; retained unchanged. Current authenticated user's assignments select this list only. |
| `GET /api/v2/cases/{caseId}/overview` | Required integral path `caseId`, 1–2147483647. | `MinimizedCaseOverview` / `readMinimizedCaseOverview` | Stable; retained unchanged. Active same-tenant Case, independently authorized and audited on each read. |
| `GET /api/cases/search`, `GET /api/cases/search-page`, `GET /api/cases/assigned`, `GET /api/cases/{caseId}` | Existing legacy inputs, including search-page size up to 100. | Broad legacy `CaseOverviewDto`, `PagedResponse` (`total=null`) or detail composition. | Compatibility-only for this Case slice; **not aliases** of v2. No removal or response narrowing here. No v1 path is introduced. |

Other generated API paths retain their existing consumers and ownership; listing them in OpenAPI does not
promote them to this stable external contract. `/api/dev/*` is internal development infrastructure.
There are no `/external`, `/mobile`, `/ai` or MCP variants. Names such as `MinimizedCaseOverview` are schema
identifiers, not database types. Domain IDs retain their existing JSON numbers; no SQL IDs beyond domain
identities, JDBC objects, RowVer, UI state, narratives, child collections or serialized internals are added.

### Authentication, authority and audit

All three stable routes require `Authorization: Bearer <server-issued JWT>` in prod/azure. Obtain a user
session through the existing `/api/auth/login` flow; use `/api/auth/me`, refresh and logout under the
[durable-session contract](architecture/application-release-session-management.md#phase-7b-implementation-record--2026-09-29).
No API keys, service accounts, third-party OAuth or special client-type permissions are provided.
The bearer resolver verifies signature/time, and bound `sid`/`jti` authority is checked against SQL ownership,
current JTI, expiry, revocation and current user eligibility. Legacy unbound tokens remain subject to the
existing finite cutover window; they are not an integration credential design. Development headers work
only in dev/local, never as production authentication. No new authentication filter is needed: these routes
resolve through `ServerRuntimeSessionState` and the configured `ServerSessionResolver`.

Tenant and user come from that principal. HTTP body/query identity fields cannot replace authority.
Each request's runtime connection initializes both tenant and principal context; the shared worker checks
current same-tenant, nondeleted/nonremoved user and matching context. Eligible users may search/read all
active tenant Cases; assignment is only a list filter. There is no administrator bypass. Missing, foreign
and deleted Overview targets share the same 404. Invalidated user sessions may fail as 401 before the
operation; an actor/context denial in the worker is 403. No invented `is_active` column is used.

REST delegates once through `CaseServicePort → CaseServiceAdapter / DaoCaseGateway → CaseSummaryDao`.
Controller work is input binding, early validation, encoding/byte checks and safe HTTP errors. The shared
worker retains tenant/deletion filtering, actor checks, SQL paging and Overview transaction/audit ownership.
Search/Assigned retain their approved ordinary minimized-summary audit exemption. Overview requires one
existing `Case.Overview.Read` event (Case type 1, FieldCode 4, `action=READ;screen=Case.Overview`) on the same
connection, committed before result return. Audit/commit failure withholds data; uncertain commit may leave
a durable event. Never delete/replay audit to hide uncertainty. Audit integration uses existing schema,
with no migration. This exemption does not authorize future AI summary disclosure or wider sensitive reads.

### Response schema, paging and resource limits

Every success member is present, including explicit nulls. Both page items and Overview use exactly:

| Member | JSON type / semantics |
| --- | --- |
| `caseId` | Required positive integer, ≤2147483647; stable domain identity. |
| `caseName` | Required non-null string, ≤255 UTF-16 units; may be empty. |
| `caseNumber` | Required nullable string, ≤200 UTF-16 units. |
| `status` | Required nullable object: required non-null `id`, `name`; required nullable `color` (`#RRGGBB` or null). |
| `practiceArea` | Required nullable object: required non-null `id`, `name`. |
| `responsibleAttorney`, `primaryLegalAssistant` | Required nullable objects: required non-null `userId`, `displayName`. |
| `updatedAt` | Required nullable stored local date-time string; no timezone/UTC/Z reinterpretation. |

Relationship IDs have the same 1–2147483647 range; names/display names have the same 255-unit limit.
Page shape is exactly `{items, page, size, hasMore}`: non-null array, integers and boolean, all required.
`items.length ≤ size ≤ 25`; no total/count. Keep size fixed across pages. Tenant/deletion/search/assignment
filters precede SQL `OFFSET page*size FETCH size+1` (offset ≤2500, fetched rows ≤26).
`hasMore=true` means the authorized extra row exists, including at page 100; **stop at page 100 and narrow
the query**, rather than requesting page 101 or claiming all results have ended. An empty/final page has
`hasMore=false`. Concurrent changes can shift offset pages; no snapshot pagination guarantee is made.
Search order is name ASC, ID ASC; Assigned is status sort order ASC, authoritative Intake date DESC,
ID DESC. Assigned ordering uses CaseDates internally; no dates are disclosed or hydrated into Overview.

Selected SQL statements retain five-second timeouts and DATALENGTH guards on projected text. Oversized
source values fail safely without truncation. Exact delivered UTF-8 JSON is checked once: **128 KiB/page,
8 KiB/Overview**. Selected success and scoped errors use `Cache-Control: no-store`. Row/statement bounds
are not an end-to-end deadline or proof of bounded scans; the web's eight-second fetch/body budget is a
client choice. Representative SQL plans/performance remain a separate acceptance gate.

### Error contract

For requests reaching the selected MVC contract, safe JSON errors use the type-backed `ApiErrorResponse`:
required `timestamp` (UTC instant), numeric `status`, string `error`, `message`, `path`, and `fieldErrors`
(empty array for this slice). The path contains no query string and is capped at 512 UTF-16 units; longer/absent paths use `/api/v2/cases`.
Fixed messages, codes, empty field errors and this path cap keep selected scoped errors below 8 KiB UTF-8. Do not interpret human-readable messages
as codes. No SQL text, table names, stack traces, credentials, foreign tenant metadata or raw exception
messages are returned. Errors contain no partial Case result. Invalid bindings can fail before authentication,
so status precedence is not a promise that every malformed unauthenticated request returns 401.

| HTTP status | `error` / meaning |
| --- | --- |
| 400 | `invalid_case_request` for malformed JSON/numeric binding; `case_request_failed` for explicit query/page/size/ID validation. |
| 401 | `case_request_failed`: missing/rejected authentication. Reauthenticate explicitly. |
| 403 | `case_read_denied` for shared operation authority denial; retain the session. |
| 404 | `case_request_failed`: Case unavailable, indistinguishable missing/foreign/deleted. Overview only. |
| 503 | `case_payload_oversized`, `case_read_timeout`, `case_audit_unavailable`, or `case_read_unavailable` for internal/read/encoding failures. No partial data; retry explicitly. Oversize is not HTTP 413 in this existing contract. |

Framework/edge failures outside mapped controller requests (unsupported routes/methods/media, CORS, proxy or
host failures) are not guaranteed to use this envelope. Consumers must safely handle non-JSON/non-success
responses and cap error bodies; current web caps them at 8 KiB. The scoped path cap does not impose a global byte budget on infrastructure or other controllers’ errors.

### Versioning and compatibility policy

* The supported selected contract is the existing **URL major `/api/v2/cases`**. OpenAPI `info.version=v1`
  is historical document metadata, not a v1 URL or a promise that all generated operations are stable.
  `/v3/api-docs` names the documentation route, not the application API major. No application version bump.
* A breaking change includes removing/renaming a route, method, parameter or response member; changing
  types, required/null semantics, identity/time meaning, paging/defaults/limits, auth requirements, error
  status/code meaning, or broadening the approved disclosure allowlist. A future breaking contract uses a
  new URL major (normally `/api/v3/...`) alongside v2 with an explicit client migration plan.
* **This selected response allowlist is closed.** Fields may not be silently added, even optional ones:
  the current web runtime validators reject unknown fields to prevent accidental wider disclosure.
  Future mobile/API clients may use the same conservative validation. New response fields require a
  reviewed compatibility plan/new major or explicit negotiated contract. Unknown request members are
  currently ignored and never trusted as tenant/actor authority. Unknown response tolerance is not required
  for this slice; no blanket additive-field promise is made for other APIs.
* This slice exposes no closed business enums: status IDs/names are data, not an enum. If a future contract
  introduces closed enums, adding a value is breaking for exhaustive clients unless that version explicitly
  defines an unknown-value fallback. Error codes listed above are supported; clients should fall back to a
  generic safe error for unrecognized failures rather than rendering raw payloads.
* Documentation/examples and schema descriptions may improve compatibly; generated ordering and operation
  metadata are not frozen. Any implementation change still must preserve behavior and disclosure policy.
* Legacy routes above remain available for existing callers. No deprecation/removal date is declared.
  Compatibility routes/aliases may remain indefinitely while supported clients need them; removal requires
  an explicit announced replacement, consumer inventory/migration evidence and release decision. No fixed
  calendar support window or alias proliferation is introduced at this maturity. Future deprecated stable
  routes must be marked in OpenAPI and these owning docs before removal; never silently repoint a broad
  route to the minimized DTO.

The unchanged web `features/cases/client.ts` and exact unknown-JSON validators already consume this
contract. No web migration/UX change is required. The same authenticated endpoints and business rules
serve future mobile and conventional integrations; SDKs, registration and public developer portals are deferred.
The bounded compatibility baseline in `OpenApiDocumentationTest` protects routes, parameters, bearer scheme,
required/null/type schemas and paging; serialized-record checks and HTTP/service tests complement it.

Phase 2 closes the **selected Case REST/OpenAPI foundation only**. Broader operation review and live gates
remain open in the [canonical roadmap](architecture/ai-integration-and-mcp.md#phase-2--stable-restopenapi-foundation-in-progress).
Phase 1R remains merged and locally/synthetically validated; **authoritative Case SQL acceptance BLOCKED**
by the unavailable approved offline artifact. Nothing here activates production RLS or establishes deployed
SQL security. MCP remains NOT STARTED; AI authority/grants/audit and runtime acceptance remain separate gates.


## Current authentication/security reconciliation — 2026-10-21

This dated reconciliation precedes merged Phase 1R/1S. The Phase 2 contract above and canonical
roadmap now record their merged status; the historical Phase 1R next-step wording below is superseded.
Authoritative Case SQL acceptance remains BLOCKED.

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


## Pre-tenant auth candidate — 2026-10-14

Shared password and remembered callers now target the bounded versioned modules described in the
[owning prerequisite implementation](testing/case-read-local-security-verification.md#pre-tenant-authentication-prerequisite-implementation--2026-10-14).
Deployment is BLOCKED: actual identities/owners/grants, security-owner approval of the internal hash
projection/ambiguity policy, opt-in auth SQL and compatible installed desktop/fleet drain remain required.
SQL must precede rebuilt server and desktop; there is no direct-SELECT fallback. No three-table FILTER
is attached here. Follow the exact module/permission/client rollback there; preserve durable sessions,
accepted R4 reports and every open security/deployment gate. Synthetic checks do not establish Azure parity.

## Case-read RLS prerequisite — 2026-10-14

Security acceptance remains **OPEN**. The authoritative offline SSMS export and user-run Shale catalog
query confirm no predicates on Users/auditLog/Statuses; local SQL integration is **NOT RUN**. This is
a confirmed RLS coverage gap, not proof of an API leak. Minimized Case queries retain explicit tenant/
join/context checks and required committed Overview audit. Current auth and remembered-sign-in lookup
run before tenant initialization, so strict Users coverage needs a compatible bounded auth-module
prerequisite and installed desktop drain first. Ownership chaining alone does not bypass RLS.
Do not weaken #1865's harness, fabricate snapshot predicates or grant shared identities SQL admin bypass.

The [owning remediation assessment and next implementation prompt](testing/case-read-local-security-verification.md#rls-blocker-assessment--2026-10-14)
define schema/source evidence, desktop/session/audit impact, unknown grants with read-only catalog queries,
targeted three-FILTER repair, separate write/admin controls, verification, rollout and scoped rollback.
No production code/migration/configuration/live fault/deployment change is part of this assessment.
All [R4 accepted reports](../shale-web/docs/r4-desktop-acceptance.md) remain attributed and accepted;
two-tenant/non-dbo, audit failure/commit uncertainty, session/host/privacy/device/AT gates remain open.

For the responsive client replacement, see [Shale Web V2 architecture and roadmap](architecture/shale-web-v2-architecture-roadmap.md). Its verified inventory distinguishes current browser mutations and durable sessions from historical beta descriptions below.

## Ordinary web-client readiness and separate AI gates — 2026-10-08

The web rebuild uses the existing bearer login/me/refresh/logout contract, SQL-backed bound WEB sessions,
server-derived tenant/user context, and shared application operations documented in
[the REST/MCP architecture roadmap, section 3.1](architecture/ai-integration-and-mcp.md).
The later session implementation records in
[the release/session roadmap](architecture/application-release-session-management.md) supersede earlier
skeleton and in-memory-only descriptions below.

Web readiness requires verification of each delivered operation's user/entity/field authorization,
tenant isolation and explicit predicates, validation/bounds, mutation concurrency and required Shale audit,
plus its REST/OpenAPI contract and browser configuration. Reuse existing routes and service/DAO workers;
address gaps per use case without waiting for unrelated AI decisions.

MCP transport, AI OAuth, tenant AI opt-in, integration registration/scopes, delegation grants and AI-specific
access audit attribution are separate integration activation requirements. They do not gate ordinary web
login, authenticated REST reads or authorized REST mutations. Ordinary requests need no fabricated AI
client/tool identity; integration credentials must never fall back to ordinary user authority to evade
those additional controls. This distinction does not weaken existing authorization, tenant/RLS or audit.

This document covers Step 4A deployment/configuration readiness for `shale-server`.

## Profiles

Use an explicit Spring profile when starting the server:

- `dev` or `local` for local development. These profiles enable the temporary development identity headers `X-Shale-UserId` and `X-Shale-TenantId`.
- `prod` or `azure` for deployed environments. These profiles reject development identity headers and DB-backed API routes fail closed until real authentication is implemented.

Do not run Azure App Service with `dev` or `local` enabled.

## Required database environment variables

The server accepts the Azure-ready names below and preserves the existing desktop/local aliases.

| Purpose | Preferred variable | Existing alias |
| --- | --- | --- |
| App/auth JDBC URL | `SHALE_APP_DB_URL` | `SHALE_APP_JDBC_URL` |
| App/auth SQL user | `SHALE_APP_DB_USER` | `SHALE_APP_USER` |
| App/auth SQL password | `SHALE_APP_DB_PASSWORD` | `SHALE_APP_PASS` |
| Runtime JDBC URL | `SHALE_RT_DB_URL` | `SHALE_RT_JDBC_URL` |
| Runtime SQL user | `SHALE_RT_DB_USER` | `SHALE_RT_USER` |
| Runtime SQL password | `SHALE_RT_DB_PASSWORD` | `SHALE_RT_PASS` |

Optional database tuning variables:

- `DB_MAX_POOL_SIZE` defaults to `10`.
- `DB_CONNECTION_TIMEOUT_MS` defaults to `10000`, while the connection pool enforces a minimum 30 second timeout.

Startup failures identify missing variables by name, for example `Missing required config: SHALE_APP_DB_URL or SHALE_APP_JDBC_URL`.

## CORS configuration

CORS is disabled unless an allowed-origin list is configured. Set one of:

- `SHALE_ALLOWED_CORS_ORIGINS`
- `SHALE_SERVER_ALLOWED_CORS_ORIGINS`

Use a comma-separated list, for example:

```bash
SHALE_ALLOWED_CORS_ORIGINS=https://app.example.com,https://staging.example.com

# Current local shale-web browser-login origin
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173
```


For the current Step 5 `shale-web` browser-login milestone, the deployed API origin is `https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net`. Restart Azure App Service after changing CORS or other application settings.

## Port binding

`shale-server` respects Azure's `PORT` environment variable. If `PORT` is not set, it defaults to `8080`.

## Local dev startup with environment variables

```bash
export SPRING_PROFILES_ACTIVE=dev
export SHALE_APP_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=ShaleApp;encrypt=true;trustServerCertificate=true'
export SHALE_APP_DB_USER='shale_app'
export SHALE_APP_DB_PASSWORD='your_app_password'
export SHALE_RT_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=ShaleRuntime;encrypt=true;trustServerCertificate=true'
export SHALE_RT_DB_USER='shale_runtime'
export SHALE_RT_DB_PASSWORD='your_runtime_password'
mvn -pl shale-server -am spring-boot:run
```

The existing `SHALE_APP_JDBC_URL`, `SHALE_APP_USER`, `SHALE_APP_PASS`, `SHALE_RT_JDBC_URL`, `SHALE_RT_USER`, and `SHALE_RT_PASS` names also continue to work.

## Azure App Service configuration notes

Configure App Service application settings with:

- `SPRING_PROFILES_ACTIVE=azure`
- `SHALE_APP_DB_URL`
- `SHALE_APP_DB_USER`
- `SHALE_APP_DB_PASSWORD`
- `SHALE_RT_DB_URL`
- `SHALE_RT_DB_USER`
- `SHALE_RT_DB_PASSWORD`
- `SHALE_ALLOWED_CORS_ORIGINS` when a browser client origin needs API access

Do not configure `X-Shale-UserId`, `X-Shale-TenantId`, real user ids, tenant ids, or secrets in source code. Passwords should live only in App Service settings or a future managed secret store.

## Health endpoints

- `GET /api/health` is a process liveness check and does not require database access.
- `GET /api/health/db` checks database connectivity and returns only safe status messages. It does not expose JDBC URLs, users, passwords, hosts, or tenant identifiers.

## Safe smoke-test checklist

### Dev/local profile

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/health/db
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' http://localhost:8080/api/dev/whoami
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' 'http://localhost:8080/api/cases/search?query=test'
curl -i 'http://localhost:8080/api/cases/search?query=test'
```

Expected behavior:

- `/api/health` returns `200` with `status=ok`.
- `/api/health/db` returns `200` when DB connectivity is valid or `503` with a safe diagnostic otherwise.
- `/api/dev/whoami` works only with valid development headers.
- DB-backed endpoints work with valid development headers and fail closed without them.

### Prod/azure profile

```bash
curl -i http://localhost:8080/api/health
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' http://localhost:8080/api/dev/whoami
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' 'http://localhost:8080/api/cases/search?query=test'
```

Expected behavior:

- `/api/health` returns `200` with `status=ok`.
- `/api/dev/whoami` is not available.
- DB-backed endpoints reject development headers and fail closed until real auth is implemented.

## Step 4B authentication foundation

`POST /api/auth/login` now validates credentials through the existing `AuthServicePort` / `AuthServiceImpl` path and issues a signed bearer token. The token contains only server-derived identity claims: `userId`, `shaleClientId`, optional `email`, issued-at time, and expiry. Tenant ids are not accepted from login request bodies.

### Required auth environment variables

- `SHALE_AUTH_TOKEN_SECRET` is required in `dev`, `local`, `prod`, and `azure` profiles. Use a high-entropy value of at least 32 characters. Do not commit it to source control.
- `SHALE_AUTH_TOKEN_TTL_SECONDS` is optional and defaults to 8 hours.

### Login request

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.test","password":"correct horse battery staple"}'
```

Successful responses include a bearer token and a safe user payload:

```json
{
  "authenticated": true,
  "tokenType": "Bearer",
  "accessToken": "<signed-token>",
  "expiresInSeconds": 28800,
  "user": {
    "authenticated": true,
    "userId": 123,
    "shaleClientId": 456,
    "email": "ada@example.test",
    "displayName": "Ada Lovelace",
    "nameFirst": "Ada",
    "nameLast": "Lovelace"
  }
}
```

Invalid passwords and unknown users return `401` with the same safe `invalid_credentials` response. Passwords, password hashes, and database-specific failure details are never returned.

### Current user

```bash
TOKEN='<signed-token-from-login>'
curl -i http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Missing, malformed, invalid, or expired tokens return `401`.

### Calling protected endpoints

Use the bearer token on DB-backed read endpoints:

```bash
curl -i 'http://localhost:8080/api/cases/search?query=smith' \
  -H "Authorization: Bearer $TOKEN"
```

The server resolves `userId` and `shaleClientId` from the signed token and uses that principal for runtime DB session context. Clients must not send tenant ids in request bodies or query strings to select a tenant.

### Dev/local compatibility

`dev` and `local` profiles still accept `X-Shale-UserId` / `X-Shale-TenantId` for the temporary local workflow, and they also accept bearer tokens when `SHALE_AUTH_TOKEN_SECRET` is configured.

### Azure/prod behavior

`prod` and `azure` profiles accept bearer tokens only. They do not trust `X-Shale-UserId` or `X-Shale-TenantId`.

## Step 4C logout and revocation

Access tokens now include a unique `jti` token id claim. `POST /api/auth/logout` verifies the presented bearer token and stores only the revoked token id until the token expiry time; raw tokens are not stored. Expired revocation entries are ignored and removed opportunistically.

```bash
curl -i -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Logout is safe to call with a missing or invalid token. The client should clear any locally held bearer token regardless of the response.

`GET /api/auth/me` returns a safe user profile shape when a valid, non-revoked token is presented:

```json
{
  "authenticated": true,
  "userId": 123,
  "shaleClientId": 456,
  "email": "ada@example.test",
  "displayName": "Ada Lovelace",
  "nameFirst": "Ada",
  "nameLast": "Lovelace",
  "isAdmin": false,
  "isAttorney": true,
  "initials": "AL",
  "color": "#123456"
}
```

`POST /api/auth/refresh` is available as a conservative foundation: it accepts a valid, non-revoked bearer token, revokes that token id, and returns a new access token. Long-lived refresh tokens are deferred.

Recommended web-client behavior:

- Store the bearer token only for as long as needed to keep the user signed in.
- Clear the bearer token on logout.
- Treat any `401` from protected API calls as a signal to return to login.
- Do not store tenant ids separately as trusted client state; use the signed token/server response only for display and let the server enforce tenant context.

## Step 4D API contracts for the React web app

### OpenAPI and Swagger UI

`shale-server` publishes generated API documentation when the app is running:

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui/index.html`

The OpenAPI document includes the `bearerAuth` HTTP bearer security scheme. Protected endpoints derive `userId` and `shaleClientId` from the server-issued token or the dev/local-only header resolver; clients must not send tenant ids as trusted request input.

### Standard error response

API errors use one safe JSON response shape:

```json
{
  "timestamp": "2026-06-17T21:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required.",
  "path": "/api/cases/search"
}
```

The server intentionally avoids stack traces, SQL text, JDBC URLs, usernames, hostnames, tenant ids, and raw exception details in API error bodies.

### Validation rules

- `POST /api/auth/login` requires nonblank `email` and `password`.
- Login email values are trimmed, must look like an email address, and must be 254 characters or fewer.
- Login password values must be 1024 characters or fewer.
- Search `query` values are optional but limited to 100 characters after trimming.
- Path IDs such as `caseId` must be positive.
- Paged search `page` must be between `0` and `100`; `size` must be between `1` and `100`.

Invalid input returns the standard `400 Bad Request` error shape.

### Paginated search endpoints

The original list endpoints remain available for compatibility:

```bash
curl -i 'http://localhost:8080/api/cases/search?query=smith' \
  -H "Authorization: Bearer $TOKEN"

curl -i 'http://localhost:8080/api/contacts/search?query=ada' \
  -H "Authorization: Bearer $TOKEN"
```

React clients can use page-shaped endpoints:

```bash
curl -i 'http://localhost:8080/api/cases/search-page?query=smith&page=0&size=25' \
  -H "Authorization: Bearer $TOKEN"

curl -i 'http://localhost:8080/api/contacts/search-page?query=ada&page=0&size=25' \
  -H "Authorization: Bearer $TOKEN"
```

Example paged response:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "total": null
}
```

`total` is currently `null` because there is no cheap count query in the existing DAO/service contract. Avoid adding expensive full-count queries without explicit DAO support.

### Step 4D smoke-test checklist

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/health/db
curl -i http://localhost:8080/v3/api-docs
curl -i http://localhost:8080/swagger-ui/index.html
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.test","password":"correct horse battery staple"}'
curl -i http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
curl -i 'http://localhost:8080/api/cases/search?query=smith' \
  -H "Authorization: Bearer $TOKEN"
curl -i 'http://localhost:8080/api/cases/search-page?query=smith&page=0&size=25' \
  -H "Authorization: Bearer $TOKEN"
curl -i 'http://localhost:8080/api/cases/search?query=smith'
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"","password":""}'
curl -i 'http://localhost:8080/api/cases/search?query=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' \
  -H "Authorization: Bearer $TOKEN"
```

## Step 4E Azure deployment guide

Use `docs/azure-app-service-deployment.md` as the repeatable first-deployment runbook for Azure App Service. It covers App Service creation, Java runtime selection, required app settings, startup command options, health checks, log streaming, restart, rollback, jar packaging, and smoke tests.

## Application release and policy read contracts (Phase 2B)

The server exposes two additive global control-plane reads for future clients:

* `GET /api/application-releases/policy/current?channel=PRODUCTION` returns the current effective policy, or
  `204 No Content` when that channel has no configured current policy.
* `GET /api/application-releases?channel=PRODUCTION&after=1.0.127` returns only published releases strictly newer
  than the supplied strict `major.minor.build` lower bound. Results use ascending numeric version order and embed
  active release items in stable sort order.

Both endpoints use the existing bearer-token requirement in `prod`/`azure` and the existing bearer-or-development
header behavior in `dev`/`local`. Authentication resolves the caller but does not tenant-filter global release or
policy data. Responses expose safe API DTOs rather than row versions or persistence models. Successful and empty
responses use short client-private caching (`Cache-Control: private, max-age=60`); there is no server-side cache or
ETag. Resource URLs are inert nullable metadata. No web, desktop, mobile, updater, manifest, acknowledgement,
enforcement, session, heartbeat, or PubSub consumer is wired in this phase.

OpenAPI at `/v3/api-docs` documents authentication, strict query parameters, the `204` empty-policy result,
response DTOs, error envelopes, and the release-channel, release-item-type, and access-mode vocabularies.
# Phase 4B application-instance endpoints (complete)

The server exposes bearer-authenticated `POST /api/application-instances` and
`POST /api/application-instances/{id}/end`. Request identity never supplies tenant/user authority; the
verified bearer principal and request-scoped RLS connection do. Enrollment accepts a canonical UUID,
`DESKTOP`, and strict `major.minor.build`. End is owner-qualified, idempotent, and preserves its first
server-observed UTC end time. These process lifecycle rows are not tokens or durable user sessions. Deployment
requires `docs/sql/2026-09-28_application_instances_foundation_phase4b.sql` plus its catalog and non-dbo RLS
verification scripts. Phase 4B verification completed before Phase 5A.

## Phase 6A tenant-admin application-instance reads (complete)

`GET /api/admin/application-instances` returns a tenant-admin-only recent page. Parameters are `page` (0–100,
default 0), `size` (1–100, default 50), exact `clientType`, canonical `version`, positive `userId`, `activeOnly`,
and ISO-8601 `since`. The `StartedAt` window defaults to 30 days and is capped at 90 days; ordering is
`StartedAt DESC, Id DESC`, and no total count is run.

`GET /api/admin/application-instances/version-distribution` groups that bounded population numerically and
returns launch `instanceCount`, separately deduplicated `distinctUserCount`, and nullable `latestHeartbeatAt`.
Both routes derive tenant/user authority from authentication, return 401/403 conventionally, and accept no
tenant selector. Phase 6A adds no migration, mutation, remote control, session/geolocation/enforcement/PubSub,
updater action, or UI.

## Phase 6B bounded administrative-read audit

The two Phase 6A routes retain their response and OpenAPI contracts. After a successful bounded DAO read, the
server writes one tenant/actor-attributed `AdministrativeReadAuditLog` row in the same transaction, then returns
the response. A page with one or 50 instances still writes one event; a distribution with any number of buckets
writes one event. Failed authentication, authorization, validation, tenant context, DAO reads, and audit writes
produce no successful response/audit claim. Audit failure is fail-closed and is handled by the existing safe
internal-error envelope; audit IDs and internals are not exposed.

Deploy `docs/sql/2026-09-29_administrative_read_audit_phase6b.sql` before enabling the audited server. It is
additive and safe while older clients run. Catalog and non-dbo RLS checks are separate verification scripts.

## Phase 8A session APIs

Authenticated bound-token clients may use `GET /api/sessions`, `POST /api/sessions/current/revoke`,
`POST /api/sessions/{sessionId}/revoke`, and `POST /api/sessions/revoke-others`. Tenant administrators additionally
have `GET /api/admin/sessions` (page default 50, maximum 100; optional `userId`, `clientType`, `activeOnly`, and
`since`) and `POST /api/admin/sessions/{sessionId}/revoke`. No request accepts a tenant selector. Revoke responses
contain no token or internal identifier. Deploy `2026-09-29_session_security_audit_phase8a.sql` before this server.
The tenant-admin list projection includes the target user's established display name and email so an authorized
same-tenant administrator can identify a session owner without per-row lookups. It still excludes internal session
row ids, JTI/token material, tenant ids, row versions, machine identifiers, IP/location, and audit metadata.

## Phase 8B best-effort invalidation publishing

When `LIVE_PUBLISH_ENDPOINT_URL` is configured, successful Phase 8A revocations publish the minimal versioned
`SESSION_INVALIDATED` LiveBus hint only after the SQL revocation and audit commit. `FUNCTION_KEY` is optional when
the key is not already present in the endpoint URL. A non-2xx response, timeout, or transport failure is sanitized
and cannot roll back the durable operation. With no endpoint configured the publisher is a no-op: authentication,
login, and durable revocation remain fully functional and authoritative.

The additive `APPLICATION_POLICY_CHANGED` publisher contract is global and release-channel scoped, matching the
existing policy read API; there is no runtime policy mutation/API in this phase. Receivers must reload
`GET /api/application-releases/policy/current` (or the established desktop policy service) rather than enforce the
event body. No delivery log, replay store, SQL migration, policy administration, or Phase 9 session UI is added.

## Phase 11A policy server time

The successful current-policy response additively includes `serverTime`: UTC database time captured with the policy read. Clients can anchor deadline presentation without trusting workstation wall clock. Existing clients ignore the additive field. No endpoint, authentication rule, schema, or cache directive changed.

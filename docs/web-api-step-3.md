# Web/API Migration Step 3: Server Port Wiring and Auth/Session Skeleton

Step 3 wires `shale-server` to the shared service ports introduced in Step 2 and adds the server-side auth/session design skeleton, but it deliberately does **not** implement browser/mobile authentication, issue tokens, or enable request-scoped tenant database access yet.

## What is wired

- `shale-server` depends on `shale-core` and `shale-data` so it can construct the shared service port adapters without depending on `shale-ui` or JavaFX.
- `ShaleServerServiceConfiguration` creates Spring beans for:
  - `AuthServicePort` via `AuthServiceAdapter`
  - `CaseServicePort` via `CaseServiceAdapter`
  - `TaskServicePort` via `TaskServiceAdapter`
  - `ContactServicePort` via `ContactServiceAdapter`
  - `NotificationServicePort` via `NotificationServiceAdapter`
- The DAO-backed adapters are constructed with `RequestScopedDbSessionProvider`, a temporary server `DbSessionProvider` skeleton. It resolves the current request session first and intentionally refuses to open a DB connection until authenticated tenant/user context exists.
- The existing `/api/health` route remains available and does not require tenant context.

## Auth/session skeleton added

The server now models the future request identity flow with JavaFX-free runtime classes:

- `ServerPrincipal` carries the authenticated Shale user id, `ShaleClientId`, and optional email/subject.
- `ServerSessionContext` represents either an authenticated request principal or an unauthenticated/unavailable context.
- `ServerSessionResolver` is the request-to-session abstraction that future auth code will implement.
- `UnauthenticatedServerSessionResolver` is the default/production placeholder implementation and always returns an unauthenticated context.
- `DevelopmentHeaderServerSessionResolver` is a temporary development-profile-only resolver that reads `X-Shale-UserId` and `X-Shale-TenantId` request headers. It is not authentication and must not be used for production, JWTs, cookies, Azure auth, or browser sessions.
- `ServerRuntimeSessionState` is the controller-facing guard used by DB-backed routes. It fails closed with `501 Not Implemented` when no principal is available.
- `RequestScopedDbSessionProvider` resolves a principal, obtains a runtime connection, and initializes SQL Server `SESSION_CONTEXT` with `ShaleClientId` and `PrincipalUserId` using the same `RuntimeSessionService` path as desktop before DAO/service-port calls. Without a resolved principal, it still fails closed before opening a connection.


## Login route skeleton

Step 3 now defines the initial browser/mobile credential-validation route shape:

- `POST /api/auth/login`
- Request JSON:

  ```json
  {
    "email": "user@example.com",
    "password": "plain-text password supplied by the client"
  }
  ```

- The controller calls `AuthServicePort.authenticate(email, password)` and does not log or echo the password.
- On successful credential validation, the route returns a temporary response with non-sensitive identity fields only:

  ```json
  {
    "authenticated": true,
    "userId": 123,
    "shaleClientId": 456,
    "displayName": "Example User",
    "nameFirst": "Example",
    "nameLast": "User",
    "todo": "TODO: token/session issuance is not implemented yet; this route only validates credentials."
  }
  ```

  `displayName`, `nameFirst`, and `nameLast` are derived from the authenticated `User` when those name fields are available. The response intentionally does **not** include password hashes, JWTs, session ids, cookie values, or other sensitive fields.

- On credential failure, the route returns `401 Unauthorized` with a safe response that does not disclose whether the email exists or expose adapter/database-specific failure details:

  ```json
  {
    "authenticated": false,
    "error": "invalid_credentials",
    "message": "Invalid email or password."
  }
  ```

The route is only a local server API skeleton. It validates credentials through the existing auth port, but it does **not** create browser session cookies, issue JWTs, or mark future DB-backed read requests as authenticated.


## Development request-context simulation

Step 3 also includes a temporary development-only request-context simulation so server DAO/service-port calls can be proven under a resolved user/tenant context without implementing real browser/mobile auth yet.

- Active only when the Spring `dev` profile is enabled.
- Request headers:

  ```http
  X-Shale-UserId: 123
  X-Shale-TenantId: 456
  ```

- The development resolver creates `ServerPrincipal(userId=123, shaleClientId=456)` from those positive integer header values. Missing, blank, malformed, or non-positive values leave the request unauthenticated and the guarded endpoints fail closed.
- The default/production profile continues to wire `UnauthenticatedServerSessionResolver`, so these headers are ignored outside the `dev` profile.
- No tenant is hard-coded and RLS is not bypassed. The resolved tenant/user context is applied to a runtime connection before DAO calls.
- The runtime connection path creates a request-scoped `RuntimeSessionService`, calls `initialize(shaleClientId, userId)`, and then uses `getConnection()` so SQL Server receives the same `SESSION_CONTEXT` keys desktop uses: `ShaleClientId` and `PrincipalUserId`.

### Development proof endpoint

`GET /api/dev/whoami` is available only in the Spring `dev` profile. With valid development headers it returns:

```json
{
  "authenticated": true,
  "userId": 123,
  "shaleClientId": 456
}
```

Without valid development headers, the endpoint returns the same fail-closed `501 Not Implemented` response as other guarded endpoints.

### First read endpoint enabled through the context path

`GET /api/notifications/unread` now uses the resolved `ServerRuntimeSessionState` principal for `userId` and `shaleClientId`. In the default profile it remains blocked because no principal is resolved. In the `dev` profile, valid temporary headers allow the controller to call `NotificationServicePort.listUnreadNotifications(shaleClientId, userId)`, and the DAO-backed adapter obtains a runtime connection with SQL `SESSION_CONTEXT` initialized before executing the query.

This is still not browser/mobile authentication: no JWTs, browser session cookies, Azure auth, durable sessions, or token validation have been implemented.

## Intended future flow

1. Browser/mobile authenticates with a server endpoint.
2. The server validates credentials/session state and resolves both user id and `ShaleClientId` into a `ServerPrincipal`.
3. Each HTTP request creates or resolves a `ServerSessionContext` for the request principal.
4. `RequestScopedDbSessionProvider` creates scoped runtime DB access for the request.
5. SQL Server `SESSION_CONTEXT` is set with tenant and principal user values before DAO calls.
6. DAO/service-port calls execute under the database RLS policies for that tenant/user context.

## Initial read routes

The server has route handlers for the first safe read surface:

- `GET /api/health`
- `GET /api/cases/search?query=`
- `GET /api/cases/{caseId}`
- `GET /api/cases/{caseId}/tasks`
- `GET /api/contacts/search?query=`
- `GET /api/notifications/unread`

Only `/api/health` returns live data in the default profile. The DB-backed read routes currently return `501 Not Implemented` with a TODO message because `UnauthenticatedServerSessionResolver` does not provide authenticated tenant/user context. Under the temporary `dev` profile, `GET /api/notifications/unread` can be exercised with development headers to prove the authenticated request-context and runtime `SESSION_CONTEXT` path.

## Why DB-backed endpoints fail closed

Desktop code initializes runtime database access after login and sets `ShaleClientId` / principal user context on SQL Server sessions. `shale-server` does not yet have an equivalent browser/mobile authentication flow, request principal, tenant resolution, or request-scoped session context. Returning fake data or using a hard-coded tenant would risk bypassing or weakening RLS assumptions.

Until that server runtime context exists, DB-backed endpoints must fail closed instead of opening unscoped runtime connections.

## Remaining blockers before enabling DB-backed reads

- Decide and implement browser/mobile session/token issuance after credential validation.
- Persist/verify issued browser/mobile sessions or tokens and map them back to a Shale user and tenant.
- Add cookie/JWT security decisions, including expiration, rotation, revocation, CSRF posture for cookies, and secure transport requirements.
- Replace the development header resolver with real browser/mobile authentication and request principal resolution.
- Decide how production request-scoped runtime datasource pooling should be managed and observed.
- Confirm pooled connections cannot leak prior tenant/user `SESSION_CONTEXT` values across requests and add integration coverage with a safe database strategy.
- Decide how server errors should represent missing tenant/user context consistently across controllers.
- Add integration tests around request-scoped session context once a safe non-production database test strategy exists.

## Test coverage added

- Controller tests verify `POST /api/auth/login` calls `AuthServicePort.authenticate(email, password)`, returns the temporary non-sensitive success shape, returns safe `401 Unauthorized` failures, and does not create browser session cookies.
- Development resolver tests verify missing headers remain blocked and valid `X-Shale-UserId` / `X-Shale-TenantId` headers create a `ServerPrincipal`.
- Request-scoped DB provider tests verify missing principals block DB access, valid development principals invoke the runtime connection provider, and `RuntimeSessionServiceConnectionProvider` uses the desktop `SESSION_CONTEXT` initialization SQL for `ShaleClientId` and `PrincipalUserId`.
- Controller tests verify `GET /api/dev/whoami` returns the resolved development principal and `GET /api/notifications/unread` reaches the service layer under authenticated development request context.
- Controller routing tests verify `/api/health` still returns `{ "status": "ok" }`.
- Controller routing tests verify each DB-backed route returns `501 Not Implemented` with a clear TODO message while server auth/session context is unavailable.
- Configuration tests verify the Spring configuration constructs the shared service port adapter beans and request/session skeleton beans without opening a database connection.
- Runtime skeleton tests verify the placeholder resolver returns unauthenticated context, tenant access is blocked without a principal, and `ServerPrincipal` carries user/tenant ids without defaults.

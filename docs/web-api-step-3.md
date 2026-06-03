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
- `UnauthenticatedServerSessionResolver` is the current placeholder implementation and always returns an unauthenticated context.
- `ServerRuntimeSessionState` is the controller-facing guard used by DB-backed routes. It fails closed with `501 Not Implemented` when no principal is available.
- `RequestScopedDbSessionProvider` is the planned DAO-facing provider. It does not open connections today; future work should make it borrow a runtime connection and set SQL Server `SESSION_CONTEXT` before DAO calls.

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

Only `/api/health` returns live data today. The DB-backed read routes currently return `501 Not Implemented` with a TODO message because `UnauthenticatedServerSessionResolver` does not provide authenticated tenant/user context.

## Why DB-backed endpoints fail closed

Desktop code initializes runtime database access after login and sets `ShaleClientId` / principal user context on SQL Server sessions. `shale-server` does not yet have an equivalent browser/mobile authentication flow, request principal, tenant resolution, or request-scoped session context. Returning fake data or using a hard-coded tenant would risk bypassing or weakening RLS assumptions.

Until that server runtime context exists, DB-backed endpoints must fail closed instead of opening unscoped runtime connections.

## Remaining blockers before enabling DB-backed reads

- Define browser/mobile authentication and session issuance.
- Resolve tenant and principal user from each authenticated HTTP request.
- Implement request-scoped runtime datasource access in `RequestScopedDbSessionProvider`.
- Set SQL Server `SESSION_CONTEXT` before DAO/service-port calls and clear/close the scoped connection safely afterward.
- Decide how server errors should represent missing tenant/user context consistently across controllers.
- Add integration tests around request-scoped session context once a safe non-production database test strategy exists.

## Test coverage added

- Controller routing tests verify `/api/health` still returns `{ "status": "ok" }`.
- Controller routing tests verify each DB-backed route returns `501 Not Implemented` with a clear TODO message while server auth/session context is unavailable.
- Configuration tests verify the Spring configuration constructs the shared service port adapter beans and request/session skeleton beans without opening a database connection.
- Runtime skeleton tests verify the placeholder resolver returns unauthenticated context, tenant access is blocked without a principal, and `ServerPrincipal` carries user/tenant ids without defaults.

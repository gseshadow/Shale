# Web/API Migration Step 3: Server Port Wiring and Fail-Closed Read Routes

Step 3 starts wiring `shale-server` to the shared service ports introduced in Step 2, but it deliberately does **not** implement browser/mobile authentication or request-scoped tenant session context yet.

## What is wired

- `shale-server` now depends on `shale-core` and `shale-data` so it can construct the shared service port adapters without depending on `shale-ui` or JavaFX.
- `ShaleServerServiceConfiguration` creates Spring beans for:
  - `AuthServicePort` via `AuthServiceAdapter`
  - `CaseServicePort` via `CaseServiceAdapter`
  - `TaskServicePort` via `TaskServiceAdapter`
  - `ContactServicePort` via `ContactServiceAdapter`
  - `NotificationServicePort` via `NotificationServiceAdapter`
- The DAO-backed adapters are constructed with a temporary server `DbSessionProvider` placeholder. That provider intentionally throws if a DB connection is requested before server auth/session context exists.
- The existing `/api/health` route remains available and does not require tenant context.

## Initial read routes

The server now has route handlers for the first safe read surface:

- `GET /api/health`
- `GET /api/cases/search?query=`
- `GET /api/cases/{caseId}`
- `GET /api/cases/{caseId}/tasks`
- `GET /api/contacts/search?query=`
- `GET /api/notifications/unread`

Only `/api/health` returns live data today. The DB-backed read routes currently return `501 Not Implemented` with a TODO message because they require authenticated request context before they can safely set tenant/user session context for RLS.

## Why DB-backed endpoints fail closed

Desktop code initializes runtime database access after login and sets `ShaleClientId` / principal user context on SQL Server sessions. `shale-server` does not yet have an equivalent browser/mobile authentication flow, request principal, tenant resolution, or request-scoped session context. Returning fake data or using a hard-coded tenant would risk bypassing or weakening RLS assumptions.

Until that server runtime context exists, DB-backed endpoints must fail closed instead of opening unscoped runtime connections.

## Remaining blockers before enabling DB-backed reads

- Define browser/mobile authentication and session issuance.
- Resolve tenant and principal user from each authenticated HTTP request.
- Implement a request-scoped `DbSessionProvider` that sets SQL Server session context before DAO calls.
- Decide how server errors should represent missing tenant/user context consistently across controllers.
- Add integration tests around request-scoped session context once a safe non-production database test strategy exists.

## Test coverage added

- Controller routing tests verify `/api/health` still returns `{ "status": "ok" }`.
- Controller routing tests verify each DB-backed route returns `501 Not Implemented` with a clear TODO message while server auth/session context is unavailable.
- Configuration tests verify the Spring configuration constructs the shared service port adapter beans without opening a database connection.

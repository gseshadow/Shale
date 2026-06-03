# Web/API Migration Step 2: Shared Logic Inventory

This note identifies the auth, session, case, task, contact, and notification logic that `shale-server` will likely need to reuse as the browser/mobile API grows. This is documentation only: no code should move in this step, and no runtime behavior should change.

## Current modules and responsibilities

- `shale-core`
  - Shared Java model, DTO, runtime contracts, and utility code.
  - Relevant exports include `com.shale.core.model`, `com.shale.core.dto`, `com.shale.core.runtime`, `com.shale.core.auth`, `com.shale.core.result`, and common constants/utilities.
  - Important existing reusable types include `User`, `DbSessionProvider`, case/task DTOs, `RoleSemantics`, and `PasswordVerifier`.
- `shale-data`
  - JDBC data access and persistence concerns.
  - Owns `DataSources`, authentication against the app/auth pool, runtime tenant-aware connections through `RuntimeSessionService`, DAOs, and PHI/audit persistence helpers.
  - This is the primary existing module `shale-server` should reuse first, but server request scoping will need to be introduced before DAO calls are safely exposed over HTTP.
- `shale-ui`
  - JavaFX presentation, controllers, UI state, card/dialog factories, document rendering, and several UI-facing service facades.
  - Some `shale-ui` service classes currently contain business/application logic that should be extracted into shared service code before `shale-server` depends on it.
  - `shale-server` should not depend on `shale-ui` because it pulls in JavaFX/UI concepts and desktop-specific runtime bridge assumptions.
- `shale-desktop`
  - JavaFX application launcher and desktop composition root.
  - Wires `Config`, `DataSources`, `AuthServiceImpl`, `RuntimeSessionService`, `DesktopRuntimeSessionProvider`, `SceneRouter`, and live update bridge behavior.
  - Desktop startup, login, logout, live bus, update launcher, and file-opening behavior must stay desktop-only during the migration.
- `shale-server`
  - Newly added Spring Boot `jar` module with only a minimal application entrypoint and `/api/health` endpoint.
  - Future responsibility: host browser/mobile APIs by composing shared core/data/application services with HTTP request/session/auth concerns.

## Logic `shale-server` will likely need from `shale-core`

- Auth/session identity models and contracts:
  - `com.shale.core.model.User` for authenticated user identity and tenant fields.
  - `com.shale.core.runtime.DbSessionProvider` as the existing DAO-facing connection provider contract.
  - `com.shale.core.auth.PasswordVerifier` if auth verification is consolidated on the core interface rather than the duplicate data package interface.
- Case/task DTOs already suitable for API boundaries or service return values:
  - `CaseOverviewDto`, `CaseDetailDto`, `CasePartyDto`, `CaseTimelineEventDto`, `CaseUpdateDto`.
  - `CaseTaskListItemDto`, `TaskDetailDto`, `TaskPriorityOptionDto`, `TaskStatusOptionDto`.
- Shared semantic helpers:
  - `RoleSemantics` for role IDs/meaning used by DAOs and future authorization/service decisions.
  - `Result`, `Preconditions`, and app constants where they are independent of JavaFX/desktop runtime.


## Shared service ports added in Step 2

The first concrete setup for shared services is a set of JavaFX-free interfaces in `shale-core` under `com.shale.core.service`. These are ports only; no implementations were moved and desktop wiring continues to use the current DAO/UI-service path.

- `AuthServicePort`
  - Minimal login boundary returning the shared `User` identity in a `Result`.
  - Intended first adapter: wrap existing `shale-data` `AuthService` / `AuthServiceImpl` and map `AuthException` to `Result.fail(...)`.
- `CaseServicePort`
  - Read/detail/search/note/update shaped boundary based on `CaseDao` and current `CaseDetailService` capabilities.
  - Includes TODO placeholder command records for future write endpoint DTOs.
- `TaskServicePort`
  - Boundary for case task lists, assigned task lists, task detail, priorities/statuses, create/update, and assignment operations.
  - Intended first adapter: extract the business portions of `CaseTaskService` without copying JavaFX notification or live-update presentation code.
- `ContactServicePort`
  - Boundary for contact search/detail/create/update/soft-delete operations.
  - Defines minimal shared records because contact API DTOs do not yet exist in `shale-core` and server code should not depend on `ContactDao` row classes directly.
- `NotificationServicePort`
  - Durable notification boundary for unread/read/dismiss/create operations.
  - Explicitly avoids `shale-ui` notification classes such as `NotificationCenterService`, JavaFX properties, and observable lists.

Next intended adapter step: create thin adapter implementations outside `shale-ui` that delegate to `shale-data` DAOs/services, then switch desktop UI services to consume those adapters only after parity is proven. `shale-server` can then wire the same ports with request-scoped session context.


## Thin data adapters added in Step 2

A first set of implementation adapters now lives in `shale-data` under `com.shale.data.service.adapter`. These classes implement the `shale-core` service ports without changing desktop wiring and without adding Spring or JavaFX dependencies.

- `AuthServiceAdapter` delegates `AuthServicePort.authenticate(...)` to the existing `shale-data` `AuthService` and maps `AuthException` to `Result.fail(...)`.
- `CaseServiceAdapter` delegates safe read/search methods to `CaseDao` (`getDetail`, `getOverview`, `searchCasesByName`, and `listCaseUpdates`). Placeholder write methods still throw `UnsupportedOperationException` because the port commands do not yet model the full existing note/update return and row-version contracts.
- `TaskServiceAdapter` delegates task list/detail/priority/status/assignment methods to `TaskDao`. `createTask(...)` delegates only when no explicit status is requested; explicit status creation remains a TODO because the current DAO create method resolves the default status internally.
- `ContactServiceAdapter` delegates search/detail/create/update/delete operations to `ContactDao` and maps between the port records and existing contact DAO request/row types.
- `NotificationServiceAdapter` delegates unread/read/dismiss operations to `NotificationDao`. Generic notification creation remains a TODO and throws `UnsupportedOperationException` until the port is split into entity/action-specific creation commands matching existing DAO methods.

Next intended adapter step: add focused tests around these adapters with fake/stub DAOs or a test `DbSessionProvider`, then decide whether the placeholder port methods should be narrowed, split, or given richer command/response records before `shale-server` endpoints consume them.

## Logic `shale-server` will likely need from `shale-data`

- Authentication and password verification:
  - `AuthService` / `AuthServiceImpl` for credential validation and `User` loading.
  - `BCryptPasswordVerifier` for password hash verification.
  - `DataSources.auth()` for the auth/app pool used by login.
- Runtime session and tenant-aware DB access:
  - `RuntimeSessionService` currently initializes `ShaleClientId` and `PrincipalUserId`, then sets SQL Server session context on each runtime connection.
  - Existing DAOs depend on a `DbSessionProvider`; the server will need a request-scoped implementation that derives tenant/user context from authenticated HTTP requests instead of desktop login state.
- Case data and timeline operations:
  - `CaseDao` for intake creation, listing/searching cases, detail/overview loading, updates, soft delete/restore, notes/updates, contacts/organizations/parties, statuses, practice areas, team/responsible attorney, and timeline events.
  - `PhiAuditService` and `AuditLogDao` where case/contact/task reads or writes require PHI/audit handling.
- Task data and timeline/notification operations:
  - `TaskDao` for case task lists, assigned task boards, task detail, task creation/update/delete/complete state, task assignments, priorities/statuses, task updates, and task timeline events.
  - `NotificationDao` for durable notification rows created by task assignment, task notes, task due dates, and task actions.
- Contact/user data used by case/task flows:
  - `ContactDao` for tenant contact directories, contact search, details, related cases, profile updates, creation, and soft deletion.
  - `UserDao` for assignable users, tenant user lists/search, profile details, and role assignments used by task/case assignment flows.
- Supporting DAOs that will likely matter as API scope grows:
  - `OrganizationDao` for organization endpoints and case organization relationships.
  - `CalendarEventDao`, `CalendarEventTypeDao`, and `CalendarFeedDao` for calendar/mobile views.
  - `UserPreferencesDao` and `UserBoardLanePreferencesDao` for user-specific board/UI preferences if exposed to browser clients.

## UI-specific code that must not be reused by `shale-server`

`shale-server` should avoid depending on `shale-ui` and `shale-desktop`. The following code is UI/desktop specific and should not become server dependencies:

- JavaFX controllers, components, dialogs, factories, FXML, CSS, and UI state classes:
  - Examples: `CaseController`, `CasesController`, `TaskDetailDialog`, `NewTaskDialog`, `CaseCardFactory`, `NotificationCard`, `AppState`, and related JavaFX resource files.
- JavaFX notification presentation state:
  - `NotificationCenterService`, `AppNotification`, card/dialog classes, and any code using `javafx.application.Platform`, JavaFX properties, or observable lists.
  - Server-side notification APIs should use shared domain/application services backed by `NotificationDao`, not JavaFX notification state.
- Desktop composition and runtime bridge code:
  - `MainApp`, `SceneRouter`, `DesktopUiAuthService`, `DesktopUiRuntimeBridge`, `DesktopRuntimeSessionProvider`, `DefaultRuntimeSessionProvider`, and `SessionContext`.
  - Desktop live bus clients, negotiate calls, update launchers, install locators, and `openPath` behavior.
- UI document/export code until explicitly designed for server-side use:
  - `CaseDocumentExportService`, HTML/PDF renderers, and file-oriented UI export flows should not be reused by default because they may carry JavaFX/user-workstation assumptions.
- UI facade services in their current form:
  - `CaseDetailService`, `CaseTaskService`, `ContactDetailService`, `SearchService`, `CalendarService`, `DurableNotificationService`, and preference services are useful inventories of application behavior, but should not be depended on directly from `shale-server` while they live in `shale-ui`.

## Proposed minimal next refactor path

Keep the next steps small and compatible with desktop behavior. Prefer extracting application services behind interfaces while leaving DAOs and desktop UI flows intact.

### Auth/session

1. Introduce a shared application-service layer outside `shale-ui`, likely a new module such as `shale-services` or a clearly non-UI package in an existing shared module.
2. Move or wrap login orchestration into a shared `AuthApplicationService` that returns `User` or an auth/session DTO using `shale-data` authentication.
3. Add a server-specific request-scoped `DbSessionProvider` that sets/uses `ShaleClientId` and `PrincipalUserId` from authenticated HTTP context.
4. Keep `DesktopRuntimeSessionProvider` and `RuntimeSessionService.initialize(...)` behavior unchanged for desktop until the server path is proven.

### Cases

1. Extract non-UI logic currently in `CaseDetailService` and case-related controller orchestration into a shared `CaseApplicationService` that delegates to `CaseDao`.
2. Keep pure API inputs/outputs in `shale-core` DTOs or new shared request/response records without JavaFX dependencies.
3. Preserve `CaseDao` tenant-scoped method signatures and timeline/audit behavior; do not let controllers call low-level DAO methods directly from multiple places without a shared service policy.

### Tasks

1. Extract business operations from `CaseTaskService` into a shared `TaskApplicationService` while leaving the existing UI service as a thin adapter.
2. Centralize task assignment, completion, update/note, timeline event, and notification creation behavior so desktop and server produce consistent side effects.
3. Keep task priority/status lookup and task board list methods reusable through the shared service rather than duplicating endpoint-specific DAO calls.

### Contacts

1. Extract `ContactDetailService` behavior into a shared `ContactApplicationService` for directory/search/detail/update/create/soft-delete operations.
2. Keep contact/case relationship logic coordinated with `CaseApplicationService` so role/party updates, tenant checks, and audit behavior are not duplicated.
3. Continue using `ContactDao` request objects and rows only where they are appropriate for service boundaries; introduce API DTOs if DAO row shapes leak persistence concerns.

### Notifications

1. Separate durable notification persistence and generation from JavaFX notification presentation.
2. Create a shared notification service around `NotificationDao` for list/read/dismiss/create operations.
3. Leave `NotificationCenterService`, banners, unread JavaFX properties, and local UI seeding as desktop/UI adapters.
4. Decide how live updates will reach browser/mobile clients separately from the existing desktop live bus bridge.

## Risks and constraints to preserve

- RLS/session context safety:
  - `RuntimeSessionService` sets SQL Server session context keys `ShaleClientId` and `PrincipalUserId` on runtime connections. Server request handling must guarantee the correct values are applied for every connection and cannot leak between tenants or users through pooled connections.
  - Any future server `DbSessionProvider` should be request-scoped or otherwise strongly bound to authenticated request identity.
- Tenant stamping and method parameters:
  - Many DAO methods accept `shaleClientId` explicitly while also relying on session context/RLS. Future shared services must keep these values consistent and should reject mismatches early.
  - Create/update paths must preserve tenant stamping behavior for new rows and audit entries.
- Auth pool vs runtime pool:
  - Login currently reads from the auth/app datasource, while tenant-aware business operations use the runtime datasource. Server auth/session design must preserve that separation unless intentionally redesigned later.
- Desktop startup behavior:
  - `DataSources` currently avoids failing startup on transient Azure/network issues and lazily creates the runtime pool. Do not alter this while extracting server services.
  - Desktop login initializes runtime session state after successful auth; server requests should not depend on global mutable desktop login state.
- Side effects and live updates:
  - Some UI services combine DAO changes, timeline rows, durable notifications, and live update publishing. Extracting shared services must preserve side effects for desktop and avoid double-publishing or missing notifications.
- JavaFX dependency leakage:
  - Any shared module used by `shale-server` must stay free of JavaFX classes, FXML/CSS resources, `Platform.runLater`, observable properties/lists, and desktop file/update APIs.
- Broad-refactor risk:
  - `CaseDao` and `TaskDao` are large and contain many operations. The safer path is to wrap and gradually extract service methods by feature slice rather than split DAOs or move package trees in one step.

Shale is a multi-tenant law firm case management platform.

Primary modules:
- shale-core
- shale-data
- shale-ui
- shale-desktop
- shale-server

Current primary client:
- Curtis & Co.
- Tenant separation via ShaleClientId

Desktop application:
- JavaFX
- Java 21
- Maven multi-module

Database:
- Azure SQL Server

Primary entities:
- Cases
- Contacts
- Organizations
- Tasks
- Users
- Notifications

Navigation:
My Shale
Cases
Contacts
Organizations
Team
Reports
Calendar
Settings

The dedicated Tasks route remains available for internal, deep-link, and back-stack navigation, but it is not a shared main-sidebar destination. My Shale exposes task work through its My Tasks tab.

## New Intake date authority

New Intake persists the date/time shown or edited by the user as a timed `dbo.CaseDates` occurrence.
The effective Intake type is selected through the protected, tenant-effective `INTAKE` semantic-role
mapping rather than a label or fixed type id. Case creation, occurrence persistence, configured dates,
and required audits are one transaction. The Cases list displays and sorts Intake Date from this
authoritative occurrence; `dbo.Cases.CallerDate` and `CallerTime` remain only as legacy reconciliation
and history inputs.

The forward-only, idempotent production reconciliation inserted 21 missing Intake occurrences for
`ShaleClientId = 7`. New Intake and reconciliation do not create duplicate `CalendarEvents`; Calendar
projects `CaseDates` directly.

## Calendar feed source-of-truth rules

The desktop Calendar feed is a unified read model, not a separate scheduling store. `dbo.CalendarEvents` contains persisted scheduled events that users can create and edit. Task due dates are projected directly from `dbo.Tasks.DueAt`; accepted, denied, and closed dates remain lifecycle projections from `dbo.Cases` because they are outside the Case Dates migration; and active authoritative occurrences are projected directly from `dbo.CaseDates`. The former fixed `dbo.Cases` projections for intake, injury, medical-negligence, discovery, deadline, fee-agreement, and non-engagement dates were removed at the Calendar cutover because they duplicated or became stale beside `CaseDates`.

Authoritative `CaseDates` use occurrence identity (`CASE_DATE:<CaseDates.Id>`) and the `CASE_DATE` source discriminator, preserve stored local `StartsAt`/`EndsAt`/`AllDay` values with range-intersection filtering, and use the stored type identity plus the existing effective tenant/global type presentation rules. Any permitted calendar category and arbitrary custom type can appear; items are never collapsed by label or date. Deleted occurrences are hidden, stored historical type presentation remains the fallback when no active effective overlay exists, and no absent occurrence is fabricated from a workflow flag or `dbo.Cases` fallback.

Calendar projects Case Dates without copying them to `CalendarEvents`. Activating a projected occurrence opens the authoritative `CaseDateOccurrenceDialog` in place over Calendar: a shared UI launcher reloads by stable Case Date ID, validates tenant/actor/Case/open/generation state, and updates through the existing `CaseServicePort` RowVer mutation. Success publishes the PHI-free `CaseDates` LiveBus invalidation and reloads the unchanged Calendar range; cancel and failure do not navigate. Tenant checks, client-instance suppression, event-id coalescing, and load generations prevent cross-tenant corruption, loops, and stale responses. LiveBus carries identifiers and change classification only, never date values, labels, names, notes, or concurrency tokens.

Task due dates and Case Dates must not be duplicated into `dbo.CalendarEvents`; `CalendarEvents` remains the source of truth only for real persisted scheduled events. By default, the feed hides cancelled persisted events and completed tasks while continuing to respect task and case soft-delete filtering. The lifecycle authority for accepted, denied, and closed dates remains intentionally unchanged in this slice pending a separately proven status-history authority.


## Desktop foreground activity observation

The authenticated JavaFX shell owns one `ForegroundHumanActivityObserver`. It is installed after the main
scene is shown and observes only key press, mouse press, scroll, and touch press events from a showing, focused
primary Shale window or a JavaFX window owned by it. Filters never consume input. Logout, user switching, and
normal shutdown detach all filters and clear the timestamp; login credential entry is excluded.

The only state is an optional UTC `Instant` supplied by an injected `Clock` and held in an
`AtomicReference` for safe cross-thread reads. No event payload, key/content, pointer coordinates, target, user,
tenant, or business identifier is retained. Mouse movement and all programmatic/background work are excluded.
This Phase 5A foundation is process-local: it performs no persistence, SQL, API/network call, heartbeat, idle
classification, policy action, audit mutation, PubSub operation, updater decision, or OS-wide monitoring.

## Application-instance heartbeat runtime (Phase 5B, complete)

The authenticated desktop shell starts one application-instance heartbeat lifecycle only after a
successful Phase 4B enrollment and Phase 5A observer installation. It sends the strict
`AppVersionProvider.currentVersion()` semantic version and only the observer's `Optional<Instant>`;
JavaFX input events never cross the networking/service boundary. The cadence is 60 seconds with
bounded ±10-second jitter. A single completion-rescheduled task plus an in-flight guard prevents
overlap and queued retries. Transient failures are sanitized and suppressed after the first outage
log, then retry only on the next normal interval. Logout and shutdown cancel scheduling before the
best-effort instance end. A generation token makes callbacks from an old authenticated enrollment
unable to affect a replacement enrollment. Ended or owner-inaccessible instances stop the lifecycle.

The database/API heartbeat is authoritative; Web PubSub is not involved. This foundation adds no
idle/online classification, enforcement directive, remote logout, durable user session, version
policy comparison, geolocation, updater action, device UI, or heartbeat/activity history.

## Tenant-admin application-instance reads (Phase 6A, complete; Phase 6B audit)

The server-only `ApplicationInstanceAdminReadServicePort` provides set-based, tenant-qualified bounded recent
launch pages and numeric version distribution. Authenticated principal identity, controller and DAO admin
checks, and strict RLS enforce tenant isolation. Responses expose raw nullable timestamps and the random Phase
4A machine UUID, but no inferred presence, hostname, IP/location, secrets, row version, session state, UI, or
mutation.

The same DAO transaction now appends one `AdministrativeReadAuditLog` row after each successful recent-page or
version-distribution query and commits before returning the sensitive response. Audit persistence is fail-closed;
authentication, authorization, validation, tenant-context, and read failures create no success event. Metadata is
deterministic, allowlisted, and bounded, and result volume never changes the one-event-per-query cardinality.
No ordinary reads, heartbeat writes, instance mutations, remote control, sessions, geolocation, PubSub,
enforcement, or UI are included.
## Durable user-session foundation (Phase 7A; verified)

The core/data boundary defines internal `UserSessionServicePort` -> `UserSessionServiceAdapter` ->
`UserSessionDao` operations for durable create/find/idempotent revoke/conditional JTI rotation. This model is an
authentication lifecycle, not an application process: its optional same-owner desktop instance relationship
does not couple heartbeat or instance abandonment to revocation. Phase 7A itself introduced no runtime wiring. Phase 7B now connects server bearer filtering, login, refresh, and logout to this boundary as described below; desktop remains outside it. There are no new session-management APIs, UI, PubSub, geolocation, enforcement, or device controls.

## Durable server API authentication (Phase 7B)

Server API login continues through `AuthServicePort`, then creates a tenant/user-qualified Phase 7A `UserSessions` row before returning a JWT. New JWTs retain the existing identity/time claims and add public UUID `sid`; SQL `CurrentAccessJti`, expiry, and revocation are authoritative on every authenticated bound-token request. Refresh conditionally rotates the JTI and logout durably revokes only that session. The request resolver performs no lookup for absent bearer tokens or public routes. A temporary, required-cutoff legacy branch accepts otherwise-valid pre-cutover unbound JWTs and upgrades them on refresh; its in-memory revocation store is not authoritative for bound sessions. Desktop direct-JDBC authentication is unchanged until Phase 7C.

## Desktop durable session enrollment (Phase 7C; verification pending)

After the existing direct-JDBC bcrypt login and runtime tenant context succeed, desktop best-effort enrolls its
Phase 4B instance and performs one additive HTTPS credential exchange. The server re-verifies the credential,
derives tenant/user rather than accepting asserted ids, validates any active DESKTOP instance against that owner,
and issues the ordinary Phase 7B bound JWT with a durable DESKTOP `UserSessions` row. The centralized desktop
bearer is process-memory-only and is not a JDBC authority. Endpoint absence or transport failure leaves an explicit
JDBC-only compatibility session; security rejection never silently downgrades server-session functionality.
Logical logout revokes and clears the bound session; process exit clears memory and ends the instance without
reclassifying exit as user logout. Enrollment is not heartbeat, and this phase adds no remote controls, PubSub,
geolocation, UI, or update enforcement. Required Maven verification is pending because Maven Central returned 403.

## Authoritative durable-session management (Phase 8A)

The server exposes additive self session list/current revoke/owned revoke/revoke-others APIs and bounded tenant-admin
list/revoke APIs. Current identity is the JWT `sid`, never recency or machine identity. SQL revocation is checked on
the next bound request by every replica. There is no session cache, process-presence inference, UI, PubSub delivery,
geolocation, trust/fingerprinting, or updater behavior in this phase; `ApplicationInstances` remain unchanged.

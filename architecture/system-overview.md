Shale is a multi-tenant law firm case management platform.

## Minimum-version safe drain (Phase 11B)

The global application policy read and database UTC anchor are the only minimum-version authority.
The desktop owns a session-scoped `SafeWorkDrainCoordinator`: controllers gate mutation-workflow entry
and retain a lease for already-open work, while reads, navigation, heartbeat, save completion, updater,
and normal exit remain available. This is intentionally not a generic read-only access mode. Direct
JDBC remains outside HTTP middleware, so API enforcement alone cannot provide this contract. See
`docs/architecture/application-release-session-management.md` for the 15-minute bounded-cache rule,
startup surface, rollout constraint, and workflow inventory.

Primary modules:
- shale-core
- shale-data
- shale-ui
- shale-desktop
- shale-server

## Installer-owned Windows registration (Phase 13F; installed validation pending)

The WiX 3.14 lifecycle, not Shale runtime, owns one protected 64-bit HKLM registration per per-user installation at
`SOFTWARE\Shale\Installations\<opaque UUID>`. It stores only schema, UUID, Windows owner SID, install root, and owner
support root. Elevated actions validate the pre-elevation MSI owner SID against ProfileList path authority, reject
reparse roots, harden ACLs, preserve identity across upgrade/repair, and perform exact uninstall/rollback. A read-only
core reader classifies invalid, stale, duplicate, and unsafe records without healing them. Windows packaging feeds
a minimally augmented copy of JDK 21's own `main.wxs` resource into jpackage's original compile, so
jpackage retains its implicit WiX defaults and generated package identity without a second main-source compile.
The MSI declares only `InstallScope="perUser"`; same-owner administrator consent elevates `msiexec`, after which
deferred non-impersonating actions use the installer service for protected writes. Secondary-credential elevation
is unsupported because it changes the per-user installation owner.
Installed Windows acceptance remains outstanding, so Phase 13F is in progress even though implementation is complete and the full
repository `mvn test` verification is PASS. Logged-out automatic updating remains `UNSUPPORTED`; there is no task,
service, SYSTEM executor, principal selection, or scheduler.

## Privacy-safe update-attempt observability (Phase 12)

The desktop and the existing updater correlate one user-initiated handoff with a random UUID and a bounded,
atomic properties file under the per-user Shale application-support directory. The file contains only semantic
source/target/actual versions, `PRODUCTION`/`DESKTOP`, closed lifecycle/failure codes, and local diagnostic UTC
times. It contains no tenant/user identity, machine identifier, credential, network/location data, PHI, exception,
URL, or log text. Updater launch and install application are evidence, not success: only a later Shale startup at
the target version or a newer compatible production version records `COMPLETED`. Startup reconciliation is
best-effort and cannot block application startup. Detailed diagnostics remain in the existing local updater logs.

## Workstation automatic-update preference (Phase 13A)

The desktop exposes a fail-safe machine-scoped permission through `WorkstationUpdatePreferenceProvider`. Its
bounded versioned file lives beside, but independently of, the Phase 4A machine UUID. Missing, disabled,
unavailable, or corrupt state never permits future unattended execution. Authenticated Shale administrators may
change it in Settings; every user observes the same state across logout, tenant switch, and restart. This foundation
performs no scheduling, idle detection, updater launch, policy decision, or central audit/database mutation.

## Windows unattended-update feasibility foundation (Phase 13B)

Phase 13B adds pure, safe-default-off contracts only; it does not register a timer or Windows task and cannot
launch an unattended production update. `UnattendedUpdateEvaluationService` rereads the Phase 13A provider at
evaluation time. Its reasoned evaluator requires fresh eligible Phase 11A policy, an exact Production policy/
manifest target match, a strictly newer compatible package, a workstation-local 02:00–04:00 window, at least
30 minutes since observed Phase 5A foreground input, no foreground window or Phase 11B work, a cooperative
shutdown capability, and an available shared execution lock. The final two foundation guarantees are now supplied: one aggregate inspect-only readiness contract reuses Phase 11B workflow leases and save registrations, and one per-user OS file lock coordinates desktop handoff with updater execution. Only `READY` permits the future normal-lifecycle shutdown seam; uncertainty defers. Manual force-stop compatibility remains isolated from additive unattended mode. A 2026-09-30
validation pass could not convert those findings into installed-Windows evidence because only a Linux runner was
available; the Windows checklist therefore remains open. The full repository `mvn test` has since passed outside
that restricted runner, so automated Maven verification is PASS and is not the remaining blocker. Consequently
activation is intentionally absent.
Closed/logged-out operation, Task Scheduler provisioning, macOS parity, forced termination, and automatic reboot
are unsupported. Four eligibility evaluations at 30-minute spacing and one real handoff are the maximum proposed
per local-date/time-zone window; Phase 12 starts only at handoff.

## Windows in-session automatic-update activation (Phase 13C)

The authenticated JavaFX runtime owns one daemon `InSessionAutomaticUpdateScheduler`. It starts only after the
policy coordinator and foreground activity observer are available, and it stops on logout, user switch, or process
shutdown with generation-guarded callbacks. It schedules the next local 02:00 candidate rather than polling every
minute, delegates every decision and immediate recheck to the Phase 13B evaluator, and launches the ZIP updater only
as `UNATTENDED`. The machine preference is reread for every evaluation and recheck. Four evaluations approximately
30 minutes apart and one handoff are allowed per local-date/time-zone window; Phase 12 still begins only after the
execution lock is acquired and the real updater launch begins.

This is not a background system updater: Shale must already be running in an authenticated Windows desktop session
and remain idle and safely closable. It creates no Task Scheduler entry, service, wake timer, logged-out helper,
reboot, macOS scheduler, API, or SQL state. Sleep simply delays the timer; resume inside the window evaluates, while
resume after 04:00 waits for the next local window. Startup reconciliation, not the scheduler, determines completion.

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

## Best-effort session/policy acceleration (Phase 8B)

The existing desktop LiveBus can now dispatch PHI-free `SESSION_INVALIDATED` and
`APPLICATION_POLICY_CHANGED` hints. Phase 8A revocations publish only after the audited SQL transaction commits;
publisher failure never rolls back SQL. A durable-session desktop filters tenant and public session identity,
coalesces concurrent hints, and performs a bounded authenticated session read before clearing only its server
bearer. Reconnect also revalidates that session and reloads the global PRODUCTION policy using the established
read service. JDBC login remains separate, compatibility-mode login does not require PubSub, unknown events are
ignored, and missed events remain correct because every bound API request validates `UserSessions`.

No schema, delivery audit, durable replay, session/device UI, geolocation, presence classification, updater
schedule, shutdown enforcement, or Phase 9 behavior is included.
## Desktop self-session management

The JavaFX Settings surface depends only on `UiRuntimeBridge.UserSessionManagement`; the desktop implementation owns
HTTP and the process-local Phase 7C bearer. The UI module does not depend on server classes or query `UserSessions`
through JDBC. Phase 8A authorization, current-`sid` binding, revocation, and security auditing remain authoritative.
Compatibility mode is represented by an absent capability, and async presentation is guarded by active tenant/user
identity. This boundary is self-service only; tenant-administrator session tooling remains Phase 10.

## Tenant-administrator session management UI (Phase 10)

Settings > Administration > Sessions is a lazy, admin-only desktop management window. JavaFX uses the
`UiRuntimeBridge.AdminSessionManagement` port; the desktop adapter calls only the bounded Phase 8A admin list and
revoke endpoints with its memory-only bound bearer. The server remains authoritative for active same-tenant admin
membership, tenant scope, filtering, revocation, one bounded sensitive-read audit per page query, and transactional
mutation audit. One Users join adds established same-tenant display name/email without per-row lookup.

The page requests 50 rows and supports server-side user, client-type, active-only, and since filters with compact
Previous/Next navigation. It reloads after successful revoke, while generation/identity/admin guards discard late
completions after close, logout, or user/tenant switch. The current administrator session has no row revoke and
points to ordinary logout. Phase 8A has no authoritative admin bulk-user revoke, so the UI does not simulate one.
Phase 8B remains the sole optional invalidation accelerator; the admin UI trusts the revoke response and its own
authoritative reload rather than PubSub delivery.

## Desktop update-policy presentation (Phase 11A)

The authenticated shell resolves global PRODUCTION `ApplicationPolicy` through the existing release-read boundary. Strict semantic versions and database server UTC determine current, recommended, required-before-deadline, and deadline-reached presentation. A 15-minute process cache advances only from a monotonic receipt anchor; stale or unavailable authority is informative and fail-open. Phase 8B invalidation/reconnect invokes the same coordinator. The manifest remains package authority and the established updater remains execution authority. This phase does not enforce, drain, log out, shut down, schedule, or administer policy.

Phase 13B is complete only as a feasibility/foundation phase. No production scheduler is wired, logged-out execution remains unsupported, and signing remains deferred.

## Windows logged-out automatic-update feasibility (Phase 13D)

Phase 13D is complete as a security design with the capability explicitly `UNSUPPORTED`. No current credentialless
principal can both preserve ownership of a per-user LocalAppData installation and obtain authenticated central
policy while logged out. SYSTEM/service execution would widen privilege and profile/ACL ambiguity, especially with
multiple per-user installs sharing one machine consent; unsigned installed executables make that materially worse.
The public manifest remains package authority only and cannot replace Phase 11 policy. No public endpoint, task,
service, helper, install registration, credential, SQL/API/schema change, reboot, force-kill, or macOS work was added.
Phase 13C remains the supported automatic path while Shale is running in an authenticated session. The immutable
`LoggedOutAutomaticUpdateSupport` contract prevents callers from claiming capability until a later explicit
architecture phase closes policy, signing, owner registration/path, attempt-store, and uninstall-lifecycle blockers.

## Windows background-update prerequisite closure (Phase 13E)

Phase 13E approves a narrow unauthenticated `GET /api/public/application-policy?channel=PRODUCTION` read backed by
the same Phase 11 service/SQL authority. It exposes only channel, revision, recommended/minimum-allowed versions,
deadline, and database server time. It adds production-optional/developer-disabled Authenticode release enforcement,
payload-lifecycle installed-version metadata, and pure validated owner/install path contracts. The present per-user,
non-elevated jpackage/WiX MSI cannot safely create and maintain an HKLM or ACL-hardened ProgramData registration, so
authoritative registration persistence, repair, upgrade identity preservation, and exact uninstall cleanup remain
explicit blockers rather than being delegated to the ordinary application process.

`LoggedOutAutomaticUpdateSupport = UNSUPPORTED` is unchanged. No execution principal, scheduled task, service,
SYSTEM execution, impersonation, logged-out updater, rollout flag, SQL migration, or tenant audit was added.

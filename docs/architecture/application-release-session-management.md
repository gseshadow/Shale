# Application Release and Session Management Architecture

**Status:** Phase 13C IN PROGRESS — in-session scheduler implemented; installed-Windows activation validation outstanding

**Last reviewed:** 2026-09-30

**Authority:** This document is the roadmap and current-state record for application releases, update
policy, installed desktop instances, authenticated sessions, revocation, and future client support.
Later work in this initiative must update the progress tracker and any decisions changed by verified
implementation evidence.

## 1. Purpose and scope

This initiative will evolve Shale's existing updater and authentication paths without replacing them
implicitly. It covers release metadata and policy, post-update announcements, installed desktop
instance telemetry, first-class user sessions for desktop/web/mobile clients, durable revocation,
version enforcement, update-attempt history, and a possible future unattended updater.

Phase 0 is documentation only. It introduces no SQL, API, authentication, updater, UI, heartbeat,
geolocation, RLS, or enforcement behavior. The baseline remains Java 21, JavaFX 21, Azure SQL, and the
existing Maven modules (`shale-core`, `shale-data`, `shale-ui`, `shale-desktop`, `shale-server`, and
`shale-updater`), plus the standalone React/Vite `shale-web` client.

### Documents and evidence reviewed

The inspection followed `architecture/codex-prompt-rules.md` and reviewed:

* `architecture/README.md`, `system-overview.md`, `database-schema.md`, `tenancy-and-rls.md`, and
  `live-update-architecture.md`;
* `docs/architecture/desktop-notification-polling.md`;
* the web/API migration, readiness, smoke-test, and deployment documents under `docs/`;
* Windows/macOS packaging and release scripts/guides;
* updater, desktop startup/login/runtime, server token/authentication, web token storage, PubSub,
  audit, user administration, settings, RLS migration, and related test sources.

Source code is authoritative for behavior described below. Older comments describing server auth as
"planned" are stale where later code now issues and validates bearer tokens.

## 2. Current-state findings

### 2.1 Application version detection and representation

* The root Maven version is currently `1.0.127`; release scripts treat it as the artifact and manifest
  version. Maven filtering writes it to `shale-ui`'s `/version.properties` as `app.version`.
* `AppVersionProvider.currentVersion()` resolves, in order: `APP_VERSION` system property, JAR
  implementation version, filtered `version.properties`, then `unknown`. `DesktopConfig` copies an
  environment/config `APP_VERSION` into the system property. There is no database version record.
* The current release convention is numeric `major.minor.build` (for example `1.0.127`). Preserve it.
  `VersionComparator` already compares numeric components rather than lexically, tolerates a leading
  `v`, trailing zero components, build metadata, and prerelease identifiers. The future policy service
  should reuse one shared strict value object derived from this comparator, reject malformed policy
  values at administration boundaries, and not add prerelease syntax merely because the current
  comparator happens to accept it.

### 2.2 Update discovery and mandatory notification

* Both desktop detection and the updater fetch the global public manifest at
  `https://shalestorage.z13.web.core.windows.net/shale-stable.json`. It contains version, channel,
  platform archive URLs/hashes, installer URL, notes, `mandatory`, and publication time.
* Login performs a synchronous-to-the-login-worker manifest check after credentials establish the
  desktop runtime context. Failure does **not** block entry: it reports an error and opens the main
  shell. A newer optional release may be skipped; declining a newer mandatory release exits.
* Once authenticated, `UpdatePollingService` checks every 15 minutes (the first scheduled check is
  after 15 minutes). Failures are logged and ignored. Results create or remove session-local
  notification-center entries. A mandatory result is critical/banner-capable, but no durable
  server policy or minimum-version rule exists.
* Activating an update notification opens the same update dialog. In an already-running client,
  declining that dialog is a no-op even when the manifest says mandatory. Thus today's `mandatory`
  behavior is strict only in the post-login prompt, not authoritative continuous enforcement.
* Update notification preferences can suppress the notification-center entry; they do not change the
  post-login mandatory dialog. There is no required-update deadline, minimum recommended/allowed
  version, access mode, or per-channel rollout behavior.

### 2.3 Existing concepts that must not be duplicated

* Before Phase 1A there were no `ApplicationReleases`, durable application policy, application instance,
  durable user session, update attempt, or per-user release-state tables. Phase 1A now supplies only the
  unused global release/release-item storage described below; every other listed concept remains absent.
* `dbo.UserPreferences` is a strict tenant/user key-value store. It is suitable for user UI choices,
  but not for global release authority, workstation identity, durable security sessions, or update
  history.
* `SessionContext` is an in-memory desktop holder, but the active desktop UI actually retains identity
  in `AppState` and arms `DesktopRuntimeSessionProvider`; it is not a server-side session entity.
* `ServerSessionContext` means request-resolved API principal, not a persisted session row.
* The API's `TokenRevocationStore` is a real per-token validation check but its production bean is an
  in-memory store. It is neither durable nor shared across server replicas and has no device/session
  listing model.

### 2.4 Settings and administration conventions

`SettingsController`/`settings.fxml` provide the existing card/pane administration shell. Admin
surfaces are gated from authenticated `AppState`; authoritative services/DAOs recheck tenant, actor,
active membership, and administrator status. Mutations use service ports/adapters and DAO-owned
transactions rather than UI SQL. Future release/session administration should follow those patterns,
but no UI belongs in the foundation phases.

## 3. Existing updater flow

### 3.1 Release production and packaging

1. `release.bat <version> <mandatory>` bumps Maven versions and builds the desktop and updater.
2. `build-shale-release.bat` creates a Windows `jpackage` app image, embeds the separately packaged
   `ShaleUpdater` image under the desktop payload, ZIPs the app image, and creates the MSI.
3. `update-manifest.bat` hashes the Windows ZIP, optionally carries macOS ZIP/hash metadata, and writes
   the global `shale-stable.json`. `release-and-publish.bat` publishes those static artifacts.
4. The MSI is installation/distribution output. The current in-app update consumes the ZIP, not the
   manifest's installer URL.

No inspected build or installer source creates a Windows Scheduled Task, Windows service, launchd
job, cron entry, or persistent helper. The updater is launched on demand only.

### 3.2 Update-now lifecycle

1. `UpdateFlowCoordinator` shows a non-closeable progress dialog and calls
   `DesktopUiUpdateLauncher.launchUpdater()`.
2. `DesktopUpdateLauncher` locates the installed application and writes local launcher/updater logs
   beneath the platform application-support directory.
3. Windows launches installed `app/updater/ShaleUpdater.exe` (with a legacy alternate location) with
   `--currentVersion` and `--installDir`; output is redirected locally. Windows Shale remains running
   until the updater later executes `taskkill /IM Shale.exe /F`.
4. macOS launches the packaged updater JAR using the bundled runtime through a detached shell helper;
   after successful handoff JavaFX calls `Platform.exit()`. The updater also attempts a best-effort
   external stop and uses a pre-armed detached relaunch helper because its own bundle is replaced.
5. The updater refetches the same manifest and rechecks the version. It downloads the platform ZIP to
   the system temp `ShaleUpdater` directory, optionally verifies SHA-256, rejects ZIP-slip paths, and
   extracts into a versioned staging directory.
6. It resolves the staged install root, stops Shale, arms the macOS helper, and copies a backup beside
   the install directory as `<install-name>-backup`. Backup and Windows overlay copy intentionally
   exclude updater directories so the executing updater is not overwritten.
7. Windows overlays staged files into the existing install directory. macOS deletes and replaces the
   `.app` bundle, restores executable permissions, and logs runtime helper status.
8. Windows starts `Shale.exe`. macOS reports relaunch as delegated to the pre-armed helper, which waits
   for the updater process to exit and expected versioned JARs to appear before starting the app.

### 3.3 Failure and recovery behavior

* Launcher failure keeps Shale open and shows a generic error. Update-check failures are non-blocking.
* Download, hash, extraction, stop, backup, or install exceptions are printed to the local redirected
  updater log. There is no server report, structured result file, retry orchestration, or automatic
  rollback from the backup.
* A relaunch failure after installation is explicitly treated as install success and instructs the
  operator via logs to reopen Shale manually. There is no UI that reads that result after restart.
* Windows uses forceful process termination; there is no coordinated unsaved-work handshake. This is
  why enforcement and unattended update work must precede termination with safe-work checks.
* Reuse and extend this updater. Inspection found no reason to create a second updater.

## 4. Existing desktop authentication flow

1. `MainApp` loads desktop configuration and directly constructs the JDBC `AuthServiceImpl` and
   `RuntimeSessionService`; `SceneRouter` builds one `AppState`, one runtime DB provider, and one
   `DesktopUiRuntimeBridge` for the process.
2. `LoginController` sends email/password through `DesktopUiAuthService` to `AuthServiceImpl` on a
   worker. `AuthServiceImpl` queries `dbo.Users` by exact email with `is_deleted = 0` using the auth
   datasource and verifies the bcrypt `password_hash`.
3. Success returns user/tenant and role flags. The controller stores these in process-memory
   `AppState`. The runtime bridge initializes SQL Server session context with tenant/user, arms the
   shared runtime connection provider, and starts the tenant/user PubSub connection.
4. Desktop does not receive an API bearer token, cookie, opaque session secret, or durable session ID.
   Its authenticated state lasts only in memory while this process and runtime bridge remain active.

### 4.1 Current termination and account-change semantics

* **Logout:** `SceneManager.logout()` invalidates/stops background producers, PubSub, update polling,
  notification work, and clears notifications; then `runtimeBridge.onLogout()` closes live transport
  and clears runtime database authority; then `AppState` identity/roles are cleared and login is shown.
  No server session row is ended because none exists.
* **Application close:** JavaFX shutdown closes SceneManager-owned workers. `MainApp.stop()` attempts to
  clear a newly constructed `SessionContext`, not the singleton, but current authentication authority
  is the SceneManager/AppState/runtime provider and the process exits. No durable end reason is saved.
* **Account disabled/removed:** a future login fails because authentication filters `is_deleted = 0`.
  An already-running desktop is not periodically re-authenticated; it can continue until a later DAO
  authorization check happens to reject that particular action or the process logs out/exits.
* **Password reset/change:** future credential checks use the new bcrypt hash. Existing desktop state
  and already-issued API tokens are not invalidated.
* **Access/role change:** many sensitive DAO operations re-read current admin/active/tenant state, so
  those operations reflect changes. `AppState` role flags and ordinary already-running UI affordances
  remain stale until login/restart; there is no general authorization-version invalidation.

The future `UserSession` must therefore be additive and client-neutral. A desktop `ApplicationInstance`
may refer to a desktop `UserSession`, but neither a process nor a machine defines session identity.

## 5. Existing API authentication flow and contracts

### 5.1 Authentication

* `POST /api/auth/login` reuses `AuthServicePort`/`AuthServiceImpl`, derives user and tenant only from
  the database result, and returns a signed HS256 bearer token plus a safe user profile.
* Tokens contain `jti`, user ID, tenant ID, optional email, issued-at, and expiry; default lifetime is
  eight hours. Protected requests resolve identity from the signed token. The request-scoped runtime
  DB provider initializes the established tenant/user SQL session context before DAO access.
* `GET /api/auth/me` resolves current identity and reloads a safe profile when available.
  `POST /api/auth/refresh` revokes the presented token ID and issues a replacement. There is no
  long-lived refresh token.
* `POST /api/auth/logout` stores only `jti` through `TokenRevocationStore`, never the raw token.
  `BearerTokenServerSessionResolver` rejects a revoked ID on every protected request.
* The only implementation is `InMemoryTokenRevocationStore`. Revocation is lost on restart and is not
  shared across replicas. It is genuine within one live process, but not sufficient durable remote
  revocation.
* `dev`/`local` may additionally trust explicit user/tenant headers. `prod`/`azure` use bearer tokens
  only. Those compatibility rules must remain unchanged until an explicit API migration.

### 5.2 Authorization and client contracts

API routes use the resolved principal and tenant-scoped runtime provider; tenant selection is not
accepted from normal client input. DAOs/services continue to enforce entity and role authorization.
The existing `/api/auth/login`, `/logout`, `/refresh`, `/me`, standard error envelope, bearer scheme,
and current case/contact/organization/task/notification routes are active contracts for `shale-web`
and future mobile clients. OpenAPI is published at `/v3/api-docs`.

The React client stores the access token in per-tab `sessionStorage`, restores it on refresh, sends it
as `Authorization: Bearer`, and clears it on logout/auth failure. This reduces persistence relative to
`localStorage` but remains readable by same-origin JavaScript; CSP/XSS hardening remains important.
Mobile secure storage and a browser HttpOnly-cookie alternative require separate threat-model and
contract decisions. No reusable raw session secret should ever be stored in SQL: durable sessions
should store a random opaque credential's hash (or bind existing `jti` to a session), rotation state,
and revocation metadata.

The API server is also the authoritative point that can observe the public client IP from the HTTP
request/proxy chain. It must trust forwarded headers only from configured Azure proxies. Desktop SQL
connections and workstation APIs cannot reliably determine public IP. Any later region/city lookup is
nullable, approximate metadata and never an authentication factor.

## 6. Existing Web PubSub capabilities

Desktop login negotiates an Azure Web PubSub connection and joins the tenant group. The current
implementation publishes PHI-free invalidation envelopes, dispatches entity/connectivity events, and
reconnects after transport loss. It has no durable replay and missed events are recovered through
authoritative reloads. The inspected server/web client does not provide equivalent browser PubSub
session control.

This transport is appropriate as an accelerator for `SESSION_INVALIDATED`, `APPLICATION_POLICY_CHANGED`, or
`INSTANCE_COMMAND` hints after authoritative state commits. It must never be the authority: a missed
message cannot resurrect a revoked session or permit a blocked version. The next authenticated
request/heartbeat must read authoritative state. Existing tenant group routing and dispatcher
architecture must be preserved; session-specific delivery will require verified user/session group
claims or safe tenant event filtering, not secrets in payloads.

## 7. Existing audit and RLS conventions

### 7.1 RLS and ownership

Tenant business data uses non-null `ShaleClientId`, explicit tenant predicates, runtime
`SESSION_CONTEXT`, and the existing `TenantFilter`/`sec.fn_FilterByTenant` policy. Global/tenant
overlay catalogs use the separately established global-or-tenant predicate only where global rows are
intentional.

Recommended ownership:

* `ApplicationReleases`, release items, and the default application policy are **global product
  control-plane data**, because today's manifest and updater are global. Do not put workstation tenant
  RLS on them. Their write path must be a tightly authorized operator/control-plane path, not a tenant
  admin path. Optional tenant/channel assignment can be added later as a separate tenant-owned mapping.
* `UserSessions`, `ApplicationInstances`, `ApplicationUpdateAttempts`, and `UserReleaseState` are
  strict tenant-owned data and must use non-null `ShaleClientId`, explicit equality checks, and the
  established strict RLS predicate. An unauthenticated startup instance may need a server-issued
  enrollment/instance credential, but tenant ownership must be established before tenant data writes.

### 7.2 Audit compatibility review

`dbo.AuditLog` is field/PHI-oriented. `dbo.EntityActionAuditLog` is the correct append-only semantic
action framework, but its constrained entity/action allowlists must be deliberately extended by the
phase introducing each new mutation. Authoritative mutations and audit append must share the DAO-owned
connection and transaction; failure rolls back both. Metadata must be short and allowlisted and must
exclude tokens, hashes, IP/location, release notes, machine names, DTOs, SQL, exception text, and logs.

Required future entity actions include release publication/change, policy minimum/deadline/access-mode
change, user self-revocation, administrator session/user revocation, and (if mutable administratively)
instance/update preference change. Ordinary heartbeat writes, policy reads, token validation, update
progress, and UI views should not emit one audit row each. Sensitive administrative session listings
need a scoped read-audit decision; IP/location must not be copied into general audit metadata. Schema
allowlist changes belong in the same narrow phase as each audited capability, never Phase 0.

## 8. Target architecture

### 8.1 Boundaries

1. **Release catalog and policy:** server-controlled, correctable without workstation changes, queried
   through shared service contracts. Static manifest remains the updater bootstrap/asset locator until
   a dedicated compatibility migration.
2. **Application instance:** one running installed desktop process, attached to a stable non-invasive
   machine UUID and optionally to the current session. It owns start/heartbeat/activity/end facts.
3. **User session:** one authenticated client grant for `DESKTOP`, `WEB`, `MOBILE`, or an explicitly
   supported future API client. It owns issuance, last use, expiry, revocation, and server-observed
   network metadata. It is not a workstation/process.
4. **Updater:** the existing platform-aware updater owns download, verification, staging, replacement,
   local detailed logs, and relaunch. It later reports sanitized attempt results through a narrow API.
5. **Clients:** desktop may initially use direct SQL for established business operations, while new
   control-plane/session operations should use authenticated server APIs so the server can observe IP,
   apply uniform revocation, and support web/mobile. Do not expose direct database access to web/mobile.

### 8.2 Stable machine identity and activity

Generate a random UUID; do not derive it from MAC, disk, CPU, motherboard, or other hardware. Because
the preference is workstation-wide and must survive user logout and in-place app-directory replacement,
the target is an installer-created, least-privilege writable data location outside the install tree and
outside a single user's profile: `%ProgramData%\Shale\machine-id` on Windows and
`/Library/Application Support/Shale/machine-id` on macOS. Phase 4 must validate installer ACLs and
multi-user requirements before choosing this over the existing per-user `AppPaths.appSupportDir`.
Uninstall retention/removal is an explicit operator decision.

`LastHeartbeatAt` records liveness only. `LastActivityAt` advances only from foreground user input
(keyboard, pointer, touch, or explicit app action), with throttling; background refresh, polling,
PubSub, timers, startup hydration, and updater checks never count as human activity.

### 8.3 Consolidated client-control heartbeat

Use one server API/service boundary rather than direct SQL or several polling loops. A desktop request
can carry instance ID, session ID/credential, semantic app/updater versions, instance-start time, and
throttled activity time. A minimal response can carry authoritative server time, explicit session
state, effective version policy, and a policy revision/next-check hint. Web/mobile use the same session
control service but do not invent desktop `ApplicationInstance` records unless they truly represent an
installed desktop process.

A likely cadence is 2-5 minutes while active, slower when idle, with jitter and backoff; security- or
policy-sensitive API requests independently validate session/version state. PubSub can prompt an
immediate heartbeat. There must be one lifecycle owner (analogous to `SceneManager`) and no activity
updates from background callbacks.

## 9. Entities and proposed contracts

Phase 1A verified and implemented the two release-catalog names below. Later entity names remain
provisional until their own phases verify live schema naming and deployment authority.

### 9.1 `ApplicationReleases` (global)

Implemented by `docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql` as global storage
with a `bigint` identity, nonnegative numeric version components, persisted computed canonical
`major.minor.build`, channel (`PRODUCTION`, `PILOT`, `DEVELOPMENT`), `DRAFT`/`PUBLISHED` lifecycle,
publication time/actor, short summary, creation/update provenance, and `RowVer`. Channel plus numeric
components is unique. Drafts cannot carry publication metadata; published rows require publication time.
There are no artifact fields because Phase 1A does not replace or duplicate the static manifest's asset
authority. Published identity/content should later be append-only or superseded at the service layer.
Publication/change will use transactional `EntityActionAuditLog` actions without release-note bodies.
No runtime reads this table; today's static manifest remains authoritative.

### 9.2 `ApplicationReleaseItems` (global)

Implemented as global children with a non-cascading release FK, unique per-release nonnegative sort
position, type (`FEATURE`, `FIX`, `IMPROVEMENT`, `IMPORTANT`, `LINK`, or `VIDEO`), title, short body,
optional generic resource URL, draft-editing active state, provenance, and `RowVer`. Resource URLs are
storage only. No RLS predicate or `ShaleClientId` exists on either catalog table. Future publication
audits use counts/types, not full text; a later read API may aggregate skipped releases.

### 9.3 `ApplicationPolicy` (global revisioned policy)

Implemented by `docs/sql/2026-09-28_application_policy_foundation_phase1b.sql` as one append-oriented row
per channel revision. Positive `RevisionNumber` is unique within `ReleaseChannel`; a filtered unique index
allows at most one `IsCurrent = 1` row per channel. A future correction transaction will supersede the old
row and insert the next revision, retaining the prior policy. Publication/creation and supersession timestamps
and nullable actor FKs provide provenance, while `RowVer` enables future optimistic concurrency. Actor identity
does not grant global authority; the dedicated control-plane authorization boundary remains future work.

Latest, minimum recommended, and minimum allowed are nullable, non-cascading FKs to Phase 1A release rows.
SQL enforces FK existence, channel/access-mode vocabulary, positive revision, unique revision/current state,
and internally consistent supersession timing. Cross-table channel equality, published-release eligibility,
and numeric `minimumAllowed <= minimumRecommended <= latest` ordering cannot be expressed by a SQL Server
CHECK; the future mutation service must enforce them transactionally from numeric release components before a
revision becomes current. Phase 1B intentionally adds no trigger or lexical version comparison.

The optional deadline is UTC `datetime2(7)`; future clients use authoritative server time. `NORMAL` is the only
currently meaningful access mode; `READ_ONLY`, `MAINTENANCE`, and `BLOCKED` are reserved and wholly unenforced.
The table is global, has no `ShaleClientId`, tenant/workstation targeting, or RLS, and starts empty. No runtime
reads it, and there is no manifest synchronization: `shale-stable.json` remains authoritative. Policy mutation
and its transactional, sanitized semantic audit actions are deferred together; Phase 1B changes no audit
allowlist.

### 9.4 `UserSessions` (strict tenant-owned)

Purpose: durable client-neutral authenticated grant. Key fields: ID, `ShaleClientId`, user ID, client
type (`DESKTOP`, `WEB`, `MOBILE`, later explicit API type), created/authenticated/last-seen/activity/
expires/revoked timestamps, revocation reason/actor, credential hash or bound token ID, rotation family,
server-observed IP, nullable region/city, client version and safe device label. A desktop session may
be referenced by multiple sequential application instances only if product semantics deliberately
allow resume; it is never the instance itself. Strict RLS; self-list/revoke and admin-list/revoke need
separate authorization. Retain revocation/security metadata for a defined security period, then
minimize/anonymize network metadata. Contracts must migrate existing JWTs deliberately.

Small sensible session termination reasons: `USER_LOGOUT`, `REMOTE_REVOKE`, `ADMIN_REVOKE`,
`ACCOUNT_DISABLED`, `CREDENTIAL_CHANGED`, `EXPIRED`. Do not put `APPLICATION_EXIT`, `UPDATE_RESTART`,
or abnormal process loss here; those are instance end reasons.

### 9.5 `ApplicationInstances` (strict tenant-owned after enrollment)

Purpose: running installed desktop client. Key fields: ID, tenant, stable machine UUID, machine name,
app/updater/OS versions, start, heartbeat, human activity, optional session/user reference, end time and
reason. End reasons: `NORMAL_EXIT`, `USER_LOGOUT` (instance may actually continue at login and need not
end), `UPDATE_RESTART`, `REMOTE_CONTROLLED_EXIT`, and `TIMEOUT_OR_ABNORMAL` derived after staleness.
Prefer a small set based on observable facts; never claim a clean exit when only heartbeat expiry is
known. Strict RLS. Retain recent operational detail, then aggregate/anonymize according to policy.

### 9.6 `ApplicationUpdateAttempts` (strict tenant-owned)

Purpose: append-only update history, not one mutable result column. Key fields: ID, machine and optional
originating instance, from/to semantic versions, channel/release, started/completed times, result,
sanitized failure code/summary, updater version, correlation ID. Lifecycle begins before handoff when
online and is completed after updater/relaunch reporting; abandoned attempts become timed out. Store no
large logs, stack traces, URLs with secrets, or raw exceptions. Detailed logs remain local. Operational
retention can be shorter than releases/security audits.

### 9.7 `UserReleaseState` (strict tenant-owned)

Purpose: per-user/client-family announcement progress. Key fields: tenant, user, client type or
experience scope, last-seen release/version/time, row version. It does not control update eligibility.
Advance only after the aggregate What's New experience is successfully acknowledged, and never past
unseen releases. Strict RLS. Retain with the user or according to user-data retention rules.

### 9.8 Read/control contracts

Additive contracts should eventually include: effective release policy; releases/items since a
version; heartbeat/client-control response; self session list/revoke/revoke-others; authorized admin
session list/revoke; instance summaries; and update-attempt start/complete. DTOs belong in shared
contract/core boundaries with server adapters; JavaFX/React models must not leak into persistence.

## 10. Security model

* Authentication proves credentials; session issuance creates a durable, tenant-bound grant; every
  protected request validates signature/opaque secret, expiry, session status, user membership, and
  appropriate authorization. A server-explicit revocation is authoritative.
* Persist only hashes of random opaque session/refresh credentials, or non-secret JWT IDs plus durable
  status. Never store bearer/access tokens or reusable raw secrets in SQL or audit metadata.
* Remote logout commits revocation first and audits it in the same transaction. PubSub notification is
  best effort after commit. Missing push only delays client UX; the next request/heartbeat is rejected.
* Account disable/removal should transactionally revoke active sessions (or advance a user security
  epoch checked by all requests). Credential change policy—revoke all sessions or all except the
  initiating session—must be explicit. Role change should refresh authorization from authoritative
  data or invalidate cached claims; a stale client flag must not grant access.
* Session list output masks IP as appropriate, treats city/region as approximate, and exposes only
  minimum device metadata. Location changes never invalidate sessions automatically.
* The desktop's direct-DB authentication cannot provide durable server revocation as-is. Adding a
  desktop control-plane session credential/API exchange is therefore a deliberate future auth contract
  migration with compatibility overlap—not something hidden inside heartbeat work.

## 11. Update-policy model

Evaluate versions numerically with the shared strict `major.minor.build` parser:

* `installed >= latest`: current (a newer development build is not lexically mishandled);
* `installed >= minimumRecommended`: supported/current enough;
* `minimumAllowed <= installed < minimumRecommended`: update required/recommended according to policy
  and deadline, but still permitted during the grace period;
* `installed < minimumAllowed`, or deadline passed for a version made disallowed: blocked.

`latest`, `minimumRecommended`, and `minimumAllowed` are policy pointers/values, not per-release
`IsMandatory` truth. The legacy manifest `mandatory` flag remains compatibility input until a dedicated
cutover. Channel is a reserved axis (`PRODUCTION`, later `PILOT`/`DEVELOPMENT`); do not implement rollout
now. Likewise reserve access-mode vocabulary (`NORMAL`, potentially `READ_ONLY`, `MAINTENANCE`,
`BLOCKED`) but do not claim read-only safety until every mutation path can enforce it.

Startup/login can block strictly after an authoritative policy response. For a running client newly
made prohibited: stop starting new work, visibly enter update-required state, allow safe completion and
save of already-open work where practical, then require update. Never force-kill a process with unsaved
work. Emergency `BLOCKED` may deny server mutations immediately but still needs explicit save/recovery
product rules.

## 12. Failure semantics

| Condition | Required behavior |
| --- | --- |
| Server explicitly returns session revoked/expired/disabled | Fail closed for protected work, clear local credential/runtime authority, preserve recoverable local UI state, and return to authentication. |
| Server explicitly returns installed version blocked | Fail closed for new protected/new-work operations; at startup block entry; in-process use safe-drain behavior and update prompt. |
| Valid cached policy says a deadline has passed | Enforce only within the approved cache/clock-skew rules; reconnect promptly for a correctable newer revision. |
| Policy/session endpoint times out, DNS fails, Azure/SQL is transiently unavailable | Do not translate uncertainty into revocation or a newly blocked version. Keep existing behavior within a bounded grace window, show degraded connectivity, retry with backoff/jitter, and let existing operation-specific DB failures behave normally. |
| No policy has ever been retrieved on a fresh install | Preserve current login/update-check fail-open behavior until the enforcement phase explicitly defines a safe bootstrap rule. Do not lock out the firm due solely to control-plane outage. |
| PubSub event is missed | No security effect; the next request/heartbeat consults durable authority. |
| Updater install fails | Keep structured local result and backup; report a sanitized failed attempt when possible; never mark the target release seen merely because handoff began. |

Clients should distinguish explicit authoritative negative responses from unavailable/unknown state.
Server time in heartbeat responses limits workstation clock dependence. Policy caches require revision,
retrieval/expiry times, and a deliberately chosen grace period.

## 13. API compatibility constraints

1. Existing desktop JDBC authentication must continue to work until an approved desktop-session
   migration provides overlap, rollback, and deployment sequencing.
2. Existing API authentication and contracts must not be silently broken. Current bearer tokens,
   dev/local header behavior, auth routes, error shapes, and business endpoints remain compatible.
3. Session architecture must support `DESKTOP`, `WEB`, and `MOBILE`; it must not encode JavaFX process
   assumptions into `UserSession`.
4. Any token/session contract migration—durable JWT session binding, refresh credential, cookie option,
   or desktop exchange—must be independently designed, versioned, tested, and documented.
5. Existing updater/static manifest and installation paths remain authoritative until their dedicated
   compatibility phase. The server policy must not strand old clients that only understand the JSON
   manifest.
6. RLS, audit, historical rows, Java/Maven boundaries, and public OpenAPI behavior remain preserved.

## 14. Phased roadmap

Every phase is independently reviewable and must update this document. “Likely files” are routing
hints, not permission for unrelated refactoring.

### Phase 0 — Inspection and architecture (**COMPLETE**)

* **Goal:** establish verified baseline and roadmap.
* **In scope:** this document and static inspection of code/tests/docs.
* **Non-goals:** all production behavior, schema, API, UI, auth, updater, and infrastructure changes.
* **Likely files:** this document only.
* **Schema/API impact:** none/none.
* **Verification:** documentation checks, diff review, relevant existing focused tests, critical suite.
* **Dependencies:** none.
* **Risks:** stale operational infrastructure not represented in source; explicitly recorded as an open
  decision rather than guessed.

### Phase 1A — Global release catalog schema foundation

* **Goal:** add release and release-item storage only.
* **In scope:** forward-only migration, keys/indexes/version constraints, publication lifecycle fields,
  verification SQL, schema docs, migration-contract tests.
* **Non-goals:** policy, APIs, seeds/publication, UI, manifest replacement.
* **Likely files:** `docs/sql/`, verification SQL, `architecture/database-schema.md`, data tests.
* **Schema/API impact:** additive global tables; no API impact.
* **Verification:** migration idempotence, malformed-version rejection, FK/index checks, no RLS attached
  accidentally, existing migration contracts.
* **Dependencies:** operator confirms global control-plane database/authorization boundary.
* **Risks:** putting global product data behind tenant RLS or allowing tenant admins to mutate it.
* **Result:** implemented on 2026-09-28; Maven verification remains blocked by the documented external HTTP 403. Added the two empty global catalog tables, closed rerun validation,
  read-only verification SQL, and focused migration contracts. No runtime, updater, policy, or auth path
  consumes the schema.

### Phase 1B — Global application-policy schema foundation

* **Goal:** add revisioned, independently correctable policy storage.
* **In scope:** latest/recommended/allowed values, deadline, reserved channel/access mode, concurrency and
  history/audit design; verification tests.
* **Non-goals:** enforcement, manifest synchronization, policy UI/API.
* **Likely files:** same schema/docs/test areas as 1A.
* **Schema/API impact:** one additive global policy/history model; none.
* **Verification:** ordering invariants, correction/supersession, concurrency, deployment rollback path.
* **Dependencies:** 1A and operator authority decision.
* **Risks:** an uncorrectable singleton or invalid minimum version.
* **Result:** implemented on 2026-09-28; Maven verification remains blocked by the documented external HTTP 403. Added empty global `ApplicationPolicy` revision history with one-current-
  per-channel uniqueness, release FKs, lifecycle/vocabulary constraints, optimistic concurrency, closed rerun
  validation, read-only verification, focused contracts, and no runtime or audit mutation path. Cross-release
  channel/publication/version ordering is explicitly reserved for the future transactional mutation service.

### Phase 2A — Shared semantic-version and read service boundary

* **Goal:** one strict `major.minor.build` model and read-only release/policy ports.
* **In scope:** core DTO/value object, data adapters/DAOs, delegation tests.
* **Non-goals:** HTTP, UI, updater, writes, enforcement.
* **Likely files:** `shale-core`, `shale-data`, their focused tests.
* **Schema/API impact:** none beyond Phase 1 reads; no external API.
* **Verification:** numeric ordering (`1.0.130 > 1.0.99`), malformed inputs, adapter delegation, empty policy.
* **Dependencies:** 1A-1B.
* **Risks:** behavior drift from updater comparator; resolve with shared vectors before replacement.
* **Result:** complete on 2026-09-28. `com.shale.core.model.SemanticVersion` is the single strict internal
  `major.minor.build` value: it accepts exactly three nonnegative, base-10 Java `int` components in canonical
  form (no trimming, prefixes, leading zeroes, prerelease/build suffixes, or overflow) and compares numeric
  tuples. The deliberately more permissive updater `VersionComparator` remains unchanged and in runtime use;
  canonical production vectors prove that both orderings agree.
* **Read boundary:** `ApplicationReleaseReadServicePort` exposes only current-policy, published-releases-after,
  and ordered-release-item reads using immutable core views and closed vocabularies. The implementation is
  `ApplicationReleaseReadServiceAdapter` over `ApplicationReleaseReadDao`; it has no runtime consumer yet.
  An empty current-policy table returns `Optional.empty()` rather than a synthesized policy.
* **Read integrity:** DAO SQL is global (no tenant id/session-context predicate), selects only `IsCurrent=1`
  policy and `PUBLISHED` release history, compares/orders the three numeric version columns, and orders items by
  `SortOrder, Id`. Effective-policy joins reject unknown vocabulary, wrong-channel or draft references, multiple
  current rows, and every comparable violation of `minimumAllowed <= minimumRecommended <= latest`. Nullable
  references have no additional presence dependency: every available pair is validated. Reads never repair data.
* **Audit review:** these are global, non-PHI product-control reads and introduce no mutation, so no PHI or
  entity-action audit event is appropriate. A later administration phase must separately design mutation audit.
* **Verification:** the previously blocked dependency resolution was cleared and the repository-local critical
  reactor command `mvn test` passed for the completed Phase 2A implementation.

### Phase 2B — Read-only release/policy HTTP contracts

* **Goal:** expose safe versioned read contracts for future clients.
* **In scope:** authenticated policy and releases-since endpoints, OpenAPI, compatibility DTOs, caching.
* **Non-goals:** administration, enforcement, heartbeat, changing auth.
* **Likely files:** `shale-server`, shared ports, server tests/docs.
* **Schema/API impact:** read-only queries; additive endpoints.
* **Verification:** bearer/RLS boundaries where applicable, safe errors, OpenAPI, old endpoints unchanged.
* **Dependencies:** 2A.
* **Risks:** exposing draft releases or coupling policy to tenant input.
* **Implementation:** `GET /api/application-releases/policy/current?channel=PRODUCTION` returns the current
  effective policy, or `204 No Content` when none is configured. `GET /api/application-releases?channel=PRODUCTION&after=1.0.127`
  returns published releases strictly after the canonical lower bound in ascending numeric version order, with
  their active items embedded in DAO-defined `SortOrder, Id` order. Both routes require the existing bearer
  principal in production/Azure and retain the existing dev/local header compatibility; caller tenant identity
  is resolved for authentication but never filters or selects this global control-plane data.
* **Contract boundaries:** external DTOs omit row versions, publication internals, actor/audit data, and SQL
  diagnostics. Closed vocabularies serialize as their stable uppercase enum names. Resource URLs are returned
  only as nullable inert metadata. Invalid channel/version input uses the standard `400` envelope; read-boundary
  invariant failures and unexpected failures use the sanitized standard `500` envelope.
* **Caching and consumers:** successful and empty responses use `Cache-Control: private, max-age=60`; no ETag,
  server-side cache, Redis, runtime consumer, policy evaluation, enforcement, acknowledgement, session,
  heartbeat, PubSub, client, updater, or manifest behavior was introduced.
* **Verification:** focused controller/auth/error/cache and OpenAPI coverage, Phase 2A regression coverage,
  service wiring coverage, and the repository-level `mvn test` passed. Phase 2B is complete; Phase 3A has not
  started.

### Phase 3A — User release-state foundation

* **Goal:** store per-user acknowledgement without UI.
* **In scope:** strict tenant table/RLS, service operations, aggregate-since queries, transactional advance.
* **Non-goals:** popup, rich media, updater changes.
* **Likely files:** migrations, core/data ports, RLS verification, tests.
* **Schema/API impact:** additive tenant table; additive internal/API acknowledgement contract.
* **Verification:** cross-tenant denial, monotonic acknowledgement, skipped releases, concurrent writes.
* **Dependencies:** 2B.
* **Risks:** marking unseen releases seen or scoping state to a workstation instead of user experience.

Implementation uses strict tenant-owned `dbo.UserReleaseState`, keyed uniquely by
`(ShaleClientId, UserId, ClientType, ReleaseChannel)`. Channel is intentionally part of the logical key: a user
may participate in production, pilot, or development streams without one stream overwriting another. Client
type is closed to `DESKTOP`, `WEB`, and `MOBILE`. The nullable release FK preserves an honest schema concept
for “no acknowledgement,” but reads do not create empty rows and the Phase 3A service creates a row only for a
real acknowledgement of a published release.

`UserReleaseStateServicePort` and its data adapter expose current-actor `findCurrent` and `acknowledge`
operations. The DAO validates tenant and principal session context plus active same-tenant user membership,
then owns one transaction that locks the logical scope, resolves the canonical release, validates publication
and channel, compares `SemanticVersion` numerically, and inserts or updates. Skipped releases are valid;
backwards movement is rejected; same-release acknowledgement is idempotent and does not update timestamps or
`RowVer`. Updates require the expected `RowVer`; stale writes fail, and unique-key conflict handling makes a
concurrent first create fail clearly rather than duplicate state. No installed-version upper bound exists.

The table uses `TenantFilter` with `sec.fn_FilterByTenant(ShaleClientId)` as filter and insert/update block
predicates, tenant-qualified user ownership, non-cascading release/user FKs, and no seed. Ordinary release-note
acknowledgement deliberately produces no `EntityActionAuditLog` row: it is routine nonsensitive UI state, not
an administrative or sensitive domain mutation. No HTTP endpoint, UI/runtime consumer, updater, policy,
instance, session, heartbeat, PubSub, or geolocation behavior is included.

**Verification history (2026-09-28):** the implementation run initially encountered Maven Central HTTP 403 and
had no live SQL Server. Those blockers were subsequently cleared before the Phase 3B initiative baseline; Phase
3A is complete. This historical note does not weaken its monotonic service contract or reopen its scope.

### Phase 3B — Desktop What's New experience

* **Goal:** one post-update aggregate dialog.
* **In scope:** simple feature/fix/improvement display after verified version transition; acknowledge on
  successful dismissal.
* **Non-goals:** rich content rendering, one dialog per release, update enforcement.
* **Likely files:** `shale-ui`, desktop adapter, focused JavaFX contract tests.
* **Schema/API impact:** uses 3A; no breaking API.
* **Verification:** multi-version aggregation, first-run rule, failure does not advance, visual inspection.
* **Dependencies:** 3A.
* **Risks:** popup loops and confusing clean installs with completed updates.

Phase 3B is implemented in the desktop JavaFX composition through `WhatsNewCoordinator`,
`WhatsNewPresentation`, and `WhatsNewDialog`. `SceneManager.showMain()` schedules the coordinator only after
the authenticated main scene and initial My Shale route are installed. The coordinator uses the existing
direct desktop `ApplicationReleaseReadServicePort` and `UserReleaseStateServicePort` adapters with the current
tenant/user and the fixed `DESKTOP` / `PRODUCTION` announcement scope. It resolves the running version only
through `AppVersionProvider.currentVersion()` and parses that value with strict `SemanticVersion`; an unknown
or malformed value skips the experience for that launch without a fallback version.

For an existing state, selection is every published production release strictly newer than the acknowledged
semantic version and no newer than the running version. Missing catalog versions are valid. Releases are
grouped in semantic ascending order, and active items retain `SortOrder ASC, Id ASC`. For no state, only the
newest published release at or below the running version is evaluated, preventing a historical catalog dump.
If it has active items, that one release is shown and acknowledged after dismissal. If it has no active items,
it is silently acknowledged after the interval is successfully evaluated. With existing state, a wholly empty
interval is likewise silently advanced to its highest applicable release; where visible and empty releases are
mixed, the dialog and acknowledgement stop at the highest release that actually contributes visible content,
and a later launch can silently advance any remaining empty tail. No applicable catalog row creates no state.

The UI is one resizable, window-modal, vertically scrollable Shale secondary window. It shows release-version
groups, nonduplicated stored summaries, and simple title/body treatments for `FEATURE`, `FIX`, `IMPROVEMENT`,
and emphasized-but-dismissible `IMPORTANT` items. `LINK` and `VIDEO` remain safe text-only title/body/resource
presentation: no WebView, embedding, autoplay, fetch, or download occurs. `ThemeManager` supplies the same
token-based stylesheet in Light and Dark, and the single “Got it” action uses the shared semantic Primary
control. Both that action and the ordinary custom-window close affordance count as dismissal. Fetching,
constructing, or displaying the window does not acknowledge; the target is the highest published release
actually represented by visible content.

Catalog/state/version failures are logged with sanitized context and abandon only this non-critical experience.
Acknowledgement runs off the JavaFX thread; failure closes normally and does not reopen during the process
login. A failed optimistic write reloads durable state and is accepted when another client already advanced to
the target or later version; otherwise it is deferred to a future launch. A per-authenticated-context in-memory
guard prevents route/refresh duplication and resets during authoritative logout/session teardown. This adds no
SQL migration or audit event: ordinary announcement acknowledgement remains the Phase 3A nonsensitive UI-state
mutation. It has no updater/manifest, update-policy/enforcement, instance, session, heartbeat, PubSub,
geolocation, administration, web, or mobile coupling.

**Verification status (2026-09-28):** focused coordinator, presentation, theme, and SceneManager wiring tests
cover interval bounds, skipped releases, first-run/empty catalog, dismissal timing, no-content advancement,
optimistic-concurrency recovery, duplicate suppression, and startup failure paths. The repository-level
`mvn test` subsequently passed after the earlier Maven Central outage; Phase 3B is **COMPLETE**.

### Phase 4A — Stable machine identity

* **Goal:** create/persist a non-invasive workstation UUID.
* **In scope:** platform storage abstraction, installer ACL/location validation, read/create concurrency.
* **Non-goals:** server reporting, hardware fingerprint, settings UI.
* **Likely files:** `shale-core` platform paths, `shale-desktop`, packaging scripts/tests/docs.
* **Schema/API impact:** none/none.
* **Verification:** persistence across users/restarts/in-place update, atomic creation, permissions, uninstall.
* **Dependencies:** machine-ID location decision.
* **Risks:** per-user duplication or unwritable system directory.

Phase 4A is implemented by the platform-neutral `MachineIdentityStore` boundary, filesystem-backed
`FileMachineIdentityStore`, lazy `MachineIdentityProvider`, and explicit `MachineIdentityResult`. Desktop
composition exposes the memoized `MainApp.machineIdentity()` accessor independently of authentication;
it performs no I/O unless requested and is not connected to Phase 3B release acknowledgement. The result is
either a stable `UUID` or a typed `PLATFORM_STORAGE_UNAVAILABLE` / `STORAGE_ACCESS_FAILED` failure. Callers are
never given an ephemeral substitute.

The exact identity paths are `%ProgramData%\Shale\machine-id` on Windows and
`/Library/Application Support/Shale/machine-id` on macOS. `AppPaths.machineDataDir` owns path resolution;
Windows requires the real `ProgramData` environment value and never falls back to a profile, install directory,
temp directory, roaming data, or Local AppData. Unsupported platforms fail explicitly; Phase 4A does not
invent Linux storage behavior. The file contains one canonical lowercase UUID generated locally with
`UUID.randomUUID()`, optionally followed by a newline, and no version, timestamp, hostname, operating-system,
tenant, user, session, network, or location data.

Initialization creates the parent directory when permitted, then serializes contenders with an in-JVM lock
and an OS file lock on `machine-id.lock`. Under that lock it re-reads the winner, or writes and forces a uniquely
named sibling temporary file before an atomic same-directory move to `machine-id`. Atomic-move support is
required rather than silently degrading to a partial-write risk. The persisted file is re-read and validated
before success is returned. Concurrent provider/process callers therefore converge on one file and UUID; failed
temporary writes/moves clean up the temporary file and return an explicit unavailable result.

Existing content is trimmed only to allow a trailing newline and must round-trip through Java `UUID` to its
canonical lowercase text. Empty, truncated, non-UUID, or noncanonical content is not accepted. While holding
the initialization lock, the store atomically preserves it as `machine-id.corrupt-<random UUID>`, creates a new
random identity, and logs a warning without logging either file content or machine UUID. Read, directory,
lock, write, sync, move, and permission failures are logged in sanitized form, do not crash desktop startup or
login, and remain explicit in `MachineIdentityResult`.

The identity is workstation-scoped and takes no Shale tenant/user/login/session input. Switching Shale users
therefore cannot change it. On Windows, the current supported MSI is deliberately per-user and cannot safely
provision elevated `%ProgramData%` ACLs. Managed shared workstations should pre-create only the Shale directory
with administrator/System control and read/write access for users authorized to run Shale. First-launch
creation is used where inherited platform permissions allow it; cross-Windows-account sharing is available
where that directory ACL permits, and otherwise the second account receives explicit unavailability rather
than a second identity. The first-pass unsigned macOS package likewise requires administrator provisioning of
the machine-wide Shale support directory when ordinary users lack access. No privileged helper or broad ACL is
introduced in this phase.

Both locations are outside the Windows install payload and macOS `.app` bundle. Existing updater replacement
remains scoped to its explicit install directory, while the MSI and macOS packaging scripts do not own the
identity file. Normal overlay/bundle upgrades and versioned payload cleanup therefore leave it untouched.
Ordinary uninstall/reinstall intentionally retains machine identity; a future explicit full-data removal may
define a separate deletion contract, but Phase 4A adds no cleanup UI or installer action.

This UUID is an identifier, not a secret, credential, authorization factor, or proof of device trust. Shale
does not collect or derive MAC addresses, Windows MachineGuid/SIDs, hostnames, serial numbers, BIOS/motherboard,
TPM, disk, or CPU identifiers. No encryption is added merely to obscure the UUID. Phase 4A has no database
migration, API/server reporting, application-instance row, version reporting, heartbeat/activity, session,
remote logout, enforcement, PubSub, geolocation, device name, Settings UI, or updater scheduling. Local identity
creation/recovery is not an established sensitive read or domain/administrative mutation, so the audit review
requires no database audit event or schema change.

Focused tests cover paths, creation/persistence/restart, canonical parsing/newlines, malformed/empty/truncated
recovery, parent creation, thread/provider convergence, temporary-write and atomic-move failures, simulated
permission failure, explicit non-ephemeral results, authentication independence, and updater/MSI/macOS external-
state contracts. **Phase 4A is COMPLETE**; its stable random UUID contract is the machine identity consumed by
Phase 4B.

### Phase 4B — Application-instance persistence and authenticated enrollment/end contracts

**Status: COMPLETE (2026-09-28).** Phase 4B implementation and its required Maven and live SQL/RLS
verification were completed before Phase 5A began.

`dbo.ApplicationInstances` is strict tenant-owned lifecycle history. Its `bigint IDENTITY` primary key makes
every launch distinct, including concurrent/reopened launches on the same machine or by the same user. It has
non-null `ShaleClientId` and `UserId`, a trusted non-cascading tenant-qualified user FK, nullable
`uniqueidentifier MachineId`, closed `DESKTOP`/`WEB`/`MOBILE` `ClientType`, nonnegative numeric
`MajorVersion`/`MinorVersion`/`BuildVersion`, database-defaulted UTC `StartedAt`, nullable `EndedAt`, UTC
created/updated metadata, and `RowVer`. DESKTOP requires a machine UUID; WEB/MOBILE prohibit one rather than
inventing device semantics. Active means only `EndedAt IS NULL`; end means `EndedAt IS NOT NULL` and cannot
precede start. There is no single-active-instance constraint. Minimal filtered indexes support tenant/user
active and tenant/machine history reads.

The enabled `TenantFilter` has exactly a strict `sec.fn_FilterByTenant(ShaleClientId)` FILTER predicate and
AFTER INSERT/AFTER UPDATE block predicates for this table; it never uses overlay/global filtering. The
migration is forward-only, rerunnable, additive, seeds no rows, and preserves history with `NO ACTION` FKs.
The read-only catalog verifier emits independent `CheckName | FindingCount` results. A separate live script
uses a disposable non-dbo principal for same-tenant insert, filtered cross-tenant visibility, and an isolated
expected SQL Server 33504 cross-tenant insert failure.

`ApplicationInstanceServicePort`, `ApplicationInstanceDao`, and `ApplicationInstanceServiceAdapter` form the
narrow current-principal boundary. Enrollment accepts only machine UUID, `ClientType`, and strict Phase 2A
`SemanticVersion`; authenticated tenant/user IDs are resolved by the server principal (or the already-stamped
desktop runtime context), verified against the active tenant-qualified user, and never trusted from an HTTP
body. The DAO relies on database `SYSUTCDATETIME()` defaults. End targets the authenticated tenant and owning
user, uses `COALESCE(EndedAt, SYSUTCDATETIME())`, preserves the first end time, returns the existing ended row
on repeats, and never deletes it.

Authenticated additive HTTP contracts are `POST /api/application-instances` and
`POST /api/application-instances/{id}/end`. Enrollment accepts `{machineId, clientType, applicationVersion}`
and returns only `{id, machineId, clientType, applicationVersion, startedAt, endedAt}`. Current Phase 4B
runtime accepts DESKTOP only, validates canonical UUID and `major.minor.build`, and documents bearer auth and
safe 400/401/404/500 envelopes in OpenAPI. End is self/owner-only, idempotent, and intentionally provides no
list, admin query, or remote termination contract.

Desktop resolves the Phase 4A stable machine result at startup, but enrolls only after authentication has
initialized tenant/user database session context. It parses the authoritative `AppVersionProvider.currentVersion()`
through `SemanticVersion`, registers once, and holds the returned view in process-local
`CurrentApplicationInstance`. Unavailable machine identity or invalid/unknown version skips enrollment;
enrollment failures are sanitized and fail open without a fake ID or retry loop. Logout best-effort ends then
clears the current instance before clearing runtime identity; the next login creates a distinct row. Normal
SceneManager shutdown performs the same best-effort end. End failures never block logout/shutdown. Crash,
kill, power loss, and outage may leave `EndedAt` NULL; this phase performs no recovery or stale inference.

Audit compatibility review: routine enrollment/end is high-volume operational lifecycle state, not a semantic
entity mutation or sensitive read. `ApplicationInstances` itself is authoritative, so Phase 4B intentionally
adds no `EntityActionAuditLog` vocabulary/event and no audit migration. No PHI or hostname, OS account,
hardware identifier, IP, location, or arbitrary exception text is captured.

Phase 4B explicitly does **not** add heartbeat/last-seen/activity columns or traffic, durable `UserSession`,
auth credentials/grants, remote logout, policy/version enforcement, PubSub, device/session UI, geolocation,
updater reporting, scheduling, or unattended-update behavior. `ApplicationInstance` is only a registered
client-process launch; it is not a bearer/refresh token, authorization grant, or durable user session.

### Phase 5A — Local foreground human-activity observation foundation (**COMPLETE**)

`ForegroundHumanActivityObserver` is the one process-local observer for the current authenticated desktop
runtime. `SceneManager.showMain()` installs it only after the authenticated main scene has been placed on the
primary `Stage`; login credential entry, launcher/updater windows, and unauthenticated surfaces are outside its
scope. `stopSessionOwnedWork()` detaches and resets it before logout changes runtime identity, so a subsequent
user starts with no activity timestamp. `SceneManager.shutdown()` also detaches it. Reinstalling for the same
shell is idempotent, and the stable primary `Scene` survives route/root replacement, avoiding per-route or
per-controller registrations.

The explicit qualifying JavaFX event types are `KeyEvent.KEY_PRESSED`, `MouseEvent.MOUSE_PRESSED`,
`ScrollEvent.SCROLL`, and `TouchEvent.TOUCH_PRESSED`. Window-level filters observe but never consume these
events. Every observation requires the event's Shale window to be both showing and focused at dispatch time;
stale scene state is insufficient. Continuous mouse movement, mouse hover, key release/typed payloads, action
or focus events, animation/layout/rendering, startup, timers, database refresh, polling, PubSub, network
responses, updater activity, and programmatic/service mutations do not qualify. Updating one in-memory atomic
reference is intentionally not throttled: no mouse-move stream is observed and there is no I/O to coalesce.

The primary authenticated stage and any currently showing JavaFX `Stage` or `PopupWindow` whose owner chain
leads to it form the authenticated window context. Thus input in Shale-owned modal dialogs and popups counts,
while unrelated top-level windows do not. The current desktop architecture has one authenticated primary stage;
there is no independent second authenticated top-level shell. A JavaFX window-list listener attaches owned
windows exactly once and retains the exact event-handler references used for removal.

The only observation is `Optional<Instant> lastHumanActivityAt()`. It is empty at startup and after stop/logout,
and advances from one injected `Clock` (`Clock.systemUTC()` in `SceneManager`, controlled clocks in tests).
An `AtomicReference<Instant>` makes later background-worker reads safe. The observer retains no key text/code,
modifiers, target/control, text-field or clipboard content, mouse coordinates/button/click history, scroll or
touch payload, user/tenant/entity identifiers, document/window/screen content, accessibility/biometric data, or
input-event object. It never logs individual events or timestamps.

This phase is strictly local and ephemeral. It adds no SQL/migration, persistence, preference or machine-ID
write, `ApplicationInstance` mutation, API/server dependency, network transmission, heartbeat or scheduler,
durable session, remote logout, PubSub, geolocation, device UI, updater behavior, policy enforcement, idle
threshold, or ACTIVE/IDLE/AWAY/OFFLINE interpretation. Activity observation is neither a sensitive read nor a
meaningful domain/administrative mutation, so the audit compatibility review requires no audit event or schema
change. Installation failure is sanitized, logged without event data, and fails open without a user dialog.

Focused tests were added for empty initial state; controlled-clock key, mouse, scroll, and touch timestamps;
deterministic replacement; hidden/unfocused/programmatic/mouse-move exclusions; timing-only retained state;
cross-thread reads; logout/user-switch reset; exact filter types and handler removal; idempotent registration;
owned-dialog scope; authenticated installation; and logout/shutdown cleanup. The initially blocked required verification was subsequently completed before Phase 5B; Phase 5A is complete.

### Phase 5B — Consolidated client-control heartbeat

* **Goal:** report instance liveness/version/activity and receive server time/session/policy state.
* **In scope:** one server endpoint/service, jitter/backoff, lifecycle owner, explicit/unknown states.
* **Non-goals:** enforcement, PubSub authority, admin UI.
* **Likely files:** core contracts, `shale-server`, desktop bridge/UI lifecycle, tests.
* **Schema/API impact:** instance timestamp updates; additive API.
* **Verification:** heartbeat/activity separation, session generation cancellation, transient outage behavior,
  authorization/RLS, no overlapping loops.
* **Dependencies:** 2B, 4B, 5A; desktop session identity may initially be nullable.
* **Risks:** accidental lockout or direct-DB coupling.

### Phase 6A — Tenant-admin application-instance read service and API

* **Goal:** authorized tenant admins can view recent instances and version distribution.
* **In scope:** paged safe read API/service only.
* **Non-goals:** remote control, sessions, location map, updater scheduling.
* **Likely files:** server/core/data and API documentation/tests.
* **Schema/API impact:** read-only; additive endpoint.
* **Verification:** admin and cross-tenant denial, pagination, filters, numeric aggregation, and no secret exposure.
* **Dependencies:** 5B.
* **Risks:** machine-name/IP privacy and expensive unbounded queries.

Implementation uses the dedicated `ApplicationInstanceAdminReadServicePort`,
`ApplicationInstanceAdminReadServiceAdapter`, and `ApplicationInstanceAdminReadDao`; the lifecycle mutation
port remains unchanged. `GET /api/admin/application-instances` is tenant-admin-only and uses authenticated
principal tenant/actor identities. The controller verifies the current user is an administrator, and the DAO
independently verifies active same-tenant `is_admin` membership and both SQL session-context values. Queries
are explicitly tenant-qualified in addition to strict RLS; no tenant request parameter is authority.

Recent reads use the repository offset-page response (`page` default 0, allowed 0–100; `size` default 50,
allowed 1–100; no total count) and deterministic `StartedAt DESC, Id DESC` ordering. Filters are exact closed
`clientType`, canonical `major.minor.build` `version`, positive `userId`, `activeOnly`, and ISO-8601 `since`.
The default population is launches started in the prior 30 days; explicit windows are limited to 90 days and
five minutes of future clock skew. One set-based Users join returns instance ID, user ID/display name/email,
machine UUID, client type/version, and raw start/end/heartbeat/human-activity timestamps. Nulls remain null;
no presence state is inferred.

The machine UUID is exposed solely to group repeated launches from one workstation. It is random, not a
hostname, hardware fingerprint, secret, authentication factor, or proof of trust. DTOs exclude tenant/session
internals, row versions, audit metadata, tokens/JWT IDs, passwords, IP/location, and authentication state.

`GET /api/admin/application-instances/version-distribution` uses the same bounded `StartedAt` population. SQL
groups and orders numeric version components descending. `instanceCount` counts launch rows;
`distinctUserCount` separately counts distinct user IDs, and nullable `latestHeartbeatAt` is the maximum
received heartbeat. These are not active-user metrics.

Audit decision: current `AuditLog` is PHI/field-oriented and `EntityActionAuditLog` represents mutations, so
neither safely represents this query without misleading semantics or schema/allowlist work. Phase 6A emits no
per-row or ad-hoc audit records; one bounded administrative-query audit mechanism is deferred to Phase 6B.

There is no Phase 6A SQL migration. Existing Phase 4B/5B clients and contracts remain compatible. No instance
mutation, remote logout/revocation, durable session, geolocation, updater scheduling, version enforcement,
PubSub authority, liveness classification, or administration UI is added.

**Verification status (2026-09-29): COMPLETE.** Required Phase 6A verification was completed before Phase 6B.

### Phase 6B — Administrative-read audit mechanism

* **Status:** **IN PROGRESS** — implementation and static contracts are complete; Maven Central HTTP 403 and
  unavailable live SQL configuration currently block required executable verification.
* **Exact recommended scope:** one bounded tenant/actor-attributed audit event per successful sensitive admin
  query (never per row), with sanitized allowlisted query-kind/window/filter-presence/result-count metadata,
  retention/reviewer authorization, additive schema/vocabulary only if required, and cross-tenant tests. Exclude
  instance mutation, remote logout/revocation, sessions, geolocation, updater/enforcement, PubSub, and UI.

Phase 6B selects a dedicated `AdministrativeReadAuditLog`: `AuditLog` is PHI-field history and
`EntityActionAuditLog` deliberately describes mutations, so reusing either would be semantically misleading.
The table is strict tenant-owned and append-only in application semantics, with a tenant-qualified actor FK,
closed read types `APPLICATION_INSTANCE_RECENT_LIST` and
`APPLICATION_INSTANCE_VERSION_DISTRIBUTION`, database UTC occurrence time, nonnegative result count, and
bounded `varchar(1000)` metadata. Strict FILTER and AFTER INSERT/UPDATE RLS predicates use
`sec.fn_FilterByTenant`; there is no overlay or seed data.

`ApplicationInstanceAdminReadDao` is the unavoidable integration seam for the sensitive service: it verifies
admin/session context, performs the bounded read, appends exactly one event through
`AdministrativeReadAuditDao`, and commits before returning. Read and audit use one connection/transaction.
Audit persistence failure therefore fails closed and returns no data; authentication, authorization, malformed
filters/windows/pages, tenant-context, validation, and DAO read failures emit no success event. Each separate
page request is one query/event. Result count is page rows for recent list and bucket count for distribution.

Recent-list metadata allows only `page`, `pageSize`, `clientTypeFilter`, `applicationVersionFilter`,
`userFilterPresent`, `activeOnly`, and bounded `since`; distribution allows only bounded `since`. Serialization
is deterministic and never includes returned IDs/rows, machine UUID, names/emails, heartbeat/activity values,
IP/location, tokens, headers, SQL, exceptions, or arbitrary DTOs. No audit review endpoint/UI is added. Future
review is for a same-tenant administrator or designated audit administrator, never ordinary users.

Retention target is seven years for security/administrative review, subject to approved tenant/legal policy.
There is no existing automated audit-retention mechanism, so Phase 6B deliberately adds no cleanup job; an
operator-approved retention implementation remains future work. The migration is purely additive: older clients
do not populate or reference the new table, no existing constraint/RLS/API changes, and deployment before all
clients upgrade is safe.

Phase 6B adds no remote logout/control, revocation, durable session, device/audit UI, geolocation, updater
scheduling, version enforcement, PubSub authority, liveness classification, heartbeat audit, per-result audit,
ordinary-user read audit, or analytics telemetry.

### Phase 7A — Durable user-session schema

**Status: COMPLETE — foundation deployed and verified before Phase 7B.**

* **Goal:** introduce client-neutral durable session/revocation state.
* **In scope:** strict tenant schema/RLS, hashed opaque/bound-JTI representation, expiry/reason model,
  retention, entity-action audit allowlists.
* **Non-goals:** switching existing tokens/desktop, revocation UI, PubSub.
* **Likely files:** migrations/docs/tests only plus model contracts if needed.
* **Schema/API impact:** additive tables; none yet.
* **Verification:** RLS, uniqueness/hash rules, no raw secrets, transactional audit, retention queries.
* **Dependencies:** credential/token binding design decision.
* **Risks:** confusing token, session, device, and instance identities.

### Phase 7B — API token/session compatibility migration

**Status: COMPLETE — implementation and required verification completed before Phase 7C.**

* **Goal:** bind newly issued API tokens to durable sessions without breaking existing clients.
* **In scope:** versioned issuance/validation, overlap for legacy JWTs, durable shared revocation adapter,
  refresh/rotation behavior, deployment/rollback document.
* **Non-goals:** desktop migration, UI, remote notification.
* **Likely files:** `shale-server` runtime/auth controllers/config, core/data ports, web compatibility tests.
* **Schema/API impact:** uses 7A; auth response may gain optional fields, existing fields remain.
* **Verification:** restart/multi-replica revocation, old token overlap/expiry, refresh replay, all auth/API tests.
* **Dependencies:** 7A.
* **Risks:** locking out web users or accepting a revoked legacy token indefinitely.

### Phase 7C — Desktop session enrollment migration

**Status: IN PROGRESS — implementation is present; required Maven verification is blocked by Maven Central HTTP 403.**

* **Goal:** give desktop a durable session identity while preserving JDBC login during rollout.
* **In scope:** explicit post-credential server exchange or approved equivalent, secure local credential
  storage, attach/detach instance, rollback/compatibility mode.
* **Non-goals:** removing direct SQL auth, remote UI, enforcement.
* **Likely files:** desktop/auth adapters, server auth endpoint, secure storage abstraction, tests/docs.
* **Schema/API impact:** uses sessions; additive desktop auth contract.
* **Verification:** old desktop remains usable during rollout, credential secrecy, logout/exit distinction.
* **Dependencies:** 7B and secure-storage decision.
* **Risks:** this is the key auth migration; it must never be smuggled into heartbeat work.

### Phase 8A — Revocation service behavior

* **Goal:** authoritative self/admin revocation and account/credential invalidation.
* **In scope:** list/revoke/revoke-others services, transactional audit, request/heartbeat validation,
  explicit response codes.
* **Non-goals:** UI and PubSub acceleration.
* **Likely files:** core/data/server auth/services and focused security tests.
* **Schema/API impact:** session mutations; additive endpoints.
* **Verification:** revoked remains revoked across restart/replicas/missed push; tenant/admin boundaries;
  account disable and credential-change policy; audit rollback.
* **Dependencies:** 7B-7C.
* **Risks:** races during refresh/revocation and accidental cross-tenant administration.

### Phase 8B — PubSub revocation/policy acceleration

**Status: COMPLETE — implementation and required verification completed before Phase 9.**

* **Goal:** notify connected clients promptly after authoritative commits.
* **In scope:** PHI/secret-free invalidations and immediate revalidation.
* **Non-goals:** using push as authority or adding durable replay.
* **Likely files:** existing LiveBus dispatcher/publisher/server integration and tests.
* **Schema/API impact:** none; additive event types.
* **Verification:** missed/duplicate/wrong-tenant events, commit-before-publish, reconnect authority.
* **Dependencies:** 8A and policy read API.
* **Risks:** broad tenant events exposing session existence; prefer safe targeted routing.

### Phase 9 — User Devices & Sessions UI

**Status: IN PROGRESS — implementation and focused contracts are present; required Maven verification is blocked
because Maven Central returns HTTP 403 for the Spring Boot dependency BOM.**

* **Goal:** users view/revoke current and other sessions.
* **In scope:** current marker, client/device, activity, nullable approximate location, revoke one/others.
* **Non-goals:** admin firm view, GPS, exact location, instance equivalence.
* **Likely files:** JavaFX Settings/account surface, server APIs; web/mobile later reuse contracts.
* **Schema/API impact:** none beyond Phase 8; no breaking changes.
* **Verification:** self-only authorization, current-session handling, nullable fields, accessibility/visual QA.
* **Dependencies:** 8A.
* **Risks:** misleading location/activity and self-lockout UX.

### Phase 10 — Administrator session visibility and revocation

**Status: IN PROGRESS — implementation and focused contracts are present; required Maven verification is blocked
because Maven Central returns HTTP 403 for the Spring Boot dependency BOM.**

* **Goal:** authorized admins manage tenant sessions.
* **In scope:** paged/filterable view, revoke session/user, reasons, required audit and read-audit decision.
* **Non-goals:** global release administration or hidden surveillance.
* **Likely files:** Settings admin pane, server/data services, audit migration/tests.
* **Schema/API impact:** additive admin contracts/allowlists.
* **Verification:** current admin/tenant checks, last-admin safeguards where relevant, audited rollback,
  metadata sanitization.
* **Dependencies:** 8A, 9 patterns.
* **Risks:** privilege escalation and sensitive network metadata exposure.

### Phase 11A — Recommended/deadline policy UX

**IN PROGRESS.** Implementation is present, but this phase cannot be marked complete until the required Maven verification and rendered JavaFX QA execute successfully.

* **Goal:** replace per-release mandatory interpretation with central policy messaging/grace periods.
* **In scope:** recommended/required states and deadline UX, cached revision/server time.
* **Non-goals:** hard block, updater changes, access modes.
* **Likely files:** desktop policy adapter/UI notifications, server tests.
* **Schema/API impact:** none beyond policy; compatible reads.
* **Verification:** version boundaries, deadline/clock skew, corrected policy, transient outage.
* **Dependencies:** 2B, 5B.
* **Risks:** duplicate prompts with legacy manifest; define precedence explicitly.

### Phase 11B — Minimum-allowed enforcement and safe drain

* **Goal:** authoritatively block prohibited versions without destroying work.
* **In scope:** strict startup/login response and in-process new-work gate/safe completion workflow.
* **Non-goals:** unattended force termination, generic read-only mode.
* **Likely files:** server authorization/client-control, desktop navigation/work guards, extensive tests/docs.
* **Schema/API impact:** policy read only; explicit blocked response contract.
* **Verification:** explicit-block fail-closed; outage unknown fail-open within grace; unsaved-work scenarios;
  policy correction recovery.
* **Dependencies:** 11A and operator-approved safe-work inventory.
* **Risks:** firm-wide lockout; staged rollout and kill-switch/correction path are mandatory.

### Phase 12A — Update-attempt persistence contract

* **Goal:** durable sanitized history.
* **Implemented Phase 12 decision:** bounded per-user local persistence, because the updater has no credential and
  completion is observable before login. No tenant/user is attributed from a later login, and no bearer is passed
  to the updater. Central persistence/API/RLS was deliberately not added; local support evidence is the narrow
  Phase 12 operational surface.
* **In scope:** start/completion idempotency, retention and result taxonomy.
* **Non-goals:** log upload or a generic telemetry service.
* **Schema/API impact:** none.
* **Verification:** idempotency, unresolved classification, sanitization.
* **Dependencies:** instance/session identity.
* **Risks:** secrets/stack traces in failure summaries.

### Phase 12B — Existing updater result integration

* **Goal:** extend, not replace, `ShaleUpdater` to report structured outcomes.
* **In scope:** correlation/result handoff, post-relaunch completion, retry-safe server report, backup/recovery
  documentation.
* **Non-goals:** scheduling or changing normal install semantics.
* **Likely files:** `shale-updater`, desktop launcher/startup, local result format, tests/build scripts.
* **Schema/API impact:** uses 12A; compatible updater args extended carefully.
* **Verification:** download/hash/extract/install/relaunch failure codes, offline deferred report, existing
  updater tests and installed Windows/macOS smoke tests.
* **Dependencies:** 12A.
* **Risks:** updater self-replacement and partial-install reporting.

### Phase 13A — Workstation automatic-update preference

* **Goal:** persist explicit workstation opt-in independently of a user.
* **In scope:** machine-scoped preference/permissions and admin/user ownership decision.
* **Non-goals:** scheduler/helper or installation.
* **Implemented files:** core provider/result contract, desktop platform storage/service, and Settings > Administration UI.
* **Schema/API impact:** none; this is a local machine setting only.
* **Verification:** multi-user consistency, least privilege, opt-out, upgrade persistence.
* **Dependencies:** 4A.
* **Risks:** ambiguity over who may opt in on shared workstations.

### Phase 13B — Idle-aware unattended updater feasibility/prototype

* **Goal:** choose and prove one OS-supported scheduler/helper architecture.
* **In scope:** Windows first feasibility, signed helper/task lifecycle, 2 AM window, heartbeat vs real
  activity checks, retries and safe deferral.
* **Non-goals:** terminating active clients, macOS parity unless separately scoped, broad rollout.
* **Likely files:** updater/installer/build scripts and operational docs/tests.
* **Schema/API impact:** update attempts/instance controls only; no breaking API.
* **Verification:** active/unsaved client never killed, locked/logged-out machine cases, privileges,
  reboot/offline/retry, uninstall cleanup, installed-machine smoke test.
* **Dependencies:** 5B, 12B, 13A.
* **Risks:** privilege escalation, unsigned task tampering, and data loss; production rollout requires a
  separate phase after feasibility.

## 15. Progress tracker

| Phase | Status | Notes |
| --- | --- | --- |
| 0 | **COMPLETE** | Current state, target architecture, audit compatibility, and roadmap documented; no production/schema change. |
| 1A | **COMPLETE** | Empty global release catalog and ordered release-item schema, verification, contracts, and documentation; no runtime behavior. |
| 1B | **COMPLETE** | Empty global revisioned policy schema, verification, contracts, and documentation; no runtime behavior. |
| 2A | **COMPLETE** | Strict shared semantic version plus immutable release/item/effective-policy models and global read-only DAO/service boundary; `mvn test` passed; no runtime consumer. |
| 2B | **COMPLETE** | Authenticated read-only release/policy HTTP contracts, safe DTOs/errors/caching, OpenAPI, and focused regressions complete; repository-level `mvn test` passed. |
| 3A | **COMPLETE** | Strict tenant/user DESKTOP/WEB/MOBILE release-state foundation and monotonic service boundary complete. |
| 3B | **COMPLETE** | Desktop What's New implementation and focused tests complete; repository-level `mvn test` passed after the earlier Maven Central outage. |
| 4A | **COMPLETE** | Stable random machine UUID storage/provider, failure behavior, upgrade persistence, and packaging contracts complete. |
| 4B | **COMPLETE** | Authenticated application-instance enrollment/end lifecycle and required verification completed before Phase 5A. |
| 5A | **COMPLETE** | Foreground activity observation completed and verified before this Phase 5B run. |
| 5B | **COMPLETE** | Heartbeat lifecycle and required verification completed before Phase 6A. |
| 6A | **COMPLETE** | Read-only tenant-admin service/API and required verification completed before Phase 6B. |
| 6B | **COMPLETE** | Dedicated bounded administrative-read auditing and required verification completed before Phase 7A. |
| 7A | **COMPLETE** | Additive strict-tenant UserSessions schema and internal service foundation were verified before Phase 7B. |
| 7B | **COMPLETE** | Durable API issuance/validation/rotation/revocation and bounded legacy compatibility were completed and verified before Phase 7C. |
| 7C | **COMPLETE** | Desktop durable-session enrollment and required verification completed before Phase 8A. |
| 8A | **COMPLETE** | Authoritative self/admin revocation, audit, and account-security invalidation were completed and verified before Phase 8B. |
| 8B | **COMPLETE** | Best-effort invalidation/revalidation acceleration and required verification completed before Phase 9. |
| 9 | **COMPLETE** | Desktop self-service Devices & Sessions completed and verified before Phase 10. |
| 10 | **COMPLETE** | Tenant-admin session visibility/revocation completed and verified before Phase 11A. |
| 11A | **COMPLETE** | Central policy resolver, server-time anchored shell UX, outage/correction behavior, and updater precedence are verified. |
| 11B | **COMPLETE** | Minimum-allowed enforcement and safe drain are verified. |
| 12 | **COMPLETE** | Privacy-safe local attempt/outcome correlation and required verification completed before Phase 13A. |
| 13A | **COMPLETE** | Machine-scoped opt-in storage, provider, authorization, and Settings control were completed and verified before Phase 13B. |
| 13B | **COMPLETE** | Eligibility, aggregate readiness, cooperative shutdown, explicit invocation modes, update locking, and bounded retry/window contracts are implemented and verified. |
| 13C | **IN PROGRESS** | One authenticated, process-local Windows scheduler is wired. Automated and installed-Windows activation validation are required before completion. |

Status vocabulary: **NOT STARTED**, **IN PROGRESS**, **COMPLETE**, **BLOCKED**. Later Codex runs must
update this table and the applicable phase section.

## 16. Open decisions requiring operator input

1. **Global control-plane ownership:** which database/schema and operator identity may mutate global
   releases/policy? Tenant administrators should not receive this authority by default.
2. **Machine directory deployment:** Phase 4A chose system-wide locations and uninstall retention. Decide
   whether a future machine-wide installer should provision the documented application-specific ACL instead
   of relying on managed deployment/first-launch permissions; do not add a privileged helper implicitly.
3. **Durable API session migration:** choose bound JWT `jti` plus durable session, or access-token plus
   hashed rotating refresh credential; decide the short legacy-token overlap window.
4. **Credential-change policy:** revoke all sessions, or preserve the initiating verified session?
   Account disable/removal should revoke all.
5. **Retention/privacy durations:** approve durations for sessions, IP/region/city, detailed instances,
   and update attempts, including anonymization rules and whether admin session-list reads require a
   dedicated sensitive-read audit enhancement.
6. **Policy outage grace:** select the bounded cached-policy/session-control grace period after product
   and operational review. Source inspection cannot determine acceptable firm outage risk.
7. **Approximate geolocation provider:** if location is wanted, approve provider, Azure proxy/header
   trust configuration, privacy notice, and data-processing terms. Nullable IP-only storage is valid.

## 17. Future extension points (not committed near-term scope)

* Rich announcement items: important notice, safe link, tutorial/video, localization, audience targeting.
* Pilot/development channels and staged rollout assignments after production policy is proven.
* Maintenance, read-only, or blocked access modes after mutation coverage and safe-save semantics exist.
* Workstation opt-in overnight updates, idle windows, signed scheduler/helper, fleet rollout controls.
* More detailed update diagnostics while keeping verbose logs local and SQL summaries sanitized.
* Security notifications for new session, remote revoke, credential change, and suspicious refresh reuse.
* Web/mobile PubSub support, push notifications, and mobile secure credential storage.
* Tenant-specific policy overrides only if a demonstrated product need outweighs global-policy simplicity.

## Recommended exact scope for the next run

Complete **Phase 13B installed-Windows validation only** by performing the installed-Windows feasibility checklist;
the supplied full-repository `mvn test` result is **PASS** and need not be repeated for this documentation-only run.
If validation confirms the documented blockers,
scope a later phase to add a cooperative no-prompt shutdown readiness contract and one per-install-owner shared
update lock before considering any production scheduler activation. Do not add macOS scheduling, a privileged
service, public policy API, or production rollout in that validation run.

## Phase 7A implementation record — 2026-09-29

`dbo.UserSessions` is strict tenant-owned authentication history, deliberately distinct from
`ApplicationInstances`. An instance is a process/enrollment with machine, version, heartbeat, and activity;
a session is a logical authentication lifecycle with credential identity, expiry, and revocation. A session may
optionally reference a same-tenant, same-user desktop instance, one instance may have sequential sessions, web
and mobile need no manufactured instance, and instance abandonment does not revoke a session.

The additive table contains an internal `bigint IDENTITY` key; random unique `SessionId uniqueidentifier`;
tenant-qualified user ownership; optional tenant-qualified, non-cascading `ApplicationInstanceId`; closed
`DESKTOP`/`WEB`/`MOBILE` client type; unique UUID-shaped `CurrentAccessJti`; database UTC `IssuedAt`,
`CreatedAt`, and `UpdatedAt`; authoritative `ExpiresAt`; nullable `LastRefreshedAt`, `RevokedAt`, and bounded
closed `RevocationReason`; and `RowVer`. Active means not revoked and server time is before `ExpiresAt`;
expired rows are retained. Revocation is idempotent and preserves its first database timestamp/reason. The
initial retention target is seven years, subject to approved tenant/legal policy; cleanup automation is deferred.

The current API has no separate refresh credential: refresh revokes the old access-token JTI in memory and
issues a fresh UUID JTI with the same eight-hour access lifetime. Phase 7A therefore stores only the current
access JTI as UUID metadata. The stable random session ID is independent of every token and database row.
Future rotation conditionally replaces the current JTI and expiry while preserving the logical session; no
unbounded token history is introduced. No raw JWT, refresh token, bearer value, password, MFA secret,
arbitrary metadata, or credential proof is persisted. `UserSessionServicePort`, `UserSessionServiceAdapter`,
and `UserSessionDao` form an internal-only create/find/idempotent-revoke/conditional-rotation boundary.

Strict `TenantFilter` FILTER and AFTER INSERT/UPDATE block predicates use `sec.fn_FilterByTenant`; there is no
global overlay or seed data. Existing login, refresh, logout, JWT validation, in-memory JTI revocation,
desktop/web clients, heartbeat, and administrative reads remain unchanged and require no `UserSessions` row.
No endpoint, UI, remote logout, PubSub, geolocation, update enforcement, device control, or heartbeat coupling
is added.

Audit review intentionally adds no `EntityActionAuditLog` vocabulary in this foundation. Session creation and
routine rotation are security lifecycle state, not PHI/entity administration, and there is no runtime caller.
Phase 7B must transactionally define bounded security audit events for actual issuance and explicit
logout/security/admin revocation before activating writes; routine refresh remains unaudited unless an approved
security-event store is introduced. This avoids misusing PHI logs or claiming atomicity before runtime cutover.

## Phase 5B implementation record — 2026-09-29

**Status: COMPLETE — implementation and required verification completed before Phase 6A.**

Phase 5B adds only the consolidated latest-state heartbeat foundation. The rerunnable migration
`docs/sql/2026-09-29_application_instance_heartbeat_phase5b.sql` adds nullable
`LastHeartbeatAt datetime2(7)` and `LastHumanActivityAt datetime2(7)` to `ApplicationInstances`.
It performs no backfill, creates no table/index/default/constraint, and changes no Phase 4B column,
key, check, or RLS predicate. Therefore current older Shale builds retain their original insert,
update, and read behavior and may leave both fields null. Deployment before desktop rollout is safe;
the nullable-column operation should be metadata-only/low-impact and has no index-build locking.
The companion verifier is
`docs/sql/verification/2026-09-29_application_instance_heartbeat_phase5b_verification.sql`.

`ApplicationInstanceServicePort.heartbeat` and its production adapter/DAO use authenticated tenant,
user, and instance identity. One owner-qualified atomic SQL update rejects ended rows, assigns
`LastHeartbeatAt` and `UpdatedAt` from `SYSUTCDATETIME()`, refreshes the existing three numeric
version columns, and advances human activity only when the supplied value is newer. Null never
erases activity. The service rejects activity more than five minutes ahead of its UTC clock. Unknown
and cross-owner instances are indistinguishable (404); an ended owned instance is a conflict (409).
The authenticated additive endpoint is `POST /api/application-instances/{id}/heartbeat`, accepting
only `applicationVersion` and nullable `lastHumanActivityAt`, and returning safe authoritative
instance lifecycle/version/heartbeat/activity state. Ordinary high-frequency heartbeat writes are
intentionally not entity-action audited, consistent with the initiative's audit review; they contain
no PHI and adding one audit row per heartbeat would create unbounded telemetry history.

The desktop owns one scheduler per successfully enrolled instance. Its baseline is 60 seconds with
uniform bounded jitter from -10 through +10 seconds (always 50–70 seconds). It reschedules only after
an attempt completes and also uses an atomic in-flight guard, so calls cannot overlap or queue.
The send reads Phase 5A's `Optional<Instant>` at execution time and parses the single authoritative
`AppVersionProvider.currentVersion()` through strict `SemanticVersion`. Transient failures keep the
application usable, log only the first sanitized outage transition, and wait for the next ordinary
jittered interval; there is no immediate retry loop. Durable ended/not-found outcomes stop that
enrollment. Logout/shutdown cancel future work before best-effort end without waiting on heartbeat.
An enrollment generation token discards stale callbacks, so User A's completion cannot stop or alter
User B's lifecycle.

No ACTIVE/IDLE/AWAY/OFFLINE inference, enforcement, remote logout, durable session work, PubSub
heartbeat/presence authority, geolocation, device/admin UI, updater behavior, or heartbeat/activity
history was added. Phase 6A subsequently implemented the authorized, paged, safe read API/service described above without adding remote control, sessions, location, updater scheduling, or enforcement.

Verification was subsequently completed before the Phase 6A run; the historical dependency outage no longer controls Phase 5B status.


## Phase 7B implementation record — 2026-09-29

New server API logins now prepare one UUID JTI and access-token expiry, create a `WEB` `UserSessions` row with a random public UUID `SessionId` and null `ApplicationInstanceId`, and only then sign a JWT containing canonical string claims `sid` and `jti`. The persisted `ExpiresAt` equals JWT `exp`; refresh extends both by the configured access-token TTL because Shale has no separate refresh credential. HMAC signing follows persistence and has no expected runtime I/O failure; an exceptional signing failure can leave an unusable orphan row but cannot expose an unpersisted token. Existing login/refresh/logout request and response DTOs are unchanged, and the web treats bearer tokens as opaque values. Desktop authentication remains untouched.

After signature and ordinary expiry verification, bound-token authentication parses UUID `sid`/`jti` and performs one minimal, indexed, tenant-and-user-qualified `UserSessions` lookup through a principal-initialized runtime connection. SQL state is authoritative: missing, revoked, expired, wrong-tenant/wrong-user, or current-JTI-mismatched sessions fail with the existing generic 401. There is no cache, so replicas observe rotation or revocation on their next request. Refresh uses the Phase 7A conditional update, now also requiring database `ExpiresAt > SYSUTCDATETIME()`; one stale/concurrent refresh loses. Logout sets the first `RevokedAt`/`USER_LOGOUT` reason idempotently and also records the JTI in the process-local store only as rollback defense in depth.

`SHALE_AUTH_SESSION_BINDING_CUTOVER_AT` is a required ISO-8601 UTC instant in active server profiles. An otherwise-valid unbound JWT is eligible only when its `iat` is strictly before that boundary and server time is strictly before `cutover + SHALE_AUTH_TOKEN_TTL_SECONDS`; its original JWT expiry and in-memory JTI revocation still apply. An unbound token issued at/after cutover is rejected. Refresh during the window creates a durable WEB session, returns a bound token, and retires the legacy JTI. Missing or malformed cutoff configuration fails startup rather than permitting legacy tokens indefinitely.

No Phase 7B SQL migration is needed: Phase 7A already contains session/JTI, expiry, refresh, and first-revocation fields plus the tenant-qualified index/constraints. This is N-1 compatible at the schema and HTTP-contract layers; older clients send no new fields. Rollback is operationally sensitive: a pre-7B server ignores SQL revocation and could accept a still-unexpired bound JWT as an ordinary signed token. Normal rollback must therefore occur only after the maximum token TTL with Phase 7B traffic drained, or preserve every relevant process-local revocation entry; emergency rollback after durable revocation requires rotating the JWT signing secret, which invalidates all outstanding tokens. Durable rows themselves do not block old code.

Audit decision: Phase 7B uses the bounded intrinsic `UserSessions` lifecycle record (`IssuedAt`, `LastRefreshedAt`, first `RevokedAt`, and closed reason) as the security record. It does not put authentication events into PHI `AuditLog`, administrative-read audit, or the entity-mutation audit whose allowlists do not model security sessions. No raw JWT or token history is stored. A second audit table would duplicate the authoritative bounded lifecycle and require unnecessary schema in this cutover; no per-request or routine-refresh event is emitted.

This phase added no public session/device/admin-revoke endpoint, remote logout delivery, PubSub, UI, geolocation, desktop-auth redesign, mobile flow, or updater policy/enforcement. Its required verification was subsequently completed before Phase 7C, so Phase 7B is **COMPLETE**.


## Phase 7C implementation record — 2026-09-29

**Status: COMPLETE — implementation and required verification completed before Phase 8A.**

Desktop credential authentication remains the unchanged first authority: `AuthServiceImpl` reads the active user
through the auth datasource and bcrypt-verifies the password, after which the existing runtime JDBC tenant/user
context is armed. Phase 7C then makes one bounded HTTPS `POST /api/auth/desktop-session` exchange. Because the
repository has no trusted post-JDBC assertion issuer, the selected transitional proof is a single second credential
verification through the server's existing `AuthServicePort`. The password remains only in process memory between
the successful JDBC check and this immediate exchange, is never persisted or logged, and is discarded when the
exchange finishes. This is stronger than accepting claimed tenant/user/machine identifiers and avoids introducing
an evergreen desktop secret or a new proof table. A future migration may replace it with a short-lived assertion.

The additive request contains email, password, and an optional application-instance database id; it contains no
tenant, user, or client-type authority. The server derives tenant/user from credential authentication, fixes client
type to `DESKTOP`, owner-qualifies any instance by id + tenant + user + active `DESKTOP` state on a
principal-initialized connection, and then reuses `ServerAuthSessionService` and Phase 7B signing. The response
contains the bearer token, public `SessionId`, current JTI, and expiry; internal `UserSessions.Id` is not exposed.
A legitimate unavailable Phase 4B enrollment results in a null instance link, not a fabricated instance or desktop
lockout. No SQL change is required because Phase 7A already permits that nullable relationship.

`DesktopSessionEnrollmentLifecycle` stages the credential only after successful JDBC authentication, exchanges it
after best-effort instance enrollment, and generation-guards installation in `DesktopServerSession`. The latter is
the single process-level bearer provider. Tokens are deliberately **memory-only**: Phase 7C has no remember-me or
restart-resume requirement, so OS credential storage would add attack surface without user behavior to support.
There is therefore no plaintext fallback, token file, registry entry, preferences value, Keychain/Credential Manager
entry, or token effect on packaging/uninstall. Java `String` values cannot be reliably zeroized; the design limits
retention instead of claiming erasure.

HTTP 404/501 (older server) and transport/5xx failure enter explicit JDBC-only compatibility mode for that login;
there is no retry loop. Bound-token HTTP features are unavailable while JDBC database work and prior compatible
paths remain usable. 401/403, instance mismatch, and malformed successful responses set security-rejected state
and never masquerade as compatibility. A rejected bearer is cleared rather than retried; Phase 8A owns expanded
recovery/revocation APIs. Existing desktop HTTP feature inventory found no ordinary API data calls requiring bearer
conversion; LiveBus negotiate/publish and heartbeat remain separate existing protocols.

Logical logout best-effort calls existing `/api/auth/logout`, clears bearer state, stops heartbeat, ends the instance,
and clears JDBC/runtime identity. Process exit ends the instance and clears memory but does not revoke the durable
session merely because the process stopped. A user switch goes through logical logout before staging the next
identity, so the prior bearer cannot be installed or reused. Old desktops ignore the additive endpoint and rows; a
new desktop against an old server uses documented JDBC-only compatibility. Rollback simply restores the old
desktop; historical session rows remain and no local credential artifact affects uninstall or rollback. N-1 schema,
web login/refresh/logout contracts, and old JDBC operation remain compatible.

Audit compatibility review: issuance and logout use the bounded authoritative `UserSessions` lifecycle selected in
Phase 7B. This phase adds no PHI/entity mutation and does not duplicate security lifecycle events into PHI,
administrative-read, or entity-action audit tables. It adds no session UI, remote/admin revoke API, PubSub event,
geolocation, fingerprinting, heartbeat authentication, update policy, or enforcement.

Deployment order is: retain verified Phase 7A schema; deploy the verified Phase 7B server plus this additive
endpoint; deploy the Phase 7C desktop; continue JDBC-first login; observe sanitized enrollment outcome logs; and
only after verification and rollout stability consider Phase 8A. Required focused and full Maven tests could not
start because Maven Central returned HTTP 403 for the Spring Boot BOM. Phase 7C must remain IN PROGRESS until
those tests pass.

### Phase 7C focused manual verification checklist

The following installed/runtime checks remain unverified while Phase 7C is IN PROGRESS:

1. Valid desktop JDBC login succeeds before enrollment.
2. Durable desktop enrollment returns a bound session and matching public session/JTI state.
3. An available owner-qualified ApplicationInstance is linked; unavailable enrollment produces a null link.
4. Ordinary JDBC-backed application use remains functional.
5. Logical logout revokes/clears the durable session and ends the instance.
6. Restart has no resumed bearer, and user switch cannot reuse the prior user's bearer.
7. A simulated endpoint outage or old server enters JDBC-only compatibility mode once without retry spam.
8. Invalid credentials or instance ownership rejection leaves server-session functionality failed closed rather than silently downgrading.

## Phase 8A implementation record — 2026-09-29

**Status: IN PROGRESS — implementation, schema/catalog verification, and Maven tests pass; the explicit non-`dbo`
Phase 8A RLS verifier is present but still requires live execution. Phase 8B is NOT STARTED.**

Phase 8A adds `GET /api/sessions`, `POST /api/sessions/current/revoke`,
`POST /api/sessions/{sessionId}/revoke`, and one set-based transactional
`POST /api/sessions/revoke-others`. Self scope is always the principal tenant/user; the current marker and exclusion
come only from the authenticated bound JWT `sid`. Responses omit internal row id, JTI, bearer material, tenant id,
row version, machine UUID, and inferred liveness. Revocation is idempotent and preserves the first timestamp/reason.

Tenant administrators receive bounded `GET /api/admin/sessions` (default 50, maximum 100; strict user/client/active/
since filters) and `POST /api/admin/sessions/{sessionId}/revoke`. Controller checks are backed by service/SQL checks
of session context, active membership, and `is_admin`; every lookup/mutation includes the principal tenant. Admin
list writes one audit per query and explicit self/admin revocations write one event in the same transaction. Audit
failure rolls back the mutation/read response. The additive, RLS-protected `SessionSecurityAuditLog` contains only
closed event/reason codes, actor, optional public session/target user, affected count, and server UTC time. The
UserSessions reason check adds only `USER_REVOKED`; no token/JTI is audited. This additive migration is N-1 schema
compatible: old builds ignore the table and tolerate stored varchar values, while HTTP contracts are additive.

Deactivation, tenant removal, and administrative password reset revoke all active sessions with `SECURITY` in the
same user transaction. Bound validation also joins active/non-removed user eligibility, closing the race and making
disablement authoritative across replicas. There is no distinct end-user password-change flow in the repository;
when one is introduced it must explicitly choose current-session preservation. The existing administrative reset is
security-sensitive and revokes all, including the initiating user's target session if applicable. Unrelated users
are untouched.

SQL updates are set-based. Conditional refresh still requires unrevoked state and its expected JTI, so revoke wins:
a refresh completed first is followed by revoke, while a refresh attempting after revoke fails and cannot resurrect
the row. Server A's commit is observed by server B's next indexed SQL validation. No cache, heartbeat,
ApplicationInstance mutation, PubSub, or client push is required. ApplicationInstance rows and historical heartbeat
facts remain independent.

Rollback retains revoked rows and audit history. Rolling back to Phase 7B preserves durable enforcement; rolling
back before 7B can ignore SQL revocation, so the existing mitigation remains mandatory: drain for the maximum token
TTL or, for emergency rollback, rotate the signing secret. Phase 8A does not worsen that risk.

### Phase 8A API-level manual checklist (not yet executed)

1. Login creates a usable current session.
2. Self-list shows exactly the token-bound current session marker.
3. A second login appears as an independent session.
4. Revoking the other session makes its next authenticated request fail.
5. Revoke-others preserves the current `sid`.
6. A tenant admin lists/revokes a same-tenant session.
7. A non-admin receives 403 from admin endpoints.
8. Deactivation/removal/password reset revokes all target sessions and leaves unrelated users untouched.

Phase 8A added no session UI, PubSub session event, geolocation, hardware fingerprint, device trust, updater
enforcement, desktop password-auth removal, or refresh-token redesign. Its catalog, Maven, and live non-`dbo` RLS
verification (using `2026-09-29_session_security_audit_phase8a_rls.sql` and the exact-fixture cleanup script) were
completed before Phase 8B began; Phase 8A is complete.

### Phase 8B implementation boundary and manual verification

Phase 8B implements optional best-effort notification layered over SQL authority. `SESSION_INVALIDATED` is routed
through the existing tenant-authorized `client-{ShaleClientId}` group because the inspected negotiation contract
does not safely grant a session-specific group; the payload's public `sessionId` narrows reaction without exposing
a secret. `APPLICATION_POLICY_CHANGED` is global/channel-scoped (`policy:{channel}`), matching the Phase 1B global
policy rather than inventing tenant RLS. Its mutation publisher is an additive hook because runtime policy
administration does not yet exist. Both event payloads are minimal invalidations, not state replication.

The Phase 8A service commits revocation and its existing audit, returns the changed public ids directly from the
set-based update, and only then invokes the best-effort publisher. Rollback emits nothing. A publisher exception is
logged by exception class only and does not alter the successful operation. Explicit current-session logout skips
its redundant self-notification because local teardown already clears the bearer. Notification delivery/reception
is intentionally not audited.

After durable desktop enrollment, LiveBus handlers validate tenant, public session id, and login generation. One
in-flight bounded `/api/sessions` request coalesces duplicates. Only authoritative 401/403 confirmation clears the
HTTP bearer; a valid response retains it and a timeout/transport failure makes no revocation assumption. JDBC
authority remains intact as Phase 7C requires. Policy hints and reconnect reload the existing authoritative global
PRODUCTION policy read. Reconnect never expects replay. Logout/user switch/shutdown detach handlers, wrong-tenant
and wrong-session hints are ignored, and unknown types remain safe for old clients. Older servers and PubSub
outages fall back to ordinary bound-token validation and policy reads.

Manual verification checklist (not executed in this non-connected environment): (1) establish two durable sessions;
(2) revoke one from the other client/API; (3) observe prompt invalidation; (4) observe authoritative revalidation;
(5) confirm only the revoked bearer is cleared; (6) disconnect PubSub, revoke, reconnect, and confirm revalidation
finds the missed revocation; (7) publish a committed policy change and confirm authoritative reload; (8) duplicate
the hint and confirm no duplicate visible behavior.

No SQL migration, durable replay/outbox/cursor, polling loop, Phase 9 UI, geolocation, new audit row, policy
enforcement, updater scheduling, or mandatory shutdown was added. Phase 9 is **NOT STARTED**. Its exact next scope
remains the self-service User Devices & Sessions UI over the existing Phase 8A APIs: current marker, self list,
revoke one/revoke others, nullable approximate location only after its separate privacy decision, and accessibility/
visual verification—excluding tenant-admin UI, GPS/exact location, instance/session equivalence, and later updater
enforcement.

## Phase 9 implementation record — 2026-09-29

**Status: COMPLETE — implementation and required verification completed before Phase 10.**

Settings > Personal now contains a lazy **Devices & Sessions** inline surface. It uses the desktop's memory-only
Phase 7C bearer through a narrow `UserSessionManagementClient` and only Phase 8A self endpoints. Each successful
mutation is followed by a fresh authoritative list; there is no polling, per-row lookup, direct JDBC, or optimistic
removal. An absent durable server session produces the expected compatibility-mode unavailable state without
affecting JDBC work.

Rows show only a polished client label, server-provided current marker, signed-in time, last token refresh when
present, expiry, and reliable signed-out/expired/active state. The current row is pinned first and has no remote
sign-out action. Other active rows and “Sign out all other sessions” use cancel-default destructive confirmation;
copy explains that server access changes authoritatively on validation and does not promise immediate process exit.
Times are rendered in the workstation's local zone. Heartbeat and foreground input are absent from Phase 8A, so
the UI does not invent “Last active” or “Last server contact” semantics.

Loads and mutations run off the FX thread. A page generation plus tenant/user identity guard discards late results
after close, logout, or user switch, and close clears rendered rows. The page creates no PubSub subscriber and never
treats push as authority; Phase 8B remains the single current-session invalidation/revalidation owner. A coalesced
reconnect hint, successful local mutation, or explicit Refresh triggers authoritative reload while the pane is open.

No approved privacy-safe location source exists, so no location/IP is captured or displayed. No machine name/UUID,
fingerprint, hardware metadata, token/JTI, internal row id, tenant id, or audit metadata is exposed. No admin/cross-
user or web UI, updater enforcement, API field, schema, or SQL migration was added. Existing Phase 8A transactional
session-security auditing remains authoritative; the presentation adds no duplicate audit event.

Phase 9 required focused, selector-selected, full-reactor, rendered JavaFX, and manual verification was completed before Phase 10.

## Phase 10 implementation record — 2026-09-29

**Status: COMPLETE — implementation and required verification completed before Phase 11A.**

Settings > Administration now includes a lazy **Sessions** management window for authenticated tenant
administrators. Hidden navigation and the controller's current `AppState` admin/tenant/user guard prevent ordinary
entry, while the Phase 8A controller/service/SQL active-membership administrator checks remain authoritative when a
caller bypasses the UI. The JavaFX module depends on `UiRuntimeBridge.AdminSessionManagement`; the desktop adapter
uses only `GET /api/admin/sessions` and `POST /api/admin/sessions/{sessionId}/revoke` with the process-memory bound
bearer. It never queries `UserSessions` through desktop JDBC.

Each request is bounded to 50 rows. User, exact client type, active-only, and since-window selections map directly
to Phase 8A server filters; Previous/Next changes the server page and no local filter implies unloaded completeness.
The user selector is hydrated once from the established same-tenant user service. Page results retain Phase 8A's
deterministic `IssuedAt DESC, Id DESC` ordering and expose no invented total. Opening, filter changes, page changes,
explicit Refresh, and successful revocation issue an authoritative reload; there is no polling or per-row request.

The safe Phase 8A admin projection was additively extended with the established same-tenant user display name and
email because numeric user identity alone was a concrete target-selection blocker. The server obtains both in the
existing set-based page query with one tenant-qualified Users join, not an N+1 lookup. Rows display owner name/email,
client type, signed-in time, optional last refresh, expiry, revoked time/reason where applicable, and only factual
Active, Revoked, or Expired state. The current bound administrator session is marked and has no row revoke action;
ordinary logout is the prescribed path.

Revoke-one requires destructive confirmation identifying owner, client, and sign-in time. Copy states that server
access is revoked authoritatively, process closure may not be immediate, the next validation rejects the session,
and the existing Phase 8B hint may accelerate validation. There is no free-text reason: the server continues to use
fixed `ADMIN_REVOKED`. Success reloads the same page/filter state; failure preserves existing cards and permits retry.
Generation plus tenant/user/admin identity checks discard stale list and revoke completions after close, logout,
user switch, tenant switch, or a newer filter/page request; disposal clears rows and selector data.

Phase 8A exposes no same-tenant administrator bulk-user revoke operation. Phase 10 therefore adds no bulk button,
client-side loop, or new server endpoint; this capability is explicitly deferred until separately justified. The
existing list transaction still writes exactly one bounded `ADMIN_SESSION_LIST` security/read event per successful
query, never per row, and the existing admin revoke transaction still writes its one `ADMIN_REVOKE` / `ADMIN_REVOKED`
security event before commit. The UI creates no duplicate audit rows and displays no audit internals.

No schema or SQL migration was required. No global/all-tenant view, release administration, location/IP, machine
UUID, fingerprinting, presence/activity surveillance, updater control, enforcement, second PubSub subscriber, or
Phase 11 behavior was added. Phase 8B remains best-effort acceleration only; admin success is the HTTP mutation plus
authoritative reload, not push delivery.

Phase 10 required focused, selector-selected, full-reactor, rendered JavaFX, and live authorization/audit verification was completed before Phase 11A.

## Phase 11A implementation record — 2026-09-29

The shared `ApplicationUpdatePolicyResolver` compares strict numeric `major.minor.build` values and produces `CURRENT`, `RECOMMENDED`, `REQUIRED_BEFORE_DEADLINE`, `REQUIRED_DEADLINE_REACHED`, or `UNKNOWN`. A running version equal to or newer than the relevant target never warns. `minimumAllowed` owns required semantics; `minimumRecommended` owns recommendation only after the allowed check. Channel mismatch, missing policy/time, and malformed running versions are unknown rather than invented authority.

The existing current-policy read remains authoritative and now returns database UTC captured in the same query. Desktop anchors it to monotonic elapsed time for at most 15 minutes. Local wall-clock skew cannot move the deadline. A stale/missing anchor becomes informative `UNKNOWN`; it never blocks work. The process-only cache contains only global non-secret policy data and clears at shell teardown. A transient failure shows last-known policy as stale when available, otherwise unknown. A later success replaces the snapshot wholesale, so corrected policy immediately removes or weakens obsolete messaging.

The authenticated shell owns one responsive wrapping banner. Recommended notices are subtle and dismissible for the revision/target pair. Required-before-deadline notices show a local-formatted deadline and day-granularity grace and may be temporarily dismissed until refresh. Deadline-reached notices remain visible. Text conveys status in addition to semantic styling; actions are keyboard accessible and refresh never steals focus. Update calls the established launcher.

Phase 8B remains the single policy invalidation/reconnect subscriber and calls this coordinator for an authoritative reread. Once a revision loads, policy presentation suppresses legacy manifest availability notifications. When policy is absent/unavailable, legacy manifest notification remains the older-server fallback. The post-login manifest modal no longer exits or blocks entry. What's New and its acknowledgement remain separate.

No SQL migration, policy administration, session change, access-mode behavior, hard block, read-only mode, safe drain, forced logout/shutdown, updater replacement, or unattended scheduling was added. This global read-only presentation has no meaningful audited mutation or sensitive read.

Phase 11A's focused, selector-selected, full-reactor, and rendered JavaFX verification was subsequently completed. Phase 11A is **COMPLETE**.

## Phase 11B implementation record — 2026-09-30

**Status: COMPLETE — implementation and required verification completed before Phase 12.**

Phase 11B separates operational enforcement from Phase 11A presentation with `ApplicationVersionEnforcementState`: `ALLOWED`, `DRAINING_REQUIRED_UPDATE`, `BLOCKED_NEW_WORK`, `UNKNOWN_GRACE`, and the observable correction transition `RECOVERING`. Only `REQUIRED_DEADLINE_REACHED`, derived from authoritative `minimumAllowed` and server-anchored time, closes the new-work gate. Recommendation and a future deadline remain allowed. There is no persisted blocked bit: every successful authoritative refresh replaces the decision, and a correction emits recovery then restores normal entry without restart.

`SafeWorkDrainCoordinator` is a session-scoped launch gate and lease counter, not a generic window registry or read-only mode. **New work** means entering a user-driven workflow which can create or mutate substantive case/legal data: New Intake; create/edit case, contact, organization, task, or calendar event; and comparable mutation editors. Searching, browsing lists, opening record views, Settings navigation, update/retry, normal close/exit, heartbeat, notification acknowledgement, and other operational background activity are not new work. A lease granted before the enforcement boundary remains valid until that workflow saves/finishes, cancels, or closes. Save/apply paths are never policy-gated, an already-dispatched write is never cancelled, and drain reaching zero changes only to `BLOCKED_NEW_WORK`; it never terminates the process.

The concrete inventory found: New Intake and new Organization are modal multi-step create workflows with existing dirty-close protection; Contact aggregate create/edit is a modal transaction retaining values after failed saves; case overview/details have inline edit modes plus focused field, team, party, task, link, date, and enhanced-text dialogs; task detail is an editable hydrated dialog; calendar has a new-event wizard and asynchronous event/case-date editors; Settings contains administrative editors; material requests and file/document-adjacent case-material actions have modal create/edit flows. These are safe to finish or cancel after entry and unsafe to close from under the user. Plain list/profile/search navigation is read-only. Existing editor dirty prompts remain authoritative; the drain coordinator does not serialize form state or add duplicate prompts.

At authenticated startup the shell first presents a textual policy-check surface. A fresh prohibited result presents an update-required surface with keyboard-reachable Update, Retry policy check, and Exit actions rather than entering the normal shell. Authentication credentials/session are retained, background policy invalidation and heartbeat remain active, and a corrected policy opens the shell without restart. Missing policy, no cache, or an expired time anchor enters `UNKNOWN_GRACE` and permits startup. In-process policy changes retain read/navigation access, strengthen the persistent Phase 11A notice, disable/gate substantive workflow entry, and grandfather registered work.

The bounded authority window is exactly **15 minutes of monotonic elapsed time** from the last policy response's database UTC. A transient failure inside that window retains a last-known prohibited decision (hysteresis). Once older than 15 minutes it becomes `UNKNOWN_GRACE`; an ancient cache cannot lock out the firm. A later authoritative success replaces it wholesale. If the package is unavailable, the notice says so, enforcement remains, Retry/read/finish/exit remain available, and no write is automatically retried. The existing updater launcher is unchanged.

No server mutation middleware was added. Current desktop domain mutations remain predominantly direct JDBC and authenticated API requests do not carry a sufficiently authoritative, ubiquitous desktop version plus workflow-start token to distinguish grandfathered completion from new work. Blocking all POST/PUT would destroy drain safety. The explicit desktop contract is therefore the launch-gate message `Shale must be updated before starting new work.` Server-side classified rejection remains a future additive hardening only after routes carry safe workflow semantics; reads and grandfathered completion must remain allowed. This is operational compatibility enforcement, not anti-tampering.

Rollout rule: never raise `minimumAllowed` above a version lacking Phase 11B safe-drain behavior until the installed fleet is sufficiently migrated. Before raising it, verify the updater package and policy endpoint, raise conservatively, monitor, retain the central correction path, and maintain an explicit plan for older clients. An accidentally high/past policy is recovered by correction; an endpoint outage ages into grace; a missing updater package leaves read/drain/retry/exit available. Older clients cannot be claimed to safe-drain merely because the policy schema is N-1 compatible.

Audit compatibility review: the gate and leases create no domain or administrative mutation and no sensitive read. Existing saves continue through their established transactional entity/PHI audit seams. Policy reads are global non-PHI operational reads, so no new audit row or schema is appropriate. No API or SQL schema changed.

Phase 11B's focused, selector-selected, full-reactor, rendered, and installed verification was completed before
Phase 12. Its installed checklist covered allowed startup, grandfathered workflow completion, refusal of new work,
continued reads/settings access, policy correction recovery, missing-package behavior, and data-safe exit.

## Phase 12 implementation record — 2026-09-30

**Status: COMPLETE — implementation and required verification completed before Phase 13A.**

An attempt begins only inside `launchUpdater()`, after the user has chosen Update now and immediately before the
process handoff. The desktop generates a random opaque UUID; it encodes no user, tenant, machine, or version and is
not a credential. Rapid duplicate actions continue to be coalesced by the existing `UpdateFlowCoordinator` in-flight
guard, while a later retry creates a distinct UUID/history file. Observability is fail-open: inability to create or
transition the local record is logged and never prevents the updater launch.

Correlation uses one atomic, maximum-4-KiB Java properties file per attempt beneath the established **per-user**
application-support directory (`%LOCALAPPDATA%\Shale\update-attempts` or
`~/Library/Application Support/Shale/update-attempts`). Per-user scope matches the per-user Windows install and
avoids broadening machine-directory ACLs. The location is outside the replaceable install tree, so it survives app
exit, ZIP overlay, reboot, and bundle replacement. The optional `--attemptId` and `--attemptDir` arguments preserve
old updater invocation compatibility. Files contain only UUID, strict source/target/actual semantic versions,
`PRODUCTION`, `DESKTOP`, closed state/failure codes, and diagnostic local UTC timestamps. They contain no password,
JWT/JTI, email/name, tenant/user ID, machine UUID/fingerprint, IP/location, PHI/business data, URL, raw response,
exception, stack trace, or arbitrary metadata.

The lifecycle is monotonic: `STARTED -> UPDATER_LAUNCHED -> INSTALL_APPLIED -> COMPLETED`. `FAILED` and
`OUTCOME_UNKNOWN` are terminal; duplicate reports are idempotent and terminal records cannot regress. The existing
updater does not invoke the manifest MSI, so Phase 12 does not claim fictional installer-process evidence.
`INSTALL_APPLIED` means only that stop/backup and ZIP replacement/overlay returned successfully. Relaunch success,
updater process start, desktop exit, and install application are explicitly not completion.

The closed failure vocabulary reflects facts the current implementation can distinguish:
`UPDATER_LAUNCH_FAILED`, `MANIFEST_UNAVAILABLE`, `PACKAGE_UNAVAILABLE`, `PACKAGE_DOWNLOAD_FAILED`,
`PACKAGE_VALIDATION_FAILED`, `PACKAGE_EXTRACTION_FAILED`, and `INSTALL_APPLY_FAILED`. Existing detailed local logs
remain unchanged. Central state never receives log or exception text because Phase 12 introduces no central state.
Cancellation cannot currently be distinguished from updater disappearance, so it is not guessed as a failure.

At every application start, before login, best-effort reconciliation scans the bounded local markers. For an
`INSTALL_APPLIED` attempt, strict `SemanticVersion` comparison confirms `COMPLETED` only when the running production
version is equal to or greater than the recorded target; the actual running version is retained. Restarting on the
source version, starting below target, malformed/unknown version, or a marker before install application does not
prove success and remains unresolved. This avoids attributing an attempt to whichever user later logs in and does
not require the old and new `ApplicationInstance` to match. A reconciliation error never delays or blocks startup.
What's New remains driven independently by its existing version/release-state logic, and Phase 11 enforcement
remains driven independently by actual running version and authoritative policy.

Unresolved attempts remain eligible for reconciliation for 30 days, then become `OUTCOME_UNKNOWN`, never failed.
Terminal local evidence is retained for 90 days and removed during a later successful startup reconciliation; no
scheduler or purge service was added. Multiple attempts remain separate and cannot complete each other because
each target and state is stored under its own UUID.

Central persistence decision: **no SQL table or API in this phase**. The updater is intentionally not made an API
client, no bearer is delegated, and pre-login reconciliation has no authoritative user/tenant principal. A
tenant-owned server record would therefore either misattribute the later user or require a new secret/enrollment
contract outside this phase. Local bounded evidence plus existing logs answers support diagnostics without creating
cross-tenant data. Consequently there is no migration, FK, RLS policy, live RLS run, server timestamp, audit row, or
audit allowlist change. Update progress is operational observability rather than PHI/entity mutation audit.

N-1 remains compatible: old desktop/updater pairs ignore the new files, the new updater accepts invocation without
the optional correlation arguments, and no server/schema requires an attempt write. No UI/dashboard, generic
telemetry ingestion, scheduling, automatic 2 AM behavior, idle processing, updater/MSI replacement, additional
termination, policy administration, safe-drain redesign, or session-management behavior was added.

Phase 12's focused updater/reconciliation/privacy/N-1/Phase 11/What's New, repository, and installed verification
completed before Phase 13A began.

## Phase 13A implementation record — 2026-09-30

**Status: COMPLETE — implementation and required verification completed before Phase 13B.**

The preference grants permission only for a future unattended executor; it cannot alter central policy, safe drain,
eligibility, or manual Update now behavior. Missing configuration defaults to not permitted without creating a file.
Only valid explicit `automaticUpdatesEnabled=true` permits execution; disabled, missing, unavailable, corrupt, and
unsupported-schema states remain distinguishable and fail safe.

The bounded UTF-8 properties file is `%ProgramData%\Shale\automatic-update-preference.properties` on Windows and
`/Library/Application Support/Shale/automatic-update-preference.properties` on macOS. Its complete schema is
`schemaVersion=1` plus `automaticUpdatesEnabled=true|false`. It contains no user, tenant, session, token, machine
UUID, network/location, PHI, or arbitrary metadata. It shares Phase 4A's durable directory but is independent of
machine identity. Upgrade and ordinary uninstall/reinstall retain it; old clients ignore it.

Writes use a normalized-path JVM lock plus OS file lock, unique temporary file, forced write, atomic replacement,
cleanup, and deterministic reread. Corruption never implies consent; authorized recovery preserves the prior file
under `.corrupt-*`. Permission/I/O failures return explicit unavailable state and never fall back per user.

Any authenticated Shale administrator may change the machine setting; ordinary users may read it but cannot mutate
through the controller/service path. This is not tenant ownership: a tenant 7 administrator's choice remains when a
tenant 8 user signs in. Logout, switching, and restart do not clear it. Application authorization is not an OS ACL:
the per-user Windows MSI provisions no ProgramData ACL, so external file modification remains possible wherever the
OS ACL permits it. Managed deployment can provision stronger ACLs. Equivalent macOS provisioning requires installed
verification.

Settings > Administration presents workstation-scoped state and disables non-admin mutation. Failed saves reread
the authoritative prior state. Changes invoke no updater, create no Phase 12 attempt, and create no central/entity
audit: this non-PHI local operational setting uses sanitized local transition/error logs.

Phase 13B consumes `WorkstationUpdatePreferenceProvider.current()` and proceeds only when
`unattendedExecutionPermitted()` is true for `ENABLED`; it must separately combine policy, update availability, idle,
and schedule rules. Phase 13A adds no timer, thread, task/launchd job, idle observer, updater invocation, MSI
execution, restart, or eligibility logic.

## Phase 13B implementation record — 2026-09-30

**Status: IMPLEMENTED, VERIFICATION PENDING — aggregate cooperative readiness and an OS-backed per-install-owner update lock are present. Required Maven verification is blocked by repository access (HTTP 403), so Phase 13B is not yet declared complete. Production unattended scheduling remains disabled.**

### Architecture decision and supported boundary

The selected narrow architecture is an **in-process evaluator in the existing authenticated desktop**, with no
timer wired in this phase. `UnattendedUpdateEvaluationService` rereads
`WorkstationUpdatePreferenceProvider.current()` for every evaluation and only calls the supplied handoff seam after
the pure resolver returns `ELIGIBLE`. It never reads or writes the Phase 13A file. Manual Update now remains on its
existing independent path. The foundation supports deterministic eligibility, local-window, idle, and bounded-retry
decisions and proves that every deferral stays before the Phase 12 attempt boundary. It does not claim that an
unattended update can currently execute.

Windows Task Scheduler (installer- or application-created), a detached helper, a combined task/helper, and a
Windows Service were rejected for activation now. The per-user MSI belongs to the installing Windows user; SYSTEM
must not mutate that user's install. A logged-out current-user task would need stored credentials or a new safe
credential-free logon contract. A detached helper also cannot obtain fresh authenticated Phase 11 authority without
delegating a bearer. The current updater force-kills `Shale.exe`, and no shared cross-process update/install lock
protects a second Shale process. An in-process timer alone cannot cover closed/logged-out machines, but it is the
only context that can presently reuse fresh authenticated policy and Phase 5A/11B state. Therefore no scheduler,
task, service, helper, MSI/WiX custom action, task name, or operational state file was added. Feature activation is
safe-default off by absence of runtime wiring, independently of workstation consent.

### Eligibility and scheduling contract

The preference is the first gate. Only `unattendedExecutionPermitted() == true` passes; `DISABLED`, `MISSING`,
`UNAVAILABLE`, and `CORRUPT` all return `DEFER_PREFERENCE`. Eligible central states are `RECOMMENDED`,
`REQUIRED_BEFORE_DEADLINE`, and `REQUIRED_DEADLINE_REACHED`; `CURRENT` and `UNKNOWN` defer. A required deadline
never overrides consent or safety. Package eligibility requires a reachable manifest, a present package, existing
compatibility approval, exact manifest/policy target equality, equal policy/manifest channel, and a target strictly
newer than current according to `SemanticVersion`. Production never changes channel to chase a newer file.

The candidate window is **02:00 inclusive to 04:00 exclusive in the workstation's current local zone**. Each
evaluation uses `ZonedDateTime` local calendar fields rather than adding 24 hours, so missing/repeated hours and
timezone changes are evaluated from reality at wake-up. The conservative idle threshold is **30 minutes** using
the Phase 5A observer's optional last foreground activity `Instant`. Exactly 30 minutes passes; less, future-skewed,
or absent evidence defers. A visible/foreground Shale window also defers even if the timestamp is old. No activity
history is stored.

Idle is insufficient. Any Phase 11B safe-work lease, open/dirty or otherwise uncertain mutation editor, save in
flight, unavailable cooperative no-prompt shutdown, another process, or occupied update lock must defer. The
existing lease count can supply active-work evidence, but there is no trustworthy global dirty-editor readiness
signal or cooperative unattended shutdown today, so a production caller must supply shutdown unavailable. The
resolver exposes one immutable reasoned decision rather than scattered booleans.

The retry foundation permits at most four evaluations per local-date/time-zone window, conceptually at initial
wake then 30-minute intervals, and at most one real handoff. Offline policy/manifest/package uncertainty is an
eligibility deferral, creates no attempt, and may use those bounded evaluations. A terminal real attempt is not
relaunched that window. A different local date or changed zone creates a new window; counters are not activity
history and no persistence is introduced in this phase.

### Execution, lifecycle, security, and packaging findings

Phase 12 begins only when the eligible evaluator crosses the handoff consumer seam; scheduler wake-ups and all
deferrals create nothing. An eventual handoff must reuse `DesktopUiUpdateLauncher` and existing updater validation.
The downloader enforces configured SHA-256 and extraction rejects path traversal. Source inspection found no
runtime Authenticode verification of the updater executable or archive/MSI; do not claim it. The in-app Windows
path applies the ZIP, not MSI, and the per-user install is expected to run as that user without UAC, but installed
confirmation is still required. No code bypasses UAC.

Locked is not equivalent to safe: the same activity/work/shutdown/lock gates remain. Closed and logged-out
execution is unsupported because authenticated policy, install-owner identity, preference access, and safe process
coordination cannot all be established without credentials or a larger service/API design. No password, JWT/JTI,
session secret, user, tenant, email, machine UUID, activity history, PHI, or case data enters evaluator/retry state.
Routine evaluation adds no central audit row; a real handoff retains Phase 12. There is no API, SQL, schema, or
central persistence change.

Automatic reboot and force termination are forbidden. The current updater's force-stop makes unattended handoff a
blocker rather than an allowed capability. The ZIP updater has no MSI reboot-required result; if a future installer
returns one it must be recorded/reconciled without reboot. Existing successful-restart semantics remain unchanged
and no credential is persisted.

Because no task/helper/state is installed, upgrade and uninstall have nothing new to update, duplicate, or remove;
Phase 13A preference retention and N-1 behavior are unchanged. A future approved task must be a single stable
current-user `Shale\\Automatic Update` identity, point to a stable installed path, contain no credentials, reread
preference/policy on every run, update idempotently, and be explicitly removed on uninstall. macOS launchd parity,
production rollout, configurable schedule UI, policy administration, a public policy endpoint, privileged daemon,
force-kill, and workstation reboot are explicitly outside Phase 13B.

Audit compatibility review: evaluation is local non-PHI operational computation and no domain/administrative
mutation or sensitive read is added. Existing Phase 12 local operational evidence begins only at updater handoff.
No audit schema, event, allowlist, or SQL migration is appropriate.

The exact installed-Windows acceptance checklist and the 2026-09-30 validation-run evidence are in
`docs/testing/windows-unattended-update-phase13b.md`. **Full repository `mvn test`: PASS** (supplied after execution outside the previously restricted runner), so
automated Java/Maven verification is no longer a blocker. The available validation runner was
Linux rather than an installed Windows workstation; it could not inspect `%ProgramData%` ACLs, the MSI/install
tree, UAC, Windows accounts, Task Scheduler, foreground/session-lock behavior, or updater execution. The earlier restricted-runner Maven Central HTTP 403 is retained only as historical context and does not
downgrade the supplied PASS.

### Installed-Windows evidence table

Only the Maven row is newly established by supplied execution evidence. Runtime-only rows remain `NOT TESTED`;
static blockers remain `FAIL`. This distinction prevents Linux/source inspection from becoming Windows PASS.

| Scenario | Result | Evidence / limitation |
|---|---|---|
| Maven full suite | PASS | Full repository `mvn test` passed outside the previously restricted runner. |
| Installation scope/path | NOT TESTED | No installed Windows Shale instance is available; executable path, owner, effective write, and genuine per-user scope await the PowerShell transcript. |
| Updater path | NOT TESTED | Installed primary/legacy candidate and cross-version stability require the real installation and an upgrade observation. |
| ProgramData ACL | NOT TESTED | `%ProgramData%\Shale` owner, inheritance, ACEs, and effective current-user access require Windows. |
| Phase 13A persistence | NOT TESTED | Disable/enable plus restart sequence has not run on the installed application. |
| Second Shale-user behavior | NOT TESTED | Shale-user switch has not been observed on the installed application. |
| Cross-Windows-user behavior | NOT TESTED | Test only with a readily available second Windows account. |
| UAC | NOT TESTED | Updater launch and installed-owner privilege behavior require Windows observation. |
| ZIP/MSI execution | NOT TESTED | ZIP application and any otherwise-required MSI path have not been observed; do not install solely for validation. |
| Recent activity | NOT TESTED | No installed JavaFX Windows session; deterministic contracts are not runtime evidence. |
| Foreground app | NOT TESTED | No installed foreground-window observation; supplied `foregroundVisible=true` defers in the pure evaluator. |
| New Intake active work | NOT TESTED | Static lease presence is not substituted for installed lifecycle/eligibility observation. |
| Case edit | FAIL | Static inspection finds no reliable aggregate readiness signal; installed behavior remains to be observed and safety is UNKNOWN / DEFER. |
| Contact edit | NOT TESTED | Static lease presence is not substituted for installed lifecycle/eligibility observation. |
| Organization edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Task edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Calendar edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Dirty-state coverage | FAIL | Editor-local state is not a reliable global inspect-only readiness answer. |
| Save-in-flight coverage | FAIL | Save-in-flight cannot currently be proven globally; unattended handoff must defer unless stronger readiness is introduced. |
| Cooperative shutdown | FAIL | No aggregate inspect-only prompt/loss readiness exists; Windows updater force-stops Shale. |
| Cross-process update lock | FAIL | No `FileLock`, `FileChannel.tryLock`, named mutex, or equivalent protects desktop handoff/updater application. |
| Phase 12 ordering | PASS | Deterministic contract places the attempt at actual handoff; every eligibility deferral, including unavailable shutdown/lock, produces no attempt. No installed handoff was run. |
| Offline behavior | NOT TESTED | Installed authority/package loss and bounded retry observation remain outstanding. |
| Retry/window contract | PASS | Deterministic contracts retain 02:00 inclusive/04:00 exclusive, four evaluations, 30-minute nominal spacing, one handoff per local-date/zone window, and date/zone reset. |
| Package integrity | NOT TESTED | Installed package has not run; source has configured SHA-256 and traversal checks but no runtime Authenticode validation. |
| Windows lock screen | NOT TESTED | No Windows interactive session is available. |
| Logged-out behavior | DEFER/UNSUPPORTED | Phase 13B in-process architecture does not support logged-out execution. |
| Reboot | NOT TESTED | No installed update ran; source has no automatic reboot and ZIP has no MSI reboot-required result. |
| Production scheduler activation | DEFER/UNSUPPORTED | No timer, recurring executor, task, service, daemon, helper, or startup registration is activated. |


Static validation confirmed the two previously documented activation blockers rather than supplying contrary
Windows evidence. The updater still force-stops every `Shale.exe` with `taskkill /F`; ordinary JavaFX exit has no
inspect-only aggregate answer for active, dirty, prompt-requiring, or saving editors. Phase 11B leases positively
observe New Intake, New Organization, and Contact edit lifetimes, but Case, Organization, Task, and Calendar/event
editors do not expose a reliable aggregate readiness signal to Phase 13B. No shared OS-backed update lock exists at
desktop handoff, updater startup, or install application. The evaluator can represent both facts as deferrals, but
no production caller supplies them and no runtime scheduler is wired. Therefore unsafe handoff remains unreachable,
no speculative readiness/lock/invocation-mode implementation was added without installed-Windows evidence, and
Phase 12 attempt ordering remains unchanged.

**Phase 13B remains IN PROGRESS.** Its concrete remaining work is installed per-user Windows execution of the
checklist, followed by only those narrow corrections proven necessary there. Production activation continues to
require either validated prompt-free cooperative readiness plus a per-install-owner OS lock, or an unconditional
`DEFER/UNSUPPORTED` result for those capabilities. Do not begin another roadmap phase until this validation closes;
the exact next recommended scope is continuation of Phase 13B on an installed Windows workstation, not scheduler
activation.

### Phase 13B hardening completion — installed Windows 1.0.129 evidence

Installed acceptance established a per-user installation at `C:\Users\Curtis and Lucero\AppData\Local\Shale\`, with `Shale.exe` at the installation root and `app\updater\ShaleUpdater.exe` as the updater. The running runtime/SemanticVersion authority reported **1.0.129**; Add or Remove Programs `DisplayVersion` can remain older and is not runtime version authority. Phase 13A persistence, cross-Shale-user machine scope, non-admin mutation protection, and ordinary `BUILTIN\Users` read/no-write access on the existing preference file passed. Both executables were `NotSigned`; signing remains explicitly deferred production hardening.

Installed workflow evidence was: New Intake blocks the parent, confirms discard, and preserves data when discard is cancelled; Case field edit blocks the parent but has no child discard confirmation; Organization edit blocks the parent and confirms discard; Task edit blocks the parent but has no child discard confirmation; and Calendar/Event edit blocks the parent but has no child discard confirmation. **An open mutation window is therefore sufficient to make unattended shutdown not ready; dirty-state perfection is not required.** Contact create/edit and obvious equivalent substantive editors use the same registry category. Existing close/prompt behavior is unchanged.

`SafeWorkDrainCoordinator` is now the single session-scoped mutation authority for Phase 11B leases and Phase 13B readiness. Its inspect-only result is `READY`, `ACTIVE_MUTATION_WORKFLOW`, `SAVE_IN_FLIGHT`, `PROMPT_REQUIRED`, or `UNKNOWN`; only `READY` permits a future cooperative request. `READY` means evidence is known, no substantive mutation lease is open, no registered save is executing, and no known prompt-only application condition exists. Unknown evidence always defers. Lease and save handles are idempotent `AutoCloseable` registrations, supporting exception-safe save/cancel/X-close lifetimes and simultaneous workflows without a second drifting counter. A narrow `CooperativeShutdownCoordinator` rechecks readiness on dispatch and invokes only the injected normal JavaFX lifecycle; it never closes children, answers prompts, saves, launches an updater, or terminates a process. It has no production scheduling caller.

The shared execution lock is `%LOCALAPPDATA%\Shale\updates\update-execution.lock` on Windows (the analogous established per-user application-support path elsewhere). `FileChannel.tryLock()` is authority; an empty stale file does not block after OS lock release and stores no identifiers, versions, credentials, or activity. The desktop obtains a short handoff lock **before** creating a Phase 12 attempt or launching a process. Busy returns `A Shale update is already in progress.` with no attempt and no launch. The launched updater waits only for that deliberate handoff, then independently acquires and retains the same lock across package execution; independently launched updaters perform a non-waiting acquisition and only one proceeds. Thus ordering is eligibility → cooperative readiness → execution availability → handoff begins → Phase 12 attempt begins → updater owns execution lock.

Invocation mode is additive: absent/old arguments remain `MANUAL`; desktop currently passes `MANUAL`. Manual Windows force-stop compatibility remains. `UNATTENDED` returns/defer before `stopRunningApp` can call the manual `taskkill /F` implementation, so unattended code cannot force-kill Shale. No unattended caller, timer, task, service, daemon, startup executor, credential, logged-out support, SQL migration, or API change was added. Audit review found only local non-PHI coordination, not a domain/administrative mutation; existing Phase 12 operational evidence remains the audit-compatible handoff record.

Phase 13B is **implemented but remains unverified as a feasibility/foundation phase** until the required Maven suites pass. This does not activate or declare production unattended updates ready. Signing remains deferred, logged-out execution remains unsupported, and production scheduling remains disabled. The exact recommended next phase is **Phase 13C — installed-Windows activation design and acceptance for an opt-in in-session scheduler**, which must be separately authorized and must consume these readiness and lock contracts without widening into logged-out execution.

## Phase 13C implementation record — 2026-09-30

**Status: IN PROGRESS — Windows opt-in in-session scheduler is implemented; installed-Windows activation validation is outstanding.**

### Supported production scenario and lifecycle

Phase 13C supports exactly one scenario: Shale is already running under an authenticated Windows desktop session,
the workstation preference is enabled, and the process remains running into the local overnight window. One
process-scoped `InSessionAutomaticUpdateScheduler` starts after the authenticated shell, Phase 11 policy coordinator,
Phase 5A foreground observer, and Phase 13A provider exist. Logout, user switch, and application shutdown stop it;
generation tokens make callbacks retained by an old session inert, and repeated initialization is idempotent.
Eligibility and manifest/network work run on its single daemon executor; the final shell interaction is dispatched
to JavaFX. Logs are low-volume semantic start/evaluation/defer/handoff/error events and contain no identity, token,
activity history, case data, PHI, or location.

If Shale is closed, its process is terminated, Windows is logged out, or the computer sleeps through the whole
window, no automatic update occurs. There is no Task Scheduler registration, Windows Service, SYSTEM execution,
stored credential, detached helper, anonymous policy access, wake timer, automatic reboot, macOS scheduler,
configurable schedule, tenant/user preference, new API, SQL migration, or fleet management. A Windows lock screen is
not treated as evidence of safety. Phase 13C is an authenticated in-process coordinator, not a background system
updater.

### Timing, consent, and authority

The timer derives candidates from the current local date, time, and zone rather than adding 24 hours. The window is
02:00 inclusive through 04:00 exclusive. A delayed callback recomputes current local time: resume or JVM delay inside
the window performs an ordinary evaluation, while a callback after 04:00 schedules the next local window and never
updates at (for example) 09:00. DST and timezone identity use `UnattendedUpdateRetryPolicy.WindowId`. No wake timer is
created. Each eligible-window deferral may be reevaluated at the existing nominal 30-minute interval, with at most
four evaluations and one real handoff per window.

Every scheduled evaluation calls `UnattendedUpdateEvaluationService`; the scheduler contains no duplicate gate
policy. `WorkstationUpdatePreferenceProvider.current()` is read at evaluation and again for the immediate recheck,
so disabling while waiting prevents a later launch without restart. Enabling merely participates at the next
scheduled candidate and never triggers an immediate daytime check. Once a real updater handoff begins, preference
changes do not cancel it; Phase 12/updater lifecycle owns it. The current Phase 11 policy presentation and a freshly
fetched Production manifest/package selection are read at evaluation time. Stale/unavailable policy, offline
manifest, target/channel mismatch, absent compatible Windows ZIP, unsupported/non-writable installed path (including
a path expected to require elevation), or a busy update lock safely defer.

### Safe handoff and boundaries

The Phase 13B evaluator requires the established 30-minute foreground-human-activity evidence, no active foreground
Shale window, aggregate `READY` shutdown state, no mutation lease, no save in flight, and the shared OS execution lock.
`PROMPT_REQUIRED`, `UNKNOWN`, recent activity, foreground visibility, active mutation, save, or unavailable lock always
defers with no Phase 12 attempt. Eligibility rereads all volatile inputs immediately before the handoff callback; the
JavaFX callback again checks activity, foreground, aggregate readiness, and lock immediately before launch. It never
closes child windows, automates prompts, discards or saves work, waits indefinitely, or force-terminates Shale.

The automatic launcher passes explicit `UNATTENDED`; manual Settings/update actions continue through `MANUAL` and are
independent of preference, scheduler state, and automatic window. The shared lock is probed during eligibility and
acquired authoritatively before attempt creation/process launch. Only the real launch creates the Phase 12 attempt;
pure scheduler deferrals do not. Updater launch is evidence, not completion: Phase 12 startup reconciliation remains
the only completion authority, and Phase 11B enforcement remains until an allowed version actually runs or policy is
corrected. The unattended updater mode cannot call `taskkill /F`; inability to close cooperatively aborts rather than
forcing termination. The in-app path remains ZIP overlay, not unattended MSI.

Retry counters are process-memory-only and contain only local window identity and bounded counters; they persist no
activity, identity, tenant, machine, token, case, or PHI data. A process restart resets those counters. The durable
Phase 12 pending-attempt evidence and independently enforced update lock remain the minimum duplicate-execution
protection; Phase 13C does not add scheduler persistence. This limitation must be re-evaluated only if installed
activation evidence proves those safeguards insufficient.

Settings now says that eligible updates are evaluated only while Shale is left running and idle, and explicitly says
closed, logged-out, and sleeping computers are not woken. It adds no schedule controls. `Shale.exe` and
`ShaleUpdater.exe` remain NotSigned; Authenticode signing and verification are production-hardening debt and are not
claimed by this phase.

### Validation and roadmap boundary

Deterministic tests cover lifecycle idempotence/stale generations, before/at/inside/after-window timing, DST local
window arithmetic, resume behavior, preference rereads, four-evaluation/one-handoff budgets, immediate activity-race
recheck, shared-lock ordering, and exact `UNATTENDED` command wiring. The installed-Windows Phase 13C checklist is in
`docs/testing/windows-unattended-update-phase13c.md`. Until that checklist validates the packaged build and the
required Maven suite passes, Phase 13C remains **IN PROGRESS** and must not be represented as production-complete.

**NOT STARTED — NEXT PROPOSED STEP:** Phase 13D, a separately authorized Windows logged-out scheduling feasibility and
security design. It must not be inferred from, or included in, Phase 13C.

### Phase 13C audit-compatibility review

Phase 13C adds no database/domain mutation, sensitive read surface, administrative preference mutation, or server API.
It consumes the existing machine preference, authenticated policy read, public package manifest, process-local safety
signals, and local Phase 12 attempt evidence. Therefore no PHI-read or entity-action audit row is appropriate and no
schema migration is required. The existing Phase 13A administrative preference boundary remains unchanged; scheduler
timer ticks and eligibility deferrals are operational diagnostics rather than tenant audit events. Logs remain
sanitized and never serialize policy DTOs, activity timestamps/history, identities, credentials, case values, or
exception text.

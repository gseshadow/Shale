# Application Release and Session Management Architecture

**Status:** Phase 2B complete; Phase 3A not started — next proposed step

**Last reviewed:** 2026-09-28

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

This transport is appropriate as an accelerator for `SESSION_REVOKED`, `POLICY_CHANGED`, or
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

### Phase 4A — Stable machine identity

* **Goal:** create/persist a non-invasive workstation UUID.
* **In scope:** platform storage abstraction, installer ACL/location validation, read/create concurrency.
* **Non-goals:** server reporting, hardware fingerprint, settings UI.
* **Likely files:** `shale-core` platform paths, `shale-desktop`, packaging scripts/tests/docs.
* **Schema/API impact:** none/none.
* **Verification:** persistence across users/restarts/in-place update, atomic creation, permissions, uninstall.
* **Dependencies:** machine-ID location decision.
* **Risks:** per-user duplication or unwritable system directory.

### Phase 4B — Application-instance schema and service foundation

* **Goal:** model desktop process instances independently from sessions.
* **In scope:** strict tenant schema/RLS, lifecycle service, explicit end reasons, retention indexes.
* **Non-goals:** heartbeat scheduler, activity capture, admin UI.
* **Likely files:** migrations, core/data/server ports and tests.
* **Schema/API impact:** additive tenant table and narrow create/end API.
* **Verification:** RLS, idempotent end, session nullable/reference integrity, abnormal timeout semantics.
* **Dependencies:** 4A and tenant enrollment/auth approach.
* **Risks:** treating missing heartbeat as a known clean exit.

### Phase 5A — Human-activity signal

* **Goal:** define a throttled foreground-input timestamp separate from liveness.
* **In scope:** desktop activity observer and unit/lifecycle tests; no network traffic.
* **Non-goals:** key content capture, background activity, heartbeat.
* **Likely files:** `shale-ui` lifecycle boundary, `shale-desktop` adapter/tests.
* **Schema/API impact:** none/none.
* **Verification:** real input advances; timers, refreshes, polling do not; logout removes observers.
* **Dependencies:** 4B contract.
* **Risks:** privacy overreach or event-filter leaks.

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

### Phase 6 — Administrator version/instance visibility

* **Goal:** authorized tenant admins can view recent instances and version distribution.
* **In scope:** paged safe read API/service and existing Settings-pattern UI.
* **Non-goals:** remote control, sessions, location map, updater scheduling.
* **Likely files:** server/core/data, `shale-ui` settings (and later web), audit-read review/tests.
* **Schema/API impact:** read-only; additive endpoint.
* **Verification:** admin and cross-tenant denial, stale labeling, pagination, no secret exposure, UI tests.
* **Dependencies:** 5B.
* **Risks:** machine-name/IP privacy and expensive unbounded queries.

### Phase 7A — Durable user-session schema

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

* **Goal:** notify connected clients promptly after authoritative commits.
* **In scope:** PHI/secret-free invalidations and immediate revalidation.
* **Non-goals:** using push as authority or adding durable replay.
* **Likely files:** existing LiveBus dispatcher/publisher/server integration and tests.
* **Schema/API impact:** none; additive event types.
* **Verification:** missed/duplicate/wrong-tenant events, commit-before-publish, reconnect authority.
* **Dependencies:** 8A and policy read API.
* **Risks:** broad tenant events exposing session existence; prefer safe targeted routing.

### Phase 9 — User Devices & Sessions UI

* **Goal:** users view/revoke current and other sessions.
* **In scope:** current marker, client/device, activity, nullable approximate location, revoke one/others.
* **Non-goals:** admin firm view, GPS, exact location, instance equivalence.
* **Likely files:** JavaFX Settings/account surface, server APIs; web/mobile later reuse contracts.
* **Schema/API impact:** none beyond Phase 8; no breaking changes.
* **Verification:** self-only authorization, current-session handling, nullable fields, accessibility/visual QA.
* **Dependencies:** 8A.
* **Risks:** misleading location/activity and self-lockout UX.

### Phase 10 — Administrator session visibility and revocation

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

### Phase 12A — Update-attempt schema and API

* **Goal:** durable sanitized history.
* **In scope:** strict tenant table/RLS, start/complete/idempotency API, retention and result taxonomy.
* **Non-goals:** updater modification and log upload.
* **Likely files:** migrations, core/data/server, tests.
* **Schema/API impact:** additive table/endpoints.
* **Verification:** RLS, idempotency, abandoned attempt classification, sanitization.
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
* **Likely files:** platform storage/installer and later Settings UI.
* **Schema/API impact:** likely local machine setting; optional reported flag only.
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
| 3A | **NOT STARTED — NEXT PROPOSED STEP** | Strict tenant-owned user release-state foundation only. |
| 3B-13B | **NOT STARTED** | Start only after predecessors and listed decisions are satisfied. |

Status vocabulary: **NOT STARTED**, **IN PROGRESS**, **COMPLETE**, **BLOCKED**. Later Codex runs must
update this table and the applicable phase section.

## 16. Open decisions requiring operator input

1. **Global control-plane ownership:** which database/schema and operator identity may mutate global
   releases/policy? Tenant administrators should not receive this authority by default.
2. **Machine UUID lifecycle:** approve system-wide locations/installer ACLs and decide whether uninstall
   preserves identity (useful for continuity) or removes it (privacy/fresh-install semantics).
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

Implement **Phase 3A only**: add the strict tenant-owned
`UserReleaseState` schema/RLS foundation and narrowly scoped service operations for reading and monotonically
advancing per-user release acknowledgement. Include transactional concurrency and cross-tenant tests. Do not add
the Phase 3B What's New UI, client consumption, rich media behavior, updater/manifest changes, policy evaluation
or enforcement, instances, sessions, heartbeat, PubSub, or geolocation.

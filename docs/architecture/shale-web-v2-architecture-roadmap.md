# Shale Web V2 architecture and phased implementation roadmap

**Reviewed:** 2026-10-08. **Inventory/documentation:** COMPLETE. **Decision acceptance:** OPEN.
**Web V2 implementation:** Phase 2A foundation, Phase 2B authenticated shell and Phase 2C My Shale presentation adoption IMPLEMENTED FOR REVIEW; Phase 2D acceptance review and targeted feedback fix RECORDED; ACCEPTANCE OPEN.
Phase 2 remains IN PROGRESS; acceptance OPEN. Phase 3 IN PROGRESS — Phase 3A safe login return-path restoration and Phase 3B startup
verification uncertainty/recovery IMPLEMENTED FOR REVIEW. Phases 4–10 NOT STARTED. The user explicitly authorized this bounded
Phase 3 slice while Phase 2 acceptance remains open; that sequencing does not mark Phase 2 complete.
Documentation completion is not implementation completion.

**Evidence baseline:** live `origin/codex/latest`, `32e2cfdd25ad475bc03fbb91763c9769ce2ccd6a`.
[PR #1837](https://github.com/gseshadow/Shale/pull/1837) was merged into that base.
Repository source and tests establish the implementation inventory below; they do not establish which
binaries, SQL migrations, CORS settings, Function grants, or browser assets are deployed or working.
No runtime, database, Azure, or device acceptance was performed for this documentation task.

## 1. Purpose, authority, and evidence labels

Replace the beta within `shale-web` with a first-class responsive React/TypeScript client sharing
Shale's desktop identity and business workflows. Later iOS/Android delivery should reuse that UI through
small platform boundaries. This roadmap owns web client composition, responsive parity, and sequencing.
It does not replace domain, session, audit, tenancy, or deployment authorities.

* **VERIFIED** means found in the pinned repository source/test contracts; tests were inspected, not run here.
* **PROPOSED** means a recommendation for a later implementation task, including all new dependencies.
* **OPEN** means an explicit product/security/technical decision or verification still needed.
* **FUTURE / NOT STARTED** means no Web V2 implementation is claimed.

Ordinary web REST delivery depends on the selected operation's verified authorization, tenant isolation,
validation, concurrency, required audits, and stable contract. It is independent of MCP, AI OAuth,
integration registration, delegation grants, and AI-specific attribution. Authorized web mutations do
not wait for the AI read-only rollout. Integration credentials must never fall back to ordinary user
session authority. See [shared-operation alignment, section 3.1](ai-integration-and-mcp.md).
A full desktop migration from JDBC to HTTP is separate work, not a Web V2 prerequisite.

### Owning references reviewed

| Authority | Use in this roadmap |
| --- | --- |
| [Prompt rules](../../architecture/codex-prompt-rules.md), [development rules](../../architecture/development-rules.md), [system overview](../../architecture/system-overview.md) | Documentation routing, module boundaries, reuse, local verification and mandatory audit review. |
| [Design system](../../architecture/design-system.md), particularly A.2 and later semantic controls/selector guidance; [theme infrastructure](../../architecture/theme-infrastructure.md) | Current desktop visual identity supersedes early glass-layer wording where it differs. |
| [Tenancy/RLS](../../architecture/tenancy-and-rls.md), [schema](../../architecture/database-schema.md), [historical RLS audit](../tenant-rls-audit-2026-06-29.md) | Principal initialization, explicit predicates, live verification limits; RLS is not user authorization. |
| [Step 2 inventory](../web-api-migration-step-2.md), [Step 3 server](../web-api-step-3.md), [API readiness](../web-api-azure-readiness.md), [local smoke guide](../web-api-local-smoke-test.md) | Reusable ports/adapters, bearer contracts, errors, OpenAPI and historical-stage distinctions. |
| [Release/session roadmap](application-release-session-management.md), later 7B/8A/8B/9/10 records and closeouts | Durable WEB sessions; desktop remembered credentials are a separate capability. |
| [Live updates](../../architecture/live-update-architecture.md) | Invalidation lifecycle; verify actual source envelope and group rather than early examples. |
| [Case Dates](../../architecture/case-dates.md), [runtime cutover](../../architecture/case-dates-runtime-cutover-inventory.md), [presentation cutover](../../architecture/case-date-presentation-cutover-design.md), [Calendar unification](../../architecture/case-date-calendar-unification.md), [confirmation](../../architecture/field-confirmation.md) | Occurrence authority, later SOL/TCN lifecycle cutover, presentation precedence and transaction rules. |
| [Lookup overlays](../../architecture/customizable-lookup-types.md), [Case Team](../../architecture/case-team-roles.md), [firm-wide roles](../../architecture/firm-wide-roles.md), [case lifecycle](../../architecture/case-deletion-restoration.md) | Stable identities, separate role concepts, history and restoration. |
| [Contacts](../../architecture/contact-management.md), [Organizations](organization-management.md), [Materials](../../architecture/case-materials.md), [summary/projection inventory](../../architecture/case-summary-projection-inventory.md), [search suggestions](universal-search-suggestions.md) | Later implementation records and source supersede early phase inventories; no new client business-rule authority. |
| [Web deployment](../shale-web-deployment.md), [App Service guide](../azure-app-service-deployment.md), [web README](../../shale-web/README.md) | Existing Azure static-client/API approach, compatibility, preview and rollback planning. |

## 2. Verified implementation inventory and disposition

Paths below are relative to the repository root unless linked. Symbol names make the evidence searchable.

### 2.1 Browser client

| VERIFIED evidence | Current capability/limit | Disposition |
| --- | --- | --- |
| [package.json](../../shale-web/package.json), [main.tsx](../../shale-web/src/main.tsx) | React, TypeScript, Vite, React Router DOM 7; StrictMode entry. Vitest/jsdom/Testing Library exist. Several packages use `latest`, so this is not a claim of pinned future versions. | RETAIN project/toolchain and existing router; future dependency/version review only in its implementation task. |
| [App.tsx](../../shale-web/src/App.tsx): `AppRoutes`, `ProtectedRoute`, `AppShell` | One roughly 2,950-line file holds auth, shell, routes, lists/details, forms, utilities and primitives. Local state/effects load server data with some `isCurrent` guards; no centralized server-state cache. | REFACTOR by concern, preserve usable workflows during cutover. |
| `App.tsx`: `EntityCard`, `EntityList`, `MetadataGrid/Row`, `PageHeader`, `SearchBar`, `FilterBar`, `StatusPill`, `EmptyState`, `ActionButton`, `SecondaryButton` | Reusable vocabulary already exists, but is file-local and tied to beta CSS. | EXTRACT/adapt into shared web primitives; review semantic HTML, keyboard behavior and A.2 tokens rather than starting another card system. |
| [ContactValueInput.tsx](../../shale-web/src/ContactValueInput.tsx), [its tests](../../shale-web/src/ContactValueInput.test.tsx) | Server-backed phone/email preview on blur, draft preservation, generation guards, field errors and `RETAIN/SET/CLEAR`; unchanged legacy values are preserved. Duplicate errors survive advisory validation until authoritative Save. | RETAIN shared parsing endpoint and semantics; extend accessibility/mobile composition through the same component. |
| [api.ts](../../shale-web/src/api.ts) | Manually maintained interfaces; repeated `fetch`, bearer headers and JSON casts. `VITE_SHALE_API_BASE_URL` overrides a hard-coded Azure default. `ApiError` includes status and field errors; 404/409 handling varies. No shared cancellation, refresh coordinator or runtime response validation. | REFACTOR transport and typed feature contracts incrementally. Do not claim TypeScript casts validate JSON. |
| `api.ts`: `storeAccessToken/readAccessToken/clearAccessToken`; `AppRoutes` | Opaque bearer stored per tab in `sessionStorage` under `shale-web.accessToken`; startup calls `/me`. Phase 3B clears startup credentials only on confirmed `/me` 401; uncertain verification retains storage, blocks content/feature requests, and offers explicit Retry or local Return to sign in. Login TTL is not coordinated into browser refresh; no refresh call exists in this file. Logout clears locally before fetching server logout, whose HTTP status is not checked. | RETAIN bearer protocol, REPLACE lifecycle handling in phase 3; storage tradeoff needs explicit review. |
| `App.tsx` and `api.ts` mutation handlers | Browser already creates cases, tasks, updates, Contacts and Organizations; edits case core/assignment/status, task detail/completion, Contact and Organization details. | RETAIN verified contracts where safe. Historical “read-only beta” descriptions are obsolete for source capabilities; names such as `ApiReadController` and `*ReadOnly` components do not prove read-only behavior. |
| [styles.css](../../shale-web/src/styles.css) | Beta styling and responsive rules exist; browser uses Inter/system sans stack. | REPLACE incrementally with canonical web tokens and shared component styles; investigate actual visual parity through later screenshots/device review. |

**Existing browser URL routes (VERIFIED):** `/` redirect, `/login`, `/my-shale`, `/cases`,
`/cases/:caseId`, `/tasks`, `/tasks/:taskId`, `/contacts`, `/contacts/:contactId`, `/organizations`,
`/organizations/:organizationId`, `/team`, `/team/:userId`, `/settings`. Unknown URLs redirect.
There are no current Calendar, Reports, unified Search, or case subsection URL routes in `AppRoutes`.
These future routes must not be advertised as implemented. Current create/edit forms are local UI state,
not canonical subsection routes. `ProtectedRoute` passes the current location in navigation state;
`AppRoutes` now owns the successful-login replacement through `redirectPathFrom`, validating the
existing protected route allowlist and preserving pathname/search/hash. `LoginPage` verifies login
through `/me` before installing auth; it no longer issues a competing navigation. Explicit logout
replaces the current entry with `/login` and clears return state. [Phase 3A review](../../shale-web/docs/phase-3a-review.md)
records reproduction, safety rules and synthetic evidence. Future route/filter policy remains separate.

### 2.2 Server, shared services, desktop

| VERIFIED evidence | Current capability/limit | Disposition |
| --- | --- | --- |
| [ApiReadController](../../shale-server/src/main/java/com/shale/server/controller/ApiReadController.java) | Existing REST reads and writes listed in section 7; validations and server-derived tenant/user. Case detail returns Java `Object`, limiting concrete schema quality. No inspected generic Dates/confirmation, Calendar, Reports, Links/Materials/Timeline or role-admin REST routes. | RETAIN/extend per operation; formalize DTO schemas and policy before exposing gaps. |
| [ShaleServerServiceConfiguration](../../shale-server/src/main/java/com/shale/server/config/ShaleServerServiceConfiguration.java) | Spring composition uses core/data without JavaFX. `prod/azure` use bearer; dev/local identity headers are development-only. | RETAIN composition, never send simulated identity headers from production browser. |
| [core service directory](../../shale-core/src/main/java/com/shale/core/service), [data adapters](../../shale-data/src/main/java/com/shale/data/service/adapter) | JavaFX-free Case, Task, Contact, Organization, User, auth, notification and Material ports exist. `CaseServiceAdapter` reaches authoritative Case Date/aggregate workers; unsupported port defaults throw in many newer contracts. | RETAIN; verify production delegation, actor policy and transaction ownership per use case. A port's existence is not proof of REST exposure or accepted security. |
| [RequestScopedDbSessionProvider](../../shale-server/src/main/java/com/shale/server/runtime/RequestScopedDbSessionProvider.java), data `RuntimeSessionService` | Principal-derived `ShaleClientId`/`PrincipalUserId` initialize runtime connections; explicit tenant/deletion predicates supplement RLS. Current setup resets keys on each borrow, without `@read_only=1`. | RETAIN request scope/pooling semantics; no shared mutable tenant context or privileged auth connection for domain reads. |
| [AuthController](../../shale-server/src/main/java/com/shale/server/controller/AuthController.java), [ServerAuthSessionService](../../shale-server/src/main/java/com/shale/server/runtime/ServerAuthSessionService.java), [DurableSessionTokenValidator](../../shale-server/src/main/java/com/shale/server/runtime/DurableSessionTokenValidator.java), `SqlDurableSessionStore` | Login/me/refresh/logout exist. New active-profile login creates SQL-backed `WEB` session and bound JWT `sid/jti`; SQL owner, expiry, revocation and current JTI are checked. Refresh conditionally rotates the same session. Limited legacy unbound-token compatibility remains. | RETAIN; browser refresh and session management UI are future work, not missing server foundations. |
| [SessionController](../../shale-server/src/main/java/com/shale/server/controller/SessionController.java), [AdminSessionController](../../shale-server/src/main/java/com/shale/server/controller/AdminSessionController.java), [SessionManagementService](../../shale-server/src/main/java/com/shale/server/runtime/SessionManagementService.java) | Self list/current/one/others revocation and tenant-admin list/revoke exist; current session derives from bound token. Admin listing defaults to 50, caps size at 100; self list is bounded to 100. Admin list and revocations have transactional session-security auditing. No beta browser consumer. | RETAIN; responsive cards can consume these in phase 7 (security boundary first in phase 3). |
| `OpenApiConfiguration`, [OpenApiDocumentationTest](../../shale-server/src/test/java/com/shale/server/controller/OpenApiDocumentationTest.java) | Springdoc `/v3/api-docs`, Swagger UI and bearer scheme exist. Case/contact search-page responses have `items/page/size/total`, with `total=null`. Controller fetches a prefix then slices in memory; Contact adapter can limit after DAO retrieval. | INVESTIGATE/harden bounded database paging, concrete response schemas and compatibility. Page-shaped HTTP is not database paging. |
| [main.fxml](../../shale-ui/src/main/resources/fxml/main.fxml), `MainController`, `SceneManager` | Desktop destinations: My Shale, Cases, Contacts, Organizations, Team, Reports, Calendar, Settings; global search, New Intake, notifications, profile/logout. Dedicated Tasks exists for direct/internal navigation; My Shale exposes My Tasks. | RETAIN information hierarchy; responsive global navigation must keep every destination discoverable. |
| `CaseController`, `NewIntakeController`, `CaseDetailService`, `CalendarService`, `ReportsController`, [CaseDocumentService](../../shale-ui/src/main/java/com/shale/ui/document/CaseDocumentService.java) | Desktop combines shared ports with UI orchestration and direct DAO/JDBC paths. Calendar reads `CalendarFeedDao`; Documents generates a Case Summary HTML/PDF through UI render/export flows. JDBC remains desktop domain authority even with durable HTTP enrollment. | EXTRACT only necessary non-UI orchestration for selected missing API use cases; retain authoritative DAO workers. Do not import `AppState`, JavaFX or desktop filesystem behavior into server. |
| [TaskServiceAdapter](../../shale-data/src/main/java/com/shale/data/service/adapter/TaskServiceAdapter.java), `TaskDao` | Creation plus optional assignment and update plus assignment are separate gateway calls. Completion has an actor argument at the port but gateway completion does not carry it. Existing title/description PHI calls use non-connection overloads. | INVESTIGATE/scoped transaction, actor, concurrency and audit work before V2 task mutations; do not label the whole adapter atomic. |

**Tests inspected for evidence and later reuse:** browser ContactValueInput tests; server AuthController,
SessionController, AdminSessionController, ApiReadController, ContactValueApi and OpenApiDocumentation tests;
data WebCaseCreationCaseDatesContractTest and Case Date/confirmation/presentation contracts; service adapters
and existing audit allowlists. Source-contract and mocked-controller tests are limited evidence; future
acceptance also needs authorized integration and two-tenant/non-dbo checks. No production or test file changes here.

No equivalent Web V2 authority was found among the existing architecture/web documents. Those documents
cover historical beta delivery, individual domains, sessions, or AI integration, so this document fills a
client-specific ownership gap without copying those roadmaps.

## 3. Proposed client organization and explicit technology decisions

Organize inside the existing `shale-web/src`; these directories describe future ownership, not scaffolding
created by this task:

```text
app/                  composition, route registry, providers, error boundaries
shell/                responsive global navigation, header, account/feedback surfaces
ui/                   tokens, typography, Button, Field, EntityCard, Dialog, Menu, states
features/             auth, my-shale, cases, tasks, contacts, organizations,
                      calendar, team, reports, search, settings
api/                  shared fetch transport, errors, contracts, feature endpoint clients
session/              verified session state, generation, refresh coordinator, credential port
platform/             browser capabilities and small replaceable interfaces
```

One route registry owns paths, navigation labels and availability. Features own endpoint clients/query keys,
view models, forms and entity presentation composed from `ui`. Shell owns layout, not business rules.
`ui` has no endpoint calls; `platform` has no domain authorization. Shared logic/components have responsive
compositions; no phone/tablet/desktop forks of every feature. Prefer CSS grid/flex, intrinsic sizing and
container queries where supported; use layout state only for real interaction differences. Lazy-load large
feature routes after stable composition, avoiding premature microfrontends or new shared packages.

| PROPOSED decision | Recommendation and reason | Future dependency/acceptance |
| --- | --- | --- |
| Routing | Retain React Router 7; move to an explicit route configuration and supported data-router APIs when navigation blocking is introduced. Nested entity sections preserve browser history and direct links. | Already installed. Verify installed version's blocker API and migration without lost routes in phase 3. |
| Server state | Adopt TanStack Query for request dedupe, bounded memory cache, cancellation and targeted invalidation. Keep forms/local interactions in React state. Do not store authenticated responses persistently. | New dependency decision for phase 3; review current compatible version. Query defaults must override automatic retries/refetch where audits, errors or drafts make them unsafe. |
| Forms | Retain simple native controlled forms; use React Hook Form for larger intake/aggregate editors to track dirty state and field errors. Server is authoritative. Prefer typed constraints/adapters; add a schema library only if measured contract/form reuse justifies it. | React Hook Form is a proposed later addition, not installed. Preserve `ValueUpdate` retain/set/clear, explicit null and opening concurrency witnesses. |
| Component foundation | Native semantic HTML with Shale CSS variables; selectively adopt unstyled Radix primitives for focus-managed dialogs/menus/popovers. No pre-themed Material/Ionic UI or broad visual kit. | Radix is a proposed addition only for needed controls after keyboard/screen-reader evaluation. Reuse current EntityCard vocabulary first. |
| Contracts | Keep typed feature clients initially; improve selected concrete OpenAPI responses, then evaluate generated TypeScript types with reproducible generation and compatibility review. | Generator/tool choice OPEN; do not generate a client against ambiguous `Object` or claim all interfaces match server. |
| Platform | Small capability ports, browser adapters initially. Inject credential storage separately from auth lifecycle. | No native/plugin/runtime dependency now; unsupported actions return a typed unsupported/cancelled/failure result. |

API transport owns base URL, Authorization, AbortSignal, status/body validation, safe error normalization
and session-generation checks. Feature clients own typed request/response mapping, not header repetition.
Separate validation errors (400), confirmed auth failure (401), forbidden operation (403), unavailable
entity (404), conflict (409), throttling, server and transport failures. Never show SQL/constraint/exception
text or log credentials, request bodies, sensitive queries, document bytes or signed URLs.

Query keys include session generation, tenant, actor, entity and normalized nonsensitive filter/paging context;
the cache is scoped to the verified identity. Cancellation and generation guards both matter: aborted browser
requests may already be executing on the server. An old request cannot populate a newer login's cache.
Use bounded stale/gc policies and narrow invalidation after successful mutations, including related summaries.
Audited detail reads need deliberate focus/reconnect behavior; do not blindly refetch all views on focus.
A mutation response may commit before a follow-up read fails: report “saved; refresh needed,” not “save failed.”
Disable mutation retry. A timeout/connection loss can mean the write committed; reconcile through an authorized
read and ask for deliberate resubmission only after outcome review. No assumed idempotency key exists today.

## 4. Web equivalents of the current Shale visual language

A.2 explicitly refines the earlier translucent/glass description: deep navy/blue chrome, pale blue canvas,
near-white functional surfaces, crisp text, blue actions and selective purple accents, moderate density,
without glow, glassmorphism, decorative waves or spreadsheet-like detail grids. Use current
[light](../../shale-ui/src/main/resources/css/theme/light.css) and
[dark](../../shale-ui/src/main/resources/css/theme/dark.css) semantic paint resources,
[content-components](../../shale-ui/src/main/resources/css/foundation/content-components.css),
[density](../../shale-ui/src/main/resources/css/foundation/density.css), buttons/forms/cards and shell resources
as the reference. Translate their meaning into CSS custom properties; do not copy JavaFX selectors or APIs.

| Desktop meaning/reference | PROPOSED web equivalent |
| --- | --- |
| Typography: page 24/800, section 18/800, subsection 15/700, body 14/400, value 14/600, metadata 12, label 11 | Named `rem` roles with semantic headings/labels and inherited system sans stack initially. Preserve hierarchy/weight; propose 16px editing inputs on phones to avoid keyboard auto-zoom. Validate readability rather than blindly reproducing tiny desktop labels. Font licensing/bundling decision remains OPEN. |
| 4/8/12/16/24 rhythm; comfortable 16 padding/12 gap, compact 12/8, dense 8/6 | Shared spacing/density variables; reading/forms comfortable, scanning cards compact, metadata/tables dense. Density never shrinks the touch hit area. |
| Canvas/content/section/card/elevated/input/overlay and text/border roles | One theme token vocabulary (`--shale-color-*`), shared geometry once. Initial Light examples: text `#10213d`, card `#f9fbfe`, link `#075fca`; Dark text `#f0f5fc`, card `#203249`. Use actual current resources for the complete mapping. |
| Cards 12–14 radius, inner controls 8–12; one restrained card shadow | EntityCard full/compact/inline/embedded variants and common section surfaces. Selected state adds border plus checkmark/text/ARIA, without replacing primary text with white on pale cards. |
| Primary/Secondary/Ghost/Danger/Navigation; Standard 40, Small 32; form shell 36 | Semantic Button props and native buttons/links. One Primary per local action area; Danger for confirmed destructive effects. Data/status color never paints the action/control shell. Touch hit regions expand to at least 44px while retaining intended visual density. |
| Status and Practice Area badge/pill/selector distinctions, stored colors | Read-only dot+label for dense metadata; filled labelled pills for prominent state; real accessible selectors for edits. Validate color and choose readable foreground against the actual fill; invalid/missing colors use theme-neutral fallback. IDs/DTOs remain authoritative, never display labels. |
| Dialog, menu, badges, feedback, stage tracker and Updates rail | Shared focus-managed Dialog/Sheet composition, keyboard menus, labelled badges, inline validation, status/alert feedback, connected labelled stages and reusable update cards. Gradients only for Primary actions, selected global navigation and current stage; never inputs, cards, page backgrounds or update content. |
| Light/Dark and personal Appearance | Both token sets supported and verified per V2 component. Current desktop `appearance.theme` uses UserPreferences; browser persistence API is missing, so initial preview/local session choice must not pretend to save that preference. Light is the unauthenticated safe default. |

Verify normal text contrast at 4.5:1 (large text 3:1), essential control/focus boundaries at 3:1, including
alpha compositing and dynamic database colors. Gradients need worst-point checks and visual review.
Always include text/shape meaning; color is supplementary. Dark token availability is not evidence every
legacy desktop or beta screen has completed dark acceptance. Browser focus, disabled, pending, invalid,
hover and pressed states belong to primitives, not page-local copies.

## 5. Responsive composition, navigation, and accessibility

Initial **PROPOSED** ranges: compact `<48rem`, medium `48rem–<75rem`, wide `>=75rem`.
Validate against content, translated/long labels, zoom, keyboard and real devices; these are not device
class guarantees. Container size may constrain a panel more than the viewport. No orientation lock.

| Layout | Shell and work composition |
| --- | --- |
| Compact | Single primary content column; labelled bottom destinations My Shale, Cases, Search, More (proposal). More opens a full discoverable destination list. Entity list navigates to full-page detail; sections use wrapping/scrollable navigation with visible overflow cues. Updates becomes a subsection rather than permanently stealing screen width. |
| Medium | Collapsible rail/drawer and labelled search; list/detail split only when both remain readable. Two-column forms only for independent short fields. Secondary case content moves below or into section navigation. |
| Wide | Stable labelled rail, global header/search/intake/account; list/detail and overview/updates rail where useful. Bound reading width for narrative/forms; wide tables only for genuine comparison tasks. |

**Destination mapping:** My Shale includes My Cases and My Tasks with visible tabs/links; `/tasks` remains
canonical task workspace/deep-link entry and `/tasks/:taskId` stays valid. Cases includes New Intake and
case sections. Contacts and Organizations have their own labelled entries in More and wider rails.
Team, Calendar, Reports and Settings are visible in More and wider navigation, not hidden only in Search.
Search is global, plus entity filters. Settings visibly separates Personal from authorized Administration;
role-gated actions must explain unavailable authority without inventing permission. Search or More must
not require knowing a workflow's name to discover it. Notifications/profile remain shell utilities.
Future features may show a clear unavailable label in previews; no placeholder action reports success.

Use native landmarks, skip link, one route heading, meaningful link names, visible focus, keyboard traversal,
Escape/dismiss behavior and return focus. Menus/sheets trap focus only while modal; navigate with browser links,
not clickable `div`s. Screen-reader status announces loading/result/error summaries without every keystroke.
Search suggestions follow combobox/listbox semantics with arrow/Enter/Escape and “View all.” Respect reduced
motion and allow 200% text zoom/400% browser zoom with reflow at 320 CSS px except necessary data comparisons.
Touch targets at least 44×44px, separated from competing actions; no essential hover-only commands.

Phones use suitable input modes/autocomplete, visible labels, `100dvh` with fallback and safe-area insets.
Virtual keyboard opening must leave focused inputs/errors and Save/Cancel reachable; sticky footers cannot
cover content. Prefer one page scroll on compact, bounded independent scroll only for deliberate drawers/
long lists. Preserve position across list/detail back navigation and orientation changes without losing drafts.
Long names wrap on detail, may truncate with accessible full value on cards, and never crowd out actions.
URLs/long unbroken text wrap; prose preserves paragraphs and significant whitespace.

Tables use header/cell semantics and bounded horizontal scroll only where column comparison matters.
Cards on compact retain the same ordered facts, status and commands; changing layout must not remove data
or bulk-action discoverability. Avoid mounting duplicate accessible table/card trees. Large datasets need
server paging before any optional virtualization, whose keyboard/screen-reader behavior needs validation.

Short edits use a shared dialog on wide/medium and accessible sheet or full-height dialog on compact;
long intake/aggregate forms use a page. Validation preserves entered values, links an error summary to fields,
focuses the first actionable error, and distinguishes advisory syntax from authoritative save validation.
Save/Cancel have stable placement; confirmation stages locally and persists only on accepted parent Save.
Dirty forms are not repainted by background reads or closed on a conflict.

All features explicitly distinguish: initial loading; refreshing with retained content; successful empty;
filtered empty; unavailable capability/entity; forbidden; offline/connection unknown; and failed request.
Only successful zero-row responses are empty. Local section errors retain unaffected content and offer
safe read Retry; never offer automatic retry of an uncertain mutation. A 404 must not disclose whether a
foreign tenant's entity exists. Offline UI explains that writes are unavailable and does not enqueue them.

## 6. Routes and bearer/session lifecycle

### 6.1 Canonical routes (PROPOSED additions; retain current aliases)

| Area | Canonical model |
| --- | --- |
| Home/work | `/my-shale` with tab state; `/tasks`, `/tasks/:taskId` and later task activity/updates subsections. |
| Cases | `/cases`, `/cases/new`, `/cases/:caseId/overview`, `/details`, `/dates`, `/tasks`, `/updates`, `/parties`, `/team`, `/links`, `/materials`, `/documents`, `/timeline` under the same entity parent. `/cases/:caseId` redirects with replace to Overview. |
| Directories | Existing Contact/Organization/Team ID URLs retained; nested subsection routes added only when implemented. |
| Global | `/search`, `/calendar`, `/reports`, `/settings/personal`, `/settings/personal/sessions`, `/settings/administration/:section`; existing `/settings` redirects safely. |

Section navigation pushes history; default/legacy canonicalization replaces history. Browser back/forward
restores list position, selected entity, safe filters/page/sort and chosen section without artificial popstate
rewrites. Refresh/direct entry reauthorizes and loads the same route. List/detail split uses the same route,
not a second desktop-only location. Entity identifiers do not convey authorization.

Allowlist nonsensitive query parameters (page/size/sort/status IDs/date range where appropriate), validate
bounds, reset paging when filters change, and reflect stable committed filters rather than every draft.
Do not put case names, narrative, Contact details, free-text search, tokens, credentials or signed links in
URLs. Existing REST search queries in URLs are a compatibility constraint; separately review safe POST
search and access-log/referrer handling before broad sensitive search, without breaking old clients.
Sensitive search state may stay session-memory-only; reload can request re-entry rather than leaking it.

Capture return-to in navigation state/session memory as a normalized same-origin allowlisted app path.
Reject schemes, protocol-relative paths, unexpected hosts and auth-loop destinations; reauthorize after
login and drop sensitive query values. Later mobile deep links map verified app/universal links into this
route registry through `platform`, never carry bearer credentials, and still require session verification.
OS association/configuration is separately authorized phase 10 work.

Unsaved-work protection combines router blockers, modal discard confirmation and best-effort `beforeunload`
only while dirty. Mobile process termination cannot be guaranteed to prompt. Keep drafts in memory by default;
no persistent PHI drafts. Save navigation waits for a known outcome; conflict retains the user's draft and
opening witness while a separate latest snapshot supports explicit review/reapply.

### 6.2 VERIFIED protocol and proposed coordination

Existing `/api/auth/login` returns opaque bearer, `expiresInSeconds`, profile; `/me` verifies current identity;
`POST /refresh` accepts the **current still-valid bearer**, rotates JTI and returns replacement bearer/TTL;
`POST /logout` revokes. New WEB session expiry begins at JWT expiry, and normal refresh extends it with the
configured TTL. SQL rotation checks current JTI and database expiry: one concurrent rotation wins, the other
loses. Expired bearer cannot use an independent long-lived refresh credential: none exists for web.
Desktop's remembered credential is not a web refresh token or a native handoff contract.

| Lifecycle event | PROPOSED web behavior and guard |
| --- | --- |
| Startup | Read through `CredentialStore`, enter verifying state, call `/me`, reveal protected data only after verification. No token means signed-out. Network/5xx means verification unavailable with safe Retry, not confirmed logout. Never render another cached identity meanwhile. |
| Expiry/refresh | Record TTL/deadline in session memory and refresh before expiry with clock margin. On reload, TTL metadata is not currently persisted; choose a verified scheduling approach (e.g. one coordinated refresh after `/me`) in phase 3. JWT parsing can be a timing hint only, never authority. No refresh after confirmed expiry/revocation. |
| Concurrent requests | One refresh promise per session generation. New requests wait for rotation; replace bearer atomically in memory/storage. Suppress stale 401 responses from old JTI requests; revalidate current session rather than signing out the replacement. No mutation replay; safe reads may be deliberately reissued under current generation. |
| Refresh failure | Transport uncertainty may mean JTI rotated but response was lost. Keep authority unknown, block new writes, offer verification/relogin; do not repeatedly submit old refresh credentials or claim refresh succeeded. Current controller maps refresh exceptions to 401; review this ambiguity separately before promising perfect outage classification. |
| Logout | Detach live handlers/timers, abort requests, advance generation, clear credentials and sensitive query/draft/blob caches immediately; best-effort server revoke with truthful result if unconfirmed. Local sign-out is certain; remote revocation is not if network failed. Do not retain a token for a background revoke queue. |
| Remote revocation | Every authorized API request checks durable session; future safe hint accelerates a current-session read. Only confirmed session authentication rejection clears auth; operation-level 403 means forbidden operation, not universal session expiry. No claim of browser live enforcement today. |
| Account/tenant change | Go through session teardown before another login; no client tenant header or query can switch authority. Clear previous sensitive cache, search history, forms, object URLs and pending callbacks before installing new identity. |
| Long-form reauthentication | Pause Save and keep memory draft privately isolated; authenticate and verify same tenant/user before allowing explicit Save after latest-version review. Different identity discards the sensitive draft; no automatic resubmit after login. Permission changes are rechecked server-side. |
| Resume/focus | Check freshness/current session once where needed and reconcile visible authorized reads. Coalesce focus/live/reconnect triggers; never overwrite edited fields. Show unknown network status distinctly from rejected credentials. |

Expose a small authentication interface (`verify`, `signIn`, `refresh`, `signOut`, subscribe to state) with a
replaceable `CredentialStore` (`read/write/clear`). Features never access `sessionStorage` directly or receive
raw tokens merely to render UI. Browser starts with the existing per-tab storage policy unless an explicit
scoped decision changes it. `sessionStorage` survives refresh but is JavaScript-readable and vulnerable to XSS;
memory-only reduces persistence at a reload usability cost and is still vulnerable during execution.
Neither encrypting with a browser-held key nor calling storage “secure” fixes XSS. Review CSP, dependency
hygiene, escaped text and sanitized allowed rich content; avoid unsafe HTML injection. Proposed cookie auth,
OAuth, separate refresh credentials, native secure storage/handoff all require their own decision, server
compatibility, CSRF/origin/threat review and rollout plan. None is an existing browser capability.

## 7. API/workflow parity and delivery matrix

**Common gates for every row:** authenticated server-derived tenant/actor; explicit tenant predicates and
RLS; current user/entity/field authority including parent-case checks; bounded validated requests and responses;
soft deletion/history; safe errors; required sensitive-read and meaningful-mutation audits (section 8).
Role visibility in React is presentation only. Apply permission filtering before page/count/search disclosure.
Case Team/firm role membership is not proof of a general case ACL. Policy/ethical-wall decisions remain OPEN.

Phases below are proposed Web V2 phases, not historical domain implementation phase numbers. HTTP paths
are VERIFIED only where identified as current; missing-contract descriptions are FUTURE, not wire promises.

| Area / current desktop workflow | VERIFIED current web/API | Missing server/service contracts or investigation | Responsive interaction | Security, concurrency and audit needs / dependencies / phase |
| --- | --- | --- | --- | --- |
| My Shale, assigned work | `/api/cases/assigned`, `/api/tasks/assigned`; beta My Shale/My Tasks and task details | Paged assigned results, dashboard counts/projections if required; avoid whole-list task loads. | Compact work cards; wide grouped lists/detail; visible My Cases/My Tasks. | Actor-owned assignment plus parent-case policy; minimized summaries, scoped audit treatment; phase 4 first slice, phase 5 tasks. |
| Search and suggestions | Case/Contact/Organization search plus Case/Contact `search-page`; no unified suggestion HTTP contract. Desktop SuggestionDao/SearchService and latest-only popup cover multiple categories. | Non-UI suggestion orchestration, selected-ID revalidation, full category paging; current prefix/slicing is not true paging. | Debounced cancellable combobox; Enter/View all full results, labelled categories; no local persistent queries by default. | Preserve allowed deleted-case/admin visibility and category predicates; review ordinary search disclosure/read audit without AI attribution; phase 4 basic search, phase 6 broader search, phase 7 Calendar/Team categories. |
| Cases and New Intake | `POST /api/cases` basic case creation with mapped CaseDates; beta form lacks full desktop caller/client/party/configured intake and duplicate merge workflow. | Expose existing intake aggregate/configured form/duplicate review through reviewed service boundary; type/lookups and field errors; deletion/restoration routes missing. | Page form with sections, staged duplicate review and stable Save/Cancel; no fake multi-step commits. | One case/parties/status/dates/confirmation/audit transaction; Intake effective protected mapping; explicit lifecycle authority and RowVer; phase 6 after Dates and contract work. |
| Case Overview / Details | GET detail with related Contacts/status history/mapped dates; PATCH core-details with expected Case RowVer and occurrence/absence witnesses; assignment/status writes exist without opening-token fields. | Rich Overview/configuration, general field/party/team surfaces and server effective presentations; concrete detail DTO; assignment/status concurrency review. | Shared header/stage/details/Updates composition; compact subsection pages, wide rail. | Sensitive view audit seam; do not recreate scalar dates or last-write-wins. Retain drafts on 409, coherent snapshot reload; read slice phase 4, safe edits/configuration phase 6. |
| Case Dates / confirmation / presentation | Mapped families via detail/core/create and `GET /api/lookups/case-date-types` (family string list); generic occurrence/confirmation/configuration ports exist but no corresponding inspected REST routes. Desktop has occurrence editing, generic Dates and configured presentations. | Typed effective type metadata; generic bounded list/create/update/remove/restore; confirmation state/eligible action and policy/presentation configuration API adapters. | Occurrence cards/table share facts; date editor dialog/sheet; explicit confirmation state and action, no color-only pending state. | Intake-only protected role; SOL/TCN multi-occurrence families; Case+occurrence/requirement RowVer and ValueRevision; transaction-owned confirmation/audit; phase 5 occurrence/confirmation, phase 7 administration. |
| Tasks | List/detail/create/update/complete routes and browser handlers; priority lookup. Desktop uses shared NewTaskDialog/TaskDetailDialog, assignments/activity and authoritative due-date policy. | Paging, policy read/acknowledged WARN interaction, task status/assignment/activity parity; actor/concurrency tokens; atomic update+assignment seam; audit gaps below. | Shared task card/detail form; comfortable phone inputs; explicit complete action. | REQUIRED rejected below UI; OPTIONAL nullable; WARN user confirmation in client after server policy read. No client policy inference, no retroactive migration. Phase 5 gated on task transaction/audit/concurrency work. |
| Case and task updates/notes | Case GET/POST updates; no inspected task-update routes; desktop richer note/edit/activity flows. | Bounded page/detail and edit/remove contracts with author/policy/tokens; task updates; content format/sanitization decision. | Composer below/on section on compact, rail wide; preserved draft and timestamp/author. | Narrative is sensitive; PHI reads/writes, author authority; transaction/audit review of existing note writers; phases 5–6. |
| Relationships / Parties | Related Contacts in case detail and related Cases in Organization detail; desktop CaseParties and richer relationship editors. | Authoritative Contact/Organization party commands and bounded inverse lookups; avoid new dependence on legacy CaseContacts. | Entity cards/pickers, staged relationship edits. | Same-tenant entities plus parent-case permission; stable role IDs/side/primary, concurrency and aggregate audits; phase 6. |
| Case Team | Desktop membership aggregate allows roleless members/multiple roles; beta only Responsible Attorney assignment. | CaseServicePort membership/role contracts already exist; REST aggregate snapshot/reconcile adapter missing. | Staged searchable person-card selection and roles, no separate mobile team logic. | Validate active same-tenant actor/users/effective roles, Responsible Attorney singleton, membership versions; same-transaction team/audit/timeline; phase 6. |
| Links | Desktop Links, primary link, staged sharing, Contact reverse lookup; no inspected beta/server link routes. | REST over existing CaseServicePort aggregates, ordering/primary/share tokens and authorized reads. | Shared link cards with independent embedded Contact navigation; safe external open and staged share sheet. | HTTP/HTTPS only, no credential-bearing URLs; aggregate save/share audit transaction and unique-index conflict handling; external share row is not provider entitlement; phase 6. |
| Materials / Documents | Desktop request/item metadata ports, follow-ups, external link references and generated Case Summary HTML/PDF; no inspected browser content/download endpoint. | REST metadata adapters; non-UI summary composition, export/content IDs and storage/provider authorization/download audit decision. | Request/item lists/detail; browser select/download capabilities, truthful unavailable actions. | Parent-case/item policy, content bounds/type, PHI view audit and transaction-owned material mutations; file bytes/providers are separate scope, not inferred from URLs. Phase 6 metadata, gated document export/content slice. |
| Case/Task Timeline | Desktop domain chronology via DAO/UI; current case statusHistory and updates are partial chronology, not full timeline API. | Bounded ordered read and permitted additive actions using existing owners; stable cursor semantics. | Chronological cards with author/time, filters and load-more; wide comparison only if useful. | Sensitive activity reads; never use timeline as compliance audit; no rewriting append-only history; phase 6. |
| Contacts | Search/detail/create; PATCH `/api/v2/contacts/:id` uses expectedUpdatedAt and ValueUpdate. Desktop structured names, classifications, credentials/contact methods and reverse relationships are richer. | Bounded directory and authoritative structured aggregate REST DTO/commands, classifications/history/lifecycle. Keep current scalar API compatibility. | Shared Contact cards/profile/section form and phone/email component. | Explicit parent tenant predicates (Contacts RLS gap), child RLS, active effective definitions/historical identity, aggregate concurrency/audits; phase 6. |
| Organizations | Search/detail/create; PATCH `/api/v2/organizations/:id` requires RowVer/ValueUpdate. Desktop structured methods, type memberships and admin removed-only restore exist. | Structured profile/directory paging/type membership/restore adapters, explicit null semantics; legacy scalar contracts already delegate aggregate ownership. | Shared organization cards/section forms; removed badge and confirmed admin Restore. | Same-tenant admin restore uses opening parent RowVer, preserves children/history/relationships; no fabricated restored child rows. Same-transaction entity audit; phase 6. |
| Calendar | Desktop unified feed of persisted CalendarEvents, Tasks.DueAt, CaseDates and established case lifecycle projections; no beta Calendar route/API. | Non-UI feed/event orchestration and bounded range/user filters; reviewed event edit contracts. | Agenda default compact, week/day grids where fit; occurrence action opens date editor in place. | Local/all-day semantics and end-exclusive ranges; never copy task/date projections into CalendarEvents; parent policy, event tokens/audits; phase 7 after phase 5. |
| Reports | Desktop status detail and XLSX/CSV via authoritative projections; no beta Reports route/API. | Bounded authorized report queries, field selection/aggregation, export limits and audit policy. | Summary cards then accessible table/card detail; explicit download. | Prevent count/export disclosure, preserve CaseDates projection and stable report semantics; sensitive read/export audit decision; phase 7. |
| Team / users / roles | `/api/users`, `/api/users/:id` directory/profile read; desktop user admin/firm-wide role definitions/assignments. | Paged directory and user/admin role contracts; no inspected user mutation/role REST API. | Person cards/profile; administration separate from directory. | Live same-tenant active admin check; legacy is_admin/is_attorney authority separate from firm roles and Case Team roles; user/role RowVer and same-transaction audits; phase 7. |
| Personal / tenant administration / sessions | Beta shows current profile and admin read-only statuses/practice areas; server self/admin session APIs plus release/policy/instance APIs exist. Desktop Appearance, notification settings, forms/lookups/roles/Task policy/audit viewer are richer. | Personal preferences API, tenant definition/configuration/audit-view adapters; web session consumers. Global release control plane is distinct from tenant administration. | Personal and Administration subsections; bounded session cards with facts and confirmed targeted sign-out; wrapping names/actions. | No token/JTI/machine/IP enrichment. Session-security reads/revokes already transactional; settings audits per action, theme preference intentionally unaudited; phases 3 lifecycle, 7 UI/admin. |

### 7.1 Authoritative invariants and contract requirements

CaseDates, not migrated `Cases` scalar columns or React arithmetic, own date values. Intake uses the
protected tenant-effective `INTAKE` mapping. Later presentation/lifecycle cutover makes SOL and TCN ordinary
SystemKey families, permits multiple occurrences, and retains historical identities; fixed compatibility
projections select earliest StartsAt then lowest occurrence ID. An unavailable family is not a fabricated
creatable absence witness. Generic Dates/Calendar retain every active occurrence. Card/Overview configuration
uses service-returned ordered effective presentations, with per-case custom/explicit-empty override precedence
and inherited firm defaults; these selections do not redefine value authority.

Lookup overlay resolution stays server-owned: nondeleted tenant SystemKey winner, inactive winner masks
global selection, deleted override resets to global fallback; historical references still render stored
identity safely. Name/id guesses and beta `mergeEffectivePracticeAreas` name fallback must not become V2
business authority. Different domains have verified eligibility limits; do not generalize all global rows.

Confirmation policy belongs to the resulting Case Date Type's stable policy identity (`SYSTEM:<key>` or
`TYPE:<id>`). New value/edit/restore snapshots policy into the owning transaction; existing confirmations are
immutable. `ValueRevision` changes for type/start/end/all-day business changes, not title/notes/RowVer alone.
Manual confirmation validates actor's snapshotted firm-wide role and current occurrence/requirement tokens;
Case Team membership is not eligibility. Policy changes do not backfill or silently invalidate old facts.
React renders authoritative state/availability and submits a command, never calculates confirmation authority.

Preserve soft delete/restore and parent lifecycle visibility with domain-specific policy. Task REQUIRED
enforcement, WARN presentation, lookup defaults, case status/lifecycle, due-date derivation and Calendar
projection belong to existing service/DAO authority. Narrow shared orchestration extraction may be needed
for a verified gap; no broad backend rewrite or second React rule engine is planned.

Every new/hardened list contract must bound work **in SQL**, use stable ordering and validated page/size or
cursor, cap query length, range and response size, and avoid N+1 per-card hydration. Authorization predicates
precede paging/counting. Null total is acceptable with an explicit continuation contract; do not infer total
or exact completeness from a capped array. Browser AbortSignal and latest-generation guards are proposed;
server cancellation/statement timeout must be verified independently, not assumed from fetch abortion.

Use concrete response DTOs and documented null/optional/retain/clear behavior. Treat concurrency tokens as
opaque command witnesses, never display or audit them. Existing Contact timestamp and Organization RowVer
are different contracts; do not silently substitute one. Case aggregate snapshots include Case and occurrence
versions/expected-absence witnesses coherently. 409 loads latest state separately, preserves draft, explains
conflict and permits deliberate review/reapply; no automatic overwrite, merge, or uncertain mutation retry.

Date-only values remain date-only; Case Date StartsAt/EndsAt and existing Task due contracts use local
wall-clock semantics, not implicit UTC conversion. Security/audit/session timestamps use explicit UTC Instants,
localized only for display. Specify all-day, range intersection/end-exclusive boundaries, timezone display,
DST and optional end-date rules per endpoint. Legal deadlines must not move a day through `new Date` conversion.
Future absolute scheduling/timezone changes need their own compatibility decision.

## 8. Live updates and required audit compatibility review

### 8.1 VERIFIED live source and limits

[LiveBus](../../shale-desktop/src/main/java/com/shale/desktop/net/LiveBus.java) connects and joins
`client-{ShaleClientId}`; the early `tenant:{id}` diagram is historical. Domain publisher emits `eventId`,
`type=EntityUpdated`, `entityType`, `entityId`, `shaleClientId`, `updatedByUserId`, `clientInstanceId`,
`timestamp` and optional `patch`. It does **not** currently emit `schemaVersion` in that domain method;
parser defaults a missing version to 1. New server
[HttpInvalidationPublisher](../../shale-server/src/main/java/com/shale/server/live/HttpInvalidationPublisher.java)
emits explicit `schemaVersion=1` for `SESSION_INVALIDATED` (tenant/session public UUID) and
`APPLICATION_POLICY_CHANGED` (global channel). Do not collapse those envelopes into a fabricated uniform wire
shape. Dispatcher still has legacy Case patch parsing, including a name field; newer domain allowlists are
PHI-free hints. Audit all event families before allowing a browser subscriber; do not forward arbitrary patches.

[NegotiateClient](../../shale-desktop/src/main/java/com/shale/desktop/net/NegotiateClient.java) passes tenant/user
query parameters and optional Function key, receiving a tokenized `wss` URL; domain publish uses an external
HTTP Function endpoint with optional key. This is not an inspected bearer-authorized browser negotiate API.
The Function implementation/grants are outside this source boundary; an example Function document is not
proof of deployed authorization or routing. Group names in clients do not establish safe server grants.
There is no browser WebSocket/PubSub consumer in inspected `shale-web` source, no session-group grant/replay
store, and no verified general REST-domain invalidation publisher for the existing API writes.

**PROPOSED:** separately implement an authenticated negotiate boundary deriving tenant/user and allowed groups
from the verified session; never embed Function keys, accept client-selected tenant/session groups, or expose
publish authority to the browser. Verify actual Function routing/token roles/expiry and tenant denial with
security owners. Keep policy channel control plane distinct. Each REST domain publisher must run after the
owning transaction commits; publishing failure cannot undo or misreport an already committed mutation.

Browser lifecycle: attach once per verified session, validate version/type/allowed identifiers and tenant,
deduplicate event IDs with a bounded memory window, coalesce bursts into query invalidations, then read the
authorized REST contract. Never reconstruct visible records from patch, timestamp or actor ID. Dirty editors
are marked stale; their input and opening witnesses remain untouched. Session hint revalidates own session,
not trust in payload. Reconnect/resume rechecks session and reconciles visible queries once because missed
hints have no replay; hidden sections stay stale until visited. Requests use identity/entity generations so
late reads cannot replace newer navigation. Logout removes subscriptions/socket/timers/dedupe/cache before
identity change. Safe manual refresh remains usable if live transport is unavailable. Receipt/reconnect and
ordinary transport do not generate audit rows.

### 8.2 Audit ownership and exact scoped gaps

[PhiFieldRegistry](../../shale-core/src/main/java/com/shale/core/privacy/PhiFieldRegistry.java),
[PhiAuditService](../../shale-data/src/main/java/com/shale/data/dao/PhiAuditService.java)/AuditLogDao,
[PhiReadAuditService](../../shale-ui/src/main/java/com/shale/ui/services/PhiReadAuditService.java),
[EntityActionAuditEvent](../../shale-data/src/main/java/com/shale/data/dao/EntityActionAuditEvent.java)/DAO,
[AdministrativeReadAuditEvent](../../shale-data/src/main/java/com/shale/data/dao/AdministrativeReadAuditEvent.java)
and SessionManagementService are existing frameworks; they are not interchangeable.

| Action surface | Existing framework/authoritative owner | Web compatibility/gap and phase gate |
| --- | --- | --- |
| Case detail/sections, Contact view, Task detail/activity, updates/timeline, Materials views | Desktop PHI read intent seam appends to AuditLog; actor derives from AppState and failures are caught. | Cannot be reused by HTTP through JavaFX. Add a non-UI actor/tenant-aware sensitive-read seam with approved view/operation vocabulary, dedupe and failure policy before real-data phase 4 detail and later sections. No fabricated entity READ row. |
| Search summary/suggestions | Desktop summary/keystroke paths intentionally unaudited; selected detail uses established view audit. | Review web summary disclosure/required audit per operation, including caching/refetch intent; do not inherit AI-specific access-event rules or audit every keypress. Record owner decision before broader search. |
| Case core, intake/CaseDates, confirmation, Team, Links, Contacts/Organizations aggregates, Materials, Calendar/user/lookup/admin writes | Owning DAO/aggregate transaction plus PHI fields and/or allowlisted entity actions; newer workers use connection-bound audit. | Reuse exactly those owners and approved vocabularies. Prove rollback on audit failure at each selected seam; port/HTTP existence alone does not prove every old path is compatible. |
| Task create/edit/complete/assignment and Case/task notes | TaskDao has existing PHI field audit calls; TaskServiceAdapter splits calls; entity-action vocabulary has no TASK entity. | Scoped task transaction/actor/concurrency review: non-connection PHI overload catches errors and borrows separately; completion does not pass port actor into gateway. Establish required action audit representation (possibly reviewed allowlist/schema/viewer extension) before V2 writes. Review note writer transaction/audit coverage independently. No invented TASK audit insertion. |
| Session list/admin revoke/self revoke/logout | SessionSecurityAuditLog via SessionManagementService transaction; admin list uses ADMIN_SESSION_LIST, revocations closed security events. Ordinary auth logout uses durable session lifecycle in ServerAuthSessionService. | Retain current distinction. Self list has no extra audit; routine refresh/request validation uses lifecycle, not per-request audit rows. Browser sign-out transport must not fabricate successful server revocation. |
| Administrative instance reads; broader audit/settings/report/export reads | Existing AdministrativeReadAuditEvent only supports recent-instance list/version distribution; session admin reads use separate security framework. | Reuse those exact paths. General reports, export/download, administrative audit viewer and other sensitive admin reads need a scoped vocabulary/failure/retention decision where not already represented. Do not insert arbitrary read types or JSON into PHI value fields. |
| Appearance, menu navigation, staged selection, Cancel, local filters, live delivery | Personal appearance is intentionally unaudited; no meaningful saved domain/admin change in staging or transport. | No new audit rows. Sensitive view entry remains separately audited where required; rerendering/navigation mechanics themselves are not events. |

For mutations, audit belongs on the **same SQL Connection before commit** as business change; required audit
failure rolls back business state. UI/HTTP never constructs authoritative audit rows or queues them after commit.
Sensitive reads need a deliberate authoritative failure policy before release, not a browser callback audit
that can be bypassed. Multi-query view composition must choose its transaction/connection plan because some
ports independently borrow. Connection-bound PHI auditing fails closed; old convenience overloads suppress
failure, so blanket claims of transactional coverage are incorrect.

Preserve verified tenant, actor, entity, parent/case, source and correlation context. Entity/admin/security
metadata uses only allowlisted IDs, closed states, counts and booleans. Exclude names, email/phone, URLs,
notes, dates/values, token/JTI, RowVer bytes, document text, commands/DTO snapshots, SQL and exception text.
Registered PHI value handling stays in the established field-audit representation with approved sanitization,
not copied into entity metadata or browser telemetry. Review both Java and deployed SQL allowlists plus viewer
compatibility and audit-event tests. If schema cannot represent a required action safely, defer that surface
for an explicitly scoped audit enhancement; Timeline is not an alternative audit store.

**Exact future review backlog:** non-UI sensitive view/read ownership; ordinary web search audit policy;
task/assignment/completion and note transaction/audit gaps; report/export/document access event representation;
remaining administrative-read vocabulary; Function authorization and REST post-commit invalidation coverage.
Also retain the documented Contacts parent-RLS and live non-dbo verification gaps. None is repaired here;
no migration is prescribed or audit integration claimed by this documentation-only PR.

## 9. Future mobile readiness and delivery boundaries

One shared UI source does not mean one update mechanism. Define only small capabilities needed by web workflows:

| Capability boundary | Browser initially | FUTURE native decision |
| --- | --- | --- |
| File selection/download | Native file input; authorized response download/object URL with cleanup and size/type checks; no persistent sensitive cache. | Picker/filesystem/share adapter, temp-file lifetime and document audit/authorization. |
| External links/sharing | Reviewed HTTP/HTTPS open; browser share API if supported, otherwise explicit copy/open choices. | System browser/share adapter; provider authentication remains separate. |
| Lifecycle/deep links | Visibility/online events are hints; normalize safe route entry and reverify session. | App resume/background listener and validated universal/app-link mapping. |
| Credentials | Browser CredentialStore with existing bearer policy. | Secure storage/enrollment/handoff protocol requires a scoped server-compatible decision; do not assume desktop remember credentials transfer. |
| Camera/scanning, biometrics, notifications | Report unsupported unless an explicitly implemented browser capability exists; no fake success. | Later device permissions, scanner/content pipeline, local unlock versus server authentication, notification authority and privacy. |

Capacitor is a possible thin wrapper, not a chosen/installed implementation. Primary documentation reviewed
2026-10-08: [Capacitor workflow](https://capacitorjs.com/docs/basics/workflow) says sync copies the compiled
bundle into native projects; [configuration](https://capacitorjs.com/docs/config) defines `webDir` and describes
`server.url` as live-reload usage, not production delivery. Recheck selected versions during mobile decision.

| Delivery/update mechanism | Meaning and later gate |
| --- | --- |
| Browser deployment | Users receive hosted application assets through normal page/cache lifecycle; open sessions may retain an older bundle until safe reload. |
| Packaged mobile assets | Build web bundle, copy/sync into iOS/Android projects, release native binary. Publishing the website does not update those embedded assets. |
| Optional controlled asset updates | Separate later architecture: signed/provenance-checked bundles, plugin/API compatibility, staged activation, atomic rollback, privacy and store-policy review. No automatic OTA promise or production server.url shortcut. |
| Native binary/plugin updates | Runtime/plugins/entitlements need native build/signing, platform acceptance and store distribution even if UI source is shared. |

Mobile delivery/update choice is OPEN until phase 10, including asset freshness, session origins/CORS,
plugin availability, binary compatibility and deep-link association. This task starts no mobile projects,
installs no Capacitor, and creates no native code, push infrastructure, app-store settings or certificates.

## 10. Online-first operation and replacement rollout

Start online-first: no offline mutations, write queues or background retry. Do not persist sensitive
case/document responses or drafts in localStorage, IndexedDB, service-worker caches or Cache Storage by
default. In-memory data may remain visibly stale while offline within the current verified identity; no new
protected disclosure on unknown startup authority. Local logout immediately clears sensitive state.

Optional phase 9 PWA/static-asset caching must separate versioned public application assets from authenticated
API/document content, exclude bearer responses from shared caches, and test logout/tenant switch/storage
cleanup. A cached shell is not authenticated offline business capability. Offline read or draft retention
would need its own security/product decision; there is no promise of later queued writes.

**PROPOSED clean replacement:** keep one project and shared component vocabulary. Extract primitives from
beta, then introduce V2 shell/features behind a temporary route/composition selection for review. Keep a
known-good beta build/commit as rollback and, only as needed, a short-lived legacy composition during migration.
Do not duplicate every feature for both versions indefinitely. Each accepted slice moves into the shared V2
feature module; delete obsolete beta composition after cutover acceptance/soak. Rollback can redeploy the beta
artifact, so permanent duplicate implementations are unnecessary.

Existing Azure plan is a Vite static build on Static Web Apps and independent Spring Boot App Service API.
`public/staticwebapp.config.json` already rewrites navigation to `index.html`. That establishes source intent,
not deployed direct-link acceptance. Retain this practical direction unless measured needs justify another
host. Preview deployment is later authorized operator work; no resources/configuration change here.

1. Build reviewed V2 previews against an approved test API/tenant with exact origin CORS and no development
   identity headers. Preview authentication/access control, secrets, data and logs need deployment review.
2. Prove additive API compatibility with beta/current desktop before each new slice. Ship necessary server
   contracts before their client consumer; independently validate required SQL deployment where applicable.
   No requirement to finish unrelated MCP/AI or desktop HTTP migration.
3. Verify direct-link refresh/back/forward on the real host, including new case subsections, not merely Vite.
   Preserve asset paths/errors; keep API routes outside SPA HTML fallback when colocated.
4. Use content-hashed immutable assets with a revalidated entry document; test old tabs/dynamic imports while
   a new build appears. Retain prior assets for the compatibility window; offer safe reload after unsaved work
   finishes rather than forced reload that loses a form. Exact cache headers/update mechanism remain OPEN.
5. Stage acceptance with test/pilot users and explicit operator control, then broader rollout. Record build/API
   versions, accepted workflows/devices, error/conflict outcomes and unresolved exclusions without PHI telemetry.
6. Roll back frontend to the known-good artifact if necessary; keep old API contracts during rollback window.
   Durable-session server rollback has separate signing/revocation constraints in the session roadmap; a web
   rollback does not justify reverting to an API that ignores SQL revocation. No automatic DB rollback.
7. Retire beta after parity/accessibility/security/operational gates and agreed soak window; remove temporary
   selector/legacy entry, update web docs and retain reproducible rollback artifact until owner-approved expiry.

## 11. Open decisions and phased progress tracker

### 11.1 Explicit decision register

| ID | OPEN decision / evidence needed | Owner and first affected gate |
| --- | --- | --- |
| D1 | Approve per-operation case/directory/field policy, deleted-case access and sensitive-read failure/audit treatment. | Product/security/domain owners; phase 4 real-data reads. |
| D2 | Select compatible TanStack Query, forms and unstyled primitive versions; router-blocker migration; contract generator only after concrete schemas. | Web maintainer; phases 2–3. |
| D3 | Browser storage policy, reload refresh timing, rotation/network ambiguity and authenticated cache cleanup acceptance. No auth protocol change assumed. | Security/API/web owners; phase 3. |
| D4 | Validate content breakpoints, compact destinations, fonts/density and real-device input/zoom/contrast. | Product/design/accessibility; phase 2 onward. |
| D5 | Numeric SQL paging/range/payload budgets, timezone/date contracts, missing concurrency witnesses and command outcome reconciliation. | API/domain owners; each slice before exposure. |
| D6 | Non-UI PHI reads; task/note atomic audit seams; export/download/admin-read representations and any allowlist/schema/viewer changes. | Audit/security/domain owners; phases 4–7 per operation. |
| D7 | Actual Function bearer negotiation/grants/routing and safe post-commit REST domain publication. | Security/deployment/API owners; live-enabled slices. |
| D8 | Document storage/provider authority and generated-summary export scope; approved report/export access. | Product/security/domain owners; phases 6–7. |
| D9 | Preview/pilot origin/hosting/cache settings, acceptance devices, rollout/soak/rollback-retention window and API compatibility window. | Deployment/product owners; preview through phase 8. |
| D10 | Optional PWA and later mobile packaging/update model, secure credential contract and native capabilities. | Separate authorization/decision; phases 9–10. |

### 11.2 Tracker

Evidence column distinguishes current reusable implementation from future Web V2 completion. Future tasks
update status, commit/PR evidence and owner decisions here; checkboxes must not mark implementation complete
merely because this document describes it.

| Phase | Status | Dependencies | Deliverables | Verification | Completion gate / evidence and open decisions |
| --- | --- | --- | --- | --- | --- |
| 1 — Inventory and architecture | DOCUMENTATION COMPLETE; ACCEPTANCE OPEN | Live base and required authorities | This inventory, client decisions, parity/audit matrix and next prompt | Source/test review, links/fences/status/diff checks and documentation selector | Documentation only; no V2 features accepted. D1–D10 remain explicit. |
| 2 — Tokens, components, responsive shell | IN PROGRESS — Phase 2A foundation / Phase 2B authenticated shell / Phase 2C My Shale implemented for review; Phase 2D reviewed with feedback fix; acceptance OPEN | Phase 1 recommendations; D2/D4 for this milestone | Token mapping, shared semantic primitives, responsive shell with complete navigation; temporary preview composition and beta rollback | Existing web tests plus focused semantics/navigation; typecheck/build; screenshots both themes at compact/medium/wide, keyboard/zoom/contrast | Reviewable foundation using synthetic data, all destinations discoverable, no backend/new feature sprawl. Phase 2A: shared primitives/tokens and synthetic preview. Phase 2B: shell adopted beneath existing ProtectedRoute around unchanged outlets; beta screen skin retained. [Phase 2A review](../../shale-web/docs/phase-2a-review.md) and [Phase 2B exact scope, checks, captures and gaps](../../shale-web/docs/phase-2b-review.md). [Phase 2C My Shale review](../../shale-web/docs/phase-2c-review.md) adds shared Light/Dark operational presentation and native Chromium 200%/400% zoom evidence. [Phase 2D acceptance review](../../shale-web/docs/phase-2d-review.md) records rerun Chromium shell/state/zoom checks and persistent task completion feedback; [operator checklist](../../shale-web/docs/phase-2d-operator-checklist.md) owns unavailable Firefox/WebKit, screen-reader and physical-device checks. No all-Phase-2 completion claim; D4 device/screen-reader/other-engine acceptance remains OPEN. |
| 3 — Routing, session boundary, transport | IN PROGRESS — Phase 3A safe return and Phase 3B startup verification recovery implemented for review | Phase 2 acceptance OPEN; user-authorized bounded sequencing exception for 3A/3B; D2/D3 and selected contract review for remaining work | Canonical routes/aliases, safe return-to, dirty blockers, CredentialStore, coordinated refresh, cancellation/errors, memory query cache | Router/session/transport behavior including refresh race, stale 401, network failure, logout/identity cleanup and dirty forms; legacy endpoint compatibility | Phase 3A: [review and synthetic evidence](../../shale-web/docs/phase-3a-review.md), validated pathname/query/hash, replacement history and logout return-state cleanup. Phase 3B: [review and synthetic evidence](../../shale-web/docs/phase-3b-review.md), confirmed 401 versus uncertain verification, retained bearer with blocked access, explicit read-only Retry/local sign-out and stale-result guards. Broader canonical routes, dirty blockers, storage abstraction, refresh/transport/cancellation/cache work UNFINISHED. No overall Phase 3 acceptance; unchanged bearer protocol; no replayed uncertain write. |
| 4 — First end-to-end read slice | NOT STARTED | Phases 2–3; D1/D5/D6 only for selected reads | Sign in → assigned/basic search → case Overview; genuine bounded reads and required read-audit seam | Current controller/adapter/OpenAPI tests plus targeted SQL paging/actor/two-tenant/audit-failure tests; web loading/empty/error/deep-link/back acceptance | Usable reviewed slice, no broad backend rewrite/all-screen dependency. Basic search may ship before unified suggestions with exclusions explicit. |
| 5 — Tasks and Case Dates/confirmation | NOT STARTED | Phase 4; D5/D6 per write; reviewed occurrence/confirmation adapters | Task policy/forms/assignment/completion and generic dates/confirmation through authoritative workers; incremental usable slices | Due-policy tests, actor/tenant denial, token conflicts, aggregate rollback/audit tests; mobile editor/draft behavior | Task transaction/audit gaps closed for exposed commands; CaseDates/ValueRevision preserved. Live remains optional until D7 passes. |
| 6 — Remaining case flows, Contacts/Organizations | NOT STARTED | Relevant phase 5 contracts; D1/D5/D6/D8 | Intake, parties/team/links/updates/material metadata/timeline, structured directories; separately gated document export/content | Domain aggregate/delegation tests, conflict/audit rollback, parent-case permissions, responsive long forms/cards and deliberate mutation recovery | Per-feature acceptance; no fake blob/provider entitlement, no scalar/date/overlay authority in React. Unaccepted content scope remains visibly unavailable. |
| 7 — Calendar, Team, Reports, Settings/admin | NOT STARTED | Relevant earlier slices; D1/D5/D6/D8; personal/session APIs reviewed | Calendar projection/event flow, directory/user/role admin, reports, preferences, tenant configuration/audit/session UI | Date/range/DST tests, report/export limits, live admin-role rechecks, session-security tests, keyboard/touch acceptance | All agreed parity rows accepted or explicit owner exclusions; administrative reads/mutations use approved audits. |
| 8 — Parity and production cutover | NOT STARTED | Phases 2–7 accepted; D4/D7 where enabled/D9 | Full workflow/security/accessibility matrix, preview/pilot, staged production replacement/rollback and legacy retirement | Local relevant suites under repository rules; real host deep links/assets/CORS, devices/screen readers/zoom, N-1 compatibility, operator rollback drill | Owner accepts documented production evidence/soak, API compatibility and retirement. Repository source is not deployment evidence. |
| 9 — Optional PWA enhancements | NOT STARTED | Online-first V2 acceptance and D10 authorization | Application-asset-only offline shell/install/update behavior if chosen | Cache separation, stale bundle/draft-safe update, logout/tenant switch and storage inspection | No persistent sensitive responses or queued writes; PWA choice may be declined. |
| 10 — Separately authorized mobile | NOT STARTED | Accepted shared V2; D10 mobile delivery/auth/native decisions | Thin wrappers and selected capability adapters in later tasks | Packaged asset vs binary/plugin updates, origin/session/deep-link/permissions and physical-device acceptance | Requires separate authorization; no native projects, plugin install, push/certificates or app-store work in this roadmap task. |

Backend gaps are work packages attached to the first consuming slice, not a new global “rewrite all services”
phase. Live browser integration can follow a usable manual-refresh slice after D7 review; it does not force
MCP/AI activation. Phase 2 is intentionally independent of real-data API decisions so it can be reviewed now.

### 11.3 Phase 2A implementation evidence — 2026-10-08

Implemented on separate task branch `codex/web-v2-phase-2a` from live base
`eda5045153d6a4dc175884bbb889021e04df83d2` after confirming PR #1838 was merged.
[Developer review](../../shale-web/docs/phase-2a-review.md) owns exact files, token adaptations,
preview instructions, audit review, captures and acceptance limits.

* Extracted/adapted beta presentation primitives into `shale-web/src/ui`; native semantic actions,
  card activation/selection, fields, regions, feedback and validated readable DB-color indicators.
  A.2 solid surfaces and restricted gradients; no new package/dependency/version.
* One shared navigation registry and responsive shell in `src/shell`; all ten labels including My Tasks
  are reachable. Compact uses a native in-flow disclosure, medium a 10rem labelled rail, wide 15rem.
  This deliberately refines the proposed bottom-bar/drawer, avoiding fixed keyboard-covering surfaces.
* Temporary `foundation.html`/`src/preview` entry: synthetic data only, both themes, long names,
  selection and every requested content/validation state. No API or persistence; no fake save/download.
  Beta remains the default composition with all routes/session/mutation handlers, legacy paint and
  recoverable base. Only shared primitive semantics are deliberately adopted there.
* Verification: 52 web tests, typecheck/build, selected AuthControllerTest (10 tests), critical `mvn test`
  (116 tests), selector and whitespace checks pass. Eight 320/360/768/1280 screenshots cover both themes;
  no page overflow, visible targets >=44px, native keyboard/compact Escape/return focus, no API requests.
  Both themes also pass 200% text-size emulation at 320px and measured 22 text/5 boundary pairs each.
* Audit review: no new sensitive read/domain/admin mutation, no new audit seam/schema; synthetic UI
  changes intentionally unaudited. Existing beta authoritative API/service/DAO audit paths unchanged.
* Acceptance OPEN: actual browser UI zoom, physical device keyboard/safe areas, screen readers and
  other browser engines not verified. Headless text-size emulation is not browser-zoom acceptance.
  No backend, SQL, authentication/session/transport, deployment or native/PWA changes; no deploy/merge.

At the Phase 2A checkpoint, the next narrow milestone was Phase 2B shell adoption. Section 11.4
records its implementation. Remaining Phase 2 includes device/zoom/screen-reader acceptance, deliberate
screen adoption and any needed accessible dialog/menu primitives. Phase 3 remains separate. Remove the temporary HTML/preview/Vite input only after accepted
composition replaces beta, as documented; shared primitives/shell and evidence remain.

### 11.4 Phase 2B authenticated shell evidence — 2026-10-08

Implemented on `codex/web-v2-phase-2b` from current `origin/codex/latest`,
`6951305201c1d18b9712cb4a2a7c5200269abb5b`, including merged Phase 2A PR #1839.
[Phase 2B developer review](../../shale-web/docs/phase-2b-review.md) records ownership, exact commands,
synthetic browser evidence, preserved auth limitations and frontend-only rollback guidance.

* Adopted `ResponsiveShell` inside existing authenticated `AppShell`, around the unchanged `Outlet`.
  Existing route declarations, authentication/session handling, API/service calls and mutation handlers
  remain unchanged. One navigation registry owns labels/paths/availability; My Tasks is discoverable.
  Future Calendar/Reports/Search are noninteractive Unavailable labels on operational routes.
* Existing React Router pathname and location key drive active state and route focus. Browser deep
  links/query/hash, back/forward, single main/skip link, compact native keyboard/Escape/return focus,
  same-path history-entry focus and focused-link resize handling are covered. No new router or route.
* Split existing foundation tokens/button styles so shell adoption does not restyle every feature.
  Operational screen content retains its Light beta skin in both shell themes. Theme choice is session
  memory only, reset on reload/logout; no preference API/persistence is implied. Presentation bridge
  removes old clipping/mobile margins/sticky detail behavior and adjusts card columns/label wrapping
  to available width beside the rail. No broader screen redesign.
* Retained `foundation.html`, isolated synthetic preview, Vite input and Phase 2A evidence. Browser
  interception fixtures are review-only and never imported by operational routes. Preview retains
  synthetic unavailable-destination navigation and makes zero API calls in both themes at all widths.
* Verification: 61 web tests, typecheck/build; selector-selected AuthControllerTest (10 passed) and
  critical Maven reactor (116 passed), diff whitespace check. Sixteen My Shale/case-detail captures at
  320/360/768/1280 cover both shell themes; no page overflow, shell targets >=44px, staged Edit/Cancel,
  history/focus/skip/native compact keyboard/logout pass. Other retained URLs receive 360px synthetic
  empty/error/detail smoke review. Chromium accessibility tree exposes expected landmarks.
* Audit review: no new sensitive read, domain/admin mutation or audit seam/schema. Existing server
  authorization, tenant, validation, concurrency and audit owners unchanged; local theme/navigation
  intentionally unaudited. No backend, SQL, API/auth protocol, deployment/version/dependency/native or
  MCP/AI activation changes; no merge/deploy. No live authentication/backend acceptance claim.
* Acceptance OPEN: actual UI zoom shortcuts left viewport/devicePixelRatio unchanged; browser settings
  target was unavailable. This does not establish zoom acceptance. Physical device input/safe areas,
  screen readers, other engines, real-host and live API acceptance remain open. Existing protected
  login return-to detail failed identically against untouched base `App.tsx` in fixture tests (landed
  on My Shale); full signed-out deep-link round-trip acceptance remains open for scoped Phase 3 work.

Next bounded Phase 2C: native zoom/screen-reader/device shell acceptance and deliberate shared
presentation adoption of one screen, such as My Shale, with both theme contrast/reflow evidence and
unchanged services/session/mutations. Keep wider screen and dialog/menu migration separate. Phase 2
remains **IN PROGRESS**, and Phase 3 remains **NOT STARTED**; its first routing/session slice should
investigate the existing login return-to defect rather than broadening this shell milestone.

### 11.5 Phase 2C My Shale shared presentation evidence — 2026-10-08

Implemented on `codex/web-v2-phase-2c-my-shale` from live `origin/codex/latest`,
`7e7972d6ebb0f1fcc5ae61c1d2eead4854aad39e`, after fetching the remote-tracking ref and verifying
merged Phase 2B PR #1840 by ancestry. [Developer review](../../shale-web/docs/phase-2c-review.md)
owns exact scope, screenshots, reproducible browser/zoom/contrast scripts, checks and rollback.

* My Shale is the first operational screen adopting shared V2 Light/Dark presentation. Its heading,
  sections, assigned-case/task cards, metadata, labels/actions and loading/empty/error states use existing
  semantic primitives/tokens. Extracted foundation presentation into opt-in `ui/presentation.css`;
  no dashboard palette or second card system. The shell retains theme ownership. Other routes keep
  beta styling; shared task-list semantic actions are opted in only by My Shale. Preview/HTML retained.
* Existing fields, fallbacks, calls, identifiers, formatting, case/task navigation and authoritative
  Complete response merge remain. Native keyboard/pointer card navigation and Complete stay separate;
  pending disables the task action. Failed Complete now displays a separate alert while retaining
  loaded My Shale cards. No optimistic success, auto retry, new capability or other-route migration.
  Corrected the dashboard read-only welcome to acknowledge its existing action.
* Both themes at 320/360/768/1280 cover long/unbroken names, missing values, loading, empty, read errors,
  completed tasks, pending/completion failure/success. 56 synthetic captures, no horizontal page
  overflow/measured clipping, >=44px dashboard controls, visible focus and native Tab/Enter/Space/card
  separation. Preview makes zero API requests. 40 axe scans report zero violations; incomplete
  pseudo-element/gradient color checks are recorded and complemented by 50 measured token pairs.
* Native Chromium Settings page zoom exercised in a disposable persistent context: fixed 1280×900
  viewport yields DPR 2 / CSS 640×450 at 200% and DPR 4 / CSS 320×225 at 400%, both themes. Four captures
  and keyboard/failure checks pass with no horizontal overflow. Earlier shortcut/isolated-settings
  attempts failed; viewport or text-size emulation is not counted as zoom evidence.
* Checks: 68 web tests, typecheck/build, selector (server/AuthController; no escalation), selected
  AuthControllerTest (10) and critical Maven reactor (116), git diff whitespace check pass.
  Existing all-route/auth/login-return-gap and preview/shared/validation tests remain intact.
* Audit review: no new sensitive read/domain/admin mutation, audit integration or schema migration.
  Existing authorization, tenant/principal, validation, concurrency and audit paths unchanged;
  presentation intentionally unaudited. Section 8.2 read/task audit gaps remain deferred, not closed
  by retaining Complete. No backend/SQL/API/auth/session/transport/deployment/version/dependency/native
  or MCP/AI activation change. No merge/deploy or live backend/security acceptance claim.
* Acceptance OPEN: physical-device keyboard/safe areas, screen readers, Firefox/WebKit, real host and
  live authentication/backend/tenant/audit workflows remain unverified. Manual acceptance steps are
  in the review. Existing login return-path defect remains scoped Phase 3 work.

Rollback: revert the frontend milestone or rebuild base `7e7972d6ebb0f1fcc5ae61c1d2eead4854aad39e`
with the same API-origin setting; no backend/session/SQL rollback. Next bounded **Phase 2D**: obtain
screen-reader/physical-device acceptance for shell/My Shale and resolve only findings in those adopted
surfaces; choose any next screen separately. Phase 2 remains **IN PROGRESS**, Phase 3 **NOT STARTED**.

### 11.6 Phase 2D adopted-surface acceptance review — 2026-10-08

Separate branch `codex/web-v2-phase-2d-acceptance` from fetched live `origin/codex/latest`,
`fc45437e6519be7e905b1ace90e418cbd49276cc`; merged Phase 2C PR #1841 verified by head ancestry.
[Review](../../shale-web/docs/phase-2d-review.md) records exact checks, evidence and limits;
[operator checklist](../../shale-web/docs/phase-2d-operator-checklist.md) supplies launch instructions,
expected outcomes and browser/device/outcome/findings fields. No merge/deployment.

* Review confined to adopted shell/My Shale. Concrete feedback defect: disabled pending label had no
  live update, and success mounted a populated status. My Shale now uses one initially empty persistent,
  polite/atomic status with task-named pending and server-confirmed success; failure retains its alert and
  cards. Initial completed facts are ordinary text. Existing service/data/navigation/completion semantics,
  single-task pending bookkeeping, other-route presentation and authentication/session handling preserved.
* Chromium 151.0.7922.173, both themes at 320/360/768/1280: shell disclosure/active/skip/route focus/history,
  native keyboard/visible focus, independent cards/Complete, long/missing values and all requested states
  pass. 56 state screenshots, 56 zero-violation axe scans with incomplete contrast checks recorded, 50
  measured token pairs pass; no horizontal overflow, targets >=44px, zero page errors. Native Settings
  zoom 200%/400% passes both themes with fixed physical viewport; four captures. Preview retained/API-free.
  Automated live-region/tree checks are not screen-reader acceptance.
* Firefox 157.0 downloaded but profile launch failed, including one targeted TMPDIR retry. WebKit 27.2
  downloaded but missing host libraries prevent launch. Neither engine's matrix/native zoom accepted.
  No real screen reader/display or physical device available. No real-host/live auth/backend/tenant/audit
  acceptance performed at that checkpoint. Later user-reported Windows Firefox/Narrator synthetic
  results are recorded below and in the operator checklist; required full acceptance remains OPEN.
* 68 web tests, typecheck/build, selector-selected AuthControllerTest (10), critical Maven reactor (116)
  and git diff checks pass. Existing affected tests extended for stable task status and failure/legacy
  isolation; no geometry test gate or GitHub testing workflow. Review scripts/fixtures isolated from app.
* Audit compatibility unchanged: no new sensitive read/domain/admin mutation, audit integration or migration;
  presentation intentionally unaudited. Section 8.2 gaps remain deferred. No backend/SQL/API/auth/session/
  transport/deployment/version/application-dependency/native/MCP/AI changes. Frontend rollback to base above.

Next bounded **Phase 2E acceptance closure**: run the outstanding shell/My Shale screen-reader,
physical-device and other-engine checks and fix only reproduced findings; select any new-screen adoption
separately. Phase 2 remains **IN PROGRESS**, Phase 3 **NOT STARTED**, including the deferred focused login
return-path investigation and safe pathname/search/hash restoration. No acceptance closure by inference.

### 11.7 Phase 3A safe login return restoration — 2026-10-08

Based on fetched live `origin/codex/latest` `151d05a9f34af2922dddc94036bc446b22bf465b`, including merged
Phase 2D PR #1842 and dependency-security PR #1843. Separate task branch
`codex/web-v2-phase-3a-return-path`; [review](../../shale-web/docs/phase-3a-review.md) owns cause,
checks, synthetic browser evidence, limits and rollback. Phase 3 IN PROGRESS; only this slice is
implemented for review. The user explicitly authorized beginning it while Phase 2 acceptance stays OPEN.

* Reproduced protected detail → login → My Shale on untouched production. `LoginPage` return navigation
  competed with `AppRoutes`' authenticated-login default redirect. One rendered redirect now owns return;
  auth is installed only after successful login and `/me`. Valid router-relative protected paths retain
  query/hash exactly; malformed/external/auth-loop/undeclared targets use `/my-shale`. Login history is
  replaced and return state consumed. Failed attempts retain state; logout clears it with replacement.
* Focused and complete web tests, typecheck/build, selected server bearer compatibility and critical
  Maven checks; synthetic Chromium case/contact/task round trips in both shell themes plus history,
  delayed verification, failed-then-successful login, default/invalid login and logout boundary checks.
  Fixtures live only under review documentation; no live backend/authentication acceptance claimed.
* User-reported Windows Firefox synthetic follow-up: completion success/failure, native 200%/400% zoom
  in both themes, keyboard/skip/disclosure/Escape/independent Complete and loading/empty/read-error/normal
  states behaved/looked good. Windows Narrator spoke task-named pending completion; readable structure
  and errors confirmed. Success speech is **assumed by the user, NOT verified**, because releasing the
  response required switching to Command Prompt. Exact browser version, full AT coverage and full
  acceptance are not inferred. Physical devices, Safari/WebKit, real-host/live backend checks stay OPEN.
  [Operator record](../../shale-web/docs/phase-2d-operator-checklist.md) retains remaining gates.
* Audit: no new mutation or sensitive-read endpoint, audit seam/schema or event. Existing authenticated
  detail/service/DAO authorization, tenant, validation, concurrency and audit enforcement are retained;
  navigation mechanics intentionally unaudited. Section 8.2 audit gaps remain deferred. Dependencies and
  security patches unchanged. No backend/SQL/API/protocol/storage/preview/presentation/deployment change.

Next bounded **Phase 3B**: startup verification uncertainty only — distinguish confirmed authentication
rejection from transient network/5xx failure, block protected content while unknown, and offer explicit Retry
with focused tests. No token-storage change, refresh coordination or transport/cache redesign in that slice
without separate scope. Broader Phase 3 routing/session/transport/cache/dirty-form work remains UNFINISHED.
Phase 2E acceptance work continues independently. Rollback reverts only Phase 3A to this security-patched
base; it requires no backend/session/SQL rollback. No merge or deployment.

### 11.8 Phase 3B startup verification uncertainty and explicit Retry — 2026-10-08

Separate branch `codex/web-v2-phase-3b-session-retry` from live `origin/codex/latest`
`8cba4b85569b6c9afbfe8b447b169e6be3380e7f`, including merged Phase 3A PR #1844 and
security dependency PR #1843. [Review](../../shale-web/docs/phase-3b-review.md) owns classification,
test-impact mapping, synthetic browser evidence, exact validation, gaps and rollback.

* Startup `/me` 401 confirms rejection under the inspected resolver/controller contract. Network,
  other 4xx (including 403), 5xx and unusable successful responses preserve the stored bearer while
  installing no identity or feature credential. Protected routes/effects stay unmounted while pending
  or unavailable; public login/default redirects also wait, preserving the original URL.
* Explicit Retry reads the current bearer and issues only a fresh `/me` GET; synchronous pending guard
  prevents duplicates. Local Return to sign in invalidates the attempt, clears via existing storage,
  and replaces with `/login` without the old return target. Confirmed rejection retains Phase 3A's
  signed-out capture/restoration. Successful Retry retains exact pathname/query/hash and history.
* Attempt generations, mount cleanup and stored-token comparison discard late success/rejection after
  local sign-out, replacement, newer attempt or unmount. Accessible status/alert, native buttons,
  initial/return heading focus and deliberate pending/repeated-failure Retry focus are covered.
* Focused hook/API and App recovery/history tests plus full web tests/build; intercepted Chromium
  outage → repeated failure → pending Retry → detail and outage → local sign-in/late-result discard
  at 320/1280, native Enter/Space/Tab, reflow/targets and preview isolation. Synthetic results do not
  establish live backend/session acceptance. Exact counts/checks are in the review.
* Audit: no new sensitive-read endpoint/domain/admin mutation/audit seam or schema; routine `/me`
  validation and local recovery do not generate browser audit rows. Existing authoritative tenancy,
  authorization, validation, concurrency and audit enforcement are retained; §8.2 gaps stay deferred.
  Dependency-security patches and package/lockfile remain unchanged. No merge/deployment.

Phase 2 acceptance stays **OPEN**, Phase 3 **IN PROGRESS**. Next bounded **Phase 3C**: review
established-session logout transport outcomes, then make local sign-out certain while communicating
unconfirmed remote revocation with focused tests; do not retain credentials for background replay.
Refresh coordination, storage redesign/abstraction, broad transport, query cache and dirty-form work
remain **UNFINISHED** and require separate scope. Rollback only Phase 3B to the security-patched
Phase 3A base above; no server/session/SQL rollback.

## 12. Ready-to-run next Codex implementation prompt

The prompt below is retained as the original Phase 2A specification. It was not executed by the
documentation task; the implementation evidence and remaining acceptance above now supersede its status.

```text
Repository: gseshadow/Shale
PR base: codex/latest
Task: Web V2 phase 2A — reviewable responsive foundation and shared shell only.

Fetch live origin/codex/latest. Read AGENTS.md and architecture/codex-prompt-rules.md completely;
follow documentation routing, including the Web V2 roadmap, canonical design-system A.2 and current
Light/Dark resources. Create a separate task branch from the current remote base; preserve unrelated work.
Inspect App.tsx, styles.css, ContactValueInput, routes and related existing tests before editing.

Implement only inside the existing shale-web project:
- Extract/adapt shared semantic Button (Primary/Secondary/Ghost/Danger/Navigation), EntityCard,
  section/heading/field/feedback primitives and Light/Dark CSS tokens from the existing vocabulary.
- Compose a responsive shell for the proposed compact/medium/wide ranges, validating against content.
  Keep My Shale/My Tasks, Cases, Contacts, Organizations, Team, Calendar, Reports, Search and Settings
  discoverable through labelled navigation. Future unavailable routes must say unavailable and never
  simulate working features. Preserve every existing functional beta route and mutation handler.
- Provide a temporary isolated foundation preview composition with synthetic nonsensitive examples:
  long entity names, selected cards, status/practice-area DB-color examples, form errors and states.
  Reuse shared primitives; do not create three independent device-specific feature implementations.
- Keep the beta build/composition recoverable until acceptance and describe temporary preview entry
  and removal plan. Update this roadmap's phase 2 evidence and status accurately.

Do not rebuild all feature screens, change API/auth protocol or session behavior, introduce real-data
queries to the preview, change backend/SQL/deployment/version, install native/PWA tooling or start mobile
projects. React Router is already installed. Prefer native semantic controls for this bounded milestone;
add no dependencies unless an explicitly justified accessible control requires one within this scope.
Do not perform phase 3 or execute a production deployment.

Acceptance criteria:
1. Shared token/primitive ownership replaces extracted duplication; current beta behavior/routes work.
2. Both themes render legibly, including dynamic colors with neutral fallback/readable foreground.
   A.2 geometry/action roles/gradient restrictions and one Primary per local action area are preserved.
3. At 360/768/1280 CSS px plus 320px reflow/zoom, no unintended page overflow; navigation and primary
   content remain reachable. Long text wraps; touch targets >=44px; safe areas and virtual keyboard
   do not cover focused fields or footer actions. Actual breakpoints are recorded as validated/proposed.
4. Keyboard and screen-reader landmarks/link names/focus/selected state work; active destination is
   identifiable without color. Escape/return-focus works for any introduced navigation drawer/dialog.
5. Preview contains synthetic data only, no persisted sensitive cache and no fake save/download success.
6. Audit compatibility review records that this foundation adds no domain/admin mutation or new sensitive
   read; existing beta authoritative audit paths remain unchanged.

Verification: inspect and maintain related existing tests. Add focused behavior tests only for new shared
semantics, destination reachability and keyboard interactions; avoid rendered pixel/framework-internal
contracts. Run npm test --prefix shale-web, npm run typecheck --prefix shale-web and npm run build
--prefix shale-web; inspect both themes/layouts with available browser tooling and report real-device
keyboard/safe-area checks not actually performed. Run the repository change-aware selector and its
relevant local verification under prompt rules, plus git diff --check. No new GitHub test workflows/gates.
Do not claim completion if required checks are blocked; identify exact commands/evidence still needed.
Commit, push the separate branch and open a PR targeting codex/latest. Return files, validation, screenshots
or preview evidence, limits and the next narrowly scoped phase; do not deploy.
```

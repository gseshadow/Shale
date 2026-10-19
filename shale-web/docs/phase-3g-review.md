# Web V2 Phase 3G: data router and bounded Contact draft protection

Separate branch `codex/web-v2-phase-3g`, live base `fcfcd1d35a87c8709ea6a8a5ae9ad246b9e31708`
(2026-10-09). Explicit fetch refreshed the main-only clone's remote-tracking PR base. Ancestry checks
for `e49a434a`, `88c2e5cd`, `8a3d3f96`, `91323921`, `6166f3ab`, `76879ec1`, `c6fb9d74` verify security
and Phases 3A–3F. Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**; appearance **provisional**.
No base push, merge or deployment.

## Ownership and behavior

`app/routeRegistry.ts` retains every existing URL, navigation parent/label/order and safe-return policy.
`App.tsx` owns static `appRouteObjects`, exhaustive typed operational screen composition and public
root/login/fallback redirects. `main.tsx` creates one `createBrowserRouter` outside React; App renders
`RouterProvider`. AppRoutes still owns useStartupSession and unknown verification gating; the explicit
ProtectedRoute remains authoritative and generation-keyed. No loader/action, router API execution,
route additions, feature-module migration or authorization through metadata. Root/login/fallback,
query/hash, push/replace/Back/Forward, return state, shell focus and parent navigation remain covered.
The Contact screen is keyed by history location so deliberate same-screen/suffix navigation cannot keep
an abandoned editor or deliver an old continuation. `foundation.html` keeps its isolated BrowserRouter,
synthetic adapter and API/storage-independent composition.

Installed React Router/DOM **7.18.2** APIs were checked against official version-tagged documentation:
[createBrowserRouter](https://github.com/remix-run/react-router/blob/react-router%407.18.2/docs/api/data-routers/createBrowserRouter.md),
[RouterProvider](https://github.com/remix-run/react-router/blob/react-router%407.18.2/docs/api/data-routers/RouterProvider.md),
[useBlocker](https://github.com/remix-run/react-router/blob/react-router%407.18.2/docs/api/hooks/useBlocker.md),
[useBeforeUnload](https://github.com/remix-run/react-router/blob/react-router%407.18.2/docs/api/hooks/useBeforeUnload.md)
and installed hook/router implementation. useBlocker needs a data router, handles SPA navigation only,
and exposes blocked/proceeding/unblocked with reset/proceed. No unstable window.confirm prompt API.
The document listener is an effect rather than unconditional useBeforeUnload, so clean forms do not
register it. Dependencies/security patches and package/lockfile are unchanged.

**Exact protected editor:** `/contacts/:contactId` → Contact Information → **Edit contact**,
`ContactDetailsForm`. The baseline is its loaded opening snapshot (including existing display-name
fallback and date input projection), replaced only on usable authoritative Save response followed by
editor close/reopen. Trimmed draft scalar comparisons define meaningful scalar changes; internal
whitespace, dates and Deceased remain significant. Phone/email/extensions reuse contactValueUpdate:
exact opening spelling/extension retains even legacy values; changed values SET or CLEAR. No browser
parser guesses server-equivalence. Exact reversion becomes clean. Advisory blur formatting can affect
dirty status but never confirms a save or replaces the opening concurrency witness.

Dirty or pending forms block ordinary shell/detail/router links and same-document Back/Forward. One
native modal uses existing semantic buttons, labelled title/description, initial Keep editing focus,
bounded Tab/Shift+Tab cycling, Escape-as-Keep and focus return. Background is inert while modal.
Keep editing resets the blocker and preserves inputs; Discard proceeds through the router's latest
blocked destination once. Repeated attempts use one prompt. Explicit Cancel closes clean forms;
dirty/pending Cancel uses the same deliberate choices and returns focus to Edit contact on close.
Navigation focuses the existing shell main. Browser review discovered native Shift+Tab escape and the
bounded two-button cycle fixes it. No general dialog framework is added.

Save duplicates are synchronously guarded. Fields stay disabled during Save, but Cancel/navigation
remain available with explicit uncertain-commit text. Discard does not cancel/undo a server commit.
No request replay/automatic resubmit or optimistic request-copy baseline. Only a successful usable
snapshot for the same Contact/tenant confirms Save. Validation/conflict/network/server/unusable-success
responses preserve values and dirty state; advisory callbacks begun before disabled Save are invalidated,
including after failure. Captured component/session guards plus synchronous discard state prevent late
success/error/focus/callback delivery after Cancel, route unmount or session replacement. A known Save
success while a prompt is open closes editor/prompt and stays on Contact Detail; it resets, never replays,
that navigation. Advisory reversion to clean similarly releases an obsolete prompt without replay.

Security invalidation remains synchronous at the existing session owner. Blocker predicates and unload
handlers check the captured generation/credential's current authority, so explicit logout, confirmed
rejection, local Return to sign in and identity replacement bypass prompts. Keyed unmount removes draft
UI/state/listeners and outstanding continuations cannot restore it. Logout still replaces login with
null return state and bounded truthful remote feedback; rejection retains safe return capture. Startup
uncertainty/eight-second deadlines and no-replay behavior remain unchanged. No credentials/drafts are
restored, persisted or resubmitted after sign-in. The modal's normal inert background requires dismissal
before another user action there; it cannot obstruct programmatic/session rejection teardown.

**Unprotected:** ContactCreateForm; Organization create/detail editor; case create/core/assignment/status;
task create/detail; case update composer; other local forms/search/filter state. This is one consumer,
not an all-form guarantee. Browser reload/close/cross-document navigation uses beforeunload only while
dirty/pending, removed on reversion/close/unmount. User activation and browser policy control the generic
prompt; custom text is not guaranteed, Firefox may affect bfcache, and mobile background/process death
may never fire it. No guaranteed mobile protection or persistent recovery. Unsupported older dialog
engines, real devices, assistive technology and other engines require separate acceptance.

## Test impact and validation

Read AGENTS/prompt rules completely; reviewed development/design-system A.2/system overview, Web V2
roadmap, Phase 3A–3F/security reviews, router/forms/accessibility sections, ContactValueInput and Contact
management boundaries, web README/migration/readiness/local-smoke/deployment and test-selection guidance.
Pre-edit repository-wide symbol/consumer searches reviewed App routes/session/feature handlers, registry,
returnPath, shell, preview, input/advisory/mutation consumers and neighboring tests. Existing App tests
now create/dispose a router outside render; their route/session expectations remain intact.

| Production owner | Maintained coverage |
| --- | --- |
| App/main/route composition | App.test + sessionRejectionApp: every existing URL, parent/focus/history/login returns, root/fallback, uncertainty/deadline/logout and stale consumer guards. |
| ContactDetailsForm/ContactDraftProtection | contactDraftApp: real clients/storage plus clean/edit/reversion/SET/CLEAR/extensions/boolean; Stay/Discard/Cancel/POP/suffix; save success/failures/unusable body/pending/late results; duplicate/fallback/obsolete prompts; conditional listener and security teardown/replacement/local return. |
| ContactValueInput | Existing suite plus disabled-Save advisory invalidation; real-client failed-Save/advisory race. |
| Registry/returnPath/shell/preview/primitives/session | Existing full web contracts retained, no metadata or preview migration. |
| Unchanged server auth/Contact API | AuthController, ApiReadController, ContactValueApi compatibility; selector-selected auth and critical reactor. |

| Check | Result |
| --- | --- |
| `npm test --prefix shale-web -- --maxWorkers=1` | **399 passed**, 12 files. Includes 39 Contact draft integration cases and all existing route/session contracts. |
| Focused App/session/draft/input suites | **133 passed**; the final session-fixture rerun also passed all 11 cases. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | **PASS**; operational and foundation entrypoints built. |
| AuthController + ApiReadController + ContactValueApi focused Maven reactor | **58 passed**, no failures/errors/skips. |
| Repository selector against `origin/codex/latest` | Server/AuthController selected; no full-suite escalation. [Selection](phase-3g-evidence/test-selection.md). |
| Selected AuthController Maven reactor | **10 passed**, no failures/errors/skips. |
| Required local critical `mvn test` reactor | **116 passed** across six tested modules; reactor **BUILD SUCCESS**, no failures/errors/skips. |
| Full `npm audit --json`; production `npm audit --omit=dev --json` | **0 vulnerabilities** at every severity. [Full](phase-3g-evidence/audit.json), [production](phase-3g-evidence/audit-production.json). |
| Isolated Chromium 151 browser review | **8 scenarios passed**, 10 captures, Light 320px / Dark 1280px; no console/page errors or unexpected network destinations. [Observations](phase-3g-evidence/browser-observations.json), [compact modal](phase-3g-evidence/dialog-320.png), [wide modal](phase-3g-evidence/dialog-1280.png). |
| `git diff --check`; base-to-head whitespace and dependency diff | **PASS**; package manifest/lockfile unchanged. |

Earlier overlapping runs hit timing limits. A full run also exposed a session fixture taking its
request-count snapshot before the new screen's normal mount reads; it now disposes the old router and
waits for those expected reads, retaining the assertion that late old-login results add zero requests.
The final full suite ran sequentially with one worker and passed. No production deadline was relaxed.

Browser tooling lives only under `work/`;
review fixtures intercept all API calls, abort unexpected destinations and record no payload/credential.
[Browser script](phase-3g-evidence/browser-review.cjs) uses local Vite/system Chromium with external Playwright.
It exercises actual search/card/links/history, native modal keyboard/focus, pending discard and teardown
at 320/1280, proving isolated preview first. Cross-document Back is a document-unload case, separate
from data-router POP; the fixture uses actual same-document router entries for POP assertions.

## Audit, gaps, next milestone and rollback

No new sensitive-read/domain/admin endpoint, mutation or audit seam. Existing Contact update API,
service/DAO timestamp/validation/tenancy/actor and audit owners remain authoritative. Staging, navigation,
Cancel and discard intentionally emit no audit row; browser code cannot author transactional audits.
No audit integration/schema/migration; roadmap §8.2 sensitive-read/task/note gaps remain deferred.
No backend/SQL/API/auth protocol/storage redesign/refresh/cache/deployment/version/native/MCP/AI change.

Synthetic evidence does not establish live backend/revocation/two-tenant/audit or deployed-host acceptance.
Physical-device/screen-reader/Firefox/WebKit and remaining Phase 2 gates stay OPEN. Appearance remains
provisional. Refresh/rotation ambiguity, CredentialStore, broader transport/cache and other editors remain
unfinished. Next bounded **Phase 3H: CredentialStore boundary extraction**, preserving the same per-tab
sessionStorage key/policy and lifecycle guards, with no draft persistence or refresh/replay. Review D3 and
all credential consumers before implementation; refresh/cache/other-form adoption need later scope.

Rollback: revert 3G or rebuild security-patched Phase 3F base `fcfcd1d3` with the same API origin.
This removes the Contact guard/data-router migration while preserving 3A–3F/security patches.
No server/session/SQL rollback; any deployment is separate operator work.

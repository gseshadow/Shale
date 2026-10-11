# R3 Case workspace and limited read-only Overview review

Implemented for review on fetched `origin/codex/latest` `862b8671da698da5469e8d28220bca04bad98944`
(merged #1856), separate branch `codex/r3-case-workspace`. R1, R2, Phases 3A–3J and dependency-security
prerequisites are ancestors of this base; existing lockfile security versions are unchanged. Original
checkout and unrelated work are preserved. No backend/schema/dependency/version changes, merge or deployment.
Appearance remains provisional. Phase 2 acceptance OPEN; Phase 3 IN PROGRESS; Phase 4 first read slice
implemented for review but INCOMPLETE until separate acceptance.

## Launch and review

From the repository root:

```bash
npm ci --prefix shale-web
npm run dev --prefix shale-web -- --host 127.0.0.1
```

Open `http://127.0.0.1:5173/case-workspace`. The existing default API origin is described in
[README](../README.md); it is **not verified to have R2 deployed**. For authorized live review, set only
an approved API origin in `shale-web/.env.local` as `VITE_SHALE_API_BASE_URL=https://<approved-api-host>`
before starting Vite. R2 deployment, bearer, CORS and runtime prerequisites below must be verified first.
Use approved synthetic fixtures; do not paste credentials, case names, tokens or raw responses into evidence.
The isolated `/foundation.html` preview remains synthetic and makes no operational API requests.

1. Sign in through the existing login. Select **Case workspace**. Blank search makes no Case request.
   Submit a case name explicitly or select **Assigned**. Search uses the POST body against the entire active
   tenant dataset; Assigned alone uses the assignment filter. Check a match beyond 25 unfiltered records,
   an unassigned search match and a direct `/case-workspace/<approved-case-id>` link.
2. Next/Previous request fixed size 25. A new query or list selection resets page to zero. Page labels
   describe the visible page, never a total. At API page 100 with `hasMore: true`, Next is disabled and
   instructions ask the user to narrow the search. URLs/history/storage contain no query.
3. Open a Case by ID. The **Limited read-only Overview** contains only Case ID, Case number, Case name,
   Status, Practice area, Responsible attorney, Primary legal assistant and Updated (stored local time).
   It obtains a fresh audited Overview; a list row never substitutes for authorized detail. Read Overview
   again is an explicit audited request. Back restores query/page/cards and the opening button's focus
   within the same identity, without fetching the list again. Reload requires query re-entry.
4. Exercise accepted empty results, 400, 403 (session retained), safe 404, service/audit-unavailable,
   malformed/oversized bodies and fetch/body timeout. Verify clear feedback, live announcements and manual
   retry. Navigate to another ID during a pending read: previous entity clears and late success/failure
   cannot repopulate it. Cancelling does not undo a server audit that already committed.
5. A confirmed current-session 401 clears workspace memory and follows existing explicit re-login/safe
   return. Fresh login opens a direct Overview again; search requires re-entry. No automatic retry,
   request/mutation replay, refresh endpoint, focus/reconnect refresh or background refetch. Logout,
   identity replacement and credential/security failure tear down requests and identity-scoped memory.
   Preserve uncertain startup verification, login deadline and truthful remote-logout feedback.
6. Check both themes at 320/360/768/1280, keyboard-only navigation, visible focus, selected navigation,
   error/result announcements and 200%/400% zoom. Existing **Cases** (`/cases`, `/cases/:caseId`), My Shale,
   Contact and Organization routes and write workflows remain reachable. Unavailable destinations remain
   labelled unavailable. This new composition has explicit routes and uses the existing shell/primitives.

## Wire and lifecycle review

The path uses only `POST /api/v2/cases/search-page`, `GET /api/v2/cases/assigned-page` and
`GET /api/v2/cases/{caseId}/overview` after the existing authentication flow. No broad detail, tasks,
updates, history, contacts, dates, lookups, documents, edit witnesses or mutations are fetched here.
Contract authority is [R2 §11](../../docs/architecture/shale-web-v2-first-read-contract.md#11-r2-minimized-tenant-wide-case-reads--2026-10-10).
Unknown JSON is validated for exact allowlists/nullability/relationships, SQL-int IDs, string bounds,
`#RRGGBB` colors, calendar-valid local timestamps (including Java local `HH:mm` with omitted zero seconds),
requested page/size, unique IDs and consistent `hasMore`. Actual streamed UTF-8 bytes are counted before
rendering: 128 KiB/page and 8 KiB/Overview. Content-Length and TypeScript types are not trusted.

Each deliberate exchange has one eight-second total fetch/body/parse deadline. The narrow `sessionRequests`
extension composes caller cancellation with existing generation/rejection ownership through headers/body,
cleans listeners/timers and rejects ignored-abort late success/failure. StrictMode setup probes are coalesced
before dispatch so each Overview opening makes one audited request. Legacy callers retain their original
session lifetime signal. Memory is bounded to one query/draft (100 characters each), selection, page and
validated page (up to 25 rows); Overview state belongs to its route instance. No persistent query cache.
Diagnostics contain fixed categories only; no response bodies, sensitive field values, queries or tokens.

## Audit compatibility

Overview opening and explicit reread use R2's authoritative required PHI/entity read audit, with server-owned
tenant/actor/Case context and approved sanitized metadata. A failed required audit withholds detail. Browser
code writes no audit rows and makes no claim that cancellation reverses an audit. R2 summary search and
Assigned pages retain their approved audit exemption; navigation/theme/paging add no domain mutations.
No schema migration, new audit event or browser-generated timeline is introduced. Legacy write/audit paths
are untouched. Mocked browser feedback and MockMvc contracts do not prove live audit persistence/grants.

## Executed validation and evidence

The pre-edit inventory and final changed-file mapping reviewed existing sessionRequests, rejection,
CredentialStore, login/startup/logout, route registry, safe returns, App, responsive shell and preview tests.
New contract/client tests protect actual wire validation/deadlines/cancellation; actual-App tests protect
paging, tenant-wide search fixtures, identity cleanup, safe return/Back and legacy editor compatibility.
A synthetic server fixture places 35 nonmatching records before the first match and filters before paging.
No client-side name filtering is used by production code.

| Changed production | Relevant tests reviewed/maintained and executed |
| --- | --- |
| `sessionRequests.ts` | Existing sessionRequests/rejection/credential failure/login/startup/logout suites; new caller/body/byte/deadline client tests. |
| `routeRegistry.ts`, `App.tsx` session/screen composition | Existing registry, safe-return, App, shell/preview tests; new actual-App deep link/re-login/active navigation/legacy editor checks. |
| Case `contracts.ts`, `client.ts` | 54 exact-schema, request/paging, streamed-body/deadline/cancellation/error tests; actual-App fixtures prove body-based server search beyond the first25. |
| `CaseWorkspace.tsx` | 19 actual-App paging/reset/ceiling/Back/identity/failure/StrictMode/no-replay tests; synthetic browser/zoom review. |

```bash
npm test --prefix shale-web -- src/features/cases/client.test.ts src/sessionRequests.test.tsx src/caseWorkspaceApp.test.tsx
npm test --prefix shale-web -- src/caseWorkspaceApp.test.tsx
npm test --prefix shale-web
npm run typecheck --prefix shale-web
npm run build --prefix shale-web
npm audit --prefix shale-web
npm audit --prefix shale-web --omit=dev
mvn -Dmaven.compiler.parameters=true -pl shale-server -am \
  -Dtest=AuthControllerTest,MinimizedCaseReadControllerTest,OpenApiDocumentationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/test-selection -p 'test_*.py'
python3 build/test-selection/select_tests.py \
  --base 862b8671da698da5469e8d28220bca04bad98944 --head HEAD --format markdown
mvn -Dmaven.compiler.parameters=true -pl shale-server -am \
  -Dtest=com.shale.server.controller.AuthControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dmaven.compiler.parameters=true test
git diff --check
```

Results: focused client **54 passed**; actual-App **19 passed**; full web **640 passed / 18 files**;
typecheck/build PASS; both npm audits **zero vulnerabilities**. Focused Maven **20 passed** (auth ownership,
R2 wire and OpenAPI compatibility); selector unit tests **24 passed**. Commit-based selector selected
server/AuthControllerTest without full-suite escalation. Its exact affected-area command above passed **10 tests**; subsequent critical reactor passed **116 tests**, zero failures/errors/skips.
The server area is relevant because this browser consumer relies on established bearer/rejection ownership;
R2/OpenAPI focused checks additionally protect the narrow wire and legacy compatibility. No Java code changed.
Maven uses repository-capable JDK 21/Maven 3.9.11
with `-Dmaven.compiler.parameters=true`, as in the base review. No new tools/dependencies enter the app.

Available synthetic browser checks (start Vite in another terminal; provide an existing Playwright module):

```bash
PLAYWRIGHT_MODULE=/absolute/path/to/existing/playwright \
  node shale-web/docs/r3-evidence/browser-review.cjs
PLAYWRIGHT_MODULE=/absolute/path/to/existing/playwright \
  node shale-web/docs/r3-evidence/zoom-review.cjs
```

`REVIEW_ORIGIN` optionally selects the Vite origin. Zoom uses an isolated `/tmp/shale-r3-zoom-profile`
(`ZOOM_PROFILE` can override). Executed with installed Playwright and Chromium 151.0.7922.173:
**15 browser scenarios** (eight width/theme combinations and seven safe failure/session modes), plus
**four native zoom scenarios**. Zero page errors/excluded requests; no page overflow; native keyboard,
3px visible focus, minimum 44px controls, Back focus/query/page and live-region/AX-tree checks pass.
Native browser Settings zoom at 200%/400% changes DPR to 2/4 and CSS viewport to 640/320 at a fixed
1280×900 physical viewport; this is not CSS zoom or viewport-only emulation. Screenshots and structured
observations are in [r3-evidence](r3-evidence/); fixtures contain only synthetic names and credentials.
Both themes' width and zoom screenshots were visually inspected. The AX tree does not verify speech.

Inherited advisories remain exactly as documented in [R2 evidence](../../docs/architecture/shale-web-v2-first-read-contract.md):
its all-tests run passed core111, reached data813 with **12 failures**, and did not run downstream modules;
all 12 failures reproduced in the unchanged base's 51-test focused command. Additional unchanged-base
`ApiExceptionHandlerTest` has **three failures / four tests**. Those results are prior base evidence,
not R3 passes or R3 reruns. The full Maven suite is not claimed passing; unrelated failures are not fixed.

## Rollback, deployment prerequisites and next acceptance

Rollback removes the R3 browser routes/composition/clients and their tests/documentation, restoring the
previous web assets; remove the caller/body extension only if no later consumer depends on it. Keep all
legacy routes/write workflows, R1/R2 server contracts, dependency-security patches, durable WEB sessions,
required audit policy and persisted audit history. Never bypass audit, relax RLS or delete history to recover.
Server rollback has the separate durable-session token-drain/secret-rotation requirements in existing runbooks.

Before a separately authorized deployment/acceptance, verify the actual R2 server binary/OpenAPI, selected
runtime schema and durable session grants, active-tenant/nondeleted actor, explicit tenant predicates and
non-dbo RLS, tenant/overlay/user relationship rules, required audit event/grants/transaction and viewer,
two-tenant active/deleted/assigned/unassigned fixtures and forced audit failure. Verify audited opening/reread
persists the correct server-owned sanitized context, while approved summary reads remain exempt. Check live
session expiry/rejection/login/logout/storage uncertainty and no-refresh behavior. Check exact HTTPS/CORS,
no-store throughout API/proxies, request-body logging exclusion for search, bearer/body redaction, static host
SPA deep-link fallback and rollback asset/API compatibility. Use the existing deployment runbooks; this task
performs no host or deployment changes.

The next bounded step is a separately authorized **R4 acceptance** against an isolated approved live target:
execute the preceding tenant/RLS/audit/session/host checklist using nonsensitive fixtures, then physical
phone/tablet/desktop keyboard/safe-area/virtual-keyboard checks and real screen-reader speech/zoom in both
themes. Record witnesses, gaps and explicit acceptance before completing Phase 4. Live SQL/performance/RLS,
persisted audit/session, deployed host, real devices and assistive technology remain **UNVERIFIED** here.
Phase 2 acceptance remains OPEN and Phase 3 IN PROGRESS; synthetic evidence does not close those gates.

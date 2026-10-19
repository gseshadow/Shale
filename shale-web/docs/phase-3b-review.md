# Web V2 Phase 3B: startup verification uncertainty and explicit Retry

Separate branch `codex/web-v2-phase-3b-session-retry` from live `origin/codex/latest`
**8cba4b85569b6c9afbfe8b447b169e6be3380e7f** (2026-10-08). GitHub live branch/compare
confirmed merged Phase 3A #1844 and security dependency #1843; `git fetch origin codex/latest`
subsequently succeeded against that same base. No direct base push, merge or deployment.
Phase 2 acceptance remains **OPEN**; Phase 3 **IN PROGRESS**, only 3A/3B implemented for review.

## Response classification and behavior

Inspected `AuthController.me`, `BearerTokenServerSessionResolver`, `ServerRuntimeSessionState`,
`ServerAuthSessionService`, `DurableSessionTokenValidator` and `ApiExceptionHandler`, plus existing
AuthController tests. Missing/invalid/expired/revoked/ineligible sessions produce **401**. Durable
lookup failures propagate to the server error handler; `/me` has no operation-level 403 rejection
contract. Profile lookup can legitimately fall back to the verified principal with nullable names,
false role flags and positive user/tenant IDs.

| `/me` outcome | Startup result |
| --- | --- |
| No local bearer | Signed out; no request. |
| Pending | No installed identity/feature credential; no route/feature effects or credential login mounted. |
| Usable successful JSON | Install verified identity/bearer; reveal requested route. |
| HTTP 401 | Clear via existing storage and use Phase 3A signed-out flow, including safe requested-location capture. |
| Network failure, any other non-success status (including 403/429/5xx), malformed/empty/unusable successful body | Retain stored bearer; keep access blocked; show verification unavailable, Retry and Return to sign in. |

`getCurrentUser` now validates only its existing safe profile contract: object, `authenticated === true`,
positive safe-integer user/tenant IDs, boolean role flags, and string-or-null profile fields. A false
authentication marker in a 2xx body is unusable, not the server's confirmed-rejection response. JSON
parse failures also remain uncertain. This validation applies to the existing login `/me` call;
valid successful login behavior is unchanged. No broad transport or protocol change.

`useStartupSession` owns the bounded state/attempt guard. Unknown states have null in-memory identity
and feature bearer; retaining storage never establishes authentication. Retry reads current storage
and issues only `/me` GET. Duplicate pending attempts coalesce synchronously; no timer, automatic
retry, refresh, login replay or domain-mutation replay. Attempt generation, mount cleanup and stored
bearer equality discard stale outcomes, including stale 401 after token replacement. A replacement
discovered at completion remains unverified until explicit Retry; direct removal becomes signed out.

The recovery screen retains the complete requested URL while unknown. Success restores that same
route without adding history. Return to sign in is local: invalidate, clear existing per-tab storage,
replace `/login` and clear return state together under the existing Phase 3A transition pattern. It
does not promise server revocation or call remote logout. Confirmed rejection retains safe deep-link
login restoration; established authenticated logout keeps its existing remote call. No feature/API,
shell/My Shale, router allowlist or preview semantics were changed.

Loading has a persistent polite/atomic status; uncertainty has a safe alert, never raw error text.
Native semantic buttons expose disabled/busy Retry. Initial recovery and returned login headings
receive focus. Chromium drops disabled-button focus, so a focused Retry hands focus to loading status;
failure returns focus only if the user has not moved elsewhere. Return remains usable while pending.

## Test impact and evidence

Read AGENTS/prompt rules completely; routed through development/design-system A.2, system overview,
Web V2 roadmap, Phase 3A review, web README, migration Step 2/3, API readiness/deployment/local-smoke
guides, durable WEB session 7B/8A/8B records and test-selection guidance. Pre-edit repository searches
mapped startup/login/logout/history to `App.test.tsx`; inspected neighboring shell, primitive, preview,
return-path/input tests and server auth tests. The old all-failure-clears expectation now uses an actual
401; existing failed credential-login expectations remain unchanged.

| Production owner | Coverage |
| --- | --- |
| AppRoutes, AuthState, ProtectedRoute, LoginPage/recovery focus | Existing App route/login/logout/history suites plus pending/unavailable blocking, Retry/repeated failure/duplicates, rejection capture, explicit local return, late success and historical Back tests. |
| useStartupSession, getCurrentUser/storage | Real API/storage hook tests: no bearer, success, network, nine non-401 statuses, malformed/empty/false/invalid profiles, duplicate attempts, sign-out/replacement/newer attempt/unmount/StrictMode stale outcomes. |
| Unchanged shell/My Shale/preview/returnPath/validation | Full existing suites retained and rerun. |

Executed from repository root; Maven uses `source /workspace/.tools/shale-env.sh` (Java 21).

| Command | Result |
| --- | --- |
| `git fetch origin codex/latest` | Passed; live base above. Initial restricted-sandbox proxy failure resolved with the supported network-enabled execution permission. Refetch hydrated the complete base objects. |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | Passed; package/lockfile unchanged. An initial offline-only attempt lacked a cached package and did not count as validation. |
| `npm test --prefix shale-web -- src/App.test.tsx src/useStartupSession.test.tsx` | 93 passed. |
| `npm test --prefix shale-web` | 193 passed, seven files. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | Passed; operational and isolated foundation entries retained. |
| `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown` | [Selection](phase-3b-evidence/test-selection.md): server bearer compatibility/AuthController; no full-suite escalation. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 10 passed; verifies the unchanged server contract consumed by startup/login. |
| `mvn test` | Critical reactor 116 passed; zero failures/errors/skips. |
| `PLAYWRIGHT_MODULE=/workspace/work/browser-tools/node_modules/playwright node shale-web/docs/phase-3b-evidence/browser-review.cjs` | Four scenarios passed; eight captures; zero page errors/unexpected requests. Chromium 151.0.7922.173 / Playwright 1.64.0. |
| `git diff --check`; `git diff origin/codex/latest HEAD --check` | Passed. |
| `git diff --exit-code origin/codex/latest -- shale-web/package.json shale-web/package-lock.json` | Unchanged; security patches intact (router 7.18.2, PostCSS 8.5.23). |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | [All dependencies](phase-3b-evidence/audit.json) / [production](phase-3b-evidence/audit-production.json): zero vulnerabilities at every severity. |

The browser's first run reproduced lost focus on disabled Retry; the status-focus handoff fixed it.
A focused test run also exposed existing neighboring Phase 3A assertions that treated heading render
as proof a feature effect had executed. Those request assertions now wait for the actual call, keeping
the same expected arguments/counts. Focused and full suites passed after correction. Final affected-symbol
search and production-to-test diff review retained login, shell, returnPath and preview coverage.

[Browser script](phase-3b-evidence/browser-review.cjs) intercepts every API request and aborts unexpected
remote destinations. External Playwright and system Chromium run against local Vite; fixtures are
outside operational imports and application dependencies. [Observations](phase-3b-evidence/browser-observations.json)
record outage → repeated failure → pending Retry → exact Contact detail and outage → local sign-in →
late result discarded → ordinary login default, each at 320/1280. Native Tab/Enter/Space, pending
duplicate prevention, safe history, no protected requests while unknown, no login/mutation replay,
focus, reflow and >=44px buttons are checked. Dev StrictMode replays existing feature mount reads;
this is not a Retry or mutation replay and was not redesigned here. Eight synthetic captures accompany
the observations. These checks do **not** establish live backend/session, tenancy or audit acceptance.

Reproduce with local Vite and independent browser tooling:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
npm install --prefix work/browser-tools --no-save playwright
PLAYWRIGHT_MODULE="$PWD/work/browser-tools/node_modules/playwright" \
  node shale-web/docs/phase-3b-evidence/browser-review.cjs
```

## Gaps, audit compatibility and rollback

No live backend/session acceptance; no physical device, real screen reader, Firefox/WebKit, native
zoom or real-host acceptance added. Phase 2 operator gates remain OPEN. Existing storage policy is
unchanged and remains JavaScript-readable. Broader refresh/storage/transport/cache/dirty-form work
is unfinished. Established-session response coordination and truthful remote logout outcomes are
separate work; there is no background recovery loop or stored credential queue.

Audit review: routine `/me` validation uses existing server authority/lifecycle, not a new sensitive
view endpoint or mutation. Local Retry/navigation/credential clearing intentionally create no audit
row. No new audit integration/schema/migration; existing server/DAO authorization, tenant, validation,
concurrency and audit enforcement remain authoritative. Roadmap §8.2 gaps are deferred.

Rollback: revert only Phase 3B or rebuild security-patched Phase 3A base `8cba4b85` with the same API
origin. This restores the known startup-all-failures-clear behavior, without server/session/SQL
rollback. Preserve dependency-security patches. Deployment remains separate operator work.

Next bounded **Phase 3C**: inspect established-session logout transport outcomes and provide truthful
feedback when remote revocation is unconfirmed, while preserving immediate local teardown and safe
history. No background credential retention, refresh coordination or mutation replay in that slice.

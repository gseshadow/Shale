# Web V2 Phase 3C: truthful remote-logout outcome feedback

Task branch `codex/web-v2-phase-3c-logout-feedback` starts at fetched live `origin/codex/latest`
**a60297be90fc51e225c715396f909bd50f90a64a** (2026-10-08). Git ancestry verifies merged Phase 3A
#1844, Phase 3B #1845 and dependency-security #1843. The checkout initially had a main-only fetch
refspec; explicit fetch into `refs/remotes/origin/codex/latest` hydrated the current remote base.
No direct base push, merge or deployment. Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**.

## Response authority and exact behavior

Reviewed `AuthController.logout/currentToken`, `LogoutResponse`, bearer resolution/runtime principal,
`ServerAuthSessionService.logout/validate`, durable token validation, SQL store and owner-qualified
`UserSessionDao.revoke`, plus the existing controller/durable/logout tests. The endpoint returns HTTP
200 with **`revoked`**, not a generic fetch-success marker. A verified eligible token invokes logout
before returning `revoked: true`; absent/invalid/ineligible tokens can return `revoked: false` with the
same “Logged out.” message. Malformed bearer syntax may produce 401. Other 401 causes include expiry,
rotation, missing/revoked sessions or ineligible owners; this does not identify a committed revocation.
Storage/service errors are failures, not positive acknowledgments.

Bound-session logout revokes only the presented session's `sid`, retaining token-derived tenant/user
ownership and the first revocation timestamp/reason. The legacy compatibility path only revokes the
presented JTI in process memory. The browser treats the bearer as opaque and does not infer which path
ran, other devices' state, or revocation of other sessions. Its confirmation says: “The server confirmed
revocation of the session used here.” This is endpoint acknowledgment, not independent live SQL proof.

| Remote result | Signed-out feedback |
| --- | --- |
| Single attempt in flight | “You are signed out in this tab. Waiting for the server to confirm session revocation…” |
| HTTP 200 and parsed object with `revoked === true` | Local sign-out plus server confirmation for the session used here. |
| Other HTTP status (including 201/202/204/401), false/missing/wrong-type marker, empty/malformed body, network/redirect error | “You are signed out in this tab. Server session revocation could not be confirmed. The session may still be active on the server. You can sign in again.” |
| Eight-second deadline, including stalled body reading | Same unconfirmed feedback; abort does not establish whether revocation committed. |
| New verified login, local lifecycle invalidation or unmount | Abort the old attempt; discard its late result. No stale feedback, storage clearing or navigation in the newer session. |

`AppRoutes` groups logout teardown and replacement navigation in Phase 3A's existing transition.
The hook synchronously invalidates the lifecycle, removes the stored bearer, clears identity/feature
credentials, and starts one POST with the captured bearer. Protected content unmounts while the
request is held. `/login` replaces the current entry with null return state; historical protected
entries remain guarded. Local startup **Return to sign in** still calls only `signOut`; it never
silently calls the remote endpoint or promises revocation.

One synchronous activation guard plus the render's generation rejects duplicate/stale handlers.
New login and unmount abort through the same generation owner. The transport bounds fetch **and body
reading**, races cancellation even if a synthetic promise ignores AbortSignal, clears timers/listeners,
and consumes late settlement. Redirect following is disabled for this single logout POST. No timeout
or failed response restores credentials; no retry button, automatic retry, refresh, background queue,
credential replay or persisted feedback exists. The only old bearer is scoped to the in-flight
exchange; the lifecycle cancellation handle contains no bearer. Reload drops feedback and makes no
logout call because local storage was already cleared.

Login remains enabled while confirmation is pending/unavailable. Shared `Feedback` accepts paragraph
ARIA attributes without changing existing status/alert ownership. Login mounts an initially empty,
persistent polite/atomic status and updates its text after mount. The existing login heading focus
remains; a remote result never moves input focus. Failure text never displays server message/error
payloads, credentials or identifiers. Login stays in the established unauthenticated Light presentation.

## Test impact and validation

Read complete AGENTS/prompt rules and routed development/design-system A.2/system overview, Web V2
roadmap, Phase 3A/3B reviews, web README, Step 2/3, API readiness/local-smoke/deployment boundaries,
durable session 7B/8A/8B and test-selection guidance. Pre-edit searches covered every logout/signOut
consumer and related tests. The only operational web remote consumer was AppRoutes; it now delegates
to the hook. Historical synthetic review scripts returning `{}` remain correctly unconfirmed fixtures.

| Production owner | Maintained coverage |
| --- | --- |
| AppRoutes/LoginPage/history | Existing App logout/return-path/recovery tests plus persistent pending/confirmed/unavailable status, input focus, safe error text, usable login and late-result discard. Storage mocks now model clear/install behavior. |
| useStartupSession/signIn/signOut/generation | Existing startup recovery/replacement/unmount/StrictMode tests retained; real-fetch/storage logout tests verify immediate clear, synchronous duplicate/stale guard, timeout, new login (even reused opaque value), late rejection and cancellation/unmount. |
| api.logout | Real response tests for true/false/unusable body, non-contract HTTP statuses, malformed/empty JSON, network error, stalled fetch/body, abort, deadline, no storage restoration/replay and consumed late rejection. |
| Shared Feedback | Stable polite/atomic paragraph attributes and retained alert ownership; all shell/preview/returnPath/input suites retained. |
| Unchanged server authority | AuthControllerTest, ServerAuthSessionLogoutTest and DurableSessionAuthTest establish the consumed contract, exact-session ownership and durable/JTI validation semantics. |

Commands run from repository root. Maven uses `source /workspace/.tools/shale-env.sh` (Java 21).
Network operations and local Vite/browser use the supported network-enabled execution permission;
initial restricted-shell proxy/listen failures were resolved without changing proxy/CA/deployment settings.

| Command | Result |
| --- | --- |
| `git fetch origin codex/latest`; explicit `git fetch origin codex/latest:refs/remotes/origin/codex/latest` | Passed; base and ancestry above. |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | Passed; manifests/locks unchanged. |
| `npm test --prefix shale-web -- src/App.test.tsx src/logout.test.tsx src/ui/primitives.test.tsx src/useStartupSession.test.tsx` | 145 passed, four files. |
| `npm test --prefix shale-web` | 229 passed, eight files. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | Passed; operational and foundation build entries retained. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.ServerAuthSessionLogoutTest,com.shale.server.runtime.DurableSessionAuthTest -Dsurefire.failIfNoSpecifiedTests=false test` | 14 passed, zero failures/errors/skips. |
| `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --output shale-web/docs/phase-3c-evidence/test-selection.md` | [Selection](phase-3c-evidence/test-selection.md): server/AuthController compatibility; no full-suite escalation. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 10 passed; unchanged auth endpoint is the browser contract owner. |
| `mvn test` | Critical reactor 116 passed, zero failures/errors/skips. |
| `PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright node shale-web/docs/phase-3c-evidence/browser-review.cjs` | 12 scenarios, eight captures; zero page errors/unexpected requests/unhandled rejections. Chromium 151.0.7922.173 / Playwright 1.64.0. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | [Full](phase-3c-evidence/audit.json) / [production](phase-3c-evidence/audit-production.json): zero vulnerabilities, every severity. |
| `git diff --check`; package/lockfile diff against base | Passed/unchanged. Router 7.18.2 and PostCSS 8.5.23 security patches retained. |

[Browser script](phase-3c-evidence/browser-review.cjs) uses external Playwright/system Chromium and local
Vite only, intercepts every API request and aborts unexpected remote destinations. Review fixtures are
outside operational imports/application dependencies. [Observations](phase-3c-evidence/browser-observations.json)
record each result at 320/1280, native Tab/Enter/Space, duplicate activation, heading/input focus,
reflow, safe history and no protected-content return. Preview makes zero API calls. Screenshots:
[pending](phase-3c-evidence/pending-320.png), [confirmed](phase-3c-evidence/success-320.png),
[HTTP unavailable](phase-3c-evidence/http-320.png), [timeout](phase-3c-evidence/timeout-320.png),
with corresponding 1280px captures. No credential values appear in captures.

Reproduce with local Vite on port 5173 and independent tooling:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
npm install --prefix work/browser-tools --no-save playwright
PLAYWRIGHT_MODULE="$PWD/work/browser-tools/node_modules/playwright" \
  node shale-web/docs/phase-3c-evidence/browser-review.cjs
```

## Audit, remaining gaps and rollback

Existing auth logout uses the authoritative durable-session lifecycle/security ownership, not a
browser-created PHI/entity/admin audit row. This change adds feedback/cancellation/local navigation,
intentionally unaudited. No new sensitive-read endpoint, domain/admin mutation, audit integration,
schema or migration. Existing authorization, tenancy, validation, concurrency, audits and operational
UI owners remain unchanged; roadmap §8.2 audit gaps stay deferred. Preview isolation is retained.

Synthetic/browser and mocked server tests **do not establish live revocation acceptance**, multi-replica
SQL enforcement, deployed profile/legacy cutoff, tenant/audit acceptance or real-host behavior. Physical
devices, real screen readers, Firefox/WebKit and Phase 2 operator gates remain OPEN. Persistent live-region
checks do not prove spoken pending/confirmed/unavailable announcements. Operators should separately
verify those messages and usable keyboard login, then verify a real bound session's acknowledgment and
rejection of its old bearer without printing/storing it in review artifacts. No live check was run here.

Broader established-session response coordination, refresh/storage redesign, transport/cache and dirty-form
work remain UNFINISHED. Startup verification still has no automatic deadline; local Return to sign in
remains available. Next bounded **Phase 3D**: give startup `/me` verification a deadline that preserves
uncertainty/storage and explicit Retry/local return, without automatic replay or refresh coordination.
Phase 2 acceptance remains **OPEN**; Phase 3 **IN PROGRESS**.

Rollback: revert only Phase 3C or rebuild security-patched base `a60297be` with the same API origin.
That restores the known unchecked/logout-without-feedback behavior; no backend/session/SQL rollback.
Retain dependency-security patches and Phase 3A/3B. Any deployment remains separate operator work.

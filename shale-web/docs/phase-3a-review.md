# Web V2 Phase 3A: safe login return-path restoration

Implemented for review on `codex/web-v2-phase-3a-return-path` from explicitly fetched live
`origin/codex/latest` **151d05a9f34af2922dddc94036bc446b22bf465b** (2026-10-08).
Merged Phase 2D PR #1842 and dependency-security PR #1843 are present; ancestry checks for
`00cc9abc` and `e49a434a` pass. Phase 2 remains **IN PROGRESS; acceptance OPEN**.
The user explicitly authorized this bounded Phase 3 slice before Phase 2 acceptance closure.
Phase 3 is **IN PROGRESS; only 3A implemented for review**. No merge or deployment.

## Reproduction, cause and resulting behavior

Before production edits, changed the existing App verification-failure/login test's expectation from
My Shale to Contact Detail. On untouched production, the focused test failed with My Shale rendered,
after capturing `/contacts/7` in login state. This reproduces the defect with synthetic API mocks.

`ProtectedRoute` already replaces the requested route with `/login`, carrying the complete location
in `state.from`. After login and `/me` verification, `LoginPage` previously installed auth and navigated
to the saved pathname. Auth installation independently rendered `AppRoutes`' `/login` element as
`Navigate /my-shale`. These competing updates discarded the requested destination. The old helper
also ignored query/hash and accepted any string pathname.

The authenticated `/login` element is now the sole owner of return navigation. `LoginPage` retains
existing credential submission and `/me` verification; it installs auth without a second navigation.
`returnPath.ts` validates navigation state and returns a declared protected router-relative path with
its original query/hash, or the existing `/my-shale` default. It uses React Router `matchPath`, including
existing case-insensitive/trailing-slash matching. There is no BrowserRouter basename or Vite subpath
base in this application; targets outside the existing root routes are rejected. The small explicit
allowlist must be maintained alongside future protected route declarations; no new route is introduced.

Reject non-object/wrong-type state, schemes, absolute/protocol-relative paths, backslashes, controls,
raw whitespace, malformed URI escapes, path dot segments/encoded separators, mixed pathname/query/hash
components, undeclared routes and login-loop destinations. Encoded query values and valid hash fragments
are preserved without decoding/re-encoding the destination. This adds no new filter semantics or URL
writer; the roadmap's future per-feature nonsensitive-query policy remains unfinished. Do not introduce
sensitive values into URLs. No query or return target is logged to operational telemetry.

Restoration occurs only with verified auth, replaces login and consumes return state (`state: null`).
Failure during credential login or `/me` keeps the login location/target for deliberate retry and does
not read protected detail or install a token. Existing authenticated deep links/startup verification,
root/default/unknown redirects and ordinary push/back/forward remain supported.

Logout removes the token immediately and replaces the current entry with `/login` without return state.
The location and signed-out React state updates share `startTransition`, matching BrowserRouter's
transitioned location update; this prevents an intermediate signed-out render from recapturing the
just-left detail. Protected content unmounts before the remote logout completes. Historical protected
entries reached by Back still require authentication; their newly captured target is a fresh protected
route request. Explicit login after logout uses My Shale. Remote logout status handling and broader
async session-generation coordination remain outside this slice.

## Test-impact inventory and checks

Read AGENTS/prompt rules completely and followed UI/web routing through development rules, canonical
design system/A.2, system overview, Web V2 roadmap, Phase 2B/2C/2D reviews and operator checklist,
web README, migration Step 2/3, relevant readiness/deployment, durable WEB session 7B/8A/8B and local
smoke/testing selection guidance. Inspected AppRoutes/ProtectedRoute/LoginPage, auth state/effects,
API login/me/logout/storage, all route declarations and the existing App/shell/preview/primitive/input
suites before selecting the fix. Repository-wide searches mapped old login behavior and auth/history/
logout contracts to `App.test.tsx`; neighboring tests were retained. No obsolete My Shale-return
expectation remains. Changed production-to-test map:

| Production owner | Reviewed/maintained coverage |
| --- | --- |
| AppRoutes / LoginPage | Existing verification-failure test updated; case/contact/task return, delayed `/me`, failed credentials/verification then success, default/invalid state and replacement history added. Existing all-route/startup/shell/completion coverage retained. |
| handleLogout / ProtectedRoute interaction | Existing local-before-remote logout test retained; detail return consumption, logout default and protected Back boundary added. |
| returnPath.ts | Focused allowlist/query/hash and invalid/external/malformed/auth-loop tests in `returnPath.test.ts`. |
| Unchanged shell/preview/validation | Complete existing suites rerun for focus/history, API/storage-independent preview and input semantics. |

Executed from repository root (Java 21/Maven via `source /workspace/.tools/shale-env.sh`):

- `npm ci --prefix shale-web --cache /workspace/.cache/npm`: passed, existing lockfile restored.
- Before edits: `npm test --prefix shale-web -- src/App.test.tsx -t 'preserves verification failure'`:
  expected failure reproducing My Shale landing instead of Contact Detail.
- `npm test --prefix shale-web -- src/App.test.tsx src/returnPath.test.ts`: **102 passed**.
- `npm test --prefix shale-web`: **143 passed**, six files.
- `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web`: passed;
  both operational and isolated foundation entries retained.
- `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`:
  [selection](phase-3a-evidence/test-selection.md). Browser consumer ownership selects server bearer
  compatibility/AuthController; relevant despite no server edits. No all-suite escalation.
- `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **10 passed**.
- `mvn test`: critical reactor **116 passed**, zero failures/errors/skips.
- `git diff --check`: passed. Production/test diff and affected-symbol searches reviewed again.
- `git diff --exit-code origin/codex/latest -- shale-web/package.json shale-web/package-lock.json`:
  unchanged. `npm audit --prefix shale-web --json`: **zero vulnerabilities**, all severities.
  Security patches remain intact: router 7.18.2 plus patched locked CSS tooling; no dependency/version edits.

### Synthetic browser evidence

[Review script](phase-3a-evidence/browser-review.cjs) intercepts every API request and aborts unexpected
remote requests. External Playwright **1.64.0**, Chromium **151.0.7922.173**, local Vite only;
fixtures/tooling are outside operational imports and application dependencies. No live authentication,
backend, tenancy, authorization or audit acceptance is implied by synthetic service responses.

[Observations](phase-3a-evidence/browser-observations.json): **six** case/contact/task detail round trips,
both shell themes at 1280×900. Query/hash match exactly. No protected read before login or while `/me`
is held; task flows include failed credentials then successful login. No transient My Shale reads after
successful detail login. History length stays stable across replacements; Back reaches the preceding
preview document without a login loop, Forward restores verified detail. Authenticated My Tasks navigation
and Back/Forward preserve detail suffixes. Logout clears state/token; Back does not read detail while
signed out. Direct default login and seeded protocol-relative state use My Shale. Zero page errors and
unexpected requests. Six captures: [case Light](phase-3a-evidence/cases-light.png)/[Dark](phase-3a-evidence/cases-dark.png),
[contact Light](phase-3a-evidence/contacts-light.png)/[Dark](phase-3a-evidence/contacts-dark.png),
[task Light](phase-3a-evidence/tasks-light.png)/[Dark](phase-3a-evidence/tasks-dark.png).
Details retain their existing beta Light presentation within either shell theme.

An initial harness assertion treated revisiting the *same* `/login` URL after Back as a fresh login;
Chromium retained that entry's newly captured return state. The script now creates a distinct direct-login
entry (`/login?direct=1`) for the no-target check. This was a fixture assumption, not a production defect.

Reproduce with the Vite server on port 5173 and independent tooling:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
# In another terminal; no application dependency change:
npm install --prefix work/browser-tools --no-save playwright --cache /workspace/.cache/npm
PLAYWRIGHT_MODULE="$PWD/work/browser-tools/node_modules/playwright" \
  BROWSER_EXECUTABLE_PATH=/usr/bin/chromium node shale-web/docs/phase-3a-evidence/browser-review.cjs
```

## Acceptance limits, audit and rollback

[Phase 2D operator record](phase-2d-operator-checklist.md) records user-reported Windows Firefox synthetic
completion success/failure, native 200%/400% zoom and states in both themes, keyboard/skip/disclosure/Escape/
independent Complete, Narrator's task-named pending speech and readable screen-reader structure/errors.
Success speech is **assumed by the user, NOT verified**: releasing the response required switching to
Command Prompt. Exact Firefox version, complete screen-reader coverage and full acceptance are not inferred.
Physical devices, Safari/WebKit and live-host/backend checks remain OPEN. This routing review does not
claim those checks, a new accessibility matrix, or live authentication acceptance.

Audit compatibility: navigation creates no new domain/admin mutation or sensitive-read endpoint/audit seam.
Existing verified login/me/logout and selected-detail service/DAO authority remain unchanged; returned
identifiers do not confer authorization. Existing tenant isolation, validation, concurrency and audit
owners remain authoritative. No browser-authored audit row or schema/migration is added; navigation
mechanics intentionally have no audit event. Roadmap §8.2 read/task/audit gaps remain deferred. Shell,
task completion and preview isolation remain covered; no backend/SQL/protocol/storage/transport/native/
deployment configuration or MCP/AI activation change.

Frontend rollback: revert only Phase 3A commits or rebuild security-patched base `151d05a9` with the same
API-origin setting. This restores the known return defect but retains Phase 2D and security updates.
No server/session/SQL rollback is required. Any deployment is separate operator work; none occurred here.

Next bounded **Phase 3B**: startup verification uncertainty — distinguish confirmed authentication rejection
from transient network/5xx, keep protected content blocked while unknown, and offer explicit Retry with
focused tests. Broader canonical routing, session generation/refresh coordination, transport/cache,
credential abstraction and dirty-form blockers are **UNFINISHED**. Phase 2E acceptance continues independently.

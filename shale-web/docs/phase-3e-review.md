# Web V2 Phase 3E: canonical existing-route registry

Implemented for review on `codex/web-v2-phase-3e-route-registry` from fetched live
`origin/codex/latest` **1280257ac634e093e4f2f81db24a6b4e661fa820** (2026-10-09).
Ancestry includes merged Phase 3A #1844, 3B #1845, 3C #1846, 3D #1847 and dependency-security #1843.
Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**. No base push, merge or deployment.

## Ownership and preserved contracts

- `src/app/routeRegistry.ts` owns all 15 existing route identities/patterns, including root, login
  and fallback, explicit navigation membership/parent metadata, labels/order and safe-return eligibility.
  Its operational projection drives declarations; its destination projection drives shell navigation.
  Exact Router matching replaces prefix guesses, preserving case-insensitive/trailing-slash behavior.
  Detail routes select their declared list destination; undeclared subsections do not become routes.
- `AppRoutes` retains readable, exhaustive typed screen composition and explicit `ProtectedRoute`/shell
  nesting. Startup/session and server authorization remain authoritative. Neither navigation availability
  nor safe-return metadata grants access. Root/login/fallback and replacement/state semantics are unchanged.
- `returnPath.ts` retains every Phase 3A validation layer; only recognition/default ownership changes.
  Same-origin router-relative allowlisting still rejects malformed escapes, controls/whitespace/backslashes,
  encoded separators/dot segments, mixed components, external/loop/unknown/unavailable destinations.
  Existing parameter semantics and query/hash restoration remain unchanged; no numeric-ID restriction added.
- Existing card/create/related-entity links now use a typed builder deriving patterns/parameter names from
  the registry. Back links retain their original destinations, including Task detail → My Shale.
  Shell active parent for Task detail remains My Tasks. No new routes, aliases, screens or capabilities.
- `shell/navigation.ts` projects registry metadata. `preview/navigation.ts` explicitly adapts synthetic
  links/active state, including Calendar/Reports/Search; these remain noninteractive Unavailable labels
  operationally and are excluded from declarations/returns. Preview stays API/storage independent.
  Registry imports only installed Router helpers, never features/shell/API; preview imports do not enter
  the operational graph. No runtime import cycle or feature extraction out of App.tsx.
- Phase 3B/3D unknown verification, eight-second deadlines, deliberate Retry and stale-response guards;
  Phase 3C immediate local logout and bounded truthful remote feedback; history, authenticated deep links,
  route focus, feature/service/mutation behavior and preview isolation are preserved.

## Inspection and validation

Read complete AGENTS/prompt rules; reviewed development/design/system guidance, Web V2 roadmap,
Phase 2B navigation and Phase 3A–3D/security reviews, web README, migration/readiness/deployment boundaries
and change-aware testing guidance. Pre-edit searches inspected route/link/return/navigation/preview consumers
and neighboring tests. Changed-file review mapped App/routes/links to App tests; recognition to returnPath
and new registry tests; shell/adapter to existing shell/preview suites. Startup/logout/input/primitives
suites remain intact. Independent expected URL/navigation inventories prevent silently accepting new routes.

Commands from repository root (Maven: `source /workspace/.tools/shale-env.sh`, Java 21):

| Command | Result |
| --- | --- |
| `git fetch origin codex/latest`; `git fetch origin codex/latest:refs/remotes/origin/codex/latest` | Passed; remote base above; separate task branch. |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | Passed; unchanged manifest/lock installed. |
| `npm test --prefix shale-web -- src/app/routeRegistry.test.ts src/returnPath.test.ts src/App.test.tsx src/preview/FoundationPreview.test.tsx src/shell/ResponsiveShell.test.tsx` | 185 passed, five files. |
| `npm test --prefix shale-web` | 298 passed, nine files, including all Phase 3A–3D regressions. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | Passed; both operational/foundation entries retained. Initial test-only TypeScript errors were corrected before final build. |
| `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --output shale-web/docs/phase-3e-evidence/test-selection.md` | [Selection](phase-3e-evidence/test-selection.md): server/AuthController compatibility, no full-suite escalation. Relevant to the preserved browser bearer/login/me contract. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 10 passed; zero failures/errors/skips. |
| `mvn test` | 116 passed; zero failures/errors/skips. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | [Full](phase-3e-evidence/audit.json) / [production](phase-3e-evidence/audit-production.json): zero findings at every severity. |
| `git diff --check`; `git diff origin/codex/latest HEAD --check`; package/lock diff against base | Passed/unchanged; security patches retained. |

Synthetic Chromium 151.0.7922.173 / external Playwright 1.64.0 fixtures intercept every API request,
abort unexpected destinations, and never enter application imports/dependencies. Three scripts under
`phase-3e-evidence` reproduce prior regression fixtures in fresh output directories, with added login
active-parent/focus/unavailable/fallback assertions. **22 scenarios, 26 captures**, zero page errors or
unexpected requests: six case/contact/task login round trips in both themes at 1280; four startup recovery
scenarios at 320/1280; twelve logout scenarios at 320/1280. Exact suffix/restoration/replacement/history,
blocked pre-verification reads, Back/Forward, active state, local teardown and truthful feedback pass.
[Login observations](phase-3e-evidence/login/browser-observations.json),
[startup observations](phase-3e-evidence/startup/browser-observations.json),
[logout observations](phase-3e-evidence/logout/browser-observations.json).

Reproduce with local Vite and tooling isolated under `work/`:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
npm install --prefix work/browser-tools --no-save playwright --cache /workspace/.cache/npm
# Run separately for login, startup and logout:
PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright \
  node shale-web/docs/phase-3e-evidence/login/browser-review.cjs
```

## Gaps, next milestone, audit and rollback

Current appearance is **provisional**. User preference: prioritize functionality and deployment, then
perform a dedicated visual refinement pass. Prior visual checks do not constitute final aesthetic approval.
Accessibility and security acceptance remain distinct gates; aesthetic deferral does not waive them.
Synthetic evidence establishes neither live backend/session/revocation/tenant/audit nor real-host acceptance.
Remaining physical-device, screen-reader/other-engine and Phase 2 operator gates stay OPEN. Broader refresh,
CredentialStore, transport/cache, established-session coordination and data-router/dirty blockers remain
UNFINISHED. Query/filter policy and credential-login deadlines remain separate decisions.

Next bounded **Phase 3F: established-session rejection coordination**: introduce a session-generation-bound
boundary for authenticated request failures, prevent stale old-session 401s from clearing newer identity,
keep operation-level 403 distinct, and reconcile current authority through deliberate bounded `/me`
verification without mutation replay or automatic credential replay. Dependencies: inspect all feature/API
error consumers and server rejection contracts; agree unknown-state/write-blocking behavior under D3;
reuse 3B–3D lifecycle/cancellation and registry restoration; inventory tests for login/logout/replacement
and outstanding requests. Refresh/rotation ambiguity, storage abstraction, query cache and dirty blockers
remain separately scoped follow-ups. Phase 2 acceptance continues independently.

Audit compatibility: no new sensitive-read endpoint or domain/admin mutation/audit seam. Existing service/DAO
authorization, tenant, validation, concurrency and audits remain authoritative. Navigation/builders/preview
mechanics intentionally emit no audit row; no audit integration/schema/migration required. Roadmap §8.2
sensitive-read/task audit gaps remain deferred. No backend/SQL/API/auth protocol/dependency/version/native/
deployment/MCP/AI changes.

Rollback: revert only Phase 3E or rebuild security-patched base `1280257a` with the same API origin.
This restores the separate path definitions while retaining Phase 3A–3D. No server/session/SQL rollback;
any deployment remains separate operator work.

# Web V2 Phase 3D: bounded startup session verification

Task branch `codex/web-v2-phase-3d` starts at live `origin/codex/latest`
**f7ab40c8abc624aac5d6fcc3eb4b05b3f4b9cc62** (2026-10-09). Git ancestry confirms merged
Phase 3A #1844, 3B #1845, 3C #1846 and dependency-security #1843.
Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**. No merge or deployment.

## Deadline and behavior

Each startup or explicit Retry has **eight seconds**, matching the existing logout budget: long enough
for an ordinary verification exchange while giving stalled startup a finite recovery point. The budget
starts with the attempt and covers fetch, response JSON consumption and profile validation together;
headers do not reset it. Browser suspension/event-loop scheduling can delay timer delivery; this is a
client recovery deadline, not a real-time guarantee or a server execution limit.

`useStartupSession` owns the deadline and its cancellation race, following Phase 3C's existing approach.
`getCurrentUser` accepts an optional signal but has no default deadline. Its only other operational
consumer, credential login, keeps its existing behavior and one-argument call. No shared transport or
logout implementation is refactored.

Timeout aborts the attempt, retains the stored bearer, installs no identity/feature credential and enters
the existing unavailable-verification state. Protected content and all feature effects stay unmounted;
Retry and local Return to sign in remain available. Retry gets a fresh full budget, reads the current
bearer and synchronously prevents duplicates for that in-flight credential. There is no automatic retry.
Confirmed `/me` 401 before the deadline still clears storage; other errors/unusable profiles remain uncertain.
A timed-out late 401 cannot clear the bearer, and late success cannot install identity.

Generation/mount/token guards remain authoritative, together with racing the complete exchange even
when cancellation is ignored. Superseding Retry, verified login, local sign-out and unmount abort pending
startup work; timers clear synchronously on cancellation and in finally on success/failure/timeout, and
abort listeners are removed. Direct storage replacement is checked at settlement/deadline and requires
explicit verification; the superseded result never installs or clears its replacement. No storage observer
or redesign is introduced. StrictMode cancels the first mount and leaves only the active attempt's timer.

The uncertainty screen holds pathname/query/hash without navigation. Successful Retry restores the same
detail with no new history entry. Return clears locally and replaces `/login` with null return state;
it never invokes remote logout. Confirmed rejection retains Phase 3A safe login return behavior. Phase 3C
immediate local logout, eight-second fetch/body deadline and truthful confirmation feedback remain intact.

## Inspection and validation

Read AGENTS/prompt rules completely; reviewed development/design/system guidance, Web V2 roadmap,
Phase 3A–3C reviews, dependency-security record, web README, API Step 2/3/readiness/local-smoke/deployment
boundaries, durable WEB session 7B/8A/8B records and change-aware testing guidance. Repository-wide
pre-edit symbol search mapped both getCurrentUser consumers and startup/Retry/invalidation/storage/logout
contracts to existing App, startup, logout and return-path tests; neighboring shell/preview/input/primitives
coverage remains. The existing route feature-request assertion now waits for its actual effect.

| Changed production owner | Focused coverage |
| --- | --- |
| `api.getCurrentUser` optional signal | Real fetch/storage startup tests; direct shared API call has no deadline and succeeds after the startup budget; existing credential-login App tests retain one-argument expectations. |
| `useStartupSession` deadline/invalidation | Fake timers: never-resolving fetch, stalled body, success/401 before budget, retained storage, fresh Retry/repeated timeout, duplicate prevention, late success/rejection before/after newer Retry, replacement/sign-in/sign-out/unmount/StrictMode, zero deadline timers after termination. |
| Existing App route boundary | Fake-timer timeout blocks all protected reads/content, exact detail/query/hash/history restored by Retry, local Return clears without remote logout; full existing login/history/logout suites retained. |

Validation commands and results are recorded below after execution. Network-enabled execution uses the
supported permission path; the initial sandbox fetch could not reach the configured proxy. No proxy,
CA or deployment settings were changed. npm manifests/lockfile remain unchanged.

[Browser fixture](phase-3d-evidence/browser-review.cjs) runs local Vite with external Playwright and system
Chromium, intercepts every API request, aborts unexpected destinations and seeds only synthetic credentials.
A paused Playwright clock deterministically advances eight-second deadlines. Four scenarios at 320/1280
cover stalled startup → uncertainty → repeated Retry timeout → successful Retry → exact Contact detail,
and timeout → local Return to sign in. Twelve screenshots and
[observations](phase-3d-evidence/browser-observations.json) record blocking/storage/history, native Enter/Space,
reflow, zero page errors/unexpected requests and preview isolation. Ignored-cancellation late settlements
are established by unit fixtures; browser cancellation may prevent fulfilling a held request.

Reproduce with local Vite on port 5173 and isolated tooling:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
npm install --prefix work/browser-tools --no-save playwright --cache /workspace/.cache/npm
PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright \
  node shale-web/docs/phase-3d-evidence/browser-review.cjs
```

## Audit, remaining gaps, next milestone and rollback

Routine `/me` uses existing session authority and lifecycle records. Local timers/Retry/navigation/clearing
intentionally emit no browser audit row. No new sensitive-read endpoint, domain/admin mutation, audit
integration, schema or migration. Existing authorization, tenancy, validation, concurrency and authoritative
audit enforcement are preserved; roadmap §8.2 read/task audit gaps remain deferred.

Synthetic evidence does **not** establish live backend/session/revocation, two-tenant/audit, deployed profile
or real-host acceptance. Physical devices, full screen-reader/other-engine coverage and remaining Phase 2
operator gates remain OPEN. JavaScript-readable per-tab storage and unbounded credential-login verification
remain existing limitations. Established-session coordination, refresh, storage abstraction/redesign,
broad transport, query cache and dirty-form work remain **UNFINISHED**.

Next bounded **Phase 3E: canonical existing-route registry**. Consolidate existing route declarations,
shell destinations and safe-return allowlist under one typed authority, retaining every supported URL,
query/hash/history behavior and explicitly unavailable destinations. Dependencies: inspect installed Router 7
composition and Phase 3A return rules; preserve the 3B–3D session boundary and isolated preview; review
route consumers/tests before extraction. Test direct links, malformed/external return paths, Back/Forward
and logout. Do not add new workflows or aliases without a compatibility decision. This creates the route
ownership needed before separately scoped data-router/dirty-form blocking; it does not complete those blockers
or refresh/storage/transport/cache work. Phase 2 acceptance continues independently.

Rollback: revert only Phase 3D or rebuild security-patched base `f7ab40c8` with the same API origin.
This restores unbounded startup verification while retaining Phase 3A–3C and dependency patches.
No server/session/SQL rollback is needed; any deployment remains separate operator work.

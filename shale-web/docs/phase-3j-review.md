# Web V2 Phase 3J: credential-login deadlines and cancellation

Task branch `codex/web-v2-phase-3j-login-deadlines` starts from explicitly fetched live
`origin/codex/latest` **2179b2e753f60b34e61f9a62d4cf1d8d7c990256** (2026-10-09).
GitHub live ref and tracking-ref fetch agree. Ancestry verifies merged Phase 3A–3I
(#1844–#1852) and dependency-security #1843. No direct base push, merge or deployment.
Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**; appearance **provisional**.

## Policy, authority and uncertainty

Each deliberate sign-in receives **one eight-second total budget** covering login POST fetch,
JSON processing/validation, subsequent `/me` fetch, JSON processing/validation and the final
installation eligibility check. Neither headers nor stage transitions reset the budget.
This matches startup/Retry and logout budgets and supplies a finite recovery point without
multiplying eight seconds by stages. `sessionDeadline.ts` extracts startup's cancellation race;
startup retains its independent per-attempt budget and explicit Retry. Logout is unchanged.
Monotonic elapsed-time checks reject stage and installation continuations even before a delayed
timer callback runs. Browser suspension/event-loop blocking can delay feedback; this is a client
recovery deadline, not a server execution deadline or a guarantee that synchronous storage can
be interrupted. Cancellation clears the timer immediately; finally removes timer/listeners.

`useStartupSession.signInWithCredentials` owns the attempt, session generation and installation.
LoginPage retains its synchronous ref guard and owns an AbortController cancelled on route unmount;
the session owner additionally coalesces concurrent calls. Local sign-out/logout, startup Retry,
identity replacement and owner unmount invalidate pending credentials through the existing owner.
Ending an unverified attempt through logout is local only: it has no installed bearer to revoke
and makes no remote cleanup request or revocation claim. Credentials cannot be submitted through
this method while an identity is installed.

Both login and `/me` must be usable. Login validates the existing authenticated/Bearer/nonempty
header-safe opaque token/positive integer TTL/profile contract. `/me` retains existing profile
validation; its user and tenant IDs must match the login response, while its current profile/roles
remain authoritative. Only then may the existing signIn/store/establish seam install auth. Storage
failure still tears down, attempts partial-write cleanup once and reports the existing sanitized
failure/residual-storage messages. No alternate credential policy, refresh or token parsing.

Guards check mount, generation, attempt identity, caller signal and unchanged CredentialStore
witness. Arbitrary external credential replacement is not an identity-installation path; it makes
the old attempt ineligible. Reads that throw cannot authorize installation. Ignored-abort late
fetches/bodies are consumed, never continued to verification/installation or allowed to clear a
replacement. A fresh explicit submission after failure has a fresh budget; there is no automatic
login, verification, cleanup, refresh, read or mutation replay.

Inspected `AuthController.login/me`, `ApiValidation`, the safe exception handler, bearer/session
resolver and durable-session records/tests. Login's 401 is its generic `invalid_credentials`
contract; 400 is request validation, and other status/transport/body failures are not bad-password
proof. `/me`'s 401 rejects the issued session, not the original password.

| Outcome before deadline | Feedback / installation |
| --- | --- |
| Login 401 | Email/password not accepted; no identity or credential installed. |
| `/me` 401 | New session rejected during verification; no identity or credential installed. |
| Other HTTP failure, network/redirect failure, malformed/unusable response, mismatched identity | Sign-in could not be confirmed; not signed in in this tab; server session may have been created and may still be active. Usable form for deliberate retry. |
| Deadline | Same uncertainty, explicitly labelled sign-in timed out; abort and discard late results. |
| Store/read failure | Existing sanitized CredentialStore failure; no installed identity. |
| Inactive/cancelled attempt | No installation/cleanup of a newer credential; mounted form becomes usable with cancellation feedback. |

**A timed-out login POST may have created a durable server session.** Aborting, losing its response,
or failing `/me`/storage does not prove that issuance failed or revoke that session. There is no
automatic cleanup/replay or backend change to conceal this limitation. Login error bodies and raw
network/parser/storage exceptions are never displayed or logged. Passwords and bearers are absent
from observations/telemetry. The browser storage XSS/residual-removal limitations from 3H remain.

The existing route owner still preserves safe return state through failure, then replaces login with
exact pathname/query/hash and consumes state only after verified auth. Logout return-state cleanup,
startup Retry/uncertainty, established 401 coordination and Contact/Organization dirty security
bypass remain covered. No screen redesign, transport/cache rewrite, dependency/version, backend,
SQL/API/auth protocol, deployment/native or MCP/AI changes.

## Test impact and checks

Read complete AGENTS/prompt rules; reviewed development/design-system A.2/system overview, Web V2
roadmap and relevant 3A–3I/session/security reviews, web README, migration Step 2/3, authentication
readiness/session lifecycle, deployment boundaries and local smoke/test-selection guidance.
Pre-edit inventory is mapped below; final changed-file and affected-symbol review repeated.

| Changed owner | Coverage |
| --- | --- |
| api.login | Real-response credentialLogin tests: unusable marker/token/TTL/profile/JSON, 401 vs other statuses/network, signal/redirect handling; server AuthController contract tests. |
| sessionDeadline / useStartupSession | 61 new deterministic credential tests: all stalled fetch/body stages, shared boundaries including delayed timer delivery, duplicate/failed-then-successful attempts, late success/rejection after timeout/newer attempt/logout/replacement/unmount, external store witness, timer/listener cleanup and storage failure. Existing startup/logout/session/credential regressions retained. |
| LoginPage / safe return | Existing App mocks updated to complete LoginResponse and signal-aware calls/safe feedback; real App test for LoginPage-only unmount while session owner remains mounted; old unmount success fixture now has a valid login body. Complete return/history and Contact/Organization suites retained. |
| Browser | Fresh synthetic contexts with ignored-abort fetch/body promises, paused deterministic clock, duplicate activations, precise shared budget, rejection/unavailable/malformed/storage feedback, failed-then-successful exact suffix/history, preview isolation and compact reflow. |

Commands from repository root; Java 21/Maven via `source /workspace/.tools/shale-env.sh`.
Network/browser checks use supported network-enabled execution because the sandbox could not reach
its configured proxy. No proxy, CA, credential or deployment configuration was changed.

| Command | Result |
| --- | --- |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | PASS, unchanged lockfile. |
| `npm test --prefix shale-web -- src/sessionRejectionApp.test.tsx src/credentialLogin.test.tsx src/App.test.tsx src/useStartupSession.test.tsx --maxWorkers=1` | **207 passed**. |
| `npm test --prefix shale-web -- --maxWorkers=1` | **559 passed**, 16 files. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | PASS; operational and foundation entries. |
| `mvn -B -ntp -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.DurableSessionAuthTest,com.shale.server.runtime.ServerAuthSessionLogoutTest -Dsurefire.failIfNoSpecifiedTests=false test` | **14 passed**, zero failures/errors/skips. |
| `python3 build/test-selection/select_tests.py --base 2179b2e753f60b34e61f9a62d4cf1d8d7c990256 --head HEAD --format markdown --output shale-web/docs/phase-3j-evidence/test-selection.md` | [Selection](phase-3j-evidence/test-selection.md): server/AuthController, no full-suite escalation; relevant to consumed login/bearer/me contract. |
| `mvn -B -ntp -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | **10 passed**, selected compatibility suite. |
| `mvn -B -ntp test` | **116 passed**, zero failures/errors/skips; BUILD SUCCESS. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | **Zero vulnerabilities** in [full](phase-3j-evidence/audit.json) and [production](phase-3j-evidence/audit-production.json). |
| `PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright node shale-web/docs/phase-3j-evidence/browser-review.cjs` | **18 scenarios passed**, zero page errors/unexpected requests; Chromium 151.0.7922.173 / Playwright 1.64.0. [Observations](phase-3j-evidence/browser-observations.json), [fixture](phase-3j-evidence/browser-review.cjs). |
| `git diff --check`; base-to-head whitespace/package/lockfile comparison | PASS; dependencies and security patches unchanged. |

Initial browser assertions assumed one feature mount read (StrictMode makes two) and treated full-document
Forward as retained fixture state. The corrected fixture compares reads before/after late settlement and
models startup on Forward. Duplicate events now occur in the same JS turn for immediately failing requests;
a submission after an already-settled failure is correctly a new explicit attempt. An existing task test
needed passive mount effects flushed before synthetic activation; its mutation assertions are unchanged.

## Gaps, remaining Phase 3, next milestone and rollback

Synthetic evidence does **not** establish live server-session issuance/revocation, deployed status/body
contracts, tenant/audit/non-dbo or real-host acceptance. Physical devices, full screen-reader speech,
Firefox/WebKit and Phase 2 operator gates remain OPEN. Deadline messages need live/AT acceptance;
client cancellation cannot bound or revoke server work. No deploy or merge occurred.

Audit compatibility: existing server login issuance/session validation/lifecycle remains authoritative.
Local deadlines, cancellation, staging/navigation/storage/feedback intentionally emit no browser audit
rows. No new sensitive-read/domain/admin seam or audit integration/schema/migration. Roadmap §8.2
sensitive-read/task/note transaction/audit gaps remain deferred.

| Remaining Phase 3 category | Inventory |
| --- | --- |
| Implemented, awaiting acceptance | 3A safe returns/history; 3B/3D startup uncertainty/Retry/deadline; 3C truthful bounded logout; 3E registry; 3F generation/rejection guards; 3G data router/Contact detail protection; 3H CredentialStore/failure bounds; 3I Organization detail protection; 3J credential deadlines/cancellation/classification. Live-host/session, device/AT and owner acceptance remain. |
| Required implementation unfinished | Coordinated refresh/expiry/reload scheduling; broader typed transport/error/cancellation and per-identity memory query cache; remaining create/case/task/note editor dirty protection; feature routes/subsections/aliases and per-feature safe-query policy as consuming slices land. No all-form/cache/refresh completion claimed. |
| Blocked policy/contract decisions | D3 still-valid/current-JTI rotation, one-winner races, lost-response uncertainty and collapsed refresh 401 exceptions, replacement storage failure, unknown-authority write blocking, reload scheduling and first-slice session policy. D2 dependency/schema choices for wider cache/forms/transport. D1/D5/D6 selected read authorization, genuine SQL bounds and non-UI sensitive-read audit; D9 hosting/CORS/assets/rollout and acceptance. |

**Next substantive milestone:** first-slice readiness for sign-in → bounded assigned/basic case search →
read-only Case Overview (Phase 4). Close the selected D1/D5/D6 read/audit/bounds contracts and explicitly
approve its D3 session policy (coordinated refresh after contract closure, or a deliberately accepted
re-login limitation). Then implement only those reviewed read contracts and client path, followed by
authorized live two-tenant/session/host/device acceptance. Do not treat refresh as implicitly enabled,
or expand into all-form refinements, unified search/live integration or unrelated screen polish first.

Frontend rollback: revert only Phase 3J or rebuild security-patched 3I base `2179b2e7` with the same
API origin. This restores unbounded credential attempts and the old overbroad rejection message while
retaining 3A–3I/security patches. No credential migration or backend/session/SQL rollback is required.

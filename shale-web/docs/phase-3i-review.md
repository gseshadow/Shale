# Web V2 Phase 3I: Organization Detail dirty-form protection

Branch `codex/web-v2-phase-3i-org-dirty` starts at live `origin/codex/latest`
`406021e1c8a49791b495fb396848ffd6bccb9036` (2026-10-09). Explicit tracking-ref fetch and
GitHub agree; merged #1850 (3G, `a1adf286`) and #1851 (3H, base above) verified.
No base push, merge or deployment. Phase 2 acceptance **OPEN**; Phase 3 **IN PROGRESS**;
appearance **provisional**.

## Protected editor and reused ownership

Exactly `/organizations/:organizationId` → Organization Information → **Edit organization**
(`OrganizationDetailsForm`) is added. Organization creation remains unprotected.
`DetailDraftProtection.tsx` is the renamed existing Contact hook/dialog; both editors use it,
with entity-specific wording. Its single useBlocker/state machine, native accessible modal,
Keep editing/Discard actions, Escape, bounded Tab/Shift+Tab, focus restoration and conditional
unload listener remain shared. CSS is renamed without changing appearance. Contact behavior
and session/storage ownership remain intact; there is no second blocker or confirmation system.

Organization owns its opening snapshot and RowVer. Name, website, all six address fields and notes
compare trimmed command values; internal whitespace matters. Phone, fax, email and both extensions
reuse exact `contactValueUpdate` RETAIN/SET/CLEAR semantics, including unchanged legacy spelling.
No browser parser infers equivalence. Exact reversion is clean. Advisory formatting can affect dirty
state but cannot replace the baseline or witness or establish save success.

Clean navigation works normally. Dirty/pending shell links, parent/related-case links, ordinary router
navigation and same-document Back/Forward use Keep editing (all values retained) or Discard (intended
transition once). Cancel is deliberate discard through the same choices when dirty/pending; clean Cancel
closes immediately. Editor close restores Edit organization focus. History-location keys remount this
screen after discard, including same-ID query/hash transitions, preventing abandoned state/continuations.

Save duplicates are synchronously guarded; fields disable while pending, Cancel/navigation remain usable.
Validation, conflict, network, server, JSON/body and unusable-success failures retain the draft and opening
RowVer. Only a current authoritative snapshot for the same Organization/tenant, with complete renderable
fields/related Cases and a nonempty, whitespace-free Base64 RowVer, closes the editor and establishes the next opening
baseline. A successful request copy or HTTP status alone is insufficient. Known success resets an open
blocker without replaying its navigation. A failed clean pending Save also releases an obsolete prompt
without replay. No optimistic success, automatic resubmission, persistent draft or post-sign-in restoration.

Pending discard says the mutation may already have committed. Leaving/aborting proves neither commit nor
rollback; check the entity before another explicit submission. Synchronous discard, mounted consumer and
captured session guards reject late success/error/focus callbacks. Existing ContactValueInput generation
and disabled-Save invalidation protect phone/fax/email advisories; Organization also guards formatting
callbacks. No advisory/parser or API submission policy is changed. Existing scalar null submission and
server retention semantics remain; broader explicit-null contract work stays separate.

Security invalidation at useStartupSession/sessionRequests still bypasses prompts immediately: logout,
current 401, local Return to sign in and identity replacement discard the mounted sensitive editor, even
with an active prompt/pending Save or failed CredentialStore clearing. Generation invalidation also handles
replacement reusing the same opaque bearer. Storage failures retain 3H suppression and separate residual
feedback. Failed remove may leave a bearer in raw sessionStorage; document suppression cannot guarantee
erasure, a new document may verify it again, and XSS exposure remains. No storage-policy change.

## Test impact and validation

Before production edits: read AGENTS/prompt rules completely; reviewed development/design/system guidance,
roadmap forms/router/session/audit sections, Phase 3G/3H reviews, web migration/readiness/smoke/deployment,
Organization aggregate compatibility/audit ownership and change-aware testing. Searched all hook/input,
Organization/RowVer, router/session/storage consumers and neighboring tests.

| Changed owner | Coverage |
| --- | --- |
| App Organization form/location key | `organizationDraftApp.test.tsx`: every scalar/contact/extension dirty/reverted state, all-value Keep, shell/parent/related-case links, Cancel/Escape/focus, POP/suffix/other entity, pending/no duplicate/replay, success/new RowVer, failure/unusable success, stale advisory/results, conditional unload and security/storage teardown. Real API clients with isolated synthetic responses. Creation explicitly remains unprotected. |
| Shared DetailDraftProtection + Contact import | Complete existing `contactDraftApp.test.tsx` contract and ContactValueInput suite retained and passing; only shared naming/text parameter changes. |
| Unchanged router/session/CredentialStore | Existing App, session rejection/request, startup, logout, credential failure/store, return/registry/shell/preview/primitives suites; same-token replacement and failed-clear local return additionally exercised for Organization. |
| Retained backend contracts | ApiReadController, ContactValueApi and AuthController focused compatibility tests; selected auth suite and local critical reactor. No backend test/code expectation changes. |

Commands from repository root; Java 21 via `source /workspace/.tools/shale-env.sh`:

| Command | Result |
| --- | --- |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | PASS, existing lock; no dependency changes. |
| `npm test --prefix shale-web -- src/organizationDraftApp.test.tsx src/contactDraftApp.test.tsx src/ContactValueInput.test.tsx --maxWorkers=1` | 120 passed; five additional Organization cases verified in the final focused/full runs below. |
| `npm test --prefix shale-web -- src/organizationDraftApp.test.tsx --maxWorkers=1` | **73 passed**. |
| `npm test --prefix shale-web -- --maxWorkers=1` | **497 passed**, 15 files; Contact, routing/session/storage and preview regressions included. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | PASS; operational and isolated foundation entries built. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.controller.ApiReadControllerTest,com.shale.server.controller.ContactValueApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 58 passed, no failures/errors/skips. |
| `python3 build/test-selection/select_tests.py --base 406021e1c8a49791b495fb396848ffd6bccb9036 --head HEAD --format markdown --output shale-web/docs/phase-3i-evidence/test-selection.md` | Selected server/AuthController, no full-suite escalation; [selection](phase-3i-evidence/test-selection.md). Auth covers retained bearer/me/logout boundaries. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`; `mvn test` | **10** selected / **116** critical passed, no failures/errors/skips; BUILD SUCCESS. Maven runs used `-B -ntp` for quiet output. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | **0 vulnerabilities** in both [full](phase-3i-evidence/audit.json) and [production](phase-3i-evidence/audit-production.json). |
| Chromium synthetic browser fixture | **24 scenarios passed**, Chromium **151.0.7922.173**, zero page errors/unexpected destinations; [observations](phase-3i-evidence/browser-observations.json). Compact Light/wide Dark modal captures visually inspected. |
| `git diff --check`; base-to-head whitespace/dependency comparison | PASS; package manifest/lockfile unchanged. |

[Browser fixture](phase-3i-evidence/browser-review.cjs) intercepts all API calls, aborts unexpected
destinations and uses fresh contexts at 320 Light / 1280 Dark. It tests preview isolation, real shell
links, native modal keyboard/focus, history, clean/reverted states, retained values, pending discard/late
failure, authoritative success, validation/conflict/network/unusable success, logout/rejection and failed
credential clear. Observations contain no credential, header or submitted payload. Dispatching cancelable
beforeunload events tests the registered handler, not guaranteed browser prompt delivery. Tooling is only
under `work/browser-tools`; run local Vite, then
`PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright node shale-web/docs/phase-3i-evidence/browser-review.cjs`.

The first focused run exposed missing Edit organization focus return after Cancel/Discard; production now
matches Contact's established focus behavior. Final review also rejects whitespace-corrupted save RowVer values that Java Base64 decoding would not accept; focused failure coverage preserves the draft. No assertion was weakened. Initial sandbox Git fetch could
not reach the proxy; supported network-enabled execution fetched the explicit current tracking ref.

## Audit, limitations, remaining Phase 3 and rollback

No new sensitive-read/domain/admin endpoint or mutation/audit seam. Existing Organization V2 API → service
adapter → OrganizationTypeMutationDao aggregate owns tenant/actor/concurrency/validation and existing
transaction-bound PHI-safe/entity-action audit events. Local staging, Keep/Discard, Cancel/navigation and
teardown intentionally emit no audit rows or sensitive telemetry. No audit integration/schema/migration;
roadmap §8.2 read/task/note gaps stay deferred. No backend/SQL/API/auth protocol/dependencies/versions/
deployment/native/MCP/AI changes, refresh work or query cache.

Remaining unprotected: Contact/Organization creation, case creation/core/assignment/status editors,
task creation/detail, case update composer and other local form/search/filter state. Browser unload protection
is registered only while dirty/pending, removed on clean reversion/close/unmount, and conditional on current
session authority. Browsers require user activation and control generic text; Firefox may affect bfcache;
mobile background/process death may never dispatch it. No guaranteed recovery or persistent drafts.
Synthetic evidence establishes no live backend, server mutation/revocation/audit/two-tenant, deployed-host,
physical-device or screen-reader acceptance. Firefox/WebKit/AT and Phase 2 gates remain OPEN.

| Remaining Phase 3 deliverable | Blocker / dependency |
| --- | --- |
| Other-editor dirty protection | Bounded per-editor opening/saved witnesses, mutation/advisory semantics, pending-outcome and security teardown inventory. No all-form guarantee. |
| Credential-login request/verification bounds | Login fetch/body and subsequent /me can stall; agree deadline, cancellation and unknown-outcome feedback, preserve storage failure and safe-return behavior without replay. |
| Coordinated refresh/expiry/reload timing | D3 **unresolved**: still-valid current bearer, one conditional current-JTI rotation winner, lost-response uncertainty, refresh exceptions collapsed to 401, concurrent old-JTI requests, failed replacement storage and unknown-authority/write blocking. No separate web refresh credential; do not enable refresh before agreed server-compatible semantics/tests. |
| Broader transport/error/cancellation and memory query cache | D2/D3 dependency/policy decisions, per-identity cleanup, invalidation and stale/read/write boundaries; no persistent sensitive cache or mutation replay. |
| Future canonical routes/subsections/aliases and acceptance | Only add as consuming features land; safe query/return policy and real-host/history/device/AT acceptance. Existing routes are implemented. |

Next substantive bounded **Phase 3J: credential-login verification deadlines and cancellation**, covering
only existing login plus verifying /me, with explicit uncertain outcomes, duplicate/stale-result guards,
safe-return/history/storage-failure tests and no refresh/retry/replay/protocol change. Decide its precise
time budget before implementation; keep refresh-rotation ambiguity expressly unresolved. Other editors,
transport/cache and Phase 2 acceptance proceed as separately scoped work.

Rollback: revert only 3I, or rebuild the same-origin security-patched 3H base `406021e1`.
This removes Organization protection and restores the Contact-scoped file names while retaining 3A–3H
session/storage behavior. No storage migration, backend/session/SQL rollback or deployment is needed here.

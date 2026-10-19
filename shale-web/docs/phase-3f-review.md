# Web V2 Phase 3F: established-session rejection coordination

Branch `codex/web-v2-phase-3f` starts at fetched live `origin/codex/latest`
**d8cc450fad480dead660ffdd152c8caa27529a67** (2026-10-09). Ancestry checks verify merged
Phase 3A–3E (#1844–#1848) and dependency-security #1843. The clone tracks only main by
configuration; explicit fetch updated the PR base before creating the task branch.
Phase 2 acceptance **OPEN**, Phase 3 **IN PROGRESS**. Appearance **provisional**;
functionality takes priority before a later visual pass. No base push, merge or deployment.

## Inspected response authority

Reviewed AGENTS/prompt rules completely, development/design/system guidance, Web V2 roadmap,
Phase 3A–3E/security reviews, web README, API migration/readiness/local-smoke/deployment and
change-aware testing guidance, durable WEB session 7B/8A/8B/security records. Inspected all
API clients, ApiError consumers, startup/login/logout, feature effects and completion callbacks;
server ApiReadController, ContactValueValidationController, AuthController, bearer resolver,
ServerRuntimeSessionState, ServerAuthSessionService, DurableSessionTokenValidator, SQL durable
store and safe exception handler. Related web and server tests were searched before edits.

| Feature outcome | Established-session decision |
| --- | --- |
| 401 under the inspected feature contracts | Authentication rejection. Invalidate only its current generation/credential, once. |
| 403 | Operation-level authorization denial (including live admin check); retain auth and existing feature error. |
| 400/404/409/429 and other non-401 errors | Existing validation, visibility/conflict/throttling/error paths; retain auth. |
| Network failure, 5xx, unusable feature JSON | Retain auth; existing feature error path. No retry or proof that a write did not commit. |
| Old generation/credential, after logout/owner unmount | Discard outcome; never invalidate the newer session or deliver protected success. |

All inventoried feature endpoints resolve the bearer through requirePrincipal/requireShaleClientId/
requireUserId. Invalid, expired, revoked, missing/ineligible or wrong-current-JTI bound sessions use
401. Durable lookup exceptions propagate to the safe 500 handler, rather than being converted to
invalid-session signals. ApiReadController's live admin denial uses 403. Neither a field-validation
error nor a conflict establishes session rejection. No ambiguous feature 401 was found, so the direct
contract is used without an extra `/me` round trip. The previous roadmap's suggested bounded probe
is unnecessary for this inspected inventory. If a future endpoint has ambiguous rejection semantics,
it must explicitly review the contract and use bounded verification/unknown blocking before joining
this classification. Auth refresh has separate exception/rotation ambiguity and is not a consumer;
there is no browser refresh implementation. Login failures and logout's 401/unconfirmed revocation
retain their distinct existing policies. Startup `/me` uncertainty/Retry/eight-second deadlines remain.

## Seam, consumers and races

`sessionRequests.ts` is one small seam beneath existing feature clients; the operational app has one
session owner. A binding object witnesses the generation, retains its dispatched opaque credential,
and carries a lifecycle validity predicate and abort signal. No new credential store or queue exists.
`useStartupSession` installs it before revealing verified auth, and disposes it synchronously on
invalidation. Feature dispatch, response headers and body settlement check binding identity, generation,
mount state and current stored credential. Cancellation races the whole exchange/body, so even synthetic
ignored-abort results cannot escape; timers/retry loops are not introduced. Abort listeners are removed.

401 consumes the binding synchronously **at notification time**, before callback/another response.
The hook clears bearer/identity, invalidates lifecycle work and sets one session-ended feedback state.
Concurrent read/write rejections cannot redirect repeatedly or duplicate feedback. Reused opaque bearer
values in a newer login still have a different binding/generation. Old logout/startup outcomes remain
covered by their existing guards. No request is dispatched after the binding is gone.

ProtectedRoute is keyed by lifecycle generation, remounting all protected state after identity replacement.
Existing load-effect cleanup remains. Every awaited feature handler checks a captured session and component lifetime before dispatch
and again before setting result state or invoking success callbacks. ContactValueInput also checks before
formatting/validation callbacks and invalidates on credential change/unmount. Credential login ignores
unmounted results and duplicate submission. These consumer checks cover results queued just before
teardown as well as the transport's late-header/body cases. No old create callback can navigate to detail.

The independent consumer test inventory invokes **all 32 real feature clients** with 401:

| Group | Consumers (api.ts names) |
| --- | --- |
| Cases | searchCases, getCaseDetail, listAssignedCases, createCase, updateCaseAssignment, updateCaseCoreDetails, updateCaseStatus |
| Tasks | listAssignedTasks, listCaseTasks, getTaskDetail, createCaseTask, updateTaskDetail, completeTask |
| Updates | listCaseUpdates, addCaseUpdate |
| Contacts | searchContacts, getContactDetail, createContact, updateContactDetails |
| Organizations | searchOrganizations, getOrganizationDetail, createOrganization, updateOrganizationDetails |
| Team | listTeamMembers, getTeamMemberDetail |
| Lookups/settings | listEffectiveCaseDateTypes, listTaskPriorityLookups, listCaseStatusLookup, listPracticeAreaLookups, listCaseStatusSettings, listPracticeAreaSettings |
| Advisory validation | validateContactValue (phone/email/fax form consumer) |

Only login, getCurrentUser and logout retain raw fetch in api.ts. Existing URL, headers, payload,
field-error handling and feature-specific 404/409 errors are unchanged. The response adapter exposes
only ok/status/json, which are the properties consumed by these clients; it adds no general transport,
cache, runtime-schema or dependency rewrite. Preview never activates this seam and makes no API calls.

Rejection leaves the current protected location for the existing signed-out capture. Verified login
uses the existing safe return contract (pathname/query/hash allowlist), replaces login and consumes state.
Logout still replaces login with null return state. Safe-return recognition remains metadata, not authority.
No stored request/read/mutation is resumed or replayed. Restored screens issue their normal fresh mount
reads under verified auth; a failed search or in-flight mutation is not automatically resubmitted.
Dev StrictMode may replay existing mount effects, as before, without enabling mutation retries.

**Draft loss:** unmount discards memory forms/search state. Feedback explicitly says unsaved changes
were discarded and submitted changes may have saved. Cancellation never proves server rollback or failed
commit. After sign-in users must inspect authoritative state before deliberately submitting again.
There is no draft restoration, persistent draft store or dirty-form blocker in this slice.

## Test impact and validation

| Changed owner | Maintained coverage |
| --- | --- |
| api/sessionRequests | New real-client matrix for all 32, read/write non-401 statuses, network/field errors, concurrent rejection, reused/replaced credentials, logout/unmount, late headers/body/write callbacks. |
| useStartupSession | New real-fetch seam lifecycle tests; existing startup/deadline/Retry and truthful logout suites retained. |
| AppRoutes/LoginPage and feature handlers | New real-fetch App recovery/history/read/write/create-race tests and queued-result consumer isolation; existing App/returnPath/registry/shell suites retained. Unmounted login cannot clear/install over a replacement. |
| ContactValueInput | Existing validation/retain/duplicate/typing tests maintained with an installed synthetic binding; new session teardown before unmount prevents parent formatting callbacks. |
| Preview/primitives | Existing isolation/accessibility suites retained; complete web suite run. |
| Unchanged server authority | AuthController, DurableSessionAuth, ServerAuthSessionLogout, ApiReadController and ContactValueApi compatibility tests verify consumed contracts without server changes. |

Commands from repository root; Java 21/Maven via `source /workspace/.tools/shale-env.sh`.
Network/browser commands use supported network-enabled execution after the initial sandbox could
not reach its configured proxy. Proxy, CA, deployment settings and manifests/lockfile are unchanged.

| Command | Result |
| --- | --- |
| Explicit live git fetch and six merge-base ancestry checks | Passed; pinned base above. |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | Passed, existing lock. |
| `npm test --prefix shale-web -- src/sessionRejectionApp.test.tsx src/sessionRequests.test.tsx src/ContactValueInput.test.tsx src/App.test.tsx` | 142 passed, four files; existing startup/logout covered by full suite. |
| `npm test --prefix shale-web` | 359 passed, 11 files; zero failures. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | Passed, operational and foundation entries. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.DurableSessionAuthTest,com.shale.server.runtime.ServerAuthSessionLogoutTest,com.shale.server.controller.ApiReadControllerTest,com.shale.server.controller.ContactValueApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 62 passed; zero failures/errors/skips. |
| `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --output shale-web/docs/phase-3f-evidence/test-selection.md` | [Selection](phase-3f-evidence/test-selection.md): server/AuthController; no full-suite escalation. Relevant to the preserved bearer and identity contract. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 10 passed; zero failures/errors/skips. |
| `mvn test` | 116 passed; zero failures/errors/skips. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | [Full](phase-3f-evidence/audit.json)/[production](phase-3f-evidence/audit-production.json): zero findings, all severities. |
| `git diff --check`; base-to-head diff check; package/lock comparison | Passed/unchanged. |

Focused tests exposed a notification-time microtask race; the final current-binding check coalesces
simultaneous responses. Initial browser fixture reload state and test-only type/label mistakes were
corrected before final validation. No assertions were weakened to retain obsolete behavior.

[Isolated script](phase-3f-evidence/browser-review.cjs) / [observations](phase-3f-evidence/browser-observations.json):
**10 synthetic Chromium scenarios** at 320/1280, eight recovery screenshots, zero page errors or
unexpected network destinations. In-page fetch fixtures deliberately ignore cancellation to exercise
stale response races; external network interception aborts unexpected destinations. Each fresh context
first proves preview isolation. Expired-read/revoked-write labels describe synthetic 401 fixtures,
not real JWT or SQL acceptance. Covers recovery, stale 401 after logout/new login, late create success,
403/conflict/5xx/network retention, native keyboard sign-in, heading focus, reflow, exact suffixes,
replacement/Back/Forward history and no completion/create replay. No credential/header/payload is
recorded in observations. Screenshot [compact rejection](phase-3f-evidence/expired-read-320.png).

Reproduce with local Vite on port 5173 and external tooling, without adding app dependencies:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
npm install --prefix work/browser-tools --no-save playwright --cache /workspace/.cache/npm
PLAYWRIGHT_MODULE=$PWD/work/browser-tools/node_modules/playwright \
  node shale-web/docs/phase-3f-evidence/browser-review.cjs
```

## Limits, audit, next milestone and rollback

Synthetic/mocked evidence does **not** establish live expiry/revocation, tenant isolation, audit,
multi-replica/legacy-cutoff, real hosting or deployment acceptance. Real screen-reader speech,
physical devices, Firefox/WebKit and remaining Phase 2 operator checks stay OPEN. Existing per-tab
JavaScript-readable storage and unbounded credential-login verification remain. Arbitrary external
storage edits are not a supported identity-installation path: mismatch guards discard old results,
and verification/lifecycle APIs must establish any replacement. No storage observer is added.

No new sensitive read/domain/admin endpoint or mutation/audit seam. Existing server authorization,
tenancy, validation, concurrency, PHI/entity-action and session-lifecycle audit owners are preserved;
local coordination/feedback/cancellation intentionally emit no audit row. No audit integration/schema/
migration required. Roadmap §8.2 read/task/note audit gaps remain separately deferred. No backend,
SQL, auth/API protocol, deployment, version, native, dependency or MCP/AI changes.

Next bounded **Phase 3G**: migrate existing route registry composition to Router 7 data-router ownership
and protect ordinary navigation for one representative Contact editor with Stay/Discard and best-effort
reload prompts. Preserve URLs/history/session/preview; session teardown must still discard sensitive
drafts immediately. No persistent drafts or reauthentication restoration. Remaining editor adoption,
refresh/rotation ambiguity, CredentialStore and query cache require subsequent scoped D2/D3 decisions.

Rollback: revert Phase 3F or rebuild security-patched Phase 3E base `d8cc450f` with the same API origin.
This restores feature-local rejection handling; preserves 3A–3E and dependency fixes. No server/session/
SQL rollback required; any deployment is separate operator work. Phase 2 acceptance remains OPEN;
Phase 3 remains IN PROGRESS.

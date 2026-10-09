# Web V2 Phase 3H: CredentialStore extraction

Task branch `codex/web-v2-phase-3h-credential-store` starts at fetched live
`origin/codex/latest` **a1adf286d66e8ac52f188960df6226fed0af736e** (2026-10-09).
GitHub's live ref and explicit fetch agree; explicit tracking-ref fetch corrects this clone's
main-only fetch configuration. Ancestry checks verify Phase 3A–3G (#1844–#1850) and dependency
security #1843. No direct base push, merge or deployment. Phase 2 acceptance **OPEN**;
Phase 3 **IN PROGRESS**; appearance **provisional**, functionality and deployment readiness prioritized.

## Ownership and preserved policy

`src/credentialStore.ts` owns the minimal synchronous `CredentialStore.read/store/clear` port,
the single operational browser instance, sanitized storage errors, and an isolated instance-local
memory implementation for tests. Browser access is lazy: module imports do not touch storage.
`api.ts`'s existing exported helpers are compatibility adapters to that same instance, with no key
or independent credential copy. `useStartupSession` receives a stable store (browser by default)
and owns all operational credential lifecycle decisions. LoginPage delegates failure teardown to
the session owner; it no longer independently clears persistence. Features retain captured bearer
arguments and existing generation/credential guards, not new storage access or identity authority.

Policy is unchanged: **sessionStorage**, exactly **shale-web.accessToken**, opaque bearer contents,
existing per-tab lifetime/reload behavior. No tenant/user/profile data, TTL metadata, drafts, query
cache or other payload is moved into the store. No localStorage/cookie/IndexedDB/native fallback,
remembered login, encryption, dependency, backend/SQL/API/auth protocol, deployment/version or MCP/AI
change. JavaScript-readable sessionStorage remains an explicit **XSS exposure**. The abstraction
is **not a security upgrade** and does not guarantee credential erasure or server revocation.

Successful login still verifies `/me` before installing identity. Storage must then succeed before
binding feature requests or revealing protected UI. Generation, mount and credential witnesses,
immediate local teardown, eight-second startup/Retry deadlines, retained network uncertainty,
explicit duplicate-guarded Retry, bounded truthful logout and rejection coordination remain.
Safe return pathname/query/hash and replacement history remain. Contact's security bypass still
unmounts dirty/pending editors without waiting for confirmation, including storage failures.
No automatic retries, refresh, request replay, queued revocation or mutation resubmission is added.

## Bounded storage failure behavior

| Failure | Outcome |
| --- | --- |
| Getter/getItem at startup or verification settlement | No identity/protected access; sanitized unavailable feedback; no request if the initial read fails. Only explicit Retry rereads and re-verifies; no timer/retry loop or persistence fallback. |
| Read exception in an established request guard | Discard request, invalidate the binding/generation and tear down identity; best-effort clear through the owner. No feature dispatch or raw exception delivery. |
| Getter/setItem during verified login | Invalidate previous identity/requests first. Do not bind/install the new identity. Attempt clear once to clean a partial write; report sanitized unsuccessful sign-in. Preserve safe return state for a later explicit successful login. |
| Getter/removeItem during local Return, logout, startup 401 or current feature rejection | Invalidation and memory identity removal still finish. Browser denial flag suppresses residual credentials in this document; hook local-ended guard prevents Retry reuse. Residual-storage feedback is separate from server revocation outcome. |

Browser suppression is a boolean denial flag, not a credential mirror or another persistence mechanism.
A successful explicit `store` releases suppression; a failed store cannot release it. Generation and
credential checks still reject stale responses and external replacements. Arbitrary external storage
edits remain unsupported identity installation, and no storage observer is introduced.

**Residual limitation:** failed removal may leave the previous or partially written bearer in raw
sessionStorage. Suppression lasts only for this loaded document/module instance: a new document or
reload can read that residual value and attempt normal `/me` verification. Feedback explicitly says
reloading may restore it and advises closing the tab before reopening. Browser tab restore/duplication
behavior is outside this guarantee; raw browser storage may require operator/browser cleanup. XSS can
still read raw storage regardless of this flag. Storage cleanup failure proves neither remote revocation
nor failure to revoke. Confirmed remote logout is reported independently; no other-device guarantee.
A server session issued before failed browser persistence may remain active until revoked/expired;
there is no new remote cleanup/replay attempt. String values cannot be reliably zeroized.

## Test impact, search and checks

Before editing: read AGENTS/prompt rules completely; reviewed system/development/design guidance,
Web V2 roadmap (especially §6.2/D3), Phase 3 reviews and dependency-security review, web migration,
readiness/smoke/deployment/README, durable session 7B/security records and change-aware testing guidance.
Inspected all credential helpers/consumers, startup/login/logout and request guards, Contact security
teardown, App/router/history, preview imports and neighboring tests. No server contract is modified.

| Changed owner | Coverage |
| --- | --- |
| CredentialStore + compatibility helpers | Actual browser implementation against Storage: exact key/opaque values/read/store/remove, helper delegation, getter/method exceptions, sanitized errors, residual suppression/new-document limitation, no fallback; isolated memory instances. |
| Session hook | Existing startup lifecycle/deadline/Retry/StrictMode/stale tests now use isolated memory stores and real auth fetch clients. New failure tests exercise browser adapter, settlement read failure, established guard, failed replacement/partial write, teardown with failed clear and no reuse. |
| App/LoginPage | App tests mock the boundary rather than storage helpers; real-client integration verifies failed persistence/cleanup, no protected reads, safe return/history and successful explicit recovery. |
| Contact editor | Existing draft/blocker tests plus failed-clear logout/rejection and failed-store identity replacement; unload handler removed and late Save discarded. |
| Preview | Existing API isolation plus explicit zero storage read/write/remove assertions even with a seeded credential. |
| Unchanged server authority | Focused AuthController, DurableSessionAuth and ServerAuthSessionLogout compatibility; selector's AuthController suite and local critical reactor. |

Post-edit `rg` over production `shale-web/src` finds direct storage access only in CredentialStore's
three browser operations; helpers only delegate. No credential access in foundation production sources.
Intentional direct access outside production ownership: adapter assertions/exception injection, test cleanup,
preview's pre-render synthetic seed, and historical/current browser review fixture seeding/assertions.
Browser fixtures are never imported by application code; observations contain no bearer/header/payload.

Commands from repository root (Java 21 via `source /workspace/.tools/shale-env.sh`):

| Command | Result |
| --- | --- |
| `npm ci --prefix shale-web --cache /workspace/.cache/npm` | PASS; existing manifest/lock, no dependency changes. |
| Focused eight web suites, `--maxWorkers=1` | **249 passed**. |
| `npm test --prefix shale-web -- --maxWorkers=1` | **423 passed**, 14 files. |
| `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web` | PASS; operational and foundation entries built. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.DurableSessionAuthTest,com.shale.server.runtime.ServerAuthSessionLogoutTest -Dsurefire.failIfNoSpecifiedTests=false test` | **14 passed**, no failures/errors/skips. |
| Repository change-aware selector against pinned base | See [selection](phase-3h-evidence/test-selection.md); selected auth compatibility remains relevant to preserved bearer/me/logout behavior. |
| Selected AuthController reactor | Pending final recording. |
| `mvn test` | PASS; **116 tests**, no failures/errors/skips. |
| `npm audit --prefix shale-web --json`; `npm audit --prefix shale-web --omit=dev --json` | **0 vulnerabilities**, both [full](phase-3h-evidence/audit.json) and [production](phase-3h-evidence/audit-production.json). |
| Chromium synthetic browser review | Pending final recording. |
| `git diff --check`; base-to-head diff; package/lock comparison | Pending final recording. |

Initial missing installed test dependencies, an empty alert in the new feedback composition and a browser
fixture expecting a placeholder rather than authoritative Contact heading were corrected. No behavioral
assertions or production deadlines were weakened. Runtime/browser checks use supported network-enabled
execution; an automatic review credit outage briefly blocked browser-server launch, then resolved.

## Audit, remaining gaps, next milestone and rollback

No new sensitive-read/domain/admin endpoint or mutation/audit seam. Existing server-owned session lifecycle,
PHI/entity-action/session-security audits, authorization, tenant, validation and concurrency enforcement
remain authoritative. Local credential access, staging, navigation, failure feedback and teardown intentionally
emit no browser audit rows or sensitive telemetry. No audit integration/schema/migration; roadmap §8.2 gaps
remain deferred.

Synthetic tests establish neither live backend/session/revocation/two-tenant/audit nor real-host acceptance.
Physical devices, screen readers, Firefox/WebKit and remaining Phase 2 gates stay OPEN. Credential-login
verification remains without a new deadline. Refresh, broad transport/cache and other editor adoption remain
unfinished. Appearance is provisional; this task does not deploy.

Next substantive bounded **Phase 3I: Organization Detail dirty-form protection**, adopting the existing
Contact blocker for the Organization detail editor only. Dependencies: inspect its RowVer opening baseline,
RETAIN/SET/CLEAR/extension semantics, advisory/mutation callbacks and tests; reuse 3G data-router/modal and
3H generation/credential/security teardown; preserve failed/conflicted Save and uncertain pending outcomes.
No persistent draft or replay; other forms and query cache remain separate scopes.

**Refresh remains a separate D3 blocker:** current server refresh uses a still-valid access bearer, rotates
current JTI with one conditional winner and extends expiry; no separate web refresh credential exists.
Lost responses can leave the old bearer unusable after a committed rotation; controller refresh exceptions
currently collapse to 401. Before a browser coordinator, review definitive rejection versus storage/network/
server uncertainty, current-JTI request races, reload TTL scheduling, failed replacement persistence and
unknown-state/write blocking. Agree server-compatible semantics and tests; do not invent old-credential retry,
mutation replay or a silent downgrade. The store abstraction resolves none of those decisions.

Rollback: revert only 3H or rebuild security-patched Phase 3G base `a1adf286` with the same API origin.
No storage migration or backend/session/SQL rollback is needed. Reversion removes the new exception bounds
and document suppression, so any residual storage must be handled explicitly. Deployment is separate work.

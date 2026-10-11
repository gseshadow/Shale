# Web V2 first-read-slice implementation contract readiness

**Inspected:** 2026-10-11. **Readiness documentation: COMPLETE; R2 first-slice owner decisions: ACCEPTED; live/deployment acceptance: OPEN.**
**Phase 2 acceptance: OPEN; Phase 3: IN PROGRESS; Phase 4: bounded R1/R2 server reads and R3 browser consumer implemented for review; first read slice INCOMPLETE.**
Appearance is provisional; visual refinement follows functional delivery. Accessibility remains an
acceptance gate. Ordinary web delivery is independent of MCP/AI activation.

The original documentation-only assessment below was against fetched `origin/codex/latest`
`ba3b1acad7710d4afeae099b2a24f9b4c3e77bbf`, on separate branch
`codex/web-v2-first-read-contract`. The original recommendations below were **PROPOSED**, not approved wire,
permission, audit, storage or deployment contracts. Existing endpoint exposure is not policy approval.
That original implementation readiness was conditional; the selected R2 server contract is now implemented
for review (§11). Deployment readiness is not established.
R1 compatibility work is recorded in §10; accepted R2 decisions and server evidence in §11 supersede
the earlier selected-slice proposals. Broader and live/deployment acceptance remain OPEN.

## 1. Evidence baseline and authorities

Read complete [AGENTS](../../AGENTS.md) and [prompt rules](../../architecture/codex-prompt-rules.md).
Routing review covered [system overview](../../architecture/system-overview.md),
[development rules](../../architecture/development-rules.md), the relevant A.2 design guidance in
[design system](../../architecture/design-system.md), [schema](../../architecture/database-schema.md)
Case/User/audit records, [tenancy](../../architecture/tenancy-and-rls.md),
[Case Team](../../architecture/case-team-roles.md), [firm roles](../../architecture/firm-wide-roles.md),
[case lifecycle](../../architecture/case-deletion-restoration.md), and the server/web cutover in
[summary inventory](../../architecture/case-summary-projection-inventory.md).
Also reviewed [Web V2 roadmap](shale-web-v2-architecture-roadmap.md),
[Step 2](../web-api-migration-step-2.md), [Step 3](../web-api-step-3.md),
[API readiness](../web-api-azure-readiness.md), [local smoke guide](../web-api-local-smoke-test.md),
[web deployment](../shale-web-deployment.md), [App Service](../azure-app-service-deployment.md),
[web README](../../shale-web/README.md), relevant durable-session/audit records in
[session roadmap](application-release-session-management.md), and
[change-aware checks](../testing/change-aware-test-selection.md).
Historical skeleton/read-only-beta descriptions are superseded by actual source and later records.
Credential authentication SQL lives directly in AuthServiceImpl, not a UserDao auth method; `/me` profile
SQL does use UserDao. Login 401 remains the controller's generic failure contract; the auth adapter also
turns AuthException into a failed result, so this status is not evidence of a particular password error.

**SOURCE** means inspected implementation; **TEST** means inspected synthetic/mocked/source-contract
test, not executed runtime proof. Prior review results are attributed to those reviews. This task
performed no authorized application login, SQL execution, deployed OpenAPI capture, host/device or
live audit-failure test. GitHub/git merge evidence proves source ancestry only.

| Merged prerequisite | Merge on fetched base | Retained contract / review |
| --- | --- | --- |
| Dependency security #1843 | `151d05a9` | [Remediation](../../shale-web/docs/security-dependency-remediation.md): Router/DOM 7.18.2, PostCSS 8.5.23, Nano ID 3.3.18, source-map-js 1.2.2. Recorded clean audits are dated registry snapshots, not a new audit or deployed-bundle proof. |
| 3A #1844 | `8cba4b85` | [Safe return](../../shale-web/docs/phase-3a-review.md): one verified-login redirect owner, safe path/query/hash and replacement history. |
| 3B #1845 | `a60297be` | [Startup uncertainty](../../shale-web/docs/phase-3b-review.md): retain uncertain bearer, block protected access, explicit Retry/local Return. |
| 3C #1846 | `f7ab40c8` | [Logout](../../shale-web/docs/phase-3c-review.md): local teardown first, truthful remote outcome. |
| 3D #1847 | `1280257a` | [Startup deadline](../../shale-web/docs/phase-3d-review.md): eight seconds for fetch/body per deliberate attempt. |
| 3E #1848 | `d8cc450f` | [Registry](../../shale-web/docs/phase-3e-review.md): existing route identities/navigation/returns. |
| 3F #1849 | `fcfcd1d3` | [Rejection](../../shale-web/docs/phase-3f-review.md): generation-bound 401 teardown and stale-result discard; 403 is operation denial. |
| 3G #1850 | `a1adf286` | [Router/Contact](../../shale-web/docs/phase-3g-review.md): operational data router and bounded dirty protection. |
| 3H #1851 | `406021e1` | [CredentialStore](../../shale-web/docs/phase-3h-review.md): bounded storage failures and residual-storage limitation. |
| 3I #1852 | `2179b2e7` | [Organization](../../shale-web/docs/phase-3i-review.md): same detail draft guard. |
| 3J #1853 | `ba3b1aca` | [Credential deadline](../../shale-web/docs/phase-3j-review.md): one eight-second login + matching `/me` budget; uncertain issuance remains possible. |

## 2. Selected operations and authoritative traces

Paths are repository-relative; links point to authoritative owners, not copies of their implementation.

| Operation / consumer | SOURCE trace and current contract | First-slice disposition |
| --- | --- | --- |
| Sign-in / LoginPage | [useStartupSession](../../shale-web/src/useStartupSession.ts) `signInWithCredentials` → [api](../../shale-web/src/api.ts) `login`, `getCurrentUser` → [AuthController](../../shale-server/src/main/java/com/shale/server/controller/AuthController.java) POST `/api/auth/login`, GET `/api/auth/me` → `AuthServicePort` / `AuthServiceAdapter` / `AuthServiceImpl.login` on auth datasource: parameterized TOP 1 dbo.Users by email/is_deleted=0, `BCryptPasswordVerifier.verify` → BCrypt.checkpw; tenant comes from returned User, not request input. `ServerAuthSessionService.issue` → SqlDurableSessionStore → UserSessionServiceAdapter → UserSessionDao.create verifies same-tenant nonremoved owner and both session keys, persists WEB session, then ShaleAuthTokenService signs bound token. `/me` uses `UserDaoCurrentUserProfileService` → `UserDao.findById`; fallback principal profile has false admin/attorney flags. `LoginResponse` contains authenticated, Bearer, accessToken, expiresInSeconds, user; `AuthenticatedUserResponse` is current profile. | Reuse unchanged 3A–3J installation/return contracts; no new login protocol. |
| Basic search / CasesPage | [App](../../shale-web/src/App.tsx) `CasesPage.handleSearch` → `api.searchCases` → [ApiReadController](../../shale-server/src/main/java/com/shale/server/controller/ApiReadController.java) GET `/api/cases/search`, validated query, fixed limit 25 → [CaseServicePort](../../shale-core/src/main/java/com/shale/core/service/CaseServicePort.java) `searchCases` → [CaseServiceAdapter](../../shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java) → `DaoCaseGateway.searchActiveForServer` → [CaseSummaryDao](../../shale-data/src/main/java/com/shale/data/dao/CaseSummaryDao.java) `searchActiveForServer` / `listActiveForServer`, offset 0. | Existing implementation searches **case name only**. Browser placeholder incorrectly promises number/client. First slice must say case name; no number/client/narrative search expansion. |
| Page search / no inspected browser consumer | GET `/api/cases/search-page` → `searchCasesPage` port/adapter → existing offset-aware gateway/DAO with checked `page*size` offset and requested size; no Case Java slice (R1, §10). [PagedResponse](../../shale-server/src/main/java/com/shale/server/dto/PagedResponse.java): items/page/size/total; total null. | Repair true SQL paging separately, retain legacy shape and semantics. |
| My Cases / MyCasesSection | `api.listAssignedCases` → GET `/api/cases/assigned` → port/adapter `listAssignedCases(userId, tenant, 25)` → gateway → `CaseSummaryDao.listActiveAssignedForServer`, actor must equal assigned user. SQL `EXISTS CaseUsers` applies before limit. | Current result is the first 25, not all assigned cases; no next page/count. Minimal new paged projection needs continuation. |
| Case deep link / CaseDetailPage | `api.getCaseDetail` → GET `/api/cases/{caseId}` (numeric route, positive ID) → `getAuthoritativeCaseDetail` → gateway `getDetail` → [CaseDao](../../shale-data/src/main/java/com/shale/data/dao/CaseDao.java) `getDetail` / `selectCaseDetail`; adapter then calls [CaseDateDao](../../shale-data/src/main/java/com/shale/data/dao/CaseDateDao.java) `listMigratedCompatibilityStateForCase`. | Broad edit/detail DTO is not a minimal Overview. Current browser also starts tasks and updates in `Promise.allSettled`; do not carry these requests into the read slice. |

Search/assigned results serialize [CaseOverviewDto](../../shale-core/src/main/java/com/shale/core/dto/CaseOverviewDto.java).
`ServerCaseRow` wraps `CaseSummaryProjection` plus dates, Description and caller/client/opposing-contact
identity/display. Adapter `toOverview` returns all these, including clients; browser `CaseSearchResult`
uses only a subset. A TypeScript interface does not strip the extra wire disclosure.
[CaseDetailDto](../../shale-core/src/main/java/com/shale/core/dto/CaseDetailDto.java) includes Description,
Summary, AcceptedDetail, DeniedDetail, ReceivedUpdates, workflow flags, related-contact email/phone,
status history, RowVer and nine mapped date/edit witnesses. `selectCaseDetail` adds unpaged
`listRelatedContacts` and `listCaseStatusHistory`; mapped-date composition borrows separately.
No row-count/page-shaped response bounds those collections or nvarchar(max) narratives.

[OpenAPI configuration](../../shale-server/src/main/java/com/shale/server/config/OpenApiConfiguration.java)
defines bearerAuth and generated `/v3/api-docs`. `getCase` returns Java Object, so concrete detail schema
cannot be assumed. [OpenApiDocumentationTest](../../shale-server/src/test/java/com/shale/server/controller/OpenApiDocumentationTest.java)
checks core path/scheme presence, not complete selected response schemas. No live schema was captured.

### Minimal proposed payload and behavior

A first Overview is an identity/assignment summary, not desktop Overview parity. Recommend this exact
allowlist for both list rows and the one-case response:

* `caseId` positive safe integer; `caseNumber` nullable string; `caseName` string.
* `status`: nullable `{id, name, color}` from shared current-status resolver.
* `practiceArea`: nullable `{id, name}` from same-tenant/global definition join.
* `responsibleAttorney` and `primaryLegalAssistant`: nullable `{userId, displayName}` from shared
  semantic compatibility assignment resolution; null means no value, not failed loading.
* `updatedAt`: nullable existing local date-time text; do not append Z or invent UTC conversion.

Case ID is navigation identity; duplicate/renamed labels never resolve a case. Preserve optional
relationships without dropping/multiplying cases. Internal Intake-date computation may remain solely
for assigned ordering, without returning dates or making date API calls. Do not infer permissions from
status or assignments.
No tenant selector/input, narrative, parties/contact data, full team, dates/confirmation/configuration,
RowVer/edit witnesses, history, tasks, updates, documents, mutations, unified suggestions, or live updates.
Those are separate contracts, not eager background requests. Names remain sensitive even without narratives.

PROPOSED additive minimized contracts: GET `/api/v2/cases/search-page`, GET
`/api/v2/cases/assigned-page`, GET `/api/v2/cases/{caseId}/overview`, typed response records and
`items/page/size/hasMore` (no count). These routes do not exist. They isolate narrowed payload/approved
policy/auditing from legacy broad consumers while reusing shared projection SQL and service boundaries;
no duplicate business rules or JavaFX dependencies. Prefer an existing endpoint extension if the API owner
can preserve legacy defaults and distinguish the minimized contract explicitly. Do not silently replace
legacy DTOs or overload page semantics. Endpoint choice needs API-owner acceptance.

First path: sign in → a cases workspace with Assigned and explicit case-name search → read-only Overview
→ Back. Keep `/cases` and `/cases/:caseId` working; choose the reviewed composition without removing
legacy write workflows globally. If adding `/cases/:caseId/overview`, add registry/safe-return coverage and
a reviewed alias transition; it is optional for this slice. Do not require a subsection router first.
Page/size may be bounded nonsensitive URL state; query stays in session memory, reset page on submit.
Blank query shows instructions, no tenant-wide browse. Search is deliberate submit, not suggestions or
keystroke audit. Back retains a bounded in-memory list/query for this identity; reload may require re-entry.

**Shared impact:** legacy search and assigned clients in App/My Shale expect `CaseOverviewDto`; legacy
detail and case mutation response consumers expect `CaseDetailDto`. Core/data ports also serve desktop
and non-browser callers. PR 1 changes only how existing search-page reaches SQL, not `/search` limit/order,
assigned limit/order, detail, Contact search or mutation return shapes. Minimal projections are additive.
Any policy hardening of shared legacy reads needs explicit compatibility/security rollout review; preserving
a legacy contract is not approval of its current disclosure.

## 3. Authorization and isolation, per selected read

[Bearer resolver](../../shale-server/src/main/java/com/shale/server/runtime/BearerTokenServerSessionResolver.java)
verifies JWT signature/time, then [durable validator](../../shale-server/src/main/java/com/shale/server/runtime/DurableSessionTokenValidator.java)
checks owned session, not revoked, expiry and current JTI. [SQL store](../../shale-server/src/main/java/com/shale/server/runtime/SqlDurableSessionStore.java)
→ `UserSessionServiceAdapter` → [UserSessionDao](../../shale-data/src/main/java/com/shale/data/dao/UserSessionDao.java)
qualifies tenant/user and joins nondeleted/nonremoved Users. Legacy-token overlap is separately bounded;
do not claim all deployed tokens are bound without verification. `/me` profile/roles are presentation,
not selected-case authorization.

Every domain request resolves principal via `ServerRuntimeSessionState`; request-scoped
[DB provider](../../shale-server/src/main/java/com/shale/server/runtime/RequestScopedDbSessionProvider.java)
→ `RuntimeSessionServiceConnectionProvider` → [RuntimeSessionService](../../shale-data/src/main/java/com/shale/data/runtime/RuntimeSessionService.java)
sets `ShaleClientId` and `PrincipalUserId` on every borrowed runtime connection. Initialization failure
closes it. Source does not set read_only=1 despite documentation examples. Auth pool is for credentials;
domain reads must not use it. Prod/azure bearer configuration ignores development identity headers.

| Read | Existing checks (SOURCE) | Missing policy / implementation gate |
| --- | --- | --- |
| `/me` | Verified principal; current User lookup tenant-qualified; bound session DAO rejects deleted/removed owner. | No case or field permission granted by this profile. Preserve unknown-authority handling on lookup/transport failure. |
| Case search and search-page | `verifyTenant` compares requested tenant to SQL SESSION_CONTEXT; `verifyEligibleAssignedUser` checks actor is a nondeleted same-tenant User. SQL filters active Cases by explicit tenant. Search has **no assignment/ACL predicate**. DAO actor argument is not compared to PrincipalUserId here. | Approve searchable case set and field allowlist; enforce same actor/session witness in new boundary. Tenant-wide exposure is implementation, not an accepted all-user policy. |
| Assigned list | Same tenant/user validation; actor=assignedUserId; CaseUsers membership EXISTS, any role/primary state, active case only. No closed-case exclusion. | Membership defines list selection only. It does not prove case/field permission. Recheck approved permission before selection/page/hasMore. |
| Detail, including mapped dates | `selectCaseDetail` primary query filters ID and active state, **no explicit c.ShaleClientId predicate**; tenant safety relies on runtime RLS. Related contacts explicitly tenant-qualified. Mapped-date/absence reads validate tenant, same-tenant nondeleted actor and active tenant-qualified Case. | No general case ACL/ethical-wall/field checks found. New minimized lookup must explicitly predicate tenant and approved case authority before reading fields, on one authoritative connection. Do not compose the broad legacy detail then redact. |

RLS isolates tenant rows, not users within a tenant. `CaseUsers`, Case Team role assignments,
firm-wide custom roles, `is_admin` and `is_attorney` are distinct concepts. None is evidence of an
approved general case-read ACL. No new permission name, role ID, admin bypass or field entitlement
is established here. The historical [RLS audit](../tenant-rls-audit-2026-06-29.md) and later documents
have coverage/Contacts-parent discrepancies; source joins explicitly qualify Contacts, but deployed
predicates must be inspected. Do not extrapolate broad documentation table lists into live enforcement.

**Historical recommendation (superseded by the tenant-wide R2 owner decision in §11):** initially restrict selected search and Overview to the same
active assigned-case set, with the minimal fields above and no admin exception; lower disclosure but
excludes unassigned cases and is not existing policy. Alternatively approve tenant-wide minimal reads
with explicit exceptions/ethical walls. Owner must identify any narrower field restrictions. Once chosen,
one server predicate must govern search, assigned pages, hasMore and direct lookup, before pagination.
Policy unresolved means blocked real-data slice, not a synthetic grant or an empty successful result.

Deleted cases are absent from this slice even for administrators; existing desktop Deleted Cases/restoration
remain separate. Missing, foreign-tenant, deleted and case-forbidden detail should yield the same safe 404
without names or existence clues. An operation-wide denial may return safe 403 and retains session; it is
not expiry. For stale membership, reauthorize deep link and refetch server-side. Clear the previous entity
before displaying denial; never fall back to the list row as authorized detail. Invalid ID is safe 400;
401 retains existing generation-bound session rejection/safe-return behavior. Mapping these new outcomes
is explicit work; legacy multi-query deletion races can currently surface generic errors, not guaranteed 404.

## 4. Genuine bounds and search semantics

R1 chain: controller validates query ≤100 after trim, page 0–100, size 1–100/default 25;
`Math.multiplyExact(page,size)` reaches the adapter/gateway/DAO as the row offset, with requested size.
`CaseSummaryDao.listActiveForServer` binds `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`.
Maximum row offset is **10,000** and maximum fetch is **100 rows**. Before R1, the maximum
prefix was **10,100 rows**, fetched from zero and sliced in Java. The existing SQL is unchanged.
Assigned SQL returns at most 25; neither it nor legacy search provides hasMore. SQL limit bounds returned
rows, not scanned/sorted work or bytes. Description is nvarchar(max), so current row limits are not payload
limits. No query timeout is set in this selected DAO. Client abort does not prove SQL stopped.

Search strips/lowercases with Locale.ROOT, then `LOWER(COALESCE(c.Name,'')) LIKE '%…%'`;
`escapeLike` escapes `[`, `%`, `_` using SQL Server bracket literals. Unicode/collation behavior needs
real SQL verification. It is literal case-name substring, not full text, prefix, number or client search.
SQL search order is `c.Name ASC,c.Id ASC`; assigned order is status sort order, authoritative Intake date
DESC, Case ID DESC. Intake comes from CaseDates, not legacy CallerDate. Equal sort values have ID tie-breaks;
offset pages can still shift during concurrent edits. Empty normalized search returns no rows before DB access.

| Budget / behavior | PROPOSED first-slice contract | Compatibility / tradeoff |
| --- | --- | --- |
| Page | default/max 25; pages 0–100; offset ≤2,500; fetch ≤26 for hasMore | At ceiling, hasMore still reflects the extra authorized row; disable Next and label result-window limit if true, rather than claiming end-of-results. Legacy page caps stay 100/100 in PR 1. |
| SQL execution | Exact requested offset, size(+1 only for new hasMore); 5-second JDBC statement timeout for selected new domain statements; 8-second total client fetch/body recovery budget | Measure on representative authorized data; timeouts fail safely, never truncate as success. Pool/auth/audit time is additional server work; client deadline is not server execution guarantee. |
| Query | Trimmed 1–100 UTF-16 code units, Unicode literal case-name substring; blank gives instructions/empty search contract; no minimum 2/3 chars or semantic expansion | Keeps current search meaning. Lowercase/collation and wildcard literal tests required; no claim of index seek under leading wildcard. |
| Order | Search name ASC, ID ASC; assigned retain status-sort/Intake DESC/ID DESC | Reuse existing predictable order. No snapshot pagination promise; explicit reload resets page. No new sort/filter vocabulary. |
| Fields / bytes | Allowlist in §2; cap name/display 255 code units, number 200, color canonical or neutral fallback; ≤128 KiB UTF-8 JSON page, ≤8 KiB Overview | Enforce at SQL/projection and serialization boundary, not after transferring max fields. Recommend safe failure for oversized historical values, never silent or identity truncation; any display-truncation indicator is a separately approved DTO change. These limits need schema/data compatibility verification. |
| Transport/cache | application/json concrete schema; authenticated response Cache-Control: no-store; no shared/persistent PHI cache | Proposed response headers/validators, not current verified deployment settings. GET query may enter access logs despite memory-only UI; choose POST body search or reviewed query-log/referrer suppression before live use. |

No expensive count required. `hasMore` derives from one extra **authorized** row. Invalid page/size/query
returns safe 400; malformed or oversized response is an error, not successful empty. No automatic Retry,
focus polling or mutation replay. Test exact bindings/execution delegation rather than only response length.

## 5. Required sensitive-read audit contract

Desktop owner: [CaseController](../../shale-ui/src/main/java/com/shale/ui/controller/CaseController.java)
`showOverview` path calls `auditCaseRead("Case.Overview.Read", "Case.Overview")`;
Detail/Timeline/Calendar have their own established names. [PhiReadAuditService](../../shale-ui/src/main/java/com/shale/ui/services/PhiReadAuditService.java)
gets actor from AppState, uses Case object type 1 and FieldCode 4, writes `action=READ;screen=Case.Overview`
through [AuditLogDao](../../shale-data/src/main/java/com/shale/data/dao/AuditLogDao.java), null date value.
It dedupes for two seconds by actor/object/field (not tenant) and catches persistence errors. This is
screen-open intent, not per-field rendering. It cannot be imported into server or called by the browser
as authoritative audit. No such audit call was found in the selected controller/adapter/DAO reads.

Non-UI reuse evidence: `AuditLogDao.appendPhiWriteAudit(Connection,…)` stamps tenant from SQL session
and throws on append failure. [PhiAuditService](../../shale-data/src/main/java/com/shale/data/dao/PhiAuditService.java)
is a registered-field **write** helper, not read orchestration; its convenience overload suppresses failures.
[AdministrativeReadAuditEvent](../../shale-data/src/main/java/com/shale/data/dao/AdministrativeReadAuditEvent.java)
only allows instance recent-list/version-distribution. `ApplicationInstanceAdminReadDao` demonstrates
read + required audit on one connection/transaction, commit before returning. Session administration
has its own SessionSecurityAuditLog. Entity-action vocabulary is for domain actions, not an invented CASE/READ.
Timeline and browser telemetry are not compliance audit substitutes.

**Recommended authoritative seam:** a small non-UI selected-read service/DAO boundary behind CaseServicePort,
with verified tenant/actor, approved case/field predicate, bounded minimized query, connection-bound required
read append and commit **before** returning DTO. Capture trusted actor/tenant/case once; verify both session
keys on that same connection. For one-case Overview reuse exactly `Case.Overview.Read`, Case type 1,
FieldCode 4, `action=READ;screen=Case.Overview`, no sensitive values. Generic viewer already derives READ
from action=read; [AuditLogViewerController](../../shale-ui/src/main/java/com/shale/ui/controller/AuditLogViewerController.java)
remains tenant-admin presentation. Test viewer classification; do not change desktop failure policy incidentally.

Recommend server request delivery as auditable read intent: each successful Overview read, including explicit
refetch/reopen, writes one existing event. React rerenders and reuse of an already-delivered in-memory result
write none. No background refetch in first slice; StrictMode duplicate requests may create duplicate reads.
Do not copy desktop two-second suppression: it can hide different views/tenants and skip required delivery audit.
If owners require exactly one event per view intent rather than per delivered request, separately approve a
bounded dedupe key scoped to tenant/actor/case/intent and concurrency semantics; never trust a client screen
label or key as permission to skip audit. Failed delivery after committed audit remains an auditable server
read, not proof that the human saw it. No automatic replay after timeout.

**Historical list/search policy gap (closed for this first slice by the explicit R2 summary exemption in §11).** Desktop summary/search does not establish web exemption. Recommend
required server auditing of successful minimized page disclosure, using existing approved vocabulary only
where its meaning fits. No established page/search event was found; do not call a page Case.Overview.Read
or invent Case.Search.Read. Until audit owner either approves an explicit summary exemption with rationale,
or approves a scoped vocabulary/storage enhancement, PR 2 and live list disclosure remain blocked.

Separate enhancement inventory:

* Overview reuse appears representable in existing AuditLog; verify deployed FieldName/FieldCode constraints,
  ObjectTypes mapping, insert grants, tenant RLS and viewer before claiming no migration required. The
  [AuditLog tenant migration](../sql/2026-04-15_auditlog_shaleclientid_tenant_scope_phase1.sql) only adds a
  nullable tenant column/index, deferring historical backfill; it does not establish live RLS predicates.
* Search/page needs an approved representation if auditing is required. AuditLog has one ObjectId and cannot
  express a multi-case page/context automatically. Extending administrative/entity enums alone is invalid;
  review Java and SQL allowlists, tenant/actor FKs, viewer and retention together in an optional dedicated PR.
* Parent/case context is ObjectId for Case Overview. Source WEB, result count/page and correlation are not
  currently part of that exact vocabulary; approve any extension explicitly. Never store query, case names,
  narrative, dates/values, result DTOs, tokens/JTI, RowVer, headers, SQL or exception text as audit metadata.

Recommended failure policy needs audit/security-owner approval: **fail closed**, rollback read-audit transaction,
return safe 503 audit-unavailable (or agreed safe 5xx), no DTO/partial response/cache update, manual read Retry.
Authorization/validation/not-found/read failures emit no success-read event; denied-access security logging is
separate approved work. Do not swallow errors, append asynchronously, audit after response, or queue in browser.

## 6. First-slice session policy and minimal client infrastructure

[ServerAuthSessionService](../../shale-server/src/main/java/com/shale/server/runtime/ServerAuthSessionService.java)
refresh accepts still-valid current bearer, validates current JTI, conditionally rotates the same sid through
UserSessionDao; SQL checks expected JTI, not revoked and DB expiry. One concurrent attempt wins.
[AuthController](../../shale-server/src/main/java/com/shale/server/controller/AuthController.java) collapses runtime
refresh exceptions to 401. A committed rotation with lost response leaves the old bearer unusable;
repeating it cannot recover the new JTI. Late old-JTI responses can reject a newer browser generation if a
coordinator is careless. Browser reload currently persists opaque bearer only, no trusted TTL deadline.

| Option | Required work / limitation | Recommendation |
| --- | --- | --- |
| Coordinated refresh | Single flight per generation, gate new requests during rotation, atomic replacement memory/storage, late-response and old-JTI rejection handling, lost-response unknown authority, reload scheduling, no mutation replay. Failed persistence after rotation can strand the session; do not restore old token or fallback storage. Review ambiguous refresh 401 and multi-tab/replica tests. | Defer. Existing proposal is not approval; too many unresolved recovery/security contracts for the first read slice. |
| Explicit expiry/re-login | No browser refresh call, no new TTL persistence/parser/timer authority. Server rejects expired/revoked token on next selected request; use existing current-generation 401 teardown and safe return. Reload verifies stored bearer via bounded `/me`. Display limitation clearly: session can end and require sign-in; idle screen is not continuously revalidated. | Smallest defensible first slice, **conditional on owner acceptance** of limitation and existing per-tab storage. Not a new security/storage-policy approval. |

Retain startup uncertainty: network/5xx/unusable `/me` retains bearer, installs no identity, mounts no protected
content or feature requests, explicit Retry/local Return only. Login remains one shared eight-second budget,
usable matching identities, successful CredentialStore persistence before installation. Lost login/verification
response or failed persistence can leave server issuance active; no cleanup/replay claim. Concurrent submissions
coalesce; late bodies after cancel/replacement/logout do not install or clear newer credentials. Logout tears
down immediately and makes one bounded remote attempt; only existing HTTP 200 + revoked:true confirms that
endpoint outcome. Removal failure leaves residual-storage uncertainty visibly reported, not reused authority.
Unknown request/session authority must not authorize a new protected disclosure; never label transport failure
as invalid password/revocation. No automatic request or mutation replay, even after sign-in. Preserve safe path/
query/hash/history rules; any new URL query policy is scoped, not a rewrite of established return behavior.

Only a small feature client is required:

* Concrete Java DTO/OpenAPI schema and local TypeScript validators for the selected page/Overview response:
  actual unknown JSON, IDs safe for JS, nullable relationships, bounded strings/array/bytes, hasMore/page/size,
  valid local timestamps and sanitized color. TypeScript casts in api.ts are not runtime validation.
* Reuse [sessionRequests](../../shale-web/src/sessionRequests.ts) generation binding, feature 401 ownership
  and body guard. It currently replaces init.signal with its session signal; add narrow caller cancellation
  composition for route/query changes plus per-attempt total fetch/body budget, with listener/timer cleanup.
  Latest query/route generation must guard success **and failure**; ignored abort is not sufficient.
* Safe errors distinguish loading, accepted empty/filtered empty, 400, operation 403, unavailable 404,
  audit/server unavailable, connection/deadline and malformed response. Never render raw error body/SQL.
  Clear old case before switching ID; retain stale list only labelled within same verified identity.
* Mounted feature state or one bounded memory snapshot, keyed by session generation + tenant + actor +
  operation/query/page/ID. Clear on sign-out/rejection/identity replacement/storage failure; abort pending
  work and reject late callbacks. No persistent PHI query/draft/cache or background focus/reconnect fetch.

No new cache, form or schema dependency is necessary. Native search form and existing primitives suffice;
handwritten validators for this small allowlist are reviewable. TanStack Query, form libraries and schema/code
generation remain wider D2 decisions; first slice does not require a broad api.ts rewrite or all-form protection.
Existing create/edit functionality outside the selected composition remains separate; no new write entry/replay.

## 7. Delivery sequence, tests and rollback

R1 is now bounded backend implementation work (§10); R2–R4 remain conditional future PRs.
Files/symbols named new in those later rows remain proposals.

| PR / dependency | Bounded implementation and files/symbols | Behavioral evidence / rollback |
| --- | --- | --- |
| R1 — exact legacy case-search SQL paging; independent of new exposure | `CaseServicePort` new explicit paged operation; `CaseServiceAdapter` / CaseGateway / DaoCaseGateway delegation; `CaseSummaryDao.searchActiveForServer` offset/limit; `ApiReadController.searchCasesPage` passes offset, removes case prefix/slice usage only. Preserve query/order/DTO/page/size/total and legacy caps; no client adoption/policy change. | Update existing `ApiReadControllerTest.caseSearchPageReturnsPageContractWithDevelopmentHeaders` obsolete prefix-limit assertion and recording port. Extend `CaseServiceAdapterTest` to prove production delegation; JDBC execution/binding test size vs prefix at pages 0/1/100; duplicates, empty/final page, literal wildcard/Unicode tests; OpenAPI compatibility. Revert R1; old prefix cost returns. Existing audit/policy/payload gaps remain, not approved by this fix. |
| R2a — audit enhancement, only if page policy requires it | After owner decisions, scoped approved model/DAO + SQL allowlist/schema/viewer changes, separate from case queries. No fabricated event here. | Migration/allowlist/viewer plus fail-closed insert tests; authorized non-dbo RLS/grants verification before dependent deployment. Additive history remains on rollback; do not delete audit rows or disable protection. If explicit summary exemption accepted, omit this PR and record rationale. |
| R2b — minimal authorized/audited read API; depends on selected D1/D5/D6 and R1, R2a if needed | Proposed typed DTOs, CaseServicePort operations, adapter gateway production delegation; narrow CaseSummaryDao read projection and connection-scoped audit worker; ApiReadController/versioned read controller + OpenAPI/errors. All selected list/detail policy filtering precedes page/disclosure; no broad CaseDao detail composition. | Same-tenant assigned/unassigned/denied-field rules, removed actor, missing/deleted/foreign ID 404, ≤26 SQL rows, no excluded columns/child loads, oversized fields/payload errors, audit commit before DTO and audit failure returns none. Test actor/session mismatch. Mock/source tests labelled synthetic. Revert new routes/worker, keep legacy stable; do not relax authority/audit as rollback. |
| R3 — usable browser path; depends on accepted R2b contract and explicit D3 re-login limitation | New `features/cases` feature client/validators and read workspace/Overview; App route composition, api compatibility facade, sessionRequests optional cancellation, registry only if new alias needed. Reuse ui/shell. No tasks/updates/lookups/mutations on selected Overview. | Existing App/returnPath/useStartupSession/sessionRequests/sessionRejectionApp/credentialStore/credentialLogin/logout plus focused malformed/bounds/latest-result/cancel/identity/403/404/audit-error/back/deep-link tests. StrictMode duplicate/late requests and no refresh/replay assertions. Full web tests/typecheck/build, selector-relevant Java compatibility; isolated synthetic browser fixture. Revert composition/feature client only; preserve security patches/3A–3J. |
| R4 — acceptance record; after R3, authorized target access | Record approved fixture identities/tenants, source/build/API refs, sanitized live outcomes, host/device/accessibility limitations; roadmap statuses only after evidence. | Live checklist below; no deployment activation implicit. Frontend artifact rollback independent of server durable-session authority; pre-7B server rollback restrictions still apply. |

For each implementation PR inspect existing tests before editing, inventory affected symbols/SQL bindings,
maintain intentionally superseded assertions, run focused tests → repository selector's relevant suite →
critical Maven under prompt rules. No unrelated all-tests/visual suite to inflate evidence; no GitHub test workflow.

### Acceptance gates: synthetic versus authorized live

1. **SOURCE/TEST implementation:** concrete OpenAPI selected schemas/security/errors; exact DAO execution
   bindings/row and byte limits; no per-row overview hydration; policy applied before pagination/hasMore;
   connection-bound audit ordering/failure; legacy compatibility. Tests against fakes/source are synthetic.
2. **Authorized live session:** approved test user/API over HTTPS in prod/azure (no dev identity headers).
   Verify WEB issuance, `/me`, selected reads, expiry/re-login, reload during uncertainty, remote revocation,
   old bearer rejected after confirmed logout; 3A–3J timeout/late/storage messages remain truthful. Use test
   short TTL only in an independently authorized environment; do not change production TTL to test.
   Session SQL unavailable is unknown/failed service, not a fabricated successful identity.
3. **Two tenants/non-dbo:** approved disposable fixtures for two tenants, ordinary/nonremoved/removed users,
   assigned/unassigned/closed/deleted cases and forbidden fields according to approved policy. Confirm list,
   search/page/hasMore/deep-link never disclose forbidden/foreign/deleted data. Inspect actual enabled RLS
   predicates for Cases/Users/CaseUsers and selected joins/audit tables; exercise non-dbo runtime identity,
   alternating tenants/actors on pooled connections and null/mismatched context. dbo catalog output alone
   proves no enforcement. Check strict tenant versus global lookup semantics separately. No ad-hoc production
   fixtures, grants or policy edits in this documentation task.
4. **Audit failure:** in authorized isolated environment, fault injection/approved denied-insert fixture,
   assert no response data/partial render on failed required append/commit, safe error and manual Retry only.
   Verify tenant/actor/Case ObjectId/vocabulary in persisted successful Overview rows and page representation
   if approved; no PHI/query/tokens in metadata. Audit viewer renders existing READ correctly; no timeline
   substitute. Restore exact fixture/grants through authorized procedure; no disabling production audit.
5. **Host:** actual static host HTTPS/origin, exact CORS, compiled API origin, `/cases/:id` refresh/direct link,
   login return suffix/history, old/new assets and safe error fallback. API failures must never become SPA
   HTML successful JSON. Verify authenticated no-store and approved query-log/referrer handling. Vite/mock
   navigation is synthetic, not deployed-host proof. Deployment operator still owns pilot/rollback/asset window.
6. **Accessibility:** keyboard Search/Assigned/paging/open/Back/Retry/logout; route heading/focus, result/error
   announcements and forbidden/not-found meaning; 320/360/768/1280, both themes, 200% text/400% zoom,
   long names/no overflow and 44px targets. Real screen-reader speech, Firefox/WebKit and physical mobile
   keyboard/safe-area checks remain explicitly open when unavailable. Axe/headless screenshots are synthetic,
   useful checks but not physical-device/AT or final aesthetic acceptance.

### Documentation task verification

Test-impact inventory: no production or test contract changes. Inspected controller pagination/detail and
OpenAPI tests, CaseSummaryServerProjectionContractTest (SQL/source assertions), CaseServiceAdapterTest
(gateway delegation), durable-session tests (memory store), and browser App/sessionRequests/credential
contracts and 3A–3J review records. These support source understanding and future test routing, not a
new executed Java/browser result. No test assertion or inventory file needed maintenance in this task.

* `python3 build/test-selection/select_tests.py --base ba3b1acad7710d4afeae099b2a24f9b4c3e77bbf --head HEAD --format markdown --output work/first-read/test-selection.md` — PASS: only these two
  documentation paths, no selected area/module/test, no full-suite escalation. Selector always prints
  generic `mvn test`; no affected runtime suite exists for this scope. Per the user's documentation-only
  instruction, unrelated Maven/npm/browser suites were not run to inflate evidence.
* `python3 work/first-read/check_docs.py` — PASS: both changed documents' local link targets, balanced
  Markdown fences, status/scope markers and trailing whitespace; all eleven prerequisite merge commits
  are ancestors of the pinned base and all five patched lock versions are retained. Scratch checker is
  local task tooling, not a new repository test or runtime proof.
* `git diff --check`; `git diff ba3b1aca HEAD --check` — PASS. Base-to-head changed-file review contains
  only this contract and the roadmap. Production/dependencies/SQL/auth/deployment/versions unchanged.

No live security/session/SQL/host/accessibility readiness is claimed. Documentation rollback reverts these
two files only; retained dependencies/security patches and 3A–3J source remain untouched.

## 8. Original decision table and owner questions (selected R2 answers accepted in §11)

| Question | Verified evidence | Recommended choice and tradeoff | Owner approval needed | Implementation blocked by decision |
| --- | --- | --- | --- | --- |
| D1: which cases/fields can each actor read? | Search is tenant-wide; assigned is membership-selected; detail relies on RLS and has no general case/field ACL. Roles are distinct authorities. | Start active assigned-case set and §2 minimal fields, no admin bypass; narrower than current exposure. Owner may explicitly approve tenant-wide scope/ethical-wall rules instead. | Product/security/domain: concrete predicate, exceptions and fields; no new permission invented. | R2b policy, R3 real-data consumer, live acceptance. R1 compatibility-only bounds can proceed. |
| D1: deletion/deep-link concealment? | Selected SQL excludes deleted Cases; desktop lifecycle has separate transactional owner. Legacy detail lacks explicit root tenant predicate. | Same safe 404 for missing/foreign/deleted/case-forbidden; 403 only operation-wide denial. Explicit root tenant predicate. | Security/domain accept outcome mapping, including any admin exception (none recommended). | R2b direct lookup/error contract. |
| D5: exact limits/order/query? | Query 100; page 0–100/size 1–100; SQL prefix up to 10,100; leading-wildcard name-only; assigned 25. | R1 preserve legacy values/meaning with true offset; new 25/26-row, offset 2,500, 5s query/8s client, 128KiB/8KiB, no count. Less data/scanning; offset drift explicitly tolerated. | API/domain approve proposed new budgets and oversized-history treatment; maintainer reviews R1 compatibility. | New page/DTO/timeout contracts R2b/R3; no assumption of approved numeric values. |
| D6: Overview audit seam/failure/refetch? | Desktop established Case.Overview.Read, catch/suppress + 2s dedupe; connection DAO can throw; selected server reads unaudited. | Non-UI read + existing event on same connection, commit before data, fail closed, one per successful server read; no dedupe/refetch automation. Duplicate synthetic mount deliveries may yield two events. | Audit/security confirm meaning, failure outcome and deployed schema/grants/viewer. | R2b Overview delivery and R3 live use. |
| D6: summary/search page audit? | No established ordinary web page/search vocabulary found; administrative enum is instance-only. | Explicit owner exemption with rationale, or dedicated scoped enhancement; recommend auditing sensitive disclosure if policy requires it, never fabricate names/events. | Audit/security/legal select requirement/representation/retention. | R2a if required, R2b page disclosure. |
| D3: first-slice session/storage? | Current-JTI one-winner rotation; lost response strands old bearer; refresh exceptions collapsed to 401; reload opaque storage; 3A–3J recovery implemented. | Explicit expiry/re-login, preserve current CredentialStore/per-tab policy, no refresh/new persistence/timer authority. Session can expire mid-read and idle data is not continuously revalidated. | Security/API/product accept limitation and existing storage exposure; this doc approves neither. | R3 real-data session acceptance. Coordinator deferred, not Phase 3 complete. |
| D2: new cache/form/schema stack? | Existing local state, React Router, Vitest; JSON casts; generation seam available. | No dependencies required; narrow validators, cancellation and identity-scoped memory snapshot. Less generic tooling, smaller usable slice. | Maintainer review only; separate decision if introducing dependency/storage change. | Broad stack choices do not block R1–R3 with current tools. |
| D5/D9: endpoint compatibility and sensitive GET logs? | Broad legacy DTOs used by App; existing GET query and host/CORS plan. | Add minimized versioned projections sharing workers; preserve legacy shapes; body search or explicit log/referrer suppression before live search. Additional API surface preserves clients. | API/security choose version/extension and query privacy; deployment operator verifies target. | R2b route choice; live search/host gate. |
| D9: deployment readiness? | Source SPA fallback and historical Azure session reports; no selected live proof here. | Independent authorized pilot after contract/implementation tests and live tenant/audit/session/host/AT gates. Appearance polish follows functionality. | Deployment/product select target, pilot, asset/rollback window and acceptance. | Deployment only; no MCP/AI activation dependency. |

The selected R2 answers to questions 1–4 are accepted in §11. Question 5 and broader/live acceptance
remain OPEN. Original questions:

1. Approve the exact case set, minimal fields, exceptions and deleted/forbidden 404 behavior.
2. Approve Overview fail-closed server audit/refetch semantics; decide summary/page audit exemption or
   scoped representation enhancement, including any schema/allowlist/viewer change.
3. Accept first-slice expiry/re-login and existing per-tab CredentialStore limitations, leaving refresh off.
4. Approve new bounds/oversized-value treatment, minimized endpoint compatibility and search query privacy.
5. Before deployment, provide authorized live test target/fixtures and pilot/host/accessibility acceptance owners.

## 9. Ready-to-run first implementation PR prompt

```text
Repository: gseshadow/Shale
PR base: codex/latest
Task: R1 — exact SQL paging for existing case search-page, compatibility only.

Fetch live origin/codex/latest. Read AGENTS.md and architecture/codex-prompt-rules.md completely,
follow documentation routing, and read docs/architecture/shale-web-v2-first-read-contract.md plus
Web V2 roadmap. Create a separate task branch from fetched base; preserve unrelated work.

Conditional contract: proceed only with the compatibility-only R1 scope below. If it requires a new
permission/field policy, changed response/limits/search semantics, or new audit representation,
record the unresolved decision and stop that dependent implementation. Current exposure is not
security approval. Do not enable the first real-data V2 slice or claim its policy/audit gates closed.

Trace and inspect ApiReadController.searchCasesPage, CaseServicePort, CaseServiceAdapter and its
production CaseGateway/DaoCaseGateway delegation, CaseSummaryDao.searchActiveForServer and SQL,
ApiValidation, PagedResponse, ApiReadControllerTest, CaseServiceAdapterTest,
CaseSummaryServerProjectionContractTest and OpenApiDocumentationTest before editing.

Replace prefix fetch + in-memory case slice with explicit checked page offset and requested size
reaching SQL execution through the existing port/adapter/gateway. Keep legacy query trim/max 100,
page 0–100, size 1–100/default 25, name-only literal substring, Name ASC/Id ASC, active/tenant/actor
checks, CaseOverviewDto and items/page/size/total-null behavior. No hasMore/count/new client here.
Keep /cases/search fixed 25, assigned ordering/limit, Contact pagination and detail/mutation paths
unchanged. Interface default must throw actionable unsupported, never mimic empty success.

Add meaningful delegation/JDBC binding tests proving nonzero offset and size rather than prefix
at pages 0/1/100, empty/final pages and tied names. Update the existing recording-port/prefix-limit
assertions deliberately; retain tenant/actor and literal wildcard/Unicode contracts. Inspect all
neighboring tests. Label mock/source tests synthetic; no live SQL performance or RLS proof inferred.
No new dependencies/schema/auth protocol/deployment/version, broad transport/cache or UI change.

Run focused affected tests, repository change-aware selector and its relevant suites/critical
checks under prompt rules, plus git diff --check. Document exact commands/results and any blocked
live SQL test. Update roadmap/readiness with R1 evidence while Phase 2 acceptance stays OPEN,
Phase 3 IN PROGRESS, and Phase 4 remains incomplete (record only the bounded backend work begun).
Do not claim first-slice or deployment readiness: minimal payload, approved permission, required
sensitive-read audit, D3 re-login acceptance and authorized live/host/accessibility gates remain.
Commit and push separate branch, open PR targeting codex/latest. Do not merge or deploy.
Return PR, compatibility evidence, limits, rollback (revert only R1) and next conditional R2 step.
```


## 10. R1 exact legacy Case search-page SQL paging — 2026-10-09

Separate task branch `codex/r1-exact-case-search-paging` from explicitly fetched live
`origin/codex/latest` **`227c36ad73ca114e69c1c0ae1a35f9152d5604c3`**. PR #1854 and all eleven
3A–3J/security prerequisite merges are ancestors; the five patched Router/DOM, PostCSS, Nano ID
and source-map-js lock versions are retained. Original checkout preserved through an isolated worktree.

**Scope and compatibility:** `ApiReadController.searchCasesPage` passes checked `page*size` and
requested size to the new explicit `CaseServicePort.searchCasesPage` operation. Its unsupported default
throws an actionable error. `CaseServiceAdapter` delegates to the existing production
`CaseGateway.searchActiveForServer` / `DaoCaseGateway` / `CaseSummaryDao.searchActiveForServer`,
which already accepts offset/limit. The existing SQL statement, projection and DTO mapper are reused;
no new DAO, query, SQL columns, predicate or schema change. The controller returns the SQL page directly.

Query trim/max 100, page 0–100, size 1–100/default 25, literal Case-name substring and bracket escaping,
Name ASC / Id ASC, active-case filtering, tenant/session equality and eligible same-tenant nondeleted actor
checks remain. `CaseOverviewDto` and exactly `items/page/size/total:null` remain. `/cases/search` and
assigned still fetch 25 with their established orders; Contact prefix/slicing, detail and mutation
contracts are unchanged. No hasMore/count/new endpoint/browser adoption or policy change.

**Test impact:** inspected existing controller recording-port/prefix expectation and neighboring search,
assigned, Contact, detail/mutation and bearer tests; adapter/gateway, projection source contracts and OpenAPI
coverage before edits. Updated the obsolete prefix assertion; added exact page/default/bounds/query/wire
checks, explicit unsupported-default/delegation checks, and `CaseSearchPagingJdbcTest` through the public
production adapter constructor to the real gateway and DAO. The JDBC doubles execute prepared statements
and capture all six bindings, including `(offset,size)` **(0,25), (25,25), (10000,100)** for pages 0/1/100.
They also protect final/empty pages, no second slice, SQL-requested tied-name ordering and row-order mapping,
blank/no-connection behavior, literal `%`, `_`, `[` and Unicode normalization, tenant/actor failure before
Case query, unchanged legacy search/assigned bindings and orders. Existing projection tests remain relevant.
OpenAPI verifies query/page/size defaults, bearer security, the four-field page and `CaseOverviewDto` schema.
The new JDBC test is owned by the existing cases selector area.

**Local validation:** Maven 3.9.11 / JDK 21 were installed in unpublished scratch storage because
this environment initially had only a JRE. All commands below use `-Dmaven.compiler.parameters=true`
for existing controller parameter-name reflection, with session proxy/trust and local-repository settings;
no POM, repository dependency or version changes.

* Focused: `mvn -Dmaven.compiler.parameters=true -pl shale-server -am
  -Dtest=ApiReadControllerTest,OpenApiDocumentationTest,CaseServiceAdapterTest,CaseSearchPagingJdbcTest,CaseSummaryServerProjectionContractTest
  -Dsurefire.failIfNoSpecifiedTests=false test` — **79 passed**, zero failures/errors/skips.
* Change-aware selector: `python3 build/test-selection/select_tests.py --base
  227c36ad73ca114e69c1c0ae1a35f9152d5604c3 --head HEAD --format markdown` selects cases, contacts,
  organizations, reports, server and tasks (shared port consumers), nine classes across core/data/server/UI.
  Its selected Maven command with the compiler metadata flag — **115 passed**, zero failures/errors/skips.
  `python3 -m unittest discover -s build/test-selection -p test_select_tests.py` — **24 passed**.
* Required critical: `mvn -Dmaven.compiler.parameters=true test` — **116 passed**, zero failures/errors/skips.
* Selector-recommended advisory `mvn -Dmaven.compiler.parameters=true -Pall-tests test` — **FAILED**:
  shale-data ran 804 tests with 12 failures, zero errors/skips; downstream UI/updater/desktop/server were
  not run. Baseline reproduction is recorded below; this is not a passing full-reactor result.
* Documentation relative file links, status/scope checks and `git diff --check` passed.

Baseline check: a detached worktree at fetched `227c36ad` ran
`mvn -Dmaven.compiler.parameters=true -pl shale-data -am -Pall-tests
-Dtest=AdministrativeReadAuditMigrationContractTest,ApplicationInstanceHeartbeatMigrationContractTest,ApplicationReleaseImportContractTest,CaseDaoCasesGridQueryTest,CaseDateTypeLifecycleCutoverContractTest,CaseDatesFinalRuntimeCleanupContractTest,CaseOverviewConfigurationContractTest,CaseSummaryReportsContractTest,ContactPhase2BAuditMigrationContractTest,FormConfigurationFoundationTest,SessionInvalidationPhase8AContractTest,UserSessionMigrationContractTest
-Dsurefire.failIfNoSpecifiedTests=false test`. Its 51 tests reproduced **the same 12 failing methods**,
zero errors/skips. These existing source/migration contract failures are outside R1 and were preserved;
no full-suite or future security acceptance is inferred. This rerun covers the failing classes, not the
entire base reactor.

The existing OpenAPI fixture needed a schema-only session service (database access explicitly fails),
and stale release assertions were aligned to the generated wildcard media key and inline closed enums.
Production schema annotations, session wiring and release contracts are unchanged. Generated local OpenAPI
and MockMvc responses are compatibility evidence, not deployed-host acceptance.

**Limits and audit review:** JDBC/source/OpenAPI evidence is synthetic. No live SQL performance, scanned work,
collation/Unicode acceptance, concurrency/snapshot guarantee, or two-tenant/non-dbo RLS acceptance is claimed.
No authorized live SQL/application/host/device/audit-failure acceptance was performed. The broad legacy payload
and unbounded narrative bytes remain; changing row fetch does not minimize fields or bound scanned/sorted work.
Existing actor validation is retained, not upgraded to a new session-actor/case ACL. Existing server search
read-audit gaps remain unresolved; no audit event, exemption, representation or failure-policy decision is
invented, no audit integration/schema/migration is added. Existing exposure is not V2 security approval.

**Status:** Phase 2 acceptance **OPEN**, Phase 3 **IN PROGRESS**. Only bounded Phase 4 R1 backend work
has begun; the first end-to-end read slice is **INCOMPLETE**. Permission/field/deletion policy, minimized
payload, required read auditing, first-slice session acceptance and authorized live/host/accessibility gates
remain **OPEN**. Appearance remains **provisional**. No UI, authentication protocol, dependency, deployment
configuration or version change; no merge or deployment.

**Rollback:** revert only the R1 commit (controller, paged port/adapter, focused tests/selector ownership and
these readiness/roadmap updates). The old SQL-prefix/Java-slice cost returns, up to 10,100 rows. No SQL,
schema, audit-history, session, security-patch or frontend rollback is required.

**Next conditional R2:** obtain exact D1 case/field/deletion policy, D5 minimized bounds/oversized-value,
endpoint compatibility/query-privacy contract, D6 Overview fail-closed/refetch audit and page/search audit
representation or explicit exemption, plus D3 first-slice expiry/re-login acceptance. If required page auditing
needs a safe vocabulary/schema/viewer enhancement, isolate R2a first; only then implement R2b minimized,
authorized, audited server reads. No new real-data browser consumer is authorized by R1.

## 11. R2 minimized tenant-wide Case reads — 2026-10-10

Separate branch `codex/r2-minimized-case-reads` from freshly fetched live
`origin/codex/latest` **`fe029cb2d157a3e4f1f86130f3872d731dd2a991`** (merged R1/#1855).
The original checkout is preserved. Ancestry includes #1843 dependency-security and 3A–3J/#1844–1853;
retained source/lock versions were inspected. This is server implementation, not browser or deployment acceptance.
The owner's R2 instruction explicitly supersedes the earlier proposed assignment-only policy and unresolved
selected-slice decisions above; broader feature and live/deployment decisions remain open.

### Accepted owner decisions and wire contract

* Authenticated eligible users may read **all active Cases in their own tenant**. Assignment is a list
  filter only. No cross-tenant access, new ACL, numeric-role permission, or administrator bypass.
* Exactly `caseId`, `caseNumber`, `caseName`, `status`, `practiceArea`, `responsibleAttorney`,
  `primaryLegalAssistant`, `updatedAt`. ID is a positive SQL int (safe JavaScript integer); number nullable,
  name string (legacy null Case name resolves to empty, as the existing name-search normalization does).
  Status nullable `{id,name,color}`, Practice Area nullable `{id,name}`, assignment nullable
  `{userId,displayName}`. Optional unresolved relationships remain null without losing/multiplying Cases;
  present relationship names are required. Updated timestamp is nullable stored local date-time text,
  with no timezone or appended Z. No narratives, contacts, dates, tasks, history, documents or edit witnesses.
* Deleted Cases excluded. Missing/foreign/deleted/unavailable IDs share `404 Case unavailable.`.
  Invalid ID/page/query is 400; operation-wide authority denial is 403 and retains the session;
  confirmed authentication rejection is 401 with the existing explicit re-login/safe-return contract.
* Each successful Overview server read requires a committed authoritative audit before any data delivery.
  Audit append/commit failure returns safe 503 with no Case data. Each deliberate server reopen/refetch
  creates an event; no desktop two-second dedupe, browser callback, queue, fabricated entity READ or replay.
* Search and assigned **summary pages are explicitly exempt** from Case-read audit entries for this first
  slice. Opening Overview is the required audited read. This owner-selected exemption scopes summary
  discovery separately from authoritative Overview; it does not establish exemptions for other features.
* No sensitive field values or search text in operational logs/audit metadata. Preserve established audit
  representations/sanitization. Search text lives only in JSON request bodies, never request URLs.
* First-slice automatic refresh stays disabled. Confirmed session rejection requires explicit re-login;
  existing CredentialStore, startup uncertainty, generation guards and safe-return behavior remain.
  The accepted browser fetch **and body** budget is eight seconds; enforcement belongs to R3, no browser
  is changed here. Idle data is not continuously revalidated; no new storage/cache/refresh authority.
* Default/max page size 25; query at most 100 trimmed UTF-16 code units; selected JDBC statements have a
  five-second timeout. Exact UTF-8 JSON at most **128 KiB/page** and **8 KiB/Overview**. Oversized source
  fields or serialized responses fail clearly, with no partial disclosure or silent value truncation.
* Appearance remains provisional. Phase 2 acceptance **OPEN**, Phase 3 **IN PROGRESS**, Phase 4 incomplete.

| Additive operation | Concrete contract / authoritative path |
| --- | --- |
| `POST /api/v2/cases/search-page` | JSON `{query,page?,size?}`; defaults 0/25; literal Case-name substring only; blank returns empty without a Case browse. No GET/query-text parameter. |
| `GET /api/v2/cases/assigned-page?page=0&size=25` | Authenticated actor's any-role `CaseUsers` membership is SQL selection, before paging; no actor/tenant request selector. |
| `GET /api/v2/cases/{caseId}/overview` | Same tenant-wide active set; minimized one-Case read and required committed existing Overview audit. |
| Page wire | Exactly `{items,page,size,hasMore}`; no count/total. Items use the same eight-field allowlist as Overview. |

**Exact continuation:** pages are zero-based **0–100**, size **1–25**, with checked `offset=page*size`.
SQL fetches at most `size+1` (maximum **26**) already authorized/filtered rows, returns at most `size`,
and derives hasMore from that probe. At size 25 the maximum offset is **2,500**; page 100 can return
rows 2,501–2,525 and probe row 2,526. `hasMore=true` at page 100 truthfully signals more matching data,
not permission for page 101: R3 must disable Next and label the result-window ceiling, inviting a narrower
search. At smaller size the ceiling covers `101*size` rows. Keep size fixed during continuation; reset
page on new selection/search. Offset drift under concurrent insert/delete/rename/assignment/status changes
is explicit; no snapshot or count guarantee.

### Implementation and audit compatibility evidence (SOURCE / synthetic TEST)

`MinimizedCaseReadController` captures one verified principal and delegates additive CaseServicePort
operations through explicit CaseServiceAdapter/CaseGateway/DaoCaseGateway delegation to CaseSummaryDao.
Unsupported defaults throw actionable errors. Legacy routes, DTOs, limits, consumers and broad detail
remain unchanged; the new worker never invokes CaseDetailDto or child hydration.

On each borrowed connection, authority requires matching tenant **and PrincipalUserId** SQL context and
same-tenant Users with `is_deleted=0` and `IsRemoved=0`, matching durable-session eligibility. SQL explicitly
predicates the active Case root, tenant/global status/Practice Area joins and tenant-owned assignment/User
joins alongside RLS. Status uses the existing deterministic current-status worker. The existing semantic
compatibility assignment worker was extracted for reuse without changing legacy bindings or predicates;
new reads additionally predicate CaseUsers tenant and use the resolved same-tenant User ID. Responsible
Attorney / Legal Assistant keep RoleSemantics and primary/recent/ID tie ordering, not new permission rules.
Search applies tenant/active/name **before** Name ASC/ID ASC and OFFSET/FETCH. Assigned applies membership
**before** the established status-sort/authoritative-Intake DESC/Case ID DESC ordering. Only assigned
ordering computes Intake set-wise internally; no dates are exposed or separately fetched.

SQL CASE/DATALENGTH guards cap transfer of historical nvarchar(max) names without LEFT/SUBSTRING truncation:
name/display at most 255 UTF-16 units, number 200, status color source 20. Oversized sentinel rejects the
whole operation, including an oversized probe row. DTO construction validates required names/identities
and string bounds. Colors are canonical six-digit CSS hex or neutral null. The server serializes once,
checks the resulting UTF-8 byte array and delivers those **exact bytes**; escaped characters and multibyte
text count toward the cap. Success and selected safe errors use `Cache-Control: no-store`.

Overview owns one connection/transaction: authority → narrow read → existing
`AuditLogDao.appendPhiWriteAudit(Connection, …, timeout=5)` → commit → return DTO → checked HTTP bytes.
Representation remains **Case type 1**, **FieldCode 4**, **FieldName `Case.Overview.Read`**,
**StringValue `action=READ;screen=Case.Overview`**, null date/other values; ObjectId is the Case ID,
actor the verified user, tenant stamped from the authoritative connection. No new metadata or schema.
Append must affect exactly one row; append/commit failure attempts rollback and throws audit-unavailable
before returning a result. A failed commit acknowledgement can leave persistence uncertain; no data or
automatic replay is returned, and rollback attempts do not prove an already-committed row was undone. Read/not-found/authority/oversized-source failures emit no success event. A later
serialization limit, disconnect or lost HTTP response can follow a committed server read audit; that row
records server access, not proof the human viewed it. Never delete it or replay automatically.

Existing generic viewer recognizes `action=READ`; a focused viewer regression protects that classification.
The existing schema/vocabulary/connection overload can represent this event; inspection found no necessary
R2a allowlist/schema/viewer enhancement. The timeout overload is additive; legacy overload behavior is
preserved. Audit metadata inspection warning now emits only the exception class, preserving privacy.
SearchRequest formatting is redacted so Spring body-conversion DEBUG/TRACE cannot print the query;
selected error handling never logs/echoes source values, query bodies or exception text, including malformed JSON.
Generated OpenAPI has concrete closed read/page/relationship schemas, required/nullable fields, numeric and
string/page bounds, bearer security, body search and safe 400/401/403/404/503 contracts. It does not rewrite
legacy schemas.

Five seconds applies to selected authority, Case SELECT, audit tenant witness and INSERT statements.
Connection acquisition, JDBC metadata inspection and commit are additional server work; this is not a
five-second end-to-end server deadline. Row bounds do **not** bound scanned/sorted work. Browser abort
and its eight-second deadline do **not** prove SQL cancellation or audit rollback. Live collation,
representative query plans/timeouts, concurrency and failure behavior remain acceptance work.

### Test impact and local validation

Pre-edit inventory inspected CaseServiceAdapterTest, executing CaseSearchPagingJdbcTest,
CaseSummaryServerProjectionContractTest and neighboring shared projection contracts; ApiReadControllerTest,
OpenApiDocumentationTest, ApiExceptionHandlerTest; established PHI read/write helpers and viewer classification.
New synthetic JDBC coverage reaches the public production adapter, gateway and DAO. Its fixture has **35
unfiltered nonmatches before matching Cases 36–90**, then foreign/deleted matches; it proves the SQL filter
precedes ordering/paging, first/later/tied/final/empty pages, exact 26-row probe bindings, ceiling/escaping,
assignment versus tenant-wide search/direct read, mismatched/null authority, removed actor, isolation and
safe unavailable IDs. Transaction tests protect same-connection audit identity/metadata/timeout/commit and
failure rollback. HTTP tests protect minimal fields, authority/defaults/bounds, 403 versus 401, 404,
audit/timeout failures, exact byte limits (including JSON escaping), no partial payload and query/error privacy.
Defaults and legacy SQL/DTO/OpenAPI remain covered. Viewer READ classification and selector ownership updated.

Final local checks used the existing scratch Maven 3.9.11/JDK21 wrapper (`/workspace/.tools/bin/mvn`),
with `-Dmaven.compiler.parameters=true` for controller parameter reflection. Network-enabled shell execution
was needed for the existing dependencies; no repository toolchain, dependency or version change.

* Focused command below — **97 passed**, zero failures/errors/skips. Includes an actual Spring body-converter
  TRACE test proving search-request formatting is redacted, not merely a toString unit assertion.
* Change-aware selector against `fe029cb2...` and final HEAD — cases/contacts/organizations/reports/security-data/
  server/tasks, 19 classes across core/data/server/UI. Shared port consumers plus authoritative audit/RLS and
  directly modified/new tests explain these selected areas. Exact emitted affected command below — **110
  passed**, zero failures/errors/skips. Final selector command/class plan unchanged after documentation update.
* Required `mvn -Dmaven.compiler.parameters=true test` — **116 passed**, zero failures/errors/skips.
* `python3 -m unittest discover -s build/test-selection -p test_select_tests.py` — **24 passed**.
* Selector-recommended advisory `mvn -Dmaven.compiler.parameters=true -Pall-tests test` — **FAILED**:
  core111 passed; data813 tests/**12 failures**/zero errors/skips; UI/updater/desktop/server unexecuted.
  All12 failure **methods match R1 §10** and reproduced again on unchanged fetched `fe029cb2` in the
  51-test command below (12 failures, zero errors/skips). This is not a passing full suite. No unrelated fix.
* Additional diagnostic compatibility inspection ran `mvn -Dmaven.compiler.parameters=true -pl shale-server
  -am -Dtest=ApiExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` on the unchanged fetched
  base: four tests/**three failures**. Identical failures had appeared in an exploratory R2 focused run:
  `invalidRequestLogsDiagnosticAndKeepsClientResponseSanitized`,
  `illegalArgumentLogsDiagnosticAndKeepsClientResponseSanitized`,
  `sqlServerFailureLogsSafeMetadataAndCompletesWithoutReplacingOriginalFailure`. The first two expect the
  original throwable instead of current sanitization; the third sees no mocked SQL cause metadata under this
  toolchain. Existing handler/test code is untouched; this additional advisory result is not relabelled passing.
* Changed-production-to-test review, old-symbol/legacy-field search, both changed documents' relative links/
  balanced fences/whitespace, R1/3A–3J/security ancestry and retained lock versions, `git diff --check` and
  `git diff fe029cb2 HEAD --check` — PASS. Original checkout remains clean on `work`.

```bash
mvn -Dmaven.compiler.parameters=true -pl shale-server,shale-ui -am \
  -Dtest=MinimizedCaseReadJdbcTest,MinimizedCaseReadControllerTest,CaseServiceAdapterTest,CaseSearchPagingJdbcTest,CaseSummaryServerProjectionContractTest,ApiReadControllerTest,OpenApiDocumentationTest,AuditLogViewerEntityTypeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base fe029cb2d157a3e4f1f86130f3872d731dd2a991 --head HEAD --format markdown
mvn -Dmaven.compiler.parameters=true -pl shale-core,shale-data,shale-server,shale-ui -am -Dtest=com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.data.service.adapter.CaseServiceAdapterTest,com.shale.data.service.adapter.MinimizedCaseReadJdbcTest,com.shale.data.service.adapter.OrganizationServiceAdapterTest,com.shale.data.service.adapter.TaskServiceAdapterTest,com.shale.server.controller.AuthControllerTest,com.shale.server.controller.MinimizedCaseReadControllerTest,com.shale.server.controller.OpenApiDocumentationTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest,com.shale.ui.controller.AuditLogViewerEntityTypeTest,com.shale.ui.controller.ReportsControllerLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
# Unchanged fetched-base reproduction of the inherited advisory failures:
mvn -Dmaven.compiler.parameters=true -pl shale-data -am -Pall-tests \
  -Dtest=AdministrativeReadAuditMigrationContractTest,ApplicationInstanceHeartbeatMigrationContractTest,ApplicationReleaseImportContractTest,CaseDaoCasesGridQueryTest,CaseDateTypeLifecycleCutoverContractTest,CaseDatesFinalRuntimeCleanupContractTest,CaseOverviewConfigurationContractTest,CaseSummaryReportsContractTest,ContactPhase2BAuditMigrationContractTest,FormConfigurationFoundationTest,SessionInvalidationPhase8AContractTest,UserSessionMigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

All JDBC/MockMvc/source/viewer/OpenAPI results are **synthetic**. No authorized live application/SQL/RLS/
audit-failure/host/device/AT acceptance was performed. Known base advisory failures remain unfixed and are
not a reason to weaken auditing or claim live acceptance.

### Deployment prerequisites and rollback (no deployment performed)

1. Deploy only after separately authorized acceptance. Retain the security-patched 3A–3J/R1 base, durable
   WEB sessions/cutoff configuration and production/azure verified-bearer request-scoped runtime connection
   initialization. Development identity headers must remain disabled on the target.
2. Verify deployed existing schema and runtime grants: Cases/CaseStatuses/Statuses/PracticeAreas/Users,
   semantic RoleSemantics compatibility rows, tenant-owned CaseUsers (`2026-09-03_case_team_member_roles_phase2.sql`),
   Users.IsRemoved (`2026-08-03_users_management_completion.sql`), assigned ordering's CaseDates/types/semantic
   mappings, and AuditLog.ShaleClientId (`2026-04-15_auditlog_shaleclientid_tenant_scope_phase1.sql`). These are
   existing prerequisites, not new R2 migrations. Verify FieldName/FieldCode storage supports the exact
   existing event, ObjectTypes Case=1, INSERT grant and tenant-scoped audit visibility/viewer. The tenant-column
   migration alone does not prove deployed RLS or grants. If a deployed audit/schema protection gap is found,
   resolve it through a separately scoped prerequisite before enabling these reads; never weaken the audit.
3. With separately approved two-tenant fixtures and a **non-dbo runtime identity**, verify enabled RLS and
   explicit root/join isolation, active/removed actors, assigned/unassigned/closed/deleted Cases, pooled
   tenant/actor switching and missing/mismatched context. Inspect exact live predicates for Cases, Users,
   CaseUsers, selected strict/overlay lookups, ordering dates and audit visibility; dbo catalog output is
   not enforcement evidence. Exercise search beyond the unfiltered first page and representative scan/plans.
4. Prove persisted Overview event identity/representation and no data when authorized isolated INSERT/commit
   fault injection fails; verify timeout and byte-limit outcomes. No production grant alteration or fixture
   work is authorized by this PR. Do not infer success from synthetic JDBC doubles.
5. Verify actual HTTPS API/host, exact CORS, no-store end to end, current concrete OpenAPI and safe JSON errors;
   keep request bodies/search text out of tracing, access/error/APM logs and referrers. Do not enable payload
   logging to diagnose failures. R3/browser and live session/host/accessibility acceptance remain open.

Rollback removes/reverts only the additive R2 read routes/contracts/worker, associated tests/schema customizer
and documentation, restoring the retained R1/security base. Stop new consumers first. Retain legacy consumers,
durable-session/security patches and **all audit history**. No migration rollback is needed. Never disable
required audit, switch to the suppressed convenience overload, bypass authority/RLS, or delete audit rows as
rollback. Rollback to pre-durable-session server code still has the independent token-drain/secret-rotation
requirements in the deployment runbook.

### R3 browser-consumer prompt (implemented for review; see §12)

```text
Repository: gseshadow/Shale; PR base: codex/latest.
Task: R3 — consume the accepted minimized Case read contracts only.
Fetch live base and verify merged R2 and 3A–3J/security prerequisites. Read AGENTS, complete prompt rules,
Web V2 roadmap and first-read contract (R2 §11), follow routing; create a separate branch, preserve work.
Inspect neighboring browser/session/route tests before edits. Implement only an assigned/case-name-search
workspace and read-only Overview using POST /api/v2/cases/search-page, GET assigned-page and audited Overview.
Use narrow unknown-JSON validators for the exact schema, nullability, safe IDs, fields and UTF-8/page bounds.
Do not fetch broad detail, tasks, updates, history, contacts, dates, lookups or mutation witnesses for this path.
Keep query in bounded identity-scoped memory, never URLs/logs/persistent storage. Blank search gives instructions;
search is explicit submit and filters the entire tenant dataset server-side. Reset page on selection/search,
continue size-stably through page 100; if hasMore at that ceiling, disable Next and explain the result-window
limit. Preserve deterministic IDs, existing legacy route/consumer compatibility and deliberate Back/reopen.
Reuse shell/primitives, route registry and sessionRequests generation/rejection owners; compose narrow route/
query cancellation with an eight-second total fetch AND body deadline. Reject stale success and failure;
clear old entity before new ID, clear/abort memory on logout/rejection/identity replacement. No automatic
retry/refetch/focus refresh/replay. Opening/refetching Overview invokes the server's required audit; never
append audit from browser callbacks. Summary pages are explicitly audit-exempt. Distinguish accepted empty,
400, operation 403 retaining session, safe 404, audit/read unavailable, oversized/malformed and deadline outcomes.
Confirmed session rejection uses explicit re-login and existing safe returns; automatic refresh remains off.
Preserve CredentialStore/startup uncertainty/login/storage/logout contracts. Appearance provisional; reuse
semantic/accessibility controls, responsive layouts/focus/announcements. No new dependencies, broad transport/
cache rewrite, native/MCP/AI, mutations, refresh implementation, deployment or version changes.
Maintain affected tests, run focused/full web tests/typecheck/build and synthetic browser checks plus selector/
relevant/critical checks under prompt rules and diff checks. Carry forward known base failures honestly.
Update evidence/prerequisites/rollback; Phase 2 OPEN, Phase 3 IN PROGRESS, Phase 4 incomplete until separately
accepted. Commit/push/open PR targeting codex/latest; do not merge/deploy. Live SQL/session/host/device/AT
acceptance requires separate authorization; report gaps, never infer it from mocks.
```

## 12. R3 browser consumer implementation record (2026-10-11)

R3 implements the previously ready-to-run browser prompt on fetched #1856 base
`862b8671da698da5469e8d28220bca04bad98944`, verifying R1/R2, 3A–3J and retained security ancestry.
The accepted R2 §11 schema/tenant/audit/bounds contract remains authoritative and unchanged.
[Launch/review, exact validation, synthetic evidence, rollback and deployment prerequisites](../../shale-web/docs/r3-review.md)
record the implementation. [Roadmap §11.20](shale-web-v2-architecture-roadmap.md#1120-r3--bounded-case-browser-path-implemented-for-review-2026-10-11)
records phase status and the next bounded R4 acceptance.

The explicit Case workspace routes preserve legacy editors, use only the three R2 reads and keep server-side
tenant-wide submitted search independent of Assigned. Validated eight-field Overview is always obtained through
its required audited endpoint. Body-byte limits, exact unknown-JSON schemas, SQL IDs/local calendar timestamps,
page0–100/size25/hasMore checks, identity-scoped bounded query/page memory, one eight-second fetch/body budget,
caller plus session cancellation and stale success/failure guards precede rendering. No excluded reads, browser
audit rows, detail fallback, automatic refresh/refetch/retry/replay or new persistence/dependency/schema changes.
Confirmed401 reuses explicit login/safe returns;403 preserves session; failure feedback is sanitized and distinct.

Executed tests/browser evidence are synthetic, with inherited advisory failures attributed to R2 rather than
claimed fixed or passing. Phase 2 acceptance OPEN, Phase 3 IN PROGRESS, Phase 4 first read slice INCOMPLETE
until separate acceptance. Appearance provisional. Live SQL/RLS/performance, persisted audit/session, host,
physical devices and real assistive-technology speech remain unverified. No merge or deployment authorized here.

# AI Integration, REST/OpenAPI, and MCP Architecture and Roadmap

**Status:** Phase 0 assessment COMPLETE; broader architecture/contract review IN PROGRESS; selected Case REST/OpenAPI foundation COMPLETE; Phase 1R merged with synthetic local validation; Phase 1S shared minimized Case boundary COMPLETE (source/synthetic proof); authoritative Case SQL BLOCKED; Phase 3A security contract COMPLETE; Phase 3B implementation IN PROGRESS; Phase 3B.1 COMPLETE (source/synthetic proof); MCP inactive.

**Last reviewed:** 2026-10-21

**Repository baseline:** Phase 3A inspected live `origin/codex/latest`, commit
`216cf082f0223460217c8cf24199df946fbb87e0`, the merge of
[#1900 / selected Phase 2 REST](https://github.com/gseshadow/Shale/pull/1900), following merged
[#1899 / Phase 1S](https://github.com/gseshadow/Shale/pull/1899) and
[#1898 / Phase 1R](https://github.com/gseshadow/Shale/pull/1898).
This is repository inspection, not deployed SQL, API configuration, or runtime security acceptance.
Phase 3A's normative proposed implementation contract is [section 10](#10-phase-3a--mcp-security-delegation-integration-and-audit-contract).
It supersedes earlier provisional AI choices below; historical execution records retain their scope.

## 1. Purpose, scope, and document ownership

This document is the authoritative roadmap for provider-neutral AI integration and the shared secured
application operations used by REST/OpenAPI and future MCP. It records recommendations for review; it does
not authorize deployment or claim that proposed contracts or controls exist. Phase 1R adds a narrowly scoped
local SQL candidate and synthetic acceptance tooling; it does not activate MCP or authorize deployment.

This existing document already owns the combined external REST/MCP contract, delegated integration
authority, and AI access audit model. Update it rather than creating another roadmap. The web roadmap
owns client delivery and the release/session roadmap owns authentication lifecycle; reuse those authorities
rather than copying their roadmaps. Subsequent tasks must update this tracker and the affected owning document.

**Web delivery is independent of AI integration activation.** The web rebuild uses ordinary authenticated
REST/OpenAPI and shared Shale operations. It does not depend on completing MCP, AI OAuth, integration
registration, delegation grants, or the AI-specific audit extension. Shared operation security and audit
requirements still apply; section 3.1 separates these from additional AI gates.

Reviewed evidence and continuing authorities:

* [Repository rules](../../architecture/codex-prompt-rules.md), [system overview](../../architecture/system-overview.md),
  [database schema](../../architecture/database-schema.md), and [tenancy/RLS](../../architecture/tenancy-and-rls.md).
* [Shared logic inventory](../web-api-migration-step-2.md), [server migration](../web-api-step-3.md),
  [API readiness and OpenAPI contracts](../web-api-azure-readiness.md), [local smoke tests](../web-api-local-smoke-test.md),
  [App Service deployment](../azure-app-service-deployment.md), [web deployment](../shale-web-deployment.md),
  and [web client README](../../shale-web/README.md).
* [Web V2 roadmap](shale-web-v2-architecture-roadmap.md), especially R1–R4 and its remaining acceptance
  gates; [first-read contract](shale-web-v2-first-read-contract.md), especially the accepted R2 decisions
  in section 11; [R4 acceptance ledger](../../shale-web/docs/r4-desktop-acceptance.md); and
  [isolated SQL verification runbook](../testing/case-read-local-security-verification.md).
* [Release/session roadmap](application-release-session-management.md), especially the later Phase 7B/7C,
  Phase 8A/8B implementation records and remembered-sign-in closeout. Earlier inventory sections describe
  historical states and must not be interpreted as today's exclusively in-memory revocation design.
* [Auth permission cutover](../testing/auth-module-permission-cutover.md), its private-evidence scope and
  remaining-acceptance references; [UTC verification](../testing/remembered-session-utc-results.md),
  [retirement transport correction](../sql/auth/permission-cutover/transport-output.md),
  [release publication](../release-publishing.md), release 1.0.137 source notes/manifest and
  [Case Updates deployment requirements](../testing/case-updates-workspace.md). GitHub merged metadata
  for #1868/#1871/#1889/#1892 and recent merged PR descriptions/discussions were read; no later exact
  production SQL/API/artifact closeout was found there. The October 20 production report comes from
  the operator's task statement. Lack of a PR comment or failure report is not acceptance evidence.
* [Firm-wide roles](../../architecture/firm-wide-roles.md), [Case Team roles](../../architecture/case-team-roles.md),
  [Contacts](../../architecture/contact-management.md), [Organizations](organization-management.md),
  [Case Materials](../../architecture/case-materials.md), [summary/document projection inventory](../../architecture/case-summary-projection-inventory.md),
  [universal search](universal-search-suggestions.md), [historical RLS audit](../tenant-rls-audit-2026-06-29.md),
  and [live updates](../../architecture/live-update-architecture.md).

## 2. Current-state assessment

### 2.1 Existing reusable boundaries and strengths

| Area | Verified repository components | Reuse and limits |
| --- | --- | --- |
| Shared contracts | `shale-core`, `com.shale.core.service`: `CaseServicePort`, `TaskServicePort`, `ContactServicePort`, `OrganizationServicePort`, `AuthServicePort`, `UserServicePort`; shared DTOs and `DbSessionProvider` | Existing JavaFX-free application entry points; no new service module is justified merely to add MCP. A port interface alone does not prove complete policy enforcement. |
| Implementations | `shale-data`, `com.shale.data.service.adapter.*`, backed by `CaseDao`, `CaseSummaryDao`, `TaskDao`, `ContactDao`, Organization aggregate DAOs | Many adapters are thin delegates. Important validation, lifecycle, concurrency, relationship, and transaction rules live in DAOs/aggregate workers. Keep these authoritative workers. |
| Server composition | `shale-server`, `ShaleServerServiceConfiguration` | Spring Boot composes core/data services without `shale-ui` or `shale-desktop`. This is the natural initial host for both external adapters. Separate deployment is not currently required. |
| REST | `MinimizedCaseReadController`, `ApiReadController` plus auth, session, release, instance, admin, and contact-value-validation controllers | Minimized actor-aware Case search/assigned/Overview reads now exist alongside broad legacy reads and writes. Despite its name, `ApiReadController` is not read-only. Reuse operations; do not expose a controller wholesale to MCP. |
| OpenAPI | `OpenApiConfiguration`, `MinimizedCaseOpenApiConfiguration`, Springdoc dependency in `shale-server/pom.xml`, `OpenApiDocumentationTest` | Generated `/v3/api-docs`, `/swagger-ui/index.html`, and `bearerAuth` exist. Minimized Case schemas have concrete required/nullable fields and bounds. The historical `v1` document label is distinct from the selected v2 URL compatibility policy (§9.4); broader API review remains pending. |
| Tenant context | `ServerSessionResolver`, `ServerRuntimeSessionState`, `RequestScopedDbSessionProvider`, `RuntimeSessionServiceConnectionProvider`, data `RuntimeSessionService` | Authenticated principal is resolved before business access; connections set `ShaleClientId` and `PrincipalUserId`. DAOs also use tenant equality and lifecycle predicates. |
| Sessions | `ShaleAuthTokenService`, `ServerAuthSessionService`, `DurableSessionTokenValidator`, `SqlDurableSessionStore`, `UserSessionServicePort`/adapter/DAO | Bound JWT `sid`/`jti` is checked against SQL expiry, current JTI, ownership, and revocation; refresh rotates conditionally. Reuse durable lifecycle patterns, not an in-memory-only replacement. |
| Audit | `PhiFieldRegistry`, data `PhiAuditService`/`AuditLogDao`, `EntityActionAuditEvent`/DAO, `AdministrativeReadAuditEvent`/DAO, server `SessionManagementService` | Existing tenant-owned PHI, mutation, bounded administrative-read, and session-security mechanisms. Their vocabularies are constrained and not interchangeable. |

### 2.2 Where business logic and policy live

There is already a reusable service/application boundary, but it is uneven. `CaseServiceAdapter.searchCases`
uses `CaseSummaryDao.searchActiveForServer` and maps authoritative summaries. `getAuthoritativeCaseDetail`
combines `CaseDao.getDetail` with actor/tenant-aware migrated Case Date state. Case Dates must retain their
existing semantic mappings and occurrence authority; MCP must not reconstruct them from retired Case scalars.

Contact and Organization aggregate workers own structured contact methods, classifications, optimistic
concurrency, compatibility projections, and same-connection audits. The Organization roadmap's later Phase 3F.2
record supersedes its early inventory: legacy `OrganizationDao.create/update` now delegate to aggregate mutation
ownership. Do not propose rewriting that already completed boundary or making legacy scalar fields a second
storage authority. Server scalar response contracts remain compatibility projections.

Some orchestration still lives in `shale-ui.services` and JavaFX controllers. `CaseDetailService` uses
`AppState` admin/attorney flags for deleted-case actions. `CaseTaskService` and task callers orchestrate UI/live
side effects; `TaskServiceAdapter` delegates task creation and assignment separately and does not itself prove
one transaction for the whole use case. Extraction should address only a selected operation's policy or
transaction gap, retaining DAO transaction owners and post-commit invalidations. Never import JavaFX services,
`AppState`, or desktop bridge identity into server/MCP execution.

### 2.3 Security and audit gaps

The accepted R2 web contract is an important scoped exception to the older gaps below. Eligible users may
search/read all active Cases in their own tenant through the eight-field minimized projection; assignment
filters only the Assigned list. `CaseServicePort.searchMinimizedCases`, `listMinimizedAssignedCases`, and
`readMinimizedCaseOverview` delegate through `CaseServiceAdapter`/`DaoCaseGateway` to `CaseSummaryDao`.
That worker checks tenant/actor against SQL session context and the current non-deleted/non-removed user,
applies tenant/deletion predicates before paging, and uses tenant-qualified relationship joins. No new
Case ACL or administrator bypass is part of this slice. Broader field/child/document access still needs its own review; the pilot AI grant is specified in §10.2; do not reopen the accepted ordinary web policy as an AI prerequisite.

Overview uses one connection/transaction for authority, narrow read, existing `Case.Overview.Read` PHI
audit, and commit before returning the DTO. Audit append/commit failure withholds data. Web search/assigned
summary pages have an explicit owner-approved audit exemption for this slice only. These are reusable
boundaries, not proof of complete AI attribution or permission for wider payloads. Source and recorded
synthetic tests establish implementation; the R4 ledger records bounded user-reported desktop/hosted/iPhone
acceptance. Two-tenant/non-dbo/pooled-context, audit-failure/commit uncertainty, deployed protection and
performance gates remain open. Phase 1R is merged and its three-FILTER candidate passed local synthetic SQL
validation. Authoritative Case SQL acceptance remains BLOCKED: the approved offline export is unavailable
in this workspace, so derived/candidate artifacts and their acceptance remain pending. Phase 1S proves the
shared source boundary separately (§9.3); it does not rerun or activate Phase 1R.

* RLS coverage is not uniformly established. The Contacts roadmap explicitly records that the parent
  `dbo.Contacts` has no TenantFilter predicate, while newer Contact child tables use strict RLS; the
  generic tenancy list must not override this specific finding. The historical RLS audit also identifies
  live-catalog verification gaps. Preserve explicit Contact tenant predicates and inspect deployed
  predicates/grants with a non-dbo principal before any integration activation. Any remediation is
  separate scoped work; no RLS addition or weakening is performed here.
* Tenant isolation is a substantial reusable strength, but SQL RLS is tenant isolation, not proof of
  acting-user permission, case confidentiality, or document entitlement. For example,
  `TaskServicePort.listCaseTasks`, `CaseServicePort.listCaseUpdates`, and basic Contact/Organization
  search/detail signatures take tenant identity without an explicit actor. Session context can still
  provide the actor; each chosen path must prove that policy is actually checked.
* Actor parameters in Case operations and active-user checks do not establish a general per-case ACL.
  Case Team membership/role assignment is not automatically permission to read a matter. Firm-wide
  roles are separate eligibility identities; built-in ADMIN/ATTORNEY authority remains `Users.is_admin`
  and `Users.is_attorney`. Do not infer access from labels or make every AI caller an administrator.
* There is no inspected tenant-owned AI registration, delegation grant, integration scope, or client-bound
  token model. `ServerPrincipal` currently identifies user/tenant/email, not an AI integration. Existing
  desktop remembered credentials and `DESKTOP` sessions are not an integration registration mechanism.
* `shale-ui.services.PhiReadAuditService` derives actor from `AppState`, deduplicates briefly, and catches
  audit failures. `CaseController` and task detail callers invoke it on view intent. HTTP and future MCP
  calls cannot rely on that UI seam. The minimized Overview now has an authoritative fail-closed seam;
  other selected reads still need operation-specific audit review and any necessary shared boundary.
* `EntityActionAuditEvent` already has source/correlation/parent context, but no general READ/FAILED
  action or integration/tool metadata keys. `AdministrativeReadAuditEvent.ReadType` currently models
  only application-instance list/distribution reads. `SessionSecurityAuditLog` models session events,
  not general AI data reads. A full AI access event cannot simply be inserted under current allowlists.
* New bound tokens have durable revocation. The bounded `LegacyTokenCompatibilityPolicy` and
  `InMemoryTokenRevocationStore` remain compatibility paths; neither should be accepted for new AI
  grants. Account disablement and permission changes need authoritative revalidation, not trust in
  stale token role flags or desktop state.

### 2.4 Document/file architecture and client coupling

Shale has `ExternalLinks`, `CaseLinks`, and `CaseLinkShares`, plus Material Request/Item metadata and
`MaterialItemServicePort`/`MaterialRequestServicePort`. `CaseServicePort.listCaseLinks` provides existing
link metadata. These are not a Shale blob store or a content-fetch authorization boundary. A share row records
Shale's knowledge of sharing; it does not verify permission in Box, Clio, or another external provider.

The desktop Documents surface generates a `CASE_SUMMARY` HTML/PDF through UI `CaseDocumentService`, renderers,
and export/open flows. `CaseSummaryDao.findActiveForDocuments` is a reusable bounded summary query, but the
composition and workstation file handling remain UI-specific. No inspected server controller provides
case-document content search/download, provider authorization, or a stable secured document identifier contract.
Do not reinterpret generated summaries, Material Items, or arbitrary URLs as authorized document bytes.

`shale-web/src/api.ts` remains a compatibility consumer of bearer APIs; the newer
`shale-web/src/features/cases` client/workspace uses the minimized Case contract with runtime response
validation. `CredentialStore` owns the existing per-tab `sessionStorage` boundary; first-slice refresh is
disabled and confirmed rejection requires explicit re-login. These are client choices, not AI credentials.
The server has safe errors via `ApiExceptionHandler`/`ApiErrorResponse`, validation via `ApiValidation`,
and configured origin CORS. R1 removed prefix slicing from legacy Case search-page through exact SQL
offset/size delegation (`total=null` remains); Contact search-page still slices a fetched prefix and its
adapter can limit after DAO retrieval. R2 adds SQL size+1 paging, page/size limits, statement timeouts and
checked serialized byte budgets. Row limits/timeouts do not establish bounded scanned work or an
end-to-end deadline. Broad legacy responses still expose UI-oriented fields or concurrency bytes, and
`getCase` returns Java `Object`. Harden those contracts per use case; retain the newer proven source seams.

### 2.5 Authentication, permissions and delivery reconciliation — 2026-10-21

This is the current cross-workstream status; dated implementation/rehearsal records retain their
original scope. Phase 1R's [local candidate record](../testing/case-read-local-security-verification.md#phase-1r-candidate--2026-10-21)
supplements this ledger with synthetic results; authoritative Case SQL and all deployment gates remain open.
**Implemented** means present in inspected source; **merged** means GitHub records the
change in the base; **operator-reported deployed** attributes an operational report; **verified** must
name the environment and check. None of these alone means complete security or operational acceptance.

| Workstream | Implemented / merged evidence | Deployment / verification boundary and remaining work |
| --- | --- | --- |
| Legacy login-procedure retirement | Guarded exact ACL delta and rollback in #1874/#1880/#1881; runner corrections through [#1892](https://github.com/gseshadow/Shale/pull/1892), merged October 20. | **Operator-reported production success, October 20:** removed `shale_app` EXECUTE on `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure`, `dbo.sp_login_mark_success`; denied all three to `shale_runtime` and `shale_runtime_role`. The operator then reported API login/identity/refresh/logout and installed-desktop automatic sign-in/user-detail checks successful. This report is supplied in the reconciliation task, not independently rerun or a reviewed private packet. Retirement is no longer an unperformed next task. |
| Bounded pre-tenant authentication | [#1868](https://github.com/gseshadow/Shale/pull/1868) adds `LookupAuthenticationCredentialV1` and `ResolveDesktopRememberCredentialV2`; current shared password and server remembered callers use them. [#1871](https://github.com/gseshadow/Shale/pull/1871) adds separate Stage A installation/module fences and Stage B table/hash read fences. | Auth SQL scenarios have recorded Linux/disposable and operator-reported native Windows passes. The three legacy-procedure retirement does **not** prove Stage A or Stage B applied. Exact current production module hashes/owners, effective auth/runtime identity mapping, fence roles/grants and compatible fleet/job drain remain unconfirmed in available records. No new direct-SELECT fallback exists in current password code. |
| Password and API sessions | `AuthServiceImpl` calls bounded V1 and verifies BCrypt in Java. `AuthController` implements login, `/me`, refresh and logout; `ServerAuthSessionService` binds new WEB/DESKTOP JWTs to SQL `sid`/current `jti`, conditional refresh and durable revocation. | October 20 operator-reported API checks supplement earlier acceptance. They do not certify every instance, negative authorization path or legacy-token drain. Existing bounded legacy JWT compatibility is not authority for AI credentials. |
| Remembered desktop and session management | Existing DPAPI-protected opaque remembered credential, SQL hash/installation binding, serializable credential/JTI rotation, fixed 30-day remembered deadline, self/admin sessions and revocation. [#1889](https://github.com/gseshadow/Shale/pull/1889) preserves current V2 lookup and corrects remaining UTC JDBC session persistence/projections; pinned recovery compatibility stays separate. | October 6 installed-Windows Stay logged in and October 2 session-management reports remain accepted at their original scopes. October 20 installed automatic sign-in/user-detail checks add production observations, not every DPAPI/outage/account-disable/security case. Ordinary close preserves remembered sign-in; explicit logout clears local state and attempts server revocation. No offline authority is established. |
| Desktop / API / SQL delivery | Release source `9330133a` and manifest identify 1.0.137. #1893–#1895 add desktop Case Updates work and explicitly require existing schema/audit support, with no new API deployment/migration. | A release commit/manifest is evidence of repository release metadata, not installed fleet, active API JAR or SQL state. Subsequent desktop/API/SQL deployment work is separate from retirement; available runbooks, merged PR bodies/discussions and release notes do not establish its exact production sequence/artifact/ACL state. Follow the [release dependency matrix](../azure-app-service-deployment.md#release-dependencies-and-order--2026-10-21). |
| Web V2 / phone delivery | R1/R2 minimized Case APIs and R3 browser workspace, themes, routing and session work are merged. R4 records hosted publication and bounded physical iPhone/Firefox use. | Preserve [R4](../../shale-web/docs/r4-desktop-acceptance.md): hosted browser use on a phone is accepted only for its reported flows; it is not an iOS/Android app. Web Phases 2/3 remain in progress and Phase 4 incomplete. Native/PWA decisions, secure storage, packaged assets, signing/store delivery and wider device/AT/security acceptance remain pending in the web roadmap. |
| AI / MCP | Existing ports, minimized Case operations and OpenAPI are reusable. No inspected MCP adapter, delegated integration identity/grant or complete AI access-audit extension exists. | **NOT STARTED / activation blocked.** No MCP deployment follows from auth retirement, installed desktop acceptance, web publication or release 1.0.137. Start with the two Case tools only after shared-read and AI controls pass. |

**Evidence scopes retained:** the October 15 native Windows auth-SQL report covers its three named
scenarios; Linux/Core SQLCMD rehearsals cover their disposable fixtures; source/overlay recovery and
the pinned 1.0.136-compatible sandbox are not installed production artifacts. #1889's current-source
SQL/JDBC/Tomcat UTC lifecycle checks are local, with synthetic credential verification, not Windows
DPAPI or full production-profile acceptance. The known historical account-revocation source assertion
and other advisory failures remain documented, not retroactively green. Private packets/captures were
not read here. No password, token, production rows or wholesale packet upload is needed for this ledger.

**Retirement scope is exact.** It revokes three auth EXECUTEs and adds runtime denials; it does not drop
procedures, remove desktop JDBC, revoke every direct table/column capability, install the new auth
modules/fences, attach RLS, prove complete least privilege, or finish a monitoring window. Absence of a
reported failure is not monitoring evidence. Monitoring/observation duration, review/sign-off and actual
post-change catalog/artifact parity remain unconfirmed; retain them as uncertainties, not a request
to repeat completed rehearsals or retirement.

**Security debt versus activation blockers:**

* The last recorded authoritative catalog has no FILTER on `dbo.Users`, `dbo.auditLog` or `dbo.Statuses`.
  Phase 1R (#1898) merged a three-FILTER candidate, locally validated only with synthetic SQL; no
  production activation report closes that gap. Strict Users/audit and
  tenant-or-global Statuses read isolation, non-dbo A/B/NULL and pooled-context evidence are selected
  Case-read security prerequisites. Explicit minimized-query predicates remain valuable; the gap is
  not a demonstrated API leak. The older claim that the offline schema was never supplied is obsolete:
  the supplied export confirmed the gap; it is unavailable in this workspace, so authoritative
  derived/candidate preparation and execution remain BLOCKED.
* Last reported runtime authority included Users password-hash SELECT/UPDATE and audit SELECT/INSERT/
  UPDATE/DELETE. Stage B restricts hash **SELECT**, not password-management writes or audit CRUD.
  Auth base-table restrictions, broader runtime grants, append-only audit enforcement, authoritative
  admin-only audit reads and audit-list SQL/value logging need their own compatible remediation.
  FILTERs alone do not block foreign INSERT/ownership changes or make global Statuses immutable.
  Do not call the repair complete least privilege or repeat broad June RLS SQL.
* Shared desktop SQL credentials and client-initialized context remain capabilities outside HTTP token
  enforcement. Desktop revocation relies on corrected-client polling/locking (push accelerates it);
  it is not database credential revocation. A full JDBC-to-API migration is separate work, not a
  prerequisite for every ordinary web feature or for preparing a tightly scoped MCP candidate.
* Before MCP activation, prove the selected operation's live user/tenant/entity/field policy, bounded
  results and fail-closed required audit, then integration opt-in/registration, approved issuer/transport,
  audience/scope/grant intersection, durable revoke/expiry, AI read/failure attribution and extraction
  limits. Unrelated desktop UI parity or all five proposed tools need not finish first. Broad SQL
  exposure of selected data/audits must be resolved or explicitly dispositioned by security owners;
  a narrow FILTER pass is not permission to activate.

## 3. Target architecture

```text
 Web / Mobile / Conventional clients        Compatible hosted or local AI clients
                |                                        |
       REST / OpenAPI adapter                       MCP adapter
                |                                        |
                +--------------------+-------------------+
                                     |
                    Shared Shale application operations
          verified context: tenant + acting user + optional integration/grant
         authorization / validation / business rules / read or mutation audit
                                     |
                   Existing core ports + data adapters / workers
                                     |
                      DbSessionProvider / RuntimeSessionService
                 ShaleClientId + PrincipalUserId / explicit predicates
                                     |
                       Azure SQL / existing tenant RLS

 Future document operation -> authorized storage/provider access (when designed)
                            -> same case/document policy and audit boundary
```

Arrows flow inward: both adapters translate protocol requests to the same authoritative operations.
Today's JavaFX desktop also reaches core/data through direct auth/runtime JDBC; it separately uses
HTTP for durable enrollment, remembered restore and session management. Web and future phone clients
use the server's REST contracts and receive no SQL credentials. Browser `sessionStorage`/explicit
re-login is the present web choice; desktop DPAPI is not a phone credential-storage contract. A future
native client needs its own reviewed protected storage/lifecycle and packaged delivery. An AI client
must enter through a delegated integration boundary with tenant opt-in, narrower grants and audit;
it cannot borrow desktop SQL credentials, reuse the desktop remembered secret or assert tenant/user.

Neither adapter executes raw SQL, calls low-level DAOs to evade a service policy, or calls another HTTP
controller as its business layer. A shared operation may reuse an existing port unchanged when verification
proves its requirements, extend a port with actor-aware policy, or add a small non-UI orchestration facade
in the existing core/data structure. Do not mandate a new module, database, queue, vector store, model host,
or retrieval subsystem. MCP resources/prompts are outside the first surface and must obey the same policy
if later introduced.

Recommend initially hosting MCP alongside REST in `shale-server`, with independent routing, protocol
schemas, and discovery. Async execution must carry immutable verified context explicitly, revalidate on
execution, and borrow a newly initialized scoped connection; servlet/thread-local identity must not leak
between requests. Never use the privileged auth pool or `GlobalControlPlaneDbSessionProvider` for tenant
business reads. Do not create a shared mutable tenant session for concurrent clients.

The tenancy document illustrates read-only SQL session context; current `RuntimeSessionService` sets both
keys on every borrow without `@read_only=1`. Preserve and test that actual pooling/setup behavior. Changing
SQL context mutability is separate scoped work requiring pool compatibility evidence, not an MCP prerequisite
that silently alters existing runtime behavior. Initialization failure must close the connection and fail.

### 3.1 Shared operations and independent client readiness

Shared application services own tenant isolation, current user/entity/field authorization, validation,
business rules, concurrency, and required audit semantics. Protocol adapters resolve and validate their
own credentials, supply verified context, and translate contracts. Integration restrictions augment that
context only for registered integration requests; the shared operation must not require an AI registration
or delegation grant for an ordinary authenticated web user. Absence of integration context is valid only
for the ordinary user-session path, never an AI credential fallback that evades integration controls.

| Concern | Ordinary authenticated web/mobile REST | AI integration through MCP or an approved REST integration contract |
| --- | --- | --- |
| Identity/session | Existing Shale bearer login/me/refresh/logout and durable WEB-session validation; server-derived tenant/user | Separate approved AI client identity, OAuth/audience validation, live delegation and tenant grant |
| Shared operation | Current user, tenant, case/field policy, validation, bounds, business rules, concurrency and required audits | The same operation and policies, further restricted by integration scope, tenant opt-in and AI controls |
| Audit attribution | Actual user/tenant, operation, entity/parent context and required PHI/entity/session audit; no fabricated integration or tool identity | Additional verified integration/tool/grant attribution and approved AI access/failure representation |
| Reads and writes | Authorized web reads and mutations under existing business and transaction rules | Initially read-only; later AI mutations require separate controlled-write gates |
| Delivery dependency | Relevant REST/service contract and security verification for each web use case | Selected shared operations plus AI authentication, registration, revocation, audit and operational acceptance |

Phases 1–2 describe reusable service/REST work that can proceed and ship per operation while AI decisions
remain open. They do not require completion of the entire Phase 0 AI contract review or Phases 3–7. Review
any unresolved tenant, user, case/field or audit issue affecting the actual web operation before exposing
that operation; unrelated AI issuer, grant or tool decisions do not block it. Preserve the documented RLS
coverage review and explicit predicates; this separation does not waive any ordinary web security control.

## 4. Architectural decisions and security model

### 4.1 Recommended decisions

| Decision | Recommendation |
| --- | --- |
| Provider neutrality | Use standard MCP contracts/authentication for compatible clients. No model/vendor SDK in domain operations; Curtis & Co.'s local AI is one ordinary registered consumer. |
| Independent REST | Retain conventional resource/application-oriented REST and generated OpenAPI. The authenticated web rebuild can ship without MCP or AI OAuth; MCP has its own semantic tool catalog and activation gates. |
| Shared services | Strengthen existing ports/adapters only where inspection proves gaps. Tenant, user policy, business validation, and audit must be authoritative below both adapters. |
| Database boundary | No AI SQL, database credentials, connection strings, table browsers, arbitrary query tool, or privileged data path. Internal schema is not the external contract. |
| Read-only first | For AI access, explicitly allowlist approved query operations. Read-only means no domain writes or side effects; required audit/session bookkeeping remains permissible. HTTP POST used by MCP is not itself a domain mutation. |
| Stable semantics | Return minimized Case/Task/Contact/Organization concepts with stable IDs, typed fields, documented nulls/time semantics, bounds, and errors. Do not expose raw rows, SQL, credentials, or unnecessary RowVer data. |
| Delegation | Normal AI access is a registered integration acting for an authenticated Shale user in one tenant, under explicit revocable grants. Unattended service-user access is deferred for separate policy review. |
| Auditing | AI-originated reads through MCP or approved REST integration contracts must be identifiable by tenant, actor, integration, operation, correlation, outcome, and entity context through existing audit architecture or a scoped compatible enhancement. |
| Activation gate | Tenant opt-in, registration, revocation, read scopes, quotas, and approved audit persistence are mandatory before any real-data dogfooding. An admin UI can follow. |

### 4.2 Separate AI identities and intersect authority

| Boundary | Meaning and authority |
| --- | --- |
| AI integration/client identity | Registered client identity representing an application/connector, not a model's self-reported name, a human account, or a tenant. Registration binds the installation/grants to approved tenant use. Public client IDs are identifiers, not secrets. |
| Authenticated Shale user | Human delegate whose active same-tenant account and current permissions authorize the operation. The model cannot choose or impersonate the user through tool arguments. |
| Tenant | Server-verified `ShaleClientId`, derived from authenticated ownership and the integration's tenant grant; never a free tenant selector in tool/REST input. |
| Integration permissions | Tenant/admin-approved maximum operation scopes, optional case restrictions, field restrictions, quotas, and enablement. Tenant opt-in alone grants no data access. |
| User permissions | Current Shale operation, case/matter, sensitive-field, and document permissions, evaluated independently of integration scopes. Consent cannot grant what the user does not possess. |

```text
AI effective authority = tenant policy AND enabled integration AND live delegation/session
                      AND integration scopes/restrictions AND current user permissions
                      AND case/document/field policy AND read-only phase restriction
```

For ordinary web requests, effective authority is the live authenticated user session intersected with
tenant, current user, case/document/field and operation policy. Integration enablement, scopes and AI phase
restrictions apply only when integration authority is present. Authorized web mutations remain governed by
their existing command, concurrency and audit contracts rather than the AI read-only rollout.

Any denial wins. A valid token, known case ID, Case Team membership, discovery listing, prompt instruction,
or caller-supplied role cannot override a boundary. Apply restrictions in searches before pagination/counts
and before detail/child reads, not by loading forbidden rows and trimming the response. Recheck parent-case
access for every task, timeline, material, or document operation; a child ID cannot bypass parent policy.
Contact/Organization directory access requires its own policy and must not reveal restricted case relationships.
Deny by default when the needed policy is unresolved; initially omit deleted-case and administrative surfaces.

### 4.3 AI authentication, lifecycle, and controls

Phase 3A settles the pilot profile in [§10.3](#103-oauth-profile-and-credential-boundary):
Shale-controlled OAuth authorization code + S256 PKCE, separately issued opaque MCP access tokens,
manual tenant registration and explicit user consent, live SQL grant/credential validation, and
HTTPS Streamable HTTP in `shale-server`. Existing WEB/DESKTOP JWTs, remembered credentials,
development headers and SQL credentials are never accepted by MCP. No OAuth or MCP implementation
exists yet. [§10.7](#107-bounds-and-extraction-policy) supplies numerical pilot limits;
[§10.5](#105-audit-representation-and-disclosure-transaction) supplies mandatory audit semantics.
Curtis & Co.'s local client uses this same contract.

### 4.4 Decisions still requiring evidence

| Remaining gate | Exact boundary |
| --- | --- |
| Broader resource/field policy | Pilot uses only the already accepted tenant-wide minimized active Case policy, explicitly consented to (§10.2). Wider fields, child data, directories, deleted Cases, per-case grants and unattended identities remain excluded. |
| Implementation dependency acceptance | §10.3 pins protocol/security behavior. 3B.2/3B.4 must demonstrate a maintained OAuth implementation and MCP Java SDK compatible with the actual server dependencies and independent clients; no custom OAuth parser or permissive fallback. |
| Persistent schema and enforcement | §10.8 is a concrete proposal, not installed schema. Verify authoritative shapes, confined bootstrap lookup, RLS/grants, concurrency, quotas and audits in isolated SQL before activation. |
| Operational acceptance | Exact issuer/resource URLs, approved proxy/TLS/origins, client redirect/key inventory, audit retention sign-off and performance evidence must be recorded before pilot enablement. Missing configuration fails closed. |
| Authoritative Case SQL | Approved offline source artifact remains unavailable; Phase 1R authoritative Case acceptance remains BLOCKED. Synthetic results cannot close this gate. |
| Document/timeline expansion | No authoritative document-content boundary or equivalent secured bounded chronology operation; both remain deferred. |

## 5. Audit compatibility review

The integration/tool/grant access-event requirements below gate AI data access. Ordinary web requests
must satisfy their operation's required Shale audit semantics, but do not need a registered AI identity,
MCP tool attribution, or the complete AI read/failure audit extension. Implement any required non-UI
PHI-read seam for a web use case independently; do not fabricate integration fields or silently omit a
required audit. Reuse that seam for AI once its additional attribution and persistence gates are ready.

The minimized Overview is now a concrete reuse example: `CaseSummaryDao.readMinimizedCaseOverview`
appends the existing event through `AuditLogDao.appendPhiWriteAudit(Connection, ...)` before commit.
Its row preserves tenant/user/Case/time and `action=READ;screen=Case.Overview`, but has no structured
integration, tool, grant, correlation or outcome attribution. Preserve this PHI event and its failure
semantics; add approved AI attribution at the same authoritative boundary rather than duplicating the
Overview query/audit in MCP. Phase 3A chooses a narrow supplementary access event (§10.5);
do not require another success PHI row merely to attach AI provenance. Later serialization or transport
failure after commit must be distinguishable from successful server access; an audit row does not prove
delivery to a human or model.

Use existing Shale audit ownership, intended append-only retention, administrator review, sanitization,
and transaction patterns. Do not create a parallel AI audit product or use Case/Task Timeline as compliance
history. Reported runtime audit CRUD means database-enforced append-only/admin-only protection is still
debt; intended architecture is not a verified permission property. Required conceptual access-event fields are:

| Field | Recommended treatment |
| --- | --- |
| Tenant, acting user | Server-derived tenant/actor; validate against SQL context. Pre-authentication failure may have no verified tenant/actor; never fabricate one from request input. |
| Integration and delegation | Stable registered integration ID and safe grant reference, not token, key, installation secret, or provider conversation contents. |
| Tool/application operation | Closed vocabulary, contract version, and source such as MCP or REST integration; distinguish protocol tool from shared business operation. |
| Entity/parent context | Case, Contact, Organization, Task, or eventual Document IDs where relevant; preserve parent Case identity. Search events use bounded result count and scope; §10.5 adds typed child resource rows for the at-most-ten disclosed Case IDs, never raw queries or serialized result-ID lists. |
| Time/correlation | Server UTC timestamp and server-generated/validated bounded request correlation ID propagated into authoritative audit; no trusted free-form caller metadata. |
| Outcome and class | Success, denied, validation failure, unavailable, or internal failure as closed safe codes; explicitly READ or MUTATION. No exception strings. |

Compatibility matrix:

* Overview/detail, task detail/list fields, case updates/timeline narratives, contact sensitive fields,
  material metadata, and eventual document opens/downloads are sensitive access. Reuse PHI-read semantics
  through a non-UI application seam, with an approved access-event extension for integration attribution.
* AI search summaries are automated disclosure. Require one bounded operation-level access event, including
  empty successful results, without keystroke telemetry or query text. This is an external-access policy;
  it does not alter the accepted R2 web summary exemption or the intentionally unaudited desktop
  suggestions. Other web search audit treatment requires its own scoped review. Because the reused R2
  page worker intentionally appends no PHI read row, the shared AI context/access-event seam must enforce
  approved integration auditing before MCP disclosure; the MCP adapter cannot silently inherit that exemption.
* Tenant integration enable/disable, registration, scope changes, grant revocation, and later domain writes
  are meaningful administrative/security/domain actions. Map administrative/domain changes to reviewed
  entity-action vocabulary and session/grant revocation to compatible session-security semantics.
* Token checks, nonsensitive permission predicates, health, and tool schema discovery intentionally produce
  no PHI/entity-action read row. Aggregate sanitized operational counters are sufficient unless security
  review identifies abuse; discovery invocation does not grant access.

The exact gap is structured integration/tool/outcome context for reads and failures: PHI UI read rows do not
carry it, entity-action has no READ/FAILED action or integration/tool keys, administrative-read types are limited,
and session-security events cannot represent general business reads. Existing source/correlation fields help
with later mutation attribution but do not solve the entire read model. Phase 3A specifies the scoped
supplementary event in §10.5; defer its schema/service/review implementation to Phase 3B. Do not squeeze
JSON into arbitrary PHI value fields or claim today's schema already supports it.

For AI sensitive reads, perform authorization, bounded read, and required audit persistence at the authoritative
operation seam and commit audit before releasing data. Follow the existing minimized Overview and
administrative-read fail-closed patterns, not UI best-effort failure handling. Multi-query compositions need
a deliberate connection/transaction
plan because existing ports often borrow independently. Failure returns no sensitive result and no success claim.
A denied/failed attempt needs separately approved durable security-event semantics outside a rolled-back
business transaction; no invented success event or untrusted tenant attribution. If required failure audit is
unavailable, deny access and emit only sanitized operational failure telemetry; persistence/recovery behavior
must be verified before activation.

Later mutations and required domain/entity/PHI audits share the business SQL connection before commit and roll
back together on audit failure. Propagate correlation/source from verified context; avoid existing hardcoded
`SHALE_DESKTOP` attribution on any newly exposed shared mutation. Publish invalidations only after commit.
Never audit prompts, raw query strings, names, PHI values in general metadata, URLs, file content, tokens/hashes,
RowVer, DTOs, SQL, or exception text. Test cross-tenant review denial, actor/entity attribution, bounded metadata,
audit failure rollback/no disclosure, and absence of sensitive values. This task adds no audit schema or events.

## 6. Proposed initial MCP surface

The Phase 3B pilot has exactly two semantic tools, with complete proposed schemas, descriptions,
bounds, errors, scopes and shared-operation mappings in [§10.9](#109-first-two-tool-contracts).
They are **not implemented or active**. Their names do not expose REST routing details.

| Tool | Shared operation | Required scope |
| --- | --- | --- |
| `search_cases` | `CaseServicePort.searchMinimizedCases` | `cases.search` |
| `get_case_overview` | `CaseServicePort.readMinimizedCaseOverview` | `cases.overview.read` |

`listMinimizedAssignedCases` remains an ordinary REST/shared operation with no pilot MCP tool.
The earlier five-tool assessment is superseded for this pilot: tasks, Contacts and Organizations
are later work requiring separate policy, bounded shared operations and audit acceptance.
No `get_case_timeline`: existing updates/domain timeline reads do not establish one authoritative,
actor-aware, bounded and audited chronology contract. Never reconstruct it from audits/UI/child tables.
No `search_case_documents` or `get_case_document`: CaseLinks, Material Item metadata, generated
summaries and arbitrary URLs do not establish authorized document content. Documents, RAG, resources,
prompts, mutation/write scopes and generic REST/DAO/SQL/URL tools remain excluded.

## 7. REST/OpenAPI implications

The present Spring Boot/core/data split is suitable for redesigned web and future mobile clients. Continue it
and strengthen selected contracts instead of starting a second web/mobile backend. Prefer the existing
minimized `/api/v2/cases` search/assigned/Overview routes and their concrete OpenAPI schemas for the
selected read workflow. Retain broad legacy case search/detail, case tasks/updates, Contact/Organization
search/detail, lookup, notification cursor, auth, and durable-session routes for their existing consumers.
Reuse authoritative Contact/Organization aggregates and Case Date projections rather than raw tables
or legacy scalar mutation logic. Keep release/policy global control-plane operations separate from tenant data.

Web/API work can implement and verify these shared decisions per use case using existing bearer sessions.
MCP/AI OAuth, grant storage, tool discovery and AI-specific audit metadata are separate follow-on work.
The existing required audit/authorization checks for each REST operation remain release criteria.

Phase 2 should standardize:

* Concrete response schemas and operation IDs, replacing ambiguous `Object` descriptions when necessary;
  safe request DTOs/projections rather than persistence rows. Document nullable fields, enum/custom lookup
  identity, UTC instants versus existing local Case Date/task times, ID encoding for mobile/JavaScript,
  relationship visibility, soft deletion, and empty/unavailable results.
* Bounded database paging/sorting and response budgets, including remaining Contact prefix slicing
  and Organization directory/search behavior. Preserve the completed R1 Case SQL paging and R2
  size+1/window semantics. Keep existing list/page contracts compatible until an explicit deprecation;
  do not add full counts where unavailable/expensive. Notifications already offer a cursor pattern to assess.
* Common safe error envelope with documented 400/401/403/404/409/429/503 behavior, sanitized correlation,
  and no forbidden-resource existence leakage. REST error codes and MCP protocol errors are translated by
  adapters from the same application outcomes, not duplicated domain rules.
* Server-side authorization/field validation/audit consistency for every web/mobile use case. Transport parsing
  stays in controllers; business policy moves below adapters only where needed. Clarify mutation concurrency
  and retain existing `/api/v2/contacts` and `/api/v2/organizations` compatibility decisions; a versioned
  route alone is not a complete version policy.
* Reviewed generated OpenAPI with bearer/security requirements, schemas, examples, pagination and errors;
  export/review a reproducible specification and compatibility diff in later local verification. Assess typed
  client generation against `shale-web/src/api.ts`; do not require a runtime API gateway or hosted registry.
* Preserve existing bearer login/refresh/logout/me and bound session behavior. Decide browser XSS/CSP and
  HttpOnly-cookie tradeoffs and mobile protected storage/refresh needs in separate client security work.
  OAuth integration credentials are not ordinary web access bearers and cannot unlock unrestricted REST
  writes. Enforce audience/scope at every eligible adapter or reject the credential entirely.
* A published compatibility/deprecation policy based on actual client consumption, including additive changes,
  field retirement, versioned breaking changes, and independently scheduled MCP tool-schema evolution.

Normal REST remains independently useful even if no AI integration is enabled. MCP may compose several shared
queries into a semantic overview, but each composition must preserve policy, bounds, consistent domain meaning,
and audit. It does not need a corresponding new HTTP route or a one-to-one endpoint mirror.

## 8. Phased implementation roadmap

Checkboxes mean verified completion of the stated item, not merely code written. Update evidence and owning
documents with each subsequent task; split implementation into small reviewable changes. Historical
documentation-only checks remain scoped to their dated records; implementation phases must
run the relevant focused, affected and critical checks below.

### Phase 0 — Architecture and contract (**IN PROGRESS**)

**Goal:** A reviewed, evidence-backed shared architecture and minimal contracts.
**Dependencies:** Repository assessment; product/security and client owners for deferred choices.

* [x] Assess live repository, service boundaries, REST/OpenAPI, sessions, RLS, audit, and documents.
* [x] Record recommended service/security/audit model, REST improvements, and five-tool proposal.
* [x] Record existing authority and deferred decisions without implementation or schema changes.
* [ ] Confirm policy and required audit treatment per shared/web operation being delivered.
* [ ] Finalize REST compatibility decisions per web/mobile contract independently of AI review.
* [x] Phase 3A: specify AI authentication/transport, grants and additional audit representation (§10); implementation/activation evidence remains required.
* [x] Phase 3A: finalize the first two proposed MCP tool contracts (§10.9); wider tools remain deferred.

**Verification:** Documentation consistency/relative-link review and `git diff --check`; reviewers trace
selected operations to code and resolve the decision table. **Completion:** Contract/security owners record
accepted choices and remaining exclusions; no unresolved policy is treated as permission to expose data.

### Phase 1 — Shared application/service boundary (**IN PROGRESS**)

**Goal:** Selected operations are authoritative and reusable from either protocol.
**Dependencies:** Policy/contract and required audit acceptance for the operation being changed, plus
its owning domain roadmap. AI issuer/transport/grant decisions and full Phase 0 completion are not
prerequisites for ordinary web/service work.

* [x] Identify existing minimized Case ports/adapters/workers with actor/context checks, SQL paging
  and committed Overview audit; source evidence is in the first-read R2 record.
* [x] Phase 1S: prove the existing three minimized Case operations at the shared boundary (§9.3),
  retaining one worker, actor/context checks, committed Overview audit and bounded DTO/paging behavior.
* [ ] Complete each additional selected path's actor, tenant, field policy, audit, connection and bounds.
* [ ] Reuse core ports/data adapters/DAO workers; extract only necessary non-UI orchestration.
* [ ] Establish verified immutable execution context and actor-aware shared query operations.
* [ ] Implement required ordinary operation read-audit seams; retain strict RLS and explicit predicates.
* [ ] Separately extend those seams for approved AI integration attribution before AI exposure.
* [ ] Establish bounded DAO paging and minimized projections; no false-success interface defaults.
* [ ] Verify live RLS/grants for all selected parent/child tables, explicitly resolving the documented
  Contacts parent-predicate gap with security owners before activation.

**Tests:** Port-to-production-gateway delegation, direct service-call bypass denial, revoked/disabled user,
restricted case/child and field access, non-dbo two-tenant RLS, pooled concurrent tenant/user reuse, context
initialization failure, audit failure/no disclosure, and legacy REST parity. Run relevant focused suites and
repository selector/critical local checks under existing rules. **Completion:** Each delivered REST operation
invokes a proven shared boundary without duplicating business/security logic; reads are bounded and
required audits pass. Record AI readiness separately; REST acceptance does not require an implemented
MCP adapter or AI audit extension.

### Phase 2 — Stable REST/OpenAPI foundation (**IN PROGRESS**)

**Goal:** Web/mobile/integrations consume documented stable application contracts.
**Dependencies:** Verified shared operations and REST compatibility decisions for the selected web/mobile
use cases. No dependency on MCP, AI OAuth, registration/grants or completion of AI contract review.

* [x] Reconcile the implemented R1 exact legacy Case paging and R2 minimized endpoints/OpenAPI with
  this roadmap; the R3 browser consumer and R4 acceptance ledger remain owned by the web roadmap.
* [x] Formalize the selected three minimized Case endpoints/schemas/errors/paging (§9.4).
* [ ] Review broader REST operations before adding any missing use case.
* [ ] Close outstanding Case-read runtime security/deployment/performance gates using the existing
  runbook/ledger; the supplied export confirms missing FILTERs and needs labelled derived/candidate preparation.
* [x] Retain the selected Phase 1S authority/audit boundary below REST and compatible existing routes.
* [ ] Review authoritative auth/authorization and audit for broader REST operations.
* [x] Review generated selected Case OpenAPI, existing web validators and version/deprecation policy;
  protect material compatibility and serialized DTOs with executing tests (§9.4).
* [x] Document the separation of browser user authority and future integration audience/scopes;
  implementing AI OAuth or grants is not part of ordinary web acceptance.

**Tests:** Existing `OpenApiDocumentationTest` plus selected controller/service integration contracts,
authentication and authorization failures, safe errors, pagination/query bounds, sensitive field omission,
concurrency compatibility, and existing web-client regression checks as affected. **Completion:** Reviewed
OpenAPI describes actual behavior and selected web/mobile contracts remain compatible and secure.

### Phase 3 — Read-only MCP foundation and minimum tenant controls

#### Phase 3A — MCP security/integration contract (**COMPLETE**)

Documentation/implementation-readiness only. [Section 10](#10-phase-3a--mcp-security-delegation-integration-and-audit-contract)
defines separate integration/tenant/user/credential/grant/operation identities, opt-in, OAuth and transport,
audit transaction/disclosure policy, resource limits, revocation, tool schemas, persistent entities,
threat controls and the testable implementation sequence. No authorization semantics are left for the
adapter author to invent. This status does not approve a deployment or claim controls exist.

#### Phase 3B — First read-only MCP implementation (**IN PROGRESS**)

**Goal:** Implement §10 in small increments [§10.11](#1011-phase-3b-implementation-sequence), with every
activation flag off until acceptance. Dependencies: selected Phase 1S/2 operations, new persistence/auth/audit/
quota enforcement and actual SQL/client/runtime security acceptance. Authoritative Case SQL is still BLOCKED.
No active MCP route, AI data disclosure, production SQL or deployment is part of Phase 3A.

**Completion:** Both tools pass direct-operation, adversarial, cross-tenant, pool/async, audit-failure,
revocation-race, cumulative-budget and independent-client acceptance with recorded evidence. Real-data
pilot enablement requires explicit later authorization and closure of §10.12 gates. Documents/timeline,
other domains and writes remain absent. An admin UI is later; minimum audited operator controls and
user consent/revocation must exist before any pilot.

### Phase 4 — First-party dogfooding (**NOT STARTED**)

**Goal:** Validate usefulness and controls with Curtis & Co.'s local AI as a normal client.
**Dependencies:** Phase 3 acceptance and explicit tenant/user grants; start with representative test data.

* [ ] Register the local client through ordinary tenant controls; no SQL, desktop identity or privileged keys.
* [ ] Exercise search -> overview workflows; tasks/directories require separate later acceptance; measure useful fields, latency and cost.
* [ ] Verify audit reconstruction, denied access, revocation, tenant switch and output minimization.
* [ ] Refine versioned contracts from recorded evidence without a local-AI exception.

**Verification:** Repeat common workflow/security cases through another compatible hosted or local client;
confirm equivalent policy/outcomes and no provider-dependent service logic. **Completion:** Tenant/security
owners accept useful read workflows, bounded performance and accurate audit evidence; no customer rollout yet.

### Phase 5 — Tenant administration experience (**NOT STARTED**)

**Goal:** Productize controls already mandatory in Phase 3.
**Dependencies:** Phase 3 lifecycle and Phase 4 feedback; existing Settings/session/admin patterns.

* [ ] Add tenant-admin enable/disable and registration UI, safe credential/session management and rotation.
* [ ] Add grant/scope management and user consent/revoke visibility with no secret redisplay.
* [ ] Provide authorized tenant audit visibility and integration usage/quota administration.
* [ ] Audit meaningful administrative/security changes through reviewed existing-framework extensions.

**Tests:** Tenant-admin and ordinary user separation, cross-tenant review denial, reduced scope/revocation
immediacy, lifecycle races, no secret/PHI metadata, and same-transaction administrative audit rollback.
**Completion:** Tenants can independently onboard, constrain, inspect, and revoke integrations; pilot controls
are no longer operator-only. Full UI is deferred, but basic controls were not postponed past dogfooding.

### Phase 6 — Controlled write operations (**NOT STARTED; OPTIONAL**)

**Goal:** Narrow approved mutations after read-only architecture is mature.
**Dependencies:** Phases 3–5 maturity and separate product/security approval per mutation.

* [ ] Select semantic commands; verify existing aggregate transaction/validation/concurrency ownership.
* [ ] Require explicit write scopes and live user/case/field permission; read grants cannot be upgraded silently.
* [ ] Design human confirmation/approval bound to exact proposed operation, target, material arguments,
  expiry and authoritative record version; model text or a client flag cannot prove approval.
* [ ] Reauthorize at commit, make required mutation/audit atomic, publish invalidations after commit.
* [ ] Define scoped idempotency keys, conflict handling, safe retries and uncertain-result reconciliation.

**Tests:** Approval substitution/expiry, prompt-injected writes, concurrent edit conflicts, retry/replay and
idempotency isolation, partial-failure rollback, audit failure rollback and accurate actor/integration source.
**Completion:** Each enabled mutation has approved controls and demonstrated atomic outcomes; unapproved writes
stay absent. This optional phase does not block a read-only external release.

### Phase 7 — External/customer readiness (**NOT STARTED**)

**Goal:** Support authorized tenant integrations independently of provider.
**Dependencies:** Phases 0–5 and security acceptance; Phase 6 only for any writes being offered.

* [ ] Publish provider-neutral REST/MCP onboarding, scopes/consent/revocation, tools and safe examples.
* [ ] Publish compatibility/version/deprecation policy, quotas, error/retry guidance and supported clients.
* [ ] Add operational monitoring, incident response, signing-key/credential rotation and audit retention/review.
* [ ] Complete adversarial multi-tenant, OAuth, extraction, document (if offered), and operational security review.

**Verification:** Independent tenant onboarding/revocation, multiple client implementations, upgrade/rollback,
load/failure recovery, fail-closed dependencies and documented audit reconstruction. **Completion:** Approved
release evidence, support/onboarding instructions, enforceable quotas and monitoring; no undocumented vendor
privilege. Document or write tools require their own gates even if read-only customer readiness is complete.

## 9. Progress tracker and next task

Status vocabulary: **COMPLETE** = recorded evidence for the item; **IN PROGRESS** = work/review remains;
**NOT STARTED** = no implementation; **BLOCKED** = prerequisite unresolved. These statuses do not certify
live deployment. Individual blockers are distinct from the phase's implementation status.

| Item | Status | Evidence / dependency |
| --- | --- | --- |
| Repository architecture assessment | COMPLETE | Sections 1–2, pinned live base and concrete source inspection. |
| Recommended target/security/audit/REST/tool direction | COMPLETE | Sections 3–7 record the assessment; §10 now specifies the proposed pilot security/wire contract. No MCP implementation. |
| Phase 0 broader review and final contract | IN PROGRESS | Wider REST/domain/AI expansion review remains; the first two MCP tools have the Phase 3A contract (§10). This aggregate status does not gate all web work. |
| Phase 1 shared secured operations | IN PROGRESS | Phase 1S proves the R2 minimized Case boundary (§9.3); wider operations, AI restrictions/attribution and runtime acceptance remain. No dependency on AI issuer/grants for ordinary web work. |
| Phase 2 stable REST/OpenAPI foundation | SELECTED CASE FOUNDATION COMPLETE; broader REST IN PROGRESS | §9.4 and the owning API contract formalize the existing three minimized reads; broader operation review and live runtime/security/performance acceptance remain open. Independent of MCP/AI OAuth. |
| Phase 3A MCP security/integration contract | COMPLETE (design only) | §10; no route, credential, schema or disclosure activated. |
| Phase 3B first read-only MCP implementation | IN PROGRESS | §10.11 sequence; no endpoint or disclosure; remaining auth/audit/runtime/client gates. |
| Phase 3B.1 integration persistence/security | COMPLETE (source/synthetic proof) | §10.13; focused/affected/critical checks pass; defaults off. |
| Phase 3B.2 credential/authentication validation | BLOCKED at dependency gate | §10.14; separate supported Spring-stack prerequisite before runtime work. |
| Phase 3B.2A OAuth dependency/configuration/protocol foundation | BLOCKED | Current Boot 3.3.4 baseline has no proven maintained OSS AS combination; no dependency or endpoint added. |
| Phase 3B.2B authorization code + S256 PKCE + consent transaction | NOT STARTED | Complete the stack prerequisite and 3B.2A first; no usable token issuance in this slice. |
| Phase 3B.2C opaque access + rotating refresh lifecycle | NOT STARTED | Durable atomic lifecycle, replay/family invalidation and expiry clipping. |
| Phase 3B.2D live credential validation + VerifiedIntegrationContext | NOT STARTED | Exact credential bindings and authoritative live context resolution. |
| Phase 3B.3 AI audit/shared disclosure seam | NOT STARTED | Mandatory integration access audit and final handoff gate. |
| Phase 3B.4 MCP transport/discovery | NOT STARTED | No route, dependency or discovery in 3B.1. |
| Phase 3B.5 search_cases | NOT STARTED | No Case tool implementation. |
| Phase 3B.6 get_case_overview | NOT STARTED | No Case tool implementation. |
| Phase 3B.7 adversarial/local acceptance | NOT STARTED | Authoritative Case artifact and remaining operational/security/client acceptance required. |
| Phase 4 ordinary-client dogfooding | NOT STARTED | Phase 3 tested controls, tenant/user grants. |
| Phase 5 administration experience | NOT STARTED | Minimum controls first, then pilot feedback. |
| Phase 6 optional controlled writes | NOT STARTED | Read-only maturity and per-command approval. |
| Phase 7 customer readiness | NOT STARTED | Read-only acceptance; write maturity only if offered. |
| Legacy login EXECUTE retirement | OPERATOR-REPORTED DEPLOYED, October 20 | Exact three procedures and reported API/installed-desktop checks in §2.5; monitoring closeout not established. Do not replay retirement. |
| Bounded auth modules / Stage A and B permissions | IMPLEMENTED / MERGED; production details UNCONFIRMED | #1868/#1871, later UTC/session corrections #1870/#1889; separate from the legacy ACL delta. |
| Password / durable sessions / remembered desktop | IMPLEMENTED / MERGED; scoped operator acceptance RECORDED | October 2/6 session records and October 20 production checks, each with original environment limits. |
| Desktop/API/database release compatibility | IN PROGRESS | 1.0.137 repository release metadata exists; current API JAR, SQL/fence state and fleet/source parity need separate deployment evidence. SQL/API/client order is in the deployment guide. |
| Web V2 and eventual native phone clients | IN PROGRESS / native NOT STARTED | Merged R1–R3 and R4 reported hosted/iPhone browser flows; web acceptance incomplete; no native release. |
| Live RLS coverage acceptance | BLOCKED | Non-dbo deployment evidence, including documented Contacts parent-predicate gap. |
| Wider Case/field policy | BLOCKED for expansion | Pilot grant explicitly covers the accepted minimized active tenant Case set (§10.2); additional fields/children/directories and per-case grants require new decisions. Team membership is not an ACL. |
| AI read/failure audit | CONTRACT COMPLETE; implementation NOT STARTED | §10.5 supplementary event; schema, transaction, protected review and fault acceptance remain activation gates. |
| Phase 1R three-FILTER migration / exact rollback | MERGED #1898; SYNTHETIC LOCAL acceptance | [Owning runbook](../testing/case-read-local-security-verification.md#phase-1r-candidate--2026-10-21): transactional/idempotent candidate, drift/atomic-failure tests, confined V1/V2 and real SQL session/controller compatibility; no production activation. |
| Phase 1S shared minimized Case boundary | COMPLETE (source/synthetic proof) | §9.3: existing three JavaFX-free operations retained, direct service/context/audit/paging and REST/OpenAPI checks pass. No broad API, live SQL or MCP acceptance. |
| Local Case-read SQL security execution | BLOCKED | Original export is absent from this task workspace; derived/candidate schema artifacts and hashes unavailable. Authoritative Case SQL, real audit denial and injected lost acknowledgement remain unexecuted. Synthetic auth passes do not close Case SQL. |
| Timeline/document tool expansion | BLOCKED | Bounded timeline service and authorized content/provider boundary. |

**Phase 3A COMPLETE (design); Phase 3B IN PROGRESS, with 3B.1 COMPLETE at source/synthetic scope (§10.13); MCP inactive.**
Phase 1S shared Case source/synthetic proof and selected Phase 2 REST completion are retained.
Broader REST review remains IN PROGRESS. Phase 1R is merged and its three-FILTER candidate locally/
synthetically validated; authoritative Case SQL remains **BLOCKED** by unavailable approved offline input.
Production activation, Stage A/B deployment/fleet evidence, non-bypass grants/RLS and operational acceptance
remain independent gates. Phase 3A does not close or redesign them. Web/phone delivery remains independent.
Phase 3B.1 is COMPLETE at its recorded scope (§10.13); 3B.2A is BLOCKED by the compatibility gate (§10.14).
Complete its separate Spring-stack prerequisite before resuming 3B.2A. All capability/tenant flags remain off.

### 9.1 Ready-to-run Codex prompt — Phase 1R

Historical scope/acceptance specification, implemented and merged in #1898. Do not treat it as the next task.
Candidate implementation and synthetic evidence are recorded above;
remaining authoritative and deployment gates must not be mistaken for completed work.

```text
Repository: gseshadow/Shale
PR base: codex/latest
Task: Implement Phase 1R, a targeted three-FILTER Case-read RLS candidate with isolated local acceptance.

Fetch current origin/codex/latest; read AGENTS.md and architecture/codex-prompt-rules.md completely.
Create a separate task branch/worktree and preserve unrelated work. Follow documentation routing.
Read docs/architecture/ai-integration-and-mcp.md §§2.5/9, architecture/database-schema.md,
architecture/tenancy-and-rls.md, docs/testing/case-read-local-security-verification.md (especially
RLS blocker assessment and prerequisite rollout), docs/testing/auth-module-permission-cutover.md,
docs/azure-app-service-deployment.md and R4. Inspect current V1/V2 auth callers, module installer,
Stage A/B guards, runtime context initialization, minimized Case queries/audit and their existing tests.

Preserve October 20 operator-reported production retirement and API/installed-desktop passes.
Do not repeat retirement, completed recovery/rehearsals or accepted desktop/phone-browser checks.
Do not treat retirement as evidence of Stage A/B, current JAR/MSI parity, three FILTERs or monitoring.

Scope:
1. Add a fail-fast, transactional, idempotent candidate migration/preflight/postcheck and exact scoped
   rollback for only Users and actual auditLog -> sec.fn_FilterByTenant, Statuses ->
   sec.fn_FilterByTenantOrGlobal, on existing enabled sec.TenantFilter. Resolve actual identifier
   collation/ownership and reviewed function definitions. Matching predicates are a no-op; unknown,
   conflicting or disabled policy/function shapes fail. Preserve all pre-existing policies/functions,
   grants, rows, history, columns, indexes and overlay precedence. Do not run the June broad proposal.
2. Keep original authoritative offline schema artifact immutable. Use only approved offline schema-only
   input already available; record source/derived/candidate hashes and dependency inventories separately.
   Preserve actual constraints/computed functions/collation. Derive a labelled candidate with exactly
   these additions. Keep CaseReadLocalSqlServerTest's fixed security expectations unchanged. If importer
   support needs extension, make the smallest safety-reviewed/tested change; never fabricate coverage.
   If the artifact is unavailable, complete independently reviewable migration/static/fixture work and
   explicitly mark authoritative Case SQL acceptance blocked. Do not request data or wholesale packets.
3. Only in a new loopback disposable database with synthetic data, verify null-context V1 password and
   V2 remembered resolution, confined owner authority/caller restoration, separate non-bypass runtime,
   login/me/refresh/logout and remembered rotation/replay/revocation under the new Users FILTER.
   Reuse existing auth fixtures; distinguish their synthetic schema from authoritative Case input.
4. Run the unchanged Case harness against the labelled candidate: non-dbo tenants A/B/NULL, pooled
   context reuse/setup failure, global/current Statuses and foreign exclusion, active actors/joins,
   paging/hasMore and required Overview audit. Real denied audit INSERT must withhold data/roll back;
   preserve committed audit under injected lost-acknowledgement and label that injection accurately.
5. Record SQL-before-API/desktop compatibility and deployment gates without performing deployment.
   Users FILTER activation requires sanitized existing evidence of installed V1/V2/module fences,
   intended auth/runtime identities and compatible deployed callers/drain. Never use dbo/ShaleAdmins
   runtime bypass or an auth SELECT fallback to preserve old clients. Unknowns stay explicit.

Acceptance:
- Exact three-predicate delta, repeat no-op, drift rejection, failure atomicity and exact rollback tested.
- Auth/bootstrap/session compatibility plus non-bypass two-tenant/null/pool and required-audit scenarios
  pass locally, with real versus synthetic/injected/authoritative evidence labelled separately.
- Review existing affected migration/auth/session/Case/runner tests before edits; test production
  delegation and negative paths, not only source strings. Run focused tests, repository selector's
  relevant affected suite and critical mvn test. Attribute inherited advisories; no blanket all-tests.
- Update this canonical tracker and owning runbook with exact commands/results, remaining gates and
  rollback; check relative links, anchors, fences and git diff --check. Commit/push/open a PR to codex/latest.
  Unexecuted authoritative SQL is unverified, never a passed security gate.

Explicit exclusions: production/Azure connections or SQL, permission changes outside disposable fixtures,
deployment/publication/version bumps, executing old operator packets, editing private captures,
password/token/data collection, whole JDBC migration, auth rewrite, RLS write BLOCKs, broad least-privilege
or audit-admin/logging remediation, schema/data backfills, new Case fields/endpoints, MCP/OAuth/grants/AI
audit implementation, mobile/PWA/native features. No MCP activation or claim of complete least privilege.
Preserve existing audit schema and fail-closed Overview event/transaction; summary web audit exemption
stays unchanged. Audit grant/BLOCK/admin gaps and AI attribution stay explicit follow-on activation gates.
```

### 9.2 Reconciliation verification — 2026-10-21

Documentation only: ten existing Markdown files changed; no production/test/SQL/configuration contract
changes. Audit compatibility uses existing schema/events unchanged; recording evidence and dependencies
creates no sensitive read or domain/admin mutation. AI attribution remains the scoped gap in §5.

The configured Git proxy refused fetch connections. The GitHub connector fetched current branch/tree/
blob and PR metadata instead; imported head/tree/text blob hashes were checked against GitHub before
creating the isolated task worktree. Original checkout/work and unrelated image tree entries are preserved.

Checks for this reconciliation (not runtime/production acceptance):

```bash
python3 /workspace/work/check-roadmap-docs.py
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --plan-output /workspace/work/roadmap-selection.json
git diff --check origin/codex/latest HEAD
```

The task-local documentation checker verifies all relative links/anchors, fences, documentation-only
scope, SQL prerequisite paths and evidence/status markers. No Maven/npm/SQL/Windows/rehearsal suite is
appropriate to unchanged runtime contracts; selector output is impact review only. Exact executed
results are recorded in the PR, without promoting historical or unexecuted tests to new passes.

### 9.3 Phase 1S — shared secured Case read operations — 2026-10-21

**Status: COMPLETE for the selected source/application boundary and local synthetic proof.** This is a narrow application-boundary proof,
not broad REST completion, live SQL acceptance or MCP readiness. The fetched base is the #1898 merge
`345452f9e196bc537b78dc70fc46e6353b2b8d22`; Phase 1R remains merged/synthetically validated, with
**authoritative Case SQL acceptance BLOCKED** by the unavailable approved offline schema artifact.
No Stage A/B completion or production RLS activation is inferred.

**Finding and implementation decision:** R2 already supplied the appropriate JavaFX-free shared boundary.
Keep the three existing [CaseServicePort](../../shale-core/src/main/java/com/shale/core/service/CaseServicePort.java)
operations; no new service/module, execution-context framework or duplicate business implementation is needed.
Their production [CaseServiceAdapter / DaoCaseGateway](../../shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java)
delegates to [CaseSummaryDao](../../shale-data/src/main/java/com/shale/data/dao/CaseSummaryDao.java).
`CaseDao` supplies the same configured `DbSessionProvider`; its broad detail worker is not invoked by these reads.
Port membership alone is not a security proof for the other Case methods.

| Selected shared operation | Authoritative behavior | Existing REST adapter |
| --- | --- | --- |
| `searchMinimizedCases(query, tenant, actor, page, size)` | Current eligible actor, active tenant-wide Cases; literal name filter before SQL paging. Blank query still checks authority and returns no browse. | `POST /api/v2/cases/search-page` |
| `listMinimizedAssignedCases(tenant, actor, page, size)` | Same authority/active tenant predicate, plus tenant-qualified `CaseUsers` EXISTS for this actor before paging. Assignment selects this list only. | `GET /api/v2/cases/assigned-page` |
| `readMinimizedCaseOverview(caseId, tenant, actor)` | Same authority/active tenant predicate, narrow one-Case read and required committed Overview audit. Missing, deleted and foreign Cases all return empty. | `GET /api/v2/cases/{caseId}/overview` |

#### Enforcement map (source inspection and synthetic execution)

| Concern | Actual owner / guarantee |
| --- | --- |
| Authenticated principal | Prod/azure `BearerTokenServerSessionResolver` verifies the token and `ServerAuthSessionService` validates the session; bound tokens use durable ownership/current JTI/expiry/revocation and current eligible Users. Legacy compatibility remains separately bounded. Development headers remain dev/local only. Authentication is infrastructure, not a Case query parameter. |
| Request actor and tenant | `MinimizedCaseReadController` obtains one `ServerRuntimeSessionState.requirePrincipal()` per invocation and passes its tenant/user. `RequestScopedDbSessionProvider` resolves the authenticated request again for the DB borrow. Parameters are assertions against that authority, not a freely selectable identity. |
| Current actor eligibility | `CaseSummaryDao.verifyMinimizedActor`, on the actual read connection, requires positive IDs, matching `Users.Id`/`ShaleClientId`, `COALESCE(is_deleted,0)=0`, `COALESCE(IsRemoved,0)=0`, and both SQL session keys equal the supplied tenant/actor. In this schema eligibility means non-deleted/non-removed; no invented `is_active` or role flag. All three reads, including blank search, use it. |
| Context initialization / RLS participation | `RequestScopedDbSessionProvider` → `RuntimeSessionServiceConnectionProvider` creates a fresh `RuntimeSessionService` per borrow and stamps both `ShaleClientId` and `PrincipalUserId`. Either setup failure closes the lease. No shared mutable desktop identity, auth/global datasource or privileged bypass is used for these operations. Existing mutable pooled keys are preserved. Missing Phase 1R live FILTER acceptance is not silently treated as active protection. |
| Explicit isolation / visibility / deletion | `selectMinimized` predicates `c.ShaleClientId=?` and `ISNULL(c.IsDeleted,0)=0`; assignment/status/user/practice-area/date joins retain tenant or approved global-overlay constraints. Eligible ordinary users may search/read active tenant Cases; no Team-only ACL, role-label inference or administrator exception is added. |
| Paging / limits | Worker validates pages 0–100, sizes 1–25 and stripped query ≤100 UTF-16 units. SQL OFFSET is `page*size` (≤2500), FETCH is size+1 (≤26), and hasMore derives from that authorized probe even at page 100. No count, prefix materialization or client security filtering. Five-second statement timeouts and SQL DATALENGTH guards remain. Row limits do not bound scanned work or end-to-end latency. |
| DTOs | Immutable `MinimizedCasePage` and `MinimizedCaseOverview` core records expose the existing semantic eight-field allowlist and bounded immutable relationship records, no DAO model/JDBC type/RowVer/UI state. `updatedAt` remains nullable stored local text; no UTC reinterpretation. Existing application and HTTP shapes agree, so no extra DTO layer is justified. |
| Case Dates | Minimized Overview exposes **no dates** and performs no date/detail hydration. Assigned ordering alone uses existing tenant-qualified active `CaseDates.StartsAt` occurrences with the protected tenant-effective INTAKE mapping. Broad legacy `getAuthoritativeCaseDetail` uses `CaseDateDao` migrated compatibility state separately; it is not this secured slice. No retired Case scalar reconstruction or new date source is introduced. |
| Summary transaction / audit | Search/Assigned borrow and close one connection using normal autocommit reads. Their approved ordinary minimized summary audit exemption is unchanged; it is not extended to Overview, broad reads or future AI disclosure. |
| Overview transaction / audit | `CaseSummaryDao.readMinimizedCaseOverview` owns begin → actor check → narrow read → `AuditLogDao.appendPhiWriteAudit` on the same connection → commit → return. Empty reads roll back without success audit. Required audit must append exactly one row; read/audit/commit failure withholds the result and attempts rollback where a transaction began. Connection close must succeed before return completes. |
| Error handling / uncertainty | Invalid operation bounds throw `IllegalArgumentException`; worker authority throws `CaseReadException.DENIED`; SQL timeout/read failures and required audit failures use typed Case-read outcomes. REST `MinimizedCaseReadErrors` maps these to safe 400/401/403/404/503 outcomes without data or private exception text. A lost commit acknowledgement withholds data; a rollback attempt cannot undo a committed audit. No automatic replay or audit deletion. |
| Protocol-only work | REST parses/validates request inputs, maps empty Overview to 404, serializes once and checks the exact delivered UTF-8 bytes (128 KiB page / 8 KiB Overview), sets no-store, and translates errors. Duplicate input validation is deliberate early HTTP rejection; worker bounds and authority remain enforced for direct callers. Exact JSON byte budgets belong to the encoder, not an HTTP-dependent core operation. |

**Audit compatibility:** the existing schema/event is sufficient for this slice: Case object type 1,
FieldCode 4, `Case.Overview.Read`, `action=READ;screen=Case.Overview`, Case ID and authoritative actor,
connection-derived tenant, existing timestamp; no sensitive values or search text. No migration or new audit
vocabulary is required. Committed audit records server access; subsequent serialization failure/disconnect
is not proof of delivery. Integration read/failure attribution and AI summary auditing remain §5 blockers.

**REST/OpenAPI impact:** no URL, payload, default, limit or schema change. The REST controller continues to
call the shared port once; no controller owns SQL, actor eligibility or audit decisions. Existing
`MinimizedCaseOpenApiConfiguration` and selected `OpenApiDocumentationTest` contracts describe the same core
results. `shale-web/src/features/cases/client.ts`, validators and tests continue to consume those exact
contracts; no web UX or legacy `ApiReadController` contract is changed.

**Future adapter seam and remaining blockers:** an independently authenticated adapter can inject the same
`CaseServicePort` with an application-owned `DbSessionProvider` and invoke these methods without importing
REST controllers, DAOs, SQL, JavaFX, AppState or desktop identity. The current server provider uses servlet
request resolution; it is not an async or MCP identity carrier. A future transport must supply verified
execution identity through server/application infrastructure and initialize each borrowed connection;
passing arbitrary IDs alone is insufficient and fails against SQL context. No new context carrier is
necessary for today's synchronous REST slice. Future integration restrictions and required AI auditing must
extend the same authoritative worker/seam, never fork the query or inherit the web summary exemption.
OAuth/transport/issuer/audience, tenant opt-in, registration/grants, revocation, attribution, extraction/rate/
concurrency/encoding budgets and live acceptance remain independent MCP blockers. MCP is **NOT STARTED**.

Before REST expansion, approve each added operation's field/actor policy, audit/transaction ownership,
bounds and contract, and close the existing authoritative Case SQL, non-bypass deployed RLS/grants,
compatibility/session/audit-fault and representative performance gates. These broader gates do not justify
rewriting the proven minimized operation or declaring all REST/API work complete.

#### Test impact and verification

Pre-edit review inspected `MinimizedCaseReadJdbcTest`, `CaseServiceAdapterTest`, `CaseSearchPagingJdbcTest`,
`CaseSummaryServerProjectionContractTest`, `MinimizedCaseReadControllerTest`, `OpenApiDocumentationTest`,
`RequestScopedDbSessionProviderTest`, `ServerSessionSkeletonTest`, `ShaleServerServiceConfigurationTest`,
and the opt-in `CaseReadLocalSqlServerTest` audit-denial/lost-ack scenarios. The owning R2/audit/date/web
contracts, schema/tenancy, web migration/readiness and current browser consumer/tests were inspected first.

No runtime production behavior changed. Port Javadoc makes the existing secured contract explicit.
The existing JDBC test now invokes the production implementation through `CaseServicePort` and covers all
three reads (plus blank search) against deleted/removed/missing/foreign actors and absent/mismatched context,
Assigned foreign/deleted exclusion and probe bounds, both page limits/ceiling continuation, transaction
setup/read failure, zero-row audit append and injected lost acknowledgement after simulated commit. The
existing runtime test invokes each Case operation through the real server provider/initializer, fails either
context key, and proves lease closure with no Case/audit SQL. REST tests verify caller identity fields cannot
replace principal authority and all operations translate shared denial without a fallback read.

These executing JDBC/HTTP/context tests are **synthetic**, with SQL shapes/bindings asserted and rows modeled
by doubles; they do not execute SQL Server or establish deployed isolation/date-resolution semantics. Existing
authoritative Case SQL harness and its stronger real audit-denial/commit scenarios remain unchanged and
BLOCKED, not reported as passing. Existing tests retain same-tenant success, paging, projection/date source,
required-audit transaction ordering, privacy, response-byte and OpenAPI compatibility assertions.

Final local commands/results follow. No MCP, AI integration, production/Azure SQL,
RLS activation, deployment, version bump or installer publication is part of Phase 1S.


Executed with the existing Maven 3.9.11 / Temurin JDK 21.0.12.1 wrapper (`mvn` below denotes
`/workspace/.tools/bin/mvn`). The initial offline attempt could not resolve two uncached dependencies;
using the configured network resolved them. An OpenAPI attempt without the JVM attach property hit this
sandbox's Mockito self-attachment restriction; the unchanged test passed with the property below.
No repository toolchain, POM or dependency configuration changed. These corrected environment attempts
are not passing runs; final results are:

| Check | Result |
| --- | --- |
| Focused service/gateway/JDBC, paging/date-source, controller, runtime/composition, legacy REST and selected Case OpenAPI contracts | **122 PASS**, zero failures/errors/skips. |
| Committed-base selector affected suite | **114 PASS**, zero failures/errors/skips; 18 classes across data/server/UI and core dependencies. Shared-port consumers plus security/audit/session tests explain the selected cases/contacts/organizations/reports/security-data/server/tasks areas. No full-suite escalation. |
| Critical reactor | **117 PASS**, zero failures/errors/skips; BUILD SUCCESS. |
| Selector units | **25 PASS**. |
| Documentation / diff review | **37 relative links/anchors**, balanced fences, retained evidence/status markers and `git diff --check` PASS. Complete five-file diff reviewed; only port Javadoc, existing tests and this roadmap changed. |

```bash
mvn -o -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-data,shale-server -am -Dtest=MinimizedCaseReadJdbcTest,CaseServiceAdapterTest,CaseSearchPagingJdbcTest,CaseSummaryServerProjectionContractTest,MinimizedCaseReadControllerTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest,ShaleServerServiceConfigurationTest,ApiReadControllerTest,OpenApiDocumentationTest#minimizedReadSchemasAreConcreteBoundedNullableSecuredAndAdditive+legacyCaseSearchPageKeepsParametersDtoAndFourFieldPageSchema -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 345452f9e196bc537b78dc70fc46e6353b2b8d22 --head HEAD --format markdown --plan-output work/selection.json
mvn -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-core,shale-data,shale-server,shale-ui -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.data.service.adapter.MinimizedCaseReadJdbcTest,com.shale.data.service.adapter.OrganizationServiceAdapterTest,com.shale.data.service.adapter.TaskServiceAdapterTest,com.shale.server.controller.AuthControllerTest,com.shale.server.controller.MinimizedCaseReadControllerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest,com.shale.ui.controller.ReportsControllerLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -DargLine=-Djdk.attach.allowAttachSelf=true test
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 work/check_docs.py
git diff --check
git diff --check 345452f9e196bc537b78dc70fc46e6353b2b8d22 HEAD
```

`work/check_docs.py` is an uncommitted task-local checker, consistent with prior roadmap checks: verify
relative targets/heading anchors, balanced fences and retained Phase 1R/MCP evidence classifications.
The selector's emitted Maven arguments were run unchanged with the test-JVM property added. Its plan
was rechecked after the final documentation-only update. No blanket all-tests, Phase 1R SQL, authoritative
Case SQL, production/Azure connection, deployment, version/installer publication or AI-specific work ran.
The unavailable authoritative artifact remains an independent BLOCKED acceptance gate, not a Phase 1S pass.


### 9.4 Phase 2 — stable selected Case REST/OpenAPI foundation — 2026-10-21

**COMPLETE for the selected contract/source foundation; broader REST review remains IN PROGRESS.**
The [owning API/OpenAPI contract](../web-api-azure-readiness.md#stable-selected-case-rest-contract--phase-2-2026-10-21)
records the pre-change exact route/parameter/DTO/error/audit/byte inventory, retained stable versus legacy
paths, current authentication/session authority, paging/hasMore limits and practical compatibility policy.
The live base is Phase 1S merge `18f2dbf0`; Phase 1R merge #1898 and Phase 1S merge #1899 are confirmed
by fetched source/ancestry. Existing minimized REST and Springdoc configuration already supply this slice.

Retain one stable contract for all ordinary clients:

* `POST /api/v2/cases/search-page`
* `GET /api/v2/cases/assigned-page`
* `GET /api/v2/cases/{caseId}/overview`

No route, response DTO, worker, business rule, request default/limit or web consumer changes.
The safe error envelope retains its fields; only overlong error paths now use a bounded fallback. REST still
calls only the proven shared operations (§9.3). OpenAPI now gives the path ID bounds, explicit integer
query/path types, optional/null body defaults, stripped UTF-16 query semantics (rather than an incorrect
raw maxLength), paging/hasMore semantics and required shared error-record members. `info.version` remains
historical metadata; URL v2 is this selected major. The response allowlist is closed to preserve existing
strict web validators and disclosure controls. Future added fields/closed-enum values require explicit
compatibility review; breaking contracts receive a new URL major. No legacy removal deadline is invented;
compatibility routes remain until documented consumer migration and a release decision permit removal.

Audit compatibility retains existing schema/events and committed Overview transaction, plus the approved
ordinary summary exemption. No migration, new audit vocabulary or client-side audit is added. There is
no AI client identity/grant/audit implementation. Documentation/contract refinement adds no sensitive read
or mutation of its own. Unknown infrastructure errors still require safe bounded client parsing; no global
error/privacy guarantee is inferred from these three MVC operations.

**Remaining REST work:** per-operation review of broader Case detail/children, other domains, DTOs,
authorization, audit and resource controls; selected authoritative SQL/non-bypass/deployment/audit-fault/
performance gates remain open under the owning runbook and R4 ledger. This contract completion does not
close Web V2's separately numbered Phase 2/3/4 acceptance or declare the entire REST API stable.
**Phase 1R remains merged, locally/synthetically validated; authoritative Case SQL acceptance BLOCKED**
by unavailable approved offline input. No production activation occurs. **MCP remains NOT STARTED**:
approved delegated authority/audience/issuer/grants, opt-in/integration controls, AI audit representation,
resource budgets and selected runtime security acceptance remain prerequisites (§§4–5).

#### Test impact and verification

Before editing, reviewed the existing selected controller and all OpenAPI tests, error/validation tests,
web caller/runtime validators and client tests, service/gateway/JDBC/paging/projection tests, and relevant
bearer/durable-session/request-context/composition tests. Production changes are annotations/schema customization plus a scoped error-path cap: malformed IDs
cannot inflate reflected error bodies; paths over 512 UTF-16 units use `/api/v2/cases`. Existing controller tests cover principal-only authority, denied reads, no-store,
malformed requests, explicit/default/min/max paging, all-route 401, query stripping/null defaults and all
three routes' byte failures. Existing Phase 1S JDBC doubles retain same/foreign/deleted Case/actor/assignment
filtering, SQL probes, timeouts, audit/transaction failure and uncertain commit without disclosure.

The bounded `OpenApiDocumentationTest` baseline asserts exact supported paths/methods, parameters/defaults/
bounds, body input, bearer scheme, response/error references, field types/required/null semantics and paging.
Descriptions/order/operation metadata remain free to improve. A schema walk checks actual Jackson-serialized
null/populated records, page and error against the generated schema features used here. No full OpenAPI
snapshot or metadata freeze is introduced. These are local synthetic/source contract tests, not authoritative
SQL or deployed client/security evidence. Exact executed commands/results are recorded below after validation.


Final local verification used Maven 3.9.11 / Temurin JDK 21.0.12.1 through the existing
`/workspace/.tools/bin/mvn` wrapper (`mvn` below). Network access was enabled for the configured proxy;
initial offline attempts lacked ICU/phone/JNA dependencies. The JDK attach property plus network-enabled
execution was needed for this sandbox's Mockito instrumentation. No POM/toolchain/dependency change.

| Executed check | Result / scope |
| --- | --- |
| Focused selected controller/service/JDBC/projection, selected and legacy Case OpenAPI, validation/legacy HTTP and bearer/durable-session/context | **145 PASS**, zero failures/errors/skips. |
| Selector affected suite | **91 PASS, 1 inherited failure**, zero errors/skips (92 total). Only the unrelated application-release OpenAPI `channel` binding fails. |
| Full existing OpenAPI/error tests, with unchanged-base reproduction | Three existing `ApiExceptionHandlerTest` diagnostic assertions and the existing application-release OpenAPI binding fail identically at untouched `18f2dbf0`. They are not new regressions or passing checks. The selected scoped error/Case OpenAPI tests pass. |
| Critical Maven reactor | **117 PASS**, zero failures/errors/skips; BUILD SUCCESS. |
| Selector unit tests | **25 PASS**; no full-suite escalation. |
| Docs / final diff | **49 relative links/anchors**, balanced fences/status markers and `git diff --check` PASS. Full nine-file diff reviewed; unchanged DAO/core DTO/web/SQL/build/version sources confirmed. |

```bash
mvn -o -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-server -am -Dtest=MinimizedCaseReadControllerTest,MinimizedCaseReadJdbcTest,CaseServiceAdapterTest,CaseSearchPagingJdbcTest,CaseSummaryServerProjectionContractTest,OpenApiDocumentationTest#minimizedReadSchemasAreConcreteBoundedNullableSecuredAndAdditive+legacyCaseSearchPageKeepsParametersDtoAndFourFieldPageSchema+openApiDocumentsBearerAuthAndCoreWebEndpoints+stableCaseContractRetainsRoutesInputsTypesRequiredNullabilityAndAuthentication+generatedCaseSchemasDescribeActualSerializedRecordsIncludingNullsAndRelationships,ContactValueApiTest,ApiReadControllerTest,DurableSessionAuthTest,ServerAuthSessionLogoutTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest,AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 18f2dbf09955e0ee8d25e50c4beb5f0aa685587c --head HEAD --format markdown --plan-output work/selection.json
mvn -o -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.controller.AuthControllerTest,com.shale.server.controller.MinimizedCaseReadControllerTest,com.shale.server.controller.OpenApiDocumentationTest,com.shale.server.runtime.DurableSessionAuthTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -DargLine=-Djdk.attach.allowAttachSelf=true test
# Existing failing checks reproduced in a separate clean detached base worktree:
mvn -o -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-server -am -Dtest=ApiExceptionHandlerTest,OpenApiDocumentationTest#openApiDocumentsAuthenticatedApplicationReleaseContractsAndClosedVocabularies -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python work/check_docs.py
git diff --check 18f2dbf09955e0ee8d25e50c4beb5f0aa685587c HEAD
```

The emitted affected argv were executed from the JSON plan, then critical Maven ran separately even
though the inherited affected failure was nonzero. The failed offline critical attempt was repeated
online after resolving the missing JNA artifact; it is not counted as a passing run. The task-local
uncommitted documentation checker verifies all relative links/anchors, fences and status markers in
the two changed owners. The final selector recheck uses the same nine paths. No new blanket all-tests,
SQL, browser or npm run is claimed: web files are unchanged, and its validators/callers/tests were reviewed
against retained DTO/routes/defaults/limits; the bounded error-path fallback is not consumed by that client.

No MCP, AI-specific integration, production/Azure SQL, RLS activation, deployment, version bump or
installer publication occurred. Phase 1R authoritative Case SQL remains **BLOCKED**, not a synthetic pass.

## 10. Phase 3A — MCP security, delegation, integration and audit contract

**Status: COMPLETE as the proposed Phase 3B implementation contract; MCP inactive.** Normative “must”
below specifies future behavior, not existing functionality or deployment authorization. Phase 3B is
**NOT STARTED** at design completion; current implementation status is in §10.13. This section resolves the pilot recommendations in §§4–6. No production source,
dependency, schema, permission, endpoint, version or installer changes are needed for Phase 3A.

### 10.1 Evidence and boundaries

Inspected at `216cf082`: `ServerPrincipal` has user/tenant/email only; `ShaleAuthTokenService` is a custom
HS256 issuer/verifier with an eight-hour default, `sid`/`jti` binding but no MCP issuer/audience/scope/client
contract. `BearerTokenServerSessionResolver` delegates to `ServerAuthSessionService`;
`DurableSessionTokenValidator`/`SqlDurableSessionStore`/`UserSessionDao` check ownership, expiry, current JTI,
revocation and current nondeleted/nonremoved user. Conditional refresh and user disable/remove/password-reset
revocation are useful patterns. They do not make ordinary JWTs OAuth tokens. `UserSessions.ClientType`
is closed to WEB/DESKTOP/MOBILE; do not relabel integrations to fit it.

`ShaleServerServiceConfiguration` selects prod/azure bearer resolution and dev/local header compatibility;
`ServerRuntimeSessionState`/`RequestScopedDbSessionProvider` depend on a servlet request. Runtime connections
use `RuntimeSessionServiceConnectionProvider` and stamp both `ShaleClientId` and `PrincipalUserId` on every
borrow, closing on either initialization failure. `GlobalControlPlaneDbSessionProvider` is not a tenant
business-data path. MCP needs an explicit immutable invocation context, not servlet inheritance.

`shale-server` uses Spring Boot 3.3.4 Web MVC, validation and Springdoc 2.6.0. The inspected POMs contain
Spring Security crypto for BCrypt but no OAuth authorization/resource server, SecurityFilterChain or MCP
library. `OpenApiConfiguration.bearerAuth` describes ordinary JWT REST only. Reuse existing modules and
credential verification (`AuthServicePort`/bounded V1); do not extend the regular expression JWT parser
into an OAuth implementation or introduce a provider SDK/domain dependency.

The existing `CaseServicePort` → `CaseServiceAdapter`/`DaoCaseGateway` → `CaseSummaryDao` path is mandatory.
The pilot inherits its accepted active same-tenant minimized Case policy, explicit predicates, parameterized
literal-name search, size+1 SQL paging, five-second statement limits and Overview PHI transaction.
It must add integration restrictions/audit through that same application boundary. No controller/DAO/SQL
entry point in the MCP adapter. The existing `MinimizedCaseReadJdbcTest`, `CaseServiceAdapterTest`,
`CaseSearchPagingJdbcTest`, projection, controller/OpenAPI, `DurableSessionAuthTest`,
`RequestScopedDbSessionProviderTest`, composition and opt-in `CaseReadLocalSqlServerTest` contracts were
reviewed. Phase 1S/2 records above classify synthetic proof and inherited failures; none is a new Phase 3A run.

Protocol evidence reviewed on 2026-10-21: upstream's published
[MCP 2026-07-28 authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization),
[client registration](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/client-registration),
[Streamable HTTP](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http)
and [tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools), from the corresponding
`modelcontextprotocol/modelcontextprotocol` source directory (tree `494ea7d00a6d8aecf659a3bbcea90cb9d4136ada`).
Use **2026-07-28 only for the first pilot**. It removes protocol sessions/GET streams and uses per-request
metadata; do not accidentally implement an older initialize/session handshake. Client compatibility is an
acceptance gate, not a claim that every ChatGPT/Claude/local client currently supports this revision.
An older-version compatibility adapter needs separate explicit review while preserving this security contract.

### 10.2 Identities, opt-in, delegation and effective access

| Identity | Exact binding |
| --- | --- |
| Integration | Random immutable public `IntegrationId` UUID for one tenant registration; bounded display name is presentation only. A distinct OAuth `client_id` identifies that registration at the configured issuer. One tenant registration per client ID in this pilot; the same vendor in two tenants gets distinct registrations/client IDs. |
| Tenant | Existing `ShaleClients.Id` / `ShaleClientId`; non-null owner of registration, grant, session and all access records. No tenant selector in tools or caller headers. |
| Acting user | Existing same-tenant `Users.id`, authenticated by Shale during consent; eligibility uses current `COALESCE(is_deleted,0)=0` and `COALESCE(IsRemoved,0)=0`, not a guessed `is_active` field or token role flags. |
| Credential/session | A public client ID is not a credential. Optional confidential-client key authenticates the client at OAuth endpoints. Separate opaque access/refresh credentials reference a durable delegated session; neither credential nor MCP transport state is the user/grant. |
| Grant | Immutable owner tuple `(tenant, integration, user)` plus specifically consented scopes, creation/expiry/revocation and policy revision. Only Shale's authenticated consent flow creates it. |
| Tool/operation | Static mapping of two exact tool names to two scopes and shared application methods; neither a prompt nor client-supplied method/URL can alter the mapping. |

Four gates are independent: product operator enables the global capability (default false); an active
same-tenant administrator enables the tenant (missing row means false); that administrator registers an
active unexpired integration and its scope ceiling; a user explicitly delegates to that integration.
All tenant defaults remain off. Product enablement gives no tenant authority. Registration gives no user
authority. No bootstrap tenant, no automatic enablement of existing tenants, no migration seeds.

Use the existing authoritative tenant-admin convention: current active/nonremoved same-tenant
`Users.is_admin=1`, checked at the service/SQL boundary against both context keys. Custom firm-wide roles,
ATTORNEY flags and Case Team role labels are not administrator authority. No SQL ShaleAdmins membership.
Tenant admins may enable/disable, register, reduce/change the ceiling, rotate/revoke client credentials,
suspend/revoke registrations and revoke any same-tenant grant/session. Product operators control only the
global feature/configuration through restricted operator configuration/change control; it cannot impersonate
a user. Minimal operator-facing commands/services may precede an admin UI, but must authenticate the actual
Shale admin, apply RowVer and commit the required audit. No direct ad-hoc SQL administration shortcut.

User consent occurs on a Shale-hosted browser surface: authenticate/revalidate through the existing Shale
auth boundary, derive tenant/user server-side, show registered client name, exact scopes, tenant, expiry,
and that minimized names/numbers/staff identities leave Shale for this client. CSRF-protect approval and
bind it to a server-held authorization transaction, client, exact redirect, resource, PKCE challenge and
requested scopes. A user may consent only for self and revoke own grants/sessions; an admin cannot consent
as another user. Denial produces no grant. A tenant switch requires a new registration/grant/authorization
flow; never edit an existing grant's tenant/user. Integration status/enablement must be rechecked when
consent is accepted and the code is redeemed. A code never supplies arbitrary user/tenant authority.

The scope ceiling and consent cover **only the existing eight-field minimized active tenant Case set**
that the acting user can read through Phase 1S. This explicitly includes same-tenant unassigned Cases;
assignment is not an ACL. No new per-case visibility rule or admin override is introduced. If the tenant
requires selected-Case/assignment-only delegation, this pilot is unsuitable and stays disabled: do not
accept a case restriction and then ignore it, filter after paging, or infer access from Case Team roles.
Later per-case grants require authoritative SQL predicates before offset/probe and independent design.
Future resource policies must automatically restrict these shared reads; integration scopes cannot defeat them.

```text
allow = product capability enabled
    AND tenant exists/active AND tenant AI enabled
    AND integration active/unexpired/same tenant
    AND credential/session valid/unexpired/unrevoked and bound to issuer + resource + client
    AND grant live/unexpired with immutable same tenant + integration + acting user
    AND required scope in (product pilot allowlist ∩ registration ceiling ∩ grant scopes ∩ token scopes)
    AND acting user currently eligible in that tenant
    AND current Shale operation/resource/field policy permits the read
    AND read-only allowlist + bounds + quotas + required audit persistence succeed
```

Any missing/unknown/failed element denies. Scopes only reduce authority. Initial exact case-sensitive ASCII
scope values are **`cases.search`** and **`cases.overview.read`** (at most these two, OAuth space-delimited
representation). Neither implies the other. No `read_all`, `database_read`, `admin`, wildcard, write scope,
service account, offline export or all-powerful AI user. Unknown requested scopes are rejected, not coerced.
Increasing a registration ceiling never silently expands an existing grant/token; fresh explicit consent
is required. Reduction is immediate through live intersection, even if old token scope text remains broader.

### 10.3 OAuth profile and credential boundary

Recommend a **Shale-controlled authorization server logical component in `shale-server`**, using a maintained
OAuth implementation, and a separate MCP resource-server validation path. Standards profile: OAuth 2.1-style
authorization code, S256 PKCE for public **and** confidential clients, RFC 8707 resource indicators,
RFC 9728 protected-resource metadata, RFC 8414 AS metadata, RFC 9207 issuer response binding, RFC 7009
revocation and rotating refresh tokens following RFC 9700. OAuth 2.1 is still a draft, not a final RFC.
No implicit/password/client-credentials grant, token exchange, third-party access-token pass-through or
unattended delegation. Shale's ordinary login can authenticate the human at consent; its bearer is never
accepted as an MCP credential or passed to the AI. No desktop password/remembered/SQL credential reuse.

| Property | Concrete pilot rule |
| --- | --- |
| Issuer | Exactly one configured absolute HTTPS issuer per deployment, proposed `${publicOrigin}/oauth/mcp`; metadata and records agree byte-for-byte, no trailing-slash aliases or caller Host/forwarded-host derivation. Separate dev issuer and token namespace. |
| Audience/resource | Exactly configured `${publicOrigin}/mcp` canonical URI, supplied at authorization and token exchange and bound in durable token records. Reject absent/other/multiple resources; ordinary REST is a different audience. |
| Access token | Opaque CSPRNG ≥256-bit bearer, lifetime **5 minutes**, clipped to credential/session/grant/registration expiry. Store SHA-256 digest and issuance/expiry, not raw token. It has no trusted caller-decoded JWT claims; issuer/audience/subject/client/scope are server record fields. Never accept ordinary HS256 JWTs on MCP. |
| Subject/client | Subject is the durable grant's same-tenant Shale user, not email/model name. Token records independently carry issuer, resource, OAuth client ID, IntegrationId, GrantId and delegated SessionId. Verify the complete relationship, not a client assertion of userId. |
| Authorization code | CSPRNG ≥256 bits; hash-only storage; **60 seconds**, single-use atomic consume, bound to authorization transaction/user/grant/client/redirect/resource/S256 challenge. `state` and issuer response checks prevent CSRF/mix-up; no open redirects. |
| Refresh | Opaque ≥256-bit, hash-only; single-use atomic rotation. Session/grant absolute maximum **24 hours** from consent; refresh idle maximum **8 hours**, never slide the absolute deadline. No `offline_access` scope. Reused consumed refresh credential revokes its entire session family and all its access tokens; concurrent refresh has one winner, no retry grace. Client must recover through new consent if a rotation response is lost. |
| Registration | Manual tenant-admin preregistration only; expires in at most **90 days**, requires explicit renewal. Name ≤100 UTF-16 units, no arbitrary metadata, at most two scopes and five exact approved redirect URIs. A renewal never restores revoked grants. |
| Public client | `token_endpoint_auth_method=none`, no embedded shared secret. S256 PKCE proves code possession, not machine trust. Local AI is normally this profile. |
| Confidential client | `private_key_jwt`, pre-provisioned/pinned public keys, RS256 only for pilot, RSA ≥2048 bits, exact token-endpoint audience/client issuer+subject, assertion ≤60 seconds, unique JTI consumed in a shared replay store. No fetch of caller `jku`, `x5u` or arbitrary JWKS URL. Client key lifetime ≤90 days. Hosted clients can instead use the public profile if appropriate; a client secret never substitutes for user consent. |
| Redirects | Hosted HTTPS URIs exactly matched, no wildcards/fragments/open redirect. Native public clients may register literal loopback IP callback with ephemeral port under RFC 8252: exact IP/path, only port varies. This exception is for delivering an OAuth code to the browser's client callback, never for serving Shale MCP/data without TLS/auth. |
| Rotation | Register replacement key explicitly, maximum 24-hour planned overlap then revoke old key. Every session records its authenticating key ID (null for public clients); key revocation invalidates those sessions/access tokens immediately. Emergency revoke has no overlap. Public-client compromise requires session/grant or registration revocation, not pretending client IDs are secrets. |

Opaque tokens deliberately avoid relying on long-lived self-contained claims or current WEB signing keys.
For this co-hosted AS/resource server, validation is an internal bounded lookup rather than a network
introspection call. Hash the presented opaque token, perform a **new confined bootstrap resolver** of only
that digest under the configured issuer/resource, derive the owner tuple, then revalidate it through a freshly
initialized tenant runtime connection. Bootstrap returns no Case data, password/hash, secret or general User
lookup. Its SQL permission/ownership model must be reviewed/tested like V1/V2 auth modules, separately;
never give the general auth pool unrestricted integration/grant tables to make opaque lookup work. Context
from this lookup is provisional until the live relationships/tenant/user are revalidated. Resolver outage,
ambiguous digest or missing policy fails closed. No alternative issuer lookup, unsigned tenant prefix,
development-header fallback or ordinary bearer resolver fallback is permitted.

Advertise RFC 9728 at `${publicOrigin}/.well-known/oauth-protected-resource/mcp` with
`resource=${publicOrigin}/mcp`, exactly one `authorization_servers` issuer and the two `scopes_supported`.
Serve RFC 8414 at `${publicOrigin}/.well-known/oauth-authorization-server/oauth/mcp`, with exact issuer,
fixed authorize/token/revoke endpoints under `/oauth/mcp`, `code_challenge_methods_supported=["S256"]`,
`response_types_supported=["code"]`, `grant_types_supported=["authorization_code","refresh_token"]`, and
`token_endpoint_auth_methods_supported=["none","private_key_jwt"]`. No registration endpoint or ID-token
claim is advertised. These are future metadata paths only. Metadata discloses fixed product information;
401 includes the resource metadata challenge. Emit `iss` in authorization responses and advertise
`authorization_response_iss_parameter_supported=true`. No tenant names/enablement/client inventory in
metadata. Pre-registration is a supported MCP path. Defer dynamic registration and Client ID Metadata
Document fetching: the latter is a current MCP SHOULD, not a MUST; the pilot explicitly declines the
extra SSRF/trust surface and does not advertise it. DCR is deprecated in the inspected revision.
Clients requiring automated registration or unsupported auth profiles are not pilot-compatible; do not
weaken validation to support them. OAuth endpoints need authentication-attempt limits, CSRF, secure
HttpOnly/SameSite consent cookies, no-store, bounded request parsing, and no password/token/payload logs.
Ordinary REST filters must reject MCP credentials, including mutations, auth refresh and admin routes.
MCP must reject WEB/DESKTOP/MOBILE, legacy unbound JWTs and remembered credentials in every profile.

Dependency choice is implementation work: first evaluate maintained Spring Authorization Server/Spring
Security and the official MCP Java SDK against the actual Boot 3.3.4/JDK21 baseline. No framework or
version is added now. Pin maintained compatible versions in 3B.2/3B.4 and run existing REST/session regressions;
if this requires a Boot upgrade, make it a separately reviewed prerequisite. Lack of library support for the
specified profile blocks that increment; it does not permit hand-written OAuth or silent protocol downgrade.

**Replay limit:** PKCE/code consumption, refresh-family reuse detection and private-key assertion JTI
uniqueness resist those replays. A stolen valid access **bearer can be replayed until revoked/expired**;
TLS, five-minute expiry, live state and shared quotas bound exposure but do not prove sender possession.
DPoP/mTLS is not assumed compatible with all pilot clients and is deferred. If sender-constrained access is
a tenant's requirement, that tenant cannot enable this bearer pilot. Never claim prompt instructions or
localhost location supply replay protection.

### 10.4 Transport, discovery and structural read-only enforcement

Future route: **client outbound HTTPS POST `${publicOrigin}/mcp`**, Streamable HTTP 2026-07-28, in the
existing `shale-server` deployment behind the approved TLS reverse proxy. Both a hosted AI and a local AI
connect to Shale; Shale does not connect back to arbitrary client URLs or need inbound access to the local
AI machine. Same non-bypass runtime pool/application workers, separate OAuth/MCP route security; no second
backend, AI-to-SQL/DAO/REST-controller path, exposed JDBC port or new storage service.

Choose single buffered JSON responses; no protected streaming/progress chunks, GET SSE endpoint, protocol
session ID, resumable result, subscription, sampling, roots, elicitation, task execution, resource or prompt
capability. Reject unsupported methods; in this revision GET/DELETE return 405, and no `initialize`
handshake is offered. Respect required `MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name` and per-request `_meta`
validation, including header/body equality and `HeaderMismatch`/unsupported-version errors. ClientInfo/name
is untrusted presentation, never IntegrationId. No `x-mcp-header` annotations on query/Case inputs; edge logs
must not copy Case/query/token fields. Standard MCP metadata is not an arbitrary extension of tool arguments.

Every POST validates authentication, even discovery. Unauthenticated callers receive 401 and **no tools**;
authenticated but unregistered/revoked/disabled/undelegated requests receive safe 401/403 and **no catalog**.
An eligible registration + live user grant/session sees only exact tools whose scope is present in the
live intersection. A search-only grant sees only `search_cases`. Recompute on every `tools/list`, no list
cache or list-change stream. Current user account eligibility gates discovery; operation/resource-specific
permissions are checked on invocation, without per-Case discovery or permission-revealing catalog variants.
Discovery is never proof of invocation authority. Metadata can be public while global capability is
configured; when capability is off no MCP data route is mounted. `server/discover` is mandatory and returns only fixed supportedVersions/capabilities/serverInfo,
with the same auth gate and no tenant inventory. Do not advertise unsupported capabilities or cache grants.

Use an explicit dispatch map with two handlers that depend only on a narrow query facade. Do not inject a
generic service registry, broad mutation gateway, HTTP client/URL fetcher or database provider into a handler.
The application boundary accepts immutable verified integration context and enforces the two scopes even for
direct non-protocol callers. Missing integration context cannot downgrade an integration invocation to web.
Only dedicated ordinary-user composition can omit it. Tool annotations (`readOnlyHint=true`,
`destructiveHint=false`, `openWorldHint=false`) document behavior; they are not enforcement. No reflective
auto-registration, REST passthrough, arbitrary tool names, SQL, dynamic field/filter expressions or hidden
mutation fallback. Required audit, quota and OAuth lifecycle writes are the sole non-domain side effects.

Validate Origin when present against a dedicated exact allowlist, Host/public-origin configuration and
trusted proxy forwarding; reject invalid Origin with 403. Missing Origin is normal for nonbrowser clients
and grants no authority. Current `/api/**` CORS is not MCP configuration. Never wildcard credential/origin
access. Local development may use a separate synthetic-only HTTPS loopback instance/issuer, same OAuth
and quotas, globally off by default. No stdio/HTTP/no-auth MCP mode or X-Shale identity headers, even in dev.
Production must never use dev/local profiles, cleartext MCP, loopback trust, SQL bypass, permissive Origin,
token-query parameters or proxy/body debug logging. Local placement changes connectivity, not authorization.

### 10.5 Audit representation and disclosure transaction

**Decision: supplement existing audit infrastructure with a narrow `IntegrationAccessAudit` event family.**
Do not add READ/FAILED to `EntityActionAuditEvent` as if they were mutations; do not classify Case reads as
`AdministrativeReadAuditEvent` instance-list types or session revocations. Reuse core/data audit ownership,
strict tenant isolation, insert-only runtime API, sanitization, bounded admin review and transaction patterns.
Keep the existing successful Overview PHI event exactly once; no extra PHI success row just for AI provenance.
This is a proposed scoped schema/service enhancement for 3B.3, not supported by today's allowlists.

Each verified invocation produces one bounded parent event with:

| Field | Closed/bounded treatment |
| --- | --- |
| Identity | Internal bigint event key, public UUID InvocationId; tenant int, actor int, IntegrationId UUID, GrantId UUID, delegated SessionId UUID; issuer/resource contract identified by configured deployment, never raw client text. |
| Time/source | Database UTC `OccurredAt`; server-generated UUID CorrelationId shared across adapter/worker; fixed source `MCP`; contract `case-read-v1`. Ignore caller correlation as authority; do not persist raw JSON-RPC IDs. |
| Operation | Exact tool enum `search_cases` / `get_case_overview` and corresponding shared-operation enum. Unknown tool becomes `UNSUPPORTED_TOOL` with no copied name. |
| Outcome | `ALLOWED_READ`, `DENIED`, `INVALID_INPUT`, `UNAVAILABLE`, `THROTTLED`, `CANCELLED`; closed reason code, no exception/SQL. ALLOWED_READ means authorized server read committed, not confirmed AI/human delivery. |
| Volume | Returned result count (0–10); optional page (0–9) and fixed size 10 for search; no query/query hash/filter text, prompts, DTOs, token/JTI/hash, URLs or arbitrary metadata. |
| Resource | Typed child rows `(tenant, event, CaseId, relation)` with relation `RETURNED` or `REQUESTED`. Search success records only ≤10 returned Case IDs, no probe row; Overview success records its one Case ID. No serialized/unbounded ID list. |

Typed resource rows are a deliberate refinement of §5's operation-level search event: they let an authorized
reviewer answer “which Cases left the shared operation?” without storing names/PHI payloads. Empty search
still gets one parent event and no child. For failed Overview, record syntactically valid requested ID as
`REQUESTED` only, without existence/foreign-owner lookup; it is not a successful disclosure fact. Malformed
IDs produce no resource row. Resource rows do not FK to Cases (a foreign probe must not cross-link or expose
existence); same-tenant returned Case policy is checked by the authoritative read. Parent count matches
RETURNED children. At most ten children total per event. No raw metadata column is proposed.

| Tool | Persistence required before protected result? | Existing behavior retained |
| --- | --- | --- |
| `search_cases` | **Yes**, including zero matches. Parent + bounded returned-resource children commit before return from the shared operation. | Ordinary REST/desktop summary exemption stays unchanged; only verified integration context selects this additional mandatory path. |
| `get_case_overview` | **Yes**, integration parent + one returned child + existing required `Case.Overview.Read` PHI append in **one connection/transaction**, commit before returning DTO. | Existing PHI event and no-disclosure-on-append/commit failure preserved. |

Required future worker sequence: open fresh tenant/user initialized runtime lease → begin transaction →
verify live integration/grant/session/user/scope and bounds/quota reservation → execute the **same existing
minimized query** → append existing PHI event if Overview, and integration event/children → final live
policy/version check under transaction locks → commit → close successfully → release DTO to trusted
encoder. The current search worker is autocommit/audit-exempt and Overview internally commits; they need a
scoped context-aware extension inside the existing transaction owner. **An outer MCP wrapper around today's
independently committed methods cannot provide this atomicity.** Extend port/application context/provider
and existing worker ownership as needed, with unchanged ordinary callers. Do not duplicate queries, borrow
independent audit connections for success, suppress PHI failure, or use proxy tricks to intercept commit.

Integration InvocationId on the success parent makes the atomic group identifiable. Existing PHI append
returns void and has no correlation column; do not claim exact row linkage through timestamps. The required
atomic group proves both writes occurred; exact PHI row linkage, if later required, needs a separately
verified append-result/schema extension, never PHI StringValue metadata abuse.

Read/append/zero-row-insert/commit/connection-close failure returns **no protected result**, attempts rollback
where possible and emits safe UNAVAILABLE. A lost commit acknowledgment may leave ALLOWED_READ/PHI rows;
retain them, label server-access uncertainty operationally, never delete them or automatically replay the
read. Cancellation/deadline after commit likewise does not undo access history. Encoder then constructs one
bounded immutable byte buffer, rechecks live authority immediately before handoff (§10.6), and discloses no
partial text/structured result on serialization/size/policy/audit failure. A terminal failure after committed
ALLOWED_READ may add a second typed outcome event linked by InvocationId; do not rewrite the committed event
or imply it proves delivery. Unique `(InvocationId, event stage)` prevents accidental duplicate stage writes.

Denied/invalid/unavailable/throttled verified attempts append their outcome in a **separate short transaction**
after the read transaction rolls back. Reuse the verified owner tuple, permitting audit of now-disabled users/
integrations through a confined audit append; eligibility to read data is not eligibility to erase failure
history. No success record or result. If failure-event persistence also fails, continue withholding data and
emit a fixed operational audit-outage signal, not an unaudited retry or sensitive emergency spool.
Unauthenticated/unresolved-token failures cannot safely acquire a tenant; only sanitized bounded aggregate
security telemetry is allowed, with no invented user/integration attribution. Apply edge limits before
these failures can fill storage. Pure token predicates, health and schema discovery intentionally have no
per-call PHI/access event; security counters suffice. This exception never covers tool data reads.

Registration/enablement/ceiling/rotation/disable/revoke and consent/revoke are meaningful administrative/
security mutations. 3B.1 must extend reviewed entity-action vocabulary with `TENANT_AI_POLICY`,
`AI_INTEGRATION`, `AI_INTEGRATION_CREDENTIAL`, `AI_DELEGATION_GRANT`, using existing CREATED/UPDATED/
ACTIVATED/DEACTIVATED/REMOVED actions and typed ID/state-only keys (no keys/secrets/redirect/name strings).
Internal bigint entity IDs allow existing audit EntityId semantics while public IDs stay UUIDs. Grant creation
records the consenting user, administrative changes the actual admin; never a Shale/AI pseudo-user. Routine
code/access issuance and refresh use the intrinsic durable lifecycle; refresh-reuse detection and explicit
session revocation need a narrow integration-security event in the same event family, not a mislabelled
WEB session-security row. Define closed `REFRESH_REUSE`/`SESSION_REVOKED` reasons and identity-only fields.
All administrative/security mutations and required audits commit/roll back together; quota reservations are
operational accounting and not entity-action events. Global kill-switch changes reuse `GlobalControlPlaneAuditLog` under restricted operator change control,
with a reviewed `AI_PRODUCT_CAPABILITY` entity/action vocabulary and only enabled/revision state,
on the capability-change connection before commit. The existing release importer demonstrates this
operator/global audit pattern; do not fabricate a tenant event or reuse its release-control token for AI authority. No sensitive arbitrary audit blob or model conversation logging.

Audit review must revalidate active same-tenant admin in service/SQL, bound pages (≤100), use strict RLS and
safe typed rendering; no generic dump endpoint. Append-only and admin-only SQL capabilities must be proven
with actual non-bypass grants/denials. The current PHI viewer has known admin/logging/grant gaps (§2.5/§5);
do not route integration review through that unverified DAO. Initial protected operator review may precede
UI, but must demonstrate reconstruction of time/tenant/user/integration/tool/Case/outcome/correlation.
Propose the existing session-history seven-year retention target, subject to explicit tenant/legal sign-off
before real-data pilot. No automatic purge, cascading deletes or retention claim is implemented here.

### 10.6 Revocation, races, async and pooled context

| Change | Effective rule |
| --- | --- |
| Global capability or tenant AI disabled | No new token/refresh/consent, discovery or invocation; queued work and buffered results fail their next gate. Use a default-off startup flag plus a shared authoritative global capability row (§10.8). Global off is observed on every gate across replicas; acknowledge disable only after its audited control-plane transaction commits. The startup flag can only further deny. |
| Integration suspended/revoked/expired | All grants/sessions unusable immediately on committed live check; no broad token cache. Reenable/renew does not un-revoke descendants. |
| Confidential credential revoked | Sessions bound to that key unusable, no waiting for access expiry; replacement needs a valid key and new session. |
| Grant/session revoked/expired | No invocation/refresh; first revoke timestamp/reason preserved. Owner user or tenant admin can revoke; each session independently revocable. |
| User disabled/deleted/removed | Current eligibility denies on every gate even if new integration sessions were not swept by today's WEB session revoke code. Future disable/remove/reset transaction should revoke integration sessions too; active-user joins are mandatory race defense. Re-enable never resurrects revoked grants. Administrative password reset is SECURITY revocation for all integration sessions. |
| Scope reduced | Intersect live registration/grant revision with token scopes; required scope missing denies next check across replicas. Added scope requires fresh consent; reduction never waits for token expiry. |
| Tenant removed/inactive | Deny independently of retained registration/history. No migration of a grant to another tenant. |

No positive authorization cache in pilot. Use authoritative SQL reads on admission, after any queue wait,
inside the shared read transaction before audit commit, and at final buffered handoff. Recheck token/session
expiry with database UTC; no skew extension for opaque access expiry. Locks/version predicates must prevent
concurrent reduction/revocation from slipping past an already stale check. The final short gate acquires
shared authorization-row locks in a fixed order (global capability → tenant policy → integration → grant → session → user),
validates versions/expiry and authorizes a single buffer handoff; mutators take conflicting update locks in
the same order. Do not hold a SQL transaction while waiting for network transmission. A revocation committed
before that gate prevents disclosure; a handoff authorized first can finish and cannot be recalled. This is
the precise concurrency boundary, not a promise to retract bytes already handed to the transport. Expiry or
outage while waiting for the gate discards the buffer. SQL lock wait is included in the deadline. Implement
and test this handoff boundary before claiming revocation of in-flight requests.

One immutable server-created invocation object contains tenant/user, IntegrationId/client ID, credential
reference, GrantId, SessionId, intersected scopes, policy versions, tool/operation, InvocationId/CorrelationId,
deadline and quota reservation. No tokens/email/prompt data in `toString`, logging or audit context. Protocol
metadata may not mutate it. Submit it explicitly to a bounded executor; no inheritable ThreadLocal, AppState,
servlet object, shared mutable RuntimeSessionService or per-connection remembered grant. Clear transient
carriers in finally; cancellation does not donate context to the next task. No detached/background Case
reads; retries are new authorized/quota-accounted/audited invocations, never automatic transport replay.

Borrow runtime connections only after identity proof. Every lease stamps **both existing mutable SQL keys**
through the production initializer; integration/grant context stays in the immutable application object,
not extra pooled SQL keys. Worker rechecks SQL tenant/user equality and current authority. Failure of either
stamp closes/fails before any Case/audit SQL; do not reuse partly initialized connections. Transaction
failure must roll back and restore/discard pool state; lease close failure withholds results. Test single-SPID
A1 → A2 → B1 → A1 reuse, null/mismatched keys, concurrent async interleaving, queue cancellation, and setup
failure followed by clean borrow. Do not add `@read_only=1` based on documentation examples without pool
compatibility review. No auth/global pool for tenant reads and no global pool identity impersonation.

### 10.7 Bounds and extraction policy

These are **maximum pilot ceilings**, with lower operator-configured limits permitted. Missing/invalid
configuration or unavailable shared quota authority denies. Increases require a reviewed contract change
and workload evidence; they are not a tenant/model-selectable setting. Existing REST limits remain unchanged.

| Dimension | Pilot maximum |
| --- | --- |
| Search input | Required literal Case-name string; strip surrounding whitespace using existing Java semantics, 3–100 UTF-16 units, at least three Unicode letters/digits. No wildcard language, browse/all/blank search, fields/sorts/regex/SQL/URL. Existing literal `%`, `_`, `[` escaping remains authoritative. |
| Search page/window | Fixed size **10**, page **0–9**, at most 11 fetched rows including probe; at most 100 returned positions per normalized query window. No caller size, no total/export, no server follow-next loop. Page 9 may have hasMore=true: `windowExhausted=true` requires narrowing/stopping. Offset pages are not snapshots. |
| Cumulative extraction | Reserve worst-case disclosed rows before reading: 10 for search, 1 for Overview (even empty/error/repeat). Trailing 24-hour budgets: **200** per `(tenant,user)` across all integrations, **500** per `(tenant,integration)` across users, **1000** per tenant. Repeated IDs/queries/pages count again; grants/tokens/refresh/new registrations cannot reset user/tenant budgets. No credit refund after failure/uncertain commit. |
| Call rates | Sliding 60 seconds: integration **20**, `(tenant,user)` **10** across clients, tenant **60**; all authenticated tool attempts including denial count. Discovery ≤10/minute/session and under the tenant/integration request budget. Unauthenticated/auth endpoint attempts have edge/IP limits of 10/minute/IP for token/consent starts and 60/minute/IP for MCP rejects, plus global load shedding; IP is abuse mitigation, never identity. |
| Concurrency | At most **2/integration**, **1/(tenant,user)**, **5/tenant**, **5/server process** active tool reads; immediate rejection at admission, no data-work queue. Count each scope simultaneously; distributed leases across replicas. Crash leases expire after at most 15 seconds, do not reset counters. |
| Fanout | One tool invocation per JSON-RPC request, one selected shared operation, no batching, auto-pagination, N+1 hydration, recursive tool calls, server sampling or multi-tool orchestration. Probe and mandatory policy/audit/quota statements are internal bounded work, not extra data tools. |
| Bytes | Request body ≤8 KiB decoded/uncompressed, no request compression; bounded JSON depth 8, duplicate keys rejected, string JSON-RPC ID ≤64 ASCII chars. Search structured output ≤32 KiB; complete JSON-RPC body including optional duplicate text ≤64 KiB. Overview structured output ≤8 KiB; complete body ≤32 KiB. Errors ≤4 KiB. Check exact delivered UTF-8 bytes, no truncation/partial results. |
| Time | **10 seconds** end-to-end from admission through final buffer handoff, including pool/locks/auth/audit/encoding. Existing data statements ≤5 seconds, further clipped to remaining deadline; all new security/audit/DB work bounded too. Pool acquisition, executor admission and cancellation must honor remaining time (existing Hikari timeout alone does not meet this contract). No protected response after deadline. |
| Cursor | None in pilot; no reusable bearer cursor or cursor lifetime. Page arguments never grant access or reset extraction budgets. Later cursor design requires expiry and tenant/user/integration/grant/filter/schema-version binding. |

Maintain atomic shared SQL admission/reservation records (or a separately approved equivalent with identical
cross-replica/restart semantics), in deterministic scope-lock order, before data work. Counters are based on
server time, not client timestamps; reserve against **all** applicable budgets atomically. Durable reservations
retain enough time buckets/entries for exact trailing windows and crash recovery. No process-local limiter as
the sole authority, no reset on restart/refresh/re-consent. Complete/release concurrency leases in finally;
consumed call/row budgets remain. A shared budget failure returns safe throttling with bounded retry-after,
no result. Lease loss/expiry prevents final handoff. Failures can exhaust budgets deliberately: this is a safe
availability tradeoff, recorded in monitoring, never a reason to disclose without reservation.

Per-query page ceilings alone do not prevent varying queries into a tenant dump; aggregate row budgets do
bound that extraction rate. They cannot make authorized disclosures uncopyable or prevent slow extraction
over days. Tenant/user consent, audit review, revocation and conservative limits are required residual controls;
a bulk export capability is explicitly not offered. Representative SQL plans, worst-case Unicode/escaping
sizes, limit races and latency must pass acceptance before any pilot with real data.

### 10.8 Proposed persistent model (not schema implementation)

Use existing conventions: internal bigint IDENTITY where appropriate plus random public UUIDs, required int
ShaleClientId, UTC datetime2 lifecycle, RowVer for mutable state, trusted non-cascading tenant-qualified
relationships, strict `sec.TenantFilter`/`sec.fn_FilterByTenant` FILTER and INSERT/UPDATE blocks plus explicit
SQL predicates. **Names below are proposed**, not verified existing tables/columns. Before DDL, inventory the
approved authoritative schema/constraints and current security modules; do not append these to the schema
reference as deployed facts. This phase creates no SQL migration, seed, enum, credential or registration.

| Proposed entity | Keys, ownership and contents | Lifecycle/concurrency; authority and audit |
| --- | --- | --- |
| `AiProductCapability` | Global singleton numeric key, Enabled default false, Revision, updated UTC and RowVer; no tenant or secret. | Restricted authenticated product-operator control only; existing GlobalControlPlaneAuditLog records the change on the same connection/transaction under a reviewed AI capability vocabulary. Runtime may read only this fixed capability, never mutate it or use global authority to read tenant data. No tenant RLS on this deliberately global singleton; missing/unknown row=false. Live shared gate plus default-off startup configuration avoids stale per-replica enablement. Separate from release-channel ApplicationPolicy. |
| `TenantAiIntegrationPolicy` | One row per existing tenant; internal numeric ID, unique ShaleClientId, Enabled default false, Revision, updated UTC/by admin, RowVer. No secrets. | Missing=false. Active same-tenant admin changes with expected RowVer and entity-action audit on same transaction. Disable increments revision; reenable does not revive grants. Strict tenant RLS; no overlay. Product capability is the separate restricted singleton above, default false; do not misuse global release-channel ApplicationPolicy as AI permission. |
| `AiIntegrationRegistration` | Internal numeric ID + unique IntegrationId UUID; tenant, issuer-bound unique OAuth client ID, bounded Name, PUBLIC/CONFIDENTIAL type, ACTIVE/SUSPENDED/REVOKED, two-scope ceiling, exact redirect allowlist (≤5 typed entries), CreatedAt/By, ExpiresAt, RevokedAt/By/closed reason, policy revision, RowVer. No provider enum or secret. | Active same-tenant admin registers/manages; immutable tenant/identity. No automatic registration, global tenant wildcard or transferable client ID. Soft revoke retains history, first revocation immutable; suspended may resume but old revoked grants stay revoked. All meaningful changes entity-action audited. Expiry/ceiling rechecked live. |
| `AiIntegrationClientKey` | Internal ID + public CredentialId UUID; tenant-qualified registration, key ID, pinned algorithm/public key material, validity interval, first revocation/by/reason, RowVer. No private key/client secret stored. No row required for public client. | Admin provisions/rotates/revokes, proof of key possession at token endpoint. At most two concurrently active keys per registration during ≤24h overlap; optimistic row version plus transaction lock prevents excess keys. Keys are public but never copied to general audit. Entity-action audit contains credential reference/state only. Revoked keys retained. |
| `AiDelegationGrant` | Internal ID + GrantId UUID; tenant-qualified integration/user, two-scope grant set, explicit minimized-tenant-Case policy version, consent UTC, maximum 24h expiry, revoked UTC/by/reason, revision/RowVer. | Only authenticated user consent creates; user owns revoke, same-tenant admin may revoke/reduce, never grant as user. Immutable owner tuple, no restoration of revoked grants. Multiple sessions share a live grant only within original consent deadline; no automatic grant expansion. Consent/revoke/reduce audited transactionally; no prompt/consent free text. |
| `AiDelegatedSession` | Internal ID + SessionId UUID; tenant-qualified grant/integration/user, optional authenticating client-key ID, issuer/resource identity, issued/absolute expiry/last refresh/idle deadline, current refresh generation, revoked UTC/reason, RowVer. | Distinct from UserSessions: OAuth family/client/grant binding and refresh lifecycle do not fit current WEB/DESKTOP/MOBILE semantics. Intrinsic lifecycle records routine issuance/rotation; explicit revoke/reuse gets integration security event. Conditional rotation, fixed lock order and first-revoke semantics reuse existing patterns. No ApplicationInstance/desktop-machine coupling. |
| `AiOAuthCredential` | Internal ID; tenant-qualified session or authorization transaction, kind CODE/ACCESS/REFRESH, unique SHA-256 token digest, issuer/resource/client/grant/user binding, scope snapshot, issued/expires/consumed UTC, generation/family and revoked marker. Code additionally holds redirect + S256 challenge binding; no raw token, verifier, password or private key. | CSPRNG secrets only returned once, after durable commit. Atomic code consume/refresh rotate; consumed refresh digests retained until session absolute expiry to detect reuse. Only live access digest accepted. Revocation checked through session/grant even if credential row not individually swept. Expired credential cleanup cannot erase lifecycle/audit or active replay defenses. Token issuance after lost commit ACK returns no token and requires explicit recovery; no automatic reuse. |
| `AiClientAssertionReplay` | Tenant/integration/client-key + digest of assertion JTI, expiry ≤60s; unique per issuer/client/key/JTI, no assertion/private material. | Atomic insert rejects replay across replicas; automatic expiry cleanup only after assertion can no longer validate. Internal OAuth service owns writes, no admin/UI access or per-assertion entity audit. Same strict RLS after confined client registration bootstrap. |
| `AiQuotaReservation` | Tenant, integration, acting user, public InvocationId, admitted UTC, fixed row-cost, call kind, lease deadline and completion state. Index by each aggregate owner/time; immutable consumed budget facts plus short concurrency lease. | SQL admission serializes all relevant tenant/integration/user scopes in fixed order; scope lock/index strategy verified in 3B.1. Unique invocation reservation, no client-chosen identity/idempotency key. Retain ≥24h plus lease margin; bounded cleanup may remove only expired accounting, never access audit. No RowVer required for immutable entries; conditional lease completion. Internal service only, no secrets/query/result IDs, no per-reservation entity-action audit. |
| `IntegrationAccessAuditLog` | Internal ID, public InvocationId + stage key, tenant/actor/integration/grant/session, CorrelationId UUID, DB UTC, source/contract/tool/operation/outcome/reason enums, count/page/size. Security lifecycle subtype for explicit session revoke/refresh reuse has nullable tool/operation and no result fields. | Append-only, no update/delete runtime API; unique invocation/stage, immutable identity/time. Tenant-qualified historical identity FKs must allow inactive/revoked owners; no cascading deletion. Tenant-admin protected bounded review. No RowVer for immutable history, no token/hash or arbitrary metadata. |
| `IntegrationAccessAuditResource` | Composite `(tenant,event,CaseId,relation)`; ≤10 rows/event, typed REQUESTED/RETURNED; tenant-qualified parent FK only, no foreign-target Case FK. | Same transaction as parent; append-only/no cascade purge. Service and schema enforce count/shape invariants; no names/PHI text. Same strict RLS and admin review as parent. |

Scopes can be represented internally by a checked two-bit set (0–3), mapped bijectively to the two public
strings; no separate role/permission/scopes table is needed for two fixed values. Redirect URIs are typed
bounded registration configuration, not a general metadata bag (normalized child rows if needed for exact
comparison/uniqueness). Public identifiers and internal entity-action numeric IDs must map through the same
tenant-qualified registration, never a globally unqualified ID lookup. User/tenant/registration history is
retained through revocation; no hard-delete cascade. Tenant user transfer creates new authority, not FK edits.
Secrets exist only in client/server transport memory long enough to hash/issue; no raw credential SQL storage,
logging, crash-report DTOs or redisplay. SHA-256 is appropriate for random high-entropy tokens, not passwords;
Shale password verification remains BCrypt.

Two pre-tenant lookups are necessary implementation prerequisites: opaque credential digest → provisional
owner, and preregistered client ID → bounded OAuth registration/redirect/public-key configuration. Both must
use reviewed confined modules with no general table SELECT or caller-selected tenant/user, restore caller
context, and enforce exact issuer/resource, limits and ambiguity rejection. The trusted module execution
exception must be inventoried separately from tenant runtime RLS, as for existing V1/V2; **no new global
runtime bypass**. Persist consent authorization transactions/cookie state with the maintained AS store,
bounded ≤5 minutes, CSRF/PKCE/resource/client binding and single-use state; do not build another user table.
Where AS library persistence already safely provides code/token/replay/transaction semantics, adapt it to
these entities rather than duplicate its store. Library defaults retaining plaintext tokens are unacceptable.

The global capability singleton requires separately confined control-plane write/read grants, not a tenant bypass.
All new tenant tables require explicit strict RLS and non-bypass local SQL tests, including BLOCK behavior,
indexes, tenant-qualified FKs where schema permits, admin checks and wrong-context inserts. Where existing
parent composite keys are unavailable, 3B.1 must add a reviewed compatible key or enforce proven same-tenant
validation on the same transaction; no guessed FK shape. Schema drift fails migration; no auth fallback.
Administrative action/source/correlation and audit viewer extensions require their own additive reviewed
allowlists, not edits to historical migrations. Install/rollback preparation belongs to 3B; no production
execution is authorized by this proposal.

### 10.9 First two tool contracts

Contract name **`case-read-v1`**, protocol **2026-07-28**; these are proposed wire schemas, not endpoints.
Descriptions are fixed client-visible strings below. Both require a currently eligible acting Shale user,
matching immutable tenant/integration/grant/session, corresponding scope and all gates above. Tenant is
implicit and cannot be selected. Only active same-tenant Cases are eligible, including closed-status Cases
that are not soft deleted. A guessed ID is never authority. No dates/narratives/children/details expansion.

The following is the exact reusable **Case schema**. Every member is present, including nulls; objects are
closed. String schema maxLength counts Unicode code points; runtime must additionally enforce the existing
UTF-16-unit bounds at the same numeric limits. Reject invalid/oversized source fields, do not truncate.
`updatedAt` is stored local date-time text, never an instant or appended Z; reject unrepresentable data.

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["caseId", "caseNumber", "caseName", "status", "practiceArea", "responsibleAttorney", "primaryLegalAssistant", "updatedAt"],
  "properties": {
    "caseId": {"type": "integer", "minimum": 1, "maximum": 2147483647},
    "caseNumber": {"type": ["string", "null"], "maxLength": 200},
    "caseName": {"type": "string", "maxLength": 255},
    "status": {
      "type": ["object", "null"], "additionalProperties": false,
      "required": ["id", "name", "color"],
      "properties": {
        "id": {"type": "integer", "minimum": 1, "maximum": 2147483647},
        "name": {"type": "string", "maxLength": 255},
        "color": {"type": ["string", "null"], "pattern": "^#[0-9a-fA-F]{6}$", "maxLength": 7}
      }
    },
    "practiceArea": {
      "type": ["object", "null"], "additionalProperties": false,
      "required": ["id", "name"],
      "properties": {
        "id": {"type": "integer", "minimum": 1, "maximum": 2147483647},
        "name": {"type": "string", "maxLength": 255}
      }
    },
    "responsibleAttorney": {"$ref": "#/$defs/CaseUser"},
    "primaryLegalAssistant": {"$ref": "#/$defs/CaseUser"},
    "updatedAt": {
      "type": ["string", "null"], "minLength": 16, "maxLength": 29,
      "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]{1,9})?)?$"
    }
  }
}
```

The two schemas below are exact **schema composition templates**: before advertising, place the Case object
above under each outputSchema's `$defs.Case` and add the following `$defs.CaseUser` alongside it. References
then resolve locally inside that outputSchema. No external schema/network resolution and no template marker
is sent to clients. This composition avoids a duplicated field authority while specifying every emitted field.

```json
{
  "type": ["object", "null"], "additionalProperties": false,
  "required": ["userId", "displayName"],
  "properties": {
    "userId": {"type": "integer", "minimum": 1, "maximum": 2147483647},
    "displayName": {"type": "string", "maxLength": 255}
  }
}
```

#### search_cases

Purpose: bounded literal name search, not browse, export, case-number or natural-language full-text search.

```json
{
  "name": "search_cases",
  "description": "Find active Cases by a literal Case-name substring in your authorized Shale tenant. Use at least three letters or digits. Returns at most ten minimized summaries per page, pages 0 through 9. No blank browse or export. If windowExhausted is true, stop and narrow the query; do not request another page. No documents, narratives or Case Dates are returned.",
  "annotations": {"readOnlyHint": true, "destructiveHint": false, "openWorldHint": false},
  "inputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object", "additionalProperties": false,
    "required": ["query"],
    "properties": {
      "query": {"type": "string", "minLength": 3, "maxLength": 100},
      "page": {"type": "integer", "minimum": 0, "maximum": 9, "default": 0}
    }
  },
  "outputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object", "additionalProperties": false,
    "required": ["items", "page", "size", "hasMore", "windowExhausted", "correlationId"],
    "properties": {
      "items": {"type": "array", "maxItems": 10, "items": {"$ref": "#/$defs/Case"}},
      "page": {"type": "integer", "minimum": 0, "maximum": 9},
      "size": {"const": 10},
      "hasMore": {"type": "boolean"},
      "windowExhausted": {"type": "boolean"},
      "correlationId": {"type": "string", "format": "uuid"}
    }
  }
}
```

Required scope `cases.search`. Default page=0 only when absent; null is invalid. Raw query is schema-bounded
before normalization, then strip and enforce 3–100 UTF-16 units and ≥3 Unicode letters/digits (server semantic
check; JSON Schema alone does not prove it). Whitespace-only or short/wildcard-only inputs fail, never list all.
No null query/default-empty behavior copied from REST. Shared operation call is exactly
`searchMinimizedCases(normalizedQuery, verifiedTenant, verifiedActor, page, 10)`, once. Existing worker lowercase/
collation and LIKE escaping remain authority; do not reimplement search in the adapter. SQL predicate/filtering
precedes offset/probe. `items` are the same eight-field minimized DTO projection; no independent hydration.
Order name ASC, ID ASC; concurrent edits may shift pages. `hasMore` reflects the authorized eleventh row,
including page 9. `windowExhausted = (page == 9 && hasMore)`; zero matches is success with empty items and both
flags false. No total. Exactly one access parent + ≤10 returned-ID child records must commit before disclosure.
Maximum 10 items, 32 KiB structured / 64 KiB full response and shared cumulative budgets; no result caching.
Expected errors: INVALID_INPUT, FORBIDDEN, THROTTLED, TIMEOUT, AUDIT_UNAVAILABLE, PAYLOAD_TOO_LARGE, UNAVAILABLE,
plus transport authentication/version/protocol errors below. No assigned-case filter, case restrictions,
contact search, arbitrary conditions, pagination cursor or automated continuation.

#### get_case_overview

Purpose: fetch the existing minimized active Case identity/relationships, independently of prior search.

```json
{
  "name": "get_case_overview",
  "description": "Read one active Case's minimized overview in your authorized Shale tenant by Case ID. Returns name, number, status, practice area, responsible attorney, primary legal assistant and stored local update time only. This is not full Case detail and includes no documents, narratives, chronology or Case Dates. An unavailable Case has no disclosed existence or tenant information.",
  "annotations": {"readOnlyHint": true, "destructiveHint": false, "openWorldHint": false},
  "inputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object", "additionalProperties": false,
    "required": ["caseId"],
    "properties": {
      "caseId": {"type": "integer", "minimum": 1, "maximum": 2147483647}
    }
  },
  "outputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object", "additionalProperties": false,
    "required": ["case", "correlationId"],
    "properties": {
      "case": {"$ref": "#/$defs/Case"},
      "correlationId": {"type": "string", "format": "uuid"}
    }
  }
}
```

Required scope `cases.overview.read`; search scope is unnecessary. Call exactly
`readMinimizedCaseOverview(caseId, verifiedTenant, verifiedActor)` once through the shared operation with
verified integration context. Empty/missing/foreign/deleted target maps to identical CASE_UNAVAILABLE; no
foreign ownership lookup or hints. Authorized same-tenant unassigned Case remains readable under the accepted
policy. Success contains exactly one minimized Case, ≤8 KiB structured / 32 KiB full response. Both the
existing PHI audit and integration event/resource child commit on the worker connection before return (§10.5).
Expected errors: CASE_UNAVAILABLE plus all search errors. No broad getAuthoritativeCaseDetail, child/date/
Timeline assembly, external link fetch, generated summary or related resource discovery.

#### Common wire/error and privacy rules

Success is a JSON-RPC result with `resultType="complete"`, `isError=false`, `structuredContent` conforming
to the fully expanded outputSchema, and one `content` TextContent containing the serialized **identical**
structured JSON for compatible clients. Count both copies in the full-response byte budget; never add a
model-generated narrative, URL/resource link or a broader fallback string. All responses use no-store and
no protected progress/stream data. Case text is untrusted data, never executable instructions.

Authentication failure is HTTP 401 with RFC 9728 challenge; valid identity with missing scope/disabled
tenant/integration/grant is HTTP 403 (no result/catalog, insufficient_scope only when safe). Invalid token
reasons are indistinguishable. Admission throttle is HTTP 429 with bounded Retry-After (1–86400 seconds),
no tool output. Unsupported tool/invalid protocol request uses the selected MCP/JSON-RPC error vocabulary:
unknown tool -32602 without reflecting the arbitrary name; method not found -32601 with the revision's HTTP
404; malformed JSON -32700; malformed request -32600; required metadata mismatch -32020/HTTP400.
Protocol-version errors use the revision's defined UnsupportedProtocolVersion response, no downgrade.

After protocol validation, tool semantic/shared-operation errors use `resultType="complete", isError=true`,
no structuredContent or Case data, and one bounded text block containing safe JSON
`{"code":"<closed-code>","correlationId":"<server-uuid>","retryAfterSeconds":null}`.
The exact allowed tool codes are INVALID_INPUT, FORBIDDEN, CASE_UNAVAILABLE (Overview only), THROTTLED,
TIMEOUT, AUDIT_UNAVAILABLE, PAYLOAD_TOO_LARGE and UNAVAILABLE. `retryAfterSeconds` is null except THROTTLED,
where it is an integer 1–86400. Messages do not include query, Case names/IDs, hidden tenant, scopes not
already public, credentials or exception text. Worker authority denials are FORBIDDEN; missing/foreign/deleted
Overview is CASE_UNAVAILABLE; shared timeout/audit/oversize kinds map to the matching closed code. No partial
items on any failure. No automatic retries on timeout/audit/uncertain commit. Raw argument/payload/response
logging, including request-record toString and transport debug logs, must be disabled/redacted and tested.
Any output-field, bound, scope or semantic expansion requires deliberate version/compatibility review;
REST/OpenAPI remains separately owned and is not mechanically mirrored into tool definitions.

### 10.10 Concrete threat analysis

| Threat | Intended control and residual boundary |
| --- | --- |
| Stolen integration private key | Key identifies client only; cannot create another user's consent. Revoke key/session family, short assertions and live checks. A public client ID is not secret. |
| Stolen access/refresh token; replay | TLS/no logs; 5-minute access, live grant/session, refresh single-use/family reuse revoke; assertion JTI store. Valid bearer replay before revoke/expiry remains possible and consumes shared budgets. |
| Wrong audience | Exact configured MCP resource in code/token record and every lookup; MCP tokens rejected by REST and vice versa; no token transit to providers. |
| Wrong issuer / OAuth mix-up | Fixed issuer metadata/namespace, response iss validation, exact token endpoint/client binding, no caller-selected issuer/key URLs. |
| Tenant spoofing | Tenant comes only from confined token lookup + durable tuple, never tool/header; strict RLS/context and explicit predicates, non-bypass runtime. Tenant A registration cannot bind B grant. |
| Acting-user spoofing | Shale-hosted authenticated self-consent, CSRF/PKCE/code binding and immutable grant owner; no UserId argument or admin impersonation. |
| Scope escalation | Live ceiling ∩ consent ∩ token ∩ allowlist, reject unknown/writes, new consent for expansion; direct shared-operation enforcement. |
| Disabled/deleted/removed user | Current Users eligibility at every gate plus session revocation on security change; stale role claims cannot permit read. |
| Revoked integration/key/grant or tenant off | Authoritative live checks, fixed lock/version protocol and final handoff gate across replicas; no positive cache or long token-only authority. |
| Cross-tenant Case ID probe | Tenant/active predicates, independent resource policy, identical CASE_UNAVAILABLE without owner lookup; requested probe audit is not returned-resource proof. |
| Pagination/extraction abuse | 10 rows, pages 0–9, minimum literal query, fixed probe; shared trailing-day row budgets across queries/grants and per-user/tenant limits, no export. Slow authorized copying remains possible. |
| Arbitrary tool name injection | Static dispatch map; unknown names denied without reflection/log echo; discovery independent of authorization. |
| Hidden write attempt | Handler facade exposes only two queries; explicit read allowlist/scopes below protocol; MCP credential cannot authenticate ordinary REST writes. Prompts cannot change operation mapping. |
| SQL injection / raw SQL | Only literal bound query/ID inputs, existing LIKE escaping, no expressions/SQL/tool; validation before bounded shared query. No AI DB credential. |
| Arbitrary URL / SSRF | No URL arguments/tools/content fetches, no DCR/CIMD/JWKS fetching or caller redirect fetch; pinned registration/keys. Future metadata fetching needs separate SSRF review. |
| Prompt injection in input or Case text | Treat text as data; same server scopes/resource/limits/read allowlist every call. Prompt/model compliance is never a security boundary. |
| Async/pooled-context leak | Immutable explicit context, per-lease two-key stamps and checks, initialization failure closes, deadline/cancellation/final revalidation; single-SPID and concurrent isolation tests. |
| Audit failure / uncertain commit | Required same-connection append+commit withholds DTO; no partial buffer; separate failure event, safe outage telemetry, preserve committed uncertain events without replay/delete. |
| Compromised local AI machine | Same OAuth/consent, no Shale SQL/password/remembered secret, revoke sessions/grants/registration, quotas and audit. Already authorized output/refresh secrets on that machine may be stolen; Shale cannot retract it. No localhost trust claim. |
| Confused-deputy consent/redirect abuse | Exact approved redirects, PKCE/state/issuer/resource binding, explicit user consent, no client-credentials/password/token-exchange shortcut or public automatic registration. |
| Audit/availability abuse | Strict parsing/size/deadline, authenticated and edge limits, durable quota/concurrency admission before data; tenant-attributed failure records only for verified owners; safe load shedding without disclosure. |

### 10.11 Phase 3B implementation sequence

All increments retain global and tenant enablement off, no production SQL/deployment/public onboarding.
Apply repository focused → affected-selector → critical tests for actual source changes. Schema tests use
isolated approved offline input/synthetic fixtures with evidence class labelled; synthetic is not authoritative.
Completion of an increment is not permission to enable the next data surface.

| Increment | Dependencies and implementation scope | Tests / completion criterion | Rollback consideration |
| --- | --- | --- | --- |
| **3B.1 Integration persistence/security foundation** | §10.2/10.8; authoritative schema inventory. Add reviewed additive migration candidates, tenant policy/registration/keys/grants/session/credential/quota foundations, confined bootstrap resolvers, typed context, global capability/control-plane audit, audited admin command/service and self-consent/revoke boundary. No MCP route. | Same-tenant admin/self checks, wrong actor/tenant/null state, exact FKs/RLS FILTER/BLOCK/non-bypass permissions, drift/repeat/concurrency, RowVer, atomic admin audit, consent CSRF/substitution, absolute expiry and quota races/restart. Complete when default-off state and revoke/rotation/admin controls are executable/tested without disclosing Case data. Build schema/authority first, then audited controls/consent, then shared quota admission as separately reviewable subincrements. | Keep flags off; additive tables may remain unused. Retain revocations/audits, no hard-delete rollback or editing historical migrations. No deployment authorized. |
| **3B.2 Credential/authentication validation** | 3B.1; select maintained OAuth dependency with Boot compatibility proof (separate upgrade prerequisite if needed). Implement AS metadata/code+PKCE/self-consent, opaque access/refresh store, fixed issuer/resource, pinned private_key_jwt/replay and confined owner resolution/live validation. Separate route/filter composition. | Code/refresh/assertion replay, wrong issuer/audience/client/redirect/key/PKCE, expired/unbound token, all revoked layers, public clients, response loss/rotation race, REST↔MCP credential rejection including writes; existing WEB/DESKTOP/session regressions. Complete with no general data endpoint and durable lifecycle across replicas/restart. | Disable new AS surface, revoke integration families. Never fall back to WEB JWT or desktop restore; retain rows and configured token namespace. |
| **3B.2A OAuth dependency/configuration/protocol foundation — BLOCKED** | Maintained library compatibility and separate stack prerequisite (§10.14); then typed canonical issuer/resource, isolated AS composition, client mapping, exact redirect and S256-only policy. No issuance. | Resolve actual dependencies, start context, prove namespace isolation and canonical configuration/redirect/PKCE rules; publish only truthful implemented metadata, otherwise leave it absent. | Keep all AI flags off and AS endpoints absent; preserve ordinary authentication. |
| **3B.2B authorization code + S256 PKCE + consent transaction — NOT STARTED** | Completed 3B.2A; durable code transaction bound to current human/grant/client/redirect/resource/challenge, browser CSRF/state and RFC 9207. No usable access/refresh credentials. | Atomic single-use code consume, wrong/missing/plain PKCE, redirect/resource substitution, consent races and expiry; preserve WEB/DESKTOP regressions. | Disable authorization transactions; retain consent and security history. |
| **3B.2C opaque access + rotating refresh lifecycle — NOT STARTED** | 3B.2B; hash-only storage, conditional rotation, consumed-refresh reuse revokes family, five-minute access and absolute/idle clipping, pinned assertions/shared replay. | Cross-replica consume/rotation/replay and response-loss faults, expiry/revoke/key/reset races; no MCP endpoint. | Deny issuance/refresh and revoke families; no JWT fallback. |
| **3B.2D live credential validation + VerifiedIntegrationContext — NOT STARTED** | 3B.2C; exact issuer/resource/client/owner/scope binding through confined bootstrap and fresh runtime live gates. | Invalid/revoked/expired credentials, wrong owner/audience, pooled context isolation and REST credential rejection; no data route. | Disable credential resolution; retain rows and default-off capability. |
| **3B.3 AI audit attribution and shared disclosure seam** | 3B.1–2. Implement narrow access parent/children/security events, protected bounded review, immutable context-aware existing worker extensions and final handoff gate. Keep original REST exemption/Overview event. | Exact actor/integration/tool/resource/correlation; empty search, unavailable/probe denial, one PHI row; real denied INSERT, zero-row append, precommit failure/lost ACK, close/encoding/final-gate failure, audit outage, privacy sentinel tests and admin/cross-tenant review. Complete when both shared reads can withhold results under every fault without a protocol adapter. | Off flag stops all integration calls; retain committed audit. Old workers remain ordinary-web only; no partial “audit later” fallback. |
| **3B.4 MCP transport/discovery** | 3B.2–3; SDK proves 2026-07-28 compliance. Add disabled-by-default Streamable HTTP/JSON transport, fixed metadata/Origin/TLS/filter checks, strict parsing/deadlines and distributed quota admission, explicit catalog mapping (no data handlers enabled yet). | Unauth/unregistered/revoked discovery, scope-filtered catalog, metadata header/body mismatch, duplicate keys/batches, unsupported methods/versions, no GET/session assumptions, Origin/proxy/config errors, body/byte/deadline limits. Complete with no Case response and two independent conforming clients against synthetic transport. | Unmount MCP route/metadata as appropriate; no second service or REST changes. Never substitute legacy insecure transport. |
| **3B.5 search_cases** | 3B.1–4; exact expanded §10.9 schema and shared search audit path. Enable handler only in isolated test configuration. | Production port delegation once, tenant/user/context/scope direct bypass denial, literal search/order/probe, exact schema/UTF-16/Unicode bounds, page9 hasMore, empty search audit, full-envelope bytes, repeated/varied query row budgets and simultaneous reservations across replicas. Complete with synthetic data only, fail-closed audit/final gate and no unbounded pagination. | Remove search from dispatch/catalog or product off; retain histories/reservations. Never expose unaudited ordinary summary method as fallback. |
| **3B.6 get_case_overview** | 3B.5 plus preserved Overview transaction. Add sole Overview handler, not broad detail composition. | Same/foreign/deleted/missing/unassigned target, exact minimized DTO/local time, scopes independent, one shared operation/PHI audit, supplementary event atomicity, denial/commit/close/size/time faults and no child data. Complete with only bounded synthetic authorized output and all negative paths passing. | Remove handler/scope eligibility or product off; preserve original REST Overview and committed audit. |
| **3B.7 Adversarial/local acceptance** | All increments and available authoritative Case artifact; Phase 1R/1S/2 outstanding SQL/security prerequisites. Test serving server/TLS/proxy with representative local and independent hosted-compatible clients, non-bypass two-tenant and pooled/async real SQL, shared quotas/revoke races/restart, audit reconstruction, load and compromise drills. | Every §10.10 threat exercised, required focused/affected/critical checks pass, actual client/revision/library versions and real versus injected evidence recorded. Authoritative Case SQL and audit/grant/admin protection must pass; missing artifact remains BLOCKED. Complete only when security/tenant owners accept safe pilot evidence and explicitly authorize a separate default-off pilot configuration change. | Global/tenant off then revoke all pilot sessions/grants; remove route if necessary. Preserve audit/history and ordinary REST. No data/history deletion, automatic SQL rollback or deployment from this phase. |

### 10.12 Remaining decisions, acceptance blockers and Phase 3A verification

No permissive policy decision is deferred to coding: the pilot has a chosen issuer ownership, opaque OAuth
profile, tenant/self-delegation model, scopes, resource policy, audit representation, limits and wire schemas.
The following are **closed-gate implementation/operational inputs**, not optional bypasses:

* Authoritative Case SQL acceptance remains BLOCKED by absent approved offline artifact; Phase 1R is merged
  with local/synthetic validation only. Preserve Stage A/B compatibility/deployment/fleet, deployed non-bypass
  RLS/grants, audit append-only/admin protection and representative performance gates. No production RLS activation.
* Select exact maintained OAuth/SDK versions with 2026-07-28 compatibility and actual two-client evidence.
  If current clients cannot use preregistration/current revision, the smallest decision is a separately
  reviewed compatibility profile/version adapter; no ad-hoc static token or weaker tenant/user model.
* Before any real-data enablement record exact configured HTTPS issuer/resource/public origin, trusted proxy/
  Origin list, tenant admin and client redirect/key inventory, shared quota/disable propagation, consent
  acceptance and retention sign-off. Tenant/legal owners must approve or replace the proposed seven-year
  retention target; until then no real-data pilot. No public onboarding/admin UI is implied.
* Bearer replay and authorized output retention at the AI remain residual risks. A tenant requiring
  sender-constrained tokens or per-Case grants needs that smallest separate security decision/implementation
  before enabling; neither is silently approximated by this tenant-wide minimized-read pilot.

Phase 3A test-impact review: documentation only, no production symbols/constructors/DTOs/SQL or test expectations
changed. Inspected security/Case/audit tests are evidence for the design, not newly executed passes.
Documentation verification checks relative links/heading anchors, balanced fences, JSON schema composition,
closed fields/references and representative valid/invalid schemas, roadmap state and `git diff --check`.
No new runtime tests were created simply to test prose. Exact commands/results are recorded with this PR.
No MCP endpoint/AI data access/local-AI exception, production/Azure SQL, RLS activation, deployment,
version bump or installer publication occurred. Broader REST remains open, selected Phase 2/Phase 1S remain
complete at their recorded source/synthetic scope, and Phase 3B remains **NOT STARTED**.

Phase 3A executed verification (documentation only):

```bash
python3 work/check_phase3a_docs.py
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --plan-output work/phase3a-selection.json
git diff --check
git diff --check origin/codex/latest HEAD
```

Results: **51 relative links/anchors**, balanced fences in both changed owners, four parseable JSON blocks,
four composed input/output schemas and **56 valid/invalid schema cases PASS**; roadmap/disclosure-scope
consistency PASS. The task-local checker expands local `$defs` and validates representative nulls, required/
unknown fields, IDs, query/page bounds, local timestamps and relationship objects. These are documentation
examples, not executed MCP/server authorization tests. Selector: documentation-only, no affected modules/
tests and no full-suite escalation; its generic critical `mvn test` suggestion was not executed because no
runtime contract changed. No Maven/npm/SQL/client runtime acceptance is claimed. Full two-file diff and
whitespace checks PASS. Bootstrap/required architecture and source/security/test review are recorded in
§10.1; only this canonical roadmap and the session-owner cross-reference changed.

Command-line Git's configured proxy was unreachable. The GitHub connector retrieved live commit/tree/blobs;
Git hashes were verified before the isolated worktree was advanced to the exact live base. Unrelated image
entries remain in the base tree and were excluded from sparse checkout, not deleted. Publication uses the
connector's Git object/ref operations, preserving every unrelated base entry and the original checkout.


### 10.13 Phase 3B.1 integration persistence and security foundation

**Status: COMPLETE — persistent security foundation and focused/affected/critical source/synthetic verification.**
Phase 3A remains COMPLETE as design. At this record's completion, Phase 3B.2–3B.7 were NOT STARTED;
§10.14 records the subsequent 3B.2A compatibility blocker. MCP is inactive.
Live base `a97691ecb2147d9888d9f636aee8cc9e07842cdd` includes merged Phase 1R #1898,
Phase 1S #1899, selected Phase 2 #1900 and Phase 3A #1901, confirmed using live GitHub metadata.
The isolated task branch preserves the original checkout. Source/tree hashes were checked; a sandbox-network
fetch subsequently succeeded. No historical source/deployment assumption replaces that live base.

The [SQL candidate and operator record](../sql/ai-integration/README.md) contains the pre-DDL dependency
inventory, exact role/module authority, schema, audit compatibility, race protocol and retained-history rollback.
It reuses Users/ShaleClients, their existing tenant key, strict TenantFilter, UTC/RowVer and both existing audit
families. Only new integration-owned tables receive FILTER/BLOCK predicates; shared functions and legacy
RLS are unchanged. Missing dependencies/drift abort transactionally. Nothing installs at startup.

Implemented source is `AiScope`, `AiIntegrationSecurityService`, `AiProductCapabilityService`,
`AiBootstrapResolver`, `VerifiedIntegrationContext`, and the additive `docs/sql/ai-integration/phase3b1.sql`.
Ten persistent entities are:

| Entity | Foundation |
| --- | --- |
| AiProductCapability | Global default-false singleton, restricted actual SQL operator, revision/RowVer and atomic global audit. |
| TenantAiIntegrationPolicy | Strict tenant singleton, absent=false, current admin, RowVer, audited change; disable revokes grants/families/intents. |
| AiIntegrationRegistration / AiIntegrationRedirect | Immutable tenant and random public/client IDs; closed status/ceiling, 90-day bound, exact ≤5 redirects, actor/time/revoke/revision/RowVer. |
| AiIntegrationClientKey | Pinned RSA public modulus/exponent/fingerprint only; two-key maximum, ≤24-hour overlap, bounded validity, current registration RowVer for rotation, immediate revoke. |
| AiDelegationGrant / AiConsentIntent | Server-issued hash-only single-use intent bound to current user; explicit same-user scopes, ≤5-minute acceptance, ≤24-hour consent, permanent revoke. No admin impersonation. |
| AiDelegatedSession / AiOAuthCredential | Separate durable tuple/family, absolute/idle expiry, generation and revoke; hash-only ACCESS/REFRESH snapshots and unique refresh generation. No issuance/protocol implementation. |
| AiQuotaReservation | SQL transaction admission across user/integration/tenant, trailing rows/calls, ≤15-second concurrency lease, unique invocation, durable no-refund accounting. |

The default service constructor is off; configured startup enablement can only further deny the shared SQL
switches. No tenant/user/key/grant/session/credential is automatically provisioned. Only synthetic tests enable
new disposable rows. The closed case-sensitive scopes remain `cases.search` and `cases.overview.read`.
Ceiling/grant/session intersection is deterministic; credential intersection/validation is 3B.2. Reduction also
revokes affected grants, preserving explicit re-consent after later ceiling increases. No Case operation accepts
this context yet, so the new foundation cannot disclose data through existing ordinary Case reads.

The runtime role has bounded EXECUTE and explicit table denials. Tenant modules derive both identities from
current authenticated context and revalidate active membership; administrative checks use `Users.is_admin`,
not custom roles or SQL ShaleAdmins. Only the two pre-tenant resolvers execute as owner; their bounded exact
inputs/projection produce provisional references, restore identity and grant no tenant business access.
A separately initialized runtime lease and live session gate create immutable verified context. It carries
server correlation/invocation, tenant/user/integration/grant/session and exact scopes, without credentials,
servlet state or an ordinary REST principal. It remains a snapshot requiring future final revalidation.

Admin/security changes use existing entity-action storage with five additive closed entity types and existing
CREATED/UPDATED/DEACTIVATED actions, fixed source, stable target and correlation. Metadata is only ACTIVE and
AI_SCOPE_MASK. Product enable/disable uses existing global audit. SQL owns mutation plus audit before commit;
failed/zero-row append rolls back. JDBC consumes procedure completion and closes before returning, so lost
commit acknowledgement/close failures withhold results. Eligibility/bounded configuration reads, consent intent
preparation and intrinsic quota facts intentionally add no mutation-audit spam. No PHI or access-read audit
vocabulary is misused; IntegrationAccessAudit remains 3B.3 work.

The shared lock order is capability then tenant, with exclusive tenant admission/mutations and shared
verification. Durable worst-case row reservations enforce 200/user, 500/integration, 1000/tenant per trailing
24 hours, sliding minute 10/20/60, distributed concurrency 1/2/5. Rejected quota attempts count calls; release
never refunds committed rows/calls. A replacement JVM and distinct service instances use the same SQL state.
Future transport must add edge/discovery/process limits and account every verified attempted tool dispatch.
No data-work queue, tool dispatch or response handoff is implemented in this foundation.

Pre-edit impact inspection covered current tenant policy/admin/firm-role, EntityActionAuditEvent/DAO and
migration chain, auth modules, durable sessions, context initializer and local SQL fixtures. The existing
chronological audit test was extended for this successor (including the already-existing Task policy token),
without rewriting historical migrations. New scope/context/SQL tests route through security-data and default
critical smokes; opt-in SQL remains an explicit separate run. Full changed-file/test review is required below.

**Evidence classes and continuing boundaries:**

- **Source/static:** exact live base, additive schema/module review, selector and default-off/no-route contracts.
- **Synthetic real SQL:** new loopback-only SQL Server 2022 Developer, separate actual setup/runtime/bootstrap/
  operator/verifier logins, synthetic prerequisites and nonce-owned databases. Per-table FILTER/BLOCK, foreign/
  NULL context, ownership, constraints, live gates, scopes, RowVer/races, atomic audit, quota dimensions and
  replacement-JVM persistence are tested; these are not authoritative Case-schema or deployed-security claims.
- **Injected evidence:** partial DDL/audit/resolver faults and JDBC lost-ack/close failure are explicit injections.
- **Authoritative evidence:** merged source and repository migration definitions only. The approved Case schema
  artifact remains unavailable; authoritative Case SQL acceptance is **BLOCKED**, unchanged by these tables.
- **Unexecuted production evidence:** all production/Azure SQL, deployed grants/owners, RLS activation, actual
  independent OAuth/MCP clients, load/real-data retention and deployment acceptance. No such execution is claimed.

Next increment: **3B.2**, select a maintained OAuth implementation and prove Boot/JDK compatibility before
protocol work. Bind maintained AS transactions/PKCE/redirect/browser-CSRF/resource; implement code/access/
refresh/assertion replay, exact credential scopes, expiry clipping, conditional rotation and security-reset
family revocation before any credentials issue. 3B.3 must add integration access audit and final handoff at the
shared Case worker. 3B.4–3B.6 own all transport and tools. Existing ordinary auth is unchanged.

No MCP dependency/route/discovery/tool, OAuth protocol endpoint, AI Case data disclosure, local-AI bypass,
production/Azure SQL, RLS production activation, deployment, version bump or installer publication occurs.
No Phase 3B completion or real-data enablement is implied.

Executed verification (local Maven 3.9.9 / Temurin JDK 21.0.12.1; no POM/toolchain changes):

| Check | Result and evidence boundary |
| --- | --- |
| Focused scope/context/audit plus explicit local SQL | **44 PASS**, zero failures/errors/skips: 2 core scope, 5 context/default-off/failure tests, 12 existing audit tests and **25 real synthetic SQL scenarios**. |
| Repository selector affected suite | **67 PASS**, zero failures/errors. Security-data plus the modified chronological audit test, core/data/server dependencies. Its opt-in SQL class reports zero executed tests without credentials; the separate explicit 25-scenario run above is its acceptance evidence. |
| Critical Maven reactor | **125 PASS**, zero failures/errors/skips; all seven reactor modules BUILD SUCCESS. |
| Selector Python tests | **26 PASS**, including new AI SQL ownership/default-off routing. |
| Exact SQLCMD install / repeat / verify | PASS in a separate fresh nonce-owned synthetic database: nine tables each have FILTER/INSERT BLOCK/UPDATE BLOCK = 1/1/1, no disabled/untrusted findings; global=0, enabled tenants=0, registrations=0. Exact fixture removed afterward. |
| Documentation / diff | Five Markdown owners, **68 relative links/anchors**, balanced fences and four parseable JSON schema blocks; full 24-file diff reviewed and `git diff --check` passes. |

SQL is Microsoft SQL Server **16.0.4305.5** (2022 Developer), task-owned loopback-only container pinned to
`mcr.microsoft.com/mssql/server@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
Each test creates/removes only its exact nonce-owned synthetic databases and separate SQL logins. The test-only
verifier gets direct grants solely to prove real BLOCK rejection on otherwise valid foreign inserts for every
new tenant table; the ordinary runtime has direct denials and no bypass roles. Successful and injected-failure
resolver calls prove caller restoration, including comparison of the installed resolver definition to source.
Synthetic process restart means an **actual replacement JVM**, not a counter reset or just a new service object;
no SQL Server/production restart is claimed. Existing missing authoritative Case evidence remains BLOCKED.

Exact commands (the task-local Maven wrapper supplies JDK, configured proxy and system CA trust):

```bash
# Password supplied privately only to the explicitly disposable fixture, never in argv/logs:
SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI SHALE_AI_SQL_PORT=14337 \
  /workspace/work/tools/bin/mvn -B -pl shale-data -am \
  -Dtest=AiScopeTest,AiSecurityContractTest,AiSecurityLocalSqlServerTest,EntityActionAuditEventTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/work/tools/bin:$PATH JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true \
  python3 build/test-selection/select_tests.py \
  --base a97691ecb2147d9888d9f636aee8cc9e07842cdd --head HEAD \
  --format markdown --plan-output work/phase3b1-selection.json --run
# --run executed the emitted affected core/data/server command, then critical mvn test.
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 work/run_verify_sql.py
python3 work/check_ai_docs.py
git diff --check
git diff --check a97691ecb2147d9888d9f636aee8cc9e07842cdd HEAD
```

`run_verify_sql.py` is task-local orchestration of actual SQLCMD `-b` against one new synthetic database:
prerequisites → exact installer → exact repeat → `verify.sql` → exact cleanup. It does not alter candidate SQL,
use an old operator packet, or connect to another server. `run_ai_sql.py` supplies only generated disposable
credentials to the exact focused Maven command. These scratch helpers and secrets are not committed.

Early local failures were corrected before the passing runs: runtime-only JDK/proxy CA setup, JDBC binary
NULL typing, zero-row trigger behavior, ordered catalog hashing, and a BLOCK fixture colliding with an existing
unique tenant row. The final per-table test requires actual SQL error 33504, not a coincidental constraint
failure. No failed/initial run is reported as passing. Selector changes recommend historical all-tests only
informationally; that optional broad suite was not run. No required focused/affected/critical check is blocked.

### 10.14 Phase 3B.2A OAuth compatibility gate — 2026-10-21

**Status: BLOCKED — framework/support prerequisite; OAuth runtime implementation has not started.**
3B.1 COMPLETE (source/synthetic scope); 3B.2B, 3B.2C, 3B.2D and 3B.3+ NOT STARTED.
This follows §10.3's explicit separate-upgrade rule. It does not complete 3B.2A or permit a custom OAuth server.
Live `codex/latest` is `41fdefd8447661a85ab8bdc26d500492d5b7cc11`; live GitHub PR metadata confirms
#1901 merged as `a97691ec` and #1902 merged as `41fdefd8`. The original checkout is preserved.

#### Dependency and support findings

| Candidate/baseline | Inspected evidence | Decision |
| --- | --- | --- |
| Current Shale | JDK 21; server imports Boot **3.3.4** BOM: Framework **6.1.13**, Security **6.3.3**, AS **1.3.2**. Parent explicitly manages Security crypto **6.3.3**. Server has web/validation starters and springdoc **2.6.0**, no Security web/config starter or AS dependency. | Java meets the AS minimum; that alone does not establish Spring API/support compatibility. |
| Boot-managed AS 1.3.2 / 1.3.x | [Spring's April 2026 release notice](https://spring.io/blog/2026/04/21/spring-authorization-server-1-5-7-available-now/) confirms 1.3.x and 1.4.x OSS support ended; maintained patches on those lines require a commercial subscription. | Do not add an unsupported old version merely because the BOM resolves it. No commercial entitlement/artifact access is established in this task. |
| Standalone AS **1.5.8** | [Tagged dependency catalog](https://github.com/spring-attic/spring-authorization-server/blob/1.5.8/gradle/libs.versions.toml): Framework **6.2.19**, Security **6.5.11**, Jackson **2.18.8**, Nimbus **9.47**. [Module dependencies](https://github.com/spring-attic/spring-authorization-server/blob/1.5.8/oauth2-authorization-server/spring-security-oauth2-authorization-server.gradle) add Security config/web/OAuth2 core/JOSE/resource-server plus Nimbus/Jackson. | Not a safe drop-in on Shale's 6.1/6.3 stack; Boot mediation would retain older APIs unless deliberately upgraded. This inspected release is compatibility evidence, not a selected maintained production version. |
| Spring Security Authorization Server **7.0.7** | [AS installation guide](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/authorization-server/getting-started.html) and [tagged catalog](https://github.com/spring-projects/spring-security/blob/7.0.7/gradle/libs.versions.toml): Framework **7.0.9**, Servlet **6.1**, Nimbus **10.4**; AS now belongs to Spring Security 7. | Preferred maintained implementation family; requires a separately reviewed Boot 4 / Framework 7 / Security 7 stack. No runtime version is accepted or pinned here. |

There is a concrete API mismatch as well as BOM drift: AS 1.5.8's
[DPoPProofVerifier](https://github.com/spring-attic/spring-authorization-server/blob/1.5.8/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/DPoPProofVerifier.java)
imports `DPoPProofContext` and `DPoPProofJwtDecoderFactory`, introduced in
[Security 6.5](https://docs.spring.io/spring-security/site/docs/6.5.0/api/org/springframework/security/oauth2/jwt/DPoPProofJwtDecoderFactory.html).
Its authorization-code provider invokes this verifier. Disabling DPoP in the pilot does not establish binary
compatibility with Security 6.3.3. Do not exclude/downgrade those dependencies or invent replacement classes.
No new transitive dependencies, framework overrides or auto-configuration have been added.

#### Smallest separate prerequisite recommendation

Open a dedicated **supported Spring server baseline** change before resuming OAuth. For an OSS-only path,
evaluate Boot **4.0.x** with its BOM-aligned Framework **7.0.x** / Security **7.0.x**, retaining JDK 21;
select and pin supported, advisory-reviewed patch versions during that change. Security AS **7.0.7** is
the inspected example, not an instruction to override a newer BOM patch. Align the parent's crypto version
with that Security BOM across all modules, review springdoc 2.6.0 compatibility/replacement and Jackson/
Servlet/MVC/test API migration, and prove the complete dependency tree/context plus existing auth/session
and critical reactor tests. Keep OAuth/MCP endpoints and credentials absent in that prerequisite PR.
This is a substantial framework migration and is deliberately not performed in this task.

A Boot 3.5 / Security 6.5 bridge is not an established maintained OSS alternative: Spring labels
[Boot 3.5.16 the last OSS release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).
If the owner already has commercial support, a smaller separately reviewed supported 3.x patch alignment
may instead be evaluated with actual entitlement and current patched AS artifacts. This task neither assumes
that access nor purchases support. Recheck upstream support/advisories and runtime compatibility at the
prerequisite's execution date; archive status or a documentation version list is not maintenance proof.

#### Preserved protocol boundary and next increment

The fixed future identities remain `${publicOrigin}/oauth/mcp` and `${publicOrigin}/mcp` from §10.3.
No typed OAuth configuration or new environment variable is wired yet. Current deployment CORS/API-origin
settings are not a validated server issuer authority; request Host/Forwarded headers cannot supply one.
Resume 3B.2A after the prerequisite: validate one canonical HTTPS production origin/issuer/resource with exact
comparison and separate dev/test identities, reject aliases/malformed/other/multiple resources, and start the
maintained library in an isolated, issuance-incapable composition before progressing to 3B.2B.

Source inspection finds **no existing Spring SecurityFilterChain**: ordinary auth uses
`BearerTokenServerSessionResolver`, `ServerRuntimeSessionState`, `RequestScopedDbSessionProvider` and
`ServerAuthSessionService`/`DurableSessionTokenValidator`. Adding Security web/config would introduce a new
servlet-filter layer. The resumed 3B.2A must explicitly match only the OAuth namespace/metadata, preserve
ordinary controller/service authorization and test the fallback composition, rather than enabling default
form login, Basic auth or a catch-all authenticated chain. REST JWTs, development headers and desktop restore
credentials must never supply OAuth client/delegated authority. No filter-chain isolation proof is claimed here.

Client mapping remains implementation work: tenant registration/client ID and redirects to `RegisteredClient`,
PUBLIC to `ClientAuthenticationMethod.NONE`, CONFIDENTIAL to `PRIVATE_KEY_JWT` with pinned local RSA keys/
RS256 and no remote JWKS or client secret. The existing bounded registration projection does not yet include
all redirect/key/type data for that adapter; do not invent an unaudited unrestricted repository. Require PKCE
for both types **and** an S256-only request validator; `requireProofKey(true)` alone is not that policy.
Enforce exact hosted redirects and the native-public literal-loopback IP/path exception with only the port
variable. These are preserved requirements, not newly executed library mapping/redirect/PKCE tests.

RFC 8414 and RFC 9728 routes remain **absent**, with no grants or usable endpoints advertised. The §10.3
metadata describes the eventual profile; it is not a current response. Resumed 3B.2A may expose only truthful
library-supported capabilities or leave metadata off with a recorded reason. No MCP data route is mounted.
3B.2B then owns durable single-use authorization code + S256 PKCE + self-consent transaction, exact resource/
redirect/state/issuer binding and browser CSRF, while access/refresh issuance remains blocked until 3B.2C;
live credential/context validation belongs to 3B.2D. Do not skip 3B.2A because this investigation is recorded.

**Audit compatibility:** this documentation-only stop performs no sensitive read, tenant/admin mutation,
credential issuance or protocol request, so no new PHI/entity/security audit row or migration is appropriate.
Phase 3B.1's transactional audit and all future §10.5 disclosure obligations remain unchanged.
Global and tenant defaults remain OFF; no usable AI access/refresh token, MCP, Case disclosure, production/
Azure SQL, enablement, deployment, version bump or installer publication occurred. WEB/DESKTOP auth is retained.

Verification evidence for this investigation is recorded below; upstream source/API inspection is distinct
from dependency resolution and runtime testing. No OAuth application-context or protocol test has passed
because no compatible runtime was installed.

| Check | Result / limit |
| --- | --- |
| Live source and upstream compatibility inspection | PASS for recorded versions/API references; not a successful resolved library build. Git fetch could not connect to the configured proxy. GitHub connector supplied the live ref, signed commit, tree and text blobs; local object hashes match. The separate sparse worktree omits only twelve unrelated historical PNG screenshots while retaining their exact tree entries. |
| OAuth dependency resolution probe | BLOCKED: task-local POM importing Boot 3.3.4 and AS 1.5.8 cannot download the AS POM from Central (`Operation not permitted`). No application POM is changed. No transitive runtime tree or OAuth context start is claimed. |
| Existing dependency tree | BLOCKED before server: ICU4J **78.3** and libphonenumber **9.0.40** descriptors cannot be downloaded (`Operation not permitted`). Cached Boot BOM inspection establishes managed versions only. |
| Focused existing auth/session/default-off regressions | BLOCKED in shale-data: the same two dependencies are absent from the local cache. WEB login/me/refresh/logout, durable WEB/DESKTOP/remembered-session and server-configuration tests did not execute. |
| Selector affected suite | Documentation only: no affected Maven areas/modules/classes and no full-suite escalation. No affected runtime suite is reported as passing. |
| Critical cached Maven reactor | BLOCKED in shale-data by the two missing artifacts. Six core tests passed (two AiScope and four AppPaths); later modules did not execute. This is not a passing reactor. |
| Selector Python | **26 PASS**. |
| Documentation / diff | Two Markdown owners, **55 relative links/anchors**, balanced fences, four parseable JSON blocks and slice status checks PASS; complete two-file diff reviewed, `git diff --check` PASS. |

Commands use the existing `/workspace/.tools/bin/mvn` wrapper (Maven 3.9.11, JDK 21 and system CA trust).
Task-local probes/checkers/logs remain in `work/`, not the deliverable tree:

```bash
mvn -o -B -pl shale-server,shale-desktop -am \
  -Dtest=AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedSessionSqlTest,SqlRememberCredentialStoreTest,ShaleAuthTokenServiceTest,RequestScopedDbSessionProviderTest,ShaleServerServiceConfigurationTest,DesktopRememberedRecoveryTest,DesktopRuntimeSessionLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,AiSecurityContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-server -am -DskipTests dependency:tree \
  -Dincludes=org.springframework.security:*,org.springframework:*,org.springframework.boot:*
mvn -B -f work/oauth-probe.xml -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dmaven.wagon.http.retryHandler.count=0 \
  org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree
mvn -o -B test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 build/test-selection/select_tests.py \
  --base 41fdefd8447661a85ab8bdc26d500492d5b7cc11 --head HEAD \
  --format markdown --plan-output work/phase3b2a-selection.json
python3 work/check_phase3b2a_docs.py
git diff --check
git diff --check 41fdefd8447661a85ab8bdc26d500492d5b7cc11 HEAD
```

The dependency/cache and network failures are independent of the framework/support blocker. Restoring Maven
access alone does not approve AS 1.3.x or make AS 1.5/7 compatible with this unchanged Boot baseline.
After the separate prerequisite, complete the previously unexecuted OAuth configuration/library, redirect,
PKCE, metadata and filter-isolation tests plus focused existing auth/session, selector affected and critical
checks before marking 3B.2A COMPLETE. No runtime regression or independent OAuth client acceptance is inferred
from this documentation-only investigation.

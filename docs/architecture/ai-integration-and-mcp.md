# AI Integration, REST/OpenAPI, and MCP Architecture and Roadmap

**Status:** Phase 0 assessment COMPLETE; broader architecture/contract review IN PROGRESS; selected Case REST/OpenAPI foundation COMPLETE; Phase 1R merged with synthetic local validation; Phase 1S shared minimized Case boundary COMPLETE (source/synthetic proof); authoritative Case SQL BLOCKED; Phase 3A security contract COMPLETE; Phase 3B implementation IN PROGRESS; Phase 3B.1 COMPLETE (source/synthetic proof); 3B.2A COMPLETE (foundation/local proof); MCP inactive.

**Last reviewed:** 2026-10-22 (authenticated RFC 7009 closeout; historical acceptance scopes retained)

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
| Supported Spring server baseline prerequisite | COMPLETE (merged #1904; verified baseline) | §10.15; Boot 4.1.1 with focused, affected, critical and packaging proof; separate from OAuth. |
| Phase 3B.2 credential/authentication validation | COMPLETE (source/local synthetic verification) | §10.16–10.21; credential lifecycle, authenticated revocation and internal ACCESS verification complete at this scope; deployed/runtime acceptance remains open. |
| Phase 3B.2A OAuth dependency/configuration/protocol foundation | COMPLETE (foundation/source/local verification) | §10.16; maintained isolated composition, canonical identities, bounded client adapter, redirect/S256 policy and truthful metadata; no usable AI credential. |
| Phase 3B.2B authorization code + S256 PKCE + consent transaction | COMPLETE (source/local synthetic verification) | §10.17; durable self-only consent, 60-second hash-only codes, S256/state/issuer binding and atomic replay/race/restart proof. No usable access/refresh issuance. |
| Phase 3B.2C opaque access + rotating refresh lifecycle | COMPLETE (source/local synthetic verification) | §10.19 final-source verification; revocation closeout is recorded separately in §10.20. |
| Phase 3B.2C.1 authorization-code exchange validation | COMPLETE (source/local synthetic verification) | §10.18; pinned client proof, durable replay, atomic consume/live authority; no usable credential. |
| Phase 3B.2C.2 opaque issuance + rotating refresh | COMPLETE (source/local synthetic verification) | §10.19 already records the final-source critical pass. |
| OAuth revocation closeout | COMPLETE (source/local synthetic verification) | §10.20; authenticated RFC 7009, atomic family audit, races/replay/restart and full required local verification. |
| Phase 3B.2D live credential validation + VerifiedIntegrationContext | COMPLETE (source/local synthetic verification) | §10.21 expiry closeout; observed SQL lock wait, post-release database UTC rejection, complete affected suite and critical reactor pass. |
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
Phase 3B.1 is COMPLETE at its recorded scope (§10.13); the supported Spring prerequisite is recorded in §10.15.
PR #1904 is merged and its supported baseline is verified. 3B.2A source/local evidence is in §10.16;
durable usable OAuth credential lifecycle is COMPLETE at source/local synthetic scope (§10.19). 3B.2A is COMPLETE at foundation scope; 3B.2B is COMPLETE at source/local synthetic scope (§10.17); 3B.2C.1 is COMPLETE at source/local synthetic scope (§10.18), aggregate 3B.2C and 3B.2C.2 are COMPLETE; authenticated revocation closeout is recorded in §10.20; 3B.2D is COMPLETE (source/local synthetic verification, §10.21); 3B.3+ remain NOT STARTED.
All capability/tenant flags remain off.

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

At Phase 3A inspection, `shale-server` used Boot 3.3.4 and Springdoc 2.6.0; §10.15 records the subsequent
Boot 4 baseline. The inspected POMs contain
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
Security and the official MCP Java SDK against the actual baseline (now Boot 4.1.1/JDK21; §10.15). No OAuth
or MCP dependency is added by the framework prerequisite. Pin maintained compatible versions in 3B.2/3B.4
and run existing REST/session regressions;
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
| **3B.2A OAuth dependency/configuration/protocol foundation — COMPLETE (foundation/source/local verification)** | Merged prerequisite #1904; §10.16 implements typed canonical issuer/resource, isolated maintained AS composition, bounded client mapping, exact redirect and S256-only policy. No issuance. | Resolve actual dependencies, start context, prove namespace isolation and canonical configuration/redirect/PKCE rules; publish only truthful implemented metadata, otherwise leave it absent. | Keep all AI flags off and credential endpoints denied; optional metadata is informational only; preserve ordinary authentication. |
| **3B.2B authorization code + S256 PKCE + consent transaction — COMPLETE (source/local synthetic verification)** | Completed 3B.2A; durable code transaction bound to current human/grant/client/redirect/resource/challenge, browser CSRF/state and RFC 9207. No usable access/refresh credentials. | Atomic single-use code consume, wrong/missing/plain PKCE, redirect/resource substitution, consent races and expiry; preserve WEB/DESKTOP regressions. | Disable authorization transactions; retain consent and security history. |
| **3B.2C opaque access + rotating refresh lifecycle — COMPLETE** | 3B.2B; hash-only storage, conditional rotation, consumed-refresh reuse revokes family, five-minute access and absolute/idle clipping, pinned assertions/shared replay. | Cross-replica consume/rotation/replay and response-loss faults, expiry/revoke/key/reset races; no MCP endpoint. | Deny issuance/refresh and revoke families; no JWT fallback. |
| **3B.2C.1 authorization-code exchange validation — COMPLETE (source/local synthetic verification)** | §10.18; confined code owner, public S256, pinned RS256 assertions, shared replay, atomic consume/live authority; internal result only. | Exact bindings, client proof, revocation/expiry, real SQL races/restart/audit faults, maintained HTTP and WEB/DESKTOP regressions. | Disable validation switch; retain consume/replay/revocation/audit state. |
| **3B.2C.2 opaque credential issuance and rotating refresh — COMPLETE** | 3B.2C.1; atomic issuance at the validated boundary, existing session/credential structures, expiry clipping and reuse-family revoke. | Issuance/rotation/response-loss/reuse/race/restart and security-reset proof before usable metadata. | Disable issuance/refresh, revoke families, retain history. |
| **3B.2D live credential validation + VerifiedIntegrationContext — COMPLETE (source/local synthetic verification)** | 3B.2C; exact issuer/resource/client/owner/scope binding through confined bootstrap and fresh runtime live gates. | Invalid/revoked/expired credentials, wrong owner/audience, pooled context isolation and REST credential rejection; no data route. | Disable credential resolution; retain rows and default-off capability. |
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

**Historical compatibility decision: BLOCKED — framework/support prerequisite; OAuth runtime implementation has not started.**
The separate prerequisite and current merge/verification gate are now recorded in [§10.15](#1015-supported-spring-server-baseline-prerequisite--2026-10-22);
the inspected 7.0.7 example below is not the selected BOM version.
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


### 10.15 Supported Spring server baseline prerequisite — 2026-10-22

**Historical preparation status: COMPLETE at source/local verification scope; merge acceptance pending.**
PR #1904 is now merged and its prerequisite is verified; §10.16 supersedes this historical gate.
Phase 3B.1 remains **COMPLETE at its existing source/synthetic scope**. Phase 3B.2A remains **BLOCKED until
this prerequisite is merged and verified**, then is the next task. Its framework dependency is satisfied by
the selected stack; OAuth configuration/protocol implementation is unfinished. **3B.2B+ NOT STARTED**.
No OAuth/MCP dependency, endpoint, credential issuance or Case AI disclosure is added.

Prepared from live `codex/latest` **a0ce0d856ccbf8167ae0e48ed4cac8af19670f05**, confirming #1902 and #1903
merged, in a separate `codex/supported-spring-baseline` worktree. The original checkout is preserved.
Before editing, live [Maven Central metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml)
and the [Spring project page](https://spring.io/projects/spring-boot/) identified **4.1.1** as the current stable
Boot release (4.2 entries are milestones). Its actual BOM/imports resolved successfully, matching the [managed dependency inventory](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html). Java **21** is retained.
[Springdoc's current instructions](https://springdoc.org/getting-started.html) select
`springdoc-openapi-starter-webmvc-ui` **3.1.1**; the artifact name stays the same.

#### Dependency inventory and management decision

The before/after server reactor dependency trees, cached/resolved BOMs and all seven POMs were inspected.
“Managed” does not mean every artifact is present: Security web/config/authorization server remain absent,
and embedded Tomcat supplies the Servlet API classes rather than a separate servlet-api jar.

| Component | Previous effective/managed baseline | Selected baseline |
| --- | --- | --- |
| Boot / repackage plugin | 3.3.4 | **4.1.1** |
| Framework | 6.1.13 | **7.0.9**, Boot BOM |
| Security / crypto | 6.3.3 managed; crypto 6.3.3 present | **7.1.1**, Boot BOM; only crypto present |
| Embedded Tomcat / Servlet | 10.1.30 / 6.0.0 | **11.0.24 / 6.1.0** |
| Application Jackson | 2.17.2 | **3.1.5** (`tools.jackson`) |
| Swagger-internal Jackson 2 | 2.17.2 | **2.21.5**; shared annotations **2.21**, all Boot-managed |
| Hibernate Validator / Jakarta Validation | 8.0.1.Final / 3.0.2 | **9.1.3.Final / 3.1.1** |
| Springdoc / Swagger annotations+models | 2.6.0 / 2.2.22 | **3.1.1 / 2.2.55** |
| SLF4J | API 2.0.12, JUL bridge 2.0.16 | **2.0.18** throughout |
| Logback | classic 1.5.6, core 1.5.8 | **1.5.38** throughout |
| JUnit / Surefire+Failsafe | Jupiter 5.10.2 outside server, 5.10.3 server / 3.2.5 | **6.0.3 / 3.5.6** |

The parent now imports the single Boot BOM, including for shared data/desktop crypto consumers; the server's
private Boot import and version are removed. No Framework/Security artifact is hand-pinned. Removed parent
`spring.security.version=6.3.3` and its explicit crypto management, SLF4J/Logback properties and explicit
management (fixing the old classic/core mismatch), and HttpClient 5.3.1 override so its client/core family
aligns with Boot. Removed all five child Jupiter 5.10.2 pins to avoid a JUnit 5/6 platform mixture.

Deliberate retained exceptions: parent Microsoft JDBC **12.6.1.jre11** and HikariCP **5.1.0** preserve the
existing direct-JDBC pool/connection lifecycle, outside Spring JDBC auto-configuration; no JDBC starter is
added. Parent Gson **2.11.0**, plus the existing UI/updater Gson declarations, retain independent desktop
JSON behavior; Gson is not the MVC serializer. These were reviewed rather than blindly carried forward.
JavaFX/RichTextFX/JNA/jBCrypt/POI/PDF and their existing pins are outside the Spring migration. Compiler
**3.13.0**, resources **3.3.1**, dependency **3.6.1**, updater jar **3.4.1** and shade **3.5.3** stay unchanged;
importing a BOM does not inherit Boot's parent plugin management. Surefire/Failsafe **3.5.6** match the new
JUnit platform; the Boot repackage plugin follows **4.1.1**. No desktop release/version bump.

#### Required source and compatibility adaptations

- Use Boot's focused `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test`; move MockMvc
  auto-configuration and embedded Tomcat test factory imports to their Boot 4 modules.
- Enable server compiler `parameters` metadata for existing unnamed MVC arguments and generated query
  documentation. Stable Case bindings remain explicit; no route/controller redesign.
- The bounded Case serializer injects Jackson 3 `ObjectMapper`, catches `JacksonException`, and still
  delivers the exact checked UTF-8 bytes. Use `spring.jackson.use-jackson2-defaults=true` for Boot 3 wire
  compatibility; retain explicit nulls, unknown-property handling, record members and ISO Java-time text.
  Test fixtures use immutable JsonMapper builders and the renamed JSON converter/tree APIs.
- Set `springdoc.api-docs.version=OPENAPI_3_0` explicitly: Springdoc 3's newer default must not silently
  replace the published nullable/schema contract. Preserve `/v3/api-docs`, Swagger UI, `bearerAuth`, the
  minimized Case descriptions and every existing Phase 2 compatibility assertion.
- Existing admin-session invalid-timestamp coverage exposed an uncaught `DateTimeParseException` returning
  500. Catch it alongside invalid enum arguments to retain the tested sanitized 400 rejection and never
  broaden accepted filters. Existing error-log tests are corrected to assert sanitized copies with complete
  stack frames, not raw exception messages; a real driver throwable replaces a mock with invalid Throwable
  bookkeeping. Production diagnostic/audit behavior is unchanged.
- Existing session source checks contained stale whole-file assumptions: an RLS dynamic-SQL variable was
  mistaken for a persisted unbounded column, and a global substring count did not identify the actual three
  account mutation paths. Scope prohibited columns to the UserSessions declaration and verify deactivation,
  password reset and removal each revoke on the same connection before commit. SQL and DAO code are unchanged.

Servlet request/session resolution, request-scoped tenant/actor connection initialization, CORS, forwarded
headers, async/error dispatch and JDBC ownership remain in their existing seams. No custom servlet filter,
Tomcat setting or authority redesign is needed. No SQL schema/grant/procedure/RLS/Phase 1R/3B.1 change.
Validation retains the same DTO constraints, controller bounds and safe errors. Password verification still
uses the existing jBCrypt verifier; the managed crypto artifact does not install Spring authentication.

#### Verification and limits

All counts below are from the final successful commands; groups overlap and must not be summed as unique tests.

| Evidence category / check | Result and scope |
| --- | --- |
| Source/static inventory | All seven POMs, affected controllers/tests, servlet/auth/session/JDBC and default-off boundaries reviewed. No SQL or authority redesign. |
| Dependency resolution | Boot 4.1.1 BOM/import probe and complete before/after/final reactor trees PASS. No unintended old/new Spring generation. Framework 7.0.9, Boot 4.1.1 and crypto 7.1.1 throughout; no Security web/config/AS or actuator. Jackson 2 for Swagger and Jackson 3 for MVC coexist intentionally. |
| Compile | Server/core/data `compile` PASS; subsequent affected/critical builds compile all affected tests and desktop dependencies. |
| Focused context/startup | Default embedded server, full prod composition and local/azure bean selection PASS; actual Tomcat 11 starts on ephemeral loopback ports. No startup errors/default generated password. SQL destinations are dummy loopback values; no SQL is executed. |
| Focused existing authentication | **69 passed**, 16 classes, zero failures/errors/skips: password login/me/refresh/logout, durable and remembered-session/JTI/replay/revocation, desktop recovery/enrollment/lifecycle and pre-tenant password lookup. |
| REST/OpenAPI/validation/errors | **136 passed**, 16 classes, zero failures/errors/skips: all controller tests, selected Case compatibility/privacy/byte bounds, OpenAPI, actual wire mapper and safe errors. |
| Data/session/default-off | **39 passed**, 12 classes, zero failures/errors/skips: context initialization/closure faults, runtime session timing, adapter/revocation/migration source contracts, pre-tenant modules and 3B.1 AiScope/AiSecurityContract. Proxy/synthetic faults are not real SQL acceptance. |
| Selector affected suite | **185 passed**, zero failures/errors, **6 opt-in SQL scenarios skipped** (191 discovered, 27 classes). Case/contact/security-data/server plus the selected Settings lifecycle contract pass. The six skipped Phase1R/remembered-session SQL scenarios did not execute and are not passing SQL evidence. |
| Critical Maven reactor | **126 passed**, 31 classes, zero failures/errors/skips; all seven modules BUILD SUCCESS. |
| Executable server package | `package -DskipTests` PASS, Boot 4.1.1 repackage and packaged-library inspection PASS; this packaging command does not itself execute tests. |
| Selector Python | **26 passed**. Parent/module POM changes recommend historical all-tests informationally; it was not run and is not substituted for focused/affected/critical proof. |
| Documentation/diff | Owning documents, relative links/anchors, fences, full diff and `git diff --check` reviewed/validated before commit. |
| Real synthetic SQL / production acceptance | Real synthetic SQL scenarios were not run for this framework-only change. No production/Azure SQL, installed-desktop acceptance or deployment is claimed. Earlier 3B.1 synthetic receipts retain their original scope. |

The initial sandbox-restricted fetch/download failed; one retry with explicit network access succeeded.
No repeated infrastructure retries. A concurrent compile attempt observed disappearing shared target class
files; subsequent Maven builds ran sequentially. That failed attempt is not a passing check. Initial OpenAPI
schema/argument-name, validation and obsolete fixture/source assertions were resolved as described above;
no assertion was relaxed to accept a changed Case contract.

Exact Maven commands used the existing JDK 21 / Maven 3.9.11 wrapper, `-B`, and bounded process timeouts.
The affected command comes directly from the selector's structured `selected_maven_batches` in
`work/selection.json`; it selects the directly modified tests and required Case/contact/security/server
smokes, with the SQL opt-ins left disabled. Intermediate logs, trees and the pre-edit test-impact inventory
remain in `work/` and are not committed.

```bash
mvn -B -pl shale-server -am -DskipTests compile
mvn -B -pl shale-server -am -Dtest=OpenApiDocumentationTest,ShaleServerServiceConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-server,shale-desktop -am -Dtest=AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,ShaleAuthTokenServiceTest,ServerAuthSessionLogoutTest,DesktopApplicationInstanceVerifierTest,AdminSessionControllerTest,SessionControllerTest,SessionManagementContractTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,DesktopRememberedRecoveryTest,DesktopRuntimeSessionLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-server -am '-Dtest=*ControllerTest,ContactValueApiTest,ApiExceptionHandlerTest,OpenApiDocumentationTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl shale-server -am -Dtest=ShaleServerServiceConfigurationTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest,RuntimeSessionServiceTimingTest,PreTenantAuthenticationModuleContractTest,UserSessionDaoContractTest,UserSessionMigrationContractTest,SessionInvalidationPhase8AContractTest,SessionSecurityAuditMigrationContractTest,UserSessionServiceAdapterTest,AiScopeTest,AiSecurityContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base a0ce0d856ccbf8167ae0e48ed4cac8af19670f05 --head HEAD --format markdown --plan-output work/selection.json
mvn -B test
mvn -B -pl shale-server -am -DskipTests package
mvn -B -DskipTests dependency:tree
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
git diff --check
```

Before committing, selection used `--path` for every tracked worktree change; after commit the equivalent
`--base ... --head HEAD` command above verifies the identical path/test selection. The framework prerequisite
has no remaining local compatibility blocker. Resume the **separate** Phase 3B.2A task after this prerequisite
merges and its recorded checks are verified. That task still must implement and prove its maintained OAuth
library, configuration, metadata, namespace isolation, client/redirect and S256-only contracts; none are complete here.

The actual embedded server/context tests cover both the default schema-only test composition and full prod
composition using dummy loopback DB destinations; no SQL request is made. They check existing public health,
Swagger UI, application mapper/converter identity, null/default/date handling, and absence of generated
passwords, SecurityFilterChain/user-details beans, default Basic/form/session authority, management, OAuth,
RFC metadata and MCP surfaces. Existing catch-all advice returns sanitized 500 for unmapped static resources;
that historical fallback is retained and is not treated as a new endpoint or a new 404 guarantee.

Audit compatibility: no new sensitive read or domain/administrative operation exists. Existing PHI/session/
entity-action transactions and disclosure withholding remain authoritative; no new audit row/schema is needed.
Global AI capability remains **OFF**; absent tenant policy remains **OFF**. There are no usable AI credentials,
MCP tools/routes or Case AI disclosure. Production/Azure SQL, authoritative Case SQL acceptance, installed
Windows acceptance, deployment and installer/version publication are **unexecuted** and are not inferred from
Maven. The missing approved Case artifact and later operational/client acceptance gates remain independent.


### 10.16 Phase 3B.2A OAuth dependency, protocol and configuration foundation — 2026-10-22

**Status: COMPLETE at foundation/source/local verification scope.** 3B.1 remains COMPLETE at its recorded scope. 3B.2B, 3B.2C,
3B.2D and 3B.3+ are NOT STARTED. This is not OAuth credential-lifecycle completion or permission to
activate AI. PR #1904 is confirmed merged as `56153820d6347b2507143431d18c7403c362a81a`, the fetched base
of the separate `codex/phase-3b2a-oauth-foundation` worktree; the original checkout is preserved.
The historical merge gate in §10.15 is satisfied.

#### Maintained dependency and isolation

Java 21 / Boot **4.1.1** / Framework **7.0.9** / Security **7.1.1** / Tomcat **11.0.24** /
Servlet **6.1** / springdoc **3.1.1** remain the supported baseline. The server adds
`spring-security-oauth2-authorization-server` **7.1.1** and `spring-security-config` **7.1.1**,
both Boot/Security BOM managed. The effective graph also resolves Nimbus **10.9.1** and application
Jackson **3.1.5** under Boot management, and successfully starts the composed server. No standalone 1.x AS or older pin is forced. Framework 7's
`commons-logging` **1.3.6** is declared explicitly, also Boot managed: Security's nearer spring-core
edge excludes it and otherwise removes the existing framework runtime logging dependency during Maven
mediation. Security core/web/OAuth2 core/JOSE/resource-server are transitive libraries; their presence
installs no REST/MCP bearer validation. No Boot Security/Authorization Server starter auto-configuration,
JWK signing source, generated password or default operational authorization store is added.

`McpOAuthSecurityConfiguration` is the only SecurityFilterChain. Its matcher covers only `/oauth/mcp`
and descendants plus the authorization-server well-known namespace. There is no catch-all chain.
Existing `/api/**`, password login, me/refresh/logout, DESKTOP enrollment/remembered credentials,
request-scoped durable-session validation and service/controller authorization retain Shale authority.
The isolated chain disables Basic/form login/logout, request caching and Spring-session authority;
it uses a null security context repository and STATELESS policy. The foundation gate precedes security
context/client/AS processing. Every credential, consent, registration, device, PAR, revocation,
introspection and JWKS request remains an empty **404**, regardless of ordinary JWT, Basic, remembered
credential or Spring session. No resource-server bearer filter is enabled.

The maintained AS configurer actually initializes in explicit internal-composition tests. Its client
converters/providers are restricted to public/private-key profiles, with secret/Basic fallbacks removed.
Its assertion decoder factory rejects all use until pinned-key validation/shared replay is implemented;
it cannot fetch a remote JWKS. The configured client repository resolves nothing and rejects writes.
Authorization and consent persistence reject mutations; lookups resolve nothing; the token generator
returns no token. No property can enable credential issuance in this phase.

#### Canonical operator configuration

`shale.ai.oauth.composition-enabled=false` is explicit in the default configuration. The typed
`McpOAuthProperties` permits an absent origin only with composition off; any supplied malformed origin
fails startup even with composition off. Set `shale.ai.oauth.public-origin` to one canonical origin
without a trailing slash, userinfo, path, query, fragment, host-case/default-port aliases or unsafe
local/test production identity. Production requires HTTPS. Derived identities are exactly
`${publicOrigin}/oauth/mcp` and `${publicOrigin}/mcp`; there are no independent issuer/audience overrides
or Host/Forwarded-derived values. Resource validation requires exactly one matching value, rejecting
missing, duplicate, other, REST and trailing-slash resources.

`shale.ai.oauth.identity` defaults to PRODUCTION. DEVELOPMENT/TEST require a separate loopback or
reserved `.test`/`.invalid` identity and an explicit local/dev/test profile, and are rejected in prod/azure.
A configured production identity is rejected in a local/dev/test profile. These are operator
configuration boundaries, not tenant capability flags. Global capability and absent tenant policy
remain OFF; no integration or tenant is created/enabled by configuration or startup.

#### Truthful metadata and deferred resource contract

With composition explicitly enabled, only **GET**
`/.well-known/oauth-authorization-server/oauth/mcp` publishes RFC 8414 informational metadata:

```json
{
  "issuer": "${publicOrigin}/oauth/mcp",
  "response_types_supported": [],
  "grant_types_supported": [],
  "token_endpoint_auth_methods_supported": []
}
```

Empty arrays are intentional: omission would imply RFC default grants/client authentication. No
endpoint URL, PKCE capability, scope, refresh, OIDC, registration, device or MCP capability is advertised.
Host and arbitrary forwarded headers cannot alter the issuer. Other methods/metadata aliases and
trailing-slash variants are denied. Default-off composition publishes no metadata. The framework's
operational metadata builder requires authorization/token endpoints and nonempty capabilities; its
model constructor is private. Therefore the gate serializes a fixed immutable claim allowlist through
Spring's standard Jackson JSON HTTP converter instead of exposing the framework's inaccurate operational
defaults. OAuth protocol/cryptography/lifecycle remains the maintained implementation, not a custom issuer.

`OAuthMetadataContract.protectedResource` prepares only the RFC 9728 identity contract:
`resource=${publicOrigin}/mcp`, `authorization_servers=[${publicOrigin}/oauth/mcp]`.
It has no route/controller. `/.well-known/oauth-protected-resource/mcp` remains unmounted, as does `/mcp`;
actual protected-resource discovery/handling waits for 3B.4 and live 3B.2D credential validation. Tests
verify the exact one-resource contract and route absence. No MCP discovery is implemented.

#### Bound client mapping and request validation

`TenantBoundOAuthClientRepository` adapts the existing 3B.1 `Registration` and the verified
`AiIntegrationRegistration`, `AiIntegrationRedirect`, `AiIntegrationClientKey` field contracts into a
single explicitly bound owner snapshot. Construction requires the expected tenant and integration;
wrong tenant/integration/client/issuer/resource, inactive/expired registrations, malformed redirects,
missing/conflicting key profiles and invalid RSA/algorithm/owner data fail closed. It is not a Spring bean,
not a global registry and not wired into the AS's empty runtime repository. Saves are prohibited; the
audited 3B.1 integration service continues to own administrative writes.

PUBLIC maps only to `none`, with no secret/key. CONFIDENTIAL maps only to `private_key_jwt`, with local
pinned RSA 2048–4096 public keys/exponent 65537/RS256, credential-ID kids, no private key/JWKS URL/secret
fallback. Only currently valid, unrevoked keys are projected. Registered clients allow authorization-code
representation only, require PKCE and consent for both profiles, retain the two-scope registration ceiling
and specify reference access-token format. This represents the future profile; it does not issue a code/token
or validate an assertion. No access/refresh grant or signing credential is operational.

The existing confined SQL projections intentionally do not return all redirects/key material. This phase
adds no SQL/projection/grant expansion or fabricated runtime loader. Before using the adapter in 3B.2B/C,
load a fresh owner-qualified snapshot through an explicitly reviewed confined backend seam after provisional
client resolution, and recheck capability/tenant/registration/key state. Never use admin-list procedures,
a broad client-ID repository, caller tenant claims, or a cached snapshot as current integration authority.
This operational loading is a lifecycle prerequisite; the tested mapping foundation is complete on its own.

`OAuthRedirectPolicy` allows exact hosted HTTPS strings only; no wildcard/userinfo/query/fragment,
dot-path or encoded-path redirect aliases. For PUBLIC native HTTP callbacks only, the literal registered
`127.0.0.1` or `[::1]` and path stay fixed while the valid explicit port may vary. Hostnames, other IPs,
paths, HTTPS aliases and confidential loopback exceptions are rejected. No caller URL is reflected on
validation failure.

`OAuthAuthorizationRequestPolicy` is installed on the actual library authorization-code provider,
adds exact resource and redirect validation, retains library scope validation, and explicitly requires
`code_challenge_method=S256` with a canonical base64url SHA-256-sized challenge for both profiles.
Missing/plain/lowercase/duplicate/malformed methods/challenges/resources are rejected. Framework
`requireProofKey(true)` alone is not treated as S256 enforcement. Code consume/verifier validation,
CSRF/state/issuer/consent transactions and PKCE lifecycle remain 3B.2B.

#### Audit compatibility, verification and next increment

Only static public metadata/configuration, pure snapshot adaptation and denied protocol requests are new.
No sensitive read, tenant/admin mutation, credential issue/consume or business disclosure is performed;
no new PHI/security/entity-action audit row or schema is appropriate. Existing transactional 3B.1 audit
is unchanged. Future consent/code persistence must retain its authenticated actor, tenant/integration
and atomic audit requirements. No Case data was read from SQL or disclosed to AI.

All required local verification completed. Groups overlap and must not be summed as unique tests.

| Check | Final result / limits |
| --- | --- |
| Compile/dependency resolution | PASS; resolved AS/config/Security 7.1.1, Framework 7.0.9, Boot 4.1.1, Jackson 3.1.5, Nimbus 10.9.1 and Commons Logging 1.3.6; final dependency tree inspected. |
| OAuth config/client/redirect/S256/metadata/filter contracts | **12 PASS**, four classes, no failures/errors/skips. Includes default-off binding, negative startup cases, both client profiles, exact one-resource/redirect/PKCE rules, hostile Host/forwarded headers and no credential authority. |
| Embedded full production composition | Included in **17 PASS** OAuth/composition check; uses dummy loopback DB settings only; actual HTTP metadata/credential gate and no generated-password/session authority verified. |
| Existing auth/session plus OAuth/composition | **78 PASS**, 18 classes, no failures/errors/skips. Includes 21 desktop recovery/enrollment/lifecycle checks and existing password/me/refresh/logout/durable/remembered checks. Existing password and desktop controller tests now run through real OAuth composition. |
| REST/OpenAPI/validation/errors | **136 PASS**, 16 classes, no failures/errors/skips. Existing wire/schema/controller and minimized Case synthetic assertions retained. |
| Selector affected suite | **102 PASS**, 22 classes, no failures/errors/skips; security-data/server, tenant/audit/default-off/auth/session/OpenAPI plus new OAuth contracts. No SQL opt-ins selected or executed. |
| Critical reactor | **138 PASS**, 35 classes, all seven modules BUILD SUCCESS, no failures/errors/skips. Run once after focused/affected checks. |
| Selector Python | **27 PASS**, including explicit new server blocking-smoke selection. |
| Documentation/diff | Owning Markdown: 47 relative links/anchors, balanced fences, parseable JSON blocks; POM XML and complete changed-file/test mapping reviewed; `git diff --check` PASS. |

The initial fetch/HTTP probe failed inside the restricted sandbox. One retry with explicit network access
succeeded, based on the prerequisite's recorded fix. No repeated infrastructure retries. The first OAuth
run exposed Security's exclusion of Commons Logging; explicit BOM-managed declaration fixed it. The first
metadata compile proved the model constructor is private; the final static claim allowlist avoids that API
and the operational builder's inaccurate defaults. A test fixture's package-private remembered-secret helper
caused the initial affected compile to fail; it now uses a synthetic credential of the same format. Selector
verification required explicit blocking-smoke entries, not merely test inventory membership. These failed
checks are not reported as passes; the final focused/affected/critical commands above all succeeded.
The generated historical full-suite inventory is already stale beyond this task; broad regeneration was
reviewed and left out to preserve scope. No all-tests runtime result is claimed; the selector recommends it
informationally for the server POM/testing-infrastructure change.

Exact successful commands (existing JDK 21/Maven wrapper; per-command timeouts 90–240 seconds;
logs/selection/test-impact/check scripts remain in uncommitted `work/`):

```bash
/workspace/.tools/bin/mvn -B -pl shale-server -am -DskipTests compile
/workspace/.tools/bin/mvn -B -pl shale-server -am -Dtest=OAuthConfigurationTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthMetadataTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn -B -pl shale-server -am -Dtest=OAuthConfigurationTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthMetadataTest,ShaleServerServiceConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn -B -pl shale-server,shale-desktop -am -Dtest=OAuthConfigurationTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthMetadataTest,ShaleServerServiceConfigurationTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,ShaleAuthTokenServiceTest,ServerAuthSessionLogoutTest,DesktopApplicationInstanceVerifierTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest,DesktopRememberedRecoveryTest,DesktopRuntimeSessionLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn -B -pl shale-server -am '-Dtest=*ControllerTest,ContactValueApiTest,ApiExceptionHandlerTest,OpenApiDocumentationTest' -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 56153820d6347b2507143431d18c7403c362a81a --head HEAD --format markdown --plan-output work/selection.json
# Execute each selected_maven_batches argv from the structured plan, with -B, sequentially.
/workspace/.tools/bin/mvn -B test
/workspace/.tools/bin/mvn -B -pl shale-server -am -DskipTests dependency:tree '-Dincludes=org.springframework.security:*,org.springframework:*,org.springframework.boot:*,commons-logging:*,tools.jackson.core:*,com.nimbusds:*,org.apache.tomcat.embed:*,org.springdoc:*'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 work/check-docs.py
git diff --check 56153820d6347b2507143431d18c7403c362a81a HEAD
```

Selection initially used `--path` for every staged/working-tree change; after commit the equivalent base/head
command verifies the same affected argv. Unmatched historical class names in the broad focused auth argv
produce no tests and are not inferred passes; counts above come from actual Surefire class receipts.

**Exact next recommendation: 3B.2B — durable authorization-code + S256 PKCE + Shale self-consent transaction.**
First add the confined fresh registration snapshot seam described above. Bind the existing Shale-authenticated
human/tenant, registration/grant, exact redirect/resource, S256 challenge, state and issuer to a durable
60-second hash-only code transaction; test single-use atomic consume across replicas, replays/races,
wrong/absent/plain PKCE, substitution, CSRF/denial/expiry and audited consent. Do not enable usable
access/refresh issuance (3B.2C), token-to-VerifiedIntegrationContext (3B.2D), MCP or Case disclosure.
Keep every capability default OFF and preserve existing WEB/DESKTOP auth regressions.

No production/Azure SQL, SQL migration execution, tenant enablement, deployment, admin/consent UI,
installer or version publication is included. Authoritative Case SQL and operational/client acceptance
remain independent gates. This phase does not authorize the next data surface.

### 10.17 Phase 3B.2B authorization code, S256 and self-consent — 2026-10-22

**Status: COMPLETE at source/local synthetic verification scope.** Base `549584f8bcc968cb7371194149bdcc405d4a10be`
(merged #1905) was fetched and inspected in a separate task worktree. 3B.1/3B.2A retain their recorded
completion scopes. At this increment's completion, 3B.2C, 3B.2D and 3B.3+ were NOT STARTED (subsequent progress: §10.18). This increment creates authorization codes
and grants only; it does not create a usable AI access/refresh credential, MCP surface or Case disclosure.

#### Maintained endpoint and existing human authority

The Security 7.1.1 Authorization Server request and consent providers remain the protocol engine.
`shale.ai.oauth.authorization-enabled=false` is a separate default-off browser transaction switch;
composition must also be explicitly enabled. SQL global capability, tenant policy, active tenant/user,
registration/key/grant and expiry remain independent live gates. No real tenant is enabled.

Only `GET /oauth/mcp/authorize` starts a flow, with `response_type=code`, explicit closed scopes,
bounded nonempty state, one exact configured resource, approved redirect and canonical S256 challenge.
Only form `POST /oauth/mcp/authorize` completes approval/denial. The maintained converters/providers
validate requests and client/consent relationships. Unknown fields (including tenant/UserId/IntegrationId/
issuer substitution), duplicate singleton parameters, unsupported flows and absent/plain/malformed PKCE
are rejected. Repeated POST scope fields allow explicit approval of either or both closed scopes.

The human bridge reuses existing signature verification, durable session/JTI validation and stored
`UserSessions.ClientType=WEB`; it rejects legacy unbound JWTs, DESKTOP/MOBILE sessions, remembered
credentials, Basic/client credentials, development identity headers and generic Spring session principals.
Subject is durable UserId; email is never authority. No Spring account, login page, session cookie,
remembered credential or WEB JWT is persisted by OAuth. Every browser request must supply its existing
WEB bearer header. The Spring context contains only tenant/UserId/SessionId and is cleared after the request.
The tenant SQL lease is initialized through the existing `RuntimeConnectionProvider`, using this identity.

`GET /oauth/mcp/consent` is a minimal authenticated JSON protocol surface: registered client name/ID,
tenant, requested scopes, one-hour grant deadline and bounded disclosure notice. It is intended for the
existing Shale browser to fetch with its own session, then submit explicitly selected scope fields and
the server's intent state. It is not a login system or a browser UI redesign. No WEB bearer or remembered credential is placed in
URLs. There is no ambient cookie authority: bearer authentication plus a random, durable, single-use,
original-WEB-session-bound consent nonce protects approval; the client-supplied OAuth state is distinct.
Denial has durable intent history and produces no grant/code; a validated denial returns `access_denied`,
original state and canonical issuer to the approved redirect. Invalid requests fail locally without redirects.
The future browser UX can consume this surface without changing the security authority.

Canonical issuer/resource remain configuration-derived; Host/Forwarded headers cannot select them.
The supported AS response-handler extension emits RFC 9207 `iss` with the exact original state and only
the validated redirect. Default-off metadata remains the 3B.2A informational allowlist. Explicit browser
composition advertises the authorization endpoint, code/S256/two scopes and issuer-response support;
token grant and authentication lists remain explicitly empty, with no token endpoint or refresh capability. All token/revoke/introspect/
JWKS/device/PAR/registration endpoints remain blocked before AS processing. No `/mcp` or protected-resource
metadata route is added. No ordinary auth/controller route matches the OAuth chain.

#### Confined fresh client loading and durable transaction

The new [manual SQL candidate and authority inventory](../sql/ai-integration/README.md#phase-3b2b-authorization-transaction-candidate)
adds one strict tenant table and seven bounded procedures without modifying historical migrations,
receipts, grants or shared RLS functions. The new installer validates the old source/catalog receipt and
refuses pre-existing OAuth objects rather than overwriting them. It is executed only in disposable fixtures.

`AiLoadOAuthClientV1` resolves exactly the requested globally unique external client ID under the existing
human's authenticated tenant context. It returns minimum identity/type/ceiling/state/expiry/redirect/key
fields plus bounded consent display name; inactive/revoked/expired/foreign/unknown registrations fail
closed. There is no unrestricted integration repository, positive cache or wider bootstrap/auth pool grant.
Public clients remain secretless `none`; confidential clients remain local pinned RSA/RS256
`private_key_jwt`. Both require S256. Full assertion signature/audience/lifetime/shared-JTI replay handling
is deferred to 3B.2C, and no current token endpoint accepts assertions. The authorization snapshot pins a
live local key reference; 3B.2C must reconcile that binding with its actually verified assertion key.

The fixed-column transaction binds tenant, integration, user, WEB session identifier, transaction UUID,
client, issuer, resource, exact redirect, original state, requested/approved closed scopes, S256 challenge,
registration RowVer, optional pinned key, creation/deadline and final GrantId. No serialized authentication,
password, WEB bearer, raw session token, desktop secret, prompts or Case data is stored. Five-minute
intents reuse `AiConsentIntent`. Grants reuse `AiDelegationGrant` and expire one hour from intent creation;
registration must have at least that lifetime remaining. Scope decisions are explicit per transaction;
there is no accumulated Spring consent that silently restores a reduced or newly requested scope.

Approval records only an intent decision. Completion atomically consumes the intent, creates the existing
self-only delegation grant, appends the established security/control audit and persists the code hash.
It rechecks startup/global/tenant/actor/registration/key authority and exact scopes under the existing
capability/tenant application locks and held actor/tenant SQL rows. A registration version change or a
same-user/integration grant revocation since initiation makes the intent stale. No admin may complete
for another user, and a tenant A client cannot authorize a tenant B user. Committed disablement before
completion prevents issuance. Failed audit rolls back grant, intent consumption and code together.

#### Code lifecycle, replay and audit

Codes contain 32 CSPRNG bytes (256 bits), encoded opaque base64url and returned once. SQL stores only
SHA-256 of the presented ASCII code, with database-UTC issuance and **exactly 60 seconds** expiry.
The unique hash record retains every transaction binding and GrantId. Immutable/monotonic triggers,
strict RLS, tenant-qualified FKs and procedure-only permissions preserve ownership and consumed history.

The internal consume seam computes S256 from the supplied verifier and atomically checks/consumes the
exact hash/client/issuer/resource/issued redirect/challenge and live grant/registration/key/user/tenant/
global authority. Confidential clients receive no verifier bypass. Exactly one concurrent consumer wins;
replay and expiry fail with no retry grace. Committed consumption and grant history survive replacement
JVMs. SQL locking serializes approval/revocation; a completion authorized first can create its fresh grant,
while a revoke committed first makes a pre-existing intent stale. Previously revoked grants are never
restored or edited. This is an authorization transaction boundary, not a future network disclosure claim.

Consume returns only a transaction/GrantId/scope validation receipt. It cannot issue an access/refresh token,
create an `AiDelegatedSession`, construct `VerifiedIntegrationContext` or invoke a Case operation. The seam
currently requires the trusted original WEB runtime context; 3B.2C must implement the reviewed confined
exchange bootstrap and client authentication before an AI client can redeem codes through HTTP.

Audit compatibility uses the existing `EntityActionAuditLog` schema, `AI_DELEGATION_GRANT/CREATED`, current
tenant/actor, stable grant entity ID, correlation UUID and `AI_SCOPE_MASK` only. Grant and code completion
share this audited SQL transaction; any append failure aborts it. New scope approval creates a fresh audited
grant rather than widening old consent. Intent preparation/approval/denial, code issuance and consumption
also retain their intrinsic bounded transaction history; no new entity-action vocabulary is invented for
these records. There are no sensitive business reads or disclosure events in this phase. Audit metadata
contains no raw code, verifier, secret/digest, JWT, redirect/state, prompts, PHI or Case data. 3B.3 disclosure
audit remains separate and unfinished.

#### Test impact, evidence and remaining boundary

Pre-edit inspection covered existing OAuth mapping/configuration/metadata/isolation tests, 3B.1 data/SQL
contracts, existing WEB/DESKTOP session authority and selector configuration. Existing foundation tests
retain their default-off expectations. New `ShaleOAuthHumanTest` enters affected and critical selection;
explicitly opt-in SQL/HTTP fixtures remain separate from default checks. The changed data default-off
contract now checks the new adapter too. Full changed-file/reference review is required before closeout.

Evidence is **local source + synthetic disposable SQL + maintained servlet-filter protocol tests**.
The SQL fixture uses actual distinct non-bypass runtime identities, strict RLS and committed transactions.
The servlet flow uses real maintained AS filters and restricted SQL plus a synthetic WEB identity store;
existing authentication regression tests separately exercise production session/controller adapters.
Expiry includes explicitly labelled owner-only timestamp fault injection. Replacement-JVM replay is actual
separate-process execution against committed SQL. This is not production, authoritative Case SQL,
independent OAuth-client/browser UX, Azure, deployment or operational acceptance evidence.

Final local verification passed. Groups overlap and must not be added as unique test counts.

| Check | Result / scope |
| --- | --- |
| Production/test compilation | PASS through final focused, affected and critical Maven reactor stages; no dependency/version change. |
| Full approved synthetic SQL fixture | **35 PASS**, no failures/errors/skips; includes ten OAuth transaction/code tests plus all existing foundation tests. |
| Final OAuth config/mapping/filter/metadata/WEB bridge focus | **14 PASS**, no failures/errors/skips. |
| Maintained servlet authorization/consent/denial/callback flow | **4 PASS**, real restricted disposable SQL, synthetic WEB identity fixture, no usable token. Included in affected execution too. |
| WEB/DESKTOP auth/session, OAuth and full composition | **84 PASS**, including 22 desktop lifecycle/recovery/enrollment checks; no failures/errors/skips. Final metadata-only refinement additionally passed the 14-test focus above. |
| Selector affected security-data/server suite | **126 PASS**, 22 classes, no failures/errors/skips; explicitly opted into the disposable SQL fixtures. |
| Critical reactor | **140 PASS**, 36 classes, all seven modules BUILD SUCCESS; no failures/errors/skips. |
| Selector Python | **27 PASS**. Full historical suite escalation was informational; `-Pall-tests` was not run or claimed. |
| Documentation / diff | Four owning Markdown files, **56 relative links/anchors**, balanced fences, selector JSON and complete changed-file/reference review PASS; `git diff --check` PASS. |

Executed commands (Maven via the environment's existing JDK21/proxy-aware wrapper; timeouts 120–180 seconds):

```bash
/workspace/.tools/bin/mvn -B -pl shale-server -am -DskipTests compile
# Initial standalone base compile passed; final implementation compilation is also part of every test command below.
# Only the explicit disposable SQL fixture receives SHALE_AI_SQL_PASSWORD privately; never in argv/logs.
SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI SHALE_AI_SQL_PORT=14337 \
  /workspace/.tools/bin/mvn -B -pl shale-server -am \
  -Dtest=AiSecurityLocalSqlServerTest,OAuthAuthorizationFlowTest,ShaleOAuthHumanTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthConfigurationTest,OAuthMetadataTest,AiSecurityContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn -B -pl shale-server -am \
  -Dtest=OAuthMetadataTest,OAuthConfigurationTest,OAuthClientMappingTest,OAuthFilterIsolationTest,ShaleOAuthHumanTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
# With the same disposable SQL opt-in for OAuthAuthorizationFlowTest:
/workspace/.tools/bin/mvn -B -pl shale-server,shale-desktop -am \
  -Dtest=OAuthAuthorizationFlowTest,ShaleOAuthHumanTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthConfigurationTest,OAuthMetadataTest,ShaleServerServiceConfigurationTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,ServerAuthSessionLogoutTest,DesktopApplicationInstanceVerifierTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest,DesktopRememberedRecoveryTest,DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,DesktopRememberedSignInDiagnosticsTest,DesktopApplicationInstanceLifecycleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 549584f8bcc968cb7371194149bdcc405d4a10be --head HEAD --format markdown --plan-output work/selection.json
# Execute the structured selected_maven_batches argv with -B and explicit disposable SQL opt-in, sequentially.
/workspace/.tools/bin/mvn -B test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 work/check-docs.py
git diff --check 549584f8bcc968cb7371194149bdcc405d4a10be HEAD
```

Initial Git/Maven/Docker sandbox network denials were diagnosed and corrected with one concrete
permission-scoped retry per affected operation, without infrastructure retry loops. Implementation
verification exposed and corrected adapter return-type/checked-exception compile errors, new-table RLS
batch resolution, fixture case/session assumptions and the Security 7 authorization-filter ordering.
An overlapping Maven invocation caused transient missing classes; subsequent runs were serialized.
These failed checks are not counted as passes. Final review strengthened substitution tests to give
other users valid WEB sessions and enable both disposable tenants, so ownership rejection is proven
independently of default-off/session denials. Metadata advertises code responses while leaving token
grant/authentication lists empty until actual token exchange exists. No required local check remains blocked.

No production/Azure SQL, deployment, tenant enablement, installer/version publication, admin UI,
MCP route or Case disclosure occurred. Existing independent production/authoritative Case SQL and
real-client/browser UX acceptance remain outside this source/synthetic increment.

**Exact next recommendation: Phase 3B.2C — opaque access-token and rotating refresh lifecycle.** First
implement the confined exchange lookup and live owner-qualified runtime gate, exact client/redirect/resource/
issuer/S256 verification, and pinned `private_key_jwt` assertion verification with shared JTI replay state.
Preserve atomic one-use code consume and add a fail-closed credential-creation transaction, hash-only opaque
five-minute access and absolute/idle-clipped rotating refresh families with reuse revocation, reset/key/
registration/grant/disable races and response-loss/restart tests. Only then expose supported token exchange.
Do not implement token-to-VerifiedIntegrationContext/resource-server validation (3B.2D), MCP or Case access
in that increment. No production SQL, deployment, tenant enablement or installer/version publication.

### 10.18 Phase 3B.2C.1 authorization-code exchange boundary — 2026-10-22

**Status: COMPLETE at source/local synthetic verification scope.** Aggregate 3B.2C remains IN PROGRESS.

Source base: `ffe537bca1064b7e4715f35436e69a416baffc40`, merged #1906, fetched into a separate
`codex/phase-3b2c1` worktree. The original checkout is preserved. This phase validates/consumes only;
usable credential lifecycle remains unfinished. No MCP or Case disclosure is implemented.

#### Protocol composition and client proof

An explicit default-off `shale.ai.oauth.exchange-enabled` switch enables only form POST
`/oauth/mcp/token`, within the existing isolated maintained Spring Authorization Server chain.
The maintained public/JWT client converters and authorization-code converter feed dedicated provider
extensions. Default client/grant providers are removed; password, implicit, client credentials,
token exchange, device, refresh and arbitrary extension grants have no provider or converter.
Singleton allowlisting rejects secret attempts, caller authority fields, query parameters, duplicate
parameters, missing/malformed PKCE, and ordinary WEB/DESKTOP/remembered/Basic bearer headers.
No Spring cookie/session, resource-server filter, REST authentication or human login fallback is added.

PUBLIC uses `none`, client ID only as identifier, exact code/client/redirect/canonical resource and
mandatory S256. The verifier must be 43–128 RFC 7636 unreserved ASCII characters. Java computes
base64url(SHA-256(ASCII verifier)); SQL compares the original immutable challenge. No plain challenge,
challenge mutation or verifier persistence exists. Cross-code/client substitution cannot bypass that binding.

CONFIDENTIAL additionally requires `private_key_jwt`, explicit client ID, RS256, a local pinned RSA key
at least 2048 bits, exact client `iss` and `sub`, one exact configured token-endpoint `aud`, valid iat/exp,
positive lifetime at most 60 seconds, and nonempty bounded jti. No skew extends that validity. Nimbus JOSE
verifies the signature with the freshly loaded exact tenant/integration/client public key. Caller jku,
x5u, embedded JWK/certificate chains, critical/custom headers, HS/PS/EC, unknown/expired/revoked keys and
client-secret fallback fail closed. There is no remote decoder/key loader. SQL rechecks the same key at
final consume and requires the original authorization-pinned key; rotation or registration version change
requires new consent/code, rather than rebinding old code to a replacement key.

#### Durable validation, replay, result and audit

The [operator/schema record](../sql/ai-integration/README.md#phase-3b2c1-exchange-validation-candidate)
is authoritative for the new additive candidate, receipts, RLS, permissions, dedicated bootstrap config,
retention and rollback. Bootstrap hashes the raw code and selects only tenant/user identifiers from one
exact code/client/issuer/resource. It returns no grant/session/client inventory. Fresh initialized runtime
leases use the existing production initializer; no auth/global pool gains tenant access.

`AiExchangeOAuthCodeV1` rechecks global capability, tenant policy/activity, active nondeleted/nonremoved
user, original live WEB session, integration/registration state and expiry/version, pinned key, original
grant tenant/user/integration/scopes/expiry/revocation, code issuer/resource/redirect/client/challenge and
single-use expiry. Existing global capability/tenant locks serialize control changes and exchange;
held authority rows prevent a committed revoke preceding final authorization from slipping through.
Expiry is checked again against database UTC at consumption. Reduced ceilings permanently revoke affected
grants under existing Phase 3B.1 rules; expansion/re-enable never un-revokes descendants.

The shared SQL unique tenant/integration/key/JTI-digest key is the replay authority across replicas and
replacement JVMs. Replay reservation, code consume and bounded existing grant UPDATED audit are one
transaction. Failed validation/audit rolls back all three; no failed attempt burns either input. Exactly
one valid exchange of a code or simultaneous assertion use across codes wins. Committed consume/replay
survives restart. Expired replay digests are removed in bounded tenant batches; inactive-tenant retention
is explicitly opportunistic, without extending validity. No process cache is security authority.

`AiOAuthExchangeStore.ValidatedExchange` is an immutable, redacted internal receipt of verified tenant,
acting user, integration/client/grant/transaction, optional authenticating key, immutable approved scopes,
issuer/resource, minimum grant/registration/original-WEB-session/key deadline, and server correlation.
It contains no code, verifier, assertion, raw JTI, WEB JWT, password, prompt, PHI or Case data. It is never
serialized to the client, never a bearer, and never constructs the final `VerifiedIntegrationContext`.

After successful SQL validation the maintained grant provider deliberately raises controlled
`server_error` / `Credential issuance is disabled in this phase.` It creates no placeholder token,
access/refresh row or delegated session. Success consumes the inputs despite that protocol error; clients
must obtain new consent/code. Response loss/close error also withholds the result without undoing a committed
consume. Metadata stays authorization-only: token endpoint absent and token grant/auth lists empty.
Advertising operational token issuance would be false; framework token generation remains disabled.

Audit compatibility uses the existing `AI_DELEGATION_GRANT/UPDATED`, `SHALE_SERVICE`, server correlation,
verified actor/tenant and only AI_SCOPE_MASK. Required audit failure aborts consumption/replay. No raw
code/verifier/assertion/JTI/signature/digest/key material/state/redirect/exception/PHI enters metadata.
Untrusted denial/lookup has no entity-action row because no trusted actor may exist. Durable code/replay
state provides bounded intrinsic security history. Phase 3B.3 access/disclosure audit remains separate.

#### Test impact, evidence and verification

Pre-edit inspection covered `AiSql`, `AiOAuthTransactionStore`, `AiSecurityContractTest`, the entire SQL
fixture's OAuth/revoke/race/restart tests, OAuth configuration/mapping/isolation/metadata/human/HTTP tests,
and existing ordinary auth/session regressions. New tests cover migration receipts/permissions/RLS, PKCE
vectors and malformed input, assertion claims/algorithms/local keys/URLs, actual maintained HTTP filters,
code/assertion races, transaction-coupled audit faults, expiry, authority/revoke and replacement JVMs.
Synthetic expiry explicitly uses test-owner timestamp fault injection; it is not wall-clock evidence.

Verification results are recorded below. Evidence is local source/static contracts,
synthetic loopback SQL with separate restricted identities, and maintained servlet filters. The HTTP WEB
identity store is synthetic; ordinary production authentication adapters are covered separately. These
results do not establish production/Azure, authoritative Case/RLS, independent client/browser UX,
fleet/deployment or external penetration-test acceptance. All capability/tenant defaults remain OFF.

**Next phase recommendation:** 3B.2C.2 must replace the intentional issuance-disabled terminal with atomic
hash-only CSPRNG opaque access and refresh issuance in the same consume/replay transaction. Reuse the
verified result and existing session/credential tables without accepting caller identities. Clip five-minute
access, eight-hour refresh idle and at-most-24-hour consent/session absolute limits to every live deadline.
Implement single-use refresh rotation, consumed-refresh reuse-family revoke, response-loss recovery via
fresh consent, key/security-reset revocation and replica/race/restart/audit-fault tests before advertising
usable token grants. Keep 3B.2D bearer resolution/VerifiedIntegrationContext and 3B.3+ disclosure separate.

Explicitly: no usable access token, no refresh token, no bearer authentication, no MCP route, no Case
disclosure; WEB/DESKTOP auth remains authoritative; AI defaults OFF; no production/Azure SQL, deployment,
real tenant enablement, installer/version publication or Curtis-specific behavior.


Local verification uses Maven 3.9.11 / JDK 21.0.12.1; `mvn` below denotes the existing
`/workspace/.tools/bin/mvn` wrapper. SQL variables select only the explicitly approved disposable
loopback fixture on port 14337; its generated SA password was supplied privately through the environment.
Final groups overlap and are not additive unique-test counts:

| Check | Final result |
| --- | --- |
| Focused core/data/OAuth contracts and protocol | 86 PASS, no failures/errors/skips |
| Full disposable SQL fixture (included above) | 50 PASS, including 15 exchange/replay/RLS/expiry/revoke/race/restart tests |
| Maintained servlet authorization and token boundary (included above) | 7 PASS |
| WEB/DESKTOP server/auth/session regressions | 51 PASS |
| Desktop enrollment/client/recovery/diagnostics plus remembered-login UI contract | 21 PASS |
| Selector affected security-data/server suite | 152 PASS, 25 classes, SQL opt-ins explicitly enabled |
| Critical reactor | 148 PASS, 39 classes, all seven modules BUILD SUCCESS; no failures/errors/skips |
| Selector Python | 27 PASS |
| Documentation/diff | 60 relative links/anchors, balanced fences, JSON and git diff --check PASS |

Initial offline dependency absence was resolved through the configured network. Migration DDL ordering,
public NULL timestamp binding, fixture expiry constraints, a test import and framework client-method/
request-authorization composition failures were corrected during focused debugging. They are not counted
as passing runs. No full reactor was used to debug them. The selector's all-tests escalation is
informational; historical all-tests were not executed. No local acceptance is inferred from a skip.

```bash
mvn -o -pl shale-server -am -Dtest=AiScopeTest,AiSecurityContractTest,AiOAuthExchangeContractTest,AiSecurityLocalSqlServerTest,OAuthAuthorizationFlowTest,OAuthCodeExchangePolicyTest,PinnedClientAssertionTest,OAuthConfigurationTest,OAuthClientMappingTest,OAuthFilterIsolationTest,OAuthMetadataTest,ShaleOAuthHumanTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-server -am -Dtest=AuthControllerTest,DesktopSessionControllerTest,SessionControllerTest,AdminSessionControllerTest,DurableSessionAuthTest,ServerAuthSessionLogoutTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,DesktopApplicationInstanceVerifierTest,SessionManagementContractTest,OAuthFilterIsolationTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-desktop -am -Dtest=DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,DesktopRememberedRecoveryTest,DesktopRememberedSignInDiagnosticsTest,LoginRememberedSignInContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base ffe537bca1064b7e4715f35436e69a416baffc40 --head HEAD --format markdown --plan-output work/selection.json
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
mvn -DargLine=-Djdk.attach.allowAttachSelf=true test
git diff --check
git diff --check ffe537bca1064b7e4715f35436e69a416baffc40 HEAD
```

Focused/affected SQL runs set `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`,
`SHALE_AI_SQL_PORT=14337`, and the private `SHALE_AI_SQL_PASSWORD`.
The exact selector `selected_maven_batches` arguments were executed unchanged with `-o` and
`-DargLine=-Djdk.attach.allowAttachSelf=true` added, using Python `subprocess.run` argument arrays,
`check=True` and a 240-second deadline. Other Maven runs also had 120–240-second shell deadlines.
Documentation verification checks every relative link/anchor in the four changed Markdown documents,
fences, selector JSON, and the complete changed-file/test mapping. Original checkout remains untouched.


No remaining local 3B.2C.1 verification blocker. Authoritative Case/non-bypass deployed permissions/RLS,
operational activation and independent client acceptance remain separate existing gates, not inferred passes.
Final roadmap:

```text
3B.1     COMPLETE
3B.2A    COMPLETE
3B.2B    COMPLETE
3B.2C.1  COMPLETE
3B.2C.2  NOT STARTED
3B.2D    NOT STARTED
3B.3+    NOT STARTED
```

Aggregate **3B.2C remains IN PROGRESS**; usable OAuth credential lifecycle is not complete.

### 10.19 Phase 3B.2C.2 opaque issuance and rotating refresh — 2026-10-22

**Status: COMPLETE at source/local synthetic verification scope.** Aggregate 3B.2C is COMPLETE.
3B.2D and 3B.3+ remain NOT STARTED. Base is merged #1907,
`6d39e01e59f1f090f9ef62a616b89f4486f1dbfd`, fetched into separate `codex/phase-3b2c2` worktree;
the original checkout remains untouched. The [SQL lifecycle record](../sql/ai-integration/README.md#phase-3b2c2-opaque-credential-lifecycle-candidate)
owns schema/permissions/retention detail; the [future deployment sequence](../azure-app-service-deployment.md#future-ai-oauth-deployment-sequence--through-phase-3b2d)
retains JAR → accumulated SQL → verifier → live OAuth acceptance. Nothing here authorizes deployment.

#### Issuance and durable identity

The issuer's sole internal input is `ValidatedExchange`, produced by the existing live exchange boundary.
One fresh JDBC transaction carries client/PKCE proof and code/JTI consume, receipt mapping, live durable
lineage revalidation, one delegated family and two token hashes plus required security audit to commit.
The unique authorization transaction index prevents a second family from a reused validation receipt.
SQL Server JDBC `setAutoCommit(false)` uses IMPLICIT_TRANSACTIONS; explicit transaction setup disables
implicit nesting and establishes exactly one transaction before AiLockV1. Exchange commits only if it
owns the transaction; composed issuance commits after family creation. Commit acknowledgement and
successful lease close precede return of raw credentials; any validation/insert/audit failure rolls back.

Independent access/refresh values each contain 32 CSPRNG bytes, encoded as opaque unpadded base64url
(43 characters). SQL receives/stores only SHA-256 digests. No JWT, WEB signing key, client-visible claims,
ID token, profile, tenant/integration metadata, PHI or Case data. Spring's maintained token-response
handler emits only access_token, token_type=Bearer, expires_in, refresh_token and scope.

Existing AiDelegatedSession/AiOAuthCredential remain authority; no parallel token table. Immutable session
lineage adds authorization transaction, exact client/issuer/resource, consent time and capability revision.
Tenant-qualified authorization/grant/session/user/integration/key FKs and existing strict RLS/direct-table
denials remain. Each credential independently stores tenant/user/integration/grant/session, exact scope,
kind, generation, issue/expiry/consume/revoke and digest. Its immutable session supplies exact client,
issuer/resource and authenticating key. Refresh generation uniquely reconstructs parent/replacement within
that family; normal rotation never deletes consumed or expired history. Migration is additive, manual,
transactional, repeat-safe and catalog/source-receipted; the cumulative verifier detects catalog drift.

#### Lifetime, refresh and response loss

Access maximum is five minutes. Refresh idle maximum is eight hours; delegated absolute maximum is
24 hours from grant consent, fixed at issuance and never extended by refresh. Issuance and refresh use
the earliest applicable session, grant, registration, original WEB-session and pinned-key deadline.
Existing 3B.2B one-hour grants usually shorten the pilot's maximums. No offline_access or scope expansion.

Only authorization_code and refresh_token grants exist. Both keep public `none` and confidential locally
pinned RS256 private_key_jwt, exact client/audience and fresh shared assertion-JTI protection. Confidential
refresh must use the original authenticating key; replacement keys cannot inherit existing families.
Refresh permits only exact client/resource/refresh credential and optional required client proof; ordinary
WEB/DESKTOP credentials, secrets and caller tenant/user/grant/scope fields cannot supply authority.
The REFRESH-only confined bootstrap returns provisional tenant/user for one exact digest/client/configured
issuer/resource, including consumed history. ACCESS cannot resolve there; fresh tenant runtime authority
is still required. No access-token presentation or final VerifiedIntegrationContext exists in this phase.

A successful refresh atomically consumes the old token, inserts a new access/refresh pair, advances durable
generation/idle/activity and appends required audit. Shared-global/exclusive-tenant transaction locks,
held live authority rows, conditional consume and unique generation give exactly one concurrent winner.
A consumed-token reuse commits full session/access/refresh descendant revocation before Java returns
invalid_grant. First revocation timestamp/reason remains unchanged. The concurrent loser is reuse, so
it also revokes the winner's newly issued pair. **A lost rotation response followed by retry is intentionally
family-revoking:** no grace interval; recover through new authorization/consent. A SQL/commit/close outage
never returns credentials optimistically. Consumed state, descendants and revocation survive replacement JVMs.

#### Live authority and security audit

Global/tenant capability, tenant/user eligibility, integration/registration state and expiry, current ceiling,
grant/session, original WEB lineage, issuer/resource and pinned key are checked live without positive caches.
Session revocation cascades to every credential; grant/key/integration/WEB revocation, user deletion/removal
and tenant deactivation cascade to sessions. Existing UserDao administrative password/security reset revokes
WEB sessions in its owning transaction, activating the same family cascade. A restricted runtime SQL test
proves that transition and the actual admin audit actor. Re-enable never un-revokes descendants. Global
capability revision mismatch permanently invalidates pre-disable families after re-enable; the next
authenticated refresh persists DISABLED family revocation. Existing transaction-coupled global control
revision audit records that invalidation without a global tenant-data sweep or expanded global grants.
Existing trusted session/quota gates also compare that revision for lifecycle-bound families; neither
an existing internal session reference nor an earlier internal context restores pre-disable authority.
This updates existing family checks only and adds no presented-token validation or new context path.

Audit compatibility reuses AI_DELEGATED_SESSION CREATED/UPDATED/DEACTIVATED with existing closed
AI_SCOPE_MASK/ACTIVE metadata, verified tenant/actor, numeric session ID and server correlation. Automatic
cascades use current actor context, or affected user as service subject if no actor context exists. Required
audit commits with state or rolls it back. Invalid/unowned input adds no entity row because no trusted subject
exists; durable code/replay/consume history remains intrinsic security state. No token, digest, code, PKCE
verifier, assertion, JTI, key material, prompts, PHI or Case data enters audit. No audit schema change;
3B.3 access/disclosure audit remains separate and NOT STARTED.

Enabled RFC 8414 metadata advertises only code/refresh, none/private_key_jwt/RS256 and S256. Default-off
composition remains truthful. RFC 7009 is deferred to the smallest client-authenticated family-revocation
follow-on before 3B.2D; no custom unauthenticated route exists. Existing authenticated internal session
revocation remains available. No OIDC, client-credentials/password/implicit/device/token-exchange,
arbitrary-scope or MCP capability is advertised.

#### Test impact, verification and evidence

Pre-edit inventory covered exchange/SQL seams, all AI fixtures, metadata/configuration/client mapping,
filter isolation/human/HTTP tests, ordinary auth/session adapters and selector ownership. Existing live
issuance-disabled assertions were replaced; historical 3B.2C.1 tests/docs remain historical. New tests
exercise receipt/family uniqueness, exact digest state, five-minute and clipped expiry, eight-hour idle,
24-hour consent deadline, all authority failures, key/JTI rules, rotation/reuse/response loss, audit failures,
committed revoke ordering, races and replacement JVMs. Test-only verifier privileges are separate from
runtime identities; ACCESS persistence observation is not a production access resolver.

Final executed results follow; groups overlap and must not be added as unique totals.

| Check | Result / scope |
| --- | --- |
| Focused migration / initial race / response-loss smoke + static credential contracts | 5 PASS |
| Focused complete credential SQL lifecycle + static credential contracts | 41 PASS initially; final affected run additionally covers the new global-revision gate scenario |
| Focused final epoch, restricted reset and cumulative verifier | 5 PASS |
| Final verifier / replacement ACCESS and REFRESH JVM probes + HTTP/request/metadata/assertion focus | 23 PASS, including 8 maintained HTTP flows |
| Selector affected security-data/server suite, SQL explicitly enabled | 197 PASS, no failures/errors/skips, in two disjoint bounded partitions |
| Full synthetic SQL coverage within affected suite | 90 PASS: 50 foundation/authorization/exchange and 40 credential lifecycle scenarios |
| WEB/DESKTOP server/auth/session regressions | 51 PASS |
| Final critical reactor | Final-source rerun: 152 PASS across all seven modules; zero failures/errors/skips. An earlier attempt was not executed because approval review was temporarily out of credits; after approved cleanup proved the service available, one bounded rerun passed. |
| Selector Python | 27 PASS |
| Documentation / diff | Relative links/anchors, fences, JSON and git diff checks PASS; final counts are recorded in the PR. |

Evidence is compiled/static contracts, actual separate restricted loopback SQL identities, shared committed
transactions/concurrency and replacement JVMs, plus maintained servlet filters. The HTTP WEB identity store
is synthetic; ordinary production auth adapters have separate regressions. Expiry injection explicitly uses
test-owner timestamp changes. This does **not** establish production/Azure, authoritative Case/RLS,
independent client/browser UX, fleet/deployment or penetration-test acceptance. Defaults stay OFF.

Initial DDL batching and a grant timestamp reference failed before issuance, then were corrected. Automatic
review initially blocked further SQL retries under the original anti-hang rule; the user explicitly approved
bounded corrective runs, which completed. One stale concurrency rejection was resolved by proving the prior
run had exited before starting the next. Failed/blocked attempts are not passes. Git/dependency sandbox
errors and Mockito attachment were diagnosed with concrete permission/startup-agent corrections. An affected-run
test-edit mistake in the self-revocation actor assertion was corrected; the new restricted reset fixture
also needed ordinary UserSessions SELECT/UPDATE grants, without any AI-table privilege expansion. Both
audit paths then passed focused verification and final affected checks. No full
reactor was used to debug code; Maven/SQL commands were serialized and bounded at 120–240 seconds.
Both owned disposable SQL containers were removed, including final loopback-only `shale-ai-3b2c2`. The generated password file was removed.

Maven is the existing `/workspace/.tools/bin/mvn` wrapper (Maven 3.9.11/JDK21), with `-o -B` and
`-DargLine=-javaagent:/workspace/.cache/maven/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar`.
SQL runs privately supply SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI, SHALE_AI_SQL_PORT=14337 and the
generated SHALE_AI_SQL_PASSWORD; no secret is placed in command arguments or logs.

```bash
# Focused disposable credential lifecycle (240-second outer process budget is enforced):
mvn -pl shale-data -am -Dtest=AiOAuthCredentialContractTest,AiSecurityLocalSqlServerTest#credential* -Dsurefire.failIfNoSpecifiedTests=false test
# Final probes, cumulative verifier (inside migration test), maintained OAuth servlet flows:
mvn -pl shale-server -am -Dtest=AiOAuthCredentialContractTest,AiSecurityLocalSqlServerTest#credentialMigrationRepeatAndCatalogDriftFailClosed+credentialRestartObservesAccessRefreshAndConsumedHistory,OAuthAuthorizationFlowTest,OAuthCodeExchangePolicyTest,OAuthMetadataTest,PinnedClientAssertionTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 6d39e01e59f1f090f9ef62a616b89f4486f1dbfd --head HEAD --format markdown --plan-output work/final-selection.json
# Execute selected_maven_batches, splitting only AiSecurityLocalSqlServerTest into disjoint
# pre-existing and credential* method lists to keep each command below 240 seconds.
# Every selected class/method executes; no SQL opt-out, exclusion or skip counts as acceptance.
mvn test
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 work/check-docs.py
git diff --check 6d39e01e59f1f090f9ef62a616b89f4486f1dbfd HEAD
```

The selector's historical all-tests escalation is informational; `-Pall-tests` was not executed. No required local verification remains blocked. Approved deployed permissions/RLS, operational enablement, edge
attempt limits and independent live client acceptance remain deployment prerequisites; no acceptance is inferred.

```text
3B.1     COMPLETE
3B.2A    COMPLETE
3B.2B    COMPLETE
3B.2C.1  COMPLETE
3B.2C.2  COMPLETE
3B.2C    COMPLETE
3B.2D    NOT STARTED
3B.3+    NOT STARTED
```

**Exact next recommendation:** implement the minimal RFC 7009 follow-on with the same authenticated
client profile and durable family revocation, then 3B.2D's distinct ACCESS-only confined owner resolver,
fresh runtime full-tuple live validation and immutable VerifiedIntegrationContext construction. Verify every
revocation/deadline/scope/issuer/resource binding, non-bypass pooled isolation, replica/restart and ordinary
WEB/DESKTOP rejection. Keep MCP, resource data endpoints, Case disclosure and 3B.3 disclosure audit outside
that increment. No access token is currently accepted by any Shale resource.

Explicitly: no token-derived VerifiedIntegrationContext; no MCP route; no Case disclosure; WEB/DESKTOP
remains authoritative; AI defaults OFF; no production/Azure SQL, deployment, real tenant enablement,
installer/version publication or Curtis-specific integration.


### 10.20 Authenticated RFC 7009 credential revocation closeout — 2026-10-22

**Status: COMPLETE at source/local synthetic verification scope.** Base is live `codex/latest`, merged #1908 at
`d7b2e0cedd148f7fe8d433aef5cc5ca83d7029b2`. The accepted §10.19 final-source evidence already records
3B.2C.2 and aggregate 3B.2C COMPLETE; this closeout does not reopen that evidence investigation.
3B.2D and 3B.3+ remain NOT STARTED. The separate task worktree reconstructs the exact live commit and
tree `4bfc4a6b8c0d0adf413f1401dd835a344b042b29` through GitHub connector objects; cached files are
reused only when their Git blob hashes match. Publication uses that exact live tree plus explicit changed
paths, preserving unrelated content. Unavailable binary historical UI screenshots remain unchanged in
the live Git tree and are not needed for these checks. Original checkout/work is preserved.

#### Endpoint, authority and revocation rule

The maintained Spring Authorization Server revocation converter/provider/response boundary exposes
`POST /oauth/mcp/revoke` only with the existing explicit composition/exchange switches. Public clients
supply exact `client_id` and presented opaque `token`, with no secret. Confidential clients supply fresh
`private_key_jwt`, pinned active RSA/RS256, exact client issuer/subject and configured `/revoke` audience,
with the existing 60-second bound and shared durable JTI protection. No Basic/secret fallback, remote
JWKS, arbitrary algorithms or caller tenant/user/integration authority. Ordinary human-session filtering
exempts revocation as it already exempts `/token`; human WEB/DESKTOP authentication remains separate.

The [manual revocation SQL/operator record](../sql/ai-integration/README.md#rfc-7009-revocation-closeout-candidate)
owns the three new confined modules and cumulative catalog/source receipt. The bootstrap digest lookup
returns provisional tenant/user only for one exact client/configured issuer/resource, including inactive
credential history. Unknown/foreign input supplies that registration's creator solely for configuration/
revocation runtime setup; this is never human or business authority. A fresh normal runtime lease loads
one client under RLS and revalidates exact ownership/proof in the SQL transaction. Inactive credential
subjects do not alter authentication outcomes; expired grants/tokens remain revocable. No inventory,
auth-pool privilege expansion, table, resource authentication or new verified context is introduced.

**Deterministic rule: either ACCESS or REFRESH revokes the entire delegated session/family**, including
all active access credentials and all refresh generations. The existing session lifecycle trigger performs
the cascade. Consumed/expired/revoked history stays stored; first timestamp/reason stays immutable (SELF
for this client-requested mutation). Any active pinned key of that same confidential client may reduce
its family's authority; it does not inherit issuance/refresh authority from the family's original key.
Existing shared capability/tenant locks serialize revocation with refresh/family activity. Committed revoke
before refresh finalization prevents new credentials; concurrent winner ordering always leaves the family
revoked. Re-enable never resurrects descendants. No process-local revocation cache exists.

Authenticated unknown, malformed-presented, foreign and already-revoked credentials receive the same
empty HTTP 200 as a new revocation, with no-store and no ownership/existence fields. Token hints are
advisory, even unknown/mismatched hints. Invalid forms/authentication fail by their public protocol rules;
confidential no-op requests still consume JTI. Enabled RFC 8414 metadata truthfully adds revocation endpoint
and none/private_key_jwt/RS256 only; default-off metadata withholds it. No resource/MCP capability is added.

#### Audit compatibility and verification

The SQL transaction owns client/key revalidation, shared replay reservation, family mutation/cascade and
one existing `AI_DELEGATED_SESSION/DEACTIVATED` entity-action audit per newly revoked family. Audit uses
the server-resolved affected subject, numeric family row, correlation UUID, service source and ACTIVE=false;
it does not claim that a human initiated the client request. Failure rolls the entire transaction back and
withholds success. No-op/repeated requests intentionally add no entity row because no new mutation occurs;
confidential replay state remains durable. No token, digest, assertion, JTI, secret, key, prompt, PHI or
Case data enters audit. Existing audit schema is reused; Phase 3B.3 disclosure audit is untouched.

Pre-edit test impact covered credential/transaction adapter and SQL binding contracts; existing migration,
shared replay, cascade, audit, race and restart fixtures; HTTP/client/human-filter/metadata/assertion tests;
WEB/DESKTOP regressions and selector ownership. Enabled metadata's prior revocation omission is superseded.
New policy/binding/migration tests and synthetic restricted-identity SQL/HTTP scenarios cover both credential
kinds, unknown/repeated/foreign/malformed inputs, wrong tenant/user, expired and inactive subjects, audit
rollback including JTI, committed revoke ordering, concurrent family activity and replacement JVMs.
Test-only owner expiry injection is labelled; it is not elapsed-wall-clock or production evidence.

Final executed results (overlapping groups, not additive unique counts):

| Check | Final result |
| --- | --- |
| Focused revocation contracts/policy, SQL and maintained HTTP | 29 PASS: 5 binding/policy/migration, 12 SQL revocation scenarios, 12 maintained HTTP flows |
| Selector affected security-data/server | 218 PASS across 28 classes; no failures/errors/skips |
| Full synthetic SQL within affected suite | 102 PASS: 50 foundation/authorization/exchange, 40 prior credential lifecycle, 12 revocation scenarios |
| WEB/DESKTOP server/data authentication regressions | 51 PASS across 14 classes |
| Desktop enrollment/remembered restore/diagnostics/UI contract | 21 PASS across 5 classes |
| Critical reactor | 157 PASS across 42 classes; all seven modules BUILD SUCCESS; no failures/errors/skips |
| Selector Python | 27 PASS |
| Documentation/diff | 69 relative links/anchors, balanced fences, selector JSON, migration/verifier source receipt and git diff --check PASS |

All Maven runs used `/workspace/.tools/bin/mvn` (Maven 3.9.11/JDK21), with a 240-second outer
process deadline and `-DargLine=-Djdk.attach.allowAttachSelf=true`. Focused verification ran first,
then public/confidential HTTP, final race/replay/restart checks, existing authentication regressions,
selector affected suite, critical reactor, selector Python, documentation and diff checks. The selector's
single selected batch was partitioned without omission into non-SQL classes, existing foundation SQL,
credential SQL and revocation SQL so each command remained bounded. SQL opt-ins supplied only the
loopback fixture and generated private password; no secret was in argv or logs. No all-tests profile
was run: the selector's build/selector escalation is informational.

```bash
mvn -o -pl shale-server -am -Dtest=AiOAuthRevocationContractTest,OAuthRevocationPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-data -am -Dtest=AiOAuthRevocationContractTest,AiSecurityLocalSqlServerTest#revocation* -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-server -am -Dtest=OAuthAuthorizationFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-server -am -Dtest=AuthControllerTest,DesktopSessionControllerTest,SessionControllerTest,AdminSessionControllerTest,DurableSessionAuthTest,ServerAuthSessionLogoutTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,DesktopApplicationInstanceVerifierTest,SessionManagementContractTest,OAuthFilterIsolationTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-desktop -am -Dtest=DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,DesktopRememberedRecoveryTest,DesktopRememberedSignInDiagnosticsTest,LoginRememberedSignInContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base d7b2e0cedd148f7fe8d433aef5cc5ca83d7029b2 --head HEAD --format markdown
# Execute selected_maven_batches with AiSecurityLocalSqlServerTest split into disjoint
# pre-existing, credential* and revocation* method sets. Every selected class/method is covered.
mvn test
python -m unittest discover -s build/test-selection -p test_select_tests.py
git diff --check d7b2e0cedd148f7fe8d433aef5cc5ca83d7029b2 HEAD
```

The initial offline build lacked the live baseline's dependencies; permitted network execution populated
the cache and focused verification passed. Docker's sandbox socket denial was resolved through approved
local access. No proxy retry loop or stale implementation base was used. Two new synthetic fixture mistakes
were corrected before final checks: requesting unregistered confidential scopes, and expiring ACCESS with
an interval exceeding its five-minute constraint. Those attempts are not passes. No required task-scope
local check remains blocked. The dedicated loopback SQL container and generated credential file were removed.

Synthetic HTTP uses the existing test WEB identity store; ordinary production authentication adapters
are separately regressed. No production/Azure SQL, authoritative Case acceptance, operational enablement, independent
client acceptance, deployment or installer/version publication is inferred. AI defaults stay OFF.


```text
3B.1     COMPLETE
3B.2A    COMPLETE
3B.2B    COMPLETE
3B.2C.1  COMPLETE
3B.2C.2  COMPLETE
3B.2C    COMPLETE
OAuth revocation closeout COMPLETE
3B.2D    NOT STARTED
3B.3+    NOT STARTED
```

**Next task: 3B.2D — opaque access-token validation and immutable VerifiedIntegrationContext.**
No Shale resource accepts an AI access token yet; no MCP route or Case disclosure. WEB/DESKTOP remains
authoritative; AI defaults remain OFF. No production/Azure SQL, deployment or installer/version publication.

### 10.21 Phase 3B.2D internal opaque ACCESS validation — 2026-10-22

**Status: COMPLETE (source/local synthetic verification), expiry closeout 2026-10-23.** The corrected merged fixture, complete affected suite and critical reactor pass. The original implementation record below preserves the pre-closeout limits; the expiry evidence and final status are recorded at the end of this section. Implementation base: merged #1909, `967d72638fd6f85c1e7d680d642db659cdaf4740`; verified merged source: #1910, `c8272a8366246e380d49acdf186705ffc3bc0916`.
The separate `codex/phase-3b2d` worktree restores the exact live Git commit and tree through the GitHub
connector after the configured shell proxy refused Git fetch. Every restored blob and the complete tree
were checked by Git SHA; the signed commit reconstructs exactly. Original checkout/unrelated work is preserved.

#### Authority path and security boundary

The [manual ACCESS migration/operator record](../sql/ai-integration/README.md#phase-3b2d-internal-access-validation-candidate)
describes two new procedures, strict existing RLS/direct-table denial, dedicated bootstrap permissions,
cumulative catalog/source receipts, fixed locking and audit compatibility. No new table or duplicate
authority is created. Existing historical migrations and ordinary authentication are unchanged.

AiAccessTokenValidator hashes an exact 43-character opaque token immediately. Only the digest enters
ResolveAiAccessV1; only lifecycle-bound ACCESS can resolve. The bounded provisional tuple initializes a
fresh production RuntimeSessionService with both existing SQL keys. Setup statements are bounded; the
adapter independently verifies exact tenant/user before AiValidateAccessV1. Stamp/verification/SQL/close
failure withholds context. There is no fallback user, servlet carrier, ThreadLocal or positive cache.

Every admission/recheck validates global epoch and tenant policy, tenant/user eligibility, registration/
integration/key/grant/session/ACCESS state, original consumed authorization and WEB-session lineage,
exact client/issuer/resource and every tenant/user/integration/grant/session/key relationship. Held rows
follow global → tenant policy → integration → grant → session → user, under existing capability/tenant
locks conflicting with mutators. Database UTC after lock waits determines expiry, with no opaque-token skew.
The earliest credential/session/idle/grant/registration/WEB/key deadline clips the immutable snapshot.

Effective scopes intersect credential, session, grant, current registration ceiling, prior snapshot and
exactly `cases.search` / `cases.overview.read`. Supported administrative reductions permanently revoke
affected grants/families; expansion or re-enable never restores descendants. Recheck cannot widen a prior
snapshot. Committed revocation before the gate denies. The recheck seam prepares/proves a short SQL gate,
returns after commit/close, and performs no network handoff. 3B.3 still owns final buffered handoff semantics.

VerifiedIntegrationContext remains final/internal, with copied immutable scopes and bounded tenant/user/
integration/grant/session/client/key/credential identifiers, configured issuer/resource, four live authority
revisions, clipped deadline and server correlation. ACCESS admission leaves operation/invocation unset at
this layer. Existing trusted session-control objects retain their operation identity and cannot substitute
for credential provenance in recheck. toString is redacted. Raw token/digest/code/assertion/refresh, email,
secret, prompt, servlet and Case fields are absent.

AiBearerParser is an unregistered header-only internal utility: exactly one Authorization field, Bearer
scheme, one ASCII space, 43 base64url characters; malformed whitespace/control/duplicates/oversize fail.
No query/cookie/alternate-header source enters the API. All validation failures use the same bounded
Integration authentication failed message without nested SQL/ownership details. No WEB fallback.

Audit review deliberately adds no authentication outcome to entity-action mutation vocabulary: validation
is read-only, has no disclosure and may have no verified subject. Existing security mutations retain their
transaction-coupled bounded audits. Any future authentication-outcome vocabulary needs scoped review;
no token/digest/PHI/prompt is audited. IntegrationAccessAudit remains 3B.3, NOT STARTED.

#### Verification and evidence

Pre-edit impact inventory covered immutable constructors, resolver/store/JDBC bindings, SQL receipts/
RLS/gates and existing foundation/lifecycle/revocation fixtures; runtime setup and pool tests; maintained
OAuth filters/HTTP; ordinary WEB/DESKTOP adapters and selector ownership. Existing trusted-session seams
and historical resolver expectations are retained. New static, restricted SQL, pool/async, race and
replacement-JVM tests exercise the production validator through internal seams only.

Pre-closeout independent verification results are preserved below; the 2026-10-23 closeout supersedes the expiry-race blocker at source/local synthetic scope.
Maven commands use a 240-second outer budget, JDK21 and local synthetic loopback SQL on port 14338.
No failed/unexecuted/skipped check counts as passing. Evidence is source/static, compiled unit/servlet,
actual distinct restricted synthetic SQL identities, real shared transactions/locks and replacement JVMs.
Owner-only timestamp/state injection is labelled synthetic. It does not establish production/Azure,
authoritative Case RLS, independent client acceptance, fleet/deployment or penetration-test acceptance.

**Deferred 3B.3 recommendation (NOT STARTED):** The expiry closeout below completes only 3B.2D at source/local synthetic scope. A separately scoped future task would implement the narrow IntegrationAccessAudit schema/service and extend the
existing shared minimized query transaction owner for verified integration context, required audit/PHI
atomicity, quota and repeated live authority checks. Compose and prove the final short buffered handoff
boundary with cancellation/deadline/audit/commit/close/serialization failures and committed-revoke races
before any Case result reaches a transport. Preserve ordinary callers. Keep MCP transport/discovery in
3B.4+, and keep all flags OFF until separately authorized deployment/acceptance.

Explicitly: no MCP route; no Case disclosure; no Shale data resource accepts AI bearer tokens;
WEB/DESKTOP remains authoritative; AI defaults OFF; no production/Azure SQL; no deployment;
no tenant enablement; no installer/version publication. Aggregate Phase 3B remains IN PROGRESS.

#### Historical blocker and bounded retry record before expiry closeout

The following records the prior task budget and evidence, superseded by the 2026-10-23 closeout below.

The initial authority-state fixture omitted the required grant RevokedByUserId in direct synthetic
revocation; its one concrete corrective retry passed. The expiry-during-gate test unexpectedly returned a context after the fixture attempted to cross expiry
under a held lock. Its one retry changed UPDLOCK to XLOCK and failed the same expectation. Neither
attempt asserted the actual blocking precondition, so the cause is unresolved within the allowed budget. It is **not a passing
expiry-race proof**, and no third execution is authorized under this task's anti-hang rule.
The final fixture uses TABLOCKX and explicit transaction/blocking assertions to make a future failure
actionable, but that correction is **unexecuted**. Independently selected tests exclude this one blocked
method; therefore the complete affected suite and Phase 3B.2D acceptance remain unverified even if all
other checks pass. No production defect or successful fix is inferred from the two fixture attempts.

#### Pre-closeout executed independent verification

| Check | Executed result |
| --- | --- |
| Initial migration/parser/context/ACCESS smoke | 10 PASS (early focused source) |
| Final selected contracts and extended server/OAuth authentication regressions | 151 PASS across 38 classes, including maintained OAuth HTTP flows |
| Independent restricted synthetic SQL partitions | 137 PASS: 50 foundation/authorization/exchange, 40 credential lifecycle, 12 revocation and 35 ACCESS; one required expiry method excluded as blocked |
| Combined independent selected/auth checks | 288 PASS; **complete affected suite remains UNVERIFIED** because the expiry method is blocked |
| Desktop enrollment/remembered restore regressions | 21 PASS / 5 classes |
| One critical reactor | 161 PASS / 43 classes / all seven modules BUILD SUCCESS |
| Selector Python | 27 PASS |
| Documentation/static checks | 87 relative links/anchors, balanced fences, selector JSON and ACCESS source/verifier receipt checks PASS; final diff checks recorded below |

All executed final passing partitions have zero failures/errors/skips; overlapping groups are not unique
additive totals. The blocked method is excluded, not a passing skip. Two earlier expiry attempts failed;
the final stronger fixture is compiled but unexecuted. The full affected suite and 3B.2D acceptance are
therefore **not passing**, despite the independent critical success.

The environment reconnect terminated the last independent ACCESS partition before JUnit completion.
The stopped driver/JVMs were confirmed zombies; the task-owned SQL container remained healthy. That
interrupted attempt is not counted. One bounded retry of that independent partition passed. The blocked
expiry method was excluded throughout. Git fetch initially failed through the restricted shell proxy;
one justified network-enabled retry succeeded. Baseline offline Maven lacked Boot 4.1.1; one online
attempt populated dependencies and passed the focused contracts. No repeated full-reactor debugging.
SQL used only the generated-password, task-owned loopback container; no production/Azure SQL.

Final commands below were serialized with a 240-second outer process budget per Maven command.
The runner privately supplied SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI, SHALE_AI_SQL_PORT=14338 and
the generated SHALE_AI_SQL_PASSWORD; no secret was in argv/logs. SQL method partitions below cover every
selected method except the explicitly blocked accessExpiryWhileWaitingForFinalGateUsesDatabaseUtc.

```bash
# affected-nonsql-auth.log: 151 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-server -am -Dtest=com.shale.core.security.ai.AiScopeTest,com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.data.security.ai.AiAccessContractTest,com.shale.data.security.ai.AiOAuthCredentialContractTest,com.shale.data.security.ai.AiOAuthExchangeContractTest,com.shale.data.security.ai.AiOAuthRevocationContractTest,com.shale.data.security.ai.AiSecurityContractTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest,com.shale.data.runtime.RuntimeSessionServiceTimingTest,AuthControllerTest,DesktopSessionControllerTest,SessionControllerTest,AdminSessionControllerTest,DurableSessionAuthTest,ServerAuthSessionLogoutTest,RememberedDesktopSessionTest,SqlRememberCredentialStoreTest,DesktopApplicationInstanceVerifierTest,SessionManagementContractTest,OAuthFilterIsolationTest,OAuthAuthorizationFlowTest,OAuthClientMappingTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,OAuthConfigurationTest,OAuthMetadataTest,ShaleOAuthHumanTest,PinnedClientAssertionTest -Dsurefire.failIfNoSpecifiedTests=false test
# affected-foundation-0.log: 18 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#migrationDefaultsOffRepeatsAndRejectsCatalogDrift+realRuntimeHasNoBypassOrDirectControlTableAuthority+tenantPolicyUsesCurrentAdminContextAndRowVersion+registrationCeilingAndForeignTenantAreEnforced+selfConsentIsOneUseBoundToActorAndCeiling+consentRacingRevokeAndScopeReductionCannotResurrectAuthority+globalTenantIntegrationAndUserRevocationAreLiveAcrossServiceInstances+typedContextRejectsSpoofedTenantUserSessionAndMissingGrant+keyRotationIsBoundedConcurrentAndRevocationInvalidatesSessions+expiredGrantFailsClosedAndDigestResolverRestoresItsCaller+rlsFiltersAndBlocksForeignRowsWithoutPrivilegedRuntime+auditFailureRollsBackMutationAndConsentConsumption+everyNewTenantTableFiltersAndBlocksAnOtherwiseValidForeignInsert+concurrentReservationsHaveOneWinnerAndReleaseNeverRefundsBudget+durableDailyRowAndSlidingMinuteBudgetsSurviveNewServiceInstances+concurrentScopeChangesAndRotationsRejectStaleVersions+dailyBudgetsApplyAcrossUsersIntegrationsAndNewProcesses+distributedConcurrencyCountsAllThreeOwners' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-foundation-1.log: 18 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#slidingMinuteLimitsApplyToIntegrationAndTenant+actualReplacementJvmSeesCommittedAccounting+sameNonceAndSameInvocationRacesCannotDoubleSpend+bootstrapDefinitionAndCallerRestorationAreExactEvenOnFailure+migrationFailureRollsBackPartialDdlAndAmbiguousAuditAllowlist+expiredKeySessionAndConsentIntentCannotAcquireLiveContext+sessionFamilyRevocationIsSelfBoundPermanentAndAudited+oauthFreshClientLoaderAndSelfConsentBindEveryOwnerAndExactScopes+oauthCodeBindingRejectsChangedClientRedirectIssuerResourceAndVerifier+oauthCodeConsumeRaceHasExactlyOneWinnerAndReplacementJvmRejectsReplay+oauthCommittedRegistrationUserTenantAndGlobalDisablePreventCompletion+oauthGrantRevocationAndApprovalRaceNeverResurrectsRevokedAuthority+oauthScopeWideningDenialAndInjectedExpiryFailClosed+oauthAuditFailureRollsBackCodeGrantAndIntentConsumptionTogether+oauthStorageIsStrictTenantOwnedAndRuntimeCannotReadOrMutateItDirectly+oauthConfidentialPinnedKeyAndGrantRevocationPreventCodeUse+oauthNativeLoopbackPortIsConfinedAndExactIssuedRedirectCannotChange+exchangeMigrationRepeatsRejectsDriftAndRestrictsResolverAndReplayPermissions' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-foundation-2.log: 14 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#exchangeReplayTableUsesActualNonBypassTenantFilterAndInsertBlock+exchangePublicExactBindingsFailureDoesNotConsumeAndRaceHasOneWinner+exchangeAssertionReplayRaceAcrossCodesHasOneWinnerAndFailedPkceDoesNotBurnJti+exchangeCommittedBindingExpiryOrRevocationDenies+exchangeAuditFailureRollsBackBothReplayAndConsume+exchangeCommittedRevocationAndEveryAuthorityDisableDenyWithoutConsumption+exchangeCommittedKeyRevokePreventsOldKeyAndReplacementKeySubstitution' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-credential-0.log: 39 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#credentialMigrationRepeatAndCatalogDriftFailClosed+credentialInitialRaceOneFamilyAndHashOnlyFiveMinuteLifetime+credentialRotationResponseLossRetryRevokesAllDescendantsPermanently+credentialConcurrentRefreshHasOneWinnerAndLoserRevokesFamily+credentialReuseRaceWithDescendantActivityLeavesNoActiveCredentials+credentialWrongClientAndAccessTokenCannotRefreshOrResolve+credentialCommittedRevocationDeniesAndReenableCannotRestore+credentialDeadlineClippingAndExpiredAuthorityFailClosed+credentialInitialAndRotationAuditFailureRollBackAllState+credentialRestartObservesAccessRefreshAndConsumedHistory+credentialConfidentialRefreshRequiresSameKeyFreshSharedJtiAndLiveKey+credentialCommittedRevokeBeforeRotationPreventsNewRows+credentialSecurityResetWebRevocationAuditsFamilyAndCannotResurrect+credentialReuseAuditFailureRollsBackRevocationAndRetriesFailClosed+credentialRevokeHoldingAuthorityLockPreventsConcurrentRefreshCommit+credentialEightHourIdleAndConsentAbsoluteMaximumNeverSlide+credentialInitialLiveAuthorityCannotCreateAnyFamily+credentialSameValidatedTransactionCannotIssueASecondFamily' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-credential-1.log: 1 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#credentialGlobalDisableCannotRestoreInternalSessionOrQuotaAuthority' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-revocation-0.log: 12 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#revocationMigrationIsRepeatSafeAndDetectsDrift+revocationEitherCredentialRevokesAllDescendantsAndPreservesFirstHistory+revocationUnknownMalformedForeignAndWrongTenantHaveNoStateOrAuditEffects+revocationInactiveSubjectsRemainNonOracleAndPermanent+revocationAuditFailureRollsBackFamilyAndSharedReplayReservation+revocationUnknownConfidentialRequestConsumesSharedJtiAndCannotReplayAcrossRefresh+revocationRaceWithRefreshAndAccessDescendantActivityAlwaysEndsRevoked+revocationCommittedWhileRefreshAwaitsFinalizationPreventsNewCredentials+revocationRepeatedConcurrentRequestsAreIdempotentAndAuditOnce+revocationExpiredAccessAndRefreshHistoryStillRevokeFamily+revocationModulesRetainSeparateRestrictedSqlIdentities' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-access-states-0.log: 15 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#accessCommittedAuthorityChangesPreventNewAndRecheckedContexts' -Dsurefire.failIfNoSpecifiedTests=false test
# affected-access-other-0.log: 20 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -o -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#accessMigrationRepeatsAndCumulativeVerifierDetectsDrift+accessOnlyFullBoundContextAndSafeFailures+accessOwnerBindingMismatchFailsClosed+accessConfidentialKeyBoundAndRevocationIsLive+accessRecheckNarrowsAndNeverExpandsSnapshotScopes+accessCommittedRevokeBetweenBootstrapAndGatePreventsContext+accessSingleSpidPoolReuseAndConcurrentAsyncCancellation+accessScopeReductionBetweenBootstrapAndGateDeniesAndDoesNotResurrect+accessSetupMismatchAndStampFailureWithholdContextThenCleanNextBorrow+accessDistinctOwnersInterleaveThroughOnePhysicalSqlConnection+accessReplacementJvmObservesRevocationAndScopeReduction' -Dsurefire.failIfNoSpecifiedTests=false test
# desktop-regressions.log: 21 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true -pl shale-desktop -am -Dtest=DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,DesktopRememberedRecoveryTest,DesktopRememberedSignInDiagnosticsTest,LoginRememberedSignInContractTest -Dsurefire.failIfNoSpecifiedTests=false test
# critical.log: 161 PASS
mvn -B -DargLine=-Djdk.attach.allowAttachSelf=true test
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format json --plan-output work/final-selection.json
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 /workspace/work/check-docs.py # scratch relative-link/anchor/fence checker
# JSON parsing and normalized SHA-256 migration/verifier source receipt checks were also executed.
git diff --check origin/codex/latest HEAD
```

The selector's historical all-tests escalation remains informational; no all-tests profile was run.

#### Expiry-race verification closeout — 2026-10-23

The follow-up branch starts from fetched `origin/codex/latest` at merged #1910,
`c8272a8366246e380d49acdf186705ffc3bc0916`. Original checkout/unrelated work is preserved.
The merged fixture was executed unchanged first, using its exact focused command. No production,
fixture, procedure, lock-timeout, permission, installer or version change was necessary. This closeout
changes only documentation. [Verification evidence and exact accepted commands](../testing/phase3b2d-expiry-verification.json)
record the unchanged source SHA-256 receipts, all selected partitions and zero-skip results.

The owner injects `IdleExpiresAt=DATEADD(second,1,SYSUTCDATETIME())`, then retains an explicit
`TABLOCKX,HOLDLOCK` transaction on `dbo.AiDelegatedSession` before submitting `recheck`.
The transaction assertion and two-second database `WAITFOR` remain unchanged. The unfinished-future
assertion alone is not the independent blocking evidence: SQL Server Extended Events positively
observed the conflicting SQL wait, its completion, the resumed UTC sample and authentication rejection.

| Database UTC / observation | Evidence |
| --- | --- |
| `14:14:48.427Z` | Initial successful validation sampled UTC on SPID 74; the one-second idle-deadline injection follows it. |
| `14:14:48.482Z` (inferred wait start) | Validator SPID 78 begins a real `LCK_M_IS` wait; duration is **1,990 ms**. The injected deadline lies approximately between `14:14:49.427Z` and `14:14:49.482Z`, inferred from fixture order and these observed times. |
| `14:14:50.472Z` | Owner SPID 77 completes `COMMIT TRANSACTION`; SPID 78's lock wait ends. Release crosses the deadline while remaining inside the procedure's unchanged **3,000 ms** lock timeout. |
| `14:14:50.473Z` | The same SPID 78 completes `DECLARE @now datetime2(7)=SYSUTCDATETIME()` and raises **58901**; the awaited validator fails and returns no context. |

The trace ring buffer is untruncated with zero dropped events and no **1222** lock-timeout error.
The only authority mutation between successful validation and recheck is the labelled synthetic idle
expiry injection. The actual wait and same-SPID post-release sample/rejection establish the required
property; a scheduling delay, timeout or unrelated SQL exception is not counted as proof. SQL is the
task-owned loopback SQL Server 2022 Developer `16.0.4305.5` on port 14338, with generated credentials
and actual distinct restricted synthetic identities. No Case schema or data is installed.

| Closeout check | Result |
| --- | --- |
| Exact merged focused command | **5 PASS**, zero failures/errors/skips; one evidence-only repeat also passes after the first external DMV observer produced no usable records. Extended Events supplies the blocking proof above. |
| Complete selector-required affected suite | **213 PASS / 20 classes**, including **all 138 SQL invocations**: 79 ordinary methods and 59 cases across seven parameterized methods. Expiry executes within acceptance. Zero failures/errors/skips; no required test excluded. |
| One critical reactor, `mvn test` | **161 PASS / 43 classes**, all seven modules BUILD SUCCESS; zero failures/errors/skips. |
| Selector Python | **27 PASS**. |
| Documentation/static | Roadmap relative links/anchors and balanced fences, evidence/selector JSON, unchanged ACCESS source receipt, static UI resources and diff checks pass. |

Affected selection uses #1910's first parent versus its merged source, retaining the security-data
coverage even though the follow-up itself changes documentation only. The combined affected command
exceeded the scratch runner's 240-second outer budget while SQL fixtures were progressing, without a
reported test failure. After confirming the JVMs stopped and cleaning only that run's exact generated
database/logins, its one concrete retry partitioned the complete selection: 75 non-SQL tests, four
ordinary SQL groups (20/20/20/19), and three parameterized groups (24/20/15). JUnit reports were checked
against every selected class, ordinary method and parameterized case count. No repeated reactor debugging.

The prior environment-only Maven failures did not count as verification. This resumed task supplies the
full JDK 21 and system session-CA trust store, with private SQL opt-ins; no credential value enters the
commands or evidence. Overlapping focused/affected/critical results are not additive unique totals.

**3B.2D COMPLETE (source/local synthetic verification); 3B.3 NOT STARTED.** No production/Azure SQL
acceptance, no deployed permission/RLS acceptance and no real-client acceptance. No Phase 3B.3 work,
IntegrationAccessAudit, MCP, Case disclosure, data-resource AI bearer acceptance, production/Azure SQL,
deployment, tenant enablement or installer/version changes. Existing mutation audits remain unchanged;
read-only authentication introduces no audit schema or disclosure event. Aggregate Phase 3B remains IN PROGRESS.

```text
3B.1                      COMPLETE
3B.2A                     COMPLETE
3B.2B                     COMPLETE
3B.2C                     COMPLETE
OAuth revocation closeout COMPLETE
3B.2D                     COMPLETE (source/local synthetic verification)
3B.3                      NOT STARTED
3B.4+                     NOT STARTED
Aggregate Phase 3B        IN PROGRESS
```

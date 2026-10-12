# AI Integration, REST/OpenAPI, and MCP Architecture and Roadmap

**Status:** Phase 0 assessment COMPLETE; architecture/contract review IN PROGRESS; implementation NOT STARTED.

**Last reviewed:** 2026-10-08

**Repository baseline:** live `origin/codex/latest`, commit `852a9c9d72cf5c62a2963323185eb67b88cf7a20`.
This is repository inspection, not evidence of deployed SQL, API configuration, or runtime security acceptance.

## 1. Purpose, scope, and document ownership

This document is the authoritative roadmap for provider-neutral AI integration and the shared secured
application operations used by REST/OpenAPI and future MCP. It records recommendations for review; it does
not authorize deployment or claim that proposed contracts or controls exist. This task changes documentation
only: no MCP implementation, endpoints, database migrations, production code, or runtime configuration.

A new document is appropriate because the web migration notes inventory shared services, and the
release/session roadmap owns authentication lifecycle, but neither owns the combined external REST/MCP
contract, delegated integration authority, or AI access audit model. Reuse those authorities rather than
copying their roadmaps. Subsequent tasks must update this tracker and the affected owning document.

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
* [Release/session roadmap](application-release-session-management.md), especially the later Phase 7B/7C,
  Phase 8A/8B implementation records and remembered-sign-in closeout. Earlier inventory sections describe
  historical states and must not be interpreted as today's exclusively in-memory revocation design.
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
| REST | `ApiReadController` plus auth, session, release, instance, admin, and contact-value-validation controllers | Existing case/contact/organization/task reads and several writes. Despite its name, `ApiReadController` is not read-only. Reuse routes and operations; do not expose the controller wholesale to MCP. |
| OpenAPI | `OpenApiConfiguration`, Springdoc dependency in `shale-server/pom.xml`, `OpenApiDocumentationTest` | Generated `/v3/api-docs`, `/swagger-ui/index.html`, and `bearerAuth` already exist. The advertised `v1` is not yet a complete external compatibility policy. |
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
  calls cannot rely on that UI seam. Selected server reads need an authoritative audit-before-release
  boundary with a deliberate failure policy.
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

`shale-web/src/api.ts` is an existing React/TypeScript consumer of bearer APIs, with per-tab `sessionStorage`
and manually maintained types. The server has safe errors via `ApiExceptionHandler`/`ApiErrorResponse`,
validation via `ApiValidation`, and configured origin CORS. Case and Contact search-page endpoints currently
fetch a prefix and slice it in memory with `total=null`; Contact search also applies its adapter limit after
DAO retrieval. A bounded response alone is not bounded database work. Some responses expose UI-oriented
fields or concurrency bytes, and `getCase` returns Java `Object`. These are contract-hardening issues, not
reasons to replace Spring Boot or the existing domain architecture.

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

Recommend HTTPS remote MCP with standards-based delegated OAuth authorization-code flow and PKCE for public
clients, following the selected MCP specification's authorization/discovery requirements. Shale or a reviewed
authorization server authenticates the user and issues audience-bound, short-lived, integration/grant-bound
credentials; clients do not receive Shale passwords or desktop remembered credentials. Validate issuer,
audience/resource, signature, expiry, and live grant/session state. Do not pass tokens to a storage provider or
accept a token issued for another resource. Confidential-client keys authenticate the integration only and
never substitute for user delegation. Local AI can use the same remote connection; localhost/stdio, if later
needed, must not create a trusted local SQL or identity-assertion exception.

Reuse SQL-backed session ownership/rotation/revocation semantics where compatible. The current WEB/DESKTOP
session vocabulary, principal, JWT issuer, and signing configuration do not yet model integration identity,
audience, scopes, consent, or authorization-server discovery. Do not mark existing JWT login as MCP OAuth-ready
or label an AI session DESKTOP to fit a constraint. Choose narrowly reviewed grant/session extensions and token
exchange only after the issuer/transport decision; preserve current browser/desktop contracts.

* Tenant enable/disable is fail-safe off; an administrator approves registration and allowed read scopes.
  A user separately delegates within that ceiling. Disable/revoke blocks the next request and queued work;
  recheck before delivering long-running sensitive results. Existing PubSub may accelerate notification
  only after commit; durable state remains authoritative without delivery.
* Validate active user, same-tenant membership, current case/document/field entitlement, and live integration
  grant for each operation. Permission/account changes must take effect without restarting an AI client.
* Credentials expire, rotate safely, and support user, integration, grant, and tenant revocation. Define
  absolute delegation expiry and refresh/reuse behavior; store hashes of opaque secrets, never raw keys or
  tokens. Protect confidential credentials, restrict redirect URIs, and never log authorization headers.
* Rate-limit authentication attempts and data access by tenant, integration, and acting user; cap query
  length, page size, database work/time, concurrency, response bytes, and cumulative extraction. Use safe
  throttling/retry responses. Numeric limits need workload evidence; conservative configured limits are
  required before pilot traffic. Do not allow unbounded export through repeated small calls.
* Tool discovery lists only the approved surface; invocation independently authorizes. Read-only enforcement
  is an application operation allowlist, not just HTTP method or client-side tool annotations. No generic
  REST proxy, arbitrary URL fetcher, dynamic DAO dispatch, executable query, or hidden mutation fallback.
* Treat case narratives and eventual document text as untrusted data, never new Shale authorization or tool
  instructions. Prompt injection cannot enable writes, change tenant/user context, or enlarge grants.
  Tenant administrators must understand that authorized output leaves Shale for their chosen AI system;
  hosting/provider retention policy is an onboarding decision, not a model-specific backend exception.

### 4.4 Decisions still requiring evidence

| Deferred decision | Evidence/owner needed before implementation or activation |
| --- | --- |
| Case/matter and sensitive-field read policy | For each operation being exposed, product/security owners must confirm whether ordinary active cases are tenant-wide visible or restricted, any ethical walls, deleted-case handling, directory relationships, and role/field exceptions. Existing Team roles do not answer this. Required for that operation's policy acceptance; unrelated AI OAuth decisions do not block ordinary REST work. |
| OAuth issuer and remote MCP transport/library | Inspect current deployment capabilities, selected MCP revision and representative independent clients; decide authorization-server responsibilities, audience/scopes/discovery, consent and client registration, and supported transport. Required before Phase 3. |
| Grant/session persistence and admin controls | Map integration/delegation lifecycle to existing durable sessions and verified schema constraints; decide minimal compatible extension and rollback/revocation behavior. No table/enum/migration is prescribed in this assessment. |
| Audit representation | Review SQL and Java allowlists, PHI-read persistence, denied-request attribution and approved retention/review. Decide the scoped compatible AI attribution extension before AI access; ordinary required read audits are reviewed per web operation. |
| External document retrieval | Establish real storage ownership, case/document IDs, provider entitlement, supported formats, download audit, and bounded content/extraction path. No Phase 1 document tool until resolved. |
| Compatibility and budgets | Agree REST/MCP version/deprecation policy, payload/time semantics, performance measurements and numeric quotas with web/mobile/integration consumers before publishing stable contracts. |

## 5. Audit compatibility review

The integration/tool/grant access-event requirements below gate AI data access. Ordinary web requests
must satisfy their operation's required Shale audit semantics, but do not need a registered AI identity,
MCP tool attribution, or the complete AI read/failure audit extension. Implement any required non-UI
PHI-read seam for a web use case independently; do not fabricate integration fields or silently omit a
required audit. Reuse that seam for AI once its additional attribution and persistence gates are ready.

Use existing Shale audit ownership, tenant RLS, append-only retention, administrator review, sanitization,
and transaction patterns. Do not create a parallel AI audit product or use Case/Task Timeline as compliance
history. Required conceptual access-event fields are:

| Field | Recommended treatment |
| --- | --- |
| Tenant, acting user | Server-derived tenant/actor; validate against SQL context. Pre-authentication failure may have no verified tenant/actor; never fabricate one from request input. |
| Integration and delegation | Stable registered integration ID and safe grant reference, not token, key, installation secret, or provider conversation contents. |
| Tool/application operation | Closed vocabulary, contract version, and source such as MCP or REST integration; distinguish protocol tool from shared business operation. |
| Entity/parent context | Case, Contact, Organization, Task, or eventual Document IDs where relevant; preserve parent Case identity. Search events use bounded result count and scope, not raw queries or serialized result-ID lists. |
| Time/correlation | Server UTC timestamp and server-generated/validated bounded request correlation ID propagated into authoritative audit; no trusted free-form caller metadata. |
| Outcome and class | Success, denied, validation failure, unavailable, or internal failure as closed safe codes; explicitly READ or MUTATION. No exception strings. |

Compatibility matrix:

* Overview/detail, task detail/list fields, case updates/timeline narratives, contact sensitive fields,
  material metadata, and eventual document opens/downloads are sensitive access. Reuse PHI-read semantics
  through a non-UI application seam, with an approved access-event extension for integration attribution.
* AI search summaries are automated disclosure. Require one bounded operation-level access event, including
  empty successful results, without keystroke telemetry or query text. This is an external-access policy;
  it does not impose AI operation-level search auditing on ordinary web searches or retroactively alter
  the intentionally unaudited desktop suggestions. Web search audit treatment requires its own scoped review.
* Tenant integration enable/disable, registration, scope changes, grant revocation, and later domain writes
  are meaningful administrative/security/domain actions. Map administrative/domain changes to reviewed
  entity-action vocabulary and session/grant revocation to compatible session-security semantics.
* Token checks, nonsensitive permission predicates, health, and tool schema discovery intentionally produce
  no PHI/entity-action read row. Aggregate sanitized operational counters are sufficient unless security
  review identifies abuse; discovery invocation does not grant access.

The exact gap is structured integration/tool/outcome context for reads and failures: PHI UI read rows do not
carry it, entity-action has no READ/FAILED action or integration/tool keys, administrative-read types are limited,
and session-security events cannot represent general business reads. Existing source/correlation fields help
with later mutation attribution but do not solve the entire read model. Defer a scoped audit enhancement,
including any necessary allowlist/schema/viewer change, to implementation; do not squeeze JSON into arbitrary
PHI value fields or claim today's schema already supports it.

For AI sensitive reads, perform authorization, bounded read, and required audit persistence at the authoritative
operation seam and commit audit before releasing data. Follow the existing administrative-read fail-closed
pattern, not UI best-effort failure handling. Multi-query compositions need a deliberate connection/transaction
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

These five tools are a small proposed **Phase 1 read-only contract**, delivered in roadmap Phase 3 after
shared-service and security gates. They are not implemented or approved wire schemas. All inputs exclude tenant,
user, integration identity, SQL, arbitrary field expressions, and permission claims. Proposed search pages
start with a maximum of 25 items (policy may lower it); response byte/time budgets are also required. Cursors
are opaque, bound to authorized scope/filter/version, and never allow tenant selection. Pagination support
below the port must be proven before promising a cursor. Search output is minimized; narrative, DOB/condition,
private notes, external URLs, audit internals, and row versions are excluded by default.

| Tool | Purpose; conceptual inputs | Conceptual outputs | Authorization considerations | Underlying Shale operation and existing REST equivalent |
| --- | --- | --- | --- | --- |
| `search_cases` | Find relevant active accessible matters; literal query, bounded page limit/cursor | Case ID/number/name, current semantic status and practice area, continuation/truncation indicator; no narrative by default | Case-search grant AND current user read policy, before paging; exclude deleted/restricted matters and hidden counts | Reuse `CaseServicePort.searchCases` -> `CaseServiceAdapter` -> `CaseSummaryDao.searchActiveForServer`; strengthen policy/true paging as needed. REST exists: `GET /api/cases/search` and `/search-page`. |
| `get_case_overview` | Understand one accessible matter; Case ID | Minimized typed overview: case identity, status, practice area, authorized team and authoritative selected dates; allowed summary fields only after field-policy review | Case-read grant, active user, same tenant, case/field visibility; one auditable access, no deleted-case admin capability | Compose/narrow existing `getAuthoritativeCaseDetail`, summary and authoritative date operations behind a shared query boundary; avoid automatic full detail/narrative loading. REST `GET /api/cases/{caseId}` exists but returns fuller detail, not this exact minimized contract. |
| `get_case_tasks` | Identify case work and deadlines; Case ID, bounded page/filter for completion | Task IDs, title, due date/time, status/completion and permitted assignee summary; continuation indicator | Case and task read permission plus grant; authorize parent independently, exclude private narrative, bound child query | Reuse `TaskServicePort.listCaseTasks` -> `TaskServiceAdapter` -> `TaskDao.listActiveTasksForCase`; add authoritative actor policy/bounds where needed. REST exists: `GET /api/cases/{caseId}/tasks`, currently list-shaped. |
| `search_contacts` | Find people in the permitted directory; literal query, limit/cursor | Contact ID, display name, permitted classification/primary contact summary; no DOB, condition, or notes | Directory-read grant AND user directory/field policy; no restricted case relationship expansion | Reuse `ContactServicePort.searchContacts` or actor-aware `getContactDirectoryPage` where its filtering/paging fits; shared projection and policy must avoid unbounded legacy retrieval. REST exists: `GET /api/contacts/search` and `/search-page`. |
| `search_organizations` | Find permitted organizations; literal query, limit/cursor | Organization ID, name and permitted type/primary contact summary | Organization-directory grant AND user/field policy; no private notes or hidden related cases | Reuse `OrganizationServicePort.searchOrganizations`; `OrganizationDao.findDirectoryPage(OrganizationSearchCriteria)` is a structured paged query to assess behind an actor-aware shared port extension, not an existing directory port. REST exists: `GET /api/organizations/search`; current scalar summary is a compatibility projection. |

A projection change is deliberate contract work; do not promise new filters/types that existing operations
cannot supply. Equivalent REST functionality does not imply equivalent authorization/audit completeness.
MCP returns structured data with clear unavailable/denied/error behavior, not model-generated answers presented
as Shale facts. Any text representation uses the same minimized authorized projection.

Deferred tools:

* `get_case_timeline`: `CaseDao.listCaseTimelineEvents` and `listCaseUpdates` already supply domain history,
  but no unified actor-aware bounded timeline port/REST contract was found. `/api/cases/{caseId}/updates`
  is notes/updates, not a complete timeline. Resolve event grain, source ordering/pagination, PHI policy,
  and audit before exposing it; do not reconstruct chronology from security audit tables.
* `search_case_documents` / `get_case_document`: blocked on the document boundary identified above.
  Link/Material Item metadata and generated summaries are reusable evidence, not content access. A later
  operation must resolve a stable Shale document ID, authorize tenant/user/case/document/provider, audit
  release, bound bytes/pages, and prevent arbitrary URL fetch/SSRF and credential leakage. No new storage
  subsystem is required until actual storage evidence warrants one.
* All create/update/delete/complete/assignment/admin tools: excluded from discovery and invocation until
  controlled-write gates. Existing REST writes remain conventional client contracts and grant no AI writes.

## 7. REST/OpenAPI implications

The present Spring Boot/core/data split is suitable for redesigned web and future mobile clients. Continue it
and strengthen selected contracts instead of starting a second web/mobile backend. Reuse case search/detail,
case tasks/updates, Contact/Organization search/detail, lookup, notification cursor, auth, and durable-session
routes. Reuse authoritative Contact/Organization aggregates and Case Date projections rather than raw tables
or legacy scalar mutation logic. Keep release/policy global control-plane operations separate from tenant data.

Web/API work can implement and verify these shared decisions per use case using existing bearer sessions.
MCP/AI OAuth, grant storage, tool discovery and AI-specific audit metadata are separate follow-on work.
The existing required audit/authorization checks for each REST operation remain release criteria.

Phase 2 should standardize:

* Concrete response schemas and operation IDs, replacing ambiguous `Object` descriptions when necessary;
  safe request DTOs/projections rather than persistence rows. Document nullable fields, enum/custom lookup
  identity, UTC instants versus existing local Case Date/task times, ID encoding for mobile/JavaScript,
  relationship visibility, soft deletion, and empty/unavailable results.
* Bounded database paging/sorting and response budgets, including case/Contact prefix slicing and Organization
  directory/search behavior. Keep existing list/page contracts compatible until an explicit deprecation;
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
documents with each subsequent task; split implementation into small reviewable changes. No runtime tests are
required for this documentation task; the tests below are future phase acceptance requirements.

### Phase 0 — Architecture and contract (**IN PROGRESS**)

**Goal:** A reviewed, evidence-backed shared architecture and minimal contracts.
**Dependencies:** Repository assessment; product/security and client owners for deferred choices.

* [x] Assess live repository, service boundaries, REST/OpenAPI, sessions, RLS, audit, and documents.
* [x] Record recommended service/security/audit model, REST improvements, and five-tool proposal.
* [x] Record existing authority and deferred decisions without implementation or schema changes.
* [ ] Confirm policy and required audit treatment per shared/web operation being delivered.
* [ ] Finalize REST compatibility decisions per web/mobile contract independently of AI review.
* [ ] Separately approve AI authentication/transport, grants and additional audit representation.
* [ ] Finalize MCP input/output/error/pagination contracts before AI activation.

**Verification:** Documentation consistency/relative-link review and `git diff --check`; reviewers trace
selected operations to code and resolve the decision table. **Completion:** Contract/security owners record
accepted choices and remaining exclusions; no unresolved policy is treated as permission to expose data.

### Phase 1 — Shared application/service boundary (**NOT STARTED**)

**Goal:** Selected operations are authoritative and reusable from either protocol.
**Dependencies:** Policy/contract and required audit acceptance for the operation being changed, plus
its owning domain roadmap. AI issuer/transport/grant decisions and full Phase 0 completion are not
prerequisites for ordinary web/service work.

* [ ] Inventory each selected path's actor, tenant, field policy, audit, connection and bounds.
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
invokes a proven shared boundary without duplicating business/security logic; reads are bounded and required audits pass. Record AI readiness separately;
REST acceptance does not require an implemented MCP adapter or AI audit extension.

### Phase 2 — Stable REST/OpenAPI foundation (**NOT STARTED**)

**Goal:** Web/mobile/integrations consume documented stable application contracts.
**Dependencies:** Verified shared operations and REST compatibility decisions for the selected web/mobile
use cases. No dependency on MCP, AI OAuth, registration/grants or completion of AI contract review.

* [ ] Formalize existing selected endpoints/schemas/errors/paging before adding any missing use case.
* [ ] Align authoritative auth/authorization and audit below REST; retain current compatible routes.
* [ ] Review generated OpenAPI and typed web/mobile contract consumption and version/deprecation policy.
* [ ] Document the separation of browser user authority and future integration audience/scopes;
  implementing AI OAuth or grants is not part of ordinary web acceptance.

**Tests:** Existing `OpenApiDocumentationTest` plus selected controller/service integration contracts,
authentication and authorization failures, safe errors, pagination/query bounds, sensitive field omission,
concurrency compatibility, and existing web-client regression checks as affected. **Completion:** Reviewed
OpenAPI describes actual behavior and selected web/mobile contracts remain compatible and secure.

### Phase 3 — Read-only MCP foundation and minimum tenant controls (**NOT STARTED**)

**Goal:** A disabled-by-default, securely delegated read-only pilot interface.
**Dependencies:** Phases 0–2; approved OAuth/transport/grant model and audit enhancement. New schema work, if
needed, belongs in separately scoped implementation with live catalog/RLS verification, never this task.

* [ ] Select protocol revision/library and transport; implement discovery and five approved tool schemas.
* [ ] Bind authentication to integration, tenant, acting user and live grant/session; validate audience.
* [ ] Add minimum admin-controlled tenant opt-in, registration, read scopes, expiry/rotation and revocation.
* [ ] Invoke shared operations only; enforce read allowlist, quotas, time/bytes/concurrency budgets.
* [ ] Persist approved access outcomes/correlation before disclosure and reject missing policy/audit state.

**Tests:** Authorized and unauthorized discovery/invocation; wrong issuer/audience, missing/expired/replayed
credentials, tenant spoofing, scope escalation, account/grant/tenant revocation across replicas/restart, async
context isolation, cross-tenant resource and cursor probes, throttling and extraction bounds, audit failure,
and attempted hidden writes/SQL/arbitrary URL calls. Test with representative independent MCP clients.
**Completion:** First authorized read and audit are demonstrated with no bypass or domain mutation; minimum
controls are usable and everything defaults off. Document content/timeline tools remain excluded.

### Phase 4 — First-party dogfooding (**NOT STARTED**)

**Goal:** Validate usefulness and controls with Curtis & Co.'s local AI as a normal client.
**Dependencies:** Phase 3 acceptance and explicit tenant/user grants; start with representative test data.

* [ ] Register the local client through ordinary tenant controls; no SQL, desktop identity or privileged keys.
* [ ] Exercise search -> overview -> tasks and directory workflows; measure useful fields, latency and cost.
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
| Recommended target/security/audit/REST/tool direction | COMPLETE | Sections 3–7 are documented recommendations, not accepted wire contracts. |
| Phase 0 review and final contract | IN PROGRESS | Per-operation REST review and separate AI policy/issuer/audit decisions remain; this aggregate status does not gate all web work. |
| Phase 1 shared secured operations | NOT STARTED | Relevant operation policy/required audit decisions; no dependency on AI issuer/grants. |
| Phase 2 stable REST/OpenAPI foundation | NOT STARTED | Selected shared operations and REST compatibility; can ship while MCP/AI OAuth remain pending. |
| Phase 3 MCP and minimum tenant controls | NOT STARTED | Transport/grants, audit and service gates. |
| Phase 4 ordinary-client dogfooding | NOT STARTED | Phase 3 tested controls, tenant/user grants. |
| Phase 5 administration experience | NOT STARTED | Minimum controls first, then pilot feedback. |
| Phase 6 optional controlled writes | NOT STARTED | Read-only maturity and per-command approval. |
| Phase 7 customer readiness | NOT STARTED | Read-only acceptance; write maturity only if offered. |
| Live RLS coverage acceptance | BLOCKED | Non-dbo deployment evidence, including documented Contacts parent-predicate gap. |
| Case/field policy acceptance | BLOCKED | Product/security decision; Team membership is not an access policy. |
| Complete AI read/failure audit representation | BLOCKED | Scoped compatible enhancement decision and verification. |
| Timeline/document tool expansion | BLOCKED | Bounded timeline service and authorized content/provider boundary. |

**Recommended next tasks:** For the web rebuild, select its next REST use case and verify its existing
service mapping, bearer session, tenant/user/entity/field policy, required audit and stable contract.
Implement only the necessary shared boundary/REST improvements and verify that use case independently.
For AI, continue the five-query policy/audit/connection inventory and obtain issuer, grant, tool and additional
audit decisions before activation. Keep AI disabled/read-only according to its own gates; ordinary authorized
web delivery continues without completing MCP or AI OAuth.

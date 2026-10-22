# Tenant Architecture

## Current authentication/security reconciliation — 2026-10-21

The [canonical API/MCP roadmap §2.5](../docs/architecture/ai-integration-and-mcp.md#25-authentication-permissions-and-delivery-reconciliation--2026-10-21)
records the October 20 **operator-reported production retirement** of EXECUTE on the three legacy login
procedures for `shale_app`, runtime principal/role denials, and successful API login/identity/refresh/logout
plus installed-desktop automatic sign-in/user-detail checks. Do not repeat completed operator rehearsals
or treat the older retirement-BLOCKED statements below as current status for that exact delta.

The separate V1/V2 auth-module Stage A installation/fences, Stage B table/hash read restrictions,
subsequent desktop/API/SQL deployment details and monitoring closeout are not established by that report.
Current source is merged through release 1.0.137; merge/release metadata does not establish deployed parity.
Users/auditLog/Statuses read coverage, runtime hash/audit capabilities, write/admin restrictions and
Case SQL acceptance remain open as qualified in that ledger. Supplied offline schema confirmed the gap;
older missing-input statements are historical. No complete least-privilege, MCP or native-mobile acceptance.

Follow the [SQL/API/client release order](../docs/azure-app-service-deployment.md#release-dependencies-and-order--2026-10-21).
The one next implementation phase is the roadmap's [Phase 1R prompt](../docs/architecture/ai-integration-and-mcp.md#91-ready-to-run-codex-prompt--phase-1r),
a local three-FILTER candidate, without production changes. Earlier dated tests, local Windows/source
recovery limits and all accepted R4/session observations retain their scopes. This update executes no SQL.


All business data is tenant scoped.

Primary tenant key:
- ShaleClientId

Database access:
- Runtime connections are intended to use a non-bypass tenant identity, conventionally shale_runtime.
  Actual configured principals, effective grants/roles and module execution authority require catalog evidence.
- SESSION_CONTEXT('ShaleClientId') must be set before querying tenant protected tables.

Pattern:

EXEC sys.sp_set_session_context
    @key=N'ShaleClientId',
    @value=@TenantId,
    @read_only=1

Intended RLS coverage (not a verified deployed inventory):
- Cases
- Contacts
- Organizations
- Users
- Tasks
- Roles
- Statuses
- Categories
- Priorities
- UserReleaseState

### Confirmed Case-read coverage gap — 2026-10-14

The authoritative user-supplied schema-only export and user-run Shale catalog result confirm **no
security predicates** on `dbo.Users`, `dbo.auditLog` or `dbo.Statuses`. The enabled `sec.TenantFilter`
and its functions exist, but unattached functions do not protect these tables. Users/audit history
must be strict tenant reads; Statuses must allow global NULL ownership plus current-tenant rows.
Absent RLS is a coverage gap, not proof of an API leak: minimized Case reads have explicit root/join
predicates and same-connection actor/context checks. No local SQL integration run has occurred.

The owning [Case-read runbook assessment](../docs/testing/case-read-local-security-verification.md#rls-blocker-assessment--2026-10-14)
records actual exported shapes, application/auth/desktop/audit paths, unknown effective grants,
bounded read-only catalog queries and prerequisite/verification/rollout/rollback decisions.
Reuse the existing sec FILTER functions; keep the harness and original export unchanged. First
establish bounded pre-tenant password/remembered identity resolution and compatible installed clients.
Attaching strict Users RLS directly can break current login and remembered bootstrap. Ownership
chaining alone does not bypass RLS; do not solve this by adding shared identities to ShaleAdmins or
changing the shared function. Tenant-admin UI authority is distinct from SQL ShaleAdmins bypass.
FILTER coverage does not establish write blocking, column protection or admin-only audit reads.

RuntimeSessionService actually stamps both ShaleClientId and PrincipalUserId on every borrow without
read_only=1; pooled context switching needs independent non-dbo verification. The example above is
not the runtime initializer. Pre-tenant authentication and approved global control-plane operations
are distinct bounded paths; tenant domain access must remain initialized. Security acceptance OPEN.

### User release announcement state

`dbo.UserReleaseState` is strict tenant-owned data. It has a non-null `ShaleClientId`, a tenant-qualified user
foreign key, and participates in the existing enabled `TenantFilter` policy through
`sec.fn_FilterByTenant(ShaleClientId)`. A filter predicate protects reads and update/delete targeting; matching
`AFTER INSERT` and `AFTER UPDATE` block predicates reject a row whose owner differs from session tenant
context. It never uses `sec.fn_FilterByTenantOrGlobal`. The service additionally requires tenant and principal
session context to equal the requested current user and validates active same-tenant membership.

## Authoritative Intake reconciliation

New Intake resolves the tenant-effective date type through the protected `INTAKE` semantic role and
persists its displayed or edited date/time as a timed `CaseDates` occurrence. Case creation,
occurrence persistence, and required audits share one tenant-scoped transaction. Runtime Case lists
read and sort this occurrence; legacy `Cases.CallerDate`/`CallerTime` are reconciliation inputs only.

The 2026-08-14 repair required a null tenant session context plus approved all-tenant administrative
visibility, still applied explicit tenant equality throughout semantic resolution, and inserted 21
missing occurrences for tenant 7. It is forward-only and idempotent. It emitted the established
tenant-owned PHI and entity-action audits and did not insert `CalendarEvents`.

Rules:

- Never bypass tenant filtering.
- Never remove RLS predicates.
- Never query tenant data without an initialized RuntimeSessionService.
- All server requests must resolve a tenant before opening DB connections.

## Case Links / External Links tenancy classification

Phase 1 case-link tables use the existing `TenantFilter` security policy and `sec.fn_FilterByTenant` predicate architecture. Migrations must fail fast if those live RLS objects are not present rather than creating a competing policy.

| Table | Classification | Expected RLS behavior |
| --- | --- | --- |
| `dbo.LinkTypes` | Global overlay lookup | Current tenant sees global rows (`ShaleClientId IS NULL`) plus its own rows. Tenant rows override global rows with the same `SystemKey` in effective lists. Other tenants' custom rows are hidden. |
| `dbo.ExternalLinks` | Strict tenant-owned | Current tenant sees only rows where `ShaleClientId` matches `SESSION_CONTEXT(N'ShaleClientId')`. |
| `dbo.CaseLinks` | Strict tenant-owned | Current tenant sees only rows where `ShaleClientId` matches `SESSION_CONTEXT(N'ShaleClientId')`. |
| `dbo.CaseLinkShares` | Strict tenant-owned | Current tenant sees only share rows where `ShaleClientId` matches `SESSION_CONTEXT(N'ShaleClientId')`; uses `sec.fn_FilterByTenant`, never overlay/global filtering. |

`dbo.LinkTypes` uses normalized lowercase `SystemKey` values with a filtered unique index per scope. Services should build effective Link Type lists by loading global and current-tenant rows, partitioning by non-null `SystemKey`, and preferring the tenant row when both scopes define the same key.

Remaining service-layer validation requirements:

* `dbo.ExternalLinks.LinkTypeId` must reference either a global Link Type or a Link Type with the same tenant id as the ExternalLink. This cannot be represented as a verified declarative composite foreign key while Link Types support global rows.
* `dbo.CaseLinks.ShaleClientId` must match both the linked `dbo.Cases.ShaleClientId` and `dbo.ExternalLinks.ShaleClientId`. Phase 1 keeps single-column foreign keys because the referenced tables do not expose a verified tenant composite key for direct enforcement.
* Soft-deleted CaseLinks do not block later replacements. Active uniqueness is enforced by filtered indexes for duplicate case/link pairs and one primary active link per case.

* `dbo.CaseLinkShares` records Shale's knowledge that a case-specific Case Link was shared with a Contact; it performs no Box, Clio, URL, or other external-service permission verification.
* `dbo.CaseLinkShares.ShaleClientId` must match the referenced Case Link, Contact, and actor Users tenant. Phase 5.2 keeps verified single-column foreign keys and requires Phase 5.3 services to validate CaseLink/Contact/actor tenant compatibility before mutations.
* `dbo.CaseLinkShares` is never global or overlay data and must not use `sec.fn_FilterByTenantOrGlobal`.

## Case Links Phase 3 Link Type administration

Settings Link Type management is an admin-only tenant lookup administration surface layered on the existing `CaseServicePort -> CaseServiceAdapter -> CaseDao` path. Administration reads use an actor-aware operation that validates the current tenant, the current user, active/not-deleted user state, same-tenant membership, and `dbo.Users.is_admin = 1` before returning raw global plus current-tenant Link Type rows.

The Settings cards resolve those raw rows into one card per effective Link Type: global rows are shown as `Global/default`; same-`SystemKey` tenant rows are `Tenant override` cards and mask global rows even when inactive; deleted tenant override rows act as reset markers so the global default appears again; tenant rows without a matching global `SystemKey` are `Tenant custom` cards. Other tenant rows are excluded.

Global Link Type rows are never mutated by Settings. Editing or activating/deactivating a global default creates or updates a tenant override. Reset soft-deletes/deactivates the tenant override so the global default is effective again. Remove soft-deletes tenant custom rows. Existing Case Links retain their stored Link Type relationship when an admin resets or removes future effective selections.

## Contact shared Case Link reverse lookup

Contact View shared-link reads use the existing Case Link service boundary (`CaseServicePort -> CaseServiceAdapter -> CaseDao`) and return UI-free shared Case Link DTOs. The reverse lookup is authoritative from active `dbo.CaseLinkShares`: active share visibility is determined by the `CaseLinkShares`, `CaseLinks`, `ExternalLinks`, target `Contacts`, and compatible `LinkTypes` lifecycle/tenant predicates. The Case join is still mandatory for Case identity, grouping, navigation, and tenant compatibility (`c.Id = cl.CaseId` and `c.ShaleClientId = current tenant`), but `Cases.IsDeleted` alone must not suppress an otherwise active shared Link when that Case remains available through established Shale Case visibility/navigation. This is a focused Contact reverse-lookup display rule and is not a global redefinition of Case deletion behavior. The DAO explicitly validates the target `dbo.Contacts.ShaleClientId` because Contact ownership cannot be inferred from Case parties/contacts and must not rely on unrelated relationships. Tenant predicates are included for share, Case Link, External Link, Case, and Contact rows in addition to RLS. Soft-deleted shares, Case Links, External Links, unavailable Contacts, and deleted Link Types are excluded; Link Type global-or-current-tenant compatibility follows normal Case Link display semantics. Returned links are hydrated with all active shares through one connection-scoped batch share load to avoid N+1 queries.


## Entity-action audit tenancy

`dbo.EntityActionAuditLog` is strict tenant-owned audit history. It uses non-null `ShaleClientId` and the existing `TenantFilter` policy with `sec.fn_FilterByTenant(ShaleClientId)`, not the global/overlay predicate. Tenant administrators may read audit rows for their tenant through approved audit tooling; ordinary feature deletion never deletes audit history. Application DAO code may insert audit rows inside the same transaction as the business mutation and must not expose ordinary update/delete audit methods.

## Application instances (Phase 4B implementation; verification pending)

`dbo.ApplicationInstances` is strict tenant-owned history: `ShaleClientId` is non-null, its user FK is
`(ShaleClientId, UserId)`, and the enabled `TenantFilter` uses `sec.fn_FilterByTenant(ShaleClientId)` for one
FILTER plus AFTER INSERT and AFTER UPDATE block predicates. It never uses the tenant/global overlay function.
Runtime enrollment/end also qualifies by authenticated tenant and owning user. Catalog checks may run as dbo,
but live enforcement must use the disposable non-dbo verifier in
`docs/sql/verification/2026-09-28_application_instances_phase4b_rls.sql`.

## Administrative read audit tenancy (Phase 6B)

`dbo.AdministrativeReadAuditLog` is strict tenant-owned, append-only application history. Its tenant-qualified
actor FK and `TenantFilter` FILTER plus AFTER INSERT/UPDATE block predicates prevent cross-tenant attribution;
it never uses the global-overlay predicate. The server supplies tenant and actor from the authenticated
principal and revalidates current admin membership before reading or inserting. Future review is limited to a
tenant administrator or designated audit administrator through separately approved tooling; ordinary users
have no visibility and Phase 6B adds no review API or UI.
## Durable user-session tenancy (Phase 7A; live verification pending)

`dbo.UserSessions` is strict tenant-owned data. Its user and optional application-instance relationships are
tenant-qualified, trusted, and non-cascading. The enabled `TenantFilter` supplies exactly one FILTER and AFTER
INSERT/UPDATE blocks through `sec.fn_FilterByTenant(ShaleClientId)`; the global-overlay predicate is forbidden.
The internal DAO additionally qualifies every operation by authenticated tenant/user and validates an optional
desktop instance against the same tenant and owner. The explicit non-dbo verifier creates a disposable
`WITHOUT LOGIN` user, executes as it, expects error 33504 for cross-tenant writes, and cleans its exact fixture.

## Phase 8A durable-session administration

Self operations derive tenant, user, and current `sid` from the authenticated bound token. Admin operations derive
tenant/actor the same way and recheck active same-tenant `is_admin` at the service/SQL boundary. Every list/update
is explicitly tenant-qualified; a cross-tenant UUID is indistinguishable from an unavailable UUID. The new
`SessionSecurityAuditLog` is strict tenant-owned data protected by the existing `TenantFilter` FILTER and AFTER
INSERT/UPDATE predicates. RLS is defense in depth, not a replacement for predicates.


## AI integration controls — Phase 3B.1 candidate

The [new-object authority inventory](../docs/sql/ai-integration/README.md#runtime-permissions-and-tenant-isolation)
reviews every new tenant table: TenantAiIntegrationPolicy, AiIntegrationRegistration, AiIntegrationRedirect,
AiIntegrationClientKey, AiDelegationGrant, AiConsentIntent, AiDelegatedSession, AiOAuthCredential and
AiQuotaReservation. Each uses non-null ShaleClientId and the existing strict sec.TenantFilter FILTER plus
AFTER INSERT/UPDATE BLOCK predicates. No overlay, shared-function change or unrelated-table remediation.
Composite FKs and immutable-owner triggers prevent tenant/user/integration movement; no cascade deletes.

Procedure-only runtime roles deny direct table reads/DML/ALTER/CONTROL. Tenant modules retain caller/RLS
and derive both context keys; active same-tenant Users.is_admin controls administration, while consent is
self-bound. Global AiProductCapability has no tenant RLS and a separate operator-only audited boundary.
Confined owner resolvers return provisional references only; ordinary runtime cannot execute them. Identity
restoration and every new table's FILTER/BLOCK are tested with actual separate non-bypass SQL identities in
synthetic local databases. This is not production or authoritative Case RLS acceptance. The separate missing
Case artifact/Stage A/B/deployment gates remain unchanged. Keep all capability and tenant flags off.

## AI authorization transaction isolation — Phase 3B.2B candidate

`docs/sql/ai-integration/phase3b2b.sql` adds strict tenant-owned `AiAuthorizationTransaction` with
existing TenantFilter FILTER and AFTER INSERT/UPDATE BLOCK predicates. New bounded modules derive
both context identities, recheck the original durable WEB session and current authority, and use the
existing capability/tenant locks. Client loading is one requested ID under the authenticated human's
tenant; there is no caller tenant selector or expanded bootstrap pool. Direct table privileges remain
denied. Immutable bindings and durable consume state survive process replacement. The canonical
[implementation record](../docs/architecture/ai-integration-and-mcp.md#1017-phase-3b2b-authorization-code-s256-and-self-consent--2026-10-22)
separates synthetic SQL evidence from production/RLS/Case acceptance. Defaults remain OFF.

## Authorization-code exchange isolation — Phase 3B.2C.1 candidate

`AiAssertionReplay` uses existing strict TenantFilter FILTER and AFTER INSERT/UPDATE BLOCK predicates,
tenant-qualified key FK and explicit direct-table denials. `ResolveAiOAuthCodeV1` alone executes as
owner for one exact code digest/client/issuer/resource and returns only tenant/user identifiers.
A dedicated restricted bootstrap identity never supplies business access or bypass membership.
The provisional tuple initializes a fresh normal runtime lease; caller/RLS-bound exchange then rechecks
all live authority and bindings under existing locks before atomic replay/consume/audit.
See the [operator review](../docs/sql/ai-integration/README.md#phase-3b2c1-exchange-validation-candidate).
No production RLS or authoritative Case acceptance is inferred from disposable synthetic SQL.

## Opaque credential lifecycle isolation — Phase 3B.2C.2 candidate

Existing strict session/credential FILTER and AFTER INSERT/UPDATE BLOCK predicates and direct table
denials remain unchanged. A tenant-qualified authorization FK and immutable client/issuer/resource/key
lineage bind each hash-only credential family. New confined ResolveAiRefreshV1 accepts REFRESH only,
returns only provisional tenant/user and requires exact client/configured identities; no ACCESS resolver
or token-to-tenant initialization for resource authentication exists. Runtime rechecks all live authority under existing SQL locks.
See the [lifecycle operator record](../docs/sql/ai-integration/README.md#phase-3b2c2-opaque-credential-lifecycle-candidate).
No global tenant-data grant, ordinary auth-pool expansion, MCP route or Case disclosure is introduced.

## RFC 7009 revocation isolation candidate

The manual `docs/sql/ai-integration/revocation.sql` extension adds a bootstrap-only exact digest/client/
issuer/resource resolver for ACCESS or REFRESH revocation, including inactive history. A missing/foreign
credential supplies only that registration's creator for configuration-only runtime setup; it never grants
business authority. Fresh caller/RLS runtime modules revalidate client, tenant, subject membership, keys
and exact credential-family ownership under the existing capability/tenant locks. Inactive subjects remain
revocable without creating an existence oracle. Shared SQL assertion replay, family revocation and required
entity-action audit commit atomically. No table, RLS predicate, direct-table grant, general inventory or
ordinary auth-pool capability changes. See the [operator record](../docs/sql/ai-integration/README.md#rfc-7009-revocation-closeout-candidate).
This is neither bearer authentication nor token-to-VerifiedIntegrationContext; defaults remain OFF.

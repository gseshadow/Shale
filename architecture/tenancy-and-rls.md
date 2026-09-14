# Tenant Architecture

All business data is tenant scoped.

Primary tenant key:
- ShaleClientId

Database access:
- Runtime connections use shale_runtime.
- SESSION_CONTEXT('ShaleClientId') must be set before querying tenant protected tables.

Pattern:

EXEC sys.sp_set_session_context
    @key=N'ShaleClientId',
    @value=@TenantId,
    @read_only=1

RLS protected tables:
- Cases
- Contacts
- Organizations
- Users
- Tasks
- Roles
- Statuses
- Categories
- Priorities

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

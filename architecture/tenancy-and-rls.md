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

`dbo.LinkTypes` uses normalized lowercase `SystemKey` values with a filtered unique index per scope. Services should build effective Link Type lists by loading global and current-tenant rows, partitioning by non-null `SystemKey`, and preferring the tenant row when both scopes define the same key.

Remaining service-layer validation requirements:

* `dbo.ExternalLinks.LinkTypeId` must reference either a global Link Type or a Link Type with the same tenant id as the ExternalLink. This cannot be represented as a verified declarative composite foreign key while Link Types support global rows.
* `dbo.CaseLinks.ShaleClientId` must match both the linked `dbo.Cases.ShaleClientId` and `dbo.ExternalLinks.ShaleClientId`. Phase 1 keeps single-column foreign keys because the referenced tables do not expose a verified tenant composite key for direct enforcement.
* Soft-deleted CaseLinks do not block later replacements. Active uniqueness is enforced by filtered indexes for duplicate case/link pairs and one primary active link per case.

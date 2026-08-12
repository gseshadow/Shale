# Customizable Lookup Types Architecture Standard

*Last updated: 2026-07-22*

This document is the authoritative engineering standard and implementation roadmap for Shale customizable lookup type definition tables. It is documentation-only: it describes the future standard, verified current-state facts from the completed read-only audit, and implementation checklists. It does **not** assert that existing tables already conform.

Related references:

- [Database schema reference](database-schema.md)
- [Tenancy and RLS architecture](tenancy-and-rls.md)
- [Case materials architecture](case-materials.md)
- [Case dates architecture](case-dates.md)
- [Request lookup overlay SQL](../docs/sql/2026-07-21_request_lookup_overlay.sql)
- [Case materials foundation SQL](../docs/sql/2026-07-21_case_materials_foundation_phase1.sql)
- [Link type foundation SQL](../docs/sql/2026-07-16_case_links_foundation_phase1.sql)

## 1. Scope and terminology

This standard applies to Shale **customizable lookup type** definitions: small, enumerable definition lists that may have global built-ins and tenant-specific administration. They are not arbitrary user-defined form fields.

Definitions:

| Term | Definition |
| --- | --- |
| Customizable lookup definition | A row that defines a selectable type/status/category/role-like value for application records. It describes allowed choices and their metadata; it is not itself a business event. |
| Global built-in | A Shale-provided definition row with `ShaleClientId IS NULL`. Global built-ins provide default behavior and presentation for every tenant. |
| Tenant override | A tenant-scoped definition row whose `SystemKey` matches a global built-in. It overrides tenant-visible presentation/configuration while preserving the built-in semantic identity. |
| Tenant-created value | A tenant-scoped definition row whose `SystemKey` does not match a global built-in. It is available only to that tenant and receives generic behavior unless explicit domain semantics are configured. |
| Effective lookup list | The resolved list shown to a tenant after combining global built-ins and that tenant's rows, with tenant overrides winning by `SystemKey` and other tenants excluded. |
| Protected semantic key | A reserved built-in `SystemKey` that tenant-created values MUST NOT claim because application behavior or domain meaning may be attached to it. |
| Presentation fields | User-facing or display-oriented columns such as `Name`, `Description`, `Color`, and `SortOrder`. These MAY vary by tenant override. |
| Semantic fields | Behavior-driving columns or flags such as `IsClosed`, `LifecycleKey`, `IsCompleted`, `IsTerminal`, capability flags, or synchronization/reminder flags. |
| Transactional/reference table | A table that records events, assignments, references, history, or business transactions and may reference a customizable lookup definition through a foreign key. |

Lookup definitions and transactional/reference records are different concepts. For example, `Statuses` is the case-status definition table, while `CaseStatuses` is transactional case-status history referencing `Statuses.Id`. `CaseStatuses` MUST NOT be standardized or administered as a customizable lookup type definition table.

## 2. Normative schema baseline

Future customizable lookup type tables SHOULD normally use the following baseline. Existing tables differ substantially and MUST be assessed table-by-table before migration.

| Column | Recommended SQL Server type | Nullability | Default | Required vs. optional | Purpose |
| --- | --- | --- | --- | --- | --- |
| `Id` | `int IDENTITY(1,1)` | `NOT NULL` | Identity | Required identity | Stable surrogate primary key for foreign keys. Application behavior MUST NOT assume specific numeric values. |
| `ShaleClientId` | `int` | `NULL` | None | Required overlay scope | `NULL` means global built-in; non-null means tenant-owned override or tenant-created value. |
| `SystemKey` | `nvarchar(64)` | Transitionally nullable; new rows SHOULD be nonblank | Generated or seeded by application/migration | Required semantic/overlay identity | Stable key for built-ins, overlay matching, and protected semantics. |
| `Name` | `nvarchar(100)` | `NOT NULL` | None | Required presentation | Display label. MUST NOT drive application behavior. |
| `Description` | `nvarchar(500)` | `NULL` | `NULL` | Optional presentation | Longer display/help text. The preferred table shape includes the column, but values are optional. |
| `Color` | `nvarchar(20)` | `NULL` | `NULL` | Optional presentation | Display color token or hex value. The preferred table shape includes the column, but values are optional. |
| `SortOrder` | `int` | `NOT NULL` | `0` | Required presentation ordering | Stable ordering before secondary deterministic tie-breakers. |
| `IsActive` | `bit` | `NOT NULL` | `1` | Required lifecycle | Controls whether the value is selectable for new records. |
| `IsDeleted` | `bit` | `NOT NULL` | `0` | Required lifecycle | Soft-delete/reset marker. Hard deletion MUST NOT break historical references. |
| `CreatedAt` | `datetime2` | `NOT NULL` | `SYSUTCDATETIME()` | Required lifecycle/audit | Creation timestamp. |
| `UpdatedAt` | `datetime2` | `NULL` | `NULL` | Required lifecycle/audit | Last update timestamp. |
| `CreatedByUserId` | `int` | `NULL` | `NULL` | Optional actor audit | Creator user FK where actor auditing is supported. |
| `UpdatedByUserId` | `int` | `NULL` | `NULL` | Optional actor audit | Updater user FK where actor auditing is supported. |
| `RowVer` | `rowversion` | `NOT NULL` | Database generated | Required concurrency | Optimistic concurrency token for Settings mutations. |

Column groups:

- Required identity/lifecycle/concurrency columns: `Id`, `ShaleClientId`, `SystemKey`, `Name`, `SortOrder`, `IsActive`, `IsDeleted`, `CreatedAt`, `UpdatedAt`, and `RowVer`.
- Optional presentation columns: `Description` and `Color`. Columns SHOULD be present for consistency, but values MAY be `NULL`.
- Optional actor-audit columns: `CreatedByUserId` and `UpdatedByUserId` where the table's mutation surface can reliably capture the actor.
- Domain-specific semantic columns: add only when the domain requires behavior, for example `IsClosed`, `LifecycleKey`, `IsCompleted`, `IsTerminal`, capability flags, or synchronization/reminder configuration.

Verified current-state facts that differ from the future baseline include: `MaterialTypes` currently provides the most complete baseline shape; `LinkTypes` lacks `Description` and `SortOrder`; `CalendarEventTypes` uses `CalendarEventTypeId` and `ColorHex` rather than `Id` and `Color`; existing `SystemKey` types and lengths are inconsistent.

## 3. SystemKey rules

`SystemKey` is the stable identity used for overlay matching and protected built-in semantics. It is separate from display names and from numeric database identifiers.

Rules:

- Application behavior MUST never depend on `Name`.
- Application behavior MUST never depend on an assumed numeric `Id`.
- Built-in values MUST use fixed, lowercase, immutable keys.
- Tenant overrides MUST use the same `SystemKey` as the global built-in they override.
- Tenant-created values MUST receive a stable generated key.
- Generated keys MUST NOT change when `Name` changes.
- Generated keys SHOULD be opaque, such as `custom_<uuid>` or `custom_<short-id>`. They SHOULD NOT encode mutable names as permanent identity.
- Tenant-created values MUST NOT claim protected built-in keys.
- Existing nullable keys MAY remain temporarily during migration, but new implementations SHOULD create nonblank keys.
- Key casing MUST be normalized and validated consistently, preferably lowercase.

`SystemKey` answers "which definition is this?" and "does this tenant row override a global built-in?" Domain semantic fields answer "what behavior should this definition have?" Examples include `IsClosed`, `IsCompleted`, `IsTerminal`, or capability flags. A custom value with an unknown `SystemKey` MUST receive generic behavior unless explicit semantic fields/configuration say otherwise.

## 4. Global/tenant overlay algorithm

Effective-list reads MUST implement this behavior:

1. Load candidate rows where `ShaleClientId IS NULL` or `ShaleClientId = @TenantId`.
2. Exclude rows for all other tenants.
3. Treat `ShaleClientId IS NULL` as a global definition.
4. Treat a tenant row with a `SystemKey` matching a global built-in as a tenant override.
5. Treat a tenant row with a nonmatching `SystemKey` as a tenant-created value.
6. Produce no duplicate global/tenant value for the same non-null `SystemKey`.
7. Select the tenant row as the winner when both global and tenant rows exist for the same `SystemKey`.
8. Use deterministic winner selection and ordering, for example `SortOrder`, `Name`, `SystemKey`, then `Id` as tie-breakers after resolving overlay winners.
9. Never use `Name` for overlay matching.

Lifecycle behavior:

- `IsActive = 0` MUST prevent selection for new records while preserving existing references.
- `IsDeleted = 1` is soft deletion and MUST NOT break historical foreign keys.
- A deleted tenant override SHOULD expose/reset to the global default, following the established LinkTypes Settings behavior documented in [tenancy-and-rls.md](tenancy-and-rls.md). In effective-list terms, a deleted override is a reset marker, not a tenant-visible replacement.
- If a tenant needs to hide a global built-in, use an inactive tenant override unless the application defines a separate suppression mechanism.
- Existing records referencing inactive or deleted definitions MUST still render safely, usually by loading the referenced row directly or returning a disabled/archived display model.

## 5. Required constraints, indexes, FKs, and RLS

Customizable lookup type tables SHOULD include these database protections when the live schema and migration path allow them:

- Foreign key from `ShaleClientId` to `dbo.ShaleClients(Id)`.
- Optional actor foreign keys from `CreatedByUserId` and `UpdatedByUserId` to `dbo.Users(id)` where actor auditing is supported.
- Unique global `SystemKey` enforcement.
- Unique tenant-scoped `SystemKey` enforcement.
- Effective-list indexes supporting tenant-or-global reads ordered by `SystemKey`, `IsDeleted`, `IsActive`, and `SortOrder` as appropriate.
- Lowercase and nonblank `SystemKey` validation for new implementations.
- Deleted-implies-inactive validation (`IsDeleted = 0 OR IsActive = 0`) if retained as the Shale standard for that table.
- RLS using the tenant-or-global predicate for overlay definition tables.
- Normal tenant filtering for strict tenant-owned transactional/reference tables.
- `RowVer`-based optimistic concurrency for Settings mutations.

SQL Server permits multiple `NULL` values in filtered unique designs unless indexes are explicit. Recommended enforcement is:

```sql
CREATE UNIQUE INDEX UX_<Table>_Global_SystemKey
ON dbo.<Table>(SystemKey)
WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL;

CREATE UNIQUE INDEX UX_<Table>_Tenant_SystemKey
ON dbo.<Table>(ShaleClientId, SystemKey)
WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL;
```

Where a table still has legacy nullable keys, migrations MUST first verify duplicates, mixed-case keys, blank keys, and tenant/global collisions before tightening constraints.

## 6. Behavior-sensitive customizable lookup type rules

Some customizable lookup types affect workflow, authorization, filtering, or rendering beyond ordinary presentation. These require explicit semantic design before tenant administration is enabled.

### Statuses

- `Statuses` is the case-status definition table.
- Preserve `IsClosed` and `LifecycleKey`.
- Case behavior MUST NOT use status `Name` or assumed `Id` values.
- `CaseStatuses` remains transactional case-status history referencing `Statuses.Id`; it MUST NOT be modified as though it were a definition table.
- Known live-data concern: global `Denied` and `Closed` rows were observed with `IsClosed = 0`. This MUST be verified and corrected deliberately before migration or behavior changes.

### TaskStatuses

- Task status customization requires an explicit completion/terminal semantic design.
- Unknown custom task statuses MUST default to noncompleted and nonterminal.
- Completion MUST NOT be inferred from names such as `Done` or `Completed`.

### RequestStatuses

- Request workflow behavior MUST use stable semantic identity or explicit flags.
- Legacy `MaterialRequests` text status compatibility MUST be considered during migration because request lookup overlay work currently coexists with text status fields.

### PartyRoles

- Existing behavior that depends on `Name == "party"` MUST be replaced with `SystemKey` or an explicit semantic field before administration is considered safe.
- Unknown roles MUST behave generically.

### PartySides

- Built-ins such as `represented`, `opposing`, and `neutral` MUST use stable semantics.
- Unknown sides MUST behave generically.

### Roles

- `Roles` is currently unsafe for tenant customization.
- Existing authorization and assignment behavior relies heavily on numeric IDs.
- The live inventory says `Roles` currently has no `SystemKey`.
- Do not merely add `SystemKey` and immediately enable tenant administration.
- A separate authorization/capability model and migration plan is required first.
- Unknown tenant-created roles MUST receive no privileged capabilities by default.

### CaseDateTypes

- `CaseDateTypes` describes authoritative case facts or deadlines, not manually created calendar events.
- Broad presentation/filter categories use a constrained `CalendarCategory` vocabulary rather than a separate table.
- Built-in fixed legal/factual case fields stay authoritative in `Cases` until a later verified migration.
- Workflow/lifecycle dates remain owned by workflow fields unless deliberately reclassified.

### CalendarEventTypes

- `CalendarEventTypes` describes manually created events only; do not use it for authoritative case facts or deadlines.
- Unknown custom event types MUST render and schedule normally.
- Unknown custom event types MUST receive no special synchronization/reminder behavior unless explicitly configured.

## 7. Adding a new customizable lookup type checklist

Use this ordered checklist for any new customizable lookup type or modernization effort:

1. Define product/domain semantics, including whether any built-ins have behavior beyond presentation.
2. Design the database migration from verified live schema, not from assumptions.
3. Define global seeds and protected keys.
4. Add constraints and indexes, including filtered uniqueness for global and tenant-scoped `SystemKey`.
5. Add RLS using tenant-or-global behavior for definition tables.
6. Implement DAO read and mutation paths with tenant context and admin authorization.
7. Implement effective overlay resolution by `SystemKey`.
8. Add DTO/model fields for `SystemKey`, presentation fields, lifecycle fields, and `RowVer`.
9. Add or update service ports and adapters.
10. Add Desktop Settings administration only after behavior-sensitive semantics are safe.
11. Update shared selectors, pills, description, and color presentation.
12. Add server API endpoints if web or external clients need access.
13. Add web UI behavior where exposed.
14. Implement `RowVer` conflict handling for updates, resets, activation changes, and deletion.
15. Ensure existing-reference rendering works for inactive/deleted definitions.
16. Add focused tests from the testing standard below.
17. Update architecture/schema documentation with current-state facts and future requirements.
18. Perform deployment and live-data verification before applying schema constraints.

Do not ship if any of these checks fail:

- No behavior based on display `Name`.
- No new hardcoded numeric IDs.
- No cross-tenant rows in effective results.
- No hard deletion of referenced definitions.
- No update API without concurrency handling.
- No selector that rejects unknown custom values.
- No migration that assumes checked-in schema exactly matches production.

## 8. Testing standard

Implementations SHOULD include focused coverage for:

- Global-only effective result.
- Tenant override winning by `SystemKey`.
- Tenant-created result.
- Other-tenant exclusion.
- Inactive behavior for new selection.
- Soft-deleted behavior and reset-to-global behavior.
- Duplicate/conflicting keys.
- Null legacy `SystemKey` behavior during transition.
- Stable ordering.
- Unknown custom semantic behavior.
- Existing references to inactive/deleted definitions.
- `RowVer` conflict handling.
- RLS/database integration where practical.
- Desktop selector/admin contracts.
- Server/web serialization where exposed.

## 9. Current-table roadmap

Initial classification from the read-only audit:

- Mature or comparatively mature overlay implementations: `LinkTypes`, `MaterialTypes`, `RequestMethods`, `RequestStatuses`.
- New Phase 1A foundation following this standard: `CaseDateTypes` with `CaseDates` as the tenant-owned occurrence table.
- Partial overlay implementations: `CalendarEventTypes`, `PracticeAreas`, `Statuses`.
- Behavior-sensitive customizable lookups: `PartyRoles`, `PartySides`, `Statuses`, `TaskStatuses`, `RequestStatuses`, and `Roles`, although `Roles` is not yet safe for tenant customization.
- Uncertain or requiring a product decision: `Categories`, `OrganizationTypes`.
- Placeholder: `TaskCategories`.
- Explicit exclusion: `CaseStatuses` is transactional case-status history referencing `Statuses.Id`; it is not a customizable lookup definition table.

| Candidate | Current classification | Important gap | Behavior sensitivity | Recommended phase | Preconditions |
| --- | --- | --- | --- | --- | --- |
| `LinkTypes` | Mature/comparatively mature overlay | Lacks `Description` and `SortOrder` versus future baseline | Low to moderate; link display and selection | Phase 2 | Verify live constraints, RLS, reset behavior, and existing references. |
| `MaterialTypes` | Mature/comparatively mature overlay | Closest baseline; still verify live constraints and data | Low to moderate; material request categorization | Phase 2 | Verify live catalog, protected keys, RLS, RowVer mutation behavior. |
| `RequestMethods` | Mature/comparatively mature overlay | Supports nullable `Color`; type/length differs from preferred `SystemKey`; verify compatibility | Moderate; request workflow/reporting | Phase 2 | Preserve legacy `MaterialRequests` text method compatibility during migration. |
| `RequestStatuses` | Mature/comparatively mature overlay and behavior-sensitive | Needs stable workflow semantics/flags beyond presentation | High; request workflow state | Phase 2 with behavior safeguards | Preserve legacy text status compatibility; verify semantic keys or flags. |
| `CalendarEventTypes` | Partial overlay | Uses `CalendarEventTypeId` and `ColorHex` rather than `Id` and `Color` | Moderate; scheduling plus possible sync/reminder behavior | Phase 4 | Decide mapping strategy; verify any special behavior before admin. |
| `PracticeAreas` | Partial overlay | Needs alignment to baseline and effective overlay validation | Low to moderate; case classification | Phase 4 | Verify live columns, keys, constraints, and references. |
| `Statuses` | Partial overlay and behavior-sensitive | Preserve `IsClosed`/`LifecycleKey`; verify incorrect live `IsClosed` data | High; case lifecycle | Phase 3 | Live verify `Denied`/`Closed`, preserve history, do not alter `CaseStatuses` as definition data. |
| `PartyRoles` | Behavior-sensitive customizable lookup | Name-based behavior exists; must move to `SystemKey` or explicit semantic | High; party/contact behavior | Phase 1 behavior correction; Phase 5 administration | Required `SystemKey` exists; replace `Name == "party"`; verify unknown generic behavior. |
| `PartySides` | Behavior-sensitive customizable lookup | Needs stable semantics for represented/opposing/neutral | High; party alignment/filtering | Phase 5 | Verify `SystemKey`, remove name/id assumptions, define generic unknown behavior. |
| `TaskStatuses` | Behavior-sensitive customizable lookup | Needs explicit completion/terminal semantics | High; task completion workflow | Phase 6 | Define `IsCompleted`/`IsTerminal` or equivalent; migrate name-based completion logic. |
| `Roles` | Behavior-sensitive, not safe for tenant customization | No `SystemKey`; numeric authorization/assignment semantics | Critical; authorization and assignment | Phase 7 | Separate authorization/capability redesign; migration plan; deny privileges by default. |
| `Categories` | Uncertain/product decision | Candidate purpose and administration model unresolved | Unknown | Phase 8 | Product decision and live schema verification. |
| `OrganizationTypes` | Uncertain/product decision | Candidate purpose and administration model unresolved | Unknown | Phase 8 | Product decision and live schema/reference verification. |
| `TaskCategories` | Placeholder | Placeholder status; definition/use unclear | Unknown | Phase 8 | Product decision, schema inventory, and implementation proposal. |
| `CaseStatuses` | Explicit exclusion | Transactional history referencing `Statuses.Id`, not definition data | High; case history integrity | Excluded | Never standardize as a customizable lookup type definition table. |

Phased roadmap:

- Phase 0: Live catalog and data verification; no schema changes.
- Phase 1: Publish this architecture standard; correct safe name-based behavior where the required `SystemKey` already exists. `PartyRoles` is a likely first candidate. Do not include `Roles` because it currently lacks `SystemKey` and uses numeric authorization semantics.
- Phase 2: Verify and align mature material/request/link overlay tables: `LinkTypes`, `MaterialTypes`, `RequestMethods`, and `RequestStatuses`.
- Phase 3: Modernize `Statuses` while preserving `IsClosed` and `LifecycleKey`. Never modify `CaseStatuses` as though it were a definition table.
- Phase 4: Modernize `PracticeAreas` and `CalendarEventTypes`.
- Phase 5: Add `PartyRoles` and `PartySides` administration after semantic behavior is safe.
- Phase 6: Modernize `TaskStatuses` after explicit completion/terminal semantics are established.
- Phase 7: Complete a separate `Roles` authorization/capability redesign.
- Phase 8: Make product decisions for `Categories`, `OrganizationTypes`, and `TaskCategories`.

## 10. Live verification appendix

This is a verification checklist, not a destructive migration. Run against the target SQL Server environment before designing or applying changes.

- Exact columns, SQL types, nullability, identity settings, defaults, and computed columns.
- Primary keys and foreign keys, including lookup references from transactional/reference tables.
- Unique indexes, filtered indexes, included columns, and disabled/hypothetical index state.
- RLS security policies and predicates, especially whether a table uses tenant-or-global filtering or strict tenant filtering.
- Duplicate non-null `SystemKey` values globally and per tenant.
- Mixed-case `SystemKey` values.
- `NULL` and blank `SystemKey` values.
- Global/tenant collisions and whether tenant rows intentionally override built-ins.
- Referencing transactional rows, including references to inactive or deleted definitions.
- Active/deleted inconsistencies such as `IsDeleted = 1` with `IsActive = 1`.
- Behavior-sensitive data quality, including the known concern that global `Denied` and `Closed` status rows were observed with `IsClosed = 0`.

Suggested read-only inspection patterns:

```sql
-- Columns, types, nullability, defaults.
SELECT
    c.name AS ColumnName,
    t.name AS TypeName,
    c.max_length,
    c.precision,
    c.scale,
    c.is_nullable,
    dc.definition AS DefaultDefinition
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
WHERE c.object_id = OBJECT_ID(N'dbo.<Table>')
ORDER BY c.column_id;

-- Duplicate global keys.
SELECT SystemKey, COUNT(*) AS RowCount
FROM dbo.<Table>
WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL
GROUP BY SystemKey
HAVING COUNT(*) > 1;

-- Duplicate tenant keys.
SELECT ShaleClientId, SystemKey, COUNT(*) AS RowCount
FROM dbo.<Table>
WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL
GROUP BY ShaleClientId, SystemKey
HAVING COUNT(*) > 1;

-- Mixed-case, null, and blank keys.
SELECT Id, ShaleClientId, SystemKey, Name
FROM dbo.<Table>
WHERE SystemKey IS NULL
   OR NULLIF(LTRIM(RTRIM(SystemKey)), N'') IS NULL
   OR SystemKey <> LOWER(SystemKey);

-- Active/deleted inconsistencies.
SELECT Id, ShaleClientId, SystemKey, Name, IsActive, IsDeleted
FROM dbo.<Table>
WHERE IsDeleted = 1 AND IsActive = 1;
```

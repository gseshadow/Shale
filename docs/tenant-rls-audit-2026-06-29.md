# Tenant RLS Audit — 2026-06-29

## Scope and source limits

This audit is based on repository documentation, SQL scripts, and DAO/query code only. The repo contains tenant schema references and additive migrations, but no checked-in SQL Server RLS creation script (`CREATE SCHEMA security`, `CREATE FUNCTION ... SESSION_CONTEXT`, or `CREATE SECURITY POLICY`) was found by repository search. Run the manual validation SQL below against each environment before applying any remediation.

Documents reviewed:

- `architecture/codex-prompt-rules.md`
- `architecture/database-schema.md`
- `architecture/tenancy-and-rls.md`
- `docs/web-api-migration-step-2.md`
- `docs/web-api-step-3.md`
- `docs/modularization_migration_runbook.md`
- `docs/sql/*.sql`
- DAO code under `shale-data/src/main/java/com/shale/data/dao`
- Server request/runtime session code under `shale-server/src/main/java/com/shale/server`

## Repository RLS artifact inventory

| Artifact type | Repository finding | Risk |
| --- | --- | --- |
| `security` schema | Not found in checked-in SQL. | Medium: RLS may exist only in live DB drift/manual setup and cannot be reproduced from repo. |
| Predicate function(s), e.g. `rls.fn_TenantMatch` | Not found in checked-in SQL. | High: tenant/global semantics cannot be verified from source-controlled migration history. |
| `CREATE SECURITY POLICY` statements | Not found in checked-in SQL. | High: new tenant tables can be created without RLS unless live DB has out-of-band policies. |
| Filter predicates per table | Not found in checked-in SQL. | High for tables where DAO paths rely on RLS/session context rather than explicit predicates. |

## Expected predicate behavior

Use two predicate shapes, depending on table semantics:

1. **Strict tenant table**: visible only when `ShaleClientId = CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)`.
2. **Global overlay lookup/admin table**: visible when `ShaleClientId IS NULL OR ShaleClientId = CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)`. Other tenant rows must be denied.

The global-overlay behavior is appropriate only for lookup/default tables whose migrations intentionally allow `ShaleClientId IS NULL` global rows.

## Tenant table coverage report

| Table | Has `ShaleClientId` column? | Has RLS policy in repo? | Predicate used in repo | Expected tenant behavior | Risk | Recommended fix |
| --- | --- | --- | --- | --- | --- | --- |
| `dbo.Users` | Yes, documented. | No. | None found. DAO generally tenant-filters list/search/detail paths and has tenant-scoped unique index migration. | Strict tenant. Auth may read user rows through auth/app path, but runtime user lists must be tenant scoped. | Medium. | Verify live RLS. If absent, add strict policy. Keep auth/login path explicitly constrained by credential lookup and tenant assignment. |
| `dbo.Cases` | Yes, documented. | No. | None found. DAOs generally pass tenant id and/or run under runtime session. | Strict tenant. | High if live RLS absent because cases are primary PHI/business rows. | Verify live RLS. If absent, add strict policy and keep explicit `c.ShaleClientId = ?` filters on broad read paths. |
| `dbo.Contacts` | Yes, documented. | No. | None found. DAO has tenant-explicit paths plus runtime-session checks. | Strict tenant. | High if live RLS absent. | Verify live RLS. If absent, add strict policy. |
| `dbo.Organizations` | Yes, documented. | No. | None found. DAO includes runtime session checks and tenant-aware paths. | Strict tenant. | High if live RLS absent. | Verify live RLS. If absent, add strict policy. |
| `dbo.CaseUpdates` | Yes, documented. | No. | None found. Query patterns include `cu.ShaleClientId = c.ShaleClientId`. | Strict tenant; soft-deleted rows also filtered by app logic. | Medium. | Verify live RLS. If absent, add strict policy. |
| `dbo.Tasks` | Yes, required by tenancy doc, referenced heavily in DAO. | No. | None found. Task DAO often filters by passed tenant and joins tenant/global lookup rows. | Strict tenant. | High if live RLS absent. | Verify live RLS. If absent, add strict policy. |
| `dbo.TaskUpdates` | Yes, created by migration. | No. | None found. | Strict tenant. | High: new tenant table migration does not create source-controlled RLS. | Add strict policy after live precheck. |
| `dbo.AuditLog` | Yes, nullable tenant column added by migration. | No. | None found. DAO uses `SESSION_CONTEXT(N'ShaleClientId')` helper. | Mixed: tenant rows should be strict; legacy/system rows with NULL need an explicit admin-only or non-runtime policy decision. | High: nullable audit rows can be ambiguous. | Define policy explicitly. Runtime tenant reads should see only own tenant rows; decide separately whether `NULL` rows are system/admin-only. |
| `dbo.Statuses` | Yes, documented nullable after migration. | No. | None found. DAO uses `ShaleClientId = ? OR ShaleClientId IS NULL` for lookup. | Global overlay: global + current tenant only. | Medium. | Verify live overlay RLS. If absent, add global-overlay policy. |
| `dbo.Roles` | Yes, documented. | No. | None found. | Strict tenant unless later converted to global overlay. | Medium. | Verify live RLS. If absent, add strict policy. |
| `dbo.Categories` | Yes per tenancy doc; schema details not in current schema excerpt. | No. | None found. | Strict tenant unless proven lookup/global. | Medium. | Verify live schema and RLS. Add strict or overlay policy according to actual nullability/semantics. |
| `dbo.Priorities` | Yes, modularization migrations allow global rows. | No. | None found. DAO uses `ShaleClientId = ? OR ShaleClientId IS NULL` for lookup. | Global overlay. | Medium. | Verify live overlay RLS. If absent, add global-overlay policy. |
| `dbo.PracticeAreas` | Yes, migration requires column and phase 3 allows global rows. | No. | None found. DAO `listPracticeAreas` explicitly filters to `NULL` or current tenant, but several case/task joins join by id without tenant predicate. | Global overlay for settings/listing; case joins should not expose other-tenant labels if an invalid cross-tenant FK exists. | **High** because Settings previously showed tenant leakage and no source-controlled RLS was found. | Add/verify overlay RLS. Also harden joins to `pa.ShaleClientId = c.ShaleClientId OR pa.ShaleClientId IS NULL` where practical. |
| `dbo.PartyRoles` | Yes, modularized global activation migration. | No. | None found. DAO lookup paths use explicit overlay predicates for party roles. | Global overlay. | Medium. | Verify live overlay RLS. If absent, add overlay policy. |
| `dbo.PartySides` | Yes, modularized global activation migration. | No. | None found. DAO lookup paths use explicit overlay predicates for party sides. | Global overlay. | Medium. | Verify live overlay RLS. If absent, add overlay policy. |
| `dbo.CalendarEventTypes` | Yes, created nullable. | No. | None found. DAO list filters `tenant OR NULL`. | Global overlay. | Medium. | Add overlay RLS policy when calendar tables are deployed. |
| `dbo.CalendarEvents` | Yes, created not null. | No. | None found. DAO should run through runtime session and filter by tenant. | Strict tenant. | High if exposed without RLS. | Add strict RLS policy when calendar tables are deployed. |
| `dbo.UserPreferences` | Yes, created not null. | No. | None found. DAO filters by tenant/user key. | Strict tenant, and usually user scoped at app layer. | Medium. | Add strict tenant RLS; retain `UserId` app filters. |
| `dbo.UserBoardLanePreferences` | Yes, created not null. | No. | None found. DAO filters by tenant/user/board/lane key. | Strict tenant, and usually user scoped at app layer. | Medium. | Add strict tenant RLS; retain `UserId` app filters. |

## DAO/query path findings

### Runtime session path

- Desktop runtime uses `RuntimeSessionService` and server request-scope code is documented to initialize `SESSION_CONTEXT` for `ShaleClientId` and `PrincipalUserId` before DAO calls.
- Existing DAOs call `db.requireConnection()`, which is only safe when the injected session provider is runtime/request scoped and initialized. Any direct `DataSources.app()`/admin path for tenant data should be treated as high risk unless it has explicit `ShaleClientId` predicates.

### PracticeAreas high-priority notes

- `CaseDao.listPracticeAreas(...)` is explicitly safe at the SQL level for the Settings/effective list use case: it selects only `ShaleClientId IS NULL OR ShaleClientId = ?`, then Java keeps global rows separate from same-tenant rows before resolving tenant overrides.
- `CaseDao.listTenantPracticeAreas(...)` is tenant-only and safe for tenant-management screens.
- `CaseDao.listPracticeAreasForTenant(...)` first loads tenant-only active rows and seeds from global templates only when no tenant rows exist. The seed path intentionally reads global template rows.
- Multiple case/task display joins use `LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId` or equivalent. If `Cases` RLS is present and `PracticeAreaId` values are valid, this is probably safe. If RLS is absent or a cross-tenant/global-invalid `PracticeAreaId` is stored, those joins can reveal another tenant's practice-area name/color. Harden them with `(pa.ShaleClientId = c.ShaleClientId OR pa.ShaleClientId IS NULL)`.

## Manual validation SQL: current RLS coverage

Run this read-only script in each SQL Server environment:

```sql
SET NOCOUNT ON;

DECLARE @TenantTables table (SchemaName sysname, TableName sysname, ExpectedPredicate nvarchar(30));
INSERT INTO @TenantTables (SchemaName, TableName, ExpectedPredicate) VALUES
('dbo','Users','STRICT'),
('dbo','Cases','STRICT'),
('dbo','Contacts','STRICT'),
('dbo','Organizations','STRICT'),
('dbo','CaseUpdates','STRICT'),
('dbo','Tasks','STRICT'),
('dbo','TaskUpdates','STRICT'),
('dbo','AuditLog','STRICT_OR_ADMIN_NULL'),
('dbo','Roles','STRICT'),
('dbo','Categories','STRICT'),
('dbo','Statuses','GLOBAL_OVERLAY'),
('dbo','Priorities','GLOBAL_OVERLAY'),
('dbo','PracticeAreas','GLOBAL_OVERLAY'),
('dbo','PartyRoles','GLOBAL_OVERLAY'),
('dbo','PartySides','GLOBAL_OVERLAY'),
('dbo','CalendarEventTypes','GLOBAL_OVERLAY'),
('dbo','CalendarEvents','STRICT'),
('dbo','UserPreferences','STRICT'),
('dbo','UserBoardLanePreferences','STRICT');

SELECT
    tt.SchemaName,
    tt.TableName,
    HasShaleClientId = CONVERT(bit, CASE WHEN c.object_id IS NULL THEN 0 ELSE 1 END),
    ShaleClientIdNullable = c.is_nullable,
    tt.ExpectedPredicate,
    SecurityPolicy = sp.name,
    PredicateFunction = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    p.type_desc AS PredicateType,
    p.operation_desc AS Operation,
    sp.is_enabled AS PolicyEnabled,
    m.definition AS PredicateDefinition
FROM @TenantTables tt
LEFT JOIN sys.tables t
  ON t.name = tt.TableName
 AND SCHEMA_NAME(t.schema_id) = tt.SchemaName
LEFT JOIN sys.columns c
  ON c.object_id = t.object_id
 AND c.name = N'ShaleClientId'
LEFT JOIN sys.security_predicates p
  ON p.object_id = t.object_id
LEFT JOIN sys.security_policies sp
  ON sp.object_id = p.security_policy_id
LEFT JOIN sys.sql_modules m
  ON m.object_id = p.target_object_id
ORDER BY tt.SchemaName, tt.TableName, sp.name, p.operation_desc;
```

## Manual validation SQL: tables with tenant column but no enabled RLS predicate

```sql
SELECT
    SchemaName = SCHEMA_NAME(t.schema_id),
    TableName = t.name,
    ShaleClientIdNullable = c.is_nullable
FROM sys.tables t
JOIN sys.columns c
  ON c.object_id = t.object_id
 AND c.name = N'ShaleClientId'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.security_predicates p
    JOIN sys.security_policies sp
      ON sp.object_id = p.security_policy_id
    WHERE p.object_id = t.object_id
      AND sp.is_enabled = 1
)
ORDER BY SchemaName, TableName;
```

## Manual validation SQL: predicate shape review

```sql
SELECT
    PolicyName = sp.name,
    TargetTable = OBJECT_SCHEMA_NAME(p.object_id) + N'.' + OBJECT_NAME(p.object_id),
    PredicateFunction = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    p.operation_desc,
    sp.is_enabled,
    m.definition
FROM sys.security_predicates p
JOIN sys.security_policies sp
  ON sp.object_id = p.security_policy_id
LEFT JOIN sys.sql_modules m
  ON m.object_id = p.target_object_id
ORDER BY TargetTable, p.operation_desc, PolicyName;
```

## Recommended migration approach

Do **not** run a destructive migration. First run the validation SQL above and compare live policies with this report. If the live database lacks source-controlled RLS for these tables, use a new additive migration like `docs/sql/proposed/2026-06-29_tenant_rls_coverage_proposed.sql` as a starting point and tailor table list to actual live schema.

Key prerequisites before enabling:

1. Confirm all target tables exist in the target environment.
2. Confirm each target table has `ShaleClientId` with intended nullability.
3. Confirm runtime role/user name that should receive policy protection.
4. Decide `AuditLog` null-row behavior explicitly before adding a global-overlay predicate to it.
5. Add PracticeAreas join hardening in application SQL in a follow-up patch, because that is a safe defense-in-depth even with RLS.

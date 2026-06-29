/*
  Proposed tenant RLS coverage migration — implementation plan and safe migration.

  Status: PROPOSAL ONLY. Do not apply automatically. Run the read-only preflight
  sections first in the target environment, review output, then run the migration
  in a planned maintenance window.

  This script intentionally extends the existing live RLS pattern instead of
  creating a competing rls.* system:
    - Existing schema:    sec
    - Existing predicate: sec.fn_FilterByTenant(@ShaleClientId)
    - Existing policy:    TenantFilter

  Live assumption from manual audit:
    - dbo.Cases already has an enabled FILTER predicate under TenantFilter:
      sec.fn_FilterByTenant([ShaleClientId])

  Investigation answers encoded by this plan:
    1. sec.fn_FilterByTenant strict-vs-overlay behavior must be verified from
       sys.sql_modules before migration. The preflight prints the definition and
       reports whether it appears to allow ShaleClientId IS NULL.
    2. A separate overlay predicate is needed for lookup/default tables when the
       existing sec.fn_FilterByTenant is strict only; otherwise global/default
       rows (ShaleClientId IS NULL) would be hidden.
    3. SQL Server supports ALTER SECURITY POLICY ... ADD FILTER PREDICATE, so this
       plan adds predicates to the existing TenantFilter policy.
    4. Nullable lookup/default tables must not receive a strict predicate because
       that would hide intended global/default rows and can break Settings,
       Tasks, Contacts, Organizations, and Case Overview lookup rendering.
    5. BLOCK predicates are deferred. Add them only after write-path review for
       inserts/updates, seed paths, system/global rows, and admin jobs.

  Classification summary:
    STRICT tenant-owned tables:
      CalendarEvents, CaseTimelineEvents, CaseUpdates, Contacts, Facilities,
      Notifications, Organizations, Providers, TaskAssignments, Tasks,
      TaskTimelineEvents, TaskUpdates, UserBoardLanePreferences, UserPreferences.

    OVERLAY/global lookup/default tables:
      CalendarEventTypes, Categories, OrganizationTypes, PartyRoles, PartySides,
      PracticeAreas, Priorities, Roles, Statuses, TaskCategories, TaskStatuses.

    DEFER/careful:
      auditLog/AuditLog: nullable/system-row semantics not decided.
      Users: existing fn_TenantMatch_Users/equivalent must be reviewed before
             attaching a generic ShaleClientId predicate.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ============================================================================
   READ-ONLY PREFLIGHT: existing policy/function inventory and compatibility
   ============================================================================ */

SELECT
    PolicyName = sp.name,
    PolicyEnabled = sp.is_enabled,
    TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    PredicateType = p.predicate_type_desc,
    Operation = p.operation_desc,
    PredicateDefinition = p.predicate_definition
FROM sys.security_predicates AS p
JOIN sys.security_policies AS sp
  ON sp.object_id = p.object_id
WHERE sp.name = N'TenantFilter'
ORDER BY TargetTable, PredicateType, Operation;

SELECT
    FunctionName = N'sec.fn_FilterByTenant',
    FunctionExists = CONVERT(bit, CASE WHEN OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NOT NULL THEN 1 ELSE 0 END),
    AppearsToAllowNullGlobals = CONVERT(bit, CASE
        WHEN OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NOT NULL
         AND LOWER(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant', N'IF'))) LIKE N'%is null%'
        THEN 1 ELSE 0 END),
    Definition = OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant', N'IF'));

SELECT
    RequiredObject = v.ObjectName,
    ObjectExists = CONVERT(bit, CASE
        WHEN v.ObjectType = N'IF' AND OBJECT_ID(v.ObjectName, N'IF') IS NOT NULL THEN 1
        WHEN v.ObjectType = N'SECURITY_POLICY' AND EXISTS (SELECT 1 FROM sys.security_policies WHERE name = v.ObjectName) THEN 1
        ELSE 0 END)
FROM (VALUES
    (N'sec.fn_FilterByTenant', N'IF'),
    (N'TenantFilter', N'SECURITY_POLICY')
) AS v(ObjectName, ObjectType);

DECLARE @Coverage TABLE (
    Classification nvarchar(20) NOT NULL,
    SchemaName sysname NOT NULL,
    TableName sysname NOT NULL,
    Notes nvarchar(4000) NULL
);

INSERT INTO @Coverage (Classification, SchemaName, TableName, Notes) VALUES
(N'STRICT',  N'dbo', N'CalendarEvents',             N'tenant-owned calendar rows'),
(N'STRICT',  N'dbo', N'CaseTimelineEvents',         N'tenant-owned case timeline rows'),
(N'STRICT',  N'dbo', N'CaseUpdates',                N'tenant-owned case notes/updates'),
(N'STRICT',  N'dbo', N'Contacts',                   N'tenant-owned contacts'),
(N'STRICT',  N'dbo', N'Facilities',                 N'tenant-owned facilities'),
(N'STRICT',  N'dbo', N'Notifications',              N'tenant-owned notifications'),
(N'STRICT',  N'dbo', N'Organizations',              N'tenant-owned organizations'),
(N'STRICT',  N'dbo', N'Providers',                  N'tenant-owned providers'),
(N'STRICT',  N'dbo', N'TaskAssignments',            N'tenant-owned task assignments'),
(N'STRICT',  N'dbo', N'Tasks',                      N'tenant-owned tasks'),
(N'STRICT',  N'dbo', N'TaskTimelineEvents',         N'tenant-owned task timeline rows'),
(N'STRICT',  N'dbo', N'TaskUpdates',                N'tenant-owned task updates'),
(N'STRICT',  N'dbo', N'UserBoardLanePreferences',   N'tenant/user-scoped preferences'),
(N'STRICT',  N'dbo', N'UserPreferences',            N'tenant/user-scoped preferences'),
(N'OVERLAY', N'dbo', N'CalendarEventTypes',         N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'Categories',                 N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'OrganizationTypes',          N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'PartyRoles',                 N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'PartySides',                 N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'PracticeAreas',              N'global defaults plus tenant overrides; Settings leak fix'),
(N'OVERLAY', N'dbo', N'Priorities',                 N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'Roles',                      N'global defaults plus tenant overrides per current plan'),
(N'OVERLAY', N'dbo', N'Statuses',                   N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'TaskCategories',             N'global defaults plus tenant overrides'),
(N'OVERLAY', N'dbo', N'TaskStatuses',               N'global defaults plus tenant overrides'),
(N'DEFER',   N'dbo', N'auditLog',                   N'defer until nullable/system audit rows are classified'),
(N'DEFER',   N'dbo', N'AuditLog',                   N'defer until nullable/system audit rows are classified'),
(N'DEFER',   N'dbo', N'Users',                      N'review existing fn_TenantMatch_Users/equivalent first');

SELECT
    c.Classification,
    TargetTable = QUOTENAME(c.SchemaName) + N'.' + QUOTENAME(c.TableName),
    TableExists = CONVERT(bit, CASE WHEN t.object_id IS NOT NULL THEN 1 ELSE 0 END),
    HasShaleClientId = CONVERT(bit, CASE WHEN col.object_id IS NOT NULL THEN 1 ELSE 0 END),
    ShaleClientIdNullable = col.is_nullable,
    ExistingTenantFilterPredicate = CONVERT(bit, CASE WHEN existing.object_id IS NOT NULL THEN 1 ELSE 0 END),
    ExistingPredicateDefinition = existing.predicate_definition,
    c.Notes
FROM @Coverage AS c
LEFT JOIN sys.tables AS t
  ON t.name = c.TableName
 AND SCHEMA_NAME(t.schema_id) = c.SchemaName
LEFT JOIN sys.columns AS col
  ON col.object_id = t.object_id
 AND col.name = N'ShaleClientId'
OUTER APPLY (
    SELECT TOP (1) p.*
    FROM sys.security_predicates AS p
    JOIN sys.security_policies AS sp
      ON sp.object_id = p.object_id
    WHERE p.target_object_id = t.object_id
      AND p.predicate_type_desc = N'FILTER'
      AND sp.name = N'TenantFilter'
    ORDER BY p.security_predicate_id
) AS existing
ORDER BY c.Classification, c.SchemaName, c.TableName;
GO

/* ============================================================================
   MIGRATION: extend existing TenantFilter policy with missing FILTER predicates
   ============================================================================ */

IF SCHEMA_ID(N'sec') IS NULL
BEGIN
    THROW 53000, 'Required schema sec is missing. Stop: do not create a competing RLS system.', 1;
END;

IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL
BEGIN
    THROW 53001, 'Required predicate sec.fn_FilterByTenant is missing. Stop and investigate live RLS before proceeding.', 1;
END;

IF NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = N'TenantFilter')
BEGIN
    THROW 53002, 'Required security policy TenantFilter is missing. Stop and investigate live RLS before proceeding.', 1;
END;

DECLARE @BasePredicateAllowsNull bit = CONVERT(bit, CASE
    WHEN LOWER(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant', N'IF'))) LIKE N'%is null%'
    THEN 1 ELSE 0 END);

IF @BasePredicateAllowsNull = 0 AND OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL
BEGIN
    EXEC(N'
CREATE FUNCTION sec.fn_FilterByTenantOrGlobal(@ShaleClientId int)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS fn_accessResult
    WHERE @ShaleClientId IS NULL
       OR @ShaleClientId = TRY_CONVERT(int, SESSION_CONTEXT(N''''ShaleClientId''''));');
END;

DECLARE @Strict TABLE (SchemaName sysname NOT NULL, TableName sysname NOT NULL);
INSERT INTO @Strict (SchemaName, TableName) VALUES
(N'dbo', N'CalendarEvents'),
(N'dbo', N'CaseTimelineEvents'),
(N'dbo', N'CaseUpdates'),
(N'dbo', N'Contacts'),
(N'dbo', N'Facilities'),
(N'dbo', N'Notifications'),
(N'dbo', N'Organizations'),
(N'dbo', N'Providers'),
(N'dbo', N'TaskAssignments'),
(N'dbo', N'Tasks'),
(N'dbo', N'TaskTimelineEvents'),
(N'dbo', N'TaskUpdates'),
(N'dbo', N'UserBoardLanePreferences'),
(N'dbo', N'UserPreferences');

DECLARE @Overlay TABLE (SchemaName sysname NOT NULL, TableName sysname NOT NULL);
INSERT INTO @Overlay (SchemaName, TableName) VALUES
(N'dbo', N'CalendarEventTypes'),
(N'dbo', N'Categories'),
(N'dbo', N'OrganizationTypes'),
(N'dbo', N'PartyRoles'),
(N'dbo', N'PartySides'),
(N'dbo', N'PracticeAreas'),
(N'dbo', N'Priorities'),
(N'dbo', N'Roles'),
(N'dbo', N'Statuses'),
(N'dbo', N'TaskCategories'),
(N'dbo', N'TaskStatuses');

DECLARE @SchemaName sysname,
        @TableName sysname,
        @Qualified nvarchar(517),
        @Sql nvarchar(max),
        @OverlayPredicate sysname = CASE WHEN @BasePredicateAllowsNull = 1 THEN N'fn_FilterByTenant' ELSE N'fn_FilterByTenantOrGlobal' END;

DECLARE strict_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT s.SchemaName, s.TableName
FROM @Strict AS s
JOIN sys.tables AS t
  ON t.name = s.TableName
 AND SCHEMA_NAME(t.schema_id) = s.SchemaName
JOIN sys.columns AS c
  ON c.object_id = t.object_id
 AND c.name = N'ShaleClientId'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.security_predicates AS p
    JOIN sys.security_policies AS sp
      ON sp.object_id = p.object_id
    WHERE p.target_object_id = t.object_id
      AND p.predicate_type_desc = N'FILTER'
      AND sp.name = N'TenantFilter'
);

OPEN strict_cursor;
FETCH NEXT FROM strict_cursor INTO @SchemaName, @TableName;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Qualified = QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName);
    SET @Sql = N'ALTER SECURITY POLICY TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON ' + @Qualified + N';';
    PRINT @Sql;
    EXEC sys.sp_executesql @Sql;
    FETCH NEXT FROM strict_cursor INTO @SchemaName, @TableName;
END;
CLOSE strict_cursor;
DEALLOCATE strict_cursor;

DECLARE overlay_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT o.SchemaName, o.TableName
FROM @Overlay AS o
JOIN sys.tables AS t
  ON t.name = o.TableName
 AND SCHEMA_NAME(t.schema_id) = o.SchemaName
JOIN sys.columns AS c
  ON c.object_id = t.object_id
 AND c.name = N'ShaleClientId'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.security_predicates AS p
    JOIN sys.security_policies AS sp
      ON sp.object_id = p.object_id
    WHERE p.target_object_id = t.object_id
      AND p.predicate_type_desc = N'FILTER'
      AND sp.name = N'TenantFilter'
);

OPEN overlay_cursor;
FETCH NEXT FROM overlay_cursor INTO @SchemaName, @TableName;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Qualified = QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName);
    SET @Sql = N'ALTER SECURITY POLICY TenantFilter ADD FILTER PREDICATE sec.' + @OverlayPredicate + N'(ShaleClientId) ON ' + @Qualified + N';';
    PRINT @Sql;
    EXEC sys.sp_executesql @Sql;
    FETCH NEXT FROM overlay_cursor INTO @SchemaName, @TableName;
END;
CLOSE overlay_cursor;
DEALLOCATE overlay_cursor;
GO

/* ============================================================================
   READ-ONLY AFTER VERIFICATION
   ============================================================================ */

SELECT
    PolicyName = sp.name,
    PolicyEnabled = sp.is_enabled,
    TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    PredicateType = p.predicate_type_desc,
    Operation = p.operation_desc,
    PredicateDefinition = p.predicate_definition
FROM sys.security_predicates AS p
JOIN sys.security_policies AS sp
  ON sp.object_id = p.object_id
WHERE sp.name = N'TenantFilter'
ORDER BY TargetTable, PredicateType, Operation;

/* Tenant isolation smoke checks. Replace column names only if live schema differs. */
DECLARE @TenantA int = 7, @TenantB int = 8;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = @TenantA;
SELECT N'Tenant 7 visible PracticeAreas by ShaleClientId' AS CheckName, ShaleClientId, COUNT(*) AS RowCount
FROM dbo.PracticeAreas
GROUP BY ShaleClientId
ORDER BY ShaleClientId;
SELECT TOP (50) N'Tenant 7 should not see tenant 8 custom PracticeAreas' AS CheckName, Id, ShaleClientId, Name
FROM dbo.PracticeAreas
WHERE ShaleClientId = @TenantB
ORDER BY Id;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = @TenantB;
SELECT N'Tenant 8 visible PracticeAreas by ShaleClientId' AS CheckName, ShaleClientId, COUNT(*) AS RowCount
FROM dbo.PracticeAreas
GROUP BY ShaleClientId
ORDER BY ShaleClientId;
SELECT TOP (50) N'Tenant 8 should not see tenant 7 custom PracticeAreas' AS CheckName, Id, ShaleClientId, Name
FROM dbo.PracticeAreas
WHERE ShaleClientId = @TenantA
ORDER BY Id;
SELECT TOP (50) N'Global/default PracticeAreas should remain visible' AS CheckName, Id, ShaleClientId, Name
FROM dbo.PracticeAreas
WHERE ShaleClientId IS NULL
ORDER BY Id;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
GO

/* ============================================================================
   ROLLBACK SQL: remove only predicates added by this proposal
   ============================================================================

-- Review first, then execute if rollback is required.

DECLARE @RollbackTables TABLE (SchemaName sysname NOT NULL, TableName sysname NOT NULL);
INSERT INTO @RollbackTables (SchemaName, TableName) VALUES
(N'dbo', N'CalendarEvents'),
(N'dbo', N'CaseTimelineEvents'),
(N'dbo', N'CaseUpdates'),
(N'dbo', N'Contacts'),
(N'dbo', N'Facilities'),
(N'dbo', N'Notifications'),
(N'dbo', N'Organizations'),
(N'dbo', N'Providers'),
(N'dbo', N'TaskAssignments'),
(N'dbo', N'Tasks'),
(N'dbo', N'TaskTimelineEvents'),
(N'dbo', N'TaskUpdates'),
(N'dbo', N'UserBoardLanePreferences'),
(N'dbo', N'UserPreferences'),
(N'dbo', N'CalendarEventTypes'),
(N'dbo', N'Categories'),
(N'dbo', N'OrganizationTypes'),
(N'dbo', N'PartyRoles'),
(N'dbo', N'PartySides'),
(N'dbo', N'PracticeAreas'),
(N'dbo', N'Priorities'),
(N'dbo', N'Roles'),
(N'dbo', N'Statuses'),
(N'dbo', N'TaskCategories'),
(N'dbo', N'TaskStatuses');

DECLARE @SchemaName sysname, @TableName sysname, @Fn nvarchar(517), @Qualified nvarchar(517), @Sql nvarchar(max);
DECLARE rollback_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT rt.SchemaName,
       rt.TableName,
       p.predicate_definition AS PredicateDefinition
FROM @RollbackTables AS rt
JOIN sys.tables AS t
  ON t.name = rt.TableName
 AND SCHEMA_NAME(t.schema_id) = rt.SchemaName
JOIN sys.security_predicates AS p
  ON p.target_object_id = t.object_id
JOIN sys.security_policies AS sp
  ON sp.object_id = p.object_id
WHERE sp.name = N'TenantFilter'
  AND p.predicate_type_desc = N'FILTER'
  AND (p.predicate_definition LIKE N'%sec.fn_FilterByTenant(%'
       OR p.predicate_definition LIKE N'%[sec].[fn_FilterByTenant](%'
       OR p.predicate_definition LIKE N'%sec.fn_FilterByTenantOrGlobal(%'
       OR p.predicate_definition LIKE N'%[sec].[fn_FilterByTenantOrGlobal](%');

OPEN rollback_cursor;
FETCH NEXT FROM rollback_cursor INTO @SchemaName, @TableName, @Fn;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Qualified = QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName);
    SET @Sql = N'ALTER SECURITY POLICY TenantFilter DROP FILTER PREDICATE ON ' + @Qualified + N';';
    PRINT @Sql + N' -- predicate was ' + @Fn;
    EXEC sys.sp_executesql @Sql;
    FETCH NEXT FROM rollback_cursor INTO @SchemaName, @TableName, @Fn;
END;
CLOSE rollback_cursor;
DEALLOCATE rollback_cursor;

IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.security_predicates
       WHERE predicate_definition LIKE N'%sec.fn_FilterByTenantOrGlobal(%'
          OR predicate_definition LIKE N'%[sec].[fn_FilterByTenantOrGlobal](%'
   )
BEGIN
    DROP FUNCTION sec.fn_FilterByTenantOrGlobal;
END;
*/

/*
  Modularization integrity hardening (phase 1): unique keyed-scope guardrails.

  Goal:
  - Prevent duplicate keyed rows in the same scope for modularized tables.

  Enforced rule:
  - UNIQUE on (ShaleClientId, SystemKey) for rows where SystemKey IS NOT NULL.

  Overlay semantics preserved:
  - same SystemKey can exist once for tenant N (ShaleClientId = N)
  - and once globally (ShaleClientId IS NULL)

  Safety:
  - read prechecks run first; script throws if duplicate keyed rows already exist.
  - no data rewrite, no FK/history rewrite.
*/

SET NOCOUNT ON;

PRINT '=== Precheck duplicate keyed rows (fail-fast) ===';

IF OBJECT_ID('dbo.Statuses', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.Statuses', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.Statuses', 'ShaleClientId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM dbo.Statuses
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
   )
BEGIN
    THROW 52001, 'Cannot create unique keyed index on dbo.Statuses: duplicate (ShaleClientId, SystemKey) rows exist.', 1;
END;

IF OBJECT_ID('dbo.PartyRoles', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PartyRoles', 'ShaleClientId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM dbo.PartyRoles
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
   )
BEGIN
    THROW 52002, 'Cannot create unique keyed index on dbo.PartyRoles: duplicate (ShaleClientId, SystemKey) rows exist.', 1;
END;

IF OBJECT_ID('dbo.PartySides', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PartySides', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PartySides', 'ShaleClientId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM dbo.PartySides
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
   )
BEGIN
    THROW 52003, 'Cannot create unique keyed index on dbo.PartySides: duplicate (ShaleClientId, SystemKey) rows exist.', 1;
END;

IF OBJECT_ID('dbo.Priorities', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.Priorities', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.Priorities', 'ShaleClientId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM dbo.Priorities
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
   )
BEGIN
    THROW 52004, 'Cannot create unique keyed index on dbo.Priorities: duplicate (ShaleClientId, SystemKey) rows exist.', 1;
END;

IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PracticeAreas', 'ShaleClientId') IS NOT NULL
   AND EXISTS (
        SELECT 1
        FROM dbo.PracticeAreas
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
   )
BEGIN
    THROW 52005, 'Cannot create unique keyed index on dbo.PracticeAreas: duplicate (ShaleClientId, SystemKey) rows exist.', 1;
END;

PRINT '=== Create filtered unique indexes where eligible ===';

IF OBJECT_ID('dbo.Statuses', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.Statuses', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.Statuses', 'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.Statuses')
          AND name = 'UX_Statuses_ShaleClientId_SystemKey_NonNull'
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_Statuses_ShaleClientId_SystemKey_NonNull
        ON dbo.Statuses (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

IF OBJECT_ID('dbo.PartyRoles', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PartyRoles', 'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.PartyRoles')
          AND name = 'UX_PartyRoles_ShaleClientId_SystemKey_NonNull'
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_PartyRoles_ShaleClientId_SystemKey_NonNull
        ON dbo.PartyRoles (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

IF OBJECT_ID('dbo.PartySides', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PartySides', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PartySides', 'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.PartySides')
          AND name = 'UX_PartySides_ShaleClientId_SystemKey_NonNull'
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_PartySides_ShaleClientId_SystemKey_NonNull
        ON dbo.PartySides (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

IF OBJECT_ID('dbo.Priorities', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.Priorities', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.Priorities', 'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.Priorities')
          AND name = 'UX_Priorities_ShaleClientId_SystemKey_NonNull'
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_Priorities_ShaleClientId_SystemKey_NonNull
        ON dbo.Priorities (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NOT NULL
   AND COL_LENGTH('dbo.PracticeAreas', 'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.PracticeAreas')
          AND name = 'UX_PracticeAreas_ShaleClientId_SystemKey_NonNull'
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_PracticeAreas_ShaleClientId_SystemKey_NonNull
        ON dbo.PracticeAreas (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

PRINT '=== Verification: created indexes and key properties ===';
SELECT
    OBJECT_NAME(i.object_id) AS TableName,
    i.name AS IndexName,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM sys.indexes i
WHERE i.name IN (
    'UX_Statuses_ShaleClientId_SystemKey_NonNull',
    'UX_PartyRoles_ShaleClientId_SystemKey_NonNull',
    'UX_PartySides_ShaleClientId_SystemKey_NonNull',
    'UX_Priorities_ShaleClientId_SystemKey_NonNull',
    'UX_PracticeAreas_ShaleClientId_SystemKey_NonNull'
)
ORDER BY TableName, IndexName;

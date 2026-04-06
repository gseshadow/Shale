/*
  Modularization gating/verification checks (read-only).

  Covers:
  - Statuses
  - PartyRoles
  - PartySides
  - Priorities
  - PracticeAreas

  This script performs diagnostics only and makes no data/schema changes.
*/

SET NOCOUNT ON;

DECLARE @tenantId int = 7;

PRINT '=== 1) Table + key-column + nullability matrix ===';
;WITH targets AS (
    SELECT 'Statuses' AS TableName UNION ALL
    SELECT 'PartyRoles' UNION ALL
    SELECT 'PartySides' UNION ALL
    SELECT 'Priorities' UNION ALL
    SELECT 'PracticeAreas'
)
SELECT
    t.TableName,
    CASE WHEN OBJECT_ID('dbo.' + t.TableName, 'U') IS NOT NULL THEN 1 ELSE 0 END AS TableExists,
    CASE WHEN COL_LENGTH('dbo.' + t.TableName, 'SystemKey') IS NOT NULL THEN 1 ELSE 0 END AS HasSystemKey,
    c.is_nullable AS ShaleClientIdIsNullable
FROM targets t
LEFT JOIN sys.columns c
  ON c.object_id = OBJECT_ID('dbo.' + t.TableName)
 AND c.name = 'ShaleClientId'
ORDER BY t.TableName;

PRINT '=== 2) Statuses: lifecycle/system key readiness ===';
IF OBJECT_ID('dbo.Statuses', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN LifecycleKey IS NOT NULL THEN 1 ELSE 0 END) AS WithLifecycleKey,
        SUM(CASE WHEN SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.Statuses;

    SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
    FROM dbo.Statuses
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
    ORDER BY ShaleClientId, SystemKey;

    SELECT Id, ShaleClientId, Name, LifecycleKey, SystemKey, SortOrder
    FROM dbo.Statuses
    WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
      AND (SystemKey IN ('intake', 'accepted', 'denied', 'closed')
           OR LifecycleKey IN ('accepted', 'denied', 'closed'))
    ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;
END;

PRINT '=== 3) PartyRoles: built-ins + duplicates ===';
IF OBJECT_ID('dbo.PartyRoles', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.PartyRoles;

    SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
    FROM dbo.PartyRoles
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
    ORDER BY ShaleClientId, SystemKey;

    SELECT Id, ShaleClientId, Name, SystemKey
    FROM dbo.PartyRoles
    WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
      AND SystemKey IN ('caller', 'party', 'counsel')
    ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;
END;

PRINT '=== 4) PartySides: built-ins + duplicates + CaseParties.Side normalization ===';
IF OBJECT_ID('dbo.PartySides', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.PartySides;

    SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
    FROM dbo.PartySides
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
    ORDER BY ShaleClientId, SystemKey;

    SELECT Id, ShaleClientId, Name, SystemKey
    FROM dbo.PartySides
    WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
      AND SystemKey IN ('represented', 'opposing', 'neutral')
    ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;
END;

IF OBJECT_ID('dbo.CaseParties', 'U') IS NOT NULL
BEGIN
    SELECT Side, COUNT(*) AS Cnt
    FROM dbo.CaseParties
    GROUP BY Side
    ORDER BY Cnt DESC, Side;
END;

PRINT '=== 5) Priorities: normal key + duplicates ===';
IF OBJECT_ID('dbo.Priorities', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.Priorities;

    SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
    FROM dbo.Priorities
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
    ORDER BY ShaleClientId, SystemKey;

    SELECT Id, ShaleClientId, Name, SortOrder, IsActive, SystemKey
    FROM dbo.Priorities
    WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
      AND (SystemKey = 'normal' OR LOWER(LTRIM(RTRIM(COALESCE(Name, '')))) IN ('normal', 'medium', 'default', 'standard'))
    ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, Id;
END;

PRINT '=== 6) PracticeAreas: explicit built-ins + duplicates ===';
IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.PracticeAreas;

    SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
    FROM dbo.PracticeAreas
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
    ORDER BY ShaleClientId, SystemKey;

    SELECT Id, ShaleClientId, Name, IsActive, IsDeleted, SystemKey
    FROM dbo.PracticeAreas
    WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
      AND (
            SystemKey IN ('medical_malpractice', 'personal_injury', 'sexual_assault')
            OR LTRIM(RTRIM(COALESCE(Name, ''))) IN ('Medical Malpractice', 'Personal Injury', 'Sexual Assault')
          )
    ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, Name, Id;
END;

PRINT '=== 7) Global overlay readiness summary (nullable + global row counts) ===';
DECLARE @summary TABLE (
    TableName sysname NOT NULL,
    ShaleClientIdIsNullable bit NULL,
    GlobalRowCount bigint NULL
);

INSERT INTO @summary (TableName, ShaleClientIdIsNullable, GlobalRowCount)
SELECT 'Statuses',
       (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Statuses') AND name = 'ShaleClientId'),
       NULL;
IF OBJECT_ID('dbo.Statuses', 'U') IS NOT NULL
    UPDATE @summary SET GlobalRowCount = (SELECT COUNT(*) FROM dbo.Statuses WHERE ShaleClientId IS NULL) WHERE TableName = 'Statuses';

INSERT INTO @summary (TableName, ShaleClientIdIsNullable, GlobalRowCount)
SELECT 'PartyRoles',
       (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PartyRoles') AND name = 'ShaleClientId'),
       NULL;
IF OBJECT_ID('dbo.PartyRoles', 'U') IS NOT NULL
    UPDATE @summary SET GlobalRowCount = (SELECT COUNT(*) FROM dbo.PartyRoles WHERE ShaleClientId IS NULL) WHERE TableName = 'PartyRoles';

INSERT INTO @summary (TableName, ShaleClientIdIsNullable, GlobalRowCount)
SELECT 'PartySides',
       (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PartySides') AND name = 'ShaleClientId'),
       NULL;
IF OBJECT_ID('dbo.PartySides', 'U') IS NOT NULL
    UPDATE @summary SET GlobalRowCount = (SELECT COUNT(*) FROM dbo.PartySides WHERE ShaleClientId IS NULL) WHERE TableName = 'PartySides';

INSERT INTO @summary (TableName, ShaleClientIdIsNullable, GlobalRowCount)
SELECT 'Priorities',
       (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Priorities') AND name = 'ShaleClientId'),
       NULL;
IF OBJECT_ID('dbo.Priorities', 'U') IS NOT NULL
    UPDATE @summary SET GlobalRowCount = (SELECT COUNT(*) FROM dbo.Priorities WHERE ShaleClientId IS NULL) WHERE TableName = 'Priorities';

INSERT INTO @summary (TableName, ShaleClientIdIsNullable, GlobalRowCount)
SELECT 'PracticeAreas',
       (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PracticeAreas') AND name = 'ShaleClientId'),
       NULL;
IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NOT NULL
    UPDATE @summary SET GlobalRowCount = (SELECT COUNT(*) FROM dbo.PracticeAreas WHERE ShaleClientId IS NULL) WHERE TableName = 'PracticeAreas';

SELECT TableName, ShaleClientIdIsNullable, GlobalRowCount
FROM @summary
ORDER BY TableName;

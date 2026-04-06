/*
  Modularization diagnostics + readiness checks (read-only)

  Covers:
  - Statuses
  - PartyRoles
  - PartySides
  - Priorities
  - PracticeAreas

  Operator notes:
  - This script performs diagnostics only (no DDL/DML writes).
  - Some findings are expected in prep phases (for example missing global rows when
    ShaleClientId is still NOT NULL for a table).
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
    c.is_nullable AS ShaleClientIdIsNullable,
    CASE WHEN OBJECT_ID('dbo.' + t.TableName, 'U') IS NULL THEN 'MISSING_TABLE'
         WHEN COL_LENGTH('dbo.' + t.TableName, 'SystemKey') IS NULL THEN 'MISSING_SYSTEMKEY_COLUMN'
         ELSE 'OK_OR_PREP_PHASE'
    END AS DiagnosticState
FROM targets t
LEFT JOIN sys.columns c
  ON c.object_id = OBJECT_ID('dbo.' + t.TableName)
 AND c.name = 'ShaleClientId'
ORDER BY t.TableName;

PRINT '=== 2) Statuses diagnostics ===';
IF OBJECT_ID('dbo.Statuses', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN COL_LENGTH('dbo.Statuses', 'LifecycleKey') IS NOT NULL AND LifecycleKey IS NOT NULL THEN 1 ELSE 0 END) AS WithLifecycleKey,
        SUM(CASE WHEN COL_LENGTH('dbo.Statuses', 'SystemKey') IS NOT NULL AND SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.Statuses;

    IF COL_LENGTH('dbo.Statuses', 'SystemKey') IS NOT NULL
    BEGIN
        SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
        FROM dbo.Statuses
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
        ORDER BY ShaleClientId, SystemKey;
    END
    ELSE
        SELECT 'Statuses.SystemKey missing: duplicate-key check skipped.' AS Note;

    DECLARE @statusesDetail nvarchar(max) = N'
        SELECT Id, ShaleClientId, Name, '
        + CASE WHEN COL_LENGTH('dbo.Statuses', 'LifecycleKey') IS NOT NULL THEN N'LifecycleKey' ELSE N'CAST(NULL AS nvarchar(32)) AS LifecycleKey' END
        + N', '
        + CASE WHEN COL_LENGTH('dbo.Statuses', 'SystemKey') IS NOT NULL THEN N'SystemKey' ELSE N'CAST(NULL AS nvarchar(64)) AS SystemKey' END
        + N', '
        + CASE WHEN COL_LENGTH('dbo.Statuses', 'SortOrder') IS NOT NULL THEN N'SortOrder' ELSE N'CAST(NULL AS int) AS SortOrder' END
        + N'
          FROM dbo.Statuses
          WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
          ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, Id;';
    EXEC sp_executesql @statusesDetail, N'@tenantId int', @tenantId = @tenantId;
END;

PRINT '=== 3) PartyRoles diagnostics ===';
IF OBJECT_ID('dbo.PartyRoles', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NOT NULL AND SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.PartyRoles;

    IF COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NOT NULL
    BEGIN
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

        SELECT
            SUM(CASE WHEN ShaleClientId = @tenantId AND SystemKey IN ('caller', 'party', 'counsel') THEN 1 ELSE 0 END) AS Tenant7BuiltinCount,
            SUM(CASE WHEN ShaleClientId IS NULL AND SystemKey IN ('caller', 'party', 'counsel') THEN 1 ELSE 0 END) AS GlobalBuiltinCount
        FROM dbo.PartyRoles;
    END
    ELSE
        SELECT 'PartyRoles.SystemKey missing: duplicate and built-in checks skipped.' AS Note;
END;

PRINT '=== 4) PartySides diagnostics ===';
IF OBJECT_ID('dbo.PartySides', 'U') IS NOT NULL
BEGIN
    SELECT
        COUNT(*) AS TotalRows,
        SUM(CASE WHEN COL_LENGTH('dbo.PartySides', 'SystemKey') IS NOT NULL AND SystemKey IS NOT NULL THEN 1 ELSE 0 END) AS WithSystemKey,
        SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
    FROM dbo.PartySides;

    IF COL_LENGTH('dbo.PartySides', 'SystemKey') IS NOT NULL
    BEGIN
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

        SELECT
            SUM(CASE WHEN ShaleClientId = @tenantId AND SystemKey IN ('represented', 'opposing', 'neutral') THEN 1 ELSE 0 END) AS Tenant7BuiltinCount,
            SUM(CASE WHEN ShaleClientId IS NULL AND SystemKey IN ('represented', 'opposing', 'neutral') THEN 1 ELSE 0 END) AS GlobalBuiltinCount
        FROM dbo.PartySides;
    END
    ELSE
        SELECT 'PartySides.SystemKey missing: duplicate and built-in checks skipped.' AS Note;
END
ELSE
    SELECT 'PartySides table missing: checks skipped.' AS Note;

IF OBJECT_ID('dbo.CaseParties', 'U') IS NOT NULL AND COL_LENGTH('dbo.CaseParties', 'Side') IS NOT NULL
BEGIN
    SELECT Side, COUNT(*) AS Cnt
    FROM dbo.CaseParties
    GROUP BY Side
    ORDER BY Cnt DESC, Side;
END;

PRINT '=== 5) Priorities diagnostics (schema-drift tolerant) ===';
IF OBJECT_ID('dbo.Priorities', 'U') IS NOT NULL
BEGIN
    DECLARE @prioritiesSummary nvarchar(max) = N'
        SELECT
            COUNT(*) AS TotalRows,
            SUM(CASE WHEN '
            + CASE WHEN COL_LENGTH('dbo.Priorities', 'SystemKey') IS NOT NULL THEN N'SystemKey IS NOT NULL' ELSE N'1=0' END
            + N' THEN 1 ELSE 0 END) AS WithSystemKey,
            SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
        FROM dbo.Priorities;';
    EXEC sp_executesql @prioritiesSummary;

    IF COL_LENGTH('dbo.Priorities', 'SystemKey') IS NOT NULL
    BEGIN
        SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
        FROM dbo.Priorities
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
        ORDER BY ShaleClientId, SystemKey;
    END
    ELSE
        SELECT 'Priorities.SystemKey missing: duplicate-key check skipped.' AS Note;

    DECLARE @prioritiesDetail nvarchar(max) = N'
        SELECT Id, ShaleClientId, '
        + CASE WHEN COL_LENGTH('dbo.Priorities', 'Name') IS NOT NULL THEN N'Name' ELSE N'CAST(NULL AS nvarchar(128)) AS Name' END
        + N', '
        + CASE WHEN COL_LENGTH('dbo.Priorities', 'SortOrder') IS NOT NULL THEN N'SortOrder' ELSE N'CAST(NULL AS int) AS SortOrder' END
        + N', '
        + CASE WHEN COL_LENGTH('dbo.Priorities', 'IsActive') IS NOT NULL THEN N'IsActive' ELSE N'CAST(NULL AS bit) AS IsActive' END
        + N', '
        + CASE WHEN COL_LENGTH('dbo.Priorities', 'SystemKey') IS NOT NULL THEN N'SystemKey' ELSE N'CAST(NULL AS nvarchar(64)) AS SystemKey' END
        + N'
          FROM dbo.Priorities
          WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
          ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, Id;';
    EXEC sp_executesql @prioritiesDetail, N'@tenantId int', @tenantId = @tenantId;
END;

PRINT '=== 6) PracticeAreas diagnostics ===';
IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NOT NULL
BEGIN
    DECLARE @practiceSummary nvarchar(max) = N'
        SELECT
            COUNT(*) AS TotalRows,
            SUM(CASE WHEN '
            + CASE WHEN COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NOT NULL THEN N'SystemKey IS NOT NULL' ELSE N'1=0' END
            + N' THEN 1 ELSE 0 END) AS WithSystemKey,
            SUM(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END) AS GlobalRows
        FROM dbo.PracticeAreas;';
    EXEC sp_executesql @practiceSummary;

    IF COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NOT NULL
    BEGIN
        SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
        FROM dbo.PracticeAreas
        WHERE SystemKey IS NOT NULL
        GROUP BY ShaleClientId, SystemKey
        HAVING COUNT(*) > 1
        ORDER BY ShaleClientId, SystemKey;

        SELECT Id, ShaleClientId, Name,
               CASE WHEN COL_LENGTH('dbo.PracticeAreas', 'IsActive') IS NOT NULL THEN IsActive ELSE NULL END AS IsActive,
               CASE WHEN COL_LENGTH('dbo.PracticeAreas', 'IsDeleted') IS NOT NULL THEN IsDeleted ELSE NULL END AS IsDeleted,
               SystemKey
        FROM dbo.PracticeAreas
        WHERE (ShaleClientId = @tenantId OR ShaleClientId IS NULL)
          AND (
                SystemKey IN ('medical_malpractice', 'personal_injury', 'sexual_assault')
                OR LTRIM(RTRIM(COALESCE(Name, ''))) IN ('Medical Malpractice', 'Personal Injury', 'Sexual Assault')
              )
        ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, Name, Id;
    END
    ELSE
        SELECT 'PracticeAreas.SystemKey missing: duplicate and built-in checks skipped.' AS Note;
END;

PRINT '=== 7) Readiness summary per table ===';
DECLARE @readiness TABLE (
    TableName sysname NOT NULL,
    TableExists bit NOT NULL,
    HasSystemKey bit NOT NULL,
    ShaleClientIdIsNullable bit NULL,
    DuplicateKeyRows bigint NULL,
    Tenant7BuiltinRows bigint NULL,
    GlobalBuiltinRows bigint NULL,
    ReadinessHint nvarchar(200) NOT NULL
);

INSERT INTO @readiness (TableName, TableExists, HasSystemKey, ShaleClientIdIsNullable, DuplicateKeyRows, Tenant7BuiltinRows, GlobalBuiltinRows, ReadinessHint)
SELECT
    'Statuses',
    CASE WHEN OBJECT_ID('dbo.Statuses', 'U') IS NULL THEN 0 ELSE 1 END,
    CASE WHEN COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL THEN 0 ELSE 1 END,
    (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Statuses') AND name = 'ShaleClientId'),
    CASE WHEN OBJECT_ID('dbo.Statuses', 'U') IS NULL OR COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM (
            SELECT ShaleClientId, SystemKey
            FROM dbo.Statuses
            WHERE SystemKey IS NOT NULL
            GROUP BY ShaleClientId, SystemKey
            HAVING COUNT(*) > 1
        ) d
    ) END,
    CASE WHEN OBJECT_ID('dbo.Statuses', 'U') IS NULL OR COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.Statuses WHERE ShaleClientId = @tenantId AND SystemKey IN ('intake','accepted','denied','closed')
    ) END,
    CASE WHEN OBJECT_ID('dbo.Statuses', 'U') IS NULL OR COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.Statuses WHERE ShaleClientId IS NULL AND SystemKey IN ('intake','accepted','denied','closed')
    ) END,
    N'Prep OK when HasSystemKey=1; activation expects nullable + global built-ins present'
UNION ALL
SELECT
    'PartyRoles',
    CASE WHEN OBJECT_ID('dbo.PartyRoles', 'U') IS NULL THEN 0 ELSE 1 END,
    CASE WHEN COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NULL THEN 0 ELSE 1 END,
    (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PartyRoles') AND name = 'ShaleClientId'),
    CASE WHEN OBJECT_ID('dbo.PartyRoles', 'U') IS NULL OR COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM (
            SELECT ShaleClientId, SystemKey
            FROM dbo.PartyRoles
            WHERE SystemKey IS NOT NULL
            GROUP BY ShaleClientId, SystemKey
            HAVING COUNT(*) > 1
        ) d
    ) END,
    CASE WHEN OBJECT_ID('dbo.PartyRoles', 'U') IS NULL OR COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PartyRoles WHERE ShaleClientId = @tenantId AND SystemKey IN ('caller','party','counsel')
    ) END,
    CASE WHEN OBJECT_ID('dbo.PartyRoles', 'U') IS NULL OR COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PartyRoles WHERE ShaleClientId IS NULL AND SystemKey IN ('caller','party','counsel')
    ) END,
    N'Global built-ins may be 0 when ShaleClientId is NOT NULL (expected pre-activation)'
UNION ALL
SELECT
    'PartySides',
    CASE WHEN OBJECT_ID('dbo.PartySides', 'U') IS NULL THEN 0 ELSE 1 END,
    CASE WHEN COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL THEN 0 ELSE 1 END,
    (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PartySides') AND name = 'ShaleClientId'),
    CASE WHEN OBJECT_ID('dbo.PartySides', 'U') IS NULL OR COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM (
            SELECT ShaleClientId, SystemKey
            FROM dbo.PartySides
            WHERE SystemKey IS NOT NULL
            GROUP BY ShaleClientId, SystemKey
            HAVING COUNT(*) > 1
        ) d
    ) END,
    CASE WHEN OBJECT_ID('dbo.PartySides', 'U') IS NULL OR COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PartySides WHERE ShaleClientId = @tenantId AND SystemKey IN ('represented','opposing','neutral')
    ) END,
    CASE WHEN OBJECT_ID('dbo.PartySides', 'U') IS NULL OR COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PartySides WHERE ShaleClientId IS NULL AND SystemKey IN ('represented','opposing','neutral')
    ) END,
    N'Tenant 7 built-ins should exist after phase1; global depends on nullable schema'
UNION ALL
SELECT
    'Priorities',
    CASE WHEN OBJECT_ID('dbo.Priorities', 'U') IS NULL THEN 0 ELSE 1 END,
    CASE WHEN COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL THEN 0 ELSE 1 END,
    (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Priorities') AND name = 'ShaleClientId'),
    CASE WHEN OBJECT_ID('dbo.Priorities', 'U') IS NULL OR COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM (
            SELECT ShaleClientId, SystemKey
            FROM dbo.Priorities
            WHERE SystemKey IS NOT NULL
            GROUP BY ShaleClientId, SystemKey
            HAVING COUNT(*) > 1
        ) d
    ) END,
    CASE WHEN OBJECT_ID('dbo.Priorities', 'U') IS NULL OR COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.Priorities WHERE ShaleClientId = @tenantId AND SystemKey = 'normal'
    ) END,
    CASE WHEN OBJECT_ID('dbo.Priorities', 'U') IS NULL OR COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.Priorities WHERE ShaleClientId IS NULL AND SystemKey = 'normal'
    ) END,
    N'normal key is core built-in identity; medium/default names are legacy aliases'
UNION ALL
SELECT
    'PracticeAreas',
    CASE WHEN OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL THEN 0 ELSE 1 END,
    CASE WHEN COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL THEN 0 ELSE 1 END,
    (SELECT is_nullable FROM sys.columns WHERE object_id = OBJECT_ID('dbo.PracticeAreas') AND name = 'ShaleClientId'),
    CASE WHEN OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL OR COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM (
            SELECT ShaleClientId, SystemKey
            FROM dbo.PracticeAreas
            WHERE SystemKey IS NOT NULL
            GROUP BY ShaleClientId, SystemKey
            HAVING COUNT(*) > 1
        ) d
    ) END,
    CASE WHEN OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL OR COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PracticeAreas WHERE ShaleClientId = @tenantId AND SystemKey IN ('medical_malpractice','personal_injury','sexual_assault')
    ) END,
    CASE WHEN OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL OR COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL THEN NULL ELSE (
        SELECT COUNT(*) FROM dbo.PracticeAreas WHERE ShaleClientId IS NULL AND SystemKey IN ('medical_malpractice','personal_injury','sexual_assault')
    ) END,
    N'Tenant 7 built-ins expected now; global built-ins can be deferred by design';

SELECT *
FROM @readiness
ORDER BY TableName;

PRINT '=== 8) Quick interpretation guide ===';
SELECT 'BLOCK_ROLLOUT' AS Severity, 'Missing table for active feature area' AS Rule
UNION ALL SELECT 'BLOCK_ROLLOUT', 'Missing SystemKey column after running intended prep migration(s)'
UNION ALL SELECT 'BLOCK_POST_ROLLOUT_ACTIVATION', 'Duplicate (ShaleClientId,SystemKey) rows for the same table/scope'
UNION ALL SELECT 'BLOCK_POST_ROLLOUT_ACTIVATION', 'ShaleClientId still NOT NULL for a table where global overlay is being activated'
UNION ALL SELECT 'EXPECTED_TODAY', 'Global built-in rows absent when nullability not yet enabled or seeding intentionally deferred'
UNION ALL SELECT 'EXPECTED_TODAY', 'Tenant and global rows can coexist by SystemKey (overlay pattern)';

PRINT '=== 9) Optional integrity index presence (post-hardening) ===';
SELECT
    t.TableName,
    i.name AS ExpectedIndexName,
    CASE WHEN i.index_id IS NULL THEN 0 ELSE 1 END AS IndexPresent,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM (
    SELECT 'Statuses' AS TableName, 'UX_Statuses_ShaleClientId_SystemKey_NonNull' AS IndexName UNION ALL
    SELECT 'PartyRoles', 'UX_PartyRoles_ShaleClientId_SystemKey_NonNull' UNION ALL
    SELECT 'PartySides', 'UX_PartySides_ShaleClientId_SystemKey_NonNull' UNION ALL
    SELECT 'Priorities', 'UX_Priorities_ShaleClientId_SystemKey_NonNull' UNION ALL
    SELECT 'PracticeAreas', 'UX_PracticeAreas_ShaleClientId_SystemKey_NonNull'
) t
LEFT JOIN sys.indexes i
  ON i.object_id = OBJECT_ID('dbo.' + t.TableName)
 AND i.name = t.IndexName
ORDER BY t.TableName;

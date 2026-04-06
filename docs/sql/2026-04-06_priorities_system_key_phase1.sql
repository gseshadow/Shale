/*
  Priorities modularization pass 1 (conservative).

  Goals:
  - Ensure dbo.Priorities.SystemKey exists.
  - Normalize and backfill clear built-in identity rows.
  - Optionally seed global/default priority rows only when ShaleClientId is nullable.
  - Keep tenant rows active and avoid rewriting task history/FKs.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.Priorities', 'U') IS NULL
BEGIN
    THROW 51200, 'dbo.Priorities does not exist.', 1;
END;

IF COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.Priorities
    ADD SystemKey nvarchar(64) NULL;
END;

-- Normalize existing key formatting.
UPDATE p
SET SystemKey = LOWER(LTRIM(RTRIM(p.SystemKey)))
FROM dbo.Priorities p
WHERE p.SystemKey IS NOT NULL
  AND p.SystemKey <> LOWER(LTRIM(RTRIM(p.SystemKey)));

/*
  Backfill known default-priority identity.
  Legacy names 'normal', 'medium', 'default', and 'standard' are all treated
  as the same built-in semantic identity: SystemKey='normal'.
*/
UPDATE p
SET SystemKey = 'normal'
FROM dbo.Priorities p
WHERE p.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(p.Name, '')))) IN ('normal', 'medium', 'default', 'standard');

/*
  Seed global/default built-ins only when Priorities.ShaleClientId allows NULL.
  If NOT NULL, seeding is intentionally deferred.
*/
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Priorities')
      AND name = 'ShaleClientId'
      AND is_nullable = 1
)
BEGIN
    INSERT INTO dbo.Priorities (ShaleClientId, Name, SortOrder, IsActive, SystemKey)
    SELECT
        NULL AS ShaleClientId,
        COALESCE(src.Name, N'Normal') AS Name,
        COALESCE(src.SortOrder, 100) AS SortOrder,
        COALESCE(src.IsActive, 1) AS IsActive,
        N'normal' AS SystemKey
    FROM (
        SELECT TOP (1) p.Name, p.SortOrder, p.IsActive
        FROM dbo.Priorities p
        WHERE p.SystemKey = 'normal'
          AND p.ShaleClientId = 7
        ORDER BY p.Id
    ) src
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.Priorities existing
        WHERE existing.ShaleClientId IS NULL
          AND existing.SystemKey = 'normal'
    );
END;

-- Suggested diagnostics.
-- SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
-- FROM dbo.Priorities
-- WHERE SystemKey IS NOT NULL
-- GROUP BY ShaleClientId, SystemKey
-- HAVING COUNT(*) > 1;

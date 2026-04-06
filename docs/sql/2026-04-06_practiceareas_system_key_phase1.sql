/*
  PracticeAreas modularization pass 1 (conservative).

  Goals:
  - Ensure dbo.PracticeAreas.SystemKey exists.
  - Normalize existing SystemKey values.
  - Backfill only unambiguous built-in identity rows.
  - Seed global/default rows only if PracticeAreas.ShaleClientId is nullable.
  - Keep tenant rows active and avoid rewriting case history/FKs.

  Note:
  Built-in practice-area identities are currently ambiguous in many datasets,
  so this pass intentionally avoids aggressive name-based mapping.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL
BEGIN
    THROW 51300, 'dbo.PracticeAreas does not exist.', 1;
END;

IF COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.PracticeAreas
    ADD SystemKey nvarchar(64) NULL;
END;

-- Normalize existing key formatting.
UPDATE pa
SET SystemKey = LOWER(LTRIM(RTRIM(pa.SystemKey)))
FROM dbo.PracticeAreas pa
WHERE pa.SystemKey IS NOT NULL
  AND pa.SystemKey <> LOWER(LTRIM(RTRIM(pa.SystemKey)));

/*
  Conservative backfill:
  only rows whose Name is already a key-like token (lowercase letters/digits/underscore/hyphen)
  are backfilled directly, because they are explicit enough to be considered stable identity.
*/
UPDATE pa
SET SystemKey = LOWER(LTRIM(RTRIM(pa.Name)))
FROM dbo.PracticeAreas pa
WHERE pa.SystemKey IS NULL
  AND NULLIF(LTRIM(RTRIM(COALESCE(pa.Name, ''))), '') IS NOT NULL
  AND LOWER(LTRIM(RTRIM(pa.Name))) = LTRIM(RTRIM(pa.Name))
  AND PATINDEX('%[^a-z0-9_-]%', LTRIM(RTRIM(pa.Name))) = 0;

/*
  Seed global/default rows only when ShaleClientId allows NULL.
  We only seed rows that already have an explicit SystemKey in tenant 7.
*/
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.PracticeAreas')
      AND name = 'ShaleClientId'
      AND is_nullable = 1
)
BEGIN
    INSERT INTO dbo.PracticeAreas (
        ShaleClientId,
        Name,
        Color,
        IsActive,
        IsDeleted,
        SystemKey
    )
    SELECT
        NULL AS ShaleClientId,
        src.Name,
        src.Color,
        src.IsActive,
        src.IsDeleted,
        src.SystemKey
    FROM (
        SELECT
            pa.SystemKey,
            MIN(pa.Id) AS SourceId
        FROM dbo.PracticeAreas pa
        WHERE pa.ShaleClientId = 7
          AND pa.SystemKey IS NOT NULL
        GROUP BY pa.SystemKey
    ) keys
    INNER JOIN dbo.PracticeAreas src
      ON src.Id = keys.SourceId
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.PracticeAreas existing
        WHERE existing.ShaleClientId IS NULL
          AND existing.SystemKey = src.SystemKey
    );
END;

-- Suggested diagnostics.
-- SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
-- FROM dbo.PracticeAreas
-- WHERE SystemKey IS NOT NULL
-- GROUP BY ShaleClientId, SystemKey
-- HAVING COUNT(*) > 1;

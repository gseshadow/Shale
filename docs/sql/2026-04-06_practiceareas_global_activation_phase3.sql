/*
  PracticeAreas global/default activation (phase 3).

  Goal:
  - Enable true global PracticeAreas overlay by allowing ShaleClientId IS NULL
    and seeding separate global built-ins.

  Built-ins:
  - medical_malpractice
  - personal_injury
  - sexual_assault

  Safety:
  - does NOT rewrite Cases.PracticeAreaId history
  - does NOT convert tenant rows into global rows
  - keeps tenant 7 rows active as override-capable rows
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PracticeAreas', 'U') IS NULL
BEGIN
    THROW 52300, 'dbo.PracticeAreas does not exist.', 1;
END;

IF COL_LENGTH('dbo.PracticeAreas', 'ShaleClientId') IS NULL
BEGIN
    THROW 52301, 'dbo.PracticeAreas.ShaleClientId does not exist.', 1;
END;

IF COL_LENGTH('dbo.PracticeAreas', 'SystemKey') IS NULL
BEGIN
    THROW 52302, 'dbo.PracticeAreas.SystemKey does not exist. Run prep phases first.', 1;
END;

-- Normalize key formatting before activation checks.
UPDATE pa
SET SystemKey = LOWER(LTRIM(RTRIM(pa.SystemKey)))
FROM dbo.PracticeAreas pa
WHERE pa.SystemKey IS NOT NULL
  AND pa.SystemKey <> LOWER(LTRIM(RTRIM(pa.SystemKey)));

-- Explicit built-in mapping only (no heuristic mapping in this activation pass).
UPDATE pa
SET SystemKey = 'medical_malpractice'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Medical Malpractice'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'medical_malpractice');

UPDATE pa
SET SystemKey = 'personal_injury'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Personal Injury'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'personal_injury');

UPDATE pa
SET SystemKey = 'sexual_assault'
FROM dbo.PracticeAreas pa
WHERE pa.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(pa.Name, ''))) = 'Sexual Assault'
  AND (pa.SystemKey IS NULL OR pa.SystemKey <> 'sexual_assault');

-- Fail fast if keyed duplicates exist in same scope before activation.
IF EXISTS (
    SELECT 1
    FROM dbo.PracticeAreas
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 52303, 'Duplicate keyed PracticeAreas rows exist by (ShaleClientId, SystemKey). Resolve before activation.', 1;
END;

-- Preflight metadata visibility to help operators if alter fails.
SELECT
    c.name AS ColumnName,
    ty.name AS SqlType,
    c.max_length,
    c.precision,
    c.scale,
    c.is_nullable
FROM sys.columns c
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.PracticeAreas')
  AND c.name = 'ShaleClientId';

SELECT
    i.name AS IndexName,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.PracticeAreas')
  AND c.name = 'ShaleClientId'
GROUP BY i.name, i.is_unique, i.has_filter, i.filter_definition
ORDER BY i.name;

-- Make PracticeAreas.ShaleClientId nullable if not already.
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.PracticeAreas')
      AND name = 'ShaleClientId'
      AND is_nullable = 0
)
BEGIN
    DECLARE @typeName sysname;
    DECLARE @maxLength smallint;
    DECLARE @precision tinyint;
    DECLARE @scale tinyint;
    DECLARE @typeDecl nvarchar(128);
    DECLARE @sql nvarchar(max);

    SELECT
        @typeName = ty.name,
        @maxLength = c.max_length,
        @precision = c.precision,
        @scale = c.scale
    FROM sys.columns c
    JOIN sys.types ty ON ty.user_type_id = c.user_type_id
    WHERE c.object_id = OBJECT_ID('dbo.PracticeAreas')
      AND c.name = 'ShaleClientId';

    SET @typeDecl =
        CASE
            WHEN @typeName IN ('varchar', 'char', 'varbinary', 'binary')
                THEN @typeName + '(' + CASE WHEN @maxLength = -1 THEN 'max' ELSE CAST(@maxLength AS varchar(10)) END + ')'
            WHEN @typeName IN ('nvarchar', 'nchar')
                THEN @typeName + '(' + CASE WHEN @maxLength = -1 THEN 'max' ELSE CAST(@maxLength / 2 AS varchar(10)) END + ')'
            WHEN @typeName IN ('decimal', 'numeric')
                THEN @typeName + '(' + CAST(@precision AS varchar(10)) + ',' + CAST(@scale AS varchar(10)) + ')'
            WHEN @typeName IN ('datetime2', 'datetimeoffset', 'time')
                THEN @typeName + '(' + CAST(@scale AS varchar(10)) + ')'
            ELSE @typeName
        END;

    SET @sql = N'ALTER TABLE dbo.PracticeAreas ALTER COLUMN ShaleClientId ' + @typeDecl + N' NULL;';
    EXEC sp_executesql @sql;
END;

-- Seed separate global rows (ShaleClientId IS NULL) for established built-ins.
DECLARE @seed TABLE (
    SystemKey nvarchar(64) NOT NULL
);

INSERT INTO @seed (SystemKey)
VALUES
    ('medical_malpractice'),
    ('personal_injury'),
    ('sexual_assault');

INSERT INTO dbo.PracticeAreas (ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey)
SELECT
    NULL AS ShaleClientId,
    COALESCE(src.Name, defaults.DefaultName) AS Name,
    COALESCE(src.Color, defaults.DefaultColor) AS Color,
    COALESCE(src.IsActive, 1) AS IsActive,
    COALESCE(src.IsDeleted, 0) AS IsDeleted,
    s.SystemKey
FROM @seed s
OUTER APPLY (
    SELECT TOP (1) pa.Name, pa.Color, pa.IsActive, pa.IsDeleted
    FROM dbo.PracticeAreas pa
    WHERE pa.ShaleClientId = 7
      AND pa.SystemKey = s.SystemKey
    ORDER BY pa.Id
) src
OUTER APPLY (
    SELECT
        CASE s.SystemKey
            WHEN 'medical_malpractice' THEN 'Medical Malpractice'
            WHEN 'personal_injury' THEN 'Personal Injury'
            WHEN 'sexual_assault' THEN 'Sexual Assault'
            ELSE s.SystemKey
        END AS DefaultName,
        CAST(NULL AS nvarchar(32)) AS DefaultColor
) defaults
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.PracticeAreas existing
    WHERE existing.ShaleClientId IS NULL
      AND existing.SystemKey = s.SystemKey
);

-- Verification outputs.
SELECT
    c.name AS ColumnName,
    c.is_nullable
FROM sys.columns c
WHERE c.object_id = OBJECT_ID('dbo.PracticeAreas')
  AND c.name = 'ShaleClientId';

SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey
FROM dbo.PracticeAreas
WHERE (ShaleClientId = 7 OR ShaleClientId IS NULL)
  AND SystemKey IN ('medical_malpractice', 'personal_injury', 'sexual_assault')
ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;

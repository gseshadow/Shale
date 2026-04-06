/*
  PartySides global/default activation (phase 2).

  Goal:
  - Enable true global PartySides overlay by allowing ShaleClientId IS NULL
    and seeding separate global built-ins.

  Built-ins:
  - represented
  - opposing
  - neutral

  Safety:
  - does NOT redesign CaseParties.Side storage
  - does NOT convert tenant rows into global rows
  - keeps tenant 7 rows active as override-capable rows
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PartySides', 'U') IS NULL
BEGIN
    THROW 52200, 'dbo.PartySides does not exist.', 1;
END;

IF COL_LENGTH('dbo.PartySides', 'ShaleClientId') IS NULL
BEGIN
    THROW 52201, 'dbo.PartySides.ShaleClientId does not exist.', 1;
END;

IF COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL
BEGIN
    THROW 52202, 'dbo.PartySides.SystemKey does not exist. Run phase1 prep first.', 1;
END;

-- Normalize key formatting before activation checks.
UPDATE ps
SET SystemKey = LOWER(LTRIM(RTRIM(ps.SystemKey)))
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NOT NULL
  AND ps.SystemKey <> LOWER(LTRIM(RTRIM(ps.SystemKey)));

-- Ensure built-in tenant rows are keyed where name intent is clear.
UPDATE ps
SET SystemKey = 'represented'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'represented';

UPDATE ps
SET SystemKey = 'opposing'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'opposing';

UPDATE ps
SET SystemKey = 'neutral'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'neutral';

-- Fail fast if keyed duplicates exist in same scope before activation.
IF EXISTS (
    SELECT 1
    FROM dbo.PartySides
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 52203, 'Duplicate keyed PartySides rows exist by (ShaleClientId, SystemKey). Resolve before activation.', 1;
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
WHERE c.object_id = OBJECT_ID('dbo.PartySides')
  AND c.name = 'ShaleClientId';

SELECT
    i.name AS IndexName,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.PartySides')
  AND c.name = 'ShaleClientId'
GROUP BY i.name, i.is_unique, i.has_filter, i.filter_definition
ORDER BY i.name;

-- Make PartySides.ShaleClientId nullable if not already.
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.PartySides')
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
    WHERE c.object_id = OBJECT_ID('dbo.PartySides')
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

    SET @sql = N'ALTER TABLE dbo.PartySides ALTER COLUMN ShaleClientId ' + @typeDecl + N' NULL;';
    EXEC sp_executesql @sql;
END;

-- Seed separate global rows (ShaleClientId IS NULL) for established built-ins.
DECLARE @seed TABLE (
    SystemKey nvarchar(64) NOT NULL,
    DefaultName nvarchar(128) NOT NULL
);

INSERT INTO @seed (SystemKey, DefaultName)
VALUES
    ('represented', 'Represented'),
    ('opposing', 'Opposing'),
    ('neutral', 'Neutral');

INSERT INTO dbo.PartySides (ShaleClientId, Name, SystemKey)
SELECT
    NULL AS ShaleClientId,
    COALESCE(src.Name, s.DefaultName) AS Name,
    s.SystemKey
FROM @seed s
OUTER APPLY (
    SELECT TOP (1) ps.Name
    FROM dbo.PartySides ps
    WHERE ps.ShaleClientId = 7
      AND ps.SystemKey = s.SystemKey
    ORDER BY ps.Id
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.PartySides existing
    WHERE existing.ShaleClientId IS NULL
      AND existing.SystemKey = s.SystemKey
);

-- Verification outputs.
SELECT
    c.name AS ColumnName,
    c.is_nullable
FROM sys.columns c
WHERE c.object_id = OBJECT_ID('dbo.PartySides')
  AND c.name = 'ShaleClientId';

SELECT Id, ShaleClientId, Name, SystemKey
FROM dbo.PartySides
WHERE (ShaleClientId = 7 OR ShaleClientId IS NULL)
  AND SystemKey IN ('represented', 'opposing', 'neutral')
ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;

-- Compatibility visibility: CaseParties.Side text values (unchanged model in this pass).
IF OBJECT_ID('dbo.CaseParties', 'U') IS NOT NULL AND COL_LENGTH('dbo.CaseParties', 'Side') IS NOT NULL
BEGIN
    SELECT Side, COUNT(*) AS Cnt
    FROM dbo.CaseParties
    GROUP BY Side
    ORDER BY Cnt DESC, Side;
END;

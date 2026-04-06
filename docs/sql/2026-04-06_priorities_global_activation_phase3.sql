/*
  Priorities global/default activation (phase 3).

  Goal:
  - Enable true global Priorities overlay by allowing ShaleClientId IS NULL
    and seeding separate global built-ins.

  Built-ins:
  - low
  - normal
  - high

  Safety:
  - does NOT rewrite Tasks.PriorityId history
  - does NOT convert tenant rows into global rows
  - keeps tenant 7 rows active as override-capable rows
  - leaves Urgent as unkeyed/custom in this pass
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.Priorities', 'U') IS NULL
BEGIN
    THROW 52400, 'dbo.Priorities does not exist.', 1;
END;

IF COL_LENGTH('dbo.Priorities', 'ShaleClientId') IS NULL
BEGIN
    THROW 52401, 'dbo.Priorities.ShaleClientId does not exist.', 1;
END;

IF COL_LENGTH('dbo.Priorities', 'SystemKey') IS NULL
BEGIN
    THROW 52402, 'dbo.Priorities.SystemKey does not exist. Run phase1 prep first.', 1;
END;

-- Normalize key formatting before activation checks.
UPDATE p
SET SystemKey = LOWER(LTRIM(RTRIM(p.SystemKey)))
FROM dbo.Priorities p
WHERE p.SystemKey IS NOT NULL
  AND p.SystemKey <> LOWER(LTRIM(RTRIM(p.SystemKey)));

-- Explicit built-in mapping only (no heuristic mapping in this activation pass).
UPDATE p
SET SystemKey = 'low'
FROM dbo.Priorities p
WHERE p.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(p.Name, ''))) = 'Low'
  AND (p.SystemKey IS NULL OR p.SystemKey <> 'low');

UPDATE p
SET SystemKey = 'normal'
FROM dbo.Priorities p
WHERE p.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(p.Name, ''))) = 'Medium'
  AND (p.SystemKey IS NULL OR p.SystemKey <> 'normal');

UPDATE p
SET SystemKey = 'high'
FROM dbo.Priorities p
WHERE p.ShaleClientId = 7
  AND LTRIM(RTRIM(COALESCE(p.Name, ''))) = 'High'
  AND (p.SystemKey IS NULL OR p.SystemKey <> 'high');

-- Fail fast if keyed duplicates exist in same scope before activation.
IF EXISTS (
    SELECT 1
    FROM dbo.Priorities
    WHERE SystemKey IS NOT NULL
    GROUP BY ShaleClientId, SystemKey
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 52403, 'Duplicate keyed Priorities rows exist by (ShaleClientId, SystemKey). Resolve before activation.', 1;
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
WHERE c.object_id = OBJECT_ID('dbo.Priorities')
  AND c.name = 'ShaleClientId';

SELECT
    i.name AS IndexName,
    i.is_unique,
    i.has_filter,
    i.filter_definition
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.Priorities')
  AND c.name = 'ShaleClientId'
GROUP BY i.name, i.is_unique, i.has_filter, i.filter_definition
ORDER BY i.name;

-- Make Priorities.ShaleClientId nullable if not already.
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Priorities')
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
    WHERE c.object_id = OBJECT_ID('dbo.Priorities')
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

    SET @sql = N'ALTER TABLE dbo.Priorities ALTER COLUMN ShaleClientId ' + @typeDecl + N' NULL;';
    EXEC sp_executesql @sql;
END;

-- Seed separate global rows (ShaleClientId IS NULL) for established built-ins.
DECLARE @seed TABLE (
    SystemKey nvarchar(64) NOT NULL,
    DefaultName nvarchar(128) NOT NULL,
    DefaultSortOrder int NOT NULL
);

INSERT INTO @seed (SystemKey, DefaultName, DefaultSortOrder)
VALUES
    ('low', 'Low', 10),
    ('normal', 'Medium', 20),
    ('high', 'High', 30);

INSERT INTO dbo.Priorities (ShaleClientId, Name, SortOrder, IsActive, SystemKey)
SELECT
    NULL AS ShaleClientId,
    COALESCE(src.Name, s.DefaultName) AS Name,
    COALESCE(src.SortOrder, s.DefaultSortOrder) AS SortOrder,
    COALESCE(src.IsActive, 1) AS IsActive,
    s.SystemKey
FROM @seed s
OUTER APPLY (
    SELECT TOP (1) p.Name, p.SortOrder, p.IsActive
    FROM dbo.Priorities p
    WHERE p.ShaleClientId = 7
      AND p.SystemKey = s.SystemKey
    ORDER BY p.Id
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.Priorities existing
    WHERE existing.ShaleClientId IS NULL
      AND existing.SystemKey = s.SystemKey
);

-- Verification outputs.
SELECT
    c.name AS ColumnName,
    c.is_nullable
FROM sys.columns c
WHERE c.object_id = OBJECT_ID('dbo.Priorities')
  AND c.name = 'ShaleClientId';

SELECT Id, ShaleClientId, Name, SortOrder, IsActive, SystemKey
FROM dbo.Priorities
WHERE (ShaleClientId = 7 OR ShaleClientId IS NULL)
  AND SystemKey IN ('low', 'normal', 'high')
ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, SystemKey, Id;

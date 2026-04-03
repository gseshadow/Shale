/*
  Phase 2 prerequisite for global/default statuses:
  allow dbo.Statuses.ShaleClientId to be NULL.

  Why:
  - Global/default status rows are modeled as ShaleClientId IS NULL.
  - Tenant rows remain ShaleClientId = <tenant id>.

  Safety:
  - Does NOT change any Statuses.Id values.
  - Does NOT modify CaseStatuses history/FKs.
  - Only relaxes nullability on Statuses.ShaleClientId.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.Statuses', 'U') IS NULL
BEGIN
    THROW 51000, 'dbo.Statuses does not exist.', 1;
END;

IF COL_LENGTH('dbo.Statuses', 'ShaleClientId') IS NULL
BEGIN
    THROW 51001, 'dbo.Statuses.ShaleClientId does not exist.', 1;
END;

-- Preflight visibility: dependencies/assumptions to review before applying.
SELECT
    t.name  AS TableName,
    c.name  AS ColumnName,
    ty.name AS SqlType,
    c.max_length,
    c.precision,
    c.scale,
    c.is_nullable
FROM sys.columns c
JOIN sys.tables t ON t.object_id = c.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.object_id = OBJECT_ID('dbo.Statuses')
  AND c.name = 'ShaleClientId';

SELECT
    fk.name AS ForeignKeyName,
    OBJECT_SCHEMA_NAME(fk.parent_object_id) + '.' + OBJECT_NAME(fk.parent_object_id) AS ReferencingTable,
    pc.name AS ReferencingColumn,
    OBJECT_SCHEMA_NAME(fk.referenced_object_id) + '.' + OBJECT_NAME(fk.referenced_object_id) AS ReferencedTable,
    rc.name AS ReferencedColumn
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
WHERE (fkc.parent_object_id = OBJECT_ID('dbo.Statuses') AND pc.name = 'ShaleClientId')
   OR (fkc.referenced_object_id = OBJECT_ID('dbo.Statuses') AND rc.name = 'ShaleClientId')
ORDER BY fk.name;

SELECT
    i.name AS IndexName,
    i.is_unique,
    i.has_filter,
    i.filter_definition,
    STRING_AGG(c.name, ', ') WITHIN GROUP (ORDER BY ic.key_ordinal) AS IndexedColumns
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.Statuses')
  AND i.is_hypothetical = 0
GROUP BY i.name, i.is_unique, i.has_filter, i.filter_definition
HAVING SUM(CASE WHEN c.name = 'ShaleClientId' THEN 1 ELSE 0 END) > 0
ORDER BY i.name;

SELECT
    cc.name AS CheckConstraintName,
    cc.definition
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID('dbo.Statuses')
  AND cc.definition LIKE '%ShaleClientId%';

SELECT
    tr.name AS TriggerName,
    m.definition
FROM sys.triggers tr
JOIN sys.sql_modules m ON m.object_id = tr.object_id
WHERE tr.parent_id = OBJECT_ID('dbo.Statuses')
  AND m.definition LIKE '%ShaleClientId%';

-- Apply nullability relaxation if needed.
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.Statuses')
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
    WHERE c.object_id = OBJECT_ID('dbo.Statuses')
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

    SET @sql = N'ALTER TABLE dbo.Statuses ALTER COLUMN ShaleClientId ' + @typeDecl + N' NULL;';
    EXEC sp_executesql @sql;
END;

-- Post-check
SELECT
    c.name AS ColumnName,
    c.is_nullable
FROM sys.columns c
WHERE c.object_id = OBJECT_ID('dbo.Statuses')
  AND c.name = 'ShaleClientId';


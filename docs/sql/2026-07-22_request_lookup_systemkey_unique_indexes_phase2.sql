/*
  Request lookup SystemKey uniqueness phase 2 hardening.

  Adds filtered unique indexes for RequestMethods and RequestStatuses overlay
  lookup keys. Global rows and tenant-owned rows intentionally use separate
  uniqueness scopes: one global row per SystemKey where ShaleClientId is NULL,
  and one tenant override/custom row per (ShaleClientId, SystemKey) where
  ShaleClientId is NOT NULL.

  This migration does not read or mutate lookup data before creating the
  indexes. SQL Server unique-index creation is allowed to validate every
  physical row, including rows that row-level security may hide from the
  deploying session. Any duplicate-key error rolls back the full migration.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.RequestMethods', N'U') IS NULL THROW 54820, 'Required table dbo.RequestMethods is missing.', 1;
IF OBJECT_ID(N'dbo.RequestStatuses', N'U') IS NULL THROW 54821, 'Required table dbo.RequestStatuses is missing.', 1;
IF COL_LENGTH(N'dbo.RequestMethods', N'ShaleClientId') IS NULL THROW 54822, 'Required tenant column dbo.RequestMethods.ShaleClientId is missing.', 1;
IF COL_LENGTH(N'dbo.RequestMethods', N'SystemKey') IS NULL THROW 54823, 'Required key column dbo.RequestMethods.SystemKey is missing.', 1;
IF COL_LENGTH(N'dbo.RequestStatuses', N'ShaleClientId') IS NULL THROW 54824, 'Required tenant column dbo.RequestStatuses.ShaleClientId is missing.', 1;
IF COL_LENGTH(N'dbo.RequestStatuses', N'SystemKey') IS NULL THROW 54825, 'Required key column dbo.RequestStatuses.SystemKey is missing.', 1;

/* Global overlay keys: exactly one global RequestMethods row may claim each SystemKey. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RequestMethods') AND name = N'UX_RequestMethods_Global_SystemKey')
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS i
        WHERE i.object_id = OBJECT_ID(N'dbo.RequestMethods')
          AND i.name = N'UX_RequestMethods_Global_SystemKey'
          AND i.is_unique = 1
          AND i.has_filter = 1
          AND i.filter_definition = N'([ShaleClientId] IS NULL AND [SystemKey] IS NOT NULL)'
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1)
          AND 1 = (SELECT COUNT(*) FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0)
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'SystemKey')
    )
        THROW 54826, 'Index UX_RequestMethods_Global_SystemKey exists with a different definition. Stop for manual review.', 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_RequestMethods_Global_SystemKey
        ON dbo.RequestMethods (SystemKey)
        WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL;
END;

/* Tenant overlay keys: each tenant may claim each RequestMethods SystemKey once. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RequestMethods') AND name = N'UX_RequestMethods_Tenant_SystemKey')
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS i
        WHERE i.object_id = OBJECT_ID(N'dbo.RequestMethods')
          AND i.name = N'UX_RequestMethods_Tenant_SystemKey'
          AND i.is_unique = 1
          AND i.has_filter = 1
          AND i.filter_definition = N'([ShaleClientId] IS NOT NULL AND [SystemKey] IS NOT NULL)'
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1)
          AND 2 = (SELECT COUNT(*) FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0)
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'ShaleClientId')
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 2 AND ic.is_included_column = 0 AND c.name = N'SystemKey')
    )
        THROW 54827, 'Index UX_RequestMethods_Tenant_SystemKey exists with a different definition. Stop for manual review.', 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_RequestMethods_Tenant_SystemKey
        ON dbo.RequestMethods (ShaleClientId, SystemKey)
        WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL;
END;

/* Global overlay keys: exactly one global RequestStatuses row may claim each SystemKey. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RequestStatuses') AND name = N'UX_RequestStatuses_Global_SystemKey')
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS i
        WHERE i.object_id = OBJECT_ID(N'dbo.RequestStatuses')
          AND i.name = N'UX_RequestStatuses_Global_SystemKey'
          AND i.is_unique = 1
          AND i.has_filter = 1
          AND i.filter_definition = N'([ShaleClientId] IS NULL AND [SystemKey] IS NOT NULL)'
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1)
          AND 1 = (SELECT COUNT(*) FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0)
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'SystemKey')
    )
        THROW 54828, 'Index UX_RequestStatuses_Global_SystemKey exists with a different definition. Stop for manual review.', 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_RequestStatuses_Global_SystemKey
        ON dbo.RequestStatuses (SystemKey)
        WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL;
END;

/* Tenant overlay keys: each tenant may claim each RequestStatuses SystemKey once. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RequestStatuses') AND name = N'UX_RequestStatuses_Tenant_SystemKey')
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS i
        WHERE i.object_id = OBJECT_ID(N'dbo.RequestStatuses')
          AND i.name = N'UX_RequestStatuses_Tenant_SystemKey'
          AND i.is_unique = 1
          AND i.has_filter = 1
          AND i.filter_definition = N'([ShaleClientId] IS NOT NULL AND [SystemKey] IS NOT NULL)'
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1)
          AND 2 = (SELECT COUNT(*) FROM sys.index_columns AS ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0)
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'ShaleClientId')
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 2 AND ic.is_included_column = 0 AND c.name = N'SystemKey')
    )
        THROW 54829, 'Index UX_RequestStatuses_Tenant_SystemKey exists with a different definition. Stop for manual review.', 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_RequestStatuses_Tenant_SystemKey
        ON dbo.RequestStatuses (ShaleClientId, SystemKey)
        WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL;
END;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/*
Read-only post-deployment verification for the four RequestMethods and
RequestStatuses SystemKey uniqueness indexes.
*/
SELECT
    TableName = OBJECT_SCHEMA_NAME(i.object_id) + N'.' + OBJECT_NAME(i.object_id),
    IndexName = i.name,
    IsUnique = i.is_unique,
    FilterDefinition = i.filter_definition,
    KeyOrdinal = ic.key_ordinal,
    ColumnName = c.name,
    IsIncludedColumn = ic.is_included_column
FROM sys.indexes AS i
JOIN sys.index_columns AS ic
  ON ic.object_id = i.object_id
 AND ic.index_id = i.index_id
JOIN sys.columns AS c
  ON c.object_id = ic.object_id
 AND c.column_id = ic.column_id
WHERE i.object_id IN (OBJECT_ID(N'dbo.RequestMethods'), OBJECT_ID(N'dbo.RequestStatuses'))
  AND i.name IN (
      N'UX_RequestMethods_Global_SystemKey',
      N'UX_RequestMethods_Tenant_SystemKey',
      N'UX_RequestStatuses_Global_SystemKey',
      N'UX_RequestStatuses_Tenant_SystemKey'
  )
ORDER BY TableName, IndexName, ic.key_ordinal, c.name;
GO

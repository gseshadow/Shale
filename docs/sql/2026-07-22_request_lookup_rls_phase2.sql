/*
  Request lookup RLS phase 2 hardening.

  Adds tenant-or-global FILTER predicates to the existing RequestMethods and
  RequestStatuses overlay lookup tables. This protects global-plus-current-tenant
  lookup definitions while preserving the existing LinkTypes and MaterialTypes
  predicates, data, indexes, and application behavior.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.RequestMethods', N'U') IS NULL THROW 54800, 'Required table dbo.RequestMethods is missing.', 1;
IF OBJECT_ID(N'dbo.RequestStatuses', N'U') IS NULL THROW 54801, 'Required table dbo.RequestStatuses is missing.', 1;
IF COL_LENGTH(N'dbo.RequestMethods', N'ShaleClientId') IS NULL THROW 54802, 'Required tenant column dbo.RequestMethods.ShaleClientId is missing.', 1;
IF COL_LENGTH(N'dbo.RequestStatuses', N'ShaleClientId') IS NULL THROW 54803, 'Required tenant column dbo.RequestStatuses.ShaleClientId is missing.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL THROW 54804, 'Required overlay predicate sec.fn_FilterByTenantOrGlobal is missing.', 1;

DECLARE @PolicySchemaName sysname, @PolicyName sysname, @PolicyQualified nvarchar(517), @PolicyObjectId int;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name = N'TenantFilter') <> 1 THROW 54805, 'Required security policy TenantFilter is missing or ambiguous.', 1;
SELECT @PolicySchemaName = SCHEMA_NAME(schema_id), @PolicyName = name, @PolicyObjectId = object_id FROM sys.security_policies WHERE name = N'TenantFilter';
SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName);

DECLARE @Sql nvarchar(max);

/* Protect global-plus-current-tenant RequestMethods lookup definitions. */
IF NOT EXISTS (
    SELECT 1
    FROM sys.security_predicates AS p
    WHERE p.object_id = @PolicyObjectId
      AND p.target_object_id = OBJECT_ID(N'dbo.RequestMethods')
      AND p.predicate_type_desc = N'FILTER'
      AND p.predicate_definition = N'[sec].[fn_FilterByTenantOrGlobal]([ShaleClientId])'
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.security_predicates AS p
        WHERE p.object_id = @PolicyObjectId
          AND p.target_object_id = OBJECT_ID(N'dbo.RequestMethods')
          AND p.predicate_type_desc = N'FILTER'
    )
        THROW 54806, 'dbo.RequestMethods already has a non-matching FILTER predicate. Stop for manual review.', 1;

    SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestMethods;';
    EXEC sys.sp_executesql @Sql;
END;

/* Protect global-plus-current-tenant RequestStatuses lookup definitions. */
IF NOT EXISTS (
    SELECT 1
    FROM sys.security_predicates AS p
    WHERE p.object_id = @PolicyObjectId
      AND p.target_object_id = OBJECT_ID(N'dbo.RequestStatuses')
      AND p.predicate_type_desc = N'FILTER'
      AND p.predicate_definition = N'[sec].[fn_FilterByTenantOrGlobal]([ShaleClientId])'
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.security_predicates AS p
        WHERE p.object_id = @PolicyObjectId
          AND p.target_object_id = OBJECT_ID(N'dbo.RequestStatuses')
          AND p.predicate_type_desc = N'FILTER'
    )
        THROW 54807, 'dbo.RequestStatuses already has a non-matching FILTER predicate. Stop for manual review.', 1;

    SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestStatuses;';
    EXEC sys.sp_executesql @Sql;
END;

SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' WITH (STATE = ON);';
EXEC sys.sp_executesql @Sql;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/*
Read-only post-deployment verification. Expected: all four tables have enabled
FILTER predicates using sec.fn_FilterByTenantOrGlobal(ShaleClientId).
*/
SELECT
    TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    SecurityPolicy = SCHEMA_NAME(sp.schema_id) + N'.' + sp.name,
    PredicateType = p.predicate_type_desc,
    PredicateDefinition = p.predicate_definition,
    PolicyEnabled = sp.is_enabled
FROM sys.security_predicates AS p
JOIN sys.security_policies AS sp
  ON sp.object_id = p.object_id
WHERE p.target_object_id IN (
    OBJECT_ID(N'dbo.LinkTypes'),
    OBJECT_ID(N'dbo.MaterialTypes'),
    OBJECT_ID(N'dbo.RequestMethods'),
    OBJECT_ID(N'dbo.RequestStatuses')
)
  AND p.predicate_type_desc = N'FILTER'
ORDER BY TargetTable, SecurityPolicy;
GO

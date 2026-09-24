/*
  Shale A.2 Phase 7: harden the reused UserPreferences store for Appearance.

  This migration does not create a parallel preference table. It adds strict tenant
  RLS to the existing generic store and constrains the typed appearance.theme value.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.UserPreferences', N'U') IS NULL
        THROW 55700, 'Required table dbo.UserPreferences is missing.', 1;
    IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL
        THROW 55701, 'Required tenant predicate sec.fn_FilterByTenant is missing.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.UserPreferences')
          AND name = N'CK_UserPreferences_AppearanceTheme'
    )
    BEGIN
        ALTER TABLE dbo.UserPreferences WITH CHECK ADD CONSTRAINT CK_UserPreferences_AppearanceTheme
            CHECK (PreferenceKey <> N'appearance.theme'
                OR (ValueType = N'STRING' AND PreferenceValue IN (N'LIGHT', N'DARK')));
        ALTER TABLE dbo.UserPreferences CHECK CONSTRAINT CK_UserPreferences_AppearanceTheme;
    END;

    DECLARE @PolicySchema sysname, @PolicyName sysname, @PolicyId int, @Sql nvarchar(max);
    IF (SELECT COUNT(*) FROM sys.security_policies WHERE name = N'TenantFilter') <> 1
        THROW 55702, 'Required security policy TenantFilter is missing or ambiguous.', 1;
    SELECT @PolicySchema = SCHEMA_NAME(schema_id), @PolicyName = name, @PolicyId = object_id
    FROM sys.security_policies WHERE name = N'TenantFilter';

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates
        WHERE object_id = @PolicyId
          AND target_object_id = OBJECT_ID(N'dbo.UserPreferences')
          AND predicate_type_desc = N'FILTER'
          AND predicate_definition = N'[sec].[fn_FilterByTenant]([ShaleClientId])'
    )
    BEGIN
        IF EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyId
                   AND target_object_id = OBJECT_ID(N'dbo.UserPreferences') AND predicate_type_desc = N'FILTER')
            THROW 55703, 'UserPreferences has a non-matching RLS predicate; manual review is required.', 1;
        SET @Sql = N'ALTER SECURITY POLICY ' + QUOTENAME(@PolicySchema) + N'.' + QUOTENAME(@PolicyName)
                 + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserPreferences;';
        EXEC sys.sp_executesql @Sql;
    END;

    SET @Sql = N'ALTER SECURITY POLICY ' + QUOTENAME(@PolicySchema) + N'.' + QUOTENAME(@PolicyName)
             + N' WITH (STATE = ON);';
    EXEC sys.sp_executesql @Sql;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* Read-only verification. Every FindingCount must be zero; the predicate and constraint must be enabled/trusted. */
SELECT Finding = N'Invalid appearance value', FindingCount = COUNT_BIG(*)
FROM dbo.UserPreferences
WHERE PreferenceKey = N'appearance.theme'
  AND (ValueType <> N'STRING' OR PreferenceValue IS NULL OR PreferenceValue NOT IN (N'LIGHT', N'DARK'))
UNION ALL
SELECT N'Orphan tenant', COUNT_BIG(*) FROM dbo.UserPreferences p
WHERE NOT EXISTS (SELECT 1 FROM dbo.ShaleClients c WHERE c.Id = p.ShaleClientId)
UNION ALL
SELECT N'Orphan or cross-tenant user', COUNT_BIG(*) FROM dbo.UserPreferences p
WHERE NOT EXISTS (SELECT 1 FROM dbo.Users u WHERE u.id = p.UserId AND u.ShaleClientId = p.ShaleClientId)
UNION ALL
SELECT N'Duplicate tenant/user/key', COUNT_BIG(*) FROM (
    SELECT ShaleClientId, UserId, PreferenceKey FROM dbo.UserPreferences
    GROUP BY ShaleClientId, UserId, PreferenceKey HAVING COUNT_BIG(*) > 1
) d;

SELECT ConstraintEnabledAndTrusted = CASE WHEN is_disabled = 0 AND is_not_trusted = 0 THEN 1 ELSE 0 END
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.UserPreferences') AND name = N'CK_UserPreferences_AppearanceTheme';

SELECT SecurityPolicy = SCHEMA_NAME(sp.schema_id) + N'.' + sp.name,
       p.predicate_definition, sp.is_enabled
FROM sys.security_predicates p
JOIN sys.security_policies sp ON sp.object_id = p.object_id
WHERE p.target_object_id = OBJECT_ID(N'dbo.UserPreferences') AND p.predicate_type_desc = N'FILTER';
GO

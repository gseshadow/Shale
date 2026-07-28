/* Durable notification Phase 0 foundation. Idempotent, data-preserving upgrade only. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Notifications', N'U') IS NULL
    THROW 54900, 'Required existing table dbo.Notifications is missing. Run notification schema verification before continuing.', 1;
IF COL_LENGTH(N'dbo.Notifications', N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.Notifications', N'UserId') IS NULL
    OR COL_LENGTH(N'dbo.Notifications', N'Id') IS NULL OR COL_LENGTH(N'dbo.Notifications', N'CreatedAt') IS NULL
    OR COL_LENGTH(N'dbo.Notifications', N'EventKey') IS NULL OR COL_LENGTH(N'dbo.Notifications', N'IsRead') IS NULL
    OR COL_LENGTH(N'dbo.Notifications', N'IsDismissed') IS NULL
    THROW 54901, 'dbo.Notifications is missing a required established column. Stop for manual review.', 1;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Notifications') AND name=N'EventKey' AND max_length=-1)
    THROW 54906, 'dbo.Notifications.EventKey is a MAX type and cannot be indexed safely. Stop for manual review.', 1;

IF COL_LENGTH(N'dbo.Notifications', N'ExpiresAt') IS NULL
    ALTER TABLE dbo.Notifications ADD ExpiresAt datetime2 NULL;
IF COL_LENGTH(N'dbo.Notifications', N'RowVer') IS NULL
    ALTER TABLE dbo.Notifications ADD RowVer rowversion NOT NULL;

IF EXISTS (
    SELECT 1 FROM dbo.Notifications
    WHERE EventKey IS NOT NULL
    GROUP BY ShaleClientId, UserId, EventKey HAVING COUNT_BIG(*) > 1
)
    THROW 54902, 'Duplicate tenant/user/EventKey rows exist. Resolve them without deleting notification history before retrying.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    WHERE i.object_id=OBJECT_ID(N'dbo.Notifications') AND i.is_unique=1
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=3
      AND EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 AND COL_NAME(ic.object_id,ic.column_id)=N'ShaleClientId')
      AND EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=2 AND COL_NAME(ic.object_id,ic.column_id)=N'UserId')
      AND EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=3 AND COL_NAME(ic.object_id,ic.column_id)=N'EventKey')
)
    CREATE UNIQUE INDEX UX_Notifications_Tenant_User_EventKey
        ON dbo.Notifications(ShaleClientId, UserId, EventKey) WHERE EventKey IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Notifications') AND name=N'IX_Notifications_Tenant_User_Cursor')
    CREATE INDEX IX_Notifications_Tenant_User_Cursor
        ON dbo.Notifications(ShaleClientId, UserId, Id)
        INCLUDE (CreatedAt, Category, IsRead, IsDismissed, ExpiresAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Notifications') AND name=N'IX_Notifications_Tenant_User_Unread')
    CREATE INDEX IX_Notifications_Tenant_User_Unread
        ON dbo.Notifications(ShaleClientId, UserId, IsDismissed, IsRead, CreatedAt DESC, Id DESC)
        INCLUDE (ExpiresAt);

IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL
    THROW 54903, 'Required strict predicate sec.fn_FilterByTenant is missing.', 1;
DECLARE @PolicyObjectId int, @PolicyQualified nvarchar(517), @Sql nvarchar(max);
SELECT @PolicyObjectId=object_id,
       @PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyObjectId IS NULL THROW 54904, 'Enabled TenantFilter security policy is missing.', 1;
IF EXISTS (SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.Notifications') AND predicate_type_desc=N'FILTER'
           AND NOT (object_id=@PolicyObjectId AND predicate_definition=N'[sec].[fn_FilterByTenant]([ShaleClientId])'))
    THROW 54905, 'dbo.Notifications has a non-matching FILTER predicate. Stop for manual review.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyObjectId AND target_object_id=OBJECT_ID(N'dbo.Notifications')
               AND predicate_type_desc=N'FILTER' AND predicate_definition=N'[sec].[fn_FilterByTenant]([ShaleClientId])')
BEGIN
    SET @Sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Notifications;';
    EXEC sys.sp_executesql @Sql;
END;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

SELECT c.column_id,c.name,TYPE_NAME(c.user_type_id) AS DataType,c.is_nullable
FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.Notifications') ORDER BY c.column_id;
SELECT i.name,i.is_unique,i.filter_definition
FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Notifications') ORDER BY i.name;
SELECT SCHEMA_NAME(sp.schema_id)+N'.'+sp.name AS SecurityPolicy,p.predicate_definition,sp.is_enabled
FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
WHERE p.target_object_id=OBJECT_ID(N'dbo.Notifications');

/* Phase 3A: per-user announcement acknowledgement only. Forward-only and rerunnable. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL THROW 57300,'Required Phase 1A or identity tables are missing.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57301,'Required strict tenant predicate is missing.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,Id')
  CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id);
 IF OBJECT_ID(N'dbo.UserReleaseState',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.UserReleaseState(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_UserReleaseState PRIMARY KEY,
   ShaleClientId int NOT NULL,
   UserId int NOT NULL,
   ClientType varchar(16) NOT NULL,
   ReleaseChannel varchar(32) NOT NULL,
   ApplicationReleaseId bigint NULL,
   AcknowledgedAt datetime2(7) NOT NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_UserReleaseState_CreatedAt DEFAULT(SYSUTCDATETIME()),
   UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_UserReleaseState_UpdatedAt DEFAULT(SYSUTCDATETIME()),
   RowVer rowversion NOT NULL,
   CONSTRAINT CK_UserReleaseState_ClientType CHECK(ClientType IN('DESKTOP','WEB','MOBILE')),
   CONSTRAINT CK_UserReleaseState_ReleaseChannel CHECK(ReleaseChannel IN('PRODUCTION','PILOT','DEVELOPMENT')),
   CONSTRAINT FK_UserReleaseState_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_UserReleaseState_UserTenant FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id) ON DELETE NO ACTION,
   CONSTRAINT FK_UserReleaseState_Release FOREIGN KEY(ApplicationReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION
  );
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserReleaseState') AND name=N'UX_UserReleaseState_Scope') CREATE UNIQUE INDEX UX_UserReleaseState_Scope ON dbo.UserReleaseState(ShaleClientId,UserId,ClientType,ReleaseChannel);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserReleaseState') AND name=N'IX_UserReleaseState_Release') CREATE INDEX IX_UserReleaseState_Release ON dbo.UserReleaseState(ApplicationReleaseId) WHERE ApplicationReleaseId IS NOT NULL;
 DECLARE @ExpectedColumns table(ColumnName sysname,TypeName sysname,MaxLength smallint,Nullable bit,IdentityColumn bit);
 INSERT @ExpectedColumns VALUES(N'Id',N'bigint',8,0,1),(N'ShaleClientId',N'int',4,0,0),(N'UserId',N'int',4,0,0),(N'ClientType',N'varchar',16,0,0),(N'ReleaseChannel',N'varchar',32,0,0),(N'ApplicationReleaseId',N'bigint',8,1,0),(N'AcknowledgedAt',N'datetime2',8,0,0),(N'CreatedAt',N'datetime2',8,0,0),(N'UpdatedAt',N'datetime2',8,0,0),(N'RowVer',N'timestamp',8,0,0);
 IF EXISTS(SELECT 1 FROM @ExpectedColumns e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.UserReleaseState') AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName COLLATE DATABASE_DEFAULT LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR t.name COLLATE DATABASE_DEFAULT<>e.TypeName COLLATE DATABASE_DEFAULT OR c.max_length<>e.MaxLength OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityColumn) THROW 57302,'UserReleaseState has an incompatible column.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.UserReleaseState') AND (is_disabled=1 OR is_not_trusted=1 OR delete_referential_action<>0)) THROW 57303,'UserReleaseState foreign keys must be trusted and non-cascading.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserReleaseState') AND name COLLATE DATABASE_DEFAULT=N'UX_UserReleaseState_Scope' COLLATE DATABASE_DEFAULT AND is_unique=1) THROW 57304,'Logical scope uniqueness is missing.',1;
 DECLARE @PolicyId int,@Policy nvarchar(517),@Sql nvarchar(max);
 IF (SELECT COUNT(*) FROM sys.security_policies WHERE name COLLATE DATABASE_DEFAULT=N'TenantFilter' COLLATE DATABASE_DEFAULT AND is_enabled=1)<>1 THROW 57305,'Enabled TenantFilter is missing or ambiguous.',1;
 SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name COLLATE DATABASE_DEFAULT=N'TenantFilter' COLLATE DATABASE_DEFAULT AND is_enabled=1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserReleaseState') AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserReleaseState;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserReleaseState') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserReleaseState AFTER INSERT;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserReleaseState') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserReleaseState AFTER UPDATE;';EXEC sys.sp_executesql @Sql;END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
/* No rows are seeded. Ordinary acknowledgement intentionally emits no EntityActionAuditLog row. */

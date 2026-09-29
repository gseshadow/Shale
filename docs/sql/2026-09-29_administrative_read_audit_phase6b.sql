/* Phase 6B: bounded sensitive administrative-read audit. Forward-only, additive, and rerunnable. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL
 BEGIN
  THROW 57600, 'Required identity tables are missing.', 1;
 END;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL
 BEGIN
  THROW 57601, 'Required strict tenant predicate is missing.', 1;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,Id')
 BEGIN
  CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id);
 END;
 IF OBJECT_ID(N'dbo.AdministrativeReadAuditLog',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.AdministrativeReadAuditLog(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_AdministrativeReadAuditLog PRIMARY KEY,
   ShaleClientId int NOT NULL,
   ActorUserId int NOT NULL,
   ReadType varchar(64) NOT NULL,
   OccurredAt datetime2(7) NOT NULL CONSTRAINT DF_AdministrativeReadAuditLog_OccurredAt DEFAULT(SYSUTCDATETIME()),
   ResultCount int NOT NULL,
   Metadata varchar(1000) NULL,
   CONSTRAINT CK_AdministrativeReadAuditLog_ReadType CHECK(ReadType IN('APPLICATION_INSTANCE_RECENT_LIST','APPLICATION_INSTANCE_VERSION_DISTRIBUTION')),
   CONSTRAINT CK_AdministrativeReadAuditLog_ResultCount CHECK(ResultCount>=0),
   CONSTRAINT FK_AdministrativeReadAuditLog_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_AdministrativeReadAuditLog_ActorTenant FOREIGN KEY(ShaleClientId,ActorUserId) REFERENCES dbo.Users(ShaleClientId,Id) ON DELETE NO ACTION
  );
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.AdministrativeReadAuditLog') AND name=N'IX_AdministrativeReadAuditLog_TenantOccurred')
 BEGIN
  CREATE INDEX IX_AdministrativeReadAuditLog_TenantOccurred ON dbo.AdministrativeReadAuditLog(ShaleClientId,OccurredAt DESC,Id DESC);
 END;
 DECLARE @PolicyId int,@Policy nvarchar(517),@Sql nvarchar(max);
 IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1)<>1
 BEGIN
  THROW 57602, 'Enabled TenantFilter is missing or ambiguous.', 1;
 END;
 SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.AdministrativeReadAuditLog') AND predicate_type_desc=N'FILTER')
 BEGIN
  SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AdministrativeReadAuditLog;'; EXEC sys.sp_executesql @Sql;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.AdministrativeReadAuditLog') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT')
 BEGIN
  SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AdministrativeReadAuditLog AFTER INSERT;'; EXEC sys.sp_executesql @Sql;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.AdministrativeReadAuditLog') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE')
 BEGIN
  SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AdministrativeReadAuditLog AFTER UPDATE;'; EXEC sys.sp_executesql @Sql;
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
/* No seed rows. Application code exposes insert only; retention is an operator policy. */

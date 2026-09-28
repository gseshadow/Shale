/* Phase 4B: application process enrollment. Forward-only, additive, and rerunnable. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 57400,'Required identity tables are missing.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57401,'Required strict tenant predicate is missing.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,Id')
  CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id);
 IF OBJECT_ID(N'dbo.ApplicationInstances',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.ApplicationInstances(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApplicationInstances PRIMARY KEY,
   ShaleClientId int NOT NULL,
   UserId int NOT NULL,
   MachineId uniqueidentifier NULL,
   ClientType varchar(16) NOT NULL,
   MajorVersion int NOT NULL,
   MinorVersion int NOT NULL,
   BuildVersion int NOT NULL,
   StartedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationInstances_StartedAt DEFAULT(SYSUTCDATETIME()),
   EndedAt datetime2(7) NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationInstances_CreatedAt DEFAULT(SYSUTCDATETIME()),
   UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationInstances_UpdatedAt DEFAULT(SYSUTCDATETIME()),
   RowVer rowversion NOT NULL,
   CONSTRAINT CK_ApplicationInstances_ClientType CHECK(ClientType IN('DESKTOP','WEB','MOBILE')),
   CONSTRAINT CK_ApplicationInstances_Machine CHECK((ClientType='DESKTOP' AND MachineId IS NOT NULL) OR (ClientType IN('WEB','MOBILE') AND MachineId IS NULL)),
   CONSTRAINT CK_ApplicationInstances_Version CHECK(MajorVersion>=0 AND MinorVersion>=0 AND BuildVersion>=0),
   CONSTRAINT CK_ApplicationInstances_Lifecycle CHECK(EndedAt IS NULL OR EndedAt>=StartedAt),
   CONSTRAINT FK_ApplicationInstances_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_ApplicationInstances_UserTenant FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id) ON DELETE NO ACTION
  );
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name=N'IX_ApplicationInstances_TenantUserActive') CREATE INDEX IX_ApplicationInstances_TenantUserActive ON dbo.ApplicationInstances(ShaleClientId,UserId,StartedAt DESC) WHERE EndedAt IS NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name=N'IX_ApplicationInstances_TenantMachine') CREATE INDEX IX_ApplicationInstances_TenantMachine ON dbo.ApplicationInstances(ShaleClientId,MachineId,StartedAt DESC) WHERE MachineId IS NOT NULL;
 DECLARE @PolicyId int,@Policy nvarchar(517),@Sql nvarchar(max);
 IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1)<>1 THROW 57402,'Enabled TenantFilter is missing or ambiguous.',1;
 SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ApplicationInstances;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ApplicationInstances AFTER INSERT;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ApplicationInstances AFTER UPDATE;';EXEC sys.sp_executesql @Sql;END;
 COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO
/* No seed rows. Routine enrollment/end uses this lifecycle row and intentionally emits no entity-action audit event. */

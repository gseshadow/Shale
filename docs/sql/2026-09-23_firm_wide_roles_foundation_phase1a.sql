/*
 Phase 1A firm-wide role foundation.
 REVIEW/APPLY MANUALLY IN SSMS OR SQLCMD. DO NOT run as part of application startup.
 Forward-only and rerunnable. Execution order:
   1. this migration; 2. read-only verification script; 3. application deployment.

 Compatibility: ADMIN and ATTORNEY definitions are stable tenant-owned identities, but
 dbo.Users.is_admin and dbo.Users.is_attorney remain their sole authority. No assignment
 rows are created for those definitions. Future tenant roles use assignment rows.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
 BEGIN TRANSACTION;

 IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL
  THROW 56800,'Required ShaleClients or Users table is missing.',1;
 IF COL_LENGTH(N'dbo.Users',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.Users',N'is_admin') IS NULL
    OR COL_LENGTH(N'dbo.Users',N'is_attorney') IS NULL OR COL_LENGTH(N'dbo.Users',N'is_deleted') IS NULL
    OR COL_LENGTH(N'dbo.Users',N'IsRemoved') IS NULL
  THROW 56801,'Required Users tenant, lifecycle, or legacy role columns are missing.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 56802,'Strict tenant RLS predicate is missing.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND i.is_disabled=0
  AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=2
  AND EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 AND c.name='id')
  AND EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=2 AND c.name='ShaleClientId'))
  THROW 56809,'Required unique Users(id,ShaleClientId) tenant key is missing; apply User Management concurrency prerequisites first.',1;
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter');
 IF @policyId IS NULL OR EXISTS(SELECT 1 FROM sys.security_policies WHERE object_id=@policyId AND is_enabled=0)
  THROW 56803,'Enabled TenantFilter security policy is required.',1;

 IF OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.FirmWideRoleDefinitions(
   Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_FirmWideRoleDefinitions PRIMARY KEY,
   ShaleClientId int NOT NULL,
   SystemKey varchar(64) NOT NULL,
   Name nvarchar(100) NOT NULL,
   Description nvarchar(510) NULL,
   SortOrder int NOT NULL CONSTRAINT DF_FirmWideRoleDefinitions_SortOrder DEFAULT(0),
   IsActive bit NOT NULL CONSTRAINT DF_FirmWideRoleDefinitions_IsActive DEFAULT(1),
   IsDeleted bit NOT NULL CONSTRAINT DF_FirmWideRoleDefinitions_IsDeleted DEFAULT(0),
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_FirmWideRoleDefinitions_CreatedAt DEFAULT(SYSUTCDATETIME()),
   CreatedByUserId int NULL, UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL,
   DeletedAt datetime2(7) NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
   CONSTRAINT FK_FirmWideRoleDefinitions_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
   CONSTRAINT FK_FirmWideRoleDefinitions_CreatedByTenant FOREIGN KEY(CreatedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT FK_FirmWideRoleDefinitions_UpdatedByTenant FOREIGN KEY(UpdatedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT FK_FirmWideRoleDefinitions_DeletedByTenant FOREIGN KEY(DeletedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT CK_FirmWideRoleDefinitions_SystemKey CHECK(SystemKey=UPPER(SystemKey) AND SystemKey NOT LIKE '%[^A-Z0-9_]%'),
   CONSTRAINT CK_FirmWideRoleDefinitions_Name CHECK(LEN(LTRIM(RTRIM(Name)))>0),
   CONSTRAINT CK_FirmWideRoleDefinitions_Lifecycle CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
  );
 END;

 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'UX_FirmWideRoleDefinitions_Tenant_SystemKey')
  CREATE UNIQUE INDEX UX_FirmWideRoleDefinitions_Tenant_SystemKey ON dbo.FirmWideRoleDefinitions(ShaleClientId,SystemKey);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'UX_FirmWideRoleDefinitions_Id_Tenant')
  CREATE UNIQUE INDEX UX_FirmWideRoleDefinitions_Id_Tenant ON dbo.FirmWideRoleDefinitions(Id,ShaleClientId);

 /* Fail rather than silently reinterpret an existing built-in identity. */
 IF EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions WHERE SystemKey='ADMIN' AND Name<>N'Administrator')
  THROW 56804,'Existing ADMIN firm-wide role is incompatible.',1;
 IF EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions WHERE SystemKey='ATTORNEY' AND Name<>N'Attorney')
  THROW 56805,'Existing ATTORNEY firm-wide role is incompatible.',1;
 INSERT dbo.FirmWideRoleDefinitions(ShaleClientId,SystemKey,Name,Description,SortOrder)
 SELECT c.Id,v.SystemKey,v.Name,v.Description,v.SortOrder
 FROM dbo.ShaleClients c CROSS JOIN (VALUES
  ('ADMIN',N'Administrator',N'Compatibility role backed by Users.is_admin.',10),
  ('ATTORNEY',N'Attorney',N'Compatibility role backed by Users.is_attorney.',20)
 )v(SystemKey,Name,Description,SortOrder)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions d WHERE d.ShaleClientId=c.Id AND d.SystemKey=v.SystemKey);

 IF OBJECT_ID(N'dbo.UserFirmWideRoleAssignments',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.UserFirmWideRoleAssignments(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_UserFirmWideRoleAssignments PRIMARY KEY,
   ShaleClientId int NOT NULL, UserId int NOT NULL, FirmWideRoleDefinitionId int NOT NULL,
   IsDeleted bit NOT NULL CONSTRAINT DF_UserFirmWideRoleAssignments_IsDeleted DEFAULT(0),
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_UserFirmWideRoleAssignments_CreatedAt DEFAULT(SYSUTCDATETIME()),
   CreatedByUserId int NOT NULL, DeletedAt datetime2(7) NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
   CONSTRAINT FK_UserFirmWideRoleAssignments_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
   CONSTRAINT FK_UserFirmWideRoleAssignments_UserTenant FOREIGN KEY(UserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT FK_UserFirmWideRoleAssignments_DefinitionTenant FOREIGN KEY(FirmWideRoleDefinitionId,ShaleClientId) REFERENCES dbo.FirmWideRoleDefinitions(Id,ShaleClientId),
   CONSTRAINT FK_UserFirmWideRoleAssignments_CreatedByTenant FOREIGN KEY(CreatedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT FK_UserFirmWideRoleAssignments_DeletedByTenant FOREIGN KEY(DeletedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
   CONSTRAINT CK_UserFirmWideRoleAssignments_Lifecycle CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
  );
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') AND name=N'UX_UserFirmWideRoleAssignments_Active')
  CREATE UNIQUE INDEX UX_UserFirmWideRoleAssignments_Active ON dbo.UserFirmWideRoleAssignments(ShaleClientId,UserId,FirmWideRoleDefinitionId) WHERE IsDeleted=0;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') AND name=N'IX_UserFirmWideRoleAssignments_Eligibility')
  CREATE INDEX IX_UserFirmWideRoleAssignments_Eligibility ON dbo.UserFirmWideRoleAssignments(ShaleClientId,FirmWideRoleDefinitionId,UserId,IsDeleted);

 /* Built-ins must never acquire a second assignment authority. */
 IF OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns',N'TR') IS NULL
  EXEC(N'CREATE TRIGGER dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns ON dbo.UserFirmWideRoleAssignments AFTER INSERT,UPDATE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId WHERE d.SystemKey IN (''ADMIN'',''ATTORNEY'')) THROW 56806,''ADMIN and ATTORNEY assignments must be changed through Users legacy flags.'',1; END');

 DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.FirmWideRoleDefinitions'),OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')) AND predicate_type_desc=N'FILTER' AND object_id<>@policyId)
  THROW 56807,'A firm-wide role table is registered under an unexpected RLS policy.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND predicate_type_desc=N'FILTER')
  EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.FirmWideRoleDefinitions;');
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') AND predicate_type_desc=N'FILTER')
  EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserFirmWideRoleAssignments;');
 IF (SELECT COUNT(*) FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id IN(OBJECT_ID(N'dbo.FirmWideRoleDefinitions'),OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')) AND predicate_type_desc=N'FILTER')<>2
  THROW 56808,'Strict RLS was not installed on both firm-wide role tables.',1;

 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;

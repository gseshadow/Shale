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

/* On a reviewed execution copy, replace both inventory values and set the acknowledgement
   only after the independent all-tenant visibility preflight described in the architecture. */
DECLARE @ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE';
DECLARE @ExpectedTenantCount int=0;
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF @ExpectedDatabase=N'REPLACE_WITH_APPROVED_DATABASE' OR DB_NAME()<>@ExpectedDatabase
 THROW 56810,'Set and verify the approved deployment database before execution.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 56811,'All-tenant deployment requires NULL ShaleClientId and PrincipalUserId session context; do not clear retained application context automatically.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 56812,'Use an approved all-tenant administrative principal, never an application principal.',1;
IF @OperatorVerifiedAllTenantVisibility<>1
 THROW 56813,'Operator acknowledgement is required only after independent all-tenant visibility preflight and inventory reconciliation.',1;
IF @ExpectedTenantCount<=0 THROW 56814,'Set ExpectedTenantCount from the independently approved tenant inventory.',1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 56800,'Required ShaleClients table is missing.',1;
DECLARE @VisibleTenantCount int=(SELECT COUNT(*) FROM dbo.ShaleClients);
IF @VisibleTenantCount<>@ExpectedTenantCount
 THROW 56815,'Visible tenant count does not match the independently approved expected tenant count; stop before writes.',1;

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
   CONSTRAINT CK_FirmWideRoleDefinitions_SystemKey CHECK(SystemKey COLLATE Latin1_General_100_BIN2=UPPER(SystemKey) COLLATE Latin1_General_100_BIN2 AND SystemKey COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%' AND LEN(SystemKey)>0),
   CONSTRAINT CK_FirmWideRoleDefinitions_Name CHECK(LEN(LTRIM(RTRIM(Name)))>0),
   CONSTRAINT CK_FirmWideRoleDefinitions_Lifecycle CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
  );
 END;

 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'UX_FirmWideRoleDefinitions_Tenant_SystemKey')
  CREATE UNIQUE INDEX UX_FirmWideRoleDefinitions_Tenant_SystemKey ON dbo.FirmWideRoleDefinitions(ShaleClientId,SystemKey);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'UX_FirmWideRoleDefinitions_Id_Tenant')
  CREATE UNIQUE INDEX UX_FirmWideRoleDefinitions_Id_Tenant ON dbo.FirmWideRoleDefinitions(Id,ShaleClientId);

 /* Fail rather than silently reinterpret an existing built-in identity. */
 IF EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions WHERE SystemKey='ADMIN' AND (Name<>N'Administrator' OR IsActive<>1 OR IsDeleted<>0 OR DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL))
  THROW 56804,'Existing ADMIN firm-wide role is incompatible or inactive/deleted.',1;
 IF EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions WHERE SystemKey='ATTORNEY' AND (Name<>N'Attorney' OR IsActive<>1 OR IsDeleted<>0 OR DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL))
  THROW 56805,'Existing ATTORNEY firm-wide role is incompatible or inactive/deleted.',1;
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

 /* Fail closed when a prior/partial deployment left objects with familiar names but incompatible shape. */
 DECLARE @ExpectedColumns table(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit);
 INSERT @ExpectedColumns VALUES
 (N'FirmWideRoleDefinitions',N'Id',N'int',4,0,1),(N'FirmWideRoleDefinitions',N'ShaleClientId',N'int',4,0,0),(N'FirmWideRoleDefinitions',N'SystemKey',N'varchar',64,0,0),
 (N'FirmWideRoleDefinitions',N'Name',N'nvarchar',200,0,0),(N'FirmWideRoleDefinitions',N'Description',N'nvarchar',1020,1,0),(N'FirmWideRoleDefinitions',N'SortOrder',N'int',4,0,0),
 (N'FirmWideRoleDefinitions',N'IsActive',N'bit',1,0,0),(N'FirmWideRoleDefinitions',N'IsDeleted',N'bit',1,0,0),(N'FirmWideRoleDefinitions',N'CreatedAt',N'datetime2',8,0,0),
 (N'FirmWideRoleDefinitions',N'CreatedByUserId',N'int',4,1,0),(N'FirmWideRoleDefinitions',N'UpdatedAt',N'datetime2',8,1,0),(N'FirmWideRoleDefinitions',N'UpdatedByUserId',N'int',4,1,0),
 (N'FirmWideRoleDefinitions',N'DeletedAt',N'datetime2',8,1,0),(N'FirmWideRoleDefinitions',N'DeletedByUserId',N'int',4,1,0),(N'FirmWideRoleDefinitions',N'RowVer',N'timestamp',8,0,0),
 (N'UserFirmWideRoleAssignments',N'Id',N'bigint',8,0,1),(N'UserFirmWideRoleAssignments',N'ShaleClientId',N'int',4,0,0),(N'UserFirmWideRoleAssignments',N'UserId',N'int',4,0,0),
 (N'UserFirmWideRoleAssignments',N'FirmWideRoleDefinitionId',N'int',4,0,0),(N'UserFirmWideRoleAssignments',N'IsDeleted',N'bit',1,0,0),(N'UserFirmWideRoleAssignments',N'CreatedAt',N'datetime2',8,0,0),
 (N'UserFirmWideRoleAssignments',N'CreatedByUserId',N'int',4,0,0),(N'UserFirmWideRoleAssignments',N'DeletedAt',N'datetime2',8,1,0),(N'UserFirmWideRoleAssignments',N'DeletedByUserId',N'int',4,1,0),
 (N'UserFirmWideRoleAssignments',N'RowVer',N'timestamp',8,0,0);
 IF EXISTS(SELECT 1 FROM @ExpectedColumns e LEFT JOIN sys.tables st ON st.name=e.TableName AND SCHEMA_NAME(st.schema_id)=N'dbo' LEFT JOIN sys.columns c ON c.object_id=st.object_id AND c.name=e.ColumnName LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR ty.name<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity)
  THROW 56816,'A required firm-wide role column is missing or incompatible.',1;

 DECLARE @RequiredConstraints table(TableName sysname,ConstraintName sysname,ConstraintKind char(2));
 INSERT @RequiredConstraints VALUES
 (N'FirmWideRoleDefinitions',N'PK_FirmWideRoleDefinitions','PK'),(N'FirmWideRoleDefinitions',N'FK_FirmWideRoleDefinitions_Client','F'),
 (N'FirmWideRoleDefinitions',N'FK_FirmWideRoleDefinitions_CreatedByTenant','F'),(N'FirmWideRoleDefinitions',N'FK_FirmWideRoleDefinitions_UpdatedByTenant','F'),(N'FirmWideRoleDefinitions',N'FK_FirmWideRoleDefinitions_DeletedByTenant','F'),
 (N'FirmWideRoleDefinitions',N'CK_FirmWideRoleDefinitions_SystemKey','C'),(N'FirmWideRoleDefinitions',N'CK_FirmWideRoleDefinitions_Name','C'),(N'FirmWideRoleDefinitions',N'CK_FirmWideRoleDefinitions_Lifecycle','C'),
 (N'UserFirmWideRoleAssignments',N'PK_UserFirmWideRoleAssignments','PK'),(N'UserFirmWideRoleAssignments',N'FK_UserFirmWideRoleAssignments_Client','F'),
 (N'UserFirmWideRoleAssignments',N'FK_UserFirmWideRoleAssignments_UserTenant','F'),(N'UserFirmWideRoleAssignments',N'FK_UserFirmWideRoleAssignments_DefinitionTenant','F'),
 (N'UserFirmWideRoleAssignments',N'FK_UserFirmWideRoleAssignments_CreatedByTenant','F'),(N'UserFirmWideRoleAssignments',N'FK_UserFirmWideRoleAssignments_DeletedByTenant','F'),
 (N'UserFirmWideRoleAssignments',N'CK_UserFirmWideRoleAssignments_Lifecycle','C');
 IF EXISTS(SELECT 1 FROM @RequiredConstraints e LEFT JOIN sys.tables t ON t.name=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo' LEFT JOIN sys.objects o ON o.parent_object_id=t.object_id AND o.name=e.ConstraintName AND o.type=e.ConstraintKind WHERE o.object_id IS NULL)
  THROW 56818,'A required firm-wide role constraint is missing or has the wrong kind.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys fk WHERE fk.parent_object_id IN(OBJECT_ID(N'dbo.FirmWideRoleDefinitions'),OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')) AND (fk.is_disabled=1 OR fk.is_not_trusted=1))
  THROW 56819,'Firm-wide role foreign keys must be enabled and trusted.',1;
 DECLARE @ExpectedForeignKeyColumns table(ConstraintName sysname,Ordinal int,ParentColumn sysname,ReferencedTable sysname,ReferencedColumn sysname);
 INSERT @ExpectedForeignKeyColumns VALUES
 (N'FK_FirmWideRoleDefinitions_Client',1,N'ShaleClientId',N'ShaleClients',N'Id'),
 (N'FK_FirmWideRoleDefinitions_CreatedByTenant',1,N'CreatedByUserId',N'Users',N'id'),(N'FK_FirmWideRoleDefinitions_CreatedByTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId'),
 (N'FK_FirmWideRoleDefinitions_UpdatedByTenant',1,N'UpdatedByUserId',N'Users',N'id'),(N'FK_FirmWideRoleDefinitions_UpdatedByTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId'),
 (N'FK_FirmWideRoleDefinitions_DeletedByTenant',1,N'DeletedByUserId',N'Users',N'id'),(N'FK_FirmWideRoleDefinitions_DeletedByTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId'),
 (N'FK_UserFirmWideRoleAssignments_Client',1,N'ShaleClientId',N'ShaleClients',N'Id'),
 (N'FK_UserFirmWideRoleAssignments_UserTenant',1,N'UserId',N'Users',N'id'),(N'FK_UserFirmWideRoleAssignments_UserTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId'),
 (N'FK_UserFirmWideRoleAssignments_DefinitionTenant',1,N'FirmWideRoleDefinitionId',N'FirmWideRoleDefinitions',N'Id'),(N'FK_UserFirmWideRoleAssignments_DefinitionTenant',2,N'ShaleClientId',N'FirmWideRoleDefinitions',N'ShaleClientId'),
 (N'FK_UserFirmWideRoleAssignments_CreatedByTenant',1,N'CreatedByUserId',N'Users',N'id'),(N'FK_UserFirmWideRoleAssignments_CreatedByTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId'),
 (N'FK_UserFirmWideRoleAssignments_DeletedByTenant',1,N'DeletedByUserId',N'Users',N'id'),(N'FK_UserFirmWideRoleAssignments_DeletedByTenant',2,N'ShaleClientId',N'Users',N'ShaleClientId');
 IF EXISTS(SELECT ConstraintName,Ordinal,ParentColumn,ReferencedTable,ReferencedColumn FROM @ExpectedForeignKeyColumns EXCEPT SELECT fk.name,fkc.constraint_column_id,pc.name,rt.name,rc.name FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns pc ON pc.object_id=fk.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.tables rt ON rt.object_id=fk.referenced_object_id JOIN sys.columns rc ON rc.object_id=fk.referenced_object_id AND rc.column_id=fkc.referenced_column_id)
    OR EXISTS(SELECT fk.name,fkc.constraint_column_id,pc.name,rt.name,rc.name FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns pc ON pc.object_id=fk.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.tables rt ON rt.object_id=fk.referenced_object_id JOIN sys.columns rc ON rc.object_id=fk.referenced_object_id AND rc.column_id=fkc.referenced_column_id WHERE fk.name IN(SELECT ConstraintName FROM @ExpectedForeignKeyColumns) EXCEPT SELECT ConstraintName,Ordinal,ParentColumn,ReferencedTable,ReferencedColumn FROM @ExpectedForeignKeyColumns)
  THROW 56824,'A firm-wide role foreign key has incompatible columns or target.',1;
 IF EXISTS(SELECT 1 FROM sys.check_constraints cc WHERE cc.parent_object_id IN(OBJECT_ID(N'dbo.FirmWideRoleDefinitions'),OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')) AND (cc.is_disabled=1 OR cc.is_not_trusted=1))
  THROW 56820,'Firm-wide role checks must be enabled and trusted.',1;
 DECLARE @ExpectedDefaults table(TableName sysname,ConstraintName sysname,ColumnName sysname,NormalizedDefinition nvarchar(100));
 INSERT @ExpectedDefaults VALUES
 (N'FirmWideRoleDefinitions',N'DF_FirmWideRoleDefinitions_SortOrder',N'SortOrder',N'((0))'),
 (N'FirmWideRoleDefinitions',N'DF_FirmWideRoleDefinitions_IsActive',N'IsActive',N'((1))'),
 (N'FirmWideRoleDefinitions',N'DF_FirmWideRoleDefinitions_IsDeleted',N'IsDeleted',N'((0))'),
 (N'FirmWideRoleDefinitions',N'DF_FirmWideRoleDefinitions_CreatedAt',N'CreatedAt',N'(SYSUTCDATETIME())'),
 (N'UserFirmWideRoleAssignments',N'DF_UserFirmWideRoleAssignments_IsDeleted',N'IsDeleted',N'((0))'),
 (N'UserFirmWideRoleAssignments',N'DF_UserFirmWideRoleAssignments_CreatedAt',N'CreatedAt',N'(SYSUTCDATETIME())');
 IF EXISTS(SELECT 1 FROM @ExpectedDefaults e LEFT JOIN sys.tables t ON t.name=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo' LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name=e.ColumnName LEFT JOIN sys.default_constraints dc ON dc.parent_object_id=t.object_id AND dc.parent_column_id=c.column_id AND dc.name=e.ConstraintName WHERE dc.object_id IS NULL OR UPPER(REPLACE(dc.definition,N' ',N''))<>e.NormalizedDefinition)
  THROW 56826,'A required firm-wide role default constraint is missing or incompatible.',1;
 DECLARE @SystemKeyCheck nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'CK_FirmWideRoleDefinitions_SystemKey');
 IF @SystemKeyCheck NOT LIKE N'%Latin1_General_100_BIN2%' OR @SystemKeyCheck NOT LIKE N'%UPPER%' OR CHARINDEX(N'%[^A-Z0-9_]%',@SystemKeyCheck)=0 OR @SystemKeyCheck NOT LIKE N'%LEN%'
  THROW 56821,'SystemKey check must enforce uppercase syntax with a binary collation.',1;
 DECLARE @DefinitionLifecycleCheck nvarchar(max)=(SELECT UPPER(REPLACE(REPLACE(REPLACE(definition,N'[',N''),N']',N''),N' ',N'')) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND name=N'CK_FirmWideRoleDefinitions_Lifecycle');
 DECLARE @AssignmentLifecycleCheck nvarchar(max)=(SELECT UPPER(REPLACE(REPLACE(REPLACE(definition,N'[',N''),N']',N''),N' ',N'')) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') AND name=N'CK_UserFirmWideRoleAssignments_Lifecycle');
 IF @DefinitionLifecycleCheck NOT LIKE N'%ISDELETED=(0)%DELETEDATISNULL%DELETEDBYUSERIDISNULL%' OR @DefinitionLifecycleCheck NOT LIKE N'%ISDELETED=(1)%ISACTIVE=(0)%DELETEDATISNOTNULL%DELETEDBYUSERIDISNOTNULL%' OR @AssignmentLifecycleCheck NOT LIKE N'%ISDELETED=(0)%DELETEDATISNULL%DELETEDBYUSERIDISNULL%' OR @AssignmentLifecycleCheck NOT LIKE N'%ISDELETED=(1)%DELETEDATISNOTNULL%DELETEDBYUSERIDISNOTNULL%'
  THROW 56825,'A firm-wide role lifecycle check is incompatible.',1;

 DECLARE @ExpectedIndexes table(TableName sysname,IndexName sysname,IsUnique bit,FilterText nvarchar(200),KeyColumns nvarchar(400));
 INSERT @ExpectedIndexes VALUES
 (N'FirmWideRoleDefinitions',N'PK_FirmWideRoleDefinitions',1,NULL,N'Id'),
 (N'FirmWideRoleDefinitions',N'UX_FirmWideRoleDefinitions_Tenant_SystemKey',1,NULL,N'ShaleClientId,SystemKey'),
 (N'FirmWideRoleDefinitions',N'UX_FirmWideRoleDefinitions_Id_Tenant',1,NULL,N'Id,ShaleClientId'),
 (N'UserFirmWideRoleAssignments',N'PK_UserFirmWideRoleAssignments',1,NULL,N'Id'),
 (N'UserFirmWideRoleAssignments',N'UX_UserFirmWideRoleAssignments_Active',1,N'(IsDeleted=(0))',N'ShaleClientId,UserId,FirmWideRoleDefinitionId'),
 (N'UserFirmWideRoleAssignments',N'IX_UserFirmWideRoleAssignments_Eligibility',0,NULL,N'ShaleClientId,FirmWideRoleDefinitionId,UserId,IsDeleted');
 IF EXISTS(SELECT 1 FROM @ExpectedIndexes e LEFT JOIN sys.tables t ON t.name=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo' LEFT JOIN sys.indexes i ON i.object_id=t.object_id AND i.name=e.IndexName OUTER APPLY(SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)k WHERE i.index_id IS NULL OR i.is_unique<>e.IsUnique OR i.is_disabled=1 OR k.KeyColumns<>e.KeyColumns OR ISNULL(REPLACE(REPLACE(REPLACE(i.filter_definition,N'[',N''),N']',N''),N' ',N''),N'')<>ISNULL(e.FilterText,N''))
  THROW 56822,'A required firm-wide role index is missing or incompatible.',1;

 /* Built-ins must never acquire a second assignment authority. */
 IF OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns',N'TR') IS NULL
  EXEC(N'CREATE TRIGGER dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns ON dbo.UserFirmWideRoleAssignments AFTER INSERT,UPDATE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId WHERE d.SystemKey IN (''ADMIN'',''ATTORNEY'')) THROW 56806,''ADMIN and ATTORNEY assignments must be changed through Users legacy flags.'',1; END');
 IF (SELECT COUNT(*) FROM sys.trigger_events WHERE object_id=OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns') AND type_desc IN(N'INSERT',N'UPDATE'))<>2 OR EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns') AND (parent_id<>OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') OR is_disabled=1)) OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns')) NOT LIKE N'%SystemKey IN (''ADMIN'',''ATTORNEY'')%' OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns')) NOT LIKE N'%THROW 56806%'
  THROW 56823,'The built-in assignment guard trigger is missing, disabled, or incompatible.',1;

 DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.FirmWideRoleDefinitions'),OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')) AND predicate_type_desc=N'FILTER' AND object_id<>@policyId)
  THROW 56807,'A firm-wide role table is registered under an unexpected RLS policy.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.FirmWideRoleDefinitions') AND predicate_type_desc=N'FILTER')
  EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.FirmWideRoleDefinitions;');
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.UserFirmWideRoleAssignments') AND predicate_type_desc=N'FILTER')
  EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserFirmWideRoleAssignments;');
 IF EXISTS(SELECT 1 FROM (VALUES(OBJECT_ID(N'dbo.FirmWideRoleDefinitions')),(OBJECT_ID(N'dbo.UserFirmWideRoleAssignments')))e(ObjectId) OUTER APPLY(SELECT COUNT(*) PredicateCount,MIN(UPPER(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''))) PredicateDefinition FROM sys.security_predicates p WHERE p.object_id=@policyId AND p.target_object_id=e.ObjectId AND p.predicate_type_desc=N'FILTER')x WHERE x.PredicateCount<>1 OR x.PredicateDefinition NOT IN(N'SEC.FN_FILTERBYTENANT(SHALECLIENTID)',N'(SEC.FN_FILTERBYTENANT(SHALECLIENTID))'))
  THROW 56808,'Each firm-wide role table must have exactly the established strict tenant filter predicate.',1;

 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;

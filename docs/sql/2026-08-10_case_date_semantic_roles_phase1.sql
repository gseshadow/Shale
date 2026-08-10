SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56800, 'Missing dbo.CaseDateTypes prerequisite.', 1;

IF OBJECT_ID(N'dbo.CaseDateSemanticRoles', N'U') IS NULL
BEGIN
 CREATE TABLE dbo.CaseDateSemanticRoles(
  RoleKey nvarchar(64) NOT NULL CONSTRAINT PK_CaseDateSemanticRoles PRIMARY KEY,
  IsProtected bit NOT NULL CONSTRAINT DF_CaseDateSemanticRoles_IsProtected DEFAULT(1),
  CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseDateSemanticRoles_CreatedAt DEFAULT SYSUTCDATETIME(),
  RowVer rowversion NOT NULL,
  CONSTRAINT CK_CaseDateSemanticRoles_RoleKey CHECK(RoleKey IN(N'INTAKE',N'STATUTE_OF_LIMITATIONS',N'TORT_NOTICE_DEADLINE'))
 );
END;

MERGE dbo.CaseDateSemanticRoles WITH (HOLDLOCK) AS target
USING (VALUES(N'INTAKE'),(N'STATUTE_OF_LIMITATIONS'),(N'TORT_NOTICE_DEADLINE')) seed(RoleKey)
ON target.RoleKey=seed.RoleKey
WHEN NOT MATCHED THEN INSERT(RoleKey,IsProtected) VALUES(seed.RoleKey,1);

IF EXISTS(SELECT 1 FROM dbo.CaseDateSemanticRoles WHERE RoleKey IN(N'INTAKE',N'STATUTE_OF_LIMITATIONS',N'TORT_NOTICE_DEADLINE') AND IsProtected<>1)
 THROW 56801, 'Protected Case Date semantic role definition conflicts with the expected contract.', 1;

IF OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings', N'U') IS NULL
BEGIN
 CREATE TABLE dbo.CaseDateTypeSemanticRoleMappings(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseDateTypeSemanticRoleMappings PRIMARY KEY,
  ShaleClientId int NULL,
  SemanticRoleKey nvarchar(64) NOT NULL,
  CaseDateTypeId int NOT NULL,
  IsActive bit NOT NULL CONSTRAINT DF_CaseDateTypeSemanticRoleMappings_IsActive DEFAULT(1),
  IsDeleted bit NOT NULL CONSTRAINT DF_CaseDateTypeSemanticRoleMappings_IsDeleted DEFAULT(0),
  CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseDateTypeSemanticRoleMappings_CreatedAt DEFAULT SYSUTCDATETIME(),
  CreatedByUserId int NULL, UpdatedAt datetime2 NULL, UpdatedByUserId int NULL,
  DeletedAt datetime2 NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_Role FOREIGN KEY(SemanticRoleKey) REFERENCES dbo.CaseDateSemanticRoles(RoleKey),
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_Type FOREIGN KEY(CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id),
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_Tenant FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(Id),
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(Id),
  CONSTRAINT FK_CaseDateTypeSemanticRoleMappings_DeletedBy FOREIGN KEY(DeletedByUserId) REFERENCES dbo.Users(Id)
 );
END;

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings') AND name=N'UX_CaseDateRoleMappings_Global_Active')
 CREATE UNIQUE INDEX UX_CaseDateRoleMappings_Global_Active ON dbo.CaseDateTypeSemanticRoleMappings(SemanticRoleKey) WHERE ShaleClientId IS NULL AND IsActive=1 AND IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings') AND name=N'UX_CaseDateRoleMappings_Tenant_Active')
 CREATE UNIQUE INDEX UX_CaseDateRoleMappings_Tenant_Active ON dbo.CaseDateTypeSemanticRoleMappings(ShaleClientId,SemanticRoleKey) WHERE ShaleClientId IS NOT NULL AND IsActive=1 AND IsDeleted=0;

DECLARE @Expected TABLE(SystemKey nvarchar(100) PRIMARY KEY,RoleKey nvarchar(64) UNIQUE);
INSERT @Expected VALUES(N'intake',N'INTAKE'),(N'statute_of_limitations',N'STATUTE_OF_LIMITATIONS'),(N'tort_notice_deadline',N'TORT_NOTICE_DEADLINE');
IF EXISTS(SELECT 1 FROM @Expected e LEFT JOIN dbo.CaseDateTypes t ON t.ShaleClientId IS NULL AND t.SystemKey=e.SystemKey AND t.IsActive=1 AND t.IsDeleted=0 GROUP BY e.SystemKey HAVING COUNT(t.Id)<>1)
 THROW 56802, 'Expected exactly one active global compatibility Case Date Type for each protected semantic role.', 1;
IF EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m JOIN @Expected e ON e.RoleKey=m.SemanticRoleKey JOIN dbo.CaseDateTypes expected_type ON expected_type.ShaleClientId IS NULL AND expected_type.SystemKey=e.SystemKey WHERE m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0 AND m.CaseDateTypeId<>expected_type.Id)
 THROW 56803, 'Existing global semantic-role mapping conflicts with its compatibility Case Date Type.', 1;
INSERT dbo.CaseDateTypeSemanticRoleMappings(ShaleClientId,SemanticRoleKey,CaseDateTypeId)
SELECT NULL,e.RoleKey,t.Id FROM @Expected e JOIN dbo.CaseDateTypes t ON t.ShaleClientId IS NULL AND t.SystemKey=e.SystemKey AND t.IsActive=1 AND t.IsDeleted=0
WHERE NOT EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.ShaleClientId IS NULL AND m.SemanticRoleKey=e.RoleKey AND m.IsActive=1 AND m.IsDeleted=0);

DECLARE @PolicyObjectId int=(SELECT TOP(1) object_id FROM sys.security_policies WHERE name=N'TenantFilter');
IF @PolicyObjectId IS NULL THROW 56804, 'Missing established TenantFilter security policy.', 1;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyObjectId AND target_object_id=OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings') AND predicate_type_desc=N'FILTER')
BEGIN
 DECLARE @PolicyQualified nvarchar(517)=QUOTENAME(OBJECT_SCHEMA_NAME(@PolicyObjectId))+N'.'+QUOTENAME(OBJECT_NAME(@PolicyObjectId));
 DECLARE @Sql nvarchar(max)=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.CaseDateTypeSemanticRoleMappings;';
 EXEC sys.sp_executesql @Sql;
END;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

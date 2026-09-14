/* Phase 1 Case Team Role definitions. Guarded/rerunnable; does not alter dbo.CaseUsers assignments. */
SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.CaseUsers',N'U') IS NULL OR OBJECT_ID(N'dbo.Roles',N'U') IS NULL THROW 56500,'Legacy Case Team schema is missing.',1;
 IF OBJECT_ID(N'dbo.CaseTeamRoleDefinitions',N'U') IS NULL
 CREATE TABLE dbo.CaseTeamRoleDefinitions(
  Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseTeamRoleDefinitions PRIMARY KEY,
  ShaleClientId int NULL, SystemKey varchar(64) NULL, LegacyRoleId int NULL,
  Name nvarchar(200) NOT NULL, Description nvarchar(510) NULL,
  Color nvarchar(20) NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_Color DEFAULT(N'#6C757D'),
  SortOrder int NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_SortOrder DEFAULT(0),
  IsActive bit NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_IsActive DEFAULT(1),
  IsDeleted bit NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_IsDeleted DEFAULT(0),
  IsProtected bit NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_IsProtected DEFAULT(0),
  CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseTeamRoleDefinitions_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
  UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, DeletedAt datetime2 NULL, DeletedByUserId int NULL,
  RowVer rowversion NOT NULL,
  CONSTRAINT FK_CaseTeamRoleDefinitions_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseTeamRoleDefinitions_LegacyRole FOREIGN KEY(LegacyRoleId) REFERENCES dbo.Roles(Id),
  CONSTRAINT FK_CaseTeamRoleDefinitions_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT FK_CaseTeamRoleDefinitions_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT FK_CaseTeamRoleDefinitions_DeletedBy FOREIGN KEY(DeletedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT CK_CaseTeamRoleDefinitions_Name CHECK(LEN(LTRIM(RTRIM(Name))) BETWEEN 1 AND 200),
  CONSTRAINT CK_CaseTeamRoleDefinitions_Color CHECK(Color LIKE N'#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]'),
  CONSTRAINT CK_CaseTeamRoleDefinitions_Lifecycle CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)),
  CONSTRAINT CK_CaseTeamRoleDefinitions_System CHECK((ShaleClientId IS NULL AND SystemKey IS NOT NULL AND IsProtected=1 AND LegacyRoleId IS NOT NULL) OR ShaleClientId IS NOT NULL)
 );
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND name=N'UX_CaseTeamRoleDefinitions_GlobalSystemKey')
  CREATE UNIQUE INDEX UX_CaseTeamRoleDefinitions_GlobalSystemKey ON dbo.CaseTeamRoleDefinitions(SystemKey) WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND name=N'UX_CaseTeamRoleDefinitions_TenantSystemKey')
  CREATE UNIQUE INDEX UX_CaseTeamRoleDefinitions_TenantSystemKey ON dbo.CaseTeamRoleDefinitions(ShaleClientId,SystemKey) WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND name=N'UX_CaseTeamRoleDefinitions_GlobalLegacyRoleId')
  CREATE UNIQUE INDEX UX_CaseTeamRoleDefinitions_GlobalLegacyRoleId ON dbo.CaseTeamRoleDefinitions(LegacyRoleId) WHERE ShaleClientId IS NULL AND LegacyRoleId IS NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND name=N'IX_CaseTeamRoleDefinitions_TenantOrder')
  CREATE INDEX IX_CaseTeamRoleDefinitions_TenantOrder ON dbo.CaseTeamRoleDefinitions(ShaleClientId,IsDeleted,IsActive,SortOrder,Name);
 DECLARE @seed TABLE(SystemKey varchar(64),LegacyRoleId int,LegacyName nvarchar(200),Name nvarchar(200),Description nvarchar(510),SortOrder int);
 INSERT @seed VALUES
  ('responsible_attorney',4,N'responsible_attorney',N'Responsible Attorney',N'Attorney responsible for the case.',10),
  ('prelitigation_staff',5,N'prelitigation',N'Prelitigation Staff',NULL,20),
  ('attorney',7,N'attorney',N'Attorney',NULL,30),
  ('legal_assistant',11,N'legal_assistant',N'Legal Assistant',NULL,40),
  ('paralegal',12,N'paralegal',N'Paralegal',NULL,50),
  ('law_clerk',13,N'law_clerk',N'Law Clerk',NULL,60),
  ('co_counsel',14,N'co_counsel',N'Co-counsel',NULL,70);
 IF EXISTS(
  SELECT 1
  FROM @seed s
  LEFT JOIN dbo.Roles r ON r.Id=s.LegacyRoleId
  WHERE r.Id IS NULL OR LOWER(LTRIM(RTRIM(r.Name)))<>s.LegacyName
 ) THROW 56501,'A legacy Case Team role ID/name mapping is missing or does not match the expected production vocabulary.',1;
 MERGE dbo.CaseTeamRoleDefinitions t USING @seed s ON t.ShaleClientId IS NULL AND t.SystemKey=s.SystemKey
 WHEN MATCHED THEN UPDATE SET LegacyRoleId=s.LegacyRoleId,IsProtected=1
 WHEN NOT MATCHED THEN INSERT(ShaleClientId,SystemKey,LegacyRoleId,Name,Description,Color,SortOrder,IsActive,IsDeleted,IsProtected) VALUES(NULL,s.SystemKey,s.LegacyRoleId,s.Name,s.Description,N'#6C757D',s.SortOrder,1,0,1);
 IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal',N'IF') IS NULL THROW 56502,'Tenant/global RLS predicate is missing.',1;
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter'); IF @policyId IS NULL THROW 56503,'TenantFilter policy is missing.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND predicate_type_desc=N'FILTER')
 BEGIN DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId); EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.CaseTeamRoleDefinitions;'); END;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;

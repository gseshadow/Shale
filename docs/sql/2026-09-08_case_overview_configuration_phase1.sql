/* Phase 1 per-case Overview date layout. Additive, guarded, and rerunnable. */
SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Cases',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL THROW 56900,'Required Case Overview dependencies are missing.',1;
 IF OBJECT_ID(N'dbo.CaseOverviewConfigurations',N'U') IS NULL CREATE TABLE dbo.CaseOverviewConfigurations(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseOverviewConfigurations PRIMARY KEY, ShaleClientId int NOT NULL, CaseId int NOT NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_CaseOverviewConfigurations_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NOT NULL,
  UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
  CONSTRAINT FK_CaseOverviewConfigurations_CaseTenant FOREIGN KEY(ShaleClientId,CaseId) REFERENCES dbo.Cases(ShaleClientId,Id), CONSTRAINT FK_CaseOverviewConfigurations_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseOverviewConfigurations_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id), CONSTRAINT FK_CaseOverviewConfigurations_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT UQ_CaseOverviewConfigurations_TenantCase UNIQUE(ShaleClientId,CaseId), CONSTRAINT UQ_CaseOverviewConfigurations_TenantId UNIQUE(ShaleClientId,Id));
 IF OBJECT_ID(N'dbo.CaseOverviewDateSelections',N'U') IS NULL CREATE TABLE dbo.CaseOverviewDateSelections(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseOverviewDateSelections PRIMARY KEY, ShaleClientId int NOT NULL, CaseOverviewConfigurationId bigint NOT NULL, CaseDateTypeId int NOT NULL, SortOrder int NOT NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_CaseOverviewDateSelections_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NOT NULL,
  CONSTRAINT FK_CaseOverviewDateSelections_ConfigTenant FOREIGN KEY(ShaleClientId,CaseOverviewConfigurationId) REFERENCES dbo.CaseOverviewConfigurations(ShaleClientId,Id) ON DELETE CASCADE, CONSTRAINT FK_CaseOverviewDateSelections_Type FOREIGN KEY(CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id),
  CONSTRAINT FK_CaseOverviewDateSelections_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id), CONSTRAINT FK_CaseOverviewDateSelections_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT CK_CaseOverviewDateSelections_SortOrder CHECK(SortOrder>=0), CONSTRAINT UQ_CaseOverviewDateSelections_ConfigType UNIQUE(CaseOverviewConfigurationId,CaseDateTypeId), CONSTRAINT UQ_CaseOverviewDateSelections_ConfigOrder UNIQUE(CaseOverviewConfigurationId,SortOrder));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseOverviewDateSelections') AND name=N'IX_CaseOverviewDateSelections_TenantConfigOrder') CREATE INDEX IX_CaseOverviewDateSelections_TenantConfigOrder ON dbo.CaseOverviewDateSelections(ShaleClientId,CaseOverviewConfigurationId,SortOrder) INCLUDE(CaseDateTypeId);
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 56901,'Strict tenant RLS predicate is missing.',1;
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter'); IF @policyId IS NULL THROW 56902,'TenantFilter policy is missing.',1;
 DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseOverviewConfigurations') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseOverviewConfigurations;');
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseOverviewDateSelections') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseOverviewDateSelections;');
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
SELECT t.name AS TableName,p.predicate_type_desc FROM sys.tables t LEFT JOIN sys.security_predicates p ON p.target_object_id=t.object_id WHERE t.name IN(N'CaseOverviewConfigurations',N'CaseOverviewDateSelections');

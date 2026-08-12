/* Generic tenant-owned form configuration foundation. No seeds or runtime/UI changes. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 55800,'Required dbo.ShaleClients is missing.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL THROW 55801,'Required dbo.Users is missing.',1;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 55802,'Required dbo.CaseDateTypes is missing.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 55803,'Required strict tenant predicate is missing.',1;
DECLARE @PolicyObjectId int,@PolicyQualified nvarchar(517),@Sql nvarchar(max);
SELECT @PolicyObjectId=object_id,@PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyObjectId IS NULL THROW 55804,'Enabled TenantFilter policy is missing.',1;

IF OBJECT_ID(N'dbo.FormConfigurations',N'U') IS NULL CREATE TABLE dbo.FormConfigurations(
 Id bigint IDENTITY CONSTRAINT PK_FormConfigurations PRIMARY KEY, ShaleClientId int NOT NULL,
 FormKey varchar(64) NOT NULL, IsDeleted bit NOT NULL CONSTRAINT DF_FormConfigurations_IsDeleted DEFAULT(0),
 DeletedAt datetime2 NULL,DeletedByUserId int NULL,CreatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfigurations_CreatedAt DEFAULT SYSUTCDATETIME(),
 CreatedByUserId int NOT NULL,UpdatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfigurations_UpdatedAt DEFAULT SYSUTCDATETIME(),UpdatedByUserId int NOT NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_FormConfigurations_FormKey CHECK(FormKey IN('NEW_INTAKE')),
 CONSTRAINT CK_FormConfigurations_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

IF OBJECT_ID(N'dbo.FormConfigurationSections',N'U') IS NULL CREATE TABLE dbo.FormConfigurationSections(
 Id bigint IDENTITY CONSTRAINT PK_FormConfigurationSections PRIMARY KEY,ShaleClientId int NOT NULL,FormConfigurationId bigint NOT NULL,
 SectionKey varchar(128) NOT NULL,Title nvarchar(200) NOT NULL,SortOrder int NOT NULL,IsEnabled bit NOT NULL,IsVisible bit NOT NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfigurationSections_CreatedAt DEFAULT SYSUTCDATETIME(),CreatedByUserId int NOT NULL,
 UpdatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfigurationSections_UpdatedAt DEFAULT SYSUTCDATETIME(),UpdatedByUserId int NOT NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_FormConfigurationSections_Order CHECK(SortOrder>=0));

IF OBJECT_ID(N'dbo.FormConfiguredFields',N'U') IS NULL CREATE TABLE dbo.FormConfiguredFields(
 Id bigint IDENTITY CONSTRAINT PK_FormConfiguredFields PRIMARY KEY,ShaleClientId int NOT NULL,FormConfigurationId bigint NOT NULL,FormConfigurationSectionId bigint NOT NULL,
 FieldKey varchar(128) NOT NULL,FieldKind varchar(32) NOT NULL,CaseDateTypeId int NULL,SortOrder int NOT NULL,
 IsEnabled bit NOT NULL,IsVisible bit NOT NULL,IsRequired bit NOT NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfiguredFields_CreatedAt DEFAULT SYSUTCDATETIME(),CreatedByUserId int NOT NULL,
 UpdatedAt datetime2 NOT NULL CONSTRAINT DF_FormConfiguredFields_UpdatedAt DEFAULT SYSUTCDATETIME(),UpdatedByUserId int NOT NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_FormConfiguredFields_Order CHECK(SortOrder>=0),CONSTRAINT CK_FormConfiguredFields_Kind CHECK(FieldKind IN('CASE_DATE')),
 CONSTRAINT CK_FormConfiguredFields_Reference CHECK((FieldKind='CASE_DATE' AND CaseDateTypeId IS NOT NULL)));

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurations') AND name=N'UX_FormConfigurations_Tenant_FormKey') CREATE UNIQUE INDEX UX_FormConfigurations_Tenant_FormKey ON dbo.FormConfigurations(ShaleClientId,FormKey) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurations') AND name=N'UX_FormConfigurations_Tenant_Id') CREATE UNIQUE INDEX UX_FormConfigurations_Tenant_Id ON dbo.FormConfigurations(ShaleClientId,Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurationSections') AND name=N'UX_FormConfigurationSections_Tenant_Id') CREATE UNIQUE INDEX UX_FormConfigurationSections_Tenant_Id ON dbo.FormConfigurationSections(ShaleClientId,Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurationSections') AND name=N'UX_FormConfigurationSections_Tenant_Form_Id') CREATE UNIQUE INDEX UX_FormConfigurationSections_Tenant_Form_Id ON dbo.FormConfigurationSections(ShaleClientId,FormConfigurationId,Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurationSections') AND name=N'UX_FormConfigurationSections_Key') CREATE UNIQUE INDEX UX_FormConfigurationSections_Key ON dbo.FormConfigurationSections(FormConfigurationId,SectionKey);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfigurationSections') AND name=N'UX_FormConfigurationSections_Order') CREATE UNIQUE INDEX UX_FormConfigurationSections_Order ON dbo.FormConfigurationSections(FormConfigurationId,SortOrder);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfiguredFields') AND name=N'UX_FormConfiguredFields_Key') CREATE UNIQUE INDEX UX_FormConfiguredFields_Key ON dbo.FormConfiguredFields(FormConfigurationId,FieldKey);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfiguredFields') AND name=N'UX_FormConfiguredFields_SectionOrder') CREATE UNIQUE INDEX UX_FormConfiguredFields_SectionOrder ON dbo.FormConfiguredFields(FormConfigurationSectionId,SortOrder);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FormConfiguredFields') AND name=N'UX_FormConfiguredFields_CaseDate') CREATE UNIQUE INDEX UX_FormConfiguredFields_CaseDate ON dbo.FormConfiguredFields(FormConfigurationId,CaseDateTypeId) WHERE FieldKind='CASE_DATE';

IF OBJECT_ID(N'dbo.FK_FormConfigurations_Tenant',N'F') IS NULL ALTER TABLE dbo.FormConfigurations ADD CONSTRAINT FK_FormConfigurations_Tenant FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_FormConfigurationSections_FormTenant',N'F') IS NULL ALTER TABLE dbo.FormConfigurationSections ADD CONSTRAINT FK_FormConfigurationSections_FormTenant FOREIGN KEY(ShaleClientId,FormConfigurationId) REFERENCES dbo.FormConfigurations(ShaleClientId,Id) ON DELETE CASCADE;
IF OBJECT_ID(N'dbo.FK_FormConfiguredFields_FormTenant',N'F') IS NULL ALTER TABLE dbo.FormConfiguredFields ADD CONSTRAINT FK_FormConfiguredFields_FormTenant FOREIGN KEY(ShaleClientId,FormConfigurationId) REFERENCES dbo.FormConfigurations(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_FormConfiguredFields_SectionTenant',N'F') IS NULL ALTER TABLE dbo.FormConfiguredFields ADD CONSTRAINT FK_FormConfiguredFields_SectionTenant FOREIGN KEY(ShaleClientId,FormConfigurationId,FormConfigurationSectionId) REFERENCES dbo.FormConfigurationSections(ShaleClientId,FormConfigurationId,Id) ON DELETE CASCADE;
IF OBJECT_ID(N'dbo.FK_FormConfiguredFields_CaseDateType',N'F') IS NULL ALTER TABLE dbo.FormConfiguredFields ADD CONSTRAINT FK_FormConfiguredFields_CaseDateType FOREIGN KEY(CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id);

DECLARE @Table sysname; DECLARE tables CURSOR LOCAL FAST_FORWARD FOR SELECT v FROM(VALUES(N'dbo.FormConfigurations'),(N'dbo.FormConfigurationSections'),(N'dbo.FormConfiguredFields'))x(v);
OPEN tables;FETCH NEXT FROM tables INTO @Table;WHILE @@FETCH_STATUS=0 BEGIN IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyObjectId AND target_object_id=OBJECT_ID(@Table) AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON '+@Table+N';';EXEC sys.sp_executesql @Sql;END;FETCH NEXT FROM tables INTO @Table;END;CLOSE tables;DEALLOCATE tables;

/* SQL Server cannot express global-or-same-tenant CaseDateTypes as a composite FK. */
EXEC(N'CREATE OR ALTER TRIGGER dbo.TR_FormConfiguredFields_CaseDateTypeTenant ON dbo.FormConfiguredFields AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN dbo.CaseDateTypes t ON t.Id=i.CaseDateTypeId
 WHERE i.FieldKind=''''CASE_DATE'''' AND (t.Id IS NULL OR (t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>i.ShaleClientId)))
 THROW 55805,''Configured case-date type must be global or owned by the same tenant.'',1;
END');

COMMIT;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK;THROW;END CATCH;
GO

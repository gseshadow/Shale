/* Contacts Phase 1A: additive definitions, assignments, and structured-name storage.
   Forward-only and idempotent. This script deliberately leaves all legacy columns and runtime
   behavior intact. Execute only through the approved all-tenant migration connection. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Contacts',N'U') IS NULL THROW 56100, 'Required table dbo.Contacts is missing.', 1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 56101, 'Required table dbo.ShaleClients is missing.', 1;
IF COL_LENGTH(N'dbo.Contacts',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.Contacts',N'IsExpert') IS NULL
    THROW 56102, 'Contacts must expose ShaleClientId and IsExpert.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL OR OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal',N'IF') IS NULL
    THROW 56103, 'Required tenant RLS predicate functions are missing.', 1;

DECLARE @PolicyId int, @PolicyQualified nvarchar(517);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter') <> 1
    THROW 56104, 'Required security policy TenantFilter is missing or ambiguous.', 1;
SELECT @PolicyId=object_id,
       @PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 56105, 'Security policy TenantFilter is disabled.', 1;

/* Additive only: Name/FirstName/LastName/WorkName and their display behavior remain authoritative. */
IF COL_LENGTH(N'dbo.Contacts',N'Prefix') IS NULL ALTER TABLE dbo.Contacts ADD Prefix nvarchar(50) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'MiddleName') IS NULL ALTER TABLE dbo.Contacts ADD MiddleName nvarchar(100) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'PreferredName') IS NULL ALTER TABLE dbo.Contacts ADD PreferredName nvarchar(100) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'Suffix') IS NULL ALTER TABLE dbo.Contacts ADD Suffix nvarchar(50) NULL;

IF OBJECT_ID(N'dbo.ContactTypes',N'U') IS NULL CREATE TABLE dbo.ContactTypes(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactTypes PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_ContactTypes_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_ContactTypes_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactTypes_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_ContactTypes_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactTypes_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_ContactTypes_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0));

IF OBJECT_ID(N'dbo.Specialties',N'U') IS NULL CREATE TABLE dbo.Specialties(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Specialties PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_Specialties_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_Specialties_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_Specialties_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_Specialties_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_Specialties_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_Specialties_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0));

IF OBJECT_ID(N'dbo.CredentialDefinitions',N'U') IS NULL CREATE TABLE dbo.CredentialDefinitions(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CredentialDefinitions PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_CredentialDefinitions_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_CredentialDefinitions_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_CredentialDefinitions_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_CredentialDefinitions_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_CredentialDefinitions_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_CredentialDefinitions_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0));

IF OBJECT_ID(N'dbo.ContactContactTypes',N'U') IS NULL CREATE TABLE dbo.ContactContactTypes(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactContactTypes PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL, ContactTypeId int NOT NULL,
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactContactTypes_IsDeleted DEFAULT(0), CreatedAt datetime2 NOT NULL CONSTRAINT DF_ContactContactTypes_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, DeletedAt datetime2 NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactContactTypes_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));
IF OBJECT_ID(N'dbo.ContactSpecialties',N'U') IS NULL CREATE TABLE dbo.ContactSpecialties(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactSpecialties PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL, SpecialtyId int NOT NULL,
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactSpecialties_IsDeleted DEFAULT(0), CreatedAt datetime2 NOT NULL CONSTRAINT DF_ContactSpecialties_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, DeletedAt datetime2 NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactSpecialties_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));
IF OBJECT_ID(N'dbo.ContactCredentials',N'U') IS NULL CREATE TABLE dbo.ContactCredentials(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactCredentials PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL, CredentialDefinitionId int NOT NULL,
 DisplayOrder int NOT NULL CONSTRAINT DF_ContactCredentials_DisplayOrder DEFAULT(0), IsDeleted bit NOT NULL CONSTRAINT DF_ContactCredentials_IsDeleted DEFAULT(0),
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_ContactCredentials_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL, UpdatedAt datetime2 NULL, UpdatedByUserId int NULL,
 DeletedAt datetime2 NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL, CONSTRAINT CK_ContactCredentials_DisplayOrder CHECK(DisplayOrder>=0),
 CONSTRAINT CK_ContactCredentials_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

/* Idempotency refuses a differently shaped partial deployment rather than silently accepting it. */
DECLARE @Required TABLE(T sysname,C sysname); INSERT @Required VALUES
(N'ContactTypes',N'RowVer'),(N'Specialties',N'RowVer'),(N'CredentialDefinitions',N'RowVer'),
(N'ContactContactTypes',N'ShaleClientId'),(N'ContactContactTypes',N'RowVer'),(N'ContactSpecialties',N'ShaleClientId'),
(N'ContactSpecialties',N'RowVer'),(N'ContactCredentials',N'DisplayOrder'),(N'ContactCredentials',N'RowVer');
IF EXISTS(SELECT 1 FROM @Required r WHERE COL_LENGTH(N'dbo.'+r.T,r.C) IS NULL)
 THROW 56106, 'Existing Contacts Phase 1A table has an incompatible partial shape.', 1;

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'UX_Contacts_ShaleClientId_Id')
 CREATE UNIQUE INDEX UX_Contacts_ShaleClientId_Id ON dbo.Contacts(ShaleClientId,Id);
DECLARE @d sysname;
DECLARE definition_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT v FROM (VALUES(N'ContactTypes'),(N'Specialties'),(N'CredentialDefinitions'))x(v);
OPEN definition_cursor; FETCH NEXT FROM definition_cursor INTO @d;
WHILE @@FETCH_STATUS=0 BEGIN
 EXEC(N'IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.'+@d+N''') AND name=N''UX_'+@d+N'_Global_SystemKey'') CREATE UNIQUE INDEX UX_'+@d+N'_Global_SystemKey ON dbo.'+@d+N'(SystemKey) WHERE ShaleClientId IS NULL;');
 EXEC(N'IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.'+@d+N''') AND name=N''UX_'+@d+N'_Tenant_SystemKey'') CREATE UNIQUE INDEX UX_'+@d+N'_Tenant_SystemKey ON dbo.'+@d+N'(ShaleClientId,SystemKey) WHERE ShaleClientId IS NOT NULL;');
 FETCH NEXT FROM definition_cursor INTO @d; END CLOSE definition_cursor; DEALLOCATE definition_cursor;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactContactTypes') AND name=N'UX_ContactContactTypes_Active') CREATE UNIQUE INDEX UX_ContactContactTypes_Active ON dbo.ContactContactTypes(ShaleClientId,ContactId,ContactTypeId) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactSpecialties') AND name=N'UX_ContactSpecialties_Active') CREATE UNIQUE INDEX UX_ContactSpecialties_Active ON dbo.ContactSpecialties(ShaleClientId,ContactId,SpecialtyId) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactCredentials') AND name=N'UX_ContactCredentials_Active') CREATE UNIQUE INDEX UX_ContactCredentials_Active ON dbo.ContactCredentials(ShaleClientId,ContactId,CredentialDefinitionId) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactCredentials') AND name=N'IX_ContactCredentials_Display') CREATE INDEX IX_ContactCredentials_Display ON dbo.ContactCredentials(ShaleClientId,ContactId,IsDeleted,DisplayOrder,Id);

IF OBJECT_ID(N'dbo.FK_ContactContactTypes_Contact_Tenant',N'F') IS NULL ALTER TABLE dbo.ContactContactTypes ADD CONSTRAINT FK_ContactContactTypes_Contact_Tenant FOREIGN KEY(ShaleClientId,ContactId) REFERENCES dbo.Contacts(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_ContactSpecialties_Contact_Tenant',N'F') IS NULL ALTER TABLE dbo.ContactSpecialties ADD CONSTRAINT FK_ContactSpecialties_Contact_Tenant FOREIGN KEY(ShaleClientId,ContactId) REFERENCES dbo.Contacts(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_ContactCredentials_Contact_Tenant',N'F') IS NULL ALTER TABLE dbo.ContactCredentials ADD CONSTRAINT FK_ContactCredentials_Contact_Tenant FOREIGN KEY(ShaleClientId,ContactId) REFERENCES dbo.Contacts(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_ContactContactTypes_Definition',N'F') IS NULL ALTER TABLE dbo.ContactContactTypes ADD CONSTRAINT FK_ContactContactTypes_Definition FOREIGN KEY(ContactTypeId) REFERENCES dbo.ContactTypes(Id);
IF OBJECT_ID(N'dbo.FK_ContactSpecialties_Definition',N'F') IS NULL ALTER TABLE dbo.ContactSpecialties ADD CONSTRAINT FK_ContactSpecialties_Definition FOREIGN KEY(SpecialtyId) REFERENCES dbo.Specialties(Id);
IF OBJECT_ID(N'dbo.FK_ContactCredentials_Definition',N'F') IS NULL ALTER TABLE dbo.ContactCredentials ADD CONSTRAINT FK_ContactCredentials_Definition FOREIGN KEY(CredentialDefinitionId) REFERENCES dbo.CredentialDefinitions(Id);
IF OBJECT_ID(N'dbo.FK_ContactTypes_ShaleClient',N'F') IS NULL ALTER TABLE dbo.ContactTypes ADD CONSTRAINT FK_ContactTypes_ShaleClient FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_Specialties_ShaleClient',N'F') IS NULL ALTER TABLE dbo.Specialties ADD CONSTRAINT FK_Specialties_ShaleClient FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_CredentialDefinitions_ShaleClient',N'F') IS NULL ALTER TABLE dbo.CredentialDefinitions ADD CONSTRAINT FK_CredentialDefinitions_ShaleClient FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);

/* The sole Phase 1A global default is the compatibility authority for IsExpert. */
IF NOT EXISTS(SELECT 1 FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert')
 INSERT dbo.ContactTypes(ShaleClientId,SystemKey,Name,Description,SortOrder) VALUES(NULL,N'expert',N'Expert',N'Authoritative expert classification.',10);
DECLARE @ExpertId int=(SELECT Id FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert');
INSERT dbo.ContactContactTypes(ShaleClientId,ContactId,ContactTypeId,IsDeleted,CreatedAt)
SELECT c.ShaleClientId,c.Id,@ExpertId,0,SYSUTCDATETIME() FROM dbo.Contacts c
WHERE c.IsExpert=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactContactTypes a WHERE a.ShaleClientId=c.ShaleClientId AND a.ContactId=c.Id AND a.ContactTypeId=@ExpertId AND a.IsDeleted=0);

DECLARE @sql nvarchar(max);
DECLARE rls_cursor CURSOR LOCAL FAST_FORWARD FOR
 SELECT T,P FROM (VALUES(N'ContactTypes',N'fn_FilterByTenantOrGlobal'),(N'Specialties',N'fn_FilterByTenantOrGlobal'),(N'CredentialDefinitions',N'fn_FilterByTenantOrGlobal'),
 (N'ContactContactTypes',N'fn_FilterByTenant'),(N'ContactSpecialties',N'fn_FilterByTenant'),(N'ContactCredentials',N'fn_FilterByTenant'))x(T,P);
DECLARE @t sysname,@p sysname; OPEN rls_cursor; FETCH NEXT FROM rls_cursor INTO @t,@p;
WHILE @@FETCH_STATUS=0 BEGIN IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.'+@t) AND predicate_type_desc=N'FILTER') BEGIN
 SET @sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.'+QUOTENAME(@p)+N'(ShaleClientId) ON dbo.'+QUOTENAME(@t)+N';'; EXEC sys.sp_executesql @sql; END
 FETCH NEXT FROM rls_cursor INTO @t,@p; END CLOSE rls_cursor; DEALLOCATE rls_cursor;

COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO
/* Future mutation services MUST transactionally prove each definition is global or owned by the
   assignment tenant and validate actor ownership. SQL FKs cannot express that overlay rule.
   Phase 1B+ must dual-write IsExpert and the expert assignment before read cutover; only a later,
   separately validated phase may retire IsExpert. No rollback script is supplied: this additive
   migration is forward-only; rollback means leaving the unused schema in place. */

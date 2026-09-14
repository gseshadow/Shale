/* Contacts Phase 1A: additive definitions, assignments, and structured-name storage.
   Forward-only and idempotent. This script deliberately leaves all legacy columns and runtime
   behavior intact. Execute only through the approved all-tenant migration connection. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
/* On a disposable execution copy only, set this to 1 after the independent all-tenant
   visibility preflight confirms that this principal can see every tenant. */
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 THROW 56100, 'Contacts Phase 1A requires SESSION_CONTEXT ShaleClientId to be NULL; do not clear retained application context automatically.', 1;
IF USER_NAME() IN (N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 56101, 'Use the approved all-tenant migration/administrative database principal, never shale_app or shale_runtime.', 1;
IF @OperatorVerifiedAllTenantVisibility<>1
 THROW 56131, 'Operator-verified all-tenant visibility is required; set the acknowledgement only after independent visibility preflight.',1;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Contacts',N'U') IS NULL THROW 56102, 'Required table dbo.Contacts is missing.', 1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 56103, 'Required table dbo.ShaleClients is missing.', 1;
IF COL_LENGTH(N'dbo.Contacts',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.Contacts',N'IsExpert') IS NULL
    THROW 56104, 'Contacts must expose ShaleClientId and IsExpert.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL OR OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal',N'IF') IS NULL
    THROW 56105, 'Required tenant RLS predicate functions are missing.', 1;

DECLARE @PolicyId int, @PolicyQualified nvarchar(517);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter') <> 1
    THROW 56106, 'Exactly one established security policy named TenantFilter is required.', 1;
SELECT @PolicyId=object_id,
       @PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 56107, 'The established TenantFilter security policy must be enabled.', 1;

/* Additive only: Name/FirstName/LastName/WorkName and their display behavior remain authoritative. */
IF COL_LENGTH(N'dbo.Contacts',N'Prefix') IS NULL ALTER TABLE dbo.Contacts ADD Prefix nvarchar(50) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'MiddleName') IS NULL ALTER TABLE dbo.Contacts ADD MiddleName nvarchar(100) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'PreferredName') IS NULL ALTER TABLE dbo.Contacts ADD PreferredName nvarchar(100) NULL;
IF COL_LENGTH(N'dbo.Contacts',N'Suffix') IS NULL ALTER TABLE dbo.Contacts ADD Suffix nvarchar(50) NULL;
IF EXISTS(SELECT 1 FROM (VALUES(N'Prefix',100),(N'MiddleName',200),(N'PreferredName',200),(N'Suffix',100))e(c,l) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.Contacts') AND c.name=e.c WHERE c.column_id IS NULL OR c.system_type_id<>231 OR c.max_length<>e.l OR c.is_nullable<>1) THROW 56125,'Existing structured Contact column is incompatible.',1;

IF OBJECT_ID(N'dbo.ContactTypes',N'U') IS NULL CREATE TABLE dbo.ContactTypes(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactTypes PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_ContactTypes_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_ContactTypes_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactTypes_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_ContactTypes_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactTypes_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_ContactTypes_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0),
 CONSTRAINT CK_ContactTypes_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

IF OBJECT_ID(N'dbo.Specialties',N'U') IS NULL CREATE TABLE dbo.Specialties(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Specialties PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_Specialties_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_Specialties_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_Specialties_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_Specialties_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_Specialties_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_Specialties_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0),
 CONSTRAINT CK_Specialties_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

IF OBJECT_ID(N'dbo.CredentialDefinitions',N'U') IS NULL CREATE TABLE dbo.CredentialDefinitions(
 Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CredentialDefinitions PRIMARY KEY, ShaleClientId int NULL,
 SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Abbreviation nvarchar(50) NOT NULL, Description nvarchar(500) NULL,
 SortOrder int NOT NULL CONSTRAINT DF_CredentialDefinitions_SortOrder DEFAULT(0), IsActive bit NOT NULL CONSTRAINT DF_CredentialDefinitions_IsActive DEFAULT(1),
 IsDeleted bit NOT NULL CONSTRAINT DF_CredentialDefinitions_IsDeleted DEFAULT(0), DeletedAt datetime2 NULL, DeletedByUserId int NULL,
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_CredentialDefinitions_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_CredentialDefinitions_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)),N'') IS NOT NULL),
 CONSTRAINT CK_CredentialDefinitions_DeletedInactive CHECK(IsDeleted=0 OR IsActive=0),
 CONSTRAINT CK_CredentialDefinitions_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

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

/* Full-contract idempotency: reject partial or differently shaped deployments for manual review. */
DECLARE @ExpectedColumns TABLE(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit,IsRowVersion bit);
INSERT @ExpectedColumns VALUES
(N'ContactTypes',N'Id',N'int',4,0,1,0),(N'ContactTypes',N'ShaleClientId',N'int',4,1,0,0),(N'ContactTypes',N'SystemKey',N'nvarchar',128,0,0,0),(N'ContactTypes',N'Name',N'nvarchar',200,0,0,0),(N'ContactTypes',N'Description',N'nvarchar',1000,1,0,0),(N'ContactTypes',N'SortOrder',N'int',4,0,0,0),(N'ContactTypes',N'IsActive',N'bit',1,0,0,0),(N'ContactTypes',N'IsDeleted',N'bit',1,0,0,0),(N'ContactTypes',N'DeletedAt',N'datetime2',8,1,0,0),(N'ContactTypes',N'DeletedByUserId',N'int',4,1,0,0),(N'ContactTypes',N'CreatedAt',N'datetime2',8,0,0,0),(N'ContactTypes',N'CreatedByUserId',N'int',4,1,0,0),(N'ContactTypes',N'UpdatedAt',N'datetime2',8,1,0,0),(N'ContactTypes',N'UpdatedByUserId',N'int',4,1,0,0),(N'ContactTypes',N'RowVer',N'timestamp',8,0,0,1),
(N'Specialties',N'Id',N'int',4,0,1,0),(N'Specialties',N'ShaleClientId',N'int',4,1,0,0),(N'Specialties',N'SystemKey',N'nvarchar',128,0,0,0),(N'Specialties',N'Name',N'nvarchar',200,0,0,0),(N'Specialties',N'Description',N'nvarchar',1000,1,0,0),(N'Specialties',N'SortOrder',N'int',4,0,0,0),(N'Specialties',N'IsActive',N'bit',1,0,0,0),(N'Specialties',N'IsDeleted',N'bit',1,0,0,0),(N'Specialties',N'DeletedAt',N'datetime2',8,1,0,0),(N'Specialties',N'DeletedByUserId',N'int',4,1,0,0),(N'Specialties',N'CreatedAt',N'datetime2',8,0,0,0),(N'Specialties',N'CreatedByUserId',N'int',4,1,0,0),(N'Specialties',N'UpdatedAt',N'datetime2',8,1,0,0),(N'Specialties',N'UpdatedByUserId',N'int',4,1,0,0),(N'Specialties',N'RowVer',N'timestamp',8,0,0,1),
(N'CredentialDefinitions',N'Id',N'int',4,0,1,0),(N'CredentialDefinitions',N'ShaleClientId',N'int',4,1,0,0),(N'CredentialDefinitions',N'SystemKey',N'nvarchar',128,0,0,0),(N'CredentialDefinitions',N'Name',N'nvarchar',200,0,0,0),(N'CredentialDefinitions',N'Abbreviation',N'nvarchar',100,0,0,0),(N'CredentialDefinitions',N'Description',N'nvarchar',1000,1,0,0),(N'CredentialDefinitions',N'SortOrder',N'int',4,0,0,0),(N'CredentialDefinitions',N'IsActive',N'bit',1,0,0,0),(N'CredentialDefinitions',N'IsDeleted',N'bit',1,0,0,0),(N'CredentialDefinitions',N'DeletedAt',N'datetime2',8,1,0,0),(N'CredentialDefinitions',N'DeletedByUserId',N'int',4,1,0,0),(N'CredentialDefinitions',N'CreatedAt',N'datetime2',8,0,0,0),(N'CredentialDefinitions',N'CreatedByUserId',N'int',4,1,0,0),(N'CredentialDefinitions',N'UpdatedAt',N'datetime2',8,1,0,0),(N'CredentialDefinitions',N'UpdatedByUserId',N'int',4,1,0,0),(N'CredentialDefinitions',N'RowVer',N'timestamp',8,0,0,1);
INSERT @ExpectedColumns
SELECT t,c,ty,l,n,i,r FROM (VALUES
(N'ContactContactTypes',N'Id',N'bigint',8,0,1,0),(N'ContactContactTypes',N'ShaleClientId',N'int',4,0,0,0),(N'ContactContactTypes',N'ContactId',N'int',4,0,0,0),(N'ContactContactTypes',N'ContactTypeId',N'int',4,0,0,0),
(N'ContactSpecialties',N'Id',N'bigint',8,0,1,0),(N'ContactSpecialties',N'ShaleClientId',N'int',4,0,0,0),(N'ContactSpecialties',N'ContactId',N'int',4,0,0,0),(N'ContactSpecialties',N'SpecialtyId',N'int',4,0,0,0),
(N'ContactCredentials',N'Id',N'bigint',8,0,1,0),(N'ContactCredentials',N'ShaleClientId',N'int',4,0,0,0),(N'ContactCredentials',N'ContactId',N'int',4,0,0,0),(N'ContactCredentials',N'CredentialDefinitionId',N'int',4,0,0,0),(N'ContactCredentials',N'DisplayOrder',N'int',4,0,0,0))v(t,c,ty,l,n,i,r);
INSERT @ExpectedColumns SELECT t,c,ty,l,n,i,r FROM (SELECT t,v.* FROM (VALUES(N'ContactContactTypes'),(N'ContactSpecialties'),(N'ContactCredentials'))q(t) CROSS APPLY(VALUES
(N'IsDeleted',N'bit',1,0,0,0),(N'CreatedAt',N'datetime2',8,0,0,0),(N'CreatedByUserId',N'int',4,1,0,0),(N'UpdatedAt',N'datetime2',8,1,0,0),(N'UpdatedByUserId',N'int',4,1,0,0),(N'DeletedAt',N'datetime2',8,1,0,0),(N'DeletedByUserId',N'int',4,1,0,0),(N'RowVer',N'timestamp',8,0,0,1))v(c,ty,l,n,i,r))x;
IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id WHERE t.name IN(N'ContactTypes',N'Specialties',N'CredentialDefinitions',N'ContactContactTypes',N'ContactSpecialties',N'ContactCredentials') AND c.name IN(N'CreatedAt',N'UpdatedAt',N'DeletedAt') AND (c.precision<>27 OR c.scale<>7)) THROW 56130,'Phase 1A datetime2 columns must use datetime2(7).',1;
IF EXISTS(SELECT 1 FROM sys.identity_columns ic JOIN sys.tables t ON t.object_id=ic.object_id WHERE t.name IN(N'ContactTypes',N'Specialties',N'CredentialDefinitions',N'ContactContactTypes',N'ContactSpecialties',N'ContactCredentials') AND (CONVERT(bigint,ic.seed_value)<>1 OR CONVERT(bigint,ic.increment_value)<>1)) THROW 56126,'Phase 1A identities must be IDENTITY(1,1).',1;
IF EXISTS(SELECT 1 FROM @ExpectedColumns e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ColumnName LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR y.name<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity OR c.is_rowguidcol<>0 OR (CASE WHEN c.system_type_id=189 THEN 1 ELSE 0 END)<>e.IsRowVersion)
 THROW 56108, 'Incompatible Contacts Phase 1A column contract; manual review required.',1;
/* Deliberately do not reject other columns: later additive phases may extend these tables. */

/* Contacts live-schema and tenant-ownership preflight precedes its composite key and backfill. */
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'Id' AND system_type_id=56 AND is_nullable=0 AND is_identity=1)
 OR NOT EXISTS(SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.object_id=OBJECT_ID(N'dbo.Contacts') AND i.is_primary_key=1 AND c.name=N'Id')
 THROW 56110,'dbo.Contacts.Id must be the NOT NULL int IDENTITY primary key.',1;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'IsExpert' AND system_type_id=104 AND is_nullable=1) THROW 56129,'dbo.Contacts.IsExpert must remain nullable bit.',1;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'ShaleClientId' AND system_type_id=56 AND is_nullable=0)
 THROW 56111,'dbo.Contacts.ShaleClientId must be int NOT NULL.',1;
IF EXISTS(SELECT 1 FROM dbo.Contacts c LEFT JOIN dbo.ShaleClients sc ON sc.Id=c.ShaleClientId WHERE sc.Id IS NULL)
 THROW 56112,'A Contact has an invalid or missing tenant; ownership must be repaired manually.',1;
IF EXISTS(SELECT 1 FROM dbo.Contacts GROUP BY ShaleClientId,Id HAVING COUNT_BIG(*)>1)
 THROW 56113,'Duplicate Contacts (ShaleClientId,Id) prevent the required composite unique index.',1;
IF EXISTS(SELECT 1 FROM dbo.ContactTypes GROUP BY ShaleClientId,SystemKey HAVING COUNT_BIG(*)>1) OR EXISTS(SELECT 1 FROM dbo.Specialties GROUP BY ShaleClientId,SystemKey HAVING COUNT_BIG(*)>1) OR EXISTS(SELECT 1 FROM dbo.CredentialDefinitions GROUP BY ShaleClientId,SystemKey HAVING COUNT_BIG(*)>1)
 THROW 56127,'Duplicate definition keys prevent required unique indexes; manual review required.',1;
IF EXISTS(SELECT 1 FROM dbo.ContactContactTypes WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,ContactTypeId HAVING COUNT_BIG(*)>1) OR EXISTS(SELECT 1 FROM dbo.ContactSpecialties WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,SpecialtyId HAVING COUNT_BIG(*)>1) OR EXISTS(SELECT 1 FROM dbo.ContactCredentials WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,CredentialDefinitionId HAVING COUNT_BIG(*)>1)
 THROW 56128,'Duplicate active assignments prevent required filtered unique indexes; manual review required.',1;
IF (SELECT COUNT_BIG(*) FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert')>1 THROW 56120,'Duplicate global expert definitions require manual review.',1;
IF EXISTS(SELECT 1 FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert' AND (Name<>N'Expert' OR IsActive<>1 OR IsDeleted<>0 OR DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) THROW 56121,'The pre-existing global expert definition is incompatible.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'UX_Contacts_ShaleClientId_Id') CREATE UNIQUE INDEX UX_Contacts_ShaleClientId_Id ON dbo.Contacts(ShaleClientId,Id);

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

IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR COL_LENGTH(N'dbo.Users',N'id') IS NULL THROW 56114,'dbo.Users(id) actor authority is required.',1;
DECLARE @Fks TABLE(ConstraintName sysname,ChildTable sysname,ChildColumns nvarchar(200),ParentTable sysname,ParentColumns nvarchar(200));
INSERT @Fks VALUES
(N'FK_ContactContactTypes_Contact_Tenant',N'ContactContactTypes',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id'),(N'FK_ContactSpecialties_Contact_Tenant',N'ContactSpecialties',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id'),(N'FK_ContactCredentials_Contact_Tenant',N'ContactCredentials',N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id'),
(N'FK_ContactContactTypes_Definition',N'ContactContactTypes',N'ContactTypeId',N'ContactTypes',N'Id'),(N'FK_ContactSpecialties_Definition',N'ContactSpecialties',N'SpecialtyId',N'Specialties',N'Id'),(N'FK_ContactCredentials_Definition',N'ContactCredentials',N'CredentialDefinitionId',N'CredentialDefinitions',N'Id'),
(N'FK_ContactTypes_ShaleClient',N'ContactTypes',N'ShaleClientId',N'ShaleClients',N'Id'),(N'FK_Specialties_ShaleClient',N'Specialties',N'ShaleClientId',N'ShaleClients',N'Id'),(N'FK_CredentialDefinitions_ShaleClient',N'CredentialDefinitions',N'ShaleClientId',N'ShaleClients',N'Id'),
(N'FK_ContactContactTypes_ShaleClient',N'ContactContactTypes',N'ShaleClientId',N'ShaleClients',N'Id'),(N'FK_ContactSpecialties_ShaleClient',N'ContactSpecialties',N'ShaleClientId',N'ShaleClients',N'Id'),(N'FK_ContactCredentials_ShaleClient',N'ContactCredentials',N'ShaleClientId',N'ShaleClients',N'Id');
DECLARE @ct sysname,@cc nvarchar(200),@pt sysname,@pc nvarchar(200),@fn sysname,@ddl nvarchar(max);
DECLARE fk_create CURSOR LOCAL FAST_FORWARD FOR SELECT ConstraintName,ChildTable,ChildColumns,ParentTable,ParentColumns FROM @Fks;
OPEN fk_create; FETCH NEXT FROM fk_create INTO @fn,@ct,@cc,@pt,@pc; WHILE @@FETCH_STATUS=0 BEGIN IF OBJECT_ID(N'dbo.'+@fn,N'F') IS NULL BEGIN SET @ddl=N'ALTER TABLE dbo.'+QUOTENAME(@ct)+N' ADD CONSTRAINT '+QUOTENAME(@fn)+N' FOREIGN KEY('+@cc+N') REFERENCES dbo.'+QUOTENAME(@pt)+N'('+@pc+N');'; EXEC sys.sp_executesql @ddl; END FETCH NEXT FROM fk_create INTO @fn,@ct,@cc,@pt,@pc; END CLOSE fk_create; DEALLOCATE fk_create;
DECLARE actor_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT t,a FROM (VALUES(N'ContactTypes'),(N'Specialties'),(N'CredentialDefinitions'),(N'ContactContactTypes'),(N'ContactSpecialties'),(N'ContactCredentials'))t(t) CROSS JOIN(VALUES(N'CreatedByUserId'),(N'UpdatedByUserId'),(N'DeletedByUserId'))a(a);
DECLARE @a sysname; OPEN actor_cursor; FETCH NEXT FROM actor_cursor INTO @ct,@a; WHILE @@FETCH_STATUS=0 BEGIN SET @fn=N'FK_'+@ct+N'_'+@a; IF OBJECT_ID(N'dbo.'+@fn,N'F') IS NULL BEGIN SET @ddl=N'ALTER TABLE dbo.'+QUOTENAME(@ct)+N' ADD CONSTRAINT '+QUOTENAME(@fn)+N' FOREIGN KEY('+QUOTENAME(@a)+N') REFERENCES dbo.Users(id);'; EXEC sys.sp_executesql @ddl; END FETCH NEXT FROM actor_cursor INTO @ct,@a; END CLOSE actor_cursor; DEALLOCATE actor_cursor;

/* Validate critical defaults, checks, indexes and every FK by semantics, not name alone. */
IF EXISTS(SELECT 1 FROM (VALUES(N'ContactTypes',N'SortOrder',N'((0))'),(N'ContactTypes',N'IsActive',N'((1))'),(N'ContactTypes',N'IsDeleted',N'((0))'),(N'ContactTypes',N'CreatedAt',N'(sysutcdatetime())'),(N'Specialties',N'SortOrder',N'((0))'),(N'Specialties',N'IsActive',N'((1))'),(N'Specialties',N'IsDeleted',N'((0))'),(N'Specialties',N'CreatedAt',N'(sysutcdatetime())'),(N'CredentialDefinitions',N'SortOrder',N'((0))'),(N'CredentialDefinitions',N'IsActive',N'((1))'),(N'CredentialDefinitions',N'IsDeleted',N'((0))'),(N'CredentialDefinitions',N'CreatedAt',N'(sysutcdatetime())'),(N'ContactContactTypes',N'IsDeleted',N'((0))'),(N'ContactContactTypes',N'CreatedAt',N'(sysutcdatetime())'),(N'ContactSpecialties',N'IsDeleted',N'((0))'),(N'ContactSpecialties',N'CreatedAt',N'(sysutcdatetime())'),(N'ContactCredentials',N'IsDeleted',N'((0))'),(N'ContactCredentials',N'CreatedAt',N'(sysutcdatetime())'),(N'ContactCredentials',N'DisplayOrder',N'((0))'))v(t,c,d) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+v.t) AND c.name=v.c LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id WHERE LOWER(REPLACE(dc.definition,N' ',N''))<>LOWER(v.d) OR dc.object_id IS NULL)
 THROW 56115,'Critical default constraint is missing or incompatible.',1;
DECLARE @ExpectedChecks TABLE(TableName sysname,ConstraintName sysname,RequiredToken1 nvarchar(50),RequiredToken2 nvarchar(50),RequiredToken3 nvarchar(50),RequiredToken4 nvarchar(50));
INSERT @ExpectedChecks VALUES
(N'ContactTypes',N'CK_ContactTypes_SystemKey',N'SystemKey',N'lower',N'like',N'ltrim'),(N'Specialties',N'CK_Specialties_SystemKey',N'SystemKey',N'lower',N'like',N'ltrim'),(N'CredentialDefinitions',N'CK_CredentialDefinitions_SystemKey',N'SystemKey',N'lower',N'like',N'ltrim'),
(N'ContactTypes',N'CK_ContactTypes_DeletedInactive',N'IsDeleted',N'IsActive',NULL,NULL),(N'Specialties',N'CK_Specialties_DeletedInactive',N'IsDeleted',N'IsActive',NULL,NULL),(N'CredentialDefinitions',N'CK_CredentialDefinitions_DeletedInactive',N'IsDeleted',N'IsActive',NULL,NULL),
(N'ContactTypes',N'CK_ContactTypes_DeleteFields',N'IsDeleted',N'IsActive',N'DeletedAt',N'DeletedByUserId'),(N'Specialties',N'CK_Specialties_DeleteFields',N'IsDeleted',N'IsActive',N'DeletedAt',N'DeletedByUserId'),(N'CredentialDefinitions',N'CK_CredentialDefinitions_DeleteFields',N'IsDeleted',N'IsActive',N'DeletedAt',N'DeletedByUserId'),
(N'ContactContactTypes',N'CK_ContactContactTypes_DeleteFields',N'IsDeleted',N'DeletedAt',N'DeletedByUserId',NULL),(N'ContactSpecialties',N'CK_ContactSpecialties_DeleteFields',N'IsDeleted',N'DeletedAt',N'DeletedByUserId',NULL),(N'ContactCredentials',N'CK_ContactCredentials_DeleteFields',N'IsDeleted',N'DeletedAt',N'DeletedByUserId',NULL),(N'ContactCredentials',N'CK_ContactCredentials_DisplayOrder',N'DisplayOrder',N'>=',NULL,NULL);
IF EXISTS(SELECT 1 FROM @ExpectedChecks e LEFT JOIN sys.check_constraints c ON c.parent_object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ConstraintName WHERE c.object_id IS NULL OR c.is_disabled=1 OR c.is_not_trusted=1 OR c.definition NOT LIKE N'%'+e.RequiredToken1+N'%' OR (e.RequiredToken2 IS NOT NULL AND c.definition NOT LIKE N'%'+e.RequiredToken2+N'%') OR (e.RequiredToken3 IS NOT NULL AND c.definition NOT LIKE N'%'+e.RequiredToken3+N'%') OR (e.RequiredToken4 IS NOT NULL AND c.definition NOT LIKE N'%'+e.RequiredToken4+N'%'))
 THROW 56116,'A required named Phase 1A CHECK constraint is missing or incompatible.',1;
/* Unrelated checks added by later phases are intentionally tolerated. */
/* Canonical key lists expose wrong order, includes, uniqueness, or filters even when names match. */
DECLARE @ExpectedIndexes TABLE(TableName sysname,IndexName sysname,IsUnique bit,Filter nvarchar(100),Keys nvarchar(300),Includes nvarchar(300));
INSERT @ExpectedIndexes VALUES
(N'ContactTypes',N'PK_ContactTypes',1,NULL,N'Id',N''),(N'Specialties',N'PK_Specialties',1,NULL,N'Id',N''),(N'CredentialDefinitions',N'PK_CredentialDefinitions',1,NULL,N'Id',N''),(N'ContactContactTypes',N'PK_ContactContactTypes',1,NULL,N'Id',N''),(N'ContactSpecialties',N'PK_ContactSpecialties',1,NULL,N'Id',N''),(N'ContactCredentials',N'PK_ContactCredentials',1,NULL,N'Id',N''),
(N'Contacts',N'UX_Contacts_ShaleClientId_Id',1,NULL,N'ShaleClientId,Id',N''),(N'ContactTypes',N'UX_ContactTypes_Global_SystemKey',1,N'([ShaleClientId] IS NULL)',N'SystemKey',N''),(N'ContactTypes',N'UX_ContactTypes_Tenant_SystemKey',1,N'([ShaleClientId] IS NOT NULL)',N'ShaleClientId,SystemKey',N''),(N'Specialties',N'UX_Specialties_Global_SystemKey',1,N'([ShaleClientId] IS NULL)',N'SystemKey',N''),(N'Specialties',N'UX_Specialties_Tenant_SystemKey',1,N'([ShaleClientId] IS NOT NULL)',N'ShaleClientId,SystemKey',N''),(N'CredentialDefinitions',N'UX_CredentialDefinitions_Global_SystemKey',1,N'([ShaleClientId] IS NULL)',N'SystemKey',N''),(N'CredentialDefinitions',N'UX_CredentialDefinitions_Tenant_SystemKey',1,N'([ShaleClientId] IS NOT NULL)',N'ShaleClientId,SystemKey',N''),(N'ContactContactTypes',N'UX_ContactContactTypes_Active',1,N'([IsDeleted]=(0))',N'ShaleClientId,ContactId,ContactTypeId',N''),(N'ContactSpecialties',N'UX_ContactSpecialties_Active',1,N'([IsDeleted]=(0))',N'ShaleClientId,ContactId,SpecialtyId',N''),(N'ContactCredentials',N'UX_ContactCredentials_Active',1,N'([IsDeleted]=(0))',N'ShaleClientId,ContactId,CredentialDefinitionId',N''),(N'ContactCredentials',N'IX_ContactCredentials_Display',0,NULL,N'ShaleClientId,ContactId,IsDeleted,DisplayOrder,Id',N'');
IF EXISTS(SELECT 1 FROM @ExpectedIndexes e OUTER APPLY(SELECT i.object_id,i.index_id,i.is_unique,i.filter_definition FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND i.name=e.IndexName)i OUTER APPLY(SELECT STRING_AGG(CASE WHEN ic.is_included_column=0 THEN c.name END,N',') WITHIN GROUP(ORDER BY ic.index_column_id) Keys,STRING_AGG(CASE WHEN ic.is_included_column=1 THEN c.name END,N',') WITHIN GROUP(ORDER BY ic.index_column_id) Includes FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id)x WHERE i.index_id IS NULL OR i.is_unique<>e.IsUnique OR ISNULL(REPLACE(i.filter_definition,N' ',N''),N'')<>ISNULL(REPLACE(e.Filter,N' ',N''),N'') OR ISNULL(x.Keys,N'')<>e.Keys OR ISNULL(x.Includes,N'')<>e.Includes)
 THROW 56117,'Required index has incompatible keys, uniqueness, includes, or filter.',1;
IF EXISTS(SELECT 1 FROM @Fks e LEFT JOIN sys.foreign_keys f ON f.parent_object_id=OBJECT_ID(N'dbo.'+e.ChildTable) AND f.name=e.ConstraintName OUTER APPLY(SELECT STRING_AGG(pc.name,N',') WITHIN GROUP(ORDER BY fkc.constraint_column_id) ChildColumns,STRING_AGG(rc.name,N',') WITHIN GROUP(ORDER BY fkc.constraint_column_id) ParentColumns FROM sys.foreign_key_columns fkc JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id WHERE fkc.constraint_object_id=f.object_id)x WHERE f.object_id IS NULL OR OBJECT_NAME(f.referenced_object_id)<>e.ParentTable OR x.ChildColumns<>e.ChildColumns OR x.ParentColumns<>e.ParentColumns)
 THROW 56118,'Required foreign key name exists with incompatible parent/column mapping.',1;
IF EXISTS(SELECT 1 FROM (SELECT t.t,a.a,N'FK_'+t.t+N'_'+a.a n FROM (VALUES(N'ContactTypes'),(N'Specialties'),(N'CredentialDefinitions'),(N'ContactContactTypes'),(N'ContactSpecialties'),(N'ContactCredentials'))t(t) CROSS JOIN(VALUES(N'CreatedByUserId'),(N'UpdatedByUserId'),(N'DeletedByUserId'))a(a))e LEFT JOIN sys.foreign_keys f ON f.parent_object_id=OBJECT_ID(N'dbo.'+e.t) AND f.name=e.n LEFT JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id WHERE f.object_id IS NULL OR f.referenced_object_id<>OBJECT_ID(N'dbo.Users') OR COL_NAME(fc.parent_object_id,fc.parent_column_id)<>e.a OR COL_NAME(fc.referenced_object_id,fc.referenced_column_id)<>N'id')
 THROW 56119,'Actor FK must map the actor column exactly to dbo.Users(id).',1;

/* The sole Phase 1A global default is the compatibility authority for IsExpert. */
IF NOT EXISTS(SELECT 1 FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert')
 INSERT dbo.ContactTypes(ShaleClientId,SystemKey,Name,Description,SortOrder) VALUES(NULL,N'expert',N'Expert',N'Authoritative expert classification.',10);
DECLARE @ExpertId int=(SELECT Id FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert');
IF EXISTS(SELECT 1 FROM dbo.ContactContactTypes a JOIN dbo.Contacts c ON c.Id=a.ContactId WHERE a.ContactTypeId=@ExpertId AND (a.ShaleClientId<>c.ShaleClientId OR (a.IsDeleted=0 AND ISNULL(c.IsExpert,0)<>1))) THROW 56122,'Incompatible pre-existing Expert assignment requires manual review.',1;
INSERT dbo.ContactContactTypes(ShaleClientId,ContactId,ContactTypeId,IsDeleted,CreatedAt)
SELECT c.ShaleClientId,c.Id,@ExpertId,0,SYSUTCDATETIME() FROM dbo.Contacts c
WHERE c.IsExpert=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactContactTypes a WHERE a.ShaleClientId=c.ShaleClientId AND a.ContactId=c.Id AND a.ContactTypeId=@ExpertId AND a.IsDeleted=0);

DECLARE @sql nvarchar(max);
IF EXISTS(SELECT 1 FROM sys.security_predicates sp WHERE sp.target_object_id IN(OBJECT_ID(N'dbo.ContactTypes'),OBJECT_ID(N'dbo.Specialties'),OBJECT_ID(N'dbo.CredentialDefinitions'),OBJECT_ID(N'dbo.ContactContactTypes'),OBJECT_ID(N'dbo.ContactSpecialties'),OBJECT_ID(N'dbo.ContactCredentials')) AND (sp.object_id<>@PolicyId OR sp.predicate_type_desc<>N'FILTER'))
 THROW 56123,'Unexpected policy or non-FILTER predicate exists on a Phase 1A table.',1;
DECLARE rls_cursor CURSOR LOCAL FAST_FORWARD FOR
 SELECT T,P FROM (VALUES(N'ContactTypes',N'fn_FilterByTenantOrGlobal'),(N'Specialties',N'fn_FilterByTenantOrGlobal'),(N'CredentialDefinitions',N'fn_FilterByTenantOrGlobal'),
 (N'ContactContactTypes',N'fn_FilterByTenant'),(N'ContactSpecialties',N'fn_FilterByTenant'),(N'ContactCredentials',N'fn_FilterByTenant'))x(T,P);
DECLARE @t sysname,@p sysname; OPEN rls_cursor; FETCH NEXT FROM rls_cursor INTO @t,@p;
WHILE @@FETCH_STATUS=0 BEGIN IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.'+@t) AND predicate_type_desc=N'FILTER') BEGIN
 SET @sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.'+QUOTENAME(@p)+N'(ShaleClientId) ON dbo.'+QUOTENAME(@t)+N';'; EXEC sys.sp_executesql @sql; END
 FETCH NEXT FROM rls_cursor INTO @t,@p; END CLOSE rls_cursor; DEALLOCATE rls_cursor;

IF EXISTS(SELECT 1 FROM (VALUES
 (N'ContactTypes',N'sec.fn_filterbytenantorglobalshaleclientid'),(N'Specialties',N'sec.fn_filterbytenantorglobalshaleclientid'),(N'CredentialDefinitions',N'sec.fn_filterbytenantorglobalshaleclientid'),
 (N'ContactContactTypes',N'sec.fn_filterbytenantshaleclientid'),(N'ContactSpecialties',N'sec.fn_filterbytenantshaleclientid'),(N'ContactCredentials',N'sec.fn_filterbytenantshaleclientid'))e(t,ExpectedDefinition)
 OUTER APPLY(SELECT COUNT(*) n,MAX(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(sp.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''),N'(',N''),N')',N''))) NormalizedDefinition,
 MAX(CASE WHEN sp.operation IS NULL AND sp.operation_desc IS NULL THEN 0 ELSE 1 END) WrongOperation
 FROM sys.security_predicates sp WHERE sp.object_id=@PolicyId AND sp.target_object_id=OBJECT_ID(N'dbo.'+e.t) AND sp.predicate_type_desc=N'FILTER')x
 WHERE x.n<>1 OR x.WrongOperation<>0 OR x.NormalizedDefinition<>e.ExpectedDefinition)
 THROW 56124,'Each Phase 1A table requires exactly one expected FILTER function on enabled TenantFilter.',1;

COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO
/* Future mutation services MUST transactionally prove each definition is global or owned by the
   assignment tenant and validate actor ownership. SQL FKs cannot express that overlay rule.
   Phase 1B+ must dual-write IsExpert and the expert assignment before read cutover; only a later,
   separately validated phase may retire IsExpert. No rollback script is supplied: this additive
   migration is forward-only; rollback means leaving the unused schema in place. */

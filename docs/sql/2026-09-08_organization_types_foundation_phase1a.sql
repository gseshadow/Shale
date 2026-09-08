/* Organizations redesign Phase 1A: Organization Type definitions and historical assignments.
   Forward-only, rerunnable, and intentionally leaves Organizations.OrganizationTypeId authoritative.
   Set the acknowledgement only after an independent all-tenant visibility preflight. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL THROW 57000,'Phase 1A requires NULL SESSION_CONTEXT(ShaleClientId).',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 57001,'Use an approved all-tenant administrative principal, never shale_app or shale_runtime.',1;
IF @OperatorVerifiedAllTenantVisibility<>1 THROW 57002,'Operator acknowledgement of independently verified all-tenant visibility is required.',1;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.OrganizationTypes',N'U') IS NULL OR OBJECT_ID(N'dbo.Organizations',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL
 THROW 57003,'Required OrganizationTypes, Organizations, ShaleClients, or Users table is missing.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL OR OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal',N'IF') IS NULL THROW 57004,'Established RLS functions are missing.',1;
DECLARE @PolicyId int,@Policy nvarchar(517);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')<>1 THROW 57005,'Exactly one TenantFilter policy is required.',1;
SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 57006,'TenantFilter must be enabled.',1;

/* Capture immutable foundation state before adding/backfilling any Phase 1A column. Mutable color,
   ordering, and lifecycle values must never be used to infer whether this is the first deployment. */
DECLARE @IsFirstFoundationDeployment bit=CASE WHEN COL_LENGTH(N'dbo.OrganizationTypes',N'SystemKey') IS NULL
 AND COL_LENGTH(N'dbo.OrganizationTypes',N'Color') IS NULL AND COL_LENGTH(N'dbo.OrganizationTypes',N'SortOrder') IS NULL
 AND COL_LENGTH(N'dbo.OrganizationTypes',N'IsActive') IS NULL AND COL_LENGTH(N'dbo.OrganizationTypes',N'IsDeleted') IS NULL
 AND COL_LENGTH(N'dbo.OrganizationTypes',N'RowVer') IS NULL THEN 1 ELSE 0 END;
/* The explicit semantic mapping is safe only for the verified first-deployment Phase 0 baseline. */
IF @IsFirstFoundationDeployment=1 AND ((SELECT COUNT_BIG(*) FROM dbo.OrganizationTypes)<>7 OR EXISTS(
 SELECT 1 FROM (VALUES(1,N'Provider',7),(2,N'Facility',7),(3,N'Firm',7),(4,N'Agency',7),(5,N'Insurer',7),(6,N'Lab',7),(7,N'Other',7)) e(Id,Name,Tenant)
 FULL JOIN dbo.OrganizationTypes d ON d.OrganizationTypeId=e.Id
 WHERE e.Id IS NULL OR d.OrganizationTypeId IS NULL OR d.Name<>e.Name OR d.ShaleClientId<>e.Tenant))
 THROW 57007,'OrganizationTypes differs from the exact seven-row Phase 0 baseline; manual semantic review is required.',1;
IF @IsFirstFoundationDeployment=0 AND EXISTS(
 SELECT 1 FROM (VALUES(1,N'Provider',7),(2,N'Facility',7),(3,N'Firm',7),(4,N'Agency',7),(5,N'Insurer',7),(6,N'Lab',7),(7,N'Other',7)) e(Id,Name,Tenant)
 LEFT JOIN dbo.OrganizationTypes d ON d.OrganizationTypeId=e.Id WHERE d.OrganizationTypeId IS NULL OR d.Name<>e.Name OR d.ShaleClientId<>e.Tenant)
 THROW 57024,'A preserved baseline OrganizationType ID, name, or tenant changed.',1;

IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'Id' AND system_type_id=56 AND is_nullable=0)
 OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'ShaleClientId' AND system_type_id=56 AND is_nullable=0)
 OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'OrganizationTypeId' AND system_type_id=56)
 THROW 57008,'Organizations ownership/type columns are incompatible.',1;
IF EXISTS(SELECT 1 FROM dbo.Organizations o LEFT JOIN dbo.ShaleClients c ON c.Id=o.ShaleClientId WHERE c.Id IS NULL)
 OR EXISTS(SELECT 1 FROM dbo.Organizations GROUP BY ShaleClientId,Id HAVING COUNT_BIG(*)>1)
 OR EXISTS(SELECT 1 FROM dbo.Organizations o LEFT JOIN dbo.OrganizationTypes t ON t.OrganizationTypeId=o.OrganizationTypeId WHERE t.OrganizationTypeId IS NULL)
 THROW 57009,'Organizations tenant ownership, composite identity, or legacy type data is invalid.',1;

CREATE TABLE #OrganizationBaseline(Id int NOT NULL,ShaleClientId int NOT NULL,OrganizationTypeId int NOT NULL,IsDeleted bit NULL,RowVer binary(8) NULL,PRIMARY KEY(ShaleClientId,Id));
INSERT #OrganizationBaseline SELECT Id,ShaleClientId,OrganizationTypeId,IsDeleted,CONVERT(binary(8),RowVer) FROM dbo.Organizations;

/* Resolve the unsafe generated default through the catalog, not by name. */
DECLARE @DefaultName sysname,@sql nvarchar(max);
SELECT @DefaultName=dc.name FROM sys.default_constraints dc JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id WHERE dc.parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND c.name=N'ShaleClientId';
IF @DefaultName IS NOT NULL BEGIN SET @sql=N'ALTER TABLE dbo.OrganizationTypes DROP CONSTRAINT '+QUOTENAME(@DefaultName); EXEC sys.sp_executesql @sql; END;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'ShaleClientId' AND system_type_id=56) THROW 57018,'OrganizationTypes.ShaleClientId must be int.',1;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'ShaleClientId' AND is_nullable=0) ALTER TABLE dbo.OrganizationTypes ALTER COLUMN ShaleClientId int NULL;

IF COL_LENGTH(N'dbo.OrganizationTypes',N'SystemKey') IS NULL ALTER TABLE dbo.OrganizationTypes ADD SystemKey nvarchar(64) NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'Description') IS NULL ALTER TABLE dbo.OrganizationTypes ADD Description nvarchar(500) NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'Color') IS NULL ALTER TABLE dbo.OrganizationTypes ADD Color nvarchar(20) NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'SortOrder') IS NULL ALTER TABLE dbo.OrganizationTypes ADD SortOrder int NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'IsActive') IS NULL ALTER TABLE dbo.OrganizationTypes ADD IsActive bit NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'IsDeleted') IS NULL ALTER TABLE dbo.OrganizationTypes ADD IsDeleted bit NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'CreatedAt') IS NULL ALTER TABLE dbo.OrganizationTypes ADD CreatedAt datetime2 NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'CreatedByUserId') IS NULL ALTER TABLE dbo.OrganizationTypes ADD CreatedByUserId int NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'UpdatedAt') IS NULL ALTER TABLE dbo.OrganizationTypes ADD UpdatedAt datetime2 NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'UpdatedByUserId') IS NULL ALTER TABLE dbo.OrganizationTypes ADD UpdatedByUserId int NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'DeletedAt') IS NULL ALTER TABLE dbo.OrganizationTypes ADD DeletedAt datetime2 NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'DeletedByUserId') IS NULL ALTER TABLE dbo.OrganizationTypes ADD DeletedByUserId int NULL;
IF COL_LENGTH(N'dbo.OrganizationTypes',N'RowVer') IS NULL ALTER TABLE dbo.OrganizationTypes ADD RowVer rowversion NOT NULL;

IF @IsFirstFoundationDeployment=1
UPDATE d SET SystemKey=v.SystemKey,Color=v.Color,SortOrder=v.SortOrder,IsActive=1,IsDeleted=0,CreatedAt=COALESCE(d.CreatedAt,SYSUTCDATETIME())
FROM dbo.OrganizationTypes d JOIN (VALUES(1,N'provider',N'#0F766E',0),(2,N'facility',N'#2563EB',1),(3,N'firm',N'#7C3AED',2),(4,N'agency',N'#D97706',3),(5,N'insurer',N'#059669',4),(6,N'lab',N'#0891B2',5),(7,N'other',N'#6B7280',6))v(Id,SystemKey,Color,SortOrder) ON v.Id=d.OrganizationTypeId
WHERE d.SystemKey IS NULL OR d.Color IS NULL OR d.SortOrder IS NULL OR d.IsActive IS NULL OR d.IsDeleted IS NULL OR d.CreatedAt IS NULL;
ELSE
UPDATE d SET SystemKey=COALESCE(d.SystemKey,v.SystemKey),Color=COALESCE(d.Color,N'#6C757D'),SortOrder=COALESCE(d.SortOrder,v.SortOrder),
 IsActive=COALESCE(d.IsActive,CONVERT(bit,1)),IsDeleted=COALESCE(d.IsDeleted,CONVERT(bit,0)),CreatedAt=COALESCE(d.CreatedAt,SYSUTCDATETIME())
FROM dbo.OrganizationTypes d JOIN (VALUES(1,N'provider',0),(2,N'facility',1),(3,N'firm',2),(4,N'agency',3),(5,N'insurer',4),(6,N'lab',5),(7,N'other',6))v(Id,SystemKey,SortOrder) ON v.Id=d.OrganizationTypeId
WHERE d.SystemKey IS NULL OR d.Color IS NULL OR d.SortOrder IS NULL OR d.IsActive IS NULL OR d.IsDeleted IS NULL OR d.CreatedAt IS NULL;
IF EXISTS(SELECT 1 FROM (VALUES(1,N'provider'),(2,N'facility'),(3,N'firm'),(4,N'agency'),(5,N'insurer'),(6,N'lab'),(7,N'other'))v(Id,SystemKey) LEFT JOIN dbo.OrganizationTypes d ON d.OrganizationTypeId=v.Id WHERE d.SystemKey<>v.SystemKey OR d.SystemKey IS NULL)
 THROW 57025,'A preserved baseline OrganizationType SystemKey is missing or changed.',1;

IF EXISTS(SELECT 1 FROM dbo.OrganizationTypes WHERE SystemKey IS NULL OR Color IS NULL OR SortOrder IS NULL OR IsActive IS NULL OR IsDeleted IS NULL OR CreatedAt IS NULL) THROW 57010,'Definition backfill is incomplete.',1;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN SystemKey nvarchar(64) NOT NULL;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN Color nvarchar(20) NOT NULL;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN SortOrder int NOT NULL;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN IsActive bit NOT NULL;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN IsDeleted bit NOT NULL;
ALTER TABLE dbo.OrganizationTypes ALTER COLUMN CreatedAt datetime2 NOT NULL;
IF EXISTS(SELECT 1 FROM (VALUES(N'OrganizationTypeId',N'int',4,0,1,0),(N'Name',N'nvarchar',200,0,0,0),(N'ShaleClientId',N'int',4,1,0,0),(N'SystemKey',N'nvarchar',128,0,0,0),(N'Description',N'nvarchar',1000,1,0,0),(N'Color',N'nvarchar',40,0,0,0),(N'SortOrder',N'int',4,0,0,0),(N'IsActive',N'bit',1,0,0,0),(N'IsDeleted',N'bit',1,0,0,0),(N'CreatedAt',N'datetime2',8,0,0,0),(N'CreatedByUserId',N'int',4,1,0,0),(N'UpdatedAt',N'datetime2',8,1,0,0),(N'UpdatedByUserId',N'int',4,1,0,0),(N'DeletedAt',N'datetime2',8,1,0,0),(N'DeletedByUserId',N'int',4,1,0,0),(N'RowVer',N'timestamp',8,0,0,1))e(n,t,l,z,i,r) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND c.name=e.n LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR y.name<>e.t OR c.max_length<>e.l OR c.is_nullable<>e.z OR c.is_identity<>e.i OR (CASE WHEN c.system_type_id=189 THEN 1 ELSE 0 END)<>e.r)
 THROW 57019,'OrganizationTypes has incompatible required columns.',1;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name IN(N'CreatedAt',N'UpdatedAt',N'DeletedAt') AND (precision<>27 OR scale<>7)) THROW 57020,'OrganizationTypes timestamps must be datetime2(7).',1;

/* Remove every uniqueness constraint/index whose sole key is display Name. */
DECLARE names CURSOR LOCAL FAST_FORWARD FOR SELECT i.name FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND i.is_unique=1 AND i.is_primary_key=0 AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=0)=1 AND EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND c.name=N'Name');
OPEN names; FETCH NEXT FROM names INTO @DefaultName; WHILE @@FETCH_STATUS=0 BEGIN
 IF EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=@DefaultName) SET @sql=N'ALTER TABLE dbo.OrganizationTypes DROP CONSTRAINT '+QUOTENAME(@DefaultName); ELSE SET @sql=N'DROP INDEX '+QUOTENAME(@DefaultName)+N' ON dbo.OrganizationTypes'; EXEC sys.sp_executesql @sql;
 FETCH NEXT FROM names INTO @DefaultName; END CLOSE names; DEALLOCATE names;

IF NOT EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'DF_OrganizationTypes_SortOrder') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT DF_OrganizationTypes_SortOrder DEFAULT(0) FOR SortOrder;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'DF_OrganizationTypes_IsActive') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT DF_OrganizationTypes_IsActive DEFAULT(1) FOR IsActive;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'DF_OrganizationTypes_IsDeleted') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT DF_OrganizationTypes_IsDeleted DEFAULT(0) FOR IsDeleted;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'DF_OrganizationTypes_CreatedAt') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT DF_OrganizationTypes_CreatedAt DEFAULT(SYSUTCDATETIME()) FOR CreatedAt;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'DF_OrganizationTypes_Color') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT DF_OrganizationTypes_Color DEFAULT(N'#6C757D') FOR Color;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'CK_OrganizationTypes_SystemKey') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT CK_OrganizationTypes_SystemKey CHECK(SystemKey=LOWER(SystemKey) AND SystemKey<>N'' AND SystemKey NOT LIKE N'%[^a-z0-9_]%' AND LEFT(SystemKey,1) LIKE N'[a-z]');
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'CK_OrganizationTypes_Color') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT CK_OrganizationTypes_Color CHECK(Color=UPPER(Color) AND Color LIKE N'#[0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F]');
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'CK_OrganizationTypes_SortOrder') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT CK_OrganizationTypes_SortOrder CHECK(SortOrder>=0);
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'CK_OrganizationTypes_DeleteFields') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT CK_OrganizationTypes_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsActive=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL));
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'FK_OrganizationTypes_CreatedBy') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT FK_OrganizationTypes_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id);
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'FK_OrganizationTypes_UpdatedBy') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT FK_OrganizationTypes_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id);
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'FK_OrganizationTypes_DeletedBy') ALTER TABLE dbo.OrganizationTypes ADD CONSTRAINT FK_OrganizationTypes_DeletedBy FOREIGN KEY(DeletedByUserId) REFERENCES dbo.Users(id);

IF EXISTS(SELECT 1 FROM dbo.OrganizationTypes GROUP BY SystemKey HAVING COUNT(*)>1 AND MIN(CASE WHEN ShaleClientId IS NULL THEN 1 ELSE 0 END)=1) THROW 57011,'Duplicate global SystemKeys prevent uniqueness.',1;
IF EXISTS(SELECT 1 FROM dbo.OrganizationTypes WHERE ShaleClientId IS NOT NULL GROUP BY ShaleClientId,SystemKey HAVING COUNT(*)>1) THROW 57012,'Duplicate tenant SystemKeys prevent uniqueness.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'UX_OrganizationTypes_Global_SystemKey') CREATE UNIQUE INDEX UX_OrganizationTypes_Global_SystemKey ON dbo.OrganizationTypes(SystemKey) WHERE ShaleClientId IS NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationTypes') AND name=N'UX_OrganizationTypes_Tenant_SystemKey') CREATE UNIQUE INDEX UX_OrganizationTypes_Tenant_SystemKey ON dbo.OrganizationTypes(ShaleClientId,SystemKey) WHERE ShaleClientId IS NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'UX_Organizations_ShaleClientId_Id') CREATE UNIQUE INDEX UX_Organizations_ShaleClientId_Id ON dbo.Organizations(ShaleClientId,Id);

DECLARE @IsFirstAssignmentDeployment bit=CASE WHEN OBJECT_ID(N'dbo.OrganizationOrganizationTypes',N'U') IS NULL THEN 1 ELSE 0 END;
IF @IsFirstAssignmentDeployment=1 CREATE TABLE dbo.OrganizationOrganizationTypes(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_OrganizationOrganizationTypes PRIMARY KEY, ShaleClientId int NOT NULL, OrganizationId int NOT NULL, OrganizationTypeId int NOT NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_OrganizationOrganizationTypes_IsPrimary DEFAULT(0), SortOrder int NOT NULL CONSTRAINT DF_OrganizationOrganizationTypes_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_OrganizationOrganizationTypes_IsDeleted DEFAULT(0), CreatedAt datetime2 NOT NULL CONSTRAINT DF_OrganizationOrganizationTypes_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, DeletedAt datetime2 NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_OrganizationOrganizationTypes_SortOrder CHECK(SortOrder>=0),
 CONSTRAINT CK_OrganizationOrganizationTypes_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)),
 CONSTRAINT FK_OrganizationOrganizationTypes_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
 CONSTRAINT FK_OrganizationOrganizationTypes_Organization_Tenant FOREIGN KEY(ShaleClientId,OrganizationId) REFERENCES dbo.Organizations(ShaleClientId,Id),
 CONSTRAINT FK_OrganizationOrganizationTypes_Type FOREIGN KEY(OrganizationTypeId) REFERENCES dbo.OrganizationTypes(OrganizationTypeId),
 CONSTRAINT FK_OrganizationOrganizationTypes_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
 CONSTRAINT FK_OrganizationOrganizationTypes_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
 CONSTRAINT FK_OrganizationOrganizationTypes_DeletedBy FOREIGN KEY(DeletedByUserId) REFERENCES dbo.Users(id));

/* Validate compatible partial/additive objects; later phases may add unrelated columns. */
IF EXISTS(SELECT 1 FROM (VALUES(N'Id',N'bigint',8,0,1,0),(N'ShaleClientId',N'int',4,0,0,0),(N'OrganizationId',N'int',4,0,0,0),(N'OrganizationTypeId',N'int',4,0,0,0),(N'IsPrimary',N'bit',1,0,0,0),(N'SortOrder',N'int',4,0,0,0),(N'IsDeleted',N'bit',1,0,0,0),(N'CreatedAt',N'datetime2',8,0,0,0),(N'CreatedByUserId',N'int',4,1,0,0),(N'UpdatedAt',N'datetime2',8,1,0,0),(N'UpdatedByUserId',N'int',4,1,0,0),(N'DeletedAt',N'datetime2',8,1,0,0),(N'DeletedByUserId',N'int',4,1,0,0),(N'RowVer',N'timestamp',8,0,0,1))e(n,t,l,z,i,r) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.OrganizationOrganizationTypes') AND c.name=e.n LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR y.name<>e.t OR c.max_length<>e.l OR c.is_nullable<>e.z OR c.is_identity<>e.i OR (CASE WHEN c.system_type_id=189 THEN 1 ELSE 0 END)<>e.r)
 THROW 57013,'OrganizationOrganizationTypes has incompatible required columns.',1;

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationOrganizationTypes') AND name=N'UX_OrganizationOrganizationTypes_Active') CREATE UNIQUE INDEX UX_OrganizationOrganizationTypes_Active ON dbo.OrganizationOrganizationTypes(ShaleClientId,OrganizationId,OrganizationTypeId) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationOrganizationTypes') AND name=N'UX_OrganizationOrganizationTypes_ActivePrimary') CREATE UNIQUE INDEX UX_OrganizationOrganizationTypes_ActivePrimary ON dbo.OrganizationOrganizationTypes(ShaleClientId,OrganizationId) WHERE IsDeleted=0 AND IsPrimary=1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationOrganizationTypes') AND name=N'IX_OrganizationOrganizationTypes_Display') CREATE INDEX IX_OrganizationOrganizationTypes_Display ON dbo.OrganizationOrganizationTypes(ShaleClientId,OrganizationId,IsDeleted,SortOrder,Id);
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.OrganizationOrganizationTypes') AND name IN(N'CreatedAt',N'UpdatedAt',N'DeletedAt') AND (precision<>27 OR scale<>7)) THROW 57021,'Assignment timestamps must be datetime2(7).',1;

/* Named objects are owned contracts: compatible later additive objects are tolerated, but an
   incompatible object using a Phase 1A name fails rather than being silently accepted. */
IF EXISTS(SELECT 1 FROM (VALUES(N'OrganizationTypes',N'UX_OrganizationTypes_Global_SystemKey',1),(N'OrganizationTypes',N'UX_OrganizationTypes_Tenant_SystemKey',1),(N'Organizations',N'UX_Organizations_ShaleClientId_Id',1),(N'OrganizationOrganizationTypes',N'UX_OrganizationOrganizationTypes_Active',1),(N'OrganizationOrganizationTypes',N'UX_OrganizationOrganizationTypes_ActivePrimary',1),(N'OrganizationOrganizationTypes',N'IX_OrganizationOrganizationTypes_Display',0))e(t,n,u) LEFT JOIN sys.indexes i ON i.object_id=OBJECT_ID(N'dbo.'+e.t) AND i.name=e.n WHERE i.index_id IS NULL OR i.is_unique<>e.u OR i.is_disabled=1 OR i.is_hypothetical=1) THROW 57022,'Required named index is missing or incompatible.',1;
IF EXISTS(SELECT 1 FROM sys.foreign_keys f WHERE f.parent_object_id IN(OBJECT_ID(N'dbo.OrganizationTypes'),OBJECT_ID(N'dbo.OrganizationOrganizationTypes')) AND f.name IN(N'FK_OrganizationType_ShaleClient',N'FK_OrganizationTypes_CreatedBy',N'FK_OrganizationTypes_UpdatedBy',N'FK_OrganizationTypes_DeletedBy',N'FK_OrganizationOrganizationTypes_Client',N'FK_OrganizationOrganizationTypes_Organization_Tenant',N'FK_OrganizationOrganizationTypes_Type',N'FK_OrganizationOrganizationTypes_CreatedBy',N'FK_OrganizationOrganizationTypes_UpdatedBy',N'FK_OrganizationOrganizationTypes_DeletedBy') AND (f.is_disabled=1 OR f.is_not_trusted=1)) THROW 57023,'Required foreign key is disabled or untrusted.',1;

INSERT dbo.OrganizationOrganizationTypes(ShaleClientId,OrganizationId,OrganizationTypeId,IsPrimary,SortOrder,IsDeleted,CreatedAt)
SELECT o.ShaleClientId,o.Id,o.OrganizationTypeId,1,0,0,SYSUTCDATETIME() FROM dbo.Organizations o
WHERE NOT EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND a.OrganizationTypeId=o.OrganizationTypeId);
IF @IsFirstAssignmentDeployment=1 AND ((SELECT COUNT_BIG(*) FROM dbo.OrganizationOrganizationTypes WHERE IsDeleted=0 AND IsPrimary=1)<>176 OR EXISTS(SELECT 1 FROM dbo.Organizations o WHERE NOT EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND a.OrganizationTypeId=o.OrganizationTypeId AND a.IsDeleted=0 AND a.IsPrimary=1)))
 THROW 57026,'Initial assignment backfill did not create exactly 176 matching active primary assignments.',1;

/* Type ownership (global or same tenant) cannot be expressed by the simple authoritative-ID FK;
   Phase 1C must validate it transactionally. The backfill is nevertheless checked here. */
IF EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes a JOIN dbo.OrganizationTypes t ON t.OrganizationTypeId=a.OrganizationTypeId WHERE t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>a.ShaleClientId) THROW 57014,'Cross-tenant definition assignment detected.',1;

DECLARE @ExpectedRls TABLE(TableName sysname,Expected nvarchar(200)); INSERT @ExpectedRls VALUES(N'OrganizationTypes',N'sec.fn_filterbytenantorglobalshaleclientid'),(N'OrganizationOrganizationTypes',N'sec.fn_filterbytenantshaleclientid');
DECLARE @table sysname,@fn sysname; DECLARE rls CURSOR LOCAL FAST_FORWARD FOR SELECT TableName,CASE WHEN TableName=N'OrganizationTypes' THEN N'fn_FilterByTenantOrGlobal' ELSE N'fn_FilterByTenant' END FROM @ExpectedRls;
OPEN rls; FETCH NEXT FROM rls INTO @table,@fn; WHILE @@FETCH_STATUS=0 BEGIN
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.'+@table) AND object_id<>@PolicyId) THROW 57015,'A competing RLS policy predicate exists.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.'+@table)) BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.'+QUOTENAME(@fn)+N'(ShaleClientId) ON dbo.'+QUOTENAME(@table)+N';'; EXEC sys.sp_executesql @sql; END;
 FETCH NEXT FROM rls INTO @table,@fn; END CLOSE rls; DEALLOCATE rls;

IF EXISTS(SELECT 1 FROM @ExpectedRls e WHERE (SELECT COUNT(*) FROM sys.security_predicates sp WHERE sp.object_id=@PolicyId AND sp.target_object_id=OBJECT_ID(N'dbo.'+e.TableName) AND sp.predicate_type_desc=N'FILTER' AND sp.operation IS NULL AND sp.operation_desc IS NULL AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(sp.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''),N'(',N''),N')',N''))=e.Expected)<>1)
 THROW 57016,'Organization Phase 1A RLS predicate is missing or incompatible.',1;

IF EXISTS(SELECT 1 FROM #OrganizationBaseline b FULL JOIN dbo.Organizations o ON o.ShaleClientId=b.ShaleClientId AND o.Id=b.Id WHERE b.Id IS NULL OR o.Id IS NULL OR o.OrganizationTypeId<>b.OrganizationTypeId OR ISNULL(CONVERT(int,o.IsDeleted),-1)<>ISNULL(CONVERT(int,b.IsDeleted),-1) OR CONVERT(binary(8),o.RowVer)<>b.RowVer)
 THROW 57017,'An Organization row was lost or changed by the foundation migration.',1;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO

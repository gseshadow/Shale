/*
  Case Dates foundation phase 1A.

  Adds dbo.CaseDateTypes as a global/tenant overlay lookup and dbo.CaseDates
  as strict tenant-owned authoritative case date occurrences. This migration is
  database foundation only: no Java, DAO, service, UI, intake, calendar-feed, or
  fixed dbo.Cases date-column migration is included.

  Ownership decision: CaseDates stores authoritative case facts/deadlines.
  CalendarEvents remains for manually created calendar events. Do not duplicate
  case dates into CalendarEvents; a later unified calendar will project sources.

  CalendarEvents and CalendarEventTypes currently have no RLS predicates in the
  inspected foundation migration. This migration intentionally does not correct
  that unrelated security gap; create a separate focused security migration.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.ShaleClients', N'U') IS NULL THROW 55000, 'Required table dbo.ShaleClients is missing.', 1;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 55001, 'Required table dbo.Cases is missing.', 1;
IF COL_LENGTH(N'dbo.Cases', N'ShaleClientId') IS NULL THROW 55002, 'Required tenant column dbo.Cases.ShaleClientId is missing.', 1;
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL THROW 55003, 'Required table dbo.Users is missing for actor/removal metadata foreign keys.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL THROW 55004, 'Required strict predicate sec.fn_FilterByTenant is missing.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL THROW 55005, 'Required overlay predicate sec.fn_FilterByTenantOrGlobal is missing.', 1;

DECLARE @PolicySchemaName sysname, @PolicyName sysname, @PolicyQualified nvarchar(517), @PolicyObjectId int;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name = N'TenantFilter') <> 1 THROW 55006, 'Required security policy TenantFilter is missing or ambiguous.', 1;
SELECT @PolicySchemaName = SCHEMA_NAME(schema_id), @PolicyName = name, @PolicyObjectId = object_id FROM sys.security_policies WHERE name = N'TenantFilter' AND is_enabled = 1;
IF @PolicyObjectId IS NULL THROW 55007, 'Security policy TenantFilter is disabled.', 1;
SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName);

IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL
CREATE TABLE dbo.CaseDateTypes (
    Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseDateTypes PRIMARY KEY,
    ShaleClientId int NULL,
    SystemKey nvarchar(64) NULL,
    Name nvarchar(100) NOT NULL,
    Description nvarchar(500) NULL,
    CalendarCategory varchar(32) NOT NULL CONSTRAINT DF_CaseDateTypes_CalendarCategory DEFAULT ('OTHER'),
    Color nvarchar(20) NULL,
    SupportsTime bit NOT NULL CONSTRAINT DF_CaseDateTypes_SupportsTime DEFAULT (0),
    SortOrder int NOT NULL CONSTRAINT DF_CaseDateTypes_SortOrder DEFAULT (0),
    IsActive bit NOT NULL CONSTRAINT DF_CaseDateTypes_IsActive DEFAULT (1),
    IsDeleted bit NOT NULL CONSTRAINT DF_CaseDateTypes_IsDeleted DEFAULT (0),
    DeletedAt datetime2 NULL,
    DeletedByUserId int NULL,
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseDateTypes_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NULL,
    UpdatedAt datetime2 NULL,
    UpdatedByUserId int NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_CaseDateTypes_SystemKey_Lower CHECK (SystemKey IS NULL OR (SystemKey = LOWER(SystemKey) AND SystemKey NOT LIKE N'% %' AND NULLIF(LTRIM(RTRIM(SystemKey)), N'') IS NOT NULL)),
    CONSTRAINT CK_CaseDateTypes_Category CHECK (CalendarCategory IN ('DEADLINE','TRIAL','HEARING','MEDIATION','DEPOSITION','NOTICE','APPOINTMENT','MILESTONE','OTHER')),
    CONSTRAINT CK_CaseDateTypes_Color CHECK (Color IS NULL OR Color LIKE N'#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]'),
    CONSTRAINT CK_CaseDateTypes_DeletedInactive CHECK (IsDeleted = 0 OR IsActive = 0),
    CONSTRAINT CK_CaseDateTypes_DeleteFields CHECK ((IsDeleted = 0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
);

IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL
CREATE TABLE dbo.CaseDates (
    Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseDates PRIMARY KEY,
    ShaleClientId int NOT NULL,
    CaseId int NOT NULL,
    CaseDateTypeId int NOT NULL,
    StartsAt datetime2 NOT NULL,
    EndsAt datetime2 NULL,
    AllDay bit NOT NULL CONSTRAINT DF_CaseDates_AllDay DEFAULT (1),
    Notes nvarchar(max) NULL,
    IsDeleted bit NOT NULL CONSTRAINT DF_CaseDates_IsDeleted DEFAULT (0),
    DeletedAt datetime2 NULL,
    DeletedByUserId int NULL,
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseDates_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NOT NULL,
    UpdatedAt datetime2 NULL,
    UpdatedByUserId int NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_CaseDates_Range CHECK (EndsAt IS NULL OR StartsAt <= EndsAt),
    CONSTRAINT CK_CaseDates_DeleteFields CHECK ((IsDeleted = 0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
);

/* Existing table contract checks: fail clearly for incompatible partial schemas. */
DECLARE @RequiredColumns TABLE (TableName sysname, ColumnName sysname, TypeName sysname, MaxLength smallint, IsNullable bit);
INSERT INTO @RequiredColumns VALUES
(N'CaseDateTypes',N'Id',N'int',4,0),(N'CaseDateTypes',N'ShaleClientId',N'int',4,1),(N'CaseDateTypes',N'SystemKey',N'nvarchar',128,1),(N'CaseDateTypes',N'Name',N'nvarchar',200,0),(N'CaseDateTypes',N'Description',N'nvarchar',1000,1),(N'CaseDateTypes',N'CalendarCategory',N'varchar',32,0),(N'CaseDateTypes',N'Color',N'nvarchar',40,1),(N'CaseDateTypes',N'SupportsTime',N'bit',1,0),(N'CaseDateTypes',N'SortOrder',N'int',4,0),(N'CaseDateTypes',N'IsActive',N'bit',1,0),(N'CaseDateTypes',N'IsDeleted',N'bit',1,0),(N'CaseDateTypes',N'DeletedAt',N'datetime2',8,1),(N'CaseDateTypes',N'DeletedByUserId',N'int',4,1),(N'CaseDateTypes',N'CreatedAt',N'datetime2',8,0),(N'CaseDateTypes',N'CreatedByUserId',N'int',4,1),(N'CaseDateTypes',N'UpdatedAt',N'datetime2',8,1),(N'CaseDateTypes',N'UpdatedByUserId',N'int',4,1),(N'CaseDateTypes',N'RowVer',N'timestamp',8,0),
(N'CaseDates',N'Id',N'bigint',8,0),(N'CaseDates',N'ShaleClientId',N'int',4,0),(N'CaseDates',N'CaseId',N'int',4,0),(N'CaseDates',N'CaseDateTypeId',N'int',4,0),(N'CaseDates',N'StartsAt',N'datetime2',8,0),(N'CaseDates',N'EndsAt',N'datetime2',8,1),(N'CaseDates',N'AllDay',N'bit',1,0),(N'CaseDates',N'Notes',N'nvarchar',-1,1),(N'CaseDates',N'IsDeleted',N'bit',1,0),(N'CaseDates',N'DeletedAt',N'datetime2',8,1),(N'CaseDates',N'DeletedByUserId',N'int',4,1),(N'CaseDates',N'CreatedAt',N'datetime2',8,0),(N'CaseDates',N'CreatedByUserId',N'int',4,0),(N'CaseDates',N'UpdatedAt',N'datetime2',8,1),(N'CaseDates',N'UpdatedByUserId',N'int',4,1),(N'CaseDates',N'RowVer',N'timestamp',8,0);
IF EXISTS (
    SELECT 1 FROM @RequiredColumns r
    LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.' + r.TableName) AND c.name = r.ColumnName
    LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
    WHERE c.column_id IS NULL OR t.name <> r.TypeName OR c.max_length <> r.MaxLength OR c.is_nullable <> r.IsNullable
) THROW 55008, 'Existing CaseDateTypes or CaseDates table shape is incompatible with Phase 1A contract.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.Cases') AND name = N'UX_Cases_ShaleClientId_Id') CREATE UNIQUE INDEX UX_Cases_ShaleClientId_Id ON dbo.Cases (ShaleClientId, Id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDateTypes') AND name = N'UX_CaseDateTypes_Global_SystemKey') CREATE UNIQUE INDEX UX_CaseDateTypes_Global_SystemKey ON dbo.CaseDateTypes (SystemKey) WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDateTypes') AND name = N'UX_CaseDateTypes_Tenant_SystemKey') CREATE UNIQUE INDEX UX_CaseDateTypes_Tenant_SystemKey ON dbo.CaseDateTypes (ShaleClientId, SystemKey) WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDateTypes') AND name = N'IX_CaseDateTypes_Effective_SystemKey') CREATE INDEX IX_CaseDateTypes_Effective_SystemKey ON dbo.CaseDateTypes (ShaleClientId, SystemKey) INCLUDE (Name, CalendarCategory, Color, SupportsTime, SortOrder, IsActive, IsDeleted);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDateTypes') AND name = N'IX_CaseDateTypes_EffectiveList') CREATE INDEX IX_CaseDateTypes_EffectiveList ON dbo.CaseDateTypes (ShaleClientId, IsDeleted, IsActive, SortOrder, Name) INCLUDE (SystemKey, CalendarCategory, Color, SupportsTime);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDates') AND name = N'IX_CaseDates_Case_Active') CREATE INDEX IX_CaseDates_Case_Active ON dbo.CaseDates (ShaleClientId, CaseId, IsDeleted, StartsAt, Id) INCLUDE (CaseDateTypeId, EndsAt, AllDay);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDates') AND name = N'IX_CaseDates_CalendarRange_Active') CREATE INDEX IX_CaseDates_CalendarRange_Active ON dbo.CaseDates (ShaleClientId, StartsAt, EndsAt, CaseId) INCLUDE (CaseDateTypeId, AllDay) WHERE IsDeleted = 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDates') AND name = N'IX_CaseDates_Type_Usage') CREATE INDEX IX_CaseDates_Type_Usage ON dbo.CaseDates (ShaleClientId, CaseDateTypeId, IsDeleted, StartsAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseDates') AND name = N'IX_CaseDates_SoftDeletion') CREATE INDEX IX_CaseDates_SoftDeletion ON dbo.CaseDates (ShaleClientId, IsDeleted, DeletedAt) INCLUDE (CaseId, CaseDateTypeId);

IF OBJECT_ID(N'dbo.FK_CaseDateTypes_ShaleClientId_ShaleClients', N'F') IS NULL ALTER TABLE dbo.CaseDateTypes ADD CONSTRAINT FK_CaseDateTypes_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_CaseDateTypes_CreatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDateTypes ADD CONSTRAINT FK_CaseDateTypes_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_CaseDateTypes_UpdatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDateTypes ADD CONSTRAINT FK_CaseDateTypes_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_CaseDateTypes_DeletedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDateTypes ADD CONSTRAINT FK_CaseDateTypes_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_ShaleClientId_ShaleClients', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_Case_Tenant', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_Case_Tenant FOREIGN KEY (ShaleClientId, CaseId) REFERENCES dbo.Cases(ShaleClientId, Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_CaseDateTypeId_CaseDateTypes', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_CaseDateTypeId_CaseDateTypes FOREIGN KEY (CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_CreatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_UpdatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_CaseDates_DeletedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.CaseDates ADD CONSTRAINT FK_CaseDates_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);

DECLARE @Seeds TABLE (SystemKey nvarchar(64) NOT NULL, Name nvarchar(100) NOT NULL, Category varchar(32) NOT NULL, Color nvarchar(20) NOT NULL, SupportsTime bit NOT NULL, SortOrder int NOT NULL);
INSERT INTO @Seeds VALUES
(N'statute_of_limitations',N'Statute of Limitations','DEADLINE',N'#DC2626',0,10),
(N'tort_notice_deadline',N'Tort Notice Deadline','NOTICE',N'#EA580C',0,20),
(N'discovery_deadline',N'Discovery Deadline','DEADLINE',N'#D97706',0,30),
(N'date_of_injury',N'Date of Injury','MILESTONE',N'#7C3AED',0,40),
(N'date_of_medical_negligence',N'Date of Medical Negligence','MILESTONE',N'#9333EA',0,50),
(N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered','MILESTONE',N'#A855F7',0,60),
(N'trial',N'Trial','TRIAL',N'#B91C1C',1,70),
(N'hearing',N'Hearing','HEARING',N'#2563EB',1,80),
(N'mediation',N'Mediation','MEDIATION',N'#059669',1,90),
(N'deposition',N'Deposition','DEPOSITION',N'#0F766E',1,100);
INSERT dbo.CaseDateTypes (ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder)
SELECT NULL,s.SystemKey,s.Name,N'Global default case date type.',s.Category,s.Color,s.SupportsTime,s.SortOrder
FROM @Seeds s
WHERE NOT EXISTS (SELECT 1 FROM dbo.CaseDateTypes t WHERE t.ShaleClientId IS NULL AND t.SystemKey=s.SystemKey);

DECLARE @Sql nvarchar(max);
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.CaseDateTypes') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.CaseDateTypes;'; EXEC sys.sp_executesql @Sql; END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.CaseDates') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDates;'; EXEC sys.sp_executesql @Sql; END;

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
THROW;
END CATCH;
GO

/* Phase 1C service-layer validation still required: CaseDateTypeId must be either global or owned by the same ShaleClientId as CaseDates.ShaleClientId; actor users must belong to the same tenant where the live Users schema supports tenant membership; SupportsTime/AllDay consistency must be enforced transactionally by the service. */

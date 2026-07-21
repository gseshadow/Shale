/*
  Case Materials foundation phase 1.
  Adds dbo.MaterialTypes overlay lookup and strict tenant-owned dbo.MaterialRequests,
  dbo.MaterialRequestFollowUps, dbo.MaterialItems. Registers required entity-action
  audit SQL allowlist values. No DAO/service/UI/API/task/calendar/live-update/file storage.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.ShaleClients', N'U') IS NULL THROW 54700, 'Required table dbo.ShaleClients is missing.', 1;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 54701, 'Required table dbo.Cases is missing.', 1;
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL THROW 54702, 'Required table dbo.Users is missing.', 1;
IF OBJECT_ID(N'dbo.Contacts', N'U') IS NULL THROW 54703, 'Required table dbo.Contacts is missing.', 1;
IF OBJECT_ID(N'dbo.Organizations', N'U') IS NULL THROW 54704, 'Required table dbo.Organizations is missing.', 1;
IF OBJECT_ID(N'dbo.ExternalLinks', N'U') IS NULL THROW 54705, 'Required table dbo.ExternalLinks is missing.', 1;
IF OBJECT_ID(N'dbo.EntityActionAuditLog', N'U') IS NULL THROW 54706, 'Required table dbo.EntityActionAuditLog is missing.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL THROW 54707, 'Required strict predicate sec.fn_FilterByTenant is missing.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL THROW 54708, 'Required overlay predicate sec.fn_FilterByTenantOrGlobal is missing.', 1;

DECLARE @PolicySchemaName sysname, @PolicyName sysname, @PolicyQualified nvarchar(517), @PolicyObjectId int;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name = N'TenantFilter') <> 1 THROW 54709, 'Required security policy TenantFilter is missing or ambiguous.', 1;
SELECT @PolicySchemaName = SCHEMA_NAME(schema_id), @PolicyName = name, @PolicyObjectId = object_id FROM sys.security_policies WHERE name = N'TenantFilter' AND is_enabled = 1;
IF @PolicyObjectId IS NULL THROW 54710, 'Security policy TenantFilter is disabled.', 1;
SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName);

IF OBJECT_ID(N'dbo.MaterialTypes', N'U') IS NULL
CREATE TABLE dbo.MaterialTypes (
    Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaterialTypes PRIMARY KEY,
    ShaleClientId int NULL,
    SystemKey nvarchar(64) NULL,
    Name nvarchar(100) NOT NULL,
    Description nvarchar(500) NULL,
    Color nvarchar(20) NULL,
    SortOrder int NOT NULL CONSTRAINT DF_MaterialTypes_SortOrder DEFAULT (0),
    IsActive bit NOT NULL CONSTRAINT DF_MaterialTypes_IsActive DEFAULT (1),
    IsDeleted bit NOT NULL CONSTRAINT DF_MaterialTypes_IsDeleted DEFAULT (0),
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_MaterialTypes_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NULL,
    UpdatedAt datetime2 NULL,
    UpdatedByUserId int NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_MaterialTypes_SystemKey_Lower CHECK (SystemKey IS NULL OR SystemKey = LOWER(SystemKey)),
    CONSTRAINT CK_MaterialTypes_DeletedInactive CHECK (IsDeleted = 0 OR IsActive = 0)
);

IF OBJECT_ID(N'dbo.MaterialRequests', N'U') IS NULL
CREATE TABLE dbo.MaterialRequests (
    Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaterialRequests PRIMARY KEY,
    ShaleClientId int NOT NULL,
    CaseId int NOT NULL,
    MaterialTypeId int NOT NULL,
    Title nvarchar(255) NULL,
    Description nvarchar(max) NULL,
    RequestedByUserId int NOT NULL,
    AssignedToUserId int NULL,
    RequestedFromContactId int NULL,
    RequestedFromOrganizationId int NULL,
    RequestedFromText nvarchar(255) NULL,
    RequestMethod varchar(32) NOT NULL,
    RequestedAt datetime2 NOT NULL,
    RelevantStartDate date NULL,
    RelevantEndDate date NULL,
    Status varchar(32) NOT NULL CONSTRAINT DF_MaterialRequests_Status DEFAULT ('DRAFT'),
    ExpectedResponseDate date NULL,
    NextFollowUpAt datetime2 NULL,
    FirstReceivedAt datetime2 NULL,
    FullyReceivedAt datetime2 NULL,
    ClosedAt datetime2 NULL,
    ClosedByUserId int NULL,
    ClosureReason varchar(32) NULL,
    Notes nvarchar(max) NULL,
    IsDeleted bit NOT NULL CONSTRAINT DF_MaterialRequests_IsDeleted DEFAULT (0),
    DeletedAt datetime2 NULL,
    DeletedByUserId int NULL,
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_MaterialRequests_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NOT NULL,
    UpdatedAt datetime2 NULL,
    UpdatedByUserId int NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_MaterialRequests_DateRange CHECK (RelevantStartDate IS NULL OR RelevantEndDate IS NULL OR RelevantStartDate <= RelevantEndDate),
    CONSTRAINT CK_MaterialRequests_Method CHECK (RequestMethod IN ('EMAIL','PHONE','FAX','MAIL','PORTAL','IN_PERSON','OTHER')),
    CONSTRAINT CK_MaterialRequests_Status CHECK (Status IN ('DRAFT','REQUESTED','FOLLOW_UP_DUE','PARTIALLY_RECEIVED','FULLY_RECEIVED','CLOSED','CANCELLED')),
    CONSTRAINT CK_MaterialRequests_Source CHECK ((RequestedFromContactId IS NOT NULL OR RequestedFromOrganizationId IS NOT NULL) OR NULLIF(LTRIM(RTRIM(RequestedFromText)), N'') IS NOT NULL),
    CONSTRAINT CK_MaterialRequests_Closure CHECK ((Status IN ('CLOSED','CANCELLED') AND ClosedAt IS NOT NULL AND ClosedByUserId IS NOT NULL AND ClosureReason IS NOT NULL) OR (Status NOT IN ('CLOSED','CANCELLED') AND ClosedAt IS NULL AND ClosedByUserId IS NULL AND ClosureReason IS NULL)),
    CONSTRAINT CK_MaterialRequests_DeleteFields CHECK ((IsDeleted = 0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
);

IF OBJECT_ID(N'dbo.MaterialRequestFollowUps', N'U') IS NULL
CREATE TABLE dbo.MaterialRequestFollowUps (
    Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaterialRequestFollowUps PRIMARY KEY,
    ShaleClientId int NOT NULL,
    MaterialRequestId bigint NOT NULL,
    CaseId int NOT NULL,
    AttemptedAt datetime2 NOT NULL,
    AttemptedByUserId int NOT NULL,
    Method varchar(32) NOT NULL,
    Outcome varchar(64) NOT NULL,
    NextFollowUpAt datetime2 NULL,
    Notes nvarchar(max) NULL,
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_MaterialRequestFollowUps_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NOT NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_MaterialRequestFollowUps_Method CHECK (Method IN ('EMAIL','PHONE','FAX','MAIL','PORTAL','IN_PERSON','OTHER')),
    CONSTRAINT CK_MaterialRequestFollowUps_Outcome CHECK (Outcome IN ('NO_RESPONSE','LEFT_MESSAGE','CONTACTED','PROMISED','RECEIVED_PARTIAL','RECEIVED_COMPLETE','REFUSED','OTHER'))
);

IF OBJECT_ID(N'dbo.MaterialItems', N'U') IS NULL
CREATE TABLE dbo.MaterialItems (
    Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaterialItems PRIMARY KEY,
    ShaleClientId int NOT NULL,
    CaseId int NOT NULL,
    MaterialRequestId bigint NULL,
    MaterialTypeId int NOT NULL,
    Format varchar(32) NOT NULL,
    Name nvarchar(255) NOT NULL,
    Description nvarchar(max) NULL,
    SourceContactId int NULL,
    SourceOrganizationId int NULL,
    SourceText nvarchar(255) NULL,
    ReceivedByUserId int NOT NULL,
    ReceivedAt datetime2 NOT NULL,
    RelevantStartDate date NULL,
    RelevantEndDate date NULL,
    Completeness varchar(32) NOT NULL CONSTRAINT DF_MaterialItems_Completeness DEFAULT ('UNKNOWN'),
    QuantityCount int NULL,
    PageCount int NULL,
    FileCount int NULL,
    StorageLocation nvarchar(500) NULL,
    ExternalLinkId int NULL,
    PhysicalCondition nvarchar(500) NULL,
    CustodyStatus varchar(32) NOT NULL CONSTRAINT DF_MaterialItems_CustodyStatus DEFAULT ('UNKNOWN'),
    ReturnedOrReleasedAt datetime2 NULL,
    ReturnedOrReleasedToContactId int NULL,
    ReturnedOrReleasedToOrganizationId int NULL,
    ReturnedOrReleasedToText nvarchar(255) NULL,
    ReturnReleaseMethod varchar(32) NULL,
    ReturnReleaseNotes nvarchar(max) NULL,
    IsDeleted bit NOT NULL CONSTRAINT DF_MaterialItems_IsDeleted DEFAULT (0),
    DeletedAt datetime2 NULL,
    DeletedByUserId int NULL,
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_MaterialItems_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CreatedByUserId int NOT NULL,
    UpdatedAt datetime2 NULL,
    UpdatedByUserId int NULL,
    RowVer rowversion NOT NULL,
    CONSTRAINT CK_MaterialItems_Format CHECK (Format IN ('ELECTRONIC_FILE','PAPER','CD_DVD','EMAIL','PORTAL_ACCESS','PHYSICAL_OBJECT','OTHER')),
    CONSTRAINT CK_MaterialItems_DateRange CHECK (RelevantStartDate IS NULL OR RelevantEndDate IS NULL OR RelevantStartDate <= RelevantEndDate),
    CONSTRAINT CK_MaterialItems_Completeness CHECK (Completeness IN ('COMPLETE','PARTIAL','UNKNOWN','DUPLICATE','UNUSABLE','SUPERSEDED')),
    CONSTRAINT CK_MaterialItems_Counts CHECK ((QuantityCount IS NULL OR QuantityCount >= 0) AND (PageCount IS NULL OR PageCount >= 0) AND (FileCount IS NULL OR FileCount >= 0)),
    CONSTRAINT CK_MaterialItems_CustodyStatus CHECK (CustodyStatus IN ('IN_FIRM_CUSTODY','WITH_REVIEWER','RETURNED','RELEASED','DESTROYED','UNKNOWN')),
    CONSTRAINT CK_MaterialItems_Source CHECK ((SourceContactId IS NOT NULL OR SourceOrganizationId IS NOT NULL) OR NULLIF(LTRIM(RTRIM(SourceText)), N'') IS NOT NULL),
    CONSTRAINT CK_MaterialItems_ReturnRelease CHECK ((ReturnedOrReleasedAt IS NULL AND ReturnedOrReleasedToContactId IS NULL AND ReturnedOrReleasedToOrganizationId IS NULL AND ReturnedOrReleasedToText IS NULL AND ReturnReleaseMethod IS NULL AND ReturnReleaseNotes IS NULL) OR ReturnedOrReleasedAt IS NOT NULL),
    CONSTRAINT CK_MaterialItems_DeleteFields CHECK ((IsDeleted = 0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted = 1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialTypes') AND name = N'UX_MaterialTypes_ShaleClientId_SystemKey_NonNull') CREATE UNIQUE INDEX UX_MaterialTypes_ShaleClientId_SystemKey_NonNull ON dbo.MaterialTypes (ShaleClientId, SystemKey) WHERE SystemKey IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialTypes') AND name = N'IX_MaterialTypes_EffectiveList') CREATE INDEX IX_MaterialTypes_EffectiveList ON dbo.MaterialTypes (ShaleClientId, IsDeleted, IsActive, SortOrder, Name) INCLUDE (SystemKey, Color);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialRequests') AND name = N'UX_MaterialRequests_Tenant_Case_Id') CREATE UNIQUE INDEX UX_MaterialRequests_Tenant_Case_Id ON dbo.MaterialRequests (ShaleClientId, CaseId, Id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialRequests') AND name = N'IX_MaterialRequests_Case_Active') CREATE INDEX IX_MaterialRequests_Case_Active ON dbo.MaterialRequests (ShaleClientId, CaseId, IsDeleted, Status, NextFollowUpAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialRequests') AND name = N'IX_MaterialRequests_Assignee_Open') CREATE INDEX IX_MaterialRequests_Assignee_Open ON dbo.MaterialRequests (ShaleClientId, AssignedToUserId, Status, NextFollowUpAt) WHERE IsDeleted = 0 AND Status NOT IN ('FULLY_RECEIVED','CLOSED','CANCELLED');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialRequests') AND name = N'IX_MaterialRequests_Type_Status') CREATE INDEX IX_MaterialRequests_Type_Status ON dbo.MaterialRequests (ShaleClientId, MaterialTypeId, Status) WHERE IsDeleted = 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialRequestFollowUps') AND name = N'IX_MaterialRequestFollowUps_Request_Chronology') CREATE INDEX IX_MaterialRequestFollowUps_Request_Chronology ON dbo.MaterialRequestFollowUps (ShaleClientId, MaterialRequestId, AttemptedAt DESC, Id DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialItems') AND name = N'IX_MaterialItems_Case_Active') CREATE INDEX IX_MaterialItems_Case_Active ON dbo.MaterialItems (ShaleClientId, CaseId, IsDeleted, ReceivedAt DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialItems') AND name = N'IX_MaterialItems_Request') CREATE INDEX IX_MaterialItems_Request ON dbo.MaterialItems (ShaleClientId, MaterialRequestId, IsDeleted) WHERE MaterialRequestId IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialItems') AND name = N'IX_MaterialItems_Type') CREATE INDEX IX_MaterialItems_Type ON dbo.MaterialItems (ShaleClientId, MaterialTypeId, IsDeleted);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.MaterialItems') AND name = N'IX_MaterialItems_ExternalLink') CREATE INDEX IX_MaterialItems_ExternalLink ON dbo.MaterialItems (ShaleClientId, ExternalLinkId) WHERE ExternalLinkId IS NOT NULL;

/* FKs: MaterialTypes can be global; tenant/global compatibility is registered for DAO validation in later phases. */
IF OBJECT_ID(N'dbo.FK_MaterialTypes_ShaleClientId_ShaleClients', N'F') IS NULL ALTER TABLE dbo.MaterialTypes ADD CONSTRAINT FK_MaterialTypes_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_ShaleClientId_ShaleClients', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_CaseId_Cases', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_CaseId_Cases FOREIGN KEY (CaseId) REFERENCES dbo.Cases(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_MaterialTypeId_MaterialTypes', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_MaterialTypeId_MaterialTypes FOREIGN KEY (MaterialTypeId) REFERENCES dbo.MaterialTypes(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_RequestedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_RequestedByUserId_Users FOREIGN KEY (RequestedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_AssignedToUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_AssignedToUserId_Users FOREIGN KEY (AssignedToUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_RequestedFromContactId_Contacts', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_RequestedFromContactId_Contacts FOREIGN KEY (RequestedFromContactId) REFERENCES dbo.Contacts(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_RequestedFromOrganizationId_Organizations', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_RequestedFromOrganizationId_Organizations FOREIGN KEY (RequestedFromOrganizationId) REFERENCES dbo.Organizations(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_ClosedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_ClosedByUserId_Users FOREIGN KEY (ClosedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_CreatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_UpdatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequests_DeletedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT FK_MaterialRequests_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequestFollowUps_Request_Tenant_Case', N'F') IS NULL ALTER TABLE dbo.MaterialRequestFollowUps ADD CONSTRAINT FK_MaterialRequestFollowUps_Request_Tenant_Case FOREIGN KEY (ShaleClientId, CaseId, MaterialRequestId) REFERENCES dbo.MaterialRequests(ShaleClientId, CaseId, Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequestFollowUps_AttemptedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequestFollowUps ADD CONSTRAINT FK_MaterialRequestFollowUps_AttemptedByUserId_Users FOREIGN KEY (AttemptedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequestFollowUps_CreatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialRequestFollowUps ADD CONSTRAINT FK_MaterialRequestFollowUps_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_ShaleClientId_ShaleClients', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_CaseId_Cases', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_CaseId_Cases FOREIGN KEY (CaseId) REFERENCES dbo.Cases(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_Request_Tenant_Case', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_Request_Tenant_Case FOREIGN KEY (ShaleClientId, CaseId, MaterialRequestId) REFERENCES dbo.MaterialRequests(ShaleClientId, CaseId, Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_MaterialTypeId_MaterialTypes', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_MaterialTypeId_MaterialTypes FOREIGN KEY (MaterialTypeId) REFERENCES dbo.MaterialTypes(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_SourceContactId_Contacts', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_SourceContactId_Contacts FOREIGN KEY (SourceContactId) REFERENCES dbo.Contacts(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_SourceOrganizationId_Organizations', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_SourceOrganizationId_Organizations FOREIGN KEY (SourceOrganizationId) REFERENCES dbo.Organizations(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_ExternalLinkId_ExternalLinks', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_ExternalLinkId_ExternalLinks FOREIGN KEY (ExternalLinkId) REFERENCES dbo.ExternalLinks(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_ReceivedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_ReceivedByUserId_Users FOREIGN KEY (ReceivedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_CreatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_UpdatedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_MaterialItems_DeletedByUserId_Users', N'F') IS NULL ALTER TABLE dbo.MaterialItems ADD CONSTRAINT FK_MaterialItems_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);

IF NOT EXISTS (SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL AND SystemKey = N'medical_records') INSERT dbo.MaterialTypes (ShaleClientId,SystemKey,Name,Description,Color,SortOrder) VALUES (NULL,N'medical_records',N'Medical records',N'Global default material type.',N'#0891B2',10);
IF NOT EXISTS (SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL AND SystemKey = N'billing_records') INSERT dbo.MaterialTypes (ShaleClientId,SystemKey,Name,Description,Color,SortOrder) VALUES (NULL,N'billing_records',N'Billing records',N'Global default material type.',N'#0F766E',20);
IF NOT EXISTS (SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL AND SystemKey = N'police_report') INSERT dbo.MaterialTypes (ShaleClientId,SystemKey,Name,Description,Color,SortOrder) VALUES (NULL,N'police_report',N'Police report',N'Global default material type.',N'#2563EB',30);
IF NOT EXISTS (SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL AND SystemKey = N'photographs') INSERT dbo.MaterialTypes (ShaleClientId,SystemKey,Name,Description,Color,SortOrder) VALUES (NULL,N'photographs',N'Photographs',N'Global default material type.',N'#7C3AED',40);
IF NOT EXISTS (SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL AND SystemKey = N'other') INSERT dbo.MaterialTypes (ShaleClientId,SystemKey,Name,Description,Color,SortOrder) VALUES (NULL,N'other',N'Other',N'Global default material type.',N'#64748B',50);

DECLARE @Sql nvarchar(max);
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.MaterialTypes') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.MaterialTypes;'; EXEC sys.sp_executesql @Sql; END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.MaterialRequests') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequests;'; EXEC sys.sp_executesql @Sql; END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.MaterialRequestFollowUps') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequestFollowUps;'; EXEC sys.sp_executesql @Sql; END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id = @PolicyObjectId AND target_object_id = OBJECT_ID(N'dbo.MaterialItems') AND predicate_type_desc = N'FILTER') BEGIN SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialItems;'; EXEC sys.sp_executesql @Sql; END;

/* Extend SQL audit check constraints without narrowing existing values. */
IF OBJECT_ID(N'dbo.CK_EntityActionAuditLog_EntityType', N'C') IS NOT NULL ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT CK_EntityActionAuditLog_EntityType;
ALTER TABLE dbo.EntityActionAuditLog ADD CONSTRAINT CK_EntityActionAuditLog_EntityType CHECK (EntityType IN ('LINK_TYPE','CASE_LINK','CASE_LINK_SHARE','MATERIAL_TYPE','MATERIAL_REQUEST','MATERIAL_REQUEST_FOLLOW_UP','MATERIAL_ITEM'));
IF OBJECT_ID(N'dbo.CK_EntityActionAuditLog_Action', N'C') IS NOT NULL ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT CK_EntityActionAuditLog_Action;
ALTER TABLE dbo.EntityActionAuditLog ADD CONSTRAINT CK_EntityActionAuditLog_Action CHECK (Action IN ('CREATED','OVERRIDE_CREATED','UPDATED','ACTIVATED','DEACTIVATED','OVERRIDE_RESET','DELETED','PRIMARY_SET','REORDERED','ADDED','REMOVED','STATUS_CHANGED','FOLLOW_UP_ADDED','LINKED','UNLINKED','LOCATION_UPDATED','RELEASED'));

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
THROW;
END CATCH;
GO

/* Tenant 7 / tenant 8 RLS verification. Run on a session where ShaleClientId is not read_only. */
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 7;
SELECT N'Tenant 7 should see zero tenant 8 MaterialRequests' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequests WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 MaterialRequestFollowUps' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequestFollowUps WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 MaterialItems' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialItems WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see global MaterialTypes' AS CheckName, COUNT(*) AS GlobalMaterialTypeCount FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL;
SELECT N'Tenant 7 should see own MaterialTypes' AS CheckName, COUNT(*) AS TenantMaterialTypeCount FROM dbo.MaterialTypes WHERE ShaleClientId = 7;
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 8;
SELECT N'Tenant 8 should see zero tenant 7 MaterialRequests' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequests WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 MaterialRequestFollowUps' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequestFollowUps WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 MaterialItems' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialItems WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see global MaterialTypes' AS CheckName, COUNT(*) AS GlobalMaterialTypeCount FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL;
SELECT N'Tenant 8 should see own MaterialTypes' AS CheckName, COUNT(*) AS TenantMaterialTypeCount FROM dbo.MaterialTypes WHERE ShaleClientId = 8;
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
GO

/*
Effective MaterialTypes overlay behavior: current tenant can see global rows plus its own rows through sec.fn_FilterByTenantOrGlobal. Later reads must collapse by SystemKey, prefer a non-deleted current-tenant row over the global row, ignore a deleted tenant override as a reset marker so the global default is effective again, and include tenant custom rows without global SystemKey matches.
Sensitive read/open/download decision: later Case Materials list/detail/history views should use existing PHI read auditing. Electronic open/download remains deferred; add a scoped read/open/download audit extension only if PHI read audit cannot carry item/case context safely. Metadata must remain ID/state/count only.
Unresolved PHI value policy: request/item/follow-up text fields are registered now; later DAO writes should use the established PHI path and prefer redacted markers for fields whose raw old/new values are not policy-approved.
Append-only follow-ups: this phase creates no update/delete DAO path and no soft-delete fields on dbo.MaterialRequestFollowUps; corrections must be additive rows.
*/

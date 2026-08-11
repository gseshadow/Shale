/* Unified Case-date/Calendar foundation, step 1. Additive; no historical pairing. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.CalendarEvents',N'U') IS NULL THROW 55200,'Required table dbo.CalendarEvents is missing.',1;
IF OBJECT_ID(N'dbo.CalendarEventTypes',N'U') IS NULL THROW 55201,'Required table dbo.CalendarEventTypes is missing.',1;
IF OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL THROW 55202,'Required table dbo.CaseDates is missing.',1;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 55203,'Required table dbo.CaseDateTypes is missing.',1;
IF COL_LENGTH(N'dbo.CalendarEvents',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.CalendarEvents',N'CalendarEventId') IS NULL THROW 55204,'CalendarEvents tenant/id contract is missing.',1;
IF COL_LENGTH(N'dbo.CaseDates',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.CaseDates',N'Id') IS NULL THROW 55205,'CaseDates tenant/id contract is missing.',1;

/* The Calendar-owned nullable reference avoids a circular FK. */
IF COL_LENGTH(N'dbo.CalendarEvents',N'CaseDateId') IS NULL ALTER TABLE dbo.CalendarEvents ADD CaseDateId bigint NULL;
IF COL_LENGTH(N'dbo.CalendarEvents',N'RowVer') IS NULL ALTER TABLE dbo.CalendarEvents ADD RowVer rowversion NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDates') AND name=N'UX_CaseDates_ShaleClientId_Id')
 CREATE UNIQUE INDEX UX_CaseDates_ShaleClientId_Id ON dbo.CaseDates(ShaleClientId,Id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarEvents') AND name=N'UX_CalendarEvents_ActiveCaseDateLink')
 CREATE UNIQUE INDEX UX_CalendarEvents_ActiveCaseDateLink ON dbo.CalendarEvents(ShaleClientId,CaseDateId) WHERE CaseDateId IS NOT NULL;
IF OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant',N'F') IS NULL
 ALTER TABLE dbo.CalendarEvents ADD CONSTRAINT FK_CalendarEvents_CaseDate_Tenant FOREIGN KEY(ShaleClientId,CaseDateId) REFERENCES dbo.CaseDates(ShaleClientId,Id);

IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings',N'U') IS NULL
CREATE TABLE dbo.CalendarCaseDateTypeMappings(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CalendarCaseDateTypeMappings PRIMARY KEY,
 ShaleClientId int NOT NULL,
 CalendarEventTypeId int NOT NULL,
 CaseDateTypeId int NOT NULL,
 CaseDateToCalendar bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CDToCal DEFAULT(0),
 CalendarToCaseDate bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CalToCD DEFAULT(0),
 IsActive bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_IsActive DEFAULT(1),
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CreatedAt DEFAULT(SYSUTCDATETIME()),
 CreatedByUserId int NOT NULL,
 UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_CalendarCaseDateTypeMappings_Direction CHECK(CaseDateToCalendar=1 OR CalendarToCaseDate=1),
 CONSTRAINT FK_CalendarCaseDateTypeMappings_Tenant FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
 CONSTRAINT FK_CalendarCaseDateTypeMappings_EventType FOREIGN KEY(CalendarEventTypeId) REFERENCES dbo.CalendarEventTypes(CalendarEventTypeId),
 CONSTRAINT FK_CalendarCaseDateTypeMappings_DateType FOREIGN KEY(CaseDateTypeId) REFERENCES dbo.CaseDateTypes(Id),
 CONSTRAINT FK_CalendarCaseDateTypeMappings_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
 CONSTRAINT FK_CalendarCaseDateTypeMappings_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id)
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND name=N'UX_CalendarCaseDateTypeMappings_EventType')
 CREATE UNIQUE INDEX UX_CalendarCaseDateTypeMappings_EventType ON dbo.CalendarCaseDateTypeMappings(ShaleClientId,CalendarEventTypeId) WHERE IsActive=1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND name=N'UX_CalendarCaseDateTypeMappings_DateType')
 CREATE UNIQUE INDEX UX_CalendarCaseDateTypeMappings_DateType ON dbo.CalendarCaseDateTypeMappings(ShaleClientId,CaseDateTypeId) WHERE IsActive=1;

DECLARE @Policy nvarchar(517);
SELECT TOP(1) @Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @Policy IS NULL THROW 55206,'Enabled TenantFilter policy is required.',1;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND predicate_type_desc=N'FILTER')
 EXEC(N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CalendarCaseDateTypeMappings;');

/* Enforce that each mapped lookup is global or owned by the mapping tenant. */
EXEC(N'CREATE OR ALTER TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant
ON dbo.CalendarCaseDateTypeMappings AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(
  SELECT 1 FROM inserted i
  LEFT JOIN dbo.CalendarEventTypes et ON et.CalendarEventTypeId=i.CalendarEventTypeId
  LEFT JOIN dbo.CaseDateTypes dt ON dt.Id=i.CaseDateTypeId
  WHERE et.CalendarEventTypeId IS NULL OR dt.Id IS NULL
     OR (et.ShaleClientId IS NOT NULL AND et.ShaleClientId<>i.ShaleClientId)
     OR (dt.ShaleClientId IS NOT NULL AND dt.ShaleClientId<>i.ShaleClientId)
 ) THROW 55207,''Mapped types must be global or owned by the mapping tenant.'',1;
END');

/* Deliberately no mapping seeds and no modification of existing event/date rows. */
COMMIT;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH;
GO

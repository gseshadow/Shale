/* Unified Case-date/Calendar foundation, step 1. Additive; no historical pairing. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;

/* Authoritative prerequisites and deterministic RLS policy resolution. */
IF OBJECT_ID(N'dbo.CalendarEvents',N'U') IS NULL THROW 55200,'Required table dbo.CalendarEvents is missing.',1;
IF OBJECT_ID(N'dbo.CalendarEventTypes',N'U') IS NULL THROW 55201,'Required table dbo.CalendarEventTypes is missing.',1;
IF OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL THROW 55202,'Required table dbo.CaseDates is missing.',1;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 55203,'Required table dbo.CaseDateTypes is missing.',1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 55204,'Required table dbo.ShaleClients is missing.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL THROW 55205,'Required table dbo.Users is missing.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 55206,'Required inline tenant predicate sec.fn_FilterByTenant is missing.',1;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')=0 THROW 55207,'Required security policy TenantFilter is missing.',1;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')>1 THROW 55208,'Multiple security policies named TenantFilter exist; policy schema/name is ambiguous.',1;
DECLARE @PolicyObjectId int,@PolicyQualified nvarchar(517);
SELECT @PolicyObjectId=object_id,@PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyObjectId IS NULL THROW 55209,'The established TenantFilter security policy is disabled.',1;
IF (SELECT COUNT(*) FROM sys.parameters WHERE object_id=OBJECT_ID(N'sec.fn_FilterByTenant') AND parameter_id>0)<>1 OR NOT EXISTS(SELECT 1 FROM sys.parameters p JOIN sys.types t ON t.user_type_id=p.user_type_id WHERE p.object_id=OBJECT_ID(N'sec.fn_FilterByTenant') AND p.parameter_id=1 AND t.name=N'int')
 THROW 55210,'sec.fn_FilterByTenant must accept exactly one int tenant key.',1;

DECLARE @PrerequisiteColumns TABLE(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength smallint,Nullable bit);
INSERT @PrerequisiteColumns VALUES
(N'CalendarEvents',N'ShaleClientId',N'int',4,0),(N'CalendarEvents',N'CalendarEventId',N'int',4,0),
(N'CaseDates',N'ShaleClientId',N'int',4,0),(N'CaseDates',N'Id',N'bigint',8,0),
(N'CalendarEventTypes',N'CalendarEventTypeId',N'int',4,0),(N'CalendarEventTypes',N'ShaleClientId',N'int',4,1),
(N'CaseDateTypes',N'Id',N'int',4,0),(N'CaseDateTypes',N'ShaleClientId',N'int',4,1),
(N'Users',N'id',N'int',4,0),(N'Users',N'ShaleClientId',N'int',4,1);
IF EXISTS(SELECT 1 FROM @PrerequisiteColumns r LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+r.TableName) AND c.name COLLATE DATABASE_DEFAULT=r.ColumnName LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR t.name COLLATE DATABASE_DEFAULT<>r.TypeName OR c.max_length<>r.MaxLength OR c.is_nullable<>r.Nullable)
 THROW 55211,'A prerequisite ID/tenant column is missing or incompatible; inspect CalendarEvents, CaseDates, type tables, and Users column types/nullability.',1;

/* Calendar-owned nullable link and concurrency token. */
IF COL_LENGTH(N'dbo.CalendarEvents',N'CaseDateId') IS NULL ALTER TABLE dbo.CalendarEvents ADD CaseDateId bigint NULL;
ELSE IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND c.name=N'CaseDateId' AND (t.name<>N'bigint' OR c.max_length<>8 OR c.is_nullable<>1))
 THROW 55212,'Existing dbo.CalendarEvents.CaseDateId must be nullable bigint.',1;
IF COL_LENGTH(N'dbo.CalendarEvents',N'RowVer') IS NULL ALTER TABLE dbo.CalendarEvents ADD RowVer rowversion NOT NULL;
ELSE IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND c.name=N'RowVer' AND (t.name<>N'timestamp' OR c.max_length<>8 OR c.is_nullable<>0))
 THROW 55213,'Existing dbo.CalendarEvents.RowVer must be non-null rowversion.',1;

/* Definition-aware unique key needed by the composite tenant FK. */
IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDates') AND name=N'UX_CaseDates_ShaleClientId_Id')
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CaseDates') AND i.name=N'UX_CaseDates_ShaleClientId_Id' AND i.is_unique=1 AND i.has_filter=0 AND i.is_disabled=0
  AND (SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,Id')
  THROW 55214,'Index UX_CaseDates_ShaleClientId_Id exists with an incompatible definition; expected UNIQUE (ShaleClientId,Id) without a filter.',1;
END ELSE CREATE UNIQUE INDEX UX_CaseDates_ShaleClientId_Id ON dbo.CaseDates(ShaleClientId,Id);

IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarEvents') AND name=N'UX_CalendarEvents_ActiveCaseDateLink')
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND i.name=N'UX_CalendarEvents_ActiveCaseDateLink' AND i.is_unique=1 AND i.has_filter=1 AND i.is_disabled=0
  AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(i.filter_definition),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'casedateidisnotnull'
  AND (SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,CaseDateId')
  THROW 55215,'Index UX_CalendarEvents_ActiveCaseDateLink is incompatible; expected UNIQUE (ShaleClientId,CaseDateId) WHERE CaseDateId IS NOT NULL.',1;
END ELSE CREATE UNIQUE INDEX UX_CalendarEvents_ActiveCaseDateLink ON dbo.CalendarEvents(ShaleClientId,CaseDateId) WHERE CaseDateId IS NOT NULL;

IF OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant',N'F') IS NOT NULL
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk WHERE fk.object_id=OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant') AND fk.parent_object_id=OBJECT_ID(N'dbo.CalendarEvents') AND fk.referenced_object_id=OBJECT_ID(N'dbo.CaseDates') AND fk.delete_referential_action=0 AND fk.update_referential_action=0 AND fk.is_disabled=0 AND fk.is_not_trusted=0
  AND (SELECT STRING_AGG(pc.name+N'>'+rc.name,N',') WITHIN GROUP(ORDER BY fkc.constraint_column_id) FROM sys.foreign_key_columns fkc JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id WHERE fkc.constraint_object_id=fk.object_id)=N'ShaleClientId>ShaleClientId,CaseDateId>Id')
  THROW 55216,'FK_CalendarEvents_CaseDate_Tenant is incompatible; expected the non-cascading composite tenant/CaseDate foreign key.',1;
END ELSE ALTER TABLE dbo.CalendarEvents ADD CONSTRAINT FK_CalendarEvents_CaseDate_Tenant FOREIGN KEY(ShaleClientId,CaseDateId) REFERENCES dbo.CaseDates(ShaleClientId,Id);

/* Mapping table: an existing compatible partial install is completed, never recreated. */
IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') IS NOT NULL AND OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings',N'U') IS NULL THROW 55217,'dbo.CalendarCaseDateTypeMappings exists but is not a user table.',1;
IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings',N'U') IS NULL
CREATE TABLE dbo.CalendarCaseDateTypeMappings(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CalendarCaseDateTypeMappings PRIMARY KEY,
 ShaleClientId int NOT NULL, CalendarEventTypeId int NOT NULL, CaseDateTypeId int NOT NULL,
 CaseDateToCalendar bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CDToCal DEFAULT(0),
 CalendarToCaseDate bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CalToCD DEFAULT(0),
 IsActive bit NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_IsActive DEFAULT(1),
 CreatedAt datetime2 NOT NULL CONSTRAINT DF_CalendarCaseDateTypeMappings_CreatedAt DEFAULT(SYSUTCDATETIME()),
 CreatedByUserId int NOT NULL, UpdatedAt datetime2 NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_CalendarCaseDateTypeMappings_Direction CHECK(CaseDateToCalendar=1 OR CalendarToCaseDate=1));

DECLARE @MappingColumns TABLE(ColumnName sysname,TypeName sysname,MaxLength smallint,Nullable bit,IdentityFlag bit);
INSERT @MappingColumns VALUES (N'Id',N'bigint',8,0,1),(N'ShaleClientId',N'int',4,0,0),(N'CalendarEventTypeId',N'int',4,0,0),(N'CaseDateTypeId',N'int',4,0,0),(N'CaseDateToCalendar',N'bit',1,0,0),(N'CalendarToCaseDate',N'bit',1,0,0),(N'IsActive',N'bit',1,0,0),(N'CreatedAt',N'datetime2',8,0,0),(N'CreatedByUserId',N'int',4,0,0),(N'UpdatedAt',N'datetime2',8,1,0),(N'UpdatedByUserId',N'int',4,1,0),(N'RowVer',N'timestamp',8,0,0);
IF EXISTS(SELECT 1 FROM @MappingColumns r LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name COLLATE DATABASE_DEFAULT=r.ColumnName LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR t.name COLLATE DATABASE_DEFAULT<>r.TypeName OR c.max_length<>r.MaxLength OR c.is_nullable<>r.Nullable OR c.is_identity<>r.IdentityFlag)
 THROW 55218,'Existing dbo.CalendarCaseDateTypeMappings has missing or incompatible required columns; no destructive repair was attempted.',1;

/* Safely complete missing defaults/check/PK; reject incompatible same-named objects. */
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name=N'CaseDateToCalendar') ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT DF_CalendarCaseDateTypeMappings_CDToCal DEFAULT(0) FOR CaseDateToCalendar;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name=N'CalendarToCaseDate') ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT DF_CalendarCaseDateTypeMappings_CalToCD DEFAULT(0) FOR CalendarToCaseDate;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name=N'IsActive') ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT DF_CalendarCaseDateTypeMappings_IsActive DEFAULT(1) FOR IsActive;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name=N'CreatedAt') ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT DF_CalendarCaseDateTypeMappings_CreatedAt DEFAULT(SYSUTCDATETIME()) FOR CreatedAt;
IF EXISTS(SELECT 1 FROM (VALUES(N'CaseDateToCalendar',N'((0))'),(N'CalendarToCaseDate',N'((0))'),(N'IsActive',N'((1))')) v(ColumnName,Definition) JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name COLLATE DATABASE_DEFAULT=v.ColumnName JOIN sys.default_constraints d ON d.parent_object_id=c.object_id AND d.parent_column_id=c.column_id WHERE REPLACE(d.definition,N' ',N'') COLLATE DATABASE_DEFAULT<>v.Definition)
 THROW 55219,'Mapping direction/active defaults are incompatible.',1;
IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND c.name=N'CreatedAt' AND LOWER(REPLACE(d.definition,N' ',N'')) LIKE N'%sysutcdatetime()%') THROW 55220,'Mapping CreatedAt default is incompatible; expected SYSUTCDATETIME().',1;
IF OBJECT_ID(N'dbo.CK_CalendarCaseDateTypeMappings_Direction',N'C') IS NULL BEGIN IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings WHERE CaseDateToCalendar<>1 AND CalendarToCaseDate<>1) THROW 55230,'Existing mapping rows violate the required synchronization-direction contract; no constraint was added.',1; ALTER TABLE dbo.CalendarCaseDateTypeMappings WITH CHECK ADD CONSTRAINT CK_CalendarCaseDateTypeMappings_Direction CHECK(CaseDateToCalendar=1 OR CalendarToCaseDate=1); END;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND name=N'CK_CalendarCaseDateTypeMappings_Direction' AND is_disabled=0 AND is_not_trusted=0 AND LOWER(REPLACE(REPLACE(REPLACE(definition,N'[',N''),N']',N''),N' ',N'')) IN (N'(casedatetocalendar=(1)orcalendartocasedate=(1))',N'((casedatetocalendar=(1)orcalendartocasedate=(1)))')) THROW 55221,'CK_CalendarCaseDateTypeMappings_Direction is disabled, untrusted, or incompatible.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND is_primary_key=1) ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT PK_CalendarCaseDateTypeMappings PRIMARY KEY(Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND i.is_primary_key=1 AND i.is_unique=1 AND (SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'Id') THROW 55222,'Mapping table primary key is incompatible; expected Id.',1;

/* Definition-aware mapping indexes. */
DECLARE @IndexName sysname,@ExpectedKeys nvarchar(200);
IF EXISTS(SELECT ShaleClientId,CalendarEventTypeId FROM dbo.CalendarCaseDateTypeMappings WHERE IsActive=1 GROUP BY ShaleClientId,CalendarEventTypeId HAVING COUNT(*)>1) OR EXISTS(SELECT ShaleClientId,CaseDateTypeId FROM dbo.CalendarCaseDateTypeMappings WHERE IsActive=1 GROUP BY ShaleClientId,CaseDateTypeId HAVING COUNT(*)>1) THROW 55231,'Existing active mapping rows violate one-to-one type cardinality; no unique index was added.',1;
DECLARE mapping_indexes CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM (VALUES(N'UX_CalendarCaseDateTypeMappings_EventType',N'ShaleClientId,CalendarEventTypeId'),(N'UX_CalendarCaseDateTypeMappings_DateType',N'ShaleClientId,CaseDateTypeId'))v(n,k);
OPEN mapping_indexes; FETCH NEXT FROM mapping_indexes INTO @IndexName,@ExpectedKeys;
WHILE @@FETCH_STATUS=0 BEGIN
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND name COLLATE DATABASE_DEFAULT=@IndexName) AND NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND i.name COLLATE DATABASE_DEFAULT=@IndexName AND i.is_unique=1 AND i.has_filter=1 AND i.is_disabled=0 AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(i.filter_definition),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'isactive=1' AND (SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0) COLLATE DATABASE_DEFAULT=@ExpectedKeys)
  THROW 55223,'A named active mapping unique index exists with an incompatible definition.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND name COLLATE DATABASE_DEFAULT=@IndexName)
 BEGIN
  IF @IndexName=N'UX_CalendarCaseDateTypeMappings_EventType' CREATE UNIQUE INDEX UX_CalendarCaseDateTypeMappings_EventType ON dbo.CalendarCaseDateTypeMappings(ShaleClientId,CalendarEventTypeId) WHERE IsActive=1;
  ELSE CREATE UNIQUE INDEX UX_CalendarCaseDateTypeMappings_DateType ON dbo.CalendarCaseDateTypeMappings(ShaleClientId,CaseDateTypeId) WHERE IsActive=1;
 END
 FETCH NEXT FROM mapping_indexes INTO @IndexName,@ExpectedKeys;
END CLOSE mapping_indexes; DEALLOCATE mapping_indexes;

/* Single-column relationship FKs; trigger below supplies overlay-aware tenant ownership. */
IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings i LEFT JOIN dbo.ShaleClients sc ON sc.Id=i.ShaleClientId LEFT JOIN dbo.CalendarEventTypes et ON et.CalendarEventTypeId=i.CalendarEventTypeId LEFT JOIN dbo.CaseDateTypes dt ON dt.Id=i.CaseDateTypeId LEFT JOIN dbo.Users cu ON cu.id=i.CreatedByUserId LEFT JOIN dbo.Users uu ON uu.id=i.UpdatedByUserId
 WHERE sc.Id IS NULL OR et.CalendarEventTypeId IS NULL OR dt.Id IS NULL OR cu.id IS NULL OR (et.ShaleClientId IS NOT NULL AND et.ShaleClientId<>i.ShaleClientId) OR (dt.ShaleClientId IS NOT NULL AND dt.ShaleClientId<>i.ShaleClientId) OR cu.ShaleClientId IS NULL OR cu.ShaleClientId<>i.ShaleClientId OR (i.UpdatedByUserId IS NOT NULL AND (uu.id IS NULL OR uu.ShaleClientId IS NULL OR uu.ShaleClientId<>i.ShaleClientId)))
 THROW 55229,'Existing mapping rows contain cross-tenant or missing type/audit-user references; no relationship constraint was added and no data was changed.',1;
DECLARE @FkName sysname,@ParentColumn sysname,@ReferencedTable sysname,@ReferencedColumn sysname;
DECLARE mapping_fks CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM (VALUES
(N'FK_CalendarCaseDateTypeMappings_Tenant',N'ShaleClientId',N'ShaleClients',N'Id'),
(N'FK_CalendarCaseDateTypeMappings_EventType',N'CalendarEventTypeId',N'CalendarEventTypes',N'CalendarEventTypeId'),
(N'FK_CalendarCaseDateTypeMappings_DateType',N'CaseDateTypeId',N'CaseDateTypes',N'Id'),
(N'FK_CalendarCaseDateTypeMappings_CreatedBy',N'CreatedByUserId',N'Users',N'id'),
(N'FK_CalendarCaseDateTypeMappings_UpdatedBy',N'UpdatedByUserId',N'Users',N'id'))v(a,b,c,d);
OPEN mapping_fks; FETCH NEXT FROM mapping_fks INTO @FkName,@ParentColumn,@ReferencedTable,@ReferencedColumn;
WHILE @@FETCH_STATUS=0 BEGIN
 IF OBJECT_ID(N'dbo.'+@FkName,N'F') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id WHERE fk.object_id=OBJECT_ID(N'dbo.'+@FkName) AND fk.parent_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND OBJECT_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT=@ReferencedTable AND pc.name COLLATE DATABASE_DEFAULT=@ParentColumn AND rc.name COLLATE DATABASE_DEFAULT=@ReferencedColumn AND fk.delete_referential_action=0 AND fk.update_referential_action=0 AND fk.is_disabled=0 AND fk.is_not_trusted=0 AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id=fk.object_id)=1)
  THROW 55224,'A named mapping foreign key exists with incompatible columns or cascade actions.',1;
 IF OBJECT_ID(N'dbo.'+@FkName,N'F') IS NULL BEGIN
  DECLARE @FkSql nvarchar(max)=N'ALTER TABLE dbo.CalendarCaseDateTypeMappings ADD CONSTRAINT '+QUOTENAME(@FkName)+N' FOREIGN KEY('+QUOTENAME(@ParentColumn)+N') REFERENCES dbo.'+QUOTENAME(@ReferencedTable)+N'('+QUOTENAME(@ReferencedColumn)+N');'; EXEC sys.sp_executesql @FkSql;
 END
 FETCH NEXT FROM mapping_fks INTO @FkName,@ParentColumn,@ReferencedTable,@ReferencedColumn;
END CLOSE mapping_fks; DEALLOCATE mapping_fks;

/* Tenant ownership for overlay lookup IDs and both audit-user IDs. Users are not lifecycle-filtered: audit columns preserve historical actors. */
IF OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant',N'TR') IS NOT NULL
BEGIN
 DECLARE @TriggerDefinition nvarchar(max)=(SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition,N'[',N''),N']',N''),N'(',N''),N')',N''),CHAR(13),N''),CHAR(10),N''),CHAR(9),N'')) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant'));
 SET @TriggerDefinition=REPLACE(@TriggerDefinition,N' ',N'');
 IF EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant') AND (is_disabled=1 OR parent_id<>OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings'))) OR @TriggerDefinition NOT LIKE N'%createdbyuserid%' OR @TriggerDefinition NOT LIKE N'%updatedbyuserid%' OR @TriggerDefinition NOT LIKE N'%cu.shaleclientidisnullorcu.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%uu.shaleclientidisnulloruu.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%et.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%dt.shaleclientid<>i.shaleclientid%'
  THROW 55225,'TR_CalendarCaseDateTypeMappings_Tenant exists with an incompatible tenant-validation definition.',1;
END ELSE EXEC(N'CREATE TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant ON dbo.CalendarCaseDateTypeMappings AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN dbo.CalendarEventTypes et ON et.CalendarEventTypeId=i.CalendarEventTypeId LEFT JOIN dbo.CaseDateTypes dt ON dt.Id=i.CaseDateTypeId LEFT JOIN dbo.Users cu ON cu.id=i.CreatedByUserId LEFT JOIN dbo.Users uu ON uu.id=i.UpdatedByUserId
 WHERE et.CalendarEventTypeId IS NULL OR dt.Id IS NULL OR cu.id IS NULL OR (et.ShaleClientId IS NOT NULL AND et.ShaleClientId<>i.ShaleClientId) OR (dt.ShaleClientId IS NOT NULL AND dt.ShaleClientId<>i.ShaleClientId) OR cu.ShaleClientId IS NULL OR cu.ShaleClientId<>i.ShaleClientId OR (i.UpdatedByUserId IS NOT NULL AND (uu.id IS NULL OR uu.ShaleClientId IS NULL OR uu.ShaleClientId<>i.ShaleClientId)))
 THROW 55226,''Mapped types must be global/current-tenant and audit users must belong to the mapping tenant.'',1;
END');

/* Reject competing or incompatible predicates, then add the complete strict-write RLS set. */
IF EXISTS(SELECT 1 FROM sys.security_predicates p WHERE p.target_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND (p.object_id<>@PolicyObjectId OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N''))<>N'sec.fn_filterbytenantshaleclientid'))
 THROW 55227,'CalendarCaseDateTypeMappings has a competing or incompatible security predicate.',1;
DECLARE @Rls TABLE(PredicateType nvarchar(60),Operation nvarchar(60),Clause nvarchar(60));
INSERT @Rls VALUES(N'FILTER',NULL,N'FILTER'),(N'BLOCK',N'AFTER INSERT',N'BLOCK_AFTER_INSERT'),(N'BLOCK',N'BEFORE UPDATE',N'BLOCK_BEFORE_UPDATE'),(N'BLOCK',N'AFTER UPDATE',N'BLOCK_AFTER_UPDATE');
IF EXISTS(SELECT 1 FROM sys.security_predicates p WHERE p.object_id=@PolicyObjectId AND p.target_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND NOT EXISTS(SELECT 1 FROM @Rls r WHERE r.PredicateType=p.predicate_type_desc COLLATE DATABASE_DEFAULT AND (r.Operation=p.operation_desc COLLATE DATABASE_DEFAULT OR (r.Operation IS NULL AND p.operation_desc IS NULL))))
 THROW 55228,'CalendarCaseDateTypeMappings has an unsupported TenantFilter predicate operation.',1;
DECLARE @PredicateType nvarchar(60),@Operation nvarchar(60),@Clause nvarchar(60),@RlsSql nvarchar(max);
DECLARE rls_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT PredicateType,Operation,Clause FROM @Rls; OPEN rls_cursor; FETCH NEXT FROM rls_cursor INTO @PredicateType,@Operation,@Clause;
WHILE @@FETCH_STATUS=0 BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates p WHERE p.object_id=@PolicyObjectId AND p.target_object_id=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') AND p.predicate_type_desc COLLATE DATABASE_DEFAULT=@PredicateType AND (p.operation_desc COLLATE DATABASE_DEFAULT=@Operation OR (p.operation_desc IS NULL AND @Operation IS NULL)))
 BEGIN
  SET @RlsSql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD '+CASE WHEN @PredicateType=N'FILTER' THEN N'FILTER PREDICATE ' ELSE N'BLOCK PREDICATE ' END+N'sec.fn_FilterByTenant(ShaleClientId) ON dbo.CalendarCaseDateTypeMappings'+CASE @Operation WHEN N'AFTER INSERT' THEN N' AFTER INSERT' WHEN N'BEFORE UPDATE' THEN N' BEFORE UPDATE' WHEN N'AFTER UPDATE' THEN N' AFTER UPDATE' ELSE N'' END+N';'; EXEC sys.sp_executesql @RlsSql;
 END
 FETCH NEXT FROM rls_cursor INTO @PredicateType,@Operation,@Clause;
END CLOSE rls_cursor; DEALLOCATE rls_cursor;

/* Deliberately no mapping seeds and no modification of existing event/date rows. */
COMMIT;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH;
GO

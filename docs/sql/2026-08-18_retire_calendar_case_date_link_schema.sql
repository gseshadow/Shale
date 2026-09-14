/*
  Retire the obsolete Calendar Event / Case Date mapping and persisted link.
  Forward-only: restore a pre-deployment backup to reverse this migration.
  Deployment requires an all-tenant administrative connection and the application cleanup at ba3fafe7 or later.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
  BEGIN TRANSACTION;

  IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
     OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'), 0) <> 1 AND ISNULL(IS_MEMBER(N'db_owner'), 0) <> 1)
    THROW 57100, 'All-tenant administrative visibility is required and ShaleClientId session context must be NULL.', 1;
  IF OBJECT_ID(N'dbo.CalendarEvents', N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL
    THROW 57101, 'Required retained CalendarEvents or CaseDates table is missing.', 1;
  IF (SELECT COUNT(*) FROM sys.security_policies WHERE name COLLATE DATABASE_DEFAULT=N'TenantFilter') <> 1
    THROW 57102, 'Exactly one established TenantFilter security policy is required.', 1;
  DECLARE @PolicyId int, @PolicyName nvarchar(517);
  SELECT @PolicyId=object_id,@PolicyName=(QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)) COLLATE DATABASE_DEFAULT
  FROM sys.security_policies WHERE name COLLATE DATABASE_DEFAULT=N'TenantFilter' AND is_enabled=1;
  IF @PolicyId IS NULL THROW 57103, 'The established TenantFilter security policy must be enabled.', 1;
  IF @PolicyName<>N'[sec].[TenantFilter]' THROW 57103, 'The established TenantFilter policy has an incompatible schema.', 1;
  /* Administrative role membership is a deployment permission requirement, not an RLS bypass. */
  IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.CalendarEvents'))
    THROW 57103, 'CalendarEvents unexpectedly has a security predicate; all-row link validation cannot proceed.', 1;

  DECLARE @MappingId int=OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings',N'U');
  DECLARE @LinkColumnId int=(SELECT column_id FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.CalendarEvents') AND name COLLATE DATABASE_DEFAULT=N'CaseDateId');
  DECLARE @Present bit=CASE WHEN @MappingId IS NOT NULL AND @LinkColumnId IS NOT NULL THEN 1 ELSE 0 END;
  IF (@MappingId IS NULL AND @LinkColumnId IS NOT NULL) OR (@MappingId IS NOT NULL AND @LinkColumnId IS NULL)
    THROW 57104, 'Partial retired schema detected: mapping table and CalendarEvents.CaseDateId must be both present or both absent.', 1;
  IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') IS NOT NULL AND @MappingId IS NULL
    THROW 57105, 'CalendarCaseDateTypeMappings exists with an incompatible object type.', 1;
  IF @Present=1 AND NOT EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND c.column_id=@LinkColumnId AND t.name COLLATE DATABASE_DEFAULT=N'bigint' AND c.max_length=8 AND c.is_nullable=1 AND c.is_computed=0)
    THROW 57105, 'CalendarEvents.CaseDateId is incompatible; expected nullable, non-computed bigint.', 1;
  IF @Present=1 AND EXISTS(SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.CalendarEvents') AND parent_column_id=@LinkColumnId)
    THROW 57105, 'CalendarEvents.CaseDateId has an unexpected default constraint.', 1;

  /* Both supported states must retain the independent Case Date key and Calendar concurrency token. */
  IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CaseDates') AND i.name COLLATE DATABASE_DEFAULT=N'UX_CaseDates_ShaleClientId_Id' AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=0
    AND (SELECT STRING_AGG(c.name COLLATE DATABASE_DEFAULT,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,Id')
    THROW 57106, 'Retained UX_CaseDates_ShaleClientId_Id is missing or incompatible.', 1;
  IF NOT EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND c.name COLLATE DATABASE_DEFAULT=N'RowVer' AND t.name COLLATE DATABASE_DEFAULT=N'timestamp' AND c.max_length=8 AND c.is_nullable=0)
    THROW 57107, 'Retained CalendarEvents.RowVer is missing or incompatible.', 1;

  /* Reject dangling named remnants in the fully absent state. */
  IF @Present=0 BEGIN
    IF OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant') IS NOT NULL OR OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant') IS NOT NULL
       OR EXISTS(SELECT 1 FROM sys.objects WHERE name COLLATE DATABASE_DEFAULT LIKE N'%CalendarCaseDateTypeMappings%')
       OR EXISTS(SELECT 1 FROM sys.indexes WHERE name COLLATE DATABASE_DEFAULT=N'UX_CalendarEvents_ActiveCaseDateLink')
      THROW 57108, 'The retired schema is only partly absent or contains renamed/incompatible remnants.', 1;
    COMMIT TRANSACTION;
    RETURN;
  END;

  /* Complete expected mapping shape: no extra/missing columns or table-owned dependencies. */
  DECLARE @ExpectedColumns TABLE(Name sysname, TypeName sysname, MaxLength smallint, Nullable bit, IdentityFlag bit);
  INSERT @ExpectedColumns VALUES
   (N'Id',N'bigint',8,0,1),(N'ShaleClientId',N'int',4,0,0),(N'CalendarEventTypeId',N'int',4,0,0),
   (N'CaseDateTypeId',N'int',4,0,0),(N'CaseDateToCalendar',N'bit',1,0,0),(N'CalendarToCaseDate',N'bit',1,0,0),
   (N'IsActive',N'bit',1,0,0),(N'CreatedAt',N'datetime2',8,0,0),(N'CreatedByUserId',N'int',4,0,0),
   (N'UpdatedAt',N'datetime2',8,1,0),(N'UpdatedByUserId',N'int',4,1,0),(N'RowVer',N'timestamp',8,0,0);
  IF (SELECT COUNT(*) FROM sys.columns WHERE object_id=@MappingId)<>12 OR EXISTS(
    SELECT 1 FROM @ExpectedColumns e FULL JOIN (SELECT c.name COLLATE DATABASE_DEFAULT Name,t.name COLLATE DATABASE_DEFAULT TypeName,c.max_length,c.is_nullable,c.is_identity FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=@MappingId) a
      ON a.Name=e.Name WHERE e.Name IS NULL OR a.Name IS NULL OR a.TypeName<>e.TypeName OR a.max_length<>e.MaxLength OR a.is_nullable<>e.Nullable OR a.is_identity<>e.IdentityFlag)
    THROW 57109, 'CalendarCaseDateTypeMappings column shape is incomplete or incompatible.', 1;
  DECLARE @ExpectedMappingFks TABLE(Name sysname,ParentColumn sysname,ReferencedSchema sysname,ReferencedTable sysname,ReferencedColumn sysname);
  INSERT @ExpectedMappingFks VALUES
   (N'FK_CalendarCaseDateTypeMappings_Tenant',N'ShaleClientId',N'dbo',N'ShaleClients',N'Id'),
   (N'FK_CalendarCaseDateTypeMappings_EventType',N'CalendarEventTypeId',N'dbo',N'CalendarEventTypes',N'CalendarEventTypeId'),
   (N'FK_CalendarCaseDateTypeMappings_DateType',N'CaseDateTypeId',N'dbo',N'CaseDateTypes',N'Id'),
   (N'FK_CalendarCaseDateTypeMappings_CreatedBy',N'CreatedByUserId',N'dbo',N'Users',N'id'),
   (N'FK_CalendarCaseDateTypeMappings_UpdatedBy',N'UpdatedByUserId',N'dbo',N'Users',N'id');
  IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id=@MappingId)<>5 OR EXISTS(
    SELECT 1 FROM @ExpectedMappingFks e LEFT JOIN sys.foreign_keys fk ON fk.parent_object_id=@MappingId AND fk.name COLLATE DATABASE_DEFAULT=e.Name
    WHERE fk.object_id IS NULL OR fk.is_disabled=1 OR fk.is_not_trusted=1 OR fk.delete_referential_action<>0 OR fk.update_referential_action<>0
      OR OBJECT_SCHEMA_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT<>e.ReferencedSchema OR OBJECT_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT<>e.ReferencedTable
      OR (SELECT COUNT(*) FROM sys.foreign_key_columns WHERE constraint_object_id=fk.object_id)<>1
      OR NOT EXISTS(SELECT 1 FROM sys.foreign_key_columns fkc JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
        WHERE fkc.constraint_object_id=fk.object_id AND pc.name COLLATE DATABASE_DEFAULT=e.ParentColumn AND rc.name COLLATE DATABASE_DEFAULT=e.ReferencedColumn))
    THROW 57110, 'Mapping foreign keys are missing, renamed, disabled, untrusted, cascading, or have incompatible endpoints.', 1;
  IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id=@MappingId AND index_id>0)<>3
   OR NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=@MappingId AND i.name COLLATE DATABASE_DEFAULT=N'PK_CalendarCaseDateTypeMappings' AND i.is_primary_key=1 AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=0 AND (SELECT STRING_AGG(c.name COLLATE DATABASE_DEFAULT,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'Id' AND NOT EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=1))
   OR NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=@MappingId AND i.name COLLATE DATABASE_DEFAULT=N'UX_CalendarCaseDateTypeMappings_EventType' AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=1 AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(i.filter_definition COLLATE DATABASE_DEFAULT),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'isactive=1' AND (SELECT STRING_AGG(c.name COLLATE DATABASE_DEFAULT,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,CalendarEventTypeId' AND NOT EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=1))
   OR NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=@MappingId AND i.name COLLATE DATABASE_DEFAULT=N'UX_CalendarCaseDateTypeMappings_DateType' AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=1 AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(i.filter_definition COLLATE DATABASE_DEFAULT),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'isactive=1' AND (SELECT STRING_AGG(c.name COLLATE DATABASE_DEFAULT,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,CaseDateTypeId' AND NOT EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=1))
    THROW 57112, 'Mapping indexes/primary key are missing, renamed, disabled, or unexpected.', 1;
  IF (SELECT COUNT(*) FROM sys.default_constraints WHERE parent_object_id=@MappingId)<>4
   OR NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=@MappingId AND d.name COLLATE DATABASE_DEFAULT=N'DF_CalendarCaseDateTypeMappings_CDToCal' AND c.name COLLATE DATABASE_DEFAULT=N'CaseDateToCalendar' AND REPLACE(d.definition COLLATE DATABASE_DEFAULT,N' ',N'')=N'((0))')
   OR NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=@MappingId AND d.name COLLATE DATABASE_DEFAULT=N'DF_CalendarCaseDateTypeMappings_CalToCD' AND c.name COLLATE DATABASE_DEFAULT=N'CalendarToCaseDate' AND REPLACE(d.definition COLLATE DATABASE_DEFAULT,N' ',N'')=N'((0))')
   OR NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=@MappingId AND d.name COLLATE DATABASE_DEFAULT=N'DF_CalendarCaseDateTypeMappings_IsActive' AND c.name COLLATE DATABASE_DEFAULT=N'IsActive' AND REPLACE(d.definition COLLATE DATABASE_DEFAULT,N' ',N'')=N'((1))')
   OR NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id WHERE d.parent_object_id=@MappingId AND d.name COLLATE DATABASE_DEFAULT=N'DF_CalendarCaseDateTypeMappings_CreatedAt' AND c.name COLLATE DATABASE_DEFAULT=N'CreatedAt' AND LOWER(REPLACE(d.definition COLLATE DATABASE_DEFAULT,N' ',N'')) LIKE N'%sysutcdatetime()%')
   OR (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=@MappingId)<>1
   OR NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@MappingId AND name COLLATE DATABASE_DEFAULT=N'CK_CalendarCaseDateTypeMappings_Direction' AND is_disabled=0 AND is_not_trusted=0 AND LOWER(REPLACE(REPLACE(REPLACE(definition COLLATE DATABASE_DEFAULT,N'[',N''),N']',N''),N' ',N'')) IN (N'(casedatetocalendar=(1)orcalendartocasedate=(1))',N'((casedatetocalendar=(1)orcalendartocasedate=(1)))'))
    THROW 57113, 'Mapping defaults/check constraint shape is incomplete or incompatible.', 1;
  DECLARE @TriggerDefinition nvarchar(max)=(SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition COLLATE DATABASE_DEFAULT,N'[',N''),N']',N''),N'(',N''),N')',N''),N' ',N''),CHAR(13),N''),CHAR(10),N''),CHAR(9),N'')) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant'));
  IF (SELECT COUNT(*) FROM sys.triggers WHERE parent_id=@MappingId)<>1 OR NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant') AND parent_id=@MappingId AND is_disabled=0)
   OR @TriggerDefinition NOT LIKE N'%createdbyuserid%' OR @TriggerDefinition NOT LIKE N'%updatedbyuserid%' OR @TriggerDefinition NOT LIKE N'%cu.shaleclientidisnullorcu.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%uu.shaleclientidisnulloruu.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%et.shaleclientid<>i.shaleclientid%' OR @TriggerDefinition NOT LIKE N'%dt.shaleclientid<>i.shaleclientid%'
    THROW 57114, 'Mapping trigger is missing, renamed, disabled, or unexpected.', 1;

  /* Verify exactly the four strict predicates before taking any destructive action. */
  DECLARE @ExpectedRls TABLE(PredicateType nvarchar(60),Operation nvarchar(60));
  INSERT @ExpectedRls VALUES(N'FILTER',NULL),(N'BLOCK',N'AFTER INSERT'),(N'BLOCK',N'BEFORE UPDATE'),(N'BLOCK',N'AFTER UPDATE');
  IF (SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id=@MappingId)<>4 OR EXISTS(
    SELECT 1 FROM sys.security_predicates p WHERE p.target_object_id=@MappingId AND (p.object_id<>@PolicyId
      OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(p.predicate_definition COLLATE DATABASE_DEFAULT,N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N''))<>N'sec.fn_filterbytenantshaleclientid'
      OR NOT EXISTS(SELECT 1 FROM @ExpectedRls e WHERE e.PredicateType=p.predicate_type_desc COLLATE DATABASE_DEFAULT AND (e.Operation=p.operation_desc COLLATE DATABASE_DEFAULT OR e.Operation IS NULL AND p.operation_desc IS NULL))))
   OR EXISTS(SELECT 1 FROM @ExpectedRls e WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates p WHERE p.object_id=@PolicyId AND p.target_object_id=@MappingId AND p.predicate_type_desc COLLATE DATABASE_DEFAULT=e.PredicateType AND (p.operation_desc COLLATE DATABASE_DEFAULT=e.Operation OR p.operation_desc IS NULL AND e.Operation IS NULL)))
    THROW 57115, 'Expected exact four TenantFilter mapping predicates are incomplete or incompatible.', 1;
  DECLARE @UnrelatedPredicates TABLE(security_predicate_id int PRIMARY KEY, object_id int, target_object_id int, predicate_type int, operation int, definition nvarchar(max));
  INSERT @UnrelatedPredicates SELECT security_predicate_id,object_id,target_object_id,predicate_type,operation,predicate_definition COLLATE DATABASE_DEFAULT FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id<>@MappingId;

  /* Verify link dependency through index metadata (index id 13 is not CalendarEvents.SourceId column id 13). */
  IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk WHERE fk.object_id=OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant') AND fk.parent_object_id=OBJECT_ID(N'dbo.CalendarEvents') AND OBJECT_SCHEMA_NAME(fk.parent_object_id) COLLATE DATABASE_DEFAULT=N'dbo' AND fk.referenced_object_id=OBJECT_ID(N'dbo.CaseDates') AND OBJECT_SCHEMA_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT=N'dbo' AND fk.is_disabled=0 AND fk.is_not_trusted=0 AND fk.delete_referential_action=0 AND fk.update_referential_action=0
    AND (SELECT COUNT(*) FROM sys.foreign_key_columns WHERE constraint_object_id=fk.object_id)=2
    AND (SELECT STRING_AGG((pc.name COLLATE DATABASE_DEFAULT)+N'>'+(rc.name COLLATE DATABASE_DEFAULT),N',') WITHIN GROUP(ORDER BY fkc.constraint_column_id) FROM sys.foreign_key_columns fkc JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id WHERE fkc.constraint_object_id=fk.object_id)=N'ShaleClientId>ShaleClientId,CaseDateId>Id')
    THROW 57116, 'CalendarEvents to CaseDates foreign key is missing, disabled, untrusted, or incompatible.', 1;
  IF (SELECT COUNT(*) FROM sys.foreign_key_columns WHERE parent_object_id=OBJECT_ID(N'dbo.CalendarEvents') AND parent_column_id=@LinkColumnId)<>1
    THROW 57116, 'CalendarEvents.CaseDateId has an unexpected foreign key dependency.', 1;
  IF (SELECT COUNT(*) FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.column_id=@LinkColumnId))<>1
    THROW 57117, 'Exactly one CalendarEvents index may involve CaseDateId.', 1;
  DECLARE @LinkIndexId int=(SELECT i.index_id FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.CalendarEvents') AND i.name COLLATE DATABASE_DEFAULT=N'UX_CalendarEvents_ActiveCaseDateLink' AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=1
    AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(i.filter_definition COLLATE DATABASE_DEFAULT),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'casedateidisnotnull'
    AND (SELECT STRING_AGG(c.name COLLATE DATABASE_DEFAULT,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=N'ShaleClientId,CaseDateId'
    AND NOT EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=1));
  IF @LinkIndexId IS NULL
    THROW 57117, 'Verified CalendarEvents CaseDateId index is missing or incompatible.', 1;
  /* The expected filtered-index expression is reported with referencing_minor_id=@LinkIndexId.
     Do not confuse that index id (13 in the inventory) with CalendarEvents.SourceId column id 13. */
  IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d WHERE d.referenced_id=OBJECT_ID(N'dbo.CalendarEvents') AND d.referenced_minor_id=@LinkColumnId
    AND NOT (d.referencing_id=OBJECT_ID(N'dbo.CalendarEvents') AND d.referencing_minor_id=@LinkIndexId))
    THROW 57118, 'An unexpected non-index expression dependency targets CalendarEvents.CaseDateId.', 1;

  /* Transaction-owned table locks close the preflight/write race even for non-cooperating writers.
     The mapping probe is deliberately not a row-count assertion because its FILTER predicate is still active. */
  DECLARE @MappingLockProbe int,@EventLockProbe int;
  SELECT @MappingLockProbe=CHECKSUM_AGG(BINARY_CHECKSUM(Id)) FROM dbo.CalendarCaseDateTypeMappings WITH (TABLOCKX,HOLDLOCK);
  SELECT @EventLockProbe=CHECKSUM_AGG(BINARY_CHECKSUM(CalendarEventId)) FROM dbo.CalendarEvents WITH (TABLOCKX,HOLDLOCK);
  IF EXISTS(SELECT 1 FROM dbo.CalendarEvents WHERE CaseDateId IS NOT NULL) THROW 57119, 'CalendarEvents.CaseDateId contains data.', 1;
  IF EXISTS(SELECT 1 FROM dbo.CalendarEvents e LEFT JOIN dbo.CaseDates d ON d.Id=e.CaseDateId
    WHERE e.CaseDateId IS NOT NULL AND (d.Id IS NULL OR d.ShaleClientId<>e.ShaleClientId OR e.CaseId IS NULL OR d.CaseId<>e.CaseId))
   OR EXISTS(SELECT 1 FROM dbo.CalendarEvents WHERE CaseDateId IS NOT NULL GROUP BY ShaleClientId,CaseDateId HAVING COUNT_BIG(*)>1)
    THROW 57121, 'Calendar/Case Date links contain missing, cross-tenant, cross-Case, or duplicate anomalies.', 1;

  ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.CalendarCaseDateTypeMappings;
  /* Authoritative all-row mapping preflight: TABLOCKX remains transaction-owned and the FILTER is now absent.
     Any THROW reaches the CATCH rollback, which restores the predicate atomically. */
  IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings) THROW 57120, 'CalendarCaseDateTypeMappings contains data after removal of its FILTER predicate.', 1;
  ALTER SECURITY POLICY sec.TenantFilter DROP BLOCK PREDICATE ON dbo.CalendarCaseDateTypeMappings AFTER INSERT;
  ALTER SECURITY POLICY sec.TenantFilter DROP BLOCK PREDICATE ON dbo.CalendarCaseDateTypeMappings BEFORE UPDATE;
  ALTER SECURITY POLICY sec.TenantFilter DROP BLOCK PREDICATE ON dbo.CalendarCaseDateTypeMappings AFTER UPDATE;
  DROP TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant;
  ALTER TABLE dbo.CalendarCaseDateTypeMappings DROP CONSTRAINT FK_CalendarCaseDateTypeMappings_Tenant,FK_CalendarCaseDateTypeMappings_EventType,FK_CalendarCaseDateTypeMappings_DateType,FK_CalendarCaseDateTypeMappings_CreatedBy,FK_CalendarCaseDateTypeMappings_UpdatedBy;
  DROP INDEX UX_CalendarCaseDateTypeMappings_EventType ON dbo.CalendarCaseDateTypeMappings;
  DROP INDEX UX_CalendarCaseDateTypeMappings_DateType ON dbo.CalendarCaseDateTypeMappings;
  ALTER TABLE dbo.CalendarCaseDateTypeMappings DROP CONSTRAINT CK_CalendarCaseDateTypeMappings_Direction,DF_CalendarCaseDateTypeMappings_CDToCal,DF_CalendarCaseDateTypeMappings_CalToCD,DF_CalendarCaseDateTypeMappings_IsActive,DF_CalendarCaseDateTypeMappings_CreatedAt,PK_CalendarCaseDateTypeMappings;
  DROP TABLE dbo.CalendarCaseDateTypeMappings;
  ALTER TABLE dbo.CalendarEvents DROP CONSTRAINT FK_CalendarEvents_CaseDate_Tenant;
  DROP INDEX UX_CalendarEvents_ActiveCaseDateLink ON dbo.CalendarEvents;
  ALTER TABLE dbo.CalendarEvents DROP COLUMN CaseDateId;

  IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings') IS NOT NULL OR COL_LENGTH(N'dbo.CalendarEvents',N'CaseDateId') IS NOT NULL
   OR OBJECT_ID(N'dbo.TR_CalendarCaseDateTypeMappings_Tenant') IS NOT NULL OR OBJECT_ID(N'dbo.FK_CalendarEvents_CaseDate_Tenant') IS NOT NULL
   OR EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CalendarEvents') AND name COLLATE DATABASE_DEFAULT=N'UX_CalendarEvents_ActiveCaseDateLink')
    THROW 57123, 'Retired schema postcondition failed.', 1;
  IF EXISTS(SELECT security_predicate_id,object_id,target_object_id,predicate_type,operation,predicate_definition COLLATE DATABASE_DEFAULT FROM sys.security_predicates WHERE object_id=@PolicyId
            EXCEPT SELECT security_predicate_id,object_id,target_object_id,predicate_type,operation,definition COLLATE DATABASE_DEFAULT FROM @UnrelatedPredicates)
   OR EXISTS(SELECT security_predicate_id,object_id,target_object_id,predicate_type,operation,definition COLLATE DATABASE_DEFAULT FROM @UnrelatedPredicates
             EXCEPT SELECT security_predicate_id,object_id,target_object_id,predicate_type,operation,predicate_definition COLLATE DATABASE_DEFAULT FROM sys.security_predicates WHERE object_id=@PolicyId)
   OR NOT EXISTS(SELECT 1 FROM sys.security_policies WHERE object_id=@PolicyId AND is_enabled=1)
    THROW 57124, 'Unrelated TenantFilter predicates changed or the policy became disabled.', 1;

  COMMIT TRANSACTION;
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  THROW;
END CATCH;

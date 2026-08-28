/* Contacts Phase 3A -- READ-ONLY legacy-retirement readiness gate.
   Run unchanged on a restored copy first. This batch never repairs data or changes SESSION_CONTEXT.

   Operator preamble (same connection, before this batch, only when context is not already read-only):
     EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=7, @read_only=1;
     EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=<approved active admin user id>, @read_only=1;
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* OPERATOR PARAMETERS -- edit deliberately. */
DECLARE @ExpectedDatabase sysname=N'REPLACE_WITH_COPY_OR_PRODUCTION_DATABASE';
DECLARE @ExpectedShaleClientId int=7;
DECLARE @OperatorAcknowledgedReadOnlyAudit bit=0; -- disabled by default
DECLARE @ApplicationDependencyBoundaryPassed bit=0; -- set only after the Phase 2E/3A source contract passes
DECLARE @MaximumMismatchIds int=100;              -- 0 suppresses detail
/* The same-session sqlcmd wrapper supplies overrides as immutable SESSION_CONTEXT values.
   Interactive operators may instead edit the defaults above. Invalid overrides fail validation. */
SET @ExpectedDatabase=COALESCE(CONVERT(sysname,SESSION_CONTEXT(N'Phase3AExpectedDatabase')),@ExpectedDatabase);
SET @ExpectedShaleClientId=COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N'Phase3AExpectedTenantId')),@ExpectedShaleClientId);
SET @OperatorAcknowledgedReadOnlyAudit=COALESCE(TRY_CONVERT(bit,SESSION_CONTEXT(N'Phase3AOperatorAcknowledgement')),@OperatorAcknowledgedReadOnlyAudit);
SET @ApplicationDependencyBoundaryPassed=COALESCE(TRY_CONVERT(bit,SESSION_CONTEXT(N'Phase3AApplicationBoundaryAcknowledgement')),@ApplicationDependencyBoundaryPassed);
SET @MaximumMismatchIds=COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N'Phase3AMismatchIdCap')),@MaximumMismatchIds);

DECLARE @Environment TABLE(CheckCode varchar(64) PRIMARY KEY,Passed bit NOT NULL,Detail nvarchar(512) NOT NULL);
DECLARE @Schema TABLE(CheckCode varchar(64) PRIMARY KEY,Passed bit NOT NULL,Detail nvarchar(512) NOT NULL);
DECLARE @Findings TABLE(CategoryCode varchar(96) NOT NULL,ContactId int NULL,Blocking bit NOT NULL DEFAULT(1));
DECLARE @Dependencies TABLE(SchemaName sysname,ObjectName sysname,ObjectType nvarchar(60),ColumnName sysname,DependencyKind varchar(40),Allowed bit NOT NULL DEFAULT(0));
DECLARE @Legacy TABLE(ColumnName sysname PRIMARY KEY);
INSERT @Legacy VALUES(N'PhoneCell'),(N'PhoneHome'),(N'PhoneWork'),(N'EmailPersonal'),(N'EmailWork'),(N'EmailOther'),(N'AddressHome'),(N'AddressWork'),(N'AddressOther'),(N'IsExpert');

INSERT @Environment VALUES
 ('OPERATOR_ACK',IIF(@OperatorAcknowledgedReadOnlyAudit=1,1,0),N'Explicit read-only audit acknowledgement'),
 ('APPLICATION_BOUNDARY',IIF(@ApplicationDependencyBoundaryPassed=1,1,0),N'Phase 2E/3A application dependency contract passed for this build'),
 ('DATABASE_NAME',IIF(DB_NAME()=@ExpectedDatabase,1,0),N'Expected database name matches DB_NAME()'),
 ('TENANT_PARAMETER',IIF(@ExpectedShaleClientId>0,1,0),N'Expected ShaleClientId is positive'),
 ('SESSION_TENANT',IIF(TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))=@ExpectedShaleClientId AND CONVERT(nvarchar(128),SESSION_CONTEXT(N'ShaleClientId'))=CONVERT(nvarchar(128),@ExpectedShaleClientId),1,0),N'SESSION_CONTEXT ShaleClientId exactly matches'),
 ('PRINCIPAL_CONTEXT',IIF(TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))>0 AND USER_NAME() NOT IN(N'guest',N'public'),1,0),N'Authenticated principal context is present'),
 ('DETAIL_CAP',IIF(@MaximumMismatchIds BETWEEN 0 AND 1000,1,0),N'Mismatch ID cap is between 0 and 1000');

DECLARE @Required TABLE(TableName sysname,ColumnName sysname NULL);
INSERT @Required VALUES
(N'Contacts',N'Id'),(N'Contacts',N'ShaleClientId'),(N'Contacts',N'IsDeleted'),
(N'ContactPhoneNumbers',N'ContactId'),(N'ContactPhoneNumbers',N'NormalizedNumber'),(N'ContactPhoneNumbers',N'DisplayNumber'),
(N'ContactEmailAddresses',N'ContactId'),(N'ContactEmailAddresses',N'NormalizedEmail'),(N'ContactEmailAddresses',N'EmailAddress'),
(N'ContactAddresses',N'ContactId'),(N'ContactAddresses',N'LegacyAddressText'),
(N'ContactContactTypes',N'ContactTypeId'),(N'ContactTypes',N'SystemKey'),
(N'ContactSpecialties',N'SpecialtyId'),(N'ContactCredentials',N'CredentialDefinitionId'),(N'CredentialDefinitions',N'SystemKey');
INSERT @Schema
SELECT N'OBJECT_'+TableName+N'_'+ColumnName,IIF(COL_LENGTH(N'dbo.'+TableName,ColumnName) IS NOT NULL,1,0),N'Required table/column: dbo.'+TableName+N'.'+ColumnName FROM @Required;

DECLARE @LegacyPresent int=(SELECT COUNT(*) FROM @Legacy WHERE COL_LENGTH(N'dbo.Contacts',ColumnName) IS NOT NULL);
INSERT @Schema VALUES
 ('LEGACY_SCHEMA_STATE',IIF(@LegacyPresent IN(0,10),1,0),CONCAT(N'Legacy columns present: ',@LegacyPresent,N' of 10 (partial retirement fails closed)'));

DECLARE @RlsTargets TABLE(TableName sysname PRIMARY KEY);
INSERT @RlsTargets VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'),(N'ContactContactTypes'),(N'ContactSpecialties'),(N'ContactCredentials');
INSERT @Schema
SELECT N'RLS_'+r.TableName,IIF(x.n=1 AND x.bad=0,1,0),N'Exactly one enabled strict tenant FILTER predicate: dbo.'+r.TableName
FROM @RlsTargets r OUTER APPLY(SELECT COUNT(*) n,SUM(CASE WHEN sp.is_enabled=1 AND spr.predicate_type_desc=N'FILTER' AND spr.operation_desc IS NULL
 AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(spr.predicate_definition,
     N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''),N'(',N''),N')',N''))
     =N'sec.fn_filterbytenantshaleclientid' THEN 0 ELSE 1 END) bad
 FROM sys.security_predicates spr JOIN sys.security_policies sp ON sp.object_id=spr.object_id WHERE spr.target_object_id=OBJECT_ID(N'dbo.'+r.TableName))x;

/* Static parity is intentionally entered only for the complete pre-retirement shape. The
   established 2C-A rules are: meaningful = NULLIF(LTRIM(RTRIM(value)),N''); phone E.164-like
   normalization only; email lower-case only when syntactically eligible; address is lossless
   LegacyAddressText; kind/order maps are MOBILE/HOME/WORK and PERSONAL/WORK/OTHER, 0/1/2. */
IF @LegacyPresent=10 AND NOT EXISTS(SELECT 1 FROM @Environment WHERE Passed=0) AND NOT EXISTS(SELECT 1 FROM @Schema WHERE Passed=0)
BEGIN
 ;WITH L AS(SELECT c.Id,c.ShaleClientId,c.IsDeleted,v.Kind,v.Value,
   CASE WHEN v.Value LIKE N'+%' AND LEN(v.Value) BETWEEN 8 AND 16 AND SUBSTRING(v.Value,2,32) NOT LIKE N'%[^0-9]%' THEN v.Value END Normalized
  FROM dbo.Contacts c CROSS APPLY(VALUES(N'MOBILE',CONVERT(nvarchar(255),c.PhoneCell)),(N'HOME',CONVERT(nvarchar(255),c.PhoneHome)),(N'WORK',CONVERT(nvarchar(255),c.PhoneWork)))v(Kind,Value)
  WHERE c.ShaleClientId=@ExpectedShaleClientId AND NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL)
 INSERT @Findings SELECT 'PHONE_PRESERVATION_MISSING',Id,1 FROM L WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=L.ShaleClientId AND p.ContactId=L.Id AND p.Kind=L.Kind AND ((L.Normalized IS NOT NULL AND p.NormalizedNumber=L.Normalized) OR (L.Normalized IS NULL AND p.DisplayNumber=L.Value)))
 UNION ALL SELECT 'PHONE_LIVE_MISSING',Id,1 FROM L WHERE ISNULL(IsDeleted,0)=0 AND NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=L.ShaleClientId AND p.ContactId=L.Id AND p.Kind=L.Kind AND p.IsDeleted=0 AND ((L.Normalized IS NOT NULL AND p.NormalizedNumber=L.Normalized) OR (L.Normalized IS NULL AND p.DisplayNumber=L.Value)));

 ;WITH L AS(SELECT c.Id,c.ShaleClientId,c.IsDeleted,v.Kind,v.Value,CASE WHEN v.Value=LTRIM(RTRIM(v.Value)) AND v.Value NOT LIKE N'% %' AND LEN(v.Value)-LEN(REPLACE(v.Value,N'@',N''))=1 AND v.Value LIKE N'%_@_%._%' THEN LOWER(v.Value) END Normalized
  FROM dbo.Contacts c CROSS APPLY(VALUES(N'PERSONAL',CONVERT(nvarchar(320),c.EmailPersonal)),(N'WORK',CONVERT(nvarchar(320),c.EmailWork)),(N'OTHER',CONVERT(nvarchar(320),c.EmailOther)))v(Kind,Value)
  WHERE c.ShaleClientId=@ExpectedShaleClientId AND NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL)
 INSERT @Findings SELECT 'EMAIL_PRESERVATION_MISSING',Id,1 FROM L WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=L.ShaleClientId AND e.ContactId=L.Id AND e.Kind=L.Kind AND ((L.Normalized IS NOT NULL AND e.NormalizedEmail=L.Normalized) OR (L.Normalized IS NULL AND e.EmailAddress=L.Value)))
 UNION ALL SELECT 'EMAIL_LIVE_MISSING',Id,1 FROM L WHERE ISNULL(IsDeleted,0)=0 AND NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=L.ShaleClientId AND e.ContactId=L.Id AND e.Kind=L.Kind AND e.IsDeleted=0 AND ((L.Normalized IS NOT NULL AND e.NormalizedEmail=L.Normalized) OR (L.Normalized IS NULL AND e.EmailAddress=L.Value)));

 ;WITH L AS(SELECT c.Id,c.ShaleClientId,c.IsDeleted,v.Kind,v.Value FROM dbo.Contacts c CROSS APPLY(VALUES(N'HOME',CONVERT(nvarchar(max),c.AddressHome)),(N'WORK',CONVERT(nvarchar(max),c.AddressWork)),(N'OTHER',CONVERT(nvarchar(max),c.AddressOther)))v(Kind,Value)
  WHERE c.ShaleClientId=@ExpectedShaleClientId AND NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL)
 INSERT @Findings SELECT 'ADDRESS_PRESERVATION_MISSING',Id,1 FROM L WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=L.ShaleClientId AND a.ContactId=L.Id AND a.Kind=L.Kind AND a.LegacyAddressText=L.Value)
 UNION ALL SELECT 'ADDRESS_LIVE_MISSING',Id,1 FROM L WHERE ISNULL(IsDeleted,0)=0 AND NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=L.ShaleClientId AND a.ContactId=L.Id AND a.Kind=L.Kind AND a.IsDeleted=0 AND a.LegacyAddressText=L.Value);

 ;WITH E AS(SELECT c.Id,c.IsExpert,IIF(EXISTS(SELECT 1 FROM dbo.ContactContactTypes a JOIN dbo.ContactTypes d ON d.Id=a.ContactTypeId AND (d.ShaleClientId IS NULL OR d.ShaleClientId=c.ShaleClientId) WHERE a.ShaleClientId=c.ShaleClientId AND a.ContactId=c.Id AND a.IsDeleted=0 AND d.IsDeleted=0 AND d.IsActive=1 AND d.SystemKey=N'expert'),1,0) HasExpert FROM dbo.Contacts c WHERE c.ShaleClientId=@ExpectedShaleClientId AND ISNULL(c.IsDeleted,0)=0)
 INSERT @Findings SELECT 'EXPERT_LEGACY_TRUE_ASSIGNMENT_MISSING',Id,1 FROM E WHERE IsExpert=1 AND HasExpert=0 UNION ALL SELECT 'EXPERT_ASSIGNMENT_PRESENT_LEGACY_FALSE',Id,1 FROM E WHERE HasExpert=1 AND ISNULL(IsExpert,0)<>1;

 /* Structured integrity: removed history is checked only for lifecycle corruption, never for absence. */
 INSERT @Findings
 SELECT 'PHONE_TENANT_OR_ORPHAN',p.ContactId,1 FROM dbo.ContactPhoneNumbers p LEFT JOIN dbo.Contacts c ON c.Id=p.ContactId WHERE p.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR c.ShaleClientId<>p.ShaleClientId)
 UNION ALL SELECT 'EMAIL_TENANT_OR_ORPHAN',e.ContactId,1 FROM dbo.ContactEmailAddresses e LEFT JOIN dbo.Contacts c ON c.Id=e.ContactId WHERE e.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR c.ShaleClientId<>e.ShaleClientId)
 UNION ALL SELECT 'ADDRESS_TENANT_OR_ORPHAN',a.ContactId,1 FROM dbo.ContactAddresses a LEFT JOIN dbo.Contacts c ON c.Id=a.ContactId WHERE a.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR c.ShaleClientId<>a.ShaleClientId)
 UNION ALL SELECT 'PHONE_DUPLICATE_ACTIVE_PRIMARY',ContactId,1 FROM dbo.ContactPhoneNumbers WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 AND IsPrimary=1 GROUP BY ContactId HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'EMAIL_DUPLICATE_ACTIVE_PRIMARY',ContactId,1 FROM dbo.ContactEmailAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 AND IsPrimary=1 GROUP BY ContactId HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'ADDRESS_DUPLICATE_ACTIVE_PRIMARY_KIND',ContactId,1 FROM dbo.ContactAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 AND IsPrimary=1 GROUP BY ContactId,Kind HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'PHONE_BLANK_DISPLAY',ContactId,1 FROM dbo.ContactPhoneNumbers WHERE ShaleClientId=@ExpectedShaleClientId AND NULLIF(LTRIM(RTRIM(DisplayNumber)),N'') IS NULL
 UNION ALL SELECT 'EMAIL_BLANK_PRESENTATION',ContactId,1 FROM dbo.ContactEmailAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND NULLIF(LTRIM(RTRIM(EmailAddress)),N'') IS NULL
 UNION ALL SELECT 'ADDRESS_BLANK_PRESENTATION',ContactId,1 FROM dbo.ContactAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND AddressLine1 IS NULL AND AddressLine2 IS NULL AND City IS NULL AND StateOrProvince IS NULL AND PostalCode IS NULL AND CountryCode IS NULL AND LegacyAddressText IS NULL
 UNION ALL SELECT 'PHONE_INVALID_NORMALIZED',ContactId,1 FROM dbo.ContactPhoneNumbers WHERE ShaleClientId=@ExpectedShaleClientId AND (NormalizedNumber IS NOT NULL AND (NormalizedNumber NOT LIKE N'+%' OR SUBSTRING(NormalizedNumber,2,32) LIKE N'%[^0-9]%'))
 UNION ALL SELECT 'EMAIL_INVALID_NORMALIZED',ContactId,1 FROM dbo.ContactEmailAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND (NormalizedEmail IS NOT NULL AND (NormalizedEmail<>LOWER(EmailAddress) OR EmailAddress LIKE N'% %'))
 UNION ALL SELECT 'CONTACT_POINT_INVALID_SORT_ORDER',ContactId,1 FROM dbo.ContactPhoneNumbers WHERE ShaleClientId=@ExpectedShaleClientId AND SortOrder<0
 UNION ALL SELECT 'CONTACT_POINT_INVALID_SORT_ORDER',ContactId,1 FROM dbo.ContactEmailAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND SortOrder<0
 UNION ALL SELECT 'CONTACT_POINT_INVALID_SORT_ORDER',ContactId,1 FROM dbo.ContactAddresses WHERE ShaleClientId=@ExpectedShaleClientId AND SortOrder<0;

 INSERT @Findings
 SELECT 'TYPE_ORPHAN_OR_TENANT_MISMATCH',a.ContactId,1 FROM dbo.ContactContactTypes a LEFT JOIN dbo.Contacts c ON c.Id=a.ContactId AND c.ShaleClientId=a.ShaleClientId LEFT JOIN dbo.ContactTypes d ON d.Id=a.ContactTypeId AND (d.ShaleClientId IS NULL OR d.ShaleClientId=a.ShaleClientId) WHERE a.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR d.Id IS NULL)
 UNION ALL SELECT 'SPECIALTY_ORPHAN_ASSIGNMENT',a.ContactId,1 FROM dbo.ContactSpecialties a LEFT JOIN dbo.Contacts c ON c.Id=a.ContactId AND c.ShaleClientId=a.ShaleClientId LEFT JOIN dbo.Specialties d ON d.Id=a.SpecialtyId AND (d.ShaleClientId IS NULL OR d.ShaleClientId=a.ShaleClientId) WHERE a.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR d.Id IS NULL)
 UNION ALL SELECT 'CREDENTIAL_ORPHAN_ASSIGNMENT',a.ContactId,1 FROM dbo.ContactCredentials a LEFT JOIN dbo.Contacts c ON c.Id=a.ContactId AND c.ShaleClientId=a.ShaleClientId LEFT JOIN dbo.CredentialDefinitions d ON d.Id=a.CredentialDefinitionId AND (d.ShaleClientId IS NULL OR d.ShaleClientId=a.ShaleClientId) WHERE a.ShaleClientId=@ExpectedShaleClientId AND (c.Id IS NULL OR d.Id IS NULL)
 UNION ALL SELECT 'TYPE_DUPLICATE_ACTIVE_ASSIGNMENT',ContactId,1 FROM dbo.ContactContactTypes WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 GROUP BY ContactId,ContactTypeId HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'SPECIALTY_DUPLICATE_ACTIVE_ASSIGNMENT',ContactId,1 FROM dbo.ContactSpecialties WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 GROUP BY ContactId,SpecialtyId HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'CREDENTIAL_DUPLICATE_ACTIVE_ASSIGNMENT',ContactId,1 FROM dbo.ContactCredentials WHERE ShaleClientId=@ExpectedShaleClientId AND IsDeleted=0 GROUP BY ContactId,CredentialDefinitionId HAVING COUNT_BIG(*)>1
 UNION ALL SELECT 'CREDENTIAL_INVALID_DISPLAY_ORDER',ContactId,1 FROM dbo.ContactCredentials WHERE ShaleClientId=@ExpectedShaleClientId AND DisplayOrder<0
 UNION ALL SELECT 'EXPERT_INVALID_DEFINITION',a.ContactId,1 FROM dbo.ContactContactTypes a JOIN dbo.ContactTypes d ON d.Id=a.ContactTypeId WHERE a.ShaleClientId=@ExpectedShaleClientId AND a.IsDeleted=0 AND d.SystemKey=N'expert' AND (d.IsDeleted=1 OR d.IsActive=0 OR (d.ShaleClientId IS NOT NULL AND d.ShaleClientId<>a.ShaleClientId));

 INSERT @Findings SELECT 'ACTIVE_LEGACY_WITHOUT_USABLE_STRUCTURED_POINT',c.Id,1 FROM dbo.Contacts c WHERE c.ShaleClientId=@ExpectedShaleClientId AND ISNULL(c.IsDeleted,0)=0
 AND (NULLIF(LTRIM(RTRIM(c.PhoneCell)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.PhoneHome)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.PhoneWork)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.EmailPersonal)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.EmailWork)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.EmailOther)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.AddressHome)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.AddressWork)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(c.AddressOther)),N'') IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=c.ShaleClientId AND p.ContactId=c.Id AND p.IsDeleted=0 AND NULLIF(LTRIM(RTRIM(p.DisplayNumber)),N'') IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=c.ShaleClientId AND e.ContactId=c.Id AND e.IsDeleted=0 AND NULLIF(LTRIM(RTRIM(e.EmailAddress)),N'') IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=c.ShaleClientId AND a.ContactId=c.Id AND a.IsDeleted=0);
END;

/* Database dependency inventory: expression dependencies plus metadata-only objects not
   reliably represented there. Only this verification batch is allowlisted (and is not persisted). */
INSERT @Dependencies
SELECT DISTINCT OBJECT_SCHEMA_NAME(d.referencing_id),OBJECT_NAME(d.referencing_id),o.type_desc,l.ColumnName,'SQL_EXPRESSION',0
FROM sys.sql_expression_dependencies d JOIN sys.objects o ON o.object_id=d.referencing_id JOIN @Legacy l ON d.referenced_id=OBJECT_ID(N'dbo.Contacts') AND d.referenced_minor_id=COLUMNPROPERTY(OBJECT_ID(N'dbo.Contacts'),l.ColumnName,'ColumnId');
INSERT @Dependencies SELECT DISTINCT SCHEMA_NAME(o.schema_id),o.name,o.type_desc,l.ColumnName,'MODULE_TEXT',0 FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id CROSS JOIN @Legacy l WHERE m.definition LIKE N'%'+l.ColumnName+N'%';
INSERT @Dependencies SELECT SCHEMA_NAME(t.schema_id),t.name,N'COMPUTED_COLUMN',c.name,'COMPUTED_COLUMN',0 FROM sys.computed_columns c JOIN sys.tables t ON t.object_id=c.object_id CROSS JOIN @Legacy l WHERE c.definition LIKE N'%'+l.ColumnName+N'%';
INSERT @Dependencies SELECT SCHEMA_NAME(t.schema_id),t.name,N'CHECK_CONSTRAINT',l.ColumnName,'CHECK_CONSTRAINT',0 FROM sys.check_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id CROSS JOIN @Legacy l WHERE c.definition LIKE N'%'+l.ColumnName+N'%';
INSERT @Dependencies SELECT SCHEMA_NAME(t.schema_id),t.name,N'DEFAULT_CONSTRAINT',l.ColumnName,'DEFAULT_CONSTRAINT',0 FROM sys.default_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id CROSS JOIN @Legacy l WHERE c.definition LIKE N'%'+l.ColumnName+N'%';
INSERT @Dependencies SELECT SCHEMA_NAME(t.schema_id),i.name,N'INDEX',c.name,'INDEX_KEY_INCLUDE_OR_FILTER',0 FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id JOIN @Legacy l ON c.name=l.ColumnName WHERE i.object_id=OBJECT_ID(N'dbo.Contacts') OR i.filter_definition LIKE N'%'+l.ColumnName+N'%';
INSERT @Dependencies SELECT SCHEMA_NAME(t.schema_id),fk.name,N'FOREIGN_KEY',c.name,'FOREIGN_KEY',0 FROM sys.foreign_key_columns fc JOIN sys.foreign_keys fk ON fk.object_id=fc.constraint_object_id JOIN sys.tables t ON t.object_id=fc.parent_object_id JOIN sys.columns c ON c.object_id=fc.parent_object_id AND c.column_id=fc.parent_column_id JOIN @Legacy l ON c.name=l.ColumnName WHERE fc.parent_object_id=OBJECT_ID(N'dbo.Contacts');
INSERT @Dependencies SELECT SCHEMA_NAME(sp.schema_id),sp.name,N'SECURITY_POLICY',l.ColumnName,'SECURITY_PREDICATE',0 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id CROSS JOIN @Legacy l WHERE p.predicate_definition LIKE N'%'+l.ColumnName+N'%';

/* Deterministic result contract. No PHI-bearing value column is projected. */
SELECT CheckCode,Passed,Detail FROM @Environment ORDER BY CheckCode;                                      -- 1
SELECT CheckCode,Passed,Detail FROM @Schema ORDER BY CheckCode;                                           -- 2
SELECT CategoryCode,COUNT_BIG(*) FindingCount FROM @Findings WHERE CategoryCode LIKE N'%PRESERVATION%' OR CategoryCode LIKE N'%LIVE%' OR CategoryCode LIKE N'EXPERT_%' GROUP BY CategoryCode ORDER BY CategoryCode; -- 3
SELECT CategoryCode,COUNT_BIG(*) FindingCount FROM @Findings WHERE CategoryCode NOT LIKE N'%PRESERVATION%' AND CategoryCode NOT LIKE N'%LIVE%' AND CategoryCode NOT LIKE N'EXPERT_%' GROUP BY CategoryCode ORDER BY CategoryCode; -- 4
SELECT SchemaName,ObjectName,ObjectType,ColumnName,DependencyKind FROM @Dependencies WHERE Allowed=0 ORDER BY SchemaName,ObjectName,ObjectType,ColumnName,DependencyKind; -- 5
SELECT CategoryCode,ContactId FROM(SELECT CategoryCode,ContactId,ROW_NUMBER() OVER(ORDER BY CategoryCode,ContactId) rn FROM @Findings WHERE ContactId IS NOT NULL)d WHERE rn<=@MaximumMismatchIds ORDER BY CategoryCode,ContactId; -- 6
DECLARE @Ready bit=IIF(NOT EXISTS(SELECT 1 FROM @Environment WHERE Passed=0) AND NOT EXISTS(SELECT 1 FROM @Schema WHERE Passed=0) AND NOT EXISTS(SELECT 1 FROM @Findings WHERE Blocking=1) AND NOT EXISTS(SELECT 1 FROM @Dependencies WHERE Allowed=0),1,0);
SELECT IIF(@Ready=1,N'PASS_READY_FOR_PHASE_3B',N'FAIL_NOT_READY') AS FinalReadiness;                       -- 7
IF @Ready<>1 THROW 56630,'Contacts Phase 3A readiness audit failed; review the PHI-safe result sets.',1;

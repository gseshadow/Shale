/* Read-only, PHI-free deployment verification. No values or row identifiers are returned.
   Run under the independently approved administrative visibility contract, then test tenant RLS
   with two disposable tenants in a non-production database. This script does not bypass RLS. */
SET NOCOUNT ON;
SELECT CASE WHEN OBJECT_ID(N'dbo.IntakePhoneAvailability',N'U') IS NULL THEN 1 ELSE 0 END AS MissingAvailabilityTableFindingCount;
SELECT COUNT(*) AS MissingMirrorCapacityFindingCount FROM (VALUES(N'Phone',510),(N'Fax',510),(N'Email',640)) expected(ColumnName,RequiredBytes)
WHERE NOT EXISTS(SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.Organizations') AND c.name=expected.ColumnName AND TYPE_NAME(c.user_type_id)=N'nvarchar' AND (c.max_length=-1 OR c.max_length>=expected.RequiredBytes));
SELECT CASE WHEN EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND p.predicate_type_desc=N'FILTER' AND s.is_enabled=1
 AND p.predicate_definition LIKE N'%fn_FilterByTenant%') THEN 0 ELSE 1 END AS MissingTenantPredicateFindingCount;
SELECT CASE WHEN EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND name=N'UX_OrganizationPhoneNumbers_ActiveValueExtension' AND is_unique=1 AND has_filter=1)
 THEN 0 ELSE 1 END AS MissingExtensionIndexFindingCount;
SELECT COUNT(*) AS UntrustedAvailabilityForeignKeyFindingCount FROM sys.foreign_keys
 WHERE parent_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND (is_disabled=1 OR is_not_trusted=1);
/* No automatic syntax audit in SQL: use the pinned shared parser on a secured read-only export.
   Broad SQL regex substitutes cannot represent international numbering plans or email domains. */

SELECT CASE WHEN (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability')
 AND name IN(N'FK_IntakePhoneAvailability_Case',N'FK_IntakePhoneAvailability_Contact',N'FK_IntakePhoneAvailability_Actor') AND is_disabled=0 AND is_not_trusted=0)=3 THEN 0 ELSE 1 END AS MissingAvailabilityOwnershipFindingCount;
SELECT 2-COUNT(*) AS MissingAvailabilityWritePredicateFindingCount FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND p.predicate_type_desc=N'BLOCK' AND p.operation_desc IN(N'AFTER INSERT',N'AFTER UPDATE') AND s.is_enabled=1 AND p.predicate_definition LIKE N'%fn_FilterByTenant%';
SELECT COUNT(*) AS AvailabilityColumnContractFindingCount FROM (VALUES
 (N'Id',N'bigint',8,0),(N'ShaleClientId',N'int',4,0),(N'CaseId',N'int',4,0),(N'ContactId',N'int',4,0),(N'IntakeRole',N'nvarchar',12,0),(N'UnavailableReason',N'nvarchar',24,1),
 (N'CreatedAt',N'datetime2',8,0),(N'CreatedByUserId',N'int',4,0),(N'RowVer',N'timestamp',8,0)) expected(ColumnName,TypeName,Bytes,Nullable)
 WHERE NOT EXISTS(SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND c.name=expected.ColumnName AND TYPE_NAME(c.user_type_id)=expected.TypeName AND c.max_length=expected.Bytes AND c.is_nullable=expected.Nullable);
SELECT CASE WHEN (SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) FROM sys.indexes i JOIN sys.index_columns ic ON i.object_id=ic.object_id AND i.index_id=ic.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE i.object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND i.name=N'UX_OrganizationPhoneNumbers_ActiveValueExtension' AND ic.key_ordinal>0)=N'ShaleClientId,OrganizationId,Kind,DisplayNumber,Extension' THEN 0 ELSE 1 END AS ExtensionIndexColumnFindingCount;

SELECT COUNT(*) AS AvailabilityForeignKeyMappingFindingCount FROM (VALUES
 (N'FK_IntakePhoneAvailability_Case',N'Cases',N'CaseId'),(N'FK_IntakePhoneAvailability_Contact',N'Contacts',N'ContactId'),(N'FK_IntakePhoneAvailability_Actor',N'Users',N'CreatedByUserId')) expected(ForeignKeyName,ParentTable,IdColumn)
 WHERE NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk WHERE fk.parent_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND fk.name=expected.ForeignKeyName AND fk.referenced_object_id=OBJECT_ID(N'dbo.'+expected.ParentTable)
  AND (SELECT COUNT(*) FROM sys.foreign_key_columns fc WHERE fc.constraint_object_id=fk.object_id)=2
  AND EXISTS(SELECT 1 FROM sys.foreign_key_columns fc WHERE fc.constraint_object_id=fk.object_id AND COL_NAME(fc.parent_object_id,fc.parent_column_id)=N'ShaleClientId' AND COL_NAME(fc.referenced_object_id,fc.referenced_column_id)=N'ShaleClientId')
  AND EXISTS(SELECT 1 FROM sys.foreign_key_columns fc WHERE fc.constraint_object_id=fk.object_id AND COL_NAME(fc.parent_object_id,fc.parent_column_id)=expected.IdColumn AND COL_NAME(fc.referenced_object_id,fc.referenced_column_id)=N'Id'));
SELECT CASE WHEN EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND name=N'UX_OrganizationPhoneNumbers_ActiveValueExtension'
 AND is_unique=1 AND is_disabled=0 AND REPLACE(REPLACE(REPLACE(REPLACE(filter_definition,N' ',N''),N'[',N''),N']',N''),N'(',N'') LIKE N'IsDeleted=0%') THEN 0 ELSE 1 END AS ExtensionIndexActiveFilterFindingCount;

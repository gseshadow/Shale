/* Read-only, all-tenant verification. Run with approved migration visibility and no tenant context. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL THROW 56200, 'Verification requires an unset ShaleClientId session context.', 1;

SELECT t.name AS TableName,c.name AS ColumnName,TYPE_NAME(c.user_type_id) AS SqlType,c.max_length,c.is_nullable
FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
WHERE SCHEMA_NAME(t.schema_id)=N'dbo' AND t.name IN(N'Contacts',N'ContactTypes',N'Specialties',N'CredentialDefinitions',N'ContactContactTypes',N'ContactSpecialties',N'ContactCredentials')
ORDER BY t.name,c.column_id;

SELECT OBJECT_NAME(i.object_id) TableName,i.name,i.is_unique,i.has_filter,i.filter_definition
FROM sys.indexes i WHERE i.object_id IN(OBJECT_ID(N'dbo.Contacts'),OBJECT_ID(N'dbo.ContactTypes'),OBJECT_ID(N'dbo.Specialties'),OBJECT_ID(N'dbo.CredentialDefinitions'),OBJECT_ID(N'dbo.ContactContactTypes'),OBJECT_ID(N'dbo.ContactSpecialties'),OBJECT_ID(N'dbo.ContactCredentials')) AND i.name IS NOT NULL ORDER BY 1,2;
SELECT OBJECT_NAME(f.parent_object_id) TableName,f.name,OBJECT_NAME(f.referenced_object_id) ReferencedTable
FROM sys.foreign_keys f WHERE f.parent_object_id IN(OBJECT_ID(N'dbo.ContactTypes'),OBJECT_ID(N'dbo.Specialties'),OBJECT_ID(N'dbo.CredentialDefinitions'),OBJECT_ID(N'dbo.ContactContactTypes'),OBJECT_ID(N'dbo.ContactSpecialties'),OBJECT_ID(N'dbo.ContactCredentials')) ORDER BY 1,2;

SELECT 'expert global seed (expect exactly 1)' CheckName,COUNT(*) FindingCount FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert';
SELECT 'missing expert assignments (expect 0)' CheckName,COUNT(*) FindingCount FROM dbo.Contacts c WHERE c.IsExpert=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactContactTypes a JOIN dbo.ContactTypes t ON t.Id=a.ContactTypeId WHERE a.ShaleClientId=c.ShaleClientId AND a.ContactId=c.Id AND a.IsDeleted=0 AND t.ShaleClientId IS NULL AND t.SystemKey=N'expert');
SELECT 'expert assignments for active contacts' Population,COUNT(*) FindingCount FROM dbo.ContactContactTypes a JOIN dbo.Contacts c ON c.Id=a.ContactId AND c.ShaleClientId=a.ShaleClientId JOIN dbo.ContactTypes t ON t.Id=a.ContactTypeId WHERE c.IsExpert=1 AND c.IsDeleted=0 AND a.IsDeleted=0 AND t.SystemKey=N'expert' AND t.ShaleClientId IS NULL
UNION ALL SELECT 'expert assignments for deleted contacts',COUNT(*) FROM dbo.ContactContactTypes a JOIN dbo.Contacts c ON c.Id=a.ContactId AND c.ShaleClientId=a.ShaleClientId JOIN dbo.ContactTypes t ON t.Id=a.ContactTypeId WHERE c.IsExpert=1 AND c.IsDeleted=1 AND a.IsDeleted=0 AND t.SystemKey=N'expert' AND t.ShaleClientId IS NULL;

SELECT 'duplicate active contact types (expect 0)' CheckName,COUNT(*) FindingCount FROM(SELECT 1 x FROM dbo.ContactContactTypes WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,ContactTypeId HAVING COUNT(*)>1)d
UNION ALL SELECT 'duplicate active specialties',COUNT(*) FROM(SELECT 1 x FROM dbo.ContactSpecialties WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,SpecialtyId HAVING COUNT(*)>1)d
UNION ALL SELECT 'duplicate active credentials',COUNT(*) FROM(SELECT 1 x FROM dbo.ContactCredentials WHERE IsDeleted=0 GROUP BY ShaleClientId,ContactId,CredentialDefinitionId HAVING COUNT(*)>1)d;

SELECT 'cross-tenant contact assignment (expect 0)' CheckName,COUNT(*) FindingCount FROM(
 SELECT a.ShaleClientId FROM dbo.ContactContactTypes a JOIN dbo.Contacts c ON c.Id=a.ContactId WHERE c.ShaleClientId<>a.ShaleClientId
 UNION ALL SELECT a.ShaleClientId FROM dbo.ContactSpecialties a JOIN dbo.Contacts c ON c.Id=a.ContactId WHERE c.ShaleClientId<>a.ShaleClientId
 UNION ALL SELECT a.ShaleClientId FROM dbo.ContactCredentials a JOIN dbo.Contacts c ON c.Id=a.ContactId WHERE c.ShaleClientId<>a.ShaleClientId)x
UNION ALL SELECT 'cross-tenant definition assignment (expect 0)',COUNT(*) FROM(
 SELECT a.Id FROM dbo.ContactContactTypes a JOIN dbo.ContactTypes d ON d.Id=a.ContactTypeId WHERE d.ShaleClientId IS NOT NULL AND d.ShaleClientId<>a.ShaleClientId
 UNION ALL SELECT a.Id FROM dbo.ContactSpecialties a JOIN dbo.Specialties d ON d.Id=a.SpecialtyId WHERE d.ShaleClientId IS NOT NULL AND d.ShaleClientId<>a.ShaleClientId
 UNION ALL SELECT a.Id FROM dbo.ContactCredentials a JOIN dbo.CredentialDefinitions d ON d.Id=a.CredentialDefinitionId WHERE d.ShaleClientId IS NOT NULL AND d.ShaleClientId<>a.ShaleClientId)x
UNION ALL SELECT 'invalid assignment deletion metadata (expect 0)',COUNT(*) FROM(
 SELECT Id FROM dbo.ContactContactTypes WHERE (IsDeleted=0 AND (DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) OR (IsDeleted=1 AND DeletedAt IS NULL)
 UNION ALL SELECT Id FROM dbo.ContactSpecialties WHERE (IsDeleted=0 AND (DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) OR (IsDeleted=1 AND DeletedAt IS NULL)
 UNION ALL SELECT Id FROM dbo.ContactCredentials WHERE (IsDeleted=0 AND (DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) OR (IsDeleted=1 AND DeletedAt IS NULL))x;

SELECT OBJECT_NAME(sp.target_object_id) TableName,sp.predicate_type_desc,OBJECT_SCHEMA_NAME(sp.predicate_object_id)+N'.'+OBJECT_NAME(sp.predicate_object_id) PredicateFunction,pol.name PolicyName,pol.is_enabled
FROM sys.security_predicates sp JOIN sys.security_policies pol ON pol.object_id=sp.object_id
WHERE sp.target_object_id IN(OBJECT_ID(N'dbo.ContactTypes'),OBJECT_ID(N'dbo.Specialties'),OBJECT_ID(N'dbo.CredentialDefinitions'),OBJECT_ID(N'dbo.ContactContactTypes'),OBJECT_ID(N'dbo.ContactSpecialties'),OBJECT_ID(N'dbo.ContactCredentials')) ORDER BY 1;

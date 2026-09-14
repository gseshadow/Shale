/* Read-only post-deployment verification. Outputs identifiers/counts only; no definition names or PHI. */
SET NOCOUNT ON;
SELECT t.name TableName,c.name ColumnName,ty.name SqlType,c.max_length,c.is_nullable
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE s.name=N'dbo' AND t.name IN(N'ContactTypes',N'Specialties',N'CredentialDefinitions') AND c.name=N'Color' ORDER BY t.name;
SELECT N'ContactTypes' DefinitionTable,COUNT_BIG(*) InvalidOrMissingColor FROM dbo.ContactTypes WHERE Color IS NULL OR LEN(Color)<>7 OR LEFT(Color,1)<>N'#' OR SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9A-F]%' OR Color<>UPPER(Color)
UNION ALL SELECT N'Specialties',COUNT_BIG(*) FROM dbo.Specialties WHERE Color IS NULL OR LEN(Color)<>7 OR LEFT(Color,1)<>N'#' OR SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9A-F]%' OR Color<>UPPER(Color)
UNION ALL SELECT N'CredentialDefinitions',COUNT_BIG(*) FROM dbo.CredentialDefinitions WHERE Color IS NULL OR LEN(Color)<>7 OR LEFT(Color,1)<>N'#' OR SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9A-F]%' OR Color<>UPPER(Color);
SELECT OBJECT_NAME(parent_object_id) TableName,name ConstraintName,N'CHECK' ConstraintType,is_disabled,is_not_trusted,definition FROM sys.check_constraints WHERE name IN(N'CK_ContactTypes_Color',N'CK_Specialties_Color',N'CK_CredentialDefinitions_Color')
UNION ALL SELECT OBJECT_NAME(parent_object_id),name,N'DEFAULT',0,0,definition FROM sys.default_constraints WHERE name IN(N'DF_ContactTypes_Color',N'DF_Specialties_Color',N'DF_CredentialDefinitions_Color');
SELECT N'ContactTypes' DefinitionTable,CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END LifecycleState,COUNT_BIG(*) DefinitionCount FROM dbo.ContactTypes GROUP BY CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END
UNION ALL SELECT N'Specialties',CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END,COUNT_BIG(*) FROM dbo.Specialties GROUP BY CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END
UNION ALL SELECT N'CredentialDefinitions',CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END,COUNT_BIG(*) FROM dbo.CredentialDefinitions GROUP BY CASE WHEN IsDeleted=1 THEN N'REMOVED' WHEN IsActive=1 THEN N'ACTIVE' ELSE N'INACTIVE' END;
SELECT Id AuthoritativeDefinitionId,Color FROM dbo.ContactTypes WHERE ShaleClientId IS NULL AND SystemKey=N'expert';
SELECT N'ContactContactTypes' AssignmentTable,COUNT_BIG(*) AssignmentCount,COUNT_BIG(DISTINCT ContactTypeId) ReferencedDefinitionCount FROM dbo.ContactContactTypes
UNION ALL SELECT N'ContactSpecialties',COUNT_BIG(*),COUNT_BIG(DISTINCT SpecialtyId) FROM dbo.ContactSpecialties
UNION ALL SELECT N'ContactCredentials',COUNT_BIG(*),COUNT_BIG(DISTINCT CredentialDefinitionId) FROM dbo.ContactCredentials;
SELECT
    sp.name AS PolicyName,
    sp.is_enabled,
    OBJECT_SCHEMA_NAME(spr.target_object_id) + N'.' +
        OBJECT_NAME(spr.target_object_id) AS TargetTable,
    spr.predicate_type_desc,
    spr.predicate_definition
FROM sys.security_policies sp
JOIN sys.security_predicates spr
    ON spr.object_id = sp.object_id
WHERE spr.target_object_id IN
(
    OBJECT_ID(N'dbo.ContactTypes'),
    OBJECT_ID(N'dbo.Specialties'),
    OBJECT_ID(N'dbo.CredentialDefinitions')
)
ORDER BY TargetTable, spr.predicate_type_desc;

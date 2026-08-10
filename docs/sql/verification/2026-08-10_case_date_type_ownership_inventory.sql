/* Read-only production inventory. Run administratively with NULL tenant session context. */
SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 56900,'Missing dbo.CaseDateTypes.',1;
IF OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL THROW 56901,'Missing dbo.CaseDates.',1;
IF OBJECT_ID(N'dbo.FormConfiguredFields',N'U') IS NULL THROW 56902,'Missing dbo.FormConfiguredFields.',1;
DECLARE @Noncritical TABLE(SystemKey nvarchar(64) PRIMARY KEY,ExpectedName nvarchar(100));
INSERT @Noncritical VALUES
(N'trial',N'Trial'),(N'hearing',N'Hearing'),(N'mediation',N'Mediation'),(N'deposition',N'Deposition'),
(N'discovery_deadline',N'Discovery Deadline'),(N'date_of_injury',N'Date of Injury'),
(N'date_of_medical_negligence',N'Date of Medical Negligence'),
(N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered'),
(N'fee_agreement_signed',N'Fee Agreement Signed'),(N'non_engagement_letter_sent',N'Non-Engagement Letter Sent');

SELECT N'01_DEFINITIONS' SectionName,t.Id CaseDateTypeId,t.ShaleClientId TypeOwnerTenantId,t.SystemKey,t.Name,t.IsActive,t.IsDeleted,t.CreatedByUserId
FROM dbo.CaseDateTypes t JOIN @Noncritical n ON n.SystemKey=t.SystemKey ORDER BY t.SystemKey,t.ShaleClientId,t.Id;
SELECT N'02_OCCURRENCE_USAGE' SectionName,t.Id CaseDateTypeId,t.SystemKey,cd.ShaleClientId,COUNT_BIG(cd.Id) OccurrenceCount,
 SUM(CASE WHEN cd.IsDeleted=0 THEN 1 ELSE 0 END) ActiveOccurrenceCount,COUNT_BIG(DISTINCT cd.CaseId) CaseCount
FROM dbo.CaseDateTypes t JOIN @Noncritical n ON n.SystemKey=t.SystemKey LEFT JOIN dbo.CaseDates cd ON cd.CaseDateTypeId=t.Id
GROUP BY t.Id,t.SystemKey,cd.ShaleClientId ORDER BY t.SystemKey,t.Id,cd.ShaleClientId;
SELECT N'03_FORM_REFERENCES' SectionName,t.Id CaseDateTypeId,t.SystemKey,f.ShaleClientId,COUNT_BIG(f.Id) ConfiguredFieldCount,
 COUNT_BIG(DISTINCT f.FormConfigurationId) FormConfigurationCount
FROM dbo.CaseDateTypes t JOIN @Noncritical n ON n.SystemKey=t.SystemKey LEFT JOIN dbo.FormConfiguredFields f ON f.CaseDateTypeId=t.Id
GROUP BY t.Id,t.SystemKey,f.ShaleClientId ORDER BY t.SystemKey,t.Id,f.ShaleClientId;
SELECT N'04_REFERENCING_FOREIGN_KEYS' SectionName,QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id))+N'.'+QUOTENAME(OBJECT_NAME(fk.parent_object_id)) ReferencingTable,
 COL_NAME(fkc.parent_object_id,fkc.parent_column_id) ReferencingColumn,fk.name ForeignKeyName
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
WHERE fk.referenced_object_id=OBJECT_ID(N'dbo.CaseDateTypes') ORDER BY ReferencingTable,ReferencingColumn;
SELECT N'05_SEMANTIC_ROLE_MAPPINGS' SectionName,m.ShaleClientId,m.SemanticRoleKey,m.CaseDateTypeId,t.SystemKey,m.IsActive,m.IsDeleted
FROM dbo.CaseDateTypeSemanticRoleMappings m JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId ORDER BY m.SemanticRoleKey,m.ShaleClientId,m.Id;
SELECT N'06_CROSS_TENANT_BLOCKERS' SectionName,cd.ShaleClientId CaseDateTenantId,t.ShaleClientId TypeOwnerTenantId,COUNT_BIG(*) RowCount
FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
WHERE t.ShaleClientId IS NOT NULL AND (cd.ShaleClientId IS NULL OR t.ShaleClientId<>cd.ShaleClientId) GROUP BY cd.ShaleClientId,t.ShaleClientId;

SELECT N'07_PROTECTED_GLOBALS' SectionName,t.Id,t.SystemKey,t.Name,t.ShaleClientId
FROM dbo.CaseDateTypes t JOIN dbo.CaseDateTypeSemanticRoleMappings m ON m.CaseDateTypeId=t.Id
WHERE m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0 ORDER BY m.SemanticRoleKey;
SELECT N'08_TENANT7_REFERENCE_TOTALS' SectionName,
 (SELECT COUNT_BIG(*) FROM dbo.CaseDates cd JOIN @Noncritical n ON EXISTS(SELECT 1 FROM dbo.CaseDateTypes t WHERE t.Id=cd.CaseDateTypeId AND t.SystemKey=n.SystemKey) WHERE cd.ShaleClientId=7) OccurrenceCount,
 (SELECT COUNT_BIG(*) FROM dbo.FormConfiguredFields f JOIN @Noncritical n ON EXISTS(SELECT 1 FROM dbo.CaseDateTypes t WHERE t.Id=f.CaseDateTypeId AND t.SystemKey=n.SystemKey) WHERE f.ShaleClientId=7) FormReferenceCount;
SELECT N'09_TENANT8_CUSTOM_TYPES' SectionName,Id,SystemKey,Name FROM dbo.CaseDateTypes WHERE ShaleClientId=8;
SELECT N'10_FORM_CROSS_TENANT_BLOCKERS' SectionName,f.ShaleClientId,t.ShaleClientId TypeOwnerTenantId,COUNT_BIG(*) RowCount
FROM dbo.FormConfiguredFields f JOIN dbo.CaseDateTypes t ON t.Id=f.CaseDateTypeId
WHERE t.ShaleClientId IS NOT NULL AND (f.ShaleClientId IS NULL OR t.ShaleClientId<>f.ShaleClientId) GROUP BY f.ShaleClientId,t.ShaleClientId;

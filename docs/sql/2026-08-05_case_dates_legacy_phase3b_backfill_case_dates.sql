/* Transactional idempotent backfill from legacy dbo.Cases dates into dbo.CaseDates. Does not touch CalendarEvents or clear/drop legacy columns. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56200, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56201, 'Missing dbo.CaseDates.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56202, 'Missing dbo.CaseDateTypes.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'IsDeleted') IS NULL THROW 56203, 'Expected dbo.CaseDates.IsDeleted removal column is missing.', 1;
DECLARE @MigrationActors TABLE(ShaleClientId int NOT NULL PRIMARY KEY, CreatedByUserId int NOT NULL);
-- REQUIRED BEFORE EXECUTION: add one explicit, same-tenant migration/system actor per participating tenant, for example:
-- INSERT @MigrationActors(ShaleClientId, CreatedByUserId) VALUES (1, 101), (2, 202);
DECLARE @ExpectedTypes TABLE(SystemKey nvarchar(64) NOT NULL PRIMARY KEY, CalendarCategory varchar(32) NOT NULL, SupportsTime bit NOT NULL);
INSERT @ExpectedTypes VALUES
(N'intake','OTHER',1),(N'date_of_injury','OTHER',0),(N'date_of_medical_negligence','OTHER',0),(N'medical_negligence_discovered','OTHER',0),
(N'statute_of_limitations','DEADLINE',0),(N'tort_notice_deadline','DEADLINE',0),(N'discovery_deadline','DEADLINE',0),
(N'fee_agreement_signed','MILESTONE',0),(N'non_engagement_letter_sent','MILESTONE',0);

WITH TypeCandidates AS (
    SELECT t.Id, t.ShaleClientId, t.SystemKey, t.Name, t.CalendarCategory, t.Color, t.SupportsTime, t.IsActive, t.IsDeleted,
           tenantScope.ShaleClientId AS EffectiveTenantId,
           ROW_NUMBER() OVER (PARTITION BY tenantScope.ShaleClientId, t.SystemKey ORDER BY CASE WHEN t.ShaleClientId = tenantScope.ShaleClientId THEN 0 ELSE 1 END, t.Id) AS rn,
           COUNT(*) OVER (PARTITION BY tenantScope.ShaleClientId, t.SystemKey, CASE WHEN t.ShaleClientId = tenantScope.ShaleClientId THEN 0 ELSE 1 END) AS SamePrecedenceCount
    FROM (SELECT DISTINCT ShaleClientId FROM dbo.Cases WHERE ShaleClientId IS NOT NULL) tenantScope
    JOIN dbo.CaseDateTypes t ON (t.ShaleClientId = tenantScope.ShaleClientId OR t.ShaleClientId IS NULL)
    JOIN @ExpectedTypes e ON e.SystemKey = t.SystemKey
)
SELECT * INTO #EffectiveCaseDateTypes FROM TypeCandidates WHERE rn = 1;
CREATE UNIQUE CLUSTERED INDEX IX_EffectiveCaseDateTypes_TenantKey ON #EffectiveCaseDateTypes(EffectiveTenantId, SystemKey);

IF EXISTS (SELECT 1 FROM #EffectiveCaseDateTypes et JOIN @ExpectedTypes e ON e.SystemKey=et.SystemKey WHERE et.CalendarCategory<>e.CalendarCategory OR et.SupportsTime<>e.SupportsTime OR ISNULL(et.IsActive,0)<>1 OR ISNULL(et.IsDeleted,0)<>0 OR et.SamePrecedenceCount>1) THROW 56204, 'Ambiguous, inactive, deleted, or conflicting effective CaseDateType definition found.', 1;

SELECT c.ShaleClientId, c.Id AS CaseId, v.FieldName, v.SystemKey,
       CASE WHEN v.FieldName = 'CallerDate' AND c.CallerTime IS NOT NULL THEN
            DATETIME2FROMPARTS(DATEPART(year, c.CallerDate), DATEPART(month, c.CallerDate), DATEPART(day, c.CallerDate),
                               DATEPART(hour, CAST(c.CallerTime AS time)), DATEPART(minute, CAST(c.CallerTime AS time)), DATEPART(second, CAST(c.CallerTime AS time)), DATEPART(nanosecond, CAST(c.CallerTime AS time)) / 100, 7)
            ELSE CAST(v.LegacyDate AS datetime2(7)) END AS ExpectedStartsAt,
       CAST(CASE WHEN v.FieldName = 'CallerDate' AND c.CallerTime IS NOT NULL THEN 0 ELSE 1 END AS bit) AS ExpectedAllDay
INTO #LegacyCaseDateSources
FROM dbo.Cases c
CROSS APPLY (VALUES
    ('CallerDate','intake',c.CallerDate),
    ('DateOfMedicalNegligence','date_of_medical_negligence',c.DateOfMedicalNegligence),
    ('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered',c.DateMedicalNegligenceWasDiscovered),
    ('DateOfInjury','date_of_injury',c.DateOfInjury),
    ('StatuteOfLimitations','statute_of_limitations',c.StatuteOfLimitations),
    ('TortNoticeDeadline','tort_notice_deadline',c.TortNoticeDeadline),
    ('DiscoveryDeadline','discovery_deadline',c.DiscoveryDeadline),
    ('DateFeeAgreementSigned','fee_agreement_signed',c.DateFeeAgreementSigned),
    ('DateNonEngagementLetterSent','non_engagement_letter_sent',c.DateNonEngagementLetterSent)
) v(FieldName,SystemKey,LegacyDate)
WHERE v.LegacyDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0;
CREATE CLUSTERED INDEX IX_LegacyCaseDateSources_TenantCaseKey ON #LegacyCaseDateSources(ShaleClientId, CaseId, SystemKey);

IF EXISTS (SELECT 1 FROM dbo.Cases WHERE CallerTime IS NOT NULL AND CallerDate IS NULL) THROW 56205, 'Unresolved orphan CallerTime rows exist; run preflight and resolve before backfill.', 1;
SELECT l.*, et.Id AS EffectiveCaseDateTypeId, ma.CreatedByUserId,
       SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) AS ActiveExactMatches,
       SUM(CASE WHEN cd.Id IS NOT NULL AND NOT (cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) AS ActiveSameTypeDifferentValue,
       SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END) AS RemovedExactMatches
INTO #ResolvedBackfill
FROM #LegacyCaseDateSources l
JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey
LEFT JOIN @MigrationActors ma ON ma.ShaleClientId=l.ShaleClientId
LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=et.Id
GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey,l.ExpectedStartsAt,l.ExpectedAllDay,et.Id,ma.CreatedByUserId;
CREATE UNIQUE CLUSTERED INDEX IX_ResolvedBackfill_Row ON #ResolvedBackfill(ShaleClientId, CaseId, SystemKey);
IF EXISTS (SELECT 1 FROM #ResolvedBackfill WHERE CreatedByUserId IS NULL) THROW 56206, 'Every participating tenant requires an explicit same-tenant migration actor mapping.', 1;
IF EXISTS (SELECT 1 FROM #ResolvedBackfill r WHERE NOT EXISTS (SELECT 1 FROM dbo.Users u WHERE u.Id=r.CreatedByUserId AND u.ShaleClientId=r.ShaleClientId AND ISNULL(u.is_deleted,0)=0)) THROW 56207, 'Migration actor mapping must reference an active user in the same tenant.', 1;
IF EXISTS (SELECT 1 FROM #ResolvedBackfill WHERE ActiveExactMatches>1 OR ActiveSameTypeDifferentValue>0 OR RemovedExactMatches>0) THROW 56208, 'Existing CaseDates conflicts found; run preflight and resolve before backfill.', 1;
INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Notes,CreatedByUserId)
SELECT ShaleClientId,CaseId,EffectiveCaseDateTypeId,ExpectedStartsAt,NULL,ExpectedAllDay,NULL,CreatedByUserId FROM #ResolvedBackfill WHERE ActiveExactMatches=0;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;

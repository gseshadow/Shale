/* Read-only preflight/profile for legacy dbo.Cases date migration to dbo.CaseDates. */
SET NOCOUNT ON;
DECLARE @TenantId int = NULL;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56000, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56001, 'Missing dbo.CaseDates.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56002, 'Missing dbo.CaseDateTypes.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'IsDeleted') IS NULL THROW 56003, 'Expected dbo.CaseDates.IsDeleted removal column is missing.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'IsRemoved') IS NOT NULL PRINT 'WARNING: dbo.CaseDates.IsRemoved also exists; repository Phase 1A/runtime contract uses IsDeleted.';

DECLARE @Legacy TABLE(FieldName sysname, SystemKey nvarchar(64), Destination nvarchar(64));
INSERT @Legacy VALUES ('CallerDate','intake','CaseDates'),('CallerTime','intake','CaseDates companion'),('DateOfMedicalNegligence','date_of_medical_negligence','CaseDates'),('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered','CaseDates'),('DateOfInjury','date_of_injury','CaseDates'),('StatuteOfLimitations','statute_of_limitations','CaseDates'),('TortNoticeDeadline','tort_notice_deadline','CaseDates'),('DiscoveryDeadline','discovery_deadline','CaseDates'),('DateFeeAgreementSigned','fee_agreement_signed','CaseDates'),('DateNonEngagementLetterSent','non_engagement_letter_sent','CaseDates'),('AcceptedDate',NULL,'StatusHistoryBlocker'),('DeniedDate',NULL,'StatusHistoryBlocker'),('ClosedDate',NULL,'StatusHistoryBlocker');
SELECT 'Legacy field coverage' SectionName,* FROM @Legacy ORDER BY FieldName;
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

SELECT 'Effective destination type issues' SectionName, e.SystemKey, t.EffectiveTenantId AS ShaleClientId, t.Id, t.ShaleClientId AS TypeOwnerTenantId, t.Name, t.CalendarCategory, t.SupportsTime, t.IsActive, t.IsDeleted, t.SamePrecedenceCount
FROM @ExpectedTypes e LEFT JOIN #EffectiveCaseDateTypes t ON t.SystemKey=e.SystemKey
WHERE t.Id IS NULL OR t.CalendarCategory<>e.CalendarCategory OR t.SupportsTime<>e.SupportsTime OR ISNULL(t.IsActive,0)<>1 OR ISNULL(t.IsDeleted,0)<>0 OR t.SamePrecedenceCount>1;
SELECT 'Legacy value profile' SectionName, FieldName, ShaleClientId, COUNT_BIG(*) NonNullCount, MIN(ExpectedStartsAt) MinStartsAt, MAX(ExpectedStartsAt) MaxStartsAt, SUM(CASE WHEN ExpectedAllDay=0 THEN 1 ELSE 0 END) TimedCount, SUM(CASE WHEN ExpectedAllDay=1 THEN 1 ELSE 0 END) AllDayCount FROM #LegacyCaseDateSources WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) GROUP BY FieldName,ShaleClientId;
SELECT 'Orphan CallerTime' SectionName, ShaleClientId, COUNT_BIG(*) BlockerCount FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND CallerTime IS NOT NULL AND CallerDate IS NULL GROUP BY ShaleClientId;
SELECT 'Workflow flag/date mismatch' SectionName, ShaleClientId, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=1 AND DateFeeAgreementSigned IS NULL THEN 1 ELSE 0 END) FeeFlagWithoutDate, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=0 AND DateFeeAgreementSigned IS NOT NULL THEN 1 ELSE 0 END) FeeDateWithoutFlag, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=1 AND DateNonEngagementLetterSent IS NULL THEN 1 ELSE 0 END) NonEngagementFlagWithoutDate, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=0 AND DateNonEngagementLetterSent IS NOT NULL THEN 1 ELSE 0 END) NonEngagementDateWithoutFlag FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) GROUP BY ShaleClientId;
SELECT 'Existing occurrence conflicts' SectionName, l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey, SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveExactMatches, SUM(CASE WHEN cd.Id IS NOT NULL AND NOT (cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveSameTypeDifferentValue, SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END) RemovedExactMatches FROM #LegacyCaseDateSources l JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=et.Id WHERE (@TenantId IS NULL OR l.ShaleClientId=@TenantId) GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey HAVING SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END)<>1 OR SUM(CASE WHEN cd.Id IS NOT NULL AND NOT (cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END)>0 OR SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END)>0;
IF OBJECT_ID(N'dbo.CaseStatuses', N'U') IS NOT NULL BEGIN SELECT 'Status date/history evidence' SectionName, v.FieldName, c.ShaleClientId, COUNT_BIG(*) LegacyValues, SUM(CASE WHEN h.MatchCount=0 THEN 1 ELSE 0 END) WithoutMatchingStatusHistoryEvidence, SUM(CASE WHEN h.MatchCount>1 THEN 1 ELSE 0 END) RepeatedRelevantStatusTransitions FROM dbo.Cases c CROSS APPLY (VALUES ('AcceptedDate','accepted',c.AcceptedDate),('DeniedDate','denied',c.DeniedDate),('ClosedDate','closed',c.ClosedDate)) v(FieldName,LifecycleKey,LegacyDate) CROSS APPLY (SELECT COUNT_BIG(*) MatchCount FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL) WHERE cs.CaseId=c.Id AND CAST(cs.EffectiveDate AS date)=v.LegacyDate AND (s.LifecycleKey=v.LifecycleKey OR s.SystemKey=v.LifecycleKey)) h WHERE v.LegacyDate IS NOT NULL AND (@TenantId IS NULL OR c.ShaleClientId=@TenantId) GROUP BY v.FieldName,c.ShaleClientId; END;

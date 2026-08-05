/*
  Phase 3C read-only preflight/profile for legacy dbo.Cases date migration.

  This script is intentionally read-only against application tables. It only
  creates session-scoped temporary analysis tables and reads visible data. It
  does not seed types, backfill CaseDates, write audit rows, execute dynamic SQL,
  or expose PHI such as names, notes, descriptions, or narrative fields.
*/
SET NOCOUNT ON;
DECLARE @TenantId int = NULL; -- Optional: limit to one tenant when desired/permitted.

IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56000, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56001, 'Missing dbo.CaseDates.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56002, 'Missing dbo.CaseDateTypes.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'IsDeleted') IS NULL THROW 56003, 'Expected dbo.CaseDates.IsDeleted removal column is missing.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'IsRemoved') IS NOT NULL PRINT 'WARNING: dbo.CaseDates.IsRemoved also exists; repository Phase 1A/runtime contract uses IsDeleted.';

SELECT FieldName, SystemKey, Destination
INTO #LegacyFields
FROM (VALUES
    ('CallerDate','intake','CaseDates'),
    ('CallerTime','intake','CaseDates companion'),
    ('DateOfMedicalNegligence','date_of_medical_negligence','CaseDates'),
    ('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered','CaseDates'),
    ('DateOfInjury','date_of_injury','CaseDates'),
    ('StatuteOfLimitations','statute_of_limitations','CaseDates'),
    ('TortNoticeDeadline','tort_notice_deadline','CaseDates'),
    ('DiscoveryDeadline','discovery_deadline','CaseDates'),
    ('DateFeeAgreementSigned','fee_agreement_signed','CaseDates'),
    ('DateNonEngagementLetterSent','non_engagement_letter_sent','CaseDates'),
    ('AcceptedDate',NULL,'StatusHistoryBlocker'),
    ('DeniedDate',NULL,'StatusHistoryBlocker'),
    ('ClosedDate',NULL,'StatusHistoryBlocker')
) v(FieldName, SystemKey, Destination);

SELECT SystemKey, CalendarCategory, SupportsTime
INTO #ExpectedTypes
FROM (VALUES
    (N'intake','OTHER',CAST(1 AS bit)),
    (N'date_of_injury','OTHER',CAST(0 AS bit)),
    (N'date_of_medical_negligence','OTHER',CAST(0 AS bit)),
    (N'medical_negligence_discovered','OTHER',CAST(0 AS bit)),
    (N'statute_of_limitations','DEADLINE',CAST(0 AS bit)),
    (N'tort_notice_deadline','DEADLINE',CAST(0 AS bit)),
    (N'discovery_deadline','DEADLINE',CAST(0 AS bit)),
    (N'fee_agreement_signed','MILESTONE',CAST(0 AS bit)),
    (N'non_engagement_letter_sent','MILESTONE',CAST(0 AS bit))
) v(SystemKey, CalendarCategory, SupportsTime);

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
WHERE v.LegacyDate IS NOT NULL
  AND ISNULL(c.IsDeleted,0)=0
  AND (@TenantId IS NULL OR c.ShaleClientId=@TenantId);
CREATE CLUSTERED INDEX IX_LegacyCaseDateSources_TenantCaseKey ON #LegacyCaseDateSources(ShaleClientId, CaseId, SystemKey);

SELECT DISTINCT ShaleClientId
INTO #ParticipatingTenants
FROM #LegacyCaseDateSources;
CREATE UNIQUE CLUSTERED INDEX IX_ParticipatingTenants ON #ParticipatingTenants(ShaleClientId);

SELECT pt.ShaleClientId AS EffectiveTenantId, et.SystemKey, et.CalendarCategory AS ExpectedCalendarCategory, et.SupportsTime AS ExpectedSupportsTime
INTO #TenantExpectedTypes
FROM #ParticipatingTenants pt
CROSS JOIN #ExpectedTypes et;
CREATE UNIQUE CLUSTERED INDEX IX_TenantExpectedTypes ON #TenantExpectedTypes(EffectiveTenantId, SystemKey);

WITH TypeCandidates AS (
    SELECT tet.EffectiveTenantId, tet.SystemKey, tet.ExpectedCalendarCategory, tet.ExpectedSupportsTime,
           t.Id, t.ShaleClientId AS TypeOwnerTenantId, t.Name, t.CalendarCategory, t.Color, t.SupportsTime, t.IsActive, t.IsDeleted,
           CASE WHEN t.ShaleClientId = tet.EffectiveTenantId THEN 'TENANT_OVERRIDE' WHEN t.ShaleClientId IS NULL THEN 'GLOBAL' ELSE 'UNEXPECTED' END AS ResolutionSource,
           ROW_NUMBER() OVER (PARTITION BY tet.EffectiveTenantId, tet.SystemKey ORDER BY CASE WHEN t.ShaleClientId = tet.EffectiveTenantId THEN 0 ELSE 1 END, t.Id) AS rn,
           COUNT(*) OVER (PARTITION BY tet.EffectiveTenantId, tet.SystemKey, CASE WHEN t.ShaleClientId = tet.EffectiveTenantId THEN 0 ELSE 1 END) AS SamePrecedenceCount
    FROM #TenantExpectedTypes tet
    LEFT JOIN dbo.CaseDateTypes t
      ON t.SystemKey = tet.SystemKey
     AND (t.ShaleClientId = tet.EffectiveTenantId OR t.ShaleClientId IS NULL)
)
SELECT *
INTO #EffectiveCaseDateTypes
FROM TypeCandidates
WHERE rn = 1 OR Id IS NULL;
CREATE UNIQUE CLUSTERED INDEX IX_EffectiveCaseDateTypes_TenantKey ON #EffectiveCaseDateTypes(EffectiveTenantId, SystemKey);

SELECT l.ShaleClientId, l.CaseId, l.FieldName, l.SystemKey, l.ExpectedStartsAt, l.ExpectedAllDay,
       et.Id AS EffectiveCaseDateTypeId, et.TypeOwnerTenantId, et.ResolutionSource,
       SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) AS ActiveExactMatches,
       SUM(CASE WHEN cd.Id IS NOT NULL AND NOT (cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) AS ActiveSameTypeDifferentValue,
       SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END) AS RemovedExactMatches
INTO #OccurrenceEvidence
FROM #LegacyCaseDateSources l
LEFT JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey
LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=et.Id
GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey,l.ExpectedStartsAt,l.ExpectedAllDay,et.Id,et.TypeOwnerTenantId,et.ResolutionSource;
CREATE CLUSTERED INDEX IX_OccurrenceEvidence_TenantField ON #OccurrenceEvidence(ShaleClientId, FieldName, SystemKey);

SELECT '01_LEGACY_FIELD_COVERAGE' AS SectionName, * FROM #LegacyFields ORDER BY FieldName;
SELECT '02_PARTICIPATING_TENANTS' AS SectionName, ShaleClientId, COUNT_BIG(*) AS EligibleLegacyCaseDateRows FROM #LegacyCaseDateSources GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT '03_ELIGIBLE_COUNTS_BY_FIELD' AS SectionName, FieldName, ShaleClientId, COUNT_BIG(*) AS EligibleRows, MIN(ExpectedStartsAt) AS MinExpectedStartsAt, MAX(ExpectedStartsAt) AS MaxExpectedStartsAt FROM #LegacyCaseDateSources GROUP BY FieldName, ShaleClientId ORDER BY FieldName, ShaleClientId;
SELECT '04_EFFECTIVE_CASE_DATE_TYPE_RESOLUTION' AS SectionName, EffectiveTenantId AS ShaleClientId, SystemKey, Id AS CaseDateTypeId, TypeOwnerTenantId, ResolutionSource, Name, CalendarCategory, Color, SupportsTime, IsActive, IsDeleted FROM #EffectiveCaseDateTypes ORDER BY ShaleClientId, SystemKey;
SELECT '05_TYPE_SEED_OR_DEFINITION_ISSUES' AS SectionName, EffectiveTenantId AS ShaleClientId, SystemKey, Id AS CaseDateTypeId, ResolutionSource, Name, CalendarCategory, ExpectedCalendarCategory, SupportsTime, ExpectedSupportsTime, IsActive, IsDeleted, SamePrecedenceCount,
       CASE WHEN Id IS NULL THEN 'EXPECTED_MISSING_SEED_CAN_CREATE'
            WHEN SamePrecedenceCount > 1 THEN 'BLOCKS_SEED_DUPLICATE_OR_AMBIGUOUS'
            WHEN ISNULL(IsDeleted,0) <> 0 THEN 'BLOCKS_SEED_DELETED_EFFECTIVE_TYPE'
            WHEN ISNULL(IsActive,0) <> 1 THEN 'BLOCKS_SEED_INACTIVE_EFFECTIVE_TYPE'
            WHEN CalendarCategory <> ExpectedCalendarCategory OR SupportsTime <> ExpectedSupportsTime THEN 'BLOCKS_SEED_CONFLICTING_DEFINITION'
            ELSE 'OK' END AS IssueClassification
FROM #EffectiveCaseDateTypes
WHERE Id IS NULL OR SamePrecedenceCount > 1 OR ISNULL(IsDeleted,0)<>0 OR ISNULL(IsActive,0)<>1 OR CalendarCategory<>ExpectedCalendarCategory OR SupportsTime<>ExpectedSupportsTime
ORDER BY ShaleClientId, SystemKey;
SELECT '06_ORPHAN_CALLER_TIME' AS SectionName, ShaleClientId, COUNT_BIG(*) AS BlockerRows FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND CallerTime IS NOT NULL AND CallerDate IS NULL GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT '07_INTAKE_TIMED_ALL_DAY_COUNTS' AS SectionName, ShaleClientId, SUM(CASE WHEN ExpectedAllDay=0 THEN 1 ELSE 0 END) AS TimedIntakeRows, SUM(CASE WHEN ExpectedAllDay=1 THEN 1 ELSE 0 END) AS AllDayIntakeRows FROM #LegacyCaseDateSources WHERE FieldName='CallerDate' GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT '08_EXISTING_CASEDATE_EVIDENCE_BY_FIELD' AS SectionName, FieldName, ShaleClientId, COUNT_BIG(*) AS EligibleRows, SUM(ActiveExactMatches) AS ActiveExactMatches, SUM(CASE WHEN ActiveExactMatches > 1 THEN 1 ELSE 0 END) AS RowsWithMultipleActiveExactMatches, SUM(CASE WHEN ActiveSameTypeDifferentValue > 0 THEN 1 ELSE 0 END) AS RowsWithActiveSameTypeDifferentValue, SUM(CASE WHEN RemovedExactMatches > 0 THEN 1 ELSE 0 END) AS RowsWithRemovedExactMatches FROM #OccurrenceEvidence GROUP BY FieldName, ShaleClientId ORDER BY FieldName, ShaleClientId;
SELECT '09_EXISTING_CASEDATE_CONFLICT_SAFE_IDS' AS SectionName, ShaleClientId, CaseId, FieldName, SystemKey, ActiveExactMatches, ActiveSameTypeDifferentValue, RemovedExactMatches FROM #OccurrenceEvidence WHERE ActiveExactMatches > 1 OR ActiveSameTypeDifferentValue > 0 OR RemovedExactMatches > 0 ORDER BY ShaleClientId, FieldName, CaseId;
SELECT '10_CROSS_TENANT_CASEDATE_OR_TYPE' AS SectionName, cd.ShaleClientId AS CaseDateTenantId, c.ShaleClientId AS CaseTenantId, t.ShaleClientId AS TypeOwnerTenantId, COUNT_BIG(*) AS Rows FROM dbo.CaseDates cd LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId LEFT JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE (c.Id IS NULL OR cd.ShaleClientId<>c.ShaleClientId OR (t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>cd.ShaleClientId)) GROUP BY cd.ShaleClientId,c.ShaleClientId,t.ShaleClientId;
SELECT '11_CASEDATE_CREATED_BY_TENANT_MISMATCH' AS SectionName, cd.ShaleClientId AS CaseDateTenantId, u.ShaleClientId AS CreatedByTenantId, COUNT_BIG(*) AS Rows FROM dbo.CaseDates cd JOIN dbo.Users u ON u.Id=cd.CreatedByUserId WHERE u.ShaleClientId<>cd.ShaleClientId GROUP BY cd.ShaleClientId,u.ShaleClientId;
SELECT '12_WORKFLOW_FLAG_DATE_MISMATCH' AS SectionName, ShaleClientId, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=1 AND DateFeeAgreementSigned IS NULL THEN 1 ELSE 0 END) FeeFlagWithoutDate, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=0 AND DateFeeAgreementSigned IS NOT NULL THEN 1 ELSE 0 END) FeeDateWithoutFlag, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=1 AND DateNonEngagementLetterSent IS NULL THEN 1 ELSE 0 END) NonEngagementFlagWithoutDate, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=0 AND DateNonEngagementLetterSent IS NOT NULL THEN 1 ELSE 0 END) NonEngagementDateWithoutFlag FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) GROUP BY ShaleClientId ORDER BY ShaleClientId;
IF OBJECT_ID(N'dbo.CaseStatuses', N'U') IS NOT NULL
BEGIN
    SELECT '13_STATUS_DATE_HISTORY_EVIDENCE' AS SectionName, v.FieldName, c.ShaleClientId, COUNT_BIG(*) AS LegacyDateRows, SUM(CASE WHEN h.MatchCount=0 THEN 1 ELSE 0 END) AS MissingSameDateEvidence, SUM(CASE WHEN h.MatchCount>1 THEN 1 ELSE 0 END) AS MultipleSameDateEvidence
    FROM dbo.Cases c
    CROSS APPLY (VALUES ('AcceptedDate','accepted',c.AcceptedDate),('DeniedDate','denied',c.DeniedDate),('ClosedDate','closed',c.ClosedDate)) v(FieldName,LifecycleKey,LegacyDate)
    CROSS APPLY (SELECT COUNT_BIG(*) MatchCount FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL) WHERE cs.CaseId=c.Id AND CAST(cs.EffectiveDate AS date)=v.LegacyDate AND (s.LifecycleKey=v.LifecycleKey OR s.SystemKey=v.LifecycleKey)) h
    WHERE v.LegacyDate IS NOT NULL AND (@TenantId IS NULL OR c.ShaleClientId=@TenantId)
    GROUP BY v.FieldName,c.ShaleClientId
    ORDER BY v.FieldName,c.ShaleClientId;
    SELECT '14_REOPENED_OR_REPEATED_RELEVANT_STATUS_TRANSITIONS' AS SectionName, c.ShaleClientId, cs.CaseId, COUNT_BIG(*) AS RelevantTransitionRows
    FROM dbo.CaseStatuses cs
    JOIN dbo.Cases c ON c.Id=cs.CaseId
    JOIN dbo.Statuses s ON s.Id=cs.StatusId AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL)
    WHERE (@TenantId IS NULL OR c.ShaleClientId=@TenantId) AND (s.LifecycleKey IN ('accepted','denied','closed') OR s.SystemKey IN ('accepted','denied','closed','reopened'))
    GROUP BY c.ShaleClientId,cs.CaseId
    HAVING COUNT_BIG(*)>1
    ORDER BY c.ShaleClientId,cs.CaseId;
END
ELSE
BEGIN
    SELECT '13_STATUS_DATE_HISTORY_EVIDENCE' AS SectionName, CAST(NULL AS sysname) AS FieldName, CAST(NULL AS int) AS ShaleClientId, CAST(0 AS bigint) AS LegacyDateRows, CAST(0 AS bigint) AS MissingSameDateEvidence, CAST(0 AS bigint) AS MultipleSameDateEvidence WHERE 1=0;
    SELECT '14_REOPENED_OR_REPEATED_RELEVANT_STATUS_TRANSITIONS' AS SectionName, CAST(NULL AS int) AS ShaleClientId, CAST(NULL AS int) AS CaseId, CAST(0 AS bigint) AS RelevantTransitionRows WHERE 1=0;
END;
SELECT '15_REQUIRED_MIGRATION_ACTOR_TENANTS' AS SectionName, ShaleClientId, 'Populate @MigrationActors for this tenant before running backfill.' AS RequiredAction FROM #ParticipatingTenants ORDER BY ShaleClientId;

DECLARE @ExpectedMissingTypeCount bigint = (SELECT COUNT_BIG(*) FROM #EffectiveCaseDateTypes WHERE Id IS NULL);
DECLARE @SeedBlockerCount bigint = (SELECT COUNT_BIG(*) FROM #EffectiveCaseDateTypes WHERE Id IS NOT NULL AND (SamePrecedenceCount>1 OR ISNULL(IsDeleted,0)<>0 OR ISNULL(IsActive,0)<>1 OR CalendarCategory<>ExpectedCalendarCategory OR SupportsTime<>ExpectedSupportsTime));
DECLARE @OrphanCallerTimeCount bigint = (SELECT COUNT_BIG(*) FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND CallerTime IS NOT NULL AND CallerDate IS NULL);
DECLARE @OccurrenceConflictCount bigint = (SELECT COUNT_BIG(*) FROM #OccurrenceEvidence WHERE ActiveExactMatches > 1 OR ActiveSameTypeDifferentValue > 0 OR RemovedExactMatches > 0);
DECLARE @CrossTenantCaseDateCount bigint = (SELECT COUNT_BIG(*) FROM dbo.CaseDates cd LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId LEFT JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE (c.Id IS NULL OR cd.ShaleClientId<>c.ShaleClientId OR (t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>cd.ShaleClientId)));
DECLARE @CreatedByTenantMismatchCount bigint = (SELECT COUNT_BIG(*) FROM dbo.CaseDates cd JOIN dbo.Users u ON u.Id=cd.CreatedByUserId WHERE u.ShaleClientId<>cd.ShaleClientId);
DECLARE @WorkflowMismatchCount bigint = (SELECT COUNT_BIG(*) FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND ((ISNULL(FeeAgreementSigned,0)=1 AND DateFeeAgreementSigned IS NULL) OR (ISNULL(FeeAgreementSigned,0)=0 AND DateFeeAgreementSigned IS NOT NULL) OR (ISNULL(NonEngagementLetterSent,0)=1 AND DateNonEngagementLetterSent IS NULL) OR (ISNULL(NonEngagementLetterSent,0)=0 AND DateNonEngagementLetterSent IS NOT NULL)));
DECLARE @StatusHistoryBlockerCount bigint = (SELECT COUNT_BIG(*) FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND (AcceptedDate IS NOT NULL OR DeniedDate IS NOT NULL OR ClosedDate IS NOT NULL));
DECLARE @BackfillBlockerCount bigint = @SeedBlockerCount + @OrphanCallerTimeCount + @OccurrenceConflictCount + @CrossTenantCaseDateCount + @CreatedByTenantMismatchCount + @WorkflowMismatchCount;
SELECT 'PREFLIGHT_VALIDATION_SUMMARY' AS SectionName,
       @ExpectedMissingTypeCount AS ExpectedMissingTypeCountSeedMayCreate,
       @SeedBlockerCount AS SeedBlockerCount,
       @OrphanCallerTimeCount AS OrphanCallerTimeBlockerCount,
       @OccurrenceConflictCount AS OccurrenceConflictBlockerCount,
       @CrossTenantCaseDateCount AS CrossTenantCaseDateBlockerCount,
       @CreatedByTenantMismatchCount AS CreatedByTenantMismatchBlockerCount,
       @WorkflowMismatchCount AS WorkflowMismatchReviewCount,
       @StatusHistoryBlockerCount AS StatusHistoryOutOfScopeRows,
       @BackfillBlockerCount AS BackfillBlockerCount,
       CASE WHEN @SeedBlockerCount=0 THEN 'READY_FOR_SEED_REVIEW' ELSE 'BLOCKED_FOR_SEED' END AS SeedReadiness,
       CASE WHEN @ExpectedMissingTypeCount=0 AND @BackfillBlockerCount=0 THEN 'READY_FOR_BACKFILL_REVIEW' ELSE 'BLOCKED_FOR_BACKFILL' END AS BackfillReadiness;

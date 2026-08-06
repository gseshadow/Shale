/*
  PHASE 3B DIAGNOSTIC BLOCKER DETAILS -- READ ONLY AND NON-AUTHORIZING.
  Application tables are read only by SELECT. Temporary analysis tables contain
  metadata/count inputs only. No case IDs, source values, dates/times, or PHI are output.
  @OperatorVerifiedAllTenantVisibility remains 0, so this report cannot authorize mutation.
*/
SET NOCOUNT ON;
DECLARE @OperatorVerifiedAllTenantVisibility bit = 0;
DECLARE @ExpectedSeedBlockerCount bigint = 0;
DECLARE @ExpectedMissingSeedableCount bigint = 3;
DECLARE @ExpectedUnresolvedSourceCount bigint = NULL; -- informational count depends on whether the three safe global seeds have run.
DECLARE @ExpectedFlagSetDateMissingCount bigint = 610;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56400, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56401, 'Missing dbo.CaseDateTypes.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56402, 'Missing dbo.CaseDates.', 1;
DECLARE @DraftAliasDefinitionCount bigint=(SELECT COUNT_BIG(*) FROM dbo.CaseDateTypes WHERE SystemKey=N'medical_negligence_discovered');
DECLARE @DraftAliasOccurrenceCount bigint=(SELECT COUNT_BIG(*) FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE t.SystemKey=N'medical_negligence_discovered');

SELECT v.SystemKey, v.ExpectedName, v.ExpectedCategory, v.ExpectedSupportsTime INTO #ExpectedTypes
FROM (VALUES
 (N'intake',N'Intake',N'OTHER',CAST(1 AS bit)),(N'date_of_injury',N'Date of Injury',N'MILESTONE',CAST(0 AS bit)),
 (N'date_of_medical_negligence',N'Date of Medical Negligence',N'MILESTONE',CAST(0 AS bit)),(N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered',N'MILESTONE',CAST(0 AS bit)),
 (N'statute_of_limitations',N'Statute of Limitations',N'DEADLINE',CAST(0 AS bit)),(N'tort_notice_deadline',N'Tort Notice Deadline',N'NOTICE',CAST(0 AS bit)),
 (N'discovery_deadline',N'Discovery Deadline',N'DEADLINE',CAST(0 AS bit)),(N'fee_agreement_signed',N'Fee Agreement Signed',N'MILESTONE',CAST(0 AS bit)),
 (N'non_engagement_letter_sent',N'Non-Engagement Letter Sent',N'MILESTONE',CAST(0 AS bit))
) v(SystemKey,ExpectedName,ExpectedCategory,ExpectedSupportsTime);

SELECT e.SystemKey,COUNT(t.Id) GlobalDefinitionCount,
 SUM(CASE WHEN t.Id IS NOT NULL AND t.Name<>e.ExpectedName THEN 1 ELSE 0 END) ExactNameConflictCount,
 SUM(CASE WHEN t.Id IS NOT NULL AND t.CalendarCategory<>e.ExpectedCategory THEN 1 ELSE 0 END) CategoryConflictCount,
 SUM(CASE WHEN t.Id IS NOT NULL AND t.SupportsTime<>e.ExpectedSupportsTime THEN 1 ELSE 0 END) SupportsTimeConflictCount,
 SUM(CASE WHEN t.Id IS NOT NULL AND ISNULL(t.IsActive,0)<>1 THEN 1 ELSE 0 END) InactiveCount,
 SUM(CASE WHEN t.Id IS NOT NULL AND ISNULL(t.IsDeleted,0)<>0 THEN 1 ELSE 0 END) DeletedCount
INTO #GlobalSeedProfile FROM #ExpectedTypes e
LEFT JOIN dbo.CaseDateTypes t ON t.ShaleClientId IS NULL AND t.SystemKey=e.SystemKey GROUP BY e.SystemKey;

SELECT p.SystemKey,r.BlockerReason,r.AffectedDefinitions INTO #SeedBlockerReasons
FROM #GlobalSeedProfile p CROSS APPLY(VALUES
 (N'DUPLICATE_GLOBAL_SYSTEM_KEY',CASE WHEN p.GlobalDefinitionCount>1 THEN p.GlobalDefinitionCount ELSE 0 END),
 (N'EXACT_NAME_CONFLICT',p.ExactNameConflictCount),(N'CATEGORY_CONFLICT',p.CategoryConflictCount),
 (N'SUPPORTS_TIME_CONFLICT',p.SupportsTimeConflictCount),(N'INACTIVE_GLOBAL_DEFINITION',p.InactiveCount),
 (N'DELETED_GLOBAL_DEFINITION',p.DeletedCount))r(BlockerReason,AffectedDefinitions)
WHERE r.AffectedDefinitions>0;

SELECT c.ShaleClientId,v.FieldName,v.SystemKey INTO #LegacySources FROM dbo.Cases c CROSS APPLY(VALUES
 (N'CallerDate',N'intake',c.CallerDate),(N'DateOfMedicalNegligence',N'date_of_medical_negligence',c.DateOfMedicalNegligence),
 (N'DateMedicalNegligenceWasDiscovered',N'date_medical_negligence_discovered',c.DateMedicalNegligenceWasDiscovered),
 (N'DateOfInjury',N'date_of_injury',c.DateOfInjury),(N'StatuteOfLimitations',N'statute_of_limitations',c.StatuteOfLimitations),
 (N'TortNoticeDeadline',N'tort_notice_deadline',c.TortNoticeDeadline),(N'DiscoveryDeadline',N'discovery_deadline',c.DiscoveryDeadline),
 (N'DateFeeAgreementSigned',N'fee_agreement_signed',c.DateFeeAgreementSigned),
 (N'DateNonEngagementLetterSent',N'non_engagement_letter_sent',c.DateNonEngagementLetterSent)
)v(FieldName,SystemKey,LegacyValue) WHERE v.LegacyValue IS NOT NULL AND ISNULL(c.IsDeleted,0)=0;
SELECT DISTINCT ShaleClientId INTO #ParticipatingTenants FROM #LegacySources;

SELECT pt.ShaleClientId,e.SystemKey,COALESCE(tw.Id,gw.Id) SelectedCaseDateTypeId,
 CASE WHEN tw.Id IS NOT NULL THEN N'TENANT_OVERRIDE' WHEN gw.Id IS NOT NULL THEN N'GLOBAL_OR_RESET_FALLBACK' ELSE N'MISSING' END ResolutionSource,
 COALESCE(tw.IsActive,gw.IsActive) SelectedIsActive,COALESCE(tw.IsDeleted,gw.IsDeleted) SelectedIsDeleted,
 ISNULL(tc.NonDeletedTenantCount,0) NonDeletedTenantCount,ISNULL(tc.DeletedTenantCount,0) DeletedTenantCount,gc.GlobalCount,
 CASE WHEN ISNULL(tc.NonDeletedTenantCount,0)>1 THEN N'DUPLICATE_ACTIVE_TENANT_DEFINITIONS'
      WHEN gc.GlobalCount=0 THEN N'MISSING_GLOBAL_DEFINITION'
      WHEN gc.GlobalCount>1 THEN N'DUPLICATE_GLOBAL_DEFINITIONS' WHEN COALESCE(tw.Id,gw.Id) IS NULL THEN N'MISSING_EFFECTIVE_DEFINITION'
      WHEN ISNULL(COALESCE(tw.IsDeleted,gw.IsDeleted),0)<>0 THEN N'SELECTED_DEFINITION_DELETED'
      WHEN ISNULL(COALESCE(tw.IsActive,gw.IsActive),0)<>1 THEN N'SELECTED_DEFINITION_INACTIVE'
      WHEN COALESCE(tw.CalendarCategory,gw.CalendarCategory)<>e.ExpectedCategory THEN N'CATEGORY_CONFLICT'
      WHEN COALESCE(tw.SupportsTime,gw.SupportsTime)<>e.ExpectedSupportsTime THEN N'SUPPORTS_TIME_CONFLICT' ELSE N'NONE' END BlockerReason
INTO #EffectiveTypes FROM #ParticipatingTenants pt CROSS JOIN #ExpectedTypes e
OUTER APPLY(SELECT SUM(CASE WHEN ISNULL(IsDeleted,0)=0 THEN 1 ELSE 0 END) NonDeletedTenantCount,SUM(CASE WHEN ISNULL(IsDeleted,0)=1 THEN 1 ELSE 0 END) DeletedTenantCount FROM dbo.CaseDateTypes WHERE ShaleClientId=pt.ShaleClientId AND SystemKey=e.SystemKey)tc
OUTER APPLY(SELECT COUNT_BIG(*) GlobalCount FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL AND SystemKey=e.SystemKey)gc
OUTER APPLY(SELECT TOP(1) Id,IsActive,IsDeleted,CalendarCategory,SupportsTime FROM dbo.CaseDateTypes WHERE ShaleClientId=pt.ShaleClientId AND SystemKey=e.SystemKey AND ISNULL(IsDeleted,0)=0 ORDER BY Id)tw
OUTER APPLY(SELECT TOP(1) Id,IsActive,IsDeleted,CalendarCategory,SupportsTime FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL AND SystemKey=e.SystemKey ORDER BY Id)gw;

SELECT c.ShaleClientId,v.FieldName,v.SystemKey,v.MismatchReason INTO #WorkflowMismatches FROM dbo.Cases c CROSS APPLY(VALUES
 (N'DateFeeAgreementSigned',N'fee_agreement_signed',CASE WHEN ISNULL(c.FeeAgreementSigned,0)=1 AND c.DateFeeAgreementSigned IS NULL THEN N'FLAG_SET_DATE_MISSING' WHEN ISNULL(c.FeeAgreementSigned,0)=0 AND c.DateFeeAgreementSigned IS NOT NULL THEN N'DATE_PRESENT_FLAG_NOT_SET' END),
 (N'DateNonEngagementLetterSent',N'non_engagement_letter_sent',CASE WHEN ISNULL(c.NonEngagementLetterSent,0)=1 AND c.DateNonEngagementLetterSent IS NULL THEN N'FLAG_SET_DATE_MISSING' WHEN ISNULL(c.NonEngagementLetterSent,0)=0 AND c.DateNonEngagementLetterSent IS NOT NULL THEN N'DATE_PRESENT_FLAG_NOT_SET' END)
)v(FieldName,SystemKey,MismatchReason) WHERE v.MismatchReason IS NOT NULL;

/*
  Return every diagnostic section through one final result set. Some SQL clients
  retain only the last result set produced by a batch, so the former six outward
  SELECTs are normalized into this temporary table first.
*/
CREATE TABLE #DiagnosticResults (
    SectionName nvarchar(80) NOT NULL,
    MetricName nvarchar(80) NULL,
    ShaleClientId int NULL,
    FieldName nvarchar(128) NULL,
    SystemKey nvarchar(128) NULL,
    DetailReason nvarchar(160) NULL,
    NumericValue bigint NULL,
    ExpectedNumericValue bigint NULL,
    SelectedCaseDateTypeId int NULL,
    ResolutionSource nvarchar(80) NULL,
    IsActive bit NULL,
    IsDeleted bit NULL,
    LifecycleState nvarchar(40) NULL,
    CompatibilityStatus nvarchar(40) NULL,
    ReconciliationStatus nvarchar(80) NULL,
    OperatorVerifiedAllTenantVisibility bit NULL,
    AuthorizationStatus nvarchar(80) NULL
);

INSERT #DiagnosticResults (SectionName,MetricName,NumericValue) VALUES
 (N'00_DRAFT_SYSTEMKEY_COLLISION',N'DraftAliasDefinitionCount',@DraftAliasDefinitionCount),
 (N'00_DRAFT_SYSTEMKEY_COLLISION',N'DraftAliasOccurrenceCount',@DraftAliasOccurrenceCount);

INSERT #DiagnosticResults
    (SectionName,MetricName,SystemKey,DetailReason,NumericValue)
SELECT N'01_SEED_BLOCKERS',N'AffectedDefinitionCount',SystemKey,BlockerReason,
       SUM(AffectedDefinitions)
FROM #SeedBlockerReasons
GROUP BY SystemKey,BlockerReason;

INSERT #DiagnosticResults
    (SectionName,MetricName,SystemKey,DetailReason)
SELECT N'02_EXPECTED_MISSING_GLOBAL_TYPES',N'ExpectedMissingType',SystemKey,
       N'EXPECTED_MISSING_SEED_MAY_CREATE'
FROM #GlobalSeedProfile
WHERE GlobalDefinitionCount=0;

INSERT #DiagnosticResults
    (SectionName,MetricName,ShaleClientId,FieldName,SystemKey,DetailReason,NumericValue)
SELECT N'03_UNRESOLVED_LEGACY_SOURCES',N'UnresolvedSourceCount',s.ShaleClientId,
       s.FieldName,s.SystemKey,e.BlockerReason,COUNT_BIG(*)
FROM #LegacySources s
LEFT JOIN #EffectiveTypes e
  ON e.ShaleClientId=s.ShaleClientId AND e.SystemKey=s.SystemKey
WHERE e.SelectedCaseDateTypeId IS NULL
   OR ISNULL(e.NonDeletedTenantCount,0)>1
   OR e.GlobalCount<>1
   OR ISNULL(e.SelectedIsActive,0)<>1
   OR ISNULL(e.SelectedIsDeleted,0)<>0
   OR e.BlockerReason IN (N'CATEGORY_CONFLICT',N'SUPPORTS_TIME_CONFLICT')
GROUP BY s.ShaleClientId,s.FieldName,s.SystemKey,e.BlockerReason;

INSERT #DiagnosticResults
    (SectionName,MetricName,ShaleClientId,FieldName,SystemKey,DetailReason,NumericValue)
SELECT N'04_WORKFLOW_FLAG_DATE_ANOMALIES',N'WorkflowAnomalyInstanceCount',ShaleClientId,
       FieldName,SystemKey,MismatchReason,COUNT_BIG(*)
FROM #WorkflowMismatches
GROUP BY ShaleClientId,FieldName,SystemKey,MismatchReason;

INSERT #DiagnosticResults
    (SectionName,MetricName,ShaleClientId,SystemKey,DetailReason,
     SelectedCaseDateTypeId,ResolutionSource,IsActive,IsDeleted,LifecycleState,
     CompatibilityStatus)
SELECT N'05_EFFECTIVE_TYPE_RESOLUTION',N'EffectiveTypeResolution',ShaleClientId,
       SystemKey,BlockerReason,SelectedCaseDateTypeId,ResolutionSource,
       SelectedIsActive,SelectedIsDeleted,
       CASE WHEN SelectedCaseDateTypeId IS NULL THEN N'MISSING'
            WHEN ISNULL(SelectedIsDeleted,0)=1 THEN N'DELETED'
            WHEN ISNULL(SelectedIsActive,0)=0 THEN N'INACTIVE'
            ELSE N'ACTIVE' END,
       CASE WHEN BlockerReason=N'NONE' THEN N'COMPATIBLE' ELSE N'BLOCKED' END
FROM #EffectiveTypes;

DECLARE @SeedBlockerCount bigint=(SELECT COUNT_BIG(*) FROM #GlobalSeedProfile WHERE GlobalDefinitionCount>1 OR ExactNameConflictCount>0 OR CategoryConflictCount>0 OR SupportsTimeConflictCount>0 OR InactiveCount>0 OR DeletedCount>0);
SET @SeedBlockerCount=@SeedBlockerCount+@DraftAliasDefinitionCount+@DraftAliasOccurrenceCount;
DECLARE @MissingSeedableCount bigint=(SELECT COUNT_BIG(*) FROM #GlobalSeedProfile WHERE GlobalDefinitionCount=0);
DECLARE @UnresolvedSourceCount bigint=(SELECT COUNT_BIG(*) FROM #LegacySources s LEFT JOIN #EffectiveTypes e ON e.ShaleClientId=s.ShaleClientId AND e.SystemKey=s.SystemKey WHERE e.SelectedCaseDateTypeId IS NULL OR ISNULL(e.NonDeletedTenantCount,0)>1 OR e.GlobalCount<>1 OR ISNULL(e.SelectedIsActive,0)<>1 OR ISNULL(e.SelectedIsDeleted,0)<>0 OR e.BlockerReason IN (N'CATEGORY_CONFLICT',N'SUPPORTS_TIME_CONFLICT'));
/* Match PREFLIGHT_VALIDATION_SUMMARY exactly: one count per case satisfying either workflow mismatch. */
DECLARE @FlagSetDateMissingCount bigint=(SELECT COUNT_BIG(*) FROM #WorkflowMismatches WHERE MismatchReason=N'FLAG_SET_DATE_MISSING');
DECLARE @DatePresentFlagNotSetCount bigint=(SELECT COUNT_BIG(*) FROM #WorkflowMismatches WHERE MismatchReason=N'DATE_PRESENT_FLAG_NOT_SET');
DECLARE @WorkflowAnomalyInstanceCount bigint=(SELECT COUNT_BIG(*) FROM #WorkflowMismatches);
DECLARE @ReconciliationStatus nvarchar(80)=
 CASE WHEN @SeedBlockerCount=@ExpectedSeedBlockerCount
        AND @MissingSeedableCount=@ExpectedMissingSeedableCount
                AND @FlagSetDateMissingCount=@ExpectedFlagSetDateMissingCount
      THEN N'MATCHES_REVIEW_INVENTORY'
      ELSE N'DOES_NOT_MATCH_REVIEW_INVENTORY' END;

INSERT #DiagnosticResults
    (SectionName,MetricName,NumericValue,ExpectedNumericValue,
     ReconciliationStatus,OperatorVerifiedAllTenantVisibility,AuthorizationStatus)
VALUES
 (N'06_BLOCKER_RECONCILIATION_SUMMARY',N'SeedBlockerCount',
  @SeedBlockerCount,@ExpectedSeedBlockerCount,@ReconciliationStatus,
  @OperatorVerifiedAllTenantVisibility,N'DIAGNOSTIC_NON_AUTHORIZING'),
 (N'06_BLOCKER_RECONCILIATION_SUMMARY',N'ExpectedMissingTypesSeedMayCreate',
  @MissingSeedableCount,@ExpectedMissingSeedableCount,@ReconciliationStatus,
  @OperatorVerifiedAllTenantVisibility,N'DIAGNOSTIC_NON_AUTHORIZING'),
 (N'06_BLOCKER_RECONCILIATION_SUMMARY',N'UnresolvedSourceCount',
  @UnresolvedSourceCount,@ExpectedUnresolvedSourceCount,@ReconciliationStatus,
  @OperatorVerifiedAllTenantVisibility,N'DIAGNOSTIC_NON_AUTHORIZING'),
 (N'06_BLOCKER_RECONCILIATION_SUMMARY',N'FlagSetDateMissingCount',
  @FlagSetDateMissingCount,@ExpectedFlagSetDateMissingCount,@ReconciliationStatus,
  @OperatorVerifiedAllTenantVisibility,N'DIAGNOSTIC_NON_AUTHORIZING'),
 (N'06_BLOCKER_RECONCILIATION_SUMMARY',N'DatePresentFlagNotSetCount',
  @DatePresentFlagNotSetCount,NULL,@ReconciliationStatus,
  @OperatorVerifiedAllTenantVisibility,N'DIAGNOSTIC_NON_AUTHORIZING');

SELECT SectionName,MetricName,ShaleClientId,FieldName,SystemKey,DetailReason,
       NumericValue,ExpectedNumericValue,SelectedCaseDateTypeId,ResolutionSource,
       IsActive,IsDeleted,LifecycleState,CompatibilityStatus,
       ReconciliationStatus,OperatorVerifiedAllTenantVisibility,AuthorizationStatus
FROM #DiagnosticResults
ORDER BY SectionName,ShaleClientId,FieldName,SystemKey,DetailReason,MetricName;

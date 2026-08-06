/* Independent all-tenant post-backfill validation. Requires administrative/RLS visibility; no @TenantId partial-success mode.
   Covered legacy fields: CallerDate, CallerTime, DateOfMedicalNegligence, DateMedicalNegligenceWasDiscovered,
   DateOfInjury, StatuteOfLimitations, TortNoticeDeadline, DiscoveryDeadline, DateFeeAgreementSigned,
   DateNonEngagementLetterSent. AcceptedDate, DeniedDate, and ClosedDate remain status-history blockers, not CaseDates. */
SET NOCOUNT ON;
DECLARE @SessionTenantId sql_variant=SESSION_CONTEXT(N'ShaleClientId');
DECLARE @IsAdministrativePrincipal bit=CONVERT(bit,CASE WHEN ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)=1 OR ISNULL(IS_MEMBER(N'db_owner'),0)=1 THEN 1 ELSE 0 END);
IF OBJECT_ID(N'dbo.Cases',N'U') IS NULL THROW 56402,'Missing dbo.Cases.',1;
IF OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL THROW 56403,'Missing dbo.CaseDates.',1;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 56404,'Missing dbo.CaseDateTypes.',1;
IF COL_LENGTH(N'dbo.CaseDates',N'IsDeleted') IS NULL THROW 56401,'Expected dbo.CaseDates.IsDeleted.',1;
DECLARE @ExpectedTypes TABLE(SystemKey nvarchar(64) NOT NULL PRIMARY KEY, Name nvarchar(100) NOT NULL, CalendarCategory varchar(32) NOT NULL, SupportsTime bit NOT NULL);
INSERT @ExpectedTypes VALUES
(N'intake',N'Intake','OTHER',1),(N'date_of_injury',N'Date of Injury','OTHER',0),(N'date_of_medical_negligence',N'Date of Medical Negligence','OTHER',0),(N'medical_negligence_discovered',N'Medical Negligence Discovered','OTHER',0),
(N'statute_of_limitations',N'Statute of Limitations','DEADLINE',0),(N'tort_notice_deadline',N'Tort Notice Deadline','DEADLINE',0),(N'discovery_deadline',N'Discovery Deadline','DEADLINE',0),
(N'fee_agreement_signed',N'Fee Agreement Signed','MILESTONE',0),(N'non_engagement_letter_sent',N'Non-Engagement Letter Sent','MILESTONE',0);
SELECT c.ShaleClientId,c.Id CaseId,v.FieldName,v.SystemKey,
 CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NOT NULL THEN DATETIME2FROMPARTS(DATEPART(year,c.CallerDate),DATEPART(month,c.CallerDate),DATEPART(day,c.CallerDate),DATEPART(hour,CAST(c.CallerTime AS time)),DATEPART(minute,CAST(c.CallerTime AS time)),DATEPART(second,CAST(c.CallerTime AS time)),DATEPART(nanosecond,CAST(c.CallerTime AS time))/100,7) ELSE CAST(v.LegacyDate AS datetime2(7)) END ExpectedStartsAt,
 CAST(CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NOT NULL THEN 0 ELSE 1 END AS bit) ExpectedAllDay
INTO #LegacyCaseDateSources FROM dbo.Cases c CROSS APPLY(VALUES
 ('CallerDate','intake',c.CallerDate),('DateOfMedicalNegligence','date_of_medical_negligence',c.DateOfMedicalNegligence),('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered',c.DateMedicalNegligenceWasDiscovered),('DateOfInjury','date_of_injury',c.DateOfInjury),('StatuteOfLimitations','statute_of_limitations',c.StatuteOfLimitations),('TortNoticeDeadline','tort_notice_deadline',c.TortNoticeDeadline),('DiscoveryDeadline','discovery_deadline',c.DiscoveryDeadline),('DateFeeAgreementSigned','fee_agreement_signed',c.DateFeeAgreementSigned),('DateNonEngagementLetterSent','non_engagement_letter_sent',c.DateNonEngagementLetterSent)
) v(FieldName,SystemKey,LegacyDate) WHERE v.LegacyDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0;
CREATE UNIQUE CLUSTERED INDEX IX_LegacySources ON #LegacyCaseDateSources(ShaleClientId,CaseId,SystemKey);
SELECT DISTINCT ShaleClientId INTO #ParticipatingTenants FROM #LegacyCaseDateSources;
SELECT pt.ShaleClientId EffectiveTenantId,e.SystemKey,e.Name ExpectedName,e.CalendarCategory ExpectedCategory,e.SupportsTime ExpectedSupportsTime,
 tc.NonDeletedTenantCount,tc.DeletedTenantCount,gc.GlobalCount,
 COALESCE(tw.Id,gw.Id) EffectiveCaseDateTypeId,COALESCE(tw.ShaleClientId,gw.ShaleClientId) TypeOwnerTenantId,COALESCE(tw.Name,gw.Name) ActualName,COALESCE(tw.CalendarCategory,gw.CalendarCategory) ActualCategory,COALESCE(tw.SupportsTime,gw.SupportsTime) ActualSupportsTime,COALESCE(tw.IsActive,gw.IsActive) IsActive,COALESCE(tw.IsDeleted,gw.IsDeleted) IsDeleted,
 CASE WHEN tw.Id IS NOT NULL THEN 'TENANT_OVERRIDE' WHEN gw.Id IS NOT NULL THEN 'GLOBAL_OR_RESET_FALLBACK' ELSE 'MISSING' END ResolutionSource
INTO #EffectiveCaseDateTypes FROM #ParticipatingTenants pt CROSS JOIN @ExpectedTypes e
OUTER APPLY(SELECT SUM(CASE WHEN ISNULL(IsDeleted,0)=0 THEN 1 ELSE 0 END) NonDeletedTenantCount,SUM(CASE WHEN ISNULL(IsDeleted,0)=1 THEN 1 ELSE 0 END) DeletedTenantCount FROM dbo.CaseDateTypes WHERE ShaleClientId=pt.ShaleClientId AND SystemKey=e.SystemKey) tc
OUTER APPLY(SELECT COUNT_BIG(*) GlobalCount FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL AND SystemKey=e.SystemKey) gc
OUTER APPLY(SELECT TOP(1) * FROM dbo.CaseDateTypes WHERE ShaleClientId=pt.ShaleClientId AND SystemKey=e.SystemKey AND ISNULL(IsDeleted,0)=0 ORDER BY Id) tw
OUTER APPLY(SELECT TOP(1) * FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL AND SystemKey=e.SystemKey ORDER BY Id) gw;
CREATE UNIQUE CLUSTERED INDEX IX_EffectiveTypes ON #EffectiveCaseDateTypes(EffectiveTenantId,SystemKey);
SELECT l.*,et.EffectiveCaseDateTypeId,et.ResolutionSource,
 SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveExactMatches,
 SUM(CASE WHEN cd.Id IS NOT NULL AND NOT(cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveSameKeyDifferentValue,
 SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END) RemovedExactMatches
INTO #OccurrenceEvidence FROM #LegacyCaseDateSources l
LEFT JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey
LEFT JOIN dbo.CaseDateTypes variants ON variants.SystemKey=l.SystemKey AND (variants.ShaleClientId=l.ShaleClientId OR variants.ShaleClientId IS NULL)
LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=variants.Id
GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey,l.ExpectedStartsAt,l.ExpectedAllDay,et.EffectiveCaseDateTypeId,et.ResolutionSource;
SELECT 'UNRESOLVED_SOURCE_SAFE_IDS' SectionName,l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey FROM #LegacyCaseDateSources l LEFT JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey WHERE et.EffectiveCaseDateTypeId IS NULL OR ISNULL(et.NonDeletedTenantCount,0)>1 OR et.GlobalCount<>1 OR ISNULL(et.IsActive,0)<>1 OR ISNULL(et.IsDeleted,0)<>0 OR et.ActualCategory<>et.ExpectedCategory OR et.ActualSupportsTime<>et.ExpectedSupportsTime;
SELECT 'DESTINATION_RECONCILIATION_SAFE_IDS' SectionName,ShaleClientId,CaseId,FieldName,SystemKey,ActiveExactMatches,ActiveSameKeyDifferentValue,RemovedExactMatches FROM #OccurrenceEvidence WHERE EffectiveCaseDateTypeId IS NULL OR ActiveExactMatches<>1 OR ActiveSameKeyDifferentValue>0 OR RemovedExactMatches>0;
DECLARE @UnresolvedTypeCount bigint=(SELECT COUNT_BIG(*) FROM #LegacyCaseDateSources l LEFT JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey WHERE et.EffectiveCaseDateTypeId IS NULL OR ISNULL(et.NonDeletedTenantCount,0)>1 OR et.GlobalCount<>1 OR ISNULL(et.IsActive,0)<>1 OR ISNULL(et.IsDeleted,0)<>0 OR et.ActualCategory<>et.ExpectedCategory OR et.ActualSupportsTime<>et.ExpectedSupportsTime);
DECLARE @DestinationMismatchCount bigint=(SELECT COUNT_BIG(*) FROM #OccurrenceEvidence WHERE ActiveExactMatches<>1 OR ActiveSameKeyDifferentValue>0 OR RemovedExactMatches>0);
DECLARE @OrphanCallerTimeCount bigint=(SELECT COUNT_BIG(*) FROM dbo.Cases WHERE CallerTime IS NOT NULL AND CallerDate IS NULL);
DECLARE @WorkflowMismatchCount bigint=(SELECT COUNT_BIG(*) FROM dbo.Cases WHERE (ISNULL(FeeAgreementSigned,0)=1 AND DateFeeAgreementSigned IS NULL) OR (ISNULL(FeeAgreementSigned,0)=0 AND DateFeeAgreementSigned IS NOT NULL) OR (ISNULL(NonEngagementLetterSent,0)=1 AND DateNonEngagementLetterSent IS NULL) OR (ISNULL(NonEngagementLetterSent,0)=0 AND DateNonEngagementLetterSent IS NOT NULL));
DECLARE @CrossTenantCount bigint=(SELECT COUNT_BIG(*) FROM dbo.CaseDates cd LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId LEFT JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE c.Id IS NULL OR t.Id IS NULL OR c.ShaleClientId<>cd.ShaleClientId OR (t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>cd.ShaleClientId));
DECLARE @ActorMismatchCount bigint=(SELECT COUNT_BIG(*) FROM dbo.CaseDates cd LEFT JOIN dbo.Users u ON u.Id=cd.CreatedByUserId AND u.ShaleClientId=cd.ShaleClientId WHERE u.Id IS NULL);
DECLARE @ParticipatingTenantCount bigint=(SELECT COUNT_BIG(*) FROM #ParticipatingTenants),@EligibleSourceRowCount bigint=(SELECT COUNT_BIG(*) FROM #LegacyCaseDateSources);
DECLARE @VisibilityConfirmed bit=CONVERT(bit,CASE WHEN @SessionTenantId IS NULL AND @IsAdministrativePrincipal=1 AND @ParticipatingTenantCount>0 AND @EligibleSourceRowCount>0 THEN 1 ELSE 0 END);
DECLARE @BlockerCount bigint=(CASE WHEN @VisibilityConfirmed=1 THEN 0 ELSE 1 END)+@UnresolvedTypeCount+@DestinationMismatchCount+@OrphanCallerTimeCount+@WorkflowMismatchCount+@CrossTenantCount+@ActorMismatchCount;
SELECT 'FINAL_VALIDATION_SUMMARY' SectionName,CONVERT(nvarchar(128),@SessionTenantId) SessionContextShaleClientId,@ParticipatingTenantCount ParticipatingTenantCount,@EligibleSourceRowCount EligibleSourceRowCount,CASE WHEN @VisibilityConfirmed=1 THEN 'ALL_TENANT_VISIBILITY_CONFIRMED' ELSE 'VISIBILITY_NOT_CONFIRMED' END VisibilityReadiness,@UnresolvedTypeCount UnresolvedTypeSourceCount,@DestinationMismatchCount DestinationMismatchCount,@OrphanCallerTimeCount OrphanCallerTimeCount,@WorkflowMismatchCount WorkflowMismatchCount,@CrossTenantCount CrossTenantOrBrokenRelationshipCount,@ActorMismatchCount CreatedByTenantMismatchCount,@BlockerCount BlockerCount,CASE WHEN @BlockerCount=0 THEN 'SUCCESS' ELSE 'BLOCKED' END ValidationResult;
IF @BlockerCount<>0 THROW 56400,'Post-backfill validation blocked; review safe-id result sets and FINAL_VALIDATION_SUMMARY.',1;

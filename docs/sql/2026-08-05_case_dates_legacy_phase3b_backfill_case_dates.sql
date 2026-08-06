/* Phase 3B transactional backfill. REVIEW ONLY: do not run until preflight approval. */
SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @OperatorVerifiedAllTenantVisibility bit=0; -- Set to 1 only after the documented independent RLS/inventory verification.
IF OBJECT_ID(N'dbo.Cases',N'U') IS NULL THROW 56200,'Missing dbo.Cases.',1;
IF OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL THROW 56201,'Missing dbo.CaseDates.',1;
IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL THROW 56202,'Missing dbo.CaseDateTypes.',1;
IF COL_LENGTH(N'dbo.CaseDates',N'IsDeleted') IS NULL THROW 56203,'Expected dbo.CaseDates.IsDeleted.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1) THROW 56211,'All-tenant administrative visibility is required; ShaleClientId session context must be NULL.',1;
IF @OperatorVerifiedAllTenantVisibility<>1 THROW 56216,'Operator-verified all-tenant visibility is required before backfill.',1;
DECLARE @MigrationActors TABLE(ShaleClientId int NOT NULL PRIMARY KEY,CreatedByUserId int NOT NULL);
-- REQUIRED: explicitly populate one same-tenant actor for every participating tenant.
DECLARE @ExpectedTypes TABLE(SystemKey nvarchar(64) NOT NULL PRIMARY KEY, Name nvarchar(100) NOT NULL, CalendarCategory varchar(32) NOT NULL, SupportsTime bit NOT NULL);
INSERT @ExpectedTypes VALUES
(N'intake',N'Intake','OTHER',1),(N'date_of_injury',N'Date of Injury','MILESTONE',0),(N'date_of_medical_negligence',N'Date of Medical Negligence','MILESTONE',0),(N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered','MILESTONE',0),
(N'statute_of_limitations',N'Statute of Limitations','DEADLINE',0),(N'tort_notice_deadline',N'Tort Notice Deadline','NOTICE',0),(N'discovery_deadline',N'Discovery Deadline','DEADLINE',0),
(N'fee_agreement_signed',N'Fee Agreement Signed','MILESTONE',0),(N'non_engagement_letter_sent',N'Non-Engagement Letter Sent','MILESTONE',0);
SELECT c.ShaleClientId,c.Id CaseId,v.FieldName,v.SystemKey,
 CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NOT NULL THEN DATETIME2FROMPARTS(DATEPART(year,c.CallerDate),DATEPART(month,c.CallerDate),DATEPART(day,c.CallerDate),DATEPART(hour,CAST(c.CallerTime AS time)),DATEPART(minute,CAST(c.CallerTime AS time)),DATEPART(second,CAST(c.CallerTime AS time)),DATEPART(nanosecond,CAST(c.CallerTime AS time))/100,7) ELSE CAST(v.LegacyDate AS datetime2(7)) END ExpectedStartsAt,
 CAST(CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NOT NULL THEN 0 ELSE 1 END AS bit) ExpectedAllDay
INTO #LegacyCaseDateSources FROM dbo.Cases c CROSS APPLY(VALUES
 ('CallerDate','intake',c.CallerDate),('DateOfMedicalNegligence','date_of_medical_negligence',c.DateOfMedicalNegligence),('DateMedicalNegligenceWasDiscovered','date_medical_negligence_discovered',c.DateMedicalNegligenceWasDiscovered),('DateOfInjury','date_of_injury',c.DateOfInjury),('StatuteOfLimitations','statute_of_limitations',c.StatuteOfLimitations),('TortNoticeDeadline','tort_notice_deadline',c.TortNoticeDeadline),('DiscoveryDeadline','discovery_deadline',c.DiscoveryDeadline),('DateFeeAgreementSigned','fee_agreement_signed',c.DateFeeAgreementSigned),('DateNonEngagementLetterSent','non_engagement_letter_sent',c.DateNonEngagementLetterSent)
) v(FieldName,SystemKey,LegacyDate) WHERE v.LegacyDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0;
CREATE UNIQUE CLUSTERED INDEX IX_LegacySources ON #LegacyCaseDateSources(ShaleClientId,CaseId,SystemKey);
IF NOT EXISTS(SELECT 1 FROM #LegacyCaseDateSources) THROW 56212,'Visibility not confirmed: no eligible source rows are visible.',1;
IF EXISTS(SELECT 1 FROM dbo.Cases WHERE CallerTime IS NOT NULL AND CallerDate IS NULL) THROW 56205,'Orphan CallerTime blocks backfill.',1;
/* Workflow flag/date anomalies are preservation-only diagnostics: never invent dates or change flags. Valid non-null dates remain eligible. */
IF EXISTS(SELECT 1 FROM dbo.CaseDates cd LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId LEFT JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE c.Id IS NULL OR t.Id IS NULL OR c.ShaleClientId<>cd.ShaleClientId OR (t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>cd.ShaleClientId)) THROW 56214,'Broken or cross-tenant CaseDate case/type relationships block backfill.',1;
IF EXISTS(SELECT 1 FROM dbo.CaseDates cd LEFT JOIN dbo.Users u ON u.Id=cd.CreatedByUserId AND u.ShaleClientId=cd.ShaleClientId WHERE u.Id IS NULL) THROW 56215,'Missing or cross-tenant CaseDate creator relationships block backfill.',1;
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
IF EXISTS(SELECT 1 FROM #EffectiveCaseDateTypes WHERE EffectiveCaseDateTypeId IS NULL OR ISNULL(NonDeletedTenantCount,0)>1 OR GlobalCount<>1 OR ISNULL(IsDeleted,0)<>0 OR ISNULL(IsActive,0)<>1 OR ActualCategory<>ExpectedCategory OR ActualSupportsTime<>ExpectedSupportsTime) THROW 56204,'Every source key must resolve to one valid effective type.',1;
SELECT l.*,et.EffectiveCaseDateTypeId,et.ResolutionSource,
 SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveExactMatches,
 SUM(CASE WHEN cd.Id IS NOT NULL AND NOT(cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay) AND ISNULL(cd.IsDeleted,0)=0 THEN 1 ELSE 0 END) ActiveSameKeyDifferentValue,
 SUM(CASE WHEN cd.Id IS NOT NULL AND cd.StartsAt=l.ExpectedStartsAt AND cd.EndsAt IS NULL AND cd.AllDay=l.ExpectedAllDay AND ISNULL(cd.IsDeleted,0)=1 THEN 1 ELSE 0 END) RemovedExactMatches
INTO #OccurrenceEvidence FROM #LegacyCaseDateSources l
LEFT JOIN #EffectiveCaseDateTypes et ON et.EffectiveTenantId=l.ShaleClientId AND et.SystemKey=l.SystemKey
LEFT JOIN dbo.CaseDateTypes variants ON variants.SystemKey=l.SystemKey AND (variants.ShaleClientId=l.ShaleClientId OR variants.ShaleClientId IS NULL)
LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=variants.Id
GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey,l.ExpectedStartsAt,l.ExpectedAllDay,et.EffectiveCaseDateTypeId,et.ResolutionSource;
IF EXISTS(SELECT 1 FROM #OccurrenceEvidence WHERE ActiveExactMatches>1 OR ActiveSameKeyDifferentValue>0 OR RemovedExactMatches>0) THROW 56208,'Same-SystemKey CaseDates conflict; resolve manually.',1;
SELECT e.*,ma.CreatedByUserId INTO #ResolvedBackfill FROM #OccurrenceEvidence e LEFT JOIN @MigrationActors ma ON ma.ShaleClientId=e.ShaleClientId;
CREATE UNIQUE CLUSTERED INDEX IX_ResolvedBackfill_Row ON #ResolvedBackfill(ShaleClientId,CaseId,SystemKey);
DECLARE @SourceRowCount bigint=(SELECT COUNT_BIG(*) FROM #LegacyCaseDateSources),@ResolvedRowCount bigint=(SELECT COUNT_BIG(*) FROM #ResolvedBackfill WHERE EffectiveCaseDateTypeId IS NOT NULL);
IF @SourceRowCount<>@ResolvedRowCount THROW 56209,'Source rows were not resolved exactly once; no insert performed.',1;
IF EXISTS(SELECT 1 FROM #ResolvedBackfill WHERE CreatedByUserId IS NULL) THROW 56206,'Every participating tenant requires @MigrationActors.',1;
IF EXISTS(SELECT 1 FROM #ResolvedBackfill r WHERE NOT EXISTS(SELECT 1 FROM dbo.Users u WHERE u.Id=r.CreatedByUserId AND u.ShaleClientId=r.ShaleClientId AND ISNULL(u.is_deleted,0)=0)) THROW 56207,'Migration actor must be active and same-tenant.',1;
INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Notes,CreatedByUserId)
SELECT ShaleClientId,CaseId,EffectiveCaseDateTypeId,ExpectedStartsAt,NULL,ExpectedAllDay,NULL,CreatedByUserId FROM #ResolvedBackfill WHERE ActiveExactMatches=0;
DECLARE @InsertedOrExactExistingCount bigint=@@ROWCOUNT+(SELECT COUNT_BIG(*) FROM #ResolvedBackfill WHERE ActiveExactMatches=1);
IF @InsertedOrExactExistingCount<>@SourceRowCount THROW 56210,'Inserted-or-exact-existing reconciliation failed.',1;
COMMIT TRANSACTION; END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;

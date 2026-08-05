/*
  Post-backfill validation/reconciliation for legacy dbo.Cases date migration to dbo.CaseDates.
  Does not mutate data and intentionally reports aggregate counts and safe ids only.
*/
SET NOCOUNT ON;
DECLARE @TenantId int = NULL;

IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56000, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56001, 'Missing dbo.CaseDates.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56002, 'Missing dbo.CaseDateTypes.', 1;
IF OBJECT_ID(N'dbo.CaseStatuses', N'U') IS NULL PRINT 'BLOCKER: dbo.CaseStatuses table is not present; status-date migration cannot be generated.';

DECLARE @Legacy TABLE(FieldName sysname, SystemKey nvarchar(64), Category varchar(32), SupportsTime bit);
INSERT @Legacy VALUES
('CallerDate','intake','OTHER',1),('CallerTime','intake','OTHER',1),
('DateOfMedicalNegligence','date_of_medical_negligence','OTHER',0),('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered','OTHER',0),('DateOfInjury','date_of_injury','OTHER',0),
('StatuteOfLimitations','statute_of_limitations','DEADLINE',0),('TortNoticeDeadline','tort_notice_deadline','DEADLINE',0),('DiscoveryDeadline','discovery_deadline','DEADLINE',0),
('DateFeeAgreementSigned','fee_agreement_signed','MILESTONE',0),('DateNonEngagementLetterSent','non_engagement_letter_sent','MILESTONE',0),
('AcceptedDate',NULL,NULL,NULL),('DeniedDate',NULL,NULL,NULL),('ClosedDate',NULL,NULL,NULL);
SELECT 'Legacy field coverage' SectionName,* FROM @Legacy ORDER BY FieldName;

WITH L AS (
 SELECT c.ShaleClientId,c.Id CaseId,v.FieldName,v.SystemKey,v.LegacyDate,v.AllDay
 FROM dbo.Cases c CROSS APPLY (VALUES
  ('CallerDate','intake',CAST(c.CallerDate AS datetime2),CASE WHEN c.CallerTime IS NULL THEN 1 ELSE 0 END),
  ('DateOfMedicalNegligence','date_of_medical_negligence',CAST(c.DateOfMedicalNegligence AS datetime2),1),
  ('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered',CAST(c.DateMedicalNegligenceWasDiscovered AS datetime2),1),
  ('DateOfInjury','date_of_injury',CAST(c.DateOfInjury AS datetime2),1),
  ('StatuteOfLimitations','statute_of_limitations',CAST(c.StatuteOfLimitations AS datetime2),1),
  ('TortNoticeDeadline','tort_notice_deadline',CAST(c.TortNoticeDeadline AS datetime2),1),
  ('DiscoveryDeadline','discovery_deadline',CAST(c.DiscoveryDeadline AS datetime2),1),
  ('DateFeeAgreementSigned','fee_agreement_signed',CAST(c.DateFeeAgreementSigned AS datetime2),1),
  ('DateNonEngagementLetterSent','non_engagement_letter_sent',CAST(c.DateNonEngagementLetterSent AS datetime2),1),
  ('AcceptedDate',NULL,CAST(c.AcceptedDate AS datetime2),1),('DeniedDate',NULL,CAST(c.DeniedDate AS datetime2),1),('ClosedDate',NULL,CAST(c.ClosedDate AS datetime2),1)
 ) v(FieldName,SystemKey,LegacyDate,AllDay) WHERE (@TenantId IS NULL OR c.ShaleClientId=@TenantId) AND ISNULL(c.IsDeleted,0)=0
), E AS (
 SELECT l.*, COUNT_BIG(cd.Id) ExactMatches
 FROM L l LEFT JOIN dbo.CaseDateTypes t ON t.SystemKey=l.SystemKey AND (t.ShaleClientId IS NULL OR t.ShaleClientId=l.ShaleClientId) AND ISNULL(t.IsDeleted,0)=0
 LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=l.ShaleClientId AND cd.CaseId=l.CaseId AND cd.CaseDateTypeId=t.Id AND cd.StartsAt=l.LegacyDate AND cd.AllDay=l.AllDay AND cd.EndsAt IS NULL AND ISNULL(cd.IsDeleted,0)=0
 GROUP BY l.ShaleClientId,l.CaseId,l.FieldName,l.SystemKey,l.LegacyDate,l.AllDay
)
SELECT FieldName,ShaleClientId,COUNT_BIG(*) VisibleCases,SUM(CASE WHEN LegacyDate IS NOT NULL THEN 1 ELSE 0 END) NonNullCount,MIN(LegacyDate) MinValue,MAX(LegacyDate) MaxValue,SUM(CASE WHEN ExactMatches=1 THEN 1 ELSE 0 END) ExactExistingCaseDateMatches,SUM(CASE WHEN ExactMatches>1 THEN 1 ELSE 0 END) ConflictingExactCaseDateOccurrences FROM E GROUP BY FieldName,ShaleClientId ORDER BY FieldName,ShaleClientId;

SELECT 'Orphan CallerTime' SectionName, ShaleClientId, COUNT_BIG(*) RowCount FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) AND CallerTime IS NOT NULL AND CallerDate IS NULL GROUP BY ShaleClientId;
SELECT 'Workflow flag/date mismatch' SectionName, ShaleClientId, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=1 AND DateFeeAgreementSigned IS NULL THEN 1 ELSE 0 END) FeeFlagWithoutDate, SUM(CASE WHEN ISNULL(FeeAgreementSigned,0)=0 AND DateFeeAgreementSigned IS NOT NULL THEN 1 ELSE 0 END) FeeDateWithoutFlag, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=1 AND DateNonEngagementLetterSent IS NULL THEN 1 ELSE 0 END) NonEngagementFlagWithoutDate, SUM(CASE WHEN ISNULL(NonEngagementLetterSent,0)=0 AND DateNonEngagementLetterSent IS NOT NULL THEN 1 ELSE 0 END) NonEngagementDateWithoutFlag FROM dbo.Cases WHERE (@TenantId IS NULL OR ShaleClientId=@TenantId) GROUP BY ShaleClientId;
SELECT 'Existing destination types' SectionName, ShaleClientId,SystemKey,Name,CalendarCategory,Color,SupportsTime,IsActive,IsDeleted,COUNT(*) OVER(PARTITION BY ShaleClientId,SystemKey) DuplicateScopeCount FROM dbo.CaseDateTypes WHERE SystemKey IN ('intake','date_of_injury','date_of_medical_negligence','medical_negligence_discovered','statute_of_limitations','tort_notice_deadline','discovery_deadline','fee_agreement_signed','non_engagement_letter_sent') ORDER BY SystemKey,ShaleClientId;
IF OBJECT_ID(N'dbo.CaseStatuses', N'U') IS NOT NULL
BEGIN
 SELECT 'Status date/history evidence' SectionName, v.FieldName, c.ShaleClientId, COUNT_BIG(*) LegacyValues,
  SUM(CASE WHEN h.CaseId IS NULL THEN 1 ELSE 0 END) WithoutMatchingStatusHistoryEvidence,
  SUM(CASE WHEN h.MatchCount > 1 THEN 1 ELSE 0 END) RepeatedRelevantStatusTransitions
 FROM dbo.Cases c CROSS APPLY (VALUES ('AcceptedDate','accepted',c.AcceptedDate),('DeniedDate','denied',c.DeniedDate),('ClosedDate','closed',c.ClosedDate)) v(FieldName,LifecycleKey,LegacyDate)
 OUTER APPLY (SELECT COUNT_BIG(*) MatchCount, MAX(cs.CaseId) CaseId FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId WHERE cs.CaseId=c.Id AND (s.LifecycleKey=v.LifecycleKey OR s.SystemKey=v.LifecycleKey) AND CAST(cs.EffectiveDate AS date)=v.LegacyDate) h
 WHERE v.LegacyDate IS NOT NULL AND (@TenantId IS NULL OR c.ShaleClientId=@TenantId) GROUP BY v.FieldName,c.ShaleClientId;
 SELECT 'Reopened/repeated status transitions' SectionName,c.ShaleClientId,cs.CaseId,COUNT_BIG(*) TransitionRows FROM dbo.CaseStatuses cs JOIN dbo.Cases c ON c.Id=cs.CaseId JOIN dbo.Statuses s ON s.Id=cs.StatusId WHERE (@TenantId IS NULL OR c.ShaleClientId=@TenantId) AND (s.LifecycleKey IN ('accepted','denied','closed') OR s.SystemKey IN ('accepted','denied','closed','reopened')) GROUP BY c.ShaleClientId,cs.CaseId HAVING COUNT_BIG(*)>1;
END

-- Success criterion: unresolved/orphan/conflict counts above must be zero and each eligible non-status legacy row must have exactly one active destination CaseDate.

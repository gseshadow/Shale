/* Transactional idempotent backfill from legacy dbo.Cases dates into dbo.CaseDates. Does not touch CalendarEvents or clear/drop legacy columns. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL THROW 56200, 'Missing dbo.Cases.', 1;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 56201, 'Missing dbo.CaseDates.', 1;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56202, 'Missing dbo.CaseDateTypes.', 1;
DECLARE @MigrationActorUserId int = NULL;
SELECT TOP(1) @MigrationActorUserId = Id FROM dbo.Users WHERE IsDeleted=0 ORDER BY Id;
IF @MigrationActorUserId IS NULL THROW 56203, 'No safe CreatedByUserId available for CaseDates backfill; create/identify a migration actor first.', 1;
IF EXISTS (SELECT 1 FROM dbo.Cases WHERE CallerTime IS NOT NULL AND CallerDate IS NULL) THROW 56204, 'Unresolved orphan CallerTime rows exist; run preflight and resolve before backfill.', 1;
DECLARE @Map TABLE(FieldName sysname,SystemKey nvarchar(64),AllDay bit);
INSERT @Map VALUES ('CallerDate','intake',0),('DateOfMedicalNegligence','date_of_medical_negligence',1),('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered',1),('DateOfInjury','date_of_injury',1),('StatuteOfLimitations','statute_of_limitations',1),('TortNoticeDeadline','tort_notice_deadline',1),('DiscoveryDeadline','discovery_deadline',1),('DateFeeAgreementSigned','fee_agreement_signed',1),('DateNonEngagementLetterSent','non_engagement_letter_sent',1);
IF EXISTS (SELECT 1 FROM @Map m WHERE NOT EXISTS (SELECT 1 FROM dbo.CaseDateTypes t WHERE t.SystemKey=m.SystemKey AND t.ShaleClientId IS NULL AND ISNULL(t.IsDeleted,0)=0 AND ISNULL(t.IsActive,0)=1)) THROW 56205, 'Required global CaseDateType is missing or inactive.', 1;
WITH L AS (
 SELECT c.ShaleClientId,c.Id CaseId,v.FieldName,v.SystemKey,
  CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NOT NULL THEN DATEADD(NANOSECOND,DATEDIFF_BIG(NANOSECOND,CAST('00:00:00' AS time),CAST(c.CallerTime AS time)),CAST(c.CallerDate AS datetime2)) ELSE CAST(v.LegacyDate AS datetime2) END StartsAt,
  CASE WHEN v.FieldName='CallerDate' AND c.CallerTime IS NULL THEN CAST(1 AS bit) ELSE v.AllDay END AllDay
 FROM dbo.Cases c CROSS APPLY (VALUES
  ('CallerDate','intake',c.CallerDate,CAST(0 AS bit)),('DateOfMedicalNegligence','date_of_medical_negligence',c.DateOfMedicalNegligence,1),('DateMedicalNegligenceWasDiscovered','medical_negligence_discovered',c.DateMedicalNegligenceWasDiscovered,1),('DateOfInjury','date_of_injury',c.DateOfInjury,1),('StatuteOfLimitations','statute_of_limitations',c.StatuteOfLimitations,1),('TortNoticeDeadline','tort_notice_deadline',c.TortNoticeDeadline,1),('DiscoveryDeadline','discovery_deadline',c.DiscoveryDeadline,1),('DateFeeAgreementSigned','fee_agreement_signed',c.DateFeeAgreementSigned,1),('DateNonEngagementLetterSent','non_engagement_letter_sent',c.DateNonEngagementLetterSent,1)
 ) v(FieldName,SystemKey,LegacyDate,AllDay) WHERE v.LegacyDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0
), R AS (SELECT l.*,t.Id TypeId,COUNT(*) OVER(PARTITION BY l.ShaleClientId,l.CaseId,l.FieldName) SourceCount FROM L l JOIN dbo.CaseDateTypes t ON t.ShaleClientId IS NULL AND t.SystemKey=l.SystemKey AND ISNULL(t.IsDeleted,0)=0 AND ISNULL(t.IsActive,0)=1), C AS (SELECT r.*,COUNT(cd.Id) ExistingExact FROM R r LEFT JOIN dbo.CaseDates cd ON cd.ShaleClientId=r.ShaleClientId AND cd.CaseId=r.CaseId AND cd.CaseDateTypeId=r.TypeId AND cd.StartsAt=r.StartsAt AND cd.EndsAt IS NULL AND cd.AllDay=r.AllDay AND ISNULL(cd.IsDeleted,0)=0 GROUP BY r.ShaleClientId,r.CaseId,r.FieldName,r.SystemKey,r.StartsAt,r.AllDay,r.TypeId,r.SourceCount)
SELECT FieldName,COUNT_BIG(*) ConflictRows INTO #Conflicts FROM C WHERE ExistingExact>1 GROUP BY FieldName;
IF EXISTS (SELECT 1 FROM #Conflicts) THROW 56206, 'Conflicting existing CaseDates exact matches found; run preflight and resolve before backfill.', 1;
INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Notes,CreatedByUserId)
SELECT ShaleClientId,CaseId,TypeId,StartsAt,NULL,AllDay,NULL,@MigrationActorUserId FROM C WHERE ExistingExact=0;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;

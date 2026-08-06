/* Idempotent seed/verification for approved legacy CaseDateTypes. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL THROW 56100, 'Missing dbo.CaseDateTypes.', 1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1) THROW 56104, 'Global CaseDateType seed requires an approved administrative principal with NULL ShaleClientId session context.', 1;
DECLARE @Seeds TABLE(SystemKey nvarchar(64) NOT NULL PRIMARY KEY, Name nvarchar(100) NOT NULL, CalendarCategory varchar(32) NOT NULL, Color nvarchar(20) NOT NULL, SupportsTime bit NOT NULL, SortOrder int NOT NULL);
INSERT @Seeds VALUES
(N'intake',N'Intake','OTHER',N'#475569',1,5),(N'date_of_injury',N'Date of Injury','MILESTONE',N'#7C3AED',0,40),(N'date_of_medical_negligence',N'Date of Medical Negligence','MILESTONE',N'#9333EA',0,50),(N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered','MILESTONE',N'#A855F7',0,60),(N'statute_of_limitations',N'Statute of Limitations','DEADLINE',N'#DC2626',0,10),(N'tort_notice_deadline',N'Tort Notice Deadline','NOTICE',N'#EA580C',0,20),(N'discovery_deadline',N'Discovery Deadline','DEADLINE',N'#D97706',0,30),(N'fee_agreement_signed',N'Fee Agreement Signed','MILESTONE',N'#16A34A',0,110),(N'non_engagement_letter_sent',N'Non-Engagement Letter Sent','MILESTONE',N'#64748B',0,120);
IF EXISTS (SELECT 1 FROM dbo.CaseDateTypes t JOIN @Seeds s ON s.SystemKey=t.SystemKey WHERE t.ShaleClientId IS NULL AND (t.CalendarCategory<>s.CalendarCategory OR t.SupportsTime<>s.SupportsTime OR ISNULL(t.IsDeleted,0)<>0)) THROW 56101, 'Conflicting global CaseDateType definition exists.', 1;
IF EXISTS (SELECT 1 FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL AND SystemKey IN (SELECT SystemKey FROM @Seeds) GROUP BY SystemKey HAVING COUNT(*)>1) THROW 56102, 'Duplicate global CaseDateType system key exists.', 1;
INSERT dbo.CaseDateTypes(ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,IsDeleted,CreatedByUserId)
SELECT NULL,s.SystemKey,s.Name,N'Global default for legacy dbo.Cases date migration.',s.CalendarCategory,s.Color,s.SupportsTime,s.SortOrder,1,0,NULL FROM @Seeds s WHERE NOT EXISTS (SELECT 1 FROM dbo.CaseDateTypes t WHERE t.ShaleClientId IS NULL AND t.SystemKey=s.SystemKey);
IF EXISTS (SELECT 1 FROM dbo.CaseDateTypes t JOIN @Seeds s ON s.SystemKey=t.SystemKey WHERE t.ShaleClientId IS NULL AND (t.Name<>s.Name OR t.CalendarCategory<>s.CalendarCategory OR t.SupportsTime<>s.SupportsTime OR ISNULL(t.IsActive,0)<>1 OR ISNULL(t.IsDeleted,0)<>0)) THROW 56103, 'Post-seed CaseDateType verification failed.', 1;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;

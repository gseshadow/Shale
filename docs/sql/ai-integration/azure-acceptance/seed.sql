:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
:r docs/sql/ai-integration/azure-acceptance/owner.sql
GO
/* Synthetic rows from AiCaseDataLocalSqlServerTest.database/insertCase; no token/authority seed. */
DECLARE @SeedApproved bit=0;
DECLARE @A71 nvarchar(255)=NULL,@A72 nvarchar(255)=NULL,@B81 nvarchar(255)=NULL,@B82 nvarchar(255)=NULL;
IF @SeedApproved<>1 OR @A71 IS NULL OR @A72 IS NULL OR @B81 IS NULL OR @B82 IS NULL
 OR LEN(@A71)<>60 OR LEN(@A72)<>60 OR LEN(@B81)<>60 OR LEN(@B82)<>60
 OR @A71 NOT LIKE N'$2%' OR @A72 NOT LIKE N'$2%' OR @B81 NOT LIKE N'$2%' OR @B82 NOT LIKE N'$2%'
 THROW 59806,'STOP: approve synthetic seed and independently generated BCrypt hashes.',1;
IF EXISTS(SELECT 1 FROM dbo.ShaleClients) OR EXISTS(SELECT 1 FROM dbo.Users) OR EXISTS(SELECT 1 FROM dbo.Cases)
 THROW 59806,'STOP: seed requires empty tenant, user and Case tables.',1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 SET IDENTITY_INSERT dbo.ShaleClients ON;
 INSERT dbo.ShaleClients(Id,Code,DisplayName) VALUES(7,N'LOCAL_A',N'Local A'),(8,N'LOCAL_B',N'Local B');
 SET IDENTITY_INSERT dbo.ShaleClients OFF;
 SET IDENTITY_INSERT dbo.Users ON;
 INSERT dbo.Users(id,ShaleClientId,is_admin,is_deleted,IsRemoved,name_first,name_last,email,password_hash) VALUES
 (71,7,1,0,0,N'Synthetic',N'Admin',N'admin@example.invalid',@A71),
 (72,7,0,0,0,N'Synthetic',N'A72',N'a72@example.invalid',@A72),
 (81,8,1,0,0,N'Synthetic',N'BAdmin',N'badmin@example.invalid',@B81),
 (82,8,0,0,0,N'Synthetic',N'B82',N'b82@example.invalid',@B82);
 SET IDENTITY_INSERT dbo.Users OFF;
 SET IDENTITY_INSERT dbo.Cases ON;
 INSERT dbo.Cases(Id,ShaleClientId,Name,CaseNumber,IsDeleted,ClientEstate,FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,CreatedAt,UpdatedAt) VALUES
 (100,7,N'Literal Alpha',N'NUMBER',0,0,0,0,0,0,0,0,0,0,0,0,0,'2026-10-23T11:22:33.1234567','2026-10-23T11:22:33.1234567'),
 (200,8,N'Literal Alpha',N'NUMBER',0,0,0,0,0,0,0,0,0,0,0,0,0,'2026-10-23T11:22:33.1234567','2026-10-23T11:22:33.1234567'),
 (300,7,N'Literal deleted',N'NUMBER',1,0,0,0,0,0,0,0,0,0,0,0,0,'2026-10-23T11:22:33.1234567','2026-10-23T11:22:33.1234567');
 SET IDENTITY_INSERT dbo.Cases OFF;
 IF (SELECT COUNT(*) FROM dbo.ShaleClients)<>2 OR (SELECT COUNT(*) FROM dbo.Users)<>4
 OR (SELECT COUNT(*) FROM dbo.Cases)<>3
 THROW 59806,'STOP: synthetic seed count mismatch.',1;
 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
SELECT 'SYNTHETIC_ROWS_ONLY_NO_AUTHORITY' AS SeedStatus;

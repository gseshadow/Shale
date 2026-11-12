CREATE TABLE dbo.AiDcrControlV3(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),Enabled bit NOT NULL DEFAULT 0);
INSERT dbo.AiDcrControlV3 VALUES(1,0);
CREATE TABLE dbo.AiDcrRateV3(
 Kind tinyint NOT NULL CHECK(Kind BETWEEN 0 AND 2),KeyHash binary(32) NOT NULL,
 MinuteWindow bigint NOT NULL,Attempts int NOT NULL CHECK(Attempts BETWEEN 1 AND 100),
 CONSTRAINT PK_AiDcrRateV3 PRIMARY KEY(Kind,KeyHash,MinuteWindow)
);
GO
CREATE PROCEDURE dbo.AiDcrAdmissionV3 @Source varbinary(max),@Fingerprint varbinary(max),
 @Allowed bit=NULL OUTPUT,@RetryAfter int=NULL OUTPUT,@ReturnResult bit=1
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR ISNULL(IS_MEMBER(N'AiDcrEndpointV3'),0)<>1
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR @ReturnResult IS NULL OR @ReturnResult NOT IN(0,1)
 OR (@Source IS NULL AND @Fingerprint IS NULL) OR (@Source IS NOT NULL AND @Fingerprint IS NOT NULL)
 OR @Source IS NOT NULL AND DATALENGTH(@Source)<>32 OR @Fingerprint IS NOT NULL AND DATALENGTH(@Fingerprint)<>32
 THROW 59701,'Neutral bounded admission lease required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock int,@now datetime2(7)=SYSUTCDATETIME(),@window bigint;
 SET @Allowed=1;
 SET @window=DATEDIFF_BIG(minute,CONVERT(datetime2(7),'20000101'),@now);
 EXEC @lock=sys.sp_getapplock @Resource=N'AiDcrAdmissionV3',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 THROW 59701,'Admission unavailable.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiDcrControlV3 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 THROW 59701,'DCR disabled.',1;
 DELETE dbo.AiDcrRateV3 WHERE MinuteWindow<@window;
 DECLARE @keys table(Kind tinyint,KeyHash binary(32),Ceiling int);
 IF @Source IS NOT NULL INSERT @keys VALUES(0,CONVERT(binary(32),0x00),100),(1,@Source,10);
 ELSE INSERT @keys VALUES(2,@Fingerprint,10);
 IF EXISTS(SELECT 1 FROM @keys k JOIN dbo.AiDcrRateV3 r ON r.Kind=k.Kind AND r.KeyHash=k.KeyHash AND r.MinuteWindow=@window WHERE r.Attempts>=k.Ceiling)
 OR (SELECT COUNT(*) FROM dbo.AiDcrRateV3)+(SELECT COUNT(*) FROM @keys)>=1024 SET @Allowed=0;
 IF @Allowed=1 BEGIN
  UPDATE r SET Attempts=Attempts+1 FROM dbo.AiDcrRateV3 r JOIN @keys k ON k.Kind=r.Kind AND k.KeyHash=r.KeyHash WHERE r.MinuteWindow=@window;
  INSERT dbo.AiDcrRateV3 SELECT k.Kind,k.KeyHash,@window,1 FROM @keys k WHERE NOT EXISTS(SELECT 1 FROM dbo.AiDcrRateV3 r WHERE r.Kind=k.Kind AND r.KeyHash=k.KeyHash AND r.MinuteWindow=@window);
 END;
 COMMIT TRANSACTION;
 SET @RetryAfter=60-DATEPART(second,@now);
 IF @ReturnResult=1 SELECT @Allowed AS Allowed,@RetryAfter AS RetryAfter;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
/* Private owner projection sees all approval history despite tenant RLS.
   No positive grant. Invoked inside the registration transaction/registry lock. */
CREATE PROCEDURE dbo.AiDcrExpirePendingV3
WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59701,'Registration transaction required.',1;
 DECLARE @expired table(ApplicationId uniqueidentifier PRIMARY KEY);
 INSERT @expired SELECT TOP(100) e.ApplicationId FROM dbo.AiExternalApplication e WITH(XLOCK,HOLDLOCK)
 JOIN dbo.AiDcrMetadataV2 m ON m.ApplicationId=e.ApplicationId AND m.ApplicationRevision=e.Revision
 WHERE e.Source='DCR' AND m.PendingExpiresAt<=SYSUTCDATETIME()
 AND NOT EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WITH(UPDLOCK,HOLDLOCK) WHERE p.ApplicationId=e.ApplicationId AND p.IntegrationId IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM dbo.AiCimdKeyBindingV1 b WHERE b.ApplicationId=e.ApplicationId)
 AND NOT EXISTS(SELECT 1 FROM dbo.AiCimdNeutralReplayV1 r WHERE r.ApplicationId=e.ApplicationId AND r.ExpiresAt>SYSUTCDATETIME())
 ORDER BY m.PendingExpiresAt,e.ApplicationId;
 DELETE TOP(256) r FROM dbo.AiCimdNeutralReplayV1 r JOIN @expired x ON x.ApplicationId=r.ApplicationId WHERE r.ExpiresAt<=SYSUTCDATETIME();
 DELETE x FROM @expired x WHERE EXISTS(SELECT 1 FROM dbo.AiCimdNeutralReplayV1 r WHERE r.ApplicationId=x.ApplicationId);
 DELETE p FROM dbo.AiCimdPublicationV1 p JOIN @expired x ON x.ApplicationId=p.ApplicationId;
 DELETE m FROM dbo.AiDcrMetadataV2 m JOIN @expired x ON x.ApplicationId=m.ApplicationId;
END;
GO
CREATE TRIGGER dbo.AiDcrImmutableMetadataV2 ON dbo.AiDcrMetadataV2 AFTER UPDATE,DELETE AS
BEGIN
 IF EXISTS(SELECT 1 FROM inserted) OR USER_NAME()<>N'dbo'
 OR EXISTS(SELECT 1 FROM deleted d WHERE d.PendingExpiresAt>SYSUTCDATETIME()
 OR EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WHERE p.ApplicationId=d.ApplicationId AND p.IntegrationId IS NOT NULL))
 THROW 59600,'DCR revision metadata is immutable; only expired never-approved owner cleanup is allowed.',1;
END;
GO
CREATE PROCEDURE dbo.AiDcrStatusV3 AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR ISNULL(IS_MEMBER(N'AiDcrEndpointV3'),0)<>1 THROW 59701,'Neutral endpoint lease required.',1;
 SELECT CONVERT(bit,CASE WHEN EXISTS(SELECT 1 FROM dbo.AiDcrControlV3 WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WHERE Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WHERE Enabled=1) THEN 1 ELSE 0 END) AS Enabled;
END;
GO
CREATE PROCEDURE dbo.AiDcrCompatibilityV3 AS THROW 59700,'Private C2 compatibility metadata.',1;
GO
CREATE TRIGGER dbo.AiCimdImmutablePublicationV1 ON dbo.AiCimdPublicationV1 AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted) BEGIN
  IF UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(MetadataDigest) OR UPDATE(Redirects) OR UPDATE(SigningKeys)
   THROW 59501,'CIMD publication is immutable.',1;
  RETURN;
 END;
 IF NOT EXISTS(SELECT 1 FROM deleted) RETURN;
 IF USER_NAME()<>N'dbo' OR EXISTS(SELECT 1 FROM deleted d
 LEFT JOIN dbo.AiExternalApplication e ON e.ApplicationId=d.ApplicationId
 LEFT JOIN dbo.AiDcrMetadataV2 m ON m.ApplicationId=d.ApplicationId AND m.ApplicationRevision=d.ApplicationRevision
 WHERE e.Source<>'DCR' OR e.Source IS NULL OR m.PendingExpiresAt IS NULL OR m.PendingExpiresAt>SYSUTCDATETIME()
 OR EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WHERE p.ApplicationId=d.ApplicationId AND p.IntegrationId IS NOT NULL))
 THROW 59501,'Publication deletion requires expired never-approved DCR owner cleanup.',1;
END;

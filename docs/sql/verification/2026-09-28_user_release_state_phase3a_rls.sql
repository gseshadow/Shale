/* Execute after the Phase 3A migration with disposable published release/user fixtures. Rolls back all writes. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
BEGIN TRY
 DECLARE @ReleaseId bigint=(SELECT TOP(1) Id FROM dbo.ApplicationReleases WHERE PublicationStatus='PUBLISHED' AND ReleaseChannel='PRODUCTION' ORDER BY MajorVersion DESC,MinorVersion DESC,BuildVersion DESC);
 DECLARE @User7 int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=7),@User8 int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=8);
 IF @ReleaseId IS NULL OR @User7 IS NULL OR @User8 IS NULL THROW 57330,'Published production release and tenant 7/8 users are required.',1;
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7;
 INSERT dbo.UserReleaseState(ShaleClientId,UserId,ClientType,ReleaseChannel,ApplicationReleaseId,AcknowledgedAt) VALUES(7,@User7,'DESKTOP','PRODUCTION',@ReleaseId,SYSUTCDATETIME());
 IF NOT EXISTS(SELECT 1 FROM dbo.UserReleaseState WHERE ShaleClientId=7 AND UserId=@User7) THROW 57331,'Same-tenant insert/read failed.',1;
 IF EXISTS(SELECT 1 FROM dbo.UserReleaseState WHERE ShaleClientId=8) THROW 57332,'Tenant 7 read tenant 8 state.',1;
 BEGIN TRY
  INSERT dbo.UserReleaseState(ShaleClientId,UserId,ClientType,ReleaseChannel,ApplicationReleaseId,AcknowledgedAt) VALUES(8,@User8,'WEB','PRODUCTION',@ReleaseId,SYSUTCDATETIME());
  THROW 57333,'Tenant 7 cross-tenant insert unexpectedly succeeded.',1;
 END TRY BEGIN CATCH IF ERROR_NUMBER()=57333 THROW; END CATCH;
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8;
 IF EXISTS(SELECT 1 FROM dbo.UserReleaseState WHERE ShaleClientId=7) THROW 57334,'Tenant 8 read tenant 7 state.',1;
 ROLLBACK TRANSACTION;
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=NULL;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=NULL;
 THROW;
END CATCH;

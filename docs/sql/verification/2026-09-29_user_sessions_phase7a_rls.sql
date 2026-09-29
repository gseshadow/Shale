/* Disposable explicit non-dbo enforcement check. Requires existing tenant 7/8 users; cleans only its own row. */
SET NOCOUNT ON;
IF USER_ID(N'Phase7AUserSessionRlsVerifier') IS NOT NULL DROP USER Phase7AUserSessionRlsVerifier;
CREATE USER Phase7AUserSessionRlsVerifier WITHOUT LOGIN;
GRANT SELECT,INSERT,UPDATE ON dbo.UserSessions TO Phase7AUserSessionRlsVerifier;
DECLARE @SameTenant int=7,@SameUser int=(SELECT TOP(1) Id FROM dbo.Users WHERE ShaleClientId=7 ORDER BY Id),@OtherTenant int=8,@OtherUser int=(SELECT TOP(1) Id FROM dbo.Users WHERE ShaleClientId=8 ORDER BY Id);
IF @SameUser IS NULL OR @OtherUser IS NULL
BEGIN
 THROW 57710, 'RLS verification requires users in tenants 7 and 8.', 1;
END;
DECLARE @SessionId uniqueidentifier=NEWID(),@Jti uniqueidentifier=NEWID(),@RowId bigint,@InsertBlocked bit=0,@UpdateBlocked bit=0;
EXECUTE AS USER=N'Phase7AUserSessionRlsVerifier';
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@SameTenant;
INSERT dbo.UserSessions(ShaleClientId,UserId,SessionId,ClientType,CurrentAccessJti,ExpiresAt) VALUES(@SameTenant,@SameUser,@SessionId,'WEB',@Jti,DATEADD(day,1,SYSUTCDATETIME()));
SET @RowId=SCOPE_IDENTITY();
SELECT 'SameTenantInsertAndVisibility' CheckName,CASE WHEN EXISTS(SELECT 1 FROM dbo.UserSessions WHERE Id=@RowId) THEN 0 ELSE 1 END FindingCount;
SELECT 'CrossTenantVisibilityFiltered' CheckName,COUNT(*) FindingCount FROM dbo.UserSessions WHERE ShaleClientId=@OtherTenant;
BEGIN TRY
 INSERT dbo.UserSessions(ShaleClientId,UserId,SessionId,ClientType,CurrentAccessJti,ExpiresAt) VALUES(@OtherTenant,@OtherUser,NEWID(),'WEB',NEWID(),DATEADD(day,1,SYSUTCDATETIME()));
END TRY BEGIN CATCH IF ERROR_NUMBER()=33504 SET @InsertBlocked=1; ELSE THROW; END CATCH;
BEGIN TRY
 UPDATE dbo.UserSessions SET ShaleClientId=@OtherTenant,UserId=@OtherUser WHERE Id=@RowId;
END TRY BEGIN CATCH IF ERROR_NUMBER()=33504 SET @UpdateBlocked=1; ELSE THROW; END CATCH;
SELECT 'CrossTenantInsertBlocked33504' CheckName,CASE WHEN @InsertBlocked=1 THEN 0 ELSE 1 END FindingCount;
SELECT 'CrossTenantUpdateBlocked33504' CheckName,CASE WHEN @UpdateBlocked=1 THEN 0 ELSE 1 END FindingCount;
REVERT;
DELETE dbo.UserSessions WHERE Id=@RowId AND SessionId=@SessionId;
DROP USER Phase7AUserSessionRlsVerifier;

/* Run with a migration/admin connection. Enforcement itself executes as a disposable non-dbo user. */
SET NOCOUNT ON;
DECLARE @User7 int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=7),@User8 int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=8);
IF @User7 IS NULL OR @User8 IS NULL THROW 57430,'Tenant 7 and 8 user fixtures are required.',1;
IF USER_ID(N'Phase4BRlsVerifier') IS NULL CREATE USER Phase4BRlsVerifier WITHOUT LOGIN;
GRANT SELECT,INSERT ON dbo.ApplicationInstances TO Phase4BRlsVerifier;
EXECUTE AS USER=N'Phase4BRlsVerifier';
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7;
INSERT dbo.ApplicationInstances(ShaleClientId,UserId,MachineId,ClientType,MajorVersion,MinorVersion,BuildVersion) VALUES(7,@User7,'44444444-4444-4444-8444-444444444444','DESKTOP',1,0,0);
SELECT N'SameTenantInsert' CheckName,COUNT(*)-1 FindingCount FROM dbo.ApplicationInstances WHERE ShaleClientId=7 AND UserId=@User7 AND MajorVersion=1 AND MinorVersion=0 AND BuildVersion=0;
SELECT N'CrossTenantVisibility' CheckName,COUNT(*) FindingCount FROM dbo.ApplicationInstances WHERE ShaleClientId=8;
REVERT;
DELETE dbo.ApplicationInstances WHERE MachineId='44444444-4444-4444-8444-444444444444';
GO
/* Expected result: error 33504. Run as its own batch so no writes follow a doomed transaction. */
DECLARE @User8ForDenied int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=8);
EXECUTE AS USER=N'Phase4BRlsVerifier';
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7;
INSERT dbo.ApplicationInstances(ShaleClientId,UserId,MachineId,ClientType,MajorVersion,MinorVersion,BuildVersion) VALUES(8,@User8ForDenied,NEWID(),'DESKTOP',1,0,0);
REVERT;
GO
DROP USER Phase4BRlsVerifier;
GO

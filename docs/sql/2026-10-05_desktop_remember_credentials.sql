/* Required before deploying desktop/server remembered sign-in. Additive and rerunnable. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL
 BEGIN
  THROW 58100, 'UserSessions must be deployed before desktop remember credentials.', 1;
 END;
 IF OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.DesktopRememberCredentials(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_DesktopRememberCredentials PRIMARY KEY,
   ShaleClientId int NOT NULL,
   UserId int NOT NULL,
   SessionId uniqueidentifier NOT NULL,
   InstallationId uniqueidentifier NOT NULL,
   CredentialHash binary(32) NOT NULL,
   AbsoluteExpiresAt datetime2(7) NOT NULL,
   RotatedAt datetime2(7) NULL,
   ConsumedAt datetime2(7) NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_DesktopRememberCredentials_CreatedAt DEFAULT(SYSUTCDATETIME()),
   UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_DesktopRememberCredentials_UpdatedAt DEFAULT(SYSUTCDATETIME()),
   CONSTRAINT UQ_DesktopRememberCredentials_Hash UNIQUE(CredentialHash),
   CONSTRAINT UQ_DesktopRememberCredentials_SessionInstallation UNIQUE(SessionId,InstallationId),
   CONSTRAINT FK_DesktopRememberCredentials_Session FOREIGN KEY(SessionId) REFERENCES dbo.UserSessions(SessionId) ON DELETE CASCADE,
   CONSTRAINT FK_DesktopRememberCredentials_User FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id)
  );
  CREATE INDEX IX_DesktopRememberCredentials_Expiry ON dbo.DesktopRememberCredentials(AbsoluteExpiresAt);
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO

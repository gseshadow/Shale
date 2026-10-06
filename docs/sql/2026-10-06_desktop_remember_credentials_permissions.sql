/*
 Additive remembered-restore permission correction.
 Run as the approved migration principal, never as shale_app or shale_runtime.
 Grants no database role membership and no database-wide permission.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL
 THROW 58110,N'dbo.DesktopRememberCredentials is required.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL
 THROW 58111,N'Remembered restore prerequisite tables are required.',1;
IF DATABASE_PRINCIPAL_ID(N'shale_app') IS NULL
 THROW 58112,N'The established shale_app authentication principal is required.',1;
IF DATABASE_PRINCIPAL_ID(N'shale_runtime') IS NULL
 THROW 58113,N'The established shale_runtime tenant principal is required.',1;
GO

/* Ownership chaining permits this module to read its dbo-owned tables without granting shale_app table access. */
CREATE OR ALTER PROCEDURE dbo.ResolveDesktopRememberCredential
 @CredentialHash binary(32),
 @InstallationId uniqueidentifier,
 @Now datetime2(7)
AS
BEGIN
 SET NOCOUNT ON;
 SELECT r.ShaleClientId,r.UserId,r.SessionId,u.Email
 FROM dbo.DesktopRememberCredentials r
 JOIN dbo.Users u ON u.Id=r.UserId AND u.ShaleClientId=r.ShaleClientId
 WHERE r.CredentialHash=@CredentialHash
   AND r.InstallationId=@InstallationId
   AND r.ConsumedAt IS NULL
   AND r.AbsoluteExpiresAt>@Now
   AND COALESCE(u.is_deleted,0)=0
   AND COALESCE(u.IsRemoved,0)=0;
END;
GO

GRANT EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredential TO [shale_app];
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.DesktopRememberCredentials TO [shale_runtime];
GO

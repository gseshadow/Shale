/* REVIEW CANDIDATE ONLY; deployment is blocked pending the owning runbook's security/operator review.
   Additive versioned modules; no grants, roles, policy changes or data rewrites.
   Execute batches in order on ONE connection, stop on FIRST error and ROLLBACK.
   The runner must not continue after a failing GO batch. Never blindly replay historical grants.
   Proposed authority: database-local dbo via EXECUTE AS OWNER, confined to static module bodies.
   Both effective module owners must remain dbo. No runtime ALTER/CONTROL/IMPERSONATE authority.
   Ordinary ownership chaining alone does not bypass the established USER_NAME-based RLS.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF SCHEMA_ID(N'dbo') IS NULL OR
   COALESCE(USER_NAME((SELECT principal_id FROM sys.schemas WHERE name=N'dbo')),N'') <> N'dbo'
 THROW 58200,N'Authentication modules require reviewed dbo schema ownership.',1;
IF OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1') IS NOT NULL OR
   OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2') IS NOT NULL
 THROW 58201,N'Versioned authentication module already exists; review its definition and permissions.',1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL OR
   OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL OR
   OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL
 THROW 58202,N'Authentication prerequisite tables are required.',1;
BEGIN TRANSACTION;
GO
CREATE PROCEDURE dbo.LookupAuthenticationCredentialV1
 @Email nvarchar(max)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON;
 IF USER_NAME() <> N'dbo'
  THROW 58203,N'Authentication execution owner must be dbo.',1;
 -- Preserve existing email comparison/collation; no tenant selector or wildcard search.
 -- Capture at most two matches ONCE. Even an ineligible duplicate makes the identity ambiguous.
 SELECT TOP (2) Id,name_first,name_last,email,password_hash AS pw_hash,
        color,is_attorney,is_admin,is_deleted,IsRemoved,
        default_organization,organization_id,initials,ShaleClientId
 INTO #CredentialCandidates
 FROM dbo.Users WHERE email=CONVERT(nvarchar(255),@Email)
   AND @Email IS NOT NULL AND DATALENGTH(@Email) BETWEEN 1 AND 510;
 SELECT Id,name_first,name_last,email,pw_hash,color,is_attorney,is_admin,is_deleted,IsRemoved,
        default_organization,organization_id,initials,ShaleClientId
 FROM #CredentialCandidates
 WHERE (SELECT COUNT_BIG(*) FROM #CredentialCandidates)=1
   AND Id>0 AND ShaleClientId>0 AND is_deleted=0 AND COALESCE(IsRemoved,0)=0
   AND pw_hash IS NOT NULL AND DATALENGTH(pw_hash)>0
   AND EXISTS(SELECT 1 FROM dbo.ShaleClients t WHERE t.Id=#CredentialCandidates.ShaleClientId);
END;
GO
CREATE PROCEDURE dbo.ResolveDesktopRememberCredentialV2
 @CredentialHash varbinary(max),
 @InstallationId uniqueidentifier,
 @Now datetime2(7)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON;
 IF USER_NAME() <> N'dbo'
  THROW 58203,N'Authentication execution owner must be dbo.',1;
 -- Capture raw matches before owner/session joins; invalid ownership cannot hide ambiguity.
 SELECT TOP (2) r.ShaleClientId,r.UserId,r.SessionId,
        r.ConsumedAt,r.AbsoluteExpiresAt
 INTO #RememberCandidates
 FROM dbo.DesktopRememberCredentials r
 WHERE r.CredentialHash=CONVERT(binary(32),@CredentialHash) AND r.InstallationId=@InstallationId
   AND DATALENGTH(@CredentialHash)=32 AND @Now IS NOT NULL;
 SELECT r.ShaleClientId,r.UserId,r.SessionId,u.Email FROM #RememberCandidates r
 JOIN dbo.Users u ON u.Id=r.UserId AND u.ShaleClientId=r.ShaleClientId
 JOIN dbo.UserSessions s ON s.SessionId=r.SessionId
   AND s.ShaleClientId=r.ShaleClientId AND s.UserId=r.UserId
 WHERE (SELECT COUNT_BIG(*) FROM #RememberCandidates)=1
   AND r.ShaleClientId>0 AND r.UserId>0 AND r.ConsumedAt IS NULL
   AND r.AbsoluteExpiresAt>@Now AND r.AbsoluteExpiresAt>SYSUTCDATETIME()
   AND s.RevokedAt IS NULL AND s.ExpiresAt>@Now AND s.ExpiresAt>SYSUTCDATETIME()
   AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0
   AND EXISTS(SELECT 1 FROM dbo.ShaleClients t WHERE t.Id=r.ShaleClientId);
 -- Candidate only: the caller still revalidates/rotates with serializable runtime locks.
END;
GO
COMMIT TRANSACTION;
GO

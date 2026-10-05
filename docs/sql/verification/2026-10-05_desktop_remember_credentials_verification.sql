SET NOCOUNT ON;
SELECT CASE WHEN OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL THEN 1 ELSE 0 END AS FindingCount;
SELECT expected.name AS MissingColumn
FROM (VALUES(N'ShaleClientId'),(N'UserId'),(N'SessionId'),(N'InstallationId'),(N'CredentialHash'),(N'AbsoluteExpiresAt')) expected(name)
LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.DesktopRememberCredentials') AND c.name=expected.name
WHERE c.name IS NULL;
SELECT COUNT_BIG(*) AS ExpiredCredentialCount FROM dbo.DesktopRememberCredentials WHERE AbsoluteExpiresAt<=SYSUTCDATETIME();
SELECT COUNT_BIG(*) AS OrphanedOrRevokedCredentialCount
FROM dbo.DesktopRememberCredentials r LEFT JOIN dbo.UserSessions s ON s.SessionId=r.SessionId
WHERE s.SessionId IS NULL OR s.RevokedAt IS NOT NULL;
GO

/*
 READ ONLY. Run once while connected as shale_app and once while connected as shale_runtime.
 Do not run only as dbo: dbo cannot prove either application's effective permissions.
 No credential or credential hash is selected or accepted by this verifier.
*/
SET NOCOUNT ON;

DECLARE @Principal sysname=USER_NAME();
IF @Principal NOT IN(N'shale_app',N'shale_runtime')
 THROW 58120,N'Connect as the actual shale_app or shale_runtime database principal.',1;

SELECT @Principal AS InspectionPrincipal,
       ORIGINAL_LOGIN() AS InspectionLogin,
       TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')) AS SessionTenant,
       TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')) AS SessionActor;

DECLARE @Findings TABLE(CheckName nvarchar(160) NOT NULL,FindingCount int NOT NULL);
IF @Principal=N'shale_app'
BEGIN
 INSERT @Findings VALUES
  (N'authentication principal can execute narrow remembered lookup module',
   CASE WHEN HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredential',N'OBJECT',N'EXECUTE')=1 THEN 0 ELSE 1 END);
END;
ELSE
BEGIN
 INSERT @Findings VALUES
  (N'runtime principal can select remembered rows',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.DesktopRememberCredentials',N'OBJECT',N'SELECT')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can insert remembered rows',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.DesktopRememberCredentials',N'OBJECT',N'INSERT')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can update remembered rows',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.DesktopRememberCredentials',N'OBJECT',N'UPDATE')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can delete remembered rows',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.DesktopRememberCredentials',N'OBJECT',N'DELETE')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can select UserSessions',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.UserSessions',N'OBJECT',N'SELECT')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can update UserSessions',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.UserSessions',N'OBJECT',N'UPDATE')=1 THEN 0 ELSE 1 END),
  (N'runtime principal can select Users',CASE WHEN HAS_PERMS_BY_NAME(N'dbo.Users',N'OBJECT',N'SELECT')=1 THEN 0 ELSE 1 END);
END;

SELECT CheckName,FindingCount FROM @Findings ORDER BY CheckName;

SELECT p.name AS PolicyName,p.is_enabled AS PolicyEnabled,sp.predicate_type_desc AS PredicateType,
       sp.operation_desc AS PredicateOperation,sp.predicate_definition AS PredicateDefinition
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id=sp.object_id
WHERE sp.target_object_id=OBJECT_ID(N'dbo.UserSessions')
ORDER BY sp.predicate_type_desc,sp.operation_desc;

IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0)
 THROW 58121,N'Remembered restore effective-permission verification failed.',1;
GO

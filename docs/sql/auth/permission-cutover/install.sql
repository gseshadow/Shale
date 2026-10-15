:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/preflight.sql
IF OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1') IS NOT NULL
 OR OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2') IS NOT NULL
 THROW 58310,N'Versioned modules must be absent; never overwrite existing code or permissions.',1;
GO
:r docs/sql/auth/permission-cutover/capture.sql
CREATE TABLE #CutoverDelta(PermissionCount int,MembershipCount int);
INSERT #CutoverDelta VALUES(6,0);
GO
BEGIN TRANSACTION;
GO
-- Historical installer has its own nested transaction. Its COMMIT does NOT publish these objects:
-- the enclosing transaction stays open until fences and postchecks succeed.
:r docs/sql/2026-10-14_pretenant_authentication_modules.sql
DENY EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO shale_runtime AS dbo;
DENY EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredentialV2 TO shale_runtime AS dbo;
DENY EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO shale_runtime_role AS dbo;
DENY EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredentialV2 TO shale_runtime_role AS dbo;
GRANT EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO shale_app AS dbo;
GRANT EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredentialV2 TO shale_app AS dbo;
GO
:r docs/sql/auth/permission-cutover/module-postcheck.sql
:r docs/sql/auth/permission-cutover/unchanged.sql
COMMIT TRANSACTION;
SELECT N'AUTH_CUTOVER_INSTALLED' AS CheckName;
SELECT o.name,CONVERT(varchar(66),HASHBYTES('SHA2_256',m.definition),1) AS DefinitionHash
 FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id IN (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'));
GO

DROP TABLE #CutoverDelta;
GO

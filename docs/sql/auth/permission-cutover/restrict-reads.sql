:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/preflight.sql
IF N'$(CompatibleFleetDrained)'<>N'CONFIRMED' OR N'$(CallerInventoryApproved)'<>N'APPROVED'
 THROW 58314,N'Compatible installed fleet, jobs, password-management and projection inventory required.',1;
IF DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1') IS NOT NULL
 OR DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1') IS NOT NULL
 THROW 58315,N'Fence roles must be absent; do not reuse unknown authority.',1;
IF COALESCE((SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1')),N'')<>N'$(LookupDefinitionHash)'
 OR COALESCE((SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')),N'')<>N'$(RememberDefinitionHash)'
 THROW 58323,N'Use the approved prior installation hashes; unknown module code blocks restriction.',1;
GO
:r docs/sql/auth/permission-cutover/module-postcheck.sql
:r docs/sql/auth/permission-cutover/capture.sql
CREATE TABLE #CutoverDelta(PermissionCount int,MembershipCount int);
INSERT #CutoverDelta VALUES(4,3);
GO
BEGIN TRANSACTION;
CREATE ROLE ShaleAuthTableFenceV1 AUTHORIZATION dbo;
CREATE ROLE ShaleRuntimeHashFenceV1 AUTHORIZATION dbo;
-- Additive denials preserve every old grant/grantor/state for rollback.
DENY SELECT ON OBJECT::dbo.Users TO ShaleAuthTableFenceV1 AS dbo;
DENY SELECT ON OBJECT::dbo.DesktopRememberCredentials TO ShaleAuthTableFenceV1 AS dbo;
DENY EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredential TO ShaleAuthTableFenceV1 AS dbo;
ALTER ROLE ShaleAuthTableFenceV1 ADD MEMBER shale_app;
-- SELECT only. User create/reset still need INSERT and password_hash/password_alg UPDATE.
DENY SELECT ON OBJECT::dbo.Users(password_hash) TO ShaleRuntimeHashFenceV1 AS dbo;
ALTER ROLE ShaleRuntimeHashFenceV1 ADD MEMBER shale_runtime;
ALTER ROLE ShaleRuntimeHashFenceV1 ADD MEMBER shale_runtime_role;
GO
:r docs/sql/auth/permission-cutover/fence-postcheck.sql
:r docs/sql/auth/permission-cutover/read-postcheck.sql
:r docs/sql/auth/permission-cutover/module-postcheck.sql
:r docs/sql/auth/permission-cutover/unchanged.sql
COMMIT TRANSACTION;
SELECT N'AUTH_CUTOVER_READS_RESTRICTED' AS CheckName;
SELECT name,CONVERT(varchar(172),sid,1) AS FenceSid FROM sys.database_principals
 WHERE name IN (N'ShaleAuthTableFenceV1',N'ShaleRuntimeHashFenceV1');
GO

DROP TABLE #CutoverDelta;
GO

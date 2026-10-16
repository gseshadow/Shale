:ON ERROR EXIT
-- Read-only catalog/effective verification; never invoke a legacy or application procedure.
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF LEN(N'$(LegacyAfterHash)')<>66 OR TRY_CONVERT(varbinary(32),N'$(LegacyAfterHash)',1) IS NULL
 OR (SELECT StateHash FROM #LegacyFingerprint)<>TRY_CONVERT(varbinary(32),N'$(LegacyAfterHash)',1)
 THROW 58501,N'Original emitted AfterHash required; drift stops acceptance.',1;
IF (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets))<>6
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets)
 AND minor_id=0 AND type='EX' AND state='D' AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo')
 AND grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'shale_runtime'),DATABASE_PRINCIPAL_ID(N'shale_runtime_role')))<>6
 THROW 58501,N'Exact six retirement DENYs required.',1;
:r docs/sql/auth/permission-cutover/auth-authority-snapshot.sql
:r docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql
:r docs/sql/auth/permission-cutover/auth-authority-check.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-effective.sql
EXECUTE AS USER=N'shale_app';
BEGIN TRY
 IF COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users',N'OBJECT',N'SELECT'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredential',N'OBJECT',N'EXECUTE'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.sp_login_get_hash',N'OBJECT',N'EXECUTE'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.sp_login_mark_failure',N'OBJECT',N'EXECUTE'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.sp_login_mark_success',N'OBJECT',N'EXECUTE'),1)<>0
 THROW 58501,N'Auth legacy denial or retained Users/remembered permissions not verified.',1;
 REVERT;
END TRY BEGIN CATCH
 REVERT; THROW;
END CATCH;
SELECT N'OperatorCatalogVerified' AS CheckName;
GO

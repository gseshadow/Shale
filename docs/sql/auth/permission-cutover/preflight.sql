/* Metadata/effective preflight; session impersonation and temporary tables only. Run from repo root using SQLCMD -b, approved operator/dbo.
   Undefined sqlcmd variables intentionally fail. No credential values belong in variables/output.
   Configuration mapping is an OPERATOR assertion, never inferred from catalog names. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF @@TRANCOUNT<>0 OR USER_NAME()<>N'dbo' OR DB_NAME()<>N'$(ExpectedDatabase)'
 THROW 58300,N'Expected database, dbo metadata authority and no ambient transaction required.',1;
IF N'$(ReviewedIdentityMapping)'<>N'CONFIRMED' OR N'$(SecurityOwnerApproval)'<>N'APPROVED'
 THROW 58301,N'Actual server/desktop mapping and bounded hash/module authority must be approved.',1;
IF CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation'))<>N'SQL_Latin1_General_CP1_CI_AS'
 THROW 58302,N'Unexpected database collation; review comparison and identifier semantics.',1;
IF USER_NAME((SELECT principal_id FROM sys.schemas WHERE name=N'dbo'))<>N'dbo'
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_app' AND type IN ('S','E','X'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_runtime' AND type IN ('S','E','X'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_runtime_role' AND type='R' AND is_fixed_role=0)
 THROW 58303,N'Reviewed principals and dbo schema owner required.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.auditLog',N'U') IS NULL
 OR OBJECT_ID(N'dbo.Statuses',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL
 OR OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL OR OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL
 OR COL_LENGTH(N'dbo.Users',N'password_hash') IS NULL
 THROW 58304,N'Required existing tables and credential column must be visible.',1;
IF NOT EXISTS(SELECT 1 FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id
 JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredential',N'P')
 AND m.execute_as_principal_id IS NULL AND COALESCE(o.principal_id,s.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo')
 AND m.definition IS NOT NULL)
 THROW 58305,N'Old caller-executed dbo-owned remembered resolver must be reviewed and retained.',1;
-- Refuse incomplete or newly conflicting evidence; this task attaches no predicates.
IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN
 (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')))
 THROW 58306,N'Three-table predicate state changed; stop for compatibility review.',1;
GO
-- Effective checks, not merely direct ACLs. Caller is restored on every catch path.
-- Snapshot metadata under dbo before impersonation: denied metadata can otherwise disappear.
:r docs/sql/auth/permission-cutover/auth-authority-snapshot.sql
SELECT o.object_id,QUOTENAME(OBJECT_SCHEMA_NAME(o.object_id))+N'.'+QUOTENAME(o.name) AS Securable
 INTO #ElevatedModules FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.is_ms_shipped=0 AND (m.execute_as_principal_id IS NOT NULL OR m.definition IS NULL
 OR EXISTS(SELECT 1 FROM sys.crypt_properties cp WHERE cp.class=1 AND cp.major_id=o.object_id))
 AND o.object_id NOT IN (COALESCE(OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),-1),
 COALESCE(OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'),-1));
GO
:r docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql
:r docs/sql/auth/permission-cutover/auth-authority-check.sql
DECLARE @PreflightRuntimeUnsafe bit=0;
EXECUTE AS USER=N'shale_runtime';
BEGIN TRY
 IF COALESCE(IS_MEMBER(N'db_owner'),0)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SCHEMA'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SECURITY POLICY'),1)<>0
 OR EXISTS(SELECT 1 FROM sys.user_token t JOIN sys.database_principals p ON p.principal_id=t.principal_id
           WHERE p.is_fixed_role=1 AND p.name<>N'public')
 OR EXISTS(SELECT 1 FROM #CutoverPrincipals p WHERE p.name<>USER_NAME()
           AND HAS_PERMS_BY_NAME(p.name,'USER','IMPERSONATE')=1)
 OR EXISTS(SELECT 1 FROM #CutoverOwnership WHERE principal_id IN (SELECT principal_id FROM sys.user_token))
 OR COALESCE(IS_MEMBER(N'ShaleAdmins'),0)<>0
 OR COALESCE(IS_MEMBER(N'shale_runtime_role'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo','SCHEMA','ALTER'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo','USER','IMPERSONATE'),1)<>0
 OR EXISTS(SELECT 1 FROM #CutoverObjects WHERE HAS_PERMS_BY_NAME(Securable,'OBJECT','ALTER')=1
           OR HAS_PERMS_BY_NAME(Securable,'OBJECT','CONTROL')=1)
 OR EXISTS(SELECT 1 FROM #ElevatedModules WHERE HAS_PERMS_BY_NAME(Securable,'OBJECT','EXECUTE')=1)
  SET @PreflightRuntimeUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @PreflightRuntimeUnsafe=1 THROW 58308,N'Runtime identity has unexpected membership or escalation authority.',1;
GO

DROP TABLE #AuthAuthorityChecks;
DROP TABLE #CutoverOwnership;
DROP TABLE #CutoverGrantOptions;
DROP TABLE #CutoverVisibility;
DROP TABLE #CutoverPrincipals;
DROP TABLE #CutoverObjects;
DROP TABLE #CutoverColumns;
DROP TABLE #ElevatedModules;
GO

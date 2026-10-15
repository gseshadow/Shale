:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-approval.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF TRY_CONVERT(varbinary(32),N'$(LegacyBeforeHash)',1) IS NULL OR LEN(N'$(LegacyBeforeHash)')<>66
 OR (SELECT StateHash FROM #LegacyFingerprint)<>TRY_CONVERT(varbinary(32),N'$(LegacyBeforeHash)',1)
 THROW 58344,N'Independently reviewed baseline missing or drifted.',1;
-- Only captured G/GRANT AS dbo is supported. Reject W/D/R, other grantors or extra target ACLs.
IF (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets))<>3
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets)
 AND minor_id=0 AND type='EX' AND state='G' AND grantee_principal_id=DATABASE_PRINCIPAL_ID(N'shale_app')
 AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))<>3
 THROW 58345,N'Exact three original dbo GRANT rows required; no grant-option cascade or grantor substitution.',1;
SELECT * INTO #LegacyExpectedAcl FROM sys.database_permissions;
DELETE #LegacyExpectedAcl WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets);
INSERT #LegacyExpectedAcl
 SELECT 1,N'OBJECT_OR_COLUMN',t.ObjectId,0,p.principal_id,DATABASE_PRINCIPAL_ID(N'dbo'),'EX',N'EXECUTE','D',N'DENY'
 FROM #LegacyTargets t CROSS JOIN sys.database_principals p WHERE p.name IN(N'shale_runtime',N'shale_runtime_role');
SELECT OtherHash INTO #LegacyBeforeOther FROM #LegacyFingerprint;
BEGIN TRANSACTION;
GO
REVOKE EXECUTE ON OBJECT::dbo.sp_login_get_hash FROM shale_app AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_failure FROM shale_app AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_success FROM shale_app AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_get_hash TO shale_runtime AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_mark_failure TO shale_runtime AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_mark_success TO shale_runtime AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_get_hash TO shale_runtime_role AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_mark_failure TO shale_runtime_role AS dbo;
DENY EXECUTE ON OBJECT::dbo.sp_login_mark_success TO shale_runtime_role AS dbo;
GO
-- Reuse unchanged auth authority checks after the nine-row correction; independently check
-- runtime escalation and all three denials before commit. Full Stage A preflight is still required later.
:r docs/sql/auth/permission-cutover/auth-authority-snapshot.sql
:r docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql
:r docs/sql/auth/permission-cutover/auth-authority-check.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-effective.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF EXISTS(SELECT * FROM #LegacyExpectedAcl EXCEPT SELECT * FROM sys.database_permissions)
 OR EXISTS(SELECT * FROM sys.database_permissions EXCEPT SELECT * FROM #LegacyExpectedAcl)
 OR (SELECT OtherHash FROM #LegacyFingerprint)<>(SELECT OtherHash FROM #LegacyBeforeOther)
 THROW 58346,N'Unexpected catalog delta; rollback the entire retirement.',1;
COMMIT TRANSACTION;
SELECT N'LegacyRetirementCommitted' AS CheckName,N'$(LegacyBeforeHash)' AS BeforeHash,
 CONVERT(varchar(66),StateHash,1) AS AfterHash FROM #LegacyFingerprint;
GO

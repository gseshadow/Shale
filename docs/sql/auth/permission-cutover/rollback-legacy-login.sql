:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-approval.sql
IF N'$(LegacyRollbackApproval)'<>N'APPROVED' OR N'$(RollbackCompatibilityReviewed)'<>N'CONFIRMED'
 THROW 58348,N'Separate rollback approval and reopened capability/compatibility review required.',1;
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF LEN(N'$(LegacyBeforeHash)')<>66 OR TRY_CONVERT(varbinary(32),N'$(LegacyBeforeHash)',1) IS NULL
 OR LEN(N'$(LegacyAfterHash)')<>66 OR TRY_CONVERT(varbinary(32),N'$(LegacyAfterHash)',1) IS NULL
 OR (SELECT StateHash FROM #LegacyFingerprint)<>TRY_CONVERT(varbinary(32),N'$(LegacyAfterHash)',1)
 THROW 58344,N'Original saved before/after identities required; drift forbids rollback.',1;
IF (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets))<>6
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets)
 AND minor_id=0 AND type='EX' AND state='D' AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo')
 AND grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'shale_runtime'),DATABASE_PRINCIPAL_ID(N'shale_runtime_role')))<>6
 THROW 58345,N'Exact six retirement DENYs required; no unrelated ACL may be removed.',1;
BEGIN TRANSACTION;
GO
REVOKE EXECUTE ON OBJECT::dbo.sp_login_get_hash FROM shale_runtime AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_failure FROM shale_runtime AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_success FROM shale_runtime AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_get_hash FROM shale_runtime_role AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_failure FROM shale_runtime_role AS dbo;
REVOKE EXECUTE ON OBJECT::dbo.sp_login_mark_success FROM shale_runtime_role AS dbo;
GRANT EXECUTE ON OBJECT::dbo.sp_login_get_hash TO shale_app AS dbo;
GRANT EXECUTE ON OBJECT::dbo.sp_login_mark_failure TO shale_app AS dbo;
GRANT EXECUTE ON OBJECT::dbo.sp_login_mark_success TO shale_app AS dbo;
GO
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF (SELECT StateHash FROM #LegacyFingerprint)<>TRY_CONVERT(varbinary(32),N'$(LegacyBeforeHash)',1)
 THROW 58346,N'Rollback must restore the exact original catalog including grantor/state; rollback this attempt.',1;
COMMIT TRANSACTION;
SELECT N'LegacyRetirementRolledBack' AS CheckName,CONVERT(varchar(66),StateHash,1) AS StateHash FROM #LegacyFingerprint;
GO

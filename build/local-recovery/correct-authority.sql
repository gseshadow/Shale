-- LOCAL sandbox tooling only. Never included by a production entry point.
-- Connection/nonce, original baseline, complete findings and explicit review are checked by AuthorityCorrection.
IF CONVERT(int,SERVERPROPERTY('EngineEdition')) NOT IN(2,3,4)
 OR DB_NAME()<>N'ShaleRetirementExercise_$(OwnershipNonce)'
 OR DB_NAME()<>N'$(ExpectedDatabase)' OR USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0
 THROW 58600,N'Exact local owned sandbox and dedicated dbo required.',1;
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF (SELECT StateHash FROM #LegacyFingerprint)<>CONVERT(varbinary(32),N'$(LegacyBeforeHash)',1)
 THROW 58600,N'Original sandbox baseline drift; no correction permitted.',1;
SELECT OtherHash INTO #OwnershipOther FROM #LegacyFingerprint;
SELECT * INTO #OwnershipExpectedAcl FROM sys.database_permissions;
IF (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'sandbox.Ownership'))<>2
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'sandbox.Ownership')
 AND minor_id=0 AND type='SL' AND state='G' AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo')
 AND grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'shale_app'),DATABASE_PRINCIPAL_ID(N'shale_runtime')))<>2
 THROW 58600,N'Exact two original sandbox grants required.',1;
DELETE #OwnershipExpectedAcl WHERE class=1 AND major_id=OBJECT_ID(N'sandbox.Ownership')
 AND grantee_principal_id=DATABASE_PRINCIPAL_ID(N'shale_app');
BEGIN TRANSACTION;
REVOKE SELECT ON OBJECT::sandbox.Ownership FROM shale_app AS dbo;
GO
:r docs/sql/auth/permission-cutover/auth-authority-snapshot.sql
:r docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql
-- The three legacy EXECUTEs are still present: this correction does NOT retire them.
IF (SELECT COUNT(*) FROM #AuthAuthorityChecks WHERE EffectiveValue=1 OR EffectiveValue IS NULL)<>3
 OR EXISTS(SELECT 1 FROM #AuthAuthorityChecks WHERE (EffectiveValue=1 OR EffectiveValue IS NULL)
 AND (EffectiveValue IS NULL OR ConditionName<>N'object' OR PermissionName<>N'EXECUTE' OR Securable NOT IN
 (N'[dbo].[sp_login_get_hash]',N'[dbo].[sp_login_mark_failure]',N'[dbo].[sp_login_mark_success]')))
 THROW 58600,N'Unexpected complete authority findings after local correction.',1;
EXECUTE AS USER=N'shale_app';
IF COALESCE(HAS_PERMS_BY_NAME(N'sandbox.Ownership',N'OBJECT',N'SELECT'),1)<>0
 THROW 58600,N'Auth ownership data restriction failed.',1;
REVERT;
:r docs/sql/auth/permission-cutover/legacy-retirement-fingerprint.sql
IF EXISTS(SELECT * FROM #OwnershipExpectedAcl EXCEPT SELECT * FROM sys.database_permissions)
 OR EXISTS(SELECT * FROM sys.database_permissions EXCEPT SELECT * FROM #OwnershipExpectedAcl)
 OR (SELECT OtherHash FROM #LegacyFingerprint)<>(SELECT OtherHash FROM #OwnershipOther)
 THROW 58600,N'Correction exceeded its exact one ACL row removal.',1;
-- Extended properties and rows are outside the legacy fingerprint; validate originals explicitly.
IF (SELECT COUNT(*) FROM sandbox.Ownership)<>1
 OR (SELECT COUNT(*) FROM sandbox.Ownership WHERE Nonce=N'$(OwnershipNonce)')<>1
 OR (SELECT COUNT(*) FROM sys.extended_properties WHERE class=0 AND name=N'ShaleRecoveryNonce'
 AND CONVERT(nvarchar(128),value)=N'$(OwnershipNonce)')<>1
 THROW 58600,N'Original ownership evidence changed.',1;
COMMIT;
SELECT N'LocalOwnershipCorrected' AS CheckName,CONVERT(varchar(66),StateHash,1) AS StateHash FROM #LegacyFingerprint;
GO

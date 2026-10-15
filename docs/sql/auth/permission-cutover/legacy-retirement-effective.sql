-- No application modules executed. Snapshot targets before impersonating metadata-denied users.
SELECT m.object_id INTO #LegacyElevated FROM sys.sql_modules m WHERE
 (execute_as_principal_id IS NOT NULL OR definition IS NULL OR EXISTS
 (SELECT 1 FROM sys.crypt_properties cp WHERE cp.class=1 AND cp.major_id=m.object_id));
DECLARE @LegacyUnsafe bit=0;
EXECUTE AS USER=N'shale_runtime';
BEGIN TRY
 IF COALESCE(IS_MEMBER(N'shale_runtime_role'),0)<>1
 OR EXISTS(SELECT 1 FROM sys.user_token t JOIN sys.database_principals p ON p.principal_id=t.principal_id
 WHERE p.is_fixed_role=1 AND p.name<>N'public')
 OR EXISTS(SELECT 1 FROM #CutoverOwnership WHERE principal_id IN(SELECT principal_id FROM sys.user_token))
 OR EXISTS(SELECT 1 FROM #CutoverPrincipals WHERE type IN('S','E','X') AND name<>USER_NAME() AND COALESCE(HAS_PERMS_BY_NAME(name,'USER','IMPERSONATE'),1)<>0)
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SCHEMA'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SECURITY POLICY'),1)<>0
 OR COALESCE(IS_MEMBER(N'ShaleAdmins'),0)<>0
 OR EXISTS(SELECT 1 FROM #CutoverObjects WHERE object_id IN(SELECT object_id FROM #LegacyElevated)
 AND COALESCE(HAS_PERMS_BY_NAME(Securable,'OBJECT','EXECUTE'),1)<>0)
 OR EXISTS(SELECT 1 FROM #CutoverObjects WHERE COALESCE(HAS_PERMS_BY_NAME(Securable,'OBJECT','ALTER'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(Securable,'OBJECT','CONTROL'),1)<>0)
 OR EXISTS(SELECT 1 FROM #CutoverObjects WHERE object_id IN(SELECT ObjectId FROM #LegacyTargets) AND COALESCE(HAS_PERMS_BY_NAME(Securable,'OBJECT','EXECUTE'),1)<>0)
 SET @LegacyUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @LegacyUnsafe=1 THROW 58347,N'Runtime escalation or inherited legacy access remains; rollback.',1;
GO

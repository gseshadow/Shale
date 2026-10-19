-- Ownership chaining through a caller-executed wrapper can skip the target's DENY.
-- Reject static/unresolved module callers too; do not trust direct HAS_PERMS alone.
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies WHERE referenced_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'))
 OR (referenced_entity_name IN (N'LookupAuthenticationCredentialV1',N'ResolveDesktopRememberCredentialV2')
 AND COALESCE(referenced_schema_name,N'dbo')=N'dbo'))
 THROW 58328,N'Wrapper/deferred module dependencies require separate authority review.',1;
GO
DECLARE @Expected TABLE(grantee_principal_id int,state char(1));
INSERT @Expected VALUES (DATABASE_PRINCIPAL_ID(N'shale_app'),'G'),
 (DATABASE_PRINCIPAL_ID(N'shale_runtime'),'D'),(DATABASE_PRINCIPAL_ID(N'shale_runtime_role'),'D');
IF (SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')))<>6
 OR EXISTS(SELECT o.object_id,e.grantee_principal_id,e.state FROM sys.objects o CROSS JOIN @Expected e
 WHERE o.object_id IN (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'))
 EXCEPT SELECT major_id,grantee_principal_id,state COLLATE DATABASE_DEFAULT FROM sys.database_permissions
 WHERE class=1 AND minor_id=0 AND permission_name=N'EXECUTE' AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))
 THROW 58325,N'New module ACLs drifted; preserve unknown grantors/states for review.',1;

GO
IF (SELECT COUNT(*) FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id
 JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.object_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1',N'P'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2',N'P'))
 AND m.execute_as_principal_id=-2 AND COALESCE(o.principal_id,s.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo')
 AND m.definition IS NOT NULL)<>2
 THROW 58311,N'Both reviewed modules require visible definitions and dbo owner execution.',1;
DECLARE @ModuleRuntimeUnsafe bit=0;
EXECUTE AS USER=N'shale_runtime';
BEGIN TRY
 IF COALESCE(HAS_PERMS_BY_NAME(N'dbo.LookupAuthenticationCredentialV1','OBJECT','EXECUTE'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredentialV2','OBJECT','EXECUTE'),1)<>0
  SET @ModuleRuntimeUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @ModuleRuntimeUnsafe=1 THROW 58312,N'Runtime inherited EXECUTE was not fenced.',1;
DECLARE @ModuleAuthUnsafe bit=0;
EXECUTE AS USER=N'shale_app';
BEGIN TRY
 IF COALESCE(HAS_PERMS_BY_NAME(N'dbo.LookupAuthenticationCredentialV1','OBJECT','EXECUTE'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredentialV2','OBJECT','EXECUTE'),0)<>1
  SET @ModuleAuthUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @ModuleAuthUnsafe=1 THROW 58313,N'Auth module capability unavailable.',1;
GO

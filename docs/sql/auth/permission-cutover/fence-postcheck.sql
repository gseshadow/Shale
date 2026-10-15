-- Refuse changed roles, additional members, ACLs, owners or grantors. No CASCADE or grant replay.
IF (SELECT COUNT(*) FROM sys.database_role_members WHERE role_principal_id IN
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1')))<>3
 OR NOT EXISTS(SELECT 1 FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1') AND member_principal_id=DATABASE_PRINCIPAL_ID(N'shale_app'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1') AND member_principal_id=DATABASE_PRINCIPAL_ID(N'shale_runtime'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1') AND member_principal_id=DATABASE_PRINCIPAL_ID(N'shale_runtime_role'))
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1')))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN (N'ShaleAuthTableFenceV1',N'ShaleRuntimeHashFenceV1') AND (COALESCE(owning_principal_id,-1)<>DATABASE_PRINCIPAL_ID(N'dbo') OR type<>'R' OR is_fixed_role<>0))
 THROW 58320,N'Fence role memberships/owners drifted; manual exact-state review required.',1;
DECLARE @Expected TABLE(grantee_principal_id int,major_id int,minor_id int,permission_name nvarchar(128));
INSERT @Expected VALUES
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),OBJECT_ID(N'dbo.Users'),0,N'SELECT'),
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),OBJECT_ID(N'dbo.DesktopRememberCredentials'),0,N'SELECT'),
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredential'),0,N'EXECUTE'),
 (DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1'),OBJECT_ID(N'dbo.Users'),COLUMNPROPERTY(OBJECT_ID(N'dbo.Users'),N'password_hash','ColumnId'),N'SELECT');
IF EXISTS(SELECT grantee_principal_id,major_id,minor_id,permission_name FROM @Expected
 EXCEPT SELECT grantee_principal_id,major_id,minor_id,permission_name COLLATE DATABASE_DEFAULT FROM sys.database_permissions
 WHERE class=1 AND state='D' AND grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id IN
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1')))<>4
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantor_principal_id IN
 (DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1'),DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1')))
 THROW 58321,N'Fence permission states/grantors drifted; stop.',1;

GO

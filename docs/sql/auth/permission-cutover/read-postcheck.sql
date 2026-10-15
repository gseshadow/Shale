SELECT QUOTENAME(OBJECT_SCHEMA_NAME(object_id))+N'.'+QUOTENAME(OBJECT_NAME(object_id)) AS Securable,name AS ColumnName
 INTO #AuthColumns FROM sys.columns WHERE object_id IN
 (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.DesktopRememberCredentials'));
IF NOT EXISTS(SELECT 1 FROM #AuthColumns) THROW 58326,N'Full column metadata required.',1;
DECLARE @ReadAuthUnsafe bit=0;
EXECUTE AS USER=N'shale_app';
BEGIN TRY
 -- Column GRANT can defeat a table DENY in SQL Server. Check EVERY existing column, fail closed.
 IF EXISTS(SELECT 1 FROM #AuthColumns WHERE COALESCE(HAS_PERMS_BY_NAME(Securable,
 'OBJECT','SELECT',ColumnName,'COLUMN'),1)<>0)
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredential','OBJECT','EXECUTE'),1)<>0
  SET @ReadAuthUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @ReadAuthUnsafe=1 THROW 58316,N'Auth retains direct column access or the retired resolver; review without rewriting grants.',1;
DECLARE @ReadRuntimeUnsafe bit=0;
EXECUTE AS USER=N'shale_runtime';
BEGIN TRY
 IF COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','SELECT',N'password_hash','COLUMN'),1)<>0
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','SELECT',N'id','COLUMN'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','UPDATE',N'password_hash','COLUMN'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','UPDATE',N'password_alg','COLUMN'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','INSERT'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(N'dbo.auditLog','OBJECT','INSERT'),0)<>1
  SET @ReadRuntimeUnsafe=1;
 REVERT;
END TRY BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 REVERT; THROW;
END CATCH;
IF @ReadRuntimeUnsafe=1 THROW 58317,N'Hash fence or required User/password/audit capability failed.',1;
GO

DROP TABLE #AuthColumns;
GO

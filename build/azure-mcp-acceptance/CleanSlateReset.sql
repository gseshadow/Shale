/* Invoked only by Start-CleanSlateAcceptance after its single approval.
   In-place reset preserves database owner, Azure configuration and credentials.
   No server DDL, USE, database create/drop or three-part object names. */
SET XACT_ABORT ON;
IF CONVERT(varbinary(max),ISNULL(DB_NAME(),N''))<>CONVERT(varbinary(max),N'ShaleMcpAcceptance')
 OR CONVERT(varbinary(max),ISNULL(CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')),N'')) NOT IN(CONVERT(varbinary(max),N'shale'),CONVERT(varbinary(max),N'shale.database.windows.net'))
 OR ISNULL(CONVERT(int,SERVERPROPERTY('EngineEdition')),0)<>5
 OR CONVERT(varbinary(max),ISNULL(CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')),N''))<>CONVERT(varbinary(max),N'SQL_Latin1_General_CP1_CI_AS')
 OR ISNULL(USER_NAME(),N'')<>N'dbo' OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 OR @@TRANCOUNT<>0 OR (2 & @@OPTIONS)=2
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalEmail') IS NOT NULL
 THROW 59800,'RESET_IDENTITY_REFUSED',1;
/* Only the known acceptance table type is supported. Its TT catalog object and
   type-owned constraints survive until referencing modules have been removed. */
DECLARE @AcceptanceTableTypeObjectId int=(
 SELECT type_table_object_id FROM sys.table_types
 WHERE CONVERT(varbinary(max),SCHEMA_NAME(schema_id))=CONVERT(varbinary(max),N'dbo')
  AND CONVERT(varbinary(max),name)=CONVERT(varbinary(max),N'AiCaseDataIds'));
/* Refuse unfamiliar database features before any destructive statement.
   This reset supports the acceptance fixture / installer object families only. */
IF EXISTS(SELECT 1 FROM sys.triggers WHERE parent_class=0 AND is_ms_shipped=0)
 OR EXISTS(SELECT 1 FROM sys.tables WHERE is_ms_shipped=0 AND (temporal_type<>0 OR is_memory_optimized=1 OR is_external=1))
 OR EXISTS(SELECT 1 FROM sys.assemblies WHERE is_user_defined=1)
 OR EXISTS(SELECT 1 FROM sys.database_scoped_credentials)
 OR EXISTS(SELECT 1 FROM sys.external_data_sources)
 OR EXISTS(SELECT 1 FROM sys.certificates WHERE name NOT LIKE '##%')
 OR EXISTS(SELECT 1 FROM sys.asymmetric_keys)
 OR EXISTS(SELECT 1 FROM sys.symmetric_keys WHERE name<>N'##MS_DatabaseMasterKey##')
 OR EXISTS(SELECT 1 FROM sys.xml_schema_collections WHERE xml_collection_id>1)
 OR EXISTS(SELECT 1 FROM sys.types t WHERE t.is_user_defined=1
  AND NOT EXISTS(SELECT 1 FROM sys.table_types tt WHERE tt.user_type_id=t.user_type_id
   AND tt.type_table_object_id=@AcceptanceTableTypeObjectId))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id>4 AND type NOT IN('R','S','E','X'))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE is_ms_shipped=0 AND type NOT IN('U','V','P','FN','IF','TF','TR','C','D','F','PK','UQ','SO','SN','IT','SP')
  AND NOT(type='TT' AND object_id=ISNULL(@AcceptanceTableTypeObjectId,-1)))
 THROW 59802,'RESET_UNSUPPORTED_FEATURE',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @LockResult int;
 EXEC @LockResult=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=0;
 IF @LockResult<0 THROW 59802,'RESET_MAINTENANCE_LOCK_REFUSED',1;
 DECLARE @Ddl nvarchar(max),@Id int;
 DECLARE @Drops table(Id int PRIMARY KEY,Ddl nvarchar(max));
 INSERT @Drops SELECT object_id,N'DROP SECURITY POLICY '+QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies;
 INSERT @Drops SELECT object_id,N'ALTER TABLE '+QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id))+N'.'+QUOTENAME(OBJECT_NAME(parent_object_id))+N' DROP CONSTRAINT '+QUOTENAME(name)
 FROM sys.objects WHERE is_ms_shipped=0 AND type IN('F','C','D') AND parent_object_id<>0
  AND parent_object_id<>ISNULL(@AcceptanceTableTypeObjectId,-1);
 INSERT @Drops SELECT object_id,N'DROP TRIGGER '+QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.objects WHERE is_ms_shipped=0 AND type='TR';
 WHILE EXISTS(SELECT 1 FROM @Drops)
 BEGIN
  SELECT TOP(1) @Id=Id,@Ddl=Ddl FROM @Drops ORDER BY Id;
  EXEC sys.sp_executesql @Ddl;
  DELETE @Drops WHERE Id=@Id;
 END;
 /* Drop referencing objects before schema-bound dependencies. Table-owned
    indexes/constraints/internal objects are removed with their table. */
 WHILE EXISTS(SELECT 1 FROM sys.objects WHERE is_ms_shipped=0 AND parent_object_id=0 AND type IN('U','V','P','FN','IF','TF','SO','SN'))
 BEGIN
  SET @Ddl=NULL;
  SELECT TOP(1) @Ddl=N'DROP '+CASE o.type WHEN 'U' THEN N'TABLE' WHEN 'V' THEN N'VIEW' WHEN 'P' THEN N'PROCEDURE'
   WHEN 'SO' THEN N'SEQUENCE' WHEN 'SN' THEN N'SYNONYM' ELSE N'FUNCTION' END+N' '+QUOTENAME(SCHEMA_NAME(o.schema_id))+N'.'+QUOTENAME(o.name)
  FROM sys.objects o WHERE o.is_ms_shipped=0 AND o.parent_object_id=0 AND o.type IN('U','V','P','FN','IF','TF','SO','SN')
   AND NOT EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d WHERE d.referenced_id=o.object_id AND d.referencing_id<>o.object_id AND d.is_schema_bound_reference=1)
  ORDER BY o.object_id;
  IF @Ddl IS NULL THROW 59802,'RESET_DEPENDENCY_CYCLE',1;
  EXEC sys.sp_executesql @Ddl;
 END;
 /* Table types cannot be altered like ordinary tables. DROP TYPE removes its
    CHECK/PK objects atomically, after all referencing procedures/functions. */
 IF @AcceptanceTableTypeObjectId IS NOT NULL
  EXEC sys.sp_executesql N'DROP TYPE [dbo].[AiCaseDataIds];';
 IF EXISTS(SELECT 1 FROM sys.objects WHERE is_ms_shipped=0) THROW 59802,'RESET_OBJECTS_REMAIN',1;
 DECLARE @Name sysname;
 WHILE EXISTS(SELECT 1 FROM sys.schemas WHERE schema_id<16384 AND name NOT IN(N'dbo',N'guest',N'sys',N'INFORMATION_SCHEMA'))
 BEGIN
  SELECT TOP(1) @Name=name FROM sys.schemas WHERE schema_id<16384 AND name NOT IN(N'dbo',N'guest',N'sys',N'INFORMATION_SCHEMA');
  SET @Ddl=N'DROP SCHEMA '+QUOTENAME(@Name); EXEC sys.sp_executesql @Ddl;
 END;
 /* Remove role memberships/ownership before dropping users and custom roles. */
 WHILE EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id>4)
 BEGIN
  SELECT TOP(1) @Ddl=N'ALTER ROLE '+QUOTENAME(USER_NAME(role_principal_id))+N' DROP MEMBER '+QUOTENAME(USER_NAME(member_principal_id)) FROM sys.database_role_members WHERE member_principal_id>4;
  EXEC sys.sp_executesql @Ddl;
 END;
 DECLARE RoleOwners CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM sys.database_principals WHERE type='R' AND is_fixed_role=0 AND name<>N'public';
 OPEN RoleOwners; FETCH NEXT FROM RoleOwners INTO @Name;
 WHILE @@FETCH_STATUS=0
 BEGIN
  SET @Ddl=N'ALTER AUTHORIZATION ON ROLE::'+QUOTENAME(@Name)+N' TO dbo'; EXEC sys.sp_executesql @Ddl;
  FETCH NEXT FROM RoleOwners INTO @Name;
 END;
 CLOSE RoleOwners; DEALLOCATE RoleOwners;
 WHILE EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id>4 AND is_fixed_role=0 AND name<>N'public')
 BEGIN
  SELECT TOP(1) @Name=name,@Id=principal_id FROM sys.database_principals WHERE principal_id>4 AND is_fixed_role=0 AND name<>N'public';
  SELECT @Ddl=N'ALTER AUTHORIZATION ON SCHEMA::'+QUOTENAME(name)+N' TO dbo' FROM sys.schemas WHERE principal_id=@Id;
  IF EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id=@Id) EXEC sys.sp_executesql @Ddl;
  SET @Ddl=N'ALTER AUTHORIZATION ON ROLE::'+QUOTENAME(@Name)+N' TO dbo';
  IF EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id=@Id AND type='R') EXEC sys.sp_executesql @Ddl;
  SET @Ddl=N'DROP '+CASE WHEN EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id=@Id AND type='R') THEN N'ROLE ' ELSE N'USER ' END+QUOTENAME(@Name);
  EXEC sys.sp_executesql @Ddl;
 END;
 COMMIT;
 SELECT 'CLEAN_SLATE_RESET_COMMITTED' AS AcceptanceResult;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;

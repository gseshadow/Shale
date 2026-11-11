/* Full independent metadata, including databases inaccessible to business callers. */
-- SYSTEM_BASELINE
IF ISNULL(CONVERT(varchar(64),SERVERPROPERTY('ProductVersion')),'')<>@SystemProductVersion
 THROW 59620,'Unreviewed boxed system catalog version.',1;
DECLARE @RuntimeSids table(Sid varbinary(85) PRIMARY KEY);
INSERT @RuntimeSids SELECT DISTINCT p.sid FROM sys.database_role_members m JOIN sys.database_principals p ON p.principal_id=m.member_principal_id
WHERE m.role_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdOAuthBrowserV1'),DATABASE_PRINCIPAL_ID('AiCimdOAuthIssuerV1'),DATABASE_PRINCIPAL_ID('AiCimdOAuthResolverV1'),DATABASE_PRINCIPAL_ID('AiCimdMetadataPublisherV1'),DATABASE_PRINCIPAL_ID('AiCimdOAuthNeutralV1'),DATABASE_PRINCIPAL_ID('AiApplicationPublisher'),DATABASE_PRINCIPAL_ID('AiApplicationTenant'),DATABASE_PRINCIPAL_ID('AiCimdCatalogV2'));
IF EXISTS(SELECT 1 FROM @RuntimeSids WHERE Sid=@OwnerSid OR Sid=(SELECT sid FROM sys.server_principals WHERE principal_id=@SignerId))
 THROW 59620,'Execution identities overlap runtime identities.',1;
INSERT @RuntimeSids SELECT @OwnerSid UNION SELECT sid FROM sys.server_principals WHERE principal_id=@SignerId;
DECLARE @SystemName sysname,@SystemSql nvarchar(max);
CREATE TABLE #CimdCheckedSids(Sid varbinary(85) PRIMARY KEY);
INSERT #CimdCheckedSids SELECT Sid FROM @RuntimeSids;
CREATE TABLE #CimdSystemPermissions(DatabaseName sysname,Class int,MajorId int,MinorId int,Grantee sysname,Grantor sysname,PermissionName sysname,State char(1));
CREATE TABLE #CimdSystemObjects(DatabaseName sysname,ObjectId int,SchemaName sysname,Name sysname,Type char(2),OwnerId int,DefinitionHash varchar(64),ExecuteAs int,Ansi bit,Quoted bit);
CREATE TABLE #CimdSystemAuthority(DatabaseName sysname,Kind nvarchar(32),Name nvarchar(517),Related nvarchar(517),Type nvarchar(128),Value nvarchar(max),Flags nvarchar(128));
DECLARE SystemCatalogs CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM sys.databases WHERE name<>DB_NAME();
OPEN SystemCatalogs;FETCH NEXT FROM SystemCatalogs INTO @SystemName;
WHILE @@FETCH_STATUS=0 BEGIN
 SET @SystemSql=N'USE '+QUOTENAME(@SystemName)+N';
 IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),''DATABASE'',''VIEW DEFINITION''),0)<>1 THROW 59620,''Cross-database metadata visibility required.'',1;
 IF EXISTS(SELECT 1 FROM sys.database_principals p JOIN #CimdCheckedSids s ON s.Sid=p.sid) THROW 59620,''Cross-database principal mapping refused.'',1;
 IF DB_ID()<=4 BEGIN
  IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')) AND NOT(name=''guest'' AND principal_id=DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(''guest'') AND is_ms_shipped=0)
   OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.types WHERE is_user_defined=1 AND principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.certificates WHERE principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   OR EXISTS(SELECT 1 FROM sys.assemblies WHERE principal_id IN(DATABASE_PRINCIPAL_ID(''public''),DATABASE_PRINCIPAL_ID(''guest'')))
   THROW 59620,''System public or guest ownership/inheritance refused.'',1;
  INSERT #CimdSystemPermissions SELECT DB_NAME(),p.class,p.major_id,p.minor_id,USER_NAME(p.grantee_principal_id),USER_NAME(p.grantor_principal_id),p.permission_name,p.state FROM sys.database_permissions p;
  INSERT #CimdSystemObjects SELECT DB_NAME(),o.object_id,s.name,o.name,o.type,COALESCE(o.principal_id,s.principal_id),CONVERT(varchar(64),HASHBYTES(''SHA2_256'',m.definition),2),m.execute_as_principal_id,m.uses_ansi_nulls,m.uses_quoted_identifier FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.object_id>0 AND o.type NOT IN(''PK'',''UQ'') AND NOT(DB_NAME()=''tempdb'' AND o.name LIKE ''#%'' AND o.name NOT LIKE ''##%'');
  -- AUTHORITY_COLLECTION
 END;';
 EXEC sys.sp_executesql @SystemSql;
 FETCH NEXT FROM SystemCatalogs INTO @SystemName;
END;
CLOSE SystemCatalogs;DEALLOCATE SystemCatalogs;
IF EXISTS(SELECT * FROM #CimdSystemPermissions EXCEPT SELECT * FROM @ExpectedSystemPermissions)
 OR EXISTS(SELECT * FROM @ExpectedSystemPermissions EXCEPT SELECT * FROM #CimdSystemPermissions)
 OR EXISTS(SELECT * FROM #CimdSystemObjects EXCEPT SELECT * FROM @ExpectedSystemObjects)
 OR EXISTS(SELECT * FROM @ExpectedSystemObjects EXCEPT SELECT * FROM #CimdSystemObjects)
 OR EXISTS(SELECT * FROM #CimdSystemAuthority EXCEPT SELECT * FROM @ExpectedSystemAuthority)
 OR EXISTS(SELECT * FROM @ExpectedSystemAuthority EXCEPT SELECT * FROM #CimdSystemAuthority)
 THROW 59620,'System public/guest permission or shipped-object drift.',1;
DROP TABLE #CimdCheckedSids;DROP TABLE #CimdSystemPermissions;DROP TABLE #CimdSystemObjects;DROP TABLE #CimdSystemAuthority;

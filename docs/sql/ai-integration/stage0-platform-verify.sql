/* READ ONLY platform dispatcher. No historical migrations or application data read.
   Dynamic batches keep SQL Server/MI-only catalog names out of the Azure branch. */
SET NOCOUNT ON;
DECLARE @edition int=CONVERT(int,SERVERPROPERTY('EngineEdition'));
IF @edition=5 BEGIN
 EXEC sys.sp_executesql N'/* READ ONLY. Before migration or identity provisioning. Run using an already authorized
   operator connection to the target database, never the AI/verifier runtime credential.
   Requires existing CONTROL DATABASE for complete scoped-credential visibility.
   Does not return names, locations, credential identities, PHI, secrets or hashes. */
SET NOCOUNT ON;
SET LOCK_TIMEOUT 3000;
DECLARE @edition int=CONVERT(int,SERVERPROPERTY(''EngineEdition''));
IF @edition IS NULL OR @edition<>5 THROW 59100,''Azure SQL Database required; use the reviewed platform dispatcher.'',1;
IF @@TRANCOUNT<>0 OR SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 THROW 59100,''Fresh read-only inventory session required.'',1;
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),''DATABASE'',''CONTROL''),0)<>1
 THROW 59100,''Existing operator CONTROL DATABASE required for complete credential inventory; do not expand verifier privileges.'',1;
DECLARE @sources int=(SELECT COUNT(*) FROM sys.external_data_sources),
 @tables int=(SELECT COUNT(*) FROM sys.external_tables),
 @credentials int=(SELECT COUNT(*) FROM sys.database_scoped_credentials),
 @synonyms int=(SELECT COUNT(*) FROM sys.synonyms),
 @dependencies int=(SELECT COUNT(*) FROM sys.sql_expression_dependencies WHERE referenced_server_name IS NOT NULL OR referenced_database_name IS NOT NULL),
 @ownership int=(SELECT COUNT(*) FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on<>0 OR is_db_chaining_on<>0)),
 @public int;
SELECT @public=COUNT(*) FROM sys.database_permissions p
 LEFT JOIN sys.all_objects o ON p.class=1 AND o.object_id=p.major_id
 WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(''public'')
 AND (p.state=''D'' AND p.permission_name LIKE ''%DEFINITION%''
 OR p.state IN(''G'',''W'') AND (p.state=''W'' OR NOT(
 p.class=0 AND p.permission_name IN(''CONNECT'',''VIEW ANY COLUMN ENCRYPTION KEY DEFINITION'',''VIEW ANY COLUMN MASTER KEY DEFINITION'')
 OR p.class=1 AND p.permission_name IN(''SELECT'',''EXECUTE'',''VIEW DEFINITION'')
 AND OBJECT_SCHEMA_NAME(p.major_id) IN(''sys'',''INFORMATION_SCHEMA'')
 AND (COALESCE(o.is_ms_shipped,0)=1 OR p.major_id<0 AND p.permission_name=''SELECT'')
 OR p.class IN(1,3) AND p.permission_name=''VIEW DEFINITION'')));
SELECT @edition EngineEdition,''AZURE_SQL_DATABASE'' Platform,
 @sources ExternalDataSourceFindingCount,@tables ExternalTableFindingCount,
 @credentials ScopedCredentialFindingCount,@synonyms SynonymFindingCount,
 @dependencies CrossDatabaseDependencyFindingCount,@ownership OwnershipRouteFindingCount,
 @public PublicAuthorityFindingCount,
 CASE WHEN @sources+@tables+@credentials+@synonyms+@dependencies+@ownership+@public=0
 THEN ''PLATFORM_COMPATIBLE_PENDING_IDENTITY_AND_CATALOG_ATTESTATION'' ELSE ''STOP'' END PlatformStatus;
IF @sources+@tables+@credentials+@synonyms+@dependencies+@ownership+@public<>0
 THROW 59100,''Unsupported external mechanism or inherited authority; STOP for review.'',1;
';
 RETURN;
END;
IF @edition IN(2,3,4,8) BEGIN
 EXEC sys.sp_executesql N'
 IF COALESCE(HAS_PERMS_BY_NAME(NULL,NULL,''VIEW ANY DEFINITION''),0)<>1
  OR COALESCE(HAS_PERMS_BY_NAME(NULL,NULL,''VIEW ANY DATABASE''),0)<>1
  THROW 59100,''Complete server catalog visibility required.'',1;
 SELECT CASE WHEN CONVERT(int,SERVERPROPERTY(''EngineEdition''))=8 THEN ''MANAGED_INSTANCE'' ELSE ''SQL_SERVER'' END Platform,
 (SELECT COUNT(*) FROM sys.server_permissions) ServerPermissionInventoryCount,
 (SELECT COUNT(*) FROM sys.server_principals) ServerPrincipalInventoryCount,
 (SELECT COUNT(*) FROM sys.endpoints) EndpointInventoryCount,
 ''SERVER_CATALOG_AVAILABLE_PENDING_IDENTITY_AND_CATALOG_ATTESTATION'' PlatformStatus;';
 RETURN;
END;
THROW 59100,'Unknown or unsupported platform; STOP.',1;

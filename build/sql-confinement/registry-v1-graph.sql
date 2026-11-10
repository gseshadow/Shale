/* Read-only closed graph for the registry extension. Inputs are declared by the generated wrapper.
   No user/Case/credential data is returned. DDL belongs only to the separate dbo installer. */
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59310,'Complete registry catalog visibility required.',1;
IF (SELECT COUNT(*) FROM sys.schemas WHERE name IN(N'dbo',N'sec') AND principal_id=1)<>2
 OR EXISTS(SELECT 1 FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on=1 OR is_db_chaining_on=1))
 THROW 59310,'Registry schema ownership or database isolation drift.',1;
/* public is an implicit parent of every new identity; application grants here are bridges. */
IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 THROW 59310,'Registry public ownership/inheritance bridge.',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions p LEFT JOIN sys.all_objects o ON p.class=1 AND o.object_id=p.major_id
 WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND
 (p.state='D' AND p.permission_name LIKE '%DEFINITION%' OR p.state IN('G','W') AND
 (p.state='W' OR NOT(p.class=0 AND p.permission_name IN('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')
 OR p.class=1 AND p.permission_name IN('SELECT','EXECUTE','VIEW DEFINITION') AND OBJECT_SCHEMA_NAME(p.major_id) IN('sys','INFORMATION_SCHEMA') AND (COALESCE(o.is_ms_shipped,0)=1 OR p.major_id<0 AND p.permission_name='SELECT')
 OR p.class IN(1,3) AND p.permission_name='VIEW DEFINITION'))))
 THROW 59310,'Registry public application authority bridge.',1;
IF EXISTS(SELECT 1 FROM sys.triggers WHERE parent_class=0) THROW 59310,'Unreviewed database DDL trigger.',1;
DECLARE @RegistryDmlTables table(Name sysname PRIMARY KEY);
INSERT @RegistryDmlTables VALUES(N'AiExternalApplication'),(N'AiExternalApplicationRevision'),(N'AiTenantApplicationApproval'),
 (N'AiIntegrationRegistration'),(N'AiDelegationGrant'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiConsentIntent'),(N'EntityActionAuditLog'),(N'GlobalControlPlaneAuditLog');
/* Constraints/defaults/computed expressions and policy modules are also ownership-chain edges.
   Application checks may use built-ins and their own columns, never an unreviewed user module. */
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d JOIN sys.objects o ON o.object_id=d.referencing_id
 WHERE (o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @RegistryDmlTables)
 OR o.type IN('C','D') AND OBJECT_SCHEMA_NAME(o.parent_object_id)=N'dbo' AND OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @RegistryDmlTables))
 AND (d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR d.is_caller_dependent=1 OR d.is_ambiguous=1
 OR EXISTS(SELECT 1 FROM sys.objects f WHERE f.object_id=d.referenced_id AND f.is_ms_shipped=0 AND f.type IN('FN','IF','TF','FS','FT','P','PC','SN'))))
 THROW 59310,'Unreviewed registry table-expression dependency.',1;
IF EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE OBJECT_SCHEMA_NAME(p.target_object_id)=N'dbo' AND OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @RegistryDmlTables)
 AND (OBJECT_NAME(p.target_object_id) IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'GlobalControlPlaneAuditLog')
 OR s.object_id<>OBJECT_ID(N'sec.TenantFilter') OR s.is_enabled<>1 OR s.is_schema_bound<>1
 OR COALESCE(s.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=s.schema_id))<>1
 OR p.predicate_definition COLLATE Latin1_General_100_BIN2<>N'([sec].[fn_FilterByTenant]([ShaleClientId]))'))
 THROW 59310,'Unreviewed registry reachable policy dependency.',1;
DECLARE @RegistryTriggers table(ParentName sysname,Name sysname PRIMARY KEY);
INSERT @RegistryTriggers VALUES
 (N'AiIntegrationRegistration',N'AiImmutable_AiIntegrationRegistration'),(N'AiIntegrationRegistration',N'AiCascade_Registration'),
 (N'AiDelegationGrant',N'AiImmutable_AiDelegationGrant'),(N'AiDelegationGrant',N'AiCascade_Grant'),
 (N'AiDelegatedSession',N'AiImmutable_AiDelegatedSession'),(N'AiDelegatedSession',N'AiLifecycle_Session'),
 (N'AiOAuthCredential',N'AiImmutable_AiOAuthCredential'),(N'AiOAuthCredential',N'AiLifecycle_Credential'),
 (N'AiConsentIntent',N'AiImmutable_AiConsentIntent');
IF @RegistryBefore=0 INSERT @RegistryTriggers VALUES
 (N'AiExternalApplication',N'AiImmutable_ExternalApplication'),(N'AiExternalApplicationRevision',N'AiImmutable_ApplicationRevision'),(N'AiTenantApplicationApproval',N'AiImmutable_ApplicationApproval');
IF EXISTS(SELECT 1 FROM sys.triggers t WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables)
 AND NOT EXISTS(SELECT 1 FROM @RegistryTriggers a WHERE a.Name=t.name AND a.ParentName=OBJECT_NAME(t.parent_id)))
 OR EXISTS(SELECT 1 FROM @RegistryTriggers a WHERE NOT EXISTS(SELECT 1 FROM sys.triggers t WHERE t.name=a.Name AND t.parent_id=OBJECT_ID(N'dbo.'+a.ParentName) AND t.is_disabled=0 AND t.is_instead_of_trigger=0))
 THROW 59310,'Registry DML/terminal-audit trigger graph drift.',1;
IF EXISTS(SELECT 1 FROM sys.trigger_events e JOIN sys.triggers t ON t.object_id=e.object_id WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables) AND (e.is_first=1 OR e.is_last=1))
 THROW 59310,'Unreviewed registry trigger order.',1;
/* The generated source inserts exact approved module hashes, including two reviewed LF/CRLF forms. */
-- REGISTRY_EXPECTED_MODULES
IF EXISTS(SELECT 1 FROM @RegistryModules a WHERE (@RegistryBefore=0 OR a.IsNew=0) AND NOT EXISTS(
 SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE s.name=N'dbo' AND o.name=a.Name AND COALESCE(o.principal_id,s.principal_id)=1 AND m.execute_as_principal_id IS NULL
 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND HASHBYTES('SHA2_256',m.definition) IN(a.HashLF,a.HashCRLF)))
 OR EXISTS(SELECT 1 FROM sys.crypt_properties c WHERE c.major_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules WHERE @RegistryBefore=0 OR IsNew=0))
 THROW 59310,'Registry module source/settings/owner/signature drift.',1;
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d WHERE d.referencing_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules WHERE @RegistryBefore=0 OR IsNew=0)
 AND (d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR d.is_caller_dependent=1 OR d.is_ambiguous=1
 OR d.referenced_id IS NULL AND NOT(ISNULL(d.referenced_schema_name,N'')=N'sys' AND d.referenced_entity_name IN(N'sp_getapplock',N'sp_set_session_context')
 OR d.referenced_schema_name IS NULL AND d.referenced_entity_name IN(N'inserted',N'deleted'))))
 THROW 59310,'Registry unresolved/cross-database dependency.',1;
IF @RegistryBefore=1 BEGIN
 IF EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'AiTenantApplicationApproval',N'AiPublishApplicationV1',N'AiRequestApplicationV1',N'AiDecideApplicationV1'))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant'))
 THROW 59310,'Registry preparation requires an uninstalled extension.',1;
END ELSE BEGIN
 IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant') AND type='R' AND owning_principal_id=1)<>2
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')))
 THROW 59310,'Registry role ownership/inheritance drift.',1;
 DECLARE @RegistryMembers table(Id int PRIMARY KEY,RoleId int,Sid varbinary(85),AuthenticationType int);
 INSERT @RegistryMembers SELECT p.principal_id,r.role_principal_id,p.sid,p.authentication_type
 FROM sys.database_role_members r JOIN sys.database_principals p ON p.principal_id=r.member_principal_id
 WHERE r.role_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'));
 IF EXISTS(SELECT 1 FROM @RegistryMembers a JOIN sys.database_principals p ON p.principal_id=a.Id WHERE p.type<>'S' OR p.principal_id<=4)
 OR EXISTS(SELECT 1 FROM sys.database_role_members r JOIN @RegistryMembers a ON a.Id=r.member_principal_id WHERE r.role_principal_id<>a.RoleId)
 OR EXISTS(SELECT 1 FROM sys.database_permissions p JOIN @RegistryMembers a ON p.grantee_principal_id=a.Id WHERE NOT(p.class=0 AND p.permission_name='CONNECT' AND p.state='G'))
 THROW 59310,'Registry member token/direct authority drift.',1;
 DECLARE @RegistryTokens table(Id int PRIMARY KEY);
 INSERT @RegistryTokens SELECT Id FROM @RegistryMembers;
 INSERT @RegistryTokens VALUES(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher')),(DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'));
 IF EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.types WHERE is_user_defined=1 AND principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.certificates WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.assemblies WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 THROW 59310,'Registry securable ownership bridge.',1;
 /* Exact permissions: one/two EXECUTEs and six denials on each of three tables, no grant option. */
 IF EXISTS(SELECT 1 FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')) AND NOT(
 p.class=1 AND p.minor_id=0 AND p.grantor_principal_id=1 AND
 (p.state='G' AND p.permission_name='EXECUTE' AND OBJECT_SCHEMA_NAME(p.major_id)=N'dbo' AND
 (p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher') AND OBJECT_NAME(p.major_id)=N'AiPublishApplicationV1'
 OR p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationTenant') AND OBJECT_NAME(p.major_id) IN(N'AiRequestApplicationV1',N'AiDecideApplicationV1'))
 OR p.state='D' AND p.permission_name IN('SELECT','INSERT','UPDATE','DELETE','ALTER','CONTROL') AND OBJECT_SCHEMA_NAME(p.major_id)=N'dbo'
 AND OBJECT_NAME(p.major_id) IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'AiTenantApplicationApproval'))))
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'))<>19
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'))<>20
 THROW 59310,'Registry EXECUTE/table/column/schema permission drift.',1;
 IF (SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.AiTenantApplicationApproval') AND s.object_id=OBJECT_ID(N'sec.TenantFilter') AND s.is_enabled=1 AND s.is_schema_bound=1
 AND COALESCE(s.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=s.schema_id))=1
 AND p.predicate_definition COLLATE Latin1_General_100_BIN2=N'([sec].[fn_FilterByTenant]([ShaleClientId]))'
 AND (p.predicate_type_desc='FILTER' OR p.predicate_type_desc='BLOCK' AND p.operation_desc IN('AFTER INSERT','AFTER UPDATE')))<>3
 OR (SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.AiTenantApplicationApproval'))<>3
 THROW 59310,'Registry strict RLS policy identity drift.',1;
 DECLARE @RegistryEdition int=CONVERT(int,SERVERPROPERTY('EngineEdition'));
 IF @RegistryEdition NOT IN(2,3,4,5,8) OR @RegistryEdition IS NULL THROW 59310,'Unknown registry platform.',1;
 IF @RegistryEdition=5 BEGIN
  IF EXISTS(SELECT 1 FROM @RegistryMembers WHERE AuthenticationType<>2) THROW 59310,'Contained registry SQL users required.',1;
 END ELSE BEGIN
  IF EXISTS(SELECT 1 FROM @RegistryMembers WHERE AuthenticationType<>1) THROW 59310,'Mapped registry SQL users required.',1;
  IF ISNULL(HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION'),0)<>1 OR ISNULL(HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DATABASE'),0)<>1
  THROW 59310,'Registry server catalog visibility required.',1;
  /* This branch is never issued on Azure SQL Database. */
  DECLARE @RegistryServerCheck nvarchar(max)=N'
  IF EXISTS(SELECT 1 FROM sys.database_role_members r JOIN sys.database_principals u ON u.principal_id=r.member_principal_id LEFT JOIN sys.server_principals s ON s.sid=u.sid
   WHERE r.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')) AND (s.type<>''S'' OR s.is_disabled=1 OR s.principal_id IS NULL))
  OR EXISTS(SELECT 1 FROM sys.server_role_members r JOIN sys.server_principals s ON s.principal_id=r.member_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id
   WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')))
  OR EXISTS(SELECT 1 FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id
   WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')) AND p.state IN(''G'',''W'') AND NOT(p.class=100 AND p.state=''G'' AND p.permission_name IN(''CONNECT SQL'',''VIEW ANY DATABASE'')))
  THROW 59310,''Registry SQL login server authority drift.'',1;';
  EXEC(@RegistryServerCheck);
 END;
END;

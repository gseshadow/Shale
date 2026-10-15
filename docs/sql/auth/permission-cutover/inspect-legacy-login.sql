:ON ERROR EXIT
/* Read-only dbo evidence for the three operator-identified modules. Run from repo root.
   No application statements, procedure calls, parameter default values, SIDs, keys or credentials.
   Definitions are private security evidence: review embedded literals before sharing.
   Serialize DDL/ACL changes during collection; this is not an installation approval. */
SET NOCOUNT ON;
IF @@TRANCOUNT<>0 OR USER_NAME()<>N'dbo' OR DB_NAME()<>N'$(ExpectedDatabase)'
 THROW 58300,N'Expected database, dbo metadata authority and no ambient transaction required.',1;

SELECT n.ModuleName,o.object_id,o.type_desc,o.principal_id,s.principal_id AS SchemaOwnerId,
 m.execute_as_principal_id,m.definition,m.uses_ansi_nulls,m.uses_quoted_identifier,
 m.is_schema_bound,m.uses_database_collation,m.is_recompiled,m.null_on_null_input
 INTO #LegacyLoginModules
 FROM (VALUES(N'sp_login_get_hash'),(N'sp_login_mark_failure'),(N'sp_login_mark_success')) n(ModuleName)
 LEFT JOIN sys.schemas s ON s.name=N'dbo'
 LEFT JOIN sys.objects o ON o.schema_id=s.schema_id AND o.name=n.ModuleName
 LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id;

-- Include direct inbound/outbound dependencies and their ACLs, never their application data.
SELECT DISTINCT d.* INTO #LegacyLoginDependencies FROM sys.sql_expression_dependencies d
 WHERE d.referencing_id IN (SELECT object_id FROM #LegacyLoginModules)
 OR d.referenced_id IN (SELECT object_id FROM #LegacyLoginModules);
SELECT object_id INTO #LegacyLoginObjects FROM #LegacyLoginModules WHERE object_id IS NOT NULL
 UNION SELECT referencing_id FROM #LegacyLoginDependencies
 UNION SELECT referenced_id FROM #LegacyLoginDependencies WHERE referenced_id IS NOT NULL;
SELECT DISTINCT schema_id INTO #LegacyLoginSchemas FROM sys.objects
 WHERE object_id IN (SELECT object_id FROM #LegacyLoginObjects);
INSERT #LegacyLoginSchemas SELECT SCHEMA_ID(N'dbo') WHERE NOT EXISTS
 (SELECT 1 FROM #LegacyLoginSchemas WHERE schema_id=SCHEMA_ID(N'dbo'));

-- Bound every result set before emitting definitions. Over-limit evidence stops, never silently truncates.
IF EXISTS(SELECT 1 FROM #LegacyLoginModules WHERE DATALENGTH(definition)>1048576)
 OR (SELECT COUNT_BIG(*) FROM #LegacyLoginDependencies)>200
 OR (SELECT COUNT_BIG(*) FROM #LegacyLoginObjects)>200
 OR (SELECT COUNT_BIG(*) FROM sys.database_principals)>1000
 OR (SELECT COUNT_BIG(*) FROM sys.database_role_members)>1000
 OR (SELECT COUNT_BIG(*) FROM sys.parameters WHERE object_id IN
     (SELECT object_id FROM #LegacyLoginModules))>6303
 OR (SELECT COUNT_BIG(*) FROM sys.crypt_properties WHERE class=1 AND major_id IN
     (SELECT object_id FROM #LegacyLoginModules))>200
 OR (SELECT COUNT_BIG(*) FROM sys.triggers WHERE parent_id IN
     (SELECT object_id FROM #LegacyLoginObjects))>200
 THROW 58330,N'Legacy metadata exceeds review bounds; stop for a narrower approved query.',1;

SELECT principal_id INTO #LegacyLoginAuthority FROM sys.database_principals WHERE name IN
 (N'public',N'shale_app',N'shale_runtime',N'shale_runtime_role')
 UNION SELECT COALESCE(principal_id,SchemaOwnerId) FROM #LegacyLoginModules
 UNION SELECT execute_as_principal_id FROM #LegacyLoginModules WHERE execute_as_principal_id>0
 UNION SELECT dp.principal_id FROM sys.crypt_properties cp
 LEFT JOIN sys.certificates c ON c.thumbprint=cp.thumbprint
 LEFT JOIN sys.asymmetric_keys a ON a.thumbprint=cp.thumbprint
 JOIN sys.database_principals dp ON (dp.type='C' AND dp.sid=c.sid) OR (dp.type='K' AND dp.sid=a.sid)
 WHERE cp.class=1 AND cp.major_id IN (SELECT object_id FROM #LegacyLoginModules);
-- Include nested role authority; fixed roles/owners confer authority beyond explicit ACL rows.
WHILE EXISTS(SELECT 1 FROM sys.database_role_members r WHERE r.member_principal_id IN
 (SELECT principal_id FROM #LegacyLoginAuthority) AND r.role_principal_id NOT IN
 (SELECT principal_id FROM #LegacyLoginAuthority WHERE principal_id IS NOT NULL))
 INSERT #LegacyLoginAuthority SELECT DISTINCT r.role_principal_id FROM sys.database_role_members r
 WHERE r.member_principal_id IN (SELECT principal_id FROM #LegacyLoginAuthority)
 AND r.role_principal_id NOT IN (SELECT principal_id FROM #LegacyLoginAuthority WHERE principal_id IS NOT NULL);

-- Database/schema/object authority, including columns, original grantors and G/W/D/R states.
-- USER/ROLE/type authority is included so execution identities and dependency parameters can be reviewed.
SELECT p.* INTO #LegacyLoginPermissions FROM sys.database_permissions p
 WHERE p.class IN (0,4,6)
 OR p.grantee_principal_id IN (SELECT principal_id FROM #LegacyLoginAuthority)
 OR (p.class=3 AND p.major_id IN (SELECT schema_id FROM #LegacyLoginSchemas))
 OR (p.class=1 AND p.major_id IN (SELECT object_id FROM #LegacyLoginObjects));
IF (SELECT COUNT_BIG(*) FROM #LegacyLoginPermissions)>5000
 THROW 58330,N'Legacy permission evidence exceeds review bounds; stop for a narrower approved query.',1;

SELECT N'LegacyLoginCollection' AS EvidenceKind,DB_NAME() AS DatabaseName,USER_NAME() AS Collector,
 SYSUTCDATETIME() AS CollectedAtUtc,CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')) AS DatabaseCollation,
 N'PRIVATE_METADATA_ONLY; NOT DEPLOYMENT APPROVAL' AS EvidenceStatus;
SELECT N'LegacyLoginModule' AS EvidenceKind,ModuleName,object_id,type_desc,
 CASE WHEN object_id IS NULL THEN N'MISSING' WHEN definition IS NULL THEN N'UNAVAILABLE' ELSE N'AVAILABLE' END AS DefinitionStatus,
 principal_id AS ExplicitOwnerId,USER_NAME(principal_id) AS ExplicitOwner,
 SchemaOwnerId,USER_NAME(SchemaOwnerId) AS SchemaOwner,
 USER_NAME(COALESCE(principal_id,SchemaOwnerId)) AS EffectiveOwner,
 execute_as_principal_id,CASE WHEN execute_as_principal_id IS NULL THEN N'CALLER'
 WHEN execute_as_principal_id=-2 THEN N'OWNER' ELSE USER_NAME(execute_as_principal_id) END AS ExecutionContext,
 uses_ansi_nulls,uses_quoted_identifier,is_schema_bound,uses_database_collation,is_recompiled,null_on_null_input,
 DATALENGTH(definition) AS DefinitionBytes,
 CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) AS DefinitionHash,definition
 FROM #LegacyLoginModules ORDER BY ModuleName;
SELECT N'LegacyLoginParameter' AS EvidenceKind,OBJECT_NAME(p.object_id) AS ModuleName,
 p.parameter_id,p.name,SCHEMA_NAME(t.schema_id) AS TypeSchema,t.name AS TypeName,
 p.system_type_id,p.user_type_id,p.max_length,p.precision,p.scale,p.is_output,p.is_cursor_ref,
 p.has_default_value,p.is_xml_document,p.xml_collection_id,p.is_readonly
 FROM sys.parameters p JOIN sys.types t ON t.user_type_id=p.user_type_id
 WHERE p.object_id IN (SELECT object_id FROM #LegacyLoginModules) ORDER BY p.object_id,p.parameter_id;
SELECT N'LegacyLoginSignature' AS EvidenceKind,OBJECT_NAME(cp.major_id) AS ModuleName,
 cp.crypt_type_desc,CONVERT(varchar(130),cp.thumbprint,1) AS SignerThumbprint,
 c.name AS CertificateName,c.pvt_key_encryption_type_desc AS CertificatePrivateKeyState,
 a.name AS AsymmetricKeyName,dp.principal_id AS SignerPrincipalId,dp.name AS SignerPrincipal
 FROM sys.crypt_properties cp
 LEFT JOIN sys.certificates c ON c.thumbprint=cp.thumbprint
 LEFT JOIN sys.asymmetric_keys a ON a.thumbprint=cp.thumbprint
 LEFT JOIN sys.database_principals dp ON (dp.type='C' AND dp.sid=c.sid) OR (dp.type='K' AND dp.sid=a.sid)
 WHERE cp.class=1 AND cp.major_id IN (SELECT object_id FROM #LegacyLoginModules)
 ORDER BY cp.major_id,cp.crypt_type;
SELECT N'LegacyLoginDependency' AS EvidenceKind,d.* FROM #LegacyLoginDependencies d
 ORDER BY referencing_id,referenced_id,referenced_minor_id;
SELECT N'LegacyLoginRelatedObject' AS EvidenceKind,o.object_id,SCHEMA_NAME(o.schema_id) AS SchemaName,
 o.name,o.type_desc,o.principal_id AS ExplicitOwnerId,s.principal_id AS SchemaOwnerId,
 USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS EffectiveOwner,
 tr.parent_id AS TriggerParentId,tr.is_disabled AS TriggerDisabled
 FROM #LegacyLoginObjects x JOIN sys.objects o ON o.object_id=x.object_id
 JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.triggers tr ON tr.object_id=o.object_id
 ORDER BY o.object_id;
-- Triggers are additional behavior requiring review; no trigger or dependency body is assumed safe.
SELECT N'LegacyLoginTrigger' AS EvidenceKind,tr.object_id,tr.name,tr.parent_id,tr.is_disabled,
 tr.is_instead_of_trigger,m.execute_as_principal_id
 FROM sys.triggers tr LEFT JOIN sys.sql_modules m ON m.object_id=tr.object_id
 WHERE tr.parent_id IN (SELECT object_id FROM #LegacyLoginObjects) ORDER BY tr.object_id;
SELECT N'LegacyLoginPermission' AS EvidenceKind,p.class,p.class_desc,p.major_id,p.minor_id,
 CASE p.class WHEN 0 THEN QUOTENAME(DB_NAME()) WHEN 3 THEN QUOTENAME(SCHEMA_NAME(p.major_id))
 WHEN 4 THEN QUOTENAME(USER_NAME(p.major_id)) WHEN 6 THEN QUOTENAME(TYPE_NAME(p.major_id))
 WHEN 1 THEN QUOTENAME(OBJECT_SCHEMA_NAME(p.major_id))+N'.'+QUOTENAME(OBJECT_NAME(p.major_id)) END AS Securable,
 CASE WHEN p.class=1 AND p.minor_id<>0 THEN COL_NAME(p.major_id,p.minor_id) END AS ColumnName,
 p.type,p.permission_name,p.state,p.state_desc,p.grantee_principal_id,USER_NAME(p.grantee_principal_id) AS Grantee,
 p.grantor_principal_id,USER_NAME(p.grantor_principal_id) AS Grantor
 FROM #LegacyLoginPermissions p ORDER BY class,major_id,minor_id,grantee_principal_id,grantor_principal_id,type;
SELECT N'LegacyLoginPrincipal' AS EvidenceKind,principal_id,name,type_desc,owning_principal_id,is_fixed_role
 FROM sys.database_principals ORDER BY principal_id;
SELECT N'LegacyLoginMembership' AS EvidenceKind,role_principal_id,USER_NAME(role_principal_id) AS RoleName,
 member_principal_id,USER_NAME(member_principal_id) AS MemberName
 FROM sys.database_role_members ORDER BY role_principal_id,member_principal_id;

IF EXISTS(SELECT 1 FROM #LegacyLoginModules WHERE object_id IS NULL OR definition IS NULL OR type_desc<>N'SQL_STORED_PROCEDURE')
 THROW 58331,N'Legacy module missing, encrypted or unsupported; evidence incomplete; installation remains blocked.',1;
DROP TABLE #LegacyLoginPermissions;
DROP TABLE #LegacyLoginAuthority;
DROP TABLE #LegacyLoginSchemas;
DROP TABLE #LegacyLoginObjects;
DROP TABLE #LegacyLoginDependencies;
DROP TABLE #LegacyLoginModules;
GO

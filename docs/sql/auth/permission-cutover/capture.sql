/* Private change-control evidence, no application rows. Preserve full precision and every row.
   Exact snapshots include G/W/D/R states, original grantors, column IDs, SID and nested memberships.
   New module/role names MUST be absent before their own stage. No existing ACL is rewritten. */
SELECT * INTO #PriorPermissions FROM sys.database_permissions;
SELECT * INTO #PriorModules FROM sys.sql_modules WHERE object_id IN
 (OBJECT_ID(N'dbo.ResolveDesktopRememberCredential'),OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'));
SELECT object_id,principal_id,schema_id INTO #PriorOwners FROM sys.objects WHERE object_id IN
 (OBJECT_ID(N'dbo.ResolveDesktopRememberCredential'),OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'));
SELECT * INTO #PriorMemberships FROM sys.database_role_members;
SELECT object_id,security_predicate_id,target_object_id,predicate_definition,predicate_type,operation
 INTO #PriorPredicates FROM sys.security_predicates;
SELECT N'PriorPermission' AS EvidenceKind,p.*,USER_NAME(grantee_principal_id) AS Grantee,
 USER_NAME(grantor_principal_id) AS Grantor FROM #PriorPermissions p
 ORDER BY class,major_id,minor_id,grantee_principal_id,grantor_principal_id,type;
SELECT N'PriorMembership' AS EvidenceKind,* FROM #PriorMemberships ORDER BY role_principal_id,member_principal_id;
SELECT N'PriorPrincipal' AS EvidenceKind,principal_id,name,type,CONVERT(varchar(172),sid,1) AS PrincipalSid,
 owning_principal_id,is_fixed_role FROM sys.database_principals ORDER BY principal_id;
SELECT N'PriorPredicate' AS EvidenceKind,* FROM #PriorPredicates ORDER BY object_id,security_predicate_id;
SELECT N'PriorModule' AS EvidenceKind,o.object_id,o.name,o.principal_id,s.principal_id AS SchemaOwner,
 m.execute_as_principal_id,m.definition,CONVERT(varchar(66),HASHBYTES('SHA2_256',m.definition),1) AS DefinitionHash
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id IN (OBJECT_ID(N'dbo.ResolveDesktopRememberCredential'),
 OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'));
GO

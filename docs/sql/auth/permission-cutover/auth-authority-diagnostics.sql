/* Bounded metadata-only explanation of every 58307 condition. Run only after dbo snapshots.
   Raw NULL is UNKNOWN, never GRANTED or evidence of no authority. No application SQL is executed. */
CREATE TABLE #AuthAuthorityChecks(ConditionName nvarchar(64),Securable nvarchar(776),
 PermissionName nvarchar(128),EffectiveValue int NULL);
EXECUTE AS USER=N'shale_app';
BEGIN TRY
 INSERT #AuthAuthorityChecks VALUES
 (N'role',N'db_owner',N'MEMBER',IS_MEMBER(N'db_owner')),
 (N'role',N'ShaleAdmins',N'MEMBER',CASE WHEN EXISTS(SELECT 1 FROM #CutoverPrincipals WHERE name=N'ShaleAdmins') THEN IS_MEMBER(N'ShaleAdmins') ELSE 0 END);
 INSERT #AuthAuthorityChecks
 SELECT N'database',DB_NAME(),v.PermissionName,HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE',v.PermissionName)
 FROM (VALUES(N'ALTER ANY USER'),(N'ALTER ANY SCHEMA'),(N'ALTER ANY SECURITY POLICY'),
 (N'CONTROL'),(N'ALTER ANY ROLE'),(N'EXECUTE')) v(PermissionName);
 INSERT #AuthAuthorityChecks VALUES
 (N'dbo-schema',N'dbo',N'ALTER',HAS_PERMS_BY_NAME(N'dbo','SCHEMA','ALTER')),
 (N'dbo-schema',N'dbo',N'EXECUTE',HAS_PERMS_BY_NAME(N'dbo','SCHEMA','EXECUTE')),
 (N'dbo-user',N'dbo',N'IMPERSONATE',HAS_PERMS_BY_NAME(N'dbo','USER','IMPERSONATE'));
 INSERT #AuthAuthorityChecks
 SELECT N'fixed-role',p.name,N'MEMBER',1 FROM sys.user_token t
 JOIN #CutoverPrincipals p ON p.principal_id=t.principal_id WHERE p.is_fixed_role=1 AND p.name<>N'public';
 INSERT #AuthAuthorityChecks
 SELECT N'impersonate-user',p.name,N'IMPERSONATE',HAS_PERMS_BY_NAME(p.name,'USER','IMPERSONATE')
 FROM #CutoverPrincipals p WHERE p.name<>USER_NAME() AND p.type IN ('S','E','X');
 INSERT #AuthAuthorityChecks
 SELECT N'ownership',o.Securable,N'OWNER',1 FROM #CutoverOwnership o
 WHERE o.principal_id IN (SELECT principal_id FROM sys.user_token);
 INSERT #AuthAuthorityChecks
 SELECT N'database-permission',DB_NAME(),permission_name,1 FROM sys.fn_my_permissions(NULL,'DATABASE')
 WHERE permission_name NOT IN (N'CONNECT',N'VIEW ANY COLUMN ENCRYPTION KEY DEFINITION',N'VIEW ANY COLUMN MASTER KEY DEFINITION');
 INSERT #AuthAuthorityChecks
 SELECT N'grant-option',p.Securable,p.permission_name,1 FROM #CutoverGrantOptions p
 WHERE p.grantee_principal_id IN (SELECT principal_id FROM sys.user_token);
 INSERT #AuthAuthorityChecks
 SELECT N'column',c.Securable+N'.'+QUOTENAME(c.ColumnName),v.PermissionName,
 HAS_PERMS_BY_NAME(c.Securable,'OBJECT',v.PermissionName,c.ColumnName,'COLUMN')
 FROM #CutoverColumns c CROSS JOIN (VALUES(N'UPDATE'),(N'SELECT')) v(PermissionName)
 WHERE v.PermissionName=N'UPDATE' OR c.Securable<>N'[dbo].[Users]';
 INSERT #AuthAuthorityChecks
 SELECT N'object',o.Securable,v.PermissionName,HAS_PERMS_BY_NAME(o.Securable,'OBJECT',v.PermissionName)
 FROM #CutoverObjects o CROSS JOIN
 (VALUES(N'ALTER'),(N'CONTROL'),(N'INSERT'),(N'UPDATE'),(N'DELETE'),(N'SELECT'),(N'EXECUTE')) v(PermissionName)
 WHERE (v.PermissionName<>N'SELECT' OR o.Securable<>N'[dbo].[Users]')
 AND (v.PermissionName<>N'EXECUTE' OR o.Securable NOT IN
 (N'[dbo].[ResolveDesktopRememberCredential]',N'[dbo].[LookupAuthenticationCredentialV1]',N'[dbo].[ResolveDesktopRememberCredentialV2]'));
 -- Metadata visibility is evidence context, not an additional required capability.
 SELECT N'AuthMetadataVisibility' AS EvidenceKind,
 HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION') AS EffectiveViewDefinition,
 CASE HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')
 WHEN 1 THEN N'GRANTED' WHEN 0 THEN N'NOT_GRANTED' ELSE N'UNKNOWN' END AS Outcome;
 SELECT TOP (200) N'AuthMetadataAcl' AS EvidenceKind,p.name AS PrincipalName,v.permission_name,v.state_desc
 FROM #CutoverVisibility v JOIN #CutoverPrincipals p ON p.principal_id=v.grantee_principal_id
 WHERE v.grantee_principal_id IN (SELECT principal_id FROM sys.user_token) ORDER BY p.name;
 SELECT COUNT_BIG(*) AS TokenCount,CASE WHEN COUNT_BIG(*)>200 THEN 1 ELSE 0 END AS TokenDetailsTruncated FROM sys.user_token;
 -- Explain the effective token, including public and nested roles, without exposing login SIDs.
 SELECT TOP (200) N'AuthToken' AS EvidenceKind,p.name AS PrincipalName,t.usage AS TokenUsage
 FROM sys.user_token t JOIN #CutoverPrincipals p ON p.principal_id=t.principal_id ORDER BY p.name;
 REVERT;
END TRY BEGIN CATCH
 REVERT; THROW;
END CATCH;
SELECT CASE WHEN EffectiveValue IS NULL THEN N'UNKNOWN' WHEN EffectiveValue=1 THEN N'GRANTED'
 ELSE N'NOT_GRANTED' END AS Outcome,COUNT_BIG(*) AS CheckCount
 FROM #AuthAuthorityChecks GROUP BY EffectiveValue;
SELECT ConditionName,COUNT_BIG(*) AS FindingCount FROM #AuthAuthorityChecks
 WHERE EffectiveValue=1 OR EffectiveValue IS NULL GROUP BY ConditionName ORDER BY ConditionName;
SELECT TOP (200) ConditionName,Securable,PermissionName,EffectiveValue,
 CASE WHEN EffectiveValue IS NULL THEN N'UNKNOWN' ELSE N'GRANTED' END AS Outcome
 FROM #AuthAuthorityChecks WHERE EffectiveValue=1 OR EffectiveValue IS NULL
 ORDER BY CASE WHEN EffectiveValue=1 THEN 0 ELSE 1 END,ConditionName,Securable,PermissionName;
SELECT COUNT_BIG(*) AS DetailFindingCount,CASE WHEN COUNT_BIG(*)>200 THEN 1 ELSE 0 END AS DetailsTruncated
 FROM #AuthAuthorityChecks WHERE EffectiveValue=1 OR EffectiveValue IS NULL;
GO

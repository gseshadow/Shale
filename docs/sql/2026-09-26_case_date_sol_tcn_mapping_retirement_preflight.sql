/*
 READ ONLY. Run unchanged with the approved all-tenant administrative connection and NULL
 application session context. This script does not authorize the forward migration by itself.
*/
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57300,'Preflight requires NULL application session context.',1;
IF OBJECT_ID(N'dbo.CaseDateSemanticRoles',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings',N'U') IS NULL
 THROW 57301,'Case Date semantic-role foundations are missing.',1;

SELECT ORIGINAL_LOGIN() OriginalLogin,SUSER_SNAME() ExecutionLogin,USER_NAME() DatabaseUser,
 SESSION_CONTEXT(N'ShaleClientId') ShaleClientIdContext,SESSION_CONTEXT(N'PrincipalUserId') PrincipalUserIdContext;

SELECT r.RoleKey,r.IsProtected,m.Id MappingId,m.ShaleClientId,m.CaseDateTypeId,
 t.ShaleClientId TypeOwnerTenantId,t.SystemKey,t.Name TypeName,t.IsActive TypeIsActive,t.IsDeleted TypeIsDeleted,
 m.IsActive MappingIsActive,m.IsDeleted MappingIsDeleted,m.CreatedAt,m.CreatedByUserId,m.UpdatedAt,m.UpdatedByUserId,
 m.DeletedAt,m.DeletedByUserId,m.RowVer
FROM dbo.CaseDateSemanticRoles r LEFT JOIN dbo.CaseDateTypeSemanticRoleMappings m ON m.SemanticRoleKey=r.RoleKey
LEFT JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId
WHERE r.RoleKey IN('INTAKE','STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE')
ORDER BY r.RoleKey,m.ShaleClientId,m.Id;

/* Exact effective Intake winner and cardinalities for every tenant. */
SELECT sc.Id TenantId,tc.TenantCount,gc.GlobalCount,w.Id EffectiveMappingId,w.CaseDateTypeId,
 t.SystemKey,t.ShaleClientId TypeOwnerTenantId,t.IsActive TypeIsActive,t.IsDeleted TypeIsDeleted,
 CASE WHEN tc.TenantCount>1 OR (tc.TenantCount=0 AND gc.GlobalCount<>1) THEN N'BLOCKING_MAPPING_CARDINALITY'
      WHEN w.Id IS NULL THEN N'BLOCKING_NO_EFFECTIVE_INTAKE'
      WHEN t.Id IS NULL OR t.IsActive=0 OR t.IsDeleted=1 THEN N'BLOCKING_INELIGIBLE_INTAKE_TYPE'
      ELSE N'READY' END Readiness
FROM dbo.ShaleClients sc
OUTER APPLY(SELECT COUNT_BIG(*) TenantCount FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.ShaleClientId=sc.Id AND m.SemanticRoleKey='INTAKE' AND m.IsActive=1 AND m.IsDeleted=0)tc
OUTER APPLY(SELECT COUNT_BIG(*) GlobalCount FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.ShaleClientId IS NULL AND m.SemanticRoleKey='INTAKE' AND m.IsActive=1 AND m.IsDeleted=0)gc
OUTER APPLY(SELECT TOP(1)m.* FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE (m.ShaleClientId=sc.Id OR m.ShaleClientId IS NULL) AND m.SemanticRoleKey='INTAKE' AND m.IsActive=1 AND m.IsDeleted=0 ORDER BY CASE WHEN m.ShaleClientId=sc.Id THEN 0 ELSE 1 END,m.Id DESC)w
LEFT JOIN dbo.CaseDateTypes t ON t.Id=w.CaseDateTypeId ORDER BY sc.Id;

/* SOL/TCN ordinary-family eligibility uses lifecycle/overlay precedence, never semantic mappings. */
;WITH families AS(SELECT sc.Id TenantId,v.SystemKey FROM dbo.ShaleClients sc CROSS JOIN(VALUES('statute_of_limitations'),('tort_notice_deadline'))v(SystemKey)),
candidates AS(SELECT f.*,t.Id,t.ShaleClientId,t.IsActive,t.IsDeleted,ROW_NUMBER() OVER(PARTITION BY f.TenantId,f.SystemKey ORDER BY CASE WHEN t.ShaleClientId=f.TenantId THEN 0 ELSE 1 END,t.Id DESC) rn
 FROM families f LEFT JOIN dbo.CaseDateTypes t ON LOWER(LTRIM(RTRIM(t.SystemKey)))=f.SystemKey AND t.IsDeleted=0 AND (t.ShaleClientId=f.TenantId OR t.ShaleClientId IS NULL))
SELECT TenantId,SystemKey,Id EffectiveCaseDateTypeId,ShaleClientId TypeOwnerTenantId,IsActive,IsDeleted,
 CASE WHEN Id IS NULL THEN N'BLOCKING_MISSING_FAMILY' WHEN IsActive=0 THEN N'BLOCKING_INACTIVE_EFFECTIVE_FAMILY' ELSE N'ELIGIBLE' END Readiness
FROM candidates WHERE rn=1 ORDER BY TenantId,SystemKey;

/* Database-resident SQL modules only; encrypted modules have NULL definitions and are reported below. */
SELECT s.name SchemaName,o.name ModuleName,o.type_desc,
 CASE WHEN sm.definition LIKE N'%CaseDateTypeSemanticRoleMappings%' THEN 1 ELSE 0 END ReferencesMappingTable,
 CASE WHEN sm.definition LIKE N'%STATUTE_OF_LIMITATIONS%' THEN 1 ELSE 0 END ReferencesSolRole,
 CASE WHEN sm.definition LIKE N'%TORT_NOTICE_DEADLINE%' THEN 1 ELSE 0 END ReferencesTcnRole
FROM sys.sql_modules sm JOIN sys.objects o ON o.object_id=sm.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id
WHERE sm.definition LIKE N'%CaseDateTypeSemanticRoleMappings%' OR sm.definition LIKE N'%STATUTE_OF_LIMITATIONS%' OR sm.definition LIKE N'%TORT_NOTICE_DEADLINE%'
ORDER BY s.name,o.name;
SELECT s.name SchemaName,o.name ModuleName,o.type_desc,N'BLOCKING_ENCRYPTED_MODULE_NOT_SEARCHABLE' Finding
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules sm ON sm.object_id=o.object_id
WHERE o.type IN('P','V','FN','IF','TF','TR') AND sm.object_id IS NOT NULL AND sm.definition IS NULL;

SELECT COUNT_BIG(*) FindingCount,N'Active SOL/TCN mappings with unresolved or ineligible type lifecycle' Finding
FROM dbo.CaseDateTypeSemanticRoleMappings m LEFT JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId
WHERE m.SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND m.IsActive=1 AND m.IsDeleted=0
 AND (t.Id IS NULL OR t.IsActive=0 OR t.IsDeleted=1 OR NOT(t.ShaleClientId=m.ShaleClientId OR (t.ShaleClientId IS NULL AND m.ShaleClientId IS NULL)));
SELECT COUNT_BIG(*) InformationalCount,N'SOL/TCN roles that migration will make nonprotected' Finding FROM dbo.CaseDateSemanticRoles WHERE RoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsProtected=1;
SELECT COUNT_BIG(*) FindingCount,N'Intake is absent or not protected' Finding FROM (VALUES(1))v(n)
WHERE (SELECT COUNT_BIG(*) FROM dbo.CaseDateSemanticRoles WHERE RoleKey='INTAKE' AND IsProtected=1)<>1;
SELECT COUNT_BIG(*) FindingCount,N'Active mapping uses an unknown semantic role' Finding FROM dbo.CaseDateTypeSemanticRoleMappings m
WHERE m.IsActive=1 AND m.IsDeleted=0 AND NOT EXISTS(SELECT 1 FROM dbo.CaseDateSemanticRoles r WHERE r.RoleKey=m.SemanticRoleKey);
SELECT COUNT_BIG(*) FindingCount,N'A tenant/global SOL/TCN family has duplicate nondeleted definitions' Finding FROM(
 SELECT t.ShaleClientId,LOWER(LTRIM(RTRIM(t.SystemKey))) SystemKey FROM dbo.CaseDateTypes t
 WHERE t.IsDeleted=0 AND LOWER(LTRIM(RTRIM(t.SystemKey))) IN('statute_of_limitations','tort_notice_deadline')
 GROUP BY t.ShaleClientId,LOWER(LTRIM(RTRIM(t.SystemKey))) HAVING COUNT_BIG(*)>1)x;

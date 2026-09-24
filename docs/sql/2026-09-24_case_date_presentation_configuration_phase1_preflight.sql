/* READ ONLY. Run unchanged with the approved all-tenant principal and NULL application session context. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57220,'Preflight requires NULL application session context.',1;

/* A failed 57206 run is expected to leave neither object because CREATE/INSERT and THROW share one transaction. */
IF OBJECT_ID(N'dbo.CaseDatePresentationConfigurations',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDatePresentationSelections',N'U') IS NULL
 SELECT OBJECT_ID(N'dbo.CaseDatePresentationConfigurations',N'U') ConfigurationObjectId,
        OBJECT_ID(N'dbo.CaseDatePresentationSelections',N'U') SelectionObjectId,
        CAST(NULL AS bigint) ConfigurationCount,CAST(NULL AS bigint) SelectionCount,N'ROLLED_BACK_OR_NEVER_APPLIED' StateFinding;
ELSE
 EXEC sys.sp_executesql N'SELECT OBJECT_ID(N''dbo.CaseDatePresentationConfigurations'',N''U'') ConfigurationObjectId,
  OBJECT_ID(N''dbo.CaseDatePresentationSelections'',N''U'') SelectionObjectId,
  (SELECT COUNT_BIG(*) FROM dbo.CaseDatePresentationConfigurations) ConfigurationCount,
  (SELECT COUNT_BIG(*) FROM dbo.CaseDatePresentationSelections) SelectionCount,N''OBJECTS_PRESENT_REVIEW_ROWS'' StateFinding;';

CREATE TABLE #Proposed(TenantId int NOT NULL,Purpose varchar(32) NOT NULL,SelectionIdentity varchar(160) NOT NULL,OriginalSortOrder int NOT NULL);
INSERT #Proposed
SELECT sc.Id,'CASE_OVERVIEW',v.SelectionIdentity,v.OriginalSortOrder FROM dbo.ShaleClients sc CROSS JOIN(VALUES
 ('SYSTEM:date_of_injury',0),('SYSTEM:date_of_medical_negligence',1),('SYSTEM:intake',2),
 ('SYSTEM:statute_of_limitations',3),('SYSTEM:tort_notice_deadline',4))v(SelectionIdentity,OriginalSortOrder);

/* Candidate detail and an explicit eligibility reason for every proposed tenant/identity pair. */
SELECT p.TenantId,p.Purpose,p.SelectionIdentity,p.OriginalSortOrder,t.Id CaseDateTypeId,t.ShaleClientId TypeOwnerTenantId,
 t.SystemKey,t.Name,t.IsActive,t.IsDeleted,
 CASE
  WHEN t.Id IS NULL THEN 'NO_VISIBLE_CANDIDATE'
  WHEN t.ShaleClientId IS NULL AND NOT EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=t.Id AND m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0) THEN 'GLOBAL_NOT_RUNTIME_VISIBLE'
  WHEN t.ShaleClientId=p.TenantId AND t.IsDeleted=1 THEN 'TENANT_RESET_MARKER'
  WHEN t.IsDeleted=1 THEN 'DELETED'
  WHEN t.IsActive=0 THEN 'INACTIVE_EFFECTIVE_WINNER'
  WHEN EXISTS(SELECT 1 FROM dbo.CaseDateTypes newer WHERE newer.ShaleClientId=p.TenantId AND newer.SystemKey=t.SystemKey AND newer.IsDeleted=0 AND newer.Id<>t.Id) THEN 'SHADOWED_OR_AMBIGUOUS_TENANT_ROW'
  ELSE 'ELIGIBLE'
 END CandidateReason
FROM #Proposed p
LEFT JOIN dbo.CaseDateTypes t ON LOWER(LTRIM(RTRIM(t.SystemKey)))=SUBSTRING(p.SelectionIdentity,8,160)
 AND (t.ShaleClientId=p.TenantId OR t.ShaleClientId IS NULL)
ORDER BY p.TenantId,p.Purpose,p.OriginalSortOrder,CASE WHEN t.ShaleClientId=p.TenantId THEN 0 ELSE 1 END,t.Id;

/* Exact runtime-effective winners, using the same visibility and precedence as listEffectiveCaseDateTypes. */
;WITH visible AS(
 SELECT p.TenantId,p.Purpose,p.SelectionIdentity,p.OriginalSortOrder,t.Id,t.ShaleClientId,t.SystemKey,t.IsActive,t.IsDeleted,
  ROW_NUMBER() OVER(PARTITION BY p.TenantId,p.Purpose,p.SelectionIdentity ORDER BY CASE WHEN t.ShaleClientId=p.TenantId AND t.IsDeleted=0 THEN 0 ELSE 1 END,t.Id) rn
 FROM #Proposed p JOIN dbo.CaseDateTypes t ON LOWER(LTRIM(RTRIM(t.SystemKey)))=SUBSTRING(p.SelectionIdentity,8,160)
 WHERE t.ShaleClientId=p.TenantId OR (t.ShaleClientId IS NULL AND EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=t.Id AND m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0)))
)
SELECT p.TenantId,p.Purpose,p.SelectionIdentity,p.OriginalSortOrder,v.Id EffectiveCaseDateTypeId,v.ShaleClientId EffectiveOwnerTenantId,v.IsActive,v.IsDeleted,
 CASE WHEN v.Id IS NULL THEN 'NO_RUNTIME_VISIBLE_DEFINITION' WHEN v.IsDeleted=1 THEN 'DELETED_EFFECTIVE_WINNER' WHEN v.IsActive=0 THEN 'INACTIVE_EFFECTIVE_WINNER' ELSE 'ELIGIBLE' END Finding
FROM #Proposed p LEFT JOIN visible v ON v.TenantId=p.TenantId AND v.Purpose=p.Purpose AND v.SelectionIdentity=p.SelectionIdentity AND v.rn=1
ORDER BY p.TenantId,p.OriginalSortOrder;

/* Current displays: cards use protected mappings; uncustomized Overview filters its five keys through the runtime-effective list. */
SELECT sc.Id TenantId,'CASE_CARD' Purpose,r.RoleKey,m.SemanticRoleKey,t.Id CaseDateTypeId,t.SystemKey,t.IsActive,t.IsDeleted
FROM dbo.ShaleClients sc CROSS JOIN(VALUES('INTAKE'),('STATUTE_OF_LIMITATIONS'),('TORT_NOTICE_DEADLINE'))r(RoleKey)
OUTER APPLY(SELECT TOP(1) x.* FROM dbo.CaseDateTypeSemanticRoleMappings x
 WHERE (x.ShaleClientId=sc.Id OR x.ShaleClientId IS NULL) AND x.SemanticRoleKey=r.RoleKey AND x.IsActive=1 AND x.IsDeleted=0
 ORDER BY CASE WHEN x.ShaleClientId=sc.Id THEN 0 ELSE 1 END,x.Id DESC)m
LEFT JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId
ORDER BY sc.Id,CASE r.RoleKey WHEN 'INTAKE' THEN 0 WHEN 'STATUTE_OF_LIMITATIONS' THEN 1 ELSE 2 END;

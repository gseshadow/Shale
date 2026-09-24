/* READ ONLY. Run with NULL application session context after both Phase 1 migrations. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 57210,'Verification requires NULL application session context.',1;
SELECT COUNT_BIG(*) FindingCount,N'Tenant/purpose rows missing or duplicated' Finding FROM(
 SELECT sc.Id,p.Purpose,COUNT_BIG(c.Id) Matches FROM dbo.ShaleClients sc CROSS JOIN(VALUES('CASE_CARD'),('CASE_OVERVIEW'))p(Purpose)
 LEFT JOIN dbo.CaseDatePresentationConfigurations c ON c.ShaleClientId=sc.Id AND c.Purpose=p.Purpose GROUP BY sc.Id,p.Purpose HAVING COUNT_BIG(c.Id)<>1)x;
SELECT c.ShaleClientId,c.Purpose,c.Id ConfigurationId,s.Id SelectionId,s.SelectionIdentity,s.SortOrder
FROM dbo.CaseDatePresentationConfigurations c LEFT JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id
ORDER BY c.ShaleClientId,c.Purpose,s.SortOrder,s.Id;
SELECT COUNT_BIG(*) FindingCount,N'Initial selection count/order differs from current display' Finding FROM(
 SELECT c.ShaleClientId,c.Purpose,COUNT_BIG(s.Id) SelectionCount,MIN(s.SortOrder) MinimumSortOrder,MAX(s.SortOrder) MaximumSortOrder
 FROM dbo.CaseDatePresentationConfigurations c LEFT JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id
 GROUP BY c.ShaleClientId,c.Purpose HAVING COUNT_BIG(s.Id)<>CASE c.Purpose WHEN 'CASE_CARD' THEN 3 ELSE 5 END OR MIN(s.SortOrder)<>0 OR MAX(s.SortOrder)<>CASE c.Purpose WHEN 'CASE_CARD' THEN 2 ELSE 4 END)x;
SELECT c.ShaleClientId,c.Purpose,s.SelectionIdentity,s.SortOrder,e.ExpectedIdentity,e.ExpectedSortOrder,N'INITIAL_DEFAULT_MISMATCH' Finding
FROM dbo.CaseDatePresentationConfigurations c JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id
FULL JOIN(VALUES('CASE_OVERVIEW','SYSTEM:date_of_injury',0),('CASE_OVERVIEW','SYSTEM:date_of_medical_negligence',1),('CASE_OVERVIEW','SYSTEM:intake',2),('CASE_OVERVIEW','SYSTEM:statute_of_limitations',3),('CASE_OVERVIEW','SYSTEM:tort_notice_deadline',4))e(Purpose,ExpectedIdentity,ExpectedSortOrder)
 ON e.Purpose=c.Purpose AND e.ExpectedSortOrder=s.SortOrder
WHERE c.Purpose='CASE_OVERVIEW' AND (s.SelectionIdentity<>e.ExpectedIdentity OR s.Id IS NULL);
;WITH candidates AS(SELECT sc.Id TenantId,m.SemanticRoleKey,m.CaseDateTypeId,ROW_NUMBER() OVER(PARTITION BY sc.Id,m.SemanticRoleKey ORDER BY CASE WHEN m.ShaleClientId=sc.Id THEN 0 ELSE 1 END,m.Id DESC) rn
 FROM dbo.ShaleClients sc JOIN dbo.CaseDateTypeSemanticRoleMappings m ON m.ShaleClientId=sc.Id OR m.ShaleClientId IS NULL WHERE m.IsActive=1 AND m.IsDeleted=0 AND m.SemanticRoleKey IN('INTAKE','STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE')),
expected AS(SELECT x.TenantId,CASE WHEN NULLIF(LTRIM(RTRIM(t.SystemKey)),'') IS NULL THEN CONCAT('TYPE:',t.Id) ELSE CONCAT('SYSTEM:',LOWER(LTRIM(RTRIM(t.SystemKey)))) END ExpectedIdentity,
 CASE x.SemanticRoleKey WHEN 'INTAKE' THEN 0 WHEN 'STATUTE_OF_LIMITATIONS' THEN 1 ELSE 2 END ExpectedSortOrder FROM candidates x JOIN dbo.CaseDateTypes t ON t.Id=x.CaseDateTypeId WHERE x.rn=1)
SELECT e.TenantId,c.Id ConfigurationId,s.SelectionIdentity,s.SortOrder,e.ExpectedIdentity,e.ExpectedSortOrder,N'CARD_DEFAULT_MISMATCH' Finding
FROM expected e JOIN dbo.CaseDatePresentationConfigurations c ON c.ShaleClientId=e.TenantId AND c.Purpose='CASE_CARD'
LEFT JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id AND s.SortOrder=e.ExpectedSortOrder
WHERE s.Id IS NULL OR s.SelectionIdentity<>e.ExpectedIdentity;
/* Row-level overlay evidence: both stored global and tenant ids are returned for the same SYSTEM family without mutation. */
SELECT c.ShaleClientId,c.Purpose,s.SelectionIdentity,cd.CaseId,cd.Id CaseDateId,cd.CaseDateTypeId,t.ShaleClientId StoredTypeOwner,cd.StartsAt
FROM dbo.CaseDatePresentationConfigurations c JOIN dbo.CaseDatePresentationSelections s ON s.CaseDatePresentationConfigurationId=c.Id AND s.ShaleClientId=c.ShaleClientId
JOIN dbo.CaseDateTypes t ON (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL) AND s.SelectionIdentity=CONCAT('SYSTEM:',LOWER(LTRIM(RTRIM(t.SystemKey))))
JOIN dbo.CaseDates cd ON cd.ShaleClientId=c.ShaleClientId AND cd.CaseDateTypeId=t.Id AND cd.IsDeleted=0
ORDER BY c.ShaleClientId,c.Purpose,s.SortOrder,cd.CaseId,cd.StartsAt,cd.Id;
SELECT COUNT_BIG(*) FindingCount,N'Per-case Overview parent or explicit-empty override was modified' Finding
FROM dbo.CaseOverviewConfigurations c WHERE NOT EXISTS(SELECT 1 FROM dbo.Cases x WHERE x.ShaleClientId=c.ShaleClientId AND x.Id=c.CaseId);

/* READ ONLY. Run after migration with the approved all-tenant connection and NULL app context. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 57330,'Verification requires NULL application session context.',1;
SELECT COUNT_BIG(*) FindingCount,N'Intake is not the sole protected semantic role' Finding FROM (VALUES(1))v(n)
WHERE (SELECT COUNT_BIG(*) FROM dbo.CaseDateSemanticRoles WHERE IsProtected=1)<>1 OR NOT EXISTS(SELECT 1 FROM dbo.CaseDateSemanticRoles WHERE RoleKey='INTAKE' AND IsProtected=1);
SELECT COUNT_BIG(*) FindingCount,N'Active SOL/TCN semantic mappings remain' Finding FROM dbo.CaseDateTypeSemanticRoleMappings WHERE SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsActive=1 AND IsDeleted=0;
SELECT Id,ShaleClientId,SemanticRoleKey,CaseDateTypeId,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId,IsActive,IsDeleted,RowVer
FROM dbo.CaseDateTypeSemanticRoleMappings WHERE SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') ORDER BY SemanticRoleKey,ShaleClientId,Id;

;WITH families AS(SELECT sc.Id TenantId,v.SystemKey FROM dbo.ShaleClients sc CROSS JOIN(VALUES('statute_of_limitations'),('tort_notice_deadline'))v(SystemKey)),
winners AS(SELECT f.*,t.Id,t.ShaleClientId,t.IsActive,t.IsDeleted,ROW_NUMBER() OVER(PARTITION BY f.TenantId,f.SystemKey ORDER BY CASE WHEN t.ShaleClientId=f.TenantId THEN 0 ELSE 1 END,t.Id DESC)rn FROM families f LEFT JOIN dbo.CaseDateTypes t ON LOWER(LTRIM(RTRIM(t.SystemKey)))=f.SystemKey AND t.IsDeleted=0 AND (t.ShaleClientId=f.TenantId OR t.ShaleClientId IS NULL))
SELECT TenantId,SystemKey,Id EffectiveCaseDateTypeId,ShaleClientId TypeOwnerTenantId,IsActive,IsDeleted,CASE WHEN Id IS NULL OR IsActive=0 THEN N'BLOCKING' ELSE N'ACCESSIBLE' END VerificationState FROM winners WHERE rn=1 ORDER BY TenantId,SystemKey;
SELECT cd.ShaleClientId,cd.CaseId,cd.Id CaseDateId,cd.CaseDateTypeId,t.SystemKey,cd.StartsAt,cd.EndsAt,cd.AllDay,cd.IsDeleted
FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId WHERE LOWER(LTRIM(RTRIM(t.SystemKey))) IN('statute_of_limitations','tort_notice_deadline') ORDER BY cd.ShaleClientId,cd.CaseId,t.SystemKey,cd.StartsAt,cd.Id;

/* Concrete identities for operator comparison to captured preflight/deployment evidence. */
SELECT c.Id ConfigurationId,c.ShaleClientId,c.Purpose,c.CreatedAt,c.CreatedByUserId,c.UpdatedAt,c.UpdatedByUserId,c.RowVer,
 s.Id SelectionId,s.SelectionIdentity,s.SortOrder,s.CreatedAt SelectionCreatedAt,s.CreatedByUserId SelectionCreatedByUserId
FROM dbo.CaseDatePresentationConfigurations c LEFT JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id ORDER BY c.ShaleClientId,c.Purpose,s.SortOrder,s.Id;
SELECT c.Id OverviewConfigurationId,c.ShaleClientId,c.CaseId,c.CreatedAt,c.CreatedByUserId,c.UpdatedAt,c.UpdatedByUserId,c.RowVer,
 s.Id SelectionId,s.CaseDateTypeId,s.SortOrder,s.CreatedAt SelectionCreatedAt,s.CreatedByUserId SelectionCreatedByUserId
FROM dbo.CaseOverviewConfigurations c LEFT JOIN dbo.CaseOverviewDateSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseOverviewConfigurationId=c.Id ORDER BY c.ShaleClientId,c.CaseId,s.SortOrder,s.Id;
SELECT Id PolicyId,ShaleClientId,CaseDateTypePolicyKey,PolicyRevision,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,SupersededAt,CreatedAt,CreatedByUserId
FROM dbo.FieldConfirmationPolicies WHERE CaseDateTypePolicyKey IS NOT NULL ORDER BY ShaleClientId,CaseDateTypePolicyKey,PolicyRevision,Id;
SELECT COUNT_BIG(*) FindingCount,N'Presentation selection has no parent' Finding FROM dbo.CaseDatePresentationSelections s WHERE NOT EXISTS(SELECT 1 FROM dbo.CaseDatePresentationConfigurations c WHERE c.ShaleClientId=s.ShaleClientId AND c.Id=s.CaseDatePresentationConfigurationId);
SELECT COUNT_BIG(*) FindingCount,N'Per-case Overview selection has no parent/case' Finding FROM dbo.CaseOverviewDateSelections s LEFT JOIN dbo.CaseOverviewConfigurations o ON o.Id=s.CaseOverviewConfigurationId AND o.ShaleClientId=s.ShaleClientId LEFT JOIN dbo.Cases c ON c.Id=o.CaseId AND c.ShaleClientId=o.ShaleClientId WHERE o.Id IS NULL OR c.Id IS NULL;
SELECT COUNT_BIG(*) FindingCount,N'Current confirmation policy identity is duplicated' Finding FROM(SELECT ShaleClientId,CaseDateTypePolicyKey FROM dbo.FieldConfirmationPolicies WHERE CaseDateTypePolicyKey IS NOT NULL AND SupersededAt IS NULL GROUP BY ShaleClientId,CaseDateTypePolicyKey HAVING COUNT_BIG(*)>1)x;

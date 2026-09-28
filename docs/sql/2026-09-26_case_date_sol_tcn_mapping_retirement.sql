/* Forward-only, rerun-safe retirement. Deploy the required application first and review preflight.
   Set the acknowledgement to 1 only on the reviewed execution copy. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @OperatorVerifiedAllTenantVisibilityAndPreflight bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57310,'Use the approved all-tenant administrative connection with NULL application session context.',1;
IF @OperatorVerifiedAllTenantVisibilityAndPreflight<>1
 THROW 57311,'Review a clean preflight, verify external consumers, then explicitly acknowledge execution.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.CaseDateSemanticRoles',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings',N'U') IS NULL THROW 57312,'Semantic-role foundations are missing.',1;
 IF (SELECT COUNT_BIG(*) FROM dbo.CaseDateSemanticRoles WHERE RoleKey='INTAKE' AND IsProtected=1)<>1 THROW 57313,'Intake must exist and remain protected.',1;
 IF EXISTS(SELECT 1 FROM dbo.ShaleClients sc OUTER APPLY(SELECT COUNT_BIG(*) n FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.ShaleClientId=sc.Id AND m.SemanticRoleKey='INTAKE' AND m.IsActive=1 AND m.IsDeleted=0)t OUTER APPLY(SELECT COUNT_BIG(*) n FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.ShaleClientId IS NULL AND m.SemanticRoleKey='INTAKE' AND m.IsActive=1 AND m.IsDeleted=0)g WHERE t.n>1 OR (t.n=0 AND g.n<>1)) THROW 57314,'Effective Intake mapping is missing or ambiguous.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m LEFT JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId WHERE m.SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND m.IsActive=1 AND m.IsDeleted=0 AND (t.Id IS NULL OR t.IsActive=0 OR t.IsDeleted=1)) THROW 57315,'An active SOL/TCN mapping has unresolved lifecycle state.',1;
 IF EXISTS(SELECT 1 FROM dbo.ShaleClients sc CROSS JOIN(VALUES('statute_of_limitations'),('tort_notice_deadline'))v(SystemKey) OUTER APPLY(SELECT TOP(1)t.Id,t.IsActive FROM dbo.CaseDateTypes t WHERE LOWER(LTRIM(RTRIM(t.SystemKey)))=v.SystemKey AND t.IsDeleted=0 AND (t.ShaleClientId=sc.Id OR t.ShaleClientId IS NULL) ORDER BY CASE WHEN t.ShaleClientId=sc.Id THEN 0 ELSE 1 END,t.Id DESC)w WHERE w.Id IS NULL OR w.IsActive=0) THROW 57326,'A tenant lacks an active effective SOL/TCN ordinary family.',1;

 SELECT Id,ShaleClientId,SemanticRoleKey,CaseDateTypeId,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId,IsActive,IsDeleted
 INTO #MappingsBefore FROM dbo.CaseDateTypeSemanticRoleMappings;
 SELECT Id,ShaleClientId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Title,Notes,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId,IsDeleted
 INTO #DatesBefore FROM dbo.CaseDates;
 SELECT Id,ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,IsDeleted,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId
 INTO #TypesBefore FROM dbo.CaseDateTypes;
 SELECT * INTO #PresentationConfigurationsBefore FROM dbo.CaseDatePresentationConfigurations;
 SELECT * INTO #PresentationSelectionsBefore FROM dbo.CaseDatePresentationSelections;
 SELECT * INTO #OverviewConfigurationsBefore FROM dbo.CaseOverviewConfigurations;
 SELECT * INTO #OverviewSelectionsBefore FROM dbo.CaseOverviewDateSelections;
 SELECT * INTO #ConfirmationPoliciesBefore FROM dbo.FieldConfirmationPolicies;

 UPDATE dbo.CaseDateTypeSemanticRoleMappings SET IsActive=0,IsDeleted=1,
  UpdatedAt=COALESCE(UpdatedAt,SYSUTCDATETIME()),DeletedAt=COALESCE(DeletedAt,SYSUTCDATETIME())
 WHERE SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsActive=1 AND IsDeleted=0;
 UPDATE dbo.CaseDateSemanticRoles SET IsProtected=0 WHERE RoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsProtected<>0;

 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings WHERE SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsActive=1 AND IsDeleted=0) THROW 57316,'SOL/TCN mapping retirement did not converge.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateSemanticRoles WHERE (RoleKey='INTAKE' AND IsProtected<>1) OR (RoleKey<>'INTAKE' AND IsProtected=1)) THROW 57317,'Intake is not the sole protected role.',1;
 /* Exact, row-level guardrails for every table this migration must not change. */
 IF EXISTS((SELECT * FROM #DatesBefore EXCEPT SELECT Id,ShaleClientId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Title,Notes,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId,IsDeleted FROM dbo.CaseDates) UNION ALL (SELECT Id,ShaleClientId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Title,Notes,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId,IsDeleted FROM dbo.CaseDates EXCEPT SELECT * FROM #DatesBefore)) THROW 57318,'CaseDates changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #TypesBefore EXCEPT SELECT Id,ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,IsDeleted,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId FROM dbo.CaseDateTypes) UNION ALL (SELECT Id,ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,IsDeleted,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,DeletedAt,DeletedByUserId FROM dbo.CaseDateTypes EXCEPT SELECT * FROM #TypesBefore)) THROW 57319,'CaseDateTypes changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #PresentationConfigurationsBefore EXCEPT SELECT * FROM dbo.CaseDatePresentationConfigurations) UNION ALL (SELECT * FROM dbo.CaseDatePresentationConfigurations EXCEPT SELECT * FROM #PresentationConfigurationsBefore)) THROW 57320,'Presentation configurations changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #PresentationSelectionsBefore EXCEPT SELECT * FROM dbo.CaseDatePresentationSelections) UNION ALL (SELECT * FROM dbo.CaseDatePresentationSelections EXCEPT SELECT * FROM #PresentationSelectionsBefore)) THROW 57321,'Presentation selections changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #OverviewConfigurationsBefore EXCEPT SELECT * FROM dbo.CaseOverviewConfigurations) UNION ALL (SELECT * FROM dbo.CaseOverviewConfigurations EXCEPT SELECT * FROM #OverviewConfigurationsBefore)) THROW 57322,'Overview overrides changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #OverviewSelectionsBefore EXCEPT SELECT * FROM dbo.CaseOverviewDateSelections) UNION ALL (SELECT * FROM dbo.CaseOverviewDateSelections EXCEPT SELECT * FROM #OverviewSelectionsBefore)) THROW 57323,'Overview selections changed unexpectedly.',1;
 IF EXISTS((SELECT * FROM #ConfirmationPoliciesBefore EXCEPT SELECT * FROM dbo.FieldConfirmationPolicies) UNION ALL (SELECT * FROM dbo.FieldConfirmationPolicies EXCEPT SELECT * FROM #ConfirmationPoliciesBefore)) THROW 57324,'Confirmation policies changed unexpectedly.',1;
 /* Mapping identity/provenance is retained; only lifecycle timestamps/flags may differ. */
 IF EXISTS(SELECT 1 FROM #MappingsBefore b FULL JOIN dbo.CaseDateTypeSemanticRoleMappings a ON a.Id=b.Id WHERE a.Id IS NULL OR b.Id IS NULL OR a.ShaleClientId<>b.ShaleClientId OR a.SemanticRoleKey<>b.SemanticRoleKey OR a.CaseDateTypeId<>b.CaseDateTypeId OR a.CreatedAt<>b.CreatedAt OR ISNULL(a.CreatedByUserId,-1)<>ISNULL(b.CreatedByUserId,-1)) THROW 57325,'Mapping identity or creation history changed unexpectedly.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;

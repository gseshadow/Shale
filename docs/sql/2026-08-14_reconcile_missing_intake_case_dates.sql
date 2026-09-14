/*
  Reconcile legacy Intake values that were written after the Case Dates cutover.
  Ordering: run after 2026-08-12_entity_action_audit_entity_type_constraint.sql.
  Rollback: this is a forward-only data repair. Restore the pre-deployment backup, or
  soft-delete only rows identified by the Source='CASE_DATES_INTAKE_RECONCILIATION' audit entries
  after a separately reviewed tenant-scoped rollback. Never restore CallerDate as runtime authority.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
  BEGIN TRANSACTION;

  IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL
     OR OBJECT_ID(N'dbo.CaseDateTypes', N'U') IS NULL
     OR OBJECT_ID(N'dbo.CaseDateSemanticRoles', N'U') IS NULL
     OR OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings', N'U') IS NULL
     OR OBJECT_ID(N'dbo.EntityActionAuditLog', N'U') IS NULL
     OR OBJECT_ID(N'dbo.AuditLog', N'U') IS NULL
    THROW 56800, 'Required Case Date or audit schema is missing.', 1;
  IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
     OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'), 0) <> 1 AND ISNULL(IS_MEMBER(N'db_owner'), 0) <> 1)
    THROW 56801, 'All-tenant administrative visibility is required and ShaleClientId session context must be NULL.', 1;
  IF NOT EXISTS (SELECT 1 FROM dbo.CaseDateSemanticRoles WHERE RoleKey='INTAKE' AND IsProtected=1)
    THROW 56802, 'The protected INTAKE semantic role is missing.', 1;

  /* One safe migration actor per tenant. Prefer the recorded intake taker, then an active admin.
     Actor ids are audit attribution only; Source identifies this system reconciliation. */
  ;WITH TenantActors AS (
    SELECT c.ShaleClientId,
      COALESCE(MAX(CASE WHEN iu.id IS NOT NULL THEN iu.id END), MIN(au.id)) ActorUserId
    FROM dbo.Cases c
    LEFT JOIN dbo.Users iu ON iu.id=c.IntakeTakenByUserId AND iu.ShaleClientId=c.ShaleClientId AND ISNULL(iu.is_deleted,0)=0
    LEFT JOIN dbo.Users au ON au.ShaleClientId=c.ShaleClientId AND ISNULL(au.is_deleted,0)=0 AND au.is_admin=1
    WHERE c.CallerDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0
    GROUP BY c.ShaleClientId
  ), EligibleMappings AS (
    SELECT c.ShaleClientId,c.Id CaseId,c.CallerDate,c.CallerTime,ta.ActorUserId,m.CaseDateTypeId,
      ROW_NUMBER() OVER(PARTITION BY c.ShaleClientId,c.Id ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) rn,
      COUNT_BIG(*) OVER(PARTITION BY c.ShaleClientId,c.Id,CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 1 ELSE 0 END) ScopeCount
    FROM dbo.Cases c
    JOIN TenantActors ta ON ta.ShaleClientId=c.ShaleClientId
    JOIN dbo.CaseDateTypeSemanticRoleMappings m ON m.SemanticRoleKey='INTAKE'
      AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL) AND m.IsActive=1 AND m.IsDeleted=0
    JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
      AND t.IsActive=1 AND t.IsDeleted=0 AND t.SupportsTime=1
    WHERE c.CallerDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0
      AND NOT (m.ShaleClientId IS NULL AND EXISTS (
        SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings tm
        JOIN dbo.CaseDateTypes tt ON tt.Id=tm.CaseDateTypeId
        WHERE tm.SemanticRoleKey='INTAKE' AND tm.ShaleClientId=c.ShaleClientId
          AND tm.IsActive=1 AND tm.IsDeleted=0 AND tt.ShaleClientId=c.ShaleClientId
          AND tt.IsActive=1 AND tt.IsDeleted=0))
  )
  SELECT ShaleClientId,CaseId,CallerDate,CallerTime,ActorUserId,CaseDateTypeId,ScopeCount,
    CASE WHEN CallerTime IS NULL THEN CAST(CallerDate AS datetime2(7))
         ELSE DATETIME2FROMPARTS(YEAR(CallerDate),MONTH(CallerDate),DAY(CallerDate),
           DATEPART(hour,CallerTime),DATEPART(minute,CallerTime),DATEPART(second,CallerTime),
           DATEPART(nanosecond,CallerTime)/100,7) END StartsAt,
    CAST(CASE WHEN CallerTime IS NULL THEN 1 ELSE 0 END AS bit) AllDay
  INTO #Candidates FROM EligibleMappings WHERE rn=1;

  IF EXISTS (SELECT 1 FROM #Candidates WHERE ScopeCount<>1) THROW 56803, 'Ambiguous effective INTAKE mapping.', 1;
  IF EXISTS (SELECT 1 FROM #Candidates WHERE ActorUserId IS NULL) THROW 56804, 'A participating tenant has no active intake-taker or administrator for audit attribution.', 1;
  IF EXISTS (
    SELECT 1 FROM dbo.Cases c WHERE c.CallerDate IS NOT NULL AND ISNULL(c.IsDeleted,0)=0
      AND NOT EXISTS(SELECT 1 FROM #Candidates x WHERE x.ShaleClientId=c.ShaleClientId AND x.CaseId=c.Id))
    THROW 56805, 'At least one legacy Intake value cannot resolve an effective INTAKE semantic mapping.', 1;

  ;WITH CandidateOccurrenceFlags AS (
    SELECT x.*,
      CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.CaseDates cd
      JOIN dbo.CaseDateTypeSemanticRoleMappings im ON im.CaseDateTypeId=cd.CaseDateTypeId
       AND im.SemanticRoleKey='INTAKE' AND im.IsActive=1 AND im.IsDeleted=0
       AND (im.ShaleClientId=x.ShaleClientId OR im.ShaleClientId IS NULL)
      WHERE cd.ShaleClientId=x.ShaleClientId AND cd.CaseId=x.CaseId AND cd.IsDeleted=0)
      THEN 1 ELSE 0 END AS bit) ExistingOccurrence
    FROM #Candidates x
  )
  SELECT COUNT_BIG(*) LegacyIntakeCount,
    SUM(CAST(ExistingOccurrence AS bigint)) ExistingAuthoritativeCount,
    SUM(CAST(1-ExistingOccurrence AS bigint)) PreflightReconciliationCount
  FROM CandidateOccurrenceFlags;

  SELECT x.* INTO #Missing FROM #Candidates x WHERE NOT EXISTS(
    SELECT 1 FROM dbo.CaseDates cd
    JOIN dbo.CaseDateTypeSemanticRoleMappings im ON im.CaseDateTypeId=cd.CaseDateTypeId
      AND im.SemanticRoleKey='INTAKE' AND im.IsActive=1 AND im.IsDeleted=0
      AND (im.ShaleClientId=x.ShaleClientId OR im.ShaleClientId IS NULL)
    WHERE cd.ShaleClientId=x.ShaleClientId AND cd.CaseId=x.CaseId AND cd.IsDeleted=0);
  DECLARE @Expected bigint=(SELECT COUNT_BIG(*) FROM #Missing);
  DECLARE @Inserted TABLE(CaseDateId bigint,ShaleClientId int,CaseId bigint,ActorUserId int,StartsAt datetime2(7));
  INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,Notes,CreatedAt,CreatedByUserId)
    OUTPUT INSERTED.Id,INSERTED.ShaleClientId,INSERTED.CaseId,INSERTED.CreatedByUserId,INSERTED.StartsAt
      INTO @Inserted
    SELECT ShaleClientId,CaseId,CaseDateTypeId,StartsAt,NULL,AllDay,NULL,SYSUTCDATETIME(),ActorUserId FROM #Missing;
  IF @@ROWCOUNT<>@Expected THROW 56806, 'Intake reconciliation insert count mismatch.', 1;

  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,OccurredAt,
      ParentEntityType,ParentEntityId,Source,Metadata)
    SELECT ShaleClientId,ActorUserId,'CASE_DATE',CaseDateId,'CREATED',SYSUTCDATETIME(),
      'CASE',CaseId,'CASE_DATES_INTAKE_RECONCILIATION',
      CONCAT('{"CASE_ID":"',CaseId,'","CASE_DATE_ID":"',CaseDateId,'"}') FROM @Inserted;
  INSERT dbo.AuditLog(ShaleClientId,UserId,ObjectTypeId,ObjectId,FieldName,FieldCode,StringValue,DateValue,BooleanValue,IntValue,EntryDate)
    SELECT ShaleClientId,ActorUserId,NULL,CaseDateId,'CaseDates.StartsAt',4,
      CONCAT('old=null;new=',CONVERT(nvarchar(33),StartsAt,126)),CAST(StartsAt AS date),NULL,NULL,SYSUTCDATETIME() FROM @Inserted;

  IF EXISTS(SELECT 1 FROM #Missing m WHERE NOT EXISTS(
      SELECT 1 FROM dbo.CaseDates cd WHERE cd.Id IN (SELECT CaseDateId FROM @Inserted)
       AND cd.ShaleClientId=m.ShaleClientId AND cd.CaseId=m.CaseId AND cd.CaseDateTypeId=m.CaseDateTypeId
       AND cd.StartsAt=m.StartsAt AND cd.AllDay=m.AllDay AND cd.EndsAt IS NULL AND cd.IsDeleted=0))
    THROW 56807, 'Post-migration value verification failed.', 1;

  SELECT @Expected ReconciliationCount,(SELECT COUNT_BIG(*) FROM @Inserted) InsertedCount,
    (SELECT COUNT_BIG(*) FROM @Inserted i JOIN dbo.EntityActionAuditLog a ON a.ShaleClientId=i.ShaleClientId AND a.EntityType='CASE_DATE' AND a.EntityId=i.CaseDateId AND a.Source='CASE_DATES_INTAKE_RECONCILIATION') EntityAuditCount,
    (SELECT COUNT_BIG(*) FROM @Inserted i JOIN dbo.AuditLog a ON a.ShaleClientId=i.ShaleClientId AND a.ObjectId=i.CaseDateId AND a.FieldName='CaseDates.StartsAt') PhiAuditCount;
  SELECT ShaleClientId,COUNT_BIG(*) ReconciledByTenant FROM @Inserted GROUP BY ShaleClientId ORDER BY ShaleClientId;
  COMMIT TRANSACTION;
END TRY
BEGIN CATCH
  IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
  THROW;
END CATCH;

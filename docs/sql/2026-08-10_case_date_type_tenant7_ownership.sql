/* Production ownership correction: preserve the ten deployed type ids and all references. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
 BEGIN TRANSACTION;

 IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR
    (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
  THROW 56920,'Case Date Type ownership correction requires an approved administrative session.',1;
 IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL OR
    OBJECT_ID(N'dbo.FormConfiguredFields',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings',N'U') IS NULL
  THROW 56921,'Case Date Type ownership correction prerequisites are unavailable.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.ShaleClients WHERE Id=7) OR NOT EXISTS(SELECT 1 FROM dbo.ShaleClients WHERE Id=8)
  THROW 56922,'Required tenancy prerequisites are unavailable.',1;

 DECLARE @Expected TABLE(Id int NOT NULL PRIMARY KEY,SystemKey nvarchar(64) NOT NULL UNIQUE,Name nvarchar(100) NOT NULL);
 INSERT @Expected VALUES
 (7,N'trial',N'Trial'),(8,N'hearing',N'Hearing'),(9,N'mediation',N'Mediation'),(10,N'deposition',N'Deposition'),
 (3,N'discovery_deadline',N'Discovery Deadline'),(4,N'date_of_injury',N'Date of Injury'),
 (5,N'date_of_medical_negligence',N'Date of Medical Negligence'),
 (6,N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered'),
 (12,N'fee_agreement_signed',N'Fee Agreement Signed'),(13,N'non_engagement_letter_sent',N'Non-Engagement Letter Sent');

 /* UPDLOCK/HOLDLOCK makes validation and correction one serializable operation. */
 SELECT t.Id INTO #LockedTargets
 FROM dbo.CaseDateTypes t WITH(UPDLOCK,HOLDLOCK) JOIN @Expected e ON e.SystemKey=t.SystemKey;

 IF EXISTS(SELECT 1 FROM @Expected e LEFT JOIN dbo.CaseDateTypes t ON t.SystemKey=e.SystemKey GROUP BY e.SystemKey HAVING COUNT(t.Id)<>1)
  THROW 56923,'Expected Case Date Type identity is missing or ambiguous.',1;
 IF EXISTS(SELECT 1 FROM @Expected e JOIN dbo.CaseDateTypes t ON t.SystemKey=e.SystemKey
           WHERE t.Id<>e.Id OR t.Name<>e.Name OR t.IsActive<>1 OR t.IsDeleted<>0)
  THROW 56924,'Expected Case Date Type identity or lifecycle state does not match production contract.',1;
 IF EXISTS(SELECT 1 FROM @Expected e JOIN dbo.CaseDateTypes t ON t.SystemKey=e.SystemKey
           WHERE t.ShaleClientId IS NOT NULL AND t.ShaleClientId<>7)
  THROW 56925,'Expected Case Date Type has conflicting ownership.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypes t WHERE t.ShaleClientId IS NOT NULL AND t.Id NOT IN(SELECT Id FROM @Expected)
           AND (t.SystemKey IN(SELECT SystemKey FROM @Expected) OR LOWER(LTRIM(RTRIM(t.Name))) IN(SELECT LOWER(LTRIM(RTRIM(Name))) FROM @Expected)))
  THROW 56926,'Tenant Case Date Type identity conflicts with the ownership correction.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m JOIN @Expected e ON e.Id=m.CaseDateTypeId)
  THROW 56927,'Protected semantic-role participation blocks the ownership correction.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDates cd JOIN @Expected e ON e.Id=cd.CaseDateTypeId
           LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId
           WHERE cd.ShaleClientId<>7 OR c.Id IS NULL OR c.ShaleClientId<>7)
  THROW 56928,'Cross-tenant Case Date references block the ownership correction.',1;
 IF EXISTS(SELECT 1 FROM dbo.FormConfiguredFields f JOIN @Expected e ON e.Id=f.CaseDateTypeId WHERE f.ShaleClientId<>7)
  THROW 56929,'Cross-tenant form references block the ownership correction.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
           WHERE fk.referenced_object_id=OBJECT_ID(N'dbo.CaseDateTypes')
             AND NOT(OBJECT_SCHEMA_NAME(fk.parent_object_id)=N'dbo' AND OBJECT_NAME(fk.parent_object_id) IN(N'CaseDates',N'FormConfiguredFields',N'CaseDateTypeSemanticRoleMappings')))
  THROW 56930,'An unreviewed Case Date Type foreign-key consumer blocks the ownership correction.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypes WHERE ShaleClientId=8)
  THROW 56931,'The personal test tenant must remain built-in-only.',1;

 DECLARE @OccurrenceCount bigint=(SELECT COUNT_BIG(*) FROM dbo.CaseDates cd JOIN @Expected e ON e.Id=cd.CaseDateTypeId),
         @FormCount bigint=(SELECT COUNT_BIG(*) FROM dbo.FormConfiguredFields f JOIN @Expected e ON e.Id=f.CaseDateTypeId);

 UPDATE t SET ShaleClientId=7
 FROM dbo.CaseDateTypes t JOIN @Expected e ON e.Id=t.Id
 WHERE t.ShaleClientId IS NULL;

 IF @@ROWCOUNT NOT IN(0,10) OR EXISTS(SELECT 1 FROM @Expected e JOIN dbo.CaseDateTypes t ON t.Id=e.Id WHERE t.ShaleClientId<>7)
  THROW 56932,'Case Date Type ownership correction was incomplete.',1;
 IF @OccurrenceCount<>(SELECT COUNT_BIG(*) FROM dbo.CaseDates cd JOIN @Expected e ON e.Id=cd.CaseDateTypeId) OR
    @FormCount<>(SELECT COUNT_BIG(*) FROM dbo.FormConfiguredFields f JOIN @Expected e ON e.Id=f.CaseDateTypeId)
  THROW 56933,'Case Date Type reference counts changed unexpectedly.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDateTypes t WHERE t.ShaleClientId IS NULL AND NOT EXISTS(
    SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=t.Id AND m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0))
  THROW 56934,'A nonprotected global Case Date Type remains after correction.',1;
 IF (SELECT COUNT_BIG(*) FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL)<>3 OR
    EXISTS(SELECT 1 FROM (VALUES(N'intake'),(N'statute_of_limitations'),(N'tort_notice_deadline')) p(SystemKey)
           LEFT JOIN dbo.CaseDateTypes t ON t.ShaleClientId IS NULL AND t.SystemKey=p.SystemKey
           LEFT JOIN dbo.CaseDateTypeSemanticRoleMappings m ON m.CaseDateTypeId=t.Id AND m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0
           GROUP BY p.SystemKey HAVING COUNT(t.Id)<>1 OR COUNT(m.Id)<>1)
  THROW 56935,'Protected global Case Date Type invariants are not satisfied.',1;

 COMMIT TRANSACTION;
 SELECT N'CASE_DATE_TYPE_OWNERSHIP_CORRECTED' Result,@OccurrenceCount Tenant7OccurrenceCount,@FormCount Tenant7FormReferenceCount;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

/* Forward-only Phase 1 foundation. Apply after Case Date semantic roles and before its audit/application release. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.CaseDateTypes',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDateTypeSemanticRoleMappings',N'U') IS NULL THROW 57200,'Case Date type and role foundations are required.',1;
 IF OBJECT_ID(N'dbo.CaseDatePresentationConfigurations',N'U') IS NULL CREATE TABLE dbo.CaseDatePresentationConfigurations(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseDatePresentationConfigurations PRIMARY KEY,
  ShaleClientId int NOT NULL, Purpose varchar(32) NOT NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_CaseDatePresentationConfigurations_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
  UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, RowVer rowversion NOT NULL,
  CONSTRAINT CK_CaseDatePresentationConfigurations_Purpose CHECK(Purpose IN('CASE_CARD','CASE_OVERVIEW')),
  CONSTRAINT FK_CaseDatePresentationConfigurations_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseDatePresentationConfigurations_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT FK_CaseDatePresentationConfigurations_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT UQ_CaseDatePresentationConfigurations_TenantPurpose UNIQUE(ShaleClientId,Purpose),
  CONSTRAINT UQ_CaseDatePresentationConfigurations_TenantId UNIQUE(ShaleClientId,Id));
 IF OBJECT_ID(N'dbo.CaseDatePresentationSelections',N'U') IS NULL CREATE TABLE dbo.CaseDatePresentationSelections(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseDatePresentationSelections PRIMARY KEY,
  ShaleClientId int NOT NULL, CaseDatePresentationConfigurationId bigint NOT NULL, SelectionIdentity varchar(160) NOT NULL, SortOrder int NOT NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_CaseDatePresentationSelections_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
  CONSTRAINT CK_CaseDatePresentationSelections_Identity CHECK(
   (SelectionIdentity LIKE 'SYSTEM:%' AND LEN(SelectionIdentity)>7 AND SUBSTRING(SelectionIdentity,8,160)=LOWER(LTRIM(RTRIM(SUBSTRING(SelectionIdentity,8,160))))) OR
   (SelectionIdentity LIKE 'TYPE:%' AND TRY_CONVERT(int,SUBSTRING(SelectionIdentity,6,20))>0 AND SelectionIdentity=CONCAT('TYPE:',TRY_CONVERT(int,SUBSTRING(SelectionIdentity,6,20))))),
  CONSTRAINT CK_CaseDatePresentationSelections_SortOrder CHECK(SortOrder>=0),
  CONSTRAINT FK_CaseDatePresentationSelections_ConfigTenant FOREIGN KEY(ShaleClientId,CaseDatePresentationConfigurationId) REFERENCES dbo.CaseDatePresentationConfigurations(ShaleClientId,Id) ON DELETE CASCADE,
  CONSTRAINT FK_CaseDatePresentationSelections_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseDatePresentationSelections_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT UQ_CaseDatePresentationSelections_ConfigIdentity UNIQUE(CaseDatePresentationConfigurationId,SelectionIdentity),
  CONSTRAINT UQ_CaseDatePresentationSelections_ConfigOrder UNIQUE(CaseDatePresentationConfigurationId,SortOrder));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDatePresentationSelections') AND name=N'IX_CaseDatePresentationSelections_TenantConfigOrder')
  CREATE INDEX IX_CaseDatePresentationSelections_TenantConfigOrder ON dbo.CaseDatePresentationSelections(ShaleClientId,CaseDatePresentationConfigurationId,SortOrder) INCLUDE(SelectionIdentity);

 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57201,'Strict tenant RLS predicate is missing.',1;
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter'); IF @policyId IS NULL THROW 57202,'TenantFilter policy is missing.',1;
 DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseDatePresentationConfigurations') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDatePresentationConfigurations;');
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseDatePresentationSelections') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDatePresentationSelections;');

 /* A partial prior seed is unsafe: the application treats an empty list as explicit. */
 IF EXISTS(SELECT 1 FROM dbo.CaseDatePresentationConfigurations GROUP BY ShaleClientId HAVING COUNT(*)<>2) THROW 57203,'Partial Case Date presentation seed detected.',1;
 INSERT dbo.CaseDatePresentationConfigurations(ShaleClientId,Purpose)
 SELECT sc.Id,p.Purpose FROM dbo.ShaleClients sc CROSS JOIN(VALUES('CASE_CARD'),('CASE_OVERVIEW'))p(Purpose)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.CaseDatePresentationConfigurations c WHERE c.ShaleClientId=sc.Id AND c.Purpose=p.Purpose);

 /* Cards currently expose Intake, SOL, and TCN. Resolve protected mappings with tenant precedence. */
 ;WITH mapping_candidates AS(
  SELECT sc.Id TenantId,r.SemanticRoleKey,r.CaseDateTypeId,
   ROW_NUMBER() OVER(PARTITION BY sc.Id,r.SemanticRoleKey ORDER BY CASE WHEN r.ShaleClientId=sc.Id THEN 0 ELSE 1 END,r.Id DESC) rn
  FROM dbo.ShaleClients sc JOIN dbo.CaseDateTypeSemanticRoleMappings r ON r.ShaleClientId=sc.Id OR r.ShaleClientId IS NULL
  WHERE r.SemanticRoleKey IN('INTAKE','STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND r.IsActive=1 AND r.IsDeleted=0),
 resolved AS(SELECT m.TenantId,m.SemanticRoleKey,t.Id TypeId,t.SystemKey FROM mapping_candidates m JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId WHERE m.rn=1 AND t.IsActive=1 AND t.IsDeleted=0),
 ordered AS(SELECT *,CASE SemanticRoleKey WHEN 'INTAKE' THEN 0 WHEN 'STATUTE_OF_LIMITATIONS' THEN 1 ELSE 2 END SortOrder FROM resolved)
 INSERT dbo.CaseDatePresentationSelections(ShaleClientId,CaseDatePresentationConfigurationId,SelectionIdentity,SortOrder)
 SELECT o.TenantId,c.Id,CASE WHEN NULLIF(LTRIM(RTRIM(o.SystemKey)),'') IS NULL THEN CONCAT('TYPE:',o.TypeId) ELSE CONCAT('SYSTEM:',LOWER(LTRIM(RTRIM(o.SystemKey)))) END,o.SortOrder
 FROM ordered o JOIN dbo.CaseDatePresentationConfigurations c ON c.ShaleClientId=o.TenantId AND c.Purpose='CASE_CARD'
 WHERE NOT EXISTS(SELECT 1 FROM dbo.CaseDatePresentationSelections s WHERE s.CaseDatePresentationConfigurationId=c.Id);
 IF EXISTS(SELECT 1 FROM dbo.CaseDatePresentationConfigurations c WHERE c.Purpose='CASE_CARD' AND (SELECT COUNT(*) FROM dbo.CaseDatePresentationSelections s WHERE s.CaseDatePresentationConfigurationId=c.Id)<>3) THROW 57204,'Every tenant must resolve the three current card meanings.',1;

 /* The established uncustomized Overview order is identity-based and independent of per-case overrides. */
 INSERT dbo.CaseDatePresentationSelections(ShaleClientId,CaseDatePresentationConfigurationId,SelectionIdentity,SortOrder)
 SELECT c.ShaleClientId,c.Id,v.IdentityValue,v.SortOrder FROM dbo.CaseDatePresentationConfigurations c
 CROSS JOIN(VALUES('SYSTEM:date_of_injury',0),('SYSTEM:date_of_medical_negligence',1),('SYSTEM:intake',2),('SYSTEM:statute_of_limitations',3),('SYSTEM:tort_notice_deadline',4))v(IdentityValue,SortOrder)
 WHERE c.Purpose='CASE_OVERVIEW' AND NOT EXISTS(SELECT 1 FROM dbo.CaseDatePresentationSelections s WHERE s.CaseDatePresentationConfigurationId=c.Id);
 IF EXISTS(SELECT 1 FROM dbo.CaseDatePresentationConfigurations c WHERE c.Purpose='CASE_OVERVIEW' AND (SELECT COUNT(*) FROM dbo.CaseDatePresentationSelections s WHERE s.CaseDatePresentationConfigurationId=c.Id)<>5) THROW 57205,'Every tenant must receive the five established Overview defaults.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseDatePresentationSelections s WHERE s.SelectionIdentity LIKE 'SYSTEM:%' AND NOT EXISTS(
  SELECT 1 FROM dbo.CaseDateTypes t WHERE (t.ShaleClientId=s.ShaleClientId OR t.ShaleClientId IS NULL) AND t.SystemKey IS NOT NULL
   AND LOWER(LTRIM(RTRIM(t.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160) AND t.IsActive=1 AND t.IsDeleted=0))
  THROW 57206,'An initial SYSTEM selection has no active visible definition.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;

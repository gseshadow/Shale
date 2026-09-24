/*
 Phase 2A reusable field-confirmation persistence foundation.
 REVIEW/APPLY MANUALLY. Forward-only, transactional, rerunnable; no policy seeds,
 confirmation requests, confirmations, or Case Date mutation behavior are added.

 Case Date confirmed business value = CaseDateTypeId + StartsAt + EndsAt + AllDay.
 Title, Notes, lifecycle metadata, actor/timestamps, and RowVer are not part of that value.
 ValueRevision is deliberately distinct from RowVer. Phase 2B write integration must increment
 it only when a constituent business-value property changes and create a requirement snapshot
 only when the then-current policy requires confirmation.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
 BEGIN TRANSACTION;

 IF OBJECT_ID(N'dbo.FormConfigurations',N'U') IS NULL OR OBJECT_ID(N'dbo.FormConfiguredFields',N'U') IS NULL
  THROW 57000,'Form configuration foundation is required.',1;
 IF OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL OR OBJECT_ID(N'dbo.CaseDates',N'U') IS NULL
  THROW 57001,'Firm-wide role and Case Date foundations are required.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57002,'Strict tenant predicate is required.',1;
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1);
 IF @policyId IS NULL THROW 57003,'Enabled TenantFilter is required.',1;

 IF COL_LENGTH(N'dbo.CaseDates',N'ValueRevision') IS NULL
  ALTER TABLE dbo.CaseDates ADD ValueRevision bigint NOT NULL
   CONSTRAINT DF_CaseDates_ValueRevision DEFAULT(1) WITH VALUES;
 IF OBJECT_ID(N'dbo.CK_CaseDates_ValueRevision',N'C') IS NULL
  ALTER TABLE dbo.CaseDates WITH CHECK ADD CONSTRAINT CK_CaseDates_ValueRevision CHECK(ValueRevision>0);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDates') AND name=N'UX_CaseDates_Id_Tenant')
  CREATE UNIQUE INDEX UX_CaseDates_Id_Tenant ON dbo.CaseDates(Id,ShaleClientId);

 /* Stable logical field identities survive FormConfigurationDao.replace-generated row IDs. */
 IF OBJECT_ID(N'dbo.FormFieldPolicyKeys',N'U') IS NULL CREATE TABLE dbo.FormFieldPolicyKeys(
  ShaleClientId int NOT NULL,FormKey varchar(64) NOT NULL,FieldKey varchar(128) NOT NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_FormFieldPolicyKeys_CreatedAt DEFAULT(SYSUTCDATETIME()),
  CONSTRAINT PK_FormFieldPolicyKeys PRIMARY KEY(ShaleClientId,FormKey,FieldKey),
  CONSTRAINT FK_FormFieldPolicyKeys_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT CK_FormFieldPolicyKeys_FormKey CHECK(LEN(LTRIM(RTRIM(FormKey)))>0),
  CONSTRAINT CK_FormFieldPolicyKeys_FieldKey CHECK(LEN(LTRIM(RTRIM(FieldKey)))>0));

 /* Register identities only; this does not enable a policy or enroll a saved value. */
 INSERT dbo.FormFieldPolicyKeys(ShaleClientId,FormKey,FieldKey)
 SELECT DISTINCT f.ShaleClientId,c.FormKey,f.FieldKey
 FROM dbo.FormConfiguredFields f JOIN dbo.FormConfigurations c
   ON c.ShaleClientId=f.ShaleClientId AND c.Id=f.FormConfigurationId
 WHERE NOT EXISTS(SELECT 1 FROM dbo.FormFieldPolicyKeys k WHERE k.ShaleClientId=f.ShaleClientId AND k.FormKey=c.FormKey AND k.FieldKey=f.FieldKey);

 /* Immutable versions: a configuration change retires one row and inserts its successor. */
 IF OBJECT_ID(N'dbo.FieldConfirmationPolicies',N'U') IS NULL CREATE TABLE dbo.FieldConfirmationPolicies(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_FieldConfirmationPolicies PRIMARY KEY,
  ShaleClientId int NOT NULL,FormKey varchar(64) NOT NULL,FieldKey varchar(128) NOT NULL,
  PolicyRevision bigint NOT NULL,RequiresConfirmation bit NOT NULL,
  RequiredFirmWideRoleDefinitionId int NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_FieldConfirmationPolicies_CreatedAt DEFAULT(SYSUTCDATETIME()),
  CreatedByUserId int NOT NULL,SupersededAt datetime2(7) NULL,SupersededByUserId int NULL,RowVer rowversion NOT NULL,
  CONSTRAINT FK_FieldConfirmationPolicies_FieldKey FOREIGN KEY(ShaleClientId,FormKey,FieldKey) REFERENCES dbo.FormFieldPolicyKeys(ShaleClientId,FormKey,FieldKey),
  CONSTRAINT FK_FieldConfirmationPolicies_RoleTenant FOREIGN KEY(RequiredFirmWideRoleDefinitionId,ShaleClientId) REFERENCES dbo.FirmWideRoleDefinitions(Id,ShaleClientId),
  CONSTRAINT FK_FieldConfirmationPolicies_CreatedByTenant FOREIGN KEY(CreatedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
  CONSTRAINT FK_FieldConfirmationPolicies_SupersededByTenant FOREIGN KEY(SupersededByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
  CONSTRAINT CK_FieldConfirmationPolicies_Revision CHECK(PolicyRevision>0),
  CONSTRAINT CK_FieldConfirmationPolicies_Role CHECK((RequiresConfirmation=0 AND RequiredFirmWideRoleDefinitionId IS NULL) OR (RequiresConfirmation=1 AND RequiredFirmWideRoleDefinitionId IS NOT NULL)),
  CONSTRAINT CK_FieldConfirmationPolicies_Lifecycle CHECK((SupersededAt IS NULL AND SupersededByUserId IS NULL) OR (SupersededAt IS NOT NULL AND SupersededByUserId IS NOT NULL)));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FieldConfirmationPolicies') AND name=N'UX_FieldConfirmationPolicies_Id_Tenant')
  CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Id_Tenant ON dbo.FieldConfirmationPolicies(Id,ShaleClientId);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FieldConfirmationPolicies') AND name=N'UX_FieldConfirmationPolicies_Field_Revision')
  CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Field_Revision ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey,PolicyRevision);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FieldConfirmationPolicies') AND name=N'UX_FieldConfirmationPolicies_Current')
  CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Current ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey) WHERE SupersededAt IS NULL;
 DECLARE @policyTrigger nvarchar(max)=N'CREATE OR ALTER TRIGGER dbo.TR_FieldConfirmationPolicies_ActiveRole ON dbo.FieldConfirmationPolicies AFTER INSERT,UPDATE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN dbo.FirmWideRoleDefinitions r ON r.Id=i.RequiredFirmWideRoleDefinitionId AND r.ShaleClientId=i.ShaleClientId
    WHERE i.RequiresConfirmation=1 AND (r.Id IS NULL OR r.IsActive=0 OR r.IsDeleted=1))
   THROW 57005,''A confirmation policy must select an active same-tenant firm-wide role.'',1;
 END';
 EXEC sys.sp_executesql @policyTrigger;

 /* One immutable policy/role snapshot for one typed saved-value revision. */
 IF OBJECT_ID(N'dbo.SavedValueConfirmationRequirements',N'U') IS NULL CREATE TABLE dbo.SavedValueConfirmationRequirements(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_SavedValueConfirmationRequirements PRIMARY KEY,
  ShaleClientId int NOT NULL,TargetType varchar(32) NOT NULL,BusinessValueRevision bigint NOT NULL,
  FieldConfirmationPolicyId bigint NOT NULL,PolicyRevisionSnapshot bigint NOT NULL,
  FormKeySnapshot varchar(64) NOT NULL,FieldKeySnapshot varchar(128) NOT NULL,
  RequiredFirmWideRoleDefinitionId int NOT NULL,
  EnteredAt datetime2(7) NOT NULL CONSTRAINT DF_SavedValueConfirmationRequirements_EnteredAt DEFAULT(SYSUTCDATETIME()),
  EnteredByUserId int NOT NULL,RowVer rowversion NOT NULL,
  CONSTRAINT FK_SavedValueConfirmationRequirements_PolicyTenant FOREIGN KEY(FieldConfirmationPolicyId,ShaleClientId) REFERENCES dbo.FieldConfirmationPolicies(Id,ShaleClientId),
  CONSTRAINT FK_SavedValueConfirmationRequirements_RoleTenant FOREIGN KEY(RequiredFirmWideRoleDefinitionId,ShaleClientId) REFERENCES dbo.FirmWideRoleDefinitions(Id,ShaleClientId),
  CONSTRAINT FK_SavedValueConfirmationRequirements_EnteredByTenant FOREIGN KEY(EnteredByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
  CONSTRAINT CK_SavedValueConfirmationRequirements_TargetType CHECK(TargetType IN('CASE_DATE')),
  CONSTRAINT CK_SavedValueConfirmationRequirements_Revision CHECK(BusinessValueRevision>0 AND PolicyRevisionSnapshot>0));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.SavedValueConfirmationRequirements') AND name=N'UX_SavedValueConfirmationRequirements_Id_Tenant')
  CREATE UNIQUE INDEX UX_SavedValueConfirmationRequirements_Id_Tenant ON dbo.SavedValueConfirmationRequirements(Id,ShaleClientId);

 IF OBJECT_ID(N'dbo.CaseDateConfirmationTargets',N'U') IS NULL CREATE TABLE dbo.CaseDateConfirmationTargets(
  ShaleClientId int NOT NULL,ConfirmationRequirementId bigint NOT NULL,CaseDateId bigint NOT NULL,BusinessValueRevision bigint NOT NULL,
  CONSTRAINT PK_CaseDateConfirmationTargets PRIMARY KEY(ShaleClientId,ConfirmationRequirementId),
  CONSTRAINT FK_CaseDateConfirmationTargets_RequirementTenant FOREIGN KEY(ConfirmationRequirementId,ShaleClientId) REFERENCES dbo.SavedValueConfirmationRequirements(Id,ShaleClientId),
  CONSTRAINT FK_CaseDateConfirmationTargets_CaseDateTenant FOREIGN KEY(CaseDateId,ShaleClientId) REFERENCES dbo.CaseDates(Id,ShaleClientId),
  CONSTRAINT CK_CaseDateConfirmationTargets_Revision CHECK(BusinessValueRevision>0));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseDateConfirmationTargets') AND name=N'UX_CaseDateConfirmationTargets_ValueRevision')
  CREATE UNIQUE INDEX UX_CaseDateConfirmationTargets_ValueRevision ON dbo.CaseDateConfirmationTargets(ShaleClientId,CaseDateId,BusinessValueRevision);

 IF OBJECT_ID(N'dbo.SavedValueConfirmations',N'U') IS NULL CREATE TABLE dbo.SavedValueConfirmations(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_SavedValueConfirmations PRIMARY KEY,
  ShaleClientId int NOT NULL,ConfirmationRequirementId bigint NOT NULL,
  ConfirmedByUserId int NOT NULL,ConfirmedAsFirmWideRoleDefinitionId int NOT NULL,
  ConfirmedAt datetime2(7) NOT NULL CONSTRAINT DF_SavedValueConfirmations_ConfirmedAt DEFAULT(SYSUTCDATETIME()),RowVer rowversion NOT NULL,
  CONSTRAINT FK_SavedValueConfirmations_RequirementTenant FOREIGN KEY(ConfirmationRequirementId,ShaleClientId) REFERENCES dbo.SavedValueConfirmationRequirements(Id,ShaleClientId),
  CONSTRAINT FK_SavedValueConfirmations_UserTenant FOREIGN KEY(ConfirmedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId),
  CONSTRAINT FK_SavedValueConfirmations_RoleTenant FOREIGN KEY(ConfirmedAsFirmWideRoleDefinitionId,ShaleClientId) REFERENCES dbo.FirmWideRoleDefinitions(Id,ShaleClientId));
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.SavedValueConfirmations') AND name=N'UX_SavedValueConfirmations_Requirement')
  CREATE UNIQUE INDEX UX_SavedValueConfirmations_Requirement ON dbo.SavedValueConfirmations(ShaleClientId,ConfirmationRequirementId);
 DECLARE @confirmationTrigger nvarchar(max)=N'CREATE OR ALTER TRIGGER dbo.TR_SavedValueConfirmations_SnapshotRole ON dbo.SavedValueConfirmations AFTER INSERT,UPDATE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.SavedValueConfirmationRequirements r ON r.Id=i.ConfirmationRequirementId AND r.ShaleClientId=i.ShaleClientId
    WHERE i.ConfirmedAsFirmWideRoleDefinitionId<>r.RequiredFirmWideRoleDefinitionId)
   THROW 57006,''Confirmation role must equal the immutable requirement role snapshot.'',1;
 END';
 EXEC sys.sp_executesql @confirmationTrigger;

 /* Enforce agreement between the reusable base snapshot and its typed target. */
 DECLARE @targetTrigger nvarchar(max)=N'CREATE OR ALTER TRIGGER dbo.TR_CaseDateConfirmationTargets_Validate ON dbo.CaseDateConfirmationTargets AFTER INSERT,UPDATE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.SavedValueConfirmationRequirements r ON r.Id=i.ConfirmationRequirementId AND r.ShaleClientId=i.ShaleClientId
    WHERE r.TargetType<>''CASE_DATE'' OR r.BusinessValueRevision<>i.BusinessValueRevision)
   THROW 57004,''Case Date target must match the requirement target type and business revision.'',1;
 END';
 EXEC sys.sp_executesql @targetTrigger;

 DECLARE @policyQualified nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 DECLARE @table sysname,@rlsSql nvarchar(max);
 DECLARE tables CURSOR LOCAL FAST_FORWARD FOR SELECT n FROM(VALUES
  (N'dbo.FormFieldPolicyKeys'),(N'dbo.FieldConfirmationPolicies'),(N'dbo.SavedValueConfirmationRequirements'),
  (N'dbo.CaseDateConfirmationTargets'),(N'dbo.SavedValueConfirmations'))v(n);
 OPEN tables; FETCH NEXT FROM tables INTO @table;
 WHILE @@FETCH_STATUS=0 BEGIN
  IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(@table) AND predicate_type_desc=N'FILTER') BEGIN
   SET @rlsSql=N'ALTER SECURITY POLICY '+@policyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON '+@table+N';';
   EXEC sys.sp_executesql @rlsSql;
  END;
  FETCH NEXT FROM tables INTO @table;
 END;
 CLOSE tables; DEALLOCATE tables;

 /* Closed rerun inventory: familiar but incompatible objects must not be accepted. */
 DECLARE @requiredColumns table(TableName sysname,ColumnName sysname);
 INSERT @requiredColumns VALUES
  (N'CaseDates',N'ValueRevision'),(N'FormFieldPolicyKeys',N'FormKey'),(N'FormFieldPolicyKeys',N'FieldKey'),
  (N'FieldConfirmationPolicies',N'PolicyRevision'),(N'FieldConfirmationPolicies',N'RequiresConfirmation'),(N'FieldConfirmationPolicies',N'RequiredFirmWideRoleDefinitionId'),
  (N'SavedValueConfirmationRequirements',N'TargetType'),(N'SavedValueConfirmationRequirements',N'BusinessValueRevision'),(N'SavedValueConfirmationRequirements',N'PolicyRevisionSnapshot'),
  (N'CaseDateConfirmationTargets',N'CaseDateId'),(N'CaseDateConfirmationTargets',N'BusinessValueRevision'),
  (N'SavedValueConfirmations',N'ConfirmedByUserId'),(N'SavedValueConfirmations',N'ConfirmedAsFirmWideRoleDefinitionId');
 IF EXISTS(SELECT 1 FROM @requiredColumns e LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
  LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName WHERE c.column_id IS NULL)
  THROW 57007,'A required Phase 2A column is missing.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id IN(OBJECT_ID(N'dbo.FieldConfirmationPolicies'),OBJECT_ID(N'dbo.SavedValueConfirmationRequirements'),OBJECT_ID(N'dbo.CaseDateConfirmationTargets'),OBJECT_ID(N'dbo.SavedValueConfirmations')) AND (is_disabled=1 OR is_not_trusted=1))
  THROW 57008,'Phase 2A foreign keys must be enabled and trusted.',1;
 IF EXISTS(SELECT 1 FROM(VALUES(OBJECT_ID(N'dbo.FormFieldPolicyKeys')),(OBJECT_ID(N'dbo.FieldConfirmationPolicies')),(OBJECT_ID(N'dbo.SavedValueConfirmationRequirements')),(OBJECT_ID(N'dbo.CaseDateConfirmationTargets')),(OBJECT_ID(N'dbo.SavedValueConfirmations')))e(ObjectId)
  OUTER APPLY(SELECT COUNT_BIG(*) PredicateCount FROM sys.security_predicates p WHERE p.object_id=@policyId AND p.target_object_id=e.ObjectId AND p.predicate_type_desc=N'FILTER'
   AND UPPER(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N'')) IN(N'SEC.FN_FILTERBYTENANT(SHALECLIENTID)',N'(SEC.FN_FILTERBYTENANT(SHALECLIENTID))'))a WHERE a.PredicateCount<>1)
  THROW 57009,'Every Phase 2A table must have exactly the strict tenant predicate.',1;

 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;

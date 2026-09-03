/*
 Phase 2 Case Team memberships and authoritative many-to-many role assignments.
 REVIEW/APPLY MANUALLY IN SSMS OR SQLCMD. Guarded, rerunnable, forward-only. It never deletes or overwrites CaseUsers.
 Execution order: Phase 1 definitions -> this file -> Phase 2 audit allowlist -> application deployment.

 The new ShaleClientId and TenantKey columns are intentionally bootstrapped before GO because SQL
 Server resolves column references for an entire batch before executing a conditional ALTER TABLE.
 If the main transaction later fails, these harmless additive columns can remain for a safe rerun.
*/
SET NOCOUNT ON; SET XACT_ABORT ON;
IF OBJECT_ID(N'dbo.CaseUsers',N'U') IS NULL THROW 56599,'Required CaseUsers table is missing.',1;
IF OBJECT_ID(N'dbo.CaseTeamRoleDefinitions',N'U') IS NULL THROW 56601,'Phase 1 CaseTeamRoleDefinitions is missing.',1;
IF COL_LENGTH(N'dbo.CaseUsers',N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.CaseTeamRoleDefinitions',N'TenantKey') IS NULL
BEGIN
 BEGIN TRY
  BEGIN TRANSACTION;
  IF COL_LENGTH(N'dbo.CaseUsers',N'ShaleClientId') IS NULL ALTER TABLE dbo.CaseUsers ADD ShaleClientId int NULL;
  IF COL_LENGTH(N'dbo.CaseTeamRoleDefinitions',N'TenantKey') IS NULL ALTER TABLE dbo.CaseTeamRoleDefinitions ADD TenantKey AS ISNULL(ShaleClientId,(0)) PERSISTED;
  COMMIT;
 END TRY
 BEGIN CATCH
  IF XACT_STATE()<>0 ROLLBACK;
  THROW;
 END CATCH;
END;
GO

SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.CaseUsers',N'U') IS NULL OR OBJECT_ID(N'dbo.Cases',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.Roles',N'U') IS NULL THROW 56600,'Required Case Team membership schema is missing.',1;
 IF OBJECT_ID(N'dbo.CaseTeamRoleDefinitions',N'U') IS NULL THROW 56601,'Phase 1 CaseTeamRoleDefinitions is missing.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 56602,'Strict tenant RLS predicate is missing.',1;

 DECLARE @required TABLE(SystemKey varchar(64) PRIMARY KEY,LegacyRoleId int,LegacyName nvarchar(200));
 INSERT @required VALUES ('responsible_attorney',4,N'responsible_attorney'),('prelitigation_staff',5,N'prelitigation'),('attorney',7,N'attorney'),('legal_assistant',11,N'legal_assistant'),('paralegal',12,N'paralegal'),('law_clerk',13,N'law_clerk'),('co_counsel',14,N'co_counsel');
 IF EXISTS(SELECT 1 FROM @required r LEFT JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.SystemKey=r.SystemKey AND d.LegacyRoleId=r.LegacyRoleId LEFT JOIN dbo.Roles legacy ON legacy.Id=r.LegacyRoleId WHERE d.Id IS NULL OR LOWER(LTRIM(RTRIM(legacy.Name)))<>r.LegacyName)
  THROW 56603,'A required global legacy bridge is missing or does not match the Phase 1 production vocabulary.',1;
 IF EXISTS(SELECT LegacyRoleId FROM dbo.CaseTeamRoleDefinitions WHERE ShaleClientId IS NULL AND LegacyRoleId IS NOT NULL GROUP BY LegacyRoleId HAVING COUNT(*)<>1)
  THROW 56604,'Global LegacyRoleId mapping is ambiguous.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseUsers cu LEFT JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId WHERE cu.RoleId IS NOT NULL AND d.Id IS NULL)
  THROW 56605,'At least one CaseUsers.RoleId has no global LegacyRoleId mapping.',1;

 UPDATE cu SET ShaleClientId=c.ShaleClientId FROM dbo.CaseUsers cu JOIN dbo.Cases c ON c.Id=cu.CaseId WHERE cu.ShaleClientId IS NULL;
 IF EXISTS(SELECT 1 FROM dbo.CaseUsers WHERE ShaleClientId IS NULL) THROW 56606,'A CaseUsers membership could not be tenant-backfilled.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseUsers cu LEFT JOIN dbo.Cases c ON c.Id=cu.CaseId WHERE c.Id IS NULL OR c.ShaleClientId IS NULL OR cu.ShaleClientId<>c.ShaleClientId) THROW 56607,'CaseUsers contains a missing, unowned, or cross-tenant Case relationship.',1;
 IF EXISTS(SELECT 1 FROM dbo.CaseUsers cu LEFT JOIN dbo.Users u ON u.id=cu.UserId WHERE u.id IS NULL OR u.ShaleClientId IS NULL OR cu.ShaleClientId<>u.ShaleClientId) THROW 56608,'CaseUsers contains a missing, unowned, or cross-tenant User relationship.',1;
 IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'ShaleClientId' AND is_nullable=1) ALTER TABLE dbo.CaseUsers ALTER COLUMN ShaleClientId int NOT NULL;
 DECLARE @caseUsersTenantColumnId int=COLUMNPROPERTY(OBJECT_ID(N'dbo.CaseUsers'),N'ShaleClientId','ColumnId');
 DECLARE @caseUsersTenantDefault nvarchar(max)=(SELECT definition FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.CaseUsers') AND parent_column_id=@caseUsersTenantColumnId);
 IF @caseUsersTenantDefault IS NULL ALTER TABLE dbo.CaseUsers ADD CONSTRAINT DF_CaseUsers_ShaleClientId DEFAULT(CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))) FOR ShaleClientId;
 ELSE IF CHARINDEX(N'SESSION_CONTEXT',UPPER(@caseUsersTenantDefault))=0 THROW 56612,'CaseUsers.ShaleClientId has an unexpected default constraint.',1;
 IF COL_LENGTH(N'dbo.CaseUsers',N'RowVer') IS NULL ALTER TABLE dbo.CaseUsers ADD RowVer rowversion NOT NULL;
 IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'RoleId' AND is_nullable=0)
 BEGIN
  DECLARE @responsibleIndexId int,@responsibleFilter nvarchar(max);
  SELECT @responsibleIndexId=index_id,@responsibleFilter=filter_definition FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'UX_CaseUsers_RespAtty_Primary' AND type_desc=N'NONCLUSTERED' AND is_unique=1 AND has_filter=1;
  IF @responsibleIndexId IS NULL OR CHARINDEX(N'[RoleId]=(4)',REPLACE(@responsibleFilter,N' ',N''))=0 OR CHARINDEX(N'[IsPrimary]=(1)',REPLACE(@responsibleFilter,N' ',N''))=0
   OR NOT EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=OBJECT_ID(N'dbo.CaseUsers') AND ic.index_id=@responsibleIndexId AND ic.key_ordinal=1 AND c.name=N'CaseId')
   OR NOT EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=OBJECT_ID(N'dbo.CaseUsers') AND ic.index_id=@responsibleIndexId AND ic.key_ordinal=2 AND c.name=N'RoleId')
   OR NOT EXISTS(SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=OBJECT_ID(N'dbo.CaseUsers') AND ic.index_id=@responsibleIndexId AND ic.key_ordinal=3 AND c.name=N'IsPrimary')
   THROW 56614,'The Responsible Attorney compatibility index does not match the expected production definition.',1;
  DROP INDEX UX_CaseUsers_RespAtty_Primary ON dbo.CaseUsers;
  ALTER TABLE dbo.CaseUsers ALTER COLUMN RoleId int NULL;
  CREATE UNIQUE NONCLUSTERED INDEX UX_CaseUsers_RespAtty_Primary ON dbo.CaseUsers(CaseId,RoleId,IsPrimary) WHERE RoleId=(4) AND IsPrimary=(1);
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'UX_CaseUsers_RespAtty_Primary' AND is_unique=1 AND has_filter=1) THROW 56615,'The Responsible Attorney compatibility index was not restored.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'FK_CaseUsers_ShaleClient') ALTER TABLE dbo.CaseUsers ADD CONSTRAINT FK_CaseUsers_ShaleClient FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Cases') AND name=N'UX_Cases_Id_Tenant') CREATE UNIQUE INDEX UX_Cases_Id_Tenant ON dbo.Cases(Id,ShaleClientId);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'UX_Users_Id_Tenant') CREATE UNIQUE INDEX UX_Users_Id_Tenant ON dbo.Users(id,ShaleClientId);
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'FK_CaseUsers_CaseTenant') ALTER TABLE dbo.CaseUsers WITH CHECK ADD CONSTRAINT FK_CaseUsers_CaseTenant FOREIGN KEY(CaseId,ShaleClientId) REFERENCES dbo.Cases(Id,ShaleClientId);
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'FK_CaseUsers_UserTenant') ALTER TABLE dbo.CaseUsers WITH CHECK ADD CONSTRAINT FK_CaseUsers_UserTenant FOREIGN KEY(UserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseUsers') AND name=N'UX_CaseUsers_Id_Tenant_Case') CREATE UNIQUE INDEX UX_CaseUsers_Id_Tenant_Case ON dbo.CaseUsers(Id,ShaleClientId,CaseId);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamRoleDefinitions') AND name=N'UX_CaseTeamRoleDefinitions_Id_TenantKey') CREATE UNIQUE INDEX UX_CaseTeamRoleDefinitions_Id_TenantKey ON dbo.CaseTeamRoleDefinitions(Id,TenantKey);

 IF OBJECT_ID(N'dbo.CaseTeamMemberRoles',N'U') IS NULL CREATE TABLE dbo.CaseTeamMemberRoles(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseTeamMemberRoles PRIMARY KEY,
  ShaleClientId int NOT NULL, CaseId int NOT NULL, CaseUserId int NOT NULL,
  CaseTeamRoleDefinitionId int NOT NULL, RoleDefinitionTenantKey int NOT NULL,
  IsDeleted bit NOT NULL CONSTRAINT DF_CaseTeamMemberRoles_IsDeleted DEFAULT(0),
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_CaseTeamMemberRoles_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
  UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, DeletedAt datetime2(7) NULL, DeletedByUserId int NULL,
  RowVer rowversion NOT NULL,
  CONSTRAINT CK_CaseTeamMemberRoles_DefinitionScope CHECK(RoleDefinitionTenantKey=0 OR RoleDefinitionTenantKey=ShaleClientId),
  CONSTRAINT CK_CaseTeamMemberRoles_Lifecycle CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)),
  CONSTRAINT FK_CaseTeamMemberRoles_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_CaseTeamMemberRoles_MembershipTenant FOREIGN KEY(CaseUserId,ShaleClientId,CaseId) REFERENCES dbo.CaseUsers(Id,ShaleClientId,CaseId),
  CONSTRAINT FK_CaseTeamMemberRoles_DefinitionTenant FOREIGN KEY(CaseTeamRoleDefinitionId,RoleDefinitionTenantKey) REFERENCES dbo.CaseTeamRoleDefinitions(Id,TenantKey),
  CONSTRAINT FK_CaseTeamMemberRoles_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT FK_CaseTeamMemberRoles_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
  CONSTRAINT FK_CaseTeamMemberRoles_DeletedBy FOREIGN KEY(DeletedByUserId) REFERENCES dbo.Users(id)
 );
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamMemberRoles') AND name=N'UX_CaseTeamMemberRoles_Active') CREATE UNIQUE INDEX UX_CaseTeamMemberRoles_Active ON dbo.CaseTeamMemberRoles(ShaleClientId,CaseUserId,CaseTeamRoleDefinitionId) WHERE IsDeleted=0;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CaseTeamMemberRoles') AND name=N'IX_CaseTeamMemberRoles_CaseRole') CREATE INDEX IX_CaseTeamMemberRoles_CaseRole ON dbo.CaseTeamMemberRoles(ShaleClientId,CaseId,CaseTeamRoleDefinitionId,IsDeleted) INCLUDE(CaseUserId);

 INSERT dbo.CaseTeamMemberRoles(ShaleClientId,CaseId,CaseUserId,CaseTeamRoleDefinitionId,RoleDefinitionTenantKey)
 SELECT cu.ShaleClientId,cu.CaseId,cu.Id,d.Id,0 FROM dbo.CaseUsers cu JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId
 WHERE cu.RoleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.CaseTeamMemberRoles a WHERE a.ShaleClientId=cu.ShaleClientId AND a.CaseUserId=cu.Id AND a.CaseTeamRoleDefinitionId=d.Id);

 /* Capture verification before strict RLS is added; the operator session may not have a tenant context. */
 DECLARE @totalMemberships bigint,@membershipsWithLegacyRoles bigint,@rolelessMemberships bigint,@totalActiveAssignments bigint,@unmappedLegacyRoles bigint,@missingLegacyBackfills bigint,@duplicateActiveAssignments bigint,@crossTenantViolations bigint;
 SELECT @totalMemberships=COUNT_BIG(*),@membershipsWithLegacyRoles=COUNT_BIG(CASE WHEN RoleId IS NOT NULL THEN 1 END),@rolelessMemberships=COUNT_BIG(CASE WHEN RoleId IS NULL THEN 1 END) FROM dbo.CaseUsers;
 SELECT @totalActiveAssignments=COUNT_BIG(*) FROM dbo.CaseTeamMemberRoles WHERE IsDeleted=0;
 SELECT @unmappedLegacyRoles=COUNT_BIG(*) FROM dbo.CaseUsers cu LEFT JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId WHERE cu.RoleId IS NOT NULL AND d.Id IS NULL;
 SELECT @missingLegacyBackfills=COUNT_BIG(*) FROM dbo.CaseUsers cu JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId WHERE cu.RoleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.CaseTeamMemberRoles a WHERE a.ShaleClientId=cu.ShaleClientId AND a.CaseUserId=cu.Id AND a.CaseTeamRoleDefinitionId=d.Id);
 SELECT @duplicateActiveAssignments=COUNT_BIG(*) FROM (SELECT ShaleClientId,CaseUserId,CaseTeamRoleDefinitionId FROM dbo.CaseTeamMemberRoles WHERE IsDeleted=0 GROUP BY ShaleClientId,CaseUserId,CaseTeamRoleDefinitionId HAVING COUNT(*)>1) duplicates;
 SELECT @crossTenantViolations=COUNT_BIG(*) FROM dbo.CaseTeamMemberRoles a JOIN dbo.CaseUsers cu ON cu.Id=a.CaseUserId JOIN dbo.CaseTeamRoleDefinitions d ON d.Id=a.CaseTeamRoleDefinitionId WHERE a.ShaleClientId<>cu.ShaleClientId OR a.CaseId<>cu.CaseId OR (d.ShaleClientId IS NOT NULL AND d.ShaleClientId<>a.ShaleClientId);
 
 DECLARE @policyId int=(SELECT object_id FROM sys.security_policies WHERE name=N'TenantFilter');
 IF @policyId IS NULL THROW 56609,'TenantFilter security policy is missing.',1;
 IF EXISTS(SELECT 1 FROM sys.security_policies WHERE object_id=@policyId AND is_enabled=0) THROW 56613,'TenantFilter security policy is disabled.',1;
 DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE object_id=@policyId);
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.CaseUsers'),OBJECT_ID(N'dbo.CaseTeamMemberRoles')) AND predicate_type_desc=N'FILTER' AND object_id<>@policyId) THROW 56610,'A Case Team table is already registered under an unexpected RLS policy.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseUsers') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseUsers;');
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id=OBJECT_ID(N'dbo.CaseTeamMemberRoles') AND predicate_type_desc=N'FILTER') EXEC(N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseTeamMemberRoles;');
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@policyId AND target_object_id IN(OBJECT_ID(N'dbo.CaseUsers'),OBJECT_ID(N'dbo.CaseTeamMemberRoles')) AND predicate_type_desc=N'FILTER' AND predicate_definition NOT LIKE N'%fn_FilterByTenant%') THROW 56611,'A Case Team table has an unexpected RLS filter predicate.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;

/* Reviewable verification captured before RLS. All four violation counts must be zero. */
SELECT @totalMemberships TotalMemberships,@membershipsWithLegacyRoles MembershipsWithLegacyRoles,@rolelessMemberships RolelessMemberships;
SELECT @totalActiveAssignments TotalActiveAssignments;
SELECT @unmappedLegacyRoles UnmappedLegacyRoles;
SELECT @missingLegacyBackfills MissingLegacyBackfills;
SELECT @duplicateActiveAssignments DuplicateActiveAssignments;
SELECT @crossTenantViolations CrossTenantViolations;

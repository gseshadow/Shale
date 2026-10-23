/* Phase 3B.3 internal shared disclosure. Manual additive migration; no enablement. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59000,'Fresh dbo migration session required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x6923b77be9c25417848a8b2ab48ff5d953626a2f52eba0245a17ba343c670474;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 59000,'Schema lock unavailable.',1;
IF OBJECT_ID(N'dbo.AuditLog',N'U') IS NULL OR EXISTS(
 SELECT 1 FROM(VALUES(N'ShaleClientId'),(N'UserId'),(N'ObjectTypeId'),(N'ObjectId'),(N'FieldName'),(N'FieldCode'),(N'StringValue'),(N'DateValue'),(N'BooleanValue'),(N'IntValue'),(N'EntryDate'))v(Name)
 WHERE COL_LENGTH(N'dbo.AuditLog',v.Name) IS NULL)
 THROW 59000,'Existing Overview PHI schema prerequisite missing.',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1'),(N'ResolveAiRefreshV1'),(N'AiIssueCredentialsV1'),(N'AiRefreshCredentialsV1'),(N'AiLifecycle_Session'),(N'AiLifecycle_Credential'),(N'AiCascade_Grant'),(N'AiCascade_Key'),(N'AiCascade_Registration'),(N'AiCascade_Web'),(N'AiCascade_User'),(N'AiCascade_Tenant'); INSERT @Owned VALUES (N'ResolveAiRevocationV1'),(N'AiLoadRevocationClientV1'),(N'AiRevokeOAuthCredentialV1');
INSERT @Owned VALUES (N'ResolveAiAccessV1'),(N'AiValidateAccessV1');
DECLARE @catalog nvarchar(max)=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');
DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiDisclosureV1');
IF @receipt IS NULL BEGIN
 IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1'),0x)
 THROW 59000,'Reviewed ACCESS catalog required.',1;
 IF OBJECT_ID(N'dbo.IntegrationAccessAuditLog') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.IntegrationAccessAuditResource') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiAppendCaseAccessV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiReviewCaseAccessV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiGateCaseReadV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiFinishCaseQuotaV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiAppendOnly_Access') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiAppendOnly_Resource') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiOverviewPhiCountV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
 IF OBJECT_ID(N'dbo.AiAccountCaseAttemptV1') IS NOT NULL THROW 59000,'Unreceipted disclosure object.',1;
END;
INSERT @Owned VALUES (N'IntegrationAccessAuditLog'),(N'IntegrationAccessAuditResource'),(N'AiAppendCaseAccessV1'),(N'AiReviewCaseAccessV1'),(N'AiGateCaseReadV1'),(N'AiFinishCaseQuotaV1'),(N'AiAppendOnly_Access'),(N'AiAppendOnly_Resource'),(N'AiOverviewPhiCountV1'),(N'AiAccountCaseAttemptV1');
SET @catalog=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');
IF @receipt IS NOT NULL BEGIN
 IF @receipt<>HASHBYTES('SHA2_256',@catalog) OR @sourceHash<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiDisclosureV1.Source'),0x)
 THROW 59000,'Disclosure catalog/source drift.',1;
 COMMIT TRANSACTION; RETURN;
END;
CREATE TABLE dbo.IntegrationAccessAuditLog (
 Id bigint IDENTITY NOT NULL CONSTRAINT PK_IntegrationAccessAudit PRIMARY KEY,
 ShaleClientId int NOT NULL, InvocationId uniqueidentifier NOT NULL, CorrelationId uniqueidentifier NOT NULL,
 UserId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, GrantId uniqueidentifier NOT NULL, SessionId uniqueidentifier NOT NULL,
 ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 OccurredAt datetime2(7) NOT NULL CONSTRAINT DF_IntegrationAccess_Time DEFAULT SYSUTCDATETIME(),
 Source varchar(3) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationAccess_Source CHECK(Source='MCP'),
 Contract varchar(12) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationAccess_Contract CHECK(Contract='case-read-v1'),
 Tool varchar(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Operation varchar(28) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Outcome varchar(14) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationAccess_Outcome CHECK(Outcome IN('ALLOWED_READ','DENIED','INVALID_INPUT','UNAVAILABLE','THROTTLED','CANCELLED')),
 Reason varchar(20) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationAccess_Reason CHECK(Reason IN('NONE','AUTHORITY','INPUT','READ','AUDIT','COMMIT','CLOSE','SERIALIZATION','OVERSIZED','DEADLINE','QUOTA','CANCELLED','CASE_UNAVAILABLE')),
 Stage varchar(8) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationAccess_Stage CHECK(Stage IN('READ','TERMINAL')),
 ReturnedCount tinyint NOT NULL CONSTRAINT CK_IntegrationAccess_Count CHECK(ReturnedCount BETWEEN 0 AND 10),
 SearchPage tinyint NULL, SearchSize tinyint NULL,
 CONSTRAINT CK_IntegrationAccess_ExactEnums CHECK(DATALENGTH(Tool)=LEN(Tool) AND DATALENGTH(Operation)=LEN(Operation) AND DATALENGTH(Outcome)=LEN(Outcome) AND DATALENGTH(Reason)=LEN(Reason) AND DATALENGTH(Stage)=LEN(Stage)),
 CONSTRAINT UQ_IntegrationAccess_Stage UNIQUE(InvocationId,Stage),
 CONSTRAINT UQ_IntegrationAccess_Tenant UNIQUE(ShaleClientId,Id),
 CONSTRAINT CK_IntegrationAccess_Mapping CHECK((Tool='search_cases' AND Operation='SEARCH_MINIMIZED_CASES') OR (Tool='get_case_overview' AND Operation='READ_MINIMIZED_CASE_OVERVIEW') OR (Tool='UNSUPPORTED_TOOL' AND Operation='UNSUPPORTED')),
 CONSTRAINT CK_IntegrationAccess_Page CHECK((Tool='search_cases' AND SearchSize IS NOT NULL AND SearchSize=10 AND ((SearchPage IS NOT NULL AND SearchPage BETWEEN 0 AND 9) OR (SearchPage IS NULL AND Outcome<>'ALLOWED_READ'))) OR (Tool<>'search_cases' AND SearchPage IS NULL AND SearchSize IS NULL)),
 CONSTRAINT CK_IntegrationAccess_Success CHECK((Outcome='ALLOWED_READ' AND Reason='NONE' AND Stage='READ' AND Tool<>'UNSUPPORTED_TOOL' AND (Tool='search_cases' OR ReturnedCount=1)) OR (Outcome<>'ALLOWED_READ' AND Reason<>'NONE' AND ReturnedCount=0)),
 CONSTRAINT FK_IntegrationAccess_User FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_IntegrationAccess_Grant FOREIGN KEY(ShaleClientId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegationGrant(ShaleClientId,GrantId,IntegrationId,UserId),
 CONSTRAINT FK_IntegrationAccess_Session FOREIGN KEY(ShaleClientId,SessionId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegatedSession(ShaleClientId,SessionId,GrantId,IntegrationId,UserId)
);
CREATE INDEX IX_IntegrationAccess_Review ON dbo.IntegrationAccessAuditLog(ShaleClientId,Id) INCLUDE(OccurredAt);
CREATE TABLE dbo.IntegrationAccessAuditResource (
 ShaleClientId int NOT NULL, EventId bigint NOT NULL, CaseId int NOT NULL CONSTRAINT CK_IntegrationResource_Id CHECK(CaseId>0),
 Relation varchar(9) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT CK_IntegrationResource_Relation CHECK(Relation IN('RETURNED','REQUESTED') AND DATALENGTH(Relation)=LEN(Relation)),
 CONSTRAINT PK_IntegrationResource PRIMARY KEY(ShaleClientId,EventId,CaseId,Relation),
 CONSTRAINT FK_IntegrationResource_Parent FOREIGN KEY(ShaleClientId,EventId) REFERENCES dbo.IntegrationAccessAuditLog(ShaleClientId,Id)
);
EXEC(N'CREATE TRIGGER dbo.AiAppendOnly_Access ON dbo.IntegrationAccessAuditLog AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; THROW 59001,''Append-only integration history.'',1; END;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditLog, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditLog AFTER INSERT, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditLog AFTER UPDATE;');
EXEC(N'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.IntegrationAccessAuditLog TO AiIntegrationRuntime; DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.IntegrationAccessAuditLog TO AiIntegrationBootstrap;');
EXEC(N'CREATE TRIGGER dbo.AiAppendOnly_Resource ON dbo.IntegrationAccessAuditResource AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; THROW 59001,''Append-only integration history.'',1; END;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditResource, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditResource AFTER INSERT, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntegrationAccessAuditResource AFTER UPDATE;');
EXEC(N'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.IntegrationAccessAuditResource TO AiIntegrationRuntime; DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.IntegrationAccessAuditResource TO AiIntegrationBootstrap;');
EXEC(N'CREATE PROCEDURE dbo.AiGateCaseReadV1
 @AccessCredentialId bigint,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,
 @ClientId varchar(max),@KeyId uniqueidentifier,@Issuer nvarchar(max),@Resource nvarchar(max),@PriorScopes tinyint,@RequiredScope tinyint,@CapabilityRevision bigint,@PolicyRevision bigint,@IntegrationRevision bigint,@ExpectedGrantRevision bigint,
 @InvocationId uniqueidentifier,@InvocationDeadline datetime2(7),@Handoff bit=0
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>1 THROW 58901,''Owning read transaction required.'',1;
 EXEC dbo.AiLockV1 @Exclusive=@Handoff;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @revision bigint,@policy bigint,@registration bigint,@grantRevision bigint,@held bigint;
 IF @t IS NULL OR @u IS NULL OR @AccessCredentialId IS NULL OR @IntegrationId IS NULL OR @GrantId IS NULL OR @SessionId IS NULL
 OR @Handoff IS NULL OR @RequiredScope IS NULL OR @CapabilityRevision IS NULL OR @PolicyRevision IS NULL OR @IntegrationRevision IS NULL OR @ExpectedGrantRevision IS NULL OR @InvocationId IS NULL
 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 OR @PriorScopes IS NULL OR @PriorScopes>3
 THROW 58901,''Integration authentication failed.'',1;
 -- Fixed gate order; existing mutators conflict on capability/tenant locks before descendant rows.
 SELECT @revision=Revision FROM dbo.AiProductCapability WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1;
 SELECT @policy=Revision FROM dbo.TenantAiIntegrationPolicy WITH(HOLDLOCK) WHERE ShaleClientId=@t AND Enabled=1;
 SELECT @registration=Revision FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId;
 SELECT @grantRevision=Revision FROM dbo.AiDelegationGrant WITH(HOLDLOCK) WHERE ShaleClientId=@t AND GrantId=@GrantId;
 SELECT @held=Id FROM dbo.AiDelegatedSession WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;
 SELECT @held=id FROM dbo.Users WITH(HOLDLOCK) WHERE ShaleClientId=@t AND id=@u;
 -- Read the full tuple under held locks, then sample database UTC AFTER every possible lock wait.
 DECLARE @mask tinyint,@deadline datetime2(7),@keyStart datetime2(7);
 SELECT @mask=(c.ScopeMask & s.ScopeMask & g.ScopeMask & i.ScopeMask & @PriorScopes & 3),@deadline=d.Deadline,@keyStart=k.ValidFrom
 FROM dbo.AiOAuthCredential c WITH(HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.UserId=c.UserId AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.SessionId=c.SessionId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=s.ShaleClientId AND g.UserId=s.UserId AND g.IntegrationId=s.IntegrationId AND g.GrantId=s.GrantId
 JOIN dbo.AiIntegrationRegistration i WITH(HOLDLOCK) ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 JOIN dbo.AiAuthorizationTransaction a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.TransactionId=s.TransactionId AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId AND a.GrantId=s.GrantId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=s.ShaleClientId AND w.UserId=s.UserId AND w.SessionId=a.WebSessionId
 JOIN dbo.Users u WITH(HOLDLOCK) ON u.ShaleClientId=s.ShaleClientId AND u.id=s.UserId
 JOIN dbo.ShaleClients t WITH(HOLDLOCK) ON t.Id=s.ShaleClientId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(c.ExpiresAt),(s.ExpiresAt),(s.IdleExpiresAt),(g.ExpiresAt),(i.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,s.ExpiresAt)))v(Expiry))d
 WHERE c.Id=@AccessCredentialId AND c.ShaleClientId=@t AND c.UserId=@u AND c.IntegrationId=@IntegrationId AND c.GrantId=@GrantId AND c.SessionId=@SessionId
 AND c.Kind=''ACCESS'' AND c.RevokedAt IS NULL AND c.ConsumedAt IS NULL
 AND s.RevokedAt IS NULL AND s.CapabilityRevision=@revision AND s.ConsentAt=g.ConsentedAt AND c.Generation<=s.RefreshGeneration
 AND g.RevokedAt IS NULL AND i.Status=''ACTIVE'' AND i.RevokedAt IS NULL
 AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0 AND t.IsActive=1
 AND w.ClientType=''WEB'' AND w.RevokedAt IS NULL AND a.ConsumedAt IS NOT NULL AND a.DeniedAt IS NULL
 AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)
 AND s.ClientId=i.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(i.ClientId) AND s.ClientId=a.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(a.ClientId)
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Issuer=i.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(i.Issuer) AND s.Issuer=a.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(a.Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource)
 AND s.Resource=i.Resource AND DATALENGTH(s.Resource)=DATALENGTH(i.Resource) AND s.Resource=a.Resource AND DATALENGTH(s.Resource)=DATALENGTH(a.Resource)
 AND ((i.ClientType=''PUBLIC'' AND s.CredentialId IS NULL AND @KeyId IS NULL AND a.CredentialId IS NULL)
 OR (i.ClientType=''CONFIDENTIAL'' AND s.CredentialId=@KeyId AND s.CredentialId=a.CredentialId AND k.RevokedAt IS NULL AND k.Algorithm=''RS256''));
 DECLARE @leaseExpiry datetime2(7);
 SELECT @leaseExpiry=LeaseExpiresAt FROM dbo.AiQuotaReservation WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId
 AND GrantId=@GrantId AND SessionId=@SessionId AND InvocationId=@InvocationId AND Admitted=1 AND ReleasedAt IS NULL
 AND RowCost=CASE WHEN @RequiredScope=1 THEN 10 ELSE 1 END;
 -- Final handoff owns the tenant quota lock before authority rows, then releases concurrency
 -- BEFORE the final clock sample. No housekeeping lock wait may follow that sample.
 IF @Handoff=1 BEGIN
  UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME()
  WHERE ShaleClientId=@t AND UserId=@u AND InvocationId=@InvocationId AND ReleasedAt IS NULL;
  IF @@ROWCOUNT<>1 THROW 58901,''Single handoff reservation required.'',1;
 END;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @revision IS NULL OR @policy IS NULL OR @registration IS NULL OR @grantRevision IS NULL OR @mask IS NULL OR @mask=0
 OR @revision<>@CapabilityRevision OR @policy<>@PolicyRevision OR @registration<>@IntegrationRevision OR @grantRevision<>@ExpectedGrantRevision
 OR @RequiredScope NOT IN(1,2) OR (@mask & @RequiredScope)<>@RequiredScope OR @InvocationDeadline IS NULL OR @InvocationDeadline<=@now
 OR @leaseExpiry IS NULL OR @leaseExpiry<=@now
 OR @deadline IS NULL OR @deadline<=@now OR (@keyStart IS NOT NULL AND @keyStart>@now)
 THROW 58901,''Integration authentication failed.'',1;

 SELECT @mask AS ScopeMask,@deadline AS Deadline,@revision AS CapabilityRevision,@policy AS PolicyRevision,
 @registration AS IntegrationRevision,@grantRevision AS GrantRevision, DATEDIFF_BIG(millisecond,@now,CASE WHEN @deadline<@InvocationDeadline THEN @deadline ELSE @InvocationDeadline END) AS RemainingMillis;

END;');
EXEC(N'CREATE PROCEDURE dbo.AiAppendCaseAccessV1
 @InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,
 @ClientId varchar(max),@Tool varchar(max),@Stage varchar(max),@Outcome varchar(max),@Reason varchar(max),@ReturnedCount int,@Page int,@Requested int,@Case0 int=NULL,@Case1 int=NULL,@Case2 int=NULL,@Case3 int=NULL,@Case4 int=NULL,@Case5 int=NULL,@Case6 int=NULL,@Case7 int=NULL,@Case8 int=NULL,@Case9 int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>1 THROW 59002,''Owning audit transaction required.'',1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId''));
 -- Retained ownership only: disabled/revoked owners must still be auditable. No Case lookup.
 IF @t IS NULL OR @u IS NULL OR @InvocationId IS NULL OR @CorrelationId IS NULL OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64
 OR NOT EXISTS(SELECT 1 FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ClientId)=DATALENGTH(@ClientId))
 THROW 59002,''Verified audit owner required.'',1;
 -- An uncertain commit can have either outcome. Never overwrite or replay a READ event.
 IF @Stage=''TERMINAL'' AND NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WITH(UPDLOCK,HOLDLOCK) WHERE InvocationId=@InvocationId AND Stage=''READ'') SET @Stage=''READ'';
 DECLARE @ids table(CaseId int PRIMARY KEY);
 INSERT @ids SELECT v.Id FROM(VALUES(@Case0),(@Case1),(@Case2),(@Case3),(@Case4),(@Case5),(@Case6),(@Case7),(@Case8),(@Case9))v(Id) WHERE v.Id IS NOT NULL;
 IF @ReturnedCount IS NULL OR @ReturnedCount<>(SELECT COUNT(*) FROM @ids) OR EXISTS(SELECT 1 FROM @ids WHERE CaseId<=0)
 OR (@Requested IS NOT NULL AND (@Requested<=0 OR @Tool<>''get_case_overview'' OR @Outcome=''ALLOWED_READ''))
 OR (@Outcome<>''ALLOWED_READ'' AND EXISTS(SELECT 1 FROM @ids))
 OR (@Stage=''TERMINAL'' AND NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND CorrelationId=@CorrelationId AND Stage=''READ'' AND Outcome=''ALLOWED_READ''))
 THROW 59002,''Bounded audit resource shape required.'',1;
 INSERT dbo.IntegrationAccessAuditLog(ShaleClientId,InvocationId,CorrelationId,UserId,IntegrationId,GrantId,SessionId,ClientId,Source,Contract,Tool,Operation,Outcome,Reason,Stage,ReturnedCount,SearchPage,SearchSize)
 VALUES(@t,@InvocationId,@CorrelationId,@u,@IntegrationId,@GrantId,@SessionId,@ClientId,''MCP'',''case-read-v1'',@Tool,
 CASE @Tool WHEN ''search_cases'' THEN ''SEARCH_MINIMIZED_CASES'' WHEN ''get_case_overview'' THEN ''READ_MINIMIZED_CASE_OVERVIEW'' WHEN ''UNSUPPORTED_TOOL'' THEN ''UNSUPPORTED'' ELSE '''' END,
 @Outcome,@Reason,@Stage,@ReturnedCount,@Page,CASE WHEN @Tool=''search_cases'' THEN 10 END);
 IF @@ROWCOUNT<>1 THROW 59002,''Required parent append unavailable.'',1;
 DECLARE @event bigint=SCOPE_IDENTITY(),@expected int=@ReturnedCount+CASE WHEN @Requested IS NULL THEN 0 ELSE 1 END;
 IF @event IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE Id=@event AND ShaleClientId=@t AND InvocationId=@InvocationId AND Stage=@Stage)
 THROW 59002,''Required parent persistence unavailable.'',1;
 INSERT dbo.IntegrationAccessAuditResource(ShaleClientId,EventId,CaseId,Relation)
 SELECT @t,@event,CaseId,''RETURNED'' FROM @ids UNION ALL SELECT @t,@event,@Requested,''REQUESTED'' WHERE @Requested IS NOT NULL;
 IF @@ROWCOUNT<>@expected OR (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE ShaleClientId=@t AND EventId=@event)<>@expected
 THROW 59002,''Required resource append unavailable.'',1;
 SELECT 1 AS AppendedCount;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiOverviewPhiCountV1 @CaseId int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>1 OR @CaseId IS NULL OR @CaseId<=0 THROW 59002,''Owning Overview audit transaction required.'',1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId''));
 IF @t IS NULL OR @u IS NULL THROW 59002,''Overview audit owner required.'',1;
 -- Narrow verification only. No generic PHI viewer, content, timestamp linkage or extra success append.
 SELECT COUNT_BIG(*) AS AuditCount FROM dbo.AuditLog WITH(UPDLOCK,HOLDLOCK)
 WHERE ShaleClientId=@t AND UserId=@u AND ObjectTypeId=1 AND ObjectId=@CaseId
 AND FieldName=''Case.Overview.Read'' AND TRY_CONVERT(int,FieldCode)=4
 AND StringValue=''action=READ;screen=Case.Overview'';
END;');
EXEC(N'CREATE PROCEDURE dbo.AiAccountCaseAttemptV1
 @InvocationId uniqueidentifier,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@ClientId varchar(max),@RequiredScope tinyint
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>1 OR @InvocationId IS NULL OR @RequiredScope IS NULL OR @RequiredScope NOT IN(1,2) THROW 59002,''Bounded attempt accounting required.'',1;
 EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId''));
 IF NOT EXISTS(SELECT 1 FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ClientId)=DATALENGTH(@ClientId))
 THROW 59002,''Verified attempt owner required.'',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId) BEGIN
  DECLARE @now datetime2(7)=SYSUTCDATETIME();
  INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt)
  VALUES(@t,@IntegrationId,@u,@GrantId,@SessionId,@InvocationId,CASE WHEN @RequiredScope=1 THEN ''SEARCH'' ELSE ''OVERVIEW'' END,0,0,@now,DATEADD(second,15,@now));
 END;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiFinishCaseQuotaV1 @InvocationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>1 THROW 59002,''Owning quota transaction required.'',1;
 EXEC dbo.AiLockV1;
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME()
 WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND UserId=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')) AND InvocationId=@InvocationId AND ReleasedAt IS NULL;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiReviewCaseAccessV1 @After bigint,@Size int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR @After IS NULL OR @After<0 OR @Size IS NULL OR @Size NOT BETWEEN 1 AND 100 THROW 59003,''Bounded audit review required.'',1;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiActorV1 @Admin=1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId''));
 SELECT TOP(@Size) Id INTO #page FROM dbo.IntegrationAccessAuditLog WHERE ShaleClientId=@t AND Id>@After ORDER BY Id;
 SELECT a.Id,a.OccurredAt,a.ShaleClientId,a.UserId,a.IntegrationId,a.GrantId,a.SessionId,a.ClientId,a.Tool,a.Operation,a.Outcome,a.Reason,a.Stage,a.ReturnedCount,a.SearchPage,a.SearchSize,a.CorrelationId,a.InvocationId,r.CaseId,r.Relation
 FROM #page p JOIN dbo.IntegrationAccessAuditLog a ON a.Id=p.Id AND a.ShaleClientId=@t
 LEFT JOIN dbo.IntegrationAccessAuditResource r ON r.EventId=a.Id AND r.ShaleClientId=a.ShaleClientId
 ORDER BY a.Id,r.CaseId,r.Relation;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
GRANT EXECUTE ON OBJECT::dbo.AiGateCaseReadV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiAppendCaseAccessV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiFinishCaseQuotaV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiReviewCaseAccessV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiOverviewPhiCountV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiAccountCaseAttemptV1 TO AiIntegrationRuntime;
SET @catalog=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');
DECLARE @hash varbinary(32)=HASHBYTES('SHA2_256',@catalog);
EXEC sys.sp_addextendedproperty @name=N'Shale.AiDisclosureV1',@value=@hash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiDisclosureV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

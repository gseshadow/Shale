/* Phase 3B.2C.2 additive manual-only candidate. Install only in approved disposable SQL for verification. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58700,'Fresh dbo migration session required.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x4463497b7476f047263a3e6dfb3abe28c9c251633ecec19a87eb2bcf255f3ddc;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 58700,'AI schema lock unavailable.',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1'),(N'ResolveAiRefreshV1'),(N'AiIssueCredentialsV1'),(N'AiRefreshCredentialsV1'),(N'AiLifecycle_Session'),(N'AiLifecycle_Credential'),(N'AiCascade_Grant'),(N'AiCascade_Key'),(N'AiCascade_Registration'),(N'AiCascade_Web'),(N'AiCascade_User'),(N'AiCascade_Tenant');
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
DECLARE @fingerprint varbinary(32)=HASHBYTES('SHA2_256',@catalog);


DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1');
IF @receipt IS NOT NULL BEGIN
 IF @receipt<>@fingerprint OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1.Source'),0x)<>@sourceHash
 THROW 58700,'Credential catalog/source drift; stop for review.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF COL_LENGTH(N'dbo.AiDelegatedSession',N'TransactionId') IS NOT NULL OR OBJECT_ID(N'dbo.ResolveAiRefreshV1') IS NOT NULL
 THROW 58700,'Unreceipted lifecycle objects; stop for review.',1;
DECLARE @FoundationOwned table(Name sysname PRIMARY KEY); INSERT @FoundationOwned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation');

DECLARE @foundationCatalog nvarchar(max)=N'';
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @FoundationOwned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @FoundationOwned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @FoundationOwned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @foundationCatalog=@foundationCatalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @foundationCatalog=@foundationCatalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');
DECLARE @foundationFingerprint varbinary(32)=HASHBYTES('SHA2_256',@foundationCatalog);

DECLARE @foundationReceipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability') AND minor_id=0 AND name=N'Shale.AiFoundationV1');
IF @foundationReceipt IS NULL OR @foundationReceipt<>@foundationFingerprint OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability') AND name=N'Shale.AiFoundationV1.Source'),0x)<>0x04080764f9057fbe992e44929f1199f0425964961ef42e6970d35c71ebc22b83
 THROW 58500,'AI foundation catalog drift; stop for review.',1;

DELETE @Owned; INSERT @Owned VALUES (N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1');
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
SET @fingerprint=HASHBYTES('SHA2_256',@catalog);


IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiAssertionReplay') AND minor_id=0 AND name=N'Shale.AiExchangeV1'),0x)<>@fingerprint
 OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiAssertionReplay') AND minor_id=0 AND name=N'Shale.AiExchangeV1.Source'),0x)<>0x1584ea785ed8741e093ac8646293895f4f147226bcd402f43ed8d25cb034ecf9
 THROW 58700,'Reviewed exchange catalog/source required.',1;
EXEC(N'ALTER TABLE dbo.AiDelegatedSession ADD TransactionId uniqueidentifier NULL,ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NULL,
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NULL,Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NULL,
 ConsentAt datetime2(7) NULL,CapabilityRevision bigint NULL');
EXEC(N'CREATE UNIQUE INDEX UX_AiSession_Transaction ON dbo.AiDelegatedSession(TransactionId) WHERE TransactionId IS NOT NULL');
EXEC(N'ALTER TABLE dbo.AiAuthorizationTransaction ADD CONSTRAINT UQ_AiAuthorization_Lineage UNIQUE(ShaleClientId,TransactionId,GrantId,IntegrationId,UserId)');
EXEC(N'ALTER TABLE dbo.AiDelegatedSession ADD CONSTRAINT FK_AiSession_Authorization FOREIGN KEY(ShaleClientId,TransactionId,GrantId,IntegrationId,UserId)
 REFERENCES dbo.AiAuthorizationTransaction(ShaleClientId,TransactionId,GrantId,IntegrationId,UserId)');
EXEC(N'ALTER TABLE dbo.AiDelegatedSession ADD CONSTRAINT CK_AiSession_ConsentDeadline CHECK(TransactionId IS NULL OR
 (ClientId IS NOT NULL AND Issuer IS NOT NULL AND Resource IS NOT NULL AND ConsentAt IS NOT NULL AND CapabilityRevision IS NOT NULL AND ExpiresAt<=DATEADD(hour,24,ConsentAt)))');
EXEC(N'-- Generation n''s immutable parent is generation n-1 in the same tenant/session. Existing unique REFRESH generation
-- reconstructs every parent/replacement without mutable descendant pointers or deleting consumed history.');
EXEC(N'ALTER PROCEDURE dbo.AiExchangeOAuthCodeV1
@CodeHash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@KeyId uniqueidentifier,@JtiHash varbinary(max),
 @AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL
 OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 OR DATALENGTH(@RedirectUri) NOT BETWEEN 1 AND 4096
 OR DATALENGTH(@Challenge)<>43 THROW 58602,''Invalid exchange binding.'',1;
 DECLARE @WebSessionId uniqueidentifier;
 SELECT @WebSessionId=WebSessionId FROM dbo.AiAuthorizationTransaction WHERE ShaleClientId=@t AND UserId=@u AND CodeHash=@CodeHash;
 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58602,''WEB authority unavailable.'',1;
 DECLARE @TransactionId uniqueidentifier;
 SELECT @TransactionId=TransactionId FROM dbo.AiAuthorizationTransaction WITH(UPDLOCK,HOLDLOCK)
 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND CodeHash=@CodeHash AND ConsumedAt IS NULL
 AND CodeExpiresAt>@now AND ClientId=@ClientId AND DATALENGTH(ClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer AND DATALENGTH(Issuer)=DATALENGTH(@Issuer) AND Resource=@Resource AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND RedirectUri=@RedirectUri AND DATALENGTH(RedirectUri)=DATALENGTH(@RedirectUri) AND Challenge=@Challenge COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Challenge)=DATALENGTH(@Challenge);

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiAuthorizationTransaction WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status=''ACTIVE'' AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,''OAuth authority unavailable.'',1;

 IF NOT EXISTS(SELECT 1 FROM dbo.AiAuthorizationTransaction a JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK)
 ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND g.RevokedAt IS NULL AND g.ExpiresAt>@now
 AND (g.ScopeMask & a.ApprovedScopeMask)=a.ApprovedScopeMask) THROW 58502,''Grant unavailable.'',1;
 SET @now=SYSUTCDATETIME();
 DECLARE @type varchar(16),@deadline datetime2(7),@grantId uniqueidentifier,@grantRow bigint;
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@i;
 IF (@type=''PUBLIC'' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type=''CONFIDENTIAL'' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)))
 THROW 58602,''Client authentication unavailable.'',1;
 IF @type=''CONFIDENTIAL'' BEGIN
 -- Bounded cleanup under the same exclusive tenant lock. No valid-window record can be removed.
 DELETE TOP(256) FROM dbo.AiAssertionReplay WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiAssertionReplay(ShaleClientId,IntegrationId,CredentialId,JtiHash,AcceptedAt,ExpiresAt)
 VALUES(@t,@i,@KeyId,@JtiHash,@now,@AssertionExpiresAt);
 END;
 SELECT @grantId=g.GrantId,@grantRow=g.Id,@deadline=d.Deadline FROM dbo.AiAuthorizationTransaction a
 JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.UserId=a.UserId AND g.IntegrationId=a.IntegrationId
 JOIN dbo.AiIntegrationRegistration r ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.UserSessions w ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),
 (CASE WHEN @key IS NULL THEN g.ExpiresAt ELSE (SELECT ExpiresAt FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key) END))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u;
 IF @deadline IS NULL OR @deadline<=SYSUTCDATETIME() OR (@key IS NOT NULL AND @AssertionExpiresAt<=SYSUTCDATETIME()) THROW 58602,''Exchange deadline elapsed.'',1;
 UPDATE dbo.AiAuthorizationTransaction SET ConsumedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL AND CodeExpiresAt>SYSUTCDATETIME();
 IF @@ROWCOUNT<>1 THROW 58503,''Code unavailable.'',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATION_GRANT'',@grantRow,''UPDATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',
 (SELECT ApprovedScopeMask FROM dbo.AiAuthorizationTransaction WHERE TransactionId=@TransactionId AND ShaleClientId=@t),N''}''));
 IF @@ROWCOUNT<>1 THROW 58603,''Exchange audit unavailable.'',1;
 SELECT @t AS ShaleClientId,@u AS UserId,@i AS IntegrationId,@ClientId AS ClientId,@grantId AS GrantId,
 TransactionId,ApprovedScopeMask,@KeyId AS CredentialId,@Issuer AS Issuer,@Resource AS Resource,@deadline AS Deadline,@CorrelationId AS CorrelationId
 FROM dbo.AiAuthorizationTransaction WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u;
 -- Internal validation result only. Never create a session/access/refresh credential here.
 IF @owns=1 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'ALTER PROCEDURE dbo.AiVerifySessionV1
@SessionId uniqueidentifier,@RequiredScope tinyint
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1 @Exclusive=0;

 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@integration uniqueidentifier,@grant uniqueidentifier,@mask tinyint;
 SELECT @integration=s.IntegrationId,@grant=s.GrantId,@mask=(s.ScopeMask & g.ScopeMask & i.ScopeMask)
 FROM dbo.AiDelegatedSession s
 JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId AND g.IntegrationId=s.IntegrationId AND g.UserId=s.UserId
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 LEFT JOIN dbo.AiIntegrationClientKey k ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 WHERE s.ShaleClientId=@t AND s.UserId=@u AND s.SessionId=@SessionId
 AND s.RevokedAt IS NULL AND s.ExpiresAt>@now AND s.IdleExpiresAt>@now
 AND (s.CapabilityRevision IS NULL OR s.CapabilityRevision=(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1))
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND i.Status=''ACTIVE'' AND i.ExpiresAt>@now
 AND ((i.ClientType=''PUBLIC'' AND s.CredentialId IS NULL) OR (i.ClientType=''CONFIDENTIAL'' AND k.RevokedAt IS NULL AND k.CredentialId IS NOT NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @mask IS NULL OR @RequiredScope IS NULL OR @RequiredScope NOT IN(1,2) OR (@mask & @RequiredScope)<>@RequiredScope
 THROW 58408,''AI delegation unavailable.'',1;

 SELECT @t ShaleClientId,@u UserId,@integration IntegrationId,@grant GrantId,@SessionId SessionId,@mask ScopeMask;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'ALTER PROCEDURE dbo.AiReserveQuotaV1
@SessionId uniqueidentifier,@RequiredScope tinyint,@InvocationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1;

 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@integration uniqueidentifier,@grant uniqueidentifier,@mask tinyint;
 SELECT @integration=s.IntegrationId,@grant=s.GrantId,@mask=(s.ScopeMask & g.ScopeMask & i.ScopeMask)
 FROM dbo.AiDelegatedSession s
 JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId AND g.IntegrationId=s.IntegrationId AND g.UserId=s.UserId
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 LEFT JOIN dbo.AiIntegrationClientKey k ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 WHERE s.ShaleClientId=@t AND s.UserId=@u AND s.SessionId=@SessionId
 AND s.RevokedAt IS NULL AND s.ExpiresAt>@now AND s.IdleExpiresAt>@now
 AND (s.CapabilityRevision IS NULL OR s.CapabilityRevision=(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1))
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND i.Status=''ACTIVE'' AND i.ExpiresAt>@now
 AND ((i.ClientType=''PUBLIC'' AND s.CredentialId IS NULL) OR (i.ClientType=''CONFIDENTIAL'' AND k.RevokedAt IS NULL AND k.CredentialId IS NOT NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @mask IS NULL OR @RequiredScope IS NULL OR @RequiredScope NOT IN(1,2) OR (@mask & @RequiredScope)<>@RequiredScope
 THROW 58408,''AI delegation unavailable.'',1;

 IF @InvocationId IS NULL THROW 58406,''AI invocation required.'',1;
 DECLARE @cost int=CASE WHEN @RequiredScope=1 THEN 10 ELSE 1 END,@admit bit=1;
 IF EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId) THROW 58407,''AI reservation already accounted.'',1;
 SELECT @admit=CASE WHEN
  COALESCE(SUM(CONVERT(int,RowCost)),0)+@cost>1000 OR
  COALESCE(SUM(CASE WHEN UserId=@u THEN CONVERT(int,RowCost) ELSE 0 END),0)+@cost>200 OR
  COALESCE(SUM(CASE WHEN IntegrationId=@integration THEN CONVERT(int,RowCost) ELSE 0 END),0)+@cost>500 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) THEN 1 END)>=60 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) AND UserId=@u THEN 1 END)>=10 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) AND IntegrationId=@integration THEN 1 END)>=20 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now THEN 1 END)>=5 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now AND UserId=@u THEN 1 END)>=1 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now AND IntegrationId=@integration THEN 1 END)>=2
 THEN 0 ELSE 1 END FROM dbo.AiQuotaReservation WHERE ShaleClientId=@t AND AdmittedAt>DATEADD(hour,-24,@now);
 INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt)
 VALUES(@t,@integration,@u,@grant,@SessionId,@InvocationId,CASE WHEN @RequiredScope=1 THEN ''SEARCH'' ELSE ''OVERVIEW'' END,
 CASE WHEN @admit=1 THEN @cost ELSE 0 END,@admit,@now,DATEADD(second,15,@now));
 SELECT @admit Admitted,@InvocationId InvocationId;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.ResolveAiRefreshV1
 @Hash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64
 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512
 THROW 58701,''Refresh unavailable.'',1;
 SELECT c.ShaleClientId,c.UserId FROM dbo.AiOAuthCredential c
 JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId AND s.GrantId=c.GrantId AND s.IntegrationId=c.IntegrationId AND s.UserId=c.UserId
 WHERE c.CredentialHash=@Hash AND c.Kind=''REFRESH'' AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
END;');
EXEC(N'CREATE PROCEDURE dbo.AiIssueCredentialsV1
 @TransactionId uniqueidentifier,@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@GrantId uniqueidentifier,
 @KeyId uniqueidentifier,@Scopes tinyint,@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 -- This seam is callable only within the validated exchange transaction. It never accepts a caller tenant/user.
 IF @@TRANCOUNT<>1 THROW 58702,''Validated exchange transaction required.'',1;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();
 IF @AccessHash IS NULL OR @RefreshHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR DATALENGTH(@RefreshHash)<>32 OR @AccessHash=@RefreshHash
 OR @CorrelationId IS NULL OR @Scopes IS NULL OR @Scopes NOT BETWEEN 1 AND 3 THROW 58702,''Credential binding unavailable.'',1;
 DECLARE @i uniqueidentifier,@deadline datetime2(7),@consent datetime2(7),@s uniqueidentifier=NEWID(),@revision bigint;
 SELECT @i=a.IntegrationId,@consent=g.ConsentedAt,@deadline=d.Deadline
 FROM dbo.AiAuthorizationTransaction a WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=a.ShaleClientId AND k.IntegrationId=a.IntegrationId AND k.CredentialId=a.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(DATEADD(hour,24,g.ConsentedAt)),(COALESCE(k.ExpiresAt,g.ExpiresAt)))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.GrantId=@GrantId AND a.ConsumedAt IS NOT NULL
 AND a.ApprovedScopeMask=@Scopes AND a.DeniedAt IS NULL AND r.RowVer=a.RegistrationVersion
 AND a.ClientId=@ClientId AND DATALENGTH(a.ClientId)=DATALENGTH(@ClientId) AND r.ClientId=a.ClientId
 AND a.Issuer=@Issuer AND DATALENGTH(a.Issuer)=DATALENGTH(@Issuer) AND r.Issuer=a.Issuer
 AND a.Resource=@Resource AND DATALENGTH(a.Resource)=DATALENGTH(@Resource) AND r.Resource=a.Resource
 AND r.Status=''ACTIVE'' AND r.RevokedAt IS NULL AND r.ExpiresAt>@now AND (r.ScopeMask & @Scopes)=@Scopes
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND (g.ScopeMask & @Scopes)=@Scopes
 AND w.ClientType=''WEB'' AND w.RevokedAt IS NULL AND w.ExpiresAt>@now
 AND ((r.ClientType=''PUBLIC'' AND @KeyId IS NULL AND a.CredentialId IS NULL) OR
 (r.ClientType=''CONFIDENTIAL'' AND @KeyId=a.CredentialId AND k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @i IS NULL OR @deadline<=SYSUTCDATETIME() THROW 58702,''Exchange authority unavailable.'',1;
 SELECT @revision=Revision FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(minute,5,@now) THEN @deadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(hour,8,@now) THEN @deadline ELSE DATEADD(hour,8,@now) END;
 INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialId,ScopeMask,IssuedAt,ExpiresAt,IdleExpiresAt,
 TransactionId,ClientId,Issuer,Resource,ConsentAt,CapabilityRevision)
 VALUES(@t,@i,@u,@GrantId,@s,@KeyId,@Scopes,@now,@deadline,@refreshExpiry,@TransactionId,@ClientId,@Issuer,@Resource,@consent,@revision);
 INSERT dbo.AiOAuthCredential(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@GrantId,@s,@AccessHash,''ACCESS'',@Scopes,0,@now,@accessExpiry),(@t,@i,@u,@GrantId,@s,@RefreshHash,''REFRESH'',@Scopes,0,@now,@refreshExpiry);
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT @t,@u,''AI_DELEGATED_SESSION'',Id,''CREATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@Scopes,N''}'')
 FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND SessionId=@s;
 IF @@ROWCOUNT<>1 THROW 58703,''Credential audit unavailable.'',1;
 SELECT @now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@Scopes AS ScopeMask;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRefreshCredentialsV1
 @Hash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@KeyId uniqueidentifier,
 @JtiHash varbinary(max),@AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @AccessHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR @RefreshHash IS NULL OR DATALENGTH(@RefreshHash)<>32
 OR @Hash=@AccessHash OR @Hash=@RefreshHash OR @AccessHash=@RefreshHash OR @CorrelationId IS NULL
 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 THROW 58702,''Credential unavailable.'',1;
 DECLARE @s uniqueidentifier,@id bigint,@sessionRow bigint,@i uniqueidentifier,@g uniqueidentifier,@scopes tinyint,@generation int,
 @consumed datetime2(7),@credentialExpiry datetime2(7),@deadline datetime2(7),@idle datetime2(7),@revoked datetime2(7),@type varchar(16),@key uniqueidentifier,@revision bigint;
 SELECT @s=s.SessionId,@sessionRow=s.Id,@id=c.Id,@i=s.IntegrationId,@g=s.GrantId,@scopes=s.ScopeMask,@generation=c.Generation,
 @consumed=c.ConsumedAt,@credentialExpiry=c.ExpiresAt,@deadline=s.ExpiresAt,@idle=s.IdleExpiresAt,@revoked=s.RevokedAt,@key=s.CredentialId,@revision=s.CapabilityRevision
 FROM dbo.AiOAuthCredential c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId AND s.GrantId=c.GrantId AND s.IntegrationId=c.IntegrationId AND s.UserId=c.UserId
 WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.CredentialHash=@Hash AND c.Kind=''REFRESH'' AND c.ScopeMask=s.ScopeMask
 AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i AND ClientId=@ClientId;
 IF @s IS NULL OR @type IS NULL OR (@type=''PUBLIC'' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type=''CONFIDENTIAL'' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now)))
 THROW 58702,''Client unavailable.'',1;
 IF @type=''CONFIDENTIAL'' BEGIN
 DELETE TOP(256) FROM dbo.AiAssertionReplay WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiAssertionReplay VALUES(@t,@i,@key,@JtiHash,@now,@AssertionExpiresAt);
 END;
 -- Reuse is checked even after token expiry. Required audit commits BEFORE denial is returned.
 IF @consumed IS NOT NULL OR @revision<>(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1) BEGIN
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=CASE WHEN @consumed IS NOT NULL THEN ''REUSE'' ELSE ''DISABLED'' END WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;
 IF @@ROWCOUNT=1 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATED_SESSION'',@sessionRow,''DEACTIVATED'',@CorrelationId,''SHALE_SERVICE'',N''{"ACTIVE":false}'');
 IF @@ROWCOUNT<>1 THROW 58703,''Revocation audit unavailable.'',1;
 END;
 COMMIT TRANSACTION;
 SELECT CAST(1 AS bit) AS Denied;
 RETURN;
 END;
 DECLARE @liveDeadline datetime2(7);
 SELECT @liveDeadline=d.Deadline
 FROM dbo.AiDelegatedSession s JOIN dbo.AiAuthorizationTransaction a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.TransactionId=s.TransactionId AND a.GrantId=s.GrantId AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=s.ShaleClientId AND r.IntegrationId=s.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId AND g.UserId=s.UserId AND g.IntegrationId=s.IntegrationId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=s.ShaleClientId AND w.UserId=s.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(s.ExpiresAt),(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,s.ExpiresAt)))v(Expiry))d
 WHERE s.ShaleClientId=@t AND s.SessionId=@s AND s.RevokedAt IS NULL AND s.RefreshGeneration=@generation
 AND r.ClientId=s.ClientId AND r.Issuer=s.Issuer AND r.Resource=s.Resource AND r.Status=''ACTIVE'' AND r.RevokedAt IS NULL
 AND (r.ScopeMask & @scopes)=@scopes AND g.RevokedAt IS NULL AND (g.ScopeMask & @scopes)=@scopes
 AND w.ClientType=''WEB'' AND w.RevokedAt IS NULL AND (s.CredentialId IS NULL OR (k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 SET @now=SYSUTCDATETIME();
 IF @revoked IS NOT NULL OR @credentialExpiry<=@now OR @idle<=@now OR @liveDeadline IS NULL OR @liveDeadline<=@now
 OR (@key IS NOT NULL AND @AssertionExpiresAt<=@now) THROW 58702,''Refresh authority unavailable.'',1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(minute,5,@now) THEN @liveDeadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(hour,8,@now) THEN @liveDeadline ELSE DATEADD(hour,8,@now) END;
 UPDATE dbo.AiOAuthCredential SET ConsumedAt=@now WHERE ShaleClientId=@t AND Id=@id AND ConsumedAt IS NULL AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @@ROWCOUNT<>1 THROW 58702,''Refresh unavailable.'',1;
 INSERT dbo.AiOAuthCredential(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@g,@s,@AccessHash,''ACCESS'',@scopes,@generation+1,@now,@accessExpiry),(@t,@i,@u,@g,@s,@RefreshHash,''REFRESH'',@scopes,@generation+1,@now,@refreshExpiry);
 UPDATE dbo.AiDelegatedSession SET RefreshGeneration=@generation+1,LastRefreshedAt=@now,IdleExpiresAt=@refreshExpiry WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL AND RefreshGeneration=@generation;
 IF @@ROWCOUNT<>1 THROW 58702,''Refresh concurrency conflict.'',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATED_SESSION'',@sessionRow,''UPDATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@scopes,N''}''));
 IF @@ROWCOUNT<>1 THROW 58703,''Rotation audit unavailable.'',1;
 COMMIT TRANSACTION;
 SELECT CAST(0 AS bit) AS Denied,@now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@scopes AS ScopeMask;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE TRIGGER dbo.AiLifecycle_Session ON dbo.AiDelegatedSession AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 IF UPDATE(TransactionId) OR UPDATE(ClientId) OR UPDATE(Issuer) OR UPDATE(Resource) OR UPDATE(ConsentAt) OR UPDATE(CapabilityRevision)
 THROW 58704,''Credential lineage is immutable.'',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE
 (d.RevokedAt IS NOT NULL AND ISNULL(i.RevocationReason,'''')<>d.RevocationReason) OR i.RefreshGeneration<d.RefreshGeneration
 OR (d.LastRefreshedAt IS NOT NULL AND (i.LastRefreshedAt IS NULL OR i.LastRefreshedAt<d.LastRefreshedAt)))
 THROW 58704,''Credential lifecycle is monotonic.'',1;
 UPDATE c SET RevokedAt=i.RevokedAt FROM dbo.AiOAuthCredential c JOIN inserted i ON i.ShaleClientId=c.ShaleClientId AND i.SessionId=c.SessionId
 WHERE i.RevokedAt IS NOT NULL AND c.RevokedAt IS NULL;
END;');
EXEC(N'CREATE TRIGGER dbo.AiLifecycle_Credential ON dbo.AiOAuthCredential AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.AiOAuthCredential p ON p.ShaleClientId=i.ShaleClientId AND p.SessionId=i.SessionId AND p.Generation=i.Generation-1 AND p.Kind=''REFRESH''
 WHERE i.Kind=''REFRESH'' AND i.Generation>0 AND p.ConsumedAt IS NULL) THROW 58704,''Refresh lineage unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_Grant ON dbo.AiDelegationGrant AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.ShaleClientId
 WHERE s.RevokedAt IS NULL AND s.GrantId=i.GrantId AND i.RevokedAt IS NOT NULL;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_Key ON dbo.AiIntegrationClientKey AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.ShaleClientId
 WHERE s.RevokedAt IS NULL AND s.IntegrationId=i.IntegrationId AND s.CredentialId=i.CredentialId AND i.RevokedAt IS NOT NULL;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_Registration ON dbo.AiIntegrationRegistration AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.ShaleClientId
 WHERE s.RevokedAt IS NULL AND s.IntegrationId=i.IntegrationId AND (i.RevokedAt IS NOT NULL OR i.Status<>''ACTIVE'');
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_Web ON dbo.UserSessions AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.ShaleClientId
 WHERE s.RevokedAt IS NULL AND EXISTS(SELECT 1 FROM dbo.AiAuthorizationTransaction a WHERE a.ShaleClientId=s.ShaleClientId AND a.TransactionId=s.TransactionId AND a.WebSessionId=i.SessionId) AND i.RevokedAt IS NOT NULL;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_User ON dbo.Users AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.ShaleClientId
 WHERE s.RevokedAt IS NULL AND s.UserId=i.id AND (COALESCE(i.is_deleted,0)=1 OR COALESCE(i.IsRemoved,0)=1);
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiCascade_Tenant ON dbo.ShaleClients AFTER UPDATE AS BEGIN
 SET NOCOUNT ON;
 DECLARE @affected table(Id bigint,ShaleClientId int,UserId int);
 UPDATE s SET RevokedAt=SYSUTCDATETIME(),RevocationReason=''SECURITY''
 OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId INTO @affected
 FROM dbo.AiDelegatedSession s JOIN inserted i ON s.ShaleClientId=i.Id
 WHERE s.RevokedAt IS NULL AND s.ShaleClientId=i.Id AND i.IsActive=0;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT ShaleClientId,COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),UserId),''AI_DELEGATED_SESSION'',Id,''DEACTIVATED'',NEWID(),''SHALE_SERVICE'',N''{"ACTIVE":false}'' FROM @affected;
 IF @@ROWCOUNT<>(SELECT COUNT(*) FROM @affected) THROW 58703,''Security revocation audit unavailable.'',1;
END;');
GRANT EXECUTE ON OBJECT::dbo.ResolveAiRefreshV1 TO AiIntegrationBootstrap;
GRANT EXECUTE ON OBJECT::dbo.AiIssueCredentialsV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRefreshCredentialsV1 TO AiIntegrationRuntime;
DELETE @Owned; INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1'),(N'ResolveAiRefreshV1'),(N'AiIssueCredentialsV1'),(N'AiRefreshCredentialsV1'),(N'AiLifecycle_Session'),(N'AiLifecycle_Credential'),(N'AiCascade_Grant'),(N'AiCascade_Key'),(N'AiCascade_Registration'),(N'AiCascade_Web'),(N'AiCascade_User'),(N'AiCascade_Tenant');
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
SET @fingerprint=HASHBYTES('SHA2_256',@catalog);


EXEC sys.sp_addextendedproperty @name=N'Shale.AiCredentialsV1',@value=@fingerprint,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiCredentialsV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

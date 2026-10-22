/* Phase 3B.2C.1 additive, manual-only candidate. No issuance; disposable synthetic SQL only. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58600,'Fresh dbo migration session required.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x1584ea785ed8741e093ac8646293895f4f147226bcd402f43ed8d25cb034ecf9;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 58600,'AI schema lock unavailable.',1;
IF OBJECT_ID(N'dbo.AiAuthorizationTransaction',N'U') IS NULL
 THROW 58600,'Reviewed 3B.2B foundation required.',1;
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

DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1');
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

IF OBJECT_ID(N'dbo.AiAssertionReplay',N'U') IS NOT NULL BEGIN
 DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiAssertionReplay') AND minor_id=0 AND name=N'Shale.AiExchangeV1');
 IF @receipt IS NULL OR @receipt<>@fingerprint OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiAssertionReplay') AND minor_id=0 AND name=N'Shale.AiExchangeV1.Source'),0x)<>@sourceHash THROW 58600,'Exchange catalog drift; stop for review.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF OBJECT_ID(N'dbo.ResolveAiOAuthCodeV1') IS NOT NULL OR OBJECT_ID(N'dbo.AiExchangeOAuthCodeV1') IS NOT NULL
 THROW 58600,'Unreceipted exchange objects; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiLoadOAuthClientV1')),NCHAR(13),N''))<>0xe79b8f5317ad1ff013168bd0e4c2f4b5fc2e5d5af59fc417d542d3f2b6867b4b OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiLoadOAuthClientV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiBeginOAuthV1')),NCHAR(13),N''))<>0xcf9f489d5e6556901b1858fa877f732830318939c05ebd95503a8ff696f28231 OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiBeginOAuthV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiFindOAuthIntentV1')),NCHAR(13),N''))<>0xd18f2181a9651943798498a070f32dc5cd0a90aef8eea800b83322783dbcd55b OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiFindOAuthIntentV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiApproveOAuthV1')),NCHAR(13),N''))<>0x941146372d1e653deddd4248ff3fb7f7d77516b81dd54a0ced34fae0d91ba797 OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiApproveOAuthV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiCompleteOAuthV1')),NCHAR(13),N''))<>0x01d1c378601f6314a24a8503e165ea4e4f592ddbe33649f6d771fd1a4e699788 OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiCompleteOAuthV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiDenyOAuthV1')),NCHAR(13),N''))<>0x0de420b1a2425869d34cda136a73b2cbc07d423fbd45c7710c42087c4dea1b37 OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiDenyOAuthV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiConsumeOAuthCodeV1')),NCHAR(13),N''))<>0xaaa109d9d570e1e646b71cca16dd536bea82935190ae75dccfec27cd70e7d06c OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiConsumeOAuthCodeV1')) IS NULL
 THROW 58600,'3B.2B module drift; stop for review.',1;
IF (SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.AiAuthorizationTransaction') AND sp.is_enabled=1 AND p.predicate_definition LIKE N'%fn_FilterByTenant%')<>3
 OR EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(SELECT Name FROM @Owned) AND principal_id IS NOT NULL AND principal_id<>DATABASE_PRINCIPAL_ID(N'dbo'))
 THROW 58600,'3B.2B ownership/RLS unavailable.',1;
IF HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'dbo.AiImmutable_AiAuthorizationTransaction')),NCHAR(13),N''))<>0x081cf9caf0a9b5c5732dc204e3388547d0bae377653ec6dd0ed6381b16a1cca7
 OR NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.AiImmutable_AiAuthorizationTransaction') AND is_disabled=0)
 OR EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.AiAuthorizationTransaction') AND (is_disabled=1 OR is_not_trusted=1))
 THROW 58600,'3B.2B immutable/FK drift; stop for review.',1;
CREATE TABLE dbo.AiAssertionReplay(
 ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, CredentialId uniqueidentifier NOT NULL,
 JtiHash binary(32) NOT NULL, AcceptedAt datetime2(7) NOT NULL, ExpiresAt datetime2(7) NOT NULL,
 CONSTRAINT PK_AiAssertionReplay PRIMARY KEY(ShaleClientId,IntegrationId,CredentialId,JtiHash),
 FOREIGN KEY(ShaleClientId,IntegrationId,CredentialId) REFERENCES dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId),
 CHECK(ExpiresAt>AcceptedAt AND ExpiresAt<=DATEADD(second,60,AcceptedAt))
);
CREATE INDEX IX_AiAssertionReplay_Expiry ON dbo.AiAssertionReplay(ShaleClientId,ExpiresAt);
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAssertionReplay;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAssertionReplay AFTER INSERT;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAssertionReplay AFTER UPDATE;');
EXEC(N'CREATE PROCEDURE dbo.ResolveAiOAuthCodeV1
 @CodeHash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 OR DATALENGTH(@CodeHash)<>32 OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64
 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512
 THROW 58601,''Code owner unavailable.'',1;
 SELECT ShaleClientId,UserId FROM dbo.AiAuthorizationTransaction
 WHERE CodeHash=@CodeHash AND ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND CodeExpiresAt>SYSUTCDATETIME() AND ConsumedAt IS NULL AND DeniedAt IS NULL;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiExchangeOAuthCodeV1
@CodeHash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@KeyId uniqueidentifier,@JtiHash varbinary(max),
 @AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
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
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAssertionReplay TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAssertionReplay TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAssertionReplay TO AiCapabilityOperator;
GRANT EXECUTE ON OBJECT::dbo.ResolveAiOAuthCodeV1 TO AiIntegrationBootstrap;
GRANT EXECUTE ON OBJECT::dbo.AiExchangeOAuthCodeV1 TO AiIntegrationRuntime;
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

EXEC sys.sp_addextendedproperty @name=N'Shale.AiExchangeV1',@value=@fingerprint,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiAssertionReplay';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiExchangeV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiAssertionReplay';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

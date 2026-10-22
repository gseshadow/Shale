/* Phase 3B.2B additive CANDIDATE. Manual disposable verification only. Never startup/deploy. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58500,'Fresh dbo migration session required.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 58500,'AI schema lock unavailable.',1;
IF OBJECT_ID(N'dbo.AiConsentIntent',N'U') IS NULL OR OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL
 OR NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE major_id=OBJECT_ID(N'dbo.AiProductCapability') AND name=N'Shale.AiFoundationV1')
 THROW 58500,'Reviewed AI foundation and durable WEB session schema required.',1;
IF OBJECT_ID(N'dbo.AiAuthorizationTransaction') IS NOT NULL THROW 58500,'Already installed or unreceipted OAuth object; stop for review.',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation');

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

DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability') AND minor_id=0 AND name=N'Shale.AiFoundationV1');
IF @receipt IS NULL OR @receipt<>@fingerprint OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability') AND name=N'Shale.AiFoundationV1.Source'),0x)<>0x04080764f9057fbe992e44929f1199f0425964961ef42e6970d35c71ebc22b83
 THROW 58500,'AI foundation catalog drift; stop for review.',1;
CREATE TABLE dbo.AiAuthorizationTransaction(
 TransactionId uniqueidentifier NOT NULL CONSTRAINT PK_AiAuthorizationTransaction PRIMARY KEY,
 ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL, WebSessionId uniqueidentifier NOT NULL,
 IntentHash binary(32) NOT NULL CONSTRAINT UQ_AiAuthorizationTransaction_Intent UNIQUE,
 ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL, Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 RedirectUri nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Challenge varchar(43) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ClientState nvarchar(512) COLLATE Latin1_General_100_BIN2 NOT NULL,
 RequestedScopeMask tinyint NOT NULL CHECK(RequestedScopeMask BETWEEN 1 AND 3),
 ApprovedScopeMask tinyint NULL CHECK(ApprovedScopeMask BETWEEN 1 AND 3),
 RegistrationVersion binary(8) NOT NULL, CredentialId uniqueidentifier NULL,
 CreatedAt datetime2(7) NOT NULL, IntentExpiresAt datetime2(7) NOT NULL,
 GrantId uniqueidentifier NULL, CodeHash binary(32) NULL, IssuedAt datetime2(7) NULL, CodeExpiresAt datetime2(7) NULL,
 ConsumedAt datetime2(7) NULL, DeniedAt datetime2(7) NULL,
 FOREIGN KEY(IntentHash) REFERENCES dbo.AiConsentIntent(IntentHash),
 FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId),
 FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,id),
 FOREIGN KEY(ShaleClientId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegationGrant(ShaleClientId,GrantId,IntegrationId,UserId),
 FOREIGN KEY(ShaleClientId,IntegrationId,CredentialId) REFERENCES dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId),
 CHECK(IntentExpiresAt=DATEADD(minute,5,CreatedAt)),
 CHECK((CodeHash IS NULL AND IssuedAt IS NULL AND CodeExpiresAt IS NULL AND GrantId IS NULL AND ConsumedAt IS NULL)
 OR (CodeHash IS NOT NULL AND IssuedAt IS NOT NULL AND CodeExpiresAt=DATEADD(second,60,IssuedAt) AND GrantId IS NOT NULL AND ApprovedScopeMask IS NOT NULL)),
 CHECK(ApprovedScopeMask IS NULL OR (ApprovedScopeMask & RequestedScopeMask)=ApprovedScopeMask)
);
CREATE UNIQUE INDEX UX_AiAuthorizationTransaction_Code ON dbo.AiAuthorizationTransaction(CodeHash) WHERE CodeHash IS NOT NULL;
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAuthorizationTransaction;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAuthorizationTransaction AFTER INSERT;');
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiAuthorizationTransaction AFTER UPDATE;');
EXEC(N'CREATE PROCEDURE dbo.AiLoadOAuthClientV1
@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512
 THROW 58501,''Invalid client identity.'',1;
 SELECT i.ShaleClientId,i.IntegrationId,i.ClientId,i.Name,i.ClientType,i.ScopeMask,i.Status,i.ExpiresAt,i.RowVer,i.Issuer,i.Resource,
 r.RedirectUri,k.CredentialId,k.Modulus,k.Exponent,k.Algorithm,k.ValidFrom,k.ExpiresAt AS KeyExpiresAt
 FROM dbo.AiIntegrationRegistration i
 JOIN dbo.AiIntegrationRedirect r ON r.ShaleClientId=i.ShaleClientId AND r.IntegrationId=i.IntegrationId
 LEFT JOIN dbo.AiIntegrationClientKey k ON k.ShaleClientId=i.ShaleClientId AND k.IntegrationId=i.IntegrationId
 AND k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now
 WHERE i.ShaleClientId=@t AND i.ClientId=@ClientId COLLATE Latin1_General_100_BIN2
 AND i.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND i.Resource=@Resource COLLATE Latin1_General_100_BIN2
 AND i.Status=''ACTIVE'' AND i.RevokedAt IS NULL AND i.ExpiresAt>@now AND i.ScopeMask BETWEEN 1 AND 3
 AND (i.ClientType=''PUBLIC'' OR k.CredentialId IS NOT NULL);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiBeginOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ClientId varchar(64),@Issuer nvarchar(256),@Resource nvarchar(256),
 @RedirectUri nvarchar(2048),@Challenge varchar(43),@ClientState nvarchar(512),@ScopeMask tinyint,@IntentHash varbinary(32)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 DECLARE @i uniqueidentifier,@v binary(8),@key uniqueidentifier,@expiry datetime2(7),@type varchar(16);
 SELECT @i=IntegrationId,@v=RowVer,@expiry=ExpiresAt,@type=ClientType FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK)
 WHERE ShaleClientId=@t AND ClientId=@ClientId AND Issuer=@Issuer AND Resource=@Resource AND Status=''ACTIVE'' AND RevokedAt IS NULL
 AND ExpiresAt>DATEADD(hour,1,@now) AND (ScopeMask & @ScopeMask)=@ScopeMask;
 IF @i IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 OR @ScopeMask IS NULL OR @IntentHash IS NULL OR DATALENGTH(@IntentHash)<>32
 OR @TransactionId IS NULL OR @ClientState IS NULL OR DATALENGTH(@ClientState)=0 OR DATALENGTH(@Challenge)<>43
 OR @Challenge COLLATE Latin1_General_100_BIN2 LIKE ''%[^A-Za-z0-9_-]%''
 THROW 58502,''OAuth binding unavailable.'',1;
 -- The adapter checks canonical URI syntax; SQL independently confines hosted and native loopback targets.
 IF NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRedirect r WHERE r.ShaleClientId=@t AND r.IntegrationId=@i AND
 (r.RedirectUri=@RedirectUri OR (@type=''PUBLIC'' AND
 ((LEFT(r.RedirectUri,17)=N''http://127.0.0.1:'' AND LEFT(@RedirectUri,17)=N''http://127.0.0.1:'') OR
 (LEFT(r.RedirectUri,13)=N''http://[::1]:'' AND LEFT(@RedirectUri,13)=N''http://[::1]:'')) AND
 CHARINDEX(N''/'',@RedirectUri,8)>0 AND CHARINDEX(N''/'',r.RedirectUri,8)>0 AND
 SUBSTRING(@RedirectUri,CHARINDEX(N''/'',@RedirectUri,8),2048)=SUBSTRING(r.RedirectUri,CHARINDEX(N''/'',r.RedirectUri,8),2048) AND
 TRY_CONVERT(int,SUBSTRING(@RedirectUri,CASE WHEN LEFT(@RedirectUri,17)=N''http://127.0.0.1:'' THEN 18 ELSE 14 END,
 CASE WHEN CHARINDEX(N''/'',@RedirectUri,8)>CASE WHEN LEFT(@RedirectUri,17)=N''http://127.0.0.1:'' THEN 18 ELSE 14 END THEN CHARINDEX(N''/'',@RedirectUri,8)-CASE WHEN LEFT(@RedirectUri,17)=N''http://127.0.0.1:'' THEN 18 ELSE 14 END ELSE 0 END)) BETWEEN 1 AND 65535)))
 THROW 58502,''OAuth redirect unavailable.'',1;
 IF @type=''CONFIDENTIAL'' BEGIN
 SELECT TOP(1) @key=CredentialId FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now ORDER BY ValidFrom DESC,CredentialId;
 IF @key IS NULL THROW 58502,''Client key unavailable.'',1;
 END;
 INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt)
 VALUES(@t,@i,@u,@IntentHash,@ScopeMask,@now,DATEADD(minute,5,@now),DATEADD(hour,1,@now));
 INSERT dbo.AiAuthorizationTransaction(TransactionId,ShaleClientId,IntegrationId,UserId,WebSessionId,IntentHash,ClientId,Issuer,Resource,
 RedirectUri,Challenge,ClientState,RequestedScopeMask,RegistrationVersion,CredentialId,CreatedAt,IntentExpiresAt)
 VALUES(@TransactionId,@t,@i,@u,@WebSessionId,@IntentHash,@ClientId,@Issuer,@Resource,@RedirectUri,@Challenge,@ClientState,@ScopeMask,@v,@key,@now,DATEADD(minute,5,@now));

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiFindOAuthIntentV1
@IntentHash varbinary(32),@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 SELECT * FROM dbo.AiAuthorizationTransaction WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId
 AND IntentHash=@IntentHash AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL
 AND EXISTS(SELECT 1 FROM dbo.AiConsentIntent c WHERE c.IntentHash=@IntentHash AND c.ShaleClientId=@t AND c.UserId=@u AND c.ConsumedAt IS NULL);

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiApproveOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiAuthorizationTransaction WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status=''ACTIVE'' AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,''OAuth authority unavailable.'',1;

 IF @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 OR (@ScopeMask & @scopes)<>@ScopeMask THROW 58502,''Invalid consent scope.'',1;
 UPDATE dbo.AiAuthorizationTransaction SET ApprovedScopeMask=@ScopeMask WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND ApprovedScopeMask IS NULL AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,''Consent already used or expired.'',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiCompleteOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint,@CodeHash varbinary(32),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiAuthorizationTransaction WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status=''ACTIVE'' AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,''OAuth authority unavailable.'',1;

 DECLARE @intent binary(32),@expiry datetime2(7),@grant uniqueidentifier,@id bigint;
 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL THROW 58502,''Invalid code binding.'',1;
 SELECT @intent=a.IntentHash,@expiry=c.GrantExpiresAt FROM dbo.AiAuthorizationTransaction a
 JOIN dbo.AiConsentIntent c ON c.ShaleClientId=a.ShaleClientId AND c.IntentHash=a.IntentHash AND c.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.ApprovedScopeMask=@ScopeMask
 AND a.IntentExpiresAt>@now AND a.IssuedAt IS NULL AND a.DeniedAt IS NULL AND c.ConsumedAt IS NULL AND c.ExpiresAt>@now;
 IF @intent IS NULL OR @expiry<=DATEADD(second,60,@now)
 OR EXISTS(SELECT 1 FROM dbo.AiDelegationGrant WHERE ShaleClientId=@t AND IntegrationId=@i AND UserId=@u AND RevokedAt>=@created)
 THROW 58503,''Stale or used consent.'',1;
 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE IntentHash=@intent AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,''Consent already used.'',1;
 INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,ScopeMask,ConsentedAt,ExpiresAt)
 VALUES(@t,@i,@u,@ScopeMask,@now,@expiry);
 SET @id=SCOPE_IDENTITY();
 SELECT @grant=GrantId FROM dbo.AiDelegationGrant WHERE Id=@id AND ShaleClientId=@t;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATION_GRANT'',@id,''CREATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@ScopeMask,N''}''));
 IF @@ROWCOUNT<>1 THROW 58504,''Consent audit unavailable.'',1;
 UPDATE dbo.AiAuthorizationTransaction SET GrantId=@grant,CodeHash=@CodeHash,IssuedAt=@now,CodeExpiresAt=DATEADD(second,60,@now)
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND IssuedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,''Code already completed.'',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiDenyOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL
 AND IntentHash IN(SELECT IntentHash FROM dbo.AiAuthorizationTransaction WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL);
 UPDATE dbo.AiAuthorizationTransaction SET DeniedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,''Consent unavailable.'',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiConsumeOAuthCodeV1
@CodeHash varbinary(32),@WebSessionId uniqueidentifier,@ClientId varchar(64),@Issuer nvarchar(256),@Resource nvarchar(256),
 @RedirectUri nvarchar(2048),@Challenge varchar(43)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,''WEB session unavailable.'',1;

 DECLARE @TransactionId uniqueidentifier;
 SELECT @TransactionId=TransactionId FROM dbo.AiAuthorizationTransaction WITH(UPDLOCK,HOLDLOCK)
 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND CodeHash=@CodeHash AND ConsumedAt IS NULL
 AND CodeExpiresAt>@now AND ClientId=@ClientId AND DATALENGTH(ClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer AND DATALENGTH(Issuer)=DATALENGTH(@Issuer) AND Resource=@Resource AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND RedirectUri=@RedirectUri AND DATALENGTH(RedirectUri)=DATALENGTH(@RedirectUri) AND Challenge=@Challenge;

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
 UPDATE dbo.AiAuthorizationTransaction SET ConsumedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL AND CodeExpiresAt>@now;
 IF @@ROWCOUNT<>1 THROW 58503,''Code unavailable.'',1;
 SELECT TransactionId,GrantId,ApprovedScopeMask FROM dbo.AiAuthorizationTransaction WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u;
 -- Validation receipt only. No token/session/credential creation exists in this module.

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiAuthorizationTransaction ON dbo.AiAuthorizationTransaction AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58505,''OAuth history cannot be deleted.'',1;
 IF UPDATE(TransactionId) OR UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(WebSessionId)
 OR UPDATE(IntentHash) OR UPDATE(ClientId) OR UPDATE(Issuer) OR UPDATE(Resource) OR UPDATE(RedirectUri) OR UPDATE(Challenge)
 OR UPDATE(ClientState) OR UPDATE(RequestedScopeMask) OR UPDATE(RegistrationVersion) OR UPDATE(CredentialId) OR UPDATE(CreatedAt) OR UPDATE(IntentExpiresAt)
 THROW 58505,''OAuth binding is immutable.'',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.TransactionId=d.TransactionId WHERE
 (d.ConsumedAt IS NOT NULL AND (i.ConsumedAt IS NULL OR i.ConsumedAt<>d.ConsumedAt)) OR
 (d.DeniedAt IS NOT NULL AND (i.DeniedAt IS NULL OR i.DeniedAt<>d.DeniedAt)) OR
 (d.ApprovedScopeMask IS NOT NULL AND (i.ApprovedScopeMask IS NULL OR i.ApprovedScopeMask<>d.ApprovedScopeMask)) OR
 (d.IssuedAt IS NOT NULL AND (i.IssuedAt IS NULL OR i.IssuedAt<>d.IssuedAt OR i.CodeHash IS NULL OR i.CodeHash<>d.CodeHash OR i.GrantId IS NULL OR i.GrantId<>d.GrantId OR i.CodeExpiresAt IS NULL OR i.CodeExpiresAt<>d.CodeExpiresAt)))
 THROW 58505,''OAuth transition is permanent.'',1;
END;');
EXEC(N'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAuthorizationTransaction TO AiIntegrationRuntime;');
EXEC(N'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAuthorizationTransaction TO AiIntegrationBootstrap;');
EXEC(N'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiAuthorizationTransaction TO AiCapabilityOperator;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiLoadOAuthClientV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiBeginOAuthV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiFindOAuthIntentV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiApproveOAuthV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiCompleteOAuthV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiDenyOAuthV1 TO AiIntegrationRuntime;');
EXEC(N'GRANT EXECUTE ON OBJECT::dbo.AiConsumeOAuthCodeV1 TO AiIntegrationRuntime;');
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

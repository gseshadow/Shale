/* RFC 7009 closeout. Manual additive candidate after 3B.2C.2; never install at startup. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58800,'Fresh dbo migration session required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x153fce8576ba106e182d2006b694a001b2cfd3ef017beccb725f83185d660821;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 58800,'AI schema lock unavailable.',1;
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
DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1');
IF @receipt IS NOT NULL BEGIN
 INSERT @Owned VALUES (N'ResolveAiRevocationV1'),(N'AiLoadRevocationClientV1'),(N'AiRevokeOAuthCredentialV1');
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

 IF @receipt<>HASHBYTES('SHA2_256',@catalog) OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1.Source'),0x)<>@sourceHash
 THROW 58800,'Revocation catalog/source drift; stop for review.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1'),0x)<>HASHBYTES('SHA2_256',@catalog)
 OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1.Source'),0x)<>0x4463497b7476f047263a3e6dfb3abe28c9c251633ecec19a87eb2bcf255f3ddc
 THROW 58800,'Reviewed credential catalog/source required.',1;
IF OBJECT_ID(N'dbo.ResolveAiRevocationV1') IS NOT NULL OR OBJECT_ID(N'dbo.AiLoadRevocationClientV1') IS NOT NULL OR OBJECT_ID(N'dbo.AiRevokeOAuthCredentialV1') IS NOT NULL
 THROW 58800,'Unreceipted revocation objects; stop for review.',1;
EXEC(N'CREATE PROCEDURE dbo.ResolveAiRevocationV1
 @Hash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF USER_NAME()<>N''dbo'' OR SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64
 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512
 THROW 58802,''Client unavailable.'',1;
 -- Exactly one registered client and one presented digest; no credential inventory or bearer authority.
 -- A missing/foreign digest supplies only the registration creator for configuration-only runtime setup.
 SELECT i.ShaleClientId,COALESCE(c.UserId,i.CreatedByUserId) AS UserId
 FROM dbo.AiIntegrationRegistration i
 LEFT JOIN (dbo.AiOAuthCredential c JOIN dbo.AiDelegatedSession s
 ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId AND s.IntegrationId=c.IntegrationId AND s.UserId=c.UserId AND s.GrantId=c.GrantId)
 ON c.ShaleClientId=i.ShaleClientId AND c.IntegrationId=i.IntegrationId AND c.CredentialHash=@Hash AND c.Kind IN(''ACCESS'',''REFRESH'')
 AND s.ClientId=i.ClientId AND s.Issuer=i.Issuer AND s.Resource=i.Resource AND s.TransactionId IS NOT NULL
 WHERE i.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(i.ClientId)=DATALENGTH(@ClientId)
 AND i.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(i.Issuer)=DATALENGTH(@Issuer)
 AND i.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(i.Resource)=DATALENGTH(@Resource);
END;');
EXEC(N'CREATE PROCEDURE dbo.AiLoadRevocationClientV1
@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 -- Configuration/revocation only: inactive credential subjects must not create an existence oracle.
 IF NOT EXISTS(SELECT 1 FROM dbo.ShaleClients WITH(HOLDLOCK) WHERE Id=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND IsActive=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.Users WITH(HOLDLOCK) WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND id=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')))
 THROW 58802,''Client unavailable.'',1;
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
EXEC(N'CREATE PROCEDURE dbo.AiRevokeOAuthCredentialV1
 @Hash varbinary(max),@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@KeyId uniqueidentifier,
 @JtiHash varbinary(max),@AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiLockV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME(),
 @i uniqueidentifier,@type varchar(16),@s uniqueidentifier,@id bigint;
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @CorrelationId IS NULL OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64
 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512
 THROW 58802,''Client unavailable.'',1;
 -- Revocation reduces authority. Deleted/removed subjects and expired grants still permit revocation;
 -- the subject must exist in this tenant, but never gains issuance or business-read authority here.
 IF NOT EXISTS(SELECT 1 FROM dbo.Users WITH(HOLDLOCK) WHERE ShaleClientId=@t AND id=@u)
 OR NOT EXISTS(SELECT 1 FROM dbo.ShaleClients WITH(HOLDLOCK) WHERE Id=@t AND IsActive=1) THROW 58802,''Client unavailable.'',1;
 SELECT @i=IntegrationId,@type=ClientType FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK)
 WHERE ShaleClientId=@t AND ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND Status=''ACTIVE'' AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @i IS NULL OR @type NOT IN(''PUBLIC'',''CONFIDENTIAL'')
 OR (@type=''PUBLIC'' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type=''CONFIDENTIAL'' AND (@KeyId IS NULL OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@KeyId AND Algorithm=''RS256''
 AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))) THROW 58802,''Client unavailable.'',1;
 IF @type=''CONFIDENTIAL'' BEGIN
 DELETE TOP(256) FROM dbo.AiAssertionReplay WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiAssertionReplay(ShaleClientId,IntegrationId,CredentialId,JtiHash,AcceptedAt,ExpiresAt) VALUES(@t,@i,@KeyId,@JtiHash,@now,@AssertionExpiresAt);
 END;
 SELECT @s=s.SessionId,@id=s.Id FROM dbo.AiOAuthCredential c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId
 AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.UserId=c.UserId
 WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.IntegrationId=@i AND c.CredentialHash=@Hash AND c.Kind IN(''ACCESS'',''REFRESH'')
 AND s.TransactionId IS NOT NULL AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
 -- Both kinds revoke the whole family. Existing session trigger revokes ALL descendants atomically.
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=''SELF'' WHERE ShaleClientId=@t AND UserId=@u AND IntegrationId=@i AND SessionId=@s AND RevokedAt IS NULL;
 IF @@ROWCOUNT=1 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATED_SESSION'',@id,''DEACTIVATED'',@CorrelationId,''SHALE_SERVICE'',N''{"ACTIVE":false}'');
 IF @@ROWCOUNT<>1 THROW 58803,''Revocation audit unavailable.'',1;
 END;
 IF @type=''CONFIDENTIAL'' AND @AssertionExpiresAt<=SYSUTCDATETIME() THROW 58802,''Client unavailable.'',1;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
GRANT EXECUTE ON OBJECT::dbo.ResolveAiRevocationV1 TO AiIntegrationBootstrap;
GRANT EXECUTE ON OBJECT::dbo.AiLoadRevocationClientV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRevokeOAuthCredentialV1 TO AiIntegrationRuntime;
INSERT @Owned VALUES (N'ResolveAiRevocationV1'),(N'AiLoadRevocationClientV1'),(N'AiRevokeOAuthCredentialV1');
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

DECLARE @fingerprint varbinary(32)=HASHBYTES('SHA2_256',@catalog);
EXEC sys.sp_addextendedproperty @name=N'Shale.AiRevocationV1',@value=@fingerprint,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiRevocationV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

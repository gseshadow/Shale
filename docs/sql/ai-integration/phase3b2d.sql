/* Phase 3B.2D internal ACCESS validation. Manual, additive, disposable verification only. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58900,'Fresh dbo migration session required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x85d215258b9e296d42a6544bcba606bb7455bd8827c876eba79b95159af129cd;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 58900,'AI schema lock unavailable.',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1'),(N'ResolveAiRefreshV1'),(N'AiIssueCredentialsV1'),(N'AiRefreshCredentialsV1'),(N'AiLifecycle_Session'),(N'AiLifecycle_Credential'),(N'AiCascade_Grant'),(N'AiCascade_Key'),(N'AiCascade_Registration'),(N'AiCascade_Web'),(N'AiCascade_User'),(N'AiCascade_Tenant'); INSERT @Owned VALUES (N'ResolveAiRevocationV1'),(N'AiLoadRevocationClientV1'),(N'AiRevokeOAuthCredentialV1');
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
DECLARE @receipt varbinary(32)=(SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1');
IF @receipt IS NOT NULL BEGIN
INSERT @Owned VALUES (N'ResolveAiAccessV1'),(N'AiValidateAccessV1');
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

 IF @receipt<>HASHBYTES('SHA2_256',@catalog) OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1.Source'),0x)<>@sourceHash
 THROW 58900,'ACCESS catalog/source drift; stop for review.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1'),0x)<>HASHBYTES('SHA2_256',@catalog)
 OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1.Source'),0x)<>0x153fce8576ba106e182d2006b694a001b2cfd3ef017beccb725f83185d660821
 THROW 58900,'Reviewed revocation catalog/source required.',1;
IF OBJECT_ID(N'dbo.ResolveAiAccessV1') IS NOT NULL OR OBJECT_ID(N'dbo.AiValidateAccessV1') IS NOT NULL
 THROW 58900,'Unreceipted ACCESS objects; stop for review.',1;
EXEC(N'CREATE PROCEDURE dbo.ResolveAiAccessV1
 @Hash varbinary(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF USER_NAME()<>N''dbo'' OR SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 THROW 58901,''Integration authentication failed.'',1;
 -- Only a lifecycle-bound ACCESS record, with one exact digest. No fallback owner or scope projection.
 SELECT c.ShaleClientId,c.UserId,c.IntegrationId,c.GrantId,c.SessionId,c.Id AS AccessCredentialId,s.ClientId,s.CredentialId AS KeyId
 FROM dbo.AiOAuthCredential c JOIN dbo.AiDelegatedSession s
 ON s.ShaleClientId=c.ShaleClientId AND s.UserId=c.UserId AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.SessionId=c.SessionId
 WHERE c.CredentialHash=@Hash AND c.Kind=''ACCESS'' AND c.ConsumedAt IS NULL AND c.RevokedAt IS NULL AND c.ExpiresAt>SYSUTCDATETIME()
 AND s.TransactionId IS NOT NULL
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
END;');
EXEC(N'CREATE PROCEDURE dbo.AiValidateAccessV1
 @AccessCredentialId bigint,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,
 @ClientId varchar(max),@KeyId uniqueidentifier,@Issuer nvarchar(max),@Resource nvarchar(max),@PriorScopes tinyint
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 THROW 58901,''Fresh ACCESS validation lease required.'',1;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiLockV1 @Exclusive=0;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @revision bigint,@policy bigint,@registration bigint,@grantRevision bigint,@held bigint;
 IF @t IS NULL OR @u IS NULL OR @AccessCredentialId IS NULL OR @IntegrationId IS NULL OR @GrantId IS NULL OR @SessionId IS NULL
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
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @revision IS NULL OR @policy IS NULL OR @registration IS NULL OR @grantRevision IS NULL OR @mask IS NULL OR @mask=0
 OR @deadline IS NULL OR @deadline<=@now OR (@keyStart IS NOT NULL AND @keyStart>@now)
 THROW 58901,''Integration authentication failed.'',1;
 COMMIT TRANSACTION;
 SELECT @mask AS ScopeMask,@deadline AS Deadline,@revision AS CapabilityRevision,@policy AS PolicyRevision,
 @registration AS IntegrationRevision,@grantRevision AS GrantRevision;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
GRANT EXECUTE ON OBJECT::dbo.ResolveAiAccessV1 TO AiIntegrationBootstrap;
GRANT EXECUTE ON OBJECT::dbo.AiValidateAccessV1 TO AiIntegrationRuntime;
INSERT @Owned VALUES (N'ResolveAiAccessV1'),(N'AiValidateAccessV1');
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
EXEC sys.sp_addextendedproperty @name=N'Shale.AiAccessV1',@value=@hash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiAccessV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiOAuthCredential';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

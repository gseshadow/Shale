CREATE PROCEDURE dbo.AiCimdPublishMetadataV1
 @ApplicationId uniqueidentifier,@Revision bigint,@Digest varbinary(max),@Redirects nvarchar(max),@Keys nvarchar(max),@FreshUntil datetime2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59500,'Fresh neutral metadata lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@held bigint;
 SELECT @held=Revision FROM dbo.AiExternalApplication WITH(UPDLOCK,HOLDLOCK) WHERE ApplicationId=@ApplicationId;
 IF @Digest IS NULL OR DATALENGTH(@Digest)<>32 OR @FreshUntil IS NULL OR @FreshUntil<=@now OR @FreshUntil>DATEADD(minute,5,@now)
 OR @Redirects IS NULL OR @Keys IS NULL OR DATALENGTH(@Redirects)>65536 OR DATALENGTH(@Keys)>16384 OR ISJSON(@Redirects)<>1 OR ISJSON(@Keys)<>1
 OR LEFT(@Redirects,1)<>N'[' OR LEFT(@Keys,1)<>N'['
 OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalApplication WHERE ApplicationId=@ApplicationId AND Revision=@Revision AND MetadataDigest=@Digest AND Source='CIMD' AND Disabled=0)
 THROW 59500,'Publication unavailable.',1;
 IF (SELECT COUNT(*) FROM OPENJSON(@Redirects)) NOT BETWEEN 1 AND 16
 OR EXISTS(SELECT 1 FROM OPENJSON(@Redirects) WHERE type<>1 OR DATALENGTH(value) NOT BETWEEN 2 AND 4096 OR CHARINDEX(N'#',value)>0 OR CHARINDEX(N'*',value)>0)
 OR (SELECT COUNT(*) FROM OPENJSON(@Keys))>2
 OR EXISTS(SELECT 1 FROM OPENJSON(@Keys) WITH(kid nvarchar(max),modulus varchar(max),exponent int) WHERE kid IS NULL OR DATALENGTH(kid) NOT BETWEEN 2 AND 256 OR exponent<>65537 OR exponent IS NULL OR TRY_CONVERT(varbinary(max),modulus,2) IS NULL OR DATALENGTH(TRY_CONVERT(varbinary(max),modulus,2)) NOT BETWEEN 256 AND 512)
 THROW 59500,'Metadata profile unavailable.',1;
 IF EXISTS(SELECT 1 FROM dbo.AiCimdPublicationV1 WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision) BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdPublicationV1 WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision AND MetadataDigest=@Digest
 AND Redirects=@Redirects COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Redirects)=DATALENGTH(@Redirects)
 AND SigningKeys=@Keys COLLATE Latin1_General_100_BIN2 AND DATALENGTH(SigningKeys)=DATALENGTH(@Keys)) THROW 59500,'Publication content is immutable.',1;
 UPDATE dbo.AiCimdPublicationV1 SET FreshUntil=@FreshUntil WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision;
 END ELSE INSERT dbo.AiCimdPublicationV1 VALUES(@ApplicationId,@Revision,@Digest,@Redirects,@Keys,@FreshUntil);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
/* Internal helper. The tenant lock is already held; lock application before approval/registration. */
CREATE PROCEDURE dbo.AiCimdOAuthAuthorityV1
 @ApplicationId uniqueidentifier,@Revision bigint,@Approval uniqueidentifier,@ApprovalVersion varbinary(max),@Digest varbinary(max),
 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Integration uniqueidentifier,@Web uniqueidentifier,@Scopes tinyint,
 @Method varchar(max),@Key uniqueidentifier,@RequireFresh bit,@Deadline datetime2(7) OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'CIMD authority unavailable.',1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@held bigint;
 SELECT @held=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@ApplicationId;
 SELECT @held=Id FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK) WHERE ShaleClientId=@t AND ApprovalId=@Approval;
 SELECT @held=Id FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@Integration;
 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 SET @Deadline=NULL;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 SELECT @Deadline=d.Deadline FROM dbo.AiExternalApplication e
 JOIN dbo.AiTenantApplicationApproval p ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ApprovalId=@Approval
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=@t AND i.IntegrationId=p.IntegrationId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=@t AND w.UserId=@u AND w.SessionId=@Web
 JOIN dbo.AiCimdPublicationV1 m WITH(HOLDLOCK) ON m.ApplicationId=e.ApplicationId AND m.ApplicationRevision=e.Revision
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=@t AND k.IntegrationId=i.IntegrationId AND k.CredentialId=@Key
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(p.ExpiresAt),(i.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,p.ExpiresAt)))v(Expiry))d
 WHERE e.ApplicationId=@ApplicationId AND e.Revision=@Revision AND e.MetadataDigest=@Digest AND e.Source='CIMD' AND e.Disabled=0
 AND e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
 AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
 AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
 AND p.ApplicationRevision=@Revision AND p.RowVer=@ApprovalVersion AND DATALENGTH(@ApprovalVersion)=8 AND p.State='APPROVED' AND p.ClosedAt IS NULL
 AND p.IntegrationId=@Integration AND @Scopes BETWEEN 1 AND 3 AND (p.ScopeMask & @Scopes)=@Scopes
 AND i.Status='SUSPENDED' AND i.Revision=1 AND i.RevokedAt IS NULL AND (i.ScopeMask & @Scopes)=@Scopes
 AND i.Issuer=e.Issuer AND DATALENGTH(i.Issuer)=DATALENGTH(e.Issuer) AND i.Resource=e.Resource AND DATALENGTH(i.Resource)=DATALENGTH(e.Resource)
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL
 AND (@RequireFresh=0 OR m.FreshUntil>@now)
 AND ((@Method='none' AND DATALENGTH(@Method)=4 AND i.ClientType='PUBLIC' AND @Key IS NULL AND (e.MethodMask & 1)=1)
 OR (@Method='private_key_jwt' AND DATALENGTH(@Method)=15 AND i.ClientType='CONFIDENTIAL' AND (e.MethodMask & 2)=2 AND @Key IS NOT NULL
 AND k.RevokedAt IS NULL AND k.Algorithm='RS256' AND k.ValidFrom<=@now AND k.ExpiresAt>@now
 AND EXISTS(SELECT 1 FROM dbo.AiCimdKeyBindingV1 b WHERE b.ShaleClientId=@t AND b.IntegrationId=@Integration AND b.ApplicationId=@ApplicationId AND b.ApplicationRevision=@Revision AND b.CredentialId=@Key)))
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 IF @Deadline IS NULL OR @Deadline<=SYSUTCDATETIME() THROW 59501,'CIMD authority unavailable.',1;
END;
GO
CREATE PROCEDURE dbo.AiCimdBeginOAuthV1
 @TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@ClientState nvarchar(max),@ScopeMask tinyint,@IntentHash varbinary(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD browser lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @application uniqueidentifier,@revision bigint,@approval uniqueidentifier,@av binary(8),@digest binary(32),@integration uniqueidentifier,
 @method varchar(16),@key uniqueidentifier,@client varchar(64),@rv binary(8),@deadline datetime2(7),@redirects nvarchar(max),@keys nvarchar(max);
 IF @TransactionId IS NULL OR @IntentHash IS NULL OR DATALENGTH(@IntentHash)<>32 OR @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3
 OR @ClientState IS NULL OR DATALENGTH(@ClientState) NOT BETWEEN 2 AND 1024 OR @Challenge IS NULL OR DATALENGTH(@Challenge)<>43
 OR @Challenge COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Za-z0-9_-]%'
 OR @External IS NULL OR DATALENGTH(@External) NOT BETWEEN 2 AND 4096 OR @RedirectUri IS NULL OR DATALENGTH(@RedirectUri) NOT BETWEEN 2 AND 4096
 THROW 59501,'CIMD request unavailable.',1;
 SELECT @application=ApplicationId,@revision=Revision,@digest=MetadataDigest FROM dbo.AiExternalApplication WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource) AND Source='CIMD' AND Disabled=0;
 SELECT @approval=ApprovalId,@av=RowVer,@integration=IntegrationId FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK)
 WHERE ShaleClientId=@t AND ApplicationId=@application AND ApplicationRevision=@revision AND State='APPROVED' AND ClosedAt IS NULL;
 SELECT @client=ClientId,@rv=RowVer,@method=CASE WHEN ClientType='CONFIDENTIAL' THEN 'private_key_jwt' ELSE 'none' END
 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@integration;
 SELECT @redirects=Redirects,@keys=SigningKeys FROM dbo.AiCimdPublicationV1 WITH(HOLDLOCK) WHERE ApplicationId=@application AND ApplicationRevision=@revision AND FreshUntil>SYSUTCDATETIME();
 -- SQL independently validates exact callback; the Java adapter applies the existing canonical URI policy.
 IF NOT EXISTS(SELECT 1 FROM OPENJSON(@redirects) WHERE value=@RedirectUri COLLATE Latin1_General_100_BIN2 AND DATALENGTH(value)=DATALENGTH(@RedirectUri))
 THROW 59501,'CIMD callback unavailable.',1;
 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @method='private_key_jwt' BEGIN
 DECLARE @kid nvarchar(128),@modulus varbinary(512),@expiry datetime2(7);
 SELECT TOP(1) @kid=kid,@modulus=CONVERT(varbinary(512),modulus,2) FROM OPENJSON(@keys) WITH(kid nvarchar(128),modulus varchar(1024),exponent int) WHERE exponent=65537 ORDER BY kid COLLATE Latin1_General_100_BIN2;
 IF @kid IS NULL THROW 59501,'CIMD key unavailable.',1;
 SELECT @expiry=ExpiresAt FROM dbo.AiTenantApplicationApproval WHERE ShaleClientId=@t AND ApprovalId=@approval;
 SELECT @key=CredentialId FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@integration AND Modulus=@modulus AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @key IS NULL BEGIN
 SET @key=NEWID();
 INSERT dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId,Modulus,Exponent,CreatedAt,CreatedByUserId,ValidFrom,ExpiresAt)
 VALUES(@t,@integration,@key,@modulus,65537,@now,@u,@now,@expiry);
 DECLARE @keyRow bigint=SCOPE_IDENTITY();
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_CLIENT_KEY',@keyRow,'CREATED',NEWID(),'SHALE_SERVICE',NULL);
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD key audit unavailable.',1;
 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@integration AND ApplicationRevision=@revision AND Kid=@kid)
 INSERT dbo.AiCimdKeyBindingV1 VALUES(@t,@integration,@application,@revision,@kid,@key);
 END;
 EXEC dbo.AiCimdOAuthAuthorityV1 @application,@revision,@approval,@av,@digest,@External,@Issuer,@Resource,@integration,@WebSessionId,@ScopeMask,@method,@key,1,@deadline OUTPUT;
 IF @deadline<=DATEADD(minute,5,@now) THROW 59501,'CIMD approval deadline too short.',1;
 DECLARE @grantExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(hour,1,@now) THEN @deadline ELSE DATEADD(hour,1,@now) END;
 IF (SELECT COUNT(*) FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND IssuedAt IS NULL AND DeniedAt IS NULL AND IntentExpiresAt>@now)>=8 THROW 59501,'CIMD pending capacity unavailable.',1;
 INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt)
 VALUES(@t,@integration,@u,@IntentHash,@ScopeMask,@now,DATEADD(minute,5,@now),@grantExpiry);
 INSERT dbo.AiCimdAuthorizationV1(TransactionId,ShaleClientId,IntegrationId,UserId,WebSessionId,IntentHash,ClientId,Issuer,Resource,RedirectUri,Challenge,ClientState,
 RequestedScopeMask,RegistrationVersion,CredentialId,CreatedAt,IntentExpiresAt,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,AuthMethod)
 VALUES(@TransactionId,@t,@integration,@u,@WebSessionId,@IntentHash,@client,@Issuer,@Resource,@RedirectUri,@Challenge,@ClientState,@ScopeMask,@rv,@key,@now,DATEADD(minute,5,@now),@application,@revision,@approval,@av,@digest,@External,@method);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;

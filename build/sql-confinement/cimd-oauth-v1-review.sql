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
 IF NOT EXISTS(SELECT 1 FROM OPENJSON(@redirects) r WHERE
 (((r.value COLLATE Latin1_General_100_BIN2)=@RedirectUri AND DATALENGTH((r.value COLLATE Latin1_General_100_BIN2))=DATALENGTH(@RedirectUri)) OR (@method='none' AND
 ((LEFT((r.value COLLATE Latin1_General_100_BIN2),17)=N'http://127.0.0.1:' AND LEFT(@RedirectUri,17)=N'http://127.0.0.1:') OR
 (LEFT((r.value COLLATE Latin1_General_100_BIN2),13)=N'http://[::1]:' AND LEFT(@RedirectUri,13)=N'http://[::1]:')) AND
 CHARINDEX(N'/',@RedirectUri,8)>0 AND CHARINDEX(N'/',(r.value COLLATE Latin1_General_100_BIN2),8)>0 AND
 SUBSTRING(@RedirectUri,CHARINDEX(N'/',@RedirectUri,8),2048)=SUBSTRING((r.value COLLATE Latin1_General_100_BIN2),CHARINDEX(N'/',(r.value COLLATE Latin1_General_100_BIN2),8),2048) AND
 TRY_CONVERT(int,SUBSTRING(@RedirectUri,CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END,
 CASE WHEN CHARINDEX(N'/',@RedirectUri,8)>CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END THEN CHARINDEX(N'/',@RedirectUri,8)-CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END ELSE 0 END)) BETWEEN 1 AND 65535)))
 THROW 58502,'OAuth redirect unavailable.',1;
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
GO
CREATE PROCEDURE dbo.AiCimdApproveOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 IF @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 OR (@ScopeMask & @scopes)<>@ScopeMask THROW 58502,'Invalid consent scope.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET ApprovedScopeMask=@ScopeMask WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND ApprovedScopeMask IS NULL AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent already used or expired.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdCompleteOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint,@CodeHash varbinary(32),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 DECLARE @intent binary(32),@expiry datetime2(7),@grant uniqueidentifier,@id bigint;
 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL THROW 58502,'Invalid code binding.',1;
 SELECT @intent=a.IntentHash,@expiry=c.GrantExpiresAt FROM dbo.AiCimdAuthorizationV1 a
 JOIN dbo.AiConsentIntent c ON c.ShaleClientId=a.ShaleClientId AND c.IntentHash=a.IntentHash AND c.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.ApprovedScopeMask=@ScopeMask
 AND a.IntentExpiresAt>@now AND a.IssuedAt IS NULL AND a.DeniedAt IS NULL AND c.ConsumedAt IS NULL AND c.ExpiresAt>@now;
 IF @intent IS NULL OR @expiry<=DATEADD(second,60,@now)
 OR EXISTS(SELECT 1 FROM dbo.AiDelegationGrant WHERE ShaleClientId=@t AND IntegrationId=@i AND UserId=@u AND RevokedAt>=@created)
 THROW 58503,'Stale or used consent.',1;
 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE IntentHash=@intent AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent already used.',1;
 INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,ScopeMask,ConsentedAt,ExpiresAt)
 VALUES(@t,@i,@u,@ScopeMask,@now,@expiry);
 SET @id=SCOPE_IDENTITY();
 SELECT @grant=GrantId FROM dbo.AiDelegationGrant WHERE Id=@id AND ShaleClientId=@t;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATION_GRANT',@id,'CREATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@ScopeMask,N'}'));
 IF @@ROWCOUNT<>1 THROW 58504,'Consent audit unavailable.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET GrantId=@grant,CodeHash=@CodeHash,IssuedAt=@now,CodeExpiresAt=DATEADD(second,60,@now)
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND IssuedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Code already completed.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdDenyOAuthV1
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL
 AND IntentHash IN(SELECT IntentHash FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL);
 UPDATE dbo.AiCimdAuthorizationV1 SET DeniedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent unavailable.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdFindOAuthIntentV1
@IntentHash varbinary(32),@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 DECLARE @TransactionId uniqueidentifier; SELECT @TransactionId=TransactionId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND IntentHash=@IntentHash AND WebSessionId=@WebSessionId;
 EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 SELECT *, (SELECT GrantExpiresAt FROM dbo.AiConsentIntent c WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.IntentHash=dbo.AiCimdAuthorizationV1.IntentHash) AS GrantExpiresAt FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId
 AND IntentHash=@IntentHash AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL
 AND EXISTS(SELECT 1 FROM dbo.AiConsentIntent c WHERE c.IntentHash=@IntentHash AND c.ShaleClientId=@t AND c.UserId=@u AND c.ConsumedAt IS NULL);

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE TRIGGER dbo.AiCimdImmutableAuthorizationV1 ON dbo.AiCimdAuthorizationV1 AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58505,'OAuth history cannot be deleted.',1;
 IF UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(ApprovalId) OR UPDATE(ApprovalVersion) OR UPDATE(MetadataDigest) OR UPDATE(ExternalClientId) OR UPDATE(AuthMethod) OR UPDATE(TransactionId) OR UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(WebSessionId)
 OR UPDATE(IntentHash) OR UPDATE(ClientId) OR UPDATE(Issuer) OR UPDATE(Resource) OR UPDATE(RedirectUri) OR UPDATE(Challenge)
 OR UPDATE(ClientState) OR UPDATE(RequestedScopeMask) OR UPDATE(RegistrationVersion) OR UPDATE(CredentialId) OR UPDATE(CreatedAt) OR UPDATE(IntentExpiresAt)
 THROW 58505,'OAuth binding is immutable.',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.TransactionId=d.TransactionId WHERE
 (d.ConsumedAt IS NOT NULL AND (i.ConsumedAt IS NULL OR i.ConsumedAt<>d.ConsumedAt)) OR
 (d.DeniedAt IS NOT NULL AND (i.DeniedAt IS NULL OR i.DeniedAt<>d.DeniedAt)) OR
 (d.ApprovedScopeMask IS NOT NULL AND (i.ApprovedScopeMask IS NULL OR i.ApprovedScopeMask<>d.ApprovedScopeMask)) OR
 (d.IssuedAt IS NOT NULL AND (i.IssuedAt IS NULL OR i.IssuedAt<>d.IssuedAt OR i.CodeHash IS NULL OR i.CodeHash<>d.CodeHash OR i.GrantId IS NULL OR i.GrantId<>d.GrantId OR i.CodeExpiresAt IS NULL OR i.CodeExpiresAt<>d.CodeExpiresAt)))
 THROW 58505,'OAuth transition is permanent.',1;
END;
GO
CREATE PROCEDURE dbo.AiCimdValidateTransactionV1 @TransactionId uniqueidentifier,@Web uniqueidentifier
AS BEGIN
 SET NOCOUNT ON;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @a uniqueidentifier,@r bigint,@p uniqueidentifier,@v binary(8),@d binary(32),@e nvarchar(2048),@issuer nvarchar(256),@resource nvarchar(256),
 @i uniqueidentifier,@sc tinyint,@m varchar(16),@k uniqueidentifier,@deadline datetime2(7);
 SELECT @a=ApplicationId,@r=ApplicationRevision,@p=ApprovalId,@v=ApprovalVersion,@d=MetadataDigest,@e=ExternalClientId,@issuer=Issuer,@resource=Resource,
 @i=IntegrationId,@sc=RequestedScopeMask,@m=AuthMethod,@k=CredentialId FROM dbo.AiCimdAuthorizationV1
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@Web AND DeniedAt IS NULL;
 EXEC dbo.AiCimdOAuthAuthorityV1 @a,@r,@p,@v,@d,@e,@issuer,@resource,@i,@Web,@sc,@m,@k,1,@deadline OUTPUT;
END;
GO
CREATE PROCEDURE dbo.AiCimdIssueV1
 @TransactionId uniqueidentifier,@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@GrantId uniqueidentifier,
 @KeyId uniqueidentifier,@Scopes tinyint,@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 -- This seam is callable only within the validated exchange transaction. It never accepts a caller tenant/user.
 IF @@TRANCOUNT<>1 THROW 58702,'Validated exchange transaction required.',1;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 IF @AccessHash IS NULL OR @RefreshHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR DATALENGTH(@RefreshHash)<>32 OR @AccessHash=@RefreshHash
 OR @CorrelationId IS NULL OR @Scopes IS NULL OR @Scopes NOT BETWEEN 1 AND 3 THROW 58702,'Credential binding unavailable.',1;
 DECLARE @i uniqueidentifier,@deadline datetime2(7),@consent datetime2(7),@s uniqueidentifier=NEWID(),@revision bigint;
 SELECT @i=a.IntegrationId,@consent=g.ConsentedAt,@deadline=d.Deadline
 FROM dbo.AiCimdAuthorizationV1 a WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=a.ShaleClientId AND k.IntegrationId=a.IntegrationId AND k.CredentialId=a.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(DATEADD(hour,24,g.ConsentedAt)),(COALESCE(k.ExpiresAt,g.ExpiresAt)))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.GrantId=@GrantId AND a.ConsumedAt IS NOT NULL
 AND a.ApprovedScopeMask=@Scopes AND a.DeniedAt IS NULL AND r.RowVer=a.RegistrationVersion
 AND a.ExternalClientId=@ClientId AND DATALENGTH(a.ExternalClientId)=DATALENGTH(@ClientId) AND r.ClientId=a.ClientId
 AND a.Issuer=@Issuer AND DATALENGTH(a.Issuer)=DATALENGTH(@Issuer) AND r.Issuer=a.Issuer
 AND a.Resource=@Resource AND DATALENGTH(a.Resource)=DATALENGTH(@Resource) AND r.Resource=a.Resource
 AND r.Status='SUSPENDED' AND r.Revision=1 AND r.RevokedAt IS NULL AND r.ExpiresAt>@now AND (r.ScopeMask & @Scopes)=@Scopes
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND (g.ScopeMask & @Scopes)=@Scopes
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL AND w.ExpiresAt>@now
 AND ((r.ClientType='PUBLIC' AND @KeyId IS NULL AND a.CredentialId IS NULL) OR
 (r.ClientType='CONFIDENTIAL' AND @KeyId=a.CredentialId AND k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 SELECT @deadline=CASE WHEN p.ExpiresAt<@deadline THEN p.ExpiresAt ELSE @deadline END FROM dbo.AiCimdAuthorizationV1 a JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=a.ShaleClientId AND p.ApprovalId=a.ApprovalId WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t;
 IF @i IS NULL OR @deadline<=SYSUTCDATETIME() THROW 58702,'Exchange authority unavailable.',1;
 SELECT @revision=Revision FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(minute,5,@now) THEN @deadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(hour,8,@now) THEN @deadline ELSE DATEADD(hour,8,@now) END;
 INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialId,ScopeMask,IssuedAt,ExpiresAt,IdleExpiresAt,
 TransactionId,ClientId,Issuer,Resource,ConsentAt,CapabilityRevision)
 VALUES(@t,@i,@u,@GrantId,@s,@KeyId,@Scopes,@now,@deadline,@refreshExpiry,NULL,(SELECT ClientId FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId),@Issuer,@Resource,@consent,@revision);

 INSERT dbo.AiCimdFamilyV1(ShaleClientId,SessionId,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,CodeIssuedAt,CodeExpiresAt,ConsumedAt)
 SELECT ShaleClientId,@s,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,IssuedAt,CodeExpiresAt,ConsumedAt
 FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u;
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD family receipt unavailable.',1;
 INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@GrantId,@s,@AccessHash,'ACCESS',@Scopes,0,@now,@accessExpiry),(@t,@i,@u,@GrantId,@s,@RefreshHash,'REFRESH',@Scopes,0,@now,@refreshExpiry);
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT @t,@u,'AI_DELEGATED_SESSION',Id,'CREATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@Scopes,N'}')
 FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND SessionId=@s;
 IF @@ROWCOUNT<>1 THROW 58703,'Credential audit unavailable.',1;
 SELECT @now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@Scopes AS ScopeMask;
END;
GO
CREATE PROCEDURE dbo.AiCimdRedeemV1
@CodeHash varbinary(max),@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@KeyId uniqueidentifier,@JtiHash varbinary(max),
 @AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@CorrelationId uniqueidentifier,@AccessHash varbinary(max),@RefreshHash varbinary(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();

 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL
 OR DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 OR DATALENGTH(@RedirectUri) NOT BETWEEN 1 AND 4096
 OR DATALENGTH(@Challenge)<>43 THROW 58602,'Invalid exchange binding.',1;
 DECLARE @WebSessionId uniqueidentifier;
 SELECT @WebSessionId=WebSessionId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND CodeHash=@CodeHash;
 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58602,'WEB authority unavailable.',1;
 DECLARE @TransactionId uniqueidentifier;
 SELECT @TransactionId=TransactionId FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK)
 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND CodeHash=@CodeHash AND ConsumedAt IS NULL
 AND CodeExpiresAt>@now AND ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer AND DATALENGTH(Issuer)=DATALENGTH(@Issuer) AND Resource=@Resource AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND RedirectUri=@RedirectUri AND DATALENGTH(RedirectUri)=DATALENGTH(@RedirectUri) AND Challenge=@Challenge COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Challenge)=DATALENGTH(@Challenge);

 EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;
 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdAuthorizationV1 a JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK)
 ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND g.RevokedAt IS NULL AND g.ExpiresAt>@now
 AND (g.ScopeMask & a.ApprovedScopeMask)=a.ApprovedScopeMask) THROW 58502,'Grant unavailable.',1;
 SET @now=SYSUTCDATETIME();
 DECLARE @type varchar(16),@deadline datetime2(7),@grantId uniqueidentifier,@grantRow bigint;
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@i;
 IF (@type='PUBLIC' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type='CONFIDENTIAL' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)))
 THROW 58602,'Client authentication unavailable.',1;
 IF @type='CONFIDENTIAL' BEGIN
 DECLARE @ja uniqueidentifier,@jr bigint,@ji uniqueidentifier,@jk nvarchar(128); SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND TransactionId=@TransactionId; SELECT @jk=Kid FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@ji AND ApplicationId=@ja AND ApplicationRevision=@jr AND CredentialId=@KeyId; EXEC dbo.AiCimdReserveAssertionV1 @ja,@jr,@jk,@JtiHash,@AssertionIssuedAt,@AssertionExpiresAt;

 -- Bounded cleanup under the same exclusive tenant lock. No valid-window record can be removed.
 DELETE TOP(256) FROM dbo.AiCimdAssertionReplayV1 WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiCimdAssertionReplayV1(ShaleClientId,IntegrationId,CredentialId,JtiHash,AcceptedAt,ExpiresAt)
 VALUES(@t,@i,@KeyId,@JtiHash,@now,@AssertionExpiresAt);
 END;
 SELECT @grantId=g.GrantId,@grantRow=g.Id,@deadline=d.Deadline FROM dbo.AiCimdAuthorizationV1 a
 JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.UserId=a.UserId AND g.IntegrationId=a.IntegrationId
 JOIN dbo.AiIntegrationRegistration r ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.UserSessions w ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),
 (CASE WHEN @key IS NULL THEN g.ExpiresAt ELSE (SELECT ExpiresAt FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key) END))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u;
 IF @deadline IS NULL OR @deadline<=SYSUTCDATETIME() OR (@key IS NOT NULL AND @AssertionExpiresAt<=SYSUTCDATETIME()) THROW 58602,'Exchange deadline elapsed.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET ConsumedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL AND CodeExpiresAt>SYSUTCDATETIME();
 IF @@ROWCOUNT<>1 THROW 58503,'Code unavailable.',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATION_GRANT',@grantRow,'UPDATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',
 (SELECT ApprovedScopeMask FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t),N'}'));
 IF @@ROWCOUNT<>1 THROW 58603,'Exchange audit unavailable.',1;
 DECLARE @scopesApproved tinyint; SELECT @scopesApproved=ApprovedScopeMask FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t;
 EXEC dbo.AiCimdIssueV1 @TransactionId,@ClientId,@Issuer,@Resource,@grantId,@KeyId,@scopesApproved,@AccessHash,@RefreshHash,@CorrelationId;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdRefreshV1
 @Hash varbinary(max),@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@KeyId uniqueidentifier,
 @JtiHash varbinary(max),@AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @AccessHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR @RefreshHash IS NULL OR DATALENGTH(@RefreshHash)<>32
 OR @Hash=@AccessHash OR @Hash=@RefreshHash OR @AccessHash=@RefreshHash OR @CorrelationId IS NULL
 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 THROW 58702,'Credential unavailable.',1;
 DECLARE @s uniqueidentifier,@id bigint,@sessionRow bigint,@i uniqueidentifier,@g uniqueidentifier,@scopes tinyint,@generation int,
 @consumed datetime2(7),@credentialExpiry datetime2(7),@deadline datetime2(7),@idle datetime2(7),@revoked datetime2(7),@type varchar(16),@key uniqueidentifier,@revision bigint;
 SELECT @s=s.SessionId,@sessionRow=s.Id,@id=c.Id,@i=s.IntegrationId,@g=s.GrantId,@scopes=s.ScopeMask,@generation=c.Generation,
 @consumed=c.ConsumedAt,@credentialExpiry=c.ExpiresAt,@deadline=s.ExpiresAt,@idle=s.IdleExpiresAt,@revoked=s.RevokedAt,@key=s.CredentialId,@revision=s.CapabilityRevision
 FROM dbo.AiCimdCredentialV1 c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId AND s.GrantId=c.GrantId AND s.IntegrationId=c.IntegrationId AND s.UserId=c.UserId
 WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.CredentialHash=@Hash AND c.Kind='REFRESH' AND c.ScopeMask=s.ScopeMask
 AND EXISTS(SELECT 1 FROM dbo.AiCimdFamilyV1 f WHERE f.ShaleClientId=s.ShaleClientId AND f.SessionId=s.SessionId AND f.ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.ExternalClientId)=DATALENGTH(@ClientId))
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i;
 IF @s IS NULL OR @type IS NULL OR (@type='PUBLIC' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type='CONFIDENTIAL' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now)))
 THROW 58702,'Client unavailable.',1;

 DECLARE @fa uniqueidentifier,@fr bigint,@fp uniqueidentifier,@fv binary(8),@fd binary(32),@fe nvarchar(2048),@fw uniqueidentifier,@fm varchar(16),@currentDeadline datetime2(7);
 SELECT @fa=ApplicationId,@fr=ApplicationRevision,@fp=ApprovalId,@fv=ApprovalVersion,@fd=MetadataDigest,@fe=ExternalClientId,@fw=WebSessionId,@fm=AuthMethod
 FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s AND UserId=@u;
 EXEC dbo.AiCimdOAuthAuthorityV1 @fa,@fr,@fp,@fv,@fd,@fe,@Issuer,@Resource,@i,@fw,@scopes,@fm,@key,1,@currentDeadline OUTPUT;
 IF @type='CONFIDENTIAL' BEGIN
 DECLARE @ja uniqueidentifier,@jr bigint,@ji uniqueidentifier,@jk nvarchar(128); SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s; SELECT @jk=Kid FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@ji AND ApplicationId=@ja AND ApplicationRevision=@jr AND CredentialId=@KeyId; EXEC dbo.AiCimdReserveAssertionV1 @ja,@jr,@jk,@JtiHash,@AssertionIssuedAt,@AssertionExpiresAt;

 DELETE TOP(256) FROM dbo.AiCimdAssertionReplayV1 WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiCimdAssertionReplayV1 VALUES(@t,@i,@key,@JtiHash,@now,@AssertionExpiresAt);
 END;
 -- Reuse is checked even after token expiry. Required audit commits BEFORE denial is returned.
 IF @consumed IS NOT NULL OR @revision<>(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1) BEGIN
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=CASE WHEN @consumed IS NOT NULL THEN 'REUSE' ELSE 'DISABLED' END WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;
 IF @@ROWCOUNT=1 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@sessionRow,'DEACTIVATED',@CorrelationId,'SHALE_SERVICE',N'{"ACTIVE":false}');
 IF @@ROWCOUNT<>1 THROW 58703,'Revocation audit unavailable.',1;
 END;
 UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;
 COMMIT TRANSACTION;
 SELECT CAST(1 AS bit) AS Denied;
 RETURN;
 END;
 DECLARE @liveDeadline datetime2(7);
 SELECT @liveDeadline=d.Deadline
 FROM dbo.AiDelegatedSession s JOIN dbo.AiCimdFamilyV1 a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.SessionId=s.SessionId AND s.TransactionId IS NULL AND a.GrantId=s.GrantId AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=s.ShaleClientId AND r.IntegrationId=s.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId AND g.UserId=s.UserId AND g.IntegrationId=s.IntegrationId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=s.ShaleClientId AND w.UserId=s.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(s.ExpiresAt),(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,s.ExpiresAt)))v(Expiry))d
 WHERE s.ShaleClientId=@t AND s.SessionId=@s AND s.RevokedAt IS NULL AND s.RefreshGeneration=@generation
 AND r.ClientId=s.ClientId AND r.Issuer=s.Issuer AND r.Resource=s.Resource AND r.Status='SUSPENDED' AND r.Revision=1 AND r.RevokedAt IS NULL
 AND (r.ScopeMask & @scopes)=@scopes AND g.RevokedAt IS NULL AND (g.ScopeMask & @scopes)=@scopes
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL AND (s.CredentialId IS NULL OR (k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @currentDeadline<@liveDeadline SET @liveDeadline=@currentDeadline;
 SET @now=SYSUTCDATETIME();
 IF @revoked IS NOT NULL OR @credentialExpiry<=@now OR @idle<=@now OR @liveDeadline IS NULL OR @liveDeadline<=@now
 OR (@key IS NOT NULL AND @AssertionExpiresAt<=@now) THROW 58702,'Refresh authority unavailable.',1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(minute,5,@now) THEN @liveDeadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(hour,8,@now) THEN @liveDeadline ELSE DATEADD(hour,8,@now) END;
 UPDATE dbo.AiCimdCredentialV1 SET ConsumedAt=@now WHERE ShaleClientId=@t AND Id=@id AND ConsumedAt IS NULL AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @@ROWCOUNT<>1 THROW 58702,'Refresh unavailable.',1;
 INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@g,@s,@AccessHash,'ACCESS',@scopes,@generation+1,@now,@accessExpiry),(@t,@i,@u,@g,@s,@RefreshHash,'REFRESH',@scopes,@generation+1,@now,@refreshExpiry);
 UPDATE dbo.AiDelegatedSession SET RefreshGeneration=@generation+1,LastRefreshedAt=@now,IdleExpiresAt=@refreshExpiry WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL AND RefreshGeneration=@generation;
 IF @@ROWCOUNT<>1 THROW 58702,'Refresh concurrency conflict.',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@sessionRow,'UPDATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@scopes,N'}'));
 IF @@ROWCOUNT<>1 THROW 58703,'Rotation audit unavailable.',1;
 COMMIT TRANSACTION;
 SELECT CAST(0 AS bit) AS Denied,@now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@scopes AS ScopeMask;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdResolveCodeV1 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdAuthorizationV1 x WHERE x.CodeHash=@Hash
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE PROCEDURE dbo.AiCimdResolveRefreshV1 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiCimdFamilyV1 x ON x.ShaleClientId=c.ShaleClientId AND x.SessionId=c.SessionId AND x.IntegrationId=c.IntegrationId AND x.UserId=c.UserId AND x.GrantId=c.GrantId WHERE c.CredentialHash=@Hash AND c.Kind='REFRESH'
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE PROCEDURE dbo.AiCimdResolveRevocationV1 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiCimdFamilyV1 x ON x.ShaleClientId=c.ShaleClientId AND x.SessionId=c.SessionId AND x.IntegrationId=c.IntegrationId AND x.UserId=c.UserId AND x.GrantId=c.GrantId WHERE c.CredentialHash=@Hash
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE TRIGGER dbo.AiCimdImmutablePublicationV1 ON dbo.AiCimdPublicationV1 AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM inserted) OR UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(MetadataDigest) OR UPDATE(Redirects) OR UPDATE(SigningKeys) THROW 59501,'CIMD publication is immutable.',1; END;
GO
CREATE TRIGGER dbo.AiCimdImmutableKeyBindingV1 ON dbo.AiCimdKeyBindingV1 AFTER UPDATE,DELETE AS THROW 59501,'CIMD key binding is immutable.',1;
GO
CREATE TRIGGER dbo.AiCimdImmutableOAuthReceiptV1 ON dbo.AiCimdOAuthReceiptV1 AFTER UPDATE,DELETE AS THROW 59501,'CIMD OAuth receipt is immutable.',1;
GO
CREATE PROCEDURE dbo.AiCimdLoadClientV1 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Web uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD browser lease required.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 SELECT i.ShaleClientId,i.IntegrationId,e.ExternalClientId AS ClientId,i.Name,i.ClientType,p.ScopeMask,
 CASE WHEN p.ExpiresAt<i.ExpiresAt THEN p.ExpiresAt ELSE i.ExpiresAt END AS ExpiresAt,i.RowVer,e.Issuer,e.Resource,
 r.value AS RedirectUri
 FROM dbo.AiExternalApplication e WITH(HOLDLOCK)
 JOIN dbo.AiTenantApplicationApproval p WITH(HOLDLOCK) ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ApplicationRevision=e.Revision
 JOIN dbo.AiIntegrationRegistration i WITH(HOLDLOCK) ON i.ShaleClientId=@t AND i.IntegrationId=p.IntegrationId
 JOIN dbo.AiCimdPublicationV1 m WITH(HOLDLOCK) ON m.ApplicationId=e.ApplicationId AND m.ApplicationRevision=e.Revision AND m.FreshUntil>@now
 CROSS APPLY OPENJSON(m.Redirects) r
 WHERE e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
 AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
 AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
 AND e.Disabled=0 AND e.Source='CIMD' AND p.State='APPROVED' AND p.ClosedAt IS NULL AND p.ExpiresAt>@now
 AND i.Status='SUSPENDED' AND i.Revision=1 AND i.RevokedAt IS NULL AND i.ExpiresAt>@now
 AND EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@Web AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdLoadClientProofV1 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Kind varchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD issuer lease required.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @a uniqueidentifier,@r bigint,@p uniqueidentifier,@v binary(8),@d binary(32),@e nvarchar(2048),@i uniqueidentifier,@w uniqueidentifier,@sc tinyint,@m varchar(16),@k uniqueidentifier,@deadline datetime2(7);
 IF @Kind='CODE' BEGIN
 SELECT @a=ApplicationId,@r=ApplicationRevision,@p=ApprovalId,@v=ApprovalVersion,@d=MetadataDigest,@e=ExternalClientId,@i=IntegrationId,@w=WebSessionId,@sc=RequestedScopeMask,@m=AuthMethod,@k=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND CodeHash=@Hash;
 END ELSE IF @Kind='REFRESH' BEGIN
 SELECT @a=f.ApplicationId,@r=f.ApplicationRevision,@p=f.ApprovalId,@v=f.ApprovalVersion,@d=f.MetadataDigest,@e=f.ExternalClientId,@i=f.IntegrationId,@w=f.WebSessionId,@sc=s.ScopeMask,@m=f.AuthMethod,@k=f.CredentialId
 FROM dbo.AiCimdFamilyV1 f JOIN dbo.AiCimdCredentialV1 c ON c.ShaleClientId=f.ShaleClientId AND c.SessionId=f.SessionId
 JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=f.ShaleClientId AND s.SessionId=f.SessionId
 WHERE f.ShaleClientId=@t AND f.UserId=@u AND c.CredentialHash=@Hash AND c.Kind='REFRESH';
 END;
 IF @e IS NULL OR @e<>@External COLLATE Latin1_General_100_BIN2 OR DATALENGTH(@e)<>DATALENGTH(@External) THROW 59501,'CIMD client unavailable.',1;
 EXEC dbo.AiCimdOAuthAuthorityV1 @a,@r,@p,@v,@d,@e,@Issuer,@Resource,@i,@w,@sc,@m,@k,1,@deadline OUTPUT;
 SELECT @m AS AuthMethod,@k AS CredentialId,b.Kid,k.Modulus,k.Exponent,k.ValidFrom,k.ExpiresAt AS KeyExpiresAt,@deadline AS Deadline
 FROM (VALUES(1))x(n) LEFT JOIN dbo.AiCimdKeyBindingV1 b ON b.ShaleClientId=@t AND b.IntegrationId=@i AND b.ApplicationId=@a AND b.ApplicationRevision=@r AND b.CredentialId=@k
 LEFT JOIN dbo.AiIntegrationClientKey k ON k.ShaleClientId=@t AND k.IntegrationId=@i AND k.CredentialId=@k;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdResolvePublicationV1 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Methods tinyint
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59501,'Neutral publication lease required.',1;
 SELECT ApplicationId,Revision FROM dbo.AiExternalApplication WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND MetadataDigest=@Digest AND DATALENGTH(@Digest)=32 AND MethodMask=@Methods AND Disabled=0 AND Source='CIMD';
END;
GO
CREATE PROCEDURE dbo.AiCimdReserveAssertionV1 @Application uniqueidentifier,@Revision bigint,@Kid nvarchar(max),@Jti varbinary(max),@Issued datetime2(7),@Expires datetime2(7)
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'Assertion transaction required.',1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@rc int,@lock nvarchar(255)=CONCAT(N'AiCimdAssertionV1:',@Application);
 IF @Application IS NULL OR @Revision IS NULL OR @Kid IS NULL OR DATALENGTH(@Kid) NOT BETWEEN 2 AND 256 OR @Jti IS NULL OR DATALENGTH(@Jti)<>32
 OR @Issued IS NULL OR @Expires IS NULL OR @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued) THROW 59501,'Assertion unavailable.',1;
 EXEC @rc=sys.sp_getapplock @Resource=@lock,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59501,'Assertion reservation unavailable.',1;
 SET @now=SYSUTCDATETIME();
 IF @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued) THROW 59501,'Assertion deadline elapsed.',1;
 DELETE TOP(256) FROM dbo.AiCimdNeutralReplayV1 WHERE ApplicationId=@Application AND ExpiresAt<=@now;
 IF (SELECT COUNT(*) FROM dbo.AiCimdNeutralReplayV1 WHERE ApplicationId=@Application AND ExpiresAt>@now)>=1024 THROW 59501,'CIMD replay capacity unavailable.',1;
 INSERT dbo.AiCimdNeutralReplayV1 VALUES(@Application,@Revision,@Kid,@Jti,@now,@Expires);
 IF @Expires<=SYSUTCDATETIME() THROW 59501,'Assertion deadline elapsed.',1;
END;
GO
CREATE PROCEDURE dbo.AiCimdAuthorizeRevocationV1 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7),@Application uniqueidentifier OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'Revocation authentication transaction required.',1;
 DECLARE @r bigint,@methods tinyint,@keys nvarchar(max),@now datetime2(7);
 SET @Application=NULL;
 SELECT @Application=ApplicationId,@r=Revision,@methods=MethodMask FROM dbo.AiExternalApplication WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND MetadataDigest=@Digest AND DATALENGTH(@Digest)=32 AND Source='CIMD' AND Disabled=0;
 SET @now=SYSUTCDATETIME();
 SELECT @keys=SigningKeys FROM dbo.AiCimdPublicationV1 WITH(HOLDLOCK) WHERE ApplicationId=@Application AND ApplicationRevision=@r AND MetadataDigest=@Digest AND FreshUntil>@now;
 IF @Application IS NULL OR @keys IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR (@Method COLLATE Latin1_General_100_BIN2='none' AND ((@methods & 1)<>1 OR DATALENGTH(@Method)<>4 OR @Kid IS NOT NULL OR @JtiHash IS NOT NULL OR @Issued IS NOT NULL OR @Expires IS NOT NULL))
 OR (@Method COLLATE Latin1_General_100_BIN2='private_key_jwt' AND ((@methods & 2)<>2 OR DATALENGTH(@Method)<>15 OR @Kid IS NULL OR DATALENGTH(@Kid) NOT BETWEEN 2 AND 256
 OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32 OR @Issued IS NULL OR @Expires IS NULL OR @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued)
 OR NOT EXISTS(SELECT 1 FROM OPENJSON(@keys) WITH(kid nvarchar(128)) WHERE kid=@Kid COLLATE Latin1_General_100_BIN2 AND DATALENGTH(kid)=DATALENGTH(@Kid))))
 OR @Method IS NULL OR @Method COLLATE Latin1_General_100_BIN2 NOT IN('none','private_key_jwt') THROW 59501,'CIMD client unavailable.',1;
 IF @Method COLLATE Latin1_General_100_BIN2='private_key_jwt'
 EXEC dbo.AiCimdReserveAssertionV1 @Application,@r,@Kid,@JtiHash,@Issued,@Expires;
END;
GO
CREATE PROCEDURE dbo.AiCimdRevokeUnknownV1 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59501,'Neutral revocation lease required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @application uniqueidentifier;
 EXEC dbo.AiCimdAuthorizeRevocationV1 @External,@Issuer,@Resource,@Digest,@Method,@Kid,@JtiHash,@Issued,@Expires,@application OUTPUT;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiCimdRevokeCredentialV1 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7),@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD revocation lease required.',1;
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @Correlation IS NULL THROW 59501,'CIMD credential unavailable.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@application uniqueidentifier,
 @session uniqueidentifier,@id bigint,@now datetime2(7),@changed int;
 EXEC dbo.AiCimdAuthorizeRevocationV1 @External,@Issuer,@Resource,@Digest,@Method,@Kid,@JtiHash,@Issued,@Expires,@application OUTPUT;
 SELECT @session=s.SessionId,@id=s.Id FROM dbo.AiCimdCredentialV1 c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiCimdFamilyV1 f WITH(HOLDLOCK) ON f.ShaleClientId=c.ShaleClientId AND f.SessionId=c.SessionId AND f.IntegrationId=c.IntegrationId AND f.UserId=c.UserId AND f.GrantId=c.GrantId
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=f.ShaleClientId AND s.SessionId=f.SessionId AND s.GrantId=f.GrantId AND s.IntegrationId=f.IntegrationId AND s.UserId=f.UserId AND s.TransactionId IS NULL
 WHERE c.CredentialHash=@Hash AND c.ShaleClientId=@t AND c.UserId=@u AND f.ApplicationId=@application
 AND f.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.ExternalClientId)=DATALENGTH(@External)
 AND f.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.Issuer)=DATALENGTH(@Issuer)
 AND f.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.Resource)=DATALENGTH(@Resource);
 SET @now=SYSUTCDATETIME();
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason='SELF' WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@session AND RevokedAt IS NULL;
 SET @changed=@@ROWCOUNT;
 UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@session AND RevokedAt IS NULL;
 SET @changed=@changed+@@ROWCOUNT;
 IF @changed>0 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@id,'DEACTIVATED',@Correlation,'SHALE_SERVICE',N'{"ACTIVE":false}');
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD revocation audit unavailable.',1;
 END;
 IF @Method COLLATE Latin1_General_100_BIN2='private_key_jwt' AND @Expires<=SYSUTCDATETIME() THROW 59501,'CIMD assertion expired.',1;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;

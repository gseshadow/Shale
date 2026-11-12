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
/* Cross-tenant and cross-replica JTI reservation: an external client is a global identity. */
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
/* Private current-client authentication seam shared by known and unknown revocation. */
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
/* Unknown-token authentication and replay are tenant-neutral and never pick a tenant creator. */
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
/* Revocation reduces authority. Expired/revoked WEB, approval, subject or family cannot prevent it. */
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

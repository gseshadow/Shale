CREATE TABLE dbo.AiDcrMetadataV2(
 ApplicationId uniqueidentifier NOT NULL, ApplicationRevision bigint NOT NULL,
 MetadataDigest binary(32) NOT NULL, ClientName nvarchar(200) NOT NULL,
 Redirects nvarchar(max) NOT NULL, SigningKeys nvarchar(max) NOT NULL,
 MethodMask tinyint NOT NULL CHECK(MethodMask IN(1,2)), ScopeMask tinyint NOT NULL CHECK(ScopeMask BETWEEN 1 AND 3),
 CreatedAt datetime2(7) NOT NULL, PendingExpiresAt datetime2(7) NOT NULL,
 CONSTRAINT PK_AiDcrMetadataV2 PRIMARY KEY(ApplicationId,ApplicationRevision),
 CONSTRAINT FK_AiDcrMetadataV2_Revision FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiExternalApplicationRevision(ApplicationId,Revision),
 CONSTRAINT CK_AiDcrMetadataV2_Json CHECK(ISJSON(Redirects)=1 AND ISJSON(SigningKeys)=1 AND DATALENGTH(Redirects)<=32768 AND DATALENGTH(SigningKeys)<=16384),
 CONSTRAINT CK_AiDcrMetadataV2_Expiry CHECK(PendingExpiresAt=DATEADD(day,1,CreatedAt))
);
CREATE TABLE dbo.AiExternalControlV2(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),Enabled bit NOT NULL DEFAULT 0);
INSERT dbo.AiExternalControlV2 VALUES(1,0);
CREATE TABLE dbo.AiExternalReceiptV2(
 Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),SourceHash binary(32) NOT NULL,
 OldConstraintId int NOT NULL,OldConstraintDefinition nvarchar(max) NOT NULL,
 HistoricalStateHash binary(32) NOT NULL,CatalogHash binary(32) NOT NULL
);
GO
CREATE TRIGGER dbo.AiDcrImmutableMetadataV2 ON dbo.AiDcrMetadataV2 AFTER UPDATE,DELETE AS
 THROW 59600,'DCR revision metadata is immutable.',1;
GO
CREATE TRIGGER dbo.AiExternalImmutableReceiptV2 ON dbo.AiExternalReceiptV2 AFTER UPDATE,DELETE AS
 THROW 59600,'External compatibility receipt is immutable.',1;
GO
/* Dedicated service identity; no tenant, session, approval or token is an input. */
CREATE PROCEDURE dbo.AiDcrRegisterApplicationV2
 @Issuer nvarchar(max),@Resource nvarchar(max),@Name nvarchar(max),@Redirects nvarchar(max),@Keys nvarchar(max),
 @Methods tinyint,@Scopes tinyint,@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR ISNULL(IS_MEMBER(N'AiDcrRegistrarV2'),0)<>1
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59601,'Fresh neutral DCR registrar lease required.',1;
 IF @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 18 AND 512 OR @Issuer NOT LIKE N'https://%/%'
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 18 AND 512 OR @Resource NOT LIKE N'https://%/%'
 OR @Name IS NULL OR DATALENGTH(@Name) NOT BETWEEN 2 AND 400 OR @Correlation IS NULL
 OR @Methods IS NULL OR @Methods NOT IN(1,2) OR @Scopes IS NULL OR @Scopes NOT BETWEEN 1 AND 3
 OR @Redirects IS NULL OR DATALENGTH(@Redirects)>32768 OR ISJSON(@Redirects)<>1 OR LEFT(@Redirects,1)<>N'['
 OR @Keys IS NULL OR DATALENGTH(@Keys)>16384 OR ISJSON(@Keys)<>1 OR LEFT(@Keys,1)<>N'['
 THROW 59601,'Bounded validated registration metadata required.',1;
 IF (SELECT COUNT(*) FROM OPENJSON(@Redirects)) NOT BETWEEN 1 AND 5
 OR EXISTS(SELECT 1 FROM OPENJSON(@Redirects) WHERE type<>1 OR DATALENGTH(value) NOT BETWEEN 2 AND 4096
 OR value LIKE N'%[#*%]%' OR NOT(value LIKE N'https://%/%' OR @Methods=1 AND (value LIKE N'http://127.0.0.1:%/%' OR value LIKE N'http://[[]::1]:%/%')))
 OR (SELECT COUNT(*) FROM OPENJSON(@Redirects))<>(SELECT COUNT(DISTINCT value COLLATE Latin1_General_100_BIN2) FROM OPENJSON(@Redirects))
 OR (@Methods=1 AND @Keys<>N'[]') OR (@Methods=2 AND (SELECT COUNT(*) FROM OPENJSON(@Keys)) NOT BETWEEN 1 AND 2)
 OR EXISTS(SELECT 1 FROM OPENJSON(@Keys) WITH(kid nvarchar(max),modulus varchar(max),exponent int)
 WHERE kid IS NULL OR DATALENGTH(kid) NOT BETWEEN 2 AND 256 OR exponent<>65537 OR exponent IS NULL
 OR TRY_CONVERT(varbinary(max),modulus,2) IS NULL OR DATALENGTH(TRY_CONVERT(varbinary(max),modulus,2)) NOT BETWEEN 256 AND 512)
 THROW 59601,'Registration metadata profile unavailable.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource=N'AiApplicationRegistryV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR (SELECT COUNT(*) FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE Source='DCR')>=5000
 THROW 59601,'DCR registration capacity unavailable.',1;
 DECLARE @wire nvarchar(68)=N'dcr.'+LOWER(CONVERT(varchar(64),CRYPT_GEN_RANDOM(32),2)),@application uniqueidentifier=NEWID(),@now datetime2(7)=SYSUTCDATETIME();
 DECLARE @digest binary(32)=HASHBYTES('SHA2_256',CONVERT(varbinary(max),CONCAT(DATALENGTH(@Name),N':',@Name,DATALENGTH(@Redirects),N':',@Redirects,DATALENGTH(@Keys),N':',@Keys,N':',@Methods,N':',@Scopes)));
 INSERT dbo.AiExternalApplication(ApplicationId,Issuer,Resource,ExternalClientId,IdentityHash,Source,MetadataDigest,MethodMask)
 VALUES(@application,@Issuer,@Resource,@wire,HASHBYTES('SHA2_256',CONVERT(varbinary(max),@Issuer)+0x0000+CONVERT(varbinary(max),@wire)),'DCR',@digest,@Methods);
 INSERT dbo.AiExternalApplicationRevision(ApplicationId,Revision,MetadataDigest,MethodMask,Disabled) VALUES(@application,1,@digest,@Methods,0);
 INSERT dbo.AiDcrMetadataV2 VALUES(@application,1,@digest,@Name,@Redirects,@Keys,@Methods,@Scopes,@now,DATEADD(day,1,@now));
 INSERT dbo.AiCimdPublicationV1 VALUES(@application,1,@digest,@Redirects,@Keys,DATEADD(minute,4,@now));
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 SELECT USER_NAME(),'AI_EXTERNAL_APPLICATION',Id,'UPDATED',CONCAT(N'{"CORRELATION_ID":"',CONVERT(nvarchar(36),@Correlation),N'"}') FROM dbo.AiExternalApplication WHERE ApplicationId=@application;
 IF @@ROWCOUNT<>1 THROW 59601,'Registration audit unavailable.',1;
 SELECT @wire AS ClientId,@now AS IssuedAt,ApplicationId,Revision,Disabled,RowVer FROM dbo.AiExternalApplication WHERE ApplicationId=@application;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
/* Trusted operator lifecycle only. Anonymous registration cannot update an application. */
CREATE PROCEDURE dbo.AiDcrChangeStateV2 @Application uniqueidentifier,@Disabled bit,@Expected varbinary(max),@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR ISNULL(IS_MEMBER(N'AiDcrAdministratorV2'),0)<>1
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR @Disabled IS NULL OR @Expected IS NULL OR DATALENGTH(@Expected)<>8 OR @Correlation IS NULL
 THROW 59601,'Fresh neutral DCR administrator lease required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock int,@r bigint,@digest binary(32),@methods tinyint;
 EXEC @lock=sys.sp_getapplock @Resource=N'AiApplicationRegistryV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 THROW 59601,'Registry lock unavailable.',1;
 UPDATE dbo.AiExternalApplication SET Disabled=@Disabled,Revision=Revision+1,UpdatedAt=SYSUTCDATETIME()
 WHERE ApplicationId=@Application AND Source='DCR' AND RowVer=@Expected;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR application unavailable or stale.',1;
 SELECT @r=Revision,@digest=MetadataDigest,@methods=MethodMask FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 INSERT dbo.AiExternalApplicationRevision(ApplicationId,Revision,MetadataDigest,MethodMask,Disabled) VALUES(@Application,@r,@digest,@methods,@Disabled);
 INSERT dbo.AiDcrMetadataV2 SELECT ApplicationId,@r,MetadataDigest,ClientName,Redirects,SigningKeys,MethodMask,ScopeMask,CreatedAt,PendingExpiresAt
 FROM dbo.AiDcrMetadataV2 WHERE ApplicationId=@Application AND ApplicationRevision=@r-1;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR revision metadata unavailable.',1;
 INSERT dbo.AiCimdPublicationV1 SELECT ApplicationId,@r,MetadataDigest,Redirects,SigningKeys,DATEADD(minute,4,SYSUTCDATETIME())
 FROM dbo.AiDcrMetadataV2 WHERE ApplicationId=@Application AND ApplicationRevision=@r;
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 SELECT USER_NAME(),'AI_EXTERNAL_APPLICATION',Id,'UPDATED',CONCAT(N'{"CORRELATION_ID":"',CONVERT(nvarchar(36),@Correlation),N'"}') FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR application audit unavailable.',1;
 SELECT ApplicationId,Revision,Disabled,RowVer FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
/* Public metadata only; original stored bytes renew a cache, never a supplied document. */
CREATE PROCEDURE dbo.AiDcrPrepareMetadataV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR @External IS NULL OR DATALENGTH(@External)<>136 OR LEFT(@External,4) COLLATE Latin1_General_100_BIN2<>N'dcr.'
 OR SUBSTRING(@External,5,64) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9a-f]%'
 THROW 59601,'Neutral exact DCR identity required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @a uniqueidentifier,@r bigint,@now datetime2(7)=SYSUTCDATETIME();
 SELECT @a=ApplicationId,@r=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource) AND Source='DCR' AND Disabled=0;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 m JOIN dbo.AiExternalApplication e ON e.ApplicationId=m.ApplicationId AND e.Revision=m.ApplicationRevision AND e.MetadataDigest=m.MetadataDigest
 /* Neutral retention checks only existence of historical use for this exact public application.
    Owner context is the established bounded pretenant projection pattern. It returns no tenant data. */
 WHERE m.ApplicationId=@a AND m.ApplicationRevision=@r AND (m.PendingExpiresAt>@now OR EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WHERE p.ApplicationId=@a AND p.IntegrationId IS NOT NULL)))
 THROW 59601,'Current DCR metadata unavailable.',1;
 UPDATE p SET FreshUntil=DATEADD(minute,4,@now) FROM dbo.AiCimdPublicationV1 p
 JOIN dbo.AiDcrMetadataV2 m ON m.ApplicationId=p.ApplicationId AND m.ApplicationRevision=p.ApplicationRevision AND m.MetadataDigest=p.MetadataDigest
 WHERE p.ApplicationId=@a AND p.ApplicationRevision=@r AND p.Redirects=m.Redirects COLLATE Latin1_General_100_BIN2 AND DATALENGTH(p.Redirects)=DATALENGTH(m.Redirects)
 AND p.SigningKeys=m.SigningKeys COLLATE Latin1_General_100_BIN2 AND DATALENGTH(p.SigningKeys)=DATALENGTH(m.SigningKeys);
 IF @@ROWCOUNT<>1 THROW 59601,'DCR stored publication unavailable.',1;
 SELECT e.ExternalClientId,m.*,p.FreshUntil FROM dbo.AiDcrMetadataV2 m JOIN dbo.AiExternalApplication e ON e.ApplicationId=m.ApplicationId AND e.Revision=m.ApplicationRevision
 JOIN dbo.AiCimdPublicationV1 p ON p.ApplicationId=m.ApplicationId AND p.ApplicationRevision=m.ApplicationRevision
 WHERE m.ApplicationId=@a AND m.ApplicationRevision=@r;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;

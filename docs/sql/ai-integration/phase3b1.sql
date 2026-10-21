/* Phase 3B.1 CANDIDATE. Manual/local only; never startup/deploy automatically.
   One transaction, additive and fail-fast. See README for authority and retained-history rollback. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
IF USER_NAME()<>N'dbo' OR COALESCE(USER_NAME((SELECT principal_id FROM sys.schemas WHERE name=N'dbo')),N'')<>N'dbo'
 THROW 58420,'Reviewed dbo migration ownership required.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 58420,'Fresh unscoped migration session required.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @migrationLock int;
EXEC @migrationLock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @migrationLock<0 THROW 58420,'AI migration lock unavailable.',1;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL
 OR COL_LENGTH(N'dbo.ShaleClients',N'IsActive') IS NULL OR COL_LENGTH(N'dbo.Users',N'IsRemoved') IS NULL
 OR COL_LENGTH(N'dbo.Users',N'is_deleted') IS NULL OR COL_LENGTH(N'dbo.Users',N'is_admin') IS NULL
 OR OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL OR OBJECT_ID(N'dbo.GlobalControlPlaneAuditLog',N'U') IS NULL
 OR OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL
 THROW 58421,'AI schema dependencies missing.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND i.is_disabled=0
 AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)=2
 AND EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=1 AND c.name=N'ShaleClientId')
 AND EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=2 AND c.name=N'id'))
 THROW 58421,'Tenant-qualified Users key missing.',1;
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1 AND is_schema_bound=1)<>1
 THROW 58421,'Enabled schema-bound TenantFilter required.',1;
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
IF @receipt IS NOT NULL BEGIN
 IF @receipt<>@fingerprint OR ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability') AND name=N'Shale.AiFoundationV1.Source'),0x)<>0x04080764f9057fbe992e44929f1199f0425964961ef42e6970d35c71ebc22b83 THROW 58422,'AI catalog drift; stop for review.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(SELECT Name FROM @Owned)) THROW 58422,'Unreceipted AI object; stop for review.',1;
CREATE TABLE dbo.AiProductCapability(
 Id int NOT NULL CONSTRAINT PK_AiProductCapability PRIMARY KEY CONSTRAINT CK_AiProductCapability_01 CHECK(Id=1), Enabled bit NOT NULL CONSTRAINT DF_AiProductCapability_01 DEFAULT(0),
 Revision bigint NOT NULL CONSTRAINT DF_AiProductCapability_02 DEFAULT(1) CONSTRAINT CK_AiProductCapability_02 CHECK(Revision>0), UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_AiProductCapability_03 DEFAULT(SYSUTCDATETIME()),
 UpdatedByOperator nvarchar(128) NULL, RowVer rowversion NOT NULL
);
CREATE TABLE dbo.TenantAiIntegrationPolicy(
 Id bigint IDENTITY CONSTRAINT PK_TenantAiIntegrationPolicy PRIMARY KEY, ShaleClientId int NOT NULL CONSTRAINT UQ_TenantAiIntegrationPolicy_01 UNIQUE CONSTRAINT FK_TenantAiIntegrationPolicy_02 REFERENCES dbo.ShaleClients(Id),
 Enabled bit NOT NULL CONSTRAINT DF_TenantAiIntegrationPolicy_01 DEFAULT(0), Revision bigint NOT NULL CONSTRAINT DF_TenantAiIntegrationPolicy_02 DEFAULT(1) CONSTRAINT CK_TenantAiIntegrationPolicy_01 CHECK(Revision>0),
 UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_TenantAiIntegrationPolicy_03 DEFAULT(SYSUTCDATETIME()), UpdatedByUserId int NOT NULL,
 RowVer rowversion NOT NULL,
 CONSTRAINT FK_TenantAiIntegrationPolicy_01 FOREIGN KEY(ShaleClientId,UpdatedByUserId) REFERENCES dbo.Users(ShaleClientId,id)
);
CREATE TABLE dbo.AiIntegrationRegistration(
 Id bigint IDENTITY CONSTRAINT PK_AiIntegrationRegistration PRIMARY KEY, ShaleClientId int NOT NULL CONSTRAINT FK_AiIntegrationRegistration_04 REFERENCES dbo.ShaleClients(Id),
 IntegrationId uniqueidentifier NOT NULL CONSTRAINT DF_AiIntegrationRegistration_01 DEFAULT(NEWID()) CONSTRAINT UQ_AiIntegrationRegistration_01 UNIQUE, ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT UQ_AiIntegrationRegistration_02 UNIQUE,
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL, Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Name nvarchar(100) NOT NULL CONSTRAINT CK_AiIntegrationRegistration_01 CHECK(LEN(Name)>0), ClientType varchar(16) NOT NULL CONSTRAINT CK_AiIntegrationRegistration_02 CHECK(ClientType IN('PUBLIC','CONFIDENTIAL')),
 Status varchar(16) NOT NULL CONSTRAINT DF_AiIntegrationRegistration_02 DEFAULT('ACTIVE') CONSTRAINT CK_AiIntegrationRegistration_03 CHECK(Status IN('ACTIVE','SUSPENDED','REVOKED')),
 ScopeMask tinyint NOT NULL CONSTRAINT CK_AiIntegrationRegistration_04 CHECK(ScopeMask BETWEEN 0 AND 3),
 CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_AiIntegrationRegistration_03 DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NOT NULL,
 UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_AiIntegrationRegistration_04 DEFAULT(SYSUTCDATETIME()), UpdatedByUserId int NOT NULL,
 ExpiresAt datetime2(7) NOT NULL, RevokedAt datetime2(7) NULL, RevokedByUserId int NULL,
 RevocationReason varchar(16) NULL CONSTRAINT CK_AiIntegrationRegistration_05 CHECK(RevocationReason IN('ADMIN','SECURITY','DISABLED')),
 Revision bigint NOT NULL CONSTRAINT DF_AiIntegrationRegistration_05 DEFAULT(1) CONSTRAINT CK_AiIntegrationRegistration_06 CHECK(Revision>0), RowVer rowversion NOT NULL,
 CONSTRAINT UQ_AiIntegrationRegistration_03 UNIQUE(ShaleClientId,IntegrationId),
 CONSTRAINT CK_AiIntegrationRegistration_07 CHECK(ExpiresAt>CreatedAt AND ExpiresAt<=DATEADD(day,90,CreatedAt)),
 CONSTRAINT CK_AiIntegrationRegistration_08 CHECK((Status='REVOKED' AND RevokedAt IS NOT NULL AND RevocationReason IS NOT NULL AND RevokedByUserId IS NOT NULL) OR
       (Status<>'REVOKED' AND RevokedAt IS NULL AND RevocationReason IS NULL AND RevokedByUserId IS NULL)),
 CONSTRAINT FK_AiIntegrationRegistration_01 FOREIGN KEY(ShaleClientId,CreatedByUserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiIntegrationRegistration_02 FOREIGN KEY(ShaleClientId,UpdatedByUserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiIntegrationRegistration_03 FOREIGN KEY(ShaleClientId,RevokedByUserId) REFERENCES dbo.Users(ShaleClientId,id)
);
CREATE TABLE dbo.AiIntegrationRedirect(
 ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, Position tinyint NOT NULL CONSTRAINT CK_AiIntegrationRedirect_01 CHECK(Position BETWEEN 1 AND 5),
 RedirectUri nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_AiIntegrationRedirect PRIMARY KEY(ShaleClientId,IntegrationId,Position),
 CONSTRAINT CK_AiIntegrationRedirect_02 CHECK(LEN(RedirectUri)>0 AND CHARINDEX(N'#',RedirectUri)=0 AND CHARINDEX(N'*',RedirectUri)=0),
 CONSTRAINT FK_AiIntegrationRedirect_01 FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId)
);
CREATE TABLE dbo.AiIntegrationClientKey(
 Id bigint IDENTITY CONSTRAINT PK_AiIntegrationClientKey PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL,
 CredentialId uniqueidentifier NOT NULL CONSTRAINT DF_AiIntegrationClientKey_01 DEFAULT(NEWID()) CONSTRAINT UQ_AiIntegrationClientKey_01 UNIQUE,
 Modulus varbinary(512) NOT NULL CONSTRAINT CK_AiIntegrationClientKey_01 CHECK(DATALENGTH(Modulus) BETWEEN 256 AND 512),
 Exponent int NOT NULL CONSTRAINT CK_AiIntegrationClientKey_02 CHECK(Exponent=65537), Algorithm varchar(8) NOT NULL CONSTRAINT DF_AiIntegrationClientKey_02 DEFAULT('RS256') CONSTRAINT CK_AiIntegrationClientKey_03 CHECK(Algorithm='RS256'),
 Fingerprint AS CONVERT(binary(32),HASHBYTES('SHA2_256',Modulus)) PERSISTED,
 CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_AiIntegrationClientKey_03 DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NOT NULL,
 ValidFrom datetime2(7) NOT NULL, ExpiresAt datetime2(7) NOT NULL,
 RevokedAt datetime2(7) NULL, RevokedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT UQ_AiIntegrationClientKey_02 UNIQUE(ShaleClientId,IntegrationId,CredentialId), CONSTRAINT UQ_AiIntegrationClientKey_03 UNIQUE(ShaleClientId,IntegrationId,Fingerprint),
 CONSTRAINT CK_AiIntegrationClientKey_04 CHECK(ValidFrom>=CreatedAt AND ExpiresAt>ValidFrom AND ExpiresAt<=DATEADD(day,90,CreatedAt)),
 CONSTRAINT CK_AiIntegrationClientKey_05 CHECK((RevokedAt IS NULL AND RevokedByUserId IS NULL) OR (RevokedAt IS NOT NULL AND RevokedByUserId IS NOT NULL)),
 CONSTRAINT FK_AiIntegrationClientKey_01 FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId),
 CONSTRAINT FK_AiIntegrationClientKey_02 FOREIGN KEY(ShaleClientId,CreatedByUserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiIntegrationClientKey_03 FOREIGN KEY(ShaleClientId,RevokedByUserId) REFERENCES dbo.Users(ShaleClientId,id)
);
CREATE TABLE dbo.AiDelegationGrant(
 Id bigint IDENTITY CONSTRAINT PK_AiDelegationGrant PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL,
 GrantId uniqueidentifier NOT NULL CONSTRAINT DF_AiDelegationGrant_01 DEFAULT(NEWID()) CONSTRAINT UQ_AiDelegationGrant_01 UNIQUE,
 ScopeMask tinyint NOT NULL CONSTRAINT CK_AiDelegationGrant_01 CHECK(ScopeMask BETWEEN 1 AND 3), PolicyVersion varchar(24) NOT NULL CONSTRAINT DF_AiDelegationGrant_02 DEFAULT('case-read-v1') CONSTRAINT CK_AiDelegationGrant_02 CHECK(PolicyVersion='case-read-v1'),
 ConsentedAt datetime2(7) NOT NULL CONSTRAINT DF_AiDelegationGrant_03 DEFAULT(SYSUTCDATETIME()), ExpiresAt datetime2(7) NOT NULL,
 RevokedAt datetime2(7) NULL, RevokedByUserId int NULL, RevocationReason varchar(16) NULL,
 Revision bigint NOT NULL CONSTRAINT DF_AiDelegationGrant_04 DEFAULT(1) CONSTRAINT CK_AiDelegationGrant_03 CHECK(Revision>0), RowVer rowversion NOT NULL,
 CONSTRAINT UQ_AiDelegationGrant_02 UNIQUE(ShaleClientId,GrantId,IntegrationId,UserId),
 CONSTRAINT CK_AiDelegationGrant_04 CHECK(ExpiresAt>ConsentedAt AND ExpiresAt<=DATEADD(hour,24,ConsentedAt)),
 CONSTRAINT CK_AiDelegationGrant_05 CHECK((RevokedAt IS NULL AND RevokedByUserId IS NULL AND RevocationReason IS NULL) OR
       (RevokedAt IS NOT NULL AND RevokedByUserId IS NOT NULL AND RevocationReason IN('SELF','ADMIN','SECURITY','DISABLED'))),
 CONSTRAINT FK_AiDelegationGrant_01 FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId),
 CONSTRAINT FK_AiDelegationGrant_02 FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiDelegationGrant_03 FOREIGN KEY(ShaleClientId,RevokedByUserId) REFERENCES dbo.Users(ShaleClientId,id)
);
CREATE TABLE dbo.AiConsentIntent(
 Id bigint IDENTITY CONSTRAINT PK_AiConsentIntent PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL,
 IntentHash binary(32) NOT NULL CONSTRAINT UQ_AiConsentIntent_01 UNIQUE, ScopeMask tinyint NOT NULL CONSTRAINT CK_AiConsentIntent_01 CHECK(ScopeMask BETWEEN 1 AND 3),
 CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_AiConsentIntent_01 DEFAULT(SYSUTCDATETIME()), ExpiresAt datetime2(7) NOT NULL,
 GrantExpiresAt datetime2(7) NOT NULL, ConsumedAt datetime2(7) NULL,
 CONSTRAINT CK_AiConsentIntent_02 CHECK(ExpiresAt>CreatedAt AND ExpiresAt<=DATEADD(minute,5,CreatedAt)),
 CONSTRAINT CK_AiConsentIntent_03 CHECK(GrantExpiresAt>ExpiresAt AND GrantExpiresAt<=DATEADD(hour,24,CreatedAt)),
 CONSTRAINT FK_AiConsentIntent_01 FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId),
 CONSTRAINT FK_AiConsentIntent_02 FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,id)
);
CREATE TABLE dbo.AiDelegatedSession(
 Id bigint IDENTITY CONSTRAINT PK_AiDelegatedSession PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL,
 GrantId uniqueidentifier NOT NULL, SessionId uniqueidentifier NOT NULL CONSTRAINT DF_AiDelegatedSession_01 DEFAULT(NEWID()) CONSTRAINT UQ_AiDelegatedSession_01 UNIQUE,
 CredentialId uniqueidentifier NULL, ScopeMask tinyint NOT NULL CONSTRAINT CK_AiDelegatedSession_01 CHECK(ScopeMask BETWEEN 1 AND 3),
 IssuedAt datetime2(7) NOT NULL CONSTRAINT DF_AiDelegatedSession_02 DEFAULT(SYSUTCDATETIME()), ExpiresAt datetime2(7) NOT NULL,
 IdleExpiresAt datetime2(7) NOT NULL, RefreshGeneration int NOT NULL CONSTRAINT DF_AiDelegatedSession_03 DEFAULT(0) CONSTRAINT CK_AiDelegatedSession_02 CHECK(RefreshGeneration>=0),
 LastRefreshedAt datetime2(7) NULL, RevokedAt datetime2(7) NULL, RevocationReason varchar(16) NULL,
 RowVer rowversion NOT NULL,
 CONSTRAINT UQ_AiDelegatedSession_02 UNIQUE(ShaleClientId,SessionId,GrantId,IntegrationId,UserId),
 CONSTRAINT CK_AiDelegatedSession_03 CHECK(ExpiresAt>IssuedAt AND ExpiresAt<=DATEADD(hour,24,IssuedAt)),
 CONSTRAINT CK_AiDelegatedSession_04 CHECK(IdleExpiresAt<=ExpiresAt AND IdleExpiresAt>IssuedAt AND IdleExpiresAt<=DATEADD(hour,8,COALESCE(LastRefreshedAt,IssuedAt))),
 CONSTRAINT CK_AiDelegatedSession_05 CHECK((RevokedAt IS NULL AND RevocationReason IS NULL) OR (RevokedAt IS NOT NULL AND RevocationReason IN('SELF','ADMIN','SECURITY','REUSE','DISABLED'))),
 CONSTRAINT FK_AiDelegatedSession_01 FOREIGN KEY(ShaleClientId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegationGrant(ShaleClientId,GrantId,IntegrationId,UserId),
 CONSTRAINT FK_AiDelegatedSession_02 FOREIGN KEY(ShaleClientId,IntegrationId,CredentialId) REFERENCES dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId)
);
CREATE TABLE dbo.AiOAuthCredential(
 Id bigint IDENTITY CONSTRAINT PK_AiOAuthCredential PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL,
 GrantId uniqueidentifier NOT NULL, SessionId uniqueidentifier NOT NULL,
 CredentialHash binary(32) NOT NULL CONSTRAINT UQ_AiOAuthCredential_01 UNIQUE, Kind varchar(8) NOT NULL CONSTRAINT CK_AiOAuthCredential_01 CHECK(Kind IN('ACCESS','REFRESH')),
 ScopeMask tinyint NOT NULL CONSTRAINT CK_AiOAuthCredential_02 CHECK(ScopeMask BETWEEN 1 AND 3), Generation int NOT NULL CONSTRAINT CK_AiOAuthCredential_03 CHECK(Generation>=0),
 IssuedAt datetime2(7) NOT NULL CONSTRAINT DF_AiOAuthCredential_01 DEFAULT(SYSUTCDATETIME()), ExpiresAt datetime2(7) NOT NULL,
 ConsumedAt datetime2(7) NULL, RevokedAt datetime2(7) NULL,
 CONSTRAINT CK_AiOAuthCredential_04 CHECK(ExpiresAt>IssuedAt AND ExpiresAt<=DATEADD(hour,24,IssuedAt)),
 CONSTRAINT CK_AiOAuthCredential_05 CHECK(Kind<>'ACCESS' OR ExpiresAt<=DATEADD(minute,5,IssuedAt)),
 CONSTRAINT FK_AiOAuthCredential_01 FOREIGN KEY(ShaleClientId,SessionId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegatedSession(ShaleClientId,SessionId,GrantId,IntegrationId,UserId)
);
CREATE TABLE dbo.AiQuotaReservation(
 Id bigint IDENTITY CONSTRAINT PK_AiQuotaReservation PRIMARY KEY, ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, UserId int NOT NULL,
 GrantId uniqueidentifier NOT NULL, SessionId uniqueidentifier NOT NULL, InvocationId uniqueidentifier NOT NULL CONSTRAINT UQ_AiQuotaReservation_01 UNIQUE,
 Operation varchar(16) NOT NULL CONSTRAINT CK_AiQuotaReservation_01 CHECK(Operation IN('SEARCH','OVERVIEW')), RowCost tinyint NOT NULL,
 Admitted bit NOT NULL, AdmittedAt datetime2(7) NOT NULL CONSTRAINT DF_AiQuotaReservation_01 DEFAULT(SYSUTCDATETIME()), LeaseExpiresAt datetime2(7) NOT NULL,
 ReleasedAt datetime2(7) NULL,
 CONSTRAINT CK_AiQuotaReservation_02 CHECK((Admitted=0 AND RowCost=0) OR (Admitted=1 AND ((Operation='SEARCH' AND RowCost=10) OR (Operation='OVERVIEW' AND RowCost=1)))),
 CONSTRAINT CK_AiQuotaReservation_03 CHECK(LeaseExpiresAt>=AdmittedAt AND LeaseExpiresAt<=DATEADD(second,15,AdmittedAt)),
 CONSTRAINT FK_AiQuotaReservation_01 FOREIGN KEY(ShaleClientId,SessionId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegatedSession(ShaleClientId,SessionId,GrantId,IntegrationId,UserId)
);
INSERT dbo.AiProductCapability(Id) VALUES(1);
CREATE INDEX IX_AiQuotaReservation_TenantTime ON dbo.AiQuotaReservation(ShaleClientId,AdmittedAt) INCLUDE(UserId,IntegrationId,RowCost,Admitted,LeaseExpiresAt,ReleasedAt);
CREATE UNIQUE INDEX UX_AiCredential_RefreshGeneration ON dbo.AiOAuthCredential(ShaleClientId,SessionId,Generation) WHERE Kind='REFRESH';
CREATE INDEX IX_AiGrant_TenantUser ON dbo.AiDelegationGrant(ShaleClientId,UserId,IntegrationId);
CREATE INDEX IX_AiSession_TenantUser ON dbo.AiDelegatedSession(ShaleClientId,UserId,GrantId);
DECLARE @policy nvarchar(517)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1),@sql nvarchar(max);
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TenantAiIntegrationPolicy;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TenantAiIntegrationPolicy AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TenantAiIntegrationPolicy AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRegistration;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRegistration AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRegistration AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRedirect;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRedirect AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationRedirect AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationClientKey;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationClientKey AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiIntegrationClientKey AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegationGrant;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegationGrant AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegationGrant AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiConsentIntent;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiConsentIntent AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiConsentIntent AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegatedSession;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegatedSession AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiDelegatedSession AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiOAuthCredential;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiOAuthCredential AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiOAuthCredential AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiQuotaReservation;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiQuotaReservation AFTER INSERT;'; EXEC sys.sp_executesql @sql;
SET @sql=N'ALTER SECURITY POLICY '+@policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiQuotaReservation AFTER UPDATE;'; EXEC sys.sp_executesql @sql;
DECLARE @AuditObjectId int=OBJECT_ID(N'dbo.EntityActionAuditLog'),@EntityTypeColumnId int=COLUMNPROPERTY(OBJECT_ID(N'dbo.EntityActionAuditLog'),N'EntityType',N'ColumnId');
    DECLARE @Checks table
    (
        ObjectId int NOT NULL PRIMARY KEY,
        ConstraintName sysname NOT NULL,
        ConstraintDefinition nvarchar(max) NOT NULL,
        IsDisabled bit NOT NULL,
        IsNotTrusted bit NOT NULL,
        Normalized nvarchar(max) NULL,
        IsPositiveAllowlist bit NOT NULL DEFAULT (0)
    );

    /* Inventory all EntityType checks by authoritative table/column dependency, not by name. */
    INSERT @Checks (ObjectId, ConstraintName, ConstraintDefinition, IsDisabled, IsNotTrusted)
    SELECT DISTINCT cc.object_id, cc.name, cc.definition, cc.is_disabled, cc.is_not_trusted
    FROM sys.check_constraints AS cc
    JOIN sys.sql_expression_dependencies AS dep
      ON dep.referencing_id = cc.object_id
     AND dep.referenced_id = @AuditObjectId
     AND dep.referenced_minor_id = @EntityTypeColumnId
    WHERE cc.parent_object_id = @AuditObjectId;
    IF NOT EXISTS (SELECT 1 FROM @Checks)
        THROW 54822, 'No CHECK constraint references dbo.EntityActionAuditLog.EntityType.', 1;

    /* Mixed-column checks are unrelated invariants and are never replacement candidates. */
    UPDATE c
    SET Normalized = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        c.ConstraintDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''), N']', N''))
    FROM @Checks AS c
    WHERE NOT EXISTS
    (
        SELECT 1
        FROM sys.sql_expression_dependencies AS dep
        WHERE dep.referencing_id = c.ObjectId
          AND dep.referenced_id = @AuditObjectId
          AND dep.referenced_minor_id > 0
          AND dep.referenced_minor_id <> @EntityTypeColumnId
    );

    DECLARE @Extracted table
    (
        ConstraintObjectId int NOT NULL,
        Value varchar(64) NOT NULL,
        PRIMARY KEY (ConstraintObjectId, Value)
    );

    /*
     * Recognize only these positive grammars after whitespace/bracket/parenthesis normalization:
     *   EntityType IN ('A','B')
     *   EntityType = 'A' OR EntityType = 'B'
     * Every token and delimiter is consumed. NOT, <>, functions, extra comparisons, malformed
     * literals, and mixed expressions therefore cannot contribute values.
     */
    DECLARE @CheckId int, @Expression nvarchar(max), @Work nvarchar(max), @Token nvarchar(4000);
    DECLARE @QuoteEnd int, @Valid bit;
    DECLARE check_parser CURSOR LOCAL FAST_FORWARD FOR
        SELECT ObjectId, REPLACE(REPLACE(Normalized, N'(', N''), N')', N'')
        FROM @Checks WHERE Normalized IS NOT NULL ORDER BY ConstraintName;
    OPEN check_parser;
    FETCH NEXT FROM check_parser INTO @CheckId, @Expression;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Valid = 1;
        IF LEFT(@Expression, 12) = N'ENTITYTYPEIN'
        BEGIN
            SET @Work = SUBSTRING(@Expression, 13, LEN(@Expression));
            WHILE LEN(@Work) > 0 AND @Valid = 1
            BEGIN
                IF LEFT(@Work, 1) <> N'''' SET @Valid = 0;
                ELSE
                BEGIN
                    SET @QuoteEnd = CHARINDEX(N'''', @Work, 2);
                    IF @QuoteEnd = 0 SET @Valid = 0;
                    ELSE
                    BEGIN
                        SET @Token = SUBSTRING(@Work, 2, @QuoteEnd - 2);
                        IF LEN(@Token) = 0 OR LEN(@Token) > 64
                           OR @Token COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z_]%'
                            SET @Valid = 0;
                        ELSE
                            INSERT @Extracted (ConstraintObjectId, Value)
                            VALUES (@CheckId, CONVERT(varchar(64), @Token));
                        SET @Work = SUBSTRING(@Work, @QuoteEnd + 1, LEN(@Work));
                        IF LEN(@Work) > 0
                        BEGIN
                            IF LEFT(@Work, 1) <> N',' SET @Valid = 0;
                            ELSE SET @Work = SUBSTRING(@Work, 2, LEN(@Work));
                        END;
                    END;
                END;
            END;
        END
        ELSE IF LEFT(@Expression, 11) = N'ENTITYTYPE='
        BEGIN
            SET @Work = @Expression;
            WHILE LEN(@Work) > 0 AND @Valid = 1
            BEGIN
                IF LEFT(@Work, 12) <> N'ENTITYTYPE=''' SET @Valid = 0;
                ELSE
                BEGIN
                    SET @Work = SUBSTRING(@Work, 13, LEN(@Work));
                    SET @QuoteEnd = CHARINDEX(N'''', @Work);
                    IF @QuoteEnd = 0 SET @Valid = 0;
                    ELSE
                    BEGIN
                        SET @Token = LEFT(@Work, @QuoteEnd - 1);
                        IF LEN(@Token) = 0 OR LEN(@Token) > 64
                           OR @Token COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z_]%'
                            SET @Valid = 0;
                        ELSE
                            INSERT @Extracted (ConstraintObjectId, Value)
                            VALUES (@CheckId, CONVERT(varchar(64), @Token));
                        SET @Work = SUBSTRING(@Work, @QuoteEnd + 1, LEN(@Work));
                        IF LEN(@Work) > 0
                        BEGIN
                            IF LEFT(@Work, 2) <> N'OR' SET @Valid = 0;
                            ELSE SET @Work = SUBSTRING(@Work, 3, LEN(@Work));
                        END;
                    END;
                END;
            END;
        END
        ELSE SET @Valid = 0;

        IF @Valid = 1
           /* The Phase 6.1 deployed allowlist necessarily contained all three foundation values. */
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'LINK_TYPE')
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'CASE_LINK')
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'CASE_LINK_SHARE')
            UPDATE @Checks SET IsPositiveAllowlist = 1 WHERE ObjectId = @CheckId;
        ELSE
            DELETE FROM @Extracted WHERE ConstraintObjectId = @CheckId;

        FETCH NEXT FROM check_parser INTO @CheckId, @Expression;
    END;
    CLOSE check_parser;
    DEALLOCATE check_parser;

    /* Exactly one positive allowlist must be identifiable. Other EntityType checks remain untouched. */
    IF (SELECT COUNT(*) FROM @Checks WHERE IsPositiveAllowlist = 1) <> 1
        THROW 54823, 'EntityType allowlist discovery is missing or ambiguous; no constraints were changed.', 1;

    DECLARE @AllowlistObjectId int, @AllowlistName sysname;
    SELECT @AllowlistObjectId = ObjectId, @AllowlistName = ConstraintName
    FROM @Checks WHERE IsPositiveAllowlist = 1;

    DECLARE @Allowed table (Value varchar(64) NOT NULL PRIMARY KEY);
    /* Complete current vocabulary and every value known to have been allowed by deployed migrations. */
    INSERT @Allowed (Value) VALUES ('TENANT_AI_POLICY'),('AI_INTEGRATION'),('AI_CLIENT_KEY'),('AI_DELEGATION_GRANT'),('AI_DELEGATED_SESSION');

    /* Preserve extras only from the single expression whose entire positive allowlist grammar was validated. */
    INSERT @Allowed (Value)
    SELECT e.Value FROM @Extracted AS e
    WHERE e.ConstraintObjectId = @AllowlistObjectId
      AND NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = e.Value);

    /* Preflight existing data before any DDL. Data is never changed by this migration. */
    IF EXISTS
    (
        SELECT 1 FROM dbo.EntityActionAuditLog AS audit_row
        WHERE NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = audit_row.EntityType)
    )
        THROW 54824, 'Existing EntityActionAuditLog rows contain an EntityType outside the resulting allowlist.', 1;

    DECLARE @auditValues nvarchar(max) =
    (
        SELECT STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(Value, '''')), N',')
               WITHIN GROUP (ORDER BY Value) FROM @Allowed
    );
    DECLARE @auditSql nvarchar(max) = N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '
        + QUOTENAME(@AllowlistName) + N';';
    EXEC sys.sp_executesql @auditSql;
    SET @auditSql = N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '
        + QUOTENAME(N'CK_EntityActionAuditLog_EntityType')
        + N' CHECK (EntityType IN (' + @auditValues + N'));';
    EXEC sys.sp_executesql @auditSql;
    ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;

    /* One canonical trusted allowlist must exist; every unrelated pre-existing check must be unchanged. */
    IF (SELECT COUNT(*) FROM sys.check_constraints
        WHERE parent_object_id = @AuditObjectId
          AND name = N'CK_EntityActionAuditLog_EntityType'
          AND is_disabled = 0 AND is_not_trusted = 0) <> 1
        THROW 54825, 'The canonical EntityType allowlist is not uniquely enabled and trusted.', 1;
    IF EXISTS
    (
        SELECT 1 FROM @Checks AS before_check
        WHERE before_check.ObjectId <> @AllowlistObjectId
          AND NOT EXISTS
          (
              SELECT 1 FROM sys.check_constraints AS after_check
              WHERE after_check.object_id = before_check.ObjectId
                AND after_check.parent_object_id = @AuditObjectId
                AND after_check.name = before_check.ConstraintName
                AND after_check.definition = before_check.ConstraintDefinition
                AND after_check.is_disabled = before_check.IsDisabled
                AND after_check.is_not_trusted = before_check.IsNotTrusted
          )
    )
        THROW 54826, 'An unrelated EntityType CHECK changed unexpectedly.', 1;

EXEC(N'CREATE PROCEDURE dbo.AiLockV1
@Exclusive bit=1
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @@TRANCOUNT<>1 THROW 58400,''AI operation requires its own transaction.'',1;
 DECLARE @rc int,@mode varchar(16)=CASE WHEN @Exclusive=1 THEN ''Exclusive'' ELSE ''Shared'' END;
 EXEC @rc=sys.sp_getapplock @Resource=N''AiCapabilityV1'',@LockMode=''Shared'',@LockOwner=''Transaction'',@LockTimeout=3000;
 IF @rc<0 THROW 58401,''AI lock unavailable.'',1;
 DECLARE @tenant int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId''));
 IF @tenant IS NULL OR @tenant<=0 THROW 58402,''AI context unavailable.'',1;
 DECLARE @name nvarchar(255)=CONCAT(N''AiTenantV1:'',@tenant);
 EXEC @rc=sys.sp_getapplock @Resource=@name,@LockMode=@mode,@LockOwner=''Transaction'',@LockTimeout=3000;
 IF @rc<0 THROW 58401,''AI lock unavailable.'',1;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiActorV1
@Admin bit=0
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId''));
 IF @t IS NULL OR @u IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.Users u WITH(HOLDLOCK)
   JOIN dbo.ShaleClients t WITH(HOLDLOCK) ON t.Id=u.ShaleClientId AND t.IsActive=1
   WHERE u.id=@u AND u.ShaleClientId=@t AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0
   AND (@Admin=0 OR u.is_admin=1)) THROW 58403,''AI actor unavailable.'',1;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiEnabledV1

AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND Enabled=1)
 THROW 58404,''AI capability unavailable.'',1;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiSetProductCapabilityV1
@Enabled bit,@ExpectedRowVer varbinary(8),@CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF ISNULL(IS_MEMBER(N''AiCapabilityOperator''),0)<>1 OR SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL
 OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL OR @Enabled IS NULL OR @CorrelationId IS NULL
 THROW 58403,''AI operator authority required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @rc int;
 EXEC @rc=sys.sp_getapplock @Resource=N''AiCapabilityV1'',@LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=3000;
 IF @rc<0 THROW 58401,''AI lock unavailable.'',1;
 UPDATE dbo.AiProductCapability SET Enabled=@Enabled,Revision=Revision+1,UpdatedAt=SYSUTCDATETIME(),UpdatedByOperator=USER_NAME()
 WHERE Id=1 AND RowVer=@ExpectedRowVer;
 IF @@ROWCOUNT<>1 THROW 58407,''AI concurrency conflict.'',1;
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 VALUES(USER_NAME(),''AI_PRODUCT_CAPABILITY'',1,CASE WHEN @Enabled=1 THEN ''AI_ENABLED'' ELSE ''AI_DISABLED'' END,
 CONCAT(N''{"CORRELATION_ID":"'',CONVERT(nvarchar(36),@CorrelationId),N''"}''));
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 SELECT Enabled,RowVer FROM dbo.AiProductCapability WHERE Id=1;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiGetProductCapabilityV1

AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
SELECT Enabled,RowVer FROM dbo.AiProductCapability WHERE Id=1;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiGetTenantPolicyV1

AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiLockV1 @Exclusive=0; EXEC dbo.AiActorV1 @Admin=1;
 SELECT COALESCE(p.Enabled,CONVERT(bit,0)) Enabled,p.RowVer FROM (SELECT 1 AS OneRow) seed
 LEFT JOIN dbo.TenantAiIntegrationPolicy p ON p.ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId''));
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiInspectKeysV1
@IntegrationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiLockV1 @Exclusive=0; EXEC dbo.AiActorV1 @Admin=1;
 SELECT TOP(100) CredentialId,ExpiresAt,RevokedAt,RowVer FROM dbo.AiIntegrationClientKey
 WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND IntegrationId=@IntegrationId ORDER BY Id DESC;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiSetTenantPolicyV1
@Enabled bit,@ExpectedRowVer varbinary(8), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 IF @Enabled IS NULL THROW 58406,''AI policy required.'',1;
 SELECT @id=Id FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=@t;
 IF @id IS NULL
 BEGIN
  IF @ExpectedRowVer IS NOT NULL THROW 58407,''AI concurrency conflict.'',1;
  INSERT dbo.TenantAiIntegrationPolicy(ShaleClientId,Enabled,UpdatedByUserId) VALUES(@t,@Enabled,@u);
  SET @id=SCOPE_IDENTITY();
 END
 ELSE BEGIN
  UPDATE dbo.TenantAiIntegrationPolicy SET Enabled=@Enabled,Revision=Revision+1,UpdatedAt=@now,UpdatedByUserId=@u
  WHERE Id=@id AND ShaleClientId=@t AND RowVer=@ExpectedRowVer;
  IF @@ROWCOUNT<>1 THROW 58407,''AI concurrency conflict.'',1;
 END;
 IF @Enabled=0 BEGIN
  UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE ShaleClientId=@t AND ConsumedAt IS NULL;
  UPDATE dbo.AiDelegationGrant SET RevokedAt=@now,RevokedByUserId=@u,RevocationReason=''DISABLED'',Revision=Revision+1
  WHERE ShaleClientId=@t AND RevokedAt IS NULL;
  UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=''DISABLED'' WHERE ShaleClientId=@t AND RevokedAt IS NULL;
 END;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''TENANT_AI_POLICY'',@id,''UPDATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"ACTIVE":'',CASE WHEN @Enabled=1 THEN N''true'' ELSE N''false'' END,N''}''));
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 SELECT Enabled,RowVer FROM dbo.TenantAiIntegrationPolicy WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRegisterIntegrationV1
@Name nvarchar(100),@ClientType varchar(16),@Issuer nvarchar(256),@Resource nvarchar(256),
 @ScopeMask tinyint,@Days int,@Redirects nvarchar(max), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=1;
 EXEC dbo.AiEnabledV1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 IF @Name IS NULL OR LEN(@Name)=0 OR @ClientType NOT IN(''PUBLIC'',''CONFIDENTIAL'') OR @ClientType IS NULL
 OR @Days NOT BETWEEN 1 AND 90 OR @Days IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 OR @ScopeMask IS NULL
 OR @Issuer NOT LIKE N''https://%/%'' OR @Resource NOT LIKE N''https://%/%'' OR @Issuer IS NULL OR @Resource IS NULL
 OR ISJSON(@Redirects)<>1 OR LEFT(@Redirects,1)<>N''['' OR DATALENGTH(@Redirects)>22000
 THROW 58406,''Invalid AI registration.'',1;
 IF (SELECT COUNT(*) FROM OPENJSON(@Redirects)) NOT BETWEEN 1 AND 5
 OR EXISTS(SELECT 1 FROM OPENJSON(@Redirects) WHERE type<>1 OR DATALENGTH(value)>4096 OR
 (value NOT LIKE N''https://%/%'' AND NOT(@ClientType=''PUBLIC'' AND (value LIKE N''http://127.0.0.1:%/%'' OR value LIKE N''http://[[]::1]:%/%'')))
 OR CHARINDEX(N''#'',value)>0 OR CHARINDEX(N''*'',value)>0 OR CHARINDEX(N''@'',value)>0)
 OR (SELECT COUNT(DISTINCT value COLLATE Latin1_General_100_BIN2) FROM OPENJSON(@Redirects))<>(SELECT COUNT(*) FROM OPENJSON(@Redirects))
 THROW 58406,''Invalid AI redirects.'',1;
 DECLARE @integration uniqueidentifier=NEWID();
 INSERT dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId,ClientId,Issuer,Resource,Name,ClientType,ScopeMask,CreatedByUserId,UpdatedByUserId,CreatedAt,UpdatedAt,ExpiresAt)
 VALUES(@t,@integration,CONVERT(varchar(36),NEWID()),@Issuer,@Resource,@Name,@ClientType,@ScopeMask,@u,@u,@now,@now,DATEADD(day,@Days,@now));
 SET @id=SCOPE_IDENTITY();
 INSERT dbo.AiIntegrationRedirect(ShaleClientId,IntegrationId,Position,RedirectUri)
 SELECT @t,@integration,CONVERT(int,[key])+1,value FROM OPENJSON(@Redirects);
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_INTEGRATION'',@id,''CREATED'',@CorrelationId,''SHALE_SERVICE'',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 SELECT IntegrationId,ClientId,ScopeMask,Status,ExpiresAt,RowVer FROM dbo.AiIntegrationRegistration WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiChangeIntegrationV1
@IntegrationId uniqueidentifier,@ExpectedRowVer varbinary(8),@ScopeMask tinyint,@Revoke bit, @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 IF @ScopeMask IS NULL OR @ScopeMask>3 OR @Revoke IS NULL THROW 58406,''Invalid AI change.'',1;
 SELECT @id=Id FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND RowVer=@ExpectedRowVer;
 IF @id IS NULL THROW 58407,''AI concurrency conflict.'',1;
 IF EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WHERE Id=@id AND Status=''REVOKED'')
 BEGIN
  IF @Revoke=0 THROW 58408,''AI integration unavailable.'',1;
 END
 ELSE BEGIN
  UPDATE dbo.AiIntegrationRegistration SET ScopeMask=@ScopeMask,Status=CASE WHEN @Revoke=1 THEN ''REVOKED'' ELSE Status END,
  RevokedAt=CASE WHEN @Revoke=1 THEN @now END,RevokedByUserId=CASE WHEN @Revoke=1 THEN @u END,
  RevocationReason=CASE WHEN @Revoke=1 THEN ''ADMIN'' END,Revision=Revision+1,UpdatedAt=@now,UpdatedByUserId=@u
  WHERE Id=@id AND ShaleClientId=@t AND RowVer=@ExpectedRowVer;
  UPDATE dbo.AiDelegationGrant SET RevokedAt=@now,RevokedByUserId=@u,RevocationReason=''ADMIN'',Revision=Revision+1 WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND RevokedAt IS NULL AND ((ScopeMask & @ScopeMask)<>ScopeMask OR @Revoke=1);
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_INTEGRATION'',@id,''UPDATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@ScopeMask,N'',"ACTIVE":'',CASE WHEN @Revoke=1 THEN N''false'' ELSE N''true'' END,N''}''));
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 END;
 SELECT IntegrationId,ScopeMask,Status,RowVer FROM dbo.AiIntegrationRegistration WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRotateClientKeyV1
@IntegrationId uniqueidentifier,@ExpectedIntegrationRowVer varbinary(8),@Modulus varbinary(512),@Days int, @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=1;
 EXEC dbo.AiEnabledV1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 IF @Days IS NULL OR @Days NOT BETWEEN 1 AND 90 OR @Modulus IS NULL OR DATALENGTH(@Modulus)<256
 THROW 58406,''Invalid AI public key.'',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId
 AND ClientType=''CONFIDENTIAL'' AND Status=''ACTIVE'' AND ExpiresAt>@now AND RowVer=@ExpectedIntegrationRowVer)
 THROW 58407,''AI concurrency conflict.'',1;
 IF (SELECT COUNT(*) FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND RevokedAt IS NULL AND ExpiresAt>@now)>=2
 THROW 58409,''Revoke the previous key before another rotation.'',1;
 UPDATE dbo.AiIntegrationClientKey SET ExpiresAt=CASE WHEN ExpiresAt>DATEADD(hour,24,@now) THEN DATEADD(hour,24,@now) ELSE ExpiresAt END
 WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND RevokedAt IS NULL AND ExpiresAt>@now;
 INSERT dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,Modulus,Exponent,CreatedAt,CreatedByUserId,ValidFrom,ExpiresAt)
 VALUES(@t,@IntegrationId,@Modulus,65537,@now,@u,@now,DATEADD(day,@Days,@now));
 SET @id=SCOPE_IDENTITY();
 UPDATE dbo.AiIntegrationRegistration SET Revision=Revision+1,UpdatedAt=@now,UpdatedByUserId=@u WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_CLIENT_KEY'',@id,''CREATED'',@CorrelationId,''SHALE_SERVICE'',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 SELECT CredentialId,Fingerprint,ExpiresAt,RevokedAt,RowVer FROM dbo.AiIntegrationClientKey WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRevokeClientKeyV1
@IntegrationId uniqueidentifier,@CredentialId uniqueidentifier,@ExpectedRowVer varbinary(8), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 SELECT @id=Id FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND CredentialId=@CredentialId AND RowVer=@ExpectedRowVer;
 IF @id IS NULL THROW 58407,''AI concurrency conflict.'',1;
 IF EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WHERE Id=@id AND RevokedAt IS NULL) BEGIN
  UPDATE dbo.AiIntegrationClientKey SET RevokedAt=@now,RevokedByUserId=@u WHERE Id=@id AND ShaleClientId=@t;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_CLIENT_KEY'',@id,''DEACTIVATED'',@CorrelationId,''SHALE_SERVICE'',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 END;
 SELECT RowVer FROM dbo.AiIntegrationClientKey WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiBeginConsentV1
@IntegrationId uniqueidentifier,@ScopeMask tinyint,@Hours int,@IntentHash varbinary(32), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=0;
 EXEC dbo.AiEnabledV1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 IF @IntentHash IS NULL OR DATALENGTH(@IntentHash)<>32 OR @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3
 OR @Hours IS NULL OR @Hours NOT BETWEEN 1 AND 24 THROW 58406,''Invalid AI consent.'',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId
 AND Status=''ACTIVE'' AND ExpiresAt>DATEADD(hour,@Hours,@now) AND (ScopeMask & @ScopeMask)=@ScopeMask)
 THROW 58408,''AI integration unavailable.'',1;
 INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt)
 VALUES(@t,@IntegrationId,@u,@IntentHash,@ScopeMask,@now,DATEADD(minute,5,@now),DATEADD(hour,@Hours,@now));
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiAcceptConsentV1
@IntentHash varbinary(32), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=0;
 EXEC dbo.AiEnabledV1;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 DECLARE @integration uniqueidentifier,@scopes tinyint,@expiry datetime2(7);
 IF DATALENGTH(@IntentHash)<>32 OR @IntentHash IS NULL THROW 58406,''Invalid AI consent.'',1;
 SELECT @id=Id,@integration=IntegrationId,@scopes=ScopeMask,@expiry=GrantExpiresAt FROM dbo.AiConsentIntent
 WHERE ShaleClientId=@t AND UserId=@u AND IntentHash=@IntentHash AND ConsumedAt IS NULL AND ExpiresAt>@now;
 IF @id IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@integration
 AND Status=''ACTIVE'' AND ExpiresAt>=@expiry AND (ScopeMask & @scopes)=@scopes)
 THROW 58408,''AI consent unavailable.'',1;
 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE Id=@id AND ShaleClientId=@t AND ConsumedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58407,''AI concurrency conflict.'',1;
 INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,ScopeMask,ConsentedAt,ExpiresAt)
 VALUES(@t,@integration,@u,@scopes,@now,@expiry);
 SET @id=SCOPE_IDENTITY();
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATION_GRANT'',@id,''CREATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@scopes,N''}''));
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 SELECT GrantId,ScopeMask,ExpiresAt,RowVer FROM dbo.AiDelegationGrant WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRevokeGrantV1
@GrantId uniqueidentifier,@ExpectedRowVer varbinary(8), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=0;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 DECLARE @admin bit=CASE WHEN EXISTS(SELECT 1 FROM dbo.Users WHERE id=@u AND ShaleClientId=@t AND is_admin=1) THEN 1 ELSE 0 END;
 SELECT @id=Id FROM dbo.AiDelegationGrant WHERE ShaleClientId=@t AND GrantId=@GrantId AND (UserId=@u OR @admin=1) AND RowVer=@ExpectedRowVer;
 IF @id IS NULL THROW 58407,''AI concurrency conflict.'',1;
 IF EXISTS(SELECT 1 FROM dbo.AiDelegationGrant WHERE Id=@id AND RevokedAt IS NULL) BEGIN
  UPDATE dbo.AiDelegationGrant SET RevokedAt=@now,RevocationReason=CASE WHEN @admin=1 THEN ''ADMIN'' ELSE ''SELF'' END,RevokedByUserId=@u,Revision=Revision+1 WHERE Id=@id AND ShaleClientId=@t;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATION_GRANT'',@id,''DEACTIVATED'',@CorrelationId,''SHALE_SERVICE'',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 END;
 SELECT RowVer FROM dbo.AiDelegationGrant WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiRevokeSessionV1
@SessionId uniqueidentifier,@ExpectedRowVer varbinary(8), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @CorrelationId IS NULL THROW 58406,''AI correlation required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=0;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 DECLARE @admin bit=CASE WHEN EXISTS(SELECT 1 FROM dbo.Users WHERE id=@u AND ShaleClientId=@t AND is_admin=1) THEN 1 ELSE 0 END;
 SELECT @id=Id FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND SessionId=@SessionId AND (UserId=@u OR @admin=1) AND RowVer=@ExpectedRowVer;
 IF @id IS NULL THROW 58407,''AI concurrency conflict.'',1;
 IF EXISTS(SELECT 1 FROM dbo.AiDelegatedSession WHERE Id=@id AND RevokedAt IS NULL) BEGIN
  UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=CASE WHEN @admin=1 THEN ''ADMIN'' ELSE ''SELF'' END WHERE Id=@id AND ShaleClientId=@t;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,''AI_DELEGATED_SESSION'',@id,''DEACTIVATED'',@CorrelationId,''SHALE_SERVICE'',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,''AI audit unavailable.'',1;
 END;
 SELECT RowVer FROM dbo.AiDelegatedSession WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiInspectRegistrationsV1

AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.AiLockV1 @Exclusive=0; EXEC dbo.AiActorV1 @Admin=1;
 SELECT TOP(100) IntegrationId,ClientId,Name,ClientType,Status,ScopeMask,ExpiresAt,RowVer FROM dbo.AiIntegrationRegistration
 WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) ORDER BY Id DESC;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiVerifySessionV1
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
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND i.Status=''ACTIVE'' AND i.ExpiresAt>@now
 AND ((i.ClientType=''PUBLIC'' AND s.CredentialId IS NULL) OR (i.ClientType=''CONFIDENTIAL'' AND k.RevokedAt IS NULL AND k.CredentialId IS NOT NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @mask IS NULL OR @RequiredScope IS NULL OR @RequiredScope NOT IN(1,2) OR (@mask & @RequiredScope)<>@RequiredScope
 THROW 58408,''AI delegation unavailable.'',1;

 SELECT @t ShaleClientId,@u UserId,@integration IntegrationId,@grant GrantId,@SessionId SessionId,@mask ScopeMask;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.AiReserveQuotaV1
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
EXEC(N'CREATE PROCEDURE dbo.AiReleaseQuotaV1
@InvocationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1; EXEC dbo.AiActorV1;
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME()
 WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')) AND UserId=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId''))
 AND InvocationId=@InvocationId AND ReleasedAt IS NULL;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');
EXEC(N'CREATE PROCEDURE dbo.ResolveAiClientV1
@ClientId varchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF USER_NAME()<>N''dbo'' THROW 58403,''AI resolver owner drift.'',1;
 IF DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 RETURN;
 SELECT i.ShaleClientId,i.IntegrationId,i.ClientId,i.ClientType,i.ScopeMask,i.ExpiresAt
 FROM dbo.AiIntegrationRegistration i WHERE i.ClientId=@ClientId COLLATE Latin1_General_100_BIN2
 AND i.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND i.Resource=@Resource COLLATE Latin1_General_100_BIN2
 AND i.Status=''ACTIVE'' AND i.ExpiresAt>SYSUTCDATETIME()
 AND EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=i.ShaleClientId AND Enabled=1);
 -- Identity is provisional; callers must use a fresh initialized runtime lease and live gate.

END;');
EXEC(N'CREATE PROCEDURE dbo.ResolveAiCredentialV1
@Digest varbinary(max),@Issuer nvarchar(max),@Resource nvarchar(max)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF USER_NAME()<>N''dbo'' THROW 58403,''AI resolver owner drift.'',1;
 IF DATALENGTH(@Digest)<>32 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 RETURN;
 SELECT c.ShaleClientId,c.UserId,c.IntegrationId,c.GrantId,c.SessionId,c.ScopeMask
 FROM dbo.AiOAuthCredential c JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=c.ShaleClientId AND i.IntegrationId=c.IntegrationId
 WHERE c.CredentialHash=CONVERT(binary(32),@Digest) AND c.Kind=''ACCESS'' AND c.ConsumedAt IS NULL AND c.RevokedAt IS NULL AND c.ExpiresAt>SYSUTCDATETIME()
 AND i.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND i.Resource=@Resource COLLATE Latin1_General_100_BIN2
 AND i.Status=''ACTIVE'' AND i.ExpiresAt>SYSUTCDATETIME()
 AND EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=i.ShaleClientId AND Enabled=1);
 -- Provisional identity only, never a verified execution context or Case data.

END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_TenantAiIntegrationPolicy ON dbo.TenantAiIntegrationPolicy AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) THROW 58410,''AI ownership is immutable.'',1;  END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiIntegrationRegistration ON dbo.AiIntegrationRegistration AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(ClientId) OR UPDATE(Issuer) OR UPDATE(Resource) OR UPDATE(CreatedAt) OR UPDATE(CreatedByUserId) OR UPDATE(ClientType) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.RevokedAt IS NOT NULL AND (i.RevokedAt IS NULL OR i.RevokedAt<>d.RevokedAt)) THROW 58411,''AI revocation is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiIntegrationRedirect ON dbo.AiIntegrationRedirect AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(Position) OR UPDATE(RedirectUri) THROW 58410,''AI ownership is immutable.'',1;  END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiIntegrationClientKey ON dbo.AiIntegrationClientKey AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(CredentialId) OR UPDATE(Modulus) OR UPDATE(Exponent) OR UPDATE(CreatedAt) OR UPDATE(CreatedByUserId) OR UPDATE(ValidFrom) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.RevokedAt IS NOT NULL AND (i.RevokedAt IS NULL OR i.RevokedAt<>d.RevokedAt)) THROW 58411,''AI revocation is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiDelegationGrant ON dbo.AiDelegationGrant AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(GrantId) OR UPDATE(ScopeMask) OR UPDATE(ConsentedAt) OR UPDATE(ExpiresAt) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.RevokedAt IS NOT NULL AND (i.RevokedAt IS NULL OR i.RevokedAt<>d.RevokedAt)) THROW 58411,''AI revocation is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiConsentIntent ON dbo.AiConsentIntent AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(IntentHash) OR UPDATE(ScopeMask) OR UPDATE(ExpiresAt) OR UPDATE(GrantExpiresAt) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.ConsumedAt IS NOT NULL AND (i.ConsumedAt IS NULL OR i.ConsumedAt<>d.ConsumedAt)) THROW 58411,''AI consumption is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiDelegatedSession ON dbo.AiDelegatedSession AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(GrantId) OR UPDATE(SessionId) OR UPDATE(CredentialId) OR UPDATE(ScopeMask) OR UPDATE(IssuedAt) OR UPDATE(ExpiresAt) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.RevokedAt IS NOT NULL AND (i.RevokedAt IS NULL OR i.RevokedAt<>d.RevokedAt)) THROW 58411,''AI revocation is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiOAuthCredential ON dbo.AiOAuthCredential AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(GrantId) OR UPDATE(SessionId) OR UPDATE(CredentialHash) OR UPDATE(Kind) OR UPDATE(ScopeMask) OR UPDATE(IssuedAt) OR UPDATE(ExpiresAt) OR UPDATE(Generation) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.RevokedAt IS NOT NULL AND (i.RevokedAt IS NULL OR i.RevokedAt<>d.RevokedAt)) THROW 58411,''AI revocation is permanent.'',1;IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.ConsumedAt IS NOT NULL AND (i.ConsumedAt IS NULL OR i.ConsumedAt<>d.ConsumedAt)) THROW 58411,''AI consumption is permanent.'',1; END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_AiQuotaReservation ON dbo.AiQuotaReservation AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; IF NOT EXISTS(SELECT 1 FROM deleted) RETURN; IF NOT EXISTS(SELECT 1 FROM inserted) THROW 58410,''AI history cannot be deleted.'',1; IF UPDATE(ShaleClientId) OR UPDATE(IntegrationId) OR UPDATE(UserId) OR UPDATE(GrantId) OR UPDATE(SessionId) OR UPDATE(InvocationId) OR UPDATE(Operation) OR UPDATE(RowCost) OR UPDATE(Admitted) OR UPDATE(AdmittedAt) OR UPDATE(LeaseExpiresAt) THROW 58410,''AI ownership is immutable.'',1; IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.ReleasedAt IS NOT NULL AND (i.ReleasedAt IS NULL OR i.ReleasedAt<>d.ReleasedAt)) THROW 58411,''AI release is permanent.'',1; END;');
IF DATABASE_PRINCIPAL_ID(N'AiIntegrationRuntime') IS NOT NULL THROW 58422,'Preexisting AI role; review authority.',1; CREATE ROLE AiIntegrationRuntime AUTHORIZATION dbo;
IF DATABASE_PRINCIPAL_ID(N'AiIntegrationBootstrap') IS NOT NULL THROW 58422,'Preexisting AI role; review authority.',1; CREATE ROLE AiIntegrationBootstrap AUTHORIZATION dbo;
IF DATABASE_PRINCIPAL_ID(N'AiCapabilityOperator') IS NOT NULL THROW 58422,'Preexisting AI role; review authority.',1; CREATE ROLE AiCapabilityOperator AUTHORIZATION dbo;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiProductCapability TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiProductCapability TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiProductCapability TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.TenantAiIntegrationPolicy TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.TenantAiIntegrationPolicy TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.TenantAiIntegrationPolicy TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRegistration TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRegistration TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRegistration TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRedirect TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRedirect TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationRedirect TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationClientKey TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationClientKey TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiIntegrationClientKey TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegationGrant TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegationGrant TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegationGrant TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiConsentIntent TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiConsentIntent TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiConsentIntent TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegatedSession TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegatedSession TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiDelegatedSession TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiOAuthCredential TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiOAuthCredential TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiOAuthCredential TO AiCapabilityOperator;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiQuotaReservation TO AiIntegrationRuntime;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiQuotaReservation TO AiIntegrationBootstrap;
DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.AiQuotaReservation TO AiCapabilityOperator;
GRANT EXECUTE ON OBJECT::dbo.AiSetProductCapabilityV1 TO AiCapabilityOperator;
GRANT EXECUTE ON OBJECT::dbo.AiGetProductCapabilityV1 TO AiCapabilityOperator;
GRANT EXECUTE ON OBJECT::dbo.AiGetTenantPolicyV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiInspectKeysV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiSetTenantPolicyV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRegisterIntegrationV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiChangeIntegrationV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRotateClientKeyV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRevokeClientKeyV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiBeginConsentV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiAcceptConsentV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRevokeGrantV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiRevokeSessionV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiInspectRegistrationsV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiVerifySessionV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiReserveQuotaV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.AiReleaseQuotaV1 TO AiIntegrationRuntime;
GRANT EXECUTE ON OBJECT::dbo.ResolveAiClientV1 TO AiIntegrationBootstrap;
GRANT EXECUTE ON OBJECT::dbo.ResolveAiCredentialV1 TO AiIntegrationBootstrap;

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
EXEC sys.sp_addextendedproperty @name=N'Shale.AiFoundationV1',@value=@fingerprint,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiProductCapability';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiFoundationV1.Source',@value=0x04080764f9057fbe992e44929f1199f0425964961ef42e6970d35c71ebc22b83,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiProductCapability';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

/* PR 4A immutable exchange receipt. No issuer/insert EXECUTE is granted in this increment. */
CREATE TABLE dbo.AiCimdFamilyV1(
 ShaleClientId int NOT NULL, SessionId uniqueidentifier NOT NULL, IntegrationId uniqueidentifier NOT NULL,
 GrantId uniqueidentifier NOT NULL, UserId int NOT NULL,
 TransactionId uniqueidentifier NOT NULL UNIQUE, WebSessionId uniqueidentifier NOT NULL,
 ApplicationId uniqueidentifier NOT NULL, ApplicationRevision bigint NOT NULL,
 ApprovalId uniqueidentifier NOT NULL, ApprovalVersion binary(8) NOT NULL,
 MetadataDigest binary(32) NOT NULL,
 ExternalClientId nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CredentialId uniqueidentifier NULL, AuthMethod varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
 RedirectUri nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Challenge varchar(43) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CodeHash binary(32) NOT NULL UNIQUE, CodeIssuedAt datetime2(7) NOT NULL, CodeExpiresAt datetime2(7) NOT NULL,
 ConsumedAt datetime2(7) NOT NULL, DeniedAt datetime2(7) NULL,
 CONSTRAINT PK_AiCimdFamilyV1 PRIMARY KEY(ShaleClientId,SessionId),
 CONSTRAINT UQ_AiCimdFamilyV1_Owner UNIQUE(ShaleClientId,SessionId,GrantId,IntegrationId,UserId),
 CONSTRAINT FK_AiCimdFamilyV1_Session FOREIGN KEY(ShaleClientId,SessionId,GrantId,IntegrationId,UserId)
 REFERENCES dbo.AiDelegatedSession(ShaleClientId,SessionId,GrantId,IntegrationId,UserId),
 CONSTRAINT FK_AiCimdFamilyV1_Approval FOREIGN KEY(ShaleClientId,ApprovalId) REFERENCES dbo.AiTenantApplicationApproval(ShaleClientId,ApprovalId),
 CONSTRAINT FK_AiCimdFamilyV1_Revision FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiExternalApplicationRevision(ApplicationId,Revision),
 CONSTRAINT FK_AiCimdFamilyV1_Web FOREIGN KEY(WebSessionId) REFERENCES dbo.UserSessions(SessionId),
 CONSTRAINT CK_AiCimdFamilyV1_Method CHECK((AuthMethod='none' AND DATALENGTH(AuthMethod)=4 AND CredentialId IS NULL) OR (AuthMethod='private_key_jwt' AND DATALENGTH(AuthMethod)=15 AND CredentialId IS NOT NULL)),
 CONSTRAINT CK_AiCimdFamilyV1_Code CHECK(DeniedAt IS NULL AND CodeExpiresAt>CodeIssuedAt AND CodeExpiresAt<=DATEADD(second,60,CodeIssuedAt) AND ConsumedAt>=CodeIssuedAt AND ConsumedAt<CodeExpiresAt),
 CONSTRAINT CK_AiCimdFamilyV1_Pkce CHECK(DATALENGTH(Challenge)=43 AND Challenge NOT LIKE '%[^A-Za-z0-9_-]%' COLLATE Latin1_General_100_BIN2),
 CONSTRAINT CK_AiCimdFamilyV1_Identity CHECK(DATALENGTH(ExternalClientId) BETWEEN 4 AND 4096 AND LEN(ClientId)>0 AND LEN(RedirectUri)>0)
);
CREATE TABLE dbo.AiCimdProofControlV1(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1), Enabled bit NOT NULL DEFAULT 0);
INSERT dbo.AiCimdProofControlV1(Id,Enabled) VALUES(1,0);

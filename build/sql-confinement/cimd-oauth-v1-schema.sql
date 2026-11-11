/* New public validated-snapshot cache. Publisher authority is separate from OAuth issuer authority. */
CREATE TABLE dbo.AiCimdPublicationV1(
 ApplicationId uniqueidentifier NOT NULL, ApplicationRevision bigint NOT NULL,
 MetadataDigest binary(32) NOT NULL, Redirects nvarchar(max) NOT NULL, SigningKeys nvarchar(max) NOT NULL,
 FreshUntil datetime2(7) NOT NULL,
 CONSTRAINT PK_AiCimdPublicationV1 PRIMARY KEY(ApplicationId,ApplicationRevision),
 CONSTRAINT FK_AiCimdPublicationV1_Revision FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiExternalApplicationRevision(ApplicationId,Revision),
 CONSTRAINT CK_AiCimdPublicationV1_Json CHECK(ISJSON(Redirects)=1 AND ISJSON(SigningKeys)=1 AND DATALENGTH(Redirects)<=65536 AND DATALENGTH(SigningKeys)<=16384)
);
CREATE TABLE dbo.AiCimdKeyBindingV1(
 ShaleClientId int NOT NULL, IntegrationId uniqueidentifier NOT NULL, ApplicationId uniqueidentifier NOT NULL,
 ApplicationRevision bigint NOT NULL, Kid nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL, CredentialId uniqueidentifier NOT NULL,
 CONSTRAINT PK_AiCimdKeyBindingV1 PRIMARY KEY(ShaleClientId,IntegrationId,ApplicationRevision,Kid),
 CONSTRAINT FK_AiCimdKeyBindingV1_Publication FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiCimdPublicationV1(ApplicationId,ApplicationRevision),
 CONSTRAINT FK_AiCimdKeyBindingV1_Key FOREIGN KEY(ShaleClientId,IntegrationId,CredentialId) REFERENCES dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId)
);
CREATE TABLE dbo.AiCimdOAuthControlV1(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),Enabled bit NOT NULL DEFAULT 0);
INSERT dbo.AiCimdOAuthControlV1 VALUES(1,0);
CREATE TABLE dbo.AiCimdOAuthReceiptV1(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),SourceHash binary(32) NOT NULL,BeforeHash binary(32) NOT NULL,CatalogHash binary(32) NOT NULL);
CREATE TABLE dbo.AiCimdNeutralReplayV1(
 ApplicationId uniqueidentifier NOT NULL,ApplicationRevision bigint NOT NULL,
 Kid nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,JtiHash binary(32) NOT NULL,
 AcceptedAt datetime2(7) NOT NULL,ExpiresAt datetime2(7) NOT NULL,
 CONSTRAINT PK_AiCimdNeutralReplayV1 PRIMARY KEY(ApplicationId,Kid,JtiHash),
 CONSTRAINT FK_AiCimdNeutralReplayV1_Publication FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiCimdPublicationV1(ApplicationId,ApplicationRevision),
 CONSTRAINT CK_AiCimdNeutralReplayV1_Expiry CHECK(ExpiresAt>AcceptedAt AND ExpiresAt<=DATEADD(second,60,AcceptedAt))
);

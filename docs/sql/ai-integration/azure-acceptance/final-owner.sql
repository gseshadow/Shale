:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
:r docs/sql/ai-integration/azure-acceptance/owner.sql
GO
/* Installed-state assertions only; not live restricted-provider or enabled-service acceptance. */
IF (SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN
 (N'AiProductCapability',N'AiCimdProofControlV1',N'AiCimdOAuthControlV1',N'AiExternalControlV2',N'AiDcrControlV3'))<>5
 THROW 59804,'STOP: missing installed control table.',1;
IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment)<>1
 OR EXISTS(SELECT 1 FROM dbo.AiCaseDataDeployment WHERE Id<>1 OR Contract<>'case-read-v1')
 THROW 59804,'STOP: expected one immutable binding.',1;
IF EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy)
 OR EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration)
 OR EXISTS(SELECT 1 FROM dbo.AiDelegationGrant)
 OR EXISTS(SELECT 1 FROM dbo.AiOAuthCredential)
 OR EXISTS(SELECT 1 FROM dbo.AiExternalApplication)
 THROW 59804,'STOP: installation must not seed application, tenant or credential authority.',1;

IF (SELECT COUNT(*) FROM sys.database_principals WHERE name LIKE N'mcpacc[_]%' AND type='S' AND authentication_type=2)<>17
 OR (SELECT COUNT(DISTINCT sid) FROM sys.database_principals WHERE name LIKE N'mcpacc[_]%')<>17
 THROW 59804,'STOP: expected all 17 distinct contained identities.',1;
IF (SELECT COUNT(*) FROM dbo.ShaleClients)<>2 OR (SELECT COUNT(*) FROM dbo.Users)<>4
 OR (SELECT COUNT(*) FROM dbo.Cases)<>3
 OR NOT EXISTS(SELECT 1 FROM dbo.Cases WHERE Id=100 AND ShaleClientId=7 AND IsDeleted=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.Cases WHERE Id=200 AND ShaleClientId=8 AND IsDeleted=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.Cases WHERE Id=300 AND ShaleClientId=7 AND IsDeleted=1)
 THROW 59804,'STOP: synthetic tenant/Case state mismatch.',1;
SELECT 'INSTALLED_OFF_PENDING_RESTRICTED_NATIVE_AND_APP_ACCEPTANCE' AS PreparationStatus;

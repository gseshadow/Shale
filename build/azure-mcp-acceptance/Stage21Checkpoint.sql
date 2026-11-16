/* Read-only checkpoint24 discriminator. Requires target/owner fences and full
   PR3 source/receipt/confinement checks before this file. No repair or DDL. */
IF USER_NAME()<>N'dbo' OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59812,'Full owner metadata visibility required.',1;
IF (SELECT COUNT_BIG(*) FROM dbo.AiCaseDataDeployment)<>1
 THROW 59812,'Exactly one committed deployment binding required.',1;
IF EXISTS(SELECT 1 FROM sys.objects WHERE name LIKE N'AiCimd%' OR name LIKE N'ResolveAiCimd%'
 OR name LIKE N'AiDcr%' OR name LIKE N'AiExternal%V2')
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name LIKE N'AiCimd%' OR name LIKE N'AiDcr%' OR name LIKE N'AiExternal%V2'
 OR name IN(N'mcpacc_case',N'mcpacc_reviewer',N'mcpacc_publisher',N'mcpacc_approval',N'mcpacc_onboarding',N'mcpacc_browser',N'mcpacc_issuer',N'mcpacc_metadata',N'mcpacc_resolver',N'mcpacc_neutral',N'mcpacc_dcr_endpoint',N'mcpacc_dcr_metadata'))
 THROW 59812,'Later-stage artifacts present or partial; checkpoint24 recovery refused.',1;

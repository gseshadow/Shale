:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/preflight.sql
IF N'$(NewModuleConsumersDrained)'<>N'CONFIRMED' OR N'$(RollbackCompatibilityReviewed)'<>N'CONFIRMED'
 OR DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1') IS NOT NULL
 OR DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1') IS NOT NULL
 THROW 58322,N'Drain every new caller and roll back read fences before removing modules.',1;
IF COALESCE((SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1')),N'')<>N'$(LookupDefinitionHash)'
 OR COALESCE((SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')),N'')<>N'$(RememberDefinitionHash)'
 THROW 58323,N'Original separately captured module hashes required; never adopt current hashes.',1;
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies WHERE referenced_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')))
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')))
 THROW 58324,N'Module dependencies/signatures changed; stop.',1;
GO
:r docs/sql/auth/permission-cutover/module-postcheck.sql
BEGIN TRANSACTION;
DROP PROCEDURE dbo.ResolveDesktopRememberCredentialV2;
DROP PROCEDURE dbo.LookupAuthenticationCredentialV1;
COMMIT TRANSACTION;
SELECT N'AUTH_CUTOVER_INSTALL_ROLLED_BACK' AS CheckName;
GO

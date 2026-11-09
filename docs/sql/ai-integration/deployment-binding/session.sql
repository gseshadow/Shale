/* Include only. No durable writes. Fresh authorized dbo connection; no identity provisioning. */
SET NOCOUNT ON;
IF @@TRANCOUNT<>0 OR (2 & @@OPTIONS)=2
 THROW 59300,'Fresh autocommit session required.',1;
IF USER_ID()<>DATABASE_PRINCIPAL_ID(N'dbo')
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 THROW 59300,'Existing authorized dbo operator visibility required; do not grant runtime authority.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalEmail') IS NOT NULL
 THROW 59300,'Application context must be NULL; reconnect, do not clear a tenant lease.',1;
IF ISNULL(CONVERT(int,SERVERPROPERTY('EngineEdition')),0)<>5
 THROW 59300,'This packet requires Azure SQL Database.',1;

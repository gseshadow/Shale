:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
:r docs/sql/ai-integration/azure-acceptance/owner.sql
GO
/* PRIVATE OPERATOR COPY ONLY. Fill NULL secrets only after separate approval.
   SQLCMD -x is mandatory; escape apostrophes by doubling. Never -v, -e or transcript.
   These are 17 separate contained SQL users; the owner is never app-consumed. */
DECLARE @ProvisionApproved bit=0;
DECLARE @NewUsers table(UserName sysname PRIMARY KEY, RoleName sysname NULL, Secret nvarchar(128) NULL);
INSERT @NewUsers VALUES
(N'mcpacc_auth',NULL,NULL),
(N'mcpacc_runtime',N'AiIntegrationRuntime',NULL),
(N'mcpacc_predecessor',N'AiCaseDataRuntime',NULL),
(N'mcpacc_verifier',N'AiCaseDataCatalogVerifier',NULL),
(N'mcpacc_bootstrap',N'AiIntegrationBootstrap',NULL);
IF @ProvisionApproved<>1 OR (SELECT COUNT(*) FROM @NewUsers)<>5
 OR EXISTS(SELECT 1 FROM @NewUsers WHERE Secret IS NULL OR LEN(Secret)<20)
 OR (SELECT COUNT(DISTINCT CONVERT(varbinary(256),Secret)) FROM @NewUsers)<>5
 THROW 59805,'STOP: explicit approval and 5 unique independent secrets required.',1;
IF EXISTS(SELECT 1 FROM @NewUsers n JOIN sys.database_principals p ON p.name=n.UserName)
 OR EXISTS(SELECT 1 FROM @NewUsers WHERE RoleName IS NOT NULL AND DATABASE_PRINCIPAL_ID(RoleName) IS NULL)
 THROW 59805,'STOP: existing user or missing role; no adoption/regrant.',1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @UserName sysname,@RoleName sysname,@Secret nvarchar(128),@UserSql nvarchar(max);
 DECLARE NewUsers CURSOR LOCAL FAST_FORWARD FOR SELECT UserName,RoleName,Secret FROM @NewUsers;
 OPEN NewUsers; FETCH NEXT FROM NewUsers INTO @UserName,@RoleName,@Secret;
 WHILE @@FETCH_STATUS=0
 BEGIN
  SET @UserSql=N'CREATE USER '+QUOTENAME(@UserName)+N' WITH PASSWORD=N'''+REPLACE(@Secret,N'''',N'''''')+N''';';
  EXEC sys.sp_executesql @UserSql;
  IF @RoleName IS NOT NULL
  BEGIN
   SET @UserSql=N'ALTER ROLE '+QUOTENAME(@RoleName)+N' ADD MEMBER '+QUOTENAME(@UserName)+N';';
   EXEC sys.sp_executesql @UserSql;
  END;
  FETCH NEXT FROM NewUsers INTO @UserName,@RoleName,@Secret;
 END;
 CLOSE NewUsers; DEALLOCATE NewUsers;
 /* Ordinary browser fixture ACL: existing base fixture + C2 browser additions.
    Only ordinary identities receive these direct object grants. */
 GRANT EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO mcpacc_auth;
 GRANT SELECT ON OBJECT::dbo.Cases TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.Users TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.CaseUsers TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.CaseStatuses TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.Statuses TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.PracticeAreas TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.UserSessions TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.ShaleClients TO mcpacc_runtime;
 GRANT SELECT ON OBJECT::dbo.ApplicationInstances TO mcpacc_runtime;
 GRANT INSERT ON OBJECT::dbo.auditLog TO mcpacc_runtime;
 GRANT INSERT,UPDATE ON OBJECT::dbo.UserSessions TO mcpacc_runtime;
 IF (SELECT COUNT(*) FROM sys.database_principals p JOIN @NewUsers n ON p.name=n.UserName
 WHERE p.type='S' AND p.authentication_type=2 AND p.owning_principal_id IS NULL)<>5
 OR (SELECT COUNT(DISTINCT p.sid) FROM sys.database_principals p JOIN @NewUsers n ON p.name=n.UserName)<>5
 THROW 59805,'STOP: expected 5 distinct contained SQL principals.',1;
 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;

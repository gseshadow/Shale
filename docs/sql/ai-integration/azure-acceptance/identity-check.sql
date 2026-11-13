/* One actual contained-user connection, not EXECUTE AS. Rendered literals come from stages.json. */
IF CONVERT(varbinary(max),USER_NAME())<>CONVERT(varbinary(max),N'__USER__')
 OR USER_ID()<=4 OR ORIGINAL_LOGIN()<>USER_NAME()
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id=USER_ID() AND type='S' AND authentication_type=2)
 OR (SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id=@@SPID AND authenticating_database_id=DB_ID()
 AND original_security_id=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID()))<>1
 THROW 59808,'STOP: actual contained authenticating principal required.',1;
DECLARE @ExpectedTokens table(Name sysname PRIMARY KEY);
INSERT @ExpectedTokens VALUES(USER_NAME()),(N'public');
IF N'__ROLE__'<>N'' INSERT @ExpectedTokens VALUES(N'__ROLE__');
IF EXISTS(SELECT name FROM sys.user_token EXCEPT SELECT Name FROM @ExpectedTokens)
 OR EXISTS(SELECT Name FROM @ExpectedTokens EXCEPT SELECT name FROM sys.user_token)
 THROW 59808,'STOP: exact own-user/public/capability-role token set required.',1;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>0
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER'),0)<>0
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE'),0)<>0
 THROW 59808,'STOP: unexpected administrative authority.',1;
/* Dedicated capability users must be denied direct protected reads. Ordinary runtime is distinct. */
IF N'__USER__' NOT IN(N'mcpacc_auth',N'mcpacc_runtime')
BEGIN
 DECLARE @Denied bit=0;
 BEGIN TRY
  EXEC sys.sp_executesql N'SELECT TOP (0) Id FROM dbo.Cases;';
 END TRY
 BEGIN CATCH
  IF ERROR_NUMBER()<>229 THROW;
  SET @Denied=1;
 END CATCH;
 IF @Denied<>1 THROW 59808,'STOP: protected-table permission denial required.',1;
END;
SELECT 'CONTAINED_PRINCIPAL_AND_TOKEN_CHECK_ONLY' AS IdentityStatus;

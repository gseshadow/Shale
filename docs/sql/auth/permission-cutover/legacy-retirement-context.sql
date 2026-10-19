/* Standalone pre-Stage-A candidate only. SQLCMD inputs are trusted operator literals.
   No application rows, module invocation, Azure connection or installation. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF @@TRANCOUNT<>0 OR USER_NAME()<>N'dbo' OR DB_NAME()<>N'$(ExpectedDatabase)'
 THROW 58340,N'Expected database, dbo and no ambient transaction required.',1;
IF CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation'))<>N'SQL_Latin1_General_CP1_CI_AS'
 OR USER_NAME((SELECT principal_id FROM sys.schemas WHERE name=N'dbo'))<>N'dbo'
 THROW 58340,N'Reviewed collation and dbo authority required.',1;
IF OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1') IS NOT NULL
 OR OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2') IS NOT NULL
 OR EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN
 (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')))
 THROW 58340,N'Pre-Stage-A state and unchanged RLS prerequisites required.',1;
CREATE TABLE #LegacyTargets(ObjectId int NOT NULL PRIMARY KEY, DefinitionHash varbinary(32) NOT NULL);
INSERT #LegacyTargets VALUES
 (COALESCE(OBJECT_ID(N'dbo.sp_login_get_hash',N'P'),-1),0x1B0D6F1117829EA94326E04EF955243EC7BF8110692C550876F1ADBA1C9D7453),
 (COALESCE(OBJECT_ID(N'dbo.sp_login_mark_failure',N'P'),-2),0x08AF95D876F0DBA465DE97AB5A7371544604B64B916B464722DA5D040F374112),
 (COALESCE(OBJECT_ID(N'dbo.sp_login_mark_success',N'P'),-3),0xA76F60DAEA06E046D46A7CAB99770C819449474AA8A214DD27C13F5EB24C0C6E);
IF (SELECT COUNT(*) FROM #LegacyTargets t JOIN sys.sql_modules m ON m.object_id=t.ObjectId
 JOIN sys.objects o ON o.object_id=t.ObjectId JOIN sys.schemas s ON s.schema_id=o.schema_id
 WHERE HASHBYTES('SHA2_256',m.definition)=t.DefinitionHash AND m.execute_as_principal_id IS NULL
 AND o.principal_id IS NULL AND s.name=N'dbo' AND s.principal_id=DATABASE_PRINCIPAL_ID(N'dbo')
 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND m.is_schema_bound=0
 AND m.is_recompiled=0 AND m.null_on_null_input=0 AND m.uses_database_collation=0)<>3
 OR (SELECT COUNT(*) FROM sys.parameters WHERE object_id IN(SELECT ObjectId FROM #LegacyTargets))<>3
 OR (SELECT COUNT(*) FROM sys.parameters WHERE object_id IN(SELECT ObjectId FROM #LegacyTargets)
 AND parameter_id=1 AND name=N'@email' AND user_type_id=231 AND system_type_id=231
 AND max_length=640 AND precision=0 AND scale=0 AND is_output=0 AND is_readonly=0)<>3
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE class=1 AND major_id IN(SELECT ObjectId FROM #LegacyTargets))
 THROW 58341,N'Captured module definition, SET, ownership, signature or parameter baseline changed.',1;
-- No static inbound caller was captured. Dynamic/external callers require separate operator evidence.
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies WHERE referenced_id IN(SELECT ObjectId FROM #LegacyTargets))
 OR EXISTS(SELECT 1 FROM sys.synonyms WHERE base_object_name LIKE N'%sp[_]login[_]%')
 OR EXISTS(SELECT 1 FROM sys.triggers WHERE parent_id IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.AuthConfig')))
 OR (SELECT COUNT(*) FROM sys.sql_expression_dependencies WHERE referencing_id IN(SELECT ObjectId FROM #LegacyTargets))<>4
 OR EXISTS(SELECT 1 FROM sys.sql_expression_dependencies WHERE referencing_id IN(SELECT ObjectId FROM #LegacyTargets)
 AND (referenced_id IS NULL OR referenced_id NOT IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.AuthConfig'))
 OR referenced_server_name IS NOT NULL OR referenced_database_name IS NOT NULL OR is_caller_dependent=1))
 THROW 58342,N'Dependency, synonym or trigger evidence requires separate review; no chaining bypass accepted.',1;
IF NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_app' AND type IN('S','E','X'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_runtime' AND type IN('S','E','X'))
 OR NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_runtime_role' AND type='R' AND is_fixed_role=0)
 THROW 58340,N'Reviewed auth/runtime principals required.',1;
CREATE TABLE #LegacyFingerprint(StateHash varbinary(32), OtherHash varbinary(32));
GO

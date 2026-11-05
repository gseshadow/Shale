-- PREPARED ONLY. Catalog/permission reads; no application rows or application RPCs.
-- Adapted from the reviewed Stage A and Phase 7A/8A/remembered verification inventories.
-- No temp tables, table-variable DML, includes, dynamic SQL or transaction changes.
SET NOCOUNT ON;
SET LOCK_TIMEOUT 5000;
IF DB_NAME()<>N'Shale' OR ORIGINAL_LOGIN()<>N'brian'
 OR COALESCE(CONVERT(int,SERVERPROPERTY('EngineEdition')),0)<>5
 OR COALESCE(CONVERT(nvarchar(32),CONNECTIONPROPERTY('auth_scheme')),N'')<>N'SQL'
BEGIN SELECT 'FACT|SQL_TARGET|UNKNOWN|1|-' ; RETURN; END;
SELECT 'FACT|SQL_TARGET|PASS|0|-';
IF USER_NAME()<>N'dbo' OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
BEGIN SELECT 'FACT|SQL_VISIBILITY|UNKNOWN|1|-'; RETURN; END;
SELECT 'FACT|SQL_VISIBILITY|PASS|0|-';
DECLARE @Findings bigint,@Unknown bigint,@Hash varchar(64),@Json nvarchar(max);
SELECT 'FACT|STAGE_A_PRINCIPALS|'+CASE WHEN EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_runtime_role' AND type='R' AND is_fixed_role=0) AND(SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'shale_app',N'shale_runtime') AND type IN('S','E','X'))=2 THEN 'PASS|0|-' ELSE 'FAIL|1|-' END;

SELECT @Findings=CASE WHEN EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id=OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1',N'P') AND COALESCE(o.principal_id,s.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo')
 AND s.principal_id=DATABASE_PRINCIPAL_ID(N'dbo') AND m.execute_as_principal_id=-2
 AND CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) IN('4DA91EB0B3AB425C60CC96274F7A0EB96D06A1D1ECA70DEB70C477618C5C508B','9A29346F7C7022C4858D34F1ADEC5DCF28FBB888B7750F71A396F7887CC2649F')) THEN 0 ELSE 1 END;
SET @Hash=NULL;
SELECT @Hash=CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1');
SELECT CONVERT(varchar(300),CONCAT('FACT|STAGE_A_MODULE_1|',CASE WHEN OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1') IS NOT NULL AND @Hash IS NULL THEN 'UNKNOWN' WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|',COALESCE(@Hash,'-')));

SELECT @Findings=CASE WHEN EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2',N'P') AND COALESCE(o.principal_id,s.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo')
 AND s.principal_id=DATABASE_PRINCIPAL_ID(N'dbo') AND m.execute_as_principal_id=-2
 AND CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) IN('2441E8C3143819C4844953CE743C61737A97EFFABF1913DBA5C89CC7D733464C','6C5315393FE02C78AC2BD82E28171CC486172182223BB5E9D6617A12FBB31388')) THEN 0 ELSE 1 END;
SET @Hash=NULL;
SELECT @Hash=CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2');
SELECT CONVERT(varchar(300),CONCAT('FACT|STAGE_A_MODULE_2|',CASE WHEN OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2') IS NOT NULL AND @Hash IS NULL THEN 'UNKNOWN' WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|',COALESCE(@Hash,'-')));

;WITH Expected(ModuleName,ParameterName,Ordinal,TypeId,MaxLength,Scale) AS (
 SELECT * FROM (VALUES(N'LookupAuthenticationCredentialV1',N'@Email',1,231,-1,0),
 (N'ResolveDesktopRememberCredentialV2',N'@CredentialHash',1,165,-1,0),
 (N'ResolveDesktopRememberCredentialV2',N'@InstallationId',2,36,16,0),
 (N'ResolveDesktopRememberCredentialV2',N'@Now',3,42,8,7))v(a,b,c,d,e,f))
SELECT @Findings=(SELECT COUNT_BIG(*) FROM Expected e LEFT JOIN sys.parameters p ON p.object_id=OBJECT_ID(N'dbo.'+e.ModuleName) AND p.parameter_id=e.Ordinal
 WHERE p.name IS NULL OR p.name<>e.ParameterName OR p.system_type_id<>e.TypeId OR p.max_length<>e.MaxLength OR p.scale<>e.Scale OR p.is_output<>0)
 +ABS(4-(SELECT COUNT_BIG(*) FROM sys.parameters WHERE object_id IN(OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'))));
SELECT CONVERT(varchar(300),CONCAT('FACT|STAGE_A_PARAMETERS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Expected(ModuleName,Grantee,State) AS (
 SELECT m.n,g.n,g.s FROM (VALUES(N'LookupAuthenticationCredentialV1'),(N'ResolveDesktopRememberCredentialV2'))m(n)
 CROSS JOIN (VALUES(N'shale_app','G'),(N'shale_runtime','D'),(N'shale_runtime_role','D'))g(n,s))
SELECT @Findings=(SELECT COUNT_BIG(*) FROM Expected e WHERE NOT EXISTS(SELECT 1 FROM sys.database_permissions p
 WHERE p.class=1 AND p.major_id=OBJECT_ID(N'dbo.'+e.ModuleName) AND p.minor_id=0 AND p.permission_name=N'EXECUTE'
 AND p.state=e.State AND p.grantee_principal_id=DATABASE_PRINCIPAL_ID(e.Grantee) AND p.grantor_principal_id=DATABASE_PRINCIPAL_ID(N'dbo')))
 +ABS(6-(SELECT COUNT_BIG(*) FROM sys.database_permissions WHERE class=1 AND major_id IN(OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'))));
SELECT CONVERT(varchar(300),CONCAT('FACT|STAGE_A_ACL|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

SELECT @Findings=COUNT_BIG(*) FROM sys.sql_expression_dependencies WHERE referenced_id IN
 (OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'))
 OR (referenced_entity_name IN(N'LookupAuthenticationCredentialV1',N'ResolveDesktopRememberCredentialV2') AND COALESCE(referenced_schema_name,N'dbo')=N'dbo');
SELECT CONVERT(varchar(300),CONCAT('FACT|STAGE_A_WRAPPERS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Expected(TableName,ColumnName,TypeId,MaxLength,Nullable,IdentityFlag,Scale) AS(SELECT * FROM(VALUES (N'UserSessions',N'Id',127,8,0,1,0),
(N'UserSessions',N'ShaleClientId',56,4,0,0,0),
(N'UserSessions',N'UserId',56,4,0,0,0),
(N'UserSessions',N'SessionId',36,16,0,0,0),
(N'UserSessions',N'ApplicationInstanceId',127,8,1,0,0),
(N'UserSessions',N'ClientType',167,16,0,0,0),
(N'UserSessions',N'CurrentAccessJti',36,16,0,0,0),
(N'UserSessions',N'IssuedAt',42,8,0,0,7),
(N'UserSessions',N'ExpiresAt',42,8,0,0,7),
(N'UserSessions',N'LastRefreshedAt',42,8,1,0,7),
(N'UserSessions',N'RevokedAt',42,8,1,0,7),
(N'UserSessions',N'RevocationReason',167,32,1,0,0),
(N'UserSessions',N'CreatedAt',42,8,0,0,7),
(N'UserSessions',N'UpdatedAt',42,8,0,0,7),
(N'UserSessions',N'RowVer',189,8,0,0,0),
(N'SessionSecurityAuditLog',N'Id',127,8,0,1,0),
(N'SessionSecurityAuditLog',N'ShaleClientId',56,4,0,0,0),
(N'SessionSecurityAuditLog',N'ActorUserId',56,4,0,0,0),
(N'SessionSecurityAuditLog',N'EventType',167,40,0,0,0),
(N'SessionSecurityAuditLog',N'TargetSessionId',36,16,1,0,0),
(N'SessionSecurityAuditLog',N'TargetUserId',56,4,1,0,0),
(N'SessionSecurityAuditLog',N'AffectedCount',56,4,0,0,0),
(N'SessionSecurityAuditLog',N'ReasonCode',167,32,0,0,0),
(N'SessionSecurityAuditLog',N'OccurredAt',42,8,0,0,7))v(a,b,c,d,e,f,g))
SELECT @Findings=(SELECT COUNT_BIG(*) FROM Expected e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ColumnName
 WHERE c.column_id IS NULL OR c.system_type_id<>e.TypeId OR c.user_type_id<>c.system_type_id OR c.max_length<>e.MaxLength OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityFlag OR c.scale<>e.Scale OR c.is_computed<>0)
 +ABS(24-(SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id IN(OBJECT_ID(N'dbo.SessionSecurityAuditLog'),OBJECT_ID(N'dbo.UserSessions'))))
 +(SELECT COUNT_BIG(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.object_id IN(OBJECT_ID(N'dbo.SessionSecurityAuditLog'),OBJECT_ID(N'dbo.UserSessions')) AND COALESCE(t.principal_id,s.principal_id)<>DATABASE_PRINCIPAL_ID(N'dbo'));
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_COLUMNS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Expected(TableName,ColumnName,TypeId,MaxLength,Nullable,IdentityFlag,Scale) AS(SELECT * FROM(VALUES (N'DesktopRememberCredentials',N'Id',127,8,0,1,0),
(N'DesktopRememberCredentials',N'ShaleClientId',56,4,0,0,0),
(N'DesktopRememberCredentials',N'UserId',56,4,0,0,0),
(N'DesktopRememberCredentials',N'SessionId',36,16,0,0,0),
(N'DesktopRememberCredentials',N'InstallationId',36,16,0,0,0),
(N'DesktopRememberCredentials',N'CredentialHash',173,32,0,0,0),
(N'DesktopRememberCredentials',N'AbsoluteExpiresAt',42,8,0,0,7),
(N'DesktopRememberCredentials',N'RotatedAt',42,8,1,0,7),
(N'DesktopRememberCredentials',N'ConsumedAt',42,8,1,0,7),
(N'DesktopRememberCredentials',N'CreatedAt',42,8,0,0,7),
(N'DesktopRememberCredentials',N'UpdatedAt',42,8,0,0,7))v(a,b,c,d,e,f,g))
SELECT @Findings=(SELECT COUNT_BIG(*) FROM Expected e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ColumnName
 WHERE c.column_id IS NULL OR c.system_type_id<>e.TypeId OR c.user_type_id<>c.system_type_id OR c.max_length<>e.MaxLength OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityFlag OR c.scale<>e.Scale OR c.is_computed<>0)
 +ABS(11-(SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id IN(OBJECT_ID(N'dbo.DesktopRememberCredentials'))))
 +(SELECT COUNT_BIG(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.object_id IN(OBJECT_ID(N'dbo.DesktopRememberCredentials')) AND COALESCE(t.principal_id,s.principal_id)<>DATABASE_PRINCIPAL_ID(N'dbo'));
SELECT CONVERT(varchar(300),CONCAT('FACT|REMEMBER_COLUMNS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Expected(T,N,R,LCols,RCols,CascadeFlag) AS(SELECT * FROM(VALUES (N'UserSessions',N'FK_UserSessions_Client',N'ShaleClients',N'ShaleClientId',N'Id',0),(N'UserSessions',N'FK_UserSessions_UserTenant',N'Users',N'ShaleClientId,UserId',N'ShaleClientId,Id',0),(N'UserSessions',N'FK_UserSessions_InstanceTenant',N'ApplicationInstances',N'ShaleClientId,ApplicationInstanceId',N'ShaleClientId,Id',0),(N'SessionSecurityAuditLog',N'FK_SessionSecurityAuditLog_Client',N'ShaleClients',N'ShaleClientId',N'Id',0),(N'SessionSecurityAuditLog',N'FK_SessionSecurityAuditLog_Actor',N'Users',N'ShaleClientId,ActorUserId',N'ShaleClientId,Id',0),(N'SessionSecurityAuditLog',N'FK_SessionSecurityAuditLog_TargetUser',N'Users',N'ShaleClientId,TargetUserId',N'ShaleClientId,Id',0))v(a,b,c,d,e,f))
SELECT @Findings=COUNT_BIG(*) FROM Expected e WHERE NOT EXISTS(SELECT 1 FROM sys.foreign_keys f
 WHERE f.name=e.N AND f.parent_object_id=OBJECT_ID(N'dbo.'+e.T) AND f.referenced_object_id=OBJECT_ID(N'dbo.'+e.R)
 AND f.is_disabled=0 AND f.is_not_trusted=0 AND f.delete_referential_action=e.CascadeFlag AND f.update_referential_action=0
 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),COL_NAME(k.parent_object_id,k.parent_column_id)),N',') WITHIN GROUP(ORDER BY k.constraint_column_id) FROM sys.foreign_key_columns k WHERE k.constraint_object_id=f.object_id)=e.LCols
 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),COL_NAME(k.referenced_object_id,k.referenced_column_id)),N',') WITHIN GROUP(ORDER BY k.constraint_column_id) FROM sys.foreign_key_columns k WHERE k.constraint_object_id=f.object_id)=e.RCols);
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_KEYS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Expected(T,N,R,LCols,RCols,CascadeFlag) AS(SELECT * FROM(VALUES (N'DesktopRememberCredentials',N'FK_DesktopRememberCredentials_Session',N'UserSessions',N'SessionId',N'SessionId',1),(N'DesktopRememberCredentials',N'FK_DesktopRememberCredentials_User',N'Users',N'ShaleClientId,UserId',N'ShaleClientId,Id',0))v(a,b,c,d,e,f))
SELECT @Findings=COUNT_BIG(*) FROM Expected e WHERE NOT EXISTS(SELECT 1 FROM sys.foreign_keys f
 WHERE f.name=e.N AND f.parent_object_id=OBJECT_ID(N'dbo.'+e.T) AND f.referenced_object_id=OBJECT_ID(N'dbo.'+e.R)
 AND f.is_disabled=0 AND f.is_not_trusted=0 AND f.delete_referential_action=e.CascadeFlag AND f.update_referential_action=0
 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),COL_NAME(k.parent_object_id,k.parent_column_id)),N',') WITHIN GROUP(ORDER BY k.constraint_column_id) FROM sys.foreign_key_columns k WHERE k.constraint_object_id=f.object_id)=e.LCols
 AND (SELECT STRING_AGG(CONVERT(nvarchar(max),COL_NAME(k.referenced_object_id,k.referenced_column_id)),N',') WITHIN GROUP(ORDER BY k.constraint_column_id) FROM sys.foreign_key_columns k WHERE k.constraint_object_id=f.object_id)=e.RCols);
SELECT CONVERT(varchar(300),CONCAT('FACT|REMEMBER_KEYS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

;WITH Depths(n) AS (SELECT * FROM(VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8))v(n)),
DefinitionForms(d) AS (SELECT REPLICATE(N'(',o.n)+N'sec.fn_filterbytenant('+REPLICATE(N'(',a.n)
 +N'shaleclientid'+REPLICATE(N')',a.n)+N')'+REPLICATE(N')',o.n) FROM Depths o CROSS JOIN Depths a),
Expected(T,Kind,Operation) AS(SELECT t.n,p.k,p.o FROM(VALUES(N'UserSessions'),(N'SessionSecurityAuditLog'))t(n) CROSS JOIN(VALUES(N'FILTER',NULL),(N'BLOCK',N'AFTER INSERT'),(N'BLOCK',N'AFTER UPDATE'))p(k,o))
SELECT @Findings=(SELECT COUNT_BIG(*) FROM Expected e WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id JOIN sys.schemas sc ON sc.schema_id=s.schema_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.'+e.T) AND p.predicate_type_desc=e.Kind AND ((e.Kind=N'FILTER' AND p.operation IS NULL AND p.operation_desc IS NULL) OR (e.Kind=N'BLOCK' AND p.operation_desc=e.Operation))
 AND s.name=N'TenantFilter' AND sc.name=N'sec' AND sc.principal_id=DATABASE_PRINCIPAL_ID(N'dbo') AND COALESCE(s.principal_id,sc.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo') AND s.is_enabled=1 AND s.is_schema_bound=1
 AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N'')) IN(SELECT d FROM DefinitionForms)))
 +ABS(6-(SELECT COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.UserSessions'),OBJECT_ID(N'dbo.SessionSecurityAuditLog'))));
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_RLS|',CASE WHEN EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.UserSessions'),OBJECT_ID(N'dbo.SessionSecurityAuditLog')) AND predicate_definition IS NULL) THEN 'UNKNOWN' WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

-- BEGIN LOCALIZED SESSION_RLS (fixed contract labels only)
-- Cohorts: FILTER predicates by type; BLOCK slots by operation, independently of type.
-- A missing cohort cannot be attributed to an arbitrary predicate in another slot.
;WITH Depths(n) AS (SELECT * FROM(VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8))v(n)),
DefinitionForms(d) AS (SELECT REPLICATE(N'(',o.n)+N'sec.fn_filterbytenant('+REPLICATE(N'(',a.n)
 +N'shaleclientid'+REPLICATE(N')',a.n)+N')'+REPLICATE(N')',o.n) FROM Depths o CROSS JOIN Depths a),
Expected(Slot,T,Kind,Operation) AS (SELECT * FROM (VALUES
 (0,N'UserSessions',N'FILTER',NULL),
 (1,N'UserSessions',N'BLOCK',N'AFTER INSERT'),
 (2,N'UserSessions',N'BLOCK',N'AFTER UPDATE'),
 (3,N'SessionSecurityAuditLog',N'FILTER',NULL),
 (4,N'SessionSecurityAuditLog',N'BLOCK',N'AFTER INSERT'),
 (5,N'SessionSecurityAuditLog',N'BLOCK',N'AFTER UPDATE'))v(i,t,k,o)),
Facts(Slot,FactName,Metric) AS (SELECT * FROM (VALUES
 (0,'SESSION_RLS_USERSESSIONS_FILTER_PRESENT',0),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_POLICY_SCHEMA',1),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_POLICY_OWNER',2),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_ENABLED',3),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_SCHEMA_BOUND',4),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_TYPE_OPERATION',5),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_FUNCTION_DEFINITION',6),
 (0,'SESSION_RLS_USERSESSIONS_FILTER_EXTRA_PREDICATES',7),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_PRESENT',0),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_POLICY_SCHEMA',1),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_POLICY_OWNER',2),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_ENABLED',3),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_SCHEMA_BOUND',4),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_TYPE_OPERATION',5),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_FUNCTION_DEFINITION',6),
 (1,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_EXTRA_PREDICATES',7),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_PRESENT',0),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_POLICY_SCHEMA',1),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_POLICY_OWNER',2),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_ENABLED',3),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_SCHEMA_BOUND',4),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_TYPE_OPERATION',5),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_FUNCTION_DEFINITION',6),
 (2,'SESSION_RLS_USERSESSIONS_BLOCK_AFTER_UPDATE_EXTRA_PREDICATES',7),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_PRESENT',0),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_POLICY_SCHEMA',1),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_POLICY_OWNER',2),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_ENABLED',3),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_SCHEMA_BOUND',4),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_TYPE_OPERATION',5),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_FUNCTION_DEFINITION',6),
 (3,'SESSION_RLS_SESSIONAUDIT_FILTER_EXTRA_PREDICATES',7),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_PRESENT',0),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_POLICY_SCHEMA',1),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_POLICY_OWNER',2),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_ENABLED',3),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_SCHEMA_BOUND',4),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_TYPE_OPERATION',5),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_FUNCTION_DEFINITION',6),
 (4,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_INSERT_EXTRA_PREDICATES',7),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_PRESENT',0),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_POLICY_SCHEMA',1),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_POLICY_OWNER',2),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_ENABLED',3),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_SCHEMA_BOUND',4),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_TYPE_OPERATION',5),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_FUNCTION_DEFINITION',6),
 (5,'SESSION_RLS_SESSIONAUDIT_BLOCK_AFTER_UPDATE_EXTRA_PREDICATES',7))v(i,n,m)),
Observed AS (
 SELECT e.Slot,OBJECT_ID(N'dbo.'+e.T) AS TargetId,
 COUNT_BIG(p.security_predicate_id) AS CandidateCount,
 COALESCE(SUM(CASE WHEN p.security_predicate_id IS NOT NULL AND
   (s.name<>N'TenantFilter' OR sc.name<>N'sec' OR s.object_id IS NULL) THEN 1 ELSE 0 END),0) AS PolicyBad,
 COALESCE(SUM(CASE WHEN p.security_predicate_id IS NOT NULL AND
   (sc.principal_id<>DATABASE_PRINCIPAL_ID(N'dbo') OR COALESCE(s.principal_id,sc.principal_id)<>DATABASE_PRINCIPAL_ID(N'dbo')) THEN 1 ELSE 0 END),0) AS OwnerBad,
 COALESCE(SUM(CASE WHEN s.is_enabled<>1 THEN 1 ELSE 0 END),0) AS EnabledBad,
 COALESCE(SUM(CASE WHEN s.is_schema_bound<>1 THEN 1 ELSE 0 END),0) AS BoundBad,
 -- FILTER has no operation: both catalog operation fields must be NULL.
 COALESCE(SUM(CASE WHEN p.security_predicate_id IS NOT NULL AND
   (p.predicate_type_desc<>e.Kind OR (e.Kind=N'FILTER' AND (p.operation IS NOT NULL OR p.operation_desc IS NOT NULL)) OR (e.Kind=N'BLOCK' AND (p.operation_desc<>e.Operation OR p.operation_desc IS NULL))) THEN 1 ELSE 0 END),0) AS ShapeBad,
 COALESCE(SUM(CASE WHEN p.predicate_definition IS NOT NULL AND
   df.d IS NULL THEN 1 ELSE 0 END),0) AS DefinitionBad,
 COALESCE(SUM(CASE WHEN p.security_predicate_id IS NOT NULL AND p.predicate_definition IS NULL THEN 1 ELSE 0 END),0) AS DefinitionUnknown
 FROM Expected e LEFT JOIN sys.security_predicates p ON p.target_object_id=OBJECT_ID(N'dbo.'+e.T)
 AND ((e.Kind=N'FILTER' AND p.predicate_type_desc=N'FILTER') OR (e.Kind=N'BLOCK' AND p.operation_desc=e.Operation))
 LEFT JOIN sys.security_policies s ON s.object_id=p.object_id
 LEFT JOIN sys.schemas sc ON sc.schema_id=s.schema_id
 LEFT JOIN DefinitionForms df ON LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''))=df.d
 GROUP BY e.Slot,e.T
), Results AS (
 SELECT f.FactName,
 CASE WHEN f.Metric BETWEEN 1 AND 6 AND o.CandidateCount=0 THEN 1
      WHEN f.Metric=6 THEN o.DefinitionUnknown ELSE 0 END AS UnknownCount,
 CASE f.Metric WHEN 0 THEN CASE WHEN o.TargetId IS NULL OR o.CandidateCount=0 THEN 1 ELSE 0 END
 WHEN 1 THEN o.PolicyBad WHEN 2 THEN o.OwnerBad WHEN 3 THEN o.EnabledBad
 WHEN 4 THEN o.BoundBad WHEN 5 THEN o.ShapeBad WHEN 6 THEN o.DefinitionBad
 WHEN 7 THEN CASE WHEN o.CandidateCount>1 THEN o.CandidateCount-1 ELSE 0 END END AS FindingCount
 FROM Facts f JOIN Observed o ON o.Slot=f.Slot
)
SELECT CONVERT(varchar(300),CONCAT('FACT|',FactName,'|',
 CASE WHEN FindingCount>0 THEN 'FAIL' WHEN UnknownCount>0 THEN 'UNKNOWN' ELSE 'PASS' END,
 '|',FindingCount+UnknownCount,'|-')) FROM Results ORDER BY FactName;
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_RLS_USERSESSIONS_FILTER_OPERATION_NULL|',CASE WHEN q.n IS NULL THEN 'UNKNOWN' WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',COALESCE(q.n,1),'|-')) FROM (SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.UserSessions') AND predicate_type_desc=N'FILTER') THEN NULL WHEN EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.UserSessions') AND predicate_type_desc=N'FILTER' AND (operation IS NOT NULL OR operation_desc IS NOT NULL)) THEN 1 ELSE 0 END)q(n);
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_RLS_USERSESSIONS_UNEXPECTED_PREDICATES|',CASE WHEN q.n IS NULL THEN 'UNKNOWN' WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',COALESCE(q.n,1),'|-')) FROM (SELECT COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.UserSessions') AND NOT ((predicate_type_desc=N'FILTER' AND operation IS NULL AND operation_desc IS NULL) OR (predicate_type_desc=N'BLOCK' AND COALESCE(operation_desc,N'') IN(N'AFTER INSERT',N'AFTER UPDATE'))))q(n);
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_RLS_SESSIONAUDIT_FILTER_OPERATION_NULL|',CASE WHEN q.n IS NULL THEN 'UNKNOWN' WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',COALESCE(q.n,1),'|-')) FROM (SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND predicate_type_desc=N'FILTER') THEN NULL WHEN EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND predicate_type_desc=N'FILTER' AND (operation IS NOT NULL OR operation_desc IS NOT NULL)) THEN 1 ELSE 0 END)q(n);
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_RLS_SESSIONAUDIT_UNEXPECTED_PREDICATES|',CASE WHEN q.n IS NULL THEN 'UNKNOWN' WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',COALESCE(q.n,1),'|-')) FROM (SELECT COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND NOT ((predicate_type_desc=N'FILTER' AND operation IS NULL AND operation_desc IS NULL) OR (predicate_type_desc=N'BLOCK' AND COALESCE(operation_desc,N'') IN(N'AFTER INSERT',N'AFTER UPDATE'))))q(n);
-- END LOCALIZED SESSION_RLS

SELECT @Findings=COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.DesktopRememberCredentials');
SELECT CONVERT(varchar(300),CONCAT('FACT|REMEMBER_NO_RLS|',CASE WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

SELECT @Findings=CASE WHEN EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id=OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') AND m.is_schema_bound=1 AND m.definition IS NOT NULL
 AND COALESCE(o.principal_id,s.principal_id)=DATABASE_PRINCIPAL_ID(N'dbo') AND m.execute_as_principal_id IS NULL)
 AND (SELECT COUNT(*) FROM sys.parameters WHERE object_id=OBJECT_ID(N'sec.fn_FilterByTenant') AND parameter_id>0)=1
 AND EXISTS(SELECT 1 FROM sys.parameters WHERE object_id=OBJECT_ID(N'sec.fn_FilterByTenant') AND parameter_id=1 AND system_type_id=56 AND is_output=0) THEN 0 ELSE 1 END;
SELECT CONVERT(varchar(300),CONCAT('FACT|STRICT_PREDICATE_SHAPE|',CASE WHEN OBJECT_ID(N'sec.fn_FilterByTenant') IS NOT NULL AND OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')) IS NULL THEN 'UNKNOWN' WHEN @Findings=0 THEN 'PASS' ELSE 'FAIL' END,'|',@Findings,'|','-'));

-- Definition hash only: the existing strict predicate's authoritative body is not in a migration.
SELECT CONVERT(varchar(300),CONCAT('FACT|STRICT_PREDICATE_REVIEW|UNKNOWN|1|',COALESCE(CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2),'-')))
 FROM (VALUES(1))v(n) LEFT JOIN sys.sql_modules m ON m.object_id=OBJECT_ID(N'sec.fn_FilterByTenant');
-- Stable private-review digest; definitions/metadata never leave this statement as plaintext.
SELECT @Json=(SELECT o.name AS TableName,c.name AS ColumnName,c.system_type_id,c.max_length,c.is_nullable,c.is_identity,c.is_computed,c.scale,
 d.definition AS DefaultDefinition FROM sys.tables o JOIN sys.columns c ON c.object_id=o.object_id LEFT JOIN sys.default_constraints d ON d.object_id=c.default_object_id
 WHERE o.object_id IN(OBJECT_ID(N'dbo.UserSessions'),OBJECT_ID(N'dbo.SessionSecurityAuditLog'),OBJECT_ID(N'dbo.DesktopRememberCredentials')) ORDER BY o.name,c.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT o.name AS TableName,k.name,k.type,k.definition,k.is_disabled,k.is_not_trusted FROM sys.check_constraints k JOIN sys.objects o ON o.object_id=k.parent_object_id
 WHERE o.name IN(N'UserSessions',N'SessionSecurityAuditLog',N'DesktopRememberCredentials') AND o.schema_id=SCHEMA_ID(N'dbo') ORDER BY o.name,k.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT o.name AS TableName,i.name,i.is_unique,i.is_primary_key,i.is_unique_constraint,i.is_disabled,i.has_filter,i.filter_definition,
 (SELECT c.name,ic.key_ordinal,ic.is_descending_key,ic.is_included_column FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id ORDER BY ic.index_column_id FOR JSON PATH) AS IndexColumns
 FROM sys.indexes i JOIN sys.objects o ON o.object_id=i.object_id WHERE o.object_id IN(OBJECT_ID(N'dbo.UserSessions'),OBJECT_ID(N'dbo.SessionSecurityAuditLog'),OBJECT_ID(N'dbo.DesktopRememberCredentials')) ORDER BY o.name,i.name FOR JSON PATH);
SELECT CONVERT(varchar(300),CONCAT('FACT|SESSION_CATALOG_REVIEW|UNKNOWN|1|',CONVERT(varchar(64),HASHBYTES('SHA2_256',@Json),2)));
-- SESSION_CATALOG_REVIEW includes all defaults/checks/unique/index definitions, for the existing record review.
-- Ordinary API parity is not inferred from a few table-existence checks. A prior source-bound
-- schema/grant record must cover all retained API routes, including mutation and required audit paths.
-- Source-referenced ordinary objects: scope is in contract.json; no application rows.
DECLARE @OrdinaryObjects nvarchar(max)=(SELECT v.n AS n,OBJECT_ID(N'dbo.'+v.n) AS id FROM(VALUES (N'AdministrativeReadAuditLog'),(N'ApplicationInstances'),(N'ApplicationPolicy'),(N'ApplicationReleaseItems'),(N'ApplicationReleases'),(N'AuditLog'),(N'CaseContacts'),(N'CaseDateConfirmationTargets'),(N'CaseDatePresentationConfigurations'),(N'CaseDatePresentationSelections'),(N'CaseDateTypeSemanticRoleMappings'),(N'CaseDateTypes'),(N'CaseDates'),(N'CaseLinkShares'),(N'CaseLinks'),(N'CaseOverviewConfigurations'),(N'CaseOverviewDateSelections'),(N'CaseParties'),(N'CaseStatuses'),(N'CaseTeamMemberRoles'),(N'CaseTeamRoleDefinitions'),(N'CaseTimelineEvents'),(N'CaseUpdates'),(N'CaseUsers'),(N'Cases'),(N'ContactAddresses'),(N'ContactContactTypes'),(N'ContactCredentials'),(N'ContactEmailAddresses'),(N'ContactPhoneNumbers'),(N'ContactSpecialties'),(N'ContactTypes'),(N'Contacts'),(N'CredentialDefinitions'),(N'EntityActionAuditLog'),(N'ExternalLinks'),(N'FieldConfirmationPolicies'),(N'FirmWideRoleDefinitions'),(N'FormConfigurationSections'),(N'FormConfigurations'),(N'FormConfiguredFields'),(N'GlobalControlPlaneAuditLog'),(N'IntakePhoneAvailability'),(N'LinkTypes'),(N'MaterialRequests'),(N'Notifications'),(N'OrganizationAddresses'),(N'OrganizationEmailAddresses'),(N'OrganizationOrganizationTypes'),(N'OrganizationPhoneNumbers'),(N'OrganizationTypes'),(N'OrganizationWebsites'),(N'Organizations'),(N'PartyRoles'),(N'PartySides'),(N'PracticeAreas'),(N'Priorities'),(N'RequestStatuses'),(N'Roles'),(N'SavedValueConfirmationRequirements'),(N'SavedValueConfirmations'),(N'Specialties'),(N'Statuses'),(N'TaskAssignments'),(N'TaskPolicyConfigurations'),(N'TaskStatuses'),(N'TaskTimelineEvents'),(N'TaskUpdates'),(N'Tasks'),(N'UserFirmWideRoleAssignments'),(N'UserSessions'),(N'Users'))v(n) FOR JSON PATH);
SELECT @Json=(SELECT e.n AS ObjectName,o.type,o.principal_id,s.principal_id AS SchemaOwner,c.name,c.system_type_id,c.max_length,c.scale,c.is_nullable,c.is_identity,c.is_computed,d.definition AS DefaultDefinition
 FROM OPENJSON(@OrdinaryObjects) WITH(n nvarchar(128) '$.n',id int '$.id')e LEFT JOIN sys.objects o ON o.object_id=e.id
 LEFT JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.columns c ON c.object_id=o.object_id LEFT JOIN sys.default_constraints d ON d.object_id=c.default_object_id
 ORDER BY e.n,c.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT e.n AS ObjectName,k.name,k.definition,k.is_disabled,k.is_not_trusted FROM OPENJSON(@OrdinaryObjects) WITH(n nvarchar(128) '$.n',id int '$.id')e JOIN sys.check_constraints k ON k.parent_object_id=e.id ORDER BY e.n,k.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT e.n AS ObjectName,f.name,f.is_disabled,f.is_not_trusted,f.delete_referential_action,f.update_referential_action,OBJECT_SCHEMA_NAME(f.referenced_object_id) AS RefSchema,OBJECT_NAME(f.referenced_object_id) AS RefTable,
 (SELECT COL_NAME(k.parent_object_id,k.parent_column_id) AS L,COL_NAME(k.referenced_object_id,k.referenced_column_id) AS R FROM sys.foreign_key_columns k WHERE k.constraint_object_id=f.object_id ORDER BY k.constraint_column_id FOR JSON PATH) AS KeysJson
 FROM OPENJSON(@OrdinaryObjects) WITH(n nvarchar(128) '$.n',id int '$.id')e JOIN sys.foreign_keys f ON f.parent_object_id=e.id ORDER BY e.n,f.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT e.n AS ObjectName,i.name,i.is_unique,i.is_disabled,i.filter_definition,
 (SELECT COL_NAME(k.object_id,k.column_id) AS Col,k.key_ordinal,k.is_descending_key,k.is_included_column FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id ORDER BY k.index_column_id FOR JSON PATH) AS KeysJson
 FROM OPENJSON(@OrdinaryObjects) WITH(n nvarchar(128) '$.n',id int '$.id')e JOIN sys.indexes i ON i.object_id=e.id ORDER BY e.n,i.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT OBJECT_NAME(p.target_object_id) AS ObjectName,p.predicate_definition,p.predicate_type_desc,p.operation_desc,s.name,s.is_enabled,s.is_schema_bound FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id WHERE p.target_object_id IN(SELECT id FROM OPENJSON(@OrdinaryObjects) WITH(id int '$.id')) ORDER BY ObjectName,p.predicate_type_desc,p.operation_desc FOR JSON PATH);
SELECT @Json=@Json+(SELECT OBJECT_SCHEMA_NAME(o.object_id) AS SchemaName,o.name,o.type,OBJECT_NAME(o.parent_object_id) AS ParentName,o.principal_id,m.execute_as_principal_id,m.is_schema_bound,m.uses_ansi_nulls,m.uses_quoted_identifier,CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) AS ModuleHash
 FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.object_id IN(SELECT id FROM OPENJSON(@OrdinaryObjects) WITH(id int '$.id'))
 OR o.parent_object_id IN(SELECT id FROM OPENJSON(@OrdinaryObjects) WITH(id int '$.id'))
 OR o.object_id IN(OBJECT_ID(N'sec.fn_FilterByTenant'),OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal'))
 ORDER BY SchemaName,o.name FOR JSON PATH);
SELECT @Json=@Json+(SELECT OBJECT_NAME(c.object_id) AS ObjectName,c.name,c.definition FROM sys.computed_columns c WHERE c.object_id IN(SELECT id FROM OPENJSON(@OrdinaryObjects) WITH(id int '$.id')) ORDER BY ObjectName,c.name FOR JSON PATH);
-- Full database ACL/role membership is hashed, never printed; no SID, credential or application row.
SELECT @Json=@Json+(SELECT USER_NAME(grantee_principal_id) AS Grantee,USER_NAME(grantor_principal_id) AS Grantor,class,major_id,minor_id,permission_name,state FROM sys.database_permissions ORDER BY grantee_principal_id,grantor_principal_id,class,major_id,minor_id,permission_name FOR JSON PATH);
SELECT @Json=@Json+(SELECT USER_NAME(role_principal_id) AS RoleName,USER_NAME(member_principal_id) AS MemberName FROM sys.database_role_members ORDER BY role_principal_id,member_principal_id FOR JSON PATH);
SELECT CONVERT(varchar(300),CONCAT('FACT|ORDINARY_API_REVIEW|UNKNOWN|1|',CONVERT(varchar(64),HASHBYTES('SHA2_256',@Json),2)));

-- Reviewed ordinary-runtime elevated EXECUTE allowlist is EMPTY; see reconciliation.md.
-- AI bootstrap resolvers and the private Case helper are not ordinary-runtime authorities.
-- Do not exempt a module merely because it is signed or executes as owner.
-- Snapshot only non-secret catalog names before impersonation; do not print them.
DECLARE @Owners nvarchar(max)=(SELECT id FROM(SELECT principal_id AS id FROM sys.schemas WHERE principal_id IS NOT NULL UNION SELECT principal_id FROM sys.objects WHERE principal_id IS NOT NULL)v FOR JSON PATH);
DECLARE @Principals nvarchar(max)=(SELECT name AS n FROM sys.database_principals WHERE type IN('S','E','X') FOR JSON PATH);

DECLARE @HasShaleAdmins bit=CASE WHEN DATABASE_PRINCIPAL_ID(N'ShaleAdmins') IS NULL THEN 0 ELSE 1 END;
DECLARE @FixedRoles nvarchar(max)=(SELECT principal_id AS id FROM sys.database_principals WHERE is_fixed_role=1 AND name<>N'public' FOR JSON PATH);
-- Equivalent metadata snapshots for the existing authority checks, using scalar JSON instead of temp tables.
DECLARE @AuthObjects nvarchar(max)=(SELECT QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) AS n FROM sys.objects WHERE is_ms_shipped=0 AND type IN('U','V','P','FN','IF','TF') FOR JSON PATH);
DECLARE @AuthColumns nvarchar(max)=(SELECT QUOTENAME(OBJECT_SCHEMA_NAME(c.object_id))+N'.'+QUOTENAME(OBJECT_NAME(c.object_id)) AS o,c.name AS n FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF') FOR JSON PATH);
DECLARE @GrantOptions nvarchar(max)=(SELECT grantee_principal_id AS id FROM sys.database_permissions WHERE state='W' FOR JSON PATH);
DECLARE @Elevated nvarchar(max)=(SELECT QUOTENAME(OBJECT_SCHEMA_NAME(o.object_id))+N'.'+QUOTENAME(o.name) AS n FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0 AND (m.execute_as_principal_id IS NOT NULL OR m.definition IS NULL OR EXISTS(SELECT 1 FROM sys.crypt_properties cp WHERE cp.class=1 AND cp.major_id=o.object_id)) AND o.object_id NOT IN(COALESCE(OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1'),-1),COALESCE(OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2'),-1)) FOR JSON PATH);
DECLARE @ElevatedVisibilityUnknown bigint=(SELECT COUNT_BIG(*) FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0 AND m.definition IS NULL);
-- Session impersonation only, as in existing postchecks; never EXEC a procedure.
-- Failure to impersonate aborts sqlcmd; the collector returns UNKNOWN, never PASS.
EXECUTE AS USER=N'shale_app';
BEGIN TRY
 SELECT @Unknown=SUM(CASE WHEN v.p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN v.p<>1 THEN 1 ELSE 0 END)
 FROM(VALUES(HAS_PERMS_BY_NAME(N'dbo.LookupAuthenticationCredentialV1','OBJECT','EXECUTE')),(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredentialV2','OBJECT','EXECUTE')))v(p);
 SELECT CONVERT(varchar(300),CONCAT('FACT|AUTH_EFFECTIVE|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));
 SELECT @Unknown=SUM(CASE WHEN v.p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN v.p<>0 THEN 1 ELSE 0 END)
 FROM(VALUES(IS_MEMBER(N'db_owner')),(CASE WHEN @HasShaleAdmins=0 THEN 0 ELSE IS_MEMBER(N'ShaleAdmins') END),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','EXECUTE')),(HAS_PERMS_BY_NAME(N'dbo','USER','IMPERSONATE')),(HAS_PERMS_BY_NAME(N'dbo','SCHEMA','ALTER')),(HAS_PERMS_BY_NAME(N'dbo','SCHEMA','EXECUTE')))v(p);
 SELECT @Findings=@Findings+(SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@Owners) WITH(id int '$.id')o ON o.id=t.principal_id)
 +(SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@FixedRoles) WITH(id int '$.id')o ON o.id=t.principal_id)
 +(SELECT COUNT_BIG(*) FROM sys.fn_my_permissions(NULL,'DATABASE') WHERE permission_name NOT IN(N'CONNECT',N'VIEW ANY COLUMN ENCRYPTION KEY DEFINITION',N'VIEW ANY COLUMN MASTER KEY DEFINITION'));
 SELECT @Findings=@Findings+(SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@GrantOptions) WITH(id int '$.id')g ON g.id=t.principal_id);
 SELECT @Unknown=@Unknown+COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0),@Findings=@Findings+COALESCE(SUM(CASE WHEN p<>0 THEN 1 ELSE 0 END),0)
 FROM(SELECT HAS_PERMS_BY_NAME(o.n,'OBJECT',v.a) AS p FROM OPENJSON(@AuthObjects) WITH(n nvarchar(517) '$.n')o
 CROSS JOIN(VALUES(N'ALTER'),(N'CONTROL'),(N'INSERT'),(N'UPDATE'),(N'DELETE'),(N'SELECT'),(N'EXECUTE'))v(a)
 WHERE (v.a<>N'SELECT' OR o.n<>N'[dbo].[Users]') AND(v.a<>N'EXECUTE' OR o.n NOT IN(N'[dbo].[LookupAuthenticationCredentialV1]',N'[dbo].[ResolveDesktopRememberCredentialV2]',N'[dbo].[ResolveDesktopRememberCredential]'))
 UNION ALL SELECT HAS_PERMS_BY_NAME(c.o,'OBJECT',v.a,c.n,'COLUMN') FROM OPENJSON(@AuthColumns) WITH(o nvarchar(517) '$.o',n nvarchar(128) '$.n')c CROSS JOIN(VALUES(N'UPDATE'),(N'SELECT'))v(a) WHERE v.a=N'UPDATE' OR c.o<>N'[dbo].[Users]'
 UNION ALL SELECT HAS_PERMS_BY_NAME(p.n,'USER','IMPERSONATE') FROM OPENJSON(@Principals) WITH(n nvarchar(128) '$.n')p WHERE p.n<>USER_NAME())q;
 SELECT CONVERT(varchar(300),CONCAT('FACT|AUTH_NONBYPASS|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 OR USER_NAME()=N'dbo' THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));
 REVERT;
END TRY BEGIN CATCH REVERT; THROW; END CATCH;
EXECUTE AS USER=N'shale_runtime';
BEGIN TRY
 SELECT @Unknown=SUM(CASE WHEN v.p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN v.p<>0 THEN 1 ELSE 0 END)
 FROM(VALUES(HAS_PERMS_BY_NAME(N'dbo.LookupAuthenticationCredentialV1','OBJECT','EXECUTE')),(HAS_PERMS_BY_NAME(N'dbo.ResolveDesktopRememberCredentialV2','OBJECT','EXECUTE')))v(p);
 SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_MODULE_FENCES|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));

 SELECT @Unknown=SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN p<>1 THEN 1 ELSE 0 END)
 FROM(SELECT HAS_PERMS_BY_NAME(o,'OBJECT',a) AS p FROM(VALUES (N'dbo.UserSessions',N'SELECT'),(N'dbo.UserSessions',N'INSERT'),(N'dbo.UserSessions',N'UPDATE'),(N'dbo.SessionSecurityAuditLog',N'INSERT'))v(o,a))q;
 SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_SESSION_PERMISSIONS|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));

 SELECT @Unknown=SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN p<>1 THEN 1 ELSE 0 END)
 FROM(SELECT HAS_PERMS_BY_NAME(o,'OBJECT',a) AS p FROM(VALUES (N'dbo.DesktopRememberCredentials',N'SELECT'),(N'dbo.DesktopRememberCredentials',N'INSERT'),(N'dbo.DesktopRememberCredentials',N'UPDATE'),(N'dbo.DesktopRememberCredentials',N'DELETE'))v(o,a))q;
 SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_REMEMBER_PERMISSIONS|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));

 SELECT @Unknown=SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN p<>1 THEN 1 ELSE 0 END)
 FROM(SELECT HAS_PERMS_BY_NAME(N'dbo.Users','OBJECT','SELECT',n,'COLUMN') AS p FROM(VALUES (N'Id'),(N'ShaleClientId'),(N'name_first'),(N'name_last'),(N'email'),(N'is_admin'),(N'is_attorney'),(N'is_deleted'),(N'IsRemoved'),(N'color'),(N'default_organization'),(N'organization_id'),(N'initials'))v(n)
 UNION ALL SELECT HAS_PERMS_BY_NAME(N'dbo.ApplicationInstances','OBJECT','SELECT'))q;
 SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_BASELINE_PROJECTIONS|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));

 SELECT @Unknown=SUM(CASE WHEN v.p IS NULL THEN 1 ELSE 0 END),@Findings=SUM(CASE WHEN v.p<>0 THEN 1 ELSE 0 END)
 FROM(VALUES(IS_MEMBER(N'db_owner')),(CASE WHEN @HasShaleAdmins=0 THEN 0 ELSE IS_MEMBER(N'ShaleAdmins') END),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SCHEMA')),(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SECURITY POLICY')),(HAS_PERMS_BY_NAME(N'dbo','USER','IMPERSONATE')),(HAS_PERMS_BY_NAME(N'dbo','SCHEMA','ALTER')))v(p);
 SELECT @Unknown=@Unknown+CASE WHEN IS_MEMBER(N'shale_runtime_role') IS NULL THEN 1 ELSE 0 END;
 SELECT @Findings=@Findings+CASE WHEN IS_MEMBER(N'shale_runtime_role')<>1 THEN 1 ELSE 0 END
 +(SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@Owners) WITH(id int '$.id')o ON o.id=t.principal_id)
 +(SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@FixedRoles) WITH(id int '$.id')o ON o.id=t.principal_id)
 +(SELECT COUNT_BIG(*) FROM OPENJSON(@Principals) WITH(n nvarchar(128) '$.n')p WHERE p.n<>USER_NAME() AND HAS_PERMS_BY_NAME(p.n,'USER','IMPERSONATE')=1);
 SELECT @Unknown=@Unknown+(SELECT COUNT_BIG(*) FROM OPENJSON(@Principals) WITH(n nvarchar(128) '$.n')p WHERE p.n<>USER_NAME() AND HAS_PERMS_BY_NAME(p.n,'USER','IMPERSONATE') IS NULL);
 SELECT @Unknown=@Unknown+COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0),@Findings=@Findings+COALESCE(SUM(CASE WHEN p<>0 THEN 1 ELSE 0 END),0)
 FROM(SELECT HAS_PERMS_BY_NAME(o.n,'OBJECT',v.a) AS p FROM OPENJSON(@AuthObjects) WITH(n nvarchar(517) '$.n')o CROSS JOIN(VALUES(N'ALTER'),(N'CONTROL'))v(a)
 UNION ALL SELECT HAS_PERMS_BY_NAME(o.n,'OBJECT','EXECUTE') FROM OPENJSON(@Elevated) WITH(n nvarchar(517) '$.n')o)q;
 SET @Unknown=@Unknown+@ElevatedVisibilityUnknown;
 SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS|',CASE WHEN @Unknown>0 THEN 'UNKNOWN' WHEN @Findings>0 OR USER_NAME()=N'dbo' THEN 'FAIL' ELSE 'PASS' END,'|',@Unknown+@Findings,'|-'));
-- BEGIN LOCALIZED RUNTIME_NONBYPASS (same impersonation and snapshots)
SELECT CONVERT(varchar(300),CONCAT('FACT|',v.n,'|',CASE WHEN v.p IS NULL THEN 'UNKNOWN' WHEN v.p<>v.required THEN 'FAIL' ELSE 'PASS' END,'|',CASE WHEN v.p=v.required THEN 0 ELSE 1 END,'|-'))
 FROM(VALUES
 ('RUNTIME_NONBYPASS_DB_OWNER',IS_MEMBER(N'db_owner'),0),
 ('RUNTIME_NONBYPASS_SHALEADMINS',CASE WHEN @HasShaleAdmins=0 THEN 0 ELSE IS_MEMBER(N'ShaleAdmins') END,0),
 ('RUNTIME_NONBYPASS_CONTROL_DATABASE',HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0),
 ('RUNTIME_NONBYPASS_ALTER_ANY_USER',HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER'),0),
 ('RUNTIME_NONBYPASS_ALTER_ANY_ROLE',HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE'),0),
 ('RUNTIME_NONBYPASS_ALTER_ANY_SCHEMA',HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SCHEMA'),0),
 ('RUNTIME_NONBYPASS_ALTER_ANY_SECURITY_POLICY',HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SECURITY POLICY'),0),
 ('RUNTIME_NONBYPASS_IMPERSONATE_DBO',HAS_PERMS_BY_NAME(N'dbo','USER','IMPERSONATE'),0),
 ('RUNTIME_NONBYPASS_ALTER_DBO_SCHEMA',HAS_PERMS_BY_NAME(N'dbo','SCHEMA','ALTER'),0),
 ('RUNTIME_NONBYPASS_RUNTIME_ROLE_REQUIRED',IS_MEMBER(N'shale_runtime_role'),1))v(n,p,required);
SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS_OWNER_PRINCIPALS|',CASE WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',q.n,'|-')) FROM (SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@Owners) WITH(id int '$.id')o ON o.id=t.principal_id)q(n);
SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS_FIXED_DATABASE_ROLES|',CASE WHEN q.n=0 THEN 'PASS' ELSE 'FAIL' END,'|',q.n,'|-')) FROM (SELECT COUNT_BIG(*) FROM sys.user_token t JOIN OPENJSON(@FixedRoles) WITH(id int '$.id')o ON o.id=t.principal_id)q(n);
SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS_IMPERSONATE_SCOPED_PRINCIPALS|',CASE WHEN q.bad>0 THEN 'FAIL' WHEN q.unknowns>0 THEN 'UNKNOWN' ELSE 'PASS' END,'|',q.bad+q.unknowns,'|-')) FROM (SELECT COALESCE(SUM(CASE WHEN p<>0 THEN 1 ELSE 0 END),0) AS bad,COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0) AS unknowns FROM (SELECT HAS_PERMS_BY_NAME(p.n,'USER','IMPERSONATE') AS p FROM OPENJSON(@Principals) WITH(n nvarchar(128) '$.n')p WHERE p.n<>USER_NAME())v)q;
SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS_PROTECTED_OBJECT_AUTHORITY|',CASE WHEN q.bad>0 THEN 'FAIL' WHEN q.unknowns>0 THEN 'UNKNOWN' ELSE 'PASS' END,'|',q.bad+q.unknowns,'|-')) FROM (SELECT COALESCE(SUM(CASE WHEN p<>0 THEN 1 ELSE 0 END),0) AS bad,COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0) AS unknowns FROM (SELECT HAS_PERMS_BY_NAME(o.n,'OBJECT',v.a) AS p FROM OPENJSON(@AuthObjects) WITH(n nvarchar(517) '$.n')o CROSS JOIN(VALUES(N'ALTER'),(N'CONTROL'))v(a))v)q;
SELECT CONVERT(varchar(300),CONCAT('FACT|RUNTIME_NONBYPASS_ELEVATED_OBJECT_EXECUTE|',CASE WHEN q.bad>0 THEN 'FAIL' WHEN q.unknowns+@ElevatedVisibilityUnknown>0 THEN 'UNKNOWN' ELSE 'PASS' END,'|',q.bad+q.unknowns+@ElevatedVisibilityUnknown,'|-')) FROM (SELECT COALESCE(SUM(CASE WHEN p<>0 THEN 1 ELSE 0 END),0) AS bad,COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0) AS unknowns FROM (SELECT HAS_PERMS_BY_NAME(o.n,'OBJECT','EXECUTE') AS p FROM OPENJSON(@Elevated) WITH(n nvarchar(517) '$.n')o)v)q;
-- END LOCALIZED RUNTIME_NONBYPASS
 REVERT;
END TRY BEGIN CATCH REVERT; THROW; END CATCH;
SELECT 'FACT|SQL_COMPLETE|PASS|0|-';

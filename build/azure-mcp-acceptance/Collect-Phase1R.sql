:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
GO
/* Catalogs only. Deliberately do not include owner.sql: its OFF check reads
   application control rows. No SQL permissions, credentials or business data. */
IF USER_NAME()<>N'dbo' OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 THROW 59801,'STOP: existing database-local owner visibility required.',1;
DECLARE @Other nvarchar(max)=(
 SELECT p.* FROM sys.security_predicates p
 WHERE p.target_object_id NOT IN
  (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
 ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH);
DECLARE @Evidence nvarchar(max)=(SELECT
 JSON_QUERY((SELECT DB_NAME() AS [database],
  CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) AS [server],
  CONVERT(int,SERVERPROPERTY('EngineEdition')) AS [engine],
  CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')) AS [collation],
  (SELECT physical_database_name FROM sys.databases WHERE database_id=DB_ID()) AS [physical],
  (SELECT COUNT(*) FROM sys.extended_properties WHERE class=3
    AND major_id=SCHEMA_ID(N'sec') AND name=N'Shale.Phase1R.v1') AS [receipts],
  OBJECT_ID(N'dbo.AiCaseDataInvocation') AS [invocation],
  CONVERT(varchar(33),SYSUTCDATETIME(),126) AS [collectedUtc]
  FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER)) AS [identity],
 JSON_QUERY((SELECT
  JSON_QUERY(N'["'+STRING_ESCAPE(s.name,'json')+N'","'+STRING_ESCAPE(o.name,'json')+N'"]') AS [name],
  o.type AS [type],m.is_schema_bound AS [bound],m.uses_ansi_nulls AS [ansi],
  m.uses_quoted_identifier AS [quoted],m.execute_as_principal_id AS [executeAs],
  CONVERT(varchar(max),CONVERT(varbinary(max),m.definition),2) AS [definitionHex],
  DATALENGTH(m.definition) AS [byteLength],
  CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) AS [hash],
  JSON_QUERY((SELECT p.parameter_id AS [id],p.name,
   p.system_type_id AS [systemType],p.user_type_id AS [userType],p.is_output AS [output]
   FROM sys.parameters p WHERE p.object_id=o.object_id
   ORDER BY p.parameter_id FOR JSON PATH,INCLUDE_NULL_VALUES)) AS [parameters],
  JSON_QUERY((SELECT d.referenced_server_name AS [serverName],
   d.referenced_database_name AS [databaseName],d.referenced_schema_name AS [schemaName],
   d.referenced_entity_name AS [entityName],d.referenced_minor_id AS [minorId],
   d.referenced_class_desc AS [classDescription],d.is_schema_bound_reference AS [bound],
   d.is_caller_dependent AS [callerDependent],d.is_ambiguous AS [ambiguous]
   FROM sys.sql_expression_dependencies d WHERE d.referencing_id=o.object_id
   FOR JSON PATH,INCLUDE_NULL_VALUES)) AS [dependencies]
  FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
  JOIN sys.sql_modules m ON m.object_id=o.object_id
  WHERE o.object_id IN(OBJECT_ID(N'sec.fn_FilterByTenant'),OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal'))
  ORDER BY o.name FOR JSON PATH,INCLUDE_NULL_VALUES)) AS [functions],
 JSON_QUERY((SELECT
  JSON_QUERY(N'["'+STRING_ESCAPE(s.name,'json')+N'","'+STRING_ESCAPE(p.name,'json')+N'"]') AS [name],
  p.is_enabled AS [enabled],p.is_schema_bound AS [bound]
  FROM sys.security_policies p JOIN sys.schemas s ON s.schema_id=p.schema_id
  ORDER BY s.name,p.name FOR JSON PATH)) AS [policies],
 JSON_QUERY((SELECT
  JSON_QUERY(N'["'+STRING_ESCAPE(ps.name,'json')+N'","'+STRING_ESCAPE(pol.name,'json')+N'"]') AS [policy],
  JSON_QUERY(N'["'+STRING_ESCAPE(ts.name,'json')+N'","'+STRING_ESCAPE(t.name,'json')+N'"]') AS [target],
  p.predicate_type_desc AS [kind],p.operation_desc AS [operation],p.predicate_definition AS [definition],
  p.object_id AS [policyId],p.target_object_id AS [targetId],p.security_predicate_id AS [predicateId]
  FROM sys.security_predicates p
  JOIN sys.security_policies pol ON pol.object_id=p.object_id
  JOIN sys.schemas ps ON ps.schema_id=pol.schema_id
  JOIN sys.objects t ON t.object_id=p.target_object_id
  JOIN sys.schemas ts ON ts.schema_id=t.schema_id
  ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH,INCLUDE_NULL_VALUES)) AS [predicates],
 JSON_QUERY((SELECT
  JSON_QUERY(N'["'+STRING_ESCAPE(s.name,'json')+N'","'+STRING_ESCAPE(o.name,'json')+N'"]') AS [name],
  USER_NAME(s.principal_id) AS [schemaOwner],
  USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS [owner]
  FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
  WHERE o.object_id IN(OBJECT_ID(N'sec.fn_FilterByTenant'),OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal'),
   OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
   OR o.object_id IN(SELECT object_id FROM sys.security_policies)
   OR o.object_id IN(SELECT target_object_id FROM sys.security_predicates)
  ORDER BY s.name,o.name FOR JSON PATH,INCLUDE_NULL_VALUES)) AS [owners],
 JSON_QUERY((SELECT OBJECT_NAME(c.object_id) AS [target],c.system_type_id AS [systemType],
  c.user_type_id AS [userType],c.is_computed AS [computed]
  FROM sys.columns c
  WHERE c.object_id IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
   AND c.name COLLATE Latin1_General_100_BIN2=N'ShaleClientId'
  ORDER BY target FOR JSON PATH)) AS [columns],
 @Other AS [otherJson],CONVERT(varchar(64),HASHBYTES('SHA2_256',@Other),2) AS [otherHash]
 FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER);
/* Bounded ASCII hex lines avoid SQLCMD/SSMS long-cell truncation and preserve
   exact nvarchar bytes, whitespace, NULLs and ID-bearing inventory JSON. */
DECLARE @Bytes varbinary(max)=CONVERT(varbinary(max),@Evidence);
;WITH Chunks AS (
 SELECT 1 AS ChunkNumber
 UNION ALL SELECT ChunkNumber+1 FROM Chunks
 WHERE CONVERT(bigint,ChunkNumber)*1024<DATALENGTH(@Bytes)
)
SELECT CONVERT(varchar(2048),SUBSTRING(@Bytes,(ChunkNumber-1)*1024+1,1024),2) AS [EvidenceHex]
FROM Chunks ORDER BY ChunkNumber OPTION(MAXRECURSION 0);

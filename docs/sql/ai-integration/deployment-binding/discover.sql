:on error exit
:r docs/sql/ai-integration/deployment-binding/session.sql
/* READ ONLY. No inputs needed; absence/visibility/error is never readiness. */
SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) AS LogicalServer,
 DB_NAME() AS DatabaseName, USER_NAME() AS OperatorName,
 SYSDATETIMEOFFSET() AS ObservedSqlClock, SYSUTCDATETIME() AS ObservedUtcClock;
SELECT CASE WHEN OBJECT_ID(N'dbo.AiCaseDataDeployment',N'U') IS NULL
 THEN 'MISSING_TABLE_STOP' ELSE 'TABLE_PRESENT_REVIEW_CATALOG_AND_ROWS' END AS DiscoveryStatus;
SELECT c.column_id,c.name,TYPE_NAME(c.user_type_id) AS TypeName,c.max_length,c.is_nullable,
 c.collation_name,c.is_identity,c.is_computed
 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.AiCaseDataDeployment') ORDER BY c.column_id;
SELECT definition,is_disabled,is_not_trusted FROM sys.check_constraints
 WHERE parent_object_id=OBJECT_ID(N'dbo.AiCaseDataDeployment');
SELECT i.name,i.is_primary_key,i.is_unique,i.is_disabled,COL_NAME(ic.object_id,ic.column_id) AS KeyColumn
 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
 WHERE i.object_id=OBJECT_ID(N'dbo.AiCaseDataDeployment');
SELECT t.name,t.is_disabled,m.definition FROM sys.triggers t
 LEFT JOIN sys.sql_modules m ON m.object_id=t.object_id
 WHERE t.parent_id=OBJECT_ID(N'dbo.AiCaseDataDeployment');
SELECT name,CONVERT(varchar(64),CONVERT(varbinary(32),value),2) AS ReceiptDigest
 FROM sys.extended_properties WHERE class=1 AND minor_id=0
 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation')
 AND name IN(N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source');
IF OBJECT_ID(N'dbo.AiCaseDataDeployment',N'U') IS NOT NULL
 EXEC(N'SELECT COUNT_BIG(*) AS BindingCount FROM dbo.AiCaseDataDeployment;
 SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment;');
/* Available SQL mapping names and current offsets are informational, not mapping proof. */
SELECT name,current_utc_offset,is_currently_dst FROM sys.time_zone_info ORDER BY name;

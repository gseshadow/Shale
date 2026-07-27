/* Read-only preflight/verification. Run with a principal subject to Shale RLS. */
SET NOCOUNT ON;
SELECT c.column_id,c.name,TYPE_NAME(c.user_type_id) AS DataType,c.max_length,c.is_nullable,dc.definition AS DefaultDefinition
FROM sys.columns c LEFT JOIN sys.default_constraints dc ON dc.parent_object_id=c.object_id AND dc.parent_column_id=c.column_id
WHERE c.object_id=OBJECT_ID(N'dbo.Notifications') ORDER BY c.column_id;
SELECT i.name,i.is_unique,i.filter_definition
FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.Notifications') ORDER BY i.name;
SELECT fk.name AS ForeignKeyName,OBJECT_NAME(fkc.referenced_object_id) AS ReferencedTable,
       COL_NAME(fkc.parent_object_id,fkc.parent_column_id) AS ParentColumn,
       COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id) AS ReferencedColumn
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
WHERE fk.parent_object_id=OBJECT_ID(N'dbo.Notifications');
SELECT SCHEMA_NAME(sp.schema_id)+N'.'+sp.name AS SecurityPolicy,p.predicate_type_desc,p.predicate_definition,sp.is_enabled
FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
WHERE p.target_object_id=OBJECT_ID(N'dbo.Notifications');
SELECT ShaleClientId,UserId,EventKey,COUNT_BIG(*) AS DuplicateCount
FROM dbo.Notifications WHERE EventKey IS NOT NULL
GROUP BY ShaleClientId,UserId,EventKey HAVING COUNT_BIG(*)>1;

EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7;
SELECT N'tenant-7-visible' AS CheckName,COUNT_BIG(*) AS VisibleRows,
       SUM(CASE WHEN ShaleClientId<>7 THEN 1 ELSE 0 END) AS CrossTenantRows FROM dbo.Notifications;
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8;
SELECT N'tenant-8-visible' AS CheckName,COUNT_BIG(*) AS VisibleRows,
       SUM(CASE WHEN ShaleClientId<>8 THEN 1 ELSE 0 END) AS CrossTenantRows FROM dbo.Notifications;
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=NULL;

/* dbo-only metadata inventories; no definitions, hashes, SIDs or application rows. */
SELECT principal_id,name,type,is_fixed_role INTO #CutoverPrincipals FROM sys.database_principals WHERE type IN ('S','E','X','R');
SELECT object_id,QUOTENAME(OBJECT_SCHEMA_NAME(object_id))+N'.'+QUOTENAME(name) AS Securable INTO #CutoverObjects FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','FN','IF','TF');
SELECT QUOTENAME(OBJECT_SCHEMA_NAME(object_id))+N'.'+QUOTENAME(OBJECT_NAME(object_id)) AS Securable,name AS ColumnName
 INTO #CutoverColumns FROM sys.columns WHERE object_id IN (SELECT object_id FROM #CutoverObjects);
-- Snapshot ownership and WITH GRANT OPTION as dbo: catalog filtering is not absence evidence.
SELECT principal_id,QUOTENAME(name) AS Securable INTO #CutoverOwnership FROM sys.schemas
UNION ALL SELECT principal_id,QUOTENAME(OBJECT_SCHEMA_NAME(object_id))+N'.'+QUOTENAME(name)
 FROM sys.objects WHERE principal_id IS NOT NULL;
SELECT p.grantee_principal_id,p.permission_name,
 CONVERT(nvarchar(776),CONCAT(N'class=',p.class,N';major=',p.major_id,N';minor=',p.minor_id,
 N';name=',CASE p.class WHEN 0 THEN QUOTENAME(DB_NAME())
 WHEN 1 THEN QUOTENAME(OBJECT_SCHEMA_NAME(p.major_id))+N'.'+QUOTENAME(OBJECT_NAME(p.major_id))
 +CASE WHEN p.minor_id=0 THEN N'' ELSE N'.'+QUOTENAME(COL_NAME(p.major_id,p.minor_id)) END
 WHEN 3 THEN QUOTENAME(SCHEMA_NAME(p.major_id))
 WHEN 4 THEN QUOTENAME(USER_NAME(p.major_id)) ELSE N'(catalog IDs)' END,
 N';grantee=',USER_NAME(p.grantee_principal_id),N';grantor=',USER_NAME(p.grantor_principal_id))) AS Securable
 INTO #CutoverGrantOptions FROM sys.database_permissions p WHERE state='W';
GO
SELECT grantee_principal_id,permission_name,state_desc INTO #CutoverVisibility
 FROM sys.database_permissions WHERE class=0 AND permission_name=N'VIEW DEFINITION';
GO

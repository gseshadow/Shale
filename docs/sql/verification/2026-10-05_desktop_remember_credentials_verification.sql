/*
 Read-only remembered-sign-in verification. Run three times:
   1. as dbo (or a metadata-review principal) to inspect the complete schema and constraints;
   2. using the API SHALE_APP_DB_USER credentials for the identity-lookup rows;
   3. using the API SHALE_RT_DB_USER credentials for tenant-scoped create/rotate/delete rows.
 The matching non-dbo run is authoritative for each EffectivePermission; dbo is not permission evidence.
*/
SET NOCOUNT ON;

SELECT USER_NAME() AS InspectionPrincipal,
       ORIGINAL_LOGIN() AS InspectionLogin,
       TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId')) AS SessionTenant,
       TRY_CONVERT(int, SESSION_CONTEXT(N'PrincipalUserId')) AS SessionActor;

SELECT CASE WHEN OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NULL THEN 1 ELSE 0 END AS FindingCount;

SELECT c.name AS ColumnName,
       TYPE_NAME(c.user_type_id) AS SqlType,
       c.max_length AS MaxLengthBytes,
       c.precision AS NumericPrecision,
       c.scale AS NumericScale,
       c.is_nullable AS IsNullable,
       dc.definition AS DefaultDefinition
FROM sys.columns c
LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
WHERE c.object_id=OBJECT_ID(N'dbo.DesktopRememberCredentials')
ORDER BY c.column_id;

SELECT kc.name AS ConstraintName,
       kc.type_desc AS ConstraintType,
       i.is_unique AS IsUnique,
       STRING_AGG(QUOTENAME(c.name),N',') WITHIN GROUP (ORDER BY ic.key_ordinal) AS KeyColumns
FROM sys.key_constraints kc
JOIN sys.indexes i ON i.object_id=kc.parent_object_id AND i.index_id=kc.unique_index_id
JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE kc.parent_object_id=OBJECT_ID(N'dbo.DesktopRememberCredentials')
GROUP BY kc.name,kc.type_desc,i.is_unique
ORDER BY kc.name;

SELECT fk.name AS ForeignKeyName,
       OBJECT_SCHEMA_NAME(fk.referenced_object_id)+N'.'+OBJECT_NAME(fk.referenced_object_id) AS ReferencedTable,
       fk.delete_referential_action_desc AS DeleteAction,
       fk.is_disabled AS IsDisabled,
       fk.is_not_trusted AS IsNotTrusted,
       STRING_AGG(QUOTENAME(pc.name)+N' -> '+QUOTENAME(rc.name),N',')
           WITHIN GROUP (ORDER BY fkc.constraint_column_id) AS ColumnMapping
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
WHERE fk.parent_object_id=OBJECT_ID(N'dbo.DesktopRememberCredentials')
GROUP BY fk.name,fk.referenced_object_id,fk.delete_referential_action_desc,fk.is_disabled,fk.is_not_trusted
ORDER BY fk.name;

SELECT p.name AS PolicyName,
       p.is_enabled AS PolicyEnabled,
       sp.predicate_type_desc AS PredicateType,
       sp.operation_desc AS PredicateOperation,
       sp.predicate_definition AS PredicateDefinition
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id=sp.object_id
WHERE sp.target_object_id IN (OBJECT_ID(N'dbo.DesktopRememberCredentials'),OBJECT_ID(N'dbo.UserSessions'))
ORDER BY sp.target_object_id,sp.predicate_type_desc,sp.operation_desc;

SELECT required.RequiredPool,
       required.ObjectName,
       required.PermissionName,
       HAS_PERMS_BY_NAME(required.ObjectName,N'OBJECT',required.PermissionName) AS EffectivePermission
FROM (VALUES
 (N'SHALE_APP_DB_USER',N'dbo.DesktopRememberCredentials',N'SELECT'),
 (N'SHALE_APP_DB_USER',N'dbo.Users',N'SELECT'),
 (N'SHALE_RT_DB_USER',N'dbo.DesktopRememberCredentials',N'SELECT'),
 (N'SHALE_RT_DB_USER',N'dbo.DesktopRememberCredentials',N'INSERT'),
 (N'SHALE_RT_DB_USER',N'dbo.DesktopRememberCredentials',N'UPDATE'),
 (N'SHALE_RT_DB_USER',N'dbo.DesktopRememberCredentials',N'DELETE'),
 (N'SHALE_RT_DB_USER',N'dbo.UserSessions',N'SELECT'),
 (N'SHALE_RT_DB_USER',N'dbo.UserSessions',N'UPDATE'),
 (N'SHALE_RT_DB_USER',N'dbo.Users',N'SELECT')
) required(RequiredPool,ObjectName,PermissionName)
ORDER BY required.RequiredPool,required.ObjectName,required.PermissionName;

IF OBJECT_ID(N'dbo.DesktopRememberCredentials',N'U') IS NOT NULL
BEGIN
 SELECT COUNT_BIG(*) AS ExpiredCredentialCount
 FROM dbo.DesktopRememberCredentials
 WHERE AbsoluteExpiresAt<=SYSUTCDATETIME();

 SELECT COUNT_BIG(*) AS OrphanedOrRevokedCredentialCount
 FROM dbo.DesktopRememberCredentials r
 LEFT JOIN dbo.UserSessions s
   ON s.SessionId=r.SessionId AND s.ShaleClientId=r.ShaleClientId AND s.UserId=r.UserId
 WHERE s.SessionId IS NULL OR s.RevokedAt IS NOT NULL;
END;
GO

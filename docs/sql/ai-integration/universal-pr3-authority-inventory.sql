/* Read-only review inventory. No table data, raw module bodies, credentials or binding values.
   Requires independently complete metadata; never infer absence from permission-filtered catalogs.
   Keep instance-specific principal metadata private; do not commit production output. */
SET NOCOUNT ON;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59310,'Complete authority inventory visibility required.',1;
SELECT s.name AS SchemaName,o.name AS ObjectName,o.type_desc AS ObjectType,
 USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS EffectiveOwner,m.execute_as_principal_id AS ExecuteAsPrincipalId,
 m.uses_ansi_nulls AS UsesAnsiNulls,m.uses_quoted_identifier AS UsesQuotedIdentifier,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2) AS ModuleDefinitionHash
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
WHERE o.is_ms_shipped=0 ORDER BY s.name,o.name;
SELECT OBJECT_SCHEMA_NAME(d.referencing_id) AS ReferencingSchema,OBJECT_NAME(d.referencing_id) AS ReferencingObject,
 d.referenced_class_desc AS ReferencedClass,d.referenced_server_name AS ReferencedServer,d.referenced_database_name AS ReferencedDatabase,
 d.referenced_schema_name AS ReferencedSchema,d.referenced_entity_name AS ReferencedObject,
 COL_NAME(d.referenced_id,d.referenced_minor_id) AS ReferencedColumn,d.is_caller_dependent AS CallerDependent,d.is_ambiguous AS Ambiguous
FROM sys.sql_expression_dependencies d ORDER BY d.referencing_id,d.referenced_class,d.referenced_id,d.referenced_minor_id;
SELECT OBJECT_SCHEMA_NAME(t.parent_id) AS ParentSchema,OBJECT_NAME(t.parent_id) AS ParentObject,t.name AS TriggerName,t.parent_class_desc AS TriggerScope,
 t.is_disabled AS Disabled,t.is_instead_of_trigger AS InsteadOf,e.type_desc AS TriggerEvent,e.is_first AS FirstTrigger,e.is_last AS LastTrigger
FROM sys.triggers t LEFT JOIN sys.trigger_events e ON e.object_id=t.object_id ORDER BY t.parent_id,t.name,e.type;
SELECT s.name AS PolicySchema,p.name AS PolicyName,USER_NAME(COALESCE(p.principal_id,s.principal_id)) AS PolicyOwner,
 p.is_enabled AS Enabled,p.is_schema_bound AS SchemaBound,OBJECT_SCHEMA_NAME(q.target_object_id) AS TargetSchema,
 OBJECT_NAME(q.target_object_id) AS TargetTable,q.predicate_type_desc AS PredicateType,q.operation_desc AS Operation,q.predicate_definition AS PredicateDefinition
FROM sys.security_policies p JOIN sys.schemas s ON s.schema_id=p.schema_id JOIN sys.security_predicates q ON q.object_id=p.object_id
ORDER BY q.target_object_id,q.predicate_type,q.operation;
SELECT OBJECT_SCHEMA_NAME(f.parent_object_id) AS ParentSchema,OBJECT_NAME(f.parent_object_id) AS ParentTable,f.name AS ConstraintName,
 COL_NAME(k.parent_object_id,k.parent_column_id) AS ParentColumn,OBJECT_SCHEMA_NAME(f.referenced_object_id) AS ReferencedSchema,
 OBJECT_NAME(f.referenced_object_id) AS ReferencedTable,COL_NAME(k.referenced_object_id,k.referenced_column_id) AS ReferencedColumn,
 f.is_disabled AS Disabled,f.is_not_trusted AS Untrusted,f.delete_referential_action_desc AS DeleteAction,f.update_referential_action_desc AS UpdateAction
FROM sys.foreign_keys f JOIN sys.foreign_key_columns k ON k.constraint_object_id=f.object_id ORDER BY f.parent_object_id,f.name,k.constraint_column_id;
SELECT p.name AS PrincipalName,p.type_desc AS PrincipalType,p.authentication_type_desc AS AuthenticationType,
 USER_NAME(p.owning_principal_id) AS PrincipalOwner,p.is_fixed_role AS FixedRole
FROM sys.database_principals p WHERE p.principal_id>4 ORDER BY p.name;
SELECT r.name AS RoleName,m.name AS MemberName,m.type_desc AS MemberType,m.authentication_type_desc AS AuthenticationType
FROM sys.database_role_members x JOIN sys.database_principals r ON r.principal_id=x.role_principal_id JOIN sys.database_principals m ON m.principal_id=x.member_principal_id ORDER BY r.name,m.name;
SELECT USER_NAME(p.grantee_principal_id) AS Grantee,USER_NAME(p.grantor_principal_id) AS Grantor,p.class_desc AS PermissionClass,
 p.permission_name AS PermissionName,p.state_desc AS PermissionState,OBJECT_SCHEMA_NAME(CASE WHEN p.class=1 THEN p.major_id END) AS ObjectSchema,
 CASE p.class WHEN 1 THEN OBJECT_NAME(p.major_id) WHEN 3 THEN SCHEMA_NAME(p.major_id) END AS SecurableName,
 CASE WHEN p.class=1 AND p.minor_id>0 THEN COL_NAME(p.major_id,p.minor_id) END AS ColumnName
FROM sys.database_permissions p ORDER BY p.grantee_principal_id,p.class,p.major_id,p.minor_id,p.permission_name;
SELECT OBJECT_SCHEMA_NAME(c.major_id) AS ModuleSchema,OBJECT_NAME(c.major_id) AS ModuleName,c.crypt_type_desc AS SignatureType FROM sys.crypt_properties c ORDER BY c.major_id,c.crypt_type_desc;
SELECT SchemaName=SCHEMA_NAME(s.schema_id),OwnerName=USER_NAME(s.principal_id) FROM sys.schemas s ORDER BY s.name;
SELECT CONVERT(int,SERVERPROPERTY('EngineEdition')) AS EngineEdition,is_trustworthy_on AS Trustworthy,is_db_chaining_on AS CrossDatabaseChaining FROM sys.databases WHERE database_id=DB_ID();

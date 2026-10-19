-- Run only through harness.ps1, after the connection/empty-database guards.
SELECT p.name AS PackageName, o.name AS ObjectName, o.object_type AS ObjectType,
       o.capabilities AS ObjectCapabilities, o.description AS ObjectDescription,
       c.column_id AS ColumnId, c.name AS ColumnName, c.column_type AS ColumnType,
       c.type_name AS TypeName, c.column_value AS DefaultValue,
       c.capabilities AS ColumnCapabilities, c.description AS ColumnDescription
FROM sys.dm_xe_objects AS o
JOIN sys.dm_xe_packages AS p ON p.guid=o.package_guid
LEFT JOIN sys.dm_xe_object_columns AS c
  ON c.object_package_guid=o.package_guid AND c.object_name=o.name
WHERE (p.name=N'sqlserver' AND o.name IN (N'module_start',N'module_end',N'database_id'))
   OR (p.name=N'package0' AND o.name IN (N'ring_buffer',N'event_counter'))
ORDER BY p.name,o.name,o.object_type,c.column_id;

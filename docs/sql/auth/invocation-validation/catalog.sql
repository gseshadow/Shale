-- Stable exact ownership fingerprint, including unrelated additions inside the task schema.
SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),(
 SELECT s.schema_id AS SchemaId,s.principal_id AS OwnerId,
  (SELECT CONVERT(nvarchar(128),value) AS OwnerToken FROM sys.extended_properties
   WHERE class=3 AND major_id=s.schema_id AND name=N'InvocationValidationOwner' FOR XML PATH('marker'),TYPE),
  (SELECT o.object_id,o.name,o.type,o.create_date,o.modify_date,m.definition,
    m.uses_ansi_nulls,m.uses_quoted_identifier,m.execute_as_principal_id
   FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
   WHERE o.schema_id=s.schema_id ORDER BY o.object_id FOR XML PATH('object'),TYPE),
  (SELECT p.* FROM sys.database_permissions p WHERE (p.class=3 AND p.major_id=s.schema_id)
    OR (p.class=1 AND p.major_id IN (SELECT object_id FROM sys.objects WHERE schema_id=s.schema_id))
   ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.grantor_principal_id,p.type
   FOR XML PATH('permission'),TYPE)
 FROM sys.schemas s WHERE s.name IN (N'{{schema}}',N'{{schema}}_decoy') ORDER BY s.schema_id FOR XML PATH('schema'),TYPE
))),2) AS CatalogHash;

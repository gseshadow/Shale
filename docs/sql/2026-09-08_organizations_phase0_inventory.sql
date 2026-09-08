/*
  Shale Organizations redesign — Phase 0 read-only inventory

  Purpose:
    Capture the live schema, tenancy, relationship, and legacy-data facts needed
    before designing the Organization Types and structured contact-point migrations.

  Safety:
    - Read-only: no DDL, DML, transactions, or session-context mutation.
    - Safe to run with the current tenant session or an approved administrative session.
    - Result sets intentionally avoid returning organization names, addresses, phone
      numbers, email addresses, websites, notes, or other business data.
*/

SET NOCOUNT ON;

SELECT
    DB_NAME() AS DatabaseName,
    USER_NAME() AS DatabasePrincipal,
    TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId')) AS SessionShaleClientId,
    SYSUTCDATETIME() AS CapturedAtUtc;

SELECT
    s.name AS SchemaName,
    t.name AS TableName,
    c.column_id AS ColumnOrder,
    c.name AS ColumnName,
    ty.name AS DataType,
    c.max_length AS MaxLengthBytes,
    c.precision AS NumericPrecision,
    c.scale AS NumericScale,
    c.is_nullable AS IsNullable,
    c.is_identity AS IsIdentity,
    c.is_computed AS IsComputed,
    dc.name AS DefaultConstraintName,
    dc.definition AS DefaultDefinition
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
LEFT JOIN sys.default_constraints dc
  ON dc.parent_object_id = c.object_id
 AND dc.parent_column_id = c.column_id
WHERE s.name = N'dbo'
  AND t.name IN (N'Organizations', N'OrganizationTypes', N'Contacts', N'CaseParties', N'CaseOrganizations')
ORDER BY t.name, c.column_id;

SELECT
    OBJECT_SCHEMA_NAME(i.object_id) AS SchemaName,
    OBJECT_NAME(i.object_id) AS TableName,
    i.name AS IndexName,
    i.is_unique AS IsUnique,
    i.is_primary_key AS IsPrimaryKey,
    i.type_desc AS IndexType,
    i.filter_definition AS FilterDefinition,
    STRING_AGG(CONVERT(nvarchar(max),
        QUOTENAME(c.name) + CASE WHEN ic.is_descending_key = 1 THEN N' DESC' ELSE N' ASC' END), N', ')
        WITHIN GROUP (ORDER BY ic.key_ordinal) AS KeyColumns
FROM sys.indexes i
JOIN sys.index_columns ic
  ON ic.object_id = i.object_id
 AND ic.index_id = i.index_id
 AND ic.key_ordinal > 0
JOIN sys.columns c
  ON c.object_id = ic.object_id
 AND c.column_id = ic.column_id
WHERE OBJECT_SCHEMA_NAME(i.object_id) = N'dbo'
  AND OBJECT_NAME(i.object_id) IN (N'Organizations', N'OrganizationTypes', N'Contacts', N'CaseParties', N'CaseOrganizations')
GROUP BY i.object_id, i.name, i.is_unique, i.is_primary_key, i.type_desc, i.filter_definition
ORDER BY TableName, IndexName;

SELECT
    OBJECT_SCHEMA_NAME(fk.parent_object_id) AS ParentSchema,
    OBJECT_NAME(fk.parent_object_id) AS ParentTable,
    fk.name AS ForeignKeyName,
    STRING_AGG(CONVERT(nvarchar(max), pc.name), N', ')
        WITHIN GROUP (ORDER BY fkc.constraint_column_id) AS ParentColumns,
    OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS ReferencedSchema,
    OBJECT_NAME(fk.referenced_object_id) AS ReferencedTable,
    STRING_AGG(CONVERT(nvarchar(max), rc.name), N', ')
        WITHIN GROUP (ORDER BY fkc.constraint_column_id) AS ReferencedColumns,
    fk.delete_referential_action_desc AS DeleteAction,
    fk.update_referential_action_desc AS UpdateAction,
    fk.is_disabled AS IsDisabled,
    fk.is_not_trusted AS IsNotTrusted
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns pc
  ON pc.object_id = fkc.parent_object_id
 AND pc.column_id = fkc.parent_column_id
JOIN sys.columns rc
  ON rc.object_id = fkc.referenced_object_id
 AND rc.column_id = fkc.referenced_column_id
WHERE OBJECT_SCHEMA_NAME(fk.parent_object_id) = N'dbo'
  AND OBJECT_NAME(fk.parent_object_id) IN (N'Organizations', N'OrganizationTypes', N'Contacts', N'CaseParties', N'CaseOrganizations')
GROUP BY fk.parent_object_id, fk.referenced_object_id, fk.name,
         fk.delete_referential_action_desc, fk.update_referential_action_desc,
         fk.is_disabled, fk.is_not_trusted
ORDER BY ParentTable, ForeignKeyName;

SELECT
    OBJECT_SCHEMA_NAME(cc.parent_object_id) AS SchemaName,
    OBJECT_NAME(cc.parent_object_id) AS TableName,
    cc.name AS CheckConstraintName,
    cc.definition AS CheckDefinition,
    cc.is_disabled AS IsDisabled,
    cc.is_not_trusted AS IsNotTrusted
FROM sys.check_constraints cc
WHERE OBJECT_SCHEMA_NAME(cc.parent_object_id) = N'dbo'
  AND OBJECT_NAME(cc.parent_object_id) IN (N'Organizations', N'OrganizationTypes', N'Contacts', N'CaseParties', N'CaseOrganizations')
ORDER BY TableName, CheckConstraintName;

SELECT
    sp.name AS PolicyName,
    sp.is_enabled AS PolicyEnabled,
    spr.predicate_type_desc AS PredicateType,
    spr.operation_desc AS Operation,
    OBJECT_SCHEMA_NAME(spr.target_object_id) AS TargetSchema,
    OBJECT_NAME(spr.target_object_id) AS TargetTable,
    spr.predicate_definition AS PredicateDefinition
FROM sys.security_predicates spr
JOIN sys.security_policies sp ON sp.object_id = spr.object_id
WHERE OBJECT_SCHEMA_NAME(spr.target_object_id) = N'dbo'
  AND OBJECT_NAME(spr.target_object_id) IN (N'Organizations', N'OrganizationTypes', N'Contacts', N'CaseParties', N'CaseOrganizations')
ORDER BY TargetTable, PolicyName, PredicateType;

SELECT
    o.ShaleClientId,
    COUNT_BIG(*) AS OrganizationCount,
    SUM(CASE WHEN ISNULL(o.IsDeleted, 0) = 0 THEN 1 ELSE 0 END) AS ActiveCount,
    SUM(CASE WHEN ISNULL(o.IsDeleted, 0) = 1 THEN 1 ELSE 0 END) AS DeletedCount,
    SUM(CASE WHEN o.OrganizationTypeId IS NULL THEN 1 ELSE 0 END) AS MissingLegacyTypeCount,
    SUM(CASE WHEN NULLIF(LTRIM(RTRIM(o.Name)), N'') IS NULL THEN 1 ELSE 0 END) AS MissingNameCount,
    SUM(CASE WHEN NULLIF(LTRIM(RTRIM(o.Phone)), N'') IS NOT NULL THEN 1 ELSE 0 END) AS PhonePopulatedCount,
    SUM(CASE WHEN NULLIF(LTRIM(RTRIM(o.Fax)), N'') IS NOT NULL THEN 1 ELSE 0 END) AS FaxPopulatedCount,
    SUM(CASE WHEN NULLIF(LTRIM(RTRIM(o.Email)), N'') IS NOT NULL THEN 1 ELSE 0 END) AS EmailPopulatedCount,
    SUM(CASE WHEN NULLIF(LTRIM(RTRIM(o.Website)), N'') IS NOT NULL THEN 1 ELSE 0 END) AS WebsitePopulatedCount,
    SUM(CASE WHEN COALESCE(NULLIF(LTRIM(RTRIM(o.Address1)), N''),
                           NULLIF(LTRIM(RTRIM(o.Address2)), N''),
                           NULLIF(LTRIM(RTRIM(o.City)), N''),
                           NULLIF(LTRIM(RTRIM(o.State)), N''),
                           NULLIF(LTRIM(RTRIM(o.PostalCode)), N''),
                           NULLIF(LTRIM(RTRIM(o.Country)), N'')) IS NOT NULL THEN 1 ELSE 0 END) AS AddressPopulatedCount
FROM dbo.Organizations o
GROUP BY o.ShaleClientId
ORDER BY o.ShaleClientId;

SELECT
    ot.OrganizationTypeId,
    ot.Name AS OrganizationTypeName,
    o.ShaleClientId,
    COUNT_BIG(o.Id) AS ReferencingOrganizations,
    SUM(CASE WHEN o.Id IS NOT NULL AND ISNULL(o.IsDeleted, 0) = 0 THEN 1 ELSE 0 END) AS ActiveReferencingOrganizations
FROM dbo.OrganizationTypes ot
LEFT JOIN dbo.Organizations o ON o.OrganizationTypeId = ot.OrganizationTypeId
GROUP BY ot.OrganizationTypeId, ot.Name, o.ShaleClientId
ORDER BY ot.OrganizationTypeId, o.ShaleClientId;

SELECT
    N'Organizations with a missing OrganizationTypes row' AS CheckName,
    COUNT_BIG(*) AS ViolationCount
FROM dbo.Organizations o
LEFT JOIN dbo.OrganizationTypes ot ON ot.OrganizationTypeId = o.OrganizationTypeId
WHERE o.OrganizationTypeId IS NOT NULL
  AND ot.OrganizationTypeId IS NULL
UNION ALL
SELECT
    N'Active normalized duplicate organization names within a tenant',
    COALESCE(SUM(x.DuplicateRows), 0)
FROM (
    SELECT COUNT_BIG(*) - 1 AS DuplicateRows
    FROM dbo.Organizations o
    WHERE ISNULL(o.IsDeleted, 0) = 0
      AND NULLIF(LTRIM(RTRIM(o.Name)), N'') IS NOT NULL
    GROUP BY o.ShaleClientId, LOWER(LTRIM(RTRIM(o.Name)))
    HAVING COUNT_BIG(*) > 1
) x;

SELECT
    c.ShaleClientId,
    COUNT_BIG(*) AS ContactsWithOrganizationId,
    SUM(CASE WHEN o.Id IS NULL THEN 1 ELSE 0 END) AS MissingOrganizationCount,
    SUM(CASE WHEN o.Id IS NOT NULL AND o.ShaleClientId <> c.ShaleClientId THEN 1 ELSE 0 END) AS CrossTenantCount,
    SUM(CASE WHEN o.Id IS NOT NULL AND ISNULL(o.IsDeleted, 0) = 1 THEN 1 ELSE 0 END) AS DeletedOrganizationCount
FROM dbo.Contacts c
LEFT JOIN dbo.Organizations o ON o.Id = c.OrganizationId
WHERE c.OrganizationId IS NOT NULL
GROUP BY c.ShaleClientId
ORDER BY c.ShaleClientId;

SELECT
    c.ShaleClientId,
    COUNT_BIG(*) AS CasePartyOrganizationLinks,
    SUM(CASE WHEN o.Id IS NULL THEN 1 ELSE 0 END) AS MissingOrganizationCount,
    SUM(CASE WHEN o.Id IS NOT NULL AND o.ShaleClientId <> c.ShaleClientId THEN 1 ELSE 0 END) AS CrossTenantCount,
    SUM(CASE WHEN o.Id IS NOT NULL AND ISNULL(o.IsDeleted, 0) = 1 THEN 1 ELSE 0 END) AS DeletedOrganizationCount
FROM dbo.CaseParties cp
JOIN dbo.Cases c ON c.Id = cp.CaseId
LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
WHERE cp.OrganizationId IS NOT NULL
GROUP BY c.ShaleClientId
ORDER BY c.ShaleClientId;

IF OBJECT_ID(N'dbo.CaseOrganizations', N'U') IS NOT NULL
BEGIN
    EXEC sys.sp_executesql N'
        SELECT
            c.ShaleClientId,
            COUNT_BIG(*) AS LegacyCaseOrganizationLinks,
            SUM(CASE WHEN o.Id IS NULL THEN 1 ELSE 0 END) AS MissingOrganizationCount,
            SUM(CASE WHEN o.Id IS NOT NULL AND o.ShaleClientId <> c.ShaleClientId THEN 1 ELSE 0 END) AS CrossTenantCount
        FROM dbo.CaseOrganizations co
        JOIN dbo.Cases c ON c.Id = co.CaseId
        LEFT JOIN dbo.Organizations o ON o.Id = co.OrganizationId
        GROUP BY c.ShaleClientId
        ORDER BY c.ShaleClientId;';
END
ELSE
BEGIN
    SELECT N'dbo.CaseOrganizations does not exist.' AS LegacyCaseOrganizationsStatus;
END;

SELECT
    OBJECT_SCHEMA_NAME(d.referencing_id) AS ReferencingSchema,
    OBJECT_NAME(d.referencing_id) AS ReferencingObject,
    o.type_desc AS ReferencingObjectType,
    d.referenced_entity_name AS ReferencedEntity,
    d.referenced_minor_name AS ReferencedColumn
FROM sys.sql_expression_dependencies d
JOIN sys.objects o ON o.object_id = d.referencing_id
WHERE d.referenced_schema_name = N'dbo'
  AND d.referenced_entity_name IN (N'Organizations', N'OrganizationTypes', N'CaseOrganizations')
ORDER BY ReferencedEntity, ReferencingSchema, ReferencingObject, ReferencedColumn;

SELECT
    N'Phase 0 inventory complete. Preserve every result set with the target database name and capture timestamp.' AS InventoryStatus;

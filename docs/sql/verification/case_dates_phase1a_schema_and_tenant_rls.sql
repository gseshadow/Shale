/* Read-only verification for Case Dates foundation phase 1A. */
SET NOCOUNT ON;

SELECT TableName=OBJECT_SCHEMA_NAME(c.object_id)+N'.'+OBJECT_NAME(c.object_id), c.column_id, ColumnName=c.name, TypeName=t.name, c.max_length, c.precision, c.scale, c.is_nullable
FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
WHERE c.object_id IN (OBJECT_ID(N'dbo.CaseDateTypes'), OBJECT_ID(N'dbo.CaseDates'))
ORDER BY TableName, c.column_id;

SELECT TableName=OBJECT_SCHEMA_NAME(parent_object_id)+N'.'+OBJECT_NAME(parent_object_id), ConstraintName=name, ConstraintType=type_desc, Definition=OBJECT_DEFINITION(object_id)
FROM sys.objects
WHERE parent_object_id IN (OBJECT_ID(N'dbo.CaseDateTypes'), OBJECT_ID(N'dbo.CaseDates')) AND type IN (N'C',N'F',N'PK')
ORDER BY TableName, ConstraintType, ConstraintName;

SELECT FKName=fk.name, ParentTable=OBJECT_SCHEMA_NAME(fk.parent_object_id)+N'.'+OBJECT_NAME(fk.parent_object_id), ReferencedTable=OBJECT_SCHEMA_NAME(fk.referenced_object_id)+N'.'+OBJECT_NAME(fk.referenced_object_id), ParentColumn=pc.name, ReferencedColumn=rc.name
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
WHERE fk.parent_object_id IN (OBJECT_ID(N'dbo.CaseDateTypes'), OBJECT_ID(N'dbo.CaseDates'))
ORDER BY FKName, fkc.constraint_column_id;

SELECT TableName=OBJECT_SCHEMA_NAME(i.object_id)+N'.'+OBJECT_NAME(i.object_id), IndexName=i.name, i.is_unique, i.has_filter, i.filter_definition, KeyColumns=STRING_AGG(CASE WHEN ic.is_included_column=0 THEN c.name END, N',') WITHIN GROUP (ORDER BY ic.key_ordinal), IncludedColumns=STRING_AGG(CASE WHEN ic.is_included_column=1 THEN c.name END, N',')
FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id IN (OBJECT_ID(N'dbo.CaseDateTypes'), OBJECT_ID(N'dbo.CaseDates'), OBJECT_ID(N'dbo.Cases')) AND (i.name LIKE N'%CaseDate%' OR i.name=N'UX_Cases_ShaleClientId_Id')
GROUP BY i.object_id,i.name,i.is_unique,i.has_filter,i.filter_definition
ORDER BY TableName, IndexName;

SELECT PolicySchema=SCHEMA_NAME(sp.schema_id), PolicyName=sp.name, TargetTable=OBJECT_SCHEMA_NAME(p.target_object_id)+N'.'+OBJECT_NAME(p.target_object_id), p.predicate_type_desc, p.operation_desc, p.predicate_definition
FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
WHERE p.target_object_id IN (OBJECT_ID(N'dbo.CaseDateTypes'), OBJECT_ID(N'dbo.CaseDates'), OBJECT_ID(N'dbo.CalendarEventTypes'), OBJECT_ID(N'dbo.CalendarEvents'))
ORDER BY TargetTable;

SELECT SystemKey, Name, CalendarCategory, Color, SupportsTime, SortOrder, IsActive, IsDeleted
FROM dbo.CaseDateTypes
WHERE ShaleClientId IS NULL
ORDER BY SortOrder, SystemKey;

SELECT ScopeLabel=CASE WHEN ShaleClientId IS NULL THEN N'GLOBAL' ELSE CONVERT(nvarchar(32),ShaleClientId) END, SystemKey, DuplicateCount=COUNT(*)
FROM dbo.CaseDateTypes
WHERE SystemKey IS NOT NULL
GROUP BY ShaleClientId, SystemKey
HAVING COUNT(*) > 1;

SELECT cd.Id, cd.ShaleClientId, cd.CaseId, c.ShaleClientId AS CaseTenant, cd.CaseDateTypeId, cdt.ShaleClientId AS TypeTenant
FROM dbo.CaseDates cd
LEFT JOIN dbo.Cases c ON c.Id=cd.CaseId
LEFT JOIN dbo.CaseDateTypes cdt ON cdt.Id=cd.CaseDateTypeId
WHERE c.Id IS NULL OR c.ShaleClientId <> cd.ShaleClientId OR cdt.Id IS NULL OR (cdt.ShaleClientId IS NOT NULL AND cdt.ShaleClientId <> cd.ShaleClientId);

SELECT InvalidRangeCount=COUNT(*) FROM dbo.CaseDates WHERE EndsAt IS NOT NULL AND StartsAt > EndsAt;
SELECT InvalidTypeRemovalCount=COUNT(*) FROM dbo.CaseDateTypes WHERE (IsDeleted=0 AND (DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) OR (IsDeleted=1 AND (DeletedAt IS NULL OR DeletedByUserId IS NULL OR IsActive<>0));
SELECT InvalidDateRemovalCount=COUNT(*) FROM dbo.CaseDates WHERE (IsDeleted=0 AND (DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL)) OR (IsDeleted=1 AND (DeletedAt IS NULL OR DeletedByUserId IS NULL));
SELECT ShaleClientId, RowCount=COUNT(*) FROM dbo.CaseDates GROUP BY ShaleClientId ORDER BY ShaleClientId;

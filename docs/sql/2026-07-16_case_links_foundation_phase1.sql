/*
  Case Links foundation phase 1.

  Adds:
    - dbo.LinkTypes: global/tenant overlay lookup table.
    - dbo.ExternalLinks: tenant-owned reusable external hyperlink records.
    - dbo.CaseLinks: tenant-owned associations between cases and external links.

  Safety:
    - Additive and rerunnable where practical.
    - Fails fast if required base tables, live RLS objects, or existing table
      contracts are missing/incompatible.
    - Extends the existing live RLS policy/predicate architecture only.
    - Does not apply to Azure automatically; operators must run this manually.

  Verified live-RLS assumptions are intentionally not encoded as guesses. The
  migration requires the existing sec.fn_FilterByTenant predicate and a single
  enabled TenantFilter policy. LinkTypes always uses the explicit overlay
  predicate sec.fn_FilterByTenantOrGlobal.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ============================================================================
   READ-ONLY PREFLIGHT: base schema and live RLS inventory
   ============================================================================ */

SELECT
    RequiredObject = v.ObjectName,
    ObjectExists = CONVERT(bit, CASE
        WHEN v.ObjectType = N'U' AND OBJECT_ID(v.ObjectName, N'U') IS NOT NULL THEN 1
        WHEN v.ObjectType = N'IF' AND OBJECT_ID(v.ObjectName, N'IF') IS NOT NULL THEN 1
        WHEN v.ObjectType = N'SECURITY_POLICY' AND EXISTS (SELECT 1 FROM sys.security_policies WHERE name = v.ObjectName) THEN 1
        ELSE 0 END)
FROM (VALUES
    (N'dbo.ShaleClients', N'U'),
    (N'dbo.Cases', N'U'),
    (N'dbo.Users', N'U'),
    (N'sec.fn_FilterByTenant', N'IF'),
    (N'sec.fn_FilterByTenantOrGlobal', N'IF'),
    (N'TenantFilter', N'SECURITY_POLICY')
) AS v(ObjectName, ObjectType);

SELECT
    PolicySchema = SCHEMA_NAME(sp.schema_id),
    PolicyName = sp.name,
    PolicyEnabled = sp.is_enabled,
    TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
    PredicateType = p.predicate_type_desc,
    Operation = p.operation_desc,
    PredicateDefinition = p.predicate_definition
FROM sys.security_predicates AS p
JOIN sys.security_policies AS sp
  ON sp.object_id = p.object_id
WHERE sp.name = N'TenantFilter'
ORDER BY PolicySchema, PolicyName, TargetTable, PredicateType, Operation;
GO

/* ============================================================================
   MIGRATION
   ============================================================================ */

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.ShaleClients', N'U') IS NULL
        THROW 54000, 'Required table dbo.ShaleClients is missing.', 1;

    IF OBJECT_ID(N'dbo.Cases', N'U') IS NULL
        THROW 54001, 'Required table dbo.Cases is missing.', 1;

    IF COL_LENGTH(N'dbo.Cases', N'ShaleClientId') IS NULL
        THROW 54002, 'Required tenant column dbo.Cases.ShaleClientId is missing.', 1;

    IF OBJECT_ID(N'dbo.Users', N'U') IS NULL
        THROW 54003, 'Required table dbo.Users is missing for actor audit foreign keys.', 1;

    IF SCHEMA_ID(N'sec') IS NULL
        THROW 54004, 'Required schema sec is missing. Stop: do not create a competing RLS system.', 1;

    IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL
        THROW 54005, 'Required strict predicate sec.fn_FilterByTenant is missing. Stop and investigate live RLS.', 1;

    IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal') IS NOT NULL
       AND OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL
        THROW 54006, 'sec.fn_FilterByTenantOrGlobal exists but is not an inline table-valued function. Stop for manual review.', 1;

    IF OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL
    BEGIN
        EXEC(N'
CREATE FUNCTION sec.fn_FilterByTenantOrGlobal(@ShaleClientId int)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS fn_accessResult
    WHERE @ShaleClientId IS NULL
       OR @ShaleClientId = TRY_CONVERT(int, SESSION_CONTEXT(N''''ShaleClientId''''));');
    END
    ELSE IF LOWER(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF'))) NOT LIKE N'%@shaleclientid is null%'
         OR LOWER(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF'))) NOT LIKE N'%session_context%shaleclientid%'
    BEGIN
        THROW 54007, 'Existing sec.fn_FilterByTenantOrGlobal does not match expected overlay semantics. Stop for manual review; migration will not replace it automatically.', 1;
    END;

    DECLARE @PolicyCount int;
    SELECT @PolicyCount = COUNT(*)
    FROM sys.security_policies
    WHERE name = N'TenantFilter';

    IF @PolicyCount = 0
        THROW 54008, 'Required security policy TenantFilter is missing. Stop and investigate live RLS.', 1;

    IF @PolicyCount > 1
        THROW 54009, 'Multiple security policies named TenantFilter exist. Stop: policy schema/name is ambiguous.', 1;

    DECLARE @PolicySchemaName sysname,
            @PolicyName sysname,
            @PolicyQualified nvarchar(517),
            @PolicyObjectId int,
            @PolicyEnabled bit;

    SELECT
        @PolicySchemaName = SCHEMA_NAME(schema_id),
        @PolicyName = name,
        @PolicyObjectId = object_id,
        @PolicyEnabled = is_enabled
    FROM sys.security_policies
    WHERE name = N'TenantFilter';

    IF @PolicyEnabled = 0
        THROW 54010, 'Security policy TenantFilter is disabled. Stop: migration will not silently enable it.', 1;

    SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName);

    IF OBJECT_ID(N'dbo.LinkTypes', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.LinkTypes (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_LinkTypes PRIMARY KEY,
            ShaleClientId int NULL,
            Name nvarchar(100) NOT NULL,
            Color nvarchar(20) NULL,
            IsActive bit NOT NULL CONSTRAINT DF_LinkTypes_IsActive DEFAULT (1),
            IsDeleted bit NOT NULL CONSTRAINT DF_LinkTypes_IsDeleted DEFAULT (0),
            SystemKey nvarchar(64) NULL,
            CreatedByUserId int NULL,
            UpdatedByUserId int NULL,
            CreatedAt datetime2 NOT NULL CONSTRAINT DF_LinkTypes_CreatedAt DEFAULT (SYSUTCDATETIME()),
            UpdatedAt datetime2 NULL,
            RowVer rowversion NOT NULL
        );
    END;

    IF OBJECT_ID(N'dbo.ExternalLinks', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.ExternalLinks (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_ExternalLinks PRIMARY KEY,
            ShaleClientId int NOT NULL,
            LinkTypeId int NOT NULL,
            DisplayName nvarchar(255) NOT NULL,
            Url nvarchar(2048) NOT NULL,
            Description nvarchar(max) NULL,
            IsDeleted bit NOT NULL CONSTRAINT DF_ExternalLinks_IsDeleted DEFAULT (0),
            DeletedAt datetime2 NULL,
            DeletedByUserId int NULL,
            CreatedByUserId int NULL,
            UpdatedByUserId int NULL,
            CreatedAt datetime2 NOT NULL CONSTRAINT DF_ExternalLinks_CreatedAt DEFAULT (SYSUTCDATETIME()),
            UpdatedAt datetime2 NULL,
            RowVer rowversion NOT NULL
        );
    END;

    IF OBJECT_ID(N'dbo.CaseLinks', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.CaseLinks (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseLinks PRIMARY KEY,
            ShaleClientId int NOT NULL,
            CaseId int NOT NULL,
            ExternalLinkId int NOT NULL,
            IsPrimary bit NOT NULL CONSTRAINT DF_CaseLinks_IsPrimary DEFAULT (0),
            Notes nvarchar(2000) NULL,
            SortOrder int NOT NULL CONSTRAINT DF_CaseLinks_SortOrder DEFAULT (0),
            IsDeleted bit NOT NULL CONSTRAINT DF_CaseLinks_IsDeleted DEFAULT (0),
            DeletedAt datetime2 NULL,
            DeletedByUserId int NULL,
            CreatedByUserId int NULL,
            UpdatedByUserId int NULL,
            CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseLinks_CreatedAt DEFAULT (SYSUTCDATETIME()),
            UpdatedAt datetime2 NULL,
            RowVer rowversion NOT NULL
        );
    END;

    DECLARE @RequiredColumns TABLE (
        TableName sysname NOT NULL,
        ColumnName sysname NOT NULL,
        TypeName sysname NOT NULL,
        MaxLength smallint NOT NULL,
        IsNullable bit NOT NULL
    );

    INSERT INTO @RequiredColumns (TableName, ColumnName, TypeName, MaxLength, IsNullable) VALUES
    (N'LinkTypes', N'Id', N'int', 4, 0),
    (N'LinkTypes', N'ShaleClientId', N'int', 4, 1),
    (N'LinkTypes', N'Name', N'nvarchar', 200, 0),
    (N'LinkTypes', N'Color', N'nvarchar', 40, 1),
    (N'LinkTypes', N'IsActive', N'bit', 1, 0),
    (N'LinkTypes', N'IsDeleted', N'bit', 1, 0),
    (N'LinkTypes', N'SystemKey', N'nvarchar', 128, 1),
    (N'LinkTypes', N'CreatedByUserId', N'int', 4, 1),
    (N'LinkTypes', N'UpdatedByUserId', N'int', 4, 1),
    (N'LinkTypes', N'CreatedAt', N'datetime2', 8, 0),
    (N'LinkTypes', N'UpdatedAt', N'datetime2', 8, 1),
    (N'LinkTypes', N'RowVer', N'timestamp', 8, 0),
    (N'ExternalLinks', N'Id', N'int', 4, 0),
    (N'ExternalLinks', N'ShaleClientId', N'int', 4, 0),
    (N'ExternalLinks', N'LinkTypeId', N'int', 4, 0),
    (N'ExternalLinks', N'DisplayName', N'nvarchar', 510, 0),
    (N'ExternalLinks', N'Url', N'nvarchar', 4096, 0),
    (N'ExternalLinks', N'Description', N'nvarchar', -1, 1),
    (N'ExternalLinks', N'IsDeleted', N'bit', 1, 0),
    (N'ExternalLinks', N'DeletedAt', N'datetime2', 8, 1),
    (N'ExternalLinks', N'DeletedByUserId', N'int', 4, 1),
    (N'ExternalLinks', N'CreatedByUserId', N'int', 4, 1),
    (N'ExternalLinks', N'UpdatedByUserId', N'int', 4, 1),
    (N'ExternalLinks', N'CreatedAt', N'datetime2', 8, 0),
    (N'ExternalLinks', N'UpdatedAt', N'datetime2', 8, 1),
    (N'ExternalLinks', N'RowVer', N'timestamp', 8, 0),
    (N'CaseLinks', N'Id', N'int', 4, 0),
    (N'CaseLinks', N'ShaleClientId', N'int', 4, 0),
    (N'CaseLinks', N'CaseId', N'int', 4, 0),
    (N'CaseLinks', N'ExternalLinkId', N'int', 4, 0),
    (N'CaseLinks', N'IsPrimary', N'bit', 1, 0),
    (N'CaseLinks', N'Notes', N'nvarchar', 4000, 1),
    (N'CaseLinks', N'SortOrder', N'int', 4, 0),
    (N'CaseLinks', N'IsDeleted', N'bit', 1, 0),
    (N'CaseLinks', N'DeletedAt', N'datetime2', 8, 1),
    (N'CaseLinks', N'DeletedByUserId', N'int', 4, 1),
    (N'CaseLinks', N'CreatedByUserId', N'int', 4, 1),
    (N'CaseLinks', N'UpdatedByUserId', N'int', 4, 1),
    (N'CaseLinks', N'CreatedAt', N'datetime2', 8, 0),
    (N'CaseLinks', N'UpdatedAt', N'datetime2', 8, 1),
    (N'CaseLinks', N'RowVer', N'timestamp', 8, 0);

    IF EXISTS (
        SELECT 1
        FROM @RequiredColumns rc
        LEFT JOIN sys.tables t ON t.name = rc.TableName AND SCHEMA_NAME(t.schema_id) = N'dbo'
        LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = rc.ColumnName
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        WHERE c.object_id IS NULL
           OR ty.name <> rc.TypeName
           OR c.max_length <> rc.MaxLength
           OR c.is_nullable <> rc.IsNullable
    )
    BEGIN
        SELECT
            rc.TableName,
            rc.ColumnName,
            ExpectedType = rc.TypeName,
            ActualType = ty.name,
            ExpectedMaxLength = rc.MaxLength,
            ActualMaxLength = c.max_length,
            ExpectedNullable = rc.IsNullable,
            ActualNullable = c.is_nullable
        FROM @RequiredColumns rc
        LEFT JOIN sys.tables t ON t.name = rc.TableName AND SCHEMA_NAME(t.schema_id) = N'dbo'
        LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = rc.ColumnName
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        WHERE c.object_id IS NULL
           OR ty.name <> rc.TypeName
           OR c.max_length <> rc.MaxLength
           OR c.is_nullable <> rc.IsNullable;

        THROW 54011, 'Existing Case Links foundation table is missing required columns or has incompatible column definitions.', 1;
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.LinkTypes') AND name = N'UX_LinkTypes_ShaleClientId_SystemKey_NonNull')
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX UX_LinkTypes_ShaleClientId_SystemKey_NonNull
            ON dbo.LinkTypes (ShaleClientId, SystemKey)
            WHERE SystemKey IS NOT NULL;
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.ExternalLinks') AND name = N'IX_ExternalLinks_ShaleClientId_LinkTypeId')
        CREATE NONCLUSTERED INDEX IX_ExternalLinks_ShaleClientId_LinkTypeId ON dbo.ExternalLinks (ShaleClientId, LinkTypeId) INCLUDE (DisplayName, IsDeleted);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseLinks') AND name = N'IX_CaseLinks_ShaleClientId_CaseId')
        CREATE NONCLUSTERED INDEX IX_CaseLinks_ShaleClientId_CaseId ON dbo.CaseLinks (ShaleClientId, CaseId, SortOrder, Id) INCLUDE (ExternalLinkId, IsPrimary, IsDeleted);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseLinks') AND name = N'UX_CaseLinks_CaseId_ExternalLinkId_Active')
        CREATE UNIQUE NONCLUSTERED INDEX UX_CaseLinks_CaseId_ExternalLinkId_Active ON dbo.CaseLinks (ShaleClientId, CaseId, ExternalLinkId) WHERE IsDeleted = 0;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseLinks') AND name = N'UX_CaseLinks_CaseId_Primary_Active')
        CREATE UNIQUE NONCLUSTERED INDEX UX_CaseLinks_CaseId_Primary_Active ON dbo.CaseLinks (ShaleClientId, CaseId) WHERE IsDeleted = 0 AND IsPrimary = 1;

    IF OBJECT_ID(N'dbo.FK_LinkTypes_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.LinkTypes ADD CONSTRAINT FK_LinkTypes_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);

    IF OBJECT_ID(N'dbo.FK_LinkTypes_CreatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.LinkTypes ADD CONSTRAINT FK_LinkTypes_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_LinkTypes_UpdatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.LinkTypes ADD CONSTRAINT FK_LinkTypes_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_LinkTypeId_LinkTypes', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_LinkTypeId_LinkTypes FOREIGN KEY (LinkTypeId) REFERENCES dbo.LinkTypes (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_DeletedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_CreatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_UpdatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_CaseId_Cases', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_CaseId_Cases FOREIGN KEY (CaseId) REFERENCES dbo.Cases (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_ExternalLinkId_ExternalLinks', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_ExternalLinkId_ExternalLinks FOREIGN KEY (ExternalLinkId) REFERENCES dbo.ExternalLinks (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_DeletedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_CreatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_UpdatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users (Id);

    INSERT INTO dbo.LinkTypes (ShaleClientId, SystemKey, Name, Color, IsActive, IsDeleted, CreatedByUserId, UpdatedByUserId)
    SELECT NULL, v.SystemKey, v.Name, v.Color, 1, 0, NULL, NULL
    FROM (VALUES
        (N'court_docket', N'Court Docket', N'#2563EB'),
        (N'claims_portal', N'Claims Portal', N'#7C3AED'),
        (N'medical_records_portal', N'Medical Records Portal', N'#0891B2'),
        (N'insurance_portal', N'Insurance Portal', N'#0F766E'),
        (N'document_repository', N'Document Repository', N'#475569'),
        (N'government_record', N'Government Record', N'#B45309'),
        (N'research', N'Research', N'#9333EA'),
        (N'other', N'Other', N'#64748B')
    ) AS v(SystemKey, Name, Color)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.LinkTypes lt
        WHERE lt.ShaleClientId IS NULL AND lt.SystemKey = v.SystemKey
    );

    DECLARE @Sql nvarchar(max);

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.object_id = @PolicyObjectId AND p.target_object_id = OBJECT_ID(N'dbo.LinkTypes') AND p.predicate_type_desc = N'FILTER'
    )
    BEGIN
        SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.LinkTypes;';
        EXEC sys.sp_executesql @Sql;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.object_id = @PolicyObjectId AND p.target_object_id = OBJECT_ID(N'dbo.ExternalLinks') AND p.predicate_type_desc = N'FILTER'
    )
    BEGIN
        SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ExternalLinks;';
        EXEC sys.sp_executesql @Sql;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.object_id = @PolicyObjectId AND p.target_object_id = OBJECT_ID(N'dbo.CaseLinks') AND p.predicate_type_desc = N'FILTER'
    )
    BEGIN
        SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinks;';
        EXEC sys.sp_executesql @Sql;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* ============================================================================
   POST-MIGRATION VERIFICATION
   ============================================================================ */

SELECT t.name AS TableName, c.name AS ColumnName, ty.name AS TypeName, c.max_length, c.is_nullable
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE SCHEMA_NAME(t.schema_id) = N'dbo'
  AND t.name IN (N'LinkTypes', N'ExternalLinks', N'CaseLinks')
ORDER BY t.name, c.column_id;

SELECT OBJECT_NAME(i.object_id) AS TableName, i.name, i.is_unique, i.has_filter, i.filter_definition
FROM sys.indexes i
WHERE i.object_id IN (OBJECT_ID(N'dbo.LinkTypes'), OBJECT_ID(N'dbo.ExternalLinks'), OBJECT_ID(N'dbo.CaseLinks'))
ORDER BY TableName, i.name;

SELECT sp.name AS PolicyName, OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id) AS TargetTable,
       p.predicate_type_desc, p.operation_desc, p.predicate_definition
FROM sys.security_predicates p
JOIN sys.security_policies sp ON sp.object_id = p.object_id
WHERE p.target_object_id IN (OBJECT_ID(N'dbo.LinkTypes'), OBJECT_ID(N'dbo.ExternalLinks'), OBJECT_ID(N'dbo.CaseLinks'))
ORDER BY TargetTable;

PRINT 'WARNING: tenant 7 / tenant 8 verification must run from a connection where SESSION_CONTEXT(N''ShaleClientId'') was not previously marked read_only.';

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 7;
SELECT N'Tenant 7 visible LinkTypes by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.LinkTypes GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 7 visible ExternalLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.ExternalLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 7 visible CaseLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.CaseLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 7 should see zero tenant 8 LinkTypes' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.LinkTypes WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 ExternalLinks' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.ExternalLinks WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 CaseLinks' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.CaseLinks WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see global LinkTypes' AS CheckName, COUNT(*) AS GlobalLinkTypeCount FROM dbo.LinkTypes WHERE ShaleClientId IS NULL;
SELECT N'Tenant 7 should see own custom LinkTypes' AS CheckName, COUNT(*) AS TenantLinkTypeCount FROM dbo.LinkTypes WHERE ShaleClientId = 7;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 8;
SELECT N'Tenant 8 visible LinkTypes by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.LinkTypes GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 8 visible ExternalLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.ExternalLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 8 visible CaseLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.CaseLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 8 should see zero tenant 7 LinkTypes' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.LinkTypes WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 ExternalLinks' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.ExternalLinks WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 CaseLinks' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.CaseLinks WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see global LinkTypes' AS CheckName, COUNT(*) AS GlobalLinkTypeCount FROM dbo.LinkTypes WHERE ShaleClientId IS NULL;
SELECT N'Tenant 8 should see own custom LinkTypes' AS CheckName, COUNT(*) AS TenantLinkTypeCount FROM dbo.LinkTypes WHERE ShaleClientId = 8;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
GO

/*
Rollback guidance (manual, non-destructive default):
  1. If the script fails, the transaction rolls back DDL/data changes in the
     migration block.
  2. If rollback is required after commit and no application data has been
     written, remove TenantFilter predicates for dbo.CaseLinks,
     dbo.ExternalLinks, and dbo.LinkTypes, then drop dbo.CaseLinks,
     dbo.ExternalLinks, and dbo.LinkTypes in that dependency order.
  3. Do not drop sec.fn_FilterByTenantOrGlobal if other migrations now depend on
     it. Inspect sys.security_predicates first.

Service-layer validation still required:
  - SQL Server cannot enforce ExternalLinks.LinkTypeId tenant compatibility with
    a declarative FK because dbo.LinkTypes intentionally permits global rows
    (ShaleClientId IS NULL) plus tenant rows. Services must only allow a tenant
    ExternalLink to use a LinkType whose ShaleClientId is NULL or matches the
    ExternalLink tenant.
  - Services must keep CaseLinks.ShaleClientId equal to both the linked Case and
    ExternalLink tenants; single-column legacy PKs on dbo.Cases and
    dbo.ExternalLinks prevent a verified composite FK in this phase.
*/

/*
  Case Links foundation phase 1.

  Adds:
    - dbo.LinkTypes: global/tenant overlay lookup table.
    - dbo.ExternalLinks: tenant-owned reusable external hyperlink records.
    - dbo.CaseLinks: tenant-owned associations between cases and external links.

  Safety:
    - Additive and rerunnable where practical.
    - Fails fast if required base tables or live RLS objects are missing.
    - Extends the existing live RLS policy/predicate architecture only.
    - Does not apply to Azure automatically; operators must run this manually.

  Verified live-RLS assumptions are intentionally not encoded as guesses. The
  migration requires the existing sec.fn_FilterByTenant predicate and
  TenantFilter policy that current proposed RLS coverage scripts identify as
  the live pattern. If those objects do not exist in an environment, stop and
  inspect live RLS before applying this migration.
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
    (N'sec.fn_FilterByTenant', N'IF'),
    (N'TenantFilter', N'SECURITY_POLICY')
) AS v(ObjectName, ObjectType);

SELECT
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
ORDER BY TargetTable, PredicateType, Operation;
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

    IF SCHEMA_ID(N'sec') IS NULL
        THROW 54003, 'Required schema sec is missing. Stop: do not create a competing RLS system.', 1;

    IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL
        THROW 54004, 'Required predicate sec.fn_FilterByTenant is missing. Stop and investigate live RLS.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = N'TenantFilter')
        THROW 54005, 'Required security policy TenantFilter is missing. Stop and investigate live RLS.', 1;

    DECLARE @BasePredicateAllowsNull bit = CONVERT(bit, CASE
        WHEN LOWER(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant', N'IF'))) LIKE N'%is null%'
        THEN 1 ELSE 0 END);

    IF @BasePredicateAllowsNull = 0 AND OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal', N'IF') IS NULL
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
    END;

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
            CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseLinks_CreatedAt DEFAULT (SYSUTCDATETIME()),
            UpdatedAt datetime2 NULL,
            RowVer rowversion NOT NULL
        );
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

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);

    IF OBJECT_ID(N'dbo.FK_ExternalLinks_LinkTypeId_LinkTypes', N'F') IS NULL
        ALTER TABLE dbo.ExternalLinks ADD CONSTRAINT FK_ExternalLinks_LinkTypeId_LinkTypes FOREIGN KEY (LinkTypeId) REFERENCES dbo.LinkTypes (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_CaseId_Cases', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_CaseId_Cases FOREIGN KEY (CaseId) REFERENCES dbo.Cases (Id);

    IF OBJECT_ID(N'dbo.FK_CaseLinks_ExternalLinkId_ExternalLinks', N'F') IS NULL
        ALTER TABLE dbo.CaseLinks ADD CONSTRAINT FK_CaseLinks_ExternalLinkId_ExternalLinks FOREIGN KEY (ExternalLinkId) REFERENCES dbo.ExternalLinks (Id);

    INSERT INTO dbo.LinkTypes (ShaleClientId, SystemKey, Name, Color, IsActive, IsDeleted)
    SELECT NULL, v.SystemKey, v.Name, v.Color, 1, 0
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

    DECLARE @OverlayPredicate sysname = CASE WHEN @BasePredicateAllowsNull = 1 THEN N'fn_FilterByTenant' ELSE N'fn_FilterByTenantOrGlobal' END;
    DECLARE @Sql nvarchar(max);

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.name = N'TenantFilter' AND p.target_object_id = OBJECT_ID(N'dbo.LinkTypes') AND p.predicate_type_desc = N'FILTER'
    )
    BEGIN
        SET @Sql = N'ALTER SECURITY POLICY TenantFilter ADD FILTER PREDICATE sec.' + QUOTENAME(@OverlayPredicate) + N'(ShaleClientId) ON dbo.LinkTypes;';
        EXEC sys.sp_executesql @Sql;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.name = N'TenantFilter' AND p.target_object_id = OBJECT_ID(N'dbo.ExternalLinks') AND p.predicate_type_desc = N'FILTER'
    )
        ALTER SECURITY POLICY TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ExternalLinks;

    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
        WHERE sp.name = N'TenantFilter' AND p.target_object_id = OBJECT_ID(N'dbo.CaseLinks') AND p.predicate_type_desc = N'FILTER'
    )
        ALTER SECURITY POLICY TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinks;

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

-- Tenant 7 / tenant 8 read verification. Run after inserting tenant-specific
-- sample rows in a non-production environment or against existing safe data.
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 7;
SELECT N'Tenant 7 LinkTypes by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.LinkTypes GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 7 ExternalLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.ExternalLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 7 CaseLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.CaseLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 8;
SELECT N'Tenant 8 LinkTypes by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.LinkTypes GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 8 ExternalLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.ExternalLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
SELECT N'Tenant 8 CaseLinks by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount FROM dbo.CaseLinks GROUP BY ShaleClientId ORDER BY ShaleClientId;
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

/*
  Case Links foundation phase 5.2: CaseLinkShares.

  Adds dbo.CaseLinkShares as a strict tenant-owned table recording Shale's
  knowledge that a case-specific Case Link was shared with a Contact. This is
  database/schema/documentation/testing only; Java service operations and UI are
  intentionally deferred.

  Safety:
    - Additive, transaction-wrapped, and rerunnable where practical.
    - Fails fast if required base tables, primary keys, tenant columns, live RLS
      objects, or an existing CaseLinkShares table are missing/incompatible.
    - Extends the existing enabled TenantFilter policy with sec.fn_FilterByTenant.
    - Does not create a second security policy, alter predicate functions, or
      modify live data outside dbo.CaseLinkShares DDL.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ============================================================================
   READ-ONLY PREFLIGHT: base schema, PKs, composite keys, and live RLS inventory
   ============================================================================ */

SELECT RequiredObject = v.ObjectName,
       ObjectExists = CONVERT(bit, CASE
           WHEN v.ObjectType = N'U' AND OBJECT_ID(v.ObjectName, N'U') IS NOT NULL THEN 1
           WHEN v.ObjectType = N'IF' AND OBJECT_ID(v.ObjectName, N'IF') IS NOT NULL THEN 1
           WHEN v.ObjectType = N'SECURITY_POLICY' AND EXISTS (SELECT 1 FROM sys.security_policies WHERE name = v.ObjectName) THEN 1
           ELSE 0 END)
FROM (VALUES
    (N'dbo.ShaleClients', N'U'),
    (N'dbo.CaseLinks', N'U'),
    (N'dbo.Contacts', N'U'),
    (N'dbo.Users', N'U'),
    (N'sec.fn_FilterByTenant', N'IF'),
    (N'TenantFilter', N'SECURITY_POLICY'),
    (N'dbo.CaseLinkShares', N'U')
) AS v(ObjectName, ObjectType);

SELECT TableName = OBJECT_SCHEMA_NAME(t.object_id) + N'.' + t.name,
       ColumnName = c.name,
       TypeName = ty.name,
       c.max_length,
       c.precision,
       c.scale,
       c.is_nullable,
       c.is_identity
FROM sys.tables t
JOIN sys.indexes i ON i.object_id = t.object_id AND i.is_primary_key = 1
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.object_id IN (OBJECT_ID(N'dbo.ShaleClients'), OBJECT_ID(N'dbo.CaseLinks'), OBJECT_ID(N'dbo.Contacts'), OBJECT_ID(N'dbo.Users'))
ORDER BY TableName, ic.key_ordinal;

SELECT TableName = OBJECT_SCHEMA_NAME(i.object_id) + N'.' + OBJECT_NAME(i.object_id),
       i.name, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition,
       KeyColumns = STRING_AGG(c.name, N',') WITHIN GROUP (ORDER BY ic.key_ordinal)
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id IN (OBJECT_ID(N'dbo.CaseLinks'), OBJECT_ID(N'dbo.Contacts'))
  AND i.is_unique = 1
GROUP BY i.object_id, i.name, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition
ORDER BY TableName, i.name;

SELECT PolicySchema = SCHEMA_NAME(sp.schema_id), sp.name AS PolicyName, sp.is_enabled AS PolicyEnabled,
       TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id),
       p.predicate_type_desc, p.operation_desc, p.predicate_definition
FROM sys.security_predicates p
JOIN sys.security_policies sp ON sp.object_id = p.object_id
WHERE sp.name = N'TenantFilter'
  AND (p.target_object_id IN (OBJECT_ID(N'dbo.CaseLinks'), OBJECT_ID(N'dbo.Contacts'), OBJECT_ID(N'dbo.CaseLinkShares'))
       OR OBJECT_NAME(p.target_object_id) IN (N'CaseLinks', N'Contacts', N'CaseLinkShares'))
ORDER BY PolicySchema, PolicyName, TargetTable;
GO

/* ============================================================================
   MIGRATION
   ============================================================================ */
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.ShaleClients', N'U') IS NULL THROW 54500, 'Required table dbo.ShaleClients is missing.', 1;
    IF OBJECT_ID(N'dbo.CaseLinks', N'U') IS NULL THROW 54501, 'Required table dbo.CaseLinks is missing. Apply Case Links foundation phase 1 first.', 1;
    IF OBJECT_ID(N'dbo.Contacts', N'U') IS NULL THROW 54502, 'Required table dbo.Contacts is missing.', 1;
    IF OBJECT_ID(N'dbo.Users', N'U') IS NULL THROW 54503, 'Required table dbo.Users is missing for actor audit foreign keys.', 1;
    IF SCHEMA_ID(N'sec') IS NULL THROW 54504, 'Required schema sec is missing. Stop: do not create a competing RLS system.', 1;
    IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL THROW 54505, 'Required strict predicate sec.fn_FilterByTenant is missing. Stop and investigate live RLS.', 1;

    IF COL_LENGTH(N'dbo.CaseLinks', N'ShaleClientId') IS NULL THROW 54506, 'Required tenant column dbo.CaseLinks.ShaleClientId is missing.', 1;
    IF COL_LENGTH(N'dbo.Contacts', N'ShaleClientId') IS NULL THROW 54507, 'Required tenant column dbo.Contacts.ShaleClientId is missing.', 1;
    IF COL_LENGTH(N'dbo.Users', N'ShaleClientId') IS NULL THROW 54508, 'Required tenant column dbo.Users.ShaleClientId is missing.', 1;

    DECLARE @PolicyCount int;
    SELECT @PolicyCount = COUNT(*) FROM sys.security_policies WHERE name = N'TenantFilter';
    IF @PolicyCount = 0 THROW 54509, 'Required security policy TenantFilter is missing. Stop and investigate live RLS.', 1;
    IF @PolicyCount > 1 THROW 54510, 'Multiple security policies named TenantFilter exist. Stop: policy schema/name is ambiguous.', 1;

    DECLARE @PolicySchemaName sysname, @PolicyName sysname, @PolicyQualified nvarchar(517), @PolicyObjectId int, @PolicyEnabled bit;
    SELECT @PolicySchemaName = SCHEMA_NAME(schema_id), @PolicyName = name, @PolicyObjectId = object_id, @PolicyEnabled = is_enabled
    FROM sys.security_policies WHERE name = N'TenantFilter';
    IF @PolicyEnabled = 0 THROW 54511, 'Security policy TenantFilter is disabled. Stop: migration will not silently enable it.', 1;
    SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName);

    DECLARE @RequiredPk TABLE (TableName sysname NOT NULL, ColumnName sysname NOT NULL, TypeName sysname NOT NULL, MaxLength smallint NOT NULL);
    INSERT INTO @RequiredPk VALUES
        (N'ShaleClients', N'Id', N'int', 4),
        (N'CaseLinks', N'Id', N'int', 4),
        (N'Contacts', N'Id', N'int', 4),
        (N'Users', N'Id', N'int', 4);

    IF EXISTS (
        SELECT 1
        FROM @RequiredPk rp
        LEFT JOIN sys.tables t ON t.name = rp.TableName AND SCHEMA_NAME(t.schema_id) = N'dbo'
        LEFT JOIN sys.indexes i ON i.object_id = t.object_id AND i.is_primary_key = 1
        LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        WHERE c.name <> rp.ColumnName OR ty.name <> rp.TypeName OR c.max_length <> rp.MaxLength
           OR EXISTS (SELECT 1 FROM sys.index_columns ic2 WHERE ic2.object_id = i.object_id AND ic2.index_id = i.index_id AND ic2.key_ordinal > 1)
    )
    BEGIN
        SELECT rp.TableName, ExpectedPrimaryKeyColumn = rp.ColumnName, ExpectedType = rp.TypeName,
               ActualColumn = c.name, ActualType = ty.name, ActualMaxLength = c.max_length
        FROM @RequiredPk rp
        LEFT JOIN sys.tables t ON t.name = rp.TableName AND SCHEMA_NAME(t.schema_id) = N'dbo'
        LEFT JOIN sys.indexes i ON i.object_id = t.object_id AND i.is_primary_key = 1
        LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id;
        THROW 54512, 'Required base table primary-key contract is missing or incompatible for CaseLinkShares foreign keys.', 1;
    END;

    IF OBJECT_ID(N'dbo.CaseLinkShares') IS NOT NULL AND OBJECT_ID(N'dbo.CaseLinkShares', N'U') IS NULL
        THROW 54513, 'dbo.CaseLinkShares exists but is not a user table. Stop for manual review.', 1;

    IF OBJECT_ID(N'dbo.CaseLinkShares', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.CaseLinkShares (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CaseLinkShares PRIMARY KEY,
            ShaleClientId int NOT NULL,
            CaseLinkId int NOT NULL,
            ContactId int NOT NULL,
            SharedAt datetime2 NOT NULL CONSTRAINT DF_CaseLinkShares_SharedAt DEFAULT (SYSUTCDATETIME()),
            Notes nvarchar(1000) NULL,
            IsDeleted bit NOT NULL CONSTRAINT DF_CaseLinkShares_IsDeleted DEFAULT (0),
            DeletedAt datetime2 NULL,
            DeletedByUserId int NULL,
            CreatedByUserId int NOT NULL,
            UpdatedByUserId int NULL,
            CreatedAt datetime2 NOT NULL CONSTRAINT DF_CaseLinkShares_CreatedAt DEFAULT (SYSUTCDATETIME()),
            UpdatedAt datetime2 NULL,
            RowVer rowversion NOT NULL
        );
    END;

    DECLARE @RequiredColumns TABLE (ColumnName sysname NOT NULL, TypeName sysname NOT NULL, MaxLength smallint NOT NULL, IsNullable bit NOT NULL, IsIdentity bit NULL);
    INSERT INTO @RequiredColumns VALUES
        (N'Id', N'int', 4, 0, 1), (N'ShaleClientId', N'int', 4, 0, 0), (N'CaseLinkId', N'int', 4, 0, 0),
        (N'ContactId', N'int', 4, 0, 0), (N'SharedAt', N'datetime2', 8, 0, 0), (N'Notes', N'nvarchar', 2000, 1, 0),
        (N'IsDeleted', N'bit', 1, 0, 0), (N'DeletedAt', N'datetime2', 8, 1, 0), (N'DeletedByUserId', N'int', 4, 1, 0),
        (N'CreatedByUserId', N'int', 4, 0, 0), (N'UpdatedByUserId', N'int', 4, 1, 0),
        (N'CreatedAt', N'datetime2', 8, 0, 0), (N'UpdatedAt', N'datetime2', 8, 1, 0), (N'RowVer', N'timestamp', 8, 0, 0);

    IF EXISTS (
        SELECT 1 FROM @RequiredColumns rc
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND c.name = rc.ColumnName
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        WHERE c.object_id IS NULL OR ty.name <> rc.TypeName OR c.max_length <> rc.MaxLength OR c.is_nullable <> rc.IsNullable OR (rc.IsIdentity IS NOT NULL AND c.is_identity <> rc.IsIdentity)
    )
    BEGIN
        SELECT rc.ColumnName, ExpectedType = rc.TypeName, ExpectedMaxLength = rc.MaxLength, ExpectedNullable = rc.IsNullable, ExpectedIdentity = rc.IsIdentity,
               ActualType = ty.name, ActualMaxLength = c.max_length, ActualNullable = c.is_nullable, ActualIdentity = c.is_identity
        FROM @RequiredColumns rc
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND c.name = rc.ColumnName
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id;
        THROW 54514, 'Existing dbo.CaseLinkShares table is missing required columns or has incompatible column definitions.', 1;
    END;

    IF OBJECT_ID(N'dbo.DF_CaseLinkShares_SharedAt', N'D') IS NULL THROW 54515, 'Required default DF_CaseLinkShares_SharedAt is missing. Stop for manual review.', 1;
    IF OBJECT_ID(N'dbo.DF_CaseLinkShares_IsDeleted', N'D') IS NULL THROW 54516, 'Required default DF_CaseLinkShares_IsDeleted is missing. Stop for manual review.', 1;
    IF OBJECT_ID(N'dbo.DF_CaseLinkShares_CreatedAt', N'D') IS NULL THROW 54517, 'Required default DF_CaseLinkShares_CreatedAt is missing. Stop for manual review.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND name = N'UX_CaseLinkShares_CaseLinkId_ContactId_Active')
        CREATE UNIQUE NONCLUSTERED INDEX UX_CaseLinkShares_CaseLinkId_ContactId_Active ON dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId) WHERE IsDeleted = 0;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND name = N'IX_CaseLinkShares_ShaleClientId_ContactId_Active')
        CREATE NONCLUSTERED INDEX IX_CaseLinkShares_ShaleClientId_ContactId_Active ON dbo.CaseLinkShares (ShaleClientId, ContactId) INCLUDE (CaseLinkId, SharedAt) WHERE IsDeleted = 0;

    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_ShaleClientId_ShaleClients', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);
    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_CaseLinkId_CaseLinks', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_CaseLinkId_CaseLinks FOREIGN KEY (CaseLinkId) REFERENCES dbo.CaseLinks (Id);
    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_ContactId_Contacts', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_ContactId_Contacts FOREIGN KEY (ContactId) REFERENCES dbo.Contacts (Id);
    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_DeletedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users (Id);
    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_CreatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id);
    IF OBJECT_ID(N'dbo.FK_CaseLinkShares_UpdatedByUserId_Users', N'F') IS NULL
        ALTER TABLE dbo.CaseLinkShares ADD CONSTRAINT FK_CaseLinkShares_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users (Id);

    DECLARE @Sql nvarchar(max);
    IF NOT EXISTS (
        SELECT 1 FROM sys.security_predicates p
        WHERE p.object_id = @PolicyObjectId AND p.target_object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND p.predicate_type_desc = N'FILTER')
    BEGIN
        SET @Sql = N'ALTER SECURITY POLICY ' + @PolicyQualified + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinkShares;';
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
   POST-MIGRATION READ-ONLY VERIFICATION
   ============================================================================ */
SELECT c.name AS ColumnName, ty.name AS TypeName, c.max_length, c.precision, c.scale, c.is_nullable, c.is_identity
FROM sys.columns c JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.CaseLinkShares') ORDER BY c.column_id;

SELECT dc.name AS DefaultName, c.name AS ColumnName, dc.definition
FROM sys.default_constraints dc JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID(N'dbo.CaseLinkShares') ORDER BY c.column_id;

SELECT kc.name AS PrimaryKeyName, c.name AS ColumnName, ic.key_ordinal
FROM sys.key_constraints kc JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.CaseLinkShares') AND kc.type = N'PK' ORDER BY ic.key_ordinal;

SELECT fk.name, ReferencedTable = OBJECT_SCHEMA_NAME(fk.referenced_object_id) + N'.' + OBJECT_NAME(fk.referenced_object_id), fk.is_disabled, fk.is_not_trusted
FROM sys.foreign_keys fk WHERE fk.parent_object_id = OBJECT_ID(N'dbo.CaseLinkShares') ORDER BY fk.name;

SELECT i.name, i.is_unique, i.has_filter, i.filter_definition, IncludedColumns = STRING_AGG(CASE WHEN ic.is_included_column = 1 THEN c.name END, N',')
FROM sys.indexes i LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID(N'dbo.CaseLinkShares')
GROUP BY i.name, i.is_unique, i.has_filter, i.filter_definition ORDER BY i.name;

SELECT sp.name AS PolicyName, sp.is_enabled AS TenantFilterEnabled,
       TargetTable = OBJECT_SCHEMA_NAME(p.target_object_id) + N'.' + OBJECT_NAME(p.target_object_id), p.predicate_definition
FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id = p.object_id
WHERE p.target_object_id = OBJECT_ID(N'dbo.CaseLinkShares');

SELECT N'Current tenant visible CaseLinkShares by scope' AS CheckName, ShaleClientId, COUNT(*) AS RowCount
FROM dbo.CaseLinkShares GROUP BY ShaleClientId ORDER BY ShaleClientId;
GO

/* ============================================================================
   OPTIONAL TENANT-ISOLATION VERIFICATION (transaction-wrapped and rolls back)

   WARNING: Run from a connection where SESSION_CONTEXT(N'ShaleClientId') was
   not previously marked read_only. This block discovers existing tenant 7 and
   tenant 8 CaseLink/Contact/User rows and rolls back all inserted verification
   rows. Do not claim this ran unless you execute it against a live database.
   ============================================================================ */
/*
DECLARE @Tenant7 int = 7, @Tenant8 int = 8;
DECLARE @CaseLink7 int, @Contact7 int, @User7 int, @CaseLink8 int, @Contact8 int, @User8 int;

SELECT TOP (1) @CaseLink7 = Id FROM dbo.CaseLinks WHERE ShaleClientId = @Tenant7 AND IsDeleted = 0 ORDER BY Id;
SELECT TOP (1) @Contact7 = Id FROM dbo.Contacts WHERE ShaleClientId = @Tenant7 AND ISNULL(IsDeleted, 0) = 0 ORDER BY Id;
SELECT TOP (1) @User7 = Id FROM dbo.Users WHERE ShaleClientId = @Tenant7 AND ISNULL(is_deleted, 0) = 0 ORDER BY Id;
SELECT TOP (1) @CaseLink8 = Id FROM dbo.CaseLinks WHERE ShaleClientId = @Tenant8 AND IsDeleted = 0 ORDER BY Id;
SELECT TOP (1) @Contact8 = Id FROM dbo.Contacts WHERE ShaleClientId = @Tenant8 AND ISNULL(IsDeleted, 0) = 0 ORDER BY Id;
SELECT TOP (1) @User8 = Id FROM dbo.Users WHERE ShaleClientId = @Tenant8 AND ISNULL(is_deleted, 0) = 0 ORDER BY Id;

IF @CaseLink7 IS NULL OR @Contact7 IS NULL OR @User7 IS NULL OR @CaseLink8 IS NULL OR @Contact8 IS NULL OR @User8 IS NULL
    THROW 54550, 'Tenant isolation verification requires active CaseLink, Contact, and User rows for both tenant 7 and tenant 8.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    INSERT INTO dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId, Notes, CreatedByUserId)
    VALUES (@Tenant7, @CaseLink7, @Contact7, N'Phase 5.2 verification tenant 7', @User7),
           (@Tenant8, @CaseLink8, @Contact8, N'Phase 5.2 verification tenant 8', @User8);

    EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = @Tenant7;
    IF (SELECT COUNT(*) FROM dbo.CaseLinkShares WHERE Notes = N'Phase 5.2 verification tenant 7') <> 1 THROW 54551, 'Tenant 7 cannot see its CaseLinkShares verification row.', 1;
    IF (SELECT COUNT(*) FROM dbo.CaseLinkShares WHERE Notes = N'Phase 5.2 verification tenant 8') <> 0 THROW 54552, 'Tenant 7 can see tenant 8 CaseLinkShares verification row.', 1;

    EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = @Tenant8;
    IF (SELECT COUNT(*) FROM dbo.CaseLinkShares WHERE Notes = N'Phase 5.2 verification tenant 8') <> 1 THROW 54553, 'Tenant 8 cannot see its CaseLinkShares verification row.', 1;
    IF (SELECT COUNT(*) FROM dbo.CaseLinkShares WHERE Notes = N'Phase 5.2 verification tenant 7') <> 0 THROW 54554, 'Tenant 8 can see tenant 7 CaseLinkShares verification row.', 1;

    BEGIN TRY
        INSERT INTO dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId, Notes, CreatedByUserId)
        VALUES (@Tenant8, @CaseLink8, @Contact8, N'Phase 5.2 duplicate verification should fail', @User8);
        THROW 54555, 'Duplicate active CaseLinkShares insert unexpectedly succeeded.', 1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() NOT IN (2601, 2627) THROW;
    END CATCH;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
    THROW;
END CATCH;

EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
SELECT N'CaseLinkShares verification rows persisted after rollback' AS CheckName, COUNT(*) AS ExpectedZero
FROM dbo.CaseLinkShares WHERE Notes LIKE N'Phase 5.2 verification%';
*/
GO

/*
Rollback guidance (manual operator action only):
  1. Remove only the dbo.CaseLinkShares predicate from the existing TenantFilter
     policy. Do not drop TenantFilter or sec.fn_FilterByTenant.
  2. Drop CaseLinkShares indexes/foreign keys as required by the database engine.
  3. Drop dbo.CaseLinkShares.
  4. Do not drop existing CaseLinks, Contacts, Users, ShaleClients, predicate
     functions, or unrelated security predicates.

Service-layer validation still required:
  - Single-column foreign keys are used because this migration did not verify an
    existing unique composite key on dbo.CaseLinks(ShaleClientId, Id) or
    dbo.Contacts(ShaleClientId, Id), and it must not redesign base tables solely
    for composite FKs. Phase 5.3 services must ensure CaseLinkShares.ShaleClientId
    matches the referenced CaseLink, Contact, and actor Users tenants.
  - SharedAt is the user-asserted sharing time; CreatedAt is the database record
    creation time. Later UI/service work may supply or edit SharedAt.
  - Active shares have IsDeleted = 0, DeletedAt IS NULL, and DeletedByUserId IS NULL.
    Removed/unshared rows have IsDeleted = 1 with DeletedAt populated and
    DeletedByUserId populated when the actor is known.
  - Phase 5.3 Case Link deletion must soft-delete active CaseLinkShares in the
    same transaction that soft-deletes the Case Link.
  - Contact soft deletion must not cascade-delete shares; historical reads should
    preserve records where appropriate, but new active shares to deleted or
    unavailable Contacts must be rejected.
*/

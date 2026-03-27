-- Phase 1: CaseUpdates semantics -> notes only + soft delete columns.
-- Safe to run multiple times.

IF COL_LENGTH('dbo.CaseUpdates', 'IsDeleted') IS NULL
BEGIN
    ALTER TABLE dbo.CaseUpdates
    ADD IsDeleted BIT NOT NULL CONSTRAINT DF_CaseUpdates_IsDeleted DEFAULT (0);
END;
GO

IF COL_LENGTH('dbo.CaseUpdates', 'DeletedAt') IS NULL
BEGIN
    ALTER TABLE dbo.CaseUpdates
    ADD DeletedAt DATETIME2 NULL;
END;
GO

IF COL_LENGTH('dbo.CaseUpdates', 'DeletedByUserId') IS NULL
BEGIN
    ALTER TABLE dbo.CaseUpdates
    ADD DeletedByUserId INT NULL;
END;
GO

-- Optional FK if Users exists and you want referential integrity:
-- IF OBJECT_ID('dbo.Users', 'U') IS NOT NULL
--    AND OBJECT_ID('dbo.FK_CaseUpdates_DeletedByUserId_Users', 'F') IS NULL
-- BEGIN
--     ALTER TABLE dbo.CaseUpdates
--     ADD CONSTRAINT FK_CaseUpdates_DeletedByUserId_Users
--         FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users(Id);
-- END;
-- GO

-- Optional filtered index for active notes read path.
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_CaseUpdates_CaseId_Active_CreatedAt'
      AND object_id = OBJECT_ID('dbo.CaseUpdates')
)
BEGIN
    CREATE INDEX IX_CaseUpdates_CaseId_Active_CreatedAt
        ON dbo.CaseUpdates (CaseId, IsDeleted, CreatedAt DESC, Id DESC);
END;
GO

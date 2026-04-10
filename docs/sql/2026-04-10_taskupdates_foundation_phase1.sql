SET XACT_ABORT ON;
GO

BEGIN TRAN;
GO

IF OBJECT_ID(N'dbo.TaskUpdates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TaskUpdates
    (
        Id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        TaskId BIGINT NOT NULL,
        CaseId INT NOT NULL,
        ShaleClientId INT NOT NULL,
        UserId INT NOT NULL,
        Body NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_TaskUpdates_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt DATETIME2 NULL,
        IsDeleted BIT NOT NULL CONSTRAINT DF_TaskUpdates_IsDeleted DEFAULT ((0))
    );
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TaskUpdates')
      AND name = N'IX_TaskUpdates_TaskId_CreatedAt_Id'
)
BEGIN
    CREATE INDEX IX_TaskUpdates_TaskId_CreatedAt_Id
    ON dbo.TaskUpdates (TaskId, CreatedAt DESC, Id DESC);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.TaskUpdates')
      AND name = N'IX_TaskUpdates_CaseId_CreatedAt_Id'
)
BEGIN
    CREATE INDEX IX_TaskUpdates_CaseId_CreatedAt_Id
    ON dbo.TaskUpdates (CaseId, CreatedAt DESC, Id DESC);
END;
GO

COMMIT TRAN;
GO

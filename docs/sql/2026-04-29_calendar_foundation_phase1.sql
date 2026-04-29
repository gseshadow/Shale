SET NOCOUNT ON;

IF OBJECT_ID('dbo.CalendarEventTypes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.CalendarEventTypes (
        CalendarEventTypeId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ShaleClientId INT NULL,
        SystemKey NVARCHAR(100) NULL,
        Name NVARCHAR(100) NOT NULL,
        ColorHex NVARCHAR(20) NULL,
        SortOrder INT NOT NULL CONSTRAINT DF_CalendarEventTypes_SortOrder DEFAULT (0),
        IsActive BIT NOT NULL CONSTRAINT DF_CalendarEventTypes_IsActive DEFAULT (1),
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_CalendarEventTypes_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt DATETIME2 NULL
    );
END;

IF OBJECT_ID('dbo.CalendarEvents', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.CalendarEvents (
        CalendarEventId INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ShaleClientId INT NOT NULL,
        CalendarEventTypeId INT NOT NULL,
        CaseId INT NULL,
        TaskId INT NULL,
        Title NVARCHAR(255) NOT NULL,
        Description NVARCHAR(MAX) NULL,
        StartsAt DATETIME2 NOT NULL,
        EndsAt DATETIME2 NULL,
        AllDay BIT NOT NULL CONSTRAINT DF_CalendarEvents_AllDay DEFAULT (1),
        SourceType NVARCHAR(50) NOT NULL CONSTRAINT DF_CalendarEvents_SourceType DEFAULT ('MANUAL'),
        SourceField NVARCHAR(100) NULL,
        SourceId INT NULL,
        AssignedToUserId INT NULL,
        IsCompleted BIT NOT NULL CONSTRAINT DF_CalendarEvents_IsCompleted DEFAULT (0),
        IsCancelled BIT NOT NULL CONSTRAINT DF_CalendarEvents_IsCancelled DEFAULT (0),
        CreatedByUserId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_CalendarEvents_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt DATETIME2 NULL
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEventTypes')
      AND name = 'UX_CalendarEventTypes_ShaleClientId_SystemKey_NonNull'
)
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_CalendarEventTypes_ShaleClientId_SystemKey_NonNull
        ON dbo.CalendarEventTypes (ShaleClientId, SystemKey)
        WHERE SystemKey IS NOT NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEventTypes')
      AND name = 'IX_CalendarEventTypes_ShaleClientId_SystemKey'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CalendarEventTypes_ShaleClientId_SystemKey
        ON dbo.CalendarEventTypes (ShaleClientId, SystemKey);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEvents')
      AND name = 'IX_CalendarEvents_ShaleClientId_StartsAt'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CalendarEvents_ShaleClientId_StartsAt
        ON dbo.CalendarEvents (ShaleClientId, StartsAt);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEvents')
      AND name = 'IX_CalendarEvents_ShaleClientId_CaseId'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CalendarEvents_ShaleClientId_CaseId
        ON dbo.CalendarEvents (ShaleClientId, CaseId);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEvents')
      AND name = 'IX_CalendarEvents_ShaleClientId_TaskId'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CalendarEvents_ShaleClientId_TaskId
        ON dbo.CalendarEvents (ShaleClientId, TaskId);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.CalendarEvents')
      AND name = 'IX_CalendarEvents_ShaleClientId_Source'
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CalendarEvents_ShaleClientId_Source
        ON dbo.CalendarEvents (ShaleClientId, SourceType, SourceField, SourceId);
END;

IF OBJECT_ID('dbo.FK_CalendarEventTypes_ShaleClientId_ShaleClients', 'F') IS NULL
    AND OBJECT_ID('dbo.ShaleClients', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEventTypes
        ADD CONSTRAINT FK_CalendarEventTypes_ShaleClientId_ShaleClients
            FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_ShaleClientId_ShaleClients', 'F') IS NULL
    AND OBJECT_ID('dbo.ShaleClients', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_ShaleClientId_ShaleClients
            FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_CalendarEventTypeId_CalendarEventTypes', 'F') IS NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_CalendarEventTypeId_CalendarEventTypes
            FOREIGN KEY (CalendarEventTypeId) REFERENCES dbo.CalendarEventTypes (CalendarEventTypeId);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_CaseId_Cases', 'F') IS NULL
    AND OBJECT_ID('dbo.Cases', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_CaseId_Cases
            FOREIGN KEY (CaseId) REFERENCES dbo.Cases (Id);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_TaskId_Tasks', 'F') IS NULL
    AND OBJECT_ID('dbo.Tasks', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_TaskId_Tasks
            FOREIGN KEY (TaskId) REFERENCES dbo.Tasks (Id);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_AssignedToUserId_Users', 'F') IS NULL
    AND OBJECT_ID('dbo.Users', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_AssignedToUserId_Users
            FOREIGN KEY (AssignedToUserId) REFERENCES dbo.Users (Id);
END;

IF OBJECT_ID('dbo.FK_CalendarEvents_CreatedByUserId_Users', 'F') IS NULL
    AND OBJECT_ID('dbo.Users', 'U') IS NOT NULL
BEGIN
    ALTER TABLE dbo.CalendarEvents
        ADD CONSTRAINT FK_CalendarEvents_CreatedByUserId_Users
            FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id);
END;

INSERT INTO dbo.CalendarEventTypes (ShaleClientId, SystemKey, Name, SortOrder, IsActive)
SELECT NULL, v.SystemKey, v.Name, v.SortOrder, 1
FROM (VALUES
    ('DEADLINE', 'Deadline', 10),
    ('TRIAL', 'Trial', 20),
    ('DISCOVERY', 'Discovery', 30),
    ('RESPONSE', 'Response', 40),
    ('HEARING', 'Hearing', 50),
    ('DEPOSITION', 'Deposition', 60),
    ('MEDIATION', 'Mediation', 70),
    ('MEETING', 'Meeting', 80),
    ('REMINDER', 'Reminder', 90),
    ('TASK_DUE', 'Task Due', 100),
    ('STATUTE_OF_LIMITATIONS', 'Statute of Limitations', 110),
    ('TORT_NOTICE_DEADLINE', 'Tort Notice Deadline', 120)
) v(SystemKey, Name, SortOrder)
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.CalendarEventTypes cet
    WHERE cet.ShaleClientId IS NULL
      AND cet.SystemKey = v.SystemKey
);

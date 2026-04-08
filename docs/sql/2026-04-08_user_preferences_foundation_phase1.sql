/*
  Generic per-user preferences foundation.

  Adds dbo.UserPreferences key/value storage so new preference categories
  can be added without schema changes.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.UserPreferences', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.UserPreferences (
        Id               bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ShaleClientId    int NOT NULL,
        UserId           int NOT NULL,
        PreferenceKey    nvarchar(190) NOT NULL,
        PreferenceValue  nvarchar(max) NULL,
        ValueType        nvarchar(32) NOT NULL,
        UpdatedAt        datetime2(3) NOT NULL CONSTRAINT DF_UserPreferences_UpdatedAt DEFAULT SYSUTCDATETIME(),
        UpdatedByUserId  int NULL
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.UserPreferences')
      AND name = 'UX_UserPreferences_ClientUserKey'
)
BEGIN
    CREATE UNIQUE INDEX UX_UserPreferences_ClientUserKey
        ON dbo.UserPreferences (ShaleClientId, UserId, PreferenceKey);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.UserPreferences')
      AND name = 'IX_UserPreferences_ClientUser'
)
BEGIN
    CREATE INDEX IX_UserPreferences_ClientUser
        ON dbo.UserPreferences (ShaleClientId, UserId)
        INCLUDE (PreferenceKey, ValueType, UpdatedAt);
END;

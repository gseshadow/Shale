/*
  Structured per-user board/lane preferences foundation.

  Adds dbo.UserBoardLanePreferences for board/lane state such as pinning,
  lane sort index, and collapse state.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.UserBoardLanePreferences', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.UserBoardLanePreferences (
        Id               bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ShaleClientId    int NOT NULL,
        UserId           int NOT NULL,
        BoardKey         nvarchar(100) NOT NULL,
        LaneType         nvarchar(50) NOT NULL,
        LaneKey          nvarchar(190) NOT NULL,
        IsPinned         bit NOT NULL CONSTRAINT DF_UserBoardLanePreferences_IsPinned DEFAULT (0),
        SortIndex        int NULL,
        IsCollapsed      bit NULL,
        UpdatedAt        datetime2(3) NOT NULL CONSTRAINT DF_UserBoardLanePreferences_UpdatedAt DEFAULT SYSUTCDATETIME(),
        UpdatedByUserId  int NULL
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.UserBoardLanePreferences')
      AND name = 'UX_UserBoardLanePreferences_ClientUserBoardLane'
)
BEGIN
    CREATE UNIQUE INDEX UX_UserBoardLanePreferences_ClientUserBoardLane
        ON dbo.UserBoardLanePreferences (ShaleClientId, UserId, BoardKey, LaneType, LaneKey);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.UserBoardLanePreferences')
      AND name = 'IX_UserBoardLanePreferences_ClientUserBoard'
)
BEGIN
    CREATE INDEX IX_UserBoardLanePreferences_ClientUserBoard
        ON dbo.UserBoardLanePreferences (ShaleClientId, UserId, BoardKey)
        INCLUDE (LaneType, LaneKey, IsPinned, SortIndex, IsCollapsed, UpdatedAt);
END;

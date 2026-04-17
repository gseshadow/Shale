/*
 Phase 1: tenant-scope AuditLog rows without breaking historical data.
 - Adds nullable dbo.AuditLog.ShaleClientId for safe rollout.
 - Adds index to support tenant-scoped viewer queries.
 - Historical backfill intentionally deferred.
*/
SET NOCOUNT ON;

IF COL_LENGTH('dbo.AuditLog', 'ShaleClientId') IS NULL
BEGIN
    ALTER TABLE dbo.AuditLog
        ADD ShaleClientId INT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_AuditLog_ShaleClientId_EntryDate'
      AND object_id = OBJECT_ID('dbo.AuditLog')
)
BEGIN
    CREATE INDEX IX_AuditLog_ShaleClientId_EntryDate
        ON dbo.AuditLog (ShaleClientId, EntryDate DESC);
END
GO

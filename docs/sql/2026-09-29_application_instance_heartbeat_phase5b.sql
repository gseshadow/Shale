/* Phase 5B: additive latest-state heartbeat fields. Forward-only, rerunnable, N-1 compatible. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.ApplicationInstances',N'U') IS NULL
 BEGIN
  THROW 57500, 'Required Phase 4B ApplicationInstances table is missing.', 1;
 END;
 IF COL_LENGTH(N'dbo.ApplicationInstances',N'LastHeartbeatAt') IS NULL
 BEGIN
  ALTER TABLE dbo.ApplicationInstances ADD LastHeartbeatAt datetime2(7) NULL;
 END;
 IF COL_LENGTH(N'dbo.ApplicationInstances',N'LastHumanActivityAt') IS NULL
 BEGIN
  ALTER TABLE dbo.ApplicationInstances ADD LastHumanActivityAt datetime2(7) NULL;
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0
 BEGIN
  ROLLBACK TRANSACTION;
 END;
 THROW;
END CATCH;
GO
/* No backfill, seed rows, index, constraint, or RLS/API contract change. Older insert/update shapes remain valid. */

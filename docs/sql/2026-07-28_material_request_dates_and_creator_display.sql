/*
  Material Request requested-material date range.

  RequestedAt remains the real-world Request Date and ExpectedResponseDate remains
  the optional Due Date. CreatedByUserId already exists and is intentionally not
  recreated or backfilled: the foundation migration made it required and populated
  it from the authenticated actor for every request.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.MaterialRequests', N'U') IS NULL
    THROW 54820, 'Required table dbo.MaterialRequests is missing.', 1;

IF COL_LENGTH(N'dbo.MaterialRequests', N'RequestedRangeStartDate') IS NULL
    ALTER TABLE dbo.MaterialRequests ADD RequestedRangeStartDate date NULL;

IF COL_LENGTH(N'dbo.MaterialRequests', N'RequestedRangeEndDate') IS NULL
    ALTER TABLE dbo.MaterialRequests ADD RequestedRangeEndDate date NULL;

IF OBJECT_ID(N'dbo.CK_MaterialRequests_RequestedRange', N'C') IS NULL
    ALTER TABLE dbo.MaterialRequests ADD CONSTRAINT CK_MaterialRequests_RequestedRange
        CHECK (RequestedRangeStartDate IS NULL OR RequestedRangeEndDate IS NULL
               OR RequestedRangeStartDate <= RequestedRangeEndDate);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

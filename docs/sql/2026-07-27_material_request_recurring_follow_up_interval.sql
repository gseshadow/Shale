/* Forward-only, idempotent Material Request recurring follow-up interval migration. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.MaterialRequests', N'FollowUpIntervalDays') IS NULL
    ALTER TABLE dbo.MaterialRequests ADD FollowUpIntervalDays int NULL;

IF OBJECT_ID(N'dbo.CK_MaterialRequests_FollowUpIntervalDays', N'C') IS NULL
    ALTER TABLE dbo.MaterialRequests WITH CHECK ADD CONSTRAINT CK_MaterialRequests_FollowUpIntervalDays
        CHECK (FollowUpIntervalDays IS NULL OR FollowUpIntervalDays BETWEEN 1 AND 365);

COMMIT TRANSACTION;

/* Forward-only, manually deployable Case Date occurrence title. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.CaseDates', N'U') IS NULL THROW 55818, 'Required table dbo.CaseDates is missing.', 1;
IF COL_LENGTH(N'dbo.CaseDates', N'Title') IS NULL ALTER TABLE dbo.CaseDates ADD Title nvarchar(255) NULL;
IF NOT EXISTS (
 SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.object_id=OBJECT_ID(N'dbo.CaseDates') AND c.name=N'Title'
 AND t.name=N'nvarchar' AND c.max_length=510 AND c.is_nullable=1
) THROW 55819, 'dbo.CaseDates.Title exists but is not nvarchar(255) NULL.', 1;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
THROW;
END CATCH;
GO

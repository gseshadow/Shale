:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/controlled-legacy-approval.sql
-- UTC ISO-8601 literals, without zone suffix (SQL Server datetime2); no default window.
DECLARE @Start datetime2=TRY_CONVERT(datetime2,N'$(WindowStartUtc)',126),
 @End datetime2=TRY_CONVERT(datetime2,N'$(WindowEndUtc)',126),
 @RollbackBy datetime2=TRY_CONVERT(datetime2,N'$(RollbackStartByUtc)',126);
IF @Start IS NULL OR @End IS NULL OR @RollbackBy IS NULL
 OR @Start>=@RollbackBy OR DATEADD(MINUTE,10,@RollbackBy)>@End
 OR @End>DATEADD(MINUTE,30,@Start)
 OR SYSUTCDATETIME()<@Start OR SYSUTCDATETIME()>=@RollbackBy
 THROW 58361,N'Authorized window required: maximum 30 minutes, minimum 10-minute rollback reserve; no apply outside trial time.',1;
GO
:r docs/sql/auth/permission-cutover/legacy-retirement-apply-body.sql

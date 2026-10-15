:ON ERROR EXIT
/* Standalone read-only 58307 investigation. No approval assertions or mutation scripts included.
   Run from repository root on the existing approved dbo metadata connection, SQLCMD -b -V 16.
   Only session impersonation, SET and temporary metadata tables. Output <=200 details + <=200 token rows. */
SET NOCOUNT ON;
IF @@TRANCOUNT<>0 OR USER_NAME()<>N'dbo' OR DB_NAME()<>N'$(ExpectedDatabase)'
 THROW 58300,N'Expected database, dbo metadata authority and no ambient transaction required.',1;
IF NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'shale_app' AND type IN ('S','E','X'))
 THROW 58303,N'Reviewed auth principal required.',1;
GO
:r docs/sql/auth/permission-cutover/auth-authority-snapshot.sql
:r docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql
:r docs/sql/auth/permission-cutover/auth-authority-check.sql
DROP TABLE #AuthAuthorityChecks;
DROP TABLE #CutoverOwnership;
DROP TABLE #CutoverGrantOptions;
DROP TABLE #CutoverVisibility;
DROP TABLE #CutoverPrincipals;
DROP TABLE #CutoverObjects;
DROP TABLE #CutoverColumns;
GO

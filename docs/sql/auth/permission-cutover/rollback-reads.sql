:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/preflight.sql
IF N'$(RollbackCompatibilityReviewed)'<>N'CONFIRMED'
 THROW 58318,N'Client rollback and reopened credential access require operator disposition.',1;
-- Expected SIDs come from the separately saved installation output, never from this invocation.
IF (SELECT CONVERT(varchar(172),sid,1) FROM sys.database_principals WHERE name=N'ShaleAuthTableFenceV1')<>N'$(AuthFenceSid)'
 OR (SELECT CONVERT(varchar(172),sid,1) FROM sys.database_principals WHERE name=N'ShaleRuntimeHashFenceV1')<>N'$(RuntimeFenceSid)'
 OR DATABASE_PRINCIPAL_ID(N'ShaleAuthTableFenceV1') IS NULL OR DATABASE_PRINCIPAL_ID(N'ShaleRuntimeHashFenceV1') IS NULL
 THROW 58319,N'Original fence role identity evidence required.',1;
GO
:r docs/sql/auth/permission-cutover/fence-postcheck.sql
BEGIN TRANSACTION;
ALTER ROLE ShaleAuthTableFenceV1 DROP MEMBER shale_app;
ALTER ROLE ShaleRuntimeHashFenceV1 DROP MEMBER shale_runtime;
ALTER ROLE ShaleRuntimeHashFenceV1 DROP MEMBER shale_runtime_role;
DROP ROLE ShaleAuthTableFenceV1;
DROP ROLE ShaleRuntimeHashFenceV1;
COMMIT TRANSACTION;
SELECT N'AUTH_CUTOVER_READS_ROLLED_BACK' AS CheckName;
GO

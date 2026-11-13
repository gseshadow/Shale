:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
:r docs/sql/ai-integration/azure-acceptance/owner.sql
GO
/* First connection only. Never adopt an existing fixture or try to make this pass by dropping objects. */
IF EXISTS(SELECT 1 FROM sys.objects WHERE is_ms_shipped=0)
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id>4 AND type<>'R')
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE type='R' AND is_fixed_role=0 AND name<>N'public')
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE schema_id<16384 AND name NOT IN(N'dbo',N'guest',N'sys',N'INFORMATION_SCHEMA'))
 THROW 59802,'STOP: expected a fresh empty database without custom objects or identities.',1;
SELECT 'EMPTY_TARGET_PENDING_STAGE0' AS PreparationStatus;

:on error exit
:r docs/sql/ai-integration/azure-acceptance/target.sql
:r docs/sql/ai-integration/azure-acceptance/owner.sql
GO
/* Fresh authoritative fixture only: same three-filter normalization as beforeConfinement().
   Separate operator approval, exclusive maintenance window, no serving app or other consumers. */
DECLARE @NormalizeApproved bit=0; -- set to 1 only in the private reviewed packet
IF @NormalizeApproved<>1 THROW 59803,'STOP: approve fresh-fixture-only normalization.',1;
IF EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=3 AND major_id=SCHEMA_ID(N'sec') AND name=N'Shale.Phase1R.v1')
 OR OBJECT_ID(N'dbo.AiCaseDataInvocation') IS NOT NULL
 OR (SELECT COUNT(*) FROM sys.security_predicates WHERE object_id=OBJECT_ID(N'sec.TenantFilter')
 AND target_object_id IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')) AND predicate_type=0)<>3
 THROW 59803,'STOP: expected only the fresh candidate three-filter state.',1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users;
 ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.auditLog;
 ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Statuses;
 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
/* Evidence for independent fresh-fixture review, never an automatic live-state hash adoption. */
SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant'))),2) AS StrictDefinitionHash,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal'))),2) AS OverlayDefinitionHash,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT p.* FROM sys.security_predicates p
 WHERE p.target_object_id NOT IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
 ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)),2) AS OtherPredicatesHash;

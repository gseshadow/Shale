/* READ ONLY. Run unchanged with an approved admin connection and NULL application session context. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 57070,'Verification requires NULL application session context.',1;
DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U');
SELECT CASE WHEN COUNT_BIG(*)=2 THEN 0 ELSE 1 END FindingCount,N'Missing Phase 2B EntityType allowlist values' Finding
FROM(VALUES(N'FIELD_CONFIRMATION_POLICY'),(N'SAVED_VALUE_CONFIRMATION'))v(Value)
WHERE EXISTS(SELECT 1 FROM sys.check_constraints c WHERE c.parent_object_id=@id AND c.name=N'CK_EntityActionAuditLog_EntityType' AND c.is_disabled=0 AND c.is_not_trusted=0 AND CHARINDEX(N''''+v.Value+N'''',c.definition)>0);
SELECT CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints c WHERE c.parent_object_id=@id AND c.name=N'CK_EntityActionAuditLog_Action' AND c.is_disabled=0 AND c.is_not_trusted=0 AND CHARINDEX(N'''CONFIRMED''',c.definition)>0) THEN 0 ELSE 1 END FindingCount,N'Missing CONFIRMED action allowlist value' Finding;
SELECT COUNT_BIG(*) FindingCount,N'Current enabled policy has invalid role' Finding FROM dbo.FieldConfirmationPolicies p LEFT JOIN dbo.FirmWideRoleDefinitions r ON r.Id=p.RequiredFirmWideRoleDefinitionId AND r.ShaleClientId=p.ShaleClientId WHERE p.SupersededAt IS NULL AND p.RequiresConfirmation=1 AND (r.Id IS NULL OR r.IsActive=0 OR r.IsDeleted=1);
SELECT COUNT_BIG(*) FindingCount,N'Current confirmation target revision mismatch' Finding FROM dbo.CaseDateConfirmationTargets t JOIN dbo.CaseDates d ON d.Id=t.CaseDateId AND d.ShaleClientId=t.ShaleClientId WHERE d.IsDeleted=0 AND t.BusinessValueRevision=d.ValueRevision AND NOT EXISTS(SELECT 1 FROM dbo.SavedValueConfirmationRequirements r WHERE r.Id=t.ConfirmationRequirementId AND r.ShaleClientId=t.ShaleClientId AND r.BusinessValueRevision=t.BusinessValueRevision);

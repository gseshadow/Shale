/* Organizations Phase 1C read-only audit allowlist verification. Do not execute with tenant context. */
SET XACT_ABORT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
    THROW 57100, 'Organizations Phase 1C verification requires all-tenant visibility.', 1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime')
    THROW 57101, 'Use an approved administrative read-only principal.', 1;
IF OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL
    THROW 57102, 'dbo.EntityActionAuditLog is missing.', 1;

SELECT N'Organization Phase 1C EntityTypes accepted (expect 2)' CheckName, COUNT_BIG(*) FindingCount
FROM sys.check_constraints c
WHERE c.parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog')
  AND c.is_disabled=0 AND c.is_not_trusted=0
  AND c.definition LIKE N'%ORGANIZATION_TYPE%'
  AND c.definition LIKE N'%ORGANIZATION_ORGANIZATION_TYPE%';

SELECT N'existing Organization Phase 1C audit rows (informational)' CheckName, EntityType, Action, COUNT_BIG(*) RowCount
FROM dbo.EntityActionAuditLog
WHERE EntityType IN('ORGANIZATION_TYPE','ORGANIZATION_ORGANIZATION_TYPE')
GROUP BY EntityType,Action ORDER BY EntityType,Action;

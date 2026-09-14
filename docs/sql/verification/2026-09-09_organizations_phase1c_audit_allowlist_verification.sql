/* Organizations Phase 1C read-only audit allowlist verification. Do not execute with tenant context. */
SET XACT_ABORT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
    THROW 57100, 'Organizations Phase 1C verification requires all-tenant visibility.', 1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime')
    THROW 57101, 'Use an approved administrative read-only principal.', 1;
IF OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL
    THROW 57102, 'dbo.EntityActionAuditLog is missing.', 1;

DECLARE @TokenChecks table(EntityType varchar(64) NOT NULL PRIMARY KEY, MatchingCount bigint NOT NULL);
INSERT @TokenChecks(EntityType,MatchingCount)
SELECT required.EntityType,COALESCE(SUM(CONVERT(bigint,
       (LEN(c.definition)-LEN(REPLACE(c.definition,N''''+required.EntityType+N'''',N'')))
       / NULLIF(LEN(N''''+required.EntityType+N''''),0))),0)
FROM (VALUES('ORGANIZATION_TYPE'),('ORGANIZATION_ORGANIZATION_TYPE')) required(EntityType)
LEFT JOIN sys.check_constraints c
  ON c.parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog')
 AND c.is_disabled=0 AND c.is_not_trusted=0
GROUP BY required.EntityType;

SELECT N'Organization Phase 1C quoted EntityType matches (expect 2)' CheckName,
       SUM(MatchingCount) MatchingCount
FROM @TokenChecks;

DECLARE @FindingCount bigint=(SELECT COUNT_BIG(*) FROM @TokenChecks WHERE MatchingCount<>1);
SELECT N'Organization Phase 1C EntityType findings (expect 0)' CheckName,
       @FindingCount FindingCount;
IF @FindingCount <> 0
    THROW 57103, 'Each Organization Phase 1C EntityType must occur exactly once in an enabled, trusted CHECK.', 1;

SELECT N'existing Organization Phase 1C audit rows (informational)' CheckName, EntityType, Action, COUNT_BIG(*) AuditRowCount
FROM dbo.EntityActionAuditLog
WHERE EntityType IN('ORGANIZATION_TYPE','ORGANIZATION_ORGANIZATION_TYPE')
GROUP BY EntityType,Action ORDER BY EntityType,Action;

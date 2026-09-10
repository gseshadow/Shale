/* Read-only Organizations Phase 3C audit allowlist verification. */
SET XACT_ABORT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 57310,'Verification requires NULL tenant and principal context.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') THROW 57311,'Use an approved administrative read-only principal.',1;
IF OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL THROW 57312,'dbo.EntityActionAuditLog is missing.',1;
DECLARE @Required table(EntityType varchar(64) PRIMARY KEY);INSERT @Required VALUES('ORGANIZATION'),('ORGANIZATION_PHONE'),('ORGANIZATION_EMAIL'),('ORGANIZATION_ADDRESS'),('ORGANIZATION_WEBSITE');
DECLARE @FindingCount bigint=(SELECT COUNT_BIG(*) FROM @Required r WHERE (SELECT COUNT_BIG(*) FROM sys.check_constraints c WHERE c.parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND c.is_disabled=0 AND c.is_not_trusted=0 AND c.definition LIKE N'%'''+r.EntityType+N'''%')<>1);
SELECT N'Organization Phase 3C allowlist findings (expect 0)' CheckName,@FindingCount FindingCount;
IF @FindingCount<>0 THROW 57313,'Each Phase 3C EntityType must occur once in an enabled trusted CHECK.',1;
SELECT EntityType,Action,COUNT_BIG(*) AuditRowCount FROM dbo.EntityActionAuditLog WHERE EntityType IN('ORGANIZATION','ORGANIZATION_PHONE','ORGANIZATION_EMAIL','ORGANIZATION_ADDRESS','ORGANIZATION_WEBSITE') GROUP BY EntityType,Action ORDER BY EntityType,Action;

/* Read-only catalog/state inspection. No authorization to connect to production.
   Use reviewed owner/setup authority; runtime enforcement requires the separate synthetic test. */
SET NOCOUNT ON;
DECLARE @TenantTables table(Name sysname PRIMARY KEY);
INSERT @TenantTables VALUES(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),
 (N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),
 (N'AiOAuthCredential'),(N'AiQuotaReservation');
SELECT t.Name,
 SUM(CASE WHEN p.predicate_type_desc=N'FILTER' AND p.predicate_definition LIKE N'%fn_FilterByTenant%ShaleClientId%' THEN 1 ELSE 0 END) FilterCount,
 SUM(CASE WHEN p.predicate_type_desc=N'BLOCK' AND p.operation_desc=N'AFTER INSERT' THEN 1 ELSE 0 END) InsertBlockCount,
 SUM(CASE WHEN p.predicate_type_desc=N'BLOCK' AND p.operation_desc=N'AFTER UPDATE' THEN 1 ELSE 0 END) UpdateBlockCount,
 SUM(CASE WHEN sp.is_enabled=0 THEN 1 ELSE 0 END) DisabledFindingCount
FROM @TenantTables t LEFT JOIN sys.security_predicates p ON p.target_object_id=OBJECT_ID(N'dbo.'+t.Name)
LEFT JOIN sys.security_policies sp ON sp.object_id=p.object_id GROUP BY t.Name ORDER BY t.Name;
SELECT COUNT(*) UntrustedConstraintFindingCount FROM sys.foreign_keys
WHERE OBJECT_NAME(parent_object_id) IN(SELECT Name FROM @TenantTables) AND (is_disabled=1 OR is_not_trusted=1);
SELECT Enabled GlobalEnabled,Revision FROM dbo.AiProductCapability WHERE Id=1;
SELECT COUNT(*) EnabledTenantPolicyCount FROM dbo.TenantAiIntegrationPolicy WHERE Enabled=1;
SELECT COUNT(*) RegistrationCount FROM dbo.AiIntegrationRegistration;
SELECT name ReceiptName,CONVERT(varchar(64),CONVERT(varbinary(32),value),2) ReceiptHash
FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability')
AND name IN(N'Shale.AiFoundationV1',N'Shale.AiFoundationV1.Source');
/* Expected fresh state: all nine rows have 1/1/1 predicates and zero disabled findings;
   zero untrusted findings, GlobalEnabled=0, EnabledTenantPolicyCount=0, RegistrationCount=0.
   Catalog presence is static evidence, never proof of ordinary non-bypass enforcement. */

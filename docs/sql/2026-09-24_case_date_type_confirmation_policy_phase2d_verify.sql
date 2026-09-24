/* READ ONLY. No values to fill in. Run unchanged using the approved all-tenant administrative connection with NULL application session context. */
SET NOCOUNT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57110,'Verification requires NULL application session context.',1;
IF COL_LENGTH(N'dbo.FieldConfirmationPolicies',N'CaseDateTypePolicyKey') IS NULL BEGIN
 SELECT COUNT_BIG(*) InformationalCount,N'Enabled legacy New Intake policies requiring an explicit rollout decision' Finding
 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND RequiresConfirmation=1;
 SELECT ShaleClientId,Id,FormKey,FieldKey,PolicyRevision,RequiredFirmWideRoleDefinitionId
 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND RequiresConfirmation=1 ORDER BY ShaleClientId,FormKey,FieldKey;
 SELECT CAST(1 AS bigint) FindingCount,N'Phase 2D CaseDateTypePolicyKey schema has not been applied' Finding;
END ELSE BEGIN
 EXEC sys.sp_executesql N'
 SELECT COUNT_BIG(*) InformationalCount,N''Enabled legacy New Intake policies requiring an explicit rollout decision'' Finding
 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL AND RequiresConfirmation=1;
 SELECT ShaleClientId,Id,FormKey,FieldKey,PolicyRevision,RequiredFirmWideRoleDefinitionId
 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL AND RequiresConfirmation=1 ORDER BY ShaleClientId,FormKey,FieldKey;
 SELECT COUNT_BIG(*) FindingCount,N''Invalid Case Date Type policy identity shape'' Finding
 FROM dbo.FieldConfirmationPolicies WHERE CaseDateTypePolicyKey IS NOT NULL AND (FormKey IS NOT NULL OR FieldKey IS NOT NULL);
 SELECT COUNT_BIG(*) FindingCount,N''Duplicate current Case Date Type policies'' Finding FROM(
  SELECT ShaleClientId,CaseDateTypePolicyKey FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NOT NULL
  GROUP BY ShaleClientId,CaseDateTypePolicyKey HAVING COUNT_BIG(*)>1)x;
 SELECT COUNT_BIG(*) FindingCount,N''Current enabled Case Date Type policy has an invalid firm-wide role'' Finding
 FROM dbo.FieldConfirmationPolicies p LEFT JOIN dbo.FirmWideRoleDefinitions r ON r.Id=p.RequiredFirmWideRoleDefinitionId AND r.ShaleClientId=p.ShaleClientId
 WHERE p.SupersededAt IS NULL AND p.CaseDateTypePolicyKey IS NOT NULL AND p.RequiresConfirmation=1 AND (r.Id IS NULL OR r.IsActive=0 OR r.IsDeleted=1);';
END;

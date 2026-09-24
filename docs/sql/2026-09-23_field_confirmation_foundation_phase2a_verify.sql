/* READ ONLY. Run unchanged with an approved admin connection and NULL application session context. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57050,'Verification requires NULL application session context.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 57051,'Verification requires the approved all-tenant administrative principal.',1;

SELECT COUNT_BIG(*) FindingCount,N'Missing Phase 2A table' Finding
FROM(VALUES(N'FormFieldPolicyKeys'),(N'FieldConfirmationPolicies'),(N'SavedValueConfirmationRequirements'),(N'CaseDateConfirmationTargets'),(N'SavedValueConfirmations'))v(TableName)
WHERE OBJECT_ID(N'dbo.'+v.TableName,N'U') IS NULL;
SELECT CASE WHEN COL_LENGTH(N'dbo.CaseDates',N'ValueRevision') IS NULL THEN 1 ELSE 0 END FindingCount,N'CaseDates.ValueRevision missing' Finding;
SELECT COUNT_BIG(*) FindingCount,N'Invalid Case Date business revision' Finding FROM dbo.CaseDates WHERE ValueRevision<=0;
SELECT COUNT_BIG(*) FindingCount,N'Orphan/cross-tenant Case Date confirmation target' Finding
FROM dbo.CaseDateConfirmationTargets t
LEFT JOIN dbo.SavedValueConfirmationRequirements r ON r.Id=t.ConfirmationRequirementId AND r.ShaleClientId=t.ShaleClientId
LEFT JOIN dbo.CaseDates d ON d.Id=t.CaseDateId AND d.ShaleClientId=t.ShaleClientId
WHERE r.Id IS NULL OR d.Id IS NULL OR r.TargetType<>'CASE_DATE' OR r.BusinessValueRevision<>t.BusinessValueRevision;
SELECT COUNT_BIG(*) FindingCount,N'Requirement snapshot inconsistent with policy or role tenant' Finding
FROM dbo.SavedValueConfirmationRequirements r
LEFT JOIN dbo.FieldConfirmationPolicies p ON p.Id=r.FieldConfirmationPolicyId AND p.ShaleClientId=r.ShaleClientId
LEFT JOIN dbo.FirmWideRoleDefinitions role ON role.Id=r.RequiredFirmWideRoleDefinitionId AND role.ShaleClientId=r.ShaleClientId
WHERE p.Id IS NULL OR role.Id IS NULL OR r.PolicyRevisionSnapshot<>p.PolicyRevision OR r.FormKeySnapshot<>p.FormKey OR r.FieldKeySnapshot<>p.FieldKey;
SELECT COUNT_BIG(*) FindingCount,N'Confirmation relationship or snapshotted role mismatch' Finding
FROM dbo.SavedValueConfirmations c
LEFT JOIN dbo.SavedValueConfirmationRequirements r ON r.Id=c.ConfirmationRequirementId AND r.ShaleClientId=c.ShaleClientId
LEFT JOIN dbo.Users u ON u.id=c.ConfirmedByUserId AND u.ShaleClientId=c.ShaleClientId
WHERE r.Id IS NULL OR u.id IS NULL OR c.ConfirmedAsFirmWideRoleDefinitionId<>r.RequiredFirmWideRoleDefinitionId;
SELECT COUNT_BIG(*) FindingCount,N'Duplicate active policy or target revision' Finding FROM(
 SELECT ShaleClientId,FormKey,FieldKey FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL GROUP BY ShaleClientId,FormKey,FieldKey HAVING COUNT_BIG(*)>1
 UNION ALL SELECT ShaleClientId,CONVERT(varchar(64),CaseDateId),CONVERT(varchar(128),BusinessValueRevision) FROM dbo.CaseDateConfirmationTargets GROUP BY ShaleClientId,CaseDateId,BusinessValueRevision HAVING COUNT_BIG(*)>1)x;
SELECT COUNT_BIG(*) FindingCount,N'Missing or inexact strict RLS predicate' Finding FROM(VALUES
 (OBJECT_ID(N'dbo.FormFieldPolicyKeys')),(OBJECT_ID(N'dbo.FieldConfirmationPolicies')),(OBJECT_ID(N'dbo.SavedValueConfirmationRequirements')),
 (OBJECT_ID(N'dbo.CaseDateConfirmationTargets')),(OBJECT_ID(N'dbo.SavedValueConfirmations'))
)e(ObjectId) OUTER APPLY(SELECT COUNT_BIG(*) PredicateCount FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
 WHERE p.target_object_id=e.ObjectId AND p.predicate_type_desc=N'FILTER' AND sp.name=N'TenantFilter' AND sp.is_enabled=1
 AND UPPER(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N'')) IN(N'SEC.FN_FILTERBYTENANT(SHALECLIENTID)',N'(SEC.FN_FILTERBYTENANT(SHALECLIENTID))'))a
WHERE a.PredicateCount<>1;

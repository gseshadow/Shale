/*
 Read-only Phase 1A verification. This script never changes session context.

 Run on a fresh approved administrative connection with NULL ShaleClientId and
 PrincipalUserId. It has no environment-specific values to edit. Reconcile the reported
 inventory independently before treating the finding counts as deployment evidence.

 Verified production execution (2026-09-23): tenant IDs 7, 8, and 9 were visible and every
 Phase 1A finding count was zero. This records evidence; it is not an expected-count gate.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF USER_NAME() IN(N'shale_app',N'shale_runtime')
 THROW 56852,'All-tenant verification cannot use an application principal.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 56853,'All-tenant verification requires NULL tenant and principal context.',1;
IF ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1
 THROW 56854,'All-tenant verification requires the approved administrative principal.',1;

DECLARE @VisibleTenantCount bigint=(SELECT COUNT_BIG(*) FROM dbo.ShaleClients);
SELECT N'CONNECTION_AND_VISIBILITY_PREFLIGHT' SectionName,DB_NAME() DatabaseName,USER_NAME() DatabasePrincipal,
 SESSION_CONTEXT(N'ShaleClientId') ShaleClientIdContext,SESSION_CONTEXT(N'PrincipalUserId') PrincipalUserIdContext,
 @VisibleTenantCount VisibleTenantCount;
SELECT N'VISIBLE_TENANT_INVENTORY_RECONCILE_INDEPENDENTLY' SectionName,Id ShaleClientId,DisplayName,IsActive FROM dbo.ShaleClients ORDER BY Id;

/* Authoritative all-tenant results below are reachable only after the visibility gate. */
SELECT N'ALL_TENANT_SCHEMA' SectionName,
 CASE WHEN OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL THEN 1 ELSE 0 END MissingDefinitionTable,
 CASE WHEN OBJECT_ID(N'dbo.UserFirmWideRoleAssignments',N'U') IS NULL THEN 1 ELSE 0 END MissingAssignmentTable;
SELECT N'ALL_TENANT_DATA' SectionName,COUNT_BIG(*) MissingBuiltInDefinitionCount
FROM dbo.ShaleClients c CROSS JOIN (VALUES('ADMIN'),('ATTORNEY'))v(SystemKey)
WHERE NOT EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions d WHERE d.ShaleClientId=c.Id AND d.SystemKey=v.SystemKey AND d.IsActive=1 AND d.IsDeleted=0 AND d.DeletedAt IS NULL AND d.DeletedByUserId IS NULL);
SELECT N'ALL_TENANT_DATA' SectionName,COUNT_BIG(*) BuiltInAssignmentViolationCount
FROM dbo.UserFirmWideRoleAssignments a JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
WHERE d.SystemKey IN('ADMIN','ATTORNEY');
SELECT N'ALL_TENANT_DATA' SectionName,COUNT_BIG(*) CrossTenantRelationshipViolationCount
FROM dbo.UserFirmWideRoleAssignments a
LEFT JOIN dbo.Users u ON u.id=a.UserId AND u.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users creator ON creator.id=a.CreatedByUserId AND creator.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users remover ON remover.id=a.DeletedByUserId AND remover.ShaleClientId=a.ShaleClientId
WHERE u.id IS NULL OR d.Id IS NULL OR creator.id IS NULL OR (a.DeletedByUserId IS NOT NULL AND remover.id IS NULL);
SELECT N'ALL_TENANT_DATA' SectionName,COUNT_BIG(*) DuplicateActiveAssignmentCount FROM (
 SELECT ShaleClientId,UserId,FirmWideRoleDefinitionId FROM dbo.UserFirmWideRoleAssignments
 WHERE IsDeleted=0
 GROUP BY ShaleClientId,UserId,FirmWideRoleDefinitionId HAVING COUNT_BIG(*)>1
) duplicates;
SELECT N'ALL_TENANT_SCHEMA' SectionName,COUNT_BIG(*) MissingOrInexactStrictRlsPredicateCount FROM (VALUES
 (OBJECT_ID(N'dbo.FirmWideRoleDefinitions')),(OBJECT_ID(N'dbo.UserFirmWideRoleAssignments'))
) expected(ObjectId) OUTER APPLY(
 SELECT COUNT(*) PredicateCount,MIN(UPPER(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''))) PredicateDefinition
 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
 WHERE p.target_object_id=expected.ObjectId AND p.predicate_type_desc=N'FILTER' AND sp.name=N'TenantFilter' AND sp.is_enabled=1
) actual WHERE actual.PredicateCount<>1 OR actual.PredicateDefinition NOT IN(N'SEC.FN_FILTERBYTENANT(SHALECLIENTID)',N'(SEC.FN_FILTERBYTENANT(SHALECLIENTID))');

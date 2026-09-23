/* Read-only Phase 1A verification. Set ShaleClientId context before tenant-visible checks. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT CASE WHEN OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL THEN 1 ELSE 0 END AS MissingDefinitionTable,
       CASE WHEN OBJECT_ID(N'dbo.UserFirmWideRoleAssignments',N'U') IS NULL THEN 1 ELSE 0 END AS MissingAssignmentTable;
SELECT COUNT_BIG(*) AS MissingBuiltInDefinitionCount
FROM dbo.ShaleClients c CROSS JOIN (VALUES('ADMIN'),('ATTORNEY'))v(SystemKey)
WHERE NOT EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions d WHERE d.ShaleClientId=c.Id AND d.SystemKey=v.SystemKey);
SELECT COUNT_BIG(*) AS BuiltInAssignmentViolationCount
FROM dbo.UserFirmWideRoleAssignments a JOIN dbo.FirmWideRoleDefinitions d
 ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
WHERE d.SystemKey IN('ADMIN','ATTORNEY');
SELECT COUNT_BIG(*) AS CrossTenantRelationshipViolationCount
FROM dbo.UserFirmWideRoleAssignments a
LEFT JOIN dbo.Users u ON u.id=a.UserId AND u.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users creator ON creator.id=a.CreatedByUserId AND creator.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users remover ON remover.id=a.DeletedByUserId AND remover.ShaleClientId=a.ShaleClientId
WHERE u.id IS NULL OR d.Id IS NULL OR creator.id IS NULL OR (a.DeletedByUserId IS NOT NULL AND remover.id IS NULL);
SELECT COUNT_BIG(*) AS DuplicateActiveAssignmentCount FROM (
 SELECT ShaleClientId,UserId,FirmWideRoleDefinitionId FROM dbo.UserFirmWideRoleAssignments WHERE IsDeleted=0
 GROUP BY ShaleClientId,UserId,FirmWideRoleDefinitionId HAVING COUNT_BIG(*)>1
) duplicates;
SELECT COUNT_BIG(*) AS MissingStrictRlsPredicateCount FROM (VALUES
 (OBJECT_ID(N'dbo.FirmWideRoleDefinitions')),(OBJECT_ID(N'dbo.UserFirmWideRoleAssignments'))
) expected(ObjectId)
WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
 WHERE p.target_object_id=expected.ObjectId AND p.predicate_type_desc=N'FILTER' AND sp.name=N'TenantFilter' AND sp.is_enabled=1 AND p.predicate_definition LIKE N'%fn_FilterByTenant%');

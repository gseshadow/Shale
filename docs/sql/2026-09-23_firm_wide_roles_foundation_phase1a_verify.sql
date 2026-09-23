/*
 Read-only Phase 1A verification. This script never changes session context.

 ALL_TENANT (authoritative deployment verification): use a fresh approved administrative
 connection with NULL ShaleClientId and PrincipalUserId. First run with acknowledgement 0,
 reconcile VisibleTenantCount and tenant IDs against an independently approved inventory and
 inspect the exact RLS predicates/functions. Then set acknowledgement 1 and ExpectedTenantCount.

 TENANT (optional, non-authoritative): use an application-equivalent tenant-scoped session and
 set TenantScopedVerificationTenantId to that same context value. Its counts prove only that tenant.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @VerificationMode varchar(16)='ALL_TENANT'; /* ALL_TENANT or TENANT */
DECLARE @ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE';
DECLARE @ExpectedTenantCount int=0;
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
DECLARE @TenantScopedVerificationTenantId int=NULL;

IF @ExpectedDatabase=N'REPLACE_WITH_APPROVED_DATABASE' OR DB_NAME()<>@ExpectedDatabase
 THROW 56850,'Set and verify the approved database.',1;
IF @VerificationMode NOT IN('ALL_TENANT','TENANT') THROW 56851,'VerificationMode must be ALL_TENANT or TENANT.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') AND @VerificationMode='ALL_TENANT'
 THROW 56852,'All-tenant verification cannot use an application principal.',1;
IF @VerificationMode='ALL_TENANT' AND (SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL)
 THROW 56853,'All-tenant verification requires NULL tenant and principal context.',1;
IF @VerificationMode='ALL_TENANT' AND (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 56854,'All-tenant verification requires the approved administrative principal.',1;
IF @VerificationMode='TENANT' AND (TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')) IS NULL OR TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))<>@TenantScopedVerificationTenantId)
 THROW 56855,'Tenant verification requires an explicit tenant ID matching session context.',1;

DECLARE @VisibleTenantCount bigint=(SELECT COUNT_BIG(*) FROM dbo.ShaleClients);
SELECT N'CONNECTION_AND_VISIBILITY_PREFLIGHT' SectionName,DB_NAME() DatabaseName,USER_NAME() DatabasePrincipal,
 SESSION_CONTEXT(N'ShaleClientId') ShaleClientIdContext,SESSION_CONTEXT(N'PrincipalUserId') PrincipalUserIdContext,
 @VerificationMode VerificationMode,@VisibleTenantCount VisibleTenantCount,@ExpectedTenantCount ExpectedTenantCount,
 @OperatorVerifiedAllTenantVisibility OperatorAcknowledgement;
IF @VerificationMode='ALL_TENANT'
 SELECT N'VISIBLE_TENANT_INVENTORY_RECONCILE_INDEPENDENTLY' SectionName,Id ShaleClientId,DisplayName,IsActive FROM dbo.ShaleClients ORDER BY Id;

IF @VerificationMode='ALL_TENANT' AND @OperatorVerifiedAllTenantVisibility<>1
 THROW 56856,'Pass 1 complete: independently reconcile visibility, then rerun with acknowledgement 1.',1;
IF @VerificationMode='ALL_TENANT' AND (@ExpectedTenantCount<=0 OR @VisibleTenantCount<>@ExpectedTenantCount)
 THROW 56857,'Visible tenant count does not match the independently approved expected tenant count.',1;

/* Authoritative all-tenant results below are reachable only after the visibility gate. */
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_SCHEMA' ELSE N'TENANT_SCOPED_SCHEMA_NON_AUTHORITATIVE' END SectionName,
 CASE WHEN OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL THEN 1 ELSE 0 END MissingDefinitionTable,
 CASE WHEN OBJECT_ID(N'dbo.UserFirmWideRoleAssignments',N'U') IS NULL THEN 1 ELSE 0 END MissingAssignmentTable;
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_DATA' ELSE N'TENANT_SCOPED_DATA_NON_AUTHORITATIVE' END SectionName,COUNT_BIG(*) MissingBuiltInDefinitionCount
FROM dbo.ShaleClients c CROSS JOIN (VALUES('ADMIN'),('ATTORNEY'))v(SystemKey)
WHERE (@VerificationMode='ALL_TENANT' OR c.Id=@TenantScopedVerificationTenantId)
 AND NOT EXISTS(SELECT 1 FROM dbo.FirmWideRoleDefinitions d WHERE d.ShaleClientId=c.Id AND d.SystemKey=v.SystemKey AND d.IsActive=1 AND d.IsDeleted=0 AND d.DeletedAt IS NULL AND d.DeletedByUserId IS NULL);
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_DATA' ELSE N'TENANT_SCOPED_DATA_NON_AUTHORITATIVE' END SectionName,COUNT_BIG(*) BuiltInAssignmentViolationCount
FROM dbo.UserFirmWideRoleAssignments a JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
WHERE (@VerificationMode='ALL_TENANT' OR a.ShaleClientId=@TenantScopedVerificationTenantId) AND d.SystemKey IN('ADMIN','ATTORNEY');
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_DATA' ELSE N'TENANT_SCOPED_DATA_NON_AUTHORITATIVE' END SectionName,COUNT_BIG(*) CrossTenantRelationshipViolationCount
FROM dbo.UserFirmWideRoleAssignments a
LEFT JOIN dbo.Users u ON u.id=a.UserId AND u.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.FirmWideRoleDefinitions d ON d.Id=a.FirmWideRoleDefinitionId AND d.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users creator ON creator.id=a.CreatedByUserId AND creator.ShaleClientId=a.ShaleClientId
LEFT JOIN dbo.Users remover ON remover.id=a.DeletedByUserId AND remover.ShaleClientId=a.ShaleClientId
WHERE (@VerificationMode='ALL_TENANT' OR a.ShaleClientId=@TenantScopedVerificationTenantId)
 AND (u.id IS NULL OR d.Id IS NULL OR creator.id IS NULL OR (a.DeletedByUserId IS NOT NULL AND remover.id IS NULL));
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_DATA' ELSE N'TENANT_SCOPED_DATA_NON_AUTHORITATIVE' END SectionName,COUNT_BIG(*) DuplicateActiveAssignmentCount FROM (
 SELECT ShaleClientId,UserId,FirmWideRoleDefinitionId FROM dbo.UserFirmWideRoleAssignments
 WHERE IsDeleted=0 AND (@VerificationMode='ALL_TENANT' OR ShaleClientId=@TenantScopedVerificationTenantId)
 GROUP BY ShaleClientId,UserId,FirmWideRoleDefinitionId HAVING COUNT_BIG(*)>1
) duplicates;
SELECT CASE WHEN @VerificationMode='ALL_TENANT' THEN N'ALL_TENANT_SCHEMA' ELSE N'TENANT_SCOPED_SCHEMA_NON_AUTHORITATIVE' END SectionName,COUNT_BIG(*) MissingOrInexactStrictRlsPredicateCount FROM (VALUES
 (OBJECT_ID(N'dbo.FirmWideRoleDefinitions')),(OBJECT_ID(N'dbo.UserFirmWideRoleAssignments'))
) expected(ObjectId) OUTER APPLY(
 SELECT COUNT(*) PredicateCount,MIN(UPPER(REPLACE(REPLACE(REPLACE(p.predicate_definition,N'[',N''),N']',N''),N' ',N''))) PredicateDefinition
 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id
 WHERE p.target_object_id=expected.ObjectId AND p.predicate_type_desc=N'FILTER' AND sp.name=N'TenantFilter' AND sp.is_enabled=1
) actual WHERE actual.PredicateCount<>1 OR actual.PredicateDefinition NOT IN(N'SEC.FN_FILTERBYTENANT(SHALECLIENTID)',N'(SEC.FN_FILTERBYTENANT(SHALECLIENTID))');
SELECT N'OPTIONAL_TENANT_SCOPE_NOTICE' SectionName,
 CASE WHEN @VerificationMode='TENANT' THEN N'NON_AUTHORITATIVE: results cover only the explicit session tenant.' ELSE N'Not applicable; authoritative all-tenant gate passed.' END VerificationScope;

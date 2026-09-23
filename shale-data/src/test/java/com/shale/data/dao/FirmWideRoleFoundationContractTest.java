package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FirmWideRoleFoundationContractTest {
    private static final Path MIGRATION=Path.of("../docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql");
    private static final Path VERIFY=Path.of("../docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a_verify.sql");
    private static String read(Path path)throws Exception{return Files.readString(path).replace("\r\n","\n");}
    private static String dao()throws Exception{return Files.readString(Path.of("src/main/java/com/shale/data/dao/UserDao.java")).replace("\r\n","\n");}

    @Test void migrationIsForwardOnlyRerunnableTransactionalAndHasSeparateReadOnlyVerification()throws Exception{
        String sql=read(MIGRATION),verify=read(VERIFY);
        assertAll("safe deployment contract",
            ()->assertTrue(sql.contains("SET XACT_ABORT ON")),
            ()->assertTrue(sql.contains("BEGIN TRANSACTION")),
            ()->assertTrue(sql.contains("IF OBJECT_ID(N'dbo.FirmWideRoleDefinitions',N'U') IS NULL")),
            ()->assertTrue(sql.contains("IF XACT_STATE()<>0 ROLLBACK")),
            ()->assertFalse(sql.matches("(?is).*DROP\\s+TABLE.*")),
            ()->assertFalse(verify.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP)\\s+dbo\\..*")),
            ()->assertTrue(verify.contains("ViolationCount")));
    }

    @Test void allTenantExecutionFailsClosedBeforeWritesWithoutIndependentOperatorApproval()throws Exception{
        String sql=read(MIGRATION),verify=read(VERIFY);
        int gate=sql.indexOf("BEGIN TRY");
        assertAll("all-tenant execution gate",
            ()->assertTrue(sql.indexOf("@ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE'")<gate),
            ()->assertTrue(sql.indexOf("@OperatorVerifiedAllTenantVisibility bit=0")<gate),
            ()->assertTrue(sql.indexOf("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL")<gate),
            ()->assertTrue(sql.indexOf("SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL")<gate),
            ()->assertTrue(sql.indexOf("USER_NAME() IN(N'shale_app',N'shale_runtime')")<gate),
            ()->assertTrue(sql.indexOf("IS_SRVROLEMEMBER(N'sysadmin')")<gate),
            ()->assertTrue(sql.indexOf("@VisibleTenantCount<>@ExpectedTenantCount")<gate),
            ()->assertTrue(verify.contains("VISIBLE_TENANT_INVENTORY_RECONCILE_INDEPENDENTLY")),
            ()->assertTrue(verify.contains("Pass 1 complete: independently reconcile visibility")));
    }

    @Test void schemaSupportsManyRolesAndEnforcesStrictSameTenantRelationshipsAndLifecycle()throws Exception{
        String sql=read(MIGRATION);
        assertAll("tenant-safe many-role schema",
            ()->assertTrue(sql.contains("UX_UserFirmWideRoleAssignments_Active")),
            ()->assertTrue(sql.contains("FOREIGN KEY(UserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId)")),
            ()->assertTrue(sql.contains("FOREIGN KEY(FirmWideRoleDefinitionId,ShaleClientId) REFERENCES dbo.FirmWideRoleDefinitions(Id,ShaleClientId)")),
            ()->assertTrue(sql.contains("FOREIGN KEY(CreatedByUserId,ShaleClientId) REFERENCES dbo.Users(id,ShaleClientId)")),
            ()->assertTrue(sql.contains("FK_FirmWideRoleDefinitions_UpdatedByTenant")),
            ()->assertTrue(sql.contains("FK_FirmWideRoleDefinitions_DeletedByTenant")),
            ()->assertTrue(sql.contains("CK_UserFirmWideRoleAssignments_Lifecycle")),
            ()->assertTrue(sql.contains("@ExpectedForeignKeyColumns")),
            ()->assertTrue(sql.contains("@ExpectedDefaults")),
            ()->assertTrue(sql.contains("@ExpectedIndexes")),
            ()->assertEquals(2,count(sql,"ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId)")));
    }

    @Test void rerunRejectsIncompatibleObjectsAndUsesBinaryUppercaseEnforcement()throws Exception{
        String sql=read(MIGRATION);
        assertAll("closed rerun inventory",
            ()->assertTrue(sql.contains("@ExpectedColumns")),
            ()->assertTrue(sql.contains("Latin1_General_100_BIN2=UPPER(SystemKey) COLLATE Latin1_General_100_BIN2")),
            ()->assertTrue(sql.contains("trigger_events")),
            ()->assertTrue(sql.contains("OBJECT_DEFINITION(OBJECT_ID(N'dbo.TR_UserFirmWideRoleAssignments_BlockBuiltIns'))")),
            ()->assertTrue(sql.contains("PredicateCount<>1")),
            ()->assertTrue(sql.contains("SEC.FN_FILTERBYTENANT(SHALECLIENTID)")));
    }

    @Test void adminAndAttorneyRemainLegacyFlagAuthoritativeWithoutConflictingAssignments()throws Exception{
        String sql=read(MIGRATION),source=dao();
        assertAll("legacy compatibility",
            ()->assertTrue(sql.contains("backed by Users.is_admin")),
            ()->assertTrue(sql.contains("backed by Users.is_attorney")),
            ()->assertTrue(sql.contains("TR_UserFirmWideRoleAssignments_BlockBuiltIns")),
            ()->assertTrue(sql.contains("IsActive<>1 OR IsDeleted<>0 OR DeletedAt IS NOT NULL OR DeletedByUserId IS NOT NULL")),
            ()->assertTrue(source.contains("d.SystemKey='ADMIN' AND COALESCE(u.is_admin,0)=1")),
            ()->assertTrue(source.contains("d.SystemKey='ATTORNEY' AND COALESCE(u.is_attorney,0)=1")),
            ()->assertTrue(source.contains("d.SystemKey NOT IN ('ADMIN','ATTORNEY') AND a.Id IS NOT NULL")));
    }


    @Test void verificationSeparatesAuthoritativeAllTenantFromOptionalTenantScopedResults()throws Exception{
        String verify=read(VERIFY);
        assertAll("verification visibility cannot silently pass",
            ()->assertTrue(verify.contains("@VerificationMode varchar(16)='ALL_TENANT'")),
            ()->assertTrue(verify.contains("@VisibleTenantCount<>@ExpectedTenantCount")),
            ()->assertTrue(verify.contains("TENANT_SCOPED_DATA_NON_AUTHORITATIVE")),
            ()->assertTrue(verify.contains("NON_AUTHORITATIVE: results cover only the explicit session tenant.")),
            ()->assertTrue(verify.contains("TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))<>@TenantScopedVerificationTenantId")));
    }

    @Test void eligibilityUsesCurrentActorAuthoritativeLifecycleDataAndNeverCaseTeamState()throws Exception{
        String method=method(dao(),"currentActorHasFirmWideRole");
        assertAll("authoritative eligibility",
            ()->assertTrue(method.contains("verifyTenantMatchesSession")),
            ()->assertTrue(method.contains("requireCurrentPrincipalUserId")),
            ()->assertTrue(method.contains("COALESCE(u.is_deleted,0)=0")),
            ()->assertTrue(method.contains("COALESCE(u.IsRemoved,0)=0")),
            ()->assertTrue(method.contains("d.IsActive=1 AND d.IsDeleted=0")),
            ()->assertTrue(method.contains("a.IsDeleted=0")),
            ()->assertFalse(method.contains("CaseUsers")),
            ()->assertFalse(method.contains("CaseTeam")));
    }
    private static int count(String s,String needle){int n=0,p=0;while((p=s.indexOf(needle,p))>=0){n++;p+=needle.length();}return n;}
    private static String method(String s,String name){int start=s.indexOf(" "+name+"(");int brace=s.indexOf('{',start),depth=0;for(int i=brace;i<s.length();i++){char c=s.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return s.substring(start,i+1);}throw new AssertionError(name);}
}

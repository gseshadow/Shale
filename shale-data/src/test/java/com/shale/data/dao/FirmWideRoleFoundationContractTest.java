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
            ()->assertEquals(2,count(sql,"ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId)")));
    }

    @Test void adminAndAttorneyRemainLegacyFlagAuthoritativeWithoutConflictingAssignments()throws Exception{
        String sql=read(MIGRATION),source=dao();
        assertAll("legacy compatibility",
            ()->assertTrue(sql.contains("backed by Users.is_admin")),
            ()->assertTrue(sql.contains("backed by Users.is_attorney")),
            ()->assertTrue(sql.contains("TR_UserFirmWideRoleAssignments_BlockBuiltIns")),
            ()->assertTrue(source.contains("d.SystemKey='ADMIN' AND COALESCE(u.is_admin,0)=1")),
            ()->assertTrue(source.contains("d.SystemKey='ATTORNEY' AND COALESCE(u.is_attorney,0)=1")),
            ()->assertTrue(source.contains("d.SystemKey NOT IN ('ADMIN','ATTORNEY') AND a.Id IS NOT NULL")));
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

package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseSummaryUserDetailContractTest {
    private static String source(Path path) throws Exception {
        Path p=path; if(!Files.exists(p)) p=Path.of("..").resolve(path); return Files.readString(p);
    }
    private static String method(String source,String signature) {
        int start=source.indexOf(signature), open=source.indexOf('{',start), depth=0;
        assertTrue(start>=0 && open>=0,"missing "+signature);
        for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);}
        throw new AssertionError("unbalanced method");
    }

    @Test void userDetailProjectionIsBoundedTenantScopedAssignedActiveAndDeterministic() throws Exception {
        String body=method(source(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java")),
                "public List<CaseGridRow> listActiveAssignedForUserDetail");
        assertAll(
            ()->assertTrue(body.contains("verifyTenant(con, requestedTenantId)")),
            ()->assertTrue(body.contains("verifyEligibleAssignedUser(con, requestedTenantId, assignedUserId)")),
            ()->assertTrue(body.contains("SELECT TOP (?)")),
            ()->assertTrue(body.contains("c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0")),
            ()->assertTrue(body.contains("EXISTS (SELECT 1 FROM dbo.CaseUsers scope")),
            ()->assertTrue(body.contains("scope.UserId=?")),
            ()->assertTrue(body.contains("ORDER BY dates.IntakeDate DESC,c.Id DESC")),
            ()->assertTrue(body.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY")),
            ()->assertTrue(body.contains("ResponsibleAttorneyName")),
            ()->assertTrue(body.contains("PracticeAreaName")));
    }

    @Test void userDetailDatesAreAuthoritativeEffectiveNullableAndSetBased() throws Exception {
        String body=method(source(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java")),
                "public List<CaseGridRow> listActiveAssignedForUserDetail");
        String dateProjection=body.substring(body.indexOf("OUTER APPLY (SELECT\n\t\t\t\t MAX(CASE"), body.indexOf("OUTER APPLY (SELECT TOP(1) COALESCE"));
        assertAll(
            ()->assertTrue(body.contains("FROM dbo.CaseDates")),
            ()->assertTrue(body.contains("CaseDateTypeSemanticRoleMappings")),
            ()->assertTrue(body.contains("m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL")),
            ()->assertTrue(body.contains("CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC")),
            ()->assertTrue(body.contains("t.SystemKey='date_of_injury'")),
            ()->assertTrue(body.contains("cd.IsDeleted=0")),
            ()->assertTrue(body.contains("localDate(rs,\"IntakeDate\")")),
            ()->assertEquals(1,body.lines().filter(line->line.contains("executeQuery()")).count()),
            ()->assertFalse(body.contains("c.CallerDate")),
            ()->assertFalse(body.contains("c.DateOfInjury")),
            ()->assertFalse(body.contains("c.StatuteOfLimitations")),
            ()->assertFalse(body.contains("c.TortNoticeDeadline")),
            ()->assertFalse(body.contains("FeeAgreementSigned")),
            ()->assertTrue(body.contains("c.NonEngagementLetterSent")),
            ()->assertFalse(dateProjection.contains("NonEngagementLetterSent")));
    }

    @Test void removedLegacyDaoHasNoCallerOrDefinition() throws Exception {
        String all=source(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"))+"\n"
                +source(Path.of("shale-ui/src/main/java/com/shale/ui/services/UserDetailService.java"));
        assertFalse(all.contains("listActiveCasesForUserTeamMember"));
    }
}

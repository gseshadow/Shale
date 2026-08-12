package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseSummaryServerProjectionContractTest {
    private static String source() throws Exception {
        Path p=Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java");
        if(!Files.exists(p)) p=Path.of("shale-data").resolve(p);
        return Files.readString(p);
    }
    private static String method(String source,String name,String next) {
        int start=source.indexOf(name); assertTrue(start>=0,name);
        int end=source.indexOf(next,start); assertTrue(end>start,next);
        return source.substring(start,end);
    }
    @Test void serverProjectionIsBoundedTenantScopedAndAuthoritative() throws Exception {
        String body=method(source(),"private List<ServerCaseRow> listActiveForServer","Admin Deleted Cases search");
        assertAll(
            ()->assertTrue(body.contains("FROM dbo.CaseDates")),
            ()->assertTrue(body.contains("CaseDateTypeSemanticRoleMappings")),
            ()->assertTrue(body.contains("m.ShaleClientId=c.ShaleClientId")),
            ()->assertTrue(body.contains("m.ShaleClientId IS NULL")),
            ()->assertTrue(body.contains("m.Id DESC")),
            ()->assertTrue(body.contains("cd.IsDeleted=0")),
            ()->assertTrue(body.contains("ISNULL(c.IsDeleted,0)=0")),
            ()->assertTrue(body.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")),
            ()->assertTrue(body.contains("c.Id ASC")),
            ()->assertFalse(body.contains("c.CallerDate")),
            ()->assertFalse(body.contains("c.DateOfInjury")),
            ()->assertFalse(body.contains("c.StatuteOfLimitations")),
            ()->assertFalse(body.contains("c.TortNoticeDeadline")),
            ()->assertFalse(body.contains("FeeAgreementSigned")),
            ()->assertFalse(body.contains("NonEngagementLetterSent")));
    }
    @Test void adapterHasNoOverviewHydrationOrLegacySearchGateway() throws Exception {
        Path p=Path.of("src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java");
        if(!Files.exists(p)) p=Path.of("shale-data").resolve(p);
        String s=Files.readString(p);
        String search=method(s,"public List<CaseOverviewDto> searchCases","public List<CaseOverviewDto> listAssignedCases");
        String assigned=method(s,"public List<CaseOverviewDto> listAssignedCases","private static CaseOverviewDto toOverview");
        assertFalse(search.contains("getOverview")); assertFalse(assigned.contains("getOverview"));
        assertFalse(s.contains("searchCasesByName")); assertFalse(s.contains("listAssignedCasesForBoard"));
    }
}

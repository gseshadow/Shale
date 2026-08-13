package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class CaseDatesFinalRuntimeCleanupContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void deadCompatibilityMethodsHaveNoRuntimeSource() throws Exception {
        String cases = read("src/main/java/com/shale/data/dao/CaseDao.java");
        String contacts = read("src/main/java/com/shale/data/dao/ContactDao.java");
        String adapter = read("src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java");
        for (String signature : new String[] {"createBasicCase(", "insertBasicCase(Connection", "updateCase(",
                "updateCaseDetails(", "findMyCasesPage(", "listCaseStatusReport(", "listCaseStatusReportCases("})
            assertFalse(cases.contains(signature), signature);
        assertFalse(contacts.contains("findRelatedCases("));
        assertFalse(adapter.contains("CaseDetailDto updateCase("));
        assertFalse(adapter.contains("createBasicCase("));
    }

    @Test void everyBroadCasePageUsesAuthoritativeCaseDateProjection() throws Exception {
        String source = read("src/main/java/com/shale/data/dao/CaseDao.java");
        String paging = source.substring(source.indexOf("private PagedResult<CaseRow> findPageInternal"),
                source.indexOf("public long countAll()"));
        assertTrue(paging.contains("authoritativeBoundaryDateApplySql()"));
        assertTrue(paging.contains("dbo.CaseDates"));
        assertFalse(paging.contains("c.CallerDate"));
        assertFalse(paging.contains("c.StatuteOfLimitations"));
        assertFalse(paging.contains("c.DateOfInjury"));
        assertFalse(paging.contains("c.TortNoticeDeadline"));
    }
}

package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseSummaryCalendarContractTest {
    @Test void calendarCasesReuseTheSharedProjectionAndTrustedTenantBoundary() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
        String calendar = source.substring(source.indexOf("public List<CalendarCaseRow> listActiveForCalendar"),
                source.indexOf("private static String summarySelectSql"));
        assertTrue(calendar.contains("verifyTenant(con, requestedTenantId)"));
        assertTrue(calendar.contains("ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(calendar.contains("c.Id = ?"));
        assertTrue(calendar.contains("summarySelectSql"));
        assertTrue(calendar.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
        assertTrue(calendar.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
        assertTrue(calendar.contains("LOWER(COALESCE(c.Name, '')) ASC, c.Id ASC"));
        assertFalse(calendar.matches("(?s).*RoleId\\s*=\\s*[0-9]+.*"));
    }

    @Test void legacyCalendarCaseHydrationWasRemovedWithoutChangingEventQueries() throws Exception {
        String feed = Files.readString(Path.of("src/main/java/com/shale/data/dao/CalendarFeedDao.java"));
        String controller = Files.readString(Path.of("../shale-ui/src/main/java/com/shale/ui/controller/CalendarController.java"));
        assertFalse(feed.contains("CalendarCaseCardRow"));
        assertFalse(feed.contains("listCaseCardRows"));
        assertTrue(controller.contains("caseSummaryDao.findActiveForCalendar"));
        assertTrue(controller.contains("caseSummaryDao.listActiveForCalendar"));
        assertTrue(feed.contains("ORDER BY StartsAt ASC, AllDay DESC, KeyValue ASC"));
    }
}

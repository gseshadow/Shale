package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Source-level portability guard for the SQL Server integration exercised in deployment tests. */
class CaseCalendarSynchronizationContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/" + name));
    }

    @Test void bothDirectionsAreMappingGatedAndDoNotRecurse() throws Exception {
        String s=source("CaseCalendarSynchronizer.java");
        assertAll(() -> assertTrue(s.contains("CaseDateToCalendar=?")),
                () -> assertTrue(s.contains("CalendarToCaseDate=?")),
                () -> assertTrue(s.contains("IsActive=1")),
                () -> assertFalse(s.contains("new CaseDateDao")),
                () -> assertFalse(s.contains("new CalendarEventDao")));
    }
    @Test void linkIsAuthoritativeAndHistoricalRowsAreNotMatched() throws Exception {
        String s=source("CaseCalendarSynchronizer.java");
        assertAll(() -> assertTrue(s.contains("CaseDateId=?")),
                () -> assertTrue(s.contains("CaseDateId IS NULL")),
                () -> assertFalse(s.toLowerCase().contains(" title like ")),
                () -> assertFalse(s.toLowerCase().contains(" description like ")));
    }
    @Test void projectionPreservesEntitySpecificFieldsAndUsesConcurrency() throws Exception {
        String s=source("CaseCalendarSynchronizer.java");
        assertAll(() -> assertTrue(s.contains("StartsAt=?,EndsAt=?,AllDay=?")),
                () -> assertTrue(s.contains("RowVer=?")),
                () -> assertFalse(s.contains("SET Title=?")),
                () -> assertFalse(s.contains("SET Description=?")),
                () -> assertFalse(s.contains("SET Notes=?")));
    }
    @Test void lifecycleTenantCaseAuditAndAtomicityContractsAreExplicit() throws Exception {
        String s=source("CaseCalendarSynchronizer.java");
        String dates=source("CaseDateDao.java"), events=source("CalendarEventDao.java");
        assertAll(() -> assertTrue(s.contains("IsCancelled=?")), () -> assertTrue(s.contains("IsDeleted=?")),
                () -> assertTrue(s.contains("Linked records must belong to the same Case")),
                () -> assertTrue(s.contains("SYNCHRONIZATION_DIRECTION")),
                () -> assertTrue(dates.contains("caseCalendarSynchronizer.fromCaseDate(con")),
                () -> assertTrue(events.contains("caseCalendarSynchronizer.fromCalendar(con")),
                () -> assertTrue(events.contains("con.rollback()")), () -> assertTrue(events.contains("con.commit()")));
    }
}

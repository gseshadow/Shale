package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Negative-boundary guards for the retired Calendar/Case Date runtime synchronization. */
class CaseCalendarSynchronizationRetirementContractTest {
    private static String dao(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/" + name));
    }

    @Test void caseDateMutationPathsCannotWriteCalendarEventsAndKeepTheirOwnAuditTransaction() throws Exception {
        String source = dao("CaseDateDao.java");
        for (String method : new String[] { "createCaseDate", "updateCaseDate", "mutateDeleted" }) {
            String body = method(source, method);
            assertAll(method,
                    () -> assertFalse(body.contains("CalendarEvents")),
                    () -> assertFalse(body.contains("CaseDateId")),
                    () -> assertEquals(1, occurrences(body, "audit(con,")),
                    () -> assertTrue(body.contains("con.commit()")),
                    () -> assertTrue(body.contains("con.rollback()")));
        }
        assertFalse(source.contains("CaseCalendarSynchronizer"));
    }

    @Test void calendarMutationPathsCannotWriteCaseDatesAndKeepTheirOwnAuditTransaction() throws Exception {
        String source = dao("CalendarEventDao.java");
        for (String method : new String[] { "create", "update", "deleteCalendarEvent" }) {
            String body = method(source, method);
            assertAll(method,
                    () -> assertFalse(body.contains("CaseDates")),
                    () -> assertFalse(body.contains("CaseDateId")),
                    () -> assertEquals(1, occurrences(body, "auditCalendar(con,")),
                    () -> assertTrue(body.contains("con.commit()")),
                    () -> assertTrue(body.contains("con.rollback()")));
        }
        assertFalse(source.contains("CaseCalendarSynchronizer"));
    }

    @Test void existingLinksRemainReadableButAreNotPairingIdentity() throws Exception {
        String eventDao = dao("CalendarEventDao.java");
        String feedDao = dao("CalendarFeedDao.java");
        assertAll(
                () -> assertTrue(eventDao.contains("FROM dbo.CalendarEvents")),
                () -> assertFalse(eventDao.contains("CaseDateId IS NULL")),
                () -> assertTrue(feedDao.contains("CONCAT('CASE_DATE:', CAST(cd.Id AS varchar(20)))")),
                () -> assertTrue(feedDao.contains("FROM dbo.CaseDates cd")));
    }

    @Test void noPresentationValueOrTypeNamePairingRemains() throws Exception {
        String production = dao("CaseDateDao.java") + dao("CalendarEventDao.java") + dao("CalendarFeedDao.java");
        String lower = production.toLowerCase();
        assertAll(
                () -> assertFalse(forbiddenPairing(lower, "title\\s+like")),
                () -> assertFalse(forbiddenPairing(lower, "(?:label|name)\\s+like")),
                () -> assertFalse(forbiddenPairing(lower, "startsat\\s*=")),
                () -> assertFalse(production.contains("CalendarCaseDateTypeMappings")));
    }

    private static boolean forbiddenPairing(String source, String presentationOrDatePredicate) {
        return Pattern.compile("casedateid\\s+is\\s+null.{0,500}" + presentationOrDatePredicate,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(source).find();
    }

    private static String method(String source, String name) {
        Pattern declaration = Pattern.compile("(?:public|protected|private)\\s+[^;{}]*?\\b" + Pattern.quote(name)
                + "\\s*\\([^;{}]*\\)\\s*\\{");
        Matcher matcher = declaration.matcher(source);
        assertTrue(matcher.find(), "Missing method declaration " + name);
        int open = source.indexOf('{', matcher.start());
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        return fail("Unclosed method " + name);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}

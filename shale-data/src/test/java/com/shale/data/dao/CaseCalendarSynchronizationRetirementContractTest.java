package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Negative-boundary guards for the retired Calendar/Case Date runtime synchronization. */
class CaseCalendarSynchronizationRetirementContractTest {
    private static String dao(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/" + name));
    }

    private static String productionJava() throws Exception {
        Path repository = Path.of("..");
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(repository, 20)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append('\n').append(path).append('\n').append(Files.readString(path));
            }
        }
        return source.toString();
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

    @Test void productionCannotReintroduceRetiredSchemaOrSynchronizationVocabulary() throws Exception {
        String production = productionJava();
        assertAll(
                () -> assertFalse(production.contains("CalendarCaseDateTypeMappings"), "mapping-table access is retired"),
                () -> assertFalse(Pattern.compile("CalendarEvents.{0,300}CaseDateId|CaseDateId.{0,300}CalendarEvents",
                        Pattern.DOTALL).matcher(production).find(), "CalendarEvents.CaseDateId access is retired"),
                () -> assertFalse(Pattern.compile("CaseCalendarSynchron(?:izer|ization)|synchroniz(?:e|ation).{0,80}(?:CaseDate|CalendarEvent)",
                        Pattern.CASE_INSENSITIVE).matcher(production).find(), "synchronization classes and calls are retired"),
                () -> assertFalse(production.contains("CALENDAR_CASE_DATE_TYPE_MAPPING")),
                () -> assertFalse(production.contains("CASE_DATE_TO_CALENDAR")),
                () -> assertFalse(production.contains("CALENDAR_TO_CASE_DATE")),
                () -> assertFalse(production.contains("SYNCHRONIZATION_DIRECTION")));
    }

    @Test void productionCannotPairCalendarEventsAndCaseDatesByPresentationOrDateValues() throws Exception {
        String production = productionJava();
        Pattern crossDomainPairing = Pattern.compile(
                "(?is)(?:CalendarEvents.{0,1200}CaseDates|CaseDates.{0,1200}CalendarEvents).{0,600}"
                        + "(?:Title|Name|Label|StartsAt|EndsAt|CalendarEventTypeId|CaseDateTypeId)\\s*=\\s*[^,;\\n]+\\."
                        + "(?:Title|Name|Label|StartsAt|EndsAt|CalendarEventTypeId|CaseDateTypeId)");
        assertFalse(crossDomainPairing.matcher(production).find(),
                "Calendar Event and Case Date identity must never be inferred from title/name/date/type-label pairs");
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

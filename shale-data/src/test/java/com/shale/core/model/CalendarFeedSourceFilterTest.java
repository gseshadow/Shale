package com.shale.core.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarFeedSourceFilterTest {
    @Test
    void classifierUsesStableFeedMetadata() {
        assertEquals(CalendarFeedCategory.CALENDAR_EVENTS, CalendarFeedCategory.classify(item("EVENT:42", null, null, null, "MANUAL", "Visit", 1, "MEETING")));
        assertEquals(CalendarFeedCategory.TASKS, CalendarFeedCategory.classify(item("TASK:7", "DueAt", 7, 10, "PROJECTED", "Draft", 1, "TASK_DUE")));
        assertDeadline("StatuteOfLimitations");
        assertDeadline("TortNoticeDeadline");
        assertDeadline("DiscoveryDeadline");
        assertCaseDate("CallerDate");
        assertCaseDate("AcceptedDate");
        assertCaseDate("DeniedDate");
        assertCaseDate("ClosedDate");
        assertCaseDate("DateOfInjury");
        assertCaseDate("DateFeeAgreementSigned");
        assertCaseDate("DateNonEngagementLetterSent");
        assertCaseDate("DateOfMedicalNegligence");
        assertCaseDate("DateMedicalNegligenceWasDiscovered");
    }

    @Test
    void defaultFilterShowsEventsTasksAndDeadlinesButHidesOtherCaseDates() {
        CalendarFeedSourceFilter filter = CalendarFeedSourceFilter.defaults();
        assertTrue(filter.isEnabled(CalendarFeedCategory.CALENDAR_EVENTS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.TASKS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.CASE_DEADLINES));
        assertFalse(filter.isEnabled(CalendarFeedCategory.OTHER_CASE_DATES));
        assertTrue(filter.matches(item("EVENT:1", null, null, null, "MANUAL", "Event", 1, "MEETING")));
        assertTrue(filter.matches(item("TASK:1", "DueAt", 1, 1, "PROJECTED", "Task", 1, "TASK_DUE")));
        assertTrue(filter.matches(item("CASE_SOL:1", "StatuteOfLimitations", null, 1, "PROJECTED", "SOL", 1, "STATUTE_OF_LIMITATIONS")));
        assertFalse(filter.matches(item("CASE_CALLER:1", "CallerDate", null, 1, "PROJECTED", "Intake", 1, "CASE_DATE")));
    }

    @Test
    void disabledCategoriesHideOnlyTheirLayerAndEnabledCategoriesUseOrSemantics() {
        List<CalendarFeedItem> items = List.of(
                item("EVENT:1", null, null, null, "MANUAL", "Alpha", 1, "MEETING"),
                item("TASK:1", "DueAt", 1, 1, "PROJECTED", "Beta", 1, "TASK_DUE"),
                item("CASE_SOL:1", "StatuteOfLimitations", null, 1, "PROJECTED", "Gamma", 1, "STATUTE_OF_LIMITATIONS"),
                item("CASE_TORT:1", "TortNoticeDeadline", null, 1, "PROJECTED", "Delta", 1, "TORT_NOTICE_DEADLINE"),
                item("CASE_DISC:1", "DiscoveryDeadline", null, 1, "PROJECTED", "Epsilon", 1, "DISCOVERY_DEADLINE"),
                item("CASE_CALLER:1", "CallerDate", null, 1, "PROJECTED", "Zeta", 1, "CASE_DATE"));

        assertEquals(List.of("EVENT:1", "CASE_SOL:1", "CASE_TORT:1", "CASE_DISC:1", "CASE_CALLER:1"), keysMatching(items, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS, CalendarFeedCategory.CASE_DEADLINES, CalendarFeedCategory.OTHER_CASE_DATES))));
        assertEquals(List.of("EVENT:1", "TASK:1", "CASE_CALLER:1"), keysMatching(items, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS, CalendarFeedCategory.TASKS, CalendarFeedCategory.OTHER_CASE_DATES))));
        assertEquals(List.of("EVENT:1", "TASK:1", "CASE_SOL:1", "CASE_TORT:1", "CASE_DISC:1"), keysMatching(items, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS, CalendarFeedCategory.TASKS, CalendarFeedCategory.CASE_DEADLINES))));
        assertEquals(List.of("TASK:1", "CASE_SOL:1", "CASE_TORT:1", "CASE_DISC:1", "CASE_CALLER:1"), keysMatching(items, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.TASKS, CalendarFeedCategory.CASE_DEADLINES, CalendarFeedCategory.OTHER_CASE_DATES))));
        assertEquals(List.of("EVENT:1", "CASE_SOL:1", "CASE_TORT:1", "CASE_DISC:1"), keysMatching(items, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS, CalendarFeedCategory.CASE_DEADLINES))));
    }


    @Test
    void sourceFilteringCombinesWithSearchCaseAndEventTypeUsingAndSemantics() {
        CalendarFeedItem event = item("EVENT:1", null, null, 7, "MANUAL", "Alpha deposition", 7, "MEETING");
        CalendarFeedSourceFilter eventsOnly = new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS));
        assertTrue(CalendarFeedFilters.matches(event, eventsOnly, "deposition", 7, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(event, eventsOnly, "missing", 7, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(event, eventsOnly, "deposition", 8, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(event, eventsOnly, "deposition", 7, "TASK_DUE"));
        assertFalse(CalendarFeedFilters.matches(event, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.TASKS)), "deposition", 7, "MEETING"));

        CalendarFeedItem task = item("TASK:1", "DueAt", 1, 7, "PROJECTED", "Alpha task", 7, "TASK_DUE");
        assertFalse(CalendarFeedFilters.matches(task, eventsOnly, "Alpha", 7, "TASK_DUE"));
    }

    @Test
    void allDisabledStateMatchesNoItems() {
        CalendarFeedSourceFilter filter = CalendarFeedSourceFilter.allDisabled();
        assertFalse(filter.hasAnyEnabled());
        assertFalse(filter.matches(item("EVENT:1", null, null, null, "MANUAL", "Alpha", 1, "MEETING")));
    }

    private static List<String> keysMatching(List<CalendarFeedItem> items, CalendarFeedSourceFilter filter) {
        return items.stream().filter(filter::matches).map(CalendarFeedItem::key).toList();
    }
    private static void assertDeadline(String sourceField) { assertEquals(CalendarFeedCategory.CASE_DEADLINES, CalendarFeedCategory.classify(item("CASE:" + sourceField, sourceField, null, 1, "PROJECTED", sourceField, 1, sourceField))); }
    private static void assertCaseDate(String sourceField) { assertEquals(CalendarFeedCategory.OTHER_CASE_DATES, CalendarFeedCategory.classify(item("CASE:" + sourceField, sourceField, null, 1, "PROJECTED", sourceField, 1, "CASE_DATE"))); }
    private static CalendarFeedItem item(String key, String sourceField, Integer taskId, Integer caseId, String sourceType, String title, Integer relatedCaseId, String typeKey) {
        return new CalendarFeedItem(key, title, LocalDateTime.of(2026, 7, 10, 9, 0), null, true, sourceType, sourceField, caseId == null ? relatedCaseId : caseId, taskId, "Related", typeKey, typeKey, null, null);
    }
}

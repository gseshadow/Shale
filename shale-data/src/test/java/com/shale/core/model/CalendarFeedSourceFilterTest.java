package com.shale.core.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Arrays;

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
        assertEquals(CalendarFeedCategory.CASE_DEADLINES, CalendarFeedCategory.classify(item("CASE_DATE:900", "DEADLINE", null, 1, "CASE_DATE", "SOL", 1, "CASE_DATE_DEADLINE")));
        assertEquals(CalendarFeedCategory.OTHER_CASE_DATES, CalendarFeedCategory.classify(item("CASE_DATE:901", "HEARING", null, 1, "CASE_DATE", "Hearing", 1, "CASE_DATE_HEARING")));
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
    void caseCalendarDefaultFilterShowsEventsTasksDeadlinesAndCaseDates() {
        CalendarFeedSourceFilter filter = CalendarFeedSourceFilter.caseCalendarDefaults();
        assertTrue(filter.isEnabled(CalendarFeedCategory.CALENDAR_EVENTS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.TASKS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.CASE_DEADLINES));
        assertTrue(filter.isEnabled(CalendarFeedCategory.OTHER_CASE_DATES));
        assertTrue(filter.matches(item("CASE_CALLER:1", "CallerDate", null, 1, "PROJECTED", "Intake", 1, "CASE_DATE")));
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
    void caseOptionsUseAuthoritativeCaseNameAndIgnoreSourceSpecificTitles() {
        List<CalendarFeedItem> items = List.of(
                itemWithCaseName("TASK:1", "Call", "DueAt", 1, 42, "PROJECTED", "Smith v Jones", "TASK_DUE"),
                itemWithCaseName("EVENT:2", "Event title", null, null, 43, "MANUAL", "Alpha Matter", "MEETING"),
                itemWithCaseName("CASE_SOL:42", "SOL — Smith v Jones", "StatuteOfLimitations", null, 42, "PROJECTED", "Smith v Jones", "STATUTE_OF_LIMITATIONS"),
                itemWithCaseName("CASE_CALLER:42", "Intake — Smith v Jones", "CallerDate", null, 42, "PROJECTED", "Smith v Jones", "CASE_DATE"),
                itemWithCaseName("EVENT:3", "No case event", null, null, null, "MANUAL", null, "MEETING"));

        List<CalendarCaseFilterOptions.CaseOption> options = CalendarCaseFilterOptions.fromFeedItems(items);

        assertEquals("All cases", options.getFirst().displayName());
        assertNull(options.getFirst().caseId());
        assertEquals(List.of(43, 42), options.stream().filter(option -> !option.isAll()).map(CalendarCaseFilterOptions.CaseOption::caseId).toList());
        assertEquals(List.of("Alpha Matter", "Smith v Jones"), options.stream().filter(option -> !option.isAll()).map(CalendarCaseFilterOptions.CaseOption::displayName).toList());
        assertFalse(options.stream().anyMatch(option -> "Call".equals(option.displayName())));
        assertFalse(options.stream().anyMatch(option -> "Event title".equals(option.displayName())));
        assertFalse(options.stream().anyMatch(option -> "SOL — Smith v Jones".equals(option.displayName())));
    }

    @Test
    void caseOptionsDeduplicateByCaseIdKeepDuplicateNamesDistinctAndSortByCaseNameThenId() {
        List<CalendarFeedItem> items = List.of(
                itemWithCaseName("TASK:1", "Call", "DueAt", 1, 20, "PROJECTED", "Zephyr", "TASK_DUE"),
                itemWithCaseName("EVENT:2", "Review", null, null, 10, "MANUAL", "Acme", "MEETING"),
                itemWithCaseName("CASE_SOL:10", "SOL — Acme", "StatuteOfLimitations", null, 10, "PROJECTED", "Acme", "STATUTE_OF_LIMITATIONS"),
                itemWithCaseName("CASE_CALLER:12", "Intake — Acme", "CallerDate", null, 12, "PROJECTED", "Acme", "CASE_DATE"),
                itemWithCaseName("TASK:3", "Missing case name", "DueAt", 3, 30, "PROJECTED", null, "TASK_DUE"),
                itemWithCaseName("EVENT:4", "No case", null, null, null, "MANUAL", null, "MEETING"));

        List<CalendarCaseFilterOptions.CaseOption> options = CalendarCaseFilterOptions.fromFeedItems(items);

        assertEquals(Arrays.asList(null, 10, 12, 20), options.stream().map(CalendarCaseFilterOptions.CaseOption::caseId).toList());
        assertEquals(List.of("All cases", "Acme", "Acme", "Zephyr"), options.stream().map(CalendarCaseFilterOptions.CaseOption::displayName).toList());
    }

    @Test
    void selectingCaseStillMatchesAllSourceTypesForThatCase() {
        CalendarFeedSourceFilter allLayers = new CalendarFeedSourceFilter(EnumSet.allOf(CalendarFeedCategory.class));
        assertTrue(CalendarFeedFilters.matches(itemWithCaseName("EVENT:1", "Event", null, null, 7, "MANUAL", "Case Seven", "MEETING"), allLayers, "", 7, ""));
        assertTrue(CalendarFeedFilters.matches(itemWithCaseName("TASK:1", "Task", "DueAt", 1, 7, "PROJECTED", "Case Seven", "TASK_DUE"), allLayers, "", 7, ""));
        assertTrue(CalendarFeedFilters.matches(itemWithCaseName("CASE_SOL:7", "SOL", "StatuteOfLimitations", null, 7, "PROJECTED", "Case Seven", "STATUTE_OF_LIMITATIONS"), allLayers, "", 7, ""));
        assertTrue(CalendarFeedFilters.matches(itemWithCaseName("CASE_CALLER:7", "Intake", "CallerDate", null, 7, "PROJECTED", "Case Seven", "CASE_DATE"), allLayers, "", 7, ""));
        assertFalse(CalendarFeedFilters.matches(itemWithCaseName("EVENT:2", "Other", null, null, 8, "MANUAL", "Case Eight", "MEETING"), allLayers, "", 7, ""));
        assertFalse(CalendarFeedFilters.matches(itemWithCaseName("EVENT:3", "No case", null, null, null, "MANUAL", null, "MEETING"), allLayers, "", 7, ""));
    }


    @Test
    void clickTargetsRouteProjectedTasksEventsAndCasesByStableIdentity() {
        CalendarFeedClickTarget projectedTask = CalendarFeedClickTarget.resolve(itemWithCaseName("TASK:987", "Task", "DueAt", 987, 7, "PROJECTED", "Case Seven", "TASK_DUE"));
        assertEquals(CalendarFeedClickTarget.Kind.TASK, projectedTask.kind());
        assertEquals(987L, projectedTask.id());

        CalendarFeedClickTarget persistedEventWithTask = CalendarFeedClickTarget.resolve(itemWithCaseName("EVENT:123", "Scheduled task follow-up", null, 987, 7, "MANUAL", "Case Seven", "MEETING"));
        assertEquals(CalendarFeedClickTarget.Kind.CALENDAR_EVENT, persistedEventWithTask.kind());
        assertEquals(123L, persistedEventWithTask.id());

        CalendarFeedClickTarget caseDate = CalendarFeedClickTarget.resolve(itemWithCaseName("CASE_SOL:7", "SOL", "StatuteOfLimitations", null, 7, "PROJECTED", "Case Seven", "STATUTE_OF_LIMITATIONS"));
        assertEquals(CalendarFeedClickTarget.Kind.CASE, caseDate.kind());
        assertEquals(7L, caseDate.id());

        CalendarFeedClickTarget authoritativeCaseDate = CalendarFeedClickTarget.resolve(itemWithCaseName("CASE_DATE:555", "SOL", "DEADLINE", null, 7, "CASE_DATE", "Case Seven", "CASE_DATE_DEADLINE"));
        assertEquals(CalendarFeedClickTarget.Kind.CASE_DATES, authoritativeCaseDate.kind());
        assertEquals(7L, authoritativeCaseDate.id());

        assertFalse(CalendarFeedClickTarget.resolve(itemWithCaseName("BROKEN", "Other", null, null, null, "PROJECTED", null, "OTHER")).actionable());
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
        Integer effectiveCaseId = caseId == null ? relatedCaseId : caseId;
        return itemWithCaseName(key, title, sourceField, taskId, effectiveCaseId, sourceType, effectiveCaseId == null ? null : "Case " + effectiveCaseId, typeKey);
    }

    private static CalendarFeedItem itemWithCaseName(String key, String title, String sourceField, Integer taskId, Integer caseId, String sourceType, String caseName, String typeKey) {
        return new CalendarFeedItem(key, title, null, LocalDateTime.of(2026, 7, 10, 9, 0), null, true, sourceType, sourceField, caseId, caseName, taskId, "Related", typeKey, typeKey, null, null, null, null);
    }
}

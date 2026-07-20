package com.shale.core.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CalendarOverlaySelectionTest {
    @Test void projectedItemsClassifyAsSharedCalendar() {
        assertTrue(CalendarOverlayClassifier.classify(item("TASK:7", "DueAt", 7, 12, null)).sharedCalendar());
        assertTrue(CalendarOverlayClassifier.classify(item("CASE_SOL:1", "StatuteOfLimitations", null, 1, null)).sharedCalendar());
        assertTrue(CalendarOverlayClassifier.classify(item("CASE_CALLER:1", "CallerDate", null, 1, null)).sharedCalendar());
    }

    @Test void persistedEventOwnershipUsesAssignedToUserIdOnly() {
        assertTrue(CalendarOverlayClassifier.classify(item("EVENT:1", null, null, null, null)).sharedCalendar());
        assertEquals(CalendarFeedCalendarOwner.user(42), CalendarOverlayClassifier.classify(item("EVENT:2", null, null, null, 42)));
        assertEquals(CalendarFeedCalendarOwner.user(42), CalendarOverlayClassifier.classify(item("EVENT:3", null, null, null, 42)));
    }

    @Test void defaultOverlayStateEnablesSharedAndCurrentUserOnly() {
        CalendarOverlaySelection selection = CalendarOverlaySelection.defaults(10);
        assertTrue(selection.sharedEnabled());
        assertEquals(Set.of(10), selection.enabledUserIds());
        CalendarOverlaySelection missingUser = CalendarOverlaySelection.defaults(null);
        assertTrue(missingUser.sharedEnabled());
        assertTrue(missingUser.enabledUserIds().isEmpty());
    }

    @Test void selectedCalendarsUseOrSemanticsAndNoCalendarsShowsNothing() {
        CalendarFeedItem shared = item("EVENT:1", null, null, null, null);
        CalendarFeedItem mine = item("EVENT:2", null, null, null, 10);
        CalendarFeedItem other = item("EVENT:3", null, null, null, 11);
        CalendarOverlaySelection sharedAndOther = new CalendarOverlaySelection(true, Set.of(11));
        assertTrue(sharedAndOther.matches(shared));
        assertFalse(sharedAndOther.matches(mine));
        assertTrue(sharedAndOther.matches(other));
        assertFalse(new CalendarOverlaySelection(false, Set.of()).matches(shared));
    }

    @Test void overlaysCombineWithSourceLayerAndSearchCaseTypeFiltersUsingAnd() {
        CalendarFeedItem mine = item("EVENT:2", null, null, 99, 10);
        CalendarOverlaySelection mineOnly = new CalendarOverlaySelection(false, Set.of(10));
        assertTrue(mineOnly.matches(mine));
        assertTrue(CalendarFeedFilters.matches(mine, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS)), "Title", 99, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(mine, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.TASKS)), "Title", 99, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(mine, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS)), "missing", 99, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(mine, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS)), "Title", 100, "MEETING"));
        assertFalse(CalendarFeedFilters.matches(mine, new CalendarFeedSourceFilter(EnumSet.of(CalendarFeedCategory.CALENDAR_EVENTS)), "Title", 99, "CALL"));
    }

    private static CalendarFeedItem item(String key, String sourceField, Integer taskId, Integer caseId, Integer assignedToUserId) {
        return new CalendarFeedItem(key, "Title", null, LocalDateTime.of(2026, 7, 10, 9, 0), null, true,
                key.startsWith("EVENT:") ? "MANUAL" : "PROJECTED", sourceField, caseId, "Case", taskId,
                "Related", key.startsWith("EVENT:") ? "MEETING" : "CASE_DATE", key.startsWith("EVENT:") ? "Meeting" : "Case Date",
                null, null, assignedToUserId, "User");
    }
}

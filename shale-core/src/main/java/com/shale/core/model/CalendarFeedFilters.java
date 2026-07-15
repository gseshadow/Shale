package com.shale.core.model;

import java.util.Locale;
import java.util.Objects;

public final class CalendarFeedFilters {
    private CalendarFeedFilters() {}

    public static boolean matches(
            CalendarFeedItem item,
            CalendarFeedSourceFilter sourceFilter,
            String searchText,
            Integer caseId,
            String eventTypeKey) {
        if (item == null) return false;
        CalendarFeedSourceFilter activeSourceFilter = sourceFilter == null ? CalendarFeedSourceFilter.defaults() : sourceFilter;
        if (!activeSourceFilter.matches(item)) return false;
        if (caseId != null && !Objects.equals(item.caseId(), caseId)) return false;
        String activeTypeKey = safe(eventTypeKey);
        if (!activeTypeKey.isBlank() && !eventTypeMatches(item, activeTypeKey)) return false;
        String search = safe(searchText).trim().toLowerCase(Locale.ROOT);
        if (search.isBlank()) return true;
        return containsIgnoreCase(item.title(), search)
                || containsIgnoreCase(item.relatedDisplayName(), search)
                || containsIgnoreCase(item.displayTypeName(), search)
                || containsIgnoreCase(item.calendarEventTypeSystemKey(), search);
    }

    private static boolean eventTypeMatches(CalendarFeedItem item, String matchKey) {
        return safe(item.calendarEventTypeSystemKey()).equalsIgnoreCase(matchKey)
                || safe(item.displayTypeName()).equalsIgnoreCase(matchKey);
    }

    private static boolean containsIgnoreCase(String value, String loweredNeedle) {
        return safe(value).toLowerCase(Locale.ROOT).contains(loweredNeedle);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

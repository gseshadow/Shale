package com.shale.core.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record CalendarFeedSourceFilter(Set<CalendarFeedCategory> enabledCategories) {
    public CalendarFeedSourceFilter {
        enabledCategories = enabledCategories == null || enabledCategories.isEmpty()
                ? EnumSet.noneOf(CalendarFeedCategory.class)
                : EnumSet.copyOf(enabledCategories);
    }

    public static CalendarFeedSourceFilter defaults() {
        return new CalendarFeedSourceFilter(EnumSet.of(
                CalendarFeedCategory.CALENDAR_EVENTS,
                CalendarFeedCategory.TASKS,
                CalendarFeedCategory.CASE_DEADLINES,
                CalendarFeedCategory.OTHER_CASE_DATES));
    }

    public static CalendarFeedSourceFilter caseCalendarDefaults() {
        return new CalendarFeedSourceFilter(EnumSet.allOf(CalendarFeedCategory.class));
    }

    public static CalendarFeedSourceFilter allDisabled() {
        return new CalendarFeedSourceFilter(EnumSet.noneOf(CalendarFeedCategory.class));
    }

    public boolean isEnabled(CalendarFeedCategory category) {
        return enabledCategories.contains(Objects.requireNonNull(category, "category"));
    }

    public boolean hasAnyEnabled() {
        return !enabledCategories.isEmpty();
    }

    public boolean matches(CalendarFeedItem item) {
        return hasAnyEnabled() && enabledCategories.contains(CalendarFeedCategory.classify(item));
    }
}

package com.shale.core.model;

public final class CalendarOverlayClassifier {
    private CalendarOverlayClassifier() {}

    public static CalendarFeedCalendarOwner classify(CalendarFeedItem item) {
        if (item == null) throw new IllegalArgumentException("Calendar feed item is required");
        if (CalendarFeedCategory.classify(item) != CalendarFeedCategory.CALENDAR_EVENTS) {
            return CalendarFeedCalendarOwner.shared();
        }
        Integer assignedToUserId = item.assignedToUserId();
        return assignedToUserId == null || assignedToUserId <= 0
                ? CalendarFeedCalendarOwner.shared()
                : CalendarFeedCalendarOwner.user(assignedToUserId);
    }
}

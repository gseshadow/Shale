package com.shale.core.model;

public record CalendarFeedCalendarOwner(Integer userId) {
    private static final CalendarFeedCalendarOwner SHARED = new CalendarFeedCalendarOwner(null);

    public static CalendarFeedCalendarOwner shared() { return SHARED; }
    public static CalendarFeedCalendarOwner user(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        return new CalendarFeedCalendarOwner(userId);
    }
    public boolean sharedCalendar() { return userId == null; }
}

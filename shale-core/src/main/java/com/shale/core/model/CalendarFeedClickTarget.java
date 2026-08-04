package com.shale.core.model;

public record CalendarFeedClickTarget(Kind kind, long id) {
    public enum Kind {
        CALENDAR_EVENT,
        TASK,
        CASE,
        CASE_DATES,
        NONE
    }

    public static CalendarFeedClickTarget resolve(CalendarFeedItem item) {
        if (item == null) return none();
        Integer eventId = parseEventId(item.key());
        if (eventId != null && eventId > 0) {
            return new CalendarFeedClickTarget(Kind.CALENDAR_EVENT, eventId);
        }
        if (item.taskId() != null && item.taskId() > 0) {
            return new CalendarFeedClickTarget(Kind.TASK, item.taskId());
        }
        if (item.key() != null && item.key().startsWith("CASE_DATE:") && item.caseId() != null && item.caseId() > 0) {
            return new CalendarFeedClickTarget(Kind.CASE_DATES, item.caseId());
        }
        if (item.caseId() != null && item.caseId() > 0) {
            return new CalendarFeedClickTarget(Kind.CASE, item.caseId());
        }
        return none();
    }

    public boolean actionable() {
        return kind != Kind.NONE && id > 0;
    }

    private static CalendarFeedClickTarget none() {
        return new CalendarFeedClickTarget(Kind.NONE, 0);
    }

    private static Integer parseEventId(String key) {
        if (key == null || !key.startsWith("EVENT:")) return null;
        try {
            return Integer.parseInt(key.substring("EVENT:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

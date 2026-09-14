package com.shale.core.model;

public record CalendarFeedClickTarget(Kind kind, long id, Integer caseId) {
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
            return new CalendarFeedClickTarget(Kind.CALENDAR_EVENT, eventId, item.caseId());
        }
        if (item.taskId() != null && item.taskId() > 0) {
            return new CalendarFeedClickTarget(Kind.TASK, item.taskId(), item.caseId());
        }
        if (item.key() != null && item.key().startsWith("CASE_DATE:") && item.caseId() != null && item.caseId() > 0) {
            Long caseDateId = parseId(item.key(), "CASE_DATE:");
            if (caseDateId == null || caseDateId <= 0) return none();
            return new CalendarFeedClickTarget(Kind.CASE_DATES, caseDateId, item.caseId());
        }
        if (item.caseId() != null && item.caseId() > 0) {
            return new CalendarFeedClickTarget(Kind.CASE, item.caseId(), item.caseId());
        }
        return none();
    }

    public boolean actionable() {
        return kind != Kind.NONE && id > 0;
    }

    private static CalendarFeedClickTarget none() {
        return new CalendarFeedClickTarget(Kind.NONE, 0, null);
    }

    private static Integer parseEventId(String key) {
        Long parsed = parseId(key, "CALENDAR_EVENT:");
        if (parsed == null) parsed = parseId(key, "EVENT:"); // deployed feed compatibility
        return parsed == null || parsed > Integer.MAX_VALUE ? null : parsed.intValue();
    }

    private static Long parseId(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) return null;
        try {
            return Long.parseLong(key.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

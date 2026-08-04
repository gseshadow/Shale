package com.shale.core.model;

import java.util.Locale;

public enum CalendarFeedCategory {
    CALENDAR_EVENTS,
    TASKS,
    CASE_DEADLINES,
    OTHER_CASE_DATES;

    public static CalendarFeedCategory classify(CalendarFeedItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Calendar feed item is required");
        }
        String key = safe(item.key());
        String sourceField = safe(item.sourceField());
        if (key.startsWith("EVENT:")) {
            return CALENDAR_EVENTS;
        }
        if (key.startsWith("TASK:") || item.taskId() != null || "DueAt".equalsIgnoreCase(sourceField)) {
            return TASKS;
        }
        if (key.startsWith("CASE_DATE:") || "CASE_DATE".equalsIgnoreCase(safe(item.sourceType()))) {
            String category = sourceField.trim().toUpperCase(Locale.ROOT);
            return "DEADLINE".equals(category) ? CASE_DEADLINES : OTHER_CASE_DATES;
        }
        return switch (sourceField) {
            case "StatuteOfLimitations", "TortNoticeDeadline", "DiscoveryDeadline" -> CASE_DEADLINES;
            case "CallerDate", "AcceptedDate", "DeniedDate", "ClosedDate", "DateOfInjury",
                 "DateFeeAgreementSigned", "DateNonEngagementLetterSent", "DateOfMedicalNegligence",
                 "DateMedicalNegligenceWasDiscovered" -> OTHER_CASE_DATES;
            default -> {
                String sourceType = safe(item.sourceType()).trim().toUpperCase(Locale.ROOT);
                if ("MANUAL".equals(sourceType) || "CALENDAR_EVENT".equals(sourceType)) {
                    yield CALENDAR_EVENTS;
                }
                throw new IllegalArgumentException("Unrecognized calendar feed item source: key=" + key + ", sourceField=" + sourceField);
            }
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

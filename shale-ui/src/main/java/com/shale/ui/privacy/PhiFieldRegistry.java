package com.shale.ui.privacy;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Central PHI registry for table/field pairs.
 *
 * <p>This registry is intentionally hardcoded for Phase 1 passive-surface suppression.</p>
 */
public final class PhiFieldRegistry {
    private static final Set<FieldRef> PHI_FIELDS = Set.of(
            field("Cases", "AcceptedDetail"),
            field("Cases", "DeniedDetail"),
            field("Cases", "ReceivedUpdates"),
            field("Cases", "DateOfMedicalNegligence"),
            field("Cases", "DateMedicalNegligenceWasDiscovered"),
            field("Cases", "DateOfInjury"),
            field("Cases", "Description"),
            field("Cases", "Summary"),
            field("CaseTimelineEvents", "Body"),
            field("CaseUpdates", "NoteText"),
            field("Contacts", "Description"),
            field("Contacts", "Condition"),
            field("Contacts", "Notes"),
            field("Tasks", "Title"),
            field("Tasks", "Description"),
            field("TaskTimelineEvents", "Body"),
            field("TaskUpdates", "Body"));

    private PhiFieldRegistry() {
    }

    public static boolean isPhi(String tableName, String fieldName) {
        if (isBlank(tableName) || isBlank(fieldName)) {
            return false;
        }
        return PHI_FIELDS.contains(field(tableName, fieldName));
    }

    public static boolean isPhiQualified(String tableDotField) {
        if (isBlank(tableDotField)) {
            return false;
        }
        int separator = tableDotField.indexOf('.');
        if (separator <= 0 || separator >= tableDotField.length() - 1) {
            return false;
        }
        String table = tableDotField.substring(0, separator);
        String field = tableDotField.substring(separator + 1);
        return isPhi(table, field);
    }

    private static FieldRef field(String tableName, String fieldName) {
        return new FieldRef(normalize(tableName), normalize(fieldName));
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private record FieldRef(String tableName, String fieldName) {
    }
}

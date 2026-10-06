package com.shale.core.model;

import java.util.Locale;

/** Tenant-wide policy governing whether a task may be persisted without DueAt. */
public enum TaskDueDatePolicy {
    OPTIONAL, WARN, REQUIRED;

    public static TaskDueDatePolicy fromDatabase(String value) {
        if (value == null || value.isBlank()) return WARN;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return WARN; }
    }
}

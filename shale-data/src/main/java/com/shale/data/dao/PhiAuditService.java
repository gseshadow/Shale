package com.shale.data.dao;

import com.shale.core.privacy.PhiFieldRegistry;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public final class PhiAuditService {
    private static final Map<String, Integer> OBJECT_TYPE_IDS = Map.of(
            "cases", 1,
            "casetimelineevents", 2,
            "caseupdates", 3,
            "contacts", 4,
            "tasks", 5,
            "tasktimelineevents", 6,
            "taskupdates", 7);

    private final AuditLogDao auditLogDao;

    public PhiAuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    public void auditCreate(Integer userId, String tableName, String fieldName, Long recordId, Object newValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedNew = normalizeValue(newValue);
        if (normalizedNew == null) {
            return;
        }
        append(userId, tableName, fieldName, recordId, "CREATE", null, normalizedNew);
    }

    public void auditUpdate(Integer userId, String tableName, String fieldName, Long recordId, Object oldValue, Object newValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedOld = normalizeValue(oldValue);
        Object normalizedNew = normalizeValue(newValue);
        if (Objects.equals(normalizedOld, normalizedNew)) {
            return;
        }
        append(userId, tableName, fieldName, recordId, "UPDATE", normalizedOld, normalizedNew);
    }

    public void auditDelete(Integer userId, String tableName, String fieldName, Long recordId, Object oldValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedOld = normalizeValue(oldValue);
        if (normalizedOld == null) {
            return;
        }
        append(userId, tableName, fieldName, recordId, "DELETE", normalizedOld, null);
    }

    private void append(Integer userId, String tableName, String fieldName, Long recordId, String action, Object oldValue, Object newValue) {
        LocalDate dateValue = (newValue instanceof LocalDate d) ? d : null;
        String payload = "old=" + asString(oldValue) + ";new=" + asString(newValue);
        try {
            auditLogDao.appendPhiWriteAudit(
                    userId,
                    objectTypeId(tableName),
                    recordId,
                    tableName + "." + fieldName,
                    action,
                    payload,
                    dateValue);
        } catch (RuntimeException ex) {
            System.err.println("[PHI_AUDIT] append suppressed"
                    + " table=" + tableName
                    + " field=" + fieldName
                    + " action=" + action
                    + " recordId=" + recordId
                    + " userId=" + userId
                    + " old=" + asString(oldValue)
                    + " new=" + asString(newValue)
                    + " error=" + ex.getMessage());
        }
    }

    private static Integer objectTypeId(String tableName) {
        if (tableName == null) {
            return null;
        }
        return OBJECT_TYPE_IDS.get(tableName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isBlank() ? null : trimmed;
        }
        return value;
    }

    private static String asString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return String.valueOf(value);
    }
}

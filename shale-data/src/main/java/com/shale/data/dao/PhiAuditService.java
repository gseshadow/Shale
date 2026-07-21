package com.shale.data.dao;

import com.shale.core.privacy.PhiFieldRegistry;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public final class PhiAuditService {
    private static final Map<String, Integer> OBJECT_TYPE_IDS = Map.ofEntries(
            Map.entry("cases", 1),
            Map.entry("casetimelineevents", 2),
            Map.entry("caseupdates", 3),
            Map.entry("contacts", 4),
            Map.entry("tasks", 5),
            Map.entry("tasktimelineevents", 6),
            Map.entry("taskupdates", 7),
            Map.entry("materialtypes", 8),
            Map.entry("materialrequests", 9),
            Map.entry("materialrequestfollowups", 10),
            Map.entry("materialitems", 11));

    private final AuditLogDao auditLogDao;

    public PhiAuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    public void auditCreate(Connection con, Integer userId, String tableName, String fieldName, Long recordId, Object newValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedNew = normalizeValue(newValue);
        if (normalizedNew == null) {
            return;
        }
        append(con, userId, tableName, fieldName, recordId, "CREATE", null, normalizedNew);
    }

    public void auditUpdate(Connection con, Integer userId, String tableName, String fieldName, Long recordId, Object oldValue, Object newValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedOld = normalizeValue(oldValue);
        Object normalizedNew = normalizeValue(newValue);
        if (Objects.equals(normalizedOld, normalizedNew)) {
            return;
        }
        append(con, userId, tableName, fieldName, recordId, "UPDATE", normalizedOld, normalizedNew);
    }

    public void auditDelete(Connection con, Integer userId, String tableName, String fieldName, Long recordId, Object oldValue) {
        if (!PhiFieldRegistry.isPhi(tableName, fieldName)) {
            return;
        }
        Object normalizedOld = normalizeValue(oldValue);
        if (normalizedOld == null) {
            return;
        }
        append(con, userId, tableName, fieldName, recordId, "DELETE", normalizedOld, null);
    }

    public void auditCreate(Integer userId, String tableName, String fieldName, Long recordId, Object newValue) { auditCreate(null, userId, tableName, fieldName, recordId, newValue); }
    public void auditUpdate(Integer userId, String tableName, String fieldName, Long recordId, Object oldValue, Object newValue) { auditUpdate(null, userId, tableName, fieldName, recordId, oldValue, newValue); }
    public void auditDelete(Integer userId, String tableName, String fieldName, Long recordId, Object oldValue) { auditDelete(null, userId, tableName, fieldName, recordId, oldValue); }

    private void append(Connection con, Integer userId, String tableName, String fieldName, Long recordId, String action, Object oldValue, Object newValue) {
        LocalDate dateValue = (newValue instanceof LocalDate d) ? d : null;
        Integer fieldCode = inferFieldCode(oldValue, newValue);
        String payload = "old=" + asString(oldValue) + ";new=" + asString(newValue);
        try {
            if (con == null) {
                auditLogDao.appendPhiWriteAudit(
                    userId,
                    objectTypeId(tableName),
                    recordId,
                    tableName + "." + fieldName,
                    fieldCode,
                    payload,
                    dateValue);
            } else {
                auditLogDao.appendPhiWriteAudit(con,
                    userId,
                    objectTypeId(tableName),
                    recordId,
                    tableName + "." + fieldName,
                    fieldCode,
                    payload,
                    dateValue);
            }
        } catch (RuntimeException ex) {
            System.err.println("[PHI_AUDIT] append suppressed"
                    + " table=" + tableName
                    + " field=" + fieldName
                    + " action=" + action
                    + " fieldCode=" + fieldCode
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

    private static int inferFieldCode(Object oldValue, Object newValue) {
        Object candidate = newValue != null ? newValue : oldValue;
        if (candidate instanceof Boolean) {
            return 1;
        }
        if (candidate instanceof Number) {
            return 2;
        }
        if (candidate instanceof LocalDate) {
            return 3;
        }
        return 4;
    }
}

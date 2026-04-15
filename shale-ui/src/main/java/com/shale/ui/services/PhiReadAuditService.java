package com.shale.ui.services;

import com.shale.data.dao.AuditLogDao;
import com.shale.ui.state.AppState;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class PhiReadAuditService {
    // Developer note:
    // - PHI field source-of-truth lives in core: com.shale.core.privacy.PhiFieldRegistry.
    // - AuditLog FieldCode mapping: 1=boolean, 2=int, 3=date, 4=string.
    // - Phase 3 READ auditing is screen/section-level (view-open intent), not field-render-level.
    private static final int FIELD_CODE_STRING = 4;
    private static final Duration DEDUPE_WINDOW = Duration.ofSeconds(2);
    private static final Map<String, Integer> OBJECT_TYPE_IDS = Map.of(
            "Case", 1,
            "CaseTimeline", 2,
            "CaseUpdate", 3,
            "Contact", 4,
            "Task", 5,
            "TaskTimeline", 6,
            "TaskUpdate", 7);

    private final AuditLogDao auditLogDao;
    private final AppState appState;
    private final ConcurrentHashMap<String, Instant> recentlyAudited = new ConcurrentHashMap<>();

    public PhiReadAuditService(AuditLogDao auditLogDao, AppState appState) {
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
        this.appState = Objects.requireNonNull(appState, "appState");
    }

    public void auditRead(String fieldName, String screenName, String objectType, Long objectId) {
        Integer userId = appState.getUserId();
        if (userId == null || userId <= 0) {
            return;
        }
        if (fieldName == null || fieldName.isBlank()) {
            return;
        }
        String normalizedFieldName = fieldName.trim();
        String normalizedObjectType = objectType == null ? "" : objectType.trim();
        String dedupeKey = userId + "|" + normalizedObjectType + "|" + normalizedFieldName + "|" + (objectId == null ? 0L : objectId);
        Instant now = Instant.now();
        Instant previous = recentlyAudited.put(dedupeKey, now);
        if (previous != null && Duration.between(previous, now).compareTo(DEDUPE_WINDOW) < 0) {
            return;
        }
        recentlyAudited.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(DEDUPE_WINDOW.multipliedBy(2)) > 0);

        Integer objectTypeId = OBJECT_TYPE_IDS.get(normalizedObjectType);
        String metadata = "action=READ;screen=" + safe(screenName);
        try {
            auditLogDao.appendPhiWriteAudit(
                    userId,
                    objectTypeId,
                    objectId,
                    normalizedFieldName,
                    FIELD_CODE_STRING,
                    metadata,
                    null);
        } catch (RuntimeException ex) {
            System.err.println("[PHI_READ_AUDIT] append failed fieldName=" + fieldName
                    + " objectId=" + objectId
                    + " objectType=" + objectType
                    + " userId=" + userId
                    + " error=" + ex.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

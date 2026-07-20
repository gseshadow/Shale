package com.shale.data.dao;

import java.time.Instant;
import java.util.Map;

public record EntityActionAuditViewerRow(
        long auditEventId,
        int shaleClientId,
        int actorUserId,
        String actorDisplayName,
        String entityType,
        long entityId,
        String action,
        Instant occurredAtUtc,
        String parentEntityType,
        Long parentEntityId,
        String correlationId,
        String source,
        Map<String, String> safeMetadata) {
    public EntityActionAuditViewerRow {
        if (auditEventId <= 0) throw new IllegalArgumentException("auditEventId must be > 0");
        if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
        if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
        entityType = entityType == null ? "" : entityType;
        action = action == null ? "" : action;
        occurredAtUtc = occurredAtUtc == null ? Instant.EPOCH : occurredAtUtc;
        actorDisplayName = actorDisplayName == null || actorDisplayName.isBlank() ? "User #" + actorUserId : actorDisplayName.trim();
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}

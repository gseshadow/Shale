package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Writes operational case chronology on the caller-owned transaction. */
final class CaseTimelineWriter {
    static final String CASE_DATE_CREATED = "CASE_DATE_CREATED";
    static final String CASE_DATE_UPDATED = "CASE_DATE_UPDATED";
    static final String CASE_DATE_REMOVED = "CASE_DATE_REMOVED";
    static final String CASE_DATE_RESTORED = "CASE_DATE_RESTORED";
    static final String MATERIAL_REQUEST_CREATED = "MATERIAL_REQUEST_CREATED";
    static final String MATERIAL_REQUEST_UPDATED = "MATERIAL_REQUEST_UPDATED";
    static final String MATERIAL_REQUEST_STATUS_CHANGED = "MATERIAL_REQUEST_STATUS_CHANGED";
    static final String MATERIAL_REQUEST_REMOVED = "MATERIAL_REQUEST_REMOVED";
    static final String MATERIAL_REQUEST_NOTE_ADDED = "MATERIAL_REQUEST_NOTE_ADDED";
    static final String CASE_LINK_CREATED = "CASE_LINK_CREATED";
    static final String CASE_LINK_UPDATED = "CASE_LINK_UPDATED";
    static final String CASE_LINK_REMOVED = "CASE_LINK_REMOVED";
    static final String CASE_LINK_PRIMARY_CHANGED = "CASE_LINK_PRIMARY_CHANGED";
    static final String CASE_LINKS_REORDERED = "CASE_LINKS_REORDERED";
    static final String CASE_LINK_SHARE_ADDED = "CASE_LINK_SHARE_ADDED";
    static final String CASE_LINK_SHARE_UPDATED = "CASE_LINK_SHARE_UPDATED";
    static final String CASE_LINK_SHARE_REMOVED = "CASE_LINK_SHARE_REMOVED";

    private CaseTimelineWriter() {}

    static void append(Connection connection, long caseId, int tenantId, int actorUserId,
            String eventType, String title, String body) throws SQLException {
        String sql = "INSERT INTO dbo.CaseTimelineEvents "
                + "(CaseId,ShaleClientId,EventType,OccurredAt,ActorUserId,Title,Body) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, caseId);
            ps.setInt(2, tenantId);
            ps.setString(3, eventType);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setInt(5, actorUserId);
            ps.setString(6, title);
            ps.setString(7, body);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Case timeline event was not recorded.");
            }
        }
    }
}

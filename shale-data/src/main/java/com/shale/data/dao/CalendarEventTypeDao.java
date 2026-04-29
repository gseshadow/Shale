package com.shale.data.dao;

import com.shale.core.model.CalendarEventType;
import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CalendarEventTypeDao {
    private final DbSessionProvider db;

    public CalendarEventTypeDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public List<CalendarEventType> listEffectiveEventTypes(int shaleClientId) {
        if (shaleClientId <= 0) {
            return List.of();
        }
        String sql = """
                SELECT picked.CalendarEventTypeId,
                       picked.ShaleClientId,
                       picked.SystemKey,
                       picked.Name,
                       picked.ColorHex,
                       picked.SortOrder,
                       picked.IsActive,
                       picked.CreatedAt,
                       picked.UpdatedAt
                FROM (
                  SELECT cet.*,
                         ROW_NUMBER() OVER (
                            PARTITION BY COALESCE(cet.SystemKey, CONCAT('CUSTOM_', CONVERT(varchar(20), cet.CalendarEventTypeId)))
                            ORDER BY CASE WHEN cet.ShaleClientId = ? THEN 0 ELSE 1 END,
                                     cet.CalendarEventTypeId DESC
                         ) AS rn
                  FROM dbo.CalendarEventTypes cet
                  WHERE cet.ShaleClientId = ? OR cet.ShaleClientId IS NULL
                ) picked
                WHERE picked.rn = 1
                ORDER BY picked.SortOrder ASC, picked.Name ASC, picked.CalendarEventTypeId ASC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEventType> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CalendarEventType(
                            rs.getInt("CalendarEventTypeId"),
                            (Integer) rs.getObject("ShaleClientId"),
                            rs.getString("SystemKey"),
                            rs.getString("Name"),
                            rs.getString("ColorHex"),
                            rs.getInt("SortOrder"),
                            rs.getBoolean("IsActive"),
                            toLocalDateTime(rs.getTimestamp("CreatedAt")),
                            toLocalDateTime(rs.getTimestamp("UpdatedAt"))));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list effective calendar event types", e);
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}

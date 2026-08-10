package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseSummaryProjection;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.semantics.RoleSemantics;

/** The authoritative SQL boundary for shared, tenant-scoped Case summaries. */
public final class CaseSummaryDao {
	public enum DeletedState { ACTIVE, DELETED, ALL }
	public enum Order { NAME_ASC, UPDATED_DESC }

	private final DbSessionProvider db;

	public CaseSummaryDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	/**
	 * Executes one bounded query. The requested tenant must equal the trusted
	 * SESSION_CONTEXT tenant; a mismatch fails rather than silently changing scope.
	 */
	public List<CaseSummaryProjection> list(int requestedTenantId, DeletedState deletedState, Order order) {
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		Objects.requireNonNull(deletedState, "deletedState");
		Objects.requireNonNull(order, "order");
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			String deletedPredicate = switch (deletedState) {
				case ACTIVE -> "AND ISNULL(c.IsDeleted, 0) = 0";
				case DELETED -> "AND ISNULL(c.IsDeleted, 0) = 1";
				case ALL -> "";
			};
			String orderBy = switch (order) {
				case NAME_ASC -> "LOWER(COALESCE(c.Name, '')) ASC, c.Id ASC";
				case UPDATED_DESC -> "c.UpdatedAt DESC, c.Id DESC";
			};
			String sql = """
					SELECT c.Id AS CaseId, c.ShaleClientId, c.CaseNumber, c.Name AS CaseName,
					       status_row.StatusId, status_row.SystemKey AS StatusSystemKey,
					       status_row.LifecycleKey AS StatusLifecycleKey, status_row.StatusName,
					       status_row.StatusColor, c.PracticeAreaId, pa.Name AS PracticeAreaName,
					       attorney.UserId AS ResponsibleAttorneyId,
					       attorney_user.DisplayName AS ResponsibleAttorneyName,
					       attorney_user.Color AS ResponsibleAttorneyColor,
					       assistant.UserId AS PrimaryLegalAssistantId,
					       assistant_user.DisplayName AS PrimaryLegalAssistantName,
					       assistant_user.Color AS PrimaryLegalAssistantColor,
					       c.CreatedAt, c.UpdatedAt, ISNULL(c.IsDeleted, 0) AS IsDeleted
					FROM dbo.Cases c
					OUTER APPLY (
					  SELECT TOP (1) s.Id AS StatusId, s.SystemKey, s.LifecycleKey,
					         s.Name AS StatusName, s.Color AS StatusColor
					  FROM dbo.CaseStatuses cs
					  INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
					    AND (s.ShaleClientId = c.ShaleClientId OR s.ShaleClientId IS NULL)
					  WHERE cs.CaseId = c.Id AND cs.EndDate IS NULL
					  ORDER BY cs.IsPrimary DESC, cs.EffectiveDate DESC,
					           cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
					) status_row
					LEFT JOIN dbo.PracticeAreas pa ON pa.Id = c.PracticeAreaId
					  AND (pa.ShaleClientId = c.ShaleClientId OR pa.ShaleClientId IS NULL)
					OUTER APPLY (
					  SELECT TOP (1) cu.UserId FROM dbo.CaseUsers cu
					  WHERE cu.CaseId = c.Id AND cu.RoleId = ?
					  ORDER BY cu.IsPrimary DESC, cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) attorney
					OUTER APPLY (
					  SELECT u.id, LTRIM(RTRIM(CONCAT(u.name_first, ' ', u.name_last))) AS DisplayName, u.color AS Color
					  FROM dbo.Users u WHERE u.id = attorney.UserId AND u.ShaleClientId = c.ShaleClientId
					) attorney_user
					OUTER APPLY (
					  SELECT TOP (1) cu.UserId FROM dbo.CaseUsers cu
					  WHERE cu.CaseId = c.Id AND cu.RoleId = ?
					  ORDER BY cu.IsPrimary DESC, cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) assistant
					OUTER APPLY (
					  SELECT u.id, LTRIM(RTRIM(CONCAT(u.name_first, ' ', u.name_last))) AS DisplayName, u.color AS Color
					  FROM dbo.Users u WHERE u.id = assistant.UserId AND u.ShaleClientId = c.ShaleClientId
					) assistant_user
					WHERE c.ShaleClientId = ?
					%s
					ORDER BY %s;
					""".formatted(deletedPredicate, orderBy);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				try (ResultSet rs = ps.executeQuery()) {
					List<CaseSummaryProjection> rows = new ArrayList<>();
					while (rs.next()) rows.add(map(rs));
					return List.copyOf(rows);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load authoritative Case summaries", e);
		}
	}

	private static void verifyTenant(Connection con, int requestedTenantId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId'))");
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next() || rs.getObject(1) == null)
				throw new IllegalStateException("ShaleClientId session context is missing.");
			if (rs.getInt(1) != requestedTenantId)
				throw new IllegalStateException("Requested tenant conflicts with ShaleClientId session context.");
		}
	}

	private static CaseSummaryProjection map(ResultSet rs) throws SQLException {
		return new CaseSummaryProjection(rs.getLong("CaseId"), rs.getInt("ShaleClientId"),
				rs.getString("CaseNumber"), rs.getString("CaseName"), nullableInt(rs, "StatusId"),
				rs.getString("StatusSystemKey"), rs.getString("StatusLifecycleKey"), rs.getString("StatusName"),
				rs.getString("StatusColor"), nullableInt(rs, "PracticeAreaId"), rs.getString("PracticeAreaName"),
				nullableInt(rs, "ResponsibleAttorneyId"), rs.getString("ResponsibleAttorneyName"),
				rs.getString("ResponsibleAttorneyColor"), nullableInt(rs, "PrimaryLegalAssistantId"),
				rs.getString("PrimaryLegalAssistantName"), rs.getString("PrimaryLegalAssistantColor"),
				localDateTime(rs.getTimestamp("CreatedAt")), localDateTime(rs.getTimestamp("UpdatedAt")),
				rs.getBoolean("IsDeleted"));
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static LocalDateTime localDateTime(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}
}

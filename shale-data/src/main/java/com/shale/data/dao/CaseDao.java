package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CaseDao {

	private static final String CASES_TABLE = "Cases";
	private static final String CASE_USERS_TABLE = "CaseUsers";
	private static final String USERS_TABLE = "Users";

	// CaseUsers.Role (int) for Responsible Attorney
	private static final int ROLE_RESPONSIBLE_ATTORNEY = 4;

	public enum CaseSort {
		INTAKE_NEWEST,
		INTAKE_OLDEST,
		STATUTE_SOONEST,
		STATUTE_LATEST,
		CASE_NAME_ASC,
		CASE_NAME_DESC,
		RESPONSIBLE_ATTORNEY_ASC,
		RESPONSIBLE_ATTORNEY_DESC
	}

	private final DbSessionProvider db;

	public CaseDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
	}

	/** DAO read-model for lists/cards. */
	public record CaseRow(
			long id,
			String name,
			LocalDate intakeDate, // CallerDate
			LocalDate statuteOfLimitationsDate, // IncidentStatuteOfLimitations
			Integer responsibleAttorneyId,
			String responsibleAttorneyName,
			String responsibleAttorneyColor
	) {
	}

	public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
	}

	/** page is 0-based */
	public PagedResult<CaseRow> findPage(int page, int pageSize) {
		return findPage(page, pageSize, CaseSort.INTAKE_NEWEST);
	}

	/** page is 0-based */
	public PagedResult<CaseRow> findPage(int page, int pageSize, CaseSort sort) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		long total = countAll();
		if (total == 0) {
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		CaseSort effectiveSort = sort == null ? CaseSort.INTAKE_NEWEST : sort;
		String orderByClause = orderByClauseFor(effectiveSort);

		// Pick ONE PRIMARY responsible attorney row per case.
		// If none is marked primary, return null attorney/color so UI remains white.
		String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CallerDate,
				  c.IncidentStatuteOfLimitations,
				  ra.UserId AS ResponsibleAttorneyId,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName
				FROM %s c
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.Role = ?
				      AND cu.IsPrimary = 1
				    ORDER BY
				      cu.UpdatedAt DESC,
				      cu.CreatedAt DESC,
				      cu.Id DESC
				) ra
				LEFT JOIN %s u
				  ON u.id = ra.UserId
				WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				ORDER BY
				  %s
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(CASES_TABLE, CASE_USERS_TABLE, USERS_TABLE, orderByClause);

		List<CaseRow> out = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;
			ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setInt(idx++, offset);
			ps.setInt(idx++, pageSize);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("IncidentStatuteOfLimitations")),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
					));
				}
			}
			if (!out.isEmpty()) {
				CaseRow first = out.get(0);
				System.out.println("CaseDao.findPage first row: " + first);
			}

			return new PagedResult<>(out, page, pageSize, total);

		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to load cases page (page=" + page + ", pageSize=" + pageSize + ")",
					e
			);
		}
	}

	private static String orderByClauseFor(CaseSort sort) {
		return switch (sort) {
		case INTAKE_OLDEST -> "c.CallerDate ASC, c.Id ASC";
		case STATUTE_SOONEST -> "c.IncidentStatuteOfLimitations ASC, c.Id ASC";
		case STATUTE_LATEST -> "c.IncidentStatuteOfLimitations DESC, c.Id DESC";
		case CASE_NAME_ASC -> "c.Name ASC, c.Id ASC";
		case CASE_NAME_DESC -> "c.Name DESC, c.Id DESC";
		case RESPONSIBLE_ATTORNEY_ASC -> "ResponsibleAttorneyName ASC, c.Id ASC";
		case RESPONSIBLE_ATTORNEY_DESC -> "ResponsibleAttorneyName DESC, c.Id DESC";
		case INTAKE_NEWEST -> "c.CallerDate DESC, c.Id DESC";
		};
	}

	public long countAll() {
		String sql = """
				SELECT COUNT(1)
				FROM %s c
				WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
				""".formatted(CASES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

			rs.next();
			return rs.getLong(1);

		} catch (SQLException e) {
			throw new RuntimeException("Failed to count cases", e);
		}
	}

	// ---- helpers ----

	private static LocalDate toLocalDate(java.sql.Date d) {
		return d == null ? null : d.toLocalDate();
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		Object o = rs.getObject(col);
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.intValue();
		return Integer.valueOf(o.toString());
	}
}

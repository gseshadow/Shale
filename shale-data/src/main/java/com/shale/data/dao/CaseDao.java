package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.runtime.DbSessionProvider;

public final class CaseDao {

	private static final String CASES_TABLE = "Cases";
	private static final String CASE_USERS_TABLE = "CaseUsers";
	private static final String USERS_TABLE = "Users";
	private static final String CASE_STATUSES_TABLE = "CaseStatuses";
	private static final String STATUSES_TABLE = "Statuses";
	// CaseContacts.Role values
	private static final int ROLE_CASECONTACT_CLIENT = 1;
	private static final int ROLE_CASECONTACT_CALLER = 2;
	private static final int ROLE_CASECONTACT_OPPOSING_COUNSEL = 6;

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
		return findPage(page, pageSize, CaseSort.INTAKE_NEWEST, false);
	}

	/** page is 0-based */
	public PagedResult<CaseRow> findPage(int page, int pageSize, CaseSort sort) {
		return findPage(page, pageSize, sort, false);
	}

	/** page is 0-based */
	public PagedResult<CaseRow> findPage(int page, int pageSize, CaseSort sort, boolean includeClosedDenied) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		long total = countAll(includeClosedDenied);
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
				  c.StatuteOfLimitations,
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
				OUTER APPLY (
				    SELECT TOP (1) s.Name AS CurrentStatusName
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				LEFT JOIN %s u
				  ON u.id = ra.UserId
				WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND (
				    ? = 1
				    OR current_status.CurrentStatusName IS NULL
				    OR LOWER(current_status.CurrentStatusName) NOT IN ('closed', 'denied')
				  )
				ORDER BY
				  %s
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(CASES_TABLE, CASE_USERS_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, USERS_TABLE,
				orderByClause);

		List<CaseRow> out = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;
			ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setInt(idx++, includeClosedDenied ? 1 : 0);
			ps.setInt(idx++, offset);
			ps.setInt(idx++, pageSize);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
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
		case STATUTE_SOONEST -> "c.StatuteOfLimitations ASC, c.Id ASC";
		case STATUTE_LATEST -> "c.StatuteOfLimitations DESC, c.Id DESC";
		case CASE_NAME_ASC -> "c.Name ASC, c.Id ASC";
		case CASE_NAME_DESC -> "c.Name DESC, c.Id DESC";
		case RESPONSIBLE_ATTORNEY_ASC -> "ResponsibleAttorneyName ASC, c.Id ASC";
		case RESPONSIBLE_ATTORNEY_DESC -> "ResponsibleAttorneyName DESC, c.Id DESC";
		case INTAKE_NEWEST -> "c.CallerDate DESC, c.Id DESC";
		};
	}

	public long countAll() {
		return countAll(false);
	}

	public long countAll(boolean includeClosedDenied) {
		String sql = """
				SELECT COUNT(1)
				FROM %s c
				OUTER APPLY (
				    SELECT TOP (1) s.Name AS CurrentStatusName
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND (
				    ? = 1
				    OR current_status.CurrentStatusName IS NULL
				    OR LOWER(current_status.CurrentStatusName) NOT IN ('closed', 'denied')
				  );
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, includeClosedDenied ? 1 : 0);

			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to count cases", e);
		}
	}

	public com.shale.core.dto.CaseOverviewDto getOverview(long caseId) {

		String sql = """
								SELECT
								  c.Id,
								  c.Name,
								  c.CaseNumber,
								  c.Description,
								  c.CallerDate,
								  c.DateOfInjury,
								  c.StatuteOfLimitations,
								  pa.Name AS PracticeAreaName,

								  -- Responsible Attorney (primary) - Users has name_first/name_last
								 -- Responsible Attorney (primary)
				ra.UserId AS ResponsibleAttorneyUserId,
				u.color AS ResponsibleAttorneyColor,
				LTRIM(RTRIM(
				  COALESCE(u.name_first, '') +
				  CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				  COALESCE(u.name_last, '')
				)) AS ResponsibleAttorneyName,


								  -- Current Status
								  current_status.CurrentStatusName,

								  -- Primary Caller / Client / Opposing Counsel
								  callerContact.FullName AS CallerName,
								  clientContact.FullName AS ClientName,
								  oppContact.FullName AS OpposingCounselName

								FROM %s c

								LEFT JOIN PracticeAreas pa
								  ON pa.Id = c.PracticeAreaId

								-- Primary Responsible Attorney
								OUTER APPLY (
								    SELECT TOP (1) cu.UserId
								    FROM %s cu
								    WHERE cu.CaseId = c.Id
								      AND cu.Role = ?
								      AND cu.IsPrimary = 1
								    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
								) ra

								LEFT JOIN %s u
								  ON u.id = ra.UserId

								-- Current Status
								OUTER APPLY (
								    SELECT TOP (1) s.Name AS CurrentStatusName
								    FROM %s cs
								    INNER JOIN %s s ON s.Id = cs.StatusId
								    WHERE cs.CaseId = c.Id
								    ORDER BY
								      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
								      cs.UpdatedAt DESC,
								      cs.CreatedAt DESC,
								      cs.Id DESC
								) current_status

								-- Caller (primary)
								OUTER APPLY (
								    SELECT TOP (1)
								      CASE
								        WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
								          OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
								        THEN LTRIM(RTRIM(
								              COALESCE(ct.FirstName, '') +
								              CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
								              COALESCE(ct.LastName, '')
								            ))
								        ELSE COALESCE(ct.Name, '')
								      END AS FullName
								    FROM CaseContacts cc
								    INNER JOIN Contacts ct ON ct.Id = cc.ContactId
								    WHERE cc.CaseId = c.Id
								      AND cc.Role = ?
								      AND cc.IsPrimary = 1
								    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
								) callerContact

								-- Client (primary)
								OUTER APPLY (
								    SELECT TOP (1)
								      CASE
								        WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
								          OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
								        THEN LTRIM(RTRIM(
								              COALESCE(ct.FirstName, '') +
								              CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
								              COALESCE(ct.LastName, '')
								            ))
								        ELSE COALESCE(ct.Name, '')
								      END AS FullName
								    FROM CaseContacts cc
								    INNER JOIN Contacts ct ON ct.Id = cc.ContactId
								    WHERE cc.CaseId = c.Id
								      AND cc.Role = ?
								      AND cc.IsPrimary = 1
								    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
								) clientContact

								-- Opposing Counsel (primary)
								OUTER APPLY (
								    SELECT TOP (1)
								      CASE
								        WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
								          OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
								        THEN LTRIM(RTRIM(
								              COALESCE(ct.FirstName, '') +
								              CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
								              COALESCE(ct.LastName, '')
								            ))
								        ELSE COALESCE(ct.Name, '')
								      END AS FullName
								    FROM CaseContacts cc
								    INNER JOIN Contacts ct ON ct.Id = cc.ContactId
								    WHERE cc.CaseId = c.Id
								      AND cc.Role = ?
								      AND cc.IsPrimary = 1
								    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
								) oppContact

								WHERE c.Id = ?
								  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
								""".formatted(
				CASES_TABLE,
				CASE_USERS_TABLE,
				USERS_TABLE,
				CASE_STATUSES_TABLE,
				STATUSES_TABLE
		);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;

			ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);

			ps.setInt(idx++, ROLE_CASECONTACT_CALLER); // 2
			ps.setInt(idx++, ROLE_CASECONTACT_CLIENT); // 1
			ps.setInt(idx++, ROLE_CASECONTACT_OPPOSING_COUNSEL); // 6

			ps.setLong(idx++, caseId);

			try (ResultSet rs = ps.executeQuery()) {

				if (!rs.next())
					return null;

				List<String> team = loadTeamMembers(con, caseId);

				return new com.shale.core.dto.CaseOverviewDto(
						rs.getLong("Id"),
						rs.getString("CaseNumber"),
						rs.getString("Name"),
						rs.getString("CurrentStatusName"),

						getNullableInt(rs, "ResponsibleAttorneyUserId"),
						rs.getString("ResponsibleAttorneyName"),
						rs.getString("ResponsibleAttorneyColor"),

						rs.getString("PracticeAreaName"),
						toLocalDate(rs.getDate("CallerDate")),
						toLocalDate(rs.getDate("DateOfInjury")),
						toLocalDate(rs.getDate("StatuteOfLimitations")),
						rs.getString("CallerName"),
						rs.getString("ClientName"),
						rs.getString("OpposingCounselName"),
						team,
						rs.getString("Description")
				);

			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case overview (caseId=" + caseId + ")", e);
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

	private List<String> loadTeamMembers(Connection con, long caseId) throws SQLException {

		String sql = """
				SELECT
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS FullName
				FROM %s cu
				INNER JOIN %s u ON u.Id = cu.UserId
				WHERE cu.CaseId = ?
				ORDER BY cu.Role, u.name_last, u.name_first;
				""".formatted(CASE_USERS_TABLE, USERS_TABLE);

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);

			try (ResultSet rs = ps.executeQuery()) {
				List<String> list = new ArrayList<>();
				while (rs.next()) {
					String name = rs.getString("FullName");
					if (name != null && !name.isBlank())
						list.add(name);
				}
				return list;
			}
		}
	}

}

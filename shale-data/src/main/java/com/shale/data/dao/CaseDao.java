package com.shale.data.dao;

import java.sql.Connection;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.shale.core.dto.CaseTimelineEventDto;
import com.shale.core.dto.CaseUpdateDto;
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

	// CaseUsers.RoleId (int) for Responsible Attorney
	private static final int ROLE_RESPONSIBLE_ATTORNEY = 4;
	public static final class CaseTimelineEventTypes {
		public static final String CASE_CREATED = "CASE_CREATED";
		public static final String STATUS_CHANGED = "STATUS_CHANGED";
		public static final String RESPONSIBLE_ATTORNEY_CHANGED = "RESPONSIBLE_ATTORNEY_CHANGED";
		public static final String TEAM_CHANGED = "TEAM_CHANGED";
		public static final String CLIENT_CHANGED = "CLIENT_CHANGED";
		public static final String CALLER_CHANGED = "CALLER_CHANGED";
		public static final String OPPOSING_COUNSEL_CHANGED = "OPPOSING_COUNSEL_CHANGED";
		public static final String INCIDENT_DATE_CHANGED = "INCIDENT_DATE_CHANGED";
		public static final String SOL_DATE_CHANGED = "SOL_DATE_CHANGED";
		public static final String CASE_NAME_CHANGED = "CASE_NAME_CHANGED";
		public static final String CASE_NUMBER_CHANGED = "CASE_NUMBER_CHANGED";
		public static final String PRACTICE_AREA_CHANGED = "PRACTICE_AREA_CHANGED";
		public static final String USER_NOTE_ADDED = "USER_NOTE_ADDED";

		private static final Set<String> ALLOWED = Set.of(
				CASE_CREATED,
				STATUS_CHANGED,
				RESPONSIBLE_ATTORNEY_CHANGED,
				TEAM_CHANGED,
				CLIENT_CHANGED,
				CALLER_CHANGED,
				OPPOSING_COUNSEL_CHANGED,
				INCIDENT_DATE_CHANGED,
				SOL_DATE_CHANGED,
				CASE_NAME_CHANGED,
				CASE_NUMBER_CHANGED,
				PRACTICE_AREA_CHANGED,
				USER_NOTE_ADDED
		);

		private CaseTimelineEventTypes() {
		}
	}

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
			Integer primaryStatusId,
			Integer responsibleAttorneyId,
			String responsibleAttorneyName,
			String responsibleAttorneyColor
	) {
	}

	public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
	}

	public record ContactRow(int id, String displayName) {
	}

	public record RelatedOrganizationRow(
			int id,
			String name,
			Integer organizationTypeId,
			String organizationTypeName,
			String phone,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			String color
	) {
	}

	public record RelatedContactRow(
			int id,
			String displayName,
			Integer roleId,
			String roleName,
			boolean primary,
			String email,
			String phone
	) {
	}

	public record CaseContactRoleOption(
			int id,
			String name,
			String description
	) {
	}

	public record SelectableContactRow(
			int id,
			String displayName,
			String email,
			String phone
	) {
	}

	public record SelectableOrganizationRow(
			int id,
			String name,
			String organizationTypeName
	) {
	}

	private record CaseSchema(String deletedColumn) {
	}


	public record NewIntakeCreateRequest(
			int shaleClientId,
			String caseName,
			LocalDate intakeDate,
			LocalTime intakeTime,
			boolean estateCase,
			int practiceAreaId,
			int statusId,
			String description,
			String summary,
			LocalDate dateOfMedicalNegligence,
			LocalDate dateMedicalNegligenceWasDiscovered,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortClaimsNotice,
			String clientFirstName,
			String clientLastName,
			String clientAddress,
			String clientPhone,
			String clientEmail,
			LocalDate clientDateOfBirth,
			boolean clientDeceased,
			String clientCondition,
			boolean callerIsClient,
			String callerFirstName,
			String callerLastName,
			String callerPhone,
			Integer createdByUserId
	) {
	}

	public record NewIntakeCreateResult(long caseId, int clientContactId, int callerContactId) {
	}

	public NewIntakeCreateResult createIntake(NewIntakeCreateRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.shaleClientId() <= 0)
			throw new IllegalArgumentException("shaleClientId is required.");

		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			int clientContactId = insertContact(con,
					buildFullName(request.clientFirstName(), request.clientLastName()),
					request.clientFirstName(),
					request.clientLastName(),
					request.clientAddress(),
					request.clientPhone(),
					request.clientEmail(),
					request.clientDateOfBirth(),
					request.clientCondition(),
					request.clientDeceased(),
					true,
					request.shaleClientId(),
					now);

			int callerContactId = clientContactId;
			if (!request.callerIsClient()) {
				callerContactId = insertContact(con,
						buildFullName(request.callerFirstName(), request.callerLastName()),
						request.callerFirstName(),
						request.callerLastName(),
						null,
						request.callerPhone(),
						null,
						null,
						null,
						false,
						false,
						request.shaleClientId(),
						now);
			}

			long caseId = insertCase(con, request, now);
			insertCaseContact(con, caseId, clientContactId, ROLE_CASECONTACT_CLIENT, now);
			insertCaseContact(con, caseId, callerContactId, ROLE_CASECONTACT_CALLER, now);
			insertCaseStatus(con, caseId, request.statusId(), now);

			con.commit();
			return new NewIntakeCreateResult(caseId, clientContactId, callerContactId);
		} catch (SQLException e) {
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to create intake.", e);
		} finally {
			if (con != null) {
				try {
					con.setAutoCommit(true);
				} catch (SQLException ignored) {
				}
				try {
					con.close();
				} catch (SQLException ignored) {
				}
			}
		}
	}

	private int insertContact(Connection con,
			String name,
			String firstName,
			String lastName,
			String addressHome,
			String phoneCell,
			String emailPersonal,
			LocalDate dateOfBirth,
			String condition,
			boolean isDeceased,
			boolean isClient,
			int shaleClientId,
			Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.Contacts (
				  Name,
				  FirstName,
				  LastName,
				  AddressHome,
				  PhoneCell,
				  EmailPersonal,
				  DateOfBirth,
				  Condition,
				  IsDeceased,
				  IsClient,
				  IsDeleted,
				  CreatedAt,
				  UpdatedAt,
				  ShaleClientId
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?);
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			setNullableString(ps, i++, name);
			setNullableString(ps, i++, firstName);
			setNullableString(ps, i++, lastName);
			setNullableString(ps, i++, addressHome);
			setNullableString(ps, i++, phoneCell);
			setNullableString(ps, i++, emailPersonal);
			setNullableDate(ps, i++, dateOfBirth);
			setNullableString(ps, i++, condition);
			ps.setBoolean(i++, isDeceased);
			ps.setBoolean(i++, isClient);
			ps.setTimestamp(i++, now);
			ps.setTimestamp(i++, now);
			ps.setInt(i++, shaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new RuntimeException("Failed to create contact.");
				return rs.getInt(1);
			}
		}
	}

	private long insertCase(Connection con, NewIntakeCreateRequest request, Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.Cases (
				  Name,
				  CallerDate,
				  CallerTime,
				  PracticeAreaId,
				  ClientEstate,
				  Description,
				  Summary,
				  DateOfMedicalNegligence,
				  DateMedicalNegligenceWasDiscovered,
				  DateOfInjury,
				  StatuteOfLimitations,
				  TortNoticeDeadline,
				  FollowUpMeetWithClient,
				  FollowUpNurseReview,
				  FollowUpExpertReview,
				  FollowUpCaseTransferred,
				  AcceptedChronology,
				  AcceptedConsultantExpertSearch,
				  AcceptedTestifyingExpertSearch,
				  AcceptedMedicalLiterature,
				  DeniedChronology,
				  FeeAgreementSigned,
				  MedicalRecordsReceived,
				  IsDeleted,
				  CreatedAt,
				  UpdatedAt,
				  ShaleClientId
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?);
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			setNullableString(ps, i++, request.caseName());
			setNullableDate(ps, i++, request.intakeDate());
			setNullableTime(ps, i++, request.intakeTime());
			ps.setInt(i++, request.practiceAreaId());
			ps.setBoolean(i++, request.estateCase());
			setNullableString(ps, i++, request.description());
			setNullableString(ps, i++, request.summary());
			setNullableDate(ps, i++, request.dateOfMedicalNegligence());
			setNullableDate(ps, i++, request.dateMedicalNegligenceWasDiscovered());
			setNullableDate(ps, i++, request.dateOfInjury());
			setNullableDate(ps, i++, request.statuteOfLimitations());
			setNullableDate(ps, i++, request.tortClaimsNotice());
			ps.setTimestamp(i++, now);
			ps.setTimestamp(i++, now);
			ps.setInt(i++, request.shaleClientId());

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new RuntimeException("Failed to create case.");
				return rs.getLong(1);
			}
		}
	}

	private void insertCaseContact(Connection con, long caseId, int contactId, int role, Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.CaseContacts (
				  CaseId,
				  ContactId,
				  Role,
				  Side,
				  IsPrimary,
				  Notes,
				  AddedAt,
				  CreatedAt,
				  UpdatedAt
				)
				VALUES (?, ?, ?, NULL, 1, NULL, ?, ?, ?);
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, contactId);
			ps.setInt(3, role);
			ps.setTimestamp(4, now);
			ps.setTimestamp(5, now);
			ps.setTimestamp(6, now);
			int rows = ps.executeUpdate();
			if (rows != 1)
				throw new RuntimeException("Failed to create case contact (role=" + role + ").");
		}
	}

	private void insertCaseStatus(Connection con, long caseId, int statusId, Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.CaseStatuses (
				  CaseId,
				  StatusId,
				  EffectiveDate,
				  EndDate,
				  Notes,
				  CreatedAt,
				  UpdatedAt,
				  IsPrimary
				)
				VALUES (?, ?, ?, NULL, NULL, ?, ?, 1);
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, statusId);
			ps.setTimestamp(3, now);
			ps.setTimestamp(4, now);
			ps.setTimestamp(5, now);
			int rows = ps.executeUpdate();
			if (rows != 1)
				throw new RuntimeException("Failed to create case status.");
		}
	}

	private static String buildFullName(String firstName, String lastName) {
		String first = firstName == null ? "" : firstName.trim();
		String last = lastName == null ? "" : lastName.trim();
		if (first.isBlank() && last.isBlank())
			return null;
		if (first.isBlank())
			return last;
		if (last.isBlank())
			return first;
		return first + " " + last;
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
		return findPageInternal(page, pageSize, sort, includeClosedDenied, null);
	}

	/** page is 0-based */
	public PagedResult<CaseRow> findMyCasesPage(int userId, int page, int pageSize, CaseSort sort, boolean includeClosedDenied) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be > 0");
		}
		System.out.println("[TRACE ASSIGNED_CASES][CaseDao.findMyCasesPage] "
				+ "restrictToUserId=" + userId
				+ " page=" + page
				+ " pageSize=" + pageSize
				+ " sort=" + sort
				+ " includeClosedDenied=" + includeClosedDenied);
		return findPageInternal(page, pageSize, sort, includeClosedDenied, userId);
	}

	public List<CaseRow> listActiveCasesForUserTeamMember(int userId, int limit) {
		if (userId <= 0) {
			return List.of();
		}
		if (limit <= 0) {
			return List.of();
		}
		System.out.println("[TRACE ASSIGNED_CASES][CaseDao.listActiveCasesForUserTeamMember] "
				+ "daoQueryMethodName=listActiveCasesForUserTeamMember "
				+ " daoInputUserId=" + userId
				+ " selectedUserId=" + userId
				+ " limit=" + limit);
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			int shaleClientId = requireCurrentShaleClientId(con);
			String sql = """
					SELECT TOP (?)
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  current_status.PrimaryStatusId,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId
					    FROM %s cs
					    INNER JOIN %s s ON s.Id = cs.StatusId
					    WHERE cs.CaseId = c.Id
					    ORDER BY
					      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
					      cs.UpdatedAt DESC,
					      cs.CreatedAt DESC,
					      cs.Id DESC
					) current_status
					OUTER APPLY (
					    SELECT TOP (1) cu.UserId
					    FROM %s cu
					    WHERE cu.CaseId = c.Id
					      AND cu.RoleId = ?
					      AND cu.IsPrimary = 1
					    ORDER BY
					      cu.UpdatedAt DESC,
					      cu.CreatedAt DESC,
					      cu.Id DESC
					) ra
					LEFT JOIN %s u
					  ON u.id = ra.UserId
					WHERE %s
					  AND c.ShaleClientId = ?
					  AND EXISTS (
					    SELECT 1
					    FROM %s cu_scope
					    WHERE cu_scope.CaseId = c.Id
					      AND cu_scope.UserId = ?
					      AND cu_scope.RoleId = ?
					      AND cu_scope.IsPrimary = 1
					  )
					ORDER BY c.CallerDate DESC, c.Id DESC;
					""".formatted(
							CASES_TABLE,
							CASE_STATUSES_TABLE,
							STATUSES_TABLE,
							CASE_USERS_TABLE,
							USERS_TABLE,
							activeFilter(schema.deletedColumn(), "c"),
							CASE_USERS_TABLE);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, limit);
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, userId);
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);

				List<CaseRow> out = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new CaseRow(
								rs.getLong("Id"),
								rs.getString("Name"),
								toLocalDate(rs.getDate("CallerDate")),
								toLocalDate(rs.getDate("StatuteOfLimitations")),
								getNullableInt(rs, "PrimaryStatusId"),
								getNullableInt(rs, "ResponsibleAttorneyId"),
								rs.getString("ResponsibleAttorneyName"),
								rs.getString("ResponsibleAttorneyColor")));
					}
				}
				System.out.println("[TRACE ASSIGNED_CASES][CaseDao.listActiveCasesForUserTeamMember] "
						+ "selectedUserId=" + userId
						+ " shaleClientId=" + shaleClientId
						+ " roleId=" + ROLE_RESPONSIBLE_ATTORNEY
						+ " isPrimary=1"
						+ " daoTotalRowsReturned=" + out.size());
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list assigned cases for responsible attorney user (userId=" + userId + ")", e);
		}
	}

	public List<CaseRow> searchCasesByName(String query) {
		String normalizedQuery = normalizeSearchQuery(query);
		if (normalizedQuery.isBlank()) {
			return List.of();
		}

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CallerDate,
				  c.StatuteOfLimitations,
				  current_status.PrimaryStatusId,
				  ra.UserId AS ResponsibleAttorneyId,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName
				FROM %s c
				OUTER APPLY (
				    SELECT TOP (1) s.Id AS PrimaryStatusId
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = ?
				      AND cu.IsPrimary = 1
				    ORDER BY
				      cu.UpdatedAt DESC,
				      cu.CreatedAt DESC,
				      cu.Id DESC
				) ra
				LEFT JOIN %s u
				  ON u.id = ra.UserId
				WHERE c.ShaleClientId = ?
				  AND %s
				  AND LOWER(COALESCE(c.Name, '')) LIKE ?
				ORDER BY c.Name ASC, c.Id ASC;
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, activeFilter(schema.deletedColumn(), "c"));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, requireCurrentShaleClientId(con));
				ps.setString(idx, containsPattern(normalizedQuery));

				List<CaseRow> out = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							getNullableInt(rs, "PrimaryStatusId"),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
						));
					}
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to search cases by name", e);
		}
	}

	public List<CaseRow> searchDeletedCasesByName(String query) {
		String normalizedQuery = normalizeSearchQuery(query);
		if (normalizedQuery.isBlank()) {
			return List.of();
		}

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			if (schema.deletedColumn() == null || schema.deletedColumn().isBlank()) {
				return List.of();
			}
			String deletedFilter = "(" + "c." + schema.deletedColumn() + " = 1)";
			String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CallerDate,
				  c.StatuteOfLimitations,
				  current_status.PrimaryStatusId,
				  ra.UserId AS ResponsibleAttorneyId,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName
				FROM %s c
				OUTER APPLY (
				    SELECT TOP (1) s.Id AS PrimaryStatusId
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = ?
				      AND cu.IsPrimary = 1
				    ORDER BY
				      cu.UpdatedAt DESC,
				      cu.CreatedAt DESC,
				      cu.Id DESC
				) ra
				LEFT JOIN %s u
				  ON u.id = ra.UserId
				WHERE c.ShaleClientId = ?
				  AND %s
				  AND LOWER(COALESCE(c.Name, '')) LIKE ?
				ORDER BY c.Name ASC, c.Id ASC;
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, deletedFilter);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, requireCurrentShaleClientId(con));
				ps.setString(idx, containsPattern(normalizedQuery));

				List<CaseRow> out = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							getNullableInt(rs, "PrimaryStatusId"),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
						));
					}
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to search deleted cases by name", e);
		}
	}

	private PagedResult<CaseRow> findPageInternal(int page,
			int pageSize,
			CaseSort sort,
			boolean includeClosedDenied,
			Integer restrictToUserId) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		long total = countAll(includeClosedDenied, restrictToUserId);
		if (total == 0) {
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		CaseSort effectiveSort = sort == null ? CaseSort.INTAKE_NEWEST : sort;
		String orderByClause = orderByClauseFor(effectiveSort);

		String userMembershipFilter = restrictToUserId == null
				? ""
				: """
				  AND EXISTS (
				    SELECT 1
				    FROM %s cu_scope
				    WHERE cu_scope.CaseId = c.Id
				      AND cu_scope.UserId = ?
				  )
				""".formatted(CASE_USERS_TABLE);

		List<CaseRow> out = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CallerDate,
				  c.StatuteOfLimitations,
				  current_status.PrimaryStatusId,
				  ra.UserId AS ResponsibleAttorneyId,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName
				FROM %s c
				OUTER APPLY (
				    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = ?
				      AND cu.IsPrimary = 1
				    ORDER BY
				      cu.UpdatedAt DESC,
				      cu.CreatedAt DESC,
				      cu.Id DESC
				) ra
				LEFT JOIN %s u
				  ON u.id = ra.UserId
				WHERE %s
				  AND c.ShaleClientId = ?
				  %s
				ORDER BY
				  %s
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, activeFilter(schema.deletedColumn(), "c"), userMembershipFilter, orderByClause);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int shaleClientId = requireCurrentShaleClientId(con);
				int idx = 1;
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, shaleClientId);
				StringBuilder traceParams = new StringBuilder()
						.append("raRoleId=").append(ROLE_RESPONSIBLE_ATTORNEY)
						.append(" shaleClientId=").append(shaleClientId)
						.append(" includeClosedDeniedFlag=").append(includeClosedDenied ? 1 : 0);
				if (restrictToUserId != null) {
					ps.setInt(idx++, restrictToUserId);
					traceParams.append(" restrictToUserId=").append(restrictToUserId)
							.append(" restrictByAnyCaseUserMembership=true");
				}
				ps.setInt(idx++, offset);
				ps.setInt(idx++, pageSize);
				traceParams.append(" offset=").append(offset)
						.append(" pageSize=").append(pageSize);
				System.out.println("[TRACE ASSIGNED_CASES][CaseDao.findPageInternal] "
						+ "restrictToUserId=" + restrictToUserId
						+ " sqlParams={" + traceParams + "}");

				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							getNullableInt(rs, "PrimaryStatusId"),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
						));
					}
				}
			}
			System.out.println("[TRACE ASSIGNED_CASES][CaseDao.findPageInternal] "
					+ "restrictToUserId=" + restrictToUserId
					+ " resultCount=" + out.size()
					+ " total=" + total);

			return new PagedResult<>(out, page, pageSize, total);
		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to load cases page (page=" + page + ", pageSize=" + pageSize + ")",
					e
			);
		}
	}

	private static String normalizeSearchQuery(String query) {
		if (query == null) {
			return "";
		}
		return query.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String containsPattern(String normalizedQuery) {
		return "%" + normalizedQuery + "%";
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
		return countAll(includeClosedDenied, null);
	}

	public long countMyCases(int userId, boolean includeClosedDenied) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be > 0");
		}
		return countAll(includeClosedDenied, userId);
	}

	private long countAll(boolean includeClosedDenied, Integer restrictToUserId) {
		String userMembershipFilter = restrictToUserId == null
				? ""
				: """
				  AND EXISTS (
				    SELECT 1
				    FROM %s cu_scope
				    WHERE cu_scope.CaseId = c.Id
				      AND cu_scope.UserId = ?
				  )
				""".formatted(CASE_USERS_TABLE);

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
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
				WHERE %s
				  AND c.ShaleClientId = ?
				  %s;
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, activeFilter(schema.deletedColumn(), "c"), userMembershipFilter);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int shaleClientId = requireCurrentShaleClientId(con);
				int idx = 1;
				ps.setInt(idx++, shaleClientId);
				StringBuilder traceParams = new StringBuilder()
						.append("shaleClientId=").append(shaleClientId)
						.append("includeClosedDeniedFlag=").append(includeClosedDenied ? 1 : 0);
				if (restrictToUserId != null) {
					ps.setInt(idx++, restrictToUserId);
					traceParams.append(" restrictToUserId=").append(restrictToUserId)
							.append(" restrictByAnyCaseUserMembership=true");
				}
				System.out.println("[TRACE ASSIGNED_CASES][CaseDao.countAll] "
						+ "restrictToUserId=" + restrictToUserId
						+ " sqlParams={" + traceParams + "}");

				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					long count = rs.getLong(1);
					System.out.println("[TRACE ASSIGNED_CASES][CaseDao.countAll] "
							+ "restrictToUserId=" + restrictToUserId
							+ " count=" + count);
					return count;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to count cases", e);
		}
	}

	public com.shale.core.dto.CaseOverviewDto getOverview(long caseId) {

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CaseNumber,
				  c.Description,
				  c.CallerDate,
				  c.DateOfInjury,
				  c.StatuteOfLimitations,

				  pa.Id    AS PracticeAreaId,
				  pa.Name  AS PracticeAreaName,
				  pa.Color AS PracticeAreaColor,

				  ra.UserId AS ResponsibleAttorneyUserId,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName,

				  current_status.CurrentStatusName,
				  current_status.PrimaryStatusId,
				  current_status.PrimaryStatusColor,

				  callerContact.PrimaryCallerContactId,
				  callerContact.CallerName,

				  clientContact.PrimaryClientContactId,
				  clientContact.ClientName AS ClientName,

				  oppContact.PrimaryOpposingCounselContactId,
				  oppContact.FullName AS OpposingCounselName

				FROM %s c
				LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = ?
				      AND cu.IsPrimary = 1
				    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
				) ra
				LEFT JOIN %s u ON u.id = ra.UserId
				OUTER APPLY (
				    SELECT TOP (1)
				      s.Id    AS PrimaryStatusId,
				      s.Color AS PrimaryStatusColor,
				      s.Name  AS CurrentStatusName
				    FROM %s cs
				    INNER JOIN %s s ON s.Id = cs.StatusId
				    WHERE cs.CaseId = c.Id
				    ORDER BY
				      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
				      cs.UpdatedAt DESC,
				      cs.CreatedAt DESC,
				      cs.Id DESC
				) current_status
				OUTER APPLY (
				    SELECT TOP (1)
				      cc.ContactId AS PrimaryCallerContactId,
				      CASE
				        WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				          OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				        THEN LTRIM(RTRIM(
				              COALESCE(ct.FirstName, '') +
				              CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				              COALESCE(ct.LastName, '')
				            ))
				        ELSE COALESCE(ct.Name, '')
				      END AS CallerName
				    FROM CaseContacts cc
				    INNER JOIN Contacts ct ON ct.Id = cc.ContactId
				    WHERE cc.CaseId = c.Id
				      AND cc.Role = ?
				      AND cc.IsPrimary = 1
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
				) callerContact
				OUTER APPLY (
				    SELECT TOP (1)
				      cc.ContactId AS PrimaryClientContactId,
				      CASE
				        WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				          OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				        THEN LTRIM(RTRIM(
				              COALESCE(ct.FirstName, '') +
				              CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				              COALESCE(ct.LastName, '')
				            ))
				        ELSE COALESCE(ct.Name, '')
				      END AS ClientName
				    FROM CaseContacts cc
				    INNER JOIN Contacts ct ON ct.Id = cc.ContactId
				    WHERE cc.CaseId = c.Id
				      AND cc.Role = ?
				      AND cc.IsPrimary = 1
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
				) clientContact
				OUTER APPLY (
				    SELECT TOP (1)
				      cc.ContactId AS PrimaryOpposingCounselContactId,
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
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ORDER BY cc.UpdatedAt DESC, cc.CreatedAt DESC
				) oppContact
				WHERE c.Id = ?
				  AND %s;
				""".formatted(CASES_TABLE, CASE_USERS_TABLE, USERS_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, activeFilter(schema.deletedColumn(), "c"));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, ROLE_CASECONTACT_CALLER);
				ps.setInt(idx++, ROLE_CASECONTACT_CLIENT);
				ps.setInt(idx++, ROLE_CASECONTACT_OPPOSING_COUNSEL);
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
						getNullableInt(rs, "PrimaryStatusId"),
						rs.getString("PrimaryStatusColor"),
						getNullableInt(rs, "ResponsibleAttorneyUserId"),
						rs.getString("ResponsibleAttorneyName"),
						rs.getString("ResponsibleAttorneyColor"),
						getNullableInt(rs, "PracticeAreaId"),
						rs.getString("PracticeAreaName"),
						rs.getString("PracticeAreaColor"),
						toLocalDate(rs.getDate("CallerDate")),
						toLocalDate(rs.getDate("DateOfInjury")),
						toLocalDate(rs.getDate("StatuteOfLimitations")),
						getNullableInt(rs, "PrimaryCallerContactId"),
						getNullableInt(rs, "PrimaryClientContactId"),
						getNullableInt(rs, "PrimaryOpposingCounselContactId"),
						rs.getString("CallerName"),
						rs.getString("ClientName"),
						rs.getString("OpposingCounselName"),
						team,
						rs.getString("Description")
					);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case overview (caseId=" + caseId + ")", e);
		}
	}

	public com.shale.core.dto.CaseDetailDto getDetail(long caseId) {
		try (Connection con = db.requireConnection()) {
			return selectCaseDetail(con, caseId);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case detail (caseId=" + caseId + ")", e);
		}
	}

	private com.shale.core.dto.CaseDetailDto selectCaseDetail(Connection con, long caseId) throws SQLException {
		CaseSchema schema = resolveCaseSchema(con);
		String sql = """
				SELECT
				  c.Id,
				  c.CaseNumber,
				  c.Name,
				  c.PracticeAreaId,
				  c.Description,
				  c.CallerDate,
				  c.CallerTime,
				  c.AcceptedDate,
				  c.ClosedDate,
				  c.DeniedDate,
				  c.DateOfMedicalNegligence,
				  c.DateMedicalNegligenceWasDiscovered,
				  c.DateOfInjury,
				  c.StatuteOfLimitations,
				  c.TortNoticeDeadline,
				  c.DiscoveryDeadline,
				  c.ClientEstate,
				  c.OfficePrinterCode,
				  c.MedicalRecordsReceived,
				  c.FeeAgreementSigned,
				  c.DateFeeAgreementSigned,
				  c.AcceptedChronology,
				  c.AcceptedConsultantExpertSearch,
				  c.AcceptedTestifyingExpertSearch,
				  c.AcceptedMedicalLiterature,
				  c.AcceptedDetail,
				  c.DeniedChronology,
				  c.DeniedDetail,
				  c.Summary,
				  c.ReceivedUpdates,
				  c.UpdatedAt,
				  c.RowVer,
				  current_status.CurrentStatusName
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
				WHERE c.Id = ?
				  AND %s;
				""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, activeFilter(schema.deletedColumn(), "c"));

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return mapCaseDetail(rs);
			}
		}
	}

	private static com.shale.core.dto.CaseDetailDto mapCaseDetail(ResultSet rs) throws SQLException {
		return new com.shale.core.dto.CaseDetailDto(
				rs.getLong("Id"),
				rs.getString("CaseNumber"),
				rs.getString("Name"),
				rs.getString("Description"),
				rs.getString("CurrentStatusName"),
				getNullableInt(rs, "PracticeAreaId"),
				toLocalDate(rs.getDate("CallerDate")),
				rs.getString("CallerTime"),
				toLocalDate(rs.getDate("AcceptedDate")),
				toLocalDate(rs.getDate("ClosedDate")),
				toLocalDate(rs.getDate("DeniedDate")),
				toLocalDate(rs.getDate("DateOfMedicalNegligence")),
				toLocalDate(rs.getDate("DateMedicalNegligenceWasDiscovered")),
				toLocalDate(rs.getDate("DateOfInjury")),
				toLocalDate(rs.getDate("StatuteOfLimitations")),
				toLocalDate(rs.getDate("TortNoticeDeadline")),
				toLocalDate(rs.getDate("DiscoveryDeadline")),
				rs.getString("ClientEstate"),
				rs.getString("OfficePrinterCode"),
				getNullableBoolean(rs, "MedicalRecordsReceived"),
				getNullableBoolean(rs, "FeeAgreementSigned"),
				toLocalDate(rs.getDate("DateFeeAgreementSigned")),
				getNullableBoolean(rs, "AcceptedChronology"),
				getNullableBoolean(rs, "AcceptedConsultantExpertSearch"),
				getNullableBoolean(rs, "AcceptedTestifyingExpertSearch"),
				getNullableBoolean(rs, "AcceptedMedicalLiterature"),
				rs.getString("AcceptedDetail"),
				getNullableBoolean(rs, "DeniedChronology"),
				rs.getString("DeniedDetail"),
				rs.getString("Summary"),
				rs.getString("ReceivedUpdates"),
				toLocalDateTime(rs.getTimestamp("UpdatedAt")),
				rs.getBytes("RowVer")
		);
	}

	public com.shale.core.dto.CaseDetailDto updateCase(
			long caseId,
			String name,
			String caseNumber,
			String description,
			LocalDate incidentDate,
			LocalDate solDate,
			byte[] expectedRowVer) {
		if (expectedRowVer == null || expectedRowVer.length == 0) {
			throw new IllegalArgumentException("expectedRowVer is required");
		}

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					UPDATE %s
					SET Name = ?,
					    CaseNumber = ?,
					    Description = ?,
					    DateOfInjury = ?,
					    StatuteOfLimitations = ?,
					    UpdatedAt = SYSDATETIME()
					WHERE Id = ?
					  AND RowVer = ?
					  AND %s;
					""".formatted(CASES_TABLE, activeFilter(schema.deletedColumn(), null));

			try (PreparedStatement ps = con.prepareStatement(sql)) {

				ps.setString(1, name);
				ps.setString(2, caseNumber);
				ps.setString(3, description);
				if (incidentDate == null)
					ps.setNull(4, java.sql.Types.DATE);
				else
					ps.setDate(4, java.sql.Date.valueOf(incidentDate));
				if (solDate == null)
					ps.setNull(5, java.sql.Types.DATE);
				else
					ps.setDate(5, java.sql.Date.valueOf(solDate));
				ps.setLong(6, caseId);
				ps.setBytes(7, expectedRowVer);

				int rows = ps.executeUpdate();
				if (rows == 0) {
					return null;
				}
				if (rows == 1) {
					com.shale.core.dto.CaseDetailDto updated = selectCaseDetail(con, caseId);
					if (updated == null) {
						throw new RuntimeException("Case updated but detail row was not found (caseId=" + caseId + ")");
					}
					return updated;
				}
				throw new RuntimeException("Unexpected update row count for caseId=" + caseId + ": " + rows);
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case (caseId=" + caseId + ")", e);
		}
	}

	public com.shale.core.dto.CaseDetailDto updateCaseDetails(
			long caseId,
			String name,
			String caseNumber,
			Integer practiceAreaId,
			String description,
			LocalDate callerDate,
			String callerTime,
			LocalDate acceptedDate,
			LocalDate closedDate,
			LocalDate deniedDate,
			LocalDate dateOfMedicalNegligence,
			LocalDate dateMedicalNegligenceWasDiscovered,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			LocalDate discoveryDeadline,
			String clientEstate,
			String officePrinterCode,
			Boolean medicalRecordsReceived,
			Boolean feeAgreementSigned,
			LocalDate dateFeeAgreementSigned,
			Boolean acceptedChronology,
			Boolean acceptedConsultantExpertSearch,
			Boolean acceptedTestifyingExpertSearch,
			Boolean acceptedMedicalLiterature,
			String acceptedDetail,
			Boolean deniedChronology,
			String deniedDetail,
			String summary,
			String receivedUpdates,
			byte[] expectedRowVer) {
		if (expectedRowVer == null || expectedRowVer.length == 0)
			throw new IllegalArgumentException("expectedRowVer is required");

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					UPDATE %s
					SET Name = ?,
					    CaseNumber = ?,
					    PracticeAreaId = ?,
					    Description = ?,
					    CallerDate = ?,
					    CallerTime = ?,
					    AcceptedDate = ?,
					    ClosedDate = ?,
					    DeniedDate = ?,
					    DateOfMedicalNegligence = ?,
					    DateMedicalNegligenceWasDiscovered = ?,
					    DateOfInjury = ?,
					    StatuteOfLimitations = ?,
					    TortNoticeDeadline = ?,
					    DiscoveryDeadline = ?,
					    ClientEstate = ?,
					    OfficePrinterCode = ?,
					    MedicalRecordsReceived = ?,
					    FeeAgreementSigned = ?,
					    DateFeeAgreementSigned = ?,
					    AcceptedChronology = ?,
					    AcceptedConsultantExpertSearch = ?,
					    AcceptedTestifyingExpertSearch = ?,
					    AcceptedMedicalLiterature = ?,
					    AcceptedDetail = ?,
					    DeniedChronology = ?,
					    DeniedDetail = ?,
					    Summary = ?,
					    ReceivedUpdates = ?,
					    UpdatedAt = SYSDATETIME()
					WHERE Id = ?
					  AND RowVer = ?
					  AND %s;
					""".formatted(CASES_TABLE, activeFilter(schema.deletedColumn(), null));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setString(idx++, name);
			ps.setString(idx++, caseNumber);
			if (practiceAreaId == null)
				ps.setNull(idx++, java.sql.Types.INTEGER);
			else
				ps.setInt(idx++, practiceAreaId);
			ps.setString(idx++, description);
			setNullableDate(ps, idx++, callerDate);
			setNullableString(ps, idx++, callerTime);
			setNullableDate(ps, idx++, acceptedDate);
			setNullableDate(ps, idx++, closedDate);
			setNullableDate(ps, idx++, deniedDate);
			setNullableDate(ps, idx++, dateOfMedicalNegligence);
			setNullableDate(ps, idx++, dateMedicalNegligenceWasDiscovered);
			setNullableDate(ps, idx++, dateOfInjury);
			setNullableDate(ps, idx++, statuteOfLimitations);
			setNullableDate(ps, idx++, tortNoticeDeadline);
			setNullableDate(ps, idx++, discoveryDeadline);
			setNullableString(ps, idx++, clientEstate);
			setNullableString(ps, idx++, officePrinterCode);
			setNullableBoolean(ps, idx++, medicalRecordsReceived);
			setNullableBoolean(ps, idx++, feeAgreementSigned);
			setNullableDate(ps, idx++, dateFeeAgreementSigned);
			setNullableBoolean(ps, idx++, acceptedChronology);
			setNullableBoolean(ps, idx++, acceptedConsultantExpertSearch);
			setNullableBoolean(ps, idx++, acceptedTestifyingExpertSearch);
			setNullableBoolean(ps, idx++, acceptedMedicalLiterature);
			setNullableString(ps, idx++, acceptedDetail);
			setNullableBoolean(ps, idx++, deniedChronology);
			setNullableString(ps, idx++, deniedDetail);
			setNullableString(ps, idx++, summary);
			setNullableString(ps, idx++, receivedUpdates);
			ps.setLong(idx++, caseId);
			ps.setBytes(idx, expectedRowVer);

			int rows = ps.executeUpdate();
			if (rows == 0)
				return null;
			if (rows == 1)
				return selectCaseDetail(con, caseId);
			throw new RuntimeException("Unexpected update row count for caseId=" + caseId + ": " + rows);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case details (caseId=" + caseId + ")", e);
		}
	}

	public boolean softDeleteCase(long caseId, Integer shaleClientId) {
		return updateDeletedState(caseId, shaleClientId, true);
	}

	public boolean restoreCase(long caseId, Integer shaleClientId) {
		return updateDeletedState(caseId, shaleClientId, false);
	}

	private boolean updateDeletedState(long caseId, Integer shaleClientId, boolean deleted) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (shaleClientId == null || shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			if (shaleClientId.intValue() != currentShaleClientId) {
				throw new IllegalArgumentException("shaleClientId does not match current session");
			}

			CaseSchema schema = resolveCaseSchema(con);
			if (schema.deletedColumn() == null || schema.deletedColumn().isBlank()) {
				throw new IllegalStateException("Cases table does not support soft delete.");
			}

			String desiredStateFilter = deleted
					? activeFilter(schema.deletedColumn(), null)
					: "(" + schema.deletedColumn() + " = 1)";

			String sql = """
					UPDATE %s
					SET %s = ?,
					    UpdatedAt = SYSUTCDATETIME()
					WHERE Id = ?
					  AND ShaleClientId = ?
					  AND %s;
					""".formatted(CASES_TABLE, schema.deletedColumn(), desiredStateFilter);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, deleted ? 1 : 0);
				ps.setLong(2, caseId);
				ps.setInt(3, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to " + (deleted ? "soft delete" : "restore") + " case (id=" + caseId + ")", e);
		}
	}



	public List<CaseUpdateDto> listCaseUpdates(long caseId) {
		String sql = """
				SELECT
				  cu.Id,
				  cu.CaseId,
				  cu.NoteText,
				  cu.CreatedAt,
				  cu.CreatedByUserId,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS CreatedByDisplayName
				FROM dbo.CaseUpdates cu
				LEFT JOIN dbo.Users u ON u.Id = cu.CreatedByUserId
				WHERE cu.CaseId = ?
				  AND ISNULL(cu.IsDeleted, 0) = 0
				ORDER BY cu.CreatedAt DESC, cu.Id DESC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setLong(1, caseId);

			try (ResultSet rs = ps.executeQuery()) {
				List<CaseUpdateDto> out = new ArrayList<>();
				while (rs.next()) {
					Integer createdByUserId = getNullableInt(rs, "CreatedByUserId");
					String displayName = safeUserDisplayName(
							rs.getString("CreatedByDisplayName"),
							createdByUserId
					);
					out.add(new CaseUpdateDto(
							rs.getLong("Id"),
							rs.getLong("CaseId"),
							rs.getString("NoteText"),
							toLocalDateTime(rs.getTimestamp("CreatedAt")),
							createdByUserId,
							displayName
					));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case updates (caseId=" + caseId + ")", e);
		}
	}

	public void addCaseTimelineEvent(int caseId,
			int shaleClientId,
			String eventType,
			Integer actorUserId,
			String title,
			String body) {
		if (caseId <= 0)
			throw new IllegalArgumentException("caseId is required.");
		if (shaleClientId <= 0)
			throw new IllegalArgumentException("shaleClientId is required.");

		String normalizedEventType = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
		if (!CaseTimelineEventTypes.ALLOWED.contains(normalizedEventType))
			throw new IllegalArgumentException("Unsupported timeline eventType: " + eventType);

		String normalizedTitle = title == null ? "" : title.trim();
		if (normalizedTitle.isBlank())
			throw new IllegalArgumentException("Timeline event title is required.");
		String normalizedBody = body == null ? null : body.trim();

		String sql = """
				INSERT INTO dbo.CaseTimelineEvents (
				  CaseId,
				  ShaleClientId,
				  EventType,
				  ActorUserId,
				  Title,
				  Body
				)
				VALUES (?, ?, ?, ?, ?, ?);
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, caseId);
			ps.setInt(2, shaleClientId);
			ps.setString(3, normalizedEventType);
			if (actorUserId == null)
				ps.setNull(4, java.sql.Types.INTEGER);
			else
				ps.setInt(4, actorUserId);
			ps.setString(5, normalizedTitle);
			setNullableString(ps, 6, normalizedBody);
			int rows = ps.executeUpdate();
			if (rows != 1) {
				throw new RuntimeException("Unexpected insert row count for case timeline event (caseId=" + caseId + "): " + rows);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to add case timeline event (caseId=" + caseId + ")", e);
		}
	}

	public List<CaseTimelineEventDto> listCaseTimelineEvents(int caseId) {
		if (caseId <= 0)
			throw new IllegalArgumentException("caseId is required.");

		String sql = """
				SELECT
				  cte.Id,
				  cte.CaseId,
				  cte.ShaleClientId,
				  cte.EventType,
				  cte.OccurredAt,
				  cte.ActorUserId,
				  cte.Title,
				  cte.Body,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ActorDisplayName
				FROM dbo.CaseTimelineEvents cte
				INNER JOIN dbo.Cases c ON c.Id = cte.CaseId
				                   AND c.ShaleClientId = cte.ShaleClientId
				LEFT JOIN dbo.Users u ON u.Id = cte.ActorUserId
				WHERE cte.CaseId = ?
				ORDER BY cte.OccurredAt DESC, cte.Id DESC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, caseId);

			try (ResultSet rs = ps.executeQuery()) {
				List<CaseTimelineEventDto> out = new ArrayList<>();
				while (rs.next()) {
					Integer actorUserId = getNullableInt(rs, "ActorUserId");
					out.add(new CaseTimelineEventDto(
							rs.getLong("Id"),
							rs.getInt("CaseId"),
							rs.getInt("ShaleClientId"),
							rs.getString("EventType"),
							toLocalDateTime(rs.getTimestamp("OccurredAt")),
							actorUserId,
							rs.getString("Title"),
							rs.getString("Body"),
							safeUserDisplayName(rs.getString("ActorDisplayName"), actorUserId)
					));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case timeline events (caseId=" + caseId + ")", e);
		}
	}

	public void addCaseUpdate(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
		if (isSystemGeneratedCaseUpdateText(noteText)) {
			return;
		}
		addCaseNote(caseId, shaleClientId, noteText, createdByUserId);
	}

	public void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
		String trimmedText = noteText == null ? "" : noteText.trim();
		if (trimmedText.isBlank()) {
			throw new IllegalArgumentException("Case update text is required.");
		}

		String insertSql = """
				INSERT INTO dbo.CaseUpdates (
				  CaseId,
				  ShaleClientId,
				  NoteText,
				  CreatedAt,
				  CreatedByUserId,
				  UpdatedAt,
				  EditedByUserId
				)
				VALUES (?, ?, ?, SYSDATETIME(), ?, SYSDATETIME(), NULL);
				""";

		String touchCaseSql = """
				UPDATE dbo.Cases
				SET UpdatedAt = SYSDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?;
				""";

		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			try (PreparedStatement ps = con.prepareStatement(insertSql)) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				ps.setString(3, trimmedText);
				if (createdByUserId == null)
					ps.setNull(4, java.sql.Types.INTEGER);
				else
					ps.setInt(4, createdByUserId);

				int rows = ps.executeUpdate();
				if (rows != 1) {
					throw new RuntimeException("Unexpected insert row count for case update (caseId=" + caseId + "): " + rows);
				}
			}

			try (PreparedStatement ps = con.prepareStatement(touchCaseSql)) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				int rows = ps.executeUpdate();
				if (rows != 1) {
					throw new RuntimeException("Unexpected update row count when touching case UpdatedAt (caseId=" + caseId + "): " + rows);
				}
			}

			con.commit();
		} catch (SQLException e) {
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to add case update (caseId=" + caseId + ")", e);
		} finally {
			if (con != null) {
				try {
					con.setAutoCommit(true);
				} catch (SQLException ignored) {
				}
				try {
					con.close();
				} catch (SQLException ignored) {
				}
			}
		}
	}

	private static boolean isSystemGeneratedCaseUpdateText(String noteText) {
		String text = noteText == null ? "" : noteText.trim();
		if (text.isBlank())
			return false;
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("intake created")
				|| lower.contains("changed: from");
	}

	public void softDeleteCaseUpdate(long caseUpdateId, long caseId, int shaleClientId, Integer deletedByUserId) {
		String sql = """
				UPDATE dbo.CaseUpdates
				SET IsDeleted = 1,
				    DeletedAt = SYSDATETIME(),
				    DeletedByUserId = ?,
				    UpdatedAt = SYSDATETIME(),
				    EditedByUserId = COALESCE(?, EditedByUserId)
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND ISNULL(IsDeleted, 0) = 0;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			if (deletedByUserId == null)
				ps.setNull(1, java.sql.Types.INTEGER);
			else
				ps.setInt(1, deletedByUserId);
			if (deletedByUserId == null)
				ps.setNull(2, java.sql.Types.INTEGER);
			else
				ps.setInt(2, deletedByUserId);
			ps.setLong(3, caseUpdateId);
			ps.setLong(4, caseId);
			ps.setInt(5, shaleClientId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to soft delete case update (id=" + caseUpdateId + ")", e);
		}
	}

	// ---- helpers ----

	private static LocalDate toLocalDate(java.sql.Date d) {
		return d == null ? null : d.toLocalDate();
	}

	private static LocalDateTime toLocalDateTime(Timestamp ts) {
		return ts == null ? null : ts.toLocalDateTime();
	}

	private static void setNullableDate(PreparedStatement ps, int idx, LocalDate value) throws SQLException {
		if (value == null)
			ps.setNull(idx, java.sql.Types.DATE);
		else
			ps.setDate(idx, java.sql.Date.valueOf(value));
	}

	private static void setNullableTime(PreparedStatement ps, int idx, LocalTime value) throws SQLException {
		if (value == null)
			ps.setNull(idx, java.sql.Types.TIME);
		else
			ps.setTime(idx, Time.valueOf(value));
	}

	private static void setNullableBoolean(PreparedStatement ps, int idx, Boolean value) throws SQLException {
		if (value == null)
			ps.setNull(idx, java.sql.Types.BIT);
		else
			ps.setBoolean(idx, value);
	}

	private static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
		String trimmed = value == null ? null : value.trim();
		if (trimmed == null || trimmed.isBlank())
			ps.setNull(idx, java.sql.Types.NVARCHAR);
		else
			ps.setString(idx, trimmed);
	}


	private static String safeUserDisplayName(String displayName, Integer userId) {
		String trimmed = displayName == null ? "" : displayName.trim();
		if (!trimmed.isBlank())
			return trimmed;
		if (userId != null)
			return "User #" + userId;
		return "Unknown";
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		Object o = rs.getObject(col);
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.intValue();
		return Integer.valueOf(o.toString());
	}

	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		Object o = rs.getObject(colIndex);
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.intValue();
		return Integer.valueOf(o.toString());
	}


	private static int requireCurrentShaleClientId(Connection con) throws SQLException {
		String sql = "SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT);";
		try (PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next())
				throw new IllegalStateException("ShaleClientId session context is missing.");
			Integer shaleClientId = getNullableInt(rs, 1);
			if (shaleClientId == null || shaleClientId <= 0)
				throw new IllegalStateException("ShaleClientId session context is missing.");
			return shaleClientId;
		}
	}

	private static Boolean getNullableBoolean(ResultSet rs, String col) throws SQLException {
		Object o = rs.getObject(col);
		if (o == null)
			return null;
		if (o instanceof Boolean b)
			return b;
		if (o instanceof Number n)
			return n.intValue() != 0;
		return Boolean.valueOf(o.toString());
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
				ORDER BY cu.RoleId, u.name_last, u.name_first;
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

	public List<ContactRow> listContactsForTenant(int shaleClientId) {
		String baseSql = """
				SELECT
				  Id,
				  LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(FirstName, '') +
				        CASE WHEN COALESCE(FirstName, '') = '' OR COALESCE(LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(LastName, '')
				      ELSE
				        COALESCE(Name, '')
				    END
				  )) AS DisplayName
				FROM Contacts
				WHERE ShaleClientId = ?
				  AND NULLIF(LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(FirstName, '') +
				        CASE WHEN COALESCE(FirstName, '') = '' OR COALESCE(LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(LastName, '')
				      ELSE
				        COALESCE(Name, '')
				    END
				  )), '') IS NOT NULL
				""";

		String orderSql = """
				ORDER BY LastName, FirstName, Name, Id;
				""";

		try (Connection con = db.requireConnection()) {
			boolean hasIsDeleted = contactsHasIsDeletedColumn(con);

			String sql = hasIsDeleted
					? baseSql + "\n  AND (IsDeleted = 0 OR IsDeleted IS NULL)\n" + orderSql
					: baseSql + "\n" + orderSql;

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);

				try (ResultSet rs = ps.executeQuery()) {
					List<ContactRow> out = new ArrayList<>();
					while (rs.next()) {
						String name = rs.getString("DisplayName");
						// Extra safety (should already be filtered in SQL)
						if (name == null || name.isBlank()) {
							continue;
						}
						out.add(new ContactRow(rs.getInt("Id"), name));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list contacts (clientId=" + shaleClientId + ")", e);
		}
	}

	public List<RelatedContactRow> findRelatedContacts(long caseId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}

		String baseSql = """
				SELECT
				  ct.Id,
				  LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(ct.FirstName, '') +
				        CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(ct.LastName, '')
				      ELSE
				        COALESCE(ct.Name, '')
				    END
				  )) AS DisplayName,
				  cc.Role AS RoleId,
				  NULLIF(LTRIM(RTRIM(COALESCE(r.Name, ''))), '') AS RoleName,
				  COALESCE(cc.IsPrimary, 0) AS IsPrimary,
				  NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailPersonal, ''))), '') AS Email,
				  NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneCell, ''))), '') AS Phone
				FROM dbo.CaseContacts cc
				INNER JOIN dbo.Cases c
				  ON c.Id = cc.CaseId
				INNER JOIN dbo.Contacts ct
				  ON ct.Id = cc.ContactId
				LEFT JOIN dbo.Roles r
				  ON r.Id = cc.Role
				 AND r.ShaleClientId = c.ShaleClientId
				WHERE cc.CaseId = ?
				  AND c.ShaleClientId = ?
				  AND ct.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND NULLIF(LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(ct.FirstName, '') +
				        CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(ct.LastName, '')
				      ELSE
				        COALESCE(ct.Name, '')
				    END
				  )), '') IS NOT NULL
				""";

		String orderSql = """
				ORDER BY DisplayName ASC, cc.Role ASC, ct.Id ASC;
				""";

		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			boolean hasIsDeleted = contactsHasIsDeletedColumn(con);

			String sql = hasIsDeleted
					? baseSql + "\n  AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)\n" + orderSql
					: baseSql + "\n" + orderSql;

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setLong(idx++, caseId);
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, shaleClientId);

				List<RelatedContactRow> out = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new RelatedContactRow(
							rs.getInt("Id"),
							rs.getString("DisplayName"),
							(Integer) rs.getObject("RoleId"),
							rs.getString("RoleName"),
							rs.getBoolean("IsPrimary"),
							rs.getString("Email"),
							rs.getString("Phone")));
					}
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load related contacts for case (id=" + caseId + ")", e);
		}
	}

	public List<CaseContactRoleOption> findActiveCaseContactRoles() {
		String sql = """
				SELECT
				  r.Id,
				  r.Name,
				  r.Description
				FROM dbo.Roles r
				WHERE r.ShaleClientId = ?
				  AND r.IsActive = 1
				ORDER BY r.Name ASC, r.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			ps.setInt(1, shaleClientId);
			List<CaseContactRoleOption> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new CaseContactRoleOption(
						rs.getInt("Id"),
						rs.getString("Name"),
						rs.getString("Description")));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load active case contact roles", e);
		}
	}

	public List<SelectableContactRow> findLinkableContacts(long caseId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}

		String sql = """
				SELECT
				  ct.Id,
				  LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(ct.FirstName, '') +
				        CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(ct.LastName, '')
				      ELSE
				        COALESCE(ct.Name, '')
				    END
				  )) AS DisplayName,
				  NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailPersonal, ''))), '') AS Email,
				  NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneCell, ''))), '') AS Phone
				FROM dbo.Contacts ct
				WHERE ct.ShaleClientId = ?
				  AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  AND NULLIF(LTRIM(RTRIM(
				    CASE
				      WHEN (NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName,''))), '') IS NOT NULL)
				        OR (NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName,''))), '') IS NOT NULL)
				      THEN
				        COALESCE(ct.FirstName, '') +
				        CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END +
				        COALESCE(ct.LastName, '')
				      ELSE
				        COALESCE(ct.Name, '')
				    END
				  )), '') IS NOT NULL
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  )
				  AND NOT EXISTS (
				    SELECT 1
				    FROM dbo.CaseContacts cc
				    WHERE cc.CaseId = ?
				      AND cc.ContactId = ct.Id
				  )
				ORDER BY DisplayName ASC, ct.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);

			List<SelectableContactRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new SelectableContactRow(
						rs.getInt("Id"),
						rs.getString("DisplayName"),
						rs.getString("Email"),
						rs.getString("Phone")));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load linkable contacts for case (id=" + caseId + ")", e);
		}
	}

	public boolean linkContactToCase(long caseId, int contactId, int roleId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (contactId <= 0) {
			throw new IllegalArgumentException("contactId must be > 0");
		}
		if (roleId <= 0) {
			throw new IllegalArgumentException("roleId must be > 0");
		}

		String sql = """
				INSERT INTO dbo.CaseContacts (
				  CaseId,
				  ContactId,
				  Role,
				  IsPrimary,
				  Notes,
				  AddedAt,
				  CreatedAt,
				  UpdatedAt
				)
				SELECT
				  ?,
				  ?,
				  ?,
				  0,
				  NULL,
				  SYSUTCDATETIME(),
				  SYSUTCDATETIME(),
				  SYSUTCDATETIME()
				WHERE EXISTS (
				    SELECT 1
				    FROM dbo.Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				)
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = ?
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				)
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Roles r
				    WHERE r.Id = ?
				      AND r.ShaleClientId = ?
				      AND r.IsActive = 1
				)
				  AND NOT EXISTS (
				    SELECT 1
				    FROM dbo.CaseContacts cc
				    WHERE cc.CaseId = ?
				      AND cc.ContactId = ?
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, roleId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, roleId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, contactId);
			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to link contact to case (caseId=" + caseId + ", contactId=" + contactId + ", roleId=" + roleId + ")", e);
		}
	}

	public List<RelatedOrganizationRow> findRelatedOrganizations(long caseId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}

		String sql = """
				SELECT
				  o.Id,
				  o.Name,
				  o.OrganizationTypeId,
				  ot.Name AS OrganizationTypeName,
				  o.Phone,
				  o.Email,
				  o.Website,
				  o.Address1,
				  o.Address2,
				  o.City,
				  o.State,
				  o.PostalCode,
				  o.Country,
				  o.Notes
				FROM CaseOrganizations co
				INNER JOIN Organizations o
				  ON o.Id = co.OrganizationId
				LEFT JOIN OrganizationTypes ot
				  ON ot.OrganizationTypeId = o.OrganizationTypeId
				 AND ot.ShaleClientId = o.ShaleClientId
				WHERE co.CaseId = ?
				  AND o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				ORDER BY o.Name ASC, o.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);

			List<RelatedOrganizationRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new RelatedOrganizationRow(
						rs.getInt("Id"),
						rs.getString("Name"),
						(Integer) rs.getObject("OrganizationTypeId"),
						rs.getString("OrganizationTypeName"),
						rs.getString("Phone"),
						rs.getString("Email"),
						rs.getString("Website"),
						rs.getString("Address1"),
						rs.getString("Address2"),
						rs.getString("City"),
						rs.getString("State"),
						rs.getString("PostalCode"),
						rs.getString("Country"),
						rs.getString("Notes"),
						null
					));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load related organizations for case (id=" + caseId + ")", e);
		}
	}

	public List<SelectableOrganizationRow> findLinkableOrganizations(long caseId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}

		String sql = """
				SELECT
				  o.Id,
				  o.Name,
				  ot.Name AS OrganizationTypeName
				FROM Organizations o
				LEFT JOIN OrganizationTypes ot
				  ON ot.OrganizationTypeId = o.OrganizationTypeId
				 AND ot.ShaleClientId = o.ShaleClientId
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				ORDER BY o.Name ASC, o.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			ps.setInt(1, shaleClientId);

			List<SelectableOrganizationRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new SelectableOrganizationRow(
						rs.getInt("Id"),
						rs.getString("Name"),
						rs.getString("OrganizationTypeName")
					));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load linkable organizations for case (id=" + caseId + ")", e);
		}
	}

	public boolean linkOrganizationToCase(long caseId, int organizationId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}

		String sql = """
				INSERT INTO CaseOrganizations (
				  CaseId,
				  OrganizationId,
				  RoleId,
				  IsPrimary,
				  Notes,
				  CreatedAt,
				  UpdatedAt
				)
				SELECT
				  ?,
				  ?,
				  NULL,
				  0,
				  NULL,
				  SYSUTCDATETIME(),
				  SYSUTCDATETIME()
				WHERE EXISTS (
				    SELECT 1
				    FROM Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				)
				  AND EXISTS (
				    SELECT 1
				    FROM Organizations o
				    WHERE o.Id = ?
				      AND o.ShaleClientId = ?
				      AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				)
				  AND NOT EXISTS (
				    SELECT 1
				    FROM CaseOrganizations co
				    WHERE co.CaseId = ?
				      AND co.OrganizationId = ?
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, organizationId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, organizationId);
			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to link organization to case (caseId=" + caseId + ", orgId=" + organizationId + ")", e);
		}
	}

	public boolean unlinkContactFromCase(long caseId, int contactId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (contactId <= 0) {
			throw new IllegalArgumentException("contactId must be > 0");
		}

		String sql = """
				DELETE cc
				FROM dbo.CaseContacts cc
				WHERE cc.CaseId = ?
				  AND cc.ContactId = ?
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Cases c
				    WHERE c.Id = cc.CaseId
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  )
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = cc.ContactId
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, shaleClientId);
			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to unlink contact from case (caseId=" + caseId + ", contactId=" + contactId + ")", e);
		}
	}

	public boolean unlinkOrganizationFromCase(long caseId, int organizationId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}

		String sql = """
				DELETE co
				FROM CaseOrganizations co
				WHERE co.CaseId = ?
				  AND co.OrganizationId = ?
				  AND EXISTS (
				    SELECT 1
				    FROM Cases c
				    WHERE c.Id = co.CaseId
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  )
				  AND EXISTS (
				    SELECT 1
				    FROM Organizations o
				    WHERE o.Id = co.OrganizationId
				      AND o.ShaleClientId = ?
				      AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, shaleClientId);
			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to unlink organization from case (caseId=" + caseId + ", orgId=" + organizationId + ")", e);
		}
	}

	private static boolean contactsHasIsDeletedColumn(Connection con) throws SQLException {
		String sql = """
				SELECT 1
				FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_SCHEMA = 'dbo'
				  AND TABLE_NAME = 'Contacts'
				  AND COLUMN_NAME = 'IsDeleted';
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			return rs.next();
		}
	}

	public void setPrimaryCaseContact(
			long caseId,
			int shaleClientId,
			int role,
			int contactId,
			Integer changedByUserId,
			String notes) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  DECLARE @now datetime2 = SYSDATETIME();

				  -- New contact name must exist in tenant
				  IF NOT EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = ?
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  )
				  BEGIN
				    THROW 50001, 'Contact not found for tenant.', 1;
				  END

				  -- Clear existing primary for this role
				  UPDATE dbo.CaseContacts
				  SET IsPrimary = 0,
				      UpdatedAt = @now
				  WHERE CaseId = ?
				    AND Role = ?
				    AND IsPrimary = 1;

				  -- Promote existing row if present
				  UPDATE dbo.CaseContacts
				  SET IsPrimary = 1,
				      Notes = ?,
				      UpdatedAt = @now
				  WHERE CaseId = ?
				    AND ContactId = ?
				    AND Role = ?;

				  -- Else insert new row
				  IF @@ROWCOUNT = 0
				  BEGIN
				    INSERT INTO dbo.CaseContacts
				      (CaseId, ContactId, Role, Side, IsPrimary, Notes, AddedAt, CreatedAt, UpdatedAt)
				    VALUES
				      (?, ?, ?, NULL, 1, ?, @now, @now, @now);
				  END

				  COMMIT;
				END TRY
				BEGIN CATCH
				  IF @@TRANCOUNT > 0 ROLLBACK;
				  THROW;
				END CATCH;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int i = 1;

			// tenant contact existence check
			ps.setInt(i++, contactId);
			ps.setInt(i++, shaleClientId);

			// clear primary
			ps.setLong(i++, caseId);
			ps.setInt(i++, role);

			String cleanNotes = (notes == null || notes.isBlank()) ? null : notes.trim();

			// promote existing
			ps.setString(i++, cleanNotes);
			ps.setLong(i++, caseId);
			ps.setInt(i++, contactId);
			ps.setInt(i++, role);

			// insert if missing
			ps.setLong(i++, caseId);
			ps.setInt(i++, contactId);
			ps.setInt(i++, role);
			ps.setString(i++, cleanNotes);

			ps.executeUpdate();

		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to set primary case contact (caseId=" + caseId + ", role=" + role + ")",
					e
			);
		}
	}

	public void setPrimaryStatus(long caseId, int statusId, String notes) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  DECLARE @now datetime2 = SYSDATETIME();
				  DECLARE @oldPrimaryStatusId int = (
				    SELECT TOP 1 cs.StatusId
				    FROM dbo.CaseStatuses cs
				    WHERE cs.CaseId = ?
				      AND cs.EndDate IS NULL
				      AND cs.IsPrimary = 1
				    ORDER BY cs.EffectiveDate DESC, cs.Id DESC
				  );

				  IF (@oldPrimaryStatusId IS NULL OR @oldPrimaryStatusId <> ?)
				  BEGIN
				    -- End any active statuses and clear primary
				    UPDATE dbo.CaseStatuses
				    SET EndDate   = @now,
				        IsPrimary = 0,
				        UpdatedAt = @now
				    WHERE CaseId = ?
				      AND EndDate IS NULL;

				    -- Insert new active primary status row
				    INSERT INTO dbo.CaseStatuses
				        (CaseId, StatusId, EffectiveDate, EndDate, Notes, CreatedAt, UpdatedAt, IsPrimary)
				    VALUES
				        (?, ?, @now, NULL, ?, @now, @now, 1);

				  END

				  COMMIT;
				END TRY
				BEGIN CATCH
				  IF @@TRANCOUNT > 0 ROLLBACK;
				  THROW;
				END CATCH;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int i = 1;
			ps.setLong(i++, caseId);
			ps.setInt(i++, statusId);
			ps.setLong(i++, caseId);
			ps.setLong(i++, caseId);
			ps.setInt(i++, statusId);
			ps.setString(i++, (notes == null || notes.isBlank()) ? null : notes.trim());

			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to set primary status (caseId=" + caseId + ", statusId=" + statusId + ")", e);
		}
	}

	public void populateLifecycleDateIfNull(long caseId, String normalizedStatusName) {
		String normalized = (normalizedStatusName == null) ? "" : normalizedStatusName.trim().toLowerCase(Locale.ROOT);
		if (!"accepted".equals(normalized) && !"denied".equals(normalized) && !"closed".equals(normalized))
			return;

		String sql = """
				UPDATE dbo.Cases
				SET AcceptedDate = CASE WHEN ? = 'accepted' AND AcceptedDate IS NULL THEN CAST(SYSDATETIME() AS date) ELSE AcceptedDate END,
				    DeniedDate = CASE WHEN ? = 'denied' AND DeniedDate IS NULL THEN CAST(SYSDATETIME() AS date) ELSE DeniedDate END,
				    ClosedDate = CASE WHEN ? = 'closed' AND ClosedDate IS NULL THEN CAST(SYSDATETIME() AS date) ELSE ClosedDate END,
				    UpdatedAt = CASE
				                  WHEN (? = 'accepted' AND AcceptedDate IS NULL)
				                    OR (? = 'denied' AND DeniedDate IS NULL)
				                    OR (? = 'closed' AND ClosedDate IS NULL)
				                  THEN SYSDATETIME()
				                  ELSE UpdatedAt
				                END
				WHERE Id = ?;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			ps.setString(i++, normalized);
			ps.setString(i++, normalized);
			ps.setString(i++, normalized);
			ps.setString(i++, normalized);
			ps.setString(i++, normalized);
			ps.setString(i++, normalized);
			ps.setLong(i++, caseId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to populate lifecycle date (caseId=" + caseId + ", status=" + normalized + ")", e);
		}
	}

	public record StatusRow(
			int id,
			String name,
			boolean isClosed,
			int sortOrder,
			String color
	) {
	}

	public void setPracticeArea(long caseId, int shaleClientId, int practiceAreaId) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  DECLARE @now datetime2 = SYSDATETIME();

				  -- Validate practice area exists for tenant and is active/not deleted
				  IF NOT EXISTS (
				    SELECT 1
				    FROM dbo.PracticeAreas pa
				    WHERE pa.Id = ?
				      AND pa.ShaleClientId = ?
				      AND pa.IsActive = 1
				      AND pa.IsDeleted = 0
				  )
				  BEGIN
				    THROW 50001, 'Practice area not found for tenant.', 1;
				  END

				  -- Update case practice area
				  UPDATE dbo.Cases
				  SET PracticeAreaId = ?,
				      UpdatedAt = @now
				  WHERE Id = ?
				    AND (IsDeleted = 0 OR IsDeleted IS NULL);

				  IF (@@ROWCOUNT = 0)
				  BEGIN
				    THROW 50002, 'Case not found.', 1;
				  END

				  COMMIT;
				END TRY
				BEGIN CATCH
				  IF @@TRANCOUNT > 0 ROLLBACK;
				  THROW;
				END CATCH;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int i = 1;
			ps.setInt(i++, practiceAreaId);
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, practiceAreaId);
			ps.setLong(i++, caseId);

			ps.executeUpdate();

		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to set practice area (caseId=" + caseId + ", practiceAreaId=" + practiceAreaId + ")",
					e
			);
		}
	}

	public List<StatusRow> listStatusesForTenant(int shaleClientId) {
		String sql = """
				SELECT Id, Name, IsClosed, SortOrder, Color
				FROM %s
				WHERE ShaleClientId = ?
				ORDER BY SortOrder, Name;
				""".formatted(STATUSES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				List<StatusRow> out = new ArrayList<>();
				while (rs.next()) {
					out.add(new StatusRow(
							rs.getInt("Id"),
							rs.getString("Name"),
							rs.getBoolean("IsClosed"),
							rs.getInt("SortOrder"),
							rs.getString("Color")
					));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list statuses (clientId=" + shaleClientId + ")", e);
		}
	}

	public record PracticeAreaRow(
			int id,
			String name,
			String color
	) {
	}

	public List<PracticeAreaRow> listPracticeAreasForTenant(int shaleClientId) {
		String sql = """
				SELECT Id, Name, Color
				FROM dbo.PracticeAreas
				WHERE ShaleClientId = ?
				  AND IsActive = 1
				  AND IsDeleted = 0
				ORDER BY Name, Id;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				List<PracticeAreaRow> out = new ArrayList<>();
				while (rs.next()) {
					out.add(new PracticeAreaRow(
							rs.getInt("Id"),
							rs.getString("Name"),
							rs.getString("Color")
					));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list practice areas (clientId=" + shaleClientId + ")", e);
		}
	}

	public void setResponsibleAttorney(long caseId, int userId) {
		final String sql = """
				MERGE dbo.CaseUsers AS target
				USING (SELECT ? AS CaseId, ? AS RoleId, CAST(1 AS bit) AS IsPrimary) AS src
				   ON target.CaseId = src.CaseId
				  AND target.RoleId = src.RoleId
				  AND target.IsPrimary = src.IsPrimary
				WHEN MATCHED THEN
				    UPDATE SET UserId = ?, UpdatedAt = SYSUTCDATETIME()
				WHEN NOT MATCHED THEN
				    INSERT (CaseId, UserId, RoleId, IsPrimary, Notes, CreatedAt, UpdatedAt)
				    VALUES (?, ?, ?, CAST(1 AS bit), NULL, SYSDATETIME(), SYSDATETIME());
				""";

		try (Connection c = db.requireConnection();
				PreparedStatement ps = c.prepareStatement(sql)) {

			int i = 1;
			ps.setLong(i++, caseId);
			ps.setInt(i++, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setInt(i++, userId);

			ps.setLong(i++, caseId);
			ps.setInt(i++, userId);
			ps.setInt(i++, ROLE_RESPONSIBLE_ATTORNEY);

			ps.executeUpdate();

		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to set responsible attorney (caseId=" + caseId + ", userId=" + userId + ")",
					e
			);
		}
	}

	public record UserRow(int id, String displayName, String color) {
	}

	public List<UserRow> listAttorneysForTenant(int shaleClientId) {
		String baseSql = """
				SELECT
				  u.Id,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS DisplayName,
				  u.Color
				FROM dbo.Users u
				WHERE u.ShaleClientId = ?
				  AND COALESCE(u.is_attorney, 0) = 1
				  AND NULLIF(LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )), '') IS NOT NULL
				""";

		String orderSql = """
				ORDER BY u.name_last, u.name_first, u.Id;
				""";

		try (Connection con = db.requireConnection()) {

			boolean hasIsActive = tableHasColumn(con, "Users", "IsActive");
			boolean hasIsDeleted = tableHasColumn(con, "Users", "IsDeleted");
			boolean hasIsDeletedLower = tableHasColumn(con, "Users", "is_deleted");
			StringBuilder sql = new StringBuilder(baseSql);

			if (hasIsActive) {
				sql.append("\n  AND (u.IsActive = 1 OR u.IsActive IS NULL)\n");
			} else if (hasIsDeletedLower) {
				sql.append("\n  AND (u.is_deleted = 0 OR u.is_deleted IS NULL)\n");
			}
			if (hasIsDeleted) {
				sql.append("\n  AND (u.IsDeleted = 0 OR u.IsDeleted IS NULL)\n");
			}

			sql.append(orderSql);

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, shaleClientId);

				try (ResultSet rs = ps.executeQuery()) {
					List<UserRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new UserRow(
								rs.getInt("Id"),
								rs.getString("DisplayName"),
								rs.getString("Color")
						));
					}
					return out;
				}
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to list attorneys (clientId=" + shaleClientId + ")", e);
		}
	}

	private static boolean tableHasColumn(Connection con, String tableName, String columnName) throws SQLException {
		String sql = """
				SELECT 1
				FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_SCHEMA = 'dbo'
				  AND TABLE_NAME = ?
				  AND COLUMN_NAME = ?;
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, tableName);
			ps.setString(2, columnName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public CaseRow getCaseRow(long caseId) {
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					SELECT
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  current_status.PrimaryStatusId,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId
					    FROM %s cs
					    INNER JOIN %s s ON s.Id = cs.StatusId
					    WHERE cs.CaseId = c.Id
					    ORDER BY
					      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
					      cs.UpdatedAt DESC,
					      cs.CreatedAt DESC,
					      cs.Id DESC
					) current_status
					OUTER APPLY (
					    SELECT TOP (1) cu.UserId
					    FROM %s cu
					    WHERE cu.CaseId = c.Id
					      AND cu.RoleId = ?
					      AND cu.IsPrimary = 1
					    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) ra
					LEFT JOIN %s u
					  ON u.id = ra.UserId
					WHERE c.Id = ?
					  AND %s;
					""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE,
					activeFilter(schema.deletedColumn(), "c"));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setLong(2, caseId);

				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next())
						return null;

					return new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							getNullableInt(rs, "PrimaryStatusId"),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
					);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case row (caseId=" + caseId + ")", e);
		}
	}


	public CaseRow getMyCaseRow(int userId, long caseId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be > 0");
		}
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					SELECT
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  current_status.PrimaryStatusId,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId
					    FROM %s cs
					    INNER JOIN %s s ON s.Id = cs.StatusId
					    WHERE cs.CaseId = c.Id
					    ORDER BY
					      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
					      cs.UpdatedAt DESC,
					      cs.CreatedAt DESC,
					      cs.Id DESC
					) current_status
					OUTER APPLY (
					    SELECT TOP (1) cu.UserId
					    FROM %s cu
					    WHERE cu.CaseId = c.Id
					      AND cu.RoleId = ?
					      AND cu.IsPrimary = 1
					    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) ra
					LEFT JOIN %s u
					  ON u.id = ra.UserId
					WHERE c.Id = ?
					  AND %s
					  AND EXISTS (
					    SELECT 1
					    FROM %s cu_scope
					    WHERE cu_scope.CaseId = c.Id
					      AND cu_scope.UserId = ?
					  );
					""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE,
					activeFilter(schema.deletedColumn(), "c"), CASE_USERS_TABLE);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setLong(2, caseId);
				ps.setInt(3, userId);

				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next())
						return null;

					return new CaseRow(
							rs.getLong("Id"),
							rs.getString("Name"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							getNullableInt(rs, "PrimaryStatusId"),
							getNullableInt(rs, "ResponsibleAttorneyId"),
							rs.getString("ResponsibleAttorneyName"),
							rs.getString("ResponsibleAttorneyColor")
					);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load my case row (userId=" + userId + ", caseId=" + caseId + ")", e);
		}
	}

	public record CaseUserTeamRow(
			int userId,
			String displayName,
			String color,
			String initials,
			int roleId,
			boolean isPrimary
	) {
	}

	public record TeamAssignmentRow(int userId, int roleId) {
	}

	public record CaseUserRoleRow(int userId, int roleId) {
	}

	public List<CaseUserRoleRow> listCaseUserRoles(long caseId) {
		String sql = """
				SELECT UserId, RoleId
				FROM dbo.CaseUsers
				WHERE CaseId = ?
				  AND RoleId IN (4,5,7,11,12,13,14);
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setLong(1, caseId);

			try (ResultSet rs = ps.executeQuery()) {
				List<CaseUserRoleRow> out = new ArrayList<>();
				while (rs.next()) {
					out.add(new CaseUserRoleRow(
							rs.getInt("UserId"),
							rs.getInt("RoleId")
					));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case user roles (caseId=" + caseId + ")", e);
		}
	}

	public List<UserRow> listUsersForTenant(int shaleClientId) {
		String baseSql = """
				SELECT
				  u.Id,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS DisplayName,
				  u.Color
				FROM dbo.Users u
				WHERE u.ShaleClientId = ?
				  AND NULLIF(LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )), '') IS NOT NULL
				""";

		String orderSql = """
				ORDER BY u.name_last, u.name_first, u.Id;
				""";

		try (Connection con = db.requireConnection()) {

			boolean hasIsActive = tableHasColumn(con, "Users", "IsActive");
			boolean hasIsDeleted = tableHasColumn(con, "Users", "IsDeleted");
			boolean hasIsDeletedLower = tableHasColumn(con, "Users", "is_deleted"); // in case your column is lower-case style

			StringBuilder sql = new StringBuilder(baseSql);

			if (hasIsActive) {
				sql.append("\n  AND (u.IsActive = 1 OR u.IsActive IS NULL)\n");
			}
			if (hasIsDeleted) {
				sql.append("\n  AND (u.IsDeleted = 0 OR u.IsDeleted IS NULL)\n");
			}
			if (hasIsDeletedLower) {
				sql.append("\n  AND (u.is_deleted = 0 OR u.is_deleted IS NULL)\n");
			}

			sql.append(orderSql);

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, shaleClientId);

				try (ResultSet rs = ps.executeQuery()) {
					List<UserRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new UserRow(
								rs.getInt("Id"),
								rs.getString("DisplayName"),
								rs.getString("Color")
						));
					}
					return out;
				}
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to list users (clientId=" + shaleClientId + ")", e);
		}
	}

	public List<CaseUserTeamRow> listCaseTeamRows(long caseId) {
		String sql = """
				SELECT
				  cu.UserId,
				  cu.RoleId,
				  cu.IsPrimary,
				  u.Color,
				  u.Initials,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS DisplayName
				FROM dbo.CaseUsers cu
				INNER JOIN dbo.Users u ON u.Id = cu.UserId
				WHERE cu.CaseId = ?
				ORDER BY
				  CASE WHEN cu.RoleId = 4 AND cu.IsPrimary = 1 THEN 0 ELSE 1 END,
				  cu.RoleId,
				  u.name_last,
				  u.name_first,
				  u.Id;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setLong(1, caseId);

			try (ResultSet rs = ps.executeQuery()) {
				List<CaseUserTeamRow> out = new ArrayList<>();
				while (rs.next()) {
					out.add(new CaseUserTeamRow(
							rs.getInt("UserId"),
							rs.getString("DisplayName"),
							rs.getString("Color"),
							rs.getString("Initials"),
							rs.getInt("RoleId"),
							rs.getBoolean("IsPrimary")
					));
				}
				return out;
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case team (caseId=" + caseId + ")", e);
		}
	}

	public void replaceCaseTeamAssignments(long caseId, List<TeamAssignmentRow> assignments) {

		final String deleteExisting = """
				DELETE FROM dbo.CaseUsers
				WHERE CaseId = ?
				  AND RoleId IN (4,5,7,11,12,13,14);
				""";

		final String insertRow = """
				INSERT INTO dbo.CaseUsers (CaseId, UserId, RoleId, IsPrimary, Notes, CreatedAt, UpdatedAt)
				VALUES (?, ?, ?, ?, NULL, SYSDATETIME(), SYSDATETIME());
				""";

		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			try (PreparedStatement ps = con.prepareStatement(deleteExisting)) {
				ps.setLong(1, caseId);
				ps.executeUpdate();
			}

			if (assignments != null && !assignments.isEmpty()) {
				try (PreparedStatement ps = con.prepareStatement(insertRow)) {
					for (TeamAssignmentRow a : assignments) {
						boolean isPrimary = (a.roleId() == ROLE_RESPONSIBLE_ATTORNEY);

						ps.setLong(1, caseId);
						ps.setInt(2, a.userId());
						ps.setInt(3, a.roleId());
						ps.setBoolean(4, isPrimary);
						ps.addBatch();
					}
					ps.executeBatch();
				}
			}

			con.commit();

		} catch (SQLException e) {
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to replace case team (caseId=" + caseId + ")", e);
		} finally {
			if (con != null) {
				try {
					con.setAutoCommit(true);
				} catch (SQLException ignored) {
				}
				try {
					con.close();
				} catch (SQLException ignored) {
				}
			}
		}
	}

	public Set<Integer> listAttorneyUserIdsForTenant(int shaleClientId) {
		String sql = """
				SELECT u.Id
				FROM dbo.Users u
				WHERE u.ShaleClientId = ?
				  AND u.is_attorney = 1
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				Set<Integer> out = new HashSet<>();
				while (rs.next())
					out.add(rs.getInt(1));
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list attorney user ids (clientId=" + shaleClientId + ")", e);
		}
	}

	private static String activeFilter(String deletedColumn, String alias) {
		if (deletedColumn == null || deletedColumn.isBlank()) {
			return "1 = 1";
		}
		String prefix = alias == null || alias.isBlank() ? deletedColumn : alias + "." + deletedColumn;
		return "(" + prefix + " = 0 OR " + prefix + " IS NULL)";
	}

	private static CaseSchema resolveCaseSchema(Connection con) throws SQLException {
		return new CaseSchema(existingColumn(con, CASES_TABLE, List.of("IsDeleted", "is_deleted")));
	}

	private static String existingColumn(Connection con, String tableName, List<String> candidates) throws SQLException {
		if (candidates == null) {
			return null;
		}
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank() && tableHasColumn(con, tableName, candidate)) {
				return candidate;
			}
		}
		return null;
	}
}

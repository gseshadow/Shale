package com.shale.data.dao;


import java.sql.Connection;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.shale.core.dto.CasePartyDto;
import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseTimelineEventDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkContactOptionDto;
import com.shale.core.dto.CaseLinkShareDto;
import com.shale.core.dto.ContactSharedCaseLinkDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.CaseStatusHistoryDto;
import com.shale.core.dto.CaseStatusReportRowDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.RecentCaseUpdateActivityDto;
import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.semantics.RoleSemantics;
import com.shale.core.service.CaseServicePort.CaseLinkShareDraft;
import com.shale.core.service.CaseServicePort.CaseLinkShareUpdate;
import com.shale.core.service.CaseServicePort.CaseLinkShareRemoval;

public final class CaseDao {

	private static final Logger LOG = Logger.getLogger(CaseDao.class.getName());
	private static final String CASES_TABLE = "Cases";
	private static final String CASE_USERS_TABLE = "CaseUsers";
	private static final String USERS_TABLE = "Users";

	public static final String LIFECYCLE_KEY_ACCEPTED = "accepted";
	public static final String LIFECYCLE_KEY_DENIED = "denied";
	public static final String LIFECYCLE_KEY_CLOSED = "closed";
	private static final String CASE_STATUSES_TABLE = "CaseStatuses";
	private static final String STATUSES_TABLE = "Statuses";
	// CaseUsers.RoleId (int) for Responsible Attorney
	private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;
	private static final int ROLE_LEGAL_ASSISTANT = RoleSemantics.ROLE_LEGAL_ASSISTANT;
	private static final String PARTY_ROLE_NAME_CALLER = "caller";
	private static final String PARTY_ROLE_NAME_PARTY = "party";
	private static final String PARTY_ROLE_NAME_COUNSEL = "counsel";
	private static final Map<String, String> BUILTIN_PARTY_ROLE_DISPLAY_NAMES = Map.of(
			PARTY_ROLE_NAME_CALLER, "Caller",
			PARTY_ROLE_NAME_PARTY, "Party",
			PARTY_ROLE_NAME_COUNSEL, "Counsel");
	private static final String PARTY_ROLES_TABLE = "PartyRoles";
	private static final String PARTY_SIDE_KEY_REPRESENTED = "represented";
	private static final String PARTY_SIDE_KEY_OPPOSING = "opposing";
	private static final String PARTY_SIDE_KEY_NEUTRAL = "neutral";
	private static final String PARTY_SIDES_TABLE = "PartySides";
	public static final String PRACTICE_AREA_KEY_MEDICAL_MALPRACTICE = "medical_malpractice";
	public static final String PRACTICE_AREA_KEY_PERSONAL_INJURY = "personal_injury";
	public static final String PRACTICE_AREA_KEY_SEXUAL_ASSAULT = "sexual_assault";

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
		public static final String OFFICE_CASE_CODE_CHANGED = "OFFICE_CASE_CODE_CHANGED";
		public static final String DESCRIPTION_CHANGED = "DESCRIPTION_CHANGED";
		public static final String SUMMARY_UPDATED = "SUMMARY_UPDATED";
		public static final String ACCEPTED_DETAIL_UPDATED = "ACCEPTED_DETAIL_UPDATED";
		public static final String DENIED_DETAIL_UPDATED = "DENIED_DETAIL_UPDATED";
		public static final String PRACTICE_AREA_CHANGED = "PRACTICE_AREA_CHANGED";
		public static final String USER_NOTE_ADDED = "USER_NOTE_ADDED";
		public static final String INTAKE_DATE_CHANGED = "INTAKE_DATE_CHANGED";
		public static final String INTAKE_TIME_CHANGED = "INTAKE_TIME_CHANGED";
		public static final String ACCEPTED_DATE_CHANGED = "ACCEPTED_DATE_CHANGED";
		public static final String CLOSED_DATE_CHANGED = "CLOSED_DATE_CHANGED";
		public static final String DENIED_DATE_CHANGED = "DENIED_DATE_CHANGED";
		public static final String MEDICAL_MALPRACTICE_DATE_CHANGED = "MEDICAL_MALPRACTICE_DATE_CHANGED";
		public static final String MEDICAL_MALPRACTICE_DISCOVERY_DATE_CHANGED = "MEDICAL_MALPRACTICE_DISCOVERY_DATE_CHANGED";
		public static final String INJURY_DATE_CHANGED = "INJURY_DATE_CHANGED";
		public static final String STATUTE_OF_LIMITATIONS_CHANGED = "STATUTE_OF_LIMITATIONS_CHANGED";
		public static final String TORT_NOTICE_DEADLINE_CHANGED = "TORT_NOTICE_DEADLINE_CHANGED";
		public static final String DISCOVERY_DEADLINE_CHANGED = "DISCOVERY_DEADLINE_CHANGED";
		public static final String FEE_AGREEMENT_DATE_CHANGED = "FEE_AGREEMENT_DATE_CHANGED";
		public static final String ESTATE_CASE_CHANGED = "ESTATE_CASE_CHANGED";
		public static final String MEDICAL_RECORDS_REQUESTED_CHANGED = "MEDICAL_RECORDS_REQUESTED_CHANGED";
		public static final String FEE_AGREEMENT_SIGNED_CHANGED = "FEE_AGREEMENT_SIGNED_CHANGED";
		public static final String ACCEPTED_CHRONOLOGY_CHANGED = "ACCEPTED_CHRONOLOGY_CHANGED";
		public static final String CONSULTANT_EXPERT_SEARCH_CHANGED = "CONSULTANT_EXPERT_SEARCH_CHANGED";
		public static final String TESTIFYING_EXPERT_SEARCH_CHANGED = "TESTIFYING_EXPERT_SEARCH_CHANGED";
		public static final String MEDICAL_LITERATURE_CHANGED = "MEDICAL_LITERATURE_CHANGED";
		public static final String DENIED_CHRONOLOGY_CHANGED = "DENIED_CHRONOLOGY_CHANGED";
		public static final String RECEIVED_UPDATES_CHANGED = "RECEIVED_UPDATES_CHANGED";

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
				OFFICE_CASE_CODE_CHANGED,
				DESCRIPTION_CHANGED,
				SUMMARY_UPDATED,
				ACCEPTED_DETAIL_UPDATED,
				DENIED_DETAIL_UPDATED,
				PRACTICE_AREA_CHANGED,
				USER_NOTE_ADDED,
				INTAKE_DATE_CHANGED,
				INTAKE_TIME_CHANGED,
				ACCEPTED_DATE_CHANGED,
				CLOSED_DATE_CHANGED,
				DENIED_DATE_CHANGED,
				MEDICAL_MALPRACTICE_DATE_CHANGED,
				MEDICAL_MALPRACTICE_DISCOVERY_DATE_CHANGED,
				INJURY_DATE_CHANGED,
				STATUTE_OF_LIMITATIONS_CHANGED,
				TORT_NOTICE_DEADLINE_CHANGED,
				DISCOVERY_DEADLINE_CHANGED,
				FEE_AGREEMENT_DATE_CHANGED,
				ESTATE_CASE_CHANGED,
				MEDICAL_RECORDS_REQUESTED_CHANGED,
				FEE_AGREEMENT_SIGNED_CHANGED,
				ACCEPTED_CHRONOLOGY_CHANGED,
				CONSULTANT_EXPERT_SEARCH_CHANGED,
				TESTIFYING_EXPERT_SEARCH_CHANGED,
				MEDICAL_LITERATURE_CHANGED,
				DENIED_CHRONOLOGY_CHANGED,
				RECEIVED_UPDATES_CHANGED
		);

		private CaseTimelineEventTypes() {
		}
	}

	public enum CaseSort {
		INTAKE_NEWEST,
		INTAKE_OLDEST,
		STATUTE_SOONEST,
		STATUTE_LATEST,
		TORT_NOTICE_SOONEST,
		UPDATED_OLDEST,
		UPDATED_NEWEST,
		CASE_NAME_ASC,
		CASE_NAME_DESC,
		RESPONSIBLE_ATTORNEY_ASC,
		RESPONSIBLE_ATTORNEY_DESC,
		CASE_STATUS_ASC,
		CASE_STATUS_DESC
	}

	private final DbSessionProvider db;
	private final PhiAuditService phiAuditService;

	public CaseDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
		this.phiAuditService = new PhiAuditService(new AuditLogDao(this.db));
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
			String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent,
			String primaryStatusName,
			String primaryStatusColor,
			String practiceAreaColor,
			String clientName,
			String opposingPartiesName,
			String latestCaseUpdate,
			String description,
			LocalDate dateOfIncident,
			LocalDate tortClaimsNoticeDeadline,
			LocalDateTime updatedAt
	) {
		public CaseRow(long id, String name, LocalDate intakeDate, LocalDate statuteOfLimitationsDate,
				Integer primaryStatusId, Integer responsibleAttorneyId, String responsibleAttorneyName,
				String responsibleAttorneyColor, Boolean nonEngagementLetterSent) {
			this(id, name, intakeDate, statuteOfLimitationsDate, primaryStatusId, responsibleAttorneyId,
					responsibleAttorneyName, responsibleAttorneyColor, nonEngagementLetterSent, null, null, null, null, null, null, null, null, null, null);
		}

		public CaseRow(long id, String name, LocalDate intakeDate, LocalDate statuteOfLimitationsDate,
				Integer primaryStatusId, Integer responsibleAttorneyId, String responsibleAttorneyName,
				String responsibleAttorneyColor, Boolean nonEngagementLetterSent,
				String primaryStatusName, String primaryStatusColor, String practiceAreaColor,
				String clientName, String opposingPartiesName, String latestCaseUpdate, String description,
				LocalDate dateOfIncident, LocalDate tortClaimsNoticeDeadline) {
			this(id, name, intakeDate, statuteOfLimitationsDate, primaryStatusId, responsibleAttorneyId,
					responsibleAttorneyName, responsibleAttorneyColor, nonEngagementLetterSent,
					primaryStatusName, primaryStatusColor, practiceAreaColor, clientName, opposingPartiesName,
					latestCaseUpdate, description, dateOfIncident, tortClaimsNoticeDeadline, null);
		}
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
			String side,
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

	public record PartyRoleRow(
			long id,
			String name
	) {
	}

	public record PartySideRow(
			Long id,
			String name,
			String systemKey
	) {
	}

	private record CaseSchema(String deletedColumn, String rowVersionColumn) {
		String rowVersionSelectExpression(String alias) {
			if (rowVersionColumn == null || rowVersionColumn.isBlank()) {
				return "NULL AS RowVer";
			}
			String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
			return prefix + rowVersionColumn + " AS RowVer";
		}
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
			String callerAddress,
			String callerEmail,
			List<NewIntakePendingParty> pendingParties,
			Integer createdByUserId
	) {
	}

	public record NewIntakePendingParty(
			String entityType,
			Long entityId,
			Long partyRoleId,
			String side,
			boolean primary,
			String notes,
			boolean createNew,
			String contactFirstName,
			String contactLastName,
			String organizationName,
			Integer organizationTypeId
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
			System.out.println("[IntakeCreate] start shaleClientId=" + request.shaleClientId()
					+ " caseName='" + safeLogValue(request.caseName()) + "'");
			ensureRequiredPartyRolesForTenant(con, request.shaleClientId());
			System.out.println("[IntakeCreate] required party roles verified for shaleClientId=" + request.shaleClientId());

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

			int callerContactId = resolveCallerContactId(con, request, clientContactId, now);
			System.out.println("[IntakeCreate] contacts created clientContactId=" + clientContactId + " callerContactId=" + callerContactId);

			long caseId = insertCase(con, request, now);
			System.out.println("[IntakeCreate] case row created caseId=" + caseId);
			insertCaseParty(con, caseId, clientContactId, PARTY_ROLE_NAME_PARTY, PARTY_SIDE_KEY_REPRESENTED, true, now, request.shaleClientId());
			insertCaseParty(con, caseId, callerContactId, PARTY_ROLE_NAME_CALLER, PARTY_SIDE_KEY_REPRESENTED, true, now, request.shaleClientId());
			System.out.println("[IntakeCreate] default case parties linked for caseId=" + caseId);
			List<NewIntakePendingParty> pendingParties = request.pendingParties() == null ? List.of() : request.pendingParties();
			for (NewIntakePendingParty pending : pendingParties) {
				if (pending == null || pending.partyRoleId() == null || pending.partyRoleId().longValue() <= 0) {
					continue;
				}
				String entityType = pending.entityType() == null ? "" : pending.entityType().trim().toLowerCase(Locale.ROOT);
				Long entityId = pending.entityId();
				if (pending.createNew()) {
					if ("contact".equals(entityType)) {
						entityId = Long.valueOf(insertContact(con,
								buildFullName(pending.contactFirstName(), pending.contactLastName()),
								pending.contactFirstName(),
								pending.contactLastName(),
								null,
								null,
								null,
								null,
								null,
								false,
								false,
								request.shaleClientId(),
								now));
					} else if ("organization".equals(entityType)) {
						entityId = Long.valueOf(insertOrganization(con,
								request.shaleClientId(),
								pending.organizationTypeId(),
								pending.organizationName(),
								now));
					}
				}
				if (entityId == null || entityId.longValue() <= 0) {
					continue;
				}
				Long contactId = "contact".equals(entityType) ? entityId : null;
				Long organizationId = "organization".equals(entityType) ? entityId : null;
				if (contactId == null && organizationId == null) {
					continue;
				}
				insertCasePartyWithValidation(
						con,
						caseId,
						contactId,
						organizationId,
						pending.partyRoleId().longValue(),
						pending.side(),
						pending.primary(),
						pending.notes(),
						request.shaleClientId(),
						now);
			}
			normalizeCasePartyRelationshipPrimaries(con, caseId, request.shaleClientId());
			System.out.println("[IntakeCreate] party primary normalization completed caseId=" + caseId);
			insertCaseStatus(con, caseId, request.statusId(), now);
			System.out.println("[IntakeCreate] primary status linked caseId=" + caseId + " statusId=" + request.statusId());

			con.commit();
			System.out.println("[IntakeCreate] committed caseId=" + caseId + " shaleClientId=" + request.shaleClientId());
			return new NewIntakeCreateResult(caseId, clientContactId, callerContactId);
		} catch (SQLException e) {
			System.err.println("[IntakeCreate] failed shaleClientId=" + request.shaleClientId() + " error=" + e.getMessage());
			e.printStackTrace(System.err);
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

	private void ensureRequiredPartyRolesForTenant(Connection con, int shaleClientId) throws SQLException {
		ensurePartyRoleExistsForTenant(con, shaleClientId, PARTY_ROLE_NAME_PARTY);
		ensurePartyRoleExistsForTenant(con, shaleClientId, PARTY_ROLE_NAME_CALLER);
	}

	private void ensurePartyRoleExistsForTenant(Connection con, int shaleClientId, String roleSystemKey) throws SQLException {
		Long existingId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, roleSystemKey);
		if (existingId != null && existingId.longValue() > 0) {
			return;
		}
		boolean hasSystemKey = tableHasColumn(con, PARTY_ROLES_TABLE, "SystemKey");
		String displayName = BUILTIN_PARTY_ROLE_DISPLAY_NAMES.getOrDefault(roleSystemKey, roleSystemKey);
		String insertSql = hasSystemKey
				? "INSERT INTO dbo.PartyRoles (ShaleClientId, Name, SystemKey) VALUES (?, ?, ?);"
				: "INSERT INTO dbo.PartyRoles (ShaleClientId, Name) VALUES (?, ?);";
		try (PreparedStatement ps = con.prepareStatement(insertSql)) {
			int i = 1;
			ps.setInt(i++, shaleClientId);
			ps.setString(i++, displayName);
			if (hasSystemKey) {
				ps.setString(i++, roleSystemKey);
			}
			int rows = ps.executeUpdate();
			if (rows != 1) {
				throw new RuntimeException("Failed to seed missing party role: " + roleSystemKey);
			}
			System.out.println("[IntakeCreate] seeded missing party role roleSystemKey=" + roleSystemKey + " shaleClientId=" + shaleClientId);
		}
		Long seededId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, roleSystemKey);
		if (seededId == null || seededId.longValue() <= 0) {
			throw new IllegalStateException("Party role missing for tenant after seed attempt (role=" + roleSystemKey + ", shaleClientId=" + shaleClientId + ")");
		}
	}

	private static String safeLogValue(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.length() <= 80) {
			return trimmed;
		}
		return trimmed.substring(0, 80) + "…";
	}

	private int resolveCallerContactId(Connection con, NewIntakeCreateRequest request, int clientContactId, Timestamp now) throws SQLException {
		if (request.callerIsClient()) {
			return clientContactId;
		}
		return insertContact(con,
				buildFullName(request.callerFirstName(), request.callerLastName()),
				request.callerFirstName(),
				request.callerLastName(),
				request.callerAddress(),
				request.callerPhone(),
				request.callerEmail(),
				null,
				null,
				false,
				false,
				request.shaleClientId(),
				now);
	}

	private int insertOrganization(Connection con, int shaleClientId, Integer organizationTypeId, String organizationName, Timestamp now) throws SQLException {
		if (organizationTypeId == null || organizationTypeId.intValue() <= 0) {
			throw new RuntimeException("Organization Type is required.");
		}
		String sql = """
				INSERT INTO dbo.Organizations (
				  OrganizationTypeId,
				  Name,
				  IsDeleted,
				  CreatedAt,
				  UpdatedAt,
				  ShaleClientId
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, 0, ?, ?, ?);
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			ps.setInt(i++, organizationTypeId.intValue());
			setNullableString(ps, i++, organizationName);
			ps.setTimestamp(i++, now);
			ps.setTimestamp(i++, now);
			ps.setInt(i++, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new RuntimeException("Failed to create organization.");
				}
				return rs.getInt(1);
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
		validatePracticeAreaForTenant(con, request.shaleClientId(), request.practiceAreaId());
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
				  MedicalRecordsRequested,
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

	private void validatePracticeAreaForTenant(Connection con, int shaleClientId, int practiceAreaId) throws SQLException {
		if (shaleClientId <= 0 || practiceAreaId <= 0) {
			throw new IllegalArgumentException("practiceAreaId is required.");
		}
		String sql = """
				SELECT 1
				FROM dbo.PracticeAreas
				WHERE Id = ?
				  AND (ShaleClientId = ? OR ShaleClientId IS NULL)
				  AND IsActive = 1
				  AND IsDeleted = 0;
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, practiceAreaId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return;
				}
			}
		}
		System.err.println("[IntakeCreate] invalid practice area selection shaleClientId=" + shaleClientId
				+ " practiceAreaId=" + practiceAreaId + " (no matching effective active PracticeAreas row)");
		throw new IllegalArgumentException("Selected practice area is invalid for this tenant.");
	}

	private void insertCaseParty(
			Connection con,
			long caseId,
			int contactId,
			String roleSystemKey,
			String side,
			boolean primary,
			Timestamp now,
			int shaleClientId) throws SQLException {
		Long partyRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, roleSystemKey);
		if (partyRoleId == null)
			throw new RuntimeException("Failed to create case party (role=" + roleSystemKey + ").");
		String sql = """
				INSERT INTO dbo.CaseParties (
				  CaseId,
				  ContactId,
				  OrganizationId,
				  PartyRoleId,
				  Side,
				  IsPrimary,
				  Notes,
				  CreatedAt,
				  UpdatedAt
				)
				SELECT
				  ?, ?, NULL, ?, ?, ?, NULL, ?, ?
				WHERE EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = ?
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  );
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, contactId);
			ps.setLong(3, partyRoleId.longValue());
			ps.setString(4, side);
			ps.setBoolean(5, primary);
			ps.setTimestamp(6, now);
			ps.setTimestamp(7, now);
			ps.setInt(8, contactId);
			ps.setInt(9, shaleClientId);
			int rows = ps.executeUpdate();
			if (rows != 1)
				throw new RuntimeException("Failed to create case party (role=" + roleSystemKey + ").");
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

	public long createBasicCase(
			int shaleClientId,
			String caseName,
			String caseNumber,
			LocalDate callerDate,
			int practiceAreaId,
			int responsibleAttorneyUserId,
			int statusId,
			String description,
			String summary,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			Integer createdByUserId) {
		if (shaleClientId <= 0)
			throw new IllegalArgumentException("shaleClientId is required.");
		if (caseName == null || caseName.trim().isBlank())
			throw new IllegalArgumentException("caseName is required.");
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);
			validatePracticeAreaForTenant(con, shaleClientId, practiceAreaId);
			validateResponsibleAttorneyForTenant(con, shaleClientId, responsibleAttorneyUserId);
			validateStatusForTenant(con, shaleClientId, statusId);
			long caseId = insertBasicCase(con, shaleClientId, caseName, caseNumber, callerDate, practiceAreaId,
					description, summary, dateOfInjury, statuteOfLimitations, tortNoticeDeadline, now);
			insertCaseStatus(con, caseId, statusId, now);
			insertResponsibleAttorney(con, caseId, responsibleAttorneyUserId, now);
			con.commit();
			return caseId;
		} catch (SQLException e) {
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to create case.", e);
		} finally {
			if (con != null) {
				try {
					con.setAutoCommit(true);
					con.close();
				} catch (SQLException ignored) {
				}
			}
		}
	}

	private long insertBasicCase(Connection con, int shaleClientId, String caseName, String caseNumber,
			LocalDate callerDate, int practiceAreaId, String description, String summary,
			LocalDate dateOfInjury, LocalDate statuteOfLimitations, LocalDate tortNoticeDeadline,
			Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.Cases (
				  Name, CaseNumber, CallerDate, PracticeAreaId, ClientEstate,
				  Description, Summary, DateOfInjury, StatuteOfLimitations, TortNoticeDeadline,
				  FollowUpMeetWithClient, FollowUpNurseReview, FollowUpExpertReview, FollowUpCaseTransferred,
				  AcceptedChronology, AcceptedConsultantExpertSearch, AcceptedTestifyingExpertSearch, AcceptedMedicalLiterature,
				  DeniedChronology, FeeAgreementSigned, MedicalRecordsRequested, IsDeleted, CreatedAt, UpdatedAt, ShaleClientId
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?);
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			setNullableString(ps, i++, caseName);
			setNullableString(ps, i++, caseNumber);
			setNullableDate(ps, i++, callerDate);
			ps.setInt(i++, practiceAreaId);
			setNullableString(ps, i++, description);
			setNullableString(ps, i++, summary);
			setNullableDate(ps, i++, dateOfInjury);
			setNullableDate(ps, i++, statuteOfLimitations);
			setNullableDate(ps, i++, tortNoticeDeadline);
			ps.setTimestamp(i++, now);
			ps.setTimestamp(i++, now);
			ps.setInt(i++, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new RuntimeException("Failed to create case.");
				return rs.getLong(1);
			}
		}
	}

	private void validateResponsibleAttorneyForTenant(Connection con, int shaleClientId, int userId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("""
				SELECT 1 FROM dbo.Users
				WHERE id = ? AND ShaleClientId = ? AND COALESCE(is_attorney, 0) = 1 AND COALESCE(is_deleted, 0) = 0;
				""")) {
			ps.setInt(1, userId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) return;
			}
		}
		throw new IllegalArgumentException("Responsible attorney is invalid for this tenant.");
	}

	private void validateStatusForTenant(Connection con, int shaleClientId, int statusId) throws SQLException {
		if (findStatusForTenantById(shaleClientId, statusId) == null)
			throw new IllegalArgumentException("Case status is invalid for this tenant.");
	}

	private void insertResponsibleAttorney(Connection con, long caseId, int userId, Timestamp now) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("""
				INSERT INTO dbo.CaseUsers (CaseId, UserId, RoleId, IsPrimary, Notes, CreatedAt, UpdatedAt)
				VALUES (?, ?, ?, 1, NULL, ?, ?);
				""")) {
			ps.setLong(1, caseId);
			ps.setInt(2, userId);
			ps.setInt(3, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setTimestamp(4, now);
			ps.setTimestamp(5, now);
			ps.executeUpdate();
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
		return findPageInternal(page, pageSize, sort, includeClosedDenied, null, null, null, null);
	}

	/** page is 0-based; query/status filters are pushed into SQL for the Cases view. */
	public PagedResult<CaseRow> findCasesViewPage(int page,
			int pageSize,
			CaseSort sort,
			boolean includeClosedDenied,
			String query,
			Set<Integer> selectedStatusIds) {
		return findCasesViewPage(page, pageSize, sort, includeClosedDenied, query, selectedStatusIds, null);
	}

	/** page is 0-based; reuses knownTotal to avoid recounting unchanged Cases-view queries. */
	public PagedResult<CaseRow> findCasesViewPage(int page,
			int pageSize,
			CaseSort sort,
			boolean includeClosedDenied,
			String query,
			Set<Integer> selectedStatusIds,
			Long knownTotal) {
		return findPageInternal(page, pageSize, sort, includeClosedDenied, null, query, selectedStatusIds, knownTotal);
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
		return findPageInternal(page, pageSize, sort, includeClosedDenied, userId, null, null, null);
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
			String caseUserActiveFilter = activeFilter(resolveCaseUsersDeletedColumn(con), "cu_scope");
			String sql = """
					SELECT TOP (?)
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  c.UpdatedAt,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
					WHERE %s
					  AND c.ShaleClientId = ?
					  AND EXISTS (
					    SELECT 1
					    FROM %s cu_scope
					    WHERE cu_scope.CaseId = c.Id
					      AND cu_scope.UserId = ?
					      AND %s
					  )
					ORDER BY c.CallerDate DESC, c.Id DESC;
					""".formatted(
					CASES_TABLE,
					CASE_STATUSES_TABLE,
					STATUSES_TABLE,
					CASE_USERS_TABLE,
					USERS_TABLE,
					activeFilter(schema.deletedColumn(), "c"),
					CASE_USERS_TABLE,
					caseUserActiveFilter);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, limit);
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, userId);

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
								rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent"),
								rs.getString("CurrentStatusName"),
								rs.getString("PrimaryStatusColor"),
								rs.getString("PracticeAreaColor"),
								rs.getString("ClientName"),
								rs.getString("OpposingPartiesName"),
								rs.getString("LatestCaseUpdate"),
								rs.getString("Description"),
								toLocalDate(rs.getDate("DateOfIncident")),
								toLocalDate(rs.getDate("TortNoticeDeadline")),
								toLocalDateTime(rs.getTimestamp("UpdatedAt"))));
					}
				}
				System.out.println("[TRACE ASSIGNED_CASES][CaseDao.listActiveCasesForUserTeamMember] "
						+ "selectedUserId=" + userId
						+ " shaleClientId=" + shaleClientId
						+ " sqlSummary=list-active-cases-for-user-team-member"
						+ " sql=" + sql.replace('\n', ' ')
						+ " membershipRule=anyCaseUsersRow"
						+ " sqlParamOrder=[limit, responsibleAttorneyRoleId, shaleClientId, selectedUserId]"
						+ " sqlParams=[" + limit + "," + ROLE_RESPONSIBLE_ATTORNEY + "," + shaleClientId + "," + userId + "]"
						+ " caseUsersIsDeletedFilter=" + caseUserActiveFilter
						+ " daoTotalRowsReturned=" + out.size());
				return out;
			}
		} catch (SQLException e) {
			System.err.println("[TRACE ASSIGNED_CASES][CaseDao.listActiveCasesForUserTeamMember] "
					+ "selectedUserId=" + userId
					+ " daoException=" + e.getMessage());
			e.printStackTrace(System.err);
			throw new RuntimeException("Failed to list assigned cases for team-member user (userId=" + userId + ")", e);
		}
	}

	public List<CaseStatusReportRowDto> listCaseStatusReport(int shaleClientId, LocalDate startDate, LocalDate endDate, List<Integer> selectedStatusIds) {
		if (shaleClientId <= 0) {
			return List.of();
		}
		List<StatusRow> availableStatuses = listStatusesForTenant(shaleClientId);
		Set<Integer> selectedIds = normalizeSelectedStatusIds(selectedStatusIds);
		if (selectedIds.isEmpty()) {
			return List.of();
		}
		Map<Integer, Long> countsByStatusId = loadCaseStatusReportCounts(shaleClientId, startDate, endDate, selectedIds);
		List<CaseStatusReportRowDto> rows = new ArrayList<>();
		for (StatusRow status : availableStatuses) {
			if (status == null || !selectedIds.contains(status.id())) {
				continue;
			}
			rows.add(new CaseStatusReportRowDto(
					status.id(),
					status.name(),
					status.systemKey(),
					status.lifecycleKey(),
					status.color(),
					status.sortOrder(),
					countsByStatusId.getOrDefault(status.id(), 0L)));
		}
		return rows;
	}

	private Map<Integer, Long> loadCaseStatusReportCounts(int shaleClientId, LocalDate startDate, LocalDate endDate, Set<Integer> selectedStatusIds) {
		if (selectedStatusIds == null || selectedStatusIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = sqlPlaceholders(selectedStatusIds.size());
		String sql = """
				SELECT
				    currentStatus.StatusId AS StatusId,
				    COUNT(*) AS CaseCount
				FROM dbo.Cases c
				OUTER APPLY (
				    SELECT TOP (1)
				        cs.StatusId
				    FROM dbo.CaseStatuses cs
				    WHERE cs.CaseId = c.Id
				      AND cs.EndDate IS NULL
				    ORDER BY
				        cs.IsPrimary DESC,
				        cs.EffectiveDate DESC,
				        cs.Id DESC
				) currentStatus
				INNER JOIN dbo.Statuses s
				    ON s.Id = currentStatus.StatusId
				WHERE c.ShaleClientId = ?
				  AND ISNULL(c.IsDeleted, 0) = 0
				  AND (? IS NULL OR c.CallerDate >= ?)
				  AND (? IS NULL OR c.CallerDate < DATEADD(day, 1, ?))
				  AND s.Id IN (%s)
				GROUP BY
				    currentStatus.StatusId;
				""".formatted(placeholders);
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setInt(idx++, shaleClientId);
			setNullableDateTwice(ps, idx, startDate);
			idx += 2;
			setNullableDateTwice(ps, idx, endDate);
			idx += 2;
			for (Integer statusId : selectedStatusIds) {
				ps.setInt(idx++, statusId);
			}
			Map<Integer, Long> counts = new LinkedHashMap<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					counts.put(rs.getInt("StatusId"), rs.getLong("CaseCount"));
				}
			}
			return counts;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case status report.", e);
		}
	}

	private static Set<Integer> normalizeSelectedStatusIds(List<Integer> selectedStatusIds) {
		if (selectedStatusIds == null || selectedStatusIds.isEmpty()) {
			return Set.of();
		}
		Set<Integer> ids = new LinkedHashSet<>();
		for (Integer statusId : selectedStatusIds) {
			if (statusId != null && statusId > 0) {
				ids.add(statusId);
			}
		}
		return ids;
	}

	private static String sqlPlaceholders(int count) {
		if (count <= 0) {
			throw new IllegalArgumentException("count must be positive");
		}
		return String.join(", ", java.util.Collections.nCopies(count, "?"));
	}

	private static void setNullableDateTwice(PreparedStatement ps, int startIndex, LocalDate value) throws SQLException {
		java.sql.Date sqlDate = value == null ? null : java.sql.Date.valueOf(value);
		ps.setDate(startIndex, sqlDate);
		ps.setDate(startIndex + 1, sqlDate);
	}

	public List<ReportCaseDetailRowDto> listCaseStatusReportCases(int shaleClientId, int statusId, LocalDate startDate, LocalDate endDate) {
		if (shaleClientId <= 0 || statusId <= 0) {
			return List.of();
		}
		String sql = """
				SELECT
				    c.Id,
				    c.Name AS CaseName,
				    c.CreatedAt,
				    c.CallerDate AS IntakeDate,
				    c.DeniedDate,
				    c.ClosedDate,
				    c.DateOfInjury,
				    c.Description,
				    c.StatuteOfLimitations,
				    c.TortNoticeDeadline,
				    c.UpdatedAt,
				    LTRIM(RTRIM(CONCAT(ra.name_first, ' ', ra.name_last))) AS ResponsibleAttorney
				FROM dbo.Cases c
				OUTER APPLY (
				    SELECT TOP (1)
				        cs.StatusId
				    FROM dbo.CaseStatuses cs
				    WHERE cs.CaseId = c.Id
				      AND cs.EndDate IS NULL
				    ORDER BY
				        cs.IsPrimary DESC,
				        cs.EffectiveDate DESC,
				        cs.Id DESC
				) currentStatus
				OUTER APPLY (
				    SELECT TOP (1)
				        cu.UserId
				    FROM dbo.CaseUsers cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = 4
				    ORDER BY
				        cu.IsPrimary DESC,
				        cu.UpdatedAt DESC,
				        cu.Id DESC
				) raLink
				LEFT JOIN dbo.Users ra
				    ON ra.id = raLink.UserId
				WHERE c.ShaleClientId = ?
				  AND ISNULL(c.IsDeleted, 0) = 0
				  AND currentStatus.StatusId = ?
				  AND (? IS NULL OR c.CallerDate >= ?)
				  AND (? IS NULL OR c.CallerDate < DATEADD(day, 1, ?))
				ORDER BY
				    c.CallerDate DESC,
				    c.Id DESC;
				""";
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, statusId);
			setNullableDateTwice(ps, idx, startDate);
			idx += 2;
			setNullableDateTwice(ps, idx, endDate);
			List<ReportCaseDetailRowDto> rows = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					rows.add(new ReportCaseDetailRowDto(
							rs.getInt("Id"),
							rs.getString("CaseName"),
							toLocalDateTime(rs.getTimestamp("CreatedAt")),
							toLocalDate(rs.getDate("IntakeDate")),
							toLocalDate(rs.getDate("DeniedDate")),
							toLocalDate(rs.getDate("ClosedDate")),
							toLocalDate(rs.getDate("DateOfInjury")),
							rs.getString("Description"),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							toLocalDate(rs.getDate("TortNoticeDeadline")),
							toLocalDateTime(rs.getTimestamp("UpdatedAt")),
							rs.getString("ResponsibleAttorney")));
				}
			}
			return rows;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case status report cases.", e);
		}
	}


	public List<CaseRow> listAssignedCasesForBoard(int userId) {
		if (userId <= 0) {
			return List.of();
		}
		System.out.println("[TRACE ASSIGNED_CASES][CaseDao.listAssignedCasesForBoard] "
				+ "daoQueryMethodName=listAssignedCasesForBoard "
				+ " daoInputUserId=" + userId
				+ " selectedUserId=" + userId);
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			int shaleClientId = requireCurrentShaleClientId(con);
			String caseUserActiveFilter = activeFilter(resolveCaseUsersDeletedColumn(con), "cu_scope");
			String sql = """
					SELECT
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  c.UpdatedAt,
					  CAST(NULL AS nvarchar(max)) AS LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  CAST(NULL AS nvarchar(max)) AS ClientName,
					  CAST(NULL AS nvarchar(max)) AS OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
					  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1)
					      s.Id AS PrimaryStatusId,
					      s.Name AS CurrentStatusName,
					      s.Color AS PrimaryStatusColor,
					      s.SortOrder AS CurrentStatusSortOrder
					    FROM %s cs
					    INNER JOIN %s s
					      ON s.Id = cs.StatusId
					     AND (s.ShaleClientId = ? OR s.ShaleClientId IS NULL)
					    WHERE cs.CaseId = c.Id
					      AND cs.EndDate IS NULL
					    ORDER BY
					      cs.IsPrimary DESC,
					      s.SortOrder,
					      cs.EffectiveDate DESC,
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
					      AND %s
					  )
					ORDER BY current_status.CurrentStatusSortOrder, c.CallerDate DESC, c.Id DESC;
					""".formatted(
					CASES_TABLE,
					CASE_STATUSES_TABLE,
					STATUSES_TABLE,
					CASE_USERS_TABLE,
					USERS_TABLE,
					activeFilter(schema.deletedColumn(), "c"),
					CASE_USERS_TABLE,
					caseUserActiveFilter);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, userId);

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
								rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent"),
								rs.getString("CurrentStatusName"),
								rs.getString("PrimaryStatusColor"),
								rs.getString("PracticeAreaColor"),
								rs.getString("ClientName"),
								rs.getString("OpposingPartiesName"),
								rs.getString("LatestCaseUpdate"),
								rs.getString("Description"),
								toLocalDate(rs.getDate("DateOfIncident")),
								toLocalDate(rs.getDate("TortNoticeDeadline"))));
					}
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list assigned cases for board (userId=" + userId + ")", e);
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
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
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
								rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent"),
								rs.getString("CurrentStatusName"),
								rs.getString("PrimaryStatusColor"),
								rs.getString("PracticeAreaColor"),
								rs.getString("ClientName"),
								rs.getString("OpposingPartiesName"),
								rs.getString("LatestCaseUpdate"),
								rs.getString("Description"),
								toLocalDate(rs.getDate("DateOfIncident")),
								toLocalDate(rs.getDate("TortNoticeDeadline"))
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
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
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
								rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent"),
								rs.getString("CurrentStatusName"),
								rs.getString("PrimaryStatusColor"),
								rs.getString("PracticeAreaColor"),
								rs.getString("ClientName"),
								rs.getString("OpposingPartiesName"),
								rs.getString("LatestCaseUpdate"),
								rs.getString("Description"),
								toLocalDate(rs.getDate("DateOfIncident")),
								toLocalDate(rs.getDate("TortNoticeDeadline"))
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
			Integer restrictToUserId,
			String query,
			Set<Integer> selectedStatusIds,
			Long knownTotal) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		String normalizedQuery = normalizeSearchQuery(query);
		Set<Integer> effectiveStatusIds = selectedStatusIds == null ? Set.of() : new HashSet<>(selectedStatusIds);
		boolean casesViewFiltered = restrictToUserId == null && (!normalizedQuery.isBlank() || !effectiveStatusIds.isEmpty());
		long total = knownTotal != null && knownTotal >= 0
				? knownTotal
				: (casesViewFiltered
						? countForCasesView(normalizedQuery, effectiveStatusIds)
						: countAll(includeClosedDenied, restrictToUserId));
		if (total == 0) {
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		CaseSort effectiveSort = sort == null ? CaseSort.INTAKE_NEWEST : sort;
		String orderByClause = orderByClauseFor(effectiveSort);

		List<CaseRow> out = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String userMembershipFilter = membershipExistsFilter(restrictToUserId, resolveCaseUsersDeletedColumn(con));
			StringBuilder casesViewFilter = new StringBuilder();
			if (!normalizedQuery.isBlank()) {
				casesViewFilter.append("\n  AND LOWER(COALESCE(c.Name, '')) LIKE ?");
			}
			if (!effectiveStatusIds.isEmpty()) {
				casesViewFilter.append("\n  AND (current_status.PrimaryStatusId IS NULL OR current_status.PrimaryStatusId IN (");
				casesViewFilter.append("?,".repeat(effectiveStatusIds.size()));
				casesViewFilter.setLength(casesViewFilter.length() - 1);
				casesViewFilter.append("))");
			}
			String sql = """
					SELECT
					  c.Id,
					  c.Name,
					  c.CallerDate,
					  c.StatuteOfLimitations,
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
					WHERE %s
					  AND c.ShaleClientId = ?
					  %s
					ORDER BY
					  %s
					OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
					""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, activeFilter(schema.deletedColumn(), "c"), userMembershipFilter + casesViewFilter,
					orderByClause);

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
				if (!normalizedQuery.isBlank()) {
					ps.setString(idx++, containsPattern(normalizedQuery));
					traceParams.append(" queryActive=true");
				}
				for (Integer statusId : effectiveStatusIds) {
					ps.setInt(idx++, statusId);
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
								rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent"),
								rs.getString("CurrentStatusName"),
								rs.getString("PrimaryStatusColor"),
								rs.getString("PracticeAreaColor"),
								rs.getString("ClientName"),
								rs.getString("OpposingPartiesName"),
								rs.getString("LatestCaseUpdate"),
								rs.getString("Description"),
								toLocalDate(rs.getDate("DateOfIncident")),
								toLocalDate(rs.getDate("TortNoticeDeadline"))
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
		case TORT_NOTICE_SOONEST -> "c.TortNoticeDeadline ASC, c.Id ASC";
		case UPDATED_OLDEST -> "c.UpdatedAt ASC, c.Id ASC";
		case UPDATED_NEWEST -> "c.UpdatedAt DESC, c.Id DESC";
		case CASE_NAME_ASC -> "c.Name ASC, c.Id ASC";
		case CASE_NAME_DESC -> "c.Name DESC, c.Id DESC";
		case RESPONSIBLE_ATTORNEY_ASC -> "ResponsibleAttorneyName ASC, c.Id ASC";
		case RESPONSIBLE_ATTORNEY_DESC -> "ResponsibleAttorneyName DESC, c.Id DESC";
		case CASE_STATUS_ASC -> "CurrentStatusName ASC, c.Id ASC";
		case CASE_STATUS_DESC -> "CurrentStatusName DESC, c.Id DESC";
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

	public long countForCasesView(String query, Set<Integer> selectedStatusIds) {
		String normalizedQuery = normalizeSearchQuery(query);
		Set<Integer> effectiveStatusIds = selectedStatusIds == null ? Set.of() : new HashSet<>(selectedStatusIds);

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			StringBuilder sql = new StringBuilder("""
					SELECT COUNT(1)
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
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
					WHERE %s
					  AND c.ShaleClientId = ?
					""".formatted(CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, activeFilter(schema.deletedColumn(), "c")));

			if (!normalizedQuery.isBlank()) {
				sql.append("""
					  AND LOWER(COALESCE(c.Name, '')) LIKE ?
					""");
			}

			sql.append("  AND (current_status.PrimaryStatusId IS NULL");
			if (!effectiveStatusIds.isEmpty()) {
				sql.append(" OR current_status.PrimaryStatusId IN (");
				sql.append("?,".repeat(effectiveStatusIds.size()));
				sql.setLength(sql.length() - 1);
				sql.append(")");
			}
			sql.append(");");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				int idx = 1;
				ps.setInt(idx++, requireCurrentShaleClientId(con));
				if (!normalizedQuery.isBlank()) {
					ps.setString(idx++, containsPattern(normalizedQuery));
				}
				for (Integer statusId : effectiveStatusIds) {
					ps.setInt(idx++, statusId);
				}

				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					return rs.getLong(1);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to count cases for cases view", e);
		}
	}

	private long countAll(boolean includeClosedDenied, Integer restrictToUserId) {
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String userMembershipFilter = membershipExistsFilter(restrictToUserId, resolveCaseUsersDeletedColumn(con));
			String sql = """
					SELECT COUNT(1)
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
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
		String sql = null;

		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			boolean hasPartyRoleSystemKey = tableHasColumn(con, PARTY_ROLES_TABLE, "SystemKey");
			String callerRolePredicate = hasPartyRoleSystemKey
					? "(LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'caller' OR LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'caller')"
					: "LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'caller'";
			String counselRolePredicate = hasPartyRoleSystemKey
					? "(LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'counsel' OR LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'counsel')"
					: "LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'counsel'";
			String caseUserActiveFilter = activeFilter(resolveCaseUsersDeletedColumn(con), "cu");
			String legalAssistantCaseUserActiveFilter = activeFilter(resolveCaseUsersDeletedColumn(con), "pla_cu");
			String userActiveFilter = activeFilter(resolveUsersDeletedColumn(con), "u");
			String legalAssistantUserActiveFilter = activeFilter(resolveUsersDeletedColumn(con), "pla_user");
			sql = buildOverviewSql(
					caseUserActiveFilter,
					userActiveFilter,
					legalAssistantUserActiveFilter,
					legalAssistantCaseUserActiveFilter,
					callerRolePredicate,
					counselRolePredicate,
					activeFilter(schema.deletedColumn(), "c"));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int idx = 1;
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(idx++, ROLE_LEGAL_ASSISTANT);
				ps.setLong(idx++, caseId);

				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next())
						return null;
					List<String> team = loadTeamMembers(con, caseId);
					List<com.shale.core.dto.CaseOverviewDto.ContactSummary> clients = listCasePartiesContactsByRoleAndSide(con, caseId, PARTY_ROLE_NAME_PARTY, PARTY_SIDE_KEY_REPRESENTED);
					Integer primaryClientContactId = clients.isEmpty() ? null : clients.get(0).contactId();
					String primaryClientName = clients.isEmpty() ? null : clients.get(0).displayName();
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
							getNullableInt(rs, "PrimaryLegalAssistantUserId"),
							rs.getString("PrimaryLegalAssistantName"),
							rs.getString("PrimaryLegalAssistantColor"),
							getNullableInt(rs, "PracticeAreaId"),
							rs.getString("PracticeAreaName"),
							rs.getString("PracticeAreaColor"),
							toLocalDate(rs.getDate("CallerDate")),
							toLocalDate(rs.getDate("DateOfInjury")),
							toLocalDate(rs.getDate("StatuteOfLimitations")),
							toLocalDate(rs.getDate("TortNoticeDeadline")),
							getNullableInt(rs, "PrimaryCallerContactId"),
							primaryClientContactId,
							getNullableInt(rs, "PrimaryOpposingCounselContactId"),
							rs.getString("CallerName"),
							primaryClientName,
							clients,
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

	static String buildOverviewSql(
			String caseUserActiveFilter,
			String userActiveFilter,
			String legalAssistantUserActiveFilter,
			String legalAssistantCaseUserActiveFilter,
			String callerRolePredicate,
			String counselRolePredicate,
			String caseActiveFilter) {
		return """
					SELECT
					  c.Id,
					  c.Name,
					  c.CaseNumber,
					  c.Description AS Description,
					  c.CallerDate,
					  c.DateOfInjury,
					  c.StatuteOfLimitations,
					  c.TortNoticeDeadline,

					  pa.Id    AS PracticeAreaId,
					  pa.Name  AS PracticeAreaName,
					  pa.Color AS PracticeAreaColor,

					  ra.UserId AS ResponsibleAttorneyUserId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName,

					  primary_legal_assistant.UserId AS PrimaryLegalAssistantUserId,
					  pla_user.color AS PrimaryLegalAssistantColor,
					  LTRIM(RTRIM(
					    COALESCE(pla_user.name_first, '') +
					    CASE WHEN COALESCE(pla_user.name_first, '') = '' OR COALESCE(pla_user.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(pla_user.name_last, '')
					  )) AS PrimaryLegalAssistantName,

					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusId,
					  current_status.PrimaryStatusColor,

					  callerContact.PrimaryCallerContactId,
					  callerContact.CallerName,

					  oppContact.PrimaryOpposingCounselContactId,
					  oppContact.FullName AS OpposingCounselName

					FROM dbo.Cases c
					LEFT JOIN dbo.PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) cu.UserId
					    FROM dbo.CaseUsers cu
					    WHERE cu.CaseId = c.Id
					      AND cu.RoleId = ?
					      AND cu.IsPrimary = 1
					      AND %s
					    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) ra
					LEFT JOIN dbo.Users u ON u.id = ra.UserId
					 AND u.ShaleClientId = c.ShaleClientId
					 AND %s
					OUTER APPLY (
					    SELECT TOP (1) pla_cu.UserId
					    FROM dbo.CaseUsers pla_cu
					    INNER JOIN dbo.Users pla_user
					      ON pla_user.id = pla_cu.UserId
					     AND pla_user.ShaleClientId = c.ShaleClientId
					     AND %s
					    WHERE pla_cu.CaseId = c.Id
					      AND pla_cu.RoleId = ?
					      AND pla_cu.IsPrimary = 1
					      AND %s
					    ORDER BY pla_cu.UpdatedAt DESC, pla_cu.CreatedAt DESC, pla_cu.Id DESC
					) primary_legal_assistant
					LEFT JOIN dbo.Users pla_user
					  ON pla_user.id = primary_legal_assistant.UserId
					 AND pla_user.ShaleClientId = c.ShaleClientId
					 AND %s
					OUTER APPLY (
					    SELECT TOP (1)
					      s.Id    AS PrimaryStatusId,
					      s.Color AS PrimaryStatusColor,
					      s.Name  AS CurrentStatusName
					    FROM dbo.CaseStatuses cs
					    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
					    WHERE cs.CaseId = c.Id
					    ORDER BY
					      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
					      cs.UpdatedAt DESC,
					      cs.CreatedAt DESC,
					      cs.Id DESC
					) current_status
					OUTER APPLY (
					    SELECT TOP (1)
					      cp.ContactId AS PrimaryCallerContactId,
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
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND %s
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY
					      CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END,
					      cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) callerContact
					OUTER APPLY (
					    SELECT TOP (1)
					      cp.ContactId AS PrimaryOpposingCounselContactId,
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
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND %s
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = '%s'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY
					      CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END,
					      cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) oppContact
					WHERE c.Id = ?
					  AND %s;
					""".formatted(
							caseUserActiveFilter,
							userActiveFilter,
							legalAssistantUserActiveFilter,
							legalAssistantCaseUserActiveFilter,
							legalAssistantUserActiveFilter,
							callerRolePredicate,
							counselRolePredicate,
							PARTY_SIDE_KEY_OPPOSING,
							caseActiveFilter);
	}

	private List<com.shale.core.dto.CaseOverviewDto.ContactSummary> listCasePartiesContactsByRoleAndSide(
			Connection con,
			long caseId,
			String roleName,
			String side) throws SQLException {
		boolean hasSystemKey = tableHasColumn(con, PARTY_ROLES_TABLE, "SystemKey");
		String normalizedRole = roleName == null ? "" : roleName.trim().toLowerCase(Locale.ROOT);
		String rolePredicate = hasSystemKey
				? "(LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = ? OR LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = ?)"
				: "LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = ?";
		String sql = """
				SELECT
				  cp.ContactId,
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
				  )) AS DisplayName
				FROM dbo.CaseParties cp
				INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
				INNER JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
				WHERE cp.CaseId = ?
				  AND %s
				  AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = ?
				  AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				ORDER BY
				  CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END,
				  cp.UpdatedAt DESC,
				  cp.CreatedAt DESC,
				  cp.ContactId DESC;
				""".formatted(rolePredicate);
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			int idx = 2;
			ps.setString(idx++, normalizedRole);
			if (hasSystemKey) {
				ps.setString(idx++, normalizedRole);
			}
			ps.setString(idx, side == null ? "" : side.trim().toLowerCase(Locale.ROOT));
			try (ResultSet rs = ps.executeQuery()) {
				List<com.shale.core.dto.CaseOverviewDto.ContactSummary> out = new ArrayList<>();
				while (rs.next()) {
					String name = rs.getString("DisplayName");
					if (name == null || name.isBlank())
						continue;
					out.add(new com.shale.core.dto.CaseOverviewDto.ContactSummary(rs.getInt("ContactId"), name));
				}
				return out;
			}
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
				  c.Description AS Description,
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
				  c.MedicalRecordsRequested,
				  c.FeeAgreementSigned,
				  c.DateFeeAgreementSigned,
				  c.NonEngagementLetterSent,
				  c.DateNonEngagementLetterSent,
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
				  %s,
				  current_status.CurrentStatusName,
				  LTRIM(RTRIM(
				    COALESCE(ra_user.name_first, '') +
				    CASE WHEN COALESCE(ra_user.name_first, '') = '' OR COALESCE(ra_user.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(ra_user.name_last, '')
				  )) AS ResponsibleAttorneyName,
				  responsible_attorney.UserId AS ResponsibleAttorneyId
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
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM %s cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = ?
				    ORDER BY
				      cu.IsPrimary DESC,
				      cu.UpdatedAt DESC,
				      cu.Id DESC
				) responsible_attorney
				LEFT JOIN %s ra_user
				  ON ra_user.id = responsible_attorney.UserId
				 AND ra_user.ShaleClientId = c.ShaleClientId
				 AND COALESCE(ra_user.is_deleted, 0) = 0
				WHERE c.Id = ?
				  AND %s;
				""".formatted(schema.rowVersionSelectExpression("c"), CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, activeFilter(schema.deletedColumn(), "c"));

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return mapCaseDetail(rs,
						listRelatedContacts(con, caseId, requireCurrentShaleClientId(con)),
						listCaseStatusHistory(con, caseId));
			}
		}
	}

	private static com.shale.core.dto.CaseDetailDto mapCaseDetail(ResultSet rs,
			List<com.shale.core.dto.CaseDetailDto.RelatedContactDto> relatedContacts,
			List<CaseStatusHistoryDto> statusHistory) throws SQLException {
		return new com.shale.core.dto.CaseDetailDto(
				rs.getLong("Id"),
				rs.getString("CaseNumber"),
				rs.getString("Name"),
				rs.getString("Description"),
				rs.getString("CurrentStatusName"),
				rs.getString("ResponsibleAttorneyName"),
				getNullableInt(rs, "ResponsibleAttorneyId"),
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
				getNullableBoolean(rs, "MedicalRecordsRequested"),
				getNullableBoolean(rs, "FeeAgreementSigned"),
				toLocalDate(rs.getDate("DateFeeAgreementSigned")),
				getNullableBoolean(rs, "NonEngagementLetterSent"),
				toLocalDate(rs.getDate("DateNonEngagementLetterSent")),
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
				rs.getBytes("RowVer"),
				relatedContacts,
				statusHistory
		);
	}

	public com.shale.core.dto.CaseDetailDto updateCase(
			long caseId,
			String name,
			String caseNumber,
			String description,
			LocalDate incidentDate,
			LocalDate solDate,
			LocalDate tortNoticeDeadline,
			String summary,
			byte[] expectedRowVer,
			Integer actorUserId) {
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
					    TortNoticeDeadline = ?,
					    Summary = ?,
					    UpdatedAt = SYSDATETIME()
					WHERE Id = ?
					  AND RowVer = ?
					  AND %s;
					""".formatted(CASES_TABLE, activeFilter(schema.deletedColumn(), null));

			CaseDetailDto before = selectCaseDetail(con, caseId);
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
				if (tortNoticeDeadline == null)
					ps.setNull(6, java.sql.Types.DATE);
				else
					ps.setDate(6, java.sql.Date.valueOf(tortNoticeDeadline));
				ps.setString(7, summary);
				ps.setLong(8, caseId);
				ps.setBytes(9, expectedRowVer);

				int rows = ps.executeUpdate();
				if (rows == 0) {
					return null;
				}
				if (rows == 1) {
					com.shale.core.dto.CaseDetailDto updated = selectCaseDetail(con, caseId);
					if (updated == null) {
						throw new RuntimeException("Case updated but detail row was not found (caseId=" + caseId + ")");
					}
					if (before != null) {
						phiAuditService.auditUpdate(actorUserId, "Cases", "Description", caseId, before.getDescription(), updated.getDescription());
						phiAuditService.auditUpdate(actorUserId, "Cases", "DateOfInjury", caseId, before.getDateOfInjury(), updated.getDateOfInjury());
						phiAuditService.auditUpdate(actorUserId, "Cases", "TortNoticeDeadline", caseId, before.getTortNoticeDeadline(), updated.getTortNoticeDeadline());
						phiAuditService.auditUpdate(actorUserId, "Cases", "Summary", caseId, before.getSummary(), updated.getSummary());
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
			Boolean medicalRecordsRequested,
			Boolean feeAgreementSigned,
			LocalDate dateFeeAgreementSigned,
			Boolean nonEngagementLetterSent,
			LocalDate dateNonEngagementLetterSent,
			Boolean acceptedChronology,
			Boolean acceptedConsultantExpertSearch,
			Boolean acceptedTestifyingExpertSearch,
			Boolean acceptedMedicalLiterature,
			String acceptedDetail,
			Boolean deniedChronology,
			String deniedDetail,
			String summary,
			String receivedUpdates,
			byte[] expectedRowVer,
			Integer actorUserId) {
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
					    MedicalRecordsRequested = ?,
					    FeeAgreementSigned = ?,
					    DateFeeAgreementSigned = ?,
					    NonEngagementLetterSent = ?,
					    DateNonEngagementLetterSent = ?,
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

			CaseDetailDto before = selectCaseDetail(con, caseId);
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
				setNullableBoolean(ps, idx++, medicalRecordsRequested);
				setNullableBoolean(ps, idx++, feeAgreementSigned);
				setNullableDate(ps, idx++, dateFeeAgreementSigned);
				setNullableBoolean(ps, idx++, nonEngagementLetterSent);
				setNullableDate(ps, idx++, dateNonEngagementLetterSent);
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
				if (rows == 1) {
					CaseDetailDto updated = selectCaseDetail(con, caseId);
					if (before != null && updated != null) {
						phiAuditService.auditUpdate(actorUserId, "Cases", "AcceptedDetail", caseId, before.getAcceptedDetail(), updated.getAcceptedDetail());
						phiAuditService.auditUpdate(actorUserId, "Cases", "DeniedDetail", caseId, before.getDeniedDetail(), updated.getDeniedDetail());
						phiAuditService.auditUpdate(actorUserId, "Cases", "ReceivedUpdates", caseId, before.getReceivedUpdates(), updated.getReceivedUpdates());
						phiAuditService.auditUpdate(actorUserId, "Cases", "DateOfMedicalNegligence", caseId, before.getDateOfMedicalNegligence(), updated.getDateOfMedicalNegligence());
						phiAuditService.auditUpdate(actorUserId, "Cases", "DateMedicalNegligenceWasDiscovered", caseId, before.getDateMedicalNegligenceWasDiscovered(), updated.getDateMedicalNegligenceWasDiscovered());
						phiAuditService.auditUpdate(actorUserId, "Cases", "DateOfInjury", caseId, before.getDateOfInjury(), updated.getDateOfInjury());
						phiAuditService.auditUpdate(actorUserId, "Cases", "Description", caseId, before.getDescription(), updated.getDescription());
						phiAuditService.auditUpdate(actorUserId, "Cases", "Summary", caseId, before.getSummary(), updated.getSummary());
					}
					return updated;
				}
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

	public List<RecentCaseUpdateActivityDto> listRecentCaseUpdatesForAssignedCases(int assignedUserId, int shaleClientId, int limit) {
		if (assignedUserId <= 0 || shaleClientId <= 0 || limit <= 0) {
			return List.of();
		}
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String caseUserActiveFilter = activeFilter(resolveCaseUsersDeletedColumn(con), "caseUser");
			String userDeletedColumn = resolveUsersDeletedColumn(con);
			String userDeletedPredicate = userDeletedColumn == null || userDeletedColumn.isBlank()
					? ""
					: "\n AND " + activeFilter(userDeletedColumn, "u");
			String sql = """
					SELECT TOP (?)
					  caseUpdate.Id,
					  caseUpdate.CaseId,
					  c.Name AS CaseName,
					  caseUpdate.NoteText,
					  caseUpdate.CreatedAt,
					  caseUpdate.CreatedByUserId,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS CreatedByDisplayName
					FROM dbo.CaseUpdates caseUpdate
					JOIN dbo.Cases c
					  ON c.Id = caseUpdate.CaseId
					 AND c.ShaleClientId = caseUpdate.ShaleClientId
					JOIN (
					  SELECT DISTINCT caseUser.CaseId
					  FROM dbo.CaseUsers caseUser
					  WHERE caseUser.UserId = ?
					    AND %s
					) assignedCase
					  ON assignedCase.CaseId = c.Id
					LEFT JOIN dbo.Users u
					  ON u.Id = caseUpdate.CreatedByUserId
					 AND u.ShaleClientId = caseUpdate.ShaleClientId%s
					WHERE c.ShaleClientId = ?
					  AND caseUpdate.ShaleClientId = ?
					  AND %s
					  AND ISNULL(caseUpdate.IsDeleted, 0) = 0
					  AND NULLIF(LTRIM(RTRIM(caseUpdate.NoteText)), '') IS NOT NULL
					ORDER BY caseUpdate.CreatedAt DESC, caseUpdate.Id DESC;
					""".formatted(caseUserActiveFilter, userDeletedPredicate, activeFilter(schema.deletedColumn(), "c"));

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, limit);
				ps.setInt(2, assignedUserId);
				ps.setInt(3, shaleClientId);
				ps.setInt(4, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<RecentCaseUpdateActivityDto> out = new ArrayList<>();
					while (rs.next()) {
						Integer createdByUserId = getNullableInt(rs, "CreatedByUserId");
						out.add(new RecentCaseUpdateActivityDto(
								rs.getLong("Id"),
								rs.getLong("CaseId"),
								rs.getString("CaseName"),
								rs.getString("NoteText"),
								toLocalDateTime(rs.getTimestamp("CreatedAt")),
								createdByUserId,
								safeUserDisplayName(rs.getString("CreatedByDisplayName"), createdByUserId)));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list recent assigned case updates (assignedUserId=" + assignedUserId + ")", e);
		}
	}

	public List<CaseUpdateDto> listCaseUpdates(long caseId) {
		return listCaseUpdatesInternal(caseId, null);
	}

	public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
		if (shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}
		return listCaseUpdatesInternal(caseId, shaleClientId);
	}

	private List<CaseUpdateDto> listCaseUpdatesInternal(long caseId, Integer shaleClientId) {
		String tenantPredicate = shaleClientId == null ? "" : "\n  AND caseUpdate.ShaleClientId = ?";
		try (Connection con = db.requireConnection()) {
			String userDeletedColumn = resolveUsersDeletedColumn(con);
			String userDeletedPredicate = userDeletedColumn == null || userDeletedColumn.isBlank()
					? ""
					: "\n AND " + activeFilter(userDeletedColumn, "u");
			String sql = """
					SELECT
					  caseUpdate.Id,
					  caseUpdate.CaseId,
					  caseUpdate.NoteText,
					  caseUpdate.CreatedAt,
					  caseUpdate.UpdatedAt,
					  caseUpdate.CreatedByUserId,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS CreatedByDisplayName
					FROM dbo.CaseUpdates caseUpdate
					LEFT JOIN dbo.Users u
					  ON u.Id = caseUpdate.CreatedByUserId
					 AND u.ShaleClientId = caseUpdate.ShaleClientId%s
					WHERE caseUpdate.CaseId = ?%s
					  AND ISNULL(caseUpdate.IsDeleted, 0) = 0
					  AND NULLIF(LTRIM(RTRIM(caseUpdate.NoteText)), '') IS NOT NULL
					ORDER BY caseUpdate.CreatedAt DESC, caseUpdate.Id DESC;
					""".formatted(userDeletedPredicate, tenantPredicate);

			try (PreparedStatement ps = con.prepareStatement(sql)) {

				ps.setLong(1, caseId);
				if (shaleClientId != null) {
					ps.setInt(2, shaleClientId);
				}

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
								toLocalDateTime(rs.getTimestamp("UpdatedAt")),
								createdByUserId,
								displayName
						));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case updates (caseId=" + caseId + ")", e);
		}
	}

	public long addCaseTimelineEvent(int caseId,
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
				OUTPUT INSERTED.Id
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
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new RuntimeException("Unexpected insert result for case timeline event (caseId=" + caseId + ")");
				}
				long timelineEventId = rs.getLong(1);
				phiAuditService.auditCreate(actorUserId, "CaseTimelineEvents", "Body", timelineEventId, normalizedBody);
				return timelineEventId;
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

	public boolean markMedicalRecordsRequested(long caseId, int shaleClientId) {
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement("""
						UPDATE dbo.Cases
						SET MedicalRecordsRequested = 1,
						    UpdatedAt = SYSDATETIME()
						WHERE Id = ?
						  AND ShaleClientId = ?
						  AND MedicalRecordsRequested = 0
						  AND (IsDeleted = 0 OR IsDeleted IS NULL);
						""")) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to mark medical records requested (caseId=" + caseId + ")", e);
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
				OUTPUT INSERTED.Id
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

			long caseUpdateId;
			try (PreparedStatement ps = con.prepareStatement(insertSql)) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				ps.setString(3, trimmedText);
				if (createdByUserId == null)
					ps.setNull(4, java.sql.Types.INTEGER);
				else
					ps.setInt(4, createdByUserId);

				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new RuntimeException("Unexpected insert result for case update (caseId=" + caseId + ")");
					}
					caseUpdateId = rs.getLong(1);
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
			phiAuditService.auditCreate(createdByUserId, "CaseUpdates", "NoteText", caseUpdateId, trimmedText);

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
		String existingSql = """
				SELECT NoteText
				FROM dbo.CaseUpdates
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND ISNULL(IsDeleted, 0) = 0;
				""";
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
				PreparedStatement existingPs = con.prepareStatement(existingSql);
				PreparedStatement ps = con.prepareStatement(sql)) {
			existingPs.setLong(1, caseUpdateId);
			existingPs.setLong(2, caseId);
			existingPs.setInt(3, shaleClientId);
			String oldText = null;
			try (ResultSet rs = existingPs.executeQuery()) {
				if (rs.next()) {
					oldText = rs.getString("NoteText");
				}
			}
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
			int rows = ps.executeUpdate();
			if (rows > 0) {
				phiAuditService.auditDelete(deletedByUserId, "CaseUpdates", "NoteText", caseUpdateId, oldText);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to soft delete case update (id=" + caseUpdateId + ")", e);
		}
	}

	public boolean updateCaseNote(long caseUpdateId, long caseId, int shaleClientId, int actorUserId, String noteText) {
		String trimmedText = noteText == null ? "" : noteText.trim();
		if (trimmedText.isBlank()) {
			throw new IllegalArgumentException("Case update text is required.");
		}
		if (actorUserId <= 0) {
			throw new IllegalArgumentException("actorUserId is required.");
		}

		String existingSql = """
				SELECT NoteText
				FROM dbo.CaseUpdates
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND ISNULL(IsDeleted, 0) = 0
				  AND CreatedByUserId = ?;
				""";
		String sql = """
				UPDATE dbo.CaseUpdates
				SET NoteText = ?,
				    UpdatedAt = SYSDATETIME(),
				    EditedByUserId = ?
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND ISNULL(IsDeleted, 0) = 0
				  AND CreatedByUserId = ?;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement existingPs = con.prepareStatement(existingSql);
				PreparedStatement ps = con.prepareStatement(sql)) {
			existingPs.setLong(1, caseUpdateId);
			existingPs.setLong(2, caseId);
			existingPs.setInt(3, shaleClientId);
			existingPs.setInt(4, actorUserId);
			String oldText = null;
			try (ResultSet rs = existingPs.executeQuery()) {
				if (rs.next()) {
					oldText = rs.getString("NoteText");
				}
			}
			ps.setString(1, trimmedText);
			ps.setInt(2, actorUserId);
			ps.setLong(3, caseUpdateId);
			ps.setLong(4, caseId);
			ps.setInt(5, shaleClientId);
			ps.setInt(6, actorUserId);
			boolean updated = ps.executeUpdate() == 1;
			if (updated) {
				phiAuditService.auditUpdate(actorUserId, "CaseUpdates", "NoteText", caseUpdateId, oldText, trimmedText);
			}
			return updated;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case update (id=" + caseUpdateId + ")", e);
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

	private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
		if (value == null) {
			ps.setNull(idx, java.sql.Types.BIGINT);
		} else {
			ps.setLong(idx, value);
		}
	}

	private static void validateSinglePartyEntity(Long contactId, Long organizationId) {
		boolean hasContact = contactId != null && contactId > 0;
		boolean hasOrganization = organizationId != null && organizationId > 0;
		if (hasContact == hasOrganization) {
			throw new IllegalArgumentException("Exactly one of contactId or organizationId must be provided.");
		}
	}

	private String normalizeCasePartySide(Connection con, int shaleClientId, String side) throws SQLException {
		if (side == null) {
			return null;
		}
		String normalized = side.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return null;
		}
		if (isAllowedPartySideSystemKey(con, shaleClientId, normalized)) {
			return normalized;
		}
		throw new IllegalArgumentException("side must be a configured PartySide SystemKey or null.");
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

	private static Long getNullableLong(ResultSet rs, String col) throws SQLException {
		Object o = rs.getObject(col);
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.longValue();
		return Long.valueOf(o.toString());
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

	private List<com.shale.core.dto.CaseDetailDto.RelatedContactDto> listRelatedContacts(Connection con, long caseId, int shaleClientId) throws SQLException {
		boolean hasIsDeleted = contactsHasIsDeletedColumn(con);
		String sql = relatedCasePartyContactsSql(hasIsDeleted);
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, shaleClientId);

			List<com.shale.core.dto.CaseDetailDto.RelatedContactDto> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new com.shale.core.dto.CaseDetailDto.RelatedContactDto(
							rs.getInt("Id"),
							rs.getString("DisplayName"),
							(Integer) rs.getObject("RoleId"),
							rs.getString("RoleName"),
							rs.getString("Side"),
							rs.getBoolean("IsPrimary"),
							rs.getString("Email"),
							rs.getString("Phone")));
				}
			}
			return out;
		}
	}

	private static String relatedCasePartyContactsSql(boolean includeContactSoftDeleteFilter) {
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
				  CAST(cp.PartyRoleId AS int) AS RoleId,
				  NULLIF(LTRIM(RTRIM(COALESCE(pr.Name, ''))), '') AS RoleName,
				  NULLIF(LTRIM(RTRIM(COALESCE(cp.Side, ''))), '') AS Side,
				  COALESCE(cp.IsPrimary, 0) AS IsPrimary,
				  COALESCE(
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailPersonal, ''))), ''),
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailWork, ''))), ''),
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailOther, ''))), '')
				  ) AS Email,
				  COALESCE(
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneCell, ''))), ''),
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneHome, ''))), ''),
				    NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneWork, ''))), '')
				  ) AS Phone
				FROM dbo.CaseParties cp
				INNER JOIN dbo.Cases c
				  ON c.Id = cp.CaseId
				INNER JOIN dbo.PartyRoles pr
				  ON pr.Id = cp.PartyRoleId
				INNER JOIN dbo.Contacts ct
				  ON ct.Id = cp.ContactId
				WHERE cp.CaseId = ?
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
				ORDER BY
				  COALESCE(cp.IsPrimary, 0) DESC,
				  CASE cp.Side
				    WHEN '%s' THEN 0
				    WHEN '%s' THEN 1
				    WHEN '%s' THEN 2
				    ELSE 3
				  END,
				  DisplayName ASC,
				  cp.Id ASC;
				""".formatted(
					PARTY_SIDE_KEY_REPRESENTED,
					PARTY_SIDE_KEY_OPPOSING,
					PARTY_SIDE_KEY_NEUTRAL);

		return includeContactSoftDeleteFilter
				? baseSql + "\n  AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)\n" + orderSql
				: baseSql + "\n" + orderSql;
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
				  NULLIF(LTRIM(RTRIM(COALESCE(cc.Side, ''))), '') AS Side,
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
								rs.getString("Side"),
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
				    FROM dbo.CaseParties cp
				    WHERE cp.CaseId = ?
				      AND cp.ContactId = ct.Id
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

	public List<SelectableContactRow> findSelectableContactsForTenant() {
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
				ORDER BY DisplayName ASC, ct.Id ASC;
				""";
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			ps.setInt(1, shaleClientId);
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
			throw new RuntimeException("Failed to load contacts for tenant party picker.", e);
		}
	}

	public List<PartyRoleRow> listPartyRoles() {
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			List<PartyRoleLookupRow> effective = listPartyRoleLookupRowsForTenant(con, shaleClientId);
			List<PartyRoleRow> out = new ArrayList<>(effective.size());
			for (PartyRoleLookupRow row : effective) {
				if (row == null)
					continue;
				out.add(new PartyRoleRow(row.id(), row.name()));
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load party roles", e);
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
			boolean linked = ps.executeUpdate() > 0;
			if (linked) {
				touchCaseUpdatedAt(con, caseId, shaleClientId);
			}
			return linked;
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
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  )
				  AND NOT EXISTS (
				    SELECT 1
				    FROM dbo.CaseParties cp
				    WHERE cp.CaseId = ?
				      AND cp.OrganizationId = o.Id
				  )
				ORDER BY o.Name ASC, o.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);

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

	public List<SelectableOrganizationRow> findSelectableOrganizationsForTenant() {
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
			throw new RuntimeException("Failed to load organizations for tenant party picker.", e);
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

	public List<CasePartyDto> listCaseParties(long caseId) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			boolean hasSystemKey = tableHasColumn(con, PARTY_ROLES_TABLE, "SystemKey");
			String partyRoleSystemKeySelect = hasSystemKey ? "pr.SystemKey AS PartyRoleSystemKey," : "NULL AS PartyRoleSystemKey,";
			String sql = """
				SELECT
				  cp.Id,
				  cp.CaseId,
				  cp.ContactId,
				  cp.OrganizationId,
				  cp.PartyRoleId,
				  pr.Name AS PartyRoleName,
				  %s
				  cp.Side,
				  COALESCE(cp.IsPrimary, 0) AS IsPrimary,
				  cp.Notes,
				  cp.CreatedAt,
				  cp.UpdatedAt,
				  CASE
				    WHEN cp.ContactId IS NOT NULL THEN 'contact'
				    ELSE 'organization'
				  END AS EntityType,
				  COALESCE(
				    NULLIF(LTRIM(RTRIM(
				      CASE
				        WHEN cp.ContactId IS NOT NULL THEN
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
				        ELSE COALESCE(o.Name, '')
				      END
				    )), ''),
				    CASE
				      WHEN cp.ContactId IS NOT NULL THEN 'Contact #' + CAST(cp.ContactId AS varchar(32))
				      ELSE 'Organization #' + CAST(cp.OrganizationId AS varchar(32))
				    END
				  ) AS DisplayName,
				  CASE
				    WHEN cp.ContactId IS NOT NULL THEN
				      COALESCE(
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailPersonal, ''))), ''),
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailWork, ''))), ''),
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.EmailOther, ''))), '')
				      )
				    ELSE NULLIF(LTRIM(RTRIM(COALESCE(o.Email, ''))), '')
				  END AS Email,
				  CASE
				    WHEN cp.ContactId IS NOT NULL THEN
				      COALESCE(
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneCell, ''))), ''),
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneHome, ''))), ''),
				        NULLIF(LTRIM(RTRIM(COALESCE(ct.PhoneWork, ''))), '')
				      )
				    ELSE NULLIF(LTRIM(RTRIM(COALESCE(o.Phone, ''))), '')
				  END AS Phone
				FROM dbo.CaseParties cp
				INNER JOIN dbo.Cases c
				  ON c.Id = cp.CaseId
				INNER JOIN dbo.PartyRoles pr
				  ON pr.Id = cp.PartyRoleId
				LEFT JOIN dbo.Contacts ct
				  ON ct.Id = cp.ContactId
				LEFT JOIN dbo.Organizations o
				  ON o.Id = cp.OrganizationId
				WHERE cp.CaseId = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				ORDER BY
				  COALESCE(cp.IsPrimary, 0) DESC,
				  CASE cp.Side
				    WHEN '%s' THEN 0
				    WHEN '%s' THEN 1
				    WHEN '%s' THEN 2
				    ELSE 3
				  END,
				  COALESCE(
				    NULLIF(LTRIM(RTRIM(
				      CASE
				        WHEN cp.ContactId IS NOT NULL THEN
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
				        ELSE COALESCE(o.Name, '')
				      END
				    )), ''),
				    CASE
				      WHEN cp.ContactId IS NOT NULL THEN 'Contact #' + CAST(cp.ContactId AS varchar(32))
				      ELSE 'Organization #' + CAST(cp.OrganizationId AS varchar(32))
				    END
				  ) ASC,
				  cp.Id ASC;
				""".formatted(
						partyRoleSystemKeySelect,
						PARTY_SIDE_KEY_REPRESENTED,
						PARTY_SIDE_KEY_OPPOSING,
						PARTY_SIDE_KEY_NEUTRAL);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);

			List<CasePartyDto> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new CasePartyDto(
							rs.getLong("Id"),
							rs.getLong("CaseId"),
							getNullableLong(rs, "ContactId"),
							getNullableLong(rs, "OrganizationId"),
							rs.getLong("PartyRoleId"),
							rs.getString("PartyRoleName"),
							resolvePartyRoleSystemKey(rs.getString("PartyRoleSystemKey"), rs.getString("PartyRoleName")),
							rs.getString("Side"),
							rs.getBoolean("IsPrimary"),
							rs.getString("Notes"),
							toLocalDateTime(rs.getTimestamp("CreatedAt")),
							toLocalDateTime(rs.getTimestamp("UpdatedAt")),
							rs.getString("EntityType"),
							rs.getString("DisplayName"),
							rs.getString("Email"),
							rs.getString("Phone")
					));
				}
			}
			return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case parties (caseId=" + caseId + ")", e);
		}
	}

	public long addCaseParty(long caseId, Long contactId, Long organizationId, long partyRoleId, String side, boolean primary, String notes) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (partyRoleId <= 0) {
			throw new IllegalArgumentException("partyRoleId must be > 0");
		}
		validateSinglePartyEntity(contactId, organizationId);
		String sql = """
				INSERT INTO dbo.CaseParties (
				  CaseId,
				  ContactId,
				  OrganizationId,
				  PartyRoleId,
				  Side,
				  IsPrimary,
				  Notes,
				  CreatedAt,
				  UpdatedAt
				)
				OUTPUT INSERTED.Id
				SELECT
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
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
				    FROM dbo.PartyRoles pr
				    WHERE pr.Id = ?
				      AND (pr.ShaleClientId = ? OR pr.ShaleClientId IS NULL)
				  )
				  AND (
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Contacts ct
				      WHERE ct.Id = ?
				        AND ct.ShaleClientId = ?
				        AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ))
				    OR
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Organizations o
				      WHERE o.Id = ?
				        AND o.ShaleClientId = ?
				        AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				    ))
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			String normalizedSide = normalizeCasePartySide(con, shaleClientId, side);
			con.setAutoCommit(false);
			int idx = 1;
			ps.setLong(idx++, caseId);
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, organizationId);
			ps.setLong(idx++, partyRoleId);
			setNullableString(ps, idx++, normalizedSide);
			ps.setBoolean(idx++, primary);
			setNullableString(ps, idx++, notes);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, partyRoleId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, contactId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, organizationId);
			setNullableLong(ps, idx++, organizationId);
			ps.setInt(idx++, shaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new RuntimeException("Failed to add case party (caseId=" + caseId + ").");
					}
					long insertedId = rs.getLong(1);
					normalizeCasePartyRelationshipPrimaries(con, caseId, shaleClientId);
					con.commit();
					return insertedId;
				}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to add case party (caseId=" + caseId + ")", e);
		}
	}

	private void insertCasePartyWithValidation(
			Connection con,
			long caseId,
			Long contactId,
			Long organizationId,
			long partyRoleId,
			String side,
			boolean primary,
			String notes,
			int shaleClientId,
			Timestamp now) throws SQLException {
		validateSinglePartyEntity(contactId, organizationId);
		String normalizedSide = normalizeCasePartySide(con, shaleClientId, side);
		String sql = """
				INSERT INTO dbo.CaseParties (
				  CaseId,
				  ContactId,
				  OrganizationId,
				  PartyRoleId,
				  Side,
				  IsPrimary,
				  Notes,
				  CreatedAt,
				  UpdatedAt
				)
				SELECT
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?,
				  ?
				WHERE EXISTS (
				    SELECT 1
				    FROM dbo.Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				)
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.PartyRoles pr
				    WHERE pr.Id = ?
				      AND (pr.ShaleClientId = ? OR pr.ShaleClientId IS NULL)
				  )
				  AND (
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Contacts ct
				      WHERE ct.Id = ?
				        AND ct.ShaleClientId = ?
				        AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ))
				    OR
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Organizations o
				      WHERE o.Id = ?
				        AND o.ShaleClientId = ?
				        AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				    ))
				  );
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setLong(idx++, caseId);
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, organizationId);
			ps.setLong(idx++, partyRoleId);
			setNullableString(ps, idx++, normalizedSide);
			ps.setBoolean(idx++, primary);
			setNullableString(ps, idx++, notes);
			ps.setTimestamp(idx++, now);
			ps.setTimestamp(idx++, now);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, partyRoleId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, contactId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, organizationId);
			setNullableLong(ps, idx++, organizationId);
			ps.setInt(idx++, shaleClientId);
			int rows = ps.executeUpdate();
			if (rows != 1) {
				throw new RuntimeException("Failed to add case party (caseId=" + caseId + ").");
			}
		}
	}

	public void updateCaseParty(long casePartyId,
			long caseId,
			Long contactId,
			Long organizationId,
			long partyRoleId,
			String side,
			boolean primary,
			String notes) {
		if (casePartyId <= 0) {
			throw new IllegalArgumentException("casePartyId must be > 0");
		}
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (partyRoleId <= 0) {
			throw new IllegalArgumentException("partyRoleId must be > 0");
		}
		validateSinglePartyEntity(contactId, organizationId);
		String sql = """
				UPDATE cp
				SET cp.ContactId = ?,
				    cp.OrganizationId = ?,
				    cp.PartyRoleId = ?,
				    cp.Side = ?,
				    cp.IsPrimary = ?,
				    cp.Notes = ?,
				    cp.UpdatedAt = SYSUTCDATETIME()
				FROM dbo.CaseParties cp
				INNER JOIN dbo.Cases c
				  ON c.Id = cp.CaseId
				WHERE cp.Id = ?
				  AND cp.CaseId = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND EXISTS (
				    SELECT 1
				    FROM dbo.PartyRoles pr
				    WHERE pr.Id = ?
				      AND (pr.ShaleClientId = ? OR pr.ShaleClientId IS NULL)
				  )
				  AND (
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Contacts ct
				      WHERE ct.Id = ?
				        AND ct.ShaleClientId = ?
				        AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				    ))
				    OR
				    (? IS NOT NULL AND EXISTS (
				      SELECT 1
				      FROM dbo.Organizations o
				      WHERE o.Id = ?
				        AND o.ShaleClientId = ?
				        AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				    ))
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			String normalizedSide = normalizeCasePartySide(con, shaleClientId, side);
			con.setAutoCommit(false);
			int idx = 1;
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, organizationId);
			ps.setLong(idx++, partyRoleId);
			setNullableString(ps, idx++, normalizedSide);
			ps.setBoolean(idx++, primary);
			setNullableString(ps, idx++, notes);
			ps.setLong(idx++, casePartyId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, partyRoleId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, contactId);
			setNullableLong(ps, idx++, contactId);
			ps.setInt(idx++, shaleClientId);
			setNullableLong(ps, idx++, organizationId);
			setNullableLong(ps, idx++, organizationId);
			ps.setInt(idx++, shaleClientId);

			int rows = ps.executeUpdate();
			if (rows != 1) {
				throw new RuntimeException("Failed to update case party (id=" + casePartyId + ", caseId=" + caseId + ").");
			}
			normalizeCasePartyRelationshipPrimaries(con, caseId, shaleClientId);
			con.commit();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case party (id=" + casePartyId + ", caseId=" + caseId + ")", e);
		}
	}

	public void removeCaseParty(long casePartyId) {
		if (casePartyId <= 0) {
			throw new IllegalArgumentException("casePartyId must be > 0");
		}

		String sql = """
				DELETE cp
				OUTPUT DELETED.CaseId
				FROM dbo.CaseParties cp
				INNER JOIN dbo.Cases c
				  ON c.Id = cp.CaseId
				WHERE cp.Id = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			con.setAutoCommit(false);
			ps.setLong(1, casePartyId);
			ps.setInt(2, shaleClientId);
			Long deletedCaseId = null;
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					deletedCaseId = rs.getLong(1);
				}
			}
			if (deletedCaseId == null) {
				throw new RuntimeException("Failed to remove case party (id=" + casePartyId + ").");
			}
			normalizeCasePartyRelationshipPrimaries(con, deletedCaseId, shaleClientId);
			con.commit();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to remove case party (id=" + casePartyId + ")", e);
		}
	}

	private void normalizeCasePartyRelationshipPrimaries(Connection con, long caseId, int shaleClientId) throws SQLException {
		Long callerRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_CALLER);
		Long partyRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_PARTY);
		Long counselRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_COUNSEL);
		normalizeCasePartyPrimaryBucket(con, caseId, shaleClientId, callerRoleId, null);
		normalizeCasePartyPrimaryBucket(con, caseId, shaleClientId, partyRoleId, PARTY_SIDE_KEY_REPRESENTED);
		normalizeCasePartyPrimaryBucket(con, caseId, shaleClientId, counselRoleId, PARTY_SIDE_KEY_OPPOSING);
	}

	private void normalizeCasePartyPrimaryBucket(Connection con, long caseId, int shaleClientId, Long partyRoleId, String side) throws SQLException {
		if (partyRoleId == null || partyRoleId.longValue() <= 0)
			return;
		String sql = """
				DECLARE @now datetime2 = SYSUTCDATETIME();

				WITH role_bucket AS (
				  SELECT cp.Id,
				         ROW_NUMBER() OVER (
				           ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.Id ASC
				         ) AS rn
				  FROM dbo.CaseParties cp
				  INNER JOIN dbo.Cases c ON c.Id = cp.CaseId
				  WHERE cp.CaseId = ?
				    AND c.ShaleClientId = ?
				    AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				    AND cp.PartyRoleId = ?
				    AND (? IS NULL OR LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = ?)
				)
				UPDATE cp
				SET cp.IsPrimary = CASE WHEN rb.rn = 1 THEN 1 ELSE 0 END,
				    cp.UpdatedAt = @now
				FROM dbo.CaseParties cp
				INNER JOIN role_bucket rb ON rb.Id = cp.Id
				WHERE COALESCE(cp.IsPrimary, 0) <> CASE WHEN rb.rn = 1 THEN 1 ELSE 0 END;
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);
			ps.setLong(3, partyRoleId.longValue());
			setNullableString(ps, 4, side);
			setNullableString(ps, 5, side);
			ps.executeUpdate();
		}
	}

	public void setPrimaryCasePartyOpposingCounsel(
			long caseId,
			int shaleClientId,
			int contactId,
			Integer changedByUserId,
			String notes) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  DECLARE @now datetime2 = SYSUTCDATETIME();

				  IF NOT EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = ?
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  )
				  BEGIN
				    THROW 50002, 'Contact not found for tenant.', 1;
				  END

				  UPDATE cp
				  SET cp.IsPrimary = 0,
				      cp.UpdatedAt = @now
				  FROM dbo.CaseParties cp
				  INNER JOIN dbo.Cases c ON c.Id = cp.CaseId
				  WHERE cp.CaseId = ?
				    AND cp.PartyRoleId = ?
				    AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = ?
				    AND c.ShaleClientId = ?
				    AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);

				  UPDATE cp
				  SET cp.OrganizationId = NULL,
				      cp.ContactId = ?,
				      cp.IsPrimary = 1,
				      cp.Side = ?,
				      cp.Notes = ?,
				      cp.UpdatedAt = @now
				  FROM dbo.CaseParties cp
				  INNER JOIN dbo.Cases c ON c.Id = cp.CaseId
				  WHERE cp.CaseId = ?
				    AND cp.PartyRoleId = ?
				    AND cp.ContactId = ?
				    AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = ?
				    AND c.ShaleClientId = ?
				    AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);

				  IF @@ROWCOUNT = 0
				  BEGIN
				    INSERT INTO dbo.CaseParties
				      (CaseId, ContactId, OrganizationId, PartyRoleId, Side, IsPrimary, Notes, CreatedAt, UpdatedAt)
				    SELECT
				      ?, ?, NULL, ?, ?, 1, ?, @now, @now
				    WHERE EXISTS (
				      SELECT 1
				      FROM dbo.Cases c
				      WHERE c.Id = ?
				        AND c.ShaleClientId = ?
				        AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				    );
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
			Long counselRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_COUNSEL);
			if (counselRoleId == null)
				throw new RuntimeException("Counsel PartyRole is missing.");

			String cleanNotes = (notes == null || notes.isBlank()) ? null : notes.trim();
			int i = 1;
			ps.setInt(i++, contactId);
			ps.setInt(i++, shaleClientId);
			ps.setLong(i++, caseId);
			ps.setLong(i++, counselRoleId.longValue());
			ps.setString(i++, PARTY_SIDE_KEY_OPPOSING);
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, contactId);
			ps.setString(i++, PARTY_SIDE_KEY_OPPOSING);
			ps.setString(i++, cleanNotes);
			ps.setLong(i++, caseId);
			ps.setLong(i++, counselRoleId.longValue());
			ps.setInt(i++, contactId);
			ps.setString(i++, PARTY_SIDE_KEY_OPPOSING);
			ps.setInt(i++, shaleClientId);
			ps.setLong(i++, caseId);
			ps.setInt(i++, contactId);
			ps.setLong(i++, counselRoleId.longValue());
			ps.setString(i++, PARTY_SIDE_KEY_OPPOSING);
			ps.setString(i++, cleanNotes);
			ps.setLong(i++, caseId);
			ps.setInt(i++, shaleClientId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to set primary opposing counsel via case parties (caseId=" + caseId + ")", e);
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
			boolean changed = ps.executeUpdate() > 0;
			if (changed) {
				touchCaseUpdatedAt(con, caseId, shaleClientId);
			}
			return changed;
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

	public void setPrimaryCasePartyCaller(
			long caseId,
			int shaleClientId,
			int contactId,
			Integer changedByUserId,
			String notes) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  DECLARE @now datetime2 = SYSUTCDATETIME();

				  IF NOT EXISTS (
				    SELECT 1
				    FROM dbo.Contacts ct
				    WHERE ct.Id = ?
				      AND ct.ShaleClientId = ?
				      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
				  )
				  BEGIN
				    THROW 50002, 'Contact not found for tenant.', 1;
				  END

				  UPDATE cp
				  SET cp.IsPrimary = 0,
				      cp.UpdatedAt = @now
				  FROM dbo.CaseParties cp
				  INNER JOIN dbo.Cases c ON c.Id = cp.CaseId
				  WHERE cp.CaseId = ?
				    AND cp.PartyRoleId = ?
				    AND c.ShaleClientId = ?
				    AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);

				  UPDATE cp
				  SET cp.OrganizationId = NULL,
				      cp.ContactId = ?,
				      cp.IsPrimary = 1,
				      cp.Notes = ?,
				      cp.UpdatedAt = @now
				  FROM dbo.CaseParties cp
				  INNER JOIN dbo.Cases c ON c.Id = cp.CaseId
				  WHERE cp.CaseId = ?
				    AND cp.PartyRoleId = ?
				    AND cp.ContactId = ?
				    AND c.ShaleClientId = ?
				    AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);

				  IF @@ROWCOUNT = 0
				  BEGIN
				    INSERT INTO dbo.CaseParties
				      (CaseId, ContactId, OrganizationId, PartyRoleId, Side, IsPrimary, Notes, CreatedAt, UpdatedAt)
				    SELECT
				      ?, ?, NULL, ?, NULL, 1, ?, @now, @now
				    WHERE EXISTS (
				      SELECT 1
				      FROM dbo.Cases c
				      WHERE c.Id = ?
				        AND c.ShaleClientId = ?
				        AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				    );
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
			Long callerRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_CALLER);
			if (callerRoleId == null)
				throw new RuntimeException("Caller PartyRole is missing.");

			String cleanNotes = (notes == null || notes.isBlank()) ? null : notes.trim();
			int i = 1;
			ps.setInt(i++, contactId);
			ps.setInt(i++, shaleClientId);
			ps.setLong(i++, caseId);
			ps.setLong(i++, callerRoleId.longValue());
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, contactId);
			ps.setString(i++, cleanNotes);
			ps.setLong(i++, caseId);
			ps.setLong(i++, callerRoleId.longValue());
			ps.setInt(i++, contactId);
			ps.setInt(i++, shaleClientId);
			ps.setLong(i++, caseId);
			ps.setInt(i++, contactId);
			ps.setLong(i++, callerRoleId.longValue());
			ps.setString(i++, cleanNotes);
			ps.setLong(i++, caseId);
			ps.setInt(i++, shaleClientId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to set primary caller via case parties (caseId=" + caseId + ")", e);
		}
	}

	public void syncRepresentedPartyContacts(
			long caseId,
			int shaleClientId,
			List<Integer> contactIds,
			String notes) {
		List<Integer> normalized = (contactIds == null ? List.<Integer>of() : contactIds).stream()
				.filter(Objects::nonNull)
				.map(Integer::intValue)
				.filter(id -> id > 0)
				.distinct()
				.toList();
		String cleanNotes = (notes == null || notes.isBlank()) ? null : notes.trim();
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				Long partyRoleId = findPartyRoleIdForTenantBySystemKey(con, shaleClientId, PARTY_ROLE_NAME_PARTY);
				if (partyRoleId == null) {
					throw new IllegalStateException("Party PartyRole is missing for tenant: " + shaleClientId);
				}
				for (Integer contactId : normalized) {
					String ensureContactSql = """
							SELECT 1
							FROM dbo.Contacts ct
							WHERE ct.Id = ?
							  AND ct.ShaleClientId = ?
							  AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL);
							""";
					try (PreparedStatement ps = con.prepareStatement(ensureContactSql)) {
						ps.setInt(1, contactId);
						ps.setInt(2, shaleClientId);
						try (ResultSet rs = ps.executeQuery()) {
							if (!rs.next()) {
								throw new IllegalArgumentException("Contact not found for tenant: " + contactId);
							}
						}
					}
				}

				String deleteSql = """
						DELETE cp
						FROM dbo.CaseParties cp
						INNER JOIN dbo.Cases c
						  ON c.Id = cp.CaseId
						WHERE cp.CaseId = ?
						  AND c.ShaleClientId = ?
						  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
						  AND cp.PartyRoleId = ?
						  AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = ?
						  AND cp.ContactId IS NOT NULL;
						""";
				try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
					ps.setLong(1, caseId);
					ps.setInt(2, shaleClientId);
					ps.setLong(3, partyRoleId.longValue());
					ps.setString(4, PARTY_SIDE_KEY_REPRESENTED);
					ps.executeUpdate();
				}

				if (!normalized.isEmpty()) {
					String insertSql = """
								INSERT INTO dbo.CaseParties
								  (CaseId, ContactId, OrganizationId, PartyRoleId, Side, IsPrimary, Notes, CreatedAt, UpdatedAt)
								VALUES
								  (?, ?, NULL, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME());
								""";
					try (PreparedStatement ps = con.prepareStatement(insertSql)) {
						for (int i = 0; i < normalized.size(); i++) {
							ps.setLong(1, caseId);
							ps.setInt(2, normalized.get(i));
							ps.setLong(3, partyRoleId.longValue());
							ps.setString(4, PARTY_SIDE_KEY_REPRESENTED);
							ps.setBoolean(5, i == 0);
							ps.setString(6, cleanNotes);
							ps.addBatch();
						}
						ps.executeBatch();
					}
				}
				con.commit();
			} catch (Exception ex) {
				con.rollback();
				throw ex;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (Exception e) {
			throw new RuntimeException(
					"Failed to sync represented party contacts (caseId=" + caseId + ")",
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

				    UPDATE dbo.Cases
				    SET UpdatedAt = @now
				    WHERE Id = ?
				      AND (IsDeleted = 0 OR IsDeleted IS NULL);

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
			ps.setLong(i++, caseId);

			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to set primary status (caseId=" + caseId + ", statusId=" + statusId + ")", e);
		}
	}

	private List<CaseStatusHistoryDto> listCaseStatusHistory(Connection con, long caseId) throws SQLException {
		String sql = """
				SELECT
				  cs.Id AS CaseStatusId,
				  cs.StatusId,
				  s.Name AS StatusName,
				  s.Color AS StatusColor,
				  s.IsClosed,
				  s.LifecycleKey,
				  s.SystemKey,
				  cs.Notes,
				  cs.EffectiveDate,
				  cs.EndDate,
				  cs.CreatedAt,
				  cs.UpdatedAt,
				  cs.IsPrimary
				FROM dbo.CaseStatuses cs
				INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
				WHERE cs.CaseId = ?
				ORDER BY
				  cs.EffectiveDate ASC,
				  cs.CreatedAt ASC,
				  cs.Id ASC;
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				return mapCaseStatusHistory(rs);
			}
		}
	}

	public List<CaseStatusHistoryDto> listCaseStatusHistory(long caseId) {
		String sql = """
				SELECT
				  cs.Id AS CaseStatusId,
				  cs.StatusId,
				  s.Name AS StatusName,
				  s.Color AS StatusColor,
				  s.IsClosed,
				  s.LifecycleKey,
				  s.SystemKey,
				  cs.EffectiveDate,
				  cs.EndDate,
				  cs.CreatedAt,
				  cs.UpdatedAt,
				  cs.IsPrimary,
				  cs.Notes
				FROM dbo.CaseStatuses cs
				INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
				WHERE cs.CaseId = ?
				ORDER BY
				  cs.EffectiveDate ASC,
				  cs.CreatedAt ASC,
				  cs.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				return mapCaseStatusHistory(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load case status history (caseId=" + caseId + ")", e);
		}
	}

	private static List<CaseStatusHistoryDto> mapCaseStatusHistory(ResultSet rs) throws SQLException {
		List<CaseStatusHistoryDto> out = new ArrayList<>();
		while (rs.next()) {
			out.add(new CaseStatusHistoryDto(
				rs.getLong("CaseStatusId"),
				rs.getInt("StatusId"),
				rs.getString("StatusName"),
				rs.getString("StatusColor"),
				rs.getString("LifecycleKey"),
				rs.getString("SystemKey"),
				rs.getBoolean("IsClosed"),
				rs.getString("Notes"),
				toLocalDateTime(rs.getTimestamp("EffectiveDate")),
				toLocalDateTime(rs.getTimestamp("EndDate")),
				toLocalDateTime(rs.getTimestamp("CreatedAt")),
				toLocalDateTime(rs.getTimestamp("UpdatedAt")),
				rs.getBoolean("IsPrimary")));
		}
		return out;
	}

	public static String normalizeLifecycleKey(String lifecycleKey) {
		String normalized = (lifecycleKey == null) ? "" : lifecycleKey.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
		case LIFECYCLE_KEY_ACCEPTED, LIFECYCLE_KEY_DENIED, LIFECYCLE_KEY_CLOSED -> normalized;
		default -> null;
		};
	}

	private static String normalizeLegacyLifecycleKeyFromStatusName(String statusName) {
		String normalized = (statusName == null) ? "" : statusName.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
		case LIFECYCLE_KEY_ACCEPTED, LIFECYCLE_KEY_DENIED, LIFECYCLE_KEY_CLOSED -> normalized;
		default -> null;
		};
	}

	public static String resolveLifecycleKey(String lifecycleKey, String statusName) {
		String normalizedLifecycleKey = normalizeLifecycleKey(lifecycleKey);
		if (normalizedLifecycleKey != null)
			return normalizedLifecycleKey;
		return normalizeLegacyLifecycleKeyFromStatusName(statusName);
	}

	public void populateLifecycleDateIfNull(long caseId, String lifecycleKey) {
		String normalized = normalizeLifecycleKey(lifecycleKey);
		if (normalized == null)
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
			int sortOrder,
			String color,
			String lifecycleKey,
			String systemKey
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
				      AND (pa.ShaleClientId = ? OR pa.ShaleClientId IS NULL)
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

	private static String normalizeSystemKey(String systemKey) {
		String normalized = (systemKey == null) ? "" : systemKey.trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}

	private static String resolveLegacyPartyRoleSystemKeyFromName(String roleName) {
		String normalized = (roleName == null) ? "" : roleName.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
		case PARTY_ROLE_NAME_CALLER, PARTY_ROLE_NAME_PARTY, PARTY_ROLE_NAME_COUNSEL -> normalized;
		default -> null;
		};
	}

	private static String resolvePartyRoleSystemKey(String systemKey, String roleName) {
		String normalizedSystemKey = normalizeSystemKey(systemKey);
		if (normalizedSystemKey != null)
			return normalizedSystemKey;
		return resolveLegacyPartyRoleSystemKeyFromName(roleName);
	}

	private static String resolveLegacyPartySideSystemKeyFromName(String sideName) {
		String normalized = (sideName == null) ? "" : sideName.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
		case PARTY_SIDE_KEY_REPRESENTED, PARTY_SIDE_KEY_OPPOSING, PARTY_SIDE_KEY_NEUTRAL -> normalized;
		default -> null;
		};
	}

	private static String resolvePartySideSystemKey(String systemKey, String sideName) {
		String normalizedSystemKey = normalizeSystemKey(systemKey);
		if (normalizedSystemKey != null)
			return normalizedSystemKey;
		return resolveLegacyPartySideSystemKeyFromName(sideName);
	}

	private record PartyRoleLookupRow(
			long id,
			String name,
			String systemKey
	) {
	}

	private record PartySideLookupRow(
			Long id,
			String name,
			String systemKey
	) {
	}

	private static PartyRoleLookupRow mapPartyRoleLookupRow(ResultSet rs) throws SQLException {
		return new PartyRoleLookupRow(
				rs.getLong("Id"),
				rs.getString("Name"),
				resolvePartyRoleSystemKey(rs.getString("SystemKey"), rs.getString("Name"))
		);
	}

	private static PartySideLookupRow mapPartySideLookupRow(ResultSet rs) throws SQLException {
		return new PartySideLookupRow(
				getNullableLong(rs, "Id"),
				rs.getString("Name"),
				resolvePartySideSystemKey(rs.getString("SystemKey"), rs.getString("Name"))
		);
	}

	private static List<PartyRoleLookupRow> resolveEffectivePartyRoles(List<PartyRoleLookupRow> globalRoles, List<PartyRoleLookupRow> tenantRoles) {
		List<PartyRoleLookupRow> globalUnkeyed = new ArrayList<>();
		List<PartyRoleLookupRow> tenantUnkeyed = new ArrayList<>();
		Map<String, PartyRoleLookupRow> bySystemKey = new LinkedHashMap<>();

		if (globalRoles != null) {
			for (PartyRoleLookupRow role : globalRoles) {
				if (role == null)
					continue;
				String systemKey = resolvePartyRoleSystemKey(role.systemKey(), role.name());
				if (systemKey == null) {
					globalUnkeyed.add(role);
					continue;
				}
				bySystemKey.putIfAbsent(systemKey, role);
			}
		}

		if (tenantRoles != null) {
			for (PartyRoleLookupRow role : tenantRoles) {
				if (role == null)
					continue;
				String systemKey = resolvePartyRoleSystemKey(role.systemKey(), role.name());
				if (systemKey == null) {
					tenantUnkeyed.add(role);
					continue;
				}
				bySystemKey.put(systemKey, role);
			}
		}

		List<PartyRoleLookupRow> merged = new ArrayList<>(globalUnkeyed.size() + bySystemKey.size() + tenantUnkeyed.size());
		merged.addAll(globalUnkeyed);
		merged.addAll(bySystemKey.values());
		merged.addAll(tenantUnkeyed);
		merged.sort((a, b) -> {
			if (a == b)
				return 0;
			if (a == null)
				return 1;
			if (b == null)
				return -1;
			String aName = a.name() == null ? "" : a.name();
			String bName = b.name() == null ? "" : b.name();
			int byName = aName.compareToIgnoreCase(bName);
			if (byName != 0)
				return byName;
			return Long.compare(a.id(), b.id());
		});
		return merged;
	}

	private static List<PartySideLookupRow> resolveEffectivePartySides(List<PartySideLookupRow> globalSides, List<PartySideLookupRow> tenantSides) {
		List<PartySideLookupRow> globalUnkeyed = new ArrayList<>();
		List<PartySideLookupRow> tenantUnkeyed = new ArrayList<>();
		Map<String, PartySideLookupRow> bySystemKey = new LinkedHashMap<>();

		if (globalSides != null) {
			for (PartySideLookupRow side : globalSides) {
				if (side == null)
					continue;
				String systemKey = resolvePartySideSystemKey(side.systemKey(), side.name());
				if (systemKey == null) {
					globalUnkeyed.add(side);
					continue;
				}
				bySystemKey.putIfAbsent(systemKey, side);
			}
		}

		if (tenantSides != null) {
			for (PartySideLookupRow side : tenantSides) {
				if (side == null)
					continue;
				String systemKey = resolvePartySideSystemKey(side.systemKey(), side.name());
				if (systemKey == null) {
					tenantUnkeyed.add(side);
					continue;
				}
				bySystemKey.put(systemKey, side);
			}
		}

		List<PartySideLookupRow> merged = new ArrayList<>(globalUnkeyed.size() + bySystemKey.size() + tenantUnkeyed.size());
		merged.addAll(globalUnkeyed);
		merged.addAll(bySystemKey.values());
		merged.addAll(tenantUnkeyed);
		merged.sort((a, b) -> {
			if (a == b)
				return 0;
			if (a == null)
				return 1;
			if (b == null)
				return -1;
			String aName = a.name() == null ? "" : a.name();
			String bName = b.name() == null ? "" : b.name();
			int byName = aName.compareToIgnoreCase(bName);
			if (byName != 0)
				return byName;
			long aId = a.id() == null ? Long.MAX_VALUE : a.id().longValue();
			long bId = b.id() == null ? Long.MAX_VALUE : b.id().longValue();
			return Long.compare(aId, bId);
		});
		return merged;
	}

	private List<PartyRoleLookupRow> listPartyRoleLookupRowsForTenant(Connection con, int shaleClientId) throws SQLException {
		boolean hasSystemKey = tableHasColumn(con, PARTY_ROLES_TABLE, "SystemKey");
		String systemKeySelect = hasSystemKey ? "SystemKey" : "NULL AS SystemKey";
		String tenantSql = """
				SELECT Id, Name, %s
				FROM dbo.PartyRoles
				WHERE ShaleClientId = ?
				ORDER BY Name, Id;
				""".formatted(systemKeySelect);
		try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
			tenantPs.setInt(1, shaleClientId);
			try (ResultSet tenantRs = tenantPs.executeQuery()) {
				List<PartyRoleLookupRow> tenantRoles = new ArrayList<>();
				while (tenantRs.next()) {
					tenantRoles.add(mapPartyRoleLookupRow(tenantRs));
				}
				String globalSql = """
						SELECT Id, Name, %s
						FROM dbo.PartyRoles
						WHERE ShaleClientId IS NULL
						ORDER BY Name, Id;
						""".formatted(systemKeySelect);
				try (PreparedStatement globalPs = con.prepareStatement(globalSql);
						ResultSet globalRs = globalPs.executeQuery()) {
					List<PartyRoleLookupRow> globalRoles = new ArrayList<>();
					while (globalRs.next()) {
						globalRoles.add(mapPartyRoleLookupRow(globalRs));
					}
					return resolveEffectivePartyRoles(globalRoles, tenantRoles);
				}
			}
		}
	}

	private List<PartySideLookupRow> defaultBuiltinPartySides() {
		return List.of(
				new PartySideLookupRow(null, "Represented", PARTY_SIDE_KEY_REPRESENTED),
				new PartySideLookupRow(null, "Opposing", PARTY_SIDE_KEY_OPPOSING),
				new PartySideLookupRow(null, "Neutral", PARTY_SIDE_KEY_NEUTRAL)
		);
	}

	private List<PartySideLookupRow> listPartySideLookupRowsForTenant(Connection con, int shaleClientId) throws SQLException {
		if (!tableHasColumn(con, PARTY_SIDES_TABLE, "Name")) {
			return defaultBuiltinPartySides();
		}
		boolean hasSystemKey = tableHasColumn(con, PARTY_SIDES_TABLE, "SystemKey");
		String systemKeySelect = hasSystemKey ? "SystemKey" : "NULL AS SystemKey";
		String idSelect = tableHasColumn(con, PARTY_SIDES_TABLE, "Id") ? "Id" : "NULL AS Id";
		String tenantSql = """
				SELECT %s, Name, %s
				FROM dbo.PartySides
				WHERE ShaleClientId = ?
				ORDER BY Name, %s;
				""".formatted(idSelect, systemKeySelect, idSelect);
		try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
			tenantPs.setInt(1, shaleClientId);
			try (ResultSet tenantRs = tenantPs.executeQuery()) {
				List<PartySideLookupRow> tenantSides = new ArrayList<>();
				while (tenantRs.next()) {
					tenantSides.add(mapPartySideLookupRow(tenantRs));
				}
				String globalSql = """
						SELECT %s, Name, %s
						FROM dbo.PartySides
						WHERE ShaleClientId IS NULL
						ORDER BY Name, %s;
						""".formatted(idSelect, systemKeySelect, idSelect);
				try (PreparedStatement globalPs = con.prepareStatement(globalSql);
						ResultSet globalRs = globalPs.executeQuery()) {
					List<PartySideLookupRow> globalSides = new ArrayList<>();
					while (globalRs.next()) {
						globalSides.add(mapPartySideLookupRow(globalRs));
					}
					List<PartySideLookupRow> merged = resolveEffectivePartySides(globalSides, tenantSides);
					if (merged.isEmpty()) {
						return defaultBuiltinPartySides();
					}
					return merged;
				}
			}
		}
	}

	public List<PartySideRow> listPartySides() {
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			List<PartySideLookupRow> effective = listPartySideLookupRowsForTenant(con, shaleClientId);
			List<PartySideRow> out = new ArrayList<>(effective.size());
			for (PartySideLookupRow side : effective) {
				if (side == null)
					continue;
				out.add(new PartySideRow(side.id(), side.name(), resolvePartySideSystemKey(side.systemKey(), side.name())));
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load party sides", e);
		}
	}

	private boolean isAllowedPartySideSystemKey(Connection con, int shaleClientId, String systemKey) throws SQLException {
		String normalized = normalizeSystemKey(systemKey);
		if (normalized == null)
			return false;
		List<PartySideLookupRow> sides = listPartySideLookupRowsForTenant(con, shaleClientId);
		for (PartySideLookupRow side : sides) {
			if (side == null)
				continue;
			if (Objects.equals(normalized, resolvePartySideSystemKey(side.systemKey(), side.name())))
				return true;
		}
		return false;
	}

	private Long findPartyRoleIdForTenantBySystemKey(Connection con, int shaleClientId, String systemKey) throws SQLException {
		String normalized = normalizeSystemKey(systemKey);
		if (shaleClientId <= 0 || normalized == null)
			return null;
		List<PartyRoleLookupRow> roles = listPartyRoleLookupRowsForTenant(con, shaleClientId);
		for (PartyRoleLookupRow role : roles) {
			if (role == null)
				continue;
			if (Objects.equals(normalized, resolvePartyRoleSystemKey(role.systemKey(), role.name())))
				return role.id();
		}
		return null;
	}

	public static boolean isTerminalStatus(String lifecycleKey, String systemKey) {
		String normalizedLifecycle = normalizeLifecycleKey(lifecycleKey);
		if (LIFECYCLE_KEY_CLOSED.equals(normalizedLifecycle) || LIFECYCLE_KEY_DENIED.equals(normalizedLifecycle))
			return true;
		String normalizedSystem = normalizeSystemKey(systemKey);
		return LIFECYCLE_KEY_CLOSED.equals(normalizedSystem) || LIFECYCLE_KEY_DENIED.equals(normalizedSystem);
	}

	public static boolean isTerminalStatus(StatusRow status) {
		if (status == null)
			return false;
		return isTerminalStatus(status.lifecycleKey(), status.systemKey());
	}

	private static StatusRow mapStatusRow(ResultSet rs) throws SQLException {
		return new StatusRow(
				rs.getInt("Id"),
				rs.getString("Name"),
				rs.getInt("SortOrder"),
				rs.getString("Color"),
				resolveLifecycleKey(rs.getString("LifecycleKey"), rs.getString("Name")),
				normalizeSystemKey(rs.getString("SystemKey"))
		);
	}

	private static List<StatusRow> resolveEffectiveStatuses(List<StatusRow> globalStatuses, List<StatusRow> tenantStatuses) {
		List<StatusRow> globalUnkeyed = new ArrayList<>();
		List<StatusRow> tenantUnkeyed = new ArrayList<>();
		Map<String, StatusRow> bySystemKey = new LinkedHashMap<>();

		if (globalStatuses != null) {
			for (StatusRow status : globalStatuses) {
				if (status == null)
					continue;
				String systemKey = normalizeSystemKey(status.systemKey());
				if (systemKey == null) {
					globalUnkeyed.add(status);
					continue;
				}
				bySystemKey.putIfAbsent(systemKey, status);
			}
		}

		if (tenantStatuses != null) {
			for (StatusRow status : tenantStatuses) {
				if (status == null)
					continue;
				String systemKey = normalizeSystemKey(status.systemKey());
				if (systemKey == null) {
					tenantUnkeyed.add(status);
					continue;
				}
				// Tenant status overrides matching global/default status by stable SystemKey.
				bySystemKey.put(systemKey, status);
			}
		}

		List<StatusRow> merged = new ArrayList<>(globalUnkeyed.size() + bySystemKey.size() + tenantUnkeyed.size());
		merged.addAll(globalUnkeyed);
		merged.addAll(bySystemKey.values());
		merged.addAll(tenantUnkeyed);
		merged.sort((a, b) -> {
			if (a == b)
				return 0;
			if (a == null)
				return 1;
			if (b == null)
				return -1;
			int bySortOrder = Integer.compare(a.sortOrder(), b.sortOrder());
			if (bySortOrder != 0)
				return bySortOrder;
			String aName = a.name() == null ? "" : a.name();
			String bName = b.name() == null ? "" : b.name();
			int byName = aName.compareToIgnoreCase(bName);
			if (byName != 0)
				return byName;
			return Integer.compare(a.id(), b.id());
		});
		return merged;
	}

	public List<StatusRow> listStatusesForTenant(int shaleClientId) {
		try (Connection con = db.requireConnection()) {
			boolean hasLifecycleKey = tableHasColumn(con, "Statuses", "LifecycleKey");
			boolean hasSystemKey = tableHasColumn(con, "Statuses", "SystemKey");
			String lifecycleKeySelect = hasLifecycleKey ? "LifecycleKey" : "NULL AS LifecycleKey";
			String systemKeySelect = hasSystemKey ? "SystemKey" : "NULL AS SystemKey";
			String sql = """
					SELECT Id, Name, SortOrder, Color, %s, %s
					FROM %s
					WHERE ShaleClientId = ?
					ORDER BY SortOrder, Name;
					""".formatted(lifecycleKeySelect, systemKeySelect, STATUSES_TABLE);

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<StatusRow> tenantStatuses = new ArrayList<>();
					while (rs.next()) {
						tenantStatuses.add(mapStatusRow(rs));
					}
					String globalSql = """
							SELECT Id, Name, SortOrder, Color, %s, %s
							FROM %s
							WHERE ShaleClientId IS NULL
							ORDER BY SortOrder, Name;
							""".formatted(lifecycleKeySelect, systemKeySelect, STATUSES_TABLE);
					try (PreparedStatement globalPs = con.prepareStatement(globalSql);
							ResultSet globalRs = globalPs.executeQuery()) {
						List<StatusRow> globalStatuses = new ArrayList<>();
						while (globalRs.next()) {
							globalStatuses.add(mapStatusRow(globalRs));
						}
						return resolveEffectiveStatuses(globalStatuses, tenantStatuses);
					}
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list statuses (clientId=" + shaleClientId + ")", e);
		}
	}


	public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
		if (shaleClientId <= 0) {
			return List.of();
		}
		String sql = """
				SELECT Id, ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey
				FROM dbo.Statuses
				WHERE ShaleClientId = ?
				ORDER BY SortOrder, Name, Id;
				""";
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				List<CaseStatusDto> out = new ArrayList<>();
				while (rs.next()) {
					out.add(mapCaseStatusDto(rs));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list tenant case statuses (clientId=" + shaleClientId + ")", e);
		}
	}

	public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
		if (shaleClientId <= 0) {
			return List.of();
		}
		try (Connection con = db.requireConnection()) {
			boolean hasSystemKey = tableHasColumn(con, "PracticeAreas", "SystemKey");
			String systemKeySelect = hasSystemKey ? "SystemKey" : "NULL AS SystemKey";
			String activeFilter = includeInactive ? "" : " AND IsActive = 1 AND IsDeleted = 0";
			String sql = """
					SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, %s
					FROM dbo.PracticeAreas
					WHERE (ShaleClientId IS NULL OR ShaleClientId = ?)
					%s
					ORDER BY CASE WHEN ShaleClientId = ? THEN 1 ELSE 0 END, Name, Id;
					""".formatted(systemKeySelect, activeFilter);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<PracticeAreaDto> globalAreas = new ArrayList<>();
					List<PracticeAreaDto> tenantAreas = new ArrayList<>();
					while (rs.next()) {
						PracticeAreaDto area = mapPracticeAreaDto(rs);
						if (area.shaleClientId() == null) {
							globalAreas.add(area);
						} else if (area.shaleClientId() == shaleClientId) {
							tenantAreas.add(area);
						}
					}
					return resolveEffectivePracticeAreas(globalAreas, tenantAreas);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list practice areas (clientId=" + shaleClientId + ")", e);
		}
	}

	static List<PracticeAreaDto> resolveEffectivePracticeAreas(List<PracticeAreaDto> globalAreas, List<PracticeAreaDto> tenantAreas) {
		Map<String, PracticeAreaDto> byLogicalKey = new LinkedHashMap<>();
		List<PracticeAreaDto> unkeyed = new ArrayList<>();
		if (globalAreas != null) {
			for (PracticeAreaDto area : globalAreas) {
				if (area == null) continue;
				String logicalKey = practiceAreaLogicalKey(area);
				if (logicalKey == null) {
					unkeyed.add(area);
				} else {
					byLogicalKey.putIfAbsent(logicalKey, area);
				}
			}
		}
		if (tenantAreas != null) {
			for (PracticeAreaDto area : tenantAreas) {
				if (area == null) continue;
				String logicalKey = practiceAreaLogicalKey(area);
				if (logicalKey == null) {
					unkeyed.add(area);
				} else {
					byLogicalKey.put(logicalKey, area);
				}
			}
		}
		List<PracticeAreaDto> merged = new ArrayList<>(byLogicalKey.size() + unkeyed.size());
		merged.addAll(byLogicalKey.values());
		merged.addAll(unkeyed);
		merged.sort((a, b) -> {
			if (a == b) return 0;
			if (a == null) return 1;
			if (b == null) return -1;
			String aName = a.name() == null ? "" : a.name();
			String bName = b.name() == null ? "" : b.name();
			int byName = aName.compareToIgnoreCase(bName);
			if (byName != 0) return byName;
			return Integer.compare(a.id(), b.id());
		});
		return merged;
	}

	private static String practiceAreaLogicalKey(PracticeAreaDto area) {
		String systemKey = normalizeSystemKey(area.systemKey());
		if (systemKey != null) {
			return "system:" + systemKey;
		}
		String nameKey = normalizePracticeAreaNameKey(area.name());
		return nameKey == null ? null : "name:" + nameKey;
	}

	private static String normalizePracticeAreaNameKey(String name) {
		String trimmed = name == null ? "" : name.trim();
		return trimmed.isBlank() ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
		if (shaleClientId <= 0) {
			return List.of();
		}
		String activeFilter = includeInactive ? "" : " AND IsActive = 1 AND IsDeleted = 0";
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey
				FROM dbo.PracticeAreas
				WHERE ShaleClientId = ?
				%s
				ORDER BY Name, Id;
				""".formatted(activeFilter);
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				List<PracticeAreaDto> out = new ArrayList<>();
				while (rs.next()) {
					out.add(mapPracticeAreaDto(rs));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list tenant practice areas (clientId=" + shaleClientId + ")", e);
		}
	}

	public PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey) {
		String normalizedName = normalizePracticeAreaName(name);
		String normalizedSystemKey = normalizeSystemKey(systemKey);
		try (Connection con = db.requireConnection()) {
			validatePracticeAreaUnique(con, shaleClientId, null, normalizedName, normalizedSystemKey);
			String sql = """
					INSERT INTO dbo.PracticeAreas (ShaleClientId, Name, Color, IsActive, IsDeleted, CreatedAt, UpdatedAt, SystemKey)
					VALUES (?, ?, ?, ?, 0, SYSUTCDATETIME(), SYSUTCDATETIME(), ?);
					""";
			try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
				ps.setInt(1, shaleClientId);
				ps.setString(2, normalizedName);
				ps.setString(3, trimToNull(color));
				ps.setBoolean(4, active);
				ps.setString(5, normalizedSystemKey);
				ps.executeUpdate();
				try (ResultSet keys = ps.getGeneratedKeys()) {
					if (keys.next()) return findPracticeAreaById(con, keys.getInt(1));
				}
			}
			throw new RuntimeException("Failed to read created practice area id.");
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create practice area.", e);
		}
	}

	public PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey) {
		String normalizedName = normalizePracticeAreaName(name);
		try (Connection con = db.requireConnection()) {
			PracticeAreaDto existing = findPracticeAreaById(con, practiceAreaId);
			if (existing == null) throw new IllegalArgumentException("Practice area not found.");
			String normalizedSystemKey = normalizeSystemKey(systemKey == null ? existing.systemKey() : systemKey);
			if (existing.shaleClientId() == null) {
				return createPracticeArea(shaleClientId, normalizedName, color, active, normalizedSystemKey);
			}
			if (existing.shaleClientId() != shaleClientId) throw new IllegalArgumentException("Practice area belongs to a different tenant.");
			validatePracticeAreaUnique(con, shaleClientId, practiceAreaId, normalizedName, normalizedSystemKey);
			String sql = """
					UPDATE dbo.PracticeAreas
					SET Name = ?, Color = ?, IsActive = ?, UpdatedAt = SYSUTCDATETIME(), SystemKey = ?
					WHERE Id = ? AND ShaleClientId = ?;
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, normalizedName);
				ps.setString(2, trimToNull(color));
				ps.setBoolean(3, active);
				ps.setString(4, normalizedSystemKey);
				ps.setInt(5, practiceAreaId);
				ps.setInt(6, shaleClientId);
				if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Practice area not found for this tenant.");
			}
			return findPracticeAreaById(con, practiceAreaId);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update practice area.", e);
		}
	}

	public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		try (Connection con = db.requireConnection()) {
			PracticeAreaDto existing = findPracticeAreaById(con, practiceAreaId);
			if (existing == null) throw new IllegalArgumentException("Practice area not found.");
			if (existing.systemKey() != null && !existing.systemKey().isBlank()) throw new IllegalArgumentException("System practice areas cannot be removed.");
			if (existing.shaleClientId() == null) throw new IllegalArgumentException("Global practice areas cannot be removed from tenant settings.");
			if (existing.shaleClientId() != shaleClientId) throw new IllegalArgumentException("Practice area belongs to a different tenant.");
			try (PreparedStatement ps = con.prepareStatement("""
					UPDATE dbo.PracticeAreas
					SET IsActive = 0, IsDeleted = 1, UpdatedAt = SYSUTCDATETIME()
					WHERE Id = ? AND ShaleClientId = ?;
					""")) {
				ps.setInt(1, practiceAreaId);
				ps.setInt(2, shaleClientId);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to remove practice area.", e);
		}
	}

	private static PracticeAreaDto mapPracticeAreaDto(ResultSet rs) throws SQLException {
		return new PracticeAreaDto(rs.getInt("Id"), rs.getString("Name"), rs.getString("Color"),
				rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"), normalizeSystemKey(rs.getString("SystemKey")),
				getNullableInt(rs, "ShaleClientId"));
	}

	private PracticeAreaDto findPracticeAreaById(Connection con, int practiceAreaId) throws SQLException {
		String systemKeySelect = tableHasColumn(con, "PracticeAreas", "SystemKey") ? "SystemKey" : "NULL AS SystemKey";
		String sql = "SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, " + systemKeySelect + " FROM dbo.PracticeAreas WHERE Id = ?";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, practiceAreaId);
			try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapPracticeAreaDto(rs) : null; }
		}
	}

	private static void validatePracticeAreaUnique(Connection con, int shaleClientId, Integer excludeId, String name, String systemKey) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT 1 FROM dbo.PracticeAreas
				WHERE ShaleClientId = ? AND IsDeleted = 0
				  AND (LOWER(LTRIM(RTRIM(Name))) = LOWER(?)
				""");
		if (systemKey != null) sql.append(" OR SystemKey = ?");
		sql.append(")");
		if (excludeId != null) sql.append(" AND Id <> ?");
		try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
			int i = 1;
			ps.setInt(i++, shaleClientId);
			ps.setString(i++, name);
			if (systemKey != null) ps.setString(i++, systemKey);
			if (excludeId != null) ps.setInt(i++, excludeId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) throw new IllegalArgumentException("A tenant practice area with this name already exists.");
			}
		}
	}

	private static String normalizePracticeAreaName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isBlank()) throw new IllegalArgumentException("Practice area name is required.");
		return trimmed;
	}

	public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
		if (shaleClientId <= 0) {
			return List.of();
		}
		return toCaseStatusDtos(listStatusesForTenant(shaleClientId));
	}

	static List<CaseStatusDto> toCaseStatusDtos(List<StatusRow> statuses) {
		if (statuses == null || statuses.isEmpty()) {
			return List.of();
		}
		List<CaseStatusDto> out = new ArrayList<>(statuses.size());
		for (StatusRow status : statuses) {
			if (status == null) {
				continue;
			}
			out.add(new CaseStatusDto(
					status.id(),
					status.name(),
					isTerminalStatus(status),
					status.sortOrder(),
					status.color(),
					status.lifecycleKey(),
					status.systemKey(),
					null));
		}
		return out;
	}

	public CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder,
			String color, String lifecycleKey, String systemKey) {
		String normalizedName = normalizeStatusName(name);
		try (Connection con = db.requireConnection()) {
			int effectiveSort = sortOrder == null ? nextStatusSortOrder(con, shaleClientId) : sortOrder;
			String normalizedLifecycle = normalizeLifecycleKey(lifecycleKey);
			String normalizedSystemKey = normalizeSystemKey(systemKey);
			validateCaseStatusUnique(con, shaleClientId, null, normalizedName, normalizedSystemKey);
			String sql = """
					INSERT INTO dbo.Statuses (ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey)
					VALUES (?, ?, ?, ?, ?, ?, ?);
					""";
			try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
				ps.setInt(1, shaleClientId);
				ps.setString(2, normalizedName);
				ps.setBoolean(3, closed);
				ps.setInt(4, effectiveSort);
				ps.setString(5, trimToNull(color));
				ps.setString(6, normalizedLifecycle);
				ps.setString(7, normalizedSystemKey);
				ps.executeUpdate();
				try (ResultSet keys = ps.getGeneratedKeys()) {
					if (keys.next()) {
						return findCaseStatusById(con, keys.getInt(1));
					}
				}
			}
			throw new RuntimeException("Failed to read created case status id.");
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create case status.", e);
		}
	}

	public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed,
			Integer sortOrder, String color, String lifecycleKey, String systemKey) {
		String normalizedName = normalizeStatusName(name);
		try (Connection con = db.requireConnection()) {
			CaseStatusDto existing = findCaseStatusById(con, statusId);
			if (existing == null) {
				throw new IllegalArgumentException("Case status not found.");
			}
			int targetId = statusId;
			Integer targetTenantId = existing.shaleClientId();
			String normalizedLifecycle = normalizeLifecycleKey(lifecycleKey);
			String normalizedSystemKey = normalizeSystemKey(systemKey);
			validateCaseStatusUnique(con, shaleClientId, targetTenantId == null ? null : targetId, normalizedName, normalizedSystemKey);
			if (targetTenantId == null) {
				// Global/default statuses are part of the effective lookup set. Editing them from
				// tenant Settings creates a tenant-scoped override instead of mutating global data.
				return createCaseStatus(shaleClientId, normalizedName, closed, sortOrder, color, normalizedLifecycle,
						normalizedSystemKey == null ? existing.systemKey() : normalizedSystemKey);
			}
			if (targetTenantId != shaleClientId) {
				throw new IllegalArgumentException("Case status belongs to a different tenant.");
			}
			String sql = """
					UPDATE dbo.Statuses
					SET Name = ?, IsClosed = ?, SortOrder = ?, Color = ?, LifecycleKey = ?, SystemKey = ?
					WHERE Id = ? AND ShaleClientId = ?;
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, normalizedName);
				ps.setBoolean(2, closed);
				ps.setInt(3, sortOrder == null ? 0 : sortOrder);
				ps.setString(4, trimToNull(color));
				ps.setString(5, normalizedLifecycle);
				ps.setString(6, normalizedSystemKey);
				ps.setInt(7, targetId);
				ps.setInt(8, shaleClientId);
				if (ps.executeUpdate() == 0) {
					throw new IllegalArgumentException("Case status not found for this tenant.");
				}
			}
			return findCaseStatusById(con, targetId);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case status.", e);
		}
	}

	public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		try (Connection con = db.requireConnection()) {
			CaseStatusDto first = requireTenantEditableStatus(con, shaleClientId, firstStatusId);
			CaseStatusDto second = requireTenantEditableStatus(con, shaleClientId, secondStatusId);
			try (PreparedStatement ps = con.prepareStatement("""
					UPDATE dbo.Statuses
					SET SortOrder = CASE Id WHEN ? THEN ? WHEN ? THEN ? ELSE SortOrder END
					WHERE ShaleClientId = ? AND Id IN (?, ?);
					""")) {
				ps.setInt(1, first.id());
				ps.setInt(2, second.sortOrder() == null ? 0 : second.sortOrder());
				ps.setInt(3, second.id());
				ps.setInt(4, first.sortOrder() == null ? 0 : first.sortOrder());
				ps.setInt(5, shaleClientId);
				ps.setInt(6, first.id());
				ps.setInt(7, second.id());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to reorder case statuses.", e);
		}
	}

	private static CaseStatusDto mapCaseStatusDto(ResultSet rs) throws SQLException {
		return new CaseStatusDto(
				rs.getInt("Id"),
				rs.getString("Name"),
				rs.getBoolean("IsClosed"),
				getNullableInt(rs, "SortOrder"),
				rs.getString("Color"),
				resolveLifecycleKey(rs.getString("LifecycleKey"), rs.getString("Name")),
				normalizeSystemKey(rs.getString("SystemKey")),
				getNullableInt(rs, "ShaleClientId"));
	}

	private CaseStatusDto findCaseStatusById(Connection con, int statusId) throws SQLException {
		String sql = """
				SELECT Id, ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey
				FROM dbo.Statuses
				WHERE Id = ?;
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, statusId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapCaseStatusDto(rs) : null;
			}
		}
	}

	private CaseStatusDto requireTenantEditableStatus(Connection con, int shaleClientId, int statusId) throws SQLException {
		CaseStatusDto status = findCaseStatusById(con, statusId);
		if (status == null) {
			throw new IllegalArgumentException("Case status not found.");
		}
		if (status.shaleClientId() == null) {
			throw new IllegalArgumentException("Create a tenant override before reordering a global status.");
		}
		if (status.shaleClientId() != shaleClientId) {
			throw new IllegalArgumentException("Case status belongs to a different tenant.");
		}
		return status;
	}

	private static void validateCaseStatusUnique(Connection con, int shaleClientId, Integer excludeId, String name,
			String systemKey) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT 1
				FROM dbo.Statuses
				WHERE ShaleClientId = ?
				  AND (
				    LOWER(LTRIM(RTRIM(Name))) = LOWER(?)
				""");
		if (systemKey != null) {
			sql.append(" OR SystemKey = ?");
		}
		sql.append(")");
		if (excludeId != null) {
			sql.append(" AND Id <> ?");
		}
		try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
			int i = 1;
			ps.setInt(i++, shaleClientId);
			ps.setString(i++, name);
			if (systemKey != null) {
				ps.setString(i++, systemKey);
			}
			if (excludeId != null) {
				ps.setInt(i++, excludeId);
			}
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					throw new IllegalArgumentException("A tenant case status with this name or system key already exists.");
				}
			}
		}
	}

	private static Integer nextStatusSortOrder(Connection con, int shaleClientId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(SortOrder), 0) + 10 FROM dbo.Statuses WHERE ShaleClientId = ?")) {
			ps.setInt(1, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 10;
			}
		}
	}

	private static String normalizeStatusName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isBlank()) throw new IllegalArgumentException("Status name is required.");
		return trimmed;
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isBlank() ? null : trimmed;
	}

	public String findLifecycleKeyForStatus(int shaleClientId, int statusId) {
		StatusRow status = findStatusForTenantById(shaleClientId, statusId);
		return status == null ? null : status.lifecycleKey();
	}

	public StatusRow findStatusForTenantById(int shaleClientId, int statusId) {
		if (shaleClientId <= 0 || statusId <= 0)
			return null;
		List<StatusRow> statuses = listStatusesForTenant(shaleClientId);
		for (StatusRow status : statuses) {
			if (status == null || status.id() != statusId)
				continue;
			return status;
		}
		return null;
	}

	public StatusRow findStatusForTenantBySystemKey(int shaleClientId, String systemKey) {
		String normalized = normalizeSystemKey(systemKey);
		if (shaleClientId <= 0 || normalized == null)
			return null;
		List<StatusRow> statuses = listStatusesForTenant(shaleClientId);
		for (StatusRow status : statuses) {
			if (status == null)
				continue;
			if (Objects.equals(normalized, normalizeSystemKey(status.systemKey())))
				return status;
		}
		return null;
	}

	public record PracticeAreaRow(
			int id,
			String name,
			String color,
			String systemKey
	) {
	}

	public List<PracticeAreaRow> listPracticeAreasForTenant(int shaleClientId) {
		try (Connection con = db.requireConnection()) {
			boolean hasSystemKey = tableHasColumn(con, "PracticeAreas", "SystemKey");
			String systemKeySelect = hasSystemKey ? "SystemKey" : "NULL AS SystemKey";
			String tenantSql = """
					SELECT Id, Name, Color, %s
					FROM dbo.PracticeAreas
					WHERE ShaleClientId = ?
					  AND IsActive = 1
					  AND IsDeleted = 0
					ORDER BY Name, Id;
					""".formatted(systemKeySelect);
			try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
				tenantPs.setInt(1, shaleClientId);
				try (ResultSet tenantRs = tenantPs.executeQuery()) {
					List<PracticeAreaRow> tenantAreas = new ArrayList<>();
					while (tenantRs.next()) {
						tenantAreas.add(new PracticeAreaRow(
								tenantRs.getInt("Id"),
								tenantRs.getString("Name"),
								tenantRs.getString("Color"),
								normalizeSystemKey(tenantRs.getString("SystemKey"))
						));
					}
					if (tenantAreas.isEmpty()) {
						seedTenantPracticeAreasFromGlobalTemplates(con, shaleClientId);
						tenantAreas = listTenantPracticeAreas(con, shaleClientId, systemKeySelect);
					}
					return tenantAreas;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list practice areas (clientId=" + shaleClientId + ")", e);
		}
	}

	private List<PracticeAreaRow> listTenantPracticeAreas(Connection con, int shaleClientId, String systemKeySelect) throws SQLException {
		String tenantSql = """
				SELECT Id, Name, Color, %s
				FROM dbo.PracticeAreas
				WHERE ShaleClientId = ?
				  AND IsActive = 1
				  AND IsDeleted = 0
				ORDER BY Name, Id;
				""".formatted(systemKeySelect);
		try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
			tenantPs.setInt(1, shaleClientId);
			try (ResultSet tenantRs = tenantPs.executeQuery()) {
				List<PracticeAreaRow> tenantAreas = new ArrayList<>();
				while (tenantRs.next()) {
					tenantAreas.add(new PracticeAreaRow(
							tenantRs.getInt("Id"),
							tenantRs.getString("Name"),
							tenantRs.getString("Color"),
							normalizeSystemKey(tenantRs.getString("SystemKey"))
					));
				}
				return tenantAreas;
			}
		}
	}

	private void seedTenantPracticeAreasFromGlobalTemplates(Connection con, int shaleClientId) throws SQLException {
		boolean hasSystemKey = tableHasColumn(con, "PracticeAreas", "SystemKey");
		String insertSql = hasSystemKey
				? """
						INSERT INTO dbo.PracticeAreas (ShaleClientId, Name, Color, IsActive, IsDeleted, CreatedAt, UpdatedAt, SystemKey)
						SELECT ?, pa.Name, pa.Color, pa.IsActive, pa.IsDeleted, SYSUTCDATETIME(), SYSUTCDATETIME(), pa.SystemKey
						FROM dbo.PracticeAreas pa
						WHERE pa.ShaleClientId IS NULL
						  AND pa.IsActive = 1
						  AND pa.IsDeleted = 0
						  AND NOT EXISTS (
						    SELECT 1
						    FROM dbo.PracticeAreas existing
						    WHERE existing.ShaleClientId = ?
						      AND (
						        (pa.SystemKey IS NOT NULL AND existing.SystemKey = pa.SystemKey)
						        OR (pa.SystemKey IS NULL AND existing.Name = pa.Name)
						      )
						  );
						"""
				: """
						INSERT INTO dbo.PracticeAreas (ShaleClientId, Name, Color, IsActive, IsDeleted, CreatedAt, UpdatedAt)
						SELECT ?, pa.Name, pa.Color, pa.IsActive, pa.IsDeleted, SYSUTCDATETIME(), SYSUTCDATETIME()
						FROM dbo.PracticeAreas pa
						WHERE pa.ShaleClientId IS NULL
						  AND pa.IsActive = 1
						  AND pa.IsDeleted = 0
						  AND NOT EXISTS (
						    SELECT 1
						    FROM dbo.PracticeAreas existing
						    WHERE existing.ShaleClientId = ?
						      AND existing.Name = pa.Name
						  );
						""";
		try (PreparedStatement ps = con.prepareStatement(insertSql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, shaleClientId);
			int seeded = ps.executeUpdate();
			if (seeded > 0) {
				System.out.println("[PracticeAreaSeed] seeded tenant practice areas from templates shaleClientId=" + shaleClientId
						+ " rowsInserted=" + seeded);
			}
		}
	}

	public PracticeAreaRow findPracticeAreaForTenantBySystemKey(int shaleClientId, String systemKey) {
		String normalized = normalizeSystemKey(systemKey);
		if (shaleClientId <= 0 || normalized == null)
			return null;
		List<PracticeAreaRow> areas = listPracticeAreasForTenant(shaleClientId);
		for (PracticeAreaRow area : areas) {
			if (area == null)
				continue;
			if (Objects.equals(normalized, normalizeSystemKey(area.systemKey())))
				return area;
		}
		return null;
	}

	public PracticeAreaRow findMedicalMalpracticePracticeAreaForTenant(int shaleClientId) {
		return findPracticeAreaForTenantBySystemKey(shaleClientId, PRACTICE_AREA_KEY_MEDICAL_MALPRACTICE);
	}

	public PracticeAreaRow findPersonalInjuryPracticeAreaForTenant(int shaleClientId) {
		return findPracticeAreaForTenantBySystemKey(shaleClientId, PRACTICE_AREA_KEY_PERSONAL_INJURY);
	}

	public PracticeAreaRow findSexualAssaultPracticeAreaForTenant(int shaleClientId) {
		return findPracticeAreaForTenantBySystemKey(shaleClientId, PRACTICE_AREA_KEY_SEXUAL_ASSAULT);
	}

	public void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId) {
		String sql = """
				BEGIN TRY
				  BEGIN TRAN;

				  IF NOT EXISTS (
				    SELECT 1 FROM dbo.Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  ) THROW 50002, 'Case not found.', 1;

				  IF NOT EXISTS (
				    SELECT 1 FROM dbo.PracticeAreas pa
				    WHERE pa.Id = ?
				      AND (pa.ShaleClientId = ? OR pa.ShaleClientId IS NULL)
				      AND pa.IsActive = 1
				      AND pa.IsDeleted = 0
				  ) THROW 50001, 'Practice area not found for tenant.', 1;

				  IF NOT EXISTS (
				    SELECT 1 FROM dbo.Users u
				    WHERE u.id = ?
				      AND u.ShaleClientId = ?
				      AND COALESCE(u.is_attorney, 0) = 1
				      AND COALESCE(u.is_deleted, 0) = 0
				  ) THROW 50003, 'Responsible attorney not found for tenant.', 1;

				  UPDATE dbo.Cases
				  SET PracticeAreaId = ?, UpdatedAt = SYSDATETIME()
				  WHERE Id = ? AND ShaleClientId = ? AND (IsDeleted = 0 OR IsDeleted IS NULL);

				  MERGE dbo.CaseUsers AS target
				  USING (SELECT ? AS CaseId, ? AS UserId, ? AS RoleId, CAST(1 AS bit) AS IsPrimary) AS src
				     ON target.CaseId = src.CaseId
				    AND target.RoleId = src.RoleId
				    AND target.IsPrimary = src.IsPrimary
				  WHEN MATCHED THEN
				      UPDATE SET UserId = src.UserId, UpdatedAt = SYSDATETIME()
				  WHEN NOT MATCHED THEN
				      INSERT (CaseId, UserId, RoleId, IsPrimary, Notes, CreatedAt, UpdatedAt)
				      VALUES (src.CaseId, src.UserId, src.RoleId, CAST(1 AS bit), NULL, SYSDATETIME(), SYSDATETIME());

				  COMMIT;
				END TRY
				BEGIN CATCH
				  IF @@TRANCOUNT > 0 ROLLBACK;
				  THROW;
				END CATCH;
				""";

		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			ps.setLong(i++, caseId);
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, practiceAreaId);
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, responsibleAttorneyUserId);
			ps.setInt(i++, shaleClientId);
			ps.setInt(i++, practiceAreaId);
			ps.setLong(i++, caseId);
			ps.setInt(i++, shaleClientId);
			ps.setLong(i++, caseId);
			ps.setInt(i++, responsibleAttorneyUserId);
			ps.setInt(i++, ROLE_RESPONSIBLE_ATTORNEY);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update case assignment (caseId=" + caseId + ")", e);
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

				UPDATE dbo.Cases
				SET UpdatedAt = SYSDATETIME()
				WHERE Id = ? AND (IsDeleted = 0 OR IsDeleted IS NULL);
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
			ps.setLong(i++, caseId);

			ps.executeUpdate();

		} catch (SQLException e) {
			throw new RuntimeException(
					"Failed to set responsible attorney (caseId=" + caseId + ", userId=" + userId + ")",
					e
			);
		}
	}

	public void setPrimaryLegalAssistant(long caseId, int shaleClientId, int userId) {
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			try (PreparedStatement ps = con.prepareStatement("""
					SELECT 1
					FROM dbo.Cases c
					INNER JOIN dbo.Users u ON u.ShaleClientId = c.ShaleClientId
					WHERE c.Id = ?
					  AND c.ShaleClientId = ?
					  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
					  AND u.Id = ?
					  AND COALESCE(u.is_deleted, 0) = 0;
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				ps.setInt(3, userId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new SQLException("Case or active tenant user was not found.");
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement("""
					UPDATE dbo.CaseUsers
					SET IsPrimary = CAST(0 AS bit), UpdatedAt = SYSDATETIME()
					WHERE CaseId = ?
					  AND RoleId = ?
					  AND IsPrimary = 1
					  AND UserId <> ?;
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, userId);
				ps.executeUpdate();
			}

			int updated;
			try (PreparedStatement ps = con.prepareStatement("""
					UPDATE dbo.CaseUsers
					SET IsPrimary = CAST(1 AS bit), UpdatedAt = SYSDATETIME()
					WHERE CaseId = ?
					  AND UserId = ?
					  AND RoleId = ?;
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, userId);
				ps.setInt(3, ROLE_LEGAL_ASSISTANT);
				updated = ps.executeUpdate();
			}

			if (updated == 0) {
				try (PreparedStatement ps = con.prepareStatement("""
						INSERT INTO dbo.CaseUsers (CaseId, UserId, RoleId, IsPrimary, Notes, CreatedAt, UpdatedAt)
						VALUES (?, ?, ?, CAST(1 AS bit), NULL, SYSDATETIME(), SYSDATETIME());
						""")) {
					ps.setLong(1, caseId);
					ps.setInt(2, userId);
					ps.setInt(3, ROLE_LEGAL_ASSISTANT);
					ps.executeUpdate();
				}
			}

			touchCaseUpdatedAt(con, caseId, shaleClientId);
			con.commit();
		} catch (SQLException e) {
			if (con != null) {
				try { con.rollback(); } catch (SQLException ignored) { }
			}
			throw new RuntimeException("Failed to set primary legal assistant (caseId=" + caseId + ", userId=" + userId + ")", e);
		} finally {
			if (con != null) {
				try { con.setAutoCommit(true); } catch (SQLException ignored) { }
				try { con.close(); } catch (SQLException ignored) { }
			}
		}
	}

	public void removePrimaryLegalAssistant(long caseId, int shaleClientId) {
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			try (PreparedStatement ps = con.prepareStatement("""
					SELECT 1
					FROM dbo.Cases c
					WHERE c.Id = ?
					  AND c.ShaleClientId = ?
					  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new SQLException("Case was not found for tenant.");
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement("""
					DELETE FROM dbo.CaseUsers
					WHERE CaseId = ?
					  AND RoleId = ?
					  AND IsPrimary = 1;
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, ROLE_LEGAL_ASSISTANT);
				ps.executeUpdate();
			}

			touchCaseUpdatedAt(con, caseId, shaleClientId);
			con.commit();
		} catch (SQLException e) {
			if (con != null) {
				try { con.rollback(); } catch (SQLException ignored) { }
			}
			throw new RuntimeException("Failed to remove primary legal assistant (caseId=" + caseId + ")", e);
		} finally {
			if (con != null) {
				try { con.setAutoCommit(true); } catch (SQLException ignored) { }
				try { con.close(); } catch (SQLException ignored) { }
			}
		}
	}

	public void removePrimaryLegalAssistant(long caseId, int shaleClientId) {
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);

			try (PreparedStatement ps = con.prepareStatement("""
					SELECT 1
					FROM dbo.Cases c
					WHERE c.Id = ?
					  AND c.ShaleClientId = ?
					  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new SQLException("Case was not found for tenant.");
					}
				}
			}

			try (PreparedStatement ps = con.prepareStatement("""
					DELETE FROM dbo.CaseUsers
					WHERE CaseId = ?
					  AND RoleId = ?
					  AND IsPrimary = 1;
					""")) {
				ps.setLong(1, caseId);
				ps.setInt(2, ROLE_LEGAL_ASSISTANT);
				ps.executeUpdate();
			}

			touchCaseUpdatedAt(con, caseId, shaleClientId);
			con.commit();
		} catch (SQLException e) {
			if (con != null) {
				try { con.rollback(); } catch (SQLException ignored) { }
			}
			throw new RuntimeException("Failed to remove primary legal assistant (caseId=" + caseId + ")", e);
		} finally {
			if (con != null) {
				try { con.setAutoCommit(true); } catch (SQLException ignored) { }
				try { con.close(); } catch (SQLException ignored) { }
			}
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
				  AND COALESCE(u.%s, 0) = 1
				  AND NULLIF(LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )), '') IS NOT NULL
				""".formatted(RoleSemantics.FLAG_IS_ATTORNEY);

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

	private static void touchCaseUpdatedAt(Connection con, long caseId, int shaleClientId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("""
				UPDATE dbo.Cases
				SET UpdatedAt = SYSDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND (IsDeleted = 0 OR IsDeleted IS NULL);
				""")) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);
			ps.executeUpdate();
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
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
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
							rs.getString("ResponsibleAttorneyColor"),
							getNullableBoolean(rs, "NonEngagementLetterSent"),
							rs.getString("CurrentStatusName"),
							rs.getString("PrimaryStatusColor"),
							rs.getString("PracticeAreaColor"),
							rs.getString("ClientName"),
							rs.getString("OpposingPartiesName"),
							rs.getString("LatestCaseUpdate"),
							rs.getString("Description"),
							toLocalDate(rs.getDate("DateOfIncident")),
							toLocalDate(rs.getDate("TortNoticeDeadline"))
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
					  c.DateOfInjury AS DateOfIncident,
					  c.TortNoticeDeadline,
					  latestUpdate.LatestCaseUpdate,
					  c.Description AS Description,
					  current_status.PrimaryStatusId,
					  current_status.CurrentStatusName,
					  current_status.PrimaryStatusColor,
					  pa.Color AS PracticeAreaColor,
					  clientContact.ClientName,
					  oppContact.OpposingPartiesName,
					  ra.UserId AS ResponsibleAttorneyId,
					  u.color AS ResponsibleAttorneyColor,
				  c.NonEngagementLetterSent AS NonEngagementLetterSent,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName
					FROM %s c
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
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
					OUTER APPLY (
					    SELECT TOP (1)
					      CASE
					        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					        ELSE COALESCE(ct.Name, '')
					      END AS ClientName
					    FROM dbo.CaseParties cp
					    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
					    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id
					      AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
					      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (
					      SELECT
					        LTRIM(RTRIM(
					          CASE
					            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
					              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					            ELSE COALESCE(ct.Name, o.Name, '')
					          END
					        )) AS DisplayName,
					        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
					        cp.UpdatedAt,
					        cp.CreatedAt,
					        cp.Id
					      FROM dbo.CaseParties cp
					      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
					      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id
					        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
					    ) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu
					    WHERE cu.CaseId = c.Id
					      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
					    ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
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
							rs.getString("ResponsibleAttorneyColor"),
							getNullableBoolean(rs, "NonEngagementLetterSent"),
							rs.getString("CurrentStatusName"),
							rs.getString("PrimaryStatusColor"),
							rs.getString("PracticeAreaColor"),
							rs.getString("ClientName"),
							rs.getString("OpposingPartiesName"),
							rs.getString("LatestCaseUpdate"),
							rs.getString("Description"),
							toLocalDate(rs.getDate("DateOfIncident")),
							toLocalDate(rs.getDate("TortNoticeDeadline"))
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
				  CASE WHEN cu.RoleId = ? AND cu.IsPrimary = 1 THEN 0 ELSE 1 END,
				  cu.RoleId,
				  u.name_last,
				  u.name_first,
				  u.Id;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setLong(1, caseId);
			ps.setInt(2, ROLE_RESPONSIBLE_ATTORNEY);

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
						boolean isPrimary = RoleSemantics.isResponsibleAttorneyRoleId(a.roleId());

						ps.setLong(1, caseId);
						ps.setInt(2, a.userId());
						ps.setInt(3, a.roleId());
						ps.setBoolean(4, isPrimary);
						ps.addBatch();
					}
					ps.executeBatch();
				}
			}

			touchCaseUpdatedAt(con, caseId, requireCurrentShaleClientId(con));

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
				  AND u.%s = 1
				""".formatted(RoleSemantics.FLAG_IS_ATTORNEY);

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
		return new CaseSchema(
				existingColumn(con, CASES_TABLE, List.of("IsDeleted", "is_deleted")),
				existingColumn(con, CASES_TABLE, List.of("RowVer", "rowver", "RowVersion", "row_version")));
	}

	private static String resolveCaseUsersDeletedColumn(Connection con) throws SQLException {
		return existingColumn(con, CASE_USERS_TABLE, List.of("IsDeleted", "is_deleted"));
	}

	private static String resolveUsersDeletedColumn(Connection con) throws SQLException {
		return existingColumn(con, USERS_TABLE, List.of("IsDeleted", "is_deleted"));
	}

	private static String membershipExistsFilter(Integer restrictToUserId, String caseUsersDeletedColumn) {
		if (restrictToUserId == null) {
			return "";
		}
		return """
				  AND EXISTS (
				    SELECT 1
				    FROM %s cu_scope
				    WHERE cu_scope.CaseId = c.Id
				      AND cu_scope.UserId = ?
				      AND %s
				  )
				""".formatted(CASE_USERS_TABLE, activeFilter(caseUsersDeletedColumn, "cu_scope"));
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

	public List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive) {
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, RowVer
				FROM dbo.LinkTypes
				WHERE ShaleClientId IS NULL OR ShaleClientId = ?
				ORDER BY Name, Id
				""";
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			return mapLinkTypes(ps);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list link types", e);
		}
	}

	public List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId) {
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, RowVer
				FROM dbo.LinkTypes
				WHERE ShaleClientId IS NULL OR ShaleClientId = ?
				ORDER BY Name, Id
				""";
		try (Connection con = db.requireConnection()) {
			validateAdminActorForTenant(con, shaleClientId, actorUserId);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);
				return mapLinkTypes(ps);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list link types for administration", e);
		}
	}

	public List<LinkTypeDto> listTenantLinkTypes(int shaleClientId, boolean includeInactive) {
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, RowVer
				FROM dbo.LinkTypes
				WHERE ShaleClientId = ?
				  AND (? = 1 OR (IsActive = 1 AND IsDeleted = 0))
				ORDER BY Name, Id
				""";
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setBoolean(2, includeInactive);
			return mapLinkTypes(ps);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list tenant link types", e);
		}
	}

	public LinkTypeDto createLinkType(int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey) {
		try (Connection con = db.requireConnection()) {
			validateAdminActorForTenant(con, shaleClientId, actorUserId);
			return insertTenantLinkType(con, shaleClientId, actorUserId, name, color, active, normalizeSystemKey(systemKey));
		} catch (SQLException e) {
			throw translateSql("Failed to create link type", e);
		}
	}

	public LinkTypeDto updateLinkType(int shaleClientId, int actorUserId, int linkTypeId, String name, String color,
			boolean active, String systemKey, byte[] expectedRowVer) {
		requireRowVer(expectedRowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			validateAdminActorForTenant(con, shaleClientId, actorUserId);
			LinkTypeDto existing = findLinkTypeById(con, linkTypeId);
			if (existing == null || (existing.shaleClientId() != null && existing.shaleClientId() != shaleClientId)) {
				throw new IllegalArgumentException("Link type is not available for this tenant.");
			}
			if (existing.shaleClientId() == null) {
				assertRowVerMatches("dbo.LinkTypes", linkTypeId, expectedRowVer, con, "Link type changed.");
				return customizeGlobalLinkType(con, shaleClientId, actorUserId, existing, name, color, active, systemKey);
			}
			return updateTenantLinkType(con, shaleClientId, actorUserId, linkTypeId, name, color, active,
					normalizeSystemKey(systemKey == null ? existing.systemKey() : systemKey), expectedRowVer);
		} catch (SQLException e) {
			throw translateSql("Failed to update link type", e);
		}
	}

	public LinkTypeDto setLinkTypeActive(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer) {
		requireRowVer(expectedRowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			validateAdminActorForTenant(con, shaleClientId, actorUserId);
			LinkTypeDto existing = findLinkTypeById(con, linkTypeId);
			if (existing == null || (existing.shaleClientId() != null && existing.shaleClientId() != shaleClientId)) {
				throw new IllegalArgumentException("Link type is not available for this tenant.");
			}
			if (existing.shaleClientId() == null) {
				assertRowVerMatches("dbo.LinkTypes", linkTypeId, expectedRowVer, con, "Link type changed.");
				return customizeGlobalLinkType(con, shaleClientId, actorUserId, existing, existing.name(),
						existing.color(), active, existing.systemKey());
			}
			return updateTenantLinkType(con, shaleClientId, actorUserId, linkTypeId, existing.name(),
					existing.color(), active, existing.systemKey(), expectedRowVer);
		} catch (SQLException e) {
			throw translateSql("Failed to set link type active", e);
		}
	}

	public void resetLinkTypeOverride(int shaleClientId, int actorUserId, int linkTypeId) {
		try (Connection con = db.requireConnection()) {
			validateAdminActorForTenant(con, shaleClientId, actorUserId);
			LinkTypeDto requested = findLinkTypeById(con, linkTypeId);
			if (requested == null || (requested.shaleClientId() != null && requested.shaleClientId() != shaleClientId)) {
				throw new IllegalArgumentException("Link type is not available for this tenant.");
			}
			LinkTypeDto override = requested.shaleClientId() == null
					? findTenantLinkTypeBySystemKey(con, shaleClientId, requested.systemKey())
					: requested;
			if (override == null) {
				return;
			}
			softDeleteTenantLinkType(con, shaleClientId, actorUserId, override.id());
		} catch (SQLException e) {
			throw new RuntimeException("Failed to reset link type override", e);
		}
	}

	public List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId) {
		try (Connection con = db.requireConnection()) {
			return listCaseLinks(con, caseId, shaleClientId);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case links", e);
		}
	}

	public java.util.Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId) {
		String sql = caseLinkSelect() + """
				 WHERE cl.CaseId = ?
				   AND cl.ShaleClientId = ?
				   AND el.ShaleClientId = cl.ShaleClientId
				   AND cl.IsDeleted = 0
				   AND el.IsDeleted = 0
				   AND cl.IsPrimary = 1
				   AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cl.ShaleClientId)
				 ORDER BY cl.SortOrder ASC, LOWER(el.DisplayName), cl.Id
				""";
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(topOne(sql))) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return java.util.Optional.empty();
				CaseLinkDto link = mapCaseLinkDto(rs);
				return java.util.Optional.of(withShares(link, listCaseLinkShares(con, caseId, link.caseLinkId(), shaleClientId)));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load primary case link", e);
		}
	}

	public CaseLinkDto createCaseLink(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName,
			String url, String description, boolean primary, String notes, Integer sortOrder) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);
				boolean first = !hasActiveCaseLinks(con, shaleClientId, caseId);
				long externalId = insertExternalLink(con, shaleClientId, actorUserId, linkTypeId, displayName, url, description);
				long caseLinkId = insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, primary || first,
						notes, sortOrder == null ? nextSortOrder(con, shaleClientId, caseId) : sortOrder);
				if (primary || first) {
					setOnlyPrimary(con, shaleClientId, caseId, caseLinkId, actorUserId);
				}
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("Failed to create case link", e);
		}
	}

	public CaseLinkDto createCaseLinkWithShares(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName,
			String url, String description, boolean primary, String notes, Integer sortOrder, List<CaseLinkShareDraft> shares) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);
				validateShareDraftContacts(con, shaleClientId, shares);
				boolean first = !hasActiveCaseLinks(con, shaleClientId, caseId);
				long externalId = insertExternalLink(con, shaleClientId, actorUserId, linkTypeId, displayName, url, description);
				long caseLinkId = insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, primary || first, notes, sortOrder == null ? nextSortOrder(con, shaleClientId, caseId) : sortOrder);
				for (CaseLinkShareDraft share : shares == null ? List.<CaseLinkShareDraft>of() : shares) insertCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.contactId(), share.sharedAt(), share.notes());
				if (primary || first) setOnlyPrimary(con, shaleClientId, caseId, caseLinkId, actorUserId);
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) { con.rollback(); throw e; } finally { con.setAutoCommit(true); }
		} catch (SQLException e) { throw translateSql("Failed to create case link with shares", e); }
	}

	public CaseLinkDto updateCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId,
			int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder,
			byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer) {
		requireRowVer(expectedCaseLinkRowVer, "expectedCaseLinkRowVer");
		requireRowVer(expectedExternalLinkRowVer, "expectedExternalLinkRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);
				CaseLinkDto existing = validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, externalLinkId);
				updateExternalLinkRow(con, shaleClientId, actorUserId, externalLinkId, linkTypeId, displayName, url,
						description, expectedExternalLinkRowVer);
				updateCaseLinkRow(con, shaleClientId, actorUserId, caseLinkId, notes, sortOrder, expectedCaseLinkRowVer);
				applyPrimaryUpdate(con, shaleClientId, actorUserId, caseId, caseLinkId, existing.primary(), primary);
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("Failed to update case link", e);
		}
	}

	public CaseLinkDto updateCaseLinkWithShares(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId,
			int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder,
			byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer, List<CaseLinkShareDraft> adds, List<CaseLinkShareUpdate> updates, List<CaseLinkShareRemoval> removals) {
		requireRowVer(expectedCaseLinkRowVer, "expectedCaseLinkRowVer"); requireRowVer(expectedExternalLinkRowVer, "expectedExternalLinkRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId); validateActorForTenant(con, shaleClientId, actorUserId); validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);
				CaseLinkDto existing = validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, externalLinkId);
				validateShareDraftContacts(con, shaleClientId, adds); validateShareUpdatesAndRemovals(con, shaleClientId, caseLinkId, updates, removals);
				updateExternalLinkRow(con, shaleClientId, actorUserId, externalLinkId, linkTypeId, displayName, url, description, expectedExternalLinkRowVer);
				updateCaseLinkRow(con, shaleClientId, actorUserId, caseLinkId, notes, sortOrder, expectedCaseLinkRowVer);
				for (CaseLinkShareDraft share : adds == null ? List.<CaseLinkShareDraft>of() : adds) insertCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.contactId(), share.sharedAt(), share.notes());
				for (CaseLinkShareUpdate share : updates == null ? List.<CaseLinkShareUpdate>of() : updates) updateCaseLinkShareRow(con, shaleClientId, actorUserId, caseLinkId, share.caseLinkShareId(), share.contactId(), share.sharedAt(), share.notes(), share.expectedRowVer());
				for (CaseLinkShareRemoval share : removals == null ? List.<CaseLinkShareRemoval>of() : removals) softDeleteCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.caseLinkShareId(), share.expectedRowVer());
				applyPrimaryUpdate(con, shaleClientId, actorUserId, caseId, caseLinkId, existing.primary(), primary);
				con.commit(); return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) { con.rollback(); throw e; } finally { con.setAutoCommit(true); }
		} catch (SQLException e) { throw translateSql("Failed to update case link with shares", e); }
	}

	public CaseLinkDto setPrimaryCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, null);
				setOnlyPrimary(con, shaleClientId, caseId, caseLinkId, actorUserId);
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("Failed to set primary case link", e);
		}
	}

	public List<CaseLinkDto> reorderCaseLinks(int shaleClientId, int actorUserId, long caseId, List<Long> ids) {
		if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Ordered case link ids are required.");
		}
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new IllegalArgumentException("Ordered case link ids must not contain duplicates.");
		}
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				List<CaseLinkDto> active = listCaseLinks(con, caseId, shaleClientId);
				Set<Long> expected = new HashSet<>();
				for (CaseLinkDto dto : active) {
					expected.add(dto.caseLinkId());
				}
				if (!new HashSet<>(ids).equals(expected)) {
					throw new IllegalArgumentException("Reorder must include each active case link exactly once.");
				}
				int order = 0;
				for (Long id : ids) {
					updateCaseLinkSortOrder(con, shaleClientId, caseId, actorUserId, id, order++);
				}
				con.commit();
				return listCaseLinks(con, caseId, shaleClientId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to reorder case links", e);
		}
	}

	public void deleteCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, byte[] expectedCaseLinkRowVer) {
		requireRowVer(expectedCaseLinkRowVer, "expectedCaseLinkRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				CaseLinkDto dto = findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
				if (dto == null) {
					throw new IllegalArgumentException("Case link is not available for this tenant.");
				}
				softDeleteCaseLinkSharesForLink(con, shaleClientId, actorUserId, caseLinkId);
				softDeleteCaseLink(con, shaleClientId, actorUserId, caseId, caseLinkId, expectedCaseLinkRowVer);
				if (dto.primary()) {
					selectNextPrimary(con, shaleClientId, caseId, actorUserId);
				}
				softDeleteExternalIfUnreferenced(con, shaleClientId, dto.externalLinkId(), actorUserId);
				con.commit();
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to delete case link", e);
		}
	}


	public List<CaseLinkContactOptionDto> searchCaseLinkShareContacts(int tenant, String query, int limit) {
		String q = query == null ? "" : query.trim(); int resolvedLimit = limit <= 0 ? 25 : Math.min(limit, 100);
		String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
		String sql = """
			SELECT TOP (?) ct.Id AS ContactId,
			       %s AS DisplayName
			FROM dbo.Contacts ct
			WHERE ct.ShaleClientId = ? AND ISNULL(ct.IsDeleted, 0) = 0
			  AND %s IS NOT NULL
			  AND (? = '' OR LOWER(COALESCE(ct.Name,'') + ' ' + COALESCE(ct.FirstName,'') + ' ' + COALESCE(ct.LastName,'') + ' ' + COALESCE(ct.WorkName,'') + ' ' + COALESCE(ct.EmailPersonal,'') + ' ' + COALESCE(ct.EmailWork,'') + ' ' + COALESCE(ct.EmailOther,'')) LIKE ?)
			ORDER BY DisplayName ASC, ct.Id ASC
			""".formatted(caseLinkShareContactDisplayNameExpression("ct"), caseLinkShareContactDisplayNameExpression("ct"));
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, resolvedLimit); ps.setInt(2, tenant); ps.setString(3, q); ps.setString(4, like);
			try (ResultSet rs = ps.executeQuery()) { return mapCaseLinkContactOptions(rs); }
		} catch (SQLException e) { throw new RuntimeException("Failed to search share contacts", e); }
	}

	public List<CaseLinkContactOptionDto> listCaseLinkShareContacts(int tenant) {
		String sql = """
			SELECT ct.Id AS ContactId,
			       %s AS DisplayName
			FROM dbo.Contacts ct
			WHERE ct.ShaleClientId = ? AND ISNULL(ct.IsDeleted, 0) = 0
			  AND %s IS NOT NULL
			ORDER BY DisplayName ASC, ct.Id ASC
			""".formatted(caseLinkShareContactDisplayNameExpression("ct"), caseLinkShareContactDisplayNameExpression("ct"));
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			try (ResultSet rs = ps.executeQuery()) { return mapCaseLinkContactOptions(rs); }
		} catch (SQLException e) { throw new RuntimeException("Failed to list share contacts", e); }
	}

	public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts(long caseId, int tenant) {
		String displayName = caseLinkShareContactDisplayNameExpression("ct");
		String sql = """
			SELECT ct.Id AS ContactId,
			       %s AS DisplayName
			FROM dbo.CaseParties cp
			JOIN dbo.Cases c ON c.Id = cp.CaseId
			JOIN dbo.Contacts ct ON ct.Id = cp.ContactId
			WHERE cp.CaseId = ?
			  AND cp.ContactId IS NOT NULL
			  AND c.ShaleClientId = ? AND ISNULL(c.IsDeleted, 0) = 0
			  AND ct.ShaleClientId = ? AND ISNULL(ct.IsDeleted, 0) = 0
			  AND %s IS NOT NULL
			GROUP BY ct.Id, %s
			ORDER BY DisplayName ASC, ct.Id ASC
			""".formatted(displayName, displayName, displayName);
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId); ps.setInt(2, tenant); ps.setInt(3, tenant);
			try (ResultSet rs = ps.executeQuery()) { return mapCaseLinkContactOptions(rs); }
		} catch (SQLException e) { throw new RuntimeException("Failed to list CaseParties-backed case share contacts", e); }
	}

	private static String caseLinkShareContactDisplayNameExpression(String alias) {
		return "COALESCE(NULLIF(LTRIM(RTRIM(" + alias + ".Name)), ''), "
				+ "NULLIF(LTRIM(RTRIM(CONCAT(" + alias + ".FirstName, ' ', " + alias + ".LastName))), ''), "
				+ "NULLIF(LTRIM(RTRIM(" + alias + ".WorkName)), ''))";
	}

	private static List<CaseLinkContactOptionDto> mapCaseLinkContactOptions(ResultSet rs) throws SQLException {
		List<CaseLinkContactOptionDto> out = new ArrayList<>();
		while (rs.next()) out.add(new CaseLinkContactOptionDto(rs.getInt("ContactId"), rs.getString("DisplayName")));
		return out;
	}

	public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact(int contactId, int shaleClientId) {
		long startedNanos = System.nanoTime();
		try (Connection con = db.requireConnection()) {
			int sessionShaleClientId = requireCurrentShaleClientId(con);
			if (sessionShaleClientId != shaleClientId) {
				throw new IllegalStateException("ShaleClientId session context " + sessionShaleClientId
						+ " does not match requested ShaleClientId " + shaleClientId + ".");
			}
			validateActiveContactForTenant(con, shaleClientId, contactId);
			String sql = """
					SELECT c.Id AS SharedCaseId, c.Name AS SharedCaseDisplayName, linkRows.*
					FROM dbo.CaseLinkShares cls
					JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId
					 AND cl.ShaleClientId = cls.ShaleClientId
					 AND cl.IsDeleted = 0
					JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId
					 AND el.ShaleClientId = cl.ShaleClientId
					 AND el.IsDeleted = 0
					JOIN dbo.LinkTypes lt ON lt.Id = el.LinkTypeId
					 AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cls.ShaleClientId)
					 AND lt.IsDeleted = 0
					JOIN dbo.Cases c ON c.Id = cl.CaseId
					 AND c.ShaleClientId = cls.ShaleClientId
					JOIN dbo.Contacts targetContact ON targetContact.Id = cls.ContactId
					 AND targetContact.ShaleClientId = cls.ShaleClientId
					 AND ISNULL(targetContact.IsDeleted, 0) = 0
					CROSS APPLY (
						SELECT cl.Id AS CaseLinkId, cl.ExternalLinkId, cl.CaseId, cl.ShaleClientId, el.LinkTypeId,
						       lt.Name AS LinkTypeName, lt.Color AS LinkTypeColor, lt.SystemKey AS LinkTypeSystemKey,
						       el.DisplayName, el.Url, el.Description, cl.IsPrimary, cl.Notes, cl.SortOrder,
						       cl.CreatedAt, cl.UpdatedAt, cl.RowVer AS CaseLinkRowVer, el.RowVer AS ExternalLinkRowVer
					) linkRows
					WHERE cls.ShaleClientId = ?
					  AND cls.ContactId = ?
					  AND cls.IsDeleted = 0
					ORDER BY LOWER(c.Name), c.Id, cl.IsPrimary DESC, LOWER(lt.Name), cl.SortOrder, LOWER(el.DisplayName), cl.Id
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);
				ps.setInt(2, contactId);
				try (ResultSet rs = ps.executeQuery()) {
					List<ContactSharedCaseLinkDto> rows = new ArrayList<>();
					List<CaseLinkDto> links = new ArrayList<>();
					List<Long> ids = new ArrayList<>();
					List<String> names = new ArrayList<>();
					List<Long> caseIds = new ArrayList<>();
					while (rs.next()) {
						CaseLinkDto link = mapCaseLinkDto(rs);
						links.add(link); ids.add(link.caseLinkId()); caseIds.add(rs.getLong("SharedCaseId")); names.add(rs.getString("SharedCaseDisplayName"));
					}
					if (links.isEmpty()) logContactSharedLinkJoinStages(con, shaleClientId, contactId, 0, startedNanos);
					Map<Long, List<CaseLinkShareDto>> shares = listCaseLinkSharesForLinks(con, shaleClientId, ids);
					for (int i = 0; i < links.size(); i++) rows.add(new ContactSharedCaseLinkDto(caseIds.get(i), names.get(i), withShares(links.get(i), shares.get(links.get(i).caseLinkId()))));
					return List.copyOf(rows);
				}
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to list case links shared with contact", e); }
	}

	private void logContactSharedLinkJoinStages(Connection con, int tenant, int contactId, int finalReturnedCount, long startedNanos) {
		String dbUser = null;
		Integer sessionTenant = null;
		try (PreparedStatement ps = con.prepareStatement("SELECT USER_NAME(), TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId'))");
				ResultSet rs = ps.executeQuery()) {
			if (rs.next()) {
				dbUser = rs.getString(1);
				sessionTenant = getNullableInt(rs, 2);
			}
		} catch (SQLException e) {
			LOG.log(Level.FINE, "operation=contacts.sharedLinks.sessionDiagnostic.failure tenantId=" + tenant + " contactId=" + contactId, e);
		}
		int activeShareCount = countContactSharedLinksStage(con, tenant, contactId, """
				SELECT COUNT(*)
				FROM dbo.CaseLinkShares AS cls
				WHERE cls.ShaleClientId = ?
				  AND cls.ContactId = ?
				  AND cls.IsDeleted = 0
				""");
		int caseLinkExternalLinkJoinCount = countContactSharedLinksStage(con, tenant, contactId, """
				SELECT COUNT(*)
				FROM dbo.CaseLinkShares AS cls
				JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId
				 AND cl.ShaleClientId = cls.ShaleClientId
				 AND cl.IsDeleted = 0
				JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId
				 AND el.ShaleClientId = cl.ShaleClientId
				 AND el.IsDeleted = 0
				WHERE cls.ShaleClientId = ?
				  AND cls.ContactId = ?
				  AND cls.IsDeleted = 0
				""");
		int completeProductionJoinCount = countContactSharedLinksStage(con, tenant, contactId, """
				SELECT COUNT(*)
				FROM dbo.CaseLinkShares AS cls
				JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId
				 AND cl.ShaleClientId = cls.ShaleClientId
				 AND cl.IsDeleted = 0
				JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId
				 AND el.ShaleClientId = cl.ShaleClientId
				 AND el.IsDeleted = 0
				JOIN dbo.LinkTypes lt ON lt.Id = el.LinkTypeId
				 AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cls.ShaleClientId)
				 AND lt.IsDeleted = 0
				JOIN dbo.Cases c ON c.Id = cl.CaseId
				 AND c.ShaleClientId = cls.ShaleClientId
				JOIN dbo.Contacts targetContact ON targetContact.Id = cls.ContactId
				 AND targetContact.ShaleClientId = cls.ShaleClientId
				 AND ISNULL(targetContact.IsDeleted, 0) = 0
				WHERE cls.ShaleClientId = ?
				  AND cls.ContactId = ?
				  AND cls.IsDeleted = 0
				""");
		long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
		String message = "operation=contacts.sharedLinks.daoZeroResult tenantId=" + tenant
				+ " contactId=" + contactId
				+ " databaseUser=" + dbUser
				+ " sessionTenantId=" + sessionTenant
				+ " activeShareCount=" + activeShareCount
				+ " caseLinkExternalLinkJoinCount=" + caseLinkExternalLinkJoinCount
				+ " completeProductionJoinCount=" + completeProductionJoinCount
				+ " finalCount=" + finalReturnedCount
				+ " sqlParameterCount=2 parameter1TenantId=" + tenant
				+ " parameter2ContactId=" + contactId
				+ " elapsedMs=" + elapsedMs;
		LOG.info(message);
	}

	private int countContactSharedLinksStage(Connection con, int tenant, int contactId, String sql) {
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setInt(2, contactId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : -1;
			}
		} catch (SQLException e) {
			LOG.log(Level.FINE, "operation=contacts.sharedLinks.stageCount.failure tenantId=" + tenant + " contactId=" + contactId, e);
			return -1;
		}
	}

	public List<CaseLinkShareDto> listCaseLinkShares(long caseId, long caseLinkId, int shaleClientId) {
		try (Connection con = db.requireConnection()) {
			validateCaseForTenant(con, shaleClientId, caseId);
			validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, null);
			return listCaseLinkShares(con, caseId, caseLinkId, shaleClientId);
		} catch (SQLException e) { throw new RuntimeException("Failed to list case link shares", e); }
	}

	public CaseLinkShareDto addCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, int contactId, LocalDateTime sharedAt, String notes) {
		try (Connection con = db.requireConnection()) {
			validateCaseForTenant(con, tenant, caseId); validateActorForTenant(con, tenant, actor); validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null); validateActiveContactForTenant(con, tenant, contactId);
			long shareId = insertCaseLinkShare(con, tenant, actor, caseLinkId, contactId, sharedAt, notes);
			return findCaseLinkShare(con, tenant, caseId, caseLinkId, shareId);
		} catch (SQLException e) { throw translateSql("Failed to create case link share", e); }
	}

	public CaseLinkShareDto updateCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, long shareId, int contactId, LocalDateTime sharedAt, String notes, byte[] rowVer) {
		requireRowVer(rowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			validateCaseForTenant(con, tenant, caseId); validateActorForTenant(con, tenant, actor); validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null); validateActiveContactForTenant(con, tenant, contactId);
			updateCaseLinkShareRow(con, tenant, actor, caseLinkId, shareId, contactId, sharedAt, notes, rowVer);
			return findCaseLinkShare(con, tenant, caseId, caseLinkId, shareId);
		} catch (SQLException e) { throw translateSql("Failed to update case link share", e); }
	}

	public void removeCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, long shareId, byte[] rowVer) {
		requireRowVer(rowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			validateCaseForTenant(con, tenant, caseId); validateActorForTenant(con, tenant, actor); validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null);
			softDeleteCaseLinkShare(con, tenant, actor, caseLinkId, shareId, rowVer);
		} catch (SQLException e) { throw new RuntimeException("Failed to remove case link share", e); }
	}

	private static CaseLinkDto withShares(CaseLinkDto link, List<CaseLinkShareDto> shares) {
		return new CaseLinkDto(link.caseLinkId(), link.externalLinkId(), link.caseId(), link.shaleClientId(), link.linkTypeId(), link.linkTypeName(), link.linkTypeColor(), link.linkTypeSystemKey(), link.displayName(), link.url(), link.description(), link.primary(), link.notes(), link.sortOrder(), link.createdAt(), link.updatedAt(), link.caseLinkRowVer(), link.externalLinkRowVer(), shares == null ? List.of() : shares);
	}

	private List<CaseLinkShareDto> listCaseLinkShares(Connection con, long caseId, long caseLinkId, int tenant) throws SQLException { return listCaseLinkSharesForLinks(con, tenant, List.of(caseLinkId)).getOrDefault(caseLinkId, List.of()); }

	private Map<Long, List<CaseLinkShareDto>> listCaseLinkSharesForLinks(Connection con, int tenant, List<Long> linkIds) throws SQLException {
		if (linkIds == null || linkIds.isEmpty()) return Map.of();
		String placeholders = String.join(",", java.util.Collections.nCopies(linkIds.size(), "?"));
		String sql = """
			SELECT cls.Id AS CaseLinkShareId, cls.ShaleClientId, cls.CaseLinkId, cls.ContactId,
			       LTRIM(RTRIM(CONCAT(COALESCE(ct.FirstName, ''), ' ', COALESCE(ct.LastName, '')))) AS ContactDisplayName,
			       ct.Name AS ContactName, ct.IsDeleted AS ContactIsDeleted,
			       cls.SharedAt, cls.Notes, cls.IsDeleted, cls.CreatedByUserId, cls.CreatedAt, cls.UpdatedAt, cls.RowVer
			FROM dbo.CaseLinkShares cls
			JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId AND cl.ShaleClientId = cls.ShaleClientId AND cl.IsDeleted = 0
			JOIN dbo.Cases c ON c.Id = cl.CaseId AND c.ShaleClientId = cls.ShaleClientId AND c.IsDeleted = 0
			LEFT JOIN dbo.Contacts ct ON ct.Id = cls.ContactId AND ct.ShaleClientId = cls.ShaleClientId
			WHERE cls.ShaleClientId = ? AND cls.IsDeleted = 0 AND cls.CaseLinkId IN (""" + placeholders + ") ORDER BY ContactDisplayName, cls.ContactId, cls.Id";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant); int i=2; for (Long id: linkIds) ps.setLong(i++, id);
			try (ResultSet rs = ps.executeQuery()) { Map<Long, List<CaseLinkShareDto>> out = new LinkedHashMap<>(); while (rs.next()) out.computeIfAbsent(rs.getLong("CaseLinkId"), k -> new ArrayList<>()).add(mapCaseLinkShareDto(rs)); return out; }
		}
	}

	private CaseLinkShareDto findCaseLinkShare(Connection con, int tenant, long caseId, long caseLinkId, long shareId) throws SQLException {
		List<CaseLinkShareDto> shares = listCaseLinkShares(con, caseId, caseLinkId, tenant); return shares.stream().filter(s -> s.caseLinkShareId() == shareId).findFirst().orElse(null);
	}

	private static CaseLinkShareDto mapCaseLinkShareDto(ResultSet rs) throws SQLException {
		String display = rs.getString("ContactDisplayName"); if (display == null || display.isBlank()) display = rs.getString("ContactName"); boolean unavailable = rs.getBoolean("ContactIsDeleted") || display == null || display.isBlank(); if (display == null || display.isBlank()) display = "Contact #" + rs.getInt("ContactId"); if (unavailable && !display.contains("unavailable")) display += " (unavailable)";
		return new CaseLinkShareDto(rs.getLong("CaseLinkShareId"), rs.getInt("ShaleClientId"), rs.getLong("CaseLinkId"), rs.getInt("ContactId"), display, unavailable, toLocalDateTime(rs.getTimestamp("SharedAt")), rs.getString("Notes"), rs.getBoolean("IsDeleted"), rs.getInt("CreatedByUserId"), toLocalDateTime(rs.getTimestamp("CreatedAt")), toLocalDateTime(rs.getTimestamp("UpdatedAt")), rs.getBytes("RowVer"));
	}

	private void validateActiveContactForTenant(Connection con, int tenant, int contactId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Contacts WHERE Id = ? AND ShaleClientId = ? AND IsDeleted = 0")) { ps.setInt(1, contactId); ps.setInt(2, tenant); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Contact is not available for this tenant."); } }
	}

	private void softDeleteCaseLinkShare(Connection con, int tenant, int actor, long caseLinkId, long shareId, byte[] rowVer) throws SQLException {
		String sql = """
			UPDATE dbo.CaseLinkShares SET IsDeleted = 1, DeletedAt = SYSUTCDATETIME(), DeletedByUserId = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
			WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0 AND RowVer = ?
			""";
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, actor); ps.setInt(2, actor); ps.setLong(3, shareId); ps.setInt(4, tenant); ps.setLong(5, caseLinkId); ps.setBytes(6, rowVer); if (ps.executeUpdate() != 1) throw new IllegalStateException("Optimistic conflict: case link share changed."); }
	}

	private long insertCaseLinkShare(Connection con, int tenant, int actor, long caseLinkId, int contactId, LocalDateTime sharedAt, String notes) throws SQLException {
		String sql = """
			INSERT INTO dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId, SharedAt, Notes, IsDeleted, CreatedByUserId, CreatedAt)
			VALUES (?, ?, ?, ?, ?, 0, ?, SYSUTCDATETIME())
			""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, tenant); ps.setLong(2, caseLinkId); ps.setInt(3, contactId); ps.setTimestamp(4, Timestamp.valueOf(sharedAt)); ps.setString(5, notes); ps.setInt(6, actor); ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
		}
		throw new RuntimeException("Failed to create case link share.");
	}

	private void updateCaseLinkShareRow(Connection con, int tenant, int actor, long caseLinkId, long shareId, int contactId, LocalDateTime sharedAt, String notes, byte[] rowVer) throws SQLException {
		requireRowVer(rowVer, "expectedRowVer");
		String sql = """
			UPDATE dbo.CaseLinkShares SET ContactId = ?, SharedAt = ?, Notes = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
			WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0 AND RowVer = ?
			""";
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, contactId); ps.setTimestamp(2, Timestamp.valueOf(sharedAt)); ps.setString(3, notes); ps.setInt(4, actor); ps.setLong(5, shareId); ps.setInt(6, tenant); ps.setLong(7, caseLinkId); ps.setBytes(8, rowVer); if (ps.executeUpdate() != 1) throw new IllegalStateException("Optimistic conflict: case link share changed."); }
	}

	private void validateShareDraftContacts(Connection con, int tenant, List<CaseLinkShareDraft> shares) throws SQLException {
		for (CaseLinkShareDraft share : shares == null ? List.<CaseLinkShareDraft>of() : shares) validateActiveContactForTenant(con, tenant, share.contactId());
	}

	private void validateShareUpdatesAndRemovals(Connection con, int tenant, long caseLinkId, List<CaseLinkShareUpdate> updates, List<CaseLinkShareRemoval> removals) throws SQLException {
		for (CaseLinkShareUpdate share : updates == null ? List.<CaseLinkShareUpdate>of() : updates) { validateActiveContactForTenant(con, tenant, share.contactId()); validateActiveShareForTenant(con, tenant, caseLinkId, share.caseLinkShareId()); }
		for (CaseLinkShareRemoval share : removals == null ? List.<CaseLinkShareRemoval>of() : removals) validateActiveShareForTenant(con, tenant, caseLinkId, share.caseLinkShareId());
	}

	private void validateActiveShareForTenant(Connection con, int tenant, long caseLinkId, long shareId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.CaseLinkShares WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0")) { ps.setLong(1, shareId); ps.setInt(2, tenant); ps.setLong(3, caseLinkId); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Case link share is not available for this tenant."); } }
	}

	private void softDeleteCaseLinkSharesForLink(Connection con, int tenant, int actor, long caseLinkId) throws SQLException {
		String sql = """
			UPDATE dbo.CaseLinkShares SET IsDeleted = 1, DeletedAt = SYSUTCDATETIME(), DeletedByUserId = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
			WHERE ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0
			""";
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, actor); ps.setInt(2, actor); ps.setInt(3, tenant); ps.setLong(4, caseLinkId); ps.executeUpdate(); }
	}

	private List<LinkTypeDto> mapLinkTypes(PreparedStatement ps) throws SQLException {
		try (ResultSet rs = ps.executeQuery()) {
			List<LinkTypeDto> out = new ArrayList<>();
			while (rs.next()) {
				out.add(mapLinkTypeDto(rs));
			}
			return out;
		}
	}

	private LinkTypeDto insertTenantLinkType(Connection con, int shaleClientId, int actorUserId, String name, String color,
			boolean active, String systemKey) throws SQLException {
		String normalizedName = normalizeRequired(name, "Name", 100);
		String normalizedColor = normalizeColor(color);
		String normalizedSystemKey = validateSystemKeyLength(systemKey);
		LinkTypeDto resetOverride = findTenantLinkTypeBySystemKey(con, shaleClientId, normalizedSystemKey);
		if (resetOverride != null && resetOverride.deleted()) {
			return updateTenantLinkType(con, shaleClientId, actorUserId, resetOverride.id(), normalizedName, normalizedColor,
					active, normalizedSystemKey, resetOverride.rowVer());
		}
		String sql = """
				INSERT INTO dbo.LinkTypes
					(ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, CreatedByUserId, UpdatedByUserId, CreatedAt, UpdatedAt)
				VALUES (?, ?, ?, ?, 0, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME())
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, shaleClientId);
			ps.setString(2, normalizedName);
			ps.setString(3, normalizedColor);
			ps.setBoolean(4, active);
			ps.setString(5, normalizedSystemKey);
			ps.setInt(6, actorUserId);
			ps.setInt(7, actorUserId);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return findLinkTypeById(con, keys.getInt(1));
				}
			}
		}
		throw new RuntimeException("Failed to create link type");
	}

	private LinkTypeDto customizeGlobalLinkType(Connection con, int shaleClientId, int actorUserId, LinkTypeDto global,
			String name, String color, boolean active, String systemKey) throws SQLException {
		String normalizedSystemKey = validateSystemKeyLength(systemKey == null ? global.systemKey() : normalizeSystemKey(systemKey));
		LinkTypeDto override = findTenantLinkTypeBySystemKey(con, shaleClientId, global.systemKey());
		if (override == null) {
			return insertTenantLinkType(con, shaleClientId, actorUserId, name, color, active, normalizedSystemKey);
		}
		return updateTenantLinkType(con, shaleClientId, actorUserId, override.id(), name, color, active,
				normalizedSystemKey, override.rowVer());
	}

	private LinkTypeDto updateTenantLinkType(Connection con, int shaleClientId, int actorUserId, int linkTypeId, String name,
			String color, boolean active, String systemKey, byte[] expectedRowVer) throws SQLException {
		requireRowVer(expectedRowVer, "expectedRowVer");
		String normalizedName = normalizeRequired(name, "Name", 100);
		String normalizedColor = normalizeColor(color);
		String normalizedSystemKey = validateSystemKeyLength(systemKey);
		String sql = """
				UPDATE dbo.LinkTypes
				SET Name = ?,
				    Color = ?,
				    IsActive = ?,
				    IsDeleted = 0,
				    SystemKey = ?,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, normalizedName);
			ps.setString(2, normalizedColor);
			ps.setBoolean(3, active);
			ps.setString(4, normalizedSystemKey);
			ps.setInt(5, actorUserId);
			ps.setInt(6, linkTypeId);
			ps.setInt(7, shaleClientId);
			ps.setBytes(8, expectedRowVer);
			if (ps.executeUpdate() != 1) {
				throw new IllegalStateException("Optimistic conflict: link type changed.");
			}
		}
		return findLinkTypeById(con, linkTypeId);
	}

	private void softDeleteTenantLinkType(Connection con, int shaleClientId, int actorUserId, int linkTypeId) throws SQLException {
		String sql = """
				UPDATE dbo.LinkTypes
				SET IsDeleted = 1,
				    IsActive = 0,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actorUserId);
			ps.setInt(2, linkTypeId);
			ps.setInt(3, shaleClientId);
			if (ps.executeUpdate() != 1) {
				throw new IllegalArgumentException("Link type override is not available for this tenant.");
			}
		}
	}

	private static LinkTypeDto mapLinkTypeDto(ResultSet rs) throws SQLException {
		return new LinkTypeDto(rs.getInt("Id"), getNullableInt(rs, "ShaleClientId"), rs.getString("Name"),
				rs.getString("Color"), rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"),
				normalizeSystemKey(rs.getString("SystemKey")), rs.getBytes("RowVer"));
	}

	private LinkTypeDto findLinkTypeById(Connection con, int id) throws SQLException {
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, RowVer
				FROM dbo.LinkTypes
				WHERE Id = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapLinkTypeDto(rs) : null;
			}
		}
	}

	private LinkTypeDto findTenantLinkTypeBySystemKey(Connection con, int tenant, String key) throws SQLException {
		String normalized = normalizeSystemKey(key);
		if (normalized == null) {
			return null;
		}
		String sql = """
				SELECT Id, ShaleClientId, Name, Color, IsActive, IsDeleted, SystemKey, RowVer
				FROM dbo.LinkTypes
				WHERE ShaleClientId = ?
				  AND SystemKey = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setString(2, normalized);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapLinkTypeDto(rs) : null;
			}
		}
	}

	private static String caseLinkSelect() {
		return """
				SELECT cl.Id AS CaseLinkId,
				       cl.ExternalLinkId,
				       cl.CaseId,
				       cl.ShaleClientId,
				       el.LinkTypeId,
				       lt.Name AS LinkTypeName,
				       lt.Color AS LinkTypeColor,
				       lt.SystemKey AS LinkTypeSystemKey,
				       el.DisplayName,
				       el.Url,
				       el.Description,
				       cl.IsPrimary,
				       cl.Notes,
				       cl.SortOrder,
				       cl.CreatedAt,
				       cl.UpdatedAt,
				       cl.RowVer AS CaseLinkRowVer,
				       el.RowVer AS ExternalLinkRowVer
				FROM dbo.CaseLinks cl
				JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId
				JOIN dbo.LinkTypes lt ON lt.Id = el.LinkTypeId
				""";
	}

	private static String activeCaseLinkWhere() {
		return """
				 WHERE cl.CaseId = ?
				   AND cl.ShaleClientId = ?
				   AND el.ShaleClientId = cl.ShaleClientId
				   AND cl.IsDeleted = 0
				   AND el.IsDeleted = 0
				   AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cl.ShaleClientId)
				""";
	}

	private static String caseLinkOrderBy() {
		return """
				 ORDER BY cl.IsPrimary DESC, cl.SortOrder ASC, LOWER(el.DisplayName), cl.Id
				""";
	}

	private List<CaseLinkDto> listCaseLinks(Connection con, long caseId, int shaleClientId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(caseLinkSelect() + activeCaseLinkWhere() + caseLinkOrderBy())) {
			ps.setLong(1, caseId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				List<CaseLinkDto> out = new ArrayList<>();
				while (rs.next()) out.add(mapCaseLinkDto(rs));
				Map<Long, List<CaseLinkShareDto>> shares = listCaseLinkSharesForLinks(con, shaleClientId, out.stream().map(CaseLinkDto::caseLinkId).toList());
				return out.stream().map(link -> withShares(link, shares.get(link.caseLinkId()))).toList();
			}
		}
	}

	private static CaseLinkDto mapCaseLinkDto(ResultSet rs) throws SQLException {
		return new CaseLinkDto(rs.getLong("CaseLinkId"), rs.getLong("ExternalLinkId"), rs.getLong("CaseId"),
				rs.getInt("ShaleClientId"), rs.getInt("LinkTypeId"), rs.getString("LinkTypeName"),
				rs.getString("LinkTypeColor"), normalizeSystemKey(rs.getString("LinkTypeSystemKey")),
				rs.getString("DisplayName"), rs.getString("Url"), rs.getString("Description"),
				rs.getBoolean("IsPrimary"), rs.getString("Notes"), rs.getInt("SortOrder"),
				toLocalDateTime(rs.getTimestamp("CreatedAt")), toLocalDateTime(rs.getTimestamp("UpdatedAt")),
				rs.getBytes("CaseLinkRowVer"), rs.getBytes("ExternalLinkRowVer"), List.of());
	}

	private CaseLinkDto findCaseLinkDto(Connection con, int tenant, long caseId, long id) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(caseLinkSelect() + activeCaseLinkWhere() + " AND cl.Id = ?")) {
			ps.setLong(1, caseId);
			ps.setInt(2, tenant);
			ps.setLong(3, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? withShares(mapCaseLinkDto(rs), listCaseLinkShares(con, caseId, id, tenant)) : null;
			}
		}
	}

	private void validateCaseForTenant(Connection con, int tenant, long caseId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Cases WHERE Id = ? AND ShaleClientId = ? AND IsDeleted = 0")) {
			ps.setLong(1, caseId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalArgumentException("Case is not available for this tenant.");
				}
			}
		}
	}

	private void validateActorForTenant(Connection con, int tenant, int userId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id = ? AND ShaleClientId = ? AND is_deleted = 0")) {
			ps.setInt(1, userId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalArgumentException("Actor user is not available for this tenant.");
				}
			}
		}
	}

	private void validateAdminActorForTenant(Connection con, int tenant, int userId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id = ? AND ShaleClientId = ? AND is_deleted = 0 AND is_admin = 1")) {
			ps.setInt(1, userId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalArgumentException("Actor user is not an active admin for this tenant.");
				}
			}
		}
	}

	private void validateActiveLinkTypeForTenant(Connection con, int tenant, int linkTypeId) throws SQLException {
		String sql = """
				SELECT 1
				FROM dbo.LinkTypes
				WHERE Id = ?
				  AND (ShaleClientId IS NULL OR ShaleClientId = ?)
				  AND IsActive = 1
				  AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, linkTypeId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalArgumentException("Link type is not active for this tenant.");
				}
			}
		}
	}

	private CaseLinkDto validateCaseLinkForTenant(Connection con, int tenant, long caseId, long caseLinkId, Long externalId)
			throws SQLException {
		CaseLinkDto dto = findCaseLinkDto(con, tenant, caseId, caseLinkId);
		if (dto == null || (externalId != null && dto.externalLinkId() != externalId)) {
			throw new IllegalArgumentException("Case link is not available for this tenant.");
		}
		return dto;
	}

	private boolean hasActiveCaseLinks(Connection con, int tenant, long caseId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.CaseLinks WHERE ShaleClientId = ? AND CaseId = ? AND IsDeleted = 0")) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private int nextSortOrder(Connection con, int tenant, long caseId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(SortOrder), -1) + 1 FROM dbo.CaseLinks WHERE ShaleClientId = ? AND CaseId = ? AND IsDeleted = 0")) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	private long insertExternalLink(Connection con, int tenant, int actor, int type, String name, String url, String desc)
			throws SQLException {
		String sql = """
				INSERT INTO dbo.ExternalLinks
					(ShaleClientId, LinkTypeId, DisplayName, Url, Description, IsDeleted, CreatedByUserId, UpdatedByUserId, CreatedAt, UpdatedAt)
				VALUES (?, ?, ?, ?, ?, 0, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME())
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, tenant);
			ps.setInt(2, type);
			ps.setString(3, name);
			ps.setString(4, url);
			ps.setString(5, desc);
			ps.setInt(6, actor);
			ps.setInt(7, actor);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private long insertCaseLink(Connection con, int tenant, int actor, long caseId, long externalId, boolean primary,
			String notes, int sort) throws SQLException {
		String sql = """
				INSERT INTO dbo.CaseLinks
					(ShaleClientId, CaseId, ExternalLinkId, IsPrimary, Notes, SortOrder, IsDeleted, CreatedByUserId, UpdatedByUserId, CreatedAt, UpdatedAt)
				VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME())
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			ps.setLong(3, externalId);
			ps.setBoolean(4, primary);
			ps.setString(5, notes);
			ps.setInt(6, sort);
			ps.setInt(7, actor);
			ps.setInt(8, actor);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private void setOnlyPrimary(Connection con, int tenant, long caseId, long caseLinkId, int actor) throws SQLException {
		String clearSql = """
				UPDATE dbo.CaseLinks
				SET IsPrimary = 0,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				  AND Id <> ?
				""";
		try (PreparedStatement ps = con.prepareStatement(clearSql)) {
			ps.setInt(1, actor);
			ps.setInt(2, tenant);
			ps.setLong(3, caseId);
			ps.setLong(4, caseLinkId);
			ps.executeUpdate();
		}
		String setSql = """
				UPDATE dbo.CaseLinks
				SET IsPrimary = 1,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(setSql)) {
			ps.setInt(1, actor);
			ps.setLong(2, caseLinkId);
			ps.setInt(3, tenant);
			ps.setLong(4, caseId);
			if (ps.executeUpdate() != 1) {
				throw new IllegalArgumentException("Case link is not available for this tenant.");
			}
		}
	}

	private void applyPrimaryUpdate(Connection con, int tenant, int actor, long caseId, long caseLinkId, boolean wasPrimary,
			Boolean requestedPrimary) throws SQLException {
		if (requestedPrimary == null) {
			return;
		}
		if (requestedPrimary) {
			setOnlyPrimary(con, tenant, caseId, caseLinkId, actor);
			return;
		}
		if (!wasPrimary) {
			return;
		}
		Long replacement = findNextPrimaryCandidate(con, tenant, caseId, caseLinkId);
		if (replacement == null) {
			setOnlyPrimary(con, tenant, caseId, caseLinkId, actor);
			return;
		}
		clearPrimary(con, tenant, caseId, caseLinkId, actor);
		setOnlyPrimary(con, tenant, caseId, replacement, actor);
	}

	private Long findNextPrimaryCandidate(Connection con, int tenant, long caseId, long excludedCaseLinkId) throws SQLException {
		String sql = """
				SELECT TOP (1) Id
				FROM dbo.CaseLinks
				WHERE ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				  AND Id <> ?
				ORDER BY SortOrder, Id
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			ps.setLong(3, excludedCaseLinkId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getLong(1) : null;
			}
		}
	}

	private void clearPrimary(Connection con, int tenant, long caseId, long caseLinkId, int actor) throws SQLException {
		String sql = """
				UPDATE dbo.CaseLinks
				SET IsPrimary = 0,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setLong(2, caseLinkId);
			ps.setInt(3, tenant);
			ps.setLong(4, caseId);
			if (ps.executeUpdate() != 1) {
				throw new IllegalArgumentException("Case link is not available for this tenant.");
			}
		}
	}

	private void updateExternalLinkRow(Connection con, int tenant, int actor, long id, int type, String name, String url,
			String desc, byte[] rowVer) throws SQLException {
		requireRowVer(rowVer, "expectedExternalLinkRowVer");
		String sql = """
				UPDATE dbo.ExternalLinks
				SET LinkTypeId = ?,
				    DisplayName = ?,
				    Url = ?,
				    Description = ?,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND IsDeleted = 0
				  AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, type);
			ps.setString(2, name);
			ps.setString(3, url);
			ps.setString(4, desc);
			ps.setInt(5, actor);
			ps.setLong(6, id);
			ps.setInt(7, tenant);
			ps.setBytes(8, rowVer);
			if (ps.executeUpdate() != 1) {
				throw new IllegalStateException("Optimistic conflict: external link changed.");
			}
		}
	}

	private void updateCaseLinkRow(Connection con, int tenant, int actor, long id, String notes, Integer sort,
			byte[] rowVer) throws SQLException {
		requireRowVer(rowVer, "expectedCaseLinkRowVer");
		String sql = """
				UPDATE dbo.CaseLinks
				SET Notes = ?,
				    SortOrder = COALESCE(?, SortOrder),
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND IsDeleted = 0
				  AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, notes);
			if (sort == null) {
				ps.setNull(2, java.sql.Types.INTEGER);
			} else {
				ps.setInt(2, sort);
			}
			ps.setInt(3, actor);
			ps.setLong(4, id);
			ps.setInt(5, tenant);
			ps.setBytes(6, rowVer);
			if (ps.executeUpdate() != 1) {
				throw new IllegalStateException("Optimistic conflict: case link changed.");
			}
		}
	}

	private void updateCaseLinkSortOrder(Connection con, int tenant, long caseId, int actor, long id, int sortOrder)
			throws SQLException {
		String sql = """
				UPDATE dbo.CaseLinks
				SET SortOrder = ?,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, sortOrder);
			ps.setInt(2, actor);
			ps.setLong(3, id);
			ps.setLong(4, caseId);
			ps.setInt(5, tenant);
			if (ps.executeUpdate() != 1) {
				throw new IllegalArgumentException("Case link is not available for this tenant.");
			}
		}
	}

	private void softDeleteCaseLink(Connection con, int tenant, int actor, long caseId, long caseLinkId, byte[] rowVer)
			throws SQLException {
		requireRowVer(rowVer, "expectedCaseLinkRowVer");
		String sql = """
				UPDATE dbo.CaseLinks
				SET IsDeleted = 1,
				    IsPrimary = 0,
				    DeletedAt = SYSUTCDATETIME(),
				    DeletedByUserId = ?,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND CaseId = ?
				  AND ShaleClientId = ?
				  AND IsDeleted = 0
				  AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setInt(2, actor);
			ps.setLong(3, caseLinkId);
			ps.setLong(4, caseId);
			ps.setInt(5, tenant);
			ps.setBytes(6, rowVer);
			if (ps.executeUpdate() != 1) {
				throw new IllegalStateException("Optimistic conflict: case link changed.");
			}
		}
	}

	private void selectNextPrimary(Connection con, int tenant, long caseId, int actor) throws SQLException {
		String sql = """
				SELECT TOP (1) Id
				FROM dbo.CaseLinks
				WHERE ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				ORDER BY SortOrder, Id
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					setOnlyPrimary(con, tenant, caseId, rs.getLong(1), actor);
				}
			}
		}
	}

	private void softDeleteExternalIfUnreferenced(Connection con, int tenant, long externalId, int actor) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.CaseLinks WHERE ShaleClientId = ? AND ExternalLinkId = ? AND IsDeleted = 0")) {
			ps.setInt(1, tenant);
			ps.setLong(2, externalId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return;
				}
			}
		}
		String sql = """
				UPDATE dbo.ExternalLinks
				SET IsDeleted = 1,
				    DeletedAt = SYSUTCDATETIME(),
				    DeletedByUserId = ?,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setInt(2, actor);
			ps.setLong(3, externalId);
			ps.setInt(4, tenant);
			ps.executeUpdate();
		}
	}

	private static void assertRowVerMatches(String tableName, int id, byte[] expectedRowVer, Connection con, String message)
			throws SQLException {
		String sql = "SELECT 1 FROM " + tableName + " WHERE Id = ? AND RowVer = ?";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, id);
			ps.setBytes(2, expectedRowVer);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Optimistic conflict: " + message);
				}
			}
		}
	}

	private static String topOne(String sql) {
		return sql.replaceFirst("SELECT", "SELECT TOP (1)");
	}

	private static void requireRowVer(byte[] rowVer, String label) {
		if (rowVer == null || rowVer.length == 0) {
			throw new IllegalArgumentException(label + " is required.");
		}
	}

	private static void validatePositive(long value, String name) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + " must be positive.");
		}
	}

	private static String normalizeRequired(String value, String name, int maxLength) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(name + " is required.");
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(name + " must be " + maxLength + " characters or fewer.");
		}
		return normalized;
	}

	private static String normalizeColor(String color) {
		String normalized = color == null ? null : color.trim();
		if (normalized != null && normalized.length() > 20) {
			throw new IllegalArgumentException("Color must be 20 characters or fewer.");
		}
		return normalized;
	}

	private static String validateSystemKeyLength(String systemKey) {
		String normalized = normalizeSystemKey(systemKey);
		if (normalized != null && normalized.length() > 64) {
			throw new IllegalArgumentException("System key must be 64 characters or fewer.");
		}
		return normalized;
	}

	public static RuntimeException translateSql(String operation, SQLException e) {
		if (isSqlServerUniqueViolation(e)) {
			String message = e.getMessage() == null ? "" : e.getMessage();
			if (message.contains("UX_LinkTypes_ShaleClientId_SystemKey_NonNull")) {
				return new IllegalArgumentException("Duplicate Link Type SystemKey within this scope.", e);
			}
			if (message.contains("UX_CaseLinks_CaseId_ExternalLinkId_Active")) {
				return new IllegalArgumentException("This external link is already associated with the case.", e);
			}
			if (message.contains("UX_CaseLinks_CaseId_Primary_Active")) {
				return new IllegalArgumentException("Only one active primary link is allowed for a case.", e);
			}
			if (message.contains("UX_CaseLinkShares_CaseLinkId_ContactId_Active")) {
				return new IllegalArgumentException("This contact is already shared on this link.", e);
			}
			return new IllegalArgumentException("A duplicate active value already exists.", e);
		}
		return new RuntimeException(operation + ".", e);
	}

	private static boolean isSqlServerUniqueViolation(SQLException e) {
		for (SQLException current = e; current != null; current = current.getNextException()) {
			if (current.getErrorCode() == 2601 || current.getErrorCode() == 2627) {
				return true;
			}
		}
		return false;
	}

}

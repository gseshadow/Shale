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
import java.util.Comparator;
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
import com.shale.core.dto.CaseSelectionOptionDto;
import com.shale.core.dto.CaseTimelineEventDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkContactOptionDto;
import com.shale.core.dto.CasePartyEntityOptionDto;
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
import com.shale.core.model.CaseDateSemanticRole;
import com.shale.core.semantics.RoleSemantics;
import com.shale.core.service.CaseServicePort.CaseLinkShareDraft;
import com.shale.core.service.CaseServicePort.CaseLinkShareUpdate;
import com.shale.core.service.CaseServicePort.CaseLinkShareRemoval;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactNamePresentation;

public final class CaseDao {

	private static final Logger LOG = Logger.getLogger(CaseDao.class.getName());
	private static final org.slf4j.Logger PERF_LOG = org.slf4j.LoggerFactory.getLogger(CaseDao.class);
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
		public static final String CASE_DELETED = "CASE_DELETED";
		public static final String CASE_RESTORED = "CASE_RESTORED";
		public static final String CASE_DATE_CREATED = "CASE_DATE_CREATED";
		public static final String CASE_DATE_UPDATED = "CASE_DATE_UPDATED";
		public static final String CASE_DATE_REMOVED = "CASE_DATE_REMOVED";
		public static final String CASE_DATE_RESTORED = "CASE_DATE_RESTORED";
		public static final String MATERIAL_REQUEST_CREATED = "MATERIAL_REQUEST_CREATED";
		public static final String MATERIAL_REQUEST_UPDATED = "MATERIAL_REQUEST_UPDATED";
		public static final String MATERIAL_REQUEST_STATUS_CHANGED = "MATERIAL_REQUEST_STATUS_CHANGED";
		public static final String MATERIAL_REQUEST_REMOVED = "MATERIAL_REQUEST_REMOVED";
		public static final String MATERIAL_REQUEST_NOTE_ADDED = "MATERIAL_REQUEST_NOTE_ADDED";
		public static final String CASE_LINK_CREATED = "CASE_LINK_CREATED";
		public static final String CASE_LINK_UPDATED = "CASE_LINK_UPDATED";
		public static final String CASE_LINK_REMOVED = "CASE_LINK_REMOVED";
		public static final String CASE_LINK_PRIMARY_CHANGED = "CASE_LINK_PRIMARY_CHANGED";
		public static final String CASE_LINKS_REORDERED = "CASE_LINKS_REORDERED";
		public static final String CASE_LINK_SHARE_ADDED = "CASE_LINK_SHARE_ADDED";
		public static final String CASE_LINK_SHARE_UPDATED = "CASE_LINK_SHARE_UPDATED";
		public static final String CASE_LINK_SHARE_REMOVED = "CASE_LINK_SHARE_REMOVED";
		public static final String NON_ENGAGEMENT_LETTER_SENT_CHANGED = "NON_ENGAGEMENT_LETTER_SENT_CHANGED";

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
				RECEIVED_UPDATES_CHANGED,
				CASE_DELETED,
				CASE_RESTORED
				, CASE_DATE_CREATED, CASE_DATE_UPDATED, CASE_DATE_REMOVED, CASE_DATE_RESTORED
				, MATERIAL_REQUEST_CREATED, MATERIAL_REQUEST_UPDATED, MATERIAL_REQUEST_STATUS_CHANGED,
				MATERIAL_REQUEST_REMOVED, MATERIAL_REQUEST_NOTE_ADDED
				, CASE_LINK_CREATED, CASE_LINK_UPDATED, CASE_LINK_REMOVED, CASE_LINK_PRIMARY_CHANGED,
				CASE_LINKS_REORDERED, CASE_LINK_SHARE_ADDED, CASE_LINK_SHARE_UPDATED, CASE_LINK_SHARE_REMOVED,
				NON_ENGAGEMENT_LETTER_SENT_CHANGED
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
	private final EntityActionAuditDao entityActionAuditDao = new EntityActionAuditDao();

	public CaseDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
		this.phiAuditService = new PhiAuditService(new AuditLogDao(this.db));
	}

	public DbSessionProvider dbSessionProvider() {
		return db;
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
			String name,
			String systemKey
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
			Integer createdByUserId,
			long formConfigurationId,
			byte[] formConfigurationRowVer,
			List<ConfiguredDateValue> configuredDates
	) {
		public NewIntakeCreateRequest {
			formConfigurationRowVer = formConfigurationRowVer == null ? null : formConfigurationRowVer.clone();
			configuredDates = configuredDates == null ? List.of() : List.copyOf(configuredDates);
		}

		@Override
		public byte[] formConfigurationRowVer() {
			return formConfigurationRowVer == null ? null : formConfigurationRowVer.clone();
		}
	}

	public record ConfiguredDateValue(String fieldKey, int caseDateTypeId, boolean required, LocalDate value) {
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

	public record NewIntakeCreateResult(long caseId, int clientContactId, int callerContactId,
			int createdCaseDateCount) {
	}

	public record IntakeDuplicateCase(long caseId, String caseName, String caseNumber,
			String status, String clientName, LocalDate intakeDate) { }

	/** A deliberately narrow, tenant-scoped exact-name check used only by New Intake. */
	public List<IntakeDuplicateCase> findIntakeDuplicateCases(int shaleClientId, String caseName) {
		if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId is required.");
		String normalized = normalizeCaseName(caseName);
		if (normalized == null) return List.of();
		String sql = """
			SELECT c.Id,c.Name,c.CaseNumber,
			 status_row.Name AS StatusName, client_row.ClientName, intake_row.IntakeDate
			FROM dbo.Cases c
			OUTER APPLY (SELECT TOP(1) s.Name FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId
			 WHERE cs.CaseId=c.Id ORDER BY CASE WHEN cs.IsPrimary=1 THEN 0 ELSE 1 END,cs.UpdatedAt DESC,cs.Id DESC) status_row
			OUTER APPLY (SELECT TOP(1) LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))) ClientName
			 FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId JOIN dbo.Contacts ct ON ct.Id=cp.ContactId
			 WHERE cp.CaseId=c.Id AND ct.ShaleClientId=c.ShaleClientId AND ISNULL(ct.IsDeleted,0)=0
			 AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey,pr.Name))))='party'
			 AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side,''))))='represented'
			 ORDER BY CASE WHEN ISNULL(cp.IsPrimary,0)=1 THEN 0 ELSE 1 END,cp.Id) client_row
			OUTER APPLY (SELECT TOP(1) CAST(cd.StartsAt AS date) IntakeDate FROM dbo.CaseDates cd
			 JOIN dbo.CaseDateTypes dt ON dt.Id=cd.CaseDateTypeId
			 JOIN dbo.CaseDateTypeSemanticRoleMappings rm ON rm.CaseDateTypeId=dt.Id AND rm.SemanticRoleKey='INTAKE'
			 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0 AND rm.IsActive=1 AND rm.IsDeleted=0
			 ORDER BY cd.StartsAt,cd.Id) intake_row
			WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
			 AND LOWER(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(c.Name)),'  ',' '),'  ',' '),'  ',' '))=?
			ORDER BY c.Id
			""";
		try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId); ps.setString(2, normalized);
			try (ResultSet rs=ps.executeQuery()) { List<IntakeDuplicateCase> out=new ArrayList<>(); while(rs.next()) out.add(new IntakeDuplicateCase(
				rs.getLong("Id"),rs.getString("Name"),rs.getString("CaseNumber"),rs.getString("StatusName"),
				rs.getString("ClientName"),toLocalDate(rs.getDate("IntakeDate")))); return List.copyOf(out); }
		} catch (SQLException e) { throw new RuntimeException("Failed to check for duplicate cases.",e); }
	}

	static String normalizeCaseName(String value) {
		String normalized=normalizeOptional(value);
		return normalized==null?null:normalized.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
	}

	public NewIntakeCreateResult mergeIntake(long existingCaseId, NewIntakeCreateRequest request) {
		Objects.requireNonNull(request,"request");
		Connection con=null;
		try {
			con=db.requireConnection(); con.setAutoCommit(false);
			List<ConfiguredDateValue> dates=validateConfiguredIntakeDates(con,request);
			int intakeTypeId=requireConfiguredIntakeValue(con,request,dates);
			if (!lockMatchingCase(con,existingCaseId,request)) throw new IllegalArgumentException("The selected case is no longer an eligible duplicate.");
			Timestamp now=Timestamp.valueOf(LocalDateTime.now());
			ensureRequiredPartyRolesForTenant(con,request.shaleClientId());
			fillBlankCaseScalars(con,existingCaseId,request);
			int clientId=mergeRoleContact(con,existingCaseId,request,PARTY_ROLE_NAME_PARTY,request.clientFirstName(),request.clientLastName(),
				request.clientDateOfBirth(),request.clientCondition(),request.clientDeceased(),true,request.clientPhone(),request.clientEmail(),request.clientAddress(),now);
			int callerId=request.callerIsClient()?clientId:mergeRoleContact(con,existingCaseId,request,PARTY_ROLE_NAME_CALLER,
				request.callerFirstName(),request.callerLastName(),null,null,false,false,request.callerPhone(),request.callerEmail(),request.callerAddress(),now);
			ensureCaseParty(con,existingCaseId,clientId,PARTY_ROLE_NAME_PARTY,now,request.shaleClientId());
			ensureCaseParty(con,existingCaseId,callerId,PARTY_ROLE_NAME_CALLER,now,request.shaleClientId());
			for(NewIntakePendingParty pending:request.pendingParties()==null?List.<NewIntakePendingParty>of():request.pendingParties()) addPendingPartyForMerge(con,existingCaseId,request,pending,now);
			normalizeCasePartyRelationshipPrimaries(con,existingCaseId,request.shaleClientId());
			int createdDates=0; for(ConfiguredDateValue date:dates) if(!hasActiveCaseDate(con,existingCaseId,date.caseDateTypeId(),request.shaleClientId())) {
				long id=insertConfiguredCaseDate(con,request,existingCaseId,date,intakeTypeId); auditCreatedCaseDate(con,request,existingCaseId,id,date,intakeTypeId); createdDates++; }
			con.commit(); return new NewIntakeCreateResult(existingCaseId,clientId,callerId,createdDates);
		} catch(Exception e) { if(con!=null)try{con.rollback();}catch(SQLException ignored){} throw e instanceof RuntimeException r?r:new RuntimeException("Failed to merge intake.",e); }
		finally { if(con!=null){try{con.setAutoCommit(true);}catch(SQLException ignored){} try{con.close();}catch(SQLException ignored){}} }
	}

	private void addPendingPartyForMerge(Connection con,long caseId,NewIntakeCreateRequest request,NewIntakePendingParty pending,Timestamp now)throws SQLException{
		if(pending==null||pending.partyRoleId()==null||pending.partyRoleId()<=0)return;String type=pending.entityType()==null?"":pending.entityType().trim().toLowerCase(Locale.ROOT);Long entity=pending.entityId();
		if(pending.createNew()){if("contact".equals(type))entity=(long)insertContact(con,buildFullName(pending.contactFirstName(),pending.contactLastName()),pending.contactFirstName(),pending.contactLastName(),null,null,false,false,request.shaleClientId(),now);else if("organization".equals(type))entity=(long)insertOrganization(con,request.shaleClientId(),pending.organizationTypeId(),pending.organizationName(),now);}
		if(entity==null||entity<=0)return;insertCasePartyWithValidation(con,caseId,"contact".equals(type)?entity:null,"organization".equals(type)?entity:null,pending.partyRoleId(),pending.side(),pending.primary(),pending.notes(),request.shaleClientId(),now);
	}

	private static boolean lockMatchingCase(Connection con,long id,NewIntakeCreateRequest r)throws SQLException{
		try(PreparedStatement ps=con.prepareStatement("SELECT Name FROM dbo.Cases WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){
			ps.setLong(1,id);ps.setInt(2,r.shaleClientId());try(ResultSet rs=ps.executeQuery()){return rs.next()&&Objects.equals(normalizeCaseName(rs.getString(1)),normalizeCaseName(r.caseName()));}}
	}

	private void fillBlankCaseScalars(Connection con,long id,NewIntakeCreateRequest r)throws SQLException{
		String oldDescription=null,oldSummary=null;try(PreparedStatement read=con.prepareStatement("SELECT Description,Summary FROM dbo.Cases WHERE Id=? AND ShaleClientId=?")){read.setLong(1,id);read.setInt(2,r.shaleClientId());try(ResultSet rs=read.executeQuery()){if(!rs.next())throw new SQLException("Selected case changed during merge.");oldDescription=rs.getString(1);oldSummary=rs.getString(2);}}
		try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Cases SET Description=CASE WHEN NULLIF(LTRIM(RTRIM(Description)),'') IS NULL THEN ? ELSE Description END, Summary=CASE WHEN NULLIF(LTRIM(RTRIM(Summary)),'') IS NULL THEN ? ELSE Summary END, UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){
			setNullableString(ps,1,r.description());setNullableString(ps,2,r.summary());ps.setLong(3,id);ps.setInt(4,r.shaleClientId());if(ps.executeUpdate()!=1)throw new SQLException("Selected case changed during merge.");}
		if(r.createdByUserId()!=null){ if(normalizeOptional(oldDescription)==null&&normalizeOptional(r.description())!=null)phiAuditService.auditUpdate(con,r.createdByUserId(),"Cases","Description",id,oldDescription,r.description()); if(normalizeOptional(oldSummary)==null&&normalizeOptional(r.summary())!=null)phiAuditService.auditUpdate(con,r.createdByUserId(),"Cases","Summary",id,oldSummary,r.summary()); }
	}

	private int mergeRoleContact(Connection con,long caseId,NewIntakeCreateRequest r,String role,String first,String last,LocalDate dob,String condition,boolean deceased,boolean client,String phone,String email,String address,Timestamp now)throws SQLException{
		List<Integer> ids=new ArrayList<>(); try(PreparedStatement ps=con.prepareStatement("SELECT cp.ContactId FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId JOIN dbo.Contacts ct ON ct.Id=cp.ContactId WHERE cp.CaseId=? AND ct.ShaleClientId=? AND ISNULL(ct.IsDeleted,0)=0 AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey,pr.Name))))=? ORDER BY CASE WHEN ISNULL(cp.IsPrimary,0)=1 THEN 0 ELSE 1 END,cp.Id")){
			ps.setLong(1,caseId);ps.setInt(2,r.shaleClientId());ps.setString(3,role);try(ResultSet rs=ps.executeQuery()){while(rs.next())ids.add(rs.getInt(1));}}
		int id;if(ids.size()==1){id=ids.getFirst();fillBlankContactScalars(con,id,r,first,last,dob,condition);insertMissingContactPoints(con,r,id,phone,email,address);}else{id=insertContact(con,buildFullName(first,last),first,last,dob,condition,deceased,client,r.shaleClientId(),now);insertIntakeContactPoints(con,r,id,phone,email,address);} return id;
	}

	private void fillBlankContactScalars(Connection con,int id,NewIntakeCreateRequest r,String first,String last,LocalDate dob,String condition)throws SQLException{
		try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Contacts SET FirstName=COALESCE(NULLIF(LTRIM(RTRIM(FirstName)),''),?),LastName=COALESCE(NULLIF(LTRIM(RTRIM(LastName)),''),?),Name=COALESCE(NULLIF(LTRIM(RTRIM(Name)),''),?),DateOfBirth=COALESCE(DateOfBirth,?),Condition=COALESCE(NULLIF(LTRIM(RTRIM(Condition)),''),?),UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){
			setNullableString(ps,1,first);setNullableString(ps,2,last);setNullableString(ps,3,buildFullName(first,last));setNullableDate(ps,4,dob);setNullableString(ps,5,condition);ps.setInt(6,id);ps.setInt(7,r.shaleClientId());if(ps.executeUpdate()!=1)throw new SQLException("Contact changed during merge.");}
		if(r.createdByUserId()!=null){entityActionAuditDao.append(con,EntityActionAuditEvent.now(r.shaleClientId(),r.createdByUserId(),EntityActionAuditEvent.EntityType.CONTACT,id,EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID,id)));if(normalizeOptional(condition)!=null)phiAuditService.auditUpdate(con,r.createdByUserId(),"Contacts","Condition",(long)id,null,condition);}
	}

	private void insertMissingContactPoints(Connection con,NewIntakeCreateRequest r,int id,String phone,String email,String address)throws SQLException{
		if(!hasContactPoint(con,"ContactPhoneNumbers",id,"NormalizedNumber",normalizePhone(phone)))insertIntakeContactPoint(con,r,id,"ContactPhoneNumbers","DisplayNumber,NormalizedNumber","MOBILE",normalizeOptional(phone),normalizePhone(phone),EntityActionAuditEvent.EntityType.CONTACT_PHONE_NUMBER);
		if(!hasContactPoint(con,"ContactEmailAddresses",id,"NormalizedEmail",normalizeEmail(email)))insertIntakeContactPoint(con,r,id,"ContactEmailAddresses","EmailAddress,NormalizedEmail","PERSONAL",normalizeOptional(email),normalizeEmail(email),EntityActionAuditEvent.EntityType.CONTACT_EMAIL_ADDRESS);
		if(!hasContactPoint(con,"ContactAddresses",id,"LegacyAddressText",normalizeOptional(address)))insertIntakeContactPoint(con,r,id,"ContactAddresses","LegacyAddressText","HOME",normalizeOptional(address),EntityActionAuditEvent.EntityType.CONTACT_ADDRESS);
	}
	private static boolean hasContactPoint(Connection con,String table,int id,String column,String value)throws SQLException{if(value==null)return true;String expression=column.equals("LegacyAddressText")?"LOWER(LTRIM(RTRIM("+column+")))":"LOWER("+column+")";try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo."+table+" WHERE ContactId=? AND ISNULL(IsDeleted,0)=0 AND "+expression+"=?")){ps.setInt(1,id);ps.setString(2,value.toLowerCase(Locale.ROOT));try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
	private static boolean hasActiveCaseDate(Connection con,long caseId,int type,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.CaseDates WHERE CaseId=? AND CaseDateTypeId=? AND ShaleClientId=? AND IsDeleted=0")){ps.setLong(1,caseId);ps.setInt(2,type);ps.setInt(3,tenant);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
	private void ensureCaseParty(Connection con,long caseId,int contact,String role,Timestamp now,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId WHERE cp.CaseId=? AND cp.ContactId=? AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey,pr.Name))))=?")){ps.setLong(1,caseId);ps.setInt(2,contact);ps.setString(3,role);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}insertCaseParty(con,caseId,contact,role,PARTY_SIDE_KEY_REPRESENTED,true,now,tenant);}

	public NewIntakeCreateResult createIntake(NewIntakeCreateRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.shaleClientId() <= 0)
			throw new IllegalArgumentException("shaleClientId is required.");

		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		Connection con = null;
		try {
			con = db.requireConnection();
			con.setAutoCommit(false);
			List<ConfiguredDateValue> configuredDates = validateConfiguredIntakeDates(con, request);
			int intakeTypeId = requireConfiguredIntakeValue(con, request, configuredDates);
			System.out.println("[IntakeCreate] start shaleClientId=" + request.shaleClientId()
					+ " caseName='" + safeLogValue(request.caseName()) + "'");
			ensureRequiredPartyRolesForTenant(con, request.shaleClientId());
			System.out.println("[IntakeCreate] required party roles verified for shaleClientId=" + request.shaleClientId());

			int clientContactId = insertContact(con,
					buildFullName(request.clientFirstName(), request.clientLastName()),
					request.clientFirstName(),
					request.clientLastName(),
					request.clientDateOfBirth(),
					request.clientCondition(),
					request.clientDeceased(),
					true,
					request.shaleClientId(),
					now);
			insertIntakeContactPoints(con, request, clientContactId, request.clientPhone(),
					request.clientEmail(), request.clientAddress());
			if (request.createdByUserId() != null && normalizeOptional(request.clientCondition()) != null) {
				phiAuditService.auditUpdate(con, request.createdByUserId(), "Contacts", "Condition",
						(long) clientContactId, null, normalizeOptional(request.clientCondition()));
			}

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
			for (ConfiguredDateValue date : configuredDates) {
				long caseDateId = insertConfiguredCaseDate(con, request, caseId, date, intakeTypeId);
				auditCreatedCaseDate(con, request, caseId, caseDateId, date, intakeTypeId);
			}
			System.out.println("[IntakeCreate] primary status linked caseId=" + caseId + " statusId=" + request.statusId());

			con.commit();
			System.out.println("[IntakeCreate] committed caseId=" + caseId + " shaleClientId=" + request.shaleClientId());
			return new NewIntakeCreateResult(caseId, clientContactId, callerContactId, configuredDates.size());
		} catch (Exception e) {
			System.err.println("[IntakeCreate] failed shaleClientId=" + request.shaleClientId() + " error=" + e.getMessage());
			e.printStackTrace(System.err);
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			if (e instanceof IntakeConfigurationException configured)
				throw configured;
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

	public static final class IntakeConfigurationException extends RuntimeException {
		public IntakeConfigurationException(String message) {
			super(message);
		}
	}

	private List<ConfiguredDateValue> validateConfiguredIntakeDates(Connection con, NewIntakeCreateRequest request) throws SQLException {
		long currentId = 0;
		byte[] currentRowVer = null;
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT Id,RowVer FROM dbo.FormConfigurations WITH (UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND FormKey='NEW_INTAKE' AND IsDeleted=0")) {
			ps.setInt(1, request.shaleClientId());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					currentId = rs.getLong(1);
					currentRowVer = rs.getBytes(2);
				}
			}
		}
		byte[] submittedRowVer = request.formConfigurationRowVer();
		if (currentId != request.formConfigurationId() || !java.util.Arrays.equals(currentRowVer, submittedRowVer))
			throw new IntakeConfigurationException("The intake form configuration changed. Reload the form before submitting again.");
		LinkedHashMap<String, ConfiguredDateValue> submitted = new LinkedHashMap<>();
		for (ConfiguredDateValue value : request.configuredDates()) {
			if (value == null || value.fieldKey() == null || submitted.putIfAbsent(value.fieldKey(), value) != null)
				throw invalidConfiguredDates();
		}
		if (currentId == 0) {
			List<ConfiguredDateValue> result = new ArrayList<>();
			Set<Integer> typeIds = new HashSet<>();
			for (ConfiguredDateValue value : submitted.values()) {
				if (value.caseDateTypeId() <= 0 || !fieldKeyForCaseDateType(value.caseDateTypeId()).equals(value.fieldKey())
						|| value.required() || !typeIds.add(value.caseDateTypeId())) throw invalidConfiguredDates();
				validateEffectiveConfiguredDateType(con, request.shaleClientId(), value.caseDateTypeId());
				if (value.value() != null) result.add(value);
			}
			return List.copyOf(result);
		}
		LinkedHashMap<String, ConfiguredDateValue> authoritative = new LinkedHashMap<>();
		String sql = """
				SELECT f.FieldKey,f.CaseDateTypeId,f.IsRequired
				FROM dbo.FormConfigurationSections s
				JOIN dbo.FormConfiguredFields f ON f.FormConfigurationSectionId=s.Id AND f.ShaleClientId=s.ShaleClientId
				WHERE s.FormConfigurationId=? AND s.ShaleClientId=? AND s.SectionKey='dates' AND s.IsEnabled=1 AND s.IsVisible=1
				  AND f.FieldKind='CASE_DATE' AND f.IsEnabled=1 AND f.IsVisible=1
				ORDER BY s.SortOrder,s.Id,f.SortOrder,f.Id
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, currentId);
			ps.setInt(2, request.shaleClientId());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String key = rs.getString(1);
					int type = rs.getInt(2);
					boolean required = rs.getBoolean(3);
					if (authoritative.putIfAbsent(key, new ConfiguredDateValue(key, type, required, null)) != null)
						throw invalidConfiguredDates();
				}
			}
		}
		if (!submitted.keySet().equals(authoritative.keySet()))
			throw invalidConfiguredDates();
		Set<Integer> typeIds = new HashSet<>();
		List<ConfiguredDateValue> result = new ArrayList<>();
		for (var entry : authoritative.entrySet()) {
			ConfiguredDateValue expected = entry.getValue(), actual = submitted.get(entry.getKey());
			if (actual.caseDateTypeId() != expected.caseDateTypeId() || actual.required() != expected.required() || !typeIds.add(actual.caseDateTypeId()))
				throw invalidConfiguredDates();
			validateEffectiveConfiguredDateType(con, request.shaleClientId(), actual.caseDateTypeId());
			if (expected.required() && actual.value() == null)
				throw new IntakeConfigurationException("Complete all required configured date fields.");
			if (actual.value() != null)
				result.add(actual);
		}
		return List.copyOf(result);
	}

	private static void validateEffectiveConfiguredDateType(Connection con, int tenant, int typeId) throws SQLException {
		String sql = """
				WITH visible AS (
				    SELECT
				        t.Id,
				        t.IsActive,
				        t.IsDeleted,
				        ROW_NUMBER() OVER (
				            PARTITION BY t.SystemKey
				            ORDER BY
				                CASE
				                    WHEN t.ShaleClientId = ?
				                         AND t.IsDeleted = 0 THEN 0
				                    ELSE 1
				                END,
				                t.Id
				        ) AS rn
				    FROM dbo.CaseDateTypes t
				    WHERE (t.ShaleClientId = ? OR t.ShaleClientId IS NULL)
				      AND t.SystemKey IS NOT NULL
				),
				effective AS (
				    SELECT Id
				    FROM visible
				    WHERE rn = 1
				      AND IsActive = 1
				      AND IsDeleted = 0

				    UNION ALL

				    SELECT Id
				    FROM dbo.CaseDateTypes
				    WHERE ShaleClientId = ?
				      AND SystemKey IS NULL
				      AND IsActive = 1
				      AND IsDeleted = 0
				)
				SELECT 1
				FROM effective
				WHERE Id = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setInt(2, tenant);
			ps.setInt(3, tenant);
			ps.setInt(4, typeId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw invalidConfiguredDates();
			}
		}
	}

	private static String fieldKeyForCaseDateType(int typeId) { return "case_date:" + typeId; }

	private static IntakeConfigurationException invalidConfiguredDates() {
		return new IntakeConfigurationException("The configured date fields are no longer valid. Reload the form before submitting again.");
	}

	private static int requireConfiguredIntakeValue(Connection con, NewIntakeCreateRequest request,
			List<ConfiguredDateValue> configuredDates) throws SQLException {
		if (request.createdByUserId() == null || request.createdByUserId() <= 0)
			throw new IntakeConfigurationException("An authenticated intake user is required.");
		int intakeTypeId = CaseDateSemanticRoleResolver.requireEffectiveTypeId(
				con, request.shaleClientId(), CaseDateSemanticRole.INTAKE);
		ConfiguredDateValue intake = configuredDates.stream()
				.filter(value -> value.caseDateTypeId() == intakeTypeId)
				.findFirst()
				.orElseThrow(() -> new IntakeConfigurationException(
						"The Intake date is required. Reload New Intake before submitting again."));
		if (!Objects.equals(intake.value(), request.intakeDate()))
			throw new IntakeConfigurationException("The Intake date changed. Reload New Intake before submitting again.");
		if (request.intakeTime() == null)
			throw new IntakeConfigurationException("The Intake time is required.");
		return intakeTypeId;
	}

	private static long insertConfiguredCaseDate(Connection con, NewIntakeCreateRequest request, long caseId,
			ConfiguredDateValue value, int intakeTypeId) throws SQLException {
		boolean intake = value.caseDateTypeId() == intakeTypeId;
		LocalDateTime startsAt = intake
				? LocalDateTime.of(value.value(), request.intakeTime())
				: value.value().atStartOfDay();
		try (PreparedStatement ps = con.prepareStatement(
				"INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,CreatedAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,?, ?,NULL,?,SYSUTCDATETIME(),?)")) {
			ps.setInt(1, request.shaleClientId());
			ps.setLong(2, caseId);
			ps.setInt(3, value.caseDateTypeId());
			ps.setTimestamp(4, Timestamp.valueOf(startsAt));
			ps.setBoolean(5, !intake);
			ps.setInt(6, request.createdByUserId());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw new IllegalStateException("Case date was not created.");
				return rs.getLong(1);
			}
		}
	}

	private void auditCreatedCaseDate(Connection con, NewIntakeCreateRequest request, long caseId,
			long caseDateId, ConfiguredDateValue value, int intakeTypeId) throws SQLException {
		LocalDateTime startsAt = value.caseDateTypeId() == intakeTypeId
				? LocalDateTime.of(value.value(), request.intakeTime()) : value.value().atStartOfDay();
		entityActionAuditDao.append(con, EntityActionAuditEvent.now(request.shaleClientId(),
				request.createdByUserId(), EntityActionAuditEvent.EntityType.CASE_DATE, caseDateId,
				EntityActionAuditEvent.Action.CREATED, EntityActionAuditEvent.EntityType.CASE, caseId,
				Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, caseId,
						EntityActionAuditEvent.MetadataKey.CASE_DATE_ID, caseDateId)));
		phiAuditService.auditCreate(con, request.createdByUserId(), "CaseDates", "StartsAt", caseDateId, startsAt);
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
				null,
				null,
				false,
				false,
				request.shaleClientId(),
				now, request);
	}

	private int insertContact(Connection con, String name, String firstName, String lastName,
			LocalDate dateOfBirth, String condition, boolean isDeceased, boolean isClient,
			int shaleClientId, Timestamp now, NewIntakeCreateRequest request) throws SQLException {
		int contactId = insertContact(con, name, firstName, lastName, dateOfBirth, condition,
				isDeceased, isClient, shaleClientId, now);
		insertIntakeContactPoints(con, request, contactId, request.callerPhone(), request.callerEmail(),
				request.callerAddress());
		return contactId;
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
				VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?);
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			setNullableString(ps, i++, name);
			setNullableString(ps, i++, firstName);
			setNullableString(ps, i++, lastName);
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

	private void insertIntakeContactPoints(Connection con, NewIntakeCreateRequest request, int contactId,
			String phone, String email, String address) throws SQLException {
		insertIntakeContactPoint(con, request, contactId, "ContactPhoneNumbers",
				"DisplayNumber,NormalizedNumber", "MOBILE", normalizeOptional(phone), normalizePhone(phone),
				EntityActionAuditEvent.EntityType.CONTACT_PHONE_NUMBER);
		insertIntakeContactPoint(con, request, contactId, "ContactEmailAddresses",
				"EmailAddress,NormalizedEmail", "PERSONAL", normalizeOptional(email), normalizeEmail(email),
				EntityActionAuditEvent.EntityType.CONTACT_EMAIL_ADDRESS);
		insertIntakeContactPoint(con, request, contactId, "ContactAddresses",
				"LegacyAddressText", "HOME", normalizeOptional(address),
				EntityActionAuditEvent.EntityType.CONTACT_ADDRESS);
	}

	private void insertIntakeContactPoint(Connection con, NewIntakeCreateRequest request, int contactId,
			String table, String valueColumns, String kind, String value,
			EntityActionAuditEvent.EntityType entityType) throws SQLException {
		insertIntakeContactPoint(con, request, contactId, table, valueColumns, kind, value, null, entityType);
	}

	private void insertIntakeContactPoint(Connection con, NewIntakeCreateRequest request, int contactId,
			String table, String valueColumns, String kind, String firstValue, String secondValue,
			EntityActionAuditEvent.EntityType entityType) throws SQLException {
		if (firstValue == null) return;
		String placeholders = secondValue == null ? "?" : "?,?";
		String sql = "INSERT dbo." + table + " (ShaleClientId,ContactId,Kind," + valueColumns
				+ ",IsPrimary,SortOrder,CreatedByUserId) OUTPUT INSERTED.Id VALUES (?,?,?," + placeholders + ",1,0,?)";
		long pointId;
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			ps.setInt(i++, request.shaleClientId());
			ps.setInt(i++, contactId);
			ps.setString(i++, kind);
			ps.setString(i++, firstValue);
			if (secondValue != null) ps.setString(i++, secondValue);
			if (request.createdByUserId() == null) ps.setNull(i, java.sql.Types.INTEGER);
			else ps.setInt(i, request.createdByUserId());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw new SQLException("Failed to create structured Contact information.");
				pointId = rs.getLong(1);
			}
		}
		if (request.createdByUserId() != null) {
			entityActionAuditDao.append(con, EntityActionAuditEvent.now(request.shaleClientId(),
					request.createdByUserId(), entityType, pointId, EntityActionAuditEvent.Action.CREATED,
					EntityActionAuditEvent.EntityType.CONTACT, (long) contactId,
					Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID, contactId,
							EntityActionAuditEvent.MetadataKey.KIND, kind,
							EntityActionAuditEvent.MetadataKey.PRIMARY, true)));
		}
	}

	private static String normalizeOptional(String value) {
		if (value == null || value.trim().isEmpty()) return null;
		return value.trim();
	}

	private static String normalizePhone(String value) {
		String normalized = normalizeOptional(value);
		return normalized == null ? null : normalized.replaceAll("[^0-9+]", "");
	}

	private static String normalizeEmail(String value) {
		String normalized = normalizeOptional(value);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private long insertCase(Connection con, NewIntakeCreateRequest request, Timestamp now) throws SQLException {
		validatePracticeAreaForTenant(con, request.shaleClientId(), request.practiceAreaId());
		validateIntakeUserForTenant(con, request.shaleClientId(), request.createdByUserId());
		return insertConfiguredIntakeCase(con, request, now);
	}

	/** Configured intake deliberately omits every migrated legacy date column. */
	private static long insertConfiguredIntakeCase(Connection con, NewIntakeCreateRequest request,
			Timestamp now) throws SQLException {
		String sql = """
				INSERT INTO dbo.Cases (
				  Name, PracticeAreaId, ClientEstate, Description, Summary,
				  FollowUpMeetWithClient, FollowUpNurseReview, FollowUpExpertReview,
				  FollowUpCaseTransferred, AcceptedChronology, AcceptedConsultantExpertSearch,
				  AcceptedTestifyingExpertSearch, AcceptedMedicalLiterature, DeniedChronology,
				  FeeAgreementSigned, MedicalRecordsRequested, IsDeleted,
				  CreatedAt, UpdatedAt, ShaleClientId, IntakeTakenByUserId
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?, ?);
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = 1;
			setNullableString(ps, i++, request.caseName());
			ps.setInt(i++, request.practiceAreaId());
			ps.setBoolean(i++, request.estateCase());
			setNullableString(ps, i++, request.description());
			setNullableString(ps, i++, request.summary());
			ps.setTimestamp(i++, now);
			ps.setTimestamp(i++, now);
			ps.setInt(i++, request.shaleClientId());
			if (request.createdByUserId() == null) ps.setNull(i, java.sql.Types.INTEGER);
			else ps.setInt(i, request.createdByUserId());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw new RuntimeException("Failed to create case.");
				return rs.getLong(1);
			}
		}
	}

	private void validateIntakeUserForTenant(Connection con, int shaleClientId, Integer userId) throws SQLException {
		if (userId == null) {
			return;
		}
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM dbo.Users WHERE Id = ? AND ShaleClientId = ?")) {
			ps.setInt(1, userId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next())
					return;
			}
		}
		throw new IllegalArgumentException("Intake user is invalid for this tenant.");
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

	/** Connection-bound participant for the authoritative web new-case aggregate. */
	long insertBasicCaseAggregate(Connection con, CaseServicePort.CreateCaseCommand command, int statusId)
			throws SQLException {
		validatePracticeAreaForTenant(con, command.shaleClientId(), command.practiceAreaId());
		validateResponsibleAttorneyForTenant(con, command.shaleClientId(), command.responsibleAttorneyUserId());
		try (PreparedStatement check = con.prepareStatement("SELECT 1 FROM dbo.Statuses WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL) AND IsActive=1 AND IsDeleted=0")) {
			check.setInt(1,statusId); check.setInt(2,command.shaleClientId());
			try(ResultSet rs=check.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Case status is invalid for this tenant.");}
		}
		String sql = """
				INSERT INTO dbo.Cases
				  (Name,CaseNumber,PracticeAreaId,ClientEstate,Description,Summary,
				   FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,
				   AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,
				   DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,IsDeleted,CreatedAt,UpdatedAt,ShaleClientId,IntakeTakenByUserId)
				OUTPUT INSERTED.Id
				VALUES (?,?,?,0,?,?,0,0,0,0,0,0,0,0,0,0,0,0,SYSUTCDATETIME(),SYSUTCDATETIME(),?,?)
				""";
		long id;
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i=1; setNullableString(ps,i++,command.caseName()); setNullableString(ps,i++,command.caseNumber());
			ps.setInt(i++,command.practiceAreaId()); setNullableString(ps,i++,command.description());
			setNullableString(ps,i++,command.summary()); ps.setInt(i++,command.shaleClientId()); ps.setInt(i,command.actorUserId());
			try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("Case was not created.");id=rs.getLong(1);}
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now(java.time.Clock.systemUTC()));
		insertCaseStatus(con,id,statusId,now);
		insertResponsibleAttorney(con,id,command.responsibleAttorneyUserId(),now);
		return id;
	}

	private void validateResponsibleAttorneyForTenant(Connection con, int shaleClientId, int userId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("""
				SELECT 1 FROM dbo.Users
				WHERE id = ? AND ShaleClientId = ? AND COALESCE(is_attorney, 0) = 1 AND COALESCE(is_deleted, 0) = 0;
				""")) {
			ps.setInt(1, userId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next())
					return;
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

	/**
	 * Loads the complete calendar selector projection with one bounded SQL query. RLS and the
	 * explicit tenant predicate both apply on the runtime connection.
	 */
	public List<CaseSelectionOptionDto> listCaseSelectionOptions(int shaleClientId) {
		if (shaleClientId <= 0)
			throw new IllegalArgumentException("shaleClientId must be > 0");
		long started = System.nanoTime();
		long connectionStarted = System.nanoTime();
		long connectionMs = -1;
		long sqlStarted = -1;
		long sqlMs = -1;
		long mappingMs = -1;
		int dbRoundTrips = 0;
		try (Connection con = db.requireConnection()) {
			connectionMs = (System.nanoTime() - connectionStarted) / 1_000_000;
			String sql = """
					SELECT
					  c.Id AS CaseId,
					  c.Name AS DisplayName,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS ResponsibleAttorneyName,
					  u.color AS ResponsibleAttorneyColor,
					  c.NonEngagementLetterSent
					FROM dbo.Cases c
					OUTER APPLY (
					  SELECT TOP (1) cu.UserId
					  FROM dbo.CaseUsers cu
					  WHERE cu.CaseId = c.Id
					    AND cu.RoleId = ?
					    AND cu.IsPrimary = 1
					    AND ISNULL(cu.IsDeleted, 0) = 0
					  ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) ra
					LEFT JOIN dbo.Users u ON u.id = ra.UserId
					WHERE c.ShaleClientId = ?
					  AND ISNULL(c.IsDeleted, 0) = 0
					ORDER BY LOWER(COALESCE(c.Name, '')), c.Id;
					""";
			sqlStarted = System.nanoTime();
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, shaleClientId);
				List<CaseSelectionOptionDto> out = new ArrayList<>();
				long mappingStarted;
				dbRoundTrips = 1;
				try (ResultSet rs = ps.executeQuery()) {
					sqlMs = (System.nanoTime() - sqlStarted) / 1_000_000;
					mappingStarted = System.nanoTime();
					while (rs.next()) {
						out.add(new CaseSelectionOptionDto(rs.getLong("CaseId"), rs.getString("DisplayName"),
								rs.getString("ResponsibleAttorneyName"), rs.getString("ResponsibleAttorneyColor"),
								getNullableBoolean(rs, "NonEngagementLetterSent")));
					}
				}
				mappingMs = (System.nanoTime() - mappingStarted) / 1_000_000;
				long totalMs = (System.nanoTime() - started) / 1_000_000;
				PERF_LOG.info("PERF DAO done operation=calendar-case-selector outcome=success rows={} dbRoundTrips=1 connectionMs={} sqlMs={} mappingMs={} elapsedMs={}",
						out.size(), connectionMs, sqlMs, mappingMs, totalMs);
				return List.copyOf(out);
			}
		} catch (SQLException e) {
			long elapsedMs = (System.nanoTime() - started) / 1_000_000;
			long failedSqlMs = sqlStarted < 0 ? -1 : (System.nanoTime() - sqlStarted) / 1_000_000;
			PERF_LOG.error(
					"PERF DAO failed operation=calendar-case-selector outcome=failure elapsedMs={} dbRoundTrips={} connectionMs={} sqlMs={} mappingMs={} exceptionClass={} sqlState={} vendorCode={}",
					elapsedMs, dbRoundTrips, connectionMs, failedSqlMs, mappingMs, e.getClass().getName(), e.getSQLState(), e.getErrorCode(), e);
			int chainIndex = 0;
			for (SQLException next = e.getNextException(); next != null; next = next.getNextException()) {
				PERF_LOG.error("Selector SQL chained exception operation=calendar-case-selector chainIndex={} sqlState={} vendorCode={} exceptionClass={}",
						++chainIndex, next.getSQLState(), next.getErrorCode(), next.getClass().getName(), next);
			}
			throw new RuntimeException("Failed to load calendar case selector options", e);
		}
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

	/**
	 * page is 0-based; reuses knownTotal to avoid recounting unchanged Cases-view queries.
	 */
	public PagedResult<CaseRow> findCasesViewPage(int page,
			int pageSize,
			CaseSort sort,
			boolean includeClosedDenied,
			String query,
			Set<Integer> selectedStatusIds,
			Long knownTotal) {
		return findPageInternal(page, pageSize, sort, includeClosedDenied, null, query, selectedStatusIds, knownTotal);
	}

	/**
	 * Complete Cases-view result set for exports; deliberately independent of UI page size.
	 */
	public List<CaseRow> listCasesViewForExport(CaseSort sort,
			boolean includeClosedDenied,
			String query,
			Set<Integer> selectedStatusIds) {
		final int exportBatchSize = 500;
		return collectAllExportPages(page -> findPageInternal(page, exportBatchSize, sort,
				includeClosedDenied, null, query, selectedStatusIds,
				null));
	}

	static <T> List<T> collectAllExportPages(java.util.function.IntFunction<PagedResult<T>> loader) {
		List<T> rows = new ArrayList<>();
		long total = Long.MAX_VALUE;
		for (int page = 0; rows.size() < total; page++) {
			PagedResult<T> batch = loader.apply(page);
			total = batch.total();
			rows.addAll(batch.items());
			if (batch.items().isEmpty())
				break;
		}
		return List.copyOf(rows);
	}

	/** page is 0-based */
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
		long requestStarted = System.nanoTime();
		CaseSort effectiveSort = sort == null ? CaseSort.INTAKE_NEWEST : sort;
		boolean totalCached = knownTotal != null && knownTotal >= 0;

		String normalizedQuery = normalizeSearchQuery(query);
		Set<Integer> effectiveStatusIds = selectedStatusIds == null ? Set.of() : new HashSet<>(selectedStatusIds);
		boolean casesViewFiltered = restrictToUserId == null && (!normalizedQuery.isBlank() || !effectiveStatusIds.isEmpty());
		long countStarted = System.nanoTime();
		long total = totalCached
				? knownTotal
				: (casesViewFiltered
						? countForCasesView(normalizedQuery, effectiveStatusIds)
						: countAll(includeClosedDenied, restrictToUserId));
		PERF_LOG.info("PERF DAO phase operation=cases-page phase=total-count pageIndex={} pageSize={} sort={} totalCount={} queryCount={} totalCached={} elapsedMs={}",
				page, pageSize, effectiveSort, total, totalCached ? 0 : 1, totalCached,
				(System.nanoTime() - countStarted) / 1_000_000);
		if (total == 0) {
			PERF_LOG.info("PERF DAO done operation=cases-page pageIndex={} pageSize={} sort={} resultCount=0 totalCount=0 queryCount={} totalCached={} elapsedMs={}",
					page, pageSize, effectiveSort, totalCached ? 0 : 1, totalCached,
					(System.nanoTime() - requestStarted) / 1_000_000);
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		String boundaryOrderBy = boundaryOrderByClauseFor(effectiveSort);

		List<CaseRow> out = new ArrayList<>(pageSize);

		long sessionStarted = System.nanoTime();
		try (Connection con = db.requireConnection()) {
			CaseSchema schema = resolveCaseSchema(con);
			String userMembershipFilter = membershipExistsFilter(restrictToUserId, resolveCaseUsersDeletedColumn(con));
			PERF_LOG.info("PERF DAO phase operation=cases-page phase=session-setup pageIndex={} pageSize={} sort={} queryCount=0 elapsedMs={}",
					page, pageSize, effectiveSort, (System.nanoTime() - sessionStarted) / 1_000_000);
			StringBuilder casesViewFilter = new StringBuilder();
			if (!normalizedQuery.isBlank()) {
				casesViewFilter.append("\n  AND LOWER(COALESCE(c.Name, '')) LIKE ?");
			}
			if (!effectiveStatusIds.isEmpty()) {
				casesViewFilter.append("\n  AND (boundary_status.PrimaryStatusId IS NULL OR boundary_status.PrimaryStatusId IN (");
				casesViewFilter.append("?,".repeat(effectiveStatusIds.size()));
				casesViewFilter.setLength(casesViewFilter.length() - 1);
				casesViewFilter.append("))");
			}
			boolean boundaryNeedsStatus = !effectiveStatusIds.isEmpty() || requiresStatusSort(effectiveSort);
			boolean boundaryNeedsResponsibleAttorney = requiresResponsibleAttorneySort(effectiveSort);
			boolean boundaryNeedsAuthoritativeDate = requiresAuthoritativeDateSort(effectiveSort);
			String boundaryStatusApply = boundaryNeedsStatus ? boundaryStatusApplySql() : "";
			String boundaryResponsibleAttorneyJoins = boundaryNeedsResponsibleAttorney ? boundaryResponsibleAttorneyJoinsSql() : "";
			String boundaryDateApply = boundaryNeedsAuthoritativeDate ? authoritativeBoundaryDateApplySql() : "";
			String migratedDateSelect = "CAST(NULL AS date) AS CallerDate, CAST(NULL AS date) AS StatuteOfLimitations, "
					+ "CAST(NULL AS date) AS DateOfIncident, CAST(NULL AS date) AS TortNoticeDeadline,";
			String sql = """
					WITH OrderedPage AS (
					  SELECT c.Id AS CaseId, c.ShaleClientId,
					         ROW_NUMBER() OVER (ORDER BY %s) AS PageOrdinal
					  FROM %s c
					  %s
					  %s
					  %s
					  WHERE %s
					    AND c.ShaleClientId = ?
					    %s
					  ORDER BY %s
					  OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
					)
					SELECT
					  c.Id,
					  c.Name,
					  %s
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
					  LTRIM(RTRIM(COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, ''))) AS ResponsibleAttorneyName
					FROM OrderedPage page
					INNER JOIN %s c ON c.Id = page.CaseId AND c.ShaleClientId = page.ShaleClientId
					LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
					OUTER APPLY (
					    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
					    FROM %s cs INNER JOIN %s s ON s.Id = cs.StatusId
					    WHERE cs.CaseId = c.Id
					    ORDER BY CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END, cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
					) current_status
					OUTER APPLY (
					    SELECT TOP (1) cu.UserId FROM %s cu
					    WHERE cu.CaseId = c.Id AND cu.RoleId = ? AND cu.IsPrimary = 1
					    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
					) ra
					LEFT JOIN %s u ON u.id = ra.UserId
					OUTER APPLY (
					    SELECT TOP (1) CASE
					      WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					      THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
					      ELSE COALESCE(ct.Name, '') END AS ClientName
					    FROM dbo.CaseParties cp INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId INNER JOIN Contacts ct ON ct.Id = cp.ContactId
					    WHERE cp.CaseId = c.Id AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'party'
					      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented' AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
					    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
					) clientContact
					OUTER APPLY (
					    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
					    FROM (SELECT LTRIM(RTRIM(CASE
					      WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
					      THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
					      ELSE COALESCE(ct.Name, o.Name, '') END)) AS DisplayName,
					      CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary, cp.UpdatedAt, cp.CreatedAt, cp.Id
					      FROM dbo.CaseParties cp LEFT JOIN Contacts ct ON ct.Id = cp.ContactId LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
					      WHERE cp.CaseId = c.Id AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
					        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
					        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL) AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)) opp
					    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
					) oppContact
					OUTER APPLY (
					    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
					    FROM dbo.CaseUpdates cu WHERE cu.CaseId = c.Id AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
					      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL ORDER BY cu.CreatedAt DESC, cu.Id DESC
					) latestUpdate
					ORDER BY page.PageOrdinal;
					""".formatted(boundaryOrderBy, CASES_TABLE, boundaryStatusApply,
						boundaryResponsibleAttorneyJoins, boundaryDateApply,
						activeFilter(schema.deletedColumn(), "c"), userMembershipFilter + casesViewFilter,
						boundaryOrderBy, migratedDateSelect, CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE,
						CASE_USERS_TABLE, USERS_TABLE);

			long pageQueryStarted = System.nanoTime();
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int shaleClientId = requireCurrentShaleClientId(con);
				int idx = 1;
				if (boundaryNeedsResponsibleAttorney) {
					ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
				}
				if (boundaryNeedsAuthoritativeDate) {
					ps.setString(idx++, authoritativeSortSemanticRole(effectiveSort));
				}
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
				ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY);
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
			PERF_LOG.info("PERF DAO phase operation=cases-page phase=page-row-query pageIndex={} pageSize={} sort={} resultCount={} queryCount=1 elapsedMs={}",
					page, pageSize, effectiveSort, out.size(), (System.nanoTime() - pageQueryStarted) / 1_000_000);
			System.out.println("[TRACE ASSIGNED_CASES][CaseDao.findPageInternal] "
					+ "restrictToUserId=" + restrictToUserId
					+ " resultCount=" + out.size()
					+ " total=" + total);

			PERF_LOG.info("PERF DAO done operation=cases-page pageIndex={} pageSize={} sort={} resultCount={} totalCount={} queryCount={} totalCached={} elapsedMs={}",
					page, pageSize, effectiveSort, out.size(), total, 1 + (totalCached ? 0 : 1), totalCached,
					(System.nanoTime() - requestStarted) / 1_000_000);
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

	private static String boundaryStatusApplySql() {
		return """
			OUTER APPLY (
			  SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName
			  FROM dbo.CaseStatuses cs INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
			  WHERE cs.CaseId = c.Id
			  ORDER BY CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END, cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
			) boundary_status
			""";
	}

	private static String boundaryResponsibleAttorneyJoinsSql() {
		return """
			OUTER APPLY (
			  SELECT TOP (1) cu.UserId FROM dbo.CaseUsers cu
			  WHERE cu.CaseId = c.Id AND cu.RoleId = ? AND cu.IsPrimary = 1
			  ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
			) boundary_ra
			LEFT JOIN dbo.Users boundary_user ON boundary_user.id = boundary_ra.UserId
			""";
	}

	private static String authoritativeBoundaryDateApplySql() {
		return """
			OUTER APPLY (
			  SELECT MAX(cd.StartsAt) AS SortDate
			  FROM dbo.CaseDates cd
			  INNER JOIN dbo.CaseDateTypes stored_type ON stored_type.Id = cd.CaseDateTypeId
			    AND (stored_type.ShaleClientId = cd.ShaleClientId OR stored_type.ShaleClientId IS NULL)
			  WHERE cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId
			    AND cd.IsDeleted = 0 AND EXISTS (
			      SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings role_mapping
			      WHERE role_mapping.CaseDateTypeId=stored_type.Id AND role_mapping.SemanticRoleKey=?
			        AND role_mapping.IsActive=1 AND role_mapping.IsDeleted=0
			        AND (role_mapping.ShaleClientId=c.ShaleClientId OR role_mapping.ShaleClientId IS NULL)
			        AND NOT (role_mapping.ShaleClientId IS NULL AND EXISTS (
			          SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings tenant_mapping
			          JOIN dbo.CaseDateTypes tenant_type ON tenant_type.Id=tenant_mapping.CaseDateTypeId
			          WHERE tenant_mapping.ShaleClientId=c.ShaleClientId
			            AND tenant_mapping.SemanticRoleKey=role_mapping.SemanticRoleKey
			            AND tenant_mapping.IsActive=1 AND tenant_mapping.IsDeleted=0
			            AND tenant_type.ShaleClientId=c.ShaleClientId
			            AND tenant_type.IsActive=1 AND tenant_type.IsDeleted=0))
			    )
			) boundary_date
			""";
	}

	private static boolean requiresAuthoritativeDateSort(CaseSort sort) {
		return sort == CaseSort.INTAKE_OLDEST || sort == CaseSort.INTAKE_NEWEST
				|| sort == CaseSort.STATUTE_SOONEST || sort == CaseSort.STATUTE_LATEST
				|| sort == CaseSort.TORT_NOTICE_SOONEST;
	}

	private static boolean requiresStatusSort(CaseSort sort) {
		return sort == CaseSort.CASE_STATUS_ASC || sort == CaseSort.CASE_STATUS_DESC;
	}

	private static boolean requiresResponsibleAttorneySort(CaseSort sort) {
		return sort == CaseSort.RESPONSIBLE_ATTORNEY_ASC || sort == CaseSort.RESPONSIBLE_ATTORNEY_DESC;
	}

	private static String authoritativeSortSemanticRole(CaseSort sort) {
		if (sort == CaseSort.INTAKE_OLDEST || sort == CaseSort.INTAKE_NEWEST) return CaseDateSemanticRole.INTAKE.persistedKey();
		if (sort == CaseSort.STATUTE_SOONEST || sort == CaseSort.STATUTE_LATEST) return CaseDateSemanticRole.STATUTE_OF_LIMITATIONS.persistedKey();
		if (sort == CaseSort.TORT_NOTICE_SOONEST) return CaseDateSemanticRole.TORT_NOTICE_DEADLINE.persistedKey();
		throw new IllegalArgumentException("Sort does not require an authoritative Case Date");
	}

	private static String boundaryOrderByClauseFor(CaseSort sort) {
		String responsibleName = "LTRIM(RTRIM(COALESCE(boundary_user.name_first, '') + CASE WHEN COALESCE(boundary_user.name_first, '') = '' OR COALESCE(boundary_user.name_last, '') = '' THEN '' ELSE ' ' END + COALESCE(boundary_user.name_last, '')))";
		return switch (sort) {
		case INTAKE_OLDEST -> "boundary_date.SortDate" + " ASC, c.Id ASC";
		case STATUTE_SOONEST -> "boundary_date.SortDate" + " ASC, c.Id ASC";
		case STATUTE_LATEST -> "boundary_date.SortDate" + " DESC, c.Id DESC";
		case TORT_NOTICE_SOONEST -> "boundary_date.SortDate" + " ASC, c.Id ASC";
		case UPDATED_OLDEST -> "c.UpdatedAt ASC, c.Id ASC";
		case UPDATED_NEWEST -> "c.UpdatedAt DESC, c.Id DESC";
		case CASE_NAME_ASC -> "c.Name ASC, c.Id ASC";
		case CASE_NAME_DESC -> "c.Name DESC, c.Id DESC";
		case RESPONSIBLE_ATTORNEY_ASC -> responsibleName + " ASC, c.Id ASC";
		case RESPONSIBLE_ATTORNEY_DESC -> responsibleName + " DESC, c.Id DESC";
		case CASE_STATUS_ASC -> "boundary_status.CurrentStatusName ASC, c.Id ASC";
		case CASE_STATUS_DESC -> "boundary_status.CurrentStatusName DESC, c.Id DESC";
		case INTAKE_NEWEST -> "boundary_date.SortDate" + " DESC, c.Id DESC";
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
					List<com.shale.core.dto.CaseOverviewDto.ContactSummary> clients = listCasePartiesContactsByRoleAndSide(con, caseId, PARTY_ROLE_NAME_PARTY,
							PARTY_SIDE_KEY_REPRESENTED);
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
							null,
							null,
							null,
							null,
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
				? "LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = ?"
				: "1 = 0";
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
				  c.AcceptedDate,
				  c.ClosedDate,
				  c.DeniedDate,
				  c.ClientEstate,
				  c.OfficePrinterCode,
				  c.MedicalRecordsRequested,
				  c.FeeAgreementSigned,
				  c.NonEngagementLetterSent,
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
				  responsible_attorney.UserId AS ResponsibleAttorneyId,
				  c.IntakeTakenByUserId,
				  LTRIM(RTRIM(CONCAT(intake_user.name_first, ' ', intake_user.name_last))) AS IntakeTakenByDisplayName
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
				LEFT JOIN %s intake_user
				  ON intake_user.id = c.IntakeTakenByUserId
				 AND intake_user.ShaleClientId = c.ShaleClientId
				WHERE c.Id = ?
				  AND %s;
				""".formatted(schema.rowVersionSelectExpression("c"), CASES_TABLE, CASE_STATUSES_TABLE, STATUSES_TABLE, CASE_USERS_TABLE, USERS_TABLE, USERS_TABLE, activeFilter(
				schema.deletedColumn(), "c"));

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
				null,
				"",
				toLocalDate(rs.getDate("AcceptedDate")),
				toLocalDate(rs.getDate("ClosedDate")),
				toLocalDate(rs.getDate("DeniedDate")),
				null, null, null, null, null, null,
				rs.getString("ClientEstate"),
				rs.getString("OfficePrinterCode"),
				getNullableBoolean(rs, "MedicalRecordsRequested"),
				getNullableBoolean(rs, "FeeAgreementSigned"),
				null,
				getNullableBoolean(rs, "NonEngagementLetterSent"),
				null,
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
				statusHistory,
				getNullableInt(rs, "IntakeTakenByUserId"),
				rs.getString("IntakeTakenByDisplayName")
		);
	}

	/** Existing-case desktop update boundary which deliberately owns no migrated dates. */
	public CaseDetailDto updateCaseNonDate(long caseId, String name, String caseNumber, String description,
			String summary, byte[] expectedRowVer, Integer actorUserId) {
		if (expectedRowVer == null || expectedRowVer.length == 0)
			throw new IllegalArgumentException("expectedRowVer is required");
		if (actorUserId == null || actorUserId <= 0)
			throw new IllegalArgumentException("actorUserId is required");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
			int tenant = requireCurrentShaleClientId(con);
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					UPDATE %s SET Name = ?, CaseNumber = ?, Description = ?, Summary = ?, UpdatedAt = SYSDATETIME()
					WHERE Id = ? AND RowVer = ? AND %s;
					""".formatted(CASES_TABLE, activeFilter(schema.deletedColumn(), null));
			CaseDetailDto before = selectCaseDetail(con, caseId);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, name);
				ps.setString(2, caseNumber);
				ps.setString(3, description);
				ps.setString(4, summary);
				ps.setLong(5, caseId);
				ps.setBytes(6, expectedRowVer);
				int rows = ps.executeUpdate();
				if (rows == 0) {
					con.rollback();
					return null;
				}
				if (rows != 1)
					throw new RuntimeException("Unexpected update row count for caseId=" + caseId + ": " + rows);
				CaseDetailDto updated = selectCaseDetail(con, caseId);
				if (before != null && updated != null) {
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "Description", caseId, before.getDescription(), updated.getDescription());
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "Summary", caseId, before.getSummary(), updated.getSummary());
					CaseDetailsTimelineWriter.appendChanges(con, caseId, tenant, actorUserId, before, updated);
				}
				con.commit();
				return updated;
			}
			} catch (SQLException | RuntimeException e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update non-date case fields (caseId=" + caseId + ")", e);
		}
	}

	/** Connection-bound participant for the server existing-case aggregate. */
	public CaseDetailDto updateCaseNonDate(Connection con, long caseId, int tenant, String name,
			String caseNumber, String description, String summary, byte[] expectedRowVer, int actorUserId) throws SQLException {
		CaseDetailDto before = selectCaseDetail(con, caseId);
		String sql = "UPDATE dbo.Cases SET Name=?,CaseNumber=?,Description=?,Summary=?,UpdatedAt=SYSDATETIME() "
				+ "WHERE Id=? AND ShaleClientId=? AND RowVer=? AND ISNULL(IsDeleted,0)=0";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1,name); ps.setString(2,caseNumber); ps.setString(3,description); ps.setString(4,summary);
			ps.setLong(5,caseId); ps.setInt(6,tenant); ps.setBytes(7,expectedRowVer);
			if (ps.executeUpdate()!=1) throw new IllegalStateException("Case changed; reload before saving.");
		}
		CaseDetailDto after=selectCaseDetail(con,caseId);
		if(after==null) throw new IllegalStateException("Case is not available for this tenant.");
		phiAuditService.auditUpdate(con,actorUserId,"Cases","Description",caseId,before==null?null:before.getDescription(),after.getDescription());
		phiAuditService.auditUpdate(con,actorUserId,"Cases","Summary",caseId,before==null?null:before.getSummary(),after.getSummary());
		if (before != null) CaseDetailsTimelineWriter.appendChanges(con,caseId,tenant,actorUserId,before,after);
		return after;
	}

	/** Broad Details boundary for unrelated existing-case fields only. */
	public CaseDetailDto updateCaseDetailsNonMigrated(long caseId, int shaleClientId, String name, String caseNumber, Integer practiceAreaId,
			String description, LocalDate acceptedDate, LocalDate closedDate, LocalDate deniedDate, String clientEstate,
			String officePrinterCode, Boolean medicalRecordsRequested, Boolean feeAgreementSigned,
			Boolean nonEngagementLetterSent, Boolean acceptedChronology, Boolean acceptedConsultantExpertSearch,
			Boolean acceptedTestifyingExpertSearch, Boolean acceptedMedicalLiterature, String acceptedDetail,
			Boolean deniedChronology, String deniedDetail, String summary, String receivedUpdates,
			byte[] expectedRowVer, int actorUserId) {
		if (expectedRowVer == null || expectedRowVer.length == 0)
			throw new IllegalArgumentException("expectedRowVer is required");
		try (Connection con = db.requireConnection()) {
			if (shaleClientId <= 0 || actorUserId <= 0)
				throw new IllegalArgumentException("tenant and actor are required");
			CaseSchema schema = resolveCaseSchema(con);
			String sql = """
					UPDATE %s SET Name=?, CaseNumber=?, PracticeAreaId=?, Description=?, AcceptedDate=?, ClosedDate=?, DeniedDate=?,
					ClientEstate=?, OfficePrinterCode=?, MedicalRecordsRequested=?, FeeAgreementSigned=?, NonEngagementLetterSent=?,
					AcceptedChronology=?, AcceptedConsultantExpertSearch=?, AcceptedTestifyingExpertSearch=?, AcceptedMedicalLiterature=?,
					AcceptedDetail=?, DeniedChronology=?, DeniedDetail=?, Summary=?, ReceivedUpdates=?, UpdatedAt=SYSDATETIME()
					WHERE Id=? AND ShaleClientId=? AND RowVer=? AND %s;
					""".formatted(CASES_TABLE, activeFilter(schema.deletedColumn(), null));
			con.setAutoCommit(false);
			try {
			CaseDetailDto before = selectCaseDetail(con, caseId);
			if (before == null)
				throw new IllegalArgumentException("Case is not available for this tenant.");
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int i = 1;
				ps.setString(i++, name);
				ps.setString(i++, caseNumber);
				if (practiceAreaId == null)
					ps.setNull(i++, java.sql.Types.INTEGER);
				else
					ps.setInt(i++, practiceAreaId);
				ps.setString(i++, description);
				setNullableDate(ps, i++, acceptedDate);
				setNullableDate(ps, i++, closedDate);
				setNullableDate(ps, i++, deniedDate);
				setNullableString(ps, i++, clientEstate);
				setNullableString(ps, i++, officePrinterCode);
				setNullableBoolean(ps, i++, medicalRecordsRequested);
				setNullableBoolean(ps, i++, feeAgreementSigned);
				setNullableBoolean(ps, i++, nonEngagementLetterSent);
				setNullableBoolean(ps, i++, acceptedChronology);
				setNullableBoolean(ps, i++, acceptedConsultantExpertSearch);
				setNullableBoolean(ps, i++, acceptedTestifyingExpertSearch);
				setNullableBoolean(ps, i++, acceptedMedicalLiterature);
				setNullableString(ps, i++, acceptedDetail);
				setNullableBoolean(ps, i++, deniedChronology);
				setNullableString(ps, i++, deniedDetail);
				setNullableString(ps, i++, summary);
				setNullableString(ps, i++, receivedUpdates);
				ps.setLong(i++, caseId);
				ps.setInt(i++, shaleClientId);
				ps.setBytes(i, expectedRowVer);
				int rows = ps.executeUpdate();
				if (rows == 0) {
					con.rollback();
					return null;
				}
				if (rows != 1)
					throw new RuntimeException("Unexpected update row count for caseId=" + caseId + ": " + rows);
				CaseDetailDto updated = selectCaseDetail(con, caseId);
				if (before != null && updated != null) {
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "AcceptedDetail", caseId, before.getAcceptedDetail(), updated.getAcceptedDetail());
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "DeniedDetail", caseId, before.getDeniedDetail(), updated.getDeniedDetail());
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "ReceivedUpdates", caseId, before.getReceivedUpdates(), updated.getReceivedUpdates());
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "Description", caseId, before.getDescription(), updated.getDescription());
					phiAuditService.auditUpdate(con, actorUserId, "Cases", "Summary", caseId, before.getSummary(), updated.getSummary());
					CaseDetailsTimelineWriter.appendChanges(con, caseId, shaleClientId, actorUserId, before, updated);
				}
				con.commit();
				return updated;
			}
			} catch (SQLException | RuntimeException e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update non-migrated case details (caseId=" + caseId + ")", e);
		}
	}

	public boolean softDeleteCase(long caseId, Integer shaleClientId) {
		return updateDeletedState(caseId, shaleClientId, true);
	}

	public boolean restoreCase(long caseId, Integer shaleClientId) {
		return updateDeletedState(caseId, shaleClientId, false);
	}

	public boolean restoreCase(long caseId, Integer shaleClientId, byte[] expectedRowVer) {
		if (expectedRowVer == null || expectedRowVer.length == 0)
			throw new IllegalArgumentException("expectedRowVer is required");
		return updateDeletedState(caseId, shaleClientId, false, expectedRowVer.clone());
	}

	private boolean updateDeletedState(long caseId, Integer shaleClientId, boolean deleted) {
		return updateDeletedState(caseId, shaleClientId, deleted, null);
	}

	private boolean updateDeletedState(long caseId, Integer shaleClientId, boolean deleted, byte[] suppliedRowVer) {
		if (caseId <= 0) {
			throw new IllegalArgumentException("caseId must be > 0");
		}
		if (shaleClientId == null || shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			if (shaleClientId.intValue() != currentShaleClientId) {
				throw new IllegalArgumentException("shaleClientId does not match current session");
			}
			int actorUserId = requireCurrentPrincipalUserId(con, currentShaleClientId);

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
					  AND %s
					  AND RowVer = ?;
					""".formatted(CASES_TABLE, schema.deletedColumn(), desiredStateFilter);
			byte[] expectedRowVer = suppliedRowVer == null
					? selectCaseRowVer(con, caseId, currentShaleClientId, desiredStateFilter)
					: suppliedRowVer.clone();
			if (expectedRowVer == null) { con.rollback(); return false; }

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, deleted ? 1 : 0);
				ps.setLong(2, caseId);
				ps.setInt(3, shaleClientId);
				ps.setBytes(4, expectedRowVer);
				if (ps.executeUpdate() != 1) { con.rollback(); return false; }
			}
			java.time.Instant occurredAt = java.time.Instant.now();
			EntityActionAuditEvent.Action action = deleted ? EntityActionAuditEvent.Action.DELETED : EntityActionAuditEvent.Action.RESTORED;
			entityActionAuditDao.append(con, new EntityActionAuditEvent(0, currentShaleClientId, actorUserId,
					EntityActionAuditEvent.EntityType.CASE, caseId, action, occurredAt, null, null, null,
					"SHALE_DESKTOP", Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, Long.toString(caseId))));
			insertLifecycleTimelineEvent(con, caseId, currentShaleClientId, actorUserId, deleted, occurredAt);
			con.commit();
			return true;
			} catch (RuntimeException | SQLException e) { con.rollback(); throw e; }
		} catch (SQLException e) {
			throw new RuntimeException("Failed to " + (deleted ? "soft delete" : "restore") + " case (id=" + caseId + ")", e);
		}
	}

	private static byte[] selectCaseRowVer(Connection con, long caseId, int tenant, String stateFilter) throws SQLException {
		String sql = "SELECT RowVer FROM dbo.Cases WHERE Id=? AND ShaleClientId=? AND " + stateFilter;
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setLong(1, caseId); ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getBytes(1) : null; } }
	}

	private static int requireCurrentPrincipalUserId(Connection con, int tenant) throws SQLException {
		String sql = "SELECT u.Id FROM dbo.Users u WHERE u.Id=CAST(SESSION_CONTEXT(N'PrincipalUserId') AS INT) AND u.ShaleClientId=? AND ISNULL(u.is_deleted,0)=0";
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, tenant);
			try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SecurityException("Authenticated user is unavailable for this tenant."); return rs.getInt(1); } }
	}

	private static void insertLifecycleTimelineEvent(Connection con, long caseId, int tenant, int actor, boolean deleted, java.time.Instant at) throws SQLException {
		String sql = "INSERT INTO dbo.CaseTimelineEvents (CaseId,ShaleClientId,EventType,OccurredAt,ActorUserId,Title,Body) VALUES (?,?,?,?,?,?,NULL)";
		try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setLong(1,caseId); ps.setInt(2,tenant);
			ps.setString(3, deleted ? CaseTimelineEventTypes.CASE_DELETED : CaseTimelineEventTypes.CASE_RESTORED);
			ps.setTimestamp(4, Timestamp.from(at)); ps.setInt(5,actor); ps.setString(6, deleted ? "Case deleted" : "Case restored"); ps.executeUpdate(); }
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
				                       AND u.ShaleClientId = cte.ShaleClientId
				WHERE cte.CaseId = ?
				  AND cte.ShaleClientId = CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)
				  AND cte.EventType NOT LIKE 'TASK[_]%'
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
				  (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email,
				  (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone
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
				  (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email,
				  (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone
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
				  (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email,
				  (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone
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
				  (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) AS Email,
				  (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) AS Phone
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
				out.add(new PartyRoleRow(row.id(), row.name(), row.systemKey()));
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
					      (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id)
					    ELSE NULLIF(LTRIM(RTRIM(COALESCE(o.Email, ''))), '')
					  END AS Email,
					  CASE
					    WHEN cp.ContactId IS NOT NULL THEN
					      (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id)
					    ELSE NULLIF(LTRIM(RTRIM(COALESCE(o.Phone, ''))), '')
					  END AS Phone,
					  CASE WHEN cp.ContactId IS NOT NULL THEN
					    (SELECT STRING_AGG(CONVERT(nvarchar(max), credentials.Abbreviation), NCHAR(31))
					       WITHIN GROUP (ORDER BY credentials.DisplayOrder, credentials.SortOrder,
					                              credentials.Name, credentials.DefinitionId, credentials.AssignmentId)
					     FROM (SELECT cc.Id AS AssignmentId, cc.DisplayOrder, cd.SortOrder, cd.Name,
					                  cd.Id AS DefinitionId, cd.Abbreviation
					             FROM dbo.ContactCredentials cc
					             JOIN dbo.CredentialDefinitions cd ON cd.Id=cc.CredentialDefinitionId
					              AND (cd.ShaleClientId=cc.ShaleClientId OR cd.ShaleClientId IS NULL)
					            WHERE cc.ContactId=cp.ContactId AND cc.ShaleClientId=c.ShaleClientId
					              AND cc.IsDeleted=0
					              AND NULLIF(LTRIM(RTRIM(cd.Abbreviation)), N'') IS NOT NULL) credentials)
					  END AS CredentialAbbreviations
					FROM dbo.CaseParties cp
					INNER JOIN dbo.Cases c
					  ON c.Id = cp.CaseId
					INNER JOIN dbo.PartyRoles pr
					  ON pr.Id = cp.PartyRoleId
					LEFT JOIN dbo.Contacts ct
					  ON ct.Id = cp.ContactId AND ct.ShaleClientId = c.ShaleClientId
					LEFT JOIN dbo.Organizations o
					  ON o.Id = cp.OrganizationId AND o.ShaleClientId = c.ShaleClientId
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
						String displayName = rs.getString("DisplayName");
						if (rs.getLong("ContactId") > 0 && !rs.wasNull()) {
							displayName = ContactNamePresentation.effectiveDisplayNameFromAbbreviations(
									displayName, splitCredentialAbbreviations(rs.getString("CredentialAbbreviations")));
						}
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
								displayName,
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

	private static List<String> splitCredentialAbbreviations(String value) {
		return value == null || value.isBlank() ? List.of() : List.of(value.split("\\u001f", -1));
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
			String systemKey,
			boolean active,
			boolean deleted
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

	static boolean isBuiltinPartyRoleSystemKey(String systemKey) {
		String normalizedSystemKey = normalizeSystemKey(systemKey);
		return PARTY_ROLE_NAME_PARTY.equals(normalizedSystemKey);
	}

	private static String resolvePartyRoleSystemKey(String systemKey, String roleName) {
		return normalizeSystemKey(systemKey);
	}

	private static String resolvePartySideSystemKey(String systemKey, String sideName) {
		return normalizeSystemKey(systemKey);
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
		merged.sort((a, b) ->
		{
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
		merged.sort((a, b) ->
		{
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
				normalizeSystemKey(rs.getString("SystemKey")),
				rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted")
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
		merged.sort((a, b) ->
		{
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
					SELECT Id, Name, SortOrder, Color, %s, %s, IsActive, IsDeleted
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
							SELECT Id, Name, SortOrder, Color, %s, %s, IsActive, IsDeleted
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
						List<StatusRow> effective = resolveEffectiveStatuses(globalStatuses, tenantStatuses);
						effective.removeIf(status -> !status.active() || status.deleted());
						return effective;
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
				SELECT Id, ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey, IsActive, IsDeleted
				FROM dbo.Statuses
				WHERE ShaleClientId = ?
                  AND (?=1 OR (IsActive=1 AND IsDeleted=0))
				ORDER BY SortOrder, Name, Id;
				""";
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
            ps.setBoolean(2, includeInactive);
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
				if (area == null)
					continue;
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
				if (area == null)
					continue;
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
		merged.sort((a, b) ->
		{
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
					if (keys.next())
						return findPracticeAreaById(con, keys.getInt(1));
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
			if (existing == null)
				throw new IllegalArgumentException("Practice area not found.");
			String normalizedSystemKey = normalizeSystemKey(systemKey == null ? existing.systemKey() : systemKey);
			if (existing.shaleClientId() == null) {
				return createPracticeArea(shaleClientId, normalizedName, color, active, normalizedSystemKey);
			}
			if (existing.shaleClientId() != shaleClientId)
				throw new IllegalArgumentException("Practice area belongs to a different tenant.");
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
				if (ps.executeUpdate() == 0)
					throw new IllegalArgumentException("Practice area not found for this tenant.");
			}
			return findPracticeAreaById(con, practiceAreaId);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update practice area.", e);
		}
	}

	public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		try (Connection con = db.requireConnection()) {
			PracticeAreaDto existing = findPracticeAreaById(con, practiceAreaId);
			if (existing == null)
				throw new IllegalArgumentException("Practice area not found.");
			if (existing.systemKey() != null && !existing.systemKey().isBlank())
				throw new IllegalArgumentException("System practice areas cannot be removed.");
			if (existing.shaleClientId() == null)
				throw new IllegalArgumentException("Global practice areas cannot be removed from tenant settings.");
			if (existing.shaleClientId() != shaleClientId)
				throw new IllegalArgumentException("Practice area belongs to a different tenant.");
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
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapPracticeAreaDto(rs) : null;
			}
		}
	}

	private static void validatePracticeAreaUnique(Connection con, int shaleClientId, Integer excludeId, String name, String systemKey) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT 1 FROM dbo.PracticeAreas
				WHERE ShaleClientId = ? AND IsDeleted = 0
				  AND (LOWER(LTRIM(RTRIM(Name))) = LOWER(?)
				""");
		if (systemKey != null)
			sql.append(" OR SystemKey = ?");
		sql.append(")");
		if (excludeId != null)
			sql.append(" AND Id <> ?");
		try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
			int i = 1;
			ps.setInt(i++, shaleClientId);
			ps.setString(i++, name);
			if (systemKey != null)
				ps.setString(i++, systemKey);
			if (excludeId != null)
				ps.setInt(i++, excludeId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next())
					throw new IllegalArgumentException("A tenant practice area with this name already exists.");
			}
		}
	}

	private static String normalizePracticeAreaName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isBlank())
			throw new IllegalArgumentException("Practice area name is required.");
		return trimmed;
	}

	public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
		if (shaleClientId <= 0) return List.of();
		String sql = "SELECT Id,ShaleClientId,Name,IsClosed,SortOrder,Color,LifecycleKey,SystemKey,IsActive,IsDeleted FROM dbo.Statuses WHERE (ShaleClientId=? OR ShaleClientId IS NULL) ORDER BY SortOrder,Name,Id";
		try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) {
			ps.setInt(1,shaleClientId); List<CaseStatusDto> globals=new ArrayList<>(), tenants=new ArrayList<>();
			try(ResultSet rs=ps.executeQuery()){while(rs.next()){CaseStatusDto dto=mapCaseStatusDto(rs);(dto.shaleClientId()==null?globals:tenants).add(dto);}}
			return resolveEffectiveCaseStatuses(globals,tenants,includeInactive);
		} catch(SQLException e){throw new RuntimeException("Failed to list case statuses",e);}
	}

	static List<CaseStatusDto> resolveEffectiveCaseStatuses(List<CaseStatusDto> globals,List<CaseStatusDto> tenants,boolean includeInactive){
		Map<String,CaseStatusDto> keyed=new LinkedHashMap<>();List<CaseStatusDto> unkeyed=new ArrayList<>();
		for(CaseStatusDto d:globals){String k=normalizeSystemKey(d.systemKey());if(k==null)unkeyed.add(d);else keyed.put(k,d);} for(CaseStatusDto d:tenants){String k=normalizeSystemKey(d.systemKey());if(k==null)unkeyed.add(d);else keyed.put(k,d);} List<CaseStatusDto> out=new ArrayList<>(keyed.values());out.addAll(unkeyed);if(!includeInactive)out.removeIf(d->!d.active()||d.deleted());out.sort(java.util.Comparator.comparing((CaseStatusDto d)->d.sortOrder()==null?0:d.sortOrder()).thenComparing(CaseStatusDto::name));return out;
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
					null, status.active(), status.deleted()));
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
					INSERT INTO dbo.Statuses (ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey, IsActive, IsDeleted)
					VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0);
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
					SET Name = ?, IsClosed = ?, SortOrder = ?, Color = ?, LifecycleKey = ?, SystemKey = ?, IsActive=1, IsDeleted=0
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

	public void removeCaseStatus(int tenant,int actor,int statusId){ mutateCaseStatusLifecycle(tenant,actor,statusId,false); }
	public CaseStatusDto restoreCaseStatus(int tenant,int actor,int statusId){ mutateCaseStatusLifecycle(tenant,actor,statusId,true); try(Connection c=db.requireConnection()){return findCaseStatusById(c,statusId);}catch(SQLException e){throw new RuntimeException("Failed to reload case status",e);} }
	private void mutateCaseStatusLifecycle(int tenant,int actor,int statusId,boolean restore){
		try(Connection con=db.requireConnection()){con.setAutoCommit(false);try{validateAdminActorForTenant(con,tenant,actor);CaseStatusDto status=findCaseStatusById(con,statusId);if(status==null||status.shaleClientId()==null||status.shaleClientId()!=tenant)throw new IllegalArgumentException("Case status is not available for this tenant.");
			try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Statuses SET IsActive=?,IsDeleted=? WHERE Id=? AND ShaleClientId=?")){ps.setBoolean(1,restore);ps.setBoolean(2,!restore);ps.setInt(3,statusId);ps.setInt(4,tenant);if(ps.executeUpdate()!=1)throw new IllegalStateException("Case status changed concurrently.");}
			entityActionAuditDao.append(con,EntityActionAuditEvent.now(tenant,actor,EntityActionAuditEvent.EntityType.CASE_STATUS,statusId,restore?EntityActionAuditEvent.Action.RESTORED:EntityActionAuditEvent.Action.DEACTIVATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.ACTIVE,restore)));con.commit();
		}catch(Exception e){con.rollback();throw e;}finally{con.setAutoCommit(true);}}catch(SQLException e){throw new RuntimeException("Case status lifecycle change failed.",e);}
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
				getNullableInt(rs, "ShaleClientId"), rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"));
	}

	private CaseStatusDto findCaseStatusById(Connection con, int statusId) throws SQLException {
		String sql = """
				SELECT Id, ShaleClientId, Name, IsClosed, SortOrder, Color, LifecycleKey, SystemKey, IsActive, IsDeleted
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
		if (trimmed.isBlank())
			throw new IllegalArgumentException("Status name is required.");
		return trimmed;
	}

	private static String trimToNull(String value) {
		if (value == null)
			return null;
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
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to set primary legal assistant (caseId=" + caseId + ", userId=" + userId + ")", e);
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
				try {
					con.rollback();
				} catch (SQLException ignored) {
				}
			}
			throw new RuntimeException("Failed to remove primary legal assistant (caseId=" + caseId + ")", e);
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
			con.setAutoCommit(false);
			try {
				validateAdminActorForTenant(con, shaleClientId, actorUserId);
				LinkTypeDto created = insertTenantLinkType(con, shaleClientId, actorUserId, name, color, active, normalizeSystemKey(systemKey));
				auditLinkType(con, shaleClientId, actorUserId, created.id(), EntityActionAuditEvent.Action.CREATED, active);
				con.commit();
				return created;
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	public LinkTypeDto updateLinkType(int shaleClientId, int actorUserId, int linkTypeId, String name, String color,
			boolean active, String systemKey, byte[] expectedRowVer) {
		requireRowVer(expectedRowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateAdminActorForTenant(con, shaleClientId, actorUserId);
				LinkTypeDto existing = findLinkTypeById(con, linkTypeId);
				if (existing == null || (existing.shaleClientId() != null && existing.shaleClientId() != shaleClientId)) {
					throw new IllegalArgumentException("Link type is not available for this tenant.");
				}
				LinkTypeDto result;
				EntityActionAuditEvent.Action action;
				if (existing.shaleClientId() == null) {
					assertRowVerMatches("dbo.LinkTypes", linkTypeId, expectedRowVer, con, "Link type changed.");
					result = customizeGlobalLinkType(con, shaleClientId, actorUserId, existing, name, color, active, systemKey);
					action = EntityActionAuditEvent.Action.OVERRIDE_CREATED;
				} else {
					result = updateTenantLinkType(con, shaleClientId, actorUserId, linkTypeId, name, color, active,
							normalizeSystemKey(systemKey == null ? existing.systemKey() : systemKey), expectedRowVer);
					action = EntityActionAuditEvent.Action.UPDATED;
				}
				auditLinkType(con, shaleClientId, actorUserId, result.id(), action, result.active());
				con.commit();
				return result;
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	public LinkTypeDto setLinkTypeActive(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer) {
		requireRowVer(expectedRowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateAdminActorForTenant(con, shaleClientId, actorUserId);
				LinkTypeDto existing = findLinkTypeById(con, linkTypeId);
				if (existing == null || (existing.shaleClientId() != null && existing.shaleClientId() != shaleClientId))
					throw new IllegalArgumentException("Link type is not available for this tenant.");
				LinkTypeDto result;
				EntityActionAuditEvent.Action action = active ? EntityActionAuditEvent.Action.ACTIVATED : EntityActionAuditEvent.Action.DEACTIVATED;
				if (existing.shaleClientId() == null) {
					assertRowVerMatches("dbo.LinkTypes", linkTypeId, expectedRowVer, con, "Link type changed.");
					result = customizeGlobalLinkType(con, shaleClientId, actorUserId, existing, existing.name(), existing.color(), active, existing.systemKey());
				} else {
					result = updateTenantLinkType(con, shaleClientId, actorUserId, linkTypeId, existing.name(), existing.color(), active, existing.systemKey(), expectedRowVer);
				}
				auditLinkType(con, shaleClientId, actorUserId, result.id(), action, result.active());
				con.commit();
				return result;
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	public void resetLinkTypeOverride(int shaleClientId, int actorUserId, int linkTypeId) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateAdminActorForTenant(con, shaleClientId, actorUserId);
				LinkTypeDto requested = findLinkTypeById(con, linkTypeId);
				if (requested == null || (requested.shaleClientId() != null && requested.shaleClientId() != shaleClientId))
					throw new IllegalArgumentException("Link type is not available for this tenant.");
				LinkTypeDto override = requested.shaleClientId() == null ? findTenantLinkTypeBySystemKey(con, shaleClientId, requested.systemKey()) : requested;
				if (override == null) {
					con.commit();
					return;
				}
				softDeleteTenantLinkType(con, shaleClientId, actorUserId, override.id());
				auditLinkType(con, shaleClientId, actorUserId, override.id(), EntityActionAuditEvent.Action.OVERRIDE_RESET, false);
				con.commit();
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("The change could not be saved because its audit record could not be completed.", e);
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
				if (!rs.next())
					return java.util.Optional.empty();
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
				boolean hasActiveLinks = hasActiveCaseLinks(con, shaleClientId, caseId);
				boolean hasActivePrimary = hasActivePrimaryCaseLink(con, shaleClientId, caseId);
				boolean makePrimaryOnInsert = primary || !hasActiveLinks;
				if (makePrimaryOnInsert && hasActivePrimary) {
					clearActivePrimaryForCreate(con, shaleClientId, caseId, actorUserId);
				}
				long externalId = insertExternalLink(con, shaleClientId, actorUserId, linkTypeId, displayName, url, description);
				long caseLinkId = insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, makePrimaryOnInsert,
						notes, sortOrder == null ? nextSortOrder(con, shaleClientId, caseId) : sortOrder);
				if (!makePrimaryOnInsert && !hasActivePrimary) {
					ensurePrimaryCandidate(con, shaleClientId, caseId, actorUserId);
				}
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.CREATED, Map.of(
						EntityActionAuditEvent.MetadataKey.EXTERNAL_LINK_ID, externalId));
				CaseTimelineWriter.append(con,caseId,shaleClientId,actorUserId,CaseTimelineWriter.CASE_LINK_CREATED,
						"added the link '"+safeTimelineLabel(displayName,"Link")+"'",null);
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
				boolean hasActiveLinks = hasActiveCaseLinks(con, shaleClientId, caseId);
				boolean hasActivePrimary = hasActivePrimaryCaseLink(con, shaleClientId, caseId);
				boolean makePrimaryOnInsert = primary || !hasActiveLinks;
				if (makePrimaryOnInsert && hasActivePrimary)
					clearActivePrimaryForCreate(con, shaleClientId, caseId, actorUserId);
				long externalId = insertExternalLink(con, shaleClientId, actorUserId, linkTypeId, displayName, url, description);
				long caseLinkId = insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, makePrimaryOnInsert, notes, sortOrder == null ? nextSortOrder(con,
						shaleClientId, caseId) : sortOrder);
				for (CaseLinkShareDraft share : shares == null ? List.<CaseLinkShareDraft>of() : shares)
					insertCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.contactId(), share.sharedAt(), share.notes());
				if (!makePrimaryOnInsert && !hasActivePrimary)
					ensurePrimaryCandidate(con, shaleClientId, caseId, actorUserId);
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.CREATED, Map.of(
						EntityActionAuditEvent.MetadataKey.EXTERNAL_LINK_ID, externalId));
				for (CaseLinkShareDraft share : shares == null ? List.<CaseLinkShareDraft>of() : shares)
					auditCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, caseId, share.contactId(), EntityActionAuditEvent.Action.ADDED, null);
				CaseTimelineWriter.append(con,caseId,shaleClientId,actorUserId,CaseTimelineWriter.CASE_LINK_CREATED,
						"added the link '"+safeTimelineLabel(displayName,"Link")+"'",null);
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("Failed to create case link with shares", e);
		}
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
				if (sameCaseLinkValues(existing, linkTypeId, displayName, url, description, primary, notes, sortOrder)) {
					con.rollback();
					return existing;
				}
				updateExternalLinkRow(con, shaleClientId, actorUserId, externalLinkId, linkTypeId, displayName, url,
						description, expectedExternalLinkRowVer);
				updateCaseLinkRow(con, shaleClientId, actorUserId, caseLinkId, notes, sortOrder, expectedCaseLinkRowVer);
				applyPrimaryUpdate(con, shaleClientId, actorUserId, caseId, caseLinkId, existing.primary(), primary);
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.UPDATED, Map.of(
						EntityActionAuditEvent.MetadataKey.EXTERNAL_LINK_ID, externalLinkId));
				if (Boolean.TRUE.equals(primary) && !existing.primary())
					auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.PRIMARY_SET, Map.of(
							EntityActionAuditEvent.MetadataKey.NEW_PRIMARY_CASE_LINK_ID, caseLinkId));
				CaseTimelineWriter.append(con,caseId,shaleClientId,actorUserId,CaseTimelineWriter.CASE_LINK_UPDATED,
						"updated the link '"+safeTimelineLabel(displayName,"Link")+"'",null);
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
			byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer, List<CaseLinkShareDraft> adds, List<CaseLinkShareUpdate> updates,
			List<CaseLinkShareRemoval> removals) {
		requireRowVer(expectedCaseLinkRowVer, "expectedCaseLinkRowVer");
		requireRowVer(expectedExternalLinkRowVer, "expectedExternalLinkRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);
				CaseLinkDto existing = validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, externalLinkId);
				validateShareDraftContacts(con, shaleClientId, adds);
				validateShareUpdatesAndRemovals(con, shaleClientId, caseLinkId, updates, removals);
				if (sameCaseLinkValues(existing, linkTypeId, displayName, url, description, primary, notes, sortOrder)
						&& (adds == null || adds.isEmpty()) && (updates == null || updates.isEmpty())
						&& (removals == null || removals.isEmpty())) {
					con.rollback();
					return existing;
				}
				updateExternalLinkRow(con, shaleClientId, actorUserId, externalLinkId, linkTypeId, displayName, url, description, expectedExternalLinkRowVer);
				updateCaseLinkRow(con, shaleClientId, actorUserId, caseLinkId, notes, sortOrder, expectedCaseLinkRowVer);
				for (CaseLinkShareDraft share : adds == null ? List.<CaseLinkShareDraft>of() : adds)
					insertCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.contactId(), share.sharedAt(), share.notes());
				for (CaseLinkShareUpdate share : updates == null ? List.<CaseLinkShareUpdate>of() : updates)
					updateCaseLinkShareRow(con, shaleClientId, actorUserId, caseLinkId, share.caseLinkShareId(), share.contactId(), share.sharedAt(), share.notes(), share
							.expectedRowVer());
				for (CaseLinkShareRemoval share : removals == null ? List.<CaseLinkShareRemoval>of() : removals)
					softDeleteCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, share.caseLinkShareId(), share.expectedRowVer());
				applyPrimaryUpdate(con, shaleClientId, actorUserId, caseId, caseLinkId, existing.primary(), primary);
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.UPDATED, Map.of(
						EntityActionAuditEvent.MetadataKey.EXTERNAL_LINK_ID, externalLinkId));
				for (CaseLinkShareDraft share : adds == null ? List.<CaseLinkShareDraft>of() : adds)
					auditCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, caseId, share.contactId(), EntityActionAuditEvent.Action.ADDED, null);
				for (CaseLinkShareUpdate share : updates == null ? List.<CaseLinkShareUpdate>of() : updates)
					auditCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, caseId, share.contactId(), EntityActionAuditEvent.Action.UPDATED, share.caseLinkShareId());
				for (CaseLinkShareRemoval share : removals == null ? List.<CaseLinkShareRemoval>of() : removals)
					auditCaseLinkShare(con, shaleClientId, actorUserId, caseLinkId, caseId, null, EntityActionAuditEvent.Action.REMOVED, share.caseLinkShareId());
				if (Boolean.TRUE.equals(primary) && !existing.primary())
					auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.PRIMARY_SET, Map.of(
							EntityActionAuditEvent.MetadataKey.NEW_PRIMARY_CASE_LINK_ID, caseLinkId));
				CaseTimelineWriter.append(con,caseId,shaleClientId,actorUserId,CaseTimelineWriter.CASE_LINK_UPDATED,
						"updated the link '"+safeTimelineLabel(displayName,"Link")+"'",null);
				con.commit();
				return findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("Failed to update case link with shares", e);
		}
	}

	public CaseLinkDto setPrimaryCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, shaleClientId, caseId);
				validateActorForTenant(con, shaleClientId, actorUserId);
				validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, null);
				Long previousPrimary = findCurrentPrimaryCaseLinkId(con, shaleClientId, caseId);
				if (Objects.equals(previousPrimary, caseLinkId)) {
					con.rollback();
					return validateCaseLinkForTenant(con, shaleClientId, caseId, caseLinkId, null);
				}
				setOnlyPrimary(con, shaleClientId, caseId, caseLinkId, actorUserId);
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.PRIMARY_SET, previousPrimary == null ? Map.of(
						EntityActionAuditEvent.MetadataKey.NEW_PRIMARY_CASE_LINK_ID, caseLinkId)
						: Map.of(EntityActionAuditEvent.MetadataKey.PREVIOUS_PRIMARY_CASE_LINK_ID, previousPrimary, EntityActionAuditEvent.MetadataKey.NEW_PRIMARY_CASE_LINK_ID,
								caseLinkId));
				CaseLinkDto selected = findCaseLinkDto(con, shaleClientId, caseId, caseLinkId);
				CaseTimelineWriter.append(con, caseId, shaleClientId, actorUserId, CaseTimelineWriter.CASE_LINK_PRIMARY_CHANGED,
						"made the link '" + safeTimelineLabel(selected == null ? null : selected.displayName(), "Link") + "' primary", null);
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
				List<Long> currentOrder = active.stream()
						.sorted(Comparator.comparingInt(CaseLinkDto::sortOrder).thenComparingLong(CaseLinkDto::caseLinkId))
						.map(CaseLinkDto::caseLinkId).toList();
				if (currentOrder.equals(ids)) {
					con.rollback();
					return active;
				}
				int order = 0;
				for (Long id : ids) {
					updateCaseLinkSortOrder(con, shaleClientId, caseId, actorUserId, id, order++);
				}
				auditCaseLink(con, shaleClientId, actorUserId, caseId, caseId, EntityActionAuditEvent.Action.REORDERED, Map.of(
						EntityActionAuditEvent.MetadataKey.REORDERED_LINK_COUNT, ids.size()));
				CaseTimelineWriter.append(con, caseId, shaleClientId, actorUserId, CaseTimelineWriter.CASE_LINKS_REORDERED,
						"reordered Case Links", null);
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
				auditCaseLink(con, shaleClientId, actorUserId, caseLinkId, caseId, EntityActionAuditEvent.Action.DELETED, Map.of(
						EntityActionAuditEvent.MetadataKey.EXTERNAL_LINK_ID, dto.externalLinkId()));
				CaseTimelineWriter.append(con,caseId,shaleClientId,actorUserId,CaseTimelineWriter.CASE_LINK_REMOVED,
						"removed the link '"+safeTimelineLabel(dto.displayName(),"Link")+"'",null);
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

	private static String safeTimelineLabel(String value,String fallback){String v=value==null?"":value.trim();return v.isBlank()?fallback:v.replace("'","’");}

	private static String normalizeTimelineValue(String value) {
		String normalized = value == null ? "" : value.trim();
		return normalized.isBlank() ? null : normalized;
	}

	private static boolean sameCaseLinkValues(CaseLinkDto existing, int linkTypeId, String displayName,
			String url, String description, Boolean primary, String notes, Integer sortOrder) {
		return existing != null
				&& existing.linkTypeId() == linkTypeId
				&& Objects.equals(normalizeTimelineValue(existing.displayName()), normalizeTimelineValue(displayName))
				&& Objects.equals(normalizeTimelineValue(existing.url()), normalizeTimelineValue(url))
				&& Objects.equals(normalizeTimelineValue(existing.description()), normalizeTimelineValue(description))
				&& (primary == null || existing.primary() == primary)
				&& Objects.equals(normalizeTimelineValue(existing.notes()), normalizeTimelineValue(notes))
				&& (sortOrder == null || existing.sortOrder() == sortOrder);
	}

	private static String contactTimelineLabel(Connection con, int tenant, int contactId) throws SQLException {
		String sql = "SELECT COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(COALESCE(FirstName,''),' ',COALESCE(LastName,'')))),''),"
				+ "NULLIF(LTRIM(RTRIM(Name)),'')) FROM dbo.Contacts WHERE Id=? AND ShaleClientId=? AND IsDeleted=0";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, contactId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? safeTimelineLabel(rs.getString(1), "Contact #" + contactId) : "Contact #" + contactId;
			}
		}
	}

	public List<CaseLinkContactOptionDto> searchCaseLinkShareContacts(int tenant, String query, int limit) {
		String q = query == null ? "" : query.trim();
		int resolvedLimit = limit <= 0 ? 25 : Math.min(limit, 100);
		String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
		String sql = """
				SELECT TOP (?) ct.Id AS ContactId,
				       %s AS DisplayName
				FROM dbo.Contacts ct
				WHERE ct.ShaleClientId = ? AND ISNULL(ct.IsDeleted, 0) = 0
				  AND %s IS NOT NULL
				  AND (? = '' OR LOWER(COALESCE(ct.Name,'') + ' ' + COALESCE(ct.FirstName,'') + ' ' + COALESCE(ct.LastName,'') + ' ' + COALESCE(ct.WorkName,'') + ' ' + COALESCE((SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id),'')) LIKE ?)
				ORDER BY DisplayName ASC, ct.Id ASC
				"""
				.formatted(caseLinkShareContactDisplayNameExpression("ct"), caseLinkShareContactDisplayNameExpression("ct"));
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, resolvedLimit);
			ps.setInt(2, tenant);
			ps.setString(3, q);
			ps.setString(4, like);
			try (ResultSet rs = ps.executeQuery()) {
				return mapCaseLinkContactOptions(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to search share contacts", e);
		}
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
			try (ResultSet rs = ps.executeQuery()) {
				return mapCaseLinkContactOptions(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list share contacts", e);
		}
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
			ps.setLong(1, caseId);
			ps.setInt(2, tenant);
			ps.setInt(3, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				return mapCaseLinkContactOptions(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list CaseParties-backed case share contacts", e);
		}
	}

	/**
	 * Uses the same authoritative CaseParties/tenant/deletion rules as Shared With, for both
	 * entity kinds.
	 */
	public List<CasePartyEntityOptionDto> listRequestedFromCaseParties(long caseId, int tenant) {
		String contactName = caseLinkShareContactDisplayNameExpression("ct");
		String sql = """
				SELECT EntityType, EntityId, DisplayName, Email, Phone, OrganizationTypeName
				FROM (
				  SELECT 'contact' EntityType, ct.Id EntityId, %s DisplayName,
				         (SELECT TOP(1) e.EmailAddress FROM dbo.ContactEmailAddresses e WHERE e.ContactId=ct.Id AND e.ShaleClientId=ct.ShaleClientId AND e.IsDeleted=0 ORDER BY e.IsPrimary DESC,e.SortOrder,e.Id) Email,
				         (SELECT TOP(1) p.DisplayNumber FROM dbo.ContactPhoneNumbers p WHERE p.ContactId=ct.Id AND p.ShaleClientId=ct.ShaleClientId AND p.IsDeleted=0 ORDER BY p.IsPrimary DESC,p.SortOrder,p.Id) Phone,
				         CAST(NULL AS nvarchar(255)) OrganizationTypeName
				  FROM dbo.CaseParties cp JOIN dbo.Cases c ON c.Id=cp.CaseId
				  JOIN dbo.Contacts ct ON ct.Id=cp.ContactId
				  WHERE cp.CaseId=? AND c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
				    AND ct.ShaleClientId=? AND ISNULL(ct.IsDeleted,0)=0 AND %s IS NOT NULL
				  UNION
				  SELECT 'organization', org.Id, NULLIF(LTRIM(RTRIM(org.Name)),''), NULL, NULL, ot.Name
				  FROM dbo.CaseParties cp JOIN dbo.Cases c ON c.Id=cp.CaseId
				  JOIN dbo.Organizations org ON org.Id=cp.OrganizationId
				  LEFT JOIN dbo.OrganizationTypes ot ON ot.OrganizationTypeId=org.OrganizationTypeId AND ot.ShaleClientId=org.ShaleClientId
				  WHERE cp.CaseId=? AND c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
				    AND org.ShaleClientId=? AND ISNULL(org.IsDeleted,0)=0 AND NULLIF(LTRIM(RTRIM(org.Name)),'') IS NOT NULL
				) eligible
				GROUP BY EntityType, EntityId, DisplayName, Email, Phone, OrganizationTypeName
				ORDER BY EntityType, DisplayName, EntityId
				""".formatted(contactName, contactName);
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setLong(1, caseId);
			ps.setInt(2, tenant);
			ps.setInt(3, tenant);
			ps.setLong(4, caseId);
			ps.setInt(5, tenant);
			ps.setInt(6, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				List<CasePartyEntityOptionDto> out = new ArrayList<>();
				while (rs.next())
					out.add(new CasePartyEntityOptionDto(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list requested-from CaseParties", e);
		}
	}

	private static String caseLinkShareContactDisplayNameExpression(String alias) {
		return "COALESCE(NULLIF(LTRIM(RTRIM(" + alias + ".Name)), ''), "
				+ "NULLIF(LTRIM(RTRIM(CONCAT(" + alias + ".FirstName, ' ', " + alias + ".LastName))), ''), "
				+ "NULLIF(LTRIM(RTRIM(" + alias + ".WorkName)), ''))";
	}

	private static List<CaseLinkContactOptionDto> mapCaseLinkContactOptions(ResultSet rs) throws SQLException {
		List<CaseLinkContactOptionDto> out = new ArrayList<>();
		while (rs.next())
			out.add(new CaseLinkContactOptionDto(rs.getInt("ContactId"), rs.getString("DisplayName")));
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
						links.add(link);
						ids.add(link.caseLinkId());
						caseIds.add(rs.getLong("SharedCaseId"));
						names.add(rs.getString("SharedCaseDisplayName"));
					}
					if (links.isEmpty())
						logContactSharedLinkJoinStages(con, shaleClientId, contactId, 0, startedNanos);
					Map<Long, List<CaseLinkShareDto>> shares = listCaseLinkSharesForLinks(con, shaleClientId, ids);
					for (int i = 0; i < links.size(); i++)
						rows.add(new ContactSharedCaseLinkDto(caseIds.get(i), names.get(i), withShares(links.get(i), shares.get(links.get(i).caseLinkId()))));
					return List.copyOf(rows);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case links shared with contact", e);
		}
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
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list case link shares", e);
		}
	}

	public CaseLinkShareDto addCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, int contactId, LocalDateTime sharedAt, String notes) {
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, tenant, caseId);
				validateActorForTenant(con, tenant, actor);
				validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null);
				validateActiveContactForTenant(con, tenant, contactId);
				long shareId = insertCaseLinkShare(con, tenant, actor, caseLinkId, contactId, sharedAt, notes);
				auditCaseLinkShare(con, tenant, actor, caseLinkId, caseId, contactId, EntityActionAuditEvent.Action.ADDED, shareId);
				CaseTimelineWriter.append(con, caseId, tenant, actor, CaseTimelineWriter.CASE_LINK_SHARE_ADDED,
						"shared a Case Link with " + contactTimelineLabel(con, tenant, contactId), null);
				con.commit();
				return findCaseLinkShare(con, tenant, caseId, caseLinkId, shareId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	public CaseLinkShareDto updateCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, long shareId, int contactId, LocalDateTime sharedAt, String notes,
			byte[] rowVer) {
		requireRowVer(rowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, tenant, caseId);
				validateActorForTenant(con, tenant, actor);
				validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null);
				validateActiveContactForTenant(con, tenant, contactId);
				CaseLinkShareDto existing = findCaseLinkShare(con, tenant, caseId, caseLinkId, shareId);
				if (existing != null && existing.contactId() == contactId && Objects.equals(existing.sharedAt(), sharedAt)
						&& Objects.equals(normalizeTimelineValue(existing.notes()), normalizeTimelineValue(notes))) {
					con.rollback();
					return existing;
				}
				updateCaseLinkShareRow(con, tenant, actor, caseLinkId, shareId, contactId, sharedAt, notes, rowVer);
				auditCaseLinkShare(con, tenant, actor, caseLinkId, caseId, contactId, EntityActionAuditEvent.Action.UPDATED, shareId);
				CaseTimelineWriter.append(con, caseId, tenant, actor, CaseTimelineWriter.CASE_LINK_SHARE_UPDATED,
						"updated Case Link sharing for " + contactTimelineLabel(con, tenant, contactId), null);
				con.commit();
				return findCaseLinkShare(con, tenant, caseId, caseLinkId, shareId);
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw translateSql("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	public void removeCaseLinkShare(int tenant, int actor, long caseId, long caseLinkId, long shareId, byte[] rowVer) {
		requireRowVer(rowVer, "expectedRowVer");
		try (Connection con = db.requireConnection()) {
			con.setAutoCommit(false);
			try {
				validateCaseForTenant(con, tenant, caseId);
				validateActorForTenant(con, tenant, actor);
				validateCaseLinkForTenant(con, tenant, caseId, caseLinkId, null);
				softDeleteCaseLinkShare(con, tenant, actor, caseLinkId, shareId, rowVer);
				auditCaseLinkShare(con, tenant, actor, caseLinkId, caseId, null, EntityActionAuditEvent.Action.REMOVED, shareId);
				CaseTimelineWriter.append(con, caseId, tenant, actor, CaseTimelineWriter.CASE_LINK_SHARE_REMOVED,
						"removed a Case Link share", null);
				con.commit();
			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RuntimeException("The change could not be saved because its audit record could not be completed.", e);
		}
	}

	private void auditLinkType(Connection con, int tenant, int actor, int linkTypeId, EntityActionAuditEvent.Action action, boolean active) throws SQLException {
		entityActionAuditDao.append(con, EntityActionAuditEvent.now(tenant, actor, EntityActionAuditEvent.EntityType.LINK_TYPE, linkTypeId, action, null, null,
				Map.of(EntityActionAuditEvent.MetadataKey.LINK_TYPE_ID, linkTypeId, EntityActionAuditEvent.MetadataKey.ACTIVE, active)));
	}

	private void auditCaseLink(Connection con, int tenant, int actor, long caseLinkId, long caseId, EntityActionAuditEvent.Action action,
			Map<EntityActionAuditEvent.MetadataKey, ?> metadata) throws SQLException {
		Map<EntityActionAuditEvent.MetadataKey, Object> safe = new java.util.EnumMap<>(EntityActionAuditEvent.MetadataKey.class);
		safe.put(EntityActionAuditEvent.MetadataKey.CASE_ID, caseId);
		safe.put(EntityActionAuditEvent.MetadataKey.CASE_LINK_ID, caseLinkId);
		if (metadata != null)
			safe.putAll(metadata);
		entityActionAuditDao.append(con, EntityActionAuditEvent.now(tenant, actor, EntityActionAuditEvent.EntityType.CASE_LINK, caseLinkId, action, null, null, safe));
	}

	private void auditCaseLinkShare(Connection con, int tenant, int actor, long caseLinkId, long caseId, Integer contactId, EntityActionAuditEvent.Action action, Long shareId)
			throws SQLException {
		long entityId = shareId == null ? caseLinkId : shareId;
		Map<EntityActionAuditEvent.MetadataKey, Object> safe = new java.util.EnumMap<>(EntityActionAuditEvent.MetadataKey.class);
		safe.put(EntityActionAuditEvent.MetadataKey.CASE_ID, caseId);
		safe.put(EntityActionAuditEvent.MetadataKey.CASE_LINK_ID, caseLinkId);
		if (shareId != null)
			safe.put(EntityActionAuditEvent.MetadataKey.CASE_LINK_SHARE_ID, shareId);
		if (contactId != null)
			safe.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID, contactId);
		entityActionAuditDao.append(con, EntityActionAuditEvent.now(tenant, actor, EntityActionAuditEvent.EntityType.CASE_LINK_SHARE, entityId, action,
				EntityActionAuditEvent.EntityType.CASE_LINK, caseLinkId, safe));
	}

	private Long findCurrentPrimaryCaseLinkId(Connection con, int tenant, long caseId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT TOP (1) Id FROM dbo.CaseLinks WHERE ShaleClientId = ? AND CaseId = ? AND IsDeleted = 0 AND IsPrimary = 1 ORDER BY Id")) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getLong(1) : null;
			}
		}
	}

	private static CaseLinkDto withShares(CaseLinkDto link, List<CaseLinkShareDto> shares) {
		return new CaseLinkDto(link.caseLinkId(), link.externalLinkId(), link.caseId(), link.shaleClientId(), link.linkTypeId(), link.linkTypeName(), link.linkTypeColor(), link
				.linkTypeSystemKey(), link.displayName(), link.url(), link.description(), link.primary(), link.notes(), link.sortOrder(), link.createdAt(), link.updatedAt(), link
						.caseLinkRowVer(), link.externalLinkRowVer(), shares == null ? List.of() : shares);
	}

	private List<CaseLinkShareDto> listCaseLinkShares(Connection con, long caseId, long caseLinkId, int tenant) throws SQLException {
		return listCaseLinkSharesForLinks(con, tenant, List.of(caseLinkId)).getOrDefault(caseLinkId, List.of());
	}

	private Map<Long, List<CaseLinkShareDto>> listCaseLinkSharesForLinks(Connection con, int tenant, List<Long> linkIds) throws SQLException {
		if (linkIds == null || linkIds.isEmpty())
			return Map.of();
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
			ps.setInt(1, tenant);
			int i = 2;
			for (Long id : linkIds)
				ps.setLong(i++, id);
			try (ResultSet rs = ps.executeQuery()) {
				Map<Long, List<CaseLinkShareDto>> out = new LinkedHashMap<>();
				while (rs.next())
					out.computeIfAbsent(rs.getLong("CaseLinkId"), k -> new ArrayList<>()).add(mapCaseLinkShareDto(rs));
				return out;
			}
		}
	}

	private CaseLinkShareDto findCaseLinkShare(Connection con, int tenant, long caseId, long caseLinkId, long shareId) throws SQLException {
		List<CaseLinkShareDto> shares = listCaseLinkShares(con, caseId, caseLinkId, tenant);
		return shares.stream().filter(s -> s.caseLinkShareId() == shareId).findFirst().orElse(null);
	}

	private static CaseLinkShareDto mapCaseLinkShareDto(ResultSet rs) throws SQLException {
		String display = rs.getString("ContactDisplayName");
		if (display == null || display.isBlank())
			display = rs.getString("ContactName");
		boolean unavailable = rs.getBoolean("ContactIsDeleted") || display == null || display.isBlank();
		if (display == null || display.isBlank())
			display = "Contact #" + rs.getInt("ContactId");
		if (unavailable && !display.contains("unavailable"))
			display += " (unavailable)";
		return new CaseLinkShareDto(rs.getLong("CaseLinkShareId"), rs.getInt("ShaleClientId"), rs.getLong("CaseLinkId"), rs.getInt("ContactId"), display, unavailable,
				toLocalDateTime(rs.getTimestamp("SharedAt")), rs.getString("Notes"), rs.getBoolean("IsDeleted"), rs.getInt("CreatedByUserId"), toLocalDateTime(rs.getTimestamp(
						"CreatedAt")), toLocalDateTime(rs.getTimestamp("UpdatedAt")), rs.getBytes("RowVer"));
	}

	private void validateActiveContactForTenant(Connection con, int tenant, int contactId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Contacts WHERE Id = ? AND ShaleClientId = ? AND IsDeleted = 0")) {
			ps.setInt(1, contactId);
			ps.setInt(2, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new IllegalArgumentException("Contact is not available for this tenant.");
			}
		}
	}

	private void softDeleteCaseLinkShare(Connection con, int tenant, int actor, long caseLinkId, long shareId, byte[] rowVer) throws SQLException {
		String sql = """
				UPDATE dbo.CaseLinkShares SET IsDeleted = 1, DeletedAt = SYSUTCDATETIME(), DeletedByUserId = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0 AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setInt(2, actor);
			ps.setLong(3, shareId);
			ps.setInt(4, tenant);
			ps.setLong(5, caseLinkId);
			ps.setBytes(6, rowVer);
			if (ps.executeUpdate() != 1)
				throw new IllegalStateException("Optimistic conflict: case link share changed.");
		}
	}

	private long insertCaseLinkShare(Connection con, int tenant, int actor, long caseLinkId, int contactId, LocalDateTime sharedAt, String notes) throws SQLException {
		String sql = """
				INSERT INTO dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId, SharedAt, Notes, IsDeleted, CreatedByUserId, CreatedAt)
				VALUES (?, ?, ?, ?, ?, 0, ?, SYSUTCDATETIME())
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseLinkId);
			ps.setInt(3, contactId);
			ps.setTimestamp(4, Timestamp.valueOf(sharedAt));
			ps.setString(5, notes);
			ps.setInt(6, actor);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next())
					return rs.getLong(1);
			}
		}
		throw new RuntimeException("Failed to create case link share.");
	}

	private void updateCaseLinkShareRow(Connection con, int tenant, int actor, long caseLinkId, long shareId, int contactId, LocalDateTime sharedAt, String notes, byte[] rowVer)
			throws SQLException {
		requireRowVer(rowVer, "expectedRowVer");
		String sql = """
				UPDATE dbo.CaseLinkShares SET ContactId = ?, SharedAt = ?, Notes = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0 AND RowVer = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, contactId);
			ps.setTimestamp(2, Timestamp.valueOf(sharedAt));
			ps.setString(3, notes);
			ps.setInt(4, actor);
			ps.setLong(5, shareId);
			ps.setInt(6, tenant);
			ps.setLong(7, caseLinkId);
			ps.setBytes(8, rowVer);
			if (ps.executeUpdate() != 1)
				throw new IllegalStateException("Optimistic conflict: case link share changed.");
		}
	}

	private void validateShareDraftContacts(Connection con, int tenant, List<CaseLinkShareDraft> shares) throws SQLException {
		for (CaseLinkShareDraft share : shares == null ? List.<CaseLinkShareDraft>of() : shares)
			validateActiveContactForTenant(con, tenant, share.contactId());
	}

	private void validateShareUpdatesAndRemovals(Connection con, int tenant, long caseLinkId, List<CaseLinkShareUpdate> updates, List<CaseLinkShareRemoval> removals)
			throws SQLException {
		for (CaseLinkShareUpdate share : updates == null ? List.<CaseLinkShareUpdate>of() : updates) {
			validateActiveContactForTenant(con, tenant, share.contactId());
			validateActiveShareForTenant(con, tenant, caseLinkId, share.caseLinkShareId());
		}
		for (CaseLinkShareRemoval share : removals == null ? List.<CaseLinkShareRemoval>of() : removals)
			validateActiveShareForTenant(con, tenant, caseLinkId, share.caseLinkShareId());
	}

	private void validateActiveShareForTenant(Connection con, int tenant, long caseLinkId, long shareId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.CaseLinkShares WHERE Id = ? AND ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0")) {
			ps.setLong(1, shareId);
			ps.setInt(2, tenant);
			ps.setLong(3, caseLinkId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new IllegalArgumentException("Case link share is not available for this tenant.");
			}
		}
	}

	private void softDeleteCaseLinkSharesForLink(Connection con, int tenant, int actor, long caseLinkId) throws SQLException {
		String sql = """
				UPDATE dbo.CaseLinkShares SET IsDeleted = 1, DeletedAt = SYSUTCDATETIME(), DeletedByUserId = ?, UpdatedByUserId = ?, UpdatedAt = SYSUTCDATETIME()
				WHERE ShaleClientId = ? AND CaseLinkId = ? AND IsDeleted = 0
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setInt(2, actor);
			ps.setInt(3, tenant);
			ps.setLong(4, caseLinkId);
			ps.executeUpdate();
		}
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
				while (rs.next())
					out.add(mapCaseLinkDto(rs));
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

	private boolean hasActivePrimaryCaseLink(Connection con, int tenant, long caseId) throws SQLException {
		String sql = """
				SELECT TOP (1) 1
				FROM dbo.CaseLinks
				WHERE ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				  AND IsPrimary = 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, tenant);
			ps.setLong(2, caseId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void clearActivePrimaryForCreate(Connection con, int tenant, long caseId, int actor) throws SQLException {
		String sql = """
				UPDATE dbo.CaseLinks
				SET IsPrimary = 0,
				    UpdatedByUserId = ?,
				    UpdatedAt = SYSUTCDATETIME()
				WHERE ShaleClientId = ?
				  AND CaseId = ?
				  AND IsDeleted = 0
				  AND IsPrimary = 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, actor);
			ps.setInt(2, tenant);
			ps.setLong(3, caseId);
			ps.executeUpdate();
		}
	}

	private void ensurePrimaryCandidate(Connection con, int tenant, long caseId, int actor) throws SQLException {
		if (hasActivePrimaryCaseLink(con, tenant, caseId))
			return;
		Long candidate = findNextPrimaryCandidate(con, tenant, caseId, -1);
		if (candidate != null)
			setOnlyPrimary(con, tenant, caseId, candidate, actor);
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
				return new IllegalStateException("The Primary Link changed while you were saving. The Links list has been refreshed; please review it and try again.", e);
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

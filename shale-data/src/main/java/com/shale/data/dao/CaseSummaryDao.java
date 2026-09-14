package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseSummaryProjection;
import com.shale.core.dto.CaseStatusReportRowDto;
import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.semantics.RoleSemantics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The authoritative SQL boundary for shared, tenant-scoped Case summaries. */
public final class CaseSummaryDao {
	private static final Logger LOG = LoggerFactory.getLogger(CaseSummaryDao.class);
	public enum DeletedState { ACTIVE, DELETED, ALL }
	public enum Order { NAME_ASC, UPDATED_DESC }
	/** Closed allow-list shared by the Cases UI and this SQL boundary. */
	public enum GridOrder {
		INTAKE_NEWEST, INTAKE_OLDEST, STATUTE_SOONEST, STATUTE_LATEST,
		CASE_NAME_ASC, CASE_NAME_DESC, RESPONSIBLE_ATTORNEY_ASC,
		RESPONSIBLE_ATTORNEY_DESC, CASE_STATUS_ASC, CASE_STATUS_DESC
	}
	public enum GridStatusMode { UNRESTRICTED, SELECTED, NO_STATUS }

	/** Consumer-specific, PHI-bearing shape. It is deliberately not part of CaseSummaryProjection. */
	public record CaseGridRow(CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate, LocalDate dateOfIncident, LocalDate tortClaimsNoticeDeadline,
			String practiceAreaColor, Boolean nonEngagementLetterSent, String clientName,
			String opposingPartiesName, String latestCaseUpdate, String description) { }

	public record GridPage(List<CaseGridRow> items, int page, int pageSize, long total) { }
	private static final int EXPORT_BATCH_SIZE = 500;

	/** Assigned-Case board enrichment; sensitive card fields remain outside the shared projection. */
	public record CaseBoardRow(CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline,
			String practiceAreaColor, Boolean nonEngagementLetterSent) { }

	/** Desktop global-search shape; only fields consumed by its existing compact Case card. */
	public record SearchCaseRow(CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline,
			String practiceAreaColor, Boolean nonEngagementLetterSent) { }

	/** Server/web Case-search shape, kept separate from the PHI-minimized shared summary. */
	public record ServerCaseRow(CaseSummaryProjection summary, LocalDate intakeDate, LocalDate injuryDate,
			LocalDate statuteDate, LocalDate tortDate, String practiceAreaColor, String description,
			Integer callerContactId, String callerName, Integer clientContactId, String clientName,
			Integer opposingCounselContactId, String opposingCounselName) { }

	/** Calendar selector/card data; Calendar event identity and scheduling stay outside this row. */
	public record CalendarCaseRow(CaseSummaryProjection summary, Boolean nonEngagementLetterSent) {
		public CalendarCaseRow { Objects.requireNonNull(summary, "summary"); }
	}

	/** PHI-bearing dates needed only by the bounded desktop Case document composition. */
	public record DocumentCaseRow(CaseSummaryProjection summary, LocalDate dateOfInjury,
			LocalDate statuteOfLimitations) {
		public DocumentCaseRow { Objects.requireNonNull(summary, "summary"); }
	}

	/** Case-party relationship metadata composed with the authoritative Case summary. */
	public record RelatedCaseRow(long relationshipId, int partyRoleId, CaseSummaryProjection summary,
			LocalDate intakeDate, LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline,
			String practiceAreaColor, Boolean nonEngagementLetterSent, String partyRoleName,
			String side, boolean primary, String notes) {
		public RelatedCaseRow { Objects.requireNonNull(summary, "summary"); }
	}

	/** Deleted-search card data plus the concurrency token consumed by the lifecycle restore command. */
	public record DeletedCaseRow(CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline,
			String practiceAreaColor, Boolean nonEngagementLetterSent, byte[] rowVer) {
		public DeletedCaseRow {
			Objects.requireNonNull(summary, "summary");
			if (rowVer == null || rowVer.length == 0) throw new IllegalArgumentException("rowVer is required");
			rowVer = rowVer.clone();
		}
		@Override public byte[] rowVer() { return rowVer.clone(); }
	}

	/** PHI-bearing detail used only by the desktop Case-status report. */
	public record ReportCaseRow(CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate deniedDate, LocalDate closedDate, LocalDate dateOfInjury, String description,
			LocalDate statuteOfLimitations, LocalDate tortNoticeDeadline) {
		public ReportCaseRow { Objects.requireNonNull(summary, "summary"); }

		public ReportCaseDetailRowDto toDetailRow() {
			return new ReportCaseDetailRowDto(Math.toIntExact(summary.caseId()), summary.caseName(), summary.createdAt(),
					intakeDate, deniedDate, closedDate, dateOfInjury, description, statuteOfLimitations,
					tortNoticeDeadline, summary.updatedAt(), summary.responsibleAttorneyName());
		}
	}

	private final DbSessionProvider db;

	public CaseSummaryDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	/** Status-grain aggregate for the desktop report; eligibility is shared with its detail query. */
	public List<CaseStatusReportRowDto> listActiveStatusReport(int requestedTenantId, LocalDate startDate,
			LocalDate endDate, List<Integer> selectedStatusIds) {
		Set<Integer> statuses = normalizedPositiveIds(selectedStatusIds);
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		if (statuses.isEmpty()) return List.of();
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			verifyStatuses(con, requestedTenantId, statuses);
			String placeholders = String.join(",", java.util.Collections.nCopies(statuses.size(), "?"));
			String sql = """
				SELECT s.Id,s.Name,s.SystemKey,s.LifecycleKey,s.Color,s.SortOrder,ISNULL(totals.CaseCount,0) CaseCount
				FROM dbo.Statuses s
				OUTER APPLY (SELECT COUNT_BIG(1) CaseCount FROM dbo.Cases c
				%s
				OUTER APPLY (SELECT MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				 WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0 AND status_row.StatusId=s.Id
				 AND (? IS NULL OR dates.IntakeDate>=?) AND (? IS NULL OR dates.IntakeDate<DATEADD(day,1,?))) totals
				WHERE s.Id IN (%s) AND (s.ShaleClientId=? OR s.ShaleClientId IS NULL)
				ORDER BY s.SortOrder ASC,s.Name ASC,s.Id ASC
				""".formatted(statusApplySql(), placeholders);
			try (PreparedStatement ps=con.prepareStatement(sql)) {
				int i=1; ps.setInt(i++,requestedTenantId); i=bindNullableDateTwice(ps,i,startDate);
				i=bindNullableDateTwice(ps,i,endDate); for (Integer id:statuses) ps.setInt(i++,id);
				ps.setInt(i,requestedTenantId);
				List<CaseStatusReportRowDto> rows=new ArrayList<>();
				try(ResultSet rs=ps.executeQuery()){while(rs.next()) rows.add(new CaseStatusReportRowDto(rs.getInt("Id"),
					rs.getString("Name"),rs.getString("SystemKey"),rs.getString("LifecycleKey"),rs.getString("Color"),
					rs.getInt("SortOrder"),rs.getLong("CaseCount")));} return List.copyOf(rows);
			}
		} catch(SQLException e){throw new RuntimeException("Failed to load authoritative Case status report",e);}
	}

	/** One authoritative Case-summary row per report result; no child join can multiply rows. */
	public List<ReportCaseRow> listActiveStatusReportCases(int requestedTenantId, int statusId,
			LocalDate startDate, LocalDate endDate) {
		if(requestedTenantId<=0||statusId<=0) throw new IllegalArgumentException("tenant and statusId must be > 0");
		try(Connection con=db.requireConnection()){
			verifyTenant(con,requestedTenantId); verifyStatuses(con,requestedTenantId,Set.of(statusId));
			String sql="""
				SELECT c.Id,c.ShaleClientId,c.CaseNumber,c.Name,status_row.StatusId,status_row.SystemKey StatusSystemKey,
				 status_row.LifecycleKey StatusLifecycleKey,status_row.StatusName,status_row.StatusColor,
				 c.PracticeAreaId,pa.Name PracticeAreaName,attorney.UserId ResponsibleAttorneyId,
				 attorney_user.DisplayName ResponsibleAttorneyName,attorney_user.Color ResponsibleAttorneyColor,
				 assistant.UserId PrimaryLegalAssistantId,assistant_user.DisplayName PrimaryLegalAssistantName,
				 assistant_user.Color PrimaryLegalAssistantColor,c.CreatedAt,c.UpdatedAt,CAST(0 AS bit) IsDeleted,
				 dates.IntakeDate,c.DeniedDate,c.ClosedDate,dates.InjuryDate,c.Description,dates.StatuteDate,dates.TortDate
				FROM dbo.Cases c %s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color FROM dbo.Users u
				 WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color FROM dbo.Users u
				 WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN t.SystemKey='date_of_injury' THEN CAST(cd.StartsAt AS date) END) InjuryDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=t.Id
				  AND m.IsActive=1 AND m.IsDeleted=0 AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0 AND status_row.StatusId=?
				 AND (? IS NULL OR dates.IntakeDate>=?) AND (? IS NULL OR dates.IntakeDate<DATEADD(day,1,?))
				ORDER BY dates.IntakeDate DESC,c.Id DESC
				""".formatted(statusApplySql());
			try(PreparedStatement ps=con.prepareStatement(sql)){int i=1;ps.setInt(i++,RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(i++,RoleSemantics.ROLE_LEGAL_ASSISTANT);ps.setInt(i++,requestedTenantId);ps.setInt(i++,statusId);
				i=bindNullableDateTwice(ps,i,startDate);bindNullableDateTwice(ps,i,endDate);List<ReportCaseRow> rows=new ArrayList<>();
				try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new ReportCaseRow(mapGridSummary(rs),localDate(rs,"IntakeDate"),
					localDate(rs,"DeniedDate"),localDate(rs,"ClosedDate"),localDate(rs,"InjuryDate"),rs.getString("Description"),
					localDate(rs,"StatuteDate"),localDate(rs,"TortDate")));}return List.copyOf(rows);}
		}catch(SQLException e){throw new RuntimeException("Failed to load authoritative Case status report details",e);}
	}

	/** Active Cases related to a tenant Contact through one authoritative CaseParties row. */
	public List<RelatedCaseRow> listActiveRelatedToContact(int requestedTenantId, int contactId) {
		return listActiveRelated(requestedTenantId, contactId, true);
	}

	/** Active Cases related to a tenant Organization through one authoritative CaseParties row. */
	public List<RelatedCaseRow> listActiveRelatedToOrganization(int requestedTenantId, int organizationId) {
		return listActiveRelated(requestedTenantId, organizationId, false);
	}

	private List<RelatedCaseRow> listActiveRelated(int tenantId, int entityId, boolean contact) {
		if (tenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		if (entityId <= 0) throw new IllegalArgumentException((contact ? "contactId" : "organizationId") + " must be > 0");
		String entityTable = contact ? "dbo.Contacts" : "dbo.Organizations";
		String entityColumn = contact ? "ContactId" : "OrganizationId";
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, tenantId);
			String sql = """
				SELECT cp.Id RelationshipId,cp.PartyRoleId,c.Id,c.ShaleClientId,c.CaseNumber,c.Name,
				 status_row.StatusId,status_row.SystemKey StatusSystemKey,status_row.LifecycleKey StatusLifecycleKey,
				 status_row.StatusName,status_row.StatusColor,c.PracticeAreaId,pa.Name PracticeAreaName,
				 attorney.UserId ResponsibleAttorneyId,attorney_user.DisplayName ResponsibleAttorneyName,
				 attorney_user.Color ResponsibleAttorneyColor,assistant.UserId PrimaryLegalAssistantId,
				 assistant_user.DisplayName PrimaryLegalAssistantName,assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt,c.UpdatedAt,CAST(0 AS bit) IsDeleted,pa.Color PracticeAreaColor,
				 dates.IntakeDate,dates.StatuteDate,dates.TortDate,c.NonEngagementLetterSent,
				 pr.Name PartyRoleName,cp.Side,ISNULL(cp.IsPrimary,0) IsPrimary,cp.Notes
				FROM dbo.CaseParties cp
				JOIN dbo.Cases c ON c.Id=cp.CaseId AND c.ShaleClientId=?
				JOIN %s entity ON entity.Id=cp.%s AND entity.ShaleClientId=c.ShaleClientId AND ISNULL(entity.IsDeleted,0)=0
				LEFT JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId
				 AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL)
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId
				 AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				WHERE cp.%s=? AND c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
				ORDER BY CASE WHEN ISNULL(cp.IsPrimary,0)=1 THEN 0 ELSE 1 END,c.Name ASC,c.Id ASC,cp.Id ASC
				""".formatted(entityTable, entityColumn, statusApplySql(), entityColumn);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, tenantId);
				ps.setInt(2, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(3, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(4, entityId);
				ps.setInt(5, tenantId);
				List<RelatedCaseRow> rows = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(new RelatedCaseRow(
						rs.getLong("RelationshipId"),rs.getInt("PartyRoleId"),mapGridSummary(rs),
						localDate(rs,"IntakeDate"),localDate(rs,"StatuteDate"),localDate(rs,"TortDate"),
						rs.getString("PracticeAreaColor"),(Boolean)rs.getObject("NonEngagementLetterSent"),
						rs.getString("PartyRoleName"),rs.getString("Side"),rs.getBoolean("IsPrimary"),rs.getString("Notes"))); }
				return List.copyOf(rows);
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to load authoritative related Cases", e); }
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
			String sql = summarySelectSql(deletedPredicate, orderBy);
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


	/** Authoritative active one-Case lookup for desktop Documents generation. */
	public DocumentCaseRow findActiveForDocuments(int requestedTenantId, long caseId) {
		if (requestedTenantId <= 0 || caseId <= 0) throw new IllegalArgumentException("requestedTenantId and caseId must be > 0");
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			try (PreparedStatement ps = con.prepareStatement(documentSelectSql())) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				ps.setLong(4, caseId);
				try (ResultSet rs = ps.executeQuery()) {
					return rs.next() ? new DocumentCaseRow(map(rs), localDate(rs, "InjuryDate"),
							localDate(rs, "StatuteDate")) : null;
				}
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to validate Documents Case summary", e); }
	}

	static String documentSelectSql() {
		String dates = """
			OUTER APPLY (
			 SELECT
			  MAX(CASE WHEN stored_type.SystemKey='date_of_injury' THEN CAST(cd.StartsAt AS date) END) InjuryDate,
			  MAX(CASE WHEN effective_sol.CaseDateTypeId IS NOT NULL THEN CAST(cd.StartsAt AS date) END) StatuteDate
			 FROM dbo.CaseDates cd
			 JOIN dbo.CaseDateTypes stored_type ON stored_type.Id=cd.CaseDateTypeId
			  AND (stored_type.ShaleClientId=c.ShaleClientId OR stored_type.ShaleClientId IS NULL)
			 OUTER APPLY (
			  SELECT role_mapping.CaseDateTypeId
			  FROM dbo.CaseDateTypeSemanticRoleMappings role_mapping
			  JOIN dbo.CaseDateTypes mapped_type ON mapped_type.Id=role_mapping.CaseDateTypeId
			  WHERE role_mapping.CaseDateTypeId=stored_type.Id
			   AND role_mapping.SemanticRoleKey='STATUTE_OF_LIMITATIONS'
			   AND role_mapping.IsActive=1 AND role_mapping.IsDeleted=0
			   AND mapped_type.IsActive=1 AND mapped_type.IsDeleted=0
			   AND (role_mapping.ShaleClientId=c.ShaleClientId OR role_mapping.ShaleClientId IS NULL)
			   AND (mapped_type.ShaleClientId=c.ShaleClientId OR mapped_type.ShaleClientId IS NULL)
			   AND NOT (role_mapping.ShaleClientId IS NULL AND EXISTS (
			    SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings tenant_mapping
			    JOIN dbo.CaseDateTypes tenant_type ON tenant_type.Id=tenant_mapping.CaseDateTypeId
			    WHERE tenant_mapping.ShaleClientId=c.ShaleClientId
			     AND tenant_mapping.SemanticRoleKey=role_mapping.SemanticRoleKey
			     AND tenant_mapping.IsActive=1 AND tenant_mapping.IsDeleted=0
			     AND tenant_type.ShaleClientId=c.ShaleClientId
			     AND tenant_type.IsActive=1 AND tenant_type.IsDeleted=0))
			 ) effective_sol
			 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0
			) document_dates
			""";
		return summarySelectSql("AND ISNULL(c.IsDeleted, 0) = 0 AND c.Id = ?", "c.Id ASC")
				.replace("c.NonEngagementLetterSent", "c.NonEngagementLetterSent, document_dates.InjuryDate, document_dates.StatuteDate")
				.replace("WHERE c.ShaleClientId = ?", dates + " WHERE c.ShaleClientId = ?");
	}

	/** Complete active Case selector snapshot for desktop Calendar, ordered by label then authoritative ID. */
	public List<CalendarCaseRow> listActiveForCalendar(int requestedTenantId) {
		return calendarCases(requestedTenantId, null);
	}

	/** Revalidates an event's tenant-scoped Case ID before rendering its related-Case card. */
	public CalendarCaseRow findActiveForCalendar(int requestedTenantId, long caseId) {
		if (caseId <= 0) throw new IllegalArgumentException("caseId must be > 0");
		List<CalendarCaseRow> rows = calendarCases(requestedTenantId, caseId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private List<CalendarCaseRow> calendarCases(int requestedTenantId, Long caseId) {
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			String predicate = "AND ISNULL(c.IsDeleted, 0) = 0" + (caseId == null ? "" : " AND c.Id = ?");
			try (PreparedStatement ps = con.prepareStatement(summarySelectSql(predicate,
					"LOWER(COALESCE(c.Name, '')) ASC, c.Id ASC"))) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				if (caseId != null) ps.setLong(4, caseId);
				List<CalendarCaseRow> rows = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) rows.add(new CalendarCaseRow(map(rs),
							(Boolean) rs.getObject("NonEngagementLetterSent")));
				}
				return List.copyOf(rows);
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to load authoritative Calendar Case summaries", e); }
	}

	private static String summarySelectSql(String extraPredicate, String orderBy) {
		return """
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
					       c.CreatedAt, c.UpdatedAt, ISNULL(c.IsDeleted, 0) AS IsDeleted,
					       c.NonEngagementLetterSent
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
					""".formatted(extraPredicate, orderBy);
	}

	/**
	 * Active desktop global Case search. Its established contract is a trimmed,
	 * case-insensitive literal substring of Case name, with no result cap.
	 */
	public List<SearchCaseRow> searchActiveByName(int requestedTenantId, String query) {
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		String normalized = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
		if (normalized.isBlank()) return List.of();
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			String sql = """
				SELECT c.Id,c.ShaleClientId,c.CaseNumber,c.Name,
				 status_row.StatusId,status_row.SystemKey StatusSystemKey,status_row.LifecycleKey StatusLifecycleKey,
				 status_row.StatusName,status_row.StatusColor,c.PracticeAreaId,pa.Name PracticeAreaName,
				 attorney.UserId ResponsibleAttorneyId,attorney_user.DisplayName ResponsibleAttorneyName,
				 attorney_user.Color ResponsibleAttorneyColor,assistant.UserId PrimaryLegalAssistantId,
				 assistant_user.DisplayName PrimaryLegalAssistantName,assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt,c.UpdatedAt,ISNULL(c.IsDeleted,0) IsDeleted,pa.Color PracticeAreaColor,
				 dates.IntakeDate,dates.StatuteDate,dates.TortDate,c.NonEngagementLetterSent
				FROM dbo.Cases c
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0 AND LOWER(COALESCE(c.Name,'')) LIKE ?
				ORDER BY c.Name ASC,c.Id ASC
				""".formatted(statusApplySql());
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				ps.setString(4, "%" + escapeLike(normalized) + "%");
				List<SearchCaseRow> rows = new ArrayList<>();
				try (ResultSet rs=ps.executeQuery()) { while (rs.next()) rows.add(new SearchCaseRow(
						mapGridSummary(rs), localDate(rs,"IntakeDate"), localDate(rs,"StatuteDate"),
						localDate(rs,"TortDate"), rs.getString("PracticeAreaColor"),
						(Boolean)rs.getObject("NonEngagementLetterSent"))); }
				return List.copyOf(rows);
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to search authoritative Case summaries", e); }
	}

	/** Bounded server search; one SQL statement replaces the former ID query plus N overview reads. */
	public List<ServerCaseRow> searchActiveForServer(int tenant, int actor, String query, int offset, int limit) {
		String normalized=query==null?"":query.strip().toLowerCase(java.util.Locale.ROOT);
		if(normalized.isBlank()) return List.of();
		return listActiveForServer(tenant,actor,normalized,offset,limit,null);
	}

	/** Bounded server My Cases projection; assignment membership is applied before paging. */
	public List<ServerCaseRow> listActiveAssignedForServer(int tenant,int actor,int assignedUserId,int limit) {
		if(actor!=assignedUserId) throw new IllegalArgumentException("assigned user must be the authenticated actor");
		return listActiveForServer(tenant,actor,null,0,limit,assignedUserId);
	}

	private List<ServerCaseRow> listActiveForServer(int tenant,int actor,String query,int offset,int limit,Integer assignedUserId) {
		if(tenant<=0||actor<=0||offset<0||limit<=0) throw new IllegalArgumentException("invalid server Case projection boundary");
		try(Connection con=db.requireConnection()) {
			verifyTenant(con,tenant); verifyEligibleAssignedUser(con,tenant,actor);
			String scope=assignedUserId==null?"":"AND EXISTS (SELECT 1 FROM dbo.CaseUsers scope WHERE scope.CaseId=c.Id AND scope.UserId=?)";
			String search=query==null?"":"AND LOWER(COALESCE(c.Name,'')) LIKE ?";
			String order=assignedUserId==null?"c.Name ASC,c.Id ASC":"status_row.StatusSortOrder ASC,dates.IntakeDate DESC,c.Id DESC";
			String sql="""
				SELECT c.Id,c.ShaleClientId,c.CaseNumber,c.Name,status_row.StatusId,status_row.SystemKey StatusSystemKey,
				 status_row.LifecycleKey StatusLifecycleKey,status_row.StatusName,status_row.StatusColor,
				 c.PracticeAreaId,pa.Name PracticeAreaName,pa.Color PracticeAreaColor,
				 attorney.UserId ResponsibleAttorneyId,attorney_user.DisplayName ResponsibleAttorneyName,attorney_user.Color ResponsibleAttorneyColor,
				 assistant.UserId PrimaryLegalAssistantId,assistant_user.DisplayName PrimaryLegalAssistantName,assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt,c.UpdatedAt,ISNULL(c.IsDeleted,0) IsDeleted,c.Description,
				 dates.IntakeDate,dates.InjuryDate,dates.StatuteDate,dates.TortDate,
				 caller.ContactId CallerContactId,caller.DisplayName CallerName,client.ContactId ClientContactId,client.DisplayName ClientName,
				 counsel.ContactId OpposingCounselContactId,counsel.DisplayName OpposingCounselName
				FROM dbo.Cases c
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=? ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=? ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN t.SystemKey='date_of_injury' THEN CAST(cd.StartsAt AS date) END) InjuryDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0 AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL) ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				OUTER APPLY (SELECT TOP(1) ct.Id ContactId,COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name) DisplayName FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL) JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId WHERE cp.CaseId=c.Id AND ISNULL(ct.IsDeleted,0)=0 AND LOWER(COALESCE(pr.SystemKey,pr.Name))='caller' ORDER BY cp.IsPrimary DESC,cp.Id DESC) caller
				OUTER APPLY (SELECT TOP(1) ct.Id ContactId,COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name) DisplayName FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL) JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId WHERE cp.CaseId=c.Id AND ISNULL(ct.IsDeleted,0)=0 AND LOWER(COALESCE(pr.SystemKey,pr.Name))='party' AND LOWER(LTRIM(RTRIM(cp.Side)))='represented' ORDER BY cp.IsPrimary DESC,cp.Id DESC) client
				OUTER APPLY (SELECT TOP(1) ct.Id ContactId,COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name) DisplayName FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL) JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId WHERE cp.CaseId=c.Id AND ISNULL(ct.IsDeleted,0)=0 AND LOWER(COALESCE(pr.SystemKey,pr.Name))='counsel' AND LOWER(LTRIM(RTRIM(cp.Side)))='opposing' ORDER BY cp.IsPrimary DESC,cp.Id DESC) counsel
				WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0 %s %s
				ORDER BY %s OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
				""".formatted(statusApplySql(),search,scope,order);
			try(PreparedStatement ps=con.prepareStatement(sql)) { int i=1; ps.setInt(i++,RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY); ps.setInt(i++,RoleSemantics.ROLE_LEGAL_ASSISTANT); ps.setInt(i++,tenant); if(query!=null)ps.setString(i++,"%"+escapeLike(query)+"%"); if(assignedUserId!=null)ps.setInt(i++,assignedUserId); ps.setInt(i++,offset); ps.setInt(i,limit);
				List<ServerCaseRow> out=new ArrayList<>(); try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new ServerCaseRow(mapGridSummary(rs),localDate(rs,"IntakeDate"),localDate(rs,"InjuryDate"),localDate(rs,"StatuteDate"),localDate(rs,"TortDate"),rs.getString("PracticeAreaColor"),rs.getString("Description"),nullableInt(rs,"CallerContactId"),rs.getString("CallerName"),nullableInt(rs,"ClientContactId"),rs.getString("ClientName"),nullableInt(rs,"OpposingCounselContactId"),rs.getString("OpposingCounselName")));} return List.copyOf(out); }
		} catch(SQLException e){throw new RuntimeException("Failed to load server Case summaries",e);}
	}

	/**
	 * Admin Deleted Cases search. This preserves the established unpaged, nonblank,
	 * case-name-only literal substring contract while selecting deletion explicitly.
	 */
	public List<DeletedCaseRow> searchDeletedByName(int requestedTenantId, String query) {
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		String normalized = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
		if (normalized.isBlank()) return List.of();
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			String sql = """
				SELECT c.Id,c.ShaleClientId,c.CaseNumber,c.Name,
				 status_row.StatusId,status_row.SystemKey StatusSystemKey,status_row.LifecycleKey StatusLifecycleKey,
				 status_row.StatusName,status_row.StatusColor,c.PracticeAreaId,pa.Name PracticeAreaName,
				 attorney.UserId ResponsibleAttorneyId,attorney_user.DisplayName ResponsibleAttorneyName,
				 attorney_user.Color ResponsibleAttorneyColor,assistant.UserId PrimaryLegalAssistantId,
				 assistant_user.DisplayName PrimaryLegalAssistantName,assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt,c.UpdatedAt,CAST(1 AS bit) IsDeleted,pa.Color PracticeAreaColor,
				 dates.IntakeDate,dates.StatuteDate,dates.TortDate,c.NonEngagementLetterSent,c.RowVer
				FROM dbo.Cases c
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				WHERE c.ShaleClientId=? AND c.IsDeleted = 1 AND LOWER(COALESCE(c.Name,'')) LIKE ?
				ORDER BY c.Name ASC,c.Id ASC
				""".formatted(statusApplySql());
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				ps.setString(4, "%" + escapeLike(normalized) + "%");
				List<DeletedCaseRow> rows = new ArrayList<>();
				try (ResultSet rs=ps.executeQuery()) { while (rs.next()) rows.add(new DeletedCaseRow(
						mapGridSummary(rs), localDate(rs,"IntakeDate"), localDate(rs,"StatuteDate"),
						localDate(rs,"TortDate"), rs.getString("PracticeAreaColor"),
						(Boolean)rs.getObject("NonEngagementLetterSent"), rs.getBytes("RowVer"))); }
				return List.copyOf(rows);
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to search authoritative deleted Case summaries", e); }
	}

	static String escapeLike(String value) {
		return value.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
	}

	/** Main active Cases grid. Filtering, ordering, enrichment and paging stay set based. */
	public GridPage findActiveGridPage(int requestedTenantId, int page, int pageSize, GridOrder order,
			String query, GridStatusMode statusMode, Set<Integer> selectedStatusIds, Long knownTotal) {
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		if (page < 0 || pageSize <= 0) throw new IllegalArgumentException("Invalid grid page");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(statusMode, "statusMode");
		String normalized = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
		Set<Integer> statuses = selectedStatusIds == null ? Set.of() : new LinkedHashSet<>(selectedStatusIds);
		validateStatusMode(statusMode, statuses);
		Set<Integer> boundStatuses = statusMode == GridStatusMode.SELECTED ? statuses : Set.of();
		String statusPredicate = statusPredicate(statusMode, statuses.size());
		String searchPredicate = normalized.isBlank() ? "" : "AND LOWER(COALESCE(c.Name, '')) LIKE ?";
		String orderBy = gridOrderSql(order);
		try (Connection con = db.requireConnection()) {
			int sessionTenantId = verifyTenant(con, requestedTenantId);
			long total = knownTotal != null && knownTotal >= 0 ? knownTotal
					: countActiveGrid(con, requestedTenantId, normalized, boundStatuses, searchPredicate, statusPredicate);
			LOG.debug("Cases grid query tenant={} sessionTenant={} deletedMode=ACTIVE searchEnabled={} statusMode={} statusCount={} statusIds={} page={} pageSize={} sort={} count={}",
					requestedTenantId, sessionTenantId, !normalized.isBlank(), statusMode, statuses.size(), statuses, page, pageSize, order, total);
			if (total == 0) return new GridPage(List.of(), page, pageSize, 0);
			String sql = gridSql(searchPredicate, statusPredicate, orderBy);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int index = bindGridCriteria(ps, requestedTenantId, normalized, boundStatuses, 1);
				ps.setInt(index++, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(index++, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(index++, page * pageSize);
				ps.setInt(index, pageSize);
				List<CaseGridRow> rows = new ArrayList<>(pageSize);
				try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(mapGrid(rs)); }
				LOG.debug("Cases grid page tenant={} page={} pageSize={} sort={} returnedRows={}", requestedTenantId, page, pageSize, order, rows.size());
				return new GridPage(List.copyOf(rows), page, pageSize, total);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load authoritative Cases grid page", e);
		}
	}

	public long countActiveGrid(int requestedTenantId, String query, GridStatusMode statusMode, Set<Integer> selectedStatusIds) {
		Objects.requireNonNull(statusMode, "statusMode");
		String normalized = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
		Set<Integer> statuses = selectedStatusIds == null ? Set.of() : new LinkedHashSet<>(selectedStatusIds);
		validateStatusMode(statusMode, statuses);
		Set<Integer> boundStatuses = statusMode == GridStatusMode.SELECTED ? statuses : Set.of();
		String search = normalized.isBlank() ? "" : "AND LOWER(COALESCE(c.Name, '')) LIKE ?";
		String status = statusPredicate(statusMode, statuses.size());
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			return countActiveGrid(con, requestedTenantId, normalized, boundStatuses, search, status);
		} catch (SQLException e) { throw new RuntimeException("Failed to count authoritative Cases grid", e); }
	}

	/**
	 * Complete, immutable-criteria Cases export snapshot. Retrieval remains bounded at the SQL
	 * boundary while reusing the exact grid count/data predicates and authoritative enrichment.
	 * The returned list is materialized because the established CSV/XLSX writers require a list.
	 */
	public List<CaseGridRow> listActiveGridForExport(int requestedTenantId, GridOrder order, String query,
			GridStatusMode statusMode, Set<Integer> selectedStatusIds) {
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(statusMode, "statusMode");
		Set<Integer> statuses = selectedStatusIds == null ? Set.of()
				: Set.copyOf(new LinkedHashSet<>(selectedStatusIds));
		validateStatusMode(statusMode, statuses);
		List<CaseGridRow> rows = new ArrayList<>();
		long total = -1;
		for (int page = 0; total < 0 || rows.size() < total; page++) {
			GridPage batch = findActiveGridPage(requestedTenantId, page, EXPORT_BATCH_SIZE, order,
					query, statusMode, statuses, total < 0 ? null : total);
			total = batch.total();
			rows.addAll(batch.items());
			if (batch.items().isEmpty()) break;
		}
		return List.copyOf(rows);
	}

	/**
	 * Loads the complete assigned-user board snapshot in one statement. Membership bounds this
	 * established board to one user's active Cases; every apply is scalar/aggregate, preserving
	 * exactly one row per Case even when legacy current rows are malformed.
	 */
	public List<CaseBoardRow> listActiveAssignedBoard(int requestedTenantId, int assignedUserId) {
		if (requestedTenantId <= 0 || assignedUserId <= 0)
			throw new IllegalArgumentException("tenant and assignedUserId must be > 0");
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			verifyEligibleAssignedUser(con, requestedTenantId, assignedUserId);
			String sql = """
				SELECT c.Id, c.ShaleClientId, c.CaseNumber, c.Name,
				 status_row.StatusId, status_row.SystemKey StatusSystemKey,
				 status_row.LifecycleKey StatusLifecycleKey, status_row.StatusName, status_row.StatusColor,
				 c.PracticeAreaId, pa.Name PracticeAreaName, pa.Color PracticeAreaColor,
				 attorney.UserId ResponsibleAttorneyId, attorney_user.DisplayName ResponsibleAttorneyName,
				 attorney_user.Color ResponsibleAttorneyColor,
				 assistant.UserId PrimaryLegalAssistantId, assistant_user.DisplayName PrimaryLegalAssistantName,
				 assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt, c.UpdatedAt, ISNULL(c.IsDeleted,0) IsDeleted,
				 dates.IntakeDate, dates.StatuteDate, dates.TortDate, c.NonEngagementLetterSent
				FROM dbo.Cases c
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId
				 AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu
				 WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu
				 WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
				 AND EXISTS (SELECT 1 FROM dbo.CaseUsers scope
				  WHERE scope.CaseId=c.Id AND scope.UserId=?)
				ORDER BY status_row.StatusId ASC, dates.IntakeDate DESC, c.Id DESC
				""".formatted(statusApplySql());
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(2, RoleSemantics.ROLE_LEGAL_ASSISTANT);
				ps.setInt(3, requestedTenantId);
				ps.setInt(4, assignedUserId);
				List<CaseBoardRow> rows = new ArrayList<>();
				try (ResultSet rs=ps.executeQuery()) { while(rs.next()) rows.add(new CaseBoardRow(
						mapGridSummary(rs), localDate(rs,"IntakeDate"), localDate(rs,"StatuteDate"),
						localDate(rs,"TortDate"), rs.getString("PracticeAreaColor"),
						(Boolean)rs.getObject("NonEngagementLetterSent"))); }
				return List.copyOf(rows);
			}
		} catch (SQLException e) { throw new RuntimeException("Failed to load assigned Case board", e); }
	}

	/** Bounded rich-card projection for desktop User Detail's selected team member. */
	public List<CaseGridRow> listActiveAssignedForUserDetail(int requestedTenantId, int assignedUserId, int limit) {
		if (requestedTenantId <= 0 || assignedUserId <= 0 || limit <= 0)
			throw new IllegalArgumentException("tenant, assignedUserId, and limit must be > 0");
		try (Connection con = db.requireConnection()) {
			verifyTenant(con, requestedTenantId);
			verifyEligibleAssignedUser(con, requestedTenantId, assignedUserId);
			String sql = """
				SELECT TOP (?) c.Id,c.ShaleClientId,c.CaseNumber,c.Name,
				 status_row.StatusId,status_row.SystemKey StatusSystemKey,status_row.LifecycleKey StatusLifecycleKey,
				 status_row.StatusName,status_row.StatusColor,c.PracticeAreaId,pa.Name PracticeAreaName,pa.Color PracticeAreaColor,
				 attorney.UserId ResponsibleAttorneyId,attorney_user.DisplayName ResponsibleAttorneyName,
				 attorney_user.Color ResponsibleAttorneyColor,assistant.UserId PrimaryLegalAssistantId,
				 assistant_user.DisplayName PrimaryLegalAssistantName,assistant_user.Color PrimaryLegalAssistantColor,
				 c.CreatedAt,c.UpdatedAt,CAST(0 AS bit) IsDeleted,c.Description,c.NonEngagementLetterSent,
				 dates.IntakeDate,dates.InjuryDate,dates.StatuteDate,dates.TortDate,
				 client.ClientName,opposing.OpposingPartiesName,latest.LatestCaseUpdate
				FROM dbo.Cases c
				%s
				LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
				OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
				 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
				OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
				 FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
				OUTER APPLY (SELECT
				 MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
				 MAX(CASE WHEN t.SystemKey='date_of_injury' THEN CAST(cd.StartsAt AS date) END) InjuryDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
				 MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
				 FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
				  AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
				 OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
				  WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
				   AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
				  ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END,m.Id DESC) effective
				 WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0) dates
				OUTER APPLY (SELECT TOP(1) COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name) ClientName
				 FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL)
				 JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId
				 WHERE cp.CaseId=c.Id AND LOWER(LTRIM(RTRIM(pr.SystemKey)))='party' AND LOWER(LTRIM(RTRIM(cp.Side)))='represented'
				  AND ISNULL(ct.IsDeleted,0)=0 ORDER BY cp.IsPrimary DESC,cp.UpdatedAt DESC,cp.CreatedAt DESC,cp.Id DESC) client
				OUTER APPLY (SELECT STRING_AGG(x.DisplayName,', ') WITHIN GROUP(ORDER BY x.Id) OpposingPartiesName FROM
				 (SELECT cp.Id,COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name,o.Name) DisplayName
				  FROM dbo.CaseParties cp LEFT JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId
				  LEFT JOIN dbo.Organizations o ON o.Id=cp.OrganizationId AND o.ShaleClientId=c.ShaleClientId
				  WHERE cp.CaseId=c.Id AND LOWER(LTRIM(RTRIM(cp.Side)))='opposing'
				   AND (ct.Id IS NULL OR ISNULL(ct.IsDeleted,0)=0) AND (o.Id IS NULL OR ISNULL(o.IsDeleted,0)=0)) x) opposing
				OUTER APPLY (SELECT TOP(1) NULLIF(LTRIM(RTRIM(cu.NoteText)),'') LatestCaseUpdate FROM dbo.CaseUpdates cu
				 WHERE cu.CaseId=c.Id AND ISNULL(cu.IsDeleted,0)=0 AND NULLIF(LTRIM(RTRIM(cu.NoteText)),'') IS NOT NULL
				 ORDER BY cu.CreatedAt DESC,cu.Id DESC) latest
				WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0
				 AND EXISTS (SELECT 1 FROM dbo.CaseUsers scope WHERE scope.CaseId=c.Id AND scope.UserId=?)
				ORDER BY dates.IntakeDate DESC,c.Id DESC
				""".formatted(statusApplySql());
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				int i=1; ps.setInt(i++,limit); ps.setInt(i++,RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY);
				ps.setInt(i++,RoleSemantics.ROLE_LEGAL_ASSISTANT); ps.setInt(i++,requestedTenantId); ps.setInt(i,assignedUserId);
				List<CaseGridRow> rows=new ArrayList<>();
				try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new CaseGridRow(mapGridSummary(rs),
					localDate(rs,"IntakeDate"),localDate(rs,"StatuteDate"),localDate(rs,"InjuryDate"),localDate(rs,"TortDate"),
					rs.getString("PracticeAreaColor"),(Boolean)rs.getObject("NonEngagementLetterSent"),rs.getString("ClientName"),
					rs.getString("OpposingPartiesName"),rs.getString("LatestCaseUpdate"),rs.getString("Description")));}
				return List.copyOf(rows);
			}
		} catch(SQLException e){throw new RuntimeException("Failed to load User Detail assigned Cases",e);}
	}

	static String statusPredicate(GridStatusMode mode, int selectedCount) {
		return switch (mode) {
			case UNRESTRICTED -> "";
			case NO_STATUS -> "AND status_row.StatusId IS NULL";
			case SELECTED -> "AND (status_row.StatusId IS NULL OR status_row.StatusId IN ("
					+ String.join(",", java.util.Collections.nCopies(selectedCount, "?")) + "))";
		};
	}

	private static void validateStatusMode(GridStatusMode mode, Set<Integer> statuses) {
		if (mode == GridStatusMode.SELECTED && statuses.isEmpty())
			throw new IllegalArgumentException("SELECTED status mode requires at least one status ID");
	}

	private long countActiveGrid(Connection con, int tenant, String query, Set<Integer> statuses,
			String searchPredicate, String statusPredicate) throws SQLException {
		String sql = "SELECT COUNT_BIG(1) FROM dbo.Cases c " + statusApplySql() +
				" WHERE c.ShaleClientId = ? AND ISNULL(c.IsDeleted,0)=0 " + searchPredicate + " " + statusPredicate;
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			bindGridCriteria(ps, tenant, query, statuses, 1);
			try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
		}
	}

	private static int bindGridCriteria(PreparedStatement ps, int tenant, String query, Set<Integer> statuses, int index) throws SQLException {
		ps.setInt(index++, tenant);
		if (!query.isBlank()) ps.setString(index++, "%" + query.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%");
		for (Integer status : statuses) ps.setInt(index++, status);
		return index;
	}

	private static String gridOrderSql(GridOrder order) {
		return switch (order) {
			case INTAKE_NEWEST -> "dates.IntakeDate DESC, c.Id DESC";
			case INTAKE_OLDEST -> "dates.IntakeDate ASC, c.Id ASC";
			case STATUTE_SOONEST -> "dates.StatuteDate ASC, c.Id ASC";
			case STATUTE_LATEST -> "dates.StatuteDate DESC, c.Id DESC";
			case CASE_NAME_ASC -> "c.Name ASC, c.Id ASC";
			case CASE_NAME_DESC -> "c.Name DESC, c.Id DESC";
			case RESPONSIBLE_ATTORNEY_ASC -> "attorney_user.DisplayName ASC, c.Id ASC";
			case RESPONSIBLE_ATTORNEY_DESC -> "attorney_user.DisplayName DESC, c.Id DESC";
			case CASE_STATUS_ASC -> "c.StatusName ASC, c.Id ASC";
			case CASE_STATUS_DESC -> "c.StatusName DESC, c.Id DESC";
		};
	}

	private static String statusApplySql() {
		return """
			OUTER APPLY (
			 SELECT TOP (1) s.Id StatusId, s.SystemKey, s.LifecycleKey, s.Name StatusName, s.Color StatusColor, s.SortOrder StatusSortOrder
			 FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId
			  AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL)
			 WHERE cs.CaseId=c.Id AND cs.EndDate IS NULL
			 ORDER BY cs.IsPrimary DESC, cs.EffectiveDate DESC, cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
			) status_row
			""";
	}

	private static String gridSql(String search, String status, String orderBy) {
		return """
			WITH Filtered AS (
			 SELECT c.*, status_row.StatusId, status_row.SystemKey StatusSystemKey,
			        status_row.LifecycleKey StatusLifecycleKey, status_row.StatusName, status_row.StatusColor
			 FROM dbo.Cases c
			 %s
			 WHERE c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0 %s %s
			), Ordered AS (
			 SELECT c.*, dates.IntakeDate, dates.StatuteDate, dates.InjuryDate, dates.TortDate,
			        attorney.UserId ResponsibleAttorneyId, attorney_user.DisplayName ResponsibleAttorneyName,
			        attorney_user.Color ResponsibleAttorneyColor,
			        assistant.UserId PrimaryLegalAssistantId, assistant_user.DisplayName PrimaryLegalAssistantName,
			        assistant_user.Color PrimaryLegalAssistantColor,
			        ROW_NUMBER() OVER (ORDER BY %s) PageOrdinal
			 FROM Filtered c
			 OUTER APPLY (
			  SELECT
			   MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate,
			   MAX(CASE WHEN effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS' THEN CAST(cd.StartsAt AS date) END) StatuteDate,
			   MAX(CASE WHEN t.SystemKey='date_of_injury' THEN CAST(cd.StartsAt AS date) END) InjuryDate,
			   MAX(CASE WHEN effective.SemanticRoleKey='TORT_NOTICE_DEADLINE' THEN CAST(cd.StartsAt AS date) END) TortDate
			  FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes t ON t.Id=cd.CaseDateTypeId
			   AND (t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL)
			  OUTER APPLY (SELECT TOP(1) m.SemanticRoleKey FROM dbo.CaseDateTypeSemanticRoleMappings m
			    WHERE m.CaseDateTypeId=t.Id AND m.IsActive=1 AND m.IsDeleted=0
			      AND (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
			    ORDER BY CASE WHEN m.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END, m.Id DESC) effective
			  WHERE cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId AND cd.IsDeleted=0
			 ) dates
			 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
			   ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
			 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
			   FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
			 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.RoleId=?
			   ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
			 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName,u.color Color
			   FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
			 ORDER BY %s OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
			)
			SELECT c.*, pa.Name PracticeAreaName, pa.Color PracticeAreaColor,
			 client.ClientName, opposing.OpposingPartiesName, latest.LatestCaseUpdate
			FROM Ordered c
			LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
			OUTER APPLY (SELECT TOP(1) CASE WHEN NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),'') IS NULL THEN ct.Name ELSE LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))) END ClientName
			 FROM dbo.CaseParties cp JOIN dbo.PartyRoles pr ON pr.Id=cp.PartyRoleId
			  AND (pr.ShaleClientId=c.ShaleClientId OR pr.ShaleClientId IS NULL)
			 JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId
			 WHERE cp.CaseId=c.Id AND LOWER(LTRIM(RTRIM(pr.SystemKey)))='party' AND LOWER(LTRIM(RTRIM(cp.Side)))='represented'
			  AND ISNULL(ct.IsDeleted,0)=0 ORDER BY cp.IsPrimary DESC,cp.UpdatedAt DESC,cp.CreatedAt DESC,cp.Id DESC) client
			OUTER APPLY (SELECT STRING_AGG(x.DisplayName,', ') WITHIN GROUP(ORDER BY x.Id) OpposingPartiesName FROM
				 (SELECT cp.Id,COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(ct.FirstName,' ',ct.LastName))),''),ct.Name,o.Name) DisplayName
			  FROM dbo.CaseParties cp LEFT JOIN dbo.Contacts ct ON ct.Id=cp.ContactId AND ct.ShaleClientId=c.ShaleClientId
			  LEFT JOIN dbo.Organizations o ON o.Id=cp.OrganizationId AND o.ShaleClientId=c.ShaleClientId
			  WHERE cp.CaseId=c.Id AND LOWER(LTRIM(RTRIM(cp.Side)))='opposing' AND ISNULL(ct.IsDeleted,0)=0 AND ISNULL(o.IsDeleted,0)=0) x
			 WHERE NULLIF(x.DisplayName,'') IS NOT NULL) opposing
			OUTER APPLY (SELECT TOP(1) NULLIF(LTRIM(RTRIM(cu.NoteText)),'') LatestCaseUpdate FROM dbo.CaseUpdates cu
			 WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND ISNULL(cu.IsDeleted,0)=0
			  AND NULLIF(LTRIM(RTRIM(cu.NoteText)),'') IS NOT NULL
			 ORDER BY cu.CreatedAt DESC,cu.Id DESC) latest
			ORDER BY c.PageOrdinal
			""".formatted(statusApplySql(), search, status, orderBy, orderBy);
	}

	private static CaseGridRow mapGrid(ResultSet rs) throws SQLException {
		CaseSummaryProjection summary = mapGridSummary(rs);
		return new CaseGridRow(summary, localDate(rs,"IntakeDate"), localDate(rs,"StatuteDate"), localDate(rs,"InjuryDate"),
				localDate(rs,"TortDate"), rs.getString("PracticeAreaColor"), (Boolean)rs.getObject("NonEngagementLetterSent"),
				rs.getString("ClientName"), rs.getString("OpposingPartiesName"), rs.getString("LatestCaseUpdate"), rs.getString("Description"));
	}

	private static CaseSummaryProjection mapGridSummary(ResultSet rs) throws SQLException {
		return new CaseSummaryProjection(rs.getLong("Id"), rs.getInt("ShaleClientId"),
				rs.getString("CaseNumber"), rs.getString("Name"), nullableInt(rs,"StatusId"), rs.getString("StatusSystemKey"),
				rs.getString("StatusLifecycleKey"), rs.getString("StatusName"), rs.getString("StatusColor"),
				nullableInt(rs,"PracticeAreaId"), rs.getString("PracticeAreaName"), nullableInt(rs,"ResponsibleAttorneyId"),
				rs.getString("ResponsibleAttorneyName"), rs.getString("ResponsibleAttorneyColor"), nullableInt(rs,"PrimaryLegalAssistantId"),
				rs.getString("PrimaryLegalAssistantName"), rs.getString("PrimaryLegalAssistantColor"),
				localDateTime(rs.getTimestamp("CreatedAt")), localDateTime(rs.getTimestamp("UpdatedAt")), rs.getBoolean("IsDeleted"));
	}

	private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
		java.sql.Date value=rs.getDate(column); return value==null?null:value.toLocalDate();
	}

	private static Set<Integer> normalizedPositiveIds(List<Integer> ids) {
		if (ids == null) return Set.of();
		Set<Integer> result = new LinkedHashSet<>();
		for (Integer id : ids) if (id != null && id > 0) result.add(id);
		return Set.copyOf(result);
	}

	private static int bindNullableDateTwice(PreparedStatement ps, int index, LocalDate value) throws SQLException {
		java.sql.Date date = value == null ? null : java.sql.Date.valueOf(value);
		ps.setDate(index++, date); ps.setDate(index++, date); return index;
	}

	private static void verifyStatuses(Connection con, int tenantId, Set<Integer> statusIds) throws SQLException {
		String placeholders = String.join(",", java.util.Collections.nCopies(statusIds.size(), "?"));
		String sql = "SELECT COUNT(DISTINCT s.Id) FROM dbo.Statuses s WHERE s.Id IN (" + placeholders
				+ ") AND (s.ShaleClientId=? OR s.ShaleClientId IS NULL)";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i=1; for (Integer id:statusIds) ps.setInt(i++,id); ps.setInt(i,tenantId);
			try(ResultSet rs=ps.executeQuery()) { rs.next(); if(rs.getInt(1)!=statusIds.size())
				throw new IllegalArgumentException("status IDs must belong to the trusted tenant"); }
		}
	}

	private static void verifyEligibleAssignedUser(Connection con, int requestedTenantId, int assignedUserId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM dbo.Users u WHERE u.id=? AND u.ShaleClientId=? AND ISNULL(u.is_deleted,0)=0")) {
			ps.setInt(1, assignedUserId);
			ps.setInt(2, requestedTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw new IllegalArgumentException(
						"assignedUserId must identify a nondeleted user in the trusted tenant");
			}
		}
	}

	private static int verifyTenant(Connection con, int requestedTenantId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId'))");
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next() || rs.getObject(1) == null)
				throw new IllegalStateException("ShaleClientId session context is missing.");
			if (rs.getInt(1) != requestedTenantId)
				throw new IllegalStateException("Requested tenant conflicts with ShaleClientId session context.");
			return rs.getInt(1);
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

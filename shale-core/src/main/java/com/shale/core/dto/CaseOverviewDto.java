package com.shale.core.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Read-model/DTO for the Case "Overview" tab. Keep this small and focused on what the
 * Overview screen needs.
 */
public final class CaseOverviewDto {
	public record ContactSummary(Integer contactId, String displayName) {}

	private final long caseId;

	// Display identity
	private final String caseNumber; // could be same as id for now, but keep separate
	private final String caseName;

	// Core overview fields
	private final String caseStatus;
	private final Integer primaryStatusId;
	private final String primaryStatusColor;

	// Responsible attorney (for UserCard MINI + navigation)
	private final Integer responsibleAttorneyUserId; // NEW
	private final String responsibleAttorney;
	private final String responsibleAttorneyColor; // NEW (dbo.Users.Color)

	// Primary legal assistant (for UserCard COMPACT + navigation)
	private final Integer primaryLegalAssistantUserId;
	private final String primaryLegalAssistant;
	private final String primaryLegalAssistantColor;

	// Practice Area
	private final Integer practiceAreaId;
	private final String practiceArea;
	private final String practiceAreaColor;

	// Key dates
	private final LocalDate intakeDate;
	private final LocalDate incidentDate;
	private final LocalDate solDate;
	private final LocalDate tortNoticeDeadline;

	// Parties
	private final Integer primaryCallerContactId;
	private final Integer primaryClientContactId;
	private final String caller;
	private final String client;
	private final List<ContactSummary> clients;
	private final String opposingCounsel;
	private final Integer primaryOpposingCounselContactId;

	// Team users assigned
	private final List<String> team;

	// Summary
	private final String description;

	public CaseOverviewDto(
			long caseId,
			String caseNumber,
			String caseName,

			String caseStatus,
			Integer primaryStatusId,
			String primaryStatusColor,

			Integer responsibleAttorneyUserId,
			String responsibleAttorney,
			String responsibleAttorneyColor,

			Integer primaryLegalAssistantUserId,
			String primaryLegalAssistant,
			String primaryLegalAssistantColor,

			Integer practiceAreaId,
			String practiceArea,
			String practiceAreaColor,

			LocalDate intakeDate,
			LocalDate incidentDate,
			LocalDate solDate,
			LocalDate tortNoticeDeadline,
			Integer primaryCallerContactId,
			Integer primaryClientContactId,
			Integer primaryOpposingCounselContactId,
			String caller,
			String client,
			List<ContactSummary> clients,
			String opposingCounsel,
			List<String> team,
			String description) {

		this.caseId = caseId;

		this.caseNumber = safe(caseNumber);
		this.caseName = safe(caseName);

		this.caseStatus = safe(caseStatus);
		this.primaryStatusId = primaryStatusId;
		this.primaryStatusColor = safe(primaryStatusColor);

		this.responsibleAttorneyUserId = responsibleAttorneyUserId;
		this.responsibleAttorney = safe(responsibleAttorney);
		this.responsibleAttorneyColor = safe(responsibleAttorneyColor);
		this.primaryLegalAssistantUserId = primaryLegalAssistantUserId;
		this.primaryLegalAssistant = safe(primaryLegalAssistant);
		this.primaryLegalAssistantColor = safe(primaryLegalAssistantColor);

		this.practiceAreaId = practiceAreaId;
		this.practiceArea = safe(practiceArea);
		this.practiceAreaColor = safe(practiceAreaColor);

		this.intakeDate = intakeDate;
		this.incidentDate = incidentDate;
		this.solDate = solDate;
		this.tortNoticeDeadline = tortNoticeDeadline;

		this.primaryCallerContactId = primaryCallerContactId;
		this.primaryClientContactId = primaryClientContactId;
		this.primaryOpposingCounselContactId = primaryOpposingCounselContactId;
		this.caller = safe(caller);
		this.client = safe(client);
		this.clients = clients == null ? List.of() : List.copyOf(clients);
		this.opposingCounsel = safe(opposingCounsel);

		this.team = team == null ? List.of() : List.copyOf(team);
		this.description = safe(description);
	}

	public Integer getPrimaryStatusId() {
		return primaryStatusId;
	}

	public String getPrimaryStatusColor() {
		return primaryStatusColor;
	}

	public long getCaseId() {
		return caseId;
	}

	public String getCaseNumber() {
		return caseNumber;
	}

	public String getCaseName() {
		return caseName;
	}

	public String getCaseStatus() {
		return caseStatus;
	}

	public Integer getResponsibleAttorneyUserId() {
		return responsibleAttorneyUserId;
	}

	public String getResponsibleAttorney() {
		return responsibleAttorney;
	}

	public String getResponsibleAttorneyColor() {
		return responsibleAttorneyColor;
	}

	public Integer getPrimaryLegalAssistantUserId() {
		return primaryLegalAssistantUserId;
	}

	public String getPrimaryLegalAssistant() {
		return primaryLegalAssistant;
	}

	public String getPrimaryLegalAssistantColor() {
		return primaryLegalAssistantColor;
	}

	public Integer getPracticeAreaId() {
		return practiceAreaId;
	}

	public String getPracticeArea() {
		return practiceArea;
	}

	public String getPracticeAreaColor() {
		return practiceAreaColor;
	}

	public LocalDate getIntakeDate() {
		return intakeDate;
	}

	public LocalDate getIncidentDate() {
		return incidentDate;
	}

	public LocalDate getSolDate() {
		return solDate;
	}

	public LocalDate getTortNoticeDeadline() {
		return tortNoticeDeadline;
	}

	public String getCaller() {
		return caller;
	}

	public Integer getPrimaryCallerContactId() {
		return primaryCallerContactId;
	}

	public Integer getPrimaryClientContactId() {
		return primaryClientContactId;
	}

	public String getClient() {
		return client;
	}

	public List<ContactSummary> getClients() {
		return clients;
	}

	public String getOpposingCounsel() {
		return opposingCounsel;
	}

	public List<String> getTeam() {
		return team;
	}

	public String getDescription() {
		return description;
	}

	/** Convenience for UI: "Name (Number)" style if you want it. */
	public String getDisplayTitle() {
		if (!caseName.isBlank() && !caseNumber.isBlank()) {
			return caseName + " — " + caseNumber;
		}
		if (!caseName.isBlank())
			return caseName;
		return caseNumber.isBlank() ? ("Case #" + caseId) : caseNumber;
	}

	private static String safe(String s) {
		return Objects.toString(s, "").trim();
	}

	public Integer getPrimaryOpposingCounselContactId() {
		return primaryOpposingCounselContactId;
	}
}

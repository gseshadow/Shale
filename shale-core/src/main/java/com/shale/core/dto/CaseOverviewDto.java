package com.shale.core.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Read-model/DTO for the Case "Overview" tab. Keep this small and focused on what the
 * Overview screen needs.
 */
public final class CaseOverviewDto {

	private final long caseId;

	// Display identity
	private final String caseNumber; // could be same as id for now, but keep separate
	private final String caseName;

	// Core overview fields
	private final String caseStatus;
	private final String responsibleAttorney;
	private final String practiceArea;

	// Key dates
	private final LocalDate intakeDate;
	private final LocalDate incidentDate;
	private final LocalDate solDate;

	// Parties
	private final String caller;
	private final String client;
	private final String opposingCounsel;

	// Team users assigned
	private final List<String> team;

	// Summary
	private final String description;

	public CaseOverviewDto(
			long caseId,
			String caseNumber,
			String caseName,
			String caseStatus,
			String responsibleAttorney,
			String practiceArea,
			LocalDate intakeDate,
			LocalDate incidentDate,
			LocalDate solDate,
			String caller,
			String client,
			String opposingCounsel,
			List<String> team,
			String description) {
		this.caseId = caseId;

		this.caseNumber = safe(caseNumber);
		this.caseName = safe(caseName);

		this.caseStatus = safe(caseStatus);
		this.responsibleAttorney = safe(responsibleAttorney);
		this.practiceArea = safe(practiceArea);

		this.intakeDate = intakeDate;
		this.incidentDate = incidentDate;
		this.solDate = solDate;

		this.caller = safe(caller);
		this.client = safe(client);
		this.opposingCounsel = safe(opposingCounsel);

		this.team = team == null ? List.of() : List.copyOf(team);

		this.description = safe(description);
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

	public String getResponsibleAttorney() {
		return responsibleAttorney;
	}

	public String getPracticeArea() {
		return practiceArea;
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

	public String getCaller() {
		return caller;
	}

	public String getClient() {
		return client;
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

}

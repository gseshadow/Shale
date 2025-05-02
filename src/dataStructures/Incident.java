package dataStructures;

import java.io.Serializable;
import java.sql.Date;
import java.time.LocalDate;

public class Incident implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 409728233056403522L;
	private int id;
	private LocalDate dateMedNegOccurred;
	private LocalDate dateMedNegDiscovered;
	private LocalDate dateOfInjury;
	private LocalDate incidentStatuteOfLimitations;
	private LocalDate incidentTortNoticeDeadline;
	private LocalDate incidentDiscoveryDeadline;
	private boolean incidentMedRecsInHand = false;
	private String incidentDescription = "";
	private String incidentCaseStatus = "";
	private String incidentSummary = "";
	private String incidentUpdates = "";
	private int incidentCaseId;
	private int incidentOrganizationId;
	private boolean isDeleted;
	private String potentialDefendants = "";
	private String facilitiesInvolved = "";

	public Incident() {

	}

	public Incident(int id, LocalDate dateMedNegOccurred, LocalDate dateMedNegDiscovered, LocalDate dateOfInjury, LocalDate incidentStatuteOfLimitations,
			LocalDate incidentTortNoticeDeadline, LocalDate incidentDiscoveryDeadline, boolean incidentMedRecsInHand, String incidentDescription, String incidentCaseStatus,
			String incidentSummary, String incidentUpdates, int incidentCaseId, int incidentOrganizationId, boolean isDeleted, String potentialDefendants,
			String facilitiesInvolved) {
		this.id = id;
		this.dateMedNegOccurred = dateMedNegOccurred;
		this.dateMedNegDiscovered = dateMedNegDiscovered;
		this.dateOfInjury = dateOfInjury;
		this.incidentTortNoticeDeadline = incidentTortNoticeDeadline;
		this.incidentDiscoveryDeadline = incidentDiscoveryDeadline;
		this.incidentMedRecsInHand = incidentMedRecsInHand;
		this.incidentDescription = incidentDescription;
		this.incidentCaseStatus = incidentCaseStatus;
		this.incidentSummary = incidentSummary;
		this.incidentUpdates = incidentUpdates;
		this.incidentCaseId = incidentCaseId;
		this.incidentOrganizationId = incidentOrganizationId;
		this.isDeleted = isDeleted;
		this.potentialDefendants = potentialDefendants;
		this.facilitiesInvolved = facilitiesInvolved;
	}

	public LocalDate getDateMedNegOccurred() {
		return dateMedNegOccurred;
	}

	public void setIncidentMedNegOccurred(Date dateMedNegOccurred) {
		if (dateMedNegOccurred != null)
			this.dateMedNegOccurred = dateMedNegOccurred.toLocalDate();
	}

	public LocalDate getDateMedNegDiscovered() {
		return dateMedNegDiscovered;
	}

	public void setDateMedNegDiscovered(Date dateMedNegDiscovered) {
		if (dateMedNegDiscovered != null)
			this.dateMedNegDiscovered = dateMedNegDiscovered.toLocalDate();
	}

	public LocalDate getDateOfInjury() {
		return dateOfInjury;
	}

	public void setDateOfInjury(Date dateOfInjury) {
		if (dateOfInjury != null)
			this.dateOfInjury = dateOfInjury.toLocalDate();
	}

	public LocalDate getIncidentStatuteOfLimitations() {
		return incidentStatuteOfLimitations;
	}

	public void setIncidentStatuteOfLimitations(Date incidentStatuteOfLimitations) {
		if (incidentStatuteOfLimitations != null)
			this.incidentStatuteOfLimitations = incidentStatuteOfLimitations.toLocalDate();
	}

	public LocalDate getIncidentTortNoticeDeadline() {
		return incidentTortNoticeDeadline;
	}

	public void setIncidentTortNoticeDeadline(Date incidentTortNoticeDeadline) {
		if (incidentTortNoticeDeadline != null)
			this.incidentTortNoticeDeadline = incidentTortNoticeDeadline.toLocalDate();
	}

	public LocalDate getIncidentDiscoveryDeadline() {
		return incidentDiscoveryDeadline;
	}

	public void setIncidentDiscoveryDeadline(Date incidentDiscoveryDeadline) {
		if (incidentDiscoveryDeadline != null)
			this.incidentDiscoveryDeadline = incidentDiscoveryDeadline.toLocalDate();
	}

	public boolean isIncidentMedRecsInHand() {
		return incidentMedRecsInHand;
	}

	public void setIncidentMedRecsInHand(boolean incidentMedRecsInHand) {
		this.incidentMedRecsInHand = incidentMedRecsInHand;
	}

	public String getIncidentDescription() {
		return incidentDescription;
	}

	public void setIncidentDescription(String incidentDescription) {
		this.incidentDescription = incidentDescription;
	}

	public String getIncidentCaseStatus() {
		return incidentCaseStatus;
	}

	public void setIncidentCaseStatus(String incidentCaseStatus) {
		this.incidentCaseStatus = incidentCaseStatus;
	}

	public String getIncidentSummary() {
		return incidentSummary;
	}

	public void setIncidentSummary(String incidentSummary) {
		this.incidentSummary = incidentSummary;
	}

	public String getIncidentUpdates() {
		return incidentUpdates;
	}

	public void setIncidentUpdates(String incidentUpdates) {
		this.incidentUpdates = incidentUpdates;
	}

	public int getIncidentCaseId() {
		return incidentCaseId;
	}

	public void setIncidentCaseId(int incidentCaseId) {
		this.incidentCaseId = incidentCaseId;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getIncidentOrganizationId() {
		return incidentOrganizationId;
	}

	public void setIncidentOrganizationId(int incidentOrganizationId) {
		this.incidentOrganizationId = incidentOrganizationId;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public String getPotentialDefendants() {
		return potentialDefendants;
	}

	public void setPotentialDefendants(String potentialDefendants) {
		this.potentialDefendants = potentialDefendants;
	}

	public String getFacilitiesInvolved() {
		return facilitiesInvolved;
	}

	public void setFacilitiesInvolved(String facilitiesInvolved) {
		this.facilitiesInvolved = facilitiesInvolved;
	}
}

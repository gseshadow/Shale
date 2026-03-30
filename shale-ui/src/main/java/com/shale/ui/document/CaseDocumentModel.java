package com.shale.ui.document;

import java.time.LocalDate;
import java.util.List;

public final class CaseDocumentModel {
    private final String caseName;
    private final String statusName;
    private final String responsibleAttorneyName;
    private final String practiceAreaName;
    private final String callerName;
    private final String clientName;
    private final String opposingCounselName;
    private final List<TeamEntry> teamMembers;
    private final List<OrganizationEntry> organizations;
    private final LocalDate incidentDate;
    private final LocalDate solDate;
    private final String description;
    private final String caseSummary;

    private final LocalDate callerDate;
    private final String callerTime;
    private final LocalDate acceptedDate;
    private final LocalDate deniedDate;
    private final LocalDate closedDate;
    private final LocalDate dateOfMedicalNegligence;
    private final LocalDate dateMedicalNegligenceWasDiscovered;
    private final LocalDate tortNoticeDeadline;
    private final LocalDate discoveryDeadline;
    private final LocalDate dateFeeAgreementSigned;

    private final String officeCaseCode;
    private final String clientEstate;
    private final Boolean medicalRecordsReceived;
    private final Boolean feeAgreementSigned;
    private final Boolean acceptedChronology;
    private final Boolean acceptedConsultantExpertSearch;
    private final Boolean acceptedTestifyingExpertSearch;
    private final Boolean acceptedMedicalLiterature;
    private final String acceptedDetail;
    private final Boolean deniedChronology;
    private final String deniedDetail;
    private final String receivedUpdates;
    private final String summary;

    public CaseDocumentModel(
            String caseName,
            String statusName,
            String responsibleAttorneyName,
            String practiceAreaName,
            String callerName,
            String clientName,
            String opposingCounselName,
            List<TeamEntry> teamMembers,
            List<OrganizationEntry> organizations,
            LocalDate incidentDate,
            LocalDate solDate,
            String description,
            String caseSummary,
            LocalDate callerDate,
            String callerTime,
            LocalDate acceptedDate,
            LocalDate deniedDate,
            LocalDate closedDate,
            LocalDate dateOfMedicalNegligence,
            LocalDate dateMedicalNegligenceWasDiscovered,
            LocalDate tortNoticeDeadline,
            LocalDate discoveryDeadline,
            LocalDate dateFeeAgreementSigned,
            String officeCaseCode,
            String clientEstate,
            Boolean medicalRecordsReceived,
            Boolean feeAgreementSigned,
            Boolean acceptedChronology,
            Boolean acceptedConsultantExpertSearch,
            Boolean acceptedTestifyingExpertSearch,
            Boolean acceptedMedicalLiterature,
            String acceptedDetail,
            Boolean deniedChronology,
            String deniedDetail,
            String receivedUpdates,
            String summary) {
        this.caseName = normalize(caseName);
        this.statusName = normalize(statusName);
        this.responsibleAttorneyName = normalize(responsibleAttorneyName);
        this.practiceAreaName = normalize(practiceAreaName);
        this.callerName = normalize(callerName);
        this.clientName = normalize(clientName);
        this.opposingCounselName = normalize(opposingCounselName);
        this.teamMembers = teamMembers == null ? List.of() : List.copyOf(teamMembers);
        this.organizations = organizations == null ? List.of() : List.copyOf(organizations);
        this.incidentDate = incidentDate;
        this.solDate = solDate;
        this.description = normalize(description);
        this.caseSummary = normalize(caseSummary);
        this.callerDate = callerDate;
        this.callerTime = normalize(callerTime);
        this.acceptedDate = acceptedDate;
        this.deniedDate = deniedDate;
        this.closedDate = closedDate;
        this.dateOfMedicalNegligence = dateOfMedicalNegligence;
        this.dateMedicalNegligenceWasDiscovered = dateMedicalNegligenceWasDiscovered;
        this.tortNoticeDeadline = tortNoticeDeadline;
        this.discoveryDeadline = discoveryDeadline;
        this.dateFeeAgreementSigned = dateFeeAgreementSigned;
        this.officeCaseCode = normalize(officeCaseCode);
        this.clientEstate = normalize(clientEstate);
        this.medicalRecordsReceived = medicalRecordsReceived;
        this.feeAgreementSigned = feeAgreementSigned;
        this.acceptedChronology = acceptedChronology;
        this.acceptedConsultantExpertSearch = acceptedConsultantExpertSearch;
        this.acceptedTestifyingExpertSearch = acceptedTestifyingExpertSearch;
        this.acceptedMedicalLiterature = acceptedMedicalLiterature;
        this.acceptedDetail = normalize(acceptedDetail);
        this.deniedChronology = deniedChronology;
        this.deniedDetail = normalize(deniedDetail);
        this.receivedUpdates = normalize(receivedUpdates);
        this.summary = normalize(summary);
    }

    public String caseName() { return caseName; }
    public String statusName() { return statusName; }
    public String responsibleAttorneyName() { return responsibleAttorneyName; }
    public String practiceAreaName() { return practiceAreaName; }
    public String callerName() { return callerName; }
    public String clientName() { return clientName; }
    public String opposingCounselName() { return opposingCounselName; }
    public List<TeamEntry> teamMembers() { return teamMembers; }
    public List<OrganizationEntry> organizations() { return organizations; }
    public LocalDate incidentDate() { return incidentDate; }
    public LocalDate solDate() { return solDate; }
    public String description() { return description; }
    public String caseSummary() { return caseSummary; }
    public LocalDate callerDate() { return callerDate; }
    public String callerTime() { return callerTime; }
    public LocalDate acceptedDate() { return acceptedDate; }
    public LocalDate deniedDate() { return deniedDate; }
    public LocalDate closedDate() { return closedDate; }
    public LocalDate dateOfMedicalNegligence() { return dateOfMedicalNegligence; }
    public LocalDate dateMedicalNegligenceWasDiscovered() { return dateMedicalNegligenceWasDiscovered; }
    public LocalDate tortNoticeDeadline() { return tortNoticeDeadline; }
    public LocalDate discoveryDeadline() { return discoveryDeadline; }
    public LocalDate dateFeeAgreementSigned() { return dateFeeAgreementSigned; }
    public String officeCaseCode() { return officeCaseCode; }
    public String clientEstate() { return clientEstate; }
    public Boolean medicalRecordsReceived() { return medicalRecordsReceived; }
    public Boolean feeAgreementSigned() { return feeAgreementSigned; }
    public Boolean acceptedChronology() { return acceptedChronology; }
    public Boolean acceptedConsultantExpertSearch() { return acceptedConsultantExpertSearch; }
    public Boolean acceptedTestifyingExpertSearch() { return acceptedTestifyingExpertSearch; }
    public Boolean acceptedMedicalLiterature() { return acceptedMedicalLiterature; }
    public String acceptedDetail() { return acceptedDetail; }
    public Boolean deniedChronology() { return deniedChronology; }
    public String deniedDetail() { return deniedDetail; }
    public String receivedUpdates() { return receivedUpdates; }
    public String summary() { return summary; }

    public record TeamEntry(String name, String roleName) {
        public TeamEntry {
            name = normalize(name);
            roleName = normalize(roleName);
        }
    }

    public record OrganizationEntry(String name) {
        public OrganizationEntry {
            name = normalize(name);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

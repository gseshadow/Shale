package com.shale.ui.document;

import java.time.LocalDate;
import java.util.List;

public final class CaseDocumentModel {
    private final int caseId;
    private final String caseName;
    private final String caseNumber;
    private final String statusName;
    private final String responsibleAttorneyName;
    private final String practiceAreaName;
    private final String callerName;
    private final String clientName;
    private final String opposingCounselName;
    private final LocalDate incidentDate;
    private final LocalDate solDate;
    private final String description;
    private final List<TeamEntry> teamMembers;
    private final List<OrganizationEntry> organizations;

    public CaseDocumentModel(int caseId, String caseName, String caseNumber, String statusName,
            String responsibleAttorneyName, String practiceAreaName, String callerName,
            String clientName, String opposingCounselName, LocalDate incidentDate,
            LocalDate solDate, String description, List<TeamEntry> teamMembers,
            List<OrganizationEntry> organizations) {
        this.caseId = caseId;
        this.caseName = normalize(caseName);
        this.caseNumber = normalize(caseNumber);
        this.statusName = normalize(statusName);
        this.responsibleAttorneyName = normalize(responsibleAttorneyName);
        this.practiceAreaName = normalize(practiceAreaName);
        this.callerName = normalize(callerName);
        this.clientName = normalize(clientName);
        this.opposingCounselName = normalize(opposingCounselName);
        this.incidentDate = incidentDate;
        this.solDate = solDate;
        this.description = normalize(description);
        this.teamMembers = teamMembers == null ? List.of() : List.copyOf(teamMembers);
        this.organizations = organizations == null ? List.of() : List.copyOf(organizations);
    }

    public int caseId() { return caseId; }
    public String caseName() { return caseName; }
    public String caseNumber() { return caseNumber; }
    public String statusName() { return statusName; }
    public String responsibleAttorneyName() { return responsibleAttorneyName; }
    public String practiceAreaName() { return practiceAreaName; }
    public String callerName() { return callerName; }
    public String clientName() { return clientName; }
    public String opposingCounselName() { return opposingCounselName; }
    public LocalDate incidentDate() { return incidentDate; }
    public LocalDate solDate() { return solDate; }
    public String description() { return description; }
    public List<TeamEntry> teamMembers() { return teamMembers; }
    public List<OrganizationEntry> organizations() { return organizations; }

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

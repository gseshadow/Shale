package com.shale.ui.document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CaseDocumentModel {
    private final String caseName;
    private final String statusName;
    private final String practiceAreaName;

    private final String callerName;
    private final String callerPhone;
    private final String callerAddress;
    private final String callerEmail;

    private final String clientName;
    private final String clientPhone;
    private final String clientAddress;
    private final String clientEmail;

    private final LocalDate incidentDate;
    private final LocalDate solDate;
    private final LocalDate acceptedDate;
    private final LocalDate deniedDate;
    private final LocalDate closedDate;

    private final String description;
    private final String summary;
    private final List<UpdateEntry> updates;

    public CaseDocumentModel(
            String caseName,
            String statusName,
            String practiceAreaName,
            String callerName,
            String callerPhone,
            String callerAddress,
            String callerEmail,
            String clientName,
            String clientPhone,
            String clientAddress,
            String clientEmail,
            LocalDate incidentDate,
            LocalDate solDate,
            LocalDate acceptedDate,
            LocalDate deniedDate,
            LocalDate closedDate,
            String description,
            String summary,
            List<UpdateEntry> updates) {
        this.caseName = normalize(caseName);
        this.statusName = normalize(statusName);
        this.practiceAreaName = normalize(practiceAreaName);
        this.callerName = normalize(callerName);
        this.callerPhone = normalize(callerPhone);
        this.callerAddress = normalize(callerAddress);
        this.callerEmail = normalize(callerEmail);
        this.clientName = normalize(clientName);
        this.clientPhone = normalize(clientPhone);
        this.clientAddress = normalize(clientAddress);
        this.clientEmail = normalize(clientEmail);
        this.incidentDate = incidentDate;
        this.solDate = solDate;
        this.acceptedDate = acceptedDate;
        this.deniedDate = deniedDate;
        this.closedDate = closedDate;
        this.description = normalize(description);
        this.summary = normalize(summary);
        this.updates = updates == null ? List.of() : List.copyOf(updates);
    }

    public String caseName() { return caseName; }
    public String statusName() { return statusName; }
    public String practiceAreaName() { return practiceAreaName; }
    public String callerName() { return callerName; }
    public String callerPhone() { return callerPhone; }
    public String callerAddress() { return callerAddress; }
    public String callerEmail() { return callerEmail; }
    public String clientName() { return clientName; }
    public String clientPhone() { return clientPhone; }
    public String clientAddress() { return clientAddress; }
    public String clientEmail() { return clientEmail; }
    public LocalDate incidentDate() { return incidentDate; }
    public LocalDate solDate() { return solDate; }
    public LocalDate acceptedDate() { return acceptedDate; }
    public LocalDate deniedDate() { return deniedDate; }
    public LocalDate closedDate() { return closedDate; }
    public String description() { return description; }
    public String summary() { return summary; }
    public List<UpdateEntry> updates() { return updates; }

    public record UpdateEntry(LocalDateTime createdAt, String author, String body) {
        public UpdateEntry {
            author = normalize(author);
            body = normalize(body);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

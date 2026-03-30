package com.shale.ui.document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CaseDocumentHtmlRenderer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu h:mm a");

    public String render(CaseDocumentModel model, CaseDocumentType type, LocalDateTime generatedAt) {
        Objects.requireNonNull(model, "model");
        if (type != CaseDocumentType.CASE_SUMMARY) {
            throw new IllegalArgumentException("Unsupported case document type: " + type);
        }

        LocalDateTime at = generatedAt == null ? LocalDateTime.now() : generatedAt;
        StringBuilder body = new StringBuilder();
        body.append(section("Case Information", kvTable(List.of(
                kvRow("Case Name", model.caseName()),
                kvRow("Status", model.statusName()),
                kvRow("Responsible Attorney", model.responsibleAttorneyName()),
                kvRow("Practice Area", model.practiceAreaName())))));
        body.append(section("Contacts", kvTable(List.of(
                kvRow("Caller", model.callerName()),
                kvRow("Client", model.clientName()),
                kvRow("Opposing Counsel", model.opposingCounselName())))));

        List<String> team = teamLines(model.teamMembers());
        if (!team.isEmpty()) {
            body.append(section("Team", toListHtml(team)));
        }
        List<String> orgs = orgLines(model.organizations());
        if (!orgs.isEmpty()) {
            body.append(section("Organizations", toListHtml(orgs)));
        }

        List<String> dateRows = new ArrayList<>();
        addIfPresent(dateRows, kvRow("Incident Date", fmt(model.incidentDate())));
        addIfPresent(dateRows, kvRow("SOL Date", fmt(model.solDate())));
        addIfPresent(dateRows, kvRow("Accepted Date", fmt(model.acceptedDate())));
        addIfPresent(dateRows, kvRow("Denied Date", fmt(model.deniedDate())));
        addIfPresent(dateRows, kvRow("Closed Date", fmt(model.closedDate())));
        addIfPresent(dateRows, kvRow("Intake Date", fmt(model.callerDate())));
        addIfPresent(dateRows, kvRow("Intake Time", model.callerTime()));
        addIfPresent(dateRows, kvRow("Medical Negligence Date", fmt(model.dateOfMedicalNegligence())));
        addIfPresent(dateRows, kvRow("Negligence Discovery Date", fmt(model.dateMedicalNegligenceWasDiscovered())));
        addIfPresent(dateRows, kvRow("Tort Notice Deadline", fmt(model.tortNoticeDeadline())));
        addIfPresent(dateRows, kvRow("Discovery Deadline", fmt(model.discoveryDeadline())));
        addIfPresent(dateRows, kvRow("Fee Agreement Date", fmt(model.dateFeeAgreementSigned())));
        if (!dateRows.isEmpty()) {
            body.append(section("Dates / Status Details", kvTable(dateRows)));
        }

        List<String> additionalRows = new ArrayList<>();
        addIfPresent(additionalRows, kvRow("Office Case Code", model.officeCaseCode()));
        addIfPresent(additionalRows, kvRow("Estate Case", model.clientEstate()));
        addIfPresent(additionalRows, kvRow("Medical Records Received", yesNo(model.medicalRecordsReceived())));
        addIfPresent(additionalRows, kvRow("Fee Agreement Signed", yesNo(model.feeAgreementSigned())));
        addIfPresent(additionalRows, kvRow("Accepted Chronology", yesNo(model.acceptedChronology())));
        addIfPresent(additionalRows, kvRow("Consultant Expert Search", yesNo(model.acceptedConsultantExpertSearch())));
        addIfPresent(additionalRows, kvRow("Testifying Expert Search", yesNo(model.acceptedTestifyingExpertSearch())));
        addIfPresent(additionalRows, kvRow("Accepted Medical Literature", yesNo(model.acceptedMedicalLiterature())));
        addIfPresent(additionalRows, kvRow("Denied Chronology", yesNo(model.deniedChronology())));
        addIfPresent(additionalRows, kvRow("Received Updates", model.receivedUpdates()));
        addIfPresent(additionalRows, kvRow("Summary (Detail Field)", model.summary()));

        StringBuilder additionalBlock = new StringBuilder();
        if (!additionalRows.isEmpty()) {
            additionalBlock.append(kvTable(additionalRows));
        }
        if (!isBlank(model.acceptedDetail())) {
            additionalBlock.append(block("Accepted Detail", model.acceptedDetail()));
        }
        if (!isBlank(model.deniedDetail())) {
            additionalBlock.append(block("Denied Detail", model.deniedDetail()));
        }
        if (!additionalBlock.isEmpty()) {
            body.append(section("Additional Case Details", additionalBlock.toString()));
        }

        if (!isBlank(model.description())) {
            body.append(section("Description", "<div class=\"description\">" + safe(model.description()) + "</div>"));
        }
        if (!isBlank(model.caseSummary())) {
            body.append(section("Case Summary", "<div class=\"description\">" + safe(model.caseSummary()) + "</div>"));
        }

        return """
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Case Summary</title>
  <style>
    body { margin: 0; font-family: "Segoe UI", Arial, sans-serif; color: #1f2937; }
    .page { max-width: 8.5in; margin: 0 auto; padding: 0.5in; }
    .doc-header { border-bottom: 2px solid #d1d5db; margin-bottom: 18px; padding-bottom: 10px; }
    .firm { font-size: 12px; color: #4b5563; text-transform: uppercase; }
    h1 { font-size: 26px; margin: 6px 0; color: #111827; }
    .generated { font-size: 12px; color: #6b7280; }
    .section { margin-top: 18px; page-break-inside: avoid; }
    .section-title { font-size: 16px; margin: 0 0 10px; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; }
    .kv-table { width: 100%%; border-collapse: collapse; table-layout: fixed; }
    .kv-table tr { vertical-align: top; }
    .kv-table th { width: 34%%; text-align: left; font-weight: 600; color: #4b5563; padding: 4px 10px 4px 0; }
    .kv-table td { color: #111827; padding: 4px 0; }
    .text-block { margin-top: 12px; }
    .text-title { font-weight: 600; color: #374151; margin-bottom: 4px; }
    ul { margin: 0; padding-left: 20px; }
    li { margin: 5px 0; }
    .description { white-space: normal; }
    @media print {
      .section-title { page-break-after: avoid; }
      .section { page-break-inside: avoid; }
      .kv-table { page-break-inside: avoid; }
      .text-block { page-break-inside: avoid; }
    }
  </style>
</head>
<body>
  <main class="page">
    <header class="doc-header"><div class="firm">Shale</div><h1>Case Summary</h1><div class="generated">Generated: %s</div></header>
    %s
  </main>
</body>
</html>
""".formatted(escape(TS_FORMAT.format(at)), body);
    }

    private String section(String title, String content) {
        if (content == null || content.isBlank()) return "";
        return "<section class=\"section\"><h2 class=\"section-title\">" + escape(title) + "</h2>" + content + "</section>";
    }

    private String kvTable(List<String> rows) {
        if (rows == null || rows.isEmpty()) return "";
        return "<table class=\"kv-table\"><tbody>" + String.join("", rows) + "</tbody></table>";
    }

    private String kvRow(String key, String value) {
        return "<tr><th scope=\"row\">" + escape(key) + "</th><td>" + safe(value) + "</td></tr>";
    }

    private String block(String title, String text) {
        return "<div class=\"text-block\"><div class=\"text-title\">" + escape(title) + "</div><div class=\"description\">" + safe(text) + "</div></div>";
    }

    private void addIfPresent(List<String> rows, String kv) {
        if (kv == null || kv.contains("&mdash;")) return;
        rows.add(kv);
    }

    private String fmt(LocalDate date) { return date == null ? "" : DATE_FORMAT.format(date); }
    private String yesNo(Boolean value) { return value == null ? "" : (value ? "Yes" : "No"); }

    private List<String> teamLines(List<CaseDocumentModel.TeamEntry> members) {
        if (members == null || members.isEmpty()) return List.of();
        return members.stream().filter(Objects::nonNull)
                .map(t -> isBlank(t.roleName()) ? blank(t.name()) : blank(t.name()) + " — " + blank(t.roleName()))
                .filter(v -> !v.isBlank())
                .toList();
    }

    private List<String> orgLines(List<CaseDocumentModel.OrganizationEntry> orgs) {
        if (orgs == null || orgs.isEmpty()) return List.of();
        return orgs.stream().filter(Objects::nonNull).map(CaseDocumentModel.OrganizationEntry::name).map(this::blank)
                .filter(v -> !v.isBlank()).toList();
    }

    private String toListHtml(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul>");
        for (String i : items) sb.append("<li>").append(escape(i)).append("</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    private String safe(String v) {
        String t = blank(v);
        if (t.isBlank()) {
            return "&mdash;";
        }
        return escape(t).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br />");
    }
    private String blank(String v) { return v == null ? "" : v.trim(); }
    private boolean isBlank(String v) { return blank(v).isBlank(); }

    private static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

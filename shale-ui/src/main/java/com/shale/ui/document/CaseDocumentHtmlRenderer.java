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
        body.append(section("Case Information", kvGrid(List.of(
                kv("Case Name", model.caseName()),
                kv("Status", model.statusName()),
                kv("Responsible Attorney", model.responsibleAttorneyName()),
                kv("Practice Area", model.practiceAreaName())))));
        body.append(section("Contacts", kvGrid(List.of(
                kv("Caller", model.callerName()),
                kv("Client", model.clientName()),
                kv("Opposing Counsel", model.opposingCounselName())))));

        List<String> team = teamLines(model.teamMembers());
        if (!team.isEmpty()) {
            body.append(section("Team", toListHtml(team)));
        }
        List<String> orgs = orgLines(model.organizations());
        if (!orgs.isEmpty()) {
            body.append(section("Organizations", toListHtml(orgs)));
        }

        List<String> dateRows = new ArrayList<>();
        addIfPresent(dateRows, kv("Incident Date", fmt(model.incidentDate())));
        addIfPresent(dateRows, kv("SOL Date", fmt(model.solDate())));
        addIfPresent(dateRows, kv("Accepted Date", fmt(model.acceptedDate())));
        addIfPresent(dateRows, kv("Denied Date", fmt(model.deniedDate())));
        addIfPresent(dateRows, kv("Closed Date", fmt(model.closedDate())));
        addIfPresent(dateRows, kv("Intake Date", fmt(model.callerDate())));
        addIfPresent(dateRows, kv("Intake Time", model.callerTime()));
        addIfPresent(dateRows, kv("Medical Negligence Date", fmt(model.dateOfMedicalNegligence())));
        addIfPresent(dateRows, kv("Negligence Discovery Date", fmt(model.dateMedicalNegligenceWasDiscovered())));
        addIfPresent(dateRows, kv("Tort Notice Deadline", fmt(model.tortNoticeDeadline())));
        addIfPresent(dateRows, kv("Discovery Deadline", fmt(model.discoveryDeadline())));
        addIfPresent(dateRows, kv("Fee Agreement Date", fmt(model.dateFeeAgreementSigned())));
        if (!dateRows.isEmpty()) {
            body.append(section("Dates / Status Details", kvGrid(dateRows)));
        }

        List<String> additionalRows = new ArrayList<>();
        addIfPresent(additionalRows, kv("Office Case Code", model.officeCaseCode()));
        addIfPresent(additionalRows, kv("Estate Case", model.clientEstate()));
        addIfPresent(additionalRows, kv("Medical Records Received", yesNo(model.medicalRecordsReceived())));
        addIfPresent(additionalRows, kv("Fee Agreement Signed", yesNo(model.feeAgreementSigned())));
        addIfPresent(additionalRows, kv("Accepted Chronology", yesNo(model.acceptedChronology())));
        addIfPresent(additionalRows, kv("Consultant Expert Search", yesNo(model.acceptedConsultantExpertSearch())));
        addIfPresent(additionalRows, kv("Testifying Expert Search", yesNo(model.acceptedTestifyingExpertSearch())));
        addIfPresent(additionalRows, kv("Accepted Medical Literature", yesNo(model.acceptedMedicalLiterature())));
        addIfPresent(additionalRows, kv("Denied Chronology", yesNo(model.deniedChronology())));
        addIfPresent(additionalRows, kv("Received Updates", model.receivedUpdates()));
        addIfPresent(additionalRows, kv("Summary (Detail Field)", model.summary()));

        StringBuilder additionalBlock = new StringBuilder();
        if (!additionalRows.isEmpty()) {
            additionalBlock.append(kvGrid(additionalRows));
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
    .kv-grid { display: grid; grid-template-columns: minmax(210px, 34%%) 1fr; column-gap: 14px; row-gap: 8px; }
    .key { color: #4b5563; font-weight: 600; }
    .value { white-space: normal; word-break: break-word; }
    .text-block { margin-top: 12px; }
    .text-title { font-weight: 600; color: #374151; margin-bottom: 4px; }
    ul { margin: 0; padding-left: 20px; }
    li { margin: 5px 0; word-break: break-word; }
    .description { white-space: normal; word-break: break-word; }
    @media print {
      .section-title { break-after: avoid-page; }
      .kv-grid, .section { break-inside: avoid-page; }
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

    private String kvGrid(List<String> rows) {
        if (rows == null || rows.isEmpty()) return "";
        return "<div class=\"kv-grid\">" + String.join("", rows) + "</div>";
    }

    private String kv(String key, String value) {
        return "<div class=\"key\">" + escape(key) + "</div><div class=\"value\">" + safe(value) + "</div>";
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

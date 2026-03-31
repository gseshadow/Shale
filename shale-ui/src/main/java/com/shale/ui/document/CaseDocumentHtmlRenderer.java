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
                kvRow("Practice Area", model.practiceAreaName())))));

        body.append(section("Contacts", renderContactsColumns(model)));
        body.append(section("Defendants", renderDefendantLines(6)));

        List<String> dateRows = new ArrayList<>();
        addIfPresent(dateRows, kvRow("Incident Date", fmt(model.incidentDate())));
        addIfPresent(dateRows, kvRow("SOL Date", fmt(model.solDate())));
        addIfPresent(dateRows, kvRow("Accepted Date", fmt(model.acceptedDate())));
        addIfPresent(dateRows, kvRow("Denied Date", fmt(model.deniedDate())));
        addIfPresent(dateRows, kvRow("Closed Date", fmt(model.closedDate())));
        if (!dateRows.isEmpty()) {
            body.append(section("Dates / Status Details", kvTable(dateRows)));
        }

        if (!isBlank(model.description())) {
            body.append(section("Description", "<div class=\"description\">" + safe(model.description()) + "</div>"));
        }

        if (!isBlank(model.summary())) {
            body.append(section("Summary", "<div class=\"description\">" + safe(model.summary()) + "</div>"));
        }

        body.append(section("Updates", renderUpdates(model.updates())));

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
    .contacts-columns { width: 100%%; border-collapse: collapse; table-layout: fixed; }
    .contacts-columns td { width: 50%%; vertical-align: top; padding-right: 14px; }
    .contacts-columns td:last-child { padding-right: 0; padding-left: 14px; }
    .contact-card { border: 1px solid #e5e7eb; border-radius: 4px; padding: 8px 10px; }
    .contact-card-title { font-weight: 600; color: #374151; margin-bottom: 6px; }
    .description { white-space: normal; }
    .updates-list { margin: 0; padding: 0; list-style: none; }
    .update-item { margin: 0 0 12px 0; padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 4px; }
    .update-meta { font-size: 12px; color: #6b7280; margin-bottom: 6px; }
    .update-body { white-space: normal; }
    .defendant-lines { margin-top: 6px; }
    .defendant-line { height: 28px; border-bottom: 1px solid #9ca3af; margin-bottom: 8px; }
    @media print {
      .section-title { page-break-after: avoid; }
      .section { page-break-inside: avoid; }
      .kv-table { page-break-inside: avoid; }
      .contacts-columns { page-break-inside: avoid; }
      .contact-card { page-break-inside: avoid; }
      .update-item { page-break-inside: avoid; }
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

    private String renderUpdates(List<CaseDocumentModel.UpdateEntry> updates) {
        if (updates == null || updates.isEmpty()) {
            return "<div class=\"description\">&mdash;</div>";
        }
        StringBuilder html = new StringBuilder("<ul class=\"updates-list\">");
        for (CaseDocumentModel.UpdateEntry update : updates) {
            if (update == null) {
                continue;
            }
            String meta = joinMeta(fmtTs(update.createdAt()), update.author());
            html.append("<li class=\"update-item\">")
                    .append("<div class=\"update-meta\">")
                    .append(safe(meta))
                    .append("</div>")
                    .append("<div class=\"update-body\">")
                    .append(safe(update.body()))
                    .append("</div>")
                    .append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    private String renderContactsColumns(CaseDocumentModel model) {
        String caller = contactCard("Caller", List.of(
                kvRow("Name", model.callerName()),
                kvRow("Phone", model.callerPhone()),
                kvRow("Address", model.callerAddress()),
                kvRow("Email", model.callerEmail())));
        String client = contactCard("Client", List.of(
                kvRow("Name", model.clientName()),
                kvRow("Phone", model.clientPhone()),
                kvRow("Address", model.clientAddress()),
                kvRow("Email", model.clientEmail())));

        return "<table class=\"contacts-columns\"><tbody><tr><td>" + caller + "</td><td>" + client + "</td></tr></tbody></table>";
    }

    private String contactCard(String title, List<String> rows) {
        return "<div class=\"contact-card\"><div class=\"contact-card-title\">" + escape(title) + "</div>"
                + kvTable(rows) + "</div>";
    }

    private String renderDefendantLines(int count) {
        int lines = Math.max(3, count);
        StringBuilder html = new StringBuilder("<div class=\"defendant-lines\">");
        for (int i = 0; i < lines; i++) {
            html.append("<div class=\"defendant-line\"></div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String joinMeta(String timestamp, String author) {
        boolean hasTime = !isBlank(timestamp);
        boolean hasAuthor = !isBlank(author);
        if (hasTime && hasAuthor) return timestamp + " — " + author;
        if (hasTime) return timestamp;
        if (hasAuthor) return author;
        return "—";
    }

    private String fmt(LocalDate date) { return date == null ? "" : DATE_FORMAT.format(date); }
    private String fmtTs(LocalDateTime dateTime) { return dateTime == null ? "" : TS_FORMAT.format(dateTime); }

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

    private void addIfPresent(List<String> rows, String kv) {
        if (kv == null || kv.contains("&mdash;")) return;
        rows.add(kv);
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

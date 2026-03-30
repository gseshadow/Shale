package com.shale.ui.document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class CaseDocumentRenderer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu h:mm a");

    public String render(CaseDocumentModel model, CaseDocumentType type, LocalDateTime generatedAt) {
        Objects.requireNonNull(model, "model");
        if (type != CaseDocumentType.CASE_SUMMARY) {
            throw new IllegalArgumentException("Unsupported case document type: " + type);
        }
        LocalDateTime at = generatedAt == null ? LocalDateTime.now() : generatedAt;
        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Case Summary</title>
                  <style>
                    body { margin: 0; font-family: \"Segoe UI\", Arial, sans-serif; color: #1f2937; }
                    .page { max-width: 8.5in; margin: 0 auto; padding: 0.5in; }
                    .doc-header { border-bottom: 2px solid #d1d5db; margin-bottom: 18px; padding-bottom: 10px; }
                    .firm { font-size: 12px; color: #4b5563; text-transform: uppercase; }
                    h1 { font-size: 26px; margin: 6px 0; color: #111827; }
                    .generated { font-size: 12px; color: #6b7280; }
                    .section { margin-top: 18px; page-break-inside: avoid; }
                    .section-title { font-size: 16px; margin: 0 0 10px; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; }
                    .kv-grid { display: grid; grid-template-columns: minmax(170px, 32%%) 1fr; column-gap: 14px; row-gap: 8px; }
                    .key { color: #4b5563; font-weight: 600; }
                    .value { white-space: pre-wrap; word-break: break-word; }
                    ul { margin: 0; padding-left: 20px; }
                    li { margin: 5px 0; word-break: break-word; }
                    .description { white-space: pre-wrap; word-break: break-word; }
                    @media print {
                      .section-title { break-after: avoid-page; }
                      .kv-grid, .section { break-inside: avoid-page; }
                    }
                  </style>
                </head>
                <body>
                  <main class=\"page\">
                    <header class=\"doc-header\"><div class=\"firm\">Shale</div><h1>Case Summary</h1><div class=\"generated\">Generated: %s</div></header>
                    <section class=\"section\"><h2 class=\"section-title\">Case Information</h2><div class=\"kv-grid\">%s</div></section>
                    <section class=\"section\"><h2 class=\"section-title\">Contacts</h2><div class=\"kv-grid\">%s</div></section>
                    <section class=\"section\"><h2 class=\"section-title\">Team</h2>%s</section>
                    <section class=\"section\"><h2 class=\"section-title\">Organizations</h2>%s</section>
                    <section class=\"section\"><h2 class=\"section-title\">Description</h2><div class=\"description\">%s</div></section>
                  </main>
                </body>
                </html>
                """.formatted(
                escape(TS_FORMAT.format(at)),
                caseInfoHtml(model),
                contactsHtml(model),
                toListHtml(teamLines(model.teamMembers())),
                toListHtml(orgLines(model.organizations())),
                safe(model.description()));
    }

    private String caseInfoHtml(CaseDocumentModel m) {
        return kv("Case ID", Integer.toString(m.caseId())) + kv("Case Name", m.caseName()) + kv("Case Number", m.caseNumber())
                + kv("Status", m.statusName()) + kv("Responsible Attorney", m.responsibleAttorneyName())
                + kv("Practice Area", m.practiceAreaName()) + kv("Incident Date", fmt(m.incidentDate())) + kv("SOL Date", fmt(m.solDate()));
    }

    private String contactsHtml(CaseDocumentModel m) {
        return kv("Caller", m.callerName()) + kv("Client", m.clientName()) + kv("Opposing Counsel", m.opposingCounselName());
    }

    private String kv(String k, String v) { return "<div class=\"key\">" + escape(k) + "</div><div class=\"value\">" + safe(v) + "</div>"; }
    private String fmt(LocalDate d) { return d == null ? "" : DATE_FORMAT.format(d); }
    private String safe(String v) { String t = blank(v); return t.isBlank() ? "&mdash;" : escape(t); }
    private String blank(String v) { return v == null ? "" : v.trim(); }

    private List<String> teamLines(List<CaseDocumentModel.TeamEntry> members) {
        if (members == null || members.isEmpty()) return List.of("—");
        return members.stream().filter(Objects::nonNull)
                .map(t -> blank(t.roleName()).isBlank() ? blank(t.name()) : blank(t.name()) + " — " + blank(t.roleName())).toList();
    }

    private List<String> orgLines(List<CaseDocumentModel.OrganizationEntry> orgs) {
        if (orgs == null || orgs.isEmpty()) return List.of("—");
        return orgs.stream().filter(Objects::nonNull).map(CaseDocumentModel.OrganizationEntry::name).map(this::blank).toList();
    }

    private String toListHtml(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul>");
        for (String i : items) sb.append("<li>").append(escape(blank(i).isBlank() ? "—" : i)).append("</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

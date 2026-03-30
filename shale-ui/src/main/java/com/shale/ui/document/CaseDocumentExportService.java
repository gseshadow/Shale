package com.shale.ui.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

public final class CaseDocumentExportService {

    private final CaseDocumentService caseDocumentService;
    private final CaseDocumentHtmlRenderer htmlRenderer;
    private final CaseDocumentPdfExporter pdfExporter;

    public CaseDocumentExportService(CaseDocumentService caseDocumentService) {
        this(caseDocumentService, new CaseDocumentHtmlRenderer(), new CaseDocumentPdfExporter());
    }

    public CaseDocumentExportService(
            CaseDocumentService caseDocumentService,
            CaseDocumentHtmlRenderer htmlRenderer,
            CaseDocumentPdfExporter pdfExporter) {
        this.caseDocumentService = Objects.requireNonNull(caseDocumentService, "caseDocumentService");
        this.htmlRenderer = Objects.requireNonNull(htmlRenderer, "htmlRenderer");
        this.pdfExporter = Objects.requireNonNull(pdfExporter, "pdfExporter");
    }

    public GeneratedDocument exportCaseSummary(
            int caseId,
            int shaleClientId,
            CaseDocumentType type,
            CaseDocumentFormat format) throws IOException {
        CaseDocumentModel model = caseDocumentService.buildCaseDocumentModel(caseId, shaleClientId, type);
        String html = htmlRenderer.render(model, type, LocalDateTime.now());
        String safeName = sanitizeFileName(model.caseName());
        String prefix = "Case Summary - " + safeName + "-";

        if (format == CaseDocumentFormat.HTML) {
            Path htmlPath = Files.createTempFile(prefix, ".html");
            Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
            htmlPath.toFile().deleteOnExit();
            return new GeneratedDocument(htmlPath, "Case Summary - " + safeName + ".html", CaseDocumentFormat.HTML);
        }

        Path pdfPath = Files.createTempFile(prefix, ".pdf");
        pdfExporter.export(html, pdfPath);
        pdfPath.toFile().deleteOnExit();
        return new GeneratedDocument(pdfPath, "Case Summary - " + safeName + ".pdf", CaseDocumentFormat.PDF);
    }

    private String sanitizeFileName(String caseName) {
        String source = caseName == null ? "" : caseName.trim();
        if (source.isBlank()) {
            return "Untitled Case";
        }
        String cleaned = source
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "Untitled Case";
        }
        String bounded = cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
        return bounded.toLowerCase(Locale.ROOT).equals("con") ? "Untitled Case" : bounded;
    }
}

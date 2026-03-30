package com.shale.ui.document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

public final class CaseDocumentPdfExporter {

    public Path export(String html, Path outputPdfPath) throws IOException {
        Objects.requireNonNull(outputPdfPath, "outputPdfPath");
        Files.createDirectories(outputPdfPath.getParent());
        String sanitizedXhtml = sanitizeXhtml(html);
        Path debugXhtmlPath = writeDebugXhtml(sanitizedXhtml);
        logExportDebug(outputPdfPath, sanitizedXhtml, debugXhtmlPath);

        try (OutputStream out = Files.newOutputStream(outputPdfPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(sanitizedXhtml, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception ex) {
            throw new IOException("Failed to generate PDF from case summary HTML.", ex);
        }
        return outputPdfPath;
    }

    private static String sanitizeXhtml(String html) {
        String value = html == null ? "" : html;
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1);
        }
        return value.strip();
    }

    private static Path writeDebugXhtml(String xhtml) throws IOException {
        Path debugPath = Files.createTempFile("case-summary-pdf-debug-", ".xhtml");
        Files.writeString(debugPath, xhtml, StandardCharsets.UTF_8);
        debugPath.toFile().deleteOnExit();
        return debugPath;
    }

    private static void logExportDebug(Path outputPdfPath, String xhtml, Path debugXhtmlPath) {
        String snippet = xhtml.length() <= 200 ? xhtml : xhtml.substring(0, 200);
        String flattenedSnippet = snippet
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        System.out.println("[Document][PDF] using input mode=string-content");
        System.out.println("[Document][PDF] outputPath=" + outputPdfPath);
        System.out.println("[Document][PDF] xhtmlLength=" + xhtml.length());
        System.out.println("[Document][PDF] xhtmlHead(200)=" + flattenedSnippet);
        System.out.println("[Document][PDF] xhtmlDebugPath=" + debugXhtmlPath);
    }
}

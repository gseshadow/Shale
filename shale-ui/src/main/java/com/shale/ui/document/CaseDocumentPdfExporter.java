package com.shale.ui.document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

public final class CaseDocumentPdfExporter {

    private static final Pattern NAMED_ENTITY_PATTERN = Pattern.compile("&([A-Za-z][A-Za-z0-9]+);");
    private static final Map<String, String> XHTML_SAFE_ENTITIES = Map.ofEntries(
            Map.entry("nbsp", "&#160;"),
            Map.entry("ndash", "&#8211;"),
            Map.entry("mdash", "&#8212;"),
            Map.entry("lsquo", "&#8216;"),
            Map.entry("rsquo", "&#8217;"),
            Map.entry("ldquo", "&#8220;"),
            Map.entry("rdquo", "&#8221;"),
            Map.entry("hellip", "&#8230;")
    );

    public Path export(String html, Path outputPdfPath) throws IOException {
        Objects.requireNonNull(outputPdfPath, "outputPdfPath");
        Files.createDirectories(outputPdfPath.getParent());
        String preparedXhtml = sanitizeXhtml(html);
        Path debugXhtmlPath = writeDebugXhtml(preparedXhtml);
        logExportDebug(outputPdfPath, preparedXhtml, debugXhtmlPath);

        try (OutputStream out = Files.newOutputStream(outputPdfPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // openhtmltopdf uses XML parsing in this path; normalize HTML named entities first.
            builder.withHtmlContent(preparedXhtml, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception ex) {
            throw new IOException("Failed to generate PDF from case summary HTML.", ex);
        }
        return outputPdfPath;
    }

    static String sanitizeXhtml(String html) {
        String value = html == null ? "" : html;
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1);
        }
        return normalizeForPdfXhtml(value.strip());
    }

    static String normalizeForPdfXhtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        Matcher matcher = NAMED_ENTITY_PATTERN.matcher(html);
        StringBuilder normalized = new StringBuilder(html.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = XHTML_SAFE_ENTITIES.getOrDefault(name, matcher.group(0));
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
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

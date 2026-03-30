package com.shale.ui.document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

public final class CaseDocumentPdfExporter {

    public Path export(String html, Path outputPdfPath) throws IOException {
        Objects.requireNonNull(outputPdfPath, "outputPdfPath");
        Files.createDirectories(outputPdfPath.getParent());

        try (OutputStream out = Files.newOutputStream(outputPdfPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html == null ? "" : html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception ex) {
            throw new IOException("Failed to generate PDF from case summary HTML.", ex);
        }
        return outputPdfPath;
    }
}

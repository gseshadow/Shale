package com.shale.ui.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CaseDocumentPdfExporterTest {

    @Test
    void normalizeForPdfXhtml_convertsNamedHtmlEntitiesToNumericEntities() {
        String html = "<div>&mdash; &ndash; &nbsp; &ldquo;quoted&rdquo; &lsquo;single&rsquo; &hellip;</div>";

        String normalized = CaseDocumentPdfExporter.normalizeForPdfXhtml(html);

        assertEquals("<div>&#8212; &#8211; &#160; &#8220;quoted&#8221; &#8216;single&#8217; &#8230;</div>", normalized);
    }

    @Test
    void normalizeForPdfXhtml_leavesXmlAndUnknownEntitiesUntouched() {
        String html = "<p>&amp; &lt; &gt; &quot; &apos; &custom;</p>";

        String normalized = CaseDocumentPdfExporter.normalizeForPdfXhtml(html);

        assertEquals(html, normalized);
    }
}

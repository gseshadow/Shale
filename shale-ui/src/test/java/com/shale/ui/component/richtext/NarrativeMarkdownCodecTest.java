package com.shale.ui.component.richtext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NarrativeMarkdownCodecTest {
    @Test void legacyPlainTextRoundTripsExactly() { roundTrip("Legacy narrative\nwith 2 * 3 and <not-markup>."); }
    @Test void boldRoundTrips() { roundTrip("**Severe complications** developed."); }
    @Test void italicRoundTrips() { roundTrip("Review *urgent* records."); }
    @Test void underlineUsesNarrowExtension() { roundTrip("Review <u>this passage</u>."); }
    @Test void bulletListRoundTrips() { roundTrip("- First item\n- Second item"); }
    @Test void numberedListRoundTrips() { roundTrip("1. First item\n2. Second item"); }
    @Test void mixedFormattingRoundTrips() { roundTrip("**Bold and *italic <u>underlined</u>*** text"); }

    @Test void unsupportedHtmlRemainsInertLiteralText() {
        String source = "<script>alert('private')</script>";
        NarrativeDocument document = NarrativeMarkdownCodec.decode(source);
        assertEquals(source, document.text());
        assertEquals(source, NarrativeMarkdownCodec.encode(document));
    }

    @Test void formattedContentHasReadableProjection() {
        assertTrue(NarrativeMarkdownCodec.containsFormatting("**Important**\n- Call client"));
        assertEquals("Important\n• Call client", NarrativeMarkdownCodec.plainText("**Important**\n- Call client"));
    }

    private static void roundTrip(String markdown) {
        assertEquals(markdown, NarrativeMarkdownCodec.encode(NarrativeMarkdownCodec.decode(markdown)));
    }
}

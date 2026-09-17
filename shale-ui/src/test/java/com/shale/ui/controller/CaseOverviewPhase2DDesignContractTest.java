package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the A.2 Primary Link and Updates composition without asserting JavaFX skin geometry. */
final class CaseOverviewPhase2DDesignContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path COMPONENTS = Path.of("src/main/resources/css/foundation/content-components.css");
    private static final Path APP = Path.of("src/main/resources/css/app.css");

    @Test
    void primaryLinkIsSeparateAfterDetailsAndReusesTheSharedCardFactory() throws Exception {
        String fxml = Files.readString(FXML);
        String source = Files.readString(CONTROLLER);
        int details = fxml.indexOf("fx:id=\"overviewDetailsGrid\"");
        int primary = fxml.indexOf("fx:id=\"ovPrimaryLinkSection\"");
        int parties = fxml.indexOf("fx:id=\"ovPartiesBox\"");

        assertTrue(details >= 0 && details < primary && primary < parties,
                "Primary Link must remain a separate main-column section after Case Details");
        String section = fxml.substring(primary, parties);
        assertTrue(section.contains("fx:value=\"shale-section-card\"")
                        && section.contains("fx:value=\"primary-link-section\"")
                        && section.contains("styleClass=\"shale-section-title\""));
        assertTrue(source.contains("caseLinkCardFactory.create(link, CaseLinkCardFactory.Variant.COMPACT"),
                "Overview must continue to use CaseLinkCardFactory rather than duplicate its markup");
        assertTrue(source.contains("() -> onOpenOverviewPrimaryLink(link), () -> onEditCaseLink(link)"),
                "existing activation and edit behavior must remain attached");
    }

    @Test
    void updateRailKeepsComposerSearchAndOneIndependentScrollHost() throws Exception {
        String fxml = Files.readString(FXML);
        int railStart = fxml.indexOf("fx:id=\"caseUpdatesPane\"");
        String rail = fxml.substring(railStart, fxml.indexOf("</right>", railStart));

        assertTrue(rail.contains("styleClass=\"shale-update-rail\""));
        assertTrue(rail.contains("styleClass=\"shale-section-title\""));
        assertTrue(rail.contains("styleClass=\"shale-update-composer\""));
        assertTrue(rail.contains("styleClass=\"shale-update-search\""));
        assertTrue(rail.contains("styleClass=\"transparent-scroll shale-update-list-scroll\""));
        assertTrue(rail.contains("VBox.vgrow=\"ALWAYS\""));
        assertEquals(1, count(rail, "<ScrollPane"), "the update feed should have one independent scroll host");
        assertTrue(rail.indexOf("caseUpdatesComposerArea") < rail.indexOf("caseUpdatesSearchField"));
        assertTrue(rail.indexOf("caseUpdatesSearchField") < rail.indexOf("caseUpdatesScrollPane"));
    }

    @Test
    void distinctStatesAndCreatorOnlyEditRemainExplicit() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("Loading updates…\", \"shale-update-loading"));
        assertTrue(source.contains("Updates could not be loaded. Try refreshing the case."));
        assertTrue(source.contains("searchQuery.isBlank() ? \"No updates yet.\" : \"No updates found.\""));
        assertTrue(source.contains("actorUserId.intValue() == createdByUserId.intValue()"));
        assertTrue(source.contains("if (canEditCaseUpdate(dto))"));
        assertFalse(source.contains("createdByDisplayName().equals"),
                "update authorization must not be inferred from a display name");
    }

    @Test
    void phase2dPaintHasOneThemeOwnedFoundationOwner() throws Exception {
        String css = Files.readString(COMPONENTS);
        String app = Files.readString(APP);
        for (String selector : new String[] { ".shale-update-rail", ".shale-update-composer",
                ".shale-update-search", ".shale-update-card", ".primary-link-section" }) {
            assertEquals(1, count(css, selector + " {"), "expected one foundation owner for " + selector);
            assertFalse(app.contains(selector), "app.css must not compete for " + selector);
        }
        String phase = css.substring(css.indexOf("/* Update rail"));
        assertFalse(phase.matches("(?s).*(#[0-9a-fA-F]{3,8}|rgba?\\().*"),
                "Phase 2D theme paint must use looked-up semantic colors");
    }

    private static int count(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}

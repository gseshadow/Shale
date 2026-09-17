package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseOverviewPhase2CDesignContractTest {
    @Test
    void detailsCardAndToolbarComposeCanonicalA2ContractsWithoutMigratingDeferredSections() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        int details = fxml.indexOf("fx:id=\"overviewActionToolbar\"");
        int primaryLink = fxml.indexOf("fx:id=\"ovPrimaryLinkSection\"");
        String migrated = fxml.substring(fxml.lastIndexOf("<VBox", details), primaryLink);

        assertTrue(migrated.contains("fx:value=\"shale-section-card\""));
        assertTrue(migrated.contains("styleClass=\"shale-section-title\""));
        assertTrue(migrated.contains("styleClass=\"case-details-grid\""));
        assertTrue(migrated.contains("styleClass=\"shale-property-row-label\""));
        assertTrue(migrated.contains("styleClass=\"shale-property-row-value\""));
        assertFalse(migrated.contains("TableView"), "Case Details must remain a property layout, not a spreadsheet");
        assertTrue(migrated.indexOf("editOverviewButton") < migrated.indexOf("generateSummaryMenuButton"));
        assertTrue(migrated.indexOf("generateSummaryMenuButton") < migrated.indexOf("deleteCaseButton"));

        String deferred = fxml.substring(primaryLink);
        assertTrue(deferred.contains("case-overview-section-region-case-details"),
                "Primary Link and subsequent Overview sections remain on their pre-2C presentation");
    }

    @Test
    void controllerKeepsFactoriesConfigurationAndSemanticActionHierarchy() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("ControlStyles.apply(editOverviewButton,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL)"));
        assertTrue(source.contains("ControlStyles.apply(generateSummaryMenuButton,ControlStyles.Purpose.PRIMARY,ControlStyles.Size.SMALL)"));
        assertTrue(source.contains("ControlStyles.apply(deleteCaseButton,ControlStyles.Purpose.DANGER,ControlStyles.Size.SMALL)"));
        assertTrue(source.contains("PracticeAreaIndicatorFactory.createPracticeAreaPill"));
        assertTrue(source.contains("StatusIndicatorFactory.createStatusPill"));
        assertTrue(source.contains("overviewDateConfiguration.visibleDateTypes()"));
        assertTrue(source.contains("shale-property-row-compact"));
        assertTrue(source.contains("createOverviewPersonRow(displayName, userColorCss, \"Not assigned\")"));
    }

    @Test
    void phase2cCssUsesThemeTokensAndHasOneScopedOwner() throws Exception {
        String foundation = Files.readString(Path.of("src/main/resources/css/foundation/content-components.css"));
        String app = Files.readString(Path.of("src/main/resources/css/app.css"));
        assertEquals(1, count(foundation, ".case-details-card"));
        assertEquals(1, count(foundation, ".case-details-toolbar"));
        assertFalse(app.contains(".case-details-card"));
        assertFalse(app.contains(".case-details-toolbar"));
        String rules = foundation.substring(foundation.indexOf("/* Phase 2C Case Details"), foundation.indexOf("/* Person rows"));
        assertTrue(rules.contains("-shale-color-card-shadow"));
        assertTrue(rules.contains("-shale-color-divider"));
        assertFalse(rules.matches("(?s).*(#[0-9a-fA-F]{3,8}|rgba?\\().*"), "migrated paint must be theme-owned");
    }

    private static int count(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}

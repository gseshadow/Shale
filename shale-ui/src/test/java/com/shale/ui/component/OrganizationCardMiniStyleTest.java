package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class OrganizationCardMiniStyleTest {
    private static final Path ORGANIZATION_CARD = Path.of("src/main/java/com/shale/ui/component/OrganizationCard.java");
    private static final Path CARDS_CSS = Path.of("src/main/resources/css/foundation/cards.css");
    private static final Path REQUESTED_FROM_DIALOG = Path.of("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
    private static final Path APP_CSS = Path.of("src/main/resources/css/app.css");
    private static final Path CASE_MATERIALS = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void miniOrganizationNameUsesSharedPrimaryTextStyleInsteadOfInheritedWhiteText() throws IOException {
        String source = Files.readString(ORGANIZATION_CARD);
        String css = Files.readString(CARDS_CSS);
        String miniBlock = source.substring(source.indexOf("public void applyMini()"), source.indexOf("public void applyCompact()"));

        assertTrue(miniBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(miniBlock.contains("nameLabel.getStyleClass().addAll(\"organization-card-name\", \"organization-card-name-mini\")"));
        assertTrue(miniBlock.contains("nameLabel.setStyle(null);"));
        assertFalse(miniBlock.contains("-fx-text-fill: white"));
        assertFalse(miniBlock.contains("-fx-text-fill: #fff"));
        assertTrue(css.contains(".contact-card-name,\n.organization-card-name {\n    -fx-text-fill: -shale-color-text-primary;\n}"));
        assertTrue(css.contains(".contact-card-name-mini,\n.organization-card-name-mini"));
    }

    @Test
    void requestedFromDoesNotWorkAroundOrganizationMiniTextLocally() throws IOException {
        String source = Files.readString(REQUESTED_FROM_DIALOG);
        String resultCardBlock = source.substring(source.indexOf("OrganizationCardFactory organizationCards"), source.indexOf("TextField first"));

        assertTrue(resultCardBlock.contains("OrganizationCardFactory.Variant.MINI"));
        assertTrue(resultCardBlock.contains("card.getStyleClass().add(\"requested-from-result-card\")"));
        assertFalse(resultCardBlock.contains("organization-card-name"));
        assertFalse(resultCardBlock.contains("-fx-text-fill"));
    }

    @Test
    void requestedFromSelectionHoverAndFocusStylesDoNotOverrideMiniOrganizationNameText() throws IOException {
        String css = Files.readString(APP_CSS);
        String requestedFromCss = css.substring(css.indexOf(".requested-from-results"));

        assertTrue(requestedFromCss.contains(".requested-from-results .list-cell:filled:hover"));
        assertTrue(requestedFromCss.contains(".requested-from-results .list-cell:filled:selected"));
        assertTrue(requestedFromCss.contains(".requested-from-results .list-cell:filled:focused"));
        assertFalse(requestedFromCss.contains(".organization-card-name"));
        assertFalse(requestedFromCss.contains("-fx-text-fill: white"));
        assertFalse(requestedFromCss.contains("-fx-text-fill: #fff"));
    }

    @Test
    void fullAndCompactRetainExistingInlineTitleColorsAndClearMiniClasses() throws IOException {
        String source = Files.readString(ORGANIZATION_CARD);
        String compactBlock = source.substring(source.indexOf("public void applyCompact()"), source.indexOf("public void applyFull()"));
        String fullBlock = source.substring(source.indexOf("public void applyFull()"), source.indexOf("public Node asNode()"));

        assertTrue(compactBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(compactBlock.contains("nameLabel.setStyle(\"-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;\")"));
        assertTrue(fullBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(fullBlock.contains("nameLabel.setStyle(\"-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;\")"));
    }

    @Test
    void knownMiniOrganizationConsumersUseSharedFactoryVariant() throws IOException {
        String requestedFrom = Files.readString(REQUESTED_FROM_DIALOG);
        String caseMaterials = Files.readString(CASE_MATERIALS);
        String caseController = Files.readString(CASE_CONTROLLER);

        assertTrue(requestedFrom.contains("OrganizationCardFactory.Variant.MINI"));
        assertFalse(caseMaterials.contains("OrganizationCardFactory.Variant.MINI"));
        assertFalse(caseController.contains("OrganizationCardFactory.Variant.MINI"));
    }
}

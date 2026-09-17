package com.shale.ui.controller;

import com.shale.ui.component.CaseCard;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.testutil.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CasesPhase3AStylingContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path JAVA = Path.of("src/main/java");

    @Test
    void casesListReusesAuthoritativeCardFactoryAndPreservesModesAndHandlers() throws Exception {
        String controller = read(JAVA.resolve("com/shale/ui/controller/CasesController.java"));
        String fxml = read(RESOURCES.resolve("fxml/cases.fxml"));

        assertTrue(controller.contains("caseCardFactory.create(new CaseCardModel("),
                "Cases must keep rendering through the authoritative CaseCardFactory.");
        assertTrue(controller.contains("initializeViewToggle()"));
        assertTrue(controller.contains("initializeGridRowActions()"));
        assertTrue(controller.contains("initializeStatusFilter()"));
        assertTrue(controller.contains("initializeExportMenu()"));
        for (String id : new String[] {"cardsViewToggle", "gridViewToggle", "casesSearchField",
                "statusFilterMenuButton", "casesSortChoice", "columnMenuButton", "exportMenuButton"}) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), id + " handler endpoint must remain present");
        }
    }

    @Test
    void casesPaintAndControlGeometryHaveCentralOwners() throws Exception {
        String fxml = read(RESOURCES.resolve("fxml/cases.fxml"));
        String controller = read(JAVA.resolve("com/shale/ui/controller/CasesController.java"));
        String cards = read(RESOURCES.resolve("css/foundation/cards.css"));
        String toolbars = read(RESOURCES.resolve("css/foundation/toolbars.css"));

        assertFalse(fxml.matches("(?s).*#[0-9a-fA-F]{3,8}.*"), "Cases FXML must not own theme paint.");
        assertFalse(controller.matches("(?s).*#[0-9a-fA-F]{3,8}.*"), "Cases controller must not own theme paint.");
        assertTrue(cards.contains(".case-card-neutral"));
        assertTrue(cards.contains("-fx-background-color: -shale-color-card-surface"));
        assertTrue(cards.contains(".case-card-neutral:focused"));
        assertTrue(cards.contains(".case-card__deadline-urgent"));
        assertTrue(toolbars.contains(".shale-segmented-control"));
        assertTrue(toolbars.contains(".cases-toolbar-controls"));
        assertFalse(toolbars.substring(toolbars.indexOf(".shale-segmented-control")).matches(
                "(?s).*rgba\\([^)]*\\).*"), "Segmented controls must use theme tokens, not local paint.");
    }

    @Test
    void factoryProducesNeutralCardWhileKeepingDatabaseIndicatorsAndSemanticStates() {
        JavaFxTestSupport.runAndWait(() -> {
            var factory = new CaseCardFactory(id -> { });
            var model = new CaseCardFactory.CaseCardModel(17, "Long Case Name", LocalDate.now(),
                    LocalDate.now().plusDays(10), null, "Attorney", "#228855", true,
                    "Denied", "#AA3344", "#3366AA");
            CaseCard card = assertInstanceOf(CaseCard.class,
                    factory.create(model, CaseCardFactory.Variant.COMPACT));

            assertTrue(card.getStyleClass().contains("case-card-neutral"));
            assertTrue(card.getStyleClass().contains("case-card-denied"));
            assertTrue(card.getStyleClass().contains("case-card-non-engagement"));
            assertTrue(card.lookupAll(".shale-status-pill").stream()
                    .anyMatch(node -> node.getStyle().contains("#AA3344")),
                    "The status indicator must retain its authoritative database color.");
            assertTrue(card.lookupAll(".case-card__practice-area-bar").stream()
                    .anyMatch(node -> node.getStyle().contains("#3366AA")),
                    "The compact accent must retain its authoritative database color.");
            assertTrue(card.lookupAll(".case-card__deadline-urgent").size() == 1,
                    "An urgent deadline must have a labelled semantic state in addition to color.");
        });
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }
}

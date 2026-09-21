package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Protects Phase 8A's semantic section ownership without coupling to JavaFX skin geometry. */
final class NewIntakePhase8APresentationContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path FXML = ROOT.resolve("shale-ui/src/main/resources/fxml/new-intake.fxml");

    @Test
    void canonicalSectionsUseOneDeterministicWorkspaceAndOneOuterScrollOwner() throws Exception {
        Document document = parseFxml();

        assertEquals(1, document.getElementsByTagName("ScrollPane").getLength(),
                "New Intake must retain one page-level vertical scroll owner");
        assertEquals("intakeWorkspace", ((Element) document.getElementsByTagName("GridPane").item(0)).getAttribute("fx:id"),
                "the outer workspace must be the deterministic responsive GridPane");
        assertEquals(1, elementsWithFxId(document, "callerSection"));
        assertEquals(1, elementsWithFxId(document, "clientSection"));
        assertEquals(1, elementsWithFxId(document, "caseSection"));
        assertEquals(1, elementsWithFxId(document, "partiesSection"));
        assertEquals(1, elementsWithFxId(document, "incidentSection"));
        assertEquals(1, elementsWithFxId(document, "intakeActionBar"),
                "the footer must remain outside the scrolling form");
    }

    @Test
    void sourceOrderAndResponsivePlacementKeepCompleteSectionsSemantic() throws Exception {
        String fxml = Files.readString(FXML);
        assertOrdered(fxml, "fx:id=\"callerSection\"", "fx:id=\"clientSection\"", "fx:id=\"caseSection\"",
                "fx:id=\"partiesSection\"", "fx:id=\"incidentSection\"");

        String controller = Files.readString(ROOT.resolve(
                "shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        assertTrue(controller.contains("INTAKE_STACK_BREAKPOINT"));
        assertTrue(controller.contains("narrowIntakeLayout == narrow"),
                "ordinary layout pulses must not repeatedly reconfigure section ownership");
        assertOrdered(controller, "placeSection(callerSection, 0, 0)", "placeSection(clientSection, 0, 1)",
                "placeSection(caseSection, 0, 2)", "placeSection(partiesSection, 0, 3)",
                "placeSection(incidentSection, 0, 4)");
        assertTrue(controller.contains("leftWorkspaceColumn.setPercentWidth(45)"));
        assertTrue(controller.contains("rightWorkspaceColumn.setPercentWidth(55)"));
    }

    @Test
    void migrationUsesSharedSemanticStylesAndThemeOwnedPartiesPaint() throws Exception {
        String fxml = Files.readString(FXML);
        String forms = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/foundation/forms.css"));
        String light = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/theme/light.css"));
        String dark = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/theme/dark.css"));

        assertTrue(fxml.contains("shale-section-card new-intake-section"));
        assertTrue(fxml.contains("shale-section-title"));
        assertTrue(fxml.contains("shale-field-label"));
        assertFalse(fxml.contains("-fx-background-color:"), "FXML must not own page paint");
        assertFalse(fxml.contains("textFill=\"#"), "feedback paint must be semantic and theme-owned");
        assertTrue(forms.contains("-shale-color-parties-section-surface"));
        assertTrue(light.contains("-shale-color-parties-section-surface"));
        assertTrue(dark.contains("-shale-color-parties-section-surface"));
    }

    @Test
    void existingBehavioralIdsAndHandlersRemainConnected() throws Exception {
        String fxml = Files.readString(FXML);
        for (String id : new String[] {"callerIsClientCheckBox", "caseNameField", "timeOfIntakeField",
                "practiceAreaHost", "statusHost", "addPartyButton", "partiesListBox", "descriptionArea",
                "summaryArea", "configuredDatesBox", "cancelButton", "createIntakeButton"}) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), "missing authoritative control " + id);
        }
        assertTrue(fxml.contains("onAction=\"#onCancel\""));
        assertTrue(fxml.contains("onAction=\"#onCreateIntake\""));
        assertTrue(fxml.contains("hbarPolicy=\"NEVER\""));
        assertFalse(fxml.contains("text=\"New Intake\""),
                "the shared secondary-window shell remains the sole visible title owner");

		String launcher = Files.readString(ROOT.resolve(
				"shale-ui/src/main/java/com/shale/ui/navigation/SceneManager.java"));
		assertTrue(launcher.contains("WindowSizingUtil.sizeModalStage(dialog, stage, 1180, 760, 680, 620)"),
				"the existing modal shell must use screen-aware sizing with reasonable minimum dimensions");
		assertTrue(launcher.contains("ThemeManager.application().register(dialogScene)"));
    }

    private static Document parseFxml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(FXML.toFile());
    }

    private static int elementsWithFxId(Document document, String id) {
        NodeList all = document.getElementsByTagName("*");
        int count = 0;
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if (id.equals(element.getAttributeNS("http://javafx.com/fxml", "id"))) count++;
        }
        return count;
    }

    private static void assertOrdered(String source, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value, previous + 1);
            assertTrue(current > previous, "expected semantic order for " + value);
            previous = current;
        }
    }
}

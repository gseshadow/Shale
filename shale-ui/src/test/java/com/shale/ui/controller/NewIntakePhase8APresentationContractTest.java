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
import org.w3c.dom.Node;
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
		Element footer = elementWithFxId(document, "intakeActionBar");
		Element scroll = elementWithFxId(document, "intakeScrollPane");
		assertFalse(isDescendantOf(footer, scroll), "the fixed action footer must not become scroll content");
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
		assertTrue(controller.contains("wideRightIntakeColumn.getChildren().setAll(caseSection, partiesSection, incidentSection)"),
				"wide layout must stack Parties and Incident independently of the taller Client column");
		assertTrue(controller.contains("GridPane.setVgrow(section, Priority.NEVER)"),
				"intake sections must not absorb unused viewport height");
        assertTrue(controller.contains("leftWorkspaceColumn.setPercentWidth(45)"));
        assertTrue(controller.contains("rightWorkspaceColumn.setPercentWidth(55)"));
    }

    @Test
    void migrationUsesSharedSemanticStylesAndThemeOwnedPartiesPaint() throws Exception {
        String fxml = Files.readString(FXML);
        String forms = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/foundation/forms.css"));
		String app = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/app.css"));
        String light = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/theme/light.css"));
        String dark = Files.readString(ROOT.resolve("shale-ui/src/main/resources/css/theme/dark.css"));

		for (String id : new String[] {"callerSection", "clientSection", "caseSection", "partiesSection", "incidentSection"}) {
			String classes = elementWithFxId(parseFxml(), id).getAttribute("styleClass");
			assertTrue(classes.contains("shale-section-card") && classes.contains("new-intake-section"),
					id + " must retain Intake section-card ownership");
		}
        assertTrue(fxml.contains("shale-section-title"));
        assertTrue(fxml.contains("shale-field-label"));
        assertFalse(fxml.contains("-fx-background-color:"), "FXML must not own page paint");
        assertFalse(fxml.contains("textFill=\"#"), "feedback paint must be semantic and theme-owned");
		assertTrue(app.contains("@import \"foundation/forms.css\";"), "Intake's semantic stylesheet must be loaded");
		assertTrue(app.indexOf("foundation/forms.css") < app.indexOf("foundation/party-windows.css"),
				"Party-window styling must not replace or precede Intake form ownership");
        assertTrue(forms.contains("-shale-color-parties-section-surface"));
        assertTrue(light.contains("-shale-color-parties-section-surface"));
		assertTrue(dark.contains("-shale-color-parties-section-surface"));
		for (String token : new String[] {"caller", "client", "case", "incident"}) {
			assertTrue(forms.contains("-shale-color-intake-" + token + "-surface"));
			assertTrue(light.contains("-shale-color-intake-" + token + "-surface"));
			assertTrue(dark.contains("-shale-color-intake-" + token + "-surface"));
		}
	}

	@Test
	void emptyPartiesRemainContentSizedAndIncidentImmediatelySharesItsColumn() throws Exception {
		Element parties = elementWithFxId(parseFxml(), "partiesSection");
		assertEquals("-Infinity", parties.getAttribute("maxHeight"),
				"empty Parties must use Region.USE_PREF_SIZE rather than an arbitrary fixed height");
		assertEquals("TOP", parties.getAttribute("GridPane.valignment"));
		String fxml = Files.readString(FXML);
		assertTrue(fxml.contains("text=\"No pending parties yet.\""));
		assertTrue(fxml.contains("fx:id=\"partiesListBox\""), "staged party cards must retain their render host");
		assertFalse(parties.hasAttribute("VBox.vgrow"), "Parties must not receive unbounded VBox growth");
		assertFalse(parties.hasAttribute("prefHeight"), "Parties must not reserve an arbitrary preferred height");
	}

	@Test
	void labelsAndActionsRetainSharedSemanticSpacingOwnership() throws Exception {
		String fxml = Files.readString(FXML);
		String controller = Files.readString(ROOT.resolve(
				"shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));
		for (String action : new String[] {"cancelButton", "createIntakeButton", "selectPracticeAreaButton",
				"selectStatusButton", "addPartyButton"}) {
			assertTrue(fxml.contains("fx:id=\"" + action + "\""), "missing Intake action " + action);
		}
		assertTrue(controller.contains("ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY)"));
		assertTrue(controller.contains("ControlStyles.apply(createIntakeButton, ControlStyles.Purpose.PRIMARY)"));
		assertTrue(controller.contains("ControlStyles.apply(addPartyButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL)"));
		assertTrue(fxml.contains("styleClass=\"shale-field-label\""),
				"field labels must retain shared typography and spacing ownership");
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

	private static Element elementWithFxId(Document document, String id) {
		NodeList all = document.getElementsByTagName("*");
		for (int i = 0; i < all.getLength(); i++) {
			Element element = (Element) all.item(i);
			if (id.equals(element.getAttributeNS("http://javafx.com/fxml", "id"))) return element;
		}
		throw new AssertionError("missing fx:id " + id);
	}

	private static boolean isDescendantOf(Node candidate, Node ancestor) {
		for (Node current = candidate.getParentNode(); current != null; current = current.getParentNode()) {
			if (current == ancestor) return true;
		}
		return false;
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

package com.shale.ui.controller.support;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class RequestedFromWorkflowDialogLayoutTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "JavaFX layout regression test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void contactSectionsAndFooterHaveOrderedNonOverlappingBounds() {
        verifyPopulatedLayout("contact", "Case Contacts", "All Contacts");
    }

    @Test
    void organizationSectionsAndFooterHaveOrderedNonOverlappingBounds() {
        verifyPopulatedLayout("organization", "Case Organizations", "All Organizations");
    }

    @Test
    void emptyContactCaseSectionIsInvisibleAndUnmanaged() {
        verifyEmptyCaseSection("contact");
    }

    @Test
    void emptyOrganizationCaseSectionIsInvisibleAndUnmanaged() {
        verifyEmptyCaseSection("organization");
    }

    @Test
    void applicationStylesheetLoadsWithTheSelectionLayout() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture("contact", true);
            String stylesheet = getClass().getResource("/css/app.css").toExternalForm();
            assertNotNull(stylesheet);
            fixture.scene().getStylesheets().add(stylesheet);
            fixture.pane().applyCss();
            fixture.pane().layout();
            assertTrue(fixture.layout().caseHeading.getStyleClass().contains("section-heading"));
        });
    }

    private void verifyPopulatedLayout(String entityType, String expectedCaseHeading, String expectedDirectoryHeading) {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture(entityType, true);
            RequestedFromWorkflowDialog.SelectionLayout layout = fixture.layout();
            assertTrue(layout.caseSection.isVisible() && layout.caseSection.isManaged());
            assertTrue(expectedCaseHeading.equals(layout.caseHeading.getText()));
            assertTrue(expectedDirectoryHeading.equals(layout.directoryHeading.getText()));

            assertBelow(layout.caseSection.getChildren().get(1), layout.caseHeading, "case cards must follow the case heading");
            assertBelow(layout.directoryHeading, layout.caseSection, "directory heading must follow the complete case section");
            assertBelow(layout.search, layout.directoryHeading, "search must follow the directory heading");
            assertBelow(layout.results, layout.search, "directory results must begin below search");

            Button back = (Button) fixture.pane().lookupButton(fixture.backType());
            Button add = (Button) fixture.pane().lookupButton(fixture.addType());
            Button cancel = (Button) fixture.pane().lookupButton(ButtonType.CANCEL);
            for (Button button : new Button[]{back, add, cancel}) {
                assertTrue(button.isVisible(), button.getText() + " must remain visible");
                assertBelow(button, fixture.content(), button.getText() + " must remain below main content");
                assertTrue(sceneBounds(button).getMaxY() <= sceneBounds(fixture.pane()).getMaxY() + 0.5,
                        button.getText() + " must remain inside the dialog");
            }
        });
    }

    private void verifyEmptyCaseSection(String entityType) {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture(entityType, false);
            RequestedFromWorkflowDialog.SelectionLayout layout = fixture.layout();
            assertFalse(layout.caseSection.isVisible());
            assertFalse(layout.caseSection.isManaged());
            assertBelow(layout.search, layout.directoryHeading, "managed directory controls retain normal order");
        });
    }

    private Fixture fixture(String entityType, boolean withCaseEntries) {
        FlowPane cards = new FlowPane(8, 8);
        cards.setPrefWrapLength(650);
        for (int i = 0; i < 6; i++) {
            Region card = new Region();
            card.setPrefSize(205, 58);
            cards.getChildren().add(card);
        }
        ScrollPane caseScroll = RequestedFromWorkflowDialog.adaptiveCasePartyScrollPane(cards, 180);
        TextField search = new TextField();
        Label status = new Label();
        ListView<String> results = new ListView<>();
        results.getItems().addAll("One", "Two", "Three");
        results.setMinHeight(0);
        RequestedFromWorkflowDialog.SelectionLayout layout = RequestedFromWorkflowDialog.buildSelectionLayout(
                entityType, caseScroll, search, status, results);
        RequestedFromWorkflowDialog.setCaseSectionVisible(layout, withCaseEntries);

        VBox content = new VBox(12, new Label("Select Existing"), new Label("Choose an entry."), layout.root);
        content.setMinHeight(0);
        VBox.setVgrow(layout.root, javafx.scene.layout.Priority.ALWAYS);
        BorderPane contentRegion = new BorderPane(content);
        contentRegion.setMinSize(0, 0);
        ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.LEFT);
        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        DialogPane pane = new DialogPane();
        pane.setContent(contentRegion);
        pane.getButtonTypes().addAll(backType, addType, ButtonType.CANCEL);
        Scene scene = new Scene(pane, 800, 700);
        pane.resize(800, 700);
        pane.applyCss();
        pane.layout();
        pane.applyCss();
        pane.layout();
        return new Fixture(layout, pane, scene, contentRegion, backType, addType);
    }

    private static void assertBelow(Node lower, Node upper, String message) {
        Bounds lowerBounds = sceneBounds(lower);
        Bounds upperBounds = sceneBounds(upper);
        assertTrue(lowerBounds.getMinY() + 0.5 >= upperBounds.getMaxY(),
                message + ": upper=" + upperBounds + ", lower=" + lowerBounds);
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    private static boolean hasDisplay() {
        return System.getenv("DISPLAY") != null
                || System.getenv("WAYLAND_DISPLAY") != null
                || System.getProperty("os.name", "").toLowerCase().contains("win")
                || System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private record Fixture(RequestedFromWorkflowDialog.SelectionLayout layout, DialogPane pane, Scene scene,
                           Node content, ButtonType backType, ButtonType addType) {}
}

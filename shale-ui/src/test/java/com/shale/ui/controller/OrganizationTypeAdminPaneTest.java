package com.shale.ui.controller;

import static com.shale.core.service.OrganizationServicePort.OrganizationTypeOrigin.TENANT;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeDefinition;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class OrganizationTypeAdminPaneTest {
    @Test void generatedKeysAreStableLowercaseSnakeCase() {
        assertEquals("medical_practice", OrganizationTypeAdminPane.systemKey("  Médical Practice  "));
        assertEquals("court_appeals", OrganizationTypeAdminPane.systemKey("Court / Appeals"));
    }

    @Test void editorValidationProtectsDefinitionContract() {
        assertEquals("Name is required.", OrganizationTypeAdminPane.validate(" ", "", "valid_key", "0"));
        assertEquals("Description must be at most 500 characters.", OrganizationTypeAdminPane.validate("Valid", "x".repeat(501), "valid_key", "0"));
        assertEquals("Internal key must use lowercase snake_case.", OrganizationTypeAdminPane.validate("Valid", "", "Invalid Key", "0"));
        assertNull(OrganizationTypeAdminPane.validate(" Valid ", "optional", "valid_key", "3"));
    }

    @Test void addAndEditShareNonCollapsingCompleteLabelLayout() {
        JavaFxTestSupport.runAndWait(() -> {
            TextField addKey = new TextField();
            GridPane add = form(addKey, true);
            TextField editKey = new TextField("hospital");
            GridPane edit = form(editKey, false);

            for (GridPane form : List.of(add, edit)) {
                List<String> labels = form.getChildren().stream()
                        .filter(node -> GridPane.getColumnIndex(node) == null || GridPane.getColumnIndex(node) == 0)
                        .filter(Label.class::isInstance).map(Label.class::cast)
                        .sorted(Comparator.comparingInt(node -> GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node)))
                        .map(Label::getText).toList();
                assertEquals(List.of("Name", "Description", "Color", "Sort order", "Internal key"), labels,
                        "the shared Organization Type editor must render complete field labels");
                ColumnConstraints labelColumn = form.getColumnConstraints().getFirst();
                assertEquals(112, labelColumn.getMinWidth(), "label column must not collapse under Windows scaling");
                assertEquals(112, labelColumn.getPrefWidth(), "label column should retain its stable preferred width");
                assertEquals(Priority.NEVER, labelColumn.getHgrow());
                form.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .forEach(label -> assertEquals(Region.USE_PREF_SIZE, label.getMinWidth()));
                assertTrue(form.minWidth(-1) <= OrganizationTypeAdminPane.DIALOG_MIN_WIDTH,
                        "form must fit inside the supported dialog minimum without horizontal clipping");
            }
            assertTrue(addKey.isEditable(), "Internal key remains editable during initial creation");
            assertFalse(editKey.isEditable(), "Internal key remains read-only during editing");
            assertTrue(OrganizationTypeAdminPane.DIALOG_MIN_WIDTH <= 500);
            assertTrue(OrganizationTypeAdminPane.DIALOG_PREF_WIDTH <= 600, "layout fix must not require an oversized dialog");
        });
    }

    @Test void definitionCardsAreCompactHorizontalRowsWithProtectedRightActions() {
        JavaFxTestSupport.runAndWait(() -> {
            OrganizationTypeAdminPane pane = unauthorizedPane();
            OrganizationTypeDefinition row = new OrganizationTypeDefinition(3, 7, "long_key",
                    "A very long Organization Type name that must yield space to actions",
                    "A very long description that remains bounded and cannot push lifecycle controls outside the row.",
                    "#315FBA", 4, true, false, TENANT, new byte[] { 9 });
            HBox card = (HBox) pane.card(row, false, true);

            assertTrue(card.getStyleClass().contains("organization-type-definition-card"));
            assertEquals(OrganizationTypeAdminPane.CARD_MIN_HEIGHT, card.getMinHeight());
            assertEquals(OrganizationTypeAdminPane.CARD_PREF_HEIGHT, card.getPrefHeight());
            assertEquals(OrganizationTypeAdminPane.CARD_MAX_HEIGHT, card.getMaxHeight());
            assertTrue(card.getPrefHeight() < 95, "old 180–190px vertical cards must not return");
            assertEquals(3, card.getChildren().size(), "row is swatch, flexible content, then actions");
            VBox content = (VBox) card.getChildren().get(1);
            HBox actions = (HBox) card.getChildren().get(2);
            assertEquals(Priority.ALWAYS, HBox.getHgrow(content));
            assertEquals(0, content.getMinWidth(), "long content must shrink before displacing actions");
            assertEquals(Region.USE_PREF_SIZE, actions.getMinWidth(), "right-side actions retain their footprint");
            assertTrue(actions.getStyleClass().contains("organization-type-card-actions"));
            Button edit = button(actions, "Edit"), deactivate = button(actions, "Deactivate"), remove = button(actions, "Remove");
            assertTrue(edit.getStyleClass().contains("shale-control-secondary"));
            assertTrue(deactivate.getStyleClass().contains("shale-control-secondary"));
            assertFalse(deactivate.getStyleClass().contains("shale-control-ghost"));
            assertTrue(remove.getStyleClass().contains("shale-control-danger"));
            assertEquals("Deactivate Organization Type", deactivate.getAccessibleText());
            pane.dispose();
        });
    }

    @Test void settingsWiringPreservesSectionsServiceBoundaryAndRowVerDelegation() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String settings = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String pane = Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationTypeAdminPane.java"));
        assertTrue(fxml.contains("Organization Types"));
        assertTrue(settings.contains("organizationTypeAdministrationSection.setVisible(visible)"));
        assertTrue(settings.contains("appState.isAdmin()"));
        for (String section : List.of("Active / effective", "Inactive tenant definitions", "Removed tenant definitions")) assertTrue(pane.contains(section));
        for (String operation : List.of("createOrganizationType", "updateOrganizationType", "setOrganizationTypeActive", "removeOrganizationType", "restoreOrganizationType")) assertTrue(pane.contains(operation), operation);
        assertTrue(pane.contains("existing.rowVer()"), "edit must delegate the authoritative RowVer");
        assertTrue(pane.contains("r.rowVer()"), "lifecycle actions must delegate the authoritative RowVer");
        assertFalse(pane.contains("OrganizationDao"));
        assertFalse(pane.contains("OrganizationOrganizationTypes"), "Settings must not introduce Organization assignment controls");
    }

    @Test void lifecycleCopyExplainsHistoricalPreservationAndGlobalFallback() throws Exception {
        String pane = Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationTypeAdminPane.java"));
        assertTrue(pane.contains("Existing Organization assignments are preserved for historical display"));
        assertTrue(pane.contains("The global definition will become effective again"));
        assertTrue(pane.contains("Historical Organization assignments are not changed"));
    }

    private static GridPane form(TextField key, boolean creating) {
        return OrganizationTypeAdminPane.editorForm(new TextField(), new TextArea(), new ColorPicker(), new TextField(), key, creating);
    }

    private static Button button(HBox actions, String text) {
        return actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static OrganizationTypeAdminPane unauthorizedPane() {
        OrganizationServicePort service = (OrganizationServicePort) Proxy.newProxyInstance(
                OrganizationTypeAdminPaneTest.class.getClassLoader(), new Class<?>[] { OrganizationServicePort.class },
                (proxy, method, arguments) -> { throw new AssertionError("unauthorized test pane must not call " + method.getName()); });
        return new OrganizationTypeAdminPane(service, new AppState());
    }
}

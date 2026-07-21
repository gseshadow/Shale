package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import com.shale.core.dto.MaterialTypeDto;
import com.shale.ui.component.factory.ColoredLookupComboBoxCellFactory;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class MaterialTypeSelectorParityTest {
    private static String read(String path) {
        try { return Files.readString(Path.of(path)); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static final String CASE_CONTROLLER = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final String MATERIALS = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final String REQUEST_FORM = MATERIALS.substring(MATERIALS.indexOf("final class MaterialRequestForm"), MATERIALS.indexOf("final class MaterialItemForm"));
    private static final String SHARED_CELL = read("src/main/java/com/shale/ui/component/factory/ColoredLookupComboBoxCellFactory.java");

    @Test
    void newRequestAndAddLinkUseSameCompleteColoredLookupSelectorSetup() {
        assertTrue(CASE_CONTROLLER.contains("ColoredLookupComboBoxCellFactory.configure(type, LinkTypeDto::name, LinkTypeDto::color)"));
        assertTrue(REQUEST_FORM.contains("configureMaterialTypeSelector(type)"));
        assertTrue(REQUEST_FORM.contains("ColoredLookupComboBoxCellFactory.configure(type, MaterialTypeDto::name, MaterialTypeDto::color)"));
        assertTrue(SHARED_CELL.contains("comboBox.setMaxWidth(Double.MAX_VALUE)"));
        assertTrue(SHARED_CELL.contains("comboBox.setConverter(new StringConverter<>()"));
        assertTrue(SHARED_CELL.contains("comboBox.setCellFactory(list -> popupCell(name, color))"));
        assertTrue(SHARED_CELL.contains("comboBox.setButtonCell(buttonCell(name))"));
        assertFalse(REQUEST_FORM.contains("coloredTypeCell"));
    }

    @Test
    void popupRowsRenderConfiguredColoredPillAndDisplayNameText() {
        assertTrue(SHARED_CELL.contains("LinkTypeIndicatorFactory.createLinkTypePill(displayName, color.apply(item), LinkTypeIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(SHARED_CELL.contains("new HBox(getGraphicTextGap(), pill, display)"));
        assertTrue(SHARED_CELL.contains("content.setMinWidth(Region.USE_PREF_SIZE)"));
        assertTrue(SHARED_CELL.contains("pill.setMinWidth(Region.USE_PREF_SIZE)"));
        assertTrue(SHARED_CELL.contains("display.setMinWidth(Region.USE_PREF_SIZE)"));
        assertTrue(REQUEST_FORM.contains("MaterialTypeDto::color"));
        assertTrue(REQUEST_FORM.contains("MaterialTypeDto::name"));
    }

    @Test
    void actualNewRequestMaterialTypeComboInstallsRuntimePillRenderer() throws Exception {
        assumeDisplayAvailable();
        ensureToolkit();
        ComboBox<MaterialTypeDto> combo = new ComboBox<>();
        MaterialRequestForm.configureMaterialTypeSelector(combo);
        MaterialTypeDto sample = new MaterialTypeDto(44, 7, "MEDICAL_RECORDS", "Medical records", null, "#2563EB", 10);
        ListCell<MaterialTypeDto> popupCell = combo.getCellFactory().call(null);
        updateCell(popupCell, sample, false);

        assertNull(popupCell.getText(), "Popup row text must come from the polished pill+label graphic, not dot-only cell text.");
        assertInstanceOf(HBox.class, popupCell.getGraphic());
        HBox row = (HBox) popupCell.getGraphic();
        assertEquals(2, row.getChildren().size());
        Label pill = assertLabel(row.getChildren().get(0));
        Label display = assertLabel(row.getChildren().get(1));
        assertEquals("Medical records", pill.getText());
        assertTrue(pill.getStyleClass().contains("shale-link-type-pill"));
        assertTrue(pill.getStyleClass().contains("shale-practice-area-pill-compact"));
        assertTrue(pill.getStyle().contains("#2563EB"));
        assertEquals("Medical records", display.getText());
        assertFalse("●".equals(pill.getText()) || "●".equals(display.getText()));
    }

    @Test
    void selectedButtonCellUsesAddLinkPresentation() throws Exception {
        assumeDisplayAvailable();
        ensureToolkit();
        MaterialTypeDto sample = new MaterialTypeDto(44, 7, "MEDICAL_RECORDS", "Medical records", null, "#2563EB", 10);
        ComboBox<MaterialTypeDto> combo = new ComboBox<>();
        MaterialRequestForm.configureMaterialTypeSelector(combo);
        ListCell<MaterialTypeDto> materialButton = combo.getButtonCell();
        updateCell(materialButton, sample, false);

        ListCell<MaterialTypeDto> sharedButton = ColoredLookupComboBoxCellFactory.buttonCell(MaterialTypeDto::name);
        updateCell(sharedButton, sample, false);
        assertEquals(sharedButton.getText(), materialButton.getText());
        assertEquals(sharedButton.getGraphic(), materialButton.getGraphic());
        assertEquals("Medical records", materialButton.getText());
        assertNull(materialButton.getGraphic());
    }


    @Test
    void noTemporaryDiagnosticsRemain() {
        assertFalse(REQUEST_FORM.contains("RENDERER V4 ACTIVE"));
        assertFalse(REQUEST_FORM.contains("NEW REQUEST MATERIAL TYPE RENDERER V4"));
        assertFalse(SHARED_CELL.contains("NEW REQUEST MATERIAL TYPE RENDERER V4"));
        assertFalse(SHARED_CELL.contains("PopupRowStructure"));
    }

    @Test
    void materialTypeIdIsNotDisplayedBySelector() {
        assertFalse(REQUEST_FORM.contains("MaterialTypeId"));
        assertFalse(REQUEST_FORM.contains("materialTypeId()+\""));
        assertFalse(REQUEST_FORM.contains("String.valueOf(x.id())"));
        assertTrue(SHARED_CELL.contains("return item == null ? \"\" : name.apply(item)"));
    }

    @Test
    void unrelatedNewRequestWorkflowRemainsIntact() {
        assertTrue(REQUEST_FORM.contains("field(\"Title *\",titleField)"));
        assertTrue(REQUEST_FORM.contains("field(\"Requested From *\",new VBox(8,sourceButtons,sourceCard))"));
        assertTrue(REQUEST_FORM.contains("AssignedUserPickerDialog.show"));
        assertTrue(REQUEST_FORM.contains("plusMonths(1)"));
        assertTrue(REQUEST_FORM.contains("plusWeeks(2)"));
        assertTrue(REQUEST_FORM.contains("field(\"Request Method *\",method)"));
        assertTrue(REQUEST_FORM.contains("field(\"Status *\",status)"));
        assertTrue(REQUEST_FORM.contains("setResultConverter(bt->bt==ButtonType.OK?new Values(type.getValue().id()"));
    }

    private static Label assertLabel(Node node) {
        assertInstanceOf(Label.class, node);
        return (Label) node;
    }

    private static void assumeDisplayAvailable() {
        assumeTrue(System.getenv("DISPLAY") != null && !System.getenv("DISPLAY").isBlank(),
                "JavaFX runtime behavior test requires a display server.");
    }

    private static void ensureToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private static <T> void updateCell(ListCell<T> cell, T item, boolean empty) throws Exception {
        Method updateItem = ListCell.class.getDeclaredMethod("updateItem", Object.class, boolean.class);
        updateItem.setAccessible(true);
        updateItem.invoke(cell, item, empty);
    }
}

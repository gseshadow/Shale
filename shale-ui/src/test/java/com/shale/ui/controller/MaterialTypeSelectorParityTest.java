package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MaterialTypeSelectorParityTest {
    private static String read(String path) {
        try { return Files.readString(Path.of(path)); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static final String CASE_CONTROLLER = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final String MATERIALS = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final String REQUEST_FORM = MATERIALS.substring(MATERIALS.indexOf("final class MaterialRequestForm"), MATERIALS.indexOf("final class MaterialItemForm"));
    private static final String SHARED_CELL = read("src/main/java/com/shale/ui/component/factory/ColoredLookupComboBoxCellFactory.java");

    @Test
    void newRequestAndAddLinkUseSameSharedColoredLookupCells() {
        assertTrue(CASE_CONTROLLER.contains("ColoredLookupComboBoxCellFactory.popupCell(LinkTypeDto::name, LinkTypeDto::color)"));
        assertTrue(CASE_CONTROLLER.contains("ColoredLookupComboBoxCellFactory.buttonCell(LinkTypeDto::name)"));
        assertTrue(REQUEST_FORM.contains("ColoredLookupComboBoxCellFactory.popupCell(MaterialTypeDto::name, MaterialTypeDto::color)"));
        assertTrue(REQUEST_FORM.contains("ColoredLookupComboBoxCellFactory.buttonCell(MaterialTypeDto::name)"));
        assertFalse(REQUEST_FORM.contains("coloredTypeCell"));
    }

    @Test
    void popupRowsRenderConfiguredColoredPillAndDisplayNameText() {
        assertTrue(SHARED_CELL.contains("setText(displayName)"));
        assertTrue(SHARED_CELL.contains("LinkTypeIndicatorFactory.createLinkTypePill(displayName, color.apply(item), LinkTypeIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(REQUEST_FORM.contains("MaterialTypeDto::color"));
        assertTrue(REQUEST_FORM.contains("MaterialTypeDto::name"));
    }

    @Test
    void selectedButtonCellUsesAddLinkPresentation() {
        assertTrue(SHARED_CELL.contains("public static <T> ListCell<T> buttonCell"));
        assertTrue(SHARED_CELL.contains("setText(empty || item == null ? null : name.apply(item))"));
        assertTrue(SHARED_CELL.contains("setGraphic(null)"));
    }

    @Test
    void materialTypeIdIsNotDisplayedBySelector() {
        assertFalse(REQUEST_FORM.contains("MaterialTypeId"));
        assertFalse(REQUEST_FORM.contains("materialTypeId()+\""));
        assertFalse(REQUEST_FORM.contains("String.valueOf(x.id())"));
        assertTrue(REQUEST_FORM.contains("setConverter(new javafx.util.StringConverter<>(){public String toString(MaterialTypeDto x){return x==null?\"\":x.name();}"));
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
}

package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ColorCodedComboBoxContractTest {
    private static final Path COMPONENT = Path.of("src/main/java/com/shale/ui/component/ColorCodedComboBox.java");
    private static final Path INDICATOR = Path.of("src/main/java/com/shale/ui/component/factory/LinkTypeIndicatorFactory.java");
    private static final Path COLOR_UTIL = Path.of("src/main/java/com/shale/ui/util/ColorUtil.java");

    @Test
    void exposesGenericExtractorBasedApiWithoutDtoCoupling() throws Exception {
        String source = Files.readString(COMPONENT);
        assertTrue(source.contains("public class ColorCodedComboBox<T> extends ComboBox<T>"));
        assertTrue(source.contains("Function<T, String> displayTextExtractor"));
        assertTrue(source.contains("Function<T, String> colorExtractor"));
        assertTrue(source.contains("public ColorCodedComboBox(Function<T, String> displayTextExtractor, Function<T, String> colorExtractor)"));
        assertTrue(!source.contains("LinkTypeDto"), "Reusable control must not know about LinkTypeDto or any other field-specific DTO.");
    }

    @Test
    void popupRowsAndSelectedValueShareSinglePillRendering() throws Exception {
        String source = Files.readString(COMPONENT);
        String displayNode = methodBody(source, "public HBox createDisplayNode");

        assertTrue(source.contains("setCellFactory(list -> createColorCodedCell())"));
        assertTrue(source.contains("setButtonCell(createColorCodedButtonCell())"));
        assertTrue(source.contains("return createColorCodedCell(false)"));
        assertTrue(source.contains("return createColorCodedCell(true)"));
        assertTrue(source.contains("setGraphic(createDisplayNode(item))"));
        assertTrue(displayNode.contains("LinkTypeIndicatorFactory.createLinkTypePill(displayText, color(item), LinkTypeIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(displayNode.contains("HBox content = new HBox(pill)"));
        assertFalse(displayNode.contains("new Label(displayText)"));
    }

    @Test
    void selectedPromptRemainsPlainTextAndClosedCellReservesArrowSpace() throws Exception {
        String source = Files.readString(COMPONENT);
        String cell = methodBody(source, "private ListCell<T> createColorCodedCell(boolean buttonCell)");

        assertTrue(source.contains("private static final Insets BUTTON_CELL_PADDING = new Insets(3, 28, 3, 8)"));
        assertTrue(cell.contains("setText(buttonCell ? getComboBoxPromptText() : null)"));
        assertTrue(cell.contains("setContentDisplay(buttonCell ? ContentDisplay.TEXT_ONLY : ContentDisplay.GRAPHIC_ONLY)"));
        assertTrue(cell.contains("setContentDisplay(ContentDisplay.GRAPHIC_ONLY)"));
        assertTrue(cell.contains("setPadding(buttonCell ? BUTTON_CELL_PADDING : POPUP_ROW_PADDING)"));
    }

    @Test
    void safelyHandlesEmptyItemsAndDelegatesColorFallbackToExistingDesignHelpers() throws Exception {
        String source = Files.readString(COMPONENT);
        String indicator = Files.readString(INDICATOR);
        String colorUtil = Files.readString(COLOR_UTIL);
        assertTrue(source.contains("if (empty || item == null)"));
        assertTrue(source.contains("String value = displayTextExtractor.apply(item);"));
        assertTrue(source.contains("return value == null ? \"\" : value;"));
        assertTrue(source.contains("return item == null ? null : colorExtractor.apply(item);"));
        assertTrue(indicator.contains("ColorUtil.toCssBackgroundColor(storedColor)"));
        assertTrue(colorUtil.contains("normalized == null ? \"rgba(0,0,0,0.06)\""));
        assertTrue(colorUtil.contains("return null;"));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "Missing method " + signatureStart);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) return source.substring(brace, i + 1);
        }
        throw new AssertionError("Could not parse method body for " + signatureStart);
    }
}

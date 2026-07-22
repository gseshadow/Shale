package com.shale.ui.component;

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
    void dropdownRowsAndSelectedValueShareColorCodedRendering() throws Exception {
        String source = Files.readString(COMPONENT);
        assertTrue(source.contains("setCellFactory(list -> createColorCodedCell())"));
        assertTrue(source.contains("setButtonCell(createColorCodedCell())"));
        assertTrue(source.contains("setGraphic(createDisplayNode(item))"));
        assertTrue(source.contains("LinkTypeIndicatorFactory.createLinkTypePill(displayText, color(item), LinkTypeIndicatorFactory.PillSize.COMPACT)"));
    }

    @Test
    void safelyHandlesEmptyItemsAndDelegatesColorFallbackToExistingDesignHelpers() throws Exception {
        String source = Files.readString(COMPONENT);
        String indicator = Files.readString(INDICATOR);
        String colorUtil = Files.readString(COLOR_UTIL);
        assertTrue(source.contains("if (empty || item == null)"));
        assertTrue(source.contains("String value = displayTextExtractor.apply(item);"));
        assertTrue(source.contains("return value == null ? \"\" : value;"));
        assertTrue(indicator.contains("ColorUtil.toCssBackgroundColor(storedColor)"));
        assertTrue(colorUtil.contains("normalized == null ? \"rgba(0,0,0,0.06)\""));
        assertTrue(colorUtil.contains("return null;"));
    }
}

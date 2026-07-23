package com.shale.ui.component.factory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MaterialRequestCardFactoryTest {
    private static final Path FACTORY = Path.of("src/main/java/com/shale/ui/component/factory/MaterialRequestCardFactory.java");

    @Test
    void listCardPresentsRequiredFieldsWithSharedPillsAndMaterialTypeAccent() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("public enum Variant { LIST }"));
        assertTrue(source.contains("request.materialTypeColor()"));
        assertTrue(source.contains("ColorUtil.toCssRgba(request.materialTypeColor(), 0.08)"));
        assertTrue(source.contains("StatusIndicatorFactory.createStatusPill(nvl(name, \"Material\"), color, StatusIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(source.contains("StatusIndicatorFactory.createStatusPill(nvl(status, \"Unknown\"), nvl(configuredColor, NEUTRAL_STATUS_COLOR), StatusIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(source.contains("Requested From"));
        assertTrue(source.contains("Requested By"));
        assertTrue(source.contains("Assigned To"));
        assertTrue(source.contains("Requested"));
        assertTrue(source.contains("Due"));
        assertTrue(source.contains("Next Follow-up"));
        assertTrue(source.contains("Overdue since"));
        assertTrue(source.contains("Follow-up due"));
        assertFalse(source.contains("Requested / Due / Follow-up"));
    }

    @Test
    void cardIsBoundedReadableAndOmitEmptyValues() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("title.setWrapText(true)"));
        assertTrue(source.contains("v.setWrapText(true)"));
        assertTrue(source.contains("card.setMaxWidth(Double.MAX_VALUE)"));
        assertTrue(source.contains("if (value == null || value.isBlank()) return;"));
        assertTrue(source.contains("GridPane facts"));
        assertFalse(source.contains("Description"), "Summary DTO does not expose description; card should not broaden queries just for decorative text.");
    }
}

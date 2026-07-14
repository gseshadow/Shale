package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CalendarOverlaySidebarSourceTest {
    @Test
    void calendarUsesPersistentSidebarInsteadOfOldDropdown() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/calendar.fxml"));
        assertTrue(fxml.contains("fx:id=\"calendarOverlaySidebar\""));
        assertTrue(fxml.contains("fx:id=\"calendarRowsBox\""));
        assertTrue(fxml.contains("fx:id=\"selectAllCalendarsButton\""));
        assertTrue(fxml.contains("fx:id=\"clearAllCalendarsButton\""));
        assertTrue(fxml.contains("fx:id=\"resetCalendarsButton\""));
        assertFalse(fxml.contains("MenuButton"));
        assertFalse(fxml.contains("calendarOverlayMenuButton"));
    }

    @Test
    void sourceLayersMovedIntoSidebarSection() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/calendar.fxml"));
        int sidebar = fxml.indexOf("calendarOverlaySidebar");
        int layers = fxml.indexOf("calendar-sidebar-layers-section");
        int events = fxml.indexOf("eventsLayerCheckBox");
        assertTrue(sidebar >= 0 && layers > sidebar && events > layers);
        assertTrue(fxml.contains("text=\"Layers\""));
    }

    @Test
    void controllerKeepsSidebarResetSeparateFromClearFilters() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        int reset = controller.indexOf("onResetCalendars()");
        int clear = controller.indexOf("onClearFilters()");
        assertTrue(reset >= 0);
        assertTrue(clear >= 0);
        String resetBody = controller.substring(reset, controller.indexOf("private Integer currentUserId", reset));
        assertTrue(resetBody.contains("resetCalendarOverlayDefaults()"));
        assertFalse(resetBody.contains("searchTextField.clear()"));
        assertFalse(resetBody.contains("setLayerDefaults()"));
        String clearBody = controller.substring(clear, controller.indexOf("@FXML private void onNewEvent", clear));
        assertTrue(clearBody.contains("setLayerDefaults()"));
        assertTrue(clearBody.contains("resetCalendarOverlayDefaults()"));
    }

    @Test
    void sidebarRowsHaveNonColorSelectedIndicatorAndKeyboardCapableControl() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        assertTrue(controller.contains("new ToggleButton()"));
        assertTrue(controller.contains("setFocusTraversable(true)"));
        assertTrue(controller.contains("setAccessibleText(accessibleText)"));
        assertTrue(controller.contains("selected ? \"✓\" : \"\""));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));
        assertTrue(css.contains(".toggle-button.calendar-overlay-row:selected"));
        assertTrue(css.contains(".calendar-overlay-row-check"));
    }

    @Test
    void sidebarNormalizesStoredUserColorsWithExistingColorUtil() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        assertTrue(controller.contains("import com.shale.ui.util.ColorUtil;"));
        assertTrue(controller.contains("ColorUtil.toCssBackgroundColorOrNull(storedColor)"));
        assertTrue(controller.contains("calendarOverlayUserColorCss(color)"));
        assertFalse(controller.contains("hashCode()"));
        assertFalse(controller.contains("Objects.hash("));
    }

    @Test
    void sharedIndicatorAndSelectionStylingRemainIndependentOfUserColor() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        assertTrue(controller.contains("shared ? \"◈\" : \"●\""));
        assertTrue(controller.contains("if (!shared && userColorCss != null)"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));
        assertTrue(css.contains(".calendar-overlay-shared-marker"));
        assertTrue(css.contains(".toggle-button.calendar-overlay-row:selected"));
        assertTrue(css.contains(".calendar-overlay-row-check"));
    }

    @Test
    void sidebarUsesIndependentScrollAreaForCalendarRows() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/calendar.fxml"));
        assertTrue(fxml.contains("fx:id=\"calendarRowsScrollPane\""));
        assertTrue(fxml.contains("VBox.vgrow=\"ALWAYS\""));
        assertTrue(fxml.contains("hbarPolicy=\"NEVER\""));
    }
}

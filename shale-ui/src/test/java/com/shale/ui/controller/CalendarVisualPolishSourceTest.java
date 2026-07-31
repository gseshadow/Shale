package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CalendarVisualPolishSourceTest {
    @Test
    void calendarDayStatesUseScopedPseudoClassesForTodayAndWeekend() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(controller.contains("PseudoClass.getPseudoClass(\"today\")"));
        assertTrue(controller.contains("PseudoClass.getPseudoClass(\"weekend\")"));
        assertTrue(controller.contains("DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY"));
        assertTrue(controller.contains("applyCalendarDayState(header, day, today)"));
        assertTrue(controller.contains("applyCalendarDayState(cell, day, LocalDate.now())"));
        assertTrue(controller.contains("applyCalendarDayState(box, visibleDays.get(dayIndex), today)"));
        assertTrue(css.contains(".calendar-day-header:today"));
        assertTrue(css.contains(".calendar-month-day-cell:today"));
        assertTrue(css.contains(".calendar-timed-day-cell:weekend"));
    }

    @Test
    void timedGridKeepsHourAndHalfHourVisualClassesDistinct() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(controller.contains("PseudoClass.getPseudoClass(\"hour\")"));
        assertTrue(controller.contains("PseudoClass.getPseudoClass(\"half-hour\")"));
        assertTrue(controller.contains("box.pseudoClassStateChanged(HOUR_PSEUDO_CLASS, slot % 2 == 0)"));
        assertTrue(controller.contains("box.pseudoClassStateChanged(HALF_HOUR_PSEUDO_CLASS, slot % 2 != 0)"));
        assertTrue(css.contains(".calendar-timed-grid .calendar-timed-day-cell:hour"));
        assertTrue(css.contains(".calendar-timed-grid .calendar-timed-day-cell:half-hour"));
    }

    @Test
    void allDayCollapseBehaviorRemainsWhileSectionGetsScopedEmptyStyling() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(controller.contains("allDayCollapsed = !allDayCollapsed"));
        assertTrue(controller.contains("createAllDaySection(grouped.getOrDefault(day, List.of()), allDayCollapsed)"));
        assertTrue(controller.contains("calendar-all-day-section"));
        assertTrue(controller.contains("calendar-all-day-empty"));
        assertTrue(css.contains(".calendar-all-day-section"));
        assertTrue(css.contains(".calendar-all-day-empty"));
    }

    @Test
    void toolbarControlsRemainPresentAndGroupedWithoutRemovingFilters() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/calendar.fxml"));

        assertTrue(fxml.contains("calendar-toolbar-nav"));
        assertTrue(fxml.contains("calendar-toolbar-actions"));
        assertTrue(fxml.contains("calendar-toolbar-filters"));
        assertTrue(fxml.contains("fx:id=\"todayButton\""));
        assertTrue(fxml.contains("fx:id=\"prevWeekButton\""));
        assertTrue(fxml.contains("fx:id=\"nextWeekButton\""));
        assertTrue(fxml.contains("fx:id=\"newEventButton\""));
        assertTrue(fxml.contains("fx:id=\"viewModeSelector\""));
        assertTrue(fxml.contains("fx:id=\"weekViewButton\""));
        assertTrue(fxml.contains("fx:id=\"fiveDayViewButton\""));
        assertTrue(fxml.contains("fx:id=\"dayViewButton\""));
        assertTrue(fxml.contains("fx:id=\"monthViewButton\""));
        assertTrue(fxml.contains("fx:id=\"searchTextField\""));
        assertTrue(fxml.contains("fx:id=\"caseFilterCombo\""));
        assertTrue(fxml.contains("fx:id=\"eventTypeFilterCombo\""));
        assertTrue(fxml.contains("fx:id=\"clearFiltersButton\""));
    }

    @Test
    void emptyStatePrecedenceAndVisualOnlyFilteringRemainUnchanged() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        int noCalendars = controller.indexOf("No calendars selected.");
        int noLayers = controller.indexOf("No calendar layers selected.");
        int switchView = controller.indexOf("switch (selectedViewMode())");

        assertTrue(noCalendars >= 0 && noLayers > noCalendars && switchView > noLayers);
        assertTrue(controller.contains("applyFiltersAndRender()"));
        assertFalse(controller.contains("loadCurrentRange(true); applyCalendarDayState"));
    }
}

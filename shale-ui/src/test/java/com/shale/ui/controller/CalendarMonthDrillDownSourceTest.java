package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CalendarMonthDrillDownSourceTest {
    @Test
    void monthViewDrillDownUsesSharedOpenDayViewPathAndPreservesItemRouting() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));

        assertTrue(source.contains("void openDayView(LocalDate date)"),
                "CalendarController should expose one shared drill-down path.");
        assertTrue(source.contains("selectedDate = date;"),
                "The clicked LocalDate must become the selected date.");
        assertTrue(source.contains("viewModeChoice.setValue(VIEW_DAY)"),
                "Drill-down should switch through the existing view mode control.");
        assertTrue(source.contains("if (alreadyDay || viewModeChoice == null)"),
                "The shared path should explicitly reload if Day view was already selected.");
        assertTrue(source.contains("configureMonthDayCellDrillDown(cell, day)"),
                "Month day-cell background clicks should use the shared drill-down path.");
        assertTrue(source.contains("createMonthDayButton(day)"),
                "Month day number clicks should use the shared drill-down path.");
        assertTrue(source.contains("createMonthMoreButton(day, dayItems.size() - 3)"),
                "+x more clicks should use the shared drill-down path.");
        assertTrue(source.contains("openDayView(day)"),
                "Clickable month regions should invoke the shared drill-down method.");
        assertTrue(source.contains("evt.consume();"),
                "Item clicks and drill-down control clicks should consume events to avoid day-cell interception.");
        assertTrue(source.contains("case CALENDAR_EVENT -> openEditEventDialog"));
        assertTrue(source.contains("case TASK -> onOpenTask.accept"));
        assertTrue(source.contains("case CASE -> onOpenCase.accept"));
    }

    @Test
    void monthDrillDownControlsRemainAccessibleLinkStyledButtons() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(source.contains("new Button(String.valueOf(day.getDayOfMonth()))"),
                "The day number should retain Button semantics for keyboard activation.");
        assertTrue(source.contains("new Button(\"+\" + hiddenCount + \" more\")"),
                "The overflow control should retain Button semantics for keyboard activation.");
        assertTrue(source.contains("calendar-month-day-link"),
                "The day number should use the lightweight month day link style.");
        assertTrue(source.contains("calendar-month-more-link"),
                "The overflow control should use the lightweight month more link style.");
        assertFalse(source.contains("calendar-month-day-button"),
                "The month day number should not use the old heavy button style class.");
        assertFalse(source.contains("calendar-month-more-button"),
                "The overflow control should not use the old heavy button style class.");
        assertTrue(source.contains("setFocusTraversable(true)"),
                "Both controls should remain reachable by keyboard focus traversal.");
        assertTrue(source.contains("setAccessibleText(accessible)"),
                "Both controls should retain accessible text.");
        assertTrue(source.contains("Tooltip.install"),
                "Both controls should retain explanatory tooltips.");
        assertTrue(source.contains("setOnAction(evt ->"),
                "Button action handlers preserve Enter/Space activation.");
        assertTrue(source.contains("setOnMouseClicked(evt -> evt.consume())"),
                "Mouse clicks on link-styled buttons should not bubble into the day-cell drill-down handler.");

        assertTrue(css.contains(".button.calendar-month-day-link"),
                "Month day link styles should be scoped to the dedicated Button class.");
        assertTrue(css.contains(".button.calendar-month-more-link"),
                "Month more link styles should be scoped to the dedicated Button class.");
        assertTrue(css.contains("-fx-background-color: transparent"),
                "The Month-view link styles should remove standard button chrome.");
        assertTrue(css.contains("-fx-border-color: transparent"),
                "The Month-view link styles should remove standard button borders by default.");
        assertTrue(css.contains(".button.calendar-month-more-link:hover > .text"),
                "The overflow link should expose a clear hover affordance.");
        assertTrue(css.contains(".button.calendar-month-day-link:focused"),
                "The day link should have a visible focus state.");
        assertTrue(css.contains(".button.calendar-month-more-link:focused"),
                "The overflow link should have a visible focus state.");
    }
}

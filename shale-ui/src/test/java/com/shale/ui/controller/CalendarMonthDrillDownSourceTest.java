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
}

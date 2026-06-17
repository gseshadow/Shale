package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ReportsControllerLifecycleTest {

    @Test
    void reportsInitialLoadUsesBlankDateFiltersAndLoadsStatusesBeforeReport() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ReportsController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/reports.fxml"));

        assertTrue(source.contains("loadStatusesAndReport();"),
                "Reports should load tenant statuses and report data when initialized by SceneManager.");
        assertTrue(source.contains("caseDao.listCaseStatuses(shaleClientId, true)"),
                "Reports status filter should use the same merged tenant/global case status source as Settings.");
        assertFalse(source.contains("minusMonths(12)"),
                "Initial Reports load should not default to the last 12 months.");
        assertTrue(fxml.contains("fx:id=\"statusFilterMenuButton\""));
        assertTrue(fxml.contains("text=\"Show all results\""));
        assertTrue(source.contains("startDatePicker.setValue(null)"));
        assertTrue(source.contains("endDatePicker.setValue(null)"));
    }

    @Test
    void reportsPieChartUsesDatabaseColorsAndPercentLabels() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ReportsController.java"));

        assertTrue(source.contains("PERCENT_FORMAT.format(percentage) + \"%\""));
        assertTrue(source.contains("ColorUtil.toCssBackgroundColorOrNull(row.color())"));
        assertTrue(source.contains("Platform.runLater(() -> {") && source.contains("applyPieSliceColors(colorsBySliceName)"),
                "Pie slice colors should be re-applied after JavaFX creates chart nodes.");
        assertTrue(source.contains("if (total <= 0)"),
                "Reports should avoid NaN/Infinity labels when there are no matching cases.");
    }
}

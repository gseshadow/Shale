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
    void reportsPieChartUsesResolvedDatabaseColorsForSlicesAndLegend() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ReportsController.java"));

        assertTrue(source.contains("PERCENT_FORMAT.format(percentage) + \"%\""));
        assertTrue(source.contains("String color = resolvedStatusColor(row.color())"),
                "Each status slice should resolve its configured status color from the report row, not an index palette.");
        assertTrue(source.contains("ColorUtil.toCssBackgroundColor(storedColor)"),
                "Null, blank, or invalid report colors should use the shared neutral fallback instead of JavaFX palette colors.");
        assertTrue(source.contains("slice.getNode().setStyle(\"-fx-pie-color: \" + color + \";\")"),
                "The pie slice should receive the resolved status color.");
        assertTrue(source.contains("statusPieChart.lookupAll(\".chart-legend-item\")"),
                "The JavaFX generated chart legend should be recolored explicitly.");
        assertTrue(source.contains("symbol.setStyle(\"-fx-background-color: \" + color + \";\")"),
                "The legend/key indicator should receive the same resolved status color as the slice.");
        assertTrue(source.contains("Platform.runLater(() -> {") && source.contains("applyPieSliceColors(colorsBySliceName)"),
                "Pie slice and legend colors should be re-applied after JavaFX creates chart nodes.");
        assertTrue(source.contains("if (total <= 0)"),
                "Reports should avoid NaN/Infinity labels when there are no matching cases.");
    }

    @Test
    void reportsColorMappingIsAttachedToStatusLabelNotChartOrder() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ReportsController.java"));

        assertTrue(source.contains("Map<String, String> colorsBySliceName = new LinkedHashMap<>()"));
        assertTrue(source.contains("colorsBySliceName.put(sliceName, color)"));
        assertTrue(source.contains("colorsBySliceName.get(slice.getName())"),
                "Colors should be looked up from the status slice name so reordered statuses keep their own colors.");
        assertFalse(source.contains("data" + 0));
        assertFalse(source.contains("CHART_COLOR"));
    }
}

package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShaleControllerClearAllFiltersTest {

    @Test
    void myTasksClearAllResetsSearchAndNonSearchFiltersThroughSingleSuppressedRefreshPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/my-shale.fxml"));

        assertTrue(fxml.contains("fx:id=\"myTasksClearAllFiltersButton\"")
                        && fxml.contains("onAction=\"#clearAllMyTasksFilters\"")
                        && fxml.contains("text=\"Clear all filters\""),
                "My Tasks should expose a clearly labeled Clear all filters button near its filter/search controls.");
        assertTrue(source.contains("private void clearAllMyTasksFilters()"),
                "My Tasks should use a single clear-all action path.");
        assertTrue(source.contains("myTasksSearchField.clear()"),
                "My Tasks clear-all should clear search text.");
        assertTrue(source.contains("myTasksSourceChoice.getSelectionModel().select(MyTasksSource.ASSIGNED_TO_ME)")
                        && source.contains("myTasksPriorityFilterChoice.getSelectionModel().select(ALL_PRIORITIES_OPTION)")
                        && source.contains("myTasksCaseFilterChoice.getSelectionModel().select(ALL_CASES_OPTION)"),
                "My Tasks clear-all should reset source, priority, and case filters to defaults.");
        assertTrue(source.contains("showCompletedMyTasks = false")
                        && source.contains("updateMyTasksCompletionToggleLabel()"),
                "My Tasks clear-all should reset the completed toggle filter and label.");
        assertTrue(source.contains("suppressMyTasksFilterEvents = true")
                        && containsStatementsInOrder(source, "if (sourceChanged || completedChanged) {", "refreshMyTasks(true);")
                        && containsStatementsInOrder(source, "} else {", "renderMyTasks();"),
                "My Tasks clear-all should suppress duplicate listener renders and refresh/re-render once.");
    }

    @Test
    void myCasesClearAllResetsSearchAndStatusFiltersThroughSingleSuppressedRefreshPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/my-shale.fxml"));

        assertTrue(fxml.contains("fx:id=\"myCasesClearAllFiltersButton\"")
                        && fxml.contains("onAction=\"#clearAllMyCasesFilters\"")
                        && fxml.contains("text=\"Clear all filters\""),
                "My Cases should expose a clearly labeled Clear all filters button near its filter/search controls.");
        assertTrue(source.contains("private void clearAllMyCasesFilters()"),
                "My Cases should use a single clear-all action path.");
        assertTrue(source.contains("myCasesBoardSearchField.clear()"),
                "My Cases clear-all should clear the visible board search text.");
        assertTrue(source.contains("myCasesBoardStatusFilterChoice.getSelectionModel().select(ALL_BOARD_STATUSES_OPTION)"),
                "My Cases clear-all should reset the authoritative board status filter.");
        assertTrue(source.contains("suppressMyCasesFilterEvents = true")
                        && containsStatementsInOrder(source, "renderMyCasesBoard();", "ensureMyCasesFresh(false);"),
                "My Cases clear-all should suppress duplicate listener renders and refresh the default board state once.");
    }

    private static boolean containsStatementsInOrder(String source, String... statements) {
        int searchFrom = 0;
        for (String statement : statements) {
            int index = source.indexOf(statement, searchFrom);
            if (index < 0) {
                return false;
            }
            searchFrom = index + statement.length();
        }
        return true;
    }
}

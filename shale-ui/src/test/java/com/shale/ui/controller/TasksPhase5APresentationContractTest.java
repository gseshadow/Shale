package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TasksPhase5APresentationContractTest {

    @Test
    void dedicatedRouteReusesMyShaleTaskBoardRatherThanForkingItsMarkup() throws Exception {
        String mainFxml = read("src/main/resources/fxml/main.fxml");
        String sceneManager = read("src/main/java/com/shale/ui/navigation/SceneManager.java");
        String controller = read("src/main/java/com/shale/ui/controller/MyShaleController.java");

        assertTrue(mainFxml.contains("fx:id=\"navTasksButton\"") && mainFxml.contains("onAction=\"#onNavTasks\""));
        assertTrue(sceneManager.contains("return createMyShaleView(onOpenCase, onOpenUser, true);"),
                "Tasks must compose the already-authoritative My Shale board and handlers.");
        assertTrue(controller.contains("configureDedicatedTasksMode()"));
        assertTrue(controller.contains("onSectionSelected(SECTION_TASKS)"));
        assertFalse(Files.exists(Path.of("src/main/resources/fxml/tasks.fxml")),
                "A duplicate task-board FXML resource must not be introduced.");
    }

    @Test
    void taskBoardKeepsActualModesFiltersStatesCountsAndFactory() throws Exception {
        String fxml = read("src/main/resources/fxml/my-shale.fxml");
        String controller = read("src/main/java/com/shale/ui/controller/MyShaleController.java");

        for (String id : java.util.List.of(
                "myTasksBoardViewButton", "myTasksGridViewButton", "myTasksSourceChoice",
                "myTasksSortChoice", "myTasksShowCompletedButton", "myTasksStatusFilterChoice",
                "myTasksPriorityFilterChoice", "myTasksCaseFilterChoice", "myTasksColumnOrderChoice",
                "myTasksSearchField", "myTasksClearAllFiltersButton", "myTasksLoadingLabel",
                "myTasksErrorLabel", "myTasksEmptyLabel", "myTasksResultCount")) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(controller.contains("taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true)"));
        assertTrue(controller.contains("updateResultCount(myTasksResultCount, filteredTasks.size())"));
        assertTrue(controller.contains("No tasks match the selected filters."));
    }

    @Test
    void cardsExposeLabelledTimeStatesKeyboardActivationAndSharedSemanticPaint() throws Exception {
        String card = read("src/main/java/com/shale/ui/component/TaskCard.java");
        String css = read("src/main/resources/css/foundation/cards.css");

        assertTrue(card.contains("Overdue · Due "));
        assertTrue(card.contains("Due soon · Due "));
        assertTrue(card.contains("KeyCode.ENTER") && card.contains("KeyCode.SPACE"));
        assertTrue(card.contains("setAccessibleRole(AccessibleRole.BUTTON)"));
        assertTrue(css.contains(".task-card:overdue .task-card__due"));
        assertTrue(css.contains(".task-card:completed"));
        assertTrue(css.contains(".task-card:focused"));
        String taskRules = css.substring(css.indexOf("/* Task Card:"), css.indexOf("/* Entity Card embedded variant:"));
        assertFalse(taskRules.contains("#"), "The migrated Task Card CSS must rely on semantic theme paint.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

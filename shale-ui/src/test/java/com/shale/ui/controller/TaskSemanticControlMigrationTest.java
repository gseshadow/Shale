package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskSemanticControlMigrationTest {
    private final String taskCard = read("src/main/java/com/shale/ui/component/TaskCard.java");
    private final String caseController = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private final String myShale = read("src/main/java/com/shale/ui/controller/MyShaleController.java");

    TaskSemanticControlMigrationTest() throws Exception {
    }

    @Test
    void taskActionsHaveExplicitSemanticPurposesAndCardDensity() {
        assertTrue(caseController.contains("ControlStyles.apply(addTaskButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD)"));
        assertTrue(caseController.contains("ControlStyles.apply(caseTasksShowCompletedButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD)"));
        assertTrue(taskCard.contains("ControlStyles.apply(toggleCompleteButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL)"));
        assertTrue(taskCard.contains("ControlStyles.apply(expandDetailsButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertFalse(taskCard.contains("app-toolbar-button-success"), "Complete and reopen are workflow actions, not a feature-colored success variant.");
        assertFalse(taskCard.contains("Purpose.DANGER"), "Complete and reopen must never be destructive actions.");
    }

    @Test
    void taskToolbarsUseSharedFormShellAndNonPrimaryFilters() {
        assertTrue(caseController.contains("ControlStyles.formControl(caseTasksSortChoice)"));
        for (String control : java.util.List.of(
                "myTasksSortChoice", "myTasksSourceChoice", "myTasksCaseFilterChoice",
                "myTasksPriorityFilterChoice", "myTasksStatusFilterChoice",
                "myTasksColumnOrderChoice", "myTasksSearchField")) {
            assertTrue(myShale.contains("ControlStyles.formControl(" + control + ")"), control);
        }
        assertTrue(myShale.contains("ControlStyles.apply(myTasksClearAllFiltersButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(myShale.contains("ControlStyles.apply(myTasksShowCompletedButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL)"));
        assertFalse(myShale.contains("ControlStyles.apply(myTasksShowCompletedButton, ControlStyles.Purpose.PRIMARY"));
    }

    @Test
    void segmentedAndIconOnlyControlsRetainFeatureClassesAndSemanticBase() {
        assertTrue(myShale.contains("ControlStyles.apply(myTasksBoardViewButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(myShale.contains("ControlStyles.apply(myTasksGridViewButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(myShale.contains("ControlStyles.iconOnly(collapseButton)"));
        assertTrue(myShale.contains("ControlStyles.iconOnly(pinButton)"));
        assertTrue(myShale.contains("my-tasks-view-toggle-selected"), "The established segmented selected state must remain intact.");
        assertTrue(myShale.contains("lane-pin-button-pinned"), "Pinned-lane data presentation must remain intact.");
    }

    @Test
    void dataColorsAndEmbeddedEventIsolationRemainSeparateFromActions() {
        assertTrue(taskCard.contains("setPriorityBackgroundColor"));
        assertTrue(taskCard.contains("setTaskStatus"));
        assertTrue(taskCard.contains("DueProximityStyles.accentColor"));
        assertTrue(taskCard.contains("CaseCardFactory.Variant.EMBEDDED"));
        assertTrue(taskCard.matches("(?s).*caseCard\\.setOnMouseClicked\\(e -> \\{\\s+e\\.consume\\(\\);.*"));
        assertTrue(taskCard.matches("(?s).*toggleCompleteButton\\.setOnAction\\(e ->\\s+\\{\\s+e\\.consume\\(\\);.*"));
        assertFalse(taskCard.contains("toggleCompleteButton.setStyle("), "Action buttons must not receive inline feature colors.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

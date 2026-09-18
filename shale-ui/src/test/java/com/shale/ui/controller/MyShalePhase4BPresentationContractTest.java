package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShalePhase4BPresentationContractTest {

    private static final Path FXML = Path.of("src/main/resources/fxml/my-shale.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java");

    @Test
    void dashboardComposesSharedHeaderTabsToolbarsSectionsAndWrappingControls() throws Exception {
        String fxml = Files.readString(FXML);

        assertTrue(fxml.contains("styleClass=\"shale-page-title\""));
        assertTrue(fxml.contains("styleClass=\"shale-metadata-muted\""));
        assertTrue(fxml.contains("styleClass=\"app-section-tabs-row\""));
        assertTrue(fxml.contains("styleClass=\"shale-section-card, my-shale-section\""));
        assertTrue(fxml.contains("styleClass=\"shale-toolbar, my-shale-toolbar\""));
        assertTrue(fxml.contains("<FlowPane hgap=\"8\" vgap=\"8\""),
                "Filter controls must wrap rather than impose a fixed page width.");
        assertFalse(fxml.contains("styleClass=\"strong-panel\"") || fxml.contains("styleClass=\"glass-panel\""),
                "Migrated dashboard shells must not retain superseded page-local surfaces.");
    }

    @Test
    void tasksAndCasesKeepFactoriesAndExposeDistinctAuthoritativeStates() throws Exception {
        String source = Files.readString(CONTROLLER);
        String fxml = Files.readString(FXML);

        assertTrue(source.contains("taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true)"));
        assertTrue(source.contains("taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT, true)"));
        assertTrue(source.contains("return caseCardFactory.create(new CaseCardModel("));
		assertFalse(source.contains("section.getStyleClass().add(prominent ? \"strong-panel\" : \"glass-panel\")"),
				"Overview task sections must use the shared section-card contract.");
        for (String id : java.util.List.of("myTasksLoadingLabel", "myTasksEmptyLabel", "myTasksErrorLabel",
                "myCasesLoadingLabel", "myCasesBoardEmptyLabel", "myCasesErrorLabel")) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), id + " must remain a separate state node.");
        }
        assertTrue(source.contains("No tasks match the selected filters."));
        assertTrue(source.contains("No cases match the selected filters."));
        assertTrue(source.contains("updateResultCount(myTasksResultCount, filteredTasks.size())"));
        assertTrue(source.contains("updateResultCount(myCasesResultCount, cardCount)"));
    }
}

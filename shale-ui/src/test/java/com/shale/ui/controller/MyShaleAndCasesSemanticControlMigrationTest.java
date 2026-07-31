package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MyShaleAndCasesSemanticControlMigrationTest {
    private final String cases = read("src/main/java/com/shale/ui/controller/CasesController.java");
    private final String myShale = read("src/main/java/com/shale/ui/controller/MyShaleController.java");
    private final String casesFxml = read("src/main/resources/fxml/cases.fxml");
    private final String widgetFactory = read("src/main/java/com/shale/ui/component/factory/DashboardWidgetFactory.java");

    MyShaleAndCasesSemanticControlMigrationTest() throws Exception {
    }

    @Test
    void casesToolbarUsesSharedControlsAndKeepsSegmentSelection() {
        for (String control : java.util.List.of("casesSearchField", "casesSortChoice", "statusFilterMenuButton")) {
            assertTrue(cases.contains("ControlStyles.formControl(" + control + ")"), control);
        }
        assertTrue(cases.contains("ControlStyles.apply(cardsViewToggle, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(cases.contains("ControlStyles.apply(gridViewToggle, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(cases.contains("ControlStyles.apply(columnMenuButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL)"));
        assertTrue(cases.contains("ControlStyles.apply(exportMenuButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL)"));
        assertTrue(casesFxml.contains("styleClass=\"shale-segmented-control\""));
        assertTrue(casesFxml.contains("selected=\"true\""));
        assertFalse(cases.contains("ControlStyles.Purpose.PRIMARY"), "Filters and view/export controls are not primary actions.");
        assertFalse(cases.contains("ControlStyles.Purpose.DANGER"), "My Cases has no destructive button.");
    }

    @Test
    void myShaleNonTaskControlsAreExplicitlyMigrated() {
        assertTrue(myShale.contains("ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL"));
        for (String control : java.util.List.of(
                "myCasesBoardSearchField", "myCasesBoardStatusFilterChoice", "myCasesBoardSortChoice",
                "overviewSearchFieldControl", "overviewPriorityChoiceControl", "overviewCaseChoiceControl",
                "overviewSortChoiceControl")) {
            assertTrue(myShale.contains("ControlStyles.formControl(" + control + ")"), control);
        }
        assertTrue(myShale.contains("ControlStyles.apply(myCasesClearAllFiltersButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL)"));
        assertTrue(widgetFactory.contains("ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL"));
        assertFalse(myShale.contains("ControlStyles.apply(myCasesClearAllFiltersButton, ControlStyles.Purpose.PRIMARY"));
    }

    @Test
    void taskLaneWidthContractAndDataColorBoundariesRemainUntouched() throws Exception {
        assertTrue(myShale.contains("TASKS_SINGLE_LANE_MAX_WIDTH = 430"));
        assertTrue(myShale.contains("orderedLanes.size() == 1 && !isCollapsedLane"));
        assertFalse(cases.contains("Button.setStyle("), "Cases actions must not receive inline styling.");
        assertTrue(read("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java")
                .contains("setPracticeAreaCssColor"));
    }

    @Test
    void migratedFxmlResourcesRemainWellFormedAndAvailable() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        for (String resource : java.util.List.of("cases.fxml", "my-shale.fxml")) {
            try (var input = getClass().getResourceAsStream("/fxml/" + resource)) {
                assertTrue(input != null, resource);
                assertTrue(factory.newDocumentBuilder().parse(input).getDocumentElement() != null, resource);
            }
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

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
    private final String myShaleFxml = read("src/main/resources/fxml/my-shale.fxml");
    private final String sectionTabs = read("src/main/java/com/shale/ui/util/AppSectionTabs.java");
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

		String statusFilter = fxmlElement("MenuButton", "statusFilterMenuButton");
		String intakeDateSort = fxmlElement("ChoiceBox", "casesSortChoice");
		assertFalse(statusFilter.contains("app-toolbar-button"),
				"The status filter must not retain the legacy capsule action shell.");
		assertFalse(intakeDateSort.contains("app-toolbar-select"),
				"The Intake Date sort selector must not retain the legacy capsule selector shell.");
		for (String action : java.util.List.of("columnMenuButton", "exportMenuButton")) {
			assertFalse(fxmlElement("MenuButton", action).contains("app-toolbar-button"),
					action + " must rely on its semantic action classification without a legacy override.");
		}
    }

	@Test
	void casesToolbarKeepsResponsiveAndDataPresentationContracts() throws Exception {
		assertTrue(casesFxml.contains("<FlowPane hgap=\"10\" vgap=\"8\""));
		assertTrue(casesFxml.contains("prefWrapLength=\"1040\""));
		assertTrue(casesFxml.contains("promptText=\"Search cases…\" prefWidth=\"280\""));
		assertTrue(casesFxml.contains("styleClass=\"shale-segmented-control\""));
		String indicators = read("src/main/resources/css/foundation/indicators.css");
		assertTrue(indicators.contains(".shale-indicator-status-pill"));
		assertTrue(indicators.contains(".shale-status-pill"));
		assertFalse(fxmlElement("MenuButton", "statusFilterMenuButton").contains("status-pill"),
				"Status color is data presentation inside content, not action styling on the filter shell.");
	}

    @Test
    void myShaleNonTaskControlsAreExplicitlyMigrated() {
        assertTrue(myShale.contains("AppSectionTabs.buildTabs("),
                "My Shale section navigation must use the shared specialized tab builder.");
        assertTrue(myShale.contains("AppSectionTabs.setActive("),
                "My Shale selection must use the shared specialized active-state contract.");
        assertTrue(sectionTabs.contains("TAB_BUTTON_STYLE_CLASS = \"app-section-tab\""));
        assertTrue(sectionTabs.contains("TAB_BUTTON_ACTIVE_STYLE_CLASS = \"app-section-tab-active\""));
        assertTrue(myShaleFxml.contains("styleClass=\"app-section-tabs-row\""),
                "My Shale must use the shared section-tab row rather than a page-specific navigation shell.");
        assertFalse(myShale.contains("ControlStyles.apply(button, ControlStyles.Purpose.NAVIGATION"),
                "Specialized section tabs must not also receive the competing ordinary navigation-button hierarchy.");
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

	private String fxmlElement(String element, String id) {
		int start = casesFxml.indexOf("<" + element + " fx:id=\"" + id + "\"");
		assertTrue(start >= 0, id);
		int end = casesFxml.indexOf("</" + element + ">", start);
		assertTrue(end >= 0, id);
		return casesFxml.substring(start, end);
	}
}

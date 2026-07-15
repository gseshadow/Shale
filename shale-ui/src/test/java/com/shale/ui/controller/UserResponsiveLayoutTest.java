package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class UserResponsiveLayoutTest {

    private static final Path USER_FXML = Path.of("src/main/resources/fxml/user.fxml");
    private static final Path USER_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/UserController.java");

    @Test
    void outerUserViewScrollPaneAllowsWrappedContentToGrowVertically() throws Exception {
        String fxml = Files.readString(USER_FXML);

        assertTrue(fxml.contains("fx:id=\"pageScroll\" fitToWidth=\"true\" fitToHeight=\"false\""),
                "The outer User View ScrollPane should fit width but not force content height to the viewport.");
        assertTrue(fxml.contains("fx:id=\"pageContent\""),
                "The scroll content VBox should be addressable by the responsive sizing code.");
        int flowStart = fxml.indexOf("<FlowPane fx:id=\"sectionsFlow\"");
        int flowTagEnd = fxml.indexOf(">", flowStart);
        String flowTag = fxml.substring(flowStart, flowTagEnd);
        assertFalse(flowTag.contains("VBox.vgrow=\"ALWAYS\""),
                "The wrapping sections FlowPane should not be forced to consume only the visible viewport height.");
    }

    @Test
    void userViewUsesFlowPaneAndKeepsSectionOrder() throws Exception {
        String fxml = Files.readString(USER_FXML);
        int flow = fxml.indexOf("<FlowPane fx:id=\"sectionsFlow\"");
        int details = fxml.indexOf("fx:id=\"userDetailsSection\"", flow);
        int tasks = fxml.indexOf("fx:id=\"tasksSection\"", flow);
        int cases = fxml.indexOf("fx:id=\"casesSection\"", flow);

        assertTrue(flow >= 0, "User View's main section container should remain the sections FlowPane.");
        assertTrue(details > flow && tasks > details && cases > tasks,
                "Responsive wrapping should preserve User Details, Assigned Tasks, Assigned Cases order.");
        assertFalse(fxml.contains("visible=\"false\" managed=\"false\" fx:id=\"userDetailsSection\"")
                        || fxml.contains("fx:id=\"userDetailsSection\" visible=\"false\"")
                        || fxml.contains("fx:id=\"tasksSection\" visible=\"false\"")
                        || fxml.contains("fx:id=\"casesSection\" visible=\"false\""),
                "Responsive layout must not hide any of the three sections.");
    }

    @Test
    void controllerDefinesDeliberateWideMediumAndNarrowBreakpoints() throws Exception {
        String source = Files.readString(USER_CONTROLLER);

        assertTrue(source.contains("WIDE_BREAKPOINT") && source.contains("MEDIUM_BREAKPOINT"),
                "User View should use explicit responsive breakpoints instead of accidental FlowPane clipping.");
        assertTrue(source.contains("applyWideLayout(viewportHeight)")
                        && source.contains("applyMediumLayout(contentWidth)")
                        && source.contains("applyNarrowLayout(contentWidth)"),
                "Wide, medium, and narrow arrangements should be selected deliberately.");
        assertTrue(source.contains("applySectionSize(userDetailsSection, WIDE_DETAILS_WIDTH")
                        && source.contains("applySectionSize(tasksSection, WIDE_TASKS_WIDTH")
                        && source.contains("applySectionSize(casesSection, WIDE_CASES_WIDTH"),
                "Wide layout should keep the three-column desktop arrangement.");
        assertTrue(source.contains("applySectionSize(userDetailsSection, halfWidth")
                        && source.contains("applySectionSize(tasksSection, halfWidth")
                        && source.contains("applySectionSize(casesSection, contentWidth"),
                "Medium layout should place details and tasks first, then wrap cases below.");
        assertTrue(source.contains("applySectionSize(userDetailsSection, sectionWidth")
                        && source.contains("applySectionSize(tasksSection, sectionWidth")
                        && source.contains("applySectionSize(casesSection, sectionWidth"),
                "Narrow layout should stack all sections at the same width in FXML order.");
    }

    @Test
    void outerAndInnerScrollPoliciesRemainConfiguredForNestedScrolling() throws Exception {
        String fxml = Files.readString(USER_FXML);
        String source = Files.readString(USER_CONTROLLER);

        assertTrue(source.contains("pageScroll.setFitToHeight(false)")
                        && source.contains("pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)")
                        && source.contains("pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"),
                "The outer scroll pane should be vertically scrollable without creating horizontal overflow.");
        assertTrue(fxml.contains("fx:id=\"assignedTasksScroll\" fitToWidth=\"true\" fitToHeight=\"true\" hbarPolicy=\"NEVER\" vbarPolicy=\"AS_NEEDED\"")
                        && fxml.contains("fx:id=\"assignedCasesScroll\" fitToWidth=\"true\" fitToHeight=\"true\" hbarPolicy=\"NEVER\" vbarPolicy=\"AS_NEEDED\""),
                "Assigned task and case lists should preserve independent vertical scrolling.");
        assertTrue(source.contains("MEDIUM_LIST_HEIGHT") && source.contains("NARROW_LIST_HEIGHT"),
                "Stacked list sections should keep bounded preferred heights so the outer page scrolls to other panels.");
    }
}

package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseRequestsLifecycleRegressionTest {
    private static final Path CASE = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path REQUESTS = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

    @Test
    void calendarExclusivelyDeactivatesRequestsAndEveryOtherCaseRoot() throws Exception {
        String source = Files.readString(CASE);
        String activation = methodBody(source, "private void activateCaseSectionRoot");
        assertTrue(activation.contains("overviewScrollPane, detailsSectionPane, tasksTabPane, caseCalendarTabPane"));
        assertTrue(activation.contains("caseRequestsTabPane, caseLinksTabPane, genericPane"));
        assertTrue(activation.contains("setPaneVisible(root, root == activeRoot)"));
        assertTrue(activation.contains("caseMaterialRequestsTabController.deactivate()"));
        assertTrue(methodBody(source, "private void showCalendarTab").contains("activateCaseSectionRoot(caseCalendarTabPane)"));
        assertTrue(methodBody(source, "private void showRequestsSurface").contains("activateCaseSectionRoot(caseRequestsTabPane)"));
        String visibility = methodBody(source, "private static void setPaneVisible");
        assertTrue(visibility.contains("pane.setVisible(visible)"));
        assertTrue(visibility.contains("pane.setManaged(visible)"));
        assertTrue(visibility.contains("pane.setMouseTransparent(!visible)"));
    }

    @Test
    void requestsRootIsStableAndLateLoadsAreInvalidatedInsteadOfRemounting() throws Exception {
        String caseSource = Files.readString(CASE);
        String requestsSource = Files.readString(REQUESTS);
        assertEquals(1, count(caseSource, "caseMaterialRequestsTabController.view()"),
                "The shared host must have one mount point and must not accumulate request roots.");
        assertTrue(caseSource.contains("getChildren().isEmpty()"));
        assertTrue(requestsSource.contains("void deactivate(){gen.incrementAndGet();loaded=false;}"));
        assertTrue(requestsSource.contains("if(stale(g,cid)"), "Late request callbacks must retain the generation guard.");
    }

    @Test
    void allCaseSectionsStillHaveNavigationRoutes() throws Exception {
        String source = Files.readString(CASE);
        for (String route : new String[]{"showOverview()", "showParties()", "showTasksTab()", "showCalendarTab()",
                "showRequestsTab()", "showLinksTab()", "showTimeline()", "showDetails()"}) {
            assertTrue(source.contains("-> " + route), route);
        }
    }

    private static int count(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("Unclosed method " + signature);
        return "";
    }
}

package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

final class CaseCalendarTabSourceTest {
    private static final Path CONTROLLER_PATH = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path FXML_PATH = Path.of("src/main/resources/fxml/case.fxml");
    private static final String FX_ID_ATTRIBUTE = "fx:id";

    @Test
    void caseCalendarTabIsPlacedBetweenTasksAndTimelineAndUsesSharedFlows() throws Exception {
        String controller = Files.readString(CONTROLLER_PATH);
        Document caseFxml = parseFxml(Files.readString(FXML_PATH));

        List<String> sections = caseNavigationSections(controller);
        assertTrue(sections.containsAll(List.of("Tasks", "Calendar", "Timeline")),
                "Case navigation must expose Tasks, Calendar, and Timeline sections.");
        assertInOrder(sections, "Tasks", "Calendar", "Timeline");

        Map<String, Element> elementsById = elementsByFxId(caseFxml);
        assertNotNull(elementsById.get("caseCalendarTabPane"), "Case FXML must declare the Calendar tab pane.");
        assertNotNull(elementsById.get("caseCalendarUpdatesHost"), "Case Calendar tab must keep the shared updates host.");
        assertNotNull(elementsById.get("caseCalendarAgendaBox"), "Case Calendar tab must keep the agenda content host.");
        assertNotNull(elementsById.get("caseCalendarNewEventButton"), "Case Calendar tab must keep the shared new-event entry point.");
        assertNotNull(elementsById.get("caseCalendarNewTaskButton"), "Case Calendar tab must keep the shared new-task entry point.");
        assertDocumentOrder(caseFxml, "tasksTabPane", "caseCalendarTabPane", "genericPane");

        String navigationMethod = methodBody(controller, "private void onSectionSelected");
        assertTrue(matches(navigationMethod, "case\\s+\"Calendar\"\\s*->\\s*showCalendarTab\\(\\)"),
                "Selecting the Calendar section must route to the Case Calendar tab.");
        assertTrue(matches(navigationMethod, "case\\s+\"Timeline\"\\s*->\\s*showTimeline\\(\\)"),
                "Timeline must keep its own route after the Calendar tab.");

        String showCalendarTab = methodBody(controller, "private void showCalendarTab");
        assertTrue(matches(showCalendarTab, "setPaneVisible\\(tasksTabPane,\\s*false\\)"),
                "Calendar tab activation must hide the Tasks pane.");
        assertTrue(matches(showCalendarTab, "setPaneVisible\\(caseCalendarTabPane,\\s*true\\)"),
                "Calendar tab activation must show the Calendar pane.");
        assertTrue(matches(showCalendarTab, "setPaneVisible\\(genericPane,\\s*false\\)"),
                "Calendar tab activation must not fall through to the generic Timeline pane.");
        assertTrue(showCalendarTab.contains("loadCaseCalendarAsync()"),
                "Calendar tab activation must load the case calendar flow when stale.");
        assertTrue(showCalendarTab.contains("loadCaseUpdatesAsync()"),
                "Calendar tab activation must keep the shared case updates flow.");

        String resetCaseCalendarState = methodBody(controller, "private void resetCaseCalendarState");
        String setCaseCalendarLayerDefaults = methodBody(controller, "private void setCaseCalendarLayerDefaults");
        String updateCaseCalendarSourceFilterFromControls = methodBody(controller, "private void updateCaseCalendarSourceFilterFromControls");
        assertTrue(resetCaseCalendarState.contains("CalendarFeedSourceFilter.caseCalendarDefaults()"),
                "Resetting the case calendar must preserve the shared calendar feed defaults.");
        assertTrue(setCaseCalendarLayerDefaults.contains("CalendarFeedSourceFilter.caseCalendarDefaults()"),
                "Layer defaults must preserve the shared calendar feed defaults.");
        assertTrue(updateCaseCalendarSourceFilterFromControls.contains("new CalendarFeedSourceFilter(enabled)"),
                "Layer controls must still feed the shared CalendarFeedSourceFilter model.");

        String configureControls = methodBody(controller, "private void configureCaseCalendarControls");
        assertTrue(configureControls.contains("caseCalendarNewEventButton.setOnAction(e -> onCaseCalendarNewEvent())"),
                "The Calendar New Event button must route through the shared event creation flow.");
        assertTrue(configureControls.contains("caseCalendarNewTaskButton.setOnAction(e -> onAddTask())"),
                "The Calendar New Task button must route through the shared task creation flow.");

        String loadCaseCalendarAsync = methodBody(controller, "private void loadCaseCalendarAsync");
        assertTrue(loadCaseCalendarAsync.contains("calendarService.listCalendarFeedForCase"),
                "Case Calendar must load through the shared calendar service feed.");

        String renderAgenda = methodBody(controller, "private void renderCaseCalendarAgenda");
        assertTrue(renderAgenda.contains("No case calendar layers selected."),
                "Case Calendar must preserve its empty-layer user feedback.");
        assertTrue(renderAgenda.contains("caseCalendarSourceFilter::matches"),
                "Case Calendar rendering must apply the shared source filter.");

        String configureClick = methodBody(controller, "private void configureCaseCalendarClick");
        assertTrue(configureClick.contains("CalendarFeedClickTarget.resolve(item)"),
                "Case Calendar row clicks must use the shared click-target resolver.");
        assertTrue(configureClick.contains("openCaseCalendarEventEditor(Math.toIntExact(target.id()))"),
                "Calendar-event rows must route to the shared event editor flow.");
        assertTrue(configureClick.contains("openTask(target.id())"),
                "Task rows must route to the shared task detail flow.");

        String onCaseCalendarNewEvent = methodBody(controller, "private void onCaseCalendarNewEvent");
        assertTrue(onCaseCalendarNewEvent.contains("NewCalendarEventDialog.showAndWait"),
                "Case Calendar event creation must use the shared calendar event dialog.");
        assertTrue(onCaseCalendarNewEvent.contains("calendarService.createEvent"),
                "Case Calendar event creation must persist through the shared calendar service.");

        String openEditor = methodBody(controller, "private void openCaseCalendarEventEditor");
        assertTrue(openEditor.contains("NewCalendarEventDialog.showEditDialog"),
                "Case Calendar event editing must use the shared calendar event dialog.");
        assertTrue(openEditor.contains("calendarService.updateEvent"),
                "Case Calendar event editing must persist through the shared calendar service.");
    }

    private static List<String> caseNavigationSections(String controller) {
        Matcher matcher = Pattern.compile("private\\s+static\\s+final\\s+List<String>\\s+SECTIONS\\s*=\\s*List\\.of\\((.*?)\\);", Pattern.DOTALL)
                .matcher(controller);
        assertTrue(matcher.find(), "Expected CaseController to declare SECTIONS with List.of(...).");
        Matcher stringMatcher = Pattern.compile("\"([^\"]+)\"").matcher(matcher.group(1));
        List<String> sections = new ArrayList<>();
        while (stringMatcher.find()) {
            sections.add(stringMatcher.group(1));
        }
        return sections;
    }

    private static void assertInOrder(List<String> values, String first, String second, String third) {
        int firstIndex = values.indexOf(first);
        int secondIndex = values.indexOf(second);
        int thirdIndex = values.indexOf(third);
        assertTrue(firstIndex >= 0 && secondIndex >= 0 && thirdIndex >= 0,
                "Expected all values to be present: " + List.of(first, second, third));
        assertTrue(firstIndex < secondIndex && secondIndex < thirdIndex,
                () -> String.format("Expected %s, %s, %s in that order but found %s", first, second, third, values));
    }

    private static Document parseFxml(String fxml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(fxml)));
    }

    private static Map<String, Element> elementsByFxId(Document document) {
        java.util.LinkedHashMap<String, Element> elements = new java.util.LinkedHashMap<>();
        collectElementsByFxId(document.getDocumentElement(), elements);
        return elements;
    }

    private static void collectElementsByFxId(Node node, Map<String, Element> elements) {
        if (node instanceof Element element && element.hasAttribute(FX_ID_ATTRIBUTE)) {
            elements.put(element.getAttribute(FX_ID_ATTRIBUTE), element);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectElementsByFxId(child, elements);
        }
    }

    private static void assertDocumentOrder(Document document, String first, String second, String third) {
        List<String> ids = new ArrayList<>(elementsByFxId(document).keySet());
        assertInOrder(ids, first, second, third);
    }

    private static String methodBody(String source, String methodSignaturePrefix) {
        int methodIndex = source.indexOf(methodSignaturePrefix + "(");
        assertTrue(methodIndex >= 0, "Expected CaseController to contain method " + methodSignaturePrefix);
        int bodyStart = source.indexOf('{', methodIndex);
        assertTrue(bodyStart >= 0, "Expected method " + methodSignaturePrefix + " to have a body");
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("Expected method " + methodSignaturePrefix + " body to close");
    }

    private static boolean matches(String source, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(source).find();
    }
}

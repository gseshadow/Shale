package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class MainNavigationSidebarContractTest {
    private static final Path MAIN_FXML = Path.of("src/main/resources/fxml/main.fxml");

    @Test
    void sidebarContainsExactlyTheSupportedDestinationsInOrder() throws Exception {
        Element navigationItems = findByFxId(
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(MAIN_FXML.toFile()).getDocumentElement(),
                "navigationItems");

        List<String> labels = directButtonLabels(navigationItems);
        assertEquals(List.of(
                "My Shale", "Cases", "Contacts", "Organizations", "Team", "Reports", "Calendar", "Settings"),
                labels,
                "The shared sidebar must expose exactly the supported destinations in their intended order.");
        assertFalse(labels.contains("Tasks"), "Tasks must remain available as a route, not as a sidebar button.");
        assertEquals("6", navigationItems.getAttribute("spacing"),
                "Removing Tasks must preserve the shared six-pixel spacing between remaining items.");
    }

    private static List<String> directButtonLabels(Element navigationItems) {
        List<String> labels = new ArrayList<>();
        NodeList descendants = navigationItems.getElementsByTagName("Button");
        for (int index = 0; index < descendants.getLength(); index++) {
            Node node = descendants.item(index);
            if (node instanceof Element button && button.getParentNode().getParentNode() == navigationItems) {
                labels.add(button.getAttribute("text"));
            }
        }
        return labels;
    }

    private static Element findByFxId(Element element, String id) {
        if (id.equals(element.getAttribute("fx:id"))) {
            return element;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                Element match = findByFxId(child, id);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}

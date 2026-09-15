package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

final class SettingsDirectoryContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/settings.fxml");

    @Test void directoryGroupsHaveTheExpectedOrderedRows() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(FXML.toFile());
        assertEquals(List.of("Notification Preferences", "Custom Dictionary"), titles(document, "personalGroup"));
        assertEquals(List.of("Case Statuses", "Practice Areas", "Link Types", "Case Team Roles", "Case Dates", "Protected Case Date Mappings"), titles(document, "caseConfigurationGroup"));
        assertEquals(List.of("Request Fields"), titles(document, "requestConfigurationGroup"));
        assertEquals(List.of("Contact Classifications", "Organization Types"), titles(document, "contactOrganizationConfigurationGroup"));
        assertEquals(List.of("User Management", "Audit Log"), titles(document, "administrationGroup"));
    }

    @Test void everyDirectoryEntryUsesTheSharedRowAndContainsNoInlineStyle() throws Exception {
        String fxml = Files.readString(FXML);
        assertEquals(13, count(fxml, "<SettingsManagementRow "));
        assertFalse(fxml.contains(" style=\""), "Settings presentation must remain stylesheet-owned.");
        assertFalse(fxml.contains("CardsContainer"), "Migrated inline definition hosts must not return to Settings FXML.");
        assertTrue(Files.readString(Path.of("src/main/java/com/shale/ui/component/SettingsManagementRow.java"))
                .contains("ControlStyles.Purpose.SECONDARY"));
    }

    private static List<String> titles(org.w3c.dom.Document document, String groupId) {
        Element group = byId(document, groupId);
        var nodes = group.getElementsByTagName("SettingsManagementRow");
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) result.add(((Element) nodes.item(i)).getAttribute("title"));
        return result;
    }

    private static Element byId(org.w3c.dom.Document document, String id) {
        var nodes = document.getElementsByTagName("VBox");
        for (int i = 0; i < nodes.getLength(); i++) { Element e=(Element)nodes.item(i); if(id.equals(e.getAttribute("fx:id"))) return e; }
        return fail("Missing Settings group " + id);
    }

    private static int count(String value, String needle) { int n=0, at=0; while((at=value.indexOf(needle,at))>=0){n++;at+=needle.length();} return n; }
}

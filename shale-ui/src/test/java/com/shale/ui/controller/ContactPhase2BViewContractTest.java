package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ContactPhase2BViewContractTest {
    private static String view() throws Exception { return Files.readString(Path.of("src/main/resources/fxml/contact.fxml")); }

    @Test void exposesOneCoherentEditActionAndNoFieldPencils() throws Exception {
        String fxml=view();
        assertEquals(1,occurrences(fxml,"text=\"Edit Contact\""));
        assertFalse(fxml.contains("text=\"✎\""));
        assertFalse(fxml.contains("editDisplayNameButton"));
    }

    @Test void preservesRelatedCasesAndSharedLinksBesideProfileSections() throws Exception {
        String fxml=view();
        assertTrue(fxml.contains("Contact Information"));
        assertTrue(fxml.contains("Classifications"));
        assertTrue(fxml.contains("relatedCasesContainer"));
        assertTrue(fxml.contains("sharedLinksContainer"));
        assertTrue(fxml.contains("ScrollPane fitToWidth=\"true\" hbarPolicy=\"NEVER\""));
    }

    @Test void controllerRendersAuthoritativeColoredHistoricalChipsAndAccessibleCredentials() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        assertTrue(source.contains("a.definition().color()"));
        assertTrue(source.contains("· Historical"));
        assertTrue(source.contains("setAccessibleText(a.definition().name()"));
        assertTrue(source.contains("Move Up"));
        assertTrue(source.contains("Move Down"));
        assertTrue(source.contains("saveInFlight"));
        assertTrue(source.contains("generation != detailLoadGeneration"));
        assertTrue(source.contains("void dispose()"));
    }

    @Test void profilePanelsApplyBothSemanticSurfaceClasses() throws Exception {
        String fxml=view();
        assertTrue(Pattern.compile("<String fx:value=\"case-main-surface\"\\s*/>\\s*<String fx:value=\"contact-profile-panel\"",Pattern.DOTALL).matcher(fxml).find());
        assertTrue(Pattern.compile("<String fx:value=\"secondary-panel\"\\s*/>\\s*<String fx:value=\"contact-classifications-panel\"",Pattern.DOTALL).matcher(fxml).find());
        assertFalse(fxml.contains("styleClass=\"case-main-surface contact-profile-panel\""));
    }

    @Test void aggregateEditorUsesSegmentedNavigationAndStyledCompactRows() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        assertTrue(source.contains("ToggleGroup navigationGroup"));
        assertTrue(source.contains("String[] sectionNames={\"Name\",\"Contact Types\",\"Specialties\",\"Credentials\"}"));
        assertTrue(source.contains("contact-editor-name-preview"));
        assertTrue(source.contains("contact-editor-choice-row"));
        assertTrue(source.contains("contact-editor-color-swatch"));
        assertTrue(source.contains("void updateButtons()"));
        assertFalse(source.contains("new javafx.scene.control.TabPane"));
    }

    private static int occurrences(String text,String token) {
        int count=0,at=0; while((at=text.indexOf(token,at))>=0){count++;at+=token.length();} return count;
    }
}

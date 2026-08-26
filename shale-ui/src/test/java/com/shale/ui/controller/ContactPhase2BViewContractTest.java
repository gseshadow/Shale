package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
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

    @Test void aggregateSaveFailuresAreLoggedWithoutPhiAndKeepUserSafeMessages() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        String logger=extractMethod(source,"private static void logAggregateSaveFailure(");
        assertTrue(logger.contains("operation=contact.aggregate-save"));
        assertTrue(logger.contains("tenantId=%d contactId=%d actorId=%d exceptionClass=%s"));
        assertTrue(logger.contains("failure.getClass().getName()"));
        assertTrue(logger.contains(",failure)"),"logger must retain the underlying exception and stack trace");
        for(String prohibited:List.of("displayName","structuredName","classification","email","phone","address","rowVer","expectedContactUpdatedAt","sql","command.toString"))
            assertFalse(logger.toLowerCase().contains(prohibited.toLowerCase()),"PHI-safe logger must exclude "+prohibited);
        assertTrue(source.contains("Save failed and was rolled back. Your values are retained."));
        assertTrue(source.contains("This Contact changed elsewhere. Your values are retained; choose Reload before saving again."));
        assertTrue(source.contains("catch(RuntimeException ex){logAggregateSaveFailure(cmd,ex);"),
                "aggregate save exceptions must not be silently swallowed");
    }

    private static String extractMethod(String source,String signature){
        int start=source.indexOf(signature);assertTrue(start>=0,"missing "+signature);int open=source.indexOf('{',start),depth=0;
        for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);}
        fail("unbalanced "+signature);return "";
    }

    private static int occurrences(String text,String token) {
        int count=0,at=0; while((at=text.indexOf(token,at))>=0){count++;at+=token.length();} return count;
    }
}

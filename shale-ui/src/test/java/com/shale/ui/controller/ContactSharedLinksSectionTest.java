package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ContactSharedLinksSectionTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/contact.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java");

    @Test
    void relatedCasesAndSharedLinksAreSeparateSiblingSections() throws Exception {
        String fxml = Files.readString(FXML);
        int column = fxml.indexOf("<VBox minWidth=\"430\" prefWidth=\"460\" maxWidth=\"520\" spacing=\"16\" GridPane.columnIndex=\"1\">");
        String side = fxml.substring(column, fxml.indexOf("</GridPane>", column));

        int relatedSection = side.indexOf("<VBox spacing=\"8\" styleClass=\"secondary-panel\"");
        int relatedHeading = side.indexOf("Related Cases", relatedSection);
        int relatedContainer = side.indexOf("fx:id=\"relatedCasesContainer\"", relatedHeading);
        int closeRelated = side.indexOf("</VBox>", relatedContainer);
        int sharedSection = side.indexOf("<VBox spacing=\"8\" styleClass=\"secondary-panel\"", closeRelated);
        int sharedHeading = side.indexOf("Links Shared With This Contact", sharedSection);
        int sharedContainer = side.indexOf("fx:id=\"sharedLinksContainer\"", sharedHeading);

        assertTrue(relatedSection >= 0 && relatedHeading > relatedSection && relatedContainer > relatedHeading);
        assertTrue(sharedSection > closeRelated && sharedHeading > sharedSection && sharedContainer > sharedHeading);
    }

    @Test
    void controllerDistinguishesSuccessEmptyFailureAndStaleSharedLinkLoads() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("renderSharedLinksEmpty()"));
        assertTrue(source.contains("renderSharedLinksFailure()"));
        assertTrue(source.contains("generation != sharedLinksLoadGeneration || contactId != requestedContactId"));
        assertTrue(source.contains("caseService.listCaseLinksSharedWithContact(requestedContactId, tenantId)"));
        assertTrue(source.contains("operation=contacts.sharedLinks.success"));
        assertTrue(source.contains("operation=contacts.sharedLinks.failure"));
    }

    @Test
    void initReloadsAfterControllerDependenciesAreInjected() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("private boolean initialized;"));
        assertTrue(source.contains("if (initialized) {\n            resetSharedLinksState();\n            loadContact();\n            loadSharedLinks();\n        }"));
    }
}

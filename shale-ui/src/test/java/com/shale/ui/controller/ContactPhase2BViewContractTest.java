package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
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

    private static int occurrences(String text,String token) {
        int count=0,at=0; while((at=text.indexOf(token,at))>=0){count++;at+=token.length();} return count;
    }
}

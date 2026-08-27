package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

final class ContactStructuredPresentationTest {
    private static String fxml() throws Exception { return Files.readString(Path.of("src/main/resources/fxml/contact.fxml")); }
    private static String controller() throws Exception { return Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java")); }

    @Test void structuredCollectionsAreTheOnlyVisibleContactPointPresentation() throws Exception {
        String view=fxml();
        for(String obsolete:List.of("emailValue","phoneValue","addressHomeValue","emailEditor","phoneEditor","addressHomeEditor"))
            assertFalse(view.contains("fx:id=\""+obsolete+"\""),obsolete);
        assertFalse(view.contains("text=\"Home Address\""));
        assertTrue(view.contains("fx:id=\"phoneCards\""));
        assertTrue(view.contains("fx:id=\"emailCards\""));
        assertTrue(view.contains("fx:id=\"addressCards\""));
        String source=controller();
        assertTrue(source.contains("classificationProfile.phoneNumbers()"));
        assertTrue(source.contains("classificationProfile.emailAddresses()"));
        assertTrue(source.contains("classificationProfile.addresses()"));
        assertFalse(source.contains("currentContact.email()"));
        assertFalse(source.contains("currentContact.phone()"));
        assertFalse(source.contains("currentContact.addressHome()"));
    }

    @Test void personalDetailsActionsAndPreservedSectionsRemain() throws Exception {
        String view=fxml(),source=controller();
        assertTrue(view.contains("fx:id=\"contactTitleLabel\""));
        assertTrue(view.contains("fx:id=\"structuredFullNameValue\""));
        assertTrue(view.contains("fx:id=\"preferredNameValue\""));
        assertTrue(view.contains("Related Cases"));assertTrue(view.contains("Links Shared With This Contact"));
        assertTrue(view.contains("Classifications"));assertTrue(view.contains("ScrollPane fitToWidth=\"true\" hbarPolicy=\"NEVER\""));
        assertTrue(source.contains("ContactExternalActions.telephone"));
        assertTrue(source.contains("ContactExternalActions.email"));
        assertTrue(source.contains("ContactExternalActions.maps"));
        assertTrue(source.contains("width < 900"));
        assertTrue(source.contains("setPrefColumns(twoColumns ? 2 : 1)"));
    }

    @Test void everyFxmlIdHasAControllerField() throws Exception {
        Set<String> ids=matches(fxml(),Pattern.compile("fx:id=\\\"([^\\\"]+)\\\""));
        Set<String> fields=matches(controller(),Pattern.compile("@FXML\\s+private\\s+[\\w.<>]+\\s+(\\w+)\\s*;"));
        assertEquals(Set.of(),difference(ids,fields));
    }
    private static Set<String> matches(String text,Pattern p){Set<String> out=new TreeSet<>();Matcher m=p.matcher(text);while(m.find())out.add(m.group(1));return out;}
    private static Set<String> difference(Set<String>a,Set<String>b){Set<String> out=new TreeSet<>(a);out.removeAll(b);return out;}
}

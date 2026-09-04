package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ContactCreateWorkflowContractTest {
    @Test
    void contactsPageOpensSharedEditorInExplicitCreateMode() throws Exception {
        String fxml=Files.readString(Path.of("src/main/resources/fxml/contacts.fxml"));
        String contacts=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactsController.java"));
        String editor=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        assertTrue(fxml.contains("fx:id=\"addContactButton\"")&&fxml.contains("onAction=\"#addContact\""),
                "Contacts must expose the Add Contact action");
        assertTrue(contacts.contains("showCreateEditor("),"Add Contact must reuse the Contact editor");
        assertTrue(editor.contains("createMode?\"Add Contact\":\"Edit Contact\""));
        assertTrue(editor.contains("createMode?\"Create Contact\":\"Save Changes\""));
        assertTrue(editor.contains("Display Name, First Name, or Last Name is required."));
        assertTrue(editor.contains("saveInFlight")&&editor.contains("saveButton.setDisable(true)"));
        assertFalse(editor.contains("new CreateContactDialog"),"The aggregate editor must not duplicate the legacy form");
    }

    @Test
    void successfulCreateRefreshesWithoutChangingSearchOrFilters() throws Exception {
        String contacts=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactsController.java"));
        assertTrue(contacts.contains("id->{\n            loadFirstPage();"));
        assertFalse(contacts.contains("contactsSearchField.clear()"));
        assertFalse(contacts.contains("selectedContactTypes.clear();\n            loadFirstPage()"));
        assertTrue(contacts.contains("onOpenContact.accept(id)"),"The new Contact should open after refresh");
    }
}

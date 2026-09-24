package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ContactClassificationManagementContractTest {
    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }

    @Test void settingsUsesCompactLazyManagementRow() {
        String fxml = read("src/main/resources/fxml/settings.fxml");
        String settings = read("src/main/java/com/shale/ui/controller/SettingsController.java");
        assertTrue(fxml.contains("title=\"Contact Classifications\""));
        assertTrue(fxml.contains("description=\"Manage contact types, specialties, and credentials.\""));
        assertTrue(fxml.contains("fx:id=\"contactClassificationsRow\""));
        assertFalse(fxml.contains("contactClassificationContent"));
        assertFalse(settings.contains("new ContactClassificationAdminPane"),
                "Settings must not eagerly construct the full manager");
    }

    @Test void settingsAndContactUseTheSameLauncherAndPane() {
        String settings = read("src/main/java/com/shale/ui/controller/SettingsController.java");
        String contact = read("src/main/java/com/shale/ui/controller/ContactViewController.java");
        String launcher = read("src/main/java/com/shale/ui/controller/ContactClassificationManagementLauncher.java");
        assertTrue(settings.contains("new ContactClassificationManagementLauncher"));
        assertTrue(contact.contains("new ContactClassificationManagementLauncher"));
        assertTrue(launcher.contains("new ContactClassificationAdminPane"));
        assertTrue(launcher.contains("DefinitionManagementSession"));
    }

    @Test void contactActionIsAdminOnlyAndRefreshesCapturedContactWithoutAssignmentMutation() {
        String fxml = read("src/main/resources/fxml/contact.fxml");
        String source = read("src/main/java/com/shale/ui/controller/ContactViewController.java");
        assertTrue(fxml.contains("text=\"Manage Classifications\" visible=\"false\" managed=\"false\""));
        assertTrue(source.contains("appState != null && appState.isAdmin()"));
        String method = extractMethod(source, "private void openClassificationManagement()");
        assertTrue(method.contains("final int openingContactId = contactId"));
        assertTrue(method.contains("if (!result.changed() || disposed || contactId != openingContactId) return"));
        assertTrue(method.contains("contactDetailService.invalidateContact(openingContactId, tenantId)"));
        assertTrue(method.contains("loadContact()"));
        for (String mutation : new String[] {"updateContactProfile", "createContactProfile", "IntendedAssignment"})
            assertFalse(method.contains(mutation), "definition management must not mutate assignments");
    }

    @Test void paneRetainsCategoryAndLifecycleSpecificContracts() {
        String pane = read("src/main/java/com/shale/ui/controller/ContactClassificationAdminPane.java");
        assertTrue(pane.contains("tab(\"Contact Types\", DefinitionCategory.CONTACT_TYPE)"));
        assertTrue(pane.contains("tab(\"Specialties\", DefinitionCategory.SPECIALTY)"));
        assertTrue(pane.contains("tab(\"Credentials\", DefinitionCategory.CREDENTIAL)"));
        assertTrue(pane.contains("editorCategory==DefinitionCategory.CREDENTIAL"));
        assertTrue(pane.contains("Abbreviation is required."));
        assertTrue(pane.contains("existing.rowVer()"));
        assertTrue(pane.contains("override ? existing.systemKey()"));
        assertTrue(pane.contains("override ? existing.id()"));
        assertTrue(pane.contains("worker.execute"));
        assertTrue(pane.contains("Platform.runLater"));
        assertTrue(pane.contains("generation != loadGeneration.get()"));
        assertTrue(pane.contains("changed.markCommitted()"));
    }

    @Test void sharedWindowReportsExactlyOneAccumulatedResultAfterDisposal() {
        String window = read("src/main/java/com/shale/ui/component/DefinitionManagementWindow.java");
        assertTrue(window.contains("completed.compareAndSet(false, true)"));
        assertTrue(window.contains("dispose.run()"));
        assertTrue(window.contains("onClosed.accept(new DefinitionManagementResult(changed.getAsBoolean()))"));
    }

    private static String extractMethod(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing " + signature);
        int open = source.indexOf('{', start), depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("unbalanced " + signature);
        return "";
    }
}

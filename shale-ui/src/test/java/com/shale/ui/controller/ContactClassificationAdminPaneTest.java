package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import com.shale.core.service.ContactServicePort;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

class ContactClassificationAdminPaneTest {
    @Test void generatesDeterministicSnakeCaseWithoutCollisionSuffixes() {
        assertEquals("doctor_of_medicine", ContactClassificationAdminPane.systemKeyFromName("Doctor of Medicine"));
        assertEquals("ete_specialty", ContactClassificationAdminPane.systemKeyFromName("Été Specialty"));
		assertEquals("doctor_of_medicine", ContactClassificationAdminPane.systemKeyFromName("  Doctor---of...Medicine  "));
		assertEquals("", ContactClassificationAdminPane.systemKeyFromName(" --!? "));
        assertFalse(ContactClassificationAdminPane.systemKeyFromName("Expert").matches("expert_[0-9]+"));
    }

    @Test void validatesPhaseOneCSystemKeyShape() {
        assertTrue(ContactClassificationAdminPane.validSystemKey("doctor_of_medicine"));
        assertFalse(ContactClassificationAdminPane.validSystemKey("Doctor of Medicine"));
        assertFalse(ContactClassificationAdminPane.validSystemKey("_doctor"));
    }

    @Test void keepsAllThreeLazyCategoryTabsInOneTabPane() {
        JavaFxTestSupport.runAndWait(() -> {
            ContactServicePort service = (ContactServicePort) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] { ContactServicePort.class },
                    (proxy, method, arguments) -> { throw new AssertionError("Unauthorized pane must not query " + method); });
            ContactClassificationAdminPane pane = new ContactClassificationAdminPane(service, new AppState());
            VBox root = (VBox) pane.node();
            TabPane tabs = (TabPane) root.getChildren().getFirst();
            assertEquals(3, tabs.getTabs().size());
            assertEquals("Contact Types", tabs.getTabs().get(0).getText());
            assertEquals("Specialties", tabs.getTabs().get(1).getText());
            assertEquals("Credentials", tabs.getTabs().get(2).getText());
            assertTrue(tabs.getStyleClass().contains("contact-classification-tabs"));
            pane.dispose();
        });
    }

    @Test void descriptionEditorWrapsAndUsesSharedFormStyling() {
        JavaFxTestSupport.runAndWait(() -> {
            TextArea description = new TextArea();
            ContactClassificationAdminPane.configureDescription(description);
            assertTrue(description.isWrapText());
            assertEquals(5, description.getPrefRowCount());
            assertTrue(description.getStyleClass().contains("shale-form-control"));
        });
    }

    @Test void internalKeyIsEditableOnlyForCustomCreation() {
        JavaFxTestSupport.runAndWait(() -> {
            TextField creationKey = new TextField();
            ContactClassificationAdminPane.configureInternalKey(creationKey, true);
            assertTrue(creationKey.isEditable());

            TextField persistedKey = new TextField("expert");
            ContactClassificationAdminPane.configureInternalKey(persistedKey, false);
            assertFalse(persistedKey.isEditable());
            assertFalse(persistedKey.isDisabled(), "read-only key remains selectable and copyable");
            assertTrue(persistedKey.getStyleClass().contains("contact-classification-readonly"));
        });
    }

    @Test void contactServicePortMaintainsStrictImplementationContract() {
        assertTrue(java.util.Arrays.stream(ContactServicePort.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !java.lang.reflect.Modifier.isPrivate(method.getModifiers()))
                .noneMatch(java.lang.reflect.Method::isDefault));
    }
}

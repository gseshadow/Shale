package com.shale.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContactsSemanticControlMigrationTest {
    @Test
    void contactsSurfacesUseExplicitSemanticActionsAndSharedForms() throws Exception {
        String list = read("src/main/java/com/shale/ui/controller/ContactsController.java");
        String detail = read("src/main/java/com/shale/ui/controller/ContactViewController.java");
        String create = read("src/main/java/com/shale/ui/component/dialog/CreateContactDialog.java");
        String fxml = read("src/main/resources/fxml/contact.fxml");

        assertTrue(list.contains("ControlStyles.formControl(contactsSearchField)"));
        assertTrue(detail.contains("ControlStyles.apply(saveButton,ControlStyles.Purpose.PRIMARY)"));
        assertTrue(detail.contains("ControlStyles.apply(editButton, ControlStyles.Purpose.SECONDARY)"));
        assertTrue(detail.contains("ControlStyles.apply(deleteContactButton, ControlStyles.Purpose.DANGER)"));
        assertTrue(detail.contains("ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL"));
        assertTrue(create.contains("ControlStyles.apply(createButton, ControlStyles.Purpose.PRIMARY)"));
        assertTrue(create.contains("ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY)"));
        for (String control : List.of("firstNameField", "lastNameField", "emailField", "phoneField", "clientCheckBox")) {
            assertTrue(create.contains("ControlStyles.formControl(" + control + ")"), control);
        }
        assertFalse(fxml.contains("app-toolbar-button"));
        assertFalse(fxml.contains("case-overview-edit-button"));
        for (String retiredId : List.of("editDisplayNameButton", "editNameButton", "editFirstNameButton",
                "editLastNameButton", "editEmailButton", "editPhoneButton", "editAddressHomeButton",
                "editDateOfBirthButton", "editConditionButton", "editDeceasedButton")) {
            assertFalse(fxml.contains("fx:id=\"" + retiredId + "\""), "retired field pencil remains: " + retiredId);
        }
        assertFalse(create.contains("setStyle("), "contact creation must not retain inline action/form paint");
    }

    @Test
    void productionFxmlAndCssRenderWithoutSemanticCssWarnings() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(25, TimeUnit.SECONDS), "Contacts rendering probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("CSS Error"), output);
        assertFalse(output.contains("CssStyleHelper"), output);
        assertFalse(output.contains("ClassCastException"), output);
        assertFalse(output.contains("cannot be cast to javafx.css.Size"), output);
    }

    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.startup(started::countDown);
            require(started.await(10, TimeUnit.SECONDS), "JavaFX did not start");
            Platform.runLater(() -> {
                try {
                    inspect("/fxml/contacts.fxml", 1280, 800);
                    inspect("/fxml/contacts.fxml", 560, 700);
                    Parent detail = inspect("/fxml/contact.fxml", 1280, 800);
                    inspect("/fxml/contact.fxml", 700, 760);
                    requireStyles(detail, "contact-profile-panel", "case-main-surface", "contact-profile-panel");
                    requireStyles(detail, "contact-classifications-panel", "secondary-panel", "contact-classifications-panel");
                    requireStyle(detail, "editButton", Button.class, "shale-control-secondary");
                    requireStyle(detail, "deleteContactButton", Button.class, "shale-control-danger");
                    requireControl(detail, "conditionValue", javafx.scene.control.Label.class);
                } catch (Throwable thrown) { failure.set(thrown); }
                finally { finished.countDown(); }
            });
            require(finished.await(20, TimeUnit.SECONDS), "JavaFX rendering did not finish");
            Platform.exit();
            if (failure.get() != null) throw new AssertionError("Contacts rendering failed", failure.get());
        }

        private static Parent inspect(String resource, double width, double height) throws Exception {
            Parent root = FXMLLoader.load(requireResource(resource));
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(requireResource("/css/app.css").toExternalForm());
            root.applyCss(); root.layout();
            if (resource.endsWith("contacts.fxml")) {
                Control search = requireControl(root, "contactsSearchField", TextField.class);
                require(search.getStyleClass().contains("shale-form-control"), "search shared form shell");
            }
            return root;
        }

        private static java.net.URL requireResource(String path) {
            java.net.URL resource = Probe.class.getResource(path);
            if (resource == null) throw new AssertionError("Missing " + path);
            return resource;
        }
        private static <T extends Node> T requireControl(Parent root, String id, Class<T> type) {
            Node node = root.lookup("#" + id);
            require(node != null, "Missing required control #" + id);
            require(type.isInstance(node), "Control #" + id + " must be a " + type.getSimpleName());
            return type.cast(node);
        }
        private static <T extends Control> void requireStyle(Parent root, String id, Class<T> type, String styleClass) {
            T control = requireControl(root, id, type);
            require(control.getStyleClass().contains(styleClass), "#" + id + " must use " + styleClass);
        }
        private static void requireStyles(Parent root, String lookupClass, String... expected) {
            Node node = root.lookup("." + lookupClass);
            require(node != null, "Missing required semantic surface ." + lookupClass);
            for (String styleClass : expected) require(node.getStyleClass().contains(styleClass), "." + lookupClass + " must use " + styleClass);
        }
        private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    }
}

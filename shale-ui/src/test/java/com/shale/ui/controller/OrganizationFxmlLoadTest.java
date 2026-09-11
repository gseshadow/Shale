package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

final class OrganizationFxmlLoadTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "Organization FXML load test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void organizationsDirectoryLoadsThroughRealFxmlLoaderWithSearchAndResults() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = loader("/fxml/organizations.fxml");

            Parent root = load(loader, "Organizations directory");
            OrganizationsController controller = loader.getController();
            assertNotNull(controller, "Organizations directory must construct its declared controller.");
            assertInjected(loader, controller, "organizationsSearchField", TextField.class);
            assertInjected(loader, controller, "organizationsFlow", FlowPane.class);
            assertInjected(loader, controller, "addOrganizationButton", Button.class);
            assertInjected(loader, controller, "showRemovedOrganizationsButton", Button.class);
            assertNotNull(root.lookup("#organizationTypeFilter"),
                    "Organizations directory must inject the structured Organization Type filter.");
        });
    }

    @Test
    void newOrganizationLoadsThroughRealFxmlLoaderWithEditorAndFooterActions() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = loader("/fxml/new-organization.fxml");

            load(loader, "New Organization");
            NewOrganizationController controller = loader.getController();
            assertNotNull(controller, "New Organization must construct its declared controller.");
            assertInjected(loader, controller, "editorScroll", ScrollPane.class);
            assertInjected(loader, controller, "cancelButton", Button.class);
            assertInjected(loader, controller, "createOrganizationButton", Button.class);
        });
    }

    @Test
    void organizationProfileLoadsThroughRealFxmlLoaderWithHeaderActions() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = loader("/fxml/organization.fxml");

            load(loader, "Organization profile");

            OrganizationController controller = loader.getController();
            assertNotNull(controller, "Organization profile must construct its declared controller.");
            Button edit = (Button) loader.getNamespace().get("editButton");
            Button delete = (Button) loader.getNamespace().get("deleteOrganizationButton");
            assertNotNull(edit, "Edit Organization action must be present.");
            assertNotNull(delete, "Delete Organization action must be present.");
            assertSame(edit, injectedField(controller, "editButton"), "Edit action must be injected into the controller.");
            assertSame(delete, injectedField(controller, "deleteOrganizationButton"), "Delete action must be injected into the controller.");
        });
    }

    private static FXMLLoader loader(String resource) {
        var location = OrganizationFxmlLoadTest.class.getResource(resource);
        assertNotNull(location, resource + " must be packaged as a runtime resource.");
        return new FXMLLoader(location);
    }

    private static Parent load(FXMLLoader loader, String screen) {
        Parent root = assertDoesNotThrow((org.junit.jupiter.api.function.ThrowingSupplier<Parent>) loader::load,
                screen + " FXML must resolve every JavaFX element type and controller handler.");
        assertNotNull(root, screen + " must produce a root node.");
        return root;
    }

    private static <T> void assertInjected(FXMLLoader loader, Object controller, String fieldName,
            Class<T> controlType) {
        Object control = loader.getNamespace().get(fieldName);
        assertNotNull(control, fieldName + " must exist in the real FXML namespace.");
        assertSame(control, injectedField(controller, fieldName), fieldName + " must be injected into the controller.");
        controlType.cast(control);
    }

    private static Object injectedField(Object controller, String fieldName) {
        return assertDoesNotThrow(() -> {
            Field field = controller.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(controller);
        }, fieldName + " must remain an injectable controller field.");
    }

    private static boolean hasDisplay() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null
                || os.contains("win") || os.contains("mac");
    }
}

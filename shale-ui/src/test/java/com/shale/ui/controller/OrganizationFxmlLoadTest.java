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

final class OrganizationFxmlLoadTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "Organization FXML load test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void organizationProfileLoadsThroughRealFxmlLoaderWithHeaderActions() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(OrganizationFxmlLoadTest.class.getResource("/fxml/organization.fxml"));

            Parent root = assertDoesNotThrow((org.junit.jupiter.api.function.ThrowingSupplier<Parent>) loader::load,
                    "Organization profile FXML must resolve every JavaFX element type.");
            assertNotNull(root, "Organization profile must produce a root node.");

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

    private static Object injectedField(OrganizationController controller, String fieldName) throws Exception {
        Field field = OrganizationController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(controller);
    }

    private static boolean hasDisplay() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null
                || os.contains("win") || os.contains("mac");
    }
}

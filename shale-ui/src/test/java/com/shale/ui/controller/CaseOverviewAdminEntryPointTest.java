package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.shale.core.service.CaseServicePort;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CaseOverviewAdminEntryPointTest {
    @BeforeAll static void toolkit() {
        assumeTrue(hasDisplay(), "JavaFX entry-point test requires a graphical display");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test void overviewKeepsEditEntryPointsAndOmitsDefinitionManagers() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/case.fxml"));
            loader.load();
            CaseController controller = loader.getController();
            Button edit = (Button) loader.getNamespace().get("editOverviewButton");
            Button delete = (Button) loader.getNamespace().get("deleteCaseButton");
            assertNotNull(edit);
            assertNull(loader.getNamespace().get("managePracticeAreasButton"), "Practice Area management belongs in the editor");
            assertNull(loader.getNamespace().get("manageCaseStatusesButton"), "Status management belongs in the editor");
            assertNotNull(loader.getNamespace().get("ovPracticeAreaHost"));
            assertNotNull(loader.getNamespace().get("ovCaseStatusHost"));
            assertNotNull(((Button) loader.getNamespace().get("changePracticeAreaButton")).getOnAction());
            assertNotNull(((Button) loader.getNamespace().get("changeStatusButton")).getOnAction());
            assertSame(delete.getParent(), edit.getParent(), "Edit Overview must share the Overview header action row");
            AppState state = new AppState();
            set(controller, "appState", state);
            state.setAdmin(false);
            controller.refreshOverviewAdminAction();
            assertFalse(edit.isVisible());
            assertFalse(edit.isManaged());
            AtomicBoolean opened = new AtomicBoolean();
            controller.setOverviewEditorLauncherForTest(() -> opened.set(true));
            state.setAdmin(true);
            controller.refreshOverviewAdminAction();
            assertTrue(edit.isVisible());
            assertTrue(edit.isManaged());
            edit.fire();
            assertTrue(opened.get(), "The entry point must invoke the staged Overview editor");
        });
    }

    @Test void relocatedManagersRequireExistingAuthorizationAndContext() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseController controller = new CaseController();
            AppState state = new AppState();
            state.setShaleClientId(7);
            state.setUserId(11);
            set(controller, "appState", state);
            set(controller, "caseId", 42);
            CaseServicePort service = (CaseServicePort) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { CaseServicePort.class }, (proxy, method, args) -> null);
            set(controller, "caseService", service);
            set(controller, "practiceAreaManagementLauncher", new PracticeAreaManagementLauncher(service, Runnable::run));
            set(controller, "caseStatusManagementLauncher", new CaseStatusManagementLauncher(service, Runnable::run));
            for (String label : new String[] { "Practice Area", "Case Status" }) {
                state.setAdmin(false);
                assertAvailability(action(controller, label), false);
                state.setAdmin(true);
                Button authorized = action(controller, label);
                assertAvailability(authorized, true);
                state.setAdmin(false);
                long windows = Window.getWindows().stream().filter(Window::isShowing).count();
                authorized.fire();
                assertEquals(windows, Window.getWindows().stream().filter(Window::isShowing).count(),
                        "Authorization must be checked again when the management action fires");
                state.setAdmin(true);
                state.setShaleClientId(0);
                assertAvailability(action(controller, label), false);
                state.setShaleClientId(7);
                set(controller, "caseService", null);
                assertAvailability(action(controller, label), false);
                set(controller, "caseService", service);
                set(controller, "caseId", null);
                assertAvailability(action(controller, label), false);
                set(controller, "caseId", 42);
            }
            state.setUserId(null);
            assertAvailability(action(controller, "Case Status"), false);
            assertAvailability(action(controller, "Practice Area"), true);
            set(controller, "practiceAreaManagementLauncher", null);
            assertAvailability(action(controller, "Practice Area"), false);
            set(controller, "caseStatusManagementLauncher", null);
            assertAvailability(action(controller, "Case Status"), false);
        });
    }

    private static Button action(CaseController controller, String label) throws Exception {
        Method method = CaseController.class.getDeclaredMethod("createFieldManagementAction", String.class, Supplier.class, Runnable.class);
        method.setAccessible(true);
        return (Button) method.invoke(controller, label, (Supplier<Window>) () -> null, (Runnable) () -> { });
    }

    private static void assertAvailability(Button button, boolean available) {
        assertEquals(available, button.isVisible(), "Management visibility must follow authorization");
        assertEquals(available, button.isManaged(), "Unavailable management must leave no layout gap");
        assertEquals(available, button.isFocusTraversable(), "Unavailable management must leave the tab order");
        assertEquals(available, button.getOnAction() != null, "Unavailable management must have no action handler");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean hasDisplay() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null
                || System.getProperty("os.name", "").toLowerCase().matches(".*(win|mac).*");
    }
}

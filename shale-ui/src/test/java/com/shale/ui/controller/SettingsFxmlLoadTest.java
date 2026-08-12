package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CalendarCaseDateTypeMappingServicePort;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.dao.CalendarEventTypeDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserPreferencesDao;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;

final class SettingsFxmlLoadTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "JavaFX FXML load test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void settingsFxmlLoadsWithSceneManagerControllerConstructionAndAuditActionResolves() throws Exception {
        AtomicBoolean auditOpened = new AtomicBoolean(false);
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(SettingsFxmlLoadTest.class.getResource("/fxml/settings.fxml"));
            loader.setControllerFactory(type -> {
                Object controller = assertDoesNotThrow(() -> type.getDeclaredConstructor().newInstance());
                if (controller instanceof SettingsController settingsController) {
                    settingsController.init(notificationPreferences(), nonAdminState(), () -> auditOpened.set(true),
                            noDatabaseCaseService(), noDatabaseMaterialRequestService(), noDatabaseUserDao(), null,
                            noDatabaseMappingService(), noDatabaseCalendarEventTypeDao());
                }
                return controller;
            });

            Parent root = assertDoesNotThrow((org.junit.jupiter.api.function.ThrowingSupplier<Parent>) loader::load);
            SettingsController controller = loader.getController();
            assertNotNull(controller);
            CheckBox notificationCheck = (CheckBox) loader.getNamespace().get("taskAssignedToMeCheck");
            assertNotNull(notificationCheck, "Existing Settings notification checkbox fx:id should resolve.");
            assertSame(notificationCheck, injectedField(controller, "taskAssignedToMeCheck"),
                    "Existing Settings notification checkbox should remain injected into its controller field.");
            CheckBox inactiveUsers = (CheckBox) loader.getNamespace().get("showInactiveUsersCheck");
            assertNotNull(inactiveUsers, "Existing Settings user-management checkbox should remain wired.");

            Button auditButton = (Button) loader.getNamespace().get("viewAuditLogButton");
            assertNotNull(auditButton, "Audit-log action button should be present when audit viewing is supported.");
            assertNotNull(auditButton.getOnAction(), "FXML should resolve the audit-log action handler.");
            auditButton.fire();
            assertTrue(!auditOpened.get(), "Non-admin Settings users must not open the audit log.");

            assertNotNull(inactiveUsers.getOnAction(), "Existing Settings controls should keep resolving their handlers.");
        });
    }

    private static Object injectedField(SettingsController controller, String fieldName) {
        return assertDoesNotThrow(() -> {
            Field field = SettingsController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(controller);
        });
    }

    private static boolean hasDisplay() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null || System.getProperty("os.name", "").toLowerCase().contains("win") || System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static AppState nonAdminState() {
        AppState appState = new AppState();
        appState.setAdmin(false);
        return appState;
    }

    private static NotificationPreferencesService notificationPreferences() {
        AppState appState = nonAdminState();
        return new NotificationPreferencesService(appState, new UserPreferencesService(new UserPreferencesDao(() -> {
            throw new AssertionError("Settings FXML compatibility validation must not open a database connection.");
        }), appState));
    }

    private static UserDao noDatabaseUserDao() {
        return new UserDao(() -> {
            throw new AssertionError("Settings FXML compatibility validation must not open a database connection.");
        });
    }

    private static CaseServicePort noDatabaseCaseService() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            throw new AssertionError("Settings FXML compatibility validation must not call CaseServicePort." + method.getName());
        };
        return (CaseServicePort) Proxy.newProxyInstance(
                SettingsFxmlLoadTest.class.getClassLoader(),
                new Class<?>[] { CaseServicePort.class },
                handler);
    }

    private static MaterialRequestServicePort noDatabaseMaterialRequestService() {
        return noDatabaseProxy(MaterialRequestServicePort.class);
    }

    private static CalendarCaseDateTypeMappingServicePort noDatabaseMappingService() {
        return noDatabaseProxy(CalendarCaseDateTypeMappingServicePort.class);
    }

    private static CalendarEventTypeDao noDatabaseCalendarEventTypeDao() {
        return new CalendarEventTypeDao(() -> {
            throw new AssertionError("Settings FXML compatibility validation must not open a database connection.");
        });
    }

    private static <T> T noDatabaseProxy(Class<T> port) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if (java.util.List.class.isAssignableFrom(method.getReturnType())) return java.util.List.of();
            throw new AssertionError("Settings FXML compatibility validation must not call " + port.getSimpleName() + "." + method.getName());
        };
        return port.cast(Proxy.newProxyInstance(SettingsFxmlLoadTest.class.getClassLoader(), new Class<?>[] { port }, handler));
    }
}

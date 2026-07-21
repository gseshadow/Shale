package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserPreferencesDao;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;

final class SettingsFxmlLoadTest {
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean();

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        assumeTrue(hasDisplay(), "JavaFX FXML load test requires a graphical display.");
        if (TOOLKIT_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX toolkit should start for real FXML loading.");
        }
    }

    @Test
    void settingsFxmlLoadsWithSceneManagerControllerConstructionAndAuditActionResolves() throws Exception {
        AtomicBoolean auditOpened = new AtomicBoolean(false);
        FXMLLoader loader = new FXMLLoader(SettingsFxmlLoadTest.class.getResource("/fxml/settings.fxml"));
        loader.setControllerFactory(type -> {
            Object controller = assertDoesNotThrow(() -> type.getDeclaredConstructor().newInstance());
            if (controller instanceof SettingsController settingsController) {
                settingsController.init(notificationPreferences(), nonAdminState(), () -> auditOpened.set(true), noDatabaseCaseService(), noDatabaseUserDao(), null);
            }
            return controller;
        });

        Parent root = assertDoesNotThrow((org.junit.jupiter.api.function.ThrowingSupplier<Parent>) loader::load);
        assertNotNull(loader.getController());
        assertNotNull(root.lookup("#taskAssignedToMeCheck"), "Existing Settings notification checkbox should remain wired.");
        assertNotNull(root.lookup("#showInactiveUsersCheck"), "Existing Settings user-management checkbox should remain wired.");

        Button auditButton = (Button) root.lookup("#viewAuditLogButton");
        assertNotNull(auditButton, "Audit-log action button should be present when audit viewing is supported.");
        assertNotNull(auditButton.getOnAction(), "FXML should resolve the audit-log action handler.");
        auditButton.fire();
        assertTrue(!auditOpened.get(), "Non-admin Settings users must not open the audit log.");

        CheckBox inactiveUsers = (CheckBox) root.lookup("#showInactiveUsersCheck");
        assertNotNull(inactiveUsers.getOnAction(), "Existing Settings controls should keep resolving their handlers.");
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
}

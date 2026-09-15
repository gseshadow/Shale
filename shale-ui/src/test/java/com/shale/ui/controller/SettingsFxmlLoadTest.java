package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.service.CaseServicePort;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserPreferencesDao;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.component.SettingsManagementRow;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;

final class SettingsFxmlLoadTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "JavaFX FXML load test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void sharedRowOwnsItsCompleteActionContractAcrossAvailabilityChanges() {
        JavaFxTestSupport.runAndWait(() -> {
            SettingsManagementRow row = new SettingsManagementRow();
            AtomicBoolean opened = new AtomicBoolean();
            row.configure("Case Statuses", "Manage case statuses.", "Manage", event -> opened.set(true));

            assertEquals("Case Statuses", row.getTitle());
            assertEquals("Manage case statuses.", row.getDescription());
            assertEquals("Manage", row.getActionText());
            assertEquals("Manage Case Statuses", row.getActionButton().getAccessibleText());
            assertTrue(row.getActionButton().getStyleClass().contains("shale-control-secondary"));
            assertTrue(row.getActionButton().getStyleClass().contains("shale-control-small"));

            row.setAvailable(false);
            assertTrue(!row.isVisible() && !row.isManaged());
            assertTrue(!row.getActionButton().isVisible() && !row.getActionButton().isManaged());
            assertEquals("Manage", row.getActionText(), "Availability must not erase the action label.");
            assertTrue(row.getActionButton().getOnAction() == null);

            row.setAvailable(true);
            row.getActionButton().fire();
            assertTrue(opened.get(), "Restoring availability must restore the configured action handler.");
        });
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
                            noDatabaseCaseService(), noDatabaseMaterialRequestService(), noDatabaseUserDao(), null);
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
            assertNull(loader.getNamespace().get("showInactiveUsersCheck"), "Inline User Management controls must be absent.");

            Button auditButton = ((SettingsManagementRow) loader.getNamespace().get("auditLogRow")).getActionButton();
            assertNotNull(auditButton.getOnAction(), "FXML should resolve the audit-log action handler.");
            auditButton.fire();
            assertTrue(!auditOpened.get(), "Non-admin Settings users must not open the audit log.");

            Button manageDictionary = ((SettingsManagementRow) loader.getNamespace().get("customDictionaryRow")).getActionButton();
            assertNotNull(manageDictionary, "Custom Dictionary must be presented as one compact Settings action.");
            assertNotNull(manageDictionary.getOnAction(), "The dictionary manager must be created only from the Manage action.");

            for (String rowId : List.of("customDictionaryRow", "caseStatusesRow", "practiceAreasRow",
                    "linkTypesRow", "caseTeamRolesRow", "caseDatesRow", "requestFieldsRow",
                    "contactClassificationsRow", "organizationTypesRow", "userManagementRow")) {
                SettingsManagementRow row = (SettingsManagementRow) loader.getNamespace().get(rowId);
                assertEquals("Manage", row.getActionText(), rowId + " must visibly identify its popup action.");
                assertEquals("Manage " + row.getTitle(), row.getActionButton().getAccessibleText(),
                        rowId + " must expose the same action and target to assistive technology.");
            }
            for (String rowId : List.of("notificationPreferencesRow", "caseDateMappingsRow", "auditLogRow")) {
                assertEquals("Open", ((SettingsManagementRow) loader.getNamespace().get(rowId)).getActionText(),
                        rowId + " must preserve its non-popup action semantics.");
            }

            assertNotNull(loader.getNamespace().get("organizationTypesRow"));
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

    private static <T> T noDatabaseProxy(Class<T> port) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if (java.util.List.class.isAssignableFrom(method.getReturnType())) return java.util.List.of();
            throw new AssertionError("Settings FXML compatibility validation must not call " + port.getSimpleName() + "." + method.getName());
        };
        return port.cast(Proxy.newProxyInstance(SettingsFxmlLoadTest.class.getClassLoader(), new Class<?>[] { port }, handler));
    }
}

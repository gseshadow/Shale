package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.util.ControlAvailability;
import com.shale.ui.component.SettingsManagementRow;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

final class DefinitionManagementVisibilityTest {
    private static final String[] ROWS = {"caseDatesRow", "contactClassificationsRow", "organizationTypesRow",
            "linkTypesRow", "requestFieldsRow", "caseTeamRolesRow", "practiceAreasRow", "firmWideRolesRow"};

    @BeforeAll static void toolkit() { assumeTrue(hasDisplay()); JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void helperRemovesHiddenActionFromLayoutFocusAndInvocation() {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button("Manage"); VBox onlyHost = new VBox(button); AtomicBoolean invoked = new AtomicBoolean();
            ControlAvailability.apply(button, onlyHost, false, e -> invoked.set(true));
            assertAll(() -> assertFalse(button.isVisible()), () -> assertFalse(button.isManaged()),
                    () -> assertFalse(button.isFocusTraversable()), () -> assertFalse(onlyHost.isVisible()),
                    () -> assertFalse(onlyHost.isManaged()), () -> assertNull(button.getOnAction()));
            button.fire(); assertFalse(invoked.get(), "A retained reference must not invoke a hidden management action.");
            ControlAvailability.apply(button, onlyHost, true, e -> invoked.set(true));
            assertAll(() -> assertTrue(button.isVisible()), () -> assertTrue(button.isManaged()),
                    () -> assertTrue(button.isFocusTraversable()), () -> assertTrue(onlyHost.isVisible()),
                    () -> assertTrue(onlyHost.isManaged()));
            button.fire(); assertTrue(invoked.get());
        });
    }

    @Test void allCompactSettingsRowsTrackLateAdminDependenciesAndContext() {
        JavaFxTestSupport.runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
                Parent ignored = loader.load(); SettingsController controller = loader.getController();
                AppState state = new AppState(); state.setShaleClientId(7); state.setUserId(11); state.setAdmin(false);
                set(controller, "appState", state); installServices(controller); refresh(controller);
                assertRows(loader, false);
                state.setAdmin(true); refresh(controller); assertRows(loader, true);
                set(controller, "materialRequestService", null); refresh(controller);
                assertHidden(loader, "requestFieldsRow");
                assertTrue(((SettingsManagementRow) loader.getNamespace().get("practiceAreasRow")).isVisible(),
                        "A missing request service must not hide unrelated Settings rows.");
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
    }

    private static void assertRows(FXMLLoader loader, boolean visible) {
        for (String id : ROWS) {
            SettingsManagementRow row=(SettingsManagementRow)loader.getNamespace().get(id); Button button=row.getActionButton();
            assertEquals(visible,button.isVisible(),id+" action visibility"); assertEquals(visible,button.isManaged(),id+" action managed");
            assertEquals(visible,button.isFocusTraversable(),id+" focus traversal");
            assertEquals(visible,row.isVisible(),id+" visibility"); assertEquals(visible,row.isManaged(),id+" managed");
            if(!visible) assertNull(button.getOnAction(),id+" must clear its handler");
        }
    }
    private static void assertHidden(FXMLLoader l,String id){SettingsManagementRow row=(SettingsManagementRow)l.getNamespace().get(id);assertFalse(row.getActionButton().isVisible());assertFalse(row.getActionButton().isManaged());assertFalse(row.isManaged());}
    private static void installServices(SettingsController c)throws Exception {set(c,"caseService",proxy(CaseServicePort.class));set(c,"materialRequestService",proxy(MaterialRequestServicePort.class));set(c,"contactService",proxy(ContactServicePort.class));set(c,"organizationService",proxy(OrganizationServicePort.class));}
    private static <T>T proxy(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->List.class.isAssignableFrom(m.getReturnType())?List.of():defaultValue(m.getReturnType())));}
    private static Object defaultValue(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
    private static void refresh(SettingsController c)throws Exception{Method m=SettingsController.class.getDeclaredMethod("updateAdminControlsVisibility");m.setAccessible(true);m.invoke(c);}
    private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static boolean hasDisplay(){return System.getenv("DISPLAY")!=null||System.getenv("WAYLAND_DISPLAY")!=null||System.getProperty("os.name","").toLowerCase().matches(".*(win|mac).*");}
}

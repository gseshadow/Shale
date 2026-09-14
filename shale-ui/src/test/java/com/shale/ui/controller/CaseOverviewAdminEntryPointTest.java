package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import com.shale.core.service.CaseServicePort;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CaseOverviewAdminEntryPointTest {
 @BeforeAll static void toolkit(){assumeTrue(hasDisplay(),"JavaFX entry-point test requires a graphical display");JavaFxTestSupport.ensureToolkitStarted();}
 @Test void visibilityTracksAuthenticatedAdminAndClickInvokesStagedEditor(){JavaFxTestSupport.runAndWait(()->{try{FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/case.fxml"));loader.load();CaseController controller=loader.getController();Button edit=(Button)loader.getNamespace().get("editOverviewButton");Button delete=(Button)loader.getNamespace().get("deleteCaseButton");Button managePracticeAreas=(Button)loader.getNamespace().get("managePracticeAreasButton");assertNotNull(edit);assertNotNull(managePracticeAreas);assertTrue(managePracticeAreas.getStyleClass().contains("shale-control-secondary"),"Manage Practice Areas must receive its semantic purpose during FXML initialization");assertTrue(managePracticeAreas.getStyleClass().contains("shale-control-small"),"Manage Practice Areas must fit the compact Overview content");assertSame(delete.getParent(),edit.getParent(),"Edit Overview must share the visible Overview header action row");AppState state=new AppState();set(controller,"appState",state);state.setAdmin(false);controller.refreshOverviewAdminAction();assertFalse(edit.isVisible());assertFalse(edit.isManaged());AtomicBoolean opened=new AtomicBoolean();controller.setOverviewEditorLauncherForTest(()->opened.set(true));state.setAdmin(true);controller.refreshOverviewAdminAction();assertTrue(edit.isVisible());assertTrue(edit.isManaged());edit.fire();assertTrue(opened.get(),"The visible entry point must invoke the staged Overview editor");}catch(Exception ex){throw new AssertionError(ex);}});}
 @Test void practiceAreaManagementRequiresLateAdminServiceAndTenantContext(){JavaFxTestSupport.runAndWait(()->{try{FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/case.fxml"));loader.load();CaseController controller=loader.getController();Button manage=(Button)loader.getNamespace().get("managePracticeAreasButton");AppState state=new AppState();state.setAdmin(false);state.setShaleClientId(7);state.setUserId(11);set(controller,"appState",state);set(controller,"caseId",42);CaseServicePort service=(CaseServicePort)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CaseServicePort.class},(p,m,a)->null);set(controller,"caseService",service);set(controller,"practiceAreaManagementLauncher",new PracticeAreaManagementLauncher(service,Runnable::run));refreshManagement(controller);assertFalse(manage.isVisible());assertFalse(manage.isManaged());assertFalse(manage.isFocusTraversable());assertNull(manage.getOnAction());state.setAdmin(true);refreshManagement(controller);assertTrue(manage.isVisible());assertTrue(manage.isManaged());assertTrue(manage.isFocusTraversable());set(controller,"practiceAreaManagementLauncher",null);refreshManagement(controller);assertFalse(manage.isVisible());assertFalse(manage.isManaged());assertNull(manage.getOnAction());}catch(Exception ex){throw new AssertionError(ex);}});}
 private static void refreshManagement(CaseController controller)throws Exception{Method m=CaseController.class.getDeclaredMethod("refreshContextualDefinitionManagementActions");m.setAccessible(true);m.invoke(controller);}
 private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 private static boolean hasDisplay(){return System.getenv("DISPLAY")!=null||System.getenv("WAYLAND_DISPLAY")!=null||System.getProperty("os.name","").toLowerCase().matches(".*(win|mac).*");}
}

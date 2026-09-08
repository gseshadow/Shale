package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CaseOverviewAdminEntryPointTest {
 @BeforeAll static void toolkit(){assumeTrue(hasDisplay(),"JavaFX entry-point test requires a graphical display");JavaFxTestSupport.ensureToolkitStarted();}
 @Test void visibilityTracksAuthenticatedAdminAndClickInvokesStagedEditor(){JavaFxTestSupport.runAndWait(()->{try{FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/case.fxml"));Parent root=loader.load();CaseController controller=loader.getController();Button edit=(Button)loader.getNamespace().get("editOverviewButton");Button delete=(Button)loader.getNamespace().get("deleteCaseButton");assertNotNull(edit);assertSame(delete.getParent(),edit.getParent(),"Edit Overview must share the visible Overview header action row");AppState state=new AppState();set(controller,"appState",state);state.setAdmin(false);controller.refreshOverviewAdminAction();assertFalse(edit.isVisible());assertFalse(edit.isManaged());AtomicBoolean opened=new AtomicBoolean();controller.setOverviewEditorLauncherForTest(()->opened.set(true));state.setAdmin(true);controller.refreshOverviewAdminAction();assertTrue(edit.isVisible());assertTrue(edit.isManaged());edit.fire();assertTrue(opened.get(),"The visible entry point must invoke the staged Overview editor");Parent ancestor=edit.getParent();while(ancestor!=null&&ancestor!=root)ancestor=ancestor.getParent();assertSame(root,ancestor,"Edit Overview must be a descendant of the loaded FXML root");}catch(Exception ex){throw new AssertionError(ex);}});}
 private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 private static boolean hasDisplay(){return System.getenv("DISPLAY")!=null||System.getenv("WAYLAND_DISPLAY")!=null||System.getProperty("os.name","").toLowerCase().matches(".*(win|mac).*");}
}

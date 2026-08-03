package com.shale.ui.controller;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsUserManagementSemanticRuntimeTest {
    @Test void finalInjectedButtonsHaveOneRuntimePurposeAndStandardSizeEvenWhenDisabled(){
        Assumptions.assumeTrue(System.getenv("DISPLAY")!=null || System.getProperty("os.name","").toLowerCase().contains("win"),"graphical runtime unavailable");
        JavaFxTestSupport.runAndWait(()->{try{
            Parent root=FXMLLoader.load(getClass().getResource("/fxml/settings.fxml"));
            Map<String,String> expected=Map.of("addUserButton","shale-control-primary","editUserButton","shale-control-secondary","deactivateUserButton","shale-control-danger","reactivateUserButton","shale-control-secondary","resetPasswordButton","shale-control-secondary","refreshUsersButton","shale-control-ghost","removeUserButton","shale-control-danger");
            for(var entry:expected.entrySet()){
                Button b=(Button)root.lookup("#"+entry.getKey());assertNotNull(b,entry.getKey());
                assertTrue(b.getStyleClass().containsAll(Set.of("button","shale-control-button","shale-control-standard",entry.getValue())),b.getStyleClass().toString());
                assertEquals(1,b.getStyleClass().stream().filter(s->Set.of("shale-control-primary","shale-control-secondary","shale-control-danger","shale-control-ghost","shale-control-navigation").contains(s)).count());
                assertFalse(b.getStyleClass().stream().anyMatch(s->s.startsWith("app-toolbar-button")),b.getStyleClass().toString());
            }
            long primary=expected.values().stream().filter("shale-control-primary"::equals).count();assertEquals(1,primary);
        }catch(Exception e){throw new AssertionError(e);}});
    }
}

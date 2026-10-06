package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginRememberedSignInContractTest {
    @Test void checkedLoginCommitsOnlyAfterRuntimeEnrollmentAndCleansUpUnsupportedServer() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/LoginController.java"));
        int runtime=source.indexOf("runtimeBridge.onLoginSuccess(result.userId()");
        int commit=source.indexOf("authService.commitRememberedCredential()",runtime);
        int cleanup=source.indexOf("runtimeBridge.onLogout()",commit);
        assertTrue(runtime>=0&&commit>runtime,"the credential returned by enrollment must be committed after runtime enrollment");
        assertTrue(cleanup>commit,"a checked login rejected for missing server remember support must tear down its initialized runtime");
        assertTrue(source.contains("stayLoggedInCheckBox.isSelected()"),"the checkbox value must reach authentication");
        assertTrue(source.contains("authService.login(email, pass,stayLoggedIn)"),"the remember choice must reach desktop enrollment");
    }

    @Test void startupPresenceInitiatesRestoreAsynchronously() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/LoginController.java"));
        assertTrue(source.contains("authService.hasRememberedCredential()"));
        assertTrue(source.contains("Platform.runLater(this::attemptRestore)"));
        assertTrue(source.contains("new Thread(()->{try{UiAuthService.Result result=authService.restore()"),
                "restore must not block the JavaFX application thread");
    }
}

package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesktopRememberedSignInDiagnosticsTest {
    @Test void lifecycleDiagnosticsDescribeOutcomesWithoutSecretValues() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/desktop/session/DesktopSessionEnrollmentLifecycle.java"));
        for(String expected:new String[]{"remember requested={}","serverRememberCredentialPresent={}","protected save outcome=SUCCESS","startup read outcome=FAILED","restore attempt started","restore result=SUCCESS","credential deletion requested reason={}","ordinary shutdown; remembered credential preserved"})
            assertTrue(source.contains(expected),"missing safe diagnostic: "+expected);
        assertFalse(source.contains("log.info(credential"),"credential values must never be logged");
        assertFalse(source.contains("log.warn(credential"),"credential values must never be logged");
    }
}

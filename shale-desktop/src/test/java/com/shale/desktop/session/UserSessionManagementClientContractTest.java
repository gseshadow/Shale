package com.shale.desktop.session;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class UserSessionManagementClientContractTest{
 @Test void adapterUsesOnlyAuthoritativeSelfEndpointsAndBearer()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/desktop/session/UserSessionManagementClient.java"));assertTrue(s.contains("/api/sessions"));assertTrue(s.contains("/current/revoke"));assertTrue(s.contains("/revoke-others"));assertTrue(s.contains("/revoke"));assertTrue(s.contains("Authorization"));assertFalse(s.contains("/api/admin/sessions"));assertFalse(s.contains("UserSessions"));}
}

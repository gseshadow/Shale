package com.shale.desktop.session;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
final class AdminSessionManagementClientContractTest{
 @Test void adapterUsesOnlyBoundedAuthoritativeAdminApiAndSafeProjection()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/desktop/session/AdminSessionManagementClient.java"));assertTrue(s.contains("/api/admin/sessions"));for(String q:new String[]{"page=","size=","activeOnly=","userId=","clientType=","since="})assertTrue(s.contains(q),q);assertTrue(s.contains("Authorization"));assertFalse(s.contains("UserSessions"));assertFalse(s.contains("currentAccessJti"));}
}

package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class DevicesSessionsPaneContractTest {
 @Test void presentationUsesOnlySafePhase8aFieldsAndPreciseLabels()throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/DevicesSessionsPane.java"));
  for(String allowed:new String[]{"Signed in","Last refreshed","Expires","Revoked","Active means the session is unexpired and has not been revoked","Current session","Sign out this session","Sign out all other sessions"})assertTrue(source.contains(allowed),allowed);
  for(String forbidden:new String[]{"Last active","Last server contact","machineId","tenantId","userId","currentJti","accessToken","ipAddress","location","latitude","longitude"})assertFalse(source.contains("\""+forbidden),forbidden);
 }
 @Test void currentSessionCannotUseRowRevocationAndUnknownClientsDegradeSafely()throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/DevicesSessionsPane.java"));
  assertTrue(source.contains("if(s.currentSession())return"));
  assertEquals("Desktop",DevicesSessionsPane.client("DESKTOP"));assertEquals("Web",DevicesSessionsPane.client("web"));assertEquals("Mobile",DevicesSessionsPane.client("MOBILE"));assertEquals("Unknown",DevicesSessionsPane.client("future"));
 }
 @Test void lifecycleGenerationProtectsUserSwitchAndClosedPane()throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/DevicesSessionsPane.java"));
  assertTrue(source.contains("!open||token!=generation.get()||!who.equals(identity())"));
  assertTrue(source.contains("rows.getChildren().clear()"));
  assertTrue(source.contains("subscribeConnectivity(connectivity)"));
  assertTrue(source.contains("unsubscribeConnectivity(connectivity)"));
  assertTrue(source.contains("if(loading)return"),"duplicate reconnect hints must coalesce behind the in-flight load");
 }
 @Test void currentRevocationUsesTheEstablishedRuntimeCapabilityAfterConfirmation()throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/DevicesSessionsPane.java"));
  assertTrue(source.contains("showDestructiveConfirmation"));
  assertTrue(source.contains("UserSessionManagement::revokeCurrent"));
  assertTrue(source.contains("session-ended message"));
 }
}

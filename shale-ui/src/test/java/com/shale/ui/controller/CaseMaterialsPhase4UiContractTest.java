package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

final class CaseMaterialsPhase4UiContractTest {
  private static String read(String p){ try { return Files.readString(Path.of(p)); } catch(Exception e){ throw new RuntimeException(e);} }
  private static final String CTRL = read("src/main/java/com/shale/ui/controller/CaseController.java");
  private static final String FXML = read("src/main/resources/fxml/case.fxml");
  private static final String MAT = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

  @Test void materialsTabIsInCaseViewBetweenCalendarAndLinks() {
    assertTrue(CTRL.contains("\"Calendar\",\n\t\t\t\"Materials\",\n\t\t\t\"Links\""));
    assertTrue(CTRL.contains("case \"Materials\" -> showMaterialsTab();"));
    assertTrue(FXML.indexOf("caseCalendarTabPane") < FXML.indexOf("caseMaterialsTabPane"));
    assertTrue(FXML.indexOf("caseMaterialsTabPane") < FXML.indexOf("caseLinksTabPane"));
  }

  @Test void asyncCaseScopedLoadingUsesServicePortsAndStaleGuard() {
    assertTrue(MAT.contains("MaterialRequestServicePort"));
    assertTrue(MAT.contains("MaterialItemServicePort"));
    assertTrue(MAT.contains("executor.submit"));
    assertTrue(MAT.contains("Platform.runLater"));
    assertTrue(MAT.contains("staleRequest(g,cid)"));
    assertTrue(MAT.contains("staleItem(g,cid)"));
    assertTrue(MAT.contains("listMaterialRequests(cid, tid)"));
    assertTrue(MAT.contains("listMaterialItems(cid, tid)"));
  }

  @Test void summariesAvoidSensitiveFieldsAndDetailsLoadOnDemand() {
    String requestCard = MAT.substring(MAT.indexOf("private Node requestCard"), MAT.indexOf("private Node itemCard"));
    String itemCard = MAT.substring(MAT.indexOf("private Node itemCard"), MAT.indexOf("private void openRequestDetail"));
    assertFalse(requestCard.contains("description()"));
    assertFalse(requestCard.contains("notes()"));
    assertFalse(itemCard.contains("description()"));
    assertFalse(itemCard.contains("physicalCondition()"));
    assertTrue(MAT.contains("getMaterialRequest(cid,id,tid,actor)"));
    assertTrue(MAT.contains("getMaterialItem(cid,id,tid,actor)"));
  }

  @Test void mutationCommandsMapRowVerAndExplicitOperations() {
    for (String command : new String[]{"CreateMaterialRequestCommand","UpdateMaterialRequestCommand","ChangeMaterialRequestStatusCommand","DeleteMaterialRequestCommand","RecordMaterialRequestFollowUpCommand","CreateMaterialItemCommand","UpdateMaterialItemCommand","ChangeMaterialItemLocationCommand","LinkMaterialItemToRequestCommand","UnlinkMaterialItemFromRequestCommand","ReleaseOrReturnMaterialItemCommand","SoftDeleteMaterialItemCommand"}) {
      assertTrue(MAT.contains(command), command);
    }
    assertTrue(MAT.contains("d.rowVer()"));
    assertTrue(MAT.contains("e.rowVer()"));
    assertTrue(MAT.contains("changed by another user"));
  }

  @Test void scopeGuardsNoForbiddenPhaseFourWork() {
    assertFalse(MAT.contains("FileChooser"));
    assertFalse(MAT.toLowerCase().contains("open file"));
    assertFalse(MAT.toLowerCase().contains("download action"));
    assertFalse(MAT.toLowerCase().contains("ocr"));
    assertFalse(MAT.contains("CREATE TABLE"));
    assertFalse(MAT.contains("DELETE FROM dbo.MaterialRequestFollowUps"));
    assertFalse(MAT.contains("UPDATE dbo.MaterialRequestFollowUps"));
    assertFalse(MAT.toLowerCase().contains("timeline"));
    assertFalse(MAT.toLowerCase().contains("calendar"));
  }
}

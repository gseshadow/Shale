package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

final class CaseMaterialsPhase4UiContractTest {
  private static String read(String p){ try { return Files.readString(Path.of(p)); } catch(Exception e){ throw new RuntimeException(e);} }
  private static final String CTRL = read("src/main/java/com/shale/ui/controller/CaseController.java");
  private static final String FXML = read("src/main/resources/fxml/case.fxml");
  private static final String MAT = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

  @Test void requestsAndCaseMaterialsAreFirstClassTabsInOrder() {
    assertTrue(CTRL.contains("\"Calendar\",\n\t\t\t\"Requests\",\n\t\t\t\"Case Materials\",\n\t\t\t\"Links\""));
    assertTrue(CTRL.contains("case \"Requests\" -> showRequestsTab();"));
    assertTrue(CTRL.contains("case \"Case Materials\" -> showMaterialsTab();"));
    assertFalse(CTRL.contains("case \"Materials\""));
    assertTrue(FXML.indexOf("caseCalendarTabPane") < FXML.indexOf("caseRequestsTabPane"));
    assertTrue(FXML.indexOf("caseRequestsTabPane") < FXML.indexOf("caseMaterialsTabPane"));
    assertTrue(FXML.indexOf("caseMaterialsTabPane") < FXML.indexOf("caseLinksTabPane"));
  }

  @Test void eachTabHasIndependentControllerAndLoadFailureMessaging() {
    assertTrue(MAT.contains("final class CaseMaterialRequestsTabController"));
    assertTrue(MAT.contains("final class CaseMaterialItemsTabController"));
    assertTrue(MAT.contains("case-material-requests-worker"));
    assertTrue(MAT.contains("case-material-items-worker"));
    assertTrue(MAT.contains("Material requests could not be loaded."));
    assertTrue(MAT.contains("Case materials could not be loaded."));
    assertTrue(MAT.contains("LOG.warn"));
    assertTrue(MAT.contains("listMaterialRequests(cid,tid)"));
    assertTrue(MAT.contains("listMaterialItems(c,t)"));
    assertFalse(MAT.contains("The materials change could not be completed"));
  }

  @Test void caseMaterialsPrimaryLoadIsDecoupledFromRequestChoices() {
    String refresh = MAT.substring(MAT.indexOf("void refresh(){long c=cid();int t=tenant();int g=gen.incrementAndGet();", MAT.indexOf("final class CaseMaterialItemsTabController")), MAT.indexOf("private void render(List<MaterialItemSummaryDto>", MAT.indexOf("final class CaseMaterialItemsTabController")));
    assertTrue(refresh.contains("runRead(\"list-material-items\",()->svc.listMaterialItems(c,t)"));
    assertFalse(refresh.contains("req.listMaterialRequests"));
    assertTrue(MAT.contains("requestChoiceGen"));
    assertTrue(MAT.contains("requestChoicesOrShowError"));
    assertTrue(MAT.contains("Material request choices could not be loaded"));
    assertTrue(MAT.contains("Case Materials auxiliary lookup failed"));
    assertFalse(MAT.contains("list-material-items\",()->{activeRequests=req.listMaterialRequests"));
  }

  @Test void creationUsesFullCommandBackedFormsNotTitleOnlyDialogs() {
    assertTrue(MAT.contains("final class MaterialRequestForm extends Dialog"));
    assertTrue(MAT.contains("CreateMaterialRequestCommand"));
    assertTrue(MAT.contains("Material Type *"));
    assertTrue(MAT.contains("Controlled free-text source"));
    assertTrue(MAT.contains("Requested-by user *"));
    assertTrue(MAT.contains("Assigned user selector"));
    assertTrue(MAT.contains("Initial status"));
    assertTrue(MAT.contains("final class MaterialItemForm extends Dialog"));
    assertTrue(MAT.contains("CreateMaterialItemCommand"));
    assertTrue(MAT.contains("Identity: Material Type *"));
    assertTrue(MAT.contains("Optional associated Material Request"));
    assertTrue(MAT.contains("ExternalLink reference selector"));
    assertTrue(MAT.contains("Storage location (not a file upload)"));
    assertFalse(MAT.contains("setTitle(\"Confirmation\")"));
  }

  @Test void detailsEditsAndExplicitItemOperationsRemainSeparated() {
    for (String command : new String[]{"UpdateMaterialRequestCommand","ChangeMaterialRequestStatusCommand","DeleteMaterialRequestCommand","RecordMaterialRequestFollowUpCommand","UpdateMaterialItemCommand","ChangeMaterialItemLocationCommand","LinkMaterialItemToRequestCommand","UnlinkMaterialItemFromRequestCommand","ReleaseOrReturnMaterialItemCommand","SoftDeleteMaterialItemCommand"}) assertTrue(MAT.contains(command), command);
    assertTrue(MAT.contains("Append-only follow-up history"));
    assertTrue(MAT.contains("Edit Metadata"));
    assertTrue(MAT.contains("Link/Unlink Request"));
    assertTrue(MAT.contains("Location/Reference"));
    assertTrue(MAT.contains("Record Return/Release"));
    assertTrue(MAT.contains("d.rowVer()"));
    assertTrue(MAT.contains("e.rowVer()"));
  }

  @Test void scopeGuardsNoForbiddenWork() {
    assertFalse(MAT.contains("FileChooser"));
    assertFalse(MAT.toLowerCase().contains("download action"));
    assertFalse(MAT.toLowerCase().contains("ocr"));
    assertFalse(MAT.contains("CREATE TABLE"));
    assertFalse(MAT.contains("DELETE FROM dbo.MaterialRequestFollowUps"));
    assertFalse(MAT.contains("UPDATE dbo.MaterialRequestFollowUps"));
    assertFalse(MAT.toLowerCase().contains("timeline"));
  }
}

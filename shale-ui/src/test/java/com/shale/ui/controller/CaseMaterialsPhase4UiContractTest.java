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

  @Test void requestTabIsReadOnlyWhileCaseMaterialsStillUseItemForms() {
    String requestController = MAT.substring(MAT.indexOf("final class CaseMaterialRequestsTabController"), MAT.indexOf("final class CaseMaterialItemsTabController"));
    assertTrue(requestController.contains("primary(\"New Request\")"));
    assertFalse(requestController.contains("ButtonType(\"Edit\")"));
    assertFalse(requestController.contains("openEditor"));
    assertFalse(requestController.contains("createMaterialRequest"));
    assertFalse(requestController.contains("updateMaterialRequest"));
    assertFalse(MAT.contains("final class MaterialRequestForm"));
    assertTrue(requestController.contains("listMaterialRequests(cid,tid)"));
    assertTrue(requestController.contains("getMaterialRequest(c,id,t,a)"));
    assertTrue(requestController.contains("ButtonType.CLOSE"));
    assertTrue(MAT.contains("final class MaterialItemForm extends Dialog"));
    assertTrue(MAT.contains("CreateMaterialItemCommand"));
    assertTrue(MAT.contains("Identity: Material Type *"));
    assertTrue(MAT.contains("Optional associated Material Request"));
    assertTrue(MAT.contains("ExternalLink reference selector"));
    assertTrue(MAT.contains("Storage location (not a file upload)"));
    assertFalse(MAT.contains("setTitle(\"Confirmation\")"));
  }


  @Test void requestTabNewRequestActionIsHeaderOnlyStyledAndPlaceholderOnly() {
    String requestController = MAT.substring(MAT.indexOf("final class CaseMaterialRequestsTabController"), MAT.indexOf("final class CaseMaterialItemsTabController"));
    String materialsUi = MAT.substring(MAT.indexOf("final class MaterialsUi"));
    int headerAction = requestController.indexOf("Button add=primary(\"New Request\")");
    int rootSection = requestController.indexOf("section(title,add,status,list)");
    int listCreation = requestController.indexOf("list=new VBox(10)");
    assertTrue(headerAction >= 0);
    assertTrue(rootSection > headerAction);
    assertTrue(listCreation >= 0 && headerAction > listCreation);
    assertTrue(requestController.contains("add.setOnAction(e->openNewRequestWindow())"));
    assertTrue(materialsUi.contains("ActionButtonFactory.primary(s,null)"));
    assertTrue(requestController.contains("AppDialogs.createModalStage(owner.get(),\"New Request\")"));
    assertTrue(requestController.contains("AppDialogs.createSecondaryWindowShell(stage,\"New Request\",stage::close,body)"));
    assertTrue(requestController.contains("private void openNewRequestWindow()"));
    String placeholder = requestController.substring(requestController.indexOf("private void openNewRequestWindow()"), requestController.indexOf("private void openDetail"));
    assertTrue(placeholder.contains("VBox body=newRequestBody(stage)"));
    assertTrue(placeholder.contains("TextField titleField=new TextField()"));
    assertTrue(placeholder.contains("titleField.setPromptText(\"New Request\")"));
    assertFalse(placeholder.contains("MaterialRequestForm"));
    assertTrue(placeholder.contains("DatePicker dueDate=newDatePicker(\"Select due date\")"));
    assertTrue(placeholder.contains("ColorCodedComboBox<MaterialTypeDto> materialType"));
    assertFalse(placeholder.contains("new ComboBox<MaterialTypeDto>"));
    assertFalse(placeholder.contains("ChoiceBox"));
    assertFalse(placeholder.contains("ButtonType.OK"));
    assertFalse(placeholder.contains("CreateMaterialRequest"));
    assertTrue(placeholder.contains("ActionButtonFactory.primary(\"Save\",e->{ })"));
    assertFalse(requestController.contains("createMaterialRequest"));
    assertFalse(requestController.contains("updateMaterialRequest"));
    assertFalse(MAT.contains("final class MaterialRequestForm"));
  }
  @Test void newRequestRequestedFromUsesSharedPartyEntityChooserWithoutCasePartyMutation() {
    String requestController = MAT.substring(MAT.indexOf("final class CaseMaterialRequestsTabController"), MAT.indexOf("final class CaseMaterialItemsTabController"));
    String dialog = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
    String body = requestController.substring(requestController.indexOf("VBox newRequestBody"), requestController.indexOf("private void loadNewRequestLookups"));
    assertTrue(body.contains("add(fields,1,\"Requested From:\",requestedFromBox)"));
    assertTrue(body.contains("ActionButtonFactory.primary(\"Add\",null)"));
    assertTrue(body.contains("requestedFromAction.setText(v==null?\"Add\":\"Change\")"));
    assertTrue(body.contains("ActionButtonFactory.neutral(\"Remove\",null)"));
    assertTrue(body.contains("AtomicReference<RequestedFromSelection>"));
    assertTrue(requestController.contains("record RequestedFromSelection(String entityType, Long entityId, String label, ContactCardFactory.ContactCardModel contactModel, OrganizationCardFactory.OrganizationCardModel organizationModel)"));
    assertTrue(body.contains("contactCards.create(v.contactModel(),ContactCardFactory.Variant.MINI)"));
    assertTrue(body.contains("organizationCards.create(v.organizationModel(),OrganizationCardFactory.Variant.MINI)"));
    assertTrue(body.contains("showRequestedFromChooser(stage)"));
    assertTrue(requestController.contains("RequestedFromWorkflowDialog.show"));
    assertTrue(requestController.contains("caseDao.findSelectableContactsForTenant()"));
    assertTrue(requestController.contains("caseDao.findSelectableOrganizationsForTenant()"));
    assertTrue(requestController.contains("contactDao.createContact(new ContactDao.CreateContactRequest"));
    assertTrue(requestController.contains("organizationDao.create(new OrganizationDao.OrganizationCreateRequest"));
    assertFalse(requestController.contains("caseDao.addCaseParty"));
    assertFalse(requestController.contains("createMaterialRequest"));
    assertFalse(requestController.contains("updateMaterialRequest"));
    assertFalse(MAT.contains("final class MaterialRequestForm"));
    assertTrue(dialog.contains("public record Selection"));
    assertTrue(dialog.contains("public static Selection show"));
    assertTrue(dialog.contains("Select Existing or Create New"));
    assertTrue(dialog.contains("Contact or Organization"));
  }



  @Test void itemDetailsAndExplicitItemOperationsRemainSeparated() {
    for (String command : new String[]{"UpdateMaterialItemCommand","ChangeMaterialItemLocationCommand","LinkMaterialItemToRequestCommand","UnlinkMaterialItemFromRequestCommand","ReleaseOrReturnMaterialItemCommand","SoftDeleteMaterialItemCommand"}) assertTrue(MAT.contains(command), command);
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

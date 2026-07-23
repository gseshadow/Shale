package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseMaterialsNewRequestWindowTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void newRequestWindowDefinesFinalFieldsInOrder() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");
        assertTrue(method.contains("new TextField()"));
        assertTrue(method.contains("titleField.setPromptText(\"New Request\")"));
        assertFalse(method.contains("new TextField(\"New Request\")"));
        assertFalse(method.contains("titleField.setText(\"New Request\")"));
        assertInOrder(method,
                "add(fields,0,\"Title:\",titleField)",
                "add(fields,1,\"Material Type:\",materialType)",
                "add(fields,2,\"Request Method:\",requestMethod)",
                "add(fields,3,\"Status:\",requestStatus)",
                "add(fields,4,\"Requested By:\",requestedBy)",
                "add(fields,5,\"Assigned To:\",assignedTo)");
    }

    @Test
    void newRequestWindowUsesSharedComponentsForLookupsAndUsers() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        String userFactory = methodBody(source, "private static UserSelector<CaseTaskService.AssignableUserOption> newUserSelector");
        assertTrue(source.contains("import com.shale.ui.component.ColorCodedComboBox;"));
        assertTrue(source.contains("import com.shale.ui.component.UserSelector;"));
        assertTrue(method.contains("ColorCodedComboBox<MaterialTypeDto> materialType=newLookupSelector(MaterialTypeDto::name,MaterialTypeDto::color,MaterialTypeDto::description)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestMethodDto> requestMethod=newLookupSelector(RequestMethodDto::name)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestStatusDto> requestStatus=newLookupSelector(RequestStatusDto::name)"));
        assertTrue(method.contains("UserSelector<CaseTaskService.AssignableUserOption> requestedBy=newUserSelector(\"Select requested by\",\"No users available\")"));
        assertTrue(method.contains("UserSelector<CaseTaskService.AssignableUserOption> assignedTo=newUserSelector(\"Select assigned user\",\"No users available\")"));
        assertTrue(userFactory.contains("new UserSelector<>(CaseTaskService.AssignableUserOption::id,CaseTaskService.AssignableUserOption::displayName,CaseTaskService.AssignableUserOption::color)"));
        assertFalse(method.contains("new ComboBox<CaseTaskService.AssignableUserOption>"));
        assertFalse(source.contains("private VBox userSelector"));
    }

    @Test
    void userCandidatesLoadOnceThroughServicePortOffFxThreadForBothSelectors() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestUsers");
        String caseController = Files.readString(CASE_CONTROLLER);
        assertTrue(body.contains("loadNewRequestUsers(requestedBy,assignedTo,stage)"));
        assertTrue(loader.contains("caseTaskService.loadAssignableUsers(tid)"));
        assertEquals(1, count(loader, "loadAssignableUsers(tid)"));
        assertTrue(loader.contains("ex.submit"));
        assertTrue(loader.contains("if(Platform.isFxApplicationThread())"));
        assertTrue(loader.contains("Platform.runLater"));
        assertTrue(loader.contains("requestedBy.setCandidates(safe)"));
        assertTrue(loader.contains("assignedTo.setCandidates(safe)"));
        assertTrue(loader.contains("setUserSelectorsLoading(requestedBy,assignedTo,true)"));
        assertTrue(loader.contains("setUserSelectorsLoading(requestedBy,assignedTo,false)"));
        assertTrue(caseController.contains("caseMaterialRequestsTabController.init(materialRequestService, caseTaskService, appState"));
        assertFalse(loader.toLowerCase().contains("dao"));
    }

    @Test
    void requestedByDefaultsOnlyByCurrentUserStableIdAndAssignedToStartsUnselected() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestUsers");
        assertTrue(loader.contains("Integer currentUserId=state==null?null:state.getUserId()"));
        assertTrue(loader.contains("u!=null&&u.id()==currentUserId"));
        assertTrue(loader.contains("ifPresent(requestedBy::setSelectedUser)"));
        assertTrue(loader.contains("assignedTo.clearSelection()"));
        assertFalse(loader.contains("displayName"));
        assertFalse(loader.contains("email"));
        assertFalse(loader.contains("selectFirst"));
    }

    @Test
    void userLoadingFailureLeavesBothSelectorsUnavailableAndShowsOneRequestsError() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestUsers");
        assertTrue(loader.contains("requestedBy.clearSelection()"));
        assertTrue(loader.contains("assignedTo.clearSelection()"));
        assertTrue(loader.contains("requestedBy.setDisable(true)"));
        assertTrue(loader.contains("assignedTo.setDisable(true)"));
        assertEquals(1, count(loader, "AppDialogs.showError(dialogOwner,\"Requests\",\"User choices could not be loaded. Please try again.\")"));
        assertFalse(loader.contains("stage.close()"));
    }

    @Test
    void lookupsRemainServicePortLoadedAndStatusDefaultsToRequestedSystemKey() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestLookups");
        String select = methodBody(source, "private static void selectRequestedStatus");
        assertTrue(body.contains("loadNewRequestLookups(materialType,requestMethod,requestStatus,stage)"));
        assertTrue(loader.contains("svc.listEffectiveMaterialTypes(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestMethods(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestStatuses(tenant())"));
        assertTrue(loader.contains("CaseMaterialRequestsTabController::selectRequestedStatus"));
        assertTrue(select.contains("requestStatus.getSelectionModel().clearSelection()"));
        assertTrue(select.contains("s!=null&&\"requested\".equalsIgnoreCase(s.systemKey())"));
        assertFalse(select.contains("name()"));
    }

    @Test
    void footerSaveIsNonfunctionalAndCancelStillConfirmsDiscard() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        String confirmMethod = methodBody(source, "boolean confirmDiscardNewRequest");
        assertTrue(method.contains("ActionButtonFactory.primary(\"Save\",e->{ })"));
        assertTrue(method.contains("if(confirmDiscardNewRequest(stage))stage.close()"));
        assertTrue(confirmMethod.contains("AppDialogs.showChoice"));
        assertTrue(confirmMethod.contains("\"Discard New Request?\""));
        assertFalse(method.contains("createMaterialRequest"));
        assertFalse(method.contains("updateMaterialRequest"));
        assertFalse(method.contains("mutate("));
    }

    @Test
    void requestControllerDoesNotIntroducePersistenceDatabaseOrPermissionChanges() throws Exception {
        String source = Files.readString(SOURCE);
        String requestController = source.substring(source.indexOf("final class CaseMaterialRequestsTabController"), source.indexOf("final class CaseMaterialItemsTabController"));
        for (String forbidden : new String[]{"CreateMaterialRequestCommand","UpdateMaterialRequestCommand","createMaterialRequest","updateMaterialRequest","MaterialRequestDao","materialRequestDao","UserDao","userDao","RequestedByUserId","AssignedToUserId"}) {
            assertFalse(requestController.contains(forbidden), forbidden);
        }
    }

    private static int count(String source, String needle) {
        int count = 0, pos = 0;
        while ((pos = source.indexOf(needle, pos)) >= 0) { count++; pos += needle.length(); }
        return count;
    }

    private static void assertInOrder(String source, String... snippets) {
        int pos = -1;
        for (String snippet : snippets) {
            int next = source.indexOf(snippet, pos + 1);
            assertTrue(next > pos, "Expected in-order snippet: " + snippet);
            pos = next;
        }
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "Missing method " + signatureStart);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) return source.substring(brace, i + 1);
        }
        fail("Could not parse method body for " + signatureStart);
        return "";
    }
}

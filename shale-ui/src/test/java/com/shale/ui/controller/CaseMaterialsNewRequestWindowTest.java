package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseMaterialsNewRequestWindowTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

    @Test
    void newRequestWindowDefinesTitleRequestMethodAndStatusFields() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(method.contains("new TextField()"));
        assertTrue(method.contains("titleField.setPromptText(\"New Request\")"));
        assertTrue(method.contains("add(fields,0,\"Title:\",titleField)"));
        assertTrue(method.contains("ComboBox<RequestMethodDto> requestMethod=lookupCombo(RequestMethodDto::name)"));
        assertTrue(method.contains("requestMethod.setPromptText(\"Select request method\")"));
        assertTrue(method.contains("add(fields,1,\"Request Method:\",requestMethod)"));
        assertTrue(method.contains("ComboBox<RequestStatusDto> requestStatus=lookupCombo(RequestStatusDto::name)"));
        assertTrue(method.contains("requestStatus.setPromptText(\"Select status\")"));
        assertTrue(method.contains("add(fields,2,\"Status:\",requestStatus)"));
        assertFalse(method.contains("Material Type:"));
        assertFalse(method.contains("new TextField(\"New Request\")"));
        assertFalse(method.contains("titleField.setText(\"New Request\")"));
    }

    @Test
    void lookupChoicesLoadThroughMaterialRequestServicePortOffJavaFxThread() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestLookups");

        assertTrue(body.contains("loadNewRequestLookups(requestMethod,requestStatus,stage)"));
        assertTrue(loader.contains("ex.submit"));
        assertTrue(loader.contains("svc.listEffectiveRequestMethods(tid)"));
        assertTrue(loader.contains("svc.listEffectiveRequestStatuses(tid)"));
        assertTrue(loader.contains("Platform.runLater"));
        assertFalse(loader.contains("MaterialRequestDao"));
    }

    @Test
    void lookupControlsAreDisabledDuringLoadAndEnabledAfterSuccess() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestLookups");

        assertTrue(loader.contains("requestMethod.setDisable(true)"));
        assertTrue(loader.contains("requestStatus.setDisable(true)"));
        assertTrue(loader.contains("requestMethod.setDisable(false)"));
        assertTrue(loader.contains("requestStatus.setDisable(false)"));
    }

    @Test
    void lookupFailureUsesSingleEstablishedErrorPathAndDoesNotCloseWindow() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestLookups");

        assertTrue(loader.contains("catch(Exception x)"));
        assertTrue(loader.contains("LOG.warn(\"New Material Request lookup failed tenantId={}\""));
        assertTrue(loader.contains("AppDialogs.showError(dialogOwner,\"Requests\",\"Request choices could not be loaded. Please try again.\")"));
        assertFalse(loader.contains("stage.close()"));
        assertFalse(loader.contains("dialogOwner.hide()"));
    }

    @Test
    void dropdownDisplayUsesDtoNameWhileRetainingDtoIdentity() throws Exception {
        String source = Files.readString(SOURCE);
        String comboFactory = methodBody(source, "private static <T> ComboBox<T> lookupCombo");
        String body = methodBody(source, "VBox newRequestBody");

        assertTrue(comboFactory.contains("new ComboBox<>()"));
        assertTrue(comboFactory.contains("setConverter"));
        assertTrue(comboFactory.contains("name.apply(value)"));
        assertTrue(comboFactory.contains("public T fromString(String s){return null;}"));
        assertTrue(body.contains("lookupCombo(RequestMethodDto::name)"));
        assertTrue(body.contains("lookupCombo(RequestStatusDto::name)"));
    }

    @Test
    void requestMethodStartsUnselectedAndStatusSelectsRequestedOnly() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String selector = methodBody(source, "private static void selectRequestedStatus");

        assertFalse(body.contains("requestMethod.getSelectionModel().select"));
        assertTrue(selector.contains("clearSelection()"));
        assertTrue(selector.contains("s!=null&&\"requested\".equalsIgnoreCase(s.systemKey())"));
        assertTrue(selector.contains("findFirst().ifPresent(requestStatus.getSelectionModel()::select)"));
        assertFalse(selector.contains("selectFirst()"));
    }

    @Test
    void footerUsesAppStyledButtonsAndSaveIsIntentionallyEmpty() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");

        assertTrue(method.contains("ActionButtonFactory.primary(\"Save\",e->{ })"));
        assertTrue(method.contains("ActionButtonFactory.neutral(\"Cancel\""));
        assertTrue(method.contains("footer.getStyleClass().add(\"app-dialog-actions\")"));
        assertTrue(method.contains("footer.setAlignment(Pos.CENTER_RIGHT)"));
        assertFalse(method.contains("mutate("));
        assertFalse(method.contains("showInfo("));
        assertFalse(method.contains("showError("));
    }

    @Test
    void cancelUsesStandardConfirmationChoicesAndOnlyDiscardClosesNewRequest() throws Exception {
        String source = Files.readString(SOURCE);
        String bodyMethod = methodBody(source, "VBox newRequestBody");
        String confirmMethod = methodBody(source, "boolean confirmDiscardNewRequest");

        assertTrue(bodyMethod.contains("if(confirmDiscardNewRequest(stage))stage.close()"));
        assertTrue(confirmMethod.contains("AppDialogs.showChoice"));
        assertTrue(confirmMethod.contains("\"Discard New Request?\""));
        assertTrue(confirmMethod.contains("\"Any information entered in this request will be lost.\""));
        assertTrue(confirmMethod.contains("AppDialogs.DialogAction.of(\"Discard\",true,AppDialogs.DialogActionKind.DANGER,true,false)"));
        assertTrue(confirmMethod.contains("AppDialogs.DialogAction.cancel(\"Keep Editing\",false)"));
    }

    @Test
    void materialRequestFormAndMutationPlumbingAreNotRestored() throws Exception {
        String source = Files.readString(SOURCE);
        String requestController = source.substring(source.indexOf("final class CaseMaterialRequestsTabController"), source.indexOf("final class CaseMaterialItemsTabController"));

        assertFalse(source.contains("final class MaterialRequestForm"));
        assertFalse(requestController.contains("CreateMaterialRequestCommand"));
        assertFalse(requestController.contains("UpdateMaterialRequestCommand"));
        assertFalse(requestController.contains("createMaterialRequest"));
        assertFalse(requestController.contains("updateMaterialRequest"));
        assertFalse(requestController.contains("MaterialRequestDao"));
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

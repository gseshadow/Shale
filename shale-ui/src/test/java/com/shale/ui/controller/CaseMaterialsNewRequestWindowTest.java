package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseMaterialsNewRequestWindowTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

    @Test
    void newRequestWindowDefinesTitleAndThreeLookupFieldsInOrder() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(method.contains("new TextField()"));
        assertTrue(method.contains("titleField.setPromptText(\"New Request\")"));
        assertFalse(method.contains("new TextField(\"New Request\")"));
        assertFalse(method.contains("titleField.setText(\"New Request\")"));

        assertInOrder(method,
                "add(fields,0,\"Title:\",titleField)",
                "add(fields,1,\"Material Type:\",materialType)",
                "add(fields,2,\"Request Method:\",requestMethod)",
                "add(fields,3,\"Status:\",requestStatus)");
    }

    @Test
    void newRequestWindowUsesSharedColorCodedLookupSelectorForAllLookups() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(source.contains("import com.shale.ui.component.ColorCodedComboBox;"));
        assertTrue(method.contains("ColorCodedComboBox<MaterialTypeDto> materialType=newLookupSelector(MaterialTypeDto::name,MaterialTypeDto::color,MaterialTypeDto::description)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestMethodDto> requestMethod=newLookupSelector(RequestMethodDto::name)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestStatusDto> requestStatus=newLookupSelector(RequestStatusDto::name)"));
        assertTrue(source.contains("private static <T> ColorCodedComboBox<T> newLookupSelector"));
        assertTrue(source.contains("return new ColorCodedComboBox<>(name,color,secondaryText)"));
        assertFalse(source.contains("private static <T> ComboBox<T> lookupCombo"));
        assertFalse(method.contains("new ComboBox<"));
        assertFalse(method.contains("setConverter(new javafx.util.StringConverter"));
    }

    @Test
    void lookupPromptsAndHgrowAreConfigured() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");

        assertTrue(method.contains("materialType.setPromptText(\"Select material type\")"));
        assertTrue(method.contains("requestMethod.setPromptText(\"Select request method\")"));
        assertTrue(method.contains("requestStatus.setPromptText(\"Select status\")"));
        assertTrue(method.contains("GridPane.setHgrow(titleField,Priority.ALWAYS)"));
        assertTrue(method.contains("GridPane.setHgrow(materialType,Priority.ALWAYS)"));
        assertTrue(method.contains("GridPane.setHgrow(requestMethod,Priority.ALWAYS)"));
        assertTrue(method.contains("GridPane.setHgrow(requestStatus,Priority.ALWAYS)"));
    }

    @Test
    void lookupsLoadThroughServicePortsIndependentlyWithoutDaosOrDefaultMaterialMethodSelection() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestLookups");
        String loadOne = methodBody(source, "private <T> void loadLookup");

        assertTrue(body.contains("loadNewRequestLookups(materialType,requestMethod,requestStatus,stage)"));
        assertTrue(loader.contains("svc.listEffectiveMaterialTypes(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestMethods(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestStatuses(tenant())"));
        assertFalse(loader.toLowerCase().contains("dao"));
        assertTrue(loadOne.contains("lookup.setDisable(true)"));
        assertTrue(loadOne.contains("ex.submit"));
        assertTrue(loadOne.contains("Platform.runLater"));
        assertTrue(loadOne.contains("lookup.getItems().setAll(rows==null?List.of():rows)"));
        assertTrue(loadOne.contains("lookup.setDisable(false)"));
        assertTrue(loadOne.contains("AppDialogs.showError(dialogOwner,\"Requests\",errorMessage)"));
        assertFalse(loadOne.contains("stage.close()"));
        assertFalse(body.contains("materialType.getSelectionModel().select"));
        assertFalse(body.contains("requestMethod.getSelectionModel().select"));
    }

    @Test
    void statusDefaultsToRequestedSystemKeyOnlyWhenAvailable() throws Exception {
        String source = Files.readString(SOURCE);
        String loader = methodBody(source, "private void loadNewRequestLookups");
        String select = methodBody(source, "private static void selectRequestedStatus");

        assertTrue(loader.contains("CaseMaterialRequestsTabController::selectRequestedStatus"));
        assertTrue(select.contains("requestStatus.getSelectionModel().clearSelection()"));
        assertTrue(select.contains("s!=null&&\"requested\".equalsIgnoreCase(s.systemKey())"));
        assertTrue(select.contains("findFirst().ifPresent(requestStatus.getSelectionModel()::select)"));
        assertFalse(select.contains("name()"));
    }

    @Test
    void footerUsesAppStyledButtonsAndSaveIsIntentionallyEmpty() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(method.contains("ActionButtonFactory.primary(\"Save\",e->{ })"));
        assertTrue(method.contains("ActionButtonFactory.neutral(\"Cancel\""));
        assertTrue(method.contains("footer.getStyleClass().add(\"app-dialog-actions\")"));
        assertTrue(method.contains("footer.setAlignment(Pos.CENTER_RIGHT)"));
        assertFalse(method.contains("mutate("));
        assertFalse(method.contains("createMaterialRequest"));
        assertFalse(method.contains("updateMaterialRequest"));
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
    void materialRequestFormMutationPersistenceAndDatabasePlumbingAreNotRestored() throws Exception {
        String source = Files.readString(SOURCE);
        String requestController = source.substring(source.indexOf("final class CaseMaterialRequestsTabController"), source.indexOf("final class CaseMaterialItemsTabController"));

        assertFalse(source.contains("final class MaterialRequestForm"));
        assertFalse(requestController.contains("CreateMaterialRequestCommand"));
        assertFalse(requestController.contains("UpdateMaterialRequestCommand"));
        assertFalse(requestController.contains("createMaterialRequest"));
        assertFalse(requestController.contains("updateMaterialRequest"));
        assertFalse(requestController.toLowerCase().contains("dao"));
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

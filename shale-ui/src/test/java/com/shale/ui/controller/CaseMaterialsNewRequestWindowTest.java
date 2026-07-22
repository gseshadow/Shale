package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseMaterialsNewRequestWindowTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

    @Test
    void newRequestWindowDefinesOnlyIncrementalTitleFieldAndPrompt() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(method.contains("new TextField()"));
        assertTrue(method.contains("titleField.setPromptText(\"New Request\")"));
        assertTrue(method.contains("add(fields,0,\"Title:\",titleField)"));
        assertTrue(method.contains("titleField.setMaxWidth(Double.MAX_VALUE)"));
        assertTrue(method.contains("GridPane.setHgrow(titleField,Priority.ALWAYS)"));
        assertFalse(method.contains("new TextField(\"New Request\")"));
        assertFalse(method.contains("titleField.setText(\"New Request\")"));
    }

    @Test
    void newRequestWindowUsesColorCodedMaterialTypeSelector() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");

        assertTrue(source.contains("import com.shale.ui.component.ColorCodedComboBox;"));
        assertTrue(method.contains("ColorCodedComboBox<MaterialTypeDto> materialType=new ColorCodedComboBox<>(MaterialTypeDto::name, MaterialTypeDto::color)"));
        assertTrue(method.contains("materialType.setPromptText(\"Select Material Type\")"));
        assertTrue(method.contains("materialType.setMaxWidth(Double.MAX_VALUE)"));
        assertTrue(method.contains("add(fields,1,\"Material Type:\",materialType)"));
        assertTrue(method.contains("GridPane.setHgrow(materialType,Priority.ALWAYS)"));
    }

    @Test
    void materialTypeChoicesLoadThroughExistingServicePathWithoutDefaultSelection() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestMaterialTypes");

        assertTrue(body.contains("loadNewRequestMaterialTypes(materialType,stage)"));
        assertTrue(loader.contains("ex.submit"));
        assertTrue(loader.contains("svc.listEffectiveMaterialTypes(tid)"));
        assertTrue(loader.contains("Platform.runLater(()->materialType.getItems().setAll(types==null?List.of():types))"));
        assertTrue(loader.contains("AppDialogs.showError(dialogOwner,\"Requests\",\"Material Type choices could not be loaded. Please try again.\")"));
        assertFalse(body.contains("materialType.getSelectionModel().selectFirst()"));
        assertFalse(body.contains("materialType.getSelectionModel().select("));
        assertFalse(loader.contains("selectFirst()"));
    }

    @Test
    void footerUsesAppStyledButtonsAndSaveIsIntentionallyEmpty() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");

        assertTrue(method.contains("ActionButtonFactory.primary(\"Save\",e->{ })"));
        assertTrue(method.contains("ActionButtonFactory.neutral(\"Cancel\""));
        assertTrue(method.contains("footer.getStyleClass().add(\"app-dialog-actions\")"));
        assertTrue(method.contains("footer.setAlignment(Pos.CENTER_RIGHT)"));
        assertFalse(method.contains("svc."));
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

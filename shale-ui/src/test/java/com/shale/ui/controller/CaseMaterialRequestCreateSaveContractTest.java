package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseMaterialRequestCreateSaveContractTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path PORT = Path.of("../shale-core/src/main/java/com/shale/core/service/MaterialRequestServicePort.java");

    @Test
    void saveBuildsCreateCommandThroughMaterialRequestServiceWithStableIds() throws Exception {
        String c = Files.readString(CONTROLLER);
        assertTrue(c.contains("svc.createMaterialRequest(cmd)"));
        assertTrue(c.contains("new MaterialRequestServicePort.CreateMaterialRequestCommand"));
        assertTrue(c.contains("materialType.getValue().id()"));
        assertTrue(c.contains("by.id()"));
        assertTrue(c.contains("assignee==null?null:assignee.id()"));
        assertTrue(c.contains("rf!=null&&rf.contact()?rf.entityId().intValue():null"));
        assertTrue(c.contains("rf!=null&&rf.organization()?rf.entityId().intValue():null"));
        assertFalse(c.substring(c.indexOf("final class CaseMaterialRequestsTabController"), c.indexOf("final class CaseMaterialItemsTabController")).contains("MaterialRequestDao"));
    }

    @Test
    void saveGuardsValidationLoadingDuplicateSubmissionAndLifecycle() throws Exception {
        String c = Files.readString(CONTROLLER);
        assertTrue(c.contains("loading.get()>0||saving.get()||err!=null"));
        assertTrue(c.contains("titleField.getText()==null||titleField.getText().trim().isEmpty()"));
        assertTrue(c.contains("if(!savingRequest.compareAndSet(false,true))return"));
        assertTrue(c.contains("if(Platform.isFxApplicationThread()) throw new IllegalStateException"));
        assertTrue(c.contains("if(stage.getScene()==null||stage.getOwner()==null||stale(g,c))return"));
        assertTrue(c.contains("stage.close(); refresh();"));
    }

    @Test
    void createCommandContainsDialogFieldsAndNullableDates() throws Exception {
        String p = Files.readString(PORT);
        for (String field : new String[]{"materialTypeId", "title", "description", "requestedFromContactId", "requestedFromOrganizationId", "requestedFromText", "requestMethod", "status", "requestedByUserId", "assignedToUserId", "requestedAt", "expectedResponseDate", "nextFollowUpAt"}) {
            assertTrue(p.contains(field), field);
        }
    }
}

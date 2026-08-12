package com.shale.ui.controller;

import com.shale.core.dto.MaterialTypeDto;
import com.shale.core.dto.RequestMethodDto;
import com.shale.core.dto.RequestStatusDto;
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
        assertTrue(c.contains("materialType.id()"));
        assertTrue(c.contains("selectedUserId(by)"));
        assertTrue(c.contains("assignee==null?null:assignee.id()"));
        assertTrue(c.contains("rf!=null&&rf.contact()?rf.entityId().intValue():null"));
        assertTrue(c.contains("rf!=null&&rf.organization()?rf.entityId().intValue():null"));
        assertFalse(c.substring(c.indexOf("final class CaseMaterialRequestsTabController"), c.indexOf("final class CaseMaterialItemsTabController")).contains("MaterialRequestDao"));
    }

    @Test
    void saveGuardsValidationLoadingDuplicateSubmissionAndLifecycle() throws Exception {
        String c = Files.readString(CONTROLLER);
        assertTrue(c.contains("!saveEligible(loading.get(),saving.get(),err)"));
        assertTrue(c.contains("title==null||title.trim().isEmpty()"));
        assertTrue(c.contains("if(!savingRequest.compareAndSet(false,true))return"));
        assertTrue(c.contains("if(Platform.isFxApplicationThread()) throw new IllegalStateException"));
        assertTrue(c.contains("if(stage.getScene()==null||stage.getOwner()==null||stale(g,c))return"));
        assertTrue(c.contains("stage.close(); refresh();"));
    }

    @Test
    void hiddenDateDefaultsDoNotExposeOrRequireRemovedDateFields() throws Exception {
        String c = Files.readString(CONTROLLER);
        assertTrue(c.contains("defaultRequestedAt()"));
        assertTrue(c.contains("private LocalDateTime defaultRequestedAt(){ return LocalDateTime.now(); }"));
        assertTrue(c.contains("configureFollowUpInterval(followUpInterval)"));
        assertTrue(c.contains("intervalDays(followUpInterval)"));
        assertTrue(c.contains("Calculated when saved"));
        assertFalse(c.contains("DatePicker requestedAt"));
        assertFalse(c.contains("DatePicker dueAt"));
        assertFalse(c.contains("DatePicker nextFollowUpAt"));
        assertFalse(c.contains("Requested At is required."));
    }

    @Test
    void createCommandContainsDialogFieldsAndNullableDates() throws Exception {
        String p = Files.readString(PORT);
        for (String field : new String[]{"materialTypeId", "title", "description", "requestedFromContactId", "requestedFromOrganizationId", "requestedFromText", "requestMethod", "status", "requestedByUserId", "assignedToUserId", "requestedAt", "expectedResponseDate", "nextFollowUpAt", "followUpIntervalDays"}) {
            assertTrue(p.contains(field), field);
        }
    }
    @Test
    void optionalRequestedByDoesNotParticipateInSaveEligibility() {
        var requestedFrom = new CaseMaterialRequestsTabController.RequestedFromSelection("contact", 10L, "Valid Contact", new com.shale.ui.component.factory.ContactCardFactory.ContactCardModel(10, "Valid Contact", null, null, null), null);
        var type = new MaterialTypeDto(1, null, "records", "Records", null, null, 0);
        var method = new RequestMethodDto(2, null, "email", "Email", null, 0, true, false);
        var status = new RequestStatusDto(3, null, "requested", "Requested", 0, true, false);

        String validError = CaseMaterialRequestsTabController.validateRequestFields(requestedFrom, "Valid title", type, method, status);
        assertNull(validError);
        assertTrue(CaseMaterialRequestsTabController.saveEligible(0, false, validError),
                "Requested By is intentionally absent and must not disable Save.");
        assertNull(CaseMaterialRequestsTabController.selectedUserId(null),
                "The create and update command builders must send null after Requested By is removed.");
    }

    @Test
    void requestedFromRemainsTheFirstRequiredValidationAndDisablesSave() {
        var type = new MaterialTypeDto(1, null, "records", "Records", null, null, 0);
        var method = new RequestMethodDto(2, null, "email", "Email", null, 0, true, false);
        var status = new RequestStatusDto(3, null, "requested", "Requested", 0, true, false);

        String error = CaseMaterialRequestsTabController.validateRequestFields(null, "Valid title", type, method, status);
        assertEquals("Requested From is required.", error);
        assertFalse(CaseMaterialRequestsTabController.saveEligible(0, false, error));
    }

}

package com.shale.core.service;

import com.shale.core.dto.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MaterialRequestServicePort {
    List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId);
    List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int shaleClientId);
    Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command);
    MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command);
    MaterialRequestDetailDto changeMaterialRequestStatus(ChangeMaterialRequestStatusCommand command);
    void deleteMaterialRequest(DeleteMaterialRequestCommand command);
    List<MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    FollowUpResult recordFollowUp(RecordMaterialRequestFollowUpCommand command);

    record CreateMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, int materialTypeId, String title, String description, int requestedByUserId, Integer assignedToUserId, Integer requestedFromContactId, Integer requestedFromOrganizationId, String requestedFromText, String requestMethod, LocalDateTime requestedAt, LocalDate relevantStartDate, LocalDate relevantEndDate, String status, LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt, LocalDateTime firstReceivedAt, LocalDateTime fullyReceivedAt, LocalDateTime closedAt, Integer closedByUserId, String closureReason, String notes) {}
    record UpdateMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, long materialRequestId, int materialTypeId, String title, String description, Integer assignedToUserId, Integer requestedFromContactId, Integer requestedFromOrganizationId, String requestedFromText, String requestMethod, LocalDateTime requestedAt, LocalDate relevantStartDate, LocalDate relevantEndDate, LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt, String notes, byte[] expectedRowVer) {}
    record ChangeMaterialRequestStatusCommand(int shaleClientId, int actorUserId, long caseId, long materialRequestId, String status, LocalDateTime requestedAt, LocalDateTime firstReceivedAt, LocalDateTime fullyReceivedAt, LocalDateTime closedAt, Integer closedByUserId, String closureReason, byte[] expectedRowVer) {}
    record DeleteMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, long materialRequestId, byte[] expectedRowVer) {}
    record RecordMaterialRequestFollowUpCommand(int shaleClientId, int actorUserId, long caseId, long materialRequestId, LocalDateTime attemptedAt, Integer attemptedByUserId, String method, String outcome, LocalDateTime nextFollowUpAt, String notes, byte[] expectedRequestRowVer) {}
    record FollowUpResult(MaterialRequestFollowUpDto followUp, MaterialRequestDetailDto request) {}
}
